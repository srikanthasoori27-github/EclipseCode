/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.List;

import sailpoint.object.Attributes;


/**
 * Bag of utility methods to help getting values from the Attributes class.
 */
public class AttributesUtil {

    public static Object getRequiredValue(Attributes<String,Object> attrs,
                                          String attrName)
        throws GeneralException {

        Object value =  null;
        if ( attrs != null ) {
            value = attrs.get(attrName);
        }
        checkForNonNullValue(attrName, value);
        return value;
    }

    public static String getRequiredStringValue(Attributes<String,Object> attrs,
                                                String attrName) 
        throws GeneralException {

        String value = null;
        if ( attrs != null ) {
           value = attrs.getString(attrName);
        }
        checkForNonNullValue(attrName, value);
        return value;
    }

    public static List getRequiredListValue(Attributes<String,Object> attrs,
                                            String attrName) 
        throws GeneralException {

        List value = null;
        if ( attrs != null ) {
           value = attrs.getList(attrName);
        }
        checkForNonNullValue(attrName, value);
        return value;
    }

    public static Long getRequiredLongValue(Attributes<String,Object> attrs,
                                            String attrName) 
        throws GeneralException {

        Long value = null;
        if ( attrs != null ) {
            value = attrs.getLong(attrName);
        }
        checkForNonNullValue(attrName, value);
        return value;
    }


    public static boolean getRequiredBooleanValue(Attributes<String,Object> attrs, String attrName) 
        throws GeneralException  {

        Boolean value = null;
        if ( attrs != null ) {
            value = attrs.getBooleanObject(attrName);
        }
        checkForNonNullValue(attrName, value);
        return value;

    }

    private static void checkForNonNullValue(String attrName, Object value) 
        throws GeneralException {

        if ( value == null ) {
            throw new GeneralException("Required value for attribute "
                                       + attrName + " was not found.");
        }
    }
}
