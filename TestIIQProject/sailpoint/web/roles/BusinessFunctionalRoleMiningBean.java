/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.roles;

import java.io.StringWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.TaskManager;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.BusinessFunctionalRoleGenerator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.task.TaskDefinitionBean;
import sailpoint.web.task.TaskResultBean;
import sailpoint.web.util.WebUtil;

/**
 * This class is used to create a new role from the results of stand-alone directed mining
 */
public class BusinessFunctionalRoleMiningBean extends BaseBean {
    private static final Log log = LogFactory.getLog(BusinessFunctionalRoleMiningBean.class);
    private static final String NAMING_ALGORITHM_FILTER = "filterBased";
    private static final String NAMING_ALGORITHM_UID = "genericUid";
    private static final String LAST_LAUNCH_RESULT = "businessRoleMiningLaunchResult";
    private static final String TASK_DEFINITION_NAME = "Business Role Mining";
    private static final String TASK_DEFINITION_SUB_TYPE = "Business Role Mining";
    
    private List<Object> scopingAttributes;
    private String ownerName;
    private Identity owner;
    private boolean generateOrgRoles;
    private String containerRoleName;
    private Bundle containerRole;
    private String roleNamePrefix;
    private String minNumUsersPerRole;
    private boolean useInheritedRoles;
    private boolean computePopulationStatistics;
    private String namingAlgorithm;
    private boolean mineForEntitlements;
    private List<String> applications;
    private String threshold;
    private String relationshipToIt;
    private boolean attachProfilesToBfr;
    private String createdOrganizationalRoleType;
    private String createdBusinessRoleType;
    private String createdITRoleType;
    private boolean analyzeOnly;
    
    private List<SelectItem> namingOptions;
    private List<SelectItem> relationshipOptions;
    
    /** For saving/loading mining templates **/
    private String templateName;
    private String templateId;
    private String saveResult;
    
    private String originalTemplateName;

    public BusinessFunctionalRoleMiningBean() {
        relationshipToIt = "permits";
        namingAlgorithm = "filterBased";
        
        // Apply defaults if possible
        ObjectConfig roleConfig;
        try {
            roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            RoleTypeDefinition typeDef = roleConfig.getRoleType("organizational");
            if (typeDef != null) {
                createdOrganizationalRoleType = typeDef.getName();
            }
            
            typeDef = roleConfig.getRoleType("business");
            if (typeDef != null) {
                createdBusinessRoleType = typeDef.getName();
            }

            typeDef = roleConfig.getRoleType("it");
            if (typeDef != null) {
                createdITRoleType = typeDef.getName();
            }
        } catch (Throwable t) {
            log.error("The BusinessFunctionalRoleMiningBean is unable to properly fetch RoleTypeDefinitions.", t);
        }
        
        templateId = getRequestParameter("templateId");
        if(templateId!=null && !templateId.equals("")) {
            loadTemplate();
        }
    }
    
    public String getOriginalTemplateName() {
        return originalTemplateName;
    }
    
    public void setOriginalTemplateName(String originalTemplateName) { 
        this.originalTemplateName = originalTemplateName;
    }
    
    public List<Object> getScopingAttributes() {
        return scopingAttributes;
    }

    public void setScopingAttributes(List<Object> scopingAttributes) {
        this.scopingAttributes = scopingAttributes;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwner() {
        String ownerId = null;
        if (owner != null)
            ownerId = owner.getId();
        return ownerId;
    }

    public void setOwner(String ownerId) {
        if (ownerId != null && ownerId.trim().length() > 0) {
            try {
                owner = getContext().getObjectById(Identity.class, ownerId);
            } catch (GeneralException e) {
                log.error("Failed to set an owner with ID " + ownerId, e);
            }
        }
    }

    public boolean isGenerateOrgRoles() {
        return generateOrgRoles;
    }

    public void setGenerateOrgRoles(boolean generateOrgRoles) {
        this.generateOrgRoles = generateOrgRoles;
    }

    public String getContainerRoleName() {
        return containerRoleName;
    }

    public void setContainerRoleName(String containerRoleName) {
        this.containerRoleName = containerRoleName;
    }
    
    public String getContainerRole() {
        String roleId = null;
        if (containerRole != null) {
            roleId = containerRole.getId();
        }
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

    public String getRoleNamePrefix() {
        return roleNamePrefix;
    }

    public void setRoleNamePrefix(String roleNamePrefix) {
        this.roleNamePrefix = roleNamePrefix;
    }

    public String getMinNumUsersPerRole() {
        return minNumUsersPerRole;
    }

    public void setMinNumUsersPerRole(String minNumUsersPerRole) {
        this.minNumUsersPerRole = minNumUsersPerRole;
    }

    public boolean isUseInheritedRoles() {
        return useInheritedRoles;
    }

    public void setUseInheritedRoles(boolean useInheritedRoles) {
        this.useInheritedRoles = useInheritedRoles;
    }

    public boolean isComputePopulationStatistics() {
        return computePopulationStatistics;
    }

    public void setComputePopulationStatistics(boolean computePopulationStatistics) {
        this.computePopulationStatistics = computePopulationStatistics;
    }

    public String getNamingAlgorithm() {
        return namingAlgorithm;
    }

    public void setNamingAlgorithm(String namingAlgorithm) {
        this.namingAlgorithm = namingAlgorithm;
    }

    public boolean isMineForEntitlements() {
        return mineForEntitlements;
    }

    public void setMineForEntitlements(boolean mineForEntitlements) {
        this.mineForEntitlements = mineForEntitlements;
    }

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getRelationshipToIt() {
        return relationshipToIt;
    }

    public void setRelationshipToIt(String relationshipToIt) {
        this.relationshipToIt = relationshipToIt;
    }
    
    public boolean isAttachProfilesToBfr() {
        return attachProfilesToBfr;
    }
    
    public void setAttachProfilesToBfr(boolean attachProfilesToBfr) {
        this.attachProfilesToBfr = attachProfilesToBfr;
    }

    public String getCreatedOrganizationalRoleType() {
        return createdOrganizationalRoleType;
    }

    public void setCreatedOrganizationalRoleType(
            String createdOrganizationalRoleType) {
        this.createdOrganizationalRoleType = createdOrganizationalRoleType;
    }

    public String getCreatedBusinessRoleType() {
        return createdBusinessRoleType;
    }

    public void setCreatedBusinessRoleType(String createdBusinessRoleType) {
        this.createdBusinessRoleType = createdBusinessRoleType;
    }

    public String getCreatedITRoleType() {
        return createdITRoleType;
    }

    public void setCreatedITRoleType(String createdITRoleType) {
        this.createdITRoleType = createdITRoleType;
    }

    public boolean isAnalyzeOnly() {
        return analyzeOnly;
    }

    public void setAnalyzeOnly(boolean analyzeOnly) {
        this.analyzeOnly = analyzeOnly;
    }

    public List<SelectItem> getNamingOptions() {
        if (namingOptions == null) {
            namingOptions = new ArrayList<SelectItem>();
            namingOptions.add(new SelectItem(NAMING_ALGORITHM_FILTER, new Message(MessageKeys.ROLE_NAMING_FILTER_BASED).getLocalizedMessage(getLocale(), getUserTimeZone())));
            namingOptions.add(new SelectItem(NAMING_ALGORITHM_UID, new Message(MessageKeys.ROLE_NAMING_GENERIC_UID).getLocalizedMessage(getLocale(), getUserTimeZone())));
        }
        
        return namingOptions;
    }

    public List<SelectItem> getRelationshipOptions() {
        if (relationshipOptions == null) {
            relationshipOptions = new ArrayList<SelectItem>();
            relationshipOptions.add(new SelectItem("permits", new Message(MessageKeys.ROLE_RELATIONSHIP_PERMITS).getLocalizedMessage(getLocale(), getUserTimeZone())));
            relationshipOptions.add(new SelectItem("requires", new Message(MessageKeys.ROLE_RELATIONSHIP_REQUIRES).getLocalizedMessage(getLocale(), getUserTimeZone())));
        }
        
        return relationshipOptions;
    }
    
    private List<SelectItem> getRoleTypes(RoleUtil.RoleType type) {
        List<SelectItem> items = null;
        try {
            items = RoleUtil.getRoleTypeSelectList(type, getLoggedInUser(), getLocale());
        } catch (GeneralException e) {
            log.error(e);
        }
        if (items == null || items.isEmpty())
            items.add(new SelectItem("", Internationalizer.getMessage(MessageKeys.ERR_NO_TYPE_FOUND, getLocale())));
        return items;
    }
    
    /**
     * Get role types that have the same characteristics as IT roles, as we defined them out of the box.
     * Note that this may return other types (including Business and Organizational) if customers reconfigure
     * role types.
     * @return role types that have the same characteristics as IT roles, as we have defined them out of the box.
     */
    public List<SelectItem> getItRoleTypes() {
        return getRoleTypes(RoleUtil.RoleType.ITRole);
    }

    /**
     * Get role types that have the same characteristics as Business roles, as we defined them out of the box.
     * Note that this may return other types (including IT and Organizational) if customers reconfigure
     * role types.
     * @return role types that have the same characteristics as Business roles, as we have defined them out of the box.
     */
    public List<SelectItem> getBusinessRoleTypes() {
        return getRoleTypes(RoleUtil.RoleType.BusinessRole);
    }

    /**
     * Get role types that have the same characteristics as Organizational roles, as we defined them out of the box.
     * Note that this may return other types (including IT and Business) if customers reconfigure
     * role types.
     * @return role types that have the same characteristics as Organizational roles, as we have defined them out of the box.
     */
    public List<SelectItem> getOrganizationalRoleTypes() {
        return getRoleTypes(RoleUtil.RoleType.OrganizationalRole);
    }
    
    public String getApplicationsJSON() {
        String jsonData;
        
        try {
            if (applications == null || applications.isEmpty()) {
                jsonData = JsonHelper.emptyListResult("totalCount", "objects");
            } else {
                Iterator<Application> appIterator = getContext().search(Application.class, new QueryOptions(Filter.in("id", applications)));
                if (appIterator == null || !appIterator.hasNext()) {
                    jsonData = JsonHelper.emptyListResult("totalCount", "objects");
                } else {
                    List<Application> apps = new ArrayList<Application>();
                    while (appIterator.hasNext()) {
                        apps.add(appIterator.next());
                    }
                    jsonData = WebUtil.basicJSONData(apps);
                }
            }
        } catch (GeneralException e) {
            log.error("Failed to render a JSON String for the selected applications", e);
            jsonData = JsonHelper.emptyListResult("totalCount", "objects");
        }
        
        return jsonData;
    }
    
    public String getScopingAttributesJSON() {
        String jsonData;
        
        try {
            StringWriter jsonString = new StringWriter();
            JSONWriter jsonWriter = new JSONWriter(jsonString);

            jsonWriter.object();

            // figure out how many items we have
            jsonWriter.key("totalCount");
            int totalCount = 0;
            if (scopingAttributes != null)
                totalCount = scopingAttributes.size();
            jsonWriter.value(totalCount);

            // now write out the id and name for each item
            jsonWriter.key("objects");
            jsonWriter.array();
            
            if (scopingAttributes != null) {
                for (Object scopingAttributeObj : scopingAttributes) {
                    ObjectAttribute scopingAttribute = (ObjectAttribute) scopingAttributeObj;
                    jsonWriter.object();
                    
                    jsonWriter.key("id");
                    jsonWriter.value(scopingAttribute.getName());
                    
                    jsonWriter.key("displayField");
                    jsonWriter.value(scopingAttribute.getDisplayableName());
                    
                    jsonWriter.key("name");
                    jsonWriter.value(scopingAttribute.getName());
                    
                    jsonWriter.endObject();
                }
            }

            jsonWriter.endArray();            
            jsonWriter.endObject();
            
            jsonData = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to render a JSON String for the selected scoping attributes", e);
            jsonData = JsonHelper.emptyListResult("totalCount", "objects");
        }
        
        return jsonData;
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
        /** Set all the necessary arguments **/
        if (scopingAttributes != null) {
            log.debug("Save Scoping: " + scopingAttributes);
            List<String> attributeNameList = new ArrayList<String>();
            for (Object scopingAttributeObj : scopingAttributes) {
                String scopingAttributeName;
                if (scopingAttributeObj instanceof ObjectAttribute) {
                    scopingAttributeName = ((ObjectAttribute) scopingAttributeObj).getName();
                } else {
                    scopingAttributeName = (String) scopingAttributeObj;
                }
                attributeNameList.add(scopingAttributeName);
            }
            
            String scopingAttributeCSV = Util.listToCsv(attributeNameList);
            args.put(BusinessFunctionalRoleGenerator.ARG_TIERED_SCOPING_ATTRIBUTES, scopingAttributeCSV);
        }
        String ownerName;
        if (owner == null) {
            // This could be coming from an "Analysis Only" mining task -- mock one up in that case
            ownerName = "None";
        } else {
            ownerName = owner.getName();
        }
        args.put(BusinessFunctionalRoleGenerator.ARG_OWNER, ownerName);
        args.put(BusinessFunctionalRoleGenerator.ARG_GEN_CONTAINER_ROLE, String.valueOf(generateOrgRoles));
        args.put(BusinessFunctionalRoleGenerator.ARG_MINIMUM_ROLE_SIZE, minNumUsersPerRole);
        args.put(BusinessFunctionalRoleGenerator.ARG_BFR_PREFIX, roleNamePrefix);
        if (!generateOrgRoles && containerRole != null)
            args.put(BusinessFunctionalRoleGenerator.ARG_CONTAINER_ROLE, containerRole.getId());
        args.put(BusinessFunctionalRoleGenerator.ARG_CREATE_SUBROLES, String.valueOf(useInheritedRoles));
        args.put(BusinessFunctionalRoleGenerator.ARG_COMPUTE_COVERAGE, String.valueOf(computePopulationStatistics));
        args.put(BusinessFunctionalRoleGenerator.ARG_UID_NAMING, String.valueOf(isUIDNaming()));
        args.put(BusinessFunctionalRoleGenerator.ARG_SIMULATE, String.valueOf(analyzeOnly));
        args.put(BusinessFunctionalRoleGenerator.ARG_MINE_IT_ROLES, String.valueOf(mineForEntitlements));
        args.put(TaskSchedule.ARG_RESULT_NAME, getMiningResultName());
        
        if (mineForEntitlements) {
            List<String> appIds = new ArrayList<String>();
            for (Object appObj : applications) {
                String appId = (String) appObj;
                appIds.add(appId);
            }
            args.put(BusinessFunctionalRoleGenerator.ARG_APPLICATIONS, Util.listToCsv(appIds));
            
            args.put(BusinessFunctionalRoleGenerator.ARG_THRESHOLD, threshold);
            args.put(BusinessFunctionalRoleGenerator.ARG_ATTACH_IT_PROFILES, String.valueOf(attachProfilesToBfr));
            if (!attachProfilesToBfr)
                args.put(BusinessFunctionalRoleGenerator.ARG_IT_ROLE_ASSOCIATION, "requires".equals(relationshipToIt));
        }
        args.put(BusinessFunctionalRoleGenerator.ARG_ORGANIZATIONAL_ROLE_TYPE, createdOrganizationalRoleType);
        args.put(BusinessFunctionalRoleGenerator.ARG_BUSINESS_ROLE_TYPE, createdBusinessRoleType);
        args.put(BusinessFunctionalRoleGenerator.ARG_IT_ROLE_TYPE, createdITRoleType);
        
        return args;
    }
    
    /** Takes the configured set of arguments and saves them as a new template that can be used to
     * load up the mining form again and kick of the mining
     * @return
     */
    public String saveTemplate() {
        
        log.debug("Template ID: " + templateId);
        log.debug("Template Name: " + templateName);
        
        try {
            TaskDefinition def = null;
            /** if update **/
            if(templateId!=null && !templateId.equals("")) {                
                def = getContext().getObjectById(TaskDefinition.class, templateId);
                
                if (!Util.nullSafeEq(def.getName(), templateName)) {
                    QueryOptions qo = new QueryOptions();
                    qo.add(Filter.eq("name", templateName));
                    int count = getContext().countObjects(TaskDefinition.class, qo);
                    if(count>0) {
                        Message msg = new Message(MessageKeys.ROLE_MINING_TEMPLATE_DUPLICATE);
                        setSaveResult(msg.getMessage());
                        return "";
                    }
                }
                
                def.setName(templateName);
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
                
                TaskDefinition parent = getContext().getObjectByName(TaskDefinition.class, TASK_DEFINITION_NAME);
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
            log.error("Unable to save new template for role mining: " + ge.getMessage());
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
                originalTemplateName = templateName;
                
                /** Booleans **/
                computePopulationStatistics = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_COMPUTE_COVERAGE));
                generateOrgRoles = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_GEN_CONTAINER_ROLE));
                useInheritedRoles = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_CREATE_SUBROLES));
                analyzeOnly = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_SIMULATE));
                mineForEntitlements = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_MINE_IT_ROLES));
                attachProfilesToBfr = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_ATTACH_IT_PROFILES));
                boolean uidNaming = Util.atob((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_UID_NAMING));
                if(uidNaming)
                    namingAlgorithm = NAMING_ALGORITHM_UID;
                
                /** Strings **/
                threshold = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_THRESHOLD);
                roleNamePrefix = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_BFR_PREFIX);
                minNumUsersPerRole = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_MINIMUM_ROLE_SIZE);
                boolean isRequires = Util.otob(def.getArgument(BusinessFunctionalRoleGenerator.ARG_IT_ROLE_ASSOCIATION));
                relationshipToIt = isRequires ? "requires" : "permits";
                createdITRoleType = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_IT_ROLE_TYPE);
                createdBusinessRoleType = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_BUSINESS_ROLE_TYPE);
                createdOrganizationalRoleType = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_ORGANIZATIONAL_ROLE_TYPE);
                                
                String containerRoleId = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_CONTAINER_ROLE);
                if(containerRoleId!=null) {
                    containerRole = getContext().getObjectById(Bundle.class, containerRoleId);
                    containerRoleName = containerRole.getName();
                }
                
                ownerName = (String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_OWNER);
                if(ownerName!=null) {
                    owner = getContext().getObjectByName(Identity.class, ownerName);
                }
                
                List<String> attributes = Util.csvToList((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_TIERED_SCOPING_ATTRIBUTES));
                if (attributes == null || attributes.isEmpty()) {
                    attributes = Util.csvToList((String)def.getArgument(BusinessFunctionalRoleGenerator.LEGACY_ARG_TIERED_SCOPING_ATTRIBUTES));
                }
                
                if(attributes != null) {
                    ObjectConfig config = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
                    scopingAttributes = new ArrayList<Object>();
                    for(String attribute :attributes) {
                        log.debug("Attribute: " + attribute);
                        ObjectAttribute objAttr = config.getObjectAttribute(attribute);
                        if(objAttr!=null)
                            scopingAttributes.add(objAttr);
                    }
                }
                List<String> appIds = Util.csvToList((String)def.getArgument(BusinessFunctionalRoleGenerator.ARG_APPLICATIONS));
                if(appIds!=null) {
                    applications = new ArrayList<String>();
                    for(String appId : appIds) {
                        Application app = getContext().getObjectById(Application.class, appId);
                        if(app!=null)
                            applications.add(appId);
                    }
                }
            }
        } catch(GeneralException ge) {
            log.error("Unable to load task definition: " + templateId + ". Exception: " + ge.getMessage());
        }
    }

    public String launchMining() {

        try {
            TaskManager tm = new TaskManager(getContext());           

            LocalizedDate lDate = new LocalizedDate(new Date(), DateFormat.FULL, DateFormat.LONG);
            String taskName = null;
            
            if(!Util.isNullOrEmpty(getTemplateName())) {
                taskName = templateName + " " + lDate.getLocalizedMessage(getLocale(), getUserTimeZone());
            } else {
                taskName = TASK_DEFINITION_NAME + " " + lDate.getLocalizedMessage(getLocale(), getUserTimeZone());
            }

            boolean isTaskRunning = tm.isTaskRunning(taskName, getMiningResultName()); 
            if (isTaskRunning) {
                getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.MINING_ALREADY_RUNNING, TASK_DEFINITION_NAME));
            } else {
                TaskSchedule ts = new TaskSchedule();
                ts.setName(taskName);   
                TaskDefinition def = null;
                if(!Util.isNullOrEmpty(templateId)) {
                    def = getContext().getObjectById(TaskDefinition.class, templateId);
                } else {
                    def = getContext().getObjectByName(TaskDefinition.class, TASK_DEFINITION_NAME);
                }
                ts.setArguments(getTaskArguments());
                ts.setTaskDefinition(def);
                ts.setLauncher(getLoggedInUserName()); 
    
                tm.runNow(ts);
                getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.BFR_MINING_SUCCESSFUL));
            }
        } catch (GeneralException e) {
            log.error("Failed to launch Business Functional Role Mining Task", e);
            getSessionScope().put(LAST_LAUNCH_RESULT, getMessage(MessageKeys.MINING_FAILED, TASK_DEFINITION_NAME));
        }
        
        return "";
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
    
    private boolean isUIDNaming() {
        return !(namingAlgorithm == null || namingAlgorithm.equals(NAMING_ALGORITHM_FILTER));
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
        if(!Util.isNullOrEmpty(getTemplateName()))
            return getTemplateName();        

        return TASK_DEFINITION_NAME;
    }
    
    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        //IIQTC-90 :-XSS vulnerability when adding a templateName
        this.templateName = WebUtil.escapeHTML(templateName,false);
    }

    public String getSaveResult() {
        return saveResult;
    }

    public void setSaveResult(String saveResult) {
        this.saveResult = saveResult;
    }
    
    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
}
