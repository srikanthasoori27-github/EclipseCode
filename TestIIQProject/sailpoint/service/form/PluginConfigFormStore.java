package sailpoint.service.form;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Form;
import sailpoint.object.Plugin;
import sailpoint.service.plugin.PluginsService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

public class PluginConfigFormStore extends SimpleFormStore {

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(PluginConfigFormStore.class);

    public static final String PLUGIN_MASTER_FORM = "plugin_config_master_form";
    private static final String ARG_PLUGIN_ID = "pluginId";

    /**
     * Id of the plugin using Forms
     */
    private String pluginId;

    /**
     * Plugin object using Forms
     */
    private Plugin plugin;

    private Form pluginForm;

    /**
     * Constructor signature necessary for reconstructing this object through reflection.
     *
     * @param userContext The user context.
     * @param state The form store state.
     */
    public PluginConfigFormStore(UserContext userContext, Map<String, Object> state) {

        super(userContext, state);

        if (state.containsKey(ARG_PLUGIN_ID)) {
            this.pluginId = (String) state.get(ARG_PLUGIN_ID);
        }
    }

    /**
     * Constructor used on form retrieval.
     *
     * @param userContext The user context.
     * @param pluginId Plugin id.
     */
    public PluginConfigFormStore(UserContext userContext, String pluginId) {

        super(userContext, new HashMap<>());

        this.pluginId = pluginId;

        try {
            this.pluginForm = this.getPlugin(pluginId).getSettingsForm();
        } catch (GeneralException ge) {
            // This will leave formIdOrName blank, which is handled in the SimpleFormStore code.
            // But, let's log what happened here for debugging purposes.
            log.error("Unable to retrieve plugin from data store", ge);
        }
    }

    /**
     * Returns the form arguments
     * @return The form arguments
     */
    @Override
    public Map<String,Object> getFormArguments() {
        Map<String, Object> args;

        if (this.formRenderer != null) {
            args = this.formRenderer.getData();
            if (args == null) {
                args = new HashMap<String, Object>();
            }
            return args;
        }

        return new HashMap<String, Object>();
    }

    /**
     * Gets the state necessary to recreate the form bean. For this class, the plugin
     * id and the form id comprise the state.
     *
     * @return The form bean state.
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        Map<String, Object> args = super.getFormBeanState();
        args.put(ARG_PLUGIN_ID, this.pluginId);

        return args;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form retrieveMasterForm() throws GeneralException {
        /* If the master form is stored on the session use that.  Otherwise use the form specified in the plugin. */
        if (sessionStorage.get(PLUGIN_MASTER_FORM) != null) {
            this.masterForm = (Form) sessionStorage.get(PLUGIN_MASTER_FORM);
        } else if (this.pluginForm == null) {
            if (!Util.isNullOrEmpty(this.pluginId)) {
                PluginsService pluginsService = new PluginsService(this.getContext());
                this.masterForm = pluginsService.getSettingsAsForm(this.pluginId);
            } else {
                throw new GeneralException("Form name not specified");
            }
        } else {
            this.masterForm = this.pluginForm;
        }

        return this.masterForm;
    }

    /**
     * Stores the master form on the session storage
     *
     * @param form The expanded form.
     */
    @Override
    public void storeMasterForm(Form form) {
        super.storeMasterForm(form);
        sessionStorage.put(PLUGIN_MASTER_FORM, form);
    }

    /**
     * Clears the master form off of the session storage
     */
    @Override
    public void clearMasterForm() {
        super.clearMasterForm();
        sessionStorage.remove(PLUGIN_MASTER_FORM);
    }

    /**
     * Returns whether the form store has a form specified.
     *
     * @return True if a form is present, otherwise false.
     */
    public boolean hasForm() { return this.pluginForm != null; }

    /**
     * Retrieve the Plugin object based on the id
     * @param pluginId Id of the plugin to retrieve
     * @return Plugin object with the specified id
     * @throws GeneralException If there is an error retrieving the plugin
     */
    private Plugin getPlugin(String pluginId) throws GeneralException {
        if (this.plugin == null) {
            this.plugin = getContext().getObjectById(Plugin.class, pluginId);
        }

        return this.plugin;
    }
}
