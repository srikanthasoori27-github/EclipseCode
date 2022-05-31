/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.service.BaseDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FAMConfigDTO extends BaseDTO {

    private static Log _log = LogFactory.getLog(FAMConfigDTO.class);

    String _hostname;
    String _authType;
    String _clientId;
    String _clientSecret;
    String _userName;
    String _password;
    String _scimCorrelationRule;
    List<String> _scimCorrelationApplications;

    public FAMConfigDTO() {
        super();
    }

    public FAMConfigDTO(Map<String, Object> dataMap) {
        setHostname((String)dataMap.get(Configuration.FAM_CONFIG_URL));
        setAuthType((String)dataMap.get(Configuration.FAM_CONFIG_AUTH_TYPE));
        setClientId((String)dataMap.get(Configuration.FAM_CONFIG_CLIENT_ID));
        setClientSecret((String)dataMap.get(Configuration.FAM_CONFIG_CLIENT_SECRET));
        setUserName((String)dataMap.get(Configuration.FAM_CONFIG_USER_NAME));
        setPassword((String)dataMap.get(Configuration.FAM_CONFIG_PASSWORD));
        Object scimCorrelationRule = dataMap.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_RULE);
        if (scimCorrelationRule != null) {
            if (scimCorrelationRule instanceof Map) {
                Map ruleMap = (Map)scimCorrelationRule;
                setScimCorrelationRule((String)ruleMap.get("name"));
            }
            else if (scimCorrelationRule instanceof String) {
                setScimCorrelationRule((String)scimCorrelationRule);
            }
        }
        List<Object> appMaps = (List)dataMap.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS);
        if (!Util.isEmpty(appMaps)) {
            List<String> appNames = new ArrayList<>();
            for (Object app : appMaps) {
                // existing app names
                if (app instanceof String) {
                    appNames.add((String)app);
                }
                // new app names
                else if (app instanceof Map) {
                    Map appMap = (Map)app;
                    appNames.add((String)appMap.get("name"));
                }
            }
            setScimCorrelationApplications(appNames);
        }
    }

    public FAMConfigDTO(Configuration cfg, SailPointContext ctx) {
        if (cfg != null) {
            if (cfg.getAttributes() != null) {
                setHostname(Util.otos(cfg.get(Configuration.FAM_CONFIG_URL)));
                setAuthType(Util.otos(cfg.get(Configuration.FAM_CONFIG_AUTH_TYPE)));
                setClientId(Util.otos(cfg.get(Configuration.FAM_CONFIG_CLIENT_ID)));
                setClientSecret(Util.otos(cfg.get(Configuration.FAM_CONFIG_CLIENT_SECRET)));
                setUserName(Util.otos(cfg.get(Configuration.FAM_CONFIG_USER_NAME)));
                setPassword(Util.otos(cfg.get(Configuration.FAM_CONFIG_PASSWORD)));

                //SCIMCorrelationRule
                if (cfg.containsKey(Configuration.FAM_CONFIG_SCIM_CORRELATION_RULE)) {
                    String ruleName = Util.otos(cfg.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_RULE));
                    if (Util.isNotNullOrEmpty(ruleName)) {
                        try {
                            String ruleId = ObjectUtil.getId(ctx, Rule.class, ruleName);
                            if (ruleId != null) {
                                setScimCorrelationRule(ruleName);
                            }
                        } catch (GeneralException ge) {
                            _log.error("Error geting rule Id for rule[" + ruleName + "]");
                        }

                    }
                }

                if (cfg.containsKey(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS)) {
                    List<String> appNames = Util.otol(cfg.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS));
                    for (String s : Util.safeIterable(appNames)) {
                        try {
                            String appId = ObjectUtil.getId(ctx, Application.class, s);
                            if (appId != null) {
                                addCorrelationApplication(s);
                            }
                        } catch (GeneralException ge) {
                            _log.error("Error getting applicationId[" + s + "]" + ge);
                        }
                    }
                }
            }
        }
    }

    public String getHostname() {
        return _hostname;
    }

    public void setHostname(String hostname) {
        _hostname = hostname;
    }

    public String getAuthType() {
        return _authType;
    }

    public void setAuthType(String authType) {
        _authType = authType;
    }

    public String getClientId() {
        return _clientId;
    }

    public void setClientId(String clientId) {
        _clientId = clientId;
    }

    public String getClientSecret() {
        return _clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        _clientSecret = clientSecret;
    }

    public String getUserName() {
        return _userName;
    }

    public void setUserName(String userName) {
        _userName = userName;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public String getScimCorrelationRule() {
        return _scimCorrelationRule;
    }

    public void setScimCorrelationRule(String scimCorrelationRule) {
        _scimCorrelationRule = scimCorrelationRule;
    }


    public List<String> getScimCorrelationApplications() {
        return _scimCorrelationApplications;
    }

    public void setScimCorrelationApplications(List<String> scimCorrelationApplications) {
        _scimCorrelationApplications = scimCorrelationApplications;
    }

    public void addCorrelationApplication(String appName) {
        if (_scimCorrelationApplications == null) {
            _scimCorrelationApplications = new ArrayList<>();
        }
        _scimCorrelationApplications.add(appName);
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> cfg = new HashMap<String, Object>();
        cfg.put(Configuration.FAM_CONFIG_URL, this.getHostname());
        cfg.put(Configuration.FAM_CONFIG_AUTH_TYPE, this.getAuthType());
        cfg.put(Configuration.FAM_CONFIG_CLIENT_ID, this.getClientId());
        cfg.put(Configuration.FAM_CONFIG_CLIENT_SECRET, this.getClientSecret());
        cfg.put(Configuration.FAM_CONFIG_USER_NAME, this.getUserName());
        cfg.put(Configuration.FAM_CONFIG_PASSWORD, this.getPassword());
        cfg.put(Configuration.FAM_CONFIG_SCIM_CORRELATION_RULE, this.getScimCorrelationRule());
        cfg.put(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS, this.getScimCorrelationApplications());
        return cfg;
    }
}
