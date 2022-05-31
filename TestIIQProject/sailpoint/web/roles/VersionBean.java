package sailpoint.web.roles;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.RoleLifecycler;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.BaseBean;
import sailpoint.web.modeler.RoleUtil;

public class VersionBean extends BaseBean {
    private static final Log log = LogFactory.getLog(VersionBean.class);
    
    public String getVersionsJson() {
        String roleId = (String) getRequestParam().get("roleId");
        int limit = getResultLimit();

        String startString = (String)getRequestParam().get("start");
        int start;
        if (startString != null && startString.trim().length() > 0)
            start = Integer.parseInt(startString);
        else
            start = 0;
        
        QueryOptions archiveFilter = new QueryOptions(Filter.eq("sourceId", roleId));

        String sort = (String)getRequestParam().get("sort");
        archiveFilter.setOrderBy(null != sort ? sort : "version");

        String direction = (String)getRequestParam().get("dir");
        if (null != direction && direction.equalsIgnoreCase("DESC")) {
            archiveFilter.setOrderAscending(false);
        } else {
            archiveFilter.setOrderAscending(true);
        };

        int versionCount = 0;
        List<BundleArchive> roleArchives = null;
        if (roleId != null && roleId.trim().length() > 0) {
            try {
                versionCount = getContext().countObjects(BundleArchive.class, archiveFilter);
                archiveFilter.setFirstRow(start);
                archiveFilter.setResultLimit(limit);
                roleArchives = getContext().getObjects(BundleArchive.class, archiveFilter);
            } catch (GeneralException e) {
                log.error("Unable to fetch role archives", e);
            }
        }
        
        if (roleArchives == null) {
            roleArchives = new ArrayList<BundleArchive>();
        }
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            jsonWriter.key("versions");

            List<JSONObject> versionList = new ArrayList<JSONObject>(); 
            for (BundleArchive roleArchive : roleArchives) {
                Map<String, Object> versionInfo = new HashMap<String, Object>();
                versionInfo.put("id", roleArchive.getId());
                versionInfo.put("version", roleArchive.getVersion());
                versionInfo.put("creator", roleArchive.getCreator());
                String createdDate = Internationalizer.getLocalizedDate(roleArchive.getCreated(), getLocale(), getUserTimeZone());
                versionInfo.put("created", createdDate);
                versionList.add(new JSONObject(versionInfo));
            }
            jsonWriter.value(new JSONArray(versionList));
            jsonWriter.key("numVersions");
            jsonWriter.value(versionCount);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not convert the roles to JSON.", e);            
        }
        
        return jsonString.toString();
    }
    
    public String getVersionJson() {
        String archiveId = (String) getRequestParam().get("archiveId");
        
        String jsonString;
        
        try {
            BundleArchive roleArchive = getContext().getObjectById(BundleArchive.class, archiveId);
            RoleLifecycler lifecycler = new RoleLifecycler(getContext());
            Bundle archivedRole = lifecycler.rehydrate(roleArchive);
            jsonString = RoleUtil.getReadOnlyRoleJson(archivedRole, getContext(), getLoggedInUser(), getUserTimeZone(), getLocale());
        } catch (GeneralException e) {
            jsonString = "{}";
            log.error("Could not get the JSON for the archived role with id " + archiveId, e);
        } catch (JSONException e) {
            jsonString = "{}";
            log.error("Could not get the JSON for the archived role with id " + archiveId, e);
        }

        return jsonString;
    }

}
