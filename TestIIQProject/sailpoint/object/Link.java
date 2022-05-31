/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used represent accounts on applications correlated to an identity.
 *
 * Author: Jeff
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import sailpoint.api.ObjectUtil;
import sailpoint.connector.Connector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used represent accounts on applications correlated to an identity.
 */
@XMLClass
@Indexes({
  @Index(name="appIdComposite",property="application"), 
  @Index(name="appIdComposite",property="nativeIdentity", caseSensitive=false),
  @Index(name="uuidComposite",property="application"), 
  @Index(name="uuidComposite",property="uuid", caseSensitive=false)
})
public class Link extends SailPointObject
    implements Cloneable, Comparable<Link>, LinkInterface, EntitlementDataSource
{
    private static final long serialVersionUID = 3362870191826185018L;

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
     * Owning identity.
     */
    Identity _identity;

    /**
     * Application where the account resides.
     */
    Application _application;

    /**
     * For template applications, the identifier of the specific
     * instance where the account exists.
     */
    String _instance;

    /**
     * The "raw" account identity. For directories this will be the DN.
     */
    String _nativeIdentity;

    /**
     * Alternate universally unique identifier, this is common for
     * directories, RFC 4530 calls this "entryUUID" and ActiveDirectory
     * calls it "GUID".  
     */
    String _uuid;

    Boolean iiqLocked;
    Boolean iiqDisabled;

    /**
     * Alternate nice name for the account. For directories this will
     * be a simple unique identifier like samAccountName or uid.
     */
    String _displayName;

    /**
     * Flag to indicate this account has been manually correlated in the 
     * ui and the identity association should be left as is.
     */
    boolean _manuallyCorrelated;

    /**
     * Selected attribute values from the account.
     */
    Attributes<String, Object> _attributes;

    /**
     * Special transient field used during aggregation to hold
     * the previous values of the attributes. This can be used to compare
     * the old and new values to control whether certain expensive
     * operations (like manager correlation) need to happen.
     */
    Attributes<String,Object> _oldAttributes;

    /**
     * The date the account attributes were last refreshed.
     * Set by the aggregator.
     */
    Date _lastRefresh;

    /**
     * The date aggregation was last targeted - set by the target aggregator.
     */
    Date _lastTargetAggregation;


    /**
     * List of other Link ids for accounts that are considered
     * components of this composite account. This is used only
     * when aggregating with a CompositeConnector.  
     *
     * @ignore
     * We're managing this on the composite rather than having back
     * refs on each component to make it easier to remove the composite
     * links.  Also a component can be in more than one composite so
     * it couldn't be reduced to a single reference anyway.
     *
     * Keeping this as a CSV for simplicity since they're only
     * used by the Aggregator and Certificationer with the full memory 
     * model.  If these needed to be queryable (find all DB accounts
     * that are not part of stacks) then we would wnat to model this
     * differently.
     */
    String _componentIds;

    //
    // Correlation keys
    // These are pulled out of the _attributes during aggregation
    // and maintained as Hibernate columns for searching.  Note that
    // this class does not attempt to keep the keys in sync with
    // changes made in the _attributes map.  Only the aggregator should
    // be setting attribute values and keys.
    //
    
    String _key1;
    String _key2;
    String _key3;
    String _key4;

    /**
     * The date of the lastLogin
     * 
     * @ignore
     * !! we don't have any accessors for this, do we need it?
     */
    Date _lastLogin;

    private String _passwordHistory;

    /**
     * True if this link has one or more entitlement attributes.
     * Used to find links during certification creation which might otherwise
     * be uncertified.
     */
    private boolean _entitlements;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Link() 
    {
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getId())
            .append("application", ((_application != null) ? _application.getName() : ""))
            .append("identity", _nativeIdentity)
            .toString();
    }

    /**
     * These do not have names, though now that they are supported the concept
     * of non-unique names could use this for displayName.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitLink(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    // Note that this is not serialized in the XML, it is set
    // known containment in an <Identity> element.  This is only
    // here for a Hibernate inverse relationship.

    /**
     * Owning identity.
     */
    public Identity getIdentity() {
        return _identity;
    }

    public void setIdentity(Identity id) {
        _identity = id;
    }

    /**
     * Application where the account resides.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application res) {
        _application = res;
    }

    /**
     * Gets the id of the application referenced by this link.
     *
     * @return The id of the application referenced by this link
     */
    public String getApplicationId(){
        return getApplication()!=null ? getApplication().getId() : null;
    }

    /**
     * Gets the name of the application referenced by this link.
     *
     * @return The name of the application referenced by this link
     */
    @Override
    public String getApplicationName(){
        return getApplication()!=null ? getApplication().getName() : null;
    }

    /**
     * For template applications, the identifier of the specific
     * instance where the account exists.
     */
    @Override
    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String s) {
        _instance = s;
    }
        
    /**
     * The "raw" account identity. For directories this will be the DN.
     */
    @Override
    @XMLProperty(xmlname="identity")
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String id) {
        _nativeIdentity = id;
    }

    /**
     * Alternate universally unique identifier. Normally seen only with directories.
     */
    @XMLProperty
    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String id) {
        _uuid = id;
    }

    /**
     * Alternate nice name for the account. For directories this will
     * be a simple unique identifier like samAccountName or uid.
     */
    @Override
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }
        
    /**
     * The date the account attributes were last refreshed.
     * Set by the aggregator.
     */
    @Override
    @XMLProperty
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    public void setLastRefresh(Date d) {
        _lastRefresh = d;
    }

    /**
     * The date aggregation was last targeted - set by the target aggregator.
     */
    @XMLProperty
    public Date getLastTargetAggregation() {
        return _lastTargetAggregation;
    }

    public void setLastTargetAggregation(Date d) {
        _lastTargetAggregation = d;
    }
        
    /**
     * Selected attribute values from the account
     * returned by the connector or generated
     * by extended attribute rules.
     */
    @Override
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    /**
     * Special transient field used during aggregation to hold
     * the previous values of the attributes. This can be used to compare
     * the old and new values to control whether certain expensive
     * operations (like manager correlation) need to happen.
     *
     * @ignore
     * Note that these *must not* be persistent or else we'll have
     * subtle collection ownership issues in Hibernate.
     */
    public Attributes<String,Object> getOldAttributes() {
        return _oldAttributes;
    }

    public void setOldAttributes(Attributes<String,Object> atts) {
        _oldAttributes = atts;
    }

    /**
     * Flag to indicate account was manually correlated in the 
     * ui and the identity association should be left as is.
     */
    @XMLProperty
    public boolean isManuallyCorrelated() {
        return _manuallyCorrelated;
    }

    public void setManuallyCorrelated(boolean manual) {
        _manuallyCorrelated = manual;
    }

    /**
     * Flag that indicates whether or not this link contains
     * any entitlement attributes.
     * Set during entitlement correlation.
     */
    @XMLProperty
    public boolean isEntitlements() {
        return _entitlements;
    }

    public void setEntitlements(boolean entitlements) {
        this._entitlements = entitlements;
    }

    public boolean hasEntitlements(){
        return _entitlements;
    }

    /**
     * CSV of other Link ids for accounts that are considered
     * components of this composite account. This is used only
     * when aggregating with a CompositeConnector.  
     * 
     * @ignore
     * We're managing this on the composite rather than having back
     * refs on each component to make it easier to remove the composite
     * links.  Also a component can be in more than one composite so
     * it couldn't be reduced to a single reference anyway.
     *
     * Keeping this as a CSV for simplicity since they're only
     * used by the Aggregator and Certificationer with the full memory 
     * model.  If these needed to be queryable (find all DB accounts
     * that are not part of stacks) then we would want to model this
     * differently.
     */
    @XMLProperty
    public String getComponentIds() {
        return _componentIds;
    }

    /**
     * @return True if this link has at least one component link.
     */
    public boolean isComposite(){
        return _componentIds != null && Util.csvToList(_componentIds).size() > 0;
    }

    public void setComponentIds(String csv) {
        _componentIds = csv;
    }
        
    @XMLProperty
    public String getKey1() {
        return _key1;
    }

    public void setKey1(String s) {
        _key1 = s;
    }

    @XMLProperty
    public String getKey2() {
        return _key2;
    }

    public void setKey2(String s) {
        _key2 = s;
    }

    @XMLProperty
    public String getKey3() {
        return _key3;
    }

    public void setKey3(String s) {
        _key3 = s;
    }

    @XMLProperty
    public String getKey4() {
        return _key4;
    }

    public void setKey4(String s) {
        _key4 = s;
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

    //////////////////////////////////////////////////////////////////////
    //
    // ObjectConfig cache
    //
    //////////////////////////////////////////////////////////////////////

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

    /**
     * This is the accessor required by the generic support for
     * extended attributes in SailPointObject. It is NOT an XML property.
     */
    @Override
    public Map<String, Object> getExtendedAttributes() {
        return getAttributes();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        else {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, value);

            if (Connector.ATT_IIQ_LOCKED.equals(name)) {
                setIiqLocked(Util.otob(value));
            }
            else if (Connector.ATT_IIQ_DISABLED.equals(name)) {
                setIiqDisabled(Util.otob(value));
            }
        }
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public boolean getBooleanAttribute(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    @SuppressWarnings("unchecked")
    public Attributes getEntitlementAttributes() {
        Attributes attrs = new Attributes();

        // Since this is a link we are only interested in the ACCOUNT schema.
        Schema schema = null;        
        if (_application != null ) {
            schema = _application.getSchema(Connector.TYPE_ACCOUNT);
        }

        if ( (null != _attributes) && (null != schema ) ) {            
            if (null != schema.getEntitlementAttributeNames()) {
                for (String attrName : schema.getEntitlementAttributeNames()) {
                    Object val = _attributes.get(attrName);
                    if (null != val) {
                        attrs.put(attrName, val);
                    }
                }
            }            
        }

        return attrs;
    }

    /* (non-Javadoc)
     * @see sailpoint.object.LinkInterface#getDisplayableName()
     */
    @Override
    public String getDisplayableName() {

        return (_displayName != null && _displayName.trim().length() > 0) ? _displayName : _nativeIdentity;
    }

    /**
     * @exclude
     * This will no-op because displayable name is a pseudo property that
     * is maintained strictly by hibernate.
     */
    public void setDisplayableName(String displayableName) {
    }

    /**
     * Get a list of all direct, indirect, and group permissions from this Link.
     * 
     * @ignore
     * jsl - I don't know why this is named "flattened" since that isn't what it did.
     * It did not return indirect permissions or group permissions.  It returned
     * target permissions but only those directly held by the user.
     * Starting in 7.2 it no longer returns target permissions, if you need those
     * you need to query the IdentityEntitlements table.
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public List<Permission> getFlattenedPermissions()
    {
        return getPermissions();
    }

    /**
     * Get the list of direct permissions.
     */
    public List<Permission> getPermissions() {

        return (List<Permission>)getAttribute(Connector.ATTR_DIRECT_PERMISSIONS);
    }

    /**
     * Get the list of target permissions.
     *
     * @deprecated Use {@link ObjectUtil#getTargetPermissions(sailpoint.api.SailPointContext, Link)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public List<Permission> getTargetPermissions() {
        return (List<Permission>)getAttribute(Connector.ATTR_TARGET_PERMISSIONS);
    }

    /**
     * Return a single Permission object that has all the rights
     * for a given target.  
     * 
     * @ignore
     * I don't think we have ever formally
     * stated (and certainly never enforced) that there can be
     * only one Permission object per target.  In cases where
     * we need to operate on the aggregate rights list it is convenient
     * if we can collapse multiple perms down to one.
     * There should no information loss provided that the annotations
     * are the same which they should be since they apply to the
     * target, not a collection of rights.
     *
     * There may be cases where the perms are broken up just for
     * scale, since one with too many rights is hard to display.
     * We're using this in Provisioner now for simulated provisioning
     * so it shouldn't persist.  Need to think about the consequences 
     * if we use this in other contexts!
     */
    public Permission getSinglePermission(String target) {
        return getSinglePermission(this.getPermissions(), target);
    }

    /**
     * Return a single Permission object that has all the rights
     * for a given target.
     *
     * @param perms  The list of permissions in which to look for permissions with the same target.
     * @param target  The target to look for.
     */
    private static Permission getSinglePermission(List<Permission> perms, String target) {
        Permission single = null;

        if ((perms != null) && (target != null)) {
            boolean accumulating = false;

            for (Permission perm : perms) {
                if (target.equals(perm.getTarget())) {
                    if (single == null)
                        single = perm;
                    else {
                        // make a copy to avoid corrupting
                        // the original model
                        if (!accumulating) {
                            single = new Permission(perm);
                            accumulating = true;
                        }
                        single.addRights(perm.getRightsList());
                    }
                }
            }
        }

        return single;
    }

    /**
     * Put a permission on the list replacing all others with the same
     * target. Typically this will be the permission returned
     * by getSinglePermission with some modifications.
     */
    public void setSinglePermission(Permission single) {
        setSinglePermission(single, this.getPermissions(), Connector.ATTR_DIRECT_PERMISSIONS);
    }

    /**
     * Put a permission on the given list replacing all others with the same
     * target.
     */
    private void setSinglePermission(Permission single, List<Permission> perms, String permAttrName) {

        if (single != null && single.getTarget() != null) {
            // remove what's there now
            if (perms != null) {
                ListIterator<Permission> it = perms.listIterator();
                while (it.hasNext()) {
                    Permission p = it.next();
                    if (single.getTarget().equals(p.getTarget()))
                        it.remove();
                }
            }

            // don't add empty permissions
            String rights = single.getRights();
            if (rights != null && rights.length() > 0) {
                if (perms == null)
                    perms = new ArrayList<Permission>();
                perms.add(single);
            }

            setAttribute(permAttrName, perms);
        }
    }

    /**
     * Add a component link id to the list of ids.
     */
    public void addComponent(Link comp) throws GeneralException {

        // Check this in case we're dealing with a Link that  
        // Hibernate hasn't assigned an id yet.  If this can happen
        // then we'll need another way to reference these.
        String id = comp.getId();
        if (id == null)
            throw new GeneralException("Link has no id!");

        if (_componentIds == null)
            _componentIds = id;
        else if (_componentIds.indexOf(id) < 0)
            _componentIds += "," + id;

    }

    /**
     * Returns true if the account is locked. This method checks
     * for the presence of Connector.ATTR_LOCKED in the Link's
     * attribute map.
     */
    @Override
    public boolean isLocked() {
        return Util.otob(getAttribute(Connector.ATT_IIQ_LOCKED));
    }

    /**
     * Return true if the account is disabled. This method checks
     * for the presence of Connector.ATTR_DISABLED in the Link's
     * attribute map.
     */
    @Override
    @XMLProperty
    public boolean isDisabled() {
        return Util.otob(getAttribute(Connector.ATT_IIQ_DISABLED));
    }

    public Boolean getIiqLocked() {
        return iiqLocked;
    }

    public void setIiqLocked(Boolean iiqLocked) {
        this.iiqLocked = iiqLocked;
    }

    public Boolean getIiqDisabled() {
        return iiqDisabled;
    }

    public void setIiqDisabled(Boolean iiqDisabled) {
        this.iiqDisabled = iiqDisabled;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // List sorting for unit tests
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given a List<Link> sort them in a stable order.  We don't define
     * what that order is, as long as it remains the same from release
     * to release.  The Link collection implementation changed to a <bag> mapped
     * collection in 7.1 and element indexes no longer maintain
     * order of the identity link list. This method is used in unit tests that
     * make XML comparisons to previously captured files.
     *
     * @ignore
     * I'm starting with a focused approach since we're not going to have
     * many <bag>s and not many of the unit tests appear to be impacted.
     * If this becomes a chronic problem though we'll need to handle it
     * in a more generic way in the XML serializer.  Possibly have objects
     * that need to be sorted implement a SortedList interface that 
     * BuiltinSerializers can use to determine whether to sort and how.
     * Unfortunately that may require the regeneration of a large number
     * of test files.
     * 
     * That's a bit overkill for the moment so let's see if we can
     * get away with calling it selectively for tests that care.
     */
    public static void sort(List<Link> links) {
        if (links != null) {
            Collections.sort(links);
        }
    }

    /**
     * Link order is determined by application combined with nativeIdentity.
     * 
     * @ignore
     * Technically we should include instance too, but I don't believe that
     * is used by any of the unit tests.  
     * 
     * 0=equal, -1=less, 1=greater
     */
    @Override
    public int compareTo(Link other) {


        int comparison = 0;
        Application otherapp = other.getApplication();

        // tolerate missing Applications, shoudln't happen in practice
        // but unit tests don't always obey the rules
        if (_application == null) {
            if (otherapp != null)
                comparison = -1;
        }
        else if (otherapp == null) {
            comparison = 1;
        }
        else {
            comparison = _application.getName().compareTo(otherapp.getName());
        }

        if (comparison == 0) {
            // should always have a nativeIdentity
            comparison = _nativeIdentity.compareTo(other.getNativeIdentity());
        }
        
        return comparison;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            Link otherObj = (Link) o;
            return (getIdentity() == otherObj.getIdentity() || 
                    getIdentity().equals(otherObj.getIdentity())) &&
                   (getApplication() == otherObj.getApplication() ||
                    getApplication().equals(otherObj.getApplication())) &&
                   (getNativeIdentity() == otherObj.getNativeIdentity() ||
                    getNativeIdentity().equals(otherObj.getNativeIdentity())) &&
                   (getDisplayName() == otherObj.getDisplayName() ||
                    getDisplayName().equals(otherObj.getDisplayName())) &&
                   (isManuallyCorrelated() == otherObj.isManuallyCorrelated()) &&
                   (getAttributes() == otherObj.getAttributes() ||
                    getAttributes().equals(otherObj.getAttributes())) &&
                   (getLastRefresh() == otherObj.getLastRefresh() ||
                    getLastRefresh().equals(otherObj.getLastRefresh()));
        } else {
            return false;
        }
    }

    @Override
    public List<Entitlement> getEntitlements(Locale locale, String attributeFilter) throws GeneralException {

        return Entitlement.getAccountEntitlements(locale, this, attributeFilter);
    }
}
