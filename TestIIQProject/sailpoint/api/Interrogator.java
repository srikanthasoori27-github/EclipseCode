/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object that performs policy checking for identities, 
 * records policy violations, and launches violation workflows.
 * 
 * Author: Jeff
 * 
 * This is an inner implementation class for Identitizer
 * and may be used directly.
 *
 * TODO: Need support for pruning stale violations after policies
 * change or are deleted.  If we could assume that the interrogator
 * will be applying the entire set of relevant policies and not just
 * one at a time we could do that.
 * 
 * PERSISTENCE NOTES
 *
 * The Interrogator needs to be used to perform "what if" analysis
 * by taking a transient Identity and calculating PolicyViolations 
 * without modifying anything in the database.  There are therefore
 * two "layers" of interrogation methods: one that operates strictly in 
 * memory and does not have side effects, and one that takes the results
 * of the previous layer and takes action such as persistence and
 * notification.
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.MessageTemplate;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.Plugin;
import sailpoint.object.Policy;
import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.object.PolicyAlert;
import sailpoint.object.PolicyExecutor;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkItem;
import sailpoint.plugin.PluginsUtil;
import sailpoint.server.WorkItemHandler;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.tools.MessageRenderer;

public class Interrogator implements MessageRepository, WorkItemHandler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Interrogator.class);

    //  
    // System Configuration options
    //

    /**
     * The name of the default EmailTemplate to use when sending  
     *  violation notifications.
     * 
     * We don't actually use this any more now that notifications   
     * are configured within the Policy.  Whether to send an email
     * or not is determined by having a non-null notificationTemplate
     * in the PolicyAlert, null means not to notify rather than
     * to use a default template.
     */
    public static final String CONFIG_NOTIFICATION_TEMPLATE = Configuration.POLICY_VIOLATION_EMAIL_TEMPLATE;

    /**
     * The name of the deafult MessageTemplate to use 
     * to render the work item description when policies
     * are configured to open work items.  
     *
     * This we do use though it can be overridden if the PolicyAlert
     * has a non-null descriptionTemplate.
     */
    public static final String CONFIG_WORK_ITEM_TEMPLATE = 
    "policyViolationWorkItemTemplate";

    //
    // Variables we pass into violation workflows
    // (beyond the standard ones defined in object.Workflow
    //
    
    public static final String WF_VAR_VIOLATOR = "violator";

    //
    // Arguments in the argument map
    //

    /**
     * CSV of Policy names to check.
     * This overrides the default policies when set.
     */
    public static final String ARG_POLICIES = "policies";

    /**
     * When true causes old violations to be marked inactive rather
     * then deleted.
     */
    public static final String ARG_KEEP_INACTIVE_VIOLATIONS = 
        "keepInactiveViolations";

    //
    // Policy type exclusion constants
    //
    
    public static final int EXCLUDE_NONE = 0;
    public static final int EXCLUDE_RISK = 1;
    public static final int EXCLUDE_NON_RISK = 2;

    //
    // Arguments for the policy listeners rule
    //

    public static final String RULE_ARG_IDENTITY = "identity";
    public static final String RULE_ARG_POLICY = "policy";
    public static final String RULE_ARG_CONSTRAINT = "constraint";
    public static final String RULE_ARG_VIOLATION = "violation";

    public static final String RULE_ARG_IDENTITY_NAME = "identityName";
    public static final String RULE_ARG_POLICY_NAME = "policyName";
    public static final String RULE_ARG_CONSTRAINT_NAME = "constraintName";

    //
    // Return values for the TaskResult
    //
    
    public static final String RET_POLICIES = "policies";
    public static final String RET_TOTAL = "policyChecks";
    public static final String RET_VIOLATIONS = "policyViolations";
    public static final String RET_NOTIFICATIONS = "policyNotifications";
    public static final String RET_ERRORS = "policyNotificationErrors";
    public static final String RET_WORK_ITEMS = "policyWorkItems";

    //
    // Arguments from the caller
    //

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Optional identity to be considered the originator of any
     * notifications, work items, or workflows  we generate.
     * This may be set explicitly by the constructor, if not we will
     * look for TaskSchedule.ARG_LAUNCHER in the arguments map.
     */
    String _launcher;

    /**
     * Optional argument values.
     */
    Attributes<String,Object> _arguments;

    /**
     * Enable trace messages.
     */
    boolean _trace;

    /**
     * List of policies to check.
     * If null we will check all of the currently active policies.
     * TODO: Now that we pass in an Attributes map, don't need this.
     */
    List<Policy> _policies;

    /**
     * Cached list of policy executors.
     */
    Map<Policy,PolicyExecutor> _executors;

    /**
     * Flag indicating that the _policies list represents all 
     * active policies.  This determines how we deal with
     * existing violations.  If this flag is false then existing
     * violations for policies not in the _policies list have to be 
     * left alone because they may still be active.  If this is set
     * then we assume that _policies has the only ones we care about
     * and we can clean up violations for disabled or deleted
     * policies.  
     */
    boolean _allPolicies;

    boolean _keepInactiveViolations;

    //
    // Runtime state
    //
    
    boolean _prepared;
    Configuration _sysconfig;
    MessageTemplate _descriptionTemplate;
    Workflower _workflower;
    Terminator _terminator;

    MessageRepository _messages;

    /**
     * Map of objects in the _policies list keyed by id.
     * Used for faster lookup of relevant policies by 
     * isViolationInactive.
     */
    Map<String,Policy> _policyIds;

    //
    // Statistics
    // Will probably need distinct violation counts for each policy?
    //

    int _checked;
    int _violations;
    int _workItems;
    
    /**
     * are we in simulation mode (i.e., links may be in memory and not in database).
     */
    boolean _simulating;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Must have a noarg constructor to be a WorkItemHandler.
     */
    public Interrogator() {
    }

    public Interrogator(SailPointContext context) {
        _context = context;
    }

    public Interrogator(SailPointContext context, 
                        Attributes<String,Object> args) {
        _context = context;
        _arguments = args;
    }

    public void setTrace(boolean b) {
        _trace = b;
    }

    public void setLauncher(String name) {
        _launcher = name;
    }

    public void setPolicies(List<Policy> ps) {
        _policies = ps;
    }

    public void add(Policy p) {
        if (p != null) {
            if (_policies == null)
                _policies = new ArrayList<Policy>();
            _policies.add(p);
        }
    }

    public void initStatistics() {
        _checked = 0;
        _violations = 0;
        _workItems = 0;
        _workflower.initStatistics();
    }
    
    public int getChecked() {
        return _checked;
    }

    public int getViolations() {
        return _violations;
    }
    
    public boolean isSimulating() {
        return _simulating;
    }
    
    public void setSimulating(boolean val) {
        _simulating = val;
    }

    /**
     * When used by the IdentityRefreshExecutor, causes our
     * accumulated statistics to be logged.
     */
    public void traceStatistics() {
        println(Util.itoa(_checked) + " policies checked.");
        println(Util.itoa(_violations) + " policy violations.");
        println(Util.itoa(_workflower.getNotifications()) + " notifications sent.");
        println(Util.itoa(_workflower.getNotificationErrors()) + " notification errors.");
        println(Util.itoa(_workItems) + " work items opened.");
    }

    /**
     * When used by the IdentityRefreshExecutor, copy our relevant
     * statistics into a task result.
     *
     * Note that for the numeric values we have to use addInt rather
     * than setInt since we need to merge results from several 
     * worker threads.  RET_POLICIES will be the same for every thread
     * so we can just overwrite that.
     */
    public void saveResults(TaskResult result) {

        // return the names of the policies checked 
        StringBuffer b = new StringBuffer();
        if (_policies != null) {
            for (Policy p : _policies) {
                if (b.length() > 0) b.append(", ");
                b.append(p.getName());
            }
        }
        result.setAttribute(RET_POLICIES, b.toString());

        result.setInt(RET_VIOLATIONS, _violations);
        result.setInt(RET_NOTIFICATIONS, _workflower.getNotifications());
        result.setInt(RET_ERRORS, _workflower.getNotificationErrors());
        result.setInt(RET_WORK_ITEMS, _workItems);

        // this one isn't that interesting, its just the identity scan
        // total times the number of policies
        result.setInt(RET_TOTAL, _checked);
    }

    /**
     * Called once immediately before the scan begins.
     * We must fully load everything we need from Hibernate so
     * the cache can be cleared while we run.
     */
    public void prepare() throws GeneralException {

        if (!_prepared) {

            _workflower = new Workflower(_context);
            _workflower.setEmailSuppressor(new EmailSuppressor(_context));
            
            _sysconfig = _context.getConfiguration();

            if (_arguments != null) {

                _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);
                _keepInactiveViolations = _arguments.getBoolean(ARG_KEEP_INACTIVE_VIOLATIONS);

                Object policies = _arguments.get(ARG_POLICIES);
                if (policies != null) {
                    _policies = PolicyCache.getPolicies(_context, _arguments, policies);
                }
            }

            if (_policies == null) {
                // determine the default policies
                _policies = PolicyCache.getActivePolicies(_context, _arguments);
                _allPolicies = true;
            }

            // pre-cache all of the executors
            _executors = new HashMap<Policy,PolicyExecutor>();
            for (Policy p : Util.iterate(_policies)) {
                getPolicyExecutor(p);
            }

            _prepared = true;
        }
    }

    /**
     * Get a cached policy executor instance, or a fresh policy executor instance if
     * loaded from a plugin
     */
    private PolicyExecutor getPolicyExecutor(Policy p) throws GeneralException {

        PolicyExecutor exec = null;
        String clsname = p.getExecutor();
        String pluginName = p.getString(Policy.ARG_PLUGIN_NAME);

        if (!Util.isNullOrEmpty(pluginName)) {
            // Ignore _executors cache since class is to be loaded from a plugin
            if (clsname == null) {
                throw new GeneralException("Policy missing an executor class: " + p.getName());
            }
            exec = buildPolicyExecutor(p.getName(), clsname, pluginName);
        }
        else {
            // try to get from cache
            exec = _executors.get(p);
            if (exec == null) {
                if (clsname == null) {
                    throw new GeneralException("Policy missing an executor class: " + p.getName());
                }
                exec = buildPolicyExecutor(p.getName(), clsname, null);
                // add to cache
                _executors.put(p, exec);
            }
        }

        return exec;
    }

    /**
     * Instantiate a new PolicyExecutor implementation, by constructing an instance of the class
     * named executorClassName.  If pluginName is not null, then load the class using that
     * plugin's classloader.
     * @param policyName used for logging only
     * @param executorClassName the name of class to instantiate
     * @param pluginName if not null, the plugin which supplies the class executorClassName
     * @throws GeneralException
     */
    public static PolicyExecutor buildPolicyExecutor(String policyName, String executorClassName, String pluginName) throws GeneralException {

        PolicyExecutor exec = null;

        if (Util.isNullOrEmpty(pluginName)) {
            // instantiate using standard classloader
            try {
                Class cls = Class.forName(executorClassName);
                exec = (PolicyExecutor) cls.newInstance();
            } catch (Throwable t) {
                throw new GeneralException(t);
            }
        } else {
            // Use the plugin classloader to instantiate
            exec = PluginsUtil.instantiate(pluginName, executorClassName, Plugin.ClassExportType.POLICY_EXECUTOR);
            if (exec == null) {
                String errorMsg = "Unable to instantiate executor for policy " + policyName +
                        ". The plugin with name " + pluginName + " may have been disabled or uninstalled, or " + executorClassName +
                        " was not properly declared as exported in the manifest";

                throw new GeneralException(errorMsg);
            }
        }
        return exec;
    }

    /**
     * Try to locate an email template, log if not found.
     */
    private EmailTemplate getEmailTemplate(String name)
        throws GeneralException {

        EmailTemplate t = null;
        if (name != null) {
            t = _context.getObjectByName(EmailTemplate.class, name);
            if (t == null) {
                if (log.isErrorEnabled())
                    log.error("Invalid email template: " + name);
            }
        }

        return t;
    }

    /**
     * Try to locate a message template, log if not found.
     */
    private MessageTemplate getMessageTemplate(String name) 
        throws GeneralException {

        MessageTemplate t = null;
        if (name != null) {
            t = _context.getObjectByName(MessageTemplate.class, name);
            if (t == null) {
                if (log.isErrorEnabled())
                    log.error("Invalid message template: " + name);
			}
        }

        return t;
    }

    /**
     * Load the default template for formatting work items.
     */
    private MessageTemplate getDefaultDescriptionTemplate() 
        throws GeneralException {

        if (_descriptionTemplate == null) {
            String name =_sysconfig.getString(CONFIG_WORK_ITEM_TEMPLATE);
            _descriptionTemplate = getMessageTemplate(name);
            if (_descriptionTemplate == null) {
                // nothing configured, make an empty one so we don't
                // keep hitting the db
                _descriptionTemplate = new MessageTemplate();
            }
        }
        return _descriptionTemplate;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public static void println(Object o) {
        System.out.println(o);
    }

    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            println(msg);
    }

    // MessageAccumulator methods

    /**
     * @param message Message to add to internal message list.
     */
    public void addMessage(Message message){
        if (message == null)
            return;
        if (_messages == null)
            _messages = new BasicMessageRepository();
        _messages.addMessage(message);
    }

    /**
     * @return List of messages stored on this object, or an empty list.
     */
    public List<Message> getMessages() {
        if (_messages == null)
            return new ArrayList<Message>();
        return _messages.getMessages();
    }

    /**
     * @param type Type of message to retrive.
     * @return  List of messages of the given type stored on this object, or an empty list.
     */
    public List<Message> getMessagesByType(Message.Type type) {
        if (_messages == null)
            return new ArrayList<Message>();
        return _messages.getMessagesByType(type);
    }

    /**
     * Remove all messages from this object.
     */
    public void clear() {
        _messages = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Transient Identity Interrogation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Perform a memory-only analysis of an identity and return a list
     * of the detected policy violations.  This will not touch the 
     * database or have any side effects such as notification.
     *
     * This is the interface used by "what if" analysis and the
     * web service policy checking interfaces.
     */
    public List<PolicyViolation> interrogate(Identity id)
        throws GeneralException {

        prepare();

        List<PolicyViolation> violations = new ArrayList<PolicyViolation>();

        interrogate(id, EXCLUDE_NONE, violations);

        if (violations.size() == 0) violations = null;

        return violations;
    }

    /**
     * Perform a memory-only analisys into an existing violation list.
     * This is the interface used by Identitizer to do policy
     * scans in two phases.
     */
    public void interrogate(Identity id, 
                            int exclusions,
                            List<PolicyViolation> violations)
        throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        if (violations == null)
            throw new GeneralException("Missing violation list");

        prepare();

        // IdentityRefreshExecutor is already doing this
        //trace("Interrogating: " + id.getName());

        if (_policies != null) {
            for (Policy p : _policies) {

                boolean excluded = false;
                if (p.isType(Policy.TYPE_RISK))
                    excluded = (exclusions == EXCLUDE_RISK);
                else
                    excluded = (exclusions == EXCLUDE_NON_RISK);

                if (!excluded)
                    interrogate(id, p, violations);
            }
        }
    }

    /**
     * Evaluate one policy against the identity and accumulate violations.
     */
    private void interrogate(Identity id, Policy p, 
                             List<PolicyViolation> globalViolations)
        throws GeneralException {

        trace("  Checking " + p.getName());
        _checked++;

        PolicyExecutor exec = getPolicyExecutor(p);
        exec.setSimulating(isSimulating());
        if (exec != null) {
            List<PolicyViolation> violations = exec.evaluate(_context, p, id);

            if (violations != null) {

                // Check mitigations, I guess we could defer this to the
                // persistence phase but it seems useful here too

                for (PolicyViolation v : violations) {

                    // make sure this is on
                    v.setActive(true);
                    
                    // check for associated mitigations
                    checkMitigation(id, v);
                }

                globalViolations.addAll(violations);
            }
        }
    }

    /**
     * Check mitigation of a violation detected by the policy executor.
     * Some info from the MitigationExpiration will be copied to the
     * PolicyViolation so we can quickly know it was mitigiated.
     * As with all "archive" objects, be careful not to have a direct
     * reference to the MitigationExpiration since they have independent
     * lifespans.
     *
     * This is also used later when we decide whether to open a WorkItem
     * for the violation.
     */
    private void checkMitigation(Identity id, PolicyViolation v) 
        throws GeneralException {

        // Note that we don't really need the policyId on the
        // MitigationExpiration, remove it?

        //IIQSAW-2193 -- last created mitigationExpiration takes precedence.
        MitigationExpiration lastCreated = null;

        List<MitigationExpiration> mitigations = id.getMitigationExpirations();
        if (mitigations != null) {
            // There will usually be one, but in theory there could
            // have been several overlapping certifications in which case
            // we find the one that has been created last.
            for (MitigationExpiration mit : mitigations) {

                if (mit.getCertifiableDescriptor().isSimiliarViolation(v)) {
                    if (lastCreated == null) {
                        lastCreated = mit;
                    } else if (mit.getCreated().after(lastCreated.getCreated())) {
                        lastCreated = mit;
                    }
                }
            }
        }

        if (lastCreated != null) {
            // the violation was mitigiated, remember some state
            // for faster searches and display
            Identity mitigator = lastCreated.getMitigator();
            if (mitigator != null)
                v.setMitigator(mitigator.getName());
            else {
                // this is a structural error, assume it is not actually
                // mitigated
            }
        }
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Persistent Identity Interrogation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Persist the results of a previously run transient interrogation.
     * Note that this list is considered authoritative.  Current violations
     * that are not on this list will be deactivated (well, deleted actually).
     *
     * NOTE: though we have an active flag it does not appear to have
     * ever been used.  I guess we've always done violation trends
     * on groups, not individual identities.
     * 
     * This will COMMIT the transaction at least once so we can
     * try to keep the cache clean of the violations.  This may
     * also then cause the owning Identity to have partially refreshed
     * changes to be flushed.  That doesn't seem to be a problem but
     * it is less pure.  If we could be sure we did a full decache()
     * periodically we could defer the commits.
     *
     * The "prune" option is on when you want to start with an authoritative
     * list and remove any existing violations not represented on that list.
     * It should be off if you want to add a new violation without deleting
     * any other violations.
     */
    public void saveViolations(Identity id, List<PolicyViolation> violations,
                               boolean prune)
        throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        // skip the existing violation search overhead if this is the second
        // pass and we don't have anything
        if (!prune && Util.isEmpty(violations))
            return;

        prepare();

        // start by getting all the current violations
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", id));
        // Note that we need to check the active flag, if this is not done
        // we may find an old violation that had been dealt with
        // earlier in a cert or the manage violation pages, resurecting
        // it may restore the association between the cert decision history
        // which references the violation by id, making it look like the new
        // violation has been acted upon.
        ops.add(Filter.eq("active", new Boolean(true)));
        List<PolicyViolation> current = 
            _context.getObjects(PolicyViolation.class, ops);

        if (violations != null) {
            for (PolicyViolation v : violations) {

                PolicyViolation existing = findViolation(current, v);
                if (existing != null) {
                    current.remove(existing);

                    // should we do this?..if the policy changes to change
                    // the default owner, we should prolly change it in the
                    // violation as well.
                    existing.setOwner(v.getOwner());
                    // There shouldn't be anything we need to 
                    // copy from the new violation.  I suppose
                    // for an SOD constraint the left/right lists
                    // might have changed but wouldn't this be
                    // treated as a different violation?

                    v = existing;
                }

                // make sure this is on, though we're not using it yet
                v.setActive(true);

                // use the identity's assigned scope
                v.setAssignedScope(id.getAssignedScope());
                
                // !! this will release transaction locks making
                // concurrent agg/refresh tasks unstable, need
                // to rethink locking in the Identitizer stack
                _context.saveObject(v);
                _context.commitTransaction();
                _violations++;

                // Process alerts and workflows.  
                // Alerts happen if the violation was not mitigated and
                // the executor thinks this is serious enough to warrant it.
                // Workflows always happen for new violations.
                if (existing == null) {
                    Policy p = v.getPolicy(_context);
                    // bug# 17453 remove v.getMitigator() == null check, since here we consider
                    // it's new violation, existing mitigator will reset, we need to notify user
                    if (v.isAlertable())
                        checkAlerts(p, v);

                    checkWorkflow(p, v);
                }

                // TODO: Let workflows run on previously existing 
                // violations if they have no pending workflow?  
                // This would be nice if a workflow is being added
                // after an initial scan, it saves having to delete all
                // the violations first.  However we would need to figure
                // out what "mitigation" means so we don't keep starting
                // workflows on every scan.  Maybe the best thing
                // is to include both the previous and new violation
                // so the workflow can reason about them.

                _context.decache(v);
            }
        }
        
        // anything remaining can be deleted
        if (prune && current != null) {
            for (PolicyViolation v : current) {
                if (isViolationInactive(v)) {
                    deactivateViolation(v);
                }
            }
        }

        // oddity: I originally didn't do this thinking that Hibernate
        // would be able to see the Identity as dirty after chaging
        // the Scorecard, but that only worked for the first Identity
        // returned by the search.  Is the search detaching these?
        // The commits we do during violation management would also
        // leave this in a clean state so this makes sure it is dirty,
        // sadly it takes an extra database hit.
        _context.saveObject(id);
    }

    /**
     * Persist the results of a previously run transient interrogation.
     * NOTE: This is the old method that deleted the current violations.
     */
    public void saveViolationsOld(Identity id, List<PolicyViolation> violations)
        throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        // TODO: reuse existing violations!
        boolean deletePrevious = true;

        if (deletePrevious) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity", id));
            List<PolicyViolation> current = 
                _context.getObjects(PolicyViolation.class, ops);

            if (current != null) {
                for (PolicyViolation v : current) {

                    // if there are any delegated standalone workitems 
                    // for this violation, clean them up as well.
                    QueryOptions wItemOps = new QueryOptions();
                    wItemOps.add(Filter.eq("targetId", v.getId()));
                    List<WorkItem> witems = _context.getObjects(WorkItem.class, wItemOps);
                    if (witems!=null) {
                        // this was commented out, why? - jsl
                        // for(WorkItem item: witems)
                        //   _context.removeObject(item);
                    }

                    _context.removeObject(v);
                }
            }
        }

        if (violations != null) {
            for (PolicyViolation v : violations) {

                // make sure this is on
                v.setActive(true);

                // use the identity's assigned scope
                v.setAssignedScope(id.getAssignedScope());
                
                _context.saveObject(v);
                _context.commitTransaction();
                _violations++;

                // Process alerts if not mitigated and the executor
                // thinks this is serious enough to warrant it.

                if (v.isAlertable() && v.getMitigator() == null) {
                    Policy p = v.getPolicy(_context);
                    checkAlerts(p, v);
                }

                _context.decache(v);
            }
        }

        // oddity: I originally didn't do this thinking that Hibernate
        // would be able to see the Identity as dirty after chaging
        // the Scorecard, but that only worked for the first Identity
        // returned by the search.  Is the search detaching these?
        _context.saveObject(id);
    }

    /**
     * Look for an existing violation that matches a new one.
     * Since PolicyViolation objects can contain state that is
     * not in a freshly minted violation, we have to be careful
     * to reuse existing ones.
     *
     * To be equal, both the policy and constraint identifiers
     * must be equal.  
     * 
     * When comparing the Policy and BaseConstraint, only the ids
     * are used.  The Policy name could be stale and will be refreshed
     * to track renames.  If the constraint name is different it
     * could be a simple rename, or it could represent a fundamental
     * change in the policy.  We can't tell that here without deep
     * analysis of the constraint model.  We'll assume that it is
     * a simple rename, if that isn't what you want you have to delete
     * the constraint and start over.
     *
     * For SOD constraints, the left/right bundle lists must also
     * be equal, since in theory there can be several violations
     * for the same constraint with different left/right combinations.
     * We may want an option to disable this?
     *
     * For Activity constraints, the ApplicationActivity id must match.
     * Actually, these shouldn't apply here since we're not doing log
     * scanning, but make it general in case we want to copy this somewhere.
     *
     * TODO: In theory for custom violations we may have to compare
     * things in the arguments map as well, but not everything in there
     * may be relevant and we have no model to guide us.  Instanting the
     * executor just to do the comparison is overkill.  Assume for now
     * this won't happen, but I suppose the executors could use the bundle
     * lists or the activityId for their own purposes.  If we need that
     * then it would be better to have a generic "qualifier" property or
     * argument map entry that can be used here.
     */
    private PolicyViolation findViolation(List<PolicyViolation> current,
                                           PolicyViolation neu) {

        PolicyViolation found = null;
        if (current != null) {
            for (PolicyViolation v : current) {

                if (Differencer.equal(v.getPolicyId(), neu.getPolicyId()) &&
                    Differencer.equal(v.getConstraintId(), neu.getConstraintId()) &&
                    Differencer.equal(v.getLeftBundles(), neu.getLeftBundles()) &&
                    Differencer.equal(v.getRightBundles(), neu.getRightBundles()) &&
                    Differencer.equal(v.getActivityId(), neu.getActivityId())) {

                    // Refresh some things so we track the latest model
                    v.setPolicyName(neu.getPolicyName());
                    v.setConstraintName(neu.getConstraintName());
                    v.setDescription(neu.getDescription());
                    
                    found = v;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Determine if we can delete an existing policy violation that was
     * not found during a recent interrogation.  
     *
     * If the allPolicies flag is set it means that all active
     * policies were inlcuded in the scan and we can delete everything
     * that didn't have a match.
     *
     * If allPolicies is false, then we can only delete violations
     * for policies that were included in the scan since we don't
     * know the status of the others.  I suppose we could check them
     * though to see if they're deleted or inactive.
     */
    private boolean isViolationInactive(PolicyViolation v) 
        throws GeneralException {

        boolean inactive = false;

        if (_allPolicies) {
            // special flag meaning the _policies list is authoritative
            inactive = true;
        }
        else {
            // This was as selective scan, only delete violations for
            // policies that were included.  Bootstrap an index
            // for faster searching.
            if (_policyIds == null) {
                _policyIds = new HashMap<String,Policy>();
                for (Policy p : _policies) 
                    _policyIds.put(p.getId(), p);
            }

            // if the id is in the map then this was included 
            inactive = (_policyIds.get(v.getPolicyId()) != null);
        }

        return inactive;
    }

    /**
     * Deactivate or delete a violation that is no longer relevant.
     * We have historically always deleted these to reduce clutter but
     * a one customer wanted them left behind for reporting.  This
     * behavior has to be requested with a task argument.
     */
    private void deactivateViolation(PolicyViolation v)
        throws GeneralException {
                    
        if (_keepInactiveViolations) {
            v.setActive(false);
            _context.saveObject(v);
            _context.commitTransaction();
        }
        else {
            // Terminator will now handle deletiong related work items.
            // We used to do the WorkItem search here but it was
            // commented out, why?
            if (_terminator == null)
                _terminator = new Terminator(_context);
            _terminator.deleteObject(v);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Alerts
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For violations that have not been mitigated, optionally send
     * an email notification and open work items.
     */
     private void checkAlerts(Policy p, PolicyViolation v)
        throws GeneralException {
        
        PolicyAlert alert = p.getAlert();
        if (alert != null && !alert.isDisabled()) {
            // only build the listener list if we're sure we need it
            List<Identity> listeners = null;
            if (alert.getNotificationEmail() != null) {
                listeners = getListeners(p, v);
            }

            //IIQETN-5342 :- If alert.isNoWorkItem() == true, the list of "listeners" will contains all
            //the users that should be notified, it includes "if selected in the UI" (Identity -or- Manager
            //is Violation Owner -or- Rule) and the list of Observers.

            //If alert.isNoWorkItem() == false, the list of "listeners" will contains only the list of
            //Observers, VERY IMPORTANT -- it shouldn't contain (Identity -or- Manager is Violation Owner
            //-or- Rule) even if they are selected in the UI, the notifications are going to be handled
            //by workflower.

            //The best way to avoid duplicated notifications is keeping track of the IdentityId notified.
            Set<String> alreadySent = new HashSet<String>();

            //since this is a unique PolicyViolation we should start a new EmailSupresor
            //and avoid don't receive notifications that are not duplicated.
            _workflower.setEmailSuppressor(new EmailSuppressor(_context));
            if (listeners != null && v.getMitigator() == null) {
                for (Identity listener : listeners) {
                    if (!alreadySent.contains(listener.getId())) {
                        notifyListener(p, v, listener);
                        alreadySent.add(listener.getId());
                    }
                }

            }
            
            checkWorkItemForOwner(p, v);
        }
    }
    
    private void checkWorkItemForOwner(Policy policy, PolicyViolation violation) throws GeneralException {

        PolicyAlert alert = policy.getAlert();

        if (alert != null && !alert.isDisabled() && !alert.isNoWorkItem()) {

            //Ugh, the violation owner is seriously unattached to the session, complete fetch necessary
            //IIQETN-5342 :- This is a special case where the "Policy Violation Owner" is NONE and
            //the "Open Work Item" is turn on.
            Identity owner = null;
            if (violation.getOwner() != null) {
                owner = _context.getObjectById(Identity.class, violation.getOwner().getId());
            }
            if (owner != null) {
                createWorkItem(policy, violation, owner, 
                               createViolationArgs(policy, violation));
            }
        }
    }

    /**
     * Process an alert for one of the listeners.
     */
    private void notifyListener(Policy policy, PolicyViolation violation, Identity listener) 
        throws GeneralException {

        // The Identity here may have come from a static list on the Policy 
        // which was detached after the Interrogator was prepared.  Get a fresh
        // version since it isn't clear what Workflower's requirements are
        listener = ObjectUtil.reattach(_context, listener);

        _workflower.notify(listener, policy.getAlert(), 
                           createViolationArgs(policy, violation), this);
    }

    private void createWorkItem(Policy policy, 
                                PolicyViolation violation,
                                Identity identity, 
                                Map<String, Object> args)
            throws GeneralException {
        
        WorkItem item = new WorkItem();
        item.setType(WorkItem.Type.PolicyViolation);
        item.setHandler(this.getClass().getName());
        item.setOwner(identity);
        item.setTargetClass(PolicyViolation.class);
        item.setTargetId(violation.getId());
        // no special renderer page for these yet

        Identity req = ObjectUtil.getOriginator(_context, _launcher, _arguments);
        item.setRequester(req);

        // description can be templatized!
        String description = null;
        String tmp = policy.getAlert().getDescriptionTemplate();
        if (tmp != null)
            description = MessageRenderer.render(tmp, args);
        else {
            MessageTemplate mt = getDefaultDescriptionTemplate();
            if (mt != null)
                description = mt.render(args);
            else { 
                // need something
                description = "User " + violation.getIdentity().getName() +
                    ", Policy '" + policy.getName() + "'";
            }
        }

        item.setDescription(description);

        // this will do the notifications too
        _workflower.open(item, policy.getAlert(), args, this);
    }

    private Map<String, Object> createViolationArgs(Policy policy, PolicyViolation violation) 
        throws GeneralException {

        // arguments for both the email template, and 
        // work item description template
        Map<String,Object> args = new HashMap<String,Object>();

        // Locate the associated constraint.  May be null for custom policies
        BaseConstraint constraint = policy.getConstraint(violation);

        // We have historically passed the constraint name but
        // since that was often null we fell back on the violation summary.
        // The convention is to copy the BaseConstraint.violationSummary
        // into the PolicyViolation._description.
        // Now that all the interesting objects are accessible from Velocity
        // we don't really need this any more.

        String constraintName = violation.getConstraintName();
        if (constraintName == null)
            constraintName = violation.getDescription();

        // original string values
        args.put(RULE_ARG_IDENTITY_NAME, violation.getIdentity().getName());
        args.put(RULE_ARG_POLICY_NAME, policy.getName());
        args.put(RULE_ARG_CONSTRAINT_NAME, constraintName);

        // new object values accessible with Velocity
        args.put(RULE_ARG_IDENTITY, violation.getIdentity());
        args.put(RULE_ARG_POLICY, policy);
        args.put(RULE_ARG_CONSTRAINT, constraint);
        args.put(RULE_ARG_VIOLATION, violation);
        
        return args;
    }
    
    /**
     * Determine the list of users that are interested in 
     * receiving alerts for a violation.  There are three ways
     * this can be defined.
     *
     *   - static List<Identity> in the PolicyAlert
     *   - Rule in the PolicyAlert
     *   - Policy owner
     *
     * If the PolicyAlert has both a List<Identity> and a Rule
     * we call the Rule and use the result only if it is non-null.
     * If the PolicyAlert does not product any identities, we default
     * to the policy owner.
     */
    private List<Identity> getListeners(Policy policy, PolicyViolation violation) 
        throws GeneralException {

        List<Identity> listeners = new ArrayList<Identity>();

        PolicyAlert alert = policy.getAlert();
        if (alert != null && !alert.isDisabled()) {

            // start with static owner list
            List<Identity> owners = alert.getOwners();
            if (owners != null && owners.size() > 0) {
                // the owner list is detached so it is important that it NOT be 
                // modified or you get the "unsafe use of the cache" exception
                // on commit
                listeners.addAll(owners);
            }

            // then check the rule
            Rule rule = alert.getOwnerRule();
            if (rule != null) {
                Map<String,Object> inputs = new HashMap<String,Object>();
                inputs.put("environment", _arguments);
                inputs.put("policy", policy);
                inputs.put("violation", violation);

                Object result = _context.runRule(rule, inputs);
                List<Identity> ruleListeners = resolveListeners(result);

                // if the rule returns nothing, fall back to the static list
                // otherwise it overrides the static list
                if (ruleListeners != null && ruleListeners.size() > 0) {
                    listeners.clear();
                    listeners.addAll(ruleListeners);
                }
            }
        }

        // add the violation owner if we're not opening work items
        if (alert == null || (!alert.isDisabled() && alert.isNoWorkItem())) {
            Identity vowner = violation.getOwner();
            if (vowner != null) {
                if (!containsIdentity(listeners, vowner))
                    listeners.add(vowner);
            }
        }

        //IIQETN-5342 :- This block of code should be the last one in the method
        //otherwise it will send notifications to the PolicyOwner even when the
        //listeners list is not empty

        // else fall back to the policy owner
        // now that we have violationowner
        if (listeners.size() == 0 && (alert.isNoWorkItem() ||
                policy.getViolationOwnerType() == ViolationOwnerType.None)) {
            Identity owner = policy.getOwner();
            if (owner != null) {
                listeners.add(owner);
            }
        }

        return listeners;
    }
    
    private boolean containsIdentity(List<Identity> list, Identity identity) {
        
        for (Identity member : list) {
            if (member.getName().equals(identity.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given what comes back from the listener rule, locate one
     * or more identities.
     */
    private List<Identity> resolveListeners(Object obj) 
        throws GeneralException {

        List<Identity> listeners = new ArrayList<Identity>();

        if (obj instanceof List) {
            @SuppressWarnings("rawtypes")
            List srcList = (List)obj;
            for (Object o : srcList)
                resolveListener(o, listeners);
        }
        else {
            resolveListener(obj, listeners);
        }

        return (listeners.size() > 0) ? listeners : null;
    }

    /**
     * Resolve an object from a rule to an Identity and deposit
     * it on a list.
     */
    private void resolveListener(Object obj, List<Identity> listeners)
        throws GeneralException {

        Identity ident = null;
        String name = null;

        if (obj instanceof Identity)
            ident = (Identity)obj;

        else if (obj  instanceof String)
            name = (String)obj;

        else if (obj instanceof Map) {
            // use the same names we use in the correlation rules
            @SuppressWarnings("rawtypes")
            Map map = (Map)obj;
            Object o = map.get(Correlator.RULE_RETURN_IDENTITY);
            if (o instanceof Identity)
                ident = (Identity)o;
            else {
                o = map.get(Correlator.RULE_RETURN_IDENTITY_NAME);
                if (o instanceof String)
                    name = (String)o;
            }
        }

        if (ident == null && name != null)
            ident = _context.getObjectByName(Identity.class, name);

        if (ident != null)
            listeners.add(ident);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workflow
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * New mechanism for launching workflows for policy violations.
     * This is more powerful than the older alerting mechanism
     * and could replace it.
     *
     * Even though the violation has already been persisted
     * we'll pass in the entire PolicyViolation object like we do for
     * modification approvals with other classes.  This makes it easier
     * to generate the approval work item since the first thing the
     * the workflow will need is the entire PolicyViolation.
     *
     * When work items are opened from this workflow you can either
     * copy the entire work item or only set the target id, depending
     * on which rendering page you are using.  The default violation
     * renderer, workitem/policyViolation.xhtml, uses the "violationViewBean"
     * property of WorkItemBean to create a ViolationViewBean.  This
     * understands several conventions for passing violations.
     *
     * I like having the entire PolicyViolation in the workflow/workItems
     * rather than indirectly referencing with an id because it allows
     * the violation to be edited during approval if that were interesting.
     * Might be nice to add extra comments to the violation.
     */
    private void checkWorkflow(Policy p, PolicyViolation v)
        throws GeneralException {
        
        Workflow workflow = null;

        BaseConstraint con = p.getConstraint(v);
        if (con != null) {
            String wfname = con.getViolationWorkflow();
            if (wfname != null) {
                workflow = _context.getObjectByName(Workflow.class, wfname);
                if (workflow == null) {
                    if (log.isErrorEnabled())
                        log.error("Unknown workflow: " + wfname);
	            }
            }
        }

        if (workflow == null) {
            String wfname = p.getViolationWorkflow();
            if (wfname != null) {
                workflow = _context.getObjectByName(Workflow.class, wfname);
                if (workflow == null) {
                    if (log.isErrorEnabled())
                        log.error("Unknown workflow: " + wfname);
	            }
            }
        }

        if (workflow != null) {

            // default approver always policy owner?
            // if it doesn't have an approver launch the workflow anyway
            // and let it figure it out
            String approver = null;
            Identity owner = p.getOwner();
            if (owner != null)
                approver = owner.getName();

            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put(Workflow.VAR_APPROVER, approver);
            vars.put(Workflow.VAR_APPROVAL_OBJECT, v);
            vars.put(WF_VAR_VIOLATOR, v.getIdentity().getName());

            // Carry the task launcher forward to the workflow.
            // This will normally come from TaskSchedule.ARG_LAUNCHER
            // pass in the task args of the task that is using 
            // Interrogator

            Identity origin = ObjectUtil.getOriginator(_context, _launcher, _arguments, null);
            if (origin != null)
                vars.put(Workflow.VAR_LAUNCHER, origin.getName());
            else {
                // shouldn't be here, but be sure we have something
                vars.put(Workflow.VAR_LAUNCHER, "Policy Scanner");
            }

            // these don't have to be unique but should try harder
            // to include interesting info?
            String caseName = "Policy violation for identity " + 
                v.getIdentity().getName();

            // see commentary in Workflower about why we have to use    
            // the "safe" variant to avoid corrupting the SailPointContext
            WorkflowLaunch wfl = _workflower.launchSafely(workflow, caseName, vars);
            WorkflowCase wfcase = wfl.getWorkflowCase();
            if (wfcase != null) {
                // TODO: Save the case on the PolicyViolation,
                // this will let us set the violation workflow
                // after violations have been generated...
                // this does not however require that we solve the Hibernate
                // cache stuff described in the launchSafely method
            }

        }
    }

    //////////////////////////////////////////////////////////////////////
    //  
    // WorkItemHandler
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called via Workflower whenever ownership changes.  This should
     * only update any associated models to reflect the change, generic
     * operations like notifications and commenting have already
     * been done.
     */
    public void forwardWorkItem(SailPointContext context, WorkItem item,
                                Identity newOwner)
        throws GeneralException {
    }

    /**
     * Validate modifications to a work item before it is persisted.
     * You should only throw here for errors the user can do something
     * about.
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException {
    }

    /**
     * Perform side effects after an item has been persisted.
     * Unlike Certificationer we don't have any results to assimilate 
     * back, though I suppose we could put something in the PolicyViolation 
     * for later reference during certification or scoring?
     */
    public void handleWorkItem(SailPointContext con, WorkItem item, 
                               boolean foreground)
        throws GeneralException {

        // it doesn't matter what the state is, as long as it
        // is non-null

        if (item.getState() != null) {
            try {
                Workflower wf = new Workflower(con);
                wf.archiveIfNecessary(item);

                con.removeObject(item);
                con.commitTransaction();
            }
            catch (GeneralException e) {
                // Let this try.  Since this is an odd case and
                // Terminator is more expensive don't use it unless
                // we have to.
                Terminator ahnold = new Terminator(con);
                ahnold.deleteObject(item);
            }
        }
    }

}




