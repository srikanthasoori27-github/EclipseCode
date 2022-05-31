/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.OAuthClient;
import sailpoint.object.OAuthConfiguration;
import sailpoint.service.oauth.OAuthTokenRequestException.ErrorType;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This service is responsible for issuing access token.
 * 
 * Currently only client credentials grant is supported.
 */
public class OAuthTokenIssuer {
    
    public enum GrantType {
        CLIENT_CREDENTIALS("client_credentials"),
        AUTHORIZATION_CODE("authorization_code");

        private String type;
        
        private GrantType(String type) {
            this.type = type;
        }
        public String getType() {
            return type;
        }
        @Override
        public String toString() {
            return this.type;
        }
    }
    
    private SailPointContext context;
    
    public OAuthTokenIssuer(SailPointContext context) {
        this.context = context;
    }

    /**
     * Generates an AccessToken.
     * 
     * @param authHeader The authentication header string
     * @param data Map contains Post data
     * @return
     * @throws OAuthTokenRequestException
     * @throws GeneralException
     * @throws UnsupportedEncodingException
     */
    public OAuthTokenResponse generateToken(OAuthTokenRequest req) throws OAuthTokenRequestException, GeneralException {
        
        OAuthClientService clientSvc = new OAuthClientService(context);
        OAuthClient client = clientSvc.getClient(req.getClientId());

        if (client == null) {
            //error response -- invalid client
            throw new OAuthTokenRequestException(ErrorType.INVALID_CLIENT);
        } else {
            String secret = client.getSecret();
            String rawSecret = context.decrypt(secret);
            
            boolean isAuthenticated = Util.nullSafeEq(rawSecret, req.getClientSecret());
            if (!isAuthenticated) {
                //error response -- invalid client
                throw new OAuthTokenRequestException(ErrorType.INVALID_CLIENT);
            } else {

                Identity proxyUser = validateClientConfig(client, context);
                String[] scopes = req.getScopes();
                //check for scope
                if (validateScopes(scopes, proxyUser, context)) {
                    OAuthConfiguration conf = clientSvc.getOAuthConfig();
                    //success response
                    int expiresIn = conf.getAccessTokenExpiresInSeconds();
                    
                    OAuthAccessToken token = new OAuthAccessToken();
                    token.setClientId(client.getClientId());
                    token.setScope(Util.arrayToList(scopes));
                    token.setIdentityId(client.getProxyUser());
                    token.setExpiration(System.currentTimeMillis()/1000 + expiresIn);

                    OAuthTransformer transformer = new OAuthTransformer(context);

                    OAuthTokenResponse resp = new OAuthTokenResponse();
                    String encryptedToken = transformer.encrypt(context.decrypt(client.getKey()), token.toString());
                    String tokenString = null;
                    try {
                        tokenString = Base64.encodeBytes((client.getClientId() + "." + encryptedToken).getBytes("UTF-8"), Base64.DONT_BREAK_LINES);
                    } catch (UnsupportedEncodingException e) {
                        throw new OAuthTokenRequestException(ErrorType.INVALID_REQUEST);
                    }
                    
                    resp.setAccessToken(tokenString);
                    resp.setTokenType(OAuthTokenResponse.TOKEN_TYPE_BEARER);
                    resp.setExpiresIn(expiresIn);
                    
                    return resp;
                } else {
                    //error response -- invalid scope
                    throw new OAuthTokenRequestException(ErrorType.INVALID_SCOPE);
                }
                
            }
        }
    }
    
    /**
     * Validates a valid proxy user is configured in the OAuthClient configuration.
     * @param client Client Configuration
     * @param ctx context
     * @return Identity proxyUser, can not be null
     * @throws GeneralException if getObject fails
     * @throws OAuthTokenRequestException if the client contains an empty or missing Identity
     */
    private Identity validateClientConfig(OAuthClient client, SailPointContext ctx) 
            throws GeneralException, OAuthTokenRequestException {
        String userId = client.getProxyUser();
        if (Util.isNullOrEmpty(userId)) {
            throw new OAuthTokenRequestException(ErrorType.INVALID_CLIENT);
        }
        Identity user = ctx.getObjectByName(Identity.class, userId);
        if (user == null) {
            throw new OAuthTokenRequestException(ErrorType.INVALID_CLIENT);
        }
        if (user.isInactive()) {
            throw new OAuthTokenRequestException(ErrorType.INVALID_CLIENT);
        }
        return user;
    }

    /**
     * Validates the scopes against the proxy user of the OAuth client.
     * 
     * @param scopes list of scopes
     * @param proxyUser the OAuthClient proxy user
     * @param ctx SailPointContext
     * @return
     * @throws GeneralException
     */
    private boolean validateScopes(String[] scopes, Identity proxyUser, SailPointContext ctx) throws GeneralException {
        
        if (proxyUser == null) {
            return false;
        }
        
        if (Util.isEmpty(scopes)) {
            return true;
        }
        List<Capability> capabilities = proxyUser.getCapabilities();
        Collection<String> rights = proxyUser.getCapabilityManager().getEffectiveFlattenedRights();
        //make sure every scope is authorized
        for (String scope : scopes) {
            if (!Util.isEmpty(scope) && !sailpoint.web.Authorizer.hasAccess(capabilities, rights, scope)) {
                return false;
            }
        }
        return true;
    }


}
