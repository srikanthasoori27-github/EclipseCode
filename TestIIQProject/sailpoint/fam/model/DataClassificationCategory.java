/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

public class DataClassificationCategory extends FAMObject implements SCIMObject {

    public static final String SCIM_PATH = "dataclassificationcategories";

    String _description;

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
