
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin.protocol.spplugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * URL stream handler for the plugin URL.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class Handler extends URLStreamHandler {

    /**
     * The protocol name.
     */
    public static final String PROTOCOL = "spplugin";

    /**
     * {@inheritDoc}
     */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        // plugin name is set as host upon creation of the URL
        String pluginName = u.getHost();

        // files are cached without leading / so try to safely strip it
        String file = u.getFile();
        if (file.length() > 1 && file.startsWith("/")) {
            file = file.substring(1);
        }

        return new PluginURLConnection(u, pluginName, file);
    }

}
