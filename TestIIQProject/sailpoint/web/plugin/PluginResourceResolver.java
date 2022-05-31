
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.plugin.PluginsUtil;
import sailpoint.plugin.protocol.spplugin.Handler;
import sailpoint.tools.Pair;

import javax.faces.view.facelets.ResourceResolver;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Resource resolver used to create a plugin-specific URL that references a
 * file that is cached in the plugin cache.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginResourceResolver extends ResourceResolver {

    /**
     * The log.
     */
    private static final Log log = LogFactory.getLog(PluginResourceResolver.class);

    /**
     * The plugins include prefix.
     */
    private static final String PLUGINS_PREFIX = "/plugin/include";

    /**
     * The parent resolver.
     */
    private ResourceResolver _parent;

    /**
     * Constructor.
     *
     * @param parent The parent resource resolver.
     */
    public PluginResourceResolver(ResourceResolver parent) {
        _parent = parent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL resolveUrl(String s) {
        URL url = _parent.resolveUrl(s);
        if (url == null && s.startsWith(PLUGINS_PREFIX)) {
            Pair<String, String> nameAndFile = PluginsUtil.getNameAndFileFromUrl(s, PLUGINS_PREFIX);

            String pluginName = nameAndFile.getFirst();
            String file = "/" + nameAndFile.getSecond();

            try {
                url = new URL(Handler.PROTOCOL, pluginName, -1, file, new Handler());
            } catch (MalformedURLException e) {
                log.error(e);
            }
        }

        return url;
    }

}
