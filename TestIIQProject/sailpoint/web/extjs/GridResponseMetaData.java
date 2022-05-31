/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.object.ColumnConfig;
import sailpoint.object.ReportColumnConfig;
import sailpoint.tools.Message;

/**
 * Serializable object used to pass Ext grid metadata to the client. This implementation
 * assumes that your id column is 'id', the root is 'rows' and the total row count is
 * 'rowCount'.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
*/
public class GridResponseMetaData {

    private String root="objects";
    private String totalProperty="count";
    private String id = "id";
    private String groupField;

    /**
     * Current sort state of the grid.
     */
    private GridResponseSortInfo sortInfo;

    /**
     * Exjs grid column metadata
     */

    /**
     * Grid column definition.
     */
    private List<GridColumn> columns;

    /**
     * Grid field list which defines the structure of the EXT Record objects in your
     * store. This list may be derived by examining the dataIndex property in the GridColumns.
     * If your field list matches the column definition, you can leave this 
     * list null and it will be generated for you.
     */
    private List<GridField> fields;

    public GridResponseMetaData(){
        this.fields = new ArrayList<GridField>();
        this.columns = new ArrayList<GridColumn>();
    }

    public GridResponseMetaData(GridResponseSortInfo sortInfo, List<GridColumn> columns) {
        this();
        this.columns.addAll(columns);
        this.sortInfo = sortInfo;
    }
    
    public GridResponseMetaData(List<ColumnConfig> configs, GridResponseSortInfo sortInfo) {
        this();
        this.sortInfo = sortInfo;
        
        if(configs!=null) {
            for(ColumnConfig config : configs) {
                if(!config.isFieldOnly()) {                  
                    GridColumn column = new GridColumn(config);
                    this.columns.add(column);
                }
                
                GridField field = new GridField(config.getDataIndex());
                this.fields.add(field);
            }
        }
    }

    public GridResponseMetaData(List<ReportColumnConfig> configs) {
        this();
        if(configs!=null) {
            for(ReportColumnConfig config : configs) {
                GridColumn column = new GridColumn(config);
                this.columns.add(column);
                GridField field = new GridField(config.getField());
                this.fields.add(field);
            }
        }
    }
    
    public GridResponseMetaData(List<ColumnConfig> configs, GridResponseSortInfo sortInfo, List<ColumnConfig> fieldOnlies) {
        this(configs, sortInfo);
        this.sortInfo = sortInfo;
        
        if(fieldOnlies!=null) {
            for(ColumnConfig config : fieldOnlies) {                
                GridField field = new GridField(config.getDataIndex());
                this.fields.add(field);
            }
        }
    }
    
    public void localize(Locale locale, TimeZone timezone) {
        if(columns!=null) {
            for(GridColumn column : columns) {
                Message msg = new Message(column.getHeader());
                column.setHeader(msg.getLocalizedMessage(locale, timezone));
            }
        }
    }

    public void addField(GridField field){
        if (field != null)
            this.fields.add(field);
    }

    public void addFields(List<String> names){
        if (names != null){
            for(String field : names){
                this.fields.add(new GridField(field));
            }
        }
    }

    public List<GridField> getFields() {

        if (fields == null)
            fields = new ArrayList<GridField>();

        if (fields.isEmpty()){
            generateFields();
        }

        return fields;
    }

    /**
     * Populate fields list based on the given columns. The
     * fields list can be derived by examining the column definition.
     * This method can be used so the fields list doesnt have to be
     * specified in cases where it mirrors the columns list.
     */
    private void generateFields(){
        if (columns != null){
            for(GridColumn col : columns){
                fields.add(new GridField(col.getDataIndex()));
            }
        }
    }
    
    public List<GridColumn> getColumns() {
        return columns;
    }

    public void addColumn(GridColumn col){
        if (col != null)
            this.columns.add(col);
    }

    public void addColumns(List<GridColumn> cols){
        if(cols!=null) {
            if (this.columns == null)
                this.columns = new ArrayList<GridColumn>();

            columns.addAll(cols);

            if (this.fields == null)
                this.fields = new ArrayList<GridField>();

            for(GridColumn col : cols){
                GridField field = new GridField(col.getDataIndex());
                this.fields.add(field);
            }
        }
    }

    public GridResponseSortInfo getSortInfo() {
        return sortInfo;
    }

    public String getRoot() {
        return root;
    }

    /**
     * @deprecated - This is not supported when used with a serialized GridResponse object.  
     * The serializer effectively hard-codes this property to "rows" in that situation.
     * This will only work if calling the #asMap() method
     */
    public void setRoot(String root) {
        this.root = root;
    }

    public String getTotalProperty() {
        return totalProperty;
    }

    /**
     * @deprecated - This is not supported when used with a serialized GridResponse object.  
     * The serializer effectively hard-codes this property to "rowCount" in that situation.
     * This will only work if calling the #asMap() asMap method
     */
    public void setTotalProperty(String totalProperty) {
        this.totalProperty = totalProperty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupField() {
        return groupField;
    }

    public void setGroupField(String groupField) {
        this.groupField = groupField;
    }

    public Map asMap(){
        Map map = new HashMap();
        map.put("root", getRoot());
        map.put("id", getId());
        map.put("totalProperty", getTotalProperty());
        if (getColumns() != null)
            map.put("columns", getColumns());
        if (getFields() != null)
            map.put("fields", getFields());
        if (getSortInfo() != null)
            map.put("sortInfo", getSortInfo());
        return map;
    }
}
