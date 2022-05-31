/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.api.Iconifier;
import sailpoint.api.Iconifier.Icon;
import sailpoint.api.PasswordPolice;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.service.LCMConfigService;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A sub-resource to deal with links on an identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class LinksResource extends BaseListResource {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public static final String COL_ACCOUNT_ICON_CONFIG =
        CALCULATED_COLUMN_PREFIX + "accountIcons";

    public static final String COL_ACCOUNT_STATUS =
        CALCULATED_COLUMN_PREFIX + "status";

    public static final String COL_ACCOUNT_STATUS_CLASS =
        CALCULATED_COLUMN_PREFIX + "status_class";

    public static final String STATUS_CLASS_ACTIVE = "lcmActive";
    public static final String STATUS_CLASS_DISABLED = "lcmDisabled";
    public static final String STATUS_CLASS_LOCKED = "lcmLocked";
    public static final String COLUMNS_KEY_PASSWORD = "sailpoint.web.lcm.PasswordsRequestBean";
    public static final String COLUMNS_KEY_ACCOUNTS = "sailpoint.web.lcm.AccountsRequestBean";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private String identity;
    private Application application;
    private LinkService linksService;
    private Iconifier iconifier;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Sub-resource constructor.
     */
    public LinksResource(String identity, BaseResource parent) {
        super(parent);
        this.identity = identity;
    }

    /**
     * Sub-resource constructor.
     */
    public LinksResource(Application application, BaseResource parent) {
        super(parent);
        this.application = application;
    }

    /**
     * Return the identity we're operating on.
     */
    private Identity getIdentity() throws GeneralException {
        Identity i = getContext().getObjectByName(Identity.class, this.identity);
        if (i == null) {
            throw new ObjectNotFoundException(Identity.class, this.identity);
        }
        return i;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // LINKS LIST
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return the links on the identity, optionally filtering by application and
     * instance.
     * 
     * @param  application  The application name or ID to filter by (optional).
     * @param  instance     The instance name to filter by (optional).
     * 
     * @return A ListResult with details about the links.
     */
    @GET
    public ListResult getLinks(@QueryParam("application") String application,
                               @QueryParam("instance") String instance,
                               @QueryParam("includeLinkState") boolean includeState,
                               @QueryParam("action") String action)
        throws GeneralException {
    	
    	String authAction = (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_ACTION);
        if (Util.isNothing(authAction)) {
            authAction = action;
        }
    	authorize(new LcmRequestAuthorizer(getIdentity()).setAction(authAction));
      
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity", getIdentity()));

        if (null != application) {
            Application app =
                getContext().getObjectById(Application.class, decodeRestUriComponent(application));
            qo.add(Filter.eq("application", app));
        }

        if (null != instance) {
            qo.add(Filter.eq("instance", instance));
        }

        qo.addOrdering("displayName", true);
        qo.addOrdering("nativeIdentity", true);
        
        String props = "id, nativeIdentity, displayName, application.id, application.name, application.type, application.featuresString";
        Iterator<Object[]> results = getContext().search(Link.class, qo, props);

        ListResult listResult = createListResult(results, props);
        if ( includeState ) {
            adornLinksState(listResult);
        }
        return listResult;
    }
    
    /**
     * 
     * In addition to "enabled" and "locked" it also returns
     * a flag "supportsSetPassword" that indicates its
     * configured to allow password changes.     
     * 
     * Breaking this out because its more expensive and requires
     * a fetch of the link which may not be required in many cases.
     * 
     */
    public ListResult adornLinksState( ListResult result )                              
        throws GeneralException {

        if ( result != null ) {
            List<Map<String,Object>> objects = result.getObjects();
            if ( Util.size(objects) > 0 ) {
                if (linksService == null) {
                    this.linksService = new LinkService(getContext());
                }
                for ( Map<String,Object> linkMap : objects ) {
                    String linkId =  Util.getString(linkMap, "id");
                    Link link = getContext().getObjectById(Link.class, linkId);
                    if ( link != null ) {
                        linkMap.put("disabled", link.isDisabled() );
                        linkMap.put("locked", link.isLocked());
                        linkMap.put("supportsSetPassword", 
                                    linksService.supportsSetPassword(link));
                    }
                }            
            }
        }                
        return result;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GRID DATASOURCES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return a list result that contains the accounts on this application.
     * 
     * @param  query  A quick search that will filter by link or identity name.
     * 
     * @return A ListResult that contains the accounts on this application.
     */
    @GET @Path("accountsGrid")
    public ListResult getApplicationAccountsGrid(@QueryParam("query") String query)
        throws GeneralException {
        
        authorize(new RightAuthorizer(SPRight.ViewApplication, SPRight.ManageApplication));

        if (null == this.application) {
            throw new GeneralException("Application ID is required.");
        }
        
        QueryOptions qo = super.getQueryOptions(UIConfig.APPLICATION_ACCOUNTS_COLUMNS);
        
        // Only return accounts on this application.
        qo.add(Filter.eq("application", this.application));
        
        // If something was entered in the quick search field, filter.
        if (!Util.isNullOrEmpty(query)) {
            qo.add(Filter.or(Filter.ignoreCase(Filter.like("nativeIdentity", query, MatchMode.START)),
                             Filter.ignoreCase(Filter.like("displayName", query, MatchMode.START)),
                             Filter.ignoreCase(Filter.like("identity.name", query, MatchMode.START)),
                             Filter.ignoreCase(Filter.like("identity.displayName", query, MatchMode.START))));
        }
                
        return super.getListResult(UIConfig.APPLICATION_ACCOUNTS_COLUMNS, Link.class, qo);
    }

    @Override
    protected void calculateColumn(ColumnConfig config,
                                   Map<String,Object> rawQueryResults,
                                   Map<String,Object> map)
        throws GeneralException {

        if (COL_ACCOUNT_ICON_CONFIG.equals(config.getDataIndex())) {
            if (null == this.iconifier) {
                this.iconifier = new Iconifier();
            }
            List<Icon> icons = this.iconifier.getAccountIcons(rawQueryResults);
            map.put(COL_ACCOUNT_ICON_CONFIG, icons);
        }

        if (COL_ACCOUNT_STATUS.equals(config.getDataIndex())) {
            // Bummer that we have to fetch the link, but we can't get the
            // attributes map in a projection query and it is required.
            Link link = getContext().getObjectById(Link.class, (String) map.get("id"));
            calculateStatus(link, map, getLocale(), getUserTimeZone());
        }
    }
    
    @Override
    protected List<String> getProjectionColumns(String columnsKey)
        throws GeneralException {

        List<String> cols = super.getProjectionColumns(columnsKey);
        
        // Need to add extended attributes to calculate the account icons.
        if (UIConfig.APPLICATION_ACCOUNTS_COLUMNS.equals(columnsKey)) {
            if (null == this.iconifier) {
                this.iconifier = new Iconifier();
            }
            cols.addAll(this.iconifier.getExtendedAccountAttributeProperties());
        }
        
        return cols;
    }
    
    /**
     * Return a grid compatible list of links that belong to the given identity.
     * 
     * @return A list of accounts associated to the given identity, represented
     *         as maps.
     */
    @GET @Path("managePasswordsGrid") 
    public ListResult getManagePasswordsGrid() throws GeneralException {
    	authorize(new LcmRequestAuthorizer(getIdentity()).setAction(QuickLink.LCM_ACTION_MANAGE_PASSWORDS));    	
        return getGrid(COLUMNS_KEY_PASSWORD, false, true);
    }

    /**
     * Return a grid compatible list of links that belong to the given identity.
     * 
     * @return A list of accounts associated to the given identity, represented
     *         as maps.
     */
    @GET @Path("manageAccountsGrid") 
    public ListResult getManageAccountsGrid() throws GeneralException {
    	authorize(new LcmRequestAuthorizer(getIdentity()).setAction(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS));
        return getGrid(COLUMNS_KEY_ACCOUNTS, true, false);
    }

    /**
     * Return a grid compatible list of links that belong to the given identity.
     * 
     * @return A list of accounts associated to the given identity, represented
     *         as maps.
     */
    private ListResult getGrid(String configKey, boolean includeDecisions,
                               boolean passwordOnly)
        throws GeneralException {
        
        if (linksService == null) {
            this.linksService = new LinkService(getContext());
        }
        
        Configuration config = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity", getIdentity()));

        if (passwordOnly) {
            // For the password only grid, we need to get a list of a applications that support provisioning
            Set<String> provisioningApplications = linksService.getProvisioningApplications( true);
            if (!Util.isEmpty(provisioningApplications)) {
                qo.add(Filter.in("application.id", provisioningApplications));
            }
        }
        
        List<Map<String,Object>> out = new ArrayList<Map<String,Object>>();
        int total = getContext().countObjects(Link.class, qo);
        
        // And to add the row limits and sort
        List<Filter> filters = qo.getFilters();
        qo = getQueryOptions(configKey);
        qo.getFilters().addAll(filters);
        
        Iterator<Link> links = getContext().search(Link.class, qo);
        while(links.hasNext()) {
            Link link = links.next();

            Map<String, Object> map = convertObject(link, configKey);
            
            // Calculate the decisions available on this link
            if (includeDecisions) {
                calculateDecisions(link, map, config);
            }

            // Add password policy data
            addPasswordPolicyInfo(link, map);
            
            // check if app supports current password
            addCurrentPasswordSupportInfo(link, map);
            
            out.add(map);
        }
        
        return new ListResult(out, total);
    }

    /**
     * This option tells the UI whether or not to show the current password field
     * for self service password changes
     *
     * @param link
     * @param map
     */
    private void addCurrentPasswordSupportInfo(Link link,
			Map<String, Object> map) {
    	boolean isSupported = link.getApplication().supportsFeature(Feature.CURRENT_PASSWORD);
        map.put("supports_current_password", new Boolean(isSupported));
	}

	public static void calculateDecisions(Link link, Map<String,Object> map, Configuration config) throws GeneralException{
        final String COL_DECISIONS = "IIQ_decisions";
        final String COL_REFRESH_STATUS = "IIQ_refresh_status";
        
        if(link!=null) {
            Application app = link.getApplication();

            Map<String,Object> decisions = new HashMap<String,Object>();
            /** If the app is null, it means the account was deleted **/
            if(app==null) {
                
            } else {
            
                /** if the account is disabled already **/
                boolean disabled = link.isDisabled();
                /** if the account is locked already **/
                boolean locked = link.isLocked();
                
                /** if the account supports unlocking **/
                boolean supportsUnlock = app.supportsFeature(Feature.UNLOCK);
                /** if the account supports locking **/
                boolean supportsEnable = app.supportsFeature(Feature.ENABLE);
                /** This is where we would determine what the state of the account is and send that up  **/
                
                /** This is where we determine whether the user can click a "refresh account" button **/
                boolean supportsRefresh = !app.supportsFeature(Feature.NO_RANDOM_ACCESS);
                
                /** If they've configured the system to show the unlock/enabled buttons **/
                boolean showAll = config.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_SHOW_ALL_BUTTONS);
                
                decisions.put(Configuration.LCM_OP_DELETE, new Boolean(true));
                decisions.put(Configuration.LCM_OP_DISABLE, new Boolean(!disabled && supportsEnable));
                decisions.put(Configuration.LCM_OP_ENABLE, new Boolean((showAll || disabled) && supportsEnable));
                decisions.put(Configuration.LCM_OP_UNLOCK, new Boolean((showAll || locked) && supportsUnlock));
                
                map.put(COL_REFRESH_STATUS, new Boolean(supportsRefresh));
            }
            map.put(COL_DECISIONS, decisions);
        }
    }
    
    public static void calculateStatus(Link link, Map<String, Object> map, Locale locale, TimeZone timezone) throws GeneralException{
        
        if(link!=null) {

            Application app = link.getApplication();
            
            if(app==null) {
                Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_STATUS_DELETED);
                map.put(COL_ACCOUNT_STATUS, msg.getLocalizedMessage(locale, timezone));
                map.put(COL_ACCOUNT_STATUS_CLASS, STATUS_CLASS_DISABLED);
            } else {
                if(link.isDisabled()) {
                    Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_STATUS_DISABLED);
                    map.put(COL_ACCOUNT_STATUS, msg.getLocalizedMessage(locale, timezone));
                    map.put(COL_ACCOUNT_STATUS_CLASS, STATUS_CLASS_DISABLED);
                } else if (link.isLocked()) {
                    Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_STATUS_LOCKED);
                    map.put(COL_ACCOUNT_STATUS, msg.getLocalizedMessage(locale, timezone));
                    map.put(COL_ACCOUNT_STATUS_CLASS, STATUS_CLASS_LOCKED);
                } else {
                    Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_STATUS_ACTIVE);
                    map.put(COL_ACCOUNT_STATUS, msg.getLocalizedMessage(locale, timezone));
                    map.put(COL_ACCOUNT_STATUS_CLASS, STATUS_CLASS_ACTIVE);
                }
            }
        }
    }
    
    protected void addPasswordPolicyInfo(Link link, Map<String, Object> map) throws GeneralException {
        PasswordPolice police = new PasswordPolice(getContext());
        PasswordPolicy policy = police.getEffectivePolicy(link);
        if (policy == null) {
            List<String> passwordRequirements = new ArrayList<String>();
            passwordRequirements.add(localize(MessageKeys.NO_PASSWORD_CONSTRAINTS));
            map.put("password_requirements", passwordRequirements);
        }
        else {
            map.put("password_requirements", policy.convertConstraints(getLocale(), getUserTimeZone()));
        }
    }
}
