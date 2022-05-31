/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import sailpoint.tools.Util;

/**
 * POJO representation of OAuth Access Token Response.
 * @author danny.feng
 *
 */
public class OAuthTokenResponse {
    
    public static final String TOKEN_TYPE_BEARER = "bearer";

    private String accessToken;
    private String tokenType;
    private int expiresIn;
    private List<String> scope;
    
    public String getAccessToken() {
        return accessToken;
    }
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    public String getTokenType() {
        return tokenType;
    }
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    public int getExpiresIn() {
        return expiresIn;
    }
    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
    public List<String> getScope() {
        return scope;
    }
    public void setScope(List<String> scope) {
        this.scope = scope;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("access_token", accessToken);
        map.put("token_type", tokenType);
        map.put("expires_in", expiresIn);
        if (!Util.isEmpty(scope)) {
            map.put("scope", StringUtils.join(scope, " "));
        }
        return map;
    }
}
