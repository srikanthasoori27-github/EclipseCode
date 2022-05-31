/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Scope;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has access to the specified TaskResult.
 * 
 * @author jeff.upton
 */
public class TaskResultAuthorizer implements Authorizer {

    private TaskResult _result;
    private String _workItemId;

    public TaskResultAuthorizer(TaskResult result) {
        this(result, null);
    }

    public TaskResultAuthorizer(TaskResult result, String workItemId) {
        _result = result;
        _workItemId = workItemId;
    }

    public void authorize(UserContext userContext) throws GeneralException {
        SailPointContext context = userContext.getContext();
        Identity loggedInUser = userContext.getLoggedInUser();

        // System Admin has immediate access. 
        if (_result == null ||
                Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities())) {
            return;
        } 

        // if the user owns the object, they are approved...
        if (_result.getOwner() != null && loggedInUser.equals(_result.getOwner())){
            return;
        }

        // If the user launched the task, they are approved... 
        if (Util.nullSafeEq(_result.getLauncher(), loggedInUser.getName()) ||
                Util.nullSafeEq(_result.getLauncher(), loggedInUser.getId())) {
            return;
        }

        // If user is a signer on the task, they are approved... 
        if (isSignatory(loggedInUser)) {
            return;
        }

        // If the user has ReadTaskResults, they have access. This may need a revisit should
        // we come up with some differentiation between reading and writing to a TaskResult as
        // this right implies read-only access. However, right now there isn't much in the way of
        // editing a TaskResult. For all effective purposes right now, users with this right are
        // authorized to the TaskResult
        if (userContext.getLoggedInUserRights().contains(SPRight.ReadTaskResults)) {
            return;
        }

        // FullAccessTaskManagement will have access
        //TODO: What about FullAccessTask?
        if (userContext.getLoggedInUserRights().contains(SPRight.FullAccessTaskManagement)) {
            return;
        }

        if (_workItemId != null) {

            // Check if the user owns a workitem referencing this TaskResult
            QueryOptions ops = new QueryOptions(Filter.eq("id", _workItemId));
            ops.add(Filter.eq("owner.name", userContext.getLoggedInUserName()));
            ops.add(Filter.eq("targetId", _result.getId()));
            if (context.countObjects(WorkItem.class, ops) > 0)
                return;

            // Check to see if the user is in a workgroup that owns the workitem
            ops = new QueryOptions(Filter.eq("id", _workItemId));
            ops.add(Filter.eq("targetId", _result.getId()));
            ops.add(Filter.join("owner.id", "Identity.workgroups.id"));
            ops.add(Filter.eq("Identity.name", userContext.getLoggedInUserName()));
            if (context.countObjects(WorkItem.class, ops) > 0)
                return;

        }

        // If the user doesn't have the privilege for this task, they are not approved
        TaskDefinition def = _result.getDefinition();
        if (!sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(), def.getEffectiveRights())) { 
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_PRIVILEGE_UNAUTHORIZED_ACCESS));  
        }

        if (userContext.isScopingEnabled()) {
            // If there is no scope and unscoped object is not globally accessible, then only the owner can see this task
            if (_result.getAssignedScope() == null) {
                boolean unscopedGlobal = context.getConfiguration().getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, true);
                if(!unscopedGlobal) {
                    throw new UnauthorizedAccessException();
                }
            } 
            else {
                // If user is authorized to access the/a scope to which the task result object belongs, then they have access to the task result
                List<Scope> controlledScopes =
                        loggedInUser.getEffectiveControlledScopes(context.getConfiguration());
                if (controlledScopes == null || !controlledScopes.contains(_result.getAssignedScope())) {
                    throw new UnauthorizedAccessException();
                }
            }
            // If Task result has no rights associated with it, 'hasAccess()' check will pass. Without any mitigating scoping privileges the user should not be approved. 
        } else if (Util.isEmpty(def.getEffectiveRights())) { 
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_REPORT_UNAUTHORIZED_ACCESS)); 
        } 
    }

    private boolean isSignatory(Identity loggediIdentity) {

        if (_result.getSignoff() == null) {
            return false;
        }

        return (_result.getSignoff().find(loggediIdentity.getName()) != null);
    }

}
