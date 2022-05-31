package sailpoint.rest;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.TaskManager;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.role.MiningService;
import sailpoint.task.ITRoleMiningTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.mining.EntitlementsJsonObj;

@Path("roleMining")
public class RoleMiningResource extends BaseResource {
    private static final Log log = LogFactory.getLog(RoleMiningResource.class);

    @POST
    @Path("getViewableTask")
    public Map<String, Object> getViewableTask(@FormParam("templateName") String templateName, @FormParam("templateId") String templateId, @FormParam("type") String type) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        Map<String, Object> result = new HashMap<String, Object>();
        QueryOptions qo = new QueryOptions(Filter.and(Filter.eq("type", type), Filter.eq("definition.id", templateId), Filter.notnull("completed")));
        int numViewableTasks = getContext().countObjects(TaskResult.class, qo);
        if (numViewableTasks > 0) {
            QueryOptions qo2 = new QueryOptions(Filter.and(Filter.eq("type", type), Filter.eq("definition.id", templateId)));
            qo2.addOrdering("completed", true);
            qo2.setFirstRow(0);
            qo2.setResultLimit(1);
            Iterator<Object[]> lastTaskResult = getContext().search(TaskResult.class, qo2, "id");
            String lastTaskResultId = (String) lastTaskResult.next()[0];
            result.put("lastTaskResult", lastTaskResultId);
            result.put("isViewable", true);            
        } else {
            qo = new QueryOptions(Filter.and(Filter.eq("type", type), Filter.eq("definition.id", templateId), Filter.isnull("completed")));
            int numPendingTasks = getContext().countObjects(TaskResult.class, qo);
            if (numPendingTasks == 0) {
                result.put("isViewable", false);
                result.put("errorMsg", localize(MessageKeys.IT_ROLE_MINING_RESULTS_NOT_AVAILABLE , templateName));
            } else {
                result.put("isViewable", false);
                result.put("errorMsg", localize(MessageKeys.IT_ROLE_MINING_RESULTS_PENDING, templateName));
            }
        }
        
        return result;
    }

    /**
     * Return the specified result's filter information in this form:
     * <pre>
     * {
     *     isAvailable: <true or false depending on whether the information was available>,
     *     identityFilterInfo: {
     *         identityFilterType: <attribute or ipop> depending on whether the identity filter was attribute or ipop based>
     *         <if this has an attribute-based identity filter>
     *         identityAttributes: [ {name: <name>, value: <value>}, {name: <name2>, value: <value2>, etc.]
     *         <if this has an ipop-based identity filter>
     *         ipop: <ipop name>
     *     },
     *     entitlementsInfo: <see sailpoint.web.mining.EntitlementsJsonObj for the JSON that is generated here>
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    @POST
    @Path("it/filterInfo")
    public Map<String, String> getItRoleMiningFilterInfo(@FormParam("resultName") String resultName) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String results;
        
        try {
            jsonWriter.object();
            jsonWriter.key("isAvailable");
            
            TaskResult taskResult = getContext().getObjectByName(TaskResult.class, resultName);
            TaskDefinition taskDef = null;
            
            if (taskResult != null) {
                taskDef = taskResult.getDefinition();
            }
            
            if (taskDef != null) {
                String serializedFilters = (String) taskDef.getArgument(ITRoleMiningTask.IDENTITY_FILTER);
                String identityFilterType = (String) taskDef.getArgument(ITRoleMiningTask.IDENTITY_FILTER_TYPE);
                String ipopName = (String) taskDef.getArgument(ITRoleMiningTask.POPULATION_NAME);
                String serializedEntitlements = (String) taskDef.getArgument(ITRoleMiningTask.INCLUDED_ENTITLEMENTS);
                
                jsonWriter.value(true);
                jsonWriter.key("identityFilterInfo");
                jsonWriter.object();
                jsonWriter.key("identityFilterType");
                if (identityFilterType.equals(ITRoleMiningTask.IDENTITY_FILTER_TYPE_BY_ATTRIBUTES)) {
                    jsonWriter.value("attribute");
                    jsonWriter.key("identityAttributes");
                    jsonWriter.array();
                    // Convert the identity filters saved in the task to attributes
                    if( !Util.isNullOrEmpty( serializedFilters ) ) { 
                        XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
                        
                        List<Filter> filters = (List<Filter>)taskResult.getAttribute("identityFilter");
                        
                        if (filters == null) {
                            filters = (List<Filter>)xmlDeserializer.parseXml( getContext(), serializedFilters, false );
                        }
                        
                        if( filters != null ) {
                            Map <String, String> attributes = MiningService.convertIdentityFiltersToAttributes(filters);
                            ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
                            Map<String, String> systemAttributes = new HashMap<String, String>();
                            systemAttributes.put("inactive", localize(MessageKeys.INACTIVE));
                            systemAttributes.put("managerStatus", localize(MessageKeys.SRCH_INPUT_DEF_ISMANAGER));
                            systemAttributes.put("manager", localize(MessageKeys.MANAGER));
                            if (attributes != null && !attributes.isEmpty()) {
                                Set<String> names = attributes.keySet();
                                for (String name : names) {
                                    jsonWriter.object();
                                    jsonWriter.key("name");
                                    if (systemAttributes.containsKey(name) ) {
                                        jsonWriter.value(systemAttributes.get(name));
                                    } else {
                                        jsonWriter.value(identityConfig.getDisplayName(name, getLocale()));
                                    }
                                    jsonWriter.key("value");
                                    jsonWriter.value(attributes.get(name));
                                    jsonWriter.endObject();
                                }
                            }
                        }
                    }
                    jsonWriter.endArray();
                } else {
                    jsonWriter.value("ipop");
                    jsonWriter.key("ipop");
                    jsonWriter.value(ipopName);
                }
                jsonWriter.endObject();
                
                jsonWriter.key("entitlementsInfo");
                if( Util.isNullOrEmpty( serializedEntitlements ) ) { 
                    jsonWriter.object();
                    jsonWriter.key("entitlements");
                    jsonWriter.array();
                    jsonWriter.endArray();
                    jsonWriter.endObject();
                } else {
                    XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
                    List<IdentityItem> identityItems = (List<IdentityItem>)xmlDeserializer.parseXml( getContext(), serializedEntitlements, false );
                    Set<SimplifiedEntitlement> entitlements = MiningService.convertIdentityItemsToSimplifiedEntitlements(identityItems);
                    EntitlementsJsonObj entitlementsJsonObj = MiningService.getEntitlementsJsonObj(entitlements, getContext(), getLocale(), true);
                    entitlementsJsonObj.generateJSON(jsonWriter);
                }
            } else {
                jsonWriter.value(false);
            }
            
            jsonWriter.endObject();
            
            results = jsonString.toString();
        } catch (GeneralException e) {
            log.error("Failed to get filter information for the the role mining task named " + resultName, e);
            results = "{isAvailable: false}";
        } catch (JSONException e) {
            log.error("Failed to get filter information for the the role mining task named " + resultName, e);
            results = "{isAvailable: false}";
        }
        
        // This is a hack to wrap the results in an actual JSON object because right now returning the result
        // would encode it as a String within a String.  The better solution is to fix that situation, but given
        // that this is the end of the release I don't want to make changes to a part of the product on which 
        // so much existing code depends.  Instead we'll go with this hack -- Bernie
        Map<String, String> resultWrapper = new HashMap<String, String>();
        resultWrapper.put("stringifiedJSON", results);
        
        return resultWrapper;
    }

    @POST
    @Path("terminate")
    public Map<String, Object> terminateMining(@FormParam("resultName") String resultName) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        Map<String, Object> results = new HashMap<String, Object>();
        
        try {
            TaskManager taskManager = new TaskManager(getContext());
            TaskResult jobToInterrupt = getContext().getObjectByName(TaskResult.class, resultName);
            if (jobToInterrupt != null) {
                if (taskManager.terminate(jobToInterrupt)) {
                    results.put("isTerminated", true);
                    results.put("msg", localize(MessageKeys.ROLE_MINING_TERMINATED_SUCCESSFULLY, resultName));
                } else {
                    results.put("isTerminated", true);
                    results.put("msg", localize(MessageKeys.ROLE_MINING_NOT_TERMINATED_NOT_RUNNING, resultName));
                }
            } else {
                results.put("isTerminated", false);
                results.put("msg", localize(MessageKeys.ROLE_MINING_NOT_TERMINATED_NOT_FOUND, resultName));
            }
        } catch (GeneralException e) {
            log.error("Could not terminate a mining task", e);
            results.put("isTerminated", false);
            results.put("msg", localize(MessageKeys.ROLE_MINING_NOT_TERMINATED_ERROR, resultName));
        }
        
        return results;
    }
    
    @POST
    @Path("failureInfo")
    public Map<String, Object> getFailureInfo(@FormParam("resultName") String resultName) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        Map<String, Object> results = new HashMap<String, Object>();
        
        try {
            TaskManager taskManager = new TaskManager(getContext());
            TaskResult failure = getContext().getObjectByName(TaskResult.class, resultName);
            if (failure != null) {
                results.put("type", failure.getType().toString());
                if (failure.getType() == TaskItemDefinition.Type.ITRoleMining) {
                    // IT Role Mining
                    List<Message> errorMessages = failure.getErrors();
                    if (errorMessages != null && !errorMessages.isEmpty()) {
                        StringBuilder errorHTML = new StringBuilder(); 
                        for (Message errorMessage : errorMessages) {
                            errorHTML.append("<div>")
                                .append(errorMessage.getLocalizedMessage(getLocale(), getUserTimeZone()))
                                .append("</div>");
                        }
                        results.put("errorMsg", errorHTML.toString());
                    } else {
                        results.put("errorMsg", localize(MessageKeys.NO_TASK_RESULT_FOUND));                        
                    }
                } else {
                    // Business Role Mining
                    results.put("id", failure.getId());
                }
                
            } else {
                results.put("errorMsg", localize(MessageKeys.NO_TASK_RESULT_FOUND));
            }
        } catch (GeneralException e) {
            log.error("Could not view a failed role mining task", e);
            results.put("errorMsg", localize(MessageKeys.NO_TASK_RESULT_FOUND));
        }
        
        return results;
    }
}
