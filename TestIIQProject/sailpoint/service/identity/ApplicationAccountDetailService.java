/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LinkInterface;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.view.IdentitySummary;
import sailpoint.web.UserContext;

/**
 * Service to get application account details.
 */
public class ApplicationAccountDetailService {

    private static final String DIRECT_PERMISSIONS_ATTR_NAME = Connector.ATTR_DIRECT_PERMISSIONS;
    private static final String TARGET_PERMISSIONS_ATTR_NAME = Connector.ATTR_TARGET_PERMISSIONS;

    private UserContext userContext;

    public ApplicationAccountDetailService(UserContext context) {
        this.userContext = context;
    }

    /**
     * Get the application details. 
     * @param applicationName Name of the application
     * @return ApplicationDTO object
     * @throws GeneralException
     */
    public ApplicationDTO getApplicationDetails(String applicationName) throws GeneralException {

        ApplicationDTO appDTO = new ApplicationDTO();
        Application app = this.userContext.getContext().getObjectByName(Application.class, applicationName);

        if (app != null) {
            appDTO.setId(app.getId());
            appDTO.setName(app.getName());
            appDTO.setDescription(app.getDescription(this.userContext.getLocale()));

            if (app.getOwner() != null) {
                IdentitySummary owner = new IdentitySummary(app.getOwner());
                appDTO.setOwner(owner);
            }
            appDTO.setType(app.getType());

            if (app.getRemediators() != null) {
                List<IdentitySummary> remediators = new ArrayList<>();
                for (Identity identity : app.getRemediators()) {
                    IdentitySummary remediator = new IdentitySummary(identity);
                    remediators.add(remediator);
                }
                appDTO.setRemediators(remediators);
            }
        }
        return appDTO;
    }

    /**
     * Get the application account details
     * @param link LinkInterface to fetch details from
     * @return ApplicationAccountDTO with all link and application details
     * @throws GeneralException
     */
    public ApplicationAccountDTO getAccountDetails(LinkInterface link) throws GeneralException {
        ApplicationAccountDTO accountDTO = new ApplicationAccountDTO();
        accountDTO.setApplication(getApplicationDetails(link.getApplicationName()));
        accountDTO.setNativeIdentity(link.getNativeIdentity());
        accountDTO.setAccountName(link.getDisplayableName());
        accountDTO.setInstance(link.getInstance());
        accountDTO.setLinkAttributes(getLinkAttributes(link));
        accountDTO.setEntitlements(getEntitlements(link));
        accountDTO.setTargetPermissions(createTargetPermissionsEntitlements(link));
        return accountDTO;
    }
    
    /**
     * Get the link attributes details.
     * @param link LinkInterface object
     * @return List<LinkAttributeDTO> List containing the LinkAttributeDTO object
     * @throws GeneralException
     */
    private List<LinkAttributeDTO> getLinkAttributes(LinkInterface link) throws GeneralException {

        List<LinkAttributeDTO> linkAttributeDTOs = new ArrayList<>();
        Attributes<String, Object> attrs = link.getAttributes();
        if (Util.isEmpty(attrs)) {
            return linkAttributeDTOs;
        }
        
        Application application = this.userContext.getContext().getObjectByName(Application.class, link.getApplicationName());
        // Get all the extended attributes
        linkAttributeDTOs.addAll(getExtendedAttributes(attrs));

        // Get the non-extended, non-directly-mapped Link attributes
        List<String> filteredAttributeNames = this.filterLinkAttributes(attrs.keySet(), application);
        for (String attrName : Util.iterate(filteredAttributeNames)) {
            Object attributeValue = attrs.get(attrName);
            if (attributeValue != null) {
                LinkAttributeDTO dto = new LinkAttributeDTO();
                dto.setName(attrName);
                dto.setEntitlement(false);
                dto.setValues(getLinkAttributeValues(attributeValue));
                linkAttributeDTOs.add(dto);
            }
        }
        
        return linkAttributeDTOs;
    }

    private List<IdentityEntitlementDTO> getEntitlements(LinkInterface link) throws GeneralException {
        List<IdentityEntitlementDTO> entitlementDTOs = new ArrayList<>();
        Application application = this.userContext.getContext().getObjectByName(Application.class, link.getApplicationName());
        List<String> entitlementAttributeNames = getEntitlementAttributeNames(application);
        for (String entitlementAttributeName : Util.iterate(entitlementAttributeNames)) {
            entitlementDTOs.addAll(createEntitlementDtos(link, application, entitlementAttributeName));
        }
        entitlementDTOs.addAll(createPermissionEntitlementDtos(link, application, DIRECT_PERMISSIONS_ATTR_NAME));
        return entitlementDTOs;
    }

    @SuppressWarnings("unchecked")
    private List<IdentityEntitlementDTO> createEntitlementDtos(LinkInterface link, Application application, String attributeName)
    throws GeneralException {
        List<Object> attributeValues = Util.asList(link.getAttributes().get(attributeName));
        List<IdentityEntitlementDTO> entitlementDTOs = new ArrayList<>();
        AccountGroupService accountGroupService = new AccountGroupService(this.userContext.getContext());
        for (Object oValue : Util.iterate(attributeValues)) {
            String attributeValue = String.valueOf(oValue);
            IdentityEntitlementDTO dto = new IdentityEntitlementDTO();
            dto.setAttribute(attributeName);
            dto.setDisplayValue(Explanator.getDisplayValue(application, attributeName, attributeValue));
            dto.setValue(attributeValue);
            dto.setDescription(Explanator.getDescription(application, attributeName, attributeValue, this.userContext.getLocale()));
            dto.setPermission(false);
            dto.setGroup(accountGroupService.isGroupAttribute(application.getName(), attributeName));
            ManagedAttribute managedAttribute = ManagedAttributer.get(this.userContext.getContext(), application.getId(), attributeName, attributeValue);
            if (managedAttribute != null) {
                dto.setManagedAttributeId(managedAttribute.getId());
            }
            entitlementDTOs.add(dto);
        }
        
        return entitlementDTOs;
    }

    @SuppressWarnings("unchecked")
    private List<IdentityEntitlementDTO> createPermissionEntitlementDtos(LinkInterface link, Application application, String permissionType) {
        List<IdentityEntitlementDTO> permissionDtos = new ArrayList<>();
        List<Permission> permissions = link.getAttributes().getList(permissionType);
        for (Permission permission : Util.iterate(permissions)) {
            String description = Explanator.getPermissionDescription(application, permission.getTarget(), this.userContext.getLocale());
            IdentityEntitlementDTO dto = new IdentityEntitlementDTO();
            dto.setAttribute(permission.getTarget());
            dto.setDescription(description);
            dto.setPermission(true);
            dto.setAnnotation(permission.getAnnotation());
            String rights = permission.getRights();
            dto.setDisplayValue(rights);
            dto.setValue(rights);
            permissionDtos.add(dto);
        }
        return permissionDtos;
    }

    /**
     * Starting in 7.2 target permissions are no longer on the Link, they 
     * have to be queried.
     */
    private List<IdentityEntitlementDTO> createTargetPermissionsEntitlements(LinkInterface ilink) throws GeneralException {
        List<IdentityEntitlementDTO> permissionDTOs = new ArrayList<>();

        // need Identity for the IdentityEntitlement query, why the fuck
        // are we using LinkInterface here?
        if (ilink instanceof Link) {
            Link link = (Link)ilink;

            List<Permission> perms = ObjectUtil.getTargetPermissions(userContext.getContext(), link);
            for (Permission permission : Util.iterate(perms)) {
                // these won't have descriptions or annotations so can skip that step
                IdentityEntitlementDTO dto = new IdentityEntitlementDTO();
                dto.setAttribute(permission.getTarget());
                dto.setPermission(true);
                String rights = permission.getRights();
                dto.setDisplayValue(rights);
                dto.setValue(rights);
                permissionDTOs.add(dto);
            }
        }

        return permissionDTOs;
    }

    /**
     * Get the list of LinkAttributeDTOs for the extended attributes
     * @param attributes Attributes object off the Link
     * @return List of extended attributes.
     * @throws GeneralException
     */
    private List<LinkAttributeDTO> getExtendedAttributes(Attributes attributes) throws GeneralException {
        List<LinkAttributeDTO> linkAttributes = new ArrayList<>();
        List<ObjectAttribute> linkExtendedAttributes = Link.getObjectConfig().getObjectAttributes();
        if (Util.size(linkExtendedAttributes) > 0) {
            for (ObjectAttribute linkAttribute : linkExtendedAttributes) {
                String attributeName = linkAttribute.getName();
                Object attributeValue = attributes.get(attributeName);
                if (attributeValue != null) {
                    LinkAttributeDTO extendedAttributeDTO = new LinkAttributeDTO();
                    extendedAttributeDTO.setName(linkAttribute.getName());
                    extendedAttributeDTO.setDisplayName(linkAttribute.getDisplayableName(this.userContext.getLocale()));
                    extendedAttributeDTO.setEntitlement(false);
                    extendedAttributeDTO.setDescription(linkAttribute.getDescription());
                    extendedAttributeDTO.setValues(getLinkAttributeValues(attributeValue));
                    linkAttributes.add(extendedAttributeDTO);
                }
            }
        }
        return linkAttributes;
    }

    /**
     * Check if given attribute is a permission
     * @param attribute Name of attribute
     * @return True if permission, otherwise false
     * @throws GeneralException
     */
    private boolean isPermission(String attribute) throws GeneralException {
        return Util.nullSafeEq(DIRECT_PERMISSIONS_ATTR_NAME, attribute) ||
            Util.nullSafeEq(TARGET_PERMISSIONS_ATTR_NAME, attribute);
    }

    /**
     * Get a list of attribute names that represent entitlements
     * @param application Application containing schema
     * @return List of Attribute names 
     */
    private List<String> getEntitlementAttributeNames(Application application) {
        Schema schema = application.getSchema(Application.SCHEMA_ACCOUNT);
        return (schema != null) ? schema.getEntitlementAttributeNames() : null;
    }
    
    /**
     * Returns true if the attribute is an entitlement attribute.
     * @param attribute Name of the attribute
     * @param application Application
     * @return true if the given attribute is an entitlement attribute
     * @throws GeneralException
     */
    private boolean isEntitlement(String attribute, Application application) throws GeneralException {         
        return Util.nullSafeContains(getEntitlementAttributeNames(application), attribute);
    }

    /**
     * Return a sorted list of attributes names that are not extended attributes,
     * and also not a source for an extended attribute, or entitlements or permissions.
     * @param attrs List of attributes
     * @param application Application
     * @return List of attribute names
     * @throws GeneralException
     */
    private List<String> filterLinkAttributes(Collection<String> attrs, Application application)
        throws GeneralException {

        List<String> toFilter = new ArrayList<>();
        ObjectConfig linkConfig = Link.getObjectConfig();
        if (linkConfig != null) {
            List<ObjectAttribute> linkExtendedAttributes = linkConfig.getObjectAttributes();
            for (ObjectAttribute attr : Util.iterate(linkExtendedAttributes)) {
                // filter out any extended link attributes AND also any
                // attributes that are mapped directly to extended attributes
                toFilter.add(attr.getName());
                String sourcedBy = attr.getSourceAttribute(application);
                if (!attr.isEditable() && Util.getString(sourcedBy) != null) {
                    toFilter.add(sourcedBy);
                }
            }
        }
        
        List<String> names = new ArrayList<>();
        for (String attrName : Util.iterate(attrs)) {
            if (attrName != null && !toFilter.contains(attrName) &&
                    !isEntitlement(attrName, application) && !isPermission(attrName)) {
                names.add(attrName);
            }
        }

        Collections.sort(names);
        return names;
    }

    /**
     * Get a list of value and description for an attribute
     * @param attrVal Values for the attribute
     * @return List containing LinkAttributeValue objects
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private List<Object> getLinkAttributeValues(Object attrVal) throws GeneralException {
        List<Object> linkAttrValues = new ArrayList<>();
        if (attrVal != null) {
            for (Object attributeValue : Util.asList(attrVal)) {
                Object formattedValue = attributeValue;
                if (formattedValue != null && formattedValue instanceof Date) {
                    formattedValue = Internationalizer.getLocalizedDate((Date)formattedValue, this.userContext.getLocale(), this.userContext.getUserTimeZone());
                }
                else if (formattedValue != null && formattedValue instanceof Permission) {
                    Permission permission = (Permission) formattedValue;
                    if (permission != null) {
                        formattedValue = permission.getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone());
                    }
                }
                linkAttrValues.add(formattedValue);
            }
        }
        return (linkAttrValues.size() > 0) ? linkAttrValues : null;
    }
}
