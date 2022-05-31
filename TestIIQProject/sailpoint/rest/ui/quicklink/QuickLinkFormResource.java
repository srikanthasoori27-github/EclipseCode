package sailpoint.rest.ui.quicklink;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.core.Response;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.object.WorkItem;
import sailpoint.rest.BadRequestDTO;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.ui.form.BaseFormResource;
import sailpoint.service.EditIdentityException;
import sailpoint.service.EditIdentityValidationException;
import sailpoint.service.IdentityFormService;
import sailpoint.service.RequestAccessService;
import sailpoint.service.SessionStorage;
import sailpoint.service.UpdateIdentityResultItem;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.ProvisioningPolicyFormStore;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Resource for handling quicklink forms (i.e. Edit Identity, Create Identity)
 */
public class QuickLinkFormResource extends BaseFormResource {

    private QuickLink quickLink;

    /**
     * Constructor
     * @param parent Parent Base resource
     */
    public QuickLinkFormResource(BaseResource parent) {
        super(parent);
    }


    /**
     * Constructor
     * @param parent Parent Base resource
     */
    public QuickLinkFormResource(QuickLink quickLink, BaseResource parent) {
        super(parent);
        this.quickLink = quickLink;
    }

    /**
     * Returns the form associated with the quicklink
     * @return The form
     * @throws GeneralException If the quicklink does not have a form
     */
    @GET
    public FormDTO getForm() throws GeneralException {
        String action = quickLink.getAction();
        if (action.equals(QuickLink.LCM_ACTION_EDIT_IDENTITY)) {
            Identity identity = getIdentityFromParams();
            authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_EDIT_IDENTITY));
            return getUpdateForm(identity);
        } else if(action.equals(QuickLink.LCM_ACTION_CREATE_IDENTITY)) {
            authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_CREATE_IDENTITY));
            return getCreateForm();
        }
        throw new GeneralException(quickLink.getName() + "does not have a form");
    }

    /**
     * Retrieves an identity from the query parameters
     * @return The specified identity
     * @throws GeneralException If there is no identityId in the query param or no matching identity is found
     */
    private Identity getIdentityFromParams() throws GeneralException {
        String identityId = getRequest().getParameter("identityId");
        if(identityId == null) {
            throw new GeneralException("Must have identityId parameter to edit identity");
        }
        Identity identity = getContext().getObjectById(Identity.class, identityId);
        if(identity == null) {
            throw new GeneralException("No identity found for " + identityId);
        }
        return identity;
    }

    /**
     * Endpoint for submitting identity provisioning forms
     * @param data The posted data
     * @return returns a provisioning response
     */
    @POST
    public Response submitForm(Map<String,Object> data) throws GeneralException {
        HashMap<String, Object> requestData = (HashMap<String, Object>) data.get("data");
        HashMap<String, Object> formData = (HashMap<String, Object>) requestData.get("formData");
        String comment = (String) requestData.get("comment");
        WorkItem.Level priority = getWorkItemLevel(requestData);

        String action = quickLink.getAction();
        if (action.equals(QuickLink.LCM_ACTION_EDIT_IDENTITY)) {
            Identity identity = getIdentityFromParams();
            authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_EDIT_IDENTITY));
            return submitUpdateForm(identity, formData, comment, priority);
        } else if(action.equals(QuickLink.LCM_ACTION_CREATE_IDENTITY)){
            authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_CREATE_IDENTITY, false));
            return submitCreateForm(formData, comment, priority);
        }
        throw new GeneralException(quickLink.getName() + " does not have a form");
    }
    
    static WorkItem.Level getWorkItemLevel(Map<String, Object> requestData) {
        WorkItem.Level priority = WorkItem.Level.Normal;
        if(Util.isNotNullOrEmpty(Util.getString(requestData, "priority"))) {
            priority = WorkItem.Level.valueOf((String) requestData.get("priority"));
        }
        return priority;
    }

    /**
     * Submits a create form
     * @param formData The form data
     * @param comment Optional comment
     * @param priority Optional priority
     * @return Provisioning response
     */
    private Response submitCreateForm(HashMap<String, Object> formData, String comment, WorkItem.Level priority) throws GeneralException {
        try {
            IdentityFormService service = createIdentityFormService(null);
            BaseFormStore updateFormStore = getEditIdentityFormStore();
            UpdateIdentityResultItem resultItem = service.updateIdentity(updateFormStore, formData, comment, priority);
            UpdateIdentityResultItem.Outcome outcome = UpdateIdentityResultItem.Outcome.VIEW_IDENTITY;
            resultItem.setOutcome(outcome);
            return Response.ok(resultItem).build();
        } catch (EditIdentityValidationException validationException) {
            BadRequestDTO dto = new BadRequestDTO("validation", validationException.getValidationErrors());
            return Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
        } catch (EditIdentityException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessages()).build();
        }
    }

    /**
     * Submits the update identity provisioning form
     * @param identity The identity being updated
     * @param formData The form data
     * @param comment Optional comment
     * @param priority Optional priority
     * @return Provisioning result
     */
    private Response submitUpdateForm(Identity identity, HashMap<String, Object> formData, String comment, WorkItem.Level priority) throws GeneralException {
        try {
            IdentityFormService service = createIdentityFormService(identity);
            BaseFormStore updateFormStore = getEditIdentityFormStore();
            UpdateIdentityResultItem resultItem = service.updateIdentity(updateFormStore, formData, comment, priority);
            UpdateIdentityResultItem.Outcome outcome = UpdateIdentityResultItem.Outcome.VIEW_IDENTITY;
            try {
                authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY));
            } catch (UnauthorizedAccessException ex) {
                outcome = UpdateIdentityResultItem.Outcome.HOME;
            }

            resultItem.setOutcome(outcome);
            return Response.ok(resultItem).build();
        } catch (EditIdentityValidationException validationException) {
            BadRequestDTO dto = new BadRequestDTO("validation", validationException.getValidationErrors());
            return Response.status(Response.Status.BAD_REQUEST).entity(dto).build();
        } catch (EditIdentityException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessages()).build();
        }
    }

    /**
     * Creates the identityFormService
     * @return instance of IdentityFormService
     * @throws GeneralException
     */
    private IdentityFormService createIdentityFormService(Identity identity) throws GeneralException {
        if(quickLink == null) {
            throw new InvalidParameterException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new IdentityFormService( getContext(), getLoggedInUser(), identity, quickLink, ticketApp, externalSource, this);
    }

    /**
     * Gets the edit identity form store and loads the http session storage
     * @return The edit identity form store
     */
    private BaseFormStore getEditIdentityFormStore() {
        ProvisioningPolicyFormStore store = new ProvisioningPolicyFormStore(this);
        SessionStorage sessionStorage = new HttpSessionStorage(getSession());
        store.setSessionStorage(sessionStorage);
        return store;
    }

    /**
     * Returns the update form
     * @param identity The identity to edit
     * @return The update form
     * @throws GeneralException If unable to create an update form
     */
    public FormDTO getUpdateForm(Identity identity) throws GeneralException {
        try {
            ProvisioningPolicyFormStore store = new ProvisioningPolicyFormStore(this);
            SessionStorage sessionStorage = getSessionStorage();
            store.setSessionStorage(sessionStorage);
            // Ensure any previous session data is cleared
            store.clearMasterForm();
            store.clearExpandedForm();
            store.setIdentity(identity);
            store.setAction(quickLink.getAction());

            authorize(store.getAuthorizer(true));

            return getForm(store);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_NAME), e);
        }
    }

    /**
     * Returns the create identity provisioning form
     * @return The create identity provisioning form
     * @throws GeneralException If unable to create the create identity form
     */
    public FormDTO getCreateForm() throws GeneralException {
        try {
            ProvisioningPolicyFormStore store = new ProvisioningPolicyFormStore(this);
            SessionStorage sessionStorage = getSessionStorage();
            store.setSessionStorage(sessionStorage);
            // Ensure any previous session data is cleared
            store.clearMasterForm();
            store.clearExpandedForm();
            store.setIdentity(null);
            store.setAction(quickLink.getAction());
            authorize(store.getAuthorizer(true));

            return getForm(store);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_FORM_RESOURCE_NOT_FOUND_NAME), e);
        }
    }
}
