/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import sailpoint.object.OAuthConfiguration;


/**
 * DTO representation of the OAuthConfiguration.
 * It does not include client list information.
 * 
 * @author danny.feng
 *
 */
public class OAuthConfigurationDTO {
    
    private int accessTokenExpiresInSeconds;

    public OAuthConfigurationDTO() { }

    /**
     * @param config
     */
    public OAuthConfigurationDTO(OAuthConfiguration config) {
        this.accessTokenExpiresInSeconds = config.getAccessTokenExpiresInSeconds() != null ? 
                                                  config.getAccessTokenExpiresInSeconds() : 
                                                  OAuthConfiguration.DEFAULT_ACCESS_TOKEN_EXPIRES_IN_SECONDS;
    }

    /**
     * @return the accessTokenExpiresInSeconds
     */
    public int getAccessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }

    /**
     * @param accessTokenExpiresInSeconds the accessTokenExpiresInSeconds to set
     */
    public void setAccessTokenExpiresInSeconds(int accessTokenExpiresInSeconds) {
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
    }
    
}
