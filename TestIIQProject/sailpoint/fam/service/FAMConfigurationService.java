/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.service;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.fam.FAMConnector;
import sailpoint.fam.model.FAMConfigDTO;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.service.ConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Map;

public class FAMConfigurationService extends ConfigService {

    SailPointContext _ctx;
    public static final String SECRET_DUMMY_VALUE = "tsewraf";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String PASSWORD = "password";

    public FAMConfigurationService(SailPointContext ctx) {
        super(ctx);
        this._ctx = ctx;
    }

    public FAMConfigDTO getConfigDTO() throws GeneralException {
        FAMConfigDTO dto;
        Configuration cfg = Configuration.getFAMConfig();

        if (cfg != null) {
            dto = new FAMConfigDTO(cfg, _ctx);
        } else {
            throw new GeneralException("No FAMConfiguration object found");
        }

        return dto;

    }

    /**
     * Patch FAMConfiguration object with values from the data map
     * @param dataMap
     * @throws GeneralException
     */
    public void patchFAMConfiguration(Map<String, Object> dataMap) throws GeneralException {
        FAMConfigDTO dto = new FAMConfigDTO(dataMap);
        validateFAMConfig(dto);
        patchConfigurationAttributes(Configuration.FAM_CONFIG, dto.getConfig());
    }

    /**
     * Return Configuration object built from overlaying FAMConfigDTO on top of the
     * current FAMConfig
     * @param dto - FAMConfigDTO to lay over FAMConfiguration
     * @throws GeneralException when bad things happen
     */
    public Configuration getConfig(FAMConfigDTO dto) throws GeneralException {

        if (dto == null) {
            throw new GeneralException("DTO must be non-null");
        }
        //Data mapping
        Configuration famConfig = Configuration.getFAMConfig();

        Configuration clonedCfg = new Configuration();

        //Clone attributes to ensure they aren't committed accidentally
        Attributes atts;
        if (famConfig != null && famConfig.getAttributes() != null) {
            atts = new Attributes(famConfig.getAttributes());
        } else {
            atts = new Attributes();
        }

        Map<String, Object> map = dto.getConfig();
        restoreSecretValue(map, famConfig, CLIENT_SECRET);
        restoreSecretValue(map, famConfig, PASSWORD);

        if (map != null) {
            encrypt(map);
            atts.putAll(map);
        }

        clonedCfg.setAttributes(atts);

        return clonedCfg;
    }

    /**
     * Restore original secret value if value is still dummy value
     * @param map
     * @param famConfig
     * @param field
     */
    private void restoreSecretValue(Map<String, Object> map, final Configuration famConfig, String field) {
        if (map.get(field) != null && Util.isNotNullOrEmpty(map.get(field).toString())) {
            if(map.get(field).toString().equals(SECRET_DUMMY_VALUE)) {
                map.put(field, famConfig.get(field));
            }
        }
    }

    /**
     * Validate data
     * @param dto FAMConfigDTO
     * @throws GeneralException
     */
    public void validateFAMConfig(FAMConfigDTO dto) throws GeneralException {
        validateFAMAuth(dto);

        // Validate correlation rule name exists
        String ruleName = dto.getScimCorrelationRule();
        if (Util.isNotNullOrEmpty(ruleName)) {
            String ruleId = ObjectUtil.getId(_ctx, Rule.class, ruleName);
            if (ruleId == null) {
                throw new GeneralException("Invalid correlation rule name");
            }
        }

        // Validate app names exist
        List<String> appNames = dto.getScimCorrelationApplications();
        for (String appName : Util.safeIterable(appNames)) {
            String appId = ObjectUtil.getId(_ctx, Application.class, appName);
            if (appId == null) {
                throw new GeneralException("Invalid application name");
            }
        }
    }

    /**
     * Validate the auth config values
     * @param dto
     */
    public void validateFAMAuth(FAMConfigDTO dto) throws GeneralException {
        if (Util.isNotNullOrEmpty(dto.getAuthType())) {
            try {
                if (FAMConnector.AuthType.valueOf(dto.getAuthType()) == FAMConnector.AuthType.OAUTH) {
                    //Ensure clientId/clientSecret are present
                    if (Util.isNullOrEmpty(dto.getClientId()) || Util.isNullOrEmpty(dto.getClientSecret())) {
                        throw new GeneralException("ClientID and ClientSecret required for OAUTH");
                    }
                } else if (FAMConnector.AuthType.valueOf(dto.getAuthType()) == FAMConnector.AuthType.BASIC) {
                    //Ensure userName/password are present
                    if (Util.isNullOrEmpty(dto.getUserName()) || Util.isNullOrEmpty(dto.getPassword())) {
                        throw new GeneralException("Username and Password required for Basic Auth");
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new GeneralException("Invalid AuthType");
            }
        }
    }
}
