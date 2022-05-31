/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.AuthenticationQuestion;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.AuthenticationAnswerDTO;
import sailpoint.service.SecurityQuestionsService;
import sailpoint.service.PasswordReseter.AnswerChecker;
import sailpoint.service.PasswordReseter.AnswerChecker.Outcome;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * A JSF bean for users to provide answers for their authentication questions.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class AuthenticationAnswersBean extends BaseBean {
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private List<AuthenticationAnswerDTO> answers;
    private String preLoginUrl;
    private boolean fromPreferencesPage;
    
    private List<SelectItem> questions;
    
    // Use this for 'Forgot Password' case where auth questions need to be answered
    private String accountId;

    private Identity identity;

    private int numRequired;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public AuthenticationAnswersBean() throws GeneralException {
        this(false);
    }

    public AuthenticationAnswersBean(boolean fromPreferences)
        throws GeneralException {
        
        super();
        this.preLoginUrl = LoginBean.retrievePreLoginUrl(this);
        this.fromPreferencesPage = fromPreferences;

        if (!isLoggedIn()) {
            // We need to load the answers using this id
            ValueBinding vb = getFacesContext().getApplication().createValueBinding("#{login}");
            LoginBean lb = (LoginBean) vb.getValue(getFacesContext());
            accountId = lb.getAccountId();
            // If we can't get it from the loginBean try this
            if (accountId == null) {
                accountId = super.getRequestOrSessionParameter("accountId");
            }
            if (accountId == null) {
            	accountId =
                    super.getRequestOrSessionParameter(LoginBean.ATT_EXPIRED_ACCOUNT_ID);
            }

            if (accountId != null && accountId.length() != 0) {
                identity = findIdentity(accountId);
                // Put the account id back in the session
                // when this bean is first loaded we go through this
                // constructor a couple times and the account id gets lost
                getSessionScope().put("accountId", accountId);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public List<AuthenticationAnswerDTO> getAnswers() throws GeneralException {
        if (null == this.answers) {
            initializeIdentity();
            SecurityQuestionsService service = new SecurityQuestionsService(this);
            this.answers = service.getAuthenticationAnswers(this.identity);
        }
        return this.answers;
    }
    
    public List<AuthenticationAnswerDTO> getAuthAnswers() throws GeneralException {
        if (null == this.answers) {
            initializeIdentity();
            SecurityQuestionsService service = new SecurityQuestionsService(this);
            this.answers = service.getAuthenticationAnswersForSetup(this.identity);
        }
        return this.answers;
    }
    
    public void setAnswers(List<AuthenticationAnswerDTO> answers) {
        this.answers = answers;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    private void initializeIdentity() throws GeneralException {
		if (identity == null) {
	        // Case when answering questions for authentication
	        if (accountId != null) {
	            identity = findIdentity(this.accountId);
	        }
	        else {
	            // Case when loading questions that need answering
	            identity = getLoggedInUser();
	        }
	    }
	}

	/**
    * Find the identity with the given name - this is case insensitive so that
    * it will behave the same way as authentication.
    */
   protected Identity findIdentity(String accountId) throws GeneralException {

       QueryOptions ops = new QueryOptions();
       ops.add(Filter.ignoreCase(Filter.eq("name", accountId)));
       List<Identity> results = getContext().getObjects(Identity.class, ops);

       // Shouldn't happen since we already authenticated.  Put this here just
       // to be safe, though.
       if (1 != results.size()) {
           throw new GeneralException("Could not load a unique identity: " + accountId);
       }

       return results.get(0);
   }
    
    public String getPreLoginUrl() {
        return this.preLoginUrl;
    }
    
    public void setPreLoginUrl(String preLoginUrl) {
        this.preLoginUrl = preLoginUrl;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return the select items for the possible questions.
     */
    public List<SelectItem> getQuestions() throws GeneralException {
        
        if (null == this.questions) {
            this.questions = new ArrayList<SelectItem>();

            this.questions.add(new SelectItem("", getMessage(MessageKeys.AUTH_ANSWERS_SELECT_QUESTION)));
            
            List<AuthenticationQuestion> questions =
                getContext().getObjects(AuthenticationQuestion.class);
            for (AuthenticationQuestion question : questions) {
                this.questions.add(new SelectItem(question.getId(), question.getQuestion(getLocale())));
            }
        }

        return this.questions;
    }
    
    /**
     * Return the select items for the possible questions.
     */
    public List<SelectItem> getAnsweredQuestions() throws GeneralException {
        
        initializeIdentity();
        
        if (null == this.questions) {
            questions = new ArrayList<SelectItem>();

            questions.add(new SelectItem("", getMessage(MessageKeys.AUTH_ANSWERS_SELECT_QUESTION)));
            
            List<AuthenticationAnswer> answers = identity.getAuthenticationAnswers();
            
            for (AuthenticationAnswer answer : answers) {
                AuthenticationQuestion question = answer.getQuestion();
                this.questions.add(new SelectItem(question.getId(), question.getQuestion(getLocale())));
            }
        }

        return this.questions;
    }

	
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * JSF action to save the challenge answers for the logged in user.
     */
    public String save() throws GeneralException, IOException {

        if (!this.save(true)) {
            return null;
        }
        getContext().commitTransaction();

        // Redirect to preLoginUrl if there was one.
        if (WebUtil.isValidRedirectUrl(this.preLoginUrl)) {
            getFacesContext().getExternalContext().redirect(this.preLoginUrl);
            return null;
        }
        
        return "success";
    }

    /**
     * Helper method that will validate and save the authentication answers.
     * Note this does not commit.
     * 
     * @param  requireAnswers  Whether answers are required to validate.
     * 
     * @return True if this saved with no validation errors, false otherwise.
     * @throws GeneralException
     */
    public boolean save(boolean requireAnswers) throws GeneralException {
        
        // Validate first.
        if (!validate(requireAnswers)) {
            return false;
        }

        SecurityQuestionsService service = new SecurityQuestionsService(this);

        service.saveAnswers(this.answers, getLoggedInUser());

        return true;
    }
    
    /**
     * Validate the authentication answers, add messages and return false if
     * there are any validation errors.
     */
    private boolean validate(boolean requireAnswers) throws GeneralException {
        SecurityQuestionsService service = new SecurityQuestionsService(this);

        List<Message> errors = service.validateAuthenticationAnswers(this.answers, requireAnswers);

        boolean valid = true;

        if (errors.size() > 0) {
            valid = false;

            for (Message errorMsg : errors) {
                super.addMessage(errorMsg);
            }
        }

        return valid;
    }
    
    /**
     * Create a list of AuthenticationAnswers from the dtos
     */
    private List<AuthenticationAnswer> createAnswersList() throws GeneralException {
        List<AuthenticationAnswer> answersList = new ArrayList<AuthenticationAnswer>();
        
        for (AuthenticationAnswerDTO dto : answers) {
            String answerProvided = dto.getAnswerProvided();
            AuthenticationAnswer ans = new AuthenticationAnswer();
            ans.setAnswer(answerProvided);
            ans.setQuestion(getContext().getObjectById(AuthenticationQuestion.class, dto.getQuestionId()));
            answersList.add(ans);
        }
        
        return answersList;
    }
    
    /**
     * Used when 'Forgot Password'
     * Check answers to make sure they match the answers previously provided.
     * Forward on to 'reset password' page
     * 
     * @return
     * @throws GeneralException 
     */
    public String checkAnswers() throws GeneralException {
        String outcome = "";
        AnswerChecker checker = new AnswerChecker(getContext());
        
        Outcome checkoutcome = checker.checkAnswers(identity, accountId, createAnswersList()); 
        if (checkoutcome == Outcome.StayOnPage) {
            outcome = "";
        } else if (checkoutcome == Outcome.Fail) {
            outcome = "fail";
        } else if (checkoutcome == Outcome.Success) {
            outcome = "success";
            Map<String, Object> session = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
            session.put(LoginBean.ATT_EXPIRED_ACCOUNT_ID, accountId);
            session.put(LoginBean.ATT_USERNAME, identity.getDisplayableName());
            // tell the next page that we've successfully answered the questions
            session.put(LoginBean.ATT_PASSWORD_RESET_AUTHORIZED, LoginBean.ATT_PASSWORD_RESET_FORGOT);
        } else {
            throw new IllegalStateException("unknown checkAnswer outcome: " + checkoutcome);
        }
        
        return outcome;
    }
}
