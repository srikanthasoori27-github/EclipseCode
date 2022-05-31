/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.IdentityEntitlementOnIdentityAuthorizer;
import sailpoint.authorization.IdentityRoleOnIdentityAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.rest.ui.classification.ClassificationListResource;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.IdentityEntitlementRoleService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.util.WebUtil;

/**
 *
 * This is closely related to IdentityEntitlementResource which handles
 * the non role data.
 *
 * @author dan.smith
 *
 */
public class IdentityEntitlementRoleResource extends BaseListResource implements BaseListServiceContext {

    private static final String IDENTITY_ENTS_ROLE_COLUMNS_KEY = "identityEntitlementRoleGridColumns";
    private static final String UI_IDENTITY_ENTS_ROLE_COLUMNS_KEY = "uiIdentityEntitlementRoleGridColumns";

    /**
     * Sub-resources identity "scope".
     */
    private String identity;

    /**
     * Used to localize the role descriptions.
     */
    Localizer _localizer;

    /**
     * True if we should use the warehouse column config
     */
    boolean isFromWarehouse = true;

    /**
     * Sub-resource constructor.
     */
    public IdentityEntitlementRoleResource(String identity, BaseResource parent) {
        super(parent);
        //IIQETN-6256 :- Decoding an IdentityName that was encoded before sending
        this.identity = decodeRestUriComponent(identity, false);
        _localizer = new Localizer(getContext());
    }

    /**
     * Sub-resource constructor specifying which column config to use.
     */
    public IdentityEntitlementRoleResource(String identity, BaseResource parent, boolean isFromWarehouse) {
        this(identity, parent);
        this.isFromWarehouse = isFromWarehouse;
    }

    /**
     * Return the identity we're operating on.
     */
    private Identity getIdentity() throws GeneralException {
        Identity i = getContext().getObjectById(Identity.class, this.identity);
        if (i == null) {
            throw new ObjectNotFoundException(Identity.class, this.identity);
        }

        return i;
    }
    
    /**
     * Get a list of roleEntitlementDTOs
     * @param startParm
     * @param limitParm
     * @param sortFieldParm
     * @param sortDirParm
     * @return list of roleEntitlementDTOs
     * @throws GeneralException
     */
    @GET
    public ListResult getRoleEntitlements(@QueryParam("start") int startParm,
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
        String columnConfigName = getColumnConfigName();
        QueryOptions ops = getQueryOptions(columnConfigName);
        ops.add(Filter.join("value", "Bundle.name"));

        Identity id = getIdentity();

        Map<String, String> queryParams = getQueryParamMap();
        ListServiceColumnSelector columnSelector = new BaseListResourceColumnSelector(columnConfigName);
        IdentityEntitlementRoleService service = new IdentityEntitlementRoleService(getContext(), this, this, columnSelector);
        return service.getRoleEntitlementsWithParams(id, ops, queryParams);
    }

    @Override
    protected void calculateColumns(Map<String,Object> rawQueryResults,
                                    String columnsKey,
                                    Map<String,Object> map)
        throws GeneralException{

        // should I just require these in column config
        super.calculateColumns(rawQueryResults, columnsKey, map);

    }

    /**
     * Return the RoleDetailResource for the role with the given IdentityEntitlement ID.
     *
     * @param identityEntitlementId  The ID of the IdentityEntitlement representing the role.
     */
    @Path("{identityEntitlementId}/details")
    public RoleDetailResource getRoleDetail(@PathParam("identityEntitlementId") String identityEntitlementId)
        throws GeneralException {

        // The parent resource provides authorization that the logged in user can view this identity.
        // We just need to make sure that this entitlement is on the user we're looking at.
        authorize(new IdentityEntitlementOnIdentityAuthorizer(getIdentity(), identityEntitlementId));

        IdentityEntitlement ent = getContext().getObjectById(IdentityEntitlement.class, identityEntitlementId);
        if (null == ent) {
            throw new ObjectNotFoundException(IdentityEntitlement.class, identityEntitlementId);
        }

        // The IdentityEntitlement only has the name of the role, so look up the role by name.
        String roleName = ent.getStringValue();
        String roleId = ObjectUtil.getId(getContext(), Bundle.class, roleName);
        if (null == roleId) {
            throw new ObjectNotFoundException(Bundle.class, roleName);
        }

        String identityId = this.getIdentity().getId();
        String assignmentId = ent.getAssignmentId();

        return new RoleDetailResource(roleId, assignmentId, identityId, this);
    }

    /**
     * Gets a ClassificationListResource from within the IdentityEntitlementRoleResource.
     *
     * Used for getting role details in the RoleDetailsPanel.js in the Identity warehouse.
     * Piggy backing on this Resource since this resource also handles the role list in the entitlements
     * tab in the Identity warehouse
     * @param roleId The Role ID
     * @return RoleDetailResource
     * @throws GeneralException
     */
    @Path("{roleId}/classifications")
    public ClassificationListResource getClassificationListResource(@PathParam("roleId") String roleId)
        throws GeneralException {

        // Use the same authorization methods as getting the role grid in Identity warehouse
        authorize(CompoundAuthorizer.or(
                new RightAuthorizer(SPRight.ViewIdentity),
                new LcmRequestAuthorizer(getIdentity()).setAction(QuickLink.LCM_ACTION_VIEW_IDENTITY)
        ), new IdentityRoleOnIdentityAuthorizer(getIdentity(), roleId));

        return new ClassificationListResource(Bundle.class, roleId, this);
    }

    /**
     * Returns the correct column config name
     * @return The name of the column config to use
     */
    private String getColumnConfigName() {
        /* The "warehouse view" and the "new ui" use different column configs.
         * Return the appropriate column config name. */
        if(this.isFromWarehouse) {
            return IDENTITY_ENTS_ROLE_COLUMNS_KEY;
        }
        return UI_IDENTITY_ENTS_ROLE_COLUMNS_KEY;
    }
}
