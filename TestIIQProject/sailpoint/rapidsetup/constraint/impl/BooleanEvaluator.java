package sailpoint.rapidsetup.constraint.impl;

import sailpoint.object.Identity;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

import java.util.Map;

public class BooleanEvaluator extends BaseAttributeConstraintEvaluator {

    public BooleanEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();

        ListFilterValue.Operation operation = this.attributeValue.getOperator();

        if ((operation == ListFilterValue.Operation.ChangedTo || operation == ListFilterValue.Operation.ChangedFrom)
                && !hasChanged(context, this.attributeValue.getProperty())) {
            return false;
        }

        Boolean attrValue;
        boolean value;

        if (operation == ListFilterValue.Operation.ChangedFrom) {
            Identity prevIdentity = context.getPrevIdentity();
            attrValue = (Boolean)prevIdentity.getAttribute(this.attributeValue.getProperty());
        } else {
            Identity newIdentity = context.getNewIdentity();
            attrValue = (Boolean)newIdentity.getAttribute(this.attributeValue.getProperty());
        }

        if (attrValue == null) {
            return false;
        } else {
            value = (boolean)this.attributeValue.getValue();

            if (operation == ListFilterValue.Operation.NotEquals) {
                value = !value;
            }
        }

        return attrValue == value;
    }

    public void validate() throws GeneralException {
        super.validate();

        if (!this.attributeValue.hasValue() || !(this.attributeValue.getValue() instanceof Boolean)) {
            throw new GeneralException("BooleanEvaluator: boolean value expected in " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
        }
    }
}
