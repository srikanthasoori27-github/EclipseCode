/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.ApplicationObjectBean;
import sailpoint.web.BaseBean;

public class ActivityDataSourceDataSourceBean extends BaseBean {
    private static final Log log = LogFactory.getLog(ActivityDataSourceDataSourceBean.class);
    private static final String APP_ID_PARAM = "editForm:id";
    
    public ActivityDataSourceDataSourceBean(){};
    
    @SuppressWarnings("unchecked")
    public String getActivityDataSourceJSON() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            String appId = (String) getRequestParam().get(APP_ID_PARAM);
            ApplicationObjectBean aob = (ApplicationObjectBean) FacesContext.getCurrentInstance().getApplication().createValueBinding("#{applicationObject}").getValue(FacesContext.getCurrentInstance());
            Map<String, ActivityDataSourceDTO>  editedActivityDataSources = (Map<String, ActivityDataSourceDTO>) aob.getEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS_MAP);
            Application app = getContext().getObjectById(Application.class, appId);
            final int count = (app == null) ? 0 : app.getActivityDataSourcesCount();
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(count);
            jsonWriter.key("activityDataSources");

            List<JSONObject> activityDataSourceList = new ArrayList<JSONObject>();
            List<ActivityDataSource> persistedDataSources = new ArrayList<ActivityDataSource>();
            if ( app != null ) {
                persistedDataSources = app.getActivityDataSources();
            }
            List<String> deletedDataSourceIds = (List<String>)aob.getEditState(ApplicationObjectBean.DELETED_ACTIVITY_DS_LIST);
            
            // This first map is only temporary.  It will be used to resolve name changes below
            Map<String, String> adsIdToAdsName = new HashMap<String, String>();
            // This second map will be used when generating the JSON returned below
            Map<String, ActivityDataSourceDTO> adsNameToAdsDTO = new HashMap<String, ActivityDataSourceDTO>();
            
            if (persistedDataSources != null && !persistedDataSources.isEmpty()) {
                for (ActivityDataSource dataSource : persistedDataSources) {
                    // Filter out data sources that have been deleted
                    if (deletedDataSourceIds == null || !deletedDataSourceIds.contains(dataSource.getId())) {
                        adsIdToAdsName.put(dataSource.getId(), dataSource.getName());
                        adsNameToAdsDTO.put(dataSource.getName(), new ActivityDataSourceDTO(dataSource));
                    }
                }                
            }
            
            if (editedActivityDataSources != null && !editedActivityDataSources.isEmpty()) {
                Collection<ActivityDataSourceDTO> editedDataSources = editedActivityDataSources.values();
                for (ActivityDataSourceDTO dataSource : editedDataSources) {
                    if (adsIdToAdsName.containsKey(dataSource.getId())) {
                        // Perform this step to account for potential name changes during edits
                        String nameOfRemovedDTO = adsIdToAdsName.get(dataSource.getId());
                        adsNameToAdsDTO.remove(nameOfRemovedDTO);
                    }
                    
                    adsNameToAdsDTO.put(dataSource.getName(), dataSource);
                }
            }
            
            List<String> adsNames = new ArrayList<String>();

            if (!adsNameToAdsDTO.isEmpty()) {
                adsNames.addAll(adsNameToAdsDTO.keySet());
                Collections.sort(adsNames, Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR);
            }
            
            if (adsNames != null && !adsNames.isEmpty()) {
                for (String dataSourceName : adsNames) {
                    ActivityDataSourceDTO dataSource = adsNameToAdsDTO.get(dataSourceName);
                    Map<String, Object> dataSourceInfo = new HashMap<String, Object>();
                    dataSourceInfo.put("id", dataSource.getId());
                    dataSourceInfo.put("name", dataSource.getName());
                    dataSourceInfo.put("type", dataSource.getType());
                    Date modified = dataSource.getModified();
                    long modifiedTime;
                    if (modified == null) {
                        modifiedTime = 0; 
                    } else {
                        modifiedTime = modified.getTime() / 1000;
                    }
                    dataSourceInfo.put("modified", modifiedTime);
                    activityDataSourceList.add(new JSONObject(dataSourceInfo));
                }
            }
            jsonWriter.value(new JSONArray(activityDataSourceList));
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get JSON for activity data sources");
        } catch(GeneralException e) {
            log.error("Could not get JSON for activity data sources");
        }
        return jsonString.toString();
    }

}
