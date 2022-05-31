/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

/**
 * POJO representation of OAuth Access Token.
 * @author danny.feng
 *
 */
public class OAuthAccessToken {

    private String clientId;
    private String identityId;
    private long expiration;
    private List<String> scope;

    public OAuthAccessToken() { }
    
    /**
     * Constructs an OAuth Access Token from json string based on JSON Web Token format.
     * @param jsonString the JSON string representation
     * @return OAuthAccessToken
     * @throws JSONException
     */
    public static OAuthAccessToken fromString(String jsonString) throws GeneralException {
        return JsonHelper.fromJson(OAuthAccessToken.class, jsonString);
    }
        
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getIdentityId() {
        return identityId;
    }
    
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }
    
    /**
     * Returns the expiration of the Access Token in seconds.
     * 
     * @return expiration in seconds
     */
    public long getExpiration() {
        return expiration;
    }
    
    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }
    
    public List<String> getScope() {
        return scope;
    }
    
    public void setScope(List<String> scope) {
        this.scope = scope;
    }
    
    public String toString() {
        return JsonHelper.toJson(this);
    }
}
