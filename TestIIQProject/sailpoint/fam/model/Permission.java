/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

import sailpoint.object.Filter;

import java.util.Arrays;
import java.util.List;

/**
 * information about a user or group’s direct permissions on each business resource.
 */
public class Permission extends FAMObject implements SCIMObject {

    public static String SCIM_PATH = "permissions";


    //Permission Filters

    public enum Filters {

        groupUniqeIdentifier(PERM_FILTER_GROUP_ID, Arrays.asList(Filter.LogicalOperation.EQ)),
        userUniqueIdentifier(PERM_FILTER_USER_ID, Arrays.asList(Filter.LogicalOperation.EQ)),
        classificationCategory(PERM_FILTER_CLASSIFICATION_CAT, Arrays.asList(Filter.LogicalOperation.NOTNULL)),
        fullPath(PERM_FILTER_FULL_PATH, Arrays.asList(Filter.LogicalOperation.EQ)),
        applicationId(PERM_FILTER_APP_ID, Arrays.asList(Filter.LogicalOperation.EQ)),
        permissionTypeName(PERM_FILTER_PERM_TYPE_NAME, Arrays.asList(Filter.LogicalOperation.EQ)),
        inherited(PERM_FILTER_INHERITED, Arrays.asList(Filter.LogicalOperation.EQ));

        private List<Filter.LogicalOperation> allowedFilterTypes;
        private String property;

        Filters(String property, List<Filter.LogicalOperation> filterTypes) {
            this.property = property;
            this.allowedFilterTypes = filterTypes;
        }

        public List<Filter.LogicalOperation> getAllowedFilterTypes() {
            return allowedFilterTypes;
        }


    }

    /**
     * the domain\\groupname representation of the identity group. Supports the equal operator only.
     */
    public static final String PERM_FILTER_GROUP_ID = "groupUniqueIdentifier";

    /**
     * the domain\\username representation of the identity user. Supports the equal operator only.
     */
    public static final String PERM_FILTER_USER_ID = "userUniqueIdentifier";

    /**
     * Use this filter attribute to get permissions that have classification categories assigned to their business resource. Supports the present operator only.
     */
    public static final String PERM_FILTER_CLASSIFICATION_CAT = "classificationCategory";

    /**
     * Can be used to filter by the permission’s business resource full path. Supports the equal operator only. Must be sent with the applicationId attribute filter.
     */
    public static final String PERM_FILTER_FULL_PATH = "fullPath";

    /**
     * Can be used to filter by the permission’s business resource application id. Supports the equal operator only. To query permissions in DFS applications, must use this attribute with the DFS application id.
     */
    public static final String PERM_FILTER_APP_ID = "applicationId";

    /**
     * Use this filter attribute to get permissions with a specific permission type (Read, Write etc.). Supports the equal operator only.
     */
    public static final String PERM_FILTER_PERM_TYPE_NAME = "permissionTypeName";

    /**
     * Use this filter attribute to get permissions by their inheritance value. Supports the equal operator only and the values “false” (default), “true” or "both".
     */
    public static final String PERM_FILTER_INHERITED = "inherited";


    /**
     * Attribute to request classificationCategories attribute value to be returned
     */
    public static final String PERM_ATTR_CLASS_CAT = "classificationCategories";


    boolean _inherited;
    boolean _allow;
    IdentityUser _user;
    IdentityGroup _group;
    PermissionType _permissionType;
    BusinessResource _businessResource;

    public boolean isInherited() {
        return _inherited;
    }

    public void setInherited(boolean inherited) {
        _inherited = inherited;
    }

    public boolean isAllow() {
        return _allow;
    }

    public void setAllow(boolean allow) {
        _allow = allow;
    }

    public String getExternalId() {
        return _externalId;
    }

    public void setExternalId(String externalId) {
        _externalId = externalId;
    }

    public IdentityUser getUser() {
        return _user;
    }

    public void setUser(IdentityUser user) {
        _user = user;
    }

    public IdentityGroup getGroup() {
        return _group;
    }

    public void setGroup(IdentityGroup group) {
        _group = group;
    }

    public PermissionType getPermissionType() {
        return _permissionType;
    }

    public void setPermissionType(PermissionType permissionType) {
        _permissionType = permissionType;
    }

    public BusinessResource getBusinessResource() {
        return _businessResource;
    }

    public void setBusinessResource(BusinessResource businessResource) {
        _businessResource = businessResource;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
