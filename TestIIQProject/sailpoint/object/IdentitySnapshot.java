/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * These are created by the IdentityArchiver during aggregation or refresh.
 *
 * Author: Jeff
 */

package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.Indexes;
import sailpoint.tools.Index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * A class used to hold historical information about identities.
 * It contains a simplified representation of the full identity cube
 * and can be used to track the roles and exceptional entitlements
 * an identity has had over time.
 * 
 * The createDate of the SailPoint object stores the date of each
 * snapshot.
 */
@Indexes({@Index(property="created")})
@XMLClass
public class IdentitySnapshot extends SailPointObject implements Cloneable {
    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The length at which we should truncate a field.
     */
    private static final int MAX_FIELD_LENGTH = 2000;

    /**
     * The original unique id of the identity.
     * This may no longer be valid if the identity was deleted.
     * You should use this when trying to find the snapshots
     * associated with an identity, just in case the identity was
     * renamed.
     */
    private String _identityId;

    /**
     * The name of the Identity, cannot keep reference since these will
     * outlive the actual Identity object.
     */
    private String _identityName;

    /**
     * Flat list of csv separated applicationNames.
     */
    private String _applications;

    /**
     * The scorecard at the time.
     * @ignore
     * TODO: If we need to do score trend analysis, we'll 
     * have to keep Scorecard histories around.
     */
    private Scorecard _scorecard;

    /**
     * Identity attributes. These are the ones stored directly
     * on the Identity, not the Link attributes.
     */
    Attributes<String,Object> _attributes;

    /**
     * Collection of bundle snapshots.
     */
    List<BundleSnapshot> _bundles;

    /**
     * Collection of assigned role snapshots.
     */
    List<RoleAssignmentSnapshot> _assignedRoles;

    /**
     * Collection of exception snapshots.
     */
    List<EntitlementSnapshot> _exceptions;

    /**
     * Collection of link snapshots.    
     */
    List<LinkSnapshot> _links;

    /**
     * Collection of policy violations.
     * 
     * @ignore
     * If we keep violations around forever to do trend analysis
     * then we don't necessarily have to capture them here.  But they
     * will then need a way to reference the associated IdentitySnapshot,
     * or we need to associate them by date range.
     */
    List<PolicyViolation> _violations;

    /**
     * A string summarizing the contents of the snapshot, intended
     * for fast display in a summary table.  
     * 
     * @ignore
     * It is unclear what we want here, though a list of the business 
     * role names at the time of the snapshot seems useful.
     */
    String _summary;

    /**
     * A string summarizing the differences between this identity
     * and the last one. This can be optionally calculated during
     * an identity refresh and intended for display in the identity list.
     * Since it is displayed in the result of a projection search,
     * it cannot be calculated on the fly, it has to be pre-calculated and
     * left as a string.  
     *
     * @ignore
     * This sucks for I18N since we have to localize it during
     * refresh.  We could store it in some kind of delimited format and
     * let the UI localize it when the table is rendered.
     *
     */
    String _differences;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public IdentitySnapshot() {
        _identityName = null;
        _applications = null;
    }

    /**
     * These might have names, but they are not unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns the identity being referenced by this snapshot.
     */
    @XMLProperty
    public String getIdentityId() {
        return _identityId;
    }

    /**
     * Sets the identity being referenced by this snapshot.
     */
    public void setIdentityId(String name) {
        _identityId = name;
    }

    /**
     * Returns the identity being referenced by this snapshot.
     */
    @XMLProperty
    public String getIdentityName() {
        return _identityName;
    }

    /**
     * Sets the identity being referenced by this snapshot.
     */
    public void setIdentityName(String name) {
        _identityName = name;
    }

    /**
     * CSV of application names. The names are taken from 
     * the profiles of all detected roles at the time of the snapshot.
     */
    @XMLProperty
    public String getApplications() {
        return _applications;
    }

    public void setApplications(String names) {
        _applications = names;
    }

    /** 
     * Returns the extended attributes of the Identity at the time
     * of this snapshot.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attrs) {
        _attributes = attrs;
    }

    /**
     * Returns a List of LinkSnapshots which represent the Links for
     * this identity at the time of this snapshot.
     */
    @XMLProperty(xmlname="LinkSnapshots")
    public List<LinkSnapshot> getLinks() {
        return _links;
    }

    public void setLinks(List<LinkSnapshot> links) {
        _links = links;
    }

    /**
     * Returns a list of BundleSnapshots representing the
     * detected roles.
     */
    @XMLProperty(xmlname="BundleSnapshots")
    public List<BundleSnapshot> getBundles() {
        return _bundles;
    }
    public void setBundles(List<BundleSnapshot> bundles) {
        _bundles = bundles;
    }

    /**
     * Returns a list of RoleAssignmentSnapshot representing the
     * assigned roles.
     */
    @XMLProperty(xmlname="AssignedRoleSnapshots")
    public List<RoleAssignmentSnapshot> getAssignedRoles() {
        return _assignedRoles;
    }
    
    public void setAssignedRoles(List<RoleAssignmentSnapshot> assignedRoles) {
        _assignedRoles = assignedRoles;
    }

    /**
     * Returns a list of objects representing the exceptional entitlements.
     */
    @XMLProperty(xmlname="ExceptionSnapshots")
    public List<EntitlementSnapshot> getExceptions() {
        return _exceptions;
    }

    public void setExceptions(List<EntitlementSnapshot> exceptions) {
        _exceptions = exceptions;
    }

    /**
     * Returns the scorecard at the time of snapshot.
     */
	@XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Scorecard getScorecard() {
        return _scorecard;
    }

    public void setScorecard(Scorecard card) {
        _scorecard = card;
    }

    /**
     * Returns the policy violations at the time of snapshot.
     */
    @XMLProperty(xmlname="PolicyViolations")
    public List<PolicyViolation> getViolations() {
        return _violations;
    }

    public void setViolations(List<PolicyViolation> violations) {
        _violations = violations;
    }

    /**
     * A string summarizing the contents of the snapshot, intended
     * for fast display in a summary table.  
     */
    @XMLProperty
    public String getSummary() {
        return _summary;
    }

    /**
     * Required by the persistence frameworks.
     * 
     * @deprecated  Instead use {@link #setSummaryAndTruncate(String)}.  
     */
    @Deprecated
    public void setSummary(String s) {
        _summary = s;
    }

    /**
     * A string summarizing the differences between this identity snapshot
     * and the last one. This can be optionally calculated during
     * an identity refresh and intended for display in the identity list.
     */
    @XMLProperty
    public String getDifferences() {
        return _differences;
    }

    public void setDifferences(String diffs) {
        _differences = diffs;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set the summary.  If the summary is greater than {@link #MAX_FIELD_LENGTH}, this
     * truncates the value and appends an ellipsis.
     * 
     * @param  summary  The summary to set.
     */
    public void setSummaryAndTruncate(String summary) {
        //Taking into consideration multibyte characters for the sake of DB2.
        //2 bytes per char is more than enough (most chars will be 1 byte).
        //Also subtracting 3 in case our ellipsis is double the length (not likely, but let's be paranoid!).
        summary = Util.truncate(summary, MAX_FIELD_LENGTH/2 - 3);
        setSummary(summary);
    }

    /**
     * Set the application summary string. If the string is greater than 
     *  {@link #MAX_FIELD_LENGTH}, this  truncates the value and appends 
     * an ellipsis.
     * 
     * @param  applications The applications to set.
     */
    public void setApplicationsAndTruncate(String applications) {
        applications = Util.truncate(applications, MAX_FIELD_LENGTH);
        setApplications(applications);
    }


    /**
     * Set the differences string. If the string is greater than 
     *  {@link #MAX_FIELD_LENGTH}, this  truncates the value and appends 
     * an ellipsis.
     * 
     * @param  differences The differences to set.
     */
    public void setDifferencesAndTruncate(String differences) {
        differences = Util.truncate(differences, MAX_FIELD_LENGTH);
        setApplications(differences);
    }

    /** 
     * Returns the names of the detected roles stored on this snapshot.
     */
    public List<BundleSnapshot> getBusinessRoles() {

        // Originally bundles had a type field and not all bundles
        // were necessarily representing business roles.  Now we only
        // have business roles so we don't need to filter.

        return getBundles();
    }


    /**
     * Returns the composite link for the given component link.
     *
     * @param componentLink Component LinkSnapshot
     * @return Composite link or null if not found.
     */
    public LinkSnapshot getOwningCompositeLink(LinkSnapshot componentLink) {

        LinkSnapshot composite = null;

        if (null != componentLink) {
            for (LinkSnapshot link : _links) {
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
     * @param appName Name of the application
     * @return Returns all link snapshots for the given application.
     */
    public List<LinkSnapshot> getLinks(String appName) {

       return getLinks(appName, false);
    }

    /**
     * Returns all links which reference a given application. If
     * includeCompositeLinks is true, the owning composite links
     * will also be returned.
     *
     * @param appName Name of the application
     * @param includeCompositeLinks True if composite links should be returned.
     * @return Returns all link snapshots for the given application, plus composite
     * links if includeCompositeLinks is true;
     */
    public List<LinkSnapshot> getLinks(String appName, boolean includeCompositeLinks) {

        List<LinkSnapshot> found = null;
        if (appName != null && _links != null) {
            for (LinkSnapshot l : _links) {
                if (appName.equals(l.getApplication())) {
                    if (found == null)
                        found = new ArrayList<LinkSnapshot>();
                    found.add(l);
                }
            }
        }

        List<LinkSnapshot> composites = new ArrayList<LinkSnapshot>();
        if (found != null && includeCompositeLinks){
            for(LinkSnapshot link : found){
                LinkSnapshot owningCompositeLink = this.getOwningCompositeLink(link);
                if (owningCompositeLink != null)
                    composites.add(owningCompositeLink);
            }
        }

        if (found!=null)
            found.addAll(composites);       
        return found;
    }

    public LinkSnapshot getLink(String appName, String instance, String identity) {

        LinkSnapshot found = null;
        if (appName != null && _links != null) {
            for (LinkSnapshot l : _links) {
                if (appName.equals(l.getApplication())) {

                    String linkInstance = l.getInstance();
                    if ((instance == null && linkInstance == null) ||
                        (instance != null && instance.equals(linkInstance))) {

                        if (identity == null ||
                            identity.equals(l.getNativeIdentity())) {

                            found = l;
                            break;
                        }
                    }
                }
            }
        }

        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("identityName", "Identity");
        cols.put("created", "Created");
        cols.put("id", "Id");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-24s %s\n";
    }

}
