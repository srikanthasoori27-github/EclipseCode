/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ApplicationConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;

public class ActivityMonitoringScoreConfigBean extends BaseObjectBean<ScoreConfig> {
    private static Log log = LogFactory.getLog(ActivityMonitoringScoreConfigBean.class);
    
    private Map<Application, ApplicationConfig> applicationConfigs;
    
    public ActivityMonitoringScoreConfigBean() {
        super();
        
        try {
            setScope(ScoreConfig.class);
            
            if (getObject() == null) {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                setObject(config);
                setObjectId(config.getId());
            }

            initializeActivityMonitoring();
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ACTIVITY_DUPLICATE_CAT_NAME);
            log.error(msg.getLocalizedMessage(), e);
            addMessage(msg, null);
        }
    }
    
    public String saveChangesAction() {                
        try {
            List<ApplicationConfig> configsToPersist = new ArrayList<ApplicationConfig>();
            for (ApplicationConfig config : applicationConfigs.values()) {
                if (!"0".equals(config.getActivityMonitoringFactor())) {
                    configsToPersist.add(config);
                } 
            }
            
            getObject().setApplicationConfigs(configsToPersist);
            
            saveAction();
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_SYSTEM_OFFLINE);
            log.error(msg.getLocalizedMessage(), e);
            addMessage(msg, null);
        }
        
        return "save";
    }

    public String cancelChangesAction() {
        return "cancel";
    }
    
    private void initializeActivityMonitoring() throws GeneralException {
        applicationConfigs = new HashMap<Application, ApplicationConfig>();
        List<ApplicationConfig> configuredApps = getObject().getApplicationConfigs();
        
        if (configuredApps != null) {
            // Start our list with previously configured applications
            for (ApplicationConfig config : configuredApps) {
                applicationConfigs.put(config.getApplication(), config);
            }
        }
        
        List<Application> allApps = getContext().getObjects(Application.class);
        
        if (allApps != null) {
            // Add the rest
            for (Application app : allApps) {
                if (!applicationConfigs.containsKey(app)) {
                    ApplicationConfig defaultConfig = new ApplicationConfig(app);
                    applicationConfigs.put(app, defaultConfig);
                }
            }
        }
    }
    
    public List<ApplicationConfig> getApplicationConfigs() {
        Collection<ApplicationConfig> configValues = applicationConfigs.values();
        
        // Sort the values and return them
        ApplicationConfig [] configArray = configValues.toArray(new ApplicationConfig[configValues.size()]);
        Arrays.sort(configArray, new Comparator<ApplicationConfig>() {
            public int compare(ApplicationConfig o1, ApplicationConfig o2) {
                return o1.getApplication().getName().compareTo(o2.getApplication().getName());
            }            
        });
        
        return Arrays.asList(configArray);
    }
}
