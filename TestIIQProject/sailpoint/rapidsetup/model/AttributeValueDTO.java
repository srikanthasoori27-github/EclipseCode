/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.model;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rapidsetup.constraint.ConstraintUtil;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import sailpoint.rapidsetup.constraint.impl.BaseConstraintEvaluator;

public class AttributeValueDTO {
    private static Log log = LogFactory.getLog(AttributeValueDTO.class);

    public static String ATTR_ATTRIBUTE_VALUE = "attributeValue";
    public static String ATTR_PROPERTY = "property";
    public static String ATTR_OPERATION = "operation";
    public static String ATTR_VALUE = "value";
    public static String ATTR_DATA_TYPE = "dataType";
    public static String ATTR_COERCED_TYPE = "coercedType";
    public static String ATTR_DATE_FORMAT = "dateFormat";

    private String property;
    private ListFilterValue.Operation operator;
    private ListFilterDTO.DataTypes dataType;
    private ListFilterDTO.DataTypes coercedType;
    private String dateFormat;
    private Object value;

    public AttributeValueDTO(Map constraintConfig) {
        this.property = getAttributeValueString(constraintConfig, ATTR_PROPERTY);
        this.dateFormat = getAttributeString(constraintConfig, ATTR_DATE_FORMAT);

        String opString = getAttributeValueString(constraintConfig, ATTR_OPERATION);
        String dtString = getAttributeString(constraintConfig, ATTR_DATA_TYPE);
        String ctString = getAttributeString(constraintConfig, ATTR_COERCED_TYPE);

        try {
            this.operator = ListFilterValue.Operation.valueOf(opString);
            this.dataType = ListFilterDTO.DataTypes.valueOf(dtString);
        } catch(Exception exception) {
            log.warn("Attributes " + ATTR_OPERATION + " and " + ATTR_DATA_TYPE + " are required.", exception);
        }

        try {
            this.coercedType = ListFilterDTO.DataTypes.valueOf(ctString);
        } catch(Exception exception) {
            log.debug("Empty or invalid " + ATTR_COERCED_TYPE + " attribute found. " +
                    "Using " + ATTR_DATA_TYPE + " attribute.");
        }

        this.value = getAttributeValueEntry(constraintConfig, ATTR_VALUE);
    }

    public boolean hasOperator() {
        return this.operator != null;
    }

    public ListFilterValue.Operation getOperator() {
        return this.operator;
    }

    public boolean hasDataType() {
        return this.dataType != null;
    }

    public ListFilterDTO.DataTypes getDataType() {
        return (this.coercedType != null) ? this.coercedType : this.dataType;
    }

    public boolean isCoerced() {
        return this.coercedType != null;
    }

    public boolean hasProperty() {
        return !Util.isNullOrEmpty(this.property);
    }

    public String getProperty() {
        return this.property;
    }

    public String getDateFormat() {
        return this.dateFormat;
    }

    public boolean hasValue() { return this.value != null; }

    public Object getValue() { return this.value; }

    public void setValue(Object obj) { this.value = obj; }

    public boolean isValid() {
        return hasOperator() && hasProperty();
    }

    private boolean hasAttributeValue(Map constraintConfig) {
        if (ConstraintUtil.hasAttributes(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);
            Object attributeValue = attributes.get(ATTR_ATTRIBUTE_VALUE);
            return attributeValue != null;
        }

        return false;
    }

    private Map getAttributeValue(Map constraintConfig) {
        if (hasAttributeValue(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);

            return (Map)attributes.get(ATTR_ATTRIBUTE_VALUE);
        }

        return null;
    }

    private String getAttributeValueString(Map constraintConfig, String key) {
        return (String)getAttributeValueEntry(constraintConfig, key);
    }

    private Object getAttributeValueEntry(Map constraintConfig, String key) {
        Object value = null;

        if (hasAttributeValue(constraintConfig)) {
            Map attributes = getAttributeValue(constraintConfig);
            value = attributes.get(key);
        }

        return value;
    }

    private String getAttributeString(Map constraintConfig, String key) {
        return (String)getAttributeEntry(constraintConfig, key);
    }

    private Object getAttributeEntry(Map constraintConfig, String key) {
        Object value = null;

        if (ConstraintUtil.hasAttributes(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);
            value = attributes.get(key);
        }

        return value;
    }
}
