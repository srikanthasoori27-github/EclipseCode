/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * A form of specialized identity refresh that looks for "uninteresting"
 * identity cubes and removes them.  What is considered uninteresting 
 * can be defined in several ways.
 * 
 * Author: Jeff
 *
 * I was thinking this should just be part of Identitizer which
 * already has a "pruneIdentities" option.  But we wanted to experiment
 * with various pruning options and I didn't want to mess up Identitizer
 * until we're sure what we want.
 * 
 * The original bug sugggests checking both "soft" refreences from
 * CertificationEntities and "hard" references from WorkItems, 
 * IdentitySnapshots, and ownership of things.
 *
 * The basic trigger for an identity cleansing is an identity with
 * no account links.  The cleansing will be suppressed if any
 * of the following is true, these are not configurable.
 *
 *    - Identity is marked "protected"
 * 
 *    - Identity is the "owner" of an Application, Role, or TaskResult
 *
 *    - Identity is a "secondaryOwner" or "remediator" of an Application
 *
 *    - Idenitty is the "manager" of another identity
 * 
 *    - identity is the owner of a WorkItem
 *
 *    - identity is the "requester" of a WorkItem
 * 
 * The presence of a Scorecard will not prevent an identity from being 
 * removed.  We can debate this but it doesn't make sense to me.
 * If the Identity has no accounts, then they have no risk.  I suppose
 * they may have risk from activity logs but if they no longer have
 * accounts then the logs are not relevant.  So why else would
 * it be interesting to keep the Scorecards?
 * 
 * CERTIFICATION MODEL REFERENCES
 *
 *   Certification
 *      creator - name of Identity that started the cert
 *      certifierIdentities
 *      manager
 *
 *   SignOffHistory (via Certification)
 *      signer (through a Reference)
 *
 *   CertificationAction (inherited from WorkItemMonitor)
 *      ownerName - who did the action, kept for historical reporting
 *      actorName - similar to ownerName, not sure what the difference is
 *
 *   CertificiationChallenge
 *     deciderName - who decided on the challenge
 *
 *   CertificationCommand
 *     requestor
 *   CertificationCommand.BulkReassignment
 *     recipient
 *
 *   CertificationDelegation (inherited from WorkItemMonitor)
 *      ownerName - who did the action, kept for historical reporting
 *      actorName - similar to ownerName, not sure what the difference is
 * 
 *   CertificationEntity
 *      identity - name if _type == Type.Identity
 *
 *   CertificationLink
 *      these can be on an Identity to reference past certs
 *      they contain a list of certifier names
 *   
 *   RemediationItem
 *     remediationIdentity - person what needs remediation
 *
 * OTHER MODEL REFERENCES
 * 
 *   IdentityHistory
 *     history of past cert decisions
 *
 *   IdentitySnapshot
 *     history of past cube contents
 *
 *   MitigationExpiration
 *     mitigator - person what did the mitigation
 *     identity - person what got the mitigation
 * 
 *   PolicyViolation
 *     identity
 *     mitigator
 *
 *   RoleAssignment
 *     assigner - person that assigned the role
 *
 *   Signoff.Signatory
 *     name - identity that did the task signoff
 *
 *   TargetAssociation
 *     referencedObjectId - not exactly sure but
 *       it can associate an Identity with a Target
 *
 *   WorkItem
 *     requester
 *     owner
 *
 *   Comment (used in WorkItem)
 *     author
 *  
 *
 *  WorkItemConfig
 *    owners - never really used?
 *
 * 
 * RECOMMENDATION
 *
 * An option for every possible references is overkill and hard anyway
 * so we're going to start with only one option to protect
 * identities that are part of an active certification.
 * 
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.DateUtil;
import sailpoint.tools.Util;

public class IdentityCleanser extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(IdentityCleanser.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true scans and logs but does not actually delete anything.
     */
    public static final String ARG_NO_DELETE = "noDelete";

    /**
     * Enables refreshing the GroupIndexes.
     */
    public static final String ARG_PROTECT_IF_CERTIFYING = 
    "protectIfCertifying";

    /**
     * If Correlated flag is manually set, protect the identity
     */
    public static final String ARG_PROTECT_IF_MANUALLY_CORRELATED = 
        "protectIfManuallyCorrelated";
    /**
     * An optional refresh date threshold.
     * If this is set, we only consider identities whose lastRefresh
     * date is before this date.
     */
    public static final String ARG_THRESHOLD_DATE = "thresholdDate";

    /**
     * Maximum number of identities to process before decaching the 
     * entire session.
     */
    public static final String ARG_MAX_CACHE_AGE = "maxCacheAge";

    /**
     * Default value for the maxCacheAge argument.  
     * You almost always need this for best performance in large dbs.
     * We're not exactly sure where the sweet spot is.
     * 
     * This is the same as DEFAULT_MAX_CONNECTION_AGE over in 
     * Aggregator so if there is a reason to change
     * it in one place, it should probably be changed in the other.
     */
    public static final int DEFAULT_MAX_CACHE_AGE = 100;

    /**
     * TaskResult attribute with the number of identities deleted.
     */
    public static final String RET_DELETED = "deleted";

    /**
     * TaskResult attribute with the number of identities protected.
     */
    public static final String RET_PROTECTED = "protected";

    /**
     * TaskResult attribute with the number of identities protected
     * because they were being used in a cert.
     */
    public static final String RET_CERTIFYING = "certifying";

    /**
     * TaskResult attribute with the number of identities that
     * could not be deleted due to exceptions.
     */
    public static final String RET_ERRORS = "errors";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext _context;

    boolean _trace;
    boolean _noDelete;
    boolean _protectIfCertifying;
    boolean _protectIfManuallyCorrelated;
    int _maxCacheAge;

    TaskMonitor _monitor;
    boolean _terminate;
    Terminator _terminator;

    //
    // Statistics
    //

    int _total;
    int _deleted;
    int _protected;
    int _certifying;
    int _errors;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityCleanser() {
    }

    public boolean terminate() {
        _terminate = true;
        return true;
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            System.out.println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        if (_trace)
            System.out.println(msg);
    }

    public void execute(SailPointContext context, 
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        _monitor = new TaskMonitor(context, result);

        _context = context;
        _trace = args.getBoolean(ARG_TRACE);
        _noDelete = args.getBoolean(ARG_NO_DELETE);
        _protectIfCertifying = args.getBoolean(ARG_PROTECT_IF_CERTIFYING);
        _protectIfManuallyCorrelated = args.getBoolean(ARG_PROTECT_IF_MANUALLY_CORRELATED);
        _maxCacheAge = args.getInt(ARG_MAX_CACHE_AGE, DEFAULT_MAX_CACHE_AGE);

        _terminator = new Terminator(_context);

        _total = 0;
        _deleted = 0;

        Filter filter = null;
        String filterSource = args.getString(ARG_FILTER);
        if (filterSource != null)
            filter = Filter.compile(filterSource);
        
        Date date = args.getDate(ARG_THRESHOLD_DATE);
        if (date != null) {
            Filter f = Filter.lt("lastRefresh", date);
            if (filter == null)
                filter = f;
            else
                filter = Filter.and(f, filter);
        }
        
        iterate(filter);

        String progress = "Identity cleansing complete";
        trace(progress);
        _monitor.updateProgress(progress); 

        trace(Util.itoa(_total) + " identities examined.");
        trace(Util.itoa(_deleted) + " identities deleted.");
        //trace(Util.itoa(_protected) + " identities protected.");
        if (_certifying > 0)
            trace(Util.itoa(_certifying) + " identities certifying.");
        if (_errors > 0)
            trace(Util.itoa(_errors) + " deletions failed.");

        result.setAttribute(RET_TOTAL, Util.itoa(_total));
        result.setAttribute(RET_DELETED, Util.itoa(_deleted));
        result.setAttribute(RET_PROTECTED, Util.itoa(_protected));
        result.setAttribute(RET_CERTIFYING, Util.itoa(_certifying));
        result.setAttribute(RET_ERRORS, Util.itoa(_errors));
        result.setTerminated(_terminate);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Iteration
    //
    //////////////////////////////////////////////////////////////////////

    public void iterate(Filter filter) throws Exception {

        QueryOptions ops = null;
        String progress = null;
        if (filter == null)
            progress = "Beginning identity cleansing scan...";
        else {
            progress = "Beginning identity cleansing scan with filter: " + 
                filter.toString();
            ops = new QueryOptions();
            ops.add(filter);
        }
        trace(progress);
        _monitor.updateProgress(progress);

        if (ops != null) {
            ops.setCloneResults(true);
        } else {
            ops = new QueryOptions();
            ops.setCloneResults(true);
        }

        // TODO: We could do some initial filtering in SQL
        // for the things that unconditionally protect an identity
        // but I don't want to mess with join optimization right now.

        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);

        int cacheAge = 0;
        Terminator terminator = new Terminator(_context);
        while (it.hasNext() && !_terminate) {

            String id = (String)(it.next()[0]);
            Identity identity = ObjectUtil.lockIdentity(_context, id);

            if (identity == null) {
                // just have been deleted out from under us
                trace("Lost identity " + id);
            }
            else {
                boolean deleted = false;
                try {
                    _total++; 

                    progress = "Examining " + Util.itoa(_total) + " " + identity.getName();
                    traced(progress);
                    _monitor.updateProgress(progress);

                    if (isProtected(identity)) {
                        _protected++;
                    }
                    else {
                        try {
                            if (!_noDelete) {
                                progress = "Deleting " + Util.itoa(_total) + " " + identity.getName();
                                traced(progress);
                                _monitor.updateProgress(progress);
                                terminator.deleteObject(identity);
                                deleted = true;
                            }
                            else {
                                progress = "Deletion candidate " + Util.itoa(_total) + " " + identity.getName();
                                traced(progress);
                                _monitor.updateProgress(progress);
                            
                                if (!_trace) {
                                    // since the whole purpose of this is to see what might happen,
                                    // emit messages directly to the console so you don't have
                                    // to mess with invisible trace flags and log levels
                                    System.out.println(progress);
                                }
                            }
                            _deleted++;
                        }
                        catch (GeneralException e) {
                            // sigh, this is most likely a foreign key violation
                            // in Hibenrate but we don't have a concrete
                            // exception for that, soldier on
                            _errors++;
                        }
                    }

                }
                finally {
                    if (!deleted)
                        ObjectUtil.unlockIdentity(_context, identity);

                    // not enough but try anyway
                    _context.decache(identity);

                    cacheAge++;
                    if (_maxCacheAge > 0 && cacheAge >= _maxCacheAge) {
                        //traced("Flushing session cache");
                        _context.decache();
                        cacheAge = 0;
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Analysis
    //
    //////////////////////////////////////////////////////////////////////

    private boolean isProtected(Identity ident) throws GeneralException {

        boolean p = ident.isProtected();
        
        if (!p) {
            List<Link> links = ident.getLinks();
            p = (links != null && links.size() > 0);
        }

        // assume this is up to date, save another query
        if (!p) p = ident.getManagerStatus();
        
        // look for capabilities but ignore controlled scopes,
        // if yas got no capabilities, yas don't need scopes
        if (!p) {
            List<Capability> caps = ident.getCapabilityManager().getEffectiveCapabilities();
            // TODO: some of these might be more important than others
            p = (caps != null && caps.size() > 0);
        }

        if (!p && _protectIfManuallyCorrelated) {
            p = ident.isCorrelatedOverridden();
        }

        // bug#10423, prevent pruning if there is an unexpired use by date
        // I don't think this needs to be conditionalized, right?
        if (!p) {
            Date useBy = ident.getUseBy();
            p = (useBy != null && useBy.after(DateUtil.getCurrentDate()));
        }

        // TODO: Fast things we can see in the Identity
        //   non-null identityHistory;
        //   non-null assignedRoles
        //   non-null mitigationExpirations

        // slow things

        if (!p) p = isOwner(Bundle.class, ident);
        if (!p) p = isOwner(Application.class, ident);
        if (!p) p = isOwner(WorkItem.class, ident);

        // might not want to be strict about this
        if (!p) p = isOwner(TaskResult.class, ident);

        if (!p) p = isRequester(ident);
        if (!p) p = isSecondaryOwner(ident);
        if (!p) p = isRemediator(ident);
        if (!p) p = isMitigator(ident);
        //IIQETN-5422 Protecting the identity when is the owner of remediation items
        if (!p) p = isOwner(RemediationItem.class, ident);


        // REALLY slow things

        if (!p && _protectIfCertifying)
            p = isCertifying(ident);


        return p;
    }

    private boolean isOwner(Class<? extends SailPointObject> cls,
                            Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", identity));

        int count = _context.countObjects(cls, ops);
        return (count > 0);
    }

    private boolean isRequester(Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("requester", identity));

        int count = _context.countObjects(WorkItem.class, ops);
        return (count > 0);
    }

    private boolean isSecondaryOwner(Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        List<Identity> owners = new ArrayList<Identity>();
        owners.add(identity);
        ops.add(Filter.containsAll("secondaryOwners", owners));

        int count = _context.countObjects(Application.class, ops);
        return (count > 0);
    }

    private boolean isRemediator(Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        List<Identity> idents = new ArrayList<Identity>();
        idents.add(identity);
        ops.add(Filter.containsAll("remediators", idents));

        int count = _context.countObjects(Application.class, ops);
        return (count > 0);
    }

    private boolean isMitigator(Identity mitigator) 
        throws GeneralException {

        QueryOptions ops = new QueryOptions();        
        ops.add(Filter.eq("mitigator", mitigator));

        int count = _context.countObjects(MitigationExpiration.class, ops);
        return (count > 0);
    }

    /**
     * Return true if this identity is involved in an active certification.
     * We're not going to be concerned about references to identities
     * that can involved in PERFORMING the cert, since those should all
     * have had capabilities or open work items.  We're going to look for
     * references IN a cert.
     *
     * We have several possibilities for determining a "finished" cert.
     * The "signed" date, the "finished" date, or a "phase" of "End".
     * 
     */
    private boolean isCertifying(Identity ident)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();        
        ops.add(Filter.eq("identity", ident.getName()));
        ops.add(Filter.eq("type", "Identity"));
        ops.add(Filter.isnull("certification.finished"));

        int count = _context.countObjects(CertificationEntity.class, ops);

        //IIQTC-109: Added this query validation to cover the case of
        // 'Entitlement Owner' certification.
        if (count == 0) {
            ops = new QueryOptions();
            ops.add(Filter.eq("targetName", ident.getName()));
            ops.add(Filter.eq("type", "DataOwner"));
            ops.add(Filter.isnull("parent.certification.finished"));
            count = _context.countObjects(CertificationItem.class, ops);
        }
        boolean certifying = (count > 0);
        
        if (certifying) {
            _certifying++;
        }

        return certifying;
    }


}
