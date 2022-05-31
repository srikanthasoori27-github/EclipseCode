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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.commons.lang.StringUtils;

import sailpoint.api.Aggregator;
import sailpoint.api.GroupUtil;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.TargetAggregator;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.ChangeSummary;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskResult;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import static sailpoint.api.TargetAggregator.OP_DISABLE_SKIP_EMPTY_FILTER;
import static sailpoint.api.TargetAggregator.OP_PROMOTE_INHERITED;

/**
 * Workflow library containing utilities for group management.
 *
 */
public class GroupLibrary extends WorkflowLibrary {
    private static Log log = LogFactory.getLog(GroupLibrary.class);

    public static final String ARG_APPLICATION_NAME = "applicationName";

    public GroupLibrary() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Variables and Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The master ProvisioningPlan to be applied to the group.
     * Also the name of an argument.
     */
    public static final String VAR_PLAN = "plan";

    /**
     * Argument used by buildProvisioningForm to pass in the 
     * ProvisioningProject maintained by the workflow.
     */
    public static final String ARG_PROJECT = "project";

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Dig the group request out of this workflow.
     */
    public static ObjectRequest getGroupRequest(WorkflowContext wfc)
        throws GeneralException {

        ObjectRequest req = null;

        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningPlan plan = (ProvisioningPlan)args.get(VAR_PLAN);
        if (plan == null) {
            log.error("Missing provisioning plan");
        }
        else {
            List<ObjectRequest> requests = plan.getObjectRequests();
            if (requests == null || requests.size() == 0)
                log.error("Plan has no object requests");
            else {
                if (requests.size() > 1)
                    log.error("Plan has more than one object request");

                req = requests.get(0);
            }
        }
        return req;
    }

    /**
     * Return the ManagedAttribute object associated with this workflow.
     * The object is identified by the ObjectRequest in the plan, there
     * should be only one.
     */
    public static ManagedAttribute getManagedAttribute(WorkflowContext wfc) 
        throws GeneralException {

        ManagedAttribute ma = null;
        ObjectRequest req = getGroupRequest(wfc);
        if (req != null) {
            ma = ManagedAttributer.get(wfc.getSailPointContext(), req);
        }
        return ma;
    }

    /**
     * Synthesize a string that can identify this request to the user.
     */
    public static String getSummaryName(WorkflowContext wfc)
        throws GeneralException {

        // scripts expect this to be non-null
        String name = "unknown";
        ObjectRequest req = getGroupRequest(wfc);
        if (req != null) {
            String appname = req.getApplication(); 
            String objectType = req.getType();
            String value = null;
            boolean permission = false;

            SailPointContext spc = wfc.getSailPointContext();
            ManagedAttribute ma = ManagedAttributer.get(spc, req);
            if (ma != null) {
                permission = ma.isPermission();
                if (permission) {
                    value = ma.getAttribute();
                } else {
                    if ("Container".equals(ma.getType())) {
                        value = ma.getDisplayableName() + " (" + ma.getValue() + ")";
                    } else {
                        value = ma.getValue();
                    }
                }
            }
            else {
                // must be a create
                AttributeRequest ar = req.getAttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE);
                if (ar != null)
                    permission = ManagedAttribute.isPermission(Util.otoa(StringUtils.capitalize(ar.getValue().toString())));

                ar = req.getAttributeRequest(ManagedAttribute.PROV_DISPLAY_NAME);
                if (ar != null) {
                    value = Util.otoa(ar.getValue());
                    if (Util.isNullOrEmpty(value)) {
                        ar = req.getAttributeRequest(ManagedAttribute.PROV_ATTRIBUTE);
                        if (ar != null) {
                            value = Util.otoa(ar.getValue());
                        }
                    }
                }
            }

            if (permission) {
                // I18N!!
                name = appname + ": Target - " + value;
            }
            else {
                // just the group/entitlement should be enough
                // without having to drag in the attribute?
                name = appname + " " + objectType + ": " + value;
            }
        }
        return name;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Plans
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Examine the provisioning plan and derive a single String
     * that can be used as the group "name" in approvals and logging.
     *
     * @ignore
     * Technically this should be a combination of the 
     * application/referenceAttribute/groupName but provisioning plans
     * can only target referenceAttributes  declared in the Schema so
     * we'll assume that is already known.
     */
    public static Object getGroupName(WorkflowContext wfc)
        throws GeneralException {

        String name = "???";
        ObjectRequest req = getGroupRequest(wfc);
        if (req != null) {
            name = req.getApplication() + ":" + 
                GroupUtil.getGroupName(wfc.getSailPointContext(), req);
        }
        return name;
    }

    /**
     * Compile the master plan passed into the workflow into a 
     * ProvisioningProject.  
     * 
     * @ignore
     * For groups there is expected to be
     * only one ObjectRequest but we'll allow more.  Since we
     * go through the same compilation process as identity requests
     * there can in theory be Question objects left in the project,
     * but since we don't have provisioning policies for groups (yet)
     * we can ignore that aspect of the project.
     */
    public static Object compileGroupProject(WorkflowContext wfc)
        throws GeneralException {

        ProvisioningProject project = null;
        
        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        // only generate a work item if we have questions
        Object o = args.get(VAR_PLAN);
        if (!(o instanceof ProvisioningPlan)) {
            log.error("Invalid object passed as plan argument");
        }
        else {
            ProvisioningPlan plan = (ProvisioningPlan)o;

            // Pass the step arguments for provisioner options.
            // This is also where you can pass random things for script args.
            Attributes<String,Object> provArgs = 
                IdentityLibrary.getProvisionerArguments(wfc.getStepArguments());
            
            // The map passed to the constructor may contains options
            // for compilation and evaluation. These will be stored in the
            // returned project.
            Provisioner p = new Provisioner(spcon, provArgs);

            // The argument map in this method is used for "script args"
            // that are passed to any scripts or rules in the Templates
            // or Fields.  Here we use the step args for both the
            // options to the Provisioner and the script args during compilation.
            project = p.compile(plan, provArgs);
        }

        return project;
    }

    /**
     * Called by the Identity Update and LCM workflows after all the
     * provisioning forms have been completed.  Provision what remains
     * in the project.
     *
     * You cannot pass execution options here, those must have been
     * set in the call to compileProvisioningProject and thereafter
     * were stored within the project.
     *
     * @ignore
     * Besides evaluating the project this is also where we will evaluate
     * identity change triggers after provisioning.  To do this we have to 
     * get a complete copy of the identity before provisioning so that
     * we can compare it later to detect the diffs.
     */
    public static Object provisionGroupProject(WorkflowContext wfc) throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        // Project
        Object o = args.get(ARG_PROJECT);
        if (o == null)
            throw new GeneralException("Missing argument: project");

        if (!(o instanceof ProvisioningProject))
            throw new GeneralException("Invalid argument: project");
        
        ProvisioningProject project = (ProvisioningProject)o;

        // There are no script args for execution.  If you want
        // to pass arguments they have to be doen when the
        // plan is *compiled*.  This works but it would be more
        // obvoius if they could be passed here too...
        log.info("Starting project execution");
        Provisioner p = new Provisioner(wfc.getSailPointContext());
        p.execute(project);
        log.info("Finished project execution");

        // copy errors from the executors back to the workflow case
        // so they can be seen in the task result
        IdentityLibrary.assimilateProvisioningErrors(wfc, project);

        return null;
    }

    public static void aggregateSpecificTargets(WorkflowContext wfc) throws GeneralException{
        Attributes<String,Object> args = wfc.getArguments();
        TaskResult result = wfc.getTaskResult();

        List<String> pamContainersToAggregate = args.getList("pamContainersToAggregate");
        for(String pamContainerName : Util.safeIterable(pamContainersToAggregate)) {
            if(Util.isNullOrEmpty(pamContainerName)) {
                result.addMessage(new Message(Message.Type.Warn, "Unable to find the name of the target to aggregate"));
            } else {
                doSingleTargetAggregation(wfc, pamContainerName);
            }
        }
    }

    private static void doSingleTargetAggregation(WorkflowContext wfc, String pamContainerName)
            throws GeneralException {
        SailPointContext spc = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
        TaskResult result = wfc.getTaskResult();
        TaskMonitor monitor = new TaskMonitor(spc, result);

        try {
            TargetSource targetSource = null;

            //No TargetSource set on TaskDefinition, fall back to Application for backwards compatibility.
            String appName = args.getString(ARG_APPLICATION_NAME);
            args.put(OP_DISABLE_SKIP_EMPTY_FILTER, true);
            args.put(OP_PROMOTE_INHERITED, false);
            if (appName != null) {
                Application app = spc.getObjectByName(Application.class, appName);
                if (app == null) {
                    throw new Exception("Unable to find Applciation with the name [" + appName + "]");
                }

                List<TargetSource> sources = app.getTargetSources();
                if (Util.isEmpty(sources)) {
                    throw new Exception("There are no target sources defined on the application [" + appName + "]");
                } else if (Util.size(sources) > 1) {
                    throw new Exception("Application[" + appName + "] is configured with multiple TargetSources. Need to" +
                            " configure the Task with a single TargetSource.");
                } else {
                    targetSource = sources.get(0);
                }
            }

            TargetAggregator agg = null;
            if (targetSource != null) {
                agg = new TargetAggregator(spc, targetSource, args);
            } else {
                throw new Exception("Unable to find targetSource to aggregate.");
            }


            agg.setTaskMonitor(monitor);
            agg.aggregatePAMContainer(pamContainerName);
            agg.saveResults(result);

        } catch(Exception e ) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.TARGET_SRC_SCAN_FAILED, e);
            result.addMessage(msg);
        }
    }

    public static void aggregateSpecificGroup(WorkflowContext wfc) throws GeneralException {
        Attributes<String,Object> args = wfc.getArguments();
        SailPointContext spc = wfc.getSailPointContext();
        TaskResult result = wfc.getTaskResult();
        try {
            ResourceObject resourceObject = (ResourceObject) args.get("pamContainerToGroupAggregate");
            if (resourceObject == null) {
                throw new GeneralException("Unable to find the resource object to aggregate");
            }
            String appName = args.getString(ARG_APPLICATION_NAME);
            Application app = null;
            if (appName != null) {
                app = spc.getObjectByName(Application.class, appName);
            }
            if (app == null) {
                throw new GeneralException("Unable to find application to aggregate against");
            }
            Aggregator aggregator = new Aggregator(spc);
            aggregator.aggregateGroup(app, resourceObject);
            aggregator.updateResults(result);
        } catch(Exception e) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ACCOUNT_GROUP_AGGREGATION_FAIL, e);
            result.addMessage(msg);
        }
    }

    /**
     * Build a ChangeSummary object containing a model for
     * the changes being made to a ManagedAttribute that is
     * easier for JSF to deal with. 
     */
    public static Object buildGroupChangeSummary(WorkflowContext wfc) throws GeneralException {

        ChangeSummary summary = null;

        Attributes<String,Object> args = wfc.getArguments();
        SailPointContext spc = wfc.getSailPointContext();

        Object o = args.get(VAR_PLAN);
        if (!(o instanceof ProvisioningPlan)) {
            log.error("Invalid object passed as plan argument");
        }
        else {
            ProvisioningPlan plan = (ProvisioningPlan)o;
            // plan is expected to have only one thing in it
            List<ObjectRequest> requests = plan.getObjectRequests();
            if (requests != null && requests.size() > 0) {
                ObjectRequest req = requests.get(0);
                String appname = req.getApplication();
                Application app = spc.getObjectByName(Application.class, appname);
                if (app == null) {
                    log.error("Invald application name: " + appname);
                }
                else {
                    ManagedAttribute ma = null;
                    if (req.getOp() != ProvisioningPlan.ObjectOperation.Create) {
                        // same thing is needed in several places
                        // !! consider storing the database id in the 
                        // plan since we'll need to check before launching
                        // anyway
                        ma = ManagedAttributer.get(spc, req, app);
                        if (ma == null) {
                            // why wasn't this a create?
                            // what to do now?
                            // should we put these errors in the summary?
                            log.error("Unable to find target object!");
                        }
                    }
                    summary = buildChangeSummary(spc, ma, app, req);
                }
            }
        }
        
        return summary;
    }
    
    /**
     * Build a ChangeSummary from an ObjectRequest.  This is relatively 
     * general, consider moving somewhere.
     */
    private static ChangeSummary buildChangeSummary(SailPointContext spc,
                                                    ManagedAttribute src, 
                                                    Application app, 
                                                    ObjectRequest req) throws GeneralException {

        ChangeSummary summary = new ChangeSummary();

        //If null type, default to group. This is to support legacy code. -rap
        Schema schema = app.getSchema(req.getType() != null ? req.getType() : Application.SCHEMA_GROUP);

        if (src == null)
            summary.setCreate(true);

        else if (req.getOp() == ProvisioningPlan.ObjectOperation.Delete)
            summary.setDelete(true);

        boolean permission = false;

        // If type is non null and not "ManagedAttribute" or if type is null (legacy way)
        boolean group = (req.getType() == null || 
                (req.getType() != null && !req.getType().equalsIgnoreCase(ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE)));

        List<Difference> diffs = new ArrayList<Difference>();
        summary.setDifferences(diffs);

        // on creates, pull the identity attributes to the front
        if (src == null) {
            // application isn't an Attribute Request and should go first
            // !! need catalog keys and lookup in the UI
            Difference d = new Difference();
            diffs.add(d);
            d.setAttribute("Application");
            d.setNewValue(req.getApplication());

            d = new Difference();
            diffs.add(d);
            d.setAttribute("Type");
            AttributeRequest ar = req.getAttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE);
            if (ar != null) {
                permission = ManagedAttribute.isPermission(Util.otoa(ar.getValue()));
                if (permission)
                    d.setNewValue(ManagedAttribute.Type.Permission.name());
                else {
                    if (group) {
                        d.setNewValue(req.getType());
                    } else {
                        //If not a group or permission, we default the type to Entitlement
                        d.setNewValue(ManagedAttribute.Type.Entitlement.name());
                    }
                }
            }

            d = new Difference();
            diffs.add(d);
            if (permission)
                d.setAttribute("Target");
            else
                d.setAttribute("Attribute");
            ar = req.getAttributeRequest(ManagedAttribute.PROV_ATTRIBUTE);
            if (ar != null)
                d.setNewValue(Util.otoa(ar.getValue()));

            if (!permission) {
                // expect this to be here
                d = new Difference();
                diffs.add(d);
                d.setAttribute("Value");
                d.setNewValue(req.getNativeIdentity());
            }
        }

        // if we're deleting, could include some of the more
        // important attributes to give the approver some
        // context for the delete?
        // why would this be useful for delete but not update??

        // do these later
        AttributeRequest descreq = null;

        // assume they are ordered nicely, may want to sort them?
        List<AttributeRequest> atts = req.getAttributeRequests();
        if (atts != null) {
            for (AttributeRequest att : atts) {

                String attname = att.getName();
                String displayName = null;
                String oldValue = null;
                String newValue = Util.otoa(att.getValue());

                // ignore empty values on create
                // crap, we'll need filtering on he udpate
                // plan because everything comes down
                if (src == null && 
                    (newValue == null || newValue.length() == 0))
                    continue;

                // already pulled these out for create, and shouldn't
                // see them on update
                if (ManagedAttribute.PROV_ATTRIBUTE.equals(attname) ||
                    ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE.equals(attname))
                    continue;

                else if (ManagedAttribute.PROV_DESCRIPTIONS.equals(attname)) {
                    // handle these later
                    descreq = att;
                    continue;
                }
                else if (ManagedAttribute.PROV_DISPLAY_NAME.equals(attname)) {
                    displayName = "Display Name";
                    if (src != null)
                        oldValue = src.getDisplayName();
                    // this one is complicated

                }
                else if (ManagedAttribute.PROV_REQUESTABLE.equals(attname)) {
                    displayName = "Requestable";
                    if (src != null)
                        oldValue = Util.otoa(src.isRequestable());
                }
                else if (ManagedAttribute.PROV_OWNER.equals(attname)) {
                    displayName = "Owner";
                    if (src != null) {
                        Identity owner = src.getOwner();
                        if (owner != null)
                            oldValue = owner.getName();
                    }
                    // sigh, this one comes in as an id
                    if (ObjectUtil.isUniqueId(newValue)) {
                        try {
                            Identity ident = spc.getObjectById(Identity.class, newValue);
                            if (ident != null)
                                newValue = ident.getName();
                        }
                        catch (Throwable t) {
                            log.error("Unable to load identity");
                        }
                    }
                }
                else if (ManagedAttribute.PROV_SCOPE.equals(attname)) {
                    displayName = "Assigned Scope";
                    if (src != null) {
                        Scope s = src.getAssignedScope();
                        if (s != null)
                            oldValue = s.getName();
                    }
                }
                else if (ManagedAttribute.PROV_CLASSIFICATIONS.equals(attname)) {
                    displayName = "Classifications";
                    if (src != null) {
                        oldValue = Util.listToCsv(src.getClassificationDisplayNames());
                    }
                }
                else {
                    // a schema attribute or extended
                    if (schema != null) {
                        AttributeDefinition def = schema.getAttributeDefinition(attname);
                        String identAttr = schema.getIdentityAttribute();
                        if (def != null) {
                            //This is the nativeIdentity for the group. The MA won't have an attribute for this.
                            if (def.getName().equals(identAttr)) {
                                //If new value is same as the value of the MA, no need to show it in diff
                                if (Util.nullSafeEq(src != null ? Util.otoa(src.getValue()) : null, newValue, true, true))
                                    continue;
                            }
                            displayName = def.getDisplayName();
                        }
                        else {
                            // If this is an identity or a rule, make sure we are saving the name and not the id. Nobody wants
                            // to see an id in a change summary. We can assume it's a managed attribute so look in
                            // the object config there.
                            ObjectConfig maConfig = ObjectConfig.getObjectConfig(ManagedAttribute.class);
                            if (maConfig != null) {
                                ObjectAttribute objectAttr = maConfig.getObjectAttribute(attname);
                                if (objectAttr != null) {
                                    String type = objectAttr.getType();
                                    Class clz = ObjectUtil.getSailPointClass(type);
                                    if ((Identity.class.equals(clz) || Rule.class.equals(clz)) && Util.isNotNullOrEmpty(newValue)) {
                                        // Get the object by id, so that we can get the name. Simplest way to
                                        // do this, honestly. Don't think getObject() will come back with a null but if
                                        // it does just leave newValue unchanged.
                                        SailPointObject obj = spc.getObjectById(clz, newValue);
                                        if (obj != null) {
                                            newValue = obj.getName();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (src != null)
                        oldValue = Util.otoa(src.get(attname));
                }

                // UI isn't always filtering duplicates, PlanCompiler
                // will but it looks confusing to show
                if (src == null || 
                    !Util.nullSafeEq(oldValue, newValue, true, true)) {
                    if (!("Container".equals(req.getType()) && attname.startsWith("privilegedData."))) {
                        Difference d = new Difference();
                        // Difference has a displayName property but we're not using it
                        if (displayName != null)
                            d.setAttribute(displayName);
                        else
                            d.setAttribute(attname);
                        d.setOldValue(oldValue);
                        d.setNewValue(newValue);
                        diffs.add(d);
                    } else {
                        // to make the diff of privileged data changes less ugly in the UI we want to include added and removed
                        List<String> oldList = new ArrayList<>();
                        List<String> newList = new ArrayList<>();
                        int offset = 0;
                        if (Util.isNotNullOrEmpty(oldValue)) {
                            if ('[' == oldValue.charAt(0)) { // if its an array, strip off the []
                                offset = 1;
                            }
                            oldList = Arrays.asList(oldValue.substring(offset, oldValue.length() - offset).split(", "));
                        }
                        offset = 0;
                        if (Util.isNotNullOrEmpty(newValue)) {
                            if ('[' == newValue.charAt(0)) {
                                offset = 1;
                                if (Util.isNotNullOrEmpty(oldValue) && oldValue.equals(newValue.substring(offset, newValue.length() - offset))) {
                                    // New value was just an single item array that is the same as the old value.
                                    continue;
                                }
                            }
                            newList = Arrays.asList(newValue.substring(offset, newValue.length() - offset).split(", "));
                        }
                        Difference d = Difference.diff(oldList, newList);
                        if (d == null) {
                            d = new Difference();
                        }
                        if (displayName != null)
                            d.setAttribute(displayName);
                        else
                            d.setAttribute(attname);
                        d.setOldValue(oldValue);
                        d.setNewValue(newValue);
                        diffs.add(d);
                    }
                }
            }
        }

        // descriptions are awkward because they're always set 
        // as the entire map, so we have to diff those
        if (descreq != null) {
            Object o = descreq.getValue();
            if (o == null) {
                // TODO: technically we should show all
                // the old descriptions being taken away!!
            }
            else if (!(o instanceof Map)) {
                log.error("Invalid description value");
            }
            else {
                Map<String,String> newDescs = (Map<String,String>)o;
                Map<String,String> oldDescs = (src != null) ? src.getDescriptions() : null;

                Iterator<String> it = newDescs.keySet().iterator();
                while (it.hasNext()) {
                    String lang = it.next();
                    String newDesc = newDescs.get(lang);
                    String oldDesc = (oldDescs != null) ? oldDescs.get(lang) : null;

                    // since the map is full these may be the same
                    if (!Util.nullSafeEq(oldDesc, newDesc, true, true)) {
                        Difference d = new Difference();
                        diffs.add(d);
                        d.setAttribute("Description (" + lang + ")");
                        d.setOldValue(oldDesc);
                        d.setNewValue(newDesc);
                    }
                }
            }
        }

        return summary;
    }

}
