/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.client.Client;

public class ConnectionPoolState {

    private static final Log LOG = LogFactory.getLog(OAuthPooledConnectionManager.class);

    /**
     * the cached pooling Client
     */
    private Client pooledClient = null;

    /**
     * The access token required to make API calls
     */
    private OAuthToken oAuthToken = null;

    /**
     * If true, then requests are returning HTTP 401, so we should try to get
     * a new token
     */
    private boolean tokenFailing = false;

    /**
     * The identityAI config object that was used to create this pool
     */
    private ConnectionManagerConfigProvider.ConnectionManagerConfiguration pooledClientConfig = null;

    /**
     * store the exception which explains why the pooledClientConfig is invalid.
     * A null value indicates that there is no known problem with the  pooledClientConfig.
     */
    private Exception pooledClientConfigException = null;

    public Client getPooledClient() {
        return pooledClient;
    }

    public void setPooledClient(Client pooledClient) {
        this.pooledClient = pooledClient;
    }

    public OAuthToken getOAuthToken() {
        return oAuthToken;
    }

    public void setOAuthToken(OAuthToken oAuthToken) {
        this.oAuthToken = oAuthToken;
    }

    public boolean isTokenFailing() {
        return tokenFailing;
    }

    public void setTokenFailing(boolean tokenFailing) {
        this.tokenFailing = tokenFailing;
    }

    public ConnectionManagerConfigProvider.ConnectionManagerConfiguration getPooledClientConfig() {
        return pooledClientConfig;
    }

    public void setPooledClientConfig(
            ConnectionManagerConfigProvider.ConnectionManagerConfiguration pooledClientConfig) {
        this.pooledClientConfig = pooledClientConfig;
    }

    public Exception getPooledClientConfigException() {
        return pooledClientConfigException;
    }

    public void setPooledClientConfigException(Exception pooledClientConfigException) {
        this.pooledClientConfigException = pooledClientConfigException;
    }

    public void reset() {
        setPooledClient(null);
        setPooledClientConfig(null);
        setOAuthToken(null);
        setTokenFailing(false);
        setPooledClientConfigException(null);
    }

}
