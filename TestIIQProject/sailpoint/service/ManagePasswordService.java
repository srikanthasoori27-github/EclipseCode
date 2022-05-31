package sailpoint.service;

import sailpoint.api.PasswordGenerator;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QuickLink;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.provisioning.ApplicationPolicyExpander;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.link.LinkDTO;
import sailpoint.workflow.IdentityRequestLibrary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling LCM password change requests
 */
public class ManagePasswordService {
    public static final String NEW_PASSWORD = "newPassword";
    public static final String PASSWORD_REQUEST_FLOW = IdentityResetService.Consts.Flows.PASSWORDS_REQUEST_FLOW.value();

    private final QuickLink quickLink;
    private final Identity requester;
    private final Identity requestee;
    private final SailPointContext context;
    private final String externalApp;
    private final String ticketApp;
    private final UserContext userContext;
    private final MessageService messageService;

    /**
     * Constructor.
     * @param quickLink The quicklink authorizing access to the identity
     * @param requester The Identity doing the requesting
     * @param requestee The Identity that was requested for
     * @param externalApp External application
     * @param ticketApp External ticketing application
     * @param context Context to look stuff up in
     */
    public ManagePasswordService(QuickLink quickLink, Identity requester, Identity requestee, String ticketApp, String externalApp,
                           SailPointContext context, UserContext userContext) {
        this.quickLink = quickLink;
        this.requester = requester;
        this.requestee = requestee;
        this.context = context;
        this.externalApp = externalApp;
        this.ticketApp = ticketApp;
        this.userContext = userContext;
        this.messageService = new MessageService(userContext);
    }

    /**
     * Gets the ManagePassword configuration
     * @return ManagePasswordConfigDTO object
     * @throws GeneralException
     */
    public ManagePasswordConfigDTO getConfig() throws GeneralException {
        return new ManagePasswordConfigDTO(this.userContext);
    }

    /**
     * Submits a password change request with a generated password
     * @param link The account to change the password for
     * @param isSelf If the password change is being submitted by the user who owns the account
     * @return An object containing the password request summary
     * @throws GeneralException In a multitude of unrecoverable cases
     */
    public PasswordChangeResultItem submitGeneratePasswordRequest(Link link, boolean isSelf) throws GeneralException {
        String newPassword = generatePassword(Arrays.asList(link));
        return submitSingleAccountPasswordChange(link, null, newPassword, true, isSelf);
    }

    /**
     * Submits a password change request with the given password
     * @param link The account to change the password on
     * @param currentPassword The account's current password
     * @param newPassword The new password to set on the account
     * @param isSelf If the password change is being submitted by the user who owns the account
     * @return An object containing the password request summary
     * @throws GeneralException In a multitude of unrecoverable cases
     */
    public PasswordChangeResultItem submitChangePasswordRequest(Link link, String currentPassword, String newPassword,
                                                                boolean isSelf)
            throws GeneralException {
        return submitSingleAccountPasswordChange(link, currentPassword, newPassword, false, isSelf);
    }

    /**
     * Submits a generate synchronized password for the specified links
     * @param links The links to synchronize the password for
     * @return an object contains the password request summary
     * @throws GeneralException
     */
    public BulkPasswordChangeResult submitGenerateSynchronizedPasswordRequest(List<Link> links) throws GeneralException {
        String generatedPassword = generatePassword(links);
        return submitBulkSynchronizePasswordRequest(links, generatedPassword, null, true);
    }

    /**
     * Submits a password request that generates a unique password for each link
     * @param links The links to generate passwords for
     * @return an object contains the password request summary
     * @throws GeneralException
     */
    public BulkPasswordChangeResult submitGenerateUniquePasswordsRequest(List<Link> links) throws GeneralException {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        Map<Link, String> linkPasswordMap = new HashMap<Link, String>();
        for (Link link : links) {
            String generatedPassword = generatePassword(Arrays.asList(link));
            AccountRequest accountRequest = createAccountRequest(link, null, generatedPassword, true);
            accountRequests.add(accountRequest);
            linkPasswordMap.put(link, generatedPassword);
        }
        WorkflowSession workflowSession = createLCMRequestProcessor().startWorkflow(accountRequests, ManagePasswordService.PASSWORD_REQUEST_FLOW);
        BulkPasswordChangeResult result = createBulkPasswordChangeResult(linkPasswordMap, workflowSession);
        return result;
    }

    /**
     * Submits a password change request that synchronizes all accounts to newPassword
     * @param links The accounts to synchronize
     * @param newPassword The new password
     * @param linkPasswordMap The map of the current passwords to links for verifying the current password
     * @return an object contains the password request summary
     * @throws GeneralException
     */
    public BulkPasswordChangeResult submitSynchronizePasswordRequest(List<Link> links, String newPassword,
                                                                     Map<String, String> linkPasswordMap) throws GeneralException {
        return submitBulkSynchronizePasswordRequest(links, newPassword, linkPasswordMap, false);
    }

    /**
     * create a LCMRequestProcessor object to help start request workflow
     * It seems like for change password this is always Normal priority.
     * @return LCMRequestProcessor object
     */
    private LCMRequestProcessor createLCMRequestProcessor() {
        return new LCMRequestProcessor(this.context, this.requester, this.requestee, this.quickLink, this.ticketApp, this.externalApp, null);
    }
    
    /**
     * Submits a password change for a single account
     * @param link The link to update the password on
     * @param currentPassword The current password, only needed for self-service changes
     * @param newPassword The new password
     * @param isGenerated If the password was provided or generated (properties are set on the resulting attribute request dependent on this fact)
     * @param isSelf If the password change is being submitted by the user who owns the account
     * @return an object contains the password request summary
     * @throws GeneralException
     */
    private PasswordChangeResultItem submitSingleAccountPasswordChange(Link link, String currentPassword, String newPassword,
                                                                       boolean isGenerated, boolean isSelf)
            throws GeneralException {
        validateNewPassword(link, newPassword, isSelf);
        AccountRequest accountRequest = createAccountRequest(link, currentPassword, newPassword, isGenerated);
        WorkflowSession workflowSession = createLCMRequestProcessor().startWorkflow(Arrays.asList(accountRequest), ManagePasswordService.PASSWORD_REQUEST_FLOW);
        PasswordChangeResultItem result = createPasswordChangeResult(link, newPassword, workflowSession);
        return result;
    }

    /**
     * Submits a password change request that synchronizes all accounts to the same password
     * @param links The links to synchronise
     * @param newPassword The new password to use
     * @param linkPasswordMap The map of the current passwords to links for verifying the current password
     * @param isGenerate If the password was provided or generated (properties are set on the resulting attribute request dependent on this fact)
     * @return Successes and failures
     */
    private BulkPasswordChangeResult submitBulkSynchronizePasswordRequest(List<Link> links, String newPassword,
                                                                          Map<String,String> linkPasswordMap, boolean isGenerate) throws GeneralException {
        validateNewPassword(links, newPassword);

        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        for (Link link : links) {
            String currentPassword = null;
            if(linkPasswordMap!=null) {
                currentPassword = linkPasswordMap.containsKey(link.getId()) ? linkPasswordMap.get(link.getId()) : null;

            }
            accountRequests.add(createAccountRequest(link, currentPassword, newPassword, isGenerate));
        }

        WorkflowSession workflowSession = createLCMRequestProcessor().startWorkflow(accountRequests, ManagePasswordService.PASSWORD_REQUEST_FLOW);
        BulkPasswordChangeResult result = createBulkPasswordChangeResult(links, newPassword, workflowSession);
        return result;
    }

    /**
     * Merge password policy constraints of selected links
     * @param selectedLinks list of links selected
     * @return combined password policy constraints
     * @throws PasswordPolicyException
     * @throws GeneralException
     */
    public List<String> mergeConstraints(List<Link> selectedLinks) throws GeneralException {
        PasswordPolicy unifiedPolicy = getMergedPolicy(selectedLinks);
        return unifiedPolicy.convertConstraints(getUserContext().getLocale(), getUserContext().getUserTimeZone(), false);
    }

    /**
     * Get all password policies for the list of links
     * @param links list of links
     * @return list of password policy
     * @throws GeneralException
     */
    private List<PasswordPolicy> fetchPasswordPolicies(List<Link> links) throws GeneralException {
        List<PasswordPolicy> policies = new ArrayList<PasswordPolicy>();
        PasswordPolice police = new PasswordPolice(getContext());
        for (Link link: links) {
            PasswordPolicy policy = police.getEffectivePolicy(link);
            if(policy != null) {
                policies.add(policy);
            }
        }
        return policies;
    }

    /**
     * get user context
     * @return userContext
     */
    private UserContext getUserContext() {
        return this.userContext;
    }

    /**
     * get context object
     * @return {SailPointContext} context
     */
    private SailPointContext getContext() {
        return this.context;
    }
 
    /**
     * Ensures that provided password meets account requirements
     * @param link The link to update the password for
     * @param newPassword The new password to validate
     * @param isSelf Whether we are changing the password for our own account
     * @throws GeneralException Throws PasswordChangeExpception if new password fails the policy check otherwise
     * the exception is probably unhandleable.
     */
    private void validateNewPassword(Link link, String newPassword, boolean isSelf) throws GeneralException {
        PasswordPolice police = new PasswordPolice(context);
        try {
            police.checkPasswordWithHistory(link, newPassword, !isSelf);
        } catch (PasswordPolicyException ex) {
            throw new PasswordChangeException(link.getId(), null, messageService.getLocalizedMessages(ex.getAllMessages()), true);
        }
    }

    /**
     * Validate new password with merged policy from links
     * @param links The links to get policies from
     * @param newPassword The proposed password
     * @throws GeneralException Throws PasswordChangeException if password fails validation
     */
    private void validateNewPassword(List<Link> links, String newPassword) throws GeneralException {
        PasswordPolicy mergedPolicy = getMergedPolicy(links);
        String passwordHistory = getMergedPasswordHistory(links);
        try {
            PasswordPolice police = new PasswordPolice(context);
            police.checkPassword(requestee, mergedPolicy, newPassword, passwordHistory, links);
        } catch (PasswordPolicyException ex) {
            throw new PasswordChangeException(null, null, messageService.getLocalizedMessages(ex.getAllMessages()), true);
        }
    }

    /**
     * Merge all the policies from the links into one super policy
     * @param links The links to merge policies from
     * @return A single merged policy
     * @throws GeneralException If fails trying to getting the effective policy or assimilating the policies
     */
    private PasswordPolicy getMergedPolicy(List<Link> links) throws GeneralException {
        List<PasswordPolicy> passwordPolicies = fetchPasswordPolicies(links);
        PasswordPolicy mergedPolicy = new PasswordPolicy();
        mergedPolicy.assimilatePolicies(passwordPolicies);
        return mergedPolicy;
    }

    /**
     * Merges the password history for all the links
     * @param links The links to merge the history of
     * @return The merged password history as a CSV
     */
    private String getMergedPasswordHistory(List<Link> links) {
        StringBuilder passwordHistory = new StringBuilder();
        for (Link link : links) {
            String linkPasswordHistory = link.getPasswordHistory();
            if(!Util.isNullOrEmpty(linkPasswordHistory)){
                if(passwordHistory.length() != 0) {
                    passwordHistory.append(",");
                }
                passwordHistory.append(linkPasswordHistory);
            }
        }
        return passwordHistory.toString();
    }

    /**
     * Creates the password change result from the workflow
     * @param link The account changed
     * @param newPassword The new password
     * @param workflowSession The workflow session
     * @return All the relevant data bundle up into a PasswordChangeResultItem
     * @throws GeneralException If we can't get the request id
     */
    private PasswordChangeResultItem createPasswordChangeResult(Link link, String newPassword, WorkflowSession workflowSession)
            throws GeneralException {
        WorkflowLaunch launch = workflowSession.getWorkflowLaunch();

        String status = launch == null ? null : launch.getStatus();
        String requestName = getIdentityRequestName(workflowSession);
        List<Message> messages = workflowSession.getLaunchMessages();

        IdentityRequest identityRequest = context.getObjectByName(IdentityRequest.class, requestName);

        // If the launch failed, throw a new password change exception
        if(launch.isFailed()) {
            // default requestDate to now
            Date requestDate = new Date();
            if (identityRequest != null && !Util.isEmpty(identityRequest.getItems()))
                    requestDate = identityRequest.getItems().get(0).getCreated();
            throw new PasswordChangeException(link.getId(), requestDate, messageService.getLocalizedMessages(messages), false);
        }
        /* Values that depend on existing workitem default to null */
        WorkItem wfWorkItem = workflowSession.getWorkItem();
        String workItemType = null;
        String workItemId = null;
        if (wfWorkItem != null) {
            workItemType = wfWorkItem.getType().toString();
            workItemId = wfWorkItem.getId().toString();
        }

        List<PasswordChangeItem> passwordChangeItems = new ArrayList<PasswordChangeItem>();
        LinkDTO linkDTO = new LinkDTO(link);
        if(identityRequest != null) {
            List<IdentityRequestItem> items = identityRequest.getItems();
            if (!Util.isEmpty(items)) {
                /* PasswordChange request represents a single identity request if there should only be one here.  jump
                 * through all these hoops to get the correct status */
                linkDTO.setPasswordChangeDate(items.get(0).getCreated());
                passwordChangeItems.add(new PasswordChangeItem(linkDTO, newPassword, items.get(0).getProvisioningState().toString()));
            }
        } else if(workItemId != null) {
            linkDTO.setPasswordChangeDate(wfWorkItem.getCreated());
            passwordChangeItems.add(new PasswordChangeItem(new LinkDTO(link), newPassword, ApprovalItem.ProvisioningState.Pending.toString()));
        }
        PasswordChangeResultItem result = new PasswordChangeResultItem(status, WorkflowSessionService.stripPadding(requestName), workItemType, workItemId, messages, passwordChangeItems);

        return result;
    }

    /**
     * Creates the password change result from the workflow
     * @param links The accounts changed
     * @param newPassword The new password
     * @param workflowSession The workflow session
     * @return All the relevant data bundle up into a PasswordChangeResultItem
     * @throws GeneralException If we can't get the request id
     */
    private BulkPasswordChangeResult createBulkPasswordChangeResult(List<Link> links, String newPassword, WorkflowSession workflowSession)
            throws GeneralException {
        Map<Link, String> linkPasswordMap = new HashMap<Link, String>();
        for (Link link : links) {
            linkPasswordMap.put(link, newPassword);
        }
        return createBulkPasswordChangeResult(linkPasswordMap, workflowSession);
    }

    /**
     * Creates the password change result from the workflow
     * @param linkPasswordMap a mapping of links to new passwords
     * @param workflowSession The workflow session
     * @return All the relevant data bundle up into a PasswordChangeResultItem
     * @throws GeneralException If we can't get the request id
     */
    private BulkPasswordChangeResult createBulkPasswordChangeResult(Map<Link, String> linkPasswordMap, WorkflowSession workflowSession)
            throws GeneralException {

        /* Values that depend on existing workitem default to null */
        WorkItem wfWorkItem = workflowSession.getWorkItem();
        String workItemType = null;
        String workItemId = null;
        Date workflowItemCreated = null;
        if (wfWorkItem != null) {
            workItemType = wfWorkItem.getType().toString();
            workItemId = wfWorkItem.getId().toString();
            workflowItemCreated = wfWorkItem.getCreated();
        }

        String requestName = getIdentityRequestName(workflowSession);
        IdentityRequest identityRequest = context.getObjectByName(IdentityRequest.class, requestName);
        WorkflowLaunch launch = workflowSession.getWorkflowLaunch();
        String status = launch == null ? null : launch.getStatus();
        List<Message> messages = launch.getMessages();

        BulkPasswordChangeResult result = new BulkPasswordChangeResult(status, WorkflowSessionService.stripPadding(requestName), workItemType, workItemId, messages);

        if(identityRequest == null && workItemId == null) {
            /* The workflow failed before an identity request was created create a PasswordChangeError object */
            List<String> errorMessages = messageService.getLocalizedMessages(messages);
            PasswordChangeError passwordChangeError = new PasswordChangeError(null, errorMessages, false);
            // If no IdentityRequest, the password change request is null and void, so no need to keep a request date.
            result.addFailure(passwordChangeError);
        } else if(identityRequest != null) {
            /* For each item in the identity request create either a PasswordChangeResult or PasswordChangeError */
            LinkService linkService = new LinkService(context);
            for (IdentityRequestItem identityRequestItem : identityRequest.getItems()) {
                // We only care about the items that are related to the password change, not any that include other form-related properties
                if(identityRequestItem.getName().equals(ProvisioningPlan.ATT_PASSWORD)) {
                    Link link = linkService.getMatchingLink(linkPasswordMap.keySet(), identityRequestItem);
                    if (link != null) {
                        LinkDTO linkDTO = new LinkDTO(link);
                        linkDTO.setPasswordChangeDate(identityRequestItem.getCreated());
                        if (identityRequestItem.isProvisioningFailed()) {
                            List<String> errorMessages = messageService.getLocalizedMessages(identityRequestItem.getErrors());
                            PasswordChangeError passwordChangeError = new PasswordChangeError(linkDTO, errorMessages, false);
                            result.addFailure(passwordChangeError);
                        } else {
                            ApprovalItem.ProvisioningState provisioningState = identityRequestItem.getProvisioningState();
                            PasswordChangeItem passwordChangeItem = new PasswordChangeItem(linkDTO, linkPasswordMap.get(link), provisioningState.toString());
                            result.addSuccess(passwordChangeItem);
                        }
                    }
                }
            }
        } else if(workItemId != null) {
            /* We are here because there is no identity request and there is a workitem waiting.  That
             * probably means that there is a form that needs to be filled out.  Dummy up the results
             * so we can render the changed password dialog and set the accounts to in progress */
            String pendingState = ApprovalItem.ProvisioningState.Pending.toString();
            for (Link link : linkPasswordMap.keySet()) {
                LinkDTO linkDTO = new LinkDTO(link);
                linkDTO.setPasswordChangeDate(workflowItemCreated);
                PasswordChangeItem passwordChangeItem = new PasswordChangeItem(new LinkDTO(link), linkPasswordMap.get(link), pendingState);
                result.addSuccess(passwordChangeItem);
            }
         }

        return result;
    }

    /**
     * Generates a password that meets the given link's password policy
     * @param links The links to generate a password for
     * @return The new password
     * @throws GeneralException
     */
    private String generatePassword(List<Link> links) throws GeneralException {
        PasswordGenerator generator = new PasswordGenerator(context);
        PasswordPolicy policy = getMergedPolicy(links);
        String newPassword;
        if(policy == null) {
            newPassword = generator.generateDefaultPassword();
        } else {
            newPassword = generator.generatePassword(policy);
        }
        return newPassword;
    }

    /**
     * Creates a password change account request for the passed link
     * @param link The account to change the password on
     * @param currentPassword The account's current password
     * @param newPassword The new password for the account
     * @return AccountRequest representing the password change
     * @throws GeneralException If an invalid Account Request was created.  For example if the requester does not
     *      have authorization to make the change on the requestee
     */
    private AccountRequest createAccountRequest(Link link, String currentPassword, String newPassword, boolean isGenerated) throws GeneralException {
        AccountRequest accountRequest = new AccountRequest(AccountRequest.Operation.Modify,
                                                link.getApplicationName(),
                                                link.getInstance(),
                                                link.getNativeIdentity());
        AttributeRequest attributeRequest = createAttributeRequest(currentPassword, newPassword, isGenerated);
        accountRequest.add(attributeRequest);
        /* Workflow requires this be set on the account request to process provisioning policy correctly */
        accountRequest.put(ApplicationPolicyExpander.PROVISIONING_POLICIES, Arrays.asList(ApplicationPolicyExpander.CHANGE_PASSWORD_POLICY));
        createLCMRequestProcessor().validate(accountRequest);
        return accountRequest;
    }

    /**
     * Creates an attribute request for the password change
     * @param currentPassword The accounts current password
     * @param newPassword The new password for the account
     * @return AttributeRequest representing the password change
     */
    private AttributeRequest createAttributeRequest(String currentPassword, String newPassword, boolean isGenerated) {
        AttributeRequest attributeRequest = new AttributeRequest(ProvisioningPlan.ATT_PASSWORD,
                                                     ProvisioningPlan.Operation.Set,
                                                     newPassword);
        
        if (isGenerated) {
            attributeRequest.put(ProvisioningPlan.ATT_GENERATED, new Boolean(true));
        }

        if (!Util.isNullOrEmpty(currentPassword)) {
            attributeRequest.put(ProvisioningPlan.ATT_CURRENT_PASSWORD, currentPassword);
        }

        /* For administrative password changes, set the "preExpire" flag in each attribute
         * request so that the user will have to change their password after initial login. */
        if (!isSelfService()) {
            attributeRequest.put(ProvisioningPlan.ATT_PRE_EXPIRE, true);
        }
        return attributeRequest;
    }

    /**
     * Returns true if the requester and requestee are the same
     * @return true if the requester and requestee are the same
     */
    private boolean isSelfService() {
        return requester.equals(requestee);
    }

    /**
     * Returns the name of the identity request for the workflow
     *
     * @param session The workflow session to get the id from
     * @return The name of the IdentityRequest
     * @throws GeneralException If unable to get the workflow's task result
     */
    private String getIdentityRequestName(WorkflowSession session) throws GeneralException {
        String id = null;
        WorkflowCase workflowCase = session.getWorkflowCase();
        String requestId = null;
        if ( workflowCase != null ) {
            id = (String)workflowCase.get(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
            if ( Util.getString(requestId) == null ) {
                TaskResult result = workflowCase.getTaskResult(context);
                if ( result != null ) {
                    id = (String)result.getAttribute(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
                }
            }
        }
        return id;
    }

    /**
     * Exception that is thrown when submitting a password change and that password change fails
     */
    @SuppressWarnings("serial")
    public static class PasswordChangeException extends GeneralException {

        private final String linkId;
        private final List<String> messages;
        private boolean constraintViolation;
        private Date requestDate;

        /**
         *
         * @param linkId The id of the link
         * @param messages The list of error messages
         * @param isConstraintViolation Whether the error was related to a constraint violation or not
         */
        public PasswordChangeException(String linkId, Date requestDate, List<String> messages, boolean isConstraintViolation) {
            this.linkId = linkId;
            this.messages = messages;
            this.constraintViolation = isConstraintViolation;
            this.requestDate = requestDate;
        }

        /**
         * True if the exception was caused by a password policy violation
         * @return True if the exception was caused by a password policy violation
         */
        public boolean isConstraintViolation() {
            return constraintViolation;
        }

        /**
         * The id of the link associated with the exception
         * @return The id of the link associated with the exception
         */
        public String getLinkId() {
            return linkId;
        }

        /**
         * The requestDate reflects the date of a password change.
         * Normally taken from IdentityRequestItem.getCreated()
         * @return The request date of the password change
         */
        public Date getRequestDate() {
            return requestDate;
        }

        /**
         * Messages from the expection
         * @return Messages from the expection
         */
        public List<String> getMessages() {
            return messages;
        }
    }
}
