/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.api.RoleUtil;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ListResult;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.service.CurrentAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * REST methods for the "roles" resource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path(IIQClient.RESOURCE_ROLES)
public class RoleListResource extends BaseListResource {
    
    /**
     * This class allows for filtering of other properties 
     * of the Bundle object besides extended attributes.
     */
    private class LCMBundleConfig extends ObjectConfig
    {    
        private List<String> attributes;
        private ObjectConfig bundleConfig;
        
        /**
         * Constructs a new instance of LCMBundleConfig.
         * 
         * @param config The Bundle class ObjectConfig.
         */
        public LCMBundleConfig(ObjectConfig config)
        {
            bundleConfig = config;
            
            attributes = new ArrayList<String>();
            attributes.add("type");
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasObjectAttribute(String name)
        {
            if (attributes.contains(name)) {
                return true;
            }
            
            return bundleConfig.hasObjectAttribute(name);
        }
    }

    public static final String COL_STATUS = "IIQ_status";
    public static final String COL_STATUS_CLASS = "IIQ_status_class";
    public static final String COL_AUTHORIZATION = "IIQ_authorization";
    public static final String COL_COLOR = "IIQ_color";
    public static final String COL_SELECTED = "IIQ_Selected";
    public static final String COL_ROLE_TYPE_NAME = "roleTypeName";
    public static final String COL_ROLE_TYPE_ICON = "roleTypeIcon";

    public static final String PARAM_POPULATION_PERCENT = "populationPercentMin";

    public static final String ROLE_GRID_ID = "id";
    
    private static final String ASSIGNED = "assigned";
    private static final String DETECTED = "detected";

    private static final String OP_REMOVE = "remove";
    
    public static final String COLUMNS_KEY = "sailpoint.web.lcm.RolesRequestBean";
    public static final String ATT_REQUEST_REQUESTS = "baseRequestRequests";


    /** The total population of the role search based on some identity attributes **/
    int totalPopulation;

    int total;
    
    ObjectConfig bundleConfig;

    /** Any identity filters passed in from the population search form **/
    List<Filter> identityFilters;

    Identity identity;

    private static Log log = LogFactory.getLog(RoleListResource.class);
    // Don't cache the roleConfig here -- ObjectConfig already does this for us in a 
    // manner maintains cache coherence
    private ObjectConfig roleConfig;

    public RoleListResource() {
        super();
        roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
    }

    public RoleListResource(BaseResource parent) {
        super(parent);
        roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
    }

    /**
     * Get a list of the roles that can be assigned to the given identity.  If
     * the role mode is "permitted", this returns the roles that are permitted
     * by those already assigned.  Otherwise, this returns the assignable roles.
     * This is subject to paging.
     * 
     * @param  identity  The name of the identity for which to find the roles.
     * @param  roleMode  The role mode - permitted or assignable (default).
     * 
     * @return A list result with the roles.
     */
    @GET @Path(IIQClient.SUB_RESOURCE_ASSIGNABLE_PERMITS)
    public ListResult getAssignableRoles(@QueryParam(IIQClient.ARG_IDENTITY) String identity,
            @QueryParam(IIQClient.ARG_ROLE_MODE) String roleMode)
                    throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.RolesAssignablePermitsWebService.name()));

        return getHandler().getAssignableRoles(identity, roleMode, query, start, limit);
    }

    @GET @Path("grid/{roleId}/permits")
    public ListResult getGridPermitsForRole(@PathParam("roleId") String roleId)
            throws GeneralException {
    	
    	authorize(new LcmEnabledAuthorizer());
    	
        sortBy = "displayableName";
        Bundle bundle = getContext().getObjectById(Bundle.class, roleId);
        
        if(bundle==null) {
        	return null;
        }
        
        Localizer localizer = new Localizer(getContext());
        
        List<Bundle> permits = null;
        
        permits = RoleUtil.getFlattenedPermittedRoles(getContext(), bundle, permits);

        // remove any disabled permits so they are not allowed to be selected
        if (!Util.isEmpty(permits)) {
            Iterator<Bundle> permittedItr = permits.iterator();
            while (permittedItr.hasNext()) {
                Bundle permittedRole = permittedItr.next();
                if (permittedRole.isDisabled()) {
                    permittedItr.remove();
                }
            }
        }
        
        // remove the deselected ones
        List<String> exclusionList = Util.csvToList(excludedIds);
        if (Util.atob(selectAll)){
            Iterator<Bundle> it = permits.iterator();
            while (it.hasNext()) {
                Bundle b = it.next();
                String bundleId = b.getId();
                if (null != exclusionList) {
                    for (String excluded : exclusionList) {
                        if (bundleId.equals(excluded)) {
                            it.remove();
                        }
                    }
                }
            }
        }
        
        ListResult results = getListResultFromObjects(COLUMNS_KEY, permits);


        /** Add the pretty colors! **/
        List<Map<String,Object>> objects = (List<Map<String,Object>>)results.getObjects();
        for(Map<String,Object> result : objects) {
            result.put("IIQ_color", WebUtil.getScoreColor((Integer) result.get("riskScoreWeight")));
            /** For now, duplicate the column so the access request bean can access the field **/
            result.put("riskScore", result.get("riskScoreWeight"));

            String id = (String)result.get("id");
            result.put(Localizer.ATTR_DESCRIPTION, 
                            localizer.getLocalizedValue(id, Localizer.ATTR_DESCRIPTION, getLocale()));

            getRoleTypeName(roleConfig, result, null);
        }
        
        /** If there is a cart stored on the session, we want to annotate any of these permits with whether they are
         * in the cart or not so we can display them as checked in the permits grid 
         */
        if(results.getCount()>0) 
            markPermitsChecked(results);
        
        return results;
    }
    
    /** Looks on the session to see if there is a cart, if there is, it looks through the cart to see
     * if any of the permits in the result list are in the cart.  If they are, we store IIQ_selected=true
     */
    void markPermitsChecked(ListResult results) {
        List<AccountRequest> cartRequests = (List<AccountRequest>)request.getSession().getAttribute(ATT_REQUEST_REQUESTS);
        if(cartRequests!=null) {
            for(AccountRequest cartRequest : cartRequests) {
                
                String id = (String)cartRequest.getArgument("id");
                if(!Util.isNullOrEmpty(id)) { 
                    for(Map<String,Object> map : (List<Map<String,Object>>)results.getObjects()) {
                        if(map.get("id").equals(id)) {
                            map.put(COL_SELECTED, true);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Displays a list of roles that are currently assigned to the user in LCM.
     * @param identityId The identity id.
     * @return The list result.
     * @throws GeneralException
     **/
    @GET @Path("grid/{identityId}") 
    public ListResult getGridRolesForIdentity(@PathParam("identityId") String identityId)
        throws GeneralException {

        Identity identity = getContext().getObjectById(Identity.class, identityId);
        if (identity == null) {
            return new ListResult(new ArrayList<Map<String,Object>>(), 0);
        }
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        return getGridRolesForIdentity(identityId, true);
    }

    /**
     * Gets the detected and assigned roles for the specified identity merged into
     * one list result.
     * @param identityId The identity id.
     * @param trim Indicates whether to trim and sort the list result.
     * @return The list result.
     * @throws GeneralException
     */
    public ListResult getGridRolesForIdentity(String identityId, boolean trim) throws GeneralException {
        Identity identity = getContext().getObjectById(Identity.class, identityId);
        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), identity);
        List<CurrentAccessService.CurrentAccessRole> currentRoles = currentAccessService.getRoles();
        int totalCount = Util.size(currentRoles);
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (CurrentAccessService.CurrentAccessRole currentAccessRole : currentRoles) {
            results.add(convertCurrentAccessRole(currentAccessRole, identityId));
        }

        if (trim) {
            trimAndSortResults(results);
        }

        makeJsonSafeKeys(results);
        return new ListResult(results, totalCount);
    }

    private Map<String, Object> convertCurrentAccessRole(CurrentAccessService.CurrentAccessRole currentAccessRole, String identityId)
            throws GeneralException {
        Bundle role = currentAccessRole.getObject(getContext());
        Map<String, Object> row = convertObject(role, COLUMNS_KEY);
        row.put("identityId", identityId);
        row.put("roleId", role.getId());
        row.put("assignmentId", currentAccessRole.getAssignmentId());
        getRoleTypeName(this.roleConfig, row, role);

        StringBuilder status = new StringBuilder();
        if (currentAccessRole.getSunrise() != null || currentAccessRole.getSunset() != null) {
            if (currentAccessRole.getSunrise() != null && currentAccessRole.getSunrise().compareTo(new Date()) > 0) {
                Message statusClsMsg = new Message(MessageKeys.ACTIVE_ON);

                status.append(statusClsMsg.getLocalizedMessage(getLocale(), getUserTimeZone()))
                        .append(" ")
                        .append(Internationalizer.getLocalizedDate(currentAccessRole.getSunrise(), true, getLocale(), getUserTimeZone()));

                row.put(COL_STATUS_CLASS, "clock");
            }

            if (currentAccessRole.getSunset() != null && currentAccessRole.getSunset().compareTo(new Date()) > 0) {
                Message statusClsMsg = new Message(MessageKeys.INACTIVE_ON);

                if (!status.toString().equals("")) {
                    status.append("<br/>");
                }

                status.append(statusClsMsg.getLocalizedMessage(getLocale(), getUserTimeZone()))
                        .append(" ")
                        .append(Internationalizer.getLocalizedDate(currentAccessRole.getSunset(), true, getLocale(), getUserTimeZone()));

                row.put(COL_STATUS_CLASS, "clock");
            }
            row.put(COL_STATUS, status.toString());
        } else {
            row.put(COL_STATUS, new Message(currentAccessRole.getStatus().getMessageKey()).getLocalizedMessage(getLocale(), getUserTimeZone()));
        }
        
        if (currentAccessRole.isRequested()) {
            row.put(COL_STATUS_CLASS, "requested");
        } else {
            if (currentAccessRole.isDetected()) {
                row.put("detectedOrAssigned", "detectedRoles");
                //overwrite with something unique
                row.put("id", Util.uuid());
            } else {
                row.put("detectedOrAssigned", "assignedRoles");
                row.put("id", Util.isNullOrEmpty(currentAccessRole.getAssignmentId()) ?
                        role.getId(): currentAccessRole.getAssignmentId());
            }
        }

        String source = currentAccessRole.getSource();
        Message authMessage = null;
        if (currentAccessRole.isAssigned()) {
            authMessage = new Message(MessageKeys.LCM_ROLE_AUTHORIZATION_ASSIGNED, source);
        } else if (currentAccessRole.isDetected()) {
            if (Util.isNullOrEmpty(currentAccessRole.getSource())) {
                authMessage = new Message(MessageKeys.LCM_ROLE_AUTHORIZATION_DETECTED);
            } else {
                authMessage = new Message(MessageKeys.LCM_ROLE_AUTHORIZATION_PERMITTED, source);
            }
        } else if(currentAccessRole.isDetected()) {
            authMessage = new Message(MessageKeys.LCM_ROLE_AUTHORIZATION_REQUESTED, source);
        }

        if (authMessage != null) {
            row.put(COL_AUTHORIZATION, authMessage.getLocalizedMessage(getLocale(), getUserTimeZone()));
        }

        if (row.containsKey(Localizer.ATTR_DESCRIPTION)) {
            row.put(
                    Localizer.ATTR_DESCRIPTION,
                    new Localizer(getContext()).getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, getLocale())
            );
        }

        row.put(COL_COLOR, WebUtil.getScoreColor((Integer) row.get("riskScoreWeight")));

        return row;
    }

    private void getRoleTypeName(ObjectConfig roleConfig, Map<String,Object> map, Bundle bundle) {

        String type = (String)map.get("type");
        if(bundle!=null) {
            type = bundle.getType();
        }
        if(type!=null) {
            getRoleTypeName(type, roleConfig, map);
        }        
    }

    @SuppressWarnings("unchecked")
    private void getRoleTypeName(String type, ObjectConfig roleConfig, Map<String,Object> map) {
        List<RoleTypeDefinition> typeDefs = 
                (ArrayList<RoleTypeDefinition>) roleConfig.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);

        RoleTypeDefinition def = null;
        for(RoleTypeDefinition typeDef : typeDefs) {
            if(typeDef.getName().equals(type)){
                def = typeDef;
                break;
            }
        }

        if(def!=null) {           
            map.put(COL_ROLE_TYPE_NAME, def.getDisplayableName()); 
            map.put(COL_ROLE_TYPE_ICON, def.getIcon());
        } else {
            map.put(COL_ROLE_TYPE_NAME, roleConfig.getDisplayName(type));
            map.put(COL_ROLE_TYPE_ICON, "");
        }
    }

    /** Grabs all of the permitted roles for an identity and appends them to the list being returned **/
    private void appendPermittedRoles(List<Map<String,Object>> out) throws GeneralException {
        /** Now add all permitted roles **/
        if(identity.getAssignedRoles()!=null) {
            for (Bundle bundle : identity.getAssignedRoles()) {
                List<Bundle> permits = bundle.getPermits();
                if(permits!=null) {
                    for(Bundle permit : permits) {

                        /** Only add the role if not already in the assigned/detected list **/
                        if(permit.getName().startsWith(query) && 
                                identity.getDetectedRole(permit.getId())==null && 
                                identity.getAssignedRole(permit.getId())==null) {
                            Map<String,Object> map = convertObject(permit, COLUMNS_KEY, getBundleConfig());
                            out.add(map);
                        }
                    }
                }
            }
        }
    }


    public ObjectConfig getBundleConfig() {
        if(bundleConfig==null) {
            bundleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        }
        return bundleConfig;
    }

    public void setBundleConfig(ObjectConfig bundleConfig) {
        this.bundleConfig = bundleConfig;
    }


    /** Returns whether a role is detected or not. used in role details popup.**/

    @GET @Path("isDetected") 
    public Boolean isDetectedRole(@QueryParam("identityId") String identityId, @QueryParam("roleId") String roleId) 
                    throws GeneralException {

        authorize(new RightAuthorizer(SPRight.ViewIdentity, SPRight.ViewRole, SPRight.ViewApplication));

        Identity identity = getContext().getObjectById(Identity.class, identityId);
        
        if (identity != null) {
            if (identity.getDetectedRole(roleId) != null) {
                return true;
            }
        }
        return false;
    }

}
