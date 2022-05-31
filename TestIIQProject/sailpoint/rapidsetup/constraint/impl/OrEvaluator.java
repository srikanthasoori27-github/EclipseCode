/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Perform an OR of the children
 */
public class OrEvaluator extends BaseConstraintEvaluator {

    private static Log log = LogFactory.getLog(OrEvaluator.class);

    /**
     * @return true if one or more of the children are true, otherwise false
     */
    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        validate(constraintConfig);
        List<Map> children = (List<Map>)constraintConfig.get(ATTR_CHILDREN);
        boolean result = false;

        for( Map child : children) {
            boolean childResult = context.evaluate(child);
            if (childResult) {
                result = true;
                break;
            }
        }

        return result;
    }

    @Override
    public void validate(Map constraintConfig) throws GeneralException {
        List<Map> children = (List<Map>)constraintConfig.get(ATTR_CHILDREN);
        if (Util.isEmpty(children)) {
            throw new GeneralException("OrEvaluator: filter must contain value for key '" + ATTR_CHILDREN + "'");
        }
    }
}
