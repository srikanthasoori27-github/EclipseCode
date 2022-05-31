package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * Role utility class for Role Attributes
 *
 */
public class RoleAttributesUtil {
    
    public interface IFilteredAttributesInfo {
        public List<? extends BaseAttributeDefinition> getFilteredAttributeDefinitions();
        public List<? extends BaseAttributeDefinition> getMissingAttributeDefinitions();
    }

    private static final Log log = LogFactory.getLog(RoleAttributesUtil.class);

    public static RoleAttributesUtil.IFilteredAttributesInfo getDisallowedFilteredAttributesInfo(final List<? extends BaseAttributeDefinition> attributeDefinitions, Bundle role) {
        
        RoleAttributesUtil.IFilteredAttributesInfo defaultInfo = new RoleAttributesUtil.IFilteredAttributesInfo() {
            
            public List<? extends BaseAttributeDefinition> getMissingAttributeDefinitions() {
                return new ArrayList<BaseAttributeDefinition>();
            }
            
            public List<? extends BaseAttributeDefinition> getFilteredAttributeDefinitions() {
                return attributeDefinitions;
            }
        };
        RoleTypeDefinition typeDefinition = ObjectConfig.getObjectConfig(Bundle.class).getRoleType(role);
        if (typeDefinition == null) {
            return defaultInfo;
        }
        List<String> disallowedAttributes = typeDefinition.getDisallowedAttributes();
        if (Util.isEmpty(disallowedAttributes)) {
            return defaultInfo;
        }
    
        final List<BaseAttributeDefinition> filtered = new ArrayList<BaseAttributeDefinition>();
        filtered.addAll(attributeDefinitions);
        
        RoleAttributesUtil.removeAttributesFromList(filtered, disallowedAttributes);
        
        final List<? extends BaseAttributeDefinition> missingAttributes = RoleAttributesUtil.findMissingAttributeDefinitions(attributeDefinitions, filtered, role);
        filtered.addAll(missingAttributes);
        
        return new RoleAttributesUtil.IFilteredAttributesInfo() {
            
            public List<? extends BaseAttributeDefinition> getMissingAttributeDefinitions() {
                return missingAttributes;
            }
            
            public List<? extends BaseAttributeDefinition> getFilteredAttributeDefinitions() {
                return filtered;
            }
        };
    }

    private static void removeAttributesFromList(List<BaseAttributeDefinition> attributes, List<String> toRemoveNames) {
        
        for (String removeName : toRemoveNames) {
            Iterator<? extends BaseAttributeDefinition> iterator = attributes.iterator();
            while (iterator.hasNext()) {
                if (removeName.equals(iterator.next().getName())) {
                    iterator.remove();
                }
            }
        }
    }

    private static List<? extends BaseAttributeDefinition> findMissingAttributeDefinitions(List<? extends BaseAttributeDefinition> originalList, List<? extends BaseAttributeDefinition> filteredList, Bundle role) {
        
        List<BaseAttributeDefinition> missingAttributes = new ArrayList<BaseAttributeDefinition>();
        
        Attributes<String, Object> roleAttributes = role.getAttributes();
        if (Util.isEmpty(roleAttributes)) {
            return missingAttributes;
        }
        
        for (String attributeName : roleAttributes.keySet()) {
            if (RoleAttributesUtil.findAttributeByName(filteredList, attributeName) == null) {
                BaseAttributeDefinition originalAttribute = RoleAttributesUtil.findAttributeByName(originalList, attributeName);
                if (originalAttribute != null) {
                    missingAttributes.add(originalAttribute);
                }
            }
        }
        
        return missingAttributes;
    }

    private static BaseAttributeDefinition findAttributeByName(List<? extends BaseAttributeDefinition> attributes, String attributeName) {
        
        for (BaseAttributeDefinition attribute : attributes) {
            if (attributeName.equals(attribute.getName())) {
                return attribute;
            }
        }
        
        return null;
    }
    
    public static class AttributesInfo {
        
        private List<Map<String, Object>> attributesWithNoCategory;
        private Map<String, List<Map<String, Object>>> attributesByCategory;
        private boolean missingAttributePresent;
        
        public AttributesInfo() {
            this.attributesWithNoCategory = new ArrayList<Map<String, Object>>();
            this.attributesByCategory = new HashMap<String, List<Map<String,Object>>>();
        }

        public List<Map<String, Object>> getAttributesWithNoCategory() {
            return this.attributesWithNoCategory;
        }

        public void setAttributesWithNoCategory(
                List<Map<String, Object>> attributesWithNoCategory) {
            this.attributesWithNoCategory = attributesWithNoCategory;
        }

        public Map<String, List<Map<String, Object>>> getAttributesByCategory() {
            return this.attributesByCategory;
        }

        public void setAttributesByCategory(
                Map<String, List<Map<String, Object>>> attributesByCategory) {
            this.attributesByCategory = attributesByCategory;
        }

        public boolean isMissingAttributePresent() {
            return this.missingAttributePresent;
        }

        public void setMissingAttributePresent(boolean disallowedAttributePresent) {
            this.missingAttributePresent = disallowedAttributePresent;
        }
        
    }
    
    @SuppressWarnings("unchecked")
    public static AttributesInfo getAttributesInfo(Bundle role, Locale locale) throws GeneralException {
        
        AttributesInfo info = new AttributesInfo();
        
        ObjectConfig cfg = ObjectConfig.getObjectConfig(Bundle.class);
        if (cfg == null) {
            return info;
        }
        
        List<ObjectAttribute> attributes = cfg.getObjectAttributes();
        if (Util.isEmpty(attributes)) {
            return info;
        }
        
        
        IFilteredAttributesInfo filteredInfo = RoleAttributesUtil.getDisallowedFilteredAttributesInfo(attributes, role); 
        attributes = (List<ObjectAttribute>) filteredInfo.getFilteredAttributeDefinitions();
        List<ObjectAttribute> missingAttributes = (List<ObjectAttribute>) filteredInfo.getMissingAttributeDefinitions();

        for (ObjectAttribute attribute : attributes) {
            String categoryName = attribute.getCategoryName();
            boolean missing = isMissingAttribute(attribute, missingAttributes);
            if (missing) {
                info.setMissingAttributePresent(true);
            }
            if (Util.isNullOrEmpty(categoryName)) {
                info.getAttributesWithNoCategory().add(getAttributeInfo(attribute, role, missing, locale));
            } else {
                if (!info.getAttributesByCategory().containsKey(categoryName)) {
                    List<Map<String, Object>> categoryAttributes = new ArrayList<Map<String, Object>>();
                    info.getAttributesByCategory().put(categoryName, categoryAttributes);
                }
                
                List<Map<String, Object>> categoryAttributes = info.getAttributesByCategory().get(categoryName);
                categoryAttributes.add(getAttributeInfo(attribute, role, missing, locale));
            }
        }
        
        return info;
    }
    
    private static boolean isMissingAttribute(ObjectAttribute attribute, List<ObjectAttribute> missingAttributes) {
        
        if (missingAttributes == null) {
            return false;
        }
        
        for (ObjectAttribute missingAttribute : missingAttributes) {
            if (missingAttribute.getName().equals(attribute.getName())) {
                return true;
            }
        }
        
        return false;
    }
    
    private static Map<String, Object> getAttributeInfo(ObjectAttribute attribute, Bundle role, boolean missing, Locale locale) {
        
        Map<String, Object> attributeInfo = new HashMap<String, Object>();
        
        attributeInfo.put("displayName", attribute.getDisplayableName(locale));
        attributeInfo.put("type", attribute.getType());
        attributeInfo.put("value", getStringValueForAttribute(attribute, role.getAttribute(attribute.getName())));
        attributeInfo.put("missing", missing);
        
        return attributeInfo;
    }
    
    private static String getStringValueForAttribute(ObjectAttribute attr, Object value) {
        final String stringForAttribute;
        final String type = attr.getType();

        if (value == null) {
            stringForAttribute = null;
        } else if (ObjectAttribute.TYPE_STRING.equals(type)) {
            stringForAttribute = (String) value;
        } else if (ObjectAttribute.TYPE_SECRET.equals(type)) {
            stringForAttribute = "******";
        } else if (ObjectAttribute.TYPE_LONG.equals(type) || ObjectAttribute.TYPE_INT.equals(type)) {
            stringForAttribute = String.valueOf(value);
        } else if (ObjectAttribute.TYPE_BOOLEAN.equals(type)) {
            stringForAttribute = Boolean.toString(Util.otob(value));
        } else if (ObjectAttribute.TYPE_DATE.equals(type)) {
            stringForAttribute = String.valueOf(((Date) value).getTime());
        } else if (ObjectAttribute.TYPE_IDENTITY.equals(type)) {
            if (value == null) {
                stringForAttribute = "";
            } else if (value instanceof Identity) {
                Identity identity = (Identity) value;
                stringForAttribute = identity.getDisplayableName();
            } else if (value instanceof String) {
                try {
                    Identity identity = SailPointFactory.getCurrentContext().getObjectByName(Identity.class, (String) value);
                    if (identity == null) {
                        stringForAttribute = "";
                    } else {
                        stringForAttribute = identity.getDisplayableName();
                    }
                    
                } catch (GeneralException ex) {
                    log.error(ex);
                    throw new IllegalStateException("Identity Attr: " + value);
                }
            } else {
                stringForAttribute = value.toString();
            }
        } else if (ObjectAttribute.TYPE_RULE.equals(type)) {
            if (value == null) {
                stringForAttribute = "";
            } else if (value instanceof Rule) {
                Rule rule = (Rule) value;
                stringForAttribute = rule.getName();
            } else if (value instanceof String) {
                try {
                    Rule rule = SailPointFactory.getCurrentContext().getObjectById(Rule.class, (String) value);
                    stringForAttribute = rule.getName();
                } catch (GeneralException ex) {
                    log.error(ex);
                    throw new IllegalStateException("Rule attr: " + value);
                }
            } else {
                stringForAttribute = value.toString();
            }
        } else {
            stringForAttribute = value.toString();
        }
        
        log.debug("String value for attribute " + attr.getName() + ": " + stringForAttribute);
        return stringForAttribute;
    }
    
    
}
