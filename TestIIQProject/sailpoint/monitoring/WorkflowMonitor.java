package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

public class WorkflowMonitor implements IWorkflowMonitor
{
    static final Log log = LogFactory.getLog(WorkflowMonitor.class);
    
    private SailPointContext context;
    
    public WorkflowMonitor(SailPointContext context)
    {
        this.context = context;
    }
    
    public List<SearchResult> search(SearchParams params)
        throws GeneralException
    {
        return new WorkflowSearchHelper(this.context, params).search();
    }
    
    public List<WorkflowExecutionInfo> getWorkflowExecutionInfo(WorkflowExecutionInfoParams params)
        throws GeneralException
    {
        return new WorkflowExecutionInfoHelper(this.context, params).search();
    }
    
    public Map<String, Long> getExecutionStepCounts(WorkflowExecutionInfoParams params)
        throws GeneralException
    {
        return new WorkflowExecutionInfoHelper(this.context, params).getExecutionStepCounts();        
    }
    
    public List<StepOverviewInfo> getStepOverview(StepOverviewInfoParams params)
        throws GeneralException
    {
        return new StepOverviewHelper(this.context, params).search();
    }
    
    public Map<String, Long> countStepApprovals(StepOverviewInfoParams params) 
            throws GeneralException
    {
        return new StepOverviewHelper(this.context, params).countApprovals();
    }
    
    public List<StepApprovalInfo> getStepApprovalInfo(StepApprovalInfoParams params)
            throws GeneralException
    {
        return new StepApprovalInfoHelper(this.context, params).search();
    }
    
    public List<StepExecutionOwnerInfo> getStepExecutionOwnersInfo(StepExecutionOwnerInfoParams params)
        throws GeneralException
    {
        return new StepExecutionOwnersInfoHelper(this.context, params).search();
    }
    
    public List<WorkflowExecutionDetail> getWorkflowExecutionDetails(
            WorkflowExecutionDetailParams params)
            throws GeneralException
    {
        return new WorkflowExecutionDetailsHelper(this.context, params).search();
    }
            
    public ProcessMetrics getProcessMetrics(ProcessMetricsParams params)
            throws GeneralException
    {
        return new ProcessMetricsHelper(this.context, params).getProcessMetrics();
    }

    /**
     * This is not used anywhere, should I remove it? TQM
     * @param processName
     * @param stepName
     * @param numViolators set to 0 to return everyone
     * @return
     * @throws GeneralException
     */
    public List<IWorkflowMonitor.StepOwnerInfo> getTopNOwners(String processName, String stepName, int numViolators)
        throws GeneralException
    {
        List<IWorkflowMonitor.StepOwnerInfo> infos = new ArrayList<IWorkflowMonitor.StepOwnerInfo>();
        
        List<String> props = new ArrayList<String>();
        props.add("ownerName");
        props.add("avg(stepDuration)");

        QueryOptions options = new QueryOptions();
        options.add(Filter.or(
            Filter.eq("processName", processName), 
            Filter.like("processName", processName + "/", MatchMode.START)
        ));
        options.add(Filter.eq("stepName", stepName));
        options.add(Filter.notnull("ownerName"));

        options.addGroupBy("ownerName");
        options.addOrdering("avg(stepDuration)", false);

        if (numViolators > 0)
        {
            options.setResultLimit(numViolators);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            IWorkflowMonitor.StepOwnerInfo info = new IWorkflowMonitor.StepOwnerInfo();

            Object[] vals = it.next();
            info.setOwnerName((String) vals[0]);
            info.setAverage(((Double) vals[1]).floatValue());

            infos.add(info);
        }
        
        return infos;
    }
    
}