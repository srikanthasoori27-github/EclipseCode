/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.accessrequest.AccessRequest;

/**
 * Service for accessing dynamic configuration that depends on specific access requests.
 */
public class AccessRequestConfigService {
    public static final Log log = LogFactory.getLog(AccessRequestConfigService.class);

    private static final String LCM_ADD_ACTION = "add";
    private static final String LCM_REMOVE_ACTION = "remove";

    private SailPointContext context;
    private Configuration systemConfig;

    public AccessRequestConfigService(SailPointContext context) {
        this.context = context;
        this.systemConfig = Configuration.getSystemConfig();
    }

    /**
     * Get the config DTOs for the given request made by the requester by type
     * @param requester Identity making request
     * @param accessRequest request object
     * @param type Rule type for config
     * @return Map list of AccessRequestConfigDTOs keyed by item id
     * @throws GeneralException
     */
    public Map<String, List<AccessRequestConfigDTO>> getConfigsForRequest(Identity requester, AccessRequest accessRequest, Rule.Type type) throws GeneralException {
        List<Rule> rules = getEnabledConfigRules(type);
        return getAccessRequestConfigDTOs(requester, accessRequest, rules, type);
    }

    /**
     * Run the list of rules and return the list of AccessRequestConfigDTOs
     * @param requester Identity making request
     * @param accessRequest AccessRequestObject
     * @param rules List of enabled rules to run
     * @param type Rule type for config
     * @return Map list of AccessRequestConfigDTOs keyed by item id
     * @throws GeneralException
     */
    private Map<String, List<AccessRequestConfigDTO>> getAccessRequestConfigDTOs(Identity requester, AccessRequest accessRequest, List<Rule> rules, Rule.Type type) throws GeneralException {
        List<String> identities = accessRequest.getIdentityIds();

        if (identities == null || identities.isEmpty()) {
            throw new GeneralException("The requestee is missing.");
        }

        Map<String, List<AccessRequestConfigDTO>> result = new HashMap<>();

        List<Identity> requestees = getRequestees(identities);

        // Get all configs for the added roles.
        for (String roleId : Util.iterate(accessRequest.getAddedRoleIds())) {
            List<AccessRequestConfigDTO> configs = runRules(requester, requestees, roleId, LCM_ADD_ACTION, /*isRole*/true, rules, type);
            addResult(result, roleId, configs);
        }

        // Get all attachment configs for the removed roles.
        for (String roleId : Util.iterate(accessRequest.getRemovedRoleIds())) {
            List<AccessRequestConfigDTO> configs = runRules(requester, requestees, roleId, LCM_REMOVE_ACTION, /*isRole*/true, rules, type);
            addResult(result, roleId, configs);
        }

        // Get all attachment configs for the added entitlements.
        for (String managedAttrId : Util.iterate(accessRequest.getAddedEntitlementIds())) {
            List<AccessRequestConfigDTO> configs = runRules(requester, requestees, managedAttrId, LCM_ADD_ACTION, /*isRole*/false, rules, type);
            addResult(result, managedAttrId, configs);
        }

        // Get all attachment configs for the removed entitlements.
        for (String managedAttrId : Util.iterate(accessRequest.getRemovedEntitlementIds())) {
            List<AccessRequestConfigDTO> configs = runRules(requester, requestees, managedAttrId, LCM_REMOVE_ACTION, /*isRole*/false, rules, type);
            addResult(result, managedAttrId, configs);
        }

        return result;
    }

    /**
     * Convert list of ids to identities
     * @param identities List of identity ids
     * @return List of identities
     * @throws GeneralException
     */
    private List<Identity> getRequestees(List<String> identities) throws GeneralException {
        List<Identity> requestees = new ArrayList<>();

        for (String identityId : identities ) {
            Identity requestee = context.getObjectById(Identity.class, identityId);

            if (requestee != null) {
                requestees.add(requestee);
            }
        }

        return requestees;
    }

    private void addResult (Map<String, List<AccessRequestConfigDTO>> map, String key, List<AccessRequestConfigDTO> value) {
        if (map.get(key) != null) {
            List<AccessRequestConfigDTO> existing = map.get(key);
            existing.addAll(value);
            map.put(key,existing);
        } else {
            map.put(key, value);
        }
    }

    /**
     * Get the list of enabled Rule objects by type
     * @param type config rule type. can be AttachmentConfig or CommentConfig
     * @return List<Rule> list of Rule objects that have been enabled in the system
     * @throws GeneralException
     */
    public List<Rule> getEnabledConfigRules(Rule.Type type) throws GeneralException {
        List<String> enabledRules = new ArrayList<>();

        if (type == Rule.Type.AttachmentConfig) {
            enabledRules = systemConfig.getList(Configuration.ATTACHMENT_CONFIG_RULES);
        }
        else if (type == Rule.Type.CommentConfig) {
            enabledRules = systemConfig.getList(Configuration.COMMENT_CONFIG_RULES);
        }

        List configRules = new ArrayList();

        if (!Util.isEmpty(enabledRules)) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.ignoreCase(Filter.eq("type", type)));
            qo.add(Filter.ignoreCase(Filter.in("name", enabledRules)));
            List<Rule> rules = context.getObjects(Rule.class, qo);
            for (Rule rule : rules) {
                if (rule.getType() == type) {
                    configRules.add(rule);
                }
            }
        }

        return configRules;
    }

    /**
     * Runs the given rules for one set of inputs and returns the resulting list of AccessRequestConfigDTOs.
     * The comment config rules require a list of requestees instead of a single requestee like the
     * attachment configs do.
     *
     * @param requester Identity making the request
     * @param requestees Identities that are the target of the request
     * @param itemId Bundle or ManagedAttribute id
     * @param action add or remove action
     * @param isRole true if item is Bundle
     * @param rules List of rules to run
     * @param type Rule type for config
     * @return List of AccessRequestConfigDTOs returned by the rules
     * @throws GeneralException
     */
    protected List<AccessRequestConfigDTO> runRules(Identity requester, List<Identity> requestees, String itemId, String action, boolean isRole, List<Rule> rules, Rule.Type type)
            throws GeneralException
    {
        List<AccessRequestConfigDTO> accessRequestConfigDTOList = new ArrayList<>();
        SailPointObject requestedItem = isRole ? context.getObjectById(Bundle.class, itemId) : context.getObjectById(ManagedAttribute.class, itemId);

        // if require comment for all items is enabled return AccessRequestConfigDTO with required set to true for item
        // and ignore the configured rules
        if (type == Rule.Type.CommentConfig &&
            systemConfig.getBoolean(Configuration.REQUIRE_ACCESS_REQUEST_COMMENTS_ALL, false)) {
            accessRequestConfigDTOList.add(new AccessRequestConfigDTO(true, ""));
            return accessRequestConfigDTOList;
        }

        for (Rule rule : rules) {
            Map<String, Object> params = new HashMap<>();
            params.put("requester", requester);
            // Attachment config rules take one requestee identity
            if (type == Rule.Type.AttachmentConfig) {
                params.put("requestee", requestees.get(0));
            }
            else {
                params.put("requestees", requestees);
            }
            params.put("requestedItem", requestedItem);
            params.put("action", action);

            Object result = context.runRule(rule, params);
            // Rule will return either null or empty list if there are no appropriate configs.
            if (result != null) {
                if (result instanceof List) {
                    for (Object listObj : (List)result) {
                        if (listObj instanceof AccessRequestConfigDTO) {
                            accessRequestConfigDTOList.add((AccessRequestConfigDTO)listObj);
                        }
                        else {
                            // List result contained something that isn't a dto
                            throw new GeneralException("Rule must return a list of configs dtos.");
                        }
                    }
                }
                else {
                    // Result was not a list or dto
                    throw new GeneralException("Rule must return a list of configs dtos.");
                }
            }
        }

        return accessRequestConfigDTOList;
    }

    /**
     * Returns true if any of the given configs have required set to true.
     * @param accessRequestConfigDTOMap
     * @return boolean true if any one of the AccessRequestConfigDTOs has required set to true
     */
    public boolean configsRequire( Map<String,List<AccessRequestConfigDTO>> accessRequestConfigDTOMap) {
        for (List<AccessRequestConfigDTO> configs : Util.safeIterable(accessRequestConfigDTOMap.values())){
            for (AccessRequestConfigDTO config : configs) {
                if (config.isRequired()) {
                    return true;
                }
            }
        }
        return false;
    }
}
