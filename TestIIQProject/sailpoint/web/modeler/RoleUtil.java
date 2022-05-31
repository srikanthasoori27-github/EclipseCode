/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.Explanator;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.Configuration;
import sailpoint.object.ExtState;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.RoleScorecard;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseBean;
import sailpoint.web.EventBean;
import sailpoint.web.FilterContainer;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleAttributesUtil.AttributesInfo;
import sailpoint.web.risk.BusinessRoleBarConfigBean.RoleRiskScore;
import sailpoint.web.util.WebUtil;

public class RoleUtil {
    private static final Log log = LogFactory.getLog(RoleUtil.class);

    public static final String ROLE_VIEWER_STATE = "roleViewerState";

    public static final String ROLE_PROFILE_ORDINAL = "profileOrdinal";
    public static final String ROLE_ID = "id";


    /**
     * Arbitrary constants used to define role types.
     * Note these map to general role type characteristics
     * rather than actual role type definitions.
     */
    public enum RoleType {
        // Role type has profiles
        ITRole,
        // role type must be able to inherit from other roles
        BusinessRole,
        // role type can be inherited by other roles and may inherit other roles
        OrganizationalRole
    }

    /**
     * Class implementing criteria for what counts as a "simple" filter on a role. The criteria here are:
     *
     * 1) Composite filter is simple if AND operation only and
     * 2) Leaf filter is simple if operatior is EQ or CONTAINS_ALL
     */
    public static class SimpleEntitlementCriteria {
        public boolean isSimpleCompositeFilter(Filter.CompositeFilter filter, Application app) {
            // If it's not an AND-ed expression, it's not simple.
            if (filter.getOperation() != Filter.BooleanOperation.AND) {
                return false;
            }
            // If any children are not simple leaf filters, it's not simple.
            for (Filter child : filter.getChildren()) {
                if (child instanceof Filter.CompositeFilter) {
                    return false;
                }
                if (!isSimpleLeafFilter((Filter.LeafFilter)child, app)) {
                    return false;
                }
            }
            return true;
        }

        public boolean isSimpleLeafFilter(Filter.LeafFilter filter, Application app) {
            // If the operation is not EQ or CONTAINS_ALL, it's not simple
            if (filter.getOperation() != Filter.LogicalOperation.EQ &&
                    filter.getOperation() != Filter.LogicalOperation.CONTAINS_ALL) {
                return false;
            }

            // If we get this far, it's simple
            return true;
        }
    }

    /**
     * Override of SimpleEntitlementCriteria to make simple entitlements more strict.
     * This is actually the default criteria in all cases, since only a couple places want more lenient criteria
     * This adds the following additional conditions
     *
     * 1) Leaf filter can not be ignores case.
     * 2) Leaf filter must be a managed attribute
     */
    public static class StrictSimpleEntitlementCriteria extends SimpleEntitlementCriteria {

        @Override
        public boolean isSimpleLeafFilter(Filter.LeafFilter filter, Application app) {
            if (!super.isSimpleLeafFilter(filter, app)) {
                return false;
            }

            // If it's not a managed attribute, it's not simple
            Schema schema = app.getAccountSchema();
            AttributeDefinition attributeDef = null;
            if (null != schema) {
                attributeDef = schema.getAttributeDefinition(filter.getProperty());
            } else {
                log.warn("Expected a schema for application " + app.getName() + " but it was null.");
            }
            if (attributeDef != null && !attributeDef.isManaged()) {
                return false;
            }

            // If Ignore Case is true (which is not the default), it's not simple
            if (filter.isIgnoreCase()) {
                return false;
            }

            // If we get this far, it's simple
            return true;
        }
    }

    /**
     * Representation of an entitlement from a leaf filter (see getLeafFilterJson)
     */
    public static class LeafFilterEntitlement {
        private String applicationName;
        private String property;
        private String value;
        private String roleName;
        private String displayValue;
        private String description;
        private String classifications;

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public void setDisplayValue(String displayValue) {
            this.displayValue = displayValue;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getClassifications() {
            return classifications;
        }

        public void setClassifications(String classifications) {
            this.classifications = classifications;
        }
    }

    public static Set<Bundle> getAllRolesInHierarchy(Resolver ctx, Bundle existingRole) {
        Set<Bundle> rolesInHierarchy = new HashSet<Bundle>();

        rolesInHierarchy.add(existingRole);
        try {
            addSubtree(rolesInHierarchy, existingRole, ctx, true);
            addSubtree(rolesInHierarchy, existingRole, ctx, false);
        } catch (GeneralException e) {
            log.error("Failed to find roles in hierarchy", e);
        }

        return rolesInHierarchy;
    }

    /**
     * This method returns a partial set of roles in the hierarchy.  The contents of the set vary depending
     * on whether this method is called in bottom-up or top-down mode
     *
     * @param existingRole
     * @param ctx
     * @param bottomUpMode
     * @return
     */
    public static Set<Bundle> getRolesInHierarchy(Bundle existingRole, Resolver ctx, boolean bottomUpMode) {
        Set<Bundle> rolesInHierarchy = new HashSet<Bundle>();
        rolesInHierarchy.add(existingRole);
        try {
            addSubtree(rolesInHierarchy, existingRole, ctx, !bottomUpMode);
        } catch (GeneralException e) {
            log.error("Failed to find roles in hierarchy", e);
        }


        return rolesInHierarchy;
    }

    public static int getMaxHierarchyDepth(Bundle existingRole, SailPointContext ctx, boolean bottomUpMode) {
        int maxDepth = 0;
        int depth = 0;

        if (bottomUpMode) {

        } else {
            List<Bundle> inheritance = existingRole.getInheritance();
            if (inheritance != null && !inheritance.isEmpty()) {
                for (Bundle parent : inheritance) {
                    depth = checkParent(0, parent, bottomUpMode, ctx);
                    if (depth > maxDepth)
                        maxDepth = depth;
                }
            }
        }

        return maxDepth;
    }

    private static int checkParent(int currentDepth, Bundle parent, boolean bottomUpMode, SailPointContext context) {
        int depth = currentDepth;
        int maxDepth = currentDepth;
        if (parent == null) {
            maxDepth = 0;
        } else {
            List<Bundle> grandparents;
            if (bottomUpMode) {
                QueryOptions includingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle[]{parent})));
                try {
                    grandparents = context.getObjects(Bundle.class, includingRoleFinder);
                } catch (GeneralException e) {
                    grandparents = null;
                    log.error("The role util failed to fetch the hierarchy for the " + parent.getName() + " role.", e);
                }
            } else {
                grandparents = parent.getInheritance();
            }

            if (grandparents != null && !grandparents.isEmpty()) {
                for (Bundle grandParent : grandparents) {
                    depth = checkParent(currentDepth + 1, grandParent, bottomUpMode, context);
                    if (depth > maxDepth)
                        maxDepth = depth;
                }
            }
        }

        return maxDepth;
    }

    // This method is recursively adds the contents of a subtree to the 
    // Set of roles in the hierarchy
    private static void addSubtree(Set<Bundle> rolesInHierarchy, Bundle treeNode, Resolver ctx, boolean bottomUpMode)
            throws GeneralException {
        rolesInHierarchy.add(treeNode);

        Collection<Bundle> superRoles;

        if (bottomUpMode) {
            superRoles = treeNode.getInheritance();
        } else {
            QueryOptions includingRoleFinder = new QueryOptions(Filter.containsAll("inheritance", Arrays.asList(new Bundle[]{treeNode})));
            superRoles = ctx.getObjects(Bundle.class, includingRoleFinder);
        }

        if (superRoles != null) {
            for (Bundle superNode : superRoles) {
                addSubtree(rolesInHierarchy, superNode, ctx, bottomUpMode);
            }
        }
    }

    public static String getReadOnlyRoleJson(Bundle role, SailPointContext ctx, Identity currentUser, TimeZone timezone, Locale locale) throws GeneralException, JSONException {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        jsonWriter.object();

        jsonWriter.key("roleId");
        jsonWriter.value(role.getId());

        jsonWriter.key("roleName");
        jsonWriter.value(role.getName());

        jsonWriter.key("nameI18n");
        jsonWriter.value(new Message(MessageKeys.NAME).getLocalizedMessage(locale, null));

        jsonWriter.key("roleDisplayName");
        if (null == role.getDisplayName()) {
            jsonWriter.value(new Message(MessageKeys.NONE).getLocalizedMessage(locale, null));
        } else {
            jsonWriter.value(role.getDisplayName());
        }

        jsonWriter.key("displayNameI18n");
        jsonWriter.value(new Message(MessageKeys.DISPLAY_NAME).getLocalizedMessage(locale, null));

        jsonWriter.key("roleOwner");
        if (role.getOwner() != null && role.getOwner().getDisplayableName() != null) {
            jsonWriter.value(role.getOwner().getDisplayableName());
        } else {
            jsonWriter.value(new Message(MessageKeys.NONE).getLocalizedMessage(locale, null));
        }

        jsonWriter.key("ownerI18n");
        jsonWriter.value(new Message(MessageKeys.OWNER).getLocalizedMessage(locale, null));

        jsonWriter.key("roleScope");
        if (role.getAssignedScope() != null && role.getAssignedScope().getDisplayableName() != null) {
            jsonWriter.value(role.getAssignedScope().getDisplayableName());
        } else {
            jsonWriter.value(new Message(MessageKeys.NONE).getLocalizedMessage(locale, null));
        }

        jsonWriter.key("scopeI18n");
        jsonWriter.value(new Message(MessageKeys.LABEL_SCOPE).getLocalizedMessage(locale, null));

        jsonWriter.key("roleType");

        String displayTypeName;
        String typeIcon = "";
        RoleTypeDefinition roleTypeDefinition = null;
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig != null) {
            roleTypeDefinition = roleConfig.getRoleType(role);
            if (roleTypeDefinition != null) {
                displayTypeName = roleTypeDefinition.getDisplayableName();
                typeIcon = roleTypeDefinition.getIcon();
            } else {
                displayTypeName = role.getType();
            }
        } else {
            displayTypeName = role.getType();
        }

        if (role.getType() != null) {
            jsonWriter.value(displayTypeName);
        } else {
            jsonWriter.value("");
        }

        boolean inScope = Authorizer.hasScopeAccess(role, ctx);

        jsonWriter.key("editable");
        // Make the role marked for deletion uneditable within the role modeler
        jsonWriter.value(!role.isPendingDelete() &&
                RoleUtil.isRoleManagementPermitted(roleTypeDefinition, currentUser) &&
                inScope);

        jsonWriter.key("inScope");
        jsonWriter.value(inScope);

        jsonWriter.key("typeIcon");
        jsonWriter.value(typeIcon);

        // This may seem repetitive, but the type above returns a type that's suitable for display in the
        // UI.  This type is needed to properly look up type definitions in the type definition store
        jsonWriter.key("typeName");
        jsonWriter.value(role.getType());

        jsonWriter.key("typeI18n");
        jsonWriter.value(new Message(MessageKeys.TYPE).getLocalizedMessage(locale, null));

        jsonWriter.key("roleDescription");
        String description = role.getDescription(locale);
        jsonWriter.value(Util.isNullOrEmpty(description) ? "" : description);

        jsonWriter.key("descriptionI18n");
        jsonWriter.value(new Message(MessageKeys.DESCRIPTION).getLocalizedMessage(locale, null));

        jsonWriter.key("roleClassification");
        List<String> classifications = role.getClassificationDisplayNames();
        if (!Util.isEmpty(classifications)) {
            jsonWriter.value(Util.listToCsv(classifications));
        } else {
            jsonWriter.value(new Message(MessageKeys.NONE).getLocalizedMessage(locale, null));
        }

        jsonWriter.key("classificationI18n");
        jsonWriter.value(new Message(MessageKeys.UI_CLASSIFICATIONS).getLocalizedMessage(locale, null));

        if (role.isActivityEnabled()) {
            jsonWriter.key("roleActivityMonitoringIsEnabled");
            jsonWriter.value(true);
            jsonWriter.key("roleActivityMonitoringEnabled");
            jsonWriter.value(new Message(MessageKeys.ROLE_ACTIVITY_MONITORING_ENABLED).getLocalizedMessage(locale, null));
        } else {
            jsonWriter.key("roleActivityMonitoringIsEnabled");
            jsonWriter.value(false);
        }

        jsonWriter.key("hasSimpleEntitlements");
        jsonWriter.value(hasSimpleEntitlements(role));

        boolean hasSimpleEntitlementsIgnoreCase = hasSimpleEntitlements(role, null, new SimpleEntitlementCriteria());
        jsonWriter.key("hasSimpleEntitlementsIgnoreCase");
        jsonWriter.value(hasSimpleEntitlementsIgnoreCase);

        if (hasSimpleEntitlementsIgnoreCase) {
            jsonWriter.key("simpleEntitlementsIgnoreCase");
            jsonWriter.value(getReadOnlySimpleEntitlementsJson(role, null, null, false, locale, new SimpleEntitlementCriteria()));
        }

        jsonWriter.key("hasSimpleIncludedEntitlements");
        jsonWriter.value(hasSimpleIncludedEntitlements(role, new StrictSimpleEntitlementCriteria()));

        // Role marked for pendingDelete requires a different color scheme
        if (role.isPendingDelete()) {
            jsonWriter.key("roleDeleted");
            jsonWriter.value(new Message(MessageKeys.ROLE_DELETED).getLocalizedMessage(locale, null));
        }

        jsonWriter.key("roleDisabled");
        if (role.isDisabled()) {
            jsonWriter.value(new Message(MessageKeys.ROLE_DISABLED).getLocalizedMessage(locale, null));
        } else {
            jsonWriter.value(new Message(MessageKeys.ROLE_ENABLED).getLocalizedMessage(locale, null));
        }
        
        if (role.isMergeTemplates()) {
            jsonWriter.key("roleMergeTemplates");
            jsonWriter.value(new Message(MessageKeys.LABEL_MERGE_TEMPLATES).getLocalizedMessage(locale, null));
        }

        AttributesInfo attributesInfo = RoleAttributesUtil.getAttributesInfo(role, locale);
        jsonWriter.key("roleExtendableAttributesWithNoCategory");
        jsonWriter.value(getAttributesWithNoCategory(attributesInfo));

        jsonWriter.key("roleExtendableAttributesByCategory");
        jsonWriter.value(getAttributesByCategory(attributesInfo));

        jsonWriter.key("missingAttributePresent");
        jsonWriter.value(attributesInfo.isMissingAttributePresent());

        // eventually will need to expose more complex filter/match list structure
        // for now assume everything can be neatly summarized in a string
        // oh, punt on this for now writing an Ext template is too painful,
        // since we're only allowing rules and short scripts in 3.0 display
        // it as a string in the data section
        if (role.getSelector() != null) {
            jsonWriter.key("roleSelector");
            jsonWriter.value(getSelectorJsonObject(role, true));
        }

        List<Bundle> permittedRoles = role.getPermits();
        if (permittedRoles != null && !permittedRoles.isEmpty()) {
            jsonWriter.key("roleHasPermits");
            jsonWriter.value(true);

            JSONArray invalidPermittedRoles = new JSONArray();
            for (Bundle permittedRole : permittedRoles) {
                if (roleConfig != null) {
                    RoleTypeDefinition roleTypeDef = roleConfig.getRoleType(permittedRole);
                    if (roleTypeDef == null || roleTypeDef.isNotPermittable()) {
                        invalidPermittedRoles.put(permittedRole.getName());
                    }
                } else {
                    // Theoretically this shouldn't ever happen because we should always have
                    // a role config, but in the event that something went horribly wrong let's
                    // assume the worst and mark everything invalid
                    invalidPermittedRoles.put(permittedRole.getName());
                }
            }

            jsonWriter.key("invalidPermits");
            jsonWriter.value(invalidPermittedRoles);
        }

        List<Bundle> requirements = role.getRequirements();
        if (requirements != null && !requirements.isEmpty()) {
            jsonWriter.key("roleHasRequirements");
            jsonWriter.value(true);

            JSONArray invalidRequirements = new JSONArray();
            for (Bundle requiredRole : requirements) {
                if (roleConfig != null) {
                    RoleTypeDefinition roleTypeDef = roleConfig.getRoleType(requiredRole);
                    if (roleTypeDef == null || roleTypeDef.isNotRequired()) {
                        invalidRequirements.put(requiredRole.getName());
                    }
                } else {
                    // Theoretically this shouldn't ever happen because we should always have
                    // a role config, but in the event that something went horribly wrong let's
                    // assume the worst and mark everything invalid
                    invalidRequirements.put(requiredRole.getName());
                }
            }

            jsonWriter.key("invalidRequirements");
            jsonWriter.value(invalidRequirements);
        }

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null && !inheritance.isEmpty()) {
            jsonWriter.key("roleHasInheritance");
            jsonWriter.value(true);

            JSONArray invalidInheritance = new JSONArray();
            for (Bundle inheritedRole : inheritance) {
                if (roleConfig != null) {
                    RoleTypeDefinition roleTypeDef = roleConfig.getRoleType(inheritedRole);
                    if (roleTypeDef == null || roleTypeDef.isNoSubs()) {
                        invalidInheritance.put(inheritedRole.getName());
                    }
                } else {
                    // Theoretically this shouldn't ever happen because we should always have
                    // a role config, but in the event that something went horribly wrong let's
                    // assume the worst and mark everything invalid
                    invalidInheritance.put(inheritedRole.getName());
                }
            }

            jsonWriter.key("invalidInheritance");
            jsonWriter.value(invalidInheritance);
        }
        String activationWarning = "";
        /** Build list of events **/
        if (role.getActivationDate() != null || role.getDeactivationDate() != null) {
            JSONArray events = new JSONArray();
            Date activation = role.getActivationDate();
            Date deactivation = role.getDeactivationDate();

            if (activation != null && activation.after(new Date())) {
                Map<String, Object> eventMap = new HashMap<String, Object>();
                eventMap.put("date", activation.getTime());
                eventMap.put("name", EventBean.ACTIVATION);
                eventMap.put("creator", role.getAttribute(RoleEditorBean.ACTIVATION_CREATOR));
                events.put(eventMap);

                /** If there is a state-change upcoming, indicate it **/
                if (role.isDisabled()) {
                    activationWarning = new Message(MessageKeys.ROLE_UPCOMING_ACTIVATION).getLocalizedMessage(locale, null);
                    jsonWriter.key("activationWarningDate");
                    jsonWriter.value(activation.getTime());
                    jsonWriter.key("activationWarningClass");
                    jsonWriter.value("green");
                }
            }

            if (deactivation != null && deactivation.after(new Date())) {
                Map<String, Object> eventMap = new HashMap<String, Object>();
                eventMap.put("date", deactivation.getTime());
                eventMap.put("name", EventBean.DEACTIVATION);
                eventMap.put("creator", role.getAttribute(RoleEditorBean.DEACTIVATION_CREATOR));
                if (activation == null || deactivation.after(activation)) {
                    events.put(eventMap);
                } else {
                    JSONObject activationMap = (JSONObject) events.get(0);
                    events.put(0, eventMap);
                    events.put(activationMap);
                }

                /** If there is a state-change upcoming, indicate it **/
                if (!role.isDisabled()) {
                    activationWarning = new Message(MessageKeys.ROLE_UPCOMING_DEACTIVATION).getLocalizedMessage(locale, null);
                    jsonWriter.key("activationWarningDate");
                    jsonWriter.value(deactivation.getTime());
                    jsonWriter.key("activationWarningClass");
                    jsonWriter.value("red");
                }
            }

            if (events.length() > 0) {
                jsonWriter.key("events");
                jsonWriter.value(events);
            }
        }

        jsonWriter.key("activationWarning");
        jsonWriter.value(activationWarning);


        jsonWriter.key("roleDirectEntitlements");
        jsonWriter.value(new JSONArray(getDirectEntitlementsJson(role, null, false)));

        jsonWriter.key("roleIncludedEntitlements");
        jsonWriter.value(new JSONArray(getIncludedEntitlementsJson(role)));

        String pendingChangeInfo;
        if (role.getPendingWorkflow() == null) {
            pendingChangeInfo = "";
        } else {
            if (!role.getPendingWorkflow().isAutoCreated()) {
                pendingChangeInfo =
                        new Message(MessageKeys.APPROVAL_OR_IMPACT_ANALYSIS_PENDING).getLocalizedMessage(locale, null);
            } else {
                pendingChangeInfo = "";
            }
        }

        jsonWriter.key("pendingChangeInfo");
        jsonWriter.value(pendingChangeInfo);

        jsonWriter.key("hasArchivedRoles");
        QueryOptions archiveFilter = new QueryOptions(Filter.eq("sourceId", role.getId()));
        int versionCount = ctx.countObjects(BundleArchive.class, archiveFilter);
        jsonWriter.value(versionCount > 0);

        JSONArray caps = buildJSONFromProvisioningPlan(role, Certification.IIQ_ATTR_CAPABILITIES);
        jsonWriter.key("capabilities");
        if (caps.length() > 0) {
            jsonWriter.value(caps);
        } else
            jsonWriter.value(null);

        JSONArray scopes = buildJSONFromProvisioningPlan(role, Certification.IIQ_ATTR_SCOPES);
        jsonWriter.key("scopes");
        if (scopes.length() > 0) {
            jsonWriter.value(scopes);
        } else {
            jsonWriter.value(null);
        }
        jsonWriter.key("roleMetrics");
        jsonWriter.value(getMetricsAsMap(roleTypeDefinition, role.getScorecard(), locale, timezone));

        jsonWriter.endObject();

        return jsonString.toString();
    }

    private static List<JSONObject> getAllowedRolesJson(Bundle role, Locale locale) throws GeneralException {

        // If the role is null, return an empty array.
        if (role == null)
            return new ArrayList<JSONObject>();

        List<JSONObject> allowedRolesList = new ArrayList<JSONObject>();

        // Add Direct allowed roles first
        addAllowedRolesToJsonList(role, "Direct", allowedRolesList, locale);

        // Then add ancestor allowed roles
        for (Bundle ancestor : Util.iterate(role.getFlattenedInheritance())) {
            addAllowedRolesToJsonList(ancestor, ancestor.getName(), allowedRolesList, locale);
        }

        return allowedRolesList;
    }

    private static void addAllowedRolesToJsonList(Bundle role, String sourceName, List<JSONObject> allowedRoles, Locale locale) {

        // If the role is null, we have nothing to add
        if (role == null)
            return;

        for (Bundle required : Util.iterate(role.getRequirements())) {
            Map<String, Object> requiredRole = new HashMap<String, Object>();
            requiredRole.put("sourceRole", sourceName);
            requiredRole.put("roleName", required.getName());
            String description = required.getDescription(locale);
            requiredRole.put("roleDescription", Util.isNullOrEmpty(description) ? "" : description);
            requiredRole.put("allowedType", "requiredRole");
            allowedRoles.add(new JSONObject(requiredRole));
        }

        for (Bundle permitted : Util.iterate(role.getPermits())) {
            Map<String, Object> permittedRole = new HashMap<String, Object>();
            permittedRole.put("sourceRole", sourceName);
            permittedRole.put("roleName", permitted.getName());
            String description = permitted.getDescription(locale);
            permittedRole.put("roleDescription", Util.isNullOrEmpty(description) ? "" : description);
            permittedRole.put("allowedType", "permittedRole");
            allowedRoles.add(new JSONObject(permittedRole));
        }
    }

    /**
     * Get allowed (required or permitted) roles for a role.
     *
     * @param role
     * @return a list of maps of allowed roles
     * @throws GeneralException
     * @throws JSONException
     */
    public static List<Map<String, Object>> getReadOnlyAllowedRoles(Bundle role, Locale locale) throws GeneralException, JSONException {

        List<JSONObject> allowedRolesJsonList = new ArrayList<JSONObject>();

        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);

        jsonWriter.object();
        jsonWriter.key("allowedroles");

        allowedRolesJsonList = getAllowedRolesJson(role, locale);

        // TODO: page it?

        jsonWriter.value(new JSONArray(allowedRolesJsonList));

        jsonWriter.endObject();

        Map<String, Object> allowedRoles = JsonHelper.mapFromJson(String.class, Object.class, jsonString.toString());

        List<Map<String, Object>> allowedRolesList = (List<Map<String, Object>>) allowedRoles.get("allowedroles");


        return allowedRolesList;

    }

    private static Collection<JSONObject> getAttributesWithNoCategory(AttributesInfo info) {

        if (info.getAttributesWithNoCategory().size() == 0) {
            return getEmptyJsonList();
        }

        List<JSONObject> attributes = new ArrayList<JSONObject>();
        for (Map<String, Object> attributeInfo : info.getAttributesWithNoCategory()) {
            attributes.add(new JSONObject(attributeInfo));
        }

        return attributes;
    }

    private static JSONObject getAttributesByCategory(AttributesInfo info) {

        if (info.getAttributesByCategory().size() == 0) {
            return getEmptyJsonObject();
        }

        return new JSONObject(info.getAttributesByCategory());
    }

    private static Collection<JSONObject> getEmptyJsonList() {
        return new ArrayList<JSONObject>();
    }

    private static JSONObject getEmptyJsonObject() {
        return new JSONObject();
    }


    public static String getGridJsonForRoles(ObjectConfig roleConfig, Iterator<Object[]> queriedRoles, int numRoleResults,
                                             SailPointContext ctx) {

        Locale locale = getDescriptionLocale(ctx);

        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            jsonWriter.key("roles");

            List<JSONObject> roleList = new ArrayList<JSONObject>();
            while (queriedRoles.hasNext()) {
                Map<String, Object> roleInfo = new HashMap<String, Object>();
                Object[] roleAttribs = queriedRoles.next();
                roleInfo.put("id", roleAttribs[0]);
                roleInfo.put("name", roleAttribs[1]);
                String displayName;
                if (roleConfig != null) {
                    RoleTypeDefinition roleTypeDef = roleConfig.getRoleType((String) roleAttribs[2]);
                    if (roleTypeDef != null) {
                        displayName = roleTypeDef.getDisplayableName();
                    } else {
                        displayName = (String) roleAttribs[2];
                    }
                } else {
                    displayName = (String) roleAttribs[2];
                }
                if (log.isDebugEnabled())
                    log.debug("displayname is " + displayName);

                roleInfo.put("roleType", displayName);

                Localizer localizer = new Localizer(ctx);
                String roleDescription = localizer.getLocalizedValue((String) roleAttribs[0], Localizer.ATTR_DESCRIPTION, locale);
                roleInfo.put("description", roleDescription);

                // check if we are getting displayableName
                if (roleAttribs.length > 3) {
                    roleInfo.put("displayableName", roleAttribs[3]);
                }

                roleList.add(new JSONObject(roleInfo));
            }
            jsonWriter.value(new JSONArray(roleList));
            jsonWriter.key("numRoleResults");
            jsonWriter.value(numRoleResults);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not convert the roles to JSON.", e);
        }

        return jsonString.toString();
    }

    public static String getRiskGridJsonForRoles(List<RoleRiskScore> roleRiskScores, int numRoleResults) {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        try {
            jsonWriter.object();
            jsonWriter.key("roles");

            List<JSONObject> roleList = new ArrayList<JSONObject>();
            for (RoleRiskScore roleRiskScore : roleRiskScores) {
                Map<String, Object> roleInfo = new HashMap<String, Object>();
                roleInfo.put("roleId", roleRiskScore.getRoleId());
                roleInfo.put("name", roleRiskScore.getName());
                // Warning: don't change roleType to plain 'type' because that causes ExtJS to misbehave
                String roleType = roleRiskScore.getRoleType();
                String displayableRoleType;
                if (roleConfig == null) {
                    displayableRoleType = roleType;
                } else {
                    RoleTypeDefinition typeDef = roleConfig.getRoleType(roleType);
                    if (typeDef == null) {
                        displayableRoleType = roleType;
                    } else {
                        displayableRoleType = typeDef.getDisplayableName();
                    }
                }
                roleInfo.put("roleType", displayableRoleType);
                roleInfo.put("riskScore", roleRiskScore.getRiskScore());
                roleInfo.put("description", roleRiskScore.getDescription());
                roleList.add(new JSONObject(roleInfo));
            }
            jsonWriter.value(new JSONArray(roleList));
            jsonWriter.key("numRoleResults");
            jsonWriter.value(numRoleResults);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not convert the roles to JSON.", e);
        }

        return jsonString.toString();
    }

    public static Map<String, Object> getMetricsAsMap(RoleTypeDefinition typeDef, RoleScorecard scorecard, Locale locale, TimeZone timezone) {
        Map<String, Object> metrics = new HashMap<String, Object>();
        Date modified;

        if (scorecard == null) {
            modified = null;
        } else {
            modified = scorecard.getModified();
            if (modified == null) {
                modified = scorecard.getCreated();
            }
        }

        if (scorecard == null || modified == null) {
            metrics.put("modified", null);
        } else {
            metrics.put("roleId", scorecard.getRole().getId());
            metrics.put("modified", Internationalizer.getLocalizedDate(modified, locale, timezone));
            if (typeDef != null && (!typeDef.isNoAutoAssignment() || !typeDef.isNoManualAssignment())) {
                metrics.put("showMembers", true);
            } else {
                metrics.put("showMembers", false);
            }
            metrics.put("members", scorecard.getMembers());
            metrics.put("membersWithAddtionalEntitlements", scorecard.getMembersWithAdditionalEntitlements());
            metrics.put("membersWithMissingRequired", scorecard.getMembersWithMissingRequiredRoles());
            if (typeDef != null && typeDef.isDetectable()) {
                metrics.put("showDetected", true);
            } else {
                metrics.put("showDetected", false);
            }
            metrics.put("detected", scorecard.getDetected());
            metrics.put("detectedExceptions", scorecard.getDetectedAsExceptions());
            metrics.put("provisionedEntitlements", scorecard.getProvisionedEntitlements());
            metrics.put("permittedEntitlements", scorecard.getPermittedEntitlements());
        }

        return metrics;
    }

    private static Collection<JSONObject> getJsonForFilter(FilterContainer rules, String roleName) {
        List<JSONObject> filtersJson = new ArrayList<JSONObject>();

        String filterString = rules.getFilterString();
        if (filterString != null && filterString.trim().length() > 0) {
            Map<String, String> ruleInfo = new HashMap<String, String>();
            ruleInfo.put("rule", filterString);

            if (roleName != null && roleName.trim().length() > 0) {
                ruleInfo.put("forRole", roleName);
            } else {
                ruleInfo.put("forRole", "");
            }
            filtersJson.add(new JSONObject(ruleInfo));
        }
        return filtersJson;
    }

    private static Collection<JSONObject> getJsonForPermissions(Collection<Permission> permissions, String roleName) {
        List<JSONObject> permissionsJson = new ArrayList<JSONObject>();

        if (permissions != null) {
            Iterator<Permission> permissionIter = permissions.iterator();

            while (permissionIter.hasNext()) {
                Permission permission = permissionIter.next();
                Map<String, String> permissionsInfo = new HashMap<String, String>();
                permissionsInfo.put("rights", Util.listToCsv(permission.getRightsList(), true));
                permissionsInfo.put("target", permission.getTarget());
                if (permission.getAnnotation() != null && permission.getAnnotation().trim().length() > 0) {
                    permissionsInfo.put("annotation", permission.getAnnotation());
                } else {
                    permissionsInfo.put("annotation", "");
                }

                if (roleName != null && roleName.trim().length() > 0) {
                    permissionsInfo.put("forRole", roleName);
                } else {
                    permissionsInfo.put("forRole", "");
                }
                permissionsJson.add(new JSONObject(permissionsInfo));
            }
        }

        return permissionsJson;
    }

    public static Map<String, Object> getSelectorInfo(Bundle role, Locale locale, boolean isRoleViewer) {
        Map<String, Object> selectorInfo = new HashMap<String, Object>();
        IdentitySelector selector = role.getSelector();

        String selectorType;
        String selectorDescription;
        String selectorContents;
        if (selector != null) {
            if (selector.getMatchExpression() != null) {
                selectorType = IdentitySelector.SELECTOR_TYPE_MATCH_LIST;
                selectorDescription = new Message(MessageKeys.SELECTOR_TYPE_MATCH).getLocalizedMessage(locale, null);
                selectorContents = selector.getMatchExpression().render();
            } else if (selector.getFilter() != null) {
                selectorType = IdentitySelector.SELECTOR_TYPE_FILTER;
                selectorDescription = new Message(MessageKeys.SELECTOR_TYPE_FILTER).getLocalizedMessage(locale, null);
                selectorContents = selector.getFilter().render();
            } else if (selector.getScript() != null) {
                selectorType = IdentitySelector.SELECTOR_TYPE_SCRIPT;
                selectorDescription = new Message(MessageKeys.SELECTOR_TYPE_SCRIPT).getLocalizedMessage(locale, null);
                selectorContents = selector.getScript().getSource();
            } else if (selector.getRule() != null) {
                selectorType = IdentitySelector.SELECTOR_TYPE_RULE;
                selectorDescription = new Message(MessageKeys.SELECTOR_TYPE_RULE).getLocalizedMessage(locale, null);
                selectorContents = selector.getRule().getName();
            } else if (selector.getPopulation() != null) {
                selectorType = IdentitySelector.SELECTOR_TYPE_POPULATION;
                selectorDescription = new Message(MessageKeys.SELECTOR_TYPE_POPULATION).getLocalizedMessage(locale, null);
                selectorContents = selector.getPopulation().getName();
            } else {
                selectorType = null;
                selectorDescription = null;
                selectorContents = null;
            }

            if (selectorType != null) {
                selectorInfo.put("roleView", isRoleViewer);
                selectorInfo.put("selectorI18n", new Message(MessageKeys.ROLE_LABEL_ASSIGNMENT_SELECTOR).getLocalizedMessage(locale, null));
                selectorInfo.put("selectorType", selectorType);
                selectorInfo.put("selectorDescription", selectorDescription);
                selectorInfo.put("selectorContents", selectorContents);
            }
        }
        return selectorInfo;
    }

    private static JSONObject getSelectorJsonObject(Bundle role, boolean isRoleViewer) {
        FacesContext context = FacesContext.getCurrentInstance();
        Locale locale = context.getViewRoot().getLocale();
        return new JSONObject(getSelectorInfo(role, locale, isRoleViewer));
    }

    private static boolean hasAnnotation(Profile profile) {
        boolean hasAnnotation = false;
        List<Permission> permissions = profile.getPermissions();

        if (permissions != null) {
            for (Permission permission : permissions) {
                if (permission.getAnnotation() != null && permission.getAnnotation().trim().length() > 0) {
                    hasAnnotation = true;
                }
            }
        }

        return hasAnnotation;
    }

    /**
     * Returns a JSONObject representing the entitlements for the specified role
     *
     * @param role            Role whose entitlements are being returned
     * @param currentProfiles An updated list of ProfileDTO objects for this role.
     *                        If this is set to null, a transient set of ProfileDTOs will be created from the role's persisted profiles.
     *                        As a rule, if edit mode is set to false this should be set to null and if edit mode is set to true the caller
     *                        should provide a list of DTOs for the edited role's current profiles
     * @param editMode        true if these entitlements are intended for editing in the current view; false if the view is read-only
     * @return JSONObject representing the entitlements for the specified role
     * @throws GeneralException If we are unable to properly fetch the entitlement info for a given profile
     */
    public static Collection<JSONObject> getDirectEntitlementsJson(Bundle role, List<ProfileDTO> currentProfiles, boolean editMode) throws GeneralException, JSONException {
        List<JSONObject> directEntitlements = new ArrayList<JSONObject>();
        if (currentProfiles == null) {
            currentProfiles = new ArrayList<ProfileDTO>();
            List<Profile> profiles = role.getProfiles();
            if (profiles != null) {
                for (Profile profile : profiles) {
                    currentProfiles.add(new ProfileDTO(profile));
                }
            }
        }

        Iterator<ProfileDTO> profileIter = currentProfiles.iterator();

        while (profileIter.hasNext()) {
            ProfileDTO currentProfile = profileIter.next();
            if (currentProfile.getProfile() != null) {
                ProfileEditBean profileEditBean = new ProfileEditBean(role, currentProfile);
                FilterContainer rule = profileEditBean.getProfileConstraints();
                directEntitlements.add(new JSONObject(getEntitlementInfo(currentProfile, rule, editMode)));
            }
        }

        removeDuplicateEntitlements(directEntitlements);

        return directEntitlements;
    }

    /**
     * Remove duplicate JSONObjects from the list.
     */
    private static void removeDuplicateEntitlements(List<JSONObject> entitlementList) throws JSONException {
        Set<String> entitlementSet = new HashSet<String>();
        Iterator<JSONObject> it = entitlementList.iterator();
        while (it.hasNext()) {
            JSONObject jo = it.next();

            String idVal = "";
            Integer profileOrd = -1;

            // remove the id value if it exists so it doesn't get stringified
            if (jo.has(ROLE_ID)) {
                // store it so we can set it back later
                idVal = jo.getString(ROLE_ID);
                jo.remove(ROLE_ID);
            }

            // remove profileOrdinal value
            if (jo.has(ROLE_PROFILE_ORDINAL)) {
                // store it so we can set it back later
                profileOrd = jo.getInt(ROLE_PROFILE_ORDINAL);
                jo.remove(ROLE_PROFILE_ORDINAL);
            }

            // stringify
            String stringValue = jo.toString();

            if (!idVal.isEmpty()) {
                // put the id value back
                jo.put(ROLE_ID, idVal);
            }

            if (profileOrd != -1) {
                // put the profile ordinal value back
                jo.put(ROLE_PROFILE_ORDINAL, profileOrd);
            }

            if (entitlementSet.contains(stringValue)) {
                it.remove();
            } else {
                entitlementSet.add(stringValue);
            }
        }
    }

    private static Map<String, Object> getEntitlementInfo(ProfileDTO profile, FilterContainer rule, boolean editMode) throws GeneralException {
        Map<String, Object> entitlementInfo = new HashMap<String, Object>();
        entitlementInfo.put("id", profile.getId());
        entitlementInfo.put("name", profile.getProfile().getName());
        entitlementInfo.put("editMode", editMode);
        entitlementInfo.put("application", profile.getApplication().getName());
        JSONArray ruleArray = new JSONArray(getJsonForFilter(rule, null));
        entitlementInfo.put("rules", ruleArray);
        JSONArray permissionArray = new JSONArray(getJsonForPermissions(profile.getPermissions(), null));
        entitlementInfo.put("permissions", permissionArray);
        entitlementInfo.put("hasAnnotation", hasAnnotation(profile.getProfile()));
        entitlementInfo.put("withRoleName", false);
        entitlementInfo.put("profileOrdinal", profile.getProfile().getProfileOrdinal());
        String description = profile.getDescription();
        if (description != null && description.trim().length() > 0) {
            entitlementInfo.put("description", profile.getDescription());
        } else {
            entitlementInfo.put("description", "");
        }
        return entitlementInfo;
    }

    private static Collection<JSONObject> getIncludedEntitlementsJson(Bundle role) {
        List<JSONObject> includedEntitlements = new ArrayList<JSONObject>();

        Map<String, Map<String, Profile>> profilesByApp = mapIncludedProfiles(role);

        Set<String> appNames = profilesByApp.keySet();

        if (appNames != null && !appNames.isEmpty()) {
            Iterator<String> appNameIter = appNames.iterator();

            while (appNameIter.hasNext()) {
                String appName = appNameIter.next();
                Map<String, Profile> profilesByRole = profilesByApp.get(appName);
                List<JSONObject> ruleJson = new ArrayList<JSONObject>();
                List<JSONObject> permissionsJson = new ArrayList<JSONObject>();
                List<String> roleNames = new ArrayList<String>(profilesByRole.keySet());
                if (roleNames.size() > 1) {
                    // The profile ordinal is part of the role name.  This ensures they are listed sequentially
                    Collections.sort(roleNames);
                }
                boolean hasAnnotation = false;

                // Aggregate the profiles for this application across the included roles
                for (String roleName : roleNames) {
                    Profile profileForRole = profilesByRole.get(roleName);
                    ProfileEditBean profileEditBean = new ProfileEditBean(role, new ProfileDTO(profileForRole));
                    FilterContainer rule = profileEditBean.getProfileConstraints();
                    ruleJson.addAll(getJsonForFilter(rule, roleName));
                    permissionsJson.addAll(getJsonForPermissions(profileForRole.getPermissions(), roleName));
                    hasAnnotation |= hasAnnotation(profileForRole);
                }

                Map<String, Object> entitlementsInfo = new HashMap<String, Object>();
                entitlementsInfo.put("id", "");
                entitlementsInfo.put("name", "");
                entitlementsInfo.put("editMode", false);
                entitlementsInfo.put("application", appName);
                entitlementsInfo.put("rules", new JSONArray(ruleJson));
                entitlementsInfo.put("permissions", new JSONArray(permissionsJson));
                entitlementsInfo.put("hasAnnotation", hasAnnotation);
                entitlementsInfo.put("withRoleName", true);
                entitlementsInfo.put("description", "");
                entitlementsInfo.put("profileOrdinal", 0); // profile is captured in the application name.  this is a holder to fit the template

                includedEntitlements.add(new JSONObject(entitlementsInfo));
            }
        }

        return includedEntitlements;
    }

    /**
     * Recursively aggregate all of the "included" profiles into a Map (keyed by application)
     * of Maps (keyed by role name) of Profiles
     *
     * @param role The role that we are looking for included profiles
     * @return
     */
    private static Map<String, Map<String, Profile>> mapIncludedProfiles(Bundle role) {
        Map<String, Map<String, Profile>> mapsByApp = new HashMap<String, Map<String, Profile>>();

        addProfilesForIncludedRoles(mapsByApp, role.getInheritance());

        return mapsByApp;
    }

    private static void addProfilesForIncludedRoles(Map<String, Map<String, Profile>> mapsByApp, Collection<Bundle> includedRoles) {
        if (includedRoles != null && !includedRoles.isEmpty()) {
            for (Bundle includedRole : includedRoles) {
                if (includedRole.getProfiles() != null && !includedRole.getProfiles().isEmpty()) {
                    for (Profile includedProfile : includedRole.getProfiles()) {
                        String appName = includedProfile.getApplication().getName();


                        Map<String, Profile> profileMapForApp = mapsByApp.get(appName);
                        if (profileMapForApp == null) {
                            profileMapForApp = new HashMap<String, Profile>();
                            mapsByApp.put(appName, profileMapForApp);
                        }

                        int ordinal = includedProfile.getProfileOrdinal();
                        String roleName = includedRole.getName();
                        if (ordinal > 0) {
                            roleName += " (" + ordinal + ")";
                        }
                        profileMapForApp.put(roleName, includedProfile);
                    }
                }
                //recursive call.  Should not be affected by whether the included role has a profile.
                addProfilesForIncludedRoles(mapsByApp, includedRole.getInheritance());
            }
        }
    }

    public static void updateViewerState(ExtState stateObj, Identity loggedInUser, String propertyToSave, String valueToSet)
            throws GeneralException {

        String state = stateObj.getState();

        if (state == null)
            state = "";

        String newState = updateValueInState(state, propertyToSave, valueToSet);
        stateObj.setState(newState);
    }


    /**
     * This method grabs string values out of the tree state.  Note that it is very simpleminded
     * in comparison to the fully functional ext state utility and will not work for all
     * state objects; it only supports the datatypes found in the role viewer state, no more,
     * and no less.  I just didn't feel up to porting ext's javascript code over to Java in
     * its entirety. --Bernie
     *
     * @param state State from which we want to extract the value
     * @param key   Name of the property whose value we want to extract
     * @return value of the property in the state
     */
    public static String extractValueFromState(String state, String key) {
        // Undo the colon encoding that occurred so that we can extract our string
        String stateWithColons = state.replaceAll("%253A", ":").replaceAll("%3A", ":");
        String startOfKeyDelimiter = key + "%3Ds:";
        String value;

        // Set the mode
        int startOfVal = stateWithColons.indexOf(startOfKeyDelimiter) + startOfKeyDelimiter.length();
        int endOfVal = stateWithColons.indexOf("%", startOfVal);

        if (startOfVal > startOfKeyDelimiter.length() && endOfVal > 0)
            value = stateWithColons.substring(startOfVal, endOfVal);
        else if (startOfVal > startOfKeyDelimiter.length()) {
            value = stateWithColons.substring(startOfVal);
        } else {
            value = null;
        }

        return value;
    }

    /**
     * This does the reverse of {@link #extractValueFromState(String, String)}.  In other words,
     * it finds the location of the given property, excises its current value, and replaces it
     * with an encoded version of the given value.  Note that the encoding is not fully functional
     * and only supports the special characters found within a viewer state (specifically the ':')
     *
     * @param state    State string that is being updated
     * @param key      The property that needs to be updated
     * @param newValue The updated property's new value
     * @return an updated copy of the state string
     * @see #extractValueFromState(String state, String key)
     */
    public static String updateValueInState(String state, String key, String newValue) {
        // Put the state in a more manageable form
        StringBuffer stateEditor = new StringBuffer(state.replaceAll("%253A", ":").replaceAll("%3A", ":"));
        String startOfKeyDelimiter = key + "%3Ds:";
        int startOfVal = stateEditor.indexOf(startOfKeyDelimiter) + startOfKeyDelimiter.length();
        int endOfVal = stateEditor.indexOf("%", startOfVal);

        if (startOfVal > startOfKeyDelimiter.length() && endOfVal > 0) {
            stateEditor.delete(startOfVal, endOfVal);
        } else if (startOfVal > startOfKeyDelimiter.length()) {
            stateEditor.delete(startOfVal, Integer.MAX_VALUE);
        } else {
            // If neither of the above apply, the value was not in the state to begin with, so we should add it.
            if (stateEditor.indexOf("o:") == -1) {
                // There is not even a state yet
                stateEditor.append("o%3A").append(startOfKeyDelimiter);
            } else {
                // There is a state, but our property is not in it yet
                stateEditor.append("%5E").append(startOfKeyDelimiter);
            }
        }

        // Now that we're all set, let's try it again
        startOfVal = stateEditor.indexOf(startOfKeyDelimiter) + startOfKeyDelimiter.length();
        stateEditor.insert(startOfVal, newValue);

        // Reapply the encoding for colons
        String unencodedState = stateEditor.toString();
//        return unencodedState.replaceAll(":", "%253A");
        return unencodedState.replaceAll(":", "%3A");
    }

    /**
     * Returns the page on which the specified role can be found within the specified list of roles
     *
     * @param selectedRole Role that we want to page to
     * @param roleList     List within which the role can be found
     * @param pageSize     The number of roles that are displayed per page
     */
    public static int getPageForRole(Bundle selectedRole, List<Bundle> roleList, int pageSize) {
        final int currentPage;

        if (selectedRole == null) {
            currentPage = 1;
        } else {
            int selectedIndex = roleList.indexOf(selectedRole);
            if (selectedIndex < 0)
                selectedIndex = 0;

            currentPage = selectedIndex / pageSize + 1;
        }

        return currentPage;
    }

    /**
     * This method performs basic validation on a role and returns a List of errors
     *
     * @param role                 The role that is being validated
     * @param attributes           if it is present use the attributes here instead of in the role object
     * @param attributeDefinitions list of attribute definition that are valid for this role
     * @param validatingPage       The bean for the page that is attempting to validate the role
     * @return A List of Message objects containing one entry for every error message that was generated during
     * the course of validation.  If no errors are found an empty List is returned.
     * @throws GeneralException
     */
    public static List<Message> validateBasicRole(Bundle role, Attributes<String, Object> attributes, List<ObjectAttribute> attributeDefinitions, BaseBean validatingPage) throws GeneralException {
        List<Message> errors = new ArrayList<Message>();

        if (role.getName() == null) {
            errors.add(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED));
        } else {
            //IIQTC-82 :- We are now honoring what we stated in documentation IdentityIQ_Administration_Guide.pdf
            //Note: Role names with single quotation marks, double quotation marks, or commas are not supported.
            if (!WebUtil.isSafeValue(role.getName()) || role.getName().contains(",") ) {
                errors.add(new Message(Message.Type.Error, MessageKeys.NAME_UNSAFE));
            }
        }

        try {
            ObjectUtil.checkIllegalRenameOrNewName(validatingPage.getContext(), role);
        } catch (GeneralException e) {
            errors.add(new Message(Message.Type.Error, MessageKeys.NAME_ALREADY_EXISTS, new Object[]{new Message(MessageKeys.ROLE), role.getName()}));
        }

        if (role.getOwner() == null) {
            role.setOwner(validatingPage.getLoggedInUser());
        }

        Attributes<String, Object> toCheck = (attributes == null) ? role.getAttributes() : attributes;

        // Bug26548 - We need to make sure attributes to check aren't on the disallowed attributes list
        List<String> disallowedAttributes = null;
        RoleTypeDefinition typeDefinition = ObjectConfig.getObjectConfig(Bundle.class).getRoleType(role);
        if (typeDefinition != null) {
            disallowedAttributes = typeDefinition.getDisallowedAttributes();
        }
        if (disallowedAttributes != null && !disallowedAttributes.isEmpty()) {
            if (toCheck != null && !toCheck.isEmpty()) {
                List<String> attributeNames = toCheck.getKeys();
                for (String attributeName : attributeNames) {
                    if (disallowedAttributes.contains(attributeName)) {
                        toCheck.remove(attributeName);
                    }
                }
            }
        }

        List<String> invalidAttributes = ObjectUtil.getInvalidAttributes(toCheck, attributeDefinitions, ObjectConfig.getObjectConfig(Bundle.class), validatingPage.getLocale());
        if (invalidAttributes != null && !invalidAttributes.isEmpty()) {
            errors.add(new Message(Message.Type.Error, MessageKeys.ERR_ROLE_ATTRIBUTES_REQUIRED, Util.listToCsv(invalidAttributes)));
        }

        return errors;
    }

    /**
     * This method launches a role approval workflow and returns the message that results from the attempt
     *
     * @param role                The role whose approval is being launched
     * @param pendingWorkflowCase A workflowcase that is pending on a role, if one exists.  Otherwise this should be set to null
     * @param launchingPage       The bean that is attempting to launch the approval
     * @return A Message object representing the results of the approval launch
     * @throws GeneralException if the RoleLifecycler generates errors
     */
    public static Message launchWorkflow(Bundle role, WorkflowCase pendingWorkflowCase, BaseBean launchingPage)
            throws GeneralException {
        RoleLifecycler rlc = new RoleLifecycler(launchingPage.getContext());
        boolean displayApprovalMessage;

        if (pendingWorkflowCase == null) {
            String approvalId = rlc.approve(role);
            displayApprovalMessage = approvalId != null;
        } else {
            rlc.updateApproval(pendingWorkflowCase, role);
            displayApprovalMessage = true;
        }

        Message result;

        if (displayApprovalMessage) {
            result = new Message(Message.Type.Info, MessageKeys.WORKFLOW_ROLE_APPROVAL_SUBMITTED_SUCCESSFULLY, new Object[]{role.getName()});
        } else {
            result = new Message(Message.Type.Info, MessageKeys.ROLE_SAVED_SUCCESSFULLY, new Object[]{role.getName()});
        }

        return result;
    }

    /**
     * Get RoleTypeDefinitions for the given role type.
     *
     * @param roleType
     * @return
     * @throws GeneralException
     */
    private static List<RoleTypeDefinition> getRoleTypes(RoleType roleType) throws GeneralException {
        List<RoleTypeDefinition> organizationalRoleTypes = new ArrayList<RoleTypeDefinition>();

        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        List<RoleTypeDefinition> roleTypeDefs = roleConfig.getRoleTypesList();
        if (roleTypeDefs != null) {
            for (RoleTypeDefinition roleTypeDef : roleTypeDefs) {
                if (isType(roleType, roleTypeDef))
                    organizationalRoleTypes.add(roleTypeDef);
            }
        }

        return organizationalRoleTypes;
    }

    /**
     * True if the given RoleTypeDefinition matches the role type.
     *
     * @param roleType
     * @param roleTypeDef
     * @return
     */
    private static boolean isType(RoleType roleType, RoleTypeDefinition roleTypeDef) {

        if (roleTypeDef == null)
            return false;

        switch (roleType) {
            case ITRole:
                return !roleTypeDef.isNoProfiles();
            case BusinessRole:
                return !roleTypeDef.isNoSupers();
            case OrganizationalRole:
                return !roleTypeDef.isNoSupers() && !roleTypeDef.isNoSubs();
            default:
                throw new IllegalArgumentException("Unhandled role type");
        }
    }

    /**
     * Get select list populated with roles of the given type which may be managed
     * by the given user.
     *
     * @param roleType
     * @param user
     * @param locale
     * @return
     * @throws GeneralException
     */
    public static List<SelectItem> getRoleTypeSelectList(RoleType roleType, Identity user, Locale locale)
            throws GeneralException {
        List<SelectItem> items = new ArrayList<SelectItem>();

        List<RoleTypeDefinition> types = getRoleTypes(roleType);
        if (types != null && !types.isEmpty()) {
            for (RoleTypeDefinition def : types) {
                if (isRoleManagementPermitted(def, user)) {
                    String desc = Internationalizer.getMessage(def.getDisplayableName(), locale);
                    items.add(new SelectItem(def.getName(), desc != null ? desc : def.getDisplayableName()));
                }
            }
        }

        return items;
    }

    /**
     * True if the given identity may edit roles of the given role type.
     *
     * @param definition
     * @param user
     * @return
     */
    public static boolean isRoleManagementPermitted(RoleTypeDefinition definition, Identity user) {

        // This check would also occur Authorizer.hasAccess, but we need to do it
        // here independently before we check for SPRight.ManageRole
        if (Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()))
            return true;

        Collection<String> userRights = user.getCapabilityManager().getEffectiveFlattenedRights();
        if (userRights == null || !userRights.contains(SPRight.ManageRole))
            return false;

        if (definition == null)
            return true;

        return Authorizer.hasAccess(user.getCapabilityManager().getEffectiveCapabilities(), userRights, definition.getRights());
    }

    private static JSONArray buildJSONFromProvisioningPlan(Bundle role, String key) throws GeneralException {
        JSONArray list = new JSONArray();
        Map<String, List<String>> values = getValuesFromProvisioningPlan(role);
        if ((values != null) && (values.get(key) != null)) {
            List<String> objs = values.get(key);
            for (String obj : objs) {
                Map<String, Object> o = new HashMap<String, Object>();
                o.put("name", obj);
                list.put(o);
            }
        }
        return list;
    }

    public static boolean hasSimpleEntitlements(Bundle role) {
        return hasSimpleEntitlements(role, null, new StrictSimpleEntitlementCriteria());
    }

    public static boolean hasSimpleEntitlements(Bundle role, List<ProfileDTO> currentProfiles) {
        return hasSimpleEntitlements(role, currentProfiles, new StrictSimpleEntitlementCriteria());
    }

    public static boolean hasSimpleEntitlements(Bundle role, boolean allowIgnoreCase) {
        SimpleEntitlementCriteria criteria = (allowIgnoreCase) ? new SimpleEntitlementCriteria() : new StrictSimpleEntitlementCriteria();
        return hasSimpleEntitlements(role, null, criteria);
    }

    /**
     * @return true if included entitlements are an and-ed list of equals/containsAll
     */
    private static boolean hasSimpleIncludedEntitlements(Bundle role, SimpleEntitlementCriteria criteria) {
        if (role == null || role.getProfiles() == null) {
            // It's a new role, so assume simple.
            return true;
        }

        Map<String, Map<String, Profile>> profilesByApp = mapIncludedProfiles(role);

        Set<String> appNames = profilesByApp.keySet();

        if (appNames != null && !appNames.isEmpty()) {
            for (String appName : appNames) {
                Map<String, Profile> profileMap = profilesByApp.get(appName);
                Collection<Profile> profiles = profileMap.values();
                for (Profile profile : profiles) {
                    if (!Util.isEmpty(profile.getPermissions())) {
                        return false;
                    }
                    if (!Util.isNullOrEmpty(profile.getDescription())) {
                        return false;
                    }

                    Application app = profile.getApplication();
                    for (Filter filter : profile.getConstraints()) {
                        if (filter instanceof Filter.CompositeFilter) {
                            if (!criteria.isSimpleCompositeFilter((Filter.CompositeFilter)filter, app)) {
                                return false;
                            }
                        }
                        else if (filter instanceof Filter.LeafFilter) {
                            if (!criteria.isSimpleLeafFilter((Filter.LeafFilter)filter, app)) {
                                return false;
                            }
                        } else {
                            // Should not be able to get here unless a new subclass of Filter is invented; assume not simple
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get any SailPoint specific information specifically Capabiltiies and Scopes requests out of
     * the provisioning plan.
     */
    public static Map<String, List<String>> getValuesFromProvisioningPlan(Bundle role) throws GeneralException {

        Map<String, List<String>> values = new HashMap<String, List<String>>();

        ProvisioningPlan plan = role.getProvisioningPlan();
        if (role == null || plan == null)
            return values;

        AccountRequest iiq = plan.getIIQAccountRequest();
        if (iiq != null) {
            List<AttributeRequest> attributeRequests = iiq.getAttributeRequests();
            if (Util.size(attributeRequests) > 0) {
                for (AttributeRequest attrRequest : attributeRequests) {
                    String name = attrRequest.getName();
                    if (attrRequest.getOperation().equals(Operation.Add)) {
                        if (name.equals(Certification.IIQ_ATTR_CAPABILITIES)) {
                            List<String> caps = Util.asList(attrRequest.getValue());
                            if (Util.size(caps) > 0) {
                                values.put(Certification.IIQ_ATTR_CAPABILITIES, Util.asList(attrRequest.getValue()));
                            }
                        } else if (name.equals(Certification.IIQ_ATTR_SCOPES)) {
                            List<String> scopes = Util.asList(attrRequest.getValue());
                            if (Util.size(scopes) > 0) {
                                List<String> displayablePaths = new ArrayList<String>();
                                for (String id : scopes) {
                                    Scope scope = SailPointFactory.getCurrentContext().getObjectById(Scope.class, id);
                                    if (scope != null)
                                        displayablePaths.add(scope.getDisplayablePath());
                                }
                                values.put(Certification.IIQ_ATTR_SCOPES, displayablePaths);
                            }
                        }
                    }
                }
            }
        }
        return values;
    }

    /**
     * Get the controlled scopes from the provisioning plan as Scope objects.  We can do this
     * with getValuesFromProvisioningPlan, but the scopes are queried as scope objects and then
     * converted to strings, only to be converted back into Scopes (as would happen
     * in RoleEditorBean.getControlledScopes).  Scopes as strings are difficult to deal with because
     * only the entire hierarchy is unique per given scope, not the scope name.
     */
    public static List<Scope> getControlledScopesFromProvisioningPlan(Bundle role) throws GeneralException {
        List<Scope> scopes = new ArrayList<Scope>();

        ProvisioningPlan plan = role.getProvisioningPlan();
        if (role == null || plan == null)
            return null;

        AccountRequest iiq = plan.getIIQAccountRequest();
        if (iiq != null) {
            List<AttributeRequest> attributeRequests = iiq.getAttributeRequests();
            if (Util.size(attributeRequests) > 0) {
                for (AttributeRequest attrRequest : attributeRequests) {
                    String name = attrRequest.getName();
                    if (attrRequest.getOperation().equals(Operation.Add)) {
                        if (name.equals(Certification.IIQ_ATTR_SCOPES)) {
                            List<String> scopeIds = Util.asList(attrRequest.getValue());
                            if (Util.size(scopeIds) > 0) {
                                for (String id : scopeIds) {
                                    Scope scope = SailPointFactory.getCurrentContext().getObjectById(Scope.class, id);
                                    if (scope != null)
                                        scopes.add(scope);
                                }
                            }
                        }
                    }
                }
            }
        }
        return scopes;
    }

    /**
     * Adds all the roles required by any role in the specified role's hierarchy to the specified requiredRoles Set.
     * Useful in tasks like the RoleMetadator where we want to find required roles across multiple assigned roles
     *
     * @param role          Bundle whose required roles are being added
     * @param requiredRoles Set of roles that we're appending the required roles to
     * @param examinedRoles Set of roles that have already been processed.  This is useful both as an optimization
     *                      as well as a means of avoiding daisy-chain recursion
     */
    public static void addRequiredRoles(Bundle role, Set<Bundle> requiredRoles, Set<String> examinedRoles) {
        if (examinedRoles == null) {
            examinedRoles = new HashSet<String>();
        }

        if (role != null && !examinedRoles.contains(role.getId())) {
            Collection<Bundle> currentRequiredRoles = role.getFlattenedRequirements(examinedRoles);
            if (currentRequiredRoles != null && !currentRequiredRoles.isEmpty()) {
                for (Bundle requiredRole : currentRequiredRoles) {
                    requiredRoles.add(requiredRole);
                }
                examinedRoles.add(role.getId());
            }
        }
    }

    /**
     * Adds all the roles permitted by any role in the specified role's hierarchy to the specified permittedRoles Set.
     * Useful in tasks like the RoleMetadator where we want to find permitted roles across multiple assigned roles
     *
     * @param role           Bundle whose permitted roles are being added
     * @param permittedRoles Set of roles that we're appending the permitted roles to
     * @param examinedRoles  Set of roles that have already been processed.  This is useful both as an optimization
     *                       as well as a means of avoiding daisy-chain recursion
     */
    public static void addPermittedRoles(Bundle role, Set<Bundle> permittedRoles, Set<String> examinedRoles) {
        if (examinedRoles == null) {
            examinedRoles = new HashSet<String>();
        }

        if (role != null && !examinedRoles.contains(role.getId())) {
            Collection<Bundle> currentPermittedRoles = role.getFlattenedPermits(examinedRoles);
            if (currentPermittedRoles != null && !currentPermittedRoles.isEmpty()) {
                for (Bundle permittedRole : currentPermittedRoles) {
                    permittedRoles.add(permittedRole);
                }
                examinedRoles.add(role.getId());
            }
        }
    }

    /**
     * @param role
     * @param context
     * @return A quoted CSV of the specified role's ID along with the IDs of all of its descendants in the inheritance tree
     */
    public static String getRoleIdsInHierarchy(Bundle role, Resolver context) {
        Set<String> roleIdsInHierarchy = getRoleIdsInHierarchyAsSet(role, context);
        return Util.listToQuotedCsv(Arrays.asList(roleIdsInHierarchy.toArray()), '\'', true, false);
    }

    /**
     * @param role
     * @param context
     * @return A Set<String> of the specified role's ID along with the IDs of all of its descendants in the inheritance tree
     */
    public static Set<String> getRoleIdsInHierarchyAsSet(Bundle role, Resolver context) {
        Set<Bundle> rolesInHierarchy = getRolesInHierarchy(role, context, true);
        Set<String> roleIdsInHierarchy = new HashSet<String>();
        if (!rolesInHierarchy.isEmpty()) {
            for (Bundle roleInHierarchy : rolesInHierarchy) {
                roleIdsInHierarchy.add(roleInHierarchy.getId());
            }
        }

        return roleIdsInHierarchy;
    }

    /**
     * @param role
     * @param context
     * @return A quoted CSV of the specified role's ID along with the IDs of all of its descendants in the inheritance tree
     */
    public static String getRoleNamesInHierarchy(Bundle role, Resolver context) {
        Set<String> roleNamesInHierarchy = getRoleNamesInHierarchyAsSet(role, context);
        return Util.listToQuotedCsv(Arrays.asList(roleNamesInHierarchy.toArray()), '\'', true, false);
    }

    /**
     * @param role
     * @param context
     * @return A Set<String> of the specified role's ID along with the IDs of all of its descendants in the inheritance tree
     */
    public static Set<String> getRoleNamesInHierarchyAsSet(Bundle role, Resolver context) {
        Set<Bundle> rolesInHierarchy = getRolesInHierarchy(role, context, true);
        Set<String> roleNamesInHierarchy = new HashSet<String>();
        if (!rolesInHierarchy.isEmpty()) {
            for (Bundle roleInHierarchy : rolesInHierarchy) {
                roleNamesInHierarchy.add(roleInHierarchy.getName());
            }
        }

        return roleNamesInHierarchy;
    }

    public static String getReadOnlySimpleEntitlementsJson(Bundle role, Map requestParams, List<ProfileDTO> currentProfiles, boolean editMode, Locale locale, SimpleEntitlementCriteria criteria) throws GeneralException, JSONException {

        if (!hasSimpleEntitlements(role, currentProfiles, criteria)) {
            // Attempted to retrieve simple entitlements from a role with advanced entitlements. This might happen
            // if the UI is building the simple entitlements grid in a card panel but not intending to display it, so
            // we can log it to debug and safe return an empty JSON object;
            log.debug("Attempted to retrieve simple entitlements from a role with advanced entitlements.");
            return "{}";
        }

        if (currentProfiles == null) {
            currentProfiles = new ArrayList<ProfileDTO>();
            List<Profile> profiles = role.getProfiles();
            if (profiles != null) {
                for (Profile profile : profiles) {
                    currentProfiles.add(new ProfileDTO(profile));
                }
            }
        }

        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);

        jsonWriter.object();

        jsonWriter.key("entitlements");

        List<JSONObject> entitlementList = new ArrayList<JSONObject>();
        for (ProfileDTO profile : currentProfiles) {
            for (Filter filter : profile.getConstraints()) {
                if (filter instanceof Filter.CompositeFilter) {
                    entitlementList.addAll(getSimpleCompositeJson((Filter.CompositeFilter) filter, profile.getApplication(), role.getDisplayableName(), locale));
                } else if (filter instanceof Filter.LeafFilter) {
                    entitlementList.addAll(getSimpleLeafJson((Filter.LeafFilter) filter, profile.getApplication(), role.getDisplayableName(), locale));
                }
            }
        }

        //remove duplicates
        removeDuplicateEntitlements(entitlementList);

        entitlementList = sortSimpleEntitlements(entitlementList, requestParams);
        List<JSONObject> entitlementPageList = getSimpleEntitlementsPage(entitlementList, requestParams);

        // Write the list and size
        jsonWriter.value(new JSONArray(entitlementPageList));
        jsonWriter.key("numEntitlements");
        jsonWriter.value(entitlementList.size());
        jsonWriter.endObject();

        return jsonString.toString();
    }

    static private List<JSONObject> sortSimpleEntitlements(List<JSONObject> entitlementList, Map requestParams) {
        if (requestParams == null) {
            return entitlementList;
        }
        // If necessary, sort them while they're still in a Java list.
        String orderBy = null;
        String sortDir = null;
        String sortStr = (String) requestParams.get("sort");

        if (sortStr != null && sortStr.startsWith("[")) {
            //this is used in readOnlySimpleEntitlementsJSON
            try {
                JSONArray sortArray = new JSONArray(sortStr);
                JSONObject sortObject = sortArray.getJSONObject(0);
                orderBy = sortObject.getString("property");
                sortDir = sortObject.getString("direction");
            } catch (Exception e) {
                log.debug("Invalid sort input.");
            }
        } else {
            //this is used in editing role : simpleEntitlementsQuery
            orderBy = sortStr;
            sortDir = (String) requestParams.get("dir");
        }

        if (orderBy != null) {
            Collections.sort(entitlementList, new I18NJSONObjectComparator(orderBy));
        }
        if (sortDir != null && sortDir.equals("DESC")) {
            Collections.reverse(entitlementList);
        }

        return entitlementList;
    }

    static private List<JSONObject> getSimpleEntitlementsPage(List<JSONObject> entitlementList, Map requestParams) {
        if (requestParams == null) {
            return entitlementList;
        }

        int start = Util.atoi((String)requestParams.get("start"));
        int limit = WebUtil.getResultLimit(Util.atoi((String)requestParams.get("limit")));
        int end = (start + limit < entitlementList.size()) ? start + limit : entitlementList.size();

        // If end < start, we're processing this after a delete; return an empty list and let the client deal.
        if (end < start) {
            return Collections.<JSONObject>emptyList();
        }

        return entitlementList.subList(start, end);
    }

    public static String getReadOnlySimpleIncludedEntitlementsJson(Bundle role, Map requestParams, SailPointContext ctx, Identity currentUser, TimeZone timezone, Locale locale, SimpleEntitlementCriteria criteria) throws GeneralException, JSONException {
        if (!hasSimpleIncludedEntitlements(role, criteria)) {
            // Attempted to retrieve simple included entitlements from a role with advanced entitlements. This might happen
            // if the UI is building the simple entitlements grid in a card panel but not intending to display it, so
            // we can log it to debug and safe return an empty JSON object;
            log.debug("Attempted to retrieve simple included entitlements from a role with advanced entitlements.");
            return "{}";
        }

        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);

        jsonWriter.object();

        jsonWriter.key("entitlements");

        List<JSONObject> includedEntitlementList = new ArrayList<JSONObject>();

        Map<String, Map<String, Profile>> profilesByApp = mapIncludedProfiles(role);

        for (Map<String, Profile> profileMap : profilesByApp.values()) {
            for (Map.Entry<String, Profile> row : profileMap.entrySet()) {
                Profile profile = row.getValue();
                String roleName = row.getKey();
                for (Filter filter : profile.getConstraints()) {
                    if (filter instanceof Filter.CompositeFilter) {
                        includedEntitlementList.addAll(getSimpleCompositeJson((Filter.CompositeFilter) filter, profile.getApplication(), profile.getBundle().getDisplayableName(), locale));
                    } else if (filter instanceof Filter.LeafFilter) {
                        includedEntitlementList.addAll(getSimpleLeafJson((Filter.LeafFilter) filter, profile.getApplication(), profile.getBundle().getDisplayableName(), locale));
                    }
                }
            }
        }

        includedEntitlementList = sortSimpleEntitlements(includedEntitlementList, requestParams);
        List<JSONObject> entitlementPageList = getSimpleEntitlementsPage(includedEntitlementList, requestParams);

        jsonWriter.value(new JSONArray(entitlementPageList));
        jsonWriter.key("numEntitlements");
        jsonWriter.value(includedEntitlementList.size());
        jsonWriter.endObject();

        return jsonString.toString();
    }

    private static List<JSONObject> getSimpleCompositeJson(Filter.CompositeFilter filter, Application application, String roleName, Locale locale) throws GeneralException {
        // If it's not an AND-ed expression, it's not simple.
        if (filter.getOperation() != Filter.BooleanOperation.AND) {
            throw new GeneralException("Could not get simple composite JSON: not an AND composite");
        }
        // If any children are not simple leaf filters, it's not simple.
        List<JSONObject> leafList = new ArrayList<JSONObject>();
        for (Filter child : filter.getChildren()) {
            if (child instanceof Filter.CompositeFilter) {
                throw new GeneralException("Could not get simple composite JSON: child was not a leaf");
            }
            leafList.addAll(getSimpleLeafJson((Filter.LeafFilter) child, application, roleName, locale));
        }
        return leafList;
    }

    private static List<JSONObject> getSimpleLeafJson(Filter.LeafFilter filter, Application application, String roleName, Locale locale) throws GeneralException {
        // If the operation is not EQ or CONTAINS_ALL, it's not simple
        if (filter.getOperation() != Filter.LogicalOperation.EQ &&
                filter.getOperation() != Filter.LogicalOperation.CONTAINS_ALL) {
            throw new GeneralException("Could not get simple leaf JSON: not an EQ or CONTAINS_ALL operation");
        }

        List<JSONObject> leafList = new ArrayList<JSONObject>();

        Object value = filter.getValue();
        if (value instanceof List) {
            for (Object o : (Collection) value) {
                LeafFilterEntitlement entitlement = getLeafFilterEntitlement(application, roleName, filter, o, locale);
                leafList.add(new JSONObject(entitlement));
            }
        } else {
            LeafFilterEntitlement entitlement = getLeafFilterEntitlement(application, roleName, filter, value, locale);
            leafList.add(new JSONObject(entitlement));
        }

        return leafList;
    }

    private static LeafFilterEntitlement getLeafFilterEntitlement(Application application, String roleName, Filter.LeafFilter filter, Object value, Locale locale) {
        LeafFilterEntitlement entitlement = new LeafFilterEntitlement();
        entitlement.setApplicationName(application.getName());
        entitlement.setProperty(filter.getProperty());
        entitlement.setValue(value.toString());
        entitlement.setRoleName(roleName);

        Explanator.Explanation explanation = Explanator.get(application.getId(), filter.getProperty(), value.toString());
        if (explanation != null) {
            // IIQETN-6617: Applied HTML 'unescape' change to 'display value'. This change will eliminate UI errors
            // when a JSON object is called and the attributes are displayed
            entitlement.setDisplayValue(WebUtil.stripHTML(explanation.getDisplayValue(), false));
            entitlement.setDescription(explanation.getDescription(locale));

            if (explanation.getClassificationDisplayableNames() != null) {
                entitlement.setClassifications(Util.listToCsv(explanation.getClassificationDisplayableNames()));
            }
        } else {
            entitlement.setDisplayValue(entitlement.getValue());
        }

        return entitlement;
    }

    /**
     * @return true if entitlements are an and-ed list of equals/containsAll
     */
    public static boolean hasSimpleEntitlements(Bundle role, List<ProfileDTO> currentProfiles, SimpleEntitlementCriteria criteria) {
        if (role == null || role.getProfiles() == null) {
            // It's a new role, so assume simple.
            return true;
        }

        if (currentProfiles == null) {
            currentProfiles = new ArrayList<ProfileDTO>();
            List<Profile> profiles = role.getProfiles();
            if (profiles != null) {
                for (Profile profile : profiles) {
                    currentProfiles.add(new ProfileDTO(profile));
                }
            }
        }

        for (ProfileDTO profile : currentProfiles) {
            if (!Util.isEmpty(profile.getPermissions())) {
                return false;
            }
            if (!Util.isNullOrEmpty(profile.getDescription())) {
                return false;
            }
            Application app = profile.getApplication();
            for (Filter filter : profile.getConstraints()) {
                if (filter instanceof Filter.CompositeFilter) {
                    if (!criteria.isSimpleCompositeFilter((Filter.CompositeFilter)filter, app)) {
                        return false;
                    }
                }
                else if (filter instanceof Filter.LeafFilter) {
                    if (!criteria.isSimpleLeafFilter((Filter.LeafFilter)filter, app)) {
                        return false;
                    }
                } else {
                    // Should not be able to get here unless a new subclass of Filter is invented; assume not simple
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Returns all simple entitlements for the specified role, taking into account the entire inheritance and required
     * hierarchy.
     *
     * @param role         the role
     * @param context      sailpoint context
     * @param locale       locale
     * @param timeZone     time zone
     * @param loggedInUser logged in user
     * @return list of entitlements, where each entitlement is a Map
     */
    public static List<Map<String, Object>> getAllSimpleEntitlements(Bundle role, SailPointContext context, Locale locale, TimeZone timeZone, Identity loggedInUser)
            throws GeneralException, JSONException {
        return getAllSimpleEntitlements(role, context, locale, timeZone, loggedInUser, new StrictSimpleEntitlementCriteria());
    }

    /**
     * Returns all simple entitlements for the specified role, taking into account the entire inheritance and required
     * hierarchy.
     *
     * @param role         the role
     * @param context      sailpoint context
     * @param locale       locale
     * @param timeZone     time zone
     * @param loggedInUser logged in user
     * @param criteria     SimpleEntitlementCriteria to use for limiting simple entitlements
     * @return list of entitlements, where each entitlement is a Map
     */
    public static List<Map<String, Object>> getAllSimpleEntitlements(Bundle role, SailPointContext context, Locale locale, TimeZone timeZone, Identity loggedInUser, SimpleEntitlementCriteria criteria)
            throws GeneralException, JSONException {
        // We collect the results in a set to eliminate duplicates. Pretty safe bet that the standard comparator
        // will compare the maps correctly for our purposes (this has been tested).
        Set<Map<String, Object>> result = new HashSet<Map<String, Object>>();

        // First get the direct simple entitlements since they require special handling. If this is a business role,
        // there likely won't be any.
        String directSimpleJson = RoleUtil.getReadOnlySimpleEntitlementsJson(role, null, null, false, locale, criteria);
        Map<String, Object> directSimple = JsonHelper.mapFromJson(String.class, Object.class, directSimpleJson);

        // Manually add "direct" to the roleName property for direct entitlements
        List<Map<String, Object>> directSimpleList = (List<Map<String, Object>>) directSimple.get("entitlements");
        Message message = new Message(MessageKeys.UI_ITEM_DETAIL_DIRECT);
        if (directSimpleList != null) {
            for (Map<String, Object> direct : directSimpleList) {
                direct.put("roleName", message.getLocalizedMessage(locale, timeZone));
            }
            result.addAll(directSimpleList);
        }

        // Then get the indirect simple entitlements for this role and just add them to the result. Again,
        // if this is a business role, there likely won't be any.
        String indirectSimpleJson = RoleUtil.getReadOnlySimpleIncludedEntitlementsJson(role, null,
                context, loggedInUser, timeZone, locale, criteria);
        Map<String, Object> indirectSimple = JsonHelper.mapFromJson(String.class, Object.class, indirectSimpleJson);
        if (indirectSimple != null && indirectSimple.containsKey("entitlements")) {
            result.addAll((List<Map<String, Object>>) indirectSimple.get("entitlements"));
        }

        // If this is a business role, we need to collect all the required roles for this role and its ancestors.
        // If this is an IT role, this will probably result in an empty set, which is OK.
        Set<Bundle> allRequirements = new HashSet<Bundle>();
        allRequirements.addAll(role.getRequirements());
        Collection<Bundle> ancestors = role.getFlattenedInheritance();
        for (Bundle ancestor : ancestors) {
            allRequirements.addAll(ancestor.getRequirements());
        }

        // For each of these required roles, get the direct and indirect entitlements and add them to the result.
        for (Bundle requiredRole : allRequirements) {
            String requiredSimpleJson = RoleUtil.getReadOnlySimpleEntitlementsJson(requiredRole, null, null, false, locale, criteria);
            Map<String, Object> requiredSimple = JsonHelper.mapFromJson(String.class, Object.class, requiredSimpleJson);
            if (requiredSimple != null && requiredSimple.containsKey("entitlements")) {
                result.addAll((List<Map<String, Object>>) requiredSimple.get("entitlements"));
            }

            requiredSimpleJson = RoleUtil.getReadOnlySimpleIncludedEntitlementsJson(requiredRole, null,
                    context, loggedInUser, timeZone, locale, criteria);
            requiredSimple = JsonHelper.mapFromJson(String.class, Object.class, requiredSimpleJson);
            if (requiredSimple != null && requiredSimple.containsKey("entitlements")) {
                result.addAll((List<Map<String, Object>>) requiredSimple.get("entitlements"));
            }
        }

        return new ArrayList<Map<String, Object>>(result);
    }

    /**
     * Returns true if this role has all simple entitlements, taking into account the entire inheritance and required
     * hierarchy.
     *
     * @param role the role
     * @return list of entitlements, where each entitlement is a Map
     */
    public static boolean hasAllSimpleEntitlements(Bundle role)
            throws GeneralException {
        return hasAllSimpleEntitlements(role, new StrictSimpleEntitlementCriteria());
    }

    /**
     * Returns true if this role has all simple entitlements, taking into account the entire inheritance and required
     * hierarchy.
     *
     * @param role the role
     * @param criteria SimpleEntitlementCriteria to use for limiting simple entitlements
     * @return list of entitlements, where each entitlement is a Map
     */
    public static boolean hasAllSimpleEntitlements(Bundle role, SimpleEntitlementCriteria criteria) {
        Set<Bundle> allRoles = new HashSet<Bundle>();

        // First we find all of the roles related to this role that might have entitlements.

        // Add this role and all of its inherited ancestors.
        allRoles.add(role);
        allRoles.addAll(role.getFlattenedInheritance());

        // Find all the required roles for the roles we've already found. If the original role was an IT role, this
        // will likely be an empty set.
        Set<Bundle> requirements = new HashSet<Bundle>();
        for (Bundle ancestor : allRoles) {
            requirements.addAll(ancestor.getRequirements());
        }
        allRoles.addAll(requirements);

        // For all the required roles, find THEIR ancestors as well.
        for (Bundle required : requirements) {
            allRoles.addAll(required.getFlattenedInheritance());
        }

        // Now that we've got all the roles, just iterate through them until we find one that does not have simple
        // entitlements. If we don't find any complex entitlements, we can return true.
        for (Bundle allRole : allRoles) {
            if (!RoleUtil.hasSimpleEntitlements(allRole, null, criteria)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This is for the multi language option
     */
    private static Locale getDescriptionLocale(SailPointContext context) {

        boolean useLocalizedDescriptions = Util.otob(Configuration.getSystemConfig().get("enableLocalizeRoleDescriptions"));
        Locale locale = null;
        if (useLocalizedDescriptions) {
            FacesContext o = FacesContext.getCurrentInstance();
            if (o != null && o.getViewRoot() != null) {
                locale = o.getViewRoot().getLocale();
            }
        } else {
            Localizer localizer = new Localizer(context);
            locale = localizer.getDefaultLocale();
        }
        return locale;
    }

    /**
     * Does i18n string comparison of JSONObjects based on value of orderBy field. Nulls are handled
     * as usual, and failure to coerce field value to a null is treated as if it were null.
     */
    static class I18NJSONObjectComparator implements Comparator<JSONObject> {
        private String orderBy;

        /**
         * Creates new comparator instance.
         *
         * @param orderBy order by field
         */
        public I18NJSONObjectComparator(String orderBy) {
            this.orderBy = orderBy;
        }

        public int compare(JSONObject obj1, JSONObject obj2) {
            int result = 0;

            String val1 = getSortValue(obj1);
            String val2 = getSortValue(obj2);

            // Values are possibly null but the comparator handles those appropriately.
            result = Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR.compare(val1, val2);

            return result;
        }

        private String getSortValue(JSONObject obj) {
            String val = null;
            try {
                // If no value or it's null, we'll return null
                if (obj.has(orderBy) && !obj.isNull(orderBy)) {
                    val = obj.getString(orderBy);
                }
            } catch (JSONException ex) {
                // Treat coercion failure as null for sorting purposes. It should never happen but log it on principle.
                log.debug("Failed to get string value for field '" + orderBy + "' from object " + obj);
            }

            return val;
        }
    }
}
