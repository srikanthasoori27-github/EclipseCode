/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

/**
 * JDBCAttributeDTO stores the schemaDTO and index/merge columns to allow the JSF tier
 * to easily display these properties. We previously used hard coded backing bean methods
 * getIndexColumnsForAccountJSON and getMergeColumnsForAccountJSON where we hardcode the
 * object type in method name. This allows for the backing bean to populate this data based
 * on the supplied schemaDTO.objectType field.
 * @author chris.annino
 *
 */
public class JDBCAttributeDTO extends AttributeDTO {

    private String _indexColumn;
    private String _mergeColumn;
    
    public JDBCAttributeDTO(SchemaDTO schemaDTO, String indexCol, String mergeCol) {
        super(schemaDTO);
        setIndexColumn(indexCol);
        setMergeColumn(mergeCol);
    }

    public String getIndexColumn() {
        return _indexColumn;
    }

    public void setIndexColumn(String indexColumn) {
        this._indexColumn = indexColumn;
    }

    public String getMergeColumn() {
        return _mergeColumn;
    }

    public void setMergeColumn(String mergeColumn) {
        this._mergeColumn = mergeColumn;
    }

    public void saveAttributeData()
    {
        //Nothing to do here. Logic was already done in the ApplicationObjectBean
    }

}
