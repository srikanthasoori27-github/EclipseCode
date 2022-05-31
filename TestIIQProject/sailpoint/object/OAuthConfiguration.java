/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.Util.IMatcher;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Represents a single OAuth configuration for a set of OAuth client objects.
 * There should only be one OAuth configuration per deployment. Multiple OAuthClient
 * objects are children of this element.
 */
@SuppressWarnings("serial")
@XMLClass
public class OAuthConfiguration extends AbstractXmlObject {
    
    /** default access token expires in 20 minutes **/ 
    public static final int DEFAULT_ACCESS_TOKEN_EXPIRES_IN_SECONDS = 1200;

    private Integer accessTokenExpiresInSeconds;
    private Integer refreshTokenExpiresInSeconds;
    private List<OAuthClient> oAuthClients;
    
    /**
     * @return Global configuration applied to every access token granted. Used as the 
     * value of the access token response field "expires_in".
     */
    @XMLProperty
    public Integer getAccessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }
    public void setAccessTokenExpiresInSeconds(Integer accessTokenExpiresInSeconds) {
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
    }
    /**
     * @return Global configuration used to validate a refresh token request. This configuration
     * allows for slightly longer "expires_in" values than what an access token normally would grant. 
     */
    @XMLProperty
    public Integer getRefreshTokenExpiresInSeconds() {
        return refreshTokenExpiresInSeconds;
    }
    public void setRefreshTokenExpiresInSeconds(Integer refreshTokenExpiresInSeconds) {
        this.refreshTokenExpiresInSeconds = refreshTokenExpiresInSeconds;
    }
    /**
     * @return a list of configured OAuthClient objects
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<OAuthClient> getOAuthClients() {
        return oAuthClients;
    }
    public void setOAuthClients(List<OAuthClient> oAuthClients) {
        this.oAuthClients = oAuthClients;
    }
    
    /**
     * Returns an OAuthClient by id
     * @param id id of the client
     * @return OAuthClient or null if one is not found
     */
    public OAuthClient getClient(final String id) {
        if (id == null) {
            return null;
        }
        
        IMatcher<OAuthClient> idMatcher = new IMatcher<OAuthClient>() {
            @Override
            public boolean isMatch(OAuthClient val) {
                return val != null && id.equals(val.getClientId());
            }
        };
        
        return Util.find(getOAuthClients(), idMatcher);
    }
    
    /**
     * Returns an OAuthClient by name
     * @param name name of the client
     * @return OAuthClient or null if one is not found
     */
    public OAuthClient getClientByName(final String name) {
        if (name == null) {
            return null;
        }
        
        IMatcher<OAuthClient> nameMatcher = new IMatcher<OAuthClient>() {
            @Override
            public boolean isMatch(OAuthClient val) {
                return val != null && name.equals(val.getName());
            }
        };
        
        return Util.find(getOAuthClients(), nameMatcher);
    }
    
    /**
     * Adds an OAuthClient to the list of OAuthClients
     * @param client client to add, must not be null
     */
    public void addClient(OAuthClient client) {
        if (client == null) {
            return;
        }
        
        if (oAuthClients == null) {
            oAuthClients = new ArrayList<OAuthClient>();
        }
        oAuthClients.add(client);
    }
    
    /**
     * Removes an OAuthClient from the list of OAuthClients
     * @param clientId Id of the client to delete
     * @return OAuthClient the deleted client
     */
    public OAuthClient deleteClient(String clientId) {
        OAuthClient client = getClient(clientId);
        if (client != null) {
            oAuthClients.remove(client);
        }
        return client;
    }
}
