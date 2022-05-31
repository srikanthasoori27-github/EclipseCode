/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import sailpoint.integration.AuthenticationUtil;
import sailpoint.service.oauth.OAuthTokenIssuer.GrantType;
import sailpoint.service.oauth.OAuthTokenRequestException.ErrorType;
import sailpoint.tools.Util;

/**
 * POJO representation of OAuth Access Token Request.
 * @author danny.feng
 *
 */
public class OAuthTokenRequest {
    
    public static final String PARAM_GRANT_TYPE = "grant_type";
    public static final String PARAM_SCOPE = "scope";

    private String clientId;
    private String clientSecret;
    private String[] scopes;
    private GrantType grantType;
    
    public OAuthTokenRequest(String authHeader, String type, String scopes) throws OAuthTokenRequestException {
        if (!Util.nullSafeEq(type, GrantType.CLIENT_CREDENTIALS.toString())) {
            throw new OAuthTokenRequestException(ErrorType.INVALID_GRANT);
        }
        
        if (authHeader != null) {
            try {
                String[] creds = AuthenticationUtil.getCredentials(authHeader, /*authnUserParam*/null, /*authnUserPass*/null);
                clientId = creds[0];
                clientSecret = creds[1];
            } catch (Exception e) {
                //error response -- invalid request
                throw new OAuthTokenRequestException(ErrorType.INVALID_REQUEST);
            }
        }
        
        this.grantType = GrantType.CLIENT_CREDENTIALS;
        if (!Util.isEmpty(scopes)) {
            this.scopes = scopes.split(" ");
        }
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public GrantType getGrantType() {
        return grantType;
    }
    
    public String[] getScopes() {
        return scopes;
    }
}
