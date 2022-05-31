package sailpoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Challenger;
import sailpoint.api.IdentityLifecycler;
import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.connector.Connector;
import sailpoint.injection.SailPointContextProvider;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.AuthenticationQuestion;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.SMSResetConfig;
import sailpoint.object.Source;
import sailpoint.object.VerificationToken;
import sailpoint.object.WorkflowLaunch;
import sailpoint.server.Auditor;
import sailpoint.server.Authenticator;
import sailpoint.service.AuthData.AuthQuestionsAuthData;
import sailpoint.service.AuthData.AuthQuestionsAuthData.AuthResponse;
import sailpoint.service.AuthData.AuthType;
import sailpoint.service.AuthData.SMSAuthData;
import sailpoint.service.PasswordReseter.AnswerChecker.Outcome;
import sailpoint.tools.DateService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IdentityLockedException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityLibrary;

/**
 * Identity reset service {@link IdentityResetService} delegates to this service
 * to do the actual password reset.
 * 
 * @author tapash.majumder
 *
 */
public class PasswordReseter {
    
    private static final Log log = LogFactory.getLog(PasswordReseter.class);
    
    private SailPointContext context;
    private DateService dateService;
    
    private Identity identity;
    private Link link;
    private PasswordResetData resetData;
    private AccountUnlockData unlockData;
    private ValidationException validationException = new ValidationException();

    // By default don't report error details
    private boolean reportDetailsErrors = false;
    
    /**
     * 
     * @param context sailpointcontext
     * @param dateService Provides the current date. We inject this so that we may be able to timeshift.
     */
    @Inject
    public PasswordReseter(SailPointContextProvider context, DateService dateService) throws GeneralException {
        this.context = context.get();
        this.dateService = dateService;

        Configuration config = this.context.getConfiguration();
        LoginService loginService = new LoginService(this.context);
        this.reportDetailsErrors = loginService.isDetailedErrorLogging();
    }
    
    /**
     * Resets the password for an identity.
     * 
     * @param identity The identity whose password needs to be reset
     * @param resetData This parameter encapsulates the input required to reset the password
     * @param locale User locale
     * @param timezone User timezone
     * @param smsResetConfig The SMSRestConfig
     * @return
     * @throws GeneralException
     */
    public void reset(Identity identity, PasswordResetData resetData, Locale locale, TimeZone timezone, SMSResetConfig smsResetConfig) throws GeneralException {
        
        this.identity = identity;
        this.resetData = resetData;
        String accountId = null;
        
        if (this.resetData != null && this.resetData.getAccountId() != null) {
            accountId = this.resetData.getAccountId(); 
        }
        
        
        checkLink(accountId);
        
        // validate that the input is set correctly
        this.resetData.validate();
        
        // now check the values supplied
        validateAuthorization(this.resetData.getAuthData(), smsResetConfig);
        
        // If we are here that means the request was valid
        // now check policy
        checkPasswordPolicy();
        
        // if there that policy is also right
        // we can set the password now.
        ProvisioningPlan plan = PasswordResetHelper.buildForgotPasswordPlan(identity, link, null, this.resetData.getPassword());
        launchPasswordResetWorkflow(plan);
        
        // Now delete the validation token, if present
        deleteVerificationToken();
    }

    /**
     * Unlocks a locked passthrough account for identity
     * 
     * @param identity The identity whose password needs to be reset
     * @param accountId the account used for correlating the account.
     * @param unlockData This parameter encapsulates the input required to unlock the password
     * @param locale User locale
     * @param timezone User timezone
     * @param smsResetConfig The SMSRestConfig
     * @return
     * @throws GeneralException
     */
    public void unlock(Identity identity, String accountId, AccountUnlockData unlockData, Locale locale, TimeZone timezone, SMSResetConfig smsResetConfig) throws GeneralException {

        this.identity = identity;
        this.unlockData = unlockData;
        
        checkLink(accountId);

        // validate that the input is set correctly
        this.unlockData.validate();
        
        // now check the values supplied
        validateAuthorization(this.unlockData.getAuthData(), smsResetConfig);
        
        // if here that means we have validated
        // we can unlock now.
        ProvisioningPlan plan = PasswordResetHelper.buildUnlockAccountPlan(identity, link);
        launchUnlockWorkflow(plan);
        
        // Now delete the validation token, if present
        deleteVerificationToken();
    }
    
    /**
     * We don't want to pass a lot of info to the client so will throw a
     * generic exception.
     */
    public static void throwValidationException() throws ValidationException {
        throw new ValidationException(new Message(MessageKeys.RESET_ERR_GENERIC));
    }

    /**
     * By default, messageKey will contain our generic exception, but flexibility
     * is there to change it.
     *
     * @param messageKey
     * @throws ValidationException
     */
    public static void throwValidationException(String messageKey) throws ValidationException {
        throw new ValidationException(new Message(messageKey));
    }

    /**
     * Check that a link supporting pass through exists
     * and supports setting password.
     */
    private void checkLink(String accountId) throws GeneralException {
        
        link = PasswordResetHelper.getPassthroughLink(context, identity, null, accountId);
        if (link == null) {
            if (log.isWarnEnabled()) {
                log.warn("Passthru link not found for identity: " + identity.getName());
            }
            throwValidationException();
        } else {
            boolean valid = PasswordResetHelper.supportsSetPassword(context, link);
            if (!valid) {
                if (log.isWarnEnabled()) {
                    log.warn("Passthru app does not support setting password.");
                }
                throwValidationException();
            }
        }
    }
    
    /**
     * This will validate the sms code or the auth questions.
     * @param smsResetConfig 
     */
    private void validateAuthorization(AuthData authData, SMSResetConfig smsResetConfig) throws GeneralException {
        if (authData.getType() == AuthType.SMS) {
            validateSMS(authData.getSmsAuthData(), smsResetConfig);
        } else { 
            validateAuthQuestions(authData.getAuthQuestionsAuthData());
        } 
    }
    
    private void validateSMS(SMSAuthData smsAuthData, SMSResetConfig smsResetConfig) throws GeneralException {
        String resetToken = smsAuthData.getAuthToken();
        
        //obtain the lock on the Identity.
        //This is required when check/update maxFaileAttempts
        Identity locked = null;

        try {
            locked = ObjectUtil.lockIdentity(context, identity);
            
            VerificationToken userToken = locked.getVerificationToken();
            if (userToken == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Identity has no verification token.");
                }
                throwValidationException();
            }

            int maxFailedAttemps = smsResetConfig == null || smsResetConfig.getMaxFailedAttempts() <= 0 ? 
                                         SMSResetConfig.DEFAULT_MAX_FAILED_ATTEMPTS :
                                             smsResetConfig.getMaxFailedAttempts();
            
            if (userToken.getFailedAttempts() >= maxFailedAttemps) {
                if (log.isWarnEnabled()) {
                    log.warn("Exceeds SMS Max Failed Attempts.");
                }
                //IIQHH-513 -- only show max attempts error message if the valid token is entered after lockout.
                if (userToken.getTextCode() != null && userToken.getTextCode().equals(resetToken)) {
                    throw new ValidationException(new Message(MessageKeys.RESET_ERR_MAX_FAILED_ATTEMPTS_REACHED));
                } else {
                    throwValidationException();
                }
            }
            
            if (userToken.getTextCode() == null) {
                if (log.isWarnEnabled()) {
                    log.warn("No text code present for identity.");
                }
                throwValidationException();
            }
            
            if (dateService.getCurrentDate().getTime() >= userToken.getExpireDate().getTime()) {
                if (log.isInfoEnabled()) {
                    log.info("Token expired: " + userToken.getTextCode());
                }
                throwValidationException();
            }

            boolean match = userToken.getTextCode().equals(resetToken);
            if (!match) {
                //update failedAttempts
                userToken.setFailedAttempts(userToken.getFailedAttempts() + 1);
                context.saveObject(locked);
                
                if (log.isInfoEnabled()) {
                    log.info("Token mismatch");
                }
                throwValidationException();
            }
        } 
        finally {
            // releasing the lock will also commit the Identity 
            if (locked != null) {
                ObjectUtil.unlockIdentity(context, locked);
            }
        }
    }
    
    private void validateAuthQuestions(AuthQuestionsAuthData authQuestionsAuthData) throws GeneralException {
        AnswerChecker checker = new AnswerChecker(context);
        Outcome outcome = checker.checkAnswers(identity, null, convertAuthResponsesToAuthticationAnswers(authQuestionsAuthData));
        if (outcome == Outcome.Success) {
            return;
        }

        // if here then outcome was failure so we will add the list of failures
        // to the exception
        // log the error message and throw generic error message
        if (log.isInfoEnabled()) {
            log.info("Question validation failed: " + checker.getErrorMessages());
        }

        if (this.reportDetailsErrors) {
            throw new ValidationException(checker.getErrorMessages());
        } else {
            throwValidationException();
        }

    }

    private void checkPasswordPolicy() throws GeneralException {
        boolean valid = false;
        
        Pair<Boolean, List<Message>> checkResult = PasswordResetHelper.checkPasswordPolicy(context, link, resetData.getPassword());
        valid = checkResult.getFirst();
        
        if (!valid) {
            for (Message message : checkResult.getSecond()) {
                validationException.addMessage(message);
            }
            throw validationException;
        }
    }
    
    /**
     * Launch the workflow configured to handle password change.
     * It will check the status of the workflow launch and return failure if the 
     * launch failed.
     */
    private void launchPasswordResetWorkflow(ProvisioningPlan plan) throws GeneralException {
        
        WorkflowLaunch launch = PasswordResetHelper.launchIdentityWorkflow(
                context, 
                plan, 
                identity, 
                IdentityResetService.Consts.Flows.FORGOT_PASSWORD_FLOW.value(), null);
        if (launch == null || launch.isFailed()) {
            throw new GeneralException(new Message(MessageKeys.PASSWORD_RESET_WORKFLOW_FAILED));
        } 
    }

    /**
     * Launch the workflow configured to unlock an identity.
     * It will check the status of the workflow launch and return failure if the 
     * launch failed.
     */
    private void launchUnlockWorkflow(ProvisioningPlan plan) throws GeneralException {
        
        WorkflowLaunch launch = PasswordResetHelper.launchIdentityWorkflow(
                context, 
                plan, 
                identity, 
                IdentityResetService.Consts.Flows.UNLOCK_ACCOUNT_FLOW.value(), null);
        if (launch == null || launch.isFailed()) {
            throw new GeneralException(new Message(MessageKeys.PASSWORD_RESET_UNLOCK_WORKFLOW_FAILED));
        } 
    }
    
    private void deleteVerificationToken() throws GeneralException {
        if (identity.getVerificationToken() != null) {
            identity.setVerificationToken(null);
            context.saveObject(identity);
            context.commitTransaction();
        }
    }
    
    private List<AuthenticationAnswer> convertAuthResponsesToAuthticationAnswers(AuthQuestionsAuthData authQuestionsAuthData) throws GeneralException {
        List<AuthenticationAnswer> authAnswers = new ArrayList<AuthenticationAnswer>();
        
        List<AuthResponse> responses = authQuestionsAuthData.getResponses();
        for (AuthResponse response : responses) {
            authAnswers.add(convertToAuthAnswer(response));
        }
        
        return authAnswers;
    }
    
    private AuthenticationAnswer convertToAuthAnswer(AuthResponse response) throws GeneralException {
        AuthenticationAnswer answer = new AuthenticationAnswer();
        
        answer.setQuestion(context.getObjectById(AuthenticationQuestion.class, response.getId()));
        answer.setAnswer(response.getAnswer());
        
        return answer;
    }
    
    /**
     * This class encapsulates some helper methods
     * which are used by LoginBean etc.
     * This contains common logic used on both places.
     * 
     * @author tapash.majumder
     *
     */
    public static class PasswordResetHelper {
        
        /**
         * Checks the password meets the password policy.
         * Please see {@link PasswordPolice#checkPassword(Link, String)} for more details
         * 
         * It returns a Pair with first parameter as boolean with true for success
         * if validation fails the second parameter will have error messages why the validation failed.
         * 
         * @param context SailPointContext
         * @param link the passthrought app
         * @param password the new password to be set
         */
        public static Pair<Boolean, List<Message>> checkPasswordPolicy(SailPointContext context, Link link, String password) {
            
            boolean validated = true;
            List<Message> errorMessages = new ArrayList<Message>();
            
            try {
                PasswordPolice police = new PasswordPolice(context);
                police.checkPassword(link, password);
            } catch (PasswordPolicyException pe) {
                validated = false;
                for (Message msg : pe.getAllMessages()) {
                    errorMessages.add(msg);
                }
            } catch (GeneralException ge) {
                validated = false;
                errorMessages.add(ge.getMessageInstance());
            }

            return new Pair<Boolean, List<Message>>(validated, errorMessages); 
        }

        /**
         * Make sure that the passthrough app has an integration config that supports setPassword
         */
        public static boolean supportsSetPassword(SailPointContext context, Link passthruLink) throws GeneralException {
            return new LinkService(context).supportsSetPassword(passthruLink);
        }
        
        /**
         * Get the first defined passthrough link for the identity.
         * 
         * @param context SailPointContext
         * @param user the user
         * @param appName The application where to look for the link
         * @param nativeId The account in the aforementioned app
         */
        public static Link getPassthroughLink(SailPointContext context, Identity user, String appName, String nativeId) throws GeneralException {
            
            List<Application> apps = null;
            if (appName == null || appName.isEmpty()) {
                Authenticator authMan = new Authenticator(context);
                
                apps = authMan.getAuthApplications();
            } else {
                Application authApp = context.getObjectByName(Application.class, appName);
                apps = new ArrayList<Application>();
                if (authApp != null) {
                    apps.add(authApp);
                }
            }
            
            IdentityService identSvc = new IdentityService(context);
            for (Application app : apps) {
                List<Link> links = identSvc.getLinks(user, app);
                for (Link link : links) {
                    if (nativeId == null || nativeId.length() == 0) {
                        return link;
                    } else {
                        if (nativeId.equalsIgnoreCase(link.getNativeIdentity()) ||
                            nativeId.equalsIgnoreCase(link.getDisplayName()) ||
                            isAuthSearchAttributeMatch(app, nativeId, link)) {
                            return link;
                        }
                    }
                } 
            }
            
            return null;
        }

        /**
         * Helper for getPassthroughLink, return true if the the link has a matching
         * value for one of the attributes configured as authentication pass through
         * attributes.
         */
        private static boolean isAuthSearchAttributeMatch(Application app, String nativeId, Link link) {
            boolean match = false;
            List<String> names = app.getListAttributeValue(Connector.CONFIG_AUTH_SEARCH_ATTRIBUTES);
            for (String name : Util.iterate(names)) {
                Object value = link.getAttribute(name);
                if (value != null) {
                    if (nativeId.equalsIgnoreCase(value.toString())) {
                        match = true;
                        break;
                    }
                }
            }
            return match;
        }

        /**
         * 
         * @param user the user whose password is being changed
         * @param passThroughLink the linked app where the password is being changed
         * @param currentPassword unencrypted
         * @param newPassword unencrypted
         * @return A provisioningPlan which can be passed to workflow
         */
        public static ProvisioningPlan buildForgotPasswordPlan(
                Identity user, 
                Link passThroughLink,
                String currentPassword,
                String newPassword) throws GeneralException {
            
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setIdentity(user);
            plan.addRequester(user);
            plan.setSource(Source.LCM);
            
            AccountRequest account = new AccountRequest();
            account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            account.setApplication(passThroughLink.getApplicationName());
            account.setNativeIdentity(passThroughLink.getNativeIdentity());
            plan.add(account);
            
            AttributeRequest req = new AttributeRequest();
            req.setName(ProvisioningPlan.ATT_PASSWORD);
            req.setOperation(ProvisioningPlan.Operation.Set);
            
            req.setValue(newPassword);
            
            // If the old password is available, send it in the plan.
            if (null != Util.getString(currentPassword)) {
                req.put(ProvisioningPlan.ATT_CURRENT_PASSWORD, currentPassword);
            }
            
            account.add(req);
            return plan;
        }

        /**
         * 
         * @param user the user whose password is being changed
         * @param passThroughLink the linked app which is being unlocked.
         * @return A provisioningPlan which can be passed to workflow
         */
        public static ProvisioningPlan buildUnlockAccountPlan(
                Identity user, 
                Link passThroughLink) throws GeneralException {
            
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setIdentity(user);
            plan.addRequester(user);
            plan.setSource(Source.LCM);
            
            AccountRequest account = new AccountRequest();
            account.setOperation(ProvisioningPlan.AccountRequest.Operation.Unlock);
            account.setApplication(passThroughLink.getApplicationName());
            account.setNativeIdentity(passThroughLink.getNativeIdentity());
            plan.add(account);

            return plan;
        }
        
        /**
         * Launches an identity workflow based on flowName
         * 
         * @param context the SailPointContext
         * @param plan Provisioning plan containg all information to change password
         * @param user user whose password is being changed.
         * @param flow the flowName
         * @param variables workflowVariables to set
         * @return
         * @throws GeneralException
         */
        public static WorkflowLaunch launchIdentityWorkflow(SailPointContext context,
                ProvisioningPlan plan,
                Identity user,
                String flow, 
                Attributes<String, Object> variables) throws GeneralException {

            String workflowName = ObjectUtil.getLCMWorkflowName(context, flow);
            if ( workflowName == null ) {
                throw new GeneralException(new Message(MessageKeys.PASSWORD_RESET_MISSING_WORKFLOW, workflowName));
            }
            
            Attributes<String,Object> inputs = new Attributes<String,Object>();
            if (variables != null) {
                inputs.putAll(variables);
            }
            inputs.put(IdentityLibrary.VAR_FLOW, flow);
            inputs.put(IdentityLibrary.VAR_IDENTITY_NAME, user.getName());
            inputs.put(IdentityLibrary.VAR_PLAN, plan);

            IdentityLifecycler cycler = new IdentityLifecycler(context);
            WorkflowSession ses = cycler.launchUpdate(user.getName(), plan.getIdentity(), plan, workflowName, inputs);        
            WorkflowLaunch launch = null;
            if ( ses != null ) {
                launch = ses.getWorkflowLaunch();
            }
            return launch;
        }

        /**
         * Invalidates the current session.
         */
        public static void logoutUser(HttpSession session) {
            session.removeAttribute(PageAuthenticationService.ATT_PRINCIPAL);
            session.removeAttribute(PageAuthenticationFilter.ATT_CAPABILITIES);
            session.removeAttribute(PageAuthenticationFilter.ATT_RIGHTS);

            session.invalidate();
        }
    }
    
    /**
     * Encapsulates logic to get User Authentication 
     * Questions.
     * 
     * @author tapash.majumder
     */
    public static class AuthQuestionsHelper {
        
        /**
         * Keys used for serialization /deserialization to map
         * @author tapash.majumder
         *
         */
        public static final class Keys {
            public static final String ID = "id";
            public static final String TEXT = "text";
        }
        
        /**
         * Encapsulates a question.
         * @author tapash.majumder
         *
         */
        
        public static class Question {
            private String id;
            private String text;
            
            public String getId() {
                return id;
            }
            
            public void setId(String id) {
                this.id = id;
            }
            
            public String getText() {
                return text;
            }
            
            public void setText(String text) {
                this.text = text;
            }
            
            public Question(String id, String text) {
                this.id = id;
                this.text = text;
            }
            
            /**
             * Construct from a map.
             */
            public Question(Map<String, String> map) {
                this(map.get(Keys.ID), map.get(Keys.TEXT));
            }

            public Map<String, String> toMap() {
                Map<String, String> val = new HashMap<String, String>();
                
                val.put(Keys.ID, id);
                val.put(Keys.TEXT, text);
                
                return val;
            }
            
            public static Question fromAuthenticationQuestion(AuthenticationQuestion authQuestion, Locale locale) {
                return new Question(
                        authQuestion.getId(), 
                        getLocalizedQuestionText(authQuestion.getQuestion(), locale));
            }
        }
        
        private SailPointContext context;
        private LazyLoad<Integer> minRequired;
        private LazyLoad<Integer> numDefined;
        
        public AuthQuestionsHelper(SailPointContext context) {
            this.context = context;
            // we use lazyloading pattern so that we load 
            // these values only once after they are called.
            minRequired = new LazyLoad<Integer>(new ILazyLoader<Integer>() {
                @Override
                public Integer load() throws GeneralException {
                    return loadMinimumRequired();
                }
            });
            numDefined = new LazyLoad<Integer>(new ILazyLoader<Integer>() {
                @Override
                public Integer load() throws GeneralException {
                    return loadNumDefined();
                }
            });
        }

        /**
         * Returns a list of questions set by the identity.
         * If questions are not set, it will return a set of
         * fake questions.
         */
        public List<Question> getQuestions(Identity identity, Locale locale) throws GeneralException {
            if (identity == null) {
                return getFakeQuestions(locale);
            }
            List<AuthenticationAnswer> answers = identity.getAuthenticationAnswers();
            if (Util.isEmpty(answers)) {
                // avoid phishing
                return getFakeQuestions(locale);
            }
            answers = identity.getAuthenticationAnswers();
            if (answers.size() < getMinimumRequired()) {
                // avoid phishing
                return getFakeQuestions(locale);
            }

            List<Question> questions = new ArrayList<Question>();
            
            for (AuthenticationAnswer answer : answers) {
                Question question = new Question(
                        answer.getQuestion().getId(), 
                        getLocalizedQuestionText(answer.getQuestion().getQuestion(), locale));
                questions.add(question);
            }
            
            return questions;
        }
        
        /**
         * Converts a list of questions to a list of maps
=         */
        public static List<Map<String, String>> toListMap(List<Question> questions) {
            List<Map<String, String>> listMap = new ArrayList<Map<String,String>>();
            
            for (Question question : questions) {
                listMap.add(question.toMap());
            }
            
            return listMap;
        }

        /**
         * if an identity is not found we still want to return a set of 
         * questions so that the phisher doesn't know the difference.
         * We will return {@link Configuration#NUM_AUTH_QUESTIONS_FOR_AUTHN} questions.
         *  
         */
        private List<Question> getFakeQuestions(Locale locale) throws GeneralException {
            List<Question> questions = new ArrayList<Question>();
            
            List<AuthenticationQuestion> sysQuestions = context.getObjects(AuthenticationQuestion.class);
            if (sysQuestions == null) {
                // highly unlikely we will get here
                // but throw an exception nevertheless
                throw new GeneralException(new Message(MessageKeys.PASSWORD_RESET_NO_QUESTIONS));
            }
            
            // shuffle it so that we don't get the same answers every time
            Collections.shuffle(sysQuestions);

            // return as many questions as are set in sysconfig for validation requirement
            int numToReturn = getNumDefined();
            int numCollected = 0;
            for (AuthenticationQuestion sysQuestion : sysQuestions) {
                questions.add(Question.fromAuthenticationQuestion(sysQuestion, locale));
                ++numCollected;
                if (numCollected == numToReturn) {
                    break;
                }
            }
            
            return questions;
        }

        public int getMinimumRequired() throws GeneralException {
            return minRequired.getValue();
        }
        
        /**
         *  return as many questions as are set in sysconfig for validation requirement.
         *  Note the name of method is 'load' and not 'get' because it will lookup the
         *  system config property.
         *
         * @return number of questions required to pass authentication
         * @throws GeneralException
         */
        private int loadMinimumRequired() throws GeneralException {
            Configuration config = context.getConfiguration();
            return config.getInt(Configuration.NUM_AUTH_QUESTIONS_FOR_AUTHN);
        }

        public int getNumDefined() throws GeneralException {
            return numDefined.getValue();
        }

        /**
         * Returns the number defined in sysconfig for NUM_AUTH_QUESTION_ANSWERS_REQUIRED,
         * which is used to populate the correct number of questions in getFakeQuestions()
         * so as to avoid phishing when answering questions with an invalid ID.
         *
         * @return number of questions each user should have answered
         * @throws GeneralException
         */
        public int loadNumDefined() throws GeneralException {
            Configuration config = context.getConfiguration();
            return config.getInt(Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED);
        }
        
        /**
         * Try to find localized message, failing which it will return
         * the key.
         * @param key the message key
         * @param locale user locale
         */
        private static String getLocalizedQuestionText(String key, Locale locale) {
            String result = Internationalizer.getMessage(key, locale);
            if (result == null) {
                result = key;
            }
            return result;
        }
    }
    
    /**
     * Encapsulates the logic to check answers.
     * 
     * @author tapash.majumder
     *
     */
    public static class AnswerChecker {

        public enum Outcome {
            StayOnPage,
            Fail,
            Success
        }
        
        private SailPointContext context;
        private Challenger challenger;
        private Identity identity;
        private String accountId;
        private List<AuthenticationAnswer> answers;
        private Outcome outcome;
        private List<Message> errorMessages;
        
        public AnswerChecker(SailPointContext context) {
            this.context = context;
            this.challenger = new Challenger(context);
        }
        
        public List<Message> getErrorMessages() {
            return errorMessages;
        }
        
        /**
         * Used when 'Forgot Password'
         * Check answers to make sure they match the answers previously provided.
         * Forward on to 'reset password' page
         * 
         * This will return an OutCome enumeration. This is useful mainly for navigation.
         * Outcome.Success means everything happened as expected.
         * Outcome.StayOnPage means don't navigate to next page
         * Outcome.Fail means something bad happened and we may need to navigate to another page.
         */
        public Outcome checkAnswers(Identity identity, String accountId, List<AuthenticationAnswer> authAnswers) throws GeneralException {
            this.identity = identity;
            this.accountId = accountId;
            this.answers = authAnswers;

            errorMessages = new ArrayList<Message>();

            check();
            
            return outcome;
        }

        // checks and audits
        private void check() throws GeneralException {
            if (challenger.isLockedOut(identity)) {
                errorMessages.add(new Message(Type.Error, MessageKeys.IDENTITY_LOCKED_OUT_ERROR));
                outcome = Outcome.StayOnPage;
                return;
            }

            if (!checkForDuplicate()) {
                return;
            }
            
            int numCorrectRequired = challenger.getNumCorrectAnswersRequired();
            
            // Check if we were provided enough answers.
            if (answers.size() < numCorrectRequired) {
                errorMessages.add(new Message(Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_MIN_ANSWERS, numCorrectRequired));
                outcome = Outcome.Fail;
                return;
            }
            
            AuditEvent event = new AuditEvent();

            try {
                if (!challenger.authenticate(identity, answers)) {
                    if (Util.isEmpty(identity.getAuthenticationAnswers())) {
                        errorMessages.add(new Message(Type.Error, MessageKeys.AUTH_ANSWERS_NOT_CONFIGURED));
                    } else {
                        errorMessages.add(new Message(Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_AUTH_FAILED));
                    }
                    outcome = Outcome.Fail;
                    event.setAction(AuditEvent.AuthAnswerIncorrect);
                    event.setTarget(accountId == null? identity.getName() : accountId);
                }
                else {
                    outcome = Outcome.Success;
                }
            }
            catch (IdentityLockedException e) {
                errorMessages.add(new Message(Type.Error, MessageKeys.IDENTITY_LOCKED_OUT_ERROR));
                outcome = Outcome.StayOnPage;
                event.setAction(AuditEvent.IdentityLocked);
                event.setTarget(accountId == null ? identity.getName() : accountId);
            }
            
            if ( Auditor.isEnabled(event.getAction()) ) {
                Auditor.log(event);
                context.commitTransaction();
            }
        }

        // check if two answers have the same question id
        private boolean checkForDuplicate() {
            Set<String> enteredAnswers = new HashSet<String>();
            int answer_index = 1;
            
            for (AuthenticationAnswer answer : answers) {
                // bug#17419 - don't allow duplicate questions
                String questionId = answer.getQuestion().getId();
                if (enteredAnswers.contains(questionId)) {
                    errorMessages.add(new Message(Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_DUPLICATE_QUESTION, answer_index));
                    outcome = Outcome.Fail;
                    return false;
                }
                
                enteredAnswers.add(questionId);
                answer_index++;
            }
            
            return true;
        }
        
    }
}
