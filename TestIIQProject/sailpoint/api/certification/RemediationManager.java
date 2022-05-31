/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.BatchCommitter;
import sailpoint.api.EmailTemplateRegistry;
import sailpoint.api.Emailer;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.RemediationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.provisioning.PlanUtil;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.ProvisioningTransactionService.TransactionDetails;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * Handles a number of Remediation related tasks, primarily performing remediations. It is
 * additionally used to get the appropriate RemediationAction for a given situation and for
 * providing info on the correct provisioning for different remediation cases.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class RemediationManager {

    private static Log log = LogFactory.getLog(RemediationManager.class);

    // personal copy of the sp contet
    private SailPointContext context;

    /**
     * An error handler to use.  Warnings and errors are added to this handler
     * and can be retrieved through the delegated getErrors() and getWarnings()
     * methods.
     */
    private final MessageAccumulator errorHandler;

    private RemediationCalculator remediationCalculator;

    private EmailTemplateRegistry templateManager;
    
    /**
     * Map of ProvisioningPlanSummaries for CertificationItems that are pending remediation.
     * The Map is keyed by CertificationItem ID
     */
    private Map<String, ProvisioningPlanSummary> remediationsRequiringWorkItems;

    /**
     * Flag to determine whether or not role assignments should be expanded when
     * compiling the remediation plan.  We want the PlanCompiler to do the work,
     * so the default should be true.
     */
    private boolean _expandRoleAssignments = true;

    /**
     * Flag to determine whether or not detected roles should be preserved when
     * compiling the remediation plan.
     */
    private boolean _preserveDetectedRoles = true;

    /* ----------------------------------------------------------------------------
    *
    *  CONSTRUCTORS AND PUBLIC METHODS
    *
    ----------------------------------------------------------------------------*/


    /**
     * Create a new instance, passing in the context to use.
     * Creates it's own error handler.
     *
     * @param context context SailPointContext, may not be null
     */
    public RemediationManager(SailPointContext context) {
        this(context, new BasicMessageRepository());
    }

    /**
     * Create a new instance, passing in the context to use and an
     * error handler.
     * <p/>
     * The error handler is passed in so we can add errors and warnings to the
     * parent caller's error stack.
     *
     * @param context      context SailPointContext, may not be null
     * @param errorHandler context SailPointContext, may not be null
     */
    public RemediationManager(SailPointContext context, final MessageAccumulator errorHandler) {
        this.context = context;
        this.errorHandler = errorHandler;
        remediationCalculator = new RemediationCalculator(context);
        templateManager = new EmailTemplateRegistry(context);
    }

    //------------------------------------------------------------------------
    //
    //  Public API
    //
    //------------------------------------------------------------------------

    public void setExpandRoleAssignments(boolean expandRoleAssignments) {
        _expandRoleAssignments = expandRoleAssignments;
    }

    public void setPreserveDetectedRoles(boolean preserveDetectedRoles) {
        _preserveDetectedRoles = preserveDetectedRoles;
    }

    /**
     * Build and configure a Provisioner object to do our bidding.
     * Certs have a speical set of options that disable some of the
     * things that the provisioner normally does automatically.
     * This is because the user has been presented with more granular
     * choices and we only want to do the things they asked for.
     */
    public Provisioner getProvisioner() {

        Provisioner provisioner = new Provisioner(context);

        // normally do not de-provision detected things unless
        // explicitly requested in the cert
        if (_preserveDetectedRoles) {
            provisioner.setPreserveDetectedRoles(true);
        }

        // By default, we want to allow the PlanCompiler to do all the work for required roles.
        // Only set to retainAssignmentRequiredRoles to true if _expandRoleAssignments set to false.
        if (!_expandRoleAssignments) {
            provisioner.setArgument(PlanCompiler.ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES, true);
        }

        return provisioner;
    }

    /**
     * Calculates provisioning plan for the given item and status.
     *
     * Note that the final plan created when the certification is finalized
     * takes into account remediations on all certification items on the entity.
     * The final plan may differ from this calculation if other decisions affect
     * entitlements covered by this certificaiton item.
     *
     * @param item The item to calculate
     * @param status The action status to use in the calculation
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlanSummary calculateRemediationDetails(CertificationItem item,
                                                               CertificationAction.Status status) throws GeneralException {

        ProvisioningPlanSummary summary = null;

        // the provisioner can be used to remediate entitlements on identities. This would include
        // any identity certification item or account group membership items.
        boolean useProvisioner = useProvisioner(item.getCertification());

        ProvisioningPlan masterPlan = remediationCalculator.calculateProvisioningPlan(item,
                        status);

        // bug 25529 - Some certifications need a non-null Identity in order to 
        // compile correctly via getPlanSummary(). If it needs a non-null Identity then 
        // create the plan summary using ProvisioningPlanSummary.
        Identity identity = item.getIdentity(context);
        
        // if this is a role composition certification, there will be object requests instead
        // of account requests. If that's the case skip the Provisioner
        if (useProvisioner && masterPlan != null && identity != null) {
                summary = getPlanSummary(masterPlan, item.getId(), identity);
        } else {
            summary = new ProvisioningPlanSummary(CertificationAction.RemediationAction.OpenWorkItem,
                    masterPlan, masterPlan);
        }

        return summary;
    }

    public ProvisioningPlanSummary calculateProvisioningDetails(CertificationAction.Status status,
                                                                List<Bundle> roles, Identity identity)
            throws GeneralException {

        ProvisioningPlanSummary summary = null;

        ProvisioningPlan masterPlan = remediationCalculator.calculateProvisioningPlan(status, roles, identity);

        // if this is a role composition certification, there will be object requests instead
        // of account requests. If that's the case skip the Provisioner
        if ( masterPlan != null) {
            String trackingId = Util.uuid();
            masterPlan.setRequestTrackingId(trackingId);
            masterPlan.setTrackingId(trackingId);
            summary = getPlanSummary(masterPlan, trackingId, identity);
        } else {
            summary = new ProvisioningPlanSummary(CertificationAction.RemediationAction.OpenWorkItem, masterPlan, masterPlan);
        }

        return summary;
    }
    
    public ProvisioningPlanSummary calculateRemediationDetails(PolicyViolation violation) 
    throws GeneralException {
        ProvisioningPlanSummary summary = null;
        //TODO: Create a plan if nothing to do? -rap
        ProvisioningPlan masterPlan = remediationCalculator.calculateProvisioningPlan(violation);

        if (masterPlan != null) {
            Identity identity = violation.getIdentity();
                summary = getPlanSummary(masterPlan, violation.getId(), identity);
        } else {
            summary = new ProvisioningPlanSummary(CertificationAction.RemediationAction.OpenWorkItem,
                    masterPlan, masterPlan);
        }

        return summary;
    }


    private ProvisioningPlanSummary getPlanSummary(ProvisioningPlan masterPlan, String trackingId, Identity identity)
            throws GeneralException{
        Provisioner provisioner = getProvisioner();

        // IIQETN-6282 - we're expecting the default of true for _preserveDetectedRoles.

        ProvisioningPlanSummary summary = null;

        // if this is a role composition certification, there will be object requests instead
        // of account requests. If that's the case skip the Provisioner
        if ( masterPlan != null) {
            if (identity == null) {
                provisioner.compile(masterPlan);
            } else {
                provisioner.compileOld(identity, masterPlan, false);
            }

            provisioner.itemize(true);
            
            //Ensure we have no outstanding Account Selections
            selectNativeIdForAccountSelections(provisioner);

            // Note that in getEntityPlans, we conditionally call removeDetectedRoleRequests.
            // This method, getPlanSummary, is used during decision time, which calculates the plan
            // for one item, instead of possible multiple revokes for an entity, which getEntityPlans does.
            // So for this method, we are ok with always calling removeDetectedRoleRequests and might have
            // unexpected issues if not calling it.
            // TODO: remove need to call removeDetectedRoleRequests everywhere.
            ProvisioningPlan fullPlan =
                removeDetectedRoleRequests(provisioner.getItemizedPlan(trackingId));
            ProvisioningPlan unmanagedPlan =
                removeDetectedRoleRequests(provisioner.getUnmanagedPlan(trackingId));

            CertificationAction.RemediationAction remediationAction =
                    calculateRemediationAction(fullPlan, unmanagedPlan);

            summary = new ProvisioningPlanSummary(remediationAction, fullPlan, unmanagedPlan);

        } else {
            summary = new ProvisioningPlanSummary(CertificationAction.RemediationAction.OpenWorkItem, masterPlan, masterPlan);
        }

        return summary;
    }

    /**
     * Gets the list of IDM systems which will handle the given certification item.
     *
     *
     * @return
     * @throws GeneralException
     */
    public Collection<String> getProvisioners(ProvisioningPlan plan)
            throws GeneralException{

        Set<String> provisioners = new HashSet<String>();

        if (plan != null && plan.getAccountRequests() != null){

            Provisioner provisioner = getProvisioner();

            for(ProvisioningPlan.AccountRequest request : plan.getAccountRequests()){
                if (request.getApplication() != null && !ProvisioningPlan.APP_IIQ.equals(request.getApplication())) {
                    IntegrationConfig integration = provisioner.getResourceManager(request);
                    if (integration != null && integration.getName() != null)
                        provisioners.add(integration.getName());
                }
            }
        }

        return provisioners;
    }

    /**
     * Marks the given item as needing a remediation to be kicked off.  You must
     * also call {@link #flush(Certification)} to actually kick off the 
     * remediations.
     */
    public void markForRemediation(CertificationItem item) throws GeneralException {

        CertificationAction action = item.getAction();

        boolean hasValidStatus = (action != null) &&
            (CertificationAction.Status.Remediated.equals(action.getStatus()) ||
             (action.getAdditionalActions() != null));

        if (hasValidStatus && !action.isRemediationKickedOff()) {

            // if the account has been revoked on another item, copy
            // the details to this item, but dont perform any other processing
            CertificationItem accountRevoke = getAccountRevokeDetails(item);            
            if (accountRevoke != null) {
                item.getAction().setRemediationKickedOff(true);
                copyAccountRevokeDetails(accountRevoke, item);
                return;
            }

            // If the identity no longer exists, pretend the remediation is completed and move on
            if (CertificationEntity.Type.Identity.equals(item.getParent().getType()) && item.getIdentity(context) == null){
                this.errorHandler.addMessage(Message.warn(MessageKeys.CERT_REMEDIATION_MISSING_IDENTITY,item.getIdentity()));
                // mark the remediation completed since there's nothing to do here.
                setRemediationDetailsForMissingProvisioningPlan(item);
                return;
            }

            if (log.isDebugEnabled()){
                log.debug("Marking item '" + item.getId() + "' ready for remediation.");
            }

            item.setReadyForRemediation(true);
        }
    }

    /**
     * Remediated the given violation. This is used in cases where a violation
     * needs to be remediated outside of a certification, such as the policy violation
     * viewer.
     *
     * @param violation
     * @param actor
     * @param recipientName
     * @param description
     * @param comments
     * @throws GeneralException
     */
    public CertificationAction performRemediation(PolicyViolation violation, Identity actor, String recipientName,
                                   String description, String comments) throws GeneralException {

        Provisioner provisioner = getProvisioner();

        ProvisioningPlan plan = remediationCalculator.calculateProvisioningPlan(violation);

        //TODO: Do we need a plan?
        if (plan == null)
            return null;
        
        plan.addRequester(actor);
        plan.setRequestTrackingId(violation.getId());
        Identity identity = violation.getIdentity();
        if(identity != null)
            provisioner.compileOld(violation.getIdentity(), plan, false);
        else
            provisioner.compile(plan);
        provisioner.itemize(true);
        provisioner.execute();

        ProvisioningPlan fullPlan = provisioner.getItemizedPlan(violation.getId());
        ProvisioningPlan unmanagedPlan = provisioner.getUnmanagedPlan(violation.getId());

        CertificationAction.RemediationAction remediationAction = calculateRemediationAction(fullPlan, unmanagedPlan);

        switch (remediationAction) {

            case SendProvisionRequest:
                break;
            case OpenWorkItem:
            default:

                CertificationWorkItemBuilder workItemBldr = new CertificationWorkItemBuilder(context, errorHandler);

                Policy p = violation.getPolicy(context);
                String type = null;
                if (p != null) {
                    type = p.getType();
                }

                boolean passEnts = Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(type) || null == unmanagedPlan;

                //Only pass Entitlements to Remediate if Effective Policy
                WorkItem workItem = workItemBldr.generatePolicyViolationItem(violation.getId(),
                        violation.getIdentity().getName(), unmanagedPlan, actor, recipientName, description,
                        comments, passEnts ? violation.getEntitlementsToRemediate() : null);

                if (workItem != null) {
                    notifyRemediationWorkItem(null, workItem, true);
                }

                logManualProvisioningTransaction(context, Source.PolicyViolation, violation.getIdentity().getName(),
                    unmanagedPlan, true);
        }

        CertificationAction action = new CertificationAction();
        action.setRemediationAction(remediationAction);
        action.setStatus(CertificationAction.Status.Remediated);
        action.setActor(actor);
        action.setDescription(description);
        action.setComments(comments);
        action.setCreated(new Date());
        action.setRemediationKickedOff(true);
        action.setOwnerName(recipientName);
        action.setRemediationDetails(fullPlan);

        // Being that we are touching the identity here, let's update the attribute assignments
        // here as well.
        AttributeAssignmentHandler handler = new AttributeAssignmentHandler(context);
        handler.prepare(violation);
        // Save a decision record on the identity
        // We have to lock it, and this will also refetch it since Provisioner can leave 
        // it in a bad state
        // Recache the violation so it can be used later
        Identity lockedIdentity = ObjectUtil.lockIdentity(context, violation.getIdentity());
        if (lockedIdentity != null) {
            try {
                handler.revoke(violation, actor);

                violation.setIdentity(lockedIdentity);
                lockedIdentity.addCertificationDecision(context, violation, action);
            }
            finally {
                //this will save the identity and commit
                ObjectUtil.unlockIdentity(context, lockedIdentity);
                violation = (PolicyViolation)ObjectUtil.recache(context, violation);
            }
        }
        
        // TODO: need some representation of what is being remediated
        // in a column but that could be long!!.
        String target = "Identity=" + violation.getIdentity().getName();
        String policy = "Policy=" + violation.getPolicyName();
        String rule = "Rule=" + violation.getConstraintName();

        audit(action, remediationAction, target, policy, rule);
        
        return action;
    }

    /**
     * Flush any items marked for remediation on the given certification.  This
     * will cause the remediation to be launched (or work item created), as well
     * as send notifications, audit, etc...
     */
    public void flush(Certification cert) throws GeneralException {
        
        // Find all items that are ready to be processed for the given
        // certification, nicely bucketed.
        List<Bucket> buckets = getItemsToProcess(cert);
        for (Bucket bucket : buckets) {
            performRemediation(cert, bucket);
        }
        
        /*
         * performRemediation() doesn't actually attach remediation items to their respective
         * work items any more because doing so one at a time was painfully slow.  Instead the
         * method adds them to a queue that is then processed all at once to generate work items
         * efficiently.  See bug 12585 --Bernie
         */
        flushRemediationQueue();
        
        // refetch the manager to avoid errors on flush after decache
        context.attach(cert);
        Identity thisGuy = cert.getManager(context);
        sendBatchedNotifications(cert);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FLUSH IMPLEMENATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A bucket is used to hold all items that need to have remediations
     * executed for a given identity.  For a non-identity certification, each
     * bucket will contain a single certification item.
     */
    private static class Bucket {

        private List<String> certItemIds;
        private String identityName;
        
        /**
         * Private constructor since we have static methods to create these.
         */
        private Bucket() {}
        
        /**
         * Create a bucket for the given identity.
         */
        public static Bucket createIdentityBucket(String identityName) {
            if (null == identityName) {
                throw new IllegalArgumentException("Expected an identityName");
            }
            Bucket b = new Bucket();
            b.identityName = identityName;
            b.certItemIds = new ArrayList<String>();
            return b;
        }
        
        /**
         * Create a non-identity bucket that contains the given cert item.  Note
         * that calling add() on this type of bucket will throw an exception.
         */
        public static Bucket createNonIdentityBucket(String certItemId) {
            Bucket b = new Bucket();
            b.certItemIds = Collections.singletonList(certItemId);
            return b;
        }

        /**
         * Add the given certification item ID to this bucket.  This will throw
         * for a non-identity bucket.
         */
        public void add(String certItemId) {
            this.certItemIds.add(certItemId);
        }
        
        public List<String> getCertItemIds() {
            return this.certItemIds;
        }
        
        public String getIdentityName() {
            return this.identityName;
        }
        
        public Identity getIdentity(SailPointContext context) throws GeneralException {
            if (null == this.identityName) {
                return null;
            }
            return context.getObjectByName(Identity.class, this.identityName);
        }
    }
    
    
    /**
     * Get a list of Buckets for all of the items that are marked as
     * readyForRemediation on the given certification.
     */
    private List<Bucket> getItemsToProcess(Certification cert)
        throws GeneralException {
        
        // Commit first so that we can query against readyForRemediation.
        this.context.saveObject(cert);
        this.context.commitTransaction();
        
        List<Bucket> buckets = new ArrayList<Bucket>();
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.and(Filter.eq("parent.certification.id", cert.getId()),
                          Filter.eq("action.readyForRemediation", true)));
        
        // Identity-type certifications will be bucketed by identity.
        if (cert.isCertifyingIdentities()) {
            
            // Order by the identity to make bucketing easy.
            String identityProp = cert.getIdentityProperty();
            qo.addOrdering(identityProp, true);

            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add(identityProp);
            Iterator<Object[]> it =
                this.context.search(CertificationItem.class, qo, props);
            
            Bucket bucket = null;
            String lastIdentity = null;
            while (it.hasNext()) {
                Object[] row = it.next();
                String itemId = (String) row[0];
                String identityName = (String) row[1];
                
                // Start with a fresh bucket if we found a new identity.
                if (!identityName.equals(lastIdentity)) {
                    bucket = Bucket.createIdentityBucket(identityName);
                    buckets.add(bucket);
                }
                
                bucket.add(itemId);
                lastIdentity = identityName;
            }
        }
        else {
            // Every non-identity certification will be bucketed per item for now.
            // May eventually want to change this up.
            Iterator<Object[]> it =
                this.context.search(CertificationItem.class, qo, "id");
            while (it.hasNext()) {
                buckets.add(Bucket.createNonIdentityBucket((String) it.next()[0]));
            }
        }
        
        return buckets;
    }
    
    /**
     * Launch the remediation for the given bucket.  This will send whatever is
     * available through the provisioner, copy results back onto the
     * certification, create work items, send notifications, and audit.
     */
    private void performRemediation(Certification cert, Bucket bucket)
        throws GeneralException {
        
        // First, check if this is an identity bucket with an identity that 
        // no longer exists.  If so, just mark all cert items as completed 
        // and return, since there is nothing else to do. 
        if (!Util.isNullOrEmpty(bucket.getIdentityName()) && 
                bucket.getIdentity(context) == null ) {
            if (log.isWarnEnabled())
                log.warn("Identity '" + bucket.getIdentityName() + 
                         "' no longer exists, no remediation is necessary.");
            
            for (String itemId : bucket.getCertItemIds()) {
                CertificationItem item = this.context.getObjectById(CertificationItem.class, itemId);
                setRemediationDetailsForMissingProvisioningPlan(item);
            }
            
            return;
        }
    
        // Calculate the remediation plans for all the items on the entity.
        // Since individual entitlements may be covered by more than one item,
        // the plan for each item must reflect decisions made on other items.
        EntityPlans entityPlans = getEntityPlans(cert, bucket);
        Map<String, ProvisioningPlanSummary> plans = entityPlans.getPlans();

        // If we can use the provisioner, send anything that has integration
        // support through the provisioner.  This will update the full plans in
        // the plans map to have the appropriate request ID.        
        if (useProvisioner(cert)) {
            executeUsingProvisioner(bucket, plans, entityPlans.getFiltered(), cert);

            // log anything that was completely filtered and did not pass through
            // the provisioner
            logFilteredValues(entityPlans.getFiltered(), entityPlans.getProject());

            // reattach cert here
            context.attach(cert);
        }

        AttributeAssignmentHandler handler = new AttributeAssignmentHandler(this.context);
        handler.prepare(cert);

        CertificationEntity entity = null;
        // Next, iterate over each item in the bucket and set up for revocation.  This will
        // create work items (if necessary) and a slew of other things.
        for (String itemId : Util.iterate(bucket.getCertItemIds())) {
            CertificationItem item = 
                    this.context.getObjectById(CertificationItem.class, itemId);

            // It looks like we're constantly overwriting the entity, but we really are not.
            // For Identity certifications, the entity is the Bucket's Identity.  For non-Identity
            // certifications, there is only one certification item, so this won't get overwritten.
            entity = item.getCertificationEntity();

            ProvisioningPlanSummary planSummary = plans.get(itemId);
            if (null == planSummary) {
                if (log.isWarnEnabled())
                    log.warn("No plan summary was created for item id = " + itemId);
                
                setRemediationDetailsForMissingProvisioningPlan(item);
                CertificationAction action = item.getAction();
                //In this case, something has occurred outside of the cert that means
                //we can no longer perform the remediation action - account deleted etc.
                //Since no provisioning was sent out, set no action required.
                if (RemediationAction.SendProvisionRequest.equals(action)) {
                    action.setRemediationAction(RemediationAction.NoActionRequired);
                }
                continue;
            }
    
            // flush transient provisioning plan properties.
            clearTransientProperties(planSummary);
    
            CertificationAction action = item.getAction();
    
            // Copy the details back into the certification so that the request
            // ID, integration, and action are correct.
            action.setRemediationDetails(planSummary.getFullPlan());
            action.setRemediationAction(planSummary.getAction());
    
            // Set a description for missing required role provisioning.
            if (CertificationAction.Status.Approved.equals(action.getStatus())){
                Message actionDesc = new Message(MessageKeys.PROV_REQUIREMENTS_DESC, item.getBundle());
                action.setDescription(actionDesc.getLocalizedMessage());
            }
    
            // 'Provision Assigned Role Requirements' requests will always be treated as a bulk action
            if (action.isBulkCertified() || CertificationAction.Status.Approved.equals(action.getStatus()))
                refreshActionDetails(item, planSummary.getUnmanagedPlan());
        }

        // Remove Attribute Assignments for revoked entitlements
        handler.revoke(entity);

        for (String itemId : Util.iterate(bucket.getCertItemIds())) {
            CertificationItem item = 
                    this.context.getObjectById(CertificationItem.class, itemId);
            ProvisioningPlanSummary planSummary = plans.get(itemId);
            if (null == planSummary) {
                // Don't log a warning here.  We did that earlier
                continue;
            }
            CertificationAction action = item.getAction();

            CertificationItem accountRevokeDetails = getAccountRevokeDetails(item);

            // Now that revocation has occurred, iterate over each item in the bucket and 
            // create work items (if necessary) and a slew of other things.
            // If the remediation plan cannot be automated, kickoff a workitem
            ProvisioningPlan unmanaged = planSummary.getUnmanagedPlan();
            boolean hasUnmanagedRequests = (null != unmanaged) && unmanaged.hasRequests();
            boolean hasBeenLaunched = (null != accountRevokeDetails);

            if ((hasUnmanagedRequests && !hasBeenLaunched) || planSummary.isForceOpenWorkItem()) {
                // If this remediation involved logical apps, the account requests
                // now reference the underlying tier apps which contain the
                // entitlements.  If we are sending out remediation work items,
                // we need to recalculate the owners, otherwise the logical
                // app revoker will get the remediation workitems for the tier apps.
                // TODO:  This will not work for certitem types other than entitlements,
                // so we need to find a more robust solution for this.
                if (Configuration.getSystemConfig().getBoolean(Configuration.REMEDIATE_LOGICAL_APP_TO_TIER, false)) {
                    boolean recalculate = false;
                    Set<Application> apps = item.getApplications(context);
                    for (Application app : apps) {
                        if (app.isLogical()) {
                            recalculate = true;
                            break;
                        }
                    }
                    
                    if (recalculate) refreshActionDetails(item, unmanaged);
                }
                
                /*
                 * kickoff() doesn't actually process the plan summary any more.  Instead it queues
                 * it up to be processed at a later time
                 */
                kickoff(item, unmanaged, planSummary);
            }
            // Notify the identity and their manager of the remediation if this
            // option is enabled and a notification hasn't yet been sent.
            if (!hasBeenLaunched) {
                notifyRemediation(item);
            }
    
            // Audit the decision
            String remediationTarget = "???";
            CertificationEntity ent = item.getParent();
            if (ent != null) {
                CertificationEntity.Type type = ent.getType();
                if (type != null)
                    remediationTarget = Internationalizer.getMessage(type.getMessageKey(), Locale.US) + "=" + ent.getFullname();
                else
                    remediationTarget = ent.getFullname();
            }
            audit(action, action.getRemediationAction(), remediationTarget, null, null);
    
            // If this was handled in another item already, copy the details
            // from the other item into this item.
            if (null != accountRevokeDetails) {
                copyAccountRevokeDetails(accountRevokeDetails, item);
            }
            
            // Mark this action as having the remediation kicked off so we won't
            // try to do it again.
            action.setRemediationKickedOff(true);
    
            // Now that we're done processing the item, mark it so we won't pick
            // it up again.
            item.setReadyForRemediation(false);
        }
    }
    
    /**
     * Execute the given bucket using the provisioner.  If nothing can be
     * handled by the provisioner, this does nothing.  The full plans in the
     * given map are updated with request IDs and target integrations.
     */
    private void executeUsingProvisioner(Bucket bucket,
                                         Map<String,ProvisioningPlanSummary> plans,
                                         List<AbstractRequest> filtered,
                                         Certification cert)
        throws GeneralException {
        
        // compile the plan and execute it. Calling Execute will fire off any
        // remediations that are automated by an integration in the Provisioner.
        // We want to launch this with one request per bucket, so merge all of
        // the full plans together.
        ProvisioningPlan allFullPlans = mergeFullPlans(bucket, plans);
        if (null != allFullPlans) {
    
            Provisioner provisioner = getProvisioner();

            // IIQETN-6282 - Since we let the PlanCompiler do the work for revoked roles,
            // we need the following, which are all set by default:
            // provisioner.setPreserveDetectedRoles(true);
            // provisioner.setNoRoleExpansion(false);
            // provisioner.setArgument(PlanCompiler.ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES, false);

            // set the pre-filtered items
            if (!Util.isEmpty(filtered)) {
                allFullPlans.setFiltered(filtered);
            }

            // if everything was filtered then the full plan will NOT have
            // this info set which would normally be set when provisioning
            // and is needed for the logging of filtered items in PTOs, assume
            // if source is not set then all are missing
            if (Util.isNullOrEmpty(allFullPlans.getSource())) {
                allFullPlans.setSourceId(cert.getId());
                allFullPlans.setSourceName(cert.getName());
                allFullPlans.setSourceType(Source.Certification.toString());
            }

            // Ready, aim, FIRE!!!
            Identity identity = bucket.getIdentity(context);
            if(identity == null) {
                provisioner.compile(allFullPlans);
            } else {
                provisioner.compileOld(identity, allFullPlans, false);
            }
            provisioner.execute();
    
            // Itemize the resulting plans (these have result IDs) and stick
            // them back into the summary.  This will later be saved back onto
            // the certification items.
            provisioner.itemize(true);
            for (String certItemId : bucket.getCertItemIds()) {
                ProvisioningPlanSummary summary = plans.get(certItemId);
                if (summary != null && !summary.isForceOpenWorkItem()) {
                    ProvisioningPlan itemized = provisioner.getItemizedPlan(certItemId);
                    if (null == itemized) {
                        if (log.isWarnEnabled())
                            log.warn("No itemized plan found for " + certItemId);
                        
                        continue;
                    }

                    // Copy the results into the plan.  We can't just replace with the
                    // itemized full plan since we don't itemize per value (eg - if two
                    // removes were collapsed into a single attribute request).
                    summary.setFullPlan(mergeProvisioningResults(summary.getFullPlan(), itemized));
                }
            }
        }
    }

    /**
     * Logs anything that was filtered but not run through the provisioner.
     *
     * @param filtered The filtered.
     * @param project The project.
     * @throws GeneralException
     */
    private void logFilteredValues(List<AbstractRequest> filtered, ProvisioningProject project) throws GeneralException {
        if (project != null) {
            ProvisioningTransactionService transactionService = new ProvisioningTransactionService(context);
            for (AbstractRequest request : Util.iterate(filtered)) {
                if (!Util.otob(request.get(ProvisioningProject.ATT_FILTER_LOGGED))) {
                    transactionService.logTransactionForFilteredRequest(project, request);
                }
            }
        }
    }
    
    /**
     * Merge all of the full plans from the given summaries that are in the
     * given bucket into a single plan.  Return null if there are no full plans.
     */
    private static ProvisioningPlan mergeFullPlans(Bucket bucket, Map<String,ProvisioningPlanSummary> plans) {
        ProvisioningPlan merged = null;
        for (Map.Entry<String,ProvisioningPlanSummary> entry : plans.entrySet()) {
            String certItemId = entry.getKey();
            ProvisioningPlanSummary summary = entry.getValue();
            if (bucket.getCertItemIds().contains(certItemId) && (null != summary.getFullPlan())) {
                if (null == merged) {
                    merged = new ProvisioningPlan(summary.getFullPlan());
                }
                else {
                    // 2nd arg prevents any seemingly duplicate requests from being removed
                    // during a cursory look at the plan's requests.
                    // The PlanCompiler will do all the necessary simplifying of plans
                    // when the plans are compiled later.
                    merged.merge(summary.getFullPlan(), false);
                }
            }
        }
        return merged;
    }
    
    /**
     * Merge the results (result ID, target integration) into the given plan.
     */
    private ProvisioningPlan mergeProvisioningResults(ProvisioningPlan into,
                                                      ProvisioningPlan from) {
        if (null != from) {
            if(from.isIdentityPlan()) {
            for (ProvisioningPlan.AccountRequest intoAcctReq : into.getAccountRequests()) {
                ProvisioningPlan.AccountRequest fromAcctReq =
                    from.getMatchingAccountRequest(intoAcctReq);
                if (null != fromAcctReq) {
                    intoAcctReq.setTargetIntegration(fromAcctReq.getTargetIntegration());
                    intoAcctReq.setRequestID(fromAcctReq.getRequestID());
                    }
                }
            } else {
                for (ProvisioningPlan.ObjectRequest intoObjectReq : into.getObjectRequests()) {
                    ProvisioningPlan.ObjectRequest fromObjectReq =
                        (ObjectRequest) from.getMatchingRequest(intoObjectReq);
                    if (null != fromObjectReq) {
                        intoObjectReq.setTargetIntegration(fromObjectReq.getTargetIntegration());
                        intoObjectReq.setRequestID(fromObjectReq.getRequestID());
                    }
                }
            }
        }
        
        return into;
    }

    /**
     * Clear any fields in the provisioning plans that were used temporarily by
     * the provisioner.
     */
    private static void clearTransientProperties(ProvisioningPlanSummary planSummary) {
        clearTransientProperties(planSummary.getFullPlan());
        clearTransientProperties(planSummary.getUnmanagedPlan());
    }
    
    /**
     * Clear any fields in the provisioning plan that were used temporarily by
     * the provisioner.
     */
    private static void clearTransientProperties(ProvisioningPlan plan) {
        if (null != plan) {
            plan.setTrackingId(null);
            plan.setRequestTrackingId(null);
            plan.setSourceId(null);
            plan.setSourceName(null);
            plan.setSourceType(null);
        }
    }
    
    /**
     * Send any remediation notifications that have been not been sent for this
     * certification.
     *
     * @param cert The certification for which to send the batched remediation
     *             notifications.
     */
    private void sendBatchedNotifications(Certification cert)
            throws GeneralException {

        // First, find all items that have batched remediations.  This is all
        // items that were remediated and will open work items, that have work
        // items that haven't been sent (ie - notified is null).
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("action.status", CertificationAction.Status.Remediated));
        qo.add(Filter.eq("action.remediationAction", CertificationAction.RemediationAction.OpenWorkItem));
        qo.add(Filter.eq("parent.certification", cert));
        qo.add(Filter.join("action.workItem", "WorkItem.id"));
        qo.add(Filter.isnull("WorkItem.notification"));

        // Get each work item once.
        qo.setDistinct(true);

        // Even though we're committing the transaction after the work item is
        // opened, the query wasn't finding the new work item.  Adding this
        // option fixes that problem.
        qo.setFlushBeforeQuery(true);
        qo.setCloneResults(true);
        List<String> props = new ArrayList<String>();
        props.add("WorkItem.id");
        Iterator<Object[]> ids =
                this.context.search(CertificationItem.class, qo, props);

        if (null != ids) {
            while (ids.hasNext()) {
                Object[] idArray = (Object[]) ids.next();
                String id = (String) idArray[0];

                WorkItem workItem = this.context.getObjectById(WorkItem.class, id);
                notifyRemediationWorkItem(cert.getEmailTemplatePrefix(), workItem, true);

                this.context.decache(workItem);
            }
        }
    }

    
    //------------------------------------------------------------------------
    //
    //  Remediator lookup utils
    //
    //------------------------------------------------------------------------


    /**
     * Return a single default remediator based on the default remediator
     * configuration for all applications, or null if there is not a single
     * default remediator that is common to all applications.
     *
     * @param apps Collection of applications from which to pick a remediator
     * @return Default remediator for the given apps.  Returns the system-wide remediator if no
     * common remediator exists among the apps
     * @deprecated Consider using the variation of this method that takes a Resolver
     * so that the result can take the system-wide default remediator into account as well.
     */
    public static Identity getDefaultRemediator(Collection<Application> apps) {
        return getDefaultRemediator(apps, null);
    }

    /**
     * Return a single default remediator based on the default remediator
     * configuration for all applications, or the system-wide remediator 
     * if there is not a single default remediator that is common to all 
     * applications.
     *
     * @param apps Collection of applications from which to pick a remediator
     * @param context Resolver that can look up the system-wide default remediator when necessary
     * @return Default remediator for the given apps.  Returns the system-wide remediator if no
     * common remediator exists among the apps
     */
    public static Identity getDefaultRemediator(Collection<Application> apps, Resolver context) {
        // TODO:  Consider making this method non-static because then we would already have a context.  
        // We need a context in order to resolve references from the system config.  Currently leaving
        // this as-is because we previously didn't require one (and previously failed to 
        // consider the default revoker).  Making the context optional effectively leaves legacy 
        // functionality intact.  Ideally we would fix everything in one shot, but the more practical
        // approach is to resolve these issues one bug at a time. --Bernie
        Identity defaultRemediator;
        if (apps == null || apps.isEmpty()) {
            defaultRemediator = null;
        } else {
            List<Application> appList = new ArrayList<Application>();
            appList.addAll(apps);

            defaultRemediator = getUniqueAppIdentity(new UniqueIdentityFetcher() {
                public Identity getUniqueIdentity(Application app) {
                    Identity identity = null;
                    List<Identity> appRemediators = app.getRemediators();

                    // Return the remediator if there is only one.
                    if ((null != appRemediators) && (appRemediators.size() == 1)) {
                        // Note that this only works because we only ever support one remediator
                        // per application.  The model implies that multiple are allowed, but we
                        // never expose that on our UI and don't support multiples.  If that ever
                        // changes we definitely need to fix this logic because it would be broken
                        identity = appRemediators.get(0);
                    }
                    if (null == identity) {
                        // Determine if we need to use the application owner instead
                        final boolean defaultToAppOwnerRemediation = !Configuration.getSystemConfig().getAttributes().getBoolean(
                                Configuration.DISABLE_DEFAULT_TO_APP_OWNER_REMEDIATION);
                        if (defaultToAppOwnerRemediation) {
                            identity = app.getOwner();
                        }
                    }
                    return identity;
                }
            }, appList);
        }

        // Fall back on the system-wide remediator if we can't find one for the given set of apps
        if (defaultRemediator == null && context != null) {
            // Set a default owner for delegations and remediations
            Configuration sysConfig = Configuration.getSystemConfig();
            String defaultRemediatorName = (String) sysConfig.get(Configuration.DEFAULT_REMEDIATOR);
            if (!Util.isNullOrEmpty(defaultRemediatorName)) {
                try {
                    defaultRemediator = context.getObjectByName(Identity.class, defaultRemediatorName);
                } catch (GeneralException e) {
                    log.error("Failed to fetch the default remediator from the system configuration", e);
                    defaultRemediator = null;
                }
            }
        }
        
        return defaultRemediator;
    }

    /**
     * Get the default remediator based on the applications in the unmanaged provisioning plan. If none found, will
     * fall back to system level default revoker.
     * 
     * See comments for {@link #getDefaultRemediator(Collection, Resolver)} for more details.
     * 
     * @param unmanagedPlan ProvisioningPlan with unmanaged requests.
     * @param context Resolver to look up applications and system configuration
     * @return Identity who is default remediator, or null.
     * @throws GeneralException
     */
    public static Identity getDefaultRemediator(ProvisioningPlan unmanagedPlan, Resolver context) throws GeneralException {
        List<Application> remediationApps = new ArrayList<Application>();
        for (ProvisioningPlan.AccountRequest request : Util.safeIterable(unmanagedPlan.getAccountRequests())) {
            Application app = request.getApplication(context);
            if (app != null) {
                remediationApps.add(app);
            }
        }

        return getDefaultRemediator(remediationApps, context);
    }

    /**
     * Get a unique identity from all of the applications if there is a single
     * unique identity on all applications.
     *
     * @param fetcher The fetcher to use to get the unique identity from the
     *                applications.
     * @param apps    List of applicationto search for unique identities
     * @return The unique identity for all applications, or null if there isn't
     *         one.
     */
    public static Identity getUniqueAppIdentity(UniqueIdentityFetcher fetcher, List<Application> apps) {

        Identity identity = null;

        if (null != apps) {
            for (Application app : apps) {
                Identity fetched = fetcher.getUniqueIdentity(app);

                // Return null if any app doesn't have a single remediator.
                if (null == fetched) {
                    return null;
                } else {
                    // If there is not yet a remediator or the current is the same
                    // that we found before, keep going.  Otherwise, we didn't find
                    // a single unique remediator, so bail.
                    if ((null == identity) || identity.equals(fetched)) {
                        identity = fetched;
                    } else {
                        return null;
                    }
                }
            }
        }

        return identity;
    }

    /**
     * Interface to fetch a unique identity from an application.
     */
    public static interface UniqueIdentityFetcher {

        /**
         * Return a unique identity from the application, or null if there is
         * not a unique identity for the given app.
         */
        public Identity getUniqueIdentity(Application app);
    }

    /**
     * Gathers together the specifics of a compiled provisioning plan so it
     * all can be passed around easily.
     */
    public static class ProvisioningPlanSummary {

        /**
         * The full compiled plan. This includes all remediation
         * details.
         */
        private ProvisioningPlan fullPlan;

        /**
         * Subset of the fullPlan, including accounts on applications
         * which are not managed by an integrated IDM app.
         */
        private ProvisioningPlan unmanagedPlan;

        /**
         * The calculate remediation action.
         */
        private CertificationAction.RemediationAction action;
        
        /**
         * In some cases, we have no provisioning plan, but still 
         * want to open a work item
         */
        private boolean forceOpenWorkItem;

        public ProvisioningPlanSummary(CertificationAction.RemediationAction action, ProvisioningPlan fullPlan,
                                       ProvisioningPlan unmanagedPlan) {
            this.action = action;
            this.fullPlan = new ProvisioningPlan(fullPlan);
            this.unmanagedPlan = new ProvisioningPlan(unmanagedPlan);
            forceOpenWorkItem = false;
        }

        public CertificationAction.RemediationAction getAction() {
            return action;
        }

        public ProvisioningPlan getFullPlan() {
            return fullPlan;
        }

        public void setFullPlan(ProvisioningPlan plan) {
            fullPlan = plan;
        }

        public ProvisioningPlan getUnmanagedPlan() {
            return unmanagedPlan;
        }
        
        public boolean isForceOpenWorkItem() {
            return forceOpenWorkItem;
        }
        
        public void setForceOpenWorkItem(boolean force) {
            forceOpenWorkItem = force;
        }
    }

    //------------------------------------------------------------------------
    //
    //  Private Methods
    //
    //------------------------------------------------------------------------

    /**
     * Return a list of all items on the given certification related to the
     * given bucket that are marked for provisioning.  This includes any items
     * that have already been sent for remediation since we need the full
     * picture when determining what to keep and what to remove.
     */
    private List<CertificationItem> getAllProvisioningItems(Certification cert,
                                                            Bucket bucket)
        throws GeneralException {
        
        Filter itemFilter = null;        
        if (null != bucket.getIdentityName()) {
            String identityProp = cert.getIdentityProperty();
            if (null == identityProp) {
                throw new RuntimeException("Expected an identity property for a bucket from an identity cert.");
            }
            
            itemFilter =
                Filter.and(Filter.eq(identityProp, bucket.getIdentityName()),
                           Filter.eq("parent.certification.id", cert.getId()));
        }
        else {
            // Assuming a single item per bucket for non-identity certs now.
            // If this changes, we'll need a better way to find other items for
            // the same entity.
            List<String> itemIds = bucket.getCertItemIds();
            if (1 != itemIds.size()) {
                throw new RuntimeException("Expected a bucket with a single item for a non-identity cert.");
            }
            CertificationItem item =
                this.context.getObjectById(CertificationItem.class, itemIds.get(0));
            itemFilter = Filter.eq("parent", item.getParent());
        }
        
        Filter provisionFilter =
            Filter.or(Filter.eq("action.status", CertificationAction.Status.Remediated),
                      Filter.notnull("action.additionalActions"));
        Filter f = Filter.and(itemFilter, provisionFilter);
        return context.getObjects(CertificationItem.class, new QueryOptions(f));
    }
    
    /**
     * Calculates a master plan for all the CertificationItems for the given bucket.
     * Once this plan has been calculated, it's broken out into ProvisioningPlanSummary
     * instances for each item and returned as a map.
     *
     * @return Object containing a Map of resulting plans keyed by the CertificationItem ID and
     *         the filtered values.
     */
    private EntityPlans getEntityPlans(Certification cert, Bucket bucket)
        throws GeneralException {

        // If the provisioner is used, we'll build a masterPlan and compile it
        // to determine provision plans for each item.  Otherwise, we'll just
        // manually build a plan for each item and stick it in the plans map.
        Map<String, ProvisioningPlanSummary> plans = new HashMap<String, ProvisioningPlanSummary>();
        ProvisioningPlan masterPlan = null;

        // collect all filtered values
        List<AbstractRequest> filtered = new ArrayList<>();

        // calling code will need the project to log anything that was
        // completely filtered out since the provisioner wont ever be called
        ProvisioningProject project = null;

        boolean useProvisioner = useProvisioner(cert);

        // Get all of the items that require provisioning that are associated
        // with this bucket.  This includes items that have previously been
        // remediated.  We want the full picture of everything that is going to
        // happen for this group.
        List<CertificationItem> items = getAllProvisioningItems(cert, bucket);

        // Either add the items to the masterPlan (for identity certs) so they
        // can be compiled later, or add the summary directly to the map for
        // non-identity certs.
        for (CertificationItem item : items) {

            // check to see if the item is ready to remediate
            if (isReadyForRemediation(item)) {

                CertificationAction action = item.getAction();
                CertificationAction.Status status = item.getAction() != null ? item.getAction().getStatus() : null;
                if (CertificationAction.Status.Remediated.equals(status) || action.getAdditionalActions() != null) {

                    ProvisioningPlan itemPlan = action.getRemediationDetails();

                    // set tracking ID since it will have not have been included on the cert action
                    if (itemPlan != null)
                        itemPlan.setRequestTrackingId(item.getId());

                    // if the revoke is on a detected role or violation, merge the remediation
                    // details with an IIQ request to revoke the detected role(s) in question.
                    // If this is not done Provisioner will assume the role(s) are being retained
                    // and will add back the entitlements
                    boolean includeDetectedRoleRevokes =
                        item.getPolicyViolation() != null ||
                        (item.getBundle() != null && !CertificationItem.SubType.AssignedRole.equals(item.getSubType()));

                    if (includeDetectedRoleRevokes && itemPlan != null){
                       itemPlan = remediationCalculator.calculateProvisioningPlan(item);
                       if (itemPlan != null && action.getRemediationDetails() != null){
                           itemPlan.merge(action.getRemediationDetails());
                           itemPlan.setRequestTrackingId(item.getId());
                       }
                    } else if (itemPlan == null || CertificationItem.SubType.AssignedRole.equals(item.getSubType())){
                        itemPlan = remediationCalculator.calculateProvisioningPlan(item);
                    }

                    if (null != itemPlan) {
                        // add the map version of the requester Identity so that
                        // we'll have access to all of its attributes downstream
                        if (action.getActorName() != null)
                            itemPlan.addRequester(context.getObjectByName(Identity.class,
                                action.getActorName()));
    
                        if (useProvisioner) {
                            if (masterPlan == null)
                                masterPlan = new ProvisioningPlan();
                            // 2nd arg prevents any seemingly duplicate requests from being removed
                            // during a cursory look at the plan's requests.
                            // The PlanCompiler will do all the necessary simplifying of plans
                            // when the plans are compiled later.
                            masterPlan.merge(itemPlan, false);
                        }
                        else {
                            ProvisioningPlanSummary summary = new ProvisioningPlanSummary(
                                    CertificationAction.RemediationAction.OpenWorkItem, itemPlan, itemPlan);
                            plans.put(item.getId(), summary);
                        }

                        // capture any pre-filtered values for this item, these would typically be
                        // required roles or permitted roles
                        PlanUtil.mergeFiltered(filtered, itemPlan);
                    } else {
                        // itemPlan is null if a bundle is deleted after a cert
                        // has been created, but before the housekeeper runs.
                        log.debug("getEntityPlans: ProvisioningPlan for CertificationItem is null."
                            + " Role was likely deleted after certification was generated.");
                    }
                }
            }
        }

        // If using the provisioner, compile and itemize the master plan to populate
        // the map.
        if (useProvisioner && masterPlan != null) {

            Identity identity = bucket.getIdentity(context);

            Provisioner provisioner = getProvisioner();
            if(identity == null) {
                provisioner.compile(masterPlan, masterPlan.getArguments());
            } else {
                provisioner.compileOld(identity, masterPlan, false);
            }
            provisioner.itemize(true);

            // transfer the compiled project
            project = provisioner.getProject();

            // merge any filtered values that may have come out of the entity
            // plan compilation, these would typically be entitlements that were
            // filtered out because the identity changed between when the decision
            // was saved and when the provisioning actually occurred
            PlanUtil.mergeFiltered(filtered, project.getFiltered());
            
            //Ensure we have no outstanding Account Selections
            selectNativeIdForAccountSelections(provisioner);
                
            for (CertificationItem item : items) {

                // check to see if the item is ready to remediate - or if we're doing a calcuation, continue on
                if (isReadyForRemediation(item)) {

                    CertificationAction action = item.getAction();
                    CertificationAction.Status status = item.getAction() != null ? item.getAction().getStatus() : null;
                    if (CertificationAction.Status.Remediated.equals(status) || action.getAdditionalActions() != null) {

                        ProvisioningPlan itemizedPlan = provisioner.getItemizedPlan(item.getId());
                        ProvisioningPlan unmanagedPlan = provisioner.getUnmanagedPlan(item.getId());
                        
                        //If we are remediating a policy violation, it may not have any roles or attributes 
                        //to remediate, but we still want to tell someone to do something about it.  So force a 
                        //work item even without provisioning plan.
                        boolean forceOpenWorkItem = 
                            (itemizedPlan == null && unmanagedPlan == null && 
                                    CertificationItem.Type.PolicyViolation.equals(item.getType()));

                        //If full plan is null or has no requests, there is nothing to do for this item.  
                        if ((itemizedPlan != null && !itemizedPlan.isEmpty()) || forceOpenWorkItem) {
                            
                            // todo i'm stripping out the detected role requests for now. That needs to go away. see
                            // comments on removeDetectedRoleRequests()
                            // Here's a partial "going away" of removing detected role requests:
                            //     If preserveDetectedRoles, AssignmentExpander (getDetectedRequest) will need to
                            //     check all detected role requests for overlaps; don't remove detected role requests.
                            //     If isProcessRevokesImmediately, those are handled one at a time and 
                            //     belch when trying to digest plans with detected role requests; remove detected role requests.
                            // TODO: fix issue with isProcessRevokesImmediately.
                            if ((project.getAttributes() != null && 
                                    !project.getAttributes().getBoolean(PlanCompiler.ARG_PRESERVE_DETECTED_ROLES)) ||
                                    cert.isProcessRevokesImmediately()) {

                                itemizedPlan = removeDetectedRoleRequests(itemizedPlan, true);
                            }

                            unmanagedPlan = removeDetectedRoleRequests(unmanagedPlan);

                            CertificationAction.RemediationAction remediationAction =
                                calculateRemediationAction(itemizedPlan, unmanagedPlan);

                            ProvisioningPlanSummary planSummary =
                                new ProvisioningPlanSummary(remediationAction, itemizedPlan, unmanagedPlan);
                            planSummary.setForceOpenWorkItem(forceOpenWorkItem);
                            plans.put(item.getId(), planSummary);
                        }
                    }
                }
            }

        }

        return new EntityPlans(plans, project, filtered);
    }
    
    private CertificationAction.RemediationAction calculateRemediationAction(ProvisioningPlan fullPlan,
                                                                             ProvisioningPlan unmanagedPlan){
        CertificationAction.RemediationAction action = CertificationAction.RemediationAction.OpenWorkItem;
        // if there's no unmanaged plan the whole thing is a prov request. Otherwise
        // if remedy is enabled send a ticket
        if (fullPlan != null && unmanagedPlan == null) {
            action = CertificationAction.RemediationAction.SendProvisionRequest;
        } else if (fullPlan == null && unmanagedPlan == null) {  //Nothing Burger
            action = CertificationAction.RemediationAction.NoActionRequired;
        }

        return action;
    }

    /**
     * @deprecated Use {@link #calculateRemediationAction(PolicyViolation)} instead.
     * @exclude
     * Note that this is kept for public API backwards compatibility, but was
     * deprecated because of a typo.
     */
    public CertificationAction.RemediationAction calcualteRemediationAction(PolicyViolation violation) throws GeneralException {
        return calculateRemediationAction(violation);
    }

    
    /**
     * Calculate the RemediationAction to use for the given violation.
     */
    public CertificationAction.RemediationAction calculateRemediationAction(PolicyViolation violation) throws GeneralException {

        //TODO: OpenWorkItem for Effective

        Provisioner provisioner = getProvisioner();

        ProvisioningPlan plan = remediationCalculator.calculateProvisioningPlan(violation);

        if (plan == null)
            return null;

        plan.setRequestTrackingId(violation.getId());
        if(violation.getIdentity() == null)
            provisioner.compile(plan);
        else
            provisioner.compileOld(violation.getIdentity(), plan, false);
        
        provisioner.itemize(true);

        ProvisioningPlan fullPlan = provisioner.getItemizedPlan(violation.getId());
        ProvisioningPlan unmanagedPlan = provisioner.getUnmanagedPlan(violation.getId());

        return calculateRemediationAction(fullPlan, unmanagedPlan);
    }
    
    private ProvisioningPlan removeDetectedRoleRequests(ProvisioningPlan plan) {
        return removeDetectedRoleRequests(plan, false);
    }
    /**
     * todo - The Provisioner returns detected role requests with the final plan. It really should
     * compile the detected role plan, then remove the IIQ request for the detected role.
     *
     * @param plan
     * @param keepAssignmentRemovals if true, allows the iiq plan for a hard permit removal to remain
     */
    private ProvisioningPlan removeDetectedRoleRequests(ProvisioningPlan plan, boolean keepAssignmentRemovals) {

        ProvisioningPlan result = plan;
        
        // Remove IIQ detected role requests from the completed plan
        if (plan != null && plan.getAccountRequests() != null) {
            Iterator<ProvisioningPlan.AccountRequest> iter = plan.getAccountRequests().iterator();
            while (iter.hasNext()) {
                ProvisioningPlan.AccountRequest request = iter.next();
                if (request.getAttributeRequests() != null) {
                    Iterator<ProvisioningPlan.AttributeRequest> attrIter =
                            request.getAttributeRequests().iterator();
                    while (attrIter.hasNext()) {
                        ProvisioningPlan.AttributeRequest attrReq = attrIter.next();
                        //Only remove if we don't have an assignment, otherwise
                        //we'll be unable to remove this hard permit
                        if(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName())) {
                            if (keepAssignmentRemovals && ProvisioningPlan.Operation.Remove.equals(attrReq.getOp()) && attrReq.getAssignmentId() != null) {
                                log.info("Keeping hard permit removal request for assignment : " + attrReq.getAssignmentId());
                            } else {
                                attrIter.remove();
                            }
                        }
                    }

                    if (request.getAttributeRequests().isEmpty()) {
                        iter.remove();
                    }
                }
            }
            List<AccountRequest> accountReq = plan.getAccountRequests();
            List<ObjectRequest> objectReq = plan.getObjectRequests();
            if ((accountReq != null && accountReq.isEmpty()) || (objectReq != null && objectReq.isEmpty()))   {           	
                result = null;
            }
        }
        
        return result;
    }

    private boolean isReadyForRemediation(CertificationItem item) throws GeneralException {

        return (item.getAction() != null && item.getAction().getStatus() != null &&
                !item.isChallengeActive(context) && !item.isDelegatedOrWaitingReview() &&
                getAccountRevokeDetails(item) == null);
    }

    /**
     * Returns true if remediations on this cert may be handled by an Integration
     */
    private boolean useProvisioner(Certification cert){    	
    	if(cert.isCertifyingGroups() || cert.isCertifyingIdentities())
    		return true;
    	else 
    		return false;
    }


    
    /**
     * If this was a remediation request, calculate the right thing to do based on the
     * entitlements, and set it on the action.
     *
     * @param item
     * @param unmanagedPlan
     * @throws GeneralException
     */
    private void refreshActionDetails(CertificationItem item, ProvisioningPlan unmanagedPlan)
        throws GeneralException {

        CertificationAction action = item.getAction();

        if (unmanagedPlan != null) {
            // Override the owner with the configured remediator if necessary
            Identity defaultRemediator = null;
            switch (item.getType()) {
                case BusinessRoleGrantedScope:
                case BusinessRoleGrantedCapability:
                case BusinessRolePermit:
                case BusinessRoleRequirement:
                case BusinessRoleHierarchy:
                case BusinessRoleProfile:
                    Bundle role = context.getObjectById(Bundle.class, item.getParent().getTargetId());
                    defaultRemediator = role.getOwner();
                    break;
                default:
                    List<Application> apps = new ArrayList<Application>();

                    if (unmanagedPlan != null &&
                            unmanagedPlan.getAccountRequests() != null) {
                        for (ProvisioningPlan.AccountRequest req : unmanagedPlan.getAccountRequests()) {
                            Application app = req.getApplication() != null ? req.getApplication(context) : null;
                            if (app != null)
                                apps.add(app);
                        }
                    } else {
                        apps.addAll(item.getApplications(context));
                    }

                    defaultRemediator = getDefaultRemediator(apps, context);
                    //Get ye to the role owner
                    if (defaultRemediator == null && CertificationItem.Type.Bundle.equals(item.getType())) {
                        Configuration sysConfig = Configuration.getSystemConfig();
                        if (sysConfig != null && sysConfig.getBoolean(Configuration.REMEDIATION_GOES_TO_ROLE_OWNER_INSTEAD_OF_DEFAULT)) {
                            role = context.getObjectById(Bundle.class, item.getTargetId());
                            if (role !=  null) {
                                defaultRemediator = role.getOwner();
                            }
                        }
                    }
            }

            if (defaultRemediator != null) {
                action.setOwner(defaultRemediator);
                action.setOwnerName(defaultRemediator.getName());
            } else {// else keep the backup remediator in place (i.e. don't override the owner
                if (action.getOwner() == null && action.getOwnerName() != null) {
                    //Violations will have an owner name but not an owner, fix it.
                    Identity owner = context.getObjectByName(Identity.class, action.getOwnerName());
                    if (owner != null) {
                        action.setOwner(owner);
                    }
                }
            }
        }
    }

    private void kickoff(CertificationItem item, ProvisioningPlan unmanagedPlan, ProvisioningPlanSummary planSummary) 
    throws GeneralException {

        CertificationAction action = item.getAction();

        if (log.isDebugEnabled()){
            String actionName = (action != null && action.getRemediationAction() != null) ?
                    action.getRemediationAction().name()  : "NULL";
            log.debug("Kicking off action for item '" + item.getId() + "'. Remediation action is " + actionName);
        }

        switch (action.getRemediationAction()) {

            case SendProvisionRequest:
                break;
            case OpenWorkItem:
            default:

                if (action.getOwnerName() != null && action.isActive()) {
                    queueForRemediation(item, planSummary);
                    /* 
                     * Note:  No need to send notifications here because we're going to send notifications
                     * for all items on the cert in one big batch later.  See bug 12585. -- Bernie
                     */
                }  else if (action.getOwnerName() == null){
                    log.warn("Skipping work item creation because no owner was assigned.");
                }

                // else, should we audit this if we had no one to send it to ?
                break;
        }
    }
    
    /**
     * Queue the plan summary associated with the specified certification item for remediation.
     * This is done so that we don't fire off excessive commits when processing multiple certification
     * items containing remediations that will ultimately be added to a single work item.
     * @param item
     * @param planSummary
     */
    private void queueForRemediation(CertificationItem item, ProvisioningPlanSummary planSummary) {
        String id = item.getId();
       
        if (remediationsRequiringWorkItems == null) {
            remediationsRequiringWorkItems = new HashMap<String, ProvisioningPlanSummary>();
        }
        remediationsRequiringWorkItems.put(id, planSummary);
    }
    
    /**
     * Process all queued plan summaries, generating or appending remediation items to suitable work items 
     * @throws GeneralException when database errors occur while attempting to fetch certification items or commit work items
     */
    private void flushRemediationQueue() throws GeneralException {
        if (!Util.isEmpty(remediationsRequiringWorkItems)) {
            final int totalRemediations = remediationsRequiringWorkItems.size();
            // TODO: Hard code a batch size for now.  Consider making this configurable.
            final int batchSize = 100;
            
            // Initialize extra parameters that we'll want to access during the committing process
            final String NUM_GENERATED_WORK_ITEMS = "numGeneratedWorkItems";
            final String WORK_ITEM_BUILDER = "workItemBuilder";
            Map<String, Object> params = new HashMap<String, Object>();
            params.put(NUM_GENERATED_WORK_ITEMS, 0);
            params.put(WORK_ITEM_BUILDER, new CertificationWorkItemBuilder(context, errorHandler));
            
            // Create a committer and executor
            BatchCommitter<CertificationItem> committer = new BatchCommitter<CertificationItem>(CertificationItem.class, context, true);
            BatchCommitter.BatchExecutor<CertificationItem> executor = new BatchCommitter.BatchExecutor<CertificationItem>() {
                @Override
                public void execute(SailPointContext context, CertificationItem certItem, Map<String, Object> extraParams)
                        throws GeneralException {
                    int numGeneratedWorkItems = Util.otoi(extraParams.get(NUM_GENERATED_WORK_ITEMS));
                    CertificationWorkItemBuilder workItemMgr = (CertificationWorkItemBuilder)extraParams.get(WORK_ITEM_BUILDER);
                    if (certItem != null) {
                        log.debug("RemediationManager.flushRemediationQueue generating a remediation item for the certification item with ID " + certItem.getId());

                        if (!Util.isEmpty(remediationsRequiringWorkItems)) {
                            ProvisioningPlanSummary planSummary = remediationsRequiringWorkItems.get(certItem.getId());
                            ProvisioningPlan unmanagedPlan = planSummary.getUnmanagedPlan();
                            /*
                             *  Note that generateRemediationWorkItem doesn't always generate a work item.  
                             *  If a suitable remediation work item is found the current remediation can be added to the existing work item instead.
                             *  This behavior can be overridden by specifying forceOpenWorkItem in the plan
                             */
                            WorkItem witem = workItemMgr.generateRemediationWorkItem(certItem, unmanagedPlan, !planSummary.isForceOpenWorkItem());
                            if (witem != null) {
                                // Track this purely for debugging purposes
                                numGeneratedWorkItems++;
                            }

                            // create any PTOs for the cert item and let the batch committer handle the committing
                            logManualProvisioningTransactionForCertItem(context, unmanagedPlan, certItem);

                            extraParams.put(NUM_GENERATED_WORK_ITEMS, numGeneratedWorkItems);
                        }
                    }
                }
            };
            
            // Attach the remediations to a suitable WorkItem.  If no such WorkItem is found one will be generated
            committer.execute(remediationsRequiringWorkItems.keySet(), batchSize, executor, params);
            
            // Log our results and clear the queue
            int numGeneratedWorkItems = Util.otoi(params.get(NUM_GENERATED_WORK_ITEMS));
            log.debug("RemediationManager.flushRemediationQueue generated " + numGeneratedWorkItems + " work items over " + totalRemediations + " certification items.");
            remediationsRequiringWorkItems.clear();
        }
    }

    /**
     * Logs any provisioning transactions for the manual remediation actions in the plan
     * for the specified certification item.
     *
     * @param ctx The context.
     * @param plan The plan.
     * @param item The certification item.
     * @throws GeneralException
     */
    private void logManualProvisioningTransactionForCertItem(SailPointContext ctx,
                                                             ProvisioningPlan plan,
                                                             CertificationItem item) throws GeneralException {

        // emulate what auto provisioning does
        if (item.getCertification() != null) {
            plan.setSourceId(item.getCertification().getId());
            plan.setSourceName(item.getCertification().getName());
        }

        logManualProvisioningTransaction(ctx, Source.Certification, item.getIdentity(), plan, false);
    }

    /**
     * Logs any provisioning transactions for the manual remediation actions.
     *
     * @param ctx The context.
     * @param source The source of the remediation.
     * @param identityName The identity name.
     * @param plan The unmanaged plan.
     * @param commit True if the transaction should be committed.
     * @throws GeneralException
     */
    private void logManualProvisioningTransaction(SailPointContext ctx, Source source, String identityName,
                                                  ProvisioningPlan plan, boolean commit)
        throws GeneralException {

        if (plan != null) {
            // service should commit or not based on passed in value
            ProvisioningTransactionService transactionService = new ProvisioningTransactionService(ctx);
            transactionService.setCommit(commit);

            for (ProvisioningPlan.AbstractRequest request : Util.iterate(plan.getAllRequests())) {
                TransactionDetails transactionDetails = new TransactionDetails();
                transactionDetails.setSource(source.toString());
                transactionDetails.setIdentityName(identityName);
                transactionDetails.setPartitionedPlan(plan);
                transactionDetails.setRequest(request);
                transactionDetails.setManual(true);

                transactionService.logTransaction(transactionDetails);
            }
        }
    }

    /**
     * Helper for auditing in two places.
     *
     * @param action
     * @param remediation
     * @param target
     * @param arg1
     * @param arg2
     */
    private void audit(CertificationAction action,
                       CertificationAction.RemediationAction remediation,
                       String target, String arg1, String arg2) {

        // Qualify the owner name so we know what it is
        String owner = action.getOwnerName();
        if (owner != null)
            owner = "Owner=" + owner;

        // Assuming the actor was stored here, if not it will
        // default to the SailPointContext owner which will be
        // "system" if we're a background task.

        Auditor.logAs(action.getActorName(),
                AuditEvent.ActionRemediate,
                target,
                getAuditType(remediation),
                owner,
                arg1, arg2);
    }

    /**
     * Map a RemediationAction into the action name we want to put
     * into the audit log.  The display name isn't bad but we
     * may want more control over this and the display name is unfortunately
     * part of the historical schema that can't be changed.
     *
     * @param action
     * @return
     */
    public String getAuditType(CertificationAction.RemediationAction action) {

        String auditType = "Unknown";

        switch (action) {
            case OpenTicket:
                auditType = "Trouble Ticket";
                break;
            case SendProvisionRequest:
                auditType = "Provisioning Request";
                break;
            case OpenWorkItem:
                auditType = "Work Item";
                break;
        }

        return auditType;
    }

    /**
     * Generate a remediation notification from a work item.
     */
    private void notifyRemediationWorkItem(String emailPrefix, WorkItem item, boolean sendNow)
            throws GeneralException {

        // Don't do anything if we're not sending now.
        if (!sendNow) {
            return;
        }

        Date notification = item.getNotification();
        if (notification == null) {
            EmailTemplate email = getRemediationWorkItemEmail(emailPrefix);
            if (email != null) {
                Identity owner = item.getOwner();
                Identity requester = item.getRequester();
                List<String> ownerEmails = ObjectUtil.getEffectiveEmails(context,owner);

                if (ownerEmails == null) {
                    if (log.isWarnEnabled())
                        log.warn("Work item owner (" + owner.getName() + ") has no email. " +
                                 "Could not send remediation notification.");
                } else {
                    // For now, we'll just use a map with a few pre-selected properties.
                    Map<String, Object> args = new HashMap<String, Object>();

                    // Pull the first comment out of the work item.
                    String comments = null;
                    if ((null != item.getComments()) && !item.getComments().isEmpty()) {
                        comments = item.getComments().get(0).getComment();
                    }

                    args.put("workItem", item);
                    args.put("workItemName", item.getDescription());
                    args.put("comments", comments);
                    if (null != requester) {
                        args.put("requesterName", requester.getDisplayableName());
                    }
                    EmailOptions ops = new EmailOptions(ownerEmails, args);
                    new Emailer(context, errorHandler).sendEmailNotification(email, ops);

                    // Set the notification date so we won't resend.
                    item.setNotification(new Date());

                    // This date is getting lost if we don't save and commit now,
                    // which will cause duplicate notifications.
                    context.saveObject(item);
                    context.commitTransaction();
                }
            }
        }
    }

    /**
     * Send a remediation notification to the affected user and possibly their
     * manager.  This does not currently support batching because we don't have
     * a field to trigger the batch request from right now.  For remediation
     * work items we use the notification field on the work item.  We can add
     * something later for this if we need to.
     */
    private void notifyRemediation(CertificationItem item)
        throws GeneralException {

        // KG - It would be nice to send a batch of these together per identity.
        // Eventually we may want to do this per bucket rather than per item.
        
        // Check if the option to notify is enabled.
        Certification cert = item.getCertification();
        Configuration sysConfig = this.context.getConfiguration();
        if (!Util.otob(cert.getAttribute(Configuration.NOTIFY_REMEDIATION,
                                         sysConfig.getAttributes()))) {
            return;
        }

        EmailTemplate email = getRemediationNotificationEmail(cert.getEmailTemplatePrefix());
        if (email != null) {
            Identity identity = item.getIdentity(this.context);

            // Only do this for identity certs.
            if (null != identity) {
                List<String> identityEmails = ObjectUtil.getEffectiveEmails(context,identity);

                if (identityEmails == null) {
                    if (log.isWarnEnabled())
                        log.warn("Certification identity (" + identity.getName() + ") has no email. " +
                                 "Could not send remediation notification.");
                } else {
                    Identity requester = item.getAction().getActor(this.context);

                    // Note: Rather than explicitly CC'ing the identity's manager,
                    // we'll leave it up to the email template to derive the CC if
                    // this is desired (ie - $!identity.manager.email).  Currently,
                    // email options doesn't have a way to specify a CC.  If we get
                    // pushback here, we can bake this behavior in.  I like this,
                    // though, because it makes the CC more configurable.

                    // For now, we'll just use a map with a few pre-selected properties.
                    Map<String, Object> args = new HashMap<String, Object>();

                    // Get the comment off of the remediation.
                    args.put("item", item);
                    args.put("remediationDetails", item.getAction().getRemediationDetails());
                    args.put("identity", identity);
                    args.put("comments", item.getAction().getComments());
                    if (null != requester) {
                        args.put("requesterName", requester.getDisplayableName());
                        args.put("requester", requester);
                    }

                    EmailOptions ops = new EmailOptions(identityEmails, args);
                    new Emailer(context, errorHandler).sendEmailNotification(email, ops);
                }
            }
        }
    }

    /**
     * Locate the default EmailTemplate to be used for notifying a user that an
     * item has been remediated.  Don't confuse with the remediation work item
     * email.
     */
    private EmailTemplate getRemediationNotificationEmail(String keyPrefix) throws GeneralException {
        return templateManager.getTemplate(keyPrefix, Configuration.REMEDIATION_NOTIFICATION_EMAIL_TEMPLATE);
    }

    /**
     * Locate the default EmailTemplate to be used for remediation notification
     * when a remediation work item is created.
     */
    private EmailTemplate getRemediationWorkItemEmail(String keyPrefix) throws GeneralException {
        return templateManager.getTemplate(keyPrefix, Configuration.REMEDIATION_WORK_ITEM_EMAIL_TEMPLATE);
    }

    /**
     * Check if any other items on the owning entity on the same account already
     * have an account revocation kicked off.
     */
    private CertificationItem getAccountRevokeDetails(CertificationItem item)
            throws GeneralException {
        CertificationItem accountRevokeDetails = null;
        // Only do the check if the given item is an account revocation request.
        if (item.getAction().isRevokeAccount()) {
            Filter itemsOnSameAccountFilter = CertificationEntity.getItemsOnSameAccountFilter(item);
            Filter accountRevokeDetailsFilter = Filter.and(itemsOnSameAccountFilter, Filter.eq("action.revokeAccount", true), Filter.eq("action.remediationKickedOff", true));
            accountRevokeDetails = context.getUniqueObject(CertificationItem.class, accountRevokeDetailsFilter);
        }

        return accountRevokeDetails;
    }

    /**
     * Copy the revocation details from one item that has already been processed
     * onto another item.
     */
    private void copyAccountRevokeDetails(CertificationItem from, CertificationItem into) {
        into.getAction().setRemediationCompleted(from.getAction().isRemediationCompleted());
        into.getAction().setRemediationDetails(from.getAction().getRemediationDetails());
        into.getAction().setWorkItem(from.getAction().getWorkItem());
    }
    
    /**
     * Set Remediation as kicked off and completed, with empty provisioning plan
     * @param item
     */
    private void setRemediationDetailsForMissingProvisioningPlan(CertificationItem item){
        item.getAction().setRemediationCompleted(true);
        item.getAction().setRemediationKickedOff(true);
        item.getAction().setRemediationDetails(new ProvisioningPlan());
        item.getAction().setReadyForRemediation(false);
    }
    
    /**
     * Used to select a native Identity if we were not able to find one in Plan Compilation.
     * We will select the first instance we find.
     * This happens when an Identity has multiple Links with the same entitlement. Because we do
     * not use workflow in Certification remediations, we have no user interaction after the Cert is complete.
     * Ideally, we would use workflow in certs or take care of this before the certification is completed.
     * @param provisioner
     * @throws GeneralException
     */
    private void selectNativeIdForAccountSelections(Provisioner provisioner) throws GeneralException{
        if(!Util.isEmpty(provisioner.getProject().getAccountSelections())) {
            for(AccountSelection as : provisioner.getProject().getAccountSelections()) {
                if(Util.isEmpty(as.getSelectedNativeIdentities())) {
                    List<String>nativeId = new ArrayList<String>();
                    for(AccountSelection.AccountInfo inf : Util.safeIterable(as.getAccounts())) {
                        nativeId.add(inf.getNativeIdentity());
                        break;
                    }
                    as.setSelectedNativeIdentities(nativeId);
                }
            }
            provisioner.recompile(provisioner.getProject(), null);
            provisioner.itemize(true);
        }
    }

    /**
     * Class containing the results of generating the plans for each tracking id.
     * Contains the plans map and the items which were filtered out during compilation.
     */
    private static class EntityPlans {

        /**
         * The plans.
         */
        private final Map<String, ProvisioningPlanSummary> plans;

        /**
         * The filtered values. The project does contain some filtered
         * values but this will contain the entire set of them which
         * includes anything that was filtered when the decision was saved.
         */
        private final List<AbstractRequest> filtered;

        /**
         * The provisioning project that resulted from the
         * entity plan compilation.
         */
        private final ProvisioningProject project;

        /**
         * Constructor.
         *
         * @param plans The plans.
         * @param project The project.
         * @param filtered The filtered values.
         */
        public EntityPlans(Map<String, ProvisioningPlanSummary> plans,
                           ProvisioningProject project,
                           List<AbstractRequest> filtered) {
            this.plans = plans;
            this.project = project;
            this.filtered = filtered;
        }

        /**
         * Gets the plans.
         *
         * @return The plans.
         */
        public Map<String, ProvisioningPlanSummary> getPlans() {
            return plans;
        }

        /**
         * Gets the filtered values.
         *
         * @return The filtered values.
         */
        public List<AbstractRequest> getFiltered() {
            return filtered;
        }

        /**
         * The resulting project from entity plan compilation.
         *
         * @return The project.
         */
        public ProvisioningProject getProject() {
            return project;
        }

    }
}
