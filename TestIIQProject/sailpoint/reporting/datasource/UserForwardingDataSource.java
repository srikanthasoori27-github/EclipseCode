/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LocalizedDate;
import sailpoint.web.identity.IdentityProxy;

public class UserForwardingDataSource extends IdentityDataSource {
    
    Iterator<Object []> _ids;
    Identity _id;
    
    private static final Log log = LogFactory.getLog(UserForwardingDataSource.class);
    
    public UserForwardingDataSource(List<Filter> filters, Locale locale,
            TimeZone timezone, Attributes<String, Object> inputs) {
        super(filters, locale, timezone, inputs);

    }
    
    @Override
    public void internalPrepare() throws GeneralException {
        try {
            List<String> props = new ArrayList<String>();
            props.add("id");
            
            _ids = getContext().search(Identity.class, qo, props);
        } catch (GeneralException ge) {
            log.error("GeneralException caught while executing search for identities: " + ge.getMessage());
        }
    }
    
    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        Object value = null;
        
        try {
            if(fieldName.equals("forwardUserName")) {
                Identity forwardingUser = getContext().getObjectByName(Identity.class, _id.getPreference(Identity.PRF_FORWARD).toString());
                value = forwardingUser != null ? forwardingUser.getName() : "Forwarded identity " + _id.getPreference(Identity.PRF_FORWARD).toString() + " not found";;
            } else if(fieldName.equals("forwardName")) {
                Identity forwardingUser = getContext().getObjectByName(Identity.class, _id.getPreference(Identity.PRF_FORWARD).toString());
                value = forwardingUser != null ? forwardingUser.getDisplayableName() : "Forwarded identity " + _id.getPreference(Identity.PRF_FORWARD).toString() + " not found";
            } else if(fieldName.equals("forwardStartDate")) {
                LocalizedDate start = new LocalizedDate((Date)_id.getPreference(Identity.PRF_FORWARD_START_DATE),DateFormat.SHORT, null);
                value = start.getLocalizedMessage();
            } else if(fieldName.equals("forwardEndDate")) {
                LocalizedDate end = new LocalizedDate((Date)_id.getPreference(Identity.PRF_FORWARD_END_DATE), DateFormat.SHORT, null);
                value = end.getLocalizedMessage();
            } else {
                value = IdentityProxy.get(_id, fieldName);
            }
        } catch(GeneralException ge) {
           log.error("GeneralException caught while trying to retrieve forwarding user: " + ge.getMessage());
        }
             
        return value;
    }
    
    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        String forwardingUser = null;
        
        if ( _ids != null ) {
            
            // Skip over identities that don't have a forwarding user.
            hasMore = _ids.hasNext();
            while(null == forwardingUser && hasMore) {
                try {
                    // clear the last one...
                    if ( _id != null ) {
                        getContext().decache(_id);
                    }
                } catch (Exception e) {
                    log.warn("Unable to decache identity." + e.toString());
                }
                
                hasMore = _ids.hasNext();
                if (hasMore) {
                    try {
                        _id = getContext().getObjectById(Identity.class, (String)(_ids.next()[0]));
                    } catch (GeneralException ge) {
                        log.error("GeneralException caught while trying to load identity. " + ge.getMessage());
                    }
                    
                    if ( _id != null ) {
                        forwardingUser = (String)_id.getPreference(Identity.PRF_FORWARD);
                        
                        String id = _id.getId();
                        if(processedIds.contains(id)) {
                            // We've already processed this id, so let's not use it:
                            forwardingUser = null;
                        } else {
                            processedIds.add(id);
                            updateProgress("Identity", _id.getName());
                        }
                    }
                }                
            }
        }
        return hasMore;
    }
}
    
    