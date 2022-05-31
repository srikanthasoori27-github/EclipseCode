package sailpoint.service.suggest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SailPointObject;
import sailpoint.service.LCMConfigService;
import sailpoint.service.LCMConfigService.SelectorObject;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author: pholcomb
 * A service that takes a class name and returns a list of objects from that class (as long
 * as it is a sailpoint object).  This service is used by the SuggestResources to provide results
 * through the REST server to the ui.
 */
public class SuggestService {

    public static final String SP_OBJECT_PACKAGE = "sailpoint.object.";
    private static final String IDENTITY_ID = "identityId";
    private static final String ACCOUNT_ONLY = "accountOnly";
    public static final String REQUESTED_APPLICATIONS = "requestedApplications";
    public static final String APPLICATION_TYPE_PAM = "Privileged Account Management";

    private SuggestServiceContext suggestServiceContext;


    public SuggestService(SuggestServiceContext serviceContext) {
        this.suggestServiceContext = serviceContext;
    }

    /**
     * Get the list of objects that match the provided query objects for the given
     * suggestClass
     * @param qo Query options to filter the results
     * @return A ListResult representing the limited objects and total count
     * @throws GeneralException
     */
    public ListResult getObjects(QueryOptions qo) throws GeneralException {
        Class<?> suggestClass = getSuggestClass(suggestServiceContext.getSuggestClass());
        if (suggestClass == null) {
            ListResult res = new ListResult(Collections.EMPTY_LIST, 0);
            res.setStatus(ListResult.STATUS_FAILURE);
            res.addError("Unknown class:" + suggestServiceContext.getSuggestClass());
            return res;
        }

        List<Map<String, Object>> out = new ArrayList<>();
        int total = suggestServiceContext.getContext().countObjects(suggestClass, qo);
        if (total > 0) {
            out.addAll(SuggestHelper.getSuggestResults(suggestClass, qo, suggestServiceContext.getContext()));
        }

        return new ListResult(out, total);
    }

    /**
     * A utility method for figuring out the sailpoint object class from the className string
     * @param className Simple name of the class
     * @return Class referred to by the name
     */
    public Class getSuggestClass(String className) {
        // Strip out the "sailpoint.object." if it is in the string
        if (className.contains(SP_OBJECT_PACKAGE)) {
            className = className.replace(SP_OBJECT_PACKAGE, "");
        }

        Class suggestClass = ObjectUtil.getMajorClass(className);

        // not a major class, try evaluating the class name
        if (suggestClass == null) {
            String cName = "sailpoint.object." + className;
            try {
                suggestClass = Class.forName(cName);
            } catch (ClassNotFoundException e) {
                //no match...
            }
        }
        return suggestClass;
    }
    
    /**
     * Returns the default filter list based on the suggest class.
     * 
     * @return List of Filter
     * @throws GeneralException 
     */
    public List<Filter> getDefaultFilters() throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        String suggestClass = suggestServiceContext.getSuggestClass();
        Class<?> cls = getSuggestClass(suggestClass);

        //IIQSAW-1407 : private iPop only visible to its owner or systemAdmin
        if (GroupDefinition.class.equals(cls)) {
            filters.add(Filter.isnull("factory"));

            Identity loggedInUser = suggestServiceContext.getLoggedInUser();
            if (!loggedInUser.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
                filters.add(Filter.or(Filter.eq("private", false), Filter.eq("owner", loggedInUser)));
            }
            
        }
        return filters;        
    }

    /**
     * Get a list of distinct column values that match the query
     * @param options - used to configure method behavior
     * @return ListResult of distinct column values
     * @throws GeneralException
     */
    public ListResult getColumnValues(SuggestServiceOptions options) throws GeneralException, ClassNotFoundException {
        // Assume no-ones dumb enough to query on a non-SailPoint class
        @SuppressWarnings("unchecked")
        Class<? extends SailPointObject> suggestClass = getSuggestClass(suggestServiceContext.getSuggestClass());
        if (!SuggestHelper.isValidColumn(suggestServiceContext.getContext(), suggestClass, options.getColumn())) {
            ListResult res = new ListResult(Collections.EMPTY_LIST, 0);
            res.setStatus(ListResult.STATUS_FAILURE);
            res.addError("Unknown or invalid class or column:" + suggestServiceContext.getSuggestClass() + ", " + options.getColumn());
            return res;
        }

        QueryOptions qo = getColumnSuggestQueryOptions(suggestClass, options, true);
        List<Map<String, Object>> out = new ArrayList<>();
        int total = 0;

        if (qo != null) {
            //Don't allow values outside what the useraccess filters limit.
            if (options.isLcm() && options.getTargetIdentityId() != null) {
                LCMConfigService cfgService = new LCMConfigService(suggestServiceContext.getContext());
                if (cfgService != null) {
                    if (Bundle.class.equals(suggestClass)) {
                        Identity requester = suggestServiceContext.getLoggedInUser();
                        Identity target = suggestServiceContext.getContext().getObjectById(Identity.class, options.getTargetIdentityId());

                        // pass on the exclude value
                        QueryInfo qi = cfgService.getRoleSelectorQueryInfo(requester, target, true, true, options.isExclude());
                        qo.add(Filter.and(qi.getFilter()));
                    }
                }
            }
            // No identities are in scope if qo is null, so nothing to do
            total = ObjectUtil.countDistinctAttributeValues(suggestServiceContext.getContext(), suggestClass, qo, options.getColumn());
        }

        if (total > 0) {
            if (suggestServiceContext.getStart() > 0) {
                qo.setFirstRow(suggestServiceContext.getStart());
            }

            if (suggestServiceContext.getLimit() > 0) {
                qo.setResultLimit(suggestServiceContext.getLimit());
            }

            Iterator<Object[]> it = suggestServiceContext.getContext().search(suggestClass, qo, options.getColumn());
            while (it.hasNext()) {
                Object[] obj = it.next();
                Object prop = obj[0];

                // this property could be an enum value so do some special checking in the String case
                // otherwise we only need to know that it is not null
                if (prop != null) {
                    if (!(prop instanceof String && Util.isNothing((String) prop))) {
                        out.add(SuggestHelper.getSuggestColumnValue(suggestClass, options.getColumn(), prop,
                                    suggestServiceContext.getContext(),
                                    suggestServiceContext.getLocale()));
                    }
                }
            }
        }

        return new ListResult(out, total);
    }

    /**
     * Construct QueryOptions for use with column suggest
     * @param clazz Class we are searching on
     * @param options configurable options for suggest behavior
     * @return QueryOptions, may be null if nothing in scope.
     * @throws GeneralException
     */
    public QueryOptions getColumnSuggestQueryOptions(Class<? extends SailPointObject> clazz, SuggestServiceOptions options, boolean addQueryFilter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        SailPointObject sailpointObj;
        try {
            sailpointObj = clazz.newInstance();
        } catch (Exception e) {
            // shouldn't ever happen if suggestClass is non-null, but...
            throw new GeneralException("Problem getting an instance of class: " + clazz.getSimpleName(), e);
        }

        /** When it comes to lcm requests, we have to use the lcm config service to configure the query
         * options so the scoping is defined correctly.  
         */
        if (options.isLcm() && Identity.class.equals(clazz)) {
            LCMConfigService svc = new LCMConfigService(suggestServiceContext.getContext(),
                    suggestServiceContext.getLocale(), suggestServiceContext.getUserTimeZone(),
                    options.getLcmQuicklinkName());
            qo = svc.getRequestableIdentityOptions(suggestServiceContext.getLoggedInUser(),
                    suggestServiceContext.getLoggedInUserDynamicScopeNames(), options.getLcmQuicklinkName(),
                    options.getLcmAction());
        }
        else if ((sailpointObj != null) && (!sailpointObj.hasAssignedScope())) {
            qo.setScopeResults(false);
        }
        else {
            // LCM handles scoping on its own through selector rules
            if (!options.isLcm()) {
                qo.setScopeResults(true);
            }
            Identity identity = suggestServiceContext.getLoggedInUser();
            if (identity != null) {
                qo.addOwnerScope(identity);
            }
        }

        if (qo != null) {
            qo.add(Filter.notnull(options.getColumn()));

            if (addQueryFilter && !Util.isNullOrEmpty(suggestServiceContext.getQuery())) {
                // formerly always had Filter.ignoreCase here, but extended attributes don't necessarily
                // have one which would make the query invalid, in 7.1 we let HQLFilterVisitor figure it out
                qo.add(Filter.like(options.getColumn(), suggestServiceContext.getQuery(), Filter.MatchMode.START));
            }

            qo.setDistinct(true);
            boolean ascending = true;
            if ("DESC".equalsIgnoreCase(options.getDirection())) {
                ascending = false;
            }
            qo.addOrdering(options.getColumn(), ascending);

            if (!Util.isNullOrEmpty(suggestServiceContext.getFilterString())) {
                qo.add(SuggestHelper.compileFilterString(this.suggestServiceContext.getContext(), clazz, suggestServiceContext.getFilterString()));
            }

            if (options.getAdditionalFilter() != null) {
                qo.add(options.getAdditionalFilter());
            }
        }

        return qo;
    }

    /**
     * Get a list of values for the given application and attribute
     *
     * NOTE: This handles link attribute values, not managed attribute values.
     *
     * @param application Application Name
     * @param attribute Attribute name
     * @param isPermission Boolean True if has permission, False otherwise
     * @return ListResult Values for the given application and attribute
     * @throws GeneralException
     */
    public ListResult getApplicationAttributeValues(
            String application, String attribute, boolean isPermission)
            throws GeneralException {

        List<Object> values = null;
        if (isPermission) {
            values = ObjectUtil.getLinkPermissionRights(
                    suggestServiceContext.getContext(),
                    suggestServiceContext.getLocale(),
                    application,
                    attribute,
                    suggestServiceContext.getQuery(),
                    suggestServiceContext.getStart(),
                    suggestServiceContext.getLimit());
        }
        else {
            values = ObjectUtil.getLinkAttributeValues(
                    suggestServiceContext.getContext(),
                    application,
                    attribute,
                    suggestServiceContext.getQuery(),
                    suggestServiceContext.getStart(),
                    suggestServiceContext.getLimit());
        }

        int count = ObjectUtil.countLinkAttributeValues(
                suggestServiceContext.getContext(),
                application,
                attribute,
                suggestServiceContext.getQuery());

        // New suggests require objects with name and displayName, so give it to them.
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (Object value: Util.safeIterable(values)) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("displayName", value);
            result.put("name", value);
            results.add(result);
        }

        return new ListResult(results, count);
    }

    /**
     * Return the Filter used for the LCM account-only application suggest.
     */
    public Filter getLCMAccountOnlyAppFilter(String requestee) throws GeneralException {
        return this.getLCMAccountOnlyAppFilter(requestee, Collections.<String>emptyList());
    }

    /**
     * Return the Filter used for the LCM account-only application suggest.  Takes into account applications
     * that are currently being requested.
     * @param requestee The id of the request target
     * @param requestedApplications List of ids of requested applications
     * @return LCM account-only application filter
     */
    @SuppressWarnings("unchecked")
    public Filter getLCMAccountOnlyAppFilter(String requestee, List<String> requestedApplications) throws GeneralException {
        // Fake up a request like the UI would generate.
        Map request = new HashMap();
        request.put(IDENTITY_ID, requestee);
        request.put(ACCOUNT_ONLY, "true");
        request.put(REQUESTED_APPLICATIONS, requestedApplications);
        return getLCMFilter(request, LCMConfigService.SelectorObject.Application);
    }

    /**
     * @return Filter in the context of a LCM request.  This method also sets _returnNothing to true
     * when it's appropriate.  In that case the returned Filter will be null.  However, if _returnNothing
     * remains false and a null Filter is returned, that means there are no restrictions on the query and
     * everything should be returned.
     */
    private Filter getLCMFilter(Map request, LCMConfigService.SelectorObject type) throws GeneralException {
        // Scoping will be applied in the rules.  If no rule is configured no scoping is applied.
        Identity requestor = suggestServiceContext.getLoggedInUser();
        LCMConfigService lcmConfig = new LCMConfigService(suggestServiceContext.getContext());
        boolean isSelfService = isSelfService(request);
        Identity requestee = getRequestee(request);

        QueryInfo selectorQueryInfo;
        if (type == SelectorObject.Role) {
            // Note that we're always requesting both permitted and manually assignable roles here, but the 
            // API determines whether or not the current user is actually authorized to get them.
            // This also applies the standard role exclusions (disabled, previously assigned, and 
            // pending roles) if exclude is true
            selectorQueryInfo = lcmConfig.getRoleSelectorQueryInfo(requestor, requestee, true, true, true);
        } else {
            /*
             * IIETN-6432 - If the request map has 'accountOnly=true', we need to return a blank QueryInfo since we
             * don't need to consult any rule-based object selector. However, we still need to ensure administrators get
             * a broader selection
             */
            if (request != null && Util.otob(request.get("accountOnly"))) {
                selectorQueryInfo = new QueryInfo(new QueryOptions());
            } else {
                // If not accountOnly, get the QueryInfo from the rules
                selectorQueryInfo = lcmConfig.getSelectorQueryInfo(requestor, requestee, type, isSelfService);
            }
            // Apply custom search options if they are available
            if (!selectorQueryInfo.isReturnNone()) {
                Filter additionalFilter = getAppFilter(request);
                if (additionalFilter != null) {
                    Filter baseFilter = selectorQueryInfo.getFilter();
                    Filter compositeFilter;
                    if (baseFilter == null) {
                        compositeFilter = additionalFilter;
                    } else {
                        compositeFilter = Filter.and(baseFilter, additionalFilter);
                    }
                    selectorQueryInfo = new QueryInfo(compositeFilter, false);
                }
            }
        }

        Filter returnFilter;
        if (selectorQueryInfo.isReturnNone()) {
            returnFilter = null;
        } else {
            returnFilter = selectorQueryInfo.getFilter();
        }

        return returnFilter;
    }

    /**
     * Return requestee identity
     * @param request - Map representation of the request
     * @return requestee's identity
     * @throws GeneralException
     */
    private Identity getRequestee(Map request) throws GeneralException{
        String requesteeId = (String) request.get(IDENTITY_ID);
        Identity requestee = null;
        if(requesteeId != null) {
            requestee = suggestServiceContext.getContext().getObjectById(Identity.class, requesteeId);
        }
        return requestee;
    }

    /**
     *
     * @param request Request object map
     * @return App filters
     * @throws GeneralException
     */
    private Filter getAppFilter(Map request) throws GeneralException {
        Filter filter;
        List<Filter> appFilters = new ArrayList<Filter>();

        // composite apps may either be excluded from the results if showComposite=true,
        // or the list may be limited to composites if excludeNonComposite=true
        if ((null != request.get("showComposite")) &&
                (!Boolean.parseBoolean((String)request.get("showComposite"))))
            appFilters.add(Filter.eq("logical", false));
        else if ((null != request.get("excludeNonComposite")) &&
                (Boolean.parseBoolean((String)request.get("excludeNonComposite"))))
            appFilters.add(Filter.eq("logical", true));

        if ((null != request.get("showAuthoritative")) &&
                (!Boolean.parseBoolean((String)request.get("showAuthoritative"))))
            appFilters.add(Filter.eq("authoritative", false));

        if ((null != request.get("showPAM")) &&
                (Boolean.parseBoolean((String)request.get("showPAM"))))
            appFilters.add(Filter.eq("type", APPLICATION_TYPE_PAM));

        if ((null != request.get("showAuthenticating")) &&
                (Boolean.parseBoolean((String)request.get("showAuthenticating"))))
            appFilters.add(Filter.eq("supportsAuthenticate", true));

        if ((null != request.get("proxyOnly")) &&
                (Boolean.parseBoolean((String)request.get("proxyOnly"))))
            appFilters.add(Filter.like("featuresString", Application.Feature.PROXY.toString()));

        if (Util.otob(request.get("showRequestable"))) {
            appFilters.add(Filter.join("id", "ManagedAttribute.application"));
            appFilters.add(Filter.eq("ManagedAttribute.requestable", true));
            appFilters.add(Filter.ne("ManagedAttribute.type", ManagedAttribute.Type.Permission.name()));
        }

        if (!Util.otob(request.get("showNonAggregable"))) {
            appFilters.add(Filter.eq("noAggregation", false));
        }

        if (request.get("aggregationType") != null) {
            appFilters.add(Filter.like("aggregationTypes", request.get("aggregationType")));
        }

        if (null != request.get("type")) {
            String type = (String)request.get("type");
            appFilters.add( Filter.ignoreCase(Filter.like("type", type, Filter.MatchMode.START)));
        }

        if (Util.otob(request.get("accountOnly"))) {
            appFilters.add(Filter.eq("supportsAccountOnly", true));

            // Only return an app if:
            //  a) it supports additional account requests and the user can do
            //     these, OR
            //  b) we know that the identity doesn't yet have an account
            //     on the application.
            String identityId = (String) request.get(IDENTITY_ID);
            if (null != identityId) {
                // Return an app if the user doesn't have a link on it yet.
                Filter linksByIdentity = Filter.eq("identity.id", identityId);
                Filter f = Filter.not(Filter.subquery("id", Link.class,
                        "application.id", linksByIdentity));
                /* If there are requested applications filter those out also */
                List<String> requestedApplications = (List<String>) request.get(REQUESTED_APPLICATIONS);
                if(!Util.isEmpty(requestedApplications)) {
                    Filter requestedApplicationsFilter = Filter.in("id", requestedApplications);
                    f = Filter.and(Filter.not(requestedApplicationsFilter), f);
                }

                // If the requester can request additional accounts, also return
                // apps that supports this.
                QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(suggestServiceContext.getContext());
                boolean selfService = isSelfService(request);
                boolean addtSupported = svc.isRequestControlOptionEnabled(suggestServiceContext.getLoggedInUser(),
                        suggestServiceContext.getLoggedInUserDynamicScopeNames(),
                        null,
                        QuickLink.LCM_ACTION_MANAGE_ACCOUNTS,
                        Configuration.LCM_ALLOW_MANAGE_ACCOUNTS_ADDITIONAL_ACCOUNT_REQUESTS,
                        selfService);
                if (addtSupported) {
                    f = Filter.or(f, Filter.eq("supportsAdditionalAccounts", true));
                }
                appFilters.add(f);
            }
        }

        if (Util.otob(request.get("showSync"))) {
            appFilters.add(Filter.eq("syncProvisioning", true));
        }

        if (appFilters.size() > 1){
            filter = Filter.and(appFilters);
        } else if (appFilters.size() == 1){
            filter = appFilters.get(0);
        } else {
            filter = null;
        }

        return filter;
    }

    /**
     * Check if request is selfService
     * @param request request object Map
     * @return true is request is selfservice
     * @throws GeneralException
     */
    private boolean isSelfService(Map request) throws GeneralException {
        String identityId = (String) request.get(IDENTITY_ID);
        return (null != identityId) && identityId.equals(suggestServiceContext.getLoggedInUser().getId());
    }

}
