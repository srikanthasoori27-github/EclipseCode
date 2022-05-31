/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.WorkflowSession;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.Util;
import sailpoint.object.Form;
import sailpoint.object.WorkItem;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.service.form.renderer.item.ButtonDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * A form store that retrieve form objects from the work items they are attached to.  Used by the REST service to
 * return forms by work item to the client
 *
 * @author peter.holcomb
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class WorkItemFormStore extends SessionBasedFormStore {

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(WorkItemFormStore.class);

    /**
     * The id of the work item that the form should be retrieved from.
     */
    String workItemId;

    /**
     * The work item from the data store.
     */
    WorkItem workItem;

    /**
     * The work item id argument. Used to store the work item id when
     * the form bean state is created and read.
     */
    private static final String ARG_WORK_ITEM_ID = "workItemId";

    /**
     * Constructs a new instance of WorkItemFormStore.
     *
     * @param userContext The user context.
     * @param workItemId The work item id.
     */
    public WorkItemFormStore(UserContext userContext, String workItemId) {
        super(userContext);

        this.workItemId = workItemId;
    }

    /**
     * Constructs a new instance of WorkItemFormStore. This constructor has the signature
     * necessary for this object to be constructed by reflection during the postback and
     * submission process.
     *
     * @param userContext The user context.
     * @param state The form state.
     */
    public WorkItemFormStore(UserContext userContext, Map<String, Object> state) {
        super(userContext);

        if (state.containsKey(ARG_WORK_ITEM_ID)) {
            this.workItemId = (String) state.get(ARG_WORK_ITEM_ID);
        }
    }

    /**
     * Retrieve the form from the work item.
     *
     * @return The work item form.
     */
    @Override
    public Form retrieveMasterForm() throws GeneralException {
        initWorkflowSession(true);

        if (workflowSession != null) {
            masterForm = workflowSession.getWorkItem().getForm();
        }

        return masterForm;
    }

    /**
     * Gets the expanded form off of the session. If no form on the session then
     * we expand the master form.
     *
     * @return The expanded form.
     * @throws GeneralException
     */
    @Override
    public Form retrieveExpandedForm() throws GeneralException {
        initWorkflowSession(false);

        // if workflowSession is not null but the active form is this means that we
        // transitioned to a new work item and we need to expand the new master form
        if (workflowSession != null && workflowSession.getActiveForm() != null) {
            expandedForm = workflowSession.getActiveForm();
        } else {
            // must be first request so retrieve the master form and initialize it to
            // get the expanded form which will be stored in the workflow session later
            expandedForm = super.retrieveExpandedForm();
        }

        return expandedForm;
    }

    /**
     * Gets the state necessary to recreate the form bean. For this class, the work item
     * id is the only necessary state.
     *
     * @return The form bean state.
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(ARG_WORK_ITEM_ID, this.workItemId);

        return args;
    }

    /**
     * Returns the WorkItemAuthorizer which can be used to authorize actions on
     * the work item form.
     *
     * @param isRead True if the request is to read the form, false if any action
     *               is being taken.
     * @return The authorizer.
     * @throws GeneralException
     */
    @Override
    public Authorizer getAuthorizer(boolean isRead) throws GeneralException {
        // only allow requester if trying to view the form
        // otherwise reject since requester cannot take action
        return new WorkItemAuthorizer(getWorkItem(), isRead);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormDTO retrieveFormDTO() throws GeneralException {
        FormDTO formDTO = super.retrieveFormDTO();

        if (isReadOnly()) {
            makeReadOnly(formDTO);
        }

        return formDTO;
    }

    /**
     * Determines if this form should be read only. This is the case when
     * the user viewing the work item is the requester or is in the
     * requesting workgroup.
     *
     * @return True if the form should be made read only, false otherwise.
     * @throws GeneralException
     */
    private boolean isReadOnly() throws GeneralException {
        // if we get this far then we know that the user is already
        // authorized to at least view the form now we need to decide
        // if they are only allowed to view the read only version, if
        // this authorizer fails then we now they are the requester
        // and should only have read only access
        WorkItemAuthorizer workItemAuthorizer = new WorkItemAuthorizer(getWorkItem(), false);

        try {
            workItemAuthorizer.authorize(userContext);

            return false;
        } catch (UnauthorizedAccessException e) {
            return true;
        }
    }

    /**
     * Transforms the FormDTO into the read only version.
     *
     * @param formDTO The form DTO.
     */
    private void makeReadOnly(FormDTO formDTO) {
        // set the form as read only
        formDTO.setReadOnly(true);

        // clear out all buttons and add the return button
        formDTO.getButtons().clear();
        formDTO.addButton(createReturnButton());
    }

    /**
     * Creates the return button that is shown to a user
     * who has read only access to the form. The return
     * button has an action of CANCEL.
     *
     * @return The return button.
     */
    private ButtonDTO createReturnButton() {
        Message msg = new Message(MessageKeys.UI_BUTTON_RETURN);

        Form.Button returnBtn = new Form.Button();
        returnBtn.setAction(Form.ACTION_CANCEL);
        returnBtn.setLabel(msg.getLocalizedMessage(getLocale(), getTimeZone()));

        return new ButtonDTO(returnBtn);
    }

    /**
     * Initializes the workflow session either pulling it out of the session storage or
     * creating a new instance using the work item.
     *
     * @param create True to create a new instance of WorkflowSession if one does not exist.
     * @throws GeneralException
     */
    private void initWorkflowSession(boolean create) throws GeneralException {
        if (workflowSession == null) {
            if (sessionStorage.containsKey(ATT_WORKFLOW_SESSION)) {
                workflowSession = (WorkflowSession) sessionStorage.get(ATT_WORKFLOW_SESSION);

                // check to see if the work items match and if they do not then
                // reset the workflow session
                if (!isWorkItemMatch()) {
                    workflowSession = null;
                }
            }

            if (workflowSession == null && create) {
                if (getWorkItem() != null) {
                    workflowSession = new WorkflowSession(getWorkItem());
                } else {
                    throw new ObjectNotFoundException(WorkItem.class, workItemId);
                }
            }
        }
    }

    /**
     * Determines if the work item id passed into the store matches the id
     * of the work item in the WorkflowSession.
     *
     * @return True if the ids match, false otherwise.
     */
    private boolean isWorkItemMatch() {
        return workflowSession != null &&
               workflowSession.getWorkItem() != null &&
               // IIQTC-217: Added null safe comparison
               Util.nullSafeEq(workflowSession.getWorkItem().getId(), workItemId);
    }

    /**
     * Gets the work item from the data store.
     *
     * @return The work item.
     * @throws GeneralException
     */
    private WorkItem getWorkItem() throws GeneralException {
        if (workItem == null) {
            workItem = getContext().getObjectById(WorkItem.class, workItemId);
        }

        return workItem;
    }
}
