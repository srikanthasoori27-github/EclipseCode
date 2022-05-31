/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApplicationActivity;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class ActivityDataSource extends SailPointDataSource<ApplicationActivity> {

    private static final Log log = LogFactory.getLog(ActivityDataSource.class);

    public ActivityDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(ApplicationActivity.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for activities");
        _objects = getContext().search(ApplicationActivity.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        Object value = super.getFieldValue(jrField);
        return value;
    }

    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _objects != null ) {
            hasMore = _objects.hasNext();
            if ( hasMore ) {
            	_object = _objects.next();
            } else {
            	_object = null;
            }
            if ( _object != null ) {
                updateProgress("Activity", _object.getTimeStamp().toString());
            }
        }
        //log.debug("Getting Next: " + hasMore);
        return hasMore;
    }
}
