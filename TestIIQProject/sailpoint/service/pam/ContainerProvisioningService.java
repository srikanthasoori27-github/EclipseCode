package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Source;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.service.WorkflowResultItem;
import sailpoint.service.WorkflowSessionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.PAMLibrary;

import static sailpoint.service.pam.ContainerService.OBJECT_TYPE_CONTAINER;

/**
 * Provides logic to handle submitting provisioning requests for PAM workflows
 */
public class ContainerProvisioningService {

    private final SailPointContext context;
    private final Identity requester;
    private final Locale locale;

    private static final String ATT_APPLICATION = "application";
    private static final String ATT_OWNER = "sysOwner";
    private static final String ATT_NATIVE_IDENTITY = "nativeIdentity";
    private static final String ATT_ID = "id";
    private static final String ATT_ATTRIBUTES = "Attributes";
    private static final String ATT_NAME = "name";
    private static final String ATT_TYPE = "type";
    private static final String ATT_TYPE_CONTAINER = "Container";
    private static final String ATT_DISPLAY_NAME = "displayName";
    private static final String PD_VALUE = "privilegedData.value";
    private static final String PD_DISPLAY = "privilegedData.display";
    private static final String PD_TYPE = "privilegedData.type";
    private static final String PD_REF = "privilegedData.$ref";
    private static final String OBJECT_TYPE_PRIVILEGED_DATA = "PrivilegedData";

    /**
     * Constructor.
     *
     * @param context   The SailPointContext.
     * @param requester The Identity making the provisioning request.
     */
    public ContainerProvisioningService(SailPointContext context, Identity requester, Locale locale) {
        this.context = context;
        this.requester = requester;
        this.locale = locale;
    }

    /**
     * Add a list of identities to the selected container
     *
     * @param containerId      The ID of the Target container from which to remove the identities.
     * @param identityAccounts A map of identity to account id.
     * @param permissions      A list of strings to represent the permissions that are being requested
     * @return
     * @throws GeneralException
     */
    public List<WorkflowResultItem> addIdentities(String containerId, Map<String, String> identityAccounts,
                                                  List<String> permissions) throws GeneralException {
        List<WorkflowResultItem> resultItems = new ArrayList<>();
        Target target = this.context.getObjectById(Target.class, containerId);

        for (String identityId : identityAccounts.keySet()) {
            resultItems.add(addIdentity(identityId, identityAccounts.get(identityId), permissions, target));
        }

        return resultItems;
    }

    /**
     * Add an identity from the given target container.
     * @param identityId     The ID of the Identity to add to the container.
     * @param linkId         The ID of the Link for the identity to add the permissions to.
     * @param permissions    The list of permissions to assign to the user
     * @param target         The Target container from which to remove the identity's access.
     * @return
     * @throws GeneralException
     */
    public WorkflowResultItem addIdentity(String identityId, String linkId, List<String> permissions, Target target) throws GeneralException {
        PamUtil pamUtil = new PamUtil(this.context);

        Application pamApplication = PamUtil.getApplicationForTarget(context, target);

        String workflowName = pamUtil.getPamProvisioningWorkflowName();

        ProvisioningPlan plan = this.createProvisioningPlan(identityId, linkId, permissions, target, pamApplication);

        WorkflowSession session = this.runWorkflow(workflowName, target, plan, pamApplication);

        return this.createWorkflowResultItem(session, target);
    }

    /**
     * Create a ProvisioningPlan to add the selected permissions to the given identity on the given container.
     *
     * @param identityId     The ID of the Identity to add to the container.
     * @param linkId         The ID of the Link for the identity to add the permissions to.
     * @param permissions    The list of permissions to assign to the user
     * @param target         The Target container from which to remove the identity's access.
     * @param pamApplication The Pam Application to be used for the target collector
     * @return ProvisioningPlan to add the selected permissions to the given identity on the given container.
     */
    public ProvisioningPlan createProvisioningPlan(String identityId, String linkId,
                                                   List<String> permissions, Target target, Application pamApplication) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();


        Identity identity = this.context.getObjectById(Identity.class, identityId);
        Link link = this.context.getObjectById(Link.class, linkId);
        plan.setIdentity(identity);

        AccountRequest acctReq =
                new AccountRequest(Operation.Modify, link.getApplicationName(), link.getInstance(), link.getNativeIdentity());
        plan.add(acctReq);

        TargetSource source = Util.isEmpty(pamApplication.getTargetSources()) ? null : pamApplication.getTargetSources().get(0);
        PermissionRequest permReq =
                new PermissionRequest(target.getName(),
                        ProvisioningPlan.Operation.Add,
                        permissions,
                        source != null ? source.getName() : null);
        permReq.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");
        acctReq.add(permReq);

        return plan;
    }

    /**
     * Remove a list of identities from the given container
     *
     * @param containerId The ID of the Target container from which to remove the identities.
     * @param identityIds The list of identities to remove.
     * @return List<PamIdentityDeprovisioningResultItem> A list of deprovisioning results
     */
    public List<PamIdentityDeprovisioningResultItem> removeIdentities(String containerId, List<String> identityIds, boolean isSelectAll)
            throws GeneralException {

        List<PamIdentityDeprovisioningResultItem> resultItems = new ArrayList<>();
        Target target = this.context.getObjectById(Target.class, containerId);

        // If they have selected all, get all of the identities that have direct access to the container
        if(isSelectAll) {
            ContainerService containerService = new ContainerService(context);
            containerService.setTarget(target);
            QueryOptions qo = containerService.getIdentityDirectQueryOptions();

            // Handle negative selections
            if(!Util.isEmpty(identityIds)) {
                qo.add(Filter.not(Filter.in("id", identityIds)));
            }
            Iterator<Object[]> rows = context.search(Identity.class, qo, Arrays.asList("id"));

            // Clear out the identityIds and put only the returned ones on it
            identityIds = new ArrayList<>();
            if ( rows != null ) {
                while (rows.hasNext()) {
                    Object[] row = rows.next();
                    identityIds.add((String)row[0]);
                }
            }
        }

        for (String identityId : identityIds) {
            resultItems.add(removeIdentity(identityId, target));
        }

        return resultItems;
    }

    /**
     * Remove an identity from the given target container.
     *
     * @param identityId The ID of the Identity to remove from the container.
     * @param target     The Target representing the Container.
     * @return A provisioning result item.
     */
    public PamIdentityDeprovisioningResultItem removeIdentity(String identityId, Target target) throws GeneralException {

        boolean hasEffectiveAccess = this.hasEffectiveAccess(target, identityId);

        ProvisioningPlan plan = this.createDeprovisioningPlan(identityId, target);

        PamUtil pamUtil = new PamUtil(this.context);
        Application pamApplication = PamUtil.getApplicationForTarget(context, target);
        String workflowName = pamUtil.getPamProvisioningWorkflowName();
        WorkflowSession session = this.runWorkflow(workflowName, target, plan, pamApplication);

        PamIdentityDeprovisioningResultItem result = this.createIdentityDeprovisioningResultItem(identityId, hasEffectiveAccess, session, target);
        return result;
    }

    /**
     * Create a ProvisioningPlan to deprovision all permissions that the given identity has to the given container.
     *
     * @param identityId The ID of the Identity to remove from the container.
     * @param target     The Target container from which to remove the identity's access.
     * @return A ProvisioningPlan to deprovision all permissions that the given identity has to the given container.
     */
    public ProvisioningPlan createDeprovisioningPlan(String identityId, Target target) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();

        Identity identity = this.context.getObjectById(Identity.class, identityId);
        plan.setIdentity(identity);

        ContainerService svc = getContainerService(target);
        Map<Link, List<Permission>> permissionsByLink = svc.getDirectPermissionsForIdentityOnTarget(identityId);

        for (Map.Entry<Link, List<Permission>> entry : permissionsByLink.entrySet()) {
            Link link = entry.getKey();
            List<Permission> permissions = entry.getValue();

            AccountRequest acctReq =
                    new AccountRequest(Operation.Modify, link.getApplicationName(), link.getInstance(), link.getNativeIdentity());
            plan.add(acctReq);

            for (Permission perm : permissions) {
                PermissionRequest permReq =
                        new PermissionRequest(target.getName(),
                                ProvisioningPlan.Operation.Remove,
                                perm.getRightsList(),
                                perm.getAggregationSource());

                // Add a flag so that AttributeAssignments are processed with this request
                permReq.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");

                acctReq.add(permReq);
            }
        }

        return plan;
    }

    /**
     * Quick utility method for determining if an identity has effective access to a given target.
     *
     * @param target     The Target to check.
     * @param identityId The id of the identity whom we are looking at
     * @return Whether the user has effective access or not
     */
    public boolean hasEffectiveAccess(Target target, String identityId) throws GeneralException {
        ContainerService svc = getContainerService(target);
        QueryOptions qo = svc.getIdentityEffectiveQueryOptions();
        qo.add(Filter.eq("id", identityId));
        int count = this.context.countObjects(Identity.class, qo);
        return count > 0;
    }

    /**
     * Add privileged data to a container managed attribute and run an account group agg on it.
     * @param containerId
     * @param privilegedItems
     * @throws GeneralException
     */
    public void addPrivilegedItems(String containerId, List<Map> privilegedItems) throws GeneralException {
        Target target = this.context.getObjectById(Target.class, containerId);
        if (target == null) {
            throw new GeneralException("Container not found.");
        }
        Application application = PamUtil.getApplicationForTarget(context, target);
        if (application == null) {
            throw new GeneralException("No application found for container");
        }
        ManagedAttribute ma =
                ManagedAttributer.get(context, application.getId(), false, null, target.getNativeObjectId(), OBJECT_TYPE_CONTAINER);

        PamPrivilegedDataSuggestService suggestService = new PamPrivilegedDataSuggestService(context, requester, containerId, null);
        Iterator<ManagedAttribute> validPrivDataIterator = suggestService.getPrivilegedItemsForContainer();
        List<String> validPrivDataValues = new ArrayList<>();
        while(validPrivDataIterator.hasNext()) {
            ManagedAttribute privDataMA = validPrivDataIterator.next();
            validPrivDataValues.add(privDataMA.getValue());
        }
        // need to build stuff up for the update container request
        if (ma != null) {
            List<String> values = getManagedAttributeList(ma, PD_VALUE);
            List<String> displays = getManagedAttributeList(ma, PD_DISPLAY);
            List<String> types = getManagedAttributeList(ma, PD_TYPE);
            List<String> refs = getManagedAttributeList(ma, PD_REF);

            for (Map privilegedItemToAdd : privilegedItems) {
                String value = (String)privilegedItemToAdd.get("id");
                if (values.contains(value)) {
                    // don't try to add priv data the the container already has...
                    continue;
                }
                if (!validPrivDataValues.contains(value)) {
                    throw new GeneralException("One or more of the requested privileged items can not be assigned to the container");
                }
                ManagedAttribute pd = ManagedAttributer.get(context, application.getId(), false, null, value, OBJECT_TYPE_PRIVILEGED_DATA);
                if (pd == null) {
                    throw new GeneralException("Invalid privileged data value");
                }
                values.add(pd.getValue());
                displays.add(pd.getDisplayName());
                types.add((String)pd.getAttribute(ATT_TYPE));
            }

            //build attributes for plan
            updateContainerPrivilegedData(application, ma, values, displays, types, refs);
        } else {
            throw new GeneralException("Container managed attribute not found.");
        }
    }

    /**
     * Remove privileged Items from an existing container.
     * @param containerId
     * @param privilegedItems A list of the value params for privilegedItems
     * @throws GeneralException
     */
    public void removePrivilegedItems(String containerId, List<String> privilegedItems, boolean isSelectAll) throws GeneralException {
        Target target = this.context.getObjectById(Target.class, containerId);
        if (target == null) {
            throw new GeneralException("Container not found.");
        }
        Application application = PamUtil.getApplicationForTarget(context, target);
        if (application == null) {
            throw new GeneralException("No application found for container");
        }
        ManagedAttribute ma =
                ManagedAttributer.get(context, application.getId(), false, null, target.getNativeObjectId(), OBJECT_TYPE_CONTAINER);

        // need to build stuff up for the update container request
        if (ma != null) {
            if (!isSelectAll) {
                List<String> values = getManagedAttributeList(ma, PD_VALUE);
                List<String> displays = getManagedAttributeList(ma, PD_DISPLAY);
                List<String> types = getManagedAttributeList(ma, PD_TYPE);
                List<String> refs = getManagedAttributeList(ma, PD_REF);
                for (String privilegedItemValue : privilegedItems) {
                    int index = values.indexOf(privilegedItemValue);
                    if (index > -1) {
                        values.remove(index);
                        if (displays.size() > index) {
                            displays.remove(index);
                        }
                        if (types.size() > index) {
                            types.remove(index);
                        }
                        if (refs.size() > index) {
                            refs.remove(index);
                        }
                    }
                }
                updateContainerPrivilegedData(application, ma, values, displays, types, refs);
            } else {
                // for select all we want to remove all privileged items from the container
                List<String> emptyList = new ArrayList<>();
                updateContainerPrivilegedData(application, ma, emptyList, emptyList, emptyList, emptyList);
            }
        } else {
            throw new GeneralException("Container managed attribute not found.");
        }
    }

    private void updateContainerPrivilegedData(Application application, ManagedAttribute ma, List<String> values, List<String> displays, List<String> types, List<String> refs) throws GeneralException {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ATT_APPLICATION, application.getName());
        attributes.put(ATT_ID, ma.getId());
        attributes.put(ATT_NATIVE_IDENTITY, ma.getValue());
        Map<String, Object> innerAttributes = new HashMap<>();
        innerAttributes.putAll(ma.getAttributes());
        innerAttributes.put(PD_VALUE, values);
        innerAttributes.put(PD_DISPLAY, displays);
        innerAttributes.put(PD_TYPE, types);
        innerAttributes.put(PD_REF, refs);
        attributes.put(ATT_ATTRIBUTES, innerAttributes);

        String containerName = ma.getAttribute(ATT_DISPLAY_NAME) != null ? (String)ma.getAttribute(ATT_DISPLAY_NAME) : ma.getValue();
        ResourceObject resourceObject = new ResourceObject(ma.getValue(), containerName, ATT_TYPE_CONTAINER, innerAttributes);

        // update the managed attribute
        ProvisioningPlan plan =  buildContainerPlan(attributes, requester);
        String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_MANAGED_ATTRIBUTE);
        String requestName = "Update Container " + containerName;
        Map launchArgsMap = new HashMap();
        launchArgsMap.put("plan", plan);
        launchArgsMap.put("applicationName", application.getName());
        // add pamContainerToGroupAggregate and set it to the container ResourceObject so that the
        // workflow will run an account group aggregation for that container
        launchArgsMap.put("pamContainerToGroupAggregate", resourceObject);
        launchArgsMap.put("containerTargetId", ma.getId());
        launchArgsMap.put("owner", ma.getOwner());
        launchArgsMap.put("appOwner", application.getOwner());
        checkExistingWorkflows(ma.getId(), containerName);
        launchContainerProvisioningWorkflow(context, workflowName, requestName,
                launchArgsMap, plan, requester);
    }

    /**
     * Create a container managed attribute and run a target agg
     * @param attributes
     * @throws GeneralException
     */
    public void createContainer(Map<String, Object> attributes) throws GeneralException {
        Map<String, Object> attributeMap = (Map<String, Object>)attributes.get(ATT_ATTRIBUTES);
        String containerName = attributeMap.get(ATT_NAME) != null ? attributeMap.get(ATT_NAME).toString() : null;
        if (containerName == null) {
            throw new GeneralException(Internationalizer.getMessage(MessageKeys.UI_PAM_NEW_CONTAINER_NAME_REQUIRED,
                    locale));
        }
        String appName = attributes.get(ATT_APPLICATION) != null ? attributes.get(ATT_APPLICATION).toString() : null;
        if (appName == null) {
            throw new GeneralException(Internationalizer.getMessage(MessageKeys.UI_PAM_NEW_CONTAINER_APP_REQUIRED,
                    locale));
        }
        Application app = context.getObjectByName(Application.class, appName);
        if (app == null) {
            throw new GeneralException(Internationalizer.getMessage(MessageKeys.UI_PAM_NEW_CONTAINER_APP_NOT_FOUND,
                    locale));
        }

        //container name requires to be unique
        QueryOptions ops = new QueryOptions();
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq(ATT_APPLICATION, app));
        filters.add(Filter.eq(ATT_TYPE, ATT_TYPE_CONTAINER));
        ops.add(Filter.and(filters));
        List<ManagedAttribute> attrs = context.getObjects(ManagedAttribute.class, ops);
        for(ManagedAttribute attr : attrs) {
            if (containerName.equals(attr.getAttribute(ATT_NAME))) {
                throw new GeneralException(Internationalizer.getMessage(MessageKeys.UI_PAM_NEW_CONTAINER_NAME_NOT_UNIQUE,
                        locale));
            }
        }

        // create the managed attribute
        ProvisioningPlan plan =  buildContainerPlan(attributes, requester);
        String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_MANAGED_ATTRIBUTE);
        String requestName = "Create Container " + containerName;
        Map launchArgsMap = new HashMap();
        launchArgsMap.put("plan", plan);
        launchArgsMap.put("applicationName", appName);
        // add pamContainersToAggregate and set it to the newly requested container so that the
        // workflow will run a target aggregation for that target
        launchArgsMap.put("pamContainersToAggregate", containerName);
        launchArgsMap.put("appOwner", app.getOwner());
        launchContainerProvisioningWorkflow(context, workflowName, requestName,
                launchArgsMap, plan, requester);
    }

    /**
     * Builds a plan for creating or updating a container managed attribute
     * @param attributes
     * @param loggedInUser
     * @return
     */
    private ProvisioningPlan buildContainerPlan(Map<String, Object> attributes, Identity loggedInUser) {
        ProvisioningPlan.ObjectRequest or = new ProvisioningPlan.ObjectRequest();
        or.setApplication(Util.getString(attributes, ATT_APPLICATION));
        or.setType(OBJECT_TYPE_CONTAINER);
        or.setNativeIdentity(Util.trimnull(Util.getString(attributes, ATT_NATIVE_IDENTITY)));
        String id = Util.getString(attributes, ATT_ID);
        if (id == null || id.equals("new") || id.trim().length() == 0) {
            or.setOp(ProvisioningPlan.ObjectOperation.Create);
        } else {
            or.setOp(ProvisioningPlan.ObjectOperation.Modify);
        }

        Map<String, Object> attributeMap = (Map<String, Object>)attributes.get(ATT_ATTRIBUTES);
        if (attributeMap.get(ManagedAttribute.PROV_DISPLAY_NAME) == null && attributeMap.get(ATT_DISPLAY_NAME) != null) {
            attributeMap.put(ManagedAttribute.PROV_DISPLAY_NAME, attributeMap.get(ATT_DISPLAY_NAME));
        }
        List<ProvisioningPlan.AttributeRequest> standardAttributeRequests = getStandardAttributeRequests(attributeMap);
        or.addAll(standardAttributeRequests);

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setSource(Source.GroupManagement);
        plan.addRequester(loggedInUser);
        plan.addRequest(or);

        return plan;
    }

    private List<ProvisioningPlan.AttributeRequest> getStandardAttributeRequests(Map<String, Object> attributes) {
        List<ProvisioningPlan.AttributeRequest> attributeRequests = new ArrayList<ProvisioningPlan.AttributeRequest>();
        for(String key : attributes.keySet()) {
            switch(key) {
                case ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE:
                    Object type = attributes.get(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE);
                    attributeRequests.add(new ProvisioningPlan.AttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE, ProvisioningPlan.Operation.Set, type == null ? "null" : type.toString()));
                    break;

                default:
                    attributeRequests.add(new ProvisioningPlan.AttributeRequest(key, ProvisioningPlan.Operation.Set, attributes.get(key)));
                    break;
            }
        }
        return attributeRequests;
    }

    private void launchContainerProvisioningWorkflow(SailPointContext context,
                                                     String workflowName, String requestName, Map launchArgsMap,
                                                     ProvisioningPlan plan, Identity owner) throws GeneralException{

        Workflow workflow = context.getObjectByName(Workflow.class, workflowName);
        if (workflow == null) {
            throw new GeneralException("Unknown workflow: " + workflowName);
        }

        // Use the Request Launcher
        Request req = new Request();
        RequestDefinition reqdef = context.getObjectByName(
                RequestDefinition.class, "Workflow Request");
        req.setDefinition(reqdef);
        Attributes allArgs = new Attributes();
        allArgs.put("workflow", workflow.getName());

        long current = System.currentTimeMillis();
        allArgs.put("requestName", requestName);
        allArgs.putAll(launchArgsMap);
        req.setEventDate(new Date(current));

        allArgs.put("launcher", owner.getName());
        req.setOwner(owner);
        req.setName(requestName);
        req.setAttributes(reqdef, allArgs);

        // Launch the work flow via the request manager.
        RequestManager.addRequest(context, req);
        if (reqdef != null && context != null) {
            context.decache(reqdef);
        }
        if (owner != null && context != null) {
            context.decache(owner);
        }
    }

    /**
     * Create the deprovisioning result item from the workflow session and the identity
     *
     * @param identityId         The id of the identity that we are deprovisioning
     * @param hasEffectiveAccess Whether the identity has effective access or not
     * @param workflowSession    The session from the workflow we created
     * @param target             The Target container from which to remove the identity's access.
     * @return
     * @throws GeneralException
     */
    private PamIdentityDeprovisioningResultItem createIdentityDeprovisioningResultItem(String identityId, boolean hasEffectiveAccess,
                                                                                       WorkflowSession workflowSession, Target target)
            throws GeneralException {

        PamIdentityDeprovisioningResultItem resultItem =
                new PamIdentityDeprovisioningResultItem(this.createWorkflowResultItem(workflowSession, target));

        resultItem.setIdentityId(identityId);
        resultItem.setHasEffectiveAccess(hasEffectiveAccess);
        // If the user has effective access, add their group names to the result
        if (hasEffectiveAccess) {
            Identity identity = this.context.getObjectById(Identity.class, identityId);
            resultItem.setIdentityDisplayName(identity.getDisplayableName());
            resultItem.setGroups(this.getIdentityGroupsOnContainer(identityId, target));
        }

        return resultItem;
    }

    /**
     * Create a workflow result item out of the workflow session
     * @param workflowSession    The session from the workflow we created
     * @param target             The Target container from which to add the identity's access.
     * @return
     * @throws GeneralException
     */
    private WorkflowResultItem createWorkflowResultItem(WorkflowSession workflowSession, Target target)
            throws GeneralException {


        WorkflowLaunch launch = workflowSession.getWorkflowLaunch();
        String status = launch == null ? null : launch.getStatus();
        WorkflowSessionService sessionService = new WorkflowSessionService(context, null, workflowSession);
        String requestName = sessionService.getIdentityRequestName();
        WorkItem wfWorkItem = workflowSession.getWorkItem();
        String workItemType = null;
        String workItemId = null;
        if (wfWorkItem != null) {
            workItemType = wfWorkItem.getType().toString();
            workItemId = wfWorkItem.getId().toString();
        }
        List<Message> messages = workflowSession.getLaunchMessages();
        WorkflowResultItem resultItem = new WorkflowResultItem(status,
                WorkflowSessionService.stripPadding(requestName), workItemType, workItemId, messages);

        return resultItem;
    }

    /**
     * Get the list of groups that the user belongs to that have access to the container.  This allows us to display
     * a modal to the user telling them that they need to remove the user from these groups in order to fully
     * deprovision them.
     *
     * @param identityId The id of the identity we are deprovisioning
     * @return A list of names of groups that they belong to
     * @throws GeneralException
     */
    private List<String> getIdentityGroupsOnContainer(String identityId, Target target) throws GeneralException {
        List<String> groups = new ArrayList<String>();
        ContainerService containerService = new ContainerService(this.context);
        containerService.setTarget(target);
        QueryOptions qo = containerService.getIdentityEffectiveQueryOptions(IdentityEntitlement.class);
        qo.add(Filter.eq("identity.id", identityId));
        List<String> props = Arrays.asList("ManagedAttribute.id");

        Iterator<Object[]> it = this.context.search(IdentityEntitlement.class, qo, props);
        if (it != null) {
            while (it.hasNext()) {
                Object[] row = it.next();
                String id = (String) row[0];
                ManagedAttribute ma = this.context.getObjectById(ManagedAttribute.class, id);

                // The name of the group should always come from the group that grants membership.
                groups.add(ma.getDisplayableName());
            }
        }
        return groups;
    }

    /**
     * Run the workflow
     *
     * @param name The name of the workflow
     * @param target  The target that we are provisioning on
     * @param plan The plan we are submitting
     * @return
     * @throws GeneralException
     */
    private WorkflowSession runWorkflow(String name, Target target, ProvisioningPlan plan, Application pamApplication)
            throws GeneralException {

        ManagedAttribute ma =
                ManagedAttributer.get(this.context, pamApplication, false, null, target.getNativeObjectId(), OBJECT_TYPE_CONTAINER);

        Attributes<String, Object> ops = new Attributes<>();
        ops.put(PAMLibrary.ARG_CONTAINER_NAME, target.getName());
        ops.put(PAMLibrary.ARG_CONTAINER_DISPLAY_NAME, ContainerService.getTargetDisplayName(target, ma));
        if (ma != null && ma.getOwner() != null) {
            ops.put(PAMLibrary.ARG_CONTAINER_OWNER_NAME, ma.getOwner().getName());
        }

        IdentityLifecycler cycler = new IdentityLifecycler(this.context);
        return cycler.launchUpdate(this.requester.getName(), plan.getIdentity(), plan, name, ops);
    }

    /**
     * Check for any incomplete workflow case objects with type ManagedAttribute and the given targetId
     * Throw an error if one exists
     * @param targetId
     */
    private void checkExistingWorkflows(String targetId, String containerName) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("targetId", targetId));
        qo.add(Filter.eq("targetClass", "ManagedAttribute"));
        int pendingWorkflows = context.countObjects(WorkflowCase.class, qo);
        if (pendingWorkflows > 0) {
            Message errMsg = new Message(MessageKeys.PAM_CONTAINER_PENDING_REQUEST, containerName);
            throw new GeneralException(errMsg);
        }
    }

    /**
     * Return a ContainerService for the given target.
     */
    private ContainerService getContainerService(Target target) {
        ContainerService svc = new ContainerService(this.context);
        svc.setTarget(target);
        return svc;
    }

    /**
     * Fetch a string list value from a managed attribute and return an empty list if the attribute doesn't exist
     * @param ma
     * @param attrName
     * @return
     */
    private List<String> getManagedAttributeList(ManagedAttribute ma, String attrName) {
        List<String> out = Util.otol(ma.getAttribute(attrName));
        if (out == null) {
            out = new ArrayList<>();
        }
        return out;
    }
}
