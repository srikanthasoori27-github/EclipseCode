/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Plugin;
import sailpoint.object.Policy;
import sailpoint.object.RecommenderDefinition;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.TaskDefinition;
import sailpoint.plugin.PluginClassLoader;
import sailpoint.plugin.PluginsCache;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The BSFClassLoader is used for all BSF-based script executions, including
 * beanshell. It differs from its parent classloader in that it is capable
 * of loading classes from installed/enabled plugins.
 * <h3>Construction</h3>
 * The classLoader will behave differently depending on how it is constructed.
 * <p/>
 * If the parent is null, then this will look first into the bootclassloader,
 * and then into the plugins to find the classes.  If the parent is not null,
 * then this will look first into the parent, and then look into the plugins.
 * <p/>
 * If pluginName is not null, then only that specified plugin will be looked
 * into.  If null, then all plugins will be used.
 * <p/>
 * The version, set during the construction, can be retrieved by
 * getVersion() later by a caller.  This value is useful so that
 * the caller can determine if the BSFClassLoader is still valid or not.
 * <h3>Classloading</h3>
 * The plugins which will be searched to load a class (i.e. interesting plugins)
 * can be restricted to a single plugin, if a non-null pluginName is passed
 * to constructor.  Otherwise, all plugins will be interesting plugins.
 * <p/>
 * It the requested class is in a restricted package name, then no plugins will
 * be asked to load the class.  Otherwise, the interesting plugins which are enabled
 * will have their manifest checked to ensure that the requested class is in a
 * package that is declared in the "scriptPackages" attribute of the manifest.
 * If so, then qualified plugins will be each asked to load the class, stopping after found in any plugin.
 * <p/>
 * All the stuff at the end below the Console comment is used only for the console "plugin" command
 * to list some useful classloader-related info about plugins.
 *
 * @author Keith Yarbrough <keith.yarbrough@sailpoint.com>
 */

public class BSFClassLoader extends ClassLoader {

    private static final Log LOG = LogFactory.getLog(BSFClassLoader.class);

    // We store version and killSwitch during the constructor so that we
    // can use a change in either of them as a sign that the classLoader is
    // stale
    final private int _version;
    final private boolean _killSwitchOn;

    // which specific plugins to search in for classes; currently this
    // will be empty or have a single plugin name in it.  Empty
    // means to search all plugins
    final private List<String> _specificPlugins;


    /**
     *
     * @param parent the ClassLoader to use if the class can't be found in any plugin. Ignored
     *               if null.
     * @param version the plugin cache version in effect during construction
     */
    public BSFClassLoader(ClassLoader parent, int version) {
        this(parent, version, null);
    }


    /**
     *
     * @param parent the ClassLoader to use if the class can't be found in plugin(s).
     * @param version the plugin cache version in effect during construction
     * @param pluginName if not null, the specific plugin to load classes from.  If null, then
     *                   all plugins will be loaded from.
     */
    public BSFClassLoader(ClassLoader parent, int version, String pluginName) {
        super(parent);
        _version = version;
        _specificPlugins = new ArrayList<String>();

        // capture the current killswitch state so we can check in isStale() if it has changed
        _killSwitchOn = Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_PROHIBIT_SCRIPTING);
        if (Util.isNotNullOrEmpty(pluginName)) {
            _specificPlugins.add(pluginName);
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return do_findClass(name, true, null);
    }

    /**
     *
     * @param name the name of the class to find
     * @param checkForScriptAccess if true, make sure the class is in a legal package
     *                             for exporting, and that the plugin declared its
     *                             package as available for scripts.  Otherwise, do
     *                             not perform those checks,
     * @param sourcePlugin         if null, check all plugins.  Otherwise, only check the
     *                             specified plugin.
     * @return the Class for the given class name
     * @throws ClassNotFoundException class not found
     */
    private Class<?> do_findClass(String name, boolean checkForScriptAccess, String sourcePlugin) throws ClassNotFoundException {
        Class<?> result = null;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding class '" + name + "'");
        }

        boolean usePlugins = !Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_PROHIBIT_SCRIPTING);

        if (!usePlugins && checkForScriptAccess) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping find of class " + name + " because SystemConfiguration entry " +
                        Configuration.PLUGIN_PROHIBIT_SCRIPTING + " = true");
            }
        }
        else {
            PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
            List<String> cachedPlugins = getInterestingPlugins(pluginsCache);
            if (!Util.isEmpty(cachedPlugins)) {
                if (!checkForScriptAccess || isLegalForExport(name, pluginsCache)) {
                    // let's check into the plugin classLoaders
                    for (String plugin : cachedPlugins) {
                        if (sourcePlugin == null || sourcePlugin.equals(plugin)) {
                            String packageName = getPackageFromClassName(name);
                            if (!checkForScriptAccess || pluginsCache.isScriptPackage(plugin, packageName)) {
                                ClassLoader cl = pluginsCache.getClassLoader(plugin);

                                try {
                                    Class<?> clz = cl.loadClass(name);
                                    if (clz != null) {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("Found class " + name + " in plugin " + plugin);
                                        }
                                        result = clz;
                                        break;
                                    }
                                }
                                catch (ClassNotFoundException e) {
                                    // Let's try the next plugin
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result != null) {
            if (LOG.isDebugEnabled()) {
                String classLoaderName = result.getClassLoader() == null ? "bootstrap" : result.getClassLoader().toString();
                LOG.debug("Found class " + name + " with classloader = " + classLoaderName);
            }
        }
        else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to find class " + name);
            }
            throw new ClassNotFoundException(name);
        }

        return result;
    }


    /**
     * Check if the given classname can be legally exported from a plugin
     * @param className the classname to check
     * @param pluginsCache the pluginsCache
     * @return true if the classsName is in a package that is legal to export
     * classes from
     */
    public static boolean isLegalForExport(String className, PluginsCache pluginsCache) {
        String packageName = getPackageFromClassName(className);
        return !Util.isEmpty(packageName) && !pluginsCache.isRestrictedPackage(packageName);
    }

    /**
     * Check if this classLoader is now stale because the pluginsCache has changed, or
     * if the killSwitch toggle has been changed since the classLoader was constructed.
     * @return true if now stale, otherwise false
     */
    public boolean isStale() {
        boolean stale = false;
        int classLoaderVersion = getVersion();
        if (classLoaderVersion > 0) {
            // stale if the cache version has changed
            int liveVersion = Environment.getEnvironment().getPluginsCache().getVersion();
            stale = classLoaderVersion != liveVersion;
        }
        if (!stale) {
            // stale if the kill switch has changed
            boolean currentKillSwitch = Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_PROHIBIT_SCRIPTING);
            stale = (currentKillSwitch != _killSwitchOn);
        }
        return stale;
    }

    /**
     * Get the package name from the given class name
     * @param name
     * @return the package that the given class name is in, or "" if it is not in a package
     */
   static String getPackageFromClassName(String name) {
        String packageName = "";

        if (!Util.isEmpty(name)) {
            int lastDot = name.lastIndexOf ('.');
            if (lastDot!=-1) {
                packageName = name.substring(0, lastDot);
            }
        }

        return packageName;
    }

    /**
     * @param pluginsCache the pluginsCache
     * @return the list of plugin names that should be considered as candidates to search
     * for classes.  This is driven by the optional pluginName passed to constructors.
     */
    List<String> getInterestingPlugins(PluginsCache pluginsCache) {
        List<String> interestingPlugins = null;
        if (Util.isEmpty(_specificPlugins)) {
            interestingPlugins = pluginsCache.getCachedPlugins();
        }
        else {
            interestingPlugins = _specificPlugins;
        }
        return interestingPlugins;
    }

    /**
     *
     * @return the version initially passed to constructor
     */
    public int getVersion() {
        return _version;
    }



    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////
    //             Console (troubleshooting)  Support
    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    private static final String TYPE_TASK_EXEC = "Task";
    private static final String TYPE_POLICY_EXEC = "Policy";
    private static final String TYPE_SERVICE_EXEC = "Service";
    private static final String TYPE_RECOMMENDER = "Recommender";
    private static final String TYPE_SCRIPTING = "Scripting";

    /**
     * A simple class used to hold info about a class that is exorted
     * from a plugin
     */
    public static class PluginExportedClass {
        /**
         * the full classname
         */
        public String path;
        /**
         * the plugin which exports the class
         */
        public String plugin;

        /**
         * the usage this class is exported for
         */
        public String usage;


        public PluginExportedClass(String path, String usage, String plugin) {
            this.path = path;
            this.plugin = plugin;
            this.usage = usage;
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Yes, there is considerable shared code between the methods below.  However, factoring it out
    // into a shared method requires passing in at least 4 lambdas from each caller.  Not worth the
    // loss in readability, in my opinion. -- Keith Y.
    ////////////////////////////////////////////////////////////////////////

    /**
     * @return the list of PluginExportedClass objects describing which
     * classes are accessible to scripts
     */
    public List<PluginExportedClass> getScriptableClasses() {
        List<PluginExportedClass> exportedClasses = new ArrayList<PluginExportedClass>();

        PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
        List<String> plugins = getInterestingPlugins(pluginsCache);
        for(String plugin : plugins) {

            Object clObj = pluginsCache.getClassLoader(plugin);
            if (clObj instanceof PluginClassLoader) {
                PluginClassLoader pluginCl = (PluginClassLoader)clObj;
                Set<String> candidateClasses = pluginCl.getClassNames();
                for(String className : candidateClasses) {
                    try {
                        Class<?> clz = do_findClass(className, true, plugin);
                        if (clz != null) {
                            PluginExportedClass pec = new PluginExportedClass(className, TYPE_SCRIPTING, plugin);
                            exportedClasses.add(pec);
                        }
                    }
                    catch (ClassNotFoundException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected failure to load class " + className +
                                    " from plugin " + plugin);
                        }
                    }
                }

            }
        }
        return exportedClasses;
    }

    /**
     * @return the list of PluginExportedClass objects describing which
     * ServiceExecutor classes are exported and loadable
     * from the plugin(s)
     */
    public List<PluginExportedClass> getServiceExecutorClasses(SailPointContext context) {
        List<PluginExportedClass> exportedClasses = new ArrayList<PluginExportedClass>();

        PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
        List<String> plugins = getInterestingPlugins(pluginsCache);
        for(String plugin : plugins) {

            Object clObj = pluginsCache.getClassLoader(plugin);
            if (clObj instanceof PluginClassLoader) {
                Set<String> candidateClasses = pluginsCache.getServiceExecutorClassNames(plugin);
                if (Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_RELAX_EXPORT_ENFORCEMENT)) {
                    // We are in backward-compatibility mode, so look into database to find any
                    // ServiceDefinitions that have executors present in the plugin, and examine that
                    // executor classname
                    if (context != null) {
                        try {
                            List<ServiceDefinition> svcDefs = context.getObjects(ServiceDefinition.class);
                            if (!Util.isEmpty(svcDefs)) {
                                for(ServiceDefinition svcDef : svcDefs) {
                                    String pluginName = svcDef.getString(ServiceDefinition.ARG_PLUGIN_NAME);
                                    if (plugin.equals(pluginName)) {
                                        String className = svcDef.getExecutor();
                                        if (className != null) {
                                            candidateClasses.add(className);
                                        }
                                    }
                                }
                            }
                        }catch (Exception e) {
                            LOG.warn("Failed to query for existing plugin-provided ServiceDefinition executor classes", e);
                        }
                    }
                }

                for(String className : candidateClasses) {
                    try {
                        boolean isDeclared =
                                pluginsCache.isClassDeclaredExportedAsType(plugin, className, Plugin.ClassExportType.SERVICE_EXECUTOR);
                        if (isDeclared) {
                            Class<?> clz = do_findClass(className, false, plugin);
                            if (clz != null) {
                                PluginExportedClass pec = new PluginExportedClass(className, TYPE_SERVICE_EXEC, plugin);
                                exportedClasses.add(pec);
                            }
                        }
                    }
                    catch (ClassNotFoundException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected failure to load class " + className +
                                    " from plugin " + plugin);
                        }
                    }
                }

            }
        }
        return exportedClasses;
    }

    /**
     * @return the list of PluginExportedClass objects describing which
     * recommender classes are exported and loadable
     * from the plugin(s)
     */
    public List<PluginExportedClass> getRecommenderClasses(SailPointContext context) {
        List<PluginExportedClass> exportedClasses = new ArrayList<PluginExportedClass>();

        PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
        List<String> plugins = getInterestingPlugins(pluginsCache);
        for(String plugin : plugins) {

            Object clObj = pluginsCache.getClassLoader(plugin);
            if (clObj instanceof PluginClassLoader) {
                Set<String> candidateClasses = pluginsCache.getRecommenderClassNames(plugin);
                if (Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_RELAX_EXPORT_ENFORCEMENT)) {
                    // We are in backward-compatibility mode, so look into database to find any
                    // RecommenderDefinitions that have executors present in the plugin, and examine that
                    // recommender classname
                    if (context != null) {
                        try {
                            List<RecommenderDefinition> recoDefs = context.getObjects(RecommenderDefinition.class);
                            if (!Util.isEmpty(recoDefs)) {
                                for(RecommenderDefinition recoDef : recoDefs) {
                                    String pluginName = recoDef.getString(RecommenderDefinition.ATT_PLUGINNAME);
                                    if (plugin.equals(pluginName)) {
                                        String className = recoDef.getString(RecommenderDefinition.ATT_CLASSNAME);
                                        if (className != null) {
                                            candidateClasses.add(className);
                                        }
                                    }
                                }
                            }
                        }catch (Exception e) {
                            LOG.warn("Failed to query for existing plugin-provided RecommenderDefinition implementation classes", e);
                        }
                    }
                }

                for(String className : candidateClasses) {
                    try {
                        boolean isDeclared =
                                pluginsCache.isClassDeclaredExportedAsType(plugin, className, Plugin.ClassExportType.RECOMMENDER);
                        if (isDeclared) {
                            Class<?> clz = do_findClass(className, false, plugin);
                            if (clz != null) {
                                PluginExportedClass pec = new PluginExportedClass(className, TYPE_RECOMMENDER, plugin);
                                exportedClasses.add(pec);
                            }
                        }
                    }
                    catch (ClassNotFoundException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected failure to load class " + className +
                                    " from plugin " + plugin);
                        }
                    }
                }

            }
        }
        return exportedClasses;
    }

    /**
     * @return the list of PluginExportedClass objects describing which
     * TaskExecutor classes are exported and loadable
     * from the plugin(s)
     */
    public List<PluginExportedClass> getTaskExecutorClasses(SailPointContext context) {
        List<PluginExportedClass> exportedClasses = new ArrayList<PluginExportedClass>();

        PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
        List<String> plugins = getInterestingPlugins(pluginsCache);
        for(String plugin : plugins) {

            Object clObj = pluginsCache.getClassLoader(plugin);
            if (clObj instanceof PluginClassLoader) {
                Set<String> candidateClasses = pluginsCache.getTaskExecutorClassNames(plugin);
                if (Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_RELAX_EXPORT_ENFORCEMENT)) {
                    // We are in backward-compatibility mode, so look into database to find any
                    // TaskDefinitions that have executors present in the plugin, and examine that
                    // executor classname
                    if (context != null) {
                        try {
                            List<TaskDefinition> taskDefs = context.getObjects(TaskDefinition.class);
                            if (!Util.isEmpty(taskDefs)) {
                                for(TaskDefinition taskDef : taskDefs) {
                                    String pluginName = taskDef.getString(TaskDefinition.ARG_PLUGIN_NAME);
                                    if (plugin.equals(pluginName)) {
                                        String className = taskDef.getExecutor();
                                        if (className != null) {
                                            candidateClasses.add(className);
                                        }
                                    }
                                }
                            }
                        }catch (Exception e) {
                            LOG.warn("Failed to query for existing plugin-provided TaskDefinition executor classes", e);
                        }
                    }
                }

                for(String className : candidateClasses) {
                    try {
                        boolean isDeclared =
                                pluginsCache.isClassDeclaredExportedAsType(plugin, className, Plugin.ClassExportType.TASK_EXECUTOR);
                        if (isDeclared) {
                            Class<?> clz = do_findClass(className, false, plugin);
                            if (clz != null) {
                                PluginExportedClass pec = new PluginExportedClass(className, TYPE_TASK_EXEC, plugin);
                                exportedClasses.add(pec);
                            }
                        }
                    }
                    catch (ClassNotFoundException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected failure to load class " + className +
                                    " from plugin " + plugin);
                        }
                    }
                }

            }
        }
        return exportedClasses;
    }

    /**
     * @return the list of PluginExportedClass objects describing which
     * PolicyExecutor classes are exported and loadable
     * from the plugin(s)
     */
    public List<PluginExportedClass> getPolicyExecutorClasses(SailPointContext context) {
        List<PluginExportedClass> exportedClasses = new ArrayList<PluginExportedClass>();

        PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
        List<String> plugins = getInterestingPlugins(pluginsCache);
        for(String plugin : plugins) {

            Object clObj = pluginsCache.getClassLoader(plugin);
            if (clObj instanceof PluginClassLoader) {
                Set<String> candidateClasses = pluginsCache.getPolicyExecutorClassNames(plugin);
                if (Configuration.getSystemConfig().getBoolean(Configuration.PLUGIN_RELAX_EXPORT_ENFORCEMENT)) {
                    // We are in backward-compatibility mode, so look into database to find any
                    // Policy objects that have executors present in the plugin, and examine that
                    // executor classname
                    if (context != null) {
                        try {
                            List<Policy> policies = context.getObjects(Policy.class);
                            if (!Util.isEmpty(policies)) {
                                for(Policy policy : policies) {
                                    String pluginName = policy.getString(Policy.ARG_PLUGIN_NAME);
                                    if (plugin.equals(pluginName)) {
                                        String className = policy.getExecutor();
                                        if (className != null) {
                                            candidateClasses.add(className);
                                        }
                                    }
                                }
                            }
                        }catch (Exception e) {
                            LOG.warn("Failed to query for existing plugin-provided Policy executor classes", e);
                        }
                    }
                }
                for(String className : candidateClasses) {
                    try {
                        boolean isDeclared =
                                pluginsCache.isClassDeclaredExportedAsType(plugin, className, Plugin.ClassExportType.POLICY_EXECUTOR);
                        if (isDeclared) {
                            Class<?> clz = do_findClass(className, false, plugin);
                            if (clz != null) {
                                PluginExportedClass pec = new PluginExportedClass(className, TYPE_POLICY_EXEC, plugin);
                                exportedClasses.add(pec);
                            }
                        }
                    }
                    catch (ClassNotFoundException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected failure to load class " + className +
                                    " from plugin " + plugin);
                        }
                    }
                }

            }
        }
        return exportedClasses;
    }

}
