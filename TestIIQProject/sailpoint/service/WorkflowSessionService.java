/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityRequestLibrary;

/**
 * A service used to interact with the workflow session which leverages
 * the SessionStorage interface.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class WorkflowSessionService {

    public static final String ATT_WORKFLOW_SESSION = "WorkflowSession";
    /**
     * workflow variable used to keep the workflowSession object in the http session.
     */
    public static final String VAR_WORKFLOW_SESSION_PERSIST = "workflowSessionPersist";

    /**
     * The context.
     */
    private SailPointContext context;

    /**
     * The session storage.
     */
    private SessionStorage sessionStorage;

    /**
     * The workflow session that is retrieved from the session storage.
     */
    private WorkflowSession workflowSession;


    /**
     * Constructor.  Public to allow this to add to the session storage.
     */
    public WorkflowSessionService(SailPointContext context, SessionStorage sessionStorage,
                                  WorkflowSession session) {
        this.context = context;
        this.sessionStorage = sessionStorage;
        this.workflowSession = session;
    }

    /**
     * Construct a WorkflowSessionService that can complete a WorkItem.  This constructor should
     * be used when there is not a WorkflowSession in the session storage yet, for example when
     * using a stand-alone work item page that is resuming a workflow that had been previously
     * been launched.
     *
     * @param context        The context.
     * @param sessionStorage The session storage.
     * @param workItem       The WorkItem to use to create the WorkflowSession.
     */
    public WorkflowSessionService(SailPointContext context, SessionStorage sessionStorage, WorkItem workItem) {
        this(context, sessionStorage, new WorkflowSession(workItem));
    }

    /**
     * Constructs a new instance of WorkflowSessionService.
     *
     * @param context        The context.
     * @param sessionStorage The session storage.
     */
    public WorkflowSessionService(SailPointContext context, SessionStorage sessionStorage) {
        this(context, sessionStorage, (WorkflowSession) sessionStorage.get(ATT_WORKFLOW_SESSION));
    }

    /**
     * Advances the workflow using the workflow session contained in the session storage.
     *
     * @param completer The identity completing the work item.
     * @param proceed   True to proceed through the workflow, false to go back.
     * @return The WorkItemResult.
     * @throws GeneralException
     */
    public WorkItemResult advance(Identity completer, boolean proceed) throws GeneralException {
        if (workflowSession == null) {
            throw new GeneralException("No workflow session to advance");
        }

        // set the completer
        workflowSession.getWorkItem().setCompleter(completer.getDisplayName());

        // advance the workflow
        WorkItem nextWorkItem = workflowSession.advance(context, proceed);

        // clear out any form
        workflowSession.setActiveForm(null);

        // if there is no work item then clear the session otherwise save
        if (nextWorkItem == null && isClearEnabled()) {
            clear();
        } else {
            save();
        }

        WorkItemResult result = new WorkItemResult(nextWorkItem);
        decorateResult(result);
        return result;
    }

    /**
     * Saves the current workflow session into the session storage.
     */
    public void save() {
        sessionStorage.put(ATT_WORKFLOW_SESSION, workflowSession);
    }

    /**
     * Clears the workflow session from the session storage.
     */
    public void clear() {
        sessionStorage.remove(ATT_WORKFLOW_SESSION);
    }

    /**
     * Controlled by the workflow variable {@link WorkflowSessionService#VAR_WORKFLOW_SESSION_PERSIST} if
     * true should clear the workflow session when no work items are left to render.
     * @return if the workflow session should be cleared from the session
     */
    protected boolean isClearEnabled() {
        boolean isSessionPersist = false;
        WorkflowCase wfc = workflowSession.getWorkflowCase();
        if (wfc != null) {
            Workflow wf = wfc.getWorkflow();
            isSessionPersist = (wf.getVariables() != null) ? wf.getVariables().getBoolean(VAR_WORKFLOW_SESSION_PERSIST) : false;
        }
        return !isSessionPersist;
    }
    /**
     * Add information about the current workflow session to the given result, such as the return
     * page and error messages.
     *
     * @param result The WorkItemResult to decorate.
     */
    private void decorateResult(WorkItemResult result) {
        result.setReturnPage(getReturnPage());

        for (Message msg : Util.iterate(getReturnMessages())) {
            result.addMessage(new MessageDTO(msg));
        }
    }

    /**
     * Gets the return page that represents a JSF outcome on the workflow session.
     *
     * @return The return page.
     */
    private String getReturnPage() {
        String page = null;

        if (workflowSession != null) {
            page = workflowSession.getReturnPage();
        }

        return page;
    }

    /**
     * Return the current work item from the session
     *
     * @return
     */
    public WorkItem getCurrentWorkItem() {
        return workflowSession.getWorkItem();
    }

    /**
     * Gets the return messages on the workflow session.
     *
     * @return The messages.
     */
    private List<Message> getReturnMessages() {
        List<Message> messages = null;

        if (workflowSession != null) {
            messages = workflowSession.getReturnMessages();
            if (messages == null) {
                messages = this.workflowSession.getLaunchMessages();
            } else {
                for (Message message : Util.safeIterable(workflowSession.getLaunchMessages())) {
                    messages.add(message);
                }
            }
        }


        return messages;
    }

    /**
     * Create WorkflowResultItem from workflowSession launched
     *
     * @param userContext The usercontext for getting locale/timezone
     * @param throwOnFailure Whether the method should throw when the workflow fails.  Some calls to this want to return
     *                       the workflow result so they can display the messages.
     * @return WorkflowResultItem The workflow result item created by the workflow
     * @throws GeneralException
     */
    public WorkflowResultItem createWorkflowResult(UserContext userContext, boolean throwOnFailure) throws GeneralException {
        WorkflowLaunch launch = workflowSession.getWorkflowLaunch();

        String status = launch == null ? null : launch.getStatus();
        String requestName = this.getIdentityRequestName();
        List<Message> messages = workflowSession.getLaunchMessages();
        if (workflowSession != null && workflowSession.getWorkflowCase() != null) {
            List<Message> msgs = workflowSession.getWorkflowCase().getMessages();
            if (Util.nullSafeSize(msgs) > 0) {
                if (messages == null) {
                    messages = new ArrayList<Message>();
                }
                for (Message m : msgs) {
                    if (!messages.contains(m)) {
                        messages.add(m);
                    }
                }
            }
        }

        // If the launch failed, throw exception
        if ((launch == null || launch.isFailed()) && throwOnFailure) {
            Message msg = new Message(MessageKeys.UI_IDENTITY_WORKFLOW_LAUNCH_FAILED);
            throw new GeneralException(msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
        }
        /* Values that depend on existing workitem default to null */
        WorkItem wfWorkItem = workflowSession.getWorkItem();
        String workItemType = null;
        String workItemId = null;
        if (wfWorkItem != null) {
            workItemType = wfWorkItem.getType().toString();
            workItemId = wfWorkItem.getId().toString();
        }
        IdentityRequest identityRequest = context.getObjectByName(IdentityRequest.class, requestName);
        String requestId = null;

        if (identityRequest != null) {
            requestId = identityRequest.getId();
        }

        WorkflowResultItem result = new WorkflowResultItem(status, requestId, workItemType, workItemId, messages);
        return result;
    }

    /**
     * Returns the name of the identity request for the workflow
     *
     * @return The name of the IdentityRequest
     * @throws GeneralException If unable to get the workflow's task result
     */
    public String getIdentityRequestName() throws GeneralException {
        String identityRequestName = null;
        WorkflowCase workflowCase = this.workflowSession.getWorkflowCase();
        String requestId = null;
        if (workflowCase != null) {
            identityRequestName = (String) workflowCase.get(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
            TaskResult result = workflowCase.getTaskResult(context);
            if (result != null) {
                identityRequestName = (String) result.getAttribute(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
            }
        }
        return identityRequestName;
    }
    /**
     * Removes the leading zeros from an identity request item name
     * @param requestName The name to remove the padding from
     * @return Number with no leading zeros
     */
    public static String stripPadding(String requestName) {
        if ( Util.getString(requestName) != null ) {
            return Util.stripLeadingChar(requestName, '0');
        }
        return requestName;
    }
}
