package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.Plugin;
import sailpoint.object.SPRight;
import sailpoint.plugin.PluginsCache;
import sailpoint.plugin.PluginsUtil;
import sailpoint.plugin.Setting;
import sailpoint.rest.plugin.SailPointPluginRestApplication;
import sailpoint.server.Environment;
import sailpoint.service.plugin.PluginDTO;
import sailpoint.service.plugin.PluginsService;
import sailpoint.service.plugin.SettingDTO;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

/**
 * Sub resource for PluginsListResource. This class will handle the REST endpoints
 * for singular Plugins
 * @author brian.li
 *
 */
public class PluginResource extends BaseResource {

    private static final Log log = LogFactory.getLog(PluginResource.class);

    private String pluginId;

    public PluginResource(String pluginId, BaseResource parent) throws GeneralException {
        super(parent);
        this.pluginId = pluginId;
    }

    /**
     * Gets the Resource based on the plugin saved in the constructor
     *
     * @return PluginDTO object representing a Plugin
     * @throws GeneralException
     */
    @GET
    public PluginDTO getPlugin() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        PluginsService service = new PluginsService(getContext());
        Locale locale = request == null ? Locale.getDefault() : request.getLocale();

        Plugin plugin = service.getPlugin(pluginId);
        if (plugin == null) {
            throw new ObjectNotFoundException(Plugin.class, pluginId);
        }
        return new PluginDTO(plugin, locale);
    }

    /**
     * Updates the configuration on the plugin
     *
     * @param settings a Map<String, Object> of setting names to setting values to update on the plugin
     * @return Response object with 200 for a success or 500 for an error
     * @throws GeneralException 
     */
    @POST
    public Response saveConfiguration(Map<String, String> settings) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        PluginsService service = new PluginsService(getContext());
        Plugin plugin = service.getPlugin(pluginId);
        if (plugin == null) {
            throw new ObjectNotFoundException(Plugin.class, pluginId);
        }

        Response response = Response.ok().build();
        try {
            if (!Util.isEmpty(settings)) {
                service.updateSettings(plugin, settings, getPluginsCache());
                audit(AuditEvent.PluginConfigurationChanged, plugin);
            }
        } catch (Exception e) {
            log.error(e);
            response = Response.serverError().entity(e).build();
        }

        return response;
    }

    /**
     * Validates a plugin name to make sure it matches the name associated with the
     * plugin id.
     *
     * @param pluginName the name to validate against the id (from the url path)
     * @return Response object with 200 for a success or 500 for an error
     * @throws GeneralException
     */
    @Path("validatePluginName")
    @POST
    public Response validatePluginName(String pluginName) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));
        PluginsService service = new PluginsService(getContext());

        Plugin plugin = service.getPlugin(pluginId);
        if ((pluginName == null) || (plugin == null) || (!pluginName.equals(plugin.getName()))) {
            throw new GeneralException("Plugin name does not match plugin id");
        }
        return Response.ok().build();
    }
    /**
     * Toggles the disabled status of the plugin
     *
     * @return Repsonse object with 200 for a success or 500 for an error
     * @throws GeneralException
     */
    @POST
    @Path("toggle")
    public Response togglePlugin(@QueryParam("disabled") boolean disabled) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        PluginsService service = new PluginsService(getContext());
        Plugin plugin = service.getPlugin(pluginId);
        if (plugin == null) {
            throw new ObjectNotFoundException(Plugin.class, pluginId);
        }

        Response response = Response.ok().build();
        try {
            // do not bother if you want to set the same state
            if (disabled != plugin.isDisabled()) {
                service.togglePlugin(plugin, disabled, getPluginsCache());

                reloadPluginRestContainer(plugin);

                if (disabled) {
                    audit(AuditEvent.PluginDisabled, plugin);
                } else {
                    audit(AuditEvent.PluginEnabled, plugin);
                }
            }
        } catch (Exception e) {
            log.error(e);
            response = Response.serverError().entity(e).build();
        }

        return response;
    }

    /**
     * Gets settings information (as settings DTOs) for a plugin.
     *
     * @return A list of settings for the plugin.
     * @throws GeneralException
     */
    @GET
    @Path("settings")
    public List<SettingDTO> getSettings() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        PluginsService service = new PluginsService(getContext());
        Plugin plugin = service.getPlugin(pluginId);
        if (plugin == null) {
            throw new ObjectNotFoundException(Plugin.class, pluginId);
        }

        List<SettingDTO> settingDTOs = new ArrayList<SettingDTO>();

        for (Setting setting : Util.safeIterable(plugin.getSettings())) {
            // if no value is persisted, use the default value.
            if(Util.isNullOrEmpty(setting.getValue())) {
                if(Util.isNotNullOrEmpty(setting.getDefaultValue())) {
                    setting.setValue(setting.getDefaultValue());
                }
            }
            SettingDTO settingDTO = new SettingDTO();
            settingDTO.setName(setting.getName());
            settingDTO.setAllowedValues(setting.getAllowedValues());
            settingDTO.setDataType(setting.getDataType());
            settingDTO.setDefaultValue(setting.getDefaultValue());
            settingDTO.setHelpText(setting.getHelpText());
            settingDTO.setLabel(setting.getLabel());
            settingDTO.setMultiValue(setting.isMultiValue() && Setting.multiValueSupportedForType(setting));
            settingDTO.setValue(setting.getValue());
            settingDTO.setReferencedObject(getObjectForValue(setting.getValue(), setting.getDataType()));

            handleMultiValue(settingDTO, setting);
            handleSecret(settingDTO, setting);
            settingDTOs.add(settingDTO);
        }

        return settingDTOs;
    }

    private Map<String, Object> getObjectForValue(String value, String dataType) throws GeneralException {
        Map<String, Object> settingObject = null;

        if (Setting.TYPE_IDENTITY.equals(dataType)) {
            if (Util.isNotNullOrEmpty(value)) {
                settingObject = SuggestHelper.getSuggestObject(
                        Identity.class, value, getContext());
            }
        } else if (Setting.TYPE_APPLICATION.equals(dataType)) {
            if (Util.isNotNullOrEmpty(value)) {
                settingObject = SuggestHelper.getSuggestObject(
                        Application.class, value, getContext());
            }
        } else if (Setting.TYPE_MANAGED_ATTRIBUTE.equals(dataType)) {
            if (Util.isNotNullOrEmpty(value)) {
                settingObject = ManagedAttributesResource.getSuggestObject(
                        value, getContext(), null);
            }
        } else if (Setting.TYPE_BUNDLE.equals(dataType)) {
            if (Util.isNotNullOrEmpty(value)) {
                settingObject = SuggestHelper.getSuggestObject(
                        Bundle.class, value, getContext());
            }
        }

        return settingObject;
    }

    private void handleMultiValue(SettingDTO settingDTO, Setting setting) throws GeneralException {
        if (setting.isMultiValue() && Setting.multiValueSupportedForType(setting)) {
            List<String> stringValues = Util.csvToList(setting.getValue());
            List<Object> convertedValues = new ArrayList<Object>();

            for(String value : stringValues) {
                Map<String, Object> objectValue = getObjectForValue(value, setting.getDataType());
                convertedValues.add(objectValue != null ? objectValue : value);
            }

            settingDTO.setMultiValueList(convertedValues);
        }
    }

    private void handleSecret(SettingDTO settingDTO, Setting setting) throws GeneralException {
        if (Setting.TYPE_SECRET.equals(setting.getDataType())) {
            if (Util.isNotNullOrEmpty(setting.getValue())) {
                settingDTO.setValue(getContext().decrypt(setting.getValue()));
            }
        }
    }

    @DELETE
    public Response uninstallPlugin() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessPlugin));

        PluginsService service = new PluginsService(getContext());
        Plugin plugin = service.getPlugin(pluginId);
        if (plugin == null) {
            throw new ObjectNotFoundException(Plugin.class, pluginId);
        }

        Response response = Response.ok().build();
        try {
            service.uninstallPlugin(plugin, isRunSqlScripts(), getPluginsCache());
            audit(AuditEvent.PluginUninstalled, plugin);
        } catch (Exception e) {
            log.error(e);
            response = Response.serverError().entity(e.getMessage()).build();
        }

        return response;
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
     * Reloads the plugin REST application container if necessary.
     *
     * @param plugin The plugin.
     */
    private void reloadPluginRestContainer(Plugin plugin) {
        SailPointPluginRestApplication.reload(plugin);
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
