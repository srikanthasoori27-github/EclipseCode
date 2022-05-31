package sailpoint.web;
/*
 * This class is used to represent the mapping of the schema attribute
 * from the previous application to the new application in application reconfiguration task
 */
public class ApplicationReconfigMapping {

    // Represents previous application schema attribute name
    private String _oldApplicationAttribute;

    // Represents new application schema attribute name
    private String _newApplicationAttribute;

    // Represents the type i.e. account Or group
    private String _type;
    
    public String getType() {
        return _type;
    }

    public void setType( String _type ) {
        this._type = _type;
    }

    public String getOldApplicationAttribute() {
        return _oldApplicationAttribute;
    }

    public void setOldApplicationAttribute( String _oldAppAccountAttribute ) {
        this._oldApplicationAttribute = _oldAppAccountAttribute;
    }

    public String getNewApplicationAttribute() {
        return _newApplicationAttribute;
    }

    public void setNewApplicationAttribute( String _newAppAccountAttribute ) {
        this._newApplicationAttribute = _newAppAccountAttribute;
    }

}
