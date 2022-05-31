package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.ApprovalName;
import sailpoint.monitoring.IWorkflowMonitor.StepExecutionOwnerInfo;
import sailpoint.monitoring.IWorkflowMonitor.StepExecutionOwnerInfoParams;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;

class StepExecutionOwnersInfoHelper
{
    private SailPointContext context;
    private StepExecutionOwnerInfoParams params;
    private List<StepExecutionOwnerInfo> results;
    
    public StepExecutionOwnersInfoHelper(SailPointContext context, StepExecutionOwnerInfoParams params)
    {
        this.context = context;
        this.params = params;
    }

    public List<StepExecutionOwnerInfo> search()
            throws GeneralException
    {
        List<String> props = new ArrayList<String>();
        props.add("ownerName");
        props.add("approvalName");
        props.add("startTime");
        props.add("endTime");
        props.add("stepDuration");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.eq("stepName", this.params.getStepName()));
        options.add(Filter.notnull("ownerName"));
        
        if (timeRestrict())
        {
            options.add(getTimeRestrictFilter());
        }
        
        if (this.params.getParticipants().size() > 0)
        {
            options.add(Filter.in("ownerName", this.params.getParticipants()));
        }
        
        options.setOrderBy("startTime");
        
        if (WorkflowMonitor.log.isDebugEnabled())
        {
            WorkflowMonitor.log.debug("QueryOptions: " + options);
        }
        
        this.results = new ArrayList<StepExecutionOwnerInfo>();

        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            StepExecutionOwnerInfo result = new StepExecutionOwnerInfo();

            Object[] vals = it.next();

            result.setOwnerName((String) vals[0]);
            result.setApprovalName(new ApprovalName((String) vals[1]));
            result.setStartTime((Date) vals[2]);
            result.setEndTime((Date) vals[3]);
            result.setExecutionTime(((Integer) vals[4]).intValue());

            this.results.add(result);
        }
        
        return this.results;
    }
    
    private boolean timeRestrict()
    {
        return this.params.getActiveDateStart() != null
                || this.params.getActiveDateEnd() != null;
    }
    
    private Filter getTimeRestrictFilter()
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
                
        return Filter.subquery("caseId", ProcessLog.class, "caseId", subqueryFilter);
    }
}
