/**
 * 
 */
package sailpoint.web.workflow;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor;
import sailpoint.monitoring.IWorkflowMonitor.ProcessMetrics;
import sailpoint.monitoring.IWorkflowMonitor.ProcessMetricsParams;
import sailpoint.monitoring.WorkflowMonitorHelper;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.Scriptlet;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemConfig;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Step.Icon;
import sailpoint.object.WorkflowRegistry;
import sailpoint.object.WorkflowRegistry.WorkflowType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.WorkItemConfigBean;
import sailpoint.web.form.editor.FormDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FormJsonUtil;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class WorkflowUtil {
    private static final Log log = LogFactory.getLog(WorkflowUtil.class);
    
    /**
     * Our built in Generic Step that we'll maintain and change
     * from release to relase.
     */
    private static final String GENERIC_STEP_LIBRARY = "Generic Step Library";

    /**
     * Something customer's can define that will show up on every 
     * Step.  We won't ship or maintain this step libray.
     */
    private static final String CUSTOM_STEP_LIBRARY = "Custom Step Library";
    
    /**
     * 
     */
    public WorkflowUtil() {}
    
    public enum TimeUnits {
        minutes,
        hours,
        days
    }
    
    private static List<Icon> getIcons(Resolver ctx) throws GeneralException {
        WorkflowRegistry registry = WorkflowRegistry.getInstance(ctx);
        List<Icon> icons = (List<Icon>)registry.getAttribute(WorkflowRegistry.ATTR_ICONS);
        return icons;
    }
    
    private static Icon getIcon(String name, Resolver ctx) throws GeneralException {
        List<Icon> icons = getIcons(ctx);
        if(icons!=null) {
            for(Icon icon : icons) {
                if(icon.getName().equals(name)) {
                    return icon;
                }
            }
        }
        return null;
    }
    
    /** Workflows are defined as certain types, types contain step libraries which can be configured using the 
     * workflowregistry.xml file. This method returns the available step libraries for a given workflow.
     * Workflows which are either null or contain an unknown type should return all 
     * available template Workflows.
     * @param workflowType can be null
     * @param ctx
     * @return workflow dto's that are templates
     * @throws GeneralException
     */
    static List<WorkflowDTO> resolveStepLibrary(String workflowType, Resolver ctx) throws GeneralException {

        List<WorkflowDTO> workflowDTOs = new ArrayList<WorkflowDTO>();
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("template", true));
        ops.setDistinct(true);
        
        WorkflowType wfType = resolveType(workflowType, ctx);
        
        Set<String> stepLibNames = new HashSet<String>();
        stepLibNames.add(GENERIC_STEP_LIBRARY);

        // inform the user of what names defined in the registry are not found
        Set<String> namesNotFound = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (wfType != null) {
            // query the workflow registry and get the workflows by type
            Set<String> stepLibs  = Util.csvToSet(wfType.getStepLibraries(), false);
            if ( Util.size(stepLibs) > 0 )  
                stepLibNames.addAll(stepLibs);
        }        

        namesNotFound.addAll(stepLibNames);
        // Add this after we update namesNotFound so we don't get 
        // the "missing custom" error message since it won't 
        // exist by default
        stepLibNames.add(CUSTOM_STEP_LIBRARY);
        ops.addFilter(Filter.in("name", stepLibNames));
        
        List<Workflow> results = ctx.getObjects(Workflow.class, ops);
        for (Workflow result : Util.safeIterable(results)) {
            namesNotFound.remove(result.getName());
            WorkflowDTO newDTO = new WorkflowDTO(result);
            workflowDTOs.add(newDTO);
        }
        
        if (log.isWarnEnabled() && !Util.isEmpty(namesNotFound)) {
            Message mess = new Message(MessageKeys.WORKFLOW_WARNING_LOG_STEP_LIBRARY_NOT_FOUND, namesNotFound, workflowType);
            FacesContext context = FacesContext.getCurrentInstance();
            Locale locale = context.getViewRoot().getLocale();
            log.warn(mess.getLocalizedMessage(locale, null));
        }
        
        return workflowDTOs;
    }
    
    /** resolves a workflow.getType string value to a workflow Type object.
     * @param workflowType
     * @param ctx
     * @return workflowType object OR null if no type was found
     * @throws GeneralException
     */
    private static WorkflowType resolveType(String workflowType, Resolver ctx) throws GeneralException {
        if (workflowType != null) {
            List<WorkflowType> types = WorkflowRegistry.getInstance(ctx).getTypes();
            for (WorkflowType wft : Util.safeIterable(types)) {
                if (workflowType.equalsIgnoreCase(wft.getName())) {
                    return wft;
                }
            }
        }
        
        return null;
    }
    
    /**
     * @param workflows list of workflowDTO's to resolve the steps from
     * @param ctx context to use 
     * @return JSON object representing the available steps for the list of workflows
     * @throws GeneralException
     * @throws JSONException
     */
    public static String getStepOptionsJson(List<WorkflowDTO> workflows, Resolver ctx) throws GeneralException, JSONException {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        List<JSONObject> steps = new ArrayList<JSONObject>();
        int i=0;
        
        for (WorkflowDTO workflow : Util.safeIterable(workflows)) {
            /** Now try to load the steps from the template **/
            if (workflow != null && workflow.isTemplate()) {
                for (StepDTO step : workflow.getSteps()) {
                    String iconName = step.getIcon();
                    Icon icon = getIcon(iconName, ctx);
                    
                    if (icon != null) {
                        i++;
                        Map<String,Object> stepMap = new HashMap<String,Object>();
                        stepMap.put("id", i);
                        stepMap.put("name", icon.getName());
                        stepMap.put("iconCls", icon.getStyleClass() + "_sm");
                        stepMap.put("text", step.getName());
                        stepMap.put("leaf", true);
                        stepMap.put("stepClass", icon.getStyleClass());
                        stepMap.put("stepJson", getStepJson(step, ctx));
                        steps.add(new JSONObject(stepMap));
                    }
                }
            }
        }
        
        jsonWriter.object();
        jsonWriter.key("children");
        jsonWriter.value(steps);
        jsonWriter.key("totalCount");
        jsonWriter.value(steps.size());
        jsonWriter.endObject();
        
        return jsonString.toString();
    }

    public static String getWorkflowJson(WorkflowDTO workflow, Resolver ctx) throws GeneralException, JSONException {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);

        FacesContext context = FacesContext.getCurrentInstance();
        Locale locale = context.getViewRoot().getLocale();

        jsonWriter.object();

        jsonWriter.key("id");
        jsonWriter.value(workflow.getUid());
        
        jsonWriter.key("processId");
        jsonWriter.value(workflow.getPersistentId());

        jsonWriter.key("name");
        jsonWriter.value(workflow.getName());
        
        jsonWriter.key("type");
        jsonWriter.value(workflow.getType());
        
        jsonWriter.key("isMonitored");
        jsonWriter.value(workflow.isMonitored());

        jsonWriter.key("configForm");
        jsonWriter.value(workflow.getConfigForm());
        
        jsonWriter.key("taskType");
        jsonWriter.value(workflow.getTaskType());
        
        jsonWriter.key("handler");
        jsonWriter.value(workflow.getHandler());
        
        jsonWriter.key("libraries");
        jsonWriter.value(workflow.getLibraries());
        
        jsonWriter.key("description");
        if (workflow.getDescription() != null) {
            jsonWriter.value(workflow.getDescription());
        } else {
            jsonWriter.value("");
        }        
        jsonWriter.key("descriptionI18n");
        jsonWriter.value(new Message(MessageKeys.DESCRIPTION).getLocalizedMessage(locale, null));

        /** Rule Libraries */
        jsonWriter.key("ruleLibraries");
        jsonWriter.value(new JSONArray(getRuleLibrariesJson(workflow)));
        
        /** Add steps **/
        jsonWriter.key("steps");
        jsonWriter.value(new JSONArray(getStepsJson(workflow, ctx)));
        
        /** Add variables **/
        jsonWriter.key("variables");
        jsonWriter.value(new JSONArray(getVariablesJson(workflow)));

        /* Add metrics */
        jsonWriter.key("metrics");
        jsonWriter.value(getMetricsJson(workflow, (SailPointContext)ctx));

        jsonWriter.endObject();
        if(log.isDebugEnabled())
            log.debug("Workflow JSON: " + jsonString.toString());

        return jsonString.toString();
    }

    /**
     * Convert a Form object to a map of values
     * to display the Form's data on a Form Editor Window.
     * @param formDTO The Form data transmission object
     * @param resolver The SailPoint Object resolver
     * @return Map
     * @throws GeneralException, JSONException
     */
    private static Map<String, Object> getFormJSON(FormDTO formDTO,
                                                   Resolver resolver)
        throws GeneralException, JSONException {

        return FormJsonUtil.convertFormToJSON(formDTO, resolver);
    }

    private static Map<String, Object> getApprovalJSON(ApprovalDTO approval) throws GeneralException{
        
        if(approval==null)
            return null;
        
        Map<String, Object> approvalMap = new HashMap<String, Object>();
        approvalMap.put("id", approval.getUid());
        approvalMap.put("name", approval.getName());
        
        FormJsonUtil.addScript(approvalMap, approval.getOwner(), "owner", null);
        FormJsonUtil.addScript(approvalMap, approval.getDescription(), "description", null);
        FormJsonUtil.addScript(approvalMap, approval.getMode(), "mode", null);
        
        approvalMap.put("sendVal", approval.getSend());
        approvalMap.put("returnVal",approval.getReturn());
        approvalMap.put("renderer", approval.getRenderer());
        approvalMap.put("args", getArgumentsJson(approval.getArgs()));
        
        if(approval.getChildren()!=null) {
            List<JSONObject> children = new ArrayList<JSONObject>();
            for(ApprovalDTO child : approval.getChildren()) {
                children.add(new JSONObject(getApprovalJSON(child)));
            }
            approvalMap.put("children", new JSONArray(children));
        } else {
            approvalMap.put("children", new JSONArray());
        }
        
        FormJsonUtil.addScript(approvalMap, approval.getAfterScript(), "after", null);
       
        if(approval.getWorkItemConfig()!=null) {
            approvalMap.put("workItemConfig", getWorkItemConfigJSON(approval));
        }

        return approvalMap;
    }

    private static Map<String, Object> getReplicatorJSON(ReplicatorDTO replicator) throws GeneralException {
        if (replicator == null) {
            return null;
        }

        Map<String, Object> replicatorMap = new HashMap<String, Object>();
        replicatorMap.put("itemsVar", replicator.getItems());
        replicatorMap.put("arg", replicator.getArg());

        return replicatorMap;
    }
    
    private static Map<String, Object> getWorkItemConfigJSON(ApprovalDTO approval) throws GeneralException {
        Map<String, Object> workItemConfigMap = new HashMap<String, Object>();
        
        WorkItemConfigBean wiBean = approval.getWorkItemConfig();
        if(wiBean!=null) {
            workItemConfigMap.put("workItemOverride", true);
            workItemConfigMap.put("workItemEnabled", wiBean.isWorkItemEnabled());
            workItemConfigMap.put("escalationStyle", wiBean.getEscalationStyle());
            workItemConfigMap.put("daysTillReminder", wiBean.getDaysTillReminder());
            workItemConfigMap.put("daysBetweenReminders", wiBean.getDaysBetweenReminders());
            workItemConfigMap.put("maxReminders", wiBean.getMaxReminders());
            workItemConfigMap.put("escalationRule", wiBean.getEscalationRule());
            workItemConfigMap.put("daysTillEscalation", wiBean.getDaysTillEscalation());
            workItemConfigMap.put("notificationEmail", wiBean.getNotificationEmail());
            workItemConfigMap.put("reminderEmail", wiBean.getReminderEmail());
            workItemConfigMap.put("escalationEmail", wiBean.getEscalationEmail());
            workItemConfigMap.put("electronicSignature", wiBean.getElectronicSignature());
            
            String owners = WebUtil.basicJSONData(wiBean.getOwnerIds(), "IdentityListConverter");
            workItemConfigMap.put("ownerIds", new JSONArray(wiBean.getOwnerIds()));
            workItemConfigMap.put("ownersData", owners );
        }          
        return workItemConfigMap;
    }

    /** Returns the list of variables for the specified Workflow **/    
    private static List<JSONObject> getVariablesJson(WorkflowDTO workflow) {
        List<JSONObject> variables = new ArrayList<JSONObject>();

        if(workflow!=null) {
            List<VariableDTO> varDefs = workflow.getVariables();
            if(varDefs!=null) {
                for(VariableDTO varDef : varDefs) {
                    Map<String, Object> variable = new HashMap<String, Object>();
                    variable.put("id", varDef.getUid());
                    variable.put("name", varDef.getName());
                    variable.put("input", varDef.isInput());
                    variable.put("output", varDef.isOutput());
                    variable.put("required", varDef.isRequired());
                    variable.put("editable", varDef.isEditable());
                    variable.put("description", varDef.getDescription());
                    variable.put("prompt", varDef.getPrompt());
                    variable.put("type", varDef.getType());
                    variable.put("isTransient", varDef.isTransient());

                    //if(varDef.getInitializer()!=null)
                    //    variable.put("initializer", varDef.getInitializer().getScriptlet());
                    FormJsonUtil.addScript(variable, varDef.getInitializer(), "initializer", "initializerNegate");

                    variables.add(new JSONObject(variable));
                }
            }                
        }        
        return variables;
    }

    /** Returns the list of steps for the specified Workflow **/ 
    private static List<JSONObject> getStepsJson(WorkflowDTO workflow, Resolver ctx)
        throws GeneralException, JSONException {
        List<JSONObject> steps = new ArrayList<JSONObject>();

        if(workflow!=null) {
            List<StepDTO> stepDefs = workflow.getSteps();
            if(stepDefs!=null) {
                int i=0;
                for(StepDTO stepDef : stepDefs) {
                    
                    /** Look at the first step, if it doesn't have an icon, we make it a start step **/
                    if(i==0) {
                        if(stepDef.getIcon()==null) {
                            stepDef.setIcon(WorkflowRegistry.WORKFLOW_ICON_START);
                        }
                    }
                    
                    steps.add(getStepJson(stepDef, ctx));
                    i++;
                }
            }                
        }        
        return steps;
    }
    
    private static JSONObject getStepJson(StepDTO stepDef, Resolver ctx)
        throws GeneralException, JSONException {
        Map<String, Object> step = new HashMap<String, Object>();
        step.put("id", stepDef.getUid());
        
        /** If the step has no icon, check to see if the step has no transitions, if not, make it an end step **/
        if(stepDef.getIcon()==null) {
            List<TransitionDTO> transitions = stepDef.getTransitions();
            if(transitions==null || transitions.isEmpty()) {
                stepDef.setIcon(WorkflowRegistry.WORKFLOW_ICON_STOP);
            }
            
            /** If the step has a non-empty catches block, use the catches icon. **/
            if(stepDef.getCatches()!=null) {                
                stepDef.setIcon(WorkflowRegistry.WORKFLOW_ICON_CATCHES);
            }
            
        }
        
        if(stepDef.getIcon()!=null) {
            Icon icon = getIcon(stepDef.getIcon(), ctx);
            if(icon!=null) {
                step.put("icon", icon.getName()); 
                step.put("iconClass", icon.getStyleClass());
            }
        }
        step.put("name", stepDef.getName());
        if(stepDef.getDescription()!=null)
            step.put("description", stepDef.getDescription());
        
        step.put("configForm", stepDef.getConfigForm());
        step.put("isMonitored", stepDef.isMonitored());
        step.put("catches", stepDef.getCatches());
        
        FormJsonUtil.addScript(step, stepDef.getAction(), "action", null);
        
        step.put("resultVariable", stepDef.getResultVariable());
        step.put("posX", stepDef.getPosX());
        step.put("posY", stepDef.getPosY());
        step.put("hidden", stepDef.isHidden());
        step.put("wait",stepDef.getWait());
        
        FormJsonUtil.addScript(step, stepDef.getCondition(), "condition", "conditionNegated");
        
        step.put("transitions", new JSONArray(getStepTransitionsJson(stepDef)));
        step.put("args", new JSONArray(getArgumentsJson(stepDef.getArgs())));

        /** Add approval **/
        ApprovalDTO approval = stepDef.getApproval();
        step.put("approval", getApprovalJSON(approval));
        
        /** Add form **/
        FormDTO form = stepDef.getForm();
        step.put("form", getFormJSON(form, ctx));
        
        /** Add subprocess **/
        step.put("subprocess", stepDef.getSubprocess());
        // subprocessConfigForm is no longer used.  IIQSR-87.

        ReplicatorDTO replicator = stepDef.getReplicator();
        step.put("replicator", getReplicatorJSON(replicator));
        
        step.put("returns", new JSONArray(getStepReturnsJson(stepDef)));
        return new JSONObject(step);
    }

    /** Returns the list of transitions for the specified Workflow.Step **/ 
    private static List<JSONObject> getStepTransitionsJson(StepDTO step) {
        List<JSONObject> transitions = new ArrayList<JSONObject>();

        if(step!=null) {
            List<TransitionDTO> transDefs = step.getTransitions();
            if(transDefs!=null) {
                for(TransitionDTO transitionDef : transDefs) {
                    Map<String, Object> transition = new HashMap<String, Object>();
                    transition.put("id", transitionDef.getUid());
                    if(transitionDef.getTo()!=null)
                        transition.put("to", transitionDef.getTo().getScriptlet());
                   
                    FormJsonUtil.addScript(transition, transitionDef.getWhen(), "when", "negate");

                    transitions.add(new JSONObject(transition));
                }
            }                
        }        
        return transitions;
    }
    
    private static List<JSONObject> getStepReturnsJson(StepDTO step) {
        List<JSONObject> returns = new ArrayList<JSONObject>();

        if ( step != null ) {
            List<ReturnDTO> returnDTOS = step.getReturns();
            if ( returnDTOS != null ) {
                for ( ReturnDTO dto : returnDTOS ) {
                    Map<String, Object> ret = new HashMap<String, Object>();
                    ret.put("to", dto.getTo());
                    ret.put("name", dto.getName());
                    ret.put("local", dto.isLocal());
                    ret.put("merge",  dto.isMerge());
                    FormJsonUtil.addScript(ret, dto.getValue(), "value", null);
                    returns.add(new JSONObject(ret));
                }
            }
        }
        return returns;
    }

    /** Returns the list of transitions for the specified Workflow.Step **/ 
    private static List<JSONObject> getArgumentsJson(List<ArgDTO> argDefs) {
        List<JSONObject> arguments = new ArrayList<JSONObject>();

        if(argDefs!=null) {
            for(ArgDTO argDef : argDefs) {
                Map<String, Object> arg = new HashMap<String, Object>();
                arg.put("id", argDef.getUid());
                arg.put("name", argDef.getName());
                
                FormJsonUtil.addScript(arg, argDef.getValue(), "value", "negate");

                arguments.add(new JSONObject(arg));
            }
        }      
        return arguments;
    }

    private static Map<String, Object> getMetricsJson(WorkflowDTO workflow, SailPointContext context) throws GeneralException {
        IWorkflowMonitor monitor = WorkflowMonitorHelper.newWorkflowMonitor(context);
        ProcessMetricsParams params = new ProcessMetricsParams();
        params.setProcessName(workflow.getName());
        ProcessMetrics metrics = monitor.getProcessMetrics(params);
        Map<String, Object> metricData = new HashMap<String, Object>();
        metricData.put("numExecutions", metrics.getNumExecutions());
        metricData.put("numSuccessfulExecutions", metrics.getSuccessfulExecutions());
        metricData.put("numFailedExecutions", metrics.getFailedExecutions());
        metricData.put("numPendingExecutions", metrics.getPendingExecutions());
        // By default get the times in terms of minutes
        metricData.put("averageExecutionTime", getTimeFromSeconds(metrics.getAverageExecutionTime(), TimeUnits.minutes));
        metricData.put("maxExecutionTime", getTimeFromSeconds(metrics.getMaxExecutionTime(), TimeUnits.minutes));
        metricData.put("timeUnits", TimeUnits.minutes.name());
        Locale locale = FacesContext.getCurrentInstance().getViewRoot().getLocale();
        if (metrics.getDateOfLastExecution() == null) {
            metricData.put("lastExecutionDate", Internationalizer.getMessage(MessageKeys.PROCESS_INSTRUMENTATION_NO_EXECUTIONS, locale));
        } else {
            // Getting the date is messy because of localization issues
            TimeZone tz;
            Map session = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
            if (session != null && session.get("timeZone") != null)
                tz = (TimeZone)session.get("timeZone");
            else
                tz = TimeZone.getDefault();
            metricData.put("lastExecutionDate", Internationalizer.getLocalizedDate(metrics.getDateOfLastExecution(), locale, tz));
        }
        
        return metricData;
    }

    public static WorkflowDTO updateWorkflowFromJSON(String jsonString, WorkflowDTO workflow) throws GeneralException, JSONException {

        if(workflow==null) {
            workflow = new WorkflowDTO(new Workflow());
        }
        JSONObject workflowJSON = new JSONObject(jsonString);
        workflow.setName(WebUtil.getJSONString(workflowJSON,"name"));
        workflow.setMonitored(WebUtil.getJSONBoolean(workflowJSON, "isMonitored"));
        workflow.setDescription(WebUtil.getJSONString(workflowJSON, "description"));
        workflow.setType(WebUtil.getJSONString(workflowJSON, "type"));
        workflow.setConfigForm(WebUtil.getJSONString(workflowJSON, "configForm"));
        workflow.setLibraries(WebUtil.getJSONString(workflowJSON, "libraries"));
        workflow.setHandler(WebUtil.getJSONString(workflowJSON, "handler"));
        workflow.setTaskType(WebUtil.getJSONString(workflowJSON, "taskType"));
        
        JSONArray ruleLibrariesJSON = workflowJSON.getJSONArray("ruleLibraries");
        updateWorkflowRuleLibrariesFromJSON(workflow, ruleLibrariesJSON);
        
        JSONArray stepsJSON = workflowJSON.getJSONArray("steps");
        JSONArray variablesJSON = workflowJSON.getJSONArray("variables");
        updateWorkflowStepsFromJSON(workflow, stepsJSON);
        updateWorkflowVariablesFromJSON(workflow, variablesJSON);
       
        return workflow;
    }

    private static void updateWorkflowStepsFromJSON(WorkflowDTO workflow, JSONArray stepsJSON) throws GeneralException, JSONException {
        List<StepDTO> newSteps = new ArrayList<StepDTO>();

        for(int i=0; i<stepsJSON.length(); i++) {
            JSONObject stepJSON = stepsJSON.getJSONObject(i);

            /** Try to get the old step off the session and then just overwrite it with what changed on the ui **/

            StepDTO oldStep = null;
            StepDTO newStep = null;
            
            String id = WebUtil.getJSONString(stepJSON, "id");
           
            if(WebUtil.getJSONString(stepJSON, "id")!=null) {
                oldStep = (StepDTO)workflow.find(workflow.getSteps(), id);
            }
            if(oldStep!=null) {
                newStep = new StepDTO(oldStep);
            } else {
                newStep = new StepDTO();
            }

            newStep.setName(WebUtil.getJSONString(stepJSON,"name"));
            if(WebUtil.getJSONString(stepJSON, "icon")!=null) {
                newStep.setIcon(WebUtil.getJSONString(stepJSON, "icon"));
            }
            newStep.setDescription(WebUtil.getJSONString(stepJSON,"description"));
            newStep.setConfigForm(WebUtil.getJSONString(stepJSON, "configForm"));

            newStep.setMonitored(WebUtil.getJSONBoolean(stepJSON, "isMonitored"));
            newStep.setCatches(WebUtil.getJSONString(stepJSON, "catches"));

            String action = FormJsonUtil.parseScriptFromJSON(stepJSON, "action", null);
            if(null != action) {
                newStep.setAction(action);
                FormJsonUtil.updateExplicitMethod(stepJSON, "action", newStep.getAction());
                newStep.setSubprocess(null);
            } else {
                newStep.setAction(null);
                String subprocess = WebUtil.getJSONString(stepJSON, "subprocess");
                if (Util.isNullOrEmpty(subprocess)) {
                    newStep.setSubprocess(null);
                } else {
                    newStep.setSubprocess(subprocess);
                }
            }

            newStep.setPosX(WebUtil.getJSONInt(stepJSON,"posX"));
            newStep.setPosY(WebUtil.getJSONInt(stepJSON,"posY"));
            newStep.setResultVariable(WebUtil.getJSONString(stepJSON,"resultVariable"));
            newStep.setWait(WebUtil.getJSONString(stepJSON,  "wait"));

            String condition = FormJsonUtil.parseScriptFromJSON(stepJSON, "condition", "conditionNegated");
            if (null != condition) {
                newStep.setCondition(condition);
                FormJsonUtil.updateExplicitMethod(stepJSON, "condition", newStep.getCondition());
            }
            else {
                newStep.setCondition(null);
            }
            
            if(stepJSON.has("transitions"))
                newStep.setTransitions(updateStepTransitionsFromJSON(newStep, stepJSON));            

            if(stepJSON.has("args"))
                newStep.setArgs(updateStepArgsFromJSON(newStep, stepJSON));
            
            
            if(stepJSON.has("approval")) 
                updateStepApprovalFromJSON(newStep, stepJSON);
            
            if(stepJSON.has("form")) 
                updateStepFormFromJSON(newStep, stepJSON);
            
            if ( stepJSON.has("returns") ) {
                newStep.setReturns(updateStepReturnsFromJSON(newStep, stepJSON));
            }

            updateStepReplicatorFromJSON(newStep, stepJSON);
            
            newSteps.add(newStep);
        }

        workflow.setSteps(newSteps);
    }
    
    private static void updateStepFormFromJSON(StepDTO newStep, JSONObject stepJSON) throws GeneralException {
        FormDTO form = newStep.getForm();
        if(form==null) {
            form = new FormDTO();
        }
        try {
            // In case Remove Form, form object is empty.
            if(stepJSON.has("form") && stepJSON.get("form") instanceof JSONObject) {
                JSONObject formJSON = stepJSON.getJSONObject("form");

                if(formJSON.has("id")) {
                    FormJsonUtil.updateFormFromJSON(form, formJSON);
                    newStep.setForm(form);
                } else {
                    newStep.setForm(null);
                }
            } else {
                newStep.setForm(null);
            }
        } catch(JSONException jsoe) {
            newStep.setForm(null);
            if(log.isWarnEnabled()) {
            	log.warn("Unable to deserialize form json due to exception: "+jsoe.getMessage(), jsoe);
            }
        }
    }

    private static void updateStepApprovalFromJSON(StepDTO newStep, JSONObject stepJSON) throws GeneralException {
        ApprovalDTO approval = newStep.getApproval();
        if(approval==null) {
            approval = new ApprovalDTO();
        }
        try {
            if(stepJSON.has("approval")) {
                JSONObject approvalJSON = stepJSON.getJSONObject("approval");
                
                if(approvalJSON.has("id")) {                
                    updateApprovalFromJSON(approval, approvalJSON);
                    newStep.setApproval(approval);
                } else {
                    newStep.setApproval(null);
                }
            } else {
                newStep.setApproval(null);
            }
        } catch(JSONException jsoe) {
            newStep.setApproval(null);
        }
    }

    private static void updateStepReplicatorFromJSON(StepDTO newStep, JSONObject stepJSON) throws GeneralException {
        ReplicatorDTO replicator = newStep.getReplicator();
        if (replicator == null) {
            replicator = new ReplicatorDTO();
        }
        try {
            if (stepJSON.has("replicator")) {
                JSONObject replicatorJSON = stepJSON.getJSONObject("replicator");
                if (replicatorJSON.has("itemsVar") && replicatorJSON.has("arg")) {
                    replicator.setItems(replicatorJSON.getString("itemsVar"));
                    replicator.setArg(replicatorJSON.getString("arg"));
                    newStep.setReplicator(replicator);
                } else {
                    //malformed JSON
                    newStep.setReplicator(null);
                }
            } else {
                newStep.setReplicator(null);
            }
        } catch(JSONException ex) {
            newStep.setReplicator(null);
        }
    }

    private static void updateApprovalFromJSON(ApprovalDTO approval, JSONObject approvalJSON) throws GeneralException, JSONException {
        approval.setName(WebUtil.getJSONString(approvalJSON, "name"));
        
        approval.setReturn(WebUtil.getJSONString(approvalJSON, "returnVal"));
        approval.setSend(WebUtil.getJSONString(approvalJSON, "sendVal"));
        approval.setRenderer(WebUtil.getJSONString(approvalJSON, "renderer"));
        
        String mode = FormJsonUtil.parseScriptFromJSON(approvalJSON, "mode", null);
        if(null != mode) {
            approval.setMode(mode);
            FormJsonUtil.updateExplicitMethod(approvalJSON, "mode", approval.getMode());
        } else {
            approval.setMode(null);
        }
        
        String owner = FormJsonUtil.parseScriptFromJSON(approvalJSON, "owner", null);
        if(null != owner) {
            approval.setOwner(owner);
            FormJsonUtil.updateExplicitMethod(approvalJSON, "owner", approval.getOwner());
        } else {
            approval.setOwner(null);
        }
        
        String description = FormJsonUtil.parseScriptFromJSON(approvalJSON, "description", null);
        if(null != description) {
            approval.setDescription(description);
            FormJsonUtil.updateExplicitMethod(approvalJSON, "description", approval.getDescription());
        } else {
            approval.setDescription(null);
        }
        
        String after = FormJsonUtil.parseScriptFromJSON(approvalJSON, "after", null);
        if ( after != null ) {
            approval.setAfterScript(after);
        } else {
            approval.setAfterScript(null);
        }
        
        if(approvalJSON.has("args"))
            approval.setArgs(updateApprovalArgsFromJSON(approval, approvalJSON));
        
        /** Set Children **/
        if(approvalJSON.has("children")) {
            JSONArray childrenJSON = approvalJSON.getJSONArray("children");
            List<ApprovalDTO> children = new ArrayList<ApprovalDTO>();
            for(int i=0; i<childrenJSON.length(); i++) {
                JSONObject childJSON = childrenJSON.getJSONObject(i);
                ApprovalDTO childDTO = null;

                if(WebUtil.getJSONString(childJSON, "id")!=null) {
                    childDTO = (ApprovalDTO)approval.find(approval.getChildren(), WebUtil.getJSONString(childJSON, "id"));
                }

                ApprovalDTO newChild = null;
                if(childDTO!=null) {
                    newChild = new ApprovalDTO(childDTO);
                } else {
                    newChild = new ApprovalDTO();
                }
                updateApprovalFromJSON(newChild, childJSON);
                children.add(newChild);
            }
            approval.setChildren(children);
        }
        
        // We use the WorkItemConfig object to hold the electronic signature then 
        // persist the value by overriding/adding the argument on the approval step.
        JSONObject workItemConfigJSON = approvalJSON.getJSONObject("workItemConfig");
        if ( workItemConfigJSON != null ) {                
            String elecronicSignature = null;
            if ( !workItemConfigJSON.isNull("electronicSignature" ) ) {
                elecronicSignature = Util.getString(workItemConfigJSON.getString("electronicSignature"));
            }
            List<ArgDTO> args = approval.getArgs();
            
            WorkItemConfigBean wicb = approval.getWorkItemConfig();
            
            if ( elecronicSignature != null ) {
                if(wicb == null || !elecronicSignature.equals(wicb.getElectronicSignature())) {
                    //The electronic signature on the workItemConfigPanel has changed. We will
                    //Update the arg. Otherwise, leave the arg alone as it may have been manually
                    //Changed via the arg panel
                    boolean found = false;
                    if ( args != null ) {
                        for (ArgDTO arg : args ) {
                            if ( Util.nullSafeEq(WorkItem.ATT_ELECTRONIC_SIGNATURE, arg.getName() ) ) {                                
                                arg.setValue(elecronicSignature);
                                found =  true;
                                break;
                            }                                
                        }             
                        if ( !found ) {
                           ArgDTO sigArg = new ArgDTO();
                           sigArg.setValue(elecronicSignature);
                           sigArg.setName(WorkItem.ATT_ELECTRONIC_SIGNATURE);
                           args.add(sigArg);
                        }
                    }
                }
            } else {
              //See if the workItemConfig had a value for electronic signature before save
                if(wicb != null && !Util.isNullOrEmpty(wicb.getElectronicSignature())) {
                    //If there was an electronic signature on the load but not on the save, the user manually
                    //modified the workItemConfig e-Sig and we need to remove it from the Args
                    if ( args != null ) {
                        for (ArgDTO arg : args ) {
                            if ( Util.nullSafeEq(WorkItem.ATT_ELECTRONIC_SIGNATURE, arg.getName() ) ) {                                
                                args.remove(arg);
                                break;
                            }                                
                        }
                    }
                }
            }
        }
        
        if(approvalJSON.has("workItemConfig")) {
            WorkItemConfigBean config = updateWorkItemConfigFromJSON(approval.getWorkItemConfig(), approvalJSON);
            if(config!=null)
                approval.setWorkItemConfig(config);
            else {
                approval.removeWorkItemConfig();
            }
            
        }
    }
    
    private static WorkItemConfigBean updateWorkItemConfigFromJSON(WorkItemConfigBean workItemConfig, JSONObject approvalJSON) throws JSONException {
        JSONObject workItemConfigJSON = approvalJSON.getJSONObject("workItemConfig");
        
        /** Only store the work item config if it's overriding the Workflow's workitemconfig **/
        if(workItemConfigJSON.has("workItemOverride") && workItemConfigJSON.getBoolean("workItemOverride")) {
            if(workItemConfig==null) {
                workItemConfig = new WorkItemConfigBean(new WorkItemConfig());
            }
            
            workItemConfig.setWorkItemEnabled(WebUtil.getJSONBoolean(workItemConfigJSON, "workItemEnabled"));
            workItemConfig.setEscalationStyle(WebUtil.getJSONString(workItemConfigJSON, "escalationStyle"));
            workItemConfig.setDaysTillReminder(WebUtil.getJSONString(workItemConfigJSON, "daysTillReminder"));
            workItemConfig.setDaysBetweenReminders(WebUtil.getJSONString(workItemConfigJSON, "daysBetweenReminders"));
            workItemConfig.setMaxReminders(WebUtil.getJSONString(workItemConfigJSON, "maxReminders"));
            workItemConfig.setEscalationRule(WebUtil.getJSONString(workItemConfigJSON, "escalationRule"));
            workItemConfig.setDaysTillEscalation(WebUtil.getJSONString(workItemConfigJSON, "daysTillEscalation"));
            workItemConfig.setNotificationEmail(WebUtil.getJSONString(workItemConfigJSON, "notificationEmail"));
            workItemConfig.setReminderEmail(WebUtil.getJSONString(workItemConfigJSON, "reminderEmail"));
            workItemConfig.setEscalationEmail(WebUtil.getJSONString(workItemConfigJSON, "escalationEmail"));
            
            if(workItemConfigJSON.has("ownerIds")) {
                List<String> ownerList = new ArrayList<String>();
                JSONArray owners = workItemConfigJSON.getJSONArray("ownerIds");
                for(int i=0; i<owners.length(); i++) {
                    ownerList.add(owners.getString(i));
                }
                workItemConfig.setOwnerIds(ownerList);
            } else {
                workItemConfig.setOwnerIds(null);
            }
            
        } else {
            workItemConfig = null;
        }
        return workItemConfig;
    }

    private static List<TransitionDTO> updateStepTransitionsFromJSON(StepDTO newStep, JSONObject stepJSON) throws JSONException {
        /** Set the transitions **/
        List<TransitionDTO> newTransitions = null;
        JSONArray transitionsJSON = stepJSON.getJSONArray("transitions");
        if(transitionsJSON!=null) {
            newTransitions = new ArrayList<TransitionDTO>();
            for(int j=0; j<transitionsJSON.length(); j++) {
                JSONObject transitionJSON = transitionsJSON.getJSONObject(j);

                TransitionDTO oldTransition = null;
                if(WebUtil.getJSONString(transitionJSON, "id")!=null) {
                    oldTransition = (TransitionDTO)newStep.find(newStep.getTransitions(), WebUtil.getJSONString(transitionJSON, "id"));
                }

                TransitionDTO newTransition = null;
                if(oldTransition!=null) {
                    newTransition = new TransitionDTO(oldTransition);
                } else {
                    newTransition = new TransitionDTO();
                }

                newTransition.setTo(WebUtil.getJSONString(transitionJSON,"to"));
                
                String when = FormJsonUtil.parseScriptFromJSON(transitionJSON, "when", "negate");
                if(null != when) {
                    newTransition.setWhen(when);
                    FormJsonUtil.updateExplicitMethod(transitionJSON, "when", newTransition.getWhen());
                } else {
                    newTransition.removeWhen();
                }

                newTransitions.add(newTransition);
            }
        }

        return newTransitions;
    }
    
    private static List<ReturnDTO> updateStepReturnsFromJSON(StepDTO newStep, JSONObject stepJSON) throws JSONException {
        List<ReturnDTO> returns = null;
        JSONArray returnsJSON = stepJSON.getJSONArray("returns");
        if ( returnsJSON != null  ) {
            returns = new ArrayList<ReturnDTO>();
            for(int i=0; i<returnsJSON.length(); i++) {
                JSONObject retJSON = returnsJSON.getJSONObject(i);
                if ( retJSON != null ) {
                    String name =  WebUtil.getJSONString(retJSON, "name");
                    String to =  WebUtil.getJSONString(retJSON, "to");
                    boolean local = WebUtil.getJSONBoolean(retJSON, "local");
                    boolean merge = WebUtil.getJSONBoolean(retJSON, "merge");
                    
                    String value = FormJsonUtil.parseScriptFromJSON(retJSON, "value", null);
                    //updateExplicitMethod(argJSON, "value", newArg.getValue());
                    
                    ReturnDTO dto = new ReturnDTO();
                    dto.setName(name);
                    dto.setTo(to);
                    dto.setLocal(local);
                    dto.setMerge(merge);
                    dto.setValue(new ScriptDTO(value, null, Scriptlet.METHOD_SCRIPT));
                    returns.add(dto);
                }
            }
        }
        
        return returns;
    }

    private static List<ArgDTO> updateStepArgsFromJSON(StepDTO newStep, JSONObject stepJSON) throws JSONException {
        /** Set the transitions **/
        List<ArgDTO> newArgs = null;
        JSONArray argsJSON = stepJSON.getJSONArray("args");
        if(argsJSON!=null) {
            newArgs = new ArrayList<ArgDTO>();
            for(int j=0; j<argsJSON.length(); j++) {
                JSONObject argJSON = argsJSON.getJSONObject(j);

                ArgDTO oldArg = null;
                if(WebUtil.getJSONString(argJSON, "id")!=null) {
                    oldArg = (ArgDTO)newStep.find(newStep.getArgs(), WebUtil.getJSONString(argJSON, "id"));
                }
                ArgDTO newArg = null;
                if(oldArg!=null) {
                    newArg = new ArgDTO(oldArg);
                } else {
                    newArg = new ArgDTO();
                }
                newArg.setName(WebUtil.getJSONString(argJSON,"name"));
                
                String value = FormJsonUtil.parseScriptFromJSON(argJSON, "value", "negate");
                if(null != value) {
                    newArg.setValue(value);
                    FormJsonUtil.updateExplicitMethod(argJSON, "value", newArg.getValue());
                } else {
                    newArg.removeValue();
                }
                
                newArgs.add(newArg);
            }

        }

        return newArgs;
    }
    
    private static List<ArgDTO> updateApprovalArgsFromJSON(ApprovalDTO approval, JSONObject approvalJSON) throws JSONException {
        /** Set the transitions **/
        List<ArgDTO> newArgs = null;
        JSONArray argsJSON = approvalJSON.getJSONArray("args");
        if(argsJSON!=null) {
            newArgs = new ArrayList<ArgDTO>();
            for(int j=0; j<argsJSON.length(); j++) {
                JSONObject argJSON = argsJSON.getJSONObject(j);

                ArgDTO oldArg = null;
                if(WebUtil.getJSONString(argJSON, "id")!=null) {
                    oldArg = (ArgDTO)approval.find(approval.getArgs(), WebUtil.getJSONString(argJSON, "id"));
                }
                ArgDTO newArg = null;
                if(oldArg!=null) {
                    newArg = new ArgDTO(oldArg);
                } else {
                    newArg = new ArgDTO();
                }
                newArg.setName(WebUtil.getJSONString(argJSON,"name"));

                String value = FormJsonUtil.parseScriptFromJSON(argJSON, "value", null);
                if(null != value) {
                    newArg.setValue(value);
                    FormJsonUtil.updateExplicitMethod(argJSON, "value", newArg.getValue());
                } else {
                    newArg.removeValue();
                }
                newArgs.add(newArg);
            }

        }

        return newArgs;
    }

    private static void updateWorkflowVariablesFromJSON(WorkflowDTO workflow, JSONArray variablesJSON) throws JSONException {
        List<VariableDTO> newVariables = new ArrayList<VariableDTO>();

        for(int i=0; i<variablesJSON.length(); i++) {
            JSONObject variableJSON = variablesJSON.getJSONObject(i);

            /** Try to get the old variable off the session and then just overwrite it with what changed on the ui **/
            VariableDTO oldVariable = null;
            if(WebUtil.getJSONString(variableJSON, "id")!=null) {
                oldVariable = (VariableDTO)workflow.find(workflow.getVariables(), WebUtil.getJSONString(variableJSON, "id"));
            }

            VariableDTO newVariable = null;
            if(oldVariable!=null) {
                newVariable = new VariableDTO(oldVariable);
            } else {
                newVariable = new VariableDTO();
            }

            newVariable.setName(WebUtil.getJSONString(variableJSON, "name"));
            newVariable.setInitializer(WebUtil.getJSONString(variableJSON, "initializer"));
            
            String initializer = FormJsonUtil.parseScriptFromJSON(variableJSON, "initializer", "initializerNegate");
            if(null != initializer) {
                newVariable.setInitializer(initializer);
                FormJsonUtil.updateExplicitMethod(variableJSON, "initializer", newVariable.getInitializer());
            } else {
                newVariable.removeInitializer();
            }
            
            newVariable.setInput(WebUtil.getJSONBoolean(variableJSON,"input"));
            newVariable.setOutput(WebUtil.getJSONBoolean(variableJSON,"output"));
            newVariable.setRequired(WebUtil.getJSONBoolean(variableJSON, "required"));
            newVariable.setEditable(WebUtil.getJSONBoolean(variableJSON, "editable"));
            newVariable.setDescription(WebUtil.getJSONString(variableJSON, "description"));
            newVariable.setType(WebUtil.getJSONString(variableJSON, "type"));
            newVariable.setTranient(WebUtil.getJSONBoolean(variableJSON, "isTransient"));
            newVariables.add(newVariable);
        }
        workflow.setVariables(newVariables);
    }

    private static void updateWorkflowRuleLibrariesFromJSON(WorkflowDTO workflow, JSONArray ruleLibrariesJSON) throws GeneralException, JSONException {
        if ( ruleLibrariesJSON != null ) {
            List<String> rules = new ArrayList<String>();
            for ( int i=0; i<ruleLibrariesJSON.length(); i++ ) {
                JSONObject ruleJSON = ruleLibrariesJSON.getJSONObject(i);
                if ( ruleJSON != null ) {
                    String name = ruleJSON.getString("name");
                    if ( name != null ) {
                        rules.add(name);
                    }
                }
            }
            workflow.setRuleLibraries(rules);
        } 
    }
    
    
    private static List<JSONObject> getRuleLibrariesJson(WorkflowDTO workflow) {
        List<JSONObject> rules = new ArrayList<JSONObject>();
        
        List<String> ruleLibraries = workflow.getRuleLibraries();
        if(ruleLibraries !=null) {
            for( String ruleName :  ruleLibraries ) {
                Map<String, Object> rule = new HashMap<String, Object>();
                rule.put("name", ruleName);                
                rules.add(new JSONObject(rule));
            }
        }
        return rules;
    }     

    /**
     * Converts the specified time, given in the specified unit, to seconds
     * @param time Time that needs to be converted
     * @param timeUnits The TimeUnit from which we should convert
     * @return The specified time, rounded to the nearest second
     */
    public static int getTimeInSeconds(double time, TimeUnits timeUnit) {
        double timeInSeconds = time;
        
        if (timeUnit == TimeUnits.minutes) {
            timeInSeconds *= 60d;
        } else if (timeUnit == TimeUnits.hours) {
            timeInSeconds *= 60d * 60d;
        } else if (timeUnit == TimeUnits.days) {
            timeInSeconds *= 60d * 60d * 24d;
        } else {
            // Default to minutes
            timeInSeconds *= 60d;
        }
        
        return (int) Math.round(timeInSeconds);
    }

    /**
     * Converts the specified time, given in seconds, to an equivalent time given in the specified units
     * @param seconds The time that needs to be converted, specified in seconds
     * @param timeUnit TimeUnits to which we want to convert the specified time
     * @return The specified time, given in the specified units and rounded to two decimal places
     */
    public static double getTimeFromSeconds(int seconds, TimeUnits timeUnit) {
        double convertedTime = seconds;
        if (timeUnit == TimeUnits.minutes) {
            convertedTime /= 60d;
        } else if (timeUnit == TimeUnits.hours) {
            convertedTime /= 60d * 60d;
        } else if (timeUnit == TimeUnits.days) {
            convertedTime /= 60d * 60d * 24d;
        } else {
            // Default to minutes
            convertedTime /= 60d;
        }
            
        // Now round it to two decimal places
        convertedTime *= 100d;
        long roundedTime = Math.round(convertedTime);
        convertedTime = roundedTime / 100d;
        return convertedTime;
    }
}
