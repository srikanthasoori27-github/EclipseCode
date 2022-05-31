/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

import java.util.List;

public class Capability extends FAMObject implements SCIMObject {

    static String SCIM_PATH = "capabilities";

    String _description;

    List<IdentityUser> _users;
    List<IdentityGroup> _groups;
    List<ScimRight> _rights;


    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    public List<IdentityUser> getUsers() {
        return _users;
    }

    public void setUsers(List<IdentityUser> users) {
        _users = users;
    }

    public List<IdentityGroup> getGroups() {
        return _groups;
    }

    public void setGroups(List<IdentityGroup> groups) {
        _groups = groups;
    }

    public List<ScimRight> getRights() {
        return _rights;
    }

    public void setRights(List<ScimRight> rights) {
        _rights = rights;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
