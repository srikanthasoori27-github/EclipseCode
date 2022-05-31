
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.plugin;

import sailpoint.object.Plugin;
import sailpoint.plugin.Setting;
import sailpoint.service.BaseDTO;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * DTO object for the Plugin class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginDTO extends BaseDTO {

    /**
     * The plugin name.
     */
    private String _name;

    /**
     * The plugin display name.
     */
    private String _displayName;

    /**
     * Flag indicating if the plugin is disabled.
     */
    private boolean _disabled;

    /**
     * The plugin version.
     */
    private String _version;

    /**
     * The date the current version of the plugin was installed.
     */
    private Date _installDate;

    /**
     * The certification level of the plugin.
     * Used only for display purposes on the UI.
     */
    private String _certificationLevel;
    
    private List<Setting> _settings;

    /**
     * Indicates whether the plugin uses form-based settings, or a custom-coded settings page.
     */
    private boolean _hasCustomSettings;

    /**
     * Constructor.
     * @param plugin The plugin.
     */
    public PluginDTO(Plugin plugin) {
        this(plugin, Locale.getDefault());
    }

    /**
     * Constructor.
     *
     * @param plugin The plugin.
     * @param locale The locale from the browser.
     */
    public PluginDTO(Plugin plugin, Locale locale) {
        super(plugin.getId());

        setName(plugin.getName());
        setDisplayName(plugin.getDisplayableName());
        setDisabled(plugin.isDisabled());
        setInstallDate(plugin.getInstallDate());
        setVersion(plugin.getVersion());
        String localizedCertLevel = null;
        if (plugin.getCertificationLevel() != null) {
            localizedCertLevel = plugin.getCertificationLevel().getLocalizedMessage(locale, TimeZone.getDefault());
        }
        setCertificationLevel(localizedCertLevel);
        setSettings(plugin.getSettings());
        setHasCustomSettings(!Util.isNullOrEmpty(plugin.getSettingsPageName()));
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    public boolean isDisabled() {
        return _disabled;
    }

    public void setDisabled(boolean disabled) {
        _disabled = disabled;
    }

    public Date getInstallDate() {
        return _installDate;
    }

    public void setInstallDate(Date installDate) {
        _installDate = installDate;
    }

    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }

    public String getCertificationLevel() {
        return _certificationLevel;
    }

    public void setCertificationLevel(String certificationLevel) {
        _certificationLevel = certificationLevel;
    }

    public List<Setting> getSettings() {
        return _settings;
    }

    public void setSettings(List<Setting> settings) {
        this._settings = settings;
    }

    public boolean getHasCustomSettings() { return this._hasCustomSettings; }

    public void setHasCustomSettings(boolean hasCustomSettings) { this._hasCustomSettings = hasCustomSettings; }
}
