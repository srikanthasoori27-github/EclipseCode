/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Class used to schedule IT role mining tasks  
 *
 * @author Bernie Margolis
 */

package sailpoint.web.mining;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

import sailpoint.api.ManagedAttributer;
import sailpoint.object.Application;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.mining.ItRoleMiningTemplate.FilterType;
import sailpoint.web.util.WebUtil;

public class ITRoleMiningScheduleBean extends BaseBean {
    private static final Log log = LogFactory.getLog(ITRoleMiningScheduleBean.class);
    private static final String IT_ROLE_MINING_FILTER_BEAN = "itRoleMiningFilterBean";
    private static final String IT_ROLE_MINING_LAUNCH_SUCCESSFUL = "itRoleMiningLaunchSuccessful";
    private static final String IT_ROLE_MINING_LAUNCH_RESULTS_MSG = "itRoleMiningLaunchResultsMsg";
    private static final String IT_ROLE_MINING_TEMPLATE_ID = "templateId";
    private static final String IT_ROLE_MINING_TEMPLATE_ID_FORM_PARAM = "scheduleItRoleMiningForm:itRoleMiningTemplateId";
    private static final String IT_ROLE_MINING_TEMPLATE_MANAGER = "roleMiningTemplateManager";
    private static final String IT_ROLE_MINING_TEMPLATE = "roleMiningTemplate";
    private static final String IT_ROLE_MINING_TEMPLATE_SAVE_STATUS = "itRoleMiningTemplateSaveStatus";
    private static final String IT_ROLE_MINING_TEMPLATE_SAVE_MESSAGE = "itRoleMiningTemplateSaveMessage";
    
    private static final String NO_ENTITLEMENTS = "{entitlements: []}";
    private static final int DEFAULT_MIN_IDENTITIES = 0;
    private static final int DEFAULT_MIN_ENTITLEMENTS = 0;
    private static final int DEFAULT_MAX_CANDIDATE_ROLES = 1000;
    
    private static final String RESPONSE_ERROR = "error";
    private static final String RESPONSE_SUCCESS = "success";
    
    private String launchResultsMsg;
    private boolean launchSuccessful;
    private ITRoleMiningAttributeFilterBean attributeFilterBean;
    private String templateId;
    private ItRoleMiningTemplate template;
    private String appToRemove;
    private String originalTemplateName;

    public ITRoleMiningScheduleBean() {
        this.launchResultsMsg = (String) getSessionScope().get(IT_ROLE_MINING_LAUNCH_RESULTS_MSG);
        this.launchSuccessful = Util.otob(getSessionScope().get(IT_ROLE_MINING_LAUNCH_SUCCESSFUL));
        /* Fetch requested role mining template id from request parameters */
        templateId = getRequestParameter( IT_ROLE_MINING_TEMPLATE_ID );
        /* We can't wait for JSF to initialize this because we need it to fetch the template */
        if (templateId == null) {
            templateId = getRequestParameter(IT_ROLE_MINING_TEMPLATE_ID_FORM_PARAM);
        }
        
        /* Get the requested template */
        initItRoleMiningTemplate();
        this.attributeFilterBean = (ITRoleMiningAttributeFilterBean) getSessionScope().get(IT_ROLE_MINING_FILTER_BEAN);
        if (this.attributeFilterBean == null) {
            this.attributeFilterBean = new ITRoleMiningAttributeFilterBean(template);
            getSessionScope().put( IT_ROLE_MINING_FILTER_BEAN, attributeFilterBean );
        }
        
        originalTemplateName = template.getName();
    }
    
    public String getOriginalTemplateName() { 
        return originalTemplateName; 
    }
    
    public void setOriginalTemplateName(String originalTemplateName) { 
        this.originalTemplateName = originalTemplateName; 
    }
    
    public String getAppToRemove() {
        return appToRemove;
    }

    /**
     * The app multisuggest sets this value when an application is removed
     * @param appToRemove
     */
    public void setAppToRemove(String appToRemove) {
        this.appToRemove = appToRemove;
    }
    
    /**
     * Saves an IT Role Mining template
     * @return
     */
    public String saveTemplate() {
        String response = RESPONSE_SUCCESS;
        String message = "";
        /* last minute set up the filters for saving */
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        setIdentityFilters( template );
        try {
            getItRoleMiningTemplateManager().saveTemplate( template );
        } catch ( GeneralException e ) {
            message = e.getMessage();
            response = RESPONSE_ERROR;
        }
        getSessionScope().put( IT_ROLE_MINING_TEMPLATE_SAVE_MESSAGE, message );
        getSessionScope().put( IT_ROLE_MINING_TEMPLATE_SAVE_STATUS, response );
        return response;
    }

    private void setIdentityFilters( ItRoleMiningTemplate template ) {
        template.setIdentityFilters(getIdentityFilter().getFilters());
    }
    
    public String getSaveStatus() {
        return (String)getSessionScope().get( IT_ROLE_MINING_TEMPLATE_SAVE_STATUS );
    }
    
    public void setSaveStatus( String status ) {};
    
    public String getSaveMessage() {
        return (String)getSessionScope().get( IT_ROLE_MINING_TEMPLATE_SAVE_MESSAGE );
    }
    public void setSaveMessage( String message ) {}
    
    public String getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId( String id ) {
        this.templateId = id;
    }
    
    public String getTemplateName() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getName();
    }
    
    public void setTemplateName(String name) {
        if( name == null ) {
            return;
        }
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        template.setName( name );
    }
    
    public String getOwner() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getOwnerId();
    }
    
    public void setOwner(String owner) {
        if( owner == null ) {
            return;
        }
        getItRoleMiningTemplate().setOwnerId( owner );
    }
    
    public String getOwnerName() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getOwnerName();
    }
    
    public void setOwnerName(String ownerName) {
        if( ownerName == null ) {
            return;
        }
        getItRoleMiningTemplate().setOwnerName( ownerName );
    }
    
    public String getDefaultOwnerName() {
        String defaultOwnerName = null;
        try {
            defaultOwnerName = getLoggedInUser().getDisplayName();
        } catch (GeneralException e) {
            try {
                defaultOwnerName = getLoggedInUserName();
            } catch (GeneralException e1) {
                log.error("The IT Role Mining scheduler couldn't find a default owner", e);
            }
        }
        
        if (defaultOwnerName == null) {
            defaultOwnerName = "";
        }
        
        return defaultOwnerName; 
    }
    
    private String getDefaultOwnerId() {
        String id = null;
        try {
            id = getLoggedInUser().getId();
        } catch (GeneralException e) {
            log.error("The IT Role Mining scheduler couldn't find a default owner", e);
        }
        
        return id;
    }
    
    public String getPopulationFilterType() {
        String response = "searchByAttributes";
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        if( template.getFilterType().equals( FilterType.POPULATION ) ) {
            response = "searchByIpop";
        }
        return response;
    }
    
    public void setPopulationFilterType( String popFilterType ) {
        FilterType filterType = FilterType.ATTRIBUTE;
        if( !"searchByAttributes".equals( popFilterType ) ) {
            filterType = FilterType.POPULATION;
        }
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        template.setFilterType( filterType );
    }
    
    public String getIpopName() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getPopulationName();
    }
    
    public void setIpopName( String populationName ) {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        template.setPopulationName( populationName );
    }
    
    public int getMinIdentitiesPerCandidate() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getMinimumIdentitiesPerRole();
    }

    public void setMinIdentitiesPerCandidate(int minIdentitiesPerCandidate) {
        getItRoleMiningTemplate().setMinimumIdentitiesPerRole( minIdentitiesPerCandidate );
    }

    public int getMinEntitlementsPerCandidate() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getMinimumEntitlementsPerRole();
    }

    public void setMinEntitlementsPerCandidate(int minEntitlementsPerCandidate) {
        getItRoleMiningTemplate().setMinimumEntitlementsPerRole( minEntitlementsPerCandidate );
    }

    public int getMaxCandidateRoles() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return template.getMaximumRoles();
    }

    public void setMaxCandidateRoles(int maxCandidateRoles) {
        getItRoleMiningTemplate().setMaximumRoles( maxCandidateRoles );
    }

    public String getPopulationMsg() {
        return getMessage(MessageKeys.ROLE_MINING_POP_SIZE_MSG, getIdentityCount());      
    }
    
    public String getPopulationLimitMsg() {
        return getMessage(MessageKeys.ROLE_MINING_POP_LIMIT_MSG);
    }
    
    public ITRoleMiningAttributeFilterBean getFilterBean() {
        return attributeFilterBean;
    }
    
    public String getApplications() {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        return Util.listToCsv( template.getApplicationIds() );
    }
    
    public void setApplications( String applications ) {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        List<String> oldApplicationIds = template.getApplicationIds();
        List<String> newApplicationIds = Util.csvToList( applications );
        /* Util.csvToList( String ) return null instead of an empty List if String is empty ... */
        if( newApplicationIds == null ) {
            newApplicationIds = new ArrayList<String>( 0 );
        }
        /* Add all the new applications */
        for( String applicationId : newApplicationIds ) {
            if( !oldApplicationIds.contains( applicationId ) ) {
                addApplication( applicationId );
            } 
        }
        /* Remove any applications that were not present in new list */
        List<String> applicationsToRemove = new LinkedList<String>( oldApplicationIds );
        applicationsToRemove.removeAll( newApplicationIds );
        for( String applicationId : applicationsToRemove ) {
            removeApplication( applicationId );
        }
    }
    
    public String getApplicationsAsJson() {
        List<String> applications = getItRoleMiningTemplate().getApplicationIds();
        String appsAsJson;
        try {
            appsAsJson = WebUtil.basicJSONData(applications, "ApplicationListConverter");
        } catch (GeneralException e1) {
            log.error("Could not fetch apps with ids " + applications.toString());
            appsAsJson = "{}";
        }
        
        return appsAsJson;
    }
        
    /**
     * Returns the Entitlments/Permissions included in the current template
     * @return A JSON string containing a List of ITRoleMiningEntitlements 
     */
    public String getExcludedEntitlements() {
        String entitlementsJSON;
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        List<String> applications = getItRoleMiningTemplate().getApplicationIds();
        try {
            jsonWriter.object();
            jsonWriter.key("entitlements");
            jsonWriter.array();
            
            if (applications != null && !applications.isEmpty()) {
                for (String app : applications) {                    
                   try {
                       List<ManagedAttribute> entitlements = getManagedAttributes(getItRoleMiningTemplate().getExcludedEntitlementsForApplication( app ));
                       Application application = getContext().getObjectById(Application.class, app);
                       boolean hasEntitlements = !entitlements.isEmpty();
                       
                       if( hasEntitlements ) {
                           jsonWriter.object(); // Start application object
                           jsonWriter.key("applicationId");
                           jsonWriter.value(app);
                           jsonWriter.key("application");
                           jsonWriter.value(application.getName());
                           jsonWriter.key("entitlementAttributes");
        
                           jsonWriter.array(); // Start entitlement attributes
                           for( ManagedAttribute entitlement : entitlements ) {
                               jsonWriter.object();
                               jsonWriter.key("id");
                               jsonWriter.value(entitlement.getId());
                               jsonWriter.key("displayName");
                               jsonWriter.value(entitlement.getDisplayableName());
                               jsonWriter.endObject();
                           }
                           jsonWriter.endArray(); // End entitlement attributes
                                                      
                           jsonWriter.endObject(); // End application object
                       }
                    } catch (GeneralException e) {
                        log.error("Failed to fetch entitlements for application " + app, e);
                        entitlementsJSON = NO_ENTITLEMENTS;
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to fetch entitlements for application " + app, e);
                        entitlementsJSON = NO_ENTITLEMENTS;
                    }                
                }
            }

            jsonWriter.endArray();
            jsonWriter.endObject();
            
            entitlementsJSON = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate json for applications " + applications.toString(), e);
            entitlementsJSON = NO_ENTITLEMENTS;
        }
        
        return entitlementsJSON;
    }
    
    public void setExcludedEntitlements(String entitlementsJson) {
        Set<ITRoleMiningEntitlement> entitlements = new HashSet<ITRoleMiningEntitlement>();
        try {
            JSONTokener tokener = new JSONTokener(entitlementsJson);
            JSONObject jsonObj = (JSONObject) tokener.nextValue();
            JSONArray entitlementsObjs = jsonObj.getJSONArray("entitlements");
            for (int i = 0; i < entitlementsObjs.length(); ++i) {
                JSONObject entitlementsForApp = entitlementsObjs.getJSONObject(i);
                JSONArray entitlementAttributeObjs = entitlementsForApp.getJSONArray("entitlementAttributes");
                for (int j = 0; j < entitlementAttributeObjs.length(); ++j) {
                    String managedAttributeId = entitlementAttributeObjs.getJSONObject(j).getString("id");
                    ManagedAttribute attributeToExclude = getContext().getObjectById(ManagedAttribute.class, managedAttributeId);
                    entitlements.add(new ITRoleMiningEntitlement(attributeToExclude, getLocale()));
                }
            }
        } catch (JSONException e) {
            log.error("Failed to exclude entitlements due to malformed JSON", e);
        } catch (GeneralException e) {
            log.error("Failed to exclude entitlements due to a missing ManagedAttribute", e);
        }
        
        getItRoleMiningTemplate().setEntitlements(entitlements);
    }
    
    /**
     * Gets the results of launching the current template.  If it has been launched 
     * @return A JSON string describing the results of the last current task launch
     */
    public String getLaunchResults() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String launchResultsObj;
        
        try {
            jsonWriter.object();
            jsonWriter.key("launchResultMsg");
            jsonWriter.value(launchResultsMsg);
            jsonWriter.key("success");
            jsonWriter.value(launchSuccessful);
            jsonWriter.endObject();
            
            launchResultsObj = jsonString.toString();
        } catch (JSONException e) {
            log.debug("Failed to generate a JSON String for the results.  Going to dummy up a default");
            launchResultsObj = "{launchResultMsg: '', success: false}";
        }
        
        return launchResultsObj;
    }
    
    public String getDefaultValues() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String defaults;
        
        try {
            jsonWriter.object();
            jsonWriter.key("ownerName");
            jsonWriter.value(getDefaultOwnerName());
            jsonWriter.key("ownerId");
            jsonWriter.value(getDefaultOwnerId());
            jsonWriter.key("minIdentities");
            jsonWriter.value(DEFAULT_MIN_IDENTITIES);
            jsonWriter.key("minEntitlements");
            jsonWriter.value(DEFAULT_MIN_ENTITLEMENTS);
            jsonWriter.key("maxCandidateRoles");
            jsonWriter.value(DEFAULT_MAX_CANDIDATE_ROLES);
            jsonWriter.endObject();
            
            defaults = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate a JSON String for the results.  Going to dummy up a default", e);
            defaults = "{ownerName: '" + getDefaultOwnerName() + "', ownerId: '" + getDefaultOwnerId() + "', minEntitlements: " + DEFAULT_MIN_ENTITLEMENTS + ", minIdentities: " + DEFAULT_MIN_IDENTITIES + ", maxCandidateRoles: " + DEFAULT_MAX_CANDIDATE_ROLES + "}";
        }
        
        return defaults;
    }
    
    /**
     * Schedules an instance of the the currently loaded Template
     * @return nothing useful
     */
    public String scheduleItRoleMining() {
        /* last minute set up the filters for saving */
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        setIdentityFilters( template );
        try {
            getItRoleMiningTemplateManager().scheduleTemplate( getItRoleMiningTemplate() );
            launchResultsMsg = getMessage( MessageKeys.IT_ROLE_MINING_LAUNCHED_SUCCESSFULLY );
            launchSuccessful = true;
        } catch( GeneralException ex ) {
            log.error( "Could not run IT Role Mining Task", ex );
            launchResultsMsg = getMessage( MessageKeys.IT_ROLE_MINING_FAILED_TO_LAUNCH );
            launchSuccessful = false;
        }
        getSessionScope().put( IT_ROLE_MINING_LAUNCH_RESULTS_MSG, launchResultsMsg );
        getSessionScope().put( IT_ROLE_MINING_LAUNCH_SUCCESSFUL, launchSuccessful );
        
        return "";
    }
    
    /**
     * Removes session scoped variables
     * @return nothing useful
     */
    public String reset() {
        getSessionScope().remove( IT_ROLE_MINING_FILTER_BEAN );
        getSessionScope().remove( IT_ROLE_MINING_LAUNCH_RESULTS_MSG );
        getSessionScope().remove( IT_ROLE_MINING_LAUNCH_SUCCESSFUL );
        getSessionScope().remove( IT_ROLE_MINING_TEMPLATE );
        getSessionScope().remove( IT_ROLE_MINING_TEMPLATE_ID );
        getSessionScope().remove( IT_ROLE_MINING_TEMPLATE_SAVE_STATUS );
        
        return "";
    }
    
    public String getRights() {
        String rights;
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        
        // For now we just have one right: full access role mining
        try {
            jsonWriter.object();
            jsonWriter.key("fullAccessRoleMining");
            CapabilityManager capabilityMgr = getLoggedInUser().getCapabilityManager();
            Collection<String> effectiveRights = capabilityMgr.getEffectiveFlattenedRights();
            List<Capability> capabilities = capabilityMgr.getEffectiveCapabilities();
            boolean fullAccessRoleMining = 
                effectiveRights.contains(SPRight.FullAccessRoleMining) || Capability.hasSystemAdministrator(capabilities);
            jsonWriter.value(fullAccessRoleMining);
            jsonWriter.endObject();
            rights = jsonString.toString();
        } catch (GeneralException e) {
            log.error("Failed to get user rights for role mining", e);
            rights = "{fullAccessRoleMining: false}";
        } catch (JSONException e) {
            log.error("Failed to get user rights for role mining", e);
            rights = "{fullAccessRoleMining: false}";
        }
        
        return rights;
    }
    
    public String updateCount() {
        log.debug("ITRoleMining ScheduleBean count is " + getIdentityCount());
        return "";
    }

    /**
     * Returns an instance of ItRoleMiningTemplateManager for this request
     * @return An instance of ItRoleMiningTemplateManager.  Only valid for this request.
     */
    private ItRoleMiningTemplateManager getItRoleMiningTemplateManager() {
        ItRoleMiningTemplateManager templateManager = (ItRoleMiningTemplateManager)getRequestScope().get( IT_ROLE_MINING_TEMPLATE_MANAGER );
        if( templateManager == null ) {
            String loggedInUserId;
            try {
                loggedInUserId = getLoggedInUser().getId();
            } catch( GeneralException e ) {
                /* Not sure what to do in this case ... */
                loggedInUserId = "";
            }
            templateManager = new ItRoleMiningTemplateManager( getContext(), getLocale(), loggedInUserId );
            getRequestScope().put( IT_ROLE_MINING_TEMPLATE_MANAGER, templateManager );
        }
        return templateManager;
    }

    /*
     * Initializes the IT Role Mining Template.  This method should only be called once from the constructor
     */
    private void initItRoleMiningTemplate() {
        this.template = (ItRoleMiningTemplate)getSessionScope().get( IT_ROLE_MINING_TEMPLATE );
        // This is an anti-pattern because it trumps the request parameter.  Instead we move ID initialization to the
        // constructor so it is still occurs but can still be overridden by the form.
        // Keep the ID on the bean instead and defer to it.  --Bernie
        // String templateId = (String)getSessionScope().get( IT_ROLE_MINING_TEMPLATE_ID
        
        if( !Util.isNullOrEmpty( templateId ) ) {
            if( template == null || !template.getId().equals( templateId ) ) {
                try {
                    template = getItRoleMiningTemplateManager().getTemplate( templateId );
                } catch ( GeneralException e ) {
                    // Unable to get stored template 
                    throw new RuntimeException( e );
                }
                getSessionScope().put( IT_ROLE_MINING_TEMPLATE, template );
            }
        } else {
            template = new ItRoleMiningTemplate();
            try {
                template.setOwnerId(getLoggedInUser().getId());
                template.setOwnerName(getLoggedInUser().getDisplayableName());
            } catch (GeneralException e) {
                log.error("Could not determine the name of the logged in user.");
            }
            getSessionScope().put( IT_ROLE_MINING_TEMPLATE, template );
        }
    }
    
    /**
     * Returns the template we are working on
     * @return The current loaded ItRoleMiningTemplate 
     */
    private ItRoleMiningTemplate getItRoleMiningTemplate() {
        return template;
    }
    
    /**
     * Adds the Application with the specified Id to the template
     * @param applicationId The id of the Application to add
     */
    private void addApplication( String applicationId ) {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        template.addApplication( applicationId );
    }
    
    /**
     * Removes the specified application and it's entitlemetns and permissions from the template
     * @param applicationId The id of the Application to remove
     */
    private void removeApplication( String applicationId ) {
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        template.removeApplication( applicationId );
    }
    
    private int getIdentityCount() {
        QueryOptions identityFilter = getIdentityFilter();
        int identityCount;
        try {
            identityCount = getContext().countObjects(Identity.class, identityFilter);
        } catch (GeneralException e) {
            log.error("The IT Role Mining Scheduler failed to get a count of identities that are being mined.", e);
            identityCount = 0;
        }
        
        return identityCount;
    }
    
    private QueryOptions getIdentityFilter() {
        QueryOptions identityFilter = new QueryOptions();
        ItRoleMiningTemplate template = getItRoleMiningTemplate();
        
        if ( template.getFilterType().equals( ItRoleMiningTemplate.FilterType.ATTRIBUTE ) ) {
            if (this.attributeFilterBean != null) {
                Filter attributeFilter = this.attributeFilterBean.getFilter();
                if ( attributeFilter != null ) {
                    identityFilter.add( attributeFilter );
                }
            }
        } else {
            try {
                String populationName = template.getPopulationName();
                if ( populationName != null && populationName.trim().length() > 0) {
                    GroupDefinition ipop = getContext().getObjectByName( GroupDefinition.class, populationName );
                    if (ipop != null) {
                        identityFilter.add(ipop.getFilter());
                    }
                }
            } catch (GeneralException e) {
                log.error( "The IT Role Mining Scheduler could not find an ipop named " + template.getPopulationName() );
            }
        }
        List<String> applications = getItRoleMiningTemplate().getApplicationIds();
        if (applications != null && !applications.isEmpty()) {
            identityFilter.add(Filter.in("links.application.id", applications));
        }
        
        if (identityFilter != null) {
            identityFilter.setDistinct(true);
        }
        
        return identityFilter;
    }
    
    private List<ManagedAttribute> getManagedAttributes(Collection<ITRoleMiningEntitlement> entitlements) throws GeneralException {
        List<ManagedAttribute> managedAttributes = new ArrayList<ManagedAttribute>();
        
        if (entitlements != null && !entitlements.isEmpty()) {
            for (ITRoleMiningEntitlement entitlement : entitlements) {
                ITRoleMiningEntitlement.Type roleMiningType = entitlement.getType();
                ManagedAttribute attributeToAdd = null;
                boolean permission = (roleMiningType != ITRoleMiningEntitlement.Type.Attribute);
                String name;
                if (permission) {
                    name = entitlement.getTarget();
                } else {
                    name = entitlement.getName();
                }

                attributeToAdd = ManagedAttributer.get(getContext(), entitlement.getApplication(), permission, name, entitlement.getValue());                    

                if (attributeToAdd != null) {
                    managedAttributes.add(attributeToAdd);
                }
            }
        }
        
        return managedAttributes;
    }
}
