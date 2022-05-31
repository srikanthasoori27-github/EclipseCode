/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class implementing the web service methods provided by IIQ in 
 * a protocol neutral way.  It is intended to be shared by all of the 
 * web service protocols we support including REST, SPML, and Axis/SOAP.  
 *
 * This was derived from the original Axis service class (which no longer exists)
 * sailpoint.axis.SailPointService but has been simplified and then 
 * extended.  The plan in 3.1 is to use this only for the RESTful
 * service, but we should consider refactoring the SPML interface
 * to use it as well.
 *   
 * Exceptions may be thrown and are expected to be returned as errors
 * in an appropriate way.  
 *
 * jsl - This had a lot of old ARM stuff in it which was removed in 6.0.
 * It may still have some ARM leftovers but they weren't obvious.  Should
 * weed this someday.
 *
 */

package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.AuthenticationFailureException;
import sailpoint.api.Identitizer;
import sailpoint.api.Interrogator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.connector.AbstractConnector;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.integration.IIQClient.IdentityService;
import sailpoint.integration.IIQClient.PasswordService;
import sailpoint.integration.IIQClient.AuthorizationService.CheckAuthorizationResult;
import sailpoint.integration.IIQClient.IdentityService.CreateOrUpdateResult;
import sailpoint.integration.IIQClient.PasswordService.CheckPasswordResult;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.UIConfig;
import sailpoint.object.Application.Feature;
import sailpoint.object.ManagedResource;
import sailpoint.request.AggregateRequestExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityProxy;
import sailpoint.web.modeler.RoleConfig;


public class ServiceHandler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ServiceHandler.class);
    public static final String AUTHENTICATION = "authentication";
    public static final String IDENTITY = "identity";

    SailPointContext _ctx;    


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ServiceHandler(SailPointContext ctx) {
        _ctx = ctx;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Internal Utilities
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext getContext() throws GeneralException {
        return _ctx;
    }

    private String getUsername() throws GeneralException {
        return _ctx.getUserName();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Policy Checking
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given a list of potential role assignments, check to see if assigning
     * these roles would violate any SOD policies.
     *
     * The term "assignment" is a misnomer here, policies can apply to 
     * both assigned and detected roles.
     *
     * What is returned is a list of error messages, formatted as:
     *
     *     <policy name> : <constraint name>
     *
     * TODO: It would be better if this returned the two names in
     * a List or simple violation structure so we could decide whether
     * or not to merge them depending on the protocol.
     *    
     * NOTE: To support more loosely coupled integrations, the identity
     * name is optional, in case we will just check for conflicts
     * among the passed roles.  Also the role name do not need
     * to correspond to IIQ role names, we'll ignore the ones that don't
     * 
     */
    public List<String> checkRolePolicies(String user, List<String> roles) 
    throws GeneralException {

        List<String> result = new ArrayList<String>();

        SailPointContext con = getContext();

        log.info("checkRolePolicies");

        Identity identity = null;
        if (user != null)
            identity = con.getObjectByName(Identity.class, user);

        if (identity == null) {
            // fake one up so we can test the roles
            identity = new Identity();
            // not sure if we need a name for the violation?
            if (user != null)
                identity.setName(user);
            else
                identity.setName("dummy");
            identity.setId("12345");
        }

        if (roles != null) {
            for (int i = 0 ; i < roles.size() ; i++) {
                String roleName = roles.get(i);
                Bundle bundle = con.getObjectByName(Bundle.class, roleName);
                if (bundle == null) {
                    // just ignore these...
                    log.info("Unknown role: " + roleName);
                }
                else {
                    identity.add(bundle);
                }
            }

            Interrogator ig = new Interrogator(con);
            ig.prepare();
            List<PolicyViolation> violations = ig.interrogate(identity);

            if (violations != null && violations.size() > 0) {
                for (PolicyViolation v : violations) {

                    String msg = v.getPolicyName() + " : " + v.getConstraintName();
                    result.add(msg);
                }
            }
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Authentication
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Authentication Resource
     * 
     * <p>
     * This returns the username as part of the response in order to get around
     * some case sentitivity issues on some dbs.  For example, authentication for
     * "mary johnson" will succeed, but later lookups using "mary johnson" 
     * instead of the exact username "Mary Johnson" will create problems.  By
     * returning "Mary Johnson" to the caller, we can ensure that later calls
     * are made with the proper case-sensitive variant of the username. 
     * </p>
     * 
     * @return String outcome of the authentication (Success means passed);
     *            the username of the Identity if successful, null if not.
     */
    public Map<String, String> checkAuthentication(String username, String password)
            throws GeneralException, ExpiredPasswordException {
        String outcome = "authenticationFailure";
        Identity identity = null;
        try {
            log.info("checkAuthentication");
            SailPointContext con = getContext();
            identity = con.authenticate(username, password);
            outcome = "success";
        }
        catch (ExpiredPasswordException e) {
            outcome = "expiredPassword";
        }
        catch (AuthenticationFailureException e) {
            outcome = "authenticationFailure";
        }
        catch (GeneralException e) {
            outcome = "invalidUsernamePassword";
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put(AUTHENTICATION , outcome);
        map.put(IDENTITY, (null == identity) ? null : identity.getName());

        return map;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    /** Takes as an argument the name of a configuration item and returns
     * its value from the sailpoint system config
     */
    public Object getConfiguration(String configName) {
        Object configVal = null;
        try {
            log.info("configuration");
            Configuration sysConfig = 
                getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);

            if(sysConfig!=null) {
                configVal = sysConfig.get(configName);
            }

        } catch (GeneralException e) {
            configVal = null;
        }
        return configVal;        
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Roles Assignable resource
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the roles that can be assigned to the given identity based on the
     * role mode.  If the role mode is "permitted", this returns all roles
     * permitted by the assigned roles of the identity.  If the role mode is
     * "assignable" or not specified, this returns all assigned roles the
     * identity does not currently have.
     */
    public ListResult getAssignableRoles(String identity, String roleMode,
                                         String query, int start, int limit)
        throws GeneralException {
        
        List<Map<String, Object>> bundleResult = null;
        int bundlesCount = 0;
        if (checkPermittedRoleMode(roleMode)) {
            //permitted mode
            List<Map<String, Object>> rolesPermittedByAlreadyAssigned =
                rolesPermittedByAlreadyAssigned(identity);
            //find the identities that match the given query used for the total count
            List<Map<String, Object>> searchResults =
                searchPermittedRoleList(rolesPermittedByAlreadyAssigned, query);                                                      
            //find the subset needed for paging
            //getting final bundleResult and bundlesCount for Permitted mode
            bundleResult = getPagedResults(start, limit, searchResults);
            bundlesCount = searchResults.size();        
        } else {
            //default mode is All assignable roles
            //getting final bundleResult and bundlesCount for Assignable mode
            bundleResult = rolesAssignable(identity, start, limit, query);
            bundlesCount = totalRolesAssignableCount(identity, query);
        }

        return new ListResult(bundleResult, bundlesCount);
    }
    
    /**
     * Roles Assignable resource excluding roles already assigned to the specific identity
     * @return List<Map<String, Object>> all roles assignable
     */
    private List<Map<String, Object>> rolesAssignable(String decodedIdentity, int start, int limit, String query) 
    throws GeneralException {    

        SailPointContext con = getContext();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        log.info("rolesAssignable");
        // list of all assignable roles
        Identity iden = con.getObjectByName(Identity.class, decodedIdentity);
        List<Bundle> assignedRoles = alreadyAssignedRoles(iden);
        List<String> roleIdsToExclude = getRoleIdsToExclude(assignedRoles);
        
        /** Pull in assignment events **/
        List<Map<String,Object>> assignedRoleEvents = getAssignedRoleEvents(iden);
        if(!assignedRoleEvents.isEmpty()) {
            for(Map<String,Object> event : assignedRoleEvents) {
                roleIdsToExclude.add((String)event.get("id"));
            }
        }
        
        QueryOptions qo = getRolesAssignableQueryOptions(start, limit, query, roleIdsToExclude);
        scopingResults(con, qo);
        List<Bundle> bundles = con.getObjects(Bundle.class, qo);

        if (bundles != null) {
            for (Bundle b : bundles) {
                Map<String, Object> bundleResult = new HashMap<String, Object>();
                bundleResult.put("id" , b.getId());
                bundleResult.put("name" , b.getName());
                bundleResult.put("description" , b.getDescription());
                result.add(bundleResult);                
            }
        }            

        return result;
    }

    private int totalRolesAssignableCount(String decodedIdentity, String query)
    throws GeneralException {

        SailPointContext con = getContext();
        int bundlesCount;
        log.info("rolesAssignable totalBundlesCount");
        Identity iden = con.getObjectByName(Identity.class, decodedIdentity);
        List<Bundle> assignedRoles = alreadyAssignedRoles(iden);
        List<String> roleIdsToExclude = getRoleIdsToExclude(assignedRoles);
        QueryOptions qo = getRolesAssignableQueryOptions(0, 0, query, roleIdsToExclude);
        scopingResults(con, qo);
        bundlesCount = con.countObjects(Bundle.class, qo);
        return bundlesCount;
    }

    private QueryOptions getRolesAssignableQueryOptions(int start, int limit, String query, List<String> roleIdsToExclude)
    throws GeneralException {
        QueryOptions qo = getPagingOptions(start, limit, null, null);
        if (query != null && !query.equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START)));
        }
        RoleConfig rc = new RoleConfig();
        Filter typeFilter = Filter.in("type", rc.getAssignableRoleTypes());        
        if (typeFilter != null) {
            qo.add(typeFilter);
        }
        if (roleIdsToExclude != null && roleIdsToExclude.size() > 0) {
            Filter excludeFilter = Filter.not(Filter.in("id", roleIdsToExclude));
            if (excludeFilter != null) {
                qo.add(excludeFilter);
            }
        }
        
        /** Filter out disabled roles **/
        qo.add(Filter.eq("disabled", false));
        return qo;
    }

    private QueryOptions getPagingOptions(int start, int limit, String sort, String dir)
    throws GeneralException {
        QueryOptions qo = new QueryOptions();

        // sort order defaults to ascending by name 
        String sortField = (null == sort) ? "name" : sort;        
        boolean sortOrder = (null == dir) ? true : ("ASC".equals(dir));        

        qo.addOrdering(sortField, sortOrder);

        // paging 
        if (limit > 0) 
            qo.setResultLimit(limit);

        if (start > 0) 
            qo.setFirstRow(start);

        return qo;
    }

    private List<String> getRoleIdsToExclude(List<Bundle> roles) {
        List<String> roleIdsToExclude = new ArrayList<String>();
        for (Bundle role : roles) {
            roleIdsToExclude.add(role.getId());
        }        
        return roleIdsToExclude;
    }

    private List<Bundle> alreadyAssignedRoles(Identity iden) {
        List<Bundle> assignedRoles = iden.getAssignedRoles();
        return assignedRoles;
    }

    private void scopingResults(SailPointContext con, QueryOptions qo)
        throws GeneralException {

        qo.setScopeResults(true);
        
        if (null != getUsername()) {
            Identity requestor = con.getObjectByName(Identity.class, getUsername());
            if (null != requestor) {
                qo.extendScope(Filter.eq("owner", requestor));
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assigned Role Permits
    //
    ////////////////////////////////////////////////////////////////////// 

    /**
     * Assigned Role Permits resource excluding detected roles for the Identity
     * @return List<Map<String, Object>> all roles assignable
     */
    private List<Map<String, Object>> rolesPermittedByAlreadyAssigned(String decodedIdentity) 
    throws GeneralException {    

        SailPointContext con = getContext();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        log.info("rolesPermittedByAlreadyAssigned");
        // list of all assignable roles
        Identity iden = con.getObjectByName(Identity.class, decodedIdentity);
        List<Bundle> assignedRoles = alreadyAssignedRoles(iden);
        //for roles to exclude
        List<Bundle> detectedRoles = alreadyDetectedRoles(iden);
        List<String> detectedRoleIdsToExclude = getRoleIdsToExclude(detectedRoles);
        if (assignedRoles != null) {
            for (Bundle b : assignedRoles) {
                List<Bundle> permittedRoles = b.getPermits();
                if (permittedRoles != null && permittedRoles.size() > 0) {
                    for (Bundle p : permittedRoles) {
                        Map<String, Object> bundleResult = new HashMap<String, Object>();
                        String id = p.getId();
                        if (checkPermitRoleNotAlreadyDetected(detectedRoleIdsToExclude, id)) {
                            bundleResult.put("id" , id);
                            bundleResult.put("name" , p.getName());
                            bundleResult.put("description" , p.getDescription());
                            result.add(bundleResult);   
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> searchPermittedRoleList(List<Map<String, Object>> rolesPermittedByAlreadyAssigned, String query) {
        // sort the results by name
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        // bump to upper case for comparison
        if(query!=null)
            query = query.toUpperCase();

        for (Map<String, Object> map : rolesPermittedByAlreadyAssigned)
        {
            Object obj = map.get("name");
            if (obj instanceof String)
            {
                String str = ((String)obj).toUpperCase();
                //for empty string also returns true
                if (query==null || str.startsWith(query))
                {
                    results.add(map);
                }
            }
        }
        Collections.sort(results, (new MapComparator("name")));
        return results;
    }

    private List<Map<String, Object>> getPagedResults(int start, int limit,
            List<Map<String, Object>> list) {
        List<Map<String, Object>> pagedResults = 
            new ArrayList<Map<String, Object>>();

        int end = start + limit;
        if ((limit == 0) || (end > list.size()))
            end = list.size();

        for (int i = start; i < end; i++)
        {
            pagedResults.add(list.get(i));
        }

        return pagedResults;
    }

    private boolean checkPermittedRoleMode(String roleMode) {
        //assignable value or empty or null all will be taken as assignable mode
        //only checks if value is hence permitted for permitted mode
        return (roleMode != null && roleMode.equalsIgnoreCase("permitted"));
    }

    private boolean checkPermitRoleNotAlreadyDetected(List<String> roleIdsToExclude, String id) {
        if(roleIdsToExclude!=null && roleIdsToExclude.size()>0) {
            return !roleIdsToExclude.contains(id);
        }        
        return true;
    }

    private List<Bundle> alreadyDetectedRoles(Identity iden) {
        List<Bundle> detectedRoles = iden.getDetectedRoles();
        return detectedRoles;
    }


    private class MapComparator implements Comparator<Map<String, Object>>
    {
        private String key = null;

        public MapComparator(String key)
        {
            if (!key.equals(""))
                this.key = key;
        }

        public int compare(Map<String, Object> map1, Map<String,Object> map2) 
        {
            if (null == key)
                return 0;

            Object obj1 = map1.get(key);
            Object obj2 = map2.get(key);

            if ((null == obj1) && (null == obj2))
                return 0;
            else if ((null == obj1) && (null != obj2))
                return -1;
            else if ((null == obj2) && (null != obj1))
                return 1;
            else
            {
                String str1 = obj1.toString().toUpperCase();
                String str2 = obj2.toString().toUpperCase();
                return str1.compareTo(str2);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Show Identity resource
    //
    //////////////////////////////////////////////////////////////////////    

    /**
     * Show Identity resource with  roles assigned type, name, description
     * @return List all roles assignable
     */
    public Map<String, Object> showIdentity(String decodedIdentity) 
    throws GeneralException {
        SailPointContext con = getContext();

        Map<String, Object> identityResult = new HashMap<String, Object>();
        log.info("showIdentity");

        Identity iden = con.getObjectById(Identity.class, decodedIdentity);
        if (iden == null) {
            //empty returned for invalid. can be changed if needed to be more explicit
            return identityResult;
        }

        UIConfig uiConfig = con.getObjectByName(UIConfig.class, UIConfig.OBJ_NAME);
        List<ObjectAttribute> viewableIdentityAttributes = getAttributes(uiConfig);

        //Viewable attributes as per UIConfig and ObjectConfig
        displayViewableAttributes(iden, viewableIdentityAttributes, identityResult);
        displayAssignedRoles(identityResult, iden);
        return identityResult;
    }

    private List<ObjectAttribute> getAttributes(UIConfig uiConfig) throws GeneralException {
        List<ObjectAttribute> attributes = new ArrayList<ObjectAttribute>();
        ObjectConfig idConfig = ObjectConfig.getObjectConfig(Identity.class);
        if (uiConfig != null && idConfig != null) {
            List<String> atts = uiConfig.getIdentityViewAttributesList();
            if (atts != null) {
                for (String att : atts) {
                    ObjectAttribute ida = idConfig.getObjectAttribute(att);
                    if (ida != null)
                        attributes.add(ida);
                }
            }
        }        
        return attributes;
    }

    private void displayViewableAttributes(Identity identity, List<ObjectAttribute> viewableIdentityAttributes,
            Map<String, Object> identityResult) {
        Map<String, Object> viewableIdentityAttributesResult = new HashMap<String,Object>();
        List<String> listAttributes = new ArrayList<String>();
        for (ObjectAttribute attr : viewableIdentityAttributes) {
            String key = attr.getName();
            //arm always has the name as part of the user id suggest, 
            //so not sending name over the web service
            if (!key.equalsIgnoreCase("name")){
                String keyMessage = attr.getDisplayableName();
                Object value = IdentityProxy.get(identity, attr.getName());
                if (null != value)
                {
                    listAttributes.add(keyMessage);
                    viewableIdentityAttributesResult.put(keyMessage, value);
                }
            }
        }
        identityResult.put("viewableIdentityAttributes", viewableIdentityAttributesResult);
        //order as per listAttributes at the ui level
        identityResult.put("listAttributes", listAttributes);
    }   

    private void displayAssignedRoles(Map<String, Object> identityResult, Identity iden) {
        List<Bundle> assignedRoles = alreadyAssignedRoles(iden);    
        List<Map<String, Object>> assignedRoleResult = new ArrayList<Map<String,Object>>();
        // todo rshea TA4710: this seems problematic. to handle multiple assignments per role would mean
        // changing the data returned by the getIdentify REST resource, since there are possibly multiple assigners
        // and multiple dates now (I think).
        for (Bundle role : assignedRoles) {
            Map<String, Object> roleResult = new HashMap<String, Object>();
            roleResult.put("id" , role.getId());
            roleResult.put("displayName" , role.getName());
            roleResult.put("description" , role.getDescription());    

            RoleAssignment assignment = iden.getRoleAssignment(role);
            if (null != assignment) 
            {
                roleResult.put("assigner", assignment.getAssigner());
                roleResult.put("date", assignment.getDate().getTime());
            }

            assignedRoleResult.add(roleResult);
        }

        /** Pull in assignment events **/
        List<Map<String,Object>> assignedRoleEvents = getAssignedRoleEvents(iden);
        if(!assignedRoleEvents.isEmpty()) {
            assignedRoleResult.addAll(assignedRoleEvents);
        }
        
        identityResult.put("assignedRoles", assignedRoleResult);
    }
    
    /** Fetches any role assignment events for the identity **/
    private List<Map<String,Object>> getAssignedRoleEvents(Identity iden) {
        List<Map<String,Object>> assignedRoleEvents = new ArrayList<Map<String,Object>>();
        /** Pull in assignment events **/
        List<String> columns = new ArrayList<String>();
        columns.add("attributes");

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", iden));
        ops.setOrderBy("created");
        ops.setOrderAscending(false);

        try {
            Iterator<Object []> requests =
                getContext().search(Request.class, ops, columns);

            while(requests.hasNext()) {
                Object[] request = requests.next();
                Attributes<String,Object> attrs = (Attributes)request[0];
                if(attrs!=null) {
                    String eventType = attrs.getString(Request.ATT_EVENT_TYPE);
                    String roleId = attrs.getString(RoleEventGenerator.ARG_ROLE);
                    Date startDate = attrs.getDate(RoleEventGenerator.ARG_DATE);
                    if(eventType!=null 
                       && eventType.equals(RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT)
                       && startDate !=null && startDate.after(new Date())){
                        Bundle b = getContext().getObjectById(Bundle.class, roleId);
                        if(b!=null) {
                            Map<String, Object> bundleResult = new HashMap<String, Object>();
                            bundleResult.put("id" , b.getId());
                            bundleResult.put("displayName" , b.getName());
                            bundleResult.put("name", b.getName());
                            bundleResult.put("description" , b.getDescription());
                            bundleResult.put("date", startDate.getTime());
                            assignedRoleEvents.add(bundleResult);
                        }
                    }
                }
            }
        } catch(GeneralException ge) {
            log.warn("Unable to load events. Exception: "+ge.getMessage());
        }
        
        return assignedRoleEvents;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Get Identity list resource
    //
    //////////////////////////////////////////////////////////////////////    
    /**
     * Get the list of Identities for which the given Identity is authorized.
     * Because the results can't be generated by a single search, paging and
     * subset searching must be done by the caller.  
     * 
     * <p>
     * The number of search results is capped at 500 to preserve resources 
     * when searching deep hierarchies.  One risk of this cap is the 
     * possibility of maxing out the search results on the hierarchy search
     * before the scoped search is even engaged.
     * </p>
     * 
     * <p>
     * The depth of subordinate recursion is capped at 5 for similar reasons.
     * In a deep hierarchy, we want to at least capture the first few layers
     * completely.
     * </p>
     * 
     * @return List JSON formatted List of identities
     */
    private static String IDENTITY_ADMINISTRATOR = "IdentityAdministrator";
    private static int MAX_RESULTS = 500;
    private static int MAX_DEPTH = 5;
    public Set<Map<String, String>> getIdentityList(String decodedIdentity) 
    throws GeneralException {

        // using a Set will prevent dupes btwn subordinates and scope search
        Set<Map<String, String>> result = 
            new HashSet<Map<String, String>>();
        SailPointContext con = getContext();
        log.info("getIdentityList");

        Identity identity = con.getObjectById(Identity.class, decodedIdentity);
        if (identity == null) {
            throw new ObjectNotFoundException(Identity.class, decodedIdentity);
        }

        // add the given identity for self-service access requests
        result.add(loadIdentity(identity));

        // find all direct reports for the given identity - this will 
        // be capped at MAX_RESULTS entries
        List<Map<String, String>> subs = 
            new ArrayList<Map<String, String>>();
        searchSubordinates(con, identity, subs, 1);

        // add the subordinates to the result set
        result.addAll(subs);

        // now find the identities within the user's scope if capable
        if (identity.getCapabilityManager().hasCapability(IDENTITY_ADMINISTRATOR)) {
            // search as if the given identity were logged into IIQ
            con.impersonate(identity);          
            QueryOptions qo = new QueryOptions();
            qo.setScopeResults(true);
            List<Identity> identities = 
                con.getObjects(Identity.class, qo);
            for (Identity idnty : identities) {        
                result.add(loadIdentity(idnty));
            }
        }

        return result;            
    }


    /**
     * Searches for any subordinates of the given Identity.  Limits the
     * amount of recursion to MAX_RESULTS results.
     * 
     * @param con SailPointContext used to search
     * @param identity Identity of the person to search under
     * @param subs List containing subordinates
     * @throws GeneralException 
     */
    private void searchSubordinates(SailPointContext con, Identity identity, 
            List<Map<String, String>> subs, int depth) 
    throws GeneralException
    {
        // don't recurse further if we've hit the max results or depth
        if ((subs.size() >= MAX_RESULTS) || (depth >= MAX_DEPTH))
            return;

        // search for subordinates
        if (identity.getManagerStatus())
        {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("manager", identity));        
            List<Identity> identities = con.getObjects(Identity.class, qo);

            // don't just increment the depth variable, or you'll hit the
            // same memory pointer on every call to searcSubordinates?
            // playing it safe by adding one instead
            for (Identity idnty : identities)
            {        
                subs.add(loadIdentity(idnty));
                searchSubordinates(con, idnty, subs, (depth + 1));
            }
        }

        // trim any excess results before returning
        if (subs.size() > MAX_RESULTS)
            subs.subList(0, MAX_RESULTS);
    }


    /**
     * Convenience method to create a Map of the user info we need.
     * 
     * @param identity Identity whose info is requested
     * @return Map containing a set of Identity data
     */
    private Map<String, String> loadIdentity(Identity identity) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("name", notNull(identity.getName()));
        map.put("firstname", notNull(identity.getFirstname()));
        map.put("lastname", notNull(identity.getLastname()));
        map.put("displayableName", notNull(identity.getDisplayableName()));
        map.put("email", notNull(identity.getEmail()));
        map.put("id", notNull(identity.getId()));

        return map;
    }

    /**
     * Utility method that returns an empty string instead of null
     * 
     * @param str String to check for null
     * @return Given string if not null; empty string otherwise
     */
    private String notNull(String str) {
        if (null == str)
            return "";
        else
            return str;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Delete Pending Request for the Requestor who launched this Request
    //
    //////////////////////////////////////////////////////////////////////    

    // arm stuff?

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleIdentityCreateOrUpdateRequest(
            String identityName, 
            Map<String, Object> attributes) {

        try {
            if (identityName == null) {
                throw new GeneralException("No identityName provided");
            }
            if (attributes == null) {
                throw new GeneralException("No attributes specified");
            }

            SailPointContext con = getContext();
            IdentityServiceDelegate delegate = new IdentityServiceDelegate(con, getUsername());
            
            return delegate.createOrUpdateIdentity(identityName, attributes).toMap();

        } catch (Throwable t) {
            log.error(t);

            CreateOrUpdateResult result = new CreateOrUpdateResult();
            result.setPerformed(false);
            result.setStatus(CreateOrUpdateResult.STATUS_FAILURE);
            result.addError(t.getMessage());
            return result.toMap();
        }
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleIdentityCreateRequest(Map<String, Object> attributes) {
        
        try {
            if (attributes == null) {
                throw new GeneralException("No attributes specified");
            }
            
            SailPointContext con = getContext();
            IdentityServiceDelegate delegate = new IdentityServiceDelegate(con, getUsername());
            
            String identityName = (String) attributes.get(IdentityService.Consts.AttributeNames.USER_NAME);
            attributes.remove(IdentityService.Consts.AttributeNames.USER_NAME);

            return delegate.createIdentity(identityName, attributes).toMap();

        } catch (Throwable t) {
            log.error(t);

            CreateOrUpdateResult result = new CreateOrUpdateResult();
            result.setPerformed(false);
            result.setStatus(CreateOrUpdateResult.STATUS_FAILURE);
            result.addError(t.getMessage());
            return result.toMap();
        }
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> handlePasswordPolicyCheckReqest(Map<String, String> input) {
        
        try {
            if (input == null) {
                throw new GeneralException("No input specified");
            }

            SailPointContext con = getContext();

            PasswordServiceDelegate delegate = new PasswordServiceDelegate(con, getUsername());

            String identityName = (String) input.get(PasswordService.Consts.PARAM_ID);
            String password = (String) input.get(PasswordService.Consts.PARAM_PASSWORD);
            
            return delegate.checkPassword(identityName, password) .toMap();

        } catch (Throwable t) {
            log.error(t);

            CheckPasswordResult result = new CheckPasswordResult();
            result.setValid(false);
            result.setStatus(CheckPasswordResult.STATUS_FAILURE);
            result.addError(t.getMessage());
            return result.toMap();
        }  
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleCheckAuthRequest(String identityName, String right) {
        
        try {
            if (identityName == null) {
                throw new GeneralException("No identityName provided");
            }
            if (right == null) {
                throw new GeneralException("No right specified");
            }
            
            SailPointContext con = getContext();

            AuthorizationServiceDelegate delegate = new AuthorizationServiceDelegate(con, getUsername());
            return delegate.checkAuthorization(identityName, right).toMap();

        } catch (Throwable t) {
            log.error(t);

            CheckAuthorizationResult result = new CheckAuthorizationResult(); 
            result.setStatus(CheckPasswordResult.STATUS_FAILURE);
            result.setAuthorized(false);
            result.addError(t.getMessage());
            return result.toMap();
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // AGGREGATION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Option to allow aggregating even when the app has the NO_RANDOM_ACCESS
     * feature.
     */
    public static final String ARG_AGGREGATE_NO_RANDOM_ACCESS = "aggregateNoRandomAccess";
    
    /**
     * Aggregate the given resource object for the requested application onto
     * the identity with the given name.
     * 
     * @param  identityName    The name of the identity to aggregate on to.
     * @param  appName         The name of the app for the resource object.
     * @param  resourceObject  The resource object to aggregate.
     * @param  aggOptions      Options for the aggregator (not required).
     */
    public Map<String,Object> aggregate(String identityName, String appName,
                                        Map<String,Object> resourceObject,
                                        Map<String,Object> aggOptions)
        throws GeneralException {
        return aggregate(identityName, appName, resourceObject, aggOptions, false);
    }

    /**
     * Aggregate the given resource object as a retry (or not).
     * 
     * @see #aggregate(String, String, Map, Map)
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> aggregate(String identityName, String appName,
                                        Map<String,Object> resourceObject,
                                        Map<String,Object> aggOptions,
                                        boolean isRetry)
        throws GeneralException {

        RequestResult result = null;
        SailPointContext ctx = getContext();

        // Get the correct app name if we were handed a remote app name
        // from an integration.
        appName = mapApplicationName(appName);
        Application app = ctx.getObjectByName(Application.class, appName);
        if (null == app) {
            throw new GeneralException("Application not defined: " + appName);
        }

        // If the application doesn't support fetching we won't be able to use
        // Identitizer.reload().  Allow using the passed in object as a
        // ResourceObject for the aggregator if an option is set.
        if (app.supportsFeature(Feature.NO_RANDOM_ACCESS)) {
            if ((null != aggOptions) && Util.otob(aggOptions.get(ARG_AGGREGATE_NO_RANDOM_ACCESS))) {
                log.info("No random access for " + appName + " - forcing aggregation " +
                         "with given object: " + resourceObject);
                result =
                    aggregateResourceObject(identityName, appName, resourceObject, aggOptions, isRetry);
            }
            else {
                List<String> warnings = new ArrayList<String>();
                warnings.add("Application " + appName + " does not support random access " +
                             "and '" + ARG_AGGREGATE_NO_RANDOM_ACCESS + "' is not set to true. " +
                             "Not aggregating account.");
                result = new RequestResult(RequestResult.STATUS_WARNING, null, warnings, null);
            }
        }
        else {
            result = reloadAccount(identityName, app, resourceObject, aggOptions, isRetry);
        }

        return (null != result) ? result.toMap() : null;
    }

    /**
     * Check if the given identity is locked, and if so then queue an request to
     * attempt to aggregate this later unless this is a retry (ie - a retry
     * request is already in the queue).  If the identity is locked, this
     * returns a RequestResult with a status of RETRY, otherwise this will
     * return a null RequestResult.
     */
    private RequestResult queueAggIfLocked(String identityName, String appName,
                                           Map<String,Object> resourceObject,
                                           Map<String,Object> aggOptions,
                                           boolean isRetry)
        throws GeneralException {

        RequestResult result = null;

        // Check if the identity is locked by trying to get a persistent lock.
        // Don't wait for it (ie - lockTimeout = 0).  Just fail if we can't get
        // it immediately.
        boolean locked =
            ObjectUtil.isLocked(getContext(), Identity.class, identityName);

        // If the identity was locked, queue a request to retry it and return
        // a request result that says we are pending.
        if (locked) {

            // Only queue the retry request if there is not already one.
            if (!isRetry) {
                RequestDefinition def =
                    getContext().getObjectByName(RequestDefinition.class, AggregateRequestExecutor.DEF_NAME);
                if (null == def) {
                    throw new GeneralException("Could not find required RequestDefinition: " + AggregateRequestExecutor.DEF_NAME);
                }
    
                Request request = new Request(def);
                request.setAttribute(AggregateRequestExecutor.ARG_IDENTITY_NAME, identityName);
                request.setAttribute(AggregateRequestExecutor.ARG_APP_NAME, appName);
                request.setAttribute(AggregateRequestExecutor.ARG_RESOURCE_OBJECT, resourceObject);
                request.setAttribute(AggregateRequestExecutor.ARG_AGG_OPTIONS, aggOptions);
                RequestManager.addRequest(getContext(), request);
            }

            // Return a result that says to retry.
            List<String> warns = new ArrayList<String>();
            warns.add("The identity '" + identityName + "' is locked - queueing for retry.");
            result = new RequestResult(RequestResult.STATUS_RETRY, null, warns, null);
        }
        
        return result;
    }
    
    /**
     * Reaggregate by bootstrapping a stubbed link onto the given identity (if
     * it doesn't yet exist), then doing a targeted reaggregation and refresh.
     * This is preferred over aggregateResourceObject() since we have more
     * control over what the ResourceObject looks like, but does not work if the
     * application does not support fetching accounts.
     */
    private RequestResult reloadAccount(String identityName, Application app,
                                        Map<String,Object> resourceObject,
                                        Map<String,Object> aggOptions,
                                        boolean isRetry)
        throws GeneralException {
        
        // First, check if the identity is locked.  If so, return a retry result.
        RequestResult result =
            queueAggIfLocked(identityName, app.getName(), resourceObject,
                             aggOptions, isRetry);
        if (null != result) {
            return result;
        }
        
        SailPointContext ctx = getContext();

        // Load the identity.
        Identity identity = ObjectUtil.lockIdentity(ctx, identityName);
        try {
            if (null == identity) {
                throw new GeneralException("Could not find identity: " + identityName);
            }
            Schema schema = app.getAccountSchema();
            if (null == schema) {
                throw new GeneralException("Application did not have an account schema: " + app);
            }
        
            // Turn the map into a ResourceObject.
            ResourceObject ro =
                AbstractConnector.defaultTransformObject(schema, resourceObject);
            
            // The ResourceObject is likely not exactly what a connector would
            // return since it is being sent in via a web service.  Instead of
            // storing all of the attributes on the link, we'll just make sure
            // that a link exists by bootstrapping a link stub if there isn't one
            // yet.  Then we'll use Identitizer.load() to do a targeted re-agg
            // using the IIQ connector.
            Attributes<String,Object> attrs =
                new Attributes<String,Object>(aggOptions);
            Identitizer idt = new Identitizer(ctx, attrs);
            idt.setRefreshSource(Source.WebService, ctx.getUserName());
            idt.setSources(Arrays.asList(new Application[] { app }));
            idt.prepare();
            Link link = idt.getOrCreateLinkStub(identity, app, ro);
            
            // Save the link if we created a new one.
            if (null == link.getId()) {
                // Since we were told which identity this link is a part of,
                // set as manually correlated so it won't get moved on reagg if
                // the correlation rules don't match up.
                link.setManuallyCorrelated(true);
                
                ctx.saveObject(link);
            }
        
            // Now that the link has been bootstrapped, reload to do a targeted
            // re-agg.
            idt = new Identitizer(ctx, attrs);
            idt.setRefreshSource(Source.WebService, ctx.getUserName());
            idt.setPromoteAttributes(true);
            // Expect this to be passed in the options since it can be expensive.
            //idt.setCorrelateEntitlements(true);
            idt.setSources(Arrays.asList(new Application[] { app }));
            idt.prepare();
            idt.reload(identity);
        
            // Finally, refresh so that entitlements get correlated, etc...
            idt.refresh(identity);
        
            // Make sure everything is saved.
            ctx.saveObject(identity);
            ctx.commitTransaction();
        }
        finally {
            if (null != identity) {
                ObjectUtil.unlockIdentity(ctx, identity);
            }
        }
    
        // Check for errors in identitizer?
        return getResult(true);
    }
    
    /**
     * Reaggregate by passing the given resource object to the aggregator.  This
     * should usually not be used since the ResourceObject isn't likely the way
     * that we want it - rules haven't been run, connector is an external system,
     * etc...
     */
    private RequestResult aggregateResourceObject(String identityName, String appName,
                                                  Map<String,Object> resourceObject,
                                                  Map<String,Object> aggOptions,
                                                  boolean isRetry)
        throws GeneralException {
        
        // First, check if the identity is locked.  If so, return a retry result.
        RequestResult result =
            queueAggIfLocked(identityName, appName, resourceObject,aggOptions, isRetry);
        if (null != result) {
            return result;
        }

        // Specify the resource object and app name.
        Attributes<String,Object> args = new Attributes<String,Object>();
        args.put(Aggregator.ARG_APPLICATIONS, appName);
        args.put(Aggregator.ARG_RESOURCE_OBJECTS, resourceObject);

        // TODO: Add an arg to pass in identity name to strap a link onto
        // an identity sans correlation?  I'm Going to punt on this for now.
        // Implementing this raises a lot of questions.  What happens if the
        // specified identity differs from what correlation tells us?  Do we
        // add the link to the specified identity and mark it as manually
        // correlated so it doesn't get moved?  What about if there is an
        // existing link on a different identity that is already manually
        // correlated?  Need to come back around to this if it becomes an
        // issue.
      
        // How heavy do we want to make the refresh part by default?  For
        // now, keep it light and let the client request heavier processing.
        args.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
        args.put(Identitizer.ARG_REFRESH_MANAGER_STATUS, true);
      
        // Merge the given options into the aggregator options to allow
        // defining and overriding behavior.
        if (null != aggOptions) {
            args.putAll(aggOptions);
        }
      
        // Ready ... aim ... FIRE!!
        SailPointContext ctx = getContext();
        Aggregator agg = new Aggregator(ctx, args);
        agg.setSource(Source.WebService, ctx.getUserName());
        agg.execute();

        // Might want to add some more info here later.
        return getResult(!agg.hadProblems());
    }
    
    /**
     * Return the name of the IIQ application that corresponds to the given app
     * name passed in.  The given app name may be an actual IIQ application name
     * or the name of the remote application as defined in the IntegrationConfig
     * managed resources list.  If no matching application is found in either
     * place, a general exception is thrown.
     * 
     * @param  appName  The name of the application to map.
     */
    public String mapApplicationName(String appName) throws GeneralException {
        
        String foundApp = appName;
        
        Application app = getContext().getObjectByName(Application.class, appName);
        if (null == app) {
            // No matching application, set the found to null.
            foundApp = null;
            
            // Try to reverse match from the managed resources in the
            // integration configs.
            List<IntegrationConfig> cfgs =
                getContext().getObjects(IntegrationConfig.class);
            for (IntegrationConfig cfg : cfgs) {
                ManagedResource res = cfg.getRemoteManagedResource(appName);
                if (null != res) {
                    foundApp = res.getLocalName();
                    break;
                }
            }
        }

        if (null == foundApp) {
            throw new GeneralException("Could not find application " + appName);
        }

        return foundApp;
    }
    
    private static RequestResult getResult(boolean success) {
        String status =
            (success) ? RequestResult.STATUS_SUCCESS : RequestResult.STATUS_FAILURE;
        return new RequestResult(status, null, null, null);
    }
}
