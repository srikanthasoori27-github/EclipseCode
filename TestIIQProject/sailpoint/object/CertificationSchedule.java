/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.SailPointContext;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * This class is a composite object that holds a CertificationDefinition and
 * the properties need to generate a TaskSchedule. It is used to simplify the process
 * of passing a set of certification schedule parameters around.
 *
 * @author jonathan.bryant
 */
public class CertificationSchedule {

    public static final String ARG_CERTIFICATION_DEFINITION_ID = TaskSchedule.ARG_CERTIFICATION_DEFINITION_ID;

    public static final String ARG_FIRST_EXECUTION = "firstExecution";

    private static final String CERTIFICATION_SCHEDULE_TASK_DEF_NAME = "Certification Manager";

    private CertificationDefinition definition;
    private boolean runNow;
    private Date activated;
    private Date firstExecution;
    private String frequency;
    private String taskId;
    private String name;
    private String description;
    private Identity owner;
    private Scope assignedScope;

    public CertificationSchedule() {
        definition = new CertificationDefinition();
    }

    public CertificationSchedule(SailPointContext context, Identity scheduler) throws GeneralException{
        this();
        this.initialize(context, scheduler);
    }

    public CertificationSchedule(SailPointContext context, Identity scheduler, CertificationDefinition definition)
            throws GeneralException{
        this(context, scheduler);
        this.definition = definition;
    }

    public CertificationSchedule(CertificationDefinition definition, TaskSchedule task){
        taskId = task.getId();
        setName(task.getName());
        setDescription(task.getDescription());
        setFirstExecution(task.getNextExecution());
        setTaskId(task.getId());
        setAssignedScope(task.getAssignedScope());

        // The frequency has already been set to a pseudo-frequency if the
        // schedule is continuous.
        if (!definition.isContinuous()) {
            String cronString =
                (null != task.getCronExpression(0)) ? task.getCronExpression(0) : null;
            if (null != cronString) {
                CronString cron = new CronString(cronString);
                setFrequency(cron.getFrequency());
            }
        }
        else {
            setFrequency(CertificationScheduler.FREQ_CONTINUOUS);
        }

        this.definition = definition;

    }

    private void initialize(SailPointContext context, Identity scheduler) throws GeneralException{

        definition.initialize(context);

        definition.setOwner(scheduler);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) + 5);
        setFirstExecution(cal.getTime());
        setFrequency(CronString.FREQ_ONCE);

        // Default certification owner to the current user
        owner = scheduler;

        Configuration sysConfig = context.getConfiguration();

        // Initialize the assigned scope to that of the scheduler if the
        // scheduler only controls a single scope.
        if (null != scheduler) {
            List<Scope> scopes = scheduler.getEffectiveControlledScopes(sysConfig);
            if ((null != scopes) && (1 == scopes.size())) {
                definition.setAssignedScope(scopes.get(0));
                this.setAssignedScope(scopes.get(0));
            }
        }

        // Default certification owner to the current user
        getDefinition().setCertificationOwner(scheduler);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public CertificationDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(CertificationDefinition definition) {
        this.definition = definition;
    }

    public boolean isRunNow() {
        return runNow;
    }

    public void setRunNow(boolean runNow) {
        this.runNow = runNow;
    }

    public Scope getAssignedScope() {
        return assignedScope;
    }

    public void setAssignedScope(Scope assignedScope) {
        this.assignedScope = assignedScope;
    }

    public Date getFirstExecution() {
        return firstExecution;
    }

    public void setFirstExecution(Date firstExecution) {
        this.firstExecution = firstExecution;
    }
    
    public Date getActivated() {
        return activated;
    }
    
    public void setActivated(Date activated) {
        this.activated = activated;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public TaskSchedule getExistingTask(SailPointContext context) throws GeneralException{
        TaskSchedule schedule = null;
        if (taskId != null){
            schedule = context.getObjectById(TaskSchedule.class, taskId);
        }
        return schedule;
    }

    public TaskSchedule buildTask(SailPointContext context) throws GeneralException {

        TaskSchedule ts = new TaskSchedule();
        Identity launcher = definition.getOwner();
        ts.setLauncher((null != launcher) ? launcher.getName() : null);
        ts.setName((null != name) ? name : definition.getName());
        ts.setDescription(definition.getDescription());

        TaskDefinition taskDef =
            context.getObjectByName(TaskDefinition.class, CERTIFICATION_SCHEDULE_TASK_DEF_NAME);
        ts.setTaskDefinition(taskDef);

        // Specify the result name so that we always get consistent results.
        ts.getArguments().put(TaskSchedule.ARG_RESULT_NAME, ts.getName());

        ts.setAssignedScope(assignedScope);

        // Set the certification definition name on the task.
        //TODO: This was named ID, but we now set it as name?! -rap
        ts.setArgument(ARG_CERTIFICATION_DEFINITION_ID, definition.getName());

        return ts;
    }
    
    public Date getActiveStartDate() {
        if (activated != null) {
            return activated;
        } else {
            Date now = new Date();
            
            if (!runNow && firstExecution != null && firstExecution.compareTo(now) > 0) {
                return firstExecution;
            } else {
                return now;
            }           
        }
    }

    public Date getActiveEndDate() {        
        return getEndDate(getActiveStartDate(), definition.getActivePeriodDurationAmount(),
                definition.getActivePeriodDurationScale());
    }

    public Date getChallengeEndDate() {
        return getEndDate(this.getActiveEndDate(), definition.getChallengePeriodDurationAmount(),
                definition.getChallengePeriodDurationScale());
    }

    /**
     * This is a calculated property that is based off the certification end
     * date or the challenge end date (if challenge is enabled).
     */
    public Date getAutomaticClosingDate() {
        // leveraging getRemediationStartDate() since both remediation start
        // and auto close happen at the same point in time
        return getEndDate(getRemediationStartDate(),
                          definition.getAutomaticClosingInterval(),
                          definition.getAutomaticClosingIntervalScale());
    }

    /**
     * This is a calculated property that is based off the certification end
     * date or the challenge end date (if challenge is enabled).
     */
    public Date getRemediationStartDate() {
        return (definition.isChallengePeriodEnabled()) ? this.getChallengeEndDate()
                                             : this.getActiveEndDate();
    }

    /**
     * This is a calculated property that is based off the remediation duration
     * and the certification or challenge end.
     */
    public Date getRemediationEndDate() {

        return getEndDate(getRemediationStartDate(), definition.getRemediationPeriodDurationAmount(),
                definition.getRemediationPeriodDurationScale());
    }


    private static Date getEndDate(Date start, Long amount, Duration.Scale scale) {

        Date endDate = null;

        if ((null != start) && (null != scale) && amount != null) {
            Duration duration = new Duration(amount, scale);
            endDate = duration.addTo(start);
        }

        return endDate;
    }

    public boolean isGlobalCertification(){
        return definition.isGlobal();
    }

    public int getApplicationCount(){
        return definition.getApplicationCount();
    }

}
