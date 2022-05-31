package sailpoint.web.util;

import java.util.ArrayList;
import java.util.List;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.ConverterException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class IdentityNameListConverter extends BaseObjectListConverter<Identity> {
    
    private static final Log LOG = LogFactory.getLog(IdentityNameListConverter.class);
    /**
     * 
     */
    public IdentityNameListConverter() {
        super(Identity.class);
    }

    @Override
    protected String getObjectName(SailPointObject obj) {

        Identity ident = (Identity) obj;
        return ident.getName();
    }
    
    // implemented this method as base implementation always finds the object by Id
    // With this method it will search identity by Name 
    // value - contains list of identity names
    public Object getAsObject(FacesContext context, UIComponent component, String value)  {
        
        if (null == value) 
            return null;
        
        List<SailPointObject> objectList = new ArrayList<SailPointObject>();
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            for (String name : (List<String>) Util.stringToList(value)) {
                SailPointObject obj = ctx.getObjectByName(scope, name);
                if (obj != null) {
                    objectList.add(obj);
                }
                else if (this.allowTransientObjects) {
                    objectList.add(createObject(name));
                }
                else {
                    LOG.warn("Object could not be loaded and allowTransientObjects is false: " +
                             this.scope + " - " + name);
                }
            }
        } catch (GeneralException e) {
            throw new ConverterException(e);
        }
        return objectList;
    }

}
