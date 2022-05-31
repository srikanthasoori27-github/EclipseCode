package sailpoint.rest.ui.identitypreferences;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.SystemConfigOptionsAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Configuration;
import sailpoint.rest.BaseResource;
import sailpoint.service.MessageService;
import sailpoint.service.identity.ForwardingInfoDTO;
import sailpoint.service.identitypreferences.IdentityPreferencesService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Resource for identity edit preferences page
 */
@Path("identityPreferences")
public class IdentityPreferencesResource extends BaseResource {

    public static final String CURRENT_PASSWORD = "currentPassword";
    public static final String NEW_PASSWORD = "newPassword";
    public static final String CONFIRM_PASSWORD = "confirmPassword";

    /**
     * Constructor
     */
    public IdentityPreferencesResource() {
        super();
    }

    /**
     * Return the identity preferences config
     * @return Map of identity preferences config values
     */
    @GET @Path("/config")
    public Map<String, Object> getIdentityPreferencesConfig() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Map<String, Object> configs = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser()).getConfig(this.isSsoAuthenticated());
        return configs;
    }

    /**
     * Return the identity forwarding preferences for the logged in user
     * @return ForwardingInfoDTO DTO of forwarding preferences info
     */
    @GET @Path("/forwarding")
    public ForwardingInfoDTO getIdentityForwardingPreferences() throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.SHOW_EDIT_FORWARDING, MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        return preferencesService.getIdentityForwardingPreferences();
    }

    /**
     * Return the identity forwarding preferences for the logged in user
     * @return ForwardingInfoDTO DTO of forwarding preferences info
     */
    @PUT
    @Path("/forwarding")
    public Response updateIdentityForwardingPreferences(Map<String, Object> data) throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.SHOW_EDIT_FORWARDING, MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        List<String> validationErrors = preferencesService.updateIdentityForwardingPreferences(data);
        return validationErrors.size() > 0 ? Response.status(Response.Status.BAD_REQUEST).entity(validationErrors).build() : Response.ok().build();
    }

    /**
     * Return the system user attributes. Only show if logged in user is system user
     * @return Map<String, Object> map of system user attributes
     */
    @GET @Path("/systemUserAttributes")
    public Map<String, Object> getSystemUserAttributes() throws GeneralException {
        if (!getLoggedInUser().isProtected()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        }
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        return preferencesService.getSystemUserAttributes();
    }

    /**
     * Set the system user attributes. Only if logged in user is system user
     * @return Map<String, Object> map of system user attributes
     */
    @PUT @Path("/systemUserAttributes")
    public Response setSystemUserAttributes(Map<String, Object> data) throws GeneralException {
        if (!getLoggedInUser().isProtected()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        }
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        preferencesService.setSystemUserAttributes(data);
        return Response.ok().build();
    }

    /**
     * Update the password for logged in user. Authorize against sys config SHOW_CHANGE_PASSWORD_TAB
     * @param data map containing input data
     * @return Response success or list of error messsages
     * @throws GeneralException
     */
    @POST
    @Path("/updatePassword")
    public Response updatePassword(Map<String, String> data) throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.SHOW_CHANGE_PASSWORD_TAB, MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));

        // dont allow if SSO authenticated
        if (isSsoAuthenticated()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        }

        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());

        String currentPassword = data.get(CURRENT_PASSWORD);
        String newPassword = data.get(NEW_PASSWORD);
        String confirmPassword = data.get(CONFIRM_PASSWORD);

        List<Message> errors = preferencesService.updatePassword(currentPassword, newPassword, confirmPassword);

        Response response = Response.ok().build();

        if (errors.size() > 0) {
            MessageService messageService = new MessageService(this);
            List<String> errorMessages =  messageService.getLocalizedMessages(errors);
            response = Response.status(Response.Status.BAD_REQUEST).entity(errorMessages).build();
        }

        return response;
    }
    
    /**
     * Return the user preferences.
     * @return Map<String, Object> map of user preferences
     */
    @GET @Path("/userPreferences")
    public Map<String, Object> getUserPreferences() throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.ALLOW_DISABLE_NOTIFICATIONS, true, MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        return preferencesService.getUserPreferences();
    }

    /**
     * Set the user preferences.
     * @return Map<String, Object> map of user preferences
     */
    @PUT @Path("/userPreferences")
    public Response setUserPreferences(Map<String, Object> data) throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.ALLOW_DISABLE_NOTIFICATIONS, true, MessageKeys.UI_EDIT_PREFERENCES_UNAUTHORIZED_USER_EXCEPTION));
        IdentityPreferencesService preferencesService = new IdentityPreferencesService(this.getContext(), this.getLoggedInUser());
        preferencesService.setUserPreferences(data);
        return Response.ok().build();
    }

}