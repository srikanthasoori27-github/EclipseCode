/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A single remediation request that can be worked on as part of a WorkItem.
 * 
 * @author Kelly Grizzle
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.certification.PolicyTreeNode;

/**
 * Used to represent a single remediation request that can be
 * included in a work item.
 */
@XMLClass
public class RemediationItem extends SailPointObject implements AssignableItem {

    /**
     * Type type of object being remediated.
     */
    @XMLClass(xmlname = "RemediationEntityType")
    public static enum RemediationEntityType {

        Identity("Identity"),
        AccountGroup("Account Group"),
        Profile("Profile"),
        BusinessRole("Business Role");

        private String displayName;

        private RemediationEntityType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    /**
     * Back-pointer to the work item that owns this remediation item.
     */
    WorkItem workItem;

    /**
     * The ID of the CertificationItem that is being remediated. Storing as
     * a string ID rather than an object reference because Certifications
     * can be archived while the work item is still alive.
     */
    String certificationItem;

    /**
     * The name of the identity for which the entitlements need to be
     * remediated.  
     * 
     * @ignore
     * Note: we're storing this on the RemediationItem rather
     * than accessing it through the certification references because
     * the certification may already be archived and resurrecting this
     * information could be quite expensive.  This information should
     * correspond to the remediation details stored on the
     * CertificationAction for the referenced certification item, though.
     */
    String remediationIdentity;


    RemediationEntityType remediationEntityType;

    /**
     * The ProvisioningPlan to be executed to remediate the item.
     * 
     * @see #remediationIdentity
     */
    ProvisioningPlan remediationDetails;


    public static String ARG_CONTRIBUTING_ENTS = "contributingEntitlements";

    //TODO: Store information for presenting Manual Provisioning (Effective SOD). Can't determine a plan, but
    // have some information to present to aid end user in handling
    Attributes<String,Object> _attributes;


    /**
     * The List of comments on this item.
     */
    List<Comment> comments;

    /**
     * The comments entered at completion time for this remediation item.
     */
    String completionComments;

    /**
     * The date at which this remediation item was completed.
     */
    Date completionDate;

    /**
     * Whether this item has been assimilated back into the Certification or
     * not.
     */
    boolean assimilated;

    /**
     * The identity that is currently assigned to this object.
     */
    Identity assignee;

    ////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////

    public RemediationItem() { super(); }

    /**
     * Constructor.
     * 
     * @param  certItemId           The ID of the certification ID.
     * @param  type                 Type of object being remediated                              
     * @param  remediationIdentity  The name of the user being remediated.
     * @param  action               The CertificationAction with the
     *                              remediation information.
     * @param  plan                 ProvisioningPlan to describe extra remediation details
     */
    public RemediationItem(String certItemId, RemediationEntityType type, String remediationIdentity,
                           CertificationAction action, ProvisioningPlan plan) {

        super();
       
        this.remediationEntityType = type;
        this.certificationItem = certItemId;
        this.remediationIdentity = remediationIdentity;
        this.remediationDetails = plan;

        super.setDescription(action.getDescription());
        this.comments = new ArrayList<Comment>();
        String author = action.getActorName();
        this.comments.add(new Comment(action.getComments(), author));
    }

    /**
     * Create a new remediation item that does not belong to a certification. An example is the
     * standalone remediation of policy violations.
     *
     * @param actor Identity requesting this remediation
     * @param remediationIdentity The name of the user being remediated.
     * @param type  Type of object being remediated                              
     * @param plan The ProvisioningPlan to execute to remediate the item.
     * @param description Textual description provided by the actor
     * @param comments Comments created by the actor
     */
    public RemediationItem(Identity actor, String remediationIdentity, RemediationEntityType type,
                           ProvisioningPlan plan, String description,
                           String comments) {

        super();
        super.setDescription(description);
        this.remediationEntityType = type;
        this.remediationIdentity = remediationIdentity;
        this.remediationDetails = plan;
        this.comments = new ArrayList<Comment>();
        this.comments.add(new Comment(comments, actor.getDisplayName()));
    }

    @Override
    public boolean hasName() {
        return false;
    }


    ////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////

    /**
     * Complete this remediation item.
     * 
     * @param  comments  The completion comments.
     */
    public void complete(String comments) {
        // TODO: should we throw an exception if this gets completed twice?
        completionComments = comments;
        completionDate = new Date();
    }

    /**
     * Add a comment to this remediation item.
     * 
     * @param  comment  The comment text.
     * @param  author   The author of the comment.
     */
    public void addComment(String comment, Identity author) {
        if (null == this.comments) {
            this.comments = new ArrayList<Comment>();
        }
        this.comments.add(new Comment(comment, (null != author) ? author.getDisplayableName() : null));
    }

    /**
     * Mark this item as having been assimilated into the certification.
     */
    public void assimilate() {
        assimilated = true;
    }

    /**
     * Pseudo-property that says whether this remediation has been completed.
     * 
     * @return True if this remediation item has been complete, false otherwise.
     */
    public boolean isComplete() {
        return (null != this.completionDate);
    }


    ////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////

    public void setWorkItem(WorkItem workItem) {
        this.workItem = workItem;
    }

    /**
     * Back-pointer to the work item that owns this remediation item.
     */
    public WorkItem getWorkItem() {
        return this.workItem;
    }
    
    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use constructor 
     */
    @Deprecated
    @XMLProperty
    public void setCertificationItem(String item) {
        certificationItem = item;
    }

    /**
     * The ID of the CertificationItem that is being remediated. Storing as
     * a string ID rather than an object reference because Certifications
     * may be archived while the work item is still alive.
     */
    public String getCertificationItem() {
        return certificationItem;
    }

    public CertificationItem getCertificationItem(Resolver resolver)
        throws GeneralException {
        return (null != certificationItem)
            ? resolver.getObjectById(CertificationItem.class, certificationItem)
            : null;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use constructor 
     */
    @Deprecated
    @XMLProperty
    public void setRemediationIdentity(String remediationIdentity) {
        this.remediationIdentity = remediationIdentity;
    }

    /**
     * The name of the identity for which the entitlements need to be
     * remediated.
     */
    public String getRemediationIdentity() {
        return remediationIdentity;
    }


    public RemediationEntityType getRemediationEntityType() {
        return remediationEntityType;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use constructor 
     */
    @Deprecated
    @XMLProperty
    public void setRemediationEntityType(RemediationEntityType remediationEntityType) {
        this.remediationEntityType = remediationEntityType;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use constructor 
     */
    @Deprecated
    @XMLProperty(xmlname="RemediationPlan")
    public void setRemediationDetails(ProvisioningPlan plan) {
        this.remediationDetails = plan;
    }

    /**
     * The ProvisioningPlan to be executed to remediate the item.
     */
    public ProvisioningPlan getRemediationDetails() {
        return this.remediationDetails;
    }

    /**
     * If this was from remediating an effective SOD policy, there will
     * be contributing entitlements, and will there will not be a
     * provisioning plan.
     * @return
     */
    public List<PolicyTreeNode> getContributingEntitlements() {
        return (List<PolicyTreeNode>)getAttribute(ARG_CONTRIBUTING_ENTS);
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use {@link #addComment(String, Identity)} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="RemediationItemComments")
    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    /**
     * The List of comments on this item.
     */
    public List<Comment> getComments() {
        return comments;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use {@link #complete(String)} 
     */
    @Deprecated
    @XMLProperty
    public void setCompletionComments(String completionComments) {
        this.completionComments = completionComments;
    }

    /**
     * The comments entered at completion time for this remediation item.
     */
    public String getCompletionComments() {
        return completionComments;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use {@link #complete(String)} 
     */
    @Deprecated
    @XMLProperty
    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }

    /**
     * The date at which this remediation item was completed.
     */
    public Date getCompletionDate() {
        return completionDate;
    }

    /**
     * @exclude
     * This is an immutable property - setter required for
     *              persistence frameworks.
     * @deprecated use {@link #assimilate()} 
     */
    @Deprecated
    @XMLProperty
    public void setAssimilated(boolean assimilated) {
        this.assimilated = assimilated;
    }

    /**
     * True if this item has been assimilated back into the Certification.
     */
    public boolean isAssimilated() {
        return assimilated;
    }

    @XMLProperty
    public void setAssignee(Identity assignee) {
        this.assignee = assignee;
    }

    /**
     * Get the assignee for this item
     */
    public Identity getAssignee() {
        return assignee;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() { return _attributes; }

    public void setAttributes(Attributes<String,Object> atts) { this._attributes = atts; }

    public void setAttribute(String key, Object value) {
        if (_attributes == null) {
            _attributes = new Attributes<>();
        }

        _attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        if (_attributes != null) {
            return _attributes.get(key);
        }
        return null;
    }


}
