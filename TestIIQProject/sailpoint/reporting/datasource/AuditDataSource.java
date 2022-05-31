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

import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class AuditDataSource extends SailPointDataSource<AuditEvent> {

    private static final Log log = LogFactory.getLog(AuditDataSource.class);

    public AuditDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(AuditEvent.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for audit log entries");
        _objects = getContext().search(AuditEvent.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        Object o = null;
        String fieldName = jrField.getName();
        if ( fieldName != null ) {
            //we set jasper report column with type Date if column has Date class
            //if we pass string value of Date here will cause type mismatch
            if ( "created".compareTo(fieldName) == 0 )  {
                o = _object.getCreated();
            } else if ( "action".compareTo(fieldName) == 0 )  {
                o = _object.getAction();
            } else if ( "source".compareTo(fieldName) == 0 )  {
                o = _object.getSource();
            } else if ( "target".compareTo(fieldName) == 0 )  {
                o = _object.getTarget();
                if (o != null) {
                    try {
                        Identity identity = getContext().getObjectByName(Identity.class, (String)o.toString());
                        if (identity != null) {
                            o = identity.getDisplayName();
                        }
                    } catch (GeneralException e) {
                        // why doesn't BaseListBean.convertColumn() throw exceptions?
                        log.debug("Problem searching for identity: " + o.toString());
                    }
                }
            } else if ( "string1".compareTo(fieldName) == 0 )  {
                o = _object.getString1();
            } else if ( "string2".compareTo(fieldName) == 0 )  {
                o = _object.getString2();
            } else if ( "string3".compareTo(fieldName) == 0 )  {
                o = _object.getString3();
            } else if ( "string4".compareTo(fieldName) == 0 )  {
                o = _object.getString4();
            } else if ( "instance".compareTo(fieldName) == 0 )  {
                o = _object.getInstance();
            } else if ( "accountName".compareTo(fieldName) == 0 )  {
                o = _object.getAccountName();
            } else if ( "attributeName".compareTo(fieldName) == 0 )  {
                o = _object.getAttributeName();
            } else if ( "attributeValue".compareTo(fieldName) == 0 )  {
                o = _object.getAttributeValue();
            } else if ( "application".compareTo(fieldName) == 0 )  {
                o = _object.getApplication();          
            }
            //IIQETN-5473 :- Adding implementation to allow to export to PDF and CSV the "Interface" column.
            else if ( "interface".compareTo(fieldName) == 0 )  {
                o = _object.getInterface();
            }
            // IIQSAW-3491 & IIQSAW-3492 - Include Client Host & Server Host in exports.
            else if ("clientHost".compareTo(fieldName) == 0) {
                o = _object.getClientHost();
            } else if ("serverHost".compareTo(fieldName) == 0) {
                o = _object.getServerHost();
            }
        }
        return o; 
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
                updateProgress("AuditEvent", _object.getAction().toString());
            }
        }
        //log.debug("Getting Next: " + hasMore);
        return hasMore;
    }
}
