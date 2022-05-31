package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionInfo;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionInfoParams;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

class WorkflowExecutionInfoHelper
{
    private SailPointContext context;
    private WorkflowExecutionInfoParams params;
    private List<WorkflowExecutionInfo> results;
    
    public WorkflowExecutionInfoHelper(SailPointContext context, WorkflowExecutionInfoParams params)
    {
        this.context = context;
        this.params = params;
    }
    
    public List<WorkflowExecutionInfo> search()
        throws GeneralException
    {
        List<String> props = new ArrayList<String>();
        props.add("caseId");
        props.add("workflowCaseName");
        props.add("launcher");
        props.add("startTime");
        props.add("endTime");
        props.add("stepDuration");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.isnull("stepName"));
        options.add(Filter.notnull("endTime"));
        
        if (timeRestrict())
        {
            options.add(getTimeRestrictFilter());
        }
        
        if (this.params.getParticipants().size() > 0)
        {
            options.add(getParticipantsFilter());
        }
        
        options.setOrderBy("startTime");
        
        if (WorkflowMonitor.log.isDebugEnabled())
        {
            WorkflowMonitor.log.debug("QueryOptions: " + options);
        }
        
        this.results = new ArrayList<WorkflowExecutionInfo>();

        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            WorkflowExecutionInfo result = new WorkflowExecutionInfo();
            Object[] vals = it.next();
            result.setCaseId((String) vals[0]);
            result.setWorkflowCaseName((String) vals[1]);
            result.setLauncher((String) vals[2]);
            result.setStartTime((Date) vals[3]);
            result.setEndTime((Date) vals[4]);
            result.setExecutionTime(((Integer) vals[5]).intValue());

            this.results.add(result);
        }
        
        return this.results;
    }
    
    /**
     * Gets the number of steps in each execution
     * @return Map of number of steps for each execution keyed by caseId 
     * @throws GeneralException
     */
    public Map<String, Long> getExecutionStepCounts() 
        throws GeneralException
    {
        List<String> props = new ArrayList<String>();
        props.add("workflowCaseName");
        props.add("caseId");
        props.add("count(stepName)");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", this.params.getWorkflowName()), 
            Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
        ));
        options.add(Filter.isnull("approvalName"));
        options.setGroupBys(Arrays.asList(new String[] {"caseId", "workflowCaseName"}));
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        Map<String, Long> executionStepCounts = new HashMap<String, Long>();
        while (it.hasNext()) {
            Object[] vals = it.next();
            executionStepCounts.put((String)vals[1], (Long)vals[2]);
        }
        return executionStepCounts;
    }
    
    private Filter getParticipantsFilter()
    {
        Filter subqueryFilter = Filter.and(
                Filter.in("ownerName", this.params.getParticipants()),
                Filter.or(
                    Filter.eq("processName", this.params.getWorkflowName()), 
                    Filter.like("processName", this.params.getWorkflowName() + "/", MatchMode.START)
                ));
        
        return Filter.subquery("caseId", ProcessLog.class, "caseId", subqueryFilter);
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
