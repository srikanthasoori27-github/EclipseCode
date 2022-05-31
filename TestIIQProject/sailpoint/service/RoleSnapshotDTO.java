/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.SailPointObject;
import sailpoint.web.UserContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * DTO to represent a role snapshot for the UI
 */
public class RoleSnapshotDTO extends BaseDTO {
    private String displayName;
    private String typeDisplayName;
    private String description;
    private String scopeDisplayName;
    private String ownerDisplayName;

    // There are some unlocalized internal attributes added to the attribute map, do not include them in the DTO
    private static final Set<String> INTERNAL_ATTRIBUTES = new HashSet<>(Arrays.asList(
            Bundle.ATT_ACCOUNT_SELECTOR_RULES,
            Bundle.ATT_ALLOW_MULTIPLE_ASSIGNMENTS,
            Bundle.ATT_DUPLICATE_ACCOUNTS,
            Bundle.ATT_MERGE_TEMPLATES));

    /**
     * Constructor
     *
     * @param role The snapshot to DTO-ify
     * @param userContext Userconext for localization
     * @param roleConfig Object config for localizing extedned attributes
     */
    public RoleSnapshotDTO(RoleSnapshot role, UserContext userContext, ObjectConfig roleConfig) {
        super(role.getObjectId());
        this.displayName = role.getObjectDisplayableName();
        this.description = role.getObjectDescription(userContext.getLocale());
        this.typeDisplayName = role.getTypeDisplayName();
        this.scopeDisplayName = role.getScopeDisplayName();
        this.ownerDisplayName = role.getOwnerDisplayName();

        Map<String, Object> attr = role.getAttributes() != null ? role.getAttributes().getMap() : new HashMap<String, Object>();
        attr.remove(SailPointObject.ATT_DESCRIPTIONS);
        Set<String> attrKeys = attr.keySet();
        for (String key : attrKeys) {
            // Exclude internal role attributes
            if (attr.get(key) != null && !INTERNAL_ATTRIBUTES.contains(key)) {
                String displayableKey = getDisplayableKey(userContext, roleConfig, key);
                this.setAttribute(displayableKey, attr.get(key).toString());
            }
        }
    }

    /**
     * If there is a registered extended role attribute with the name key the localized displayname is returned
     * otherwise key is returned
     *
     * @param userContext The UserContext to localize with
     * @param roleConfig The ObjectConfig to lookup in
     * @param key The key to lookup
     * @return The localized attribute displayname or key
     */
    private String getDisplayableKey(UserContext userContext, ObjectConfig roleConfig, String key) {
        String displayableKey = null;
        if(roleConfig != null) {
            ObjectAttribute objectAttribute = roleConfig.getObjectAttribute(key);
            if (objectAttribute != null) {
                displayableKey = objectAttribute.getDisplayableName(userContext.getLocale());
            }
        }
        return displayableKey == null ? key : displayableKey;
    }

    /**
     * Get display name for role object
     *
     * @return Display name for the role object
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get type display name for role object
     *
     * @return Type displayName of the role object
     */
    public String getTypeDisplayName() {
        return typeDisplayName;
    }

    /**
     * Get desciption name for role object
     *
     * @return Description of the role object
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get scope display name for role object
     *
     * @return Assigned scope of the role object
     */
    public String getScopeDisplayName() {
        return scopeDisplayName;
    }

    /**
     * Get owner display name for role object
     *
     * @return Owner of the role object
     */
    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }
}