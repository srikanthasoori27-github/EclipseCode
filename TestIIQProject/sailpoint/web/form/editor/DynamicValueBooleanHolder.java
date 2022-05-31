/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.form.editor;

import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.DynamicValue;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Holds the dynamic value that can be displayed in the UI.
 * We pull out the value, ruleName, and scriptSource when displaying
 * and when saving we do the reserver.
 */

public class DynamicValueBooleanHolder {
    //These should exactly match the javascript values
    public static final String TYPE_NONE = FormDTO.TYPE_NONE;
    public static final String TYPE_RULE = FormDTO.TYPE_RULE;
    public static final String TYPE_SCRIPT = FormDTO.TYPE_SCRIPT;

    private String valueType;
    private String value;
    private String ruleName;
    private String scriptSource;

    /**
     * Will create one if dynamicValue != null
     * otherwise will return default value
     * @param dynamicValue
     * @return
     */
    public static DynamicValueBooleanHolder createFromDynamicValueOrDefault(DynamicValue dynamicValue) {

        if (dynamicValue == null) {
            return createDefault();
        }

        DynamicValueBooleanHolder holder = new DynamicValueBooleanHolder();

        if (dynamicValue.getRule() != null && !Util.isNullOrEmpty(dynamicValue.getRule().getName())) {
            holder.setValueType(TYPE_RULE);
            holder.setRuleName(dynamicValue.getRule().getName());
        } else if (dynamicValue.getScript() != null && !Util.isNullOrEmpty(dynamicValue.getScript().getSource())) {
            holder.setValueType(TYPE_SCRIPT);
            holder.setScriptSource(dynamicValue.getScript().getSource());
        } else {
            holder.setValueType(TYPE_NONE);
            holder.setValue(dynamicValue.getValue().toString());
        }

        return holder;
    }

    public static DynamicValueBooleanHolder createFromJsonObjectOrDefault(JSONObject json) throws JSONException {

        if (json == null) {
            return createDefault();
        }

        DynamicValueBooleanHolder holder = new DynamicValueBooleanHolder();

        holder.setValueType(WebUtil.getJSONString(json, "valueType"));
        holder.setRuleName(WebUtil.getJSONString(json, "ruleName"));
        holder.setScriptSource(WebUtil.getJSONString(json, "scriptSource"));

        if (json.has("value") && !json.isNull("value")) {
            Object valueObject = json.get("value");
            if (null != valueObject) {
                if (valueObject instanceof Boolean) {
                    holder.setValue(((Boolean) valueObject).toString());
                } else {
                    holder.setValue(valueObject.toString());
                }
            }
        }

        return holder;
    }

    public static DynamicValueBooleanHolder createDefault() {

        DynamicValueBooleanHolder holder = new DynamicValueBooleanHolder();
        holder.setValueType(TYPE_NONE);
        holder.setValue(Boolean.toString(false));

        return holder;
    }

    public JSONObject convertToJson() throws JSONException {

        JSONObject json = new JSONObject();
        json.put("valueType", getValueType());
        json.put("value", getValue());
        json.put("ruleName", getRuleName());
        json.put("scriptSource", getScriptSource());

        return json;
    }

    public DynamicValue convertToDynamicValue(Resolver resolver) throws GeneralException {

        DynamicValue dynamicValue;

        if (getValueType().equals(TYPE_NONE)) {
            Boolean value = Boolean.FALSE;
            if (!Util.isNullOrEmpty(getValue())) {
                value = Boolean.valueOf(getValue());
            }
            dynamicValue = new DynamicValue(null, null, value);
        } else if (getValueType().equals(TYPE_RULE)) {
            Rule rule = null;
            if (!Util.isNullOrEmpty(getRuleName())) {
                rule = resolver.getObjectByName(Rule.class, getRuleName());
            }
            dynamicValue = new DynamicValue(rule, null, null);
        } else if (getValueType().equals(TYPE_SCRIPT)) {
            dynamicValue = new DynamicValue(null, new Script(getScriptSource()), null);
        } else {
            // should not be here, throw
            throw new IllegalStateException("Unknown type");
        }

        return dynamicValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String val) {
        valueType = val;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String val) {
        ruleName = val;
    }

    public String getScriptSource() {
        return scriptSource;
    }

    public void setScriptSource(String scriptSource) {
        this.scriptSource = scriptSource;
    }
}
