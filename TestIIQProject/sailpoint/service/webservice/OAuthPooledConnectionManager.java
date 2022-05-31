/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class OAuthPooledConnectionManager extends OAuthBaseConnectionManager implements ConnectionManager, AccessTokenProvider{

    private static final Log LOG = LogFactory.getLog(OAuthPooledConnectionManager.class);

    private static final Object   LOCK = new Object();

    /**
     * the monitor running for the current Client -- cleanups up dead connections
     */
    private IdleConnectionMonitor poolMonitor = null;

    /**
     * scheduled timer task to keep the Oauth token from expiring
     */
    private OAuthTokenRefresher tokenRefresher = null;

    /**
     * scheduled timer task to get a new token if we are getting auth failures
     * with current OAuth token
     */
    private OAuthTokenFixer tokenFixer = null;

    /**
     * Timer that schedules monitor tasks
     */
    private Timer monitorTimer = new Timer();

    private ConnectionPoolState state;

    public OAuthPooledConnectionManager(ConnectionManagerConfigProvider<OAuthConnectionManagerConfiguration> configProvider, ConnectionPoolState state) {
        super(configProvider);
        this.state = state;
    }
    public OAuthPooledConnectionManager(ConnectionManagerConfigProvider<OAuthConnectionManagerConfiguration> configProvider,
                                        ConnectionPoolState state, OAuthTokenRetriever retriever) {
        super(configProvider, retriever);
        this.state = state;
    }

    /**
     * @return Return a WebTarget targeted to the url of the existing IdentityAI configuration.  The
     * underlying client will perform proper authentication.  It is recommended that you
     * first check isConfigured(), and only call this method if isConfigured() returns true.
     * @throws Exception if the identityAI configuration (in database) is invalid, or otherwise cannot establish
     * an authenticated connection to IdentityAI
     */
    @Override
    public WebTarget getWebTarget() throws Exception {
        WebTarget webTarget = null;
        Client client = getPooledClient();
        if (client != null) {
            String url = asURL(state.getPooledClientConfig().getHostname());
            webTarget = client.target(url).
                    register(JsonMessageBodyReader.class).
                    register(JsonMessageBodyWriter.class);
        }
        return webTarget;
    }

    /**
     * Close the current pooled client and clear remaining state, eventually forcing a new underlying
     * pooled connection manager to be instantiated.
     * Usually only called when there is a configuration change
     */
    @Override
    public void reset() {
        Client pooledClient = state.getPooledClient();
        try {
            if (poolMonitor != null) {
                poolMonitor.cancel();
            }
            if (tokenRefresher != null) {
                tokenRefresher.cancel();
            }
            if (tokenFixer != null) {
                tokenFixer.cancel();
            }
            if (pooledClient != null) {
                closeClient(pooledClient);
            }
        }
        catch (Exception e) {
            LOG.error("Error occurred during reset of AIServices connection manager", e);
        }
        finally {
            synchronized (LOCK) {
                state.reset();
                poolMonitor = null;
                tokenRefresher = null;
                tokenFixer = null;
            }
        }
    }

    /**
     * @return true if the Configuration object exists, and
     * has values for all needed keys
     */
    @Override
    public boolean isConfigured() {
        handleConfigChanges(state.getPooledClientConfig());

        if (state.getPooledClientConfigException() != null) {
            return false;
        }
        else if (state.getPooledClientConfig() != null) {
            return true;
        }
        else {
            synchronized(LOCK) {
                setPooledClientConfig(configProvider.getConfiguration());
            }
            return (state.getPooledClientConfigException() == null);
        }
    }

    //////////////////////////////////////
    /// Helper methods
    //////////////////////////////////////



    private Client getPooledClient() throws Exception {
        handleConfigChanges(state.getPooledClientConfig());

        // go no further if we already know that we have a config problem
        assertIfExistingConfigBad();

        if (state.getPooledClient() == null) {
            synchronized (LOCK) {
                if (state.getPooledClient() == null) {
                    // try to setup the pooledClient
                    setPooledClientConfigWithThrow(configProvider.getConfiguration(), true);

                    // we know we have decent config, so let's finally try to
                    // create the pooledClient
                    setupPooledClient();
                }
            }
        }
        return state.getPooledClient();
    }


    private void setupPooledClient() throws Exception {
        Client client = buildClient(state.getPooledClientConfig(), true);
        OAuthToken token;
        try {
            token = requestNewToken(client, (OAuthConnectionManagerConfiguration)state.getPooledClientConfig());
            TokenRequestFilter reqFilter = new TokenRequestFilter(this);
            client.register(reqFilter);
            TokenResponseFilter respFilter = new TokenResponseFilter(this);
            client.register(respFilter);
        }
        catch (Exception e) {
            if (client != null) {
                client.close();
            }
            throw e;
        }

        state.setPooledClient(client);
        state.setOAuthToken(token);
        state.setTokenFailing(false);

        // schedule our timer tasks
        rescheduleIdleConnectionMonitor();
        rescheduleTokenRefresh();
        rescheduleTokenFixer();
    }

    /**
     * Re-schedule the idle connection monitor to run periodically
     */
    private void rescheduleIdleConnectionMonitor() {
        if(poolMonitor != null) {
            poolMonitor.cancel();
        }
        poolMonitor = new IdleConnectionMonitor(state.getPooledClient());

        long msInterval = 0;
        if(state.getPooledClientConfig() != null) {
            msInterval = state.getPooledClientConfig().getIdleConnectionRefreshInterval();
        }

        if(msInterval <= 0) {
            msInterval = 10000;
        }

        LOG.debug("Rescheduling the idle connection monitor thread: " + msInterval);
        monitorTimer.schedule(poolMonitor, 0, msInterval);
    }

    private static final int MIN_REFRESH = 60 * 1000; // the minimum time until next token refresh can be 60 seconds

    /**
     * Let's schedule the next time that we should get a new OAuth access token.
     * We know what time it is now, and we know when the current token will expire.  So
     * schedule the token refresh to be a timepoint which is a certain percentage
     * (e.g. 75%) of the time between now and token expiry.
     */
    public void rescheduleTokenRefresh() {
        if(tokenRefresher != null) {
            tokenRefresher.cancel();
            tokenRefresher = null;
        }

        long msUntilNextRefresh = 0;
        if (state.getOAuthToken() != null) {
            Date expiryDate = state.getOAuthToken().getExpiryDate();

            int percentageExpiryRefresh = 75;
            if(state.getPooledClientConfig() != null) {
                int refreshPercent = ((OAuthConnectionManagerConfiguration)state.getPooledClientConfig()).getTokenRefreshPercent();
                if (refreshPercent >= 20 && refreshPercent <= 95) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Setting refresh percentage to " + refreshPercent + ", as read from config");
                    }
                    percentageExpiryRefresh = refreshPercent;
                }
            }

            msUntilNextRefresh = (expiryDate.getTime() - new Date().getTime());
            if (msUntilNextRefresh < MIN_REFRESH ) {
                LOG.debug("Using minimum token refresh time of " + MIN_REFRESH + " ms instead of " + msUntilNextRefresh + " ms");
                msUntilNextRefresh = MIN_REFRESH;
            }
            else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Token will expire in " + msUntilNextRefresh + " ms");
                }
                msUntilNextRefresh = msUntilNextRefresh * percentageExpiryRefresh / 100;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adjusting refresh delay to " + msUntilNextRefresh + ", which is " + percentageExpiryRefresh + "% of time until expiry time");
                }
            }
        }
        else {
            // we don't have an OAuth token? Yikes!
            // Let's try again in one minute
            if (LOG.isDebugEnabled()) {
                LOG.debug("No existing token found, so using default  " + msUntilNextRefresh + " ms until next token refresh attempt");
            }
            msUntilNextRefresh = MIN_REFRESH;
        }


        if (LOG.isDebugEnabled()) {
            Calendar launchDateCalendar = Calendar.getInstance();
            launchDateCalendar.add(Calendar.SECOND, (int)(msUntilNextRefresh/1000L) );
            Date launchDate = launchDateCalendar.getTime();

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateString = format.format(launchDate);

            LOG.debug("Rescheduling the token refresh thread: " + dateString);
        }

        tokenRefresher = new OAuthTokenRefresher(this);
        monitorTimer.schedule(tokenRefresher, msUntilNextRefresh);
    }

    /**
     * Re-schedule the token fixer to run periodically
     */
    private void rescheduleTokenFixer() {
        if(tokenFixer != null) {
            tokenFixer.cancel();
        }
        tokenFixer = new OAuthTokenFixer(this);

        long msInterval = 0;
        if(state.getPooledClientConfig() != null) {
            msInterval = ((OAuthConnectionManagerConfiguration)state.getPooledClientConfig()).getFailedTokenCheckInterval();
        }

        if(msInterval <= 0) {
            msInterval = 60000;
        }

        LOG.debug("Rescheduling the Oauth token fixer thread: " + msInterval);
        monitorTimer.schedule(tokenFixer, 0, msInterval);
    }

    /**
     * Return a new Client based on the given Configuration
     * @param config the configuration used to build the client
     * @param pooled true if a pooled connection is wanted
     * @return a new Client
     */
    private Client buildClient(ConnectionManagerConfigProvider.ConnectionManagerConfiguration config, boolean pooled) {
        int readTimeout = config.getReadTimeout();
        int connectTimeout = config.getConnectTimeout();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(50);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER_SHARED, true);
        clientConfig.connectorProvider(new ApacheConnectorProvider());
        return ClientBuilder.newBuilder().withConfig(clientConfig).build();
    }


    /**
     * Set this flag to indicate whether or not we are currently having problems
     * authenticating with the token
     * @param isFailing the value to set for the flag
     */
    void markFailingToken(boolean isFailing) {
        state.setTokenFailing(isFailing);
    }

    public boolean isFailingToken() {
        return state.isTokenFailing();
    }

    @Override
    public String getAccessToken() {
        String accessToken = null;
        if (state.getOAuthToken() != null) {
            accessToken = state.getOAuthToken().getAccessToken();
        }
        return accessToken;
    }

    private void closeClient(Client client) {
        if (client instanceof JerseyClient) {
            Object connMgr = null;
            JerseyClient jerseyClient = (JerseyClient) client;
            if (!jerseyClient.isClosed()) {
                connMgr = client.getConfiguration().getProperty(ApacheClientProperties.CONNECTION_MANAGER);
                jerseyClient.close();
            }
            if (connMgr instanceof PoolingHttpClientConnectionManager) {
                PoolingHttpClientConnectionManager poolingConnMgr = (PoolingHttpClientConnectionManager) connMgr;
                poolingConnMgr.shutdown();
            }
        }
    }


    public void resetOAuthToken(boolean onlyIfTokenIsFailing) throws Exception {
        if (!onlyIfTokenIsFailing || state.isTokenFailing()) {
            OAuthConnectionManagerConfiguration config = (OAuthConnectionManagerConfiguration)configProvider.getConfiguration();
            Client client = buildClient(config, false);
            if (client != null) {
                OAuthToken newToken = requestNewToken(client, config);
                synchronized (LOCK) {
                    state.setOAuthToken(newToken);
                    state.setTokenFailing(false);
                }
            }
        }

    }

    private void handleConfigChanges(
            ConnectionManagerConfigProvider.ConnectionManagerConfiguration lastKnownConfiguration) {
        if(configProvider.getConfiguration().configHasChanged(lastKnownConfiguration)) {
            reset();
        }
    }

    /**
     * @throws Exception if the pooled client configuration (previously
     * set by calling setPooledClientConfig) is invalid
     */
    private void assertIfExistingConfigBad() throws Exception {
        if (state.getPooledClientConfigException() != null) {
            throw state.getPooledClientConfigException();
        }
    }

    /**
     * Set the given Configuration to use for the pooled client.
     * @param config the configuration to set for the pooled client config
     */
    private void setPooledClientConfig(ConnectionManagerConfigProvider.ConnectionManagerConfiguration config)  {
        try {
            setPooledClientConfigWithThrow(config, false);
        }
        catch (Exception e) {
            // nothing to do here
        }
    }

    /**
     * Set the given Configuration to use for the pooled client.  If the configuration
     * is invalid, throw an exception
     * @param config the configuration to set for the pooled client config
     * @param throwIfBadConfig if true, an exception will be thrown if the configuration
     *                         is invalid
     * @throws Exception thrown if the given configuration is invalid
     */
    private void setPooledClientConfigWithThrow(
            ConnectionManagerConfigProvider.ConnectionManagerConfiguration config,
            boolean throwIfBadConfig) throws Exception {
        state.setPooledClientConfig(config);

        try {
            config.validateConfiguration();
        }
        catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Invalid AIServices Configuration", e);
                LOG.warn("No additional warnings will be issued until the configuration is updated");
            }
            state.setPooledClientConfigException(e);

            if (throwIfBadConfig) {
                throw e;
            }
        }
    }

    /**
     * When run, this timer task will (attempt to) get a
     * new access token.  Then, it will reschedule itself
     * to run again.
     */
    static class OAuthTokenRefresher extends TimerTask {

        OAuthPooledConnectionManager _manager;
        OAuthTokenRefresher(OAuthPooledConnectionManager manager) {

            super();
            _manager = manager;
        }

        @Override
        public void run() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Starting OauthTokenRefresher");
            }

            SailPointContext ctx = null;
            try {
                if (_manager != null) {
                    ctx = SailPointFactory.createContext();
                    // get a new auth token, unconditionally
                    _manager.resetOAuthToken(false);
                }
            }
            catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to renew oauth token: " + e.getLocalizedMessage());
                }
            }
            finally {
                if (ctx != null) {
                    try {
                        SailPointFactory.releaseContext(ctx);
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
            }

            if (_manager != null) {
                _manager.rescheduleTokenRefresh();
            }
        }

    }

    /**
     * When run, this timer task will (attempt to) get a
     * new access token if there are problems with the current token.
     */
    static class OAuthTokenFixer extends TimerTask {

        OAuthPooledConnectionManager _manager;
        OAuthTokenFixer(OAuthPooledConnectionManager manager) {
            super();
            _manager = manager;
        }

        @Override
        public void run() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Starting OauthTokenFixer");
            }

            SailPointContext ctx = null;
            try {
                if (_manager != null) {
                    ctx = SailPointFactory.createContext();
                    // get a new token if the current one is failing
                    _manager.resetOAuthToken(true);
                }
            }
            catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to fix oauth token: " + e.getLocalizedMessage());
                }
            }
            finally {
                if (ctx != null) {
                    try {
                        SailPointFactory.releaseContext(ctx);
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

    }
}
