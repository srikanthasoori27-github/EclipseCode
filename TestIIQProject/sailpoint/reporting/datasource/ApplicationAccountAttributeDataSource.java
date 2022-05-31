/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.reporting.ApplicationAccountAttributesReport;
import sailpoint.tools.GeneralException;

public class ApplicationAccountAttributeDataSource extends IdentityDataSource {

    private static final Log log = LogFactory.getLog(ApplicationAccountAttributeDataSource.class);

    Iterator<Link> _links;
    Link _link;
    Application _filteredApp;
    Attributes<String,Object> _appAttributes;

    private IdentityService identSvc;

    Iterator<Object[]> _objectIds;

    public ApplicationAccountAttributeDataSource(List<Filter> filters, Locale locale,
            TimeZone timezone, Attributes<String, Object> inputs) {
        super(filters, locale, timezone, inputs);

        try {
            String appId = (String)inputs.get(ApplicationAccountAttributesReport.ARG_APPLICATION);
            _filteredApp = getContext().getObjectById(Application.class, appId);
            identSvc = new IdentityService(getContext());
        } catch (GeneralException ge) {
            log.warn("Exception while getting filtered application: " + ge.getMessage());
        }
    }
    
    @Override
    public void internalPrepare() throws GeneralException {
        try {
        	
        	List<String> props = new ArrayList<String>();
        	props.add("id");
        	_objectIds = getContext().search(this.getScope(), qo, props);

            if(_objectIds.hasNext()) {
            	String id = (String)_objectIds.next()[0];
                _object = (Identity)getContext().getObjectById(this.getScope(),id); 
                List<Link> links = identSvc.getLinks(_object, _filteredApp);
                if(links!=null && !links.isEmpty()) {
                    _links = links.iterator();
                }
            }
        } catch (GeneralException ge) {
            log.error("GeneralException caught while executing search for identities: " + ge.getMessage());
        }
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        Object value = null;
        
        if(fieldName.startsWith(ApplicationAccountAttributesReport.ARG_APP_PREFIX)) {
            fieldName = fieldName.substring(ApplicationAccountAttributesReport.ARG_APP_PREFIX.length());
        }

        if(_appAttributes!=null) {
            value = _appAttributes.get(fieldName);
        }

        if(value==null) {
            value = super.getFieldValue(jrField);            
        }
        if(value!=null)
            return value.toString();
        else
            return "";
    }

    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _objectIds != null && _links !=null) {            
            hasMore = _links.hasNext();

            if ( hasMore ) {
                //log.warn("Getting Next Entitlement");
                _link = _links.next();
                if(_link!=null)
                    _appAttributes = _link.getAttributes();
            }
            else {
                hasMore = _objectIds.hasNext();
                if(hasMore) {
                    try {
                        // clear the last one...
                        if ( _object != null ) {
                            getContext().decache(_object);
                        }
                    } catch (Exception e) {
                        log.warn("Unable to decache identity." + e.toString());
                    }
                    String id = (String)_objectIds.next()[0];
                    try {
                    	_object = (Identity)getContext().getObjectById(this.getScope(), id);
                    } catch (GeneralException ge) {
                    	log.warn("Failed to load identity with id: " + id + " Exception: " + ge.getMessage());
                    }
                    if ( _object != null ) {
                        updateProgress("Identity", _object.getName());
                        try {
                            List<Link> links = identSvc.getLinks(_object, _filteredApp);
                            if(links!=null) {
                                _links = links.iterator();
                                if(_links.hasNext()) {
                                    _link = _links.next();
                                    if(_link!=null)
                                        _appAttributes = _link.getAttributes();
                                }
                            }
                        } catch (GeneralException e) {
                            throw new JRException(e);
                        }
                    }                    
                }
            }
        }
        return hasMore;
    }
}
