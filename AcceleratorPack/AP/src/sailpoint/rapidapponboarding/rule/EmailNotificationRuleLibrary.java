/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Dynamic Email Content
 * @author rohit.gupta
 *
 */
public class EmailNotificationRuleLibrary {
	private static Log emailNotificationLogger = LogFactory.getLog("rapidapponboarding.rules");
	private static final String CUSTOMEMAILNAME = "Custom-Framework-EmailTextMapping";
	private static final String CCEMAILWORKGROUP = "apCCEmailWorkgroup";
	private static final String CCEMAILWORKGROUPRULE = "apCCEmailRule";
	private static Custom customEmail = null;

    /**
     * Inject Application Attributes for Velocity Evaluation
     *
     * @param context
     * @param identityName
     * @param appName
     * @param textMap
     * @throws GeneralException
     */
    public static void injectAttributesFromNonAuthoritativeApplications(SailPointContext context, String identityName,
            String appName, Map textMap) throws GeneralException {
        injectAttributesFromNonAuthoritativeApplications(context, identityName, appName, textMap, null);
    }

    /**
     * Inject Application Attributes for Velocity Evaluation. This method
     * receives the specific native identities to handle.
     *
     * @param context
     * @param identityName
     * @param appName
     * @param textMap
     * @param nativeIdentities
     * @throws GeneralException
     */
    public static void injectAttributesFromNonAuthoritativeApplications(SailPointContext context, String identityName,
            String appName, Map textMap, List nativeIdentities) throws GeneralException {
        LogEnablement.isLogDebugEnabled(emailNotificationLogger,
                "Start injectAttributesFromNonAuthoritativeApplications..");
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "identityName.." + identityName);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "appName.." + appName);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "textMap.." + textMap);
        if (identityName != null && appName != null && textMap != null) {
            IdentityService idService = new IdentityService(context);
            Identity identity = context.getObjectByName(Identity.class, identityName);
            Application application = context.getObjectByName(Application.class, appName);

            getTextMapValues(idService, identity, application, textMap, nativeIdentities);

            if (application != null) {
                context.decache(application);
            }
            if (identity != null) {
                context.decache(identity);
            }
        }
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "After Application Injection Attributes.." + textMap);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger,
                "End injectAttributesFromNonAuthoritativeApplications..");
    }

    /**
     * Inject Dynamic Authoritative Application Data
     *
     * @param context
     * @param identityName
     * @param reqType
     * @param templateName
     * @param dynamicAppContent
     * @throws GeneralException
     * @throws ParseErrorException
     * @throws MethodInvocationException
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    public static void injectAttributesFromAuthoritativeApplications(SailPointContext context, String identityName,
            String reqType, String templateName, Map dynamicAppContent) throws GeneralException, ParseErrorException,
            MethodInvocationException, ResourceNotFoundException, IOException {

        injectAttributesFromAuthoritativeApplications(context, identityName, reqType, templateName, dynamicAppContent, null);
    }

    /**
     * Inject Dynamic Authoritative Application Data. This method
     * receives the specific native identities to handle.
     *
     * @param context
     * @param identityName
     * @param reqType
     * @param templateName
     * @param dynamicAppContent
     * @param nativeIdentities
     * @throws GeneralException
     * @throws ParseErrorException
     * @throws MethodInvocationException
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    public static void injectAttributesFromAuthoritativeApplications(SailPointContext context, String identityName,
            String reqType, String templateName, Map dynamicAppContent, List nativeIdentities) throws GeneralException, ParseErrorException,
            MethodInvocationException, ResourceNotFoundException, IOException {
        LogEnablement.isLogDebugEnabled(emailNotificationLogger,
                "Start injectAttributesFromAuthoritativeApplications..");
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "reqType.." + reqType);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "templateName.." + templateName);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "identityName.." + identityName);
        ArrayList<String> listOfAuthAppNames = (ArrayList) ROADUtil.getAuthoritativeApplicationNames(context);
        if (listOfAuthAppNames != null && identityName != null) {
            IdentityService idService = new IdentityService(context);
            Identity identity = context.getObjectByName(Identity.class, identityName);
            for (String appName : listOfAuthAppNames) {
                // Get Static Text - Operation "None" for Authoritative
                // Applications
                String staticText = EmailNotificationRuleLibrary.getStaticAppText(context, reqType, templateName,
                        appName, "None");
                if (staticText != null) {
                    Map textMap = new HashMap();
                    Application application = context.getObjectByName(Application.class, appName);

                    getTextMapValues(idService, identity, application, textMap, nativeIdentities);

                    String dynamicText = getDynamicContent(textMap, staticText);
                    if (dynamicText != null) {
                        dynamicAppContent.put(appName, dynamicText);
                    }
                    if (application != null) {
                        context.decache(application);
                    }
                }
            }
            if (identity != null) {
                context.decache(identity);
            }
        }
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "dynamicAppContent.." + dynamicAppContent);
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "End injectAttributesFromAuthoritativeApplications..");
    }

	/**
	 * Populates the map that contains the linked account attributes to be
	 * consumed as variables in the email notification.
	 *
	 * @param idService
	 * @param identity
	 * @param application
	 * @param nativeIdentities
	 * @param textMap
	 * @throws GeneralException
	 */
    private synchronized static void getTextMapValues(IdentityService idService, Identity identity,
            Application application, Map textMap, List<String> nativeIdentities) throws GeneralException {
        LogEnablement.isLogDebugEnabled(emailNotificationLogger,
                "Start getTextMapValues..");
        boolean latestValuesFromPrimary = false;

        for (Link link : Util.iterate(idService.getLinks(identity, application))) {
            if (nativeIdentities != null && !nativeIdentities.contains(link.getNativeIdentity())) {
                continue;
            }
            Attributes linkAttributes = link.getAttributes();

            if (linkAttributes != null && !linkAttributes.isEmpty()) {
                String privilegedAccountAtt = linkAttributes.getString("IIQPrivileged");
                boolean privilegedAccount = false;
                if (!Util.isNullOrEmpty(privilegedAccountAtt)) {
                    privilegedAccount = Boolean.valueOf(privilegedAccountAtt);
                }
                // IIQTC-330: Primary accounts have priority over privileged
                // accounts, which means primary accounts will overwrite
                // privileged attributes but not the other way.
                if (!privilegedAccount || !latestValuesFromPrimary) {
                    List<String> nameKeys = linkAttributes.getKeys();
                    for (String nameKey : Util.iterate(nameKeys)) {
                        if (linkAttributes.get(nameKey) != null && linkAttributes.get(nameKey) instanceof String) {
                            // Merge Multiple Links/Application Data
                            textMap.put(nameKey, linkAttributes.get(nameKey));
                        }
                    }
                    if (!privilegedAccount) {
                        latestValuesFromPrimary = true;
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(emailNotificationLogger, "End getTextMapValues..");
    }

	/**
	 * Get Static Application Email Instructions
	 * @param reqType
	 * @param templateName
	 * @param appName
	 * @param operationName
	 * @return
	 * @throws GeneralException 
	 */
	public static String getStaticAppText(SailPointContext context, String reqType, String templateName, String appName, String operationName) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start getStaticAppText..");
		String appText=null;
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Find Custom Email..");

		customEmail = getCustomEmail(context);

		if(customEmail!=null)
		{
			Attributes customMap=customEmail.getAttributes();
			if(reqType !=null && customMap!=null &&  customMap.containsKey(reqType) && customMap.get(reqType) !=null )
			{
				Map reqTypeMap = (Map) customMap.get(reqType);
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"reqTypeMap.."+reqTypeMap);
				if(templateName!=null && reqTypeMap!=null && reqTypeMap.containsKey(templateName) && reqTypeMap.get(templateName)!=null)
				{
					Map templateMap=(Map) reqTypeMap.get(templateName);
					if(appName!=null && templateMap!=null && templateMap.containsKey(appName) && templateMap.get(appName)!=null)
					{
						Map appMap = (Map) templateMap.get(appName);
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"appMap.."+appMap);
						if(operationName!=null && appMap!=null && appMap.containsKey(operationName) && appMap.get(operationName)!=null)
						{
							appText = (String) appMap.get(operationName);
						}
					}
				}
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"End getStaticAppText.."+appText);
				return appText;
			}
		}
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"End getStaticAppText.."+appText);
		return appText;
	}
	/**
	 * Generate Dynamic Email Content, Run Static Content Against Velocity
	 * Static Content May Contain Velocity Variables (Provisioning Policy Fields)
	 * @param mapAttributeRequestAttrs
	 * @param staticText
	 * @return
	 * @throws IOException 
	 * @throws ResourceNotFoundException 
	 * @throws MethodInvocationException 
	 * @throws ParseErrorException 
	 */
	public static String getDynamicContent(Map<String,String> mapAttributeRequestAttrs, String staticText) throws ParseErrorException, MethodInvocationException, ResourceNotFoundException, IOException
	{
		StringWriter body = new StringWriter();
		String dynamicContent=null;
		VelocityContext velocityContext = new VelocityContext();
		if(staticText!=null)
		{
			for(String vkey : mapAttributeRequestAttrs.keySet())
			{
				velocityContext.put(vkey, mapAttributeRequestAttrs.get(vkey));
			}
			if(mapAttributeRequestAttrs!=null && mapAttributeRequestAttrs.size()>0)
			{
				// Tag to be used in log messages
				String tag = "None";
				Velocity.evaluate(velocityContext, body, tag, (String)staticText);
				dynamicContent=body.toString();
			}
		}
		return dynamicContent;
	}
	/**
	 * Get Dynamic Email Settings
	 * 
	 * @return customEmail
	 * @throws GeneralException
	 */
	synchronized static Custom getCustomEmail(SailPointContext context)
			throws GeneralException {
		// Adding second check to avoid re-initialization when the multiple
		// threads enter into the above if condition and waiting for this to be
		// initialized
		if (null == customEmail || null == customEmail.getAttributes()) {
			customEmail = context.getObjectByName(Custom.class,EmailNotificationRuleLibrary.CUSTOMEMAILNAME);
		} else {
            Date dbModified = Servicer.getModificationDate(context, customEmail);
            if (Util.nullSafeCompareTo(dbModified, customEmail.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(emailNotificationLogger,"...Returning updated customEmail object");
                customEmail = context.getObjectByName(Custom.class, EmailNotificationRuleLibrary.CUSTOMEMAILNAME);
            } else {
                LogEnablement.isLogDebugEnabled(emailNotificationLogger,"...Returning previously initialized customEmail object");
            }
        }
		return customEmail;
	}
	/**
	 * Force Load Email Notification Custom Artifact
	 * 
	 * @throws GeneralException
	 */
	public synchronized static void forceLoad(SailPointContext context)throws GeneralException 
	{
		customEmail = context.getObjectByName(Custom.class,EmailNotificationRuleLibrary.CUSTOMEMAILNAME);
	}
	/**
	 * Get Contract Manager Email
	 * @param context
	 * @param identityName
	 * @param enableHrGroup
	 * @param requestType
	 * @param plan
	 * @param project
	 * @return
	 * @throws GeneralException
	 */
	public static String ccToContractorManager(SailPointContext context,String identityName, boolean enableHrGroup, String requestType, ProvisioningPlan plan, ProvisioningProject project) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start ccToContractorManager.."+identityName);
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start ccToContractorManager..enableHrGroup.."+enableHrGroup);
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start ccToContractorManager..requestType.."+requestType);
		String ctrManagerEmail="";
		if(identityName!=null)
		{
			String identityTypeEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrIdentityTypeEnabled(context);
			boolean identityTypeEnab=false;
			if(identityTypeEnabled!=null && identityTypeEnabled.length()>0 && identityTypeEnabled.equalsIgnoreCase("TRUE"))
			{
				identityTypeEnab=true;
			}
			if(identityTypeEnab)
			{
				LogEnablement.isLogDebugEnabled(emailNotificationLogger," identityTypeEnab.."+identityTypeEnab);
				boolean identityCtrMgrEnab=false;
				String identityCtrMgrEnabled = ObjectConfigAttributesRuleLibrary.extendedAttrCtrManagerEnabled(context);
				if(identityCtrMgrEnabled!=null && identityCtrMgrEnabled.length()>0 && identityTypeEnabled.equalsIgnoreCase("TRUE"))
				{
					identityCtrMgrEnab=true;
				}
				if(identityCtrMgrEnab)
				{
					LogEnablement.isLogDebugEnabled(emailNotificationLogger," identityCtrMgrEnabled.."+identityCtrMgrEnabled);
					QueryOptions qo = new QueryOptions();
					qo.addFilter(Filter.eq("name", identityName));
					String ctrMgr = "";
					Iterator idResult = context.search(Identity.class, qo, "ctrMgr"); 
					if(idResult != null && idResult.hasNext()) 
					{
						Object[] obj = (Object[]) idResult.next();
						if(obj != null && obj.length == 1) 
						{
							ctrMgr = (String) obj[0];
							LogEnablement.isLogDebugEnabled(emailNotificationLogger," ctrMgr.."+ctrMgr);
						}
						Util.flushIterator(idResult);
					}
					if(ctrMgr!=null)
					{
						qo = new QueryOptions();
						qo.addFilter(Filter.eq("name", ctrMgr));
						Iterator idResultEmail = context.search(Identity.class, qo, "email"); 
						if(idResultEmail != null && idResultEmail.hasNext()) 
						{
							Object[] obj = (Object[]) idResultEmail.next();
							if(obj != null && obj.length == 1) 
							{
								ctrManagerEmail = (String) obj[0];
								LogEnablement.isLogDebugEnabled(emailNotificationLogger," Found ctrManagerEmail.."+ctrManagerEmail);
							}
						}
						Util.flushIterator(idResultEmail);
					}
				}
			}
		}
		String ccEmail=null;
		if(enableHrGroup)
		{
			ccEmail=getCCEmailForAllWorkflows(context,identityName,requestType, plan,project);
		}
		else
		{
			//Used for Mitigation Expiration - Ignore HR Groups, Use Rule If defined
			ccEmail=getCCEmailForAllWorkflowsFromRule(context,identityName,requestType, plan,project);
		}
		if(ctrManagerEmail!=null && ctrManagerEmail.length()>0  && ccEmail!=null && ccEmail.length()>0)
		{
			//Append CC Email Here - Both Exists
			ctrManagerEmail = ctrManagerEmail+","+ccEmail;
			LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Appended .."+ctrManagerEmail);
		}
		else if(ccEmail!=null && ccEmail.length()>0 && (ctrManagerEmail==null || ctrManagerEmail.length()<=0)) 
		{
			//Only CC Email Exists
			ctrManagerEmail = ccEmail;
			LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Appended .."+ctrManagerEmail);
		}
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"End ccToContractorManager.."+ctrManagerEmail);
		return ctrManagerEmail;
	}
	/**
	 * Get CC Email Recipient from Rule or Workgroup
	 * @param context
	 * @param identityName
	 * @param requestType
	 * @param plan
	 * @param project
	 * @return
	 * @throws GeneralException
	 */
	public static String getCCEmailForAllWorkflows(SailPointContext context, String identityName, String requestType, ProvisioningPlan plan, ProvisioningProject project) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start getCCEmailForAllWorkflows.."+identityName);
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start getCCEmailForAllWorkflows..requestType..."+requestType);
		String email="";
		if(identityName!=null)
		{
			HashMap map = (HashMap) ROADUtil.getCustomGlobalMap(context);
			//Rule Gets Precedence Over Workgroup
			if(map!=null && map.containsKey(EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE))
			{
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"map contains rule->.." +EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE);
				String ruleName=(String) map.get(EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE);
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"ruleName->.." +ruleName);
				if(ruleName!=null && ruleName.length()>0)
				{
					//It can be single or comma separated
					try
					{
						email = (String) ROADUtil.invokePostExtendedRuleNoObjectReferences(context,null,ruleName, null,  requestType, null, null, null, identityName,null, plan,project);
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Email From Rule->.." +email);
						return email;
					}
					catch (Exception ex)
					{
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Error->"+ex.getMessage());
					}
				}
			}
			if (map!=null && map.containsKey(EmailNotificationRuleLibrary.CCEMAILWORKGROUP))
			{
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"map contains rule->.." +EmailNotificationRuleLibrary.CCEMAILWORKGROUP);
				String workGroupName=(String) map.get(EmailNotificationRuleLibrary.CCEMAILWORKGROUP);
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"workGroupName->.." +workGroupName);
				if(workGroupName!=null && workGroupName.length()>0)
				{
					Identity wgIdentity=context.getObjectByName(Identity.class, workGroupName);
					List emails=ObjectUtil.getEffectiveEmails(context, wgIdentity);
					LogEnablement.isLogDebugEnabled(emailNotificationLogger,"emails->.." +emails);
					if(emails!=null && emails.size()>0)
					{
						email=Util.listToCsv(emails);
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"email->.." +email);
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Email From Workgroup->"+email);
						return email;
					}
					if(wgIdentity!=null)
					{
						context.decache(wgIdentity);
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"End getCCEmailForAllWorkflows.." +email);
		return email;
	}
	/**
	 * Get CC Email Recipient from Rule or Workgroup
	 * @param context
	 * @param identityName
	 * @param requestType
	 * @param plan
	 * @param project
	 * @return
	 * @throws GeneralException
	 */
	public static String getCCEmailForAllWorkflowsFromRule(SailPointContext context, String identityName, String requestType, ProvisioningPlan plan, ProvisioningProject project) throws GeneralException
	{
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start getCCEmailForAllWorkflowsFromRule.."+identityName);
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Start getCCEmailForAllWorkflowsFromRule..requestType..."+requestType);
		String email="";
		if(identityName!=null)
		{
			HashMap map = (HashMap) ROADUtil.getCustomGlobalMap(context);
			//Rule Gets Precedence Over Workgroup
			if(map!=null && map.containsKey(EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE))
			{
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"map contains rule->.." +EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE);
				String ruleName=(String) map.get(EmailNotificationRuleLibrary.CCEMAILWORKGROUPRULE);
				LogEnablement.isLogDebugEnabled(emailNotificationLogger,"ruleName->.." +ruleName);
				if(ruleName!=null && ruleName.length()>0)
				{
					//It can be single or comma separated
					try
					{
						email = (String) ROADUtil.invokePostExtendedRuleNoObjectReferences(context,null,ruleName, null,  requestType, null, null, null, identityName,null, plan,project);
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Email From Rule->.." +email);
						return email;
					}
					catch (Exception ex)
					{
						LogEnablement.isLogDebugEnabled(emailNotificationLogger,"Error->"+ex.getMessage());
					}
				}
			}
		}
		LogEnablement.isLogDebugEnabled(emailNotificationLogger,"End getCCEmailForAllWorkflows.." +email);
		return email;
	}
}
