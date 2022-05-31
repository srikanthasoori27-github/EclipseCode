package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.TaskManager;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleScorecard;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.UIConfig;
import sailpoint.object.UIPreferences;
import sailpoint.task.RoleScorer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.extjs.GridResponseSortInfo;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.roles.RoleMetricsQueryRegistry;
import sailpoint.web.util.WebUtil;

@Path("roleViewer")
public class RoleViewerResource extends BaseListResource {
    private static final Log log = LogFactory.getLog(RoleViewerResource.class);
    
    /* Used by the getIdentities() method to aid in getting the proper queries */
    private String metric;
    /* Used by the getIdentities() method to aid in getting the proper queries */
    private String roleId;

    /**
     * Persists the grid state for the specified grid
     * @param roleId ID of the role being refreshed
     * @return state ext grid state to persist
     * @throws GeneralException
     */
    @POST
    @Path("refreshMetrics")
    public Map<String, Object> refreshMetrics(@FormParam("roleId") String roleId) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        Map<String, Object> result = new HashMap<String, Object>();
        
        Bundle roleToRefresh = getContext().getObjectById(Bundle.class, roleId);
        RoleScorecard roleScorecard = roleToRefresh.getScorecard();
        long millisSinceUpdate;
        
        if (roleScorecard == null || roleScorecard.getModified() == null) {
            millisSinceUpdate = Long.MAX_VALUE;
        } else {
            Date lastModified = roleScorecard.getModified();
            Date now = new Date();
            millisSinceUpdate = Util.computeDifferenceMilli(lastModified, now);
        }
        
        if (log.isDebugEnabled()) log.debug("millis since update: " + millisSinceUpdate);
        
        if (millisSinceUpdate > 60000) {
            String taskName = "Refresh Role Scorecard";
            // Check if a refresh task is running before scheduling another
            TaskResult pendingTask = getContext().getObjectByName(TaskResult.class, taskName);
            
            if (pendingTask == null || pendingTask.isComplete()) {
            
                if (log.isDebugEnabled()) log.debug((pendingTask == null) ? ("pending task is null") : ("pending task is complete: " + pendingTask.isComplete()));
                
                TaskManager tm = new TaskManager( getContext() );
                TaskDefinition metricsRefreshTask;
                try {
                    
                    metricsRefreshTask = getContext().getObjectByName(TaskDefinition.class, taskName);

                    if (null == metricsRefreshTask) {
                    	//IIQETN-4890 if we do not have a Task Definition no reason to move forward.
                    	//TODO in the future we might want to bubble this up to the UI
                    	log.error("Failed to find a Task Definition: " + taskName);
                    	return result;
                    }

                    Map<String, Object> arguments = new HashMap<String, Object>();
                    arguments.put(RoleScorer.ARG_ROLE, roleId);
                    arguments.put(TaskSchedule.ARG_RESULT_NAME, taskName);
                    arguments.put(TaskSchedule.ARG_LAUNCHER, getLoggedInUserName());
                    
                    if (log.isDebugEnabled()) log.debug("Running Refresh Role Scorecard");
                    
                    tm.runWithResult(metricsRefreshTask, arguments);
                    result.put("isPending", true);

                } catch( GeneralException e ) {
                    throw new GeneralException( "Failed to refresh metrics for role: " + roleId, e );
                }
            } else {
                result.put("isPending", true);
                if (log.isDebugEnabled()) log.debug("Not running Refresh Role Scorecard because pending task is not complete: " + pendingTask.isComplete() + ", so result isPending is true.");
            }
        } else {
            ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            RoleTypeDefinition roleTypeDefinition = null;
            if (roleConfig != null) {
                roleTypeDefinition = roleConfig.getRoleType(roleToRefresh);
            } else {
                log.error("The role viewer could not show metrics for the " + roleToRefresh.getName() + " role because the Bundle ObjectConfig is missing.");
            }

            if (log.isDebugEnabled()) log.debug("Not running Refresh Role Scorecard again because it has been run too recently or the previous run has completed.  Sending scorecard.");
            Map<String, Object> metricsJson = RoleUtil.getMetricsAsMap(roleTypeDefinition, roleScorecard, getLocale(), getUserTimeZone());
            result.put("roleMetrics", metricsJson);
        }
        
        return result;
    }
    
    /**
     * Persists the grid state for the specified grid
     * @param form form paremeters
     * @return state ext grid state to persist
     * @throws GeneralException
     */
    @POST
    @Path("identitiesGrid")
    public ListResult getIdentities(MultivaluedMap<String, String> form) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
    //@FormParam("roleId") String roleId, @FormParam("metric") String metric, @FormParam("limit") int limit, @FormParam("sort") String sort) throws GeneralException {
        this.roleId = getSingleFormValue(form, "roleId");
        this.metric = getSingleFormValue(form, "metric");
        processForm(form);
        List<Map<String, Object>> identities = getResults(UIConfig.ROLE_METRICS_IDENTITIES_COLUMNS, roleId, metric);
        QueryOptions qo = getQueryOptions(UIConfig.ROLE_METRICS_IDENTITIES_COLUMNS);
        int count;
        if (qo == null) {
            count = 0;
        } else {
            count = getContext().countObjects(Identity.class, qo);
        }
        ListResult result = new ListResult(identities, count);
        GridResponseSortInfo sortInfo = new GridResponseSortInfo(sortDirection, sortBy) ;
        GridResponseMetaData metaData = new GridResponseMetaData(UIConfig.getUIConfig().getRoleMetricsIdentitiesColumns(), sortInfo);
        // Fix the root and count because the default is obsolete
        metaData.setRoot("objects");
        metaData.setTotalProperty("count");
        result.setMetaData(metaData.asMap());
        return result;
    }
    
    @POST
    @Path("entitlements")
    public Map<String, Object> getEntitlements(MultivaluedMap<String, String> form) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        this.roleId = getSingleFormValue(form, "roleId");
        this.metric = getSingleFormValue(form, "metric");
        RoleMetricsQueryRegistry queryRegistry = new RoleMetricsQueryRegistry(getContext());
        Set<SimplifiedEntitlement> entitlements = (Set<SimplifiedEntitlement>) queryRegistry.get(metric, roleId);
        Map<String, Attributes<String, Object>> attributesByApp = new HashMap<String, Attributes<String, Object>>();
        for (SimplifiedEntitlement entitlement : entitlements) {
            String appId = entitlement.getApplicationId();
            Attributes<String, Object> attributes = attributesByApp.get(appId);
            if (attributes == null) {
                attributes = new Attributes<String, Object>();
                attributesByApp.put(appId, attributes);
            }
            
            Object existingValue = attributes.get(entitlement.getNameOrTarget());
            if (existingValue == null) {
                attributes.put(entitlement.getNameOrTarget(), entitlement.getRightOrValue());
            } else {
                if (existingValue instanceof String) {
                    String value1 = (String) existingValue;
                    existingValue = new ArrayList<String>();
                    ((List<String>)existingValue).add(value1);
                    ((List<String>)existingValue).add(entitlement.getRightOrValue());
                    attributes.put(entitlement.getNameOrTarget(), existingValue);
                } else if (existingValue instanceof Collection) {
                    ((Collection) existingValue).add(entitlement.getRightOrValue());
                } else {
                    log.error("There is erroneous logic in the RoleViewerResource's getEntitlements() method.");
                }
            }
        }
        
        Map<String, Object> returnObj = new HashMap<String, Object>();
        List<Map<String, Object>> apps = new ArrayList<Map<String, Object>>();
        if (!attributesByApp.isEmpty()) {
            Set<String> appIds = attributesByApp.keySet();
            for (String appId : appIds) {
                // appMap repesents an application object in the json
                Map<String, Object> appMap = new HashMap<String, Object>();
                Attributes<String, Object> attributes = attributesByApp.get(appId);
                Application app = getContext().getObjectById(Application.class, appId);
                String appName = app.getName();
                appMap.put("application", appName);
                Identity user = getLoggedInUser();
                boolean displayDescription;
                Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
                if (null != pref) {
                    displayDescription = Util.otob(pref);
                } else{
                    displayDescription = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
                }
                
                Collection<Map<String, Object>> attributesCollection = new ArrayList<Map<String, Object>>();
                Map <String, Map<String, String>> descriptionMap = Explanator.getDescriptions(app, attributes, getLocale());
                Set<String> attributeNames = attributes.keySet();
                for (String attributeName : attributeNames) {
                    Object value = attributes.get(attributeName);
                    Collection<String> values;
                    if (value instanceof String) {
                        values = Arrays.asList(new String [] {(String)value});
                    } else {
                        values = (Collection<String>) value;
                    }
                    
                    Collection<Map<String, Object>> valuesCollection = new ArrayList<Map<String, Object>>();
                    for (String attributeValue : values) {
                        String description;
                        boolean showInfoIcon = descriptionMap.get(attributeName).get(attributeValue) != null;
                        if (showInfoIcon) {
                            description = (String) descriptionMap.get(attributeName).get(attributeValue);
                        } else {
                            description = WebUtil.getGroupDisplayableName(appName, attributeName, attributeValue);
                        }
                        boolean isGroup = WebUtil.isGroupAttribute(appName, attributeName);
                        String valueId = WebUtil.buildValidComponentId(attributeValue);
                        String displayName = Explanator.getDisplayValue(appId, attributeName, attributeValue);
                        
                        // attributeMap represents an attribute value object in the json
                        Map<String, Object> valueMap = new HashMap<String, Object>();
                        valueMap.put("valueId", valueId);
                        valueMap.put("isGroup", isGroup);
                        valueMap.put("displayName", displayName);
                        valueMap.put("showInfoIcon", showInfoIcon);
                        valueMap.put("description", description);
                        valueMap.put("value", attributeValue);
                        // Need to repeat application here due to ExtJS XTemplate limitations
                        valueMap.put("application", appName);
                        valuesCollection.add(valueMap);
                    }
                    Map<String, Object> attributeMap = new HashMap<String, Object>();
                    attributeMap.put("name", attributeName);
                    attributeMap.put("showEntitlementDescription", displayDescription);
                    attributeMap.put("values", valuesCollection);
                    attributesCollection.add(attributeMap);
                }
                appMap.put("attributes", attributesCollection);
                apps.add(appMap);
            }
        }
        returnObj.put("applications", apps);
        
        return returnObj;
    }
    
    
    /**
     * Our own special implementation of this
     */
    public List<Map<String,Object>> getResults(String columnsKey, String roleId, String metric) throws GeneralException {

        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        try {
            List<String> cols = this.getProjectionColumns(columnsKey);
            cols.add("id");

            // If name isn't already in the column list, add it so that
            // we can use it later to construct a cert.
            if (!cols.contains("name")) {
                cols.add("name");
            }

            QueryOptions qo = getQueryOptions(columnsKey);
            if (qo != null) {
                Iterator<Object[]> rows = 
                    getContext().search(Identity.class, qo, Util.listToCsv(cols));
                while (rows.hasNext()) {
                    results.add(convertRow(rows.next(), cols, columnsKey));
                }
                makeJsonSafeKeys(results);
            } // else the query would return nothing anyways so we skip it
        } catch (IllegalArgumentException e) {
            log.error("Failed to query identities on metric: " + metric + " for role with id: " + roleId, e);
        }

        return results;
    }
    
    @Override
    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException {
        List<String> cols = this.getProjectionColumns(columnsKey);
        assert (null != cols) : "Projection columns required for using getRows().";
        QueryOptions qo = super.getQueryOptions(columnsKey);
        qo.setDistinct(true);
        RoleMetricsQueryRegistry queryRegistry = new RoleMetricsQueryRegistry(getContext());
        Filter query = (Filter) queryRegistry.get(metric, roleId);
        if (query == null) {
            qo = null;
        } else {
            qo.add(query);
        }
        return qo;
    }
    
    @Override
    public Map<String,Object> convertRow(Object [] row, List<String> projectionCols, String columnsKey) 
        throws GeneralException {
        Map<String,Object> map = super.convertRow(row, projectionCols, columnsKey);
        String displayName = (String) map.get("displayName");
        if (Util.isNullOrEmpty(displayName)) {
            map.put("displayName", map.get("name"));
        }
        Map<String, Object> rawQueryResults = getRawResults(row, projectionCols);
        Object id = rawQueryResults.get("id");
        if (id != null) {
            map.put("id", id);
        }
        
        return map;
    }
}
