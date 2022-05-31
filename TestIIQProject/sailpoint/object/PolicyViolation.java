/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding information about one policy violation for an Identity.
 * These are created by a PolicyExecutor under the control of the Interrogator.
 *
 * Author: Jeff
 * 
 * This is a awkward class because it was originally designed specifically
 * for SOD policy violations, but we've since added different kinds of
 * policy so the model arguably should be generalized.  If you add a 
 * new policy executor, try to put what you need in the _arguments map
 * rather than extending the Java/Hibernate model.  The main reason to
 * extend the model would be if the property needs to be searchable.
 *
 * We can also overload some of the SOD fields for general policies, 
 * but let's be carefull.  RiskPolicyExecutor is using _constraintName
 * as a place to put the description of what triggered the violation,
 * such as "Composite score reached 500"  or "Composite score increased
 * by 300".  This is an example of a policy that doesn't have complex
 * substructure it's basically just applying rules to the cube, but
 * if more than one rule can be applied it needs a way to convey which
 * one triggered the violation.    We could use _description for this
 * but it makes it easier for generic policy viewers to know that
 * _constraintName will always have something in it that describes
 * the violation in more detail, and will be relatively short.
 * 
 * These are considered "archival" objects which means they may live beyond
 * the lifespan of the associated Policy, SODConstraint, and Bundle objects.
 * 
 * This allows us to do violation trend analysis, and store them indefinately
 * within CertificationArchive and IdentitySnapshot objects.
 *
 * Q: Trend analysis is probably only meaningful for existing policies.
 * If you modify a policy and delete the old versions, how would you
 * ask for a trend?  We won't even know the names of the Policies
 * or SODConstraints you could ask about without sifting through all 
 * of the old PolicyViolations.
 *
 * It is ok to maintain a direct reference to the Identity because if
 * you delete the Identity there is no need to keep the PolicyViolations?
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Transient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.certification.PolicyTreeNode;


/**
 * A class holding information about one policy violation for an Identity.
 */
@XMLClass
public class PolicyViolation extends SailPointObject
    implements Certifiable, Cloneable
{
    public interface IPolicyViolationOwnerSource {
        
        public abstract ViolationOwnerType getViolationOwnerType();
        public abstract void setViolationOwnerType(ViolationOwnerType val);
        public abstract Identity getViolationOwner();
        public abstract void setViolationOwner(Identity val);
        public abstract Rule getViolationOwnerRule();
        public abstract void setViolationOwnerRule(Rule val);
    }
    
    
    private static Log log = LogFactory.getLog(PolicyViolation.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Former argument used to convey the JSF include used to render
     * details about this violation.  This is since been promoted
     * to a field like we do with WorkItem and TaskDefinition.
     * ViolationViewBean will continue to recognize this for
     * pre-3.0 violations but it will eventually be removed.
     */
    public static final String ARG_IDENTITY_PAGE_RENDERER = "PolicyViolationIdentityPageRenderer";

    /**
     * Argument for a list of applications relevant to this violation.
     * This is only needed if the violation is not based on entitlements, and
     * you want the violation to show up in application-centric certification reports.
     * Value should be csv.
     */
    public static final String ARG_RELEVANT_APPS = "RelevantApplications";

    /**
     * Argument containing more details about entitlements involved
     * in the policy violation.
     */
    public static final String ARG_DETAILS = "details";

    /**
     * The name of an optional attribute whose value is 
     * used by the PolicyScorer as the risk weight for this violation.
     * This is used with custom policies that do not have a 
     * GenericConstraints model but which might want to generate
     * different types of violation with different weights.
     */
    public static final String ARG_RISK_WEIGHT = Policy.ARG_RISK_WEIGHT;

    /**
     * Tree of entitlements that caused the violations. Currently this
     * is only used by Entitlement SOD violations, but could
     * be used by others in the future.
     */
    private static final String ARG_VIOLATING_ENTITLEMENTS = "ViolatingEntitlements";

    //////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The states an violation can be in.
     */
    @XMLClass(xmlname="PolicyViolationStatus")
    public static enum Status implements MessageKeyHolder {

        Open("policy_violation_open"),
        Mitigated("policy_violation_mitigated"),
        Remediated("policy_violation_remediated"),
        Delegated("policy_violation_delegated");

        private String messageKey;

        private Status(String messageKey)
        {
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

    // TODO: Should we assume that the object creation date is the
    // date the violation was detected, or do we need something more
    // reliable?

    /**
     * JSF include file used to render details of this violation.
     */
    String _renderer; 

    /**
     * The associated Identity.  
     * 
     * @ignore
     * Currently assuming it is ok to maintain    
     * a direct reference since if you delete the Identity, there is no 
     * longer any need to keep the PolicyViolations?
     * !!! this should be using _owner from SailPointObject.
     */
    Identity _identity;

    /**
     * True if this is considered to be an active violation. 
     * Whenever the interrogator runs, it marks violations
     * that are no longer relevant as inactive.
     */
    boolean _active;

    /**
     * The unique id of the associated policy.
     * Keep both id and name so that we can rename the policy
     * and still get back to it.
     */
    String _policyId;

    /**
     * The name of the associated policy.
     * Copied so we can get to it quickly in SQL without
     * the original Policy object still existing.
     */
    String _policyName;

    /**
     * The unique id of the BaseConstraint within the policy.
     * 
     * @ignore
     * TODO: Would we want to have a List of these or one
     * PolicyViolation for each constraint?  It makes it easier to
     * search on if there is one-for-one.
     */
    String _constraintId;

    /**
     * The short name of the BaseConstraint.
     * This is normally the "display name" used in tables.
     * 
     * @ignore
     * Note that the inherited _description field will also
     * contain a copy of the _description property
     * from the BaseConstraint.
     */
    String _constraintName;

    /**
     * For SODConstraints, the name of the "left" business role 
     * that was in conflict.
     * This can be a CSV if there was more than one matching 
     * business role on the left.
     */
    String _leftBundles;
 
    /**
     * For SODConstraints, the name of the "right" business role 
     * that was in conflict.
     * This can be a CSV if there was more than one matching 
     * business role on the right.
     */
    String _rightBundles;

    // TODO: We may need to save the and/or option for both
    // the LHS and RHS.

    /**
     * For SODConstraints, the names of the bundles (either right or left) 
     * that have been marked to be remediated. This is a CSV.
     */
    String _bundlesMarkedForRemediation;

    /**
     * For Entitlement SoD Violations, value of the entitlements to be remediated.  
     * This is a CSV (application, name, value).
     */
    String _entitlementsMarkedForRemediation;
    
    /**
     * Name of the identity that performed a mitigation on this
     * violation. This should be set by the interrogator to 
     * indicate that the violation was mitigated at the time
     * of the policy scan. If you need more information, you
     * will have to find the matching MitigationExpiration
     * from the Identity. There is no direct reference since
     * these are not SailPointObjects, and even if they were those 
     * are archival objects.
     */
    String _mitigator;

    // TODO: Anything else we want to copy from the MitigationExpiration?
    // if we go too far, then we could just have PolicyViolation *be*
    // something that the expiration scanner has to look at.

    /**
     * The unique id of the associated application activity object.
     */
    String _activityId;
    
    /**
     * Optional arguments to the violation.
     */
    Attributes<String,Object> _arguments;

    /**
     * A transient flag passed from the PolicyExecutors back
     * to the Interrogator to indicate that notifications and
     * work items need to be opened. Some policies might only
     * choose to do this for severe violations to prevent
     * email explosion.
     */
    boolean _alertable;
    
    /** 
     * The current status of the policy violation
     */
    Status _status;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyViolation() {
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitPolicyViolation(this);
    }

    /**
     * @ignore
     * NOTE WELL: This class doesn't actually declare the name mapping
     * with a unique constraint because the name is normally null and
     * SQL Server thinks two objects with null values violates uniqueness.
     *
     * For the unit tests however, we need to be able to import files
     * containing pre-constructed policy violations.  If the policy violations
     * don't have names or ids we will add them whenever the file is 
     * imported rather than replacing the existing ones.  So, this class
     * will way that it has a unique name even though it doesn't.  
     * The persistence manager will use this hint to look for an object
     * with the same name and replace it rather than adding a new one.
     * This trick is used only for the unit tests.  In practice policy
     * violations will never have names.
     */
    @Override
    public boolean isNameUnique() {
        return true;
    }
    
    /**
     * @ignore
     * Does this conflict with isNameUnique()?
     */
    @Override
    public boolean hasName() {
        return false;
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * JSF include file used to render details of this violation.
     */
    @XMLProperty
    public String getRenderer() {
        return _renderer;
    }

    public void setRenderer(String s) {
        _renderer = s;
    }

    /** 
     * The current status of the policy violation
     */
    public Status getStatus() {
        return _status;
    }

    @XMLProperty
    public void setStatus(Status _status) {
        this._status = _status;
    }

    /**
     * The identity that has this violation.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="IdentityRef")
    public Identity getIdentity() {
        return _identity;
    }

    public void setIdentity(Identity id) {
        _identity = id;
    }

    /**
     * True if this is considered to be an active violation. 
     * Whenever the interrogator runs, it marks violations
     * that are no longer relevant as inactive.
     */
    @XMLProperty
    public boolean isActive() {
        return _active;
    }

    public void setActive(boolean b) {
        _active = b;
    }

    /**
     * The unique id of the associated policy.
     */
    @XMLProperty
    public String getPolicyId() {
        return _policyId;
    }

    public void setPolicyId(String id) {
        _policyId = id;
    }

    /**
     * The name of the associated policy.
     */
    @XMLProperty
    public String getPolicyName() {
        return _policyName;
    }

    public void setPolicyName(String id) {
        _policyName = id;
    }

    @XMLProperty
    public String getConstraintId() {
        return _constraintId;
    }

    /**
     * The unique id of the BaseConstraint within the policy
     * that was violated.
     */
    public void setConstraintId(String id) {
        _constraintId = id;
    }

    /**
     * The short name of the BaseConstraint that was violated.
     */
    @XMLProperty
    public String getConstraintName() {
        return _constraintName;
    }

    public void setConstraintName(String name) {
        _constraintName = name;
    }

    /**
     * For role SOD policies, the name of the roles on the left side
     * that were in conflict. This will be a CSV if there was more 
     * than one matching role on the left.
     * 
     * This is relevant only for policies of TYPE_SOD.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="LeftBundleNames")
    public String getLeftBundles() {
        return _leftBundles;
    }

    public void setLeftBundles(String names) {
        _leftBundles = names;
    }

    /**
     * Accumulate bundle names as CSVs as they are added.  Prevent duplicates.
     * @param name The name of the role to be added
     */
    public void addLeftBundle(String name) {
    	if (null == _leftBundles) {
    		_leftBundles = name;
    	} else {
    		// don't add duplicates
    		String[] leftBundles = _leftBundles.split(",");
    		
    		for (String s : leftBundles) {
    			if (s.equals(name)) {
    				return;
    			}
    		}
    		
    		_leftBundles += "," + name;
    	}
    }
    
    /**
     * For role SOD policies, the name of the roles on the right side
     * that were in conflict. This will be a CSV if there was more 
     * than one matching role.
     * 
     * This is relevant only for policies of TYPE_SOD.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="RightBundleNames")
    public String getRightBundles() {
        return _rightBundles;
    }

    public void setRightBundles(String names) {
        _rightBundles = names;
    }
    
    /**
     * Accumulate bundle names as CSVs as they are added. Prevent duplicates.
     * @param name The name of the role to be added
     */
    public void addRightBundle(String name) {
    	if (null == _rightBundles) {
    		_rightBundles = name;
    	} else {
    		// don't add duplicates
    		String[] rightBundles = _rightBundles.split(",");
    		
    		for (String s : rightBundles) {
    			if (s.equals(name)) {
    				return;
    			}
    		}
    		
    		_rightBundles += "," + name;
    	}
    }

    /**
     * For SODConstraints, the names of the bundles (either right or left) 
     * that have been marked to be remediated. This is a CSV.
     * 
     * This is relevant only for policies of TYPE_SOD.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="BundlesMarkedForRemediation")
    public String getBundlesMarkedForRemediation() {
        return _bundlesMarkedForRemediation;
    }

    /**
     * This is relevant only for policies of TYPE_SOD.
     */
    public void setBundlesMarkedForRemediation(String names) {
        _bundlesMarkedForRemediation = names;
    }

    /**
     * This is relevant only for policies of TYPE_SOD.
     */
    public void setBundleNamesMarkedForRemediation(List<String> bundles) {
        _bundlesMarkedForRemediation = Util.listToCsv(bundles);
    }

    /**
     * This is relevant only for policies of TYPE_SOD.
     */
    public void setBundlesMarkedForRemediation(List<Bundle> bundles) {
    
        List<String> names = new ArrayList<String>();
        if (null != bundles) {
            for (Bundle b : bundles) {
                names.add(b.getName());
            }
        }
        _bundlesMarkedForRemediation = Util.listToCsv(names);
    }

    /**
     * Name of the identity that performed a mitigation on this
     * violation. This should be set by the interrogator to 
     * indicate that the violation was mitigated at the time
     * of the policy scan. If you need more information, you
     * will have to find the matching MitigationExpiration
     * from the Identity.
     */
    @XMLProperty
    public String getMitigator() {
        return _mitigator;
    }

    public void setMitigator(String name) {
        _mitigator = name;
    }

    /**
     * The unique id of the associated application activity object.
     * This is relevant only for policies of TYPE_ACTIVITY.
     */
    @XMLProperty
    public String getActivityId() {
        return _activityId;
    }

    public void setActivityId(String id) {
        _activityId = id;
    }

    public void setAlertable(boolean b) {
        _alertable = b;
    }

    /**
     * A transient flag passed from the PolicyExecutors back
     * to the Interrogator to indicate that notifications and
     * work items need to be opened. Some policies might only
     * choose to do this for severe violations to prevent
     * email explosion.
     */
    public boolean isAlertable() {
        return _alertable;
    }
    
    // backward compatibility for custom policies
    public void setNotify(boolean b) {
        setAlertable(b);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    // THINK: This little id/name pattern is appearing in several places
    // now, we could make this a little better by factoring out a 
    // Reference class and using that instead.  We'd like the references
    // to be components rather than entities though so to Hibernate
    // the approaches are really the same, its just a minor Java 
    // convenience.
    // 
    //////////////////////////////////////////////////////////////////////

    public Policy getPolicy(Resolver r) throws GeneralException {
        Policy p = null;
        if (r != null) {
            if (_policyId != null)
                p = r.getObjectById(Policy.class, _policyId);
            else if (_policyName != null)
                p = r.getObjectByName(Policy.class, _policyName);
        }
        return p;
    }

    public void setPolicy(Policy p) {
        if (p != null) {
            _policyId = p.getId();
            _policyName = p.getName();
        }
        else {
            _policyId = null;
            _policyName = null;
        }
    }

    /**
     * Gets the appropriate type of constraint for the given policy.
     *
     * @param r Resolver user to retrieve policy and constraint
     * @return Constraint or null if not found
     * @throws GeneralException
     */
    public BaseConstraint getConstraint(Resolver r) throws GeneralException{
        Policy p = getPolicy(r);
        return (p != null) ? p.getConstraint(this) : null;
    }

    /**
     * Sets the constraint on the PolicyViolation, copying the id and name.
     * @param c Constraint to set
     */
    public void setConstraint(BaseConstraint c) {
        if (c != null) {
            _constraintId = c.getId();
            _constraintName = c.getName();
            _description = c.getDescription();
        }
        else {
            _constraintId = null;
            _constraintName = null;
            _description = null;
        }
    }

    /**
     * Get the best displayable name for this violation.
     * 
     * NOTE: This is a holdover from pre-3.0 
     * where the displayable name could come from up to four
     * different places. Now, the <code>constraintName</code> should
     * be used consistently for this.
     * 
     */
    public String getDisplayableName() throws GeneralException {

        String name = _constraintName;
        if (name == null) {
            name = _description;
            if (name == null) {
                // shouldn't have to resort to this if we've upgraded
                name = _constraintId;
                if (name == null) {
                    // my my, what the hell is this?
                    name = _id;
                }
            }
        }
        return name;
    }

    /**
     * Return a short description of the specific SOD violation.
     * This is used by the unit tests.
     */
    public String getSODSummary() {
        StringBuffer b = new StringBuffer();
        b.append(_leftBundles);
        b.append(" : ");
        b.append(_rightBundles);
        return b.toString();
    }

    /**
     * Return a list with the resolved left bundles.
     */
    public List<Bundle> getLeftBundles(Resolver r) throws GeneralException {

        return getBundles(r, _leftBundles);
    }

    /**
     * Return a list with the resolved right bundles.
     */
    public List<Bundle> getRightBundles(Resolver r) throws GeneralException {

        return getBundles(r, _rightBundles);
    }

    /**
     * Get the names of the bundles marked for remediation.
     */
    public List<String> getBundleNamesMarkedForRemediation(){
        return Util.csvToList(_bundlesMarkedForRemediation);
    }

    /**
     * Return whether the given Bundle is marked for remediation.
     * 
     * @param  b  The bundle to check.
     * 
     * @return True if the given bundle is marked for remediation, false
     *         otherwise.
     */
    public boolean isMarkedForRemediation(Bundle b) throws GeneralException {
        return getBundleNamesMarkedForRemediation().contains(b.getName());
    }

    private List<Bundle> getBundles(Resolver r, String bundleCSV)
        throws GeneralException {

        List<Bundle> bundles = new ArrayList<Bundle>();

        List<String> bundleNames = Util.csvToList(bundleCSV);
        for (String bundleName : bundleNames) {
            Bundle b = r.getObjectByName(Bundle.class, bundleName);
            if (b != null) {
                bundles.add(b);
            }
            else {
                if (log.isWarnEnabled())
                    log.warn("Referenced Bundle no longer exists: " + bundleName);
            }
        }

        return bundles;
    }

    /**
     * Return true if the two violations reference the same
     * constraint.
     */
    public boolean isConstraintEqual(PolicyViolation other) {

        boolean eq = false;

        // Since constraints have unique ids we only need to 
        // compare those, the policyId is redundant.

        // try the ids first
        String id = other.getConstraintId();
        if (id != null && _constraintId != null) 
            eq = id.equals(_constraintId);
        else {
            // one of us didn't have an id, try the constraint names
            id = other.getConstraintName();
            if (id != null && _constraintName != null)
                eq = id.equals(_constraintName);
        }

        return eq;
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("identity", "Identity");
        cols.put("constraintName", "Constraint");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %s\n";
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        if (args != null) 
            _arguments = args;
        else {
            // always keep an empty map for JSF
            // !! is this really necessary, its annoying for the XML
            // serialization
            _arguments = new Attributes<String,Object>();
        }
    }

    public Object getArgument(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }
    
    public void setArgument(String name, Object value) {

        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        _arguments.put(name, value);
    }
    
    /**
     * Return a list of the names for the applications that factored into the
     * detection of this violation, or null if not available.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRelevantApps() {
        return (List<String>) getArgument(ARG_RELEVANT_APPS);
    }

    /**
     * Set the names of the applications that factored into the detection of
     * this violation.
     */
    public void setRelevantApps(List<String> apps) {
        setArgument(ARG_RELEVANT_APPS, apps);
    }
    
    /**
     * Return a list of the applications that factored into the detection of
     * this violation, or null if not available.
     */
    public List<Application> getRelevantApps(Resolver resolver)
        throws GeneralException {

        List<Application> apps = null;
        List<String> appNames = getRelevantApps();
        if (null != appNames) {
            apps = new ArrayList<Application>();
            for (String appName : appNames) {
                apps.add(resolver.getObjectByName(Application.class, appName));
            }
        }
        return apps;
    }

    public ViolationDetails getDetails() {
        return (ViolationDetails)getArgument(ARG_DETAILS);
    }

    public void setDetails(ViolationDetails details) {
        setArgument(ARG_DETAILS, details);
    }
    
    @XMLProperty
    public String getEntitlementsMarkedForRemediation() {
        return _entitlementsMarkedForRemediation;
    }
    
    public void setEntitlementsMarkedForRemediation( String entitlementsMarkedForRemediation ) {
        this._entitlementsMarkedForRemediation = entitlementsMarkedForRemediation;
    }

    @Transient
    public List<PolicyTreeNode> getEntitlementsToRemediate() throws GeneralException {
        return getPolicyTreeNodesFromCsvList();
    }
    
    @Transient
    public void setEntitlementsToRemediate( List<PolicyTreeNode> entitlementsToRemediate ) {
        String json = getJSONFromPolicyTreeNodes( entitlementsToRemediate );
        _entitlementsMarkedForRemediation = json;
    }

    public List<IdentitySelector.MatchTerm> getViolatingEntitlements(){
        return (List<IdentitySelector.MatchTerm>)getArgument(ARG_VIOLATING_ENTITLEMENTS);
    }

    public void setViolatingEntitlements(List<IdentitySelector.MatchTerm> entitlements){
        if (entitlements != null && !entitlements.isEmpty())
            setArgument(ARG_VIOLATING_ENTITLEMENTS, entitlements);
        else if (_arguments != null && this.getArguments().containsKey(ARG_VIOLATING_ENTITLEMENTS))
            this.getArguments().remove(ARG_VIOLATING_ENTITLEMENTS);
    }



    /**
     * Abstracting that this is actually stored as a csv
     * @param entitlementsToRemediate list of PolicyTreeNodes to encode
     * @return the csv representation
     *
     * @ignore
     * this should use JSON instead of CSV -rap
     */
    private String getCsvFromPolicyTreeNodes( List<PolicyTreeNode> entitlementsToRemediate ) {
        List<String> entitlements = new ArrayList<String>();
        for( PolicyTreeNode node : entitlementsToRemediate ) {
            String csv = getCsvFromPolicyTreeNode( node );
            entitlements.add( csv );
        }
        String resposne = Util.listToRfc4180Csv( entitlements );
        return resposne;
    }

    private String getJSONFromPolicyTreeNodes( List<PolicyTreeNode> entitlementsToRemediate ) {
        List<String> entitlements = new ArrayList<String>();
        for( PolicyTreeNode node : entitlementsToRemediate ) {
            String csv = gretJSONFromPolicyTreeNode( node );
            entitlements.add( csv );
        }
        String resposne = Util.listToCsv( entitlements );
        return resposne;
    }

    private String getCsvFromPolicyTreeNode( PolicyTreeNode node ) {
        if( !node.isLeaf() ) {
            throw new RuntimeException( "Entitlements to Remediate must be leaves" );
        }
        List<String> itemList = new ArrayList<String>( 5 );
        itemList.add( node.getApplication() );
        itemList.add( node.getName() );
        itemList.add( node.getValue() );
        itemList.add( node.getApplicationId() );
        itemList.add( ( node.isPermission() ? "true" : "false" ) );
        return Util.listToRfc4180Csv( itemList );
    }

    private String gretJSONFromPolicyTreeNode(PolicyTreeNode node) {
        if( !node.isLeaf() ) {
            throw new RuntimeException( "Entitlements to Remediate must be leaves" );
        }
        List<String> itemList = new ArrayList<String>( 5 );
        itemList.add(JsonHelper.toJson(node));
        return Util.listToCsv(itemList);
    }

    /**
     * Abstracting that this is actually stored as a csv
     * @return the List of PolicyTreeNode stored in EntitlementsMarkedForRemediation
     */
    private List<PolicyTreeNode> getPolicyTreeNodesFromCsvList() throws GeneralException {
        List<PolicyTreeNode> response = new ArrayList<PolicyTreeNode>();
        if (getEntitlementsMarkedForRemediation() != null && !getEntitlementsMarkedForRemediation().equals("")){
            for( String csv : Util.csvToList( getEntitlementsMarkedForRemediation() ) ) {
                response.add( getPolicyTreeNodeFromCsv( csv ) );
            }
        }
        return response;
    }

    private PolicyTreeNode getPolicyTreeNodeFromCsv( String csv ) throws GeneralException {
        List<String> values = Util.csvToList( csv );
        PolicyTreeNode response = null;
        if (values.size() == 1 ) {
            //JSON
            response = JsonHelper.fromJson(PolicyTreeNode.class, values.get(0));
        } else {
            /* Magic numbers match order in getCsvFromPolicyTreeNode( PolicyTreeNode )  */
            String application = "null".equals(values.get(0)) ? null : values.get(0);
            response = new PolicyTreeNode(application, values.get(1), values.get(2), values.get(3), values.get(4).equalsIgnoreCase("true"));
            if (values.size() > 5) {
                //Contains type
                response.setType(values.get(5));

                if (values.size() == 7) {
                    List<IdentitySelector.MatchTerm.ContributingEntitlement> cont = JsonHelper.listFromJson(IdentitySelector.MatchTerm.ContributingEntitlement.class, values.get(6));
                    response.setContributingEntitlements(cont);
                }
            }
        }
        return response;
    }
}
