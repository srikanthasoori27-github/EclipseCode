/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Tag;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskDefinition;
import sailpoint.object.Workflow;
import sailpoint.rest.RoleListResource;
import sailpoint.service.LCMConfigService;
import sailpoint.service.LCMConfigService.SelectorObject;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.service.suggest.SuggestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.modeler.RoleConfig;
import sailpoint.web.task.TaskDefinitionListBean;


/**
 * @author peter.holcomb
 *
 */
public class BaseObjectSuggestBean<E extends SailPointObject> extends BaseListBean {

    private static final Log log = LogFactory.getLog(BaseObjectSuggestBean.class);

    protected String query;
    protected String searchType;
    protected List<String> exclusionIds;
    protected String identityId;
    Filter _filter;
    List<String> _projectionColumns;
    String _defaultSortColumn;
    protected boolean _isReturnNothing;

    // Fields specific to Application suggests.
    Boolean userAllowedAddtAcctRequests;
    
    private static String SUGGEST_TYPE_ACCOUNTGROUP = "accountGroup";
    private static String SUGGEST_TYPE_INHERITED_ACCOUNT_GROUP = "inheritedAccountGroup";
    private static String SUGGEST_TYPE_APPLICATION = "application";
    private static final String SUGGEST_TYPE_SCOPE = "scope";
    private static final String SUGGEST_TYPE_POLICY = "policy";
    private static final String SUGGEST_TYPE_ROLE = "role";
    private static final String SUGGEST_TYPE_TASK = "task";
    private static final String SUGGEST_TYPE_TAG = "tag";
    private static final String SUGGEST_TYPE_GROUP = "group";
    private static final String SUGGEST_TYPE_ASSIGNABLE_ROLE = "assignableRole";
    private static final String SUGGEST_TYPE_MANUALLY_ASSIGNABLE_ROLE = "manuallyAssignableRole";
    private static final String SUGGEST_TYPE_EXCLUDED_ENTITLEMENT = "excludedEntitlement";
    private static final String SUGGEST_TYPE_LCM_ROLE = "lcmRole";
    private static final String SUGGEST_TYPE_LCM_APPLICATION = "lcmApplication";
    private static final String SUGGEST_TYPE_DESCRIBABLE_OBJECT = "describableObject";
    private static final String SUGGEST_TYPE_DYNAMIC_SCOPE = "dynamicScope";
    private static final String SUGGEST_TYPE_TARGET_SOURCE = "targetSource";

    /** A list of all roles that are assignable plus any that are permitted for the current identity **/
    private static final String SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE = "assignablePermittedRole";

    // actually these will become more complicated once we start 
    // specifying the types of roles that may be inherited or permitted
    // will need to pass in the type of the role we're editing
    private static final String SUGGEST_TYPE_INHERITABLE_ROLE = "inheritableRole";
    private static final String SUGGEST_TYPE_PERMITTABLE_ROLE = "permittableRole";
    private static final String SUGGEST_TYPE_DETECTABLE_ROLE = "detectableRole";
    private static final String SUGGEST_TYPE_IT_ROLE = "itRole";
    private static final String SUGGEST_TYPE_CONTAINER_ROLE = "containerRole";
    private static final String SUGGEST_TYPE_PROCESS = "process";
    private static final String SUGGEST_TYPE_RULE = "rule";
    private static final String SUGGEST_TYPE_SCHEMA_OBJECT_TYPE = "applicationSchema";
    private static final String SUGGEST_TYPE_CLASSIFICATION = "classification";
    private static final String QUERY = "query";
    private static final String SUGGEST_TYPE = "suggestType";
    private static final String RULE_TYPE = "ruleType";

    /** A string representation of a list of object ids to exclude from the query **/
    private static final String EXCLUSION_IDS = "exclusionIds";
    
    /** A boolean value that specifies whether to fetch permitted roles as well as assignable roles **/
    private static final String PERMITTED = "allowPermitted";
    /** A boolean value that specifies whether to fetch permitted roles as well as assignable roles **/
    private static final String ASSIGNABLE = "allowAssignable";

    /** The identity id being passed in **/
    private static final String IDENTITY_ID = "identityId";

    // jsl - I'm not sure where or if this is used but I would
    // rather use just "role" now
    private static String SUGGEST_TYPE_BUSINESSROLE = "businessRoles";

    private static final String EXCLUDED_ENTITLEMENTS_INCLUDED_APPS_REQUEST_PARAM = "includedApps";

    // list of included app ids to fetch the schemas for
    private static final String REQUEST_PARAM_SCHEMA_OBJECT_TYPE_INCLUDED_APPS = "includedApps";
    // if set to true we will not include account schema
    private static final String REQUEST_PARAM_FILTER_ACCOUNT_SCHEMA = "filterAccountSchema";
    
    private static final String DESCRIBABLE_SELECTED_CLASS = "selectedClass"; 
    
    /**
     * 
     */
    public BaseObjectSuggestBean() {
        _isReturnNothing = false;
        
        try {
            Map request = super.getRequestParam();
            query = (String)request.get(QUERY);
            searchType = (String)request.get(SUGGEST_TYPE);

            /** Check the request to see if there were any exclusion ids **/
            String exclusionIdsString = (String)request.get(EXCLUSION_IDS);
            if(exclusionIdsString!=null && !exclusionIdsString.equals("")) 
                exclusionIds = Util.stringToList(exclusionIdsString);

            identityId = (String)request.get(IDENTITY_ID);

            // Setup the default projection columns and sort columns.  These may
            // be tweaked by different suggest types.
            _projectionColumns = new ArrayList<String>();
            _projectionColumns.add("id");
            _projectionColumns.add("name");
            _defaultSortColumn = "name";

            if(SUGGEST_TYPE_BUSINESSROLE.equals(searchType)) {
                _scope = Bundle.class;
            } else if(SUGGEST_TYPE_ACCOUNTGROUP.equals(searchType) || SUGGEST_TYPE_INHERITED_ACCOUNT_GROUP.equals(searchType)) {
                _scope = ManagedAttribute.class;
                _projectionColumns.clear();
                _projectionColumns.add("id");
                _projectionColumns.add("displayableName");
                _projectionColumns.add("owner.displayName");
                _defaultSortColumn = "displayableName";
                if (SUGGEST_TYPE_INHERITED_ACCOUNT_GROUP.equals(searchType)) {
                    /* Candidates for group inheritance must meet two criteria:
                     * 1. The ManagedAttribute must be of type 'Group'
                     * 2. The selected Application needs a non-null group hierarchy attribute
                     */
                    String groupHierarchyAttribute = null;
                    List<String> groupObjectTypes = null;
                    String app = (String) request.get("application");
                    if (app != null) {
                        Application application = getContext().getObjectByName(Application.class, app);
                        if (application != null) {
                            groupHierarchyAttribute = application.getGroupHierarchyAttribute();
                            groupObjectTypes = application.getGroupSchemaObjectTypes();
                            if(Util.isEmpty(groupObjectTypes)) {
                                //If no groupSchemaObjectTypes were returned, default to "group" for
                                //backwards compatibility -rap
                                groupObjectTypes.add(Application.SCHEMA_GROUP);
                            }
                        }
                    }
                    
                    if (groupHierarchyAttribute == null) {
                        // If the application has no group hierarchy attribute we shouldn't even
                        // be giving the option to inherit anything
                        _isReturnNothing = true;
                    } else {
                        _filter = Filter.and(
                                Filter.or(Filter.eq("application.name", app), Filter.eq("application.id", app)),
                                Filter.in("type", groupObjectTypes));
                    }                    
                }
            } else if(SUGGEST_TYPE_APPLICATION.equals(searchType)) {
                _scope = Application.class;
                _filter = getAppFilter(request);
            } else if(SUGGEST_TYPE_SCOPE.equals(searchType)) {
                _scope = Scope.class;
            } else if(SUGGEST_TYPE_GROUP.equals(searchType)) {
                _scope = GroupDefinition.class;
            } else if (SUGGEST_TYPE_POLICY.equals(searchType)) {
                _scope = Policy.class;
                _filter = Filter.eq("template", new Boolean(false));
            }
            else if (SUGGEST_TYPE_ROLE.equals(searchType)) {
                _scope = Bundle.class;
                _projectionColumns.add("displayableName");
                _defaultSortColumn = "displayableName";
            }
            else if(SUGGEST_TYPE_ASSIGNABLE_ROLE.equals(searchType) || SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE.equals(searchType)) {
                _scope = Bundle.class;
                _filter = getAssignableRoleFilter();
            }
            else if(SUGGEST_TYPE_TASK.equals(searchType)) {
                _scope = TaskDefinition.class;
                _filter = Filter.and(
                        Filter.eq("template", false),
                        Filter.or(TaskDefinitionListBean.getTasksOnlyFilter(), Filter.isnull("type"))
                );
            }
            else if (SUGGEST_TYPE_DESCRIBABLE_OBJECT.equals(searchType)) {
                String selectedClassName = (String) request.get(DESCRIBABLE_SELECTED_CLASS);
                if (Util.isNullOrEmpty(selectedClassName)) {
                    _isReturnNothing = true;
                } else {
                    try {
                        _scope = Class.forName("sailpoint.object." + selectedClassName);
                        if (!Describable.class.isAssignableFrom(_scope)) {
                            _isReturnNothing = true;
                            log.error("Cannot return describable objects for class " + selectedClassName);
                            _scope = null;
                        }
                    } catch (ClassNotFoundException e) {
                        _isReturnNothing = true;
                        log.error("Cannot return describable objects for class " + selectedClassName);
                    }                    
                }
            }
            else if(SUGGEST_TYPE_DYNAMIC_SCOPE.equals(searchType)) {
                _scope = DynamicScope.class;
            }
            else if(SUGGEST_TYPE_TAG.equals(searchType)) {
                _scope = Tag.class;
            }
            else if(SUGGEST_TYPE_MANUALLY_ASSIGNABLE_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.and(Filter.in("type", rc.getManuallyAssignableRoleTypes()),
                        Filter.eq("disabled", false));
            }
            else if(SUGGEST_TYPE_INHERITABLE_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.in("type", rc.getInheritableRoleTypes());
            }
            else if(SUGGEST_TYPE_PERMITTABLE_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.in("type", rc.getPermittableRoleTypes());
            }
            else if(SUGGEST_TYPE_DETECTABLE_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.in("type", rc.getDetectableRoleTypes());
            }
            else if (SUGGEST_TYPE_CONTAINER_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.in("type", rc.getContainerRoleTypes());
            } else if (SUGGEST_TYPE_IT_ROLE.equals(searchType)) {
                RoleConfig rc = new RoleConfig();
                _scope = Bundle.class;
                _filter = Filter.in("type", rc.getItRoleTypes());
            } else if (SUGGEST_TYPE_PROCESS.equals(searchType)) {
                _scope = Workflow.class;
            } else if (SUGGEST_TYPE_EXCLUDED_ENTITLEMENT.equals(searchType)) {
                _scope = ManagedAttribute.class;

                _projectionColumns.clear();
                _projectionColumns.add("id");
                _projectionColumns.add("displayableName");
                _projectionColumns.add("application.name");
                _sort = "application.name";
                _secondarySort = "displayableName";
                String includedApps = (String) request.get(EXCLUDED_ENTITLEMENTS_INCLUDED_APPS_REQUEST_PARAM);
                if (includedApps != null && includedApps.trim().length() > 0) {
                    _filter = Filter.in("application.id", Util.csvToList(includedApps));
                } else {
                    _isReturnNothing = true;
                }
            } else if (SUGGEST_TYPE_SCHEMA_OBJECT_TYPE.equals(searchType)) {
                _scope = Application.class;

                _projectionColumns.clear();
                _projectionColumns.add("id");
                _projectionColumns.add("name");
                _projectionColumns.add("schemas.id");
                _projectionColumns.add("schemas.objectType");
                _sort = "name";
                _secondarySort = "schemas.objectType";
                String includedApps = (String) request.get(REQUEST_PARAM_SCHEMA_OBJECT_TYPE_INCLUDED_APPS);
                if (includedApps != null && includedApps.trim().length() > 0) {
                    _filter = Filter.in("id", Util.csvToList(includedApps));
                    if ("true".equalsIgnoreCase(getRequestParameter(REQUEST_PARAM_FILTER_ACCOUNT_SCHEMA))) {
                        _filter = Filter.and(_filter, Filter.ne("schemas.objectType", Connector.TYPE_ACCOUNT));
                    }
                } else {
                    _isReturnNothing = true;
                }
            } else if (SUGGEST_TYPE_RULE.equals(searchType)) {
                _scope = Rule.class;
                String ruleType = (String)request.get(RULE_TYPE);
                _filter = Filter.eq("type", ruleType);
            } else if (SUGGEST_TYPE_LCM_ROLE.equals(searchType)) {
                _scope = Bundle.class;
                try {
                    // Get an LCM filter for roles.  Note that this sets _isReturnNothing
                    // to true as needed and applies exclusions for us ahead of time
                    _filter = getLCMFilter(request, LCMConfigService.SelectorObject.Role);
                } catch (Exception e) {
                    _isReturnNothing = true;
                    log.error("Failed to get a valid filter for an LCM role request", e);
                }
            } else if (SUGGEST_TYPE_LCM_APPLICATION.equals(searchType)) {
                _scope = Application.class;
                try {
                    _filter = getLCMFilter(request, LCMConfigService.SelectorObject.Application);
                } catch (Exception e) {
                    _isReturnNothing = true;
                    log.error("Failed to get a valid filter for an LCM application request", e);
                }
            } else if (SUGGEST_TYPE_TARGET_SOURCE.equals(searchType)) {
                _scope = TargetSource.class;
            } else if (SUGGEST_TYPE_CLASSIFICATION.equals(searchType)) {
                _scope = Classification.class;
                _projectionColumns.add("displayableName");
                _defaultSortColumn = "displayableName";
            }
        }
        catch (GeneralException e) {
            // RoleConfig constructor can throw on database access
            addMessage(e);
        }
    }


    public List<String> getProjectionColumns() throws GeneralException {
        return _projectionColumns;
    }

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return _defaultSortColumn;
    }

    @Override
    public Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("name", "name");
        columnMap.put("displayableName", "displayableName");
        return columnMap;
    }
    
    /** Overriding this if the suggest requires special behavior on the getCount()
     * See bug 6406 for more explanation 
     */
    @Override
    public int getCount() throws GeneralException {
        if(SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE.equals(searchType)) {
            this.getRows();
        } else if (SUGGEST_TYPE_SCHEMA_OBJECT_TYPE.equals(searchType)) {
            // Same issue as mentioned in bug 6406 above with multiple account object types.
            // Also, since the rows are cached so we should not worry about the overhead of calling getRows() multiple times.
            this.getRows();
            _count = _rows.size();
        } else {
            if (_isReturnNothing) {
                _count = 0;
            } else {
                _count = super.getCount(); 
            }
        }
        return _count;
    }

    /** We are overriding in order to append the list of permitted roles onto the assigned roles coming
     * back from the query 
     */
    @Override
    public List<Map<String,Object>> getRows() throws GeneralException{
        if(null== _rows) {
            
    
            /** Need to load the permitted roles from the identity **/
            if(SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE.equals(searchType)) {
                Map request = super.getRequestParam();
                String allowPermitted = (String)request.get(PERMITTED);
                String allowAssignable = (String)request.get(ASSIGNABLE);
                
                if(allowAssignable==null || Boolean.parseBoolean(allowAssignable)) {
                    _rows = super.getRows();
                } else {
                    _rows = new ArrayList<Map<String,Object>>();
                }
                
                if(allowPermitted==null || Boolean.parseBoolean(allowPermitted)) {
                    getPermittedRoles(_rows);
                }
                _count = _rows.size();
                _rows = trimAndSortResults(_rows);
            }  else {
                if(_isReturnNothing) {
                    _rows = new ArrayList<Map<String,Object>>();
                } else {
                    _rows = super.getRows();
                }
            }
        }
        return _rows;
    }

    public String getRowsJson(){

        Map response = new HashMap();

        try {
            response.put("totalCount", getCount());
            List<Map<String,Object>> rows = getRows();
            if (rows != null){
                // jfb: I removed the old template based json datasource which used
                // 'displayname' instead of 'name'. for compatibility
                // with all thesuggests out there, copy name over to displayName
                for(Map<String, Object> map : rows){
                    if (searchType.equals(SUGGEST_TYPE_ROLE)) {
                        map.put("displayName", map.get("displayableName"));
                    } else if (searchType.equals(SUGGEST_TYPE_SCHEMA_OBJECT_TYPE)) {
                        handleRowForSchemaObjectType(map);
                    } else if (searchType.equals(SUGGEST_TYPE_EXCLUDED_ENTITLEMENT)) {
                        map.put("displayName", map.get("displayableName"));
                        String appName = (String)map.get("application.name");
                        map.put("applicationName", appName);
                    } else if (searchType.equals(SUGGEST_TYPE_ACCOUNTGROUP) || searchType.equals(SUGGEST_TYPE_INHERITED_ACCOUNT_GROUP)) {
                        map.put("displayName", map.get("displayableName"));
                        map.put("applicationName", map.get("application.name"));
                        map.put("owner", map.get("owner.displayName"));
                    } else if (searchType.equals(SUGGEST_TYPE_CLASSIFICATION)) {
                        map.put("displayName", map.get("displayableName"));
                    }
                    
                    //If still no displayName, set to name
                    if (!map.containsKey("displayName") && map.containsKey("name")) {
                        map.put("displayName", map.get("name"));
                    }
                }            
            }
            response.put("objects", rows != null ? rows : Collections.EMPTY_LIST);
        } catch (GeneralException e) {
            log.error("The BaseObjectSuggestBean was unable to fetch all its objects.", e);
            response.put("success", false);
        }

        return JsonHelper.toJson(response);
    }

    /**
     * The incoming map will be output as the following.
     * <pre>
     * [{
     *   id: "ajdljsdfjfjwewf234234",
     *   name: "Application Name",
     *   schemaId: "kajsdf9023-2384-23",
     *   schemaName: "group"
     *  }, ....{}]
     * </pre>
     *
     * @param row map returned from {@link #getRows()} method.
     *
     * @throws GeneralException
     */
    private void handleRowForSchemaObjectType(Map<String, Object> row) throws GeneralException {
        String id = (String) row.get("id");
        String name = (String) row.get("name");
        String schemaId = (String) row.get("schemas.id");
        String schemaName = (String) row.get("schemas.objectType");
        row.clear();

        row.put("appId", id);
        row.put("appName", name);
        row.put("schemaId", schemaId);
        row.put("schemaName", schemaName);
    }

    @Override
    public Object convertColumn(String name, Object value) {
        Object converted = super.convertColumn(name, value);
        
        if ("supportsAdditionalAccounts".equals(name)) {
            if (null == this.userAllowedAddtAcctRequests) {
                QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(getContext());
                try {
                    boolean selfService = isSelfService(getRequestParam());
                    //TODO: Is this still used?
                    // Note that currently this is only being pulled back for the
                    // request entitlements pages, we'll check the config specific
                    // to that page.
                    this.userAllowedAddtAcctRequests =
                        svc.isRequestControlOptionEnabled(getLoggedInUser(),
                                                    getLoggedInUserDynamicScopeNames(),
                                                    null,
                                                    QuickLink.LCM_ACTION_REQUEST_ACCESS,
                                                    Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS_ADDITIONAL_ACCOUNT_REQUESTS,
                                                    selfService);
                }
                catch (GeneralException e) {
                    // convertColumn() doesn't throw - turn into a runtime.
                    throw new RuntimeException(e);
                }
            }

            boolean allow = (Boolean) value;
            converted = this.userAllowedAddtAcctRequests && allow;
        }
        
        return converted;
    }
    
    public QueryOptions getQueryOptions() throws GeneralException{
        QueryOptions qo;
        RoleConfig rc = new RoleConfig();
        List<String> manAssignableTypes = rc.getManuallyAssignableRoleTypes();

        /** Don't limit and sort results if we are getting assignable/permitted roles **/
        if(SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE.equals(searchType)) {
            qo = new QueryOptions();
        } else {
            qo = super.getQueryOptions();
        }
        
        // Do not show a role if we are trying to assign roles and it is not manually assignable
        if (SUGGEST_TYPE_ASSIGNABLE_PERMITTED_ROLE.equals(searchType) ||
                ASSIGNABLE.equals(searchType)) {
            qo.add(Filter.in("type", manAssignableTypes));
        }

        if (this.query != null && !this.query.equals("") && (SUGGEST_TYPE_EXCLUDED_ENTITLEMENT.equals(searchType) || SUGGEST_TYPE_INHERITED_ACCOUNT_GROUP.equals(searchType))) {
            qo.add(Filter.ignoreCase(Filter.like("displayableName", this.query, Filter.MatchMode.START)));            
        } else if(this.query!=null && !this.query.equals("")) {
            String queryProperty = "name";
            if (SUGGEST_TYPE_ROLE.equals(this.searchType)) {
                queryProperty = "displayableName";
            }
            
            qo.add(Filter.ignoreCase(Filter.like(queryProperty, this.query, Filter.MatchMode.START)));
        }
        
        if (_filter != null)
            qo.add(_filter);

        qo.setDistinct(true);

        Filter exclusionFilter = getExclusionFilter();
        if (exclusionFilter != null) {
            qo.add(exclusionFilter);
        }

        return qo;
    }

    /**
     * Add exclusion filters for queryOptions.
     *
     * @return The exclusion filter to be used, or null if no exclusion ids
     */
    protected Filter getExclusionFilter() {
        if (Util.isEmpty(exclusionIds)) {
            return null;
        }

        if (SUGGEST_TYPE_SCHEMA_OBJECT_TYPE.equals(searchType)) {
            return Filter.not(Filter.in("schemas.id", exclusionIds));
        } else {
            return Filter.not(Filter.in("id", exclusionIds));
        }
    }

    /** Gets the list of permitted roles based on the identity's list of current roles.  */
    private void getPermittedRoles(List<Map<String,Object>> rows) throws GeneralException{
        if(identityId!=null) {
            Identity identity = getContext().getObjectById(Identity.class, identityId);
            if(identity!=null && identity.getAssignedRoles()!=null) {
                for (Bundle bundle : identity.getAssignedRoles()) {

                    List<Bundle> permits = bundle.getPermits();
                    if(permits!=null) {

                        for(Bundle permit : permits) {

                            /** Only add the role if not already in the assigned/detected list **/
                            if(identity.getDetectedRole(permit.getId())==null && identity.getAssignedRole(permit.getId())==null) {
                                // Apply query
                                if (rows.size() != 0 && permit.getName().toLowerCase().startsWith(this.query.toLowerCase())) {
                                    Map<String,Object> map = convertObject(permit, RoleListResource.COLUMNS_KEY);
                                    rows.add(map);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
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
            appFilters.add(Filter.eq("type", SuggestService.APPLICATION_TYPE_PAM));

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

                // If the requester can request additional accounts, also return
                // apps that supports this.
                QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(getContext());
                boolean selfService = isSelfService(request);
                boolean addtSupported = 
                    svc.isRequestControlOptionEnabled(getLoggedInUser(),
                                                getLoggedInUserDynamicScopeNames(),
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
        
        // Check the logged in user's LCM user types and whether they can request
        // additional accounts.  If so, we need to pull back info about whether
        // the app supports this.
        boolean calculateAllowAddtAcctRequests =
            Util.otob(request.get("calculateAllowAdditionalAccountRequests"));
        if (calculateAllowAddtAcctRequests) {
            _projectionColumns.add("supportsAdditionalAccounts");
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
    
    private boolean isSelfService(Map request) throws GeneralException {
        String identityId = (String) request.get(IDENTITY_ID);
        return (null != identityId) && identityId.equals(getLoggedInUser().getId());
    }
    
    private Filter getAssignableRoleFilter() {
        RoleConfig rc = new RoleConfig();        
        return Filter.and(Filter.in("type", rc.getAssignableRoleTypes()),
                Filter.eq("disabled", false));
    }
    
    /*
     * @return Filter in the context of a LCM request.  This method also sets _returnNothing to true
     * when it's appropriate.  In that case the returned Filter will be null.  However, if _returnNothing
     * remains false and a null Filter is returned, that means there are no restrictions on the query and
     * everything should be returned.
     */
    private Filter getLCMFilter(Map request, LCMConfigService.SelectorObject type) throws GeneralException {
        // Scoping will be applied in the rules.  If no rule is configured no scoping is applied.
        _disableOwnerScoping = true;
        Identity requestor = getLoggedInUser();
        LCMConfigService lcmConfig = new LCMConfigService(getContext());
        String requesteeId = (String) request.get(IDENTITY_ID);
        boolean isSelfService = (requesteeId != null && requesteeId.equals(requestor.getId()));
        
        Identity requestee = null;
        if (requesteeId != null) {
            if (isSelfService) {
                requestee = requestor;
            } else {
                requestee = getContext().getObjectById(Identity.class, requesteeId);
            }
        }

        QueryInfo selectorQueryInfo;
        if (type == SelectorObject.Role) {
            // Note that we're always requesting both permitted and manually assignable roles here, but the 
            // API determines whether or not the current user is actually authorized to get them.
            // This also applies the standard role exclusions (disabled, previously assigned, and 
            // pending roles) when exclude is true
            selectorQueryInfo = lcmConfig.getRoleSelectorQueryInfo(requestor, requestee, true, true, true);
        } else {
            selectorQueryInfo = lcmConfig.getSelectorQueryInfo(requestor, requestee, type, isSelfService);
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
            _isReturnNothing = true;
            returnFilter = null;
        } else {
            returnFilter = selectorQueryInfo.getFilter();
        }

        return returnFilter;
    }
    
    /**
     * Return the Filter used for the LCM account-only application suggest.
     */
    @SuppressWarnings("unchecked")
    public Filter getLCMAccountOnlyAppFilter(String requestee) throws GeneralException {
        // Fake up a request like the UI would generate.
        Map request = new HashMap();
        request.put(IDENTITY_ID, requestee);
        request.put("accountOnly", "true");
        return getLCMFilter(request, LCMConfigService.SelectorObject.Application);
    }
}
