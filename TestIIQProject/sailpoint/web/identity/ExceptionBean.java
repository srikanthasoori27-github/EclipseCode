package sailpoint.web.identity;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Resolver;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * UI bean to hold information about entitlement exceptions. We can do most of
 * this with just an EntitlementGroup, but we add the _loginName of the specific
 * account.
 */
public class ExceptionBean implements Serializable {
    
    private static final long serialVersionUID = 1L;

    Application _application;
    String _instance;
    String _nativeIdentity;
    EntitlementGroup _entitlements;
    String _id;
    Map<String, Map<String,String>> _descriptionMap;
    Locale _locale;
    boolean _showDescriptions;

    public ExceptionBean(EntitlementGroup ents, boolean showDescriptions,
                         Locale locale, SailPointContext context)
        throws GeneralException {

        _entitlements = ents;
        _application = ents.getApplication();
        _instance = ents.getInstance();
        _nativeIdentity = ents.getNativeIdentity();
        _locale = locale;
        _showDescriptions = showDescriptions;

        // Order multi-valued attribute values and permission rights
        // alphabetically.
        orderValues(context);
    }

    public Application getApplication() {
        return _application;
    }

    public String getInstance() {
        return _instance;
    }

    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public Entitlements getEntitlements() {
        return _entitlements;
    }

    public String getid() {
        if(_id==null)
            _id = Util.uuid();
        return _id;
    }

    public Map<String, Map<String, String>> getEntitlementDescriptionsMap() throws GeneralException{
        
        if (_descriptionMap==null && _application != null && _entitlements != null){
            Schema s = _application.getSchema(Connector.TYPE_ACCOUNT);
            if (s!=null){
                if(_entitlements.getAttributes() != null)
                    _descriptionMap = Explanator.getDescriptions(_application, _entitlements.getAttributes(), _locale);
                
                List<Permission> perms = _entitlements.getPermissions();
                if(perms!=null && !perms.isEmpty()) {
                    Map<String, String> permsMap = new HashMap<String, String>();
                    for(Permission perm : perms) {
                        if(perm.getTarget()!=null) {
                            String desc = Explanator.getPermissionDescription(_application, perm.getTarget(), _locale);
                            permsMap.put(perm.getTarget(), desc);
                        }
                    }
                    if(_descriptionMap == null) 
                        _descriptionMap = new HashMap<String, Map<String, String>>();
                    
                    _descriptionMap.put(ManagedAttribute.OLD_PERMISSION_ATTRIBUTE, permsMap);
                }
            }
        }
        
        return _descriptionMap;
    }

    @SuppressWarnings("unchecked")
    private void orderValues(SailPointContext context) throws GeneralException {
       
        if ((null != _entitlements) &&
            (((null != _entitlements.getAttributes()) && !_entitlements.getAttributes().isEmpty()) ||
             ((null != _entitlements.getPermissions() && !_entitlements.getPermissions().isEmpty())))) {

            final Map<String,Map<String,String>> descs = getEntitlementDescriptionsMap();

            // Create a deep copy so we don't modify the original entitlements.
            _entitlements =
                (EntitlementGroup) _entitlements.deepCopy((Resolver) context);

            // Order attributes by the value that is being displayed.
            if (null != _entitlements.getAttributes()) {
                for (Map.Entry<String,Object> entry : _entitlements.getAttributes().entrySet()) {
                    final String attrName = entry.getKey();
                    Object val = entry.getValue();

                    // Only sort if we're dealing with multiple values.
                    if (val instanceof List) {
                        List vals = (List) val;
                        Collections.sort(vals, new Comparator() {
                            public int compare(Object o1, Object o2) {
                                // Must deal with objects rather than Strings since
                                // we may have permissions, etc... see bug 6580.
                                String val1 = getDisplayedValue(o1);
                                String val2 = getDisplayedValue(o2);
                                int response = 0;
                                if( val1 != val2 ) {
                                    if( val1 == null ) {
                                        response = -1;
                                    } else if (val2 == null ) {
                                        response = 1;
                                    } else {
                                        response = val1.compareToIgnoreCase(val2); 
                                    }
                                }
                                return response; 
                            }

                            private String getDisplayedValue(Object val) {
                                String displayed = null;
                                if (_showDescriptions) {
                                    String desc = Util.getString(getDescription(val));
                                    if (null != desc) {
                                        displayed= desc;
                                    }
                                }

                                // If there was no description, just use the value.
                                if ((null == displayed) && (null != val)) {
                                    displayed = (val instanceof String) ? (String) val : val.toString();
                                }
                                
                                return displayed;
                            }
                            
                            private String getDescription(Object val) {
                                String desc = null;
                                if ((null != val) && (null != descs)) {
                                    Map<String,String> descsForAttr = descs.get(attrName);
                                    if (null != descsForAttr) {
                                        // Don't do descriptions for perms in attrs map.
                                        desc = descsForAttr.get(val);
                                    }
                                }
                                return desc;
                            }
                        });
                    }
                }
            }

            if (null != _entitlements.getPermissions()) {
                for (Permission perm : _entitlements.getPermissions()) {
                    // Order the rights list.
                    List<String> rights = perm.getRightsList();
                    Collections.sort(rights);
                    perm.setRights(rights);
                }
            }
        }
    }

}
