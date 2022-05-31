/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.WorkflowSession;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.Authorizer;
import sailpoint.object.Form;
import sailpoint.service.WorkflowSessionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.Map;

/**
 * A form store used to interact with a form that lives in the WorkflowSession of
 * a transient workflow, i.e. the work item is not persisted to the database and
 * lives only in memory inside the WorkflowSession.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class TransientFormStore extends SessionBasedFormStore {

    /**
     * The log.
     */
    private static final Log log = LogFactory.getLog(TransientFormStore.class);

    /**
     * Constructor for TransientFormStore.
     *
     * @param userContext The user context.
     */
    public TransientFormStore(UserContext userContext) {
        super(userContext);
    }

    /**
     * Constructor signature necessary for reconstructing this object through reflection.
     *
     * @param userContext The user context.
     * @param state The form store state.
     */
    public TransientFormStore(UserContext userContext, Map<String, Object> state) {
        this(userContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form retrieveMasterForm() throws GeneralException {
        initWorkflowSession();

        if (workflowSession != null && workflowSession.getWorkItem() !=null ) {
            masterForm = workflowSession.getWorkItem().getForm();
        }

        return masterForm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form retrieveExpandedForm() throws GeneralException {
        initWorkflowSession();

        if (workflowSession != null && workflowSession.getActiveForm() != null) {
            expandedForm = workflowSession.getActiveForm();
        } else {
            expandedForm = super.retrieveExpandedForm();
        }

        return expandedForm;
    }

    /**
     * Since there will not be a user to authenticate this store has
     * allow all authorization.
     *
     * @param isRead True if the request is to read the form, false if any action
     *               is being taken.
     * @return The authorizer.
     * @throws GeneralException
     */
    @Override
    public Authorizer getAuthorizer(boolean isRead) throws GeneralException {
        return new AllowAllAuthorizer();
    }

    /**
     * Grabs the workflow session object out of the session storage interface.
     *
     * @throws GeneralException
     */
    private void initWorkflowSession() throws GeneralException {
        if (workflowSession == null) {
            if (!sessionStorage.containsKey(WorkflowSessionService.ATT_WORKFLOW_SESSION)) {
                throw new ObjectNotFoundException(Message.error(MessageKeys.ERR_OBJ_NOT_FOUND));
            }

            workflowSession = (WorkflowSession) sessionStorage.get(WorkflowSessionService.ATT_WORKFLOW_SESSION);
        }
    }

}
