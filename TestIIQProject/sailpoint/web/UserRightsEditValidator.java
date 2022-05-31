/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Helper class to  validate that the logged in user is allowed to add capabilities and/or authorized scopes to
 * the given identity or workgroup
 */
public class UserRightsEditValidator {

    public interface UserRightsEditValidatorContext {
        /**
         * @return List of names of capabilities being saved
         * @throws GeneralException
         */
        List<String> getNewCapabilities() throws GeneralException;

        /**
         * @return List of IDs of scopes being saved
         * @throws GeneralException
         */
        List<String> getNewControlledScopes() throws GeneralException;

        /**
         * @return List of names of capabilities already existing on the object
         * @throws GeneralException
         */
        List<String> getExistingCapabilities() throws GeneralException;

        /**
         * @return List of IDs of scopes already controlled by the object
         * @throws GeneralException
         */
        List<String> getExistingControlledScopes() throws GeneralException;

        /**
         * @return Name of SPRight that allows a user to set the capabilities, or null if none required
         */
        String getCapabilityRight();

        /**
         * @return Name of SPRight that allows a user to set the controlled scopes, or null if none required
         */
        String getControlledScopeRight();
    }

    private UserContext userContext;
    private UserRightsEditValidatorContext validatorContext;

    public UserRightsEditValidator(UserContext userContext, UserRightsEditValidatorContext validatorContext) {
        this.userContext = userContext;
        this.validatorContext = validatorContext;
    }

    /**
     * Validates the capabilities and authorized scopes for the target identity against the logged in user
     * @return List of error messages, indicate some validation error. Empty list indicates no validation error.
     * @throws GeneralException
     */
    public List<Message> validate() throws GeneralException {
        List<Message> errors = new ArrayList<>();
        validateCapabilities(errors);
        validateControlledScopes(errors);
        return errors;
    }

    private void validateCapabilities(List<Message> errors) throws GeneralException {
        // System admins can do whatever they want
        List<Capability> loggedInUserCapabilities = this.userContext.getLoggedInUserCapabilities();
        if (Capability.hasSystemAdministrator(loggedInUserCapabilities)) {
            return;
        }
        
        List<String> existingCapabilities = this.validatorContext.getExistingCapabilities();
        List<String> newCapabilities = this.validatorContext.getNewCapabilities();
        // If new capabilities are the same as existing, no error. If new capabilities is null then the list was never
        // initialized because the user does not have the rights to set capabilities. This means that nothing changed.
        if (newCapabilities == null || (Util.isEmpty(existingCapabilities) && Util.isEmpty(newCapabilities))
                || Util.orderInsensitiveEquals(existingCapabilities, newCapabilities)) {
            return;
        }

        //Something has changed, so authorize the right
        if (!Util.isNothing(this.validatorContext.getCapabilityRight()) && !AuthorizationUtility.isAuthorized(this.userContext, new RightAuthorizer(this.validatorContext.getCapabilityRight()))) {
            errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_EDIT_UNAUTHORIZED_CAPABILITY));
            return;
        }

        List<String> unmatchedCapabilities = getUnmatchedCapabilities();
        // If all the capabilites match the logged in user, no error
        if (Util.isEmpty(unmatchedCapabilities)) {
            return;
        }

        // remove existing capabilities from the list of unmatched capabilites, if they are already set let them through
        if (!Util.isEmpty(existingCapabilities)) {
            unmatchedCapabilities.removeAll(existingCapabilities);
        }

        // IF all the unmatched capabilies already existed, all good.
        if (Util.isEmpty(unmatchedCapabilities)) {
            return;
        }

        // Otherwise we have a problem
        errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_EDIT_MISSING_CAPABILITY, Util.listToCsv(getCapabilityDisplayNames(unmatchedCapabilities))));
    }

    private List<String> getUnmatchedCapabilities() throws GeneralException {
        List<String> invalidCapabilities = new ArrayList<>();
        List<Capability> loggedInUserCapabilities = this.userContext.getLoggedInUserCapabilities();
        for (String capability : Util.iterate(this.validatorContext.getNewCapabilities())) {
            if (!Capability.hasCapability(capability, loggedInUserCapabilities)) {
                invalidCapabilities.add(capability);
            }
        }

        return invalidCapabilities;
    }

    private List<String> getCapabilityDisplayNames(List<String> capabilityNames) throws GeneralException {
        QueryOptions options = new QueryOptions();
        options.add(Filter.in("name", capabilityNames));
        Iterator<Object[]> displayNames = this.userContext.getContext().search(Capability.class, options, "displayName");
        List<String> capabilityDisplayNames = new ArrayList<>();
        while (displayNames.hasNext()) {
            String displayName = (String)displayNames.next()[0];
            capabilityDisplayNames.add(new Message(displayName).getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()));
        }

        return capabilityDisplayNames;
    }

    private void validateControlledScopes(List<Message> errors) throws GeneralException {
        if (!this.userContext.isScopingEnabled()) {
            return;
        }

        // System admins can do anything
        if (Capability.hasSystemAdministrator(this.userContext.getLoggedInUserCapabilities())) {
            return;
        }

        List<String> existingControlledScopes = this.validatorContext.getExistingControlledScopes();
        List<String> newControlledScopes = this.validatorContext.getNewControlledScopes();
        // If new scopes are the same as existing, no error. If the new controlled scopes list is null then the list was never
        // initialized because the user doesn't have rights to add them and nothing has changed
        if (newControlledScopes == null || (Util.isEmpty(existingControlledScopes) && Util.isEmpty(newControlledScopes))
                || Util.orderInsensitiveEquals(existingControlledScopes, newControlledScopes)) {
            return;
        }

        //Something has changed, so authorize the right
        if (!Util.isNothing(this.validatorContext.getControlledScopeRight()) && !AuthorizationUtility.isAuthorized(this.userContext, new RightAuthorizer(this.validatorContext.getControlledScopeRight()))) {
            errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_EDIT_UNAUTHORIZED_SCOPE));
            return;
        }

        List<String> unmatchedScopes = getUnmatchedScopes();
        // All the scopes match the logged in user, so all good.
        if (Util.isEmpty(unmatchedScopes)) {
            return;
        }

        // Remove existing scopes from unmatched, since they are already set they can continue
        if (!Util.isEmpty(existingControlledScopes)) {
            unmatchedScopes.removeAll(existingControlledScopes);
        }

        if (Util.isEmpty(unmatchedScopes)) {
            return;
        }

        // Still have unmatched scopes left, error.
        errors.add(Message.error(MessageKeys.ERR_USER_RIGHTS_EDIT_MISSING_SCOPE, Util.listToCsv(getControlledScopeDisplayNames(unmatchedScopes))));
    }

    private List<String> getUnmatchedScopes() throws GeneralException {
        if (!this.userContext.isScopingEnabled()) {
            return null;
        }

        List<String> invalidScopes = new ArrayList<>();
        List<String> loggedInUserScopes = ObjectUtil.getObjectIds(this.userContext.getLoggedInUser().getEffectiveControlledScopes(Configuration.getSystemConfig()));
        for (String scope: Util.iterate(this.validatorContext.getNewControlledScopes())) {
            if (!Util.nullSafeContains(loggedInUserScopes, scope)) {
                invalidScopes.add(scope);
            }
        }

        return invalidScopes;
    }

    private List<String> getControlledScopeDisplayNames(List<String> controlledScopes) throws GeneralException {
        QueryOptions options = new QueryOptions();
        options.add(Filter.in("id", controlledScopes));
        Iterator<Object[]> displayNames = this.userContext.getContext().search(Scope.class, options, "displayName");
        List<String> scopeDisplayNames = new ArrayList<>();
        while (displayNames.hasNext()) {
            String displayName = (String)displayNames.next()[0];
            scopeDisplayNames.add(new Message(displayName).getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()));
        }

        return scopeDisplayNames;
    }

    /**
     * @return true if any capability or authorized scope on the target identity is not held by the logged in user.
     * @throws GeneralException
     */
    public boolean hasUnmatchedPrivileges() throws GeneralException {
        if (Capability.hasSystemAdministrator(this.userContext.getLoggedInUserCapabilities())) {
            return false;
        }

        return Util.size(getUnmatchedCapabilities()) > 0 || Util.size(getUnmatchedScopes()) > 0;
    }
}
