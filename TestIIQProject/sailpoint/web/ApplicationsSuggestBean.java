/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class ApplicationsSuggestBean extends BaseObjectSuggestBean {
    
    private static final Log log = LogFactory.getLog(ApplicationsSuggestBean.class);
    
    public String getJsonForApplications() throws GeneralException {
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();

            jsonWriter.key("applications");
            jsonWriter.array();
            // Get the raw count first and save it for later
            QueryOptions qo = new QueryOptions();
            int numApplicationResults = getContext().countObjects(Application.class, qo);
            // Apply the limits and run a projection query
            Map request = super.getRequestParam();
            String query = (String) request.get("query");
            log.debug("Request params: " + request.toString());
            Filter filter = 
                Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START));
            qo.add(filter);
            setJsonQueryOptions(qo);

            boolean showComposite = request.get("showComposite") == null ||
                    Boolean.parseBoolean((String)request.get("showComposite"));

            List<Application> applications = getContext().getObjects(Application.class, qo);

            if (applications != null){
                for(Application app : applications){                   
                    if (showComposite || !app.isLogical()){
                        jsonWriter.object();

                        jsonWriter.key("id");
                        jsonWriter.value(app.getId());

                        jsonWriter.key("name");
                        jsonWriter.value(app.getName());

                        jsonWriter.endObject();
                    }
                }
            }

            jsonWriter.endArray();
            
            jsonWriter.key("numApplications");
            jsonWriter.value(numApplicationResults);
            
            jsonWriter.endObject();
        } catch (JSONException e) {
            throw new GeneralException("Could not build JSON for applications right now", e);
        }
        
        log.debug("Returning json: " + jsonString.toString());
        
        return jsonString.toString();
    }


    private void setJsonQueryOptions(QueryOptions qo) {
        qo.addOrdering("name", true);

        int start = -1;
        String startString = (String) getRequestParam().get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        
        if (start > 0) {
            qo.setFirstRow(start);
        }

        
        int limit = getResultLimit();
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
    }

    /**
     * Get the list of applications for the sp:multiSuggest component.
     * This was removed in 3.0 in favor of Ext/JSON, but we still need
     * the older components for awhile.
     */
    private static String SUGGEST_PREFIX_APPLICATION = "applicationPrefix";

    public List<Application> getApplications() throws GeneralException
    {
        List<Application> applications = null;
        Map request = super.getRequestParam();
        QueryOptions qo = new QueryOptions();
        String prefix = (String) request.get(SUGGEST_PREFIX_APPLICATION);
        
        if (null != prefix)
        {
            Filter filter = 
                Filter.ignoreCase(Filter.like("name", prefix, Filter.MatchMode.START));
            qo.add(filter);
        }
        
        qo.setResultLimit(8);
        qo.addOrdering("name", true);
        
        applications = getContext().getObjects(Application.class, qo);
        
        return applications;
    }




}
