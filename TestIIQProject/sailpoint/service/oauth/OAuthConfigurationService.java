/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.OAuthConfiguration;
import sailpoint.server.Auditor;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This service is responsible for managing OAuthConfiguration object.
 */
public class OAuthConfigurationService {
    
    private static final Log log = LogFactory.getLog(OAuthConfigurationService.class);
    
    SailPointContext context;
    
    public OAuthConfigurationService(SailPointContext context) {
        this.context = context;
    }
    
    /**
     * Retrieves the OAuth Configuration DTO object.
     * 
     * @return OAuthConfigurationDTO
     */
    public OAuthConfigurationDTO get() {
        OAuthConfiguration config = getOAuthConfig();
        
        return new OAuthConfigurationDTO(config);
    }
    
    /**
     * Returns the configuration for an OAuthClient
     * @param context context to use
     * @return OAuthConfiguration
     */
    protected OAuthConfiguration getOAuthConfig() {
        if (context == null) {
            return null;
        }
        
        OAuthConfiguration oauth = null;
        try {
            Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
            if (oauth == null) {
                oauth = new OAuthConfiguration();
                oauth.setAccessTokenExpiresInSeconds(OAuthConfiguration.DEFAULT_ACCESS_TOKEN_EXPIRES_IN_SECONDS);
                sysConfig.put(Configuration.OAUTH_CONFIGURATION, oauth);
                context.saveObject(sysConfig);
                context.commitTransaction();
            }
        } catch (GeneralException e) {
            // something pretty bad must have happened
            log.error("unable to get system configuration", e);
        }
        return oauth;
    }


    /**
     * Updates the OAuthConfiguration based on data of passed dto.
     * 
     * @param oAuthConfigurationDTO OAuthConfigurationDTO
     * @return updated OAuthConfigurationDTO
     * @throws GeneralException 
     */
    public OAuthConfigurationDTO update(OAuthConfigurationDTO oauthConfigurationDto) throws GeneralException {
        Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        OAuthConfiguration oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
        oauth.setAccessTokenExpiresInSeconds(oauthConfigurationDto.getAccessTokenExpiresInSeconds());
        context.saveObject(sysConfig);
        context.commitTransaction();
        
        audit(MessageKeys.OAUTH_CONFIGURATION_CHANGED, oauth);
        
        return oauthConfigurationDto;
    }
    
    /**
     * Audits the API configuration change related to general settings.
     * 
     * @param msgKey
     * @param config OAuthConfiguration object
     * @throws GeneralException
     */
    public void audit(String msgKey, OAuthConfiguration config) throws GeneralException {
        if (Auditor.isEnabled(AuditEvent.APIConfigurationChange)) {
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.APIConfigurationChange);
            event.setApplication(BrandingServiceFactory.getService().getApplicationName());
            event.setTarget(Message.localize(msgKey).getLocalizedMessage());
            Attributes<String,Object> attr = new Attributes<String, Object>();
            attr.put("AccessTokenExpiresInSeconds", config.getAccessTokenExpiresInSeconds());
            event.setAttributes(attr);
            
            Auditor.log(event);
            context.commitTransaction();
        }
    }

}
