/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * Utility class to process ProvisioningProjects and ProvisioningPlans.
 * This can be used directly by the certification and identity 
 * editing classes, as well as plugged in under Identitizer.
 * 
 * Author: Jeff
 *
 * Provisioning is split into three phases:
 *
 *    - compilation
 *    - template completion (aka provisioning forms)
 *    - evaluation
 *
 * Compilation is a copmlex process performed by PlanCompiler.
 * Among other things it partitions the source plan into
 * several plans for each IntegrationExecutor, and determines
 * what the effect of IIQ role assignment will be on application 
 * accounts.
 *
 * The reuslt of compilation is a ProvisioningProject that
 * contains the partitioned plans and other state.
 *
 * Compilation may result in a set of "missing fields" from account
 * templates that need to be obtained interactively.  In this case
 * a list of Question objects will be left in the project.  The component
 * using Provisioner is responsible for presenting the question fields
 * and putting the result in the Question object.
 *
 * When the project has no more unanswered questions it may be evaluted
 * by the PlanEvalator.
 *
 * See PlanCompiler and PlanEvaluator for more details.
 * In 5.0 the sailpoint.provisioning package was added to start holding
 * a growing number of classes related to provisioning.  Unfortunately
 * we still have to maintain sailpoint.api.Provisioner as the point of
 * contact since so much uses that.  Try to factor out most of what is
 * in here into internal engine classes in sailpoint.provisioning.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeMetaData;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ExpansionItem;
import sailpoint.object.ExpansionItem.Cause;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Question;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Utility class to process ProvisioningProjects and ProvisioningPlans.
 */
public class Provisioner {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    // 
    //////////////////////////////////////////////////////////////////////

    //
    // Task arguments we recognize, most are passed along to the
    // compiler and evalutor.
    //
    // We don't need arguments for noRoleProvisioning, assigner, and source
    // because these aren't used in background tasks.  Tasks will only
    // be using one of the two reconcile methods.  For provisioning
    // during cube refresh, the EntitlementCorrelator has already 
    // managed the RoleAssignment list.
    //
    
    /**
     * A special option recognized by the role reconciliation methods.
     * It will cause all currently detected roles to be removed and
     * they will only be retained if there is an assignment that requires
     * or permits them.
     *
     * @ignore
     * This was requested by and as far as is known only used by McD.
     */
    public static final String ARG_DETECTED_ROLE_RECONCILIATION =
        "detectedRoleReconciliation";

    /**
     * A special option recognized by the role reconciliation methods.
     * It will cause all detected roles that are permitted by the current
     * assignments to be removed. They will be retained only if there
     * is an assignment that requires them.
     *
     * @ignore
     * This was requested by and as far as is known only used by McD.
     */
    public static final String ARG_RESET_PERMITTED_ROLES =
        "resetPermittedRoles";

    /**
     * A special option recognized by the role reconciliation methods.
     * It will cause all entitlements to be removed from the Identity
     * and will only retain them if they are required by the assigned
     * roles.  Essentially this brings the Identity into strict compliance
     * with the assignment model. Yes, it is very dangerous.
     *
     * @ignore
     * This was requested by and as far as is known only used by
     * Northern Trust.
     */
    public static final String ARG_FULL_RECONCILIATION =
        "fullReconciliation";
    
    /**
     * A special option reconciled by the role reconciliation methods.
     * It will cause role reconciliation requests to be added to the
     * project only if there was at least one change to the assignment list.
     * This was used for a brief period of time to make the refresh task
     * not generate duplicate work items or trouble tickets each time the
     * task ran if the assignments had not changed since the last run.
     * Bug 6063. This became largely obsolete with the addition of
     * ProvisioningRequests, but it can still occasionally be used as
     * a performance optimization.
     */
    public static final String ARG_PROVISION_ROLES_IF_CHANGED =
        "provisionRolesIfChanged";

    /**
     * A special option recognized by the role reconciliation methods.
     * It enables an optimization where a role assignment will not
     * be added to the reconciliation project if it is not new. The
     * effect is similar to provisionRolesIfChanged but more selective.
     * This option is recommended to improve performance in identity
     * refresh tasks where entitlement correlation is enabled but
     * provisioning is not enabled  Since now the Provisioner is used
     * to update target memory, it is faster to only use it for
     * new assignments, old assignments can reuse their old targets.
     */
    public static final String ARG_OPTIMIZE_RECONCILIATION = 
        "optimizeReconciliation";

    /**
     * The names of all Provisioner arguments.
     */
    public static final String[] ARG_NAMES = {
        ARG_DETECTED_ROLE_RECONCILIATION,
        ARG_RESET_PERMITTED_ROLES,
        ARG_FULL_RECONCILIATION,
        ARG_PROVISION_ROLES_IF_CHANGED,
        ARG_OPTIMIZE_RECONCILIATION
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Provisioner.class);

    /**
     * The source of all knowledge.
     */
    SailPointContext _context;

    /**
     * Provisioning options.
     * Some of these might have been taken from task launch args,
     * some might have been set by the caller.  Either way all options 
     * are managed in a map so they can be transfered into
     * a ProvisioningProject for storage.
     */
    Attributes<String,Object> _arguments;

    /**
     * The project we are managing.
     */
    ProvisioningProject _project;

    /**
     * Temporary work-around for reconcile used in various workflows.
     * If this is set a transaction lock around
     * the body of reconcile() is obtained to ensure that more than one 
     * role Request cannot be processed at the same time.
     * Need to revisit this and think more about how locking
     * interacts with the two-phase provisioning process.
     */
    boolean _transactionLock;

    //
    // Transient runtime fields
    // These can accumulate state to preserve them once they have been created.
    //

    PlanCompiler _compiler;
    PlanEvaluator _evaluator;

    //
    // Statistics for the TaskResult when Provisioner is used during refresh
    //

    /**
     * Number of role assignments that have been reconciled in reconcileAutoAssignments.
     */
    public static final String RET_ASSIGNMENTS_RECONCILED = "assignmentsReconciled";
    int _assignmentsReconciled;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Accessors
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Constructor used when using the older methods that
     * do not expose projects.
     */
    public Provisioner(SailPointContext con) {
        _context = con;
        _arguments = new Attributes<String,Object>();
    }

    /**
     * Constructor used after a project has been at least partially compiled.
     */
    public Provisioner(SailPointContext con, ProvisioningProject proj) {
        _context = con;
        _project = proj;
        // we shouldn't be changing them, but be safe
        _arguments = proj.getAttributes();
    }

    /**
     * Constructor used when creating a new provisioning project
     * and initializing it with task arguments.  
     *
     * @ignore
     * Since task arguments can contain all sorts of crap we don't need,
     * be selective about what we pull in.  Defer to the compiler and
     * evaluator since they know what they want.
     */
    public Provisioner(SailPointContext con, Attributes<String,Object> args) {
        _context = con;
        _arguments = new Attributes<String,Object>();

        transferArgs(args, ARG_NAMES);
        transferArgs(args, PlanCompiler.ARG_NAMES);
        transferArgs(args, PlanEvaluator.ARG_NAMES);
    }

    /**
     * Transfer only the interesting arguments from a task arg map
     * into the project.
     */
    private void transferArgs(Attributes<String,Object> args,
                              String[] names) {

        if (args != null && names != null) {
            for (int i = 0 ; i < names.length ; i++) {
                String name = names[i];
                // don't trash an existing value if there is no entry
                // in the task arg map
                if (args.containsKey(name)) {
                    Object value = args.get(name);
                    _arguments.putClean(name, value);
                }
            }
        }
    }

    /**
     * Return true if this is an argument that Provisioner will recognize so
     * they can be partitioned.
     *
     * @ignore
     * Sort of kludgey utility used by things that make Provisioners
     * and have a noisy set of input arguments.  
     */
    static public boolean isArgument(String name) {

        boolean is = isArgument(name, ARG_NAMES);
        if (!is)
            is = isArgument(name, PlanCompiler.ARG_NAMES);
        if (!is)
            is = isArgument(name, PlanEvaluator.ARG_NAMES);
        
        return is;
    }

    static private boolean isArgument(String name, String[] names) {
        boolean is = false;
        if (name != null && names != null) {
            for (int i = 0 ; i < names.length ; i++) {
                if (name.equals(names[i])) {
                    is = true;
                    break;
                }
            }
        }
        return is;
    }

    /**
     * Transfer the value passed from one of the option
     * setter methods into the argument map.
     */
    public void setArgument(String name, Object o) {
        
        if (_project != null) {
            // hmm, we're normally not supposed to be changing
            // things after the project has been compiled, should
            // we allow this or ignore it?
            log.warn("setArgument called after project compiled");
        }

        _arguments.putClean(name, o);
    }

    //
    // Local options
    //

    /**
     * Set whether to use a transaction lock or not.
     */
    public void setTransactionLock(boolean b) {
        _transactionLock = b;
    }

    /**
     * Special option.
     * 
     * @ignore for Mcd.
     */
    public void setDetectedRoleReconciliation(boolean b) {
        setArgument(ARG_DETECTED_ROLE_RECONCILIATION, b);
    }

    //
    // PlanCompiler options
    //

    /**
     * Used by RemediationManager to prevent removal of detected roles.
     */
    public void setPreserveDetectedRoles(boolean b) {
        setArgument(PlanCompiler.ARG_PRESERVE_DETECTED_ROLES, b);
    }

    /**
     * Used by RemediationManager to disable deprovisioning of inherited
     * roles.  
     */
    public void setNoInheritedRoleDeprovisioning(boolean b) {
        setArgument(PlanCompiler.ARG_NO_INHERITED_ROLE_DEPROVISIONING, b);
    }

    /**
     * Toggle whether roles are expanded.
     * @ignore Used by unit tests.
     */
    public void setNoRoleExpansion(boolean b) {
        setArgument(PlanCompiler.ARG_NO_ROLE_EXPANSION, b);
    }

    /**
     * Enable or disable expanding creation templates.
     */
    public void setNoCreateTemplates(boolean b) {
        setArgument(PlanCompiler.ARG_NO_CREATE_TEMPLATES, b);
    }

    //
    // PlanEvaluator options
    //

    /**
     * Set the name of the identity making the provisioning request.
     */
    public void setRequester(String s) {
        setArgument(PlanEvaluator.ARG_REQUESTER, s);
    }

    /**
     * Set the name of the identity assigning the items in the project.
     */
    public void setAssigner(String s) {
        setArgument(PlanEvaluator.ARG_ASSIGNER, s);
    }

    /**
     * Set the source of the provisioning change - for example "rule", etc...
     */
    public void setSource(String s) {
        setArgument(PlanEvaluator.ARG_SOURCE, s);
    }

    /**
     * Set the source of the provisioning change.
     */
    public void setSource(Source s) {
        setSource(s.toString());
    }

    /**
     * Set whether to optimistically provision the changes to the identity
     * and links when the plan is evaluated. If false, this requires
     * confirmation from the connector before applying the changes.
     */
    public void setOptimisticProvisioning(boolean b) {
        setArgument(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING, b);
    }

    /**
     * Enable or disable locking of the identity being evaluated.
     */
    public void setNoLocking(boolean b) {
        setArgument(PlanEvaluator.ARG_NO_LOCKING, b);
    }

    /**
     * Set whether to only apply the plan to the identity and links when
     * evaluating.
     */
    public void setLocalUpdate(boolean b) {
        setArgument(PlanEvaluator.ARG_LOCAL_UPDATE, b);
    }

    /**
     * Set whether links should be updated when evaluating the project.
     */
    public void setNoLinkUpdate(boolean b) {
        setArgument(PlanEvaluator.ARG_NO_LINK_UPDATE, b);
    }

    /**
     * Set whether to perform an identity refresh after evaluating the project.
     */
    public void setDoRefresh(boolean b) {
        setArgument(PlanEvaluator.ARG_DO_REFRESH, b);
    }

    /**
     * Set the options to use when refreshing if {@link #setDoRefresh(boolean)}
     * is true.
     */
    public void setRefreshOptions(Map options) {
        setArgument(PlanEvaluator.ARG_REFRESH_OPTIONS, options);
    }

    /**
     * Set whether or not to detect native changes.
     */
    public void setNativeChange(boolean b) {
        setArgument(PlanEvaluator.ARG_NATIVE_CHANGE, b);
    }

    /**
     * Set whether TriggerSnapshots should be created for an identity.  Use when TriggerSnapshots are needed
     * and ARG_NO_LINK_UPDATE is true.
     */
    public void setTriggerSnapshots(boolean b) { setArgument(PlanEvaluator.ARG_TRIGGER_SNAPSHOTS, b); }

    //////////////////////////////////////////////////////////////////////
    //
    // Prepare and Save
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Prepare the Provisioner.  This is a no-op.
     *
     * @ignore
     * All Identitizer utility classes have one of these but we don't
     * need one.
     */
    public void prepare() throws GeneralException {
    }

    /**
     * When used from a task, copy statistics to the result.
     */
    public void saveResults(TaskResult result) {
        result.setInt(RET_ASSIGNMENTS_RECONCILED, _assignmentsReconciled);

        // no _compiler statistics yet, might be nice
        if (_evaluator != null)
            _evaluator.saveResults(result);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Compilation
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Return or create a plan compiler.
     */
    private PlanCompiler getCompiler() {
        if (_compiler == null) 
            _compiler = new PlanCompiler(_context);
        return _compiler;
    }

    /**
     * Compile a master plan into a project.  This is the newer method
     * that is recommended for new code.
     *
     * The Identity is passed in the plan.  
     * 
     * If you want ARG_NO_FILTERING or any other option you must
     * use the constructor that takes an args map or call
     * setArgument before compilation.
     *
     * The arguments map is optional and typically set only when 
     * Provisioner is being used from within a workflow.  This allows
     * the workflow to pass in state that can be used in Template 
     * and Field scripts. This might be the same as the arg map passed
     * to the constructor but do not assume so, it might have more and
     * different things.
     * 
     */
    public ProvisioningProject compile(ProvisioningPlan masterPlan,
                                       Attributes<String,Object> scriptArgs)
        throws GeneralException {

        PlanCompiler pc = getCompiler();

        _project = pc.compile(_arguments, masterPlan, scriptArgs);

        // once we have the project the masterPlan identity reference
        // is no longer necessary since the name has been promoted
        // hmm, I'm reluctant to take this way at this level, some callers
        // may not be expecting this
        //masterPlan.setIdentity(null);

        return _project;
    }

    /**
     * Compile the plan into a project without any special script arguments.
     *
     * @see #compile(ProvisioningPlan, Attributes)
     */
    public ProvisioningProject compile(ProvisioningPlan masterPlan)
        throws GeneralException {

        return compile(masterPlan, null);
    }

    /**
     * Recompile a previously compiled project, factoring in the
     * results of answered Questions. If the returned project
     * has more Questions they must be answered before the project
     * can be executed.
     */
    public ProvisioningProject recompile(ProvisioningProject project,
                                         Attributes<String,Object> scriptArgs)
        throws GeneralException {

        if (project != null) {
            PlanCompiler pc = getCompiler();
            project = pc.recompile(project, scriptArgs);
        }
        return project;
    }

    /**
     * Older compilation interface used by RemediationManager and 
     * possibly others. Identity was passed as an argument and
     * the "forceAll" flag could be used to disable filtering.
     *
     * Try to weed this out and use the newer interfaces with more
     * explicit arg passing.
     * @deprecated Use {@link #compile(ProvisioningPlan, Attributes)}.
     */
    @Deprecated
    public ProvisioningProject compileOld(Identity identity, 
                                          ProvisioningPlan masterPlan,
                                          boolean forceAll)
        throws GeneralException {
        
        // in case we're reusing the Provisioner, clear this before
        // setting arguments to avoid a warning
        _project = null;

        setArgument(PlanCompiler.ARG_NO_FILTERING, forceAll);

        // the new convention is to pass the identity in the plan
        if (identity != null)
            masterPlan.setIdentity(identity);

        return compile(masterPlan, null);
    }

    /**
     * Original interface provided for customization backward 
     * compatibility. Try not to use this in new code.
     *
     * @deprecated Use {@link #compile(ProvisioningPlan, Attributes)}
     */
    @Deprecated
    public ProvisioningProject compile(Identity identity, 
                                       ProvisioningPlan masterPlan,
                                       boolean forceAll)
        throws GeneralException {
        
        return compileOld(identity, masterPlan, forceAll);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Reconciliation
    //
    // This is the process where we build provisioning plans to reflect
    // changes to the role assignment list and/or build plans to provision
    // things that are missing by the current role assignments.
    // It is called by the identity refresh task and some workflows.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Compile a project that will bring the given identity into alignment
     * with the current assignments and attribute synchronization rules.
     *
     * @ignore
     * This is primarily used by the unit tests, though custom code could
     * use it for ad-hoc analysis of missing entitlements.
     *
     * Also now used by Identitizer when synchronizeAttributes is on
     * but correlateEntitlements is off.  
     */
    public ProvisioningProject reconcile(Identity ident, 
                                         Attributes<String,Object> scriptArgs)
        throws GeneralException {

        return reconcile(ident, ident.getRoleAssignments(), ident.getRoleAssignments(), scriptArgs);
    }

    /**
     * Compile a project that will bring the given identity into alignment
     * with a set of new assignments specified as a Bundle list.
     * This is for backward compatibility with the unit tests, and possibly
     * some custom code that thinks of assignments as a List of Bundle
     * objects without duplicates. The passed list is authoritative
     * and the RoleAssignment list is synced with it.  
     *
     * @ignore
     * NOTE: The Source is still considered to be Rule so that the assignment
     * rule tests can remove things.  We need control over this, some tests
     * want it to look like auto-assignment, some manual!!
     */
    public ProvisioningProject reconcileManualAssignments(Identity ident, 
                                                          List<Bundle> newRoles,
                                                          Attributes<String,Object> scriptArgs)
        throws GeneralException {

        List<RoleAssignment> oldAssignments = ident.getRoleAssignments();
        List<RoleAssignment> newAssignments = new ArrayList<RoleAssignment>();
        
        // search structure for the new roles
        Map<String,Bundle> newMap = new HashMap<String,Bundle>();
        for (Bundle role : Util.iterate(newRoles))
            newMap.put(role.getId(), role);

        // decide which RoleAssignments to retain
        Map<String,RoleAssignment> oldMap = new HashMap<String,RoleAssignment>();
        for (RoleAssignment old : Util.iterate(oldAssignments)) {
            if(!old.isNegative()) {
                // could have been deleted
                Bundle role = getRole(old);
                if (role != null) {
                    // bring over assignments that are still on the list
                    if (newMap.get(role.getId()) != null) {
    
                        // Prior to 6.3 EntitlementCorrelator.getAssignedRoles would check
                        // for disabled roles.  Now we have to do it here.
                        if (!role.isDisabled())
                            newAssignments.add(old);
                    }
    
                    oldMap.put(role.getId(), old);
                }
            }
        }

        // then add new assignments
        for (Bundle role : Util.iterate(newRoles)) {
            if (oldMap.get(role.getId()) == null) {
                // haven't seen this before
                RoleAssignment neu = new RoleAssignment(role, null, Source.Rule.toString());
                newAssignments.add(neu);
            }
        }

        return reconcile(ident, oldAssignments, newAssignments, scriptArgs);
    }

    /**
     * @deprecated Use {@link #reconcileManualAssignments(Identity, List, Attributes)}
     */
    @Deprecated
    public ProvisioningProject reconcileUnitTestRoles(Identity ident, 
                                                      List<Bundle> roles,
                                                      Attributes<String,Object> scriptArgs) 
        throws GeneralException {

        return reconcileManualAssignments(ident, roles, scriptArgs);
    }

    /**
     * Compile a project that will bring the given identity into alignment
     * with a list of rule-based assignments calculated by the 
     * EntitlementCorrelator. This is now the primary interface
     * between Identitizer and Provisioner.
     * 
     * This is intended for use only by Identitizer to provision the
     * list of rule-based assignments from the EntitlementCorrelator.
     * 
     * The new assignment list is expected to contain both the rule-based
     * assignments and any manual assignments we carried over. In other
     * words, it can be directly assigned to the Identity replacing the
     * old assignment list.
     *
     * Note that this can may also add attribute assignments and attribute
     * synchronization requests to the project unless disabled.
     */
    public ProvisioningProject reconcileAutoAssignments(Identity ident,
                                                        List<RoleAssignment> newAssignments,
                                                        Attributes<String,Object> scriptArgs)
        throws GeneralException {

        List<RoleAssignment> currentRoles = ident.getRoleAssignments();
        if(currentRoles != null) {
            currentRoles = currentRoles.stream().filter(roleAssignment -> {
                if (roleAssignment != null) {
                    try {
                        Bundle role = roleAssignment.getRoleObject(this._context);
                        if ((role != null) && (role.getRoleTypeDefinition() != null)) {
                            return !RapidSetupConfigUtils.isBirthrightRoleType(role.getRoleTypeDefinition());
                        }
                    } catch (GeneralException e) {
                        log.debug("Unable to read role type for role assignment '" +
                                roleAssignment.getRoleName() + "'", e);
                    }
                }
                return true;
            }).collect(Collectors.toList());
        }
        return reconcile(ident, currentRoles, newAssignments, scriptArgs);
    }

    /**
     * Inner common method to reconcile two assignment lists.  
     * This will also add requests for attribute assignments and
     * attribute synchronization unless disabled with arguments.
     */
    private ProvisioningProject reconcile(Identity ident,
                                          List<RoleAssignment> oldAssignments,
                                          List<RoleAssignment> newAssignments,
                                          Attributes<String,Object> scriptArgs)
        throws GeneralException {

        // in case we're reusing the Provisioner, clear this before we set
        // arguments to avoid a warning
        _project = null;
        List<ExpansionItem> expansions = null;

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        plan.setSource(Source.IdentityRefresh);

        // always reconcile the IIQ Identity assignment lists, whether
        // this actually results in provisinoing is governed later
        // by PlanCompiler.ARG_NO_ROLE_EXPANSION.
        addRoleAssignmentRequests(ident, oldAssignments, newAssignments, plan);
            
        // Starting in 6.0 also reconcile attribute assignments
        // these don't change on refresh since we don't have auto-assignment
        // rules on entitlements
        // To ensure we follow the previous logic to only restore assignments
        // when role expansion is on
        if (!_arguments.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION)) {
            expansions = addAttributeAssignmentRequests(ident, plan);
        }

        // Synchronize attributes by adding requests for all identity
        // attributes with targets (if this option is enabled).
        addAttributeSyncRequests(ident, plan);

        // compile what we gathered
        ProvisioningProject proj = compile(plan, scriptArgs);
        
        // Add the attribute assignment expansion items since this expansion
        // does not happen as a part of compilation.
        if (!Util.isEmpty(expansions)) {
            for (ExpansionItem item : expansions) {
                proj.addExpansionItem(item);
            }

            // Need to clean this out to only include expansions that have
            // not been filtered.
            proj.cleanupExpansionItems();
        }

        return proj;
    }

    /**
     * Inner utility to build out a role assignment reconciliation project.
     * 
     * The detectedRoleReconciliation flag was added.  
     * When set it will cause the automatic deprovisioning
     * of any detected role that is not required or permitted by the
     * assigned roles. This works by adding delete requests
     * for all the currently detected roles then enabling a subtle
     * compiler option to make the permits list of the currently 
     * assigned roles bring back the ones that are permitted.
     */
    private void addRoleAssignmentRequests(Identity ident,
                                           List<RoleAssignment> oldAssignments,
                                           List<RoleAssignment> newAssignments,
                                           ProvisioningPlan plan)
        throws GeneralException {

        // build search structures for the old and new assignments
        Map<String,RoleAssignment> oldMap = getAssignmentMap(oldAssignments);
        Map<String,RoleAssignment> newMap = getAssignmentMap(newAssignments);

        // Add requests for role adds and removes
        // The logic here is a bit obscure so pay attention.
        // Normally we will reconcile assignments even if they have not changed so
        // we can pick up changes to the role model.  An obscure performance
        // optimization "provisionIfChanged" disables that, it will only reconcile
        // if the old and new assignment lists are different.  This is rarely on.
        // If noRoleExpansion is on however we don't need to do anything unless
        // the assignment list has changed since we're not going to provision
        // the role contents.  This allows us to skip some work setting
        // RoleAssignments that already exist.
        boolean onlyIfDifferent =
            _arguments.getBoolean(ARG_PROVISION_ROLES_IF_CHANGED) ||
            _arguments.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION);

        if (!onlyIfDifferent || isDifferent(oldMap, newMap)) {

            // calculate the list of removals, these are never optimized
            List<RoleAssignment> toRemove = calculateReconRemoves(oldAssignments, newMap);

            // optimize adds if requested, but only if we have no removals
            // if we have removals then we have to do all the adds for
            // dependency retention
            boolean optimize = false;
            if (Util.isEmpty(toRemove))
                optimize = _arguments.getBoolean(ARG_OPTIMIZE_RECONCILIATION);

            // ancient and largely unused options for McD and NT
            // note that these are incompatible with optimization
            if (!optimize) {
                addDetectedRoleReconciliation(ident, plan);
                addFullReconciliation(ident, plan);
            }

            // add removes
            for (RoleAssignment assignment : Util.iterate(toRemove)) {
                addRoleRequest(assignment, plan, true);
            }

            // calculate the list of adds, these may be optimized
            List<RoleAssignment> toAdd = calculateReconAdds(oldMap, newAssignments, optimize);
            for (RoleAssignment assignment : toAdd) {
                if (!assignment.isNegative()) {
                    addRoleRequest(assignment, plan, false);
                }
            }
        }
    }
    
    /**
     * Build a Map from a List of assignments for faster lookup.
     * There is a fix here to deal with unupgraded assignments that
     * do not have an id. Should only see these on an old list. The fallback
     * is to operate only on the first one in the new list, keyed by role name.
     */
    private Map<String,RoleAssignment> getAssignmentMap(List<RoleAssignment> list) {

        Map<String,RoleAssignment> map = new HashMap<String,RoleAssignment>();
        for (RoleAssignment ra : Util.iterate(list)) {
            String aid = ra.getAssignmentId();
            if (aid != null)
                map.put(aid, ra);

            // kludge: store by name too for upgrade, see method comments
            if (map.get(ra.getRoleName()) == null)
                map.put(ra.getRoleName(), ra);
        }
        return map;
    }

    /**
     * Return true if there are differences in the two role assignment sets.
     * This supports the ARG_PROVISION_ROLES_IF_CHANGED option. This
     * was a temporary solution for bug 6063 that is not used much if at
     * all anymore since there are other ways to solve that problem.  
     * 
     * If any of the RAs in the old map do not have assignmentIds 
     * it is assumed that this is different provisioning is forced to
     * upgrade the ids.
     */
    private boolean isDifferent(Map<String,RoleAssignment> oldMap, 
                                Map<String,RoleAssignment> newMap) {

        boolean different = false;

        if (oldMap.size() != newMap.size()) {
            different = true;
        }
        else {
            for (RoleAssignment ra : oldMap.values()) {
                String id = ra.getAssignmentId();
                if (id == null || newMap.get(id) == null) {
                    different = true;
                    break;
                }
            }
        }
        
        return different;
    }

    /**
     * Given a list of old and new assignments, calculate the list
     * of assignments that are being removed.
     *
     * There is a fix here to deal with unupgraded RoleAssignments that
     * do not have assignment ids. The fallback is to operate only on the
     * first one in the new list with the role name. See more in getAssignmentMap.
     */
    private List<RoleAssignment> calculateReconRemoves(List<RoleAssignment> oldAssignments,
                                                       Map<String,RoleAssignment> newAssignments) {
        
        List<RoleAssignment> toRemove = new ArrayList<RoleAssignment>();

        for (RoleAssignment old : Util.iterate(oldAssignments)) {
            RoleAssignment neu = null;
            String aid = old.getAssignmentId();
            if (aid != null) {
                neu = newAssignments.get(aid);
            }
            else {
                // kludge: see method comments
                neu = newAssignments.get(old.getRoleName());
            }
            if (neu == null)
                toRemove.add(old);
        }
        return toRemove;
    }

    /**
     * Given a list of new assignments, calculate the list that should
     * be compiled. If optimization is not on, the entire list is
     * compiled. If optimization is on only new assignments are added.
     *
     * Originally the intent was to also add assignments with
     * missing target memory to upgrade them.  But unfortunately this
     * is a normal situation for our generated scale data which prevents
     * much of the optimization.  Reconsider this, though at this point
     * I we've required assignment id upgraades for several releases
     * now so it probably doesn't matter.
     */
    private List<RoleAssignment> calculateReconAdds(Map<String,RoleAssignment> oldAssignments,
                                                    List<RoleAssignment> newAssignments,
                                                    boolean optimize) {

        List<RoleAssignment> toAdd = new ArrayList<RoleAssignment>();
        if (!optimize) {
            if (newAssignments != null)
                toAdd.addAll(newAssignments);
        }
        else {
            for (RoleAssignment neu : Util.iterate(newAssignments)) {
                RoleAssignment old = null;
                String aid = neu.getAssignmentId();
                if (aid != null) {
                    old = oldAssignments.get(aid);
                }
                else {
                    // kludge for unupgraded assignments
                    old = oldAssignments.get(neu.getRoleName());
                }

                // originally added if it matched by there was no target memory
                // see method comments
                //if (old == null || Util.isEmpty(old.getTargets()))
                if (old == null)
                    toAdd.add(neu);
            }
        }
        return toAdd;
    }
    
    /**
     * Add an AttributeRequest for a role assignment add or remove.
     */
    private void addRoleRequest(RoleAssignment assignment, 
                                ProvisioningPlan plan,
                                boolean remove) {

        _assignmentsReconciled++;

        AttributeRequest att = new AttributeRequest();
        att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
        att.setValue(assignment.getRoleName());
        att.setAssignmentId(assignment.getAssignmentId());
        if (remove) {
            att.setOperation(Operation.Remove);
            // note that we do NOT carry over the sunset date here, 
            // if it falls off the auto-assignment list it is taken away immediately
        }
        else {
            att.setOperation(Operation.Add);
            // must preserve sunrise/sunset dates if we have them
            att.setAddDate(assignment.getStartDate());
            att.setRemoveDate(assignment.getEndDate());
        }

        getOrCreateIIQAccountRequest(plan).add(att);
    }

    /**
     * Return the IdentityIQ account request in the given plan, creating and adding one
     * if necessary.
     */
    private AccountRequest getOrCreateIIQAccountRequest(ProvisioningPlan plan) {
        AccountRequest account = plan.getIIQAccountRequest();
        if (null == account) {
            account = new AccountRequest();
            account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            account.setApplication(ProvisioningPlan.APP_IIQ);
            plan.add(account);
        }
        return account;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Obscure McD Reconciliation Options
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add requests to implement detected role reconciliation.
     * These are ancient and largely unused options.
     * Note that this is incompatible with optimization so you must
     * not call this if optimization is on.
     *
     * TODO: This needs to change for multiple detections but maybe
     * not if only McD uses this.
     */
    private void addDetectedRoleReconciliation(Identity ident, ProvisioningPlan plan) 
        throws GeneralException {


        // option to remove all currently detected roles, will only
        // be retained if they are required or permitted by the assignments
        if (_arguments.getBoolean(ARG_DETECTED_ROLE_RECONCILIATION)) {

            // Tell plan compiler to obey permits list if we're doing 
            // detected role recon.  I don't like that half of recon
            // is up here and the other is buried in AssignmentExpander
            // but it isn't really worth fixing this obscure option
            setArgument(PlanCompiler.ARG_RETAIN_PERMITTED_ROLES, true);

            List<Bundle> detected = ident.getDetectedRoles();
            if (detected != null) {
                AttributeRequest att = new AttributeRequest();
                att.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                getOrCreateIIQAccountRequest(plan).add(att);
                att.setOperation(Operation.Remove);
                // just keep the names in the project so we don't
                // serialize the entire damn Bundle in the workflow
                att.setValue(ObjectUtil.getObjectNames(detected));
            }
        }

        // option to remove all detected roles that are permitted
        // will only be retain if they are required by the assignments
        // TODO: Will need to change for multiple detections, but
        // maybe not since only McD ever uses this
        if (_arguments.getBoolean(ARG_RESET_PERMITTED_ROLES)) {
            List<Bundle> detected = ident.getDetectedRoles();
            List<Bundle> toRemove = new ArrayList<Bundle>();
            for (RoleAssignment assignment : Util.iterate(ident.getRoleAssignments())) {
                Bundle role = getRole(assignment);
                if (role != null)
                    gatherPermittedRoles(role, detected, toRemove);
            }
            if (toRemove.size() > 0) {
                AttributeRequest att = new AttributeRequest();
                att.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                getOrCreateIIQAccountRequest(plan).add(att);
                att.setOperation(Operation.Remove);
                att.setValue(ObjectUtil.getObjectNames(toRemove));
            }
        }
    }

    /**
     * Resolve a Bundle for a RoleAssignment.
     * If the role was deleted, log and carry on, need to be able to compile
     * recon plans even if there are stale assignments.
     */
    private Bundle getRole(RoleAssignment ra) throws GeneralException {

        Bundle role = null;
        if (ra.getRoleId() != null) {
            // won't Hibernate throw if we don't have an id match?  
            try {
                role = _context.getObjectById(Bundle.class, ra.getRoleId());
            }
            catch (Exception e) {
                log.error("Unable to fetch role by id: " + 
                          ra.getRoleId() + ":" + ra.getName());
            }
        }
        else if (ra.getRoleName() != null) {
            // name without id, might happen in the unit tests
            // we won't throw if there isn't a name match
            role = _context.getObjectByName(Bundle.class, ra.getRoleName());
        }

        if (role == null)
            log.error("Encountered deleted role during reconciliation: " + 
                      ra.getRoleId() + ":" + ra.getRoleName());
        
        return role;
    }

    /**
     * Helper for reconcile and _resetPermittedRoles.
     * Walk the inheritance hierarchy and gather a flat list of all roles
     * permitted by the given role. Add it to the list only if the
     * user currently has this as a detected role.
     */
    private void gatherPermittedRoles(Bundle role, 
                                      List<Bundle> detected, 
                                      List<Bundle> toRemove) {

        if (detected != null) {
            List<Bundle> permits = role.getPermits();
            if (permits != null) {
                for (Bundle b : permits) {
                    // hmm, in theory we might have to deal with subclassing 
                    // assuming McD doesn't need that
                    if (detected.contains(b)) {
                        if (!toRemove.contains(b))
                            toRemove.add(b);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Obscure Northern Trust Full Reconciliation Option
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Do a full reconciliation by first taking away EVERY entitlement
     * held by an Identity, then let the role model retain them.
     * A very dangerous option used by one customer for a time.
     */
    private void addFullReconciliation(Identity ident, ProvisioningPlan plan) 
        throws GeneralException {

        if (_arguments.getBoolean(ARG_FULL_RECONCILIATION)) {
            removeAllEntitlements(ident, plan);
            // also need this option to enable retention of permitted roles
            setArgument(PlanCompiler.ARG_RETAIN_PERMITTED_ROLES, true);
            // shouldn't be on, but make sure
            setArgument(PlanCompiler.ARG_PRESERVE_DETECTED_ROLES, false);
        }
    }

    /**
     * Add remove requests for every entitlement currently held by an identity.
     * This is used in the implementation of the "rule reconcilation" option.
     */
    private void removeAllEntitlements(Identity ident, ProvisioningPlan plan)
        throws GeneralException {

        List<Link> links = ident.getLinks();
        if (links != null) {
            for (Link link : links) {
                Application app = link.getApplication();
                if (app != null) {
                    Schema schema = app.getAccountSchema();
                    if (schema != null) {
                        List<AttributeDefinition> atts = schema.getAttributes();
                        if (atts != null) {
                            for (AttributeDefinition att : atts) {
                                if (att.isEntitlement()) {
                                    removeEntitlement(link, att, plan);
                                }
                            }
                        }
                    }
                    // also remove permissions
                    Object o = link.getAttribute(Connector.ATTR_DIRECT_PERMISSIONS);
                    if (o instanceof Permission) 
                        removePermission(link, (Permission)o, plan);
                    else if (o instanceof List) {
                        for (Object el : (List)o) {
                            if (el instanceof Permission)
                                removePermission(link, (Permission)el, plan);
                        }
                    }
                }
            }
        }
    }

    private void removeEntitlement(Link link, AttributeDefinition def, 
                                   ProvisioningPlan plan) {

        String name = def.getName();
        Object value = link.getAttribute(name);
        if (value != null) {

            // locate the plan for this app
            AccountRequest account = getAccountRequest(plan, link);

            AttributeRequest req = new AttributeRequest();
            req.setName(name);
            req.setOperation(Operation.Remove);
            // note that the value in the plan can be modified if it is a list
            // so have to clone to prevent accidental modification of
            // the Link during compilation
            if (value instanceof Collection) {
                List list = new ArrayList();    
                list.addAll((Collection)value);
                value = list;
            }
            req.setValue(value);
            account.add(req);
        }
    }

    private void removePermission(Link link, Permission p, 
                                  ProvisioningPlan plan) {

        AccountRequest account = getAccountRequest(plan, link);

        PermissionRequest req = new PermissionRequest();
        req.setTarget(p.getTarget());
        req.setOperation(Operation.Remove);
        req.setRights(p.getRights());
        account.add(req);
    }
    
    private AccountRequest getAccountRequest(ProvisioningPlan plan,
                                             Link link) {

        Application app = link.getApplication();
        AccountRequest account =
            plan.getAccountRequest(app.getName(), link.getInstance(), link.getNativeIdentity());
        if (account == null) {
            account = new AccountRequest();
            account.setApplication(app.getName());
            account.setInstance(link.getInstance());
            account.setNativeIdentity(link.getNativeIdentity());
            plan.add(account);
        }
        return account;
    }
    
    //////////////////////////////////////////////////////////////////////  
    //
    // Attribute Assignment Reconciliation
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Add account requests to the plan for each of the attribute assignments 
     * assigned to the identity. Return a list of ExpansionItems with an item
     * for every attribute assignment.
     *
     * jsl - this has always been on by default, should have an option to
     * control it like ARG_NO_ATTRIBUTE_SYNC_EXPANSION. For now it is going
     * to be assumed that if ARG_NO_LINK_UPDATE is on it also means to disable
     * this since the expansion will not do anything. This is typically on when
     * ARG_OPTIMIZE_RECONCILIATION is also on, but it does not have to be.
     */
    private List<ExpansionItem> addAttributeAssignmentRequests(Identity identity,
                                                               ProvisioningPlan plan)                                          
        throws GeneralException {
        
        if (_arguments.getBoolean(PlanEvaluator.ARG_NO_LINK_UPDATE))
            return null;

        List<ExpansionItem> expansions = new ArrayList<ExpansionItem>();
        
        // cleanup any invalid ones..
        identity.validateAttributeAssignments(_context);
        List<AttributeAssignment> assignments = identity.getAttributeAssignments();
        if ( Util.size(assignments) > 0 ) {
            Iterator<AttributeAssignment> it = assignments.iterator();
            while ( it.hasNext() ) {
                AttributeAssignment assignment = it.next();
                if ( assignment == null ) {
                    it.remove();
                    continue;
                }
                AccountRequest request = buildAccountRequest(assignment);
                if ( request != null ) {
                    // these will get merged during compilation
                    plan.add(request);  

                    // Create expansion items for this assignement.
                    List<ExpansionItem> items =
                        PlanCompiler.createExpansionItems(request,
                                                          Cause.AttributeAssignment,
                                                          assignment.getSource());
                    expansions.addAll(items);
                }
            }        
        }

        return expansions;
    }
       
    /*
     * Using the details of the assignment build an
     * account request.  We'll add requests for all
     * of the entitlements and let the compilation
     * process filter out the ones that already 
     * exist.
     * 
     * @param assignment
     * @return
     */
    private AccountRequest buildAccountRequest(AttributeAssignment assignment ) {
        AccountRequest request = new AccountRequest();
        request.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        request.setNativeIdentity(assignment.getNativeIdentity());
        request.setInstance(assignment.getInstance());
        request.setApplication(assignment.getApplicationName());

        ProvisioningPlan.GenericRequest req;
        if(Util.nullSafeEq(assignment.getType(), ManagedAttribute.Type.Permission) &&
                Util.isNotNullOrEmpty(assignment.getTargetCollector())) {
            req = new PermissionRequest(assignment.getName(),
                    Operation.Add,
                    Arrays.asList((String)assignment.getValue()));
            req.setTargetCollector(assignment.getTargetCollector());
        } else {
            req = new AttributeRequest(assignment.getName(), Operation.Add, assignment.getValue());
        }

        // transfer start date and end date so we will respect them
        req.setAddDate(assignment.getStartDate());
        req.setRemoveDate(assignment.getEndDate());
        request.add(req);
        
        return request;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Attribute Sync Reconciliation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add attribute requests to the plan for all identity attributes that have
     * targets. These will later be expanded during plan compilation into
     * requests on the targets.
     */
    private void addAttributeSyncRequests(Identity identity, ProvisioningPlan plan)                                          
        throws GeneralException {

        // Do nothing if this isn't enabled
        if (_arguments.getBoolean(PlanCompiler.ARG_NO_ATTRIBUTE_SYNC_EXPANSION)) {
            return;
        }
        
        ObjectConfig config = Identity.getObjectConfig();
        Map<String,ObjectAttribute> attrs = config.getTargetedAttributes();
        AccountRequest iiqRequest = null;
        
        for (ObjectAttribute attr : attrs.values()) {
            Object value = identity.getAttribute(attr.getName());

            if (shouldSync(identity, attr, value)) {
                AttributeRequest attrReq = new AttributeRequest();
                attrReq.setName(attr.getName());
                attrReq.setValue(value);
                attrReq.setOperation(Operation.Set);
    
                if (null == iiqRequest) {
                    iiqRequest = getOrCreateIIQAccountRequest(plan);
                }
    
                iiqRequest.add(attrReq);
            }
        }
    }

    /**
     * Return whether the given value should be synchronized to all targets.
     * This returns false for null values if there is no source.
     */
    private boolean shouldSync(Identity identity,
                               ObjectAttribute attr,
                               Object value) {
        
        boolean shouldSync = true;

         // If the value is null we only want to set it on the target if there
         // was a source.
         if (null == value) {
             AttributeSource source = null;
             AttributeMetaData metadata = identity.getAttributeMetaData(attr);
    
             // No metadata means that the value was not manually set and that
             // there was no source.
             if (null != metadata) {
                 source = attr.getSource(metadata);
             }
    
             // Skip pushing to the targets if there was no source and the value
             // was not set manually.
             boolean manualEdit =
                 (null != metadata) && (null != metadata.getUser());
             if ((source == null) && !manualEdit) {
                 shouldSync = false;
             }
         }

         return shouldSync;
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Compilation and Evaluation Results
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the result of the last compilation.
     */
    public ProvisioningProject getProject() {
        return _project;
    }

    /**
     * Return the unmanaged plan after compilation.
     */
    public ProvisioningPlan getUnmanagedPlan() {
        return (_project != null) ? _project.getUnmanagedPlan() : null;
    }

    /**
     * Return an unmanaged itemized plan with a given tracking id.
     * This can only be called after itemization.
     * These are derived from the unmanaged plan so they
     * will not be associated with an IntegrationExecutor.
     *
     * This return a copy that can be modified by the caller.
     */
    public ProvisioningPlan getUnmanagedPlan(String trackingId) {

        PlanCompiler pc = getCompiler();
        return pc.getUnmanagedPlan(trackingId);
    }

    /**
     * Return the IntegrationConfig that will be used to process the given
     * AccountRequest.
     *
     * @ignore
     * Public so it can be used by RemediationManager to tell whether
     * an application's entitlements can be provisioned or whether
     * it needs to open work items.
     *
     * UPDATE: RemediationManager should be using unmanaged now
     * for this but it still calls in getProvisioners for some other reason.
     */
    public IntegrationConfig getResourceManager(AccountRequest req)
        throws GeneralException {

        PlanCompiler pc = getCompiler();
        return pc.getResourceManager(req);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Itemization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a list of ProvisioningPlan slices for each unique
     * tracking id found in the compiled plans. If specified each
     * itemized plan will be simplified individually. 
     *
     * @param simplifyItems True if the plans should be simplified before being
     * itemized
     * @return The itemized plans after itemization
     */
    public Map<String,ProvisioningPlan> itemize(boolean simplifyItems) 
        throws GeneralException {
        
        Map<String,ProvisioningPlan> itemized = null;
        if (_compiler != null)
            itemized = _compiler.itemize(simplifyItems);

        return itemized;
    }

    /**
     * Return the itemized plans after itemization.
     * The map is keyed by tracking id and the plans contain only those
     * items associated with that id.
     */
    public Map<String, ProvisioningPlan> getItemizedPlans() {

        Map<String,ProvisioningPlan> itemized = null;
        if (_compiler != null)
            itemized = _compiler.getItemizedPlans();

        return itemized;
    }

    /**
     * Return a managed itemized plan with a given tracking id.
     * This can only be called after itemization.
     * These are derived from the managed plan list so they
     * will have been associated with an IntegrationExecutor.
     *
     * This return a copy that can be modified by the caller.
     */
    public ProvisioningPlan getItemizedPlan(String key){

        ProvisioningPlan plan = null;
        if (_compiler != null)
            plan = _compiler.getItemizedPlan(key);

        return plan;
    }

    /**
     * Interface to run simplification without evaluation.
     * Normally simplification is deferred until execute() but some of the
     * unit tests want to see the results of simplification without
     * evaluating anything.
     */
    public void simplify() throws GeneralException {
        if (_compiler != null)
            _compiler.simplify();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Evaluation
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Return or create a plan evaluator.
     */
    private PlanEvaluator getEvaluator() {
        if (_evaluator == null) 
            _evaluator = new PlanEvaluator(_context);
        return _evaluator;
    }

    /**
     * Execute the managed plans after compilation.
     * This is an older interface that expects compilation and evaluation
     * to be done immediately after each other with state 
     * carried in one instance of Provisioner. compile() must already 
     * have been called.
     */
    public void execute() throws GeneralException {

        if (_compiler != null) {
        
            // First simplify all plans to collapse operations in the same
            // attribute. This may lose tracking numbers!
            // At this point that shouldn't matter??
            _compiler.simplify();

            PlanEvaluator ev = getEvaluator();
            ev.execute(_compiler.getProject());
        }
    }

    /**
     * Execute a previously compiled project.
     * The Provisioner object that compiled the project can be different
     * than the that is now executing it.
     *
     * Note that provisioning options come exclusively from the project,
     * whatever is in the _arguments map is ignored. The map is normally
     * empty if you are calling this method. This ensures that the
     * project is evaluated with the options it was compiled with.
     *  
     * @ignore
     * TODO: If the project has unanswered questions we let it execute
     * anyway but log errors.  Not sure if we can be strict here yet,
     * old behavior may break.
     *
     * !! Think more about what the results should be, are they
     * left in the project or should we try to assemble them into
     * something else? 
     */
    public void execute(ProvisioningProject project)
        throws GeneralException {

        // warn if there are unanswered questions, should have options
        // to make this more severe?
        // What about forcing a value to null, should that be allowed?
        List<Question> questions = project.getQuestions();
        if (questions != null) {
            for (Question q : questions) {
                if (q.getAnswer() == null) {
                    if (log.isWarnEnabled())
                        log.warn("Missing answer for field: " + q.getFieldName());
                }
            }
        }

        // First simplify all plans to collapse operations in the same
        // attribute. This may lose tracking numbers!
        // At this point that shouldn't matter??

        //This creates new PlanCompiler every time, which forces compiler to load applications again.
        //Potentially we want to call getCompiler() to utilize its cache.
        //However, there are some connectors that update the application configuration as a side effect,
        //and next time we may/may not send connectors an updated application.
        //Before we change this for performance, we need to verify whether an updated application is sent to the connector,
        //if not, what is the consequences
        //-- according to Jeff , "this is only used for expiring authentication tokens.
        //The connector tries to authenticate, gets an error,
        //updates the token, and then stores the new token in the application in Hibernate."
        _compiler = new PlanCompiler(_context, project);
        _compiler.simplify();
        _project = _compiler.getProject();

        PlanEvaluator ev = getEvaluator();

        // These are kind of special, it's more of an operatinoal
        // environment option than something that effects the meaning
        // of the plan.  Normally this should already be in the
        // project but make sure.
        if (_arguments != null) {
            Object o = _arguments.get(PlanEvaluator.ARG_NO_LOCKING);
            if (o != null)
                _project.put(PlanEvaluator.ARG_NO_LOCKING, o);
            o = _arguments.get(Configuration.ASYNC_CACHE_REFRESH);
            if (o != null) {
                // asyncCacheRefresh needs to be in the project's refreshOptions
                // IIQETN-6371 - ARG_REFRESH_OPTIONS is typically a Map (could be a HashMap) so cast appropriately
                Map<String, Object> refreshOptions = (Map<String, Object>)project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
                if (refreshOptions == null) {
                    refreshOptions = new Attributes<String, Object>();
                    project.put(PlanEvaluator.ARG_REFRESH_OPTIONS, refreshOptions);
                }
                refreshOptions.put(Configuration.ASYNC_CACHE_REFRESH, _arguments.get(Configuration.ASYNC_CACHE_REFRESH));
            }
        }
        

        ev.execute(_project);
    }

    /**
     * Compile and execute a plan in one call.
     */
    public void execute(ProvisioningPlan masterPlan)
        throws GeneralException {

        compile(masterPlan, null);
        execute();
    }

    /**
     * Compile and execute a plan in one call.
     */
    public void execute(ProvisioningPlan masterPlan, 
                        Attributes<String,Object> scriptArgs)
        throws GeneralException {

        compile(masterPlan, scriptArgs);
        execute();
    }

    /**
     * Compile and execute a ProvisioningPlan.
     * 
     * @deprecated Use {@link #execute(ProvisioningPlan)}
     *
     * @ignore
     * Old interface for remediation manager.
     * Also used by StandardWorkflowHandler.provisionAllAccounts.
     * Formerly used by ArmWorkflowHandler which has been deleted.
     * Remediation manager probably should be ported to use the new
     * two-phase interface.
     *
     * provisionAllAccounts is used to do some targeted changes to the
     * links (disable/enable) so this is still convenient for that.
     *
     * !! This has historically set the  NO_FILTERING option
     * (via forceAll).  I want to take that away from standard provisioning.
     * It is still interesting for RemediatinoManager but I'd lke
     * them to ask for it the new way, by setting Provisioner options.
     */
    public void processWithoutFiltering(Identity identity, ProvisioningPlan masterPlan)
        throws GeneralException {

        // !! forcAll has traditionally be on here, why?
        compileOld(identity, masterPlan, true);
        execute();
    }

    /**
     * @deprecated Use {@link #execute(ProvisioningPlan)}or separate calls to
     * {@link #compile(ProvisioningPlan)} and {@link #execute()}.
     *
     * @ignore
     * Deprecating because of the forceAll
     */
    @Deprecated
    public void processWithoutFiltering(ProvisioningPlan masterPlan)
        throws GeneralException {

        // !! forcAll has traditionally be on here, why?
        compileOld(null, masterPlan, true);
        execute();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Simulation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Simulate a full reconciliation of an identity's current role assignments.
     * This is used for impact analysis. The Identity passed in here will
     * be modified, it is best if the caller passes in a detached skeleton.
     *
     * @ignore
     * NOTE: We have not historically done any deprovisioning here which
     * might be interesting to clear up entitlement SOD violations.
     * 
     * @return A modified copy of the Identity that has the simulated changes on it.
     *         It's important not to attach or commit this Identity because it
     *         contains simulated changes
     */
    public Identity impactAnalysis(Identity ident) 
        throws GeneralException {

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);

        AccountRequest account = new AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        plan.add(account);

        for (RoleAssignment roleAssignment : Util.iterate(ident.getRoleAssignments())) {
            //Skip any negative assignments
            if(!roleAssignment.isNegative()) {
                AttributeRequest attrRequest = new AttributeRequest();
                attrRequest.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                attrRequest.setOp(Operation.Add);
                attrRequest.setValue(roleAssignment.getRoleName());
                attrRequest.setAssignmentId(roleAssignment.getAssignmentId());
    
                account.add(attrRequest);
            }
        }

        return impactAnalysis(ident, plan);
    }

    /**
     * Do impact analysis from a plan.
     * @param ident Identity on which the analysis is being performed
     * @return A modified copy of the Identity that has the simulated changes on it.
     *         It's important not to attach or commit this Identity because it
     *         contains simulated changes
     */
    public Identity impactAnalysis(Identity ident, ProvisioningPlan plan)
        throws GeneralException {

        PlanCompiler pc = getCompiler();

        // no way to pass in script args in this interface
        pc.compile(_arguments, plan, null);
        
        // Simplify plans to collapse operations in the same attribute.
        // !! jsl - I don't think this is necessary for impact analysis?
        // if it is then we've got a problem with the signature
        // below that takes a project because the project may not 
        // be simplfied yet
        pc.simplify();

        return impactAnalysis(ident, pc.getProject());
    }

    /**
     * Do impact analysis on a previously compiled project.
     * We need to temporarily override the project options for
     * simulation and optimistic provisioning.
     * @param ident Identity on which the analysis is being performed
     * @param project ProvisioningProject used to perform analysis
     * @return A modified copy of the Identity that has the simulated changes on it.
     *         It's important not to attach or commit this Identity because it
     *         contains simulated changes
     */
    public Identity impactAnalysis(Identity ident, ProvisioningProject project)
            throws GeneralException {
        // XML-ify the arguments so that we can recreate them in a new context
        String projectXML = project.toXml();
        String identityXML = ident.toXml();

        // Create a temporary context to avoid corrupting the existing Hibernate 
        // session with any actions taken in the analysis
        SailPointContext provisionerContext = _context;
        SailPointContext originalContext = SailPointFactory.pushContext();
        _context = SailPointFactory.getCurrentContext();

        // Generate an Identity and ProvisioningProject within the temporary context
        Identity analysisIdentity = (Identity) XMLObjectFactory.getInstance().parseXml(_context, identityXML, false);
        ProvisioningProject analysisProject = (ProvisioningProject) XMLObjectFactory.getInstance().parseXml(_context, projectXML, false);

        // This option causes the identity cube to be udpated
        // to have the changes defined by the plan immediately
        // rather than waiting for the integrations to do the
        // provisioning and reaggregating.
        analysisProject.put(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING, true);
        // This option disables any persistent side effects during
        // evaluation.
        analysisProject.put(PlanEvaluator.ARG_SIMULATE, true);
        
        if (!Util.isEmpty(_arguments) && _arguments.containsKey(Configuration.ASYNC_CACHE_REFRESH)) {
            // IIQETN-6371 - ARG_REFRESH_OPTIONS is some type of Map so cast appropriately
            Map<String, Object> refreshOptions = (Map<String,Object>)project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
            if (refreshOptions == null) {
                refreshOptions = new Attributes<String, Object>();
                project.put(PlanEvaluator.ARG_REFRESH_OPTIONS, refreshOptions);
            }
            refreshOptions.put(Configuration.ASYNC_CACHE_REFRESH, _arguments.get(Configuration.ASYNC_CACHE_REFRESH));
        }
        
        // Note that we do not care about Questions, just 
        // provision what we can in memory with the
        // immediateUpdate option.

        PlanEvaluator ev = getEvaluator();

        // hmm, PlanEvaluator used to use this as a signal to 
        // not persist anything, continue with that though the
        // ARG_SIMULATE should be enough
        analysisIdentity.setId(null);

        // This is a special interface used to pass in
        // the identity rather than having the evaluator fetch
        // it using the name left in the project.  Necessary because
        // impactAnaysis identities will not be saved and may not
        // even exist.
        ev.execute(analysisIdentity, analysisProject);
        // Load the Identity prior to detaching it to avoid LazyInitialization problems on the caller
        analysisIdentity.loadForProvisioning();
        
        // Now that analysis is complete we can safely restore the context
        SailPointFactory.popContext(originalContext);
        _context = provisionerContext;

        return analysisIdentity;
    }
}
