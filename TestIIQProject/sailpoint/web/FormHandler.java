/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.transformer.Transformer;
import sailpoint.transformer.TransformerFactory;


/**
 * All pages that display forms need to go through a common lifecycle to create
 * the form, display it, and appropriately handle form submissions and
 * refreshes - the FormHandler should be used to facilitate this lifecycle.
 * 
 * At a high level, pages that display forms operate by creating a "master"
 * form that will hold the entire un-expanded form, converting this to an
 * expanded form that is rendered in the page and captures the POST data on
 * submission, copying this data back into the master form and data model
 * from the expanded form, and (if not using a form model) copying the data
 * back out of the master form into the object being edited or created.
 *
 * More specifically, pages that display forms should work like this:
 * 
 * Initialization:
 *  - Get the master form (either by building it or loading it from the
 *    database) and clone it.
 *  - Optionally populate the fields in the form with the initial values from
 *    the object that you are editing.  This is only required if you do not
 *    have a model you are binding into the form.
 *  - Call FormHandler.initializeForm() to populate the master with data from
 *    the model, create an expanded form, and appropriately store the forms for
 *    future use.
 *  - Construct a FormRenderer using the expanded form.  This will be used
 *    by the JSF page to render the form and capture POST data.
 * 
 * Rendering:
 *  - Include formRenderer.xhtml in the JSF page to render the expanded form
 *    in the FormRenderer.
 *  - The form is submitted by either clicking a button or via a postback.
 *    This packages the form data and information about the action that
 *    occurred, and submits these into the FormRenderer.
 *  - A JSF command button is clicked by the client.
 * 
 * Submit Handling:
 *  - A JSF action method attached to the command button is called.  This
 *    should retrieve the "action" from the FormRenderer and perform different
 *    behavior based on the action.
 *  - If the action is "refresh", call FormHandler.refresh().  This copies the
 *    data from the expanded form back into the master and the data model, and
 *    resets the expanded form so it will get recalculated when rendered again.
 *    The action method should return null to display the same page.
 *  - If the action is a submit (eg - "next"), call FormHandler.submit().  This
 *    performs validation, expands the dynamic values one last time, and copies
 *    the data from the expanded form back into the master form and data model.
 *    The action method can optionally convert the master form data back into
 *    the object being edited/created (or use the data model), and persist the
 *    data.
 *  - If the action is "cancel" or unknown, the action method should clear the
 *    FormStore and return.
 *
 * @author Kelly Grizzle
 */
public class FormHandler {

    private static final Log log = LogFactory.getLog(FormHandler.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A FormStore is responsible for maintaining Forms between requests.  This
     * is commonly implemented by storing the forms on the HTTP session, but
     * use of the session is not mandatory (eg - WorkItemFormBean keeps the
     * master form in the WorkItem).
     */
    public static interface FormStore {
        
        /**
         * Store the given master form for later retrieval.
         */
        public void storeMasterForm(Form form);

        /**
         * Retrieve the master form from the store.
         */
        public Form retrieveMasterForm() throws GeneralException;

        /**
         * Clear the master form from the store.
         */
        public void clearMasterForm();


        /**
         * Store the given expanded form for later retrieval.
         */
        public void storeExpandedForm(Form form);

        /**
         * Retrieve the expanded form from the store.
         */
        public Form retrieveExpandedForm() throws GeneralException;

        /**
         * Clear the expanded form from the store AND null out the FormRenderer
         * so the expanded form will be regenerated.
         */
        public void clearExpandedForm();
    }
    
    /**
     * Types of Forms
     *
     */
    public static enum FormTypes {
        MASTER,
        EXPANDED
    }
    
       
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private SailPointContext context;
    private FormStore store;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     * 
     * @param  context  The SailPointContext to use.
     * @param  store    The FormStore to use to save and retrieve forms.  This
     *                  is optional, but highly recommended.  If not supplied,
     *                  the caller is responsible for doing the right thing
     *                  with its forms.
     */
    public FormHandler(SailPointContext context, FormStore store) {
        this.context = context;
        this.store = store;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Initial form building
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Given a master form, populate it with data and expand it.  If any master
     * fields are be pre-populated with values, these will not get overwritten
     * during population.  Previous values are captured back into the master.
     * This adds the master and expanded forms to the FormStore.
     * 
     * @param  master    The master Form, which is not yet expanded and may
     *                   contain pre-populated Field values.
     * @param  populate  Whether the form should be populated with values from
     *                   the data map.  If false, it is assumed that the caller
     *                   has already populated the form.
     * @param  data      The data to populate the form with and use for
     *                   arguments during expansion.
     * 
     * @return The expanded form.  Note that the master and data may be updated
     *         as a side-effect.
     */
    public Form initializeForm(Form master, boolean populate,
                               Map<String,Object> data)
        throws GeneralException {
       
        // Forms need to have stable IDs.  If the master doesn't have one,
        // assign it now.
        assignFormId(master);

        // Make sure that the master is populated with values from the
        // arguments before we expand.  This allows the caller to pass in a
        // form that is not yet populated and have it automatically get data
        // injected into it.
        Formicator formicator = new Formicator(this.context);
        formicator.initialize(master, data, populate);
        
        // Expand the master form to create the expanded form.
        Form form = expandForm(master, data);
        
        // Store the expanded and master forms so they can be used later.
        if (null != this.store) {
            this.store.storeExpandedForm(form);
            this.store.storeMasterForm(master);
        }
        
        return form;
    }

    /**
     * If the given Form doesn't have an ID, assign a unique ID.
     */
    private void assignFormId(Form form) {
        if (form.getId() == null) {
            String id = java.util.UUID.randomUUID().toString();
            id = id.replaceAll("-", "");
            form.setId(id);
        }
    }
    
    /**
     * Expand the given master form with the optional expansion arguments.
     */
    private Form expandForm(Form master, Map<String,Object> data)
        throws GeneralException {
        
        Formicator cator = new Formicator(this.context);
        Form form = cator.expand(master, data);

        // Clean out some of our temporary state from the master form now that
        // it has been pushed into the expanded form.
        cleanMaster(master);
        
        return form;
    }

    /**
     * Clean some temporary state out of the master form.
     */
    private void cleanMaster(Form master) {
        // See refresh() for why currentField is in here.
        if (null != master.getAttributes()) {
            master.getAttributes().remove(FormRenderer.ATT_CURRENT_FIELD);

            // Keep it clean.
            if (master.getAttributes().isEmpty()) {
                master.setAttributes(null);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Form refresh and submission
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Refresh the form on a "refresh" or "postBack".  This populates the
     * expanded form with the POSTed data, copies the values from the expanded
     * form to the master form and form model, and then reinitialises the formBean
     * with the expanded dataset
     *  
     * @param  master    The master Form to copy the values into.
     * @param  formBean  The FormRenderer with the expanded form and post data.
     * @param  data      The data to use for arguments during expansion.  These
     *                   may get updated as a result of expansion.
     */
    public void refresh(Form master, FormRenderer formBean,
                        Map<String,Object> data)
        throws GeneralException {
        
        // Save this - we'll need to set it on th new FormRenderer when rebuilt.
        String currentField = formBean.getCurrentField();
        
        // Assimilate the values from the form POST into the expanded.
        formBean.populateForm();
        
        // Assimilate the edits back into the master form.  Note that for
        // model-backed forms, the model should contain all of the data.
        // This is really only needed for forms that don't use models.
        assimilate(master, formBean.getForm(), data);
        
        // If the form is backed by a model with a transformer, let the
        // transformer refresh the model also.
        refreshTransformer(master, data);
        
        // This is a little hacky.  I considered using the FormStore to hold the
        // current field in a generic map, but the caller would have to
        // implement this on the FormStore and then get this state passed into
        // the FormRenderer constructor.  Decided to make things easier on the
        // caller for now.  If we start to need to pass more data, we'll
        // re-evaluate the strategy here.
        if (!Util.isNullOrEmpty(currentField)) {
            master.put(FormRenderer.ATT_CURRENT_FIELD, currentField);
            master.put(FormRenderer.ATT_TAB_DIRECTION, formBean.getTabDir());
        }
        
        //Need to reevaluate the expanded form based on the master form
        //Go ahead and populate the expanded form while we're here
        formBean.init(this.initializeForm(master, true, data));
    }

    /**
     * Perform the final submission of the form.  This populates the expanded
     * form with the POSTed data, expands dynamic values, and runs validation.
     * If validation is successful, the values are copied from the expanded form
     * to the master form and form model, and then the expanded form is cleared
     * since it is no longer needed.  After this, the caller can copy the data
     * out of the master form back into the object being edited (if relevant).
     *  
     * @param  master    The master Form to copy the values into.
     * @param  formBean  The FormRenderer with the expanded form and post data.
     * @param  data      The optional data to use to expand dynamic values.
     *                   Note that this may be updated during expansion.
     *
     * @return True if validation was successful or false otherwise.
     */
    public boolean submit(Form master, FormRenderer formBean,
                          Map<String,Object> data)
        throws GeneralException {

        return submit(master, formBean, data, false);
    }
    
    /**
     * Perform the final submission of the form, going back to the previous
     * form in a workflow.  This is the same as submit() except it does not
     * validate, the changes are assimilated regardless of validation.
     *  
     * @param  master    The master Form to copy the values into.
     * @param  formBean  The FormRenderer with the expanded form and post data.
     * @param  data      The optional data to use to expand dynamic values.
     *                   Note that this may be updated during expansion.
     */
    public void back(Form master, FormRenderer formBean,
                     Map<String,Object> data)
        throws GeneralException {

        submit(master, formBean, data, true);
    }

    /**
     * Submit the form, possibly ignoring validation errors.
     */
    private boolean submit(Form master, FormRenderer formBean,
                           Map<String,Object> data, boolean skipValidation)
        throws GeneralException {
        
        boolean valid = false;

        // Assimilate the posted form data
        formBean.populateForm();

        // Do final expansion of dynamic value scripts prior to validation.
        expandDynamicValues(master, formBean.getForm(), data);

        Form.Button b = formBean.getForm().getClickedButton();
        boolean buttonSkip = false;

        if(b != null && b.getSkipValidation()) {
            buttonSkip = true;
            valid = true;
        }
        // must validate first only if clicked button doesn't want to skip validation
        if(!buttonSkip) {
            valid = formBean.validate(data);
        }
        
        // If valid, assimilate the edits back into the master form.
        if ((skipValidation || valid)) {
            assimilate(master, formBean.getForm(), data);

            // Clear the expanded form out of the store since we are
            // done with it.
            if (null != this.store) {
                this.store.clearExpandedForm();
            }
        }

        return valid;
    }

    /**
     * Transfer field values from the form that was last rendered into 
     * the WorkItem form and the form data.  We only transfer the values so
     * that unexpanded variable references or scripts remain in the WorkItem
     * form and can be re-expanded in the context of the new values.
     * 
     * Note that the master form shouldn't need the values if using a
     * model-backed form.  In this case, the model is the authoritative
     * source of the values.
     */
    private void assimilate(Form master, Form expanded,
                            Map<String,Object> data)
        throws GeneralException {
        
        // Callers can either use the master form or the data map to store
        // values.  Copy the values from the expanded form to the master form
        // in case the caller is not using the data map.
        if ((master != null) && (expanded != null)) {
            Iterator<Field> it = expanded.iterateFields();
            while (it.hasNext()) {
                Field src = it.next();
                String name = src.getName();
                if (name != null) {
                    Object value = src.getValue();

                    Field dest = master.getField(src.getName());
                    if (dest != null)
                        dest.setValue(value);
                    else {
                        log.warn("Unmatched form field: " + name);
                    }
                }
            }

            if (expanded.getButtons() != null){
                for(Form.Button expandedButton : expanded.getButtons()){
                    Form.Button masterButton = master.getMatchingButton(expandedButton);
                    if (masterButton != null)
                        masterButton.setClicked(expandedButton.isClicked());
                    else
                        log.warn("Unmatched button with action=" + expandedButton.getAction());
                }
            }
        }

        // Copy values out of the form into the data model.
        Formicator formicator = new Formicator(this.context);
        formicator.copyFormToData(expanded, data);
    }

    /**
     * If this is a model-backed form with a transformer, re-expand the form to
     * get any updated model values and then let the transformer refresh the
     * model.
     * 
     * @param  master  The master form.
     * @param  data    The data map that may contain the model.
     */
    @SuppressWarnings("unchecked")
    private void refreshTransformer(Form master, Map<String,Object> data)
        throws GeneralException {
        
        // Only do this if we are dealing with a model.
        if (master.hasBasePath()) {
            Map<String,Object> model =
                (Map<String,Object>) data.get(master.getBasePath());
            if (null != model) {
                // Expand before we let the transformer do a refresh.  This
                // will ensure that any values updated by expansion get stuck
                // into the model.
                Form expanded = expandForm(master, data);
                
                // Make sure to capture any expansions back into the master.
                // This is important because this expanded form will never get
                // rendered and posted back.
                assimilate(master, expanded, data);

                // Let the transformer refresh the model.
                Transformer<?> t =
                    TransformerFactory.getTransformer(this.context, model);
                t.refresh(model);
                
                // The updated model will get shoved back into the master form
                // when we re-initialize on next render.
            }
        }
    }
    
    /**
     * Do one final expansion pass for dynamic values when the form is posted.
     */
    private void expandDynamicValues(Form master, Form expanded,
                                     Map<String,Object> data)
        throws GeneralException {

        Formicator cator = new Formicator(this.context);
        cator.expandDynamicValues(master, expanded, data);
    }
    
}
