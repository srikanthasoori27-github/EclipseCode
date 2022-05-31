/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

/**
 * Class to hold target account information
 */
public class TargetAccountDTO {
    
    /**
     * Displayable value
     */
    private String value;
    
    /**
     * Application name
     */
    private String application;

    /**
     * Displayable account name
     */
    private String account;

    /**
     * Native identifier
     */
    private String nativeIdentity;

    /**
     * Name of the role that needs this account.
     */
    private String sourceRole;

    /**
     * Name of the instance
     */
    private String instance;

    /**
     * @return Displayable value
     */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return Application name
     */
    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    /**
     * @return Displayable account name
     */
    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * @return Native identifier
     */
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    /**
     * @return Name of the role that needs this account.
     */
    public String getSourceRole() {
        return sourceRole;
    }

    public void setSourceRole(String sourceRole) {
        this.sourceRole = sourceRole;
    }

    /**
     * @return Name of the instance
     */
    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }
}