/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.quicklink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.object.Script;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.service.MessageDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.PageCodeBase;
import sailpoint.web.WorkItemBean;
import sailpoint.web.WorkflowSessionWebUtil;


/**
 * The QuickLinkLauncher is a service class used to launch quick links.
 */
public class QuickLinkLauncher {

    private static final Log LOG = LogFactory.getLog(QuickLinkLauncher.class);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * An object that encapsulates the results from launching a quick link.
     * 
     * All results will contain the QuickLink action.
     * 
     * Workflow results will differ based on whether identity selection is required prior to
     * launching the workflow.
     *  - If identity selection is not required, the result will contain messages and the next page
     *    (if a work item was generated that needs to be handled by the launcher), as well as have
     *    the workflowLaunched flag set to true.
     *  - If identity selection is required, the result will have selectIdentitiesForWorkflow set
     *    to true and have the quickLinkName set.
     *
     * External link results will contain a redirectUrl.
     *
     * All other quick link results will contain an arguments map that has all quick link arguments.
     */
    public static class QuickLinkLaunchResult {

        // The QuickLink action that was launched.
        private String action;

        // Workflow action - messages resulting from launching a workflow.
        private List<MessageDTO> messages;

        // Workflow action - set to true if a workflow was launched.
        private boolean workFlowLaunched;

        // Workflow action - a string indicating the next page to transition to if launching the
        // workflow resulted in work items that need to be handled by the launcher.
        private String nextPage;

        // Workflow action - The id of the next work item for the user to complete.
        private String nextWorkItemId;

        // Workflow action - the type of the next work item for the user to complete.
        private WorkItem.Type nextWorkItemType;

        // Workflow action - Set to true if identity selection is required before launching a
        // workflow.  When true the workflowName will be non-null.
        private boolean selectIdentitiesForWorkflow;

        // Workflow action - The name of the quick link to launch after identity selection is complete.
        private String quickLinkName;

        // External action - the URL to redirect to.
        private String redirectUrl;

        // "Other" action - arguments to include when navigating to the next action.
        private Map<String,Object> arguments;


        /**
         * Private constructor - use the static creator methods instead.
         *
         * @param  link  The QuickLink that was launched.
         */
        private QuickLinkLaunchResult(QuickLink link) {
            this.action = link.getAction();
            this.quickLinkName = link.getName();
        }

        /**
         * Create a result from launching workflow, which will include the messages and nextPage
         * (if launching produced a work item).
         *
         * @param  link      The QuickLink that was launched.
         * @param  messages  The messages from launching the workflow.
         * @param  nextPage  The nextPage to go to if launching the workflow created a work item.
         *
         * @return The result.
         */
        public static QuickLinkLaunchResult createWorkflowResult(QuickLink link,
                                                                 List<MessageDTO> messages,
                                                                 String nextPage,
                                                                 String nextWorkItemId,
                                                                 WorkItem.Type nextWorkItemType) {
            QuickLinkLaunchResult result = new QuickLinkLaunchResult(link);
            result.messages = messages;
            result.nextPage = nextPage;
            result.nextWorkItemId = nextWorkItemId;
            result.nextWorkItemType = nextWorkItemType;
            result.workFlowLaunched = true;
            return result;
        }

        /**
         * Create a result from launching workflow that requires identity selection.  This will
         * have the selectIdentitiesForWorkflow flag set to true and the quickLinkName set.
         *
         * @param  link  The QuickLink that was launched.
         *
         * @return The result.
         */
        public static QuickLinkLaunchResult createWorkflowIdentitySelectionResult(QuickLink link) {
            QuickLinkLaunchResult result = new QuickLinkLaunchResult(link);
            result.selectIdentitiesForWorkflow = true;
            result.quickLinkName = link.getName();
            return result;
        }

        /**
         * Create a result from navigating to an external link, which will have the redirectUrl set.
         *
         * @param  link  The QuickLink that was launched.
         *
         * @return The result.
         */
        public static QuickLinkLaunchResult createExternalLinkResult(QuickLink link, String url) {
            QuickLinkLaunchResult result = new QuickLinkLaunchResult(link);
            result.redirectUrl = url;
            return result;
        }

        /**
         * Create a result from launching a quick link that is not external or a workflow, which
         * will have the action and arguments set.
         *
         * @param  link  The QuickLink that was launched.
         *
         * @return The result.
         */
        public static QuickLinkLaunchResult createOtherQuickLinkResult(QuickLink link,
                                                                       Map<String,Object> args) {
            QuickLinkLaunchResult result = new QuickLinkLaunchResult(link);
            result.arguments = args;
            return result;
        }

        public String getAction() {
            return action;
        }

        public List<MessageDTO> getMessages() {
            return messages;
        }

        public boolean isWorkFlowLaunched() {
            return workFlowLaunched;
        }

        public String getNextPage() {
            return nextPage;
        }

        public String getNextWorkItemId() {
            return nextWorkItemId;
        }

        public WorkItem.Type getNextWorkItemType() {
            return nextWorkItemType;
        }

        public boolean isSelectIdentitiesForWorkflow() {
            return selectIdentitiesForWorkflow;
        }

        public String getQuickLinkName() {
            return quickLinkName;
        }

        public Map<String,Object> getArguments() {
            return arguments;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private Identity launcher;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public QuickLinkLauncher(SailPointContext context, Identity launcher) {
        this.context = context;
        this.launcher = launcher;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Launch the given QuickLink and return a launch result.
     *
     * @param  link  The QuickLink to launch.
     * @param  wfIdentitySelection  Whether the user should have to select an identity before
     *             launching the workflow if this is a workflow quick link.
     * @param  session  A session Map on which to store values if workflow launching results in
     *                  work items that need to be processed.
     *                  TODO: Revisit this after we implement this type of workflow launch in the
     *                  mobile UI.
     *
     * @return The QuickLinkLaunchResult.
     *
     * @throws GeneralException  If the QuickLink was configurd incorrectly.
     */
    public QuickLinkLaunchResult launch(QuickLink link, boolean wfIdentitySelection,
                                        Map<String,Object> session)
        throws GeneralException {

        QuickLinkLaunchResult result = null;

        // If this is a workflow and we need to select the identities still,
        // return the a token that indicates which workflow to launch.
        if (wfIdentitySelection && QuickLink.ACTION_WORKFLOW.equals(link.getAction())) {
            result = QuickLinkLaunchResult.createWorkflowIdentitySelectionResult(link);
        }
        else if (QuickLink.ACTION_WORKFLOW.equals(link.getAction())) {
            result = this.launchWorkflow(link, launcher.getId(), null, session);
        } 
        else if (QuickLink.ACTION_EXTERNAL.equals(link.getAction())) {
            result = this.handleExternalLink(link);
        } 
        else {
            result = this.handleOtherQuickLink(link);
        }

        return result;
    }

    /**
     * Calculate the argument map for this QuickLink, evaluating any script args
     * on the link.
     */
    private Map<String,Object> calculateArgs(QuickLink link)
        throws GeneralException {
    
        /** An attribute map to be used in the script passed in **/
        Attributes<String,Object> scriptArgs = new Attributes<String,Object>();
        scriptArgs.put("currentUser", this.launcher);

        /** An attribute map to be used in the script passed in **/
        Map<String,Object> allArgs = new HashMap<String,Object>();
        allArgs.putAll(scriptArgs);

        allArgs.put("quickLink", link.getName());

        /** Store any items in the agrument bucket on the session **/
        if (null != link.getArguments()) {
            for (String key : link.getArguments().keySet()) {
                // Do not run label script when executing.
                if (QuickLink.ARG_LABEL_SCRIPT.equals(key)) {
                    continue;
                }
                
                Object object = link.getArguments().get(key);
                if(object instanceof Script) {
                    try {
                        Object result = this.context.runScript((Script) object, scriptArgs);
                        allArgs.put(key, result);
                    } catch(GeneralException ge) {
                        if(LOG.isWarnEnabled()) {
                            LOG.warn("Exception while running script for quicklink: " +
                                     link.getName() + ". Exception: " + ge.getMessage(), ge);
                        }
                    }
                } else {
                    allArgs.put(key, object);
                }
            }
        }

        return allArgs;
    }

    /**
     * If a user clicks on a quicklink (custom) that invokes a workflow, we run
     * the workflow and check to see if there are any work items that we need to
     * process.
     */
    public QuickLinkLaunchResult launchWorkflow(QuickLink link, String identityId,
                                                List<String> identityIds,
                                                Map<String,Object> session)
        throws GeneralException {

        String nextPage = null;
        String nextWorkItemId = null;
        WorkItem.Type nextWorkItemType = null;
        String workflowName = link.getArguments().getString(QuickLink.ARG_WORKFLOW_NAME);
        if (null == workflowName) {
            throw new GeneralException("Workflow QuickLink " + link.getName() + " does not have " +
                                       "required argument: " + QuickLink.ARG_WORKFLOW_NAME);
        }

        LOG.info("Executing Workflow: " + workflowName);             

        Workflow workflow = this.context.getObjectByName(Workflow.class, workflowName);
        if (workflow == null) {
            throw new GeneralException("Failed to launch QuickLink " + link.getName() +
                                       " workflow - no workflow found named: " + workflowName);
        }

        Map<String,Object> allArgs = calculateArgs(link);

        // jsl - bug#15871 before launching the workflow take out
        // the currentUser Identity object that was passed to the
        // argument initialization scripts in the previous loop. 
        // Passing SailPointObjects into workflows causes all sorts
        // of problems if not done carefully, transient workflows 
        // uncovered some of those.
        allArgs.remove("currentUser");
        allArgs.put("currentUserName", this.launcher.getName());

        // A little extra love for the workflow writer.  If we just have an
        // identityId, make a singleton list so they can use the identityIds
        // variable in either case.
        if ((null == identityIds) && (null != identityId)) {
            identityIds = new ArrayList<String>();
            identityIds.add(identityId);
        }

        // Note that we prefix these with "quickLink" to avoid colliding
        // with the common "identityId" variable used in many workflows.
        allArgs.put("quickLinkIdentityId", identityId);
        allArgs.put("quickLinkIdentityIds", identityIds);

        // launch a session
        Workflower wf = new Workflower(this.context);
        WorkflowLaunch wfl = wf.launchSafely(workflow, "QuickLink Launch for identity " + 
                this.launcher.getName(), allArgs);

        WorkflowSession ses = new WorkflowSession();
        ses.setWorkflowLaunch(wfl);
        ses.setWorkflowCase(wfl.getWorkflowCase());
        wfl.setWorkflowCase(null);  
        
        ses.setOwner(this.launcher.getName());
        ses.start(this.context);

        List<MessageDTO> messages = new ArrayList<MessageDTO>();
        String successMessage = link.getArguments().getString(QuickLink.ARG_WORKFLOW_SUCCESS);

        if (!ses.hasWorkItem()) {
            ses.save(this.context);

            WorkflowLaunch launch = ses.getWorkflowLaunch();
            if (launch != null && launch.isFailed()) {
                List<Message> launchMessages =  launch.getMessages();
                for (Message msg : launchMessages) {
                    if (msg.isError()) {
                        messages.add(new MessageDTO(msg));
                    }
                }
            } else {
                addMessages(successMessage, launch.getMessages(), messages);
            }
        }
        else {
            // There is a work item to deal with.
            ses.setReturnPage(PageCodeBase.NAV_OUTCOME_HOME);
            session.put(WorkItemBean.NEXT_PAGE, ses.getNextPage());
            /* IIQETN-4931 Needed to change the reset flag to false to preserve the
             * workflow session and item in case we did set it to true for example
             * by leaving an uncompleted form workitem clicking in the Home link.
             */
            session.put("reset", "false");
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(session);
            nextPage = wfUtil.saveWorkflowSession(ses);

            WorkItem workItem = ses.getWorkItem();
            if (workItem != null) {
                nextWorkItemId = workItem.getId();
                nextWorkItemType = workItem.getType();
            }

            addMessages(successMessage, ses.getLaunchMessages(), messages);
        }

        return QuickLinkLaunchResult.createWorkflowResult(link, messages, nextPage, nextWorkItemId, nextWorkItemType);
    }

    /**
     * Add the given success message to the addTo list of messages if non-null, otherwise add all
     * launchMsgs to the addTo list.
     */
    private static void addMessages(String success, List<Message> launchMsgs, List<MessageDTO> addTo) {
        if (!Util.isNullOrEmpty(success)) {
            addTo.add(new MessageDTO(success, MessageDTO.STATUS_SUCCESS));
        } else {
            for (Message m : Util.safeIterable(launchMsgs)) {
                addTo.add(new MessageDTO(m));
            }
        }
    }

    /**
     * Process this as an external QuickLink to produce a URL to which the user will be redirected.
     *
     * @param  link  The QuickLink to process.
     *
     * @return A result with the redirect URL set to the full URL.
     *
     * @throws GeneralException If the QuickLink does not have a URL argument.
     */
    @SuppressWarnings("unchecked")
    private QuickLinkLaunchResult handleExternalLink(QuickLink link)
        throws GeneralException {

        Map<String,Object> args = calculateArgs(link);

        String url = (String) args.get(QuickLink.ARG_URL);
        if (Util.isNullOrEmpty(url)) {
            throw new GeneralException("The quicklink must have a "+ QuickLink.ARG_URL +
                                       " parameter to be considered a valid external quicklink");
        }

        /** Look for any parameters that we need to append to the string **/
        Map<String,Object> parameters = (Map<String,Object>) args.get(QuickLink.ARG_PARAMETERS);
        if (parameters != null) {
            url += "?";
            String sep = "";

            for (Map.Entry<String,Object> entry : parameters.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    url += sep + key + "=" + value;
                    sep = "&";
                }
                else if (value instanceof Script) {
                    /** They can insert a script as a value in the parameter array **/  
                    try {
                        Object result = this.context.runScript((Script)value, args);
                        if (result instanceof String) {
                            url += sep + key + "=" + result;
                            sep = "&";
                        }
                    } catch(GeneralException ge) {
                        if(LOG.isWarnEnabled()) {
                            LOG.warn("Exception while running script for quicklink: " + link.getName() + ". Exception: " + ge.getMessage(), ge);
                        }
                    }
                }
            }
        }

        return QuickLinkLaunchResult.createExternalLinkResult(link, url);
    }

    /**
     * Process a QuickLink that is not an external quick link or a workflow launch.
     *
     * @param  link  The "other" QuickLink.
     *
     * @return A result with the quick link action and arguments.
     */
    private QuickLinkLaunchResult handleOtherQuickLink(QuickLink link) throws GeneralException {
        return QuickLinkLaunchResult.createOtherQuickLinkResult(link, calculateArgs(link));
    }

    /**
     * Validate that the logged in user is able to execute the given quick link
     * and that they're not trying to game the system.
     *
     * @param  quickLink  The QuickLink to validate.
     * @param  selfService  Whether this is a self service request.
     * @param  dynamicScopes  The names of the user's dynamic scopes.
     *
     * @return True if the quick link is valid to be launched, false otherwise.
     */
    public boolean validateQuickLink(QuickLink quickLink, boolean selfService,
                                     List<String> dynamicScopes)
        throws GeneralException {

        // Someone ostensibly clicked on a quicklink.  Make sure that they
        // have the permissions to have "clicked" this.  Sneaky, sneaky...
        if (null != quickLink) {
            QuickLinksService service =
                new QuickLinksService(this.context, this.launcher, dynamicScopes);

            List<QuickLink> validQLs =
                (selfService) ? service.getSelfQuickLinks() : service.getOthersQuickLinks();
            for (QuickLink ql : validQLs) {
                if (ql.getName().equals(quickLink.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
