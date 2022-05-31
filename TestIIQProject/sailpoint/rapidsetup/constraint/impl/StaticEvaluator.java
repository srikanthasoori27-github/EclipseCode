/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.constraint.ConstraintUtil;
import sailpoint.tools.GeneralException;

public class StaticEvaluator extends BaseConstraintEvaluator {
    public static String ATTR_VALUE = "value";

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        validate(constraintConfig);

        return getValue(constraintConfig);
    }

    @Override
    public void validate(Map constraintConfig) throws GeneralException {
        if (!hasValue(constraintConfig)) {
            throw new GeneralException("Must have " + ATTR_VALUE + " attribute set");
        }
    }

    private boolean hasValue(Map constraintConfig) {
        if (ConstraintUtil.hasAttributes(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);
            Object value = attributes.get(ATTR_VALUE);
            return (value instanceof Boolean);
        }

        return false;
    }

    private Boolean getValue(Map constraintConfig) {
        if (hasValue(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);

            return (Boolean)attributes.get(ATTR_VALUE);
        }

        return null;
    }
}
