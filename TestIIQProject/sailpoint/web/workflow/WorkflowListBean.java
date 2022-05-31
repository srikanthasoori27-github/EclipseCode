/**
 * 
 */
package sailpoint.web.workflow;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.Version;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseListBean;

/**
 * @author peter.holcomb
 *
 */
public class WorkflowListBean extends BaseListBean<Workflow> {

    /**
     * 
     */
    public WorkflowListBean() {
        super();
        super.setScope(Workflow.class);
    }

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException{
        QueryOptions qo = super.getQueryOptions();

        /** Load only templates if the datasource is asking for templates. **/
        if(getRequestParameter("template")!=null) {
            qo.add(Filter.eq("template", true));
        } else {
            qo.add(Filter.eq("template", false));
        }
        return qo;
    }

    public String getObjectsJson() throws GeneralException, JSONException {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString); 

        int count = getCount();
        
        List<JSONObject> wfs = new ArrayList<JSONObject>();
        List<Workflow> workflows = getObjects();
        boolean lcmEnabled = Version.isLCMEnabled();
        for(Workflow workflow : workflows) {
            if (!shouldIncludeWorkflow(lcmEnabled, workflow)) {
                continue;
            }
            Map<String, Object> wfMap = new HashMap<String, Object>();
            wfMap.put("id", workflow.getId());
            wfMap.put("name", workflow.getName());
            wfMap.put("monitored", workflow.isAnyMonitoring());
            wfMap.put("type", workflow.getType());
            wfs.add(new JSONObject(wfMap));
        }

        jsonWriter.object();
        jsonWriter.key("objects");
        jsonWriter.value(wfs);
        jsonWriter.key("totalCount");
        jsonWriter.value(count);
        jsonWriter.endObject();

        return jsonString.toString();
    }

    /**
     * Determines whether a given workflow should be included in the list
     *
     * @param lcmEnabled whethere lcm is enabled for the installation.
     * @param workflow the workflow
     *
     */
    private boolean shouldIncludeWorkflow(boolean lcmEnabled, Workflow workflow) {
        boolean shouldInclude = true;
        if (!lcmEnabled && isLCMWorkflow(workflow)) {
            shouldInclude = false;
        }
        return shouldInclude;
    }

    /**
     * Determines whether a given workflow is LCM type.
     * @param workflow the workflow
     */
    private boolean isLCMWorkflow(Workflow workflow) {
        return (workflow.getTaskType() != null && workflow.getTaskType() == TaskItemDefinition.Type.LCM);
    }

}
