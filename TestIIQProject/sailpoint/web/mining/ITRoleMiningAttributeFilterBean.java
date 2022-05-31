package sailpoint.web.mining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class ITRoleMiningAttributeFilterBean extends BaseBean {
    private static final Log log = LogFactory.getLog(ITRoleMiningAttributeFilterBean.class);
    private List<MiningAttrSelectBean> attributes;
    private static final Set<PropertyType> SUPPORTED_TYPES = new HashSet<PropertyType>(Arrays.asList(new PropertyType [] {
            PropertyType.String, PropertyType.Integer, PropertyType.Boolean, PropertyType.Identity
    }));
    
    public ITRoleMiningAttributeFilterBean() {
        ObjectConfig identityAttributes = ObjectConfig.getObjectConfig(Identity.class);
        attributes = new ArrayList<MiningAttrSelectBean>();
        List<ObjectAttribute> customAttributeList = identityAttributes.getExtendedAttributeList();
        if (customAttributeList != null && !customAttributeList.isEmpty()) {
            for (ObjectAttribute customAttribute : customAttributeList) {
                PropertyType propertyType = customAttribute.getPropertyType();
                // We're only going to support simple types for now.  If the demand exists we can get more complicated than this
                if (SUPPORTED_TYPES.contains(propertyType)) {
                    attributes.add(new MiningAttrSelectBean(customAttribute.getName(), customAttribute.getDisplayName(), "", propertyType));
                }
            }
        }
        
        Collections.sort(attributes, MiningAttrSelectBean.BY_NAME_COMPARATOR);
        attributes.add(0, new MiningAttrSelectBean("inactive", getMessage(MessageKeys.INACTIVE), "", PropertyType.Boolean));
        attributes.add(0, new MiningAttrSelectBean("managerStatus", getMessage(MessageKeys.SRCH_INPUT_DEF_ISMANAGER), "", PropertyType.Boolean));
        attributes.add(0, new MiningAttrSelectBean("manager", getMessage(MessageKeys.MANAGER), "", PropertyType.Identity));
    }

    public ITRoleMiningAttributeFilterBean(ItRoleMiningTemplate template ) {
        ObjectConfig identityAttributes = ObjectConfig.getObjectConfig(Identity.class);
        attributes = new ArrayList<MiningAttrSelectBean>();
        Map<String, String> valuesForIdentityFilters = template.getAttributeValuesForIdentityFilters();
        List<ObjectAttribute> customAttributeList = identityAttributes.getExtendedAttributeList();
        if (customAttributeList != null && !customAttributeList.isEmpty()) {
            for (ObjectAttribute customAttribute : customAttributeList) {
                PropertyType propertyType = customAttribute.getPropertyType();
                // We're only going to support simple types for now.  If the demand exists we can get more complicated than this
                if (SUPPORTED_TYPES.contains(propertyType)) {
                    attributes.add(new MiningAttrSelectBean(customAttribute.getName(), customAttribute.getDisplayableName(getLocale()), valuesForIdentityFilters.get(customAttribute.getName()), propertyType));
                }
            }
        }
        
        Collections.sort(attributes, MiningAttrSelectBean.BY_NAME_COMPARATOR);
        attributes.add(0, new MiningAttrSelectBean("inactive", getMessage(MessageKeys.INACTIVE), valuesForIdentityFilters.get("inactive"), PropertyType.Boolean));
        attributes.add(0, new MiningAttrSelectBean("managerStatus", getMessage(MessageKeys.SRCH_INPUT_DEF_ISMANAGER), valuesForIdentityFilters.get("managerStatus"), PropertyType.Boolean));
        attributes.add(0, new MiningAttrSelectBean("manager", getMessage(MessageKeys.MANAGER), valuesForIdentityFilters.get("manager"), PropertyType.Identity));
    }

    

    public List<MiningAttrSelectBean> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<MiningAttrSelectBean> attributes) {
        this.attributes = attributes;
    }
    
    public Filter getFilter() {
        Filter identityFilter = null;
        
        if (this.attributes != null && !this.attributes.isEmpty()) {
            ArrayList<Filter> filters = new ArrayList<Filter>();
            for (MiningAttrSelectBean attribute : this.attributes) {
                if (attribute.getValue() != null && attribute.getValue().trim().length() > 0) {
                    if (attribute.getType() == PropertyType.String) {
                        filters.add(Filter.ignoreCase(Filter.eq(attribute.getName(), attribute.getValue())));
                    } else if (attribute.getType() == PropertyType.Boolean) {
                        String booleanVal = attribute.getValue();
                        if (booleanVal != null && booleanVal.equalsIgnoreCase(Boolean.TRUE.toString()) || booleanVal.equalsIgnoreCase(Boolean.FALSE.toString())) {
                            filters.add(Filter.eq(attribute.getName(), Boolean.valueOf(attribute.getValue())));
                        }
                    } else if (attribute.getType() == PropertyType.Identity) {
                        Filter filter = getFilterForIdentityTypedAttribute(attribute);
                        if (filter != null) {
                            filters.add(filter);
                        }
                    } else if (attribute.getType() == PropertyType.Integer) {
                        try {
                            int value = Integer.parseInt(attribute.getValue());
                            filters.add(Filter.eq(attribute.getName(), value));
                        } catch (NumberFormatException e) {
                            log.warn(attribute.getValue() + " is not a paresable integer.  The filter for the attribute named " + attribute.getDisplayName() + " will be ignored by the IT role mining scheduler.", e);
                        } catch (IllegalArgumentException e) {
                            log.warn(attribute.getValue() + " is not a paresable integer.  The filter for the attribute named " + attribute.getDisplayName() + " will be ignored by the IT role mining scheduler.", e);
                        }
                    } else {
                        log.debug("Could not generate a filter for identity attribute named " + attribute.getName() + " with property type " + attribute.getType());
                    }
                }
            }
            
            if (filters.size() == 1) {
                identityFilter = filters.get(0);
            } else if (filters.size() > 1) {
                identityFilter = Filter.and(filters);
            }
        }

        return identityFilter;
    }

    public List<Filter> getFilters() {
        ArrayList<Filter> filters = new ArrayList<Filter>();
        for (MiningAttrSelectBean attribute : this.attributes) {
            if (attribute.getValue() != null && attribute.getValue().trim().length() > 0) {
                if (attribute.getType() == PropertyType.String) {
                    filters.add(Filter.eq(attribute.getName(), attribute.getValue()));
                } else if (attribute.getType() == PropertyType.Boolean) {
                    String booleanVal = attribute.getValue();
                    if (booleanVal != null && booleanVal.equalsIgnoreCase(Boolean.TRUE.toString()) || booleanVal.equalsIgnoreCase(Boolean.FALSE.toString())) {
                        filters.add(Filter.eq(attribute.getName(), Boolean.valueOf(attribute.getValue())));
                    }
                } else if (attribute.getType() == PropertyType.Identity) {
                    Filter filter = getFilterForIdentityTypedAttribute(attribute);
                    if (filter != null) {
                        filters.add(filter);
                    }
                } else if (attribute.getType() == PropertyType.Integer) {
                    try {
                        int value = Integer.parseInt(attribute.getValue());
                        filters.add(Filter.eq(attribute.getName(), value));
                    } catch (NumberFormatException e) {
                        log.warn(attribute.getValue() + " is not a paresable integer.  The filter for the attribute named " + attribute.getDisplayName() + " will be ignored by the IT role mining scheduler.", e);
                    } catch (IllegalArgumentException e) {
                        log.warn(attribute.getValue() + " is not a paresable integer.  The filter for the attribute named " + attribute.getDisplayName() + " will be ignored by the IT role mining scheduler.", e);
                    }
                } else {
                    log.debug("Could not generate a filter for identity attribute named " + attribute.getName() + " with property type " + attribute.getType());
                }
            }
        }
        
        return filters;
    }
    
    private Filter getFilterForIdentityTypedAttribute(MiningAttrSelectBean attribute) {
        String[] value = attribute.getValue().split("\\s");
        String attributeName = attribute.getName();
        Filter filter;
        
        if (value.length == 1) {
            filter = Filter.or(
                    Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[0], MatchMode.START)),
                    Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[0], MatchMode.START)),
                    Filter.ignoreCase(Filter.like(attributeName + ".name", value[0], MatchMode.START))
            ); 
        } else if (value.length == 2) {
            filter = Filter.or(
                    Filter.and(Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[0], MatchMode.START)), Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[1], MatchMode.START))),
                    Filter.ignoreCase(Filter.like(attributeName + ".name", value[0] + " " + value[1], MatchMode.START)));
        } else if (value.length > 2) {
            List<Filter> filters = new ArrayList<Filter>();
            for (int i = 0; i < value.length; ++i) {
                filters.add(Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[i], MatchMode.START)));
                filters.add(Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[i], MatchMode.START)));
                filters.add(Filter.ignoreCase(Filter.like(attributeName + ".name", value[i], MatchMode.START)));
            }
            filter = Filter.or(filters);
        } else {
            filter = null;
        }

        return filter;
    }
}
