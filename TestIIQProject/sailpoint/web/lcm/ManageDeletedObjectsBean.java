/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.lcm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.DeletedObject;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;

/**
 * Handles the read and restore of deleted objects, currently supported by Active Directory.
 *
 */

public class ManageDeletedObjectsBean <E extends SailPointObject> extends BaseListBean {

    private static Log log = LogFactory.getLog(ManageDeletedObjectsBean.class);

    private static final String RECYCLEBIN_ACCOUNT_GRID = "recycleBinAccountsGrid";
    private static final String MANAGE_RECYCLBIN = "manageRecycleBin";
    public static final Class SCOPE_CLASS = DeletedObject.class;
    private static final String RESTORE_WORKFLOW_NAME = "Restore Deleted Objects";
    private Map<String, String> queryParams;
    private String selectedAppId;
    /**
     * The id of the selected deleted object/s (users/groups) which are requested to be restored. 
     */
    private List<String> accountSelectedIds;
    private List<String> groupSelectedIds;
    private String selectedApplicationId;
    
    List<ColumnConfig> groupGridColumns;
    List<ColumnConfig> columns;

    public ManageDeletedObjectsBean() {
        setScope(SCOPE_CLASS);
        /**
         * Any query parameters will be passed as the attribute name plus the
         * 'q_' prefix.
         */
        queryParams = new HashMap<String, String>();
        for (Object param : this.getRequestParam().keySet()) {
            if (param != null && param.toString().startsWith("q_")) {
                String p = param.toString();
                String val = this.getRequestParameter(p);

                if (val != null && val.trim().length() > 0) {
                    String propertyName = p.substring(2, p.length()).replace("_", ".");
                    queryParams.put(propertyName, val);
                }
            }
        }

    }

    /*
     * ----------------------------------------------------------------
     * 
     * ListBean methods
     * 
     * ----------------------------------------------------------------
     */
 
    public QueryOptions getQueryOptions() throws GeneralException {
        String objectType = getRequestParameter("objectType");
        return getQueryOptions(objectType);
    }

    /**
     * Gets query options. Filters on select display name or native identity,
     * app id, and any additional attributes.
     * @param objectType object type whether account or group
     * @return
     * @throws GeneralException
     */
    public QueryOptions getQueryOptions(String objectType)
            throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.setScopeResults(false);

        if (queryParams.containsKey("name")
                && !"".equals(queryParams.get("name"))) {
            if (objectType != null && objectType.equalsIgnoreCase("group")) {
                ops.add(Filter.or(Filter.like("displayName", queryParams.get("name"), Filter.MatchMode.START)));
            } else {
                ops.add(Filter.or(Filter.like("displayName",
                        queryParams.get("name"), Filter.MatchMode.START),
                        Filter.like("nativeIdentity", queryParams.get("name"),
                                Filter.MatchMode.START)));
            }
        }

        if (objectType != null && objectType.equalsIgnoreCase("group")) {
            ops.add(Filter.eq("objectType", "group"));
        } else {
            ops.add(Filter.eq("objectType", "account"));
        }
        if (queryParams.containsKey("applicationId")) {
            ops.add(Filter.eq("application.id", queryParams.get("applicationId")));
        } else {
            selectedAppId = getRequestParameter("applicationId");
            if(Util.isNotNullOrEmpty(selectedAppId)) {
                ops.add(Filter.eq("application.id", selectedAppId));
            }
        }

        return ops;
    }

    /**
     * Get the columns for account grid from the column configs present in the configuration file
     * @return List of ColumnConfig
     * @throws GeneralException
     */
    private List<ColumnConfig> getGridColumns() throws GeneralException {
        if (columns == null) {
            columns = (List<ColumnConfig>) getUIConfig().getAttributes().get(getClass().getName() );
        }
        return columns;
    }

    /**
     * Get the columns for group grid from the column configs present in the configuration file
     * @return List of ColumnConfig
     * @throws GeneralException
     */
    public String getGroupColumnJSON() throws GeneralException {
        return getColumnJSON("displayableName", getGroupGridColumns());
    }

    public List<ColumnConfig> getGroupGridColumns() throws GeneralException {
        if (groupGridColumns == null) {
            groupGridColumns = (List<ColumnConfig>) getUIConfig().getAttributes().get(getClass().getName() + "_groups");
        }
        return groupGridColumns;
    }

    /*
     * ----------------------------------------------------------------
     * 
     * Public UI methods
     * 
     * ----------------------------------------------------------------
     */
    /**
     * Get the Ids of the selected accounts
     */
    public List<String> getAccountSelectedIds() {
        return accountSelectedIds;
    }

    /**
     * Set the Ids of the selected accounts
     * @param accountIds
     */
    public void setAccountSelectedIds(List<String> accountIds) {
        this.accountSelectedIds = accountIds;
    }

    /**
     * Get the Ids of the selected groups
     * @return
     */
    public List<String> getGroupSelectedIds() {
        return groupSelectedIds;
    }

    /**
     * Set the Ids of the selected groups
     * @param groupIds
     */
    public void setGroupSelectedIds(List<String> groupIds) {
        this.groupSelectedIds = groupIds;
    }
    
    /**
     * Get the Id of the selected application
     * @return
     */
    public String getSelectedApplicationId() {
        return selectedApplicationId;
    }

    /**
     * Set the Id of the selected application
     * @param selectedApplicationId
     */
    public void setSelectedApplicationId(String appId) {
        this.selectedApplicationId = appId;
    }

    /**
     * JSON data defining the column model for the deleted Objects account grids.
     * 
     * @return
     */
    public String getAccountGridColModel() {
        String out = JsonHelper.emptyList();
        try {
            List<ColumnConfig> fields = this.getGridColumns();

            if (!fields.isEmpty()) {
                GridResponseMetaData meta = new GridResponseMetaData(fields, null);
                out = JsonHelper.toJson(meta);
            }
        } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure();
        }
        return out;
    }

    /**
     * JSON data defining the column model for the deleted Objects group grids.
     * 
     * @return
     */
    public String getGroupGridColModel() {
        String out = JsonHelper.emptyList();
        try {
            List<ColumnConfig> fields = this.getGroupGridColumns();
            if (!fields.isEmpty()) {
                GridResponseMetaData meta = new GridResponseMetaData(fields, null);
                out = JsonHelper.toJson(meta);
            }
        } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure();
        }
        return out;
    }

    /**
     * ID used to store and retrieve the persisted state of the deleted objects grid.
     * 
     * @return
     */
    public String getGridStateName() {
        return RECYCLEBIN_ACCOUNT_GRID;
    }

    /**
     * This function is called from java script by invoking action on the command button present the JSF page 
     * to restore the deleted objects when clicked on the restore button
     * @return
     */
    public String restoreDeletedObjects() {
        String outcome = "";
        try {
            //check app id
            Application app = getContext().getObjectById(Application.class,getSelectedApplicationId());
            if(app!=null && !app.getBooleanAttributeValue(MANAGE_RECYCLBIN)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.DELETED_OBJECTS_GRID_ERR_MANAGE_RECYCLEBIN_OFF, app.getName()), null);
                Message msg = new Message(Message.Type.Error, MessageKeys.DELETED_OBJECTS_GRID_ERR_MANAGE_RECYCLEBIN_OFF, app.getName());
                addMessageToSession(msg);
                return outcome;
            }
            List<String> userSelectedIds = getAccountSelectedIds();
            List<String> groupSelectedIds = getGroupSelectedIds();
            if (Util.size(userSelectedIds) > 0) {
                for (String id : userSelectedIds) {
                    launchRestoreWorkflow(id, Application.SCHEMA_ACCOUNT);
                }
            }
            if (Util.size(groupSelectedIds) > 0) {
                for (String id : groupSelectedIds) {
                    launchRestoreWorkflow(id, Application.SCHEMA_GROUP);
                }
            }
        } catch (Exception e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.DELETED_OBJECTS_GRID_ERR_LAUNCH_WORKFLOW), null);
            Message msg = new Message(Message.Type.Error, e.getMessage());
            addMessageToSession(msg);
        }
        return outcome;
    }

    /*
     * ----------------------------------------------------------------
     * 
     * Helper Functions
     * 
     * ----------------------------------------------------------------
     */

    /**
     * This function will launch the restore workflow "Restore Deleted Objects" to restore the 
     * deleted objects
     * @param objectId id of the deleted objects to restore
     * @param type object type to restore whether account/group
     * @throws GeneralException
     */
    private void launchRestoreWorkflow(String objectId, String type) throws GeneralException{
        DeletedObject delObj = getContext().getObjectById(DeletedObject.class, objectId);

        WorkflowLaunch wflaunch = new WorkflowLaunch();
        Application app = delObj.getApplication();
        wflaunch.setWorkflowRef(RESTORE_WORKFLOW_NAME);
        wflaunch.setCaseName("Restore request for: " + delObj.getName());
        
        Map<String, Object> vars = new HashMap<String, Object>();
        wflaunch.setVariables(vars);

        vars.put("applicationName", app.getName());
        vars.put("nativeIdentity", delObj.getNativeIdentity());
        vars.put("deletedObjId", delObj.getId());
        vars.put("objectGuid", delObj.getUuid());
        vars.put("objectType", type);
        
        Workflower wflower = new Workflower(getContext());
        if (log.isInfoEnabled()) {
            log.info("Launching Recycle bin workflow");
            log.info(wflaunch.toXml());
        }
        
        WorkflowSession session = wflower.launchSession(wflaunch);
        if (session != null && !session.hasWorkItem()) {
            session.save(getContext());
            
            WorkflowLaunch launch = session.getWorkflowLaunch();
            if (launch != null && launch.isFailed()) {
                addMessageToSession(new Message(Message.Type.Info, MessageKeys.LCM_FAILURE_MESSAGE_REQUESTID, delObj.getName() ));
                List<Message> messages =  launch.getMessages();
                for (Message msg : messages) {
                    if (msg.isError()) {
                        addMessageToSession(msg);
                    }
                }
            }
            else {
                // do we want to show a "status" page before returning"
                // add a default success message if none were set
                if (Util.isEmpty(session.getLaunchMessages())) {
                    addMessageToSession(getDefaultSuccessMessage(session, delObj.getName()));
                } else {
                    for (Message msg : Util.safeIterable(session.getLaunchMessages())) {
                        addMessageToSession(msg);
                    }
                }
            }
        }
    }


    
    /*
     * Form the default success message
     */
    private Message getDefaultSuccessMessage(WorkflowSession session, String requestId) throws GeneralException {
        if (null != requestId) {
            return new Message(Message.Type.Info, MessageKeys.LCM_SUCCESS_MESSAGE_REQUESTID, requestId);
        }

        return new Message(Message.Type.Info, MessageKeys.LCM_SUCCESS_MESSAGE);
    }

}