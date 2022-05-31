/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

public class ResourceMetadata extends FAMObject {

    String _resourceType;
    String _created;
    String _lastModified;
    String _location;
    String _version;

    public String getResourceType() {
        return _resourceType;
    }

    public void setResourceType(String resourceType) {
        _resourceType = resourceType;
    }

    public String getCreated() {
        return _created;
    }

    public void setCreated(String created) {
        _created = created;
    }

    public String getLastModified() {
        return _lastModified;
    }

    public void setLastModified(String lastModified) {
        _lastModified = lastModified;
    }

    public String getLocation() {
        return _location;
    }

    public void setLocation(String location) {
        _location = location;
    }

    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }
}
