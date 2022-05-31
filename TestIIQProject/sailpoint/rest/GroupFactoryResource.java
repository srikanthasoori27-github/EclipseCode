/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("groupFactory")
public class GroupFactoryResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(GroupFactoryResource.class);

    @GET
    @Path("list")
    public ListResult getFactories()
        throws GeneralException {
    	//Used in GroupFactorySelector and AccessRequestStatusGrid
        //Note: This previously authorized on myAccessRequests.jsf, which no longer exists, but that resource
        //was authorized for all users anyway, so using AllowAllAuthorizer here.
    	authorize(new AllowAllAuthorizer());

        List<Map<String, Object>> factoryList = new ArrayList<Map<String, Object>>();

        QueryOptions qo =  super.getQueryOptions(null);
        qo.addOrdering("name", true);
        qo.add(Filter.eq("enabled", true));      
        qo.setScopeResults(true);
        
        Iterator<Object[]> results = getContext().search(GroupFactory.class, qo, Arrays.asList("id","name"));
        if (results != null){
            while(results.hasNext()){
                Object[] r = results.next();
                String id = (String)r[0];
                int count = getContext().countObjects(GroupDefinition.class,
                        new QueryOptions(Filter.eq("factory.id", id)));

                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", id);
                row.put("displayName",  r[1]);            
                row.put("count", count);

                factoryList.add(row);
            }
        }

        return new ListResult(factoryList, factoryList.size());
    }

    @GET
    @Path("options")
    public ListResult getOptions(@QueryParam("id") String factory, @QueryParam("start") int startParm,
            @QueryParam("limit") int limitParm,  @QueryParam("page") int pageParm)
        throws GeneralException {
    	
        //Used in GroupFactorySelector and AccessRequestStatusGrid
        //Note: This previously authorized on myAccessRequests.jsf, which no longer exists, but that resource
        //was available to all users, so using AllowAllAuthorizer here.
        authorize(new AllowAllAuthorizer());

        start = startParm;
        limit = WebUtil.getResultLimit(limitParm);
        int page = pageParm;

        List<Map<String, String>> optionsList = new ArrayList<Map<String, String>>();

        QueryOptions ops = new QueryOptions();
        ops.addOrdering("name", true);
        ops.add(Filter.eq("factory.id", factory));

        int count = 0;
        
        Iterator<Object[]> defs = getContext().search(GroupDefinition.class, ops, Arrays.asList("id", "name"));
        if (defs != null){
            while(defs.hasNext()){
                Object[] def = defs.next();
                Map<String, String> row = new HashMap<String, String>();
                row.put("id", (String)def[0]);
                row.put("displayName", (String)def[1]);
                
                if (count >= ((page - 1)* limit) && count < (page * limit)){
                	optionsList.add(row);
                }
                
                count++;
            }
        }

        return new ListResult(optionsList, count);
        

    }

}