/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;

import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.Capability;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.ExtState;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.Link;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * 
 * A service layer that deals with identities.
 * 
 * @author peter.holcomb
 *
 */
public class IdentityService {

    private static final Log log = LogFactory.getLog(IdentityService.class);
    
    private SailPointContext context;

    /**
     * The name of the request parameter used to specify the suggest context
     * in the IdentitySelectorConfig in
     * {@link #getIdentitySuggestQueryOptions(Map, Identity)}.
     */
    public static final String SUGGEST_CONTEXT = "context";

    /**
     * The name of the request parameter used to specify the suggest ID
     * in the IdentitySelectorConfig in
     * {@link #getIdentitySuggestQueryOptions(Map, Identity)}.
     */
    public static final String SUGGEST_ID = "suggestId";

    /**
     * The name of the request parameter used to specify the query prefix in
     * {@link #getIdentitySuggestQueryOptions(Map, Identity)}.
     */
    public static String COMBO_BOX_PREFIX = "query";

    /**
     * The name of the filter string query parameter.
     */
    private static final String FILTER_STRING_PARAM = "filterString";
    

    /**
     * Constructor.
     */
    public IdentityService(SailPointContext ctx) {
        this.context = ctx;
    }

    // ------------------------------------------------------------
    //
    // LINK RETRIEVAL METHODS
    //
    // ------------------------------------------------------------


    /**
     * Count the number of links for the given identity.
     * @param identity The identity whose links should be searched
     * @return Count of Links the given identity has.
     * @throws GeneralException
     */
    public int countLinks(Identity identity) throws GeneralException {
        return countLinks(identity, null);
    }

    /**
     * Count the number of links the identity has on the given application.
     * @param identity The identity whose links should be searched
     * @param application The application to search with.
     * @return Count of Links the given identity has on the applocation
     * @throws GeneralException
     */
    public int countLinks(Identity identity, Application application)
                throws GeneralException {

        if (Hibernate.isInitialized(identity.getLinks())){
            List<Link> links = application != null ? identity.getLinks(application) : identity.getLinks();
            return links != null ? links.size() : 0;
        } else {
            QueryOptions ops = buildBaseLinkQuery(identity, application, null, null);
            return context.countObjects(Link.class, ops);
        }
    }

    /**
     * Get a range of links for an Identity. (ordered by application.name)
     * @param identity The identity to get links for
     * @param start The start value
     * @param limit The maximum number of links to return.  (if zero all links are returned)
     * @return a range of links for an Identity. (ordered by application.name)
     */
    public List<Link> getLinks(Identity identity, int start, int limit) throws GeneralException {
        QueryOptions qo = buildBaseLinkQuery(identity, null, null, null);
        qo.addOrdering("application.name", true);
        if(start > 0) {
            qo.setFirstRow(start);
        }
        if(limit > 0) {
            qo.setResultLimit(limit);
        }
        Iterator<Link> linksIter = context.search(Link.class, qo);
        return Util.iteratorToList(linksIter);
    }

    /**
     * Returns a list of Links for the given identity on the given
     * application and instance.
     * @param identity The identity whose links should be searched
     * @param application The application to search with.
     * @param instance The application instance to search with.
     * @return Non-null list of Link objects returned from the search.
     * @throws GeneralException
     */
    public List<Link> getLinks(Identity identity, Application application, String instance)
                throws GeneralException {

        List<Link> links= null;

        if (Hibernate.isInitialized(identity.getLinks())){
            links = identity.getLinks(application, instance);
        } else {
            QueryOptions ops = buildBaseLinkQuery(identity, application, instance, null);
            links = context.getObjects(Link.class, ops);
        }

        return links != null ? links : Collections.EMPTY_LIST;
    }

    /**
     * Returns all the links from the Identity for the given Application.
     * @param identity The identity whose links should be searched
     * @param application The application to search with.
     * @return Non-null  List of Link objects if found. Otherwise an empty list will be returned.
     * @throws GeneralException
     */
    public List<Link> getLinks(Identity identity, Application application)
                throws GeneralException {
        return getLinks(identity, application, null);
    }

    public List<Link> getLinks(Identity identity, List<Application> apps, String instance)
        throws GeneralException {
        List<Link> links= new ArrayList<>();

        if (Util.isEmpty(apps)) {
            return identity.getLinks();
        } else {

            if (Hibernate.isInitialized(identity.getLinks())) {
                for (Application application : Util.safeIterable(apps)) {
                    links.addAll(getLinks(identity, application, instance));
                }
            } else {
                QueryOptions ops = buildBaseLinksQuery(identity, apps, instance, null);
                links = context.getObjects(Link.class, ops);
            }
        }

        return links != null ? links : Collections.EMPTY_LIST;
    }

    /**
     * Get the link associated to the given identity with the given id.
     * @param identity  The identity whose links should be searched.
     * @param id  The ID of the Link to return.
     * @return The link with the given ID.
     * @throws GeneralException
     */
    public Link getLinkById(Identity identity, String id) throws GeneralException{
        Link link = null;
        if (Hibernate.isInitialized(identity.getLinks())){
            link = identity.getLink(id);
        } else {
            link = context.getObjectById(Link.class, id);
        }

        return link;
    }

    /**
     * Gets a link for the specified resource object.
     *
     * @param identity The identity whose links should be searched
     * @param application The application to search with.
     * @param obj The resource object to search with. The identity, uuid and instance propertied
     *            are used in the search.
     * @return Matching Link object if found.
     * @throws GeneralException
     */
    public Link getLink(Identity identity, Application application, ResourceObject obj)
            throws GeneralException {

        Link link = null;

        if (Hibernate.isInitialized(identity.getLinks())){
            link = identity.getLink(application, obj);
        } else {
            link = queryLink(identity, application, obj.getInstance(), obj.getIdentity(), obj.getUuid());
        }

        return link;
    }


    /**
     * Retrieves the link from the identity with the given application, instance and
     * native identity.
     * @param identity The identity to search for. Must be non-null
     * @param application The application to search for. Must be non-null
     * @param instance The application instance to search for. Nullable.
     * @param nativeIdentity The native identity to search for. Must be non-null
     * @return The Link with the given information.
     * @throws GeneralException
     */
    public Link getLink(Identity identity,
                        Application application, String instance, String nativeIdentity) throws GeneralException{
        return getLink( identity, application,  instance,  nativeIdentity, null);
    }
    
    /**
     * Retrieves the link from the identity with the given application, instance,
     * native identity, and uuid.
     * @param identity The identity to search for. Must be non-null
     * @param application The application to search for. Must be non-null
     * @param instance The application instance to search for. Nullable.
     * @param nativeIdentity The native identity to search for. Must be non-null
     * @param uuid The uuid of the Kubj,
     * @return The Link with the given information.
     * @throws GeneralException
     */
    public Link getLink(Identity identity,
                        Application application, String instance, String nativeIdentity, String uuid) throws GeneralException{

        // Don't allow searches where the native identity is non null since
        // that could potentially result in multiple links being returned
        if (nativeIdentity == null)
            throw new IllegalArgumentException("nativeIdentity was null. A non-null native identity is required.");

        Link link = null;
        if (Hibernate.isInitialized(identity.getLinks())){
            link = identity.getLink(application, instance, nativeIdentity, uuid);
        } else {
            link = queryLink(identity, application, instance, nativeIdentity, uuid);
        }

        return link;
    }

    /**
     * Searches for a matching link on the given identity. Searches for links
     * on the given identity matching the provided application and instance.
     *
     * The link matching logic is a little complex, so you cannot purely use a
     * query here. Some additional filtering in memory must be done after
     * the query result set is returned. Since the set of links an identity
     * would have on a single application is small this should not be a problem.
     * It would be better if some of this logic could be removed. See
     * {@link sailpoint.object.Identity#getLink(Application, String, String)} and
     * {@link sailpoint.object.Identity#getLink(Application, ResourceObject)}
     * for more.
     *
     * If uuid is non-null we will prefer links which match the given uuid.
     *
     * If native identity is provided, links with a matching
     * native identity are preferred, assuming no uuid match is found.
     *
     * If a link cannot be found which matches the specified uuid or native
     * identity, any link that where the native identity
     * parameter matches the link's displayName property are returned.
     *
     * @param identity The identity to search for. Non null
     * @param application The application to find links for. Non null.
     * @param instance The application instance to search for. Can be null.
     * @param nativeIdentity The native identity to search for. Can be null.
     * @param uuid The account uuid to search for. May be null
     * @return Matching link object.
     * @throws GeneralException
     */
    private Link queryLink(Identity identity, Application application, String instance,
                          String nativeIdentity, String uuid)
                throws GeneralException {

        Link link = null;

        QueryOptions ops = buildBaseLinkQuery(identity, application, instance, null);

        List<Filter> identityFilters = new ArrayList<Filter>();
        // jsl - for consistency with Correlator we have to use case insensntive
        // search on nativeIdentity, displayName has been case sensntive
        identityFilters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));
        identityFilters.add(Filter.eq("displayName", nativeIdentity));
        if (uuid != null)
            identityFilters.add(Filter.eq("uuid", uuid));

        ops.add(Filter.or(identityFilters));

        // Loop thru the links we've found and select a winner.
        // We prioritize the selection based on what matched.
        // In order or priority - UUID, nativeIdentity, displayName
        Iterator<Link> links = null;
        links = context.search(Link.class, ops);
        try {
            while (links.hasNext()) {
                Link l = links.next();
                if (l.getUuid() != null && l.getUuid().equals(uuid)) {
                    link = l;
                    break;
                } else if (nativeIdentity != null && nativeIdentity.equalsIgnoreCase(l.getNativeIdentity())) {
                    link = l;
                    break;
                } else if (nativeIdentity != null && nativeIdentity.equals(l.getDisplayName())) {
                    // if we find a displayName match, store it but don't
                    // break just yet. We need to continue looping to determine
                    // if we can find a uuid or nativeIdentity match
                    link = l;
                }
            }
        } finally {
            Util.flushIterator(links);
        }

        return link;
    }

    /**
     * Convenience method which given an identity, app,
     * instance(nullable) and native identity(nullable),
     * builds our base query options.
     */
    private static QueryOptions buildBaseLinkQuery(Identity identity, Application application, String instance,
                                                   String nativeIdentity){
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("identity", identity));
        if (application != null)
            ops.addFilter(Filter.eq("application", application));
        if (instance != null)
            ops.addFilter(Filter.eq("instance", instance));
        if (nativeIdentity != null)
            ops.addFilter(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));

        return ops;
    }

    /**
     * Convenience method which given an identity, app,
     * instance(nullable) and native identity(nullable),
     * builds our base query options.
     */
    private static QueryOptions buildBaseLinksQuery(Identity identity, List<Application> apps, String instance,
                                                   String nativeIdentity){
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("identity", identity));
        if (!Util.isEmpty(apps))
            ops.addFilter(Filter.in("application", apps));
        if (instance != null)
            ops.addFilter(Filter.eq("instance", instance));
        if (nativeIdentity != null)
            ops.addFilter(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));

        return ops;
    }

    /**
     * Returns true if the identity has an account on any of the given
     * applications.
     * @param identity The identity to examine.
     * @param apps The list of applications
     * @return True if the identity has an account on any of the given applications.
     * @throws GeneralException
     */
    public boolean hasAccounts(Identity identity, List<Application> apps)
            throws GeneralException{

        boolean hasAuthapp = false;

        if (identity != null && apps != null && !apps.isEmpty()){
            if (Hibernate.isInitialized(identity.getLinks())){
                if ( Util.size(identity.getLinks()) > 0 ) {
                    for ( Application authApp : apps ) {
                        List<Link> accts = identity.getLinks(authApp);
                        if ( ( accts != null ) && ( accts.size() > 0 ) ) {
                            // no sense in continuing one is enough
                            hasAuthapp = true;
                            break;
                        }
                    }
                }
            } else {
                QueryOptions ops = new QueryOptions(Filter.eq("identity", identity));
                ops.add(Filter.in("application", apps));
                hasAuthapp = context.countObjects(Link.class, ops) > 0;
            }
        }

        return hasAuthapp;
    }

    // ------------------------------------------------------------
    //
    // IDENTITY VIOLATION METHODS
    //
    // ------------------------------------------------------------

    /**
     * Return a List of Identities that report to the given manager and have
     * policy violations.
     *
     * @param manager  The manager of the identities to return.
     *
     * @return A possibly-null list of Identities that report to the given
     *    manager and have policy violations.
     */
    public List<Identity> getSubordinatesWithViolations(Identity manager) {
        List<Identity> identities = null;
        try {
            if(manager!=null) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("identity.manager", manager));
                qo.add(Filter.eq("active", true));
                
                List<String> props = new ArrayList<String>();
                props.add("identity");
                Iterator<Object[]> it = context.search(PolicyViolation.class, qo, props);
                while(it.hasNext()) {
                    Object[] row = it.next();
                    Identity ident = (Identity)row[0];
                    if(identities==null) 
                        identities = new ArrayList<Identity>();
                    if(!identities.contains(ident)) {
                        identities.add(ident);
                    }
                }                
            }
        } catch (GeneralException ge) {
            if (log.isErrorEnabled())
                log.error("Unable to get violations for manager: " + ge.getMessage(), ge);
        }
        return identities;
    }

    /**
     * Return a List of Identities that have policy violations owned by the
     * given owner (or one of the owner's workgroups).
     *
     * @param owner  The Identity that owns the policy violation.
     *
     * @return A possibly-null list of Identities that have policy violations
     *    owned by the given owner (or one of the owner's workgroups).
     */
    public List<Identity> getIdentitiesWithViolationsOwnedByIdentity(Identity owner) {
        List<Identity> identities = null;
        try {
            if(owner!=null) {
                QueryOptions qo = new QueryOptions();
                qo.add(ObjectUtil.getOwnerFilterForIdentity(owner));
                qo.add(Filter.eq("active", true));
                qo.add(Filter.ne("identity.name", owner.getName()));
                
                List<String> props = new ArrayList<String>();
                props.add("identity");
                Iterator<Object[]> it = context.search(PolicyViolation.class, qo, props);
                while(it.hasNext()) {
                    Object[] row = it.next();
                    Identity ident = (Identity)row[0];
                    if(identities==null) 
                        identities = new ArrayList<Identity>();
                    if(!identities.contains(ident)) {
                        identities.add(ident);
                    }
                }                
            }
        } catch (GeneralException ge) {
            if (log.isErrorEnabled())
                log.error("Unable to get violations for owner: " + ge.getMessage(), ge);
        }
        return identities;
    }
    

    /**
     * Returns a list of GridState objects that are stored on the user's preferences.
     *
     * @param ident  The Identity for which to return GridStates.
     *
     * @return A possibly-null list of GridState objects that are stored on the
     *    user's preferences.
     */
    public List<GridState> getGridStates(Identity ident) {
        List<GridState> states = null;
        if (ident!=null)
            states = ident.getGridStates();
        
        return states;
    }
    
    /**
     * Returns the GridState object from the user's preferences specified by stateName.
     *
     * @param ident  The Identity for which to return the GridState.
     *
     * @return A non-null GridState for the given user with the given name.
     */
    public GridState getGridState(Identity ident, String stateName) {
        GridState state = null;
        
        List<GridState> states = getGridStates(ident);
        if(stateName!=null && states!=null) {
            for(GridState gState : states){
                if(gState.getName().equals(stateName))
                    return gState;
            }
        }
        
        if(state==null) {
            state = new GridState();
            state.setName(stateName);
        }
        return state;
    }

    /**
     * Save the given GridState on the given Identity. This commits the
     * transaction.
     *
     * @param ident  The Identity on which to save the GridState.
     * @param state  The GridState to save.
     */
    public void saveGridState(Identity ident, GridState state) 
    throws GeneralException {
        List<GridState> states = getGridStates(ident);
        
        /** Check to see if this grid state is already on the user's preferences **/
        if(states!=null) {
            for(Iterator<GridState> gStateIter = states.iterator(); gStateIter.hasNext();){
                GridState gState = gStateIter.next();
                if(gState.getName().equals(state.getName())) {
                    gStateIter.remove();
                    break;
                }
            }
               states.add(state);
        } else {
            states = new ArrayList<GridState>();
            states.add(state);
        }
        
        ident.setGridStates(states);
        context.saveObject(ident);
        context.commitTransaction();
    }
    
    /**
     * Save the given ExtState on the given Identity. This commits the
     * transaction.
     *
     * @param ident  The Identity on which to save the ExtState.
     * @param state  The ExtState to save.
     */
    public void saveState(Identity ident, ExtState state) 
    throws GeneralException {
        List<ExtState> states = getExtStates(ident);
        
        if (states == null) {
            states = new ArrayList<ExtState>();
            states.add(state);
        } else {
            for(Iterator<ExtState> extStateIter = states.iterator(); extStateIter.hasNext();){
                ExtState extState = extStateIter.next();
                if(extState.getName().equals(state.getName())) {
                    extStateIter.remove();
                    break;
                }
            }
            
            states.add(state);
        }

        ident.setExtStates(states);
        context.saveObject(ident);
        context.commitTransaction();
    }
    
    /** 
     * Return the specified state for the specified user.
     *
     * @param ident Identity whose state is being fetched
     * @param name  Name of the state that is being fetched
     * @return the specified state for the specified user; 
     *         null if no such state exists  
     */
    public ExtState getState(Identity ident, String name) {
        List<ExtState> states = getExtStates(ident);
        ExtState returnedState = null;

        if (name != null && states != null) {
            for (ExtState state : states) {
                if (name.equals(state.getName()))
                    returnedState = state;
            }
        }
        
        return returnedState;
    }
    
    /**
     * Return QueryOptions used to filter Identities using the given request
     * parameters and loggedInUser.
     *
     * @param requestParams  A Map of the HTTP request parameters.
     * @param loggedInUser   The logged in user.
     *
     * @return The QueryOptions used to filter Identities using the given info.
     */
    @SuppressWarnings("unchecked")
    public QueryOptions getIdentitySuggestQueryOptions(Map<String, ?> requestParams, Identity loggedInUser) throws GeneralException {
        QueryOptions qo;
        IdentityFilter idFilter = null;
        String id = (String) requestParams.get(SUGGEST_ID);

        Configuration selectorConfig = Configuration.getIdentitySelectorConfig();
        Map<String, IdentityFilter> identityFilters = null;
        if ( selectorConfig != null ) {
            identityFilters = (Map<String, IdentityFilter>) selectorConfig.get(Configuration.IDENTITY_FILTERS);
            if ( identityFilters != null ) {
                if (id != null) {
                    idFilter = Util.caseInsensitiveKeyValue(identityFilters, id);
                }
                if (idFilter == null) {
                    String context = (String) requestParams.get(SUGGEST_CONTEXT);
                    idFilter = identityFilters.get(context);
                }
            } else {
                log.warn("Missing Configuration for Identity Filters.");
            }
        } else {
            log.warn("Missing Selector config.");
        }
       
        if (idFilter != null) {
            Map<String, Object> queryParams = new HashMap<String, Object>();
            queryParams.putAll(requestParams);
            String queryString = (String) queryParams.get(COMBO_BOX_PREFIX);
            String [] parts;
            if (queryString != null && queryString.trim().length() > 0) {
               parts  = queryString.split(" ");
            } else {
                parts = new String[] {};
            }
            
            for (int i = 0; i < parts.length; ++i) {
                parts[i] = WebUtil.escapeJavascript(parts[i]);
            }
            
            queryParams.put("parts", Arrays.asList(parts));
            queryParams.put("loggedInUser", loggedInUser.getId());
            List<Identity> loggedInUserWorkgroups = loggedInUser.getWorkgroups();
            if (loggedInUserWorkgroups != null && !loggedInUserWorkgroups.isEmpty()) {
                String loggedInUserWorkgroupIds;
                List<String> loggedInUserWorkgroupIdList = new ArrayList<String>();
                for (Identity loggedInUserWorkgroup : loggedInUserWorkgroups) {
                    loggedInUserWorkgroupIdList.add(loggedInUserWorkgroup.getId());
                }
                loggedInUserWorkgroupIds = Util.listToQuotedCsv(loggedInUserWorkgroupIdList, '"', true);
                queryParams.put("loggedInUserWorkgroups", loggedInUserWorkgroupIds);
            }

            // Remove the "context" query parameter so that the SailPointContext
            // will be available in the rule.  This parameter is used to specify
            // which identity filter to use.
            queryParams.remove("context");
            
            qo = idFilter.buildQuery(queryParams, this.context);
            
            if (!idFilter.isIgnoreGlobal()) {
                IdentityFilter globalFilter = identityFilters.get("Global");
                QueryOptions globalQuery = globalFilter.buildQuery(queryParams, this.context);
                List<Filter> globalFilters = globalQuery.getRestrictions();
                qo.add(globalFilters.toArray(new Filter[globalFilters.size()]));
            }

            // if a filter string was specified then compile it
            if (requestParams.containsKey(FILTER_STRING_PARAM)) {
                String filterString = (String) requestParams.get(FILTER_STRING_PARAM);
                if (Util.isNotNullOrEmpty(filterString)) {
                    qo.add(SuggestHelper.compileFilterString(this.context, Identity.class, filterString));
                }
            }

            // If there is no script, assume the filter was generated through Velocity
            // and default to use scoping.
            if( null == qo.getScopeResults() ){
                qo.setScopeResults(true);
            }

            // Only return each identity once.
            qo.setDistinct(true);
        } else {
            qo = null;
        }
        
        return qo;
    }

    /**
     * Return QueryOptions used to filter Identities for delegating.  Cannot include the target of the cert item, nor
     * any workgroups the owner of the cert item is a member of (unless of course they have the appropriate rights).
     *
     * This is called in a BeanShell script from the DelegationRecipient IdentitySelectorConfiguration.
     *
     * @param context Sailpoint context
     * @param certificationItemId The id of the certification item
     * @param query The query string we are searching for
     * @return The QueryOptions used to filter Identities using the given info.
     * @throws GeneralException
     */
    public static QueryOptions getDelegationRecipientSuggestQueryOptions(SailPointContext context,
            String certificationItemId, String query) throws GeneralException {

        QueryOptions qo = new QueryOptions();

        CertificationItem item = context.getObjectById(CertificationItem.class, certificationItemId);
        Identity target = item.getIdentity(context);

        // Ensure user cannot delegate unto thynself.
        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(context, item.getParent().getCertification().getId());

        // The check for isSelfCertify is actually opposite of what you would think based on the name.  It's actually
        // checking to see if the action would result in a self certification, NOT if the user is able to self certify.
        if (target != null && item != null &&
                // First check if the target is an administrator
                !(target.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR) ||
                CertificationAuthorizer.isCertificationAdmin(target)) &&
                // Then check if this will result in a self certification
                selfCertificationChecker.isSelfCertify(target, Util.asList(item))) {
            qo.addFilter(Filter.ne("id", target.getId())); // exclude the target identity of the certification item (i.e. no self certify)
            List<String> ids = new ArrayList<String>();
            for(Identity group : Util.safeIterable(target.getWorkgroups()) ){
                ids.add(group.getId());
            }
            if (!Util.isEmpty(ids)) {
                qo.addFilter(Filter.not(Filter.in("id", ids))); // exclude any workgroups the cert item owner is a member of
            }
        }

        if (query != null && !"".equals(query)){
            List<Filter> nameFilters = new ArrayList<Filter>();
            nameFilters.add(Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START)));
            nameFilters.add(Filter.ignoreCase(Filter.like("firstname", query, Filter.MatchMode.START)));
            nameFilters.add(Filter.ignoreCase(Filter.like("lastname", query, Filter.MatchMode.START)));
            nameFilters.add(Filter.ignoreCase(Filter.like("displayName", query, Filter.MatchMode.START)));
            qo.addFilter(Filter.or(nameFilters));
        }

        List booleans = new ArrayList();
        booleans.add(true);
        booleans.add(false);
        qo.addFilter(Filter.in("workgroup", booleans));
        qo.setScopeResults(true);
        qo.addOrdering("firstname", true);
        qo.addOrdering("lastname", true);
        qo.addOrdering("name", true);
        qo.addOrdering("id", true);
        return qo;
    }
    
    /**
     * Returns a list of ExtState objects that are stored on the user's
     * preferences.
     *
     * @param ident  The Identity for which to return the ExtStates.
     *
     * @return A possibly-null list of ExtStates for the given user.
     */
    private List<ExtState> getExtStates(Identity ident) {
        List<ExtState> states = null;
        if (ident!=null)
            states = ident.getExtStates();
        
        return states;
    }

    /**
     * Get the identity object associated with the id if possible, otherwise gets an object by the
     * name passed in.
     * 
     * @param id the id to look up the object by
     * @param name the name to use if id doesn't work
     * @return the identity based on the parameters
     * @throws GeneralException if Identity is not found throws an exception
     */
    public Identity getIdentityFromIdOrName(String id, String name)
        throws GeneralException {

        Identity identity = null;

        if (!Util.isNullOrEmpty(id)) {
            identity = this.context.getObjectById(Identity.class, id);
        } else if (!Util.isNullOrEmpty(name)) {
            identity = this.context.getObjectByName(Identity.class, name);
        }

        if (identity == null) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_IDENTITY_NOT_FOUND));
        }

        return identity;
    }

    /**
     * Load the identity's display name from the database from their name
     * @param name
     * @return the identity's displayName
     * @throws GeneralException
     */
    public String resolveIdentityDisplayName(String name) throws GeneralException  {
        String displayName = name;
        if ( name != null ) {
            QueryOptions op = new QueryOptions();
            op.add(Filter.ignoreCase(Filter.eq("name", name)));
            Iterator<Object[]> rows = this.context.search(Identity.class, op, "displayName");
            if ( rows != null ) {
                int i = 0;
                while ( rows.hasNext() ) {
                    Object[] row = rows.next();
                    if ( row != null ) {
                        if ( row.length > 0 ) {
                            String display = (String)row[0];
                            if ( display != null ) {
                                displayName = display;
                            }
                        }
                    }
                    if ( i++ > 1 )
                        throw new GeneralException("Found two identities with the name ["+name+"] while attempting to resolve the user's display name.");
                }
            }
        }
        return displayName;
    }

    /**
     * Gets the identity from the policy violation given the ID
     * @param policyViolationId Id of PolicyViolation object
     * @return Identity object
     * @throws GeneralException
     */
    public Identity getIdentityFromPolicyViolation(String policyViolationId) throws GeneralException {
        Identity identity = null;
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("id", policyViolationId));
        Iterator<Object[]> violationResult = this.context.search(PolicyViolation.class, options, "identity");
        if (violationResult.hasNext()) {
            identity = (Identity)violationResult.next()[0];
        }
        
        return identity;
    }
}
