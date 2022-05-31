
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.server.Environment;

/**
 * Class loader that is used to load classes and resources from a plugin.
 * Each installed plugin will have its own instance of this class loader.
 *
 * The classes provided by a plugin are cached in memory and defined the
 * first time they are needed. When they are defined they are removed from
 * the cache.
 *
 * This class loader inverts the normal class loading delegation scheme and
 * tries to load classes itself first instead of delegating to the parent
 * class loader first.
 *
 * The resources defined for this ClassLoader are made available through
 * getResourcesAsStream(name).
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginClassLoader extends ClassLoader {

    private static Log log = LogFactory.getLog(PluginClassLoader.class);

    /**
     * Prefix used for finding the message property files needed for the ResourceBundle.
     * The cache used for the files are keyed by the full file path for each file entry.
     */
    private static final String MESSAGES_DIR = "messages/";

    /**
     * The name of the plugin for this classloader
     */
    private final String _pluginName;

    /**
     * Cache of classes that the plugin has provided.
     */
    private final Map<String, byte[]> _classes;

    /**
     * Cache of resources that the plugin has provided.
     */
    private final Map<String, byte[]> _resources;

    /**
     * the names of the classes that the plugin
     * can provide.
     */
    private final Set<String> _classNames;

    /**
     * Constructor.
     *
     * @param pluginName The plugin name.
     * @param parent The parent class loader.
     * @param classes The plugin provided classes cache.
     * @param resources The plugin provided resources cache.
     */
    public PluginClassLoader(String pluginName, ClassLoader parent, Map<String, byte[]> classes, Map<String, byte[]> resources) {
        super(parent);

        _pluginName = pluginName;
        _classes = new HashMap<>(classes);
        _classNames = new HashSet<String>(_classes.keySet());
        _resources = new HashMap<>(resources);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = _classes.remove(name);
        if (classData != null) {
            return defineClass(name, classData, 0, classData.length);
        }

        return super.findClass(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> result = findLoadedClass(name);
            if (result != null) {
                return result;
            }

            try {
                result = findClass(name);
                if (result != null) {
                    return result;
                }
            } catch (ClassNotFoundException e) {
                // ignore since we are inverting delegation
            }

            return super.loadClass(name);
        }
    }

    /**
     * Overridden to first look in the resources cache to find a resource, before letting the super
     * class loader try to find the resource.
     *
     * @param name  The name of the resource.
     *
     * @return An InputStream for the requested resource, or null if the resource cannot be found.
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        // If the resources map contains this resource, return it.
        if (_resources.containsKey(name)) {
            byte[] resourceData = _resources.get(name);
            return new ByteArrayInputStream(resourceData);
        }

        // Otherwise, let super do its business.
        return super.getResourceAsStream(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL findResource(String name) {
        URL res = super.findResource(name);

        if (res == null) {
            try {
                String propertyFilePath = MESSAGES_DIR + name;
                // Grab the static cache of files for this plugin and use that to look for a specific resource
                Map<String, byte[]> files = Environment.getEnvironment().getPluginsCache().getFiles(_pluginName);

                if (files.containsKey(propertyFilePath)) {
                    byte[] file = files.get(propertyFilePath);
                    if (file != null) {
                        // here we have to use a URLStreamHandler which uses a URLConnection to get back a URL
                        // resource for our byte[] representation of the file
                        res = new URL(null, "bytes:///" + propertyFilePath, new PluginURLStreamHandler(file));
                    }
                } else if (_resources.containsKey(name)) {
                    // The resource is in cached _resources.
                    byte[] resourceData = _resources.get(name);
                    res = new URL(null, "bytes:///" + name, new PluginURLStreamHandler(resourceData));
                }

            } catch (MalformedURLException e) {
                log.error(e);
            }
        }

        return res;
    }

    public Set<String> getClassNames() {
        return _classNames;
    }

    /**
     * Extended URLStreamHandler to take in the byte array file representation
     * to pass it onto the URLConnection
     * @author Brian Li <brian.li@sailpoint.com>
     *
     */
    private class PluginURLStreamHandler extends URLStreamHandler {
        /**
         * The byte representation of the cached file
         */
        private byte[] _file;

        public PluginURLStreamHandler(byte[] file) {
            _file = file;
        }

        /**
         * Override this method of URLStreamer to use our own ByteURL Connection
         */
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return new ByteURLConnection(url, _file);
        }

    }

    /**
     * An extended version of URLConnection to provide a ByteArrayInputStream
     * in order to serve our files from our cached byte array representation of a file.
     * @author Brian Li <brian.li@sailpoint.com>
     *
     */
    private class ByteURLConnection extends URLConnection {
        /**
         * The byte array of the file
         */
        private byte[] _file;

        protected ByteURLConnection(URL url, byte[] file) {
            super(url);
            _file = file;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            // return this as a ByteArrayInputStream so that it can be ultimately
            // utilized by the ResourceBundle.getBundle()
            return new ByteArrayInputStream(_file);
        }

        @Override
        public void connect() throws IOException {}
    }
}
