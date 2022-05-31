/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import sailpoint.api.DynamicValuator;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.object.SPRight;
import sailpoint.object.Form.Section;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FormJsonUtil;

/**
 * A list resource for Forms.
 * 
 * Currently, this just returns a list of the static button types to the combobox on the form button editor.
 * 
 * URI : /form/...
 *
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
@Path("form")
public class FormResource extends BaseResource {
    private static Log log = LogFactory.getLog(FormResource.class);
    
    @POST
    @Path("buttons")
    public ListResult getButtons(@FormParam("actionsToExclude") List<String> actionsToExclude) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessWorkflows, SPRight.FullAccessForms));
    	
    	Set<String> setOfActionsToExclude = new HashSet<String>(actionsToExclude);
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        if (!setOfActionsToExclude.contains(Form.ACTION_CANCEL)) {
            Map<String, Object> row = new HashMap<String,Object>();
            row.put("name", localize(MessageKeys.BUTTON_CANCEL));
            row.put("value", Form.ACTION_CANCEL);
            rows.add(row);
        }
        
        if (!setOfActionsToExclude.contains(Form.ACTION_BACK)) {
            Map<String, Object> row1 = new HashMap<String,Object>();
            row1.put("name", localize(MessageKeys.BUTTON_BACK));
            row1.put("value", Form.ACTION_BACK);
            rows.add(row1);
        }
        
        
        if (!setOfActionsToExclude.contains(Form.ACTION_REFRESH)) {
            Map<String, Object> row2 = new HashMap<String,Object>();
            row2.put("name", localize(MessageKeys.BUTTON_REFRESH));
            row2.put("value", Form.ACTION_REFRESH);
            rows.add(row2);
        }
        
        if (!setOfActionsToExclude.contains(Form.ACTION_NEXT)) {
            Map<String, Object> row3 = new HashMap<String,Object>();
            row3.put("name", localize(MessageKeys.BUTTON_NEXT));
            row3.put("value", Form.ACTION_NEXT);
            rows.add(row3);
        }
        
        ListResult result = new ListResult(rows, rows.size());
        return result;
    }

    /**
     * Get the Form Types.
     *
     * Currently only Application, Role and Workflow Type are supported for creating forms.
     *
     * @return The forms list result.
     */
    @GET
    @Path("formTypes")
    public ListResult getFormTypes() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessForms));

        EnumSet<Form.Type> formTypeEnumSet = EnumSet.of(Form.Type.Application,
            Form.Type.Role, Form.Type.Workflow);

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Form.Type formType : formTypeEnumSet) {
            Map<String, Object> row = new HashMap<String, Object>();
            String type = formType.toString();
            if (type.equals(Form.Type.Application.toString())) {
                row.put("name", localize(Configuration.APPLICATION_POLICY_FORM));
            } else if (type.equals(Form.Type.Role.toString())) {
                row.put("name", localize(Configuration.ROLE_POLICY_FORM));
            } else if (type.equals(Form.Type.Workflow.toString())) {
                row.put("name", localize(Configuration.WORKFLOW_FORM));
            }
            row.put("value", type);
            rows.add(row);
        }

        ListResult result = new ListResult(rows, rows.size());

        return result;
    }

    /**
     * Create a form of type Application, Role and Workflow
     * 
     * @return The request result.
    */
    @POST
    public RequestResult createForm(@FormParam("json") String formJSON)
        throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessForms));

        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        try {
            FormDTO formDTO = updateFormFromJson(formJSON);
            Form form = getContext().getObjectByName(Form.class, formDTO.getName());
            if(form != null) {
                ArrayList<String> errors = new ArrayList<String>(1);
                errors.add(localize(MessageKeys.FORM_DUPLICATE_ERROR, formDTO.getName()));
                result.setErrors(errors);
            } else {
                form = new Form();
                // Save Form
                saveForm (form, formDTO);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
        } catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage(), ex);
            }
            ArrayList<String> errors = new ArrayList<String>(1);
            errors.add(ex.getLocalizedMessage());
            result.setErrors(errors);
        } catch (JSONException jsoe) {
            if (log.isWarnEnabled())
                log.warn("Exception during form commit: " + jsoe.getMessage(), jsoe);
        }

        return result;
    }

    /**
    * Retrieve the Form by ID from database, form an equivalent JSON and return  it.
    * Return a JSON String that include all form fields.
    */
    @GET
    @Path("{formId}")
    public String getForm(@PathParam("formId") String formId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessForms));

        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String results;

        try {
            jsonWriter.object();
            jsonWriter.key("isAvailable");

            Form form = getContext().getObjectById(Form.class, formId);
            if (form != null) {
                jsonWriter.value(true);
                Map<String,Object> formMap = FormJsonUtil.convertFormToJSON(getContext(), form, true);

                jsonWriter.key("form");
                jsonWriter.value(new JSONObject(formMap));
            } else {
                jsonWriter.value(false);
            }

            jsonWriter.endObject();
            results = jsonString.toString();
        } catch (GeneralException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to load Form " +  e.getMessage(), e);
            }
            results = "{isAvailable: false}";
        } catch (JSONException jsoe) {
            if (log.isErrorEnabled()) {
                log.error("Failed to load Form " + jsoe.getMessage(), jsoe);
            }
            results = "{isAvailable: false}";
        }

        return results;
    }

    /** 
     * Updates a form of type Application, Role and Workflow 
     * @throws JSONException 
     * @throws GeneralException 
     */
   @PUT
   @Path("{formId}")
   public RequestResult updateForm(@PathParam("formId") String formId, @FormParam("json") String formJSON)
       throws GeneralException {
       authorize(new RightAuthorizer(SPRight.FullAccessForms));

       RequestResult result = new RequestResult();
       result.setStatus(RequestResult.STATUS_FAILURE);

       try {
           FormDTO formDTO = updateFormFromJson(formJSON);

           Form form = getContext().getObjectByName(Form.class, formDTO.getName());
           if (form != null && !Util.nullSafeEq(form.getId(), formId)) {
               ArrayList<String> errors = new ArrayList<String>(1);
               errors.add(localize(MessageKeys.FORM_DUPLICATE_ERROR, formDTO.getName()));
               result.setErrors(errors);
           } else {
               form = getContext().getObjectById(Form.class, formId);
               if (form != null) {
                   // Save Form
                   saveForm(form, formDTO);
                   result.setStatus(RequestResult.STATUS_SUCCESS);
               } else {
                   ArrayList<String> errors = new ArrayList<String>(1);
                   errors.add(localize(MessageKeys.FORM_EDIT_ERROR, formDTO.getName()));
                   result.setErrors(errors);
               }
           }
       } catch (GeneralException ex) {
           if (log.isErrorEnabled()) {
               log.error(ex.getMessage(), ex);
           }
           ArrayList<String> errors = new ArrayList<String>(1);
           errors.add(ex.getLocalizedMessage());
           result.setErrors(errors);
       } catch (JSONException jsoe) {
           if (log.isWarnEnabled())
               log.warn("Exception during form commit: " + jsoe.getMessage(), jsoe);
       }

       return result;
   }

   /**
    * Return a FormDTO for preview form.
    *
    * @param formJSON - The JSON of the form to be previewed
    * @return FormDTO - A DTO of a preview form
    */
   @POST
   @Path("preview")
   public sailpoint.service.form.renderer.FormDTO getPreviewForm(Map<String, Object> formMap)
       throws GeneralException, JSONException {
       authorize(new RightAuthorizer(SPRight.FullAccessForms));

       sailpoint.service.form.renderer.FormDTO previewDTO = null;

       String formJSON = Util.getString(formMap, "formJSON");

       if (Util.isNullOrEmpty(formJSON)) {
           throw new GeneralException("Exception during form preview : formJSON is empty");
       }

       FormDTO formDTO = updateFormFromJson(formJSON);
       Form form = formDTO.commit(new Form());

       // Expand form field without evaluating rule/script
       formicate(form.getItems(), form.isWizard());

       // Deliberately setting form's readOnly property to false to keep
       // button visible in form preview.
       // Because FormRenderer.decorateDTO() skips buttons for readOnly forms,
       // so setting it to false before renderer
       form.setReadOnly(false);

       FormRenderer formRenderer = new FormRenderer(form,
               null,
               getLocale(),
               getContext(),
               getUserTimeZone());
       previewDTO = formRenderer.createDTO();
       // Make preview form as readOnly to disable all angular components.
       // So script sources are not displayed and postbacks calls are avoided.
       previewDTO.setReadOnly(true);

       return previewDTO;
   }

   /**
    * Renderer takes care of this in Formicator.expandField(),
    * but since we do not want to evaluate rule/script,
    * we are handling specific expansion here.
    * 1 - Remove hidden fields
    * 2 - Set required field to false for wizard form
    * 3 - Set allowed values using allowed values dynamic definition
    * 4 - Remove any scriplet value present in field
    * 5 - Remove empty sections
    */
   private void formicate(List<FormItem> items, boolean isWizard) {
       if (items != null) {
           ListIterator<FormItem> it = items.listIterator();
           while (it.hasNext()) {
               FormItem item = it.next();
               if (item instanceof Field) {

                   Field field = (Field)item;

                   // Check for hidden fields and remove them from form
                   if(field.getBooleanAttribute(Field.ATTR_HIDDEN)) {
                       it.remove();
                       continue;
                   }

                   // Set field required to false when form's isWizard is true
                   // This helps to avoid interruption of required field and allow user to
                   // traverse through whole form in wizard mode.
                   if(field.isRequired() && isWizard) {
                       field.setRequired(false);
                   }

                   // get allowed values
                   DynamicValue dv = field.getAllowedValuesDefinition();
                   if (dv != null) {
                       Object value = dv.getValue();

                       // null check for value
                       if (value != null) {
                           List<Object> list = new ArrayList<Object>();
                           if (value instanceof Collection) {
                               list.addAll((Collection) value);
                           } else if (value instanceof String) {
                               list.add(value.toString());
                           }
                           field.setAllowedValues(list);
                       }
                   }

                   // Remove any scriplet value present in field
                   Object value = field.getValue();
                   if (value instanceof String) {
                       String stringVal = (String) value;
                       if (DynamicValuator.isScriptlet(stringVal)) {
                           field.setValue(null);
                       }
                   }
               } else if (item instanceof Section) {
                   Section s = (Section)item;
                   formicate(s.getItems(), isWizard);
                   if (Util.isEmpty(s.getItems())) {
                       it.remove();
                   }
               }
           }
       }
   }

   /** 
    * Update Form Data from JSON
    * @throws JSONException
    * @throws GeneralException 
    */
   private FormDTO updateFormFromJson (String formJSONString)
       throws GeneralException, JSONException {
       JSONObject formJSON = new JSONObject(formJSONString);
       SailPointContext ctx = getContext();

       FormDTO formDTO = new FormDTO();
       // Update Form type from JSON
       FormJsonUtil.updateTypeFromJSON(formDTO, formJSON);
       // Update FormDTO with JSON data
       FormJsonUtil.updateFormFromJSON(formDTO, formJSON);
       // Update application from JSON
       FormJsonUtil.updateApplicationFromJSON(ctx, formDTO, formJSON);
       // Update owner from JSON
       FormJsonUtil.updateOwnerDefinition(ctx, formDTO, formJSON);

       return formDTO;
   }

   /** 
    * Saves a form of type Application, Role and Workflow 
    * @throws GeneralException 
    */
   private void saveForm (Form form, FormDTO formDTO)
       throws GeneralException {
       SailPointContext ctx = getContext();
       // Takes FormDTO object and merges the changes from this to the destination form.
       formDTO.commit(form);
       // ensure the new object is committed to the session
       ctx.saveObject(form);
       // Commit to database
       ctx.commitTransaction();
   }
}
