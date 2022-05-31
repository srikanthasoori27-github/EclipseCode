/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.Map;

import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;

/**
 * Interface for retrieving a form renderer for rest services that support
 * dynamic fields.
 * 
 * @author bernie.margolis@sailpoint.com
 */
public interface FormBean {

    /**
     * Return the FormRenderer for the form with the given formId.  Usually the
     * formId can be ignored unless the page for this FormBean supports multiple
     * forms.
     */
    FormRenderer getFormRenderer(String formId) throws GeneralException;

    /**
     * Return the arguments to be passed to Formicator for expansion.
     */
    Map<String,Object> getFormArguments() throws GeneralException;

    /**
     * Return data required to construct this FormBean with a known state.
     * When accessed as a JSF bean, initialization is often done by reading
     * HTTP request or session parameters to determine the object being edited,
     * etc...  However, this FormBean also needs to be constructed outside of
     * a normal JSF request (primarily for the DynamicFieldResource).  Return
     * any data necessary to reconstruct this bean.
     * 
     * When this returns null, this bean must have a constructor that accepts
     * a single Map<String,Object>.
     */
    Map<String,Object> getFormBeanState();
}
