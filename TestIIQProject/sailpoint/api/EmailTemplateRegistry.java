/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import sailpoint.object.CertificationDefinition;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Certification;
import sailpoint.tools.GeneralException;

import java.util.Map;
import java.util.HashMap;

/**
 * Registry which retrieves and caches email templates.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class EmailTemplateRegistry {


    private SailPointContext context;

    /**
     * Cache of EmailTemplates
     */
    private Map<String, EmailTemplate> templateCache;

    public EmailTemplateRegistry(SailPointContext context) {
        this.context=context;
        templateCache = new HashMap<String, EmailTemplate>();
    }

    /**
     * Returns the EmailTemplate specified by the given key
     * in the System Configuration.
     * @param key System configuration key
     * @return EmailTemplate is matching key is found. Otherwise, null.
     * @throws GeneralException
     */
    public EmailTemplate getTemplate(String key) throws GeneralException {

        if (key == null)
            return null;

        if (templateCache.containsKey(key)){
            return templateCache.get(key);
        }

        EmailTemplate template = ObjectUtil.getSysConfigEmailTemplate(context, key);

        if (template != null){
            // fully load the template so it will survice decaches
            template.load();
        }

        templateCache.put(key, template);

        return template;
    }

    /**
     * Retrieves EmailTemplate specified by the given key and
     * key prefix from the in the System Config. Within the system
     * configuration, the prefix and key will be separated by a '.'.
     * eg 'AppOwner.challengeGenerationEmailTemplate'. If no template
     * is found for the given prefix and key combination, a second lookup
     * will be performed using the key by itself.
     * @param keyPrefix Key prefix
     * @param key EmailTemplate key from system configuration
     * @return EmailTemplate if found.
     * @throws GeneralException
     */
    public EmailTemplate getTemplate(String keyPrefix, String key) throws GeneralException {
        EmailTemplate template = null;
        if (keyPrefix != null){
            template = getTemplate(keyPrefix + "." + key);
        }
        if (template == null)
            template = getTemplate(key);

        return template;
    }

    /**
     * Convenience method used to lookup up EmailTemplates for a Certification.
     * This will attempt to retrieve the templates using the Certification's
     * EmailTemplatePrefix. If no custom templates are found, the general
     * email template will be returned.
     * @param certification Certification to find templates for
     * @param key EmailTemplate key from system configuration.
     * @return Matching EmailTemplate if found.
     * @throws GeneralException
     */
    public EmailTemplate getTemplate(Certification certification, String key) throws GeneralException {
        EmailTemplate template = null;
        if (certification != null){
            CertificationDefinition certDef = certification.getCertificationDefinition(context);
            if (certDef != null) {
                template = certDef.getEmailTemplateFor(context, key);
            }
            
            // if no cert level template defined get default template from sys config
            if (template == null) {
                template = getTemplateFromSysConfig(certification, key);
            }
        }

        return template;
    }
    
    /**
     * Convenience method used to lookup up EmailTemplates for a Certification.
     * This will attempt to retrieve the templates using the Certification's
     * EmailTemplatePrefix. If no custom templates are found, the general
     * email template will be returned.
     * @param certification Certification to find templates for
     * @param key EmailTemplate key from system configuration.
     * @return Matching EmailTemplate if found.
     * @throws GeneralException
     */
    public EmailTemplate getTemplateFromSysConfig(Certification certification, String key) throws GeneralException {
        if (certification != null){
            EmailTemplate template = null;

            // try to retrieve the certification using it's custom email template prefix
            if (certification.getEmailTemplatePrefix() != null){
                template = getTemplate(certification.getEmailTemplatePrefix(), key);
                if (template != null)
                    return template;
            }

            // try getting template by certification type
            template = getTemplate(certification.getType().toString(), key);
            if (template != null)
                return template;
        }

        return getTemplate(key);
    }
}
