/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Workflow;
import sailpoint.service.acceleratorpack.ApplicationOnboardService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import static java.util.stream.Collectors.toSet;

/**
 * Tool class to help with creating emails
 */
public class EmailTools {

    private static Log log = LogFactory.getLog(EmailTools.class);

    private static final String isProvgSuccessful = "isProvgSuccessful";
    private static final String provgStatusMsgs   = "provgStatusMsgs";

    private static final String STR_COMMA = ",";
    private static final String ARG_ID = "id";
    private static final String ARG_COMMENTS = "comments";
    private static final String ARG_REQUESTER_COMMENTS = "requesterComments";

    private static EmailTools INSTANCE = new EmailTools();

    /**
     * Get a singleton instance.  Especially useful for passing to email templates.
     * @return the EmailTools singleton instance
     */
    public static EmailTools instance() {
        return INSTANCE;
    }

    private EmailTools() {}

    /**
     * @return the display name of the given identity.  Return null if not a valid identity,
     * or if identity has no display name.
     */
    public static String getIdentityDisplayName(SailPointContext context, String identityName) throws GeneralException {
        String displayName = null;
        if (Util.isNotNullOrEmpty(identityName)) {
            Identity identity = context.getObjectByName(Identity.class, identityName);
            if (identity != null) {
                displayName = identity.getDisplayName();
                context.decache(identity);
            }
        }
        return displayName;
    }

    /**
     * Get the name of the identity that is the manager (or alternative if no manager) of the identity named by identityName.
     * @param context persistence context
     * @param identityName the identity that we want the manager of
     * @return the name of the manager (or alternative if no manager)
     * @throws GeneralException database exception occurred
     */
    public static String getManagerOrAltIdentityName(SailPointContext context, String identityName) throws GeneralException {
        String mgrName = null;
        Identity mgr = getManagerOrAltIdentity(context, identityName);
        if (mgr != null) {
            mgrName = mgr.getName();
            context.decache(mgr);
        }
        return mgrName;
    }

    /**
     * Get the display name of the identity that is the manager (or alternative if no manager) of the identity named by identityName.
     * @param context persistence context
     * @param identityName the identity that we want the manager of
     * @return the display name of the manager (or alternative if no manager)
     * @throws GeneralException database exception occurred
     */
    public static String getManagerOrAltIdentityDisplayName(SailPointContext context, String identityName) throws GeneralException {
        String mgrDisplayName = null;
        Identity mgr = getManagerOrAltIdentity(context, identityName);
        if (mgr != null) {
            mgrDisplayName = mgr.getDisplayName();
            context.decache(mgr);
        }
        return mgrDisplayName;
    }

    /**
     * Get the identity that is the manager (or alternative if no manager) of the identity named by identityName.
     * The caller is responsible for decache of the returned Identity
     * @param context persistence context
     * @param identityName the identity that we want the manager of
     * @return the display name of the manager (or alternative if no manager)
     * @throws GeneralException database exception occurred
     */
    private static Identity getManagerOrAltIdentity(SailPointContext context, String identityName) throws GeneralException {
        Identity manager = null;
        if (Util.isNotNullOrEmpty(identityName)) {
            Identity identity = context.getObjectByName(Identity.class, identityName);
            if (identity != null) {
                manager = identity.getManager();
                context.decache(identity);
                if (manager == null) {
                    String altManagerName = RapidSetupConfigUtils.getString("email,altManager");
                    if (Util.isNotNullOrEmpty(altManagerName)) {
                        Identity altIdentity = context.getObjectByName(Identity.class, altManagerName);
                        if (altIdentity != null) {
                            manager = altIdentity;
                        }
                    }
                }
            }
        }
        return manager;
    }


    /**
     * Return the content to insert for the specified area of emails.  The content which will be returned
     * is determined by getting name of an EmailTemplate by looking under the "email" map  in
     * the RapidSetup COnfiguration object for a key with the name of emailArea param. The value of
     * that key, if present, is interpreted as an EmailTemplate name.  The body of that
     * EmailTemplate is returned.
     * @param context persistence context
     * @param emailArea the name of the section to retrieve the content for.  Normal expected values
     *                  are: "styleSheet", "headerTemplate", and "footerTemplate".  Although
     *                  additional values can be supported if additional entries are added under the
     *                  "email" map of RapidSetup Configuration.
     * @return the content to insert for the specified area of emails.  Return an empty string
     * if nothing found for the given emailArea.
     * @throws GeneralException unexpected database eception
     */
    public static String getEmailSection(SailPointContext context, String emailArea) throws GeneralException {
        String content = null;
        String templateName = RapidSetupConfigUtils.getString("email," + emailArea);
        if (Util.isNotNullOrEmpty(templateName)) {
            EmailTemplate template = context.getObjectByName(EmailTemplate.class, templateName);
            if (template != null) {
                content = template.getBody();
                context.decache(template);
            }
        }
        if (Util.isNullOrEmpty(content)) {
           content = "";
        }
        return content;
    }


    /**
     * Get Request Id from Provisioning Plan
     * @param plan
     * @return String Request Id of Provisioning Plan
     * @throws GeneralException
     */
    public static String getRequestId(SailPointContext context, ProvisioningPlan plan) throws GeneralException {
        String planIdentityReqId = "";
        if (plan != null){
            planIdentityReqId = plan.getArguments().get("identityRequestId").toString();
            if (Util.isNullOrEmpty(planIdentityReqId)) {
                log.warn("An IdentityRequest id could not be found in the plan");
            }
        }
        return planIdentityReqId;
    }

    /**
     * Provides the status from the Provisioning Result in map with status as
     * boolean and error messages into a Status Messages list, if any! The
     * Status Messages List will be placed into the resulting map if there are
     * errors found in one or more underlying requests from any of the Provisioning plans
     * in the ProvisioningProject.
     * The Status Messages List will be an accumulated list of all error messages
     * found in underlying requests from input plan
     *
     * @param provProject ProvisioningProject
     * @return Map with isProvgSuccessful status as boolean and provgStatusMsgs
     * as error messages into a list, if any!
     * @throws GeneralException
     */
    private static Map getProvisioningStatusMap(SailPointContext context, ProvisioningProject provProject) throws GeneralException {
        Map provStatusMap = new HashMap();
        if (null == provProject || null == provProject.getPlans()) {
            log.debug("Input ProvisioningProject is null or has no ProvisioningPlans");
            return provStatusMap;
        }
        List<String> provStatusMsgs = new ArrayList();
        Map reqStatusMap = null;
        String identityRequest = "";
        //Get the identityRequest from the project or from master plan
        if(provProject.getString("identityRequestId") != null) {
                identityRequest = provProject.getString("identityRequestId");
        }
        else if(provProject.getMasterPlan() != null && provProject.getMasterPlan().getString("identityRequestId") != null) {
                identityRequest = provProject.getMasterPlan().getString("identityRequestId");
        }
        //First check if the IdentityRequest for contains errors
        Map idReqStatusMap = getIdentityRequestStatusMap(context, identityRequest);
        if (null != idReqStatusMap && null != idReqStatusMap.get(provgStatusMsgs)) {
            List<String> errMsgList = (List<String>)idReqStatusMap.get(provgStatusMsgs);
            for(String errStr: errMsgList) {
                provStatusMsgs.add(errStr);
            }
        }
        //check if project contains any error message
        if(provProject.getMessages() != null && provProject.getMessages().size() > 0) {
            Iterator<Message> msgIterator = provProject.getMessages().iterator();
            while(msgIterator.hasNext()) {
                Message msg = (Message) msgIterator.next();
                if(msg.isError()) {
                    provStatusMsgs.add("Error message in ProvisioningProject: " + msg.getMessage());
                }
            }
        }
        //Now, check each plan in the project
        List<ProvisioningPlan> planList = provProject.getPlans();
        for(ProvisioningPlan provPlan : planList) {
            if (provPlan == null) {
                log.debug("Input Provisioning Plan is null");
                continue;
            }
            reqStatusMap = null;
            //First check if the Plan has any ProvisioningResult
            reqStatusMap = getStatusFromProvResult(provPlan.getResult());
            if(reqStatusMap != null && reqStatusMap.get(provgStatusMsgs) != null) {
                List<Message> errMsgList = (List)reqStatusMap.get(provgStatusMsgs);
                for(Message err: errMsgList) {
                    provStatusMsgs.add("Target: " + provPlan.getTargetIntegration() + ": " + err.getMessage());
                }
            }
            //Iterate through all the ProvisioningResults from all requests into the plan
            //NULL validation is done for the list of requests from input plan into the above!
            List<AbstractRequest> allProvReqsFromPlan = provPlan.getAllRequests();
            for (Iterator iterReqs = allProvReqsFromPlan.iterator(); iterReqs.hasNext();) {
                AbstractRequest provReq = (AbstractRequest) iterReqs.next();
                reqStatusMap = getStatusFromProvResult(provReq.getResult());
                if (null != reqStatusMap && null != reqStatusMap.get(provgStatusMsgs)) {
                    List<Message> errMsgList = (List)reqStatusMap.get(provgStatusMsgs);
                    for(Message err: errMsgList) {
                        provStatusMsgs.add("Application: " + provReq.getApplicationName() + ": " + err.getMessage());
                    }
                }
            }
        }
        if (provStatusMsgs.isEmpty()) {
            //No error messages from all the underlying requests from the input Provisioning Plan hence setting the success flag as true
            provStatusMap.put(isProvgSuccessful, true);
        } else {
            //Found error messages from one or more underlying requests from the input Provisioning Plan hence setting the success flag as false
            provStatusMap.put(isProvgSuccessful, false);
            provStatusMap.put(provgStatusMsgs, provStatusMsgs);
        }
        log.debug("provStatusMap.."+provStatusMap);
        return provStatusMap;
    }

    /**
     * Provides the status from the Provisioning Result in map with status as
     * boolean and error messages into a list, if any!
     *
     * @param provResult ProvisioningResult
     * @return Map with isProvgSuccessful status as boolean and provgStatusMsgs
     * as error messages into a list, if any!
     * @throws GeneralException
     */
    public static Map getStatusFromProvResult(ProvisioningResult provResult) throws GeneralException {
        //Using Map to accommodate any future enhancements
        if (provResult == null) {
            log.debug("Input ProvisioningResult coming as null!");
            return null;
        }
        Map statusMap = new HashMap();
        String provStatus = provResult.getStatus();
        if (Util.isNotNullOrEmpty(provStatus) &&
                        (ProvisioningResult.STATUS_FAILED.equalsIgnoreCase(provStatus) ||
                        ProvisioningResult.STATUS_RETRY.equalsIgnoreCase(provStatus))) {
            log.debug("Provisioning Status is Failed (or) Retry");
            statusMap.put(provgStatusMsgs, provResult.getErrors());
        }
        return statusMap;
    }

    /**
     * Returns a Map which contains:
     * 1) A key called "isProvgSuccessful" with a true boolean value if the IdentityRequest contains no errors
     * 2) A key called "provgStatusMsgs" with a list of error strings, if available
     *
     * @param identityRequest the Identity Request name (number)
     * @return Map with isProvgSuccessful status as boolean and provgStatusMsgs
     * as error messages into a list, if any
     * @throws GeneralException
     */
    private static Map getIdentityRequestStatusMap(SailPointContext context, String identityRequest) throws GeneralException {
        Map provStatusMap = new HashMap();
        if (Util.isNullOrEmpty(identityRequest)) {
            return provStatusMap;
        }
        IdentityRequest idReq = context.getObjectByName(IdentityRequest.class, identityRequest);
        if(idReq != null) {
            List provStatusMsgs = new ArrayList();
            //check if IdentityRequest contains any error message
            if(idReq.getMessages() != null && idReq.getMessages().size() > 0) {
                Iterator<Message> msgIterator = idReq.getMessages().iterator();
                while(msgIterator.hasNext()) {
                    Message msg = msgIterator.next();
                    if(msg.isError()) {
                        provStatusMsgs.add("Error message in IdentityRequest: " + msg.getMessage());
                    }
                }
            }
            if (provStatusMsgs.isEmpty()) {
                //No error messages in IdentityRequest
                provStatusMap.put(isProvgSuccessful, true);
            }
            else {
                //Found error messages
                provStatusMap.put(isProvgSuccessful, false);
                provStatusMap.put(provgStatusMsgs, provStatusMsgs);
            }
            context.decache(idReq);
        }
        return provStatusMap;
    }

    /**
     * Ger the errors (if any) that occurred during provisioning of the given project
     * @param context persistence context
     * @param provProject the project to examine for errors
     * @return the list of errors which occurred during provisioning of the given project.
     * Returns an empty list of no errors found.
     * @throws GeneralException unexpected database error
     */
    public static List<String> getProvisioningErrors(SailPointContext context, ProvisioningProject provProject)
    throws GeneralException
    {
        List<String> errors = new ArrayList<String>();
        Map resultMap = getProvisioningStatusMap(context, provProject);
        if (!Util.isEmpty(resultMap)) {
            List<String> projErrors = (List<String>)resultMap.get(provgStatusMsgs);
            if (!Util.isEmpty(projErrors)) {
                errors.addAll(projErrors);
            }
        }
        return errors;
    }

    /**
     * Get Identity Name for Notification
     * @param identityName
     * @param provProject
     * @param manager
     * @return
     * @throws GeneralException
     */
    public static String getIdentityNameForNotification(SailPointContext context,
                                                        Workflow workflow,
                                                        String identityName,
                                                        ProvisioningProject provProject,
                                                        boolean manager) throws GeneralException {
        log.debug("Enter getIdentityNameForNotification " + identityName);
        if(identityName != null && workflow != null &&
                (identityName.equalsIgnoreCase("SelfRegistrationWorkGroup") ||
                        identityName.equalsIgnoreCase("SailPointContextRequestFilter"))) {
            log.debug("Registration getIdentityNameForNotification " + identityName);
            identityName = (String)workflow.get("identityName");
            log.debug("Registration getIdentityNameForNotification " + identityName);
        }

        String retVal = "";
        Map resultMap = getProvisioningStatusMap(context, provProject);
        if (resultMap != null && resultMap.size() > 0) {
            if (identityName != null && (boolean)resultMap.get("isProvgSuccessful"))  {
                try {
                    //Start Override Manager Notification to WorkGroup
                    String requestType = null;
                    String workgroup = null;
                    if(workflow != null) {
                        requestType = (String) workflow.get("requestType");
                        log.debug("requestType " + requestType);
                    }

                    if (manager && requestType != null) {
                        if (requestType.equalsIgnoreCase("leaver")) {
                            Boolean isTerminate = (Boolean) workflow.get("isTerminateIdentity");
                            if(Util.nullsafeBoolean(isTerminate)) {
                                workgroup = RapidSetupConfigUtils.getString(ApplicationOnboardService.PATH_TO_TERMINATE_EMAIL_ALT_NOTIFY_WORKGROUP);
                            } else {
                                workgroup = RapidSetupConfigUtils.getString(ApplicationOnboardService.PATH_TO_LEAVER_EMAIL_ALT_NOTIFY_WORKGROUP);
                            }
                        }
                        if (requestType.equalsIgnoreCase("joiner")) {
                            workgroup = RapidSetupConfigUtils.getString(ApplicationOnboardService.PATH_TO_JOINER_EMAIL_ALT_NOTIFY_WORKGROUP);
                        }
                        if (Util.isNotNullOrEmpty(workgroup)) {
                            QueryOptions ops = new QueryOptions(Filter.and(Filter.eq("workgroup", true), Filter.eq("name", workgroup)));
                            // only a possibility of a count of 1 or 0 since name is unique
                            if (context.countObjects(Identity.class, ops) == 1) {
                                log.debug("Override Manager Email for Joiner");
                                log.debug("Exit getIdentityNameForNotification " + workgroup);
                                return workgroup;
                            } else {
                                log.warn("altNotifyWorkgroup has evaporated and cannot be used for notification: " + workgroup);
                            }
                        }
                        // TODO add logic for leaver rehire, mover, etc al here
                    }
                    //End Override Manager Notification to WorkGroup

                    // Looks we have to do this the old-fashioned way
                    if (manager) {
                        retVal = getManagerOrAltIdentityName(context, identityName);
                    }
                    else {
                        Identity ident = context.getObjectByName(Identity.class, identityName);
                        if (ident != null) {
                            retVal = ident.getName();
                            context.decache(ident);
                        }
                    }
                }
                catch (GeneralException e)  {
                    log.error("SEVERE "+e.getMessage());
                }
            }
            else  {
                Identity opWrkGrp = context.getObjectByName(Identity.class, getErrorNotificationWorkGroupName());
                if(opWrkGrp != null) {
                    retVal = opWrkGrp.getName();
                    context.decache(opWrkGrp);
                }
            }
        }
        else
        {
            Identity idObj = context.getObjectByName(Identity.class, identityName);
            log.debug(" getIdentityNameForNotification "+identityName);
            if (idObj != null)  {
                retVal = idObj.getName();
                context.decache(idObj);
            }
        }
        log.debug("Exit getIdentityNameForNotification: " + retVal);
        return retVal;
    }

    /**
     * @return the name of the global error notification work group
     */
    private static String getErrorNotificationWorkGroupName() throws GeneralException {
        return RapidSetupConfigUtils.getString("email,errorNotificationWorkGroup");
    }

    /**
     * Calculate the map of dynamic content (keyed by application name)
     * @param project the ProvisioningProject used to populate Velocity context
     *                with.  Each provisioningPlan in the project will have its
     *                dynamic content computed for its associated application.  The
     *                static text that will be expanded with Velocity is found via
     *                the templatePath config property.
     * @param templatePath a comma-separated path which specifies the default key (relative
     *                     to the applcication) for the static text to expand
     * @param reqType the type of RapidSetup request occurring.  E.g. "joiner".
     * @return
     */
    public static Map getDynamicTextEmailTemplate(SailPointContext context, ProvisioningProject project, String templatePath, String reqType)
            throws GeneralException {
        Map dynamicAppContent = new HashMap();

        if (project != null) {
            String identityName = project.getIdentity();
            List<ProvisioningPlan> planList = project.getPlans();
            for (ProvisioningPlan plan : planList) {
                List<AbstractRequest> requests = plan.getAllRequests();
                if (requests != null) {
                    for (AbstractRequest req : requests) {
                        String appName = req.getApplication();
                        Map textMap = new HashMap();
                        List<AttributeRequest> atts = req.getAttributeRequests();
                        if (atts != null) {
                            for (AttributeRequest att : atts) {
                                if (att.getValue() != null && att.getValue() instanceof String) {
                                    String value = String.valueOf(att.getValue());
                                    String name = att.getName();
                                    Attributes attributeRequestAttr = att.getArguments();
                                    boolean isSecret = RapidSetupConfigUtils.getBoolean(attributeRequestAttr, "secret");
                                    if (isSecret) {
                                        if (value != null) {
                                            log.debug(" Decrypt Attr value.. " + name);
                                            value = context.decrypt(value);
                                        }
                                    }
                                    textMap.put(name, value);
                                }
                            }
                        } else {
                            // We need to handle account only provisioning also when there is no AttributeRequest.
                            // There should be a few known values in textMap, just in case someone wants to use them.
                            if (ProvisioningPlan.ObjectOperation.Create.equals(req.getOp())) {
                                String nativeIdentity = req.getNativeIdentity();
                                if (Util.isNullOrEmpty(nativeIdentity)) {
                                    nativeIdentity = "New account";
                                }
                                textMap.put("nativeIdentity", nativeIdentity);
                                if (appName != null) {
                                    textMap.put("application", appName);
                                }
                                if (identityName != null) {
                                    textMap.put("identity", identityName);
                                }
                            }
                        }
                        log.debug(" req.getOp() " + req.getOp());
                        log.debug(" textMap " + textMap);
                        String staticText = null;
                        if(req.getOp() != null) {
                                staticText = getStaticAppText(appName, reqType, templatePath, req.getOp().toString());
                        }
                        else {
                            Attributes existingPlanAttributes = plan.getArguments();
                            String requestTypePlanOperation = "Modify";
                            if (existingPlanAttributes != null &&
                                    existingPlanAttributes.containsKey("requestType") &&
                                    existingPlanAttributes.get("requestType") != null &&
                                    (((String) existingPlanAttributes.get("requestType")).equalsIgnoreCase("joiner") ||
                                            ((String) existingPlanAttributes.get("requestType")).equalsIgnoreCase("joinerRehire"))) {
                                requestTypePlanOperation = "Create";
                            }
                            staticText = getStaticAppText(appName, reqType, templatePath, requestTypePlanOperation);
                        }
                        if (staticText != null) {
                            injectAttributesFromApplication(context, identityName, appName, textMap);
                            String dynamicText = getDynamicContent(textMap, staticText);
                            log.debug(" dynamicText " + dynamicText);
                            if (dynamicText != null) {
                                dynamicAppContent.put(appName, dynamicText);
                            }
                        }
                    }
                    if (identityName != null) {
                        injectAttributesFromAuthoritativeApplications(context, identityName, templatePath, reqType, dynamicAppContent);
                    }
                }
            }
        }
        log.debug(" dynamicAppContent " + dynamicAppContent);
        if (Util.isEmpty(dynamicAppContent)) {
            dynamicAppContent = null;
        }

        return dynamicAppContent;
    }

    /**
     * Expand the staticText using Velocity, using the mapAttributeRequestAttrs
     * as the velocity variables available to the staticText.
     * @param mapAttributeRequestAttrs the variables to provide for the staticText expansion
     * @param staticText the text to expand
     * @return the expanded text
     * @throws IOException
     * @throws ResourceNotFoundException
     * @throws MethodInvocationException
     * @throws ParseErrorException
     */
    private static String getDynamicContent(Map<String,String> mapAttributeRequestAttrs, String staticText)
            throws ParseErrorException, MethodInvocationException, ResourceNotFoundException
    {
        String dynamicContent = null;
        if(staticText != null && !Util.isEmpty(mapAttributeRequestAttrs))  {
            // populate context
            VelocityContext velocityContext = new VelocityContext();
            for(String vkey : mapAttributeRequestAttrs.keySet())  {
                velocityContext.put(vkey, mapAttributeRequestAttrs.get(vkey));
            }
            // Tag to be used in log messages
            String tag = "None";

            // expand the static text
            StringWriter body = new StringWriter();
            Velocity.evaluate(velocityContext, body, tag, staticText);
            dynamicContent = body.toString();
        }
        return dynamicContent;
    }

    /**
     * Inject application's link attribute data into the textMap.
     * @param context persistence context
     * @param identityName the identity to for whom we are fetching links
     * @param appName the application from which to get identity's links
     * @param textMap the Map to populate with application's links' attributes
     * @throws GeneralException
     * @throws ParseErrorException
     * @throws MethodInvocationException
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    private static void injectAttributesFromApplication(SailPointContext context, String identityName, String appName, Map textMap) throws GeneralException
    {
        log.debug("Start injectAttributesFromApplication..");
        log.debug("identityName.."+identityName);
        log.debug("appName.."+appName);
        log.debug("textMap.."+textMap);

        if(identityName != null && appName != null && textMap != null)
        {
            IdentityService idService = new IdentityService(context);
            Identity identity = context.getObjectByName(Identity.class, identityName);
            Application application = context.getObjectByName(Application.class, appName);
            try {
                List<Link> listLinks = idService.getLinks(identity, application);
                if (!Util.isEmpty(listLinks))
                {
                    if(listLinks.size() > 1) {
                        log.debug("Multiple Links..");
                    }
                    for (Link link : listLinks) {
                        Attributes linkAttributes = link.getAttributes();
                        if(!Util.isEmpty(linkAttributes)) {
                            List<String> nameKeys = linkAttributes.getKeys();
                            for (String nameKey : Util.safeIterable(nameKeys)) {
                                if(linkAttributes.get(nameKey) instanceof String){
                                    //Merge Multiple Links/Application Data
                                    textMap.put(nameKey,linkAttributes.get(nameKey));
                                }
                            }
                        }
                    }
                }
            }
            finally {
                if(application != null) {
                    context.decache(application);
                }
                if(identity != null) {
                    context.decache(identity);
                }

            }
        }
        log.debug("After Application Injection Attributes.."+textMap);
        log.debug("End injectAttributesFromApplication..");
    }

    /**
     * Inject dynamic authoritative application data.  For the authoritative apps
     * not already with content in dynamicAppContent, calculate and store their
     * dynamic content from the configured pattern.
     * @param context persistence context
     * @param identityName the identity to find the authoritative applications for
     *                     which the identity has an account
     * @param reqType he type of RapidSetup request occurring.  E.g. "joiner".
     * @param templatePath a comma-separated path which specifies the default key for the
     *                     static text to expand
     * @param dynamicAppContent the Map to populate with applications' dynamic content
     * @throws GeneralException
     * @throws ParseErrorException
     * @throws MethodInvocationException
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    public static void injectAttributesFromAuthoritativeApplications(SailPointContext context,
                                                                     String identityName,
                                                                     String templatePath,
                                                                     String reqType,
                                                                     Map dynamicAppContent)
            throws GeneralException, ParseErrorException, MethodInvocationException, ResourceNotFoundException
    {
        log.debug("Start injectAttributesFromAuthoritativeApplications..");
        log.debug("reqType.."+reqType);
        log.debug("identityName.."+identityName);

        ArrayList<String> listOfAuthAppNames =  getAuthoritativeApplicationNames(context);
        if(!Util.isEmpty(listOfAuthAppNames) && identityName != null) {
            IdentityService idService = new IdentityService(context);
            Identity identity = context.getObjectByName(Identity.class, identityName);
            for(String appName : listOfAuthAppNames) {
                if (dynamicAppContent.containsKey(appName)) {
                    // processed earlier
                    log.debug("Dynamic content for authoritative app " + appName + " was already calculated");
                    continue;
                }
                //Get Static Text - Operation "None" for Authoritative Applications
                String staticText = getStaticAppText(appName, reqType, templatePath, "None");
                if(staticText != null) {
                    Map textMap = new HashMap<>();
                    Application application = context.getObjectByName(Application.class, appName);
                    List<Link> listLinks = idService.getLinks(identity, application);
                    if (null != listLinks && listLinks.size() > 0) {
                        if (listLinks.size() > 1) {
                            log.debug("Multiple Links..");
                        }
                        for (Link link : listLinks) {
                            Attributes linkAttributes = link.getAttributes();
                            if (linkAttributes != null && !linkAttributes.isEmpty()) {
                                List<String> nameKeys = linkAttributes.getKeys();
                                if (nameKeys != null) {
                                    for (String nameKey : nameKeys) {
                                        if (linkAttributes.get(nameKey) != null && linkAttributes.get(nameKey) instanceof String) {
                                            //Merge Multiple Links/Application Data
                                            textMap.put(nameKey, linkAttributes.get(nameKey));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    String dynamicText = getDynamicContent(textMap, staticText);
                    if(dynamicText != null) {
                        dynamicAppContent.put(appName,dynamicText);
                    }
                    if(application != null) {
                        context.decache(application);
                    }
                }
            }
            if(identity != null) {
                context.decache(identity);
            }
        }
        log.debug("dynamicAppContent.."+dynamicAppContent);
        log.debug("End injectAttributesFromAuthoritativeApplications..");
    }

    /**
     * @return names of authoritative applications
     */
    private static ArrayList<String> getAuthoritativeApplicationNames(SailPointContext context) throws GeneralException {
        ArrayList<String> list = new ArrayList<>();
        Filter filter = Filter.eq("authoritative", true);
        QueryOptions qo = new QueryOptions();
        qo.addFilter(filter);
        List<String> propsApp = new ArrayList();
        propsApp.add("name");
        Iterator iterApp = context.search(Application.class, qo, propsApp);
        if (iterApp != null) {
            while (iterApp.hasNext()) {
                Object[] rowApp = (Object[]) iterApp.next();
                if (rowApp != null && rowApp.length == 1) {
                    String appName = (String) rowApp[0];
                    if (appName != null) {
                        list.add(appName);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Get static application email pattern from the given application's config.
     * The key that the pattern string is expected to be found at under the
     * application's key is given
     * by: businessProcesses,{reqType},{templatePath}[,{operationName]}
     * @param appName the application to search the config to find the text
     * @param reqType the type of RapidSetup request occurring.  E.g. "joiner".
     * @param templatePath a comma-separated path which specifies the default key
     * @param operationName the optional operation to search for. Expected values are
     *                      "Create", "Modify", and "None".  If not present, then the
     *                      key to use is just the templatePath.
     * @return
     * @throws GeneralException
     */
    private static String getStaticAppText(String appName, String reqType, String templatePath, String operationName) {
        String staticText = null;
        Map<String,Object> appBizProcCfg = RapidSetupConfigUtils.getApplicationBusinessProcessConfig(appName, reqType);
        if (!Util.isEmpty(appBizProcCfg)) {
            final String basePath = templatePath + ",dynamic";
            // first, try with the operationName
            if (Util.isNotNullOrEmpty(operationName)) {
                String path = basePath + "," + operationName;
                staticText = RapidSetupConfigUtils.getString(appBizProcCfg, path);
                if (Util.isNotNullOrEmpty(staticText)) {
                    log.debug("Found velocity text '" + staticText + "' for key " + path + " for application " + appName);
                }
            }
            if (Util.isEmpty(staticText)) {
                // fallback to try just the base path
                Object baseVal = RapidSetupConfigUtils.get(appBizProcCfg, basePath);
                staticText = baseVal instanceof String ? baseVal.toString() : null;
                if (Util.isNotNullOrEmpty(staticText)) {
                    log.debug("Found velocity text '" + staticText + "' for key " + basePath + " for application " + appName);
                }
            }
        }
        else {
            log.debug("No " + reqType + " config found for application " + appName);
        }
        return staticText;
    }

    /**
     * Retrieves a list of Approved Account Request
     *
     * @param accountRequests
     *            All the Account Requests in the provisioning plan
     * @param approvalSet
     *            All The ApprovalSet in the provisioning plan
     * @return a list of
     *         {@link sailpoint.integration.ProvisioningPlan.AccountRequest}
     */
    public static List<AccountRequest> getApprovedAccountRequest(List<AccountRequest> accountRequests,
            ApprovalSet approvalSet, String process) {
        List<AccountRequest> filteredAccountRequest = new ArrayList<>();
        if (RapidSetupConfigUtils.shouldGenerateApprovals(process)) {
            Map<String, ApprovalItem> approvedItems = new HashMap<>();
            for (ApprovalItem approvalItem : Util.safeIterable(approvalSet.getApproved())) {
                StringBuilder approvalItemKey = new StringBuilder(approvalItem.getApplication());
                if (!(approvalItem.getOperation().equals(Operation.Create.name())
                        || approvalItem.getOperation().equals(Operation.Delete.name()))) {
                    approvalItemKey.append(STR_COMMA).append(approvalItem.getAttribute(ARG_ID));
                }
                approvedItems.put(approvalItemKey.toString(), approvalItem);
            }
            Set<String> approvedItemsKeys = Util.safeStream(approvedItems.entrySet()).map(Map.Entry::getKey)
                    .collect(toSet());
            for (AccountRequest accountRequest : Util.safeIterable(accountRequests)) {
                StringBuilder accountRequestKey = new StringBuilder(accountRequest.getApplication());
                if (!(accountRequest.getOperation().equals(Operation.Create)
                        || accountRequest.getOperation().equals(Operation.Delete))) {
                    accountRequestKey.append(STR_COMMA).append(accountRequest.getArgument(ARG_ID));
                }
                String accountRequestKeyStr = accountRequestKey.toString();
                if (approvedItemsKeys.contains(accountRequestKeyStr)) {
                    ApprovalItem tmpApprovalItem = approvedItems.get(accountRequestKeyStr);
                    accountRequest.addArgument(ARG_COMMENTS, tmpApprovalItem.getComments());
                    accountRequest.addArgument(ARG_REQUESTER_COMMENTS, tmpApprovalItem.getRequesterComments());
                    filteredAccountRequest.add(accountRequest);
                }
            }
        } else {
            filteredAccountRequest.addAll(accountRequests);
        }
        return filteredAccountRequest;
    }

}


