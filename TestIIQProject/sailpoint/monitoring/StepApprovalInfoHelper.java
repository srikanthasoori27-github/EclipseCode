package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.ApprovalName;
import sailpoint.monitoring.IWorkflowMonitor.Status;
import sailpoint.monitoring.IWorkflowMonitor.StepApprovalInfo;
import sailpoint.monitoring.IWorkflowMonitor.StepApprovalInfoParams;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;

class StepApprovalInfoHelper
{
    private SailPointContext context;
    private StepApprovalInfoParams params;
    private List<StepApprovalInfo> results;
    
    public StepApprovalInfoHelper(SailPointContext context, StepApprovalInfoParams params)
    {
        this.context = context;
        this.params = params;
    }
    
    public List<StepApprovalInfo> search()
            throws GeneralException
    {
        this.results = new ArrayList<StepApprovalInfo>();

        List<String> props = new ArrayList<String>();
        props.add("approvalName");
        props.add("min(stepDuration)");
        props.add("avg(stepDuration)");
        props.add("max(stepDuration)");
        props.add("count(*)");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.eq("stepName", this.params.getStepName()));
        options.add(Filter.notnull("ownerName"));

        options.add(getTimeAndStatusRestrictFilter()); // workflow level always need this because don't want incomplete workflows
        
        if (this.params.getParticipants().size() > 0)
        {
            options.add(getParticipantsFilter());
        }
        
        options.addGroupBy("approvalName");

        if (WorkflowMonitor.log.isDebugEnabled())
        {
            WorkflowMonitor.log.debug("QueryOptions: " + options);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            StepApprovalInfo result = new StepApprovalInfo();
            Object[] vals = it.next();
            result.setApprovalName(new ApprovalName((String) vals[0]));
            result.setMinimumExecutionTime(((Integer) vals[1]).intValue());
            result.setAverageExecutionTime(((Double) vals[2]).intValue());
            result.setMaximumExecutionTime(((Integer) vals[3]).intValue());
            result.setNumExecutions(((Long) vals[4]).intValue());

            this.results.add(result);
        }

        return this.results;
    }

    private Filter getParticipantsFilter()
    {
        return Filter.in("ownerName", this.params.getParticipants());
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
