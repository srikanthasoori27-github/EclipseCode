package sailpoint.rest.ui;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.PasswordGenerationAuthorizer;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QuickLink;
import sailpoint.object.Application.Feature;
import sailpoint.rest.BaseResource;
import sailpoint.service.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.link.LinkDTO;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LinkResource extends BaseResource {
    private final LinkListResource parent;
    private final String quickLinkName;
    private final String action;
    private final String identityId;
    private final Link link;

    public LinkResource(LinkListResource parent, Link link, String quickLinkName, String action, String identityId) {
        super(parent);
        this.parent = parent;
        this.link = link;
        this.quickLinkName = quickLinkName;
        this.action = action;
        this.identityId = identityId;
    }

    @GET
    public Map<String, Object> getAccountDetails(@PathParam("linkId") String linkId) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS, QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        LinkService service = new LinkService(getContext());
        return service.getAccountDetails(linkId);
    }

    /**
     * REST endpoint for refreshing the status of a link
     * @return LinkDTO The updated representation of the link
     * @throws GeneralException
     */

    @GET
    @Path("refresh")
    public Response refreshLink() throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);
        ManageAccountService service = getAccountService();
        boolean supportsManageExistingAccounts = service.isAllowManageExistingAccounts(getLoggedInUserDynamicScopeNames());
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(LinkListResource.ACCOUNT_LINK_COL_CONFIG);
        LinkListService llService = new LinkListService(identityId, getContext(), parent, selector);
        try {
            return Response.ok(llService.refreshLinkStatus(this.link.getId(), supportsManageExistingAccounts)).build();
        } catch(GeneralException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex).build();
        }
    }

    /**
     * REST endpoint for submitting password change requests.  When the passwordservice encounters an error
     * while trying to change the password, it will throw a PasswordChangeException.  If that is thrown,
     * we return a BadRequestDTO to the user with the list of PasswordChangeErrors
     *
     * @param data Map that contains the new password and optionally the current password
     * @return A Response if the change is successful, otherwise a BadRequestDTO
     */
    @POST
    @Path("changePassword")
    public Response changePassword(Map<String, String> data) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        String currentPassword = (String)data.get(ProvisioningPlan.ATT_CURRENT_PASSWORD);
        String newPassword = (String)data.get(ManagePasswordService.NEW_PASSWORD);
        if(Util.isNullOrEmpty(newPassword)) {
            throw new InvalidParameterException("New password must be provided.");
        }

        // Whether we are changing the password for another person
        boolean isSelf = identityId.equalsIgnoreCase(getLoggedInUser().getId());

        //IIQETN-4792 :- Verifying if the selected application is requiring current password.
        if (Util.isNullOrEmpty(currentPassword)) {
            String features = link.getApplication().getFeaturesString();
            boolean isCurrentPassRequired = false;
            if (features != null) {
                isCurrentPassRequired = features.indexOf(Feature.CURRENT_PASSWORD.toString()) >= 0;
            }
            if (isCurrentPassRequired && isSelf) {
                throw new GeneralException("Current password must be provided.");
            }
        }

        ManagePasswordService passwordService = getPasswordService();
        try {
            PasswordChangeResultItem result = passwordService.submitChangePasswordRequest(link, currentPassword, newPassword, isSelf);
            return Response.ok(result).build();
        } catch(ManagePasswordService.PasswordChangeException ex) {
            MessageService messageService = new MessageService(this);
            // Instead of displaying the actual error message from the connector, show the generic message instead. This is to
            // prevent possibly secure info to the ui that we get from the connector
            List messages = ex.getMessages();
            if(!ex.isConstraintViolation()) {
                messages = messageService.getLocalizedMessages(Arrays.asList(new Message(MessageKeys.UI_MANAGE_PASSWORDS_GENERIC_ERROR)));
            }
            LinkDTO linkDTO = new LinkDTO(link);
            linkDTO.setPasswordChangeDate(ex.getRequestDate());
            PasswordChangeError passwordChangeError = new PasswordChangeError(linkDTO, messages, ex.isConstraintViolation());
            return Response.status(Response.Status.BAD_REQUEST).entity(passwordChangeError).build();
        }
    }

    /**
     * REST endpoint for submitting generated password change requests         *
     */
    @POST
    @Path("generatePassword")
    public Response generatePassword() throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        authorize(new PasswordGenerationAuthorizer());
        ManagePasswordService passwordService = getPasswordService();

        // Whether we are changing the password for another person
        boolean isSelf = identityId.equalsIgnoreCase(getLoggedInUser().getId());

        try {
            PasswordChangeResultItem result = passwordService.submitGeneratePasswordRequest(link, isSelf);
            return Response.ok(result).build();
        } catch(ManagePasswordService.PasswordChangeException ex) {
            MessageService messageService = new MessageService(this);
            LinkDTO linkDTO = new LinkDTO(link);
            linkDTO.setPasswordChangeDate(ex.getRequestDate());
            PasswordChangeError passwordChangeError = new PasswordChangeError(linkDTO, ex.getMessages(), ex.isConstraintViolation());
            return Response.status(Response.Status.BAD_REQUEST).entity(passwordChangeError).build();
        }
    }

    /**
     * Build the password service
     * @return A password service bound to the requester, requestee, and quicklink
     * @throws GeneralException If no quicklink is found
     */
    private ManagePasswordService getPasswordService() throws GeneralException {
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, quickLinkName);
        if(quickLink == null) {
            throw new IllegalArgumentException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new ManagePasswordService(quickLink, getLoggedInUser(), getRequestee(), ticketApp, externalSource, getContext(), this);
    }

    /**
     * Build the acount service
     * @return A acount service bound to the requester, requestee, and quicklink
     * @throws GeneralException If no quicklink is found
     */
    private ManageAccountService getAccountService() throws GeneralException {
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, quickLinkName);
        if(quickLink == null) {
            throw new InvalidParameterException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new ManageAccountService(quickLink, getLoggedInUser(), getRequestee(), ticketApp, externalSource, getContext(), this);
    }

    /**
     * Return the identity we're operating on.
     * @return Identity
     */
    private Identity getRequestee() throws GeneralException {
        return getContext().getObjectById(Identity.class, this.identityId);
    }

    /**
     * Authorize by quick link name, default to quick link action.  Accepts a list of actions because
     * the detail endpoint is used by both manage account and manage password quicklinks
     * @param actions Array of actions to authorize against
     */
    private void authorizeByQuickLink(String... actions) throws GeneralException{
        /* Create an authorizer for each action */
        List<Authorizer> authorizers = new ArrayList<Authorizer>();
        for (String action : actions) {
            authorizers.add(new LcmRequestAuthorizer(getRequestee()).setQuickLinkName(quickLinkName).setAction(action));
        }
        /* If any of the authorizers pass validate ()*/
        Authorizer authorizer = CompoundAuthorizer.or(authorizers.toArray(new Authorizer[authorizers.size()]));
        authorize(authorizer);
    }

}
