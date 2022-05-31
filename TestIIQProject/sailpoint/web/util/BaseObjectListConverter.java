/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.List;

import javax.faces.component.StateHolder;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A base implementation of an object list converter.  This converts a list of
 * SailPointObject IDs to a list of object names.
 * 
 * TODO: Consider making a generic object list converter that takes the object
 * type as a parameter.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseObjectListConverter<T extends SailPointObject>
    implements Converter, StateHolder {

    private static final Log LOG = LogFactory.getLog(BaseObjectListConverter.class);
    
    protected Class<? extends SailPointObject> scope;
    protected boolean allowTransientObjects;


    /**
     * Default constructor.
     */
    public BaseObjectListConverter(Class<? extends SailPointObject> scope) {
        this.scope = scope;
    }

    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent component, String value)  {
    	
    	if (null == value) 
    		return null;
    	
    	List<SailPointObject> objectList = new ArrayList<SailPointObject>();
		try {
			SailPointContext ctx = SailPointFactory.getCurrentContext();
			for (String id : (List<String>) Util.stringToList(value)) {
				SailPointObject obj = ctx.getObjectById(scope, id);
                if (obj!=null) {
                    objectList.add(obj);
                }
                else if (this.allowTransientObjects) {
                    objectList.add(createObject(id));
                }
                else {
                    LOG.warn("Object could not be loaded and allowTransientObjects is false: " +
                             this.scope + " - " + id);
                }
			}
		} catch (GeneralException e) {
			throw new ConverterException(e);
		}
    	return objectList;
    }

    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context,
                              UIComponent component,
                              Object value) {

        List<String> objectIds = new ArrayList<String>();
        if(value!=null) {
            List<SailPointObject> objects = (List<SailPointObject>) value;
            if(objects!=null) {
                for(SailPointObject obj: objects) {
                	// sometimes the value contains a single null element - why?
                	if (null != obj) {
                	    String val = obj.getId();

                	    // If the ID is null, just use the name if we're allowing
                	    // transient objects.
                	    if (null == val) {
                	        if (this.allowTransientObjects) {
                	            val = obj.getName();
                	        }
                	        else {
                	            throw new ConverterException("Object does not have an ID and allowTransientObjects is false"); 
                	        }
                	    }

                	    objectIds.add(val);
                	}
                }
            }
        }

        return objectIds.toString();
    }

    /**
     * Subclasses should override to create an object with a name if they want
     * to support allowTransientObjects.
     * 
     * @param  name  The name from the list.
     * 
     * @return A newly created transient object with the given name.
     */
    protected SailPointObject createObject(String name) throws GeneralException {
        throw new GeneralException("createObject() is not implemented");
    }
    
    /**
     * Return the "name" of the object to display.  This can be overridden by
     * subclasses to show a more friendly name.  This value is only used for
     * display, and is not used when restoring the value.
     */
    protected String getObjectName(SailPointObject obj) {
        return obj.getName();
    }

    protected String getObjectId(SailPointObject obj) {
        return obj.getId();
    }

    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#isTransient()
     */
    public boolean isTransient() {
        return false;
    }

    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#restoreState(javax.faces.context.FacesContext, java.lang.Object)
     */
    public void restoreState(FacesContext context, Object state) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#saveState(javax.faces.context.FacesContext)
     */
    public Object saveState(FacesContext context) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#setTransient(boolean)
     */
    public void setTransient(boolean newTransientValue) {
        // TODO Auto-generated method stub

    }
}
