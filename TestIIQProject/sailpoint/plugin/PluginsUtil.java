
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Plugin;
import sailpoint.server.Auditor;
import sailpoint.server.Environment;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

/**
 * Utility methods for working with plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginsUtil {

    /**
     * The mount point of the plugins servlet.
     */
    private static final String SERVLET_MOUNT = "/plugin/";

    /**
     * Private constructor.
     */
    private PluginsUtil() {}

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name using the
     * plugins cache owned by the Environment.
     *
     * Note that the class must have a default constructor.
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @return The new instance or null if ANY exception is thrown, parameters are missing or
     *         the plugin does not exist in the cache.
     */
    @Deprecated
    public static <T> T instantiate(String pluginName, String className) {
        return instantiate(pluginName, className, Plugin.ClassExportType.UNCHECKED, (Object[]) null, (Class<?>[]) null, getPluginsCache());
    }
    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name using the
     * plugins cache owned by the Environment.
     *
     * Note that the class must have a default constructor.
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @return The new instance or null if ANY exception is thrown, parameters are missing,
     *         the plugin does not exist in the cache, or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    public static <T> T instantiate(String pluginName, String className, Plugin.ClassExportType classExportType) {
        return instantiate(pluginName, className, classExportType, (Object[]) null, (Class<?>[]) null, getPluginsCache());
    }

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name using the
     * plugins cache owned by the Environment.
     *
     * Note that the class must have a constructor with a signature
     * consistent with paramTypes
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param params  The parameters to pass to the class's constructor.
     * @param paramTypes  The types of the parameters to pass to the class's constructor.
     * @return The new instance or null if ANY exception is thrown, parameters are missing or
     *         the plugin does not exist in the cache.
     */
    @Deprecated
    public static <T> T instantiate(String pluginName, String className, Object[] params, Class<?>[] paramTypes) {
        return instantiate(pluginName, className, Plugin.ClassExportType.UNCHECKED, params, paramTypes, getPluginsCache());
    }


    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name using the
     * plugins cache owned by the Environment.
     *
     * Note that the class must have a constructor with a signature
     * consistent with paramTypes
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @param params  The parameters to pass to the class's constructor.
     * @param paramTypes  The types of the parameters to pass to the class's constructor.
     * @return The new instance or null if ANY exception is thrown, parameters are missing,
     *         the plugin does not exist in the cache, or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    public static <T> T instantiate(String pluginName, String className, Plugin.ClassExportType classExportType,
                                    Object[] params, Class<?>[] paramTypes) {
        return instantiate(pluginName, className, classExportType, params, paramTypes, getPluginsCache());
    }

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name using the
     * plugins cache owned by the Environment.
     *
     * Note that the class must have a constructor with a signature
     * consistent with paramTypes
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @param params  The parameters to pass to the class's constructor.
     * @param paramTypes  The types of the parameters to pass to the class's constructor.
     * @return The new instance
     * @throws GeneralException if ANY exception is thrown, parameters are missing,
     *         the plugin does not exist in the cache, or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    public static <T> T instantiateWithException(String pluginName, String className, Plugin.ClassExportType classExportType,
                                    Object[] params, Class<?>[] paramTypes) throws GeneralException {
        return instantiateWithException(pluginName, className, classExportType, params, paramTypes, getPluginsCache());
    }

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name.
     *
     * Note that the class must have a default constructor
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @param pluginsCache The plugins cache.
     * @return The new instance or null if ANY exception is thrown, parameters are missing,
     *         the plugin does not exist in the cache,  or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    public static <T> T instantiate(String pluginName, String className, Plugin.ClassExportType classExportType,
                                    PluginsCache pluginsCache) {
        return instantiate(pluginName, className, classExportType, null, null, pluginsCache);
    }

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name.
     *
     * Note that the class must have a constructor with a signature
     * consistent with paramTypes
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @param params  The parameters to pass to the class's constructor.
     * @param paramTypes  The types of the parameters to pass to the class's constructor.
     * @param pluginsCache The plugins cache.
     * @return The new instance or null if ANY exception is thrown, parameters are missing.
     *         the plugin does not exist in the cache,  or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    private static <T> T instantiate(String pluginName, String className, Plugin.ClassExportType classExportType,
                                     Object[] params, Class<?>[] paramTypes,
                                     PluginsCache pluginsCache) {
        try {
            return instantiateWithException(pluginName, className, classExportType, params, paramTypes, pluginsCache);
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Instantiates an object with the specified class name using the
     * class loader for the plugin with the specified name.
     *
     * Note that the class must have a constructor with a signature
     * consistent with paramTypes
     *
     * @param pluginName The plugin name.
     * @param className The class name.
     * @param classExportType make sure plugin declares the class as exported
     *                        for the given export type
     * @param params  The parameters to pass to the class's constructor.
     * @param paramTypes  The types of the parameters to pass to the class's constructor.
     * @param pluginsCache The plugins cache.
     * @return The new instance
     * @throws GeneralException if ANY exception is thrown, parameters are missing,
     *         the plugin does not exist in the cache, or the plugin didn't declare the
     *         class as exported for the given export type.
     */
    private static <T> T instantiateWithException(String pluginName, String className, Plugin.ClassExportType classExportType,
                                    Object[] params, Class<?>[] paramTypes,
                                    PluginsCache pluginsCache) throws GeneralException {
        // do some simple validation
        if (Util.isNullOrEmpty(pluginName)) {
            throw new GeneralException("plugin name is null");
        }

        if (Util.isNullOrEmpty(className)) {
            throw new GeneralException("classname is null");
        }

        if (pluginsCache == null) {
            throw new GeneralException("plugins cache is null");
        }

        ClassLoader classLoader = pluginsCache.getClassLoader(pluginName);
        if (classLoader == null) {
            throw new GeneralException("Plugin '" + pluginName + "' not found, or not enabled");
        }

        // make sure the class is properly declared as exported by the plugin
        boolean isExported = pluginsCache.isClassDeclaredExportedAsType(pluginName, className, classExportType);
        if (!isExported) {
            throw new GeneralException("Plugin '" + pluginName + "' does not declare class " + className + " as exported for " + classExportType.toString());
        }

        try {
            Class<?> cls = classLoader.loadClass(className);
            if (cls == null) {
                throw new GeneralException("Failed to load class " + className + " from plugin '" + pluginName + "'");
            }

            // Get the constructor that takes the given param types.
            Constructor<?> con = null;
            try {
                con = cls.getConstructor(paramTypes);
            }
            catch (Exception e) {
                throw new GeneralException("Class " + className + " does not define expected constructor");
            }
            if (null == con) {
                throw new GeneralException("Class " + className + " does not define expected constructor");
            }

            return (T) con.newInstance(params);
        } catch (Throwable e) {

            if (e instanceof GeneralException) {
                throw (GeneralException)e;
            }
            else {
                throw new GeneralException("Failed to instantiate class " + className + " using plugin '" + pluginName + "'", e);
            }
        }
    }

    /**
     * Parses the plugin name and file given path and a prefix. The
     * end of the prefix is the starting point to parse. The path should
     * look like the following: {prefix}/{pluginName}/{file}
     *
     * @param path The path.
     * @param prefix The prefix.
     * @return A Pair containing the name and file.
     */
    public static Pair<String, String> getNameAndFileFromUrl(String path, String prefix) {
        String pluginName = null;
        String file = null;

        int queryIndex = path.indexOf("?");
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        int prefixIdx = path.indexOf(prefix);
        if (prefixIdx >= 0) {
            // add one more if the prefix does not end with /
            int subIdx = prefixIdx + prefix.length();
            if (!prefix.endsWith("/")) {
                subIdx++;
            }

            // name will be first token and file will be rest of tokens joined, we also
            // make sure that the tokens are not empty strings so we return null
            String[] tokens = path.substring(subIdx).split("/");
            if (tokens.length > 0 && Util.isNotNullOrEmpty(tokens[0])) {
                pluginName = tokens[0];
                if (tokens.length > 1 && Util.isNotNullOrEmpty(tokens[1])) {
                    file = Util.join(Arrays.asList(Arrays.copyOfRange(tokens, 1, tokens.length)), "/");
                }
            }
        }

        return new Pair<>(pluginName, file);
    }

    /**
     * Gets the public URL for the static file.
     *
     * @param pluginName The plugin name.
     * @param file The file.
     * @return The URL.
     */
    public static String getPluginFileUrl(String pluginName, String file) {
        return SERVLET_MOUNT + pluginName + "/" + file;
    }

    /**
     * Gets the URL used to include a plugin file in a JSF context.
     *
     * @param pluginName The plugin name.
     * @param file The file.
     * @return The url.
     */
    public static String getPluginFileIncludeUrl(String pluginName, String file) {
        return "/plugin/include/" + pluginName + "/" + file;
    }

    /**
     * Determines if the content should be included on the specified page
     * for the user with the specified rights.
     *
     * @param requiredRight The required right.
     * @param regex The regex.
     * @param contentRequest The context of the page content request.
     * @return True if authorized for snippet, false otherwise.
     */
    public static boolean isAuthorizedForContent(String requiredRight,
                                                 String regex,
                                                 PageContentRequest contentRequest) {
        boolean authorized = false;

        // first check right
        if (hasRequiredRight(requiredRight, contentRequest)) {
            // no right specified or user has it so now match regex
            if (Util.isNullOrEmpty(regex)) {
                authorized = true;
            } else {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(contentRequest.getPath());

                authorized = matcher.matches();
            }
        }

        return authorized;
    }

    /**
     * Determines if the plugin min and max system version configuration is valid
     * for the current system version.
     *
     * @param plugin The plugin.
     * @param systemVersion The system version.
     * @return True if valid, false otherwise.
     */
    public static boolean isPluginValidForSystemVersion(Plugin plugin, String systemVersion) {
        String minVersion = plugin.getMinSystemVersion();
        String maxVersion = plugin.getMaxSystemVersion();

        // if neither is set then don't bother checking
        if (Util.isNullOrEmpty(minVersion) && Util.isNullOrEmpty(maxVersion)) {
            return true;
        }

        boolean valid = true;
        if (!Util.isNullOrEmpty(minVersion)) {
            valid = isVersionLessThanOrEqual(minVersion, systemVersion);
        }

        // if min version was valid check max
        if (valid && !Util.isNullOrEmpty(maxVersion)) {
            valid = isVersionGreaterThanOrEqual(maxVersion,systemVersion);
        }

        return valid;
    }

    /**
     * Determines whether a plugin upgrade is a reinstall of the same version
     * or a downgrade to an earlier version.
     *
     * @param prev The previous version.
     * @param next The new version.
     * @return True if {@code next} is an equal or lower version, false otherwise.
     */
    public static boolean isEqualOrDowngrade(Plugin prev, Plugin next) {
        if (!Util.nullSafeEq(prev.getName(), next.getName())) {
            return false;
        }
        // prevent the next version from upgrading to null
        else if (next.getVersion() == null) {
            return true;
        }
        // allow a null plugin to go to anything else
        else if (prev.getVersion() == null && next.getVersion() != null) {
            return false;
        }

        return isVersionLessThanOrEqual(next.getVersion(), prev.getVersion());
    }

    /**
     * Determines if a plugin is upgradable based on any min upgradable
     * version that is set.
     *
     * @param prev The previous version.
     * @param next The new version.
     * @return True if upgradable, false otherwise.
     */
    public static boolean isMinUpgradableVersionMet(Plugin prev, Plugin next) {
        if (!Util.nullSafeEq(prev.getName(), next.getName())) {
            return false;
        }

        String minUpgradableVersion = next.getMinUpgradableVersion();

        return Util.isNullOrEmpty(minUpgradableVersion) ||
               Util.isNullOrEmpty(prev.getVersion()) ||
               isVersionGreaterThanOrEqual(prev.getVersion(), minUpgradableVersion);
    }

    /**
     * Used to compare two string versions and return true if the first string version
     * is numerically greater than or equal to the second string version.
     *
     * @param first
     * @param second
     * @return True if first string version is numerically greater than or equal to the
     *         second string version.  False otherwise or if either string is null.
     */
    public static boolean isVersionGreaterThanOrEqual(String first, String second) {

        if (Util.nullSafeEq(first, second, true)) {
            return true;
        }
        return parseAndCheckVersion(first, second, true);
    }

    /**
     * Returns if the version is >= or <= depending on the check flag.
     * This will basically walk down two version strings and compare each tokened split on "."
     * And compare each chunk at a time with an int comparison.
     *
     * If there is a letters or hashes such as "-beta" or "#ABC" it will be assumed a 0 for comparison.
     * @param first First version to check
     * @param second Second version to check
     * @param checkingGreaterOrEqual True to check >=, otherwise to check <=
     * @return True if it returns what the flag is set to
     */
    private static boolean parseAndCheckVersion(String first, String second, boolean checkingGreaterOrEqual) {
        // Split the version out into an array by "."
        String[] firstArray = first.split("\\.", -1);
        String[] secondArray = second.split("\\.", -1);

        for (int i = 0; i < firstArray.length; i++) {
            int firstNum = Util.atoi(firstArray[i]);
            int secondNum = Util.atoi(secondArray[i]);
            // if at any point they do not match, compare them
            if (firstNum != secondNum) {
                return checkingGreaterOrEqual ?
                       firstNum >= secondNum :
                       firstNum <= secondNum;
            }
            // When we reach the end of the second array, we have to exhaust the first array to see if
            // there are any zeros to check the equal comparison or not.
            // If the second array has a 1 a hundred places later, the second is bigger.
            if (secondArray.length - 1 == i && firstArray.length > secondArray.length) {
                return allZeros(i+1, firstArray) ?
                       true :
                       checkingGreaterOrEqual;
            }

        }

        // if we are at the end and they are matching lengths, they are equal.
        if (firstArray.length == secondArray.length) {
            return true;
        }

        // If they are not equal length, we will do the same thing with exhausting the second array.
        return allZeros(firstArray.length, secondArray) ?
               true :
               !checkingGreaterOrEqual;
    }

    /**
     * Check if the rest of the array that we have processed so far has any zeroes
     * @param i index
     * @param array array to check
     * @return True if the rest are all zeroes.
     */
    private static boolean allZeros(int i, String[] array) {
        for (; i < array.length; i++) {
            if (Util.atoi(array[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Used to compare two string versions and return true if the first string version
     * is numerically greater than or equal to the second string version.
     *
     * @param first
     * @param second
     * @return True if first string version is numerically greater than or equal to the
     *         second string version.  False otherwise or if either string is null.
     */
    public static boolean isVersionLessThanOrEqual(String first, String second) {
        if (Util.nullSafeEq(first, second, true)) {
            return true;
        }
        return parseAndCheckVersion(first, second, false);
    }

    /**
     * Determines if the user has access based on the specified required right.
     *
     * @param requiredRight The required right.
     * @param contentRequest The page content request.
     * @return True if has access, false otherwise.
     */
    private static boolean hasRequiredRight(String requiredRight, PageContentRequest contentRequest) {
        // user has access if there is no right, they are a system admin or they have the right
        return Util.isNullOrEmpty(requiredRight) ||
               contentRequest.isSystemAdmin() ||
               contentRequest.getUserRights().contains(requiredRight);

    }

    /**
     * Gets the plugins cache owned by the Environment.
     *
     * @return The plugins cache.
     */
    private static PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

    /**
     * Adds a Plugin Audit Event to the system
     *
     * @param auditEvent The type of Plugin AuditEvent to log
     * @param plugin The plugin
     * @param context The SailPoint Context
     * @throws GeneralException
     */
    public static void audit(String auditEvent, Plugin plugin, 
                             SailPointContext context) throws GeneralException {
        if (Auditor.isEnabled(auditEvent) && plugin != null) {

            AuditEvent event = new AuditEvent();
            event.setAction(auditEvent);
            event.setApplication(BrandingServiceFactory.getService().getApplicationName());
            event.setTarget(plugin.getName());

            // populate the attributes if the plugin configuration changed
            if (AuditEvent.PluginConfigurationChanged.equals(auditEvent)) {
                List<Setting> settings = plugin.getSettings();

                if (!Util.isEmpty(settings)) {
                    Attributes<String, Object> attrs = new Attributes<String, Object>();
                    for (Setting setting : settings) {
                        attrs.put(setting.getName(), setting.getValue());
                    }
                    event.setAttributes(attrs);
                }
            }

            Auditor.log(event);
            context.commitTransaction();
        }
    }

    /**
     * Check if the given package name is one that is restricted
     * by IdentityIQ from being exported from a plugin.
     *
     * Currently, the following are restricted:
     *      - empty package
     *      - package "sailpoint"
     *      - any package that starts with "sailpoint."
     *      - package "java"
     *      - any package that starts with "java."
     *
     * @param packageName the package name to check
     * @return true if restricted (forbidden), otherwise false
     */
    public static boolean isRestrictedPackage(String packageName) {
        return Util.isEmpty(packageName) ||
                packageName.startsWith("sailpoint.") ||
                "sailpoint".equals(packageName) ||
                packageName.startsWith("java.") ||
                "java".equals(packageName);
    }

}
