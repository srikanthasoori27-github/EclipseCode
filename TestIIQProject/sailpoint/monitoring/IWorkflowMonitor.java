package sailpoint.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/// I have made the interface methods as object based
/// as possible because I have a sneaking suspicion that
/// the parameters would change over time. Making them objects
/// will insulate us from changing the interface.
public interface IWorkflowMonitor
{
    public abstract List<SearchResult> search(
            SearchParams params) throws GeneralException;

    public abstract List<WorkflowExecutionInfo> getWorkflowExecutionInfo(
            WorkflowExecutionInfoParams params) throws GeneralException;
    
    public abstract Map<String, Long> getExecutionStepCounts(WorkflowExecutionInfoParams params)
            throws GeneralException;
    
    public abstract List<StepOverviewInfo> getStepOverview(
            StepOverviewInfoParams params) throws GeneralException;
    
    public abstract Map<String, Long> countStepApprovals(
            StepOverviewInfoParams params) throws GeneralException;

    public abstract List<StepApprovalInfo> getStepApprovalInfo(
            StepApprovalInfoParams params) throws GeneralException;
    
    public abstract List<StepExecutionOwnerInfo> getStepExecutionOwnersInfo(
            StepExecutionOwnerInfoParams params) throws GeneralException;

    public abstract List<WorkflowExecutionDetail> getWorkflowExecutionDetails(
            WorkflowExecutionDetailParams params) throws GeneralException;
    
    public abstract ProcessMetrics getProcessMetrics(ProcessMetricsParams params)
            throws GeneralException;
    
    public class SearchParams
    {
        private Date         activeDateStart;
        private Date         activeDateEnd;

        /**
         * time is in seconds
         */
        private int          executionTimeAverage;
        private int          executionTimeMax;

        private String       name;

        /**
         * do not send null lists 0 length is much better
         */
        private List<String> participants = new ArrayList<String>();

        private Status       status = Status.All;

        public Date getActiveDateStart()
        {
            return this.activeDateStart;
        }

        public void setActiveDateStart(Date activeDateStart)
        {
            this.activeDateStart = activeDateStart;
        }

        public Date getActiveDateEnd()
        {
            return this.activeDateEnd;
        }

        public void setActiveDateEnd(Date activeDateEnd)
        {
            this.activeDateEnd = activeDateEnd;
        }

        public int getExecutionTimeAverage()
        {
            return this.executionTimeAverage;
        }

        /**
         * 
         * @param executionTimeAverage in seconds
         */
        public void setExecutionTimeAverage(int executionTimeAverage)
        {
            this.executionTimeAverage = executionTimeAverage;
        }

        public int getExecutionTimeMax()
        {
            return this.executionTimeMax;
        }

        /**
         * 
         * @param executionTimeMax in seconds
         */
        public void setExecutionTimeMax(int executionTimeMax)
        {
            this.executionTimeMax = executionTimeMax;
        }

        public String getName()
        {
            return this.name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public List<String> getParticipants()
        {
            return this.participants;
        }

        public void setParticipants(List<String> participants)
        {
            this.participants = participants;
        }

        public void addParticipant(String val)
        {
            this.participants.add(val);
        }

        public Status getStatus()
        {
            return this.status;
        }

        public void setStatus(Status state)
        {
            this.status = state;
        }

    }

    public class SearchResult
    {
        private String name;

        /**
         * in seconds
         */
        private int    averageExecutionTime;
        private int    maxExecutionTime;
        private int    minExecutionTime;

        private int    numExecutions;

        public String getName()
        {
            return this.name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public int getAverageExecutionTime()
        {
            return this.averageExecutionTime;
        }

        public void setAverageExecutionTime(int averageExecutionTime)
        {
            this.averageExecutionTime = averageExecutionTime;
        }

        public int getMaxExecutionTime()
        {
            return this.maxExecutionTime;
        }

        public void setMaxExecutionTime(int maxExecutionTime)
        {
            this.maxExecutionTime = maxExecutionTime;
        }

        public int getMinExecutionTime()
        {
            return this.minExecutionTime;
        }

        public void setMinExecutionTime(int minExecutionTime)
        {
            this.minExecutionTime = minExecutionTime;
        }

        public int getNumExecutions()
        {
            return this.numExecutions;
        }

        public void setNumExecutions(int numExecutions)
        {
            this.numExecutions = numExecutions;
        }
        
        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    public class StepOwnerInfo
    {
        private String ownerName;
        private float  average;

        public String getOwnerName()
        {
            return this.ownerName;
        }

        public void setOwnerName(String ownerName)
        {
            this.ownerName = ownerName;
        }

        public float getAverage()
        {
            return this.average;
        }

        public void setAverage(float average)
        {
            this.average = average;
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    public enum Status
    {
        Successful(WorkflowLaunch.STATUS_COMPLETE),
        Failed(WorkflowLaunch.STATUS_FAILED),
        All("all");
        
        private String val;
        
        Status(String val)
        {
            this.val = val;
        }
        
        public String getValue()
        {
            return this.val;
        }
        
        public static Status fromWorkflowLaunchStatus(String workflowLaunchStatus)
        {
            for (Status status : Status.values())
            {
                if (status.getValue().equals(workflowLaunchStatus))
                {
                    return status;
                }
            }
                
            throw new IllegalStateException("Invalid workflowLaunchStatus: " + workflowLaunchStatus);
        }
    }
    
    /**
     * each instance of 
     * when the workflow was run
     */
    public class WorkflowExecutionInfo
    {
        private String caseId;
        private String workflowCaseName;
        private String launcher;
        
        private Date   startTime;
        private Date   endTime;

        /**
         * in seconds
         */
        private int    executionTime;

        public String getCaseId()
        {
            return this.caseId;
        }

        public void setCaseId(String caseId)
        {
            this.caseId = caseId;
        }

        public Date getStartTime()
        {
            return this.startTime;
        }

        public void setStartTime(Date startTime)
        {
            this.startTime = startTime;
        }

        public Date getEndTime()
        {
            return this.endTime;
        }

        public void setEndTime(Date endTime)
        {
            this.endTime = endTime;
        }

        /**
         *  in seconds
         * @return
         */
        public int getExecutionTime()
        {
            return this.executionTime;
        }

        public void setExecutionTime(int executionTime)
        {
            this.executionTime = executionTime;
        }
        
        public String getWorkflowCaseName()
        {
            return this.workflowCaseName;
        }

        public void setWorkflowCaseName(String workflowCaseName)
        {
            this.workflowCaseName = workflowCaseName;
        }

        public String getLauncher()
        {
            return this.launcher;
        }

        public void setLauncher(String launcher)
        {
            this.launcher = launcher;
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    /**
     * 
     * Should there be more than just the workflow name here? Discuss...
     */
    public class WorkflowExecutionInfoParams
    {
        private String workflowName;

        private Date         activeDateStart;
        private Date         activeDateEnd;

        /**
         * do not send null lists. size()==0 is much better
         */
        private List<String> participants = new ArrayList<String>();

        public String getWorkflowName()
        {
            return this.workflowName;
        }

        public void setWorkflowName(String workflowName)
        {
            this.workflowName = workflowName;
        }

        public Date getActiveDateStart()
        {
            return this.activeDateStart;
        }

        public void setActiveDateStart(Date activeDateStart)
        {
            this.activeDateStart = activeDateStart;
        }

        public Date getActiveDateEnd()
        {
            return this.activeDateEnd;
        }

        public void setActiveDateEnd(Date activeDateEnd)
        {
            this.activeDateEnd = activeDateEnd;
        }

        public List<String> getParticipants()
        {
            return this.participants;
        }

        public void setParticipants(List<String> participants)
        {
            this.participants = participants;
        }
        
        public void addParticipant(String name)
        {
            this.participants.add(name);
        }
    }

    /**
     *
     * Detail about a step in a workflow
     *
     */
    public class StepOverviewInfo
    {
        private String name;

        private int    averageExecutionTime;
        private int    minimumExecutionTime;
        private int    maximumExecutionTime;

        private int    numExecutions;

        public String getName()
        {
            return this.name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public int getAverageExecutionTime()
        {
            return this.averageExecutionTime;
        }

        public void setAverageExecutionTime(int averageExecutionTime)
        {
            this.averageExecutionTime = averageExecutionTime;
        }

        public int getMinimumExecutionTime()
        {
            return this.minimumExecutionTime;
        }

        public void setMinimumExecutionTime(int minimumExecutionTime)
        {
            this.minimumExecutionTime = minimumExecutionTime;
        }

        public int getMaximumExecutionTime()
        {
            return this.maximumExecutionTime;
        }

        public void setMaximumExecutionTime(int maximumExecutionTime)
        {
            this.maximumExecutionTime = maximumExecutionTime;
        }

        public int getNumExecutions()
        {
            return this.numExecutions;
        }

        public void setNumExecutions(int numExecutions)
        {
            this.numExecutions = numExecutions;
        }
        
        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    public class StepOverviewInfoParams
    {
        private String       workflowName;
        
        private List<String> participants = new ArrayList<String>();
        
        private Date         activeDateStart;
        private Date         activeDateEnd;

        private Status       status       = Status.All;        
        
        public String getWorkflowName()
        {
            return this.workflowName;
        }

        public void setWorkflowName(String workflowName)
        {
            this.workflowName = workflowName;
        }

        public List<String> getParticipants()
        {
            return this.participants;
        }

        public void setParticipants(List<String> participants)
        {
            this.participants = participants;
        }

        public Date getActiveDateStart()
        {
            return this.activeDateStart;
        }

        public void setActiveDateStart(Date activeDateStart)
        {
            this.activeDateStart = activeDateStart;
        }

        public Date getActiveDateEnd()
        {
            return this.activeDateEnd;
        }

        public void setActiveDateEnd(Date activeDateEnd)
        {
            this.activeDateEnd = activeDateEnd;
        }

        public Status getStatus()
        {
            return this.status;
        }

        public void setStatus(Status status)
        {
            this.status = status;
        }

        public void addParticipant(String val)
        {
            this.participants.add(val);
        }
    }

    /**
     *
     * Instance when step was executed
     *
     */
    public class StepExecutionOwnerInfo
    {
        private String ownerName;
        
        private ApprovalName approvalName;

        private Date   startTime;
        private Date   endTime;

        private int    executionTime;

        public String getOwnerName()
        {
            return this.ownerName;
        }

        public void setOwnerName(String executorName)
        {
            this.ownerName = executorName;
        }

        public Date getStartTime()
        {
            return this.startTime;
        }

        public void setStartTime(Date startTime)
        {
            this.startTime = startTime;
        }

        public Date getEndTime()
        {
            return this.endTime;
        }

        public void setEndTime(Date endTime)
        {
            this.endTime = endTime;
        }

        public int getExecutionTime()
        {
            return this.executionTime;
        }

        public void setExecutionTime(int executionTime)
        {
            this.executionTime = executionTime;
        }

        public ApprovalName getApprovalName()
        {
            return this.approvalName;
        }

        public void setApprovalName(ApprovalName approvalName)
        {
            this.approvalName = approvalName;
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    public class StepExecutionOwnerInfoParams
    {
        private String workflowName;
        private String stepName;
        private Date activeDateStart;
        private Date activeDateEnd;
        private List<String> participants = new ArrayList<String>();

        public String getWorkflowName()
        {
            return this.workflowName;
        }

        public void setWorkflowName(String workflowName)
        {
            this.workflowName = workflowName;
        }

        public String getStepName()
        {
            return this.stepName;
        }

        public void setStepName(String stepName)
        {
            this.stepName = stepName;
        }

        public Date getActiveDateStart()
        {
            return this.activeDateStart;
        }

        public void setActiveDateStart(Date activeDateStart)
        {
            this.activeDateStart = activeDateStart;
        }

        public Date getActiveDateEnd()
        {
            return this.activeDateEnd;
        }

        public void setActiveDateEnd(Date activeDateEnd)
        {
            this.activeDateEnd = activeDateEnd;
        }

        public List<String> getParticipants()
        {
            return this.participants;
        }

        public void setParticipants(List<String> participants)
        {
            this.participants = participants;
        }
        
        public void addParticipant(String val)
        {
            this.participants.add(val);
        }
    }
    
    public class StepApprovalInfo
    {
        private ApprovalName approvalName;

        private int    averageExecutionTime;
        private int    minimumExecutionTime;
        private int    maximumExecutionTime;

        private int    numExecutions;

        public ApprovalName getApprovalName()
        {
            return this.approvalName;
        }

        public void setApprovalName(ApprovalName approvalName)
        {
            this.approvalName = approvalName;
        }

        public int getAverageExecutionTime()
        {
            return this.averageExecutionTime;
        }

        public void setAverageExecutionTime(int averageExecutionTime)
        {
            this.averageExecutionTime = averageExecutionTime;
        }

        public int getMinimumExecutionTime()
        {
            return this.minimumExecutionTime;
        }

        public void setMinimumExecutionTime(int minimumExecutionTime)
        {
            this.minimumExecutionTime = minimumExecutionTime;
        }

        public int getMaximumExecutionTime()
        {
            return this.maximumExecutionTime;
        }

        public void setMaximumExecutionTime(int maximumExecutionTime)
        {
            this.maximumExecutionTime = maximumExecutionTime;
        }

        public int getNumExecutions()
        {
            return this.numExecutions;
        }

        public void setNumExecutions(int numExecutions)
        {
            this.numExecutions = numExecutions;
        }
        
        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }
    
    public class StepApprovalInfoParams
    {
        private String workflowName;
        private String stepName;
        private Date activeDateStart;
        private Date activeDateEnd;
        private List<String> participants = new ArrayList<String>();
        private Status       status       = Status.All;        

        public String getWorkflowName()
        {
            return this.workflowName;
        }

        public void setWorkflowName(String workflowName)
        {
            this.workflowName = workflowName;
        }

        public String getStepName()
        {
            return this.stepName;
        }

        public void setStepName(String stepName)
        {
            this.stepName = stepName;
        }

        public Date getActiveDateStart()
        {
            return this.activeDateStart;
        }

        public void setActiveDateStart(Date activeDateStart)
        {
            this.activeDateStart = activeDateStart;
        }

        public Date getActiveDateEnd()
        {
            return this.activeDateEnd;
        }

        public void setActiveDateEnd(Date activeDateEnd)
        {
            this.activeDateEnd = activeDateEnd;
        }

        public List<String> getParticipants()
        {
            return this.participants;
        }

        public void setParticipants(List<String> participants)
        {
            this.participants = participants;
        }
        
        public void addParticipant(String val)
        {
            this.participants.add(val);
        }
        
        public Status getStatus()
        {
            return this.status;
        }

        public void setStatus(Status status)
        {
            this.status = status;
        }
    }
    
    public class WorkflowExecutionDetailParams
    {
        private String caseId;

        public String getCaseId()
        {
            return this.caseId;
        }

        public void setCaseId(String caseId)
        {
            this.caseId = caseId;
        }
    }
    
    public class ApprovalName
    {
        private String nameInDb;
        private boolean nameNull;
        private List<String> components;
        private volatile int hashCode = 0;
        
        public ApprovalName(String nameInDb)
        {
            this.nameInDb = nameInDb;
            if (nameInDb == null)
            {
                this.nameNull = true;
                this.components = new ArrayList<String>();
            }
            else
            {
                this.nameNull = false;
                this.components = Arrays.asList(nameInDb.split(","));
            }
        }
        
        public boolean isNameNull()
        {
            return this.nameNull;
        }

        public List<String> getComponents()
        {
            return this.components;
        }
        
        @Override
        public boolean equals(Object obj) 
        {
            if (this == obj)
            {
                return true;
            }
            if (!(obj instanceof ApprovalName))
            {
                return false;
            }
            
            ApprovalName other = ApprovalName.class.cast(obj);
            
            if (this.nameInDb == null)
            {
                return other.nameInDb == null;
            }
            
            return this.nameInDb.equals(other.nameInDb);
        }
        
        @Override
        public int hashCode()
        {
            if (this.hashCode == 0)
            {
                HashCodeBuilder builder = new HashCodeBuilder();
                this.hashCode = builder.append(this.nameInDb).toHashCode();
            }
            
            return this.hashCode;
        }
        
        @Override
        public String toString()
        {
            return Util.join(this.components, " > ");
        }
    }

    public interface IStepOrApprovalNameGenerator
    {
        public String generateName(String stepName, ApprovalName approvalName);
    }
    
    public class WorkflowExecutionDetail
    {
        private String stepName;
        private ApprovalName approvalName;
        
        private String participant;
        
        private Date startDate;
        private Date endDate;
        
        private int executionTime;

        public String getStepName()
        {
            return this.stepName;
        }

        public void setStepName(String stepName)
        {
            this.stepName = stepName;
        }

        public ApprovalName getApprovalName()
        {
            return this.approvalName;
        }

        public void setApprovalName(ApprovalName approvalName)
        {
            this.approvalName = approvalName;
        }

        public String getStepOrApprovalName()
        {
            IStepOrApprovalNameGenerator nameGenerator = new IStepOrApprovalNameGenerator()
            {
                public String generateName(String stepName, ApprovalName approvalName)
                {
                    if (WorkflowExecutionDetail.this.getApprovalName().isNameNull())
                    {
                        return WorkflowExecutionDetail.this.getStepName();
                    }
                    else
                    {
                        List<String> comps = new ArrayList<String>();
                        comps.add(WorkflowExecutionDetail.this.getStepName());
                        comps.addAll(WorkflowExecutionDetail.this.getApprovalName().getComponents());
                        
                        return Util.join(comps, " > ");
                    }
                }
            };
            
            return getStepOrApprovalName(nameGenerator);
        }
        
        public String getStepOrApprovalName(IStepOrApprovalNameGenerator nameGenerator)
        {
            return nameGenerator.generateName(this.stepName, this.approvalName);
        }

        public String getParticipant()
        {
            return this.participant;
        }

        public void setParticipant(String participant)
        {
            this.participant = participant;
        }

        public Date getStartDate()
        {
            return this.startDate;
        }

        public void setStartDate(Date startDate)
        {
            this.startDate = startDate;
        }

        public Date getEndDate()
        {
            return this.endDate;
        }

        public void setEndDate(Date endDate)
        {
            this.endDate = endDate;
        }

        public int getExecutionTime()
        {
            return this.executionTime;
        }

        public void setExecutionTime(int executionTime)
        {
            this.executionTime = executionTime;
        }
        
        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }
        
    }

    public class ProcessMetricsParams
    {
        private String processName;

        public String getProcessName()
        {
            return this.processName;
        }

        public void setProcessName(String processName)
        {
            this.processName = processName;
        }
    }
    
    public class ProcessMetrics
    {
        private int numExecutions;
        private int successfulExecutions;
        private int failedExecutions;
        private int pendingExecutions;
        
        private int minExecutionTime;
        private int averageExecutionTime;
        private int maxExecutionTime;
        
        private Date dateOfLastExecution;

        public int getNumExecutions()
        {
            return this.numExecutions;
        }

        public void setNumExecutions(int numExecutions)
        {
            this.numExecutions = numExecutions;
        }

        public int getSuccessfulExecutions()
        {
            return this.successfulExecutions;
        }

        public void setSuccessfulExecutions(int successfulExecutions)
        {
            this.successfulExecutions = successfulExecutions;
        }

        public int getFailedExecutions()
        {
            return this.failedExecutions;
        }

        public void setFailedExecutions(int failedExecutions)
        {
            this.failedExecutions = failedExecutions;
        }

        public int getPendingExecutions()
        {
            return this.pendingExecutions;
        }

        public void setPendingExecutions(int pendingExecutions)
        {
            this.pendingExecutions = pendingExecutions;
        }

        public int getAverageExecutionTime()
        {
            return this.averageExecutionTime;
        }

        public void setAverageExecutionTime(int averageExecutionTime)
        {
            this.averageExecutionTime = averageExecutionTime;
        }

        public int getMinExecutionTime()
        {
            return this.minExecutionTime;
        }

        public void setMinExecutionTime(int minExecutionTime)
        {
            this.minExecutionTime = minExecutionTime;
        }

        public int getMaxExecutionTime()
        {
            return this.maxExecutionTime;
        }

        public void setMaxExecutionTime(int maxExecutionTime)
        {
            this.maxExecutionTime = maxExecutionTime;
        }

        public Date getDateOfLastExecution()
        {
            return this.dateOfLastExecution;
        }

        public void setDateOfLastExecution(Date dateOfLastExecution)
        {
            this.dateOfLastExecution = dateOfLastExecution;
        }
        
        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(
                    this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }
}
