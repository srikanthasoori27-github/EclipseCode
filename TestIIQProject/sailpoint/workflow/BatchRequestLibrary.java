/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to group updates.
 *
 * Author: Jeff
 *
 * This was originally designed around AccountGroup but now
 * it can be used for any ManagedAttribute.
 */

package sailpoint.workflow;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequest;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Schema;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.provisioning.ApplicationPolicyExpander;
import sailpoint.provisioning.PlanUtil;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

/**
 * Workflow library for processing batch requests
 *
 */
public class BatchRequestLibrary extends WorkflowLibrary {

    private static Log log = LogFactory.getLog(BatchRequestLibrary.class);

    public BatchRequestLibrary() {

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    BatchRequest batchReq = null;

    Set<String> requiredFields = new HashSet<String>();

    String lastOperationType;

    String requesterName;

    List<String> headers;

    boolean skipManualWorkItems = true;
    boolean skipProvisioningForms = true;
    boolean disableIdentityRequests = false;
    boolean allowSplitBatchEntitlementRequests = true;
    String  splitBatchEntitlementString;

    private String  policyScheme;

    RFC4180LineParser csvParser = new RFC4180LineParser(',');

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    public static final String OP_CREATE_IDENTITY = "createidentity";
    public static final String OP_MODIFY_IDENTITY = "modifyidentity";
    public static final String OP_CREATE_ACCOUNT = "createaccount";
    public static final String OP_DELETE_ACCOUNT = "deleteaccount";
    public static final String OP_ENABLE_ACCOUNT = "enableaccount";
    public static final String OP_DISABLE_ACCOUNT = "disableaccount";
    public static final String OP_UNLOCK_ACCOUNT = "unlockaccount";
    public static final String OP_ADD_ROLE = "addrole";
    public static final String OP_REMOVE_ROLE = "removerole";
    public static final String OP_ADD_ENTITLEMENT = "addentitlement";
    public static final String OP_REMOVE_ENTITLEMENT = "removeentitlement";
    public static final String OP_CHANGE_PASSWORD = "changepassword";

    public static final String OPERATION_HEADER = "operation";

    public static final String NAME_HEADER = "name";

    // account related headers
    public static final String APP_HEADER = "application";
    public static final String IDENTITY_HEADER = "identityname";
    public static final String NATIVE_HEADER = "nativeidentity";

    // role related headers
    public static final String ROLES_HEADER = "roles";
    public static final String SUNRISE_HEADER = "sunrise";
    public static final String SUNSET_HEADER = "sunset";

    // entitlement related headers
    public static final String ENTNAME_HEADER = "attributename";
    public static final String ENTVALUE_HEADER = "attributevalue";

    // password header
    public static final String PASSWORD_HEADER = "password";


    public static HashSet<String> NOMIX_OPS = new HashSet<String>();
    public static HashSet<String> ROLE_OPS = new HashSet<String>();
    public static HashSet<String> ENT_OPS = new HashSet<String>();
    public static HashSet<String> ACCOUNT_OPS = new HashSet<String>();

    // flow config name lookup map
    private static HashMap<String, String> FLOW_LOOKUP_MAP = new HashMap<String, String>();

    static {
        initStatic();
    }

    public static void initStatic() {
        FLOW_LOOKUP_MAP.put(OP_CREATE_IDENTITY, IdentityRequest.IDENTITY_CREATE_FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_MODIFY_IDENTITY, IdentityRequest.IDENTITY_UPDATE_FLOW_CONFIG_NAME);

        FLOW_LOOKUP_MAP.put(OP_CREATE_ACCOUNT, IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_DELETE_ACCOUNT, IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_DISABLE_ACCOUNT, IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_ENABLE_ACCOUNT, IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_UNLOCK_ACCOUNT, IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME);

        FLOW_LOOKUP_MAP.put(OP_ADD_ENTITLEMENT, RequestAccessService.FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_REMOVE_ENTITLEMENT, RequestAccessService.FLOW_CONFIG_NAME);

        FLOW_LOOKUP_MAP.put(OP_ADD_ROLE, RequestAccessService.FLOW_CONFIG_NAME);
        FLOW_LOOKUP_MAP.put(OP_REMOVE_ROLE, RequestAccessService.FLOW_CONFIG_NAME);

        FLOW_LOOKUP_MAP.put(OP_CHANGE_PASSWORD, IdentityRequest.PASSWORD_REQUEST_FLOW);

        NOMIX_OPS.add(OP_CREATE_IDENTITY);
        NOMIX_OPS.add(OP_MODIFY_IDENTITY);
        NOMIX_OPS.add(OP_CREATE_ACCOUNT);
        NOMIX_OPS.add(OP_CHANGE_PASSWORD);

        ROLE_OPS.add(OP_ADD_ROLE);
        ROLE_OPS.add(OP_REMOVE_ROLE);

        ENT_OPS.add(OP_ADD_ENTITLEMENT);
        ENT_OPS.add(OP_REMOVE_ENTITLEMENT);

        ACCOUNT_OPS.add(OP_DISABLE_ACCOUNT);
        ACCOUNT_OPS.add(OP_ENABLE_ACCOUNT);
        ACCOUNT_OPS.add(OP_DELETE_ACCOUNT);
        ACCOUNT_OPS.add(OP_UNLOCK_ACCOUNT);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Variables and Arguments
    //
    //////////////////////////////////////////////////////////////////////

    public static final String VAR_BATCH_ID = "batchRequestId";
    public static final String VAR_BATCH_ITEM_ID = "batchRequestItemId";
    public static final String VAR_RESULT = "result";
    public static final String RES_SUCCESS = "Success";
    public static final String ALLOW_SPLIT_BATCH_ENT_REQS = "allowSplitBatchEntitlementRequests";
    public static final String SPLIT_BATCH_ENT_STR = "splitBatchEntitlementStr";

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Plans
    //
    //////////////////////////////////////////////////////////////////////

    private void getBatchRequest(SailPointContext context, String id) throws GeneralException {
        // Check if object exists and retrieve
        try {
            batchReq = context.getObjectById(BatchRequest.class, id);

            requesterName = batchReq.getOwner().getName();

            Attributes<String, Object> runConfig = batchReq.getRunConfig();

            skipProvisioningForms = (Boolean) runConfig.get("skipProvisioningForms");
            skipManualWorkItems = (Boolean) runConfig.get("skipManualWorkItems");
            policyScheme = (String) runConfig.get("policyScheme");
            disableIdentityRequests = !(Boolean) runConfig.get("generateIdentityRequests");
        }
        catch (GeneralException ge) {
            throw new GeneralException("Batch request not found.");
        }

        if (batchReq == null) {
            throw new GeneralException("Batch request not found.");
        }
    }

    /**
     * Batch request approval was rejected. Update the request with the proper status and result.
     * 
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void requestRejected(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();

        Attributes<String,Object> args = wfc.getStepArguments();

        String batchRequestId = args.getString(VAR_BATCH_ID);

        // Get the batch request
        getBatchRequest(context, batchRequestId);

        if (batchReq.getStatus() == BatchRequest.Status.Approval) {
            batchReq.setStatus(BatchRequest.Status.Rejected);
            context.saveObject(batchReq);
            context.commitTransaction();
        }

        List<BatchRequestItem> items = batchReq.getBatchRequestItems();

        for (BatchRequestItem item : items) {
            item.setResult(BatchRequestItem.Result.Failed);
            item.setStatus(BatchRequestItem.Status.Rejected);

            context.saveObject(item);
            context.commitTransaction();
        }

    }

    /**
     * Iterate through the BatchRequestItems and create AccountRequests
     * 
     * Push each request through a workflow.
     * 
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void launchBatchWorkflows(WorkflowContext wfc) throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();

        Configuration sysConfig = context.getConfiguration();
        if (null != sysConfig) {
            allowSplitBatchEntitlementRequests = sysConfig.containsAttribute(ALLOW_SPLIT_BATCH_ENT_REQS) ?
                sysConfig.getAttributes().getBoolean(ALLOW_SPLIT_BATCH_ENT_REQS) : true;
            splitBatchEntitlementString = sysConfig.getAttributes().getString(SPLIT_BATCH_ENT_STR);
        }

        context.decache();

        // Init parser
        csvParser.setTrimValues(true);
        csvParser.tolerateMissingColumns();

        // Get arguments
        Attributes<String,Object> args = wfc.getStepArguments();

        String batchRequestId = args.getString(VAR_BATCH_ID);

        // Get the batch request
        getBatchRequest(context, batchRequestId);

        // Get the batch request items
        List<BatchRequestItem> items = batchReq.getBatchRequestItems();

        // Parse out the header fields
        headers = csvParser.parseLine(batchReq.getHeader());

        // Temp map to store header field and record value pairs
        HashMap<String, String> brMap = new HashMap<String, String>();

        // If in the approval status move to running
        if (batchReq.getStatus() == BatchRequest.Status.Approval) {
            batchReq.setStatus(BatchRequest.Status.Running);
            context.saveObject(batchReq);
            context.commitTransaction();
        }

        // Iterate through the items and create AccountRequests for running through a workflow
        for (BatchRequestItem item : items) {
            // dont run invalid requests
            if (item.getStatus() == BatchRequestItem.Status.Invalid) {
                continue;
            }

            context.decache(batchReq);
            batchReq = context.getObjectById(BatchRequest.class, batchRequestId);
            if (batchReq.getStatus() == BatchRequest.Status.Terminated) {
                log.error("Terminating batch request");

                for (BatchRequestItem bi : items) {
                    if (bi.getStatus() == null) {
                        bi.setStatus(BatchRequestItem.Status.Terminated);
                        context.saveObject(bi);
                    }
                }
                break;
            }

            item.setStatus(BatchRequestItem.Status.Running);

            String requestData = item.getRequestData();

            ArrayList<String> requestValues = csvParser.parseLine(requestData);

            String op = requestValues.get(0).trim().toLowerCase();

            for (int j = 1; j < headers.size(); ++j) {
                brMap.put(headers.get(j), requestValues.get(j));
            }

            ProvisioningPlan plan = new ProvisioningPlan();

            String targetId = item.getTargetIdentityId();

            AccountRequest request = generateRequest(targetId, op, context, plan, brMap);

            if (request == null) {
                item.setStatus(BatchRequestItem.Status.Invalid);
                item.setResult(BatchRequestItem.Result.Skipped);
                context.saveObject(item);
                context.commitTransaction();
                continue;
            }

            // save run status before launching asynch workflows
            context.saveObject(item);
            context.commitTransaction();

            plan.add(request);

            String itemId = item.getId();

            Attributes<String, Object> workFlowArgs = getWorkFlowArgs(op, itemId);

            log.info("Executing workflow for plan: " + plan.toMap());

            runWorkflow(op, workFlowArgs, plan, context);
        }
    }

    /**
     * get the workflow args
     */
    private Attributes<String, Object> getWorkFlowArgs(String op, String itemId) {

        Attributes<String, Object> workFlowArgs = new Attributes<String, Object>();
        String flowName = FLOW_LOOKUP_MAP.get(op);
        // General workflow args
        workFlowArgs.put("flow", flowName);
        workFlowArgs.put(Workflow.ARG_WORK_ITEM_PRIORITY, WorkItem.Level.Normal);
        workFlowArgs.put("launcher", requesterName);
        workFlowArgs.put("endOnManualWorkItems", skipManualWorkItems);
        workFlowArgs.put("endOnProvisioningForms", skipProvisioningForms);
        workFlowArgs.put("policyScheme", policyScheme);
        workFlowArgs.put("batchRequestItemId", itemId);
        //disable approvals and notification
        workFlowArgs.put("approvalScheme", "none");
        workFlowArgs.put("notificationScheme", "none");
        workFlowArgs.put("interface", "Batch");
        workFlowArgs.put("source", "Batch");
        if (disableIdentityRequests) {
            workFlowArgs.put("disableIdentityRequests", true);
        }
        return workFlowArgs;
    }

    private WorkflowSession runWorkflow(String op, Attributes<String,Object> ops, ProvisioningPlan plan, SailPointContext context) 
            throws GeneralException {

        String name = getWorkflowName(context, op);

        IdentityLifecycler cycler = new IdentityLifecycler(context);

        // generate case name
        String caseName = "Batch Request : " + name;

        // first arg is owner
        WorkflowSession ses = cycler.launchUpdate(requesterName, plan.getIdentity(), plan, name, ops, caseName);

        WorkflowLaunch launch = ses.getWorkflowLaunch();

        launch.setLauncher(requesterName);

        return ses;
    }

    /**
     * Get batch version of workflow 
     * 
     * @param ctx SailPointContext
     * @param op Operation constant
     * @return String workflow name
     * @throws GeneralException
     * 
     * @ignore
     * Duplicate code from SubmitRequestBean
     */
    public String getWorkflowName(SailPointContext ctx, String op)
            throws GeneralException {

        String flowName = FLOW_LOOKUP_MAP.get(op);
        String workflow = null;
        Configuration sysConfig = ctx.getConfiguration();
        if (sysConfig != null) {
            String configName = "batchRequest" + flowName;
            String configuredWorkflow = sysConfig.getString(configName);
            if (Util.getString(configuredWorkflow) != null) {
                workflow = configuredWorkflow;
            } else {
                throw new GeneralException(
                        "Unable to find system config system settting for flow '"
                                + flowName + "' using config name'"
                                + configName + "'");
            }
        }
        return workflow;
    }

    /**
     * Generate a single account request
     */
    private AccountRequest generateRequest(String targetId, String op, SailPointContext context, ProvisioningPlan plan, HashMap<String, String> requestMap) throws GeneralException {

        AccountRequest request = new AccountRequest();

        Identity target = null;

        if (targetId != null) {
            target = context.getObjectById(Identity.class, targetId);
        }
        else if (!OP_CREATE_IDENTITY.equals(op) && !OP_ADD_ENTITLEMENT.equals(op)) {
            // targetId is required for non create identity ops
            log.error("invalid or missing target identity");
            return null;
        }

        if (requestMap.containsKey(APP_HEADER)) {
            request.setApplication(requestMap.get(APP_HEADER));
        }
        else {
            request.setApplication(ProvisioningPlan.APP_IIQ);
        }

        String nativeIdentity = null;

        if (requestMap.containsKey(NATIVE_HEADER)) {
            // if they bothered to include the nativeIdentity, we might want to pass that on to the request
            nativeIdentity = requestMap.get(NATIVE_HEADER);
        }
        if (OP_CREATE_IDENTITY.equals(op)) {
            target  = context.getObjectByName(Identity.class, requestMap.get("name"));
            if (target != null) {
                // identity already exists
                // generate modify request?
                generateModifyIdentityRequest(request, requestMap);
            }
            else {
                generateCreateIdentityRequest(request, requestMap);
                target = new Identity();
                String name = requestMap.get("name");
                //Validate name
                //IIQPB-879: Don't allow creating Identity with name equal to that of another Identity ID.
                if (Util.isNotNullOrEmpty(name)) {
                    QueryOptions ops = new QueryOptions(Filter.eq("id", name));
                    if (context.countObjects(Identity.class, ops) > 0) {
                        log.error("Invalid Identity Name: " + name);
                        return null;
                    }
                }
                target.setName(name);
            }
        }
        else if (OP_MODIFY_IDENTITY.equals(op)) {
            generateModifyIdentityRequest(request, requestMap);
        }
        else if (ROLE_OPS.contains(op)) {
            Date sunrise = null;
            Date sunset = null;

            if (requestMap.containsKey(SUNRISE_HEADER) && requestMap.get(SUNRISE_HEADER) != null 
                    &&  requestMap.get(SUNRISE_HEADER).length() != 0) {
                try {
                    sunrise = Util.stringToDate(requestMap.get(SUNRISE_HEADER));
                } catch (ParseException e) {
                    log.error("invalid date format for sunrise arg");
                }
            }
            if (requestMap.containsKey(SUNSET_HEADER) && requestMap.get(SUNSET_HEADER) != null 
                    && requestMap.get(SUNSET_HEADER).length() != 0)  {
                try {
                    sunset = Util.stringToDate(requestMap.get(SUNSET_HEADER));
                } catch (ParseException e) {
                    log.error("invalid date format for sunset arg");
                }
            }
            generateRoleRequest(request, requestMap.get(ROLES_HEADER), op, sunrise, sunset, target);
        }
        else if (ENT_OPS.contains(op)) {
            if (target == null) {
                target = new Identity();
                target.setName(requestMap.get(NATIVE_HEADER));
            }
            generateEntitlementRequest(request, requestMap.get(ENTNAME_HEADER), requestMap.get(ENTVALUE_HEADER), op );
            if (nativeIdentity != null) {
                request.setNativeIdentity(nativeIdentity);
            }
        }
        else if (OP_CHANGE_PASSWORD.equals(op)) {
            generateChangePasswordRequest(request, requestMap);
            if (nativeIdentity != null) {
                request.setNativeIdentity(nativeIdentity);
            }
        }
        else if (OP_CREATE_ACCOUNT.equals(op)) {
            Application app = request.getApplication(context);

            boolean hasLink = hasLink(context, nativeIdentity, target, app);

            if (hasLink) {
                // account already exists
                return null;
            }
            else {
                denormalizeMap(app, Application.SCHEMA_ACCOUNT, requestMap);
                generateCreateAccountRequest(request, requestMap);
            }
        }
        else if (OP_DELETE_ACCOUNT.equals(op)) {
            Application app = request.getApplication(context);

            boolean hasLink = hasLink(context, nativeIdentity, target, app);

            // no account to delete?
            if (!hasLink) {
                return null;
            }

            request.setOperation(AccountRequest.Operation.Delete);
        }
        else if (OP_DISABLE_ACCOUNT.equals(op)) {
            request.setOperation(AccountRequest.Operation.Disable);
        }
        else if (OP_ENABLE_ACCOUNT.equals(op)) {
            request.setOperation(AccountRequest.Operation.Enable);
        }
        else if (OP_UNLOCK_ACCOUNT.equals(op)) {
            request.setOperation(AccountRequest.Operation.Unlock);
        }

        if (ACCOUNT_OPS.contains(op) && requestMap.containsKey(NATIVE_HEADER)) {
            request.setNativeIdentity(requestMap.get(NATIVE_HEADER));
        }

        // Set plan target identity?
        plan.setIdentity(target);

        return request;
    }


    /*
     * Through various reasons, we've normalized headers to be all lower case.  For account
     * requests, this can be problematic when our account schema attributes are case
     * sensitive
     */
    private void denormalizeMap(Application app, String schemaName,
            Map<String, String> requestMap) {
        if (requestMap == null) {
            return;
        }

        Schema schema = app.getSchema(schemaName);
        List<String> schemaAttributes = schema.getAttributeNames();
        // It's a tricky thing to effectively rename a key, especially while iterating through the map.
        List<String> keys = new ArrayList<String>(requestMap.keySet());
        for (String key : keys) {
            // I have to iterate the schemaAttributes to do case insensitive comparisons
            for (String schemaAttribute : schemaAttributes) {
                if (key.equalsIgnoreCase(schemaAttribute) && !key.equals(schemaAttribute)) {
                    // Do make a change if it's a difference in case.  Don't make a change
                    // if there is no case difference or if the key isn't even found
                    String value = requestMap.remove(key);
                    requestMap.put(schemaAttribute, value);
                }
            }
        }
    }

    private boolean hasLink (SailPointContext context, String nativeIdentity, Identity target, Application app) throws GeneralException {

        QueryOptions opts = new QueryOptions();
        if (nativeIdentity != null) {
            opts.add(Filter.eq("nativeIdentity", nativeIdentity));
        }

        // I'm guessing that if either application or identity are null, there's going to be some problems down the line.
        // I choose not to be the one who holds up the show and just work with what I got.
        if (app != null) {
            opts.add(Filter.eq("application", app));
        }
        if (target != null) {
            opts.add(Filter.eq("identity", target));
        }

        int count = context.countObjects(Link.class, opts);

        return count > 0;
    }

    private void generateEntitlementRequest(AccountRequest request, String entName, String entValue, String op) {
        request.setOperation(AccountRequest.Operation.Modify);

        String[] entList = { entValue };

        if (splitBatchEntitlementString == null) {
            splitBatchEntitlementString = "\\|";
        }

        if (allowSplitBatchEntitlementRequests) {
            entList = entValue.split(splitBatchEntitlementString);
        }

        ProvisioningPlan.Operation reqOp;

        if (op.equals(OP_REMOVE_ENTITLEMENT)) {
            reqOp = ProvisioningPlan.Operation.Remove;
        }
        else {
            reqOp = ProvisioningPlan.Operation.Add;
        }

        for (int i=0; i<entList.length; ++i) {
            String entVal = entList[i].trim();

            AttributeRequest attReq = new AttributeRequest(entName, entVal);

            attReq.setOp(reqOp);
            
            //IIQSAW-2944 -- Set Assignment flag to add/remove AttributeAssignment inside Identity.
            //This flag is used in IIQEvaluator.processAttributeAssignments()
            attReq.setAssignment(true);
            
            request.add(attReq);
        }
    }

    private void generateRoleRequest(AccountRequest request, String rolesToAdd, String operationType, Date sunrise,
                                     Date sunset, Identity target) {
        ProvisioningPlan.Operation op = ProvisioningPlan.Operation.Add;

        if (operationType.equals(OP_REMOVE_ROLE)) {
            op = ProvisioningPlan.Operation.Remove;
        }

        request.setOperation(AccountRequest.Operation.Modify);


        String[] rolesList = rolesToAdd.split("\\|");

        for (int i=0; i<rolesList.length; ++i) {
            String roleName = rolesList[i].trim();
            String requestName = ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES;

            List<RoleAssignment> assignments = target.getRoleAssignments(roleName);

            //IIQSAW-2946 -- handle remove detectedRoles
            //for add, always use "assignedRole"
            //for remove, check assignedRoles first.
            if (target != null && ProvisioningPlan.Operation.Remove.equals(op)) {
                if (Util.isEmpty(assignments)) {
                    requestName = ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
                }
            }
            
            AttributeRequest attReq = new AttributeRequest(requestName, roleName);
            attReq.setOp(op);

            if (sunrise != null) {
                attReq.setAddDate(sunrise);
            }

            if (sunset != null) {
                attReq.setRemoveDate(sunset);
            }

            if (ProvisioningPlan.Operation.Remove.equals(op)) {
                // If we are removing an assigned role, request that a negative assignment be created.
                //This should only be set if the current roleAssignment came from a rule
                if (target != null) {
                    //Not sure how this even works with multiple assignments since we don't have assignmentIds -rap
                    for (RoleAssignment assignment : Util.safeIterable(assignments)) {
                        if (!assignment.isManual()) {
                            attReq.put(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT, true);
                        }
                    }
                }

            }


            request.add(attReq);
        }

        PlanUtil.addDeassignEntitlementsArgument(request);

        // add sunrise sunset

    }

    private void generateChangePasswordRequest(AccountRequest request, HashMap<String, String> requestMap) {

        request.setOperation(AccountRequest.Operation.Modify);

        AttributeRequest attReq = new AttributeRequest(PASSWORD_HEADER, requestMap.get(PASSWORD_HEADER));
        attReq.setOp(ProvisioningPlan.Operation.Set);

        request.add(attReq);

        // iiqtc-71 add the argument to check for the application's change password policy. If the application
        // doesn't have a change password policy it use the identity update policy by default.
        request.put(ApplicationPolicyExpander.PROVISIONING_POLICIES, Arrays.asList(ApplicationPolicyExpander.CHANGE_PASSWORD_POLICY));
    }


    /**
     * TODO: Still need to finish this one
     */
    private void generateCreateAccountRequest(AccountRequest request, HashMap<String, String> requestMap) {
        request.setOperation(AccountRequest.Operation.Create);

        Iterator<String> mapIt = requestMap.keySet().iterator();

        while(mapIt.hasNext()) {
            String key  = mapIt.next();
            if (APP_HEADER.equals(key) || IDENTITY_HEADER.equals(key) || NATIVE_HEADER.equals(key)) {
                continue;
            }
            AttributeRequest attReq = new AttributeRequest(key, requestMap.get(key));
            attReq.setOp(ProvisioningPlan.Operation.Set);
            request.add(attReq);
        }
    }

    private void generateCreateIdentityRequest(AccountRequest request, HashMap<String, String> requestMap) {
        request.setOperation(AccountRequest.Operation.Create);

        request.setNativeIdentity(requestMap.get("name"));

        Iterator<String> mapIt = requestMap.keySet().iterator();

        while(mapIt.hasNext()) {
            String key  = mapIt.next();
            // skip name attribute
            if ("name".equals(key)) {
                continue;
            }
            AttributeRequest attReq = new AttributeRequest(key, requestMap.get(key));
            attReq.setOp(ProvisioningPlan.Operation.Set);
            attReq.put("type", "string");
            if (requiredFields.contains(key)) {
                attReq.put("required", true);
            }
            request.add(attReq);
        }
    }

    private void generateModifyIdentityRequest(AccountRequest request, HashMap<String, String> requestMap) {

        request.setOperation(AccountRequest.Operation.Modify);

        request.setNativeIdentity(requestMap.get(IDENTITY_HEADER));

        Iterator<String> mapIt = requestMap.keySet().iterator();

        while(mapIt.hasNext()) {
            String key  = mapIt.next();
            if (IDENTITY_HEADER.equals(key)) {
                continue;
            }
            AttributeRequest attReq = new AttributeRequest(key, requestMap.get(key));
            attReq.setOp(ProvisioningPlan.Operation.Set);
            request.add(attReq);
        }
    }
}
