/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.Iterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.integration.RequestResult;
import sailpoint.integration.hpservicemanager.HPServiceManagerIntegrationExecutor;
import sailpoint.integration.remedy.RemedyIntegrationExecutor;
import sailpoint.integration.servicenow.ServiceNowIntegrationExecutor;
import sailpoint.object.Filter;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Workflow;
import sailpoint.provisioning.IntegrationConfigFinder;
import sailpoint.rapidapponboarding.testing.ROADTicketIntegrationExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Ticket Status
 * @author rohit.gupta
 *
 */
public class CheckTicketStatusExecutor {
	private static final String SERVICENOWEXEC = "ServiceNowIntegrationExecutor";
	private static final String HPEXEC = "HPServiceManagerIntegrationExecutor";
	private static final String REMEDYEXEC = "RemedyIntegrationExecutor";
	private static final String ROADEXEC = "ROADTicketIntegrationExecutor";
	private static Log ticketStatuslogger = LogFactory
			.getLog("rapidapponboarding.rules");
	/**
	 * Interrogate to see if system has multiple items
	 * 
	 * @param context
	 * @param workflow
	 * @param key
	 * @return
	 * @throws GeneralException
	 */
	public static boolean multipleIntegrationConfigs(SailPointContext context,
			Workflow workflow, String key) throws GeneralException {
		boolean result = true;
		if (context.countObjects(IntegrationConfig.class, null) > 1) {
			result = true;
		} else if (workflow != null) {
			QueryOptions qo = new QueryOptions();
			qo.add(Filter.notnull("name"));
			Iterator configIt = context.search(IntegrationConfig.class, qo,
					"name");
			if (configIt != null && key != null) {
				while (configIt.hasNext()) {
					Object[] itName = (Object[]) configIt.next();
					if (itName != null && itName.length == 1) {
						workflow.put(key, (String) itName[0]);
					}
				}
				Util.flushIterator(configIt);
			} else {
				result = true;
			}
			result = false;
		}
		return result;
	}
	/**
	 * Get Ticket Status from Executor
	 * 
	 * @param context
	 * @param ticketID
	 * @param integrationConfigName
	 * @return
	 * @throws GeneralException
	 */
	public static String getStatusFromTicketIntegration(
			SailPointContext context, String ticketID,
			String integrationConfigName) throws GeneralException {
		String executorName = null;
		if (integrationConfigName != null) {
			IntegrationConfig integrationConfig = context.getObjectByName(
					IntegrationConfig.class, integrationConfigName);
			executorName = integrationConfig.getExecutor();
			if (integrationConfig != null) {
				context.decache(integrationConfig);
			}
		}
		return getStatusFromTicketIntegration(context, ticketID,
				integrationConfigName, executorName);
	}
	/**
	 * Get Ticket Status from Integration
	 * 
	 * @param context
	 * @param ticketID
	 * @param integrationConfigName
	 * @param executorName
	 * @return
	 */
	public static String getStatusFromTicketIntegration(
			SailPointContext context, String ticketID,
			String integrationConfigName, String executorName) {
		String requestStatus = "Ticket Polling Failed";
		if (ticketID != null && ticketID.length() >= 0
				&& integrationConfigName != null
				&& integrationConfigName.length() >= 0 && executorName != null
				&& executorName.length() >= 0) {
			IntegrationConfigFinder icfinder = new IntegrationConfigFinder(
					context);
			try {
				IntegrationConfig ic = icfinder.getIntegrationConfig(
						integrationConfigName, "");
				RequestResult requestResult = null;
				ServiceNowIntegrationExecutor snowExecutor = null;
				if (executorName
						.contains(CheckTicketStatusExecutor.SERVICENOWEXEC)) {
					snowExecutor = new ServiceNowIntegrationExecutor();
					snowExecutor.configure(context, ic);
					requestResult = snowExecutor.getRequestStatus(ticketID);
				}
				RemedyIntegrationExecutor remedyExecutor = null;
				if (executorName.contains(CheckTicketStatusExecutor.REMEDYEXEC)) {
					remedyExecutor = new RemedyIntegrationExecutor();
					remedyExecutor.configure(context, ic);
					requestResult = remedyExecutor.getRequestStatus(ticketID);
				}
				HPServiceManagerIntegrationExecutor hPServiceManagerIntegrationExecutor = null;
				if (executorName.contains(CheckTicketStatusExecutor.HPEXEC)) {
					hPServiceManagerIntegrationExecutor = new HPServiceManagerIntegrationExecutor();
					hPServiceManagerIntegrationExecutor.configure(context, ic);
					requestResult = hPServiceManagerIntegrationExecutor
							.getRequestStatus(ticketID);
				}
				ROADTicketIntegrationExecutor rOADTicketIntegrationExecutor = null;
				if (executorName.contains(CheckTicketStatusExecutor.ROADEXEC)) {
					rOADTicketIntegrationExecutor = new ROADTicketIntegrationExecutor();
					rOADTicketIntegrationExecutor.configure(context, ic);
					requestResult = rOADTicketIntegrationExecutor
							.getRequestStatus(ticketID);
				}
				if (requestResult != null) {
					requestStatus = requestResult.getStatus();
				}
			} catch (GeneralException e) {
				requestStatus = "IIQ threw an exception while getting IntegrationConfig "
						+ e.getMessage();
			} catch (Exception e) {
				requestStatus = "Exception received from Ticketing System while fetching status of the request "
						+ e.getMessage();
			}
		}
		return requestStatus;
	}
}
