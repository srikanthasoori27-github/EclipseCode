/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.group;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.Grouper;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.SelectOptionBean;
import sailpoint.web.extjs.ExtGridResponse;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class GroupDefinitionListBean extends BaseListBean<GroupDefinition> 
implements NavigationHistory.Page {

    public static final String ATT_OBJECT_ID = "GroupDefinitionId";
    
    /** Used on the edit group page **/
    public static final String GRID_STATE = "groupDefinitionListGridState";
    
    /** Used on the populations tab on the main define->groups page **/ 
    public static final String IPOP_GRID_STATE = "ipopsListGridState";
    
    public static final String SUBGROUP_GRID_STATE = "subGroupGridState";
    
    public static final String ATT_SEARCH_TYPE_POP = "POP";
    public static final String ATT_SHOW_ALL = "showAll";

    private static Log log = LogFactory.getLog(GroupDefinitionListBean.class);

    private SelectItem[] definitionOptions;
    private GroupFactory groupFactory;
    private String groupFactoryName;
    private List<GroupDefinition> myIpops;
    private String searchType;
    private GridState ipopGridState;
    private GridState subGroupGridState;

    List<ColumnConfig> columns;
    
    /**
     * Flag to tell the query to only return ipops
     */
    private boolean onlyIpops;
    /**
     * 
     */
    public GroupDefinitionListBean() {
        super();
        setScope(GroupDefinition.class);
        restore();

        // if this is a json request the factory name will not be bound to the bean
        if (getRequestParameter("groupFactoryName") != null){
            groupFactoryName = getRequestParameter("groupFactoryName");
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    public List<GroupDefinition> getMyIpops() throws GeneralException {
        if(myIpops==null) {
            groupFactory = null;
            onlyIpops = true;
            searchType = ATT_SEARCH_TYPE_POP;
            setLimit(getCount());
            myIpops =  getObjects();
        }
        return myIpops;
    }
    
    public List<SelectItem> getMyIpopSelectItems() throws GeneralException {
        getMyIpops();
        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem("", getMessage(MessageKeys.SELECT_IPOP, (Object[])null)));
        
        if (myIpops != null) {
            for (GroupDefinition ipop : myIpops) {
                String ipopName = ipop.getName();
                if (ipopName != null)
                    items.add(new SelectItem(ipop.getName(), ipop.getName()));
            }
        }
        
        return items;
    }
    
    public String getMyIpopGridJSON() throws GeneralException {
        Map<String, Object> response = new HashMap<String, Object>();
        //Setup ipop search variables
        groupFactory = null;
        onlyIpops = true;
        searchType = ATT_SEARCH_TYPE_POP;
        
        //We can't use getGridResponseJson because we need the rows 
        //at the 'populations' root.
        List<Map<String, Object>> iPopRows = getRows();
        makeJsonSafeKeys(iPopRows);
        response.put("totalCount", getCount());
        response.put("populations", iPopRows );

        return JsonHelper.toJson(response);
    }
    
    public String getMyIpopJson() throws GeneralException {

        Writer jsonString = new StringWriter();
        JSONWriter ipopJson = new JSONWriter(jsonString);
        String result;
        
        getMyIpops();
        try {
            ipopJson.object();
            if (myIpops != null) {
                for (GroupDefinition ipop : myIpops) {
                    Map<String,Object> props = new LinkedHashMap<String,Object>();
                    
                    try {
                        populateNameAndDescription(props, ipop);
                        populateNumIpopMembers(props, ipop);
                        populateAppsInfo(props, ipop);
                    } catch (Exception ex) {
                        log.error("Error getting population attributes", ex);
                        continue;
                    }
                        
                    ipopJson.key(ipop.getName());
                    ipopJson.object();
                    
                    for (String key : props.keySet()) {
                        ipopJson.key(key);
                        ipopJson.value(props.get(key));
                    }

                    ipopJson.endObject();
                }
            }

            ipopJson.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            result = "{}";
            log.error("Failed to get information for ipops", e);
        }
        
        return result;
    }

    private void populateNumIpopMembers(Map<String,Object> props, GroupDefinition ipop) 
        throws JSONException, GeneralException {
        
        QueryOptions ipopQuery = new QueryOptions(ipop.getFilter());
        ipopQuery.setDistinct(true);
        int numIpopMembers = getContext().countObjects(Identity.class, ipopQuery);
        
        props.put("numIpopMembers", numIpopMembers);
    }
    
    private void populateNameAndDescription(Map<String,Object> props, GroupDefinition ipop) 
        throws JSONException {
        
        props.put("name", ipop.getName());
        props.put("description", ipop.getDescription());
    }

    private void populateAppsInfo(Map<String,Object> props, GroupDefinition ipop)
            throws GeneralException, JSONException {
        
        List<String> appIdsList = new ArrayList<String>();
        List<String> appNamesList = new ArrayList<String>();
        
        Filter ipopFilter = ipop.getFilter();
        ipopFilter = FilterConverter.convertFilter(ipopFilter, Identity.class, "identity");
        QueryOptions options = new QueryOptions(ipopFilter);
        options.setDistinct(true);
        options.setOrderBy("application.name");
        Iterator<Object[]> appInfos;
        
        try {
            appInfos = getContext().search(Link.class, options, Arrays.asList("application.id", "application.name"));
        } catch (Throwable t) {
            log.error("The Population named " + ipop.getName() + " generated an error when attempting to query for applications associated with its members.  No applications can be detected for it as a result.", t);
            appInfos = null;
        }
        
        if (appInfos != null) {
            while (appInfos.hasNext()) {
                Object[] next = appInfos.next();
                String appId = (String) next[0];
                String appName = (String) next[1];
                // If our population consists entirely of Identity IQ users who do not have any accounts
                // outside of Identity IQ (i.e. spadmin), we can get nulls in our result set.  We have to
                // guard against that situation here even if though doesn't seem to make sense --Bernie
                if (appId != null && appName != null) {
                    appIdsList.add(appId);
                    appNamesList.add(appName);
                }
            }                        
        }

        JSONArray appNamesJson = new JSONArray(appNamesList);
        props.put("applications", appNamesJson);

        JSONArray appIdsJson = new JSONArray(appIdsList);
        props.put("applicationIds", appIdsJson);
    }
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    
    public List<ColumnConfig> getIpopColumns() throws GeneralException {
        return super.getUIConfig().getPopulationTableColumns();
    }
    
    public List<ColumnConfig> getSubGroupColumns() throws GeneralException {
            return super.getUIConfig().getSubGroupDefinitionTableColumns();
    }
    
    public String getSubGroupColumnJSON() throws GeneralException {
    	return super.getColumnJSON(getDefaultSortColumn(), getSubGroupColumns());
    }
    
    void loadColumnConfig() {
        try{
            if(searchType!=null && searchType.equals(ATT_SEARCH_TYPE_POP)) {
                this.columns = getIpopColumns();
            } else {
                this.columns = getSubGroupColumns();
            }
        }catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }    

    public int getMyIpopsCount() throws GeneralException{
        onlyIpops = true;
        groupFactory = null;
        return getCount();
    }

    @Override 
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions qo =  super.getQueryOptions();

        if(groupFactoryName !=null) {
            groupFactory = getContext().getObjectByName(GroupFactory.class, groupFactoryName);
        }
        if(groupFactory != null) {
            qo.add(Filter.eq("factory", groupFactory));
        }
        if(onlyIpops) {
                // this filter should look the same as in
                // CertificationScheduleBean.getAvailableIPOPs()
            Identity loggedInUser = this.getLoggedInUser();
            PopulationFilterUtil.addPopulationOwnerFiltersToQueryOption( qo, loggedInUser );
        }

        if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
            qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
        
        qo.setScopeResults(true);
        
        return qo;
    }

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

    @Override
    public Map<String, String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        if(searchType!=null && searchType.equals(ATT_SEARCH_TYPE_POP)) {
            sortMap.put("name", "name");
            sortMap.put("description", "description");
            sortMap.put("private", "private");
            sortMap.put("indexed", "indexed");
        } else {
            sortMap.put("name", "name");
            sortMap.put("memberCount", "index.memberCount");
            sortMap.put("compositeScore", "index.compositeScore");
            sortMap.put("totalViolations", "index.totalViolations");
            sortMap.put("modified", "index.created");
        }
        return sortMap;
    }

    @Override
    public Object convertColumn(String name, Object value) {
        try {
            if (ATT_SEARCH_TYPE_POP.equals(this.searchType)) {
                if ("private".equals(name)) {
                    if ((Boolean)value == true ) {
                        return getMessage(MessageKeys.PRIVATE);
                    } else {
                        return getMessage(MessageKeys.PUBLIC);
                    }
                } else if ("indexed".equals(name)) {
                    if ((Boolean)value == true) {
                        return getMessage(MessageKeys.TXT_TRUE);
                    } else {
                        return getMessage(MessageKeys.TXT_FALSE);
                    }
                }
            } else {
                if ("index.compositeScore".equals(name)) {
                    Map<String,Object> compositeScore = new HashMap<String, Object>();
                    int compositeScoreValue = (value != null) ? (Integer)value : 0;
                    compositeScore.put("score", compositeScoreValue);
                    compositeScore.put("color", WebUtil.getScoreColor(compositeScoreValue));
                    return compositeScore;
                }
            }
        } catch (GeneralException e) {
            //converColumn doesn't throw GeneralException, so throw RuntimeException instead
            throw new RuntimeException(e);
        }
        
        return value;
    }
    

     /**
     * @return the componentOptions
     *
     * Given a groupChoice (the name of a GroupFactory) return the
     * list of all GroupDefinitions generated by that factory.
     */
    public String getFactoryDefinitions() throws GeneralException{

         if (GroupFactoryListBean.ATT_UNGROUPED.equalsIgnoreCase(groupFactoryName)){
            groupFactoryName = null;
            this.onlyIpops = true;
         }

         List<String> cols = Arrays.asList("id","name");
         int count = getContext().countObjects(getScope(), getQueryOptions());
         Iterator<Object[]> rows = getContext().search(getScope(), getQueryOptions(), cols);

         ExtGridResponse response = new ExtGridResponse(cols, rows, count, true);
         String json = null;
         try {
             json = response.getJson();
         } catch (JSONException e) {
             log.error(e);
             return JsonHelper.failure();
         }

         return json;
     }
    
    public String getSubGroupsJSON() throws GeneralException {
        Map<String, Object> response = new HashMap<String, Object>();

        //We can't use getGridResponseJson because we need the root 
        //to be 'subgroups' for the grid. 
        List<Map<String,Object>> rows = getRows();
        makeJsonSafeKeys(rows);
        
        response.put("totalCount", getCount());
        response.put("subgroups", rows);

        return JsonHelper.toJson(response);
    }

    

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    public String deleteAction() throws GeneralException {

        String selected = getSelectedId();
        if (selected != null && selected.length() > 0) {
            GroupDefinition gd = getContext().getObjectById(GroupDefinition.class, selected);
            if (gd != null) {
                Grouper grouper = new Grouper(getContext());
                grouper.deleteGroup(gd);
            }
        }
        return null;
    }

    public String editAction() throws GeneralException {

        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            next = null;
        }
        else {
            getSessionScope().put(ATT_OBJECT_ID, selected);
            next = "editGroupDefinition";
//          make sure any lingering state is cleared 
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");
        }
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        
        return next;
    }
    
    public String editSubGroupAction() throws GeneralException {
        
        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            next = null;
        }
        else {
            getSessionScope().put(ATT_OBJECT_ID, selected);
            next = "editSubGroup";
        }

        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        
        return next;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Helpers/Util Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    private void restore() {

        Map session = getSessionScope();
        Object o2 = session.get(GroupFactoryListBean.ATT_OBJECT_ID);
        if(o2!=null) {
            try {
                groupFactory = getContext().getObjectById(GroupFactory.class, (String)o2);
            } catch (GeneralException ge) {
                Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_GROUP_CANT_GET_GRPFACT, ge);
                log.error(msg.getLocalizedMessage(), ge);
            }
        } else {
			groupFactory = null;
        }
    }

    /**
     * Returns a list of select option beans to be used by the UI.  The value of the select
     * option bean is set to the id of the group def and the label is set to the group def name
     */
    public List<SelectOptionBean> getDefinitionSelectOptionList(String groupChoice) throws GeneralException {
        List<SelectOptionBean> items = new ArrayList<SelectOptionBean>();

        if("".equals(groupChoice)) {
            return null;
        }    
        
        if(groupChoice!=null && !groupChoice.equals("")) {
            //In the case where we want all group definitions who don't have a factory
            if(GroupFactoryListBean.ATT_POPULATIONS.equals(groupChoice.toLowerCase())){
                onlyIpops = true;
            } else {
                groupFactoryName = groupChoice;                
            }
            
            List<GroupDefinition> groups = getObjects();
            
            if(groups!=null && groups.size()>0) {
                // Iter over groups and create a SelectItem list. Pull out the null value option 
                // so we can stick it at the top of the list once we've sorted the other options.
                SelectItem emptyValueItem = null;
                for(GroupDefinition group : groups) {
                    if (group!=null && group.getName()!=null){
                        items.add(new SelectOptionBean(group.getId(), group.getName(), false));
                    }
                }
            }
        }

        if(groupChoice!=null && !groupChoice.equalsIgnoreCase(GroupFactoryListBean.ATT_GLOBAL)) {
            items.add(0, new SelectOptionBean(ATT_SHOW_ALL,
                    getMessage(MessageKeys.SHOW_ALL), false));
        }
        
        if(groupChoice==null) {
            items.add(new SelectOptionBean("",
                    getMessage(MessageKeys.SELECT_GROUP), false));
        }
        return items;
    }


    public List<String> getDefinitionList(String groupChoice) throws GeneralException {
        List<String> items = new ArrayList<String>();
        if("".equals(groupChoice)) {
            return null;
        }  
        if(groupChoice!=null && !groupChoice.equals("")) {
            if(groupChoice.compareToIgnoreCase(GroupFactoryListBean.ATT_UNGROUPED)==0) {
                onlyIpops = true;                
            } else {
                groupFactoryName = groupChoice;                
            }
            
            List <GroupDefinition> groups = getObjects();
            
            if(groups!=null && groups.size()>0) {

                // Iter over groups and create a SelectItem list. Pull out the null value option 
                // so we can stick it at the top of the list once we've sorted the other options.
                SelectItem emptyValueItem = null;
                for(GroupDefinition group : groups) {
                    if (group!=null && group.getName()!=null){
                        items.add(group.getName());
                    }
                }           
                items.add(0,ATT_SHOW_ALL);

            } else {
                items.add(getMessage(MessageKeys.NO_VALUES_AVAIL));
            }
        } else {
            items.add("");
        }
        
        return items;
    }

    /**
     * @return the componentOptions
     *
     * Given a groupChoice (the name of a GroupFactory) return the
     * list of all GroupDefinitions generated by that factory.
     */
    public SelectItem[] getDefinitionOptions(String groupChoice, boolean addNoValues) throws GeneralException{
        List<SelectItem> items = new ArrayList<SelectItem>();
        if(groupChoice!=null && !groupChoice.equals("")) {
            if(GroupFactoryListBean.ATT_UNGROUPED.equals(groupChoice)) {
                onlyIpops = true;                
            } else {
                groupFactoryName = groupChoice;                
            }
            
            List <GroupDefinition> groups = getObjects();
            
            if(groups!=null && groups.size()>0) {

                // Iter over groups and create a SelectItem list. Pull out the null value option 
                // so we can stick it at the top of the list once we've sorted the other options.
                SelectItem emptyValueItem = null;
                for(GroupDefinition group : groups) {
                    if (group!=null && group.getName()!=null){
                        // jsl - formerly tried to analyze the filter, now we have a nice flag
                        if (group.isNullGroup()) {
                            emptyValueItem = new SelectItem(group.getName(), group.getName());
                        }else{
                            items.add(new SelectItem(group.getName(), group.getName()));
                        }
                    }
                }                     
                // Add the 'No Value' select option to the top of the list.
                if (emptyValueItem!=null)
                    items.add(0, emptyValueItem);

            } else {
                if(addNoValues)
                    items.add(new SelectItem("", getMessage(MessageKeys.NO_VALUES_AVAIL)));
            }
        } else {
            items.add(new SelectItem("", getMessage(MessageKeys.SELECT_GROUP)));
        }
        definitionOptions = items.toArray(new SelectItem[items.size()]);
        return definitionOptions;
    }



//  Properties



    /**
     * @return the definitionOptions
     */
    public SelectItem[] getDefinitionOptions() {
        return definitionOptions;
    }

    /**
     * @param definitionOptions the definitionOptions to set
     */
    public void setDefinitionOptions(SelectItem[] definitionOptions) {
        this.definitionOptions = definitionOptions;
    }

    /**
     * @return the groupFactory
     */
    public GroupFactory getGroupFactory() {
        return groupFactory;
    }


    /**
     * @param groupFactory the groupFactory to set
     */
    public void setGroupFactory(GroupFactory groupFactory) {
        this.groupFactory = groupFactory;
    }
    
    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    public GridState getIpopGridState() {
        if (null == ipopGridState) {
            ipopGridState = loadGridState(IPOP_GRID_STATE);
        }
        return ipopGridState;
    }
    
    public void setIpopGridState(GridState gridState) {
        this.ipopGridState = gridState;
    }
    
    public GridState getSubGroupGridState() {
        if (null == subGroupGridState) {
            subGroupGridState = loadGridState(SUBGROUP_GRID_STATE);
        }
        return subGroupGridState;
    }
    
    public void setSubGroupGridState(GridState gridState) {
        this.subGroupGridState = gridState;
    }
    
////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Group Definition List";
    }

    public String getNavigationString() {
        return null;
    }

    public Object calculatePageState() {
        Object[] state = new Object[2];
        state[0] = this.getGridState();
        state[1] = this.getIpopGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
        setIpopGridState((GridState) myState[1]);
    }
}
