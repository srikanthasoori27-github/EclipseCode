package sailpoint.web.mining;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.json.JSONTokener;
import org.json.JSONWriter;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Permission;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.tools.GeneralException;

public class EntitlementsJsonObj implements JSONString {
    private static final Log log = LogFactory.getLog(EntitlementsJsonObj.class);
    
    private Map<String, List<Filter>> attributeEntitlements;
    private Map<String, List<Permission>> permissionEntitlements;
    private String jsonString;
    private SailPointContext context;
    private Locale locale;
    private boolean readOnly;
    
    /**
     * Constructor for EntitlementsJSONObj 
     * @param attributeEntitlements Map of attribute-based entitlements (as Filter Lists) keyed by application 
     * @param permissionEntitlements Map of permission-based entitlements (as Permission Lists) keyed by application
     * @param context
     * @param locale
     */
    public EntitlementsJsonObj(Map<String, List<Filter>> attributeEntitlements, Map<String, List<Permission>> permissionEntitlements, SailPointContext context, Locale locale, boolean readOnly) {
        this.attributeEntitlements = attributeEntitlements;
        this.permissionEntitlements = permissionEntitlements;
        this.context = context;
        this.locale = locale;
        this.readOnly = readOnly;
    }
    
    public EntitlementsJsonObj(String jsonString, SailPointContext context, Locale locale, boolean readOnly) throws JSONException {
        this.jsonString = jsonString;
        this.context = context;
        this.locale = locale;
        this.readOnly = readOnly;
        attributeEntitlements = new HashMap<String, List<Filter>>();        
        permissionEntitlements = new HashMap<String, List<Permission>>();
        
        JSONTokener tokener = new JSONTokener(jsonString);
        JSONObject jsonObj = (JSONObject) tokener.nextValue();
        JSONArray entitlements = jsonObj.getJSONArray("entitlements");
        for (int i = 0; i < entitlements.length(); ++i) {
            JSONObject entitlementsForApp = entitlements.getJSONObject(i);
            String applicationId = entitlementsForApp.getString("applicationId");
            List<Filter> attributeEntitlementsForApp = getAttributeEntitlements(entitlementsForApp);
            attributeEntitlements.put(applicationId, attributeEntitlementsForApp);
            List<Permission> permissionEntitlementsForApp = getPermissionEntitlements(entitlementsForApp);
            permissionEntitlements.put(applicationId, permissionEntitlementsForApp);
        }
    }

    public String toJSONString() {
        if (jsonString == null) {
            // Perform the conversion and save it off so we don't have to do it again later
            try {
                final Writer jsonStringWriter = new StringWriter();
                final JSONWriter jsonWriter = new JSONWriter(jsonStringWriter);
                generateJSON(jsonWriter);
                jsonString = jsonStringWriter.toString();
            } catch (JSONException e) {
                log.error("The EntitlementJsonObj threw a JSONException.", e);
                jsonString = "{}";
            }
        }
        
        return jsonString;
    }
    
    public Map<String, List<Filter>> getAttributeEntitlements() {
        return attributeEntitlements;
    }
    
    public Map<String, List<Permission>> getPermissionEntitlements() {
        return permissionEntitlements;
    }
    
    public void generateJSON(JSONWriter jsonWriter) throws JSONException {
        jsonWriter.object();
        jsonWriter.key("entitlements");
        jsonWriter.array();
        
        Set<String> applications = new HashSet<String>();
        if (attributeEntitlements != null && !attributeEntitlements.isEmpty()) {
            Set<String> attributeApps = attributeEntitlements.keySet();
            applications.addAll(attributeApps);
        }
        if (permissionEntitlements != null && !permissionEntitlements.isEmpty()) {
            Set<String> permissionApps = permissionEntitlements.keySet();
            applications.addAll(permissionApps);                
        }
        
        if (!applications.isEmpty()) {
            for (String application : applications) {
                jsonWriter.object();
                jsonWriter.key("readOnly");
                jsonWriter.value(readOnly);
                jsonWriter.key("applicationId");
                jsonWriter.value(application);
                jsonWriter.key("application");
                try {
                    Application applicationObj = context.getObjectById(Application.class, application);
                    jsonWriter.value(applicationObj.getName());
                } catch (GeneralException e) {
                    log.error("No name could be found for the application with ID " + application);
                    jsonWriter.value(application);
                } 
                // Encode the attribute entitlements
                jsonWriter.key("entitlementAttributes");
                jsonWriter.array();
                encodeAttributeEntitlements(jsonWriter, application);
                jsonWriter.endArray();
                // Encode the permission entitlements
                jsonWriter.key("permissions");
                jsonWriter.array();
                encodePermissionEntitlements(jsonWriter, application);
                jsonWriter.endArray();
                jsonWriter.endObject();
            }
        }
        
        jsonWriter.endArray();
        jsonWriter.endObject();
    }
    
    private List<Filter> getAttributeEntitlements(JSONObject entitlementsForApp) throws JSONException {
        List<Filter> attributeEntitlementsForApp = new ArrayList<Filter>();
        JSONArray entitlementAttributesJson = entitlementsForApp.getJSONArray("entitlementAttributes");
        Map<String, List<String>> multiValuedAttributes = new HashMap<String, List<String>>();
        for (int i = 0; i < entitlementAttributesJson.length(); ++i) {
            JSONObject attribute = entitlementAttributesJson.getJSONObject(i);
            String attributeName = attribute.getString("name");
            String attributeValue = attribute.getString("value");
            boolean isMulti = attribute.getBoolean("isMulti");
            if (isMulti) {
                // Store multi-valued attributes so that we can consolidate their 
                // possible values into a single filter later.
                List<String> multiValues = multiValuedAttributes.get(attributeName);
                if (multiValues == null) {
                    multiValues = new ArrayList<String>();
                    multiValuedAttributes.put(attributeName, multiValues);
                }
                multiValues.add(attributeValue);
            } else {
                attributeEntitlementsForApp.add(Filter.eq(attributeName, attributeValue));
            }
        }

        // Now that we've picked up all the attributes for this appplication we can
        // consolidate multivalued attributes into appropriate filters
        Set<String> multiValuedAttributeNames = multiValuedAttributes.keySet();
        if (multiValuedAttributeNames != null && !multiValuedAttributeNames.isEmpty()) {
            for (String multiValuedAttributeName : multiValuedAttributeNames) {
                List<String> values = multiValuedAttributes.get(multiValuedAttributeName);
                attributeEntitlementsForApp.add(Filter.containsAll(multiValuedAttributeName, values));
            }
        }
        
        return attributeEntitlementsForApp;
    }
    
    private List<Permission> getPermissionEntitlements(JSONObject entitlementsForApp) throws JSONException {
        List<Permission> permissionsForApp = new ArrayList<Permission>();
        JSONArray permissionsJson = entitlementsForApp.getJSONArray("permissions");
        for (int i = 0; i < permissionsJson.length(); ++i) {
            JSONObject permission = permissionsJson.getJSONObject(i);
            JSONArray rightsArray = permission.getJSONArray("rights");
            List<String> rights = new ArrayList<String>();
            for (int j = 0; j < rightsArray.length(); ++j) {
                rights.add(rightsArray.getString(j));
            }
            String target = permission.getString("target");
            permissionsForApp.add(new Permission(rights, target));
        }
        return permissionsForApp;
    }
    
    private void encodeAttributeEntitlements(JSONWriter jsonWriter, String application) throws JSONException {
        List<Filter> attributeEntitlementFilters = attributeEntitlements.get(application);
        if (attributeEntitlementFilters != null && !attributeEntitlementFilters.isEmpty()) {
            for (Filter attributeEntitlementFilter : attributeEntitlementFilters) {
                // Lucky for us attribute entitlements fall into two categories:  straight up equals and contains all.
                // If our value is a single String we have the former.  Otherwise we have the latter.
                String property = ((LeafFilter) attributeEntitlementFilter).getProperty();
                Object filterValue = ((LeafFilter) attributeEntitlementFilter).getValue();
                
                // Handle the simple case of x == a
                if (filterValue instanceof String) {
                    String value = (String) filterValue;
                    try {
                        addAttributeEntitlement(jsonWriter, application, property, value, false);
                    } catch (GeneralException e) {
                        throw new IllegalArgumentException("No entitlement could be generated from the following expression:" + attributeEntitlementFilter.getExpression());                                            
                    } 
                } else if (filterValue instanceof List){
                    // Handle the more complex case of:
                    // memberOf.containsAll({
                    //     "CN=Benefits_AD,OU=demoGroups,OU=DemoData,DC=test,DC=sailpoint,DC=com",
                    //     "CN=BenefitCommittee_AD,OU=demoGroups,OU=DemoData,DC=test,DC=sailpoint,DC=com",
                    //     "CN=Compensation_AD,OU=demoGroups,OU=DemoData,DC=test,DC=sailpoint,DC=com",
                    //     "CN=Users_AD,OU=demoGroups,OU=DemoData,DC=test,DC=sailpoint,DC=com",
                    //     "CN=CompCommittee_AD,OU=demoGroups,OU=DemoData,DC=test,DC=sailpoint,DC=com"
                    // })
                    List<String> values = (List<String>) filterValue;
                   
                    if (values != null && !values.isEmpty()) {
                        for (String value : values) {
                            try {
                                addAttributeEntitlement(jsonWriter, application, property, value, true);
                            } catch (GeneralException e) {
                                throw new IllegalArgumentException("No entitlement could be generated from the following expression:" + attributeEntitlementFilter.getExpression());                                            
                            } 
                        }                            
                    }
                } else {
                    throw new IllegalArgumentException("No entitlement could be generated from the following expression:" + attributeEntitlementFilter.getExpression());
                }
            }
        }
    }
    
    private void addAttributeEntitlement(JSONWriter jsonWriter, String appId, String name, String value, boolean isMulti) throws JSONException, GeneralException {
        jsonWriter.object();
        jsonWriter.key("name");
        jsonWriter.value(name);
        jsonWriter.key("value");
        jsonWriter.value(value);
        jsonWriter.key("isMulti");
        jsonWriter.value(isMulti);
        jsonWriter.key("displayName");
        // Get the explanation if there is one
        Application app = context.getObjectById(Application.class, appId);
        Explanator.Explanation exp = Explanator.get(app, name, value);
        if (exp != null) {
            jsonWriter.value(exp.getDisplayValue());
            String explanationString = exp.getDescription(locale);
            if (explanationString != null && explanationString.trim().length() > 0) {
                jsonWriter.key("explanation");
                jsonWriter.value(explanationString);
            }
        } else {
            jsonWriter.value(name + " = " + value);
        }
        
        jsonWriter.endObject();
    }

    
    private void encodePermissionEntitlements(JSONWriter jsonWriter, String application) throws JSONException {
        List<Permission> permissions = permissionEntitlements.get(application);
        if (permissions != null && !permissions.isEmpty()) {
            for (Permission permission : permissions) {
                jsonWriter.object();
                jsonWriter.key("target");
                jsonWriter.value(permission.getTarget());
                jsonWriter.key("rights");
                jsonWriter.array();
                List<String> rights = permission.getRightsList();
                if (rights != null && !rights.isEmpty()) {
                    for (String right : rights) {
                        jsonWriter.value(right);
                    }
                }
                jsonWriter.endArray();
                jsonWriter.endObject();
            }
        }
    }
}
