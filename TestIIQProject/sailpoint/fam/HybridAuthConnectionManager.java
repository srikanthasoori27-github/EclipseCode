/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Configuration;
import sailpoint.service.webservice.BasicAuthConnectionManager;
import sailpoint.service.webservice.ConnectionManager;
import sailpoint.service.webservice.ConnectionManagerConfigProvider;
import sailpoint.service.webservice.ConnectionPoolState;
import sailpoint.service.webservice.OAuthBaseConnectionManager;
import sailpoint.service.webservice.OAuthPooledConnectionManager;
import sailpoint.service.webservice.OAuthTokenPostDataRetriever;
import sailpoint.tools.GeneralException;

import javax.ws.rs.client.WebTarget;
import java.util.function.Supplier;

public class HybridAuthConnectionManager implements ConnectionManager {

    private static Log _log = LogFactory.getLog(HybridAuthConnectionManager.class);

    ConnectionManagerConfigProvider<HybridAuthConnectionManagerConfiguration> configProvider = null;
    ConnectionManager _subManager;

    public HybridAuthConnectionManager(ConnectionManagerConfigProvider configProvider) {
        this.configProvider = configProvider;
    }


    @Override
    public WebTarget getWebTarget() throws Exception {

        try {
            return getSubManager().getWebTarget();
        } catch (GeneralException ge) {
            _log.error("Unable to get ConnectionManager" + ge);
            throw ge;
        }
    }

    @Override
    public void reset() {
        try {
            getSubManager().reset();
        } catch (GeneralException ge) {
            _log.error("Error Resetting ConnectionManager" + ge);
        }

    }

    @Override
    public boolean isConfigured() {
        try {
            return getSubManager().isConfigured();
        } catch (GeneralException ge) {
            _log.error("Error testing Configured" + ge);
            return false;
        }
    }

    protected ConnectionManager getSubManager() throws GeneralException {
        HybridAuthConnectionManagerConfiguration cfg = this.configProvider.getConfiguration();

        //Allow for static config. Used for Testconnection, perhaps elsewhere? -rap
        if (this.configProvider instanceof HybridAuthStaticConfigProvider) {
            //Static, go ahead and create a new one?
            if (_subManager != null) {
                _subManager.reset();
                _subManager = null;
            }

            if (cfg.getSubConfig() instanceof BasicAuthConnectionManager.BasicAuthConnectionManagerConfiguration) {
                //Create a basicauth connection manager
                return new BasicAuthConnectionManager(
                        new BasicAuthConnectionManager.BasicAuthStaticConfigProvider(cfg.getConfiguration()));
            } else if (cfg.getSubConfig() instanceof OAuthBaseConnectionManager.OAuthConnectionManagerConfiguration) {
                //Don't use pooled. This is mostly here for TestConnection, and we don't want pooled connections
                return new OAuthBaseConnectionManager(
                        new OAuthBaseConnectionManager.OAuthStaticConfigProvider(cfg.getConfiguration()),
                        new OAuthTokenPostDataRetriever());
            } else {
                throw new GeneralException("Unknown ConnectionManagerConfiguration" + cfg);
            }

        }

        if (_subManager == null) {
            //Hasn't been created yet
            if (cfg.getSubConfig() instanceof BasicAuthConnectionManager.BasicAuthConnectionManagerConfiguration) {
                //Create a basicauth connection manager
                _subManager = new BasicAuthConnectionManager(
                        new BasicAuthConnectionManager.BasicAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()));
            } else if (cfg.getSubConfig() instanceof OAuthBaseConnectionManager.OAuthConnectionManagerConfiguration) {
                _subManager = new OAuthPooledConnectionManager(
                        new OAuthBaseConnectionManager.OAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()),
                        new ConnectionPoolState(), new OAuthTokenPostDataRetriever());
            } else {
                throw new GeneralException("Unknown ConnectionManagerConfiguration" + cfg);
            }
        } else {
            //Already been created, check to see if it's changed
            if (_subManager instanceof BasicAuthConnectionManager && cfg.getSubConfig() instanceof OAuthBaseConnectionManager.OAuthConnectionManagerConfiguration) {
                //We've changed Types -- reset, and reCreate
                _subManager.reset();
                _subManager = new OAuthPooledConnectionManager(
                        new OAuthBaseConnectionManager.OAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()),
                        new ConnectionPoolState(), new OAuthTokenPostDataRetriever());

            } else if (_subManager instanceof OAuthPooledConnectionManager && cfg.getSubConfig() instanceof BasicAuthConnectionManager.BasicAuthConnectionManagerConfiguration) {
                //We've changed types -- reset and recreate
                _subManager.reset();
                _subManager = new BasicAuthConnectionManager(
                        new BasicAuthConnectionManager.BasicAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()));
            } else if (cfg.getSubConfig() == null) {
                //Didn't match either, reset and null out subManager
                _subManager.reset();
                _subManager = null;
                throw new GeneralException("Invalid ConfigurationManagerConfiguration[" + cfg + "]");
            }
        }

        if (_subManager == null) {
            throw new GeneralException("Unable to create ConnectionManager");
        }
        return _subManager;

    }

    public static class HybridAuthConnectionManagerConfiguration extends ConnectionManagerConfigProvider.ConnectionManagerConfiguration {

        private static Log log = LogFactory.getLog(HybridAuthConnectionManagerConfiguration.class);

        ConnectionManagerConfigProvider.ConnectionManagerConfiguration _subConfig;

        HybridAuthConnectionManagerConfiguration(Configuration cfg) {
            super(cfg);

            if (cfg != null) {
                FAMConnector.AuthType authType = null;
                //Auth
                try {
                    if (cfg.getString(FAMConnector.CONFIG_AUTH_TYPE) != null) {
                        authType = FAMConnector.AuthType.valueOf(cfg.getString(FAMConnector.CONFIG_AUTH_TYPE));
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown authType [" + cfg.getString(FAMConnector.CONFIG_AUTH_TYPE) + "]");
                    throw e;
                }
                if (authType == FAMConnector.AuthType.BASIC) {
                    _subConfig = new BasicAuthConnectionManager.BasicAuthConnectionManagerConfiguration(cfg);

                } else if (authType == FAMConnector.AuthType.OAUTH) {
                    _subConfig = new OAuthBaseConnectionManager.OAuthConnectionManagerConfiguration(cfg);
                } else {
                    log.error("Unknown authType [" + cfg.getString(FAMConnector.CONFIG_AUTH_TYPE) + "]");
                }
            }
        }

        @Override
        public void validateConfiguration() throws GeneralException {
            super.validateConfiguration();

            if ((_subConfig != null)) {
                _subConfig.validateConfiguration();
            }

        }

        public ConnectionManagerConfigProvider.ConnectionManagerConfiguration getSubConfig() { return _subConfig; }

        public Configuration getConfiguration() { return configuration; }
    }

    public static class HybridAuthDynamicConfigProvider implements ConnectionManagerConfigProvider<HybridAuthConnectionManagerConfiguration> {

        //Supplier returning a Configuration Object
        Supplier<Configuration> _supplier;

        public HybridAuthDynamicConfigProvider(Supplier<Configuration> supp) { this._supplier = supp; }

        @Override
        public HybridAuthConnectionManagerConfiguration getConfiguration() {
            return new HybridAuthConnectionManagerConfiguration(_supplier.get());
        }
    }

    public static class HybridAuthStaticConfigProvider implements ConnectionManagerConfigProvider<HybridAuthConnectionManagerConfiguration> {

        //Supplier returning a Configuration Object
        Configuration _cfg;

        public HybridAuthStaticConfigProvider(Configuration cfg) { this._cfg = cfg; }

        @Override
        public HybridAuthConnectionManagerConfiguration getConfiguration() {
            return new HybridAuthConnectionManagerConfiguration(_cfg);
        }
    }
}
