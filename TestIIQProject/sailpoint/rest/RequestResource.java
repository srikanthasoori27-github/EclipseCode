/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * REST methods for the "requests" resource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path(IIQClient.RESOURCE_REQUESTS)
public class RequestResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(RequestResource.class);

    private String name;
    private String type;
    private String string1;
    private String createdStartDate;
    private String createdEndDate;
    private String completed;

    /**
     * Get the list of possible Request Definition Types
     * 
     * @return ListResult with RequestDefinition objects containing Id/displayName for each request definition type
     * 
     * @throws GeneralException
     */
    @GET
    @Path("typeList")
    public ListResult getRequestDefinitionTypes()
    throws GeneralException
    {
        authorize(new RightAuthorizer(SPRight.FullAccessRequest));

        List<Map<String, Object>> requestDefinitionList = new ArrayList<Map<String, Object>>();

        QueryOptions qo =  super.getQueryOptions(null);
        // get the total count for the request definition types
        int totalCount = countResults(RequestDefinition.class, qo);

        qo.addOrdering("name", true);
        qo.setScopeResults(true);

        Iterator<Object[]> results = getContext().search(RequestDefinition.class, qo, Arrays.asList("id","name"));
        if (results != null){
            while(results.hasNext()){
                Object[] r = results.next();

                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", r[0]);
                row.put("displayName", r[1]);

                requestDefinitionList.add(row);
            }
        }

        return new ListResult(requestDefinitionList, totalCount);
    }

    /**
     * Get filtered list of Request objects for the requests.jsf page
     * 
     * @param form (with possible filter parameters)
     *  
     * @return List of filtered Request rows for table 
     */

    @POST
    @Path("list")
    public ListResult list(MultivaluedMap<String, String> form) throws GeneralException {

        authorize(new RightAuthorizer(SPRight.FullAccessRequest));

        processForm(form);

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        String error = null;
        int totalCount = 0;
        
        try {

            QueryOptions ops = getQueryOptions(UIConfig.REQUESTS_TABLE_COLUMNS);
            totalCount = countResults(Request.class, ops);
            results = getResults(UIConfig.REQUESTS_TABLE_COLUMNS, Request.class, ops);

        } catch (Throwable t) {
           log.error(t);
           error = t.getMessage();
        }

        ListResult result = null;
        if (error != null){
            result =  new ListResult(new ArrayList(), 0);
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(error);
        } else {
            result = new ListResult(results, totalCount);
        }

        return result;
    }

    @Override
    protected void processForm(MultivaluedMap<String, String> form){
        super.processForm(form);
        name = getSingleFormValue(form, "name");
        type = getSingleFormValue(form, "type");
        string1 = getSingleFormValue(form, "string1");
        createdStartDate = getSingleFormValue(form, "createdStartDate");
        createdEndDate = getSingleFormValue(form, "createdEndDate");
        completed = getSingleFormValue(form, "completed");

        if (sortBy == null || "".equals(sortBy)){
            sortBy = "created";
            sortDirection = "DESC";
        }
    }

    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException{
        QueryOptions ops = super.getQueryOptions(columnsKey);

        if (!Util.isNullOrEmpty(name)){
            ops.add(Filter.like("name", name));
        }

        if (!Util.isNullOrEmpty(string1)){
            ops.add(Filter.like("string1", string1));
        }

        if (!Util.isNullOrEmpty(type)){
            ops.add(Filter.eq("definition.id", type));
        }

        Date minCreateDate = parseDateRange(createdStartDate, true);
        if (minCreateDate != null){
            ops.add(Filter.gt("created", minCreateDate));
        }

        Date maxCreateDate = parseDateRange(createdEndDate, false);
        if (maxCreateDate != null){
            ops.add(Filter.lt("created", maxCreateDate));
        }

        if (!Util.isNullOrEmpty(completed)){
            boolean completedBool = Util.atob(completed);
            if (completedBool) {
                ops.add(Filter.notnull("completed"));
            }
            else{
                ops.add(Filter.isnull("completed"));
            }
        }

        return ops;
    }
}
