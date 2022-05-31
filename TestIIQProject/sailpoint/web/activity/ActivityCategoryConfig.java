/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class ActivityCategoryConfig extends BaseBean {
    private static Log log = LogFactory.getLog(ActivityCategoryConfig.class);
    
    // Inputs
    private String selectedApplicationId;
    private String selectedActivityDataSourceId;
    private String [] selectedTargets;
    
    // Selections
    private List<SelectItem> applications;
    private List<SelectItem> dataSources;
    private List<SelectItem> targets;    

    public ActivityCategoryConfig() {
        initApplications();
    }
    
    public String updateTargetAppSelection() {
        dataSources = null;
        targets = null;
        return "updatedTargetSelection";
    }
    
    public String updateTargetActivitySelection() {
        targets = null;
        return "updatedTargetSelection";
    }
    
    public boolean isRenderActivitySelection() {
        final String selectedAppId = getSelectedApplicationId();
        return selectedAppId != null && selectedAppId.trim().length() > 0;
    }

    public boolean isRenderTargetSelection() {
        final String selectedActivityId = getSelectedActivityDataSourceId();
        return isRenderActivitySelection() && selectedActivityId != null && selectedActivityId.trim().length() > 0;
    }

    public String getSelectedActivityDataSourceId() {
        if (selectedActivityDataSourceId == null || selectedActivityDataSourceId.trim().length() == 0)
            selectedActivityDataSourceId = (String) getRequestParam().get("editForm:targetActivitySelection");
        return selectedActivityDataSourceId;
    }

    public void setSelectedActivityDataSourceId(String selectedActivityDataSourceId) {
        this.selectedActivityDataSourceId = selectedActivityDataSourceId;
    }

    public String getSelectedApplicationId() {
        if (selectedApplicationId == null || selectedApplicationId.trim().length() == 0)
            selectedApplicationId = (String) getRequestParam().get("editForm:targetAppSelection");
        return selectedApplicationId;
    }

    public void setSelectedApplicationId(String selectedApplicationId) {
        this.selectedApplicationId = selectedApplicationId;
    }

    public String [] getSelectedTargets() {
        return selectedTargets;
    }

    public void setSelectedTargets(String [] selectedTargets) {
        this.selectedTargets = selectedTargets;
    }

    public List<SelectItem> getApplications() {
        return applications;
    }

    public List<SelectItem> getDataSources() {
        if (dataSources == null) {
            dataSources = new ArrayList<SelectItem>();
            
            final String selectedAppId = getSelectedApplicationId();
            
            try {
                if (selectedAppId != null) {
                    Application selectedApp = getContext().getObjectById(Application.class, selectedAppId);
                    if (selectedApp != null) {
                        List<ActivityDataSource> dataSourcesForApp = selectedApp.getActivityDataSources();
                        
                        if (dataSourcesForApp != null) {
                            for (ActivityDataSource dataSource : dataSourcesForApp) {
                                dataSources.add(new SelectItem(dataSource.getId(), dataSource.getName()));
                            }
                        }
                    }
                }
            } catch (GeneralException e) {
                Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_DS_NOT_AVAILABLE);
                log.error(msg.getLocalizedMessage(), e);
                addMessage(msg, null);
            }
        }
        
        return dataSources;
    }

    public List<SelectItem> getTargets() {
        if (targets == null) {
            targets = new ArrayList<SelectItem>();
            
            try {
                final String selectedActivityId = getSelectedActivityDataSourceId();
                
                if (selectedActivityId != null) {
                    ActivityDataSource selectedDataSource = getContext().getObjectById(ActivityDataSource.class, selectedActivityId);
                    if (selectedDataSource != null) {
                        List<String> targetsForDS = selectedDataSource.getTargets();
                        
                        if (targetsForDS != null) {
                            for (String target : targetsForDS) {
                                if (target != null && target.trim().length() > 0)
                                    targets.add(new SelectItem(target, target));
                            }
                        }
                    } else {
                        targets.add(new SelectItem("", ""));
                    }
                } else {
                    targets.add(new SelectItem("", ""));
                }
            } catch (GeneralException e) {
                Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ACTIVITY_TARGETS_UNAVAILABLE);
                log.error(msg.getLocalizedMessage(), e);
                addMessage(msg, null);
            }
        }
        
        return targets;
    }
    
    private void initApplications() {
        applications = new ArrayList<SelectItem>();
        
        try {
            QueryOptions dataSourceAppsOrderedByName = new QueryOptions();
            dataSourceAppsOrderedByName.add(Filter.not(Filter.isempty("activityDataSources")));
            dataSourceAppsOrderedByName.setOrderBy("name");
            dataSourceAppsOrderedByName.setScopeResults(true);
            dataSourceAppsOrderedByName.addOwnerScope(super.getLoggedInUser());
            Iterator<Object[]> appIter = getContext().search(Application.class, dataSourceAppsOrderedByName, Arrays.asList(new String [] {"id", "name"}));
            
            final int ID = 0;
            final int NAME = 1;
            while (appIter.hasNext()) {
                Object [] appInfo = appIter.next();
                applications.add(new SelectItem((String)appInfo[ID], (String)appInfo[NAME]));
            }
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ACTIVITY_APPS_UNAVAILABLE);
            log.error(msg.getLocalizedMessage(), e);
            addMessage(msg, null);
        }
    }
}
