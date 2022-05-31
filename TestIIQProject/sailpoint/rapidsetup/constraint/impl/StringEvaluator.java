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

public class StringEvaluator extends BaseAttributeConstraintEvaluator {

    public StringEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();

        boolean result = false;
        ListFilterValue.Operation op = this.attributeValue.getOperator();

        if ((op == ListFilterValue.Operation.ChangedTo || op == ListFilterValue.Operation.ChangedFrom) &&
                !hasChanged(context, this.attributeValue.getProperty())) {
            return false;
        }

        String value = ((String)this.attributeValue.getValue()).toLowerCase();
        Identity prevIdentity = context.getPrevIdentity();
        Identity newIdentity = context.getNewIdentity();
        String attrValue = null;

        if (Util.nullSafeEq(Identity.ATT_USERNAME, this.attributeValue.getProperty())) {
            attrValue = (op == ListFilterValue.Operation.ChangedFrom)
                ? prevIdentity.getName()
                : newIdentity.getName();
        } else {
            attrValue = (op == ListFilterValue.Operation.ChangedFrom)
                    ? prevIdentity.getStringAttribute(this.attributeValue.getProperty())
                    : newIdentity.getStringAttribute(this.attributeValue.getProperty());
        }
        if (!Util.isNullOrEmpty(attrValue)) {
            attrValue = attrValue.toLowerCase();

            switch(op) {
                case Equals:
                case ChangedTo:
                case ChangedFrom:
                    result = attrValue.equals(value);
                    break;
                case NotEquals:
                    result = !attrValue.equals(value);
                    break;
                case Contains:
                    result = attrValue.contains(value);
                    break;
                case NotContains:
                    result = !attrValue.contains(value);
                    break;
                case StartsWith:
                    result = attrValue.startsWith(value);
                    break;
                default:
                    throw new GeneralException("Unsupported string comparison operator '" + op + "'");
            }
        }

        return result;
    }

    public void validate() throws GeneralException {
        super.validate();

        if (!this.attributeValue.hasValue() || !(this.attributeValue.getValue() instanceof String)) {
            throw new GeneralException("A String value is required inside " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
        }
    }
}
