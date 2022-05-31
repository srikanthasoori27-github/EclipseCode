/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.system;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.NameIDType;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.object.SAMLConfig;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.LoginConfigBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

import javax.faces.model.SelectItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * SAML config bean to support the Login Configuration settings UI tab.
 *
 * Author: michael.hide
 * Created: 1/22/14 11:48 AM
 */
public class SAMLConfigBean extends BaseBean {

    private static final Log log = LogFactory.getLog(SAMLConfigBean.class);

    private SAMLConfig samlConfigObject;
    private Map<String, String> nameIdFormatMap;
    private List<String> nameIdFormatList;
    private Map<String, String> bindingMethodMap;
    private List<String> bindingMethodList;
    private boolean samlEnabled;
    private boolean ruleBasedSSOEnabled;
    private Configuration config;
    private Configuration sysConfig;
    private Attributes _previousConfig;

    public SAMLConfigBean() throws GeneralException {
        super();
        this.initConfigs();
        this.init();
    }

    /**
     * Initialize the config objects
     *
     * @throws GeneralException
     */
    private void initConfigs() throws GeneralException {
        Configuration config = getSamlConfiguration();
        this.samlConfigObject = (SAMLConfig) config.get(this.getProvider());

        //Save off current version of SAMLConfig for Auditing changes
        _previousConfig = new Attributes(config.getAttributes());

        if (null == this.samlConfigObject) {
            this.samlConfigObject = new SAMLConfig();
        }

        ruleBasedSSOEnabled = getContext().getConfiguration().getBoolean(Configuration.RULE_BASED_SSO_ENABLED, true);
        samlEnabled = getContext().getConfiguration().getBoolean(Configuration.SAML_ENABLED, false);
    }

    /**
     * Initialize the Maps used to store the SAML constants.
     *
     * @throws GeneralException
     */
    private void init() throws GeneralException {
        /**
         * Populate the NameIdFormat map, storing each constant with
         * a 'short name' key parsed from the last segment of the constant.
         * i.e. 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent' is stored with
         * the key 'persistent'.  The key is then displayed in the System Config
         * selector instead of the full constant.
         */
        nameIdFormatMap = new HashMap<String, String>();
        parseConstantIntoMap(nameIdFormatMap, NameIDType.PERSISTENT);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.TRANSIENT);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.EMAIL);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.UNSPECIFIED);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.X509_SUBJECT);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.WIN_DOMAIN_QUALIFIED);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.KERBEROS);
        parseConstantIntoMap(nameIdFormatMap, NameIDType.ENTITY);

        nameIdFormatList = new ArrayList<String>();
        nameIdFormatList.addAll(nameIdFormatMap.keySet());
        Collections.sort(nameIdFormatList, String.CASE_INSENSITIVE_ORDER);

        /**
         * Populate the binding method map and list.
         * Same deal as above.
         */
        bindingMethodMap = new HashMap<String, String>();
        parseConstantIntoMap(bindingMethodMap, SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        parseConstantIntoMap(bindingMethodMap, SAMLConstants.SAML2_POST_BINDING_URI);

        bindingMethodList = new ArrayList<String>();
        bindingMethodList.addAll(bindingMethodMap.keySet());
        Collections.sort(bindingMethodList);
    }

    /**
     * Helper function to store the 'short name' of the SAML constants.
     *
     * @param m The map to store the key/value in.
     * @param s The SAML constant to store.
     */
    private void parseConstantIntoMap(Map<String, String> m, String s) {
        m.put(s.substring(s.lastIndexOf(':')+1).replace('-', ' '), s);
    }

    /**
     * Gets the SAML configuration object.
     *
     * @return The configuration.
     * @throws GeneralException
     */
    private Configuration getSamlConfiguration() throws GeneralException {
        if (null == config) {
            config = getContext().getObjectByName(Configuration.class, Configuration.SAML);

            // If it's null the config object was not imported, so create one.
            if (null == config) {
                config = new Configuration();
                config.setName(Configuration.SAML);
            }
        }
        return config;
    }

    /**
     * Convenience method to get the uncached version of system config.
     * We need this when saving values, but not when getting them.
     *
     * @return System config object
     * @throws GeneralException
     */
    private Configuration getSystemConfiguration() throws GeneralException {
        if(null == sysConfig) {
            sysConfig = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        }
        return sysConfig;
    }

    /**
     * Save the UI values into the SAMLConfig object.
     *
     * @param loginConfig
     * @return boolean indicating if the save was successful or not.
     * @throws GeneralException
     */
    public boolean save(LoginConfigBean loginConfig) throws GeneralException {
        // Validate first.
        if (!validate(loginConfig)) {
            return false;
        }

        // Use the non-cached sys config to store it, but the cached version to save it.
        // Not sure why, but that seems to work.
        getSystemConfiguration().put(Configuration.RULE_BASED_SSO_ENABLED, this.isRuleBasedSSOEnabled());
        getSystemConfiguration().put(Configuration.SAML_ENABLED, this.isSamlEnabled());
        getContext().saveObject(getContext().getConfiguration());

        getSamlConfiguration().put(this.getProvider(), this.samlConfigObject);
        getContext().saveObject(getSamlConfiguration());

        //Not sure why we're committing here -rap
        getContext().commitTransaction();

        auditSAMLConfigChange(loginConfig);

        return true;
    }

    private static String[][] SAML_CONFIG_ATTRIBUTE_NAMES = {
            {Configuration.RULE_BASED_SSO_ENABLED, MessageKeys.SSO_ENABLE_RULE_BASED},
            {Configuration.SAML_ENABLED, MessageKeys.SSO_ENABLE_SAML_BASED}
    };

    private void auditSAMLConfigChange(LoginConfigBean loginConfig) throws GeneralException {
        if (loginConfig != null) {
            AuditEvent evt = loginConfig.getAuditEvent();
            if (evt != null) {
                //Should be non-null if auditing is enabled
                Map<String, Object> origConfig = loginConfig.getOriginalConfig();
                if (origConfig != null) {
                    Attributes origAtts = (Attributes) origConfig.get(Configuration.OBJ_NAME);
                    Attributes newAtts = getSystemConfiguration().getAttributes();
                    if (origAtts != null) {
                        for (int i=0; i<SAML_CONFIG_ATTRIBUTE_NAMES.length; i++) {
                            Object origValue = origAtts.get(SAML_CONFIG_ATTRIBUTE_NAMES[i][0]);
                            Object neuVal = newAtts.get(SAML_CONFIG_ATTRIBUTE_NAMES[i][0]);

                            if (!Util.nullSafeEq(origValue, neuVal, true, true)) {
                                evt.setAttribute(SAML_CONFIG_ATTRIBUTE_NAMES[i][1],
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origValue, neuVal));
                            }
                        }
                    }
                }

                //Audit SAMLConfig object
                SAMLConfig prevConfig = (SAMLConfig) _previousConfig.get(getProvider());
                if (prevConfig == null) {
                    //Instantiate an empty SAMLConfig to prevent NPE
                    prevConfig = new SAMLConfig();
                }
                if (!Util.nullSafeEq(prevConfig.getEntityId(), this.samlConfigObject.getEntityId(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[entityId]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getEntityId(), this.samlConfigObject.getEntityId()));
                }

                if (!Util.nullSafeEq(prevConfig.getIdpServiceUrl(), this.samlConfigObject.getIdpServiceUrl(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[idpServiceUrl]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getIdpServiceUrl(), this.samlConfigObject.getIdpServiceUrl()));
                }

                if (!Util.nullSafeEq(prevConfig.getIssuer(), this.samlConfigObject.getIssuer(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[issuer]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getIssuer(), this.samlConfigObject.getIssuer()));
                }

                if (!Util.nullSafeEq(prevConfig.getBindingMethod(), this.samlConfigObject.getBindingMethod(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[bindingMethod]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getBindingMethod(), this.samlConfigObject.getBindingMethod()));
                }

                if (!Util.nullSafeEq(prevConfig.getNameIdFormat(), this.samlConfigObject.getNameIdFormat(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[nameIdFormat]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getNameIdFormat(), this.samlConfigObject.getNameIdFormat()));
                }

                if (!Util.nullSafeEq(prevConfig.getIdpPublicKey(), this.samlConfigObject.getIdpPublicKey(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[idpPublicKey]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getIdpPublicKey(), this.samlConfigObject.getIdpPublicKey()));
                }

                if (!Util.nullSafeEq(prevConfig.getSamlCorrelationRule(), this.samlConfigObject.getSamlCorrelationRule(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[samlCorrelationRule]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getSamlCorrelationRule(), this.samlConfigObject.getSamlCorrelationRule()));
                }

                if (!Util.nullSafeEq(prevConfig.getAssertionConsumerService(), this.samlConfigObject.getAssertionConsumerService(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[assertionConsumerService]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getAssertionConsumerService(), this.samlConfigObject.getAssertionConsumerService()));
                }

                if (!Util.nullSafeEq(prevConfig.getAuthnContextClassRef(), this.samlConfigObject.getAuthnContextClassRef(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[authnContextClassRef]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getAuthnContextClassRef(), this.samlConfigObject.getAuthnContextClassRef()));
                }

                if (!Util.nullSafeEq(prevConfig.getAuthnContextComparison(), this.samlConfigObject.getAuthnContextComparison(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[authnContextComparison]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getAuthnContextComparison(), this.samlConfigObject.getAuthnContextComparison()));
                }

                if (!Util.nullSafeEq(prevConfig.getSpNameQualifier(), this.samlConfigObject.getSpNameQualifier(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[spNameQualifier]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getSpNameQualifier(), this.samlConfigObject.getSpNameQualifier()));
                }

                if (!Util.nullSafeEq(prevConfig.getAddress(), this.samlConfigObject.getAddress(), true, true)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.SAML + "] " + getProvider() + "[address]",
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    prevConfig.getAddress(), this.samlConfigObject.getAddress()));
                }


            }
        }
    }

    /**
     * Validate that the fields are not empty.
     *
     * @param loginConfig
     * @return true if all fields are populated, false otherwise.
     * @throws GeneralException
     */
    public boolean validate(LoginConfigBean loginConfig) throws GeneralException {
        boolean valid = true;

        if (this.isSamlEnabled()) {
            if (Util.isNullOrEmpty(this.getEntityId())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_ENTITY_ID).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            if (Util.isNullOrEmpty(this.getIdpServiceUrl())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_IDP_SERVICE_URL).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            if (Util.isNullOrEmpty(this.getAssertionConsumerService())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_SAML_ACS).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            if (Util.isNullOrEmpty(this.getBindingMethod())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_BINDING_METHOD).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            if (Util.isNullOrEmpty(this.getNameIdFormat())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_NAMEID_FORMAT).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            if (Util.isNullOrEmpty(this.getIdpPublicKey())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_PUBLIC_X509_CERT).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }

            // getSamlCorrelationRule() should always return a string, never a null.
            if (Util.isNullOrEmpty(this.getSamlCorrelationRule())) {
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED,
                        Message.info(MessageKeys.SSO_SAML_CORRELATION_RULE).getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                valid = false;
            }
        }

        return valid;
    }

    public String getEntityId() {
        return this.samlConfigObject.getEntityId();
    }

    public void setEntityId(String entityId) {
        this.samlConfigObject.setEntityId(entityId);
    }

    public String getIdpServiceUrl() {
        return this.samlConfigObject.getIdpServiceUrl();
    }
    
    public String getIssuer() {
        return this.samlConfigObject.getIssuer();
    }

    public void setIdpServiceUrl(String idpServiceUrl) {
        this.samlConfigObject.setIdpServiceUrl(idpServiceUrl);
    }
    
    public void setIssuer(String issuer) {
        this.samlConfigObject.setIssuer(issuer);
    }

    /**
     * Since we only want a human readable value in the UI, we store the constants in a
     * HashMap and use the key as the value to 'set' and 'get'.  This returns a list of
     * the keys in that HashMap.
     * Constants used are from org.opensaml.common.xml.SAMLConstants.
     *
     * @return List of 'short names' for the binding method SAML Constants.
     */
    public List<String> getBindingMethodList() {
        return this.bindingMethodList;
    }

    /**
     * Since we only want a human readable value in the UI, we store the constants in a
     * HashMap and use the key as the value to 'set' and 'get'.
     *
     * @return The 'short name' of the SAML Constant.
     */
    public String getBindingMethod() {
        String bm = this.samlConfigObject.getBindingMethod();
        if (!Util.isNullOrEmpty(bm)) {
            for (Entry<String, String> entry : bindingMethodMap.entrySet()) {
                if (bm.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return bm;
    }

    /**
     * Takes the 'short name' of the binding method constant and stores the
     * full value on the config.
     *
     * @param bindingMethod
     */
    public void setBindingMethod(String bindingMethod) {
        this.samlConfigObject.setBindingMethod(bindingMethodMap.get(bindingMethod));
    }

    /**
     * Since we only want a human readable value in the UI, we store the constants in a
     * HashMap and use the key as the value to 'set' and 'get'.  This returns a list of
     * the keys in that HashMap.
     * Constants used are from org.opensaml.saml2.core.NameIDType.
     *
     * @return List of 'short names' for the NameIdFormat SAML Constants.
     */
    public List<String> getNameIdFormatList() {
        return this.nameIdFormatList;
    }

    /**
     * Returns the mapped 'short name' for the NameIdFormat
     * defined in org.opensaml.saml2.core.NameIDType
     *
     * @return short name of NameIdFormat
     */
    public String getNameIdFormat() {
        String cName = this.samlConfigObject.getNameIdFormat();
        if (!Util.isNullOrEmpty(cName)) {
            for (Entry<String, String> entry : nameIdFormatMap.entrySet()) {
                if (cName.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return cName;
    }

    /**
     * Takes the 'short name' of the NameIdFormat constant and stores the
     * full value on the config.
     *
     * @param nameIdFormat Short name of the constant.
     */
    public void setNameIdFormat(String nameIdFormat) {
        this.samlConfigObject.setNameIdFormat(nameIdFormatMap.get(nameIdFormat));
    }

    public String getIdpPublicKey() {
        return this.samlConfigObject.getIdpPublicKey();
    }

    public void setIdpPublicKey(String idpPublicKey) {
        this.samlConfigObject.setIdpPublicKey(idpPublicKey);
    }

    public boolean isRuleBasedSSOEnabled() {
        return this.ruleBasedSSOEnabled;
    }

    public void setRuleBasedSSOEnabled(boolean isEnabled) {
        this.ruleBasedSSOEnabled = isEnabled;
    }

    public boolean isSamlEnabled() {
        return this.samlEnabled;
    }

    public void setSamlEnabled(boolean isEnabled) {
        this.samlEnabled = isEnabled;
    }

    /**
     * Return the names of all SAML rules, suitable for use
     * in a selectOneMenu.
     */
    public List<SelectItem> getSamlRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.SAMLCorrelation, true);
    }

    /**
     *
     * @return Name of the SAML correlation rule
     */
    public String getSamlCorrelationRule() {
        return this.samlConfigObject.getSamlCorrelationRule() != null ? this.samlConfigObject.getSamlCorrelationRule().getName() : "";
    }

    /**
     *
     * @param samlCorrelationRule Name of the SAML correlation rule
     */
    public void setSamlCorrelationRule(String samlCorrelationRule) {
        try {
            // Since the rule is stored as a Rule and not a string, we need to try and find it by name.
            this.samlConfigObject.setSamlCorrelationRule(getContext().getObjectByName(Rule.class, samlCorrelationRule));
        }
        catch (GeneralException e) {
            log.warn(e);
        }
    }

    public String getAssertionConsumerService() {
        return this.samlConfigObject.getAssertionConsumerService();
    }

    /**
     *
     * @param assertionConsumerService String name of ACS
     */
    public void setAssertionConsumerService(String assertionConsumerService) {
        this.samlConfigObject.setAssertionConsumerService(assertionConsumerService);
    }

    /**
     * For now, just hard code to 'IdentityNow' since that's the only one we're supporting.
     *
     * TODO: Figure out how & where to store a list of supported providers and how to
     *       correlate each config with the proper name.
     *
     * @return The common name of the identity provider.
     */
    private String getProvider() {
        return Configuration.SAML_PROVIDER;
    }
    
    /**
     * 
     * @return authnContextClassRef
     */
    public String getAuthnContextClassRef() {
        return this.samlConfigObject.getAuthnContextClassRef();
    }

    /**
     * 
     * @param authnContextClassRef
     */
    public void setAuthnContextClassRef(String authnContextClassRef) {
        this.samlConfigObject.setAuthnContextClassRef(authnContextClassRef);
    }

    /**
     * 
     * @return authnContextComparison
     */
    public String getAuthnContextComparison() {
        return this.samlConfigObject.getAuthnContextComparison();
    }

    /**
     * 
     * @param authnContextComparison
     */
    public void setAuthnContextComparison(String authnContextComparison) {
        this.samlConfigObject.setAuthnContextComparison(authnContextComparison);
    }

    /**
     * 
     * @return spNameQualifier
     */
    public String getSpNameQualifier() {
        return this.samlConfigObject.getSpNameQualifier();
    }

    public void setSpNameQualifier(String spNameQualifier) {
        this.samlConfigObject.setSpNameQualifier(spNameQualifier);
    }
    
    public String getCorrelationRuleHelp() {
    	  Message msg = new Message("help_sso_saml_correlation_rule", BrandingServiceFactory.getService().getApplicationName());
    	  return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
    }
    
    public String getAcsHelp() {
	  Message msg = new Message("help_sso_saml_acs", BrandingServiceFactory.getService().getApplicationName());
	  return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
    }
    
}
