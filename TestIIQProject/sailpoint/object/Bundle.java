/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object used to represent complex collections of
 * entitlements.  Bundles can be organized into
 * trees with the the leaf nodes normally having a list
 * of Profiles that define application entitlements.
 *
 * Author: Jeff
 * 
 * This was originally intended to implement several
 * concepts:
 *
 *    Business Roles
 *    Contextual Roles
 *    Prototype Identities
 *    Application Requirements
 *
 * These were conceptually different things, but they
 * all had the same structure: a tree of objects 
 * containing Profiles (which in turn contain entitlements).
 *
 * As we evolved, all of these went away except for
 * Business Role, so at the moment Business Role is
 * synonymous with Bundle.  
 *
 * For awhile we had a "type" property but since we only
 * created one type of bundle (business role) it was used
 * inconsistently and removed.  If we need to resurrect
 * the other bundle types, we might want to consider 
 * subclassing rather than using a type property.  That would
 * allow the bundle types to have independent namespaces.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.BidirectionalCollection;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.util.WebUtil;


/**
 * A class used to represent roles.  The class name is <code>Bundle</code>
 * for historical reasons (representing bundles of entitlements).  You
 * can assume that "bundle" is synonymous with "role" throughout the
 * model.
 */
@Indexes({@Index(name="spt_bundle_modified", property="modified"),
          @Index(name="spt_bundle_created", property="created")})
@XMLClass
public class Bundle extends AbstractCertifiableEntity
    implements Certifiable, Cloneable, RoleContainer, Describable, Classifiable
{
    private static Log log = LogFactory.getLog(Bundle.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 9215651542990425545L;

    /**
     * The name of an attribute that can appear in the Attributes
     * map that indicates that the Template list (provisioning policies)
     * should be MERGED with the profiles rather than replacing the profiles.
     * 
     * This is the first time the Bundle attributes map has been used for
     * system properties, but it eliminages the need to add Hibernate columns
     * for things that will never be queried.
     */
    public static final String ATT_MERGE_TEMPLATES = "mergeTemplates";

    /**
     * The name of an attribute in the Attributes map that indicates
     * this role supports provisioning to multiple accounts on the same
     * Application. This triggers more complex role expansion in the
     * plan compiler, and a more complex account selection UI that
     * allows you to select different accounts for each required IT role.
     * This is only relevant for assignable (typically type=business) roles.
     */
    public static final String ATT_DUPLICATE_ACCOUNTS = "allowDuplicateAccounts";

    /**
     * The name of the attribute which determines whether or not this role allows
     * multiple assignments.
     */
    public static final String ATT_ALLOW_MULTIPLE_ASSIGNMENTS = "allowMultipleAssignments";

    /**
     * The name of an attribute that might appear in the Attributes
     * map that stores the list of AccountSelectorRule for each application.
     * 
     */
    public static final String ATT_ACCOUNT_SELECTOR_RULES = "accountSelectorRules";

    /**
     * A static configuration cache used by the persistence manager
     * to make decisions about searchable extended attributes.
     */
    static private CacheReference _objectConfig;

    /**
     * Starting with 3.0 bundle representing roles can have
     * a type defined in ObjectConfig:Bundle.
     */
    String _type;
    
    /**
     * The display name is used in the business user facing part of the UI for the bundle 
     * object instead of the name property. If null then the name property is displayed.
     */
    String _displayName;

    /**
     * List of inherited roles.
     * This is similar to the "superclass" relationship in Java 
     * or the "senior roles" relationship in RBAC.
     */
    List<Bundle> _inheritance;
    
    List<Bundle> _permits;
    List<Bundle> _requirements;

    /**
     * The "rule" for automatic role assignment, or "granting".
     * This is a new concept added in 3.0, this will be phased
     * in gradually so it will coexist with the Profile concept
     * which is still used for entitlement correlation. Post 3.0,
     * however, this will be refactored so that grant
     * rules and correlation rules are handled in the same way. 
     */
    IdentitySelector _selector;

    /**
     * Whether the profiles should be logically OR'd together 
     * during entitlement correlation.
     */
    boolean _orProfiles;

    /**
     * Leaf bundles will normally have account profiles.
     */
    List<Profile> _profiles;
    
    /**
     * Risk scoring weight for business roles.
     */
    int _riskScoreWeight;

    /**
     * Object that specifies the activity config for this bundle. 
     * Specifically bundle type business roles.
     */
    private ActivityConfig _activityConfig;
    
    /**
     * Role Mining Statistics.   
     * this is a convenient place for these and avoids having to add another 
     * wrapper class for mining results.
     */
    MiningStatistics _miningStatistics;
    
    /**
     * Optional rule for selecting specific Links to be used 
     * to match the profiles.
     */
    Rule _joinRule;


    /**
     * Extended attributes.
     */
    Attributes<String, Object> _attributes;
    
    /**
     * Optional plan to specify the explicit entitlements necessary
     * when provisioning this role to a user that does not have it.
     * This is necessary if the Profiles are ambiguous.
     */
    ProvisioningPlan _provisioningPlan;

    /**
     * List of provisioning forms to supply (possibly with interactive prompting)
     * provisioning attributes.
     * This has now replaced an internal ProvisioningPlan as the way to 
     * disambiguate profiles.
     */
    List<Form> _provisioningForms;

    /**
     * The date this role will turn from disabled to enabled.
     * This date is used to trigger.
     */
    Date _activationDate;

    /**
     * The date this role will turn from enabled to disabled.
     */
    Date _deactivationDate;
   
    RoleScorecard _scorecard;
    
    RoleIndex _roleIndex;
    
    /**
     * This is a transient property used in certifications. This will be passed along, so the
     * CertificationItem will get the assignmentId from the certifiable Bundle.
     * In the case of a Role Assignment, this is the assignment ID of the Role Assignment.
     * In the case of a Role Detection, this is the csv of assignmentId's on the RoleDetection.
     * NOTE: Certifications will not use this for RoleDetections currently due to the fact that
     * RoleDetections with assignmentId will get rolled up into the corresponding RoleAssignments 
     */
    String _assignmentId;

    /**
     * True if this bundle is pending for delete.
     */
    boolean _pendingDelete;

    List<TargetAssociation> _associations;

    /**
     * Transient variable set by the load method once it has completed.
     * There were reports of load() taking a long time with large role models   
     * in a complex graph. This should make this faster.
     *
     * This is also used to detect cycles by setting it at the start of the
     * load process.
     */
    private transient boolean _loaded;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    
    public Bundle() {
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitBundle(this);
    }

    /**
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  
     */
    public void load() {

        if (!_loaded) {
            try {
                _loaded = true;

                // assume we don't need _approval for aggregation
    
                if (_inheritance != null) {
                    for (Bundle b : _inheritance) {
                        b.load();
                    }
                }
    
                if (_permits != null) {
                    for (Bundle b : _permits)
                        b.load();
                }
    
                if (_requirements != null) {
                    for (Bundle b : _requirements)
                        b.load();
                }
    
                if (_profiles != null) {
                    for (Profile p : _profiles)
                        p.load();
                }
    
                // these can have Rule references that need to be loaded
                if (_provisioningForms != null) {
                    for (Form f : _provisioningForms)
                        f.load();
                }

                if (_selector != null)
                    _selector.load();
    
                if (_activityConfig != null)
                    _activityConfig.getEnabledApplications();
    
                if (_scorecard != null)
                    _scorecard.load();
            
                // Need this for the modeler
                WorkflowCase pendingWorkflow = getPendingWorkflow();
                if (pendingWorkflow != null)
                    pendingWorkflow.getOwner();
            
                if (_roleIndex != null)
                    _roleIndex.load();
            
                // MiningStatistics is XML

                // Load up the classification objects on the bundle to resolve
                // any lazy loading issues.
                if (_classifications != null) {
                    for (ObjectClassification c : _classifications) {
                        c.load();
                    }
                }
            }
            catch (Throwable t) {
                // must be some obscure Hibernate thing, it probably isn't
                // critical that we clear this since it will most likely
                // fail again, but just so we don't lie
                _loaded = false;
            }
        }
    }

    public void addInheritance(Bundle b) {
        if (b != null) {
            if (_inheritance == null)
              _inheritance = new ArrayList<Bundle>();
            
            if (!_inheritance.contains(b))
                _inheritance.add(b);
        }
    }

    public boolean removeInheritance(Bundle b) {
        boolean removed = false;
        if (null != _inheritance)
            removed = _inheritance.remove(b);
        return removed;
    }

    public void addPermit(Bundle b) {
        if (b != null) {
            if (_permits == null)
              _permits = new ArrayList<Bundle>();
            
            if (!_permits.contains(b))
                _permits.add(b);
        }
    }

    public boolean removePermit(Bundle b) {
        boolean removed = false;
        if (null != _permits)
            removed = _permits.remove(b);
        return removed;
    }

    /**
     * Returns true if the given role is required if a user is to
     * have this role.
     *
     * @param bundle The Role to look for.
     * @return True if the given role is in the permits list.
     */
    public boolean permits(Bundle bundle){
        return _permits != null && _permits.contains(bundle);
    }

    public void addRequirement(Bundle b) {
        if (b != null) {
            if (_requirements == null)
              _requirements = new ArrayList<Bundle>();
            
            if (!_requirements.contains(b))
                _requirements.add(b);
        }
    }

    public boolean removeRequirement(Bundle b) {
        boolean removed = false;
        if (null != _requirements)
            removed = _requirements.remove(b);
        return removed;
    }

    /**
     * Returns true if this role permits a user to have the given role.
     *
     * @param bundle The Role to look for.
     * @return  True if the given role is in the requirements list.
     */
    public boolean requires(Bundle bundle){
        // todo check parent roles?
        return (_requirements != null && _requirements.contains(bundle));
    }

    /**
     * @deprecated Now that there is more than one bundle list these
     * older accessors are discouraged.
     */
    @Deprecated
    public void add(Bundle b) {
        addInheritance(b);
    }

    /**
     * @deprecated Now that there are more than one bundle list these
     * older accessors are discouraged.
     */
    @Deprecated
    public boolean remove(Bundle b) {
        return removeInheritance(b);
    }

    public void add(Profile m) {
        if (m != null) {
            if (_profiles == null)
                _profiles = new ArrayList<Profile>();
            
            if (!_profiles.contains(m))
                _profiles.add(m);
        }
    }

    public boolean remove(Profile profile) {
        boolean removed = false;
        if (null != _profiles)
            removed = _profiles.remove(profile);
        return removed;
    }
    
    public void add(RoleScorecard scorecard) {
        _scorecard = scorecard;
        scorecard.setRole(this);
    }

    /**
     * An object encapsulating the logic for assigning
     * this role to an identity.
     */
    @XMLProperty
    public IdentitySelector getSelector() {
        return _selector;
    }
 
    public void setSelector(IdentitySelector sel) {
        _selector = sel;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Gets the display name of the Bundle.
     * 
     * @return The display name.
     */
    @XMLProperty
    public String getDisplayName() {
        return this._displayName;
    }
    
    /**
     * Sets the display name of the Bundle.
     * 
     * @param displayName The display name.
     */
    public void setDisplayName(String displayName) {
        // set display name to null if empty string
        if (null != displayName && "".equals(displayName)) {
            displayName = null;
        }
        
        this._displayName = WebUtil.stripHTML(displayName);
    }
    
    /**
     * Gets the displayable name of the Bundle. First checks to see if a display name exists, if 
     * a display name does not exist then the name is returned.
     * 
     * @return The displayable name.
     */
    public String getDisplayableName() {
        if (null == _displayName) {
            return _name;
        }
        
        return _displayName;
    }
    
    /**
     * @exclude
     * Hibernate required method. Do not use.
     * 
     * @deprecated use {@link #setDisplayName(String)}
     */
    @Deprecated
    public void setDisplayableName(String displayableName) {
        return;
    }

    @Override
    public String getAuditClassName() {
        if (Util.isNullOrEmpty(_type)) {
            return "Role";
        } else {
            return "Role - " + _type;
        }
    }

    /**
     * The name of a type defined in <code>ObjectConfig:Bundle</code>.
     */
    @XMLProperty
    public String getType() {
        return _type;
    }
    
    public void setType(String type) {
        _type = type;
    }

    /**
     * List of inherited roles.
     * This is similar to the "superclass" relationship in Java 
     * or the "senior roles" relationship in RBAC.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getInheritance() {
        return _inheritance;
    }

    public void setInheritance(List<Bundle> bundles) {
        _inheritance = bundles;
    }

    public Collection<Bundle> getFlattenedInheritance() {
        Collection<Bundle> inerits = new HashSet<Bundle>();

        if (_inheritance != null){
            for(Bundle req : _inheritance){
                inerits.add(req);
                inerits.addAll(req.getFlattenedInheritance());
            }
        }

        return inerits;
    }

    /**
     * List of permitted roles.
     * This is normally used with "business" roles to 
     * reference "IT" roles as a way of indicating which
     * IT roles are allowed to support a business role.
     * Note that this is NOT the same thing as inheritance.
     * Business and IT roles can have independent inheritance
     * hierarchies.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getPermits() {
        return _permits;
    }

    public void setPermits(List<Bundle> bundles) {
        _permits = bundles;
    }

    /**
     * Gets flattened list of permitted roles for this role as well
     * as all inherited roles.
     */
    public Collection<Bundle> getFlattenedPermits() {
        return getFlattenedPermits(null);
    }
    
    /**
     * Gets flattened list of permitted roles for this role as well
     * as all inherited roles. This is useful both as an optimization as well as 
     * a means of avoiding daisy-chain recursion
     * 
     * @param examinedRoles Set of ids of roles that have already been inspected.  
     * @return Flattened list of permitted roles
     */
    public Collection<Bundle> getFlattenedPermits(Set<String> examinedRoles) {
        Collection<Bundle> permits = new HashSet<Bundle>();

        if (examinedRoles == null) {
            examinedRoles = new HashSet<String>();
        }
        examinedRoles.add(getId());

        if (_permits != null){
            for(Bundle req : _permits){
                permits.add(req);
            }
        }

        if (_inheritance != null){
            for(Bundle parent : _inheritance) {
                if (!examinedRoles.contains(parent.getId())) {
                    permits.addAll(parent.getFlattenedPermits(examinedRoles));                    
                }
            }
        }

        return permits;
    }

    /**
     * List of required roles.
     * This is normally used with "business" roles to 
     * reference "IT" roles as a way of indicating which
     * IT roles are required to support a business role.
     *
     * The distinction between the required and permits list
     * is subtle and useful mostly for outbound provisioning.
     * It is a statement that in order to have a certain
     * business role you must have a set of IT roles and
     * in addition other IT roles are permitted but optional.
     * 
     * For certification and reporting this distinction
     * is not that important. The required and permits list
     * are merged to form the "allowed" list of IT roles.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getRequirements() {
        return _requirements;
    }

    public void setRequirements(List<Bundle> bundles) {
        _requirements = bundles;
    }

    /**
     * Gets flattened list of requirements for this role as well
     * as all inherited roles.
     */
    public Collection<Bundle> getFlattenedRequirements() {
        return getFlattenedRequirements(null);
    }

    /**
     * Gets flattened list of requirements for this role as well
     * as all inherited roles. This is useful both as an optimization 
     * as well as a means of avoiding daisy-chain recursion
     * 
     * @param examinedRoles Set of ids of roles that have already been inspected.  
     * @return List of requirements
     */
    public Collection<Bundle> getFlattenedRequirements(Set<String> examinedRoles) {
        if (examinedRoles == null) {
            examinedRoles = new HashSet<String>();
        }
        examinedRoles.add(getId());
        
        Collection<Bundle> reqs = new HashSet<Bundle>();

        if (_requirements != null){
            for(Bundle req : _requirements){
                reqs.add(req);
                // Some hybrid role types can have required roles that have requirements
                if (!examinedRoles.contains(req.getId())) {
                    reqs.addAll(req.getFlattenedRequirements(examinedRoles));
                }
            }
        }

        if (_inheritance != null){
            for(Bundle parent : _inheritance){
                if (!examinedRoles.contains(parent.getId())) {
                    reqs.addAll(parent.getFlattenedRequirements(examinedRoles));
                }
            }
        }

        return reqs;
    }

    
    /**
     * True if matching any one of the profiles will cause
     * the role to be detected. If this is false all profiles
     * must match.
     */
    @XMLProperty
    public boolean isOrProfiles() {
        return _orProfiles;
    }

    public void setOrProfiles(boolean orProfiles) {
        _orProfiles = orProfiles;
    }

    /**
     * Object that specifies the activity configuration for this bundle. 
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ActivityConfig getActivityConfig() {
        return _activityConfig;
    }

    public void setActivityConfig(ActivityConfig activityConfig) {
        _activityConfig = activityConfig;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setMiningStatistics(MiningStatistics stats) {
        _miningStatistics = stats;
    }

    /**
     * Statistics gathered during role mining.
     */
    public MiningStatistics getMiningStatistics() {
        return _miningStatistics;
    }

    /**
     * Optional rule for selecting specific Links to be used 
     * to match the profiles.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getJoinRule() {
        return _joinRule;
    }

    public void setJoinRule(Rule r) {
        this._joinRule = r;
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    @XMLProperty
    public int getRiskScoreWeight() {
        return _riskScoreWeight;
    }

    public void setRiskScoreWeight(int scoreWeight) {
        _riskScoreWeight = scoreWeight;
    }

    // jsl - what does this do?
    @BidirectionalCollection(elementClass=Profile.class, elementProperty="profile")
    public List<Profile> getProfiles() {
        return _profiles;
    }

    /**
     * @exclude
     * @deprecated This does not set the owner on the Profiles in the List and
     *             should only be used by the persistence mechanisms.  Instead,
     *             use <code>add(Profile)</code>
     * 
     * use {@link #add(Profile)}
     */
    @Deprecated
    public void setProfiles(List<Profile> profiles) {
        _profiles = profiles;
    }

    /**
     * Set the Profile list and make sure the Profiles point
     * back to their Bundle. This is not done in setProfiles
     * because the setter is what Hibernate uses when rehydrating
     * the object and the iteration will cause all the Profiles
     * to be fetched, eliminating the effect of lazy loading.
     */
    public void assignProfiles(List<Profile> profiles) {
        if (profiles != null) {
            for (Profile profile : profiles)
                profile.setBundle(this);
        }
        setProfiles(profiles);
    }

    /**
     * @exclude
     * Alternative property accessor for the Profile list used by the XML
     * serializer.
     *
     * It is important that Profiles point back to their parent Bundle,
     * applications should use the assignProfiles method to make sure that
     * happens. This is not done in the setProfiles property setter because
     * that is what Hibernate uses and it is not desirable to cause the Profiles
     * to be fetched when rehydrating.
     *
     * When editing Identity objects in XML however, it is common
     * to forget the Bundle reference in the Profile. Therefore a
     * a special property is exposed just for the XML serialization that 
     * uses assignProfiles rather setProfiles. assignProfiles cannot be used 
     * directly because the XML expects the usual get/set naming convention.
     * @deprecated use {@link #getProfiles()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Profiles")
    public List<Profile> getXmlProfiles() {
        return _profiles;
    }

    /**
     * @exclude
     * @deprecated use {@link #assignProfiles(java.util.List)}
     */
    @Deprecated
    public void setXmlProfiles(List<Profile> profiles) {
        assignProfiles(profiles);
    }

    /** 
     * @exclude
     * One consequence
     * of this is that in the XML the <Profile> element 
     * has to be within the <Profiles> element rather than
     * having a list of <Reference> elements. To ease migration
     * of all the unit tests, 
     * standalone Profile objects can still be referenced by <Reference>s,
     * but it is required that the parent element name be different.
     */
    // WAIT: this causes more trouble than it's worth if you're
    // not sure the references are all to unshared objects...
    /*
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Profile> getProfileRefs() {
        // the getter always returns null, this is not something
        // we want to emit
        return null;
    }

    public void setProfileRefs(List<Profile> profiles) {
        assignProfiles(profiles);
    }
    */

    /**
     * Optional plan to specify the explicit entitlements necessary
     * when provisioning this role to a user that does not have it.
     * This is necessary if the Profiles are ambiguous.
     *
     * UPDATE: This is obsolete, use Signatures instead.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ProvisioningPlan getProvisioningPlan() {
        return _provisioningPlan;
    }

    public void setProvisioningPlan(ProvisioningPlan plan) {
        _provisioningPlan = plan;
    }

    public RoleScorecard getScorecard() {
        return _scorecard;
    }
    
    /**
     * @exclude
     * @deprecated This should only be used by hibernate.  To safely set the scorecard
     * use the {@link #add(RoleScorecard)} method instead
     */
    @Deprecated
    public void setScorecard(RoleScorecard scorecard) {
        _scorecard = scorecard;
    }
    
    /**
     * @exclude
     *  Alternative property accessor for XML serialization that
     * ensures the bi-directional relationship is maintained.
     * @deprecated use {@link #getScorecard()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public RoleScorecard getXmlScorecard() {
        return _scorecard;
    }

    /**
     * @exclude
     * @deprecated use {@link #add(RoleScorecard)} 
     */
    @Deprecated
    public void setXmlScorecard(RoleScorecard c) {
        add(c);
    }
    
    /**
     * Return the object description. Descriptions are generally
     * longer than the name and are intended for display in
     * a multi-line text area.
     * @deprecated Use #getDescription(String locale) instead
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ELEMENT, legacy=true)
    public String getDescription() {
        return _description;
    }

    /**
     *  @deprecated Use #addDescription(String locale, String description)
     */
    @Deprecated
    public void setDescription(String s) {
        // Since there is no longer a corresponding column for this property in the Bundle's table
        // this value will not be persisted in this form.  The method only remains so that
        // we can import legacy Applications from XML.  The PostImportVisitor will 
        // properly add the description to the descriptions map when necessary.
        _description = s;
        new DescribableObject<Bundle>(this).logDeprecationWarning(s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true, the the template list will be merged with
     * the profiles when calculating the provisioning plan. When false
     * the templates replace the profiles.
     */
    public void setMergeTemplates(boolean b) {
        setAttribute(ATT_MERGE_TEMPLATES, ((b) ? "true" : "false"));
    }

    public boolean isMergeTemplates() {
        return Util.otob(getAttribute(ATT_MERGE_TEMPLATES));
    }

    /**
     * When true, this role supports provisioning to multiple accounts on
     * the same Application. This triggers more complex role expansion in the
     * plan compiler, and a more complex account selection UI that
     * allows you to select different accounts for each required IT role.
     * This is only relevant for assignable (typically type=business) roles.
     */
    public void setAllowDuplicateAccounts(boolean b) {
        setAttribute(ATT_DUPLICATE_ACCOUNTS, ((b) ? "true" : "false"));
    }

    public boolean isAllowDuplicateAccounts() {
        return Util.otob(getAttribute(ATT_DUPLICATE_ACCOUNTS));
    }

    /**
     * Determines whether or not the role allows multiple assignments. Can
     * be overridden by global multiple assignment configuration.
     * @return True if allows multiple assignments, false otherwise.
     */
    public boolean isAllowMultipleAssignments() {
        return Util.otob(getAttribute(ATT_ALLOW_MULTIPLE_ASSIGNMENTS));
    }

    /**
     * Sets whether or not the role supports multiple assignments.
     * @param b True to support multiple assignments, false otherwise.
     */
    public void setAllowMultipleAssignments(boolean b) {
        setAttribute(ATT_ALLOW_MULTIPLE_ASSIGNMENTS, ((b) ? "true" : "false"));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Forms/Templates
    //
    // In 7.0 Templates are converted to Forms as they are read from Hibernate
    // or parsed from old XML.
    //
    //////////////////////////////////////////////////////////////////////

    public List<Form> getHibernateProvisioningForms() {
        return _provisioningForms;
    }

    public void setHibernateProvisioningForms(List<Form> l) {
        // Bug#30234 - prevent overwriting _provisionForms after converting
        // from templates, since setXmlTemplates() might be called before
        // this when loading from database
        if (null != l) {
            _provisioningForms = l;
        }
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Form> getProvisioningForms() {
        return _provisioningForms;
    }

    public void setProvisioningForms(List<Form> l) {
        _provisioningForms = l;
    }
    
    /**
     * Usual XML trick to convert a <Templates> list into forms.
     * Also works for converting the Hibernate column.
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Templates")
    public List<Template> getXmlTemplates() {
        return null;
    }

    public void setXmlTemplates(List<Template> l) {
        _provisioningForms = Form.convertTemplates(l);
    }

    public List<Template> getOldTemplates() {
        return Template.convertForms(_provisioningForms);
    }

    public void setOldTemplates(List<Template> l) {
        _provisioningForms = Form.convertTemplates(l);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration cache
    //
    //////////////////////////////////////////////////////////////////////

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (_objectConfig == null) {
            // the master cache is maintained over here
            _objectConfig = ObjectConfig.getCache(Bundle.class);
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
     * This is what tells HibernatePersistenceManager to do the
     * extended attribute promotion whenever saveObject is called.
     */
    public boolean isAutoPromotion() {
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Helper Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the cached role type definition object.
     */
    public RoleTypeDefinition getRoleTypeDefinition() {
        RoleTypeDefinition type = null;
        ObjectConfig config = getObjectConfig();
        if (config != null && _type != null)
            type = config.getRoleType(_type);
        return type;
    }

    public void setAttribute(String name, Object value) {
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

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    /**
     * This was added as part of the RoleContainer implementation. It
     * just wraps the method that fetches the roles
     */
    public List<Bundle> getRoles() {
        return getInheritance();
    }

    
    /**
     * Return true if activity monitoring is enabled.
     * Convenient for JSF which just displays a checkbox.
     */
    public boolean isActivityEnabled() {

        return (_activityConfig != null) ? _activityConfig.enabled() : false;
    }

    /**
     * Returns true if this bundle or any of its inherited bundles
     * reference the given application.
     * 
     * @param  app  The Application for which to check for references.
     * 
     * @return True if this bundle or any of its children reference the given
     *         application, false otherwise.
     *
     * @ignore
     *
     * UPDATE: This is probably no longer used, it looks like something
     * to support the old modeler where strict scoping was maintained
     * over the applications that could be referenced within a business process.
     */
    public boolean referencesApplication(Application app) {
        if (null != app) {
            if (null != _profiles) {
                for (Profile profile : _profiles) {
                    if (app.equals(profile.getApplication()))
                        return true;
                }
            }

            if (null != _inheritance) {
                for (Bundle bundle : _inheritance) {
                    if (bundle.referencesApplication(app))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if this bundle or any of its children reference any of the
     * given applications.
     * 
     * @param  apps  The Applications for which to check for references.
     * 
     * @return True if this bundle or any of its children reference any of the
     *         given applications, false otherwise.
     */
    public boolean referencesAnyApplication(List<Application> apps) {
    
        if (null != apps) {
            for (Application app : apps) {
                if (this.referencesApplication(app)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<Application> getMonitoredApplications() {
        Set<Application> monitoredApplications = new HashSet<Application>();
        
        if (_activityConfig != null && _activityConfig.enabled()) {
            // For 1.1 all applications are enabled or none are
            List<Profile> profiles = getProfiles();
            
            if (profiles != null) {
                for (Profile profile : getProfiles()) {
                    Application potentiallyMonitoredApp = profile.getApplication();
                    if (potentiallyMonitoredApp.isActivityEnabled()) {
                        monitoredApplications.add(potentiallyMonitoredApp);
                    }
                }

            // But in the future this may not be the case.  If not, this is the route to take instead:
//            Set<String> appIds = _activityConfig.getEnabledApplications();
//            
//            try {
//                if (appIds != null && !appIds.isEmpty()) {
//                    QueryOptions opts = new QueryOptions();
//                    opts.add(Filter.in("id",appIds));
//                    monitoredApplications.addAll(SailPointFactory.getCurrentContext().getObjects(Application.class, opts));
//                }
//            } catch (GeneralException e) {
//                // TODO Auto-generated catch block
//                log.error("Could not fetch any applications due to persistence issues", e);
//            }
            }        
        }

        return monitoredApplications;
    }
    
    /**
     * Return the subset of profiles in this bundle that are associated with any of the 
     * applications in the specified List
     * @param apps List of Application objects for whom we want to fetch the Profiles  
     */
    public List<Profile> getProfilesForApplications(List<Application> apps) {        
        List<Profile> retval = new ArrayList<Profile>();
        
        for (Profile profile : _profiles) {
            Application app = profile.getApplication();
            if (app != null && apps != null && apps.contains(app)) {
                retval.add(profile);
            }
        }
        
        return retval;
    }

    /**
     * Gets a list of all the applications assigned to profiles on this bundle.
     *
     * @return Non-null list of all applications
     */
    public Set<Application> getApplications() {
        Set<Application> applications = new HashSet<Application>();

        if (getProfiles() != null) {
            for (Profile profile : getProfiles()) {
                if (profile.getApplication() != null)
                    applications.add(profile.getApplication());
            }
        }

        return applications;
    }
    
    /**
     * Returns a list of bundles that are in the current hierarchy 
     */
    public List<Bundle> getHierarchy(Resolver r) throws GeneralException {
        
        Set<Bundle> bundles = new HashSet<Bundle>();
        bundles.add(this);
        //Get any parents of this bundle
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("inheritance.id", this.getId()));        
        List<Bundle> parents = r.getObjects(Bundle.class, qo);
        if(parents!=null) {
            for(Bundle parent : parents) {
                bundles.addAll(parent.getHierarchy(r));            
            }
        }        
        return new ArrayList<Bundle>(bundles);
    }

    /**
     * Gets the full name of the entity. This is used to construct the names
     * and descriptions of certifications.
     *
     * @return The full name of the entity.
     */
    public String getFullName() {
        return getName();
    }

    /**
    * Returns a UI friendly short name for bundle.
    *
    * Currently this is being used when the Certificationer needs
    * to return a warning like 'no business roles to certify'.
    *
    * @param plural Should the type name be plural?
    * @return Entity type short name, pluralized if plural flag is true
    */
    public String getTypeName(boolean plural) {
        return "Business Profile" + (plural ? "s" : "");
    }

    /**
    * Indicates that you can difference this entity. In some cases,
    * such as Bundles objects, you cannot because unlike Identities,
    * historical snapshots are not stored.
    *
    * This flag allows the Certificationer to skip the differencing
    * step.
    *
    * @see sailpoint.api.Certificationer#addEntity
    * @see sailpoint.api.Certificationer#renderDifferences
    *
    * @return true if this entity can be differenced
    */
    public boolean isDifferencable() {
        return false;
    }

    /**
     * Locate a profile by name or id.
     */
    public Profile findProfile(String name) {
        Profile found = null;
        if (_profiles != null && name != null) {
            for (Profile p : _profiles) {
                if (name.equals(p.getName()) || name.equals(p.getId())) {
                    found = p;
                    break;
                }
            }
        }
        return found;
    }

    @XMLProperty
    public Date getActivationDate() {
        return _activationDate;
    }

    public void setActivationDate(Date activationDate) {
        _activationDate = activationDate;
    }

    @XMLProperty
    public Date getDeactivationDate() {
        return _deactivationDate;
    }

    public void setDeactivationDate(Date deactivationDate) {
        _deactivationDate = deactivationDate;
    }

    public RoleIndex getRoleIndex() {
        return _roleIndex;
    }

    public void setRoleIndex(RoleIndex roleIndex) {
        _roleIndex = roleIndex;
    }

    public void addRoleIndex(RoleIndex roleIndex) {
        _roleIndex = roleIndex;
        if (_roleIndex != null)
            _roleIndex.setBundle(this);
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public RoleIndex getXmlRoleIndex() {
        return _roleIndex;
    }

    public void setXmlRoleIndex(RoleIndex c) {
        addRoleIndex(c);
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Upgrade
    //
    // Prior to 3.0 the "inheritance" property was  the "children" property 
    // which was extremely confusing because it sounded like containment.
    // The property was renamed in 3.0 but we keep XML property accessors
    // around for auto-upgrade.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST,xmlname="Children")
    public List<Bundle> getXMLChildren() {
        // read-only
        return null;
    }

    /**
     * @exclude
     */
    public void setXMLChildren(List<Bundle> children) {
        _inheritance = children;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Descriptions
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * @return Map of descriptions keyed by locale
     */
    public Map<String, String> getDescriptions() {
        Map<String,String> map = null;
        Object o = getAttribute(ATT_DESCRIPTIONS);
        if (o instanceof Map)
            map = (Map<String,String>)o;
        return map;
    }

    /**
     * Set the descriptions
     */
    public void setDescriptions(Map<String, String> map) {
        setAttribute(ATT_DESCRIPTIONS, map);
    }

    /**
     * Incrementally add one description.
     */
    public void addDescription(String locale, String desc) {
        new DescribableObject<Bundle>(this).addDescription(locale, desc);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(String locale) {
        return new DescribableObject<Bundle>(this).getDescription(locale);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(Locale locale) {
        return new DescribableObject<Bundle>(this).getDescription(locale);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Account Selector Rules
    //
    //////////////////////////////////////////////////////////////////////
    public Rule getAccountSelectorRule() {
        AccountSelectorRules accountSelectorRules =  getAccountSelectorRules();
        return (accountSelectorRules != null) ? accountSelectorRules.getBundleLevelAccountSelectorRule() : null;
    }

    public void setAccountSelectorRule(Rule r) {
        AccountSelectorRules accountSelectorRules =  getAccountSelectorRules();
        if (accountSelectorRules == null) {
            accountSelectorRules = new AccountSelectorRules();
            setAttribute(ATT_ACCOUNT_SELECTOR_RULES, accountSelectorRules);
        }
        accountSelectorRules.setBundleLevelAccountSelectorRule(r);
    }


    public void setApplicationAccountSelectorRules(List<ApplicationAccountSelectorRule> rules) {
        AccountSelectorRules accountSelectorRules =  getAccountSelectorRules();
        if (accountSelectorRules == null) {
            accountSelectorRules = new AccountSelectorRules();
            setAttribute(ATT_ACCOUNT_SELECTOR_RULES, accountSelectorRules);
        }
        accountSelectorRules.setApplicationAccountSelectorRules(rules);
    }

    public List<ApplicationAccountSelectorRule> getApplicationAccountSelectorRules() {
        AccountSelectorRules accountSelectorRules =  getAccountSelectorRules();
        return (accountSelectorRules != null) ? accountSelectorRules.getApplicationAccountSelectorRules() : null;
    }

    public boolean hasAccountSelectorRules() {
        AccountSelectorRules accountSelectorRules =  getAccountSelectorRules();
        if (accountSelectorRules == null) {
            return false;
        }
        return (accountSelectorRules.getBundleLevelAccountSelectorRule() != null || 
                !Util.isEmpty(accountSelectorRules.getApplicationAccountSelectorRules()));
    }

    public AccountSelectorRules getAccountSelectorRules() {
        AccountSelectorRules accountSelectorRules = (AccountSelectorRules) (getAttribute(ATT_ACCOUNT_SELECTOR_RULES));
        return accountSelectorRules;
    }
    
    
    public void setAccountSelectorRules(AccountSelectorRules rules) {
        setAttribute(ATT_ACCOUNT_SELECTOR_RULES, rules);
    }
    
    public Rule getAccountSelectorRule(String appname) {

        Rule found = null;

        List<ApplicationAccountSelectorRule> rules = getApplicationAccountSelectorRules();
        for (ApplicationAccountSelectorRule apprule : Util.iterate(rules)) {
            Application app = apprule.getApplication();
            if (app != null && appname.equals(app.getName())) {
                found = apprule.getRule();
                break;
            }
        }

        if (found == null)
            found = getAccountSelectorRule();

        return found;
    }
    
    public void setAssignmentId(String assignId) {
        this._assignmentId = assignId;
    }
    
    public String getAssignmentId() {
        return this._assignmentId;
    }

    /**
     * Returns true if the bundle is pending for delete.
     */
    @XMLProperty
    public boolean isPendingDelete() {
        return _pendingDelete;
    }

    public void setPendingDelete(boolean b) {
        _pendingDelete = b;
    }

    public List<TargetAssociation> getAssociations() { return _associations; }

    public void setAssociations(List<TargetAssociation> assocs) {
        this._associations = assocs;
    }
    

}
