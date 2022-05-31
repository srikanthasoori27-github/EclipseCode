/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityProxy;
import sailpoint.web.messages.MessageKeys;

public class IdentityEntitlementDataSource extends IdentityDataSource {
    
    Iterator<EntitlementResult> _entitlements;
    EntitlementResult _entitlement;
    List<String> _filteredApps;
    Iterator<Object []> _ids;
    Identity _id;
    int _identitiesProcessed;
    Boolean _showScopeCaps;
    
    public static final String FILTERED_APPLICATIONS = "filteredApplications";
    public static final String SHOW_SCOPE_CAPABILITIES = "showScopeCaps";
    
    /** A simple class to conveniently store attribute name/value pairs for entitlement groups **/
    private class EntitlementResult {
        String application;
        String attributeName;
        String attributeValue;
        String account;
        
        public EntitlementResult(String application, String attributeName, String attributeValue, String account) {
            this.application = application;
            this.account = account;
            this.attributeName = attributeName;
            this.attributeValue = attributeValue;
        }
    }
    
    private static final Log log = LogFactory.getLog(IdentityEntitlementDataSource.class);
    
    public IdentityEntitlementDataSource(List<Filter> filters, Locale locale,
            TimeZone timezone, Attributes<String, Object> inputs) {
        super(filters, locale, timezone, inputs);

        _filteredApps = (List<String>)inputs.get(FILTERED_APPLICATIONS);
        _showScopeCaps = inputs.getBoolean(SHOW_SCOPE_CAPABILITIES);
    }
    
    @Override
    public void internalPrepare() throws GeneralException {
        try {
            
            List<String> props = new ArrayList<String>();
            props.add("id");
            _identitiesProcessed = 0;
            _ids = getContext().search(Identity.class, qo, props);
            
            if(_ids.hasNext()) {
                _id =  getContext().getObjectById(Identity.class, (String)(_ids.next()[0]));
                _entitlements = buildEntitlements(_id);
            }
        } catch (GeneralException ge) {
            log.error("GeneralException caught while executing search for identities: " + ge.getMessage());
        }
    }
    
    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        Object value = null;
        
        if(fieldName.equals("application") && _entitlement!=null) {
            value = _entitlement.application;
        } else if(fieldName.equals("attribute") && _entitlement!=null) {
            value = _entitlement.attributeName;
        } else if(fieldName.equals("account") && _entitlement!=null) {
            value = _entitlement.account;
        } else if(fieldName.equals("value") && _entitlement!=null) {
            value = _entitlement.attributeValue;
        } else {
            value = IdentityProxy.get(_id, fieldName);
            // Maintain backward compatibility due to changes in bug 4469 -- Bernie
            if (value != null)
                value = value.toString();
        }
        
        //if(value==null) {
        //    value = super.getFieldValue(jrField);
        //}

        return value;
    }
    
    /** Takes as an input the identity and builds an iterator over all of that identity's
     * entitlements on all of its applications.
     * @param identity
     * @return
     */
    private Iterator<EntitlementResult> buildEntitlements(Identity identity) {
        List<EntitlementResult> results = new ArrayList<EntitlementResult>();
        if(identity!=null) {     
                        
            List<Link> links = identity.getLinks();
            //log.warn("identity: " + identity.getDisplayableName() + " exceptions: " + groups);
            for(Link link : links) {
                String application = link.getApplicationName();
                Attributes attrs = link.getAttributes();
                
                if(_filteredApps!=null && !_filteredApps.isEmpty() && !_filteredApps.contains(application))
                    continue;
                
                if(attrs!=null) {
                    for(String key : (Set<String>)attrs.keySet()) {
                        Object value = attrs.get(key);
                        addEntitlement(results, key, value, application, link.getNativeIdentity());
                        
                    }
                }
                List<Permission> perms = link.getPermissions();
                if(perms!=null) {
                    for(Permission perm : perms) {
                        String value = perm.getRights();
                        addEntitlement(results, perm.getTarget(), value, application, link.getNativeIdentity());
                    }
                }
            }
            
            if(_showScopeCaps) {
                // If this identity has capabilities get them and add them to the report.
                StringBuilder caps = new StringBuilder();
                for (Capability cap : identity.getCapabilities()) {
                    Message msg = new Message(cap.getDisplayableName());
                    caps.append(msg.getLocalizedMessage() + ",");
                }
                if(caps.length() > 0) {
                    addEntitlement(results, MessageKeys.CAPABILITIES,
                                   caps.substring(0, caps.length()-1), MessageKeys.IDENTITY_IQ, "");
                }
                
                // Find assigned (controlled) scopes and add them to the report
                try {
                    StringBuilder scopes = new StringBuilder();
                    for (Scope scope : identity.getEffectiveControlledScopes(getContext().getConfiguration())) {
                        Message msg = new Message(scope.getDisplayableName());
                        scopes.append(msg.getLocalizedMessage() + ",");
                    }
                    if (scopes.length() > 0) {
                        addEntitlement(results, MessageKeys.AUTHORIZED_SCOPES,
                                scopes.substring(0, scopes.length()-1), MessageKeys.IDENTITY_IQ, "");
                    }
                } catch (GeneralException ge) {
                    log.error("GeneralException caught while trying to retrieve scopes for identities: " + ge.getMessage());
                }
            }
        }
        return results.iterator();
    }
    
    public void addEntitlement(List<EntitlementResult> results, String key, Object value, String application, String account) {
        /* What do we do with null values?  Just make it a string so it appears on the report? */
        if(value==null) {
            Message message = new Message(MessageKeys.NO_VALUE);
            value = message.getLocalizedMessage();
        }
        
        if(value instanceof ArrayList) {
            List<Object> values = (ArrayList)value;
            for(Object val : values) {
                results.add(new EntitlementResult(application, key, val.toString(), account));
            }
        } else {
            results.add(new EntitlementResult(application, key, value.toString(), account));
        }
    }
    
    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _ids != null && _entitlements !=null) {            
            hasMore = _entitlements.hasNext();
            
            if ( hasMore ) {
                //log.warn("Getting Next Entitlement");
                _entitlement = _entitlements.next();
            }
            else {
                hasMore = _ids.hasNext();
                if(hasMore) {                    
                    try {
                        // clear the last one...
                        if ( _id != null ) {
                            getContext().decache(_id);
                        }
                    } catch (Exception e) {
                        log.warn("Unable to decache identity." + e.toString());
                    }
                    //log.warn("Getting Next Object");
                    try {
                        _id = getContext().getObjectById(Identity.class, (String)(_ids.next()[0]));
                    } catch (GeneralException ge) {
                        log.error("GeneralException caught while getting next identity: " + ge.getMessage());
                    }
                    if ( _id != null ) {
                    	_identitiesProcessed++;
                        updateProgress("Identity", _id.getName());
                        _entitlements = buildEntitlements(_id);
                        if(_entitlements.hasNext()) {
                            _entitlement = _entitlements.next();
                        }
                    }                    
                }
            }
        }
        return hasMore;
    }
    
    @Override
    protected void updateProgress(String type, String name) { 
		 if (this.getMonitor() != null ) {
			 if ( getObjectCount() > 0 ) {
				 int percent = Util.getPercentage(_identitiesProcessed, getObjectCount());
				 updateProgress( type + " ["+name+"] " + _identitiesProcessed
						 +" of " + +getObjectCount()+".",percent);
			 } else {
				 updateProgress("Processing " + type + " ["+name+"].", -1); 
			 }
		 }
	 }

}
