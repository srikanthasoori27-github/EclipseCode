/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to manage approvals and impact analysis on roles.
 * 
 * Author: Jeff
 *
 * Now also manages BundleArchives.
 */

package sailpoint.api;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.web.WorkflowSessionWebUtil;
import sailpoint.web.group.AccountGroupDTO;
import sailpoint.workflow.GroupLibrary;

public class AccountGroupLifecycler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(AccountGroupLifecycler.class);
    private static final String ACCOUNT_GROUP_OWNER = "owner";
    private static final String APPLICATION_OWNER = "appOwner";
    private static final String SELECTED_ID = "SelectedId";
    /**
     * You have to love context.
     */
    private SailPointContext _context;
    private Map<String, Object> _sessionMap;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public AccountGroupLifecycler(SailPointContext context, Map<String, Object> sessionMap) {
        _context = context;
        _sessionMap = sessionMap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common Workflow Launching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Common workflow launching for updates.
     * 
     * If a WorkflowCase was peristed the id is returned.
     * This may be used as a flag to indiciate that an approval
     * is pending, and to fetch the case if desired.
     *
     * The "neu" argument has the object to be udpated
     * The "invars" argument has the initial set of workflow variables.
     */    
    public WorkflowSession launchUpdate(String owner, AccountGroupDTO groupDTO, ProvisioningPlan plan, String workflow)
        throws GeneralException {      
        // setup workflow launch options
        WorkflowLaunch wfl = new WorkflowLaunch();
        wfl.setTargetClass(ManagedAttribute.class);
        wfl.setTargetId(groupDTO.getId());

        if (owner == null)
            owner = _context.getUserName();
        wfl.setSessionOwner(owner);

        // this is the default workflow we'll run
        if (workflow == null)
            workflow = Configuration.WORKFLOW_MANAGED_ATTRIBUTE;
        wfl.setWorkflowRef(workflow);

        // generate a case name
        String caseName = "Update Account Group " + groupDTO.getDisplayableValue(_context);
        wfl.setCaseName(caseName);

        Attributes<String,Object> vars = new Attributes<String,Object>();
        vars.put(GroupLibrary.VAR_PLAN, plan);
        Attributes<String, Object> arguments = plan.getArguments();
        Application app = _context.getObjectByName(Application.class, groupDTO.getApplicationName());
        Identity appOwner = app != null ? app.getOwner() : null;
        String maId = (String)this._sessionMap.get(SELECTED_ID);
        ManagedAttribute ma = this._context.getObjectById(ManagedAttribute.class, maId);
        if (ma != null) {
            Identity groupOwner = ma.getOwner();
            vars.put(ACCOUNT_GROUP_OWNER, groupOwner);
        }
        vars.put(APPLICATION_OWNER, appOwner);
        wfl.setVariables(vars);

        // launch a session
        Workflower wf = new Workflower(_context);
        WorkflowSession session = wf.launchSession(wfl);
        WorkflowSessionWebUtil sessionUtil = new WorkflowSessionWebUtil(_sessionMap);
        sessionUtil.saveWorkflowSession(session);
        
        return session;
    }    
}
