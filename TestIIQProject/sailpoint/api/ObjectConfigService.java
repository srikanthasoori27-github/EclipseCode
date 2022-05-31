/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A service to help when dealing with object config.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class ObjectConfigService {

    private static final String TYPE_RULE = Rule.class.getName();
    private static final String TYPE_IDENTITY = Identity.class.getName();

    private SailPointContext _context;

    private Locale _locale;

    /**
     * Constructs a new instance of ObjectConfigService.
     * @param context The context.
     * @param locale The user locale.
     */
    public ObjectConfigService(SailPointContext context, Locale locale) {
        _context = context;
        _locale = locale;
    }

    /**
     * Creates a map of extended attribute display values for the object keyed by the display name
     * of the object attribute.
     * @param obj The object.
     * @param <T> The type of the object.
     * @return The map.
     * @throws GeneralException
     */
    public <T extends SailPointObject> Map<String, String> getExtendedAttributeDisplayValues(T obj)
        throws GeneralException {

        Map<String, String> valueMap = new HashMap<String, String>();

        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(obj.getClass());
        if (objectConfig != null) {
            for (ObjectAttribute objectAttribute : Util.iterate(objectConfig.getObjectAttributes())) {
                String value = getExtendedAttributeDisplayValue(obj, objectAttribute);
                if (value != null) {
                    valueMap.put(objectAttribute.getDisplayableName(_locale), value);
                }
            }
        }

        return valueMap;
    }

    /**
     * Creates a map of extended attribute values for the object keyed by the attribute name
     * of the object attribute.
     * @param obj The object.
     * @param <T> The type of the object.
     * @return The map.
     * @throws GeneralException
     */
    public <T extends SailPointObject> Map<String, String> getExtendedAttributeValues(T obj)
            throws GeneralException {

        Map<String, String> valueMap = new HashMap<String, String>();

        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(obj.getClass());
        if (objectConfig != null) {
            for (ObjectAttribute objectAttribute : Util.iterate(objectConfig.getObjectAttributes())) {
                String value = getExtendedAttributeDisplayValue(obj, objectAttribute);
                if (value != null) {
                    valueMap.put(objectAttribute.getName(), value);
                }
            }
        }

        return valueMap;
    }

    /**
     * Gets the String display value for the specified object attribute out of the attributes map
     * of the specified object. If the object attribute type is identity or rule then the id
     * will be converted into the displayable name.
     * @param obj The object.
     * @param objectAttribute The object attribute.
     * @param <T> The type of the object.
     * @return The display value or null if no value exists.
     * @throws GeneralException
     */
    public <T extends SailPointObject> String getExtendedAttributeDisplayValue(T obj, ObjectAttribute objectAttribute)
        throws GeneralException {

        if (obj == null || objectAttribute == null) {
            return null;
        }

        String result = null;

        Object value = obj.getExtendedAttribute(objectAttribute.getName());
        if (value != null) {
            if (TYPE_RULE.equals(objectAttribute.getType())) {
                Rule rule = _context.getObjectById(Rule.class, value.toString());
                if (rule != null) {
                    result = rule.getName();
                }
            } else if (TYPE_IDENTITY.equals(objectAttribute.getType())) {
                Identity identity = _context.getObjectById(Identity.class, value.toString());
                if (identity != null) {
                    result = identity.getDisplayableName();
                }
            } else {
                result = value.toString();
            }
        }

        return result;
    }
}
