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

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;

/**
 * @author dan.smith
 */
public class AccountReportDataSource extends SailPointDataSource<Link> {

    private static final Log log = LogFactory.getLog(AccountReportDataSource.class);

    Attributes<String, Object> _inputs;

    public AccountReportDataSource(List<Filter> filters, Locale locale, TimeZone timezone,
            Attributes<String, Object> inputs) {
        super(filters, locale, timezone);
        _inputs = inputs;
        qo.setOrderBy("application");
        // This requires a distinct query due to the external 
        // table refrences and the OR operator being applied
        // to the values.
        qo.setDistinct(true);
        setScope(Link.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        try {
            _objects = getContext().search(Link.class, qo);
        } catch (GeneralException ge) {
            log.error("GeneralException caught while executing search for links: "
                       + ge.getMessage());
            _objects = null;
        }
    }

    /**
     * Fields supported: 
     *
     *  application - Name of the application the Link came from...
     *  accountId - (Link.native_identity)
     *  displayName  - (Link.displayName)
     *  identity - (Link.identity)
     *  extendedattributes....
     *  
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        Object value = null;

        if (fieldName.equals("application")) {
            Application app = _object.getApplication();
            value = app.getName();
            if ( value == null )
                value = app.getId();
        } else 
        if (fieldName.equals("accountId")) {
            value = _object.getDisplayableName();
            if(value!=null)
                value = value.toString();
        } else
        if (fieldName.equals("identity")) {
            value = _object.getIdentity();
        } else if(fieldName.equals("entitlement")) {
            value = getEntitlement(fieldName);
        } else {
            Object obj = _object.getAttribute(fieldName);
            if ( obj != null ) 
               value = obj.toString();
        }
        // djs: is this necesary?
        if(value==null) {
            value = super.getFieldValue(jrField);
        }
        if ( log.isDebugEnabled() ) {
            log.debug("fieldName: "+ fieldName + " account  " + _object.getNativeIdentity() + " value: " + value);
        }
        return value;
    }
    
    private Object getEntitlement(String fieldName) {
        Object entitlement = null;
        ObjectConfig config = Link.getObjectConfig();
        if ( config != null ) {
            List<ObjectAttribute> attrs = config.getObjectAttributes();
            for ( ObjectAttribute attr : attrs ) {
                String name = attr.getName();
                Object o = _inputs.get(name);
                if ( o != null ) {
                    
                    return name;
                }
            }
        }
        return entitlement;
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if (_objects != null) {
            hasMore = _objects.hasNext();
            if (hasMore) {
                try {
                    // clear the last one...
                    if (_object != null) {
                        getContext().decache(_object);
                    }
                } catch (Exception e) {
                    log.warn("Unable to decache identity." + e.toString());
                }
                _object = _objects.next();
                if (_object != null) {
                    updateProgress("Link", _object.getNativeIdentity());
                }
            }
        }
        return hasMore;
    }
}
