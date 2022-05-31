package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.ApprovalName;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionDetail;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionDetailParams;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

class WorkflowExecutionDetailsHelper
{
    private SailPointContext context;
    private WorkflowExecutionDetailParams params;
    private List<WorkflowExecutionDetail> results;
    
    public WorkflowExecutionDetailsHelper(
            SailPointContext context,
            WorkflowExecutionDetailParams params)
    {
        this.context = context;
        this.params = params;
    }

    public List<WorkflowExecutionDetail> search()
            throws GeneralException
    {
        this.results = new ArrayList<WorkflowExecutionDetail>();

        List<String> props = new ArrayList<String>();
        props.add("stepName");
        props.add("approvalName");
        props.add("ownerName");
        props.add("startTime");
        props.add("endTime");
        props.add("stepDuration");
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("caseId", this.params.getCaseId()));
        options.add(Filter.notnull("stepName"));
        options.add(Filter.notnull("startTime"));
        options.add(Filter.notnull("endTime"));
        
        options.setOrderBy("created");
        
        if (WorkflowMonitor.log.isDebugEnabled())
        {
            WorkflowMonitor.log.debug("QueryOptions: " + options);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            WorkflowExecutionDetail result = new WorkflowExecutionDetail();

            Object[] vals = it.next();

            result.setStepName((String) vals[0]);
            result.setApprovalName(new ApprovalName((String) vals[1]));
            result.setParticipant((String) vals[2]);
            result.setStartDate((Date) vals[3]);
            result.setEndDate((Date) vals[4]);
            result.setExecutionTime(((Integer) vals[5]).intValue());

            this.results.add(result);
        }

        return this.results;
    }
}
