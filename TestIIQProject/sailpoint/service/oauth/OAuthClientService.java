/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.OAuthClient;
import sailpoint.object.OAuthConfiguration;
import sailpoint.server.Auditor;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This service is responsible for managing OAuthClient objects and
 * generating random client id's and secrets.
 */
public class OAuthClientService {

    public static final String MSG_CLIENT_NOT_FOUND = "Client not found.";
    public static final String MSG_CLIENT_NAME_NOT_UNIQUE = "Client name is not unique.";
    
    private Random random;
    
    private static final Log log = LogFactory.getLog(OAuthClientService.class);
    
    static final int CLIENT_ID_LENGTH = 32;
    static final int CLIENT_SECRET_LENGTH = 16;
    
    SailPointContext context;
    
    public OAuthClientService(SailPointContext context) {
        this.context = context;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        }
        catch (NoSuchAlgorithmException nse) {
            // will use default machine implementation
            random = new SecureRandom();
        }
    }

    /**
     * Using the context obtains the first client matching the id parameter.
     * @param id client id to find in the OAuthConfiguration
     * @return OAuthClient
     */
    public OAuthClient getClient(String id) {
        OAuthClient result = null;
        OAuthConfiguration config = getOAuthConfig();
        
        if (config != null) {
            result = config.getClient(id);
        }
        
        return result;
    }
    
    /**
     * Using the context obtains the first client matching the name parameter.
     * @param name client name to find in the OAuthConfiguration
     * @return OAuthClient
     */
    public OAuthClient getClientByName(String name) {
        OAuthClient result = null;
        OAuthConfiguration config = getOAuthConfig();
        
        if (config != null) {
            result = config.getClientByName(name);
        }
        
        return result;
    }
    
    /**
     * Generates a 32 character string
     * @return clientId string
     */
    public String generateClientId() {
        return RandomStringUtils.random(CLIENT_ID_LENGTH, 0, 0, true, true, null, random);
    }
    
    /**
     * Generates a 16 character secret
     * @return secret string
     */
    public String generateClientSecret() {
        return RandomStringUtils.random(CLIENT_SECRET_LENGTH, 0, 0, true, true, null, random);
    }
    
    /**
     * Returns the configuration for an OAuthClient
     * @param context context to use
     * @return OAuthConfiguration or null
     */
    protected OAuthConfiguration getOAuthConfig() {
        if (context == null) {
            return null;
        }
        
        OAuthConfiguration result = null;
        try {
            result = (OAuthConfiguration)context.getConfiguration().get(Configuration.OAUTH_CONFIGURATION);
        } catch (GeneralException e) {
            // something pretty bad must have happened
            log.error("unable to get system configuration", e);
        }
        return result;
    }

    /**
     * Retrieves the list of OAuth client DTOs.
     * 
     * @return list of OAuthClientDTO
     */
    public List<OAuthClientDTO> getClientDTOs() {
        List<OAuthClientDTO> dtos = new ArrayList<OAuthClientDTO>();
        OAuthConfiguration config = getOAuthConfig();
        if (config != null) {
            List<OAuthClient> clients = config.getOAuthClients();
            for (OAuthClient client : Util.safeIterable(clients)) {
                dtos.add(convertToDTO(client));
            }
        }
        return dtos;
    }

    /**
     * Converts OAuthClient object to OAuthClientDTO.
     * 
     * @param client OAuthClient object
     * @return OAuthClientDTO
     */
    private OAuthClientDTO convertToDTO(OAuthClient client) {
        OAuthClientDTO dto = new OAuthClientDTO();
        dto.setClientId(client.getClientId());
        dto.setCreateDate(client.getCreateDate());
        dto.setName(client.getName());
        try {
            String secret = context.decrypt(client.getSecret());
            dto.setSecret(secret);
        } catch (GeneralException e) {
            log.warn("Error when setting OAuthClientDTO secret.", e);
        }

        if (Util.isNotNullOrEmpty(client.getProxyUser())) {
            Identity proxy = null;
            try {
                proxy = context.getObjectByName(Identity.class, client.getProxyUser());
            } catch (GeneralException e) {
                log.warn("Error when setting OAuthClientDTO proxy user.", e);
            }
            if (proxy != null) {
                dto.setProxyUser(new IdentitySummaryDTO(proxy));
            }
        }
        return dto;
    }

    private void validate(String clientId, OAuthClientDTO clientDto) throws GeneralException {
        if (clientDto == null || Util.isNullOrEmpty(clientDto.getName())) {
            throw new GeneralException("Client name is required");
        }
        if (clientDto.getProxyUser() == null || 
             (Util.isNullOrEmpty(clientDto.getProxyUser().getName()) && Util.isNullOrEmpty(clientDto.getProxyUser().getId()))) {
            throw new GeneralException("Proxy user are required");
        }
    }

    /**
     * Checks the uniqueness of the client name.
     * 
     * @param clientId client id
     * @param name client name
     * @return is the name unique
     */
    public boolean isNameUnique(String clientId, String name) {
        OAuthConfiguration config = getOAuthConfig();
        if (config != null) {
            List<OAuthClient> clients = config.getOAuthClients();
            for (OAuthClient client : Util.safeIterable(clients)) {
                if (Util.nullSafeEq(name, client.getName()) && !Util.nullSafeEq(clientId, client.getClientId())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Creates new OAuthClient based on data of passed dto.
     * 
     * @param clientDto OAuthClientDTO
     * @return newly created OAuthClientDTO
     * @throws GeneralException 
     */
    public OAuthClientDTO createClient(OAuthClientDTO clientDto) throws GeneralException {
        validate(null, clientDto);
        
        OAuthClient client = new OAuthClient();
        client.setClientId(generateClientId());
        client.setCreateDate(new Date());
        OAuthTransformer trans = new OAuthTransformer(context);
        String key = trans.generateKey();
        client.setKey(key);
        client.setName(clientDto.getName());
        if (clientDto.getProxyUser() != null) {
            client.setProxyUser(getProxyUserName(clientDto.getProxyUser()));
        }
        client.setSecret(generateClientSecret());
        
        Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        OAuthConfiguration oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
        if (oauth == null) {
            oauth = new OAuthConfiguration();
            
            oauth.setAccessTokenExpiresInSeconds(OAuthConfiguration.DEFAULT_ACCESS_TOKEN_EXPIRES_IN_SECONDS);
            
            sysConfig.put(Configuration.OAUTH_CONFIGURATION, oauth);
        }
        oauth.addClient(client);
        
        context.saveObject(sysConfig);
        context.commitTransaction();
        
        audit(MessageKeys.OAUTH_CLIENT_CREATED, client);

        return convertToDTO(client);
    }

    /**
     * Updates the OAuthClient based on data of passed dto.
     * 
     * @param clientDto OAuthClientDTO
     * @return updated OAuthClientDTO
     * @throws GeneralException 
     */
    public OAuthClientDTO updateClient(String clientId, OAuthClientDTO clientDto) throws GeneralException {
        validate(clientId, clientDto);
        
        Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        OAuthConfiguration oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
        OAuthClient client = oauth.getClient(clientId);
        
        if (client != null ) {
            client.setName(clientDto.getName());
            client.setProxyUser(getProxyUserName(clientDto.getProxyUser()));
            context.saveObject(sysConfig);
            context.commitTransaction();
            audit(MessageKeys.OAUTH_CLIENT_UPDATED, client);
        }
        return convertToDTO(client);
    }

    /** 
     * For proxy user, either user name or id is required.
     * If both are provided, user name takes precedence.
     * 
     */
    protected String getProxyUserName(IdentitySummaryDTO dto) throws GeneralException {
        if (dto == null) {
            return null;
        }
        
        if (dto.getName() != null) {
            return dto.getName();
        } else if (dto.getId() != null) {
            Identity identity = context.getObjectById(Identity.class, dto.getId());
            if (identity != null) {
                return identity.getName();
            }
        }
        return null;
    }
    
    /**
     * Audits the detailed API configuration change related to OAuth Client.
     * 
     * @param msgKey
     * @param client OAuth Client
     * @throws GeneralException
     */
    public void audit(String msgKey, OAuthClient client) throws GeneralException {
        if (Auditor.isEnabled(AuditEvent.APIConfigurationChange)) {
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.APIConfigurationChange);
            event.setApplication(BrandingServiceFactory.getService().getApplicationName());
            event.setTarget(Message.localize(msgKey).getLocalizedMessage());
            event.setAccountName(client.getName());
            Attributes<String,Object> attr = new Attributes<String, Object>();
            attr.put(OAuthClientDTO.PARAM_CLIENT_NAME, client.getName());
            attr.put(OAuthClientDTO.PARAM_PROXY_USER, client.getProxyUser());
            event.setAttributes(attr);
            
            Auditor.log(event);
            context.commitTransaction();
        }
    }

    /**
     * Removes the oauth client from OAuthConfiguration.
     * 
     * @param clientId The Id of the OAuthClient to delete
     * @throws GeneralException 
     */
    public void deleteClient(String clientId) throws GeneralException {
        Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        OAuthConfiguration oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
        OAuthClient client = oauth.deleteClient(clientId);
        if (client != null) {
            context.saveObject(sysConfig);
            context.commitTransaction();
            audit(MessageKeys.OAUTH_CLIENT_DELETED, client);
        }
    }
    
    /**
     * Generates new secret and key for the specified oauth client.
     * 
     * @param clientId The Id of the OAuthClient to generate new secret and key.
     * @throws GeneralException 
     */
    public void regenerateKeys(String clientId) throws GeneralException {
        Configuration sysConfig = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        OAuthConfiguration oauth = (OAuthConfiguration)sysConfig.get(Configuration.OAUTH_CONFIGURATION);
        OAuthClient client = oauth.getClient(clientId);
        if (client != null) {
            OAuthTransformer trans = new OAuthTransformer(context);
            String key = trans.generateKey();
            client.setKey(key);
            client.setSecret(generateClientSecret());

            context.saveObject(sysConfig);
            context.commitTransaction();
            audit(MessageKeys.OAUTH_CLIENT_KEY_REGENERATED, client);
        }
    }
}
