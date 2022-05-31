/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Terminator;
import sailpoint.connector.Connector;
import sailpoint.connector.JDBCConnector;
import sailpoint.object.Application;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.URLUtil;
import sailpoint.tools.Util;
import sailpoint.tools.Util.ListElementWrapper;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;


/**
 * JSF bean to list applications.
 */
public class ApplicationListBean
    extends BaseListBean<Application>
    implements NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(ApplicationListBean.class);
    private static final String GRID_STATE = "applicationsListGridState";

    /**
     * HttpSession attribute we use to convey the selected application id
     * to ApplicationObjectBean.  
     */
    public static final String ATT_OBJECT_ID = "ApplicationId";

    boolean uiMaxApps;
	boolean authoritativeAppDefined;
    List<ColumnConfig> columns;

    /**
     * Configuration attribute that specifies the transport type. Should be
     * one of the TRANSPORT_TYPE_* constants.
     */
    private static final String CONFIG_TRANSPORT = "filetransport";

    /**
     * Value to specify a local file for {@link #CONFIG_TRANSPORT}.
     */
    private static final String TRANSPORT_TYPE_LOCAL = "local";

    /**
     * A ListElementWrapper that returns ApplicationWrappers instead of
     * Applications.
     */
    private static class ApplicationListWrapper
        implements Util.ListElementWrapper<Application> {

        public Application wrap(Application element) {
            return new ApplicationWrapper(element);
        }
    }

    /**
     * A decorator for an Application that adds a getHost() method.
     */
    public static class ApplicationWrapper extends Application {
        private static final long serialVersionUID = 1L;

        private Application app;

        public ApplicationWrapper(Application app) {
            this.app = app;
        }

        public String getId() { return this.app.getId(); }
        public String getName() { return this.app.getName(); }
        public String getType() { return this.app.getType(); }
        public Date getModified() { return this.app.getModified(); }

        public String getHost() {
            String hostValue =
                      this.app.getStringAttributeValue(Connector.CONFIG_HOST);
                // special case processing to find a host value
            if ( hostValue == null ) {
                String url =
                 this.app.getStringAttributeValue(JDBCConnector.CONFIG_DBURL);
                if ( url != null ) {
                    String[] urlPatterns = {
                            "//([^/]+)/",
                            "//([^:]+):",
                            ":@([^:]+):",
                            ":([^:]+)$",
                            "Tds:([^:]+):"
                    };
                    for ( String urlPattern : urlPatterns ) {
                        Pattern p = Pattern.compile(urlPattern);
                        Matcher m = p.matcher(url);
                        if ( m.find() ) {
                            hostValue = m.group(1);
                            break;
                        }
                    }
                }
            }

            String transportType = 
                this.app.getStringAttributeValue(CONFIG_TRANSPORT);
            if (TRANSPORT_TYPE_LOCAL.equals(transportType) )
                hostValue = "localhost";

                // if we still don't have a value, make sure to set it to
                // empty string
            if ( hostValue == null ) hostValue = "";
            return hostValue;
        }
    }

    /**
     *
     */
    public ApplicationListBean() {
        super();
        setScope(Application.class);
    }  // ApplicationListBean()

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * action to transition to the edit screen without setting an id to edit
     *
     * @return "edit"
     */
    @SuppressWarnings("unchecked")
    public String newApplication() {
        getSessionScope().remove(ATT_OBJECT_ID);
        // Ran into an issue with stale ids hanging around the session
        // which messes up our session key logic in the object bean
        getSessionScope().remove(BaseListBean.ATT_SELECTED_ID);
        getSessionScope().remove(BaseListBean.ATT_EDITFORM_ID);
        
        getSessionScope().put(ApplicationObjectBean.CURRENT_TAB, null);
        getSessionScope().put(BaseEditBean.FORCE_LOAD, true);
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        return "edit";
    }

    /**
     * "Delete" action handler for the application list page.
     *
     * We must make sure that this application is not referenced by any
     * business processes before allowing it to be deleted.
     *
     * @throws GeneralException
     */
    /* (non-Javadoc)
     * @see sailpoint.web.BaseListBean#deleteObject(javax.faces.event.ActionEvent)
     */
    public void deleteObject(ActionEvent event) {
        if ( _selectedId == null ) return;

        int count;
        boolean references = false;

        count = countParents(Link.class, "application", _selectedId);
        if ( count > 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_APP_HAS_LINK_REFS, count), null);
            references = true;
        }

        count = countParents(Profile.class, "application", _selectedId);
        if ( count > 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_APP_HAS_PROFILE_REFS, count), null);
            references = true;
        }

        count = countParents(EntitlementGroup.class, "application", _selectedId);
        if ( count > 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_APP_HAS_ENT_GRP_REFS, count), null);
            references = true;
        }
        
        count = countParents(ApplicationScorecard.class, "application", _selectedId);
        if ( count > 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_APP_HAS_APP_SCORECARD_REFS, count), null);
            references = true;
        }
        
        count = countParents(IdentityEntitlement.class, "application", _selectedId);
        if ( count > 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_APP_HAS_CONNECTION_REFS, count), null);
            references = true;
        }

        try {
            Configuration syscon = getContext().getConfiguration();
            Object o = syscon.get(Configuration.LOGIN_PASS_THROUGH);
            if (o instanceof List) {
                List list = (List)o;
                for (Object el : list) {
                    if (el instanceof Reference) {
                        Reference ref = (Reference)el; 
                        if (ref.getId() != null && ref.getId().equals(_selectedId)) {
                            addMessage(new Message(Message.Type.Error,
                                                   MessageKeys.ERR_APP_IS_PASS_THROUGH), null);
                            references = true;
                            break;
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to check system configuration referneces");
            log.error(t);
        }
            
        if ( references != true ) {
            try {
                Application app = getContext().getObjectById(Application.class, _selectedId);
                if (app != null) {
                    Terminator terminator = new Terminator(getContext());
                    app.setRemediators(null);
                    terminator.deleteObject(app);
                }
            } catch (GeneralException e) {
                final String errorMsg = "The selected application cannot be deleted at this time.";
                log.error(errorMsg, e);
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_APP_CANT_BE_DELETED), null);
            }
        }
    }  // deleteObject(ActionEvent)


    /**
     * Method to check to see if there is at least one authoritative application
     * marked authoritative. Adds a warning to the top of the page if there
     * is at least one application AND zero application are flagged authoritative.
     */
    public boolean isAuthoritativeAppDefined() {
        try {
			// Look for at least one authoritative app.
			if ( getCount() > 0 ) {
			    QueryOptions ops = new QueryOptions();
			    ops.add(Filter.eq("authoritative", true));
			    int count = getContext().countObjects(Application.class, ops);
			    authoritativeAppDefined = ( count > 0 );
			    }
		} catch (GeneralException e) {
            log.error(e);
		}
        
        return authoritativeAppDefined;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        // Set scoping to allow owner and scope controllers to view apps.
        // Apps without owners will only be viewable by system admininstrators.
        QueryOptions qo = super.getQueryOptions();
        //IIQETN-6231 :- allowing to be shown applications with featuresString NO_AGGREGATION
        //This change will only affect the "Application Definition grid", all other places where
        //we are filtering out applications with NO_AGGREGATION will continue working as before.
        //qo.add(Filter.eq("noAggregation", false));
        
        getQueryOptionsFromRequest(qo);
        
        return qo;
    }
    
    /**
     * This is specifically for the search text entered in the search field
     * If there is no text, this is skipped. If entered, this is taken into
     * account. Always Searches through the "name" and "type".
     * @param qo
     * @throws GeneralException
     */
    public void getQueryOptionsFromRequest(QueryOptions qo) throws GeneralException
    {
    	String searchText = getRequestParameter("searchText");
    	if((searchText != null) && (!searchText.equals(""))) {
    		List<Filter> filters = new ArrayList<Filter>();
    		filters.add(Filter.ignoreCase(Filter.like("name", searchText, MatchMode.START)));
    		filters.add(Filter.ignoreCase(Filter.like("type", searchText, MatchMode.START)));
    		qo.add(Filter.or(filters));
    	} 	
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String select() throws GeneralException {
        
        getSessionScope().put(BaseEditBean.FORCE_LOAD, true);

        getSessionScope().put(ApplicationObjectBean.CURRENT_TAB, null);
        
        //Append appId to the navigation token so SailPointNavigationHandler can pick it up
        String nav = super.select();
        if(nav != null) {
            return nav + "?appId=" + URLUtil.encodeUTF8(getSelectedId());
        }
        else {
            return nav;
        }
    }

    /**
     * This list page needs special logic to compute the host, so we'll use a
     * decorator that adds this.
     */
    @Override
    public ListElementWrapper<Application> getListElementWrapper() {
        return new ApplicationListWrapper();
    }

    @Override
    public Map<String,String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();        
        List<ColumnConfig> columns = getColumns();
        if (null != columns && !columns.isEmpty()) {
            for(int j =0; j < columns.size(); j++) {
                sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }
        }
        return sortMap;
    }
    
    void loadColumnConfig() throws GeneralException {
        this.columns = super.getUIConfig().getApplicationTableColumns();
    }
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            try {
                loadColumnConfig();     
            } catch (GeneralException ge) {
                log.info("Unable to load columns: " + ge.getMessage());
            }
        return columns;
    }
    
    @Override
    public Map<String,String> getSortedDisplayableNames() throws GeneralException {
        // Overridden to include apps owned by the logged in user.
        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        qo.addOwnerScope(super.getLoggedInUser());
        return getSortedDisplayableNames(qo);
    }
    
    public Map<String,String> getSortedDisplayableNamesOnly() throws GeneralException {
        Map<String, String> sortedDisplayNames = getSortedDisplayableNames();
        if(sortedDisplayNames!=null) {
	        for(String key : sortedDisplayNames.keySet()) {
	        	sortedDisplayNames.put(key, key);
	        }
        }
        return sortedDisplayNames;
    }
    
    public Map<String,String> getPassthroughApps()         
        throws GeneralException {

        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("supportsAuthenticate", true));
        return getSortedDisplayableNames(options);
    }
 
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Application List";
    }

    public String getNavigationString() {
        return "applicationList";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }

	public String getGridStateName() { 
		return GRID_STATE; 
	}

    private static String COL_HOST = "host";
    private static String COL_ATTRIBUTES = "attributes";

    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols) throws GeneralException {

        Map<String,Object> map = super.convertRow(row, cols);
        map.put(COL_HOST, getHost(map));
        //Remove attributes from row if not included in UIConfig
        if (!containsAttributesColumn()) {
            map.remove(COL_ATTRIBUTES);
        }

        return map;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        //Need to add attributes to projection columns in order to calculate host
        if (!Util.nullSafeContains(cols, COL_ATTRIBUTES)) {
            cols.add(COL_ATTRIBUTES);
        }

        return cols;
    }

    /**
     * If the UIConfig contains a columnconfig for attributes property, we will include it in the JSON response.
     * Otherwise, we will exclude it, and use it only to calculate Host property
     * @return True if the UIConfig contains an entry for attributes.
     */
    private boolean containsAttributesColumn() {
        List<ColumnConfig> cols = getColumns();

        for (ColumnConfig cfg : Util.safeIterable(cols)) {
            if (COL_ATTRIBUTES.equals(cfg.getProperty())) {
                return true;
            }
        }

        return false;
    }

    private String getHost(Map<String, Object> row) throws GeneralException {
        //Calculate Host from attributes
        Attributes atts = (Attributes)row.get(COL_ATTRIBUTES);
        String hostValue = null;
        if (atts != null) {
            //Should never get here, but IIQPB-337 somehow did.
            hostValue =
                    atts.getString(Connector.CONFIG_HOST);
            // special case processing to find a host value
            if (hostValue == null) {
                String url =
                        atts.getString(JDBCConnector.CONFIG_DBURL);
                if (url != null) {
                    String[] urlPatterns = {
                            "//([^/]+)/",
                            "//([^:]+):",
                            ":@([^:]+):",
                            ":([^:]+)$",
                            "Tds:([^:]+):"
                    };
                    for (String urlPattern : urlPatterns) {
                        Pattern p = Pattern.compile(urlPattern);
                        Matcher m = p.matcher(url);
                        if (m.find()) {
                            hostValue = m.group(1);
                            break;
                        }
                    }
                }
            }

            String transportType =
                    atts.getString(CONFIG_TRANSPORT);
            if (TRANSPORT_TYPE_LOCAL.equals(transportType))
                hostValue = "localhost";
        }

        // if we still don't have a value, make sure to set it to
        // empty string
        if ( hostValue == null ) hostValue = "";
        return hostValue;
    }
}  // class ApplicationListBean
