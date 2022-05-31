/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base class for JSF pages in the SailPoint Administration application.
 * Extends PageCodeBase which provides generic tools for JSF pages.
 *
 * This was originally more complicated, but complex object management
 * stuff was factored out into BaseObjectBean.
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.Version;
import sailpoint.api.Notary;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.ScopeService;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItemArchive;
import sailpoint.server.CsrfService;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.service.PageAuthenticationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.extjs.GridResponseSortInfo;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.WorkItemUtil;

public class BaseBean extends PageCodeBase implements UserContext
{
	
	
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	/** simple method generating a string that alternates between odd or even */
	private boolean _oddOrEven;

	private static Log log = LogFactory.getLog(BaseBean.class);	

    /**
     * The context used for persistent object access.
     */
    private transient SailPointContext _context;

    /**
     * A cached version of the logged in user.
     */
    private transient Identity _loggedInUser;
    
    private boolean hasHelpEmail = true;
    private String helpContactemail = null;

    private Boolean skipAuthorization;
    
    private Notary _notary;
    
    private String _signaturePass;
    
    private String _signatureAuthId;

    private boolean forceScopeCheck = true;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public BaseBean() {
        initFacesVariables();
        skipAuthorization = Boolean.FALSE;
    }

    @Override
    public SailPointContext getContext()
    {
        if (null == _context) {
            this.initFacesVariables();
        }
        try {
            if(_context.isClosed()) {
                _context = SailPointFactory.getCurrentContext();
            }
        } catch (Throwable t) {
            log.error("Exception acquiring current context: " + t.getMessage(), t);
        }
        return _context;
    }
    
    /**
     * A unit test interface to set a context when we're not
     * in an app server environment. This isn't the only thing you need,
     * the backing bean has to be careful about bypassing access to the
     * HTTPSession.
     *
     * Originally this was named the obvious "setContext" but the method
     * then started being called during certain page transitions.  From
     * bug@9870:
     *
     *   Every time I get to the forgot password page to enter auth
     *   question answers I'm seeing the following warning in std out.  
     *   It appears again when the screen to change password loads.
     *
     * Now the curious thing is that there are no calls to this method
     * other than in a special unit test constructor in WorkItemFormBean.
     * It appears that since we provide a getContext() method, the JSF
     * infrastructure is looking for a corresponding bean property
     * setter method named setContext() and calling it sometimes.
     * I didn't track down why just the password pages do this, but whatever
     * the reasons it is dangerous to let this be called unintentionally
     * so the name was changed.
     */
    public void setTestContext(SailPointContext ctx) {
        if (_context != null)
            log.warn("Replacing context in BaseBean!\n");
        _context = ctx;
    }

    /**
     * Simple method to help generate alternating CSS styles.
     * 
     * @return alternating "Odd" and "Even"
     */
    public String getOddOrEven() {
    	return (this._oddOrEven = !this._oddOrEven) ? "Odd" : "Even";
    }

    /**
     * Simple method to help generate alternating CSS styles.
     * 
     * @return alternating true and false
     */
    public boolean getOddOrEvenBoolean() {
        return (this._oddOrEven = !this._oddOrEven);
    }

    /**
     * Simple method to help generate alternating CSS styles - this does not
     * toggle the odd/even status.  This is helpful when multiple rows should be
     * grouped together in an odd/even band.
     */
    public boolean getOddOrEvenBooleanNoFlip() {
        return this._oddOrEven;
    }

	@Override
	protected void initFacesVariables() {
		super.initFacesVariables();
        try {
            // this must have been set up by a Filter by now
            _context = SailPointFactory.getCurrentContext();
            log.debug("SailPointContext = " + _context);
        }
        catch (Exception e) {
            log.error(e);
        }
	}

    @Override
    protected void clearTransientFields() {
        super.clearTransientFields();
        _context = null;
        _loggedInUser = null;
    }

    /**
     * Return the Identity for the user that is currently logged in.
     * 
     * @return The Identity for the user that is currently logged in.
     * 
     * @throws GeneralException  If a user is not logged in.
     */
    @Override
    public Identity getLoggedInUser() throws GeneralException
    {
        if (null == _loggedInUser) {
            String userName = (String)getSessionScope().
                                      get(PageAuthenticationFilter.ATT_PRINCIPAL);

            if ( userName != null && userName.length() > 0 )
                _loggedInUser = getContext().getObjectByName(Identity.class, userName);

            if (null == _loggedInUser)
                throw new GeneralException("User is not logged in.");
        } else {
            // reattach this object in case a decache has occurred
            _loggedInUser = ObjectUtil.reattach(getContext(), _loggedInUser);
        }

        return _loggedInUser;
    }
    
    public boolean isSystemAdmin() throws GeneralException {
    	return Capability.hasSystemAdministrator(getLoggedInUser().getCapabilityManager().getEffectiveCapabilities());
    }

    public boolean isLoggedIn() {
        Object principal = getSessionScope().
                                  get(PageAuthenticationFilter.ATT_PRINCIPAL);
        if ( principal != null )
            return true;

        return false;
    }

    /**
     * Make sure this syncs with the SailPoint.Platform.isMobile() method.
     *
     * @return True if this the client is a mobile browser.
     */
    public boolean isMobile() {
        HttpServletRequest request =
            (HttpServletRequest) getFacesContext().getExternalContext().getRequest();
        String userAgent = request.getHeader("user-agent");

        boolean isMobile = false;
        if(Util.isNotNullOrEmpty(userAgent)) {
            if (userAgent.contains("iPhone") || userAgent.contains("iPad") ||
                userAgent.contains("iPod") || userAgent.contains("Android")) {
                isMobile = true;
            }
        }

        return isMobile;
    }

    /**
     * Is the user loged into the mobile side of the application?
     *
     * @return true if logged in to mobile side of site
     */
    @Override
    public boolean isMobileLogin() {
        return PageAuthenticationFilter.isMobileLogin(getSessionScope());
    }

    /**
     * Used in a few cases where we want to tag things with the user
     * name, but don't need to fetch the whole Identity.
     */
    @Override
    public String getLoggedInUserName() throws GeneralException
    {
        String userName = (String)
            getSessionScope().get(PageAuthenticationFilter.ATT_PRINCIPAL);

        return userName;
    }

    public List<String> getLoggedInUsersWorkgroupNames() 
        throws GeneralException {

        List<String> names = new ArrayList<String>();
        Identity id = getLoggedInUser();
        if ( id != null ) {
             List<Identity> groups = id.getWorkgroups();
            if  ( Util.size(groups) > 0 )  {
                for ( Identity wg : groups ) {
                    String wgName = wg.getName();
                    if  ( wgName != null ) {
                        names.add(wgName);
                    }
                }
            }
        }
        return (names.size() > 0) ? names : null;
    }

    /**
     * Return the list of capabilities held by the logged in user.
     * 
     * NOTE: We're incrementally adding authorization to the tabs and
     * menu items.  Since we're not caching the menus, we cache the      
     * capabilities on the HttpSession so we don't have to keep fetching
     * the Identity.  It would be nice to work from a declarative
     * model that defines the menus items and required capabilities, then 
     * filters that rather than building one from scratch every time.
     *
     * We're assuming this is cached by PageAuthenticationFilter.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Capability> getLoggedInUserCapabilities() {

        List<Capability> capabilities = null;
        Object o = getSessionScope().get(PageAuthenticationFilter.ATT_CAPABILITIES);
        if (o instanceof List)
            capabilities = (List<Capability>) o;

        return capabilities;
    }

    /**
     * Return the list of capabilities held by the logged in user as SelectItems.
     *
     */
    @SuppressWarnings("unchecked")
    public SelectItem[] getAllCapabilityItems() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        List<Capability> capabilities = getContext().getObjects(Capability.class, qo);
        List<SelectItem> items = new ArrayList<SelectItem>();

        for (Capability capability : Util.iterate(capabilities)) {
            if (capability.getName() != null) {
                String label = capability.getDisplayName();
                if (label == null) {
                    label = capability.getName();
                }
                items.add(new SelectItem(capability.getName(), getMessage(label)));
            }
        }

        // Sort in memory since the labels are i18n'ized.
        Collections.sort(items, new SelectItemByLabelComparator(getLocale()));

        return items.toArray(new SelectItem[items.size()]);
    }

    /**
     * Return the list of the names of the rights held by the logged in user.
     * See note in getLoggedInUserCapabilities() for more details.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getLoggedInUserRights() {

        Collection<String> rights = null;
        Object o = getSessionScope().get(PageAuthenticationFilter.ATT_RIGHTS);
        if (o instanceof Collection)
            rights = (Collection<String>) o;

        return rights;
    }
    
    /**
     * Provides a map of identity attributes used for authentication. Note that these
     * are not related to normal identity attributes, but could include any value.
     *
     * @return Map of identity authentication attributes
     */
    public Map<String, Object> getIdentityAuthorizationAttributes(){
        Object o = getSessionScope().get(PageAuthenticationFilter.ATT_ID_AUTH_ATTRS);
        if (o!=null)
            return (Map<String, Object>)o;

        return null;
    }

    protected boolean validateUserAccess(SailPointObject object) throws GeneralException{

        if (object==null)
            return true;

        if (!isAuthorized(object)){
          log.error("User '"+getLoggedInUserName()+"' attempted to access " + getClass(object).getName() +
                  ", id:"+ object.getId() + ", which was not in an authorized scope.");
          return false;
        }

        return true;
    }

    /**
     * Ensures that user should have access the the given object
     * referenced by this bean. By default only checks to make
     * sure the object is in a scope controlled by the user.
     *
     * @param object
     * @return True if the user is authorized to view object.
     */
    protected boolean isAuthorized(SailPointObject object) throws GeneralException {

        if (skipAuthorization != null && skipAuthorization) {
            return true;
        }

        // if object is null, or if id null or empty(object was just created)
        if (object == null || object.getId()==null || "".equals(object.getId())) {
            return true;
        }

        Identity user = getLoggedInUser();

        if (Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities())) {
            return true;
        }

        if (user.equals(object.getOwner())) {
            return true;
        }

        Identity owner = object.getOwner();
        List<Identity> workgroups = (user != null) ? user.getWorkgroups() : null;
        if ( owner != null && Util.size(workgroups) > 0 )  {
            for ( Identity workgroup : workgroups ) {
                if ( workgroup.equals(owner) ) {
                    return true;
                }
            }
        }
        
        // WorkItemArchives do not have 'owners', so we have to compare
        // the archive ownerName with the user's group names to determine authorization.
        if(owner == null && object instanceof WorkItemArchive) {
            //
            // IIQBUGS-32 logged-in users with WorkItemAdministrator capability are authorized to view all work items
            //
            if (Capability.hasCapability(WorkItemUtil.WORK_ITEM_ADMINISTRATOR_CAPABILITY, user.getCapabilities())) {
                return true;
            }

            String ownerName = ((WorkItemArchive)object).getOwnerName();
            // First check against the user name
            if(ownerName != null && ownerName.equalsIgnoreCase(user.getName())) {
                return true;
            }
            // Then check against the user's group names
            if ( !"".equals(ownerName) && Util.size(workgroups) > 0 )  {
                for ( Identity workgroup : workgroups ) {
                    if ( workgroup.getName().equals(ownerName) ) {
                        return true;
                    }
                }
            }
        }

        /* If we are forcing the scope check or scoping is enabled return
         * if the object is in scope, otherwise we have gotten this far
         * without a positive so return false */
        if(this.forceScopeCheck || isScopingEnabled()) {
            return isObjectInUserScope(object);
        }
        return false;
    }

    /**
     * Need to use this method for DTOs. Just delegating for now before
     * moving this method to baseDTO
     * @param object
     * @return
     * @throws GeneralException
     */
    public boolean checkAuthorization(SailPointObject object) throws GeneralException {
        return isAuthorized(object);
    }


    public void setSkipAuthorization(Boolean skipAuthorization) {
        this.skipAuthorization = skipAuthorization;
    }

    protected Boolean isSkipAuthorization() {
        return skipAuthorization;
    }

    /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.  If the object has no
     * assigned scope, return true.
     *
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    @Override
    public boolean isObjectInUserScope(SailPointObject object) throws GeneralException{
        return isObjectInUserScope(object.getId(), getClass(object));
    }

    /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @param id ID of the object
     * @param clazz Class of the object
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    @Override
    public boolean isObjectInUserScope(String id, Class clazz) throws GeneralException{
        // this is a new object, no auth needed
        if (id==null || "".equals(id))
            return true;
        
        // no assigned scope = no auth needed
        try {
            SailPointObject obj = (SailPointObject)clazz.newInstance();
            if (!obj.hasAssignedScope()) {
                return true;
            }
        } catch (Exception e) {
            throw new GeneralException("Couldn't get a new instance of class: " + 
                clazz.getCanonicalName(), e);
        }
        
        QueryOptions scopingOptions = new QueryOptions();
        scopingOptions.setScopeResults(true);
        scopingOptions.add(Filter.eq("id", id));

        // add scope extensions for any special scoping rules implemented by subclasses
        addScopeExtensions(scopingOptions);

        // Getting the class straight off the object may return a hibernate
        // proxy.  For now, use the Hibernate utility class to strip the class
        // out of its proxy.
        int count = getContext().countObjects(clazz, scopingOptions);
        return (count > 0);
    }

    /**
     * Returns true if scoping is enabled in the system configuration
     * @return true if scoping is enabled, true if the option doesn't exist, false otherwise.
     * @throws GeneralException
     */
    @Override
    public boolean isScopingEnabled() throws GeneralException {
        Configuration config = getContext().getObjectByName(Configuration.class,
                Configuration.OBJ_NAME);        
        return config.getBoolean(Configuration.SCOPING_ENABLED, true);
    }
    
    /**
     * Returns true if unscoped object is globally accessible in the system configuration
     * @return true if unscoped object is globally accessible is enabled, true if the option doesn't exist, false otherwise.
     * @throws GeneralException
     */
    public boolean isUnscopedObjGlobal() throws GeneralException {
        Configuration config = getContext().getObjectByName(Configuration.class,
                Configuration.OBJ_NAME);        
        return config.getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, true);
    }
    
    /**
     * Template method used to define any changes needed to the
     * object query when we verify that the current object is
     * in the user's scope. This is only used when calling
     * BaseObjectBean#isObjectInUserScope.
     *
     * @return Updated QueryOptions
     */
    protected QueryOptions addScopeExtensions(QueryOptions ops){
        return ops;
    }

    /**
     * Used in delete object methods to determine if an object is referenced
     * by another object.
     *
     * @param cls the class to check
     * @param attrName the reference attribute in the class
     * @param id the id of the referenced object
     * @return the number of items of type cls that have id as a value of the
     *         attrName attribute
     */
    protected int countParents(Class<? extends SailPointObject> cls,
                               String attrName, String id) {
        int count = 0;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq(attrName + ".id", id));

        try {
            count = getContext().countObjects(cls, ops);
        } catch (GeneralException ex) {
            String msg = "Unable to count " + cls.getName() + " objects.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, ex), null);
        }

        return count;
    } // findParentNames()


    // ////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectConfig getIdentityConfig() 
        throws GeneralException {

        return ObjectConfig.getObjectConfig(Identity.class);
    }
    
    public ObjectConfig getBundleConfig() 
        throws GeneralException {

        return ObjectConfig.getObjectConfig(Bundle.class);
    }
    
    public ObjectConfig getEntitlementConfig() 
        throws GeneralException {

        return ObjectConfig.getObjectConfig(ManagedAttribute.class);
    }

    public UIConfig getUIConfig() 
        throws GeneralException {
        return UIConfig.getUIConfig();
    }

    public ObjectConfig getLinkConfig() 
        throws GeneralException {

        return ObjectConfig.getObjectConfig(Link.class);
    }
    
    @SuppressWarnings("unchecked")
    public boolean isErrorMessage() {
        boolean hasError = false;
        for(Iterator<FacesMessage> messages = getFacesContext().getMessages(); messages.hasNext(); ) {
            FacesMessage msg = messages.next();
            if (msg.getSeverity().equals(FacesMessage.SEVERITY_ERROR))
                hasError = true;            
        }
        return hasError;
    }

    public boolean isSsoAuthenticated() {
        return Util.otob(getSessionScope().get(PageAuthenticationService.ATT_SSO_AUTH));
    }

    /** Returns whether the Life-Cycle Manager is enabled **/
    public boolean isLcmEnabled() {
        return Version.isLCMEnabled();
    }

    /**
     * Returns whether Privileged Account Management is enabled
     * @return
     */
    public boolean isPamEnabled() {
        return Version.isPAMEnabled();
    }
    

    /**
     * @return Help email address or null if none has been set.
     * @throws GeneralException
     */
    public String getHelpContact() throws GeneralException{

        if (hasHelpEmail && helpContactemail == null){

            Configuration sysConfig = getContext().getConfiguration();
            if (sysConfig.containsAttribute(Configuration.HELP_EMAIL_ADDRESS) &&
                    sysConfig.get(Configuration.HELP_EMAIL_ADDRESS) != null)
                helpContactemail = sysConfig.get(Configuration.HELP_EMAIL_ADDRESS).toString();
        }

        return helpContactemail;
    }
    
    public String getDefaultLanguage() throws GeneralException {

        Configuration config = getContext().getConfiguration();
        String lang = config.getString(Configuration.DEFAULT_LANGUAGE);
        if (lang == null || lang.length() == 0) {
            // default to the first supported
            List<String> langs = getSupportedLanguageList();
            if (langs != null && langs.size() > 0)
                lang = langs.get(0);
        }

        return lang;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh an object reference.  The given object may have been left
     * behind from a previous request and is no longer in the 
     * Hibernate session. The returned object will be in the current session.
     * The return may be null if the object was deleted.
     */
    public SailPointObject refresh(SailPointObject src) 
        throws GeneralException {
        SailPointObject fresh = null;
        if (src != null && src.getId() != null) {
            fresh = getContext().getObjectById(getClass(src), src.getId());
        }
        return fresh;
    }

    /**
     * Refresh a list of object references.
     */
    public void refresh(List objects) throws GeneralException {
        if (objects != null) {
            for (int i = 0 ; i < objects.size() ; i++) {
                Object o = objects.get(i);
                if (o instanceof SailPointObject) {
                    objects.set(i, refresh((SailPointObject)o));
                }
                // !! if it is null need to remove the element
            }
        }
    }

    /**
     * Extremely rare instanceof Hibernate leaking into the UI layer
     * so we don't have to put another method in SailPointContext
     * or make the UI dependent HibernatePersistenceManager.
     *
     * This has to be used whenever you want to get a Class to
     * pass into a SailPointContext method using the getClass
     * method of a SailPointObject instance.  For example:
     *
     *    con.getObject(something.getClass(), ...
     *
     * Because Hibernate uses CGLib to wrap proxy classes areound
     * lazy loaded objects what you get back from Object.getClass is
     * not necessarily a "real" class and Hibernate isn't able to
     * deal with them.  Hibernate thoughtfully provides a utility
     * to unwrap the proxy class that they're apparently too lazy
     * to use themselves.
     */
    public Class getClass(SailPointObject src) {

        return org.hibernate.Hibernate.getClass(src);
    }

    
    /**
     * Return the effective controlled scopes for the logged in user.
     * This incluides any scopes granted due to workgroup membership
     * an possibly the users assigned scope.
     */
    protected List<Scope> getEffectiveControlledScopes()
        throws GeneralException {

        List<Scope> scopes = null;

        Identity user = getLoggedInUser();
        if (null != user) {
            scopes = user.getEffectiveControlledScopes(getContext().getConfiguration());
        }

        return scopes;
    }
    
    /**
     * Get the list of supported languages from the system config.
     */
    protected List<String> getSupportedLanguageList() throws GeneralException {

        List<String> langs = null;

        Configuration config = getContext().getConfiguration();
        Object o = config.getString(Configuration.SUPPORTED_LANGUAGES);
        if (o instanceof List) {
            langs = (List<String>)o;
        }
        else if (o instanceof String) {
            // in case something leaves a csv
            String s = (String)o;
            if (s.length() > 0) {
                if (s.charAt(0) == '[') {
                    // this is most likely a corrupted toString of a List, fix it
                    if (s.length() > 2 && s.charAt(s.length() - 1) == ']')
                        s = s.substring(1, s.length() - 1);
                }
                langs = Util.csvToList(s);
            }
        }

        // to get things rolling, bootstrap with the JVM default
        if (langs == null || langs.size() == 0) {
            langs = new ArrayList<String>();
            langs.add(Locale.getDefault().toString());
        }

        return langs;
    }
    
    /*
     * Return whether the assigned scope selector control should be displayed
     * on the page.
     */
    public boolean isShowAssignedScopeControl() throws GeneralException {
        ScopeService scopeSvc = new ScopeService(getContext());
        boolean show = scopeSvc.isScopingEnabled();
        if (show) {
            // Don't show the assigned scope selector if the logged in user can
            // only see zero or one scopes because we will just auto-select it
            // in this case.
            Identity user = getLoggedInUser();
            List<Scope> controlled = getEffectiveControlledScopes();
            show = ((null != controlled) && (controlled.size() > 1)) ||
                    ((null != user) && Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()));
        }
        
        return show;
    }
    
    public String getColumnJSON(String defaultSort, List<ColumnConfig> columns) {
        return this.getColumnJSON(defaultSort, columns, null);
    }

    /** 
     * Returns a grid meta data that can be used to build grid column configs 
     * and provide the list of fields to the datasource.
     * 
     * @return JSON representation of the columns
     */
    public String getColumnJSON(String defaultSort, List<ColumnConfig> columns, List<ColumnConfig> fieldOnlyColumns) {
        GridResponseSortInfo sortInfo = new GridResponseSortInfo("ASC", defaultSort);
        GridResponseMetaData metaData = new GridResponseMetaData(columns, sortInfo, fieldOnlyColumns);
        metaData.localize(getLocale(), getUserTimeZone());

        return JsonHelper.toJson(metaData);
    }   
    
    /**
     * Returns whether system is configured for showing the unsupported browser notification
     * 
     * @return true if system is configured to show notification, false otherwise.
     */
    public boolean getShowUnsupportedNotification() {
    	return Configuration.getSystemConfig().getBoolean(Configuration.UNSUPPORTED_BROWSER_NOTIFICATION);
    }

    /**
     * Returns whether pinch zooming is allowed on mobile devices.
     *
     * @return true if system is configured to allow pinch zooming on mobile devices, false otherwise.
     */
    public boolean getPinchZoomEnabled() {
        return Configuration.getSystemConfig().getBoolean(Configuration.ENABLE_PINCH_ZOOM);
    }
    
    /**
     * Determines whether the current user is authorized for all passed authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @return True if authorization is successful, false otherwise.
     * @throws GeneralException If an error occurs during authorization.
     */
    protected boolean isAuthorized(Authorizer...authorizers) throws GeneralException {
    	return AuthorizationUtility.isAuthorized(this, authorizers);
    }
    
    /**
     * Authorizes the current user with the specified authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @throws UnauthorizedAccessException If authorization fails.
     * @throws GeneralException If an error occurs during authorization.
     */
    protected void authorize(Authorizer... authorizers) throws GeneralException {
    	AuthorizationUtility.authorize(this, authorizers);
    }
    
    /**
     * @return value of the 'name' string used to authenticate logged in user.
     */
    public String getOriginalAuthId() {
        String id = (String)getSessionScope().get(LoginBean.ORIGINAL_AUTH_ID);
        // If id is blank, we should have come in through SSO.
        if(id == null || id.equals("")) {
            id = (String)getSessionScope().get(LoginBean.ATT_ORIGINAL_ACCOUNT_ID);
        }
        return id;
    }

    /**
     * Return the native ID of the link that was used to SSO into the system
     * if there is one.
     */
    public String getOriginalNativeId() {
        return (String) getSessionScope().get(LoginBean.ATT_ORIGINAL_NATIVE_ID);
    }
    
    /**
     * @return value of the SSO native identity, OR the 'originalAuthId' used to authenticate logged in user.
     */
    public String getNativeAuthId() {
        String id = (String)getSessionScope().get(LoginBean.ATT_ORIGINAL_NATIVE_ID);
        // If id is blank, we probably did NOT come in through SSO.
        if (Util.isNullOrEmpty(id)) {
            id = getOriginalAuthId();
        }
        // If id is still blank, grab whatever was submitted by the user.
        if (Util.isNullOrEmpty(id)) {
            id = getSignatureAuthId();
        }
        
        if (Util.isNullOrEmpty(id)) {
            id = (String)getSessionScope().get(PageAuthenticationFilter.ATT_PRINCIPAL);
        }
        
        return id;
    }
    
    protected Notary getNotary() {
        if(_notary == null) {
            _notary = new Notary(getContext(), getLocale());
        }
        return _notary;
    }
    
    // ALWAYS call setSignaturePass(null) after calling this so we don't
    // hold on to the password any longer than necessary!!
    public String getSignaturePass() {
        return _signaturePass;
    }
    
    public void setSignaturePass(String signaturePass) {
        _signaturePass = signaturePass;
    }
    
    public String getSignatureAuthId() {
        return _signatureAuthId;
    }
    
    public void setSignatureAuthId(String id) {
        _signatureAuthId = id;
    }
    
    public boolean isAllowXFrameOptions() {
        return PageAuthorizationFilter.isAllowXFrameOptions();
    }
    
    public String getCsrfToken() {
        HttpSession session = (HttpSession)getFacesContext().getExternalContext().getSession(false);
        
        if(session != null) {
            CsrfService csrf = new CsrfService(session);
            return csrf.getToken();
        }
        
        return null;
    }

    /**
     * @return True if the session cookie is configured to be secure.
     * If secure, then our CSRF token cookies should also be secure.
     * See CsrfCookieInitializerService.js & iiqScriptBase.xhtml.
     */
    public boolean isSecure() {
        return Util.useSecureCookies();
    }

    /**
     * If true scope check is always done when validating access, even if scoping is not enabled.
     * The need for this arose from a bug where all users have access to identity/identity.jsf
     * for the sake of lcm view identity me.  A problem arose where a user could view the details
     * of any user is scoping is disabled.
     * It seems like the behavior of forceScopeCheck = false is the correct behavior, but there
     * is not time to completely verify that, so it is made conditional.
     */
    public void setForceScopeCheck(boolean forceScopeCheck) {
        this.forceScopeCheck = forceScopeCheck;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
        List<String> dynamicScopeNames = null;
        Object o = getSessionScope().get(SessionAttributes.ATT_DYNAMIC_SCOPES.value());
        if (o instanceof List) {
            dynamicScopeNames = (List<String>) o;
        }
        
        return dynamicScopeNames;
    }

    /**
     * @return True if color contrast is enabled, false otherwise
     */
    public boolean isContrastEnabled() {
        return Configuration.getSystemConfig().getBoolean(Configuration.ENABLE_CONTRAST);
    }

    /**
     * @return The limit for list results after being validated against configured maximum and default
     */
    protected int getResultLimit() {
        return WebUtil.getResultLimit(Util.atoi(getRequestParameter("limit")));
    }
}
