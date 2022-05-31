/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.CertificationNamer;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Identity;
import sailpoint.object.Resolver;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.MessageRenderer;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * This component can convert a Certification TaskSchedule to a
 * CertificationScheduleDTO and vice versa.  This also is used to retrieve a
 * CertificationScheduleDTO with default values populated from the system
 * configuration.  Ideally, all task argument to certification schedule mapping
 * would happen here.  Unfortunately, this is also strewn about in the
 * CertificationExecutor.  It would be nice if this didn't generate an object
 * quite so web-centric (ie - a DTO), but we'll let the DTO stay for now instead
 * of coming up with a parallel object model.
 * 
 * @author peter.holcomb
 * @author Kelly Grizzle
 */
public class CertificationScheduler {

    private static final Log log = LogFactory.getLog(CertificationScheduler.class);

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constant that denotes the pseudo-frequency used by the scheduling UI for
     * continuous certifications.
     */
    public static final String FREQ_CONTINUOUS = "continuous";

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private boolean refreshCertsSynchronously;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    public CertificationScheduler(SailPointContext context) {
        this.context = context;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Set this to true to make saveSchedule() synchronously refresh
     * certifications (defaults to asynchronously).  This is really only used
     * for unit tests so we don't have to try to figure out when a request
     * completes.
     */
    public void setRefreshCertsSynchronously(boolean b) {
        this.refreshCertsSynchronously = b;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // SCHEDULE INITIALIZATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Method that initializes the schedule bean based on what is stored in the
     * system configuration
     */
    public CertificationSchedule initializeScheduleBean(Identity scheduler, Certification.Type type) throws GeneralException{
        CertificationSchedule schedule = new CertificationSchedule(context, scheduler);
        schedule.getDefinition().setType(type);
        schedule.getDefinition().setIncludePolicyViolations(!Certification.Type.ApplicationOwner.equals(type));

        // IdentityAI only supports certifications with identities as its entities.
        if (!schedule.getDefinition().getType().supportsRecommendations()) {

            schedule.getDefinition().setShowRecommendations(false);
            log.debug("Recommendations have been disabled for this certification because it's an unsupported certification type.");
        }

        // If the cert type does not support classifications, do not include classifications
        // in case the compliance manager has it globally set to true.
        if (!schedule.getDefinition().shouldIncludeClassifications()) {
            schedule.getDefinition().setIncludeClassifications(false);
        }

        return schedule;
    }

    private Message getDefaultDescription(CertificationDefinition def) throws GeneralException{

        Certification.Type type = def.getType();
        boolean isGlobal = def.isGlobal();

        String descMsgKey = null;
        String appNames = getApplicationNames(def.getApplicationIds(), 50);
        switch(type){
            case AccountGroupPermissions:
                descMsgKey = isGlobal ? MessageKeys.CERT_TYPE_GLBL_ACCT_GRP_PERMS :
                        MessageKeys.CERT_TYPE_ACCT_GRP_PERMS;
                return new Message(MessageKeys.CERT_SCHEDULE_TASK_DESC, descMsgKey, appNames);
            case AccountGroupMembership:
                descMsgKey = isGlobal ? MessageKeys.CERT_TYPE_GLBL_ACCT_GRP_MEMBERSHIP :
                        MessageKeys.CERT_TYPE_ACCT_GRP_MEMBERSHIP;
                return new Message(MessageKeys.CERT_SCHEDULE_TASK_DESC, descMsgKey, appNames);
            case BusinessRoleComposition: case BusinessRoleMembership:
                return new Message(MessageKeys.BIZ_ROLE_CERT_SCHEDULE_TASK_DESC);
            case DataOwner:
                return isGlobal ? new Message(MessageKeys.GLOBAL_DATA_OWNER_CERT_SCHEDULE_TASK_DESC) :
                        new Message(MessageKeys.CERT_SCHEDULE_TASK_DESC,
                            new Message(Certification.Type.DataOwner.getMessageKey()),appNames);
            case ApplicationOwner:
                return isGlobal ?  new Message(MessageKeys.GLOBAL_APP_CERT_SCHEDULE_TASK_DESC) :
                        new Message(MessageKeys.CERT_SCHEDULE_TASK_DESC,
                                new Message(Certification.Type.ApplicationOwner.getMessageKey()), appNames);
            case Group:
                return new Message(MessageKeys.ADVANCED_CERT_SCHEDULE_TASK_DESC);
            case Manager:
                return isGlobal ? new Message(MessageKeys.CERT_SHORTNAME_GLOBAL,
                        new Message(Certification.Type.Manager.getMessageKey())) :
                        new Message(MessageKeys.CERT_SCHEDULE_TASK_DESC,
                                       new Message(MessageKeys.MANAGER), def.getCertifierName());
            case Identity:
                return new Message(MessageKeys.INDIVIDUAL_CERT_SCHEDULE_TASK_DESC);
            case Focused:
                return new Message(MessageKeys.TARGETED_CERT_SCHEDULE_TASK_DESC);
            default:
                throw new RuntimeException("Unknown certification type");
        }

    }

     /**
     * Return a comma-separated string that has the names of the applications
     * Truncate the string if it is longer than maxLength.
     */
    public String getApplicationNames(List<String> applicationNames, int maxLength)
        throws GeneralException {

        if (Util.isEmpty(applicationNames))
            return null;

//        List<String> appNameList = new ArrayList<String>();
//        for (String appName : applicationNames) {
//            //TODO: Do we want to do the lookup to ensure it exists, or we could skip entirely -rap
//            Application app = this.context.getObjectByName(Application.class, appName);
//            if (null != app) {
//                appNameList.add(app.getName());
//            }
//        }
        
        String appNames = Util.listToCsv(applicationNames);
        if (appNames.length() > maxLength) {
            appNames = appNames.substring(0, maxLength - 3) + "...";
        }
        return appNames;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // SAVING AND LAUNCHING
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Saves the CertificationDefinition in the given schedule and created
     * a new task using the parameters specified in the schedule.
     */
    public TaskSchedule saveSchedule(CertificationSchedule schedule, boolean continuousHasLaunched)
        throws GeneralException {

        CertificationDefinition def = schedule.getDefinition();

        // Make a copy of the existing definition and schedule for a change
        // event if we have an existing schedule and this is a continuous cert.
        CertificationDefinition origDef = null;
        TaskSchedule origSched = null;
        if ((null != def.getId()) && (null != schedule.getTaskId()) &&
            def.isContinuous()) {

            // Create a deep copy of the original definition.
            origDef = copyDefinition(def);

            // TaskSchedules aren't managed by hibernate.  Not so much jiggery-pokery
            // is required.
            origSched = schedule.getExistingTask(context);
            origSched = (TaskSchedule) origSched.deepCopy((Resolver) this.context);
        }

        // Make sure the definition name is unique before saving
        String defName = def.getName() != null ? def.getName() : schedule.getName();
        def.setName(ObjectUtil.generateUniqueName(context,
                def.getId(), defName, CertificationDefinition.class, 0));

        if (def.getDescription() == null) {
            Message description = getDefaultDescription(def);
            if (description != null)
                def.setDescription(description.getLocalizedMessage());
        }

        // Set a flag that preserves the showRecommendations state.
        // This allows us to enable/disable the showRecommendations option
        // during staging phase if recommendations were enabled at cert gen.
        def.setRecommendationsGenerated(def.getShowRecommendations());

        // First, save the definition so we make sure to have an ID.
        this.context.saveObject(def);
        this.context.commitTransaction();

        // Create a nice name for the task schedule.
        String taskName = createCertificationScheduleName(def);
        
        // Create a task name using the certification name ensuring it's unique
        String taskId = schedule.getTaskId();
        taskName = ObjectUtil.generateUniqueName(context, taskId, taskName, TaskSchedule.class, 0);
        schedule.setName(taskName);

        // Delete the existing schedule if there is one.
        // QuartzPersistenceManager doesn't like to re-save schedules.
        if (null != taskId) {
            TaskSchedule existingSchedule =  schedule.getExistingTask(context);
            if (existingSchedule != null) {
                //taskName = existingSchedule.getName();
                if (log.isDebugEnabled())
                    log.debug("Certification \"" + existingSchedule.getName() + 
                              "\" is being replaced");
                
                this.context.removeObject(existingSchedule);
                this.context.commitTransaction();
            }
        }

        // Continuous certifications get executed once.
        String frequency =
            (def.isContinuous()) ? CronString.FREQ_ONCE : schedule.getFrequency();
        boolean ranNow = false;
        Date firstExecution = schedule.isRunNow() ? new Date() : schedule.getFirstExecution();

        // Build the task schedule.
        TaskSchedule ts = schedule.buildTask(context);

        // Launch it if we're running immediately.
        if (schedule.isRunNow() && !continuousHasLaunched) {
            schedule.setFirstExecution(firstExecution);

            if (CronString.FREQ_ONCE.equals(frequency)) {
                firstExecution = null;
                
                // We used to use TaskManager.run() here, but we want to save a
                // task schedule for continuous certifications, so I changed
                // this to use runNow() which uses a schedule.  Note that this
                // saves the TaskSchedule.  If we don't need it, it will be
                // removed later.
                TaskManager tm = new TaskManager(this.context);
                tm.runNow(ts);  
                ranNow = true;
            }
        }

        // If this is running in the future, set the cron string.
        if ((firstExecution != null) && !continuousHasLaunched) {
            CronString cs = new CronString(schedule.getFirstExecution(), frequency);
            String generatedCronExpression = cs.toString();
            ts.addCronExpression(generatedCronExpression);
            ts.setNextExecution(schedule.getFirstExecution());
        }

        // If the schedule hasn't been saved by the "runNow()" method, save it.
        // Don't delete the schedule here - the task execution code will take
        // care of deleting it after the last run.
        if (!ranNow) {
            this.context.saveObject(ts);
        }
        
        this.context.commitTransaction();
        
        if (log.isDebugEnabled())
            log.debug("Certification [" + ts.getName() + "] scheduled successfully");

        return ts;
    }

    /**
     * Create a nice name for the certification schedule.  This attempts to be
     * a friendly version of the certification name template so that people can
     * understand what schedule they are looking at.  See bug 8136.
     */
    private String createCertificationScheduleName(CertificationDefinition def)
        throws GeneralException {

        String name = def.getName();

        if (null != def.getCertificationNameTemplate()) {
            Map<String,String> params = new HashMap<String,String>();
            params.put(CertificationNamer.NAME_TEMPLATE_FULL_DATE, "[DATE]");
            params.put(CertificationNamer.NAME_TEMPLATE_DATE_YEAR, "[YEAR]");
            params.put(CertificationNamer.NAME_TEMPLATE_DATE_QUARTER, "[QUARTER]");
            params.put(CertificationNamer.NAME_TEMPLATE_DATE_MONTH, "[MONTH]");

            // Replace the template with friendlier names.
            name = MessageRenderer.render(def.getCertificationNameTemplate(), params);

            // The default names already surround the parameters with brackets.
            // Strip off the one we add if they are doubled up.
            name = name.replace("[[", "[");
            name = name.replace("]]", "]");

            // Now add the date at the end to help make unique.
            DateFormat df =
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            String date = df.format(new Date());
            name += " [" + date + "]";
        }

        return name;
    }
    
    /**
     * Create a deep copy of the given CertificationDefinition.
     */
    private CertificationDefinition copyDefinition(CertificationDefinition def)
        throws GeneralException {

        CertificationDefinition copy = null;

        // Go through this push/pop jiggery-pokery to avoid hibernate detach/
        // attach problems with multiple objects of the same identifier in the
        // same session.
        SailPointContext save = SailPointFactory.pushContext();
        try {
            SailPointContext mycon = SailPointFactory.getCurrentContext();
            copy = mycon.getObjectById(CertificationDefinition.class, def.getId());
            copy = (CertificationDefinition) copy.deepCopy((Resolver) this.context);
        }
        finally {
            SailPointFactory.popContext(save);
        }

        return copy;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // SEARCHING
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return all TaskSchedules for certifications.
     */
    public List<TaskSchedule> getSchedules() throws GeneralException {

        List<TaskSchedule> certSchedules = new ArrayList<TaskSchedule>();
        List<TaskSchedule> schedules =
            this.context.getObjects(TaskSchedule.class);

        if (null != schedules) {
            for (TaskSchedule schedule : schedules) {
                if (isCertificationSchedule(schedule)) {
                    certSchedules.add(schedule);
                }
            }
        }

        return certSchedules;
    }
    
    /**
     * Return all TaskSchedules that use the given CertificationDefinition.
     */
    public List<TaskSchedule> findSchedules(CertificationDefinition def)
        throws GeneralException {

        List<TaskSchedule> found = new ArrayList<TaskSchedule>();
        List<TaskSchedule> schedules = getSchedules();

        for (TaskSchedule schedule : schedules) {
            String defId = schedule.getArgument(CertificationSchedule.ARG_CERTIFICATION_DEFINITION_ID);
            //TODO: This was named ID, but we now set it as name?! -rap
            if (Util.nullSafeEq(defId, def.getName())) {
                found.add(schedule);
            }
        }

        return found;
    }

    /**
     * Return whether the given TaskSchedule is a certification schedule.
     */
    public boolean isCertificationSchedule(TaskSchedule sched)
        throws GeneralException {
    
        TaskDefinition taskDef = sched.getDefinition(this.context);
        if (null != taskDef) {
            TaskDefinition.Type type = taskDef.getEffectiveType();
            return TaskDefinition.Type.Certification.equals(type);
        }
        return false;
    }

    /**
     * Return the CertificationDefinition used by the given certification
     * schedule.
     *
     * @throws IllegalArgumentException  If the schedule does not reference a
     *     definition.
     */
    public CertificationDefinition getCertificationDefinition(TaskSchedule sched)
        throws GeneralException, IllegalArgumentException {

        CertificationDefinition def = null;
        if (null != sched) {
            String defId = sched.getArgument(CertificationSchedule.ARG_CERTIFICATION_DEFINITION_ID);
            if (null == defId) {
                throw new IllegalArgumentException("Certification schedule does not have certification definition ID: " + sched);
            }
            //TODO: This was named ID, but we now set it as name?! -rap
            def = this.context.getObjectByName(CertificationDefinition.class, defId);
        }
        return def;
    }

    public CertificationSchedule getCertificationSchedule(TaskSchedule input)
        throws GeneralException {
        CertificationDefinition def = getCertificationDefinition(input);
        return new CertificationSchedule(def, input);
    }

}
