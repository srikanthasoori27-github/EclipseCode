/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.Explanator;
import sailpoint.api.ManagedAttributeStatistician;
import sailpoint.api.ManagedAttributeStatistician.PopulationStats;
import sailpoint.api.ManagedAttributeStatistician.SizeExceededException;
import sailpoint.api.ObjectConfigService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.ManagedAttributeDetailsAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ManagedAttribute.Type;
import sailpoint.rest.ui.classification.ClassificationListResource;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.SearchInputDefinition;
import sailpoint.score.EntitlementScoreConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ManagedAttributeService;
import sailpoint.service.ManagedAttributeServiceContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.GlobalSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.ClassicManagedAttributeSuggestService;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * A listing resource for ManagedAttributes.  Note that this has resources for
 * both a grid listing and a more simple listing that can be used as a
 * datasource for a suggest.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path("/managedAttributes")
public class ManagedAttributesResource extends BaseListResource implements ManagedAttributeServiceContext, ClassicManagedAttributeSuggestService.ClassicManagedAttributeSuggestServiceContext {

    public static String MANAGED_ATTRIBUTE_TYPE_ONLY = "managedAttributeAttrTypeOnly";
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    private static Log log = LogFactory.getLog(ManagedAttributesResource.class);

    // Used for filtering results according to the LCM object request authority.
    @QueryParam("lcm") boolean lcm;
    @QueryParam("requesteeId") String requesteeId;
    // Fields used for filtering list results.
    // jsl - this should be "application" but I'm leaving it purview until
    // I know how to change the Ext to use the new name
    @QueryParam("purview") String purview;
    @QueryParam("type") String type;
    @QueryParam("attribute") String attribute;
    @QueryParam("value") String value;
    //Used to exclude certain types. Most prominently Permissions.
    //If type is included in the queryParams, it will be removed from excludedTypes
    @QueryParam("excludedTypes") List<String> excludedTypes;

    // requestable should be true by default
    @QueryParam("requestable") Boolean requestable = true;
    
    // Advanced search query parameters.
    @QueryParam("applicationIds") List<String> applicationIds;
    @QueryParam("attributes") List<String> attributes;
    @QueryParam("ownerId") String ownerId;
    @QueryParam("applicationName") String appName;

    private String filterString;

    // A cached EntitlementScoreConfig for getting entitlement risk.
    private EntitlementScoreConfig entitlementScoreConfig;
    // This variable can be used to indicate that no objects should be returned so we can avoid
    // making unnecessary queries.
    private boolean returnNothing;

    ObjectConfig entitlementConfig;

    public ManagedAttributesResource() {
        super();
    }
    public ManagedAttributesResource(BaseResource parent) {
        super(parent);
    }
    public ManagedAttributesResource(String requesteeId, boolean isLcm, BaseResource parent) 
    {
        super(parent);
        this.requesteeId = requesteeId;
        this.lcm = isLcm;

        /** Bug #13593 - don't return Permissions on lcm request access searches **/
        if(this.lcm) {
            this.excludedTypes = new ArrayList<String>();
            excludedTypes.add(Type.Permission.name());
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GRID
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Builds of a map containing details of the managed attribute including extended
     * attribute display values.
     * @param id The managed attribute id.
     * @param refererType The type of the referer (see ManagedAttributeDetailsAuthorizer)
     * @param refererId The ID of the referering object (see ManagedAttributeDetailsAuthorizer)
     * @return The map.
     * @throws GeneralException
     */
    @GET
    @Path("{id}")
    public Map<String, Object> getManagedAttributeDetails(@PathParam("id") String id, @QueryParam("refererType") String refererType, @QueryParam("refererId") String refererId) throws GeneralException {

        // I know I know, we shouldn't look at the session here, but most of the managed attribute details are in JSF beans,
        // so we need this to know if we are authorized to view these details.
        String authorizedIdentity = (String)getSession().getAttribute(IdentityDTO.VIEWABLE_IDENTITY);
        Map<String, Object> result = new HashMap<String, Object>();

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, id);
        if (managedAttribute == null) {
            return result;
        }

        authorize(new ManagedAttributeDetailsAuthorizer(managedAttribute, refererType, refererId, authorizedIdentity));

        result.put("id", managedAttribute.getId());
        result.put("name", managedAttribute.getName());
        result.put("displayName", managedAttribute.getDisplayName());
        result.put("displayableName", managedAttribute.getDisplayableName());
        result.put("application", managedAttribute.getApplication().getName());
        result.put("attribute", managedAttribute.getAttribute());
        result.put("value", managedAttribute.getValue());
        result.put("type", managedAttribute.getType());

        String requestableMsg = managedAttribute.isRequestable() ? MessageKeys.YES : MessageKeys.NO;
        result.put("requestable", Internationalizer.getMessage(requestableMsg, getLocale()));

        if (managedAttribute.getOwner() != null) {
            result.put("owner", managedAttribute.getOwner().getDisplayableName());
        }

        result.put("description", managedAttribute.getDescription(getLocale()));

        ObjectConfigService objectConfigService = new ObjectConfigService(getContext(), getLocale());

        Map<String, String> valuesMap = objectConfigService.getExtendedAttributeDisplayValues(managedAttribute);
        if (!Util.isEmpty(valuesMap)) {
            List<Map<String, String>> extendedAttrs = new ArrayList<Map<String, String>>();
            for (String key : valuesMap.keySet()) {
                Map<String, String> pairMap = new HashMap<String, String>();
                pairMap.put("key", key);
                pairMap.put("value", valuesMap.get(key));

                extendedAttrs.add(pairMap);
            }

            result.put("extendedAttributes", extendedAttrs);
        }

        return result;
    }

    /**
     * Returns a ListResult containing Classifications of the managed attribute.
     * 
     * @param id The managed attribute id.
     * @param refererType The type of the referer (see ManagedAttributeDetailsAuthorizer)
     * @param refererId The ID of the referering object (see ManagedAttributeDetailsAuthorizer)
     * @return The ListResult.
     * @throws GeneralException
     */
    @Path("{id}/classifications")
    public ClassificationListResource getManagedAttributeClassifications(@PathParam("id") String id, @QueryParam("refererType") String refererType, @QueryParam("refererId") String refererId) throws GeneralException {

        // I know I know, we shouldn't look at the session here, but most of the managed attribute details are in JSF beans,
        // so we need this to know if we are authorized to view these details.
        String authorizedIdentity = (String)getSession().getAttribute(IdentityDTO.VIEWABLE_IDENTITY);

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, id);
        if (managedAttribute == null) {
            return null;
        }

        authorize(new ManagedAttributeDetailsAuthorizer(managedAttribute, refererType, refererId, authorizedIdentity));

        return new ClassificationListResource(ManagedAttribute.class, id, this);
    }

    @Override
    protected QueryOptions getQueryOptions() throws GeneralException{
        QueryOptions qo = super.getQueryOptions();
        IdentitySearchUtil searchUtil = new IdentitySearchUtil(getContext());

        List<Filter> filters = new ArrayList<Filter>();
        searchUtil.getDynamicFilters(getRequest(), filters, null, getEntitlementConfig());
        for (Filter f : filters) {
            qo.add(f);
        }

        if(log.isInfoEnabled()) {
            log.info("QueryOptions: " + qo.getFilters() + " Orderings: " + qo.getOrderings());
        }
        return qo;
    }

    public ObjectConfig getEntitlementConfig() {
        if(entitlementConfig==null) {
            entitlementConfig = ObjectConfig.getObjectConfig(ManagedAttribute.class);
        }
        return entitlementConfig;
    }

    /**
     * Kludge to fix the mysterious "Application-name" that comes in from somewhere.
     * jsl - this was a hack to get started, it may be gone but don't count on it...
     */
    @Override
    public String fixSortBy(String s) {
        if ("Application-name".equals(s))
            s = "application-name";
        return s;
    }

    /**
     * Post process a Map containing the results of a query.
     * This the map keys will be the names of the columns
     * returned by getProjectionColumns and the map values will
     * be the corresponding values from the Object[] returned by the 
     * SailPointContext.search method for one row.
     */
    @Override
    public Map<String,Object> convertRow(Map<String,Object> rawObject, String columnsKey ) throws GeneralException{
        EntitlementDescriber describer = new EntitlementDescriber();
        /** Get the application id **/
        String appId = (String)rawObject.get("application.id");
        if (appId == null) {
            // should only be null in an un-upgraded database, 
            // what to do now?
        }
        else {
            // jsl - this was formerly done in a calculateColumn overload but
            // we're doing everything else here...
            int riskScore;
            if (!rawObject.containsKey("riskScore")) {
                riskScore = describer.getEntitlementScore(getContext(),
                        (String) rawObject.get("application.name"),
                        (String) rawObject.get("attribute"),
                        (String) rawObject.get("value"),
                        rawObject.get("type").toString());
                rawObject.put("riskScore", riskScore);
            } else {
                riskScore = (Integer)rawObject.get("riskScore");
            }

            /** Add the pretty colors! **/
            rawObject.put(RoleListResource.COL_COLOR, WebUtil.getScoreColor(riskScore));
            
            UserAccessUtil.convertPopulationStatistics(rawObject);
        }

        return super.convertRow(rawObject, columnsKey);
    }
    
    /**
     * Iterate list result check if there's any extended managed attribute has Identity type.
     * These are stored as ID for uniqueness we need to convert value into displayable name in UI
     * @param result - List result returned from query
     */
    protected void convertIdentity(ListResult result) throws GeneralException {
        if (result != null){
            List<Map<String, Object>> resultMapList = result.getObjects();
            for(Map<String,Object> resultMap : resultMapList){
                convertIdentity(resultMap);
            }
        }
    }
    
    /**
     * Iterate list result check if there's any extended managed attribute has Identity type.
     * These are stored as ID for uniqueness we need to convert value into displayable name in UI
     * @param map - each map from the array list in list result
     */
    protected void convertIdentity(Map<String, Object> map) throws GeneralException {
        for(Map.Entry<String,Object> entry : map.entrySet()){
            convertIdentity(entry);
        }
    }
    
    /**
     * Iterate list result check if there's any extended managed attribute has Identity type.
     * These are stored as ID for uniqueness we need to convert value into displayable name in UI
     * @param entry - each entry in map
     */
    protected void convertIdentity(Map.Entry<String,Object> entry) throws GeneralException {

        String attr = entry.getKey();
        ObjectConfig entitlementConfig = ObjectConfig.getObjectConfig(ManagedAttribute.class);
        ObjectAttribute objAttr = entitlementConfig.getObjectAttribute(attr);
        if(objAttr != null){
            SearchInputDefinition.PropertyType entryType = objAttr.getPropertyType();
            if (entryType.equals(SearchInputDefinition.PropertyType.Identity)) {
                //convert identityID value into identity displayableName
                Object identityID = entry.getValue();
                if(identityID != null) {
                    Identity ident = ObjectUtil.getIdentityOrWorkgroup(getContext(), (String)identityID);
                    if (null != ident) {
                        String displayName = ident.getDisplayableName();
                        entry.setValue(displayName);
                    }
                }
            }
        }
    }

    @Override
    protected QueryOptions getQueryOptions(String columnKey) throws GeneralException {

        QueryOptions qo = super.getQueryOptions(null);
        // Add the LCM object request authority filter if this is an LCM request.
        if (this.lcm) {
            LCMConfigService configService = new LCMConfigService(getContext());
            configService.addLCMAttributeAuthorityFilters(qo, getLoggedInUser(), this.requesteeId);
        }

        MatchMode matchMode = new IdentitySearchUtil(getContext()).getLCMSearchMode();

        if (!Util.isNullOrEmpty(this.query)) {
            List<String> queries = Util.csvToList(this.query);
            for(String query : queries) {
                qo.add(Filter.or(
                    Filter.ignoreCase(Filter.like("application.name", query, matchMode)),
                    Filter.ignoreCase(Filter.like("displayableName", query, matchMode))
                ));
            }            
        }

        if (!isNullOrEmpty(this.applicationIds)) {
            qo.add(Filter.in("application.id", this.applicationIds));
        }

        if (this.purview != null) {
            qo.add(Filter.eq("application.id", this.purview));
        }

        if ( !Util.isNullOrEmpty(this.appName) ) {
            qo.add(Filter.ignoreCase(Filter.eq("application.name", appName)));
        }


        if (null != this.type) {
            qo.add(Filter.eq("type", this.type));
        }

        if (!Util.isEmpty(this.excludedTypes)) {
            //Let Type trump excludedTypes. Remove type from excludedTypes
            if (this.type != null) {
                excludedTypes.remove(this.type);
            }
            qo.add(Filter.not(Filter.in("type", this.excludedTypes)));
        }

        if (!Util.isNullOrEmpty(this.attribute)) {
            // This can be part of a suggest, so do a starts with query.
            qo.add(Filter.ignoreCase(Filter.like("attribute", this.attribute, matchMode)));
        }

        if (!isNullOrEmpty(this.attributes)) {
            qo.add(Filter.in("attribute", this.attributes));
        }

        if (!Util.isNullOrEmpty(this.value)) {
            // Don't search on descriptions until we have a full text search.
            // Doing a starts with on descriptions is not really useful.
            List<Filter> descFilters = new ArrayList<Filter>();

//            List<String> descCols =
//                ManagedAttribute.getPropertiesForLocale(super.getLocale());
//            for (int i=0; i<descCols.size(); i++) {
//                List<Filter> filters = new ArrayList<Filter>();
//                filters.add(Filter.ignoreCase(Filter.like(descCols.get(i), this.value, matchMode)));
//
//                // Match on the most specific Locale/region/variant.  If this is
//                // not the first one we're looking for, only match if the
//                // previous are null.
//                if (i > 0) {
//                    for (int j=0; j<i; j++) {
//                        filters.add(Filter.isnull(descCols.get(j)));
//                    }
//                }
//
//                // AND them together and add them to the list.
//                descFilters.add(Filter.and(filters));
//            }

            // OR together all of the description filters, as well as just
            // searching on the actual value.
            if (!Util.isNullOrEmpty(this.value)) {
                descFilters.add(Filter.ignoreCase(Filter.like("displayableName", this.value, matchMode)));
                descFilters.add(Filter.ignoreCase(Filter.like("value", this.value, matchMode)));
            }

            if (!descFilters.isEmpty()) {
                Filter f = (descFilters.size() == 1) ? descFilters.get(0) : Filter.or(descFilters);
                qo.add(f);
            }
        }

        if (null != this.requestable) {
            qo.add(Filter.eq("requestable", this.requestable));
        }
        
        if (!Util.isNullOrEmpty(this.ownerId)) {
            Identity owner = getContext().getObjectById(Identity.class, this.ownerId);
            qo.add(Filter.eq("owner", owner));
        }

        log.info("QueryOptions: " + qo.getFilters() + " Orderings: " + qo.getOrderings());

        return qo;
    }

    /**
     * Check whether the given list is null or empty (or just has a single empty
     * string).
     */
    private static boolean isNullOrEmpty(List<String> list) {
        if ((null == list) || list.isEmpty()) {
            return true;
        }
        
        // If the list is not empty, return true if there is a single empty string.
        return ((1 == list.size()) && Util.isNullOrEmpty(list.get(0)));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // SELECT ALL SUPPORT
    //
    ////////////////////////////////////////////////////////////////////////////

    // The session attribute used to store the filters for the managed
    // attributes grid.
    private static final String MANAGED_ATTRIBUTES_GRID_FILTERS =
        "managedAttributesGridFilters";

    // The session attribute used to store the identity filters for the managed
    // attributes grid.
    private static final String MANAGED_ATTRIBUTES_GRID_IDENTITY_FILTERS =
        "managedAttributesGridIdentityFilters";

    // The session attribute used to store the LCM request authority app filter.
    // Only used for identity searching.
    private static final String MANAGED_ATTRIBUTES_GRID_LCM_APP_FILTER =
        "managedAttributesGridLCMAppFilter";
    
    private static final String MANAGED_ATTRIBUTES_GRID_LCM_MANAGED_ATTR_FILTERS =
            "managedAttributesGridLCMManagedAttrFilter";
    
    private static final String MANAGED_ATTRIBUTES_GRID_NO_IDENTITIES_IN_SCOPE = 
            "managedAttributesGridNoIdentitiesInScope";

    /**
     * Save the given managed attribute filters on the session so that we can
     * recall them later if "select all" was chosen.
     */
    private void saveGridAttributeFilters(List<Filter> filters) {
        super.getSession().setAttribute(MANAGED_ATTRIBUTES_GRID_FILTERS, filters);
        
        super.getSession().removeAttribute(MANAGED_ATTRIBUTES_GRID_IDENTITY_FILTERS);
        super.getSession().removeAttribute(MANAGED_ATTRIBUTES_GRID_LCM_APP_FILTER);
        super.getSession().removeAttribute(MANAGED_ATTRIBUTES_GRID_LCM_MANAGED_ATTR_FILTERS);
    }

    /**
     * Save the given identity filters on the session so that we can recall them
     * later if "select all" was chosen.
     */
    private void saveGridIdentityFilters(List<Filter> identityFilters, Filter lcmAppFilter, Filter lcmAttrFilters, boolean noIdentitiesInScope) {
        super.getSession().removeAttribute(MANAGED_ATTRIBUTES_GRID_FILTERS);

        super.getSession().setAttribute(MANAGED_ATTRIBUTES_GRID_IDENTITY_FILTERS, identityFilters);
        super.getSession().setAttribute(MANAGED_ATTRIBUTES_GRID_LCM_APP_FILTER, lcmAppFilter);
        super.getSession().setAttribute(MANAGED_ATTRIBUTES_GRID_LCM_MANAGED_ATTR_FILTERS, lcmAttrFilters);
        super.getSession().setAttribute(MANAGED_ATTRIBUTES_GRID_NO_IDENTITIES_IN_SCOPE, noIdentitiesInScope);
    }

    /**
     * Return the IDs of the ManagedAttributes that match the saved filters from
     * saveGridFilters().
     */
    @SuppressWarnings("unchecked")
    public static List<String> getSavedFilterEntitlementIds(Map<String,Object> session,
                                                            SailPointContext context)
        throws GeneralException {

        List<String> ids = new ArrayList<String>();
        
        List<Filter> filters = (List<Filter>) session.get(MANAGED_ATTRIBUTES_GRID_FILTERS);
        List<Filter> identityFilters = (List<Filter>) session.get(MANAGED_ATTRIBUTES_GRID_IDENTITY_FILTERS);
        Filter appFilter = (Filter) session.get(MANAGED_ATTRIBUTES_GRID_LCM_APP_FILTER);
        Filter managedAttrFilter = (Filter) session.get(MANAGED_ATTRIBUTES_GRID_LCM_MANAGED_ATTR_FILTERS);
        boolean noIdentitiesInScope = Util.otob(session.get(MANAGED_ATTRIBUTES_GRID_NO_IDENTITIES_IN_SCOPE));

        if (null != identityFilters) {
            try {
                ManagedAttributeStatistician stater =
                    new ManagedAttributeStatistician(context);
                PopulationStats stats;
                if (noIdentitiesInScope) {
                    stats = new PopulationStats(0);
                } else {
                    stats = stater.crunchPopulationStats(identityFilters, appFilter, managedAttrFilter);                    
                }

                ids.addAll(stats.getManagedAttributeIds());
            }
            catch (SizeExceededException e) {
                // Just return an empty list.  If this happens, someone chose to
                // "select all" after getting the size warning.
            }
        }
        else {
            QueryOptions qo = new QueryOptions();
            qo.setFilters(filters);
            Iterator<Object[]> it = context.search(ManagedAttribute.class, qo, "id");
            while (it.hasNext()) {
                ids.add((String) it.next()[0]);
            }
        }

        return ids;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // LISTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return a list of the ManagedAttributes, possibly filtered by the available
     * attributes.
     * 
     * @param  valueFilter  An optional filter to apply to the value and
     *            description of the managed attributes being returned.  If
     *            specified, this is used in a "starts with" query.
     */
    @GET @SuppressWarnings("unchecked")
    public ListResult getManagedAttributes(@QueryParam("value") String valueFilter)
        throws GeneralException {

        // This resource is used on many pages (lcm, reports, advanced analytics,
        // role editing, certification remediations).  Since some of these are
        // based on capabilities, but others are based on whether the user is in
        // a context that would allow them to access this (eg - a certifier is
        // editing a work item), it is difficult to authorize all cases.  The
        // data provided from this resource is not very sensitive, so we will
        // just open it up for now.
        authorize(new AllowAllAuthorizer());

        ManagedAttributeService service = new ManagedAttributeService(getContext(), this,
                new BaseListResourceColumnSelector("managedAttributeAllKeys"));
        ListResult result = service.getResults(getQueryOptions(false, "displayName"), lcm);

        // Need to massage the result to put the pertinent description in a
        // well-known property and find the friendly version of any account
        // group values
        if (null != result.getObjects()) {
            for (Object o : result.getObjects()) {
                Map<String,Object> current = (Map<String,Object>) o;

                String desc = (String)current.get("description");
                String appId = (String)current.get(Util.getJsonSafeKey("application.id"));
                String attribute = (String)current.get("attribute");
                String value = (String)current.get("value");
                if (appId != null && desc == null) {
                	desc = Explanator.getDescription(appId, attribute, value, getLocale());
                }
                
                if (desc == null)
                    desc = "";

                current.put("description", desc);
                //Provide a web-safe description
                current.put("sanitizedDescription", WebUtil.sanitizeHTML(desc));
                current.put("displayValue", WebUtil.getGroupDisplayableName(
                    appId, attribute, value));
            }
        }

        return result;
    }

    
    /**
     * A pseudo-resource that returns a list of the names of the
     * ManagedAttributes, possibly filtered by the available attributes.
     *
     */
    @GET @Path("/names")
    public ListResult getManagedAttributeNames(@QueryParam("includeAppNames") boolean includeAppNames, @QueryParam("excludeNullAttributes") boolean excludeNulls)
        throws GeneralException {

        // This used to be used in LCM but is now used in various places (LCM
        // access request status report, advanced identity search).  Since this
        // is a subset of the data provided by /managedAttributes, we will allow
        // all access here.
        authorize(new AllowAllAuthorizer());

        ManagedAttributeService service;
        String columnKey;
        if (includeAppNames) {
            columnKey = ListFilterService.MANAGED_ATTRIBUTE_TYPE_WITH_APP;
        } else {
            columnKey = MANAGED_ATTRIBUTE_TYPE_ONLY;
        }
        service = new ManagedAttributeService(getContext(), this,
                new BaseListResourceColumnSelector(columnKey));

        QueryOptions qo = getQueryOptions(true, "attribute");

        if(excludeNulls) {
            //Do not return results with empty attribute
            qo.add(Filter.notnull("attribute"));
        }
        return service.getResults(qo, lcm);
    }
    
    /**
     * A resource for fetching the json used in Form Field ComboBox
     */
    @POST
    @Path("spcombo")
    @Consumes("application/x-www-form-urlencoded")
    public ListResult getManagedAttSuggest(@FormParam("start") int startRec,
            @FormParam("limit") int limitRec, @FormParam("query") String queryText,
            @FormParam("sort") String sortField, @FormParam("dir") String sortDir,
            @FormParam("filter") String filter, @FormParam("context") String context,
            @FormParam("suggestId") String suggestId) 
        throws GeneralException {

        // Forms used to use this, instead they go through FormSuggestBean and are authorized against the form/field there
        // We will leave this endpoint around in case any custom code is using it, so authorize against the global suggest whitelist.
        authorize(new SuggestAuthorizer(new GlobalSuggestAuthorizerContext(), ManagedAttribute.class.getSimpleName()));

        return getManagedAttrSuggestResult(startRec, limitRec, queryText, sortField, sortDir, filter);
    }

    /**
     * This ends up being called by SuggestResource, which is not ideal, but not interested in changing it now.
     */
    public ListResult getManagedAttrSuggestResult(int startRec, int limitRec, String queryText, String sortField, String sortDir,
                                                  String filter) throws GeneralException {
        this.start = startRec;
        this.limit = WebUtil.getResultLimit(limitRec);
        this.sortBy = sortField;
        this.sortDirection = sortDir;
        this.query = queryText;
        this.filterString = filter;

        ClassicManagedAttributeSuggestService suggestService = new ClassicManagedAttributeSuggestService(this);
        return suggestService.getManagedAttributeSuggestResult();
    }

    /**
     * Return Map of values used to represent Managed Attributes in the SPCombos
     * @param idOrName ID or Name of Managed Attribute
     * @param context SailPointContext used to query ManagedAttributes
     * @param filter FilterString used to further filter our query
     * @return Map representing the ManagedAttribute used for our SPCombo
     * @throws GeneralException
     */
    public static Map<String, Object> getSuggestObject(String idOrName, SailPointContext context, String filter)
        throws GeneralException{
        List<String> projectionColumns = getSuggestProjectionColumns();
        QueryOptions ops = new QueryOptions(Filter.or(Filter.eq("id", idOrName), Filter.eq("value", idOrName)));

        if (Util.isNotNullOrEmpty(filter)) {
            ops.add(Filter.compile(filter));
        }
        
        Iterator<Object[]> results = context.search(ManagedAttribute.class, ops, projectionColumns);
        if (results != null){
            if(results.hasNext()){
                return getResultMap(results.next());
            }
        }

        return null;
        
    }

    private static Map<String, Object> getResultMap(Object[] row) {
        return ClassicManagedAttributeSuggestService.getResultMap(row);
    }
    
    private static List<String> getSuggestProjectionColumns() {
        return ClassicManagedAttributeSuggestService.getProjectionColumns();
    }

    public String getRequesteeId() {
        return requesteeId;
    }

    public String getFilterString() {
        return this.filterString;
    }

    /**
     * Return a query options with the specific attributes set.
     */
    private QueryOptions getQueryOptions(boolean distinct, String orderBy)
        throws GeneralException {

        QueryOptions qo = getQueryOptions(null);
        qo.setDistinct(distinct);
        if (!Util.isNullOrEmpty(orderBy)) {
            qo.addOrdering(orderBy, true);
        }
        return qo;
    }

    public ListResult getListResult(String columnConfigKey,
                                    Class<? extends SailPointObject> clazz,
                                    QueryOptions searchFilters)
        throws GeneralException {
        ManagedAttributeService service = new ManagedAttributeService(getContext(), this, 
                new BaseListResourceColumnSelector(columnConfigKey));
        return service.getResults(searchFilters, lcm);
    }

    public void setEntitlementConfig(ObjectConfig entitlementConfig) {
        this.entitlementConfig = entitlementConfig;
    }
}
