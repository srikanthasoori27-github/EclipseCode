/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.object.Identity;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

public class GenericChangeEvaluator extends BaseAttributeConstraintEvaluator {

    public GenericChangeEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();
        String property = attributeValue.getProperty();

        boolean changed = hasChanged(context, property);
        if (attributeValue.getOperator() == ListFilterValue.Operation.NotChanged) {
            changed = !changed;
        }

        return changed;
    }

    public void validate() throws GeneralException {
        super.validate();
    }
}
