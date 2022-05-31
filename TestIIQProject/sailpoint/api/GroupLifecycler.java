/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to manage workflows on groups.
 * 
 * Author: Jeff
 *
 * There isn't much in here yet, but I kind of like having
 * the workflow interface separated from the JSF backing bean
 * like we do for RoleLifecycler.
 *
 * As with RoleLifecycler, need to think about what we're doing
 * at this level and see if we can refactor this into more generic
 * launch utilities for Workflower.  The trick seems to be standardizing
 * the input variables.
 *
 */
package sailpoint.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.workflow.IdentityLibrary;
 
public class GroupLifecycler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(GroupLifecycler.class);

    /**
     * You have to love context.
     */
    private SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public GroupLifecycler(SailPointContext context) {
        _context = context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Group Update Workflow
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start a workflow to apply a group provisioning plan.
     */
    public WorkflowSession launchUpdate(String owner, ManagedAttribute group, 
                                        ProvisioningPlan plan, String workflow, 
                                        Attributes<String,Object> ops)
        throws GeneralException {
      
        // note that groups don't have unique names, 
        // this will make one
        String groupName;
        if (group != null)
            groupName = GroupUtil.getGroupName(group);
        else {
            // maybe we should get it here consistently?
            groupName = GroupUtil.getGroupName(_context, plan);
        }

        // setup workflow launch options
        WorkflowLaunch wfl = new WorkflowLaunch();
        wfl.setTargetClass(ManagedAttribute.class);
        if (group != null) {
            wfl.setTargetId(group.getId());
            wfl.setTargetName(groupName);
        }

        if (owner == null)
            owner = _context.getUserName();
        wfl.setSessionOwner(owner);

        // this is the default workflow we'll run
        if (workflow == null)
            workflow = Configuration.WORKFLOW_MANAGED_ATTRIBUTE;
        wfl.setWorkflowRef(workflow);

        // generate a case name
        String caseName;
        if (group != null)
            caseName = "Update Group " + groupName;
        else
            caseName = "Create Group " + groupName;

        wfl.setCaseName(caseName);

        Attributes<String,Object> vars = new Attributes<String,Object>();
        vars.put(IdentityLibrary.VAR_PLAN, plan);
        if ( ops != null ) {
            vars.putAll(ops);
        }
        wfl.setVariables(vars);

        // launch a session
        Workflower wf = new Workflower(_context);
        WorkflowSession ses = wf.launchSession(wfl);

        return ses;
    }

}
