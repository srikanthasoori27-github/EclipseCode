/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Launch a workflow process on an identity.
 * 
 * Author: Jeff
 * 
 * This is an example of the new style of "event" requests
 * that are intended to be scheduled for the future and normally
 * do not do retries.  
 */

package sailpoint.request;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.Workflower;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A RequestExecutor that launches workflows.
 *
 * This is normally used with "event" style request where
 * the Request object has an owning identity.  The name of the
 * identity is passed into the workflow case along with all of the
 * request attributes. 
 * 
 * The "launcher" of the workflow can be set by putting a value
 * for Workflow.VAR_LAUNCHER in the request arguments.  
 */
public class WorkflowRequestExecutor extends AbstractRequestExecutor {

    private static final Log log = LogFactory.getLog(WorkflowRequestExecutor.class);

    /**
     * The name of the standard RequestDefinition used to schedule
     * workflow requests.  There can be others but this one will
     * always exist.
     */
    public static final String DEFINITION_NAME = "Workflow Request";

    /**
     * The name of the Workflow we're supposed to launch.
     * This may be a name, or it may be a key in the System Configuration 
     * object.
     */
    public static final String ARG_WORKFLOW = "workflow";

    /**
     * A workflow name to fall back on if ARG_WORKFLOW is a 
     * System Configuration key that isn't set.  
     */
    public static final String ARG_DEFAULT_WORKFLOW = "defaultWorkflow";

    /**
     * The desired name of the WorkflowCase.
     * If this is not set we'll try to derive one adding numeric
     * suffixes to make it unique.
     */
    public static final String ARG_CASE_NAME = "caseName";

    public WorkflowRequestExecutor() {
        super();
    }

    public void execute(SailPointContext context, 
                        Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {


        String wfname = args.getString(ARG_WORKFLOW);
        if (wfname == null) 
            throw new RequestPermanentException("Missing workflow name");

        Workflow workflow = null;
        try {
            // first try as a literal name
            workflow = context.getObjectByName(Workflow.class, wfname);
            if (workflow == null) {
                // then try mapping it through the System Configuration
                Configuration config = context.getConfiguration();
                String mapname = config.getString(wfname);
                if (mapname != null)
                    workflow = context.getObjectByName(Workflow.class, mapname);
            }
            
            if (workflow == null) {
                // if it was an unmapped sysconfig key, allow a default
                String defname = args.getString(ARG_DEFAULT_WORKFLOW);
                if (defname != null)
                    workflow = context.getObjectByName(Workflow.class, defname);
            }
        }
        catch (GeneralException e) {
            log.error(e);
        }

        // TODO: Any need to map this through sysconfig?
        if (workflow == null) {
            log.error("Unable to launch workflow : " + wfname);
            throw new RequestPermanentException("Invalid Workflow: " + wfname);
        }

        // generate a case name, if one isn't supplied I guess
        // we start with the request name and annotate it?
        String caseName = args.getString(ARG_CASE_NAME);
        if (caseName == null) {
            caseName = request.getName();
            if (caseName == null) {
                // default to the request definition, this is 
                // relatively uninteresting
                RequestDefinition def = request.getDefinition();
                if (def != null)
                    caseName = def.getName();
                else 
                    caseName = "Workflow Event";

                // identity events should have an owner, but this
                // could also be an unfocused event
                Identity owner = request.getOwner();
                if (owner != null)
                    caseName = caseName + " - " + owner.getName();
            }
        }

        log.info("Launching workflow " + workflow.getName() +
                 ": " + caseName);

        // NOTE: remove "workflow" from the arg map because it conflicts
        // with the standard "workflow" argument that we pass into all the
        // scripts.  Ideally Workflwoer should be handling this
        args.remove(ARG_WORKFLOW);

        try {
            Workflower wf = new Workflower(context);

            WorkflowLaunch launch = new WorkflowLaunch();
            launch.setWorkflow(workflow);
            launch.setCaseName(caseName);
            launch.setVariables(args);
            //Set some target info
            if(args.containsKey("containerTargetId")) {
                // In order to track if a pending request exists for a container we need to populate
                // the target class and id fields.
                launch.setTargetClass(ManagedAttribute.class);
                launch.setTargetId((String)args.get("containerTargetId"));
            }
            if(args.containsKey("identityName")) {
                QueryOptions ops = new QueryOptions();
                String idName = (String)args.get("identityName");
                ops.addFilter(Filter.eq("name", idName));
                List<String> ids = ObjectUtil.getObjectIds(context, Identity.class, ops);
                if(!Util.isEmpty(ids)) {
                    launch.setTargetClass(Identity.class);
                    launch.setTargetName(idName);
                    launch.setTargetId(ids.get(0));
                }
                
            }
            
            wf.launch(launch);
            // anything interesting to say about the launch?
        }
        catch (Exception t) {
            throw new RequestPermanentException(t);
        }

    }

}
