/*
 * (c) Copyright 2018. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.object.Bundle;
import sailpoint.web.UserContext;

/**
 * DTO to represent a role summary for the UI
 */
public class RoleSummaryDTO extends BaseDTO {
    private String displayName;
    private String typeDisplayName;
    private String description;
    private String ownerDisplayName;

    /**
     * Constructor
     *
     * @param role 
     * @param userContext Userconext for localization
     */
    public RoleSummaryDTO(Bundle role, UserContext userContext) {
        super(role.getId());
        this.displayName = role.getDisplayableName();
        this.description = role.getDescription(userContext.getLocale());
        this.typeDisplayName = role.getTypeName(false);
        this.ownerDisplayName = role.getOwner().getDisplayableName();
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
     * Get owner display name for role object
     *
     * @return Owner of the role object
     */
    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }
}