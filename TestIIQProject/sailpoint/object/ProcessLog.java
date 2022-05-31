package sailpoint.object;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ProcessLog
        extends SailPointObject
{
    private static final long serialVersionUID = 1L;
    private String processName;
    private String workflowCaseName;
    private String launcher;
    private String caseStatus;
    private String caseId;
    private String stepName;
    private String approvalName;
    private String ownerName;
    private Date startTime;
    private Date endTime;
    /**
     * if it is called just "duration" spring fails.. why?
     */
    private int stepDuration;
    
    private int escalations;
    
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getProcessName()
    {
        return this.processName;
    }
    public void setProcessName(String processName)
    {
        this.processName = processName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getCaseId()
    {
        return this.caseId;
    }
    public void setCaseId(String caseId)
    {
        this.caseId = caseId;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getStepName()
    {
        return this.stepName;
    }
    public void setStepName(String stepName)
    {
        this.stepName = stepName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getApprovalName()
    {
        return this.approvalName;
    }
    public void setApprovalName(String approvalName)
    {
        this.approvalName = approvalName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getOwnerName()
    {
        return this.ownerName;
    }
    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public Date getStartTime()
    {
        return this.startTime;
    }
    public void setStartTime(Date start)
    {
        this.startTime = start;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public Date getEndTime()
    {
        return this.endTime;
    }
    public void setEndTime(Date end)
    {
        this.endTime = end;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public int getEscalations()
    {
        return this.escalations;
    }
    public void setEscalations(int escalations)
    {
        this.escalations = escalations;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public int getStepDuration()
    {
        return this.stepDuration;
    }
    public void setStepDuration(int stepDuration)
    {
        this.stepDuration = stepDuration;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getWorkflowCaseName()
    {
        return this.workflowCaseName;
    }
    public void setWorkflowCaseName(String workflowCaseName)
    {
        this.workflowCaseName = workflowCaseName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getCaseStatus()
    {
        return this.caseStatus;
    }
    public void setCaseStatus(String status)
    {
        this.caseStatus = status;
    }
    
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getLauncher()
    {
        return this.launcher;
    }
    public void setLauncher(String launcher)
    {
        this.launcher = launcher;
    }
    
    public int calculateStepDuration()
    {
        /**
         * It is okay to return an int because step duration is in seconds
         * and can hold duration for many years, but conversion to integer
         * must be cone only after the calculation is complete. So notice
         * the brackets
         */
        return  (int) ((this.endTime.getTime() - this.startTime.getTime()) /1000);
    }
    
    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    /**
     * Override the default display columns for this object type.
     */
    
    @Override
    public boolean hasName()
    {
        return false;
    }

    public static Map<String, String> getDisplayColumns() 
    {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("processName", "Process Name");
        cols.put("stepName", "Step Name");
        cols.put("approvalName", "Approval Name");
        cols.put("ownerName", "Owner Name");
        cols.put("stepDuration", "Step Duration");
        return cols;
    }
    
    public static String getDisplayFormat() 
    {
        return "%-34s %-20s %-20s %-20s %-20s %-10s\n";
    }
}
