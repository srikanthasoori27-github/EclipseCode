
package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.FormHandler;
import sailpoint.web.UserContext;
import sailpoint.web.lcm.attributes.IdentityPolicyAttributesFormBuilder;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityRequestLibrary;

import java.util.*;

/**
 * Service class for handling things about the Edit Identity form
 */
public class IdentityFormService {
    public static final String EDIT_IDENTITY_WORKFLOW_NAME = "IdentityEditRequest";
    public static final String CREATE_IDENTITY_WORKFLOW_NAME = "IdentityCreateRequest";

    private final SailPointContext context;
    private final Identity requester;
    private final Identity requestee;
    private final QuickLink quickLink;
    private final String ticketApp;
    private final String externalApp;
    private final UserContext userContext;

    /**
     * Constructor
     */
    public IdentityFormService(SailPointContext context, Identity requester, Identity requestee, QuickLink quickLink, String ticketApp, String externalApp, UserContext userContext) {
        this.context = context;
        this.requester = requester;
        this.requestee = requestee;
        this.quickLink = quickLink;
        this.ticketApp = ticketApp;
        this.externalApp = externalApp;
        this.userContext = userContext;
    }

    /**
     * Validates the submitted form data and launches the workflow
     * @param editIdentityFormStore The edit identity form store
     * @param formData The posted form data
     * @param comment The comment if one is provided
     * @param priority The priority if one is provided
     * @return WorkFlowResultItem with summary or results
     * @throws EditIdentityException if there was an error we want to render to the user
     * @throws GeneralException For other non-recoverable situations
     */
    public UpdateIdentityResultItem updateIdentity(BaseFormStore editIdentityFormStore, Map<String, Object> formData, String comment, WorkItem.Level priority) throws GeneralException{
        FormHandler formHandler = new FormHandler(context, editIdentityFormStore);
        Form expandedForm = editIdentityFormStore.retrieveExpandedForm();
        FormRenderer formRenderer = editIdentityFormStore.getFormRenderer(null);
        formRenderer.setData(formData);
        boolean isValid = formHandler.submit(expandedForm, formRenderer, formData);
        List<AccountRequest> requests = new ArrayList<AccountRequest>();
        if(isValid) {
            // Turn the form into an AccountRequest.  This does some
            // additional validation.
            AccountRequest request = formToRequest(expandedForm, formRenderer);
            requests.add(request);

            // Clear session of requests - this new request overwrites the
            // current requests rather than getting added to them.
            editIdentityFormStore.clearMasterForm();

        } else {
            Map<String, String> fieldValidationErrors = formRenderer.getFieldValidation();
            /* If there were field level validation errors we need to send back the validation map.  Otherwise
             * we give the generic message */
            if (!Util.isEmpty(fieldValidationErrors)) {
                throw new EditIdentityValidationException(fieldValidationErrors);
            }
            Message errorMessage = new Message(MessageKeys.LCM_MANAGE_ATTRIBUTES_FORM_VALIDATION_ERROR);
            throw new EditIdentityException(errorMessage.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
        }
        LCMRequestProcessor lcmRequestProcessor = createLCMRequestProcessor(priority);

        String workflowName = requestee == null ? CREATE_IDENTITY_WORKFLOW_NAME : EDIT_IDENTITY_WORKFLOW_NAME;
        WorkflowSession editRequestSession = lcmRequestProcessor.startWorkflow(requests, workflowName, comment);

        return createResult(editRequestSession);
    }

    /**
     * Creates a WorkFlowResultItem from the session
     * @param editIdentitySession The workflow session from starting the edit identity workflow
     * @return A workFlowResultItem
     * @throws GeneralException
     */
    private UpdateIdentityResultItem createResult(WorkflowSession editIdentitySession) throws GeneralException {
        WorkflowLaunch launch = editIdentitySession.getWorkflowLaunch();

        String status = launch == null ? null : launch.getStatus();
        String requestName = getIdentityRequestName(editIdentitySession);
        List<Message> messages = editIdentitySession.getLaunchMessages();

        // If the launch failed, throw a new password change exception
        if(launch.isFailed()) {
            MessageService messageService = new MessageService(userContext);
            throw new EditIdentityException(messageService.getLocalizedMessages(messages));
        }
        /* Values that depend on existing workitem default to null */
        WorkItem wfWorkItem = editIdentitySession.getWorkItem();
        String workItemType = null;
        String workItemId = null;
        if (wfWorkItem != null) {
            workItemType = wfWorkItem.getType().toString();
            workItemId = wfWorkItem.getId().toString();
        }

        return new UpdateIdentityResultItem(status, requestName, workItemType, workItemId, messages);
    }

    /**
     * Returns the name of the identity request for the workflow
     *
     * @param session The workflow session to get the id from
     * @return The name of the IdentityRequest
     * @throws GeneralException If unable to get the workflow's task result
     */
    private String getIdentityRequestName(WorkflowSession session) throws GeneralException {
        String id = null;
        WorkflowCase workflowCase = session.getWorkflowCase();
        String requestId = null;
        if ( workflowCase != null ) {
            id = (String)workflowCase.get(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
            if ( Util.getString(requestId) == null ) {
                TaskResult result = workflowCase.getTaskResult(context);
                if ( result != null ) {
                    id = (String)result.getAttribute(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
                }
            }
        }
        return id;
    }

    /**
     * Takes master form and form renderer and generates an account request
     * @param masterForm The master form
     * @param formRenderer The form renderer
     * @return An account request representing that changes present in the form
     * @throws GeneralException
     */
    private AccountRequest formToRequest(Form masterForm, FormRenderer formRenderer) throws GeneralException {

        AccountRequest request = null;

        String requesteeId = null;
        if(requestee != null) {
            requesteeId = requestee.getId();
        }
        // Create an AccountRequest built from the master form.
        IdentityPolicyAttributesFormBuilder formBuilder =
                new IdentityPolicyAttributesFormBuilder(context, masterForm, requesteeId);
        request = formBuilder.createRequest();

        // If there are any form errors, add them to the page and return null.
        if(formBuilder.hasError()) {
            MessageService messageService = new MessageService(userContext);
            List<String> localizedMessages = messageService.getLocalizedMessages(formBuilder.getErrors());
            throw new EditIdentityException(localizedMessages);
        }
        else {
            // Remove any AttributeRequests that don't have identity mappings.
            // At some point, we may want to way to send non-identity attributes
            // into the workflow, but this just clutters up the cart right now.
            // Also, consider only passing through "editable" or "standard" (for
            // create) attributes.
            filterNonIdentityAttributes(request);

            // This is an edit request, filter out anything that hasn't changed.
            if(requesteeId != null) {
                request = createEditRequestFromPolicyFormRequest(request, formRenderer);
            }

            // If nothing has changed on the identity, throw a new exception to the screen
            // notifying them that there is nothing to update.
            if (request == null) {
                throw new EditIdentityException(new Message(MessageKeys.LCM_MANAGE_ATTRIBUTES_NO_CHANGE_ERROR).getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
            }
        }

        return request;
    }

    /**
     * Remove any AttributeRequests from the given AccountRequest do not have
     * corresponding identity attributes.
     * @param request The request to clean
     */
    private void filterNonIdentityAttributes(AccountRequest request)
            throws GeneralException {

        if (!Util.isEmpty(request.getAttributeRequests())) {
            Iterator<ProvisioningPlan.AttributeRequest> it = request.getAttributeRequests().iterator();
            while (it.hasNext()) {
                ProvisioningPlan.AttributeRequest attrReq = it.next();
                if(!isPasswordAttribute(attrReq.getNameXml())) {
                    ObjectAttribute attr =
                            getIdentityConfig().getObjectAttribute(attrReq.getName());
                    if (null == attr) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Filter anything that has not been modified from its original value in
     * the given AccountRequest
     * @param accountRequest The account request
     * @param formRenderer The form renderer
     */
    private AccountRequest createEditRequestFromPolicyFormRequest(AccountRequest accountRequest, FormRenderer formRenderer)
            throws GeneralException {

        AccountRequest request = new AccountRequest();
        request.setApplication(ProvisioningPlan.APP_IIQ);
        request.setOperation(AccountRequest.Operation.Modify);
        /** Add a unique identifier so we can get at it from the summary page grid **/
        request.addArgument("id", Util.uuid());
        request.setNativeIdentity(requestee.getName());

        for (ProvisioningPlan.AttributeRequest formAttrRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
            ObjectAttribute attribute =
                    getIdentityConfig().getObjectAttribute(formAttrRequest.getName());

            // Should have been filtered already.  Be extra safe, though.
            if (attribute==null) {
                continue;
            }

            Object current = requestee.getAttribute(attribute.getName());
            Object newObj = formAttrRequest.getValue();

            if (hasChanged(requestee, attribute, current, newObj)) {

                ProvisioningPlan.AttributeRequest attr = new ProvisioningPlan.AttributeRequest();
                String attrName = attribute.getName();

                /** Convert the name from a suggest name if this is an identity value **/
                ObjectConfig config = Identity.getObjectConfig();
                ObjectAttribute attrDef = config.getObjectAttribute(attrName);
                if (attrDef == null && formRenderer != null) {
                    attrName = formRenderer.getForm().getIdentityFieldName(attrName);
                    attrDef = config.getObjectAttribute(attrName);
                    if (attrDef == null || attrDef.getType() == null ||
                            !attrDef.getType().equals(BaseAttributeDefinition.TYPE_IDENTITY)) {
                        // Restore the original name if this wasn't really an Identity value
                        attrName = attribute.getName();
                    }
                }

                attr.setOperation(ProvisioningPlan.Operation.Set);
                attr.setName(attrName);
                attr.setValue(newObj);
                request.add(attr);
            }
        }

        return (request.isEmpty()) ? null : request;
    }

    /**
     * Returns true if the attribute is a password
     * @param attributeName The name of the attribute
     * @return true if the attribute is a password
     */
    private boolean isPasswordAttribute(String attributeName) {
        return IntegrationConfig.ATT_PASSWORD.equals(attributeName);
    }

    /**
     * Returns the identity object config
     * @return the identity object config
     * @throws GeneralException
     */
    public ObjectConfig getIdentityConfig()
            throws GeneralException {

        return ObjectConfig.getObjectConfig(Identity.class);
    }

    /**
     * Return whether the newObj has changed from the corresponding property on
     * the given object.
     */
    private boolean hasChanged(SailPointObject object, ObjectAttribute attribute,
                               Object current, Object newObj) {

        boolean changed = false;

        if (current == null) {
            changed = newObj != null && !newObj.equals("");
        } else {
            if (object.isExtendedIdentityType(attribute)) {
                if (current instanceof Identity) {
                    Identity currentIdentity = (Identity) current;
                    changed = !currentIdentity.getName().equals(newObj);
                } else {
                    changed = !current.equals(newObj);
                }
            } else {
                changed = !current.equals(newObj);
            }
        }

        return changed;
    }

    /**
     * create a LCMRequestProcessor object to help start request workflow
     * It seems like for change password this is always Normal priority.
     * @return LCMRequestProcessor object
     */
    private LCMRequestProcessor createLCMRequestProcessor(WorkItem.Level priority) {
        return new LCMRequestProcessor(this.context, this.requester, this.requestee, this.quickLink, this.ticketApp, this.externalApp, priority);
    }
}
