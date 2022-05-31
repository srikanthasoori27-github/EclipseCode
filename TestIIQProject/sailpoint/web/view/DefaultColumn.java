/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtils;
import sailpoint.api.SailPointContext;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DefaultColumn implements ViewColumn {

    static final String CALCULATED_COLUMN_PREFIX = "IIQ_";

    private ViewEvaluationContext context;
    private ColumnConfig conf;

    private SnapshotSummaryFactory snapshotSummaryFactory;

    public DefaultColumn() {
    }

    public void init(ViewEvaluationContext context, ColumnConfig conf) {
        this.context = context;
        this.conf = conf;
    }

    public void afterRender() {

    }

    public Filter getFilter()  throws GeneralException{
        return null;
    }

    public List<String> getProjectionColumns()  throws GeneralException{
        List<String> cols = new ArrayList<String>();
        if (conf != null && conf.getProperty() != null && conf.getProperty().indexOf(CALCULATED_COLUMN_PREFIX) == -1)
            cols.add(conf.getProperty());
        return cols;
    }

    public Object getValue(Map<String, Object> row) throws GeneralException {
        return row != null && row.containsKey(conf.getProperty()) ? row.get(conf.getProperty()) : null;
    }


    protected ColumnConfig getColumnConfig(){
        return conf;
    }

    protected ViewEvaluationContext getContext(){
        return context;
    }

    protected SailPointContext getSailPointContext(){
        return context.getSailPointContext();
    }

    protected Locale getLocale(){
        return context.getUserContext().getLocale();
    }

    protected TimeZone getTimeZone(){
        return context.getUserContext().getUserTimeZone();
    }

    public SnapshotSummaryFactory getSnapshotSummaryFactory(){
        if (snapshotSummaryFactory == null){
            snapshotSummaryFactory = new SnapshotSummaryFactory(getSailPointContext(), getLocale());
        }
        return snapshotSummaryFactory;
    }

    public static Object evaluate(Object bean, String expression) throws GeneralException {

        if(expression == null)
            throw new IllegalArgumentException("A null expression was passed to evaluate.");

        if (bean == null)
            return null;

        boolean isMapExpression = expression.contains("[");
        Object val = null;
        try {
            val = PropertyUtils.getNestedProperty(bean, expression);
        } catch (IllegalAccessException e) {
            throw new GeneralException("IllegalAccessException: Could not retrieve value for column " + expression, e);
        } catch (InvocationTargetException e) {
            throw new GeneralException("InvocationTargetException: Could not retrieve value for column " + expression, e);
        } catch (NoSuchMethodException e) {
            throw new GeneralException("NoSuchMethodException: Could not retrieve value for column " + expression, e);
        } catch (NestedNullException e) {
            // return null
        }

        if (isMapExpression && val != null && val instanceof Map){
            String key = "";
            Map map = (Map)val;
            if (map.containsKey(key)){
                val = map.get(key);
            } else {
                val = null;
            }
        }

        return val;
    }
}
