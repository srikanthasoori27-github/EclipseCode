/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

public class ResourceType extends FAMObject implements SCIMObject {

    String _schema;
    String _endpoint;
    String _description;

    public String getSchema() {
        return _schema;
    }

    public void setSchema(String schema) {
        _schema = schema;
    }

    public String getEndpoint() {
        return _endpoint;
    }

    public void setEndpoint(String endpoint) {
        _endpoint = endpoint;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    @Override
    public String getSCIMPath() {
        return "resourcetypes";
    }

}
