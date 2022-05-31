/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.json.JSONException;
import org.json.JSONWriter;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.io.Writer;
import java.io.StringWriter;


/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ExtGridResponse{

    private static final String ROWS_KEY = "rows";
    private static final String ROWCOUNT_KEY = "rowCount";
    private static final String SUCCESS_KEY = "success";
    private final static String META_KEY = "metaData";

    private int rowCount;
    private List<GenericJSONObject> rows;
    private boolean success;
    private GridMetadata meta;

    public ExtGridResponse(List<String> columnNames, Iterator<Object[]> rowIter, int rowCount,
                           boolean success, GridMetadata meta) {
        this(null, rowCount, success, meta);
        rows = new ArrayList<GenericJSONObject>();
        while (rowIter != null && rowIter.hasNext()){
            Object[] row = rowIter.next();
            GenericJSONObject obj = new GenericJSONObject();
            for (int i=0;i<row.length;i++){
                String colName = columnNames.get(i).replace(".","-");
                obj.set(colName, row[i] != null ? row[i] : "");
            }
            rows.add(obj);
        }
    }

    public ExtGridResponse(List<String> columnNames, Iterator<Object[]> rows, int rowCount,
                           boolean success) {
        this(columnNames, rows, rowCount, success, null);

        if (columnNames != null && !columnNames.isEmpty()){
            List<ExtColumn> cols = new ArrayList<ExtColumn>();
            for(String name : columnNames)
                cols.add(new ExtColumn(name));
            meta = new GridMetadata(cols);
        }
    }

     public ExtGridResponse(List<GenericJSONObject> rows, int rowCount,
                           boolean success, GridMetadata meta) {
        this.rows = rows;
        this.rowCount = rowCount;
        this.success = success;
        this.meta = meta;
    }




    public String getJson() throws JSONException {
        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);

        writer.object();

        writer.key(SUCCESS_KEY);
        writer.value(this.success);

        if (meta != null){
            writer.key(META_KEY);
            meta.getJson(writer);
        }

        writer.key(ROWS_KEY);
        writer.array();
        if (rows != null){
            for(GenericJSONObject row : rows){
                row.getJson(writer);
            }
        }        
        writer.endArray();

        writer.key(ROWCOUNT_KEY);
        writer.value(this.rowCount);

        writer.endObject();

        return jsonString.toString();
    }

    /**
     * Models the metadata object returned as a part of an json
     * reponse to a extjs grid with a JsonStore. Note that we
     * have extended the field model to allow the definition o
     * the grid's column model. This allows the grid to be dynamically
     * updated with a ajax response.
     * see PagingGrid.js
     */
    public static final class GridMetadata {

        private final static String META_ID_KEY = "id";
        private final static String META_ROOT_KEY = "root";
        private final static String META_TOTAL_PROP_KEY = "totalProperty";
        private final static String META_FIELDS_KEY = "fields";

        private String id;
        private String root;
        private String totalProperty;
        private String sortField = null;
        private String sortDir = null;
        private List<ExtColumn> columns;
       
        public GridMetadata(List<ExtColumn> columns) {
            this("id", "rows", "rowCount", columns);
        }

        public GridMetadata(String id, String root, String totalProperty, List<ExtColumn> columns) {
            this.columns = columns;
            this.id = id;
            this.root = root;
            this.totalProperty = totalProperty;
        }


        public void setSortField(String sortField) {
            this.sortField = sortField;
        }

        public void setSortDir(String sortDir) {
            this.sortDir = sortDir;
        }

        public void getJson(JSONWriter writer) throws JSONException {

            writer.object();

            writer.key(META_ID_KEY);
            writer.value(id);

            writer.key(META_ROOT_KEY);
            writer.value(root);

            writer.key(META_TOTAL_PROP_KEY);
            writer.value(totalProperty);
          
            if (this.sortField != null){
               writer.key("sortInfo");
               writer.object();
               writer.key("field");
               writer.value(this.sortField);
               writer.key("direction");
               writer.value(this.sortDir);
               writer.endObject(); 
            }

            writer.key(META_FIELDS_KEY);
            writer.array();
            if (columns != null) {
                for (ExtColumn col : columns) {
                    col.getJson(writer);
                }
            }
            writer.endArray();

            writer.endObject();
        }

    }



}
