/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of the PersistenceManager interface that
 * provides access to Quartz scheduling objects being managed
 * in a relational database.   Normally would have persistence
 * managers over in sailpoint.persistence but I'm trying
 * to keep the scheduling stuff together.  The only thing we
 * don't have here is sailpoint.object.JobSchedule.
 *
 * This serves two purposes, first it encapsulates the Quartz
 * API so we can splice in different schedulers if necessary. 
 * Second it exposes scheduler control through the SailPointContext
 * interface so the Quartz objects can be managed just like the
 * objects being manged by Hibernate.
 *
 * Quartz has a nice data model, but it is more complex than we
 * really need.  The classes are:
 *
 *    Job
 *      Interface of a class that implements the job
 * 
 *    JobDetail
 *      Persistent object containing information about the
 *      job including the name of the Job implementation class,
 *      and a JobDataMap of arguments to pass to the Job.
 *
 *    Trigger
 *      Persistent object containing information about when
 *      a job is to be performed.  References a JobDetail.
 *      May also contain a JobDataMap of arguments that override
 *      those in the JobDetail.
 *
 *    JobExecutionContext
 *      The state of a currently running job.   Not sure if this
 *      is persisted or derived at runtime.  Might be used to hold
 *      results of finished jobs?
 *
 * Folks from Waveset/Sun will recognize these as:
 *
 *     Job                 = TaskExecutor
 *     JobDetail           = TaskDefinition
 *     Trigger             = TaskSchedule
 *     JobExecutionContext = TaskInstance
 *
 * In the SailPoint model we will have a TaskSchedule that
 * corresponds to both the JobDetail and the Trigger.  It also
 * has some of the characteristics of the JobExecutionContext 
 * providing an execution status, and if possible the result 
 * of the last  execution.
 *
 * OLD DESIGN NOTES:
 *
 * To map the model, we could take two approaches:
 *
 * 1) Trigger defines the JobSchedules, with data assimilated
 *    from the referenced JobDetail
 *
 * 2) JobDetail defines the JobSchedules, with data assimilated
 *    from *one* Trigger
 *
 * The advantage of 1 is that there can in theory be several 
 * Triggers referencing the same JobDetail and we could expose
 * all of them.  But we have no way of creating that situation.
 *
 * The advanage of 2 is that we can save bulk of the 
 * JobSchedule in a JobDetail and not even have a Trigger 
 * object if we don't want to be running the job.  In a 
 * Trigger-driven model, we would have to make sure the
 * Trigger had a cronString or collection of scheduling
 * parameters that effectively disabled it.
 *
 * We'll take approach 2.
 * UPDATE: After writing this I notice that a trigger can
 * be put into a "paused" state which would accomplish what
 * we need in a Trigger-driven model.  Reconsider this...
 * 
 * Not currently supporting any of the search methods defined
 * by PersistenceManager, name lookups should be enough.
 * 
 */

package sailpoint.scheduler;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.*;
import static org.quartz.TriggerKey.*;
import static org.quartz.JobKey.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.JobBuilder.*;
import static org.quartz.CronScheduleBuilder.*;
import static org.quartz.SimpleScheduleBuilder.*;

import org.quartz.impl.matchers.GroupMatcher;
import sailpoint.api.SailPointFactory;
import sailpoint.api.TaskManager;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.persistence.AbstractPersistenceManager;
import sailpoint.search.HybridReflectiveMatcher;
import sailpoint.search.ReflectiveMatcher;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;


public class QuartzPersistenceManager extends AbstractPersistenceManager {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(QuartzPersistenceManager.class);

    /**
     * The attribute in the job detail map that holds the last launch error if
     * there was a problem launching the task.
     */
    static final String ERROR_ATTRIBUTE = TaskSchedule.class.getName() + ".lastLaunchError";

    /**
     * The root of the Quartz API, provided to us through injection.
     */
    static Scheduler _scheduler;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public QuartzPersistenceManager() {
    }

    // sigh, Spring wants the setter to be non-static, there
    // is probably a way to make it use a static but  I just
    // don't have the stomach for it right now - jsl

    public void setScheduler(Scheduler s) {
        _scheduler = s;
    }

    public Scheduler getScheduler() {
        return _scheduler;
    }

    static public Scheduler getGlobalScheduler() {
        return _scheduler;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // PersistenceManager methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Helper to throw an exception if we're asked to do something
     * to a class we don't support.
     */
    private void checkClass(Class cls) throws GeneralException {

        if (_scheduler == null)
            throw new GeneralException("QuartzPersistenceManager: No Scheduler");

        // only one right now
        if (cls != TaskSchedule.class)
            throw new GeneralException("QuartzPersistenceManager: unsupported class " + 
                    cls.getName());
    }

    /**
     * We don't support rich searching in Quartz but we have to at least provide
     * lookup by name for ObjectUtil.checkIllegalRename.
     */
    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, 
                                                                 QueryOptions options, 
                                                                 List<String> properties) 
        throws GeneralException {

        checkClass(cls);

        // we allow a single filter on name
        String filterName = null;

        List<Filter> filters = null;
        if (options != null)
            filters = options.getRestrictions();

        if (filters != null && filters.size() > 0) {
            Filter f = filters.get(0);
            // we may want to allow an AND wrapper as long as there is only
            // one term, this a convention used by some code that generates filters
            if (!(f instanceof LeafFilter))
                throw new GeneralException("Unsupported filter");

            LeafFilter lf = (LeafFilter)f;
            if (lf.getOperation() != Filter.LogicalOperation.EQ)
                throw new GeneralException("Unsupported filter");

            if (!"name".equals(lf.getProperty()))
                throw new GeneralException("Unsupported filter");

            Object value = lf.getValue();
            if (!(value instanceof String))
                throw new GeneralException("Unsupported filter");

            filterName = (String)value;
        }

        if (properties == null || properties.size() != 1)
           throw new GeneralException("Unsupported projection");

        if (!properties.get(0).equals("id"))
            throw new GeneralException("Unsupported projection");

        // we could query for just the id but modifying schedules doesn't 
        // happen that often, just fetch the whole thing
        List<Object[]> result = new ArrayList<Object[]>();
        if (filterName != null) {
            TaskSchedule ts = getObjectByName(TaskSchedule.class, filterName);
            if (ts != null) {
                Object[] row = new Object[1];
                // the id is the same as the name for Quartz objects
                row[0] = filterName;
                result.add(row);
            }
        }
        else {
            try {
                // return all names
                List<String> jobs = getJobNames();

                if (jobs.size() > 0) {
                    Iterator jit = jobs.iterator();
                    while (jit.hasNext()) {
                        Object[] row = new Object[1];
                        row[0] = jit.next();
                        result.add(row);
                    }
                }
            }
            catch (SchedulerException e) {
                throw new GeneralException(e);
            }
        }

        return result.iterator();
    }

    /**
     * Get all job names for all job groups
     *
     * @return
     */
    private List<String> getJobNames() throws SchedulerException
    {
        List<String> jobnames = new ArrayList<String>();

        for(String group: _scheduler.getJobGroupNames()) {
            // enumerate each job in group
            for (JobKey jobKey : _scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))) {
                jobnames.add(jobKey.getName());
            }
        }

        return jobnames;
    }

    /**
     * Retrieve an object by id.
     * Since Quartz doesn't generate unique ids, this is the
     * same as retrieval by name.
     */
    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
    throws GeneralException {

        return getObjectByName(cls, id);
    }

    /**
     * Retrieve an object by name.
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> T getObjectByName(Class<T> cls, String name)
        throws GeneralException {

        TaskSchedule sched = null;

        checkClass(cls);

        try {
            // can we pass a null group name here?
            JobDetail jd = _scheduler.getJobDetail(jobKey(name));
            if (jd != null) {
                sched = new TaskSchedule();
                sched.setName(name);
                sched.setDescription(jd.getDescription());

                // Set the id too so we can consistently use the id for
                // object keys.  This is also used to indiciate that
                // the object is "in" Quartz as opposed to a new
                // object that is being constructed for checkin.
                sched.setId(name);

                // find out if it is currently running
                List executing = _scheduler.getCurrentlyExecutingJobs();
                if (executing != null) {
                    for (int i = 0 ; i < executing.size() ; i++) {
                        JobExecutionContext jc = (JobExecutionContext)executing.get(i);
                        // don't know if we get object equality
                        if (name.equals(jc.getJobDetail().getKey().getName())) {
                            sched.setState(TaskSchedule.State.Executing);
                        }
                    }
                }

                // technically the Trigger can define a Map that overrides
                // the elemnts in this one, we don't allow that now
                JobDataMap jdm = jd.getJobDataMap();

                // sigh, since we've typed TaskSchedule's map, have to convert
                if (jdm != null) {
                    Map<String,Object> args = new HashMap<String,Object>();
                    Iterator it = jdm.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry ent = (Map.Entry)it.next();
                        Object key = ent.getKey();
                        Object value = ent.getValue();

                        // If this is the special "error" attribute, store it.
                        if (ERROR_ATTRIBUTE.equals(key) && (value instanceof String)) {
                            sched.setLastLaunchError((String) value);
                        }
                        else {

                            //figure out if this is an xml object.  If it is, build the object
                            //and return it.
                            // jsl - only do this if the string looks like XML, creating a parser
                            // and throwing an exception are expensive!
                            String xml = (String)value;
                            if (xml != null && xml.length() > 1 && xml.charAt(0) == '<') {
                                try {
                                    value = XMLObjectFactory.getInstance().parseXml(null, (String)value, false);
                                } catch (Exception e) {
                                    // must not have been XML
                                    value = ent.getValue();
                                }
                            }

                            if (key != null) {
                                String skey = key.toString();
                                args.put(skey, value);
                            }
                        }
                    }
                    sched.setArguments(args);
                }

                // if you ask to execute a job immediately, Quartz will return
                // one (or more?) SimpleTriggers in addition to the CronTrigger
                // we create, be sure to filter these
                List<Trigger> jdt = (List<Trigger>)_scheduler.getTriggersOfJob(jobKey(name));

                if (jdt.size() > 0) {
                    Trigger trig = null;
                    Date lastExecution = null;
                    Date nextExecution = null;
                    Date nextActualExecution = null;

                    Iterator<Trigger> trgit = jdt.iterator();

                    while (trgit.hasNext()) {
                        trig = trgit.next();
                        //Last is last execution, Next is next time quartz will try,
                        // nextActual is when the task will actually run. (Calculated from resume date)
                        Date last, next, nextActual;

                        if(log.isDebugEnabled())
                        {
                            log.debug("\n******Trigger Debug*****");
                            Date now = new Date();
                            Date nextFireTime = trig.getFireTimeAfter(now);
                            int j = 0;
                            while (nextFireTime != null && j<10) {
                                log.debug(trig.getKey().getName() + ": " + nextFireTime);
                                nextFireTime = trig.getFireTimeAfter(nextFireTime);
                                j++;
                            } 
                            log.debug("******Trigger Debug*****\n");
                        }

                        if (trig instanceof CronTrigger) {
                            CronTrigger cronTrig = (CronTrigger) trig;
                            last = cronTrig.getPreviousFireTime();
                            next = cronTrig.getNextFireTime();

                            sched.addCronExpression(cronTrig.getCronExpression());
                        }
                        else {
                            // always look at the previous fire time, so we can see when
                            // we last did it manually, if this is a SimpleTrigger
                            // we don't appear to have a fire time, but we will
                            // have a start time
                            last = trig.getPreviousFireTime();
                            next = trig.getNextFireTime();
                            if (last == null)
                                last = trig.getStartTime();

                            // If we know we're not executing, but there is a trigger,
                            // can we claim to be in a Ready state?  
                            // !! hmm, not sure if SimpleTriggers hang around
                            // until the task is run again or are cleaned up
                            // immediately.
                        }
                        nextActual = computeNextActualFire(trig, sched);

                        if (last != null &&
                                (lastExecution == null || 
                                        last.compareTo(lastExecution) > 0)) {
                            lastExecution = last;
                        }
                        if (next != null &&
                                (nextExecution == null || 
                                        next.compareTo(nextExecution)< 0)) {
                            nextExecution = next;
                        }
                        if (nextActual != null &&
                                (nextActualExecution == null ||
                                        nextActual.compareTo(nextActualExecution) < 0)) {
                            nextActualExecution = nextActual;
                        }
                    }

                    sched.setLastExecution(lastExecution);
                    

                    if (trig != null) {
                        if(trig.mayFireAgain()) {
                            sched.setNextExecution(nextExecution);
                            sched.setNextActualExecution(nextActualExecution);
                        }

                        // oddly enough, this isn't on the Trigger
                        Trigger.TriggerState state = _scheduler.getTriggerState(triggerKey(trig.getKey().getName()));

                        if (Trigger.TriggerState.NONE == state) {
                            // If the default group didn't have the state, look in the
                            // MANUAL_TRIGGERS group in case the job was kicked off with
                            // triggerJob() rather than scheduleJob().  This can happen when
                            // saving the TaskSchedule if no cron strings are specified, but
                            // the requested state is "Executing".

                            // TODO: Investigate whether or not we still need to worry about this case
                            // state = _scheduler.getTriggerState(triggerKey(trig.getKey().getName(), Scheduler.DEFAULT_MANUAL_TRIGGERS));
                        }

                        if (state == Trigger.TriggerState.PAUSED)
                            sched.setState(TaskSchedule.State.Suspended);
                        else if (state == Trigger.TriggerState.ERROR)
                            sched.setState(TaskSchedule.State.Error);
                    }
                }
            }
        }
        catch (SchedulerException e) {
            // will this throw for "object not found"?  we normally
            // just return null in that case but can't identify that here
            throw new GeneralException(e);
        }

        return (T)sched;
    }

    /**
     * Compute the next date the trigger will actually fire and do work.
     * When a resumeDate is set, the trigger will technically fire, but will see
     * a resume date is set, and not do any work. This will compute the next available
     * fire time after the given resume date
     * @param trig
     * @param schedule
     * @return
     */
    protected Date computeNextActualFire(Trigger trig, TaskSchedule schedule) {
        Date nextActual = null;

        if (trig != null && schedule != null) {
            Date nextExecution = trig.getNextFireTime();
            Date resumeDate = schedule.getResumeDate();
            if (nextExecution != null && resumeDate != null && resumeDate.after(nextExecution)) {
                nextActual = trig.getFireTimeAfter(resumeDate);
            } else {
                //No Resume date set, NextActual == next
                nextActual = nextExecution;
            }
        }
        return nextActual;
    }

    /**
     * There is no locking in Quartz, though I suppose we could add that.
     */
    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id,
                                                        Map<String,Object> options)
        throws GeneralException {

        return getObjectByName(cls, id);
    }

    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name,
                                                          Map<String,Object> options)
        throws GeneralException {

        return getObjectByName(cls, name);
    }

    public <T extends SailPointObject> T lockObject(T object,
                                                    Map<String,Object> options)
        throws GeneralException {

        return object;
    }

    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {
    }

    /**
     * Save an object in the persistent store.
     *
     * If the object has a name but no id, it is assumed to be
     * a new object, and may not replace an existing object.
     * This is intended to prevent accidental overwrites since
     * the user must currently type in a name for the schedule.
     * 
     * This is different than the Hibernate persistence manager,
     * need to rethink this and have them both work the same way.
     * Maybe it would be better to have a "check existing" option
     * on the save?
     *
     * If the object has an id, this is assumed to be an existing
     * object and may be replaced.  
     *
     * If an object has an id and no name, the name and id are assumed
     * to be the same.
     *
     * If an object has an id and name, but they are different, we
     * assume this is a rename.  The object identified by id is
     * removed, and the object identified by the name is created.
     *
     */
    public void saveObject(SailPointObject obj) throws GeneralException {

        checkClass(obj.getClass());

        boolean update = false;

        // can only be this one right now
        TaskSchedule js = (TaskSchedule)obj;

        String id = js.getId();
        String name = js.getName();

        // leave the name for the new object in "name"
        if (id == null) {
            if (name != null) {
                TaskSchedule existing = getObjectById(TaskSchedule.class, name);
                if (existing != null) {
                    throw new GeneralException(Message.error(
                            MessageKeys.QTZ_PERSITENCE_MGR_ERR_DUP_TASK_NAME, name)
                    );
                }
            }
            else {
                // hmm, should we provide an option to auto-generate these?
                // would be convenient when launching one-shot schedules
                throw new GeneralException("Can't save TaskSchedule without a name");
            }
        }
        else if (name == null) {
            name = id;
        }
        else if (!name.equals(id)) {
            TaskSchedule existing = getObjectById(TaskSchedule.class, id);
            if (existing != null) {
                // we might need to be smarter about this if there are running jobs?
                removeObject(existing, false);
                update = true;
            }
        }

        try {
            // Originally we put the scheduler into stanby mode then started
            // it again after saving the job.  I'm not sure why we did that
            // but unconditionally starting the scheduler violates the new
            // taskSchedulerHosts startup option.  There really should be no
            // need to pause the scheduler, if there is then you'll have to 
            // save the current running state to decide if you need to 
            // restart it after we're done.
            //_scheduler.standby();
            Map<String,Object> args = js.getArguments();

            JobDataMap jdMap = new JobDataMap();

            if (args != null) {
                // turn the args into <String,String>
                Map<String,String> stringifiedArgs = new HashMap<String,String>();
                Iterator<Map.Entry<String,Object>> it = args.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry entry = it.next();
                    String key = (String)entry.getKey();
                    Object val = entry.getValue();
                    if ( ( key != null ) && ( val != null ) ) {
                        if(val instanceof AbstractXmlObject) {
                            String xmlVal = XMLObjectFactory.getInstance().toXml(val, false);
                            stringifiedArgs.put(key, xmlVal);
                        } else {
                            stringifiedArgs.put(key, val.toString());
                        }
                    }
                }

                // Store the last launch error in the map, also.
                if (null != js.getLastLaunchError()) {
                    stringifiedArgs.put(ERROR_ATTRIBUTE, js.getLastLaunchError());
                }

                jdMap = new JobDataMap(stringifiedArgs);
            }

            JobDetail jd = newJob(JobAdapter.class)
                    .withIdentity(name)
                    .withDescription(js.getDescription())
                    .setJobData(jdMap)
                    .storeDurably()
                    .build();

            // second arg means "replace"
            _scheduler.addJob(jd, true);
            List<String> cron = js.getCronExpressions();

            List<Trigger> jdt = (List<Trigger>)_scheduler.getTriggersOfJob(jobKey(name));

            // remove all non-cron triggers as well as those that we know to be obsolete
            if (jdt.size() > 0)
            {
                Iterator<Trigger> trgit = jdt.iterator();
                while(trgit.hasNext()) {
                    Trigger tr = trgit.next();
                    if (tr instanceof CronTrigger) {
                        String cronString = ((CronTrigger)tr).getCronExpression();
                        if (!cron.contains(cronString)
                                || !tr.getNextFireTime().equals(js.getNextExecution())) {
                            _scheduler.unscheduleJob(triggerKey(tr.getKey().getName()));
                        }
                    } else {
                        _scheduler.unscheduleJob(triggerKey(tr.getKey().getName()));
                    }
                }
            }

            int i=0;
            if(cron != null){
                //cycle through cron list and add triggers to job.
                for(Iterator cronIter = cron.iterator(); cronIter.hasNext();)
                {
                    //Set the cron trigger's execution date.
                    Date nextExecution = js.getNextExecution();

                    String cronExpr = (String)cronIter.next();

                    if (nextExecution == null)
                    {
                        try
                        {
                            // get the next valid time that matches the cron expression after now
                            nextExecution = (new CronExpression(cronExpr)).getNextValidTimeAfter(new Date());
                        }
                        catch(ParseException pe)
                        {
                            // invalid cron expression, continue to next one
                            log.warn("task schedule not saved. invalid cron expression: " + cronExpr);
                            continue;
                        }
                    }

                    String triggerName = new String(name + " " + i++);

                    CronTrigger t = newTrigger()
                            .withIdentity(triggerName)
                            .forJob(name)
                            .withSchedule(cronSchedule(cronExpr))
                            .startAt(nextExecution)
                            .build();

                    if(log.isDebugEnabled())
                    {
                        log.debug("\n******Trigger Debug*****");
                        Date now = new Date();
                        Date nextFireTime = t.getFireTimeAfter(now);
                        int j = 0;
                        while (nextFireTime != null && j < 10) {
                            log.debug(t.getKey().getName() + ": " + nextFireTime);
                            nextFireTime = t.getFireTimeAfter(nextFireTime);
                            j++;
                        } 
                        log.debug("\n******Trigger Debug*****\n");
                    }

                    Trigger existing = _scheduler.getTrigger(triggerKey(triggerName));
                    if (existing == null)
                        _scheduler.scheduleJob(t);
                    else if (existing instanceof CronTrigger) {
                        CronTrigger et = (CronTrigger)existing;
                        // try to avoid modifying this if we can, it
                        // seems to really confuse things if you immediatly 
                        // try to schedule a simple trigger in response
                        // to a state change request
                        if (!cron.contains(et.getCronExpression())
                                || !existing.getNextFireTime().equals(js.getNextExecution())) {
                            // this will replace an existing one
                            _scheduler.rescheduleJob(triggerKey(name), t);
                        }
                    }
                    else {
                        // shouldn't happen!
                        _scheduler.rescheduleJob(triggerKey(name), t);
                    }
                }
            }

            // now process state requests
            TaskSchedule.State request = js.getNewState();
            if (request != null) {
                switch (request) {
                case Suspended: {
                    // pause the trigger if we have one
                    // I don't think we want this to interrupt the currently
                    // executing job
                    if (cron != null)
                        _scheduler.pauseTrigger(triggerKey(name));
                }
                break;
                case Ready: {
                    // unpause the trigger
                    Trigger.TriggerState qstate = _scheduler.getTriggerState(triggerKey(name));
                    if (qstate == Trigger.TriggerState.PAUSED)
                        _scheduler.resumeTrigger(triggerKey(name));
                }
                break;
                case Executing: {
                    // run the job immediately
                    // asking for a volatile trigger keeps it out of the
                    // database so we don't confuse getObject()
                    // NOTE: asking for a Volatile trigger seemed to 
                    // be right, but we never acdtually triggered the job, 
                    // just left a SimpleTrigger behind?
                    // odd...
                    boolean theHardWay = false;
                    if (theHardWay) {
                        String tname = "T_" + UUID.randomUUID().toString();
                        Trigger t = newTrigger()
                                .withIdentity(tname)
                                .withSchedule(simpleSchedule()
                                        .withMisfireHandlingInstructionFireNow())
                                .build();

                        _scheduler.scheduleJob(t);
                    }
                    else {
                        // these never worked, try scheduling manually?
                        //_scheduler.triggerJobWithVolatileTrigger(name, GROUP);
                        _scheduler.triggerJob(jobKey(name));
                    }
                }
                break;
                case Terminated: {
                    // attempt to stop the currently executing job
                    _scheduler.interrupt(jobKey(name));
                }
                break;
                }
            }            
        }
        catch (SchedulerException e) {
            throw new GeneralException(e);
        }

        // NOTE: Don't audit saves of one-shot schedule objects
        // used to launch an immediate task.
        if (!TaskManager.IMMEDIATE_SCHEDULE.equalsIgnoreCase(js.getDescription())) {
            if (update)
                Auditor.log(AuditEvent.ActionUpdate, obj);
            else
                Auditor.log(AuditEvent.ActionCreate, obj);
        }
    }

    public void importObject(SailPointObject obj) 
    throws GeneralException {

        // Delete an existing schedule first so that we don't fail.
        // Attempting to save a schedule that has already been saved throws
        // an exception.
        SailPointObject existing =
            getObjectByName(TaskSchedule.class, obj.getName());
        if (null != existing) {
            removeObject(existing);
        }

        saveObject(obj);
    }

    /**
     * Remove an object from the persistent store.
     * Remove the JobDetail and all associated Triggers.
     */
    public void removeObject(SailPointObject obj)
	throws GeneralException {

	removeObject(obj, true);
    }

    private void removeObject(SailPointObject obj, boolean audit)
    throws GeneralException {

        checkClass(obj.getClass());

        try {
            List<Trigger> jdt = (List<Trigger>)_scheduler.getTriggersOfJob(jobKey(obj.getName()));
            if (jdt.size() > 0) {
                Iterator<Trigger> trigit = jdt.iterator();
                while(trigit.hasNext()) {
                    Trigger t = trigit.next();
                    _scheduler.unscheduleJob(triggerKey(t.getKey().getName(), t.getKey().getGroup()));
                }
            }

            _scheduler.deleteJob(jobKey(obj.getName()));

	    if (audit)
		Auditor.log(AuditEvent.ActionDelete, obj);

        }
        catch (SchedulerException e) {
            throw new GeneralException(e);
        }
    }


    /**
     * Return all objects of a given class.
     * Not sure we want this interface long term, especially for things
     * like the audit log, but convenient for now.  Might be    
     * better to return an Iterator or fake up a ResultSet.
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
    throws GeneralException {

        List<T> list = null;

        checkClass(cls);

        try {
            List<String> jobs = getJobNames();
            if (jobs.size() > 0) {
                Iterator<String> jit = jobs.iterator();
                while(jit.hasNext()) {
                    String jobName = jit.next();
                    T js = (T)getObjectByName(TaskSchedule.class, jobName);
                    if (js != null) {
                        if (list == null)
                            list = new ArrayList<T>();
                        list.add(js);
                    }
                }
            }
        }
        catch (SchedulerException e) {
            throw new GeneralException(e);
        }

        return list;
    }

    /**
     * Don't be fooled ... we're not iterating on the underlying datastore,
     * we're just returning an iterator over the results from getObjects().
     */
    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException {
        
        List<T> objs = getObjects(cls, options);
        return (null != objs) ? objs.iterator() : new ArrayList<T>().iterator();
    }

    /**
     * The underlying datastore (ie - quartz) can't perform the filtering that
     * we need, so we'll apply brute-force filtering using the ReflectiveMatcher
     * if any restrictions are specified in the options map.
     */
    @SuppressWarnings("unchecked")
    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options)
    throws GeneralException
    {
        List<TaskSchedule> schedules = null;

        // NOTE: InternalSession is now implementing getObject() as a call to 
        // to getObjects() with a condition on "id" and "name".  This ends up here now
        // rather than getObjectByName.

        String id = (options != null) ? options.isIdentitySearch() : null;
        if (cls == TaskSchedule.class && id != null) {
            TaskSchedule s = getObjectByName(TaskSchedule.class, id);
            if (s != null) {
                schedules = new ArrayList<TaskSchedule>();
                schedules.add(s);
            }
        }
        else {
            schedules = (List<TaskSchedule>)getObjects(cls);

            if ( ( schedules != null ) && ( options != null ) ) { 

                // Apply the filters to the returned list.  We can't do this in
                // quartz, so we have to brute-force it with the ReflectiveMatcher.
                schedules = filterSchedules(schedules, options.getRestrictions());

                List<QueryOptions.Ordering> orderings = options.getOrderings();

                // Must do a manual sort here, since we don't have the ability to
                // specify the order to the quartz api.
                if ( ( schedules.size() > 0 )  && !orderings.isEmpty() ) {

                    // For now, only support sorting by a single column ... I'm
                    // too lazy to make this work now.
                    if (1 != orderings.size()) {
                        throw new GeneralException("Can only sort task schedules by a single property.");
                    }

                    QueryOptions.Ordering ordering = orderings.get(0);
                    
                    Collections.sort(schedules, 
                                     new TaskScheduleComparator(ordering.getColumn(), ordering.isAscending()));

                }

                schedules = handleResultLimits(schedules, options);

            }
        }
        return (List<T>)schedules;
    }

    private <T extends SailPointObject> List<T> handleResultLimits(List<T> objects, QueryOptions ops) {
        List<T> filtered = objects;

        if (ops != null) {
            int start = ops.getFirstRow();
            int limit = ops.getResultLimit();

            if (start < 0 || start > objects.size()) {
                return null;
            }

            if (limit > 0) {
                limit += start;
                if ((limit) > objects.size()) {
                    limit = objects.size();
                }
                filtered = objects.subList(start, limit);
            } else if (start > 0) {
                // Limit <= 0, return all objects from start
                limit = objects.size();
                filtered = objects.subList(start, limit);
            }

        }


        return filtered;
    }

    /**
     * Apply the given filters to the given list of TaskSchedules and returns a
     * filtered list.
     * 
     * @param  schedules  A non-null list of the TaskSchedules to filter.
     * @param  filters    A possibly-null list of filters to apply.
     * 
     * @return A filtered list of the given TaskSchedules.
     */
    private List<TaskSchedule> filterSchedules(List<TaskSchedule> schedules,
                                               List<Filter> filters)
        throws GeneralException {

        if (null != filters) {
            Filter filter = null;
            if (1 == filters.size()) {
                filter = filters.get(0);
            }
            else {
                filter = Filter.and(filters);
            }

            List<TaskSchedule> matched = new ArrayList<TaskSchedule>();
            ReflectiveMatcher matcher = new HybridReflectiveMatcher(SailPointFactory.getCurrentContext(), filter);
            for (TaskSchedule schedule : schedules) {
                if (matcher.matches(schedule)) {
                    matched.add(schedule);
                }
            }
            schedules = matched;
        }

        return schedules;
    }

    /**
     * Return the number of objects defined.
     */
    public int countObjects(Class cls, QueryOptions options)
    throws GeneralException
    {
    	int count = 0;
    	List<TaskSchedule> schedules = null;
    	
    	checkClass(cls);
    	String id = (options != null) ? options.isIdentitySearch() : null;
    	
        if (cls == TaskSchedule.class && id != null) {
            TaskSchedule s = getObjectByName(TaskSchedule.class, id);
            if (s != null) {
                schedules = new ArrayList<TaskSchedule>();
                schedules.add(s);
            }
        } else {
            schedules = (List<TaskSchedule>)getObjects(cls);

            if ((schedules != null) && (options != null)) {
                schedules = filterSchedules(schedules, options.getRestrictions());
            }
        }
        
        if (null != schedules) {
            count = schedules.size();
        }

        return count;
        
    }

    static class TaskScheduleComparator implements Comparator<TaskSchedule> {

        boolean _isAcending = true;
        /** Attribute to sort on */
        String _attr;
        public final static String ATTR_NAME = "name";
        public final static String ATTR_DESCRIPTION = "description";
        public final static String ATTR_TYPE = "type";
        public final static String ATTR_DEFNAME = "defName";
        public final static String ATTR_NEXT_EXECUTION = "nextExecution";
        public final static String ATTR_NEXT_ACTUAL_EXECUTION = "nextActualExecution";
        public final static String ATTR_LAST_EXECUTION = "lastExecution";

        public TaskScheduleComparator() {
            super();
            _attr = ATTR_NAME;
        }

        public TaskScheduleComparator(String attrName, boolean acending) {
            super();
            _isAcending = acending;
            _attr = attrName;
        }

        public int compare(TaskSchedule t1, TaskSchedule t2) { 

            int compareValue = -1;
            if ( ( ATTR_NAME.compareTo(_attr) == 0 ) || 
                    ( ATTR_DESCRIPTION.compareTo(_attr) == 0 ) ||
                    ( ATTR_TYPE.compareTo(_attr) == 0 ) ||
                    ( ATTR_DEFNAME.compareTo(_attr) == 0 ) ) { 

                String s1 = t1.getName();
                String s2 = t2.getName();
                // Both of these deal with the definition
                if ( ( ATTR_DEFNAME.compareTo(_attr) == 0 ) ||
                        ( ATTR_TYPE.compareTo(_attr) == 0 ) ) {
                    s1 = null;
                    TaskDefinition s1Def = t1.getDefinition();
                    if ( s1Def != null ) {
                        if ( ATTR_DEFNAME.compareTo(_attr) == 0 ) {
                            s1 = s1Def.getName();
                        } else
                            if ( ATTR_TYPE.compareTo(_attr) == 0 ) {
                                TaskDefinition.Type type = s1Def.getType();
                                if ( type != null ) 
                                    s1 = type.toString();
                            }
                    }
                    s2 = null;
                    TaskDefinition s2Def = t2.getDefinition();
                    if ( s2Def != null ) {
                        if ( ATTR_DEFNAME.compareTo(_attr) == 0 ) {
                            s2 = s2Def.getName();
                        } else 
                            if ( ATTR_TYPE.compareTo(_attr) == 0 ) {
                                TaskDefinition.Type type = s2Def.getType();
                                if ( type != null ) 
                                    s2 =  type.toString();
                            }
                    }
                } else
                    if ( ATTR_DESCRIPTION.compareTo(_attr) == 0 ) {
                        s1 = t1.getDescription();
                        s2 = t2.getDescription();
                    }

                if ( _isAcending ) {
                    if (s1 != null ) 
                        compareValue = s1.compareTo(s2);
                } else  {
                    if (s2 != null ) 
                        compareValue = s2.compareTo(s1);
                }
            } else 
                if ( ( ATTR_NEXT_EXECUTION.compareTo(_attr) == 0 ) ||
                        ( ATTR_LAST_EXECUTION.compareTo(_attr) == 0) ||
                            (ATTR_NEXT_ACTUAL_EXECUTION.compareTo(_attr) == 0) ) {

                    Date d1 = t1.getNextExecution();
                    Date d2 = t2.getNextExecution();

                    if ( ATTR_LAST_EXECUTION.compareTo(_attr) == 0 ) {
                        d1 = t1.getLastExecution();
                        d2 = t2.getLastExecution();
                    } else if (ATTR_NEXT_ACTUAL_EXECUTION.compareTo(_attr) == 0 ) {
                        d1 = t1.getNextActualExecution();
                        d2 = t2.getNextActualExecution();
                    }

                    if ( _isAcending ) {
                        if ( d1 != null ) {
                            if ( d2 == null) 
                                compareValue = 1;
                            else
                                compareValue = d1.compareTo(d2);
                        }
                    } else  {
                        if ( d2 != null )  {
                            if ( d1 == null ) 
                                compareValue = 1;
                            else 
                                compareValue = d2.compareTo(d1);
                        }
                    }  
                }
            return compareValue;
        }
    }
}
