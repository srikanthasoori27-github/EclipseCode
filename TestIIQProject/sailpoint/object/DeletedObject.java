/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used represent deleted accounts and groups on applications supporting "manageRecycleBin" attribute.
 *
 */

package sailpoint.object;


import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used represent deleted accounts and groups on applications supporting recycle bin.
 */

@XMLClass
@Indexes({
  @Index(name="appIdCompositeDelObj",property="application"), 
  @Index(name="appIdCompositeDelObj",property="nativeIdentity", caseSensitive=false),
  @Index(name="uuidCompositeDelObj",property="application"), 
  @Index(name="uuidCompositeDelObj",property="uuid", caseSensitive=false)
})
public class DeletedObject extends SailPointObject
    implements Cloneable {
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
     * Application where the account resides.
     */
    Application _application;
 

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

    /**
     * Alternate nice name for the account. For directories this will
     * be a simple unique identifier like samAccountName or uid.
     */
    String _Name;
  

    /**
     * Selected attribute values from the account.
     */
    Attributes<String, Object> _attributes;
   
    /**
     * Deleted object type either "account" or "group"
     *  
     */
    String _objectType;

   /**
    *  Deleted Object's last updated time stamp.
    */
    
    Date _lastRefresh; 


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public DeletedObject() 
    {
    }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getId())
            .append("application", ((_application != null) ? _application.getName() : ""))
            .append("identity", _nativeIdentity)
            .toString();
    }

    /**
     * These do not have names, though now that the concept
     * of non-unique names is supported, this could be used for displayName.
     */
    @Override
    public boolean hasName() {
        return true;
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
     * Gets the name of the application referenced by this Deleted Object.
     *
     * @return The name of the application referenced by this Deleted Object
     */
    public String getApplicationName(){
        return getApplication()!=null ? getApplication().getName() : null;
    }    
        
    /**
     * The "raw" account identity. For directories this will be the DN.
     */
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
     * Deleted object type. it is account/group.
     */
    
    @XMLProperty
    public String getObjectType() {
        return _objectType;
    }

    public void setObjectType(String objectType) {
    	_objectType = objectType;
    }  

    /**
     * Selected attribute values from the account
     * returned by the connector or generated
     * by extended attribute rules.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }     
   
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String Name) {
        _name = Name;
    }
    
    @XMLProperty
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    public void setLastRefresh(Date D) {
    	_lastRefresh = D;
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
        }
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }
 
    public String getDisplayableName() {

        return (_name != null && _name.trim().length() > 0) ? _name : _nativeIdentity;
    }

    /**
     * This will no-op because displayable name is a pseudo property that
     * is maintained strictly by hibernate.
     */
    public void setDisplayableName(String displayableName) {
    }

  
    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
