/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.apponboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CorrelationModel;
import sailpoint.api.ObjectUtil;
import sailpoint.authorization.RapidSetupEnabledAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Workflow;
import sailpoint.rest.ApplicationService;
import sailpoint.rest.BaseResource;
import sailpoint.rest.dto.ListItemsDTO;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.ApplicationDTO;
import sailpoint.service.acceleratorpack.ApplicationOnboardApplicationAttributeListFilterContext;
import sailpoint.service.acceleratorpack.ApplicationOnboardApplicationAttributeSuggestListFilterContext;
import sailpoint.service.acceleratorpack.ApplicationOnboardIdentityAttributeListFilterContext;
import sailpoint.service.acceleratorpack.ApplicationOnboardDTO;
import sailpoint.service.acceleratorpack.ApplicationOnboardService;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleConfig;
import sailpoint.web.trigger.IdentityProcessingThresholdDTO;

@Path(Paths.APPLICATION_ONBOARD)
public class ApplicationOnboardResource extends BaseResource {

    private static final Log log = LogFactory.getLog(ApplicationOnboardResource.class);

    /**
     * Return an ApplicationOnboardDTO, include the ApplicationDTO
     * @param appId the id of the application for which to get an ApplicationDTO
     * @return the ApplicationOnboardDTO representing the current RapidSetup configuration for the app,
     * and other RapidSetup-related fields of the given application
     * @throws GeneralException persistence context
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Path("{appId}")
    @GET
    public ApplicationOnboardDTO getApplicationOnboard(@PathParam("appId") String appId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetup, SPRight.FullAccessRapidSetup),
                new RapidSetupEnabledAuthorizer());

        ApplicationOnboardDTO dto = new ApplicationOnboardDTO();
        boolean hasEditAccess = isAuthorized(new RightAuthorizer(SPRight.FullAccessRapidSetup));

        ApplicationService svc = new ApplicationService(getContext());
        ApplicationDTO appDto = svc.getApplicationDTO(getContext(), appId);
        dto.setApplicationDTO(appDto);
        dto.setEditable(hasEditAccess);

        // Return an Attributes populated with only the projection of
        // "applications.<application_name>" read from the full Attributes

        Configuration configObj = getContext().getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        if (configObj == null) {
            throw new ObjectNotFoundException(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        }
        // Get Global configuration attributes to check if Joiner, Mover, Leaver are enabled globally
        // This is required on the client-side to accordingly display the warnings to the user
        Attributes<String,Object> globalConfigAttrs = configObj.getAttributes();
        Map businessProcessConfig = new HashMap<String, Object>();
        Map joinerGlobalConfig = new HashMap<String, Object>();
        Map leaverGlobalConfig = new HashMap<String, Object>();
        Map moverGlobalConfig = new HashMap<String, Object>();
        Map moverCertParamsConfig = new HashMap<String, Object>();

        // Joiner global config
        joinerGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_ENABLED,
            RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, joiner, enabled"));
        joinerGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_JOINER_REQUIRE_CORRELATED,
                RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, joiner, requireCorrelated"));


        // Mover global config
        moverCertParamsConfig.put(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ENABLED,
            RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, mover, certificationParams, certificationEnabled"));
        moverGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_ENABLED,
            RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, mover, enabled"));
        moverGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_PARAMS, moverCertParamsConfig);
        moverGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_MOVER_REQUIRE_CORRELATED,
                RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, mover, requireCorrelated"));

        // Leaver global config
        leaverGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_ENABLED,
            RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, leaver, enabled"));
        leaverGlobalConfig.put(Configuration.RAPIDSETUP_CONFIG_LEAVER_REQUIRE_CORRELATED,
                RapidSetupConfigUtils.getBoolean(globalConfigAttrs, "businessProcesses, leaver, requireCorrelated"));


        businessProcessConfig.put(Configuration.RAPIDSETUP_CONFIG_JOINER, joinerGlobalConfig);
        businessProcessConfig.put(Configuration.RAPIDSETUP_CONFIG_MOVER, moverGlobalConfig);
        businessProcessConfig.put(Configuration.RAPIDSETUP_CONFIG_LEAVER, leaverGlobalConfig);

        Map appConfig = getApplicationConfig(configObj.getAttributes(), appDto.getName());

        Attributes<String,Object> configAttrs = new Attributes<>();
        Map partialAppsMap = new HashMap<String,Object>();
        configAttrs.put(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS, partialAppsMap);
        if (appConfig == null) {
            appConfig = new HashMap<String,Object>();
        }
        partialAppsMap.put(appDto.getName(), appConfig);

        configAttrs.put(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES, businessProcessConfig);
        dto.setConfigAttributes(configAttrs);

        ApplicationOnboardService service = new ApplicationOnboardService(this);
        service.enhanceAttributes(dto);
        service.enhanceFilters(dto, appId);

        return dto;
    }

    /**
     * Return an ApplicationOnboardDTO with the full current RapidSetup configuration,
     * with no ApplicationDTO
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("globalSettings")
    public ApplicationOnboardDTO getGlobalSettings() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetupConfiguration, SPRight.FullAccessRapidSetupConfiguration),
                new RapidSetupEnabledAuthorizer());

        ApplicationOnboardDTO dto = new ApplicationOnboardDTO();
        boolean hasEditAccess = isAuthorized(new RightAuthorizer(SPRight.FullAccessRapidSetupConfiguration));
        ApplicationOnboardService service = new ApplicationOnboardService(this);
        Configuration configObj = getContext().getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        if (configObj == null) {
            throw new ObjectNotFoundException(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        }

        Attributes<String,Object> configAttrs = configObj.getAttributes();
        if (!Util.isEmpty(configAttrs)) {
            // remove any application-specific config
            configAttrs.remove(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        }
        dto.setConfigAttributes(configAttrs);
        service.enhanceAttributes(dto);
        dto.setEditable(hasEditAccess);
        dto.setThresholdMap(service.getRapidSetupThresholdConfig());

        return dto;
    }

    /**
     * Update the RapidSetup Configuration object (except for "application")
     * @param data the ApplicationOnboardDTO to read the new configuration from
     * @throws GeneralException error loading or saving the Configuration object
     */
    @PUT
    @Path("globalSettings")
    public Response updateGlobalSettings(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetupConfiguration), new RapidSetupEnabledAuthorizer());

        // fetch the list of old RoleTypes that are RapidSetup Birthright for comparison later
        List<String> oldRoleTypes = RapidSetupConfigUtils.getRapidSetupBirthrightRoleTypeNames();

        String json = JsonHelper.toJson(data);
        ApplicationOnboardDTO appOnboardDTO = JsonHelper.fromJson(ApplicationOnboardDTO.class, json);
        ApplicationOnboardService service = new ApplicationOnboardService(this);
        service.scrubAttributes(appOnboardDTO);

        Attributes<String,Object> configAttributes = appOnboardDTO.getConfigAttributes();
        Map<String, IdentityProcessingThresholdDTO> thresholdConfig = appOnboardDTO.getThresholdMap();

        if (configAttributes != null) {
            Configuration cfg = getContext().getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
            if (cfg == null) {
                throw new ObjectNotFoundException(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
            }
            Map<String, List<Map<String, String>>> errors = validate(configAttributes, thresholdConfig);
            if (!Util.isEmpty(errors)) {
                return Response.status(Response.Status.BAD_REQUEST).entity(errors).build();
            }
            setGlobalSettings(cfg, configAttributes);
            getContext().saveObject(cfg);

            if(appOnboardDTO.getThresholdMap() != null) {
                Map<String, Object> busProcess = (Map) configAttributes
                        .get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
                for (String key : appOnboardDTO.getThresholdMap().keySet()) {
                    // update only in case the business process is enabled
                    if (isEnabled(busProcess, key)) {
                        updateThreshold(appOnboardDTO.getThresholdMap().get(key));
                    }
                }
            }

            getContext().commitTransaction();

            // compare the new RoleTypes to the old one and signal the CorrelationModel if they are different
            List<String> newRoleTypes = RapidSetupConfigUtils.getRapidSetupBirthrightRoleTypeNames();
            if (!Util.orderInsensitiveEquals(oldRoleTypes, newRoleTypes)) {
                CorrelationModel.setBirthrightRoleTypesChanged(true);
            }
        }

        return Response.ok().build();
    }

    /**
     * Updates the IdentityTrigger with the new Identity Threshold parameter
     * configuration.
     *
     * @param thresholdDTO
     *            IdentityProcessingThresholdDTO DTO object that contains the new threshold
     *            type and value.
     * @throws GeneralException
     */
    private void updateThreshold(IdentityProcessingThresholdDTO thresholdDTO) throws GeneralException {
        IdentityTrigger identityTrigger = getContext().getObjectById(IdentityTrigger.class, thresholdDTO.getId());
        identityTrigger.setIdentityProcessingThreshold(thresholdDTO.getIdentityProcessingThreshold());
        identityTrigger.setIdentityProcessingThresholdType(thresholdDTO.getIdentityProcessingThresholdType());
    }

    /**
     * Using the ApplicationOnboardDTO payload, update the RapidSetup configuration and application.
     * Ignore all keys in the incoming configAttributes unless they are for the given application.
     * Returns 400 if any user-facing errors are found, with error JSON detailing what needs to be fixed.
     * Returns 200 if successful.
     * @param data the jSON payload representing an ApplicationOnboardDTO
     * @return A Response object with any errors that occurred, or just a 200 status if successful.
     * @throws GeneralException persistence context
     */
    @PUT
    @Path("{appId}")
    public Response updateApplicationOnboard(Map<String, Object> data,
                                         @PathParam("appId") String appId) throws GeneralException {
        try {
            boolean needsCommit = false;
            String appName = null;

            String json = JsonHelper.toJson(data);
            ApplicationOnboardDTO appOnboardDTO = JsonHelper.fromJson(ApplicationOnboardDTO.class, json);

            // Validate the incoming DTO, and return errors if any issues are found.
            Map<String, List<String>> errors = validate(appOnboardDTO, appId);
            if (!errors.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(errors).build();
            }

            ApplicationDTO appDto = appOnboardDTO.getApplicationDTO();
            if (appDto != null) {
                authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup), new RapidSetupEnabledAuthorizer());
                appName = appDto.getName();
                ApplicationService svc = new ApplicationService(appId, this);
                // don't commit yet
                svc.updateApplication(appDto, false);
                needsCommit = true;
            }

            Attributes<String,Object> configAttributes = appOnboardDTO.getConfigAttributes();
            if (configAttributes != null) {
                // TODO what SPRight to check here?
                authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup), new RapidSetupEnabledAuthorizer());

                if (appName == null) {
                    appName = ObjectUtil.getName(getContext(), Application.class, appId);
                    if (appName == null) {
                        throw new ObjectNotFoundException(Application.class, appId);
                    }
                }

                Map appConfig = getApplicationConfig(configAttributes, appName);
                Configuration cfg = getContext().getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
                if (cfg == null) {
                    throw new ObjectNotFoundException(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
                }
                setApplicationConfig(cfg, appConfig, appName);

                ApplicationOnboardService service = new ApplicationOnboardService(this);
                service.scrubAttributes(appOnboardDTO);
                service.scrubFilterValues(appOnboardDTO, appId);

                getContext().saveObject(cfg);
                needsCommit = true;
            }

            if (needsCommit) {
                getContext().commitTransaction();
            }
        } catch(Exception e) {
            log.warn("Unable to Update Onboarding data", e);
            throw e;
        }

        return Response.ok().build();
    }

    /**
     * Gets a list of Application Attributes for the given Application ID (as filters).
     * Used in the application onboarding aggregation panel.
     * @return A list of available filters for Application Attributes
     * @throws GeneralException persistence context
     */
    @Path("applicationAttributeFilters/{appId}")
    @GET
    public List<ListFilterDTO> getApplicationAttributeFilters(@PathParam("appId") String appId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetup, SPRight.FullAccessRapidSetup));
        ApplicationOnboardApplicationAttributeListFilterContext applicationAttributeFilterContext =
                new ApplicationOnboardApplicationAttributeListFilterContext(appId);
        String suggestUrl = getMatchedUri().replace("applicationAttributeFilters", "applicationAttributeFiltersSuggest");
        applicationAttributeFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), applicationAttributeFilterContext).getListFilters(true);
    }

    /**
     * Gets a list of Application Attributes for the given Application ID (as filters).
     * This variation returns the filters with suggests for the column values.
     * Used in the application onboarding leaver panel.
     * @return A list of available filters for Application Attributes
     * @throws GeneralException persistence context
     */
    @Path("applicationAttributeSuggestFilters/{appId}")
    @GET
    public List<ListFilterDTO> getApplicationAttributeSuggestFilters(@PathParam("appId") String appId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetup, SPRight.FullAccessRapidSetup));
        ApplicationOnboardApplicationAttributeSuggestListFilterContext applicationAttributeFilterContext =
                new ApplicationOnboardApplicationAttributeSuggestListFilterContext(appId);
        String suggestUrl = getMatchedUri().replace("applicationAttributeSuggestFilters", "applicationAttributeSuggestFiltersSuggest");
        applicationAttributeFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), applicationAttributeFilterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the application attribute filters,
     * allows only the filters in {@link #getApplicationAttributeSuggestFilters(String)}
     * @throws GeneralException persistence context
     */
    @Path("applicationAttributeFiltersSuggest/{appId}")
    public SuggestResource getApplicationAttributeFiltersSuggestResource(@PathParam("appId") String appId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getApplicationAttributeFilters(appId)));
    }

    /**
     * Pass through to suggest resource for the application attribute filters,
     * allows only the filters in {@link #getApplicationAttributeFilters(String)}
     * @throws GeneralException persistence context
     */
    @Path("applicationAttributeSuggestFiltersSuggest/{appId}")
    public SuggestResource getApplicationAttributeSuggestFiltersSuggestResource(@PathParam("appId") String appId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup));
        return new ApplicationOnboardSuggestResource(appId,this,  new BaseSuggestAuthorizerContext(getApplicationAttributeSuggestFilters(appId)));
    }

    /**
     * Gets a list of Identity Extended Attributes (as filters).
     * Used in the application onboarding aggregation panel.
     * @return A list of available filters for Identity Extended Attributes
     * @throws GeneralException persistence context
     */
    @Path("identityAttributeFilters")
    @GET
    public List<ListFilterDTO> getIdentityAttributeFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetup, SPRight.FullAccessRapidSetup));
        ApplicationOnboardIdentityAttributeListFilterContext identityAttributeFilterContext =
                new ApplicationOnboardIdentityAttributeListFilterContext();
        String suggestUrl = getMatchedUri().replace("identityAttributeFilters", "identityAttributeFiltersSuggest");
        identityAttributeFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), identityAttributeFilterContext).getListFilters();
    }

    /**
     * Pass through to suggest resource for the identity attribute filters,
     * allows only the filters in {@link #getIdentityAttributeFilters()}
     * @throws GeneralException persistence context
     */
    @Path("identityAttributeFiltersSuggest")
    public SuggestResource getIdentityAttributeFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getIdentityAttributeFilters()));
    }

    /**
     * Gets a list of Ownable SailPoint Object Major Classes.
     * Used in the application onboarding global settings leaver tab.
     * @return A list of ownable SailPoint Object Class names
     * @throws GeneralException persistence context
     */
    @Path("ownableArtifactTypes")
    @GET
    public ListItemsDTO<String> getOwnableSailPointObjectClasses() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup, SPRight.FullAccessRapidSetupConfiguration,
                SPRight.ViewRapidSetup, SPRight.ViewRapidSetupConfiguration));

        ListItemsDTO<String> ownableArtifactTypes = new ListItemsDTO<>();
        ApplicationOnboardService.REASSIGNABLE_ARTIFACT_TYPES.entrySet().stream()
                .map(entry -> new ListItemsDTO.ListItemDTO<>(entry.getKey().getSimpleName(), entry.getValue()))
                .forEach(ownableArtifactTypes::addItem);

        return ownableArtifactTypes.sortItemsByName();
    }

    /**
     * Gets a list of Role Types used to select which role types are Birthright roles.
     * Used in the application onboarding global settings misc tab.
     * The returned list are only RoleTypeDefinitions that are noAutoAssignment is true
     * @return A list of role types
     * @throws GeneralException persistence context
     */
    @Path("roleTypes")
    @GET
    public List getRoleTypes() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetupConfiguration, SPRight.ViewRapidSetupConfiguration));
        List roleTypeNames = new ArrayList();
        RoleConfig rc = new RoleConfig();
        List<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
        for (RoleTypeDefinition type : types) {
            if (type.isNoAutoAssignment() && !type.isNoAssignmentSelector()) {
                roleTypeNames.add(type.getName());
            }
        }
        return roleTypeNames;
    }

    /**
     * Get the basic suggest resource for application onboarding.
     * @return SuggestResource.
     * @throws GeneralException persistence context
     */
    @Path("suggest")
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRapidSetup, SPRight.FullAccessRapidSetup,
                SPRight.FullAccessRapidSetupConfiguration));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
                .add(Application.class.getSimpleName())
                .add(Rule.class.getSimpleName())
                .add(EmailTemplate.class.getSimpleName())
                .add(GroupDefinition.class.getSimpleName())
                .add((Workflow.class.getSimpleName()));
        return new SuggestResource(this, authorizerContext);
    }

    //////////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////////

    /**
     * Get the Map value from the key  "applications"/{appName} of the given Attributes
     * @param cfgAttrs the Attributes to look in
     * @param appName the name of the key to find
     * @return the found Map, otherwise null if isn't present
     */
    private Map getApplicationConfig(Attributes<String,Object> cfgAttrs, String appName) {
        Map appConfig = null;
        Map appsMap = (Map)cfgAttrs.get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        if (appsMap != null) {
            appConfig = (Map)appsMap.get(appName);
        }
        return appConfig;
    }

    /**
     * Set the appConfig Map as the value for the  "applications"/{appName} of the Configuration.
     * Add missing nodes to not yet present.
     * @param cfg the Configuration object to update
     * @param appConfig the new Map value for the "applications"/{appName}  node
     * @param appName the key to place the Map under
     */
    private void setApplicationConfig(Configuration cfg, Map<String,Object> appConfig, String appName) {
        Attributes<String,Object> cfgAttrs = cfg.getAttributes();
        if (cfgAttrs == null) {
            cfgAttrs = new Attributes<String,Object>();
            cfg.setAttributes(cfgAttrs);
        }
        Map<String,Object> appsMap = (Map)cfgAttrs.get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        if (appsMap == null) {
            appsMap = new HashMap<String,Object>();
            cfgAttrs.put(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS, appsMap);
        }
        appsMap.put(appName, appConfig);
    }

    /**
     * Update the Configuration object using the keys in the configAttributes.  However,
     * leave the "applications" key alone in the Configuration
     * @param cfg the Configuration to update
     * @param configAttributes the attributes to copy from
     */
    private void setGlobalSettings(Configuration cfg,  Attributes<String,Object> configAttributes ) {

        // strip "applications" out of configAttributes
        configAttributes.remove(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);

        Attributes<String,Object> originalAttributes = cfg.getAttributes();
        if (!Util.isEmpty(originalAttributes)) {
            if (originalAttributes.containsKey(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS)) {
                // overlay apps from cfg into configAttributes
                Map<String,Object> apps = (Map)originalAttributes.get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
                configAttributes.put(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS, apps);
            }
        }

        // replace cfg with configAttributes
        cfg.setAttributes(configAttributes);
    }

    /**
     * Validate that the incoming DTO meets some basic requirements.
     * @param onboardDTO The DTO to validate.
     * @param appId The application ID that this DTO is for.
     * @return Map that assigns each error to the attribute it is associated with. Will be empty if no errors.
     * @throws GeneralException if there is a conflict between the appId, and the id in the ApplicationDTO
     */
    private Map<String, List<String>> validate(ApplicationOnboardDTO onboardDTO, String appId) throws GeneralException {
        Map<String, List<String>> errors = new HashMap<>();
        ApplicationDTO applicationDTO = onboardDTO.getApplicationDTO();

        if (!Util.isEmpty(applicationDTO.getId()) && !applicationDTO.getId().equals(appId)) {
            throw new GeneralException("Application ids don't match. Please check the application id passed.");
        }

        ListFilterValue identityCorrelationFilter = applicationDTO.getIdentityCorrelationFilter();
        if (identityCorrelationFilter != null) {
            if (!isFilterComplete(identityCorrelationFilter)) {
                errors.put("identityCorrelationFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_IDENTITY_CORR_INCOMPLETE));
            }
        }

        ListFilterValue managerCorrelationFilter = applicationDTO.getManagerCorrelationFilter();
        if (managerCorrelationFilter != null) {
            if (!isFilterComplete(managerCorrelationFilter)) {
                errors.put("managerCorrelationFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_MANAGER_CORR_INCOMPLETE));
            }
        }

        List<ListFilterValue> disableFilters = applicationDTO.getDisableAccountFilter();
        for (ListFilterValue filter : Util.safeIterable(disableFilters)) {
            if (!isFilterComplete(filter)) {
                errors.put("disableAccountFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_DISABLE_INCOMPLETE));
                break;
            }
        }

        List<ListFilterValue> serviceFilters = applicationDTO.getServiceAccountFilter();
        for (ListFilterValue filter : Util.safeIterable(serviceFilters)) {
            if (!isFilterComplete(filter)) {
                errors.put("serviceAccountFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_SERVICE_ACCOUNT_INCOMPLETE));
                break;
            }
        }

        List<ListFilterValue> rpaFilters = applicationDTO.getRpaAccountFilter();
        for (ListFilterValue filter : Util.safeIterable(rpaFilters)) {
            if (!isFilterComplete(filter)) {
                errors.put("rpaAccountFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_RPA_ACCOUNT_INCOMPLETE));
                break;
            }
        }

        List<ListFilterValue> lockFilters = applicationDTO.getLockAccountFilter();
        for (ListFilterValue filter : Util.safeIterable(lockFilters)) {
            if (!isFilterComplete(filter)) {
                errors.put("lockAccountFilter", Arrays.asList(MessageKeys.UI_APP_ONBOARD_AGGREGATION_LOCK_INCOMPLETE));
                break;
            }
        }

        String appName = ObjectUtil.getName(getContext(), Application.class, appId);
        if (appName != null) {
            Attributes<String,Object> configAttributes = onboardDTO.getConfigAttributes();
            Map appConfig = getApplicationConfig(configAttributes, appName);
            RapidSetupConfigUtils.validateApplicationConfig(getContext(), errors, appConfig, applicationDTO);
        }

        return errors;
    }

    /**
     * Validate that the current businessProcesses has at least one item.
     * @param busProcess a Map with all the config attributes to be validated
     * @param businessProcesses current BP to validate, it could be MLJ
     * @return Boolean
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean hasIdentityTriggerItems(Map<String, Object> busProcess, String businessProcesses) {
        Stack<String> stack = new Stack<String>();
        stack.push(Configuration.RAPIDSETUP_CONFIG_PARAM_ITEMS);
        stack.push(Configuration.RAPIDSETUP_CONFIG_PARAM_GROUP);
        stack.push(Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_FILTER);
        stack.push(businessProcesses);

        try {
            Map<String, Object> busProcessTem = busProcess;
            while(!stack.isEmpty()) {
                if (stack.size() > 1) {
                    busProcessTem = (Map)busProcessTem.get(stack.pop());
                } else {
                    List list = (List)busProcessTem.get(stack.pop());
                    if (list.size() > 0) {
                        return true;
                    }
                }
                if (busProcessTem == null) {
                    break;
                }
            }
        } catch(Exception e) {
            //Do nothing
            //If we have arrive here, it means that the busProcess is missing something
            //or has something additional.
            //The intention of adding this try/catch is to return back false, which will
            //cause to return back the "Identity Trigger is required"
            //There is a possibility to have some ClassCastException or perhaps another
            //kind of exception in either way the validation should return false.
            log.warn("You are entering invalid data to validate Identity Trigger", e);
        }
        return false;
    }

    /**
     * Validate that the incoming global config meets some basic requirements.
     * For the returned map, the key is the config section and the list is the
     * list of error maps.
     *
     * @param globalConfig
     *            The global config attributes to validate.
     * @param thresholdConfig
     *            Map that contains the Identity Processing Threshold
     *            configuration for each Rapid Setup workflow.
     * @return Map that assigns each error to the config section it is
     *         associated with. Will be empty if no errors.
     */
    private Map<String, List<Map<String, String>>> validate(Attributes<String, Object> globalConfig,
            Map<String, IdentityProcessingThresholdDTO> thresholdConfig) throws GeneralException {
        Map<String, List<Map<String, String>>> errors = new HashMap<>();
        Map<String, Object> busProcess = (Map)globalConfig.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);

        // Joiner
        if (busProcess != null && busProcess.containsKey(Configuration.RAPIDSETUP_CONFIG_JOINER)) {
            // No need to validate if it's not enabled.
            if (isEnabled(busProcess, Configuration.RAPIDSETUP_CONFIG_JOINER)) {
                validateJoinerConfig(busProcess, thresholdConfig, errors);
            }
        }

        // Mover
        if (busProcess != null && busProcess.containsKey(Configuration.RAPIDSETUP_CONFIG_MOVER)) {
            // No need to validate if it's not enabled.
            if (isEnabled(busProcess, Configuration.RAPIDSETUP_CONFIG_MOVER)) {
                validateMoverConfig(busProcess, thresholdConfig, errors);
            }
        }

        // Leaver
        if (busProcess != null && busProcess.containsKey(Configuration.RAPIDSETUP_CONFIG_LEAVER)) {
            // No need to validate if it's not enabled.
            if (isEnabled(busProcess, Configuration.RAPIDSETUP_CONFIG_LEAVER)) {
                validateLeaverConfig(busProcess, thresholdConfig, Configuration.RAPIDSETUP_CONFIG_LEAVER, errors);
            }
        }

        // Terminate (Identity Operations)
        if (busProcess != null && busProcess.containsKey(Configuration.RAPIDSETUP_CONFIG_TERMINATE)) {
            // No need to validate if it's not enabled.
            if (isEnabled(busProcess, Configuration.RAPIDSETUP_CONFIG_TERMINATE)) {
                validateLeaverConfig(busProcess, null, Configuration.RAPIDSETUP_CONFIG_TERMINATE, errors);
            }
        }

        // Misc
        validateMiscConfig(globalConfig, errors);

        return errors;
    }

    /**
     * Validate the joiner config and add all errors to the passed in param, "errors".
     * @param busProcess
     *            Rapid Setup configuration node that contains the configuration
     *            for each workflow.
     * @param thresholdConfig
     *            Map that contains Identity Processing Threshold configuration
     *            for each Rapid Setup workflow.
     * @param errors
     *            The list of errors to populate if any are found.
     * @throws GeneralException
     */
    private void validateJoinerConfig(Map<String, Object> busProcess, Map<String, IdentityProcessingThresholdDTO> thresholdConfig,
         Map<String, List<Map<String, String>>> errors) throws GeneralException {

        List<Map<String, String>> joinerErrors = new ArrayList<Map<String, String>>();
        Map<String, Object> joinerConfig = (Map)busProcess.get(Configuration.RAPIDSETUP_CONFIG_JOINER);

        validateField(joinerConfig, joinerErrors, Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW,
                Configuration.RAPIDSETUP_CONFIG_JOINER_WORKFLOW_SUGGEST, Workflow.class,
                "ui_app_onboard_settings_workflow_error",
                "ui_app_onboard_joiner_settings_error_joiner_workflow_bad", true);

        if (joinerConfig.containsKey(Configuration.RAPIDSETUP_CONFIG_JOINER_EMAIL)) {
            Map<String, Object> emails = (Map) joinerConfig.get(Configuration.RAPIDSETUP_CONFIG_JOINER_EMAIL);
            validateField(emails, joinerErrors, Configuration.RAPIDSETUP_CONFIG_JOINER_COMPLETED_EMAIL_TEMPLATE,
                    Configuration.RAPIDSETUP_CONFIG_JOINER_COMPLETED_EMAIL_TEMPLATE_SUGGEST, EmailTemplate.class,
                    "ui_app_onboard_joiner_settings_error_joiner_completed_email_template",
                    "ui_app_onboard_joiner_settings_error_joiner_completed_email_bad", true);

            validateField(emails, joinerErrors, Configuration.RAPIDSETUP_CONFIG_JOINER_ALT_NOTIFY_WORKGROUP,
                    Configuration.RAPIDSETUP_CONFIG_JOINER_ALT_NOTIFY_WORKGROUP, Identity.class, "",
                    "ui_app_onboard_joiner_settings_error_alt_notify_workgroup_bad", false);
        }

        validateField(joinerConfig, joinerErrors, Configuration.RAPIDSETUP_CONFIG_JOINER_POST_JOINER_RULE,
                Configuration.RAPIDSETUP_CONFIG_JOINER_POST_JOINER_RULE_SUGGEST, Rule.class, "",
                "ui_app_onboard_joiner_settings_error_post_joiner_rule_bad", false);

        validateThreshold(thresholdConfig, Configuration.RAPIDSETUP_CONFIG_JOINER, joinerErrors);

        if (!joinerErrors.isEmpty()) {
            errors.put(Configuration.RAPIDSETUP_CONFIG_JOINER, joinerErrors);
        }
    }

    /**
     * Validate the mover config and add all errors to the passed in param,
     * "errors".
     *
     * @param busProcess
     *            Rapid Setup configuration node that contains the configuration
     *            for each workflow.
     * @param thresholdConfig
     *            Map that contains Identity Processing Threshold configuration
     *            for each Rapid Setup workflow.
     * @param errors
     *            The list of errors to populate if any are found.
     * @throws GeneralException
     */
    private void validateMoverConfig(Map<String, Object> busProcess, Map<String, IdentityProcessingThresholdDTO> thresholdConfig,
            Map<String, List<Map<String, String>>> errors) throws GeneralException {

        Map<String, Object> moverConfig = (Map)busProcess.get(Configuration.RAPIDSETUP_CONFIG_MOVER);
        List<Map<String, String>> moverErrors = new ArrayList<Map<String, String>>();

        validateField(moverConfig, moverErrors, Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW,
                Configuration.RAPIDSETUP_CONFIG_MOVER_WORKFLOW_SUGGEST, Workflow.class,
                "ui_app_onboard_settings_workflow_error",
                "ui_app_onboard_mover_settings_error_mover_workflow_bad", true);

        validateField(moverConfig, moverErrors, Configuration.RAPIDSETUP_CONFIG_MOVER_POST_MOVER_RULE,
                Configuration.RAPIDSETUP_CONFIG_MOVER_POST_MOVER_RULE_SUGGEST, Rule.class, "",
                "ui_app_onboard_mover_settings_error_post_mover_rule_bad", false);

        if (moverConfig.containsKey(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_PARAMS)) {
            Map<String, Object> certParams = (Map) moverConfig.get(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_PARAMS);
            boolean enableCert = false;
            if (certParams.containsKey(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ENABLED)) {
                enableCert = (boolean) certParams.get(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ENABLED);
            }
            if (enableCert) {
                validateField(certParams, moverErrors, Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_OWNER,
                        Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_OWNER, Identity.class,
                        "ui_app_onboard_mover_settings_cert_owner_required",
                        "ui_app_onboard_mover_settings_cert_owner_bad", true);

                validateField(certParams, moverErrors, Configuration.RAPIDSETUP_CONFIG_MOVER_BACKUP_CERTIFIER,
                        Configuration.RAPIDSETUP_CONFIG_MOVER_BACKUP_CERTIFIER, Identity.class,
                        "ui_app_onboard_mover_settings_backup_certifier_required",
                        "ui_app_onboard_mover_settings_backup_cert_bad", true);
            }
        }
        //Making sure that mover has at least one element in the identity trigger
        if (!hasIdentityTriggerItems(busProcess, Configuration.RAPIDSETUP_CONFIG_MOVER)) {
            addTabError(moverErrors, Configuration.RAPIDSETUP_CONFIG_PARAM_ITEMS,
                    "ui_app_onboard_settings_identity_trigger_error");
        }

        validateThreshold(thresholdConfig, Configuration.RAPIDSETUP_CONFIG_MOVER, moverErrors);

        if (!moverErrors.isEmpty()) {
            errors.put(Configuration.RAPIDSETUP_CONFIG_MOVER, moverErrors);
        }
    }

    /**
     * Validate the leaver config and add all errors to the passed in param,
     * "errors". This is also used to validate terminate.
     *
     * @param busProcess
     *            The map of business process configurations.
     * @param thresholdConfig
     *            Map that contains Identity Processing Threshold configuration
     *            for each Rapid Setup workflow.
     * @param process
     *            Which process are we validating? e.g "leaver" or "terminate".
     * @param errors
     *            The list of errors to populate if any are found.
     * @throws GeneralException
     */
    private void validateLeaverConfig(Map<String, Object> busProcess, Map<String, IdentityProcessingThresholdDTO> thresholdConfig,
        String process, Map<String, List<Map<String, String>>> errors) throws GeneralException {
        Map<String, Object> leaverConfig = (Map)busProcess.get(process);

        List<Map<String, String>> leaverErrors = new ArrayList<Map<String, String>>();

        validateField(leaverConfig, leaverErrors, Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW,
                Configuration.RAPIDSETUP_CONFIG_LEAVER_WORKFLOW_SUGGEST, Workflow.class,
                "ui_app_onboard_settings_workflow_error",
                "ui_app_onboard_leaver_settings_error_workflow_bad", true);

        //reassignArtifacts
        Map<String, Object> reassignArtifacts = (Map) leaverConfig.get(Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ARTIFACTS);
        boolean enableReassignment = false;
        if (reassignArtifacts.containsKey(Configuration.RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_ARTIFACTS)) {
            enableReassignment = (boolean) reassignArtifacts.get(Configuration.RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_ARTIFACTS);
        }
        // only need to check if reassignment is enabled
        if (enableReassignment) {
            validateField(reassignArtifacts, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALTERNATIVE,
                    Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALT_ARTIFACTS_SUGGEST, Identity.class,
                    "ui_app_onboard_leaver_settings_error_reassign_artifacts_alt_not_defined",
                    "ui_app_onboard_leaver_settings_error_reassign_artifacts_alt_bad", true);
        }

        //reassignIdentities
        Map<String, Object> reassignIdentities = (Map) leaverConfig.get(Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_IDENTITIES);
        boolean enableIdentityReassignment = false;
        if (reassignIdentities.containsKey(Configuration.RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_IDENTITIES)) {
            enableIdentityReassignment = (boolean) reassignIdentities.get(Configuration.RAPIDSETUP_CONFIG_LEAVER_ENABLE_REASSIGN_IDENTITIES);
        }

        if (enableIdentityReassignment) {
            validateField(reassignIdentities, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALTERNATIVE,
                    Configuration.RAPIDSETUP_CONFIG_LEAVER_REASSIGN_ALT_IDENTITIES_SUGGEST, Identity.class,
                    "ui_app_onboard_leaver_settings_error_reassign_identities_alt_not_defined",
                    "ui_app_onboard_leaver_settings_error_reassign_identities_alt_bad", true);
        }

        //email
        if (leaverConfig.containsKey(Configuration.RAPIDSETUP_CONFIG_JOINER_EMAIL)) {
            Map<String, Object> emails = (Map) leaverConfig.get(Configuration.RAPIDSETUP_CONFIG_JOINER_EMAIL);
            validateField(emails, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_COMPLETED,
                    Configuration.RAPIDSETUP_CONFIG_LEAVER_COMPLETED_SUGGEST, EmailTemplate.class, "",
                    "ui_app_onboard_leaver_settings_error_leaver_completed_email_bad", false);

            validateField(emails, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_ALT_NOTIFY_WORKGROUP,
                    Configuration.RAPIDSETUP_CONFIG_LEAVER_ALT_NOTIFY_WORKGROUP_SUGGEST, Identity.class, "",
                    "ui_app_onboard_leaver_settings_error_alt_notify_workgroup_bad", false);

            validateField(emails, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_OWNERSHIP_REASSIGNMENT,
                    Configuration.RAPIDSETUP_CONFIG_LEAVER_OWNERSHIP_REASSIGNMENT_SUGGEST, EmailTemplate.class, "",
                    "ui_app_onboard_leaver_settings_error_ownership_reassign_email_bad", false);
        }

        validateField(leaverConfig, leaverErrors, Configuration.RAPIDSETUP_CONFIG_LEAVER_POST_RULE,
                Configuration.RAPIDSETUP_CONFIG_LEAVER_POST_RULE_SUGGEST, Rule.class, "",
                "ui_app_onboard_leaver_settings_error_post_rule_bad", false);

        //Making sure that leaver has at least one element in the identity trigger
        if (Configuration.RAPIDSETUP_CONFIG_LEAVER.equalsIgnoreCase(process)) {
            if (!hasIdentityTriggerItems(busProcess, Configuration.RAPIDSETUP_CONFIG_LEAVER)) {
                addTabError(leaverErrors, Configuration.RAPIDSETUP_CONFIG_PARAM_ITEMS,
                        "ui_app_onboard_settings_identity_trigger_error");
            }
        }

        validateThreshold(thresholdConfig, process, leaverErrors);

        if (!leaverErrors.isEmpty()) {
            errors.put(process, leaverErrors);
        }

    }

    /**
     * Validate the misc config and add all errors to the passed in param, "errors".
     * @param globalConfig
     * @param errors
     */
    private void validateMiscConfig(Attributes<String, Object> globalConfig, Map<String, List<Map<String, String>>> errors)
            throws GeneralException {
        List<Map<String, String>> miscErrors = new ArrayList<Map<String, String>>();
        Map<String, Object> workflowConfig = (Map)globalConfig.get(Configuration.RAPIDSETUP_CONFIG_SECTION_WORKFLOW);

        validateField(workflowConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_WORKFLOW_REQUESTER,
                Configuration.RAPIDSETUP_CONFIG_WORKFLOW_REQUESTER, Identity.class,
                "ui_app_onboard_settings_requester",
                "ui_app_onboard_misc_settings_requester_bad", true);

        Map<String, Object> emailConfig = (Map)globalConfig.get(Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL);
        if (emailConfig == null) {
            addTabError(miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL, "ui_app_onboard_settings_email");
        }
        validateField(emailConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_ALT_MANAGER,
                Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_ALT_MANAGER, Identity.class,
                "ui_app_onboard_settings_no_manager_email",
                "ui_app_onboard_misc_settings_alt_notify_workgroup_bad", true);

        validateField(emailConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_ERROR_WORKGROUP,
                Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_ERROR_WORKGROUP, Identity.class,
                "ui_app_onboard_settings_error_workgroup",
                "ui_app_onboard_misc_settings_err_notify_workgroup_bad", true);

        validateField(emailConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_STYLE_SHEET,
                Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_STYLE_SHEET_SUGGEST, EmailTemplate.class,
                "ui_app_onboard_settings_email_stylesheet",
                "ui_app_onboard_misc_settings_stylesheet_email_bad", true);

        validateField(emailConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_HEADER,
                Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_HEADER_SUGGEST, EmailTemplate.class,
                "ui_app_onboard_settings_email_header",
                "ui_app_onboard_misc_settings_header_email_bad", true);

        validateField(emailConfig, miscErrors, Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_FOOTER,
                Configuration.RAPIDSETUP_CONFIG_SECTION_EMAIL_FOOTER_SUGGEST, EmailTemplate.class,
                "ui_app_onboard_settings_email_footer",
                "ui_app_onboard_misc_settings_footer_email_bad", true);

        if (!miscErrors.isEmpty()) {
            // Misc errors don't belong to a specific section, so key is empty
            errors.put("", miscErrors);
        }

    }

    /**
     * Validate the field.  If problems, add the errors to the passed in errors map list.
     * @param config
     * @param errors
     * @param fieldKey
     * @param suggestId
     * @param cls
     * @param reqMsg
     * @param errMsg
     * @param required
     * @throws GeneralException
     */
    private <T extends SailPointObject> void validateField(Map<String, Object> config, List<Map<String, String>> errors,
            String fieldKey, String suggestId, Class<T> cls, String reqMsg, String errMsg, boolean required)
            throws GeneralException {

        String value = (String) config.get(fieldKey);

        if (required) {
            if (!config.containsKey(fieldKey) || Util.isNullOrEmpty(value)) {
                addTabError(errors, suggestId, reqMsg);
            }
        }

        // required or not, check if the value is valid.
        if (config.containsKey(fieldKey)) {
            if (Util.isNotNullOrEmpty(value) && getContext().getObjectByName(cls, value) == null) {
                addTabError(errors, suggestId, errMsg);
            }
        }
    }

    /**
     * Validates the values set up values for Identity Processing Threshold are
     * appropriated.
     *
     * @param thresholdConfig
     *            Map that contains the Identity Processing Threshold
     *            configuration for each Rapid Setup workflow.
     * @param errors
     *            Map that contains the errors to be presented to the user in
     *            case they are found.
     */
    private void validateThreshold(Map<String, IdentityProcessingThresholdDTO> thresholdConfig, String process,
            List<Map<String, String>> errors) {
        IdentityProcessingThresholdDTO processThresholdConfig = thresholdConfig.get(process);
        if (processThresholdConfig == null) {
            addTabError(errors, "ui_identity_trigger_threshold_type",  "ui_err_identity_trigger_threshold_missing_lifecycle_event");
        } else if (!Util.isNullOrEmpty(processThresholdConfig.getIdentityProcessingThreshold())) {
            // If Threshold is set, must have a threshold type
            if (Util.isNullOrEmpty(processThresholdConfig.getIdentityProcessingThresholdType())) {
                addTabError(errors, "ui_identity_trigger_threshold_type",  "ui_err_identity_trigger_threshold_missing_type");
            }
            // If Threshold is set, it must be numeric
            if (!Util.isNumeric(processThresholdConfig.getIdentityProcessingThreshold())) {
                addTabError(errors, "ui_identity_trigger_threshold", "ui_err_identity_trigger_threshold_not_numeric");
                // Threshold must be positive value
            } else {
                float threshold = Util.atof(processThresholdConfig.getIdentityProcessingThreshold());
                // Threshold must be positive value
                if (threshold < 0) {
                    addTabError(errors, "ui_identity_trigger_threshold", "ui_err_identity_trigger_threshold_negative");

                    // If Threshold Type is 'percentage', Threshold must be less
                    // than or equal to 100
                } else if (Util.nullSafeCaseInsensitiveEq(processThresholdConfig.getIdentityProcessingThresholdType(),
                        "percentage") && threshold > 100) {
                    addTabError(errors, "ui_identity_trigger_threshold", "ui_err_identity_trigger_threshold_too_high");
                }
            }
        }
    }

    /**
     * Add an error to the list of errors for a specific tab (i.e. - joiner, mover, leaver)
     * The keys for this map must stay as-is since the html uses those to display the error.
     * @param tabErrors the list of errors for a tab
     * @param attr the suggest id of the field with the error
     * @param msg the error message
     */
    private void addTabError (List<Map<String, String>> tabErrors, String attr, String msg) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("attr", attr);
        errorMap.put("msg", msg);
        tabErrors.add(errorMap);
    }

    /**
     * Check whether the specified filter is complete.
     * @param filter The ListFilterValue to check.
     * @return True if the filter is complete.
     */
    private boolean isFilterComplete(ListFilterValue filter) {
        return !Util.isNullOrEmpty(filter.getProperty()) && !Util.isNullOrEmpty((String)filter.getValue())
                && filter.getOperation() != null;
    }

    private boolean isEnabled(Map<String, Object> attributes, String configSection) {
        boolean enabled = false;

        if (attributes != null && attributes.containsKey(configSection)) {
            Map<String, Object> section = (Map)attributes.get(configSection);
            enabled = Util.otob(section.get(Configuration.RAPIDSETUP_CONFIG_ENABLED));
        }

        return enabled;
    }
}
