/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.rapidsetup.model.IdentSelectorDTO;
import sailpoint.rapidsetup.plan.AbstractLeaverAppConfigProvider;
import sailpoint.rapidsetup.plan.DefaultLeaverAppConfigProvider;
import sailpoint.rapidsetup.plan.LeaverAppConfigProvider;
import sailpoint.api.SailPointContext;
import sailpoint.service.ApplicationDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RapidSetupConfigUtils {


    public static boolean isEnabled() {
        return Configuration.getSystemConfig().getBoolean(Configuration.RAPIDSETUP_ENABLED);
    }

    /**
     *
     * @param trigger the trigger to get the filter for
     * @return the map of the configuration for the trigger filter for the
     * given RapidSetup identity trigger
     * @throws GeneralException
     */
    public static Map getMatchFilter(IdentityTrigger trigger) throws GeneralException {
        Map triggerFilter = null;

        String businessProcessName = trigger.getMatchProcess();
        if (Util.isNotNullOrEmpty(businessProcessName)) {
            triggerFilter = getMatchFilter(businessProcessName);
        }
        else {
            throw new GeneralException("Trigger " + trigger.getName() + " has no RapidSetup business process declared");
        }
        return triggerFilter;
    }

    /**
     *
     * @param process the process to get the trigger filter for
     * @return the map of the configuration for the trigger filter for the
     * RapidSetup identity trigger whose process is given by process
     * @throws GeneralException
     */
    public static Map getMatchFilter(String process) throws GeneralException {
        Map triggerFilter = null;

        if (Util.isNotNullOrEmpty(process)) {
            Map businessProcessConfigSection = (Map) getRapidSetupBusinessProcessConfigurationSection();
            if (businessProcessConfigSection != null) {
                Map  bizProc = (Map) businessProcessConfigSection.get(process);
                if (!Util.isEmpty(bizProc)) {
                    triggerFilter = (Map)bizProc.get(Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_FILTER);
                }
                else {
                    throw new GeneralException("No configuration found for RapidSetup business process '" + process + "'");
                }
            }
            else {
                throw new GeneralException("No business processes key found in RapidSetup configuration");
            }
        }
        else {
            throw new GeneralException("Unexpected empty trigger process");
        }
        return triggerFilter;
    }

    public static Map getRapidSetupBusinessProcessConfigurationSection() {
        return (Map) get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
    }

    /**
     * @return true if approvals should be generated when the given process
     * (e.g. mover. leaver, joiner) is running.
     */
    public static boolean shouldGenerateApprovals(String process) {
        Map processConfig = getRapidSetupBusinessProcessConfiguration(process);
        boolean doApprovals = getBoolean(processConfig, "generateApprovals");
        return doApprovals;
    }

    /**
     * Leaver has the option of whether or not to require identities to be correlated.
     * Check the global option requireCorrelated.
     * 
     * @return true if requiredCorrelated is true.
     */
    public static boolean isRequireCorrelatedForLeaver() {
        Map leaverConfig = getRapidSetupBusinessProcessConfiguration(Configuration.RAPIDSETUP_CONFIG_LEAVER);
        return getBoolean(leaverConfig, Configuration.RAPIDSETUP_CONFIG_LEAVER_REQUIRE_CORRELATED);
    }

    public static Map getRapidSetupBusinessProcessConfiguration(String businessProcessName) {
        if(Util.isNotNullOrEmpty(businessProcessName)) {
            Map businessProcessConfigSection = (Map) getRapidSetupBusinessProcessConfigurationSection();
            if (businessProcessConfigSection != null) {
                return (Map) businessProcessConfigSection.get(businessProcessName);
            }
        }

        return Collections.emptyMap();
    }

    /**
     * Returns a list of application names for applications that mover certifications includes additional entitlements for the
     * given business process name.
     * @param businessProcessName - the business process name used to lookup the configuration.
     * @return a list of application names configured that mover certifications includes additional entitlements for the given business process.
     */
    public static List<String> getApplicationsConfiguredIncludeEntitlements(String businessProcessName) {
        if(Util.isNotNullOrEmpty(businessProcessName)) {
            Map<String,Object> appsMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
            if(appsMap != null) {
                return appsMap.keySet().stream().
                        filter(appName -> additionalEntitlementsEnabledForApplication(businessProcessName, appName)).
                        collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Determine if the application has any RapidSetup configuration defined for it.
     * @param appName the name of the application to check
     * @return true if the given application curently has RapidSetup config set for it.
     * Otherwise, false.
     */
    public static boolean isApplicationConfigured(String appName) {
        boolean isConfigured = false;
        if (Util.isNotNullOrEmpty(appName)) {
            Map<String,Object> appsMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
            if(appsMap != null) {
                isConfigured = appsMap.containsKey(appName);
            }
        }
        return isConfigured;
    }

    /**
     * Returns a list of application names for applications that mover certifications include target permission for the
     * given business process name.
     * @param businessProcessName - the business process name used to lookup the configuration.
     * @return a list of application names configured that mover certifications include target permission for the given business process.
     */
    public static List<String> getApplicationsConfiguredTargetPermission(String businessProcessName) {
        if(Util.isNotNullOrEmpty(businessProcessName)) {
            Map<String,Object> appsMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
            if(appsMap != null) {
                return appsMap.keySet().stream().
                        filter(appName -> targetPermissionEnabledForApplication(businessProcessName, appName)).
                        collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Gets the certification parameters from the RapidSetup configuration object.
     * @param businessProcessName the business process name to get the configuration for.
     * @return a map containing the RapidSetup certification parameters for the given business process.
     */
    public static Map getRapidSetupCertificationParams(String businessProcessName) {
        if(Util.isNotNullOrEmpty(businessProcessName)) {
            Map moverConfigSection = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfiguration(Configuration.RAPIDSETUP_CONFIG_MOVER);
            if(moverConfigSection != null) {
                return (Map<String, Object>) Util.get(moverConfigSection, Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_PARAMS);
            }
        }

        return Collections.emptyMap();
    }

    /**
     * Remove the application from the applications section of RapidSetup Configuration object
     * @param cfg the RapidSetup Configuration object passed in from Terminator
     * @param appName the application to remove
     */
    public static void removeApplication(Configuration cfg, String appName) {
        if (cfg != null) {
            Map<String,Object> appsMap = (Map<String,Object>)cfg.get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
            if (appsMap != null) {
                appsMap.remove(appName);
            }
        }
    }

    /**
     * Get the value (Map) for the given application from RapidSetup Configuration from
     * "applications"/"{appname}"
     * @param appName
     * @return the applications config data, or null if no config for the application
     */
    public static Map<String,Object> getApplicationConfig(String appName) {
        Map<String,Object> appConfig = null;
        Map<String,Object> appsMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        if (!Util.isEmpty(appsMap)) {
            appConfig = (Map<String,Object>)appsMap.get(appName);
        }
        return appConfig;
    }

    /**
     * Validate the entire incoming application config hierarchy
     * @param context persistence context
     * @param errs the list of errors to send back to UI
     * @param appConfig the application config to be validated
     */
    public static void validateApplicationConfig(SailPointContext context, Map<String, List<String>> errs, Map appConfig, ApplicationDTO applicationDTO)  {
        if (appConfig == null) {
            return;
        }
        validateApplicationJoinerIdentSelectorDTO(context, errs, appConfig);
        validateLeaverOptions(errs, appConfig, applicationDTO);
    }

    /**
     * Get the value (Map) for the application's specific business process config from
     * "applications"/{appname}/"businessProcesses"/"{businessprocess}"
     * @param appName
     * @param businessProcess
     * @return the application's business process config it all keys present, otherwise null
     */
    public static Map<String,Object> getApplicationBusinessProcessConfig(String appName, String businessProcess) {
        Map<String,Object> bizProcConfig = null;

        Map<String,Object> appMap = getApplicationConfig(appName);
        if (!Util.isEmpty(appMap)) {
            Map<String,Object> appBizProcs = (Map<String,Object>)appMap.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
            if (appBizProcs != null) {
                bizProcConfig = (Map<String,Object>) appBizProcs.get(businessProcess);
            }
        }
        return bizProcConfig;
    }

    /**
     * Get the optional IdentitySelector form the application's joiner config "identitySelector" key
     * "applications"/{appname}/"businessProcesses"/"joiner"/"identitySelector"
     * @param appName
     * @return the IdentitySelector is all required keys are present, otherwise null
     */
    public static IdentitySelector getApplicationJoinerIdentitySelector(String appName) {
        IdentitySelector selector = null;

        Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, Configuration.RAPIDSETUP_CONFIG_JOINER);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR);
            if (obj instanceof IdentitySelector) {
                selector = (IdentitySelector)obj;
            }
        }
        return selector;
    }

    /**
     * Returns true if the given application has mover_joiner enabled for the given business process.
     * @param businessProcess the business process name.
     * @param appName the application name.
     * @return true if the application is enabled for mover_joiner for the business process, or false if not.
     */
    public static boolean moverJoinerEnabledForApplication(String businessProcess, String appName) {
        boolean performJoiner = false;

        Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, businessProcess);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_MOVER_PERFORM_JOINER);
            performJoiner = Util.otob(obj);
        }
        return performJoiner;
    }

    /**
     * Returns true if the given application mover certification includes additional entitlements
     * @param businessProcess the business process name.
     * @param appName the application name.
     * @return true if the application mover certification includes additional entitlements
     */
    public static boolean additionalEntitlementsEnabledForApplication(String businessProcess, String appName) {
        boolean includeAdditionalEntitlements = false;

        Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, businessProcess);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_ADDITIONAL_ENTITLEMENTS);
            includeAdditionalEntitlements = Util.otob(obj);
        }
        return includeAdditionalEntitlements;
    }

    /**
     * Returns true if the given application mover certification includes target permission
     * @param businessProcess the business process name.
     * @param appName the application name.
     * @return true if the application mover certification includes target permission
     */
    public static boolean targetPermissionEnabledForApplication(String businessProcess, String appName) {
        boolean includeTargetPermission = false;

        Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, businessProcess);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_MOVER_CERTIFICATION_TARGET_PERMISSION);
            includeTargetPermission = Util.otob(obj);
        }
        return includeTargetPermission;
    }
    
    /**
     * Get the optional IdentitySelector form the application's joiner config "identitySelector" key
     * "applications"/{appname}/"businessProcesses"/"joiner"/"identSelectorDTO"
     * @param appName
     * @return the IdentitySelector if all required keys are present, otherwise null
     */

    public static IdentSelectorDTO getApplicationJoinerIdentSelectorDTO(SailPointContext context, String appName) throws GeneralException {
        IdentSelectorDTO selector = null;

        Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, Configuration.RAPIDSETUP_CONFIG_JOINER);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO);
            if (obj instanceof Map) {
                Map<String,Object> identSelectorMap = (Map<String,Object>)obj;
                selector = new IdentSelectorDTO(context, identSelectorMap);
            }
        }
        return selector;
    }

    /**
     * Validate the (optional) identitySelectorDTO value under the application config
     * @param context persistence context
     * @param errs the errors to send back to the UI
     * @param appConfig the application config to look into
     */
    private static void validateApplicationJoinerIdentSelectorDTO(SailPointContext context, Map<String, List<String>> errs,
                                                                  Map appConfig) {
        if (appConfig == null) {
            return;
        }
        Map<String,Object> appBizProcs = (Map<String,Object>)appConfig.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
        if (appBizProcs == null) {
            return;
        }
        Map<String,Object> bizProc = (Map<String,Object>)appBizProcs.get(Configuration.RAPIDSETUP_CONFIG_JOINER);
        if (bizProc != null) {
            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO);
            if (obj == null) {
                return;
            }
            else {
                if (obj instanceof Map) {
                    Map<String,Object> identSelectorMap = (Map<String,Object>)obj;
                    try {
                        // here we finally validate the DTO
                        IdentSelectorDTO.validateMap(context, identSelectorMap);
                    }
                    catch (Exception e) {
                        errs.put(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO,
                                Arrays.asList(e.getMessage()));
                    }
                }
                else {
                    errs.put(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO,
                            Arrays.asList("Expected Map for identSelectorDTO, but found " + obj.getClass().getSimpleName()));
                }
            }
        }
    }

    /**
     * Validate the required sections of the leaver options are configured correctly.
     * @param errs the errors to send back to the UI
     * @param appConfig the application config to look into
     * @param applicationDTO DTO representation of the application the config is set on.
     */
    private static void validateLeaverOptions(Map<String, List<String>> errs, Map appConfig, ApplicationDTO applicationDTO) {
        if (appConfig == null) {
            return;
        }
        Map<String, Object> appBizProcs = (Map<String, Object>) appConfig.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
        if (appBizProcs == null) {
            return;
        }

        Map<String, Object> leaverSection = (Map<String, Object>) appBizProcs.get(Configuration.RAPIDSETUP_CONFIG_LEAVER);
        Map<String, Object> leaverRule = (Map)leaverSection.get(AbstractLeaverAppConfigProvider.OPT_PLAN_RULE);
        if (isUseRule(leaverSection)) {
            if (Util.isEmpty(leaverRule)) {
                String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_MISSING_PLAN_RULE).toString();
                errs.put(AbstractLeaverAppConfigProvider.OPT_PLAN_RULE, Arrays.asList(err));
                }
            // If a useRule is true then any configs will be ignored so skip the validation.
            return;
        }
        // the leaver configuration contains multiple configs.  One for the normal case, and one for
        // the breakglass terminate identity case.  Perhaps it will contain population specific configs
        // at some point also.  Need to validate each one.
        Map<String, Object> leaverConfigs = getLeaverConfigSections(leaverSection, errs);
        boolean useDefaultForBreakGlass = useDefaultForBreakGlass(leaverSection);
        validateApplicationLeaverDaysToDelay(errs, leaverConfigs);
        for (String key : leaverConfigs.keySet()) {
            if (AbstractLeaverAppConfigProvider.OPT_LEAVER_CONFIG_BREAKGLASS.equals(key) && useDefaultForBreakGlass) {
                // Don't bother validating the break glass section if it is configured to use the normal section
                break;
            }
            Map<String, Object> leaverConfig = (Map<String, Object>)leaverConfigs.get(key);

            if (isLeaverOptionEnabled(LeaverAppConfigProvider.OPT_SCRAMBLE_PASSWORD, leaverConfig)) {
                boolean passwordAttrRequired = !applicationDTO.getFeatures().contains(Application.Feature.PASSWORD.toString());
                if (passwordAttrRequired &&
                        Util.isNullOrEmpty((String)leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_PASSWORD_ATTR))) {
                    String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_MISSING_PASSWORD_ATTRIBUTE).toString();
                    errs.put(AbstractLeaverAppConfigProvider.OPT_SCRAMBLE_PASSWORD, Arrays.asList(err));
                }
            }

            if (isLeaverOptionEnabled(LeaverAppConfigProvider.OPT_MOVE_ACCOUNT, leaverConfig)) {
                if (Util.isNullOrEmpty((String)leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_MOVE_OU))) {
                    String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_MISSING_MOVE_ACCOUNT_CONTAINER).toString();
                    errs.put(AbstractLeaverAppConfigProvider.OPT_MOVE_OU, Arrays.asList(err));
                }
            }

            if (isLeaverOptionEnabled(LeaverAppConfigProvider.OPT_ADD_COMMENT, leaverConfig)) {
                if (Util.isNullOrEmpty((String)leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_COMMENT_ATTR))) {
                    String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_MISSING_COMMENT_ATTRIBUTE).toString();
                    errs.put(AbstractLeaverAppConfigProvider.OPT_COMMENT_ATTR, Arrays.asList(err));
                }
                if (Util.isNullOrEmpty((String)leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_COMMENT_STRING))) {
                    String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_MISSING_COMMENT).toString();
                    errs.put(AbstractLeaverAppConfigProvider.OPT_COMMENT_STRING, Arrays.asList(err));
                }
            }

            if (isLeaverOptionEnabled(LeaverAppConfigProvider.OPT_REMOVE_ENTITLEMENTS, leaverConfig)) {
                List<Map<String, Object>> entitlementExceptions = (List) leaverConfig.get(AbstractLeaverAppConfigProvider.OPT_ENTITLEMENT_EXCPTN);
                for (Map entitlementException : Util.safeIterable(entitlementExceptions)) {
                    ListFilterValue filter = new ListFilterValue(entitlementException);
                    if (Util.isNullOrEmpty(filter.getProperty()) || null == filter.getOperation() ||
                            null == filter.getValue() ||
                            (filter.getValue() instanceof List && Util.isEmpty((List) filter.getValue())) ||
                            (filter.getValue() instanceof String && Util.isNullOrEmpty((String) filter.getValue()))) {
                        String err = new Message(MessageKeys.UI_APP_ONBOARD_LEAVER_ENTITLEMENT_EXCEPTION_INCOMPLETE).toString();
                        errs.put(AbstractLeaverAppConfigProvider.OPT_ENTITLEMENT_EXCPTN, Arrays.asList(err));
                    }
                }
            }
        }
    }

    /**
     * Helper to check if a Leaver option is enabled. If its value is immediate or later we consider it to be enabled.
     * @param option
     * @param configMap
     * @return
     */
    private static boolean isLeaverOptionEnabled(String option, Map<String,Object> configMap) {
        String optionValue = (String)configMap.get(option);
        if (Util.isNotNullOrEmpty(optionValue) &&
                (LeaverAppConfigProvider.OPT_MODE_IMMEDIATE.equals(optionValue) || LeaverAppConfigProvider.OPT_MODE_LATER.equals(optionValue))) {
            return true;
        }
        return false;
    }

    /**
     * Validate the (optional) DaysToDelay value under the application config
     * Also validate the (optional) DaysToDelay value when paired with the (optional) DeleteAccountDelayDays
     *  The validation for this covers the case when a user wants to delay both the deletion of an account along with the
     *  other leaver configuration options (like disable account).  This validation confirms the deletion of the account
     *  happens after the other options.
     * @param errs the errors to send back to the UI
     * @param leaverConfigs the application leaver configs to look into
     */
    @SuppressWarnings("unchecked")
	private static void validateApplicationLeaverDaysToDelay(Map<String, List<String>> errs, Map<String, Object> leaverConfigs) {
        for (String key : leaverConfigs.keySet()) {
            Map<String, Object> leaverConfig = (Map<String, Object>)leaverConfigs.get(key);
            try {
                // we need to validate all the providers, if one provider is turn on
                //it means that DaysToDelay and/or deleteAccountDelayDays property should
                //be present with a valid value.
                Map<String, String> providers = new HashMap<String, String>();
                providers = notDeleteAccountProviders();
                // First check the list of providers that are NOT the OPT_DELETE_ACCT_DELAY to see if any are delayed
                Boolean nonDeleteAccountOptionDelayed = false;
                for (Map.Entry<String, String> provider : providers.entrySet()) {
                    String keyValue = leaverConfig.get(provider.getKey()) != null ?
                            leaverConfig.get(provider.getKey()).toString() : null;
                    if (Util.nullSafeCaseInsensitiveEq(keyValue, LeaverAppConfigProvider.OPT_MODE_LATER)) {
                        nonDeleteAccountOptionDelayed = true;
                        break;
                    }
                }
                // now add in the OPT_DELETE_ACCOUNT to validate that it and the others have valid numbers
                providers.put(LeaverAppConfigProvider.OPT_DELETE_ACCOUNT, DefaultLeaverAppConfigProvider.OPT_DELETE_ACCT_DELAY);

                for (Map.Entry<String, String> provider : providers.entrySet()) {
                    String keyValue = leaverConfig.get(provider.getKey()) != null ?
                            leaverConfig.get(provider.getKey()).toString() : null;
                    if (Util.nullSafeCaseInsensitiveEq(keyValue, LeaverAppConfigProvider.OPT_MODE_LATER)) {
                        Object num = leaverConfig.get(provider.getValue());
                        if (num == null || Integer.parseInt(num.toString()) <= 0) {
                            errs.put(provider.getValue(),
                                    Arrays.asList("Expected a valid number for " + provider.getValue()));
                        }
                        break;
                    }
                }
                // now check that if both are delayed, their values are staggered appropriately.
                if (nonDeleteAccountOptionDelayed) {
                    // if DeleteAccount is delayed it must be greater than the delay for the other options
                    String deleteAccountValue = leaverConfig.get(LeaverAppConfigProvider.OPT_DELETE_ACCOUNT) != null ?
                            leaverConfig.get(LeaverAppConfigProvider.OPT_DELETE_ACCOUNT).toString() : null;
                    if (Util.nullSafeCaseInsensitiveEq(deleteAccountValue, LeaverAppConfigProvider.OPT_MODE_LATER)) {
                        // At this point we know that both the DeleteAccount and some other option is delayed.
                        // Verify both are valid numbers and the DeleteAccount happens after (greater number than) the other delay.
                        Object numDeleteAccountDelay = leaverConfig.get(DefaultLeaverAppConfigProvider.OPT_DELETE_ACCT_DELAY);
                        Object numOtherDelay = leaverConfig.get(DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
                        // verify delete account greater than entitlement delay
                        if (numDeleteAccountDelay == null || numOtherDelay == null ||
                                Integer.parseInt(numDeleteAccountDelay.toString()) <= Integer.parseInt(numOtherDelay.toString())) {
                            errs.put(DefaultLeaverAppConfigProvider.OPT_DELETE_ACCT_DELAY,
                                    Arrays.asList("Expected a number for " + DefaultLeaverAppConfigProvider.OPT_DELETE_ACCT_DELAY
                                            + " that is greater than " + DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY));
                        }
                    }
                }
            } catch (Exception e) {
                errs.put(DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY,
                        Arrays.asList(e.getMessage()));
            }
        }
    }

    /**
     * Convenience Method to return the map of LeaverAppConfigProviders that are not the Delete Account option
     * @return Map of leaver options as strings that are mapped to their corresponding delay string
     */
    private static Map<String, String> notDeleteAccountProviders (){
        Map<String, String> providers = new HashMap<String, String>();
        providers.put(LeaverAppConfigProvider.OPT_REMOVE_ENTITLEMENTS, DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
        providers.put(LeaverAppConfigProvider.OPT_SCRAMBLE_PASSWORD, DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
        providers.put(LeaverAppConfigProvider.OPT_ADD_COMMENT, DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
        providers.put(LeaverAppConfigProvider.OPT_DISABLE_ACCOUNT, DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
        providers.put(LeaverAppConfigProvider.OPT_MOVE_ACCOUNT, DefaultLeaverAppConfigProvider.OPT_ENTITLEMENT_DELAY);
        return providers;
    }

    private static Map<String, Object> getLeaverConfigSections(Map<String, Object> leaver,
                                                               Map<String, List<String>> errs) {
        Map<String, Object> leaverConfigSections = new HashMap<String, Object>();
        if (leaver != null) {
            if (leaver instanceof Map) {
                Object leaverConfigsObject = leaver.get(DefaultLeaverAppConfigProvider.OPT_LEAVER_CONFIGS_SECTION);
                if ((leaverConfigsObject != null) && (leaverConfigsObject instanceof Map)) {
                    leaverConfigSections.putAll((Map<String, Object>) leaverConfigsObject);
                }
            } else {
                errs.put(Configuration.RAPIDSETUP_CONFIG_JOINER_PROV_SELECTOR_DTO,
                        Arrays.asList("Expected Map for leaver, but found " + leaver.getClass().getSimpleName()));
            }
        }
        return leaverConfigSections;
    }

    private static boolean useDefaultForBreakGlass (Map<String, Object> leaver) {
        if (leaver != null) {
            Boolean useDefaultForBreakGlass = (Boolean)leaver.get(AbstractLeaverAppConfigProvider.OPT_USE_DEFAULT_FOR_BREAKGLASS);
            return useDefaultForBreakGlass != null ? useDefaultForBreakGlass.booleanValue() : false;
        }
        return false;
    }

    private static boolean isUseRule (Map<String, Object> leaver) {
        if (leaver != null) {
            Boolean isUseRule = (Boolean)leaver.get(AbstractLeaverAppConfigProvider.OPT_USE_RULE);
            return isUseRule != null ? isUseRule.booleanValue() : false;
        }
        return false;
    }

    /**
     *
     * @return the ids of the applications that have enableBareProvisioning=true set in the joiner config
     */
    public static List<String> getJoinerProvisioningAppNames() {
        List<String> appIds = new ArrayList<String>();
        Map<String,Object> appsMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_SECTION_APPS);
        if (!Util.isEmpty(appsMap)) {
            Set<Map.Entry<String,Object>> entrySet =  appsMap.entrySet();
            for( Map.Entry<String,Object> entry : entrySet) {
                String appName = entry.getKey();
                Map<String,Object> appMap = (Map<String,Object>)entry.getValue();
                if (appMap != null) {
                    Map<String,Object> appBizProcs = (Map<String,Object>)appMap.get(Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES);
                    if (appBizProcs != null) {
                        Map<String,Object> bizProc = (Map<String,Object>) appBizProcs.get(Configuration.RAPIDSETUP_CONFIG_JOINER);
                        if (bizProc != null) {
                            Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_JOINER_BARE_PROV);
                            boolean supportProvisioning = Util.otob(obj);
                            if (supportProvisioning) {
                                appIds.add(appName);
                            }
                        }
                    }
                }
            }
        }

        return appIds;
    }

    /**
     * @return true if the application is configured to allow new identities it creates to be marked to be Joiner-ed
     * Otherwise, false.
     */
    public static boolean isApplicationJoinerProducer(Application app) {
        boolean joinerProducing = false;
        if (app != null) {
            String appName = app.getName();
            if (Util.isNotNullOrEmpty(appName)) {
                Map<String,Object> bizProc = getApplicationBusinessProcessConfig(appName, Configuration.RAPIDSETUP_CONFIG_JOINER);
                if (bizProc != null) {
                    Object obj = bizProc.get(Configuration.RAPIDSETUP_CONFIG_JOINER_PRODUCING);
                    joinerProducing = Util.otob(obj);
                }
            }
        }
        return joinerProducing;
    }

    /**
     * Fetch the workflow named configured for a specific business process
     * @param businessProcessName the business process name.
     * @return String value of the RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW (triggerWorkflow) for a business process
     * @throws GeneralException
     */
    public static String getBusinessProcessWorkflowName(String businessProcessName) throws GeneralException {
        String flowName;
        if (Util.isNotNullOrEmpty(businessProcessName)) {
            Map businessProcessConfigSection = (Map) getRapidSetupBusinessProcessConfigurationSection();
            if (businessProcessConfigSection != null) {
                Map  bizProc = (Map) businessProcessConfigSection.get(businessProcessName);
                if (!Util.isEmpty(bizProc)) {
                    flowName = (String)bizProc.get(Configuration.RAPIDSETUP_CONFIG_PARAM_TRIGGER_WORKFLOW);
                }
                else {
                    throw new GeneralException("No configuration found for RapidSetup business process '" + businessProcessName + "'");
                }
            }
            else {
                throw new GeneralException("No business processes key found in RapidSetup configuration");
            }
        }
        else {
            throw new GeneralException("A valid businessProcessName is required");
        }
        return flowName;
    }


    /**
     * The method is used to fetch the list of role types that are to be treated as RapidSetup Birthright Roles
     * @return the names of the role types or null if none exist
     */
    public static List<String> getRapidSetupBirthrightRoleTypeNames() {
        List<String> birthrightRoleTypeNames = new ArrayList<String>();
        Map<String,Object> birthrightMap = (Map<String,Object>)get(Configuration.RAPIDSETUP_CONFIG_BIRTHRIGHT);
        if (!Util.isEmpty(birthrightMap)) {
            // get the list of roleType names from the birthrightMap
            birthrightRoleTypeNames = (List<String>) birthrightMap.get(Configuration.RAPIDSETUP_CONFIG_BIRTHRIGHT_ROLETYPES);
        }
        return birthrightRoleTypeNames;
    }

    /**
     * Convenience method to determine if a role type is considered a birthright role type
     * @param roleType
     * @return
     */
    public static boolean isBirthrightRoleType(RoleTypeDefinition roleType) {
        List<String> rapidSetupBirthrightRoleTypes = getRapidSetupBirthrightRoleTypeNames();
        if (roleType != null && roleType.getName() != null && rapidSetupBirthrightRoleTypes != null) {
            if ((rapidSetupBirthrightRoleTypes.contains(roleType.getName()))
                    && (roleType.isNoAutoAssignment() == true)
                    && (roleType.isNoAssignmentSelector() == false)) {
                return true;
            }
        }
        return false;
    }

    /////////////////////////////////////////////
    // Path-based lookups
    /////////////////////////////////////////////

    /**
     * Return the value of the RapidSetup configuration key identified
     * by the path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the value of the key.  Return null if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static Object get(String csvPath) {
        return get(Configuration.getRapidSetupConfig(), csvPath);
    }

    /**
     * Return the String value of the RapidSetup configuration key identified
     * by the path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the String value of the key.  Return null if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static String getString(String csvPath) {
        return getString(Configuration.getRapidSetupConfig(), csvPath);
    }

    /**
     * Return the boolean value of the RapidSetup configuration key identified
     * by the path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the boolean value of the key.  Return false if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static boolean getBoolean(String csvPath) {
        return getBoolean(Configuration.getRapidSetupConfig(), csvPath);
    }

    /**
     * Return the integer value of the RapidSetup configuration key identified
     * by the path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the integer value of the key.  Return 0 if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static int getInt(String csvPath) {
        return getInt(Configuration.getRapidSetupConfig(), csvPath);
    }

    /**
     * Return the String value of the RapidSetup configuration key identified
     * by the path
     * @param cfg the Configuration object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the String value of the key.  Return null if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static String getString(Configuration cfg, String csvPath) {
        Object objVal = get(cfg, csvPath);
        return Util.otos(objVal);
    }

    /**
     * Return the boolean value of the RapidSetup configuration key identified
     * by the path
     * @param cfg the Configuration object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the boolean value of the key.  Return false if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static boolean getBoolean(Configuration cfg, String csvPath) {
        Object objVal = get(cfg, csvPath);
        return Util.otob(objVal);
    }

    /**
     * Return the integer value of the RapidSetup configuration key identified
     * by the path
     * @param cfg the Configuration object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the integer value of the key.  Return 0 if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    public static int getInt(Configuration cfg, String csvPath) {
        Object objVal = get(cfg, csvPath);
        return Util.otoi(objVal);
    }

    /**
     * Return the String value of the map key identified
     * by the path
     * @param map the Map object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the String value of the key.  Return null if key or any other part of the
     * path is not present in the map.
     */
    public static String getString(Map map, String csvPath) {
        Object objVal = get(map, csvPath);
        return Util.otos(objVal);
    }

    /**
     * Return the boolean value of the map key identified
     * by the path
     * @param map the Map object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the boolean value of the key.  Return false if key or any other part of the
     * path is not present in the map.
     */
    public static boolean getBoolean(Map map, String csvPath) {
        Object objVal = get(map, csvPath);
        return Util.otob(objVal);
    }

    /**
     * Return the integer value of the map key identified
     * by the path
     * @param map the Map object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the integer value of the key.  Return 0 if key or any other part of the
     * path is not present in the map.
     */
    public static int getInt(Map map, String csvPath) {
        Object objVal = get(map, csvPath);
        return Util.otoi(objVal);
    }

    /**
     * Return the value of the RapidSetup configuration key identified
     * by the path
     * @param cfg the Configuration object to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the value of the key.  Return null if key or any other part of the
     * path is not present in the RapidSetup Configuration.
     */
    static public Object get(Configuration cfg, String csvPath) {
        Object result = null;
        if (cfg != null) {
            result = get(cfg.getAttributes(), csvPath);
        }

        return result;
    }

    /**
     * Return the value of the map key identified
     * by the path
     * @param map the Map to search for the key via the given path
     * @param csvPath a CSV of the path to navigate through to find the key.
     *                The last string in the CSV is the name of the key to return.
     *                The (optional) strings before the last represent the Map
     *                keys to navigate through.
     * @return the value of the key.  Return null if key or any other part of the
     * path is not present in the map
     */
    static public Object get(Map map, String csvPath) {
        Object result = null;
        if (map != null) {
            List<String> pathFields = Util.csvToList(csvPath, true);
            if (!Util.isEmpty(pathFields)) {
                int numPathFields = pathFields.size();
                if (numPathFields > 0) {
                    Map currMap = map;
                    for (int i = 0; i < numPathFields; i++) {
                        String field = pathFields.get(i);
                        if (i < (numPathFields - 1)) {
                            // this should be a Map
                            Object fieldVal = currMap.get(field);
                            if (fieldVal instanceof Map) {
                                currMap = (Map) fieldVal;
                            } else {
                                // Cannot resolve the path
                                break;
                            }
                        } else {
                            result = currMap.get(field);
                        }
                    }
                }
            }
        }

        return result;
    }


    /**
     * @return true if the given IdentityTrigger is a RapidSetup trigger, and
     * it the RapidSetup configuration is enabled for its respective process
     * ("joiner", "mover", etc.)
     */
    public static boolean isTriggerInactive(IdentityTrigger trigger) {
        boolean inactive = false;
        if (trigger != null && trigger.getType() == IdentityTrigger.Type.RapidSetup) {
            String process = trigger.getMatchProcess();
            boolean enabled = getBoolean("businessProcesses," + process + ",enabled");
            inactive = !enabled;
        }
        return inactive;
    }


}
