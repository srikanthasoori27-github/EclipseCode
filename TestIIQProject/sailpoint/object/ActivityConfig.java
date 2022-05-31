/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.HashSet;
import java.util.Set;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

// TODO: Need to this utility to diff a list, need
// to move this to the object package or add a DiffUtil!
import sailpoint.api.Differencer;


/**
 * An object that describes activity monitoring configuration. 
 * There are two levels of enablement, a global "all ON" flag 
 * and enablement at  the application level.   One of these objects
 * can be assigned to either the individual Identity or to 
 * a role.
 *
 * @ignore
 * TODO: these really should store application references.
 */
@XMLClass
public class ActivityConfig extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flag to indicate that ALL applications should be enabled.
     */
    private boolean _enableAll;

    /** 
     * A set of application id's that should be tracking activity data.
     */
    private Set<String> _enabledApplications;

    public ActivityConfig() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns a list of the currently enabled application ids.
     */
    @XMLProperty(mode=SerializationMode.SET)
    public Set<String> getEnabledApplications() {
        return _enabledApplications;
    }

    /**
     * Set the list of the currently enabled application ids.
     */
    public void setEnabledApplications(Set<String> enabled) {
        _enabledApplications = enabled;
    }

    /**
     * Set the top level boolean to enable all applications. This will indicate
     * to the activity aggregator when true to ignore the individual
     * application assignments.
     */
    public void setAllEnabled(boolean enableAll) { 
        _enableAll = enableAll;
    }

    /**
     * Returns true if all applications activities should be stored.
     */
    @XMLProperty
    public boolean isAllEnabled() { 
        return _enableAll;
    }
    public boolean enableAll() { 
        return isAllEnabled();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    //////////////////////////////////////////////////////////////////////

    public void addApplication(String id) {
        if (id != null) {
            if (_enabledApplications == null)
                _enabledApplications = new HashSet<String>();
            if (!_enabledApplications.contains(id))
                _enabledApplications.add(id);
        }
    }

    public void removeApplication(String id) {
        if (id != null && _enabledApplications != null) {
            _enabledApplications.remove(id);
        }
    }

    /**
     * Convenience method to clear any enabled application ids.
     */
    public void clearApplications() {
        setEnabledApplications(null);
    }

    /**
     * Remove a single application from the enabled list.
     */
    public void disableApplication(Application app) {
        if ( app != null )
            removeApplication(app.getId());
    }

    /**
     * Add a single application from the enabled list. This
     * method only adds id's that do not already exist in the 
     * enabled list.
     */
    public void enableApplication(Application app) {
        if ( app != null )
            addApplication(app.getId());
    }

    /**
     * Returns true if the given application id found in the configured
     * list of enabled applications.
     */
    public boolean isEnabled(String id) {

        boolean enabled = _enableAll;
        if (!enabled && id != null && _enabledApplications != null)
            enabled = _enabledApplications.contains(id);

        return enabled;
    }

    /**
     * Returns true if the given Application is enabled.
     * Search using both the id and the name, the name seems to be required
     * by some older unit tests.    
     */
    public boolean isEnabled(Application application) {
        
        boolean enabled = _enableAll;
        if (!enabled && application != null && _enabledApplications != null) {

            enabled = _enabledApplications.contains(application.getId());
            if (!enabled)
                enabled = _enabledApplications.contains(application.getName());
        }
        
        return enabled;
    }

    /**
     * Old name, should use isEnabled instead.
     * Returns true if the given application is found in the configured
     * list of enabled applications.  It also returns true if the 
     * enableAll flag is set to true.
     */
    public boolean enabled(Application application) {
        if ( application != null ) {
            if ( enableAll() ) return true;

            String appId = application.getId();
            Set<String> enabledApps = getEnabledApplications();
            if ( ( enabledApps != null ) && ( enabledApps.size() > 0 ) ) {
                if ( enabledApps.contains(appId) ) {
                    return true;
                } else {
                    // check names too for tests
                    String appName = application.getName();
                    if ( enabledApps.contains(appName) ) {
                        return true;
                    } 
                }
            }
        }
        return false;
    }

    /**
     * Returns true if there is one ore more applications enabled
     * for this configuration.  Also returns true if the enableAll
     * flag indicates all application activities should be tracked.
     */
    public boolean enabled() {
        Set<String> enabledApps = getEnabledApplications();
        if ( ( enableAll() ) ||
             ( enabledApps != null ) && ( enabledApps.size() > 0 ) ) {
            return true;
        }
        return false;
    }

    /**
     * @ignore
     * jsl - have to implement a "deep equal" since it is stored
     * as an XML type. 
     */
    public boolean equals(Object o) {

        ActivityConfig other = (ActivityConfig)o;

        // !! pull the Differencer over into the object package
        // we need it here.

        return ((_enableAll == other._enableAll) &&
                Differencer.equal(_enabledApplications, other._enabledApplications));
    }




}
