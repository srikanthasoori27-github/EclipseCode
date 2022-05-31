package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;

import java.util.List;
import java.util.Locale;

/**
 * Context to provide information to the AdditionalQuestionsService
 */
public interface AdditionalQuestionsContext {

    /**
     * Get the SailPointContext
     * @return SailPointContext
     */
    public SailPointContext getContext();

    /**
     * Get the locale 
     * @return Locale
     */
    public Locale getLocale();

    /**
     * Get the logged in user
     * @return Identity
     * @throws GeneralException
     */
    public Identity getLoggedInUser() throws GeneralException;
    
    /**
     * Gets a list of dynamic scope names for the logged in user
     * @return List of dynamic scope names for the logged in user
     * @throws GeneralException
     */
    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException;

    /**
     * List of properties to pull off the permitted role objects
     * @return List of strings
     * @throws GeneralException
     */
    public List<String> getPermittedRoleProperties() throws GeneralException;

    /**
     * Name of the quicklink to drive the request authorities
     * @return
     */
    public String getQuickLink();

    
}