/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Holds a collection of entitlements for an application
 * inside an IdentitySnapshot.
 *
 * Author: Jeff
 *
 * This is practically identical to EntitlementGroup, except that
 * we're "archival" and maintain soft references to things.
 * Would be nice if this could be refactored to share something
 * besides just the Entitlements interface...
 *
 * This is also used inside EntitlementCollection which is the 
 * model used by EntitlementCorrelator to manage matched entitlements
 * when detecting roles.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;

import sailpoint.api.EntitlementDescriber;
import sailpoint.object.Certification.EntitlementGranularity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Holds a collection of entitlements for an application.
 * This is almost identical to <code>EntitlementGroup</code>
 * but this is used in "archival" objects and as such it has
 * no direct references to other objects, in this case
 * an <code>Application</code>.
 *
 * Primarily used inside <code>IdentitySnapshot</code> and 
 * <code>RoleDetection</code>.
 * 
 */
@XMLClass
public class EntitlementSnapshot extends AbstractXmlObject 
    implements Entitlements, Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -6082225349593935203L;

    /**
     * @exclude
     * A comparator that checks summaries - ONLY SHOULD BE USED IN UNIT TESTS.
     * jsl - this should not be having dependencies outside the object package.
     */
    public static final Comparator<EntitlementSnapshot> COMPARATOR =
        new Comparator<EntitlementSnapshot>() {
            public int compare(EntitlementSnapshot o1, EntitlementSnapshot o2) {
                return Util.nullSafeCompareTo(getKey(o1), getKey(o2));
            }
            
            private String getKey(EntitlementSnapshot es) {
                String s = null;
                if (null != es) {
                    s = es.getApplication() + ":" + es.getInstance() + ":" +
                        es.getNativeIdentity() + ":" + EntitlementDescriber.summarize(es);
                }
                return s;
            }
    };

    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Id for hibernate when used within an CertificationItem.
     */
    String _id;

    /**
     * The name of the application at the time of this snapshot.
     */ 
    private String _application;
    private String _instance;
    private String _nativeIdentity;

    /**
     * Display name for the account. This is used in cases where the
     * nativeIdentity is not user-friendly.
     */
    private String _displayName;
    private boolean _accountOnly;
    private List<Permission> _permissions;
    private Attributes<String,Object> _attributes;

    /**
     * The cached resolved Application.
     * Used in a few places to avoid database lookups when
     * we've already fetched the object.
     * This is *not* persistent.
     */
    Application _resolvedApplication;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public EntitlementSnapshot() {
        this(null, null, null, null);
    }

    public EntitlementSnapshot(String app, String instance, 
                               String nativeIdentity, String displayName) {
        this(app, instance, nativeIdentity, displayName, null, null);
    }

    public EntitlementSnapshot(String app, String instance, 
                               String nativeIdentity,
                               String displayName,
                               List<Permission> perms,
                               Attributes<String,Object> attrs) {
        _application = app;
        _instance = instance;
        _nativeIdentity = nativeIdentity;
        _permissions = perms;
        _attributes = attrs;
        _displayName = displayName;
    }

    public EntitlementSnapshot(Application app, String instance,
                               String nativeIdentity,
                               String displayName,
                               List<Permission> perms,
                               Attributes<String,Object> attrs) {
        this(app.getName(), instance, nativeIdentity, displayName, perms, attrs);
        _resolvedApplication = app;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void setId(String s) {
        _id = s;
    }

    public String getId() {
        return _id;
    }

    /** 
     * Returns the Attributes of the Link at the time
     * of this snapshot.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attrs) {
        _attributes = attrs;
    }

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String instance) { 
        _instance = instance;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) { 
        _nativeIdentity = nativeIdentity;
    }

    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        this._displayName = displayName;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String name) {
        _application = name;
    }

    /*
     * (non-Javadoc)
     * @see sailpoint.object.Entitlements#getApplicationName()
     */
    public String getApplicationName() {
        return _application;
    }

    /*
     * (non-Javadoc)
     * @see sailpoint.object.Entitlements#getApplicationObject()
     */
    public Application getApplicationObject(Resolver resolver)
        throws GeneralException {

        return resolver.getObjectByName(Application.class, _application);
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Permission> getPermissions() {
        return _permissions;
    }

    public List<Permission> getPermissionsByTarget(String target){

        List<Permission> foundPerms = new ArrayList<Permission>();
        if (target != null && _permissions != null){
            for(Permission p : _permissions){
                if(p.getTarget().equals(p.getTarget()))
                    foundPerms.add(p);
            }
        }

        return foundPerms;
    }

    @XMLProperty
    public boolean isAccountOnly() {
        return _accountOnly;
    }

    public void setAccountOnly(boolean accountOnly) {
        _accountOnly = accountOnly;
    }

    public void setPermissions(List<Permission> permissions) {
        _permissions = permissions;
    }

    public boolean isEmpty() {
        return !_accountOnly && Util.isEmpty(_permissions) && Util.isEmpty(_attributes);
    }

    public String getDisplayableName(){
        return _displayName != null ? _displayName : _nativeIdentity;
    }

    public void load() {
        if (_permissions != null)
            for (Permission permission : _permissions) {
            	permission.getTarget();
            }

        if (_resolvedApplication != null)
        	_resolvedApplication.load();
    }
    
    
    /**
     * @ignore
     * Can't seem to get the list of map Attribute map keys from JSF
     * by using "item.attributes.keys" where "item" is an EntitlementGroup
     * and Attribute.getKeys is defined to convert the keySet into a List<String>.
     * My guess is that as soon as it notices that Attributes is a Map, you
     * can't call getter methods.  So we'll provide something here.
     */
    public List<String> getAttributeNames() {
        return ((_attributes != null) ? _attributes.getKeys() : null);
    }

    public Application resolveApplication(Resolver r) 
        throws GeneralException {
        
        if (_resolvedApplication == null && _application != null)
            _resolvedApplication = r.getObjectByName(Application.class, _application);

        return _resolvedApplication;
    }

    /**
     * Create a cloned EntitlementSnapshot that has the given permissions and
     * attributes.
     */
    public Entitlements create(List<Permission> perms,
                               Attributes<String,Object> attrs) {
        return new EntitlementSnapshot(_application, _instance, _nativeIdentity,
                                       _displayName, perms, attrs);
    }
    
    @Override
    public Object clone() {
        return new EntitlementSnapshot(_application, _instance, _nativeIdentity, _displayName,
                                       Permission.clone(_permissions),
                                       _attributes);
    }

    /**
     * Determines whether or not this snapshot contains a single attribute
     * or permission value. This means that a query can be used to search
     * for related certification or identity history items by attribute
     * and value.
     */
    public boolean isValueGranularity(){
        return EntitlementGranularity.Value.equals(this.estimateGranularity());
    }

    /**
     * Determines whether or not this snapshot contains a single attribute
     * or permission. This means that a query can be used to search
     * for related certification or identity history items by attribute only.
     */
    public boolean isAttributeGranularity(){
        return EntitlementGranularity.Attribute.equals(this.estimateGranularity());
    }

    /**
     * There are times when an estimate is done to determine what the
     * granularity of a certification <i>probably</i> was in the absence of the certification
     * itself (for example - archiving of the parent certification), as determined by the 
     * data in the entitlement snapshot. The known issue here is that for a certification 
     * item from an "application" granularity certification that has a single 
     * attribute and a single value. It will appear to be from a "value" 
     * granularity certification, but there is no way to know otherwise with certainty.
     * 
     * @return The estimated EntitlementGranularity
     */
    @SuppressWarnings("rawtypes")
    public EntitlementGranularity estimateGranularity() {

        EntitlementGranularity granularity = null;
        if (Util.size(getPermissions()) + Util.size(getAttributeNames()) == 1) {  
            
            List valsOrRights = null;
            if (hasAttributes()) {
               String attr = getAttributes().getKeys().get(0);
               valsOrRights = Util.asList(getAttributes().get(attr));
            }
            
            if (hasPermissions()) {
                valsOrRights = getPermissions().get(0).getRightsList();
            }
            
            if (Util.size(valsOrRights) == 1)
                granularity = EntitlementGranularity.Value;
            else
                granularity = EntitlementGranularity.Attribute;
        } else {
            granularity = EntitlementGranularity.Application;
        }
        
        return granularity;
    }

    public boolean hasAttributes(){
        return getAttributes() != null && !getAttributes().isEmpty();
    }

    public boolean hasPermissions(){
        return getPermissions() != null && !getPermissions().isEmpty();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /*
     * (non-Javadoc)
     * @see sailpoint.object.Entitlements#getApplicationObject()
     */
    public EntitlementSnapshot convertToSnapshot() {
        return this;
    }

    /**
     * Return true if the snapshot contains a given permission 
     * target and right. Helper for EntitlementScorer.
     */
    public boolean hasPermission(String target, String right) {

        boolean found = false;
        if (_permissions != null && target != null && right != null) {
            for (Permission p : _permissions) {
                if (p.hasTarget(target) && p.hasRight(right)) {
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Return true if the snapshot contains a given attribute value.
     * Helper for EntitlementScorer.
     */
    @SuppressWarnings("unchecked")
    public boolean hasAttribute(String name, String value) {

        boolean found = false;
        if (_attributes != null && name != null && value != null) {
            Object o = _attributes.get(name);
            if (o instanceof Collection) 
                found = ((Collection)o).contains(value);
            else if (o != null)
                found = o.equals(value);
        }
        return found;
    }
    
    /**
     * Return the name of the single attribute of this EntitlementSnapshot.
     * This throws an exception if there is more than the one attribute in this
     * snapshot.
     * 
     * @return The name of the single attribute of this EntitlementSnapshot.
     *         
     * @throws GeneralException  If there is more than the one attribute.
     */
    public String getAttributeName() throws GeneralException {

        if ((null == _attributes) || (1 != _attributes.size()) ||
            ((null != _permissions) && !_permissions.isEmpty())) {
            throw new GeneralException("Expected a single attribute.");
        }

        return _attributes.getKeys().get(0);
    }

    /**
     * Return the single value of the single attribute in this snapshot. This
     * throws an exception if there is more than just the single value.
     */
    @SuppressWarnings("unchecked")
    public Object getAttributeValue() throws GeneralException {

        List vals = Util.asList(_attributes.get(getAttributeName()));
        if ((null == vals) || (1 != vals.size())) {
            throw new GeneralException("Expected a single value");
        }

        return vals.get(0);
    }       

    /**
     * Return the values for all attributes in this snapshot.
     */
    @SuppressWarnings("unchecked")
    public List<Object> getAttributeValues() throws GeneralException {
        List vals = new ArrayList<String>();

        if (!Util.isEmpty(_attributes)) {
            for (String attributeName : Util.iterate(_attributes.keySet())) {
                //IIQTC-60 :- Avoiding to add a null list to another list.
                List<Object> list = Util.asList(_attributes.get(attributeName));
                if (list != null) {
                    vals.addAll(list);
                }
            }
        }

        return vals;
    }


    /**
     * Return the target of the single permission of this EntitlementSnapshot.
     * This throws an exception if there is more than the one permission in this
     * snapshot.
     * 
     * @return The target of the single permission of this EntitlementSnapshot.
     *         
     * @throws GeneralException  If there is more than the one permission.
     */
    public String getPermissionTarget() throws GeneralException {

        assertSinglePermission();
        return _permissions.get(0).getTarget();
    }

    /**
     * Return the single right of the single permission in this snapshot. This
     * throws an exception if there is more than just the single right.
     */
    public String getPermissionRight() throws GeneralException {

        assertSinglePermission();
        List<String> vals = _permissions.get(0).getRightsList();
        if ((null == vals) || (1 != vals.size())) {
            throw new GeneralException("Expected a single right");
        }

        return vals.get(0);
    }
    
    /**
     * Return the a comma-separated list of all of the rights of this snapshot.
     */
    public String getPermissionRights() throws GeneralException {

        assertSinglePermission();
        List<String> vals = _permissions.get(0).getRightsList();
        if ((null != vals)) {
            return Util.listToCsv(vals);
        }
        else return null;
    }

    /**
     * Assert that this EntitlementSnapshot contains a single permission and no
     * attributes.
     */
    private void assertSinglePermission() throws GeneralException {
    
        if ((null == _permissions) || (1 != _permissions.size()) ||
            ((null != _attributes) && !_attributes.isEmpty())) {
            throw new GeneralException("Expected a single permission.");
        }
    }

    /**
     *  Convenience method which returns true if this entitlement references the given link.
     *
     * @param link Link to compare. Returns false if link is null, link applicationName is null or
     * if link nativeIdentity is null.
     * @return  True if this entitlement references the given link.
     */
    public boolean referencesLink(LinkInterface link){
        if (link == null || link.getApplicationName() == null || link.getNativeIdentity() == null)
            return false;

        String linkInstance = link.getInstance();

        return link.getApplicationName().equals(getApplicationName()) &&
            link.getNativeIdentity().equals(getNativeIdentity()) &&
            ((linkInstance == null && _instance == null) ||
             (linkInstance != null && linkInstance.equals(_instance)));
                                                   
    }

    /**
     * Get a unique identifier useful for identifying accounts in the UI 
     * @return Key to represent account
     */
    public String generateAccountKey(){

        String instance = getInstance() != null ? getInstance() : "";
        String key = getApplicationName() + "_" + getNativeIdentity() + "_" + instance;

        return key;
    }

    /**
     * Helper that returns a collection of all of the applications that are
     * represented in the given list of snapshots.
     * 
     * @param  snaps  A possibly-null collection of EntitlementSnapshots.
     * 
     * @return A non-null collection of all of the applications that are
     *         represented in the given list of snapshots.
     */
    public static Collection<String> getApplications(Collection<EntitlementSnapshot> snaps) {
    
        Set<String> apps = new HashSet<String>();
        if (null != snaps) {
            for (EntitlementSnapshot snap : snaps) {
                apps.add(snap.getApplication());
            }
        }
        return apps;
    }

    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("id", _id);
        builder.append("application", _application);
        builder.append("permissions", _permissions);
        builder.append("attributes", _attributes);
        
        return builder.toString();
    }
}
