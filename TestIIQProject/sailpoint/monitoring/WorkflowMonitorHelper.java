package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

public class WorkflowMonitorHelper
{
    public static IWorkflowMonitor newWorkflowMonitor(SailPointContext context)
    {
        return new WorkflowMonitor(context);
    }
    
    private static class MockWorkflowMonitor implements IWorkflowMonitor
    {

        public List<SearchResult> search(SearchParams params)
        {
            List<SearchResult> results = new ArrayList<SearchResult>();
            
            SearchResult result = new SearchResult();
            result.setAverageExecutionTime(400);
            result.setMaxExecutionTime(800);
            result.setMinExecutionTime(200);
            result.setName("Workflow1");
            result.setNumExecutions(21);
            results.add(result);

            result = new SearchResult();
            result.setAverageExecutionTime(250);
            result.setMaxExecutionTime(300);
            result.setMinExecutionTime(200);
            result.setName("Workflow2");
            result.setNumExecutions(15);
            results.add(result);
            
            result = new SearchResult();
            result.setAverageExecutionTime(500);
            result.setMaxExecutionTime(1000);
            result.setMinExecutionTime(200);
            result.setName("Workflow3");
            result.setNumExecutions(115);
            results.add(result);
            
            return results;
        }

        public List<WorkflowExecutionInfo> getWorkflowExecutionInfo(WorkflowExecutionInfoParams params) throws GeneralException
        {
            // TODO Auto-generated method stub
            return null;
        }

        public List<StepExecutionOwnerInfo> getStepExecutionOwnersInfo(
                StepExecutionOwnerInfoParams params)
        {
            // TODO Auto-generated method stub
            return null;
        }
        
        public Map<String, Long> getExecutionStepCounts(WorkflowExecutionInfoParams params)
                throws GeneralException 
        {
            return new HashMap<String, Long>();
        }

        public List<StepOverviewInfo> getStepOverview(
                StepOverviewInfoParams params)
        {
            // TODO Auto-generated method stub
            return null;
        }
        
        public Map<String, Long> countStepApprovals(
                StepOverviewInfoParams params)
        {
            return new HashMap<String, Long>();
        }


        public List<WorkflowExecutionDetail> getWorkflowExecutionDetails(
                WorkflowExecutionDetailParams params)
                throws GeneralException
        {
            // TODO Auto-generated method stub
            return null;
        }

        public ProcessMetrics getProcessMetrics(
                ProcessMetricsParams params)
                throws GeneralException
        {
            // TODO Auto-generated method stub
            return null;
        }

        public List<StepApprovalInfo> getStepApprovalInfo(
                StepApprovalInfoParams params)
                throws GeneralException
        {
            // TODO Auto-generated method stub
            return null;
        }
        
    }
}
