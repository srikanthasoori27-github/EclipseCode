/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.ActivityDataSource;
import sailpoint.reporting.datasource.ApplicationDataSource;
import sailpoint.reporting.datasource.SailPointDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class ApplicationActivityReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ApplicationActivityReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String DETAIL_REPORT = "ApplicationActivityMainReport";
    private static final String GRID_REPORT = "ApplicationActivityGridReport";

    public List<Filter> buildFilters(SailPointContext context, Attributes<String,Object> inputs) {
    	
    	/** We will receive a list of application ids, we need to convert these to a list of application
    	 * names so that we can search the application activity table by "sourceApplication"
    	 */    	
    	List<String> applicationIds = Util.stringToList(inputs.getString("applications"));
    	if(applicationIds!=null && !applicationIds.isEmpty()) {
    		List<String> applicationNames = new ArrayList<String>();
    		QueryOptions qo = new QueryOptions();
    		qo.add(Filter.in("id", applicationIds));
    		List<String> props = new ArrayList<String>();
    		props.add("name");
    		try {
	    		Iterator<Object[]> it = context.search(Application.class, qo, props);
	    		while(it.hasNext()) {
	    			Object[] row = it.next();
	                applicationNames.add((String)row[0]);
	    		}
    		} catch(GeneralException ge) {
    			log.info("Unable to search for Application by Ids: " + applicationIds);
    		}
    		inputs.put("applicationNames", applicationNames);
    	}
    	
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, "action", "action", null);
        addEQFilter(filters, inputs, "result", "result", null);
        addLikeFilter(filters, inputs, "target", "target", null);
        addEQFilter(filters, inputs, "applicationNames", "sourceApplication", null);
        inputs.put("startDateType", "After");
        inputs.put("endDateType", "Before");
        addDateTypeFilter(filters, inputs, "startDate", "startDateType", "timeStamp", null);
        addDateTypeFilter(filters, inputs, "endDate", "endDateType", "timeStamp", null);
        addEQFilter(filters, inputs, "identities", "identityId", null);
        return filters;
    }

    public List<Filter> buildAppFilters(SailPointContext ctx, Attributes<String,Object> args, List<Filter> filters) {
        
        /** Get the applications that had activity according to these filters: **/
        List<Filter> appFilters = new ArrayList<Filter>();
        try {
            QueryOptions qo = null;            
            List<Filter> actFilters = new ArrayList<Filter>();
            List<String> applicationNames = (List)args.getList("applicationNames");
        	if(applicationNames!=null && !applicationNames.isEmpty()) {
        		for(String application : applicationNames) {
        			qo = new QueryOptions();
        			for (Filter filter : filters){
                        qo.add(filter);
                    }
        			qo.add(Filter.eq("sourceApplication", application));
        			int count = ctx.countObjects(ApplicationActivity.class, qo);
        			if(count>0) {
        				actFilters.add(Filter.eq("name", application));
        			}
        		}
        	} 
        	/** If there are no filters applied, grab all applications that have activity **/
        	else {
        	    try {
            	    List<String> props = Arrays.asList("name");
            	    QueryOptions ops = new QueryOptions();
            	    ops.add(Filter.join("name", "ApplicationActivity.sourceApplication"));
            	    ops.setDistinct(true);
            	    Iterator<Object[]> apps = getContext().search(Application.class, ops, props);
            	    while(apps.hasNext()) {
            	        Object[] next = apps.next();
            	        String name = (String)next[0];
            	        actFilters.add(Filter.eq("name", name));
            	    }
        	    } catch(GeneralException ge) {
        	        log.warn("Unable to query applications for activity: " + ge.getMessage());
        	    }
        	}

            if(!actFilters.isEmpty()) {
                if(actFilters.size()>1) {
                    appFilters.add(Filter.or(actFilters));
                } else {
                    appFilters.add(actFilters.get(0));
                }
            } else {
                appFilters.add(Filter.isnull("name"));
            }
        } catch (GeneralException ge) {
            log.error("GeneralException during buildAppFilters. [" + ge.getMessage() + "]");
        }
        return appFilters;
    }

    public TopLevelDataSource getDataSource() 
        throws GeneralException {

        Attributes<String,Object> args = getInputs();
        SailPointContext ctx = getContext();
        SailPointDataSource datasource = null;
        
        List<Filter> filters = buildFilters(ctx, args);
        if(args.get(OP_JASPER_TEMPLATE).equals(GRID_REPORT)) {
            datasource = new ActivityDataSource(filters, getLocale(), getTimeZone());
            log.debug("Assigning ActivityDataSource ");
        }
        else {
            //This is probably a bad idea, but it's the only way I can think of to limit
            //the applications returned by the detail report to only applications that had 
            //activity in this period.  We are actually returning application objects in the 
            //detail report, so we need to modify the filters so that they apply to the application.class
            //and then pass the regular filters as ApplicationActivity filters to the sub report.                
            datasource = new ApplicationDataSource(buildAppFilters(ctx, args, filters), filters,
                    getLocale(), getTimeZone());
            log.debug("Assigning ApplicationDataSource ");
        }
	return datasource;
    }
}
