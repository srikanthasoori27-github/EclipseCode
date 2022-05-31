/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.Server;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Util for Tasks and TaskSchedules
 * @author rohit.gupta
 *
 */
public class TaskUtil
{
	private static final Log taskUtilLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final String OPERATIONSLAUNCH = "Operations Launch Emergency Action"; 
	/**
	 * Get All Active Tasks
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllPendingTasks(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...Start getAllPendingTasks.");
		QueryOptions queryOptions = new QueryOptions();
		queryOptions.add(Filter.isnull("completed"));
		Iterator iter = context.search(TaskResult.class, queryOptions, "name");
		List list = new ArrayList();
		String name=null;
		if (iter != null && iter.hasNext())
		{
			while (iter.hasNext()) 
			{
				Object[] item = (Object[]) iter.next();
				if(item!=null && item.length==1)
				{
					name = (String) item[0];	
					list.add(name);
				}
			}
		}
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...End getAllPendingTasks.");
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...End getAllPendingTasks."+list);
		Util.flushIterator(iter);
		return list;
	}
	/**
	 * Terminate Pedning Tasks
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static void terminatePendingTasks(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...Start terminatePendingTasks.");
		List<String> terminateTasks=getAllPendingTasks( context);
		if(terminateTasks!=null && terminateTasks.size()>0)
		{
			for(String task:terminateTasks)
			{
				TaskManager tm = new TaskManager(context);
				TaskResult result=context.getObjectByName(TaskResult.class, task);
				if(result!=null)
				{
					LogEnablement.isLogDebugEnabled(taskUtilLogger,".. terminatePendingTasks..."+result.getName());
					TaskItemDefinition.Type type =result.getType();
					if(type!=null)
					{
						LogEnablement.isLogDebugEnabled(taskUtilLogger,".. . Type..."+type.toString());
					}
					if(type!=null && (TaskItemDefinition.Type.Workflow ==type || TaskItemDefinition.Type.LCM == type ) )
					{
						LogEnablement.isLogDebugEnabled(taskUtilLogger,"...Skip Termination of Task Type Workflow");
					}
					else if(result.getName()!=null && result.getName().contains(TaskUtil.OPERATIONSLAUNCH))
					{
						LogEnablement.isLogDebugEnabled(taskUtilLogger,"...Skip Termination of Task Emergency");
					}
					else
					{
						LogEnablement.isLogDebugEnabled(taskUtilLogger,"... Termination of Task Emergency .."+result.getName());
						tm.terminate(result);
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...End terminatePendingTasks.");
	}
	/**
	 * Get All Task Schedules
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List<TaskSchedule> getAllTaskSchedules(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...Start getAllTaskSchedules.");
		QueryOptions queryOptions = new QueryOptions();
		Iterator<TaskSchedule> schedules = context.search(TaskSchedule.class, queryOptions);
		List list = new ArrayList();
		while (schedules.hasNext()) 
		{
			TaskSchedule tSd =  schedules.next();
			list.add(tSd);
		}
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...End getAllTaskSchedules.");
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"...End getAllTaskSchedules."+list);
		Util.flushIterator(schedules);
		return list;
	}
	/**
	 * Post Phone All Task Schedules
	 * @param context
	 * @param date
	 * @throws GeneralException
	 */
	public static void postPhoneAllTaskSchedules(SailPointContext context, Date date) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"..start postPhoneAllTaskSchedules.");
		List<TaskSchedule> listOfTaskScheds=getAllTaskSchedules(context);
		int count = 0;
	    int commitLimit = 100;
	 	if(listOfTaskScheds!=null && listOfTaskScheds.size()>0 && date!=null)
		{
			try 
			{
				for(TaskSchedule taskSchedule:listOfTaskScheds)
				{
					List existingCronExp=taskSchedule.getCronExpressions();
					LogEnablement.isLogDebugEnabled(taskUtilLogger,"..existingCronExps..."+existingCronExp);
					if(existingCronExp!=null && existingCronExp.size()>0)
					{
						taskSchedule.setResumeDate(date);
						context.saveObject(taskSchedule);
						count=count+1;
					}
					if ((count % commitLimit) == 0) {
			        	context.commitTransaction();
			        }
				}
			}
			catch (GeneralException ex) 
			{
				LogEnablement.isLogErrorEnabled(taskUtilLogger,"Error postPhoneAllTaskSchedules:"+ex.getMessage());
				ex.printStackTrace();
			}
			finally
	  		{
	  		//Final Commit
	  		context.commitTransaction();
	  		}
		}
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"..end postPhoneAllTaskSchedules.");
	}
	/**
	 * Set Cron Expression Task Schedule
	 * @param context
	 * @param taskScheduleName
	 * @param cronExpression
	 * @throws GeneralException
	 */
	public static void setTaskScheduleCronExpression(SailPointContext context, String taskScheduleName, String cronExpression) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"..start setTaskScheduleCronExpression.");
		List<TaskSchedule> listOfTaskScheds=getAllTaskSchedules(context);
		if(taskScheduleName!=null)
		{
			try 
			{
				TaskSchedule taskSchedule=ObjectUtil.transactionLock(context,TaskSchedule.class, taskScheduleName);
				List cronExpressionList = new ArrayList();
				cronExpressionList.add(cronExpression);
				taskSchedule.setCronExpressions(cronExpressionList);
				context.saveObject(taskSchedule);
			}
			catch (GeneralException ex) 
			{
				LogEnablement.isLogErrorEnabled(taskUtilLogger,"Error setTaskScheduleCronExpression:"+ex.getMessage());
				ex.printStackTrace();
			}
			finally
			{
				context.commitTransaction();
			}
		}
		LogEnablement.isLogDebugEnabled(taskUtilLogger,"..end postPhoneAllTaskSchesetTaskScheduleCronExpressiondules.");
	}
}
