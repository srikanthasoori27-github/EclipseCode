/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.form;

import sailpoint.api.ValidationException;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ListResult;
import sailpoint.object.Form;
import sailpoint.rest.BadRequestDTO;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.SessionStorage;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.DynamicValuesService;
import sailpoint.service.form.FormResult;
import sailpoint.service.form.FormService;
import sailpoint.service.form.TransientFormStore;
import sailpoint.service.form.object.DynamicValuesOptions;
import sailpoint.service.form.object.FormOptions;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.util.Map;

/**
 * The base form resource contains the generic form endpoints that are
 * necessary for both the internal and external form endpoints.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class BaseFormResource extends BaseResource {

    public BaseFormResource() {}

    public BaseFormResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Gets the form config for a form that lives in a work item in a
     * transient workflow.
     *
     * @return The form configuration object.
     * @throws GeneralException
     */
    @GET
    @Path("session")
    public FormDTO getSessionForm() throws GeneralException {
        TransientFormStore store = new TransientFormStore(this);

        authorize(store.getAuthorizer(true));

        return getForm(store);
    }

    /**
     * Performs a post back operation on the form contained in the submitted data. This
     * endpoint also handles the refresh button action.
     *
     * @param data The posted data.
     * @return The refreshed form configuration.
     * @throws sailpoint.tools.GeneralException
     */
    @POST
    @Path("postback")
    public FormDTO postBack(Map<String, Object> data) throws GeneralException {
        FormOptions formOptions = new FormOptions(data);

        authorize(formOptions, false);

        FormService formService = getFormService();

        try {
            return formService.refresh(formOptions);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND), e);
        }
    }

    /**
     * Gets a page of dynamic allowed values for the field in the form specified in
     * the posted data.
     *
     * @param data The posted data.
     * @return The list result representing the page of dynamic result values.
     * @throws GeneralException
     */
    @POST
    @Path("dynamicAllowedValues")
    public ListResult getDynamicAllowedValues(Map<String, Object> data) throws GeneralException {
        DynamicValuesOptions options = new DynamicValuesOptions(data);

        authorize(options, false);

        DynamicValuesService valuesService = new DynamicValuesService(this, getSessionStorage());

        return valuesService.calculateAllowedValues(options);
    }

    /**
     * Submits a form specified in the data. This endpoint handles the next, back and cancel
     * button actions.
     *
     * @param data The posted form data.
     * @return A Response object indicating that the request was either successful or failed
     */
    @POST
    @Path("submit")
    public Response submit(Map<String, Object> data)
            throws GeneralException, ExpiredPasswordException {
        FormOptions options = new FormOptions(data);

        // treat CANCEL as a read
        authorize(options, Form.ACTION_CANCEL.equals(options.getAction()));

        FormService formService = getFormService();
        Response response;
        FormResult result = new FormResult();
        //defect 29594 This is so we can do validation on the a step.  
        try {
            result = formService.submit(options, getLoggedInUser());
        } catch (ValidationException ve) {
            //bug 29594 we throw an exception for validation errors.  We must
            //catch them and send that back to the UI
            if (options.getFormId() != null) {
                result.addValidationError(options.getFormId(), ve.getMessage());
            }
        }
        
        if (!result.isValid()) {
            BadRequestDTO dto = new BadRequestDTO(Response.Status.BAD_REQUEST.name(), result.getValidationErrors());

            response = Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
        } else {
            response = Response.ok().entity(result).build();
        }

        return response;
    }

    /**
     * Creates an instance of FormService.
     *
     * @return The form service.
     */
    protected FormService getFormService() {
        return new FormService(this, getSessionStorage());
    }

    /**
     * Gets the FormDTO from the form store.
     *
     * @param formStore The form store.
     * @return The FormDTO.
     * @throws GeneralException
     */
    protected FormDTO getForm(BaseFormStore formStore) throws GeneralException {
        FormService formService = getFormService();

        FormDTO formDto = formService.getForm(formStore);

        formStore.save();

        return formDto;
    }

    /**
     * Gets the session storage implementation which is used by certain form stores.
     *
     * @return The session storage.
     */
    protected SessionStorage getSessionStorage() {
        return new HttpSessionStorage(getSession());
    }

    /**
     * Performs authorization using the authorizer retrieved from the form store
     * specified in the form options.
     *
     * @param options The form options.
     * @param isRead True if the request is to read the form, false if any action
     *               is being taken.
     * @throws GeneralException
     */
    protected void authorize(FormOptions options, boolean isRead) throws GeneralException {
        BaseFormStore formStore = FormService.storeFromOptions(options, this, getSessionStorage());

        authorize(formStore.getAuthorizer(isRead));
    }

    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws Exception {
        return new FormSuggestResource(this, getSessionStorage());
    }

}
