/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;


import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * The Container Account DTO to represent a user's account on a container (Safes).
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerAccountDTO {

    /**
     * The name of the application that the account is on
     */
    private String appName;

    /**
     * The display name of the account, if this is used for direct access.  Null otherwise.
     */
    private String nativeIdentity;

    /**
     * The name of the group that the account is part of (if it exists).  Null for direct access.
     */
    private String groupName;


    public ContainerAccountDTO(String appName, String nativeIdentity, String groupName) {
        this.appName = appName;
        this.nativeIdentity = nativeIdentity;
        this.groupName = groupName;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ContainerAccountDTO)) {
            return false;
        }

        ContainerAccountDTO that = (ContainerAccountDTO) obj;
        return new EqualsBuilder().
            append(this.appName, that.appName).
            append(this.groupName, that.groupName).
            append(this.nativeIdentity, that.nativeIdentity).
            isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().
            append(this.appName).
            append(this.groupName).
            append(this.nativeIdentity).
            toHashCode();
    }

    @Override
    public String toString() {
        return "Application = " + this.appName + "; account = " + this.nativeIdentity + "; group = " + this.groupName;
    }
}
