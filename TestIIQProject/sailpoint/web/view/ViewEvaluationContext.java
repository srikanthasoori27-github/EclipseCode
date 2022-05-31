/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.web.UserContext;

import java.util.List;


/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ViewEvaluationContext {

    private UserContext userContext;
    private Attributes<String, Object> builderAttributes;
    private Attributes<String, Object> rowAttributes;
    private List<ColumnConfig> columns;

    public ViewEvaluationContext(UserContext userContext, List<ColumnConfig> columns) {
        this.userContext = userContext;
        builderAttributes = new Attributes<String, Object>();
        rowAttributes = new Attributes<String, Object>();
        this.columns = columns;
    }

    public SailPointContext getSailPointContext(){
        return userContext.getContext();
    }

    public UserContext getUserContext(){
        return userContext;
    }

    public Attributes<String, Object> getBuilderAttributes(){
        return builderAttributes;
    }

    public Attributes<String, Object> getRowAttributes(){
        return rowAttributes;
    }

    public void clearRowAttributes(){
        rowAttributes.clear();
    }

    public List<ColumnConfig> getColumns(){
        return columns;
    }

}
