/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.api;

/**
 * Imports.
 */
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates signoff WorkItems for a given TaskDefinition.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class TaskSignoffGenerator {

    /**
     * The context.
     */
    private SailPointContext _context;

    /**
     * The task definition to generate signoffs for.
     */
    private TaskDefinition _taskDefinition;

    /**
     * The task result.
     */
    private TaskResult _taskResult;

    /**
     * The launcher of the task.
     */
    private String _launcher;

    /**
     * Creates a new instance of TaskSignoffGenerator. The launcher will be taken
     * from the specified task result.
     *
     * @param context The context.
     * @param taskDefinition The task definition.
     * @param taskResult The task result.
     */
    public TaskSignoffGenerator(SailPointContext context, TaskDefinition taskDefinition, TaskResult taskResult) {
        this(context, taskDefinition, taskResult, taskResult.getLauncher());
    }

    /**
     * Creates a new instance of TaskSignoffGenerator.
     *
     * @param context The context.
     * @param taskDefinition The task definition.
     * @param taskResult The task result.
     * @param launcher The task launcher.
     */
    public TaskSignoffGenerator(SailPointContext context, TaskDefinition taskDefinition, TaskResult taskResult, String launcher) {
        _context = context;
        _taskDefinition = taskDefinition;
        _taskResult = taskResult;
        _launcher = launcher;
    }

    /**
     * Determines if signoff is required by the task definition.
     *
     * @return True if signoff is required, false otherwise.
     */
    public boolean isSignoffRequired() {
        WorkItemConfig signoffConfig = getSignoffConfig();

        return null != signoffConfig && !signoffConfig.isDisabled();
    }

    /**
     * Generates signoffs for the identities specified in the task definition if
     * signoff is required.
     *
     * @return The generated work items or an empty list if signoff is not required.
     * @throws GeneralException
     */
    public List<WorkItem> generateSignoffs() throws GeneralException {
        if (!isSignoffRequired()) {
            return new ArrayList<WorkItem>();
        }

        WorkItemConfig signoff = getSignoffConfig();

        List<Identity> owners = signoff.getOwners();

        // If both this and an owner list is specified,
        // assume the rule wins unless it returns null.
        List<Identity> identitiesFromRule = runSignoffOwnerRule(signoff.getOwnerRule());
        if (null != identitiesFromRule) {
            owners = identitiesFromRule;
        }

        // Filter duplicate owners before we start creating
        // work items.  Note that this won't handle duplication
        // caused by forwarding to the same person.
        List<Identity> filteredOwners = removeDuplicateOwners(owners);

        List<WorkItem> items = new ArrayList<WorkItem>();
        Signoff signoffState = null;

        for (Identity owner : filteredOwners) {
            WorkItem item = openSignoffWorkItemForOwner(signoff, owner);
            if (item != null) {
                // Flesh out an incomplete Signoff object so we have
                // something to show when viewing the task results.
                // This is also necessary to associate Signatorys with
                // their WorkItems.
                if (signoffState == null) {
                    signoffState = new Signoff();
                    _taskResult.setSignoff(signoffState);
                }

                // note that this requires that the workitem have a persistent id
                signoffState.add(item);

                items.add(item);
            }
        }

        if (items.size() > 0) {
            _taskResult.setPendingSignoffs(items.size());
        }

        return items;
    }

    /**
     * Gets the signoff config specified in the task definition.
     *
     * @return The signoff config or null if none is specified.
     */
    private WorkItemConfig getSignoffConfig() {
        return _taskDefinition.getSignoffConfig();
    }

    /**
     * Runs the specified signoff owner rule.
     *
     * @param rule The signoff owner rule.
     * @return The list of signoff owners or null if rule is null.
     * @throws GeneralException
     */
    private List<Identity> runSignoffOwnerRule(Rule rule) throws GeneralException {
        if (null == rule) {
            return null;
        }

        Map<String,Object> args = new HashMap<String,Object>();
        args.put("taskDefinition", _taskDefinition);
        args.put("taskResult", _taskResult);

        Object o = _context.runRule(rule, args);

        return ObjectUtil.getObjects(_context, Identity.class, o);
    }

    /**
     * Removes any duplicate identities from the specified signoff owners list.
     *
     * @param owners The signoff owners.
     * @return The filtered list of signoff owners.
     */
    private List<Identity> removeDuplicateOwners(List<Identity> owners) {
        List<Identity> filteredOwners = new ArrayList<Identity>();
        for (Identity owner : Util.safeIterable(owners)) {
            if (!filteredOwners.contains(owner)) {
                filteredOwners.add(owner);
            }
        }

        return filteredOwners;
    }

    /**
     * Opens a signoff work item for the specified owner.
     *
     * @param signoffConfig The signoff config.
     * @param owner The owner of the work item.
     * @return The work item.
     * @throws GeneralException
     */
    private WorkItem openSignoffWorkItemForOwner(WorkItemConfig signoffConfig, Identity owner) throws GeneralException {
        WorkItem item = new WorkItem();
        item.setType(WorkItem.Type.Signoff);
        item.setRenderer("signoff.xhtml");

        // we will be notified whenever the item is saved
        item.setHandler(TaskManager.class.getName());

        item.setOwner(owner);

        // if we're a background task this won't have anything, think!
        // !! need to consistently use TaskSchedule.ARG_LAUNCHER
        // in arg maps for this, See Interrogator.getWorkItemRequester,
        // should factor out a util?
        Identity requester = ObjectUtil.getOriginator(_context, _launcher, null, _taskResult);
        item.setRequester(requester);

        String request = "Signoff result of task: " + _taskDefinition.getName();
        item.setDescription(request);

        item.setTargetClass(TaskResult.class);
        item.setTargetId(_taskResult.getId());
        item.setTargetName(_taskResult.getName());

        // arguments for rendering the notification
        Map<String,Object> vars = new HashMap<String,Object>();

        String ownerName = owner.getDisplayableName();
        vars.put("ownerName", ownerName);

        // name used prior to 5.1
        // sigh, would really like to have passed the Identity here
        vars.put("owner", ownerName);

        vars.put("objectName", _taskResult.getName());

        if (requester != null) {
            vars.put("requesterName", requester.getDisplayableName());
        }

        String objectType = TaskDefinition.Type.Report.equals(_taskDefinition.getType()) ? "report" : "task";
        vars.put("objectType", objectType);

        Workflower wf = new Workflower(_context);

        // TODO: Formerly looked for a template configured in
        // Configuration.SIGNOFF_EMAIL_TEMPLATE for the notification,
        // now we require that it be set in the WorkItemConfig.
        // If we still want a default we'll have to modify the
        // WorkItemConfig before passing it down!

        wf.open(item, signoffConfig, vars, null);

        return item;
    }
}
