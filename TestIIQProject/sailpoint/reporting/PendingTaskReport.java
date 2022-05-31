/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.reporting.datasource.WorkItemDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * A PendingTaskReport class, used to execute Jasper reports.
 */
public class PendingTaskReport extends JasperExecutor {

	//////////////////////////////////////////////////////////////////////
	//
	// Fields
	//
	//////////////////////////////////////////////////////////////////////

	private static Log _log = LogFactory.getLog(PendingTaskReport.class);

	//////////////////////////////////////////////////////////////////////
	//
	// 
	//
	//////////////////////////////////////////////////////////////////////

	private final String DETAIL_REPORT = "PendingWorkItemsReport";
	private final String GRID_REPORT = "PendingWorkItemsGridReport";

        @Override
	public String getJasperClass() {
		String className = GRID_REPORT;
		if ( showDetailed() ) {
			className = DETAIL_REPORT;
		}
	    return className;
	}


	public Map<Class, List<Filter>> buildFilters(Attributes<String,Object> inputs) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        List<Filter> workItemFilters = new ArrayList<Filter>();
        List<Filter> workItemArchiveFilters = new ArrayList<Filter>();
        
	    /** Handle states in special manner so that we create ors, not ands  */
	    String states = (String)inputs.get("states");
	    if(states!=null) {
	        List<Filter> stateFilters = new ArrayList<Filter>();
	        List<String> statesList = Util.csvToList(states);
	        for(String state : statesList) {
	          if(state.equals("Open")) {
	              stateFilters.add(Filter.isnull("state"));
	          } else {
	              stateFilters.add(Filter.eq("state", state));
	          }	          
	        }
	        if(stateFilters.size()>1) {
                filters.add(Filter.or(stateFilters));
            } else if(stateFilters.size()>0) {
                filters.add(stateFilters.get(0));
            }
	    }
	    
	    
	    
		addEQFilter(workItemFilters, inputs, "requesters", "requester.id", null);
		// for WorkItemArchive, the requester is the identity name.  Future iterations of this report may need
		// to revisit this for archives with requesters that no longer exist.
		QueryOptions nameOptions = new QueryOptions();
		nameOptions.add(Filter.eq("id", inputs.get("requesters")));
		Iterator<Object[]> requesterNameResults = getContext().search(Identity.class, nameOptions, "name");
		String requesterName = null;
		if (requesterNameResults != null && requesterNameResults.hasNext()) {
		    requesterName = (String) requesterNameResults.next()[0];
		    inputs.put("requesterName", requesterName);
		}
		addEQFilter(workItemArchiveFilters, inputs, "requesterName", "requester", null);
		
	    addEQFilter(workItemFilters, inputs, "owners", "owner.id", null);
	    // Same smoke 'n mirrors for owner
	    nameOptions = new QueryOptions();
        nameOptions.add(Filter.eq("id", inputs.get("owners")));
        Iterator<Object[]> ownerNameResults = getContext().search(Identity.class, nameOptions, "name");
        String ownerName = null;
        if (ownerNameResults != null && ownerNameResults.hasNext()) {
            ownerName = (String) ownerNameResults.next()[0];
            inputs.put("ownerName", ownerName);
        }
        addEQFilter(workItemArchiveFilters, inputs, "ownerName", "ownerName", null);  
		
		addEQFilter(filters, inputs, "types", "type", null);
		addEQFilter(filters, inputs, "levels", "level", null);

		addDateTypeFilter(filters, inputs, "expirationDate", "expirationDateType", "expiration", null);
		addDateTypeFilter(filters, inputs, "violationDate", "violationDateType", "created", null);
		addGTEFilter(filters, inputs, "reminders", "reminders");
		
		// Put'em all together
		Map<Class, List<Filter>> scopedFilters = new HashMap<Class, List<Filter>>();
		workItemFilters.addAll(filters);
		workItemArchiveFilters.addAll(filters);
		scopedFilters.put(WorkItem.class, workItemFilters);
		scopedFilters.put(WorkItemArchive.class, workItemArchiveFilters);
		
		return scopedFilters;
	}

	public TopLevelDataSource getDataSource()
	throws GeneralException {
		Attributes<String,Object> args = getInputs();
		List<String> statusOptions = Util.csvToList((String) args.get("statusOptions"));
		List<Class> scopes = new ArrayList<Class>();
		
		if (statusOptions.contains(WorkItemUtil.WORK_ITEM_ACTIVE_STATE_ACTIVE)) {
		    scopes.add(WorkItem.class);
		}
		
		if (statusOptions.contains(WorkItemUtil.WORK_ITEM_ACTIVE_STATE_ARCHIVED)) {
		    scopes.add(WorkItemArchive.class);
		}
		
		return new WorkItemDataSource(scopes.toArray(new Class[scopes.size()]), buildFilters(args), getLocale(), getTimeZone());
	}
}
