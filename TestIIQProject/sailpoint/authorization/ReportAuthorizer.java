/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportAuthorizer implements Authorizer {

	TaskDefinition _definition;
    TaskResult _result;
    boolean _edit;

	public ReportAuthorizer(TaskDefinition definition, boolean isEdit) {
		_definition = definition;
        _edit = isEdit;
	}

    public ReportAuthorizer(TaskResult result) {
        _result = result;
    }

	public void authorize(UserContext userContext) throws GeneralException {

		SailPointContext context = userContext.getContext();
        Identity loggedInUser = userContext.getLoggedInUser();

        // SystemAdmin can see and edit everything, including report templates.
        if (Capability.hasSystemAdministrator(loggedInUser.getCapabilityManager().getEffectiveCapabilities()) ) {
            return;
        }

        // A report template cannot be edited except for the System Administrator.
        if (_edit && _definition != null && _definition.isTemplate()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_EDIT_REPORT_TEMPLATE_UNAUTHORIZED_ACCESS));
        }

        // If the user is the owner of the report's task definition, they are approved.
        if (_definition != null && _definition.getOwner() != null &&
			    Util.nullSafeEq(userContext.getLoggedInUserName(), _definition.getOwner().getName())) {

			return;
		}

        // If the user is the owner of the report object itself, they are approved.
        if (_result != null && _result.getOwner() != null &&
                Util.nullSafeEq(userContext.getLoggedInUserName(), _result.getOwner().getName())) {
            return;
        }

        // People assigned to Sign Off a report must be able to access it.
        if(_result != null && _result.getSignoff() != null) {
            List<Signoff.Signatory> signatories = _result.getSignoff().getSignatories();
            if(signatories != null) {
                for(Signoff.Signatory signatory : signatories) {
                    if(Util.nullSafeEq(userContext.getLoggedInUserName(), signatory.getName())) {
                        return;
                    }
                }
            }
        }
        
        // If the user does not have the necessary rights, they are not approved.
        if (_result != null) {
            TaskDefinition def = _result.getDefinition();
            if (!sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(), def.getEffectiveRights()) || Util.isEmpty(def.getEffectiveRights())) {
                throw new UnauthorizedAccessException(new Message(MessageKeys.UI_PRIVILEGE_UNAUTHORIZED_ACCESS));
            }

            if (userContext.isScopingEnabled()) {
                // If there is no scope and unscoped object is not globally accessable, then only the owner can see this task
                if (_result.getAssignedScope() == null) {
                    boolean isUnscopedGloballyAccessible = context.getConfiguration().getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, true);
                    if (isUnscopedGloballyAccessible) {
                        return;
                    }
                } else {
                    // If user is authorized to access the scope or one of the scopes to which the report result object belongs, then they have access to the report result.
                    List<Scope> controlledScopes = loggedInUser.getEffectiveControlledScopes(context.getConfiguration());
                    if (controlledScopes != null && controlledScopes.contains(_result.getAssignedScope())) {
                        return;
                    }
                }
                // If report result has no rights associated with it, 'hasAccess()' check will pass. Without any mitigating scoping privileges the user should not be approved.
            } else {
                return;
            }
        }

        // if we didnt return earlier then no access allowed otherwise everyone has access
        throw new UnauthorizedAccessException(new Message(MessageKeys.UI_REPORT_UNAUTHORIZED_ACCESS));
	}
}