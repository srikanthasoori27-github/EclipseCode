/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * OAuthClient represents an OAuth configuration for a particular client.
 */
@SuppressWarnings("serial")
@XMLClass
public class OAuthClient extends AbstractXmlObject {
    
    
    private String name;
    private String clientId;
    private String secret;
    private String redirectUrl;
    private String proxyUser;
    private String tokenKey;
    private Date createDate;
    
    /**
     * @return Name of the object. Used as a friendly name to distinguish multiple clients. Make sure this is unique.
     */
    @XMLProperty
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    /**
     * @return String of length 32 used in OAuth access token requests.  
     */
    @XMLProperty
    public String getClientId() {
        return clientId;
    }
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    /**
     * @return String of length 16 used in OAuth access token requests.  
     */
    @XMLProperty
    public String getSecret() {
        return secret;
    }
    public void setSecret(String secret) {
        this.secret = secret;
    }
    /**
     * @return Redirect URL, used in authorization code grants to redirect OAuth clients
     * back to the client site.
     */
    @XMLProperty
    public String getRedirectUrl() {
        return redirectUrl;
    }
    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
    /**
     * @return String value representing the proxy identity. This field is required for Admin Type clients.
     */
    @XMLProperty
    public String getProxyUser() {
        return proxyUser;
    }
    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }
    /**
     * @return Encrypted key used to encrypt the access token.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="TokenKey")
    public String getKey() {
        return tokenKey;
    }
    public void setKey(String key) {
        this.tokenKey = key;
    }
    /**
     * @return Date object representing the datetime this client was created.
     */
    @XMLProperty
    public Date getCreateDate() {
        return createDate;
    }
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }
    
    
}
