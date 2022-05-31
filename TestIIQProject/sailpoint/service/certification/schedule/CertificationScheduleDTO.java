package sailpoint.service.certification.schedule;

import java.util.Date;
import java.util.Map;

import sailpoint.object.CertificationSchedule;
import sailpoint.service.BaseDTO;
import sailpoint.tools.Util;

/**
 * DTO object to represent the CertificationSchedule object on the new Certification Schedule UI
 * @author brian.li
 *
 */
public class CertificationScheduleDTO extends BaseDTO{

    private CertificationDefinitionDTO definition;
    private String name;
    private String certificationGroupId;
    private String certificationGroupName;
    private String taskId;
    private Date firstExecution;
    private Date activated;
    private String frequency;
    private boolean runNow;

    public CertificationScheduleDTO() { 
        this.definition = new CertificationDefinitionDTO();
    }

    /**
     * Constructs CertificationScheduleDTO object based on CertificationSchedule.
     * 
     * @param schedule CertificationSchedule object
     */
    public CertificationScheduleDTO(CertificationSchedule schedule) {
        setName(schedule.getName());
        setTaskId(schedule.getTaskId());
        setFirstExecution(schedule.getFirstExecution());
        setActivated(schedule.getActivated());
        setFrequency(schedule.getFrequency());
        setRunNow(schedule.isRunNow());
        if (schedule.getDefinition() != null) {
            setDefinition(new CertificationDefinitionDTO(schedule.getDefinition()));
        }
    }

    /**
     * Constructs CertificationScheduleDTO object based on input map.
     * 
     * @param data Map<String, Object> containing input data
     */
    @SuppressWarnings("unchecked")
    public CertificationScheduleDTO(Map<String, Object> data) {
        setName(Util.getString(data, "name"));
        setTaskId(Util.getString(data, "taskId"));
        setCertificationGroupId(Util.getString(data, "certificationGroupId"));
        setCertificationGroupName(Util.getString(data, "certificationGroupName"));
        setFirstExecution(Util.getDate(data, "firstExecution"));
        setFrequency(Util.getString(data, "frequency"));
        setRunNow(Util.getBoolean(data, "runNow"));
        setDefinition(new CertificationDefinitionDTO((Map<String, Object>)Util.get(data, "definition")));
    }

    public CertificationDefinitionDTO getDefinition() {
        return definition;
    }

    public void setDefinition(CertificationDefinitionDTO definition) {
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCertificationGroupId() {
        return certificationGroupId;
    }

    public void setCertificationGroupId(String certificationGroupId) {
        this.certificationGroupId = certificationGroupId;
    }

    public void setCertificationGroupName(String certificationGroupName) {
        this.certificationGroupName = certificationGroupName;
    }

    public String getCertificationGroupName() {
        return certificationGroupName;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Date getFirstExecution() {
        return firstExecution;
    }

    public void setFirstExecution(Date firstExecution) {
        this.firstExecution = firstExecution;
    }

    public Date getActivated() { return activated; }

    public void setActivated(Date activated) { this.activated = activated; }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public boolean isRunNow() {
        return runNow;
    }

    public void setRunNow(boolean runNow) {
        this.runNow = runNow;
    }
}
