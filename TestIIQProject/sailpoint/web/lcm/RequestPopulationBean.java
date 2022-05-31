/**
 * 
 */
package sailpoint.web.lcm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.service.LCMConfigService;
import sailpoint.service.RequestAccessService;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.listfilter.RequestAccessIdentitiesFilterContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.Consts;
import sailpoint.web.QuickLinkExecutorBean;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class RequestPopulationBean extends BaseListBean<Identity> 
implements NavigationHistory.Page {

    /**
     * Extend the filter list context to search on id instead of name for Identity filters. 
     */
    private class RequestPopulationBeanFilterContext extends RequestAccessIdentitiesFilterContext {
        
        @Override
        protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
            Filter filter;
            if (ListFilterDTO.DataTypes.Identity.equals(filterDTO.getDataType())) {
                filter = getSimpleFilter(filterDTO.getProperty() + ".id", value, operation);
            } else {
                filter = super.convertFilterSingleValue(filterDTO, value, ListFilterValue.Operation.StartsWith, context);
            }
            
            return filter;
        }
    }
    
    private static Log log = LogFactory.getLog(RequestPopulationBean.class);

    public static final String ATT_FLOW_NAME = "lcmFlowName";
    public static final String ATT_SEARCH_INPUTS = "lcmRequestFilterInputs";
    public static final String ATT_SEARCH_FILTER = "lcmRequestFilter";
    public static final String ATT_SEARCH_STRING_FILTER = "lcmRequestStringFilter";
    public static final String ATT_SEARCH_TYPE_LCM_REQUEST = "LCM";

    public static final String ATT_ALLOW_BULK = "lcmRequestPopulationAllowBulk";

    String flowName;
    List<String> identityIds;
    String identityId;
    String identityAttributes;
    String filterValues;



    String filterString;

    boolean allowBulk;

    public RequestPopulationBean() throws GeneralException {
        // the service quicklinkName value will be set by a quicklink launch, the request class
        // will be set when coming in through the widget and the other two values
        // would be set by launching in from an external application, if none of these values
        // are present then the user tried to come to this page directly
        if (!getSessionScope().containsKey(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK) &&
            !getSessionScope().containsKey(BaseRequestBean.ATT_REQUEST_CLASS) &&
            !getSessionScope().containsKey(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION) &&
            !getSessionScope().containsKey(RequestAccessService.ATT_EXTERNAL_SOURCE)) {
            throw new GeneralException("No workflow launched.");
        }

        this.setScope(Identity.class);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    /** Called when the user chooses "Select Everything" from the Available Identities grid.
     * Builds a query based on the user's filter criteria and grabs all identity ids and stores
     * them on the session.
     */
    public String selectAllIdentities() throws GeneralException {
        Iterator<Object[]> rows = getIdentityIdIterator();
        if(rows!=null) {
            Set<String> ids = getIdentityIdsFromSession();
            while(rows.hasNext()) {
                Object[] row = rows.next();
                String id = (String)row[0];
                if(!ids.contains(id) && (identityIds==null || !identityIds.contains(id))) {
                    ids.add(id);
                }
            }
            
            if (isAuthorized(ids)) {
                getSessionScope().put(BaseRequestBean.ATT_IDENTITY_IDS, ids);
            } else {
                throw new GeneralException("The logged in user: " + getLoggedInUser().getId() +
                        " does not have access to one or many of the requested identities.");
            }
        }

        return "";
    }

    public String deselectAllIdentities() {
        Iterator<Object[]> rows = getIdentityIdIterator();
        if(rows!=null) {
            Set<String> ids = getIdentityIdsFromSession();
            while(rows.hasNext()) {
                Object[] row = rows.next();
                String id = (String)row[0];
                ids.remove(id);
            }
            getSessionScope().put(BaseRequestBean.ATT_IDENTITY_IDS, ids);
        }
        return "";
    }

    /**
     * Wrap the getGridResponseJson method so we can add authorization.
     *
     * @return The identities JSON.
     * @throws GeneralException
     */
    public String getChosenIdentitiesJson() throws GeneralException {
        authorize(new LcmEnabledAuthorizer());

        return getGridResponseJson();
    }

    protected Iterator<Object[]> getIdentityIdIterator() {

        try {
            LCMConfigService svc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
            String quicklinkName = (String) getSessionScope().get(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
            QueryOptions qo = svc.getRequestableIdentityOptions(getLoggedInUser(), getLoggedInUserDynamicScopeNames(), quicklinkName, null);
            
            // If null is returned then no identities will match the filter so there's no need to search
            if (qo != null) {
                Filter sessionFilter = (Filter)getSessionScope().get(RequestPopulationBean.ATT_SEARCH_FILTER);
                if (sessionFilter != null) {
                    qo.add(sessionFilter);
                }
                Filter nameFilter = (Filter)getSessionScope().get(RequestPopulationBean.ATT_SEARCH_STRING_FILTER);
                if (nameFilter != null) {
                    qo.add(nameFilter);
                }

                Iterator<Object[]> rows = getContext().search(Identity.class, qo, Arrays.asList("id"));
                return rows;
            }

        } catch(GeneralException ge) {
            log.warn("Unable to select all identities.  Exception: " + ge.getMessage());
        }
        return null;
    }

    public String addIdentity() {
        Set<String> ids = getIdentityIdsFromSession();

        if(identityIds!=null) {
            ids.addAll(identityIds);
        }

        getSessionScope().put(BaseRequestBean.ATT_IDENTITY_IDS, ids);    
        return null;
    }

    public String removeIdentity() {
        Set identityIds = getIdentityIdsFromSession();
        identityIds.remove(identityId);
        
        if(this.identityIds!=null && !this.identityIds.isEmpty()) {
            identityIds.removeAll(this.identityIds);
        }
        getSessionScope().put(BaseRequestBean.ATT_IDENTITY_IDS, identityIds);   
        return null;
    }

    public String back() throws GeneralException {

        BaseRequestBean.clearSession();
        return NAV_OUTCOME_HOME;
    }

    public String submit() throws GeneralException {
        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        }

        Set<String> ids = null;
        if(isAllowBulk()) {
            ids = getIdentityIdsFromSession();

            if(ids!=null && !ids.isEmpty()) {
                if (isAuthorized(ids)) {
                    if(ids.size()>1) {
                        getSessionScope().remove(BaseListBean.ATT_EDITFORM_ID);
                        getSessionScope().remove(BaseListBean.ATT_LAST_SELECTED_ID);
                        getSessionScope().put(BaseRequestBean.ATT_IDENTITY_IDS, ids);
                    } else {
                        Iterator<String> idsIter = ids.iterator();
                        String id = idsIter.next();
                        if (isAuthorized(id)) {
                            String lastId = (String) getSessionScope().get(BaseListBean.ATT_EDITFORM_ID);
                            getSessionScope().put(BaseListBean.ATT_LAST_SELECTED_ID, lastId);
                            getSessionScope().put(BaseListBean.ATT_EDITFORM_ID, id);
                        } else {
                            throw new GeneralException("The logged in user: " + getLoggedInUser().getId() +
                                    " does not have access to the requested identity: " + id);
                        }
                    }
                } else {
                    throw new GeneralException("The logged in user: " + getLoggedInUser().getId() +
                            " does not have access to one or many of the requested identities.");
                }
            } else {
                addMessage(new Message(Message.Type.Error, MessageKeys.LCM_CHOOSE_IDENTITIES_ERROR), null);
                return null;
            }
        } else {
            if (isAuthorized(this.identityId)) {
                String lastId = (String) getSessionScope().get(BaseListBean.ATT_EDITFORM_ID);
                getSessionScope().put(BaseListBean.ATT_LAST_SELECTED_ID, lastId);
                getSessionScope().put(BaseListBean.ATT_EDITFORM_ID, identityId);
                getSessionScope().put(IdentityDTO.VIEWABLE_IDENTITY, identityId);
            } else {
                throw new GeneralException("Logged in user: " + getLoggedInUser().getId() +
                        " does not have access to this identity: " + identityId);
            }
        }

        // Use the flow name as the return for the action.
        String returnStr = getFlowName();
        
        // Check if we're dealing with a workflow request.  If so, launch it.
        QuickLinkExecutorBean qlBean = new QuickLinkExecutorBean(this);
        QuickLink link = qlBean.getWorkflowQuicklink(getFlowName());
        if (null != link) {
            String identityId = (!isAllowBulk()) ? this.identityId : null;
            List<String> identityIds = (isAllowBulk()) ? new ArrayList<String>(ids) : null;
            returnStr = qlBean.launchWorkflow(link, identityId, identityIds);
        }
        
        if (Consts.NavigationString.viewIdentity.name().equals(returnStr)) {
        	returnStr =  IdentityDTO.createNavString(Consts.NavigationString.viewIdentity.name(), identityId);
        }
        
        return returnStr;
    }


    public QueryOptions getQueryOptions() throws GeneralException{
        LCMConfigService svc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
        String quicklinkName = (String) getSessionScope().get(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
        QueryOptions qo = svc.getRequestableIdentityOptions(getLoggedInUser(), getLoggedInUserDynamicScopeNames(), quicklinkName, null);
        getSortOrdering(qo);
        qo.add(Filter.in("id", getIdentityIdsFromSession()));

        return qo;
    }

    public int getCount() throws GeneralException {
        Set<String> ids = getIdentityIdsFromSession();

        return ids!=null ? ids.size() : 0;
    }

    public List<Map<String,Object>> getRows() throws GeneralException {
        Set<String> ids = getIdentityIdsFromSession();
        if(ids!=null && !ids.isEmpty()) {
            return super.getRows();
        } else {
            return new ArrayList<Map<String,Object>>();
        }
    }

    public String getIdentityAttributes() {

        if(identityAttributes==null) {
            List<Map<String,String>> attrMap = new ArrayList<Map<String,String>>();
            try {
                
                List<ListFilterDTO> filterDTOs = 
                        new ListFilterService(getContext(), getLocale(), new RequestPopulationBeanFilterContext()).getListFilters(false);
                
                for (ListFilterDTO filterDTO: filterDTOs) {
                    Map<String,String> map = new HashMap<String,String>();
                    map.put("name", filterDTO.getProperty());
                    map.put("displayName", filterDTO.getLabel());
                    map.put("type", filterDTO.getDataType().getPropertyType().toString());

                    attrMap.add(map);

                }
            } catch(GeneralException ge) {
                log.warn("Unable to build identity attribute list: " + identityAttributes);
            }
            identityAttributes = JsonHelper.toJson(attrMap);
        }
        return identityAttributes;
    }

    public String calculateFilter() throws GeneralException {
        Filter filter = null;

        if(filterString!=null && !filterString.equals("")) {
            Map<String,String> values = JsonHelper.mapFromJson(String.class, String.class, filterString);

            ListFilterService filterService = new ListFilterService(getContext(), getLocale(), new RequestPopulationBeanFilterContext()); 
            List<Filter> filters = filterService.convertQueryParametersToFilters(values, false);

            // LEGACY: this does default (non-identity attributes) filters on its own
            for(String key : values.keySet()) {
                String value = values.get(key);
                if(value!=null && !value.equals("")) {
                    key = key.replace("_", ".");
                    filters.add(Filter.ignoreCase(Filter.like(key, value, MatchMode.START)));
                }
            }

            if(filters.size()==1) {
                filter = filters.get(0);
            } else if(filters.size()>1) {
                filter = Filter.and(filters);
            } else {
                values = null;
            }
            getSessionScope().put(ATT_SEARCH_INPUTS, values);
        } else {
            getSessionScope().remove(ATT_SEARCH_INPUTS);
        }
        getSessionScope().put(ATT_SEARCH_FILTER, filter);

        return "";
    }
    
    /**
     * Check whether the provided IDs can be accessed by the
     * logged in user.
     * @param ids IDs of users to check for access
     * @return true if logged in user can make requests for users, false if not
     * @throws GeneralException
     */
    private boolean isAuthorized(Set<String> ids) throws GeneralException {

        LCMConfigService LCMsvc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
        String quicklinkName = (String) getSessionScope().get(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
        QueryOptions idsAllowedQo = LCMsvc.getRequestableIdentityOptions(getLoggedInUser(), getLoggedInUserDynamicScopeNames(), quicklinkName, null);
        
        if (null != ids) {
            // verify that the ids we have are valid
            idsAllowedQo.add(Filter.in("id", ids));
            if (ids.size() != getContext().countObjects(Identity.class, idsAllowedQo)) {
                // you don't have access to an ID
                return false;
            }
        } else {
            // yes, you may have access to nothing
        }
        
        return true;
    }
    
    /**
     * Check whether the provided id can be accessed by the
     * logged in user.
     * @param id ID of user to check for authorization
     * @return true if logged in user can make requests for user, false if not
     * @throws GeneralException
     */
    private boolean isAuthorized(String id) throws GeneralException {
        LCMConfigService LCMsvc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
        QueryOptions idsAllowedQo = LCMsvc.getConfiguredIdentityQueryOptions(
                getLoggedInUser(), 
                getLoggedInUserDynamicScopeNames());
        
        if (null != id) {
            // verify that the id we have is accessible by the logged in user
            idsAllowedQo.add(Filter.eq("id", id));
            if (0 == getContext().countObjects(Identity.class, idsAllowedQo)) {
                // you don't have access to this ID
                return false;
            }
        } else {
            // yes, you may have access to nothing
        }
        
        return true;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // getters/setters
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getFilterValues() {
        if(filterValues==null) {
            Map<String,String> values = (Map<String,String>)getSessionScope().get(ATT_SEARCH_INPUTS);
            if(values!=null) {
                filterValues = JsonHelper.toJson(values);
            } else {
                filterValues = JsonHelper.toJson(new HashMap<String,String>());
            }
        }

        return filterValues;
    }

    public String getFlowName() {
        if(flowName==null) {
            flowName = (String)getSessionScope().get(ATT_FLOW_NAME);
        }
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public List<String> getIdentityIds() {
        return identityIds;
    }

    public Set<String> getIdentityIdsFromSession() {
        Set<String> ids = (Set<String>)getSessionScope().get(BaseRequestBean.ATT_IDENTITY_IDS);
        if(ids==null)
            ids = new HashSet<String>();
        return ids;
    }

    public void setIdentityIds(List<String> identityIds) {
        this.identityIds = identityIds;
    }

    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public boolean isAllowBulk() {
        if(!allowBulk) {
            allowBulk = Util.otob(getSessionScope().get(ATT_ALLOW_BULK));
        }
        return allowBulk;
    }

    public void setAllowBulk(boolean allowBulk) {
        this.allowBulk = allowBulk;
    }

    public String getFilterString() {
        return filterString;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }



    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Choose Population";
    }

    public String getNavigationString() {
        return "requestPopulation";
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {  

        getSessionScope().remove(ATT_SEARCH_FILTER);
    }
}
