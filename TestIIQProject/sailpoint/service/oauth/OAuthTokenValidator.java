/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.security.GeneralSecurityException;
import java.util.Iterator;

import sailpoint.api.SailPointContext;
import sailpoint.integration.AuthenticationUtil;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.OAuthClient;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class OAuthTokenValidator {
    
    SailPointContext context;
    
    public OAuthTokenValidator (SailPointContext context) {
        this.context = context;
    }
    
    /**
     * Validates the token string.
     * Expected input token string: 
     * 'Bearer '(base64 of the string <client id>.<encrypted string of access token contents>)
     * 
     * @param header The Authentication header
     * @return OAuthAccessToken if authenticate successfully
     * @throws GeneralSecurityException
     * @throws GeneralException 
     */
    public OAuthAccessToken authenticate(String header) throws GeneralSecurityException, GeneralException {
        
        if (Util.isNullOrEmpty(header)) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        
        Pair<String, String> parts = extract(header);
        
        if (Util.isNullOrEmpty(parts.getFirst()) || Util.isNullOrEmpty(parts.getSecond())) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        
        OAuthAccessToken token = extractToken(parts.getFirst(), parts.getSecond());
        
        //check expiration in seconds
        long expiration = token.getExpiration();
        if (expiration < System.currentTimeMillis()/1000) {
            throw new OAuthTokenExpiredException(MessageKeys.OAUTH_ACCESS_TOKEN_EXPIRED);
        }
        
        //check identity exists
        String identityId = token.getIdentityId();
        try {
            Iterator<Object[]> it = context.search(Identity.class, new QueryOptions(Filter.or(Filter.eq("id", identityId), Filter.eq("name", identityId))), "inactive");
            if (!it.hasNext()) {
                throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
            }
            Object[] obj = it.next();
            if (obj == null || obj.length == 0 || Util.otob(obj[0])) {
                throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
            }
        } catch (GeneralException e) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }

        return token;
    }

    /**
     * Extracts encoded client id and encrypted access token from the passed parameter.
     * 
     * @param tokenString
     * @return Pair of encoded client id and encrypted access token string 
     * @throws GeneralSecurityException
     */
    protected Pair<String, String> extract(String tokenString) throws GeneralSecurityException {
        
        String[] parts = null;
        try {
            parts = AuthenticationUtil.decodeHeader("Bearer", "\\.", tokenString);
        } catch (Exception ignore) {}
        
        if (parts == null || parts.length != 2) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        
        return new Pair<String, String>(parts[0], parts[1]);
    }
    
    /**
     * Returns the OAuthAccessToken from the encrypted token string.
     * @param clientId
     * @param encryptedToken
     * @return OAuthAccessToken will not be null, exception thrown instead
     * @throws GeneralSecurityException
     */
    protected OAuthAccessToken extractToken(String clientId, String encryptedToken) throws GeneralSecurityException, GeneralException {
        OAuthClientService service = new OAuthClientService(context);
        OAuthClient client = service.getClient(clientId);
        if (client == null || Util.isNullOrEmpty(client.getProxyUser())) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        
        String key = client.getKey();
        OAuthTransformer transformer = new OAuthTransformer(context);
        String decryptedToken;
        try {
            decryptedToken = transformer.decrypt(key, encryptedToken);
        } catch (GeneralException e) {
            throw new GeneralSecurityException(e);
        }
        
        OAuthAccessToken token = OAuthAccessToken.fromString(decryptedToken);

        if (token == null) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        if (!client.getProxyUser().equals(token.getIdentityId())) {
            throw new GeneralSecurityException(MessageKeys.OAUTH_ACCESS_TOKEN_INVALID);
        }
        
        return token;
    }
}
