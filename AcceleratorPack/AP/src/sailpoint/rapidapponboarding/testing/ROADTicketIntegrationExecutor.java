/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.testing;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import sailpoint.api.SailPointContext;
import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.RequestResult;
import sailpoint.object.Attributes;
import sailpoint.object.IntegrationConfig;
import sailpoint.rapidapponboarding.logger.LogEnablement;
/**
 * Smart Services Simulator
 * @author rohit.gupta
 *
 */
public class ROADTicketIntegrationExecutor extends AbstractIntegrationExecutor {
	private static final String TICKETID = "123456789";
	private static Log executorLogger = LogFactory.getLog("rapidapponboarding.rules");
	private Attributes<String, Object> attrs;
	private VelocityEngine velocityEngine;
	private VelocityContext initializeVelocityContext;
	private static boolean VELOCITY_CONTEXT_LOAD = true;
	private static boolean VELOCITY_CONTEXT_UNLOAD = false;
	@Override
	public void configure(SailPointContext context, IntegrationConfig config)
			throws Exception {
		super.configure(context, config);
	}
	@Override
	@SuppressWarnings("unchecked")
	public void configure(Map args) throws Exception {
		if (args instanceof Attributes) {
			this.attrs = (Attributes<String, Object>) args;
		} else if (null != args) {
			this.attrs = new Attributes<String, Object>(args);
		}
		initializeVelocityContext = new VelocityContext();
		velocityEngine = new VelocityEngine();
		velocityEngine.init();
	}
	/**
	 * Get Request Status Config
	 */
	public RequestResult getRequestStatus(String ticketId) throws Exception {
		Map<String, Object> map = (Map<String, Object>) this.attrs
				.get("getRequestStatus");
		Map<String, Object> requestStatusConfig = map;
		Map<String, Object> velocityMap = new HashMap<String, Object>();
		velocityMap.put("config", requestStatusConfig);
		velocityMap.put("ticketId", ticketId);
		loadContext(velocityMap, VELOCITY_CONTEXT_LOAD);
		StringWriter soapMsgWriter = new StringWriter();
		velocityEngine.evaluate(initializeVelocityContext, soapMsgWriter, this
				.getClass().getName(),
				getString(requestStatusConfig, "soapMessage"));
		loadContext(velocityMap, VELOCITY_CONTEXT_UNLOAD);
			LogEnablement.isLogDebugEnabled(executorLogger,"Velocity Evaluated Request Status Config...."
					+ soapMsgWriter);
		RequestResult result = new RequestResult();
		result.setRequestID(ticketId);
		if (ticketId != null && ticketId.equalsIgnoreCase(ROADTicketIntegrationExecutor.TICKETID)) {
			result.setStatus(RequestResult.STATUS_COMMITTED);
		}
		else
		{
			result.setStatus(RequestResult.STATUS_FAILURE);
		}
		if(result.toMap()!=null && result.toMap().toString()!=null)
		LogEnablement.isLogDebugEnabled(executorLogger,result.toMap().toString());
		return result;
	}
	/**
	 * Provision SOAP Message
	 */
	public RequestResult provision(String identity, ProvisioningPlan plan)
			throws Exception {
		RequestResult result = new RequestResult();
		Map<String, Object> map = (Map<String, Object>) this.attrs
				.get("provision");
		Map<String, Object> provisioningConfig = map;
		Map<String, Object> velocityEngineMap = new HashMap<String, Object>();
		velocityEngineMap.put("config", provisioningConfig);
		velocityEngineMap.put("provisioningPlan", plan);
		loadContext(velocityEngineMap, VELOCITY_CONTEXT_LOAD);
		StringWriter soapMsgWriter = new StringWriter();
		velocityEngine.evaluate(initializeVelocityContext, soapMsgWriter, this
				.getClass().getName(),
				getString(provisioningConfig, "soapMessage"));
		loadContext(velocityEngineMap, VELOCITY_CONTEXT_UNLOAD);
			LogEnablement.isLogDebugEnabled(executorLogger,"Velocity Evaluated Provisioning Config...."
					+ soapMsgWriter.toString());
		result.setRequestID(ROADTicketIntegrationExecutor.TICKETID);
		result.setStatus(RequestResult.STATUS_SUCCESS);
		return result;
	}

	/**
	 * Load Velocity Context
	 *
	 * @param velocityVariables
	 * @param load
	 */
	private void loadContext(Map<String, Object> velocityVariables, boolean load) {
		Iterator<String> it = velocityVariables.keySet().iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (load) {
				initializeVelocityContext.put(key, velocityVariables.get(key));
			} else {
				initializeVelocityContext.remove(key);
			}
		}
	}

	/**
	 * Get Integration config Key entries
	 * 
	 * IIQSR-241 (IIQSR-288): The AbstractIntegrationExecutor parent class added a getString method that
	 * is protected.  We have this method in other versions of rapid-ss and need it here also.
	 * We probably can use the parent class management of the integration configuration
	 * and get rid of this method completely, but that needs to be looked at in a separate etn.
	 *  
	 * @param map
	 * @param key
	 * @return
	 */
	protected String getString(Map<String, Object> map, String key) {
	    String value = null;
	    if (map != null) {
	        Object o = map.get(key);
	        if (o != null)
	            value = o.toString();
	    }
	    if (null == value) {
	        value = this.attrs.getString(key);
	    }
	    return value;
	}
}
