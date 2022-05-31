/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;


import java.util.ArrayList;
import java.util.List;

/**
 * The Container Group Permission DTO to represent permissions of a group on PAM Containers (Safes).
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerGroupPermissionDTO {

    /**
     * rights of the permission
     */
    private String rights;

    /**
     * The list of targets that the permission gives the user rights to
     */
    private List<String> targets;

    /**
     * Construct a ContainerGroupPermissionDTO
     */
    public ContainerGroupPermissionDTO(String rights) {
        this.rights = rights;
    }


    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public List<String> getTargets() {
        return targets;
    }

    public void addTargets(List<String> targets) {
        this.targets.addAll(targets);
    }

    /**
     * Add the name of an app that grants this permission
     *
     * @param target The name of the Target that this permission grants rights to
     */
    public void addTarget(String target) {
        if (this.targets == null) {
            this.targets = new ArrayList<String>();
        }

        if(!this.targets.contains(target)) {
            this.targets.add(target);
        }
    }
}
