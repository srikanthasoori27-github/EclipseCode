/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author Arun.Chauhan
 *
 */
public class LinkDataSource extends SailPointDataSource<Link> {

    private static final Log log = LogFactory.getLog(LinkDataSource.class);

    public LinkDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(Link.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for link log entries");
        _objects = getContext().search(Link.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();

        Object value = null;

        //Need to get the entitlement information from the attributes
        if(fieldName.equals("entitlements")){
            Attributes<String,Object> entitlementsAttribute = _object.getEntitlementAttributes();
            if (entitlementsAttribute != null && !entitlementsAttribute.isEmpty()) {
                Iterator<String> keys = entitlementsAttribute.keySet().iterator();
                while ( keys.hasNext() ) {
                    Object entitlementObject = entitlementsAttribute.get(keys.next());
                    if(entitlementObject != null){
                        if (entitlementObject instanceof List){
                            List entitlementObjectList = (List) entitlementObject;
                            value = Util.listToCsv(entitlementObjectList);
                        }else if (entitlementObject instanceof String){
                            value = entitlementObject.toString().trim();
                        }
                    }
                }
            }
        }else{
            value = super.getFieldValue(jrField);
        }
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
                updateProgress("Link", _object.getName());
            }
        }
        return hasMore;
    }
}