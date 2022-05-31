/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.tools.GeneralException;

public abstract class BaseAttributeConstraintEvaluator extends BaseConstraintEvaluator {

    protected AttributeValueDTO attributeValue;

    BaseAttributeConstraintEvaluator(AttributeValueDTO av) {
        this.attributeValue = av;
    }

    protected void checkAttributeValue(Map constraintConfig) {
        if (this.attributeValue == null) {
            this.setAttributeValue(constraintConfig);
        }
    }

    protected void setAttributeValue(Map constraintConfig) {
        attributeValue = new AttributeValueDTO(constraintConfig);
    }

    @Override
    public void validate(Map constrantConfig) throws GeneralException {
        this.setAttributeValue(constrantConfig);
        validate();
    }

    public void validate() throws GeneralException {
        if (!this.attributeValue.isValid()) {
            throw new GeneralException("Attribute details must contain both " + AttributeValueDTO.ATTR_PROPERTY +
                    " and " + AttributeValueDTO.ATTR_OPERATION);
        }
    }
}
