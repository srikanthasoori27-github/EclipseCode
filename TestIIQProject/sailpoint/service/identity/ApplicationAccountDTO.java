/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.List;

/**
 * DTO representing the details of an application account  
 */
public class ApplicationAccountDTO {

    /**
     * Details of the application
     */
    private ApplicationDTO application;

    /**
     * Display name of the account
     */
    private String accountName;

    /**
     * Native identity
     */
    private String nativeIdentity;

    /**
     * Instance name
     */
    private String instance;

    /**
     * List of attributes on the link
     */
    private List<LinkAttributeDTO> linkAttributes;

    /**
     * List of entitlements on the link
     */
    private List<IdentityEntitlementDTO> entitlements;

    /**
     * List of entitlements on the link representing the targetPermissions
     */
    private List<IdentityEntitlementDTO> targetPermissions;

    /**
     * @return Display name of the account
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * @return Native identity
     */
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    /**
     * @return Instance name
     */
    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    /**
     * @return List of non-entitlement attributes on the link
     */
    public List<LinkAttributeDTO> getLinkAttributes() {
        return linkAttributes;
    }

    public void setLinkAttributes(List<LinkAttributeDTO> linkAttributes) {
        this.linkAttributes = linkAttributes;
    }
    
    /**
     * @return The account details
     */
    public ApplicationDTO getApplication() {
        return application;
    }

    public void setApplication(ApplicationDTO application) {
        this.application = application;
    }

    /**
     * List of entitlements on the link
     */
    public List<IdentityEntitlementDTO> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<IdentityEntitlementDTO> entitlements) {
        this.entitlements = entitlements;
    }

    /**
     * List of targetPermissions entitlements
     */
    public List<IdentityEntitlementDTO> getTargetPermissions() {
        return targetPermissions;
    }

    public void setTargetPermissions(List<IdentityEntitlementDTO> targetPermissions) {
        this.targetPermissions = targetPermissions;
    }
}
