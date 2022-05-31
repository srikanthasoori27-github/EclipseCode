/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.managedattribute;

import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.api.ObjectConfigService;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.TargetAssociation;
import sailpoint.service.identity.IdentityEntitlementDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service to get details for managed attributes
 */
public class ManagedAttributeDetailService {
    
    private UserContext userContext;

    /**
     * Constructor.
     * 
     * @param userContext The UserContext
     */
    public ManagedAttributeDetailService(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Get the details for the managed attribute. 
     * @param managedAttribute ManagedAttribute to get details for.
     * @return ManagedAttributeDetailDTO with all the details
     * @throws GeneralException If the ManagedAttribute does not exist
     */
    public ManagedAttributeDetailDTO getDetails(ManagedAttribute managedAttribute) throws GeneralException {
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }

        ManagedAttributeDetailDTO dto = new ManagedAttributeDetailDTO();
        dto.setId(managedAttribute.getId());
        dto.setType(managedAttribute.getType());
        dto.setAttribute(managedAttribute.getAttribute());
        dto.setValue(managedAttribute.getValue());
        dto.setDisplayValue(managedAttribute.getDisplayableName());
        dto.setDescription(managedAttribute.getDescription(this.userContext.getLocale()));
        dto.setRequestable(managedAttribute.isRequestable());
        if (managedAttribute.getOwner() != null) {
            dto.setOwner(managedAttribute.getOwner().getDisplayableName());
        }
        dto.setHasInheritance(ManagedAttributeInheritanceListService.hasInheritance(this.userContext.getContext(), managedAttribute));

        ObjectConfigService objectConfigService = new ObjectConfigService(this.userContext.getContext(), this.userContext.getLocale());
        dto.setExtendedAttributes(objectConfigService.getExtendedAttributeDisplayValues(managedAttribute));

        AccountGroupService accountGroupService = new AccountGroupService(this.userContext.getContext());
        dto.setHasMembers(accountGroupService.getMemberCount(managedAttribute) > 0);

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("objectId", managedAttribute.getId()));
        dto.setHasAccess(this.userContext.getContext().countObjects(TargetAssociation.class, qo) > 0);

        // Only count classifications which have the correct owner type.
        boolean hasClassifications = false;
        for (ObjectClassification classification : managedAttribute.getClassifications()) {
            if (Util.nullSafeEq(classification.getOwnerType(), ManagedAttribute.class.getSimpleName())) {
                hasClassifications = true;
                break;
            }
        }
        dto.setHasClassifications(hasClassifications);

        Application application = managedAttribute.getApplication();
        if (application != null) {
            dto.setApplication(application.getName());
            Schema schema = application.getSchema(managedAttribute.getType());
            Attributes<String, Object> attributes = managedAttribute.getAttributes();
            if (schema != null && attributes != null) {
                for (String attributeName : attributes.keySet()) {
                    AttributeDefinition def = schema.getAttributeDefinition(attributeName);
                    if (def != null) {
                        List values = attributes.getList(attributeName);
                        if (def.isEntitlement()) {
                            if (dto.getGroupEntitlements() == null) {
                                dto.setGroupEntitlements(new ArrayList<IdentityEntitlementDTO>());
                            }
                            dto.getGroupEntitlements().addAll(createEntitlementDTOs(application.getName(), attributeName, values, def.getSchemaObjectType()));
                        } else {
                            if (dto.getGroupAttributes() == null) {
                                dto.setGroupAttributes(new HashMap<String, Object>());
                            }
                            dto.getGroupAttributes().put(attributeName, values);
                        }
                    }
                }
            }
            if (dto.getGroupEntitlements() == null) {
                dto.setGroupEntitlements(new ArrayList<IdentityEntitlementDTO>());
            }
            // get direct permissions
            dto.getGroupEntitlements().addAll(this.createPermissionEntitlementDtos(managedAttribute.getPermissions(), application));

        }

        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<IdentityEntitlementDTO> createEntitlementDTOs(String application, String attributeName, Object attributeValues, String objectType) throws GeneralException {
        List<IdentityEntitlementDTO> entitlementDTOs = new ArrayList<>();
        AccountGroupService accountGroupService = new AccountGroupService(this.userContext.getContext());
        for (Object attributeValue : Util.iterate(Util.asList(attributeValues))) {
            String attributeStringValue = attributeValue.toString();
            IdentityEntitlementDTO dto = new IdentityEntitlementDTO();
            dto.setApplication(application);
            dto.setAttribute(attributeName);
            dto.setDisplayValue(Explanator.getDisplayValue(application, attributeName, attributeStringValue, objectType));
            dto.setValue(attributeStringValue);
            dto.setDescription(Explanator.getDescription(application, attributeName, attributeStringValue, this.userContext.getLocale(), objectType));
            dto.setPermission(false);
            dto.setGroup(accountGroupService.isGroupAttribute(application, attributeName));
            entitlementDTOs.add(dto);
        }

        return entitlementDTOs;
    }

    private List<IdentityEntitlementDTO> createPermissionEntitlementDtos(List<Permission> permissions, Application application) {
        List<IdentityEntitlementDTO> permissionDtos = new ArrayList<>();
        for (Permission permission : Util.iterate(permissions)) {
            String description = Explanator.getPermissionDescription(application, permission.getTarget(), this.userContext.getLocale());
            IdentityEntitlementDTO dto = new IdentityEntitlementDTO();
            dto.setAttribute(permission.getTarget());
            String rights = permission.getRights();
            dto.setDisplayValue(rights);
            dto.setValue(rights);
            dto.setDescription(description);
            dto.setPermission(true);
            dto.setAnnotation(permission.getAnnotation());
            permissionDtos.add(dto);
        }

        return permissionDtos;
    }
}
