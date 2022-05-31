/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import sailpoint.object.Identity;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class IdentitySummary {

    private String id;
    private String name;
    private String displayName;

    public IdentitySummary() {
    }

    public IdentitySummary(Identity identity) {
        id=identity.getId();
        name=identity.getName();
        displayName=identity.getDisplayName() != null ? identity.getDisplayName() : identity.getName();
    }

    public IdentitySummary(String id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
