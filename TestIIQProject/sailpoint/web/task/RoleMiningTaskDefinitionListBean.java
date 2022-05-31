package sailpoint.web.task;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ColumnConfig;
import sailpoint.object.TaskDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public class RoleMiningTaskDefinitionListBean extends TaskDefinitionListBean {
    private static final Log log = LogFactory.getLog(RoleMiningTaskDefinitionListBean.class);
    
    @Override
    public List<Map<String,Object>> getRows() throws GeneralException {

        if (null == _rows) {
            List<String> cols = getProjectionColumns();
            assert (null != cols) : "Projection columns required for using getRows().";

            List<TaskDefinition> results =
                getContext().getObjects(TaskDefinition.class, getQueryOptions());
            _rows = new ArrayList<Map<String,Object>>();
            if (results != null && !results.isEmpty()) {
                for (TaskDefinition result : results) {
                    _rows.add(convertRow(result, cols));                    
                }
            }
        }
        return _rows;
    }
    
    /*
     * Convert a TaskDefinition into a attribute/value
     * Map.  This creates a HashMap that has a key/value pair for every property
     * in the cols list. If the value of the object implements Localizable,
     * the value will be localized.
     *
     * @param  row   The row to convert to a map.
     * @param  cols  The names of the projection columns returned in the object
     *               array.  The indices of the column names correspond to the
     *               indices of the array of values.
     * 
     * @return An attribute/value Map for the converted row.
     */
    private Map<String,Object> convertRow(TaskDefinition result, List<String> cols) {
        Map<String, Object> rowValues = new HashMap<String, Object>();
        if (cols != null && !cols.isEmpty()) {
            for (String col : cols) {
                Object value = null;
                
                if ("subType".equals(col)) {
                    value = WebUtil.localizeMessage(result.getEffectiveSubType());
                } else if (col == null || col.trim().length() == 0) {
                    value = "";
                } else {
                    try {
                        // Split on the periods
                        String [] props = col.split("\\x2e");
                        
                        for (int i = 0; i < props.length; ++i) {
                            String prop = props[i];
                            if (value == null) {
                                Method getter = TaskDefinition.class.getMethod("get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1));
                                value = getter.invoke(result);
                            } else {
                                Method getter = value.getClass().getMethod("get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1));
                                value = getter.invoke(value);
                            }
                        }
                    } catch (Exception e) {
                        value = "";
                        log.error("Could not get property for task defintion: " + col, e);
                    }
                }
                
                ColumnConfig config = null;

                if(columns != null) {
                    for(ColumnConfig column : columns) {
                        if(column.getProperty() != null && column.getProperty().equals(col)) {
                            config = column;
                            break;
                        }
                    }
                }

                // The following is taken from the BaseListBean's convertRow
                // if the value can be localized. If so, localize it
                if (value != null && Localizable.class.isAssignableFrom(value.getClass())) {
                    value = ((Localizable)value).getLocalizedMessage(getLocale(), getUserTimeZone());
                } else if(config != null && config.getDateStyle() != null) {
                    value = Util.dateToString((Date)value, config.getDateStyleValue(), config.getDateStyleValue(), getUserTimeZone(), getLocale());
                } else {
                    // give the subclass a chance to convert the value
                    value = convertColumn(col, value);
                }
                
                rowValues.put(col, value);
            }
        }
        
        return rowValues;
    }
}
