/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.acceleratorpack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequest;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Classification;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.Custom;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Plugin;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.Widget;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.Workflow;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.trigger.IdentityProcessingThresholdDTO;

/**
 * UI Service for the Application Onboard Settings
 */
public class ApplicationOnboardService {

    // Configuration Keys with their path prepended to simply code.  Used to drill into attributes map since its not flat
    private static final String PATH_TO_JOINER_WORKFLOW = "businessProcesses,joiner,triggerWorkflow";
    private static final String PATH_TO_MOVER_CERTIFICATION_OWNER = "businessProcesses,mover,certificationParams,certificationOwner";
    private static final String PATH_TO_MOVER_BACKUP_CERTIFIER = "businessProcesses,mover,certificationParams,backupCertifier";
    private static final String PATH_TO_MOVER_WORKFLOW = "businessProcesses,mover,triggerWorkflow";
    private static final String PATH_TO_LEAVER_REASSIGN_ARTIFACT_ALTERNATIVE = "businessProcesses,leaver,reassignArtifacts,reassignAlternative";
    private static final String PATH_TO_LEAVER_REASSIGN_ARTIFACT_RULE = "businessProcesses,leaver,reassignArtifacts,reassignRule";
    private static final String PATH_TO_LEAVER_REASSIGN_IDENTITIES_ALTERNATIVE = "businessProcesses,leaver,reassignIdentities,reassignAlternative";
    private static final String PATH_TO_LEAVER_REASSIGN_IDENTITIES_RULE = "businessProcesses,leaver,reassignIdentities,reassignRule";
    private static final String PATH_TO_TERMINATE_REASSIGN_ARTIFACT_ALTERNATIVE = "businessProcesses,terminate,reassignArtifacts,reassignAlternative";
    private static final String PATH_TO_TERMINATE_REASSIGN_ARTIFACT_RULE = "businessProcesses,terminate,reassignArtifacts,reassignRule";
    private static final String PATH_TO_TERMINATE_REASSIGN_IDENTITIES_ALTERNATIVE = "businessProcesses,terminate,reassignIdentities,reassignAlternative";
    private static final String PATH_TO_TERMINATE_REASSIGN_IDENTITIES_RULE = "businessProcesses,terminate,reassignIdentities,reassignRule";
    private static final String PATH_TO_LEAVER_POST_RULE = "businessProcesses,leaver,postLeaverRule";
    private static final String PATH_TO_TERMINATE_POST_RULE = "businessProcesses,terminate,postTerminateRule";
    private static final String PATH_TO_LEAVER_EMAIL_TEMPLATE_LEAVER_COMPLETED = "businessProcesses,leaver,email,leaverCompleted";
    private static final String PATH_TO_TERMINATE_EMAIL_TEMPLATE_LEAVER_COMPLETED = "businessProcesses,terminate,email,terminateCompleted";
    private static final String PATH_TO_JOINER_EMAIL_TEMPLATE_JOINER_COMPLETED = "businessProcesses,joiner,email,joinerCompleted";
    private static final String PATH_TO_JOINER_EMAIL_TEMPLATE_JOINER_SEND_TEMPORARY_PASSWORD = "businessProcesses,joiner,email,joinerSendTemporaryPassword";
    private static final String PATH_TO_JOINER_POST_RULE = "businessProcesses,joiner,postJoinerRule";
    private static final String PATH_TO_MOVER_POST_RULE = "businessProcesses,mover,postMoverRule";
    private static final String PATH_TO_LEAVER_EMAIL_TEMPLATE_OWNERSHIP_REASSIGNMENT = "businessProcesses,leaver,email,ownershipReassignment";
    public static final String PATH_TO_LEAVER_EMAIL_ALT_NOTIFY_WORKGROUP = "businessProcesses,leaver,email,altNotifyWorkgroup";
    private static final String PATH_TO_TERMINATE_EMAIL_TEMPLATE_OWNERSHIP_REASSIGNMENT = "businessProcesses,terminate,email,ownershipReassignment";
    public static final String PATH_TO_TERMINATE_EMAIL_ALT_NOTIFY_WORKGROUP = "businessProcesses,terminate,email,altNotifyWorkgroup";
    public static final String PATH_TO_JOINER_EMAIL_ALT_NOTIFY_WORKGROUP = "businessProcesses,joiner,email,altNotifyWorkgroup";
    private static final String PATH_TO_LEAVER_WORKFLOW = "businessProcesses,leaver,triggerWorkflow";
    private static final String PATH_TO_TERMINATE_WORKFLOW = "businessProcesses,terminate,triggerWorkflow";
    private static final String PATH_TO_GENERAL_EMAIL_TEMPLATE_STYLE_SHEET = "email,styleSheet";
    private static final String PATH_TO_GENERAL_EMAIL_TEMPLATE_HEADER_TEMPLATE = "email,headerTemplate";
    private static final String PATH_TO_GENERAL_EMAIL_TEMPLATE_FOOTER_TEMPLATE = "email,footerTemplate";
    private static final String PATH_TO_GENERAL_EMAIL_ALT_MANAGER = "email,altManager";
    private static final String PATH_TO_GENERAL_EMAIL_ERROR_NOTIFICATION_WORKGROUP = "email,errorNotificationWorkGroup";
    private static final String PATH_TO_GENERAL_WORKFLOW_REQUESTER = "workflow,requester";
    private static final String PATH_TO_APP_PLAN_RULE = "%applications%,businessProcesses,leaver,planRule";
    private static final String PATH_TO_LEAVER_ENTITLEMENT_EXCEPTION = "%applications%,businessProcesses,leaver,entitlementException";
    // special case of configuration key that does not have a class used to regenerate the suggest object
    private static final String SUGGEST_LIST_PATH_TO_LEAVER_REASSIGN_ARTIFACT_TYPES = "businessProcesses,leaver,reassignArtifacts,reassignArtifactTypes";
    private static final String SUGGEST_LIST_PATH_TO_TERMINATE_REASSIGN_ARTIFACT_TYPES = "businessProcesses,terminate,reassignArtifacts,reassignArtifactTypes";
    private static final String SUGGEST_LIST_PATH_TO_BIRTHRIGHT_ROLE_TYPES = "birthright,roleTypes";

    /**
     * The list of objects that could be reassigned in a format {<object class>, <display name>}
     */
    public static final Map<Class<? extends SailPointObject>, String> REASSIGNABLE_ARTIFACT_TYPES =
            new HashMap<Class<? extends SailPointObject>, String>() {{
                put(Alert.class, "Alert");
                put(AlertDefinition.class, "Alert Definition");
                put(Application.class, "Application");
                put(BatchRequest.class, "Batch Request");
                put(Bundle.class, "Role");
                put(Certification.class, "Access Review");
                put(CertificationArchive.class, "Access Review Archive");
                put(CertificationDefinition.class, "Certification Schedule");
                put(CertificationGroup.class, "Certification");
                put(Classification.class, "Classification");
                put(CorrelationConfig.class, "Correlation Configuration");
                put(Custom.class, "Custom");
                put(Form.class, "Form");
                put(GroupFactory.class, "Group Factory");
                put(GroupDefinition.class, "Group/Population");
                put(IdentityRequest.class, "Access Request");
                put(ManagedAttribute.class, "Entitlement");
                put(Plugin.class, "Plugin");
                put(Policy.class, "Policy");
                put(PolicyViolation.class, "Policy Violation");
                put(TaskDefinition.class, "Task and Reports");
                put(TaskResult.class, "Task and Report Results");
                put(TaskSchedule.class, "Task and Report Schedules");
                put(Widget.class, "Widget");
                put(Workflow.class, "Workflow");
                put(WorkItem.class, "Work Item");
                put(WorkItemArchive.class, "Work Item Archive");
            }};

    /**
     * Inner map of attributes that will come in as suggest objects but should be stored as simple names.
     * The Class here is what is used to regenerate the suggest object when the definition is loaded.
     */
    private static final Map<String, Class> SUGGEST_ATTRIBUTES_MAP = new HashMap<String, Class>() {{
        put(PATH_TO_JOINER_WORKFLOW, Workflow.class);
        put(PATH_TO_MOVER_CERTIFICATION_OWNER, Identity.class);
        put(PATH_TO_MOVER_BACKUP_CERTIFIER, Identity.class);
        put(PATH_TO_MOVER_WORKFLOW, Workflow.class);
        put(PATH_TO_LEAVER_REASSIGN_ARTIFACT_ALTERNATIVE, Identity.class);
        put(PATH_TO_LEAVER_REASSIGN_ARTIFACT_RULE, Rule.class);
        put(PATH_TO_LEAVER_REASSIGN_IDENTITIES_ALTERNATIVE, Identity.class);
        put(PATH_TO_LEAVER_REASSIGN_IDENTITIES_RULE, Rule.class);
        put(PATH_TO_TERMINATE_REASSIGN_ARTIFACT_ALTERNATIVE, Identity.class);
        put(PATH_TO_TERMINATE_REASSIGN_ARTIFACT_RULE, Rule.class);
        put(PATH_TO_TERMINATE_REASSIGN_IDENTITIES_ALTERNATIVE, Identity.class);
        put(PATH_TO_TERMINATE_REASSIGN_IDENTITIES_RULE, Rule.class);
        put(PATH_TO_LEAVER_POST_RULE, Rule.class);
        put(PATH_TO_TERMINATE_POST_RULE, Rule.class);
        put(PATH_TO_JOINER_POST_RULE, Rule.class);
        put(PATH_TO_MOVER_POST_RULE, Rule.class);
        put(PATH_TO_LEAVER_EMAIL_TEMPLATE_LEAVER_COMPLETED, EmailTemplate.class);
        put(PATH_TO_TERMINATE_EMAIL_TEMPLATE_LEAVER_COMPLETED, EmailTemplate.class);
        put(PATH_TO_JOINER_EMAIL_TEMPLATE_JOINER_COMPLETED, EmailTemplate.class);
        put(PATH_TO_JOINER_EMAIL_TEMPLATE_JOINER_SEND_TEMPORARY_PASSWORD, EmailTemplate.class);
        put(PATH_TO_LEAVER_EMAIL_TEMPLATE_OWNERSHIP_REASSIGNMENT, EmailTemplate.class);
        put(PATH_TO_TERMINATE_EMAIL_TEMPLATE_OWNERSHIP_REASSIGNMENT, EmailTemplate.class);
        put(PATH_TO_LEAVER_EMAIL_ALT_NOTIFY_WORKGROUP, Identity.class);
        put(PATH_TO_TERMINATE_EMAIL_ALT_NOTIFY_WORKGROUP, Identity.class);
        put(PATH_TO_JOINER_EMAIL_ALT_NOTIFY_WORKGROUP, Identity.class);
        put(PATH_TO_LEAVER_WORKFLOW, Workflow.class);
        put(PATH_TO_TERMINATE_WORKFLOW, Workflow.class);
        put(PATH_TO_GENERAL_EMAIL_TEMPLATE_STYLE_SHEET, EmailTemplate.class);
        put(PATH_TO_GENERAL_EMAIL_TEMPLATE_HEADER_TEMPLATE, EmailTemplate.class);
        put(PATH_TO_GENERAL_EMAIL_TEMPLATE_FOOTER_TEMPLATE, EmailTemplate.class);
        put(PATH_TO_GENERAL_EMAIL_ALT_MANAGER, Identity.class);
        put(PATH_TO_GENERAL_EMAIL_ERROR_NOTIFICATION_WORKGROUP, Identity.class);
        put(PATH_TO_GENERAL_WORKFLOW_REQUESTER, Identity.class);
        put(PATH_TO_APP_PLAN_RULE, Rule.class);
    }};

    /**
     * Inner map of attributes that will come in as filters and should be cleaned up before storing in the XML.
     * They will be expanded back out before being sent to the client.
     */
    private static final Map<String, Class> FILTER_ATTRIBUTES_MAP = new HashMap<String, Class>() {{
        put(PATH_TO_LEAVER_ENTITLEMENT_EXCEPTION, ApplicationOnboardApplicationAttributeSuggestListFilterContext.class);
    }};

    private static final String SUGGEST_ATTR_DISPLAY_NAME = "displayName";
    private static final String SUGGEST_ATTR_ID = "id";

    private UserContext userContext;

    private SailPointContext getContext() {
        return this.userContext.getContext();
    }

    private Locale getLocale() {
        return this.userContext.getLocale();
    }

    public ApplicationOnboardService(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Reads the map of attributes that come from the client as suggest objects and pull the name out of them,
     * updating the map for saving
     * @param attributes Map of attributes to update
     */
    private void readSuggestObjects(Attributes<String, Object> attributes) {
        for (Map.Entry<String, Class> suggestEntry : SUGGEST_ATTRIBUTES_MAP.entrySet()) {
            // The expectation is that the key from the suggestEntry is either a top level attribute
            // that does not contain the DELIMITER, or is a path containing a key divided by DELIMITER
            String attributePath = suggestEntry.getKey();
            Map<String, Object> innerMap = getAttributeAsMap(attributePath, attributes);

            if (innerMap != null) {
                String attributeName = getAttributeNameFromPath(attributePath);
                Object value = innerMap.get(attributeName);
                String newValue = SuggestHelper.extractNameOrIdFromSuggestObject(value);
                if (newValue != null) {
                    innerMap.put(attributeName, newValue);
                }
            }
        }
    }

    /**
     * Reads the map of attributes that come from the client that contains custom multi-suggest objects and pulls
     * the ids out of the list of suggest objects, updating the map for saving
     * @param attributes Map of attributes containing the custom multi-suggest objects to update
     */
    private void readSuggestList(Attributes<String, Object> attributes) {
        List<String> pathsToRead = new ArrayList<>();
        pathsToRead.add(SUGGEST_LIST_PATH_TO_LEAVER_REASSIGN_ARTIFACT_TYPES);
        pathsToRead.add(SUGGEST_LIST_PATH_TO_TERMINATE_REASSIGN_ARTIFACT_TYPES);
        pathsToRead.add(SUGGEST_LIST_PATH_TO_BIRTHRIGHT_ROLE_TYPES);
        for (String path : pathsToRead) {
            List<String> suggestItems = new ArrayList<>();
            Map<String, Object> innerMap = getAttributeAsMap(path, attributes);
            if (innerMap != null) {
                String attributeName = getAttributeNameFromPath(path);
                Object suggestItemsObject = innerMap.get(attributeName);
                // the suggestItemsObject is a list of maps that each contain an id to save
                if (suggestItemsObject instanceof List) {
                    List<Map<String, Object>> suggestItemsList;
                    suggestItemsList = (List<Map<String, Object>>) suggestItemsObject;
                    for (Map<String, Object> map : suggestItemsList) {
                        // only one entry from the map is needed since both store the same value
                        Object value = map.get(SUGGEST_ATTR_ID);
                        suggestItems.add((String) value);
                    }
                    innerMap.put(attributeName, suggestItems);
                }
            }
        }
    }

    /**
     * Convert known filter attributes to compressed ListFilterValues.
     *
     * @param attributes Attributes map to parse
     * @param appId Application ID to use for ListFilterContexts that need it.
     * @throws GeneralException
     */
    private void readFilterValues(Attributes<String, Object> attributes, String appId) throws GeneralException {

        for (Map.Entry<String, Class> filterEntry : FILTER_ATTRIBUTES_MAP.entrySet()) {
            String filterPath = filterEntry.getKey();
            Map<String, Object> innerMap = getAttributeAsMap(filterPath, attributes);

            if (innerMap != null) {
                try {
                    Class filterContextClass = filterEntry.getValue();
                    ListFilterContext filterContext;

                    // If we get more than one ListFilterContext that requires an appId, we should change
                    // this to be more generic.
                    if (filterContextClass.equals(ApplicationOnboardApplicationAttributeSuggestListFilterContext.class)) {
                        filterContext = new ApplicationOnboardApplicationAttributeSuggestListFilterContext(appId);
                    } else {
                        filterContext = (ListFilterContext)filterEntry.getValue().newInstance();
                    }

                    ListFilterService listFilterService = new ListFilterService(this.getContext(), getLocale(), filterContext);
                    String attributeName = getAttributeNameFromPath(filterPath);

                    List<ListFilterValue> filterValues = ListFilterService.getFilterValues(innerMap, attributeName);
                    listFilterService.collapseFilterValues(filterValues);
                    innerMap.put(attributeName, filterValues);
                } catch(Exception ex) {
                    throw new GeneralException("Unable to read filter values in RapidSetup config", ex);
                }
            }
        }
    }

    /**
     * Creates suggest objects in map of attributes for any values that require those on the client. Uses
     * Uses SUGGEST_ATTRIBUTES_MAP to get the list and classes.
     * @param attributes Map of attributes to update
     * @throws GeneralException
     */
    private void createSuggestObjects(Attributes<String, Object> attributes) throws GeneralException {
        for (Map.Entry<String, Class> suggestEntry : SUGGEST_ATTRIBUTES_MAP.entrySet()) {
            // The expectation is that the key from the suggestEntry is either a top level attribute
            // that does not contain the DELIMITER, or is a path with a key divided by DELIMITER
            String attributePath = suggestEntry.getKey();
            Map<String, Object> innerMap = getAttributeAsMap(attributePath, attributes);

            if (innerMap != null) {
                String attributeName = getAttributeNameFromPath(attributePath);
                Object nameObj = innerMap.get(attributeName);
                if (nameObj instanceof String) {
                    String name = (String) nameObj;
                    Object newValue = SuggestHelper.getSuggestObject(suggestEntry.getValue(), name, getContext());
                    if (newValue != null) {
                        innerMap.put(attributeName, newValue);
                    }
                }
            }
        }
    }

    /**
     * Creates list of multi suggest objects in map of attributes for custom multi-suggest objects used in client.
     * @param attributes Map of attributes to update
     */
    private void createSuggestList(Attributes<String, Object> attributes) {
        List<String> pathsToRead = new ArrayList<>();
        pathsToRead.add(SUGGEST_LIST_PATH_TO_LEAVER_REASSIGN_ARTIFACT_TYPES);
        pathsToRead.add(SUGGEST_LIST_PATH_TO_TERMINATE_REASSIGN_ARTIFACT_TYPES);
        pathsToRead.add(SUGGEST_LIST_PATH_TO_BIRTHRIGHT_ROLE_TYPES);
        for (String path : pathsToRead) {
            List<Map<String, Object>> suggestItemList = new ArrayList<>();
            Map<String, Object> innerMap = getAttributeAsMap(path, attributes);
            if (innerMap != null) {
                String attributeName = getAttributeNameFromPath(path);
                Object suggestItemsObject = innerMap.get(attributeName);
                // the suggestItemsObject is a list of strings that each need to be converted to the suggest object
                if (suggestItemsObject instanceof List) {
                    for (String suggestItem : (List<String>) suggestItemsObject) {
                        Map<String, Object> suggestItemMap = new HashMap<>();
                        suggestItemMap.put(SUGGEST_ATTR_ID, suggestItem);
                        String displayName = REASSIGNABLE_ARTIFACT_TYPES.entrySet().stream()
                                .filter(entry -> entry.getKey().getSimpleName().equals(suggestItem))
                                .findAny()
                                .map(Map.Entry::getValue)
                                .orElse(suggestItem);
                        suggestItemMap.put(SUGGEST_ATTR_DISPLAY_NAME, displayName);
                        suggestItemList.add(suggestItemMap);
                    }
                    suggestItemList.sort(Comparator.comparing(item ->
                            String.valueOf(item.get(SUGGEST_ATTR_DISPLAY_NAME))));
                    innerMap.put(attributeName, suggestItemList);
                }
            }
        }
    }

    /**
     * Expand the filter values to include suggests and whatnot as required by client. Uses FILTER_ATTRIBUTES_MAP.
     * @param attributes Attributes map to update
     * @param appId Application ID to use for ListFilterContexts that need it.
     * @throws GeneralException
     */
    private void expandFilterValues(Attributes<String, Object> attributes, String appId) throws GeneralException {

        for (Map.Entry<String, Class> filterEntry : FILTER_ATTRIBUTES_MAP.entrySet()) {
            String filterPath = filterEntry.getKey();
            Map<String, Object> innerMap = getAttributeAsMap(filterPath, attributes);

            if (innerMap != null) {
                try {
                    Class filterContextClass = filterEntry.getValue();
                    ListFilterContext filterContext;

                    // If we get more than one ListFilterContext that requires an appId, we should change
                    // this to be more generic.
                    if (filterContextClass.equals(ApplicationOnboardApplicationAttributeSuggestListFilterContext.class)) {
                        filterContext = new ApplicationOnboardApplicationAttributeSuggestListFilterContext(appId);
                    } else {
                        filterContext = (ListFilterContext)filterEntry.getValue().newInstance();
                    }

                    String attributeName = getAttributeNameFromPath(filterPath);
                    List<ListFilterValue> filterValues = ListFilterService.getFilterValues(innerMap, attributeName);
                    ListFilterService listFilterService = new ListFilterService(this.getContext(), getLocale(), filterContext);
                    listFilterService.expandFilterValues(filterValues);
                    attributes.putClean(attributeName, filterValues);
                } catch(Exception ex) {
                    throw new GeneralException("Unable to expand filter values in RapidSetup config", ex);
                }
            }
        }
    }

    /**
     * Helper method to clean the data in the configAttributes for suggest objects
     * @param dto The ApplicationOnboardDTO
     */
    public void scrubAttributes(ApplicationOnboardDTO dto) {
        Attributes<String, Object> attributes = dto.getConfigAttributes();
        // Convert suggest objects to strings for storage
        readSuggestObjects(attributes);
        readSuggestList(attributes);
    }

    /**
     * Helper method to clean the data in the configAttributes for filter values
     * @param dto The ApplicationOnboardDTO to scrub
     * @param appId The application ID that these filters are for
     * @throws GeneralException
     */
    public void scrubFilterValues(ApplicationOnboardDTO dto, String appId) throws GeneralException {
        Attributes<String, Object> attributes = dto.getConfigAttributes();
        readFilterValues(attributes, appId);
    }

    /**
     * Helper method to enhance the data before sending the DTO to the client. Builds suggest objects
     * @param dto The ApplicationOnboardDTO
     * @throws GeneralException
     */
    public void enhanceAttributes(ApplicationOnboardDTO dto) throws GeneralException {
        Attributes<String, Object> attributes = dto.getConfigAttributes();
        // Creates the suggest objects for necessary values
        createSuggestObjects(attributes);
        createSuggestList(attributes);
    }

    /**
     * Helper method to enhance the data before sending the DTO to the client. Builds filter objects
     * @param dto The ApplicationOnboardDTO
     * @param appId The app ID of the application that these filters are for
     * @throws GeneralException
     */
    public void enhanceFilters(ApplicationOnboardDTO dto, String appId) throws GeneralException {
        Attributes<String, Object> attributes = dto.getConfigAttributes();
        expandFilterValues(attributes, appId);
    }

    /**
     * Helper to get just the target attribute name from a string path.
     * @param path Attribute string path to an attribute
     * @return The target attribute name
     */
    private String getAttributeNameFromPath(String path) {
        return getAttributeNameFromPath(Util.csvToList(path));
    }

    /**
     * Helper to get just the target attribute name from a list path.
     * @param path List of tokens that form a path to an attribute
     * @return The target attribute name
     */
    private String getAttributeNameFromPath(List<String> path) {
        String attributeName = null;

        if (!Util.isEmpty(path)) {
            // the last element of the tokens list is expected to be the key of the attribute
            attributeName = path.get(path.size() - 1);
        }

        return attributeName;
    }

    /**
     * Helper to return an attribute value as a Map.
     * @param path String path to the desired attribute.
     * @param attributes The attributes to traverse.
     * @return The Map at the target location, or null if it doesn't exist.
     */
    private Map<String, Object> getAttributeAsMap(String path, Map<String, Object> attributes) {
        List<String> tokens = Util.csvToList(path);
        return getAttributeAsMap(tokens, attributes);
    }

    /**
     * Helper to return an attribute value as a Map.
     * @param tokens List of path tokens to the desired attribute.
     * @param attributes The attributes to traverse.
     * @return The Map at the target location, or null if it doesn't exist.
     */
    private Map<String, Object> getAttributeAsMap(List<String> tokens, Map<String, Object> attributes) {
        Map<String, Object> target = null;
        String attributeName = getAttributeNameFromPath(tokens);

        if (!Util.isNullOrEmpty(attributeName)) {
            // use the tokens list to dig into the attributes for nested maps
            List<Map<String, Object>> innerMaps = mapsFromEnhancedPath(attributes, tokens.subList(0, tokens.size() - 1));

            for (Map<String, Object> innerMap : Util.safeIterable(innerMaps)) {
                if (innerMap.containsKey(attributeName)) {
                    target = innerMap;
                }
            }
        }

        return target;
    }

    /**
     * Return the list of maps found by walking the topMap, navigating down using the
     * key names found in keyPath.  Each item in keyPath is expected to be the name of a key
     * whose value is a Map (or a List&lt;Map> or a Map&lt;Map> if the key is appended and prepended
     * with % -- which indicates that collection flattening should occur). <p></p>
     *
     * The return list will have only a single map if none of the keys are collections.
     *
     * @param topMap the starting Map to begin navigation at
     * @param keyPath the set of keys to follow.  The first key in the list will be used
     *                get the Map value of a key in the topNav map. The second key, if
     *                present, would be used to get the value from Map found from first key,
     *                and so on.<p></p>
     *
     *                However, if any key has % as first and last character, then the key's value is expected
     *                to be a collection of Map and the collection will be flattened, and the navigation
     *                will continue down each of the maps in the collection.  This is why the
     *                return value is a List&lt;Map> instead of just Map.
     */
    private List<Map<String, Object>> mapsFromEnhancedPath(Map<String, Object> topMap, List<String> keyPath) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!Util.isEmpty(keyPath) && !Util.isEmpty(topMap)) {
            Map<String,Object> currMap = topMap;
            for (int i = 0; i < keyPath.size(); i++) {
                String currentKey = keyPath.get(i);
                boolean isMapKey = isMultivaluedKey(currentKey);
                String trimmedKey = getUndecoratedKeyName(currentKey);
                if (currMap.containsKey(trimmedKey)) {
                    Object keyValue = currMap.get(getUndecoratedKeyName(trimmedKey));
                    if (keyValue instanceof Map) {
                        Map<String,Object> keyValueMap = (Map)keyValue;
                        if (isMapKey) {
                            if (i == keyPath.size() - 1) {
                                // Fail,  we should be at a map
                                break;
                            }
                            // the value for each key in keyValueMap is expected to
                            // be a map.
                            for(String key : Util.safeIterable(keyValueMap.keySet())) {
                                Object valObj = keyValueMap.get(key);
                                if (valObj instanceof Map) {
                                    Map valMap = (Map)valObj;
                                    List<String> relativeKeys = keyPath.subList(i+1, keyPath.size());
                                    List<Map<String, Object>> subMaps = mapsFromEnhancedPath(valMap, relativeKeys);
                                    result.addAll(subMaps);
                                }
                            }
                            // we are done because the recursion above has already walked the rest of the path
                            break;
                        }
                        else {
                            currMap = keyValueMap;
                            if (i == keyPath.size() - 1) {
                                // we have reached the end of keys
                                result.add(keyValueMap);
                            }
                        }
                    }
                    else {
                        if (i == keyPath.size() - 1) {
                            // Fail,  we should be at a map
                            break;
                        }
                        if (keyValue instanceof List) {
                            if (isMapKey) {
                                List keyValueList = (List) keyValue;
                                if (!Util.isEmpty(keyValueList)) {
                                    for (Object listObj : keyValueList) {
                                        if (listObj instanceof Map) {
                                            Map localMap = (Map) listObj;
                                            List<String> relativeKeys = keyPath.subList(i + 1, keyPath.size());
                                            List<Map<String, Object>> subMaps = mapsFromEnhancedPath(localMap, relativeKeys);
                                            result.addAll(subMaps);
                                        }
                                    }
                                    // we are done because the recursion above has already walked the rest of the path
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * @return true if the given name start and end with '%'
     */
    private boolean isMultivaluedKey(String keyName) {
        return Util.isNotNullOrEmpty(keyName) &&
                keyName.startsWith("%") &&
                keyName.endsWith("%");
    }

    /**
     * @return strip any leading and trailing '%' from the given name
     */
    private String getUndecoratedKeyName(String fullKeyName) {
        if (isMultivaluedKey(fullKeyName)) {
            return fullKeyName.substring(1, fullKeyName.length() - 1);
        }
        else {
            return fullKeyName;
        }
    }

    /**
     * Retrieves the Identity Threshold configuration related to the Rapid Setup
     * workflows (j/l/m).
     *
     * @return a {@link Map} that contains one
     *         {@link IdentityProcessingThresholdDTO} object per workflow.
     * @throws GeneralException
     */
    public Map<String, IdentityProcessingThresholdDTO> getRapidSetupThresholdConfig() throws GeneralException {
        Map<String, IdentityProcessingThresholdDTO> identityTriggers = new HashMap<>();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("type", IdentityTrigger.Type.RapidSetup.name()));
        Iterator<IdentityTrigger> it = null;
        it = getContext().search(IdentityTrigger.class, qo);
        if (it != null) {
            while (it.hasNext()) {
                IdentityTrigger trigger = it.next();
                identityTriggers.put(String.valueOf(trigger.getParameters().get(IdentityTrigger.MATCH_PARAM_PROCESS)),
                        new IdentityProcessingThresholdDTO(
                                trigger.getId(),
                                trigger.getIdentityProcessingThreshold(),
                                trigger.getIdentityProcessingThresholdType()));
            }
        }
        return identityTriggers;
    }

}
