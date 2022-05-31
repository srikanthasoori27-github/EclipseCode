/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;
import java.io.Serializable;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object which represents a definition of a Link 
 * a given point in time.
 * 
 * This object is serialized as xml and is a private object of
 * IdentitySnapshot object.
 */
@XMLClass
public class LinkSnapshot
    implements LinkInterface, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The hibernate ID of the source link for this snapshot. 
     * It is being stored so that the components of this link,
     * which are stored as hibernate IDs, can be accessed.
     */
    private String _id;

    /**
     * The name of the application at the time of this snapshot.
     */ 
    private String _application;

    /**
     * The instance identifier for template applications.
     */ 
    private String _instance;

    /**
     * The "nice" name of this account. Normally the same
     * as _nativeIdentity but for directories will be simpler.
     */
    private String _simpleIdentity;

    /**
     * The native identity of the object on the application.
     */ 
    private String _nativeIdentity; 

    /**
     * Attributes from the application. (entitlements)
     */ 
    private Attributes _attributes;

    /**
     * The date this link was last refreshed using application data.
     */ 
    private Date _lastRefresh;

    /**
     * List of other LinkSnapshot ids for accounts that are considered
     * components of this composite account. This is used only
     * when aggregating with a CompositeConnector.
     */
    private String _componentIds;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public LinkSnapshot() {
    }

    public LinkSnapshot(Link src) {
        assimilate(src);
    }

    @XMLProperty
    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    /**
     * Returns the Attributes of the Link at the time
     * of this snapshot.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes attrs) {
        _attributes = attrs;
    }

    @XMLProperty
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    public void setLastRefresh(Date refreshDate) {
        _lastRefresh = refreshDate;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String name) {
        _application = name;
    }

    /**
     * Gets the name of the application referenced by this link. 
     *
     * @return Name of the application referenced by this link
     */
    public String getApplicationName() {
        return getApplication();
    }

    @XMLProperty
    public String getSimpleIdentity() {
        return _simpleIdentity;
    }

    public void setSimpleIdentity(String identity ) {
        _simpleIdentity = identity;
    }

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String ins) {
        _instance = ins;
    }

    @XMLProperty(xmlname="identity")
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String identity ) {
        _nativeIdentity = identity;
    }

    @XMLProperty
    public String getComponentIds() {
        return _componentIds;
    }

    public void setComponentIds(String csv) {
        _componentIds = csv;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Link Interface
    //
    //////////////////////////////////////////////////////////////////////


    public String getDisplayName() {
        return _simpleIdentity;
    }
    
    public String getDisplayableName() {
        return (null != _simpleIdentity) ? _simpleIdentity : _nativeIdentity;
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

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Copy the relevant fields from a Link.
     * @ignore
     * For attributes we might want to do a deep copy, but when
     * used within IdentityArchiver it is safe since we don't
     * try to modify anything.
     */
    public void assimilate(Link link) {

        if (link != null) {
            _id = link.getId();

            Application app = link.getApplication();
            if (app != null)
                _application = app.getName();

            _instance = link.getInstance();
            _nativeIdentity = link.getNativeIdentity();
            _lastRefresh = link.getLastRefresh();
            _attributes = link.getAttributes();
            _componentIds = link.getComponentIds();

            // displayName is overloaded in Links to hold the
            // "simple" name for directory accounts.  
            _simpleIdentity = link.getDisplayName();
        }
    }

}
