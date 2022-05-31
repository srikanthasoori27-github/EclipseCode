
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.AuditEvent;
import sailpoint.object.Plugin;
import sailpoint.object.SPRight;
import sailpoint.plugin.PluginInstallationException;
import sailpoint.plugin.PluginInstaller.PluginInstallationResult;
import sailpoint.plugin.PluginsCache;
import sailpoint.plugin.PluginsUtil;
import sailpoint.rest.plugin.SailPointPluginRestApplication;
import sailpoint.server.Environment;
import sailpoint.service.plugin.PluginDTO;
import sailpoint.service.plugin.PluginsService;
import sailpoint.service.plugin.PluginsService.PluginInstallData;
import sailpoint.tools.GeneralException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resource responsible for providing functionality related to plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Path("plugins")
public class PluginsListResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(PluginsListResource.class);

    /** Sub Resource Methods **/
    @Path("{pluginId}")
    public PluginResource getPlugin(@PathParam("pluginId") String pluginId)
        throws GeneralException {

        return new PluginResource(pluginId, this);
    }

    /**
     * Gets a list of all plugins in the system.
     *
     * @return The list result.
     * @throws GeneralException
     */
    @GET
    public ListResult getPlugins() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        Locale locale = request == null ? Locale.getDefault() : request.getLocale();
        List<PluginDTO> plugins = getService().getAllPlugins(locale);

        return new ListResult(plugins, plugins.size());
    }

    /**
     * Endpoint responsible for handling the zip file that contains the plugin. Processes
     * the plugin zip file and installs or upgrades the plugin to the system.
     *
     * @param fileInputStream The InputStream used to read the file.
     * @param contentDisposition The file content disposition.
     * @return The response.
     * @throws GeneralException
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response install(@FormDataParam("file") InputStream fileInputStream,
                            @FormDataParam("fileName") String fileName)
        throws GeneralException {

        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        // cache after install, pass run scripts and import objects
        // flags set in iiq.properties
        PluginInstallData installData = new PluginInstallData(
            fileName,
            fileInputStream,
            true,
            isRunSqlScripts(),
            isImportObjects()
        );

        try {
            PluginsService pluginsService = getService();
            PluginInstallationResult result = pluginsService.installPlugin(installData, getPluginsCache());
            Plugin plugin = result.getPlugin();

            reloadPluginRestContainer(plugin);

            if (PluginInstallationResult.INSTALL.equals(result.getOperation())) {
                audit(AuditEvent.PluginInstalled, plugin);
            } else {
                audit(AuditEvent.PluginUpgraded, plugin);
            }

            return Response.ok().build();
        } catch (PluginInstallationException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse(e)).build();
        }
    }

    /**
     * Reloads the plugin REST application container if necessary.
     *
     * @param plugin The plugin.
     */
    private void reloadPluginRestContainer(Plugin plugin) {
        SailPointPluginRestApplication.reload(plugin);
    }

    /**
     * Creates the error response body using the exception.
     *
     * @param e The exception.
     * @return The response.
     */
    private Map<String, String> errorResponse(PluginInstallationException e) {
        Map<String, String> body = new HashMap<>();
        body.put("message", e.getLocalizedMessage(getLocale(), getUserTimeZone()));

        return body;
    }

    /**
     * Creates an instance of PluginsService.
     *
     * @return The service.
     */
    private PluginsService getService() {
        return new PluginsService(getContext());
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
     * Queries the plugins configuration to determine if SQL scripts should
     * be run in the installation process.
     *
     * @return True if run SQL scripts, false otherwise.
     */
    private boolean isRunSqlScripts() {
        return Environment.getEnvironment().getPluginsConfiguration().isRunSqlScripts();
    }

    /**
     * Queries the plugins configuration to determine if XML object files should
     * be imported during the installation process.
     *
     * @return True if import objects, false otherwise.
     */
    private boolean isImportObjects() {
        return Environment.getEnvironment().getPluginsConfiguration().isImportObjects();
    }

    /**
     * Audits the plugin with the associated event.
     *
     * @param auditEvent The audit event to log.
     * @param plugin The plugin to audit.
     * @throws GeneralException
     */
    private void audit(String auditEvent, Plugin plugin) throws GeneralException {
        PluginsUtil.audit(auditEvent, plugin, getContext());
    }
}
