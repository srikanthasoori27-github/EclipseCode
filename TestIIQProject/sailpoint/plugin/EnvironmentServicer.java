
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.Environment;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PluginServicer that uses the Servicer
 * owned by the Environment.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class EnvironmentServicer implements PluginServicer {

    /**
     * The length of time in seconds to wait for a plugin service
     * to terminate.
     */
    private static final int SERVICE_TERMINATE_TIMEOUT = 3;

    /**
     * The context.
     */
    private SailPointContext _context;

    /**
     * The servicer.
     */
    private Servicer _servicer;

    /**
     * Constructor.
     *
     * @param context The context.
     */
    public EnvironmentServicer(SailPointContext context) {
        _context = context;
        _servicer = Environment.getEnvironment().getServicer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installServices(Plugin plugin) throws GeneralException {
        if (plugin.hasServiceExecutorClassNames()) {
            List<ServiceDefinition> serviceDefinitions = getServiceDefinitionsForPlugin(plugin);
            for (ServiceDefinition serviceDefinition : Util.iterate(serviceDefinitions)) {
                _servicer.configure(_context, serviceDefinition);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void uninstallServices(Plugin plugin) throws GeneralException {
        if (plugin.hasServiceExecutorClassNames()) {
            List<ServiceDefinition> serviceDefinitions = getServiceDefinitionsForPlugin(plugin);
            for (ServiceDefinition serviceDefinition : Util.iterate(serviceDefinitions)) {
                // we are probably in an HTTP request thread so don't wait too long
                _servicer.uninstall(serviceDefinition, SERVICE_TERMINATE_TIMEOUT);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void uninstallServices(String pluginName) throws GeneralException {
        List<ServiceDefinition> defsToUninstall = new ArrayList<>();

        List<ServiceDefinition> serviceDefinitions = _context.getObjects(ServiceDefinition.class);
        for (ServiceDefinition serviceDefinition : Util.iterate(serviceDefinitions)) {
            String defPluginName = serviceDefinition.getString(ServiceDefinition.ARG_PLUGIN_NAME);
            if (!Util.isNullOrEmpty(defPluginName) && defPluginName.equals(pluginName)) {
                defsToUninstall.add(serviceDefinition);
            }
        }

        for (ServiceDefinition serviceDefinition : defsToUninstall) {
            _servicer.uninstall(serviceDefinition, SERVICE_TERMINATE_TIMEOUT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reconfigureServices(Plugin plugin) throws GeneralException {
        if (plugin.hasServiceExecutorClassNames()) {
            List<ServiceDefinition> serviceDefinitions = getServiceDefinitionsForPlugin(plugin);
            for (ServiceDefinition serviceDefinition : Util.iterate(serviceDefinitions)) {
                _servicer.reconfigure(_context, serviceDefinition);
            }
        }
    }

    /**
     * Gets any service definitions associated with this plugin. The
     * definitions are queried by looking at the service executors that
     * the plugin specifies.
     *
     * @param plugin The plugin.
     * @return The service definitions or null.
     * @throws GeneralException
     */
    private List<ServiceDefinition> getServiceDefinitionsForPlugin(Plugin plugin) throws GeneralException {
        List<String> serviceExecutors = plugin.getServiceExecutorClassNames();
        if (Util.isEmpty(serviceExecutors)) {
            return null;
        }

        // shouldn't be more than a few so IN should be ok here
        return _context.getObjects(ServiceDefinition.class, new QueryOptions(
            Filter.in("executor", serviceExecutors)
        ));
    }

}
