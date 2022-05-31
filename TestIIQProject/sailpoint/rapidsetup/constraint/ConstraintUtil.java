/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.constraint;

import java.util.Map;

public class ConstraintUtil {

    public static final String ATTR_ATTRIBUTES_NAME = "attributes";

    public static boolean hasAttributes(Map constraintConfig) {
        if (constraintConfig != null) {
            Object attributes = constraintConfig.get(ATTR_ATTRIBUTES_NAME);
            return attributes != null;
        }

        return false;
    }

    public static Map getAttributes(Map constraintConfig) {
        if (hasAttributes(constraintConfig)) {
            return (Map)constraintConfig.get(ATTR_ATTRIBUTES_NAME);
        }

        return null;
    }
}
