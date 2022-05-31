/*  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A semi-custom task to refresh and reprovision all of the identities that
 * may possibly be impacted by a role modification.
 *
 * This was developed for NT and is still somewhat hackey but it should
 * be general purpose with some more work.
 *
 * It would be nice if this could extend IdentityRefreshExecutor but there's
 * just too much crap in there and adding subclass hooks just makes it
 * more confusing.  This task will do a similar scan but with a 
 * fixed set of Identitizer arguments, no group support, etc.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class RoleChangeProvisioner extends AbstractTaskExecutor
{

    //////////////////////////////////////////////////////////////////////
    //
	// Fields
	//
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RoleChangeProvisioner.class);

    // Inputs

	public static final String ARG_ROLE = "role";
	public static final String ARG_PLAN = "plan";	
    public static final String ARG_FILTER_THRESHOLD = "filterThreshold";
    public static final String ARG_OPTIMISTIC_PROVISIONING = "optimisticProvisioning";

    public static final String RET_TOTAL = "total";
    public static final String RET_ERRORS = "errors";
    public static final String RET_PROVISIONS = "provisions";



    SailPointContext _context;
    TaskMonitor _monitor;
    Attributes<String,Object> _arguments;
    int _maxCacheAge;
    boolean _trace;

    boolean _filtered;
    Map<String,String> _roleIds;
    
    Provisioner _provisioner;

    int _total;
    int _errors;
    int _provisions;
    int _iterations;

    /**
     * Flag set in another thread to halt the execution of
     * the refreshIdentityScores() method.
     */
    boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //
	// TaskExecutor
	//
    //////////////////////////////////////////////////////////////////////

    public RoleChangeProvisioner() {
    }

    public void execute(SailPointContext ctx, 
    		            TaskSchedule sched,
    		            TaskResult result,
    		            Attributes<String,Object> args)
    	throws Exception {

        _context = ctx;
        _arguments = args;

        // this is now thread safe
        _monitor = new TaskMonitor(_context, result);

        // hard code for now
        _maxCacheAge = 100;
        _trace = args.getBoolean(ARG_TRACE);

        // determine the roles impacted by the change
        String roleName = args.getString(ARG_ROLE);
        if (roleName == null)
            throw new GeneralException("Missing role name");

        Bundle role = _context.getObjectByName(Bundle.class, roleName);
        if (role == null) 
            throw new GeneralException("Invalid role: " + roleName);

        // and the calculated deprovisioning plan
        // this will be null if no deprovisioning was necessary
        // we could either just ignore the request or go ahead and reprovision
        // usrs to pick up any role additions
        
        ProvisioningPlan plan = null;
        Object o = args.get(ARG_PLAN);

        // having trouble getting XML objects through the TaskSchedule
        // I think the problem is JobAdapter hear the call
        // to context.getMergedJobDataMap, this needs to be doing
        // XML transformation like we do in 
        // QuartzPersistenceManager.getObject
        if (o instanceof String) {
            String xml = (String)o;
            if (xml != null && xml.length() > 1 && xml.charAt(0) == '<') {
                try {
                    o = XMLObjectFactory.getInstance().parseXml(null, xml, false);
                } catch (Exception e) {
                }
            }
        }

        if (o instanceof ProvisioningPlan)
            plan = (ProvisioningPlan)o;
        else if (o != null)
            log.error("Object passed as plan argument not a ProvisioningPlan");

        // determine the assignable roles that would be impacted by this change
        // for now we're assuming this will be a reasonably small set
        _roleIds = getImpactedRoles(role);

        if (log.isInfoEnabled() && _roleIds.size() > 0) {
            log.info("Impacted role ids:");
            for (String id : _roleIds.keySet())
                log.info(id);
        }

        Filter filter = null;
        int threshold = args.getInt(ARG_FILTER_THRESHOLD);

        if (threshold == 0 || _roleIds.size() <= threshold) {
            List<Filter> terms = new ArrayList<Filter>();
            for (String id : _roleIds.keySet()) {
                List<String> values = new ArrayList<String>();
                values.add(id);
                Filter f = Filter.containsAll("assignedRoles.id", values);
                terms.add(f);
            }
            filter = filter.or(terms);
        }

        _provisioner = new Provisioner(_context, args);
        // we'll do the locking, this only exists in 5.2 it won't port back
        _provisioner.setNoLocking(true);
        if (args.getBoolean(ARG_OPTIMISTIC_PROVISIONING)) {
            // the old way necessary for 4.0
            //_provisioner.setImmediateUpdate(true);
        }

        scan(filter, plan);
        
        result.setTerminated(_terminate);
    }

    public boolean terminate() {
        _terminate = true;
        return true;
    }

    /**
     * Copy various statistics into a task result.
     * Note that we have to use addInt here rather than setInt
     * since we may be accucmulating results from several worker threads.
     */
    public void saveResults(TaskResult result) {

        result.addInt(RET_TOTAL, _total);
        result.addInt(RET_ERRORS, _errors);
        result.addInt(RET_PROVISIONS, _provisions);

        _provisioner.saveResults(result);
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            System.out.println(msg);
    }

    private void updateProgress(String msg) {

        trace(msg);
        _monitor.updateProgress(msg); 
    }
    
    private void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Impact Analysis
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Determine all of the roles whose definition may be impacted
     * by a change to the given role.  This is a rather expensive
     * calculation since we have to use searches rather than relationship
     * navigation.
     */
    private Map<String,String> getImpactedRoles(Bundle role) 
        throws GeneralException {

        Map<String,String> idmap = new HashMap<String,String>();

        getImpactedRoles(role, idmap);

        return idmap;
    }

    /**
     * Recursive hierarchy traverser to determining  all of the roles whose
     * definition may be impacted by a change to the given role.
     */
    private void getImpactedRoles(Bundle role, Map<String,String> ids) 
        throws GeneralException {

        // skip if we've already processed this role
        String id = role.getId();
        if (ids.get(id) == null) {

            ids.put(id, id);

            List<Bundle> values = new ArrayList<Bundle>();
            values.add(role);

            // anyone that inherits me
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.containsAll("inheritance", values));
            List<Bundle> others = _context.getObjects(Bundle.class, ops);
            if (others != null) {
                for (Bundle other : others)
                    getImpactedRoles(other, ids);
            }

            // or requires me
            ops = new QueryOptions();
            ops.add(Filter.containsAll("requirements", values));
            others = _context.getObjects(Bundle.class, ops);
            if (others != null) {
                for (Bundle other : others)
                    getImpactedRoles(other, ids);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scan
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Inner identity refresh loop.
     */
    private void scan(Filter filter, ProvisioningPlan plan) 
        throws GeneralException {

        _total = 0;
        _errors = 0;
        _iterations = 0;
        _filtered = (filter != null);

        QueryOptions ops = null;
        String progress = null;
        if (filter == null)
            progress = "Beginning provisioning scan...";
        else {
            progress = "Beginning provisioning scan with filter: " + 
                filter.toString();
            ops = new QueryOptions();
            ops.add(filter);
        }
        updateProgress(progress);

        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);

        while (it.hasNext() && !_terminate) {

            String id = (String)(it.next()[0]);
            Identity identity = null;
            try {
                log.info("Locking: " + id);
                // if we're not filtering then could fetch it first
                // and check the role ids before we lock it?
                identity = ObjectUtil.lockIdentity(_context, id);
                if (identity != null) {
                    _total++;
                    progress = "Identity " + Util.itoa(_total) + " : " + 
                        identity.getName();
                    updateProgress(progress);

                    process(identity, plan);
                }
            }
            catch (Throwable t) {
                log.error(t);
                _errors++;
            }
            finally {
                if (identity != null) {
                    try {
                        ObjectUtil.unlockIdentity(_context, identity);
                        // not enough but try
                        _context.decache(identity);
                    }
                    catch (Throwable t) {
                        // don't let this interfere with the other exception
                        log.error("Unable to unlock identity during exception recovery");
                    }
                }
            }

            _iterations++;
            if (_iterations > _maxCacheAge) {
                log.info("Periodic decache");
                _context.decache();
                _iterations = 0;
            }

        }
    }

    /**
     * For the time being, this is NOT going to support
     * all of the Identitizer options including triggers
     * though it probably should.  This is essentially a dup
     * of the logic in the middle of Identitizer.refresh, need
     * to refactor that so we can pass in a provisioning plan
     * from the outside to apply to the filtered identities.
     *
     * Since this has to work in 4.0 it does not support refresh
     * workflows and forms.
     *
     */
    public void process(Identity ident, ProvisioningPlan srcPlan)
        throws GeneralException {

        boolean relevant = _filtered;
        if (!relevant) {
            // didn't use filtering, have to check the identity in memory
            List<Bundle> roles = ident.getAssignedRoles();
            if (roles != null) {
                for (Bundle role : roles) {
                    if (_roleIds.get(role.getId()) != null) {
                        relevant = true;
                        break;
                    }
                }
            }
        }

        if (relevant) {
            // start with a copy of the source plan just to make sure that
            // mods don't propagate to the next identity
            // this will be null if there is no deprovisioning to do
            ProvisioningPlan plan;
            if (srcPlan == null)
                plan = new ProvisioningPlan();
            else
                plan = new ProvisioningPlan(srcPlan);
            plan.setIdentity(ident);

            AccountRequest account = plan.getIIQAccountRequest();
            if (account == null) {
                account = new AccountRequest();
                account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
                account.setApplication(ProvisioningPlan.APP_IIQ);
                plan.add(account);
            }

            // Add all the current roles to flesh out new missing things
            // and retain things that might have been in the input plan
            List<Bundle> roles = ident.getAssignedRoles();
            if (roles != null && roles.size() > 0) {
                AttributeRequest att = new AttributeRequest();
                att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                account.add(att);
                att.setOperation(Operation.Add);
                att.setValue(ObjectUtil.getObjectNames(roles));
            }

            if (_trace) {
                println("Compiling plan:");
                println(plan.toXml());
            }

            ProvisioningProject project = _provisioner.compileOld(ident, plan, false);

            // Let the plan dictate the source when possible, but if none is 
            // specified we should behave in a manner consistent with the Identitizer
            // See Bug 19434
            String source = project.getString(PlanEvaluator.ARG_SOURCE);
            if (Util.isNullOrEmpty(source)) {
                project.put(PlanEvaluator.ARG_SOURCE, Source.Rule.toString());
            }

            if (_trace) {
                println("Compiled project:");
                println(_provisioner.getProject().toXml());
            }

            // todo: determine if there is anything in the plan so  
            // we don't bump this every time
            _provisions++;

            _provisioner.execute();
        }
    }
	
}
