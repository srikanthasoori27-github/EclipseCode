/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Notary;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Form;
import sailpoint.object.WorkItem;
import sailpoint.service.WorkItemService;
import sailpoint.service.WorkflowSessionService;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * A base class that provides common functionality for a form store
 * that is session based.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public abstract class SessionBasedFormStore extends BaseFormStore {

    /**
     * The log.
     */
    private static final Log log = LogFactory.getLog(SessionBasedFormStore.class);

    /**
     * Argument used to store and retrieve the session which contains the
     * current work item and expanded form.
     */
    protected static final String ATT_WORKFLOW_SESSION = WorkflowSessionService.ATT_WORKFLOW_SESSION;

    /**
     * The workflow session. This is used to keep the expanded form and
     * work item around in the session.
     */
    protected WorkflowSession workflowSession;

    /**
     * The arguments used to process the form.
     */
    protected Map<String, Object> formArguments;

    /**
     * Constructor for SessionBasedFormStore.
     *
     * @param userContext The user context.
     */
    public SessionBasedFormStore(UserContext userContext) {
        super(userContext);
    }

    /**
     * Stores the expanded form as the active form on the workflow session.
     *
     * @param form The expanded form.
     */
    @Override
    public void storeExpandedForm(Form form) {
        super.storeExpandedForm(form);

        if (workflowSession != null) {
            workflowSession.setActiveForm(form);
        }
    }

    /**
     * Clears the active form off of the workflow session.
     */
    @Override
    public void clearExpandedForm() {
        super.clearExpandedForm();

        if (workflowSession != null) {
            workflowSession.setActiveForm(null);
        }
    }

    /**
     * Copies the form arguments into the work item and also puts the workflow session
     * on the session storage. This is used on refresh and submit.
     */
    @Override
    public void save() {
        if(workflowSession.getWorkItem()!=null) {
            WorkItemService.copyArgsToWorkItem(workflowSession.getWorkItem(), getFormArguments());
        }

        sessionStorage.put(ATT_WORKFLOW_SESSION, workflowSession);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormDTO retrieveFormDTO() throws GeneralException {
        FormDTO dto = getFormRenderer(null).createDTO();
        if (dto != null && workflowSession.getWorkItem() != null) {
            //Set the requires esig
            Notary notary = new Notary(getContext(), getLocale());
            dto.setEsigMeaning(notary.getSignatureMeaning(WorkItem.class, workflowSession.getWorkItem().getAttributes()));
        }
        return dto;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        return new HashMap<String, Object>();
    }

    /**
     * Pull the work item arguments out of the work item to use
     * as form arguments.
     *
     * @return The form arguments.
     */
    @Override
    public Map<String, Object> getFormArguments() {
        // during the submission process the argument map can be mutated
        // so we need to keep it around and not recreate every time this
        // method is called
        if (formArguments == null) {
            try {
                formArguments = new HashMap<String, Object>(
                    WorkItemService.getFormArguments(
                        getContext(),
                        workflowSession.getWorkItem(),
                        workflowSession.getWorkflowCase()
                    )
                );
            } catch (GeneralException ge) {
                log.warn("Unable to get form arguments for work item", ge);
            }
        }

        return formArguments;
    }

}
