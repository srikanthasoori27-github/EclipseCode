/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.roles;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.DirectMiningTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.FilterContainer;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.IdentityFilterConstraints;
import sailpoint.web.task.TaskDefinitionBean;
import sailpoint.web.task.TaskResultBean;
import sailpoint.web.util.WebUtil;

/**
 * This class is used to create a new role from the results of stand-alone directed mining
 */
public class AutomatedDirectedMiningBean extends BaseBean {
    private static final Log log = LogFactory.getLog(AutomatedDirectedMiningBean.class);
    
    private static final String CREATE_NEW_ROLE = "createNewRole";
    private static final String ADD_TO_EXISTING_ROLE = "addToExistingRole";
    private static final String CURRENT_FILTER_BEANS = "currentFilterBeans";
    private static final String TASK_NAME = "IT Role Mining";
    private static final String TASK_DEFINITION_SUB_TYPE = "IT Role Mining";
    private static final String IDENTITY_FILTER = "identityFilter";
    private static final String GROUP_FILTERS = "groupFilters";
    private static final String FORCE_RESET = "forceReset";
    private static final String LAST_LAUNCH_RESULT = "itRoleMiningLaunchResult";
    private String newRoleName;
    private String newRoleOwner;
    private String newRoleType;
    private String existingRoleName;
    private Bundle existingRole;
    private String containerRoleName;
    private Bundle containerRole;
    private List<Object> applications;
    private List<String> groups;
    private String entitlementCreationOption;
    private String filterCreationOption;
    private transient IdentityFilterConstraints identityFilterConstraints;
    private List<Filter> currentFilters;    

    /** For saving/loading mining templates **/
    private String templateName;
    private String templateId;
    private String saveResult;
    
    private int threshold;
    private boolean isSimulateActivity;
    
    @SuppressWarnings("unchecked")
    public AutomatedDirectedMiningBean() {
        Map requestParams = getRequestParam();
        String forceReset = (String) requestParams.get(FORCE_RESET);
        final boolean isForceReset = (forceReset != null && Boolean.parseBoolean((String)forceReset)); 
        if (isForceReset) {
            clearHttpSession();
        }
        entitlementCreationOption = CREATE_NEW_ROLE;
        filterCreationOption = GROUP_FILTERS;
        currentFilters = (List<Filter>) getSessionScope().get(CURRENT_FILTER_BEANS);
        if (currentFilters == null) {
            currentFilters = new ArrayList<Filter>();
            getSessionScope().put(CURRENT_FILTER_BEANS, currentFilters);
        }
        
        templateId = getRequestParameter("templateId");
        if(templateId!=null && !templateId.equals("")) {
            loadTemplate();
        }
    }
    
    public String getExistingRoleName() {
        return existingRoleName;
    }

    public void setExistingRoleName(String existingRoleName) {
        this.existingRoleName = existingRoleName;
    }
    
    public String getExistingRole() {
        String roleId = null;
        if (existingRole != null)
            roleId = existingRole.getId();
        
        return roleId;
    }
    
    public void setExistingRole(String existingRoleId) {
        if (existingRoleId != null && existingRoleId.trim().length() > 0) {
            try {
                this.existingRole = getContext().getObjectById(Bundle.class, existingRoleId);
            } catch (GeneralException e) {
                log.error("Unable to set container role with id " + existingRoleId);
            }
        }
    }

    public String getNewRoleName() {
        return newRoleName;
    }

    public void setNewRoleName(String newRoleName) {
        this.newRoleName = newRoleName;
    }

    public String getNewRoleOwner() {
        return newRoleOwner;
    }

    public void setNewRoleOwner(String newRoleOwner) {
        this.newRoleOwner = newRoleOwner;
    }

    public String getNewRoleOwnerName() {
        String ownerName = null;
        try {
            if (newRoleOwner != null) {
                Identity owner = getContext().getObjectById(Identity.class, newRoleOwner);
                if (owner != null) {
                    ownerName = owner.getDisplayableName();
                }
            }
        } catch (GeneralException e) {
            ownerName = null;
        }

        return ownerName;
    }
    
    public String getNewRoleType() {
        return newRoleType;
    }

    public void setNewRoleType(String newRoleType) {
        this.newRoleType = newRoleType;
    }

    public String getContainerRoleName() {
        return containerRoleName;
    }

    public void setContainerRoleName(String containerRoleName) {
        this.containerRoleName = containerRoleName;
    }
    
    public String getContainerRole() {
        String roleId = null;
        if (containerRole != null)
            roleId = containerRole.getId();
        
        return roleId;
    }
    
    public void setContainerRole(String containerRoleId) {
        if (containerRoleId != null && containerRoleId.trim().length() > 0) {
            try {
                this.containerRole = getContext().getObjectById(Bundle.class, containerRoleId);
            } catch (GeneralException e) {
                log.error("Unable to set container role with id " + containerRoleId);
            }
        }
    }

    public List<Object> getApplications() {
        return applications;
    }

    public void setApplications(List<Object> applications) {
        this.applications = applications;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public boolean isSimulateActivity() {
        return isSimulateActivity;
    }

    public void setSimulateActivity(boolean isSimulateActivity) {
        this.isSimulateActivity = isSimulateActivity;
    }
    
    public String getEntitlementCreationOption() {
        return entitlementCreationOption;
    }
    
    public void setEntitlementCreationOption(String entitlementCreationOption) {
        this.entitlementCreationOption = entitlementCreationOption;
    }
    
    public List<SelectItem> getEntitlementCreationOptions() {
        List<SelectItem> options = new ArrayList<SelectItem>();
        options.add(new SelectItem(CREATE_NEW_ROLE, getMessage(new Message(MessageKeys.AUTOMATED_MINING_CREATE_NEW_ROLE))));
        options.add(new SelectItem(ADD_TO_EXISTING_ROLE, getMessage(new Message(MessageKeys.AUTOMATED_MINING_ADD_TO_EXISTING_ROLE))));
        return options;
    }

    public String getFilterCreationOption() {
        return filterCreationOption;
    }
    
    public void setFilterCreationOption(String filterCreationOption) {
        this.filterCreationOption = filterCreationOption;
    }
    
    public List<SelectItem> getFilterCreationOptions() {
        List<SelectItem> options = new ArrayList<SelectItem>();
        options.add(new SelectItem(GROUP_FILTERS, getMessage(new Message(MessageKeys.AUTOMATED_MINING_GROUP_FILTER))));
        options.add(new SelectItem(IDENTITY_FILTER, getMessage(new Message(MessageKeys.AUTOMATED_MINING_IDENTITY_FILTER))));
        return options;
    }

    public String getGroupsJson() {
        String jsonData;
        
        try {
            jsonData = WebUtil.basicJSONData(groups, "GroupDefinitionListConverter");
        } catch (GeneralException e) {
            log.error("Failed to render a JSON String for the selected groups", e);
            jsonData = JsonHelper.emptyListResult("totalCount", "objects");
        }
        
        return jsonData;
    }

    public String getApplicationsJson() {
        String jsonData;
        
        try {
            jsonData = WebUtil.basicJSONData(applications);
        } catch (GeneralException e) {
            log.error("Failed to render a JSON String for the selected applications", e);
            jsonData = JsonHelper.emptyListResult("totalCount", "objects");
        }
        
        return jsonData;
    }
    
    public FilterContainer getIdentityConstraints() {
        if (identityFilterConstraints == null) {
            identityFilterConstraints = new IdentityFilterConstraints(currentFilters);
            String op = identityFilterConstraints.initFilters();
            if(null != op)
                identityFilterConstraints.setGlobalBooleanOp(op);
        }
        return identityFilterConstraints;
    }
    
    public String getLaunchResult() {
        Object lastLaunchResult = getSessionScope().get(LAST_LAUNCH_RESULT);
        if (lastLaunchResult == null) {
            lastLaunchResult = "";
        }
        
        return lastLaunchResult.toString();
    }
    
    public Attributes<String,Object> getTaskArguments() throws GeneralException{
        Attributes<String,Object> args = new Attributes<String,Object>();
        
        if (CREATE_NEW_ROLE.equals(entitlementCreationOption)) {
            if (newRoleName != null && newRoleName.trim().length() > 0)
                args.put(DirectMiningTask.ARG_NEW_ROLE_NAME, newRoleName);
            args.put(DirectMiningTask.ARG_NEW_ROLE_OWNER, newRoleOwner);
            args.put(DirectMiningTask.ARG_NEW_ROLE_TYPE, newRoleType);
            if (containerRole != null)
                args.put(DirectMiningTask.ARG_CONTAINER_ROLE, containerRole.getId());
        } else if (ADD_TO_EXISTING_ROLE.equals(entitlementCreationOption)) {
            args.put(DirectMiningTask.ARG_ROLE_ID, existingRole.getId());
        } else {
            throw new GeneralException("No entitlementCreationOption was provided to the AutomatedDirectedMiningBean");
        }
        
        if (GROUP_FILTERS.equals(filterCreationOption))
            args.put(DirectMiningTask.ARG_GROUP_NAMES, Util.listToCsv(groups));
        else if (IDENTITY_FILTER.equals(filterCreationOption)) {
            args.put(DirectMiningTask.ARG_FILTER, identityFilterConstraints.getFilterString());
            args.put(DirectMiningTask.ARG_CONSTRAINT_FILTERS, identityFilterConstraints.getFilters());
        }
        
        args.put(DirectMiningTask.ARG_APPLICATIONS, Util.listToCsv(applications));
        args.put(DirectMiningTask.ARG_THRESHOLD, String.valueOf(threshold));
        args.put(DirectMiningTask.ARG_SIMULATE, String.valueOf(isSimulateActivity));   
        
        args.put(TaskSchedule.ARG_RESULT_NAME, getMiningResultName());
        
        return args;
    }
    
    public String saveTemplate() {
        
        log.debug("Template ID: " + templateId);
        log.debug("Template Name: " + templateName);
        
        try {
            TaskDefinition def = null;
            /** if update **/
            if(templateId!=null && !templateId.equals("")) {
                def = getContext().getObjectById(TaskDefinition.class, templateId);
                def.setArguments(getTaskArguments());
            } else {
                
                /** Check to see if a task with this name already exists**/
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("name", templateName));
                int count = getContext().countObjects(TaskDefinition.class, qo);
                if(count>0) {
                    Message msg = new Message(MessageKeys.ROLE_MINING_TEMPLATE_DUPLICATE);
                    setSaveResult(msg.getMessage());
                    return "";
                }
                
                TaskDefinition parent = getContext().getObjectByName(TaskDefinition.class, TASK_NAME);
                def = TaskDefinitionBean.assimilateDef(parent);
                def.setArguments(getTaskArguments());
                def.setSubType(TASK_DEFINITION_SUB_TYPE);
                def.setOwner(getLoggedInUser());
                def.setName(templateName);
            }
            setSaveResult("success");
            
            getContext().saveObject(def);
            getContext().commitTransaction();
            log.debug("Saved Mining Template: " + templateName);

            templateId = def.getId();
        } catch (GeneralException ge) {
            log.warn("Unable to save new template for role mining: " + ge.getMessage());
            setSaveResult("error");
        }
        return "";
    }
    
    private void loadTemplate() {
        log.debug("Loading Template: " + templateId);
        try {
            TaskDefinition def = getContext().getObjectById(TaskDefinition.class, templateId);
            if(def!=null) {
                templateName = def.getName();
                
                threshold = Util.atoi((String)def.getArgument(DirectMiningTask.ARG_THRESHOLD));
                isSimulateActivity = Util.atob((String)def.getArgument(DirectMiningTask.ARG_SIMULATE));   
                groups = Util.stringToList((String)def.getArgument(DirectMiningTask.ARG_GROUP_NAMES));
                newRoleName = (String)def.getArgument(DirectMiningTask.ARG_NEW_ROLE_NAME);
                
                if(newRoleName!=null) {                
                    newRoleOwner = (String)def.getArgument(DirectMiningTask.ARG_NEW_ROLE_OWNER);
                    newRoleType = (String)def.getArgument(DirectMiningTask.ARG_NEW_ROLE_TYPE);
                    entitlementCreationOption = CREATE_NEW_ROLE;
                } else {
                    entitlementCreationOption = ADD_TO_EXISTING_ROLE;
                    String roleId = (String)def.getArgument(DirectMiningTask.ARG_ROLE_ID);
                    if(roleId!=null) {
                        existingRole = getContext().getObjectById(Bundle.class, roleId);
                        if(existingRole!=null)
                            existingRoleName = existingRole.getName();
                    }
                }
                
                groups = Util.stringToList((String)def.getArgument(DirectMiningTask.ARG_GROUP_NAMES));
                if(groups!=null) {
                    filterCreationOption = GROUP_FILTERS;
                }
                
                currentFilters = (List<Filter>)def.getArgument(DirectMiningTask.ARG_CONSTRAINT_FILTERS);
                if(currentFilters!=null) {
                    getSessionScope().put(CURRENT_FILTER_BEANS, currentFilters);
                    filterCreationOption = IDENTITY_FILTER;
                }
                
                String containerRoleId = (String)def.getArgument(DirectMiningTask.ARG_CONTAINER_ROLE);
                if(containerRoleId!=null) {
                    containerRole = getContext().getObjectById(Bundle.class, containerRoleId);
                    if(containerRole!=null)
                        containerRoleName = containerRole.getName();
                }
                
                List<String> appIds = Util.csvToList((String)def.getArgument(DirectMiningTask.ARG_APPLICATIONS));
                if(appIds!=null) {
                    applications = new ArrayList<Object>();
                    for(String appId : appIds) {
                        Application app = getContext().getObjectById(Application.class, appId);
                        if(app!=null)
                            applications.add(app);
                    }
                }
                
            }
        } catch(GeneralException ge) {
            log.warn("Unable to load task definition: " + templateId + ". Exception: " + ge.getMessage());
        }
    }
    
    public String deleteTempate() {
        if(templateId!=null) {
            try {
                TaskDefinition def = getContext().getObjectById(TaskDefinition.class, templateId);
                if(def!=null) {
                    Terminator ahnold = new Terminator(getContext());
                    ahnold.deleteObject(def);
                    addMessage(new Message(Message.Type.Info,
                            MessageKeys.TASK_DEFINITION_DELETED, def.getName()), null);
                }
                templateId = null;
            } catch(GeneralException ge) {
                log.warn("Exception while deleting mining template: " + ge.getMessage());
            }
        }
        return "";
    }
    
    public String launchMining() {
        try {
            TaskManager tm = new TaskManager(getContext());
            boolean isTaskRunning = false;
            LocalizedDate lDate = new LocalizedDate(new Date(), DateFormat.FULL, DateFormat.LONG);
            String taskName = null;
            if(!Util.isNullOrEmpty(getTemplateName())) {
                taskName = templateName + " " + lDate.getLocalizedMessage(getLocale(), getUserTimeZone());
            } else {
                taskName = TASK_NAME + " " + lDate.getLocalizedMessage(getLocale(), getUserTimeZone());
            }
            
            isTaskRunning = tm.isTaskRunning(taskName, getMiningResultName());            
            if (isTaskRunning) {
                getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.MINING_ALREADY_RUNNING, TASK_NAME));                
            } else {
                TaskSchedule ts = new TaskSchedule();
                ts.setName(taskName);
                TaskDefinition def = null;
                if(!Util.isNullOrEmpty(templateId)) {
                    def = getContext().getObjectById(TaskDefinition.class, templateId);
                } else {
                    def = getContext().getObjectByName(TaskDefinition.class, TASK_NAME);
                }
                ts.setArguments(getTaskArguments());
                ts.setTaskDefinition(def);
                ts.setLauncher(getLoggedInUserName());
                
                tm.runNow(ts);
                getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.AUTOMATED_MINING_SUCCESSFUL));
            }
            
        } catch (GeneralException e) {
            log.error("Failed to launch Business Functional Role Mining Task", e);
            getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.MINING_FAILED, "IT Role Mining"));
        }
        
        return "";
    }
    
    public List<ObjectAttribute> getAppAttributeDefinitions() {
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        List<ObjectAttribute> appAttributeDefinitions = identityConfig.getObjectAttributes();
        
        return appAttributeDefinitions;
    }
    
    @SuppressWarnings("unchecked")
    public String viewLastTaskResult() {
        try {
            TaskResult resultToView = getContext().getObjectByName(TaskResult.class, getMiningResultName());
            if (resultToView != null)
                getSessionScope().put(TaskResultBean.ATT_RESULT_ID, resultToView.getId());
        } catch (GeneralException e) {
            log.error("Failed to retrieve the last result", e);
            getSessionScope().put(TaskResultBean.ATT_RESULT_ID, null);
        }
        
        return "";
    }
    
    public void clearHttpSession() {
        getSessionScope().remove(CURRENT_FILTER_BEANS);
    }
    
    public String getMiningResultId() throws GeneralException { 
         QueryOptions queryOptions = new QueryOptions(); 
         queryOptions.add(Filter.eq("definition.id", getTemplateId())); 
         queryOptions.addOrdering("completed", false); 
         queryOptions.setResultLimit(1); 
          
         Iterator<TaskResult> taskResults = getContext().search(TaskResult.class, queryOptions); 
          
         if (taskResults.hasNext()) { 
             TaskResult taskResult = taskResults.next(); 
             return taskResult.getId(); 
         } 
          
         return ""; 
    } 
    
    public String getMiningResultName() throws GeneralException {
        if(getTemplateName()!=null)
            return getTemplateName() + " - " + getLoggedInUserName();
        
        return TASK_NAME + " - " + getLoggedInUserName();
    }
    
    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getSaveResult() {
        return saveResult;
    }

    public void setSaveResult(String saveResult) {
        this.saveResult = saveResult;
    }
}
