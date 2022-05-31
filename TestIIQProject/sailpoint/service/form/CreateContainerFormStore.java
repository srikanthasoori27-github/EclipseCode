package sailpoint.service.form;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Form;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

public class CreateContainerFormStore extends SimpleFormStore {

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(CreateContainerFormStore.class);

    public static final String CREATE_CONTAINER_MASTER_FORM = "create_container_master_form";
    private static final String APP_NAME = "app";
    private static final String CONTAINER_OBJECT_TYPE = "Container";
    

    /**
     * Name of the application using create container form
     */
    private String appName;

    /**
     * Application object using Forms
     */
    private Application app;

    private Form containerForm;

    /**
     * Constructor used on form retrieval.
     *
     * @param userContext The user context.
     * @param appName Application Name.
     */
    public CreateContainerFormStore(UserContext userContext, String appName) {

        super(userContext, new HashMap<>());

        this.appName = appName;

        try {
            this.containerForm = this.getApplication(appName).getProvisioningForm(Form.Type.Create, CONTAINER_OBJECT_TYPE);
        } catch (GeneralException ge) {
            // This will leave formIdOrName blank, which is handled in the SimpleFormStore code.
            // But, let's log what happened here for debugging purposes.
            log.error("Unable to retrieve create container from data store", ge);
        }
    }

    /**
     * Returns the form arguments
     * @return The form arguments
     */
    @Override
    public Map<String,Object> getFormArguments() {
        Map<String, Object> args;

        if (this.formRenderer != null) {
            args = this.formRenderer.getData();
            if (args == null) {
                args = new HashMap<String, Object>();
            }
            return args;
        }

        return new HashMap<String, Object>();
    }

    /**
     * Gets the state necessary to recreate the form bean. For this class, the application
     * name and the form id comprise the state.
     *
     * @return The form bean state.
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        Map<String, Object> args = super.getFormBeanState();
        args.put(APP_NAME, this.appName);

        return args;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form retrieveMasterForm() throws GeneralException {
        /* If the master form is stored on the session use that.  Otherwise use the form specified in the application. */
        if (sessionStorage.get(CREATE_CONTAINER_MASTER_FORM) != null) {
            this.masterForm = (Form) sessionStorage.get(CREATE_CONTAINER_MASTER_FORM);
        } else if (this.containerForm == null && this.app != null) {
            throw new GeneralException(Internationalizer.getMessage(MessageKeys.UI_PAM_NEW_CONTAINER_MISSING_FORM,
                    this.userContext.getLocale()));
        } else {
            this.masterForm = this.containerForm;
        }

        return this.masterForm;
    }

    /**
     * Stores the master form on the session storage
     *
     * @param form The expanded form.
     */
    @Override
    public void storeMasterForm(Form form) {
        super.storeMasterForm(form);
        sessionStorage.put(CREATE_CONTAINER_MASTER_FORM, form);
    }

    /**
     * Clears the master form off of the session storage
     */
    @Override
    public void clearMasterForm() {
        super.clearMasterForm();
        sessionStorage.remove(CREATE_CONTAINER_MASTER_FORM);
    }

    /**
     * Returns whether the form store has a form specified.
     *
     * @return True if a form is present, otherwise false.
     */
    public boolean hasForm() { return this.containerForm != null; }

    /**
     * Retrieve the Application object based on the id
     * @param appName Name of the application to retrieve
     * @return Application object with the specified name
     * @throws GeneralException If there is an error retrieving the application
     */
    private Application getApplication(String appName) throws GeneralException {
        if (this.app == null) {
            this.app = getContext().getObjectByName(Application.class, appName);
        }

        return this.app;
    }
}
