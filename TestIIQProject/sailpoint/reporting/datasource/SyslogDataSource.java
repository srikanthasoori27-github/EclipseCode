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
import sailpoint.object.Filter;
import sailpoint.object.SyslogEvent;
import sailpoint.tools.GeneralException;

/**
 * @author derry.cannon
 *
 */
public class SyslogDataSource extends SailPointDataSource<SyslogEvent> {

    //private static final Log log = LogFactory.getLog(SyslogEventDataSource.class);

    public SyslogDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(SyslogEvent.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for syslog entries");
        _objects = getContext().search(SyslogEvent.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        Object value = null;
        value = super.getFieldValue(jrField);
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
                updateProgress("SyslogEvent", _object.toString());
            }
        }
        return hasMore;
    }
}
