/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

import java.util.List;

public class BusinessResource extends FAMObject implements SCIMObject {

    public static final String SCIM_PATH = "businessresources";


    //BusinessResource Filters
    /**
     * Can be used to filter by the business resource name. Supports the contains, starts with and equal operators. If sent without parentApplicationId, will return the first 1000 records. Cannot be sent with the fullpath filter attribute.
     */
    public static final String BUS_RESOURCE_FILTER_NAME = "name";

    /**
     * Can be used to filter by the business resource full path. Supports the equal operator only. Must be sent with the parentApplicationId attribute filter. Cannot be sent with the name filter attribute.
     */
    public static final String BUS_RESOURCE_FILTER_FULL_PATH = "fullPath";

    /**
     * Can be used to filter by the business resource application id. Supports the equal operator only. Must be sent with name or fullPath filter attributes.
     */
    public static final String BUS_RESOURCE_FILTER_PARENT_APP_ID = "parentApplicationId";

    /**
     * Use this filter attribute to get business resources from DFS applications. Supports the equal operator only and the values “false” (default), “true” or "both". Must be sent with name or fullPath filter attributes.
     */
    public static final String BUS_RESOURCE_FILTER_DFS = "isDfs";

    /**
     * Use this filter attribute to get business resources that have data owners assigned to them. Supports the present operator only.
     */
    public static final String BUS_RESOURCE_FILTER_OWNERS = "owners";


    int _type;
    String _uniqueHash;
    String _fullPath;
    String _fullPathCaseSensitive;
    boolean _inheritsPermissions;
    boolean _hasUniquePermissions;
    String _lastCrawled;
    String _lastModified;
    String _creationDate;
    int _hLevel;
    double _sizeInBytes;
    boolean _hasChildren;
    double _parentApplicationId;
    double _parentResourceId;
    boolean _isDfs;
    List<DataClassificationCategory> _classificationCategories;

    public int getType() {
        return _type;
    }

    public void setType(int type) {
        _type = type;
    }

    public String getUniqueHash() {
        return _uniqueHash;
    }

    public void setUniqueHash(String uniqueHash) {
        _uniqueHash = uniqueHash;
    }

    public String getFullPath() {
        return _fullPath;
    }

    public void setFullPath(String fullPath) {
        _fullPath = fullPath;
    }

    public String getFullPathCaseSensitive() {
        return _fullPathCaseSensitive;
    }

    public void setFullPathCaseSensitive(String fullPathCaseSensitive) {
        _fullPathCaseSensitive = fullPathCaseSensitive;
    }

    public boolean isInheritsPermissions() {
        return _inheritsPermissions;
    }

    public void setInheritsPermissions(boolean inheritsPermissions) {
        _inheritsPermissions = inheritsPermissions;
    }

    public boolean isHasUniquePermissions() {
        return _hasUniquePermissions;
    }

    public void setHasUniquePermissions(boolean hasUniquePermissions) {
        _hasUniquePermissions = hasUniquePermissions;
    }

    public String getLastCrawled() {
        return _lastCrawled;
    }

    public void setLastCrawled(String lastCrawled) {
        _lastCrawled = lastCrawled;
    }

    public String getLastModified() {
        return _lastModified;
    }

    public void setLastModified(String lastModified) {
        _lastModified = lastModified;
    }

    public String getCreationDate() {
        return _creationDate;
    }

    public void setCreationDate(String creationDate) {
        _creationDate = creationDate;
    }

    public int gethLevel() {
        return _hLevel;
    }

    public void sethLevel(int hLevel) {
        _hLevel = hLevel;
    }

    public double getSizeInBytes() {
        return _sizeInBytes;
    }

    public void setSizeInBytes(double sizeInBytes) {
        _sizeInBytes = sizeInBytes;
    }

    public boolean isHasChildren() {
        return _hasChildren;
    }

    public void setHasChildren(boolean hasChildren) {
        _hasChildren = hasChildren;
    }

    public double getParentApplicationId() {
        return _parentApplicationId;
    }

    public void setParentApplicationId(double parentApplicationId) {
        _parentApplicationId = parentApplicationId;
    }

    public double getParentResourceId() {
        return _parentResourceId;
    }

    public void setParentResourceId(double parentResourceId) {
        _parentResourceId = parentResourceId;
    }

    public boolean isDfs() {
        return _isDfs;
    }

    public void setDfs(boolean dfs) {
        _isDfs = dfs;
    }

    public List<DataClassificationCategory> getClassificationCategories() {
        return _classificationCategories;
    }

    public void setClassificationCategories(List<DataClassificationCategory> classificationCategories) {
        _classificationCategories = classificationCategories;
    }

    @Override
    public String getSCIMPath() {
        return null;
    }
}
