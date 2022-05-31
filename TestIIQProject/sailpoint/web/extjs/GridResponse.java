/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

import java.util.List;
import java.util.Map;

/**
 * Serializable extjs grid response object.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
*/
public class GridResponse {

    /**
     * Current page of grid data. Row objects should be serializable by FlexJson.
     */
    private List objects;

    /**
     * Total number of records in the unpaged resultset
     */
    private int count;

    /**
     * Extjs column metatdata
     */
    private GridResponseMetaData metaData;

    /**
     * True if the request was successful.
     */
    private boolean success;

    /**
     * Description of the error if the request failed. This will be ignored if success=true.
     */
    private String errorMsg;

    /**
     * Additional information to pass along with the grid response. This is not
     * part of the ext api, but can be used to piggyback other data you need
     * for custom grid implementations or what have you.
     */
    private Map additionalData;

    public GridResponse(String errorMsg) {
        success = false;
        this.errorMsg=errorMsg;
    }

    public GridResponse(List rows) {
        this.objects = rows;
        this.count = rows != null ? rows.size() : 0;
        success = true;
        errorMsg="";
    }

    public GridResponse(List rows, int totalRows) {
        this.count = totalRows;
        this.objects=rows;
        success = true;
        errorMsg="";
    }

    public GridResponse(GridResponseMetaData metaData, List rows, int totalRows) {
        this(rows, totalRows);
        this.metaData = metaData;
    }

    public GridResponseMetaData getMetaData() {
        return metaData;
    }

    public List getObjects() {
        return objects;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public Map getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map additionalData) {
        this.additionalData = additionalData;
    }

    public int getCount() {
        return count;
    }

}
