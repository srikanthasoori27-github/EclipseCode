/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.util.Date;

import sailpoint.service.IdentitySummaryDTO;

/**
 * 
 *  
 * DTO representation of the OAuthClient.
 * 
 * @author danny.feng
 *
 */
public class OAuthClientDTO {

    public static final String PARAM_PROXY_USER = "proxyUser";
    public static final String PARAM_CLIENT_NAME = "name";
    public static final String PARAM_CREATE_DATE = "createDate";
    
    private String name;
    private String clientId;
    private String secret;
    private Date createDate;
    
    private IdentitySummaryDTO proxyUser;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the clientId
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @param clientId the clientId to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return the secret
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @param secret the secret to set
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * @return the createDate
     */
    public Date getCreateDate() {
        return createDate;
    }

    /**
     * @param createDate the createDate to set
     */
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    /**
     * @return the proxyUser
     */
    public IdentitySummaryDTO getProxyUser() {
        return proxyUser;
    }

    /**
     * @param proxyUser the proxyUser to set
     */
    public void setProxyUser(IdentitySummaryDTO proxyUser) {
        this.proxyUser = proxyUser;
    }
    
}
