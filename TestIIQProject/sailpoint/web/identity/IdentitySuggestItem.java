/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.io.Serializable;

/**
 * 
 * @author Tapash This class can be used if Identity is only needed for the
 *         suggest item.
 */
public class IdentitySuggestItem implements Serializable {

    //TODO: add generated
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String displayName;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
