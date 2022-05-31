package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.VelocityUtil;
import sailpoint.web.messages.MessageKeys;

/**
 * Instances of this class are non-persistent objects that contain the information required to 
 * process and display entitlements
 * @author Bernie Margolis
 */
public class Entitlement {
    private String applicationName;
    private String attributeName;
    private String attributeValue;
    private Permission permission;
    private String description;
    private Locale locale;
    
    private static final Log log = LogFactory.getLog(Entitlement.class);
    
    public static final String TEMPLATE_APPLICATION_NAME = "applicationName"; 
    public static final String TEMPLATE_DESCRIPTION = "description"; 
    public static final String TEMPLATE_ATTRIBUTE_NAME = "attributeName";
    public static final String TEMPLATE_ATTRIBUTE_VALUE = "attributeValue";
    public static final String TEMPLATE_PERMISSION_TARGET = "permissionTarget";
    public static final String TEMPLATE_PERMISSION_RIGHTS = "permissionRights";
       
    /**
     * This constructor is used to build entitlements that come from account schemas
     */
    public Entitlement(String applicationName, String attributeName, String attributeValue, String description, Locale locale) {
        this.applicationName = applicationName;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.description = description;
        this.locale = locale;
    }
    
    /**
     * This constructor is used to build entitlements that come from permissions
     */
    public Entitlement(String applicationName, Permission permission, String description, Locale locale) {
        this.applicationName = applicationName;
        this.permission = permission;
        this.description = description;
        this.locale = locale;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public Permission getPermission() {
        return permission;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getRoleName(String template) {
        String roleName;
        
        if (template == null) {
            if (attributeName != null) {
                roleName = new Message(MessageKeys.ENTITLEMENT_ATTRIBUTE_ROLE_NAME, applicationName, attributeName, attributeValue).getLocalizedMessage(locale, null);
            } else if (permission != null) {
                roleName = new Message(MessageKeys.ENTITLEMENT_PERMISSION_ROLE_NAME, applicationName, permission.getRights(), permission.getTarget()).getLocalizedMessage(locale, null);
            } else {
                // This should never happen
                roleName = "Broken Entitlement on " + applicationName;
            }
        } else {
            Map<String, String> params = new HashMap<String, String>();
            params.put(TEMPLATE_APPLICATION_NAME, applicationName);
            params.put(TEMPLATE_DESCRIPTION, description);
            params.put(TEMPLATE_ATTRIBUTE_NAME, attributeName);
            params.put(TEMPLATE_ATTRIBUTE_VALUE, attributeValue);
            if (permission != null) {
                params.put(TEMPLATE_PERMISSION_TARGET, permission.getTarget());
                params.put(TEMPLATE_PERMISSION_RIGHTS, permission.getRights());
            }

            try {
                roleName = VelocityUtil.render(template, params, locale, null);
            } catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error("Unable to create a role name using the template: " + 
                              template + ".  A default name will be generated instead.", e);
                
                roleName = getRoleName(null);
            }
        }
        
        return roleName;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((applicationName == null) ? 0 : applicationName.hashCode());
        result = prime * result
                + ((attributeName == null) ? 0 : attributeName.hashCode());
        result = prime * result
                + ((attributeValue == null) ? 0 : attributeValue.hashCode());
        result = prime * result
                + ((description == null) ? 0 : description.hashCode());
        result = prime * result
                + ((permission == null) ? 0 : permission.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Entitlement other = (Entitlement) obj;
        if (applicationName == null) {
            if (other.applicationName != null)
                return false;
        } else if (!applicationName.equals(other.applicationName))
            return false;
        if (attributeName == null) {
            if (other.attributeName != null)
                return false;
        } else if (!attributeName.equals(other.attributeName))
            return false;
        if (attributeValue == null) {
            if (other.attributeValue != null)
                return false;
        } else if (!attributeValue.equals(other.attributeValue))
            return false;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (permission == null) {
            if (other.permission != null)
                return false;
        } else if (!permission.equals(other.permission))
            return false;
        return true;
    }

    // jsl - I would really rather these not be in the object package with Explanator dependencies...

    public static List<Entitlement> getAccountEntitlements(Locale locale, Link link, String attributeFilter) {
        List<Entitlement> accountEntitlements = new ArrayList<Entitlement>();
        Application app = link.getApplication();
        String appName = "unknown";
        if ( app != null ) {
            appName = app.getName();
            if ( appName == null ) 
                appName = app.getId();
        }

        // we know the schema and entitlement attributes are non-null
        // and non-empty from an earlier check.  If that check had 
        // failed, we never would have reached this code block.
        Schema schema = app.getAccountSchema();
        List<String> attrs = schema.getEntitlementAttributeNames();
        for ( String attr : attrs ) {
            if (attributeFilter == null || (attr != null && attr.startsWith(attributeFilter))) {
                Object v = link.getAttribute(attr);
                List<Object> values = Util.asList(v);
                if ( values != null ) {
                    for ( Object obj : values ) {
                       String strVal = obj.toString();
                       String description = Explanator.getDescription(app, attr, strVal, locale);
                       accountEntitlements.add(new Entitlement(appName, attr, strVal, description, locale));
                    }
                }
            }
        }
        
        return accountEntitlements;
    }
    
    public static List<Entitlement> getPermissionEntitlements(Application app, List<Permission> permissions, Locale locale, String permissionFilter) {
        List<Entitlement> permissionEntitlements = new ArrayList<Entitlement>();
        
        if (permissions != null && !permissions.isEmpty()) {
            for (Permission perm : permissions) {
                String target = perm.getTarget();
                if (permissionFilter == null || (target != null && target.startsWith(permissionFilter))) {
                    String description = Explanator.getPermissionDescription(app, perm.getTarget(), locale);
                    permissionEntitlements.add(new Entitlement(app.getName(), perm, description, locale));
                }
            }
        }

        return permissionEntitlements;
    }

    public static List<Entitlement> getAccountEntitlements(Application app, Map<String, Object> attributes, Locale locale, String attributeFilter) {
        List<Entitlement> accountEntitlements = new ArrayList<Entitlement>();
        
        if (attributes != null && !attributes.isEmpty()) {
            Set<String> attrs = attributes.keySet();
            for ( String attr : attrs ) {
                if (attributeFilter == null || (attr != null && attr.startsWith(attributeFilter))) {
                    Object v = attributes.get(attr);
                    List<Object> values = Util.asList(v);
                    if ( values != null ) {
                        for ( Object obj : values ) {
                           String strVal = obj.toString();
                           String description = Explanator.getDescription(app, attr, strVal, locale);
                           accountEntitlements.add(new Entitlement(app.getName(), attr, strVal, description, locale));
                        }
                    }
                }
            }
        }
        
        return accountEntitlements;
    }
    
}
