/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Supplier;

public class BasicAuthConnectionManager implements ConnectionManager {

    ConnectionManagerConfigProvider<BasicAuthConnectionManagerConfiguration> configProvider = null;

    public BasicAuthConnectionManager(ConnectionManagerConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Override
    public WebTarget getWebTarget() throws Exception {
        WebTarget webTarget = null;
        BasicAuthConnectionManagerConfiguration config = configProvider.getConfiguration();
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
     * Use the given Configuration to build a JAXRS client.  The client will not be
     * pooling, and MUST be closed by the caller.
     * @param config the configuration for this client
     * @return a Client if successful
     * @throws Exception if the given configuration is invalid, or otherwise cannot establish
     * an authenticated connection
     */
    private Client getUnpooledClient(BasicAuthConnectionManagerConfiguration config) throws Exception {
        config.validateConfiguration();
        Client client = buildClient(config, false);

        try {
            client.register(new BasicAuthRequestFilter(config.getUserName(), config.getPassword()));
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

    public static class BasicAuthConnectionManagerConfiguration extends ConnectionManagerConfigProvider.ConnectionManagerConfiguration {

        public BasicAuthConnectionManagerConfiguration(Configuration config) {
            super(config);
        }

        public String getUserName() {
            return this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_USER_NAME);
        }

        public String getPassword() throws GeneralException {
            return SailPointFactory.getCurrentContext().decrypt(this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_PASSWORD));
        }

        @Override
        public void validateConfiguration() throws GeneralException {
            super.validateConfiguration();

            if (Util.isNullOrEmpty(getUserName())) {
                throw new GeneralException("Missing value for " + Configuration.WEB_SERVICE_CONFIG_USER_NAME);
            }

            if (Util.isNullOrEmpty(getPassword())) {
                throw new GeneralException("Missing value for " + Configuration.WEB_SERVICE_CONFIG_PASSWORD);
            }

        }
    }

    public static class BasicAuthDynamicConfigProvider implements ConnectionManagerConfigProvider<BasicAuthConnectionManagerConfiguration> {

        //Supplier returning a Configuration Object
        Supplier<Configuration> _supplier;

        public BasicAuthDynamicConfigProvider(Supplier<Configuration> supp) {
            this._supplier = supp;
        }

        public BasicAuthConnectionManagerConfiguration getConfiguration() {
            return new BasicAuthConnectionManagerConfiguration(_supplier.get());

        }

    }

    public static class BasicAuthStaticConfigProvider implements ConnectionManagerConfigProvider<BasicAuthConnectionManagerConfiguration> {

        Configuration _configuration = null;
        public BasicAuthStaticConfigProvider(Configuration config) {
            this._configuration = config;
        }


        public BasicAuthConnectionManagerConfiguration getConfiguration() {
            return new BasicAuthConnectionManagerConfiguration(this._configuration);
        }

    }
}
