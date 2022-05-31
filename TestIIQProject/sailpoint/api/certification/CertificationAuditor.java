/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.CertificationCommand.BulkReassignment;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * CertificationAuditor audits and provides adapters between AuditEvent and
 * various certification-related audit events.  Currently, some items that are
 * audited do not have an adapter class because they are not accessed through
 * code (only by viewing the audit log).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationAuditor {

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Base class for certification audit events - includes common fields of
     * comments and description (although they might not always be used).
     */
    public static abstract class BaseAudit {

        // Used to audit the final (or actual) recipient/owner of an item
        // if it was auto-forwarded past the requested recipient.
        public static final String ATTR_FINAL_RECIPIENT = "finalRecipient";
        
        public static final String ATTR_COMMENTS = "comments";
        public static final String ATTR_DESCRIPTION = "description";

        AuditEvent event;

        /**
         * Default constructor.  Subclass should set event if calling this.
         */
        public BaseAudit() {
        }
        
        /**
         * Construct from an AuditEvent.
         */
        public BaseAudit(AuditEvent event) {
            this.event = event;
        }

        /**
         * Return the actual audit event.  Should usually use other methods to
         * get specific data.
         */
        AuditEvent getEvent() {
            return this.event;
        }

        /**
         * Return the date at which the event occurred.
         */
        public Date getDate() {
            return this.event.getCreated();
        }

        /**
         * Return the source of the event (who caused it).
         */
        public String getSource() {
            return this.event.getSource();
        }

        /**
         * Return the comments for this event.
         */
        public String getComments() {
            return (String) this.event.getAttribute(ATTR_COMMENTS);
        }

        /**
         * Return the descriptions for this event.
         */
        public String getDescription() {
            return (String) this.event.getAttribute(ATTR_DESCRIPTION);
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // ReassignmentAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A representation of a reassignment audit event.
     */
    public static class ReassignmentAudit extends BaseAudit {

        public static final String ATTR_SELF_CERTIFICATION_REASSIGNMENT = "selfCertification";

        public ReassignmentAudit(AuditEvent event) {
            super(event);
        }
        
        public ReassignmentAudit(BulkReassignment cmd, Identity origRecipient,
                                 Certification cert, Certification newCert) {
            String requester = cmd.getRequester().getName();
            List<String> certifiers = cert.getCertifiers();

            // Default the previous owner to the first certifier in the
            // list.  If we find one that matches the requester, use it.
            String prevOwner = certifiers.get(0);
            for (String certifier : certifiers) {
                if (certifier.equals(requester)) {
                    prevOwner = certifier;
                    break;
                }
            }
            
            // note that you must pass the name here, not the Identity
            // or else we'll think this is a specific object event.
            // We're using the requested recipient as the target instead
            // of the final recipient.  The final recipient is stored in
            // the attributes map.
            this.event =
                new AuditEvent(requester, AuditEvent.ActionReassign,
                               origRecipient.getName());
            this.event.setString1(cert.getId());
            this.event.setString2(newCert.getId());
            this.event.setString3(prevOwner);
            this.event.setAttribute(ATTR_COMMENTS, cmd.getComments());
            this.event.setAttribute(ATTR_DESCRIPTION, cmd.getDescription());
            this.event.setAttribute(ATTR_FINAL_RECIPIENT, cmd.getRecipient().getName());
            this.event.setAttribute(ATTR_SELF_CERTIFICATION_REASSIGNMENT, cmd.isSelfCertificationReassignment());
        }

        /**
         * Return the ID of the certification from which the reassignment came.
         */
        public String getParentCertificationId() {
            return this.event.getString1();
        }

        /**
         * Return the ID of the new reassignment certification.
         */
        public String getReassignmentCertificationId() {
            return this.event.getString2();
        }

        /**
         * Return the owner of the original certification.
         */
        public String getPreviousOwner() {
            return this.event.getString3();
        }

        /**
         * Return the requested owner of the new reassignment certification -
         * note that this may not be the actual owner if any auto-forwarding
         * occurred.
         */
        public String getNewOwner() {
            return this.event.getTarget();
        }
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CertificationRescindAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A representation of a rescind certification audit event.
     */
    public static class CertificationRescindAudit extends BaseAudit {

        public CertificationRescindAudit(AuditEvent event) {
            super(event);
        }
        
        public CertificationRescindAudit(Certification cert) {

            String requester = Util.listToCsv(cert.getParent().getCertifiers());
            this.event =
                new AuditEvent(requester, AuditEvent.ActionRescindCertification,
                               cert.getParent().getName());
            this.event.setString1(cert.getId());
            this.event.setString2(cert.getParent().getId());
            
            // New and previous owners.
            this.event.setString3(Util.listToCsv(cert.getCertifiers()));
            this.event.setString4(requester);
        }

        public String getNewOwner() {
            return this.event.getString4();
        }

        public String getPreviousOwner() {
            return this.event.getString3();
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseDelegationAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A base class for all delegation-related audits.
     */
    public static abstract class BaseDelegationAudit extends BaseAudit {
        
        public static final String ATTR_NEW_OWNER = "newOwner";
        public static final String ATTR_PREVIOUS_OWNER = "previousOwner";
        
        public BaseDelegationAudit(AuditEvent event) {
            super(event);
        }

        public BaseDelegationAudit(SailPointContext context, WorkItem item,
                                   AbstractCertificationItem certitem)
            throws GeneralException {
            this.event = createAuditEvent(context, item, certitem);
            this.addCommonFields(this.event, item, certitem);
        }
        
        /**
         * Implement this to create an AuditEvent for the delegation audit
         * subclass.  This should setup the target, action, and source at a
         * minimum, as well as any other info specific to the audit event type.
         */
        abstract AuditEvent createAuditEvent(SailPointContext context, WorkItem item,
                                             AbstractCertificationItem certitem)
            throws GeneralException;

        /**
         * Fill in the fields that are common to all delegation audit events.
         */
        private void addCommonFields(AuditEvent event, WorkItem item, AbstractCertificationItem certitem) {

            String auditInfo1 = null;
            String auditInfo2 = null;
            if (certitem instanceof CertificationEntity) {
                CertificationEntity cid = (CertificationEntity) certitem;
                auditInfo1 = cid.getIdentity();
            }
            else if (certitem instanceof CertificationItem) {
                CertificationItem cit = (CertificationItem) certitem;

                // jsl - ugh, would be nice to encapsulate this in
                // the model
                CertificationItem.Type itype = cit.getType();
                CertificationEntity parent = cit.getParent();
                if (itype == CertificationItem.Type.Bundle) {
                    if (parent == null) {
                        // must be a role cert
                        auditInfo1 = cit.getBundle();
                    }
                    else {
                        auditInfo1 = parent.getIdentity();
                        auditInfo2 = cit.getBundle();
                    }
                }
                else if ((itype == CertificationItem.Type.Exception) ||
                         (itype == CertificationItem.Type.AccountGroupMembership) ||
                         (itype == CertificationItem.Type.DataOwner)) {
                    if (parent != null)
                        auditInfo1 = parent.getIdentity();
                    // could try to derive some shorthand for the exception
                    auditInfo2 = "Exception";
                }
                else if (itype == CertificationItem.Type.PolicyViolation) {
                    if (parent != null)
                        auditInfo1 = parent.getIdentity();
                    auditInfo2 = "Policy Violation";
                }
            }

            event.setString1(auditInfo1);
            event.setString2(auditInfo2);
            event.setString3(certitem.getId());
            event.setString4(item.getId());
        }

        /**
         * Return the owner of the parent work item (if this is an item
         * delegated from an entity) or the certifiers - whichever is available.
         */
        static String getParentWorkItemOwnerOrCertifiers(SailPointContext context,
                                                         WorkItem item,
                                                         AbstractCertificationItem certitem)
            throws GeneralException {

            String prevOwner = null;
            String actingWorkItem = certitem.getDelegation().getActingWorkItem();
            if (null != actingWorkItem) {
                WorkItem parentWorkItem =
                    context.getObjectById(WorkItem.class, actingWorkItem);
                if (null != parentWorkItem) {
                    prevOwner = parentWorkItem.getOwner().getName();
                }
            }

            if (null == prevOwner) {
                // Might be able to make a guess on the certifier if there are
                // multiple based on the source, but we'll just turn it into a
                // list for simplicity.
                prevOwner = Util.listToCsv(certitem.getCertification().getCertifiers());
            }
            
            return prevOwner;
        }

        protected static void setPreviousOwner(AuditEvent event, String prevOwner) {
            event.setAttribute(ATTR_PREVIOUS_OWNER, prevOwner);
        }
        
        protected static void setNewOwner(AuditEvent event, String newOwner) {
            event.setAttribute(ATTR_NEW_OWNER, newOwner);
        }        

        /**
         * Return the previous owner.
         */
        public String getPreviousOwner() {
            return (String) this.event.getAttribute(ATTR_PREVIOUS_OWNER);
        }

        /**
         * Return the new owner.
         */
        public String getNewOwner() {
            return (String) this.event.getAttribute(ATTR_NEW_OWNER);
        }

        /**
         * Return the entity name (identity, role, etc... depending on the type
         * of certification.
         */
        public String getEntityName() {
            return this.event.getString1();
        }

        /**
         * Return a description of the item that was delegated (if available).
         */
        public String getItemDescription() {
            return this.event.getString2();
        }

        /**
         * Return the ID of the AbstractCertificationItem.
         */
        public String getAbstractCertificationItemId() {
            return this.event.getString3();
        }

        /**
         * Return the ID of the WorkItem for the delegation.
         * @return
         */
        public String getWorkItemId() {
            return this.event.getString4();
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // DelegationAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * An audit for a delegation.
     */
    public static class DelegationAudit extends BaseDelegationAudit {
        
        public DelegationAudit(AuditEvent event) {
            super(event);
        }

        public DelegationAudit(SailPointContext context, WorkItem item,
                               AbstractCertificationItem certitem)
            throws GeneralException {
            super(context, item, certitem);
        }

        @Override
        AuditEvent createAuditEvent(SailPointContext context, WorkItem item,
                                    AbstractCertificationItem certitem)
            throws GeneralException {

            // jsl - what is interesting to say about a delegation?
            // hmm, here's a case where unnamed columns is going to
            // be hard to understand.  We could audit the original owner,
            // the delegate, and the identity being certified, but
            // without column headers it is hard to know which is which.
            // It might be better to audit formatted attributes such
            // as "identity=xyz" "delegate=abc"
            // We're setting the requested recipient as the target since
            // any auto-fowards will also get audited.
            CertificationDelegation del = certitem.getDelegation();
            String source = del.getActorName();
            AuditEvent event =
                new AuditEvent(source, AuditEvent.ActionDelegate, del.getOwnerName());
            event.setAttribute(ATTR_COMMENTS, del.getComments());
            event.setAttribute(ATTR_DESCRIPTION, del.getDescription());
            event.setAttribute(ATTR_FINAL_RECIPIENT, item.getOwner().getName());

            // This is the requested recipient (may not be the actual recipient).
            setNewOwner(event, del.getOwnerName());

            // Previous owner is either the certification owner or owning
            // delegation work item owner (if item is delegated from entity).
            setPreviousOwner(event, getParentWorkItemOwnerOrCertifiers(context, item, certitem));

            return event;
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // DelegationCompletionAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * An audit for a delegation completion - this may be a completion, return,
     * expiration, etc...
     */
    public static class DelegationCompletionAudit extends BaseDelegationAudit {
        
        public static final String ATTR_COMPLETION_STATE = "completionState";
        
        public DelegationCompletionAudit(AuditEvent event) {
            super(event);
        }

        public DelegationCompletionAudit(SailPointContext context, WorkItem item,
                                         AbstractCertificationItem certitem)
            throws GeneralException {
            super(context, item, certitem);
        }

        @Override
        AuditEvent createAuditEvent(SailPointContext context, WorkItem item,
                                    AbstractCertificationItem certitem) 
            throws GeneralException {

            String source = item.getOwner().getName();
            AuditEvent event =
                new AuditEvent(source, AuditEvent.ActionCompleteDelegation, item);
            event.setAttribute(ATTR_COMMENTS, item.getCompletionComments());
            event.setAttribute(ATTR_COMPLETION_STATE, item.getState().toString());

            // If this was delegated from another work item, it goes back to
            // that owner if the work item is still around.  Otherwise, it goes
            // back to the cert owner.
            setNewOwner(event, getParentWorkItemOwnerOrCertifiers(context, item, certitem));
            setPreviousOwner(event, item.getOwner().getName());

            return event;
        }

        /**
         * Return the completion state of the delegation work item.
         */
        public WorkItem.State getCompletionState() {
            return WorkItem.State.valueOf((String) this.event.getAttribute(ATTR_COMPLETION_STATE));
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // DelegationRevocationAudit inner class
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * An audit for a delegation revocation.
     */
    public static class DelegationRevocationAudit extends BaseDelegationAudit {
        
        public DelegationRevocationAudit(AuditEvent event) {
            super(event);
        }

        public DelegationRevocationAudit(SailPointContext context, WorkItem item,
                                         AbstractCertificationItem certitem)
            throws GeneralException {
            super(context, item, certitem);
        }

        @Override
        AuditEvent createAuditEvent(SailPointContext context, WorkItem item,
                                    AbstractCertificationItem certitem)
            throws GeneralException {

            // The source was either the parent work item owner or the certifier.
            String source = getParentWorkItemOwnerOrCertifiers(context, item, certitem);
            
            AuditEvent event =
                new AuditEvent(source, AuditEvent.ActionRevokeDelegation, item);

            // Set the new owner.
            setNewOwner(event, source);
            
            // Set previous owner.
            setPreviousOwner(event, item.getOwner().getName());

            return event;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private SailPointContext context;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public CertificationAuditor(SailPointContext context) {
        this.context = context;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // REASSIGNMENT
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Audit a reassignment event.
     */
    public void auditReassignment(BulkReassignment cmd, Identity origRecipient,
                                  Certification cert, Certification newCert) {

        if (Auditor.isEnabled(AuditEvent.ActionReassign)) {
            ReassignmentAudit audit = new ReassignmentAudit(cmd, origRecipient, cert, newCert);
            Auditor.log(audit.getEvent());
        }
    }

    /**
     * Return the ReassignmentAudit for the reassignment certification with the
     * given ID.
     */
    public ReassignmentAudit getReassignmentAudit(String certId)
        throws GeneralException {

        // This should be unique.
        Filter f = Filter.and(Filter.eq("action", AuditEvent.ActionReassign),
                              Filter.eq("string2", certId));
        AuditEvent evt = this.context.getUniqueObject(AuditEvent.class, f);
        return (null != evt) ? new ReassignmentAudit(evt) : null;
    }

    /**
     * Audit a certification rescind event.
     */
    public void auditRescind(Certification cert) {
        if (Auditor.isEnabled(AuditEvent.ActionRescindCertification)) {
            CertificationRescindAudit audit = new CertificationRescindAudit(cert);
            Auditor.log(audit.getEvent());
        }
    }

    /**
     * Return the CertificationRescindAudit for the certification with the given
     * ID.
     * 
     * @param  certId  The ID of the certification that was rescinded.
     */
    public CertificationRescindAudit getRescindAudit(String certId)
        throws GeneralException {
        
        // This should be unique.
        Filter f = Filter.and(Filter.eq("action", AuditEvent.ActionRescindCertification),
                              Filter.eq("string1", certId));
        AuditEvent evt = this.context.getUniqueObject(AuditEvent.class, f);
        return (null != evt) ? new CertificationRescindAudit(evt) : null;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // DELEGATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Audit a delegation event.
     */
    public void auditDelegation(WorkItem item, AbstractCertificationItem aCertitem)
        throws GeneralException {
        
        if (Auditor.isEnabled(AuditEvent.ActionViolationDelegate)) {
            if (aCertitem instanceof CertificationItem) {
                CertificationItem certitem = (CertificationItem) aCertitem;
                if (CertificationItem.Type.PolicyViolation.equals(certitem.getType())) {
                    
                    PolicyViolation pv = certitem.getPolicyViolation();
                    
                    // Use DelegationAudit to create an event, but don't use the event, just scavenge its fields
                    DelegationAudit audit = new DelegationAudit(this.context, item, aCertitem);
                    AuditEvent evt = audit.getEvent();
                    
                    String comments = evt.getAttribute(DelegationAudit.ATTR_COMMENTS).toString();
                    String newOwner = audit.getNewOwner();
                    String prevOwner = audit.getPreviousOwner();
                    String source = audit.getSource();
                    evt = null;
                    audit = null;
                    
                    Identity assignee = context.getObjectByName(Identity.class, newOwner);
                    Identity actor = context.getObjectByName(Identity.class, source);
                    
                    PolicyViolationAuditor.auditDelegation(pv, actor, AuditEvent.SourceViolationCertification, comments, assignee, prevOwner);
                }
            }
        }
        
        if (Auditor.isEnabled(AuditEvent.ActionDelegate)) {
            DelegationAudit audit =
                new DelegationAudit(this.context, item, aCertitem);
            Auditor.log(audit.getEvent());
        }
    }

    /**
     * Find all DelegationAudit events for the given CertificationItem,
     * including item or entity delegations.
     */
    public List<DelegationAudit> findDelegations(CertificationItem item)
        throws GeneralException {
        
        List<DelegationAudit> delegations = new ArrayList<DelegationAudit>();

        // Search for item and entity delegations.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("action", AuditEvent.ActionDelegate));
        qo.add(Filter.or(Filter.eq("string3", item.getId()),
                         Filter.eq("string3", item.getParent().getId())));
        List<AuditEvent> audits = this.context.getObjects(AuditEvent.class, qo);
        for (AuditEvent audit : audits) {
            delegations.add(new DelegationAudit(audit));
        }
        
        return delegations;
    }

    /**
     * Audit a delegation completion event.
     */
    public void auditDelegationCompletion(WorkItem item, AbstractCertificationItem certitem)
        throws GeneralException {

        if (Auditor.isEnabled(AuditEvent.ActionCompleteDelegation)) {
            DelegationCompletionAudit audit =
                new DelegationCompletionAudit(this.context, item, certitem);
            Auditor.log(audit.getEvent());
        }
    }

    /**
     * Return a DelegationCompletionAudit for the delegation work item with the
     * given ID.
     */
    public DelegationCompletionAudit getDelegationCompletion(String workItemId)
        throws GeneralException {
        
        // This should be unique - each work item can only be completed once.
        Filter f = Filter.and(Filter.eq("action", AuditEvent.ActionCompleteDelegation),
                              Filter.eq("string4", workItemId));
        AuditEvent evt = this.context.getUniqueObject(AuditEvent.class, f);
        return (null != evt) ? new DelegationCompletionAudit(evt) : null;
    }

    /**
     * Audit a delegation revocation event.
     */
    public void auditDelegationRevocation(WorkItem item, AbstractCertificationItem certitem)
        throws GeneralException {
        
        if (Auditor.isEnabled(AuditEvent.ActionRevokeDelegation)) {
            DelegationRevocationAudit audit =
                new DelegationRevocationAudit(this.context, item, certitem);
            Auditor.log(audit.getEvent());
        }
    }

    /**
     * Return a DelegationRevocationAudit for the delegation work item with the
     * given ID.
     */
    public DelegationRevocationAudit getDelegationRevocation(String workItemId)
        throws GeneralException {
        
        // This should be unique - each delegation can only be revoked once.
        Filter f = Filter.and(Filter.eq("action", AuditEvent.ActionRevokeDelegation),
                              Filter.eq("string4", workItemId));
        AuditEvent evt = this.context.getUniqueObject(AuditEvent.class, f);
        return (null != evt) ? new DelegationRevocationAudit(evt) : null;
    }
}
