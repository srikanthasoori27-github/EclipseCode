/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.NotificationConfig;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.messages.MessageKeys;

/**
 * This class creates WorkItems for various types of certification actions.
 *
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CertificationWorkItemBuilder {

    private static Log log = LogFactory.getLog(CertificationWorkItemBuilder.class);

    private SailPointContext context;

    /**
     * An error handler to use. Currently this is passed down from the parent caller so that
     * this class can append errors and warnings it encounters.
     */
    private final MessageAccumulator errorHandler;

    /**
     * Set of IDs of work items that are known to have remediations.  This is used to short-circuit
     * the process of determining whether or not a certification already has an existing remediation
     * work item for certifications with lots of remediations.  By caching this we avoid querying 
     * over and over for the same work item if we already did so. 
     */
    private Set<String> workItemsKnownToHaveRemediations;
    
    /* ------------------------------------------------------------------------
    *
    *  Constructors and Public methods
    *
    ------------------------------------------------------------------------* */

    /**
     * Create a new instance, passing in the context to use and an
     * error handler.
     * <p/>
     * * The error handler is passed in so we can add errors and warnings to the
     * parent caller's error stack.
     *
     * @param context      context SailPointContext, may not be null
     * @param errorHandler parent caller's error handler, may not be null
     */
    public CertificationWorkItemBuilder(SailPointContext context, final MessageAccumulator errorHandler) {
        assert (context != null);
        assert (errorHandler != null);
        this.context = context;
        this.errorHandler = errorHandler;
        workItemsKnownToHaveRemediations = new HashSet<String>();
    }

    /**
     * Create a new remediation WorkItem instance with the given info.
     *
     * @param certitem  The CertificationItem
     * @param action The CertificationAction
     * @param remediator Identity who will perform remediation
     * @return  WorkItem for the given remediation
     * @throws sailpoint.tools.GeneralException
     *
     */
    private WorkItem initializeRemediationWorkItem(AbstractCertificationItem certitem,
                                                   CertificationAction action,
                                                   Identity remediator)
            throws GeneralException {

        WorkItem item = new WorkItem();
        item.setType(WorkItem.Type.Remediation);
        item.setHandler(Certificationer.class);
        item.setRequester(action.getActor(context));
        item.setOwner(remediator);
        item.setDescription(action.getDescription());
        item.addComment(action.getComments(), action.getActor(context));
        // Don't set certification if we're certifying an entitlement outsideof a certification
        if (certitem.getCertification() != null)
            item.setCertification(certitem.getCertification().getId());

        if (certitem == null) {
            // we're certifying an entitlement outsideof a certification
        } else if (certitem instanceof CertificationEntity) {
            item.setCertificationEntity((CertificationEntity) certitem);
        } else if (certitem instanceof CertificationItem) {
            item.setCertificationItem((CertificationItem) certitem);
        }

        if (certitem.getCertification() != null) {
            // Setup the notification config for the remediation work items.
            // Right now we're overloading the notification info in the
            // remediation phase config to hold this.
            NotificationConfig notifConfig = null;
            CertificationPhaseConfig phaseCfg =
                    certitem.getCertification().getPhaseConfig(Certification.Phase.Remediation);
            if (null != phaseCfg) {
                notifConfig = phaseCfg.getNotificationConfig();
            }

            Configuration sysConfig = context.getConfiguration();
            int expDays = sysConfig.getInt(Configuration.DEFAULT_WORK_ITEM_DURATION);
            Date expiration = new Date(System.currentTimeMillis() + Util.MILLI_IN_DAY * expDays);

            item.setupNotificationConfig(context, expiration, notifConfig);
        }

        // unlike most places we build work items, we expect forwarding to have
        // already been done by generateRemediationWorkItem

        return item;
    }

    /**
     * Find a remediation work item assigned to the given remediator from this
     * certification.  This returns null if there is not one.
     *
     * @param certificationId ID of the certification for whom we are building work items
     * @param remediator Identity who will perform remediation
     * @return Remediation WorkItem is found, null if not
     * @throws GeneralException
     */
    private WorkItem findRemediationWorkItem(String certificationId,
                                             Identity remediator)
            throws GeneralException {

        WorkItem item = null;

        // todo - do we only want to search if there's a certId? Perhaps we just want to search by remediator?
        if (certificationId != null && null != remediator) {
            Filter f = Filter.and(Filter.eq("certification", certificationId),
                    Filter.eq("owner", remediator),
                    Filter.eq("type", WorkItem.Type.Remediation));
            QueryOptions qo = new QueryOptions();
            qo.add(f);

            //We may have existing work items for this remediator without any provisioning plan
            //This can happen in case of policy violations with no plan, for example (see bug 7875)
            //So we want to find a work item with remediation item(s) that have remediation details,
            //then use that one for the combined work item
            Iterator<Object[]> items = context.search(WorkItem.class, qo, "id");
            while (items != null && items.hasNext()) {
                boolean hasDetails = false;
                String workItemId = (String)items.next()[0];
                if (workItemsKnownToHaveRemediations.contains(workItemId)) {
                    // If we know this work item has remediations on it flag it appropriately.  No need to run a lengthy query
                    hasDetails = true;
                } else {
                    QueryOptions remQo = new QueryOptions();
                    remQo.add(Filter.eq("workItem.id", workItemId));
                    Iterator<Object[]> remediationItems = context.search(RemediationItem.class, remQo, "remediationDetails, attributes");
                    while (remediationItems != null && remediationItems.hasNext()) {
                        Object[] remItem = remediationItems.next();
                        //If there is no remediation details, it will come back as null from the projection query
                        if (remItem != null) {
                            if (remItem[0] != null ) {
                                hasDetails = true;
                                workItemsKnownToHaveRemediations.add(workItemId);
                                break;
                            } else if (remItem[1] != null) {
                                Attributes atts = (Attributes)remItem[1];
                                if (atts != null && atts.containsKey(RemediationItem.ARG_CONTRIBUTING_ENTS)) {
                                    //EffectiveSOD RemediationItem
                                    hasDetails = true;
                                    workItemsKnownToHaveRemediations.add(workItemId);
                                    break;
                                }
                            }
                        }
                    }
                    // Prevent an iterator leak on the remediation details
                    Util.flushIterator(remediationItems);                    
                }

                if (hasDetails) {
                    item = context.getObjectById(WorkItem.class, workItemId);;
                    break;
                }
            }
            // Prevent an iterator leak on the WorkItems
            Util.flushIterator(items);
        }

        return item;
    }


    /**
     * Create an new work item or add the remediation to an existing work item
     * for the given remediation action.  If there is already a remediation work
     * item for the specified remediator for this certification we will reuse it,
     * otherwise we'll create a new work item.  If a new WorkItem is created, it
     * is returned.  Note that even though this method saves changes it does not 
     * commit them.  The caller is responsible for committing.  This is a conscious
     * performance-based feature to enable changes to be made in large batches.
     *
     * @param certitem The abstract item being remediated.
     * @param plan Plan which has been stripped of any remediations which may be handled
     *  internally by the IIQ Provisioner. The remaining requests are those which cannot be
     *  handled in an automated fashion.
     * @param mergeWorkItem true to merge the workItem
     * @return The WorkItem that was generated if we created a new one, or null
     *         if an existing WorkItem gets the remediation added to it.
     * @throws GeneralException
     */
    public WorkItem generateRemediationWorkItem(CertificationItem certitem,
                                                ProvisioningPlan plan, 
                                                boolean mergeWorkItem)
            throws GeneralException {

        WorkItem item = null;
        boolean itemCreated = false;

        CertificationAction action = certitem.getAction();
        String remname = action.getOwnerName();
        Identity remediator = context.getObjectByName(Identity.class, remname);

        if (remediator == null) {
            // hmm, should we abort the entire refresh
            errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.INVALID_REMEDIATOR_IDENTITY, remname));
        } else {

            // Note that since we search for existing work items to consolidate
            // remediations, we have to do the auto-forwarding check now.
            // As of 5.0 we have to pass in a stub item with the type set so the
            // forwarding rule can make decisions based on type.  May be better
            // to just generate the full item and throw it away? - jsl
            WorkItem stub = new WorkItem();
            stub.setType(WorkItem.Type.Remediation);

            Workflower wf = new Workflower(context);
            Identity origRemediator = remediator;
            remediator = wf.checkForward(remediator, stub);

            String itemId = action.getWorkItem();
            if (itemId == null) {
                // Check if there is already a remediation work item for this
                // remediator on this certification.
                Certification cert = certitem.getCertification();

                if (mergeWorkItem) {
                    item = findRemediationWorkItem(cert != null ? cert.getId() : null, remediator);
                }

                // If there isn't an existing work item for this remediator,
                // create a new one.
                if (null == item) {
                    item = initializeRemediationWorkItem(certitem, action, origRemediator);
                    itemCreated = true;
                }
            } else {
                item = context.getObjectById(WorkItem.class, itemId);
                if (item == null) {
                    // A dangling reference.  This can happen after a work item
                    // is assimilated (ie - no longer active).  We shouldn't get
                    // here because this method assumes that the remediation is
                    // active.  Just log a warning and bail.
                    if (log.isWarnEnabled())
                        log.warn("No remedation work item being generated because " +
                                 "of dangling work item: " + itemId);
                    return null;
                } else {
                    Identity owner = item.getOwner();
                    if (owner == null) {
                        // this is really not supposed to happen
                        // reuse the WorkItem and assign the new owner
                    } else if (owner.getName().equals(remediator.getName())) {
                        // already generated this
                        //action.setOwnerName((String) null);
                        item = null;
                    } else {
                        // delete the old one and make a new one
                        item.setOwner(null);
                        context.removeObject(item);
                        item = initializeRemediationWorkItem(certitem, action, origRemediator);
                        itemCreated = true;
                    }
                }
            }

            if (item != null) {
                // Copy remediation information onto work item.  We're
                // duplicating this data, but it will save us from having to
                // resurrect a certification archive if the referenced
                // certification has already been archived.

                // Get info about the certification item, if this remediation is part of a cert
                String certItemId = certitem != null ? certitem.getId() : null;
               
                if (certitem != null){
                     String entityName = null;
                     RemediationItem.RemediationEntityType entityType = null;
                     switch (certitem.getType()){
                        case BusinessRoleGrantedScope:
                        case BusinessRoleGrantedCapability:
                        case BusinessRolePermit:
                        case BusinessRoleRequirement:
                        case BusinessRoleHierarchy:
                            entityName = certitem.getParent().getTargetName();
                            entityType = RemediationItem.RemediationEntityType.BusinessRole;
                            break;
                        case BusinessRoleProfile:
                            entityName = certitem.getParent().getTargetName();
                            entityType = RemediationItem.RemediationEntityType.Profile;
                            break;
                        case AccountGroupMembership:
                        case DataOwner:
                            entityName = certitem.getTargetName();
                            entityType = RemediationItem.RemediationEntityType.Identity;
                            break;
                        default:
                            entityName = certitem.getIdentity();
                            entityType = RemediationItem.RemediationEntityType.Identity;
                    }

                    //If EffectiveSOD PolicyViolation, need to pass the Entitlements To Remediate to the RemediationItem
                    List<PolicyTreeNode> ents = null;
                    boolean passEnts;
                     if (certitem.getPolicyViolation() != null) {
                         PolicyViolation violation = certitem.getPolicyViolation();
                         Policy p = violation.getPolicy(context);
                         String type = null;
                         if (p != null) {
                             type = p.getType();
                         }
                         passEnts = Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(type) || null == plan;
                         if (passEnts) {
                             ents = violation.getEntitlementsToRemediate();
                             //Currently can't have plan and entitlementsToRemediate. Clear plan.
                             //TODO: Allow both plan && entitlemntsToRemediate. Plan can be generated for those revokes we know we can handle
                             plan = null;
                         }
                     }

                    RemediationItem removed =
                        item.addRemediationItem(certitem.getCertification(), certItemId,  entityName, entityType,
                                action, plan, ents);
                    if (null != removed) {
                        this.context.removeObject(removed);
                    }
                }
                
                if (itemCreated) {
                    // Note that this has the side effect of committing the new work item so that it can be found later
                    wf.open(item);
                    workItemsKnownToHaveRemediations.add(item.getId());
                } else {
                    // added another remediation item to an already open work item
                    context.saveObject(item);
                    /* 
                     * Save, but don't commit yet.  We'll commit after the current batch 
                     * has been processed.  See bug 12585 for details.  --Bernie
                     */
                }

                // be sure to remember the id so we don't do this again
                action.setWorkItem(item.getId());
            }
        }

        return (itemCreated) ? item : null;
    }


    /**
     * Creates a remediation WorkItem for an individual policy violation.
     * This type of work item is like a normal certification item work item, but it
     * is not associated with a certification.
     *
     * @param policyViolationId
     * @param identityToRemediate Identity getting remediated
     * @param plan  details of the action
     * @param actor Identity creating the work item
     * @param recipientName work item assignee
     * @param description Description from actor
     * @param comments Comments made by actor
     * @return WorkItem for the given identity and action
     * @throws GeneralException
     */
    public WorkItem generatePolicyViolationItem(String policyViolationId, String identityToRemediate,
                                                ProvisioningPlan plan, Identity actor,
                                                String recipientName, String description, String comments,
                                                List<PolicyTreeNode> violatingEntitlements)
            throws GeneralException {

        Identity remediator = context.getObjectByName(Identity.class, recipientName);

        if (remediator == null) {
            // hmm, should we abort the entire refresh
            errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.INVALID_REMEDIATOR_IDENTITY, recipientName));
            return null;
        }

        WorkItem item = new WorkItem();
        item.setType(WorkItem.Type.Remediation);
        item.setHandler(PolicyViolationWorkItemHandler.class);
        item.setRequester(actor);
        item.setOwner(remediator);
        item.setDescription(description);
        item.addComment(comments, actor);
        item.setTargetClass(PolicyViolation.class);
        item.setTargetId(policyViolationId);

        //Configuration sysConfig = ObjectUtil.getSysConfig(context);
        //int expDays = sysConfig.getInt(Configuration.DEFAULT_WORK_ITEM_DURATION);
        // Date expiration = new Date(System.currentTimeMillis() + Util.MILLI_IN_DAY * expDays);
        // item.setupNotificationConfig(context, expiration, notifCfg)

        item.addRemediationItem(actor, RemediationItem.RemediationEntityType.Identity, identityToRemediate,  plan,
                description, comments, violatingEntitlements);

        // save the item and check forwarding
        Workflower wf = new Workflower(context);
        wf.open(item);

        return item;
    }


}
