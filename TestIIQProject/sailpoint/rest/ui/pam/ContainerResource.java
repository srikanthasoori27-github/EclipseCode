/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.service.WorkflowResultItem;
import sailpoint.service.pam.ContainerDTO;
import sailpoint.service.pam.ContainerProvisioningService;
import sailpoint.service.pam.ContainerService;
import sailpoint.service.pam.PamIdentityDeprovisioningResultItem;
import sailpoint.service.pam.PamUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single PAM container
 */
public class ContainerResource extends BaseResource {
    public static final String IDENTITYIDS_PARAM = "identityIds";
    public static final String IS_SELECT_ALL_PARAM = "isSelectAll";
    public static final String IDENTITY_ACCOUNTS_PARAM = "identityAccounts";
    public static final String PERMISSIONS_PARAM = "permissions";
    public static final String PRIVILEGED_ITEMS_PARAM = "privilegedItems";

    String containerId;

    public ContainerResource(BaseResource parent, String containerId) {
        super(parent);
        this.containerId = containerId;
    }


    @GET
    public ContainerDTO getContainer() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ContainerService service = new ContainerService(this.getContext());
        return service.getContainerDTO(containerId);
    }


    /**
     * Remove a list of identities from the given container
     * POST parameters
     *      identityIds - the list of identityIds to remove
     * @param data Form data
     * @return A list of results from removing each identity.
     */
    @POST
    @Path("removeIdentities")
    public List<PamIdentityDeprovisioningResultItem> removeIdentities(Map<String, Object> data) throws GeneralException {
        PamUtil.checkProvisionIdentitiesEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        if (!PamUtil.isContainerOwnerAndCanEdit(getLoggedInUser(), containerId, getContext())) {
            authorize(new RightAuthorizer(SPRight.FullAccessPAM, SPRight.PAMModifyIdentities));
        }

        @SuppressWarnings("unchecked")
        List<String> identityIds = (ArrayList<String>) data.get(IDENTITYIDS_PARAM);
        boolean isSelectAll = data.containsKey(IS_SELECT_ALL_PARAM) && (boolean) data.get(IS_SELECT_ALL_PARAM);
        if(Util.isEmpty(identityIds) && !isSelectAll) {
            throw new InvalidParameterException("No identity ids specified");
        }

        ContainerProvisioningService service = new ContainerProvisioningService(this.getContext(), this.getLoggedInUser(), getLocale());
        return service.removeIdentities(this.containerId, identityIds, isSelectAll);
    }


    /**
     * Add a list of identities from the given container
     * POST parameters
     *      identityAccounts - the map of identityIds to accountIds
     *      permissions - the list (strings) of permissions to add to the identities
     * @param data Form data
     * @return A list of results from adding each identity.
     */
    @POST
    @Path("addIdentities")
    public List<WorkflowResultItem> addIdentities(Map<String, Object> data) throws GeneralException {
        PamUtil.checkProvisionIdentitiesEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        if (!PamUtil.isContainerOwnerAndCanEdit(getLoggedInUser(), containerId, getContext())) {
            authorize(new RightAuthorizer(SPRight.FullAccessPAM, SPRight.PAMModifyIdentities));
        }

        @SuppressWarnings("unchecked")
        Map<String, String> identityAccounts = (HashMap<String, String>) data.get(IDENTITY_ACCOUNTS_PARAM);
        List<String> permissions = (List<String>) data.get(PERMISSIONS_PARAM);
        if(Util.isEmpty(identityAccounts)) {
            throw new InvalidParameterException("No identity accounts specified");
        }
        if(Util.isEmpty(permissions)) {
            throw new InvalidParameterException("No permissions specified");
        }

        ContainerProvisioningService service = new ContainerProvisioningService(this.getContext(), this.getLoggedInUser(), getLocale());
        return service.addIdentities(this.containerId, identityAccounts, permissions);
    }



    @Path("identities")
    public ContainerIdentityListResource getIdentities() throws GeneralException {
        return new ContainerIdentityListResource(this, containerId);
    }



    @Path("groups")
    public ContainerGroupListResource getGroups() throws GeneralException {
        return new ContainerGroupListResource(this, containerId);
    }

    /**
     * Add a list of privileged items to the given container
     * POST parameters
     *      privilegedItems - the map of privilegedItems to add to the container
     * @param data Form data
     */
    @POST
    @Path("addPrivilegedItems")
    public Response addPrivilegedItems(Map<String, Object> data) throws GeneralException {
        PamUtil.checkModifyPrivDataEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        if (!PamUtil.isContainerOwnerAndCanEdit(getLoggedInUser(), containerId, getContext())) {
            authorize(new RightAuthorizer(SPRight.FullAccessPAM, SPRight.PAMModifyPrivilegedItems));
        }
        List privilegedItems = (List<HashMap<String, String>>) data.get(PRIVILEGED_ITEMS_PARAM);
        if(Util.isEmpty(privilegedItems)) {
            throw new InvalidParameterException("No privileged items specified");
        }

        ContainerProvisioningService service = new ContainerProvisioningService(this.getContext(), this.getLoggedInUser(), getLocale());
        service.addPrivilegedItems(this.containerId, privilegedItems);
        return Response.accepted().build();
    }

    /**
     * Remove a list of privileged items from the container
     * POST parameters
     *    privilegedItems - a list of privileged item values to be removed from the container
     * @param data
     * @return
     * @throws GeneralException
     */
    @POST
    @Path("removePrivilegedItems")
    public Response removePrivilegedItems(Map<String, Object> data) throws GeneralException {
        PamUtil.checkModifyPrivDataEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        if (!PamUtil.isContainerOwnerAndCanEdit(getLoggedInUser(), containerId, getContext())) {
            authorize(new RightAuthorizer(SPRight.FullAccessPAM, SPRight.PAMModifyPrivilegedItems));
        }
        List privilegedItems = (List<String>) data.get(PRIVILEGED_ITEMS_PARAM);
        boolean isSelectAll = data.containsKey(IS_SELECT_ALL_PARAM) && (boolean) data.get(IS_SELECT_ALL_PARAM);
        if(Util.isEmpty(privilegedItems) && !isSelectAll) {
            throw new InvalidParameterException("No privileged items specified");
        }

        ContainerProvisioningService service = new ContainerProvisioningService(this.getContext(), this.getLoggedInUser(), getLocale());
        service.removePrivilegedItems(this.containerId, privilegedItems, isSelectAll);
        return Response.accepted().build();
    }

    @Path("privilegedItems")
    public ContainerPrivilegedItemListResource getPrivilegedItems() throws GeneralException {
        return new ContainerPrivilegedItemListResource(this, containerId);
    }

}
