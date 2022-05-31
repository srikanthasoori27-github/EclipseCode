/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.object.Identity;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class NumberEvaluator extends BaseAttributeConstraintEvaluator {

    public NumberEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();

        boolean result;
        ListFilterValue.Operation op = this.attributeValue.getOperator();

        if ((op == ListFilterValue.Operation.ChangedTo || op == ListFilterValue.Operation.ChangedFrom)
                && !hasChanged(context, this.attributeValue.getProperty())) {
            return false;
        }

        Integer value = (Integer)this.attributeValue.getValue();
        Identity prevIdentity = context.getPrevIdentity();
        Identity newIdentity = context.getNewIdentity();
        Integer attrValue = (op == ListFilterValue.Operation.ChangedFrom)
            ? this.getAttributeAsNumber(prevIdentity, this.attributeValue.getProperty())
            : this.getAttributeAsNumber(newIdentity, this.attributeValue.getProperty());

        if (attrValue == null) {
            return false;
        }

        switch(op) {
            case Equals:
            case ChangedTo:
            case ChangedFrom:
                result = attrValue.equals(value);
                break;
            case NotEquals:
                result = !attrValue.equals(value);
                break;
            case GreaterThan:
                result = attrValue > value;
                break;
            case GreaterThanOrEqual:
                result = attrValue >= value;
                break;
            case LessThan:
                result = attrValue < value;
                break;
            case LessThanOrEqual:
                result = attrValue <= value;
                break;
            default:
                throw new GeneralException("Unsupported string comparison operator '" + op + "'");
        }

        return result;
    }

    public void validate() throws GeneralException {
        super.validate();

        if (!this.attributeValue.hasValue() || !(this.attributeValue.getValue() instanceof Integer)) {
            throw new GeneralException("An integer value is required inside " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
        }
    }

    /**
     * Return an identity attribute as an integer, parsing a string if necessary.
     *
     * @param identity The identity to use
     * @param attrName The attribute to return as an integer
     * @return The attribute value
     * @throws GeneralException
     */
    private Integer getAttributeAsNumber(Identity identity, String attrName) throws GeneralException {
        Object value = identity.getAttribute(attrName);
        Integer result = null;

        if (value instanceof Integer) {
            result = (Integer)value;
        } else if (value instanceof String) {
            try {
                result = Integer.parseInt((String)value);
            } catch (NumberFormatException nfe) {
                throw new GeneralException("NumberEvaluator: String value could not be parsed as an integer", nfe);
            }
        }

        return result;
    }
}
