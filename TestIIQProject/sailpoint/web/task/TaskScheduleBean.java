/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.TaskManager;
import sailpoint.object.Scope;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.object.TaskSchedule.State;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class TaskScheduleBean extends BaseTaskBean<TaskSchedule> {

    private static final Log log = LogFactory.getLog(TaskScheduleBean.class);
    
    private String _taskDefinitionId;
    private String _frequency;
    private Date _startDate;
    private boolean _runNow;
    private String _scheduleError;

    @SuppressWarnings("unchecked")
    public TaskScheduleBean() throws GeneralException {
        super();

        // If the Object id is still null, check the schedule form
        // used in schedulePanel.xhtml
        if (getObjectId() == null) {
            String id = super.getRequestOrSessionParameter("scheduleForm:id");
            if (Util.isNotNullOrEmpty(id)) {
                setObjectId(id);
            }
        }

        if(getObjectId()!=null) {
            setObjectId(StringEscapeUtils.unescapeHtml4(getObjectId()));
        }
        setScope(TaskSchedule.class);
        // If this is a NEW request we'll have stuck taskDefId inthe
        // request params
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map request = ctx.getExternalContext().getRequestParameterMap();
        String id = Util.getString((String) request.get("taskDefId"));
        if ( id != null) {
            _taskDefinitionId = id;
        }

    }

    public String getTaskDefinitionId() {
        return _taskDefinitionId;
    }

    public void setTaskId(String id) {
        _taskDefinitionId = id;
    }

    public String getDefinitionName() throws GeneralException {
        String name = "unknown";
        String id = getTaskDefinitionId(); 
        if ( id != null ) {
            TaskDefinition def = 
                getContext().getObjectById(TaskDefinition.class, id);
            if ( def != null ) name = def.getName();
        }
        return name;
    }

    /**
     * Create a new schedule and seed it with the selected definition
     */
    public TaskSchedule createObject() {
        TaskSchedule ts = new TaskSchedule();
        String taskId = getTaskDefinitionId();
        if ( taskId != null ) {

            try {
                TaskDefinition def = 
                    getContext().getObjectById(TaskDefinition.class,
                        taskId);        
                ts.setTaskDefinition(def);

                // Pass the current user in as the "launcher" for
                // display in the task result.  Might want an option
                // to schedule anonymous tasks?
                ts.setLauncher(getLoggedInUserName());

            } catch(Exception e) { 
                log.warn("failed to get definition with id = '" 
                        + taskId + "'");
            }
        }
        return ts;
    }

    private void save(TaskSchedule ts) throws GeneralException {
        List<String> cronStrings = new ArrayList<String>();
        CronString cs = new CronString(getStartDate(), getFrequency());
        ts.setNextExecution(getStartDate());

        // The following variable keeps track of whether the task is being updated
        // or deleted.
        final boolean updateObject;
        
        if(isRunNow())
        {
            if (CronString.FREQ_ONCE.equals(getFrequency())) {
              TaskManager tm = new TaskManager(getContext());        
              tm.setLauncher(getLoggedInUserName());
              TaskDefinition def = ts.getDefinition();
              Map<String, Object> args = ts.getArguments();
              args.put(TaskSchedule.ARG_RESULT_NAME, ts.getName());
              tm.run(def, args);
              updateObject = false;
            } else {
                ts.setNewState(State.Executing);
                updateObject = true;
            }
        } else {
            updateObject = true;
        }

        String generatedCronExpression = cs.toString();
        //System.out.println("Setting Next Execution: " + getStartDate().toString());
        cronStrings.add(generatedCronExpression);
        
        //If this is scheduled to run on the 30th or 29th and will affect february, create a second
        //trigger just for february so that it doesn't get skipped by Quartz.
        Date febDate = isBadFebruaryDay(getStartDate());
        
        if(febDate != null){
            CronString cs2 = new CronString(febDate,CronString.FREQ_ANNUALLY);
            cronStrings.add(cs2.toString());
        }
        
        ts.setCronExpressions(cronStrings);

        try {
            if (updateObject) {
                ObjectUtil.checkIllegalRename(getContext(), ts);
                getContext().saveObject(ts);
            } else {
                getContext().removeObject(ts);
            }

            getContext().commitTransaction();
            cleanSession();
        }
        catch (GeneralException ge) {
            getContext().rollbackTransaction();
            throw ge;
        }
    }
    
    /**Quartz currently has an issue with running tasks in February.
     * If you schedule a monthly task for the 30th or 29th, quartz will skip it in February
     * (unless it's a leap year, then quartz will only skip the 30th's run).  This
     * Method determines if the day is the 30th or 29th so we can handle this accordingly.
     */
    public Date isBadFebruaryDay(Date startDate) throws GeneralException
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
            if((getFrequency().equals(CronString.FREQ_MONTHLY)) ||
                    ((getFrequency().equals(CronString.FREQ_QUARTERLY)) && 
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

    public void setNextExecution(Date date) {        
        setStartDate(date);
    }

    public Date getNextExecution() throws GeneralException {
        Date date = null;
        TaskSchedule ts = (TaskSchedule) getObject();
        if ( ts != null ) {
            date = ts.getNextExecution(); 
        }
        if ( date == null ) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 5);
            date = cal.getTime();
        }
        return date;
    }

    public String getScheduleError() {
    	return _scheduleError;
    }
    
    public void setScheduleError(String scheduleError){
    	_scheduleError = scheduleError;
    }

    public String schedule(){
        final String saveResult;
        
        try {
            TaskSchedule ts = (TaskSchedule)getObject();
            
            /** Check to see if the task name already exists **/
            String scheduleId = ts.getId();
            if(scheduleId == null || scheduleId.equals("")) {
            	TaskSchedule dupe = getContext().getObjectByName(TaskSchedule.class, getObject().getName());
            	if(dupe!=null) {
            		setScheduleError(getMessage(MessageKeys.ERROR_TASK_NAME_EXISTS));
            		return null;
            	}
            }

            TaskDefinition def = ts.getDefinition(getContext());
            
            TaskDefinition.cascadeScopeToObject(def, ts);

            if ( TaskDefinitionBean.isReportDef(def) ) {
                saveResult = "savedReport";
            } else {
                Scope taskScope= ts.getAssignedScope();
                if (taskScope != null) {
                    ts.setArgument(TaskDefinition.ARG_TASK_SCOPE, taskScope.getName());
                }
                saveResult = "savedTask";
            }

            save(ts);
        } catch (GeneralException ge) {
            log.error("Unable to save task schedule due to GeneralException: " + ge.getMessage(), ge);
            setScheduleError(ge.getMessage());
            addMessage(new Message(Message.Type.Error, ge.getMessage()));
            return null;
        }
        
        return saveResult;
    }

    public Date getLastExecution() throws GeneralException {
       
        TaskSchedule ts = (TaskSchedule)getObject();
        if (ts!=null && ts.getLastExecution()!=null){
        	return ts.getLastExecution();
        }
    
        return null;
    }

    public String getFrequency() throws GeneralException {
        if ( _frequency == null ) {
            _frequency = "Monthly";
            TaskSchedule ts = (TaskSchedule)getObject();
            if ( ts != null ) {
                //Get the frequency of the first cron expression only
                String cronString = ts.getCronExpression(0);
                if ( cronString != null ) {
                    CronString cs = new CronString(cronString);
                    _frequency = cs.getFrequency();
                }
            }
        }
        return _frequency;
    }

    public void setFrequency(String frequency) {
        _frequency = frequency;
    }

    public Date getStartDate() {
        return ( _startDate != null ) ? _startDate : new Date();
    }

    public void setStartDate(Date startDate) {
        _startDate = startDate;
    }

    /**
     * @return the _runNow
     */
    public boolean isRunNow() {
        return _runNow;
    }

    /**
     * @param now the _runNow to set
     */
    public void setRunNow(boolean now) {
        _runNow = now;
    }
}
