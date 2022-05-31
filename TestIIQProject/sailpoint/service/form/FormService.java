/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import sailpoint.api.AuthenticationFailureException;
import sailpoint.api.Notary;
import sailpoint.authorization.UnauthorizedAccessException;
import java.util.Arrays;
import java.util.Map;

import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.service.SessionStorage;
import sailpoint.service.WorkItemResult;
import sailpoint.service.WorkflowSessionService;
import sailpoint.service.form.object.FormOptions;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;
import sailpoint.web.FormBean;
import sailpoint.web.FormHandler;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * A service class that handles retrieving, refreshing and submission of forms.
 *
 * @author peter.holcomb
 */
public class FormService {

    /**
     * The user context required by the newer form stores.
     */
    protected UserContext userContext;

    /**
     * The session storage used by some form stores.
     */
    protected SessionStorage sessionStorage;

    /**
     * Constructs a new instance of FormService.
     *
     * @param userContext The user context.
     * @param sessionStorage The session storage.
     */
    public FormService(UserContext userContext, SessionStorage sessionStorage) {
        this.userContext = userContext;
        this.sessionStorage = sessionStorage;
    }

    /**
     * Gets the FormDTO from the specified form store.
     *
     * @param store The form store.
     * @return The form DTO.
     * @throws GeneralException
     */
    public FormDTO getForm(BaseFormStore store) throws GeneralException {
        store.setSessionStorage(sessionStorage);

        FormDTO formDto = store.retrieveFormDTO();

        store.save();

        return formDto;
    }

    /**
     * Refreshes the form based on the options specified. The formBeanClass contained in
     * the options MUST be a child of the BaseFormStore class for this method to work.
     *
     * @param options The options.
     * @return The form DTO.
     * @throws GeneralException
     */
    public FormDTO refresh(FormOptions options) throws GeneralException {
        BaseFormStore baseStore = storeFromOptions(options, userContext, sessionStorage);

        FormRenderer formRenderer = getFormRenderer(baseStore, options);
        formRenderer.setData(options.getFormData());
        formRenderer.setAction(options.getAction());
        formRenderer.setActionParameter(options.getActionParameter());
        formRenderer.setActionParameterValue(options.getActionParameterValue());

        FormHandler formHandler = new FormHandler(userContext.getContext(), baseStore);
        formHandler.refresh(baseStore.retrieveMasterForm(), formRenderer, baseStore.getFormArguments());

        baseStore.save();

        return baseStore.retrieveFormDTO();
    }

    /**
     * Submits the form specified by the options.
     *
     * @param options The form options.
     * @return The form result.
     */
    public FormResult submit(FormOptions options, Identity completer)
            throws GeneralException, ExpiredPasswordException {
        String action = options.getAction();

        // dispatch based on the button action
        if (Form.ACTION_NEXT.equals(action)) {
            return next(options, completer);
        } else if (Form.ACTION_CANCEL.equals(action)) {
            return cancel();
        } else if (Form.ACTION_BACK.equals(action)) {
            return back(options, completer);
        } else {
            throw new GeneralException("Unknown form button action specified: " + action);
        }
    }

    /**
     * Validates the submitted data against the form and if valid advances the workflow.
     *
     * @param options The form options.
     * @param completer The Identity that completed the work item.
     * @return The form result.
     * @throws GeneralException
     */
    private FormResult next(FormOptions options, Identity completer)
            throws GeneralException, ExpiredPasswordException {
        BaseFormStore baseStore = storeFromOptions(options, userContext, sessionStorage);

        FormRenderer renderer = getFormRenderer(baseStore, options);
        renderer.setData(options.getFormData());
        renderer.setAction(options.getAction());
        renderer.setActionParameter(options.getActionParameter());
        renderer.setActionParameterValue(options.getActionParameterValue());

        FormHandler handler = new FormHandler(userContext.getContext(), baseStore);

        boolean valid = handler.submit(
            baseStore.retrieveMasterForm(),
            renderer,
            baseStore.getFormArguments()
        );

        FormResult result = null;
        if (!valid) {
            result = new FormResult();
            result.addValidationErrors(renderer.getFieldValidation());
        } else {
            baseStore.save();

            WorkflowSessionService workflowSessionService = getWorkflowSessionService();

            // Sign the work item if it has an electronic signature present
            if (options != null && options.getElectronicSignature() != null) {
                try {
                    Notary notary = new Notary(userContext.getContext(), userContext.getLocale());
                    notary.sign(workflowSessionService.getCurrentWorkItem(), options.getElectronicSignature().getAccountId(), options.getElectronicSignature().getPassword());
                } catch (AuthenticationFailureException afe) {
                    throw new UnauthorizedAccessException(MessageKeys.ESIG_POPUP_AUTH_FAILURE, afe);
                }
            }
            
            WorkItemResult workItemResult = workflowSessionService.advance(completer, true);
            result = new FormResult(workItemResult);
        }

        return result;
    }

    /**
     * Cancels the current form work item.
     *
     * @return The form result.
     * @throws GeneralException
     */
    private FormResult cancel() throws GeneralException {
        WorkflowSessionService workflowSessionService = getWorkflowSessionService();
        workflowSessionService.clear();

        FormResult result = new FormResult();
        result.setCancelled(true);

        return result;
    }

    /**
     * Completes the form work item, skipping validation and steps back
     * in the workflow.
     *
     * @param options The form options.
     * @return The form result.
     * @throws GeneralException
     */
    private FormResult back(FormOptions options, Identity completer) throws GeneralException {
        BaseFormStore baseStore = storeFromOptions(options, userContext, sessionStorage);

        FormRenderer renderer = getFormRenderer(baseStore, options);
        renderer.setData(options.getFormData());
        renderer.setAction(options.getAction());
        renderer.setActionParameter(options.getActionParameter());
        renderer.setActionParameterValue(options.getActionParameterValue());

        FormHandler handler = new FormHandler(userContext.getContext(), baseStore);
        handler.back(baseStore.retrieveMasterForm(), renderer, baseStore.getFormArguments());

        baseStore.save();

        WorkflowSessionService workflowSessionService = getWorkflowSessionService();
        WorkItemResult result = workflowSessionService.advance(completer, false);

        return new FormResult(result);
    }

    /**
     * Creates an instance of WorkflowSessionService.
     *
     * @return The service.
     */
    private WorkflowSessionService getWorkflowSessionService() {
        return new WorkflowSessionService(userContext.getContext(), sessionStorage);
    }

    /**
     * Instantiates the form bean using the class name and state specified in the options.
     *
     * @param options The options.
     * @return The form bean instance.
     * @throws GeneralException
     */
    public FormBean instantiateFormBean(FormOptions options) throws GeneralException {
        return instantiateFromOptions(options, userContext);
    }

    /**
     * Gets the form renderer from the form bean and options specified.
     *
     * @param formBean The form bean.
     * @param options The options.
     * @return The form renderer.
     * @throws GeneralException
     */
    public FormRenderer getFormRenderer(FormBean formBean, FormOptions options) throws GeneralException {
        return formBean.getFormRenderer(options.getFormId());
    }

    /**
     * Get the field referenced by the field name from the form in the options
     * @param formOptions The formOptions to initialize the store and renderer
     * @param fieldName Name of the field to find.
     * @return Field object if the form can be found, otherwise null.
     * @throws GeneralException
     */
    public Field getField(FormOptions formOptions, String fieldName) throws GeneralException {
        BaseFormStore formStore = storeFromOptions(formOptions, this.userContext, this.sessionStorage);
        FormRenderer formRenderer = getFormRenderer(formStore, formOptions);
        Form form = formRenderer.getForm();
        return (form == null) ?  null : form.getField(fieldName);
    }

    /**
     * Instantiates an instance of BaseFormStore contained in the options.
     *
     * @param options The options.
     * @param userContext The user context.
     * @param sessionStorage The session storage.
     * @return The BaseFormStore instance.
     * @throws GeneralException
     */
    public static BaseFormStore storeFromOptions(FormOptions options,
                                                 UserContext userContext,
                                                 SessionStorage sessionStorage) throws GeneralException {

        BaseFormStore baseFormStore = instantiateFromOptions(options, userContext);
        baseFormStore.setSessionStorage(sessionStorage);

        return baseFormStore;
    }

    /**
     * Instantiates an object instance of type T. T should have a constructor with signature as
     * described below in the method. This is currently used to create instances of the FormBean
     * and BaseFormStore classes.
     *
     * @param options The form options.
     * @param <T> The return type.
     * @return The instance.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private static <T> T instantiateFromOptions(FormOptions options, UserContext userContext) throws GeneralException {
        try {
            T obj;

            if (!options.hasFormBeanState()) {
                obj = (T) Reflection.newInstance(options.getFormBeanClass());
            } else {
                Map<String, Object> formBeanState = options.getFormBeanState();
                Class<?>[] argTypes = { UserContext.class, Map.class };

                // form stores for the new UI have this constructor signature, old ones don't do
                // so fall back to the old way when we detect this constructor does not exist
                if (Reflection.hasConstructorWithArgs(options.getFormBeanClass(), argTypes)) {
                    obj = (T) Reflection.newInstance(
                        options.getFormBeanClass(),
                        argTypes,
                        userContext,
                        formBeanState
                    );
                } else {
                    obj = (T) Reflection.newInstance(
                        options.getFormBeanClass(),
                        Arrays.copyOfRange(argTypes, 1, argTypes.length),
                        formBeanState
                    );
                }
            }

            return obj;
        } catch (Exception e) {
            throw new GeneralException(
                "Could not instantiate FormBean instance for " + options.getFormBeanClass(),
                e
            );
        }
    }
}
