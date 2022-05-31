/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.object;

import java.util.Map;

import sailpoint.tools.JsonHelper;

/**
 * Class to hold the data sent from the server used to
 * instantiate the form related classes, expand the form
 * and then extract the allowed values for a field.
 */
public class DynamicValuesOptions extends FormOptions {

    /**
     * The name of the field the dynamic allowed values
     * should be retrieved for.
     */
    private String fieldName;

    /**
     * The start record number.
     */
    private String start;

    /**
     * The number of items on a page.
     */
    private String limit;

    /**
     * The user search query.
     */
    private String query;

    /**
     * Default constructor for DynamicValuesOptions.
     */
    public DynamicValuesOptions() {}

    /**
     * Constructs an instance of DynamicValuesOptions from the specified map.
     *
     * The map should contain the following fields:
     *  - fieldName
     *  - data
     *  - formBeanClass
     *  - formBeanState
     *  - formId
     *  - start
     *  - limit
     *  - query
     *
     * @param data
     */
    public DynamicValuesOptions(Map<String, Object> data) {
        super(data);
        // data and formBeanState must be strings
        Object stateObj = data.get("formBeanState");
        if (!(stateObj instanceof String)) {
            stateObj = JsonHelper.toJson(stateObj);
        }

        Object dataObj = data.get("data");
        if (!(dataObj instanceof String)) {
            dataObj = JsonHelper.toJson(dataObj);
        }

        setFieldName((String) data.get("fieldName"));

        if (data.containsKey("start")) {
            setStart(data.get("start").toString());
        }

        if (data.containsKey("limit")) {
            setLimit(data.get("limit").toString());
        }

        setQuery((String) data.get("query"));
    }

    /**
     * Gets the field name.
     *
     * @return The field name.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Sets the field name.
     *
     * @param fieldName The field name.
     */
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Gets the start record number.
     *
     * @return The start record.
     */
    public String getStart() {
        return start;
    }

    /**
     * Sets the start record number.
     *
     * @param start The start record.
     */
    public void setStart(String start) {
        this.start = start;
    }

    /**
     * Gets the page limit.
     *
     * @return The page limit.
     */
    public String getLimit() {
        return limit;
    }

    /**
     * Sets the page limit.
     *
     * @param limit The page limit.
     */
    public void setLimit(String limit) {
        this.limit = limit;
    }

    /**
     * Gets the query string entered by the user.
     *
     * @return The query string.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Sets the query string entered by the user.
     *
     * @param query The query string.
     */
    public void setQuery(String query) {
        this.query = query;
    }

}