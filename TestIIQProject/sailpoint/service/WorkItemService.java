package sailpoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attachment;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Comment;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LimitReassignmentException;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.SelfCertificationException;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItem.State;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.ViolationReviewWorkItemDTO;
import sailpoint.web.workitem.WorkItemDTO;
import sailpoint.web.workitem.WorkItemDTOFactory;
import sailpoint.workflow.IdentityRequestLibrary;

/**
 * Service to interact with work items.  Should be extended for anything specific
 * to a single type of work item. 
 */
public class WorkItemService extends BaseObjectService<WorkItem> {
    private static Log log = LogFactory.getLog(WorkItemService.class);
    
    public static final String PATCH_FIELD_PRIORITY = "priority";
    public static final String ATTACHMENTS = "attachments";
    
    private static String ATT_IDENTITY_NAME = IdentityRequestLibrary.ARG_IDENTITY_NAME;
    private static String ATT_IDENTITY_DISPLAY_NAME = IdentityRequestLibrary.ARG_IDENTITY_DISPLAY_NAME;
    
    private WorkItem workItem;
    private SailPointContext context;
    private UserContext userContext;

    /**
     * Constructor
     * @param workItemId WorkItem ID
     * @param userContext UserContext
     * @throws GeneralException
     */
    public WorkItemService(String workItemId, UserContext userContext) throws GeneralException {
        // bug 24090 - In this case, only work item owners can change the work item
        this(workItemId, userContext, false);
    }

    /**
     * Constructor
     * @param workItemId WorkItem ID
     * @param userContext UserContext
     * @param allowRequester - optionally, grant the requester privileges
     * @throws GeneralException
     */
    public WorkItemService(String workItemId, UserContext userContext, boolean allowRequester) throws GeneralException {
        if (Util.isNullOrEmpty(workItemId)) {
            throw new InvalidParameterException("workItemId");
        }
       
        initContext(userContext);
        
        this.workItem = this.context.getObjectById(WorkItem.class, workItemId);
        if (this.workItem == null) {
            throw new ObjectNotFoundException(WorkItem.class, workItemId);
        }

        authorize(allowRequester);
    }

    /**
     * Constructor
     * @param workItem The work item.
     * @param userContext The user context.
     * @throws GeneralException
     */
    public WorkItemService(WorkItem workItem, UserContext userContext) throws GeneralException {
        this(workItem, userContext, false);
    }

    /**
     * Constructor
     * @param workItem The work item.
     * @param userContext The user context.
     * @param allowRequester True if requester should have access to the work item.
     * @throws GeneralException
     */
    public WorkItemService(WorkItem workItem, UserContext userContext, boolean allowRequester) throws GeneralException {
        if (workItem == null) {
            throw new InvalidParameterException("workItem");
        }

        initContext(userContext);

        this.workItem = workItem;

        authorize(allowRequester);
    }

    /**
     * Constructor
     * @param sessionStorage The session storage which contains a WorkflowSession with a WorkItem.
     * @param userContext The user context.
     * @throws GeneralException
     */
    public WorkItemService(SessionStorage sessionStorage, UserContext userContext) throws GeneralException {
        this(sessionStorage, userContext, false);
    }

    /**
     * Constructor
     * @param sessionStorage The session storage which contains a WorkflowSession with a WorkItem.
     * @param userContext The user context.
     * @param allowRequester True if requester should have access to the work item.
     * @throws GeneralException
     */
    public WorkItemService(SessionStorage sessionStorage, UserContext userContext, boolean allowRequester) throws GeneralException {
        WorkflowSession wfSession = (WorkflowSession) sessionStorage.get(WorkflowSessionService.ATT_WORKFLOW_SESSION);
        if (wfSession == null) {
            throw new GeneralException("No workflow session found");
        }

        initContext(userContext);

        this.workItem = wfSession.getWorkItem();
        if (this.workItem == null) {
            throw new GeneralException(("No work item found on the session"));
        }

        authorize(allowRequester);
    }

    /**
     * Initialize the context
     * @param userContext UserContext
     * @throws GeneralException
     */
    private void initContext(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        this.userContext = userContext;
        this.context = userContext.getContext();

    }

    /**
     * Get the work item
     * @return WorkItem
     */
    public WorkItem getWorkItem() {
        return this.workItem;
    }

    /**
     * Get the work item DTO
     * @return WorkItemDTO
     */
    public WorkItemDTO getWorkItemDTO() throws GeneralException {
        WorkItemDTOFactory factory = new WorkItemDTOFactory(userContext);
        return factory.createWorkItemDTO(getWorkItem());
    }

    /**
     * Get the context
     * @return SailPointContext
     */
    public SailPointContext getContext() {
        return this.context;
    }

    /**
     * Get the user context
     * @return UserContext
     */
    public UserContext getUserContext() {
        return this.userContext;
    }

    /**
     * Authorize the user context against the work item. Only owners are allowed
     * to make decisions, no requesters.
     * @throws GeneralException
     */
    public void authorize() throws GeneralException {
        //only owners are allowed to make decisions, not requesters.
        authorize(false);
    }

    /**
     * Authorize user to make actions on work item. System admin does not get action privileges.
     */
    public void authorizeActions() throws GeneralException {
        if (!isEditable(userContext.getLoggedInUser())) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.ERR_WORK_ITEM_UNAUTHORIZED_ACCESS_EXCEPTION));
        }
    }
    
    /**
     * Authorize the user context against the work item and optionally allow the requester to
     * do things like add comments
     * @param allowRequester - boolean to allow requester of work item to make changes. Currently
     * used to allow requester to add comments.
     * @throws GeneralException
     */
    public void authorize(boolean allowRequester) throws GeneralException {
        // bug 24090 - optionally authorize the requester to do things like add comments
        WorkItemAuthorizer authorizer = new WorkItemAuthorizer(this.workItem, allowRequester);
        authorizer.authorize(this.userContext);
    }
    
    @Override
    protected WorkItem getObject() {
        return getWorkItem();
    }
    
    @Override
    protected void patchValue(String field, Object value) throws GeneralException {
        if (PATCH_FIELD_PRIORITY.equals(field)) {
            WorkItem.Level priority = (value instanceof WorkItem.Level) ? (WorkItem.Level)value : 
                    WorkItem.Level.valueOf((String)value);
            getWorkItem().setLevel(priority);
        }
    }
    
    @Override
    protected List<String> getAllowedPatchFields() {
        List<String> fields = new ArrayList<String>();
        if (Util.otob(Configuration.getSystemConfig().getBoolean(Configuration.WORK_ITEM_PRIORITY_EDITING_ENABLED))) {
            fields.add(PATCH_FIELD_PRIORITY);
        }
        
        return fields;
    }

    @Override
    protected boolean validateValue(String field, Object value) {
        boolean result = true;
        if (PATCH_FIELD_PRIORITY.equals(field)) {
            if (value instanceof WorkItem.Level) {
                return true;
            } else if (value instanceof String) {
                for (WorkItem.Level level : WorkItem.Level.values()) {
                    if (level.name().equals(value)) {
                        return true;
                    }
                }
            }
            result = false;
        }
        
        // allow subclasses to validate other fields by returning true if field is not PATCH_FIELD_PRIORITY
        return result;
    }
    /**
     * Get policy violation stub from the policy violation matching the given rule
     * @param ruleName Rule name to match
     * @return PolicyViolation object stub
     * @throws GeneralException
     */
    public PolicyViolation getPolicyViolationStub(String ruleName) throws GeneralException {
        return getPolicyViolationStub(null, ruleName);
    }

    /**
     * Get policy violation stub from the policy violation matching the given rule
     * @param policyName Policy name to match, or null to skip policy name match
     * @param ruleName Rule name to match
     * @return PolicyViolation object stub
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public PolicyViolation getPolicyViolationStub(String policyName, String ruleName) throws GeneralException {
        PolicyViolation violation = null;
        // make sure we have a rule name        
        if (ruleName == null) {
            throw new InvalidParameterException("ruleName");
        }

        // now find the violation in the work item with the given rule name
        Map<String, Object> violationMap = null;
        if (getWorkItem().getAttributes() != null) {
            List<Map<String, Object>> attributeViolations = getWorkItem().getAttributes().getList(WorkItem.ATT_POLICY_VIOLATIONS);
            if (!Util.isEmpty(attributeViolations)) {
                for (Map<String, Object> attributeViolation : attributeViolations) {
                    if (Util.nullSafeEq(ruleName, attributeViolation.get("ruleName")) &&
                            (policyName == null ||
                                    Util.nullSafeEq(policyName, attributeViolation.get("policyName")))) {
                        violationMap = attributeViolation;
                        break;
                    }
                }
            }
        }

        return convertPolicyViolationMap(violationMap);
    }

    /**
     * Get the ViolationDetailer for the policy violation map 
     * @param policyName Policy name to match
     * @param ruleName Rule name to match
     * @return ViolationDetailer 
     * @throws GeneralException
     */
    public ViolationDetailer getViolationDetails(String policyName, String ruleName) throws GeneralException {
        ViolationDetailer detailer = null;
        PolicyViolation violation = getPolicyViolationStub(policyName, ruleName);
        if  (violation != null) {
            detailer = new ViolationDetailer(getContext(), violation, getUserContext().getLocale(), getUserContext().getUserTimeZone());
        }
        return detailer;
    }

    /**
     * Convert the policy violation map on the work item to a PolicyViolation stub 
     * @param violationMap Map of values representing policy violation
     * @return PolicyViolation object stub
     * @throws GeneralException
     */
    private PolicyViolation convertPolicyViolationMap(Map<String, Object> violationMap) throws GeneralException {

        PolicyViolation violation = null;
        if (violationMap != null) {
            violation = new PolicyViolation();
            String owner = (String)violationMap.get("policyOwner");
            violation.setOwner(getContext().getObjectByName(Identity.class, owner));
            violation.setPolicyName((String)violationMap.get("policyName"));
            violation.setConstraintName((String)violationMap.get("ruleName"));
            violation.setDescription((String)violationMap.get("description"));
            if (violation.getDescription() == null)
                violation.setDescription((String)violationMap.get("constraintDescription"));
            //IIQTC-23 :- Adding leftBundles and rightBundles to violation details
            violation.setLeftBundles(Util.otos(violationMap.get("leftBundles")));
            violation.setRightBundles(Util.otos(violationMap.get("rightBundles")));
        }
        return violation;
    }

    /**
     * Add the given comment to the work item.
     *
     * @param  comment  The comment string.
     *
     * @return The Comment object that was added.
     *
     * @throws GeneralException  If a general error occurs.
     * @throws EmailException  If the email could not be sent.
     * @throws InvalidParameterException  If the comment was null or empty.
     */
    public Comment addComment(String comment)
        throws GeneralException, EmailException, InvalidParameterException {

        comment = validateComment(comment);
        if (Util.isNullOrEmpty(comment)) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_WORK_ITEM_NO_COMMENT));
        }

        Workflower wf = new Workflower(getContext());
        wf.addComment(getWorkItem(), getUserContext().getLoggedInUser(), comment);

        // Return the last comment in the list.  The list should be non-null,
        // but we'll be extra safe.
        WorkItem item = getWorkItem();
        List<Comment> comments = item.getComments();
        return (!Util.isEmpty(comments)) ? comments.get(comments.size()-1) : null;
    }

    /**
     * Forward work item to new owner
     * @param newOwnerId ID of the new owner identity
     * @param comment Optional comment to include with forward
     * @throws GeneralException
     * @throws InvalidParameterException
     */                           
    public void forward(String newOwnerId, String comment) throws GeneralException, InvalidParameterException {
        if (newOwnerId == null) {                        
            throw new InvalidParameterException("newOwnerId");
        }
        if (Util.nullSafeEq(newOwnerId, getWorkItem().getOwner().getId(), true)) {
            throw new GeneralException(new Message(MessageKeys.ERR_WORK_ITEM_SERVICE_FORWARD_TO_SAME_OWNER));
        }

        Identity newOwner = getContext().getObjectById(Identity.class, newOwnerId);
        if (newOwner == null) {
            throw new ObjectNotFoundException(Identity.class, newOwnerId);
        }

        Workflower wf = new Workflower(getContext());
        wf.forward(getWorkItem(), getUserContext().getLoggedInUser(), newOwner, validateComment(comment), true);
    }

    /**
     * Assign work item to a workgroup member. This method throws if work item is not owned
     * by a workgroup or assignee is not a member of that workgroup. 
     * @param assigneeId ID of identity to assign work item
     * @throws GeneralException
     */
    public void assign(String assigneeId) throws GeneralException {
        if (!getWorkItem().getOwner().isWorkgroup()) {
            throw new GeneralException("Work item is not owned by a workgroup");
        }

        Identity assignee = null;
        if (assigneeId != null) {
            assignee = getContext().getObjectById(Identity.class, assigneeId);

            if (assignee == null) {
                throw new ObjectNotFoundException(Identity.class, assigneeId);
            }

            if (!assignee.isInWorkGroup(getWorkItem().getOwner())) {
                throw new GeneralException("Assignee is not in owning workgroup");
            }
        }
        
        Workflower wf = new Workflower(getContext());
        wf.setAssignee(getUserContext().getLoggedInUser(), getWorkItem(), assignee);
    }

    /**
     * Trim and validate comment before storing. If resulting comment is empty, return null.
     * @param comment Original comment
     * @return Comment trimmed and escaped, or null 
     */
    private String validateComment(String comment) {
        return (Util.isNullOrEmpty(comment)) ? null : comment.trim();
    }

    /**
     * Helper to convert ViolationDetailer into UI consumable format.
     *
     * @param violationDetailer The container of the violation details.
     * @return Map of data
     * @throws GeneralException
     */
    public Map<String, Object> convertPolicyViolation(ViolationDetailer violationDetailer)
            throws GeneralException {
        Map<String, Object> violationMap = new HashMap<String, Object>();
        violationMap.put("ruleName", violationDetailer.getConstraint());
        violationMap.put("ruleDescription", violationDetailer.getConstraintDescription());
        violationMap.put("policyName", violationDetailer.getPolicyName());
        violationMap.put("policyDescription", violationDetailer.getConstraintPolicy());
        violationMap.put("violationSummary", violationDetailer.getSummary());
        violationMap.put("compensatingControl", violationDetailer.getCompensatingControl());
        violationMap.put("correctionAdvice", violationDetailer.getRemediationAdvice());

        Map<String, Object> riskScoreMap = null;
        if (violationDetailer.getConstraintWeight() != null) {
            int riskScore = Integer.valueOf(violationDetailer.getConstraintWeight());
            riskScoreMap = new HashMap<String, Object>();
            riskScoreMap.put("weight", riskScore);
            riskScoreMap.put("color", WebUtil.getScoreColor(riskScore, false));
        }
        violationMap.put("riskScore", riskScoreMap);

        return violationMap;
    }

    /**
     * Return the FormHandler arguments.
     */
    public static Map<String, Object> getFormArguments(SailPointContext context,
                                                       WorkItem item,
                                                       WorkflowCase wfcase)
            throws GeneralException {

        Attributes<String, Object> args = new Attributes<String, Object>();
        Identity target = null;

        // start with all work item variables
        if (item == null)
            log.error("Expected a non-null work item.");
        else {
            args.putAll(item.getAttributes());
            // just in case there's something in the item that isn't
            // in the variables
            args.put("workItem", item);
        }

        // then add a few things from the case
        // note that we do NOT include other workflow variables,
        // the work item must be self-contained
        if (wfcase == null)
            log.error("WorkflowSession without WorkflowCase!");
        else {
            // Try to determine the target identity associated with
            // this work item. We don't set this reliably on the WorkItem
            // but we should have it on the WorkflowCase
            if (wfcase != null) {
                String cls = wfcase.getTargetClass();
                if (cls != null && cls.endsWith("Identity")) {
                    String id = wfcase.getTargetId();
                    if (id != null)
                        target = context.getObjectById(Identity.class, id);
                    else {
                        String name = wfcase.getTargetName();
                        if (name != null)
                            target = context.getObjectByName(Identity.class, name);
                    }
                }
            }
        }

        // We had a bug where the target was not being set properly
        // on the WorkflowCase.  That should be fixed now, but if for
        // some other reason we can't find the target look for a work item
        // variable named "identityName" and try to resolve it.
        if (target == null) {
            String name = item.getString(ATT_IDENTITY_NAME);
            if (name != null)
                target = context.getObjectByName(Identity.class, name);
        }

        // add the standard arguments
        args.put("identity", target);

        // TODO: include the Identity that is interacting with the form in
        // case the rules need to adjust based on who is asking?

        return args;
    }

    /**
     * Copy any values from the given arguments map that the FormHandler
     * reads and writes from/to into the WorkItem. The argument map is
     * bi-directional now, so changes in this map need to stick on the
     * work item. Scrub out any special arguments so we don't get a bunch
     * of junk in the work item.
     *
     * @param workItem The work item.
     * @param args The arguments.
     */
    public static void copyArgsToWorkItem(WorkItem workItem, Map<String, Object> args) {
        if (null != args) {
            Attributes<String,Object> dest = workItem.getAttributes();

            for (Map.Entry<String,Object> entry : args.entrySet()) {
                if (!isSpecialArg(entry.getKey())) {
                    if (null == dest) {
                        dest = new Attributes<String,Object>();
                        workItem.setAttributes(dest);
                    }
                    dest.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Return whether the given argument is a special FormHandler arg that
     * should not be saved in the WorkItem attributes.
     *
     * @param argName The argument name to check.
     */
    private static boolean isSpecialArg(String argName) {
        return "identity".equals(argName) || "workItem".equals(argName);
    }

    /**
     * A method that investigates whether a given workitem
     * can be edited/completed.
     *
     * @param loggedInUser user to check
     * @return true if work item is editable by user
     * @throws GeneralException
     */
    public boolean isEditable(Identity loggedInUser) throws GeneralException {
        boolean editable = false;
        if (null != loggedInUser) {
            if (null != workItem) {
                editable = loggedInUser.equals(getObject().getOwner()) ||
                        loggedInUser.isInWorkGroup(getObject().getOwner());
            }
        }

        if (!getContext().getConfiguration().getBoolean(Configuration.LCM_ALLOW_WORKGROUP_SELF_APPROVAL, true) &&
                isWorkgroupSelfApproval(loggedInUser)) {
            editable = false;
        }

        return editable;
    }

    /**
     * Check if work item is approval, owned by a workgroup, user is in the workgroup,
     * and user is the target of the approval
     *
     * @return true if work item is approval, owned by a workgroup, user is in the workgroup,
     *         and user is the target of the approval, false otherwise
     * @throws GeneralException
     */
    public boolean isWorkgroupSelfApproval(Identity loggedInUser) throws GeneralException {
        boolean isWorkgroupSelfApproval = false;
        if (workItem != null &&
                workItem.isType(WorkItem.Type.Approval) &&
                loggedInUser != null &&
                loggedInUser.isInWorkGroup(workItem.getOwner())) {

            //We need target class and either target name or target id set on the work item to compare with logged in user.
            //These should be set by workflow arguments, log in case they are not so we can see why the check did nothing.
            if (Util.isNullOrEmpty(workItem.getTargetClass()) ||
                    (Util.isNullOrEmpty(workItem.getTargetId()) && Util.isNullOrEmpty(workItem.getTargetName()))) {
                log.error("Missing target class, target name, or target id on approval work item: " + workItem.getId());
            }
            else {
                IdentitySummaryDTO targetIdentity = getTargetIdentitySummary();
                isWorkgroupSelfApproval = targetIdentity != null &&
                        loggedInUser.getId().equals(targetIdentity.getId());
            }
        }

        return isWorkgroupSelfApproval;
    }

    /**
     * Check (if this is a certification forward request) if the certification
     * is being forwarded to a user in the certification.
     *
     * @param newOwner the new owner
     * @throws SelfCertificationException  If the certification is being
     *     forwarded to a user that is a member of the certification.
     */
    public void checkSelfCertificationForward(Identity newOwner)
            throws GeneralException {

        // If this is not a certification work item, do nothing.
        // Also do nothing for Challenge items, those are fine to forward around
        // since the ultimate decision is made by the original certifier after challenge.
        if (!isCertWorkItem() || WorkItem.Type.Challenge.equals(this.workItem.getType())) {
            return;
        }

        // If forwarding a Certification, only check for self cert if the flag is enabled.
        // Otherwise the self cert items will be moved out of the cert after forward and eventual refresh.
        if (WorkItem.Type.Certification.equals(this.workItem.getType()) &&
                !getContext().getConfiguration().getBoolean(Configuration.BLOCK_FORWARD_WORK_ITEM_SELF_CERTIFICATION, false)) {
            return;
        }

        Identity loggedInUser = this.userContext.getLoggedInUser();
        Certification cert = this.workItem.getCertification(getContext());
        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getContext(), cert);
        AbstractCertificationItem certItem = null;
        if (this.workItem.getCertificationItem() != null) {
            certItem = this.workItem.getCertificationItem(getContext());
        } else if (this.workItem.getCertificationEntity() != null) {
            certItem = this.workItem.getCertificationEntity(getContext());
        }
        List<AbstractCertificationItem> itemToCheck = (certItem == null) ? null : Collections.singletonList(certItem);
        // First check the target identity
        if (selfCertificationChecker.isSelfCertify(newOwner, itemToCheck)) {
            throw new SelfCertificationException(newOwner);
        }

        // Also check the person doing the forwarding, they should not be allowed to choose someone to do their dirty work.
        if (selfCertificationChecker.isSelfCertify(loggedInUser, itemToCheck)) {
            throw new SelfCertificationException(loggedInUser);
        }
    }

    /**
     * Check (if this is a Certification work item) if the certification is at the reassignment limit and
     * therefore should not be further forwarded.
     * @throws LimitReassignmentException If this is a certification work item at the reassignment limit and cannot be forwarded.
     */
    public void checkReassignmentLimit() throws GeneralException {
        if (WorkItem.Type.Certification.equals(this.workItem.getType())) {
            Certification cert = this.workItem.getCertification(getContext());
            if (cert != null && cert.limitCertReassignment(context)) {
                throw new LimitReassignmentException();
            }
        }
    }

    /**
     * If this is a workitem for a certification and it is a delegation or a challenge then return true
     *
     * @return true if this is a workitem for a certification and the workitem type is delegation or challenge
     */
    public boolean isCertWorkItem() throws GeneralException {
        WorkItem.Type workItemType = this.workItem.getType();
        return (null != this.workItem &&
                this.workItem.getCertification() != null &&
                (WorkItem.Type.Certification.equals(workItemType) || WorkItem.Type.Delegation.equals(workItemType) ||
                        WorkItem.Type.Challenge.equals(workItemType)));
    }

    /**
     * Get the target identity. 
     * @return Identity target identity
     * @throws GeneralException
     * @throws ObjectNotFoundException If Identity specified does not yet exist
     */
    public Identity getTargetIdentity() throws GeneralException, ObjectNotFoundException {
        IdentityService svc = new IdentityService(userContext.getContext());
        Identity targetIdentity = null;

        if (workItem.isTargetClass(PolicyViolation.class)) {
            targetIdentity = svc.getIdentityFromPolicyViolation(workItem.getTargetId());
        } else {
            String identityId = null;
            String identityName = null;
            // kludge, we normally use simple names but for awhile
            // at least LoginBean was using package qualifiers
            if (workItem.isTargetClass(Identity.class) || Identity.class.getName().equals(workItem.getTargetClass())) {
                identityId = workItem.getTargetId();
                identityName = workItem.getTargetName();
            } else {
                identityName = workItem.getString(ATT_IDENTITY_NAME);
            }

            if (identityId != null || identityName != null) {
                targetIdentity = svc.getIdentityFromIdOrName(identityId, identityName);
            }
        }

        return targetIdentity;
    }

    /**
     * Get the target identity from the work item
     * @return IdentitySummaryDTO representing the target identity. It is not guaranteed to exist.
     * @throws GeneralException
     */
    public IdentitySummaryDTO getTargetIdentitySummary() throws GeneralException {
        IdentitySummaryDTO targetIdentityDTO = null;

        try{
            Identity targetIdentity = getTargetIdentity();
            if (targetIdentity != null) {
                targetIdentityDTO = new IdentitySummaryDTO(targetIdentity);
            }
        } catch (ObjectNotFoundException ex) {
            // This can happen if we are creating a new identity and it doesn't exist yet. Fudge up a fake
            // DTO based on the info we have.
            String identityName = !Util.isNullOrEmpty(workItem.getTargetName()) ? workItem.getTargetName() : workItem.getString(ATT_IDENTITY_NAME);
            if (!Util.isNullOrEmpty(identityName)) {
                String displayName = workItem.getString(ATT_IDENTITY_DISPLAY_NAME);
                if (displayName == null) {
                    displayName = identityName;
                }

                // Use name for id just to have some unique identifier
                targetIdentityDTO = new IdentitySummaryDTO(identityName, identityName, displayName, false);
            }
        }
        return targetIdentityDTO;
    }


    /**
     * Fetches the policy violation details for the workitem
     * @return List of maps of violation details
     * @throws GeneralException If unable to get the workitem
     */
    public List<Map<String, Object>> getViolations() throws GeneralException {
        WorkItemDTO vrDTO = this.getWorkItemDTO();
        List<Map<String, Object>> violations = null;
        if (vrDTO instanceof ViolationReviewWorkItemDTO) {
            violations = ((ViolationReviewWorkItemDTO)vrDTO).getViolations();
        }
        else {
            WorkItem wi = this.getWorkItem();
            if (wi != null) {
                violations = wi.getAttributes().getList(WorkItem.ATT_POLICY_VIOLATIONS);
            }
        }
        return violations;
    }

    /**
     * Updates a single WorkItem by removing supplied list of rejected ApprovalItems.
     *
     * @param pvDecision The DTO containing the policy violation decision data
     * @return ViolationReviewResult  The result of making violation decisions.
     * @throws UnauthorizedAccessException  If the work item is not editable by the user.
     */
    public ViolationReviewResult updateAccessRequestPolicyViolationDecisions(AccessRequestPolicyViolationDecision pvDecision, WorkflowSession existingWfSession)
            throws UnauthorizedAccessException, GeneralException {

        WorkItem item = pvDecision.getWorkItem();
        if(!item.equals(workItem)) {
            throw new GeneralException("Mismatched decision and work item");
        }

        boolean isReadOnly = !this.isEditable(userContext.getLoggedInUser());

        String comment = pvDecision.getCompletionComment();

        if (pvDecision.getViolationReviewDecision().equals(pvDecision.IGNORE) &&
                item.getBoolean(ViolationReviewWorkItemDTO.REQUIRE_VIOLATION_COMMENT) &&
                comment == null) {
            throw new GeneralException(MessageKeys.UI_VIOLATION_REVIEW_REQUIRE_COMMENTS);
        }

        if (comment != null) {
            item.addComment(comment, userContext.getLoggedInUser());
        }
        // Check again that the item that we are completing is still accessible
        if (isReadOnly) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WORK_ITEM_UPDATE_UNAUTHORIZED_ACCESS, item.getId(), userContext.getLoggedInUser().getDisplayableName()));
        }

        // Get rejected list if rejected list is null set it to the empty list
        List<String> rejectedIds = pvDecision.getRejectedApprovalItemIds();
        if (rejectedIds == null) {
            rejectedIds = Collections.emptyList();
        }

            /* Rejected items can be toggled to un-rejected so go through the approval set
             * toggling items as appropriate */
        ApprovalSet set = item.getApprovalSet();
        ArrayList<ApprovalItem> approvals = (ArrayList<ApprovalItem>) set.getItems();
        for (ApprovalItem approvalItem : approvals) {
            if (rejectedIds.contains(approvalItem.getId())) {
                approvalItem.setState(WorkItem.State.Rejected);
                deleteApprovalItemAttachments(approvalItem);
            } else {
                approvalItem.setState(WorkItem.State.Finished);
            }
        }

        // Update the violationReviewDecision
        item.setAttribute(AccessRequestPolicyViolationDecision.VIOLATION_REVIEW_DECISION,
                pvDecision.getViolationReviewDecision());

        // Process the next step in the workflow
        WorkflowSession wfSession = processWorkItemWithSession(item, existingWfSession);

        WorkItem nextWorkItem = wfSession.getWorkItem();
        WorkItemDTO nextWorkItemDTO = null;
        if (nextWorkItem != null) {
            nextWorkItemDTO = new WorkItemDTOFactory(userContext).createWorkItemDTO(nextWorkItem);
        }

        RequestAccessService raService = new RequestAccessService(userContext);
        AccessRequestResultItem submitResultItem = raService.getResultItem(wfSession);

        return new ViolationReviewResult(nextWorkItem, nextWorkItemDTO,
                submitResultItem.getIdentityRequestId(),
                submitResultItem.getWorkflowStatus(),
                submitResultItem.getMessages());
    }

    /**
     * Newer version of the processWorkItem method that uses WorkflowSession.
     * It uses the session to help control transitions back to the workitem
     * page when there is more then one item for a person related to a
     * specific workflow.
     *
     * @param item The WorkItem to process
     * @return WorkflowSession
     */
    private WorkflowSession processWorkItemWithSession(WorkItem item, WorkflowSession existingWfSession) throws GeneralException {
        WorkflowSession session = existingWfSession;

        WorkItem reattachedItem = ObjectUtil.reattach(this.context, item);
        if (null == session || !reattachedItem.equals(session.getWorkItem())) {
            /*
             * Don't overwrite a WorkflowSession that is using a different WorkItem
             * create a transient one instead.
             */
            session = new WorkflowSession(reattachedItem);
        } else {
            // Any updates that we make carry over to the WorkflowSession
            session.setWorkItem(reattachedItem);
        }

        // Push the session along, this may throw a validation exception.
        session.advance(this.context);

        return session;
    }

    /**
     * This is intended to support calls from rules or anywhere else that don't carry an existing WorkflowSession
     *
     * @param pvDecision The DTO containing the policy violation decision data
     * @return ViolationReviewResult  The result of making violation decisions.
     * @throws UnauthorizedAccessException If the WorkItem is not editable by the user
     * @throws GeneralException
     */
    public ViolationReviewResult updateAccessRequestPolicyViolationDecisions(AccessRequestPolicyViolationDecision pvDecision) throws UnauthorizedAccessException, GeneralException {
        return updateAccessRequestPolicyViolationDecisions(pvDecision, null);
    }

    /**
     * Deletes the work item.
     */
    public void deleteWorkItem() throws GeneralException {

        // We don't use the Terminator here because terminating the workflow case would cause deletion of the
        // TaskResult and everything, and we want the TaskResult to stick around. This code is pretty much the
        // same as what the classic UI does.
        if (workItem != null) {
            deleteWorkItemAttachments();
            WorkflowCase wfcase = workItem.getWorkflowCase();
            if (wfcase != null) {
                // iiqetn-4296 - Simply deleting the WorkItem when it's a part of a
                // WorkflowCase will terminate the workflow before it has a chance to
                // cleanup and run through the finalize steps.

                // Setting violationReviewDecision to cancel will allow the workflow
                // to terminate early but complete the finalize steps.
                if (workItem.getType() == WorkItem.Type.ViolationReview) {
                    workItem.put("violationReviewDecision", "cancel");
                }

                // Set the WorkItem state to canceled and create a Workflower to sort out
                // what should happen when the WorkItem is in that state.
                workItem.setState(State.Canceled);
                Workflower workflower = new Workflower(context);
                workflower.handleWorkItem(context, workItem, true);
            } else {
                context.removeObject(workItem);
                context.commitTransaction();
            }
        }
    }

    /**
     * Delete all attachments on the work item
     * @throws GeneralException
     */
    private void deleteWorkItemAttachments() throws GeneralException {
        if (workItem != null) {
            ApprovalSet approvalSet = workItem.getApprovalSet();
            ArrayList<ApprovalItem> approvals = (ArrayList<ApprovalItem>) approvalSet.getItems();
            for (ApprovalItem approvalItem : approvals) {
                deleteApprovalItemAttachments(approvalItem);
            }
        }
    }

    /**
     * Delete all attachments on the given approvalItem
     * @param approvalItem
     * @throws GeneralException
     */
    private void deleteApprovalItemAttachments(ApprovalItem approvalItem) throws GeneralException {
        if (approvalItem != null) {
            List<AttachmentDTO> attachmentDTOs = (List<AttachmentDTO>) approvalItem.getAttribute(ATTACHMENTS);
            QueryOptions qo = new QueryOptions();
            List<Filter> filters = new ArrayList<>();
            for (AttachmentDTO attachmentDTO : Util.safeIterable(attachmentDTOs)) {
                filters.add(Filter.eq("id", attachmentDTO.getId()));
            }
            if (filters.size() > 0) {
                qo.add(Filter.or(filters));
                List<Attachment> attachments = context.getObjects(Attachment.class, qo);
                for (Attachment attachment : Util.safeIterable(attachments)) {
                    context.removeObject(attachment);
                }
                approvalItem.setAttribute(ATTACHMENTS, new ArrayList<AttachmentDTO>());
            }
        }
    }


}
