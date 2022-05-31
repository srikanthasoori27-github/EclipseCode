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
 * Perform an AND of the children
 */
public class AndEvaluator extends BaseConstraintEvaluator {

    private static Log log = LogFactory.getLog(AndEvaluator.class);

    /**
     * @return false if one or more of the children are false, otherwise true
     */
    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        validate(constraintConfig);

        List<Map> children = (List<Map>)constraintConfig.get(ATTR_CHILDREN);
        boolean result = true;

        for( Map child : children) {
            boolean childResult = context.evaluate(child);
            if (!childResult) {
                result = false;
                break;
            }
        }

        return result;
    }

    @Override
    public void validate(Map constraintConfig) throws GeneralException {
        List<Map> children = (List<Map>)constraintConfig.get(ATTR_CHILDREN);
        if (Util.isEmpty(children)) {
            throw new GeneralException("AndEvaluator: filter must contain value for key '" + ATTR_CHILDREN + "'");
        }
    }
}
