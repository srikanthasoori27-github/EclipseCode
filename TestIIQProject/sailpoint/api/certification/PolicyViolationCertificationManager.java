/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.Certificationer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.AuditEvent;
import sailpoint.object.CertificationAction;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemConfig;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * This class encapsulates the business logic needed for creating certification actions
 * on standalone policy violations.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class PolicyViolationCertificationManager {

    private SailPointContext context;
    private RemediationManager remediationMgr;

    /**
     * Base constructor.
     *
     * @param context Context for getting stuff.
     */
    public PolicyViolationCertificationManager(SailPointContext context) {
        this.context = context;
        remediationMgr = new RemediationManager(context);
    }

    public RemediationManager getRemediationManager() {
        return remediationMgr;
    }

    /**
     * Creates a mitigation for the given policy violation. Stores the mitigation details on
     * the identity and creates a certification history record. Deletes any non-certification
     * work items relating to this particular policy violation instance.
     *
     * @param violation The violation to mitigate
     * @param actor The identity who created the mitigation
     * @param expiration The date the mitigation expires
     * @param comments Comments made on the mitigation by the actor
     * @throws GeneralException
     */
    public void mitigate(PolicyViolation violation, Identity actor, Date expiration, String comments)
            throws GeneralException {

        MitigationExpiration mitigationExpiration = createMitigation(violation, actor,
                expiration, comments);

        context.saveObject(mitigationExpiration);

        CertificationAction action = new CertificationAction();
        // Use a null certification ID since this wasn't really decided in a cert.
        action.mitigate(null, actor, null, expiration, comments);
        action.setCreated(new Date());

        //lock the identity before modifying
        //to pass unit tests, we need to recache the violation when its done, not sure why?
        Identity lockedIdentity = ObjectUtil.lockIdentity(context, violation.getIdentity());
        if (lockedIdentity != null) {
            try {
                violation.setIdentity(lockedIdentity);
                MitigationExpiration removed = lockedIdentity.add(mitigationExpiration);
                if (null != removed) {
                    this.context.removeObject(removed);
                }
                lockedIdentity.addCertificationDecision(context, violation, action);
            }
            finally {
                //this will save the identity and commit 
                ObjectUtil.unlockIdentity(context, lockedIdentity);
                violation = (PolicyViolation)ObjectUtil.recache(context, violation);
            }
        }

        // if this policy violation has an existing delegation, remove
        // those work item since the violation has been mitigated
        cleanUpWorkItems(violation);
        
        violation.setStatus(PolicyViolation.Status.Mitigated);
        context.saveObject(violation);
        
        if (Auditor.isEnabled(AuditEvent.ActionViolationAllowException)) {
            PolicyViolationAuditor.auditMitigation(violation, actor, AuditEvent.SourceViolationManage, comments, expiration);
        }

    }

    /**
     * Creates a delegation work item for the given policy violation, assigned to
     * the specified assignee.  Deletes any non-certification
     * work items relating to this particular policy violation instance. 
     *
     * @param violation The violation to mitigate
     * @param actor The identity who created the delegation
     * @param assignee The assignee for this delegation
     * @param description Description for display in the UI. If not specified,
     *  a description is auto-generated.
     * @param comments Comments made on the delegation by the actor
     * @throws GeneralException
     */
    public void delegate(PolicyViolation violation, Identity actor, Identity assignee, String description, 
                         String comments) throws GeneralException {

        // Remove any delegations or existing remediations that exist for this violation.
        cleanUpWorkItems(violation);

        WorkItem workItem = new WorkItem();
        workItem.setOwner(assignee);
        workItem.setType(WorkItem.Type.Delegation);
        workItem.setHandler(Certificationer.class);
        workItem.setRequester(actor);
        workItem.setDescription(description);
        if (Util.isNotNullOrEmpty(comments)) {
            workItem.addComment(comments, actor);
        }
        workItem.setHandler(PolicyViolationWorkItemHandler.class.getName());
        workItem.setTargetClass(PolicyViolation.class);
        workItem.setTargetId(violation.getId());

        violation.setStatus(PolicyViolation.Status.Delegated);
        context.saveObject(violation);
        
        if (Auditor.isEnabled(AuditEvent.ActionViolationDelegate)) {
            // Don't have access to previous owner here, just the actor.
            PolicyViolationAuditor.auditDelegation(violation, actor, AuditEvent.SourceViolationManage, comments, assignee, null);
        }
        
        // bug#6941 wants notifications here
        // Workflower can do the notification but it needs a
        // WorkItemConfig to pass the EmailTemplate, could potentially
        // have one of these configured somewhere with other interesting
        // things like escalation rules
        WorkItemConfig config = new WorkItemConfig();
        String key = Configuration.POLICY_VIOLATION_DELEGATION_EMAIL_TEMPLATE;
        config.setNotificationEmail(ObjectUtil.getSysConfigEmailTemplate(context, key));
        
        // arguments for the email template
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("workItem", workItem);
        args.put("violation", violation);
        args.put("actor", actor);
        args.put("description", description);
        args.put("comments", comments);

        Workflower wf = new Workflower(context);
        wf.open(workItem, config, args, null);
    }

    /**
     * Creates a mitigation instance for the given policy violation.
     *
     * @param violation The violation to mitigate
     * @param mitigator The identity who created the mitigation
     * @param expiration The date the mitigation expires
     * @param comments Comments made on the mitigation by the actor
     * @throws GeneralException
     */
    private MitigationExpiration createMitigation(PolicyViolation violation, Identity mitigator,
                                                  Date expiration, String comments) throws GeneralException {

        MitigationExpiration mitigationExpiration =
            new MitigationExpiration(violation, mitigator, expiration, comments);

        Configuration config = context.getConfiguration();

        if (config != null) {

            String actionString = config.getString(Configuration.MITIGATION_EXPIRATION_ACTION);
            if (null != actionString) {
                mitigationExpiration.setAction(MitigationExpiration.Action.valueOf(actionString));
            }

            mitigationExpiration.setActionParameters((Map<String, Object>) config.get(Configuration.MITIGATION_EXPIRATION_ACTION_PARAMETERS));
        }

        return mitigationExpiration;
    }

    /**
     * Remediates the given policy violation. Removes any existing policy violation
     * work items pertaining to this policy violation. Removes any mitigation expirations
     * on the identity for this violation.
     *
     * @param violation The violation to remediate
     * @param actor The identity who made the decision
     * @param assigneeName Person who's going to be doing the remediation
     * @param description
     * @param comments
     * @throws GeneralException
     */
    public void remediate(PolicyViolation violation, Identity actor, String assigneeName, String description, String comments) throws GeneralException {

        cleanUpWorkItems(violation);

        CertificationAction certAction = remediationMgr.performRemediation(violation, actor,
                assigneeName, description, comments);

        // Remove an existing mitigation. The identity.remove() methd requires a mitigation record
        // so we'll create a fake one here.
        MitigationExpiration expiration = createMitigation(violation, actor, new Date(), null);

        //lock the identity before modifying
        //to pass unit tests, we need to recache the violation when its done, not sure why?
        Identity lockedIdentity = ObjectUtil.lockIdentity(context, violation.getIdentity());
        if (lockedIdentity != null) {
            try {
                violation.setIdentity(lockedIdentity);
                MitigationExpiration removed = lockedIdentity.remove(expiration);
                if (null != removed) {
                    this.context.removeObject(removed);
                }
            }
            finally {
                //this will save the identity and commit 
                ObjectUtil.unlockIdentity(context, lockedIdentity);
                violation = (PolicyViolation)ObjectUtil.recache(context, violation);
            }
        }
        
        violation.setStatus(PolicyViolation.Status.Remediated);
        context.saveObject(violation);
        
        if (Auditor.isEnabled(AuditEvent.ActionViolationCorrection)) {
            Identity assignee = context.getObjectByName(Identity.class, assigneeName);
            
            PolicyViolationAuditor.auditCorrect(violation,
                    actor,
                    AuditEvent.SourceViolationManage,
                    comments,
                    assignee,
                    certAction,
                    description);
        }
    }

     /**
     * Saves an acknowledgment decision by the actor for the given violation.
     * Removes any existing policy violation work items pertaining to this
     * policy violation.
     *
     * @param violation The violation to acknowledge
     * @param actor The identity who made the decision
     * @param comments
     * @throws GeneralException
     */
    public void acknowledge(PolicyViolation violation, Identity actor, String comments)
        throws GeneralException{

        CertificationAction action = new CertificationAction();
        action.setCreated(new Date());
        action.setActor(actor);
        action.setComments(comments);
        action.setStatus(CertificationAction.Status.Acknowledged);

        //lock the identity before modifying
        //to pass unit tests, we need to recache the violation when its done, not sure why?
        Identity lockedIdentity = ObjectUtil.lockIdentity(context, violation.getIdentity());
        if (lockedIdentity != null) {
            try {
                violation.setIdentity(lockedIdentity);
                lockedIdentity.addCertificationDecision(context, violation, action);
            }
            finally {
                //this will save the identity and commit 
                ObjectUtil.unlockIdentity(context, lockedIdentity);
                violation = (PolicyViolation)ObjectUtil.recache(context, violation);
            }
        }
        
        violation.setStatus(PolicyViolation.Status.Mitigated);
        context.saveObject(violation);

        cleanUpWorkItems(violation);
        
        PolicyViolationAuditor.auditAcknowledge(violation, actor, AuditEvent.SourceViolationManage, comments);
    }

    /**
     * Get the decisions available for a policy violation
     * @param context SailPoint context object
     * @param violation Policy violation object to get the decisions for
     * @param allowDelegate If the violation can be delegated - If a policy violation is part of a
     * certification then this is obtained from the CertificationDefinition else we look
     * up the global configuration. 
     * @return CertificationDecisionStatus Decision status for the violation
     * @throws GeneralException
     */
    public static CertificationDecisionStatus getViolationDecisionChoices(
        SailPointContext context,
        PolicyViolation violation,
        boolean allowDelegate) throws GeneralException {

        if (violation == null) {
            throw new GeneralException("PolicyViolation is null. Unable to get the list of decisions.");
        }
        CertificationDecisionStatus decisions = new CertificationDecisionStatus();

        Policy policy = violation != null ? violation.getPolicy(context) : null;

        // Revoke
        if (policy == null || policy.isActionAllowed(CertificationAction.Status.Remediated)) {
            decisions.addStatus(CertificationAction.Status.Remediated);
        }

        // Allow
        if (policy == null || policy.isActionAllowed(CertificationAction.Status.Mitigated)) {
            decisions.setAllowAcknowledge(policy != null &&
                policy.isActionAllowed(CertificationAction.Status.Acknowledged));
            decisions.addStatus(CertificationAction.Status.Mitigated);
        }

        // Delegate
        if (allowDelegate && (policy == null || policy.isActionAllowed(CertificationAction.Status.Delegated))) {
            decisions.addStatus(CertificationAction.Status.Delegated);
        }

        return decisions;
    }

    /**
     * Looks for non-certification work items that were created for this violation
     * that can be deleted now that a decision has been made.
     *
     * @param violation Policy violation we're working with
     * @throws GeneralException
     */
    private void cleanUpWorkItems(PolicyViolation violation)
            throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.isnull("certificationItem"));
        ops.add(Filter.eq("targetId", violation.getId()));
        ops.add(Filter.eq("targetClass", PolicyViolation.class.getSimpleName()));
        // We were previously ignoring policy violation work items which were created by interrogator.
        // ops.add(Filter.ne("type", WorkItem.Type.PolicyViolation));

        // jsl - we may also want to ignore the Type.Approval items used
        // but the newer approval workflows, but I think those should have
        // been deleted by now

        Workflower wf = new Workflower(context);
        List<WorkItem> items = context.getObjects(WorkItem.class, ops);
        if (items != null && items.size() > 0) {
            for (WorkItem item : items) {
                wf.archiveIfNecessary(item);
                context.removeObject(item);
            }
        }
    }

}
