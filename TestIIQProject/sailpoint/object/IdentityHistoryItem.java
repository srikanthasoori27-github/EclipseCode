/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. 
 * 
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */

package sailpoint.object;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Certification.EntitlementGranularity;
import sailpoint.policy.AccountPolicyExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * The IdentityHistoryItem class is used to track the history, particularly
 * history involving certifications, for an Identity. At the time of this
 * writing, either certification decisions made against an
 * Identity (for example - role, entitlement, policy violation) or comments made
 * against a particular entitlement for that Identity can be recorded.
 */
@XMLClass
public class IdentityHistoryItem extends SailPointObject {
    private static final long serialVersionUID = -6081589135744092335L;

    private static final Log log = LogFactory.getLog(IdentityHistoryItem.class);

    // A few of the inlined fields are shorter than the object that they come
    // from.  These are truncated when they are set.
    private static final int MAX_LENGTH_ATTR = 450;
    private static final int MAX_LENGTH_VALUE = 450;
    private static final int MAX_LENGTH_CONSTRAINT_NAME = 450;

    private Identity identity;

    /**
     * The type of the identity history item
     */
    private Type type;

    /**
     * A "unique identifier" for the identity history item
     */
    private CertifiableDescriptor certifiableDescriptor;

    /**
     * The action taken on the given certification item
     * Only used if history item type "Decision".
     * 
     * @ignore
     * DHC - Why do we keep this?  We pull all the useful bits out of it,
     * so why maintain the entire object here?
     */
    private CertificationAction action;

    /**
     * A pointer back to the original certification containing the item
     * Only used if history item type "Decision"
     */
    private CertificationLink certificationLink;

    /**
     * Comments made on a given entitlement
     * Only used if history item type "Comment"
     */
    private String comments;

    /**
     * The type of the certification item
     */
    private CertificationItem.Type certificationType;

    /**
     * The status of the action recorded by this history item
     */
    private CertificationAction.Status status;

    /**
     * The name of the person/process responsible for this history item
     */
    private String actor;

    /**
     * The date the activity associated with this history item was performed
     */
    private Date entryDate;

    /**
     * Searchable fields for an entitlement cert
     */
    private String application;

    private String instance;

    private String account;

    private String nativeIdentity;

    private String attribute;

    private String value;

    /**
     * Searchable fields for a policy violation
     */
    private String policy;

    // "constraint" is a reserved word in mysql
    private String constraintName;

    /**
     * Searchable field for a role certification
     */
    private String role;


    /**
     * History item types:
     * <p/>
     * Decision - a certification decision for an entitlement
     * Comment - a comment made on an entitlement
     */
    @XMLClass(xmlname = "IdentityHistoryItemType")
    public static enum Type {
        Decision(MessageKeys.ENT_HISTORY_ITEM_TYPE_DECISION),
        Comment(MessageKeys.ENT_HISTORY_ITEM_TYPE_COMMENT);

        private String messageKey;

        private Type(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default constructor - creates an empty history item
     */
    public IdentityHistoryItem() {
    }


    /**
     * Creates a history item for the given type and certification item.
     *
     * @param identity Identity owning the history
     * @param type Type of history item to create
     * @param item CertificationItem in question
     */
    public IdentityHistoryItem(Identity identity, Type type, CertificationItem item) {
        this(identity, type, item, new CertifiableDescriptor(item));
    }


    /**
     * Creates a history item for the given item and certifiable. Useful in
     * cases where the certifiable is not the primary entitlement being certified.
     * For example -  cases where the certifier has chosen to revoke the required
     * and/or permitted roles for a given role. In that case the certifiableDesc
     * will not match the role on the certification item.
     * <p/>
     * The constructor is used to pull interesting bits out of the item and certification
     * for later searching/filtering.
     *
     * @param identity        Identity owning the history
     * @param type            Type of history item to create
     * @param item            CertificationItem in question
     * @param certifiableDesc identifying the history item
     */
    public IdentityHistoryItem(Identity identity, Type type, CertificationItem item,
                               CertifiableDescriptor certifiableDesc) {
        this.identity = identity;
        this.type = type;
        this.certifiableDescriptor = certifiableDesc;

        this.certificationType = item.getType();
        this.action = item.getAction();

        // there might not be an action yet on an open cert item
        if (item.getAction() != null) {
            this.actor = item.getAction().getActorDisplayName();

            // extra CYA for cert items that predate the actor display name
            if (this.actor == null)
                this.actor = findActorDisplayName(item.getAction().getActorName());
        }

        if (type == Type.Comment) {
            // the entry date and status of a comment shouldn't 
            // be tied to the data from the cert action
            this.entryDate = new Date();
            this.status = null;
        } else if (this.action != null) {
            this.entryDate = item.getAction().getDecisionDate();
            this.status = item.getAction().getStatus();
        }

        // history items created by continuous certs won't have 
        // a value for the created field yet
        if (this.entryDate == null)
            this.entryDate = new Date();

        if (item.getCertification() != null)
            this.certificationLink =
                    new CertificationLink(item.getCertification(), item.getParent());

        if (item.getExceptionEntitlements() != null) {
            EntitlementSnapshot snapshot = item.getExceptionEntitlements();

            // assume the most info until told otherwise?
            EntitlementGranularity granularity = EntitlementGranularity.Value;
            if (item.getCertification() == null)
                granularity = snapshot.estimateGranularity();
            else
                granularity = item.getCertification().getEntitlementGranularity();

            this.application = snapshot.getApplicationName();
            this.instance = snapshot.getInstance();
            this.account = snapshot.getDisplayName();
            this.nativeIdentity = snapshot.getNativeIdentity();

            setAttributeAndValue(granularity, snapshot);

        }

        if (item.getPolicyViolation() != null) {
            this.policy = item.getPolicyViolation().getPolicyName();
            setConstraintNameAndTruncate(item.getPolicyViolation().getConstraintName());

            // bug 26326 add the application name to the application column so we can
            // use it in searches for specific account policy violations
            if (item.getPolicyViolation().getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME) != null) {
                this.application = (String)item.getPolicyViolation().getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME);
            }
        }

        if (certifiableDesc != null && certifiableDesc.getBusinessRole() != null) {
            this.role = certifiableDesc.getBusinessRole();
        }
    }


    /**
     * Creates a history item for the given policy violation. The violation should have
     * been handled outside of a certification, otherwise the CertificationItem constructor
     * should have been used instead.
     *
     * @param type      Type of history item to create
     * @param violation Policy violation in question
     * @param action    Certification action performed on the policy violation
     *                  
     * @ignore
     * NOTE: The type arg is probably unnecessary here since comments aren't built this
     * way, but this is consistent and clear.
     */
    public IdentityHistoryItem(Type type, PolicyViolation violation,
                               CertificationAction action) {
        this.certificationType = CertificationItem.Type.PolicyViolation;
        this.identity = violation.getIdentity();
        this.type = type;
        this.action = action;
        this.status = action.getStatus();

        this.actor = action.getActorDisplayName();
        this.entryDate = action.getCreated();

        this.policy = violation.getPolicyName();
        setConstraintNameAndTruncate(violation.getConstraintName());

        // bug 26326 add the application name to the application column so we can
        // use it in searches for specific account policy violations
        if (violation.getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME) != null) {
            this.application = (String)violation.getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME);
        }

        this.certifiableDescriptor = new CertifiableDescriptor(violation);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A helper method to figure out which comment to return:
     * <p/>
     * Decision = certification action's comment
     * Comment = this comment field
     *
     * @return Comment based on the type of this history item
     */
    public String getHistoryComments() {
        if (this.type == Type.Comment)
            return this.comments;
        else
            return this.action.getComments();
    }

    public String getBusinessRole() {
        return this.certifiableDescriptor.getBusinessRole();
    }

    public EntitlementSnapshot getExceptionEntitlements() {
        return this.certifiableDescriptor.getExceptionEntitlements();
    }

    public PolicyViolation getPolicyViolation() {
        return this.certifiableDescriptor.getPolicyViolation();
    }

    /**
     * Returns true if the comment was made on the given violation.
     *
     * @param otherViolation PolicyViolation to compare
     * @return True if the given violation is the same as the violation
     *         referenced by this decision.
     *
     * @ignore
     * Prior to 3.0 violations were deleted whenever we ran a policy scan, so we
     * had to perform a comparison on the violation details rather than using the
     * ID. We need to keep this code around for a while so that we can still match
     * get an accurate comparison for older violations.
     */
    public boolean isSimiliarViolation(PolicyViolation otherViolation)
            throws GeneralException {
        if (this.certifiableDescriptor == null)
            // shouldn't ever happen in the real world, but does in testing
            return false;
        else
            return this.certifiableDescriptor.isSimiliarViolation(otherViolation);
    }

    /**
     * @ignore
     * A VERY simple toString() implementation
     */
    public String toString() {
        return type.toString() + ":" + entryDate.toString();
    }

    /**
     * Creates a Map representation of this object.
     *
     * @return A Map representation of this object.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("type", getType());
        map.put("certifiableDescriptor", getCertifiableDescriptor());
        map.put("action", getAction());
        map.put("certificationLink", getCertificationLink());
        map.put("comments", getComments());
        map.put("certificationType", getCertificationType());
        map.put("status", getStatus());
        map.put("actor", getActor());
        map.put("entryDate", getEntryDate());
        map.put("application", getApplication());
        map.put("account", getAccount());
        map.put("nativeIdentity", getNativeIdentity());
        map.put("attribute", getAttribute());
        map.put("value", getValue());
        map.put("policy", getPolicy());
        map.put("constraintName", getConstraintName());
        map.put("role", getRole());

        return map;
    }


    /**
     * @exclude
     * Try to track down the display name of the given actor's Identity.
     * This is a CYA in case the actorDisplayName upgrader gurked.
     *
     * @param actorName Name of identity to find display name
     */
    private String findActorDisplayName(String actorName) {
        String displayName = actorName;
        try {
            SailPointContext context = SailPointFactory.getCurrentContext();
            Identity identity = context.getObjectByName(Identity.class, actorName);
            if (identity != null)
                displayName = identity.getDisplayableName();
        } catch (GeneralException e) {
            if (log.isWarnEnabled())
                log.warn("Unable to find Identity: " + actorName, e);
        }

        return displayName;
    }


    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * Sets the "Attribute" and "Value" of this history item correctly
     * from the entitlement snapshot, for the given granularity. If either
     * should not be defined, they will be set to null.
     *
     * @param granularity EntitlementGranularity to determine how to get correct data
     * @param snapshot EntitlementSnapshot containing attribute and value
     */
    public void setAttributeAndValue(EntitlementGranularity granularity, EntitlementSnapshot snapshot) {
        // we've got to pull different data for entitlements vs permissions
        // and different data for the different granularities
        Map.Entry<String, Object> attributeObj = null;
        Attributes<String, Object> attributes = snapshot.getAttributes();
        if ((null != attributes) && !attributes.isEmpty())
            attributeObj = attributes.entrySet().iterator().next();

        Permission permission = null;
        List<Permission> permissions = snapshot.getPermissions();
        if ((null != permissions) && !permissions.isEmpty())
            permission = permissions.get(0);

        if (granularity == EntitlementGranularity.Application) {
            setAttributeAndTruncate(null);
            setValueAndTruncate(null);
        } else {
            if (attributeObj != null) {
                setAttributeAndTruncate(attributeObj.getKey());

                if (granularity == EntitlementGranularity.Value)
                    setValueAndTruncate(Util.otos(attributeObj.getValue()));
                else
                    setValueAndTruncate(null);
            }

            if (permission != null) {
                setAttributeAndTruncate(permission.getTarget());

                if (granularity == EntitlementGranularity.Value)
                    setValueAndTruncate(permission.getRightsList().get(0));
                else
                    setValueAndTruncate(null);
            }
        }
    }

    /**
     * Used on debug page.
     *
     * @return The columns to display in the console.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("identity.name", "Identity");
        cols.put("type", "Type");
        cols.put("status", "Status");
        cols.put("entryDate", "Date");
        return cols;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    ////////////////////////////////////////////////////////////////////////////


    public Identity getIdentity() {
        return identity;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "HistoryIdentity")
    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public Type getType() {
        return type;
    }

    @XMLProperty
    public void setType(Type type) {
        this.type = type;
    }

    public CertifiableDescriptor getCertifiableDescriptor() {
        return this.certifiableDescriptor;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public void setCertifiableDescriptor(CertifiableDescriptor desc) {
        this.certifiableDescriptor = desc;
    }

    public CertificationAction getAction() {
        return action;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public void setAction(CertificationAction action) {
        this.action = action;
    }

    public CertificationLink getCertificationLink() {
        return certificationLink;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public void setCertificationLink(CertificationLink certificationLink) {
        this.certificationLink = certificationLink;
    }

    /**
     * Caution - this returns the value of the comment field, which is used
     * by items of type "Comment" and NOT the comments attached to a certification
     * action. To return the proper comments, regardless of item type,
     * use getHistoryComment().
     */
    public String getComments() {
        return comments;
    }

    @XMLProperty
    public void setComments(String comment) {
        this.comments = comment;
    }

    public CertificationItem.Type getCertificationType() {
        return this.certificationType;
    }

    @XMLProperty
    public void setCertificationType(CertificationItem.Type cType) {
        this.certificationType = cType;
    }

    public CertificationAction.Status getStatus() {
        return this.status;
    }

    @XMLProperty
    public void setStatus(CertificationAction.Status status) {
        this.status = status;
    }

    public String getActor() {
        return this.actor;
    }

    @XMLProperty
    public void setActor(String name) {
        this.actor = name;
    }

    public Date getEntryDate() {
        return entryDate;
    }

    @XMLProperty
    public void setEntryDate(Date entryDate) {
        this.entryDate = entryDate;
    }

    /**
     * Convenience method to retrieve the entitlements from the
     * CertifiableDescriptor.
     */
    public EntitlementSnapshot getEntitlements() {
        return (null != this.certifiableDescriptor)
                ? this.certifiableDescriptor.getExceptionEntitlements() : null;
    }

    public String getApplication() {
        return application;
    }

    @XMLProperty
    public void setApplication(String application) {
        this.application = application;
    }

    @XMLProperty
    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getInstance() {
        return instance;
    }

    public String getAccount() {
        return account;
    }

    @XMLProperty
    public void setAccount(String account) {
        this.account = account;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    @XMLProperty
    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public String getAttribute() {
        return attribute;
    }

    /**
     * @exclude
     * Only used by hibernate and XML persistence  
     * @deprecated use {@link #setAttributeAndTruncate(String)}.
     */
    @Deprecated
    @XMLProperty
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public void setAttributeAndTruncate(String attribute) {
        this.attribute = Util.truncate(attribute, MAX_LENGTH_ATTR);
    }

    public String getValue() {
        return value;
    }

    /**
     * @exclude
     * Only used by hibernate and XML persistence  
     * @deprecated use {@link #setAttributeAndTruncate(String)}.
     */
    @Deprecated
    @XMLProperty
    public void setValue(String value) {
        this.value = value;
    }

    public void setValueAndTruncate(String value) {
        this.value = Util.truncate(value, MAX_LENGTH_VALUE);
    }

    public String getPolicy() {
        return policy;
    }

    @XMLProperty
    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getConstraintName() {
        return constraintName;
    }

    /**
     * @exclude
     * Only used by hibernate and XML persistence  
     * @deprecated use {@link #setConstraintNameAndTruncate(String)} 
     */
    @Deprecated
    @XMLProperty
    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public void setConstraintNameAndTruncate(String constraintName) {
        this.constraintName = Util.truncate(constraintName, MAX_LENGTH_CONSTRAINT_NAME);
    }

    public String getRole() {
        return role;
    }

    @XMLProperty
    public void setRole(String role) {
        this.role = role;
    }
}
