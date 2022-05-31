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

import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 * djs: NOTE currently returns CertificationItem objects
 */
public class AccountGroupDataSource extends SailPointDataSource<ManagedAttribute> {

    private static final Log log = LogFactory.getLog(AccountGroupDataSource.class);

    public AccountGroupDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);

        // jsl - may want to add a filter for group='true' but the
        // outcome of the recent meeting was that all MAs should be
        // treated the same in the UI at least
        setScope(ManagedAttribute.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for Account Groups...");
        // formerly "name"
        qo.setOrderBy("displayableName");
        _objects = getContext().search(ManagedAttribute.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        Object value = null;

        if(fieldName.equals("application")) {
            value = _object.getApplication().getName();
        }
        else if(fieldName.equals("classifications.id")) {
            value = Util.listToCsv(_object.getClassificationDisplayNames());
        }
        else if(fieldName.startsWith("permissions")) {
            List<Permission> perms = _object.getPermissions();
            if(perms != null) {
                // TODO: Returning the first permission found, but is that the 'right' thing to do?
                // Clearly there can be more than one, so what's the best solution?  Return a data
                // structure instead of the value and let the consumer figure it out?
                for(int i = 0; i < perms.size(); i++) {
                    if(perms.get(i) != null) {
                        if(fieldName.equalsIgnoreCase("permissions.target")) {
                            value = perms.get(i).getTarget();
                        }
                        else if(fieldName.equalsIgnoreCase("permissions.rights")) {
                            value = perms.get(i).getRights();
                        }
                        else if(fieldName.equalsIgnoreCase("permissions.annotation")) {
                            value = perms.get(i).getAnnotation();
                        }
                        if(value != null) {
                            break;
                        }
                    }
                }
            }
        }

        if(value == null)  {
            value = super.getFieldValue(jrField);
        }

        return value;

    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
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
                updateProgress("ManagedAtribute", _object.getName());
            }
        }
        return hasMore;
    }

}
