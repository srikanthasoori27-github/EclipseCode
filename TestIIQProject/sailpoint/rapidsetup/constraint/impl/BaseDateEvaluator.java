/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import sailpoint.object.Duration;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.object.Identity;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.IdentityFilterUtil;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Base class for common date operations.
 * Originally this was used by multiple evaluators, but now there is only one date-based evaluator.
 * Decided to keep this separation in case we add more date evaluators back into the mix.
 */
public abstract class BaseDateEvaluator extends BaseAttributeConstraintEvaluator {

    public BaseDateEvaluator(AttributeValueDTO av) {
        super(av);
    }

    /**
     * Compare two dates, typically the date in an Identity attribute compared to today's date.
     *
     * @param prevAttrDate Previous attribute date value
     * @param newAttrDate New attribute date value
     * @param now Date value to compare to
     * @param attributeValue The attribute details, used primarily for operator & value info
     * @return The result of comparing the given dates using the operator in attributeValue
     * @throws GeneralException
     */
    protected boolean compareDates(java.util.Date prevAttrDate, java.util.Date newAttrDate, java.util.Date now,
                                   AttributeValueDTO attributeValue)
            throws GeneralException {

        ListFilterValue.Operation operator = attributeValue.getOperator();

        boolean result;

        if (operator == ListFilterValue.Operation.Before || operator == ListFilterValue.Operation.After) {
            Map period = (Map)attributeValue.getValue();
            int amount = Util.getInt(period, "amount");
            Duration.Scale scale = Duration.Scale.valueOf(Util.getString(period, "scale"));

            if (operator == ListFilterValue.Operation.Before) {
                amount *= -1;
            }

            Date targetDate;

            switch(scale) {
                case Day:
                    targetDate = Date.from(now.toInstant().plus(amount, ChronoUnit.DAYS));
                    break;
                case Week:
                    targetDate = Date.from(ZonedDateTime.now().plusWeeks(amount).toInstant());
                    break;
                case Month:
                    targetDate = Date.from(ZonedDateTime.now().plusMonths(amount).toInstant());
                    break;
                case Year:
                    targetDate = Date.from(ZonedDateTime.now().plusYears(amount).toInstant());
                    break;
                default:
                    targetDate = now;
            }

            if (operator == ListFilterValue.Operation.Before) {
                result = (getDaysBetween(targetDate, newAttrDate) <= 0);
            } else {
                result = (getDaysBetween(targetDate, newAttrDate) >= 0);
            }
        }
        else if (operator == ListFilterValue.Operation.Between) {
            Map range = (Map)attributeValue.getValue();
            Date startDate = Util.getDate(range, "startDate");
            Date endDate = Util.getDate(range, "endDate");

            result = (getDaysBetween(startDate, newAttrDate) >= 0 && getDaysBetween(endDate, newAttrDate) <= 0);
        }
        else {
            Date dateValue = Util.getDate(attributeValue.getValue());

            switch(operator) {
                case Equals:
                case ChangedTo:
                    result = (getDaysBetween(newAttrDate, dateValue) == 0);
                    break;
                case ChangedFrom:
                    result = (getDaysBetween(prevAttrDate, dateValue) == 0);
                    break;
                case NotEquals:
                    result = (getDaysBetween(newAttrDate, dateValue) != 0);
                    break;
                case TodayOrBefore:
                    result = (getDaysBetween(now, newAttrDate) <= 0);
                    break;
                case LessThan:
                    result = (getDaysBetween(dateValue, newAttrDate) < 0);
                    break;
                case LessThanOrEqual:
                    result = (getDaysBetween(dateValue, newAttrDate) <= 0);
                    break;
                case GreaterThan:
                    result = (getDaysBetween(dateValue, newAttrDate) > 0);
                    break;
                case GreaterThanOrEqual:
                    result = (getDaysBetween(dateValue, newAttrDate) >= 0);
                    break;
                default:
                    throw new GeneralException("Unsupported date comparison operator '" + operator + "'");
            }
        }

        return result;
    }

    /**
     * Return an identity attribute as a date, parsing a string into a date if necessary.
     *
     * @param context ConstraintContext
     * @param attrName The identity attribute to return as a date
     * @param age Designator for whether to use the previous or new identity
     * @param dateFormat The date format to use if the attribute is a string
     * @return The attribute value
     * @throws GeneralException
     */
    protected java.util.Date getAttributeAsDate(ConstraintContext context, String attrName, Age age, String dateFormat) throws GeneralException {
        java.util.Date result = null;

        Identity identity = null;
        if (age == Age.NEW) {
            identity = context.getNewIdentity();
        }
        else {
            identity = context.getPrevIdentity();
        }

        if (identity != null) {
            Object attrObj;

            // Modified & Created are base SailPointObject attributes, so need
            // to be handled specially.
            if (attrName.equals(IdentityFilterUtil.MODIFIED)) {
                attrObj = identity.getModified();
            } else if (attrName.equals(IdentityFilterUtil.CREATED)) {
                attrObj = identity.getCreated();
            } else {
                attrObj = identity.getAttribute(attrName);
            }

            if (attrObj != null) {
                if (attrObj instanceof java.util.Date) {
                    result = (java.util.Date) attrObj;
                }
                else if (attrObj instanceof String) {
                    String dateString = (String)attrObj;
                    if (!Util.isEmpty(dateString)) {
                        if (Util.isEmpty(dateFormat)) {
                            throw new GeneralException("String attribute " + attrName + " requires a date format to parse as a date");
                        }
                        try {
                            result = new SimpleDateFormat(dateFormat).parse(dateString);
                        } catch (ParseException e) {
                            throw new GeneralException("String attribute " + attrName + " with value '" +
                                    dateString + "' could be parsed using format " + dateFormat);
                        }
                    }
                }
                else {
                    throw new GeneralException("Attribute " + attrName + " is not a String or a Date");
                }
            }
        }

        return result;
    }

    /**
     * Validate that the constraint configuration is valid for a date operation.
     * @throws GeneralException if the configuration is invalid.
     */
    protected void dateValidate() throws GeneralException {
        super.validate();

        ListFilterValue.Operation operation = this.attributeValue.getOperator();

        if (operation == ListFilterValue.Operation.Before || operation == ListFilterValue.Operation.After ||
                operation == ListFilterValue.Operation.Between) {
            if (!this.attributeValue.hasValue() || !(this.attributeValue.getValue() instanceof Map)) {
                throw new GeneralException("This date operation requires a Map value in " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
            }
        }
        else if (operation != ListFilterValue.Operation.TodayOrBefore && !this.attributeValue.hasValue()) {
            throw new GeneralException("This date operation requires a value in " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
        }
    }

    private long getDaysBetween(java.util.Date date1, java.util.Date date2) {
        return ChronoUnit.DAYS.between(date1.toInstant(), date2.toInstant());
    }
}
