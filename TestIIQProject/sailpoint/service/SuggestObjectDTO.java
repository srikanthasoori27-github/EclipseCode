/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

public class SuggestObjectDTO extends BaseDTO {

    public String displayName;

    public String name;

    public SuggestObjectDTO() { }

    public SuggestObjectDTO(String id, String name, String displayName) {
        super(id);
        this.displayName = displayName;
        this.name = name;
    }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String s) { this.displayName = s; }

    public String getName() { return name; }

    public void setName(String s) { this.name = s; }
}
