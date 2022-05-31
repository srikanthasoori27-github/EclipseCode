/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter.holcomb
 *
 */
public class AttrSelectBean extends BaseBean implements Comparable<AttrSelectBean> {
    public static final String DESCRIPTION_FIELD = "description";
    
    private boolean _selected;
    private boolean _multiValue;
    private String _name;
    private String _value;
    private String _displayValue;
    private String _application;
    
    /*An extra map to store any other fields that may be needed to properly display this
     * object on the ui.  Put here because I needed to display more than just a _name to
     * the UI. - PH
     */
    private Map<String, String> _extraFields;
    /*
     * This is a hack to work around the lack of a counter in the <ui:repeat> tag.
     * The ModelerBean will set this when it returns the Collection.
     */
    private boolean _isOdd;

    public AttrSelectBean(String name, String value, String displayValue, String application, boolean multiValue) {
        super();
        _name = name;
        _value = value;
        _displayValue = displayValue;
        _application = application;
        _multiValue = multiValue;
    }
    
    public AttrSelectBean(String name, String value, String displayValue, String application, boolean multiValue, String description) {
        this(name, value, displayValue, application, multiValue);
        if (description != null) {
            _extraFields = new HashMap<String, String>();
            _extraFields.put(DESCRIPTION_FIELD, description);
        }
    }

    public boolean isSelected() { return _selected; }
    public void setSelected(boolean selected) { _selected = selected; }

    public String getName() { return _name; }
    public void setName(String name) { _name = name; }

    public String getValue() { return _value; }
    public void setValue(String value) { _value = value; }
    
    public String getDisplayValue() {return _displayValue;}
    public void setDisplayValue(String displayValue) { _displayValue = displayValue; }
    
    public String getApplication() { return _application; }
    public void setApplication(String application) { _application = application; }
    
    public boolean isMultiValue() {return _multiValue;}
    public void setMultiValue(boolean value) {_multiValue = value;}

    public String getFieldName(){
        return (_multiValue ? "mv_" : "") + _name;
    }

    public void setFieldName(String fieldName){

        if (fieldName.indexOf("mv_") == 0){
            _multiValue=true;
            _name = fieldName.substring(3);
        }else{
            _multiValue=false;
            _name = fieldName;
        }
    }

    public int compareTo(AttrSelectBean o) {
        int cmp = _name.compareTo(o.getName());
        if ( cmp != 0 ) return cmp;

        return _value.compareTo(o.getValue());
    }

    public String toString() {
        return _selected + ":" + _name + ":" + _value;
    }

    public boolean isOdd() {
        return _isOdd;
    }

    public void setOdd(boolean odd) {
        _isOdd = odd;
    }

    /**
     * @return the _extraFields
     */
    public Map<String, String> getExtraFields() {
        return _extraFields;
    }

    /**
     * @param fields the _extraFields to set
     */
    public void setExtraFields(Map<String, String> fields) {
        _extraFields = fields;
    }
}
