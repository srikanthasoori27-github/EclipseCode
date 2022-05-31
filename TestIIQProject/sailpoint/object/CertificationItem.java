/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The state of one certifiable item within an CertificationIdentity.
 * Like the Certification class, this is an "archival" class that
 * has a potentially infinite lifespan and must be careful
 * about references to other objects.  See discussion in the
 * Certification class for more.
 *
 * There are several types of certification item.  Some of these
 * are unstable and exist only in the laboratory.
 *
 * 1) Bundle (aka Business Role)
 *
 * Certification for the assignment of a business role (role, etc.)
 *
 * References the associated Bundle.  Contains a snapshot of the associated
 * raw entitlements for this particular identity for display.  We do not
 * currently intend to model the Bundle/Profile hierarchy.
 *
 * 2) Exception
 *
 * Certification on a collection of additional entitlements.  This can be
 * grouped by all entitlements on an application, all values for an attribute/
 * all rights on a target, or a single attribute value/right on a target.
 *
 * 3) Entitlement
 *
 * Certification on an individual attribute value or permission within
 * an Exception item.
 * Example: groups=a,b,c
 * Example: read,write,delete on employee_table
 *
 * 4) Attribute Value
 *
 * Certification on a particular value of a multi-valued attribute
 * within an Entitlement item.
 * Example: group=a
 *
 * 5) Permission Right
 *
 * Certification on a particular right within a permission within
 * an Entitlement item.
 * Example: read on employee_table
 *
 * 6) Policy Violation
 *
 * Certification of a single violation of a policy.  This can be an SOD policy
 * or some other generic policy.  The actions available for policy violations
 * often differ from the certification actions (ie - approve isn't allowed for
 * an SOD policy violation).
 *
 * A theoretical fully decomposed item hierarchy would look like this:
 *
 * Identity
 *   Bundle
 *   Exception
 *   PolicyViolation
 *   ...
 *
 * Initially, it is expected that certification for bundles will only
 * be done at the level of the bundle, we will not descend into the
 * entitlement groups within the bundle.  The UI may display the raw
 * entitlements but they are not certified to individually.
 *
 * The level of granularity for certifying additional entitlements
 * (EntitlementGroups) depends solely on how the Certificationer was
 * configured when generating the certification.  Any level (application,
 * attribute, or attribute value) will be certified at the Exception
 * level.
 *
 * There is some justification for subclassing here, but then the
 * interface becomes more complicated and the UI has to use instanceof
 * or downcasting methods to know what the item represents.  Think
 * more about this but blowing out subclasses feels like overkill
 * and complicates the Hibernate mapping.
 *
 * KG - I'm starting to rethink subclassing.  We're getting quite a few
 * different types of items and are seeing more switch statements scattered
 * throughout the code and in this class.  We could potentially pull the
 * switch logic from outside code into this class, and would definitely
 * clean things up a bit by removing the switch statements here.  Need to
 * play with the hibernate "table per class hierarchy" inheritance mapping.
 * This won't change our schema at all if we use "type" as the discriminator.
 */

package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Transient;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.RemediationManager;
import sailpoint.object.Certification.Phase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * The state of one item  within an Certification.
 *
 * There are several types of certification item that use
 * different properties.
 *
 */
@XMLClass
@Indexes({
    @Index(name="spt_cert_item_apps_name", column="application_name", table="spt_cert_item_applications"),
    @Index(name="spt_cert_item_att_name_ci", property="exceptionAttributeName", caseSensitive=false),
    @Index(name="certification_item_tdn_ci", property="targetDisplayName", caseSensitive=false)
})
public class CertificationItem
    extends AbstractCertificationItem
    implements Notifiable, Phaseable, Cloneable, Comparable<CertificationItem>
{
    private static final long serialVersionUID = -6138478254605039203L;
    private static final Log log = LogFactory.getLog(CertificationItem.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    // These should stay in sync with the lengths in CertificationItem.hbm.xml.
    private static final int MAX_LENGTH_EXCEPTION_APP = 128;
    private static final int MAX_LENGTH_EXCEPTION_ATTR = 255;
    private static final int MAX_LENGTH_EXCEPTION_VAL = 2048;

    /**
     * Maximum length of the violationSummary before it is truncated.  This
     * should match the hibernate mapping length.
     */
    private static final int VIOLATION_SUMMARY_MAX_LENGTH = 256;

    /**
     * Key storing the RoleAssignment for this item, in the attributes map
     */
    public static final String ATTR_ROLE_ASSIGNMENT = "IIQ_roleAssignment";

    /**
     * Key storing the RoleDetection for this item, in the attributes map
     */
    public static final String ATTR_ROLE_DETECTION = "IIQ_roleDetection";

    /**
     * Key storing the recommendation for this cert item, in the attributes map
     */
    public static final String ATTR_RECOMMENDATION = "recommendation";

    public static final String ATTR_AUTO_DECISION_GENERATED = "autoDecisionGenerated";


    //////////////////////////////////////////////////////////////////////
    //
    // Type
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Item types.
     *
     * - Bundle: Cert: Item covers an individual user's entitlements.
     *
     * - BusinessRole:  Item certifies the parent-child relationship between
     *    two business roles. So the certifier will be certifying that the relationship
     *    between this business role and its parent(the CertificationEntity) is valid
     *
     * - Profile: Certifies that the profile is a valid member of a given
     *    BusinessRole(the CertificationEntity).
     */
    @XMLClass(xmlname="CertificationItemType")
    public static enum Type implements MessageKeyHolder {

        AccountGroupMembership(MessageKeys.CERT_ITEM_TYPE_ACCOUNT_GROUP_MEMBERSHIP),
        Bundle(MessageKeys.CERT_ITEM_TYPE_BUNDLE),
        Exception(MessageKeys.CERT_ITEM_TYPE_EXCEPTION, MessageKeys.CERT_ITEM_TYPE_EXCEPTION_SHORT),
        BusinessRoleHierarchy(MessageKeys.CERT_ITEM_TYPE_BIZ_ROLE_HIER),
        BusinessRoleRequirement(MessageKeys.CERT_ITEM_TYPE_REQUIRED_ROLE),
        BusinessRolePermit(MessageKeys.CERT_ITEM_TYPE_PERMITTED_ROLE),
        BusinessRoleGrantedCapability(MessageKeys.CERT_ITEM_TYPE_GRANTED_IIQ_CAPABILITY),
        BusinessRoleGrantedScope(MessageKeys.CERT_ITEM_TYPE_GRANTED_IIQ_SCOPE),
        BusinessRoleProfile(MessageKeys.CERT_ITEM_TYPE_ENTITLEMENT_PROFILE),
        DataOwner(MessageKeys.CERT_ITEM_TYPE_DATA_OWNER),
        Account(MessageKeys.CERT_ITEM_TYPE_ACCOUNT),
        PolicyViolation(MessageKeys.CERT_ITEM_TYPE_VIOLATION);

        private String messageKey;
        private String shortMessageKey;

        private Type(String messageKey) {
            this(messageKey, null);
        }

        private Type(String messageKey, String shortMessageKey) {
            this.messageKey = messageKey;
            this.shortMessageKey = shortMessageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }

        /**
         * Return a message key with a shorter name, or the default message key if a short message
         * does not exist for this Type.
         */
        public String getShortMessageKey() {
            return (null != this.shortMessageKey) ? this.shortMessageKey : this.messageKey;
        }

    };

    /**
     * Differentiates different certifiables which share the same basic type. This is
     * being used in cases where a given item type is handled the same in all
     * but a few places.
     *
     * - AssignedRole: The role being certified was assigned rather than detected.
     */
    @XMLClass(xmlname="CertificationItemSubType")
    public static enum SubType{

        AssignedRole(MessageKeys.CERT_ITEM_SUBTYPE_ASSIGNED_ROLE),
        Entitlement(MessageKeys.CERT_ITEM_SUBTYPE_ENTITLEMENT),
        Permission(MessageKeys.CERT_ITEM_SUBTYPE_PERMISSION);

        private String messageKey;

        private SubType(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A static configuration cache used by the persistence manager
     * to make decisions about searchable extended attributes.
     */
    static private CacheReference _objectConfig;

    /**
     * Back-pointer to the CertificationIdentity that is holding this
     * CertificationItem.
     */
    CertificationEntity _parent;

    /**
     * The challenge information about this item. If non-null, a challenge has
     * been generated for this item. The workItem ID in the challenge will be
     * non-null if the work item is still active.
     */
    CertificationChallenge _challenge;

    /**
     * The date to next wake up for reminders or escalation. This is used by
     * the Notifiable interface.
     */
    Date wakeUpDate;

    /**
     * The number of reminders that have been sent for the certification item.
     * This is used by the Notifiable interface.
     */
    int remindersSent;

    /**
     * Whether or not the decision has been flushed to the history, etc...
     * which should happen immediately when a decision is made and not pending
     * review. This prevents the Certificationer from flushing continuous items
     * multiple times.
     */
    boolean needsContinuousFlush;

    /**
     * The current phase of this item. This is null for periodic certifications
     * since these store phase information on the certification itself.
     */
    Phase _phase;

    /**
     * The date at which the next Phase transition should occur for this item.
     * This is null for periodic (non-continuous) certifications. Periodic
     * certification store phase information on the certification itself. This
     * gets recalculated any time a phase is transitioned (initially when the
     * certification is started and transitions to the Active phase). A null
     * value signifies that there are no more transitions remaining.
     */
    Date _nextPhaseTransition;


    /**
     * A boolean indicating that this item needs to be refreshed.
     */
    boolean _needsRefresh;


    /**
     * Type of this certification item.
     */
    Type _type;

    /**
     * For the Bundle type, the associated bundle.
     */
    String _bundle;
    
    /**
     * If Bundle Type, and assigned, this is the assignment Id of the Role Assignment
     */
    String _bundleAssignmentId;

    /**
     * SubType of the entitlement assigned to this item. If null,
     * the entitlement is a generic instance of the given Type.
     */
    SubType _subType;

    /**
     * For the Bundle type.
     *
     * This is the collection of actual entitlements held by the Identity
     * that matched the definition of the bundle. This is optional, if set
     * it is intended for display only.
     */
    List<EntitlementSnapshot> _bundleEntitlements;

    /**
     * For the Exception type.
     *
     * The additional entitlements that are being certified. Note that
     * depending on the granularity of the certification, other items in
     * the same certification can reference the same application or
     * attribute/permission, but with different values.
     */
    EntitlementSnapshot _exceptionEntitlements;

    /**
     * For Exception type.
     *
     * These fields are used to inline the exceptional entitlement information
     * onto the certification table. These are redundant with the
     * exceptionEntitlements snapshot, but are inlined in the CertificationItem
     * table to allow for searching. The extent to which these fields are
     * populated is determined by the entitlement granularity of the
     * certification, where only application will be set for a granularity of
     * "Application", both application and entitlement will be set for a
     * granularity of "Attribute", and application, entitlement, and
     * value/permission will be set for an entitlement granularity of "Value"
     */
    private String _exceptionApplication;

    /**
     * The name of the exceptional entitlement name. Only available for
     * "Attribute" entitlement granularity or finer-grained for attribute
     * additional entitlements.
     */
    private String _exceptionAttributeName;

    /**
     * The value of the exceptional entitlement if it is an attribute. Only
     * available for "Value" entitlement granularity.
     */
    private String _exceptionAttributeValue;

    /**
     * The permission target for the exceptional entitlement. Only available
     * for "Attribute" and "Value" entitlement granularity.
     */
    private String _exceptionPermissionTarget;

    /**
     * The permission right for the exceptional entitlement. Only available for
     * "Value" granularity.
     */
    private String _exceptionPermissionRight;

    /**
     * For the PolicyViolation type. The actual violation object.
     *
     * @ignore
     * TODO: Currently this is our own private copy we will serialize
     * with the item.  PolicyViolation objects may also have a long
     * life of their own for trend analysis so we could reference them,
     * but I don't want to rely on that for certification archives.
     */
    PolicyViolation _violation;

    /**
     * For the PolicyViolation type. A possibly truncated summary of the
     * violation.  Storing this in addition to the violation so have easier
     * access to the summary instead of having to load and deserialize the
     * entire violation. This is required for sorting and for projection
     * queries.
     */
    String _violationSummary;

    private Date finishedDate;
    
    /**
     * Used with Notifiable getEscalationCount() and incrementEscalationCount()
     * Added for WorkItem, might be used with CertificationItem in the future?
     */
    //private int _escalationCount = 0;
    
    /**
     * A list of the application names for this certification item.
     */
    List<String> _applicationNames;

    /**
     * String representation of the Recommendation.RecommendedDecision value assigned to
     * this CertificationItem. Used for filtering.
     */
    String _recommendValue;
    
    /**
     * A map of generic attributes for this item. This holds both extended
     * attributes and some system attributes for non-searchable item details
     */
    Map<String,Object> _attributes;

    /**
     * List of names of Classification objects associated with either the Bundle or
     * ManagedAttribute, if exists. This will be empty for all other item types.
     */
    List<String> _classificationNames;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationItem() {
        super();
    }

    public CertificationItem(Type t) {
        this();
        _type = t;
    }

    public CertificationItem(Bundle b, List<EntitlementGroup> ents, SubType subType) {
        this(Type.Bundle);
        if (b != null) {
            _bundle = b.getName();
            _targetId = b.getId();
            _targetName = b.getName();
            _targetDisplayName = b.getDisplayableName();
            _bundleAssignmentId = b.getAssignmentId();
            
            setApplicationCollection(b.getApplications());
        } else {
            Set<Application> apps = new HashSet<Application>();
            
            if (ents != null) {
                for (EntitlementGroup eg : ents) {
                    Application app = eg.getApplication();
                    
                    if (app != null) {
                        apps.add(app);
                    }
                }
                
                setApplicationCollection(apps);
            }
        }
        
        _subType = subType;
        setBundleEntitlementGroups(ents);
    }

    /**
     * Creates a CertificationItem for a profile whose
     * assignment to a given business role is being certified.
     * Used in BusinessRoleComposition certifications.
     *
     * @param profile Profile being certified
     */
    public CertificationItem(Profile profile) {
        this(Type.BusinessRoleProfile);
        if (profile != null) {
            setTargetName(profile.getName());
            setTargetId(profile.getId());
            if (profile.getApplication() != null) {
                setApplicationNames(Arrays.asList(profile.getApplication().getName()));
            }
        }
    }

    /**
     * Creates a CertificationItem for a business role whose
     * status as a child of another business role is being certified.
     * Used in BusinessRoleComposition certifications.
     *
     * @param businessRole Business role being certified
     */
    public CertificationItem(Bundle businessRole) {
        this(Type.BusinessRoleHierarchy);
        if (businessRole != null) {
            setTargetName(businessRole.getName());
            setTargetId(businessRole.getId());
            setTargetDisplayName(businessRole.getDisplayableName());
            setBundle(businessRole);
        
            setApplicationCollection(businessRole.getApplications());
        }
    }

    public CertificationItem(CertificationItem.Type type, SailPointObject obj) {
        this(type);
        setTargetName(obj.getName());
        setTargetId(obj.getId());
        
        if (obj instanceof Bundle) {
            Bundle bundle = (Bundle)obj;
            setTargetDisplayName(bundle.getDisplayableName());
            setBundle((Bundle)obj);
        }
    }

    public CertificationItem(PolicyViolation pv)
        throws GeneralException {

        this(Type.PolicyViolation);
        setPolicyViolation(pv);
        _violationSummary = Util.truncate(pv.getDisplayableName(),
                                          VIOLATION_SUMMARY_MAX_LENGTH);
        
        List<String> appNames = pv.getRelevantApps();
        if (appNames != null ) {
            setApplicationNames(appNames);
        }
    }

    public CertificationItem(Entitlements entitlements,
                             Certification.EntitlementGranularity granularity) {
        this(entitlements.isAccountOnly() ? Type.Account : Type.Exception);
        
        _exceptionEntitlements = entitlements.convertToSnapshot();
        inlineEntitlements(_exceptionEntitlements, granularity);
        
        _applicationNames = Arrays.asList(entitlements.getApplicationName());
    }

    /**
     * New interface that assumes Value granularity and enables
     * persistence optimization.
     * jsl - had to abandon optimization for now
     */
    public CertificationItem(Entitlements entitlements) {

        this(entitlements.isAccountOnly() ? Type.Account : Type.Exception);
        
        EntitlementSnapshot ents = entitlements.convertToSnapshot();
        setExceptionEntitlements(ents);
        inlineEntitlements(ents, Certification.EntitlementGranularity.Value);

        List<String> apps = Arrays.asList(entitlements.getApplicationName());
        setApplicationNames(apps);
    }
    
    public void visit(Visitor v) throws GeneralException {
        v.visitCertificationItem(this);
    }
     
    /**
     * Copy the given entitlements onto this CertificationItem according to the
     * given entitlement granularity. See the javadoc for
     * {@link #_exceptionApplication} for more information.
     *
     * @param  es           The entitlements to inline.
     * @param  granularity  The entitlement granularity.
     */
    private void inlineEntitlements(EntitlementSnapshot es,
                                    Certification.EntitlementGranularity granularity) {

        // Inline the entitlement values onto this CertificationItem.
        Map.Entry<String,Object> attrEntry = null;
        Attributes<String,Object> attrs = es.getAttributes();
        if ((null != attrs) && !attrs.isEmpty()) {
            attrEntry = attrs.entrySet().iterator().next();
        }

        Permission perm = null;
        List<Permission> perms = es.getPermissions();
        if ((null != perms) && !perms.isEmpty()) {
            perm = perms.get(0);
        }

        String app = null;
        String attr = null;
        String val = null;
        String target = null;
        String right = null;

        app = es.getApplication();

        switch (granularity) {
        case Value:
            if (null != attrEntry) {
                attr = attrEntry.getKey();
                val = Util.otos(attrEntry.getValue());
            }
            else if (null != perm) {
                List<String> rights = perm.getRightsList();
                assert (1 == rights.size()) :
                    "Should have only one right for 'value' entitlement granularity.";
                target = perm.getTarget();
                right = rights.get(0);
            }
            break;

        case Attribute:
            if (null != attrEntry) {
                attr = attrEntry.getKey();
            }
            else if (null != perm) {
                target = perm.getTarget();
            }
            break;

        case Application:
            break;
        default:
            throw new RuntimeException("Unknown granularity: " + granularity);
        }

        setExceptionApplicationAndTruncate(app);
        setExceptionAttributeNameAndTruncate(attr);
        setExceptionAttributeValueAndTruncate(val);
        _exceptionPermissionTarget = target;
        _exceptionPermissionRight = Util.truncate(right, MAX_LENGTH_EXCEPTION_ATTR);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public List<String> getApplicationNames() {
        return _applicationNames;
    }

    @XMLProperty
    public void setApplicationNames(List<String> applicationNames) {
        _applicationNames = applicationNames;
    }
    
    public void setApplicationCollection(Collection<Application> applications) {
        List<String> names = new ArrayList<String>();
        
        if (applications != null) {
            for (Application app : applications) {
                names.add(app.getName());
            }
        }
        
        setApplicationNames(names);
    }
    
    public void setParent(CertificationEntity entity) {
        _parent = entity;
    }

    /**
     * Back-pointer to the CertificationIdentity that is holding this
     * CertificationItem.
     */
    public CertificationEntity getParent() {
        return _parent;
    }

    /**
     * The date to next wake up for reminders or escalation. This is used by
     * the Notifiable interface.
     */
    @XMLProperty
    public Date getWakeUpDate() {
        return this.wakeUpDate;
    }

    public void setWakeUpDate(Date nextWakeUp) {
        this.wakeUpDate = nextWakeUp;
    }

    /**
     * The number of reminders that have been sent for the certification item.
     * This is used by the Notifiable interface.
     */
    @XMLProperty
    public int getRemindersSent() {
        return this.remindersSent;
    }

    public void setRemindersSent(int sent) {
        this.remindersSent = sent;
    }

    /**
     * True when the decision has been flushed to the history
     * immediately when a decision is made and not pending
     * review. This prevents the Certificationer from flushing continuous items
     * multiple times.
     */
    @XMLProperty
    public boolean isNeedsContinuousFlush() {
        return this.needsContinuousFlush;
    }

    public void setNeedsContinuousFlush(boolean flushed) {
        this.needsContinuousFlush = flushed;
    }

    /**
     * Mark this item as being ready to have the remediation kicked off.
     */
    public void setReadyForRemediation(boolean ready) {
        if (null == _action) {
            throw new IllegalStateException("Can only mark remediations that have actions.");
        }
        _action.setReadyForRemediation(ready);
    }

    /**
     * The current phase of this item. This is null for periodic certifications
     * since these store phase information on the certification itself.
     */
    @XMLProperty
    public Phase getPhase() {
        return _phase;
    }

    public void setPhase(Phase phase) {
        _phase = phase;
    }

    /**
     * The date at which the next Phase transition should occur for this item.
     * This is null for periodic (non-continuous) certifications. Periodic
     * certification store phase information on the certification itself. This
     * gets recalculated any time a phase is transitioned (initially when the
     * certification is started and transitions to the Active phase). A null
     * value signifies that there are no more transitions remaining.
     */
    @XMLProperty
    public Date getNextPhaseTransition() {
        return _nextPhaseTransition;
    }

    public void setNextPhaseTransition(Date next) {
        _nextPhaseTransition = next;
    }

    @XMLProperty
    public void setType(Type type) {
        _type = type;
    }

    /**
     * Type of this certification item.
     */
    public Type getType() {
        return _type;
    }

    @XMLProperty
    public void setBundle(String s) {
        _bundle = s;
    }

    public void setBundle(Bundle b) {
        if (b != null)
            _bundle = b.getName();
        else
            _bundle = null;
    }

    /**
     * For the Bundle type, the id of the associated bundle (role).
     */
    public String getBundle() {
        return _bundle;
    }

    public Bundle getBundle(Resolver r) throws GeneralException {
        Bundle b = null;
        if (r != null && _bundle != null)
          b = r.getObjectByName(Bundle.class, _bundle);

        if (b==null && getTargetId()!=null)
            b = r.getObjectById(Bundle.class, getTargetId());

        return b;
    }
    
    public void setBundleAssignmentId(String id) {
        this._bundleAssignmentId = id;
    }
    
    /**
     * Get the assignment ID if the bundle came from a RoleAssignment
     * @return Assignment ID
     */
    @XMLProperty
    public String getBundleAssignmentId() {
        return this._bundleAssignmentId;
    }

    /**
     * Return a snapshot for this item if it exists.
     * @return A snapshot for this item if it exists.
     */
    public Snapshot getSnapshot(){
        Snapshot snap = null;
        if (getParent() != null){
            switch(getType()){
                case BusinessRoleHierarchy:
                case BusinessRolePermit:
                case BusinessRoleRequirement:
                    snap = getParent().getRoleSnapshot() != null ?
                            getParent().getRoleSnapshot().getRelatedRoleSnapshot(getTargetId()) : null;
                    break;
                case BusinessRoleProfile:
                    snap = getParent().getRoleSnapshot() != null ?
                        getParent().getRoleSnapshot().getProfileSnapshot(getTargetId()) : null;
                    break;
                case BusinessRoleGrantedCapability:
                    snap = getParent().getRoleSnapshot() != null ?
                        getParent().getRoleSnapshot().getGrantedCapability(getTargetId()) : null;
                    break;
                case BusinessRoleGrantedScope:
                    snap =  getParent().getRoleSnapshot() != null ?
                            getParent().getRoleSnapshot().getGrantedScope(getTargetId()) : null;
                    break;
            }
        }

        return snap;
    }

    /**
     * SubType of the entitlement assigned to this item. If null,
     * the entitlement is a generic instance of the given Type.
     */
    @XMLProperty
    public SubType getSubType() {
        return _subType;
    }

    public void setSubType(SubType subType) {
        this._subType = subType;
    }

    @XMLProperty(mode=SerializationMode.LIST,xmlname="BundleEntitlements")
    public void setBundleEntitlements(List<EntitlementSnapshot> ents) {
        _bundleEntitlements = ents;
    }

    /**
     * For the Bundle type.
     *
     * This is the collection of actual entitlements held by the Identity
     * that matched the definition of the role. This is optional, if set
     * it is intended for display only.
     */
    public List<EntitlementSnapshot> getBundleEntitlements() {
        return _bundleEntitlements;
    }
    
    @XMLProperty
    public void setExceptionEntitlements(EntitlementSnapshot ents) {
        _exceptionEntitlements = ents;
    }

    /**
     * For the Exception type.
     *
     * The additional entitlements that are being certified. Note that
     * depending on the granularity of the certification, other items in
     * the same certification can reference the same application or
     * attribute/permission, but with different values.
     */
    public EntitlementSnapshot getExceptionEntitlements() {
        return _exceptionEntitlements;
    }
    
    public Message getExceptionEntitlementsDescription(){
        return EntitlementDescriber.summarize(getExceptionEntitlements());
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setPolicyViolation(PolicyViolation v) {
        _violation = v;
    }

    /**
     * For the PolicyViolation type. The actual violation object.
     *
     * this is a private copy that is serialize with the item.
     */
    public PolicyViolation getPolicyViolation() {
        return _violation;
    }

    /**
     * @exclude Only to be used by persistence frameworks.
     * @deprecated set using constructor {@link #CertificationItem(PolicyViolation)}
     */
    @Deprecated
    @XMLProperty
    public void setViolationSummary(String s) {
        _violationSummary = s;
    }

    /**
     * For the PolicyViolation type. A possibly truncated summary of the
     * violation. Storing this in addition to the violation so there is easier
     * access to the summary instead of having to load and deserialize the
     * entire violation. This is required for sorting and for projection
     * queries.
     */
    public String getViolationSummary() {
        return _violationSummary;
    }

    private void setExceptionApplicationAndTruncate(String app) {
        setExceptionApplication(Util.truncate(app, MAX_LENGTH_EXCEPTION_APP));
    }

    private void setExceptionAttributeNameAndTruncate(String attr) {
        setExceptionAttributeName(Util.truncate(attr, MAX_LENGTH_EXCEPTION_ATTR));
    }

    private void setExceptionAttributeValueAndTruncate(String val) {
        setExceptionAttributeValue(Util.truncate(val, MAX_LENGTH_EXCEPTION_VAL));
    }

    /**
     * @deprecated Instead use {@link #getExceptionEntitlements()}. Only used
     *             for searching.
     */
    @Deprecated
    @XMLProperty
    public String getExceptionApplication() {
        return _exceptionApplication;
    }

    /**
     * @deprecated Instead use {@link #setExceptionEntitlements(EntitlementSnapshot)}. Only used
     *             for searching.
     */
    @Deprecated
    public void setExceptionApplication(String exceptionApplication) {
        this._exceptionApplication = exceptionApplication;
    }

    /**
     * @deprecated Instead use {@link #getExceptionEntitlements()}. Only used
     *             for searching.
     */
    @Deprecated
    @XMLProperty
    public String getExceptionAttributeName() {
        return _exceptionAttributeName;
    }

    /**
     * @deprecated Instead use {@link #setExceptionEntitlements(EntitlementSnapshot)}. Only used
     *             for searching.
     */
    @Deprecated
    public void setExceptionAttributeName(String exceptionEntitlement) {
        this._exceptionAttributeName = exceptionEntitlement;
    }

    /**
     * @deprecated Instead use {@link #getExceptionEntitlements()}. Only used
     *             for searching.
     */
    @Deprecated
    @XMLProperty
    public String getExceptionPermissionTarget() {
        return _exceptionPermissionTarget;
    }

    /**
     * @deprecated Instead use {@link #setExceptionEntitlements(EntitlementSnapshot)}. Only used
     *             for searching.
     */
    @Deprecated
    public void setExceptionPermissionTarget(String target) {
        this._exceptionPermissionTarget = target;
    }

    /**
     * @deprecated Instead use {@link #getExceptionEntitlements()}. Only used
     *             for searching.
     */
    @Deprecated
    @XMLProperty
    public String getExceptionPermissionRight() {
        return _exceptionPermissionRight;
    }

    /**
     * @deprecated Instead use {@link #setExceptionEntitlements(EntitlementSnapshot)}. Only used
     *             for searching.
     */
    @Deprecated
    public void setExceptionPermissionRight(String right) {
        this._exceptionPermissionRight = right;
    }

    /**
     * @deprecated Instead use {@link #getExceptionEntitlements()}. Only used
     *             for searching.
     */
    @Deprecated
    @XMLProperty
    public String getExceptionAttributeValue() {
        return _exceptionAttributeValue;
    }

    /**
     * @deprecated Instead use {@link #setExceptionEntitlements(EntitlementSnapshot)}. Only used
     *             for searching.
     */
    @Deprecated
    public void setExceptionAttributeValue(String exceptionValue) {
        this._exceptionAttributeValue = exceptionValue;
    }


    /**
     * The challenge information about this item. If non-null, a challenge has
     * been generated for this item. The workItem ID in the challenge will be
     * non-null if the work item is still active.
     */
    public CertificationChallenge getChallenge() {
        return _challenge;
    }

    /**
     * @deprecated Instead use {@link #challengeGenerated(WorkItem, Identity)} or
     *             {@link #challengeExpired()}.
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setChallenge(CertificationChallenge challenge) {
        _challenge = challenge;
    }

    /**
     * Return a map containing generic attributes for this item. This holds the
     * extended attributes, which currently are the same as the extended link
     * attributes. In addition, this holds some system attributes with additional details
     * of the certification item.
     * For any "custom" attributes that could affect certification processing, use getCustomMap() instead.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Map<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Map<String,Object> attributes) {
        _attributes = attributes;
    }

    /**
     * Set an attribute value on this item. 
     */
    public void setAttribute(String attr, Object value) {
        if (_attributes == null) {
            _attributes = new Attributes<String,Object>();
        }

        if (value == null) {
            _attributes.remove(attr);
        }
        else {
            _attributes.put(attr, value);
        }
        
        // Keep 'er clean!
        if (_attributes.isEmpty()) {
            _attributes = null;
        }
    }

    /**
     * Helper to get an attribute value.
     */
    public Object getAttribute(String attr) {
        if (_attributes != null && _attributes.containsKey(attr)) {
            return _attributes.get(attr);
        }

        return null;
    }
    
    /**
     * Return the extended attributes for this certification item.
     */
    @Override
    public Map<String,Object> getExtendedAttributes() {
        return _attributes;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    // Transient boolean that gets set if we detect that we need to lazily
    // upgrade the extended attributes.
    boolean needsExtendedAttributeUpgrade = false;

    /*
     * This method provides a lazy upgrade for CertificationItems that only have
     * their extended attribute values stored in the extendedXXX fields and not
     * yet in the attributes map.  This method gets called by every
     * setExtendedXXX(String) method, which hibernate calls when hydrating the
     * CertificationItem.  When this is called we check to see if the attributes
     * map is null but the value is not null.  This indicates that this
     * CertificationItem has not yet been upgraded.  If not, then we set the
     * value into the attributes map to upgrade it.
     * 
     * Note that this assumes that the "attributes" field has already been
     * hydrated before the extendedXXX fields are hydrated.  To ensure this,
     * the "attributes" property is listed before the extended properties in
     * the hbm file.  Hibernate hydrates properties in the order in which they
     * are declared in the hbm file.
     */
    @Override
    public void setExtended(int index, String value) {
        if (log.isDebugEnabled()) {
            log.debug("Calling setExtended() on CertificationItem for " + getId() +
                      " - attributes is" + ((null != _attributes) ? " not" : "") + " null");
        }
        
        // Need to lazy upgrade if the attributes is null and there is a
        // non-null value.  Also, check the "needsExtendedAttributeUpgrade"
        // flag since the attributes map will be non-null after the first
        // extended attribute is upgraded.
        boolean lazyUpgrade =
            needsExtendedAttributeUpgrade ||
            ((null == _attributes) && (null != value));
    
        if (lazyUpgrade) {
            if (log.isDebugEnabled()) {
                log.debug("Need a lazy upgrade for attribute " + index);
            }

            // Make sure this is set to true so that other extended attributes
            // will be upgraded for this item.
            needsExtendedAttributeUpgrade = true;
    
            // Look up attribute name from ObjectConfig.  If it doesn't exist in
            // the ObjectConfig, then it is likely an extended attribute that is
            // mapped in the hbm file but not set up as an extended attribute.
            ObjectConfig config = ObjectConfig.getObjectConfig(CertificationItem.class);
            if (config != null) {
                ObjectAttribute att = config.getObjectAttribute(index);
                if (att != null) {
                    String attrName = att.getName();

                    // Set the attribute in the map.
                    setAttribute(attrName, value);
                }
            }
        }
    }

    /**
     * @return the possibly-null RoleAssignment for the item. Applies only to role items.
     */
    public RoleAssignment getRoleAssignment() {
        return (RoleAssignment)getAttribute(ATTR_ROLE_ASSIGNMENT);
    }

    /**
     * Set the role assignment in the attributes map
     * @param roleAssignment RoleAssignment object
     */
    public void setRoleAssignment(RoleAssignment roleAssignment) {
        setAttribute(ATTR_ROLE_ASSIGNMENT, roleAssignment);
    }

    /**
     * @return the possibly-null RoleDetection for the item. Applies only to role items.
     */
    public RoleDetection getRoleDetection() {
        return (RoleDetection)getAttribute(ATTR_ROLE_DETECTION);
    }

    /**
     * Set the role detection in the attributes map
     * @param roleDetection RoleDetection object
     */
    public void setRoleDetection(RoleDetection roleDetection) {
        setAttribute(ATTR_ROLE_DETECTION, roleDetection);
    }

    /**
     *
     * @return Retrieves the string representation of the recommendation retrieved for this CertificationItem.
     */
    @XMLProperty
    public String getRecommendValue() { return this._recommendValue; }

    /**
     * Sets the string representation of the recommendation retrieved for this CertificationItem.
     *
     * @param recommendValue String value from Recommendation.RecommendedDecision.
     */
    public void setRecommendValue(String recommendValue) { this._recommendValue = recommendValue; }

    /**
     * @return List of names of Classification objects associated with the role or managed attributes
     */
    public List<String> getClassificationNames() {
        return _classificationNames;
    }

    /**
     * Set the names of the Classification objects associated with the item
     * @param classificationNames Names of classification objects
     */
    @XMLProperty
    public void setClassificationNames(List<String> classificationNames) {
        _classificationNames = classificationNames;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     * @return the (possibly null) most recently-calculated Recommendation for this certification item
     */
    public Recommendation getRecommendation() { return (Recommendation)getAttribute(ATTR_RECOMMENDATION); }

    /**
     * Set the recommendation in the attributes map
     * @param recommendation the recommendation to set
     */
    public void setRecommendation(Recommendation recommendation) { setPseudo(ATTR_RECOMMENDATION, recommendation);  }

    /**
     * @return true if a decision was automatically generated for this item, otherwise false.
     */
    public boolean isAutoDecisionGenerated() {
        Object autoDecisionFlag = getAttribute(ATTR_AUTO_DECISION_GENERATED);
        return autoDecisionFlag != null && (boolean)autoDecisionFlag;
    }

    /**
     * Set the auto decision flag on the item.
     *
     * @param autoDecision The boolean to set the auto decision flag to.
     */
    public void setAutoDecisionGenerated(boolean autoDecision) { setPseudo(ATTR_AUTO_DECISION_GENERATED, autoDecision); }

    /**
     * Returns true if the decision history of this object is tracked. This includes any
     * object which deals with an Identity, or any item whose parent type is marked
     * historical.
     */
    public boolean isHistorical(){
        return (getIdentity() != null || getParent().getType().isHistorical());
    }

    /**
     * Clear the ID off of this item and any associated objects.
     */
    @Override
    public void clearIds() {
        super.clearIds();

        if (null != _challenge) {
            _challenge.setId(null);
        }

        // jsl - once optimization is active for all certs, don't
        // need to do this 
        EntitlementSnapshot ents = getExceptionEntitlements();
        if (ents != null) {
            ents.setId(null);
        }
    }

    /**
     * Merge any secondary information (for example - extended attributes) from the given
     * certification item into this item.
     *
     * @param  item  The item to merge into this item.
     */
    public void merge(CertificationItem item) {
        if (null != item) {
            // transfer bundle assignment id over if it is null on this
            // item but exists on item to merge
            if (!Util.isNullOrEmpty(item.getBundleAssignmentId()) && Util.isNullOrEmpty(getBundleAssignmentId())) {
                setBundleAssignmentId(item.getBundleAssignmentId());
            }

            if (null != item.getAttributes()) {
                if (null == _attributes) {
                    _attributes = new Attributes<String,Object>();
                }

                _attributes.putAll(item.getAttributes());
            }
        }
    }

    /**
     * Returns all the applications that are associated with the bundles, exception or
     * policy violation for this certification item.
     *
     * @param r Resolver, used to retrieve app objects
     * @return Non-null list of application
     * @throws GeneralException
     */
    public Set<Application> getApplications(Resolver r) throws GeneralException{

        Set<Application> apps = new HashSet<Application>();

        switch(getType()){
            case AccountGroupMembership: case Exception:  case Account: case DataOwner:
                if (getExceptionApplication() != null)
                    addFilterNull(apps, Application.class, getExceptionApplication(), r);
                break;
            case PolicyViolation:
                if (getPolicyViolation() != null){
                    // Try to get list off of violation if available.
                    List<Application> relevantApps =
                        getPolicyViolation().getRelevantApps(r);
                    if (null != relevantApps) {
                        apps.addAll(relevantApps);
                    }
                    else {
                        // Legacy - we used to not store relevant apps.  Do a
                        // bit of digging for if this a role SoD violation to
                        // get the apps out of the violation.
                        for(Bundle bundle : getPolicyViolation().getRightBundles(r)){
                            if (bundle.getApplications() != null)
                                apps.addAll(bundle.getApplications());
                        }
                        for(Bundle bundle : getPolicyViolation().getLeftBundles(r)){
                            if (bundle.getApplications() != null)
                                apps.addAll(bundle.getApplications());
                        }
                    }
                }
                break;
            case Bundle:
                if(CertificationItem.SubType.AssignedRole.equals(_subType)){
                    for(String roleName : getRolesToRemediate()){
                        Bundle role = r.getObjectByName(Bundle.class, roleName);
                        if (role != null)
                            apps.addAll(role.getApplications());
                    }
                } else if (getBundleEntitlements() != null){
                    for(EntitlementSnapshot snapshot : getBundleEntitlements())
                        if (snapshot.getApplicationName() != null){
                            addFilterNull(apps, Application.class, snapshot.getApplicationName(), r);
                        }
                }
                break;
            default:
                // Throw an exception?
        }

        return apps;
    }

    /**
     * Attempt to load the object of the given class with the given name and add
     * it to the collection. If the object cannot be loaded, this does not add
     * it to the list but logs a warning.
     *
     * @param  collection  The collection to which to add the item.
     * @param  clazz       The Class of the item to load.
     * @param  name        The name of the item to load.
     * @param  r           The Resolver to use to load the item.
     */
    private static <T extends SailPointObject> void addFilterNull(Collection<T> collection,
                                                                  Class<T> clazz, String name,
                                                                  Resolver r)
        throws GeneralException {

        T item = r.getObjectByName(clazz, name);
        if (null == item) {
            if (log.isInfoEnabled())
                log.info("Could not load item " + clazz.getName() + ":" + name +
                         " - not adding to collection.");
        }
        else {
            collection.add(item);
        }
    }

    public List<Application> getApplications(CertificationAction action,
                                             boolean isBulkCertified,
                                             Resolver resolver)
        throws GeneralException {

        // Return the apps unless this is a bulk remediated policy violation.
        if (!(isBulkCertified &&
              CertificationAction.Status.Remediated.equals(action.getStatus()) &&
              CertificationItem.Type.PolicyViolation.equals(getType()))) {
            return new ArrayList<Application>(getApplications(resolver));
        }

        // This is a bulk remediated violation, return an empty list.
        return new ArrayList<Application>();
    }

    /**
     * Helper method that returns either the list of bundle entitlements or a
     * list with the exception entitlements.
     *
     * @return  A list with the bundle or exception entitlements, or null if
     *          this item is not for a bundle or additional entitlement.
     */
    public List<EntitlementSnapshot> getEntitlements() {

        List<EntitlementSnapshot> ents = getBundleEntitlements();
        if (ents != null && !ents.isEmpty()) {
            return ents;
        }

        EntitlementSnapshot exents = getExceptionEntitlements();
        if (exents != null) {
            List<EntitlementSnapshot> list = new ArrayList<EntitlementSnapshot>();
            list.add(exents);
            return list;
        }

        return null;
    }

    /**
     * Get a localized short description of this item.
     */
    public String getShortDescription() {

        Message desc = null;

        switch (_type) {
            case Bundle:
                desc = new Message(MessageKeys.CERT_ITEM_DESC_BUNDLE, _targetDisplayName);
                break;
            case Account:
                desc = new Message(MessageKeys.CERT_ITEM_DESC_ACCOUNT, getExceptionEntitlements().getApplication());
                break;
            case AccountGroupMembership: case Exception: case DataOwner:
                EntitlementSnapshot es = this.getExceptionEntitlements();
                desc = new Message(MessageKeys.CERT_ITEM_DESC_EXCEPTION, es.getApplication());
                break;
            case PolicyViolation:
                desc = new Message(MessageKeys.CERT_ITEM_DESC_VIOLATION, _violation.getDescription());
                break;
            case BusinessRoleHierarchy:
                desc = new Message(MessageKeys.CERT_ITEM_DESC_HIERARCHY, getTargetName());
                break;
            case BusinessRoleProfile:
                desc = new Message(MessageKeys.CERT_ITEM_DESC_PROFILE, getTargetName());
                break;
            default:
                throw new RuntimeException("Unknown certification item type:" +
                        (_type != null ? _type.toString() : "NULL"));
        }

        return desc.getLocalizedMessage();
    }

    public String getErrorDescription() {

        switch (_type) {
            case Bundle:
            case AccountGroupMembership: case Exception: case Account:
            case BusinessRoleHierarchy:
            case BusinessRoleProfile:
            case DataOwner:
                return getShortDescription();
            case PolicyViolation:
                return
                    String.format("%s : %s",
                            new Message(MessageKeys.POLICY_VIOLATION).getLocalizedMessage(),
                            getPolicyViolationDesc());
            default:
                throw new RuntimeException("Unknown certification item type:" +
                        (_type != null ? _type.toString() : "NULL"));
        }
    }

    private String getPolicyViolationDesc() {

        if (Util.isNullOrEmpty(_violation.getPolicyName())) {
            return trimBrs(_violation.getDescription());
        }

        if (Util.isNullOrEmpty(_violation.getConstraintName())) {
            return _violation.getPolicyName();
        } else {
            return _violation.getPolicyName() + " : " + _violation.getConstraintName();
        }
    }

    private static String trimBrs(String val) {

        if (val == null) {
            return null;
        }

        return val.replaceAll("<br/>", "");
    }

    /**
     * Returns the name of the Identity that this item is certifying.
     *
     * @return The name of the identity this item is certifying.
     */
    @Override
    public String getIdentity() {

        if (CertificationItem.Type.BusinessRoleHierarchy.equals(getType()) ||
                CertificationItem.Type.BusinessRoleProfile.equals(getType())) {
            return null;
        } else if (getCertification() != null &&
                (Certification.Type.AccountGroupMembership.equals(getCertification().getType()) || Certification.Type.DataOwner.equals(getCertification().getType()))){
            return _targetName;
        } else if (getCertification() != null &&
                Certification.Type.AccountGroupPermissions.equals(getCertification().getType())){
            return (null != _parent) ? _parent.getAccountGroup() : null;
        } else {
            return (null != _parent) ? _parent.getIdentity() : null;
        }
    }

    public Identity getIdentity(Resolver r) throws GeneralException {

        if (r==null)
            throw new IllegalArgumentException("Resolver may not be null.");

        if (getIdentity() ==null)
            return null;
        else
            return r.getObjectByName(Identity.class, getIdentity());

    }

    public SailPointObject getTargetObject(Resolver r) throws GeneralException{
        if (getTargetId() == null && getTargetName() == null)
            return null;

        String identifier = getTargetId() != null ? getTargetId() : getTargetName();
        boolean useId = false;
        if (getTargetId() != null) {
            useId = true;
        }
        switch(getType()){
            case BusinessRolePermit:
            case BusinessRoleRequirement:
            case BusinessRoleHierarchy:
                return useId ? r.getObjectById(Bundle.class, identifier) : r.getObjectByName(Bundle.class, identifier);
            case BusinessRoleGrantedCapability:
                return useId ? r.getObjectById(Capability.class, identifier) : r.getObjectByName(Capability.class, identifier);
            case BusinessRoleGrantedScope:
                return useId ? r.getObjectById(Scope.class, identifier) : r.getObjectByName(Scope.class, identifier);
            case BusinessRoleProfile:
                return useId ? r.getObjectById(Profile.class, identifier) : r.getObjectByName(Profile.class, identifier);
            case Bundle:
                return r.getObjectByName(Bundle.class, _bundle);
            case PolicyViolation:
                return getPolicyViolation();
        }

        return null;
    }



    /**
     * Return the name of the AccountGroup that this item is certifying.
     *
     * @return Name of the account group
     */
    public String getAccountGroup(){
         return (null != _parent) ? _parent.getAccountGroup() : null;
    }

    @Override
    public CertificationEntity getCertificationEntity() {
        return _parent;
    }

    /**
     * Return the certification that owns this item.
     */
    public Certification getCertification() {
        if (null != _parent)
            return _parent.getCertification();
        return null;
    }

    //
    // EntitlementGroup Converters
    // Until we can decide whether or not to merge these,
    // or give EntitlementCorrelator an interface to produce
    // them, need to convert EntitlementGroups into snapshots
    //

    // Oddment: can't name this setEntitlements or we get a
    // "have the same erasure" error.
    private void setBundleEntitlementGroups(List<EntitlementGroup> groups) {
        if (groups == null)
            setBundleEntitlements(null);
        else {
            List<EntitlementSnapshot> ents = new ArrayList<EntitlementSnapshot>();
            for (EntitlementGroup g : groups)
                ents.add(g.convertToSnapshot());
            setBundleEntitlements(ents);
        }
    }

    /**
     * Return true if this item has been delegated.
     */
    public boolean isDelegated() {

        boolean delegated = false;

        if (_delegation != null && _delegation.isActive() && !_delegation.isRevoked())
            delegated = true;

        return delegated;
    }

    /**
     * Return true if this item has been returned.
     */
    public boolean isReturned() {

        boolean returned = false;

        if (_delegation != null && _delegation.isReturned())
            returned = true;

        return returned;
    }

    /**
     * Return true if this item has been marked as requiring delegation review.
     */
    public boolean isReviewRequired() {

        boolean review = false;

        if (_delegation != null)
            review = _delegation.isReviewRequired();

        return review;
    }

    /**
     * Return true if this item has been marked as reviewed.
     */
    public boolean isReviewed() {

        boolean reviewed = false;

        if (isActedUpon())
            reviewed = _action.isReviewed();

        return reviewed;
    }

    /**
     * Return whether this item (or its parent) is currently delegated or was
     * previously delegated and is waiting delegation review.
     */
    public boolean isDelegatedOrWaitingReview() {

        return isDelegated() || (_parent != null && _parent.isEntityDelegated()) || requiresReview();
    }

    /**
     * Return whether this item currently requires its delegation decision to
     * be reviewed. This is true of either the item or identity delegation
     * required review, and there is an action that has not been reviewed.
     *
     * @return True if this item currently requires its delegation decision to
     *         be reviewed, false otherwise.
     */
    public boolean requiresReview() {

        CertificationDelegation parentDel =
            (null != _parent) ? _parent.getDelegation() : null;
        return CertificationItem.requiresReview(_action, _delegation, parentDel);
    }

    /**
     * See {@link #requiresReview()} for more information. This is a static
     * version of the same method so it can be calculated whether review is required
     * for a non-full item (for example - components of an item from a projection query).
     */
    public static boolean requiresReview(CertificationAction action,
                                         CertificationDelegation itemDel,
                                         CertificationDelegation entityDel) {

        // Item will not require review if there was no decision or if has
        // already been reviewed.
        if ((null == action) || (null == action.getStatus()) || action.isReviewed()) {
            return false;
        }

        // If this decision came from somewhere else, we don't need to review it, the parent review will be good enough.
        if (null != action.getSourceAction()) {
            return false;
        }

        // First, check if the item has a delegation that requires review.
        if (requiresReview(itemDel)) {
            return true;
        }

        // Next, check if the identity has a delegation that requires review.
        if (requiresReview(entityDel)) {
            return true;
        }

        // If we got here, neither the item or identity required review for
        // this item decision, so return false.
        return false;
    }

    private static boolean requiresReview(CertificationDelegation del) {

        return (null != del) && del.isReviewRequired() && !del.isActive() &&
               !del.isReturned();
    }

    /**
     * Has an action been chosen for this item.
     *
     * @return True if an action has been chosen for this item, false otherwise.
     */
    public boolean isActedUpon() {
        return ((null != _action) && (null != _action.getStatus()));
    }

    /**
     * Has an action been cleared for this item.
     *
     * @return True if an action has been cleared for this item, false otherwise.
     */
    public boolean isCleared() {
        return ((isActedUpon()) && (_action.getStatus() == CertificationAction.Status.Cleared));
    }

    /**
     * This sets the continuous state to the given value, rolls the state up
     * onto the parent entity, and updates the state on the certification. Use
     * this instead of setContinuousState() since this updates other interesting
     * pieces of the model.
     *
     * @param  state  The state that we want to transition to.
     * @param  cert   The Certification that this item is a part of. This is
     *                required if the item is not yet attached to a certification.
     *
     */
    @Deprecated
    public void assignContinuousState(ContinuousState state,
                                      Certification cert) {
        if (log.isDebugEnabled()) {
            log.debug("Continuous certification support has been removed");
        }
    }

    /**
     * Refresh the overall status of this item.
     *
     * @param  completeOverride  Allow overriding the default semantics of item
     *                           completion. This can only be used to restrict
     *                           completion of an item unless it is otherwise deemed
     *                           completed.
     */
    public void refreshSummaryStatus(Boolean completeOverride)
    {
        Status status = Status.Open;

        // If the owning entity is delegated, we'll call this delegated.
        if ((null != _parent) && _parent.isEntityDelegated()) {
            status = Status.Delegated;
        }
        // Check parent entity to see if there is a delegation item and it is returned or the parent summary status is returned
        // and there is no action.
        // Sometimes we remove the parent delegation and then all the child items don't get caught here so the summary status
        // goes back to open for all the other returned items.
        // If there is an action, item status shouldn't be in Returned.
        else if (null != _parent && !isActedUpon() &&
                ((null != _parent.getDelegation() && _parent.getDelegation().isReturned()) ||
                        (_parent.getSummaryStatus() == Status.Returned))) {
            // If the owning entity's delegation was returned, mark this item as
            // returned.  This gets cleared when a decision is made.
            status = Status.Returned;
        }
        else if (this.isReturned()) {
            status = Status.Returned;
        }
        else if (this.isDelegated()) {
            status = Status.Delegated;
        }
        else if (this.requiresReview()) {
            status = Status.WaitingReview;
        }
        else if (this.isChallengeGenerated() && _challenge.isChallenged() &&
                 !_challenge.hasBeenDecidedUpon() &&
                 !_challenge.isChallengeDecisionExpired()) {
            // Challenged, but not acted upon and not expired.
            status = Status.Challenged;
        }
        else if (isActedUpon() &&
                 ((null == completeOverride) || completeOverride.booleanValue())) {
            // clearing a previous decision counts as being acted upon
            // since there's still a cert action associated with the cert item
            if (isCleared())
                status = Status.Open;
            else
                status = Status.Complete;
        }

        // If we've completed the item, save the completion date.
        // Careful here b/c it will otherwise try to save a completion date for 
        // items that are being cleared.
        if (status.isComplete() != isComplete()) {
            if (!isCleared()) {
                Date completed = (status.isComplete()) ? new Date() : null;
                setCompleted(completed);
            } else {
                // make sure the completed date for cleared items is null
                setCompleted(null);
            }
        }

        setSummaryStatus(status);
    }

    /**
     * Refresh whether this item has action required.
     */
    public void refreshActionRequired() {

        // Look to see if this item has a challenge that needs a decision.
        _actionRequired =
            ((null != _challenge) && _challenge.requiresDecision());

        // If no action required yet, look to see if a review is required.
        if (!_actionRequired) {
            _actionRequired = this.requiresReview();
        }
    }

    /**
     * Refresh whether this item has differences.
     *
     * @param  diff      The non-null IdentityDifference.
     * @param  resolver  The Resolver to use.
     *
     * @return True if this item has differences.
     */
    public boolean refreshHasDifferences(IdentityDifference diff, Resolver resolver)
        throws GeneralException {

        boolean hasDifferences = false;

        switch (_type) {
        case Bundle:
            Difference bundleDiffs = diff.getBundleDifferences();
            if (null != bundleDiffs) {
                List<String> newBundles = bundleDiffs.getAddedValues();
                hasDifferences = ((null != newBundles) && newBundles.contains(_bundle));
            }
            if (!hasDifferences) {
                Difference assignedRoleDiffs = diff.getAssignedRoleDifferences();
                if (null != assignedRoleDiffs) {
                    List<String> newBundles = assignedRoleDiffs.getAddedValues();
                    hasDifferences = ((null != newBundles) && newBundles.contains(_bundle)); 
                }
            }
            break;

        case AccountGroupMembership: case Exception: case DataOwner: case Account:
            hasDifferences = calculateExceptionDifferences(diff, null, null);
            break;

        case PolicyViolation:
            Difference policyDiff = diff.getPolicyViolationDifferences();
            if (null != policyDiff) {
                List<String> newViolations = policyDiff.getAddedValues();
                hasDifferences =
                    ((null != newViolations) &&
                    newViolations.contains(_violation.getDisplayableName()));
            }
            break;

        case BusinessRoleProfile:
            break;
        case BusinessRoleHierarchy:
            break;
        default:
            throw new GeneralException("Difference detection not supported for: " + this);
        }

        _hasDifferences = hasDifferences;

        return _hasDifferences;
    }

    /**
     * Calculate if there are any differences for this "exception" item. Add
     * any new attribute/permission values to the given maps.
     *
     * @param  diff      The difference.
     * @param  newAttrs  The map in which to store new attributes.
     * @param  newPerms  The map in which to store new permissions.
     *
     * @return True if there are any differences.
     */
    @SuppressWarnings("rawtypes")
    public boolean calculateExceptionDifferences(IdentityDifference diff,
                                                 Map<String,Map<String,Boolean>> newAttrs,
                                                 Map<String,Map<String,Boolean>> newPerms)
        throws GeneralException {

        boolean hasDifferences = false;

        EntitlementSnapshot ents = getExceptionEntitlements();
        assert (ents != null);

        String appName = ents.getApplication();
        String nativeIdentity = ents.getNativeIdentity();
        Attributes<String,Object> attrs = ents.getAttributes();
        List<Permission> perms = ents.getPermissions();
        Certification.EntitlementGranularity gran = getEntitlementGranularity();

        if ((attrs != null) && !attrs.isEmpty()) {
            for (Difference linkDiff : diff.getLinkDifferences(appName, nativeIdentity)) {

                List<String> newVals = linkDiff.getAllNewValues();

                // Only process the relevant attribute or value depending on the
                // entitlement granularity.
                if (shouldSkipAttribute(gran, linkDiff, newVals)) {
                    continue;
                }

                // If we're less fine-grained than Value, presence of a
                // difference implies that there are changes.
                if (Certification.EntitlementGranularity.Value.compareTo(gran) < 0) {
                    hasDifferences = true;
                }

                String attr = linkDiff.getAttribute();
                List extraAttrVals = Util.asList(attrs.get(attr));
                hasDifferences |= areNew(newVals, extraAttrVals, attr, newAttrs);
            }
        }

        if ((null != perms) && !perms.isEmpty()) {
            for (PermissionDifference pDiff : diff.getPermissionDifferences(appName)) {

                // Only process the relevant target or right depending on the
                // entitlement granularity.
                if (shouldSkipPermission(gran, pDiff)) {
                    continue;
                }

                // If we're less fine-grained than Value, presence of a
                // difference implies that there are changes.
                if (Certification.EntitlementGranularity.Value.compareTo(gran) < 0) {
                    hasDifferences = true;
                }

                String target = pDiff.getTarget();
                List extraRights = getRights(perms, target);
                List rights = Util.csvToList(pDiff.getRights());
                hasDifferences |= areNew(rights, extraRights, target, newPerms);
            }
        }

        return hasDifferences;
    }

    /**
     * Return the entitlement granularity of the certification this item is a
     * part of, or Value if it can not be calculated.
     */
    private Certification.EntitlementGranularity getEntitlementGranularity() {

        Certification.EntitlementGranularity gran = null;
        if (null != getCertification()) {
            gran = getCertification().getEntitlementGranularity();
        }
        if (null == gran) {
            gran = Certification.EntitlementGranularity.Value;
        }
        return gran;
    }

    /**
     * Return whether attribute difference checking should skip the given link
     * difference.
     */
    private boolean shouldSkipAttribute(Certification.EntitlementGranularity gran,
                                        Difference linkDiff, List<String> newVals)
        throws GeneralException {

        boolean shouldSkip = false;

        EntitlementSnapshot ents = getExceptionEntitlements();

        // Check the attribute for Attribute and finer (ie - gran >= Attribute)
        if (Certification.EntitlementGranularity.Attribute.compareTo(gran) <= 0) {
            String attr = ents.getAttributeName();
            if (!attr.equals(linkDiff.getAttribute())) {
                shouldSkip = true;
            }
        }

        // Skip on Value granularity if this isn't a new value.
        if (Certification.EntitlementGranularity.Value.equals(gran)) {
            Object val = ents.getAttributeValue();
            if (!newVals.contains(val.toString())) {
                shouldSkip = true;
            }
        }

        return shouldSkip;
    }

    /**
     * Return whether permission difference checking should skip the given
     * permission difference.
     */
    private boolean shouldSkipPermission(Certification.EntitlementGranularity gran,
                                         PermissionDifference diff)
        throws GeneralException {

        boolean shouldSkip = false;

        EntitlementSnapshot ents = getExceptionEntitlements();
        
        // Check the target for Attribute and finer (ie - gran >= Attribute)
        if (Certification.EntitlementGranularity.Attribute.compareTo(gran) <= 0) {
            String target = ents.getPermissionTarget();
            if (!target.equals(diff.getTarget())) {
                shouldSkip = true;
            }
        }

        // Skip on Value granularity if this isn't a new right.
        if (Certification.EntitlementGranularity.Value.equals(gran)) {
            String right = ents.getPermissionRight();
            if (!Util.csvToList(diff.getRights()).contains(right)) {
                shouldSkip = true;
            }
        }

        return shouldSkip;
    }

    /**
     * Check whether any of the actualValues are in the given newValues list.
     * If so, add them to the newMap using the given context.
     */
    @SuppressWarnings("rawtypes")
    private boolean areNew(List newValues, List actualValues, String context,
                           Map<String,Map<String,Boolean>> newMap) {

        boolean areNew = false;

        if (null != actualValues) {
            for (Object newVal : newValues) {
                if (actualValues.contains(newVal)) {
                    if (null != newMap) {
                        Map<String,Boolean> isValNewMap = newMap.get(context);
                        if (null == isValNewMap) {
                            isValNewMap = new HashMap<String,Boolean>();
                            newMap.put(context, isValNewMap);
                        }
                        isValNewMap.put(newVal.toString(), true);
                    }

                    areNew = true;
                }
            }
        }

        return areNew;
    }

    /**
     * Get all rights from the List of Permissions on the given target.
     *
     * @param  perms   The List of Permissions from which to retrieve the
     *                 rights.
     * @param  target  The target on which the rights are granted.
     *
     * @return A non-null List of all rights on the given target pulled from
     *         the given List of Permissions.
     */
    private static List<String> getRights(List<Permission> perms, String target)
    {
        List<String> extraRights = new ArrayList<String>();
        if (null != perms)
        {
            for (Permission perm : perms)
            {
                if (target.equals(perm.getTarget()))
                {
                    List<String> currRights = perm.getRightsList();
                    if (null != currRights)
                        extraRights.addAll(currRights);
                }
            }
        }
        return extraRights;
    }

    /**
     * Overridden to (in addition to super class's behavior) clear the returned
     * delegation on the parent identity.
     */
    @Override
    void removeReturnedDelegations() {
        super.removeReturnedDelegations();

        if (null != _parent) {
            CertificationDelegation parentDel = _parent.getDelegation();
            if ((null != parentDel) && parentDel.isReturned()) {
                _parent.setDelegation(null);
            }
        }
    }

    /**
     * Determine whether the action on this item was made during the given scope
     * of the given identity delegation request. This will return true if the
     * action was decided upon in the identity delegation or in an item
     * delegation that was requested from the identity delegation.
     *
     * @param  identityDel  The identity delegation.
     *
     * @return True if this item was decided in the given identity delegation or
     *         within an item delegation that was generated from within the
     *         given identity delegation; false otherwise.
     */
    public boolean wasDecidedInIdentityDelegationChain(CertificationDelegation identityDel) {

        if ((null != _action) && (null != _action.getActingWorkItem()) &&
            (null != identityDel) && identityDel.isActive()) {

            if (_action.getActingWorkItem().equals(identityDel.getWorkItem())) {
                return true;
            }

            boolean itemDelegatedFromIdentity =
                (null != _delegation) && (null != _delegation.getActingWorkItem()) &&
                _delegation.getActingWorkItem().equals(identityDel.getWorkItem());
            if (itemDelegatedFromIdentity &&
                _action.getActingWorkItem().equals(_delegation.getWorkItem())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return whether the given IdentityHistoryItem is for this item.
     *
     * @param  historyItem  The IdentityHistoryItem to check.
     *
     * @return True if the given IdentityHistoryItem is for this item, false
     *         otherwise.
     */
    public boolean matches(IdentityHistoryItem historyItem)
        throws GeneralException {

        return new CertifiableDescriptor(this).matches(historyItem.getCertifiableDescriptor());
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Notifiable implementation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a friendly name for this item.
     */
    public String getNotificationName() {
        return this.getShortDescription();
    }

    /**
     * Return the notification config for the current continuous state.
     */
    public NotificationConfig getNotificationConfig() {

        NotificationConfig config = null;

        if ((null != _continuousState) && (null != getCertification())) {
            ContinuousStateConfig stateConfig =
                getCertification().getContinuousConfig(_continuousState);
            if (null != stateConfig) {
                config = stateConfig.getNotificationConfig();
            }
        }

        return config;
    }

    /**
     * Increment the number of reminders that have been sent.
     */
    public void incrementRemindersSent() {
        this.remindersSent++;
    }

    /**
     * Reset the number of reminders that have been sent.
     */
    public void resetRemindersSent() {
        this.remindersSent = 0;
    }

    /*
     * If delegated return the work item owner, otherwise the first certifier is
     * the notification owner.
     */
    public Identity getNotificationOwner(Resolver resolver)
        throws GeneralException {

        Identity owner = null;

        if (this.isDelegated()) {
            WorkItem workItem =
                resolver.getObjectById(WorkItem.class, _delegation.getWorkItem());
            if (null != workItem) {
                owner = workItem.getOwner();
            }
        }

        if (null == owner) {
            List<String> certifiers = getCertification().getCertifiers();
            if ((null != certifiers) && !certifiers.isEmpty()) {
                owner = resolver.getObjectByName(Identity.class, certifiers.get(0));
            }
        }

        return owner;
    }

    /*
     * CertificationItems don't escalate on max reminders.  They just stop
     * reminding, then a continuous state change will later kick off
     * escalations.
     */
    public boolean isEscalateOnMaxReminders() {
        return false;
    }

    /**
     * Save the given escalation error on this item.
     */
    public void saveEscalationError(InvalidEscalationTargetException error) {
        // No-op ... I don't know what we would do with this.
    }

    /**
     * Return whether this item supports expiration or not.
     */
    public boolean isExpirable() {
        return false;
    }

    /**
     * Return false, expirations are not supported.
     */
    public boolean isExpired() {
        return false;
    }

    /**
     * Return null, expirations are not supported.
     */
    public Date getExpirationDate() {
        return null;
    }

    public void setNeedsRefresh(boolean needsRefresh) {
        _needsRefresh = needsRefresh;
    }
    
    @XMLProperty
    public boolean getNeedsRefresh() {
        return _needsRefresh;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Phaseable implementation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the next phase for this item.
     */
    public Phase getNextPhase() {
        //MEH 16785 cert can be null;
        if(getCertification() != null) {
            return CertificationStateConfig.getNextState(getCertification().getPhaseConfig(),
                                                         _phase, Phase.Active, Phase.End);
        } else {
            return null;
        }
    }

    /**
     * Get the previous enabled Phase (or Active if this is the first phase).
     *
     * @return The previous enabled Phase, or Active if this is the first phase.
     */
    public Phase getPreviousPhase() {
        return CertificationStateConfig.getPreviousState(getCertification().getPhaseConfig(), _phase);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions on a CertificationItem
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Mark the parent certification for refresh. If the item does not
     * have a parent, assume it to be a standalone certification item and skip this
     * step.
     */
    @Override
    public void markForRefresh() {
        try {
            markForRefresh(null);
        } catch (GeneralException ge) {
            // this should never happen since the context isn't used in this case.
            // but, log anyway just in case.
            log.debug("Error while marking certification item for refresh", ge);
        }
    }

    /**
     * Version of markforRefresh that takes a SailPointContext to save the certification
     * after modification.
     * @param context SailPointContext
     * @throws GeneralException
     */
    public void markForRefresh(SailPointContext context) throws GeneralException {
        if (getParent() != null) {
            getParent().markForRefresh(context);
        }
        _needsRefresh = true;
    }

    /**
     * Mark this item as needing a continuous flush.
     */
    @Override
    public void markForContinuousFlush() {
        if (log.isDebugEnabled()) {
            log.debug("Continuous certification support has been removed");
        }
    }

    /**
     * Bulk certify this item using the status and properties from the given
     * action. This does not modify the status on a policy violation item.
     *
     * @param  who          The Identity performing the bulk certification.
     * @param  workItem     The ID of the work item in which the bulk certify is
     *                      taking place.
     * @param  bulkAction   The bulk action that contains the status and
     *                      parameters to use to bulk certify this item.
     * @param  selector     The selector used to determine if this item is to be
     *                      bulk certified. If null, bulk certify this item.
     * @param  autoClose    Whether this bulk certification is part of an auto
     *                      cert close. This allows some cert items (currently
     *                      just policy violations) to be bulk certified that
     *                      would otherwise not be permitted. THIS SHOULD ONLY
     *                      BE USED FOR TESTING OR AUTO CERT CLOSING!

     * @return A possibly empty list of CertificationItems that were not bulk
     *         certified.
     */
    public List<CertificationItem> bulkCertify(Identity who, String workItem,
                                               CertificationAction bulkAction,
                                               CertificationItemSelector selector,
                                               boolean autoClose)
        throws GeneralException { // this one

        List<CertificationItem> notApproved = new ArrayList<CertificationItem>();

        // Only attempt to bulk certify if there is no selector or we match it.
        if ((null == selector) || (selector.matches(this))) {

            /* repurposed the force flag since it didn't seem to be used anywhere,
             * even in the test code.  DHC & JB
            if (force && !isAutoCloseOrTest) {
                LOG.warn("Bulk certifying with force = true ... this should only " +
                         "be called from test code: " + this);
            }
            */

            // If this is a revoke request, automatically convert it to a revoke
            // account request if this is preferred.
            boolean prevRevokeAccount = bulkAction.isRevokeAccount();
            if (bulkAction.isRemediation() && this.useRevokeAccountInsteadOfRevoke()) {
                bulkAction.setRevokeAccount(true);
            }

            try {
                if (canBulkCertify(bulkAction, autoClose)) {
                    // Clear/revoke the delegation and set the action to be bulk approved.
                    if (null != _delegation)
                        this.revokeDelegation();
                    
                    // remove returned delegations from the parent
                    removeReturnedDelegations();

                    CertificationAction action = new CertificationAction();
                    action.bulkCertify(getCertification().getId(), who, workItem, bulkAction);
                    this.setAction(action);

                    // Mark this item as needing to be refreshed by the Certificationer.
                    markForRefresh();
                    markForContinuousFlush();
                }
                else {
                    notApproved.add(this);
                }
            }
            finally {
                // Set this back to what it was.
                bulkAction.setRevokeAccount(prevRevokeAccount);
            }
        }

        return notApproved;
    }

    /**
     * Return whether this item can be bulk certified with the given action.
     */
    private boolean canBulkCertify(CertificationAction bulkAction, boolean autoClose) {

        // Don't allow bulk certifying if the item is locked and the decision
        // is changing.
        Certification cert = getCertification();
        CertificationAction.Status currentStatus =
            (null != _action) ? _action.getStatus() : null;
        if ((isDecisionLockedByPhase(cert, _action, _phase) ||
             isDecisionLockedByRevokes(cert,getDelegation(), getParent().getDelegation(), _action)) &&
                !Util.nullSafeEq(bulkAction.getStatus(), currentStatus)) {
            return false;
        }

        // Don't allow bulk certification if another certification action depends
        // on this action.
        if (!autoClose && _action != null && _action.getParentActions() != null
                && !_action.getParentActions().isEmpty()){
            return false;
        }

        // Don't bulk certify policy violations unless this is an automatic cert
        // close, and don't allow revoke accounts on non-exceptions or IIQ entitlements.
        return (autoClose || !Type.PolicyViolation.equals(_type)) &&
               !isNonExceptionRevokeAccount(bulkAction) &&
               !isIIQEntitlementRevokeAccount(bulkAction);
    }

    /**
     * Return true if the given action is a revoke account request and this item
     * is not an exception. Currently, revoke account is only supported on
     * exception items.
     */
    private boolean isNonExceptionRevokeAccount(CertificationAction action) {

        return ((!Type.Exception.equals(_type) && !Type.Account.equals(_type)) &&
                (CertificationAction.Status.RevokeAccount.equals(action.getStatus()) ||
                 (CertificationAction.Status.Remediated.equals(action.getStatus()) && action.isRevokeAccount())));
    }

    /**
     * Return whether this item is for an IdentityIQ identity entitlement and
     * the given action is a revoke account request.
     */
    private boolean isIIQEntitlementRevokeAccount(CertificationAction action) {
        return Certification.IIQ_APPLICATION.equals(_exceptionApplication) &&
                (CertificationAction.Status.RevokeAccount.equals(action.getStatus()) ||
                (CertificationAction.Status.Remediated.equals(action.getStatus()) && action.isRevokeAccount()));
    }

    /**
     * Return whether account-level actions (for example - approve/revoke account) are
     * allowed on this item.
     */
    public boolean allowAccountLevelActions() {
        return allowAccountLevelActions(_type, _exceptionApplication,
                                       getCertification().getType());
    }

    /**
     * Return whether account-level actions (for example - approve/revoke account) are
     * allowed on an item with the given properties. This method is required
     * because there might be a sparse representation of a certification item from
     * a projection query.
     */
    public static boolean allowAccountLevelActions(Type type, String appName,
                                                   Certification.Type certType) {

        return (null == type || CertificationItem.Type.Account.equals(type) ||
                CertificationItem.Type.Exception.equals(type)) &&
               !Certification.IIQ_APPLICATION.equals(appName) &&
               !Certification.Type.DataOwner.equals(certType) &&
               !Certification.Type.AccountGroupPermissions.equals(certType);
    }

    /**
     * Return whether the revoke account should be used in favor of revoke for
     * this item.
     */
    public boolean useRevokeAccountInsteadOfRevoke() {
        return useRevokeAccountInsteadOfRevoke(getCertification(), getType(), allowAccountLevelActions());
    }

    /**
     * Return whether the revoke account should be used in favor of revoke for
     * an item with the given properties. This method is required because 
     * there might a sparse representation of a certification item from a
     * projection query.
     * @param cert Certification to check 
     * @param itemType Type of the item. If you are dealing with multiple items such as in
     *  a bulk action, this might be null.
     * @param allowBulkAccountActions True if bulk account actions are allowed.
     * @return True if revoke account should be used in favor of revoke.
     */
    public static boolean useRevokeAccountInsteadOfRevoke(Certification cert, CertificationItem.Type itemType,
                                                          boolean allowBulkAccountActions) {

       return (CertificationItem.Type.Account.equals(itemType)
               || Certification.EntitlementGranularity.Application.equals(cert.getEntitlementGranularity())) &&
              allowBulkAccountActions;
    }

    public void clearDecisionForContinuous() throws GeneralException {

        // This is similar to the meat of preAction(), but doesn't do any of the
        // sanity checks around who is making the decision.  No need to mark for
        // continuous flush since there is no decision.
        removeReturnedDelegations();
        markForRefresh();

        _action = null;
        _challenge = null;
    }

    public void clearDecision(SailPointContext context, Identity who, String workItem) throws GeneralException {

        this.clearDecision(context, who, workItem, true);
    }

    private void clearDecision(SailPointContext context, Identity who, String workItem,
                               boolean isActionOnThisItem)
        throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, null, isActionOnThisItem);
        if( _action != null ) {
            _action.clear(getCertification().getId(), who, workItem, null);
        }

        if (this.getPolicyViolation() != null){
            this.getPolicyViolation().setBundleNamesMarkedForRemediation(null);
            this.getPolicyViolation().setEntitlementsMarkedForRemediation(null);
        }
    }

    public void clearDecisionForManagerChange() throws GeneralException {

        CertificationAction.Status currentStatus =
                (null != _action) ? _action.getStatus() : null;

        // Don't allow changing from a revoke
        if (CertificationAction.Status.Remediated.equals(currentStatus) ||
                CertificationAction.Status.RevokeAccount.equals(currentStatus)) {
               return;
        }

        _action = null;
        _challenge = null;

        removeReturnedDelegations();
        
        CertificationEntity parent = getParent();
        CertificationDelegation parentDel = (null != parent) ? parent.getDelegation() : null;

        if (parentDel != null) {
            parent.revokeDelegation();
        }
        
        if (_delegation != null) {
            _delegation.revoke();
        }
        
        markForRefresh();
    }

    public void approve(SailPointContext context, Identity who, String workItem) throws GeneralException {
        this.approve(context, who, workItem, null);
    }

    public void approve(SailPointContext context, Identity who, String workItem, String comments) throws GeneralException {
        approve(context, who, workItem, comments, false);
    }

    public void approve(SailPointContext context, Identity who, String workItem, String comments, boolean autoDecision)
            throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, CertificationAction.Status.Approved);

        // Set the autoDecision attribute if this item was auto-decided
        if (autoDecision) {
            setAutoDecisionGenerated(autoDecision);
        }

        if (_action == null)
            _action = new CertificationAction();
        _action.approve(getCertification().getId(), who, workItem, comments, autoDecision);
    }

    public void approve(SailPointContext context, Identity who, String workItem, ProvisioningPlan additionalActions,
                        String additionalActionOwner, String additionalActionDesc,
                        String additionalActionComments) throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, CertificationAction.Status.Approved);

        if (_action == null)
            _action = new CertificationAction();
        _action.approve(getCertification().getId(), who, workItem, additionalActions,
                        additionalActionOwner, additionalActionDesc, additionalActionComments);
    }

    public void acknowledge(SailPointContext context, Identity who, String workItem, String comments) throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, CertificationAction.Status.Acknowledged);

        if (_action == null)
            _action = new CertificationAction();
        _action.acknowledge(getCertification().getId(), who, workItem, comments);
    }

    public void mitigate(SailPointContext context, Identity who, String workItem, Date expiration,
                         String comments) throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, CertificationAction.Status.Mitigated);

        if (_action == null)
            _action = new CertificationAction();
        _action.mitigate(getCertification().getId(), who, workItem, expiration, comments);
    }

    // TODO: different remediation actions will require different args, do we
    // want a different method for each or just give a map of args?
    /**
     * Remediate this item using the given information. If the details are not
     * specified, the certificationer will calculate them when the remediation
     * is flushed.
     */
    public void remediate(SailPointContext context, Identity who, String workItem,
                          CertificationAction.RemediationAction action,
                          String recipient, String description, String comments,
                          ProvisioningPlan details, ProvisioningPlan additionalActions)
        throws GeneralException {

        // Perform all pre-action checks and actions.
        preAction(context, who, workItem, CertificationAction.Status.Remediated);

        if (_action == null)
            _action = new CertificationAction();

        // additional actions is only allowed for assigned roles.
        _action.remediate(getCertification().getId(), who, workItem, action, recipient, description, comments,
                details, SubType.AssignedRole.equals(getSubType()) ? additionalActions : null);
    }

    /**
     * Remediate this item by revoking the account.
     */
    public void revokeAccount(SailPointContext context, Identity who, String workItem,
                              CertificationAction.RemediationAction action,
                              String recipient, String description, String comments)
        throws GeneralException {

        this.revokeAccount(context, who, workItem, action, recipient, description, comments, true);
    }

    /**
     * Remediate this item by revoking the account.
     */
    private void revokeAccount(SailPointContext context, Identity who, String workItem,
                               CertificationAction.RemediationAction action,
                               String recipient, String description, String comments,
                               boolean isActionOnThisItem)
        throws GeneralException {

        // Perform all pre-action checks and actions.  Don't perform the
        // delegation checks if this is a cascaded revoke account (ie - pushed
        // from another item) since the delegation will be revoked.
        preAction(context, who, workItem, CertificationAction.Status.RevokeAccount, isActionOnThisItem);

        // Push revoke into other items on the same account.  Need to clear
        // existing decisions and revoke delegations.
        if (isActionOnThisItem) {
            List<CertificationItem> otherItems = _parent.getItemsOnSameAccount(this);
            for (CertificationItem other : otherItems) {
                if( !isDecisionLockedByRevokes( getCertification(), other.getDelegation(),
                        other.getParent().getDelegation(),  other.getAction() ) ) {
                    if (other.isDelegated()) {
                        other.revokeDelegation();
                    }

                    other.revokeAccount(context, who, workItem, action, recipient, description, comments, false);
                }
            }
        }

        if (_action == null)
            _action = new CertificationAction();
        _action.revokeAccount(getCertification().getId(), who, workItem, action, recipient, description, comments);
    }

    /**
     * Approve all entitlements on a given account.
     * @param context SailPointContext
     * @param who The Identity approving the account
     * @param workItem  The work item ID from which the request is coming. If
     *                  null, the request is coming from the certification.
     * @param  comments  Approval comments
     * @throws GeneralException
     */
    public void approveAccount(SailPointContext context, Identity who, String workItem, String comments)
        throws GeneralException {
        this.approveAccount(context, who, workItem, comments, true);
    }

    /**
     * Approve all entitlements on a given account.
     * @param context SailPointContext
     * @param who The Identity approving the account
     * @param workItem  The work item ID from which the request is coming. If
     *                  null, the request is coming from the certification.
     * @param  comments  Approval comments
     * @param  isActionOnThisItem  Whether the action is being performed on this
     *                             item directly. If false, another action is
     *                             cascading a save to this item.
     * @throws GeneralException
     */
    private void approveAccount(SailPointContext context, Identity who, String workItem, String comments,
                               boolean isActionOnThisItem)
        throws GeneralException {

        preAction(context, who, workItem, CertificationAction.Status.Approved, isActionOnThisItem);
        if (isActionOnThisItem) {
            List<CertificationItem> otherItems = _parent.getItemsOnSameAccount(this);
            for (CertificationItem other : otherItems) {
                if (other.isDelegated()) {
                    other.revokeDelegation();
                }

                if (other.getAction() == null || !other.getAction().isRemediationKickedOff()){
                    other.approveAccount(context, who, workItem, comments, false);
                }
            }
        }

        if (_action == null)
            _action = new CertificationAction();
        _action.approve(getCertification().getId(), who, workItem, comments);
    }

    public void delegate(SailPointContext context, Identity requester, String workItem, String recipient,
                         String description, String comments)
        throws GeneralException {

        // If any other items on this account (if this is an exception item) had
        // their accounts revoked, clear the revoke account decision.
        if (this.getAction() != null && this.getAction().isRevokeAccount()) {
            clearOtherRevokeAccountItems(context, requester, workItem, false);
        }

        super.delegate(requester, workItem, recipient, description, comments);
    }

    /**
     * Mark the given action as reviewed. This replaces the action on this item
     * with the given action and marks it as reviewed. This allows for making
     * changes before marking as reviewed.
     *
     * @param  who       The person reviewing the action.
     * @param  toReview  The action that is being reviewed.
     */
    public void review(Identity who, CertificationAction toReview) {

        // Can reviews happen within a work item or are they always done by the
        // certification owner?)  Now we are assuming they are done by the
        // certification owner, so we're not storing that information.
        _action = toReview;
        _action.setReviewed(true);

        // Mark this item as needing to be refreshed by the Certificationer.
        markForRefresh();
        markForContinuousFlush();
    }

    /**
     * Perform pre-action handling with delegation checks.
     */
    void preAction(SailPointContext context, Identity who, String workItem, CertificationAction.Status newAction)
        throws GeneralException {

        preAction(context, who, workItem, newAction, true);
    }

    /**
     * An action is about to be taken on this item, perform some checks and
     * possibly get the state of the action and delegation correct based on the
     * previous state and the new state.
     *
     * @param  context    SailPointContext
     * @param  who        Who is making the action request.
     * @param  workItem   The work item ID from which the request is coming. If
     *                    null, the request is coming from the certification.
     * @param  newAction  The new status being requested.
     * @param  isActionOnThisItem  Whether the action is being performed on this
     *                             item directly. If false, another action is
     *                             cascading a save to this item.
     *
     * @throws GeneralException  If the request is an invalid state transition.
     */
    void preAction(SailPointContext context, Identity who, String workItem, CertificationAction.Status newAction,
                   boolean isActionOnThisItem)
        throws GeneralException {

        // KPG - If this gets much more complex, consider using a State pattern
        // to handle the various cases when delegated/not delegated.  Probably
        // will be most useful if we ever get another state (eg - forwarded).

        // KPG - Considered the following:
        //  - Revoke delegation if delegated and item is being changed by
        //    requester?  This is at odds with step one.  Maybe revoking the
        //    delegation needs to be explicit.
        //    DECISION: Items and identities will need to be explicitly revoked
        //    by clicking "Change Decision" on the item or identity.  This will
        //    put up a confirm dialog that makes sure the user wants to revoke
        //    the work item.  If yes, then revoke; if no, leave it as it was.


        // Make sure there's no reason this user can't make the decision
        String error = checkForDecisionErrors(who, workItem, newAction, isActionOnThisItem);
        if (error != null){
            throw new GeneralException(new Message(error));
        }

        // Turn the pseudo-status RevokeAccount into remediated.
        boolean isRevokeAccount = false;
        if (CertificationAction.Status.RevokeAccount.equals(newAction)) {
            newAction = CertificationAction.Status.Remediated;
            isRevokeAccount = true;
        }

        // If this (or the CertificationIdentity) has a returned delegation,
        // clear it.  The Certificationer should have already deleted the
        // associated work item, so it is safe to remove the delegation.
        removeReturnedDelegations();

        // If any other items on this account (if this is an exception item) had
        // their accounts revoked, clear the revoke account decision.
        if (isActionOnThisItem) {
            clearOtherRevokeAccountItems(context, who, workItem, isRevokeAccount);
        }

        // Remove all associations between this action and any children
        if (_action != null && _action.getChildActions() != null && !_action.getChildActions().isEmpty()){
            QueryOptions ops = new QueryOptions(Filter.eq("action.sourceAction", _action));
            List<CertificationItem> sourceItems = context.getObjects(CertificationItem.class, ops);
            if (!sourceItems.isEmpty()){
                for(CertificationItem childItem : sourceItems){
                    if (childItem != null){
                        if (childItem.getAction() != null)
                            childItem.getAction().setSourceAction(null);
                        childItem.clearDecision(context, who, workItem);
                        context.saveObject(childItem);
                    }
                }
            }
            _action.getChildActions().clear();
        }

        if (_action != null && _action.getParentActions() != null && !_action.getParentActions().isEmpty()){
            for(CertificationAction parent : _action.getParentActions()){
                if (parent != null){
                    if (parent.getChildActions() != null)
                        parent.getChildActions().remove(_action);
                    context.saveObject(parent);
                }
            }
            _action.getParentActions().clear();
            _action.setSourceAction(null);
        }

        // If the decision is being changed on an item that was delegated,
        // remove the delegation.
        if ((null != _action) &&
            ((null == newAction) || !newAction.equals(_action.getStatus()))) {
            if ((null != _delegation) && !_delegation.isActive()) {
                _delegation = null;
            }
        }

        // If we have let it get this far, and the item was finished, bring the item back from the dead so it will
        // be refinished (Bug #24419). 
        if (isFinished()) {
            this.setFinishedDate(null);
        }

        // About to set an action, mark to be refreshed.
        markForRefresh(context);
        markForContinuousFlush();
    }

    public String checkForDecisionErrors(Identity who, String workItem, CertificationAction.Status newAction,
                   boolean isActionOnThisItem){

        if (CertificationAction.Status.RevokeAccount.equals(newAction)) {
            newAction = CertificationAction.Status.Remediated;
        }

        CertificationEntity identity = getParent();
        CertificationDelegation parentDel =
            (null != identity) ? identity.getDelegation() : null;

        Certification cert = getCertification();

        CertificationAction.Status currentStatus =
            (null != _action) ? _action.getStatus() : null;

        if (isDecisionLockedByPhase(cert, _action, _phase) &&
            !Util.nullSafeEq(currentStatus, newAction)) {
            return MessageKeys.CERT_ITEM_ERR_CANT_CHG_AFTER_CHLG;
        }

        // Don't allow changing from a revoke if we're processing revokes immediately.
        if (isDecisionLockedByRevokes(cert, getDelegation(),parentDel, _action) &&
            !Util.nullSafeEq(currentStatus, newAction)) {
            return MessageKeys.CERT_ITEM_ERR_CANT_REMOVE_REVOKE;
        }

        if (isActionOnThisItem) {
            // Throw if the person is trying to modify an item they should not be
            // working on (ie - the requester trying to modify a delegate's item or
            // the delegate is trying to modify the requesters item).
            // Case 1 - Identity is delegated, requester tries to make a decision
            // that he did not make before delegating.
            boolean isRequesterOfReturnedItem =
                this.isReturned() && (null != who) && who.getName().equals(_delegation.getActorName());
            if ((null != parentDel) && parentDel.isActive() && !parentDel.isRevoked() &&  // Identity is delegated
                (null == workItem) &&                           // Requester is acting
                (((null == _action) && !isRequesterOfReturnedItem) ||   // Decision not yet made (and not returned)
                 ((null != _action) && parentDel.getWorkItem().equals(_action.getActingWorkItem())))) { // Decision made during delegation

                return MessageKeys.CERT_ITEM_ERR_CANT_DECIDE_ON_DELEGATED_ENTITY;
            }

            // Case 2 - Identity is delegated and work item owner tries to change
            // a decision that was made by the requester.
            if ((null != parentDel) && parentDel.isActive() &&  // Identity is delegated
                (null != workItem) &&                           // Work item owner is acting
                (null != _action) && !this.wasDecidedInIdentityDelegationChain(parentDel)) { // Decision was made elsewhere.
                return MessageKeys.CERT_ITEM_ERR_DELEGATE_CANT_CHG;
            }

            // Case 3 - Item is delegated, requester tries to make a decision.
            // Requester may only remove the delegation (newAction==null)
            if (isDelegated() && null == workItem && newAction != null) {
                return MessageKeys.CERT_ITEM_ERR_CANT_DECIDE_ON_DELEGATED_ITEM;
            }

            // Case 4 - Not delegated, request to change comes from a work item.
            if (!isDelegated() && ((null == parentDel) || !parentDel.isActive()) &&
                (null != workItem)) {
                return MessageKeys.CERT_ITEM_ERR_WORK_ITEM_OWNER_CANT_CHG;
            }

            // Case 5 - The decider is viewing an entity delegation which includes a line
            // item delegation they DID NOT request. They cannot change the line item delegation.
            if (isDelegated() && null != workItem && !workItem.equals(_delegation.getWorkItem())
                    && !who.getName().equals(_delegation.getActorName())){
                return MessageKeys.CERT_ITEM_ERR_CANT_DECIDE_ON_DELEGATED_ITEM;
        }
            }

        return null;
    }

    /**
     * Return whether the decision for this item is locked because 
     * the challenge phase is over, or it is in the challenge phase for a remediated item.
     */
    public static boolean isDecisionLockedByPhase(Certification cert,
                                                  CertificationAction action,
                                                  Certification.Phase itemPhase) {

        CertificationAction.Status currentStatus =
            (null != action) ? action.getStatus() : null;
        boolean isNewDecision = (null == currentStatus);

        boolean eitherEnabled = cert.isPhaseEnabled(Phase.Challenge) ||
                                cert.isPhaseEnabled(Phase.Remediation);

        // Prevent changing the decision if :
        // a)  Cert is past the challenge phase, or
        // b)  Cert is in the challenge phase and item is already remediated.
        // Allow making new decisions, though.  Only do this check if challenge or
        // remediation phases are enabled.
        if (eitherEnabled && !isNewDecision) {
            Certification.Phase phase = itemPhase;
            if (null == phase) {
                phase = (null != cert) ? cert.getPhase() : null;
            }

            if (null != phase)
            {
                if (Certification.Phase.Challenge.compareTo(phase) < 0) {
                    return true;
                }

                if (Certification.Phase.Challenge.equals(phase) &&
                     (CertificationAction.Status.Remediated.equals(currentStatus) ||
                      CertificationAction.Status.RevokeAccount.equals(currentStatus))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Return whether the decision for this item is locked because 
     * revokes are being processed immediately.
     */
    public static boolean isDecisionLockedByRevokes(Certification cert, CertificationDelegation itemDelegation,
                                                    CertificationDelegation parentDelegation,
                                                    CertificationAction action) {

        CertificationAction.Status currentStatus =
                    (null != action) ? action.getStatus() : null;
        boolean isNewDecision = (null == currentStatus);
        boolean hasAdditionalProvisioning = !isNewDecision && action.getAdditionalActions() != null;

        //bug 23523 once remediation has processed 
        //user should not able to change the decision anymore
        if (action != null && action.isRemediationKickedOff()) {
            return true;
        }
        
        if (!cert.isProcessRevokesImmediately() || isNewDecision)
            return false;

        // if the item is still under review it can still be changed
        if (CertificationItem.requiresReview(action, itemDelegation, parentDelegation)){
            return false;
        }

        // Don't allow changing if remediation has already been launched.
        // This includes revokes/remediations as well as approvals where the
        // user has requested provisioning of missing required roles.
        if (CertificationAction.Status.Remediated.equals(currentStatus) ||
             CertificationAction.Status.RevokeAccount.equals(currentStatus)) {
            return true;
        } else if (hasAdditionalProvisioning && CertificationAction.Status.Approved.equals(currentStatus)){
            return true;
        }

        return false;
    }

    private void clearOtherRevokeAccountItems(SailPointContext context, Identity who, String workItemId, boolean isRevokeAccount)
        throws GeneralException {

        if ( ((Type.Exception.equals(_type) )  || (Type.AccountGroupMembership.equals(_type) ) ) && !isRevokeAccount) {
            List<CertificationItem> otherItems = _parent.getItemsOnSameAccount(this);
            for (CertificationItem other : otherItems) {
                if ((null != other.getAction()) && other.getAction().isRevokeAccount()) {
                    // Prevent delegation checks on this clear action since we
                    // may be removing stuff from other work items.
                    other.clearDecision(context, who, workItemId, false);
                }
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Challenges
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if this item has an active challenge.
     */
    public boolean isChallengeActive() {
        return ((null != _challenge) && _challenge.isActive()) || (null != this._parent && this._parent.isAccountRevokeChallengeActive(this));
    }

    /**
     * Return true if this item has an active challenge.
     * This method uses a resolver to query the database for challenges to other revocation items on this item's entity
     */
    public boolean isChallengeActive(Resolver resolver) throws GeneralException {
        return ((null != _challenge) && _challenge.isActive()) || CertificationEntity.isAccountRevokeChallengeActive(this, resolver);
    }

    /**
     * Return whether a challenge has ever been generated for this item.
     *
     * @return True if a challenge has ever been generated for this item, false
     *         otherwise.
     */
    public boolean isChallengeGenerated() {
        return (null != _challenge);
    }

    /**
     * Remove the challenge from this item. This should be called when
     * resetting the item if going back to active phase.
     */
    public void clearChallenge() {
        _challenge = null;
    }

    /**
     * The given challenge work item was just generated for this item. Mark the
     * state accordingly.
     *
     * @param  item   The WorkItem that was created for the challenge.
     * @param  owner  The challenger.
     */
    public void challengeGenerated(WorkItem item, Identity owner) {
        _challenge = new CertificationChallenge(item.getId(), owner);
    }

    /**
     * The challenge for this item has expired.
     *
     * @return Whether the challenge work item was still around.
     */
    public boolean challengeExpired() {

        boolean workItemRemoved = false;

        if (null != _challenge) {
            workItemRemoved = _challenge.expireChallenge();
        }

        // Item should still be in "certified" state since the challenge work
        // item generated, but never challenged.  We don't need to transition
        // to "certified" state here.

        // Mark for refresh so that we will advance the phase of this item
        // (if using rolling phases).
        this.markForRefresh();

        return workItemRemoved;
    }

    /**
     * The item was challenged but a decision (accept/reject) was not made by
     * the end of the challenge period.
     */
    public void challengeDecisionExpired() {

        if (null != _challenge) {
            _challenge.expireChallengeDecision();

            // We're effectively removing the challenge, so we'll need to update
            // the completion status.  This will also cause the rolling phase
            // to advance (if using rolling phases).
            markForRefresh();
        }
    }

    /**
     * Accept the challenge of this decision. This clears the decision and
     * removes the challenge.
     *
     * @param  who       The person rejecting the challenge.
     * @param  comments  Comments about the challenge rejection.
     */
    public void acceptChallenge(Identity who, String comments)
        throws GeneralException {

        assertInChallengePeriod();

        _action = null;

        // _challenge could be null if this is part of an account revocation
        if (null != _challenge) {
            _challenge.acceptChallenge(who, comments);
        }

        // This item went to "certification required" state when challenged.
        // Accepting the challenge should just leave the item in that state.

        // Mark this item to be refreshed.
        markForRefresh();
    }

    /**
     * Reject the challenge of this decision. The decision stays.
     *
     * @param  who       The person rejecting the challenge.
     * @param  comments  Comments about the challenge rejection.
     */
    public void rejectChallenge(Identity who, String comments)
        throws GeneralException {

        assertInChallengePeriod();

        // _challenge could be null if this is part of an account revocation
        if (null != _challenge) {
            _challenge.rejectChallenge(who, comments);
        }

        // Mark this item to be refreshed - we'll need to update the summary
        // status and possibly advance the rolling phase.
        markForRefresh();
    }

    /**
     * Throw an exception if the certification is no longer in the challenge
     * period.
     */
    private void assertInChallengePeriod() throws GeneralException {

        // If the item is in the challenge phase, return.
        if (Certification.Phase.Challenge.equals(_phase)) {
            return;
        }

        // If item is not in challenge phase, the cert needs to be in challenge
        // phase.
        Certification cert = this.getCertification();
        if (null != cert) {
            if (!Certification.Phase.Challenge.equals(cert.getPhase())) {
                throw new GeneralException("No longer in the challenge period.");
            }
        }
    }

    /**
     * Compare this item to another item. This will compare by type and the
     * relevant information for each type.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public int compareTo(CertificationItem item) {

        int comparison = _type.compareTo(item.getType());

        // If the types are equal, look at the fields of the item.
        if (0 == comparison) {
            switch (_type) {
                case BusinessRoleProfile:
                    String profileId = this.getTargetId() != null ? this.getTargetId() : "";
                    String otherProfileId = item.getTargetId() != null ? item.getTargetId() : "";
                    return profileId.compareTo(otherProfileId);
                case BusinessRoleHierarchy:
                case BusinessRoleRequirement:
                case BusinessRolePermit:
                    String bundleId = this.getTargetId() != null ? this.getTargetId() : "";
                    String otherBundleId = item.getTargetId() != null ? item.getTargetId() : "";
                    return bundleId.compareTo(otherBundleId);
                case Bundle:
                    if (!Util.isNullOrEmpty(_bundleAssignmentId)) {
                        comparison = Util.nullSafeCompareTo(_bundleAssignmentId, item.getBundleAssignmentId());
                        if (comparison == 0) {
                            comparison = _bundle.compareTo(item.getBundle());
                        }
                    } else {
                        comparison = _bundle.compareTo(item.getBundle());
                    }
                    break;
                case DataOwner:
                    String entityName = getCertificationEntity().getNativeIdentity() + getCertificationEntity().getTargetName();
                    String otherEntityName = item.getCertificationEntity().getNativeIdentity() + item.getCertificationEntity().getTargetName();
                    comparison = entityName.compareTo(otherEntityName);
                    if (comparison == 0) {
                        comparison = getTargetName().compareTo(item.getTargetName());
                    }
                    // break here if target name is different. continue on to compare exceptionEntitlements if target
                    // names are the same.
                    if (comparison != 0) {
                        break;
                    }
                case AccountGroupMembership: case Exception: case Account:
                    // Order first by app and native ID, then by the entitlement.
                    EntitlementSnapshot e1 = getExceptionEntitlements();
                    EntitlementSnapshot e2 = item.getExceptionEntitlements();

                    comparison = e1.getApplication().compareTo(e2.getApplication());
                    if (0 == comparison) {
                        comparison = e1.getNativeIdentity().compareTo(e2.getNativeIdentity());
                        
                        //bug 20276 need to compare the instances as well
                        if (0 == comparison){
                            String e1Instance = e1.getInstance();
                            String e2Instance = e2.getInstance();
                            if(null != e1Instance && null != e2Instance){
                                comparison = e1Instance.compareTo(e2Instance);
                            }else if ((null != e1Instance) && (null == e2Instance)) {
                                comparison = 1;
                            }
                            else if ((null == e1Instance) && (null != e2Instance)) {
                                comparison = -1;
                            }
                        
                            if (0 == comparison) {
                                String e1Attr = getFirstAttributeName(e1);
                                String e2Attr = getFirstAttributeName(e2);

                                if ((null != e1Attr) && (null != e2Attr)) {
                                    comparison = e1Attr.compareTo(e2Attr);

                                    if (0 == comparison) {
                                        Object e1Val = e1.getAttributes().get(e1Attr);
                                        Object e2Val = e2.getAttributes().get(e2Attr);

                                        if ((e1Val instanceof Comparable) && (e2Val instanceof Comparable)) {
                                            comparison = ((Comparable) e1Val).compareTo((Comparable) e2Val);
                                        }
                                    }
                                }
                                else if ((null != e1Attr) && (null == e2Attr)) {
                                    comparison = 1;
                                }
                                else if ((null == e1Attr) && (null != e2Attr)) {
                                    comparison = -1;
                                }
                                else {
                                    comparison = compareFirstPermissions(e1, e2);
                                }
                            }
                        }
                    }
                    break;
                case PolicyViolation:
                    String n1 = _violation.getConstraintName();
                    String n2 = item.getPolicyViolation().getConstraintName();
                    if ((null != n1) && (null != n2)) {
                        comparison = n1.compareTo(n2);
                    }
                }
        }

        return comparison;
    }

    private int compareFirstPermissions(EntitlementSnapshot e1, EntitlementSnapshot e2) {

        int comparison = Integer.MIN_VALUE;
        
        Permission p1 = getFirstPermission(e1);
        Permission p2 = getFirstPermission(e2);
        if (p1 == null) {
            if (p2 == null) {
                comparison = 0;
            } else {
                // p1 is null p2 is not null
                // p2 is greater
                comparison = -1;
            }
        } else if (p2 == null) {
            // p1 not null and p2 null
            // p1 is greater
            comparison = 1;
        }
        
        //sanity check
        if ((p1 == null || p2 == null) && comparison == Integer.MIN_VALUE) {
            throw new IllegalStateException("if either permission is null it should have been dealt with by now");
        }

        if (p1 != null && p2 != null) {
            comparison = p1.compareTo(p2);
        }
        
        return comparison;
    }

    private String getFirstAttributeName(EntitlementSnapshot es) {

        if ((null != es.getAttributes()) && !es.getAttributes().isEmpty()) {
            List<String> attrNames = new ArrayList<String>(es.getAttributes().getKeys());
            Collections.sort(attrNames);
            return attrNames.get(0);
        }

        return null;
    }

    private Permission getFirstPermission(EntitlementSnapshot es) {

        if ((null != es.getPermissions()) && !es.getPermissions().isEmpty()) {
            List<Permission> perms = new ArrayList<Permission>(es.getPermissions());

            // Permission is comparable, so we can just sort by natural order.
            Collections.sort(perms);
            return perms.get(0);
        }

        return null;
    }

    /**
     * Convenience method to grab the LinkSnapshot.
     *            
     * @param ctx SailPointContext
     * @param application Application name
     * @param instance Instance name
     * @param nativeId Native identity 
     * @return LinkSnapshot, or null if not found
     * @throws GeneralException
     */
    public LinkSnapshot getLinkSnapshot(SailPointContext ctx, String application, String instance,
                                        String nativeId) throws GeneralException {

        if (getExceptionEntitlements() == null)
            return null;

        LinkSnapshot snapshot = null;

        if ((null != nativeId) && (null != application)) {
            IdentitySnapshot idSnap = getParent().getIdentitySnapshot(ctx);
            if (null != idSnap) {
                snapshot= idSnap.getLink(application, instance, nativeId);
            }
        }

        return snapshot;
    }

    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("id", _id);
        builder.append("name", _name);
        builder.append("type", _type);
        // jsl - why the hell would you want all the role ents in the toString,
        // this isn't even necessarily a bundle item
        builder.append("entitlements", getBundleEntitlements());
        builder.append("action", _action);
        if (getParent() != null) {
            builder.append("completed", getParent().getCompleted());
        }

        return builder.toString();
    }

    /**
     * Based on the type of entity being certified, returns the default remediator.
     *
     * @param resolver Resolver to do some querying
     * @return Default remediator or null if none found
     * @throws GeneralException
     */
    public Identity getDefaultRemediator(Resolver resolver) throws GeneralException {
        Identity defaultRemediator = null;

        switch(getType()){
            // all the role comp types goto the role owner
            case BusinessRoleHierarchy:
            case BusinessRolePermit:
            case BusinessRoleRequirement:
            case BusinessRoleGrantedScope:
            case BusinessRoleGrantedCapability:
            case BusinessRoleProfile:
                Bundle role = resolver.getObjectById(Bundle.class, getParent().getTargetId());
                defaultRemediator = role!=null ? role.getOwner() : null;
                break;
            case Bundle:
                // if this is an assigned role, assign the witem to the role owner
                // otherwise continue on the the default case
                if (CertificationItem.SubType.AssignedRole.equals(getSubType())){
                    Bundle assignedRole = getBundle(resolver);

                    // todo rshea TA4710: no clue what do do here since an identity can have multiple assignments
                    // per role
                    if (null != assignedRole) {
                        Identity identity = getIdentity(resolver);
                        RoleAssignment assignment = getRoleAssignment();
                        if (assignment == null && identity != null) {
                            assignment = identity.getRoleAssignmentById(getBundleAssignmentId());
                        }
                        if (assignment!= null && assignment.getAssigner() != null)
                            defaultRemediator = resolver.getObjectByName(Identity.class, assignment.getAssigner());

                        if (defaultRemediator == null && identity != null)
                            defaultRemediator = identity.getManager();

                        if (defaultRemediator == null && assignedRole != null)
                            defaultRemediator = assignedRole.getOwner();

                        // todo what to do is owner is null?
                    } else {
                        // TODO: What if the assignedRole has been deleted?
                        // This happens if the role is deleted before a decision is made on it in a cert.
                    }
                    break;
                } else if (null == getSubType()) {
                    // This should be a detected role.
                    // If the applications on the profiles have the same revoker, let's use that.
                    defaultRemediator = findCommonRevoker(resolver);
                    
                    // Then check the role owner.
                    if (null == defaultRemediator) {
                        Bundle detectedRole = getBundle(resolver);
                        if (detectedRole != null) {
                            defaultRemediator = detectedRole.getOwner();
                        }
                    }
                    
                    break;
                }
            default:
                defaultRemediator = findCommonRevoker(resolver);
        }

        // If nothing else, check system default revoker
        if (null == defaultRemediator) {
            Configuration config = resolver.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            String systemRevoker = config.getString(Configuration.DEFAULT_REMEDIATOR);
            if (null != systemRevoker) {
                defaultRemediator = resolver.getObjectByName(Identity.class, systemRevoker);
            }
        }

        return defaultRemediator;
    }
    
    /**
     * Finds a common revoker among revokers in a list of apps. 
     * Return null if a single common revoker could not be agreed upon or if one did not exist.
     */
    private Identity findCommonRevoker(Resolver resolver) throws GeneralException {
        List<Application> apps = new ArrayList<Application>();
        apps.addAll(getApplications(resolver));
        return RemediationManager.getDefaultRemediator(apps, resolver);
    }
    
    /**
     * Gets whether or not this CertificationItem references any of the specified applications.
     * 
     * @param applicationNames A collection of application names.
     * @param resolver The resolver used to get additional needed data.
     * @return True if the certification matches any application in applicationNames.
     */
    public boolean referencesApplications(List<String> applicationNames, Resolver resolver)
        throws GeneralException
    {
        if (Util.isEmpty(applicationNames)) {
            return false;
        }
        
        IdentitySnapshot identity = getParent().getIdentitySnapshot(resolver);

        List<LinkSnapshot> relevantLinks = new ArrayList<LinkSnapshot>();
        if (identity!=null) {
            for (String appName : applicationNames) { 
                List<LinkSnapshot> appSnapshots = identity.getLinks(appName, true);
                if (appSnapshots != null) {                            
                    relevantLinks.addAll(appSnapshots);
                }
            }
        }
        
        switch (_type) {
            case AccountGroupMembership:
            case DataOwner:
                if (applicationNames.contains(getExceptionEntitlements().getApplicationName())) {
                    return true;
                }
                break;
            case Exception:
                if (identity!=null) {
                    for (LinkSnapshot linkSnap : relevantLinks) {
                        if (linkSnap.getNativeIdentity().equals(getExceptionEntitlements().getNativeIdentity()) &&
                                linkSnap.getApplication().equals(getExceptionEntitlements().getApplicationName())) {
                            return true;
                        }
                    }
                } else {
                    if (applicationNames.contains(getExceptionEntitlements().getApplicationName())) {
                        return true;
                    }
                }
                break;
            case Bundle:
                if (!Util.isEmpty(getBundleEntitlements())) {                    
                    for (LinkSnapshot linkSnap : relevantLinks) {
                        for (EntitlementSnapshot entitlementSnap : getBundleEntitlements()) {
                            if (entitlementSnap.referencesLink(linkSnap)) {
                                return true;
                            }
                        }
                    }       
                } else {
                    // For bundles with no entitlements, *dangerously* assume that the current bundle
                    // has the same relevant applications as it did at the time the cert was generated.
                    // This should be refactored when we eventually augment CertificationItem with a list
                    // of relevant applications
                    Bundle bundle = getBundle(resolver);
                    if (bundle != null) {            
                        
                       Set<Bundle> bundlesToCheck = new HashSet<Bundle>();
                       bundlesToCheck.add(bundle);
                       
                       if (bundle.getRequirements() != null) {
                           bundlesToCheck.addAll(bundle.getRequirements());
                       }
                       
                       for (Bundle b : bundlesToCheck) { 
                           if (b.getApplications() != null && Util.isEmpty(b.getExceptions(null))) {
                               for (String applicationName : applicationNames) {
                                   for (Application application : b.getApplications()) {
                                       if (application.getName().equals(applicationName)) {
                                           return true;
                                       }
                                   }
                               }
                           }
                       }
                    }
                }                    
                break;
            case PolicyViolation:
                if (getPolicyViolation() != null) {
                    if (getPolicyViolation().getLeftBundles() != null &&
                            getPolicyViolation().getRightBundles() !=null && identity!=null) {
                        List<String> bundles = new ArrayList<String>();
                        bundles.addAll(Util.csvToList(getPolicyViolation().getLeftBundles()));
                        bundles.addAll(Util.csvToList(getPolicyViolation().getRightBundles()));
                        if (identity.getBusinessRoles() != null){
                            for(BundleSnapshot bundleSnapshot : identity.getBusinessRoles()){
                                if (bundles.contains(bundleSnapshot.getName())){
                                    if (bundleSnapshot.referencesLink(relevantLinks)){
                                        return true;
                                    }
                                }
                            }
                        }
                    } else if (getPolicyViolation().getRelevantApps() != null) {
                         List<String> apps = getPolicyViolation().getRelevantApps();
                         for (String appName : apps) {
                             if (applicationNames.contains(appName)) {
                                 return true;
                             }
                         }
                    }
                }
                break;
            default:
                return false;
        }
        
        return false;     
    }


    /**
     * Get list of the roles being revoked by this item. This includes
     * the primary role plus any additional revokes if this is a role item
     * or the roles marked for remediation on a policy violation.
     *
     * @return list of role names
     */
    public List<String> getRolesToRemediate(){
        List<String> roles = new ArrayList<String>();

        if (_bundle != null && _action != null){
            if (CertificationAction.Status.Remediated.equals(_action.getStatus()))
                roles.add(_bundle);
            List<String> additionalRevokes = _action.getAdditionalRemediations();
            if (additionalRevokes != null)
                roles.addAll(additionalRevokes);
        }

        return roles;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // ObjectConfig cache
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This one is unusual in that IdentityIQ shares the same configuration as
     * Link.class. It turns out this class can just be passed in
     * because there has to be a mapping for CertificationItem anyway
     * for Hibernate.
     */
    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (_objectConfig == null) {
            // the master cache is maintained over here
            _objectConfig = ObjectConfig.getCache(Link.class);
        }

        if (_objectConfig != null)
            config = (ObjectConfig)_objectConfig.getObject();

        return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Sorting
    //
    // Hack for the unit tests to get a reliable order when
    // comparing XML files.  We provide a comparator for
    // CertificationEntity but don't go any deeper.
    //
    //////////////////////////////////////////////////////////////////////

    public static Comparator<CertificationItem> getSortComparator(final boolean secondarySort) {
        Comparator<CertificationItem> SortComparator = 
                new Comparator<CertificationItem>() {
                    @Override
                    public int compare(CertificationItem ci1, CertificationItem ci2) {
                        int retVal = 0;
                        Collator collator = Collator.getInstance();
                        collator.setStrength(Collator.PRIMARY);
                        retVal = collator.compare(ci1.getSortKey(), ci2.getSortKey());
                        if (secondarySort && retVal == 0) {
                            retVal = collator.compare(ci1.getSecondarySortKey(), ci2.getSecondarySortKey());
                        }
                        return retVal;
                    }
                };
        return SortComparator;
    }

    /**
     * Sorting these are strange because the list can contain
     * different types of things. IdentityIQ is not going to try to
     * sort by type THEN by name, just pick a relevant key to use
     * this can cause the types to be interleaved.
     */
    public String getSortKey() {
        String key = _bundle;
        if (key == null) {
            key = _violationSummary;
            if (key == null) {
                key = _exceptionApplication;
                if (key == null) {
                    // have to have something so Collator won't puke
                    key = "";
                }
            }
        }
        return key;
    }
    
    /**
     * This method was added for unit tests only. If the result of getSortComparator
     * is 0 (the values are equal) we'll use the secondary sort key to sort further.
     * @return _exceptionAttributeValue or empty string
     */
    public String getSecondarySortKey() {
        return (_exceptionAttributeValue != null) ? _exceptionAttributeValue : ""; 
    }

    /**
     * Sort the internal structures of this CertificationItem for unit tests.
     */
    public void sort() {
        // Bundle entitlements aren't consistent in unit tests - may need to add
        // other sorting eventually.
        List<EntitlementSnapshot> ents = getBundleEntitlements();
        if (ents != null) {
            Collections.sort(ents, EntitlementSnapshot.COMPARATOR);
        }

        List<String> names = getApplicationNames();
        if (names != null) {
            Collections.sort(names);
        }
    }

    //@ Override http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5008260
    public void incrementEscalationCount() {
        //_escalationCount++;
    }

    //@ Override http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5008260
    public int getEscalationCount() {
        //return _escalationCount;
        return 0;
    }

    
    @Transient
    public boolean isFinished() {
        return finishedDate != null;
    }
    
    public void setFinishedDate( Date finishedDate ) {
        this.finishedDate = finishedDate;
    }

    public Date getFinishedDate() {
        return finishedDate;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    protected void setPseudo(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        else {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, value);
        }
    }
}
