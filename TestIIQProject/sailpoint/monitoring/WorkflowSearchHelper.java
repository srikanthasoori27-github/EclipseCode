package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.SearchParams;
import sailpoint.monitoring.IWorkflowMonitor.SearchResult;
import sailpoint.monitoring.IWorkflowMonitor.Status;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

class WorkflowSearchHelper
{
    private static final Log          log = LogFactory
                                                  .getLog(WorkflowSearchHelper.class);
    private SailPointContext          context;

    private SearchParams              params;
    private NameOwnerTimeStatusSearchHelper tnoHelper;
    private List<SearchResult>        results;

    public WorkflowSearchHelper(SailPointContext context, SearchParams params)
    {
        this.context = context;
        this.params = params;
        this.tnoHelper = new NameOwnerTimeStatusSearchHelper(this.params);
    }

    public List<SearchResult> search()
            throws GeneralException
    {
        this.results = new ArrayList<SearchResult>();

        handleFilter();

        return this.results;
    }
    
    private void handleFilter()
        throws GeneralException
    {
        String props = "processName, min(stepDuration), max(stepDuration), avg(stepDuration), count(*)";
        
        QueryOptions options = getQueryOptions();
        if (WorkflowSearchHelper.log.isDebugEnabled())
        {
            WorkflowSearchHelper.log.debug("QueryOptions: " + options);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            SearchResult result = new SearchResult();
            
            Object[] vals = it.next();

            result.setName((String) vals[0]);
            result.setMinExecutionTime(((Integer) vals[1]).intValue());
            result.setMaxExecutionTime(((Integer) vals[2]).intValue());
            result.setAverageExecutionTime(((Double) vals[3]).intValue());
            result.setNumExecutions(((Long) vals[4]).intValue());

            this.results.add(result);
        }
    }

    private QueryOptions getQueryOptions()
    {
        QueryOptions options = new QueryOptions();
        
        options.add(Filter.isnull("stepName"));
        options.add(Filter.notnull("endTime"));

        if (this.tnoHelper.restrictions())
        {
            options.add(this.tnoHelper.getFilter());
        }
        
        options.addGroupBy("processName");

        if (this.params.getExecutionTimeAverage() > 0)
        {
            options.add(Filter.ge("avg(stepDuration)", new Double(this.params.getExecutionTimeAverage())));
        }
        
        if (this.params.getExecutionTimeMax() > 0)
        {
            options.add(Filter.ge("max(stepDuration)", new Integer(this.params.getExecutionTimeMax())));
        }

        return options;
    }

    private class NameOwnerTimeStatusSearchHelper
    {
        private SearchParams params;
        
        public NameOwnerTimeStatusSearchHelper(SearchParams params)
        {
            this.params = params;
        }
        
        public boolean restrictions()
        {
            return 
                this.params.getParticipants().size() > 0
                || this.params.getName() != null
                || this.params.getActiveDateStart() != null
                || this.params.getActiveDateEnd() != null
                || (this.params.getStatus() != null && this.params.getStatus() != Status.All);
        }

        public Filter getFilter()
        {
            Filter filter = null;
            
            if (this.params.getParticipants().size() > 0)
            {
                Filter ownerNameFilter = Filter.in("ownerName", this.params.getParticipants());
                filter = Filter.subquery("caseId", ProcessLog.class, "caseId", ownerNameFilter);
            }
            
            if (this.params.getName() != null)
            {
                filter = WorkflowSearchHelper.checkNullAdd(
                        filter, 
                        Filter.eq("processName",this.params.getName()));
            }
            
            if (this.params.getActiveDateStart() != null)
            {
                filter = WorkflowSearchHelper.checkNullAdd(
                        filter, 
                        Filter.ge("startTime", this.params.getActiveDateStart()));
            }

            if (this.params.getActiveDateEnd() != null)
            {
                filter = WorkflowSearchHelper.checkNullAdd(
                        filter, 
                        Filter.le("endTime", this.params.getActiveDateEnd()));
            }
            
            if (this.params.getStatus() != null && this.params.getStatus() != Status.All)
            {
                filter = WorkflowSearchHelper.checkNullAdd(
                        filter, 
                        Filter.eq("caseStatus", this.params.getStatus().getValue()));
            }
            
            return filter;
        }
        
    }
    
    private static Filter checkNullAdd(Filter parent, Filter child)
    {
        if (parent == null)
        {
            return child;
        }
        else
        {
            return Filter.and(parent, child);
        }
    }
}
