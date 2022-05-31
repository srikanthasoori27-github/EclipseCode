/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An EntitlementGroup holds a logical group of low-level entitlements on a
 * specific account on an application. This can be used in different contexts,
 * for example, holding exceptional entitlements, holding mappings from a job
 * function to which entitlements granted the business role, etc.
 */
@XMLClass
public class EntitlementGroup
    extends SailPointObject
    implements Entitlements, EntitlementDataSource
{
    private static final long serialVersionUID = 9206381251090681349L;
    
    private static final Log log = LogFactory.getLog(EntitlementGroup.class);
            
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private Application application;
    private String instance;
    private String nativeIdentity;
    private String displayName;
    private List<Permission> permissions;
    private Attributes<String,Object> attributes;
    private boolean accountOnly;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public EntitlementGroup() {}

    /**
     * Constructor.
     */
    public EntitlementGroup(Application application, String instance,
                            String nativeIdentity,
                            String displayName,
                            List<Permission> permissions,
                            Attributes<String,Object> attributes)
    {
        if ( application == null ) {
            if (log.isErrorEnabled())
                log.error("Application reference on EntitlementGroup is null. " +
                          "Nativeidentity [" + nativeIdentity + "]");
        }
        setApplication(application);
        setInstance(instance);
        setNativeIdentity(nativeIdentity);
        setDisplayName(displayName);
        setPermissions(permissions);
        setAttributes(attributes);
    }

    public EntitlementGroup(Application application, String instance,
                            String nativeIdentity,
                            String displayName)
    {
        this(application, instance, nativeIdentity, displayName, new ArrayList<Permission>(), new Attributes<String,Object>());
        setAccountOnly(true);
    }

    /**
     * Construct from an EntitlementSnapshot.
     * @ignore 6.0 uses getApplicationObject which always queries by name
     */
    public EntitlementGroup(Resolver resolver, EntitlementSnapshot es)
        throws GeneralException
    {
        this(es.getApplicationObject(resolver),
             es.getInstance(), es.getNativeIdentity(), es.getDisplayName(), 
             es.getPermissions(), es.getAttributes());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * EntitlementGroups can be named, but are not necessarily unique.
     */
    @Override
    public boolean isNameUnique()
    {
        return false;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes()
    {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes)
    {
        this.attributes = attributes;
    }

    public void setAttribute(String name, Object value) {
        if (name != null) {
            if (this.attributes == null)
                this.attributes = new Attributes<String,Object>();
            if (value == null)
                this.attributes.remove(name);
            else
                this.attributes.put(name, value);
        }
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Permission> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions)
    {
        this.permissions = permissions;
    }
    
    public void add(Permission p) {
        if (p != null) {
            if (this.permissions == null)
                this.permissions = new ArrayList<Permission>();
            this.permissions.add(p);
        }
    }

    @XMLProperty
    public String getInstance() {
        return this.instance;
    }

    public void setInstance(String ins) { 
        this.instance = ins;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return this.nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) { 
        this.nativeIdentity = nativeIdentity;
    }

    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @XMLProperty
    public boolean isAccountOnly() {
        return accountOnly;
    }

    public void setAccountOnly(boolean accountOnly) {
        this.accountOnly = accountOnly;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication()
    {
        return application;
    }

    public void setApplication(Application application)
    {
        this.application = application;
    }

    /*
     * (non-Javadoc)
     * @see sailpoint.object.Entitlements#getApplicationObject()
     */
    public Application getApplicationObject(Resolver resolver) {
        return this.application;
    }

    /*
     * (non-Javadoc)
     * @see sailpoint.object.Entitlements#getApplicationName()
     */
    public String getApplicationName() {
        return (null != this.application) ? this.application.getName() : null;
    }

    /**
     * Cannot seem to get the list of map Attribute map keys from JSF
     * by using "item.attributes.keys" where "item" is an EntitlementGroup
     * and Attribute.getKeys is defined to convert the keySet into a List<String>.
     * The thought is that as soon as it notices that Attributes is a Map, you
     * cannot call getter methods. So something will be provided here.
     */
    public List<String> getAttributeNames() {
        return ((attributes != null) ? attributes.getKeys() : null);
    }

    public boolean isEmpty() {
        return !accountOnly && Util.isEmpty(permissions) && Util.isEmpty(attributes);
    }

    /**
     * Create a cloned EntitlementGroup that has the given permissions and
     * attributes.
     */
    public Entitlements create(List<Permission> perms,
                               Attributes<String, Object> attrs) {
        return new EntitlementGroup(this.application, this.instance,
                                    this.nativeIdentity, this.displayName,
                                    perms, attrs);
    }
    
    /**
     * Convenience method to convert from an EntitlementGroup currently
     * coming out of the correlator to a snapshot.
     * Note that the attributes and permissions
     * list must be cloned to avoid a Hibernate warning if these came from an Identity.
     */
    public EntitlementSnapshot convertToSnapshot() {
    
        EntitlementSnapshot s = new EntitlementSnapshot();
        if (this.application != null)
            s.setApplication(this.application.getName());
        
        s.setInstance(this.instance);
        s.setNativeIdentity(this.nativeIdentity);
        s.setDisplayName(this.displayName);
        s.setAccountOnly(this.accountOnly);

        // Assume we don't need a deep copy, we would have
        // taken ownership of the groups anyway?
        // No, these appear to be often owned by an Identity.
        // The Permissions list is the only one we need this for,
        // attributes are serialized to a Map.

        s.setPermissions(Permission.clone(getPermissions()));
        s.setAttributes(getAttributes());

        return s;
    }
    
    /**
     * Utility method used to determine how many distinct values this EntitlementGroup contains.
     * This is useful when making decisions about splitting queries into manageable chunks
     * @return number of distinct values in this entitlementGroup
     */
    public int getValueCount() {
        int numValues = 0;
        Attributes<String,Object> values = getAttributes();
        List<Permission> permissions = getPermissions();

        if (!Util.isEmpty(values)) {
            for (Object value : values.values()) {
                List splitValue = Util.asList(value);
                numValues += Util.size(splitValue);
            }
        }

        if ( Util.size(permissions) > 0 ) {
            for (Permission permission : permissions) {
                numValues += Util.size(permission.getRightsList());
            }
        }
        return numValues;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = super.hashCode();
        result = PRIME * result + ((application == null) ? 0 : application.hashCode());
        result = PRIME * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = PRIME * result + ((instance == null) ? 0 : instance.hashCode());
        result = PRIME * result + ((nativeIdentity == null) ? 0 : nativeIdentity.hashCode());
        result = PRIME * result + ((permissions == null) ? 0 : permissions.hashCode());
        result = PRIME * result + ((displayName == null) ? 0 : displayName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final EntitlementGroup other = (EntitlementGroup) obj;
        if (application == null) {
            if (other.application != null)
                return false;
        } else if (!application.equals(other.application))
            return false;
        if (displayName == null) {
            if (other.displayName != null)
                return false;
        } else if (!displayName.equals(other.displayName))
            return false;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        } else if (!attributes.equals(other.attributes))
            return false;
        if (instance == null) {
            if (other.instance != null)
                return false;
        } else if (!instance.equals(other.instance))
            return false;
        if (nativeIdentity == null) {
            if (other.nativeIdentity != null)
                return false;
        } else if (!nativeIdentity.equals(other.nativeIdentity))
            return false;
        if (permissions == null) {
            if (other.permissions != null)
                return false;
        } else if (!permissions.equals(other.permissions))
            return false;
        return true;
    }

    public List<Entitlement> getEntitlements(Locale locale, String permissionFilter) throws GeneralException {

        return Entitlement.getPermissionEntitlements(this.getApplication(), 
                                                     this.getPermissions(), 
                                                     locale, permissionFilter);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // UTILITIES - These are really common utilities for all Entitlements
    // implementations.  I just stuck them here because I didn't want to create
    // a separate utility class with just a couple methods.
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Split up the given list of Entitlements so that every resulting
     * Entitlement contains a single value.
     */
    public static List<Entitlements> splitToValues(List<? extends Entitlements> ents) {
        return EntitlementGroup.splitToValues(ents, false);
    }

    /**
     * Split up the given list of Entitlements so that every resulting Entitlement contains a single value
     * @param ents - List of entitlements to split
     * @param excludePermissions - Used to only fetch the entitlement values instead of entitlement and permissions
     * @return A split list of entitlements so that every resulting Entitlement contains a single value
     */
    public static List<Entitlements> splitToValues(List<? extends Entitlements> ents, boolean excludePermissions) {

        List<Entitlements> split = new ArrayList<Entitlements>();
        
        for (Entitlements ent : ents) {
            Attributes<String,Object> attrs = ent.getAttributes();
            if (null != attrs) {
                for (Map.Entry<String,Object> entry : attrs.entrySet()) {
                    if (null != entry.getValue()) {
                        if (entry.getValue() instanceof Iterable) {
                            for (Object o : (Iterable<?>) entry.getValue()) {
                                Attributes<String,Object> newAttrs =
                                    new Attributes<String,Object>();
                                newAttrs.put(entry.getKey(), o);
                                split.add(ent.create(null, newAttrs));
                            }
                        }
                        else {
                            Attributes<String,Object> newAttrs =
                                new Attributes<String,Object>();
                            newAttrs.put(entry.getKey(), entry.getValue());
                            split.add(ent.create(null, newAttrs));
                        }
                    }
                }
            }

            if(!excludePermissions) {
                List<Permission> perms = ent.getPermissions();
                if (null != perms) {
                    for (Permission perm : perms) {
                        List<String> rightsList = perm.getRightsList();
                        if (null != rightsList) {
                            for (String right : rightsList) {
                                List<String> newRights = new ArrayList<String>();
                                newRights.add(right);
                                List<Permission> newPerms = new ArrayList<Permission>();
                                newPerms.add(new Permission(newRights, perm.getTarget(), perm.getAttributes()));
                                split.add(ent.create(newPerms, null));
                            }
                        }
                    }
                }
            }
        }

        return split;
    }
    /**
     * Split up the given list of Entitlements so that every resulting
     * Entitlement contains a single attribute or permission (which can have
     * multiple values/rights).
     */
    public static List<Entitlements> splitToAttributes(List<? extends Entitlements> ents) {
        
        List<Entitlements> split = new ArrayList<Entitlements>();

        for (Entitlements ent : ents) {
            Attributes<String,Object> attrs = ent.getAttributes();
            if (null != attrs) {
                for (Map.Entry<String,Object> entry : attrs.entrySet()) {
                    if (null != entry.getValue()) {
                        Attributes<String,Object> newAttrs = new Attributes<String,Object>();
                        newAttrs.put(entry.getKey(), entry.getValue());
                        split.add(ent.create(null, newAttrs));
                    }
                }
            }

            List<Permission> perms = ent.getPermissions();
            if (null != perms) {
                for (Permission perm : perms) {
                    List<Permission> newPerms = new ArrayList<Permission>();
                    newPerms.add(perm);
                    split.add(ent.create(newPerms, null));
                }
            }
        }

        return split;
    }
}
