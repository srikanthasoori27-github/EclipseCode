/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.api.AccountGroupService;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.IdentityEntitlementOnIdentityAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.*;
import sailpoint.service.identity.IdentityEntitlementDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.util.WebUtil;

/**
 * Rest resource to handle an Identity's entitlements grid which is
 * rendered along with the other identity information from the define
 * identity tab.
 * 
 * Most of the data used by the grids are part of the IdentityEntitlment
 * mode with the exception of looking up display names, and some of hte
 * role role relationship data which is obtained from the role model.
 * 
 * This is closely related to IdentityEntitlementRoleResource which handles
 * the assigned and detected roles portion of the entitlement data.
 * 
 * @author dan.smith
 *
 */

public class IdentityEntitlementResource extends BaseListResource implements BaseListServiceContext {

    /**
     * Sub-resources identity "scope".
     */
    private String identity;
    
    /**
     * Service to indicate if an attribute is also marked group 
     * in the ui, to help us when rendering links to the details
     * in the ui tier.
     * 
     * djs: TODO Is there something new?
     */
    AccountGroupService _accountGroupService;    
    
    private static final String IDENTITY_ENTS_COLUMNS_KEY = "identityEntitlementGridColumns";
    
    /**
     * Sub-resource constructor.
     */
    public IdentityEntitlementResource(String identity, BaseResource parent) {
        super(parent);
        this.identity = decodeRestUriComponent(identity);
        _accountGroupService = new AccountGroupService(getContext());
        
    }

    /**
     * Return the identity we're operating on.
     */
    private Identity getIdentity() throws GeneralException {
        return getContext().getObjectById(Identity.class, this.identity);
    }
    
    /**
     * 
     * List method that was initially designed to be called from
     * the Entitlements grid listed on the Identity page. 
     */
    @GET
    public ListResult getEntitlements(@QueryParam("start") int startParm,
                                      @QueryParam("limit") int limitParm, 
                                      @QueryParam("sort") String sortFieldParm, 
                                      @QueryParam("dir") String sortDirParm) throws GeneralException {

        authorize(CompoundAuthorizer.or(
                new RightAuthorizer(SPRight.ViewIdentity),
                new LcmRequestAuthorizer(getIdentity()).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY)
        ));
    	
        start = startParm;
        limit = WebUtil.getResultLimit(limitParm);
        sortBy = sortFieldParm;
        sortDirection = sortDirParm;
        colKey = "identityEntitlementGridColumns";
        
        QueryOptions ops = getQueryOptions();
        // always in the scope of the identity
        Identity currentIdentity = getIdentity();

        Map<String, String> queryParams = getQueryParamMap();
        ListServiceColumnSelector columnSelector = new BaseListResourceColumnSelector(IDENTITY_ENTS_COLUMNS_KEY);
        IdentityEntitlementListService service = new IdentityEntitlementListService(getContext(), this, this, columnSelector);
        ListResult listResult = service.getEntitlementsWithParams(currentIdentity, ops, queryParams);
        return listResult;
    }

    /**
     * Build an entitlement dto out of the given entitlement id
     * @param entitlementId The id of the IdentityEntitlement object we want to load
     * @return Response A response with the entitlementDTO
     * @throws GeneralException
     */
    @GET
    @Path("{entitlementId}")
    public IdentityEntitlementDTO getEntitlementDTO(@PathParam("entitlementId") String entitlementId) throws GeneralException {

        // The parent resource provides authorization that the logged in user can view this identity.
        // We just need to make sure that this entitlement is on the user we're looking at.
        authorize(new IdentityEntitlementOnIdentityAuthorizer(getIdentity(), entitlementId));

        IdentityEntitlementService entitlementService = new IdentityEntitlementService(getContext());
        return entitlementService.getEntitlementDTO(entitlementId, this);
    }


    /**
     * Return the ManagedAttributeDetailResource for the entitlement with the given IdentityEntitlement ID.
     *
     * @param  entitlementId  The ID of the IdentityEntitlement for the entitlement that is being viewed.
     *
     * @return The ManagedAttributeDetailResource for the entitlement with the given IdentityEntitlement ID.
     */
    @Path("{entitlementId}/details")
    public ManagedAttributeDetailResource getManagedAttributeDetails(@PathParam("entitlementId") String entitlementId)
        throws GeneralException {

        // The parent resource provides authorization that the logged in user can view this identity.
        // We just need to make sure that this entitlement is on the user we're looking at.
        authorize(new IdentityEntitlementOnIdentityAuthorizer(getIdentity(), entitlementId));

        IdentityEntitlement entitlement = getContext().getObjectById(IdentityEntitlement.class, entitlementId);
        if (entitlement == null) {
            throw new ObjectNotFoundException(IdentityEntitlement.class, entitlementId);
        }

        IdentityEntitlementService entitlementService = new IdentityEntitlementService(getContext());

        ManagedAttribute managedAttribute = entitlementService.getManagedAttribute(entitlement);

        return new ManagedAttributeDetailResource(managedAttribute, this);
    }
}
