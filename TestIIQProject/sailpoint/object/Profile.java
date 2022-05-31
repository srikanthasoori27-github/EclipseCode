/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The definition of a collection of access rights on an 
 * account on a particular application.
 * 
 * Author: Jeff
 *
 * These are the definition of the "detection rules"
 * used by the entitlement correlator.  We can also
 * derive provisioning information from them but since
 * filters can be ambiguous roles may also have
 * a ProvisioningPlan that overrides the profile filters.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of a collection of access rights on an 
 * account on a particular application. These are used
 * within {@link Bundle} classes (roles) to define
 * the detection rules.
 */
@XMLClass
public class Profile extends SailPointObject 
    implements Cloneable, Certifiable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Owning bundle.
     */
    Bundle _bundle;
     
    /**
     * The target application.
     */
    Application _application;

    /**
     * The type of account needed on this application.
     * Might not need this, but leaving a hook. The values
     * here would be application-specific.
     */
    String _accountType;

    /**
     * The account attribute values required by this account.
     * Typically you will have constraints or permissions but not both.
     */
    List<Filter> _constraints;

    /**
     * The low level application permission required by this account.
     */
    List<Permission> _permissions;

    /**
     * Extended attributes.
     */
    Attributes<String,Object> _attributes;
    


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Profile() {
    }

    /**
     * These can have names but they are optional and non-unique.
     */
    public boolean isNameUnique() {
        return false;
    }

    /**
     * @ignore
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  This is part of a performance experiment
     * in the EntitlementCorrelator, where we load all the objects
     * necessary to do the correlation, then clear the cache so as we process
     * each Identity we no longer have to deal with all the entitlement
     * definitions in the cache.
     */
    public void load() {

        // assume we don't need _approval for aggregation

        if (_application != null)
            _application.load();

        if (_constraints != null) {
            for (Filter f : _constraints)
                f.hashCode();
        }

        if (_permissions != null) {
            for (Permission p : _permissions)
                p.getTarget();
        }

    }

    public void addConstraint(Filter c) {
        if (c != null) {
            if (_constraints == null)
                _constraints = new ArrayList<Filter>();
            _constraints.add(c);
        }
    }

    public void addPermission(Permission p) {
        if (p != null) {
            if (_permissions == null)
                _permissions = new ArrayList<Permission>();
            _permissions.add(p);
        }
    }
    
    public void resetPermissions() {
        if (_permissions != null) {
            _permissions.clear();
        }
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitProfile(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    // Note that this is not serialized in the XML, it is assumed by
    // containment in an <Identity> element.  This is only
    // here for a Hibernate inverse relationship.

    /**
     * Owning role
     */
    public Bundle getBundle() {
        return _bundle;
    }

    public void setBundle(Bundle id) {
        _bundle = id;
    }
    
    
    /**
     * Returns the ordinal of this profile in relation to the Application its
     * associated to. This will return a positive value when the profile is
     * one of multiples associated to the same application. If there are no
     * other profiles for the same application, it will return 0.
     */
    public int getProfileOrdinal() {
        if (_application == null) {
            // no app, no ordinal
            return 0;
        }
        Bundle bundle = getBundle();
        if (bundle == null) {
            // no bundle, no way to get other profiles, no ordinal
            return 0;
        }
        List<Application> forApplications = new ArrayList<Application>();
        forApplications.add(_application);
        List<Profile> profiles = bundle.getProfilesForApplications(forApplications);
        int ord = 0;
        if (profiles.size() <= 1) {
            // if the profiles size is only 1, there's only one profile for
            // this application.  Return 0.
            return ord;
        }
        int count = 1;
        for (Profile profile : profiles) {
            if (this.equals(profile)) {
                ord = count;
                continue;
            }
            count++;
        }
        return ord;
    }

    /**
     * The target application
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application r) {
        _application = r;
    }

    /**
     * The type of account needed on this application.
     * The values are application-specific. This has never been used.
     */
    @XMLProperty
    public void setAccountType(String type) {
        _accountType = type;
    }

    public String getAccountType() {
        return _accountType;
    }

    /**
     * A list of filters specifying the account attribute values
     * necessary to match this role. If there is more than
     * one filter, they are AND'd. Normally there is only
     * one filter.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getConstraints() {
        return _constraints;
    }

    public void setConstraints(List<Filter> cons) {
        _constraints = cons;
    }

    /**
     * List of permissions needed to match this role.
     * Usually a role will have filters or permissions but
     * not both.  If there is more than one
     * permission on the list they are AND'd.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Permission> getPermissions() {
        return _permissions;
    }

    public void setPermissions(List<Permission> perms) {
        _permissions = perms;
    }

    /**
     * Extended attributes on the profile.
     * These are no longer used.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
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

}
