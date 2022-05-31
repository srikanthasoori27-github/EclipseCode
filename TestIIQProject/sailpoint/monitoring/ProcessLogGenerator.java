package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ProcessLog;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.WorkflowContext;

public class ProcessLogGenerator
{
    private static final Log log = LogFactory.getLog(ProcessLogGenerator.class);
    

    public void startWorkflow(WorkflowContext wfc)
            throws GeneralException
    {
        Workflow wf = wfc.getWorkflow();
        if (wf.isAnyMonitoring()) {

            long startTime = System.currentTimeMillis();
            String processLogId = UUID.randomUUID().toString().replaceAll("-", "");

            wf.setProcessLogId(processLogId);
            wf.setStartTime(startTime);

            ProcessLog pl = new ProcessLog();
            RootInfo rootInfo = new RootInfo(wfc);
        
            pl.setProcessName(rootInfo.processName);
            pl.setWorkflowCaseName(wfc.getWorkflowCase().getName());
            pl.setLauncher(wfc.getWorkflowCase().getLauncher());
            pl.setCaseId(rootInfo.caseId);
            pl.setStartTime(new Date(startTime));

            saveOrQueue(wfc, pl);
        }
    }
    
    public void endWorkflow(WorkflowContext wfc)
            throws GeneralException
    {
        Workflow wf = wfc.getWorkflow();
        if (wf.isAnyMonitoring()) {

            ProcessLog pl = new ProcessLog();
            RootInfo rootInfo = new RootInfo(wfc);
        
            // If we have a bad start time, don't log.  See bug 16169.
            long startTime = wf.getStartTime();
            if (!validateStartTime(startTime, rootInfo)) {
                return;
            }
        
            pl.setProcessName(rootInfo.processName);
            pl.setWorkflowCaseName(wfc.getWorkflowCase().getName());
            pl.setLauncher(wfc.getWorkflowCase().getLauncher());
            pl.setCaseId(rootInfo.caseId);
            pl.setCaseStatus(getWorkflowCaseStatus(wfc.getWorkflowCase()));
            pl.setStartTime(new Date(startTime));
            pl.setEndTime(new Date());
            pl.setStepDuration(pl.calculateStepDuration());
        
            saveOrQueue(wfc, pl);
        }
    }

    /**
     * Validate that this is a valid start time.
     */
    private boolean validateStartTime(long startTime, RootInfo rootInfo) {
        if (startTime <= 0) {
            log.warn("Invalid start time for " + rootInfo.processName);
            return false;
        }
        return true;
    }
    
    /**
     * Either save the given ProcessLog or queue it to be saved later if the
     * workflow is transient.
     */
    private void saveOrQueue(WorkflowContext wfc, ProcessLog pl)
        throws GeneralException {

        if (wfc.isTransient()) {
            wfc.queueProcessLog(pl);
        }
        else {
            wfc.getSailPointContext().saveObject(pl);
            wfc.getSailPointContext().commitTransaction();
        }
    }
    
    private String getWorkflowCaseStatus(WorkflowCase workflowCase)
        throws GeneralException
    {
        String status = WorkflowLaunch.STATUS_COMPLETE;

        // By default we use the presence of Error messages
        // to indiciate failure. This can be overridden with
        // a case variable.
        String caseStatus = workflowCase.getString(Workflow.VAR_CASE_STATUS);
        if (caseStatus == null || caseStatus.length() == 0)
        {
            List<Message> errors = workflowCase.getErrors();
            if (errors != null && errors.size() > 0)
            {
                status = WorkflowLaunch.STATUS_FAILED;
            }
        }
        else
        {
            status = caseStatus;
        }

        return status;
    }
    
    
    public void startStep(WorkflowContext wfc)
            throws GeneralException
    {
        if (wfc.getStep().isMonitored())
        {
            wfc.getStep().setStartTime(System.currentTimeMillis());
        }

    }
    
    public void endStep(WorkflowContext wfc)
            throws GeneralException
    {
        if (wfc.getStep().isMonitored())
        {
            ProcessLog pl = new ProcessLog();
            RootInfo rootInfo = new RootInfo(wfc);

            // If we have a bad start time, don't log.  See bug 16169.
            long startTime = wfc.getStep().getStartTime();
            if (!validateStartTime(startTime, rootInfo)) {
                return;
            }

            pl.setProcessName(rootInfo.processName);
            pl.setWorkflowCaseName(wfc.getWorkflowCase().getName());
            pl.setLauncher(wfc.getWorkflowCase().getLauncher());
            pl.setStepName(wfc.getStep().getName());
            pl.setCaseId(rootInfo.caseId);
            pl.setStartTime(new Date(startTime));
            pl.setEndTime(new Date());
            pl.setStepDuration(pl.calculateStepDuration());
            saveOrQueue(wfc, pl);
        }

    }
    
    public void startApproval(WorkflowContext wfc)
        throws GeneralException
    {
        if (wfc.getStep().isMonitored())
        {
            wfc.getApproval().setStartTime(System.currentTimeMillis());
        }
        
    }

    public void endApproval(WorkflowContext wfc)
        throws GeneralException
    {
        if (wfc.getStep().isMonitored())
        {
            if (wfc.getApproval().getOwner() != null)
            {
                ProcessLog pl = new ProcessLog();
                RootInfo rootInfo = new RootInfo(wfc);

                // If we have a bad start time, don't log.  See bug 16169.
                long startTime = wfc.getApproval().getStartTime();
                if (!validateStartTime(startTime, rootInfo)) {
                    return;
                }

                pl.setProcessName(rootInfo.processName);
                pl.setWorkflowCaseName(wfc.getWorkflowCase().getName());
                pl.setLauncher(wfc.getWorkflowCase().getLauncher());
                pl.setStepName(wfc.getStep().getName());
                pl.setOwnerName(wfc.getApproval().getOwner());
                pl.setApprovalName(deriveApprovalName(wfc.getApproval()));
                pl.setCaseId(rootInfo.caseId);
                pl.setStartTime(new Date(startTime));
                pl.setEndTime(new Date());
                pl.setStepDuration(pl.calculateStepDuration());
                saveOrQueue(wfc, pl);
            }
        }
        
    }
    
    private List<Approval> getParents(final Approval approval)
    {
        List<Approval> parents = new ArrayList<Approval>();
        
        Approval currentParent = approval.getParent();
        while (currentParent != null)
        {
            parents.add(currentParent);
            
            currentParent = currentParent.getParent();
        }
    
        Collections.reverse(parents);
        return parents;
    }
    
    private List<String> getParentNames(Approval approval)
    {
        List<String> parentNames = new ArrayList<String>();

        List<Approval> parents = getParents(approval);
        for (Approval parent : parents)
        {
            parentNames.add(getApprovalName(parent));
        }
        return parentNames;
    }
    
    private String getApprovalName(Approval approval)
    {
        if (Util.isNullOrEmpty(approval.getName()))
        {
            return new Message(MessageKeys.WORK_ITEM_TYPE_APPROVAL).getLocalizedMessage();
        }
        else
        {
            return approval.getName(); 
        }
    }

    private String deriveApprovalName(final Approval approval)
    {
        List<String> comps = new ArrayList<String>();
        
        comps.addAll(getParentNames(approval));
        
        comps.add(getApprovalName(approval));
        
        return Util.join(comps, ",");
    }
    
    /*
     * Convenience class for establishing the information required to track sub-processes
     * within the context of their parents
     */
    private class RootInfo {
        private String processName;
        private String caseId;
        
        private RootInfo(WorkflowContext currentWFC) {
            processName = currentWFC.getWorkflow().getName();
            caseId = currentWFC.getWorkflow().getProcessLogId();
            if (currentWFC.getParent() != null) {
                StringBuilder nameBuilder = new StringBuilder(processName);
                while (currentWFC.getParent() != null) {
                    currentWFC = currentWFC.getParent();
                    nameBuilder.insert(0, "/");
                    nameBuilder.insert(0, currentWFC.getWorkflow().getName());
                    caseId = currentWFC.getWorkflow().getProcessLogId();
                }
                processName = nameBuilder.toString();
            }
        }
    }
}
