/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.listfilter;

import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of a ListFilterDTO value. Contains an Operation and a value.
 */
@XMLClass
public class ListFilterValue {

    public static final String FILTER_MAP_VALUE = "value";
    public static final String FILTER_MAP_OPERATION = "operation";
    public static final String FILTER_MAP_PROPERTY = "property";
    
    /**
     * Enumeration of operations for a filter.
     */
    @XMLClass(xmlname = "ListFilterValueOperation")
    public static enum Operation implements MessageKeyHolder {
        Equals(MessageKeys.LIST_FILTER_OP_EQUALS),
        GreaterThan(MessageKeys.LIST_FILTER_OP_GREATERTHAN),
        GreaterThanOrEqual(MessageKeys.LIST_FILTER_OP_GREATERTHANOREQUAL),
        LessThan(MessageKeys.LIST_FILTER_OP_LESSTHAN),
        LessThanOrEqual(MessageKeys.LIST_FILTER_OP_LESSTHANOREQUAL),
        NotEquals(MessageKeys.LIST_FILTER_OP_NOTEQUALS),
        StartsWith(MessageKeys.LIST_FILTER_OP_STARTSWITH),
        Between(MessageKeys.LIST_FILTER_OP_BETWEEN),
        EndsWith(MessageKeys.LIST_FILTER_OP_ENDSWITH),
        Contains(MessageKeys.LIST_FILTER_OP_CONTAINS),
        NotContains(MessageKeys.LIST_FILTER_OP_NOTCONTAINS),

        Changed(MessageKeys.LIST_FILTER_OP_CHANGED),
        NotChanged(MessageKeys.LIST_FILTER_OP_NOTCHANGED),
        ChangedTo(MessageKeys.LIST_FILTER_OP_CHANGEDTO),
        ChangedFrom(MessageKeys.LIST_FILTER_OP_CHANGEDFROM),
        // Relative date operators, currently only used for identity triggers.
        Before(MessageKeys.LIST_FILTER_OP_BEFORE),
        After(MessageKeys.LIST_FILTER_OP_AFTER),
        TodayOrBefore(MessageKeys.LIST_FILTER_OP_TODAYORBEFORE);

        private String messageKey;

        Operation(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    /**
     * Filter value operation.
     */
    private Operation operation;

    /**
     * Filter value actual string value.
     */
    private Object value;

    /**
     * Filter property. Should match a ListFilterDTO. Optional.
     */
    private String property;

    public ListFilterValue() {
        
    }
    
    public ListFilterValue(Object value, Operation operation) {
        this.value = value;
        this.operation = operation;
        // Default to equals
        if (this.operation == null) {
            this.operation = Operation.Equals;
        }
    }

    public ListFilterValue(Object value, Operation operation, String property) {
        this(value, operation);
        this.property = property;
    }

    public ListFilterValue(ListFilterValue listFilterValue) {
        this(listFilterValue.getValue(), listFilterValue.getOperation(), listFilterValue.getProperty());
    }
    
    public ListFilterValue(Map<String, Object> filterValueMap) {
        if (filterValueMap != null) {
            this.value = filterValueMap.get(FILTER_MAP_VALUE);
            if (filterValueMap.get(FILTER_MAP_OPERATION) != null) {
                this.operation = Enum.valueOf(Operation.class, (String)filterValueMap.get(FILTER_MAP_OPERATION));
            } else {
                this.operation = Operation.Equals;
            }

            if (filterValueMap.containsKey(FILTER_MAP_PROPERTY)) {
                this.property = Util.getString(filterValueMap, FILTER_MAP_PROPERTY);
            }
        }
    }

    public Map<String, Object> toMap() {
        return new HashMap<String, Object>() {{
            put(FILTER_MAP_PROPERTY, getProperty());
            put(FILTER_MAP_OPERATION, getOperation().name());
            put(FILTER_MAP_VALUE, getValue());
        }};
    }

    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getProperty());
        sb.append(".");
        sb.append(getOperation().toString());
        sb.append("(");
        sb.append(getValue());
        sb.append(")");
        return sb.toString();
    }

    /**
     * @return Filter value operation.
     */
    @XMLProperty
    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    /**
     * @return Filter value.
     */
    @XMLProperty
    public Object getValue() {
        return this.value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * @return Filter property
     */
    @XMLProperty
    public String getProperty() {
        return this.property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}
