
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin.protocol.spplugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.plugin.PluginsCache;
import sailpoint.server.Environment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

/**
 * URL connection created by the plugin URL handler to connect to
 * a cached file in memory to be included through a <ui:include> tag.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpont.com>
 */
public class PluginURLConnection extends URLConnection {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(PluginURLConnection.class);

    /**
     * The name of the plugin.
     */
    private String _pluginName;

    /**
     * The name of the file.
     */
    private String _file;

    /**
     * The file data to serve.
     */
    private byte[] _contents;

    /**
     * Constructor.
     *
     * @param url The url.
     * @param pluginName The plugin name.
     * @param file The file name.
     */
    public PluginURLConnection(URL url, String pluginName, String file) {
        super(url);

        _pluginName = pluginName;
        _file = file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws IOException {
        _contents = getPluginsCache().getPluginFile(_pluginName, _file);
        connected = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected) {
            connect();
        }

        if (_contents == null) {
            LOG.warn(String.format("Unable to locate %s in %s", _file, _pluginName));
            _contents = "".getBytes();
        }

        return new ByteArrayInputStream(_contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastModified() {
        // looks like JSF caches the contents based on the last modified property so
        // just always return that it has been modified since the last read, this will
        // essentially be as fast as reading from the cache since we are actually reading
        // the contents from our own in-memory cache and no I/O operation will take place
        return new Date().getTime();
    }

    /**
     * Gets the plugins cache.
     *
     * @return The plugins cache.
     */
    private PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

}
