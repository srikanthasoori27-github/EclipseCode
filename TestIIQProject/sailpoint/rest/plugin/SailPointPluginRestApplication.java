
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import sailpoint.integration.Util;
import sailpoint.object.Plugin;
import sailpoint.plugin.PluginsCache;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.rest.ui.jaxrs.GeneralExceptionMapper;
import sailpoint.server.Environment;

/**
 * The plugin REST application. Serves all REST endpoints configured
 * in enabled plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class SailPointPluginRestApplication extends ResourceConfig {

    /**
     * The container the application is deployed into.
     */
    private static Container _container;

    /**
     * Reloads the container hosting the plugin REST application if
     * it has been started up.
     */
    public static void reload() {
        reload(null);
    }

    /**
     * Reloads the container hosting the plugin REST application if
     * it has been started up and the plugin specified has REST
     * resource class names.
     *
     * @param plugin The plugin.
     */
    public static void reload(Plugin plugin) {
        if (_container != null && (plugin == null || !Util.isEmpty(plugin.getResourceClassNames()))) {
            _container.reload(new SailPointPluginRestApplication());
        }
    }

    /**
     * Constructor. Registers all configured REST resource classes
     * contained in the plugins cache.
     */
    public SailPointPluginRestApplication() {
        // plugin provided
        registerClasses(getPluginsCache().getConfiguredResources());

        // providers
        register(GeneralExceptionMapper.class);
        register(JsonMessageBodyReader.class);
        register(JsonMessageBodyWriter.class);

        // filters
        register(PluginAuthorizationFilter.class);

        // lifecycle listener
        registerInstances(getLifecycleListener());

        property(MessageProperties.LEGACY_WORKERS_ORDERING, true);
    }

    /**
     * Gets the plugins cache.
     *
     * @return The plugins cache.
     */
    private PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

    /**
     * Creates a container lifecycle listener which allows us to get
     * a handle to the container the application is deployed into.
     *
     * @return The lifecycle listener.
     */
    private ContainerLifecycleListener getLifecycleListener() {
        return new ContainerLifecycleListener() {
            @Override
            public void onStartup(Container container) {
                _container = container;
            }

            @Override
            public void onReload(Container container) {

            }

            @Override
            public void onShutdown(Container container) {
                _container = null;
            }
        };
    }

}
