package sailpoint.rest.ui.quicklink;

import sailpoint.api.Lockinator;
import sailpoint.api.PasswordPolicyException;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.*;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.IdentityEffectiveAccessListResource;
import sailpoint.rest.IdentityEntitlementRoleResource;
import sailpoint.rest.IdentityEntitlementResource;
import sailpoint.rest.ui.LinkListResource;
import sailpoint.service.*;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.ProvisioningPolicyFormStore;
import sailpoint.service.identity.ForwardingInfoDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Map;

/**
 * QuickLink Identity Resource
 */
public class IdentityResource extends BaseResource {

    private static Log _log = LogFactory.getLog(IdentityResource.class);
    private final String identityId;
    private final IdentitiesResource identitiesResource;
    public static final String IDENTITY_UNAUTHORIZED_UNLOCK  = "ui_identity_unauthorized_unlock";
    public static final String IDENTITY_UNAUTHORIZED_FORWARD  = "ui_identity_unauthorized_forward";
    public static final String PASSWORD_PARAM = "password";
    public static final String RESET_REQUIRED_PARAM = "resetRequired";

    public IdentityResource(String identityId, IdentitiesResource identitiesResource) {
        super(identitiesResource);
        this.identityId = identityId;
        this.identitiesResource = identitiesResource;
    }

    /**
     * ex: /ui/rest/quicklinks/{quickLinkId}/identities/{identityId}/links
     * @return {LinkListResource} return resource for link list
     * @throws GeneralException
     */
    @Path("links")
    public LinkListResource getLinkListResource() throws GeneralException{
        return new LinkListResource(identitiesResource, identityId, identitiesResource.getQuickLinkAction(), identitiesResource.getQuickLinkName());
    }


    /**
     * Look up identity attributes using LCMIdentityDetailsService.
     * @return identity attributes DTO
     * @throws GeneralException
     */
    @GET
    @Path("attributes")
    public IdentityAttributesDTO getIdentityAttributes() throws GeneralException {
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY));
        LCMIdentityDetailsService svc = new LCMIdentityDetailsService(identity, identitiesResource.getQuickLinkDTO(), this);
        IdentityAttributesDTO attrs = svc.getIdentityAttributesDTO(getLocale(), getUserTimeZone(),
                UIConfig.getUIConfig().getIdentityViewAttributesList());
        return attrs;
    }

    /**
     * Returns localized login password policy
     * @return localized login password policy
     */
    @GET
    @Path("loginConstraints")
    public List<String> getLoginConstraints() throws GeneralException {
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY), new RightAuthorizer(SPRight.SetIdentityPassword));
        ChangeLoginPasswordService changePasswordService = new ChangeLoginPasswordService(this.getContext(), this);
        return changePasswordService.getConstraints();
    }

    /**
     * Submits a change to the Identity's login password
     *  PUT parameters
     *      password - the new password
     *      resetRequired - if the user must reset their password on next login
     * @param data Form data
     * @return Result of Update Identity workflow
     */
    @PUT
    @Path("loginPassword")
    public Response changeIIQPassword(Map<String, Object> data) throws GeneralException {
        /* Authentication stuff */
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY), new RightAuthorizer(SPRight.SetIdentityPassword));
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, identitiesResource.getQuickLinkName());
        /* unpack post parameters */
        String password = (String) data.get(PASSWORD_PARAM);
        boolean requireReset = Util.otob(data.get(RESET_REQUIRED_PARAM));
        /* Submit the change */
        ChangeLoginPasswordService changePasswordService = new ChangeLoginPasswordService(this.getContext(), this);
        WorkflowResultItem result = null;
        try {
            result = changePasswordService.changePassword(identity, password, requireReset);
        } catch (PasswordPolicyException policyException) {
            MessageService messageService = new MessageService(this);
            PasswordChangeError passwordChangeError = new PasswordChangeError(null, messageService.getLocalizedMessages(policyException.getAllMessages()),true);
            return Response.status(Response.Status.BAD_REQUEST).entity(passwordChangeError).build();
        }
        return Response.ok(result).build();
    }

    /**
     * 
     * @return Identity
     * @throws GeneralException
     */
    private Identity getIdentity() throws GeneralException {
        return getContext().getObjectById(Identity.class, identityId);
    }

    /**
     * Gets the edit identity form store and loads the http session storage
     * @return The edit identity form store
     */
    private BaseFormStore getEditIdentityFormStore() {
        ProvisioningPolicyFormStore store = new ProvisioningPolicyFormStore(this);
        SessionStorage sessionStorage = new HttpSessionStorage(getSession());
        store.setSessionStorage(sessionStorage);
        return store;
    }

    /**
     * Creates the identityFormService
     * @return instance of IdentityFormService
     * @throws GeneralException
     */
    private IdentityFormService createIdentityFormService(Identity identity) throws GeneralException {
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, identitiesResource.getQuickLinkName());
        if(quickLink == null) {
            throw new InvalidParameterException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new IdentityFormService( getContext(), getLoggedInUser(), identity, quickLink, ticketApp, externalSource, this);
    }

    /**
     * Get sub-resource for identity roles
     * @return
     * @throws GeneralException 
     */
    @Path("access/identityRoles")  
    public IdentityEntitlementRoleResource getRoles() throws GeneralException {
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY));
        return new IdentityEntitlementRoleResource(identity.getId(), this, false);
    }  

    /**
     * Get sub-resource for identity effective access
     * @return
     * @throws GeneralException 
     */
    @Path("access/identityEffectiveAccess")  
    public IdentityEffectiveAccessListResource getEffectiveAccess() throws GeneralException {        
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY));
        return new IdentityEffectiveAccessListResource(identity.getId(), this);
    } 

    /**
     * Returns a resource for listing entitlements for the given identity
     * @return Resource for listing entitlements
     */
    @Path("access/identityEntitlements")
    public IdentityEntitlementResource getEntitlements() throws GeneralException {
        Identity identity = getIdentity();
        authorize(new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY));
        return new IdentityEntitlementResource(identityId, this);
    }
    
    /**
     * Unlock identity
     * @return response with error message
     * @throws GeneralException
     */
    @POST
    @Path("unlock")
    public Response unlockIdentity() throws GeneralException {
        Identity identity = getIdentity();
        if(hasRight(SPRight.UnlockIdentity)) {
            try {
                Lockinator padLock = new Lockinator(getContext());
                padLock.unlockUser(identity, true);  
            } catch (GeneralException e) {
                _log.error("Exception: [" + e.getMessage() + "]", e);
                return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage(getLocale(), getUserTimeZone())).build();
            }
            return Response.ok().build();
        }
        else {
            Message e = new Message(IDENTITY_UNAUTHORIZED_UNLOCK);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage(getLocale(), getUserTimeZone())).build();
        }
    }

    /**
     * Update the forwarding info preferences on the user
     * @param data Posted data that contains startDate, endDate, and forwardUser
     * @return Response Empty response if succeeds
     * @throws GeneralException
     */
    @PUT
    @Path("forwardInfo")
    public Response updateFowardInfo (Map<String, Object> data) throws GeneralException {
        Identity identity = getIdentity();
        if(hasRight(SPRight.SetIdentityForwarding)) {
            ForwardingInfoDTO forwardInfo = new ForwardingInfoDTO(data);
            ForwardingService service = new ForwardingService(getLoggedInUser(), identity, getContext(), this);
            try {
                WorkflowResultItem result = service.updateForwardInfo(forwardInfo);
                return Response.ok(result).build();
            } catch (GeneralException e) {
                _log.error("Exception: [" + e.getMessage() + "]", e);
                return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage(getLocale(), getUserTimeZone())).build();
            }
        } else {
            Message e = new Message(IDENTITY_UNAUTHORIZED_FORWARD);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage(getLocale(), getUserTimeZone())).build();
        }
    }

    /**
     * Get the forwarding info for the selected identity.  This includes an identity to forward requests to, and the
     * end/start date for the forward to occur
     * @return ForwardingInfoDTO the forwarding info object that contains the forwarding info
     * @throws GeneralException
     */
    @GET
    @Path("forwardInfo")
    public Response getForwardInfo () throws GeneralException {
        if(hasRight(SPRight.SetIdentityForwarding)) {
            ForwardingService service = new ForwardingService(getLoggedInUser(), getIdentity(), getContext(), this);
            return Response.ok(service.getForwardInfo()).build();
        } else {
            Message e = new Message(IDENTITY_UNAUTHORIZED_FORWARD);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage(getLocale(), getUserTimeZone())).build();
        }
    }

    /**
     * Verify if current logged in user has the right access
     * @param {String} right 
     * @return
     */
    private boolean hasRight(String right) {
        return Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(), right);
    }
}
