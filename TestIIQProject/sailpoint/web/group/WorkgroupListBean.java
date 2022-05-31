/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Terminator;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SODConstraint;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author dan.smith
 *
 */
public class WorkgroupListBean extends BaseListBean<Identity> 
    implements NavigationHistory.Page {

    private static Log log = LogFactory.getLog(WorkgroupListBean.class);
    
    public static final String ATT_OBJECT_ID = "workgroupId";
    public static final String GRID_STATE = "workgroupListGridState";
    List<ColumnConfig> columns;

    /**
     *
     */
    public WorkgroupListBean() {
        super();
        setScope(Identity.class);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.add(Filter.eq(Identity.ATT_WORKGROUP_FLAG, true));

        String queryByName = getRequestParameter("name");
        if ( Util.getString(queryByName) != null  )  {
            ops.add(Filter.ignoreCase(Filter.like("name", queryByName,MatchMode.START)));
        }

        // Restrict results by scope or owner.
        ops.setScopeResults(true);

        return ops;
    }

    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

    @Override
    public Map<String, String> getSortColumnMap() {
        Map<String, String> sortMap = new HashMap<String, String>();
        //the keys here should match what is being posted from the grid
        sortMap.put("name", "name");
        sortMap.put("description", "description");
        sortMap.put("modified", "modified");

        return sortMap;
    }

    /**
     *
     */
    public String select() throws GeneralException {
        String next = super.select();

        if ( next != null && next.equals("edit") )
            next = "editWorkgroup";

        return next;
    }
    
    public String getGridStateName() {
    	return GRID_STATE;
    }
    
    @Override
    public String getGridResponseJson() throws GeneralException {
        //We have to override this so that 'workgroups' can be the root
        //to work with groupGrid.js
        Map <String, Object> response = new HashMap<String,Object>();
        List<Map<String,Object>> rows = getRows();
        makeJsonSafeKeys(rows);
        response.put("totalCount", getCount());
        response.put("workgroups", rows);
        return JsonHelper.toJson(response);
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////
    //
    // Helpers/Util Methods
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getWorkgroupTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load workgroup column config: " + ge.getMessage());
        }
    }    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "WorkGroups List";
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
    
    /**
     * "Delete" action handler for the list page.
     * Remove the object from the persistent store.
     * Basic implementaiton is from the BaseList bean but added a check to make
     * sure we aren't trying to remove a group that's in antother groups
     * inheritance chain.
     *
     * @throws GeneralException
     */
    @Override
    public void deleteObject(ActionEvent event) {
        String selectedId = getSelectedId();
        if ( selectedId == null ) return;

        Identity obj = null;
        try {
            obj = getContext().getObjectById(getScope(), selectedId);
        } catch (GeneralException ex) {
            String msg = "Unable to find group object with id '" + getSelectedId() + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }

        if ( obj == null ) return;
        
        try {
            boolean okToDelete = true;

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("owner", obj));

            if (getContext().countObjects(WorkItem.class, ops) > 0) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CANNOT_DELETE_WORKGROUP), null);
                okToDelete = false;
            }

            // 20180628 IIQSR-4.  We now prevent the deletion of a workgroup that is set as the Identity value
            // for the Policy Violation Owner of any SOD Policy Rule (SODConstraint).
            // The SODConstraint violationOwnerType could or could not be "Identity" when this workgroup is the ViolationOwner;
            // however, even if another violationOwnerType is used, if the ViolationOwner is this workgroup,
            // the SODConstraint would be deleted (which we are now preventing).
            // Fixing what will be deleted will be determined in another ETN.
            QueryOptions sodOps = new QueryOptions();
            sodOps.add(Filter.eq("violationOwner", obj));

            if (getContext().countObjects(SODConstraint.class, sodOps) > 0) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CANNOT_DEL_SOD_OWNER_WRKGRP), null);
                okToDelete = false;
            }
            
            if (okToDelete) {
                Terminator term = new Terminator(getContext());
                term.deleteObject(obj);
                getContext().commitTransaction();
            }

        } catch (GeneralException ex) {
            String msg = "Unable to remove group with id '" + getSelectedId() + "' and name '"+ obj.getName() + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }
    }  // deleteObject(ActionEvent)

    @SuppressWarnings("unchecked")
    public String editAction() throws GeneralException {
        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            next = null;
        }
        else {
            getSessionScope().put(ATT_OBJECT_ID, selected);
            next = "editWorkgroup";
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");
        }
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        return next;
    }

    @SuppressWarnings("unchecked")
    public String createAction() {
        getSessionScope().remove(ATT_OBJECT_ID);
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");
        return "newWorkgroup";
    }

}  // class WorkgroupListBean
