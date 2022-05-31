/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Environment;
import sailpoint.server.RequestService;
import sailpoint.server.TaskService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Switch Hosts and Server Utils
 * @author rohit.gupta
 *
 */
public class ServiceDefinitionUtil {
	private static final Log serverUtilLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Get All Active Servers
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	public static List getAllActiveServers(SailPointContext context) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"...Start getAllActiveServers.");
		QueryOptions queryOptions = new QueryOptions();
		queryOptions.add(Filter.eq("inactive", false));
		Iterator iter = context.search(Server.class, queryOptions, "name");
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
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"...End getAllActiveServers.");
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"...End getAllActiveServers."+list);
		Util.flushIterator(iter);
		return list;
	}
	/**
	 * Request or Task Service Definition Host Change
	 * @param context
	 * @param hosts
	 * @param UI
	 * @param serviceName
	 * @throws GeneralException 
	 */
	public static void setHostOnServiceDefinition(SailPointContext context,String host, String serviceName) throws GeneralException 
	{
		try 
		{
			LogEnablement.isLogDebugEnabled(serverUtilLogger,"...start setHost.");
			ServiceDefinition serviceDefinition=ObjectUtil.transactionLock(context,ServiceDefinition.class, serviceName);
			if (Util.isNullOrEmpty(host)) 
			{
				host = "global";
				LogEnablement.isLogDebugEnabled(serverUtilLogger,"Set Global Host");
			}
			serviceDefinition.setHosts(host);
			LogEnablement.isLogDebugEnabled(serverUtilLogger,"Set Host:"+host);
			context.saveObject(serviceDefinition);
		} 
		catch (GeneralException ex) 
		{
			LogEnablement.isLogErrorEnabled(serverUtilLogger,"Error setHost:"+ex.getMessage());
			ex.printStackTrace();
			LogEnablement.isLogDebugEnabled(serverUtilLogger,"...end setHost.");
		}
		finally
		{
			context.commitTransaction();
		}
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"...end setHost.");
	}
	/**
	 * Include Services from Servers
	 * @param context
	 * @param listOfServers
	 * @param excludeServices
	 * @throws GeneralException
	 */
	private static void setIncludeServices(SailPointContext context, List<String> listOfServers, List<String> includeServices) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..start setIncludeServices.");
		if(listOfServers!=null && listOfServers.size()>0 && includeServices!=null && includeServices.size()>0)
		{
			try 
			{
				for(String serverName:listOfServers)
				{
					Server server=ObjectUtil.transactionLock(context,Server.class, serverName);
					Attributes attr=server.getAttributes();
					LogEnablement.isLogDebugEnabled(serverUtilLogger,"..attr."+attr);
					if(attr!=null)
					{
						//Overwrite
						attr.put(Server.ATT_INCL_SERVICES, includeServices);
						//Empty
						if(attr.get(Server.ATT_EXCL_SERVICES)!=null)
						{
							attr.put(Server.ATT_EXCL_SERVICES, new ArrayList());
						}
						context.saveObject(server);
					}
				}
			}
			catch (GeneralException ex) 
			{
				LogEnablement.isLogErrorEnabled(serverUtilLogger,"Error setIncludeServices:"+ex.getMessage());
				ex.printStackTrace();
			}
			finally
			{
				context.commitTransaction();
			}
		}
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..end setIncludeServices.");
	}
	/**
	 * Exclude Services from Servers
	 * @param context
	 * @param listOfServers
	 * @param excludeServices
	 * @throws GeneralException
	 */
	private static void setExcludeServices(SailPointContext context, List<String> listOfServers, List excludeServices) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..start setExcludeServices.");
		if(listOfServers!=null && listOfServers.size()>0 && excludeServices!=null && excludeServices.size()>0)
		{
			try 
			{
				for(String serverName:listOfServers)
				{
					Server server=ObjectUtil.transactionLock(context,Server.class, serverName);
					Attributes attr=server.getAttributes();
					LogEnablement.isLogDebugEnabled(serverUtilLogger,"..attr."+attr);
					if(attr!=null)
					{
						//Overwrite
						attr.put(Server.ATT_EXCL_SERVICES, excludeServices);
						//Empty
						if(attr.get(Server.ATT_INCL_SERVICES)!=null)
						{
							attr.put(Server.ATT_INCL_SERVICES, new ArrayList());
						}
						context.saveObject(server);
					}
				}
			}
			catch (GeneralException ex) 
			{
				LogEnablement.isLogErrorEnabled(serverUtilLogger,"Error setExcludeServices:"+ex.getMessage());
				ex.printStackTrace();
			}
			finally
			{
				context.commitTransaction();
			}
		}
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..end setExcludeServices.");
	}
	/**
	 * Switch UI Servers To Request/Task Servers
	 */
	public static void switchUIToTask(SailPointContext context) throws GeneralException 
	{
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Start switchUIToTask.");
		List<String> allInclusionServices = new ArrayList();
		allInclusionServices.add("Request");
		allInclusionServices.add("Task");
		for(String allInclusionService:allInclusionServices)
		{
			setHostOnServiceDefinition(context, "global", allInclusionService);
		}
		List<String> listOfServers=getAllActiveServers(context);
		setIncludeServices(context, listOfServers, allInclusionServices);
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..End switchUIToTask.");
	}
	/**
	 * Stop or Start Services
	 * @param suspend
	 * @param start
	 */
	public static void stopStartServices(boolean suspend, boolean start) 
	{
		LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Start requestTaskServicesStart.");
		try 
		{
			if(suspend|| start)
			{
				Environment env = Environment.getEnvironment();
				TaskService tService=null;
				RequestService rService=null;
				if(suspend)
				{
					//Suspend
					tService = (TaskService) env.getTaskService();
					if (tService!=null && tService.isStarted()) 
					{
						tService.suspend();
						LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Suspend.Task Service");
					}
					rService = (RequestService) env.getRequestService();
					if (rService!=null && rService.isStarted())
					{
						rService.suspend();
						LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Suspend.Request Service");
					}
				}
				if(start)
				{
					// Start
					env = Environment.getEnvironment();
					tService = (TaskService) env.getTaskService();
					if (tService!=null && !tService.isStarted())
					{
						tService.start();
						LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Start.Task Service");
					}
					rService = (RequestService) env.getRequestService();
					if (rService!=null && !rService.isStarted()) 
					{
						rService.start();
						LogEnablement.isLogDebugEnabled(serverUtilLogger,"..Start.Request Service");
					}
				}
			} 
		}
		catch (Exception e) 
		{
			LogEnablement.isLogErrorEnabled(serverUtilLogger,"Error Suspend/Start Services:"+e.getMessage());
			e.printStackTrace();
		}
	}
}
