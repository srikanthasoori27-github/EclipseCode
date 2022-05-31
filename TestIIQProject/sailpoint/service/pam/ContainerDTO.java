/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;


import sailpoint.object.ManagedAttribute;
import sailpoint.object.Target;
import sailpoint.service.IdentitySummaryDTO;

/**
 * The Container DTO to represent PAM Containers (Safes).
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerDTO {
    /**
     * ID of the Target object
     */
    private String id;

    /**
     * name of the Target
     */
    private String name;

    /**
     * The count of identities who have both indirect or direct access to the container/safe
     */
    private int identityTotalCount;

    /**
     * The count of privileged items on the container/safe
     */
    private int privilegedItemCount;

    /**
     * The count of groups that have access to the container/safe
     */
    private int groupCount;

    /**
     * The owner of the container
     */
    private IdentitySummaryDTO owner;

    /**
     * The application associated to the container
     */
    private String applicationName;


    /**
     * Construct a ContainerDTO from a Target
     *
     * @param target  The Target representing the container.
     * @param ma  The ManagedAttribute with additional information about the container (if available).
     */
    public ContainerDTO(Target target, ManagedAttribute ma, String applicationName) {
        this.id = target.getId();

        this.name = ContainerService.getTargetDisplayName(target, ma);

        this.owner = (ma != null && ma.getOwner() != null) ? new IdentitySummaryDTO(ma.getOwner()) : null;

        this.applicationName = applicationName;
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

    public int getIdentityTotalCount() {
        return identityTotalCount;
    }

    public void setIdentityTotalCount(int identityTotalCount) {
        this.identityTotalCount = identityTotalCount;
    }

    public int getPrivilegedItemCount() {
        return privilegedItemCount;
    }

    public void setPrivilegedItemCount(int privilegedItemCount) {
        this.privilegedItemCount = privilegedItemCount;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }

    public IdentitySummaryDTO getOwner() { return owner; }

    public void setOwner(IdentitySummaryDTO owner) { this.owner = owner; }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}