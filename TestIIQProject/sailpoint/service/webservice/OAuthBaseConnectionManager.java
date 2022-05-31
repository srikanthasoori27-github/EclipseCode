/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Supplier;

public class OAuthBaseConnectionManager implements ConnectionManager {
    private static final Log LOG = LogFactory.getLog(OAuthBaseConnectionManager.class);

    ConnectionManagerConfigProvider<OAuthConnectionManagerConfiguration> configProvider = null;
    OAuthTokenRetriever tokenRetriever = new OAuthTokenRetriever();

    public OAuthBaseConnectionManager(ConnectionManagerConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public OAuthBaseConnectionManager(ConnectionManagerConfigProvider configProvider, OAuthTokenRetriever retriever) {
        this(configProvider);
        this.tokenRetriever = retriever;
    }

    /**
     * @return Return a WebTarget targeted to the url of the given IdentityAI configuration.  The
     * underlying client will perform proper authentication.
     * @throws Exception if the given identityAI configuration is invalid, or otherwise cannot establish
     * an authenticated connection to IdentityAI
     */
    @Override
    public WebTarget getWebTarget() throws Exception {
        WebTarget webTarget = null;
        OAuthConnectionManagerConfiguration config = configProvider.getConfiguration();
        if(config != null) {
            Client client = getUnpooledClient(config);
            if (client != null) {
                String url = asURL(config.getHostname());
                webTarget = client.target(url).
                        register(JsonMessageBodyReader.class).
                        register(JsonMessageBodyWriter.class);
            }
        }
        return webTarget;
    }

    @Override
    public void reset() {

    }

    @Override
    public boolean isConfigured() {
        if(configProvider != null) {
            try {
                configProvider.getConfiguration().validateConfiguration();
            } catch (GeneralException ex) {
                return false;
            }

            return true;
        }

        return false;
    }

    /**
     * Use the given identityAI Configuration to build a JAXRS client.  The client will not be
     * pooling, and MUST be closed by the caller.
     * @param config the configuration for this client
     * @return a Client if successful
     * @throws Exception if the give identityAI configuration is invalid, or otherwise cannot establish
     * an authenticated connection to IdentityAI
     */
    private Client getUnpooledClient(OAuthConnectionManagerConfiguration config) throws Exception {
        config.validateConfiguration();
        Client client = buildClient(config, false);

        try {
            OAuthToken token = requestNewToken(client, config);
            final String accessToken = token.getAccessToken();
            TokenRequestFilter reqFilter = new TokenRequestFilter(new AccessTokenProvider() {
                public String getAccessToken() {
                    return accessToken;
                }
            });
            client.register(reqFilter);
        }
        catch (Exception e) {
            if (client != null) {
                client.close();
            }
            throw e;
        }

        return client;
    }

    /**
     * Return a new Client based on the given Configuration
     * @param config the configuration for this client
     * @param pooled true if a pooled connection is wanted
     * @return a new Client
     */
    private Client buildClient(ConnectionManagerConfigProvider.ConnectionManagerConfiguration config, boolean pooled) {
        int readTimeout = config.getReadTimeout();
        int connectTimeout = config.getConnectTimeout();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
        clientConfig.connectorProvider(new ApacheConnectorProvider());
        return ClientBuilder.newBuilder().withConfig(clientConfig).build();
    }


    /**
     * If the given string is a URL, return the given string.  Otherwise, try
     * prefixing with https://, and return that combined string it is a proper
     * URL.  If it is still not a proper URL after prefixing, then give up
     * and return the original string.
     * @param hostOrURL the string to try to convert to a URL string
     */
    public String asURL(String hostOrURL) {
        String url = null;
        if (!Util.isNullOrEmpty(hostOrURL)) {
            try {
                new URL(hostOrURL); // check for exception
                // it's already a URL
                url = hostOrURL;
            }
            catch (MalformedURLException e) {
                // Let's assume that only hostname was given
                String url2 = "https://" + hostOrURL;
                try {
                    new URL(url2);  // check for exception
                    url = url2;
                }
                catch (MalformedURLException e2) {
                    url = hostOrURL;
                }
            }
        }
        return url;
    }

    static String DFLT_TOKEN_PATH = "oauth/token";
    /**
     * Request a new access token from the OAuth credentials provider.  Until is refreshed, tjhe token will
     * passed to the webservice in all future requests for this client.
     * @param client Client from which to create a new webTarget to call the credentials provider
     * @param config the configuration for the connection to get the oauthtoken
     * @return a new OAuthToken containing the new access token
     * @throws Exception if failed to connect the crexentials provider, or received a bad response from it
     */
    protected OAuthToken requestNewToken(
            Client client, OAuthConnectionManagerConfiguration config) throws Exception {

        String hostname = config.getHostname();
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();
        String path = Util.isNotNullOrEmpty(config.getTokenPath()) ? config.getTokenPath() : DFLT_TOKEN_PATH;

        // POST to /oauth/token to get access_token

        WebTarget webTarget = client.target(asURL(hostname)).
                register(JsonMessageBodyReader.class).
                register(JsonMessageBodyWriter.class);

        Response response = tokenRetriever.requestNewToken(webTarget, hostname,
                path, clientId, clientSecret);

        int forcedExpirySecs = config.getForcedOAuthExpirySecs();
        OAuthToken token = tokenRetriever.getTokenFromResponse(response, forcedExpirySecs);

        return token;
    }

    public static class OAuthConnectionManagerConfiguration extends ConnectionManagerConfigProvider.ConnectionManagerConfiguration {

        public OAuthConnectionManagerConfiguration(Configuration config) {
            super(config);
        }

        public String getClientSecret() throws GeneralException {
            return SailPointFactory.getCurrentContext().decrypt(this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_CLIENT_SECRET));
        }

        public String getClientId() {
            return this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_CLIENT_ID);
        }

        public int getForcedOAuthExpirySecs() {
            return this.configuration.getInt("forcedOauthExpirySecs");
        }

        public int getTokenRefreshPercent() {
            return this.configuration.getInt(Configuration.WEB_SERVICE_CONFIG_TOKEN_REFRESH_PERCENT);
        }

        public long getFailedTokenCheckInterval() {
            return this.configuration.getLong(Configuration.WEB_SERVICE_CONFIG_FAILED_TOKEN_CHECK_INTERVAL);
        }

        public String getTokenPath() {
            return this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_OAUTH_PATH);
        }


        @Override
        public void validateConfiguration() throws GeneralException {
            super.validateConfiguration();
            if (Util.isNullOrEmpty(getClientId())) {
                throw new GeneralException("Missing value for " + Configuration.WEB_SERVICE_CONFIG_CLIENT_ID);
            }
            if (Util.isNullOrEmpty(getClientSecret())) {
                throw new GeneralException("Missing value for " + Configuration.WEB_SERVICE_CONFIG_CLIENT_SECRET);
            }
        }

    }

    public static class OAuthDynamicConfigProvider implements ConnectionManagerConfigProvider<OAuthConnectionManagerConfiguration> {

        //Supplier returning a Configuration Object
        Supplier<Configuration> _supplier;

        public OAuthDynamicConfigProvider(Supplier<Configuration> supp) {
            this._supplier = supp;
        }

        public OAuthConnectionManagerConfiguration getConfiguration() {
            return new OAuthConnectionManagerConfiguration(_supplier.get());

        }
    }

    public static class OAuthStaticConfigProvider implements ConnectionManagerConfigProvider<OAuthConnectionManagerConfiguration> {
        Configuration _configuration = null;
        public OAuthStaticConfigProvider(Configuration config) {
            this._configuration = config;
        }


        public OAuthConnectionManagerConfiguration getConfiguration() {
            return new OAuthConnectionManagerConfiguration(this._configuration);
        }
    }


}
