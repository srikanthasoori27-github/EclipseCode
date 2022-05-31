/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
*/
public class GridResponseSortInfo {

    private String field;
    private String direction;

    public GridResponseSortInfo(String direction, String field) {
        this.direction = direction;
        this.field = field;
    }

    public String getDirection() {
        return direction;
    }

    public String getField() {
        return field;
    }
}
