package sailpoint.object;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.plugin.FullPage;
import sailpoint.plugin.Setting;
import sailpoint.plugin.Snippet;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Plugin object to store information about a specific Plugin used by the Plugin framework.
 * This class is used to describe any information about a particular plugin that is to be imported in the manifest.
 * @author brian.li
 *
 */
@XMLClass
public class Plugin extends SailPointObject {

    private static final long serialVersionUID = 3611982341861245882L;

    public static final String ATT_SETTINGS = "settings";
    public static final String ATT_SNIPPETS = "snippets";
    public static final String ATT_FULL_PAGE = "fullPage";
    public static final String ATT_MIN_UPGRADABLE_VERSION = "minUpgradableVersion";
    public static final String ATT_REST_RESOURCE_CLASS_NAMES = "restResources";
    public static final String ATT_RECOMMENDER_CLASS_NAMES = "recommenders";
    public static final String ATT_SERVICE_EXECUTOR_CLASS_NAMES = "serviceExecutors";
    public static final String ATT_SETTINGS_FORM_NAME = "settingsForm";
    public static final String ATT_SETTINGS_PAGE_NAME = "settingsPage";
    public static final String ATT_TASK_EXECUTOR_CLASS_NAMES = "taskExecutors";
    public static final String ATT_POLICY_EXECUTOR_CLASS_NAMES = "policyExecutors";
    public static final String ATT_SCRIPT_PACKAGE_NAMES = "scriptPackages";

    /**
     * Used when asking if a class has been declared as exported for the given type of usage
     */
    public enum ClassExportType {
        SERVICE_EXECUTOR,  // classname is present in ATT_SERVICE_EXECUTOR_CLASS_NAMES list attribute?
        POLICY_EXECUTOR,   // classname is present in ATT_POLICY_EXECUTOR_CLASS_NAMES list attribute?
        TASK_EXECUTOR,     // classname is present in ATT_TASK_EXECUTOR_CLASS_NAMES list attribute?
        RECOMMENDER,       // classname is present in ATT_RECOMMENDER_CLASS_NAMES list attribute?
        UNCHECKED          // classname doesn't need to be present in any list attribute

    }

    /**
     * Date the Plugin was installed or updated on.
     */
    private Date installDate;
    
    /**
     * Name to display to the UI and end user
     */
    private String displayName;

    /**
     * Version of the plugin
     *
     * The version of a plugin can either be official or development.
     *
     * Development versions end with the suffix '-dev' (e.g. 2.0-dev) and bypass most version checks
     * so that the plugin can be recompiled, upgraded and tested easily.
     *
     * Official versions drop the '-dev' suffix (e.g. 2.0) and can only be installed over a development
     * version or an earlier official version. Note that the minimum upgradeable version must also be valid.
     *
     * Valid upgrade paths:
     * 1.0 -> 2.0-dev
     * 2.0-dev -> 2.0-dev
     * 2.0-dev -> 2.0
     * 1.0 -> 2.0
     *
     * Invalid upgrade paths:
     * 2.0 -> 2.0
     * 2.0 -> 1.0
     */
    private String version;
    
    /**
     * If the plugin is disabled or not
     */
    private boolean disabled;

    /**
     * SPRight required for the plugin to work
     */
    private String rightRequired;
    
    /**
     * Minimum version of IdentityIQ that the plugin will run on
     */
    private String minSystemVersion;
    
    /**
     * Maximum version of IdentityIQ that the plugin will run on
     */
    private String maxSystemVersion;
    
    /**
     * Attributes map that contains the PluginConfiguration and any configurable settings needed by the framework
     */
    private Attributes<String, Object> attributes;
    
    /**
     * The level of certification that has been given to this plugin
     */
    private CertificationLevel certificationLevel;
    
    /**
     * Position of this plugin to be started in relation with the other plugins on the system
     */
    private int position;

    /**
     * The persisted zip file reference to the Plugin's original archive
     */
    private PersistedFile file;

    /**
     * Level of certification that this particular plugin is certified as
     * @author brian.li
     *
     */
    @XMLClass(xmlname="PluginCertificationLevel")
    public enum CertificationLevel implements Localizable, Serializable {
        None(MessageKeys.PLUGIN_CERTIFICATION_LEVEL_NONE),
        Bronze(MessageKeys.PLUGIN_CERTIFICATION_LEVEL_BRONZE),
        Silver(MessageKeys.PLUGIN_CERTIFICATION_LEVEL_SILVER),
        Gold(MessageKeys.PLUGIN_CERTIFICATION_LEVEL_GOLD);

        private String messageKey;
        
        private CertificationLevel(String messageKey) {
            this.messageKey = messageKey;
        }

        @Override
        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }
        @Override
        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(this.messageKey);
            return msg.getLocalizedMessage(locale, timezone);
        }
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitPlugin(this);
    }

    @XMLProperty
    public Date getInstallDate() {
        return installDate;
    }
    
    public void setInstallDate(Date installDate) {
        this.installDate = installDate;
    }

    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayableName() {
        String displayableName = getDisplayName();
        if (Util.isNullOrEmpty(displayableName)) {
            displayableName = getName();
        }

        return displayableName;
    }

    @XMLProperty
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @XMLProperty
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @XMLProperty
    public String getRightRequired() {
        return rightRequired;
    }

    public void setRightRequired(String rightRequired) {
        this.rightRequired = rightRequired;
    }

    @XMLProperty
    public String getMinSystemVersion() {
        return minSystemVersion;
    }

    public void setMinSystemVersion(String minSystemVersion) {
        this.minSystemVersion = minSystemVersion;
    }

    @XMLProperty
    public String getMaxSystemVersion() {
        return maxSystemVersion;
    }

    public void setMaxSystemVersion(String maxSystemVersion) {
        this.maxSystemVersion = maxSystemVersion;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    @XMLProperty
    public CertificationLevel getCertificationLevel() {
        return certificationLevel;
    }

    public void setCertificationLevel(CertificationLevel certificationLevel) {
        this.certificationLevel = certificationLevel;
    }

    @XMLProperty
    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public PersistedFile getFile() {
        return file;
    }

    public void setFile(PersistedFile file) {
        this.file = file;
    }

    /**
     * The minimum version of the plugin required for an older version of the plugin that can be upgraded
     */
    public String getMinUpgradableVersion() {
        return (String) Util.get(this.getAttributes(), ATT_MIN_UPGRADABLE_VERSION);
    }

    public void setMinUpgradableVersion(String minUpgradableVersion) {
        ensureAttributes();
        this.getAttributes().put(ATT_MIN_UPGRADABLE_VERSION, minUpgradableVersion);
    }

    public FullPage getFullPage() {
        return (FullPage) Util.get(this.getAttributes(), ATT_FULL_PAGE);
    }

    public void setFullPage(FullPage fullPage) {
        ensureAttributes();
        this.getAttributes().put(ATT_FULL_PAGE, fullPage);
    }

    @SuppressWarnings("unchecked")
    public List<Setting> getSettings() {
        return (List<Setting>) Util.get(this.getAttributes(), ATT_SETTINGS);
    }

    public void setSettings(List<Setting> settings) {
        ensureAttributes();
        this.getAttributes().put(ATT_SETTINGS, settings);
    }

    /**
     * Retrieves a SailPoint Form that maps to this plugin's settings.
     * @return A SailPoint Form object describing the plugin's settings.
     */
    public Form getSettingsForm() { return (Form) Util.get(this.getAttributes(), ATT_SETTINGS_FORM_NAME); }

    public void setSettingsForm(Form form) {
        ensureAttributes();
        this.getAttributes().put(ATT_SETTINGS_FORM_NAME, form);
    }

    /**
     * The name of the XHTML page that manages this plugin's settings.
     * @return Name of an XHTML page bundled in the plugin.
     */
    public String getSettingsPageName() { return Util.getString(this.getAttributes(), ATT_SETTINGS_PAGE_NAME); }

    public void setSettingsPageName(String pageName) {
        ensureAttributes();
        this.getAttributes().put(ATT_SETTINGS_PAGE_NAME, pageName);
    }

    @SuppressWarnings("unchecked")
    public List<Snippet> getSnippets() {
        return (List<Snippet>) Util.get(this.getAttributes(), ATT_SNIPPETS);
    }

    public void setSnippets(List<Snippet> snippets) {
        ensureAttributes();
        this.getAttributes().put(ATT_SNIPPETS, snippets);
    }

    @SuppressWarnings("unchecked")
    public List<String> getResourceClassNames() {
        return (List<String>) Util.get(getAttributes(), ATT_REST_RESOURCE_CLASS_NAMES);
    }

    public void setResourceClassNames(List<String> resourceClassNames) {
        ensureAttributes();
        getAttributes().put(ATT_REST_RESOURCE_CLASS_NAMES, resourceClassNames);
    }

    // scriptPackages

    @SuppressWarnings("unchecked")
    public List<String> getScriptPackageNames() {
        return (List<String>) Util.get(getAttributes(), ATT_SCRIPT_PACKAGE_NAMES);
    }

    public void setScriptPackageNames(List<String> scriptPackageNames) {
        ensureAttributes();
        getAttributes().put(ATT_SCRIPT_PACKAGE_NAMES, scriptPackageNames);
    }

    // recommenders

    @SuppressWarnings("unchecked")
    public List<String> getRecommenderClassNames() {
        return (List<String>) Util.get(getAttributes(), ATT_RECOMMENDER_CLASS_NAMES);
    }

    public void setRecommenderClassNames(List<String> recommenderClassNames) {
        ensureAttributes();
        getAttributes().put(ATT_RECOMMENDER_CLASS_NAMES, recommenderClassNames);
    }

    // serviceExecutors

    @SuppressWarnings("unchecked")
    public List<String> getServiceExecutorClassNames() {
        return (List<String>) Util.get(getAttributes(), ATT_SERVICE_EXECUTOR_CLASS_NAMES);
    }

    public void setServiceExecutorClassNames(List<String> serviceExecutorClassNames) {
        ensureAttributes();
        getAttributes().put(ATT_SERVICE_EXECUTOR_CLASS_NAMES, serviceExecutorClassNames);
    }

    public boolean hasServiceExecutorClassNames() {
        return !Util.isEmpty(getServiceExecutorClassNames());
    }

    // taskExecutors

    @SuppressWarnings("unchecked")
    public List<String> getTaskExecutorClassNames() {
        return (List<String>) Util.get(getAttributes(), ATT_TASK_EXECUTOR_CLASS_NAMES);
    }

    public void setTaskExecutorClassNames(List<String> taskExecutorClassNames) {
        ensureAttributes();
        getAttributes().put(ATT_TASK_EXECUTOR_CLASS_NAMES, taskExecutorClassNames);
    }

    public boolean hasTaskExecutorClassNames() {
        return !Util.isEmpty(getTaskExecutorClassNames());
    }

    // policyExecutors

    @SuppressWarnings("unchecked")
    public List<String> getPolicyExecutorClassNames() {
        return (List<String>) Util.get(getAttributes(), ATT_POLICY_EXECUTOR_CLASS_NAMES);
    }

    public void setPolicyExecutorClassNames(List<String> policyExecutorClassNames) {
        ensureAttributes();
        getAttributes().put(ATT_POLICY_EXECUTOR_CLASS_NAMES, policyExecutorClassNames);
    }

    public boolean hasPolicyExecutorClassNames() {
        return !Util.isEmpty(getPolicyExecutorClassNames());
    }

    /**
     * See {@link #version} for details about official and development versions.
     *
     * @return boolean True if this plugin's version is considered development.
     */
    public boolean isDevelopmentVersion() {
        return Util.isNotNullOrEmpty(this.version) && this.version.toLowerCase().endsWith("-dev");
    }

    private void ensureAttributes() {
        if (this.getAttributes() == null) {
            this.setAttributes(new Attributes<String, Object>());
        }
    }

    /**
     * Gets the timestamp of the last modification of the plugin. If
     * the modified property is null then the created date is used.
     *
     * @return The last modified timestamp.
     */
    public long getLastModified() {
        long lastModified = getCreated().getTime();
        if (getModified() != null) {
            lastModified = getModified().getTime();
        }

        return lastModified;
    }

    /**
     * Grabs the Setting object within the Plugin if it exists
     * @param settingName name of the Setting to pull
     * @return Setting object if it exists, null if not found
     */
    public Setting getSetting(String settingName) {
        if (Util.isNotNullOrEmpty(settingName)) {
            List<Setting> settings = this.getSettings();
            for (Setting s : Util.iterate(settings)) {
                if (settingName.equals(s.getName())) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Updates the value of the setting in the Plugin
     *
     * @param settingName name of the Setting to update
     * @param settingValue value to set on the Setting
     */
    public void updateSetting(String settingName, Object settingValue) {
        Setting setting = this.getSetting(settingName);
        if (setting != null) {
            if (setting.isMultiValue() && Setting.multiValueSupportedForType(setting)) {
                if (settingValue instanceof List) {
                    settingValue = Util.listToCsv((List) settingValue);
                }
            } else if (settingValue != null) {
                settingValue = settingValue.toString();
            }

            setting.setValue((String)settingValue);
        }
    }

    /**
     * Updates the settings on the Plugin
     *
     * @param settings Map of settings keyed by the setting name
     */
    public void updateSettings(Map<String, String> settings) {
        for (String settingName : Util.iterate(settings.keySet())) {
            this.updateSetting(settingName, settings.get(settingName));
        }
    }

    /**
     * Upgrades the current plugin data to match the new version of
     * the plugin. Most data will be completely overridden except for
     * the id, name, disabled, position and the value property of any
     * existing settings.
     *
     * @param plugin The plugin.
     */
    public void upgrade(Plugin plugin) {
        // not changing id, name, disabled or position
        setVersion(plugin.getVersion());
        setDisplayName(plugin.getDisplayName());
        setMinSystemVersion(plugin.getMinSystemVersion());
        setMaxSystemVersion(plugin.getMaxSystemVersion());
        setCertificationLevel(plugin.getCertificationLevel());
        setRightRequired(plugin.getRightRequired());
        setMinUpgradableVersion(plugin.getMinUpgradableVersion());

        setFullPage(plugin.getFullPage());
        setSnippets(plugin.getSnippets());
        setResourceClassNames(plugin.getResourceClassNames());
        setScriptPackageNames((plugin.getScriptPackageNames()));
        setServiceExecutorClassNames(plugin.getServiceExecutorClassNames());
        setTaskExecutorClassNames(plugin.getTaskExecutorClassNames());
        setPolicyExecutorClassNames(plugin.getPolicyExecutorClassNames());

        setSettingsForm(plugin.getSettingsForm());
        setSettingsPageName(plugin.getSettingsPageName());

        // set the new settings and then update the values with what is
        // already configured
        List<Setting> oldSettings = getSettings();

        setSettings(plugin.getSettings());
        for (Setting setting : Util.iterate(oldSettings)) {
            updateSetting(setting.getName(), setting.getValue());
        }
    }

    /**
     * Copies relevant data from this plugin to a new instance to
     * be used for exporting.
     *
     * @return The plugin for export.
     */
    public Plugin export() {
        Plugin plugin = new Plugin();
        plugin.setName(getName());
        plugin.setDisplayName(getDisplayName());
        plugin.setVersion(getVersion());
        plugin.setPosition(getPosition());
        plugin.setMinSystemVersion(getMinSystemVersion());
        plugin.setMaxSystemVersion(getMaxSystemVersion());
        plugin.setCertificationLevel(getCertificationLevel());
        plugin.setRightRequired(getRightRequired());

        if (!Util.isNullOrEmpty(getMinUpgradableVersion())) {
            plugin.setMinUpgradableVersion(getMinUpgradableVersion());
        }

        if (getFullPage() != null) {
            plugin.setFullPage(getFullPage());
        }

        if (!Util.isEmpty(getSnippets())) {
            plugin.setSnippets(getSnippets());
        }

        if (!Util.isEmpty(getResourceClassNames())) {
            plugin.setResourceClassNames(getResourceClassNames());
        }

        if (hasServiceExecutorClassNames()) {
            plugin.setServiceExecutorClassNames(getServiceExecutorClassNames());
        }

        if (hasTaskExecutorClassNames()) {
            plugin.setTaskExecutorClassNames(getTaskExecutorClassNames());
        }

        if (hasPolicyExecutorClassNames()) {
            plugin.setPolicyExecutorClassNames(getPolicyExecutorClassNames());
        }

        if (!Util.isEmpty(getScriptPackageNames())) {
            plugin.setScriptPackageNames(getScriptPackageNames());
        }

        if (!Util.isEmpty(getRecommenderClassNames())) {
            plugin.setRecommenderClassNames(getRecommenderClassNames());
        }

        if (!Util.isEmpty(getSettings())) {
            plugin.setSettings(getSettings());
        }

        return plugin;
    }
}
