/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

/**
 * Common interface implemented by BaseBean and BaseResource.
 * This gives service classes a common interface to retrieve
 * the SailPointContext and info about the current user.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public interface UserContext {

    public SailPointContext getContext();

    public String getLoggedInUserName() throws GeneralException;

    public Identity getLoggedInUser() throws GeneralException;

    public List<Capability> getLoggedInUserCapabilities();

    public Collection<String> getLoggedInUserRights();

    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException;

    public Locale getLocale();

    public TimeZone getUserTimeZone();
    
    public boolean isMobileLogin();

     /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    public boolean isObjectInUserScope(SailPointObject object) throws GeneralException;

    /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @param id ID of the object
     * @param clazz Class of the object
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    public boolean isObjectInUserScope(String id, Class clazz) throws GeneralException;
    
    /**
     * Returns true if scoping is enabled in the system configuration
     * @return true if scoping is enabled, true if the option doesn't exist, false otherwise.
     * @throws GeneralException
     */
    public boolean isScopingEnabled() throws GeneralException;

}
