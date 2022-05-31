/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.object.Application;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.WorkItem;
import sailpoint.service.IdentityDetailsService;
import sailpoint.service.LCMConfigService;
import sailpoint.service.RemoteLoginService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.IdentitySummary;


/**
 * REST methods for the "identities" resource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityResource extends BaseResource {
    
    String identityId;
    

    /**
     * Constructor for this sub-resource.
     * 
     * @param  identityId  The id of the identity this sub-resource is
     *                       servicing.
     * @param  parent        The parent of this sub-resource.
     */
    public IdentityResource(String identityId, BaseResource parent) {
        super(parent);
        //IIQETN-6256 :- Decoding an IdentityId that was encoded before sending
        this.identityId = decodeRestUriComponent(identityId, false);
    }

    /**
     * Return a map representation of the given identity.
     * 
     * @return A Map representation of the given identity.
     */
    @GET
    public Map<String,Object> getIdentity()
        throws GeneralException {

        authorize(new RightAuthorizer(SPRight.WebServiceRights.ShowIdentityWebService.name()));

        Map<String, Object> result = getHandler().showIdentity(identityId);
        if (Util.isEmpty(result)) {
            throw new ObjectNotFoundException(Identity.class, identityId);
        }
        return result;
    }

    @GET @Path("/summary")
    public IdentitySummary getIdentitySummary()
        throws GeneralException {
        
    	authorize(new AllowAllAuthorizer());

        IdentitySummary summary = null;
        QueryOptions ops =  new QueryOptions(Filter.eq("id", identityId));
        ops.add(Filter.or(Filter.eq("workgroup", true), Filter.eq("workgroup", false)));
        Iterator<Object[]> results = this.getContext().search(Identity.class,ops, Arrays.asList("name", "displayName"));
        if (results.hasNext()){
            Object[] row = results.next();
            summary = new IdentitySummary(null, (String)row[0], (String)row[1]);
        }

        return summary;
    }

    /**
     * Check whether the request identity has the given right.
     * 
     * @param  right         The name of the SPRight to check.
     * 
     * @return A CheckAuthorizationResult map.
     *
     * NOTE: This requires identity name -rap
     */
    @GET @Path("/"+IIQClient.AuthorizationService.Consts.RESOURCE_CHECK_AUTHORIZATION)
    public Map<String,Object> checkAuthorization(@QueryParam(IIQClient.AuthorizationService.Consts.PARAM_RIGHT) String right)
        throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.CheckAuthorizationWebService.name()));
    	
        return getHandler().handleCheckAuthRequest(identityId, right);
    }
    
    /**
     * Create or update the given identity with the attributes in the given map.
     * 
     * @param  attributes    A Map with attributes for the identity to create or
     *                       update.
     * 
     * @return A CreateOrUpdateRequest map with information about the request.
     *
     * NOTE: This requires name in the case of create -rap
     */
    @PUT
    public Map<String,Object> createOrUpdateIdentity(Map<String,Object> attributes) throws GeneralException{
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.IdentityCreateOrUpdateWebService.name()));
    	
        return getHandler().handleIdentityCreateOrUpdateRequest(identityId, attributes);
    }
    
    /**
     * Create a new identity with the attributes in the given map.
     * 
     * @param  attributes A Map with attributes for the identity to create.
     * 
     * @return A CreateOrUpdateRequest map with information about the request.
     */
    @POST
    public Map<String,Object> createIdentity(Map<String,Object> attributes) throws GeneralException{
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.IdentityCreateWebService.name()));
        return getHandler().handleIdentityCreateRequest(attributes);
    }

    /**
     * Return the sub-resource that will handle links for this identity.
     */
    @Path("links")
    public LinksResource getLinks() {
        return new LinksResource(this.identityId, this);
    }
    
    /**
     * Return the sub-resource that will handle history for this identity.
     */
    @Path("history")
    public IdentityHistoryResource getIdentityHistoryItems() {
        return new IdentityHistoryResource(this.identityId, this);
    }
    
    /**
     * Return the sub-resource that will handle exceptions for this identity.
     */
    @Path("exceptions")
    public ExceptionsResource getExceptions() {
        return new ExceptionsResource(this.identityId, this);
    }

    @Path("identityEntitlements")
    public IdentityEntitlementResource getEntitlements() {
        return new IdentityEntitlementResource(this.identityId, this);
    }
        
    @Path("identityEntitlementRoles")
    public IdentityEntitlementRoleResource getRoleEntitlements() {
        return new IdentityEntitlementRoleResource(this.identityId, this);
    }

    @Path("identityIndirectAccess")
    public IdentityEffectiveAccessListResource getIndirectAccess() throws GeneralException {
        return new IdentityEffectiveAccessListResource(this.identityId, this);
    }

    /**
     * Aggregate the given resource object for the given application onto the
     * given identity.
     * 
     * @param  application     The application the resource object is coming from.
     * @param  resourceObject  A Map of the resource object attributes and an
     *                         optional "IIQ_aggregationOptions" map with options
     *                         for the aggregation.
     * 
     * @return A RequestResult map.
     *
     *
     * NOTE: This requires identity name for aggregation -rap
     */
    @SuppressWarnings("unchecked")
    @POST @Path("/" + IIQClient.RESOURCE_AGGREGATE)
    public Map<String,Object> aggregate(@QueryParam(IIQClient.PARAM_APPLICATION) String application,
                                        Map<String,Object> resourceObject)
        throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.AggregateAccountWebService.name()));

        Map<String,Object> aggOptions =
            (Map<String,Object>) resourceObject.remove(IIQClient.AGGREGATION_OPTIONS);

        // Allow the caller to pass the native identity as a special attribute
        // so they don't have to hardcode information about the IIQ schema.
        String nativeIdentity = (String) resourceObject.remove("IIQ_nativeIdentity");
        if (null != nativeIdentity) {
            String appName = getHandler().mapApplicationName(application);
            Application app =
                getContext().getObjectByName(Application.class, appName);
            if (null == app) {
                throw new GeneralException("Could not load app: " + appName);
            }
            Schema schema = app.getAccountSchema();
            if (null != schema) {
                String identityAttr = schema.getIdentityAttribute();
                if ((null != identityAttr) &&
                    (null == resourceObject.get(identityAttr))) {
                    resourceObject.put(identityAttr, nativeIdentity);
                }
            }
        }

        // Now use the handler to aggregate.
        return getHandler().aggregate(identityId, application, resourceObject, aggOptions);
    }
    
    /**
     * Remote service to allow creating of remote login tokens 
     * to allow psuedo-sso for integrations that want to 
     * launch into our app given some user context. 
     * 
     * @return a TokenId 
     * @throws Exception
     *
     *
     * NOTE: This requires identity name -rap
     */    
    @POST 
    @Path("/" + IIQClient.SUB_RESOURCE_REMOTE_LOGIN)
    public String createRemoteLogin(@QueryParam(IIQClient.PARAM_HOST) String remoteHost)                                    
        throws Exception {       
        //We now have a RemoteLoginWebService SPRight we could authorize with as well if needed
    	authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));
    	
        RemoteLoginService service = new RemoteLoginService(getContext());
        return service.createRemoteLoginToken(identityId, remoteHost);
    }    
    
    /**
     * Gets the details for an identity if the current user is able to make LCM requests for the identity.
     * @param action LCM action to use for authorization. USed only if session does not contain the action.
     * @return The identity details if the current user has access, an empty map otherwise.
     * @throws GeneralException If the identity is invalid
     */
    @POST
    @Path("/lcmDetails")
    public Map<String, Object> getLcmDetails(@QueryParam("action") String action)
        throws GeneralException
    {
        Identity requestedIdentity = ensureFind(Identity.class, identityId, false);
        
        String authAction = (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_ACTION);
        if (Util.isNothing(authAction)) {
            authAction = action;
        }
        authorize(new LcmRequestAuthorizer(requestedIdentity).setAction(authAction));
        return new IdentityDetailsService(requestedIdentity).getIdentityDetails(getLocale(),getUserTimeZone());
    }
    
    /**
     * Gets the details for an identity if the current user is the work item owner,
     * and the specified user is on the work item.
     * @param workItemId The id of the workitem to check.
     * @return The identity details if the current user has access, an empty map otherwise.
     * @throws GeneralException If the requested identity is invalid or workItemId doesn't refer to a valid WorkItem
     */
    @POST
    @Path("/workItemDetails/{workItemId}")
    public Map<String, Object> getWorkItemDetails(@PathParam("workItemId") String workItemId)
        throws GeneralException
    {
        WorkItem workItem = ensureFind(WorkItem.class, workItemId, false);
        Identity requestedIdentity = ensureFind(Identity.class, identityId, false);
        
        authorize(new WorkItemAuthorizer(workItem));
        
        if (isOnWorkItem(requestedIdentity, workItem)) {
            return new IdentityDetailsService(requestedIdentity).getIdentityDetails(getLocale(),getUserTimeZone());
        } else {
        	throw new UnauthorizedAccessException(new Message(MessageKeys.UI_IDENTITY_WORK_ITEM_OWNER_UNAUTHORIZED_ACCESS));
        }
    }
    
    /**
     * Gets whether or not the specified identity is on a work item.
     * @param identity The Identity to check.
     * @param workItem The WorkItem to check.
     * @return True if the specified identity is an "object" of the WorkItem, false otherwise.
     */
    private boolean isOnWorkItem(Identity identity, WorkItem workItem)
        throws GeneralException
    {
        assert(identity != null);
        assert(workItem != null);
        
        if (Identity.class.getName().equals(workItem.getTargetClass())) {
            if (identity.getId().equals(workItem.getTargetId())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Retrieves the specified object. If the object is not found,
     * an exception is thrown.
     * @param c The class of the object to find.
     * @param idOrName The id or the name of the object to find.
     * @return The found object.
     * @throw GeneralException If the object could not be found.
     */
    private <T extends SailPointObject> T ensureFind(Class<T> c, String idOrName, boolean byName)
        throws GeneralException
    {
        assert(c != null);
        assert(idOrName != null);

        T result = null;
        if (byName) {
            result = getContext().getObjectByName(c, idOrName);
        } else {
            result = getContext().getObjectById(c, idOrName);
        }
        
        if (result == null) {
            throw new GeneralException(c.getSimpleName() + " identified by " + idOrName + " not found");
        }

        return result;        
    }

}
