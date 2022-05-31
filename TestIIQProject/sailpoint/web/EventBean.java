/**
 * 
 */
package sailpoint.web;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

import sailpoint.object.Attributes;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 */
/**
 * UI bean to hold information about events.
 */
@SuppressWarnings("serial")
public class EventBean implements Serializable {
    
    public enum Type {
        Request,
        ProvisioningRequest;
    }
    
    public static String ACTIVATION = "Activate";
    public static String DEACTIVATION = "Deactivate";

    String _id;
    Date _created;
    String _creator;
    Date _due;
    String _name;
    String _definitionName;
    Attributes<String,Object> _attributes;
    boolean _pendingDelete;
    Type _type;

    public static class EventBeanComparator implements Comparator<EventBean> {

        public int compare(EventBean b1, EventBean b2) { 

            if(b2.getDue()==null && b1.getDue()==null) 
                return 0;
            if(b2.getDue()==null)
                return 1;
            if(b1.getDue()==null)
                return -1;
            
            if(b1.getDue() == b2.getDue())
                return 0;
            
            if(b1.getDue().after(b2.getDue()))
                return 1;
            else
                return -1;
        }
    }
    
    public EventBean() {
    }

    public String getId() {
        if(_id==null)
            _id = Util.uuid();
        return _id;
    }

    public void setId(String s) {
        _id = s;
    }

    public Date getCreated() {
        return _created;
    }

    public void setCreated(Date d) {
        _created = d;
    }

    public String getCreator() {
        return _creator;
    }

    public void setCreator(String s) {
        _creator = s;
    }

    public Date getDue() {
        return _due;
    }

    public void setDue(Date d) {
        _due = d;
    }

    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    public String getDefinitionName() {
        return _definitionName;
    }

    public void setDefinitionName(String s) {
        _definitionName = s;
    }

    /**
     * Prefer the name if we had one, otherwise
     * fallback to the RequestDefinition name.
     */
    public String getSummary() {
        return (_name != null) ? _name : _definitionName;
    }
    
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> _attributes) {
        this._attributes = _attributes;
    }
    
    public Object getAttribute(String key){
        Object attribute = null;
        if(_attributes!=null && _attributes.get(key)!=null)
            attribute = _attributes.get(key);
        
        return attribute;
    }

    public boolean isPendingDelete() {
        return _pendingDelete;
    }
    
    public void setPendingDelete(boolean val) {
        _pendingDelete = val;
    }
    
    public Type getType() {
        return _type;
    }
    
    public void setType(Type val) {
        _type = val;
    }

}