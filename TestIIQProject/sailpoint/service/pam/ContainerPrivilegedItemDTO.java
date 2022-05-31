/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

/**
 * The Container Privileged Item DTO to represent privileged items that are stored in PAM containers (safes)
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerPrivilegedItemDTO {

    /**
     * name of the Privileged Item
     */
    private String name;

    /**
     * The type of the Privileged Item
     */
    private String type;

    /**
     * Construct a ContainerDTO from a Target
     * @param name
     * @param type
     */
    public ContainerPrivilegedItemDTO(String name, String type) {

        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
