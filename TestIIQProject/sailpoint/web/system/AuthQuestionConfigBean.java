/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.system;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.AuthenticationQuestion;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.LoginConfigBean;
import sailpoint.web.messages.MessageKeys;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * A JSF bean for editing authentication question configuration.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class AuthQuestionConfigBean extends UserResetConfigBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A DTO for an authentication question - the ID may either be an actual
     * hibernate ID or a generated value if this is a new question.
     */
    public static class AuthenticationQuestionDTO {

        private String id;
        private String question;
        
        public AuthenticationQuestionDTO() {}

        public AuthenticationQuestionDTO(String id) {
            this.id = id;
        }

        public AuthenticationQuestionDTO(AuthenticationQuestion q) {
            this.id = q.getId();
            this.question = q.getQuestion();
        }

        public String getId() {
            return this.id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getQuestion() {
            return this.question;
        }
        
        public void setQuestion(String question) {
            this.question = question;
        }
        
        /**
         * Return the value to display in the UI, which will be i18n'd.
         */
        public String getDisplayedQuestion() {
            String q = Internationalizer.getMessage(this.question, getLocale());
            return (null != q) ? q : this.question;
        }

        /**
         * Set the question using the given display value - this does not
         * overwrite the question if the given value matches the i18n'd string.
         */
        public void setDisplayedQuestion(String q) {
            String displayed = this.getDisplayedQuestion();
            if ((null == displayed) || !displayed.equals(q)) {
                this.question = q;
            }
        }

        private Locale getLocale() {
            // This is a little ugly but since this is a static inner class we
            // don't have access to the locale.  We could do the attach(BaseBean)
            // trick, but this is more error-prone.
            ValueBinding vb = 
                FacesContext.getCurrentInstance().getApplication().createValueBinding("#{base}");
            BaseBean base = (BaseBean) vb.getValue(FacesContext.getCurrentInstance());
            return base.getLocale();
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private boolean authQuestionsEnabled;
    
    private List<AuthenticationQuestionDTO> questions;

    private int numQuestionsForAuthn;
    private int numAnswersRequired;
    private int maxAuthQuestionFailures;
    private long authQuestionLockoutDurationMillis;
    private boolean promptForAnswersAfterLogin;
    
    private Set<String> addedQuestionIds;
    private Set<String> removedQuestionIds;
    private String selectedQuestionId;
    //Transient var to keep count of modified questions for auditing
    private transient int modifiedQuestions;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public AuthQuestionConfigBean() throws GeneralException {
        super();

        this.addedQuestionIds = new HashSet<String>();
        this.removedQuestionIds = new HashSet<String>();
        
        this.init();
    }

    private void init() throws GeneralException {
        
        this.questions = new ArrayList<AuthenticationQuestionDTO>();

        List<AuthenticationQuestion> qs =
            getContext().getObjects(AuthenticationQuestion.class);
        if (null != qs) {
            for (AuthenticationQuestion q : qs) {
                this.questions.add(new AuthenticationQuestionDTO(q));
            }
        }

        // Add a question if we don't have one yet.
        if (Util.isEmpty(qs)) {
            this.addQuestion(0);
        }

        // Load fields from system config.
        Configuration config = getContext().getConfiguration();
        this.authQuestionsEnabled = 
            config.getBoolean(Configuration.AUTH_QUESTIONS_ENABLED);
        this.numQuestionsForAuthn =
            config.getInt(Configuration.NUM_AUTH_QUESTIONS_FOR_AUTHN);
        this.numAnswersRequired =
            config.getInt(Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED);
        this.maxAuthQuestionFailures =
            config.getInt(Configuration.MAX_AUTH_QUESTION_FAILURES);
        this.authQuestionLockoutDurationMillis =
            config.getLong(Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS);
        this.promptForAnswersAfterLogin =
            config.getBoolean(Configuration.PROMPT_FOR_AUTH_QUESTION_ANSWERS_AFTER_LOGIN);
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isAuthQuestionsEnabled() {
        return authQuestionsEnabled;
    }

    public void setAuthQuestionsEnabled(boolean enabled) {
        this.authQuestionsEnabled = enabled;
    }
    
    public List<AuthenticationQuestionDTO> getQuestions() {
        return this.questions;
    }
    
    public void setQuestions(List<AuthenticationQuestionDTO> questions) {
        this.questions = questions;
    }
    
    public int getNumQuestionsForAuthn() {
        return this.numQuestionsForAuthn;
    }

    public void setNumQuestionsForAuthn(int num) {
        this.numQuestionsForAuthn = num;
    }

    public int getNumAnswersRequired() {
        return this.numAnswersRequired;
    }

    public void setNumAnswersRequired(int numRequired) {
        this.numAnswersRequired = numRequired;
    }
    
    public int getMaxAuthQuestionFailures() {
        return this.maxAuthQuestionFailures;
    }

    public void setMaxAuthQuestionFailures(int maxAuthQuestionFailures) {
        this.maxAuthQuestionFailures = maxAuthQuestionFailures;
    }

    public long getAuthQuestionLockoutDurationMillis() {
        return this.authQuestionLockoutDurationMillis;
    }

    public void setAuthQuestionLockoutDurationMillis(long millis) {
        this.authQuestionLockoutDurationMillis = millis;
    }

    public long getAuthQuestionLockoutDurationMinutes() {
        return this.authQuestionLockoutDurationMillis / Util.MILLI_IN_MINUTE;
    }

    public void setAuthQuestionLockoutDurationMinutes(long minutes) {
        this.authQuestionLockoutDurationMillis = minutes * Util.MILLI_IN_MINUTE;
    }

    public boolean isPromptForAnswersAfterLogin() {
        return this.promptForAnswersAfterLogin;
    }

    public void setPromptForAnswersAfterLogin(boolean prompt) {
        this.promptForAnswersAfterLogin = prompt;
    }
    
    
    public Set<String> getAddedQuestionIds() {
        return this.addedQuestionIds;
    }
    
    public void setAddedQuestionIds(Set<String> addedQuestionIds) {
        this.addedQuestionIds = addedQuestionIds;
    }
    
    public Set<String> getRemovedQuestionIds() {
        return this.removedQuestionIds;
    }
    
    public void setRemovedQuestionIds(Set<String> removedQuestionIds) {
        this.removedQuestionIds = removedQuestionIds;
    }
    
    public String getSelectedQuestionId() {
        return this.selectedQuestionId;
    }
    
    public void setSelectedQuestionId(String selectedQuestionId) {
        this.selectedQuestionId = selectedQuestionId;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Save the changes to the authentication questions and the configuration
     * settings in the given config.  This is the meat of the save logic but is
     * meant to be called by other save actions - either here or from the
     * LoginConfigBean.
     * 
     * @param  config  The system configuration to save the settings in.
     * 
     * @return False if validation fails, true otherwise.
     */
    @Override
    public boolean save(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        
        // Validate first.
        if (!validate(config, loginConfig)) {
            return false;
        }
        
        SailPointContext ctx = getContext();
        
        // Remove the removed questions - use the Terminator to clean up refs.
        Terminator t = new Terminator(ctx);
        for (String removedId : this.removedQuestionIds) {
            AuthenticationQuestion q =
                ctx.getObjectById(AuthenticationQuestion.class, removedId);
            // This may be null if we added it then removed it.
            if (null != q) {
                t.deleteObject(q);
            }
        }

        // Now reconcile the remaining questions.
        for (AuthenticationQuestionDTO dto : this.questions) {

            // Load existing or create a new one.
            AuthenticationQuestion q = null;
            if (!this.addedQuestionIds.contains(dto.getId())) {
                q = ctx.getObjectById(AuthenticationQuestion.class, dto.getId());
                if (q != null && !Util.nullSafeEq(q.getQuestion(), dto.getQuestion(), true, true)) {
                    this.modifiedQuestions++;
                }
            }
            else {
                q = new AuthenticationQuestion();
            }

            q.setQuestion(dto.getQuestion());
            ctx.saveObject(q);
        }
        
        // Save config settings.
        config.put(Configuration.AUTH_QUESTIONS_ENABLED, 
                   this.authQuestionsEnabled);
        config.put(Configuration.NUM_AUTH_QUESTIONS_FOR_AUTHN,
                   this.numQuestionsForAuthn);
        config.put(Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED,
                   this.numAnswersRequired);
        config.put(Configuration.MAX_AUTH_QUESTION_FAILURES,
                   this.maxAuthQuestionFailures);
        config.put(Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS,
                   this.authQuestionLockoutDurationMillis);
        config.put(Configuration.PROMPT_FOR_AUTH_QUESTION_ANSWERS_AFTER_LOGIN,
                   this.promptForAnswersAfterLogin);

        auditAuthQuestionConfigChanges(config, loginConfig);
        
        return true;
    }

    private static String[][] AUTH_QUEST_CONFIG_ATTRIBUTE_NAMES = {
            {Configuration.AUTH_QUESTIONS_ENABLED , MessageKeys.AUTH_QUESTION_CONF_ENABLED},
            {Configuration.NUM_AUTH_QUESTIONS_FOR_AUTHN , MessageKeys.AUTH_QUESTION_CONF_NUM_QUESTIONS_FOR_AUTHN},
            {Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED , MessageKeys.AUTH_QUESTION_CONF_NUM_ANSWERS_REQUIRED},
            {Configuration.MAX_AUTH_QUESTION_FAILURES , MessageKeys.AUTH_QUESTION_CONF_MAX_AUTH_QUESTION_FAILURES},
            {Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS , MessageKeys.AUTH_QUESTION_CONF_AUTH_QUESTION_LOCKOUT_DURATION_MINUTES},
            {Configuration.PROMPT_FOR_AUTH_QUESTION_ANSWERS_AFTER_LOGIN , MessageKeys.AUTH_QUESTION_CONF_PROMPT_FOR_ANSWERS_AFTER_LOGIN}
    };

    private void auditAuthQuestionConfigChanges(Configuration config, LoginConfigBean loginConfig) {

        if (loginConfig != null) {
            AuditEvent evt = loginConfig.getAuditEvent();
            if (evt != null) {
                //Should be non-null if auditing is enabled
                Map<String, Object> origConfig = loginConfig.getOriginalConfig();
                if (origConfig != null) {
                    Attributes origAtts = (Attributes)origConfig.get(Configuration.OBJ_NAME);
                    if (origAtts != null) {
                        for (int i=0; i<AUTH_QUEST_CONFIG_ATTRIBUTE_NAMES.length; i++) {
                            Object origValue = origAtts.get(AUTH_QUEST_CONFIG_ATTRIBUTE_NAMES[i][0]);
                            Object neuVal = config != null ? config.get(AUTH_QUEST_CONFIG_ATTRIBUTE_NAMES[i][0]) : null;

                            if (!Util.nullSafeEq(origValue, neuVal, true, true)) {
                                evt.setAttribute(AUTH_QUEST_CONFIG_ATTRIBUTE_NAMES[i][1],
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origValue, neuVal));
                            }
                        }
                    }

                }

                //Auth Question Object logging
                if (Util.size(removedQuestionIds) > 0) {
                    evt.setAttribute(MessageKeys.AUTH_QUESTION_REMOVED, Util.size(removedQuestionIds));
                }

                if (Util.size(addedQuestionIds) > 0) {
                    evt.setAttribute(MessageKeys.AUTH_QUESTION_ADDED, Util.size(addedQuestionIds));
                }

                if (this.modifiedQuestions > 0) {
                    evt.setAttribute(MessageKeys.AUTH_QUESTION_MODIFIED, modifiedQuestions);
                }
            }
        }
    }
    
    /**
     * Action to add a new question at the selected index.
     */
    public String addQuestion() throws GeneralException {
        int idx = findSelectedQuestionIndex();
        this.addQuestion(idx+1);
        return "addQuestionSuccess";
    }

    /**
     * Add a new question at the given index.
     */
    private void addQuestion(int idx) {
        String id = Util.uuid();
        this.addedQuestionIds.add(id);
        this.questions.add(idx, new AuthenticationQuestionDTO(id));
    }
    
    /**
     * Action to remove the question at the selected index.
     */
    public void removeQuestion() throws GeneralException {
        
        if (this.questions.size() > 1) {
            this.addedQuestionIds.remove(this.selectedQuestionId);
            this.removedQuestionIds.add(this.selectedQuestionId);
            int idx = this.findSelectedQuestionIndex();
            this.questions.remove(idx);
        }
        
    }

    /**
     * Find the index in the querstions list of the selected question.  This
     * throws if the question is not found in the list.
     */
    private int findSelectedQuestionIndex() throws GeneralException {
        for (int i=0; i<this.questions.size(); i++) {
            if (this.questions.get(i).getId().equals(this.selectedQuestionId)) {
                return i;
            }
        }

        throw new GeneralException("Selected question was not found: " + this.selectedQuestionId);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // VALIDATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Validate the authentication question settings.
     */
    @Override
    public boolean validate(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        
        boolean valid = true;
        
        // Problem #1: blank questions (should we just auto remove these?)
        for (AuthenticationQuestionDTO dto : this.questions) {
            if (Util.isNullOrEmpty(dto.getQuestion())) {
                loginConfig.addMessage(new Message(Type.Error, MessageKeys.ERROR_AUTH_QUESTION_BLANK));
                valid = false;
                break;
            }
        }
        
        // Problem #2: # questions < answers required for authn
        if (this.questions.size() < this.numQuestionsForAuthn) {
            loginConfig.addMessage(new Message(Type.Error, MessageKeys.ERROR_INSUFFICIENT_QUESTIONS_FOR_AUTHN));
            valid = false;
        }
        
        // Problem #3: # questions < # provided answers required
        if (this.questions.size() < this.numAnswersRequired) {
            loginConfig.addMessage(new Message(Type.Error, MessageKeys.ERROR_INSUFFICIENT_QUESTIONS_FOR_REQ_ANSWERS));
            valid = false;
        }
        
        // Problem #4: Duplicate questions.
        Set<String> questions = new HashSet<String>();
        Set<String> dups = new HashSet<String>();
        for (AuthenticationQuestionDTO dto : this.questions) {
            String q = dto.getQuestion();
            if (questions.contains(q) && !dups.contains(q)) {
                loginConfig.addMessage(new Message(Type.Error, MessageKeys.ERROR_DUP_AUTH_QUESTION, q));
                valid = false;
                dups.add(q);
            }
            questions.add(q);
        }

        // Problem #5: # correct answers for auth > # provided answers required
        if (this.numQuestionsForAuthn > this.numAnswersRequired) {
            loginConfig.addMessage(new Message(Type.Error, MessageKeys.ERROR_QUESTIONS_FOR_AUTHN_GT_ANSWERS_REQUIRED));
            valid = false;
        }

        // Problem #6: If question auth is enabled, we need to allow answering
        // questions.
        if ((this.numAnswersRequired < 1) && this.isAuthQuestionsEnabled()) {
            loginConfig.addMessage(new Message(Type.Error, MessageKeys.WARN_AUTH_QUESTIONS_NO_ANSWERS_REQUIRED, this.numAnswersRequired));
            valid = false;
        }

        // Warning : Deleting question may require answers from users
        if (!this.removedQuestionIds.isEmpty()) {
            loginConfig.addWarning(MessageKeys.WARN_REMOVING_AUTH_QUESTIONS_MAY_REQ_ANSWERS);
        }

        // Warning : Increasing # provided answers required may require answers
        //             from users.
        if (this.numAnswersRequired >
            config.getInt(Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED)) {
            loginConfig.addWarning(MessageKeys.WARN_CHANGE_NUM_REQ_MAY_REQ_ANSWERS);
        }

        // Warning : Changing question text should not change the meaning
        checkForChangedQuestions(loginConfig);

        // Warning : Message key not found in catalog
        checkForMissingMessageKeys(loginConfig);
        
        return valid;
    }
    
    /**
     * Look for any questions whose question text has changed.
     */
    private void checkForChangedQuestions(LoginConfigBean loginConfig) throws GeneralException {
        for (AuthenticationQuestionDTO dto : this.questions) {
            if (!this.addedQuestionIds.contains(dto.getId())) {
                AuthenticationQuestion q =
                    getContext().getObjectById(AuthenticationQuestion.class, dto.getId());
                if (!Util.nullSafeEq(dto.getQuestion(), q.getQuestion())) {
                    loginConfig.addMessage(new Message(Type.Warn, MessageKeys.WARN_CHANGE_AUTH_QUESTION_TEXT, q.getQuestion(), dto.getQuestion()));
                }
            }
        }
    }

    /**
     * Add warnings for any questions that look like message keys that aren't
     * found in the message catalog.
     */
    private void checkForMissingMessageKeys(LoginConfigBean loginConfig) {
        for (AuthenticationQuestionDTO dto : this.questions) {
            String key = dto.getQuestion();
            if (Util.smellsLikeMessageKey(key)) {
                String localized = Internationalizer.getMessage(key, Locale.getDefault());
                if (null == localized) {
                    loginConfig.addMessage(new Message(Type.Warn, MessageKeys.WARN_AUTH_QUESTION_MISSING_MSG_KEY, key));
                }
            }
        }
    }

}
