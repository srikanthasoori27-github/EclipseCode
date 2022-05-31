/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to manage workflows on identities.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkflowTarget;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;

public class IdentityLifecycler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(IdentityLifecycler.class);

    /**
     * You have to love context.
     */
    private SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityLifecycler(SailPointContext context) {
        _context = context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Update Workflow
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start a workflow session to process a provisioning plan.
     *
     * This is used both by LCM and the Define/Identities pages.
     */
    public WorkflowSession launchUpdate(String owner, Identity identity, ProvisioningPlan plan, String workflow, Attributes<String,Object> ops, List<WorkflowTarget> secondaryTargets, String caseName)
        throws GeneralException {
      
        // old - define/identities did this to "commit non role chages", 
        // not that everything is in the plan we shouldn't need this
        if ( ( identity != null ) && ( identity.getId() != null ) )
            _context.saveObject(identity);
        _context.commitTransaction();

        // setup workflow launch options
        WorkflowLaunch wfl = new WorkflowLaunch();
        wfl.setTargetClass(Identity.class);
        wfl.setTargetId(identity.getId());
        wfl.setTargetName(identity.getName());
        wfl.setSecondaryTargets(secondaryTargets);

        if (owner == null)
            owner = _context.getUserName();
        wfl.setSessionOwner(owner);

        // this is the default workflow we'll run
        if (workflow == null)
            workflow = Configuration.WORKFLOW_IDENTITY_UPDATE;
        wfl.setWorkflowRef(workflow);

        // generate a case name if not provided
        if (caseName == null) {
	        caseName = "Update Identity " + identity.getName();
	        String flow = Util.getString(ops, IdentityLibrary.VAR_FLOW);
	        if ( flow != null ) {
	            caseName = caseName + " " + flow;
	        }
        }
        wfl.setCaseName(caseName);

        // so we don't see clear text passwords in workflow trace,
        // encrypt them before launching the workflow
        // PlanEvaluator will decrypt them later just before
        // calling the connector
        PlanEvaluator pe = new PlanEvaluator(_context);
        pe.encryptSecrets(plan);

        // bug#10423, when creating new identities look for a sysconfig
        // option to give the Identity a "use by" date to prevent it from
        // being removed when pruning is enabled in the refresh task.
        // Could have done this higher in the UI or lower in the workflow,
        // but this is less disruptive.
        AccountRequest req = plan.getAccountRequest(ProvisioningPlan.APP_IIQ);
        Configuration syscon = _context.getConfiguration();
        addUseByAttributeRequest(req, syscon);

        Attributes<String,Object> vars = new Attributes<String,Object>();
        vars.put(IdentityLibrary.VAR_IDENTITY_NAME, identity.getName());
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

    public WorkflowSession launchUpdate(String owner, Identity identity, ProvisioningPlan plan, String workflow, Attributes<String,Object> ops)
        throws GeneralException {

        return launchUpdate(owner, identity, plan, workflow, ops, null, null);
    }
    
    public WorkflowSession launchUpdate(String owner, Identity identity, ProvisioningPlan plan, String workflow, Attributes<String,Object> ops, List<WorkflowTarget> secondaryTargets)
    	throws GeneralException {
    		
    	return launchUpdate(owner, identity, plan, workflow, ops, secondaryTargets, null);
    }
    		
    public WorkflowSession launchUpdate(String owner, Identity identity, ProvisioningPlan plan, String workflow, Attributes<String,Object> ops, String caseName)
            throws GeneralException {

        return launchUpdate(owner, identity, plan, workflow, ops, null, caseName);
    }
    
    public WorkflowSession launchUpdate(String owner, Identity identity, ProvisioningPlan plan)
        throws GeneralException {
        return launchUpdate(owner, identity, plan, Configuration.WORKFLOW_IDENTITY_UPDATE,null);
    }

    /**
     * Adds an attribute request to the account request setting the Use By preference if specified in passed config
     * @param accountRequest The account request to add the Use By request
     * @param config The system config
     */
    public static void addUseByAttributeRequest(AccountRequest accountRequest, Configuration config) {
        // Return quickly if we got unusable parameters
        if(accountRequest == null || config == null) {
            return;
        }
        if (accountRequest.getOp() == ProvisioningPlan.ObjectOperation.Create) {
            int days = config.getInt(Configuration.LCM_CREATE_IDENTITY_USE_BY_DAYS);
            if (days > 0) {
                AttributeRequest attributeRequest = new AttributeRequest();
                attributeRequest.setName(Identity.PRF_USE_BY_DATE);
                attributeRequest.setOp(ProvisioningPlan.Operation.Set);
                attributeRequest.setValue(Util.incrementDateByDays(new Date(), days));
                accountRequest.add(attributeRequest);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Password Intercept
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Launch password intercept workflow with application name.
     * If we can't locate the Application, log but don't throw.
     * There's nothing the plugin or the listener can do about it.
     */
    public WorkflowLaunch launchPasswordIntercept(String appname, String id, String password)
        throws GeneralException {
        return launchPasswordIntercept(appname, id, password, null);
    }
    
    /**
     * Launch a password intercept workflow.
     * This was originally in sailpoint.rest.PasswordInterceptor but we
     * factored it out so that SMListener can also use it.
     *
     * Since the password is coming infrom a plugin without access
     * to our key store, we assume that it is unencrypted.
     */
    public WorkflowLaunch launchPasswordIntercept(Application app, String id, String password)
        throws GeneralException {
        return launchPasswordIntercept(app, id, password, null);
    }
    
    /**
     * Launch password intercept workflow with application name.
     * If we can't locate the Application, log but don't throw.
     * There's nothing the plugin or the listener can do about it.
     * 
     * Allow additional variables that will eventually show up on the
     * workflowlaunch.  additionalVars can be null.
     */
    public WorkflowLaunch launchPasswordIntercept(String appname, String id, String password, Map<String, String> additionalVars)
            throws GeneralException {
        WorkflowLaunch wfl = null;
        Application app = _context.getObjectByName(Application.class, appname);
        if (app == null) 
            log.warn("Invalid application name: " + appname);
        else
            wfl = launchPasswordIntercept(app, id, password, additionalVars);

        return wfl;
    }
    
    /**
     * Launch a password intercept workflow.
     * This was originally in sailpoint.rest.PasswordInterceptor but we
     * factored it out so that SMListener can also use it.
     *
     * Since the password is coming infrom a plugin without access
     * to our key store, we assume that it is unencrypted.
     * 
     * Allow additional variables that will eventually show up on the
     * workflowlaunch.  additionalVars can be null.
     */
    public WorkflowLaunch launchPasswordIntercept(Application app, String id, String password, Map<String, String> additionalVars)
			throws GeneralException {
		WorkflowLaunch wfl = null;
		
		QueryOptions ops = new QueryOptions();
		List<Filter> filters = new ArrayList<>();
		filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", id)));
		if (!Util.isEmpty(additionalVars) && !Util.isEmpty(additionalVars.get("uuid"))) {
			log.info("UUID received through the request. Adding UUID filter.");
			String uuid = additionalVars.get("uuid");
			filters.add(Filter.ignoreCase(Filter.eq("uuid", uuid)));
		}
		ops.add(Filter.eq("application", app));
		ops.add(Filter.or(filters));

		List<Link> links = _context.getObjects(Link.class, ops);
		if (!Util.isEmpty(links)) {
			// this shouldn't happen because the app/identity
			// combination is unique
			if (links.size() > 1)
				log.error("Multiple links found for application " + app.getName() + ", identity " + id);

			Link link = links.get(0);
			Identity ident = link.getIdentity();
			if (ident == null) {
				log.error("Orphaned link found for application " + app.getName() + ", identity " + id);
			} else {
				wfl = launchWorkflow(app, link, ident, password, additionalVars);
			}
		} else {
			// don't warn, it's just something we haven't
			// aggregated yet
		}

		return wfl;
	}

    /**
     * Inner workflow launcher for password intercepts.
     * 
     * Include the ability to pass in additional variables that get set on the
     * workflow launch.
     */
    private WorkflowLaunch launchWorkflow(Application app, Link link, Identity ident, 
                                          String password, Map<String, String> additionalVars)
        throws GeneralException {

        WorkflowLaunch wfl = new WorkflowLaunch();
        wfl.setWorkflowRef(Configuration.WORKFLOW_PASSWORD_INTERCEPT);

        // anything clever we can do about the case name?
        wfl.setLauncher("PasswordIntercept");
        wfl.setTargetClass(Identity.class);
        wfl.setTargetId(ident.getId());
        wfl.setTargetName(ident.getName());

        // Might need some control here since it's visible
        // ideally the Workflow definition could have a script/Rule
        // to initialize this, can we do this from the start step?
        wfl.setCaseName("Password Intercept for " + ident.getName());

        Map<String,Object> vars = new HashMap<String,Object>();
        wfl.setVariables(vars);

        if (null != additionalVars) {
            // Add additional vars first so that the hard-coded ones can't be overwritten.
            vars.putAll(additionalVars);
        }
        vars.put("identityName", ident.getName());
        vars.put("applicationName", app.getName());
        vars.put("nativeIdentity", link.getNativeIdentity());

        // The password is not expected to be encrypted.  If it can be
        // then there needs to be some form of key exchange.  Do
        // encrypt it when launching the workflow though.
        password = _context.encrypt(password);
        vars.put("password", password);

        if (log.isInfoEnabled()) {
            log.info("Launching password intercept workflow");
            log.info(wfl.toXml());
        }

        Workflower wf = new Workflower(_context);
        wf.launch(wfl);

        return wfl;
    }

}
