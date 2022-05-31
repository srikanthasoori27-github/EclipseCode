/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.system;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import sailpoint.api.SailPointContext;
import sailpoint.identityai.IdentityAIService;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.RecommenderDefinition;
import sailpoint.plugin.PluginsCache;
import sailpoint.recommender.RecommendationService;
import sailpoint.recommender.RecommenderFactory;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class IdentityAIConfigBean extends BaseBean {

    public static final int CONNECT_TIMEOUT_DEFAULT = 10;  // 10 secs
    public static final int READ_TIMEOUT_DEFAULT  = 60;    // 60 secs
    public static final String RECO_ENDPOINT_DEFAULT  = "beta/recommendations/request";
    public static final String RECO_CATALOG_ENDPOINT_DEFAULT  = "beta/translation-catalogs/recommender";

    // the hostname where IdentityAI can be reached.
    private String hostname;

    // OAuth client id
    private String clientId;

    // OAuth client secret
    private String clientSecret;

    // Maximum time to wait for a response before failing
    private int readTimeoutSeconds;

    // Maximum time to wait for a connection before failing
    private int connectTimeoutSeconds;

    public IdentityAIConfigBean() throws GeneralException {
            // TODO: handle errors or bad configs or whatever
        SailPointContext context = getContext();
        Configuration configuration = Configuration.getIdentityAIConfig();
        this.hostname = configuration.getString(Configuration.IAI_CONFIG_HOSTNAME);
        this.clientId = configuration.getString(Configuration.IAI_CONFIG_CLIENT_ID);
        this.clientSecret = configuration.getString(Configuration.IAI_CONFIG_CLIENT_SECRET);

        Integer rto = configuration.getInteger(Configuration.IAI_CONFIG_READ_TIMEOUT_SECS);
        if (rto == null || rto <= 0) {
            rto = READ_TIMEOUT_DEFAULT;
        }
        this.readTimeoutSeconds = rto.intValue();

        Integer cto = configuration.getInteger(Configuration.IAI_CONFIG_CONNECT_TIMEOUT_SECS);
        if (cto == null || cto <= 0) {
            cto = CONNECT_TIMEOUT_DEFAULT;
        }
        this.connectTimeoutSeconds = cto.intValue();
    }

    public String testAction() {
        Message msg = new Message(MessageKeys.IAI_CONFIG_TEST_CONNECTION_SUCCEEDED);
        FacesMessage.Severity severity = FacesMessage.SEVERITY_INFO;

        Configuration proposedConfiguration = getProposedConfiguration(null);
        try {
            IdentityAIService.testUnpooledConnection(proposedConfiguration);
        } catch (Exception ex) {
            msg = new Message(MessageKeys.IAI_CONFIG_TEST_CONNECTION_FAILED, ex.getLocalizedMessage());
            severity = FacesMessage.SEVERITY_ERROR;
        }


        FacesContext fc = FacesContext.getCurrentInstance();
        if((fc != null) && (msg != null)) {
            fc.addMessage(null, new FacesMessage(
                    severity, msg.getLocalizedMessage(), msg.getLocalizedMessage()));
        }

        return "test";
    }

    private Configuration getProposedConfiguration(Configuration currentConfig) {
        currentConfig = cloneConfiguration(Configuration.getIdentityAIConfig());
        currentConfig.setName(Configuration.IAI_CONFIG);

        applyConfigurationOverlay(currentConfig);
        return currentConfig;
    }

    private void applyConfigurationOverlay(Configuration currentConfig) {
        currentConfig.put(Configuration.IAI_CONFIG_HOSTNAME, Util.trimnull(hostname));
        currentConfig.put(Configuration.IAI_CONFIG_CLIENT_ID, Util.trimnull(clientId));
        currentConfig.put(Configuration.IAI_CONFIG_CLIENT_SECRET, Util.trimnull(clientSecret));
        currentConfig.put(Configuration.IAI_CONFIG_READ_TIMEOUT_SECS, readTimeoutSeconds);
        currentConfig.put(Configuration.IAI_CONFIG_CONNECT_TIMEOUT_SECS, connectTimeoutSeconds);
    }


    public String saveAction() throws GeneralException {
        SailPointContext context = getContext();
        Configuration configuration = Configuration.getIdentityAIConfig();
        applyConfigurationOverlay(configuration);
        context.saveObject(configuration);
        context.commitTransaction();
        return "save";
    }

    public String cancelAction() {
        return "cancel";
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() throws GeneralException {
        return getContext().decrypt(clientSecret);
    }

    public void setClientSecret(String clientSecret) throws GeneralException {
        this.clientSecret = getContext().encrypt(clientSecret);
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public String getPluginInstalled() {
        Message msg = null;
        try {
            RecommenderDefinition recommenderDefinition = RecommenderFactory.getRecommenderDefinition(getContext());
            if(recommenderDefinition == null) {
                msg = new Message(MessageKeys.IAI_CONFIG_RECOMMENDER_PLUGIN_NOT_SELECTED);
            } else {
                if (recommenderDefinition.getBoolean(RecommenderDefinition.ATT_IS_IAI_RECOMMENDER)) {
                    String pluginName = recommenderDefinition.getString(RecommenderDefinition.ATT_PLUGINNAME);
                    if (Util.isNotNullOrEmpty(pluginName)) {
                        PluginsCache plugins = Environment.getEnvironment().getPluginsCache();
                        if (plugins.getCachedPlugins().contains(pluginName)) {
                            RecommendationService recoService = RecommenderFactory.recommendationService(getContext());
                            if (recoService == null) {
                                msg = new Message(MessageKeys.IAI_CONFIG_COULD_NOT_CREATE_RECOMMENDER);
                            }
                        } else {
                            msg = new Message(MessageKeys.IAI_CONFIG_RECOMMENDER_PLUGIN_NOT_INSTALLED);
                        }
                    }
                } else {
                    msg = new Message(MessageKeys.IAI_CONFIG_RECOMMENDER_PLUGIN_NOT_SELECTED);
                }
            }
        } catch (GeneralException e) {
            msg = new Message(MessageKeys.IAI_CONFIG_RECOMMENDER_PLUGIN_NOT_INSTALLED);
        }
        FacesContext fc = FacesContext.getCurrentInstance();
        if((fc != null) && (msg != null)) {
            msg.setType(Message.Type.Warn);
            addMessage(msg);
        }

        return (msg == null) ? "Plugin OK" : msg.getKey();
    }

    private Configuration cloneConfiguration(Configuration in) {
        Configuration out = null;
        if (in != null) {
            out = new Configuration();
            Attributes<String,Object> newAttrs = cloneAttributes(in.getAttributes());
            out.setAttributes(newAttrs);
        }
        return out;
    }

    private Attributes<String,Object> cloneAttributes(Attributes<String,Object> inAttrs) {
        Attributes<String,Object> outAttrs = null;
        if (inAttrs != null) {
            outAttrs = new Attributes<>();
            for(String attributeName : inAttrs.keySet()) {
                outAttrs.put(attributeName, inAttrs.get(attributeName));
            }
        }
        return outAttrs;
    }

}  // class ImportBean
