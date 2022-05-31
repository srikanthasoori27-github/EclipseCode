package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.Status;
import sailpoint.monitoring.IWorkflowMonitor.StepOverviewInfo;
import sailpoint.monitoring.IWorkflowMonitor.StepOverviewInfoParams;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;

class StepOverviewHelper
{
    private SailPointContext context;
    private StepOverviewInfoParams params;
    private List<StepOverviewInfo> results;
    
    public StepOverviewHelper(SailPointContext context, StepOverviewInfoParams params)
    {
        this.context = context;
        this.params = params;
    }
    
    public List<StepOverviewInfo> search()
            throws GeneralException
    {
        this.results = new ArrayList<StepOverviewInfo>();

        List<String> props = new ArrayList<String>();
        props.add("stepName");
        props.add("min(stepDuration)");
        props.add("avg(stepDuration)");
        props.add("max(stepDuration)");
        props.add("count(*)");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.isnull("ownerName"));
        options.add(Filter.notnull("stepName")); // gives the endstep entry

        options.add(getTimeAndStatusRestrictFilter()); // workflow level always need this because don't want incomplete workflows
        
        if (this.params.getParticipants().size() > 0)
        {
            options.add(getParticipantsFilter());
        }
        
        options.addGroupBy("stepName");
        options.addOrdering("stepName", true);

        if (WorkflowMonitor.log.isDebugEnabled())
        {
            WorkflowMonitor.log.debug("QueryOptions: " + options);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            StepOverviewInfo result = new StepOverviewInfo();
            Object[] vals = it.next();
            result.setName((String) vals[0]);
            result.setMinimumExecutionTime(((Integer) vals[1]).intValue());
            result.setAverageExecutionTime(((Double) vals[2]).intValue());
            result.setMaximumExecutionTime(((Integer) vals[3]).intValue());
            result.setNumExecutions(((Long) vals[4]).intValue());

            this.results.add(result);
        }

        return this.results;
    }
    
    /**
     * Count the number of approvals associated with each step
     */
    public Map<String, Long> countApprovals() throws GeneralException 
    {
        Map<String, Long> countResults = new HashMap<String, Long>();
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.notnull("approvalName"));

        // workflow level always need this because don't want incomplete workflows
        options.add(getTimeAndStatusRestrictFilter()); 
        
        if (this.params.getParticipants().size() > 0)
        {
            options.add(getParticipantsFilter());
        }
        
        options.addGroupBy("stepName");
        List<String> props = Arrays.asList(new String [] {"stepName", "count(*)"});
        Iterator<Object[]> countIterator = this.context.search(ProcessLog.class, options, props);
        while (countIterator.hasNext()) 
        {
            Object[] vals = countIterator.next();
            countResults.put((String)vals[0], (Long)vals[1]);
        }
        return countResults;
    }

    private Filter getParticipantsFilter()
    {
        Filter subqueryFilter = Filter.and(
                Filter.in("ownerName", this.params.getParticipants()), 
                Filter.or(
                    Filter.eq("processName", this.params.getWorkflowName()), 
                    Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
                ));
        
        return Filter.subquery("stepName", ProcessLog.class, "stepName", subqueryFilter);
    }
    
    private Filter getTimeAndStatusRestrictFilter()
    {
        Filter subqueryFilter = Filter.and(
                Filter.isnull("stepName"),
                Filter.notnull("endTime"));
        
        if (this.params.getActiveDateStart() != null)
        {
            subqueryFilter = Filter.and(subqueryFilter, Filter.ge("startTime", this.params.getActiveDateStart()));
        }
        if (this.params.getActiveDateEnd() != null)
        {
            subqueryFilter = Filter.and(subqueryFilter, Filter.le("endTime", this.params.getActiveDateEnd()));
        }
        if (this.params.getStatus() != null && this.params.getStatus() != Status.All)
        {
            subqueryFilter = Filter.and(subqueryFilter, Filter.eq("caseStatus", this.params.getStatus().getValue()));
        }
        
        return Filter.subquery("caseId", ProcessLog.class, "caseId", subqueryFilter);
    }
}
