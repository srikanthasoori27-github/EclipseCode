/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.integration.Util;
import sailpoint.tools.ConvertTools;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class Target extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 1L;

    /**
     * Native owner Id that will be correlated to an Identity during 
     * the target aggregation process.
     */
    private String _ownerId;

    /**
     * Target source that aggregated this source object.
     */
    private TargetSource _targetSource;

    /**
     * Application that aggregated this source.
     *
     * @ignore
     * As of 7.2, SIQ will now use Application objects to configure Target Aggregations
     */
    private Application _application;

    /**
     * Native source in which the Target resides. This may not be modeled
     * in IIQ as an application.
     */
    private String _targetHost;

    /**
     * Display name of the Target
     */
    private String _displayName;

    /**
     * Full path of the Target
     */
    private String _fullPath;

    /**
     * MD5 hash of {@link #getUniqueName()}.  The hash is persisted because the unique name
     * can be very long (it can include the full path), and we need to be able to query on
     * this.  Note that MD5 hashes are not guaranteed to be unique with different input.  This
     * means that two different values from getUniqueName() could - although very unlikely -
     * produce the same uniqueNameHash.  When querying by this hash, the caller must also verify
     * that the returned unique name matches what they are searching for.
     */
    private String _uniqueNameHash;

    /**
     * ID of the target on the native Application. nativeObjectId+targetSource should be unique
     */
    private String _nativeObjectId;

    /**
     * Parent of this Target
     */
    private Target _parent;

    /**
     * Size of the target
     */
    private long _targetSize;

    /**
     * Date of the last Aggregation
     */
    private Date _lastAggregation;

    /**
     * Extended attributes.
     * This is used for two things, the extended Hibernate attributes that
     * will be promoted to columns for searching, and a few internal attributes
     * such as the list of localized descriptions.
     */
    Attributes<String, Object> _attributes;

    /**
     * Transient list of AccessMappings, which includes mapping of rights 
     * to native user ids.
     */
    private List<AccessMapping> _accountAccess;

    /**
     * Transient list of AccessMappings, which includes mapping of rights 
     * to native group ids.
     */
    private List<AccessMapping> _groupAccess;


    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Target() {
        _ownerId = null;
        _targetHost = null;
        _accountAccess = null;
	    _groupAccess = null;
	    _targetSource = null;
        _displayName = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////


    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        this._displayName = displayName;
    }

    public String getDisplayableName() {
        if (Util.isNotNullOrEmpty(_displayName)) {
            return _displayName;
        } else {
            return _name;
        }
    }

    @XMLProperty
    public String getFullPath() { return this._fullPath; }

    public void setFullPath(String path) { this._fullPath = path; }

    /**
     * Set the unique hash on this Target.  This must be called before any Target is persisted to
     * the database, since this is used to lookup Targets/TargetAssociations for a given target.
     */
    public void assignUniqueHash() {
        _uniqueNameHash = createUniqueHash(this.getUniqueName());
    }

    /**
     * Create a hash from the given unique name string.
     *
     * @param s  The unique name of the Target.
     *
     * @return A hash for the given unique name.
     */
    public static String createUniqueHash(String s) {
        return ConvertTools.convertStringToMD5String(s);
    }

    /**
     * Return the name that can uniquely identify this Target within a TargetSource/Application.
     * This is used as the "target" of a Permission when the converting these to Permission objects,
     * and is also stored as the name of the IdentityEntitlement.
     */
    public String getUniqueName() {
        return createUniqueName(_name, _fullPath, _targetHost);
    }

    /**
     * Create a name that can uniquely identify a Target within a TargetSource/Application.
     *
     * @param name  The name of the Target.
     * @param fullPath  The fullPath of the Target - possibly null.
     * @param targetHost  The targetHost of the Target - possibly null.
     *
     * @return A name that can uniquely identify a Target within a TargetSource/Application.
     */
    public static String createUniqueName(String name, String fullPath, String targetHost) {
        // KG - Target Host is not currently used since older Targets and related models (Permissions,
        // IdentityEntitlements, Certifications, etc...) did not include the targetHost in the name.
        // Talked to Ryan, and this may eventually go away if we end up making the SIQ target collector
        // be a multiplexing app rather than using target host.

        // Use the fullPath if available, otherwise use the name.
        return (null != fullPath) ? fullPath : name;
    }

    /**
     * @deprecated This is kept to allow deserializing old XML.  Use {@link #getUniqueNameHash()} instead.
     */
    @XMLProperty(legacy=true) @Deprecated
    public void setFullPathHash(String hash) {
        setUniqueNameHash(hash);
    }

    @XMLProperty
    public String getUniqueNameHash() {
        return _uniqueNameHash;
    }

    public void setUniqueNameHash(String hash) {
        this._uniqueNameHash = hash;
    }

    @XMLProperty
    public String getNativeOwnerId() {
        return _ownerId;
    }

    public void setNativeOwnerId(String owner) {
        _ownerId = owner;
    }

    @XMLProperty
    public String getTargetHost() {
        return _targetHost;
    }

    public void setTargetHost(String targetHost) {
        _targetHost = targetHost;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="TargetSourceRef")
    public TargetSource getTargetSource() {
        return _targetSource;
    }

    public void setApplication(Application app) {
        _application = app;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setTargetSource(TargetSource targetSource) {
        this._targetSource = targetSource;
    }

    @XMLProperty
    public String getNativeObjectId() {
        return this._nativeObjectId;
    }

    public void setNativeObjectId(String id) {
        this._nativeObjectId = id;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Target getParent() {
        return this._parent;
    }

    public void setParent(Target targ) {
        this._parent = targ;
    }

    @XMLProperty
    public long getTargetSize() { return this._targetSize; }

    public void setTargetSize(long size) { this._targetSize = size; }

    @XMLProperty
    public Date getLastAggregation() {
        return _lastAggregation;
    }

    public void setLastAggregation(Date _lastAggregation) {
        this._lastAggregation = _lastAggregation;
    }

    /**
     * Return the extended attributes map.
     * For group attributes, this can also contain things defined
     * in the group schema of the Application
     */
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {

        return _attributes;
    }

    /**
     * Set the extended attributes map.
     * This is normally called only from the XML parser. Application code should
     * get/set attributes one at a time.
     */
    public void setAttributes(Attributes<String, Object> atts) {

        _attributes = atts;
    }

    /**
     * Return the value of an extended attribute. For groups,
     * this can also return the value of a schema attribute.
     */
    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    /**
     * Set the value of an extended attribute. For groups,
     * this can also set the value of a schema attribute.
     */
    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();
        _attributes.put(name, value);
    }

    /**
     * This is the accessor required by the generic support for extended
     * attributes in SailPointObject. It is NOT an XML property.
     */
    public Attributes<String, Object> getExtendedAttributes() {
        return getAttributes();
    }

    @XMLProperty(mode=SerializationMode.CANONICAL)
    public List<AccessMapping> getAccountAccess() {
        return _accountAccess;
    }

    public void setAccountAccess(List<AccessMapping> mapping) {
        _accountAccess = mapping;
    }

    public void addAccountAccess(AccessMapping mapping) {
        if (getAccountAccess() == null) {
            setAccountAccess(new ArrayList<AccessMapping>());
        }
        getAccountAccess().add(mapping);
    }

    @XMLProperty(mode=SerializationMode.CANONICAL)
    public List<AccessMapping> getGroupAccess() {
        return _groupAccess;
    }

    public void setGroupAccess(List<AccessMapping> mapping) {
        _groupAccess = mapping;
    }

    public void addGroupAccess(AccessMapping mapping) {
        if (getGroupAccess() == null) {
            setGroupAccess(new ArrayList<AccessMapping>());
        }
        getGroupAccess().add(mapping);
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitTarget(this);
    }

    /**
     * Target does not have unique name. Name can be the same across targetHosts
     *
     * @ignore
     * We currently use name to store the full_path. Would be nice to use full_path to store the full_path
     * and use name to store the name of a directory/share
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * A static configuration cache used by the persistence manager to make
     * decisions about searchable extended attributes.
     */
    static private CacheReference objectConfig;

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (objectConfig == null)
            objectConfig = ObjectConfig.getCache(Target.class);

        if (objectConfig != null)
            config = (ObjectConfig)objectConfig.getObject();

        return config;
    }
}
