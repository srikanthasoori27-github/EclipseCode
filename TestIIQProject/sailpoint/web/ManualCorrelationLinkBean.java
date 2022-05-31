/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONWriter;

import sailpoint.integration.ListResult;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowTarget;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.extjs.GenericJSONObject;
import sailpoint.web.extjs.GridField;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;

/**
 * Handles link operations for the identity correlation tool
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ManualCorrelationLinkBean extends BaseListBean<Link> {

    private static Log log = LogFactory.getLog(ManualCorrelationLinkBean.class);

    private static final String MAN_CORRELATION_LINK_GRID = "manualCorrelationLinkGrid";
          
    private List<String> columnNames;
    private Map<String, String> queryParams;
    private Map<String, ObjectAttribute> attributeConf;
    private String json;

    public ManualCorrelationLinkBean() {
        setScope(Link.class);

        /**
         * Any query parameters will be passed as the attribute name
         * plus the 'q_' prefix. 
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

    /* ----------------------------------------------------------------
    *
    *  ListBean methods
    *
    * ---------------------------------------------------------------- */

    /**
     * Gets projection columns. This includes id, native id, display name and
     * creation date, plys all link attributes.
     *
     * @return
     * @throws GeneralException
     */
    public List<String> getProjectionColumns() throws GeneralException {

        if (columnNames != null)
            return columnNames;

        columnNames = new ArrayList<String>();

        // default cols
        columnNames.add("id"); //0
        columnNames.add("nativeIdentity"); //1
        columnNames.add("displayName");//2
        columnNames.add("created");

        Map<String, ObjectAttribute> attrs = getAttributeConfig();
        for (ObjectAttribute attr : attrs.values()) {
            if (attr != null && attr.getName() != null && attr.isSearchable())
                columnNames.add(attr.getName());
        }

        return columnNames;
    }
    
    /**
     * Gets query options. Filters on select display name or native identity,
     * app id, and any additional attributes. Only uncorrelated identities
     * are returned. 
     *
     * @return
     * @throws GeneralException
     */
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.setScopeResults(false);
        ops.add(Filter.eq("identity.correlated", false));

        if (queryParams.containsKey("name") && !"".equals(queryParams.get("name"))) {
            ops.add(Filter.or(
                Filter.like("displayName", queryParams.get("name"), Filter.MatchMode.START),
                Filter.like("nativeIdentity", queryParams.get("name"), Filter.MatchMode.START)
            ));
        }

        if (queryParams.containsKey("applicationId")) {
            ops.add(Filter.eq("application.id", queryParams.get("applicationId")));
        }

        for (String param : queryParams.keySet()) {
            if (getAttributeConfig().containsKey(param) && "false".equals(queryParams.get(param))) {
                ops.add(Filter.or(Filter.isnull(param), Filter.eq(param, "false")));
            }
        }

        return ops;
    }


    /**
     * Gets map of sortable columns based on the projection columns in the query.
     * Replaces any '.' in the property name with an '_'.
     *
     * @return
     * @throws GeneralException
     */
    public Map<String, String> getSortColumnMap() throws GeneralException {
        Map<String, String> mapping = new HashMap<String, String>();
        for (String col : getProjectionColumns()) {
            mapping.put(col.replace(".", "_"), col);
        }
        return mapping;
    }

    /**
     * Creates a map of Link extended attrbiutes where the attribute
     * name is the key.
     * @return
     * @throws GeneralException
     */
    private Map<String, ObjectAttribute> getAttributeConfig() throws GeneralException {

        if (attributeConf != null)
            return attributeConf;

        attributeConf = new HashMap<String, ObjectAttribute>();

        ObjectConfig config = super.getLinkConfig();
        List<ObjectAttribute> attributes = config.getExtendedAttributeList();
        if (attributes != null) {
            for (ObjectAttribute attr : attributes) {
                attributeConf.put(attr.getName(), attr);
            }
        }

        return attributeConf;
    }
    

    /**
     * Returns EXT grid configuration json for the manual correlation
     * link grid. This includes a couple of base columns, plus and the
     * searchable, non-multi attributes from the link configuration.
     *
     * @return
     * @throws GeneralException
     */
    private List<ColumnConfig> getGridColumns() throws GeneralException {

        List<ColumnConfig> fields = new ArrayList<ColumnConfig>();

        ColumnConfig nameCol = new ColumnConfig(getMessage(MessageKeys.IDENTITY_CORRELATION_LINK_GRID_COL_ACCOUNT_ID),
                "nativeIdentity");
        nameCol.setFlex(1);
        nameCol.setSortable(true);
        nameCol.setRenderer("SailPoint.LinkDetailPopup.renderGridCellLink");
        fields.add(nameCol);

        ColumnConfig displayNameCol = new ColumnConfig(
                getMessage(MessageKeys.IDENTITY_CORRELATION_LINK_GRID_COL_ACCOUNT_DISPLAY_NAME),
                "displayName");
        displayNameCol.setFlex(1);
        displayNameCol.setSortable(true);
        fields.add(displayNameCol);

        ColumnConfig createdCol = new ColumnConfig(
                getMessage(MessageKeys.IDENTITY_CORRELATION_LINK_GRID_COL_ACCOUNT_CREATE_DATE),
                "created");
        createdCol.setWidth(140);
        createdCol.setSortable(true);
        createdCol.setRenderer("SailPoint.Date.DateTimeRenderer");
        fields.add(createdCol);

        
        Map<String, ObjectAttribute> attrs = getAttributeConfig();
        for (String key : attrs.keySet()) {
            ObjectAttribute attr = attrs.get(key);
            if (attr != null && attr.isSearchable() && !attr.isMulti()) {

                ColumnConfig col = new ColumnConfig(getMessage(attr.getDisplayableName(getLocale())), attr.getName());
                col.setWidth(130);
                col.setSortable(true);

                // We allow the user to edit boolean attrs in the grid
                if (attr.isEditable() && ObjectAttribute.TYPE_BOOLEAN.equals(attr.getType())) {
                    col.setEditorClass("widget.checkcolumn");
                }

                if (ObjectAttribute.TYPE_DATE.equals(attr.getType())) {
                    col.setRenderer("SailPoint.Date.DateTimeRenderer");
                }

                fields.add(col);
            }
        }

        return fields;
    }
       
    
    /* ----------------------------------------------------------------
  *
  *  Public UI methods
  *
  * ---------------------------------------------------------------- */

    /**
     * Returns list of extended properties that the user can filter the
     * link grid on. The actual intent is to allow the user to filter
     * the list of service accounts so they can focus on accounts that
     * actually need correlation. Because we don't know which attrs indicate
     * this we just allow them to filter on all booleans.
     *
     * @return
     */
    public String getAccountTypeMenu() {

        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);
        try {
            writer.array();
            for (ObjectAttribute attr : getAttributeConfig().values()){
                if (ObjectAttribute.TYPE_BOOLEAN.equals(attr.getType())) {
                    GenericJSONObject item = new GenericJSONObject();
                    item.set("property", "q_" + attr.getName());
                    item.set("checked", true);
                    item.set("text", getMessage(attr.getDisplayableName(getLocale())));
                    item.getJson(writer);
                }
            }
            writer.endArray();
        } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure();
        }

        return jsonString.toString();
    }

    /**
     * JSON search result string. Includes column metadata.
     * @return
     */
    public String getSearchResults() {

        if (json != null)
            return json;

        try {

            if (!queryParams.containsKey("applicationId")) {
                return "{}";
            }
         
            getContext().setScopeResults(false);
            QueryOptions ops = this.getQueryOptions();
            
            List<ColumnConfig> columnConf = getGridColumns();
            ColumnConfig identId = new ColumnConfig("id","id");
            identId.setFieldOnly(true);
            columnConf.add(identId);
            
            ViewEvaluationContext viewContext = new ViewEvaluationContext(this, columnConf);

            ViewBuilder viewBuilder = new ViewBuilder(viewContext, Link.class, columnConf);

            ListResult res = viewBuilder.getResult(ops);          
            GridResponseMetaData meta = viewBuilder.calculateGridMetaData();


             for(Map<String, Object> row : (List<Map>)(res.getObjects())) {
                 
                 //Convert Boolean from String to Boolean
                 for(int j=0; j<row.size(); j++)
                 {
                     ObjectAttribute attr = getAttributeConfig().containsKey(getProjectionColumns().get(j)) ?
                             getAttributeConfig().get(getProjectionColumns().get(j)) : null;
                             
                     if (attr != null && BaseAttributeDefinition.TYPE_BOOLEAN.equals(attr.getType()))
                     {
                         row.put(attr.getName(), "true".equals(row.get(attr.getName())));
                     }
                     
                 }
                 //Convert Date to Time
                 for(Map.Entry<String, Object> entry : row.entrySet())
                 {
       
                       if(entry.getValue() instanceof Date)
                         {
                             Date d = (Date)entry.getValue();
                             entry.setValue(d.getTime());
                         }
                 }
                 addPendingMoveInfotoMap(row);
             }
            
             meta.addField(new GridField("pendingMove"));
            res.setMetaData(meta.asMap());
            json = JsonHelper.toJson(res);

        } catch (Exception e) {
            log.error(e);
            json = JsonHelper.failure();
        }

        return json;
    }
    
    private Map<String, Object> addPendingMoveInfotoMap(Map<String, Object> row)
    {
        QueryOptions existsOptions = new QueryOptions();
        existsOptions.add(Filter.eq("className", Link.class.getName()));
        existsOptions.add(Filter.eq("objectId", (String)row.get("id")));
        existsOptions.add(Filter.notnull("workflowCase"));
        try {
            if (getContext().countObjects(WorkflowTarget.class, existsOptions) > 0) {
                row.put("pendingMove", true); 
            } else {
                row.put("pendingMove", false);
            }
        } catch (GeneralException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return row;
    }

    /**
     * Returns json string containing the names and values of all
     * attributes for a given link.
     *
     * @return
     */
    public String getLinkAttributes() {

        String id = this.getRequestParameter("id");

        GenericJSONObject obj = new GenericJSONObject();
        String out = "{}";

        try {
            getContext().setScopeResults(false);
            Link link = getContext().getObjectById(Link.class, id);
            if (link != null){                
                obj.set(getMessage(MessageKeys.NATIVE_IDENTITY), link.getNativeIdentity());
                if (link.getAttributes() != null) {
                    for (String key : link.getAttributes().keySet()) {
                        // stuff the value in a msg object which handles localizing lists, dates, bools, etc.
                        Message attrVal = new Message(MessageKeys.MSG_PLAIN_TEXT,
                            link.getAttributes().get(key));
                        obj.set(key, attrVal.getLocalizedMessage(getLocale(), getUserTimeZone()));
                    }
                    out = obj.getJson();
                }
            }
        } catch (Exception e) {
            log.error(e);
        }

        return out;
    }
    
    public String getPendingActions() {
        
        String id = this.getRequestParameter("id");
        if (id == null) {
            log.error("Could not get id");
            return JsonHelper.failure();
        }
        
        try {
            List<Map<String, String>> pendingActions = new ArrayList<Map<String, String>>();
            
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("className", Link.class.getName()));
            ops.add(Filter.eq("objectId", id));
            List<WorkflowTarget> targets = getContext().getObjects(WorkflowTarget.class, ops);
            for (WorkflowTarget target : targets) {

                WorkflowCase wfcase = target.getWorkflowCase();
                if (!Workflow.TYPE_IDENTITY_CORRELATION.equalsIgnoreCase(wfcase.getWorkflow().getType())) {
                    continue;
                }

                String identityName = (String) wfcase.get("identityName");
                Map<String, String> action = new HashMap<String, String>();
                action.put("pendingAction", new Message(MessageKeys.IDENTITY_CORRELATION_PENDING_MOVE_FORMAT, identityName).getLocalizedMessage()); 
                pendingActions.add(action);
            }
            
            return JsonHelper.success("totalCount", pendingActions.size(), "rows", pendingActions);

        } catch (Exception ex) {
            log.error(ex);
            return JsonHelper.failure();
        }
        
    }
    
    /**
     * JSON data defining the column model for the manual correlation
     * link grid.
     * @return
     */
    public String getLinkGridColModel() {

        String out = JsonHelper.emptyList();
        try {
            List<ColumnConfig> fields = this.getGridColumns();

            if (!fields.isEmpty()) {
                GridResponseMetaData meta = new GridResponseMetaData(getGridColumns(), null);
                out = JsonHelper.toJson(meta);
            }
        } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure();
        }

        return out;
    }

    /**
     * ID  used to store and retrieve the persisted state of the
     * manual correlation link grid.
     * @return
     */
    public String getGridStateName(){
        return MAN_CORRELATION_LINK_GRID;
    }

    
}
