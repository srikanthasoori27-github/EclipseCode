/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The representation of an enterprise identity whose data
 * is being aggregated.   Known in the marketing model as 
 * the "Identity Cube".
 *
 * Also used to represent users of the sailpoint product, that may or
 * may not have a representation on any of the aggregation resources.
 * SailPoint users may have a password if pass-through authentication is
 * not being used, and may be assigned Capabilities that grant access
 * to protected areas of the product.
 *
 * Author: Jeff
 *
 * ISSUES:
 *
 * For fields derived from resource accounts, if we allow these to be
 * modified, there is the potential that the modifications will be lost
 * the next time an aggregation is performed.  We may need a way
 * to "lock" attributes so that the current value sticks during
 * later aggregations.
 *
 * SEARCHABLE ATTRIBUTES
 *
 * Putting extended attributes in an XML blob is convenient, but
 * the attribute cannot be used in searches.  In order to search
 * on extended attributes we have to store them in a way that
 * is accessible to Hibernate Criteria or HQL queries.  
 *
 * There are two popular ways to do this: by keeping another
 * table of attributes tagged with the primary key of the owning
 * identity, or by providing several columns in the Identity
 * table that may be allocated to extended attributes, a technique
 * some may remember as "inline" attributes.
 *
 * There are strenghts and weaknesses with both approaches.
 * But a more fundamental issue is dynamically selecting a 
 * storage format based on the searchability of an attribute.  
 * Extensible attribute with random data types are very difficult
 * to do with a mapping framework like Hibernate.  You can have
 * a table of extended attributes, but the type of those values
 * must all be the same.  
 *
 * We need the ability to store attributes with complex values
 * such as List<String> and Date so these cannot be stored in 
 * the same memory model as that used for searchable attributes.
 * There are a few approaches here:
 *
 * 1) Store all attributes in one Map<String,Object> serialized
 * as XML, and let HibernatePersistenceManager pick out those attributes
 * that need to be searchable and copy the values to a different place.
 *
 * 2) Have the Identity class put attributes in several locations
 * depending on whether they are searchable.
 *
 * Call the first appoach the "passive" approach, where we wait until
 * immediately before the object is flushed to make decisions on where
 * the values should go.  And the second the "active" approach where the
 * Java memory model always reflects what will be persisted.
 *
 * The passive approach is difficult because you are not required
 * to call PersistenceManager.saveObject to cause a modified object
 * to be flushed.  We have the ability to hook in an Interceptor,
 * but this is called to modify the INSERT or UPDATE statement about
 * to be submitted on the Identity class.  We can change the direct
 * properties of Ideityt, but it is unclear whether we can affect the 
 * flushing of the attributes table at this point.  It may have already 
 * been flushed, or at the very least happen in an undefined order.
 *
 * We're going to take the active approach for now, but should revisit
 * the passive approach as it would be nice to bury this as low as possible.
 * 
 * UPDATE: Now that we have the ObjectConfig cache it is easier to push the
 * the persistence magic down into HibernatePersistenceManager and
 * let it manage its own caches?  Think!
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityHistoryService;
import sailpoint.api.SailPointContext;
import sailpoint.tools.BidirectionalCollection;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The core class representing the "identity cube".
 */
@Indexes({
    @Index(name="spt_identity_created", property="created"),
    @Index(name="spt_identity_modified", property="modified")
        })
@XMLClass
public class Identity extends AbstractCertifiableEntity
    implements Cloneable
{
    private static final Log log = LogFactory.getLog(Identity.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 5179752444594149670L;

    /**
     * The name of the built-in system administrator.
     * This will always exist.
     */
    public static final String ADMIN_NAME = BrandingServiceFactory.getService().getAdminUserName();

    /**
     * @exclude
     * Old name for the system administrator.
     */
    public static final String CIQ_ADMIN_NAME = "ciqadmin";

    /**
     * @exclude
     * Old name for the system administrator.
     */
    public static final String OLD_ADMIN_NAME = "Admin";

    /**
     * @exclude
     * Name of the attribute that indicates that the given
     * identity is a system identity or workgroup.
     */
    public static final String ATTR_SYSTEM_USER = "systemUser";

    /**
     * Bundle summary is stored in a VARCHAR so it can be used 
     * reliably in ORDER_BY clauses (Oracle and/or Hibernate have
     * problems ordering on CLOB columns).
     *
     * Because it is easy to overflow this, be careful when
     * calculating it to keep it under this length. This needs to match
     * the length in the Hibernate mapping file.
     *
     * bug14287: changed from 128 to 2000
     */
    public static final int MAX_BUNDLE_SUMMARY = 2000;

    //////////////////////////////////////////////////////////////////////
    //
    // Attributes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The names of the standard attributes.
     * These names can be used in an ObjectConfig to define the
     * mappings from resource account attributes.
     * 
     * They will be exposed as properties of the Identity object
     * so they can be persisted in Hibernate as searchable columns.
     * The values themselves will usually be inside an Attributes
     * map which is how they will be serialized in XML.
     *
     * The "manager" attribute is special in that it will become
     * an object reference to another Identity object rather than just
     * a name.
     */
    public static final String ATT_MANAGER = "manager";

    public static final String ATT_DISPLAYNAME = "displayName"; 
    public static final String ATT_FIRSTNAME = "firstname"; 
    public static final String ATT_LASTNAME = "lastname";
    public static final String ATT_EMAIL = "email";
    public static final String ATT_INACTIVE = "inactive";
    public static final String ATT_USERNAME = "name";
    public static final String ATT_PASSWORD_LAST_CHANGED = "spt_password_last_changed";
    public static final String ATT_PROTECTED = "protected";
    public static final String ATT_TYPE = "type";
    public static final String ATT_SOFTWARE_VERSION = "softwareVersion";
    public static final String ATT_ADMINISTRATOR = "administrator";

    public static final String ATT_RAPIDSETUP_PROC_STATE = "rapidSetupProcessingState";

    public static final String ATT_BUNDLE_SUMMARY = "bundleSummary";
    public static final String ATT_EXCEPTIONS = "exceptions";
    public static final String ATT_LAST_REFRESH = "lastRefresh";
    public static final String ATT_LAST_LOGIN = "lastLogin";
    public static final String ATT_CAPABILITIES = "capabilities";
    public static final String ATT_COMPOSITE_SCORE = "scorecard.compositeScore";
    public static final String ATT_RIGHTS = "rights";
    public static final String ATT_WORKGROUPS = "workgroups";
    public static final String ATT_MANAGER_STATUS = "managerStatus";
    public static final String ATT_VERIFICATION_TOKEN = "verificationToken";

    public static final String ATT_CORRELATED = "correlated";
    
    /**
     * @exclude
     * The Hibernate property name used for the detected role list.
     * This must also match the name of the ObjectAttribute in the
     * identity config.  Historically this has been called 'bundles' rather
     * than 'detectedRoles' and it would be difficult to upgrade the
     * ObjectConfig's now.
     */
    public static final String ATT_BUNDLES = "bundles";

    /**
     * @exclude
     * The Hibernate property name used for the assigned role list.
     * This must also match the name of the ObjectAttribute in the
     * identity config.
     */
    public static final String ATT_ASSIGNED_ROLES = "assignedRoles";

    /**
     * @exclude
     * Special attribute on the identity that is set to true when the identity
     * is created, and set back to false after the creation processing has
     * taken place.  See needsCreateProcessing() for more info.
     */
    public static final String ATT_NEEDS_CREATE_PROCESSING =
    "needsCreateProcessing";

    /**
     * @exclude
     * Special attribute on the identity that holds snapshotted information
     * to help with trigger processing.  Previous values from the identity are
     * put into this map at the beginning of aggregation for each identity and
     * are pulled back out and removed when the triggers are processed.
     */
    public static final String ATT_TRIGGER_SNAPSHOTS = "triggerSnapshots";
    
    public static final String ATT_WORKGROUP_FLAG = "workgroup";

    /**
     * @exclude
     * Special attribute that holds the id of an active refresh workflow case.
     * Used by Identitizer to prevent the launching of another workflow
     * if a refresh is done before the last workflow completes.
     */
    public static final String ATT_PENDING_REFRESH_WORKFLOW =
        "pendingRefreshWorkflow";

    /**
     * Names of all the standard attributes.
     * Can be used to filter the attributes into system and external.
     */ 
    public static final String[] SYSTEM_ATTRIBUTES = {
        ATT_MANAGER,
        ATT_FIRSTNAME,
        ATT_LASTNAME,
        ATT_EMAIL,
        ATT_INACTIVE,
        ATT_DISPLAYNAME,
        ATT_PASSWORD_LAST_CHANGED,
            ATT_RAPIDSETUP_PROC_STATE
    };

    /**
     * @exclude
     * Names of attributes that are used internally and should not be displayed
     * in the UI.  These will be filtered out of snapshots and differences.
     */
    public static final String[] HIDDEN_ATTRIBUTES = {
        ATT_TRIGGER_SNAPSHOTS,
        ATT_NEEDS_CREATE_PROCESSING,
        ATT_BUNDLES,
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Preferences
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Preference name whose value is the name of an Identity
     * to whom notifications and work items will be forwarded.
     */
    public static final String PRF_FORWARD = "forward";

    /**
     * Preference name whose value is the Date at which to start forwarding 
     * notifications and work items to the forward Identity
     */
    public static final String PRF_FORWARD_START_DATE = "forwardStartDate";

    /**
     * Preference name whose value is the Date at which to stop forwarding 
     * notifications and work items to the forward Identity
     */
    public static final String PRF_FORWARD_END_DATE = "forwardEndDate";

    /**
     * Preference that contains a list of RoleAssignment objects containing
     * extra information about the roles on the _assignedRoles list.
     */
    public static final String PRF_ROLE_ASSIGNMENTS = "roleAssignments";

    /**
     * Preference that contains a list of RoleRequest objects containing
     * information about permitted roles that were requested.
     */
    public static final String PRF_ROLE_REQUESTS = "roleRequests";

    /**
     * Preference that contains a list of RoleDetection objects containing
     * information about the roles on the _detectedRoles (detected roles) list.
     */
    public static final String PRF_ROLE_DETECTIONS = "roleDetections";

    
    public static final String PRF_WORKGROUP_NOTIFICATION_OPTION = "workgroupNotificationOption";
    
    /**
     * Preferences that contains a list of Assignments to entitlements
     * or anything to be tracked.
     */
    public static final String PRF_ATTRIBUTE_ASSIGNMENTS = "attributeAssignments";
    
    /**
     * Preference that contains the number of consecutive failed login attempts
     */
    public static final String PRF_BAD_PASSWORD_COUNT  = "badPasswordCount";

    /**
     * Preference that contains a "use by" date. This is set when
     * identities are created from LCM but do not yet have any 
     * account Links. A use by date that has not expired will prevent
     * the identity from being pruned by the refresh task when the
     * pruneIdentities option is enabled. The value of the attribute 
     * is expected to be a long utime.
     */
    public static final String PRF_USE_BY_DATE = "useBy";


    //////////////////////////////////////////////////////////////////////
    //
    // RapidSetup Processing State values
    //
    //////////////////////////////////////////////////////////////////////
    public static final String RAPIDSETUP_PROC_STATE_NEEDED = "needed";
    public static final String RAPIDSETUP_PROC_STATE_FORCED = "forced";
    public static final String RAPIDSETUP_PROC_STATE_SKIPPED = "skipped";
    public static final String RAPIDSETUP_PROC_STATE_PROCESSED = "processed";

    //
    // Types
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass(xmlname="WorkgroupNotifationOption")
    public static enum WorkgroupNotificationOption {

        // Notify both the distribution list and members
        Both,

        // Notify only the members
        MembersOnly,

        // Notify only the distribution list
        GroupEmailOnly,

        // Do not notify anyone
        Disabled 
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration Cache
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A static configuration cache used by the persistence manager
     * to make decisions about searchable extended attributes.
     */
    static private CacheReference _objectConfig;

    /**
     * A cache of the system attribute names, used for filtering out
     * the extended attributes.
     * @ignore
     * TODO: This came before the far more general ObjectConfig, 
     * try to get rid of this.
     */
    static private Map<String,String> _systemAttributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Indicates that this user is protected from automatic deletion
     * during cube refresh. Deletion can occur on a refresh if
     * encounter identities that have no more links are encountered.
     */
    boolean _protected;

    /**
     * Indicates that something changed in this identity and it
     * should be refreshed.  Usually set after an aggregation but
     * can also be set manually from provisioning plans.
     */
    boolean _needsRefresh;

    /**
     * Information for each account that is correlated to this identity.
     */
    private List<Link> _links;

    /**
     * Detected entitlement bundles (roles).
     */
    private List<Bundle> _detectedRoles;

    /**
     * Assigned entitlement bundles (roles).
     * These can either be "hard" assignments or rule based assignments.
     */
    private List<Bundle> _assignedRoles;

    /**
     * A summary of the assigned Bundles, formatted as a csv of
     * displayable names. This is maintained as a simple string property
     * of Identity so it can be easily used in searches and
     * displayed without having to instantiate the Identity and
     * all the Bundles.
     *
     */
    String _bundleSummary;
    
    /**
     * A summary of the assigned roles, formatted as a csv of
     * displayable names. This is maintained as a simple string property
     * of Identity so it can be easily used in searches and
     * displayed without having to instantiate the Identity and
     * all the roles.
     *
     */
    String _assignedRoleSummary;

    /**
     * A list of metadata for all roles associated with this identity.
     *
     */
    private List<RoleMetadata> _roleMetadatas;
    
    /**
     * A collection of entitlements, scoped by application, that this
     * identity holds but which do not fit into any of the defined Bundles.
     * Created by the EntitlementCorrelator during or after aggregation.
     */
    private List<EntitlementGroup> _exceptions;

    /**
     * A list of mitigations requests for this identity. These are stored
     * when certification items are mitigated. They live on the identity
     * because the certification reports have a limited lifespan (they are
     * archived), and mitigation requests can extend past this period.
     */
    private List<MitigationExpiration> _mitigationExpirations;

    /**
     * Extended attributes.
     */
    Attributes<String,Object> _attributes;

    /**
     * Another map of system attributes.
     * These are kept out of _attributes to avoid name collisions.
     */
    private Map<String,Object> _preferences;

    /**
     * An external map of UI preferences. Made external so it
     * can be lazy loaded.
     */
    UIPreferences _uiPreferences;

    //
    // Standard Attributes
    // These are pulled from the Links during aggregation, but we 
    // may want these to be manually editable and "locked" to prevent
    // being overwritten during later aggregations.
    //

    /**
     * Immediate manager.  Normally derived during aggregation but
     * can be modified interactively.
     * @ignore
     * TODO: If this can be modifed after aggregation, may need a way
     * to "lock" it so that the next aggregation doesn't trash the value.
     */
    private Identity _manager;

    /**
     * Flag set if we are thought to be a manager of other identities.
     * This is set during identity refresh, aggregation, and various
     * other scans. Since a strict bidirectional
     * relationship between an manger and the subordinates is not maintained, this flag
     * is technically unreliable, though when in error it will more
     * often have a false positive than a false negative.
     *
     * This is used simply as an optimization for a few UI searches
     * that need to display a list of managers.
     */
    boolean _managerStatus;

    //
    // System Attributes
    // These are maintained by the system and are not associated with
    // a resource.
    //

    /**
     * List of special things that can be done within the SailPoint application.
     */
    private List<Capability> _capabilities;

    /**
     * List of scopes that are controlled by this Identity. This allows the
     * Identity to access any object within these scopes or sub-scopes.
     */
    private List<Scope> _controlledScopes;

    /**
     * Whether this identity implicitly has control over their assigned scope
     * (in addition to the explicit controlledScopes list). If null, this
     * defaults to a system configuration option.
     */
    private Boolean _controlsAssignedScope;
    
    /**
     * Password for authentication if no pass-through authentication is being 
     * used or if this user does not have a representation in the
     * pass-through authentication resource.
     */
    private String _password;
    
    /**
     * Date the password will expire.
     */
    Date _passwordExpiration;

    /**
     * History of previous passwords. This is a string but the
     * format is undefined. It should only be used by Identitizer.
     */
    String _passwordHistory;

    /**
     * A list of authentication question answers.
     */
    List<AuthenticationAnswer> _authenticationAnswers;
    
    /**
     * The date at which this identity was locked due to too many failed
     * authentication question, authentication attempts, or failed login attempts.
     * If null or in the past, the user is no longer locked out.
     */
    Date _authLockStart;
    
    /**
     * The number of failed authentication questions, authentication attempts.
     */
    int _failedAuthQuestionAttempts;
    
    /**
     * The number of consecutive failed IdentityIQ login attempts.
     */
    int _failedLoginAttempts;
    
    /**
     * Compliance scorecard for this identity.
     */
    private Scorecard _scorecard;

    /**
     * Date of last login.
     */
    private Date _lastLogin;

    /**
     * Date of last aggregation refresh.
     */
    private Date _lastRefresh;

    /**
     * List of previous certifications.
     */
    List<CertificationLink> _certifications;

    /**
     * Identity specific activity configuration. This configuration will
     * be applied in addition to any assigned activity enabled profiles.
     */
    private ActivityConfig _activityConfig;

    /**
     * An Identity is correlated if it has one or more accounts on
     * an authoritative application.
     */
    boolean _correlated;

    ///////////////////////////////////////////////////////////////////////////
    //
    // Workgroup specific attributes
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Flag that indicates if this identity is a workgroup.
     * If true the Identity is a workgroup otherwise its a user.
     */
    boolean _isWorkgroup;

    /**
     * List of workgroups that this identity belongs.
     */
    List<Identity> _workgroups;
    
    

    /**
     * Administrator for a non-human identity, used in RPA and Service identity types.
     */
    Identity _administrator;

    
    /**
     * extended identity attributes for Identity->Identity relationships
     */
    Identity _extendedIdentity1;
    Identity _extendedIdentity2;
    Identity _extendedIdentity3;
    Identity _extendedIdentity4;
    Identity _extendedIdentity5;

    /**
     * encapsulated capability related stuff for identity
     */
    private transient CapabilityManager _capabilityManager = new CapabilityManager();
    
    /**
     * Application used to authenticate this identity.
     */
    private transient String authApplication;
    
    public String getAuthApplication() {
        return authApplication;
    }

    public void setAuthApplication(String authApplication) {
        this.authApplication = authApplication;
    }

    /**
     * Account on authApplication that was used to authenticate this identity.
     */
    private transient String authAccount;

    public String getAuthAccount() {
        return authAccount;
    }

    public void setAuthAccount(String authAccount) {
        this.authAccount = authAccount;
    }

    /**
     * @exclude
     * A LAZY loaded list of the IdentityEntitlement objects
     * intended to be used strictly by hibernate. This is 
     * the inverse side of a large Collection. It is private
     * for a reason and we should keep it that way to prevent
     * scale issues.
     */
    private List<IdentityEntitlement> _identityEntitlements;
    
    private boolean _correlatedOverridden;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Configuration Cache
    //
    //////////////////////////////////////////////////////////////////////

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (_objectConfig == null) {
            // the master cache is maintained over here
            _objectConfig = ObjectConfig.getCache(Identity.class);
        }

        if (_objectConfig != null)
            config = (ObjectConfig)_objectConfig.getObject();

        return config;
    }

    /**
     * This is the accessor required by the generic support for
     * extended attributes in SailPointObject. It is NOT an XML property.
     */
    public Map<String, Object> getExtendedAttributes() {
        return getAttributes();
    }

    /**
     * Search through the extended attributes and return an appropriate value.
     * If a 'normal' extended attribute is not found, search through
     * the extendedIdentity attributes. If key contains a '.' (meaning the search is probably
     * looking for an attribute on the Identity) then try and grab that from
     * the extendedIdentity value as well.
     *
     * @param key
     * @return The requested attribute value
     */
    @Override
    public Object getExtendedAttribute(String key) {
        Object value = super.getExtendedAttribute(key);

        if (value == null) {
            String name = key;
            if (key.contains(".")) {
                name = key.substring(0, key.indexOf('.'));
            }
            value = this.getAttribute(name);

            if (value != null && value instanceof Identity) {
                if (key.contains(".")) {
                    name = key.substring(key.indexOf('.') + 1);
                }
                value = ((Identity) value).getAttribute(name);
            }
        }
        return value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctors
    //
    //////////////////////////////////////////////////////////////////////

    public Identity() {
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    
    public void visit(Visitor v) throws GeneralException {
        v.visitIdentity(this);
    }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getId())
            .append("name", getName())
            .toString();
    }

    /**
     * Returns a UI friendly short name for Identity.
     *
     * Currently this is being used when the Certificationer needs
     * to return a warning like 'no identities to certify'.
     *
     * @param plural Should the type name be plural?
     * @return Entity type name pluralized if plural flag is true
     */
    public String getTypeName(boolean plural) {
        return (plural ? "Identities" : "Identity");
    }

    /**
     * Indicates that you can difference this entity. In some cases,
     * such as AccountGroup/ManagedAttribute objects, you cannot. 
     * This allows the certification to skip the differencing step.
     *
     * @see sailpoint.api.Certificationer#addEntity
     * @see sailpoint.api.Certificationer#renderDifferences
     *
     * @return true if this entity can be differenced
     */
    public boolean isDifferencable(){
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Load
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Load enough for provisioning and plan compilation
     */
    public void loadForProvisioning() {

        if (_links != null) {
            for (Link link : _links)
                link.load();
        }
   
        if (_detectedRoles != null) {
            for (Bundle b : _detectedRoles)
                b.load();
        }

        if (_assignedRoles != null) {
            for (Bundle b : _assignedRoles)
                b.load();
        }
    }


    /**
     * @exclude
     * Specialized load for change detection.
     * Decided not to do this for everything since we haven't been 
     * until 3.1 and I don't want to slow anything down.
     */
    public void loadForChangeDetection() {

        if (_assignedScope != null)
            _assignedScope.getName();

        if (_controlledScopes != null) {
            for (Scope s : _controlledScopes)
                s.getName();
        }

        if (_capabilities != null) {
            for (Capability c : _capabilities)
                c.getName();
        }

        if (_assignedRoles != null) {
            for (Bundle b : _assignedRoles)
                b.getName();
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Indicates that this user is protected from automatic deletion
     * during cube refresh. Deletion can occur on a refresh if
     * identities that have no more links are encountered.
     */
    @XMLProperty
    public boolean isProtected() {
        return _protected;
    }

    public void setProtected(boolean b) {
        _protected = b;
    }

    /**
     * Indicates that this identity needs to be refreshed after some change,
     * typically aggregation.
     */
    @XMLProperty
    public boolean isNeedsRefresh() {
        return _needsRefresh;
    }

    public void setNeedsRefresh(boolean b) {
        _needsRefresh = b;
    }

    /**
     * The identity that was determined to be the manager
     * of this identity during aggregation.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getManager() {
        return _manager;
    }

    public void setManager(Identity u) {
        _manager = u;
    }

    /**
     * True if this identity was found to be the manager
     * of at least one other identity.
     */
    @XMLProperty
    public boolean getManagerStatus() {
        return _managerStatus;
    }

    public void setManagerStatus(boolean b) {
        _managerStatus = b;
    }

    /**
     * VerificationToken Object stored on the identity to aid in Password Reset
     * via SMS
     */
    public VerificationToken getVerificationToken() {
    	return (VerificationToken)Util.get(_attributes, ATT_VERIFICATION_TOKEN);
    }
    
    public void setVerificationToken(VerificationToken t) {
    	setPseudo(ATT_VERIFICATION_TOKEN, t);
    }
    
    /**
     * Information for each account correlated to this identity.
     */
    @BidirectionalCollection(elementClass=Link.class, elementProperty="identity")
    public List<Link> getLinks() {
        return _links;
    }

    /**
     * Set the links on the Identity. This should only be used by the
     * persistence mechanisms since it does not set the owner on the Links -
     * programmatic use should call <code>add(Link></code> instead.
     * 
     * @param  links  The List of Links to set.
     * 
     * @deprecated This does not set the owner on the Links in the List and
     *             should only be used by the persistence mechanisms. Instead,
     *             use {@link #add(Link)}
     */
    @Deprecated
    public void setLinks(List<Link> links) {
        _links = links;
    }

    /**
     * Set the Link list and make sure the Links point
     * back to their Identity. This is not done in setLinks
     * because the setter is what Hibernate uses when rehydrating
     * the object and the iteration will cause all the Links
     * to be fetched, eliminating the effect of lazy loading.
     */
    @SuppressWarnings("deprecation")
    public void assignLinks(List<Link> links) {
        if (links != null) {
            for (Link link : links)
                link.setIdentity(this);
        }
        setLinks(links);
    }

    /**
     * @exclude
     * Alternative property accessor for the Link list used by the XML
     * serializer.
     *
     * It is important that Links point back to their parent Identity, 
     * applications should use the assignLinks method to make sure that
     * happens. This is not done in the setLinks property setter because
     * that is what Hibernate uses and we do not want to cause the Links
     * to be fetched when rehydrating.
     *
     * When editing Identity objects in XML however, it is a common
     * error to forget the Identity reference in the Link which causes
     * lots of problems later.  We therefore expose a special property
     * just for the XML serialization that uses assignLinks rather
     * than setLinks.  But we can't use assignLinks directly because
     * the XML expects the usual get/set naming convention.
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Links")
    public List<Link> getXmlLinks() {
        return _links;
    }

    /**
     * @exclude
     */
    public void setXmlLinks(List<Link> links) {
        assignLinks(links);
    }

    /**
     * Return the list of assigned roles.  Roles may either
     * be assigned manually or automatically with
     * assignment rules.  To know the difference use {@link #getRoleAssignments()}
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getAssignedRoles() {
        return _assignedRoles;
    }

    public void setAssignedRoles(List<Bundle> roles) {
        _assignedRoles = roles;
    }

    /**
     * Return the list of detected roles.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getBundles() {
        return _detectedRoles;
    }

    public void setBundles(List<Bundle> bundles) {
        _detectedRoles = bundles;
    }

    /**
     * Return the list of detected roles.
     * This is the same as that returned by {@link #getBundles()}
     * but it uses our modern role terminology.
     */
    public List<Bundle> getDetectedRoles() {
        return _detectedRoles;
    }

    public void setDetectedRoles(List<Bundle> roles) {
        _detectedRoles = roles;
    }

    @XMLProperty
    public void setBundleSummary(String s) {
        _bundleSummary = s;
    }

    /**
     * Return a string containing a summary of the detected roles.
     * This will be a CSV of role names.
     */
    public String getBundleSummary() {
        return _bundleSummary;
    }
    
    @XMLProperty
    public void setAssignedRoleSummary(String s) {
        _assignedRoleSummary = s;
    }

    /**
     * Return a string containing a summary of the assigned roles.
     * This will be a CSV of role names.
     */
    public String getAssignedRoleSummary() {
        return _assignedRoleSummary;
    }
    
    /**
     * Return the list of metadata for roles associated with this Identity.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<RoleMetadata> getRoleMetadatas() {
        return _roleMetadatas;
    }

    public void setRoleMetadatas(List<RoleMetadata> roleMetadatas) {
        _roleMetadatas = roleMetadatas;
    }

    /**
     * A collection of entitlements, scoped by application, that this
     * identity holds but which do not fit into any of the detected roles.
     * Created during entitlement correlation.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<EntitlementGroup> getExceptions() {
        return _exceptions;
    }

    public void setExceptions(List<EntitlementGroup> ents) {
        _exceptions = ents;
    }

    /**
     * A list of mitigations requests for this identity. These are stored
     * when certification items are mitigated. They live on the identity
     * because the certification reports have a limited lifespan (they are
     * archived), and mitigation requests can extend past this period.
     */
    public List<MitigationExpiration> getMitigationExpirations() {
        return _mitigationExpirations;
    }

    @XMLProperty(mode=SerializationMode.LIST)
        public void setMitigationExpirations(List<MitigationExpiration> exps) {
        _mitigationExpirations = exps;
    }

    /**
     * Sets the extended attributes.
     * Note that this is the property setter for Hibernate, there is no 
     * attempt to make the "searchable" attribute bifurcation here.  
     * You must call the single setAttribute or assimilateAttributes method
     * to make decisions about searchability.
     */
    public void setAttributes(Attributes<String,Object> a) {
        _attributes = a;
    }

    /**
     * Extended attributes. Some of these are promoted from the links, 
     * some can be generated by rules, some can be edited manually.
     */
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    /**
     * @exclude
     * Alternative property for XML serialization, this one understands
     * how to split the incoming attributes map into searchable and
     * XML blob  attributes.
     *
     * Note that the property name begins with A which will push
     * it to the top of the list elements since the serializer
     * sorts the child elements by property name.  A convenience when
     * trying to read Identity XML in the debug pages or console.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAXmlAttributes() {
        return _attributes;
    }

    /**
     * @exclude
     */
    public void setAXmlAttributes(Attributes<String,Object> a) {
        assimilateAttributes(a);
    }

    public void assimilateAttributes(Attributes<String,Object> a) {

        if (a != null) {
            Iterator<Map.Entry<String,Object>> it = a.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,Object> entry = it.next();
                String key = entry.getKey();
                Object value = entry.getValue();
                // filter nulls?
                if (value != null)
                    setAttribute(key, value);
            }
        }
    }

    /**
     * True if the identity has one or more accounts on
     * an authoritative application.
     */
    @XMLProperty
    public boolean isCorrelated() {
        return _correlated;
    }

    public void setCorrelated(boolean b) {
        _correlated = b;
    }

    /**
     * True if correlated flag was set manually in the UI
     */
    @XMLProperty
    public boolean isCorrelatedOverridden() {
        return _correlatedOverridden;
    }

    public void setCorrelatedOverridden( boolean correlatedOverridden ) {
        _correlatedOverridden = correlatedOverridden;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // System Attributes
    //
    //////////////////////////////////////////////////////////////////////
    
    @XMLProperty
    public String getPassword()
	{
	    return _password;
	}

    public void setPassword(String password) {
	    _password = password;
	}

    @XMLProperty
    public Date getPasswordExpiration()
	{
	    return _passwordExpiration;
	}

    public void setPasswordExpiration(Date d)
	{
	    _passwordExpiration = d;
	}

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getPasswordHistory()
	{
	    return _passwordHistory;
	}

    public void setPasswordHistory(String hist)
	{
	    _passwordHistory = hist;
	}

    public List<AuthenticationAnswer> getAuthenticationAnswers() {
        return _authenticationAnswers;
    }

    /**
     * @deprecated Use {@link #assignAuthenticationAnswers(List)} instead, which
     * sets the owning identity appropriately.
     */
    @Deprecated
    public void setAuthenticationAnswers(List<AuthenticationAnswer> answers) {
        _authenticationAnswers = answers;
    }

    public void removeAuthenticationAnswer(AuthenticationAnswer answer) {
        if (null != _authenticationAnswers) {
            _authenticationAnswers.remove(answer);
        }
    }
    
    /**
     * Set the authentication answers for this identity.
     */
    public void assignAuthenticationAnswers(List<AuthenticationAnswer> answers) {
    	List<AuthenticationAnswer> buildList = new ArrayList<AuthenticationAnswer>();
        if (null != answers) {
            for (AuthenticationAnswer answer : answers) {
            	if (answer.getQuestion() != null) {
            		answer.setIdentity(this);
            		buildList.add(answer); // add as we go
            	}
            }
        }
        _authenticationAnswers = buildList; // reset list
    }
    
    /**
     * @exclude
     * Only used for XML persistence so the identity will get set
     * correctly on the AuthenticationAnswer.
     * @deprecated use {@link #getAuthenticationAnswers()} 
     */
    @Deprecated
    @XMLProperty(xmlname="AuthenticationAnswers")
    public List<AuthenticationAnswer> getXmlAuthenticationAnswers() {
        return _authenticationAnswers;
    }

    /**
     * @exclude
     * Only used for XML persistence so the identity will get set
     * correctly on the AuthenticationAnswer.
     * @deprecated use {@link #assignAuthenticationAnswers(java.util.List)} 
     */
    @Deprecated
    public void setXmlAuthenticationAnswers(List<AuthenticationAnswer> answers) {
        assignAuthenticationAnswers(answers);
    }
    
    @XMLProperty
    public Date getAuthLockStart() {
        return _authLockStart;
    }

    public void setAuthLockStart(Date lockStart) {
        _authLockStart = lockStart;
    }
    
    @XMLProperty
    public int getFailedAuthQuestionAttempts() {
        return _failedAuthQuestionAttempts;
    }

    public void setFailedAuthQuestionAttempts(int attempts) {
        _failedAuthQuestionAttempts = attempts;
    }
    
    public int getFailedLoginAttempts() {
        return _failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int _failedLoginAttempts) {
        this._failedLoginAttempts = _failedLoginAttempts;
    }

    @XMLProperty
    public Date getLastLogin()
	{
	    return _lastLogin;
	}

    public void setLastLogin(Date lastLogin)
	{
	    _lastLogin = lastLogin;
	}

    @XMLProperty
    public Date getLastRefresh()
	{
	    return _lastRefresh;
	}

    public void setLastRefresh(Date lastRefresh)
	{
	    _lastRefresh = lastRefresh;
	}

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Capability> getCapabilities() {
        return _capabilities;
    }

    public void setCapabilities(List<Capability> caps) {
        _capabilities = caps;

        // reset these so that they get recalculated
        _capabilityManager._effectiveRights = null;
        _capabilityManager._flattenedRights = null;
    }


    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Scope> getControlledScopes() {
        return _controlledScopes;
    }

    public void setControlledScopes(List<Scope> scopes) {
        _controlledScopes = scopes;
    }

    @XMLProperty
    public Boolean getControlsAssignedScope() {
        return _controlsAssignedScope;
    }

    public void setControlsAssignedScope(Boolean b) {
        _controlsAssignedScope = b;
    }
    
    @XMLProperty(mode=SerializationMode.LIST,xmlname="CertificationLinks")
	public void setCertifications(List<CertificationLink> certs) {
        _certifications = certs;
    }

    public List<CertificationLink> getCertifications() {
        return _certifications;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo-properties
    //
    // These are made to look like properties so we can represent them
    // as columns for Hibernate searches.  Potential deserialization order
    // issues if we allow the column values and the serialized attributes
    // Map get out of sync!  
    //
    // We have to provide setters for Hibernate (really?)
    // but these could be ignored.  The true home of the values is in the
    // attributes map.  Applications are not required to use the 
    // pseudo-property setters, but they have to be public for Hibernate.
    //
    //////////////////////////////////////////////////////////////////////

    private void setPseudo(String name, Object value) {
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

    public String getFirstname() {
        return Util.getString(_attributes, ATT_FIRSTNAME);
    }

    public void setFirstname(String value) {
        setPseudo(ATT_FIRSTNAME, value);
    }

    public String getLastname() {
        return Util.getString(_attributes, ATT_LASTNAME);
    }

    public void setLastname(String value) {
        setPseudo(ATT_LASTNAME, value);
    }

    /**
     * @return This identity's full name (first and last)
     */
    public String getFullName() {
        return Util.getFullname(getFirstname(), getLastname());
    }

    /**
     * The DisplayName is a configurable (via objectConfig) that
     * by default is the fullName and defaults to the name if it
     * is null. This is similar to getDisplayableName, but is
     * also persisted so it can be queried and also so that
     * a "friendly" property can be provided for displaying an identity
     * via a projection query in lists.
     *
     * Do the default calculation if:
     * 1) The identity IS a workgroup
     * OR
     * 2) The identity is not sourced 
     * AND
     * The identity is not editable (marked as not editable or
     * marked as editable with no attribute metadata)
     */
    public String getDisplayName() {
        boolean sourced = false;
        boolean editable = false;
        ObjectConfig config = getObjectConfig();
        if (config != null) {
            ObjectAttribute def = config.getObjectAttribute(ATT_DISPLAYNAME);
            if ( def != null ) {
                List<AttributeSource> sources = def.getSources();
                if ( Util.size(sources) > 0 ) {
                    sourced = true;
                }
                
                /** If the identity does not have a metadata object, it is not editable **/
                editable = def.isEditable();
            }
        }        

        /** if the attribute metadata is not null, that means that the display name was set in 
         * an LCM request and needs to be treated as editable and grab what is in the display name attribute **/
        AttributeMetaData metadata = this.getAttributeMetaData(ATT_DISPLAYNAME);        
        if(metadata!=null) {
            editable = true;
        }
        
        String displayName = Util.getString(_attributes, ATT_DISPLAYNAME);
        if ( (!sourced && !editable) || isWorkgroup() || Util.isNullOrEmpty(displayName)) {
            displayName = getFullName();
            if ( Util.getString(displayName) == null ) {
                displayName = getName();
            }
        }
        return displayName;
    }

    public void setDisplayName(String displayName) {
        setPseudo(ATT_DISPLAYNAME, displayName);
    }


    /**
     * Returns the displayName or if null the full name, or the 
     * name if there is no full name or display name. 
     * Useful in the UI.
     *
     * @return Full name, or if it's null the name
     */
    public String getDisplayableName() {
        String name = getDisplayName();
        if  ( name == null ) {
            name = getFullName();
            if (name == null)
                name = getName();
        }
        return name;
    }

    /**
     * Get the email address for this Identity object.
     * When using email address for notification purposes,
     * use ObjectUtil.getEffectiveEmails() to return addresses for
     * any workgroup members.
     *
     * @see sailpoint.api.ObjectUtil#getEffectiveEmails
     */
    public String getEmail() {

        return Util.getString(_attributes, ATT_EMAIL);
    }

    public void setEmail(String value) {
        setPseudo(ATT_EMAIL, value);
    }

    @Override
    public boolean isInactive() {

        return Util.getBoolean(_attributes, ATT_INACTIVE);
    }

    public void setInactive(boolean b) {

        // to reduce clutter only set this if true, and represent 
        // it as a String to avoid <value><Boolean>true</Boolean></value>
        if (b) {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(ATT_INACTIVE, "true");
        }
        else if (_attributes != null)
            _attributes.remove(ATT_INACTIVE);
    }

    /**
     * Return an Attributes object containing just the custom attributes.
     * @ignore
     * I dislike this but its convenient for the UI if we want to 
     * make a distinction.
     */
    public Attributes getCustomAttributes() {

        Attributes custom = new Attributes();
        if (_attributes != null) {
            List<String> keys = _attributes.getKeys();
            if (keys != null) {
                for (String key : keys) {
                    if (!isSystemAttribute(key))
                        custom.put(key, _attributes.get(key));
                }
            }
        }
         return custom;
    }

    /**
     * Return false if this is the name of a system attribute.
     */
    public boolean isSystemAttribute(String name) {

        if (_systemAttributes == null) {
            // should synchronize this, but it doesn't realy 
            // matter if we initialize it more than once
            HashMap atts = new HashMap();
            for (int i = 0 ; i < SYSTEM_ATTRIBUTES.length ; i++)
                atts.put(SYSTEM_ATTRIBUTES[i], SYSTEM_ATTRIBUTES[i]);
            _systemAttributes = atts;
        }
        return (_systemAttributes.get(name) != null);
    }

    public String getPendingRefreshWorkflow() {
        return Util.getString(_attributes, ATT_PENDING_REFRESH_WORKFLOW);
    }

    public void setPendingRefreshWorkflow(String id) {
        setPseudo(ATT_PENDING_REFRESH_WORKFLOW, id);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Attributes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The generic attribute setter that handles special system attributes,
     * extended attributes, and external attributes.
     *
     * @ignore
     * As of 6.1, simple extended attributes are only in this Map, we no
     * longer promote them to the numbered extended properties on 
     * SailPointObject, the promotion is now done dynamically as the object
     * is saved.
     *
     * For extended identity references however we still have to maintain
     * those in a set of special properties.  They are not in the map.
     */
    public void setAttribute(String name, Object value) {

        if (name.equals(ATT_MANAGER)) {
            if (value instanceof Identity) {
                setManager((Identity)value);
            } else if (value == null) {
                setManager(null);
            } else {
                // don't have a context to resolve the name, ignore
                // but probably should throw
            }
        }
        else if (name.equals(ATT_ADMINISTRATOR)) {
            if (value instanceof Identity) {
                setAdministrator((Identity)value);
            } else if (value == null) {
                setAdministrator(null);
            }
        }
        else {
            // Automatically coerce inactive to a boolean.
            if (name.equals(ATT_INACTIVE)) {
                value = Util.otob(value);
            }

            boolean addToMap = true;
            ObjectConfig config = getObjectConfig();
            if (config != null) {
                ObjectAttribute def = config.getObjectAttribute(name);
                if (def != null) {
                    int number = def.getExtendedNumber();
                    if (number > 0) {
                        if (isExtendedIdentityType(def)) {
                            // this is an extended identity reference,
                            // not in the map
                            addToMap = false;
                            if (value == null || value instanceof Identity)
                                setExtendedIdentity((Identity)value, number);
                            else {
                                if (log.isWarnEnabled())
                                    log.warn("Object of type Identity expected: " + value);
                            } 
                        } 
                    }
                }
            }

            if (addToMap) {
                if (_attributes == null)
                    _attributes = new Attributes<String,Object>();
    
                if (value == null)
                    _attributes.remove(name);
                else
                    _attributes.put(name, value);
            }
        }
    }

    /**
     * The generic attribute accessor is convenient for the UI and
     * other components that are data driven from the ObjectConfig.
     * 
     * @ignore
     * Have to special case a few of the built-in fields to make them
     * look like extended attributes.
     * 
     * UPDATE: Don't really like this, now that we have IdentityProxy
     * can't we push these special cases up there? - jsl
     */
    public Object getAttribute(String name) {

        Object value = null;

        // bug#8420 for IdentitySelector we also have to allow system
        // attributes that were exposed by sailpoint.web.policy.IdentitySelectorDTO
        // *really* need to think about IdentityProxy

        if (name.equals(ATT_MANAGER)) {
            if (_manager != null) {
                // hmm, might be nice to return the actual Identity but
                // the callers may not be prepared for that?
                // !! yes, we need to be symetrical with setAttribute
                value = _manager.getName();
            }
        }
        else if (name.equals(ATT_ADMINISTRATOR)) {
            if (_administrator != null) {
                value = _administrator.getName();
            }
        }
        else if (name.equals(ATT_BUNDLES)) {
            value = _detectedRoles;
        }
        else if (name.equals(ATT_ASSIGNED_ROLES)) {
            value = _assignedRoles;
        }
        else if (name.equals(ATT_CAPABILITIES)) {
            value = getCapabilityManager().getEffectiveCapabilities();
        }
        else if (name.equals(ATT_RIGHTS)) {
            value = getCapabilityManager().getEffectiveFlattenedRights();
        }
        else if (name.equals(ATT_WORKGROUPS)) {
            value = _workgroups;
        }
        else if(name.equals(ATT_MANAGER_STATUS)) {
            value = Util.otob(getManagerStatus());
        }
        else if(name.equals(ATT_LAST_REFRESH)) {
            value = _lastRefresh;
        }
        else if(name.equals(ATT_CORRELATED)) {
            value = _correlated;
        }
        else {

            boolean shouldLookIntoAttributes = true;
            
            ObjectAttribute attributeDefinition = getExtendedIdentityAttribute(name);
            if (attributeDefinition != null) {
                value = getExtendedIdentity(attributeDefinition.getExtendedNumber());
                shouldLookIntoAttributes = false;
            }
            
            if (shouldLookIntoAttributes) {
                if (_attributes != null)
                    value = _attributes.get(name);
    
                // Automatically coerce inactive to a boolean (should be stored as a
                // boolean already from setAttribute() but we'll be safe).  This also
                // makes nulls turn into false.
                if (name.equals(ATT_INACTIVE))
                    value = Util.otob(value);
            }
        }

        return value;
    }

    /**
     * Get the ObjectAttribute for the given name
     * @param name of the extended attribute
     * @return null if it is not the right type or it will return the 
     * object attribute which is guaranteed to be of extended identity type
     */
    public ObjectAttribute getExtendedIdentityAttribute(String name) {

        ObjectConfig config = getObjectConfig();
        if (config != null) {
            ObjectAttribute attributeDefinition = config.getObjectAttribute(name);
            if (attributeDefinition != null) {
                if (isExtendedIdentityType(attributeDefinition)) {
                    return attributeDefinition;
                }
            }
        }
        
        return null;
    }
    
    public String getStringAttribute(String name) {

        Object value = getAttribute(name);
        return (value != null) ? value.toString() : null;
    }

    /**
     * Gets an attribute as an Identity, if possible.
     * This is required since Manager and Administrator attributes are returned as String by getAttribute.
     *
     * @param name The identity attribute to return as an Identity type
     * @return Identity value, or null if the attribute is not Identity type
     */
    public Identity getIdentityAttribute(String name) {
        if (name.equals(ATT_MANAGER)) {
            return getManager();
        } else if (name.equals(ATT_ADMINISTRATOR)) {
            return getAdministrator();
        } else {
            Identity result = null;
            Object attr = getAttribute(name);

            if (attr instanceof Identity) {
                result = (Identity)attr;
            } else {
                log.debug("Identity attribute '" + name + "' is not of type Identity");
            }

            return result;
        }
    }

    public void removeAttribute(String name) {

        if (_attributes != null)
            _attributes.remove(name);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Links
    //
    //////////////////////////////////////////////////////////////////////

    public void add(Link link) {
        if (link != null) {
            if (_links == null)
                _links = new ArrayList<Link>();
            link.setIdentity(this);
            _links.add(link);
        }
    }

    public void remove(Link link) {

        if (_links != null)
            _links.remove(link);
    }

    /**
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public List<Link> getLinks(Application res) {

        return getLinks(res, null);
    }

    /**
     * If instance is null all links that match
     * the application are returned.
     *
     * @deprecated This method may not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application, String)} instead.
     */
    @Deprecated
    public List<Link> getLinks(Application res, String instance) {

        List<Link> found = null;
        if (res != null && _links != null) {
            for (Link l : _links) {
                // bug 30339: change to check that the IDs are equal.  This will keep from needing to load
                // the entire Application object to do the equality test.
                if (res.getId().equals(l.getApplication().getId())) {
                    String linkInstance = l.getInstance();
                    if (instance == null ||
                        (instance != null && instance.equals(linkInstance))) {

                        if (found == null)
                            found = new ArrayList<Link>();
                        found.add(l);
                    }
                }
            }
        }

        return found;
    }

    /**
     * Have to support this for older rules but system
     * code should try to use the other method that
     * passes an instance id.
     *
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(Application res, String identity) {

        return getLink(res, null, identity);
    }

    /**
     * Return this Links on this identity that are on the application with the
     * given ID (if non-null) or name.
     * 
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public List<Link> getLinksByAppIdOrName(String id, String name) {
        List<Link> links = new ArrayList<Link>();
        
        if (null != _links) {
            for (Link link : _links) {
                if (null != id) {
                    if (id.equals(link.getApplication().getId())) {
                        links.add(link);
                    }
                }
                else if (null != name) {
                    if (name.equals(link.getApplication().getName())) {
                        links.add(link);
                    }
                }
            }
        }
        
        return links;
    }

    /**
    * @deprecated This method might not perform well when identities have
    * large numbers of links. Use
    * {@link sailpoint.api.IdentityService#getLinks(Identity, Application)} instead.
    */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(Application res) {

        return getLink(res, null, null);
    }

    /**
     * NOTE WELL: Originally there was one
     * loop that checked for a match in both the native identity 
     * and display name. This however does not work in cases where
     * the aggregation rules or attribute mappings have changed and
     * might have stale display names that need to be refresh.
     * There must be a match against ALL native identities before
     * an attempt is made to match against display names. Otherwise
     * a stale display name might be matched with a different native identity.
     *
     * It would be preferable to ignore display name entirely but
     * since IdentityIQ has been doing this for a long time, there is probably
     * code that depends on it.
     *
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application, String)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(Application res, String instance, String identity) {
        return getLink(res, instance,identity, null);    	
    }
    
    /**
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLinks(Identity, Application, String)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(Application res, String instance, String identity, String uuid) {

        Link found = null;
        Link displayNameFound = null;
        Link uuidFound = null;
        if (res != null && _links != null) {
            for (Link l : _links) {
                if (res.equals(l.getApplication())) {
                    String linkInstance = l.getInstance();
                    if (instance == null ||
                        (instance != null && instance.equals(linkInstance))) {
                        // To be consistent with link queries, we have
                        // to use case insensntive matching.  Display name could
                        // probably do this too, but nativeIdentity is the most important
                        if (identity == null ||
                            identity.equalsIgnoreCase(l.getNativeIdentity())) {
                            found = l;
                            break;
                        }
                        else if (identity.equals(l.getDisplayName())) {
                            if (displayNameFound == null)
                                displayNameFound = l;
                        }
                        else if(uuid!=null && uuid.equalsIgnoreCase(l.getUuid())){
                            if (uuidFound == null){
                                uuidFound = l;
                            }
                        }
                    }
                }
            }
        }

        if (found == null && displayNameFound != null){
            found = displayNameFound;
        }
        else if (found == null && displayNameFound == null && uuidFound != null){
            found = uuidFound;
        }

        return found;
    }

    /**
     * Another Link searcher, this one takes a ResourceObject and
     * understands uuid.
     *
     * If a uuid exists on both sides it must match and that is the only
     * comparison done. If the uuid is missing on either side the match is done
     * by nativeIdentity or display name.
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLink(Identity, Application, ResourceObject)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(Application app, ResourceObject ro) {

        Link found = null;
        Link displayNameFound = null;

        if (app != null && ro != null && _links != null) {
            for (Link l : _links) {
                if (app.equals(l.getApplication())) {
                    String instance = ro.getInstance();
                    String linkInstance = l.getInstance();
                    if (instance == null ||
                        (instance != null && instance.equals(linkInstance))) {

                        String uuid = ro.getUuid();
                        String id = ro.getIdentity();

                        if (uuid != null && l.getUuid() != null) {
                            // uuids must match, ignore the other id
                            if (uuid.equals(l.getUuid())) {
                                found = l;
                                break;
                            }
                        }
                        else if (id != null && id.equalsIgnoreCase(l.getNativeIdentity())) {
                            found = l;
                            break;
                        }
                        else if (id != null && id.equals(l.getDisplayName())) {
                            if (displayNameFound == null)
                                displayNameFound = l;
                        }
                    }
                }
            }
        }

        if (found == null && displayNameFound != null)
            found = displayNameFound;

        return found;
    }

    /**
     * Look for a link by database identitifer.
     * Needed by the UI to correlate link id strings
     * rendered in the page back to a Link object.
     * @deprecated This method might not perform well when identities have
       large numbers of links. Use
       {@link sailpoint.api.IdentityService#getLinkById(Identity, String)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(String id) {

        Link found = null;
        if (id != null && _links != null) {
            for (Link l : _links) {
                if (id.equals(l.getId())) {
                    found = l;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Convenience method for Connectors, locate the Link that
     * corresponds to an AccountRequest from a ProvisioningPlan.
     * Basically the same as getLink(app,instance,id) but
     * kept separate so the Application does not need to be fetched
     * and there is no need to support display name 
     * matching here.
     * @deprecated This method might not perform well when identities have
     * large numbers of links. Use
     * {@link sailpoint.api.IdentityService#getLink(Identity, Application, String, String)} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Link getLink(ProvisioningPlan.AccountRequest req) {

        Link found = null;
        Link displayNameFound = null;

        if (req != null && _links != null) {

            String appname = req.getApplication();
            String instance = req.getInstance();
            String identity = req.getNativeIdentity();

            for (Link l : _links) {
                if (appname.equals(l.getApplication().getName())) {

                    String linkInstance = l.getInstance();
                    if (instance == null ||
                        (instance != null && instance.equals(linkInstance))) {

                        if (identity == null ||
                            identity.equalsIgnoreCase(l.getNativeIdentity())) {
                            found = l;
                            break;
                        }
                        else if (identity.equals(l.getDisplayName())) {
                            if (displayNameFound == null)
                                displayNameFound = l;
                        }
                    }
                }
            }
        }

        if (found == null && displayNameFound != null)
            found = displayNameFound;

        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Composite Links
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the Link for which a Link with the given application and
     * nativeIdentity is a composite component. For example, if this Identity
     * has a link on "HR App" (a composite application that uses "DB" application) and a link
     * on "DB" application (the composite component) you could do the following:
     * 
     * <code>
     *   Link link = id.getOwningCompositeLink(dbApp, dbNativeId);
     * </code>
     * 
     * This would return the "HR App" link. This returns null if there is
     * no link for the given application and nativeIdentity OR there is a link but it
     * is not a component for another link.
     * 
     * @param  app             The application of the component link.
     * @param  instance        The instance of the component link.                        
     * @param  nativeIdentity  The native identity of the component link.
     * 
     * @return The Link for which a Link with the given application and
     *         nativeIdentity is a composite component, or null if there is no
     *         link for the given application/nativeIdentity OR it is not part of a
     *         composite link on this identity.
     */
    public Link getOwningCompositeLink(Application app, String instance, String nativeIdentity) {

        return getOwningCompositeLink(this.getLink(app, instance, nativeIdentity));
    }

    /**
     * Older signature without instance, have to support this
     * for backward compatibility with rules.
     */
    public Link getOwningCompositeLink(Application app, String nativeIdentity) {

        return getOwningCompositeLink(this.getLink(app, null, nativeIdentity));
    }

    /**
     * Return the composite link that owns the given link.
     * 
     * @see #getOwningCompositeLink(Application, String)
     */
    public Link getOwningCompositeLink(Link componentLink) {
    
        Link composite = null;

        if (null != componentLink) {
            for (Link link : _links) {
                if ((null != link.getComponentIds()) &&
                    (link.getComponentIds().indexOf(componentLink.getId()) > -1)) {

                    composite = link;
                    break;
                }
            }
        }

        return composite;
    }

    /**
     * Gets the list of composite links that "own" the given link
     * @param app  Application of component link
     * @param instance Instance of component link
     * @param nativeIdentity nativeIdentity of component link
     * @return List of composite links, or null if none exist
     */
    public List<Link> getOwningCompositeLinks(Application app, String instance, String nativeIdentity) {

        return getOwningCompositeLinks(this.getLink(app, instance, nativeIdentity));
    }

    /**
     * Gets the list of composite links that "own" the given link
     * @param componentLink  Component link
     * @return  List of composite links, or null if none exist
     */
    public List<Link> getOwningCompositeLinks(Link componentLink) {
        List<Link> composites = null;

        if (null != componentLink) {
            for (Link link : _links) {
                if ((null != link.getComponentIds()) &&
                        (link.getComponentIds().indexOf(componentLink.getId()) > -1)) {

                    if (composites == null) {
                        composites = new ArrayList<Link>();
                    }
                    composites.add(link);
                }
            }
        }

        return composites;
    }
    
    /**
     * Convert the attributes and permissions in all links into an
     * EntitlementCollection,
     * 
     * @param  onlyEntitlementAttrs  Whether to only include entitlement
     *                               attributes in the entitlement collection.
     *
     * @return An EntitlementCollection with the permissions and attributes from
     *         the links on this identity.
     */
    @SuppressWarnings("unchecked")
    public EntitlementCollection convertLinks(boolean onlyEntitlementAttrs) {
    
        EntitlementCollection collection = new EntitlementCollection();

        if (null != _links) {
            for (Link link : _links) {
                // jsl - when run against the unittest database we 
                // sometimes encounter links with no Applications, ignore those

                if (null != link.getAttributes() &&
                    null != link.getApplication()) {

                    // Should probably peel the permissions out if we're not
                    // just using entitlement attributes.
                    Attributes<String,Object> attrs =
                        (onlyEntitlementAttrs) ? link.getEntitlementAttributes()
                                               : link.getAttributes();

                    // note that we use the constructor that accepts an Application
                    // so we can avoid database hits resolving the Application later
                    EntitlementSnapshot es =
                        new EntitlementSnapshot(link.getApplication(),
                                                link.getInstance(),
                                                link.getNativeIdentity(),
                                                link.getDisplayableName(),
                                                link.getPermissions(),
                                                attrs);
                    collection.addEntitlements(es);
                }
            }
        }

        return collection;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assigned Roles
    //
    //////////////////////////////////////////////////////////////////////

    public void addAssignedRole(Bundle role) {
        if (role != null) {
            if (_assignedRoles == null)
                _assignedRoles = new ArrayList<Bundle>();
            if (!_assignedRoles.contains(role))
                _assignedRoles.add(role);
        }
    }

    public void removeAssignedRole(Bundle role) {
        if (_assignedRoles != null)
            _assignedRoles.remove(role);
    }

    /**
     * Look for a assigned role by database identifier.
     * Needed by the UI to correlate assigned role id strings
     * rendered in the page back to a Role object.
     * @param id Role ID
     * @return Assigned role, if found, otherwise null
     */
    public Bundle getAssignedRole(String id) {

        Bundle found = null;
        if (id != null && _assignedRoles != null) {
            for (Bundle r : _assignedRoles) {
                if (r != null && id.equals(r.getId())) {
                    found = r;
                    break;
                }
            }
        }
        return found;
    }
    
    /**
     * Returns true if the identity has the given role. If
     * checkInheritedRoles == true, the inheritance of each
     * role will be checked as well.
     *
     * @param role Role to find
     * @param checkInheritedRoles True if inherited roles as well as roles which
     * are directly assigned or detected.
     */
    public boolean hasRole(Bundle role, boolean checkInheritedRoles){

        boolean hasRole;

        List<Bundle> assignedAndDetectedRoles = new ArrayList<Bundle>();
        if (_detectedRoles != null) {
            assignedAndDetectedRoles.addAll(_detectedRoles);
        }

        if (_assignedRoles != null) {
            assignedAndDetectedRoles.addAll(_assignedRoles);
        }

        hasRole = assignedAndDetectedRoles.contains(role);

        if (!hasRole && checkInheritedRoles) {
            for (Bundle assignedOrDetected : assignedAndDetectedRoles){
                if (!hasRole) {
                    Collection<Bundle> inheritance = assignedOrDetected.getFlattenedInheritance(); 
                    if (!Util.isEmpty(inheritance)) {
                        if (inheritance.contains(role)) {
                            hasRole = true;
                        }
                    }
                }
            }
        }

        // Bug 27457 - If the role is required or permitted and all its requirements 
        // are assigned or detected then this role is considered had.
        // This situation doesn't come up often, but arises from situations in which
        // in which required/permitted roles require roles.
        // Note:  Presumably the first check (role being required or permitted) is performed
        // by the caller, so we're not duplicating it here
        if (!hasRole) {
            Collection<Bundle> requirements = role.getFlattenedRequirements();
            if (!Util.isEmpty(requirements)) {
                for (Bundle requirement : requirements) {
                    if (assignedAndDetectedRoles.contains(requirement)) {
                        hasRole = true;
                    } else  {
                        // If the requirement has no profiles and is not a leaf role 
                        // then we can safely ignore it.  However, if it has profiles 
                        // and is not being detected, then we have to conclude that it 
                        // hasn't been provisioned.  Every role here is a non-leaf (because the
                        // "role" parameter itself is the leaf), so we only have
                        // to check whether or not it has profiles.
                        if (!Util.isEmpty(requirement.getProfiles())) {
                            hasRole = false;
                            break;
                        }
                    }
                }
            }
        }

        return hasRole;
    }

    /**
     * Recalculate the assigned role summary string.
     */
    public void updateAssignedRoleSummary() {

        _assignedRoleSummary = getRoleSummary(_assignedRoles);
    }

    /**
     * Calculate a role summary string from a list of roles.
     * 
     * @ignore
     * This is used for both updateAssignedRoleSummary and
     * updateDetectedRoleSummary.
     *
     */
    public String getRoleSummary(List<Bundle> roles) {

        String summary = null;

        if (roles != null && !roles.isEmpty()) {
            StringBuffer buf = new StringBuffer();
            for (Bundle role : roles) {
                /*
                 * bug 29511
                 * Assuming two bytes will prevent this error from happening the vast majority
                 * of the time across all customers. If the error is still happening, they can
                 * increase the column size for assigned_role_summary and bundle_summary.
                 * Fixing multiplying the current length by 2
                 */
                int curlen = buf.toString().length() * 2;
                int newlen = role.getDisplayableName().length() * 2;

                if (curlen + newlen < MAX_BUNDLE_SUMMARY) {
                    if (curlen > 0)
                        buf.append(", ");
                    buf.append(role.getDisplayableName());
                }
                else {
                    // add elipses if we can
                    if (curlen + 6 < MAX_BUNDLE_SUMMARY)
                        buf.append("...");
                    break;
                }
            }

            summary = buf.toString();
        }

        return summary;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Detected Roles
    //
    //////////////////////////////////////////////////////////////////////

    public void addDetectedRole(Bundle role) {
        if (role != null) {
            if (_detectedRoles == null)
                _detectedRoles = new ArrayList<Bundle>();
            _detectedRoles.add(role);
        }
    }

    public void removeDetectedRole(Bundle role) {
        if (_detectedRoles != null)
            _detectedRoles.remove(role);
    }

    // legacy name
    public void add(Bundle role) {
        addDetectedRole(role);
    }

    // legacy name
    public void remove(Bundle role) {
        removeDetectedRole(role);
    }

    /**
     * Look for a detected role by database identifier.
     */
    public Bundle getDetectedRole(String id) {

        Bundle found = null;
        if (id != null && _detectedRoles != null) {
            for (Bundle r : _detectedRoles) {
                if (r != null) {
                    if (id.equals(r.getId())) {
                        found = r;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Legacy name for getDetectedRole.
     * @deprecated - use {@link #getDetectedRole(String)}
     */
    @Deprecated
    public Bundle getBundle(String id) {
        return getDetectedRole(id);
    }

    public Bundle getDetectedRoleByName(String name) {

        Bundle found = null;
        if (name != null && _detectedRoles != null) {
            for (Bundle r : _detectedRoles) {
                if (r != null) {
                    if (name.equals(r.getName())) {
                        found = r;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * @deprecated - use {@link #getDetectedRoleByName(String)}
     */
    @Deprecated
    public Bundle getBundleByName(String name) {
        return getDetectedRoleByName(name);
    }

    /**
     * Get the bundles on this Identity that reference the given Application.
     * If the Application is null, all bundles for this Identity are returned.
     */
    public List<Bundle> getBundles(Application app) {
        if (null == app)
            return _detectedRoles;

        List<Bundle> bundles = new ArrayList<Bundle>();
        if (null != _detectedRoles) {
            for (Bundle bundle : _detectedRoles) {
                if (bundle.referencesApplication(app))
                    bundles.add(bundle);
            }
        }
        return bundles;
    }

    /**
     * Get the bundles on this Identity that reference any of the given
     * Applications. If the Applications list is null or empty, all bundles for
     * this Identity are returned.
     */
    public List<Bundle> getBundles(List<Application> apps) {

        if ((null == apps) || apps.isEmpty())
            return _detectedRoles;

        List<Bundle> bundles = new ArrayList<Bundle>();
        if (null != _detectedRoles) {
            for (Bundle bundle : _detectedRoles) {
                if (bundle.referencesAnyApplication(apps)) {
                    bundles.add(bundle);
                }
            }
        }
        return bundles;
    }

    /**
     * Refresh the detected role summary.
     */
    public void updateDetectedRoleSummary() {

        _bundleSummary = getRoleSummary(_detectedRoles);
    }

    /**
     * Refresh both the assigned and detected role bundle summaries.
     * 
     * @ignore
     * Considered calling this automatically every time the object is saved,
     * but this potentially fetches all the Bundles which we may not always
     * want.   Assuming that the EntitlementCorrelator will update it 
     * as will the UI whenever the bundle list changes.
     */
    public void updateBundleSummary() {

        updateAssignedRoleSummary();
        updateDetectedRoleSummary();
    }
    
    
    /**
     * If a RoleMetadata corresponding to the specified RoleMetadata already exists this 
     * method updates it to match. Otherwise this method adds the specified RoleMetadata 
     * object to the identity.  
     * @param roleMetadata RoleMetadata object to add or update
     */
    public void addRoleMetadata(RoleMetadata roleMetadata) {
        if (roleMetadata != null) {
            if (_roleMetadatas == null) {
                _roleMetadatas = new ArrayList<RoleMetadata>();
            }
            
            RoleMetadata existingMetadata = getRoleMetadata(roleMetadata.getName());
            if (existingMetadata == null) {
                _roleMetadatas.add(roleMetadata);
            } else {
                existingMetadata.setDescription(roleMetadata.getDescription());
                existingMetadata.setAssigned(roleMetadata.isAssigned());
                existingMetadata.setMissingRequired(roleMetadata.isMissingRequired());
                existingMetadata.setAdditionalEntitlements(roleMetadata.isAdditionalEntitlements());
                existingMetadata.setDetected(roleMetadata.isDetected());
                existingMetadata.setDetectedException(roleMetadata.isDetectedException());
            }
        }
    }
    
    private RoleMetadata getRoleMetadata(String name) {
        RoleMetadata existingMetadata = null;
        if (name != null && _roleMetadatas != null && !_roleMetadatas.isEmpty()) {
            for (RoleMetadata metadata : _roleMetadatas) {
                if (name.equals(metadata.getName())) {
                    existingMetadata = metadata;
                }
            }
        }
        return existingMetadata;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Exceptions
    //
    //////////////////////////////////////////////////////////////////////

    public void add(EntitlementGroup e) {
        if (e != null) {
            if (_exceptions == null)
                _exceptions = new ArrayList<EntitlementGroup>();
            _exceptions.add(e);
        }
    }

    /**
     * Get the exceptions on this Identity that reference the given
     * Applications. If the Applications list is null or empty, all exceptions
     * for this Identity are returned.
     */
    public List<EntitlementGroup> getExceptions(List<Application> apps) {
        if ((null == apps) || apps.isEmpty())
            return _exceptions;

        List<EntitlementGroup> exceptions = new ArrayList<EntitlementGroup>();
        if (null != _exceptions) {
            for (EntitlementGroup group : _exceptions) {
                if (apps.contains(group.getApplication()))
                    exceptions.add(group);
            }
        }
        return exceptions;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certifications
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add an certification reference to the identity.
     */
    public void add(CertificationLink link) {
        if (link != null) {
            if (_certifications == null)
                _certifications = new ArrayList<CertificationLink>();
            _certifications.add(link);
        }
    }

    public void add(Certification cert, CertificationEntity entity) {

        add(new CertificationLink(cert, entity));
    }
    
    public void remove(CertificationLink link) {
        if (_certifications != null)
            _certifications.remove(link);
    }

    /**
     * Remove all certifications of a given type. For AppOwner certifications,
     * only remove certifications for the same app
     */
    private void filterCertifications(Certification cert) {
        Certification.Type type = cert.getType();
        String appId = cert.getApplicationId();
        
        if (_certifications != null) {
            Iterator<CertificationLink> it = _certifications.listIterator();
            while (it.hasNext()) {
                CertificationLink l = it.next();
                
                if (l.getType() == Certification.Type.ApplicationOwner && l.getType() == type) {
                    if (appId.equals(l.getApplicationId())) {
                        it.remove();
                    }
                } else if (l.getType() == type) {
                    it.remove();                    
                }
            }
        }
    }

    /**
     * Add an certification reference to the identity.
     * Automatically remove prior certifications of the same type.
     * 
     * @ignore
     * TODO: We may want more policy involved here, maybe
     * a "history limit" so we could save the last N certifications?
     */
    public void addLatestCertification(Certification cert, CertificationEntity entity) {

        if (cert != null) {
            filterCertifications(cert);
            add(new CertificationLink(cert, entity));
        }
    }

    /**
     * Look for the most recent certification of a given type.
     */
    public CertificationLink getLatestCertification(Certification.Type type) {

        CertificationLink link = null;
        if (_certifications != null && type != null) {
            for (CertificationLink l : _certifications) {
                // be careful, we've seen null types before
                if (type.equals(l.getType())) {
                    if (link == null)
                        link = l;
                    else {
                        Date d = l.getCompleted();
                        // If there is no completion date, should we assume it
                        // is "most current" or "infinately old"?  It is more 
                        // conveinent for the unit tests to treat it as new, 
                        // but this feels dangerous.  Use it only if there
                        // is nothing else.
                        if (d != null) {
                            Date last = link.getCompleted();
                            if (last == null || d.compareTo(last) > 0)
                                link = l;
                        }
                    }
                }
            }
        }
        return link;
    }
    
    /**
     * Look for the most recent app owner certifications that come after the specified given date.
     * Only one certification per application will be returned
     * @param recentDate Date to limit search 
     * @return the most recent app owner certifications that come after the specified given date.
     * Only one certification per application will be returned
     */
    public List<CertificationLink> getRecentAppOwnerCertifications(Date recentDate) throws GeneralException {
        SortedCertLinkList certLinks = new SortedCertLinkList();
        
        if (_certifications != null) {
            for (CertificationLink l : _certifications) {
                if (l.getType().equals(Certification.Type.ApplicationOwner) &&
                    (null != l.getCompleted()) &&
                    (Util.nullSafeCompareTo(l.getCompleted(), recentDate) > 0)) {
                    certLinks.add(l);
                }
            }
        }
        
        return certLinks.asList();
    }

    /**
     * Look for the most recent certification.
     */
    public CertificationLink getLatestCertification() {

        CertificationLink link = null;
        if (_certifications != null) {
            for (CertificationLink l : _certifications) {
                if (link == null)
                    link = l;
                else {
                    Date d = l.getCompleted();
                    // If there is no completion date, should we assume it
                    // is "most current" or "infinately old"?  It is more 
                    // conveinent for the unit tests to treat it as new, 
                    // but this feels dangerous.  Use it only if there
                    // is nothing else.
                    if (d != null) {
                        Date last = link.getCompleted();
                        if (last == null || d.compareTo(last) > 0)
                            link = l;
                    }
                }
            }
        }
        return link;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Certification Decision History
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the list of most recent certification decisions.
     *
     * @deprecated this method might perform poorly and is currently only used in tests.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public List<IdentityHistoryItem> getCertificationDecisions(SailPointContext context) throws GeneralException{
        IdentityHistoryService svc = new IdentityHistoryService(context);
        return svc.getDecisions(getId());
    }
    
    /**
     * Gets the last decision made for the policy on the given violation
     *
     * @return Most recent decision for the given violation, 
     * or null if no decisions exist
     */
    public IdentityHistoryItem getLastViolationDecision(SailPointContext context, PolicyViolation violation)
        throws GeneralException {
        if (violation == null || violation.getIdentity() == null)
            return null;

        IdentityHistoryService svc = new IdentityHistoryService(context);
        return svc.getLastViolationDecision(violation.getIdentity().getId(), violation);
    }


    public void addCertificationDecision(SailPointContext context, CertificationItem certItem) throws GeneralException{

        if (certItem == null)
            return;

        addCertificationDecision(context, certItem, new CertifiableDescriptor(certItem));
    }

    public void addCertificationDecision(SailPointContext context,
                                         CertificationItem certItem, CertifiableDescriptor certDesc) throws GeneralException{

        if (certItem == null || certDesc == null)
            return;

        IdentityHistoryItem historyItem =
                new IdentityHistoryItem(this, IdentityHistoryItem.Type.Decision, certItem, certDesc);
        context.saveObject(historyItem);
    }

    public void addCertificationDecision(SailPointContext context, PolicyViolation violation,
                                         CertificationAction action) throws GeneralException{

        if (violation == null || action == null)
            return;

        IdentityHistoryItem historyItem =
                new IdentityHistoryItem(IdentityHistoryItem.Type.Decision, violation, action);
        context.saveObject(historyItem);
    }

    public void addEntitlementComment(SailPointContext context, CertificationItem certItem, String actor,
                                      String comment) throws GeneralException{

        if (certItem == null || actor == null || comment == null)
            return;

        IdentityHistoryItem historyItem =
                new IdentityHistoryItem(this, IdentityHistoryItem.Type.Comment, certItem);
        historyItem.setActor(actor);
        historyItem.setComments(comment);
        context.saveObject(historyItem);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Create Processing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enumeration of values that specify which creation processing step is
     * of interest. This is used for storing and retrieving the need for
     * creation processing independently for different functions.
     */
    public static enum CreateProcessingStep {
        @Deprecated
        CertRefresh,
        Trigger
    }
    
    /**
     * Return true if the given identity still needs creation processing for the
     * requested create processing step. Creating processing involves things
     * that might need to occur when the identity is created (refreshing certs,
     * processing triggers) but might not happen immediately when the identity is
     * created since the aggregation task might not have everything needs (for example -
     * entitlement correlation, etc...)
     */
    @SuppressWarnings("unchecked")
    public boolean needsCreateProcessing(CreateProcessingStep step) {

        boolean needs = false;

        switch (step) {
        case Trigger:
            Map<String,Object> snapshots =
                (Map<String,Object>) getAttribute(ATT_TRIGGER_SNAPSHOTS);
            if (null != snapshots) {
                needs = Util.otob(snapshots.get(ATT_NEEDS_CREATE_PROCESSING));
            }
            break;
        default:
            if (log.isWarnEnabled())
                log.warn("Unknown create processing step: " + step);
        }
        
        return needs;
    }

    /**
     * Set that the given identity either does or does not need creation
     * processing for the given create processing step.
     */
    @SuppressWarnings("unchecked")
    public void setNeedsCreateProcessing(boolean needs, CreateProcessingStep step) {

        Map<String,Object> attrs = null;

        switch (step) {
        case Trigger:
            attrs = (Map<String,Object>) getAttribute(ATT_TRIGGER_SNAPSHOTS);
            if ((null == attrs) && needs) {
                attrs = new HashMap<String,Object>();
                setAttribute(ATT_TRIGGER_SNAPSHOTS, attrs);
            }
            break;
        default:
            if (log.isWarnEnabled())
                log.warn("Unknown create processing step: " + step); 
        }

        // If we don't need it, just remove it from the attribute map.
        if (!needs) {
            if (null != attrs) {
                attrs.remove(ATT_NEEDS_CREATE_PROCESSING);
            }
        }
        else {
            attrs.put(ATT_NEEDS_CREATE_PROCESSING, needs);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Capabilities
    //
    //////////////////////////////////////////////////////////////////////

    public void add(Capability cap) {
    	if (cap != null) {
    	    if (_capabilities == null)
                _capabilities = new ArrayList<Capability>();
    	    _capabilities.add(cap);
    	}
    }

    public void remove(Capability cap) {
        if (cap != null && _capabilities != null)
            _capabilities.remove(cap);
    }
    
    /**
     * @deprecated
     * This is there so that previous rules written do not break.
     * Use the {@link CapabilityManager#hasCapability(String)} method from
     * {@link #getCapabilityManager()} instead.
     */
    @Deprecated
    public boolean hasCapability(String capabilityName) {
        return getCapabilityManager().hasCapability(capabilityName);
    }

    /**
     * @deprecated
     * This is there so that rules written do not break.
     * Use {@link CapabilityManager#getEffectiveCapabilities()} method from 
     * {@link #getCapabilityManager()} instead
     */
    @Deprecated
    public List<Capability> getEffectiveCapabilities() {
        return getCapabilityManager().getEffectiveCapabilities();
    }
    
    /**
     * @deprecated
     * This is there so that rules written do not break.
     * Use {@link CapabilityManager#getEffectiveFlattenedRights()} method from 
     * {@link #getCapabilityManager()} instead
     */
    @Deprecated
    public Collection<String> getEffectiveFlattenedRights() {
        return getCapabilityManager().getEffectiveFlattenedRights();
    }

    /**
     * @deprecated
     * This is there so that rules written do not break.
     * Use {@link CapabilityManager#getEffectiveFlattenedRights()} method from 
     * {@link #getCapabilityManager()} instead
     */
    @Deprecated
    public Collection<String> getFlattenedRights() {
        return getCapabilityManager().getEffectiveFlattenedRights();
    }
    
    /**
     * @deprecated
     * This is there so that rules written do not break.
     * Use {@link CapabilityManager#createCapabilitiesAttributes()} method from 
     * {@link #getCapabilityManager()} instead
     */
    @Deprecated
    public Attributes<String,Object> createCapabilitiesAttributes() {
        return getCapabilityManager().createCapabilitiesAttributes();
    }
    
    public CapabilityManager getCapabilityManager(){
        return _capabilityManager;
    }
    
    public class CapabilityManager {

        /**
         * A calculated set of all the names of all rights assigned by the list of
         * capabilities.
         */
        private transient Collection<String> _flattenedRights;
        
        /**
         * A calculated set of all the names of all rights assigned by the list of
         * capabilities including directly assigned capabilities and 
         * capabilities granted through workgroup membership;
         */
        private transient Collection<String> _effectiveRights;
        
        /**
         * This only checks the identity capabilities not workgroup
         */
        public boolean hasNativeCapability(String capabilityName) {
        
            return hasCapability(getCapabilities(), capabilityName);
        }
        
        public boolean hasCapability(String capabilityName) {

            return hasCapability(getEffectiveCapabilities(), capabilityName);
        }

        private boolean hasCapability(List<Capability> capabilities, String capabilityName) {
            
            if ((null != capabilityName) && (null != capabilities)) {
                for (Capability cap : capabilities) {
                    if (capabilityName.equals(cap.getName())) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        public boolean hasRight(String rightName) {
            if(getEffectiveFlattenedRights().contains(rightName))
                return true;
            else
                return false;
        }
        
        /**
         * Get a flattened collection of the names of rights this user has access
         * to via his capabilities.
         */
        private Collection<String> getFlattenedRights() {
            if (null == _flattenedRights) {
                _flattenedRights = new HashSet<String>();
                if (null != _capabilities) {
                    for (Capability cap : _capabilities) {
                        for (SPRight right : cap.getAllRights()) {
                            _flattenedRights.add(right.getName());
                        }
                    }
                }
            }
            return _flattenedRights;
        }
        
        /**
         * Get a flatten list of rights from all capabilities
         * including directly assigned capabilities and 
         * capabilities granted through workgroup membership.
         */
        public Collection<String> getEffectiveFlattenedRights() {
            if ( _effectiveRights == null ) {
                _effectiveRights = new HashSet<String>();
                Collection<String> directRights = getCapabilityManager().getFlattenedRights();
                if ( Util.size(directRights) > 0 ) 
                    _effectiveRights.addAll(directRights);
                List<Identity> workgroups = getWorkgroups();
                if ( Util.size(workgroups) > 0 ) {
                    for ( Identity group : workgroups ) {
                        Collection<String> indirectRights = group.getCapabilityManager().getFlattenedRights();
                        _effectiveRights.addAll(indirectRights);
                    } 
                }
            }
            return _effectiveRights;
        }

        /**
         * Get a flat list of capabilities that includes
         * the directly assigned capabilities AND the
         * all capabilities granted through workgroup
         * membership.
         */
        public List<Capability> getEffectiveCapabilities() {
            List<Capability> effective = null;
            if ( getCapabilities() == null ) {
                effective = new ArrayList<Capability>();
            } else {
                effective = new ArrayList<Capability>(getCapabilities());
            }
            List<Identity> workgroups = getWorkgroups();
            if ( Util.size(workgroups) > 0 ) {
                for ( Identity group : workgroups ) {
                    Collection<Capability> indirect = group.getCapabilities();
                    effective.addAll(indirect);
                } 
            }
            return effective;
        }    
        
        /**
         * Return a non-null Attributes map that contains the localized names of
         * this Identity's capabilities as if they were "attributes" on the
         * identity.
         */
        public Attributes<String,Object> createCapabilitiesAttributes() {
            
            Attributes<String,Object> attrs = new Attributes<String,Object>();
            if (null != _capabilities) {
                List<String> capNames = new ArrayList<String>();
                for (Capability cap : _capabilities) {
                    // Use en_US because we'll need to reverse translate this when
                    // auto-remediating or scanning for remediations.  Alternatively,
                    // we could store the capability name but this doesn't look great
                    // in the UI.  Think about just using the name here.  This is
                    // fragile if the message catalog changes.
                    capNames.add(cap.getDisplayableName(Locale.US));
                }
                attrs.put(Certification.IIQ_ATTR_CAPABILITIES, capNames);
            }
            return attrs;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Controlled Scopes
    //
    //////////////////////////////////////////////////////////////////////

    public void addControlledScope(Scope scope) {
        if (scope != null) {
            if (_controlledScopes == null)
                _controlledScopes = new ArrayList<Scope>();
            _controlledScopes.add(scope);
        }
    }

    public void removeControlledScope(Scope scope) {
        if (scope != null && _controlledScopes != null)
            _controlledScopes.remove(scope);
    }

    /**
     * Return the effective list of controlled scopes for this identity, which
     * will include all controlled scopes as well as the assigned scope (if
     * getControlsAssignedScope(Resolver) returns true).
     * This method also will take into account any scopes that are assigned
     * due to workgroup membership.
     */
    public List<Scope> getEffectiveControlledScopes(Configuration sysConfig)
        throws GeneralException {

        List<Scope> scopes = new ArrayList<Scope>();

        if (null != _controlledScopes) {
            scopes.addAll(_controlledScopes);
        }

        if (getControlsAssignedScope(sysConfig) && (null != _assignedScope) 
                &&  (!scopes.contains(_assignedScope))) {
            scopes.add(_assignedScope);
        }

        List<Identity> groups = ( !this.isWorkgroup() ) ? this.getWorkgroups() : null;
        if ( Util.size(groups) > 0 ) {
             for ( Identity wg : groups ) {
                 List<Scope> wgScopes = wg.getEffectiveControlledScopes(sysConfig);
                 if ( Util.size(wgScopes) > 0 ) {
                     for ( Scope wgScope : wgScopes ) {
                         if ( !scopes.contains(wgScope) )
                             scopes.add(wgScope);
                     }
                 }
             }
        }
        return scopes;
    }
    
    /**
     * Helper to return whether this identity controls their assigned scope,
     * first by looking at the preference on the identity then at the system
     * configuration.
     */
    public boolean getControlsAssignedScope(Configuration sysConfig)
        throws GeneralException {
        
        if (null != _controlsAssignedScope) {
            return _controlsAssignedScope;
        }

        return sysConfig.getBoolean(Configuration.IDENTITY_CONTROLS_ASSIGNED_SCOPE, false);
    }

    /**
     * Return a non-null Attributes map that contains the localized display name
     * paths  this Identity's effective controlled scopes as if they were
     * "attributes" on the identity.
     */
    public Attributes<String,Object> createEffectiveControlledScopesAttributes(Configuration sysConfig)
        throws GeneralException {
        
        Attributes<String,Object> attrs = new Attributes<String,Object>();

        List<Scope> scopes = getEffectiveControlledScopes(sysConfig);
        if ((null != scopes) && !scopes.isEmpty()) {
            List<String> scopeNames = new ArrayList<String>();
            for (Scope scope : scopes) {
                // Use the displayablePath so that we have a unique name.
                // Display names may not be unique.
                scopeNames.add(scope.getDisplayablePath());
            }
            attrs.put(Certification.IIQ_ATTR_SCOPES, scopeNames);
        }

        return attrs;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Preferences
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Preferences are system-defined attributes that are serialized
     * to XML since they are not searchable. This is not intended
     * for custom attributes, those go in the <code>attributes</code> map.
     */
    @XMLProperty
    public Map<String, Object> getPreferences() {
        return _preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this._preferences = preferences;
    }

    public void setPreference(String name, Object value) {

        if (_preferences == null)
            _preferences = new HashMap<String,Object>();

        if (value != null)
            _preferences.put(name, value);
        else
            _preferences.remove(name);
    }

    public Object getPreference(String name) {

        Object value = null;

        if (_preferences != null)
            value = _preferences.get(name);

        return value;
    }

    public Date getUseBy() {
        return Util.getDate(getPreference(PRF_USE_BY_DATE));
    }
    
    public void setUseBy(Date d) {
        if (d == null)
            setPreference(PRF_USE_BY_DATE, null);
        else
            setPreference(PRF_USE_BY_DATE, Util.ltoa(d.getTime()));
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="UIPreferencesRef")
    public UIPreferences getUIPreferences() {
        return _uiPreferences;
    }

    public void setUIPreferences(UIPreferences prefs) {
        _uiPreferences = prefs;
    }

    public void setUIPreference(String name, Object value) {
        if (_uiPreferences == null) {
            _uiPreferences = new UIPreferences();
            _uiPreferences.setOwner(this);
        }
        _uiPreferences.put(name, value);
    }

    public Object getUIPreference(String name) {

        Object value = null;
        if (_uiPreferences != null)
            value = _uiPreferences.get(name);

        return value;
    }

    /**
     * Return the requested preferences, looking in the given system defaults if
     * it is not found.
     */
    public Object getUIPreference(String name, Attributes<String,Object> defaults) {
        return getUIPreference(name, defaults, null);
    }

    /**
     * Return the requested preferences, looking in the given system defaults 
     * with the given key if it is not found.
     */
    public Object getUIPreference(String name, Attributes<String,Object> defaults,
                                  String defaultsKey) {
        Object val = getUIPreference(name);
        if ((null == val) && (null != defaults)) {
            String key = (null != defaultsKey) ? defaultsKey : name;
            val = defaults.get(key);
        }
        return val;
    }

    //
    // UI Preference accessors
    //

    public List<SearchItem> getSavedSearches() {
        return (List<SearchItem>)getUIPreference(UIPreferences.PRF_ADV_SEARCH_ITEMS);
    }

    public void setSavedSearches(List<SearchItem> items) {
        setUIPreference(UIPreferences.PRF_ADV_SEARCH_ITEMS, items);
    }

    public List<GridState> getGridStates() {
        return (List<GridState>)getUIPreference(UIPreferences.PRF_GRID_STATES);
    }

    public void setGridStates(List<GridState> states) {
        setUIPreference(UIPreferences.PRF_GRID_STATES, states);
    }
    
    public List<ExtState> getExtStates() {
        return (List<ExtState>)getUIPreference(UIPreferences.PRF_EXT_STATES);
    }

    public void setExtStates(List<ExtState> states) {
        setUIPreference(UIPreferences.PRF_EXT_STATES, states);
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // AttributeAssignments used like Role assignments to make the values
    // sticky so they can be reprovisioned when they've been assigned
    // through LCM or deemed assigned.
    //
    ///////////////////////////////////////////////////////////////////////////
    
    public void validateAttributeAssignments(Resolver resolver)
        throws GeneralException {
        
        List<AttributeAssignment> current = getAttributeAssignments();        
        if ( current != null ) {
            Iterator<AttributeAssignment> it = current.iterator();
            while  (it.hasNext()) {
                AttributeAssignment ass = it.next();
                if ( ass == null || !ass.isValid(resolver) ) 
                    it.remove();
            }
        }
        setAttributeAssignments(current);
    }
    
    /**
     * Return the identity's current list of AttributeAssignments.
     */
    public List<AttributeAssignment> getAttributeAssignments() {
        List<AttributeAssignment> assignments = null;
        Object o = getPreference(PRF_ATTRIBUTE_ASSIGNMENTS);
        if (o instanceof List)
            assignments = (List<AttributeAssignment>)o;
        return assignments;
    }

    public AttributeAssignment getAttributeAssignment(String appName, String nativeId, String attribute, String value,
                                                      String instance, String assignmentId) {
        AttributeAssignment assign = null;
        List<AttributeAssignment> assignments = getAttributeAssignments();
        for (AttributeAssignment assignment : Util.safeIterable(assignments)) {
            if (Util.nullSafeCompareTo(appName, assignment.getApplicationName()) == 0 &&
                    Util.nullSafeCompareTo(value, assignment.getStringValue()) == 0 &&
                    Util.nullSafeCompareTo(instance, assignment.getInstance()) == 0 &&
                    Util.nullSafeCompareTo(attribute, assignment.getName()) == 0) {

                    //Try assignmentId first.
                    if (Util.nullSafeEq(assignmentId, assignment.getAssignmentId())) {
                        return assignment;
                    } else if (Util.nullSafeEq(nativeId, assignment.getNativeIdentity())) {
                        return assignment;
                    }
            }
        }

        return assign;
    }
    
    /**
     * Set the current list of assignments on the Identity.
     * These values are stored in the preference map of the
     * Identity object, just like role assignments.
     * 
     * @see #PRF_ATTRIBUTE_ASSIGNMENTS
     * 
     * @param assignments List of AttributeAssignments
     */
    public void setAttributeAssignments(List<AttributeAssignment> assignments) {
        if (assignments != null)
            setPreference(PRF_ATTRIBUTE_ASSIGNMENTS, assignments);
        else {
            if (_preferences != null)
                _preferences.remove(PRF_ATTRIBUTE_ASSIGNMENTS);
        }
    }

    /**
     * Remove an assignment from the identity's current list.
     * 
     * @param assignment AttributeAssignment to remove
     */
    public void remove(AttributeAssignment assignment) {
        List<AttributeAssignment> current = getAttributeAssignments();        
        if ( current != null ) {
            Iterator<AttributeAssignment> it = current.iterator();
            while ( it != null && it.hasNext() ) {
                AttributeAssignment as = it.next();
                if ( as == null || as.matches(assignment) ) {
                    it.remove();
                }
            }
            setAttributeAssignments(current);
        }
    }

    /**
     * Add an assignment to the identity's current list.
     * 
     * @param assignment AttributeAssignment to add
     */
    public void add(AttributeAssignment assignment) {
        if ( assignment != null ) {
            List<AttributeAssignment> assignments = getAttributeAssignments();
            if ( assignments == null ) {
                assignments = new ArrayList<AttributeAssignment>();
                assignments.add(assignment);
            } else {
                assignments = mergeAttributeAssignment(assignments, assignment);
            }
            setAttributeAssignments(assignments);
        }   
    }
    
    /**
     * Merge an attribute assignment into the current list
     * of assignments.
     * 
     * @param currentAssignment List of current AttributeAssignments
     * @param assignment AttributeAssignment to merge
     * @return The merged AttributeAssignment list.
     */
    private List<AttributeAssignment> mergeAttributeAssignment(List<AttributeAssignment> currentAssignment, 
                                                               AttributeAssignment assignment) {
        
        boolean found = false;
        List<AttributeAssignment> assignments = currentAssignment;
        if ( currentAssignment == null ) { 
            add(assignment);
            return getAttributeAssignments();
        }
        
        for ( int i=0; i< assignments.size(); i++ ) {
            AttributeAssignment current  = assignments.get(i);
            
            if ( current != null && current.matches(assignment) ) { 
                assignments.set(i, assignment);
                found = true;
                break;
            }
        }
        if ( !found ) { 
            assignments.add(assignment);                
        }
            
        return assignments;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Assignments
    //
    // So we don't have to put yet another BLOB on the Identity we're
    // going to store role assignment metadata in the preferences map.
    // Consider doing this with some of the others like AttributeMetadata.
    //
    // The rather over-engineered collection of assignment searchers is
    // so we can search by either role name or id if we know it.  This
    // is needed by the unit tests that only use names.  This also evolved
    // in 6.2 so that assignment lookup should normally done only with
    // a generated assignmentId, not role names since there can be more
    // than one assignment of a given role.  The older methods that can
    // return an ambiguous assignment are now marked deprecated.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return information about all role assignments.
     */
    public List<RoleAssignment> getRoleAssignments() {
        List<RoleAssignment> roles = null;
        Object o = getPreference(PRF_ROLE_ASSIGNMENTS);
        if (o instanceof List)
            roles = (List<RoleAssignment>)o;
        return roles;
    }

    /**
     * Sets the list of role assignments in the identity.
     * @param roleAssignments the list of role assignments
     */
    public void setRoleAssignments(List<RoleAssignment> roleAssignments) {
        // jsl - this has to add the list even if the size is null
        // or else add(RoleAssignment) doesn't work
        if (roleAssignments != null)
            setPreference(PRF_ROLE_ASSIGNMENTS, roleAssignments);
        else {
            if (_preferences != null)
                _preferences.remove(PRF_ROLE_ASSIGNMENTS);
        }
    }

    /**
     * Add a new RoleAssignment to the Identity. This does not do intelligent
     * matching, it simply appends it to the list.
     */
    public void addRoleAssignment(RoleAssignment ra) {
        if (ra != null) {
            List<RoleAssignment> assignments = getRoleAssignments();
            if (assignments == null) {
                assignments = new ArrayList<RoleAssignment>();
                setRoleAssignments(assignments);
            }
            assignments.add(ra);
        }
    }

    /**
     * Remove a role assignment from the identity.
     * @param ra the role assignment to remove
     */
    public void removeRoleAssignment(RoleAssignment ra) {
        if (ra != null) {
            List<RoleAssignment> roles = getRoleAssignments();
            if (roles != null)
                roles.remove(ra);
        }
    }

    /**
     * Look up a RoleAssignment by assignment id.
     * {@link #getRoleAssignment(String)} will do this too, but this will match
     * only on assignment id rather than the Bundle id so it makes
     * it clearer in code what is being looked for. The old method
     * is retained for backward compatibility.
     * Note:  The IDs are not necessarily unique.  When looking for a specific
     * RoleAssignment, it's best to query by both assignmentID and roleID.
     * See {@link #getRoleAssignmentByAssignmentAndRoleId(String, String)}
     */
    public RoleAssignment getRoleAssignmentById(String assignmentId) {
        RoleAssignment found = null;
        if (assignmentId != null) {
            List<RoleAssignment> list = getRoleAssignments();
            if (list != null) {
                for (RoleAssignment ra : list) {
                    if (assignmentId.equals(ra.getAssignmentId())) {
                        found = ra;
                        break;
                    }
                }
            }
        }
        return found;
    }
    
    /**
     * @return the RoleAssignment that matches the given assignment ID and role ID.
     * If no role ID is given, then the first RoleAssignment with a matching assignment ID is returned.
     */
    public RoleAssignment getRoleAssignmentByAssignmentAndRoleId(String assignmentId, String roleId) {
        RoleAssignment found = null;
        if (!Util.isNullOrEmpty(assignmentId)) {
            List<RoleAssignment> list = getRoleAssignments();
            if (list != null) {
                for (RoleAssignment ra : list) {
                    if (assignmentId.equals(ra.getAssignmentId())) {
                        if (Util.isNullOrEmpty(roleId)) {
                            found = ra;
                            break;
                        } else if (roleId.equals(ra.getRoleId())) {
                            found = ra;
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * 
     * @param assignmentId AssignmentId to search for in the RoleDetection assignmentIds
     * @param bundleId BundleId to match on for the RoleDetection
     * @return RoleDetection matching the given assignmentId and bundleId
     */
    public RoleDetection getRoleDetection(String assignmentId, String bundleId) {
        RoleDetection found = null;
        if(assignmentId != null && bundleId != null) {
            List<RoleDetection> list = getRoleDetections();
            if(list != null) {
                for(RoleDetection rd : list) {
                    if(bundleId.equals(rd.getRoleId()) && rd.getAssignmentIdList().contains(assignmentId)) {
                        found = rd;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Get the list of role assignments for the specified role. 
     *
     * @param role the role 
     * @return the list of role assignments for the role
     */
    public List<RoleAssignment> getRoleAssignments(Bundle role) {
        return getRoleAssignmentsInternal(role.getId(), role.getName());
    }

    /**
     * Get the list of role assignments for the specified role name.
     *
     * @param name name of the role
     * @return the list of role assignments for the role
     */
    public List<RoleAssignment> getRoleAssignments(String name) {
        return getRoleAssignmentsInternal(null, name);
    }

    /**
     * Get the first RoleAssignment matching the Bundle, warning if there is more than one.
     * This is intended for use only in system code where you may need to fall back to 
     * an ambiguous search but do not want to trigger a deprecation warning.
     */
    public RoleAssignment getFirstRoleAssignment(Bundle role) {
        return getFirstRoleAssignment(getRoleAssignments(role));
    }

    /**
     * Look up a role assignment by role name.
     * This is intended only for the unit tests and might be ambiguous.
     */
    public RoleAssignment getFirstRoleAssignment(String name) {
        return getFirstRoleAssignment(getRoleAssignmentsInternal(null, name));
    }

    /**
     * Gets any negative role assignments for the specified role.
     * @param roleName The role name.
     *
     * @return The negative role assignments.
     */
    public List<RoleAssignment> getNegativeRoleAssignments(String roleName) {
        List<RoleAssignment> negativeAssignments = new ArrayList<RoleAssignment>();
        for (RoleAssignment roleAssignment : Util.iterate(getRoleAssignments(roleName))) {
            if (roleAssignment.isNegative()) {
                negativeAssignments.add(roleAssignment);
            }
        }

        return negativeAssignments;
    }

    /**
     * Gets all of the active role assignments for the identity. These are role
     * assignments whose negative flag is set to false.
     *
     * @return The active role assignments.
     */
    public List<RoleAssignment> getActiveRoleAssignments() {
        return filterNegativeRoleAssignments(getRoleAssignments());
    }

    /**
     * Gets the active role assignments for the specified role. These are role
     * assignments whose negative flag is set to false.
     *
     * @param bundle The role.
     * @return The active role assignments for the role.
     */
    public List<RoleAssignment> getActiveRoleAssignments(Bundle bundle) {
        if (bundle == null) {
            return null;
        }

        return getActiveRoleAssignments(bundle.getName());
    }

    /**
     * Gets the active role assignments for the specified role. These are role
     * assignments whose negative flag is set to false.
     *
     * @param roleName The role name.
     * @return The active role assignments for the role.
     */
    public List<RoleAssignment> getActiveRoleAssignments(String roleName) {
        return filterNegativeRoleAssignments(getRoleAssignmentsInternal(null, roleName));
    }

    /**
     * Filters out any negative role assignments from the specified list.
     *
     * @param roleAssignments The role assignment list.
     * @return The filtered role assignment list.
     */
    private List<RoleAssignment> filterNegativeRoleAssignments(List<RoleAssignment> roleAssignments) {
        List<RoleAssignment> active = new ArrayList<RoleAssignment>();
        for (RoleAssignment roleAssignment : Util.iterate(roleAssignments)) {
            if (!roleAssignment.isNegative()) {
                active.add(roleAssignment);
            }
        }

        return active;
    }

    /**
     * Inner utility method which returns the list of role assignments within the given list which match the 
     * specified id and/or name.
     * 
     * Note the id is compared first to the RoleAssignment's assignmentId, which is the expected
     * way of identifying a RoleAssignment as of 6.3. If no match is found, then for backward compatibility the 
     * id will be compared with the RoleAssignment roleId and roleName.
     * 
     * @return a list of role assignments, which can be empty.
     */
    private List<RoleAssignment> getRoleAssignmentsInternal(String id, String name) {

        List<RoleAssignment> foundAssignments = new ArrayList<RoleAssignment>();
        for (RoleAssignment roleAssignment : Util.iterate(getRoleAssignments())) {
            RoleAssignment match = null;

            if (id != null && id.equals(roleAssignment.getAssignmentId())) {
                match = roleAssignment;
            }
            else if (id != null && id.equals(roleAssignment.getRoleId())) {
                match = roleAssignment;
                // Reset name in case a role was renamed - bug 7903
                if (name != null) {
                    match.setRoleName(name);
                }
            }
            else if (name != null && name.equals(roleAssignment.getRoleName())) {
                // only do this for the unit tests that don't have ids
                // if there is an id it must be obeyed regardless of name
                match = roleAssignment;
            }

            if (match != null) {
                foundAssignments.add(match);
            }
        }

        return foundAssignments;
    }

    /**
     * Utility which returns the first assignment in a list of assignments, or null if the list is empty. Logs a 
     * warning for a list of size > 1. Used as a utility for deprecated versions of getRoleAssignment()
     * that do not handle duplicate assignments.
     */
    private RoleAssignment getFirstRoleAssignment(List<RoleAssignment> assignments) {
        RoleAssignment assignment = null;
        if (!Util.isEmpty(assignments)) {
            if (log.isWarnEnabled() && assignments.size() > 1) {
                log.warn("Deprecated method found more than one matching RoleAssignment. Returning only the first one found.");
            }
            assignment = assignments.get(0);
        }
        return assignment;
    }

    //
    // Deprecated methods that won't handle dupliate assignments.  System code should no longer
    // call these, but calls still exist in custom code.
    //

    /**
     * Get a role assignment with the specified assignment id. 
     * 
     * @deprecated as of 6.3 since there is no longer any guarantee that only a single matching role assignment will be found. 
     * Only the first matching assignment will be returned and a WARNING will be logged. 
     * Specifying a role id as an argument to this method is deprecated.
     * 
     * @param id the assignment id
     * @return the role assignment, or null if none was found.
     * 
     * Note: for backward-compatibility purposes, if an assignment with the specified id is not found,
     * this method will return a role assignment where the role id matches the specified id; however, since
     * this is not guaranteed to find only a single role assignment, only the first such assignment will be
     * returned and a WARNING will be logged. 
     */
    // todo rshea TA4710: check all calls to this
    @Deprecated
    public RoleAssignment getRoleAssignment(String id) {
        return getFirstRoleAssignment(getRoleAssignmentsInternal(id, null));
    }

    /**
     * Get a role assignment for a specific role. 
     *
     * @deprecated as of 6.3 since there is no longer any guarantee that only a single matching role assignment will be found. 
     * Only the first matching assignment will be returned and a WARNING will be logged.
     * @param role Role to match assignment
     * @return a role assignment for a specific role
     */
    @Deprecated
    public RoleAssignment getRoleAssignment(Bundle role) {
        return getRoleAssignment(role.getId(), role.getName());
    }

    /**
     * Get a role assignment with a specified role id or name. 
     *
     * @deprecated as of 6.3 since there is no longer any guarantee that only a single matching role assignment will be found. 
     * Only the first matching assignment will be returned and a WARNING will be logged.
     * @param id the role id (NOT the assignment id)
     * @param name the role name (NOT the assignment id)
     * @return a role assignment with a specified roleId or name
     */
    @Deprecated
    public RoleAssignment getRoleAssignment(String id, String name) {
        return getFirstRoleAssignment(getRoleAssignmentsInternal(id, name));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RoleRequests
    //
    // This is a semi-deprecated model for recording requests to
    // provision a non-assigned role with a sunrise or sunset date.
    // In practice it should only be used to provision detectable roles
    // permitted by an assigned role, but we have a new model for that
    // with nested RoleAssignments.  This old model will only be used
    // for backward compatibility with role requests that do not match
    // any of the current assignments.
    // 
    //////////////////////////////////////////////////////////////////////

    public List<RoleRequest> getRoleRequests() {
        List<RoleRequest> roles = null;
        Object o = getPreference(PRF_ROLE_REQUESTS);
        if (o instanceof List)
            roles = (List<RoleRequest>)o;
        return roles;
    }

    public void setRoleRequests(List<RoleRequest> roles) {
        if (roles != null)
            setPreference(PRF_ROLE_REQUESTS, roles);
        else {
            if (_preferences != null)
                _preferences.remove(PRF_ROLE_REQUESTS);
        }
    }

    /**
     * Look for a request for a role.
     * In this model you cannot have more than one request for the
     * same role.
     */
    public RoleRequest getRoleRequest(Bundle role) {

        List<RoleRequest> requests = getRoleRequests();
        String id = role.getId();
        String name = role.getName();
        
        if (requests != null) {
            for (RoleRequest roleRequest : requests) {
                if (id != null && id.equals(roleRequest.getRoleId())) {
                    // Reset name in case a role was renamed - bug 7903
                    if (name != null) {
                        roleRequest.setRoleName(name);
                        return roleRequest;
                    }
                }
                else if (name != null && name.equals(roleRequest.getRoleName())) {
                    // only do this for the unit tests that don't have ids
                    // if there is an id it must be obeyed regardless of name
                    return roleRequest;
                }
            }
        }
        
        return null;
    }
    
    public void removeRoleRequest(RoleRequest req) {
        if (req != null) {
            List<RoleRequest> requests = getRoleRequests();
            if (requests != null)
                requests.remove(req);
        }
    }
    
    public void addRoleRequest(RoleRequest req) {
        if (req != null) {
            List<RoleRequest> requests = getRoleRequests();
            if (requests == null) {
                requests = new ArrayList<RoleRequest>();
                setRoleRequests(requests);
            }
            requests.add(req);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Role Detections
    //
    // For each detected role, information about which entitlements
    // were used in the detection of each role.
    // Like RoleAssignments, we put these in the preferences map so 
    // we don't have to maintain another blob.
    //
    //////////////////////////////////////////////////////////////////////
        
    /**
     * Return information about all detected role entitlements.
     */
    public List<RoleDetection> getRoleDetections() {
        List<RoleDetection> list = null;
        Object o = getPreference(PRF_ROLE_DETECTIONS);
        if (o instanceof List)
            list = (List<RoleDetection>)o;
        return list;
    }
    
    /**
     * Return List of RoleDetections that contain a RoleTarget for a given Application
     * If Application is null, all RoleDetections will be returned
     */
    public List<RoleDetection> getRoleDetections(Application app) {
        if(app == null) {
            return getRoleDetections();
        }
        List<RoleDetection> appRoleDetects = new ArrayList<RoleDetection>();
        if(!Util.isEmpty(getRoleDetections())) {
            for(RoleDetection rd : getRoleDetections()) {
                //Can we go off of RoleTargets or should we get the bundle and use bundle.referencesApp?
                if(rd.getTargets() != null) {
                    for(RoleTarget rt : rd.getTargets()) {
                        if(rt.getApplicationId() != null && app.getId() != null) {
                            if(rt.getApplicationId().equals(app.getId())) {
                                appRoleDetects.add(rd);
                                break;
                            }
                        } else if(rt.getApplicationName() != null && app.getName() != null) {
                            //Should always be able to use ID, but keep Name logic for unit tests
                            if(rt.getApplicationName().equals(app.getName())) {
                                appRoleDetects.add(rd);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return appRoleDetects;
    }
    
    /**
     * Return a set of RoleDetections that contain a RoleTarget referencing any of the applications supplied 
     * @param apps Applications to filter against
     */
    public Set<RoleDetection> getRoleDetections(List<Application> apps) {
        if(apps == null || apps.isEmpty()) {
            if(!Util.isEmpty(getRoleDetections()))
                return new HashSet<RoleDetection>(getRoleDetections());
            else
                return new HashSet<RoleDetection>();
        }
        
        Set<RoleDetection> filteredDetections = new HashSet<RoleDetection>();
        for(Application a : apps) {
            filteredDetections.addAll(getRoleDetections(a));
        }
        
        return filteredDetections;
    }
        
    /**
     * Set the list of detected roles. This should be used only
     * by the EntitlementCorrelator or IIQEvaluator.
     */
    public void setRoleDetections(List<RoleDetection> detections) {
        if (detections != null)
            setPreference(PRF_ROLE_DETECTIONS, detections);
        else {
            if (_preferences != null)
                _preferences.remove(PRF_ROLE_DETECTIONS);
        }
    }
    
    /**
     * NOTE: This should only be used in Tests. In general, RoleDetections should only be modified by 
     * Correlator and Evaluator.
     * @param roleName Name of the Role associated with the Detection to be removed
     * @param assignmentIds CSV of assignmentId's that the RoleDetection should contain. If null,
     *              assignmentId is not tested, but instead any RoleDetection associated to 
     *              the given RoleName is removed
     */
    public void removeRoleDetection(String roleName, String assignmentIds) {
        if(!Util.isEmpty(getRoleDetections())) {
            List<RoleDetection> detections = getRoleDetections();
            for(Iterator<RoleDetection> it = detections.iterator(); it.hasNext(); ) {
                RoleDetection det = it.next();
                if(det.getRoleName().equals(roleName)) {
                    if(!Util.isNullOrEmpty(assignmentIds)) {
                        if(Util.nullSafeEq(det.getAssignmentIds(),assignmentIds)) {
                            it.remove();
                        }
                    } else {
                        it.remove();
                    }
                }
            }
        }
    }
    
    

    /**
     * Add a new RoleDetection to the Identity.  This does not do intelligent
     * matching, it simply appends it to the list.
     */
    public void addRoleDetection(RoleDetection rd) {
        if (rd != null) {
            List<RoleDetection> detections = getRoleDetections();
            if (detections == null) {
                detections = new ArrayList<RoleDetection>();
                setRoleDetections(detections);
            }
            detections.add(rd);
        }
    }

    /**
     * @exclude
     * Note that we allow name matches for the unit tests.
     * This is only used by IIQEvaluator and Entitlizer now.
     */
    @Deprecated
    public RoleDetection getRoleDetection(List<RoleDetection> list, 
                                          String id, 
                                          String name) {
        RoleDetection found = null;
        if (list != null) {
            for (RoleDetection ra : list) {
                String otherId = ra.getId();
                if (id != null && otherId != null) {
                    if (id.equals(otherId))
                        found = ra;
                }
                else if (name != null && name.equals(ra.getName())) {
                    // only do this for the unit tests that don't have ids
                    // if there is an id it must be obeyed regardless of name
                    found = ra;
                }
                if (found != null)
                    break;
            }
        }
        return found;
    }
    
    /**
     * Search for a RoleDetection for the given Bundle that does not have an assignment Id
     * There will only ever be one RoleDetection for a given role without an assignment Id
     * @param bundle Bundle for which role detection we are searching
     * @return RoleDetection containing the supplied bundle without an assignmentId or null
     */
    public RoleDetection getUnassignedRoleDetection(Bundle bundle) {
        if (bundle == null) {
            return null;
        }

        RoleDetection unassignedDetection = null;
        if(getRoleDetections() != null) {
            for(RoleDetection rd : getRoleDetections()) {
                if(Util.isNullOrEmpty(rd.getAssignmentIds())) {
                    if(rd.getRoleId() != null && bundle.getId() != null) {
                        if(rd.getRoleId().equals(bundle.getId())) {
                            unassignedDetection = rd;
                        }
                    } else if(rd.getRoleName() != null && bundle.getName() != null) {
                        if(rd.getRoleName().equals(bundle.getName())) {
                            unassignedDetection = rd;
                        }
                    }
                    if(unassignedDetection != null)
                        break;
                }
            }
        }
        return unassignedDetection;
    }
    
    /**
     * Transform the RoleDetection model for this Identity into that the
     * older model EntitlementCorrelator used. This is to support legacy
     * code written against that model.  
     *
     * @deprecated As of 6.3 this should no longer be used by system code since the
     * map is keyed by Bundle and there can be more than one detection
     * for that role. 
     *
     * Have to pass a Resolver so Bundle and Application
     * references can be fetched.
     */
    @Deprecated
    public Map<Bundle,List<EntitlementGroup>> getRoleEntitlementMappings(Resolver r) 
        throws GeneralException {

        Map<Bundle,List<EntitlementGroup>> mappings = 
            new HashMap<Bundle,List<EntitlementGroup>>();
        
        for (RoleDetection detection : Util.iterate(getRoleDetections())) {

            Bundle role = null;
            // allow name only for the unit tests
            String id = detection.getRoleId();
            String name = detection.getRoleName();
            if (id != null)
                role = r.getObjectById(Bundle.class, id);

            if (role == null && name != null)
                role = r.getObjectByName(Bundle.class, name);

            if (role == null)
                log.warn("Unresolved role in RoleDetection");
            else {
                List<EntitlementGroup> ents = RoleTarget.toEntitlementGroups(r, detection.getTargets());
                mappings.put(role, ents);
            }
        }

        return mappings;
    }

    /**
     * Get the RoleDetection for a given role.
     * @deprecated This should no longer be used since a role can be detected multiple
     * times. 
     */
    @Deprecated
    public RoleDetection getRoleDetection(Bundle role) {

        RoleDetection found = null;
        if (role != null)
            found = getRoleDetection(getRoleDetections(), role.getId(), role.getName());
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scores
    //
    // In the XML these are similar to Links, they must be inside
    // an Identity.
    //
    //////////////////////////////////////////////////////////////////////

    public Scorecard getScorecard() {
        return _scorecard;
    }

    public void setScorecard(Scorecard c) {
        _scorecard = c;
    }

    /**
     * Assign the scorecard and set the back pointer.
     * Do not do this in the Hibernate setter as it marks
     * the object dirty.
     */
    public void addScorecard(Scorecard c) {
        _scorecard = c;
        if (_scorecard != null)
            _scorecard.setIdentity(this);
    }

    /**
     * Alternative property accessor for XML serialization that
     * ensures the bi-directional relationship is maintained.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
	public Scorecard getXmlScorecard() {
        return _scorecard;
    }

    public void setXmlScorecard(Scorecard c) {
        addScorecard(c);
    }

    /**
     * Convenience method to dig out the composite score.
     */
    public int getScore() {
        return ((_scorecard != null) ? _scorecard.getCompositeScore() : 0);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MitigationExpirations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add the given MitigationExpiration to this Identity. This will remove
     * an existing mitigation before adding the new mitigation if there is
     * already a mitigation expiration for this same entitlement requested from
     * the same type of certification. This allows, for example, a manager to
     * mitigate a business role in one certification report and later re-mitigate
     * it to extend the mitigation period. The caller should delete the
     * returned MitigationExpiration if one is returned.
     * 
     * @param  exp  The MitigationExpiration to add to this identity.
     * 
     * @return A removed MitigationExpiration if adding this expiration
     *         obsoleted an older expiration.  The caller should delete this.
     */
    public MitigationExpiration add(MitigationExpiration exp) {
        MitigationExpiration found = null;
        if (exp != null) {
            if (_mitigationExpirations == null)
                _mitigationExpirations = new ArrayList<MitigationExpiration>();

            // Look to see if a matching MitigationExpiration already exists on
            // this identity.  If so, remove it and let the newest
            // MitigationExpiration determine the expiration time.
            Comparator<MitigationExpiration> comparator =
                new MitigationExpiration.UniquenessComparator();
            found = Util.find(_mitigationExpirations, exp, comparator);
            if (null != found) {
                _mitigationExpirations.remove(found);
            }

            _mitigationExpirations.add(exp);
        }
        return found;
    }

    /**
     * Remove a MitigationExpiration from this Identity if it matches the
     * given MitigationExpiration (for example - is referring to the same certification
     * target on the same type of certification).
     * 
     * @param  exp  A MitigationExpiration with a Certification and
     *              certification target information (for example - entitlements, etc...)
     *              to use to find the matching MitigationExpiration to remove.
     *
     * @return The removed MitigationExpiration.  The caller should delete this.
     *
     * @see sailpoint.object.MitigationExpiration.UniquenessComparator
     */
    public MitigationExpiration remove(MitigationExpiration exp) {
        MitigationExpiration found = null;

        if ((null != exp) && (null != _mitigationExpirations)) {
            Comparator<MitigationExpiration> comparator =
                new MitigationExpiration.UniquenessComparator();
            found = Util.find(_mitigationExpirations, exp, comparator);
            if (null != found) {
                _mitigationExpirations.remove(found);
            }
        }

        return found;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
	public ActivityConfig getActivityConfig() {
        return _activityConfig;
    }

    public void setActivityConfig(ActivityConfig config) {
        _activityConfig = config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is helper class does the following for the 
     * getRecentAppOwnerCertifications() method:
     * 
     * 1. It maintains a set of CertificationLink objects for AppOwner 
     *    certifications such  that each application associated with the
     *    objects in the set occurs only once.
     * 
     * 2. It sorts these CertificationLinks in date order.
     * 
     */
    private class SortedCertLinkList {
        // Map of Certification Dates keyed by application ID
        Map<String, CertificationLink> appCerts;
        
        SortedCertLinkList() throws GeneralException {
            appCerts = new HashMap<String, CertificationLink>();
        }
        
        public void add(CertificationLink link) throws GeneralException {
            if (null != link) {
                String appId = link.getApplicationId();
                if (appCerts.containsKey(appId)) {
                    CertificationLink conflictingCert = appCerts.get(appId);
                    // Keep the most recent one for the given application
                    if (Util.nullSafeCompareTo(link.getCompleted(), conflictingCert.getCompleted()) > 0) {
                        appCerts.put(appId, link);
                    }
                } else {
                    appCerts.put(appId, link);
                }
            }
        }
        
        public List<CertificationLink> asList() {
            SortedSet<CertificationLink>certLinks = 
                new TreeSet<CertificationLink>(
                    new Comparator<CertificationLink>() {
    				    public int compare(CertificationLink arg0, CertificationLink arg1) {
                            // Return the most recent ones first
                            return -Util.nullSafeCompareTo(arg0.getCompleted(), arg1.getCompleted());
    				    }
                    }
                );
            certLinks.addAll(appCerts.values());
            
            CertificationLink [] certLinkArray = certLinks.toArray(new CertificationLink[certLinks.size()]);
            return Arrays.asList(certLinkArray);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Workgroups
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public boolean isWorkgroup() {
        return _isWorkgroup;
    }

    public void setWorkgroup(boolean isWorkgroup) {
        _isWorkgroup = isWorkgroup;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Identity> getWorkgroups() {
        return _workgroups;
    }

    public void setWorkgroups(List<Identity> workgroups) {
        _workgroups = workgroups;
    }

    public boolean isInWorkGroup(Identity workgroup){
        return workgroup != null && _workgroups != null && _workgroups.contains(workgroup); 
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //  Extended Identity Attributes
    //
    ///////////////////////////////////////////////////////////////////////////
    
    public void setExtendedIdentity1(Identity val) {
        _extendedIdentity1 = val;
    }
    
    public boolean supportsExtendedIdentity() {
        return true;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getExtendedIdentity1() {
        return _extendedIdentity1;
    }

    public void setExtendedIdentity2(Identity val) {
        _extendedIdentity2 = val;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getExtendedIdentity2() {
        return _extendedIdentity2;
    }

    public void setExtendedIdentity3(Identity val) {
        _extendedIdentity3 = val;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getExtendedIdentity3() {
        return _extendedIdentity3;
    }

    public void setExtendedIdentity4(Identity val) {
        _extendedIdentity4 = val;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getExtendedIdentity4() {
        return _extendedIdentity4;
    }

    public void setExtendedIdentity5(Identity val) {
        _extendedIdentity5 = val;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getExtendedIdentity5() {
        return _extendedIdentity5;
    }
    

    /**
     * Add a workgroup to a user, this method 
     * checks for existence before adding 
     * it to the list.
     */
    public void add(Identity workgroup) {
        if ( _workgroups == null ) 
            _workgroups = new ArrayList();

        if ( !_workgroups.contains(workgroup) ) {
            _workgroups.add(workgroup);
        }
    }

    /**
     * Remove a workgroup from a user.
     */
    public void remove(Identity workgroup) {
        if ( _workgroups != null ) {
            _workgroups.remove(workgroup);
        }
    }
    /**
     * Returns a string to indicate which type of Notification
     * settings are configured.
     */ 
    public WorkgroupNotificationOption getNotificationOption() {
        return (WorkgroupNotificationOption)getPreference(PRF_WORKGROUP_NOTIFICATION_OPTION);
    }

    public void setNotificationOption(WorkgroupNotificationOption value) {
        setPreference(PRF_WORKGROUP_NOTIFICATION_OPTION, value);
    }

    /**
     * @deprecated 
     * DO NOT CALL this method, it could cause a fetch of 
     * all the identity's entitlements. These should be
     * queried and modified directly. It is here only so 
     * hibernate can call it.
     *  
     * @return nothing! This method is private and not
     * callable outside this object and should not be called.
     */     
    @SuppressWarnings("unused")
    private List<IdentityEntitlement> getIdentityEntitlements() {
        return _identityEntitlements;
    }

    /**
     * @deprecated 
     * DO NOT CALL this method, it could cause a change of 
     * all the identity's entitlements. These should be
     * queried and modified directly. It is here so 
     * hibernate can call it.
     *  
     */     
    @SuppressWarnings("unused")
    private void setIdentityEntitlements(List<IdentityEntitlement> ents) {
        _identityEntitlements = ents;
    }

    /**
     * Return a list of the change detections that have been accumulated
     * since the changes were last analyzed.
     * 
     * @ignore
     * NOTE: for now keeping this in the trigger snapshot
     * map so they'll be cleared at the same time
     * as other events.  
     * 
     * @return nativeChanges list of NativeChangesDetection objects
     */
    @SuppressWarnings("unchecked")
    public List<NativeChangeDetection> getNativeChangeDetections() {
        HashMap<String,Object> triggers = (HashMap<String,Object>)getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
        if ( triggers != null) {
            List<NativeChangeDetection> detections = (List<NativeChangeDetection>)triggers.get(IdentityTrigger.Type.NativeChange.toString());
            return detections;
        }
        return null;
    }  
    
    // hibernate does not call this directly.. so tidy up the values to purge
    // empty lists
    @SuppressWarnings("unchecked")
    public void setNativeChangeDetections(List<NativeChangeDetection> detections) {
        HashMap<String,Object> triggers = (HashMap<String,Object>)getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
        if ( Util.size(detections) > 0 ) {
            if ( triggers == null ) {
                triggers = new HashMap<String,Object>();
                setAttribute(Identity.ATT_TRIGGER_SNAPSHOTS, triggers);
            }        
            triggers.put(IdentityTrigger.Type.NativeChange.toString(), detections);    
        } else {
            if ( triggers != null)
               triggers.remove(IdentityTrigger.Type.NativeChange);
        }
    }
    
    
    public static boolean isStandardAttributeName(String attributeName) {
        Set<String> standardAttributeNames = new HashSet<String>();
        addNamesToSet(standardAttributeNames, SYSTEM_ATTRIBUTES);        
        return standardAttributeNames.contains(attributeName.toLowerCase());
    }
    
    private static void addNamesToSet(Set<String> setOfNames, String[] namesToAdd) {
        int numAttrs = namesToAdd == null ? 0 : namesToAdd.length;
        
        for (int i = 0; i < numAttrs; ++i) {
            setOfNames.add(namesToAdd[i].toLowerCase());
        }
    }

    /**
     * The name of a type defined in <code>ObjectConfig:Identity</code>.
     */
    public String getType() {
        return Util.getString(_attributes, ATT_TYPE);
    }
    
    public void setType(String type) {
        setPseudo(ATT_TYPE, type);
    }

    /**
     * The software version if type is RPA.
     */
    public String getSoftwareVersion() {
        return Util.getString(_attributes, ATT_SOFTWARE_VERSION);
    }
    
    public void setSoftwareVersion(String softwareVersion) {
        setPseudo(ATT_SOFTWARE_VERSION, softwareVersion);
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getAdministrator() {
        return _administrator;
    }
    
    public void setAdministrator(Identity administrator) {
        _administrator = administrator;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Helper Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the cached identity type definition object.
     */
    public IdentityTypeDefinition getIdentityTypeDefinition() {
        IdentityTypeDefinition type = null;
        ObjectConfig config = getObjectConfig();
        if (config != null && getType() != null) {
            type = config.getIdentityType(getType());
        }
        return type;
    }

    /**
     * Helper to check if a type exists on the Identity Object config or if it is null.
     * @param newValue Proposed new type to be set on the Identity
     * @return True if the type exists or is null, otherwise false.
     */
    public static boolean isValidTypeValue(Object newValue) {
        if (newValue == null) {
            return true;
        } else {
            ObjectConfig config = getObjectConfig();
            if (config != null) {
                return config.hasIdentityType(newValue.toString());
            }
        }
        return false;
    }

}
