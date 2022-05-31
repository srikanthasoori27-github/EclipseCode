/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;


import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;

/**
 * The Container Permission DTO to represent permissions of an identity on PAM Containers (Safes).
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerPermissionDTO {

    /**
     * rights of the permission
     */
    private String rights;

    /**
     * The list of accounts that grant the permission
     */
    private List<ContainerAccountDTO> grantingAccounts;

    /**
     * Construct a ContainerPermissionDTO
     */
    public ContainerPermissionDTO(String rights) {
        this.rights = rights;
    }


    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public List<ContainerAccountDTO> getGrantingAccounts() {
        return grantingAccounts;
    }

    public void addGrantingAccounts(List<ContainerAccountDTO> grantingAccounts) {
        for (ContainerAccountDTO acct : Util.iterate(grantingAccounts)) {
            this.addGrantingAccount(acct.getAppName(), acct.getNativeIdentity(), acct.getGroupName());
        }
    }

    /**
     * Add the name of an app that grants this permission
     *
     * @param appName
     */
    public void addGrantingAccount(String appName, String nativeIdentity, String groupName) {
        if (this.grantingAccounts == null) {
            this.grantingAccounts = new ArrayList<ContainerAccountDTO>();
        }

        ContainerAccountDTO acct = new ContainerAccountDTO(appName, nativeIdentity, groupName);
        if (!this.grantingAccounts.contains(acct)) {
            this.grantingAccounts.add(acct);
        }
    }
}
