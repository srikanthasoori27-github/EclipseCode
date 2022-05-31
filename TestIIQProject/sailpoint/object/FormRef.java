/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * FormRef object to represent referenced form within a template
 * @author alevi.d'costa
 *
 */
@XMLClass
public class FormRef extends AbstractXmlObject implements FormItem {
    
    /**
     * id of the referred form
     */
    private String _id;
    /**
     * name of the referred form
     */
    private String _name;

    public void load() {
    }
    
    public int getPriority() {
        return 0;
    }

    @XMLProperty(mode=SerializationMode.ATTRIBUTE)
    public void setId(String refId) {
        this._id = refId;
    }

    public String getId() {
        return _id;
    }

    @XMLProperty(mode=SerializationMode.ATTRIBUTE)
    public void setName(String refName) {
        this._name = refName;
    }

    public String getName() {
        return _name;
    }

    public FormRef(){
        _id = null;
    }

    public FormRef(String refId,String refName) {
        this._id = refId;
        this._name = refName;
    }

    public FormRef(FormRef frmRef) {
        _id = frmRef.getId();
    }

    public String getIdOrName() {
        return (_id != null) ? _id : _name;
    }
}
