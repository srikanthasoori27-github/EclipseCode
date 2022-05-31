
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.plugin;

import sailpoint.plugin.PluginsCache;
import sailpoint.plugin.PluginsUtil;
import sailpoint.server.Environment;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet that serves up static files from the plugin cache.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginServlet extends HttpServlet {

    /**
     * The prefix that is used to parse the plugin name and file to serve.
     */
    private static final String PREFIX = "/";

    /**
     * Map containing the content types for supported file types.
     */
    private static final Map<String, String> CONTENT_TYPES;

    static {
        CONTENT_TYPES = new HashMap<>();
        CONTENT_TYPES.put("js", "application/javascript");
        CONTENT_TYPES.put("css", "text/css");
        CONTENT_TYPES.put("html", "text/html");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("gif", "image/gif");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
    }

    /**
     * Attempts to serve the requested static file from an installed plugin.
     *
     * @param req The HTTP request.
     * @param resp The HTTP response.
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // path info comes in as "/{pluginName}/{file}" so we just use '/' as the prefix
        Pair<String, String> nameAndFile = PluginsUtil.getNameAndFileFromUrl(req.getPathInfo(), PREFIX);

        String pluginName = nameAndFile.getFirst();
        String file = nameAndFile.getSecond();

        if (isValidRequest(pluginName, file)) {
            serveFile(resp, pluginName, file);
        } else {
            notFound(resp);
        }
    }

    /**
     * Determines if the plugin name and file are valid.
     *
     * @param pluginName The plugin name.
     * @param file The file.
     * @return True if valid, false otherwise.
     */
    private boolean isValidRequest(String pluginName, String file) {
        return Util.isNotNullOrEmpty(pluginName) && Util.isNotNullOrEmpty(file);
    }

    /**
     * Attempts to serve the specified plugin file. Sends a 404 response if the file
     * could not be found in the cache.
     *
     * @param resp The response.
     * @param pluginName The plugin name.
     * @param file The file.
     * @throws IOException
     */
    private void serveFile(HttpServletResponse resp, String pluginName, String file) throws IOException {
        byte[] fileData = getPluginsCache().getPluginFile(pluginName, file);
        if (fileData != null) {
            resp.setContentType(getContentTypeForFile(file));
            resp.setContentLength(fileData.length);

            if (isTextFile(file)) {
                resp.setCharacterEncoding("UTF-8");
            }

            resp.getOutputStream().write(fileData);
        } else {
            notFound(resp);
        }
    }

    /**
     * Determines if the file is a text file.
     *
     * @param file The file.
     * @return True if text file, false otherwise.
     */
    private boolean isTextFile(String file) {
        return file.endsWith("css") || file.endsWith("js") || file.endsWith("html");
    }

    /**
     * Sends a 404 error response.
     *
     * @param resp The response.
     * @throws IOException
     */
    private void notFound(HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Attempts to get the content type for the specified file.
     *
     * @param file The file.
     * @return The content type or null.
     */
    private String getContentTypeForFile(String file) {
        if (Util.isNullOrEmpty(file)) {
            return null;
        }

        // start substring one after the last '.'
        String extension = file.substring(file.lastIndexOf(".") + 1);

        return CONTENT_TYPES.get(extension);
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
