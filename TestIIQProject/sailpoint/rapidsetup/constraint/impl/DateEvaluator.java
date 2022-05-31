/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class DateEvaluator extends BaseDateEvaluator {

    private static Log log = LogFactory.getLog(DateEvaluator.class);

    public DateEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig)  throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();

        String  property = this.attributeValue.getProperty(),
                dateFormat = this.attributeValue.getDateFormat();

        java.util.Date prevDate = getAttributeAsDate(context, property, Age.OLD, dateFormat);
        java.util.Date newDate  = getAttributeAsDate(context, property, Age.NEW, dateFormat);
        java.util.Date now = new java.util.Date();

        boolean result = false;

        if (newDate == null) {
            // newDate is null
            result = false;
        }
        else  {
            ListFilterValue.Operation operation = this.attributeValue.getOperator();

            // if ChangedTo or ChangedFrom operator, make sure the dates have actually changed
            if (prevDate != null && prevDate.getTime() == newDate.getTime() &&
                    (operation == ListFilterValue.Operation.ChangedTo || operation == ListFilterValue.Operation.ChangedFrom)) {
                // no change
                result = false;
            }
            else {
                result = compareDates(prevDate, newDate, now, this.attributeValue);
            }
        }

        return result;
    }

    public void validate() throws GeneralException {
        dateValidate();
    }
}
