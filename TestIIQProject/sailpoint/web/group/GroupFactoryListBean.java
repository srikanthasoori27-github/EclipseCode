/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Grouper;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.GroupFactory;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class GroupFactoryListBean extends BaseListBean<GroupFactory> 
implements NavigationHistory.Page {
    
    private static Log log = LogFactory.getLog(GroupFactoryListBean.class);
    
    public static final String ATT_OBJECT_ID = "GroupFactoryId";
    public static final String GRID_STATE = "groupFactoryListGridState";
    

    //String to identify all group definitions that do not belong to a factory
    public static final String ATT_UNGROUPED = "Populations";
    public static final String ATT_POPULATIONS = "populations";

    // String to identify the global group. This will be used as the select item value for
    // UI inputs since we can't assume the word 'Global' will be universal.
    public static final String ATT_GLOBAL = "Global";

    private SelectItem[] factoryOptions;
    List<ColumnConfig> columns;
    
    /**
     * A flag to tell the query whether to gather enabled group factories or not.
     **/
    private boolean hideDisabled;
    
    /**
     * 
     */
    public GroupFactoryListBean() {
        super();
        setScope(GroupFactory.class);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String deleteAction() throws GeneralException {

        String selected = getSelectedId();
        if (selected != null && selected.length() > 0) {
            GroupFactory gf = getContext().getObjectById(GroupFactory.class, selected);
            if (gf != null) {
                Grouper grouper = new Grouper(getContext());
                grouper.deleteGroupFactory(gf);
            }
        }
        return null;
    }
    
    public String createAction() {
        getSessionScope().remove(ATT_OBJECT_ID);
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");
        return "newGroupFactory";
    }
    
    public String editAction() throws GeneralException {

        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            next = null;
        }
        else {
            getSessionScope().put(ATT_OBJECT_ID, selected);
            next = "editGroupFactory";
//          make sure any lingering state is cleared 
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");
        }
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        return next;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }
    
    @Override
    public Map<String, String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        sortMap.put("name", "name");
        sortMap.put("factoryAttribute", "factoryAttribute");
        sortMap.put("description", "description");
        sortMap.put("enabled", "enabled");
        return sortMap;
    }
    
    @Override 
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions qo =  super.getQueryOptions();
        if(hideDisabled)
            qo.add(Filter.eq("enabled", true));
        
        if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
            qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
        
        qo.setScopeResults(true);
        return qo;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getFactoriesJSON() throws GeneralException {
        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String,Object>> rows = getRows();
        makeJsonSafeKeys(rows);

        response.put("totalCount", getCount());
        response.put("groups", rows);

        return JsonHelper.toJson(response);
    }
    
    public SelectItem[] getFactoryOptions() throws GeneralException {
        if(factoryOptions==null) {
            List<SelectItem> items = new ArrayList<SelectItem>();
            hideDisabled = true;
            List<GroupFactory> factories = getObjects();

            if(factories==null) {
                items.add(new SelectItem("", getMessage(MessageKeys.NO_GROUPS_AVAIL)));
            }
            else {
                for (GroupFactory f : factories) {
                    items.add(new SelectItem(f.getName(), f.getName()));
                }
                items.add(0, new SelectItem(ATT_UNGROUPED, getMessage(MessageKeys.POPULATIONS)));
                items.add(0, new SelectItem("", getMessage(MessageKeys.SELECT_GROUP)));
            }

            factoryOptions = items.toArray(new SelectItem[items.size()]);
        }
        return factoryOptions;
    }
    
    public List<Map<String, String>> getFactoryOptionsList() throws GeneralException {

        List<Map<String, String>> items = new ArrayList<Map<String, String>>();

        hideDisabled = true;
        List<GroupFactory> factories = getObjects();

        if (factories != null) {
            for (GroupFactory f : factories) {
                Map<String, String> map = new HashMap<String, String>();
                if (f.getFactoryAttribute() == null)
                    map.put("key", ATT_GLOBAL);
                else
                    map.put("key", f.getName());
                map.put("value", f.getName());
                items.add(map);
            }

            Map<String, String> pops = new HashMap<String, String>();
            pops.put("key", ATT_POPULATIONS);
            pops.put("value", getMessage(MessageKeys.POPULATIONS));
            items.add(0, pops);
        }
        return items;
    }

    /**
     * Check to see if there's a factory option to display. If not
     * we display a message that group factories are not available.
     *
     * @return True if there's at least one group factory.
     * @throws GeneralException
     */
    public boolean isFactoryOptionsListEmpty() throws GeneralException{
        return getCount() < 1;
    }
    
    /**
     * @param factoryOptions the groupOptions to set
     */
    public void setFactoryOptions(SelectItem[] factoryOptions) {
        this.factoryOptions = factoryOptions;
    }
    
    public String getGridStateName() {
    	return GRID_STATE;
    }   
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getGroupTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }    
    
////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Group Factory List";
    }

    public String getNavigationString() {
        return null;
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

}
