/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.reporting.IdentityHistoryExport;
import sailpoint.tools.GeneralException;
import sailpoint.web.identity.IdentityHistoryUtil;

/**
 * @author derry.cannon
 *
 */
public class IdentityHistoryDataSource extends SailPointDataSource<IdentityHistoryItem> 
    {
    private static final Log log = LogFactory.getLog(IdentityHistoryDataSource.class);

    private Attributes<String,Object> inputs;
    
    public IdentityHistoryDataSource(List<Filter> filters, Locale locale, 
        TimeZone timezone, Attributes<String,Object> inputs) 
        {
        super(filters, locale, timezone);
        setScope(IdentityHistoryItem.class);
        this.inputs = inputs;
        }

    
    @Override
    @SuppressWarnings("unchecked")
    public void internalPrepare() throws GeneralException 
        {
        updateProgress("Querying for identity history");
        
        List<Ordering> orderings = 
            (List<Ordering>)inputs.get(IdentityHistoryExport.ARG_ORDERING);
        
        for (Ordering ordering : orderings)
            {
            qo.addOrdering(ordering.getColumn(), ordering.isAscending());
            }
            
        _objects = getContext().search(IdentityHistoryItem.class, qo);
        }

    
    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException 
        {
        Object value = null;
        String fieldName = jrField.getName();

        Map<String, Object> map = _object.toMap();
        
        if (fieldName.equals("description"))
            {
            IdentityHistoryUtil.calculateDescription(map, getLocale());
            value = map.get("description");
            } 
        else if (fieldName.equals("status"))
            {
            IdentityHistoryUtil.calculateStatus(map, getLocale());
            value = map.get("status");
            } 
        else if (fieldName.equals("certificationType"))
            {
            IdentityHistoryUtil.localizeCertificationType(map, getLocale());
            value = map.get("certificationType");
            } 
        else if (fieldName.equals("comments"))
            {
            IdentityHistoryUtil.calculateComments(map);
            value = map.get("comments");
            } 
        else
            value = super.getFieldValue(jrField);
        
        return value;
        }

    public boolean internalNext() throws JRException 
        {
        boolean hasMore = false;
        if ( _objects != null ) 
            {
            hasMore = _objects.hasNext();
            
            if (hasMore) 
            	_object = _objects.next();
            else 
            	_object = null;
            
            if (_object != null) 
                updateProgress("Identity history item", _object.toString()); 
        }
        //log.debug("Getting Next: " + hasMore);
        return hasMore;
        }
    }
