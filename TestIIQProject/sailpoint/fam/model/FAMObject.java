/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

/**
 * Base model object for all FAM objects
 */
public class FAMObject {

    String _id;
    String _name;
    String _externalId;
    ResourceMetadata _meta;


    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getExternalId() {
        return _externalId;
    }

    public void setExternalId(String externalId) {
        _externalId = externalId;
    }

    public ResourceMetadata getMeta() {
        return _meta;
    }

    public void setMeta(ResourceMetadata meta) {
        _meta = meta;
    }
}
