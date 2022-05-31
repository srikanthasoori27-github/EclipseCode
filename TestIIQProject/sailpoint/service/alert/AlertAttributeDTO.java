/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

/**
 * Created by ryan.pickens on 8/24/16.
 */
public class AlertAttributeDTO {

    String _name;

    String _displayName;

    String _value;

    String _description;

    // True if the attribute name is found in the application's schema
    boolean _foundInSchema;

    // True if this is an extended attribute
    boolean _extendedAttribute;

    public AlertAttributeDTO(String name, Object value) {
        _name = name;
        _value = value.toString();
    }

    public String getName() { return _name; }

    public void setName(String s) { _name = s; }

    public String getDisplayName() { return _displayName; }

    public void setDisplayName(String s) { _displayName = s; }

    public String getValue() { return _value; }

    public void setValue(String s) { _value = s; }

    public String getDescription() { return _description; }

    public void setDescription(String d) { _description = d; }

    public boolean isFoundInSchema() { return _foundInSchema; }

    public void setFoundInSchema(boolean b) { _foundInSchema = b; }

    public void setExtendedAttribute(boolean b) { _extendedAttribute = b; }

    public boolean isExtendedAttribute() { return _extendedAttribute; }

}
