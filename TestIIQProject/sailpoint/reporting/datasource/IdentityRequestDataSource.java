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
import sailpoint.object.IdentityRequest;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class IdentityRequestDataSource extends SailPointDataSource<IdentityRequest> {

    private static final Log log = LogFactory.getLog(IdentityRequestDataSource.class);

    public IdentityRequestDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        this(filters, locale, timezone,false);
    }

    public IdentityRequestDataSource(List<Filter> filters, Locale locale, TimeZone timezone, boolean distinct) {
        super(filters, locale, timezone, distinct);
        setScope(IdentityRequest.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for identity request entries");
        qo.setOrderBy("id");
        _objects = getContext().search(IdentityRequest.class, qo);
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
        String currentId = (_object == null) ? null : _object.getId();

        if ( _objects != null ) {
            IdentityRequest nextObject = null;
            while (_objects.hasNext()) {
                nextObject = _objects.next();

                if (nextObject == null) {
                    // should never happen, but let's handle it
                    // just to be sure.
                    continue;
                }

                // if we have no previous object, or the previous object has
                // a different id than the object we just read, return it.
                if ((currentId == null) ||
                        (!currentId.equalsIgnoreCase(nextObject.getId()))) {
                    _object = nextObject;
                    updateProgress("IdentityRequest", _object.toString());
                    return true;
                }
            }
        }

        return false;
    }
}
