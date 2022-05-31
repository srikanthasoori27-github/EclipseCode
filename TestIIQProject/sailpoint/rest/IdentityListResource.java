/**
 * 
 */
package sailpoint.rest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Correlator;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.IdentityMatchAuthorizer;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.IdentityListService;
import sailpoint.service.IdentityListServiceContext;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.lcm.RequestPopulationBean;

/**
 * @author peter.holcomb
 *
 */
@Path(IIQClient.RESOURCE_IDENTITIES)
public class IdentityListResource extends BaseListResource implements IdentityListServiceContext {

    private static final Log log = LogFactory.getLog(IdentityListResource.class);

    Filter nameFilter = null;
    public static final String FIELD_SELECTED = "IIQ_Selected";
    public static final String ATT_IDENTITY_IDS = "lcmRequestIdentityIds";
    public static final String ATT_SEARCH_STRING_FILTER = "lcmRequestStringFilter";
    public static final String ATT_SEARCH_FILTER = "lcmRequestFilter";


    /** Sub Resource Methods **/
    @Path("{identityId}")
    public IdentityResource getIdentity(@PathParam("identityId") String identityId)
    throws GeneralException {
        return new IdentityResource(identityId, this);
    }

    /**
     * Create a new identity with the attributes in the given map.
     * 
     * @param  attributes A Map with attributes for the identity to create.
     * 
     * @return A CreateOrUpdateRequest map with information about the request.
     */
    @POST
    public Map<String,Object> createIdentity(Map<String,Object> attributes) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.IdentityCreateWebService.name()));
        return getHandler().handleIdentityCreateRequest(attributes);
    }


    /**
     * Return a list of identities that the given identity can "manage".
     * This includes identities that are under the given identity in the manager
     * hierarchy or all identities (within scope) if the identity is an
     * IdentityAdministrator.
     * 
     * @return A list of identities "managed" by the given identity, represented
     *         as maps.
     */
    @GET @Path("{identityId}/" + IIQClient.RESOURCE_MANAGED_IDENTITIES)
    public Set<Map<String,String>> getIdentities(@PathParam("identityId") String identityId)
    throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.GetIdentityListWebService.name()));
    	
        // Currently the client (ARM) expects a full list so that it can cache
        // the results and do its own searching.  This doesn't seem like a great
        // idea, but I'm not going to change it.  In the future consider making
        // this do paging, sorting, etc... and changing return type to
        // ListResult.
        // jsl - we no longer have ARM, is this still necessary?
        return getHandler().getIdentityList(decodeRestUriComponent(identityId));
    }

    /**
     * Return a grid compatible list of identities that the given identity can "manage".
     * This includes identities that are under the given identity in the manager
     * hierarchy or all identities (within scope) if the identity is an
     * IdentityAdministrator.
     * 
     * @return A list of identities "managed" by the given identity, represented
     *         as maps.
     */
    @GET @Path("grid/{identityId}/" + IIQClient.RESOURCE_MANAGED_IDENTITIES)
    public ListResult getGridManagedIdentities(@PathParam("identityId") String identityId, @QueryParam("name") String name)
    throws GeneralException {
        identityId = decodeRestUriComponent(identityId);
        Identity identity = getContext().getObjectById(Identity.class, identityId);

        if (identity == null) {
            throw new ObjectNotFoundException(Identity.class, identityId);
        }

        authorize(new IdentityMatchAuthorizer(identity));
        
        String quicklinkName = (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);

        /** Create a simple name filter if they entered a name **/
        Filter nameFilter = IdentityListService.createNameFilter(name);
        if (nameFilter != null) {
            request.getSession().setAttribute(ATT_SEARCH_STRING_FILTER, nameFilter);
        }
        else {
            request.getSession().removeAttribute(ATT_SEARCH_STRING_FILTER);
        }
        setNameFilter(nameFilter);

        // Delegate to the list service to get the results. If no identity options in request, this will return
        // properly instantiated empty ListResult.
        IdentityListService service = createIdentityListService();
        ListResult result = service.getManagedIdentitiesByQuicklink(getLoggedInUser(), quicklinkName);

        //Need to post-process results and mark the ids which are already selected based on what's in the session.
        Set<String> ids = (Set<String>)request.getSession().getAttribute(ATT_IDENTITY_IDS);
        if (!Util.isEmpty(ids)) {
            for (Object obj : result.getObjects()) {
                // rshea: This cast kinda sucks but ListResult is multipurpose and widely used so whaddya gonna do.
                Map<String, Object> row = (Map<String, Object>)obj;
                if (ids.contains(row.get("id"))) {
                    row.put(FIELD_SELECTED, true);
                }
            }
        }
        
        return result;
    }

    /**
     * Overriding this so we can default the sorting to 'lastname' in the classic UI.
     *
     * @return String - value of sortBy property
     */
    @Override
    public String getSortBy() {
        return Util.isNullOrEmpty(this.sortBy) ? "lastname" : this.sortBy;
    }

    public Filter getSessionFilter() {
        return (Filter)request.getSession().getAttribute(ATT_SEARCH_FILTER);
    }

    @Override
    public Filter getNameFilter() {
        return this.nameFilter;
    }

    public void setNameFilter(Filter nameFilter) {
        this.nameFilter = nameFilter;
    }

    @Override
    public Filter getIdFilter() {
        // no op
        return null;
    }


    @Override
    public boolean isCurrentUserFirst() {
        return false;
    }

    @Override
    public boolean isRemoveCurrentUser() {
        return true;
    }

    @Override
    public String getQuickLink() {
        return (String) request.getSession().getAttribute(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
    }

   
    /** returns a list of identities that's been filtered using search criteria **/
    @Path("grid/filtered")
    public FilteredIdentityListResource getFilteredIdentities(@QueryParam("roleId") String roleId,
                                            @QueryParam("entitlementId") String entitlementId,
                                            @QueryParam("showNonMatched") Boolean showNonMatched)
        throws GeneralException {

        authorize(new LcmEnabledAuthorizer());
        return new FilteredIdentityListResource(this, roleId, entitlementId, showNonMatched);
    }
    
    /**
     * Creates a properly initialized IdentityListService 
     */
    private IdentityListService createIdentityListService()
    {
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(RequestPopulationBean.class.getName());
        return new IdentityListService(getContext(), this, selector);
    }
    
    /**
     * Return a grid compatible list of identities based on the given GroupDefinition id.
     * 
     * @return A list of identities defined by the filter in the given GroupDefinition id.
     */
    @GET @Path("population/{populationId}/" + IIQClient.RESOURCE_GROUP_DEFINITION) 
    public ListResult getPopulation(@PathParam("populationId") String groupDefId)
            throws GeneralException {

        authorize(new RightAuthorizer(SPRight.FullAccessGroup, SPRight.ManageWorkgroup, SPRight.ViewGroups, SPRight.ViewPopulations));

        GroupDefinition groupDef = getContext().getObjectById(GroupDefinition.class, groupDefId);
        if (groupDef == null) {
            throw new ObjectNotFoundException(GroupDefinition.class, groupDefId);
        }

        QueryOptions qo = new QueryOptions();
        qo.add(groupDef.getFilter());
        qo.setDistinct(true);

        int total;
        List<Map<String, Object>> results;

        total = getContext().countObjects(Identity.class, qo);

        /*
         * Provide a default sort and deJSONify the sort parameters as needed 
         */
        if (sortBy == null) {
            sortBy = "name";
        } else {
            sortBy = Util.getKeyFromJsonSafeKey(sortBy);
        }
        
        handleOrdering(qo, UIConfig.POPULATION_EDIT_TABLE_COLUMNS);
        
        if (start > -1) {
            qo.setFirstRow(start);
        }

        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        results = getResults(UIConfig.POPULATION_EDIT_TABLE_COLUMNS, Identity.class, qo); 
        
        return new ListResult(results, total);
    }

    /**
     * @param {identityId} - it is identityID for whom workItem count is calculated.
     * @param {type} - user has workItem and workItem type can be Approval, Certification, Delegation etc...
     * If type is null/empty or all, then this method calculates count of all work item associated with Identity.
     * @return count for given work item type.
     **/
     @GET @Path("{identityId}/workItemCount")
     public String getWorkItemCount(@PathParam("identityId") String identityId,
                                    @QueryParam("type") String workItemType)
         throws GeneralException {
         // authorization check
         authorize(new RightAuthorizer(SPRight.WebServiceRights.GetWorkItemCountWebService.name()));

         identityId = decodeRestUriComponent(identityId);
         Identity identity = getContext().getObjectById(Identity.class, identityId);
         if (null == identity) {
             throw new ObjectNotFoundException(Identity.class, identityId);
         }
         QueryOptions qo = new QueryOptions();
         if (Util.isNullOrEmpty(workItemType) || workItemType.equals("all") ) {
             qo.add(Filter.eq("owner", identity));
         }
         else {
             qo.add(Filter.and(Filter.eq("owner", identity),
                                 Filter.eq("type", workItemType)));
         }
         int count = getContext().countObjects(WorkItem.class, qo);
         return Integer.toString(count);
     }

     /**
      * Returns the identity cube name for given Link.
      *
      * @param  appName     application name to filter by
      * @param  nativeIdentity  account name from native application to filter by.
      * @return identity cube name.
      */
      @GET
      @Path("findByAccount")
      public String getIdentityNameFilterByLinks( @QueryParam("application") String appName,
                                                  @QueryParam("nativeIdentity") String nativeIdentity)
         throws Exception {

         authorize(new RightAuthorizer(SPRight.WebServiceRights.GetIdentityNameByLinkWebService.name()));
         SailPointContext _context = getContext();

         if ( Util.isNullOrEmpty(appName) ) {
             throw new InvalidParameterException("application is a required query parameter.");
         }
         if ( Util.isNullOrEmpty(nativeIdentity) ) {
             throw new InvalidParameterException("nativeIdentity is a required query parameter.");
         }

         // get application object
         Application app = _context.getObjectByName(Application.class, appName);
         if ( null == app ) {
             throw new ObjectNotFoundException(Application.class, appName);
         }

         // get identity name using link
         Link uniqueLink = new Correlator(_context).findLinkByNativeIdentity(app, null, nativeIdentity);
         if ( null == uniqueLink ) {
             throw new ObjectNotFoundException(Link.class, nativeIdentity);
         }

         return uniqueLink.getIdentity().getName();
     }
}
