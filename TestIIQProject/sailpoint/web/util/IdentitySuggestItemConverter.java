/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import javax.faces.component.StateHolder;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentitySuggestItem;

public class IdentitySuggestItemConverter implements Converter, StateHolder {

    public IdentitySuggestItemConverter() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext
     * , javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent component,
            String value) {

        if (Util.isNullOrEmpty(value)) {
            return null;
        }

        try {
            return createFromIdentity(SailPointFactory.getCurrentContext()
                    .getObjectById(Identity.class, value));
        } catch (GeneralException ge) {
            throw new ConverterException(ge);
        }
    }
    
    public static IdentitySuggestItem createFromIdentity(Identity identity) {
        
        IdentitySuggestItem item = new IdentitySuggestItem();
        
        item.setId(identity.getId());
        item.setName(identity.getName());
        item.setDisplayName(identity.getDisplayName());
        
        return item;
    }
    

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext
     * , javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context, UIComponent component,
            Object value) {
        
        if (value == null) {
            return null;
        }
        
        return ((IdentitySuggestItem) value).getId();
    }

    /*
     * (non-Javadoc)
     * 
     * @seejavax.faces.component.StateHolder#restoreState(javax.faces.context.
     * FacesContext, java.lang.Object)
     */
    public void restoreState(FacesContext context, Object state) {
        // not needed
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.faces.component.StateHolder#saveState(javax.faces.context.FacesContext
     * )
     */
    public Object saveState(FacesContext context) {
        return null; // not needed
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.faces.component.StateHolder#isTransient()
     */
    public boolean isTransient() {
        return false; // not needed
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.faces.component.StateHolder#setTransient(boolean)
     */
    public void setTransient(boolean newTransientValue) {
        // not needed
    }
}
