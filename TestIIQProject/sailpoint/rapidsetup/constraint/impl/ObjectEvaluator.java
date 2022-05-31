/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

public class ObjectEvaluator extends BaseAttributeConstraintEvaluator {

    public ObjectEvaluator(AttributeValueDTO av) {
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

        Identity newIdentity = context.getNewIdentity();
        Identity prevIdentity = context.getPrevIdentity();
        String rawValue = (String)this.attributeValue.getValue();
        SailPointObject value = null;
        SailPointObject attrValue = null;
        Class objectType = null;

        // For now, only supporting Application type.
        // This can be expanded later if needed.
        if (this.attributeValue.getDataType() == ListFilterDTO.DataTypes.Application) {
            objectType = Application.class;
            attrValue = (op == ListFilterValue.Operation.ChangedFrom)
                    ? (Application)prevIdentity.getAttribute(this.attributeValue.getProperty())
                    : (Application)newIdentity.getAttribute(this.attributeValue.getProperty());
        }
        else {
            throw new GeneralException("Invalid data type " + this.attributeValue.getDataType() + " in constraint configuration value.");
        }

        if (attrValue == null) {
            return false;
        }
        if (objectType != null && op != ListFilterValue.Operation.StartsWith) {
            value = context.getSailPointContext().getObjectById(objectType, rawValue);

            if (value == null) {
                throw new GeneralException("Invalid object type or ID in constraint configuration value.");
            }
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
            case StartsWith:
                result = attrValue.getName().toLowerCase().startsWith(rawValue.toLowerCase());
                break;
            default:
                throw new GeneralException("Unsupported string comparison operator '" + op + "'");
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
