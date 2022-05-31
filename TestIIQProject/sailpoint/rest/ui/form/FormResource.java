/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.form;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.rest.ui.form.BaseFormResource;
import sailpoint.service.SessionStorage;
import sailpoint.service.form.CreateContainerFormStore;
import sailpoint.service.form.PluginConfigFormStore;
import sailpoint.service.form.SimpleFormStore;
import sailpoint.service.form.WorkItemFormStore;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * A REST resource for processing forms in the responsive UI.
 *
 * @author peter.holcomb
 */
@Path("forms")
public class FormResource extends BaseFormResource {

    /**
     * Retrieve a form from the database by name
     *
     * @param formName The name of the form to be returned
     * @return FormDTO a dto representation of the form
     * @throws GeneralException
     * @path ui/rest/forms/form/{name}
     */
    @GET
    @Path("form/{name}")
    public FormDTO getSimpleForm(@PathParam("name") String formName)
            throws GeneralException {

        try {
            SimpleFormStore store = new SimpleFormStore(this, formName);

            authorize(store.getAuthorizer(true));

            return getForm(store);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_NAME), e);
        }
    }

    /**
     * Returns the form attached to a specific work id (as specified by the id of the work item
     *
     * @param workItemId The id of the work item we are fetching a form for
     * @return FormDTO a dto representation of the form
     * @throws GeneralException
     * @path ui/rest/forms/workItem/{id}
     */
    @GET
    @Path("workItem/{id}")
    public FormDTO getWorkItemForm(@PathParam("id") String workItemId)
            throws GeneralException {

        try {
            WorkItemFormStore store = new WorkItemFormStore(this, workItemId);

            authorize(store.getAuthorizer(true));

            return getForm(store);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_WORKITEM_ID), e);
        }
    }

    /**
     * Retrieve a plugin config form from the database by plugin id
     *
     * @param pluginId The id of the plugin that uses a Sailpoint Form for configuration
     * @return FormDTO a dto representation of the form
     * @throws GeneralException
     * @path ui/rest/forms/pluginConfig/{id}
     */
    @GET
    @Path("pluginConfig/{id}")
    public FormDTO getPluginConfigForm(@PathParam("id") String pluginId)
            throws GeneralException {

        PluginConfigFormStore store = null;

        try {
            store = new PluginConfigFormStore(this, pluginId);

            authorize(store.getAuthorizer(true));

            // Ensure any previous session data is cleared
            SessionStorage sessionStorage = getSessionStorage();
            store.setSessionStorage(sessionStorage);
            store.clearMasterForm();
            store.clearExpandedForm();

            return getForm(store);
        } catch (ObjectNotFoundException e) {

            // Only return a "not found" error if a form is defined.
            // Otherwise, a "no configuration" message should be displayed.
            if (store.hasForm()) {
                throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_NAME), e);
            }
        }

        return null;
    }

    /**
     * Retrieve a container form from the application by application name
     *
     * @param app The name of the application that contains the container form
     * @return FormDTO a dto representation of the form
     * @throws GeneralException
     * @path ui/rest/forms/contianerConfig/{app}
     */
    @GET
    @Path("createContainer/{app}")
    public FormDTO getContainerConfigForm(@PathParam("app") String app)
            throws GeneralException {

        authorize(new RightAuthorizer(SPRight.CreatePAMContainer, SPRight.FullAccessPAM));

        CreateContainerFormStore store = null;

        try {
            store = new CreateContainerFormStore(this, app);

            authorize(store.getAuthorizer(true));

            // Ensure any previous session data is cleared
            SessionStorage sessionStorage = getSessionStorage();
            store.setSessionStorage(sessionStorage);
            store.clearMasterForm();
            store.clearExpandedForm();

            return getForm(store);
        } catch (ObjectNotFoundException e) {

            // Only return a "not found" error if a form is defined.
            // Otherwise, a "no configuration" message should be displayed.
            if (store.hasForm()) {
                throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_NAME), e);
            }
        }

        return null;
    }
}
