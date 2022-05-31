/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class has been refactored from the IdentityLibrary class. The main method is
 * checkPolicyViolations and provides a facade to the Interrogator, Provisioner and other classes.
 */
public class InterrogatorFacade {
    
    private static final Log log = LogFactory.getLog(InterrogatorFacade.class);
    
    
    /**
     * Variable for LCM workflows that specifies the names of policies
     * to check.
     */
    public static final String VAR_POLICIES = "policies";

    /*
     * These constants have been copied from sailpoint.workflow.IdentityLibrary. Do not reference
     * IdentityLibrary directly because of the circular dependencies that would be created.
     */
    private static final String ARG_PLAN = "plan";
    private static final String VAR_IDENTITY_NAME = "identityName";
    private static final String VAR_PROJECT = "project";
    private static final String VAR_PLAN = "plan";

    /**
     * Refactored from the IdentityLibrary, this method does not modify any database objects. It performs
     * high level 'what if' scenarios to determine if a violation would occur after provisioning a ProvisioningProject.
     * @param spcon
     * @param args note that arguments must be explicitly passed as step arguments, we don't fall back to workflow variables
     * @return List Map of the policy violation summary
     * @throws GeneralException
     */
    public List<Map<String,Object>> checkPolicyViolations(SailPointContext spcon, Attributes<String,Object> args)
            throws GeneralException {
            
            Meter.enterByName("checkPolicyViolations");
            
            // Short circuit if no active policies are present
            if (!anyPoliciesPresent(spcon)) {
                return null;
            }

            // the violation summary we will eventually set and return
            List<Map<String, Object>> summary = null;

            // initialize a provisioner for simulation
            Provisioner provisioner = new Provisioner(spcon);

            // initialize an Interrogator for policy checking
            Attributes<String,Object> interrogatorArgs = new Attributes<String,Object>();
            Object policiesToCheck = args.get(VAR_POLICIES);
            if ( policiesToCheck != null) 
                interrogatorArgs.put(VAR_POLICIES, policiesToCheck);

            // This overrides the global ASYNC_CACHE_REFRESH option if you
            // need to turn it on and off per-workflow.  PolicyCache uses
            // containsKey so only set it if it is an Arg.  
            Object cacheRefresh = args.get(Configuration.ASYNC_CACHE_REFRESH);
            if (cacheRefresh != null)
                interrogatorArgs.put(Configuration.ASYNC_CACHE_REFRESH, cacheRefresh);

            Interrogator gator = new Interrogator(spcon, interrogatorArgs);
            gator.prepare();

            // build a test identity for simulation
            String identityName = args.getString(VAR_IDENTITY_NAME);
            if ( identityName == null ) 
                throw new GeneralException("Identity name is required.");
            Identity testIdentity = getTestIdentityNew(spcon, identityName);

            // get the provisioning project to apply during simulation
            ProvisioningProject project = (ProvisioningProject)args.get(VAR_PROJECT);
            if (project == null) {
                // old interface allowed a plan which must be compiled
                ProvisioningPlan plan = (ProvisioningPlan)args.get(VAR_PLAN);
                if (plan == null)
                    throw new GeneralException("Either a project or plan is required.");
                // we have historically cloned this, see comments above...
                ProvisioningPlan planCopy = new ProvisioningPlan(plan);
                planCopy.setIdentity(testIdentity);
                project = provisioner.compile(planCopy, null);
            }

            // We have historically set this obscure flag when doing
            // impact analysis on the project and I don't remember why
            // it may have been an ARM holdover or something from McDonalds.
            // What this does is cause any AttributeRequests on the
            // IIQ "detectedRoles" list to be actually applied, usually
            // they are ignored.  This feels wrong on several levels, first
            // if someone puts a detectedRoles request in the plan why
            // the hell are we not obeying it?  It is probably for the 
            // ugly McDonalds detected role recon options...it would be great
            // to simplify this but I don't have the energy right now.
            // Continue to pass this down. - jsl
            Object saveModifyDetectedRoles = project.get(PlanEvaluator.ARG_MODIFY_DETECTED_ROLES);
            project.put(PlanEvaluator.ARG_MODIFY_DETECTED_ROLES, "true");

            // bug#12046 - suppress application templates
            // There was some surprise that create templates were firing during
            // policy checking.  Technically they shouldn't be adding anything
            // that would cause a policy conflict but I suppose they could.  
            // Update policies are used for attribute sync though and those should
            // run.  Unfortunately we don't have a way to turn off just creation
            // policies.  Punting for now since we've done it this way for awhile
            // and it doesn't hurt unless there is something really expensive
            // in the polcy.
            //project.put(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES);

            try {
                // 
                // Pre-Violations
                // Run the policy scanner on the identity before we make any 
                // changes.  This is used to get a baseline set of violations so
                // we don't show violations the user already had.  
                // 
                // jsl - not sure I like this conceptually, there may still be good
                // reasons to show these, letting the LCM user fix them 
                //
                // djs - this existing ARM carry-over behavior
                //

                // first flesh out entitlements for the currently assigned roles
                testIdentity = provisioner.impactAnalysis(testIdentity);

                // then interrogate with original roles
                List<PolicyViolation> preViolations = gator.interrogate(testIdentity);

                //
                // Post-Violations
                //

                // Now apply the project and simulate provisioning
                testIdentity = getTestIdentityNew(spcon, identityName);

                /* Recompile the plan with the ignore sunrise option so we can find policy violations in
                 * requests with sunrise dates */
                ProvisioningPlan masterPlan = project.getMasterPlan();
                ProvisioningPlan masterPlanCopy = new ProvisioningPlan(masterPlan);
                Attributes<String,Object> provisionerArgs = getProvisionerArguments(args);
                provisionerArgs.put(PlanEvaluator.ARG_IGNORE_SUNRISE_DATE, true);
                /* Re-instantiate provisioner here with ignore sunrise attribute */
                provisioner = new Provisioner(spcon, provisionerArgs);
                if (cacheRefresh != null) {
                    provisioner.setArgument(Configuration.ASYNC_CACHE_REFRESH, cacheRefresh);
                }
                /* Evaluate against our temporary identity */
                masterPlanCopy.setIdentity(testIdentity);
                ProvisioningProject simulatedProject = provisioner.compile(masterPlanCopy, provisionerArgs);
                testIdentity = provisioner.impactAnalysis(testIdentity, simulatedProject);

                // Refresh detected roles based on the provisioning simulation
                Attributes<String,Object> correlatorArgs = new Attributes<String,Object>();
                if (cacheRefresh != null) {
                    correlatorArgs.put(Configuration.ASYNC_CACHE_REFRESH, cacheRefresh);
                    provisioner.setArgument(Configuration.ASYNC_CACHE_REFRESH, cacheRefresh);
                }
                EntitlementCorrelator correlator = new EntitlementCorrelator(spcon, correlatorArgs);
                correlator.analyzeIdentity(testIdentity);
                testIdentity.setDetectedRoles(correlator.getDetectedRoles());

                // Check violations
                // I don't think there is lingering state, but make sure and create
                // another gator
                gator = new Interrogator(spcon, interrogatorArgs);
                gator.setSimulating(true);
                gator.prepare();
                List<PolicyViolation> postViolations = gator.interrogate(testIdentity);
            
                // get only the new violations
                List<PolicyViolation> newViolations = getNewViolations(preViolations, postViolations);

                // convert the violation list into a Map that is easier for the
                // workflow to deal with
                // !! jsl - the workflow actually doesn't care, this is being put
                // into a WorkItem and redereed by the custom JSF page for identity
                // approvals.  Think more about generalizing this so we can deal
                // with violations in a form.  Maybe this should become another
                // ApprovalSet?
                summary = summarizeViolations(spcon, newViolations);
            }
            finally {
                // remove the test identity
                spcon.decache(testIdentity);
                // Rollback is needed here because the test identity will null id
                // causes Hibernate to barf
                spcon.rollbackTransaction();

                // restore this silly flag if the project was passed in
                project.put(PlanEvaluator.ARG_MODIFY_DETECTED_ROLES, saveModifyDetectedRoles);
            }

            Meter.exitByName("checkPolicyViolations");
            return summary;
    }
    
    /**
     * We return a fresh copy of an identity which will then be used to 
     * add/remove roles, entitlements etc to check for any violations.
     *  
     * IMP: During we have to be guaranteed that IT WILL NOT BE COMMITTED
     * while we are using this testIdentity to check for violations.
     * 
     */
    public Identity getTestIdentityNew(SailPointContext context, String name) 
        throws GeneralException {
    
        Meter.enterByName("getTestIdentityNew");
        Identity testIdentity = null;
        
        // We decache the older to make sure that we always get a fresh copy
        // SailPointContext context = wfc.getSailPointContext();
        Identity original = context.getObjectByName(Identity.class, name);
        if (original == null) {
            testIdentity = new Identity();
        } else {
            context.decache(original);
            testIdentity = context.getObjectByName(Identity.class, name);
        }        
    
        Meter.exitByName("getTestIdentityNew");
        return testIdentity;
    }

    /**
     * Given the Step arguments for one of the methods that
     * call the Provisioner, gather all the arguments related to 
     * identity refresh option and leave them in a single argument
     * "refreshOptions".
     *
     * Provisioner requires that all refresh options be passed
     * in one Map to prevent random task args from task using
     * Provisioner getting in and polluting things.  But it is more
     * convenient for workflow authors to do this:
     *
     *     <Arg name='corrrelateEntitlements' value='true'/>
     *
     * than this, which I don't think is even possible in the GWE right now.
     *
     *     <Arg name='refreshArgs'>
     *       <value>
     *         <Map>
     *           <entry key='correlateEntitlements' value='true'/>
     *         </Map>
     *       </value>
     *     </Arg>
     * 
     *
     * So id ARG_DO_REFRESH is on, then we will automatically build
     * a refreshArgs Map containing all the step args that look related
     * to refresh.
     */
    public static Attributes<String,Object> getProvisionerArguments(Attributes<String,Object> stepArgs) {
    
        Attributes<String,Object> provArgs = stepArgs;
    
        if (stepArgs.getBoolean(PlanEvaluator.ARG_DO_REFRESH)) {
            
            provArgs = new Attributes<String,Object>();
            provArgs.putAll(stepArgs);
    
            Map refreshOptions = null;
    
            // now add the step args that aren't provisioning args
            Iterator<String> it = stepArgs.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                if (key.equals(ARG_PLAN) ||
                    key.equals(VAR_IDENTITY_NAME)) {
                    // it's one of ours, ignore
                    continue;
                }
                else if (key.equals(PlanEvaluator.ARG_REFRESH_OPTIONS)) {
                    // they passed encapsulated argumets, 
                    // we could either treat these as authoritative
                    // or continue merging
                    Object o = stepArgs.getBoolean(key);
                    if (o instanceof Map) {
                        if (refreshOptions == null)
                            refreshOptions = new HashMap(); 
                        refreshOptions.putAll((Map)o);
                    }
                }
                else if (!Provisioner.isArgument(key)) {
                    if (refreshOptions == null)
                        refreshOptions = new HashMap(); 
                    refreshOptions.put(key, stepArgs.get(key));
                }
            }
    
            // this replaces the original options map if any
            provArgs.put(PlanEvaluator.ARG_REFRESH_OPTIONS, refreshOptions);
        }
    
        return provArgs;
    }

    /**
     * Summarize a list of PolicyViolation objects into a simpler
     * model to put into a work item and display.
     * ARM originally had a List of Maps with the maps containing
     * the policy name, constraint name, and constraint description
     * (which may be rule generated).  
     *
     * @ignore
     * Need to think more about how a Form-based work item would deal
     * with this.  Should we have a concrete Java model for this?
     */
    protected List<Map<String,Object>> summarizeViolations(SailPointContext spcontext, 
                                                           List<PolicyViolation> violations) 
        throws GeneralException{

        List<Map<String,Object>> summary = null;
        if ( Util.size(violations) > 0 ) {
            summary = new ArrayList<Map<String,Object>>();
            for (PolicyViolation pv : violations) {
                Policy policy = pv.getPolicy(spcontext);
                // started seeing this in some unit testes after the policy cache, why?
                if (policy == null) {
                    log.error("Invalid policy: " + pv.getPolicyName());
                    continue;
                }

                BaseConstraint constraint = pv.getConstraint(spcontext);
                Map<String,Object> map = new HashMap<String,Object>();

                if (constraint != null) {
                    String constraintDescription = constraint.getDescription();
                    if ( constraintDescription != null ) 
                        map.put("constraintDescription", constraintDescription);
                }

                map.put("policyType", policy.getType());
                map.put("policyName", pv.getPolicyName());
                map.put("ruleName", pv.getConstraintName());
                map.put("entitlements", buildViolatingEntitlementsMap(pv));
                
                Object description = pv.getDescription();
                if ( description != null ) 
                    map.put("description", pv.getDescription());

                map.put("leftBundles", Util.csvToList(pv.getLeftBundles()));
                map.put("rightBundles",  Util.csvToList(pv.getRightBundles()));
                summary.add(map);
            }
        }
        return summary;
    }
    
    /**
     * Whether any active policies are present. 
     */
    private boolean anyPoliciesPresent(SailPointContext context) throws GeneralException {
        
        boolean present = false;
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("state", Policy.State.Active));
        if (context.countObjects(Policy.class, options) > 0) {
            present = true;
        }
        
        return present;
    }

    /**
     * Given a list of "pre" and "post"  violations, return a list
     * of violations that are in the post list but not in the pre list.
     */
    private List<PolicyViolation> getNewViolations(List<PolicyViolation> preViolations,     
                                                   List<PolicyViolation> postViolations)
            throws GeneralException {

        if ( preViolations == null ) {
            return postViolations;
        }
        if ( Util.size(postViolations) == 0 ) {
            return null;
        }
        List<PolicyViolation> newViolations = new ArrayList<PolicyViolation>();
        for (PolicyViolation postViolation : postViolations) {
            boolean foundInPreList = false;
            for ( PolicyViolation preViolation : preViolations ) {
                if ( preViolation.isConstraintEqual(postViolation) ) {
                    if ( isConstraintUnchanged(preViolation, postViolation) ) {
                        foundInPreList = true;
                        break;
                    }
                }
            }
            if (!foundInPreList) {
                newViolations.add(postViolation);
            }
        }
        return newViolations;
    }
    
    /**
     * @param policy violation
     * @return list of maps containing information about violating entitlements
     */
    private List<Map<String, Object>> buildViolatingEntitlementsMap(PolicyViolation pv) {
        List<Map<String, Object>> entitlements = new ArrayList<Map<String, Object>>();
        List<IdentitySelector.MatchTerm> violatingEntitlements = pv.getViolatingEntitlements();             
        for (IdentitySelector.MatchTerm violatingEntitlement : Util.safeIterable(violatingEntitlements)) {
            Application app = violatingEntitlement.getApplication();
            Object application = app == null ? null : app.getName(); 
            Object name = violatingEntitlement.getName();
            Object value = violatingEntitlement.getValue();
            Map<String, Object> entitlement = new HashMap<String, Object>();
            entitlement.put("application", application);
            entitlement.put("name", name);
            entitlement.put("value", value);
            entitlements.add(entitlement);
        }
        return entitlements;
    }
    
    /*
     * IIQETN-249/IIQETN-5085: Make sure that the constraint was not edited, so that currently assigned
     * roles are causing a pre policy violation, that would prevent us from showing the new
     * post policy violation.
     */
    private boolean isConstraintUnchanged(PolicyViolation preViolation, PolicyViolation postViolation) {
        boolean unchanged = true;

        //First let's look for changes on the right side of the policy
        String preViolationBundles = preViolation.getRightBundles();
        String postViolationBundles = postViolation.getRightBundles();

        if (preViolationBundles != null && postViolationBundles != null) {
            unchanged =  preViolationBundles.equals(postViolationBundles);
        } 

        //Next we look for changes on Left side of the policy
        if (unchanged) {
            preViolationBundles = preViolation.getLeftBundles();
            postViolationBundles = postViolation.getLeftBundles();
            if (preViolationBundles != null && postViolationBundles != null) {
                unchanged =  preViolationBundles.equals(postViolationBundles);
            }
        }

        //Last but not least, let's check the Violating Entitlements
        if (unchanged) {
            List<MatchTerm> preMatchTerms = (List<MatchTerm>) preViolation.getArgument("ViolatingEntitlements");
            List<MatchTerm> postMatchTerms = (List<MatchTerm>) postViolation.getArgument("ViolatingEntitlements");
            if (preMatchTerms != null && postMatchTerms != null) {
                unchanged = Util.orderInsensitiveEquals(preMatchTerms, postMatchTerms);
            }
        }

        return unchanged;
    }
}
