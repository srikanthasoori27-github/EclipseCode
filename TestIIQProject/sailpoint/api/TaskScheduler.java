/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Provides a high level api for scheduling tasks. Most of this code
 * was copied from the TaskScheduleBean.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class TaskScheduler {

    private SailPointContext context;

    public TaskScheduler(SailPointContext context) {
        this.context = context;
    }

    protected SailPointContext getContext(){
        return context;
    }

    public boolean isNameUnique(String name) throws GeneralException{
        QueryOptions ops = new QueryOptions(Filter.eq("name", name));
        return 0 == getContext().countObjects(TaskSchedule.class, ops);
    }

    public TaskSchedule schedule(TaskDefinition def, String name, String description, String scheduler,
                         String frequency, Date startDate, boolean runNow) throws GeneralException{

         /** Check to see if the task name already exists **/
        if (!isNameUnique(name)){
            throw new GeneralException(MessageKeys.ERROR_TASK_NAME_EXISTS);
        }

        TaskSchedule ts = new TaskSchedule();
        ts.setName(name);
        ts.setDescription(description);
        ts.setTaskDefinition(def);
        ts.setLauncher(scheduler);

        List<String> cronStrings = new ArrayList<String>();
        CronString cs = new CronString(startDate, frequency);
        ts.setNextExecution(startDate);

        if(runNow)
        {
            if (CronString.FREQ_ONCE.equals(frequency)) {
              TaskManager tm = new TaskManager(getContext());
              tm.setLauncher(scheduler);
              Map<String, Object> args = ts.getArguments();
              args.put(TaskSchedule.ARG_RESULT_NAME, ts.getName());
              tm.run(def, args);
            } else {
                ts.setNewState(TaskSchedule.State.Executing);
            }
        }

        String generatedCronExpression = cs.toString();
        cronStrings.add(generatedCronExpression);

        //If this is scheduled to run on the 30th or 29th and will affect february, create a second
        //trigger just for february so that it doesn't get skipped by Quartz.
        Date febDate = isBadFebruaryDay(startDate, frequency);

        if(febDate != null){
            CronString cs2 = new CronString(febDate,CronString.FREQ_ANNUALLY);
            cronStrings.add(cs2.toString());
        }

        ts.setCronExpressions(cronStrings);

        getContext().saveObject(ts);
        getContext().commitTransaction();

        return ts;
    }


     /**Quartz currently has an issue with running tasks in February.
     * If you schedule a monthly task for the 30th or 29th, quartz will skip it in February
     * (unless it's a leap year, then quartz will only skip the 30th's run).  This
     * Method determines if the day is the 30th or 29th so we can handle this accordingly.
     */
    private Date isBadFebruaryDay(Date startDate, String frequency) throws GeneralException
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        //System.out.println("Now Time: " + cal.getTime());
        if(((cal.get(Calendar.DAY_OF_MONTH)>28) && (cal.get(Calendar.DAY_OF_MONTH)<31)) )
        {
            int month = cal.get(Calendar.MONTH);
            //System.out.println("Month: " + month);

            /**If the frequency is monthly, or if it is quarterly and
            * february is involved in the quarter pattern, create an
            * extra annual trigger just for feb.
            * */
            if((frequency.equals(CronString.FREQ_MONTHLY)) ||
                    ((frequency.equals(CronString.FREQ_QUARTERLY)) &&
                            ((month == 1) || //Feb
                             (month == 4) || //May
                             (month == 7) || //Aug
                             (month == 10))))
            {
                //Make the date to be february 28 and return
                cal.set(Calendar.DAY_OF_MONTH, 28);
                cal.set(Calendar.MONTH, Calendar.FEBRUARY);
                //System.out.println(Calendar.FEBRUARY);
                //System.out.println("Feb Time: " + cal.getTime());
                return cal.getTime();
            }
        }
        return null;
    }



}
