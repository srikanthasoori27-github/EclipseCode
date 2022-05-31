/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Reference;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;


/**
 * JSF converter to convert SailPointObjects and/or References to and from strings.
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class SailPointObjectConverter implements Converter
{
    private static final long serialVersionUID = -7897322520968766525L;

    private boolean referenceByName;
    
    public SailPointObjectConverter() {
        referenceByName = false;
    }
    
    /*
     * (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    @SuppressWarnings("unchecked")
    public Object getAsObject(FacesContext context, UIComponent comp, String value)
        throws ConverterException
    {
        Object retval;
        Class objType = comp.getValueBinding("value").getType(context);

        if (value == null || 0 == value.trim().length()) {
            retval = null;
        } else {
            if (SailPointObject.class.isAssignableFrom(objType) || Reference.class.isAssignableFrom(objType)) {
                try {
                    SailPointContext spContext = SailPointFactory.getCurrentContext();
                    
                    if (referenceByName) {
                        retval = spContext.getObjectByName(objType, value);                        
                    } else {
                        retval = spContext.getObjectById(objType, value);
                    }
                    
                    // Convert to a reference if necessary
                    if (retval != null && Reference.class.isAssignableFrom(objType)) {
                        retval = new Reference((SailPointObject) retval);
                    }
                } catch (GeneralException e) {
                    throw new ConverterException("The SailPointObjectConverter was unable to retrieve the " + objType.getName() + " named " + value + " right now.", e);
                }
            } else {
                throw new ConverterException("The SailPointObjectConverter does not support objects of type " + objType.getName() + ".");
            }
        } 
        
        if (retval == null) {
            try {
                retval = objType.getConstructor((Class[]) null).newInstance((Object[]) null);
            } catch (Exception e) {
                throw new ConverterException("Unable to create a new object of type " + objType.getName());
            }
        }
        
        return retval;
    }

    /*
     * (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context, UIComponent component, Object object)
        throws ConverterException
    {        
        if (null == object)
            return "";
        
        if (object instanceof SailPointObject) {
            String retval;
            
            if (referenceByName) {
                retval = ((SailPointObject) object).getName();
                
                if (retval == null) {
                    retval = "";
                }
            } else {
                retval = ((SailPointObject) object).getId();
                if (retval == null) {
                    retval = "";
                }
            }
            
            return retval;
        } else if (object instanceof Reference) {
            String retval;
            
            if (referenceByName) {
                retval = ((Reference) object).getName();
                
                if (retval == null) {
                    retval = "";
                }
            } else {
                retval = ((Reference) object).getId();
                if (retval == null) {
                    retval = "";
                }
            }
            
            return retval;
        } else {
            throw new ConverterException("The SailPointObjectConverter does not support objects of type " + object.getClass().getName());
        }
    }

    /**
     * Specifies whether the object is being referenced by name or by ID.
     * @return true if the object is being referenced by name; 
     *         false if it's being referenced by ID
     */
    public boolean isReferenceByName() {
        return referenceByName;
    }

    /**
     * Specify whether the object is being referenced by name or by ID.
     * @param referenceByName true if the object is being referenced by name; 
     *                        false if it's being referenced by ID
     */
    public void setReferenceByName(boolean referenceByName) {
        this.referenceByName = referenceByName;
    }
}
