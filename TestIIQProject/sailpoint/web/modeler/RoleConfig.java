/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.web.BaseDTO;

/**
 * This class serves as a proxy for the generic sailpoint.object.ObjectConfig class.
 * It provides convenience methods for obtaining information specific to Role configuration
 * jsl - I moved most of the type lookup utilities to ObjectConfig since it's more
 * convenient to have it there even though it is specific to only one class.
 * 
 * @author Bernie Margolis
 */
public class RoleConfig extends BaseDTO {

    private static final long serialVersionUID = -4566838022277668997L;

    public static final String USAGE_TIME_START_ATTR = "usageTimeStart";    
    public static final String USAGE_TIME_END_ATTR = "usageTimeEnd";
    private Map<String, RoleTypeDefinition> roleTypesMap;
    
    public RoleConfig() {
        super();
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        roleTypesMap = roleConfig.getRoleTypesMap();
    }
    
    @SuppressWarnings("unchecked")
    public RoleTypeDefinition getRoleTypeDefinition(String roleType) {
        RoleTypeDefinition roleTypeDef = null;
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        
        if (roleConfig != null && roleType != null)
            roleTypeDef = roleTypesMap.get(roleType);
        
        return roleTypeDef;
    }
    
    @SuppressWarnings("unchecked")
    public List<RoleTypeDefinition> getRoleTypeDefinitionsList() {
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig == null) {
            return new ArrayList<RoleTypeDefinition>();
        } else {
            return roleConfig.getRoleTypesList();
        }
    }
    
    public List<ObjectAttribute> getExtendableAttributes() {
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig == null) {
            return new ArrayList<ObjectAttribute>();
        } else {
            return roleConfig.getExtendedAttributeList();
        }
    }

    public Attributes<String, Object> getDefaultValues() {
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig == null) {
            return new Attributes<String, Object>();
        } else {
            return roleConfig.getDefaultValues();
        }
    }
    
    /**
     * Return a list of role type names that are manually assignable.
     * Used by BaseObjectSuggestBean to filter the list.
     */
    public List<String> getManuallyAssignableRoleTypes() {

        List<String> assignable = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (!type.isNoManualAssignment())
                    assignable.add(type.getName());
            }
        }
        return assignable;
    }

    /**
     * Return a list of role type names that are not currently assigned as RapidSetup Birthright role types.
     */
    public List<String> getNonBirthrightRapidSetupRoleTypes() {

        List<String> nonBirthrightRapidSetup = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (!RapidSetupConfigUtils.isBirthrightRoleType(type))
                    nonBirthrightRapidSetup.add(type.getName());
            }
        }
        return nonBirthrightRapidSetup;
    }
    
    public List<String> getAssignableRoleTypes() {

        List<String> assignable = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (RapidSetupConfigUtils.isBirthrightRoleType(type) || !type.isNoManualAssignment() || !type.isNoAutoAssignment())
                    assignable.add(type.getName());
            }
        }
        return assignable;
    }

    /**
     * Return a list of role type names that are inheritable.
     * Used by BaseObjectSuggestBean to filter the list.
     */
    public List<String> getInheritableRoleTypes() {

        List<String> inheritable = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (!type.isNoSubs())
                    inheritable.add(type.getName());
            }
        }
        return inheritable;
    }

    /**
     * Return a list of role type names that are permittable.
     * Used by BaseObjectSuggestBean to filter the list.
     */
    public List<String> getPermittableRoleTypes() {

        List<String> permittable = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (!type.isNotPermittable())
                    permittable.add(type.getName());
            }
        }
        return permittable;
    }

    /**
     * Return a list of role type names that are permittable.
     * Used by BaseObjectSuggestBean to filter the list.
     */
    public List<String> getContainerRoleTypes() {
        List<String> containers = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (isContainer(type))
                    containers.add(type.getName());
            }
        }
        return containers;
    }
    
    private boolean isContainer(RoleTypeDefinition type) {
        return (!type.isNoSubs() && !type.isNoSupers());
    }

    public List<String> getItRoleTypes() {
        List<String> itTypes = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (!type.isNoProfiles())
                    itTypes.add(type.getName());
            }
        }
        return itTypes;
    }
    
    public List<String> getDetectableRoleTypes() {
        List<String> detectableTypes = new ArrayList<String>();
        Collection<RoleTypeDefinition> types = getRoleTypeDefinitionsList();
        if (types != null) {
            for (RoleTypeDefinition type : types) {
                if (type.isDetectable())
                    detectableTypes.add(type.getName());
            }
        }
        return detectableTypes;
    }
}

