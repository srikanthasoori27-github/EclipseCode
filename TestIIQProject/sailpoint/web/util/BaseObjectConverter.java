/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import javax.faces.component.StateHolder;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

/**
 * A base implementation of an object converter. This converts a SailPointObject
 * to to its id.
 * 
 * This originally was written by Peter to convert from object to name, not id.
 * However, it wasn't in use, so I hijacked it for id purposes. DC
 * 
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public abstract class BaseObjectConverter<T extends SailPointObject> implements
        Converter, StateHolder {
    protected Class<? extends SailPointObject> scope;

    /**
     * Default constructor.
     */
    public BaseObjectConverter(Class<? extends SailPointObject> scope) {
        this.scope = scope;
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
        Object o = null;

        if (null != value) {
            try {
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                o = ctx.getObjectById(scope, value);
            } catch (GeneralException ge) {
                throw new ConverterException(ge);
            }
        }

        return o;
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
        String id = null;

        if (null != value)
            id = ((SailPointObject) value).getId();

        return id;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.faces.component.StateHolder#restoreState(javax.faces.context.
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
