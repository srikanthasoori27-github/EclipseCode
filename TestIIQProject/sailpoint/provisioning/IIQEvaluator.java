/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Private utility class uesd by PlanEvaluator to evaluate a plan
 * targeted at an IIQ identity cube.
 *
 * Author: Jeff
 * 
 * This was originally part of PlanEvaluator but I factored it out since
 * it was getting kind of big, and we're likely going to be adding
 * more to this in the future while the rest of PlanEvaluator is 
 * relatively stable.  
 *
 * It is conceptually similar to an IntegrationExecutor for IIQ but
 * isn't implemented that way for a few reasons.  Primarily I want 
 * access to the  sailpoint.object.ProvisioningPlan rather than the 
 * converted sailpoint.integration.ProvisioningPlan which is what 
 * the IntegrationExecutors receive. Should think about extending
 * that interface for executors that are implemented mostly on the
 * IIQ side rather than RESTing over to a remote agent.
 * 
 * The other reason is that IIQ doesn't really do anything other thanm
 * the provision() method and it needs access to the full 
 * ProvisioningProject so it can look at evaluation options.  With enough
 * work I'm sure we could continue refactoring this so that IIQ
 * evaluation looks just like any other IntegrationExecutor, but there
 * isn't much gain with that since the compilation process has to 
 * treat the IIQ AccountRequest in a very special way anyway. 
 *
 * About the only potential win would be the ability to use the Request
 * machinery to retry the provisioning if it failed.  The only reason for
 * that would be a lock timeout.  Revisit this someday, until then we've
 * at least made a step in that direction.
 *
 * 
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Entitlizer;
import sailpoint.api.Identitizer;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.Workflower;
import sailpoint.object.AccountSelection;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.AttributeMetaData;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.IdentityArchive;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningRequest;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.Request;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleRequest;
import sailpoint.object.RoleTarget;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkflowCase;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.ProvisioningTransactionService.TransactionDetails;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Internal support class for PlanEvaluator to evaluate provisioning
 * plans targeted at an IIQ identity cube.
 */
public class IIQEvaluator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(IIQEvaluator.class);

    /**
     * Task Result names.
     */
    public static final String RET_UNANSWERED_ACCOUNT_SELECTIONS = "unansweredAccountSelections";
    public static final String RET_UNANSWERED_IDENTITIES = "unansweredAccountSelectionIdentities";

    /**
     * Who doesn't love context?
     */
    SailPointContext _context;

    /**
     * Identity being provisioned (if the plan has AccountRequests).
     */
    Identity _identity;

    /**
     * Previously compiled project containing plans and options.
     */
    ProvisioningProject _project;
    
    /**
     * System object used to refresh identities after significant structural
     * changes like link moves.
     */
    Identitizer _identitizer;

    /**
     * System object used to recalculate RoleDetection state after modifications
     * to the assigned or detected role lists.
     */
    EntitlementCorrelator _eCorrelator;

    /**
     * Transient flag set if we encounter an op=Create.
     */
    boolean _creating;

    /**
     * Runtime variable set to true if the IIQ Identity is modified
     * by the plan. 
     */
    boolean _identityUpdated;
    
    /**
     * Runtime variable set to true if something was found in the plan
     * that may require refreshing Request objects for scheduled assignments.
     */
    boolean _needsScheduledAssignmentRefresh;

    /**
     * Runtim variable set to true if we made changes to the assigned role
     * list that may have a side effect on role detections. 
     */
    boolean _needsReDetection;

    /**
     * The number of AccountSelections we found that did not have a selection.
     * This normally happens in identity refresh when you're reconciling role
     * assignments but do not have the option enabled to prompt for account selections.
     * We keep a count of those but don't log warnings.
     */
    int _unansweredAccountSelections;

    /**
     * A constrained list of identity names that had one or more
     * unanswered AccountSelections.  Unlike most state in IIQEvaluator
     * this does not reset on each call to provision().
     */
    Set<String> _unansweredAccountSelectionIdentities;

    //////////////////////////////////////////////////////////////////////
    // 
    // Interface
    //
    //////////////////////////////////////////////////////////////////////

    public IIQEvaluator(SailPointContext context) {
        _context = context;
    }

    /**
     * When used from a task, copy statistics to the result.
     */
    public void saveResults(TaskResult result) {

        result.setInt(RET_UNANSWERED_ACCOUNT_SELECTIONS, _unansweredAccountSelections);
        
        if (Util.size(_unansweredAccountSelectionIdentities) > 0)
            result.setAttribute(RET_UNANSWERED_IDENTITIES,  Util.setToCsv(_unansweredAccountSelectionIdentities));
    }

    /**
     * Evaluate the IIQ plan stored in the project and 
     * apply any committed connector plans.
     * 
     * This can be called in two contexts: the PlanEvaluator.execute
     * method and the PlanEvaluator.retry method.  The second is used
     * only for connector-specific plans that failed with a retryable
     * error.  In this case the project will not contain an IIQ plan.
     */
    public void provision(Identity identity, ProvisioningProject project)
        throws GeneralException {

        final String phase1 = "IIQEvaluator.provision";
        Meter.enterByName(phase1);

        // reset per-identity state
        _identity = identity;
        _project = project;
        _creating = false;
        _identityUpdated = false;
        _needsScheduledAssignmentRefresh = false;
        _needsReDetection = false;

        boolean noLinkUpdates = _project.getBoolean(PlanEvaluator.ARG_NO_LINK_UPDATE);
        boolean triggerSnapshots = _project.getBoolean(PlanEvaluator.ARG_TRIGGER_SNAPSHOTS);

        // Identitizer and EntitlementCorrelator we refresh every time
        // because in theory they can be sensitive to options in the project.
        _identitizer = null;        

        Attributes<String,Object> args = null;
        Object o = _project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
        if (o instanceof Map) {
            // IIQETN-6371 - ARG_REFRESH_OPTIONS is typically a Map (and could be a HashMap) so convert accordingly
            args = new Attributes<String, Object>((Map<String,Object>)o);
        }

        _eCorrelator = new EntitlementCorrelator(_context, args);

        // since we have to lock the identity, make sure there is
        // something we need to do
        ProvisioningPlan iiqPlan = _project.getIIQPlan();
        
        PlanApplier planApplier = new PlanApplier(_context, _project);
        List<ProvisioningPlan> applyablePlans = null;
        if (!noLinkUpdates)
            applyablePlans = planApplier.getApplyablePlans(_project, true);

        if (_identity != null && 
            (Util.size(applyablePlans) > 0 ||
             (iiqPlan != null && !iiqPlan.isEmpty()) ||
             hasScheduledOrAssignedAttribute())) {

            // Get a lock on the identity if we intend to actually modify it.
            // The lock may have already been acquired by a higher level.
                
            boolean lock = isLockingRequired();
            Identity locked = _identity;

            if (lock) {
                // Still having weird Hibernate errors if we have to 
                // delete a link.  We evict the current identity and
                // it appears that the link collection is evicted, but then
                // when we try to delete a link iterating over the Link
                // list produces Link objects that point back to the OLD
                // identity.  Deacache here to be safe.  This should not be
                // necessary but it's Hiberante...
                // not so fast, need to think about the consequences on the callers...
                // hmm, I have isolated what may or may not be a Hibernate bug
                // this needs more investigation, but until then decache so
                // the tests can pass - jsl 
                // UGH...unfortunately about 5 remediation tests fails when we
                // do this, which is probably a problem for remediations
                // but need time to sort this out...
                //_context.decache();
                // IIQMAG-3008 decache links.  This code was added due to a
                // problem where links stale, and during concurrent updates
                // of the same identities links, we could loose some of the
                // changes (full details in the bug).  This is possibly caused
                // by a hibernate bug - https://hibernate.atlassian.net/browse/HHH-12867
                // If that bug ever gets addressed, we should see if this code
                // is really needed.  It might perhaps address the issue in
                // the comment a couple lines up just prior to the lock.
                for(Link link : Util.safeIterable(identity.getLinks())) {
                    _context.decache(link);
                }
                //We also need to decache the identity
                _context.decache(_identity);
                locked = ObjectUtil.lockIdentity(_context, _identity);

            }
                    
            if (locked == null) {
                // deleted out from under us!
                log.error("Identity evaporated: " + _identity.getName());
            }
            else {
                try {
                    _identity = locked;

                    // bug 24659, if this is a new identity set a flag so that
                    // Create triggers will happen.  I don't particularly like
                    // doing this here but this changed with 18965 we don't
                    // just check for a null previous any more
                    if (_identity.getId() == null) {
                        _identity.setNeedsCreateProcessing(true, Identity.CreateProcessingStep.Trigger);
                    }
                    // apply connector plans first in case
                    // identity operations are sensitive to them
                    if (!noLinkUpdates) {
                        prepareIdentity();
                        planApplier.applyAcccountPlans(_identity, applyablePlans);
                    } else if (triggerSnapshots) {
                        Identitizer idz = getIdentitizer();
                        idz.storeTriggerSnapshots(_identity);
                    }

                    // then the IIQ plan
                    // this must be done even if noLInkUpdates so we can refresh
                    // assignment and detection metadata
                    final String phase2 = "IIQEvaluator.processIIQPlan";
                    Meter.enterByName(phase2);
                    processIIQPlan(iiqPlan);                    
                    Meter.exitByName(phase2);

                    final String phase3 = "IIQEvaluator phase 3";
                    Meter.enterByName(phase3);

                    // Everything in this block is relevant only if we're
                    // not under Identity Refresh.
                    if (!noLinkUpdates) {
                        // redetect roles if we made assignment changes
                        final String phase3_1 = "IIQEvaluator phase 3_1";
                        Meter.enterByName(phase3_1);
                        reDetectRoles();
                        Meter.exitByName(phase3_1);

                        // Update any entitlement assignments specified
                        // in the plan
                        final String phase3_2 = "IIQEvaluator phase 3_2";
                        Meter.enterByName(phase3_2);
                        processAttributeAssignments();
                        Meter.exitByName(phase3_2);

                        // Promote changes to the IdentityEntitlements table
                        // This must be done after redetecting and all other
                        // post-processing phases that may change the Identity
                        if (!isSimulating()) {
                            // If we haven't called planApplier.applyAccountPlans, we 
                            // have to call with our identity (with IQ
                            final String phase3_3 = "IIQEvaluator phase 3_3";
                            Meter.enterByName(phase3_3);
                            planApplier.setCorrelator(_eCorrelator);
                            planApplier.finish(_identity, _identityUpdated);
                            Meter.exitByName(phase3_3);
                        }

                        // Create or refresh Requests for scheduled assignment changes
                        final String phase3_4 = "IIQEvaluator phase 3_4";
                        Meter.enterByName(phase3_4);
                        scheduleAssignmentRequests();
                        Meter.exitByName(phase3_4);

                        // and finally do any refresh options
                        // this was formerly done a lot to get redetection but now
                        // that we do that automatically and we maintain the 
                        // IdentityEntitlements table in PlanApplier, forcing refresh
                        // should be rare
                        final String phase3_5 = "IIQEvaluator phase 3_5";
                        Meter.enterByName(phase3_5);

                        Identitizer.RefreshResult result = refreshIdentity(planApplier.getUpdatedLinks(), planApplier.getDeletedLinks());
                        if (result != null && result.deleted) {
                            // no need to unlock it 
                            _identity = null;
                        }
                        Meter.exitByName(phase3_5);

                    }
                    Meter.exitByName(phase3);
                } catch (Throwable th) {
                    
                    //Let someone know
                    log.error("Unable to complete provisionioning on user " + identity.getName() + ".", th);

                    
                    //Assume a GeneralException is a previously-handled exception,
                    //do not decache in case a user was supposed to be created etc.
                    //In case of a non-GeneralException, assume the worst.
                    if (!(th instanceof GeneralException)) {
                        try {
                            _context.decache();
                            _identity = ObjectUtil.reattach(_context, _identity);
                        }
                        catch (Throwable t) {
                            if (log.isErrorEnabled())
                                log.error("Unable to reattach identity after exception to release lock: " +
                                        _identity.getName(), t);
                        }
                    }
                    
                    //Don't double wrap in case of upstream handling
                    if (th instanceof GeneralException) {
                        throw th;
                    }
                    
                    //Wrap because who knows what this beast is
                    throw new GeneralException(th);
                }
                finally {
                    // _identity may have become null if was deleted by
                    // processLinks or a Delete AccountRequest
                    if (_identity != null) {
                        if (lock) {
                            // this will call saveObject and commit                            
                            ObjectUtil.unlockIdentity(_context, _identity);
                        }
                        else if (!isSimulating()) {
                            // We're either creating a new object or NO_LOCKING was on
                            // We are expected to commit the changes.
                            if (_identity.getId() == null && !_creating) {
                                // The identity has never been saved but we didn't
                                // find an op=Create in an AccountRequest.  We could
                                // throw here or just implicitly promote it to a 
                                // Create.  Let's be tolerant for now, but log since
                                // this most likely indicates a logic error somewhere.
                                log.error("Plan did not ask to create a new identity");
                            }

                            // have to do this to get passwords encrypted
                            //checking if it is a Restore deleted object operation (AD RecycleBin)
                            if(!Util.nullsafeBoolean(isIdentityNotRequired())){
                                _context.saveObject(_identity);
                            }
                            _context.commitTransaction();
                        }
                    }
                }
            }
        }

        planApplier.applyObjectPlans();

        Meter.exitByName(phase1);
    }

    /**
     * Return true if there is a sunrise or sunset data on any attribute requests
     * in the project.
     */
    private boolean hasScheduledOrAssignedAttribute() {

        boolean hasAttribute = false;
        ProvisioningPlan plan = _project.getMasterPlan();
        if (plan != null) {
            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {
                for (AccountRequest account : accounts) {
                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        for (AttributeRequest att : atts) {
                            if (PlanUtil.isScheduled(_project, att) ||
                                att.isAssignment()) {
                                hasAttribute = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return hasAttribute;
    }
    
    /**
     * Sift through the plans looking for assignment requests and
     * promote those to AttributeAssignment metadata.
     */
    private void processAttributeAssignments() throws GeneralException {
        
        if (!isSimulating() && _identity != null) {

            // check to make sure the ones we have on the identity are valid
            // remove the ones that aren't valid
            _identity.validateAttributeAssignments(_context);

            // the master plan has deferred assignments
            processAttributeAssignments(_project.getMasterPlan(), true);

            // the partitioned plans have non-deferred assignments
            List<ProvisioningPlan> plans = _project.getPlans();
            if ( plans != null ) {
                for (ProvisioningPlan plan : plans) {
                    // skip the IIQ plan
                    if (!plan.isIIQ()) {
                        processAttributeAssignments(plan, false);
                    }
                }
            }

            // iiqetn-435 look for assignments that were filtered
            List<AbstractRequest> filteredReqs = _project.getFiltered();
            for (AbstractRequest req : Util.iterate(filteredReqs)) {
                // don't think we can have ObjectRequests in here, but
                // ignore them if we do
                if (req instanceof AccountRequest) {
                    AccountRequest areq = (AccountRequest)req;
                    processAttributeAssignments(areq, areq.getAttributeRequests(), false, true);
                }
            }
            
        }
    }

    /**
     * Look for assignments in a plan.
     */
    private void processAttributeAssignments(ProvisioningPlan plan, boolean deferredOnly) 
        throws GeneralException {

        // retry plans won't have a master
        if (plan != null) {
            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {

                for (AccountRequest account : accounts) {
                    // Process any assigned attributes.
                    processAttributeAssignments(account, account.getAttributeRequests(), deferredOnly, true);

                    // Process any assigned permissions.
                    processAttributeAssignments(account, account.getPermissionRequests(), deferredOnly, false);

                    // Remove all attribute assignements if deleting the account.
                    if (account.getOperation() == AccountRequest.Operation.Delete) {
                        //Handle removals of associated assigned attributes
                        List<AttributeAssignment> aas= _identity.getAttributeAssignments();
                        if(null != aas && !aas.isEmpty()) {
                            Iterator<AttributeAssignment> aaIt = aas.iterator();
                            while(aaIt.hasNext()) {
                                AttributeAssignment aa = aaIt.next();
                                Application app = account.getApplication(_context);
                                if(attributeAssignmentMatchesAccountRequest(account, aa, app.isCaseInsensitive())){
                                    aaIt.remove();
                                }
                            }
                        }
                    }
                }
            }         
        }
    }

    /**
     * Look for assignments in a plan.
     */
    private <T extends GenericRequest> void processAttributeAssignments(AccountRequest acctReq, List<T> reqs,
                                                                        boolean deferredOnly, boolean isAttribute)
        throws GeneralException {

        if (reqs != null) {
            for (T req : reqs) {
                if (req.isAssignment() &&
                    (deferredOnly == PlanUtil.isDeferred(_project, req) ||
                     //Changes to sunsetDates for provisioned entitlmenets won't have integration plans.
                     (deferredOnly && PlanUtil.isScheduled(_project, req) && acctReq.getNativeIdentity() != null))) {
                    promoteAttributeAssignment(acctReq, req, isAttribute);
                }
            }
        }
    }

    /**
     * See if this account request could be linked to this attribute request
     * @param account
     * @param aa
     * @param caseInsensitive
     * @return
     */
    private boolean attributeAssignmentMatchesAccountRequest(AccountRequest account, AttributeAssignment aa, boolean caseInsensitive) {
        if(caseInsensitive) {
            if(Util.nullSafeCaseInsensitiveEq(aa.getNativeIdentity(), account.getNativeIdentity()) &&
               Util.nullSafeCaseInsensitiveEq(aa.getApplicationName(), account.getApplication()) &&
               Util.nullSafeCaseInsensitiveEq(aa.getInstance(), account.getInstance())) {
                  return true;
            }
        } else {
            if(Util.nullSafeEq(aa.getNativeIdentity(), account.getNativeIdentity()) &&
               Util.nullSafeEq(aa.getApplicationName(), account.getApplication()) &&
               Util.nullSafeEq(aa.getInstance(), account.getInstance(), true)) {
                  return true;
            }
        }
        
        return false;
    }

    /**
     * Generate an AttributeAssignment from a AttributeRequest.
     */
    private void promoteAttributeAssignment(AccountRequest account, GenericRequest req, boolean isAttribute)
        throws GeneralException {

        String name = req.getName();
        Object value = req.getValue();
        Application app = account.getApplication(_context);
        if ( app == null ) {
            // shouldn't happen
            log.warn("processAccountRequestsAssignments : Account request application resolution was null.");
        }

        if (app != null && name != null && value != null) {

            // this triggers later generation of Requests
            _needsScheduledAssignmentRefresh = true;
            Source source = null;
            String assignmentSourceStr = getAssignmentSource();
            if ( assignmentSourceStr != null )
                source = Source.valueOf(assignmentSourceStr);

            List<Object> objList = Util.asList(value);
            if ( Util.size(objList) > 0 ) {
                for ( Object val : objList ) {                            
                    ManagedAttribute.Type type =
                        (isAttribute) ? ManagedAttribute.Type.Entitlement : ManagedAttribute.Type.Permission;

                    // Deal with Assignments
                    AttributeAssignment assignment = new AttributeAssignment(app, 
                                                                             account.getNativeIdentity(),
                                                                             account.getInstance(),
                                                                             name, 
                                                                             val, 
                                                                             getAssigner(),
                                                                             source,
                                                                             type,
                                                                             req.getAssignmentId());
                    assignment.setStartDate(req.getAddDate());
                    assignment.setEndDate(req.getRemoveDate());
                    assignment.setTargetCollector(req.getTargetCollector());

                    if (req.getOperation() == Operation.Remove && 
                        assignment.getEndDate() == null) {
                        // can remove it now
                        _identity.remove(assignment);
                    }
                    else {
                        // add or replace the current one
                        _identity.add(assignment);
                    }
                }
            }
        }
    }

    /**
     * Return true if we're simulating.
     * This disables locking and prevents the transaction from being committed.
     */
    private boolean isSimulating() {
        return (_project.getBoolean(PlanEvaluator.ARG_SIMULATE));
    }

    /**
     * Returns true if ARG_IDENTITY_NOT_REQUIRED is true.
     * ARG_IDENTITY_NOT_REQUIRED is used in the workflow 'Restore Deleted Objects'
     * for restoring deleted Object of Active Directory
     */
    private boolean isIdentityNotRequired() {
        return (_project.getBoolean(PlanEvaluator.ARG_IDENTITY_NOT_REQUIRED));
    }
    
    /**
     * Return true if we should lock the identities touched by this plan.
     * If the simulation option is on we neither lock or commit.
     * If the identity has no id this is a create so there is nothing to lock.
     * The ARG_NO_LOCKING option is set when the Identizer is being used
     * by something that is maintaining the lock so we don't have to.
     */
    private boolean isLockingRequired() {

        return (!_project.getBoolean(PlanEvaluator.ARG_NO_LOCKING) &&
                !isSimulating() &&
                _identity.getId() != null);
    }

    /**
     * Called before we're about to apply a ProvisioningPlan to an Identity.
     * The state we're in now is similar to the Aggregator when it
     * receives a new ResourceObject.  There are things we can do
     * before applying the plan that helps us make decisiosn later.
     */
    private void prepareIdentity() throws GeneralException {

        // refresh is disabled if we're simulating because it is too hard
        // to ensure that the Identitizer stack won't commit
        // might need to relax this for certain subsets of options...

        if (_project.getBoolean(PlanEvaluator.ARG_DO_REFRESH) &&
            !isSimulating()) {
        
            Identitizer idz = getIdentitizer();

            // need this for trigger detection
            idz.storeTriggerSnapshots(_identity);

            // need this for attribute promotion
            // TODO: We could be setting these dynamically as we process
            // AccountRequests rather than assuming all Links need them
            
            List<Link> links = _identity.getLinks();
            if (links != null) {
                for (Link link : links) {
                    Attributes<String,Object> atts = link.getAttributes();
                    if (atts != null) {
                        // !! since we're always doing incremental changes
                        // here we tehnically need to be copying the values
                        // of all multi-valued attributes as well as
                        // the containing map.  I don't think this is a problem
                        // at the moment because we only use the old map
                        // to detect changes to scalara...true?
                        Attributes<String,Object> old = new Attributes<String,Object>(atts);
                        link.setOldAttributes(old);
                    }
                }
            }
        }
    }

    /**
     * This is called after we have applied AccountRequests directly
     * to the Links due to the connector returning STATUS_COMMITTED,
     * the ARG_LOCAL_UPDATE option or the ARG_SIMULATE option.
     * In the first two cases, we want to optionally treat this like
     * "targeted reaggregation" and do some of the same things that
     * the aggregation and refresh tasks do.
     *
     * The refresh is currently controlled by the ARG_DO_REFRESH option.
     * !! Think...there are some things in Identitizer.refreshLinks
     * that we may want to do all the time:
     * 
     *    correlation key updates, set last refresh data
     *
     * The things we absolutely don't want to do all the time are
     * link attribute promotion and making a trigger snapshot.
     *
     */
    private Identitizer.RefreshResult refreshIdentity(List<Link> updatedLinks, 
                                                      List<Link> deletedLinks) 
        throws GeneralException {

        Identitizer.RefreshResult result = null;

        // refresh is disabled if we're simulating because it is too hard
        // to ensure that the Identitizer stack won't commit
        // might need to relax this for certain subsets of options...

        if (_project.getBoolean(PlanEvaluator.ARG_DO_REFRESH) &&
            !isSimulating()) {
            
            // only go through this if the plan did something
            // we don't actually need the _deletedLinks list
            // just knowing we had some is enough to trigger refresh

            if (_identityUpdated ||
                ( Util.size(updatedLinks) > 0) ||
                ( Util.size(deletedLinks) > 0 ) ) {

                Identitizer idz = getIdentitizer();

                // first refresh all of the modified links
                for (Link link : updatedLinks ) {
                    idz.refreshLink(link);
                }

                // then refresh the owning identity
                result = idz.refresh(_identity);

                // if result.committed we don't necessarily have to do it
                // again, but we will to release the lock, not bothering
                // with this for now but we might be able to avoid
                // a DB hit in some cases?

                // TODO: Refresh can contain a lot of other stuff too,
                // including error message, where should those go? 
                // The IIQ ProvisioningResult?
            }
        }

        return result;
    }

    /**
     * After evaluating the IIQ plan check for the need to rerun
     * role detection to reflect the side effect of assignment changes.
     * First the _needsReDetection flag must be on which means that we had
     * at least one add or remove request for the assignedRoles attributes.
     * Next check if role expansion was disabled, if so then the plan won't
     * have any role-related changes so we don't bother redetecting.
     * Next check if proivisoning was disabled, if so then even if the plan
     * contained role changes we won't have sent them or changed the Links
     * so don't bother redetecting.
     */
    private void reDetectRoles() throws GeneralException {

        // ignore if we didn't change the assignment list or
        // change any of the Links
        if (_needsReDetection )
            /* DF: Bug#20908 -- We need to redetect roles for LCM request, even it only has IIQ plan.
                             -- This may have performance impact on identity refresh.
            && 
            !_project.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION) &&
            !_project.getBoolean(PlanCompiler.ARG_NO_ROLE_PROVISIONING) &&
            hasDetectableChanges()
            */
        {

            // kludge: assume if this flag is on then we're from the Identitizer and
            // don't need to redetect anything, we really should be restoring all of the
            // logic above but I don't remember why this was taken out - jsl
            if (!_project.getBoolean(PlanEvaluator.ARG_NO_LINK_UPDATE))
                _eCorrelator.redetect(_identity);
        }
    }

    /**
     * Helper for reDetectRoles.
     * Return true if the plan contains anything that was committed.
     * We only need to redetect if there was at least one successful connector
     * plan.  We could use isProjectSuccessful and require all plans be successful
     * but even if one succeeds there may be something to detect.
     */
    private boolean hasDetectableChanges() {

        boolean changes = false;

        List<ProvisioningPlan> plans = _project.getPlans();
        for (ProvisioningPlan plan : Util.iterate(plans)) {
            String target = plan.getTargetIntegration();
            if (target == null) {
                // it's the unmanged plan, ignore
            }
            else if (target.equals("IIQ")) {
                // IIQ plan, ignore
            }
            else {
                // walk over the results
                if (plan.isFullyCommitted()) {
                    // only one is required
                    changes = true;
                    break;
                }
            }
        }
        return changes;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the identitizer to use for cube refreshes.
     *
     * The refreshOptions map may contain an explicit set of options.
     * The keys in the map are the same as those recognized by the
     * identity refresh task (the Identitizer).   If this is not
     * specified and doRefresh is enalbed, we will by default
     * assume these options:
     *
     *     promoteLinkAttributes
     *     promoteAttributes
     *     correlateEntitlements
     *     
     * Maybe:
     *
     *     noManagerCorrelation
     *     promoteManagedAttributes
     *     correlateScope
     *     processTriggers
     *
     * The following options are forced off to prevent recursion:
     *
     *     provision
     *     provisionIfChanged
     *         
     */
    private Identitizer getIdentitizer() throws GeneralException {
        
        if (_identitizer == null) {
            Attributes<String,Object> args = new Attributes<String,Object>();

            Object o = _project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
            if (o instanceof Map) {
                // user must specify all options
                args.putAll((Map)o);
            }
            else {
                // build a default set
                args.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
                args.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, true);
            }
            
            // never allowed
            args.remove(Identitizer.ARG_PROVISION);
            args.remove(Identitizer.ARG_PROVISION_IF_CHANGED);

            _identitizer = new Identitizer(_context, args);

            _identitizer.setRefreshSource(Source.UI, _project.getRequester());
            _identitizer.prepare();
        }

        return _identitizer;
    }

    /**
     * Get an Identitizer suitable for link moves.
     *
     * !! These are the options we have historically used for link moves,
     * this seems like too many reconsider this!  We may need different
     * option sets for different refreshes, and the options may need to 
     * come in from the project rather than hard coded down here!
     *
     * ARG_PRUNE_IDENTITIES used to be in here but I don't think 
     * that should be assumed.  They can prune on the next refresh
     * if necessary.  One thing it does cause is 
     * IdentityLibrary.provisionProject to lose the Identity and emit
     * a warning.  
     *
     * If we already have an _identitizer created for ARG_DO_REFRESH
     * use that and require that these options be passed down.
     * This is okay because the only thing making link move plans
     * right now is the manual correlation UI and that's all it does.
     */
    private Identitizer getLinkMoveIdentitizer() {

        if (_identitizer == null) {
            Attributes<String,Object> args = new Attributes<String,Object>();
            
            Object o = _project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
            if (o instanceof Map) {
                // user must specify all options
                args.putAll((Map)o);
            }
            else {
                args.put(Identitizer.ARG_NO_MANAGER_CORRELATION, true);
                args.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
                args.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, true);
                args.put(Identitizer.ARG_REFRESH_IDENTITY_ENTITLEMENTS, true);
            }
            
            _identitizer = new Identitizer(_context, args);
            _identitizer.setRefreshSource(Source.UI, _project.getRequester());
        }

        return _identitizer;
    }

    /**
     * Return the name of the identity considered to be the actor behind
     * the plan.   This is used for metadata that tracks role assignment
     * and manual identity attribute edits.
     */
    private String getAssigner() {

        String assigner = _project.getString(PlanEvaluator.ARG_ASSIGNER);

        if (assigner == null)
            assigner = _project.getRequester();

        // !! NOOO never default to the context owner
        // Once the RoleAssignment assigner becomes non-null this is considered
        // "manually assigned" and can only be removed manually.  For rule-based
        // role assignment this must not be set, the assigner and requester
        // won't be in the project and the context user is something
        // like "RequestProcessor" or "System".
        // Think...we may not even want the project requester here?  Make
        // them explicitly set assigner?
        /*
        if (assigner == null)
            assigner = _context.getUserName();
        */

        return assigner;
    }

    /**
     * Return the assignment "source" from the project.
     * These are expected to be Source enumeration names
     * for use in RoleAssignment metadata when we add role assignments.
     */
    private String getAssignmentSource() {

        String source = _project.getString(PlanEvaluator.ARG_SOURCE);
        if (source == null)
            source = Source.Unknown.toString();

        return source;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Plan Evaluation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a ProvisioningPlan for IIQ identities.
     * Normally this should have a single AccountRequest, though
     * I suppose we could allow more than one.
     * 
     * For each AccountRequest:
     * 
     * The Operation is ignored and assumed to be Modify.
     * The application is ignored but normally will be "IIQ".
     * The instance is ignored.
     * The permissions list is ignored.
     * The applications list is expected to have something.
     * 
     * The nativeIdentity is ignored since the Identity was passed in
     * as an argument to compile().  In theory we could use this
     * to bundle changes to several cubes in one plan but I don't
     * see a need.
     */
    private void processIIQPlan(ProvisioningPlan iiqPlan)
        throws GeneralException {

        if (iiqPlan != null) {
            List<AccountRequest> requests = iiqPlan.getAccountRequests();
            if (requests != null) {
                for (AccountRequest req : requests) {
                    processIIQPlan(req);

                    // We have been copying the target integration to each
                    // AccountRequest for the RemediationManager.  I'd rather
                    // that that be done when the RM makes it's itemized
                    // plans for tracking, but continue to do this for now.
                    req.setTargetIntegration(ProvisioningPlan.APP_IIQ);
                    if (!isSimulating()) {
                        logIIQTransaction(iiqPlan, req);
                    }

                    // it doesn't matter what we did, set this so it 
                    // may cause a refresh at the end
                    _identityUpdated = true;
                }
            }
            
        }
    }

    private void processIIQPlan(AccountRequest accreq)
        throws GeneralException {

        // Set a flag if we encounter a Create so we can check later whether
        // we're supposed to create a new one or not.
        // Prior to 5.2 we did the saveObject call down here because 
        // "otherwhse you'll get "unsaved transient instance"".
        // I don't know if that's true any more but we have to defer this
        // until after we've processed the AttributeRequests so we can
        // encrypt the password.
        _creating = ObjectOperation.Create.equals(accreq.getOp());

        // If we get an AccountRequest to Delete an IIQ Identity,
        // call the terminator to delete the identity.
        if (ObjectOperation.Delete.equals(accreq.getOp()) &&
                ProvisioningPlan.APP_IIQ.equals(accreq.getApplication())) {
            deleteIdentity();
        }

        List<AttributeRequest> attreqs = accreq.getAttributeRequests();
        if (attreqs != null) {
            List<AttributeRequest> otherRequests = new ArrayList<AttributeRequest>();
            
            /* To properly match up permitted role assignments with top level assignments, 
             * we have to process assignedRole requests first. */
            for (AttributeRequest attreq : attreqs) {
                if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attreq.getName())) {
                    processIIQPlan(attreq);
                } else {
                    otherRequests.add(attreq);
                }
            }
            for (AttributeRequest attreq : otherRequests) {
                processIIQPlan(attreq);
                // processLinks can in theory delete the _identity
                // if all the links are moved away, make sure we stop 
                // processing the plan if that happens
                if (_identity == null)
                    break;
            }
        }
    }

    /**
     * Process one AttributeRequest from an IIQ plan.
     * Attribute/method mapping is getting a bit unwieldy.
     *
     * NOTE: I'm assuming we can use the preference name
     * constants (Identity.PRF_...) as is without any extra
     * prefix.  This means that you can't have an identity
     * attribute named the same as one of the built-in preferneces.
     * May want to revisit this...
     */
    private void processIIQPlan(AttributeRequest req)
        throws GeneralException {

        // auto-upgrade permitted role metadata while we're here
        // actually no, this seems arbitrary and dangerous, rethink this
        //upgradeRoleRequests(_context, _identity);

        String name = req.getName();
        if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name)) {
            processAssignedRoles(req);
            _needsScheduledAssignmentRefresh = true;
            _needsReDetection = true;
        }
        else if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name)) {
            processDetectedRoles(req);
            _needsScheduledAssignmentRefresh = true;
            _needsReDetection = true;
        }
        else if (ProvisioningPlan.ATT_IIQ_LINKS.equals(name)) {
            processLinks(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_CAPABILITIES_NEW.equals(name) || (ProvisioningPlan.ATT_IIQ_CAPABILITIES.equals(name)) ) {
            processCapabilities(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_ACTIVITY_CONFIG.equals(name)) {
            processActivityConfig(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_WORKGROUPS.equals(name)) {
            processWorkgroups(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES_NEW.equals(name) || ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES.equals(name)) {
            processControlledScopes(req);
        }
        else if (ProvisioningPlan.ATT_ASSIGNED_SCOPE.equals(name) || ProvisioningPlan.ATT_IIQ_SCOPE.equals(name)) {
            processScope(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_ARCHIVES.equals(name)) {
            processArchives(req);
        } 
        else if (ProvisioningPlan.ATT_IIQ_SNAPSHOTS.equals(name)) {
            processSnapshots(req);
        } 
        else if (ProvisioningPlan.ATT_IIQ_EVENTS.equals(name)) {
            processEvents(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_PROVISIONING_REQUESTS.equals(name)) {
            processProvisioningRequests(req);
        }
        else if (ProvisioningPlan.ATT_PASSWORD.equals(name)) {
            processPassword(req);
        }
        else if (ProvisioningPlan.ATT_IIQ_CONTROLS_ASSIGNED_SCOPE.equals(name)) {

            // This can be set to null if the user is supposed to use the system default
            // See bug 6749 -- Bernie
            Boolean b = (Boolean)req.getValue();
            _identity.setControlsAssignedScope(b);
        }
        else if (Identity.PRF_FORWARD.equals(name)) {

            String identityName = null;
            Object value = req.getValue();
            if (value instanceof Identity) 
                identityName = ((Identity)value).getName();
            else if (value != null)
                identityName = value.toString();

            _identity.setPreference(Identity.PRF_FORWARD, identityName);
        }
        else if (Identity.PRF_FORWARD_START_DATE.equals(name)) {

            Date d = Util.getDate(req.getValue());
            _identity.setPreference(Identity.PRF_FORWARD_START_DATE, d);
        }
        else if (Identity.PRF_FORWARD_END_DATE.equals(name)) {

            Date d = Util.getDate(req.getValue());
            _identity.setPreference(Identity.PRF_FORWARD_END_DATE, d);
        }
        else if (Identity.PRF_USE_BY_DATE.equals(name)) {
            Date d = Util.getDate(req.getValue());
            _identity.setUseBy(d);
        } 
        else if (  Identity.ATT_PROTECTED.equals(name) ) {
            boolean b = Util.otob(req.getValue());
            _identity.setProtected(b);
        }
        else if ( Identity.ATT_MANAGER.equals(name) ) {
            Identity current = _identity.getManager();
            String currentName = null;
            if ( current != null ) 
                currentName = current.getName();

            Identity manager = null;
            Object val = req.getValue();
            if ( val != null ) {
                String strVal = val.toString();
                if ( Util.getString(strVal) != null ) {
                    //Use Name for manager? -rap
                    manager = _context.getObjectByName(Identity.class, strVal);
                }                
            }
            _identity.setManager(manager);
            ObjectAttribute att = Identity.getObjectConfig().getObjectAttribute(Identity.ATT_MANAGER);
            updateAttributeMetadata(att, currentName);
        } 
        else if (ProvisioningPlan.ATT_IIQ_NEEDS_REFRESH.equals(name)) {
            _identity.setNeedsRefresh(Util.otob(req.getValue()));
        }
        else if (Identity.ATT_TYPE.equals(name)) {
            Object value = req.getValue();
            // validate the Identity type against the config first
            if (!Identity.isValidTypeValue(value)) {
                if (log.isWarnEnabled()) {
                    log.warn("Identity type value [" + value + "] not found in IdentityConfig. Skipping setting of the attribute.");
                }
            } else {
                // otherwise continue as we were
                processIdentityAttribute(req);
            }
        }
        else {
            // else assume it's a random attribute
            // can't MANAGER be handled this way?
            processIdentityAttribute(req);
        }
    }

    /**
     * Logs the provisioning transactions in the provisioning transaction table
     * targeted at the IIQ application.
     *
     * @param iiqPlan The iiq plan.
     * @param request The account request.
     * @throws GeneralException
     */
    private void logIIQTransaction(ProvisioningPlan iiqPlan, AccountRequest request)
        throws GeneralException {

        TransactionDetails details = new TransactionDetails();
        details.setProject(_project);
        details.setPartitionedPlan(iiqPlan);
        details.setRequest(request);

        // should always be here but doesn't hurt to check
        if (_identity != null) {
            details.setIdentityName(_identity.getName());
        }

        // check project then master plan
        String source = (String) _project.get(ProvisioningPlan.ARG_SOURCE);
        if (Util.isNullOrEmpty(source) && _project.getMasterPlan() != null) {
            source = _project.getMasterPlan().getSource();
        }

        details.setSource(source);

        ProvisioningTransactionService transactionService = new ProvisioningTransactionService(_context);
        transactionService.setCommit(false);
        transactionService.logTransaction(details);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Assigned Roles
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a change to the assigned roles list.
     * We've already done the side effects for the integrated
     * IDM systems, here we just manage the assigned role list,
     * the list of role assignment metadata, and the scheduling
     * of sunrise/sunset events.
     *
     * SUNRISE/SUNSET dates are defined as arguments in the AttributeRequest,
     * see comments at the top of ProvisioningPlan for more information.
     * Basically seeing any Add/Set/Remove request cancels any 
     * previous sunrise sunset dates and may or may not set new ones.
     * Plan compiler should have removed dates that were before the
     * current time so we don't have to mess with that.
     *
     * After the RoleAssignment metadata has been updated we then
     * reconcile them with the Request objects that have been scheduled
     * to process them.
     *
     * NOTE WELL: You can't remove sunrise/sunset dates here
     * if they happen to be past the expiration point.  You have to 
     * go ahead and schedule events to handle them.  The reason is that
     * there are two parts to assignments the "expansion" into
     * attribute requests for the low-level entitlements and the 
     * IIQ role assignment list.  If we get here with a non-null
     * date it means that PlanCompiler did NOT do expansion so
     * we must also NOT change the assignment list.  Schedulling the
     * event, even if it gets picked up immediately, will ensure
     * that we start over and do both the entitlment expansion
     * and the assignment list change at the same time.
     *
     */
    private void processAssignedRoles(AttributeRequest req)
        throws GeneralException {

        Operation op = req.getOp();

        // ignore Retains, they're either there or we ignore them,
        // you cannot sunrise/sunset with this op
        if (op == Operation.Retain)
            return;

        // allow name, object, name list, or object list
        List<Bundle> reqRoles = ObjectUtil.getObjects(_context, 
                                                      Bundle.class,
                                                      req.getValue(),
                                                      // trust rules
                                                      true,
                                                      // throw exceptions
                                                      false, 
                                                      // convert CSV to List
                                                      false);
                                                      
        
        // Rebuild this list
        List<Bundle> assignedRoles = _identity.getAssignedRoles();
        if (assignedRoles == null)
            assignedRoles = new ArrayList<Bundle>();

        // keep track of the what we did for later detection 
        List<RoleAssignment> provisionedRoles = new ArrayList<RoleAssignment>();
        List<RoleAssignment> deprovisionedRoles = new ArrayList<RoleAssignment>();

        // Set and Add logic is similar, but merging them makes it harder
        // to read than just duplicating the little there share

        if (op == null || op == Operation.Set) {
            if (log.isInfoEnabled())
                logRoleList("Setting roles: ", reqRoles);
            
            // start over with new lists
            assignedRoles = new ArrayList<Bundle>();
            List<RoleAssignment> newMetadata = new ArrayList<RoleAssignment>();

            if (reqRoles != null) {
                for (Bundle role : reqRoles) {

                    // transfer or create RoleAssignment
                    RoleAssignment assignment = getRoleAssignment(req, role);
                    if (assignment == null)
                        assignment = new RoleAssignment(role);
                    newMetadata.add(assignment);
                    refreshRoleAssignment(req, assignment, role);

                    // add to the current assignment list if not sunrising
                    if (req.getAddDate() == null) {
                        provisionedRoles.add(assignment);
                        if (!assignedRoles.contains(role))
                            assignedRoles.add(role);
                    }
                }
            }

            _identity.setRoleAssignments(newMetadata);
        }
        else if (op == Operation.Add) {
            if (log.isInfoEnabled())
                logRoleList("Adding roles: " , reqRoles);

            if (reqRoles != null) {
                for (Bundle role : reqRoles) {

                    // create or refresh metadata
                    RoleAssignment assignment = getRoleAssignment(req, role);
                    if (assignment == null) {
                        assignment = new RoleAssignment(role);
                        _identity.addRoleAssignment(assignment);
                    }
                    refreshRoleAssignment(req, assignment, role);

                    // add to the current assignment list if not sunrising
                    if (req.getAddDate() == null) {
                        provisionedRoles.add(assignment);
                        if (!assignedRoles.contains(role))
                            assignedRoles.add(role);
                    }

                    // since the op is Add remove any negative assignments for this role, we
                    // don't need to do this for Set because all old assignments will be blown away
                    removeNegativeRoleAssignments(assignment.getRoleName());
                }
            }
        }
        else {
            // remove or revoke
            if (log.isInfoEnabled())
                logRoleList("Removing roles: ", reqRoles);

            if (reqRoles != null) {
                for (Bundle role : reqRoles) {

                    // remove or refresh the metadata
                    RoleAssignment assignment = getRoleAssignment(req, role);
                    if (req.getRemoveDate() != null || op == Operation.Revoke || req.getBoolean(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT)) {
                        if (assignment == null) {
                            assignment = new RoleAssignment(role);
                            _identity.addRoleAssignment(assignment);
                        }
                        refreshRoleAssignment(req, assignment, role);

                    }
                    else {
                        // no longer need this
                        _identity.removeRoleAssignment(assignment);
                    }

                    // remove the role if no sunset
                    if (req.getRemoveDate() == null) {
                        assignedRoles.remove(role);
                        if (assignment != null)
                            deprovisionedRoles.add(assignment);
                    }
                }
            }
        }

        // update the cube with what we have left over
        // the RoleAssignment list will have been incrementally updated above
        _identity.setAssignedRoles(assignedRoles);

        // Fix inconsistencies between the two assignment lists
        reconcileRoleAssignments();

        // update the summary strings
        _identity.updateAssignedRoleSummary();

        // regenerate detections for these assignments
        // incremental detection, doesn't work
        // autoDetectRolesIncremental(provisionedRoles, deprovisionedRoles);
    }

    /**
     * Removes any negative role assignments for the specified role from the identity.
     *
     * @param roleName The role name.
     */
    private void removeNegativeRoleAssignments(String roleName) {
        List<RoleAssignment> negativeAssignments = _identity.getNegativeRoleAssignments(roleName);
        for (RoleAssignment negativeAssignment : Util.iterate(negativeAssignments)) {
            _identity.removeRoleAssignment(negativeAssignment);
        }
    }

    /**
     * Look for a RoleAssignment object matching an AttributeReuqest for an
     * assigned role.  Starting with 6.3, the AttributeRequest must be tagged
     * with an assignmentId.
     *
     * Ugh, unless it is an op=Set with multiple values.  That probably never
     * happens but if it does have to assume that duplicates are not allowed.
     * PlanCompiler will have left the id null so it looks like an old plan.
     */
    private RoleAssignment getRoleAssignment(AttributeRequest request, 
                                             Bundle role) {

        RoleAssignment assignment = null;

        String aid = request.getAssignmentId();
        if (aid == null) {
            // not supposed to happen, could be a plan from a previous release
            // have to assume it's the first one
            assignment = _identity.getFirstRoleAssignment(role);
        }
        else {
            List<RoleAssignment> assignments = _identity.getRoleAssignments(role);
            for(RoleAssignment assigned : Util.iterate(assignments)) {
                if(aid.equals(assigned.getAssignmentId())) {
                    assignment = assigned;
                }
            }
            if (assignment == null && isUsedFirstAssignment(request)) {
                // Normlaly this can't happen but if we were dealing with an
                // old Identity that did not have upgraded RoleAssignments there
                // would be no assignmentId and we generated one for this project.
                assignment = _identity.getRoleAssignment(role);
                if (assignment != null && assignment.getAssignmentId() != null) {
                    // someone came along and gave it one in another provision.
                    // It's okay to use this though note that the ids will be different
                    // so don't overwrite it!
                    // Ideally we should check to make sure there is only
                    // one since we're guessing
                }
            }
        }

        return assignment;
    }

    /**
     * Determines if the first assignment was used for this request.
     * @param request The attribute request.
     * @return True if first assignment was used, false otherwise.
     */
    private boolean isUsedFirstAssignment(AttributeRequest request) {
        return request.getBoolean(AssignmentExpander.ATT_USED_FIRST_ASSIGNMENT);
    }

    /**
     * Formerly called Identity.refreshRoleAssignments but we're trying to phase
     * that out and the logic we need now is slightly different.  The intent here
     * is that we try to fix corruption in the assignment and RoleAssignment lists,
     * but starting in 6.3 the RoleAssignment list is authoritative rather  than
     * the Identity.assignedRoles list.  
     *
     * For each RoleAssignment that does not have a matching Bundle in the
     * assignedRoles list, add one.
     *
     * If a Bundle exists on the assignedRoles list that does not have at least
     * one matching RoleAssignment, remove it.
     *
     * Might have to put this back on Identity depending on how the
     * Identitizer and EntitlementCorrelator changes go.
     */
    public void reconcileRoleAssignments() throws GeneralException {

        List<RoleAssignment> assignments = _identity.getRoleAssignments();
        List<Bundle> bundles = _identity.getAssignedRoles();

        // make search Maps just in case the user has many roles
        Map<String,Bundle> bundleMap = new HashMap<String,Bundle>();
        Map<String,Bundle> bundleIdMap = new HashMap<String,Bundle>();
        if (bundles != null) {
            for (Bundle b : bundles) {
                bundleMap.put(b.getName(), b);
                bundleIdMap.put(b.getId(), b);
            }
        }

        Map<String,RoleAssignment> assignmentMap = new HashMap<String,RoleAssignment>();
        Map<String,RoleAssignment> assignmentIdMap = new HashMap<String,RoleAssignment>();
        if (assignments != null) {
            for (RoleAssignment ra : assignments) {
                // only include the non-negative assignments, 
                // and the ones without a sunrise unless ignoring sunrise dates
                // ?? should we check to see if the start date has passed, no there
                // may be extra stuff in the sunrise workflow that needs to run
                if (!ra.isNegative() && (_project.getBoolean(PlanEvaluator.ARG_IGNORE_SUNRISE_DATE) || ra.getStartDate() == null)) {
                    assignmentMap.put(ra.getRoleName(), ra);
                    assignmentIdMap.put(ra.getRoleId(), ra);
                }
            }
        }

        if (bundles != null) {
            ListIterator<Bundle> it = bundles.listIterator();
            while (it.hasNext()) {
                Bundle b = it.next();
                if (assignmentMap.get(b.getName()) == null && assignmentIdMap.get(b.getId()) == null) {
                    // dangling bundle
                    it.remove();
                }
            }
        }
        
        // iterate over the map that has already been filtered for negatives and sunrises
        for (RoleAssignment ra : assignmentMap.values()) {
            if (bundleIdMap.get(ra.getRoleId()) == null && bundleMap.get(ra.getRoleName()) == null) {
                // missing bundle
                // sigh, we fetched this above but RA doesn't have a handle to it
                Bundle b = null;
                if (Util.isNotNullOrEmpty(ra.getRoleId())) {
                    b = _context.getObjectById(Bundle.class, ra.getRoleId());
                } else if (Util.isNotNullOrEmpty(ra.getRoleName())) {
                    b = _context.getObjectByName(Bundle.class, ra.getRoleName());
                }
                
                if (b != null)
                    bundles.add(b);
                else {
                    // else, evaporated in the millisecond since we fetched it the
                    // last time, shouldn't happen
                    log.error("Role " + ra.getRoleName() + " (" + ra.getRoleId() + ") could not be found");
                }
            }
        }

    }

    /**
     * Given a newly created or previous RoleAssignment, update it
     * to reflect the request options.
     *
     * Prior to 6.0 we would only update assigner and source
     * if they were left null in the RoleAssignment.  In practice
     * I don't think they ever were, rule based assignment
     * would have assigner="spadmin" and source="Rule" and
     * manual assignment would have assidner="anybody" and
     * source="UI".  
     *
     * In retrospect, this is wrong.  While we normally prevent
     * the UI from assigning a role that has already been assigned
     * at the plan level it nedds to be allowed.  One use for this
     * is to convert something that was automatically assigned
     * into something that is permanently assigned, and to to that
     * you have to change the assigner and source.
     *
     * This also raised issues with the isNegative flag set
     * when you revoke a role from a cert.  The negative flag
     * needs to be obeyed for automated assignment (src="Rule")
     * but if someone does any form of manual assignment
     * we need to clear the flag.  This "promotion" is a bit
     * like the previous case, not only do we need to clear the
     * flag but we also need to reset the source and assigner.
     *
     * So the rules are: 
     *
     *   - If current source is Rule and new source is set, use new source.
     *     Anything other than Rule is considered "manual" and promotes
     *     the assignment.  The one exception may be Source.BATCH.
     *
     *   - If the new source is Rule, it does not override the current
     *     assigner or source.  This normally shouldn't happen since
     *     the plan compliation should have filtered out redundant 
     *     assignments.
     *
     *   - If the source is being changed, the assigner is also changed.
     *
     *   - If the assignment is currently negative, it becomes positive
     *     if the op is Set or Add.
     *
     * Beyond that, we may need to think all of the possible 
     * sailpoint.object.Source's and see if there is any kind of priority
     * among them.  I'm reluctant to replace the source in all cases
     * because you could lose something, like say start out with
     * source="UI"/assigner="joe" but then later get 
     * source"BATCH"/assigner="spadmin".  Perhaps not because the batch
     * requestor should do filtering too, but be safe until we can
     * consider all the Source combinations.  
     *     
     */
    private void refreshRoleAssignment(AttributeRequest req, RoleAssignment assignment, Bundle bundle)
        throws GeneralException {

        // if there is no assignment date set one, actually since
        // the constructors all set a date, this technically isn't
        // necessary but I don't like relying on default objects
        if (assignment.getDate() == null)
            assignment.setDate(new Date());

        String curSource = assignment.getSource();
        String newSource = getAssignmentSource();   // defaults to UI
        Operation op = req.getOp();

        // subtle logic warning
        // reset source if we're revoking, the source is uninitialized,
        // the source is being raised to a higher level, or we're
        // promoting a negative 

        //Never ser source on RoleAssignment to RoleChangePropagation
        if (!Source.RoleChangePropagation.toString().equals(newSource) &&
                (curSource == null ||
                op == Operation.Revoke ||
                (assignment.isNegative() && op != Operation.Revoke) ||
                (Source.Rule.toString().equals(curSource) &&
                 newSource != null &&
                 !Source.Rule.toString().equals(newSource)))) {

            assignment.setSource(newSource);
            assignment.setAssigner(getAssigner());
            assignment.setNegative(false);
            // this is important for negatve promotion, we could leave
            // the old date for the others but it seems more consistent 
            // to have this match the new assigner and source
            assignment.setDate(new Date());
        }
        else if (newSource != null && !newSource.equals(curSource)) {
            log.info("Ignoring conversion from source " + curSource + 
                     " to " + newSource);
        }

        // if this is a Revoke or negative assignment has been requested
        // then mark the assignment as negative
        // do not set negative for sunsets, wait for the "real" remove
        if (op == Operation.Revoke ||
                (req.getRemoveDate() == null && req.getBoolean(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT))) {
            assignment.setNegative(true);
        }

        if (op == Operation.Remove || op == Operation.Revoke) {
            // sunset is updated, sunrise is canceled
            // in theory we could allow a sunrise here, but I think
            // op=Add should be used for that
            assignment.setStartDate(null);
            assignment.setEndDate(req.getRemoveDate());
        }
        else {
            // buth sunrise and set are updated or canceled
            assignment.setStartDate(req.getAddDate());
            assignment.setEndDate(req.getRemoveDate());
        }

        // assign an id if this is new
        // note that ProvisioningProject will have temporary ids for this
        // assignment, have to leave those in place 
        if (assignment.getAssignmentId() == null) {
            // use assignment id on request if exists and is not temp otherwise generate
            String assignId = req.getAssignmentId();
            if (Util.isNullOrEmpty(assignId) || AssignmentExpander.isTemporaryAssignmentId(assignId)) {
                assignId = Util.uuid();
            }

            assignment.setAssignmentId(assignId);
        }

        // set the targets for the assignment throwing away any old ones
        ProvisioningTarget provTarget = null;
        String assignmentId = req.getAssignmentId();
        if (assignmentId != null) {
            provTarget = _project.getProvisioningTarget(assignmentId);
            if (provTarget == null) {
                // if noRoleExpansion is on we won't have targets to don't whine
                if (!_project.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION)) {
                    // corrupted plan, don't try to guess
                    log.error("Unresolved ProvisioningTarget");
                }
            }
        }

        if (provTarget != null) {
            boolean hasStartDate = (null != assignment.getStartDate());
            List<RoleTarget> newTargets = buildRoleTargets(provTarget, hasStartDate);
            boolean resetTargets = true;
            // if this comes back null do we keep the old ones?
            // no, if the role model changes targets can be removed
            // We do not want to remove the targets if we have a remove with a change
            // in end date, otherwise we wipe out the targets and prevent future removal
            // of the it role / entitlements.
            if (Operation.Remove.equals(op) && (req.getRemoveDate() != null) && Util.isEmpty(newTargets)) {
                resetTargets = false;
            }
            
            if (resetTargets) {
                assignment.setTargets(newTargets);
            }
        }
        else {
            // Either we didn't expand or this is an old plan in a workflow
            // that didn't have targets.  Don't try to guess.
        }
        
        //set assignment note from attribute request
        String note = req.getString(ProvisioningPlan.ARG_ASSIGNMENT_NOTE);
        if (null != note) {
            assignment.setComments(note);
        }

        // when an assignment is negative we should clear out any role targets
        if (assignment.isNegative()) {
            assignment.setTargets(null);
        }
    }

    /**
     * Creates a role target from data in the account selection object.
     * @param provTarget The ProvisioningTarget object.
     * @param assignmentHasSunrise Whether the assignment has a sunrise date or not.
     * @return The role target.
     */
    private List<RoleTarget> buildRoleTargets(ProvisioningTarget provTarget, boolean assignmentHasSunrise) 
        throws GeneralException {

        List<RoleTarget> roleTargets = new ArrayList<RoleTarget>();

        for (AccountSelection selection : Util.safeIterable(provTarget.getAccountSelections())) {

            if (!selection.isDoCreate() && selection.getSelection() == null) {
                // Unanswered selection, if we're not simulating, would have normally
                // resulted in Connector errors first.
                // Happens in some unit tests with simulated connectors.
                recordUnansweredAccountSelection(selection);
            }

            RoleTarget roleTarget = new RoleTarget();
            roleTarget.setApplicationId(selection.getApplicationId());
            roleTarget.setApplicationName(selection.getApplicationName());
            roleTarget.setNativeIdentity(selection.getSelection());
            roleTarget.setDisplayNameIfDifferent(getDisplayNameOfSelection(selection));
            roleTarget.setRoleName(selection.getRoleName());

            if (selection.isDoCreate()) {
                // nativeIdentity wasn't in the AccountSelection, have to locate
                // the AccountRequest
                AccountRequest req = getCreateRequest(provTarget, selection);
                if (req == null && !assignmentHasSunrise) {
                    // bug#20956 -- create requests could have been merged, 
                    // even sourceRoles are different
                    // since we won't have an id don't bother adding a target
                    log.info("Unresolved Create request for role assignment");
                    roleTarget = null;
                }
                else if (req == null && assignmentHasSunrise) {
                    // The account processing will not happen until the sunrise date.
                    // Since we have an explicit account creation, we must mark the RoleTarget as such
                    // so that we will know when the sunrise processing happens.
                    roleTarget.setDoCreate(true);
                }
                else if (req.getNativeIdentity() == null) {
                    // Must have let a plan through with unanswered account selections.
                    recordUnansweredAccountSelection(selection);
                    roleTarget = null;
                }
                else{
                    roleTarget.setNativeIdentity(req.getNativeIdentity());
                    roleTarget.setDisplayName(getNewAccountDisplayName(req));
                }
            }

            if (roleTarget != null) {
                roleTargets.add(roleTarget);

                // duplicate for followers if any exist
                for (String follower : Util.iterate(selection.getFollowers())) {
                    RoleTarget duplicate = new RoleTarget(roleTarget);
                    duplicate.setRoleName(follower);

                    roleTargets.add(duplicate);
                }
            }
        }
        return roleTargets;
    }
    
    /**
     * Creates a role target from data in the account selection object.  Assumes no sunrise date.
     * @param provTarget
     * @return
     * @throws GeneralException
     */
    private List<RoleTarget> buildRoleTargets(ProvisioningTarget provTarget)
            throws GeneralException {
        
        return buildRoleTargets(provTarget, false);
        
    }

    /**
     * Look for an op=Create AccountRequest that belonged to a role assignment.
     * There can only be one that matches.
     */
    private AccountRequest getCreateRequest(ProvisioningTarget targ, AccountSelection sel) {

        AccountRequest found = null;
        for (ProvisioningPlan plan : Util.safeIterable(_project.getPlans())) {
            for (AccountRequest account : Util.safeIterable(plan.getAccountRequests())) {
                if (account.hasAssignmentId(targ.getAssignmentId()) &&
                    Util.nullSafeEq(account.getApplication(), sel.getApplicationName())) 
                {
                    if (Util.nullSafeEq(account.getSourceRole(), sel.getRoleName(), true)) {
                        found = account;
                        return found;
                    } else {
                        //bug#21301 -- restore the merged create request after provisioning.
                        List<AccountRequest> followers = (List<AccountRequest>)account.getArgument("followers");
                        for (AccountRequest follower : Util.safeIterable(followers)) {
                            if (Util.nullSafeEq(follower.getSourceRole(), sel.getRoleName(), true)) {
                                follower.setNativeIdentity(account.getNativeIdentity());
                                found = follower;
                                return found;
                            }
                        }
                    }
                }
            }
        }

        return found;
    }

    /**
     * Derive the display name for a newly created account. 
     * There are several approaches.  The most reliable is to locate the Link
     * for this AccountRequest and get the displayName from there since it will
     * have been updated when the plan was applied.  This unfortnately has the
     * long-list-o-links problem.  The alternative would be to look in the 
     * ProvisioningResult returned by the connector or for an AttributeRequest
     * whose name matches the display name in the Schema.  This requires annoying
     * schema knowledge and result spelunking.  Best of both worlds would be to have
     * PlanEvaluator annotate the AccountRequest with the Link that was updated 
     * so we don't have to keep searching for it.  That would help in several places.
     */
    private String getNewAccountDisplayName(AccountRequest req) 
        throws GeneralException {

        String displayName = null;
        Application app = _context.getObjectByName(Application.class, req.getApplication());
        String nativeId = req.getNativeIdentity();
        Link link = _identity.getLink(app, null, nativeId);
        if (link != null)
            displayName = link.getDisplayName();

        return displayName;
    }

    /**
     * Derive the display name of the selected account.
     * In the usual case the AccountSelection has the nativeIdentity and we just
     * find the AccountInfo with the same nativeIdentity which will have the display name.
     *
     * In the create case it's harder since we don't know which Link was created.
     * This will be handled elsewhere.
     */
    private String getDisplayNameOfSelection(AccountSelection acctSelection) 
        throws GeneralException {

        String displayName = null;
        String selectedIdentity = acctSelection.getSelection();

        if (!acctSelection.isDoCreate() && selectedIdentity != null) {

            // find the AccountInfo
            AccountSelection.AccountInfo account = null;
            for (AccountSelection.AccountInfo ai : Util.safeIterable(acctSelection.getAccounts())) {
                if (Util.nullSafeEq(ai.getNativeIdentity(), selectedIdentity)) {
                    account = ai;
                    break;
                }
            }
            
            // should only happen if they were setting ids in the wrong way
            if (account == null) 
                log.error("Improper AccountSelection identity encountered");
            else {
                displayName = account.getDisplayName();
                if (displayName == null)
                    log.error("Display name missing from AccountInfo");
            }

            // At this point if the displayName is null we can search for the Link
            // with the matching native identity, but this should never happen.
            if (displayName == null) {
                Application app = _context.getObjectById(Application.class, acctSelection.getApplicationId());
                Link link = _identity.getLink(app, null, selectedIdentity);
                if (link != null)
                    displayName = link.getDisplayName();
            }
        }

        return displayName;
    }

    /**
     * Keep track of the number of identities with unanswered account selections.
     */
    private void recordUnansweredAccountSelection(AccountSelection selection) {

        if (!isSimulating()) {

            if (selection.isDoCreate()) {
                log.info("Create request left without a nativeIdentity: Identity " + 
                         _identity.getName() + 
                         ", application " + selection.getApplicationName());
            }
            else {
                log.info("Unanswered account selection: Identity " + 
                         _identity.getName() + 
                         ", application " + selection.getApplicationName());
            }

            _unansweredAccountSelections++;

            if (_unansweredAccountSelectionIdentities == null)
                _unansweredAccountSelectionIdentities = new HashSet<String>();

            // jsl - todo: make this configurable?
            if (_unansweredAccountSelectionIdentities.size() < 20)
                _unansweredAccountSelectionIdentities.add(_identity.getDisplayableName());
        }
    }

    private void logRoleList(String prefix, List<Bundle> roles) {

        if (log.isInfoEnabled()) {
            log.info(prefix);
            if (roles != null) {
                StringBuilder b = new StringBuilder();
                for (Bundle role : roles) {
                    if (b.length() > 0) b.append(", ");
                    b.append(role.getName());
                }
                log.info(b.toString());
            }
        }
    }

    private void logXml(Object o) {
        if (log.isInfoEnabled()) {
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String xml = f.toXml(o);
            log.info(xml);
        }
    }

    /**
     * After provisioning changes to the assignedRoles, 
     * recalculate role detections associated with that assignment.
     * Starting in 6.3 we're using EntitlementCorrelator to do that.
     * 
     * UPDATE: Taken out because it's a complicated new code path
     * and doesn't work.  Rather than fixing it, do auto detection
     * after we've finished the IIQ plan and reconcile all assignments.
     */
    private void autoDetectRolesIncremental(List<RoleAssignment> provisioned, 
                                            List<RoleAssignment> deprovisioned) 
        throws GeneralException {

        if (!_project.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION) &&
            !_project.getBoolean(PlanCompiler.ARG_NO_ROLE_PROVISIONING)) {


            for (RoleAssignment ra : Util.iterate(provisioned))
                _eCorrelator.detectAssignment(_identity, ra);

            for (RoleAssignment ra : Util.iterate(deprovisioned))
                _eCorrelator.detectDeassignment(_identity, ra);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Detected Roles
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * UPDATE: I decided not to use this but I want to keep the code
     * for awhile just in case.
     *
     * Upgrader to convert the old RoleRequest list into nested
     * RoleAssignments.  This is static so it can be called from several
     * places.  It would be convenient to have it on Identity but it needs
     * a SailPointContext.
     * 
     * While the model makes it look like we've been maintaining these
     * for every permitted role request we actually only created them
     * if there was a sunrise or sunset date on the request so 
     * RoleEventGenerator has something to work from.  We have never
     * set permittedById or permittedByName either so we have to guess.
     * The first assigned role that has the requested role on the permits
     * list is assumed to be the owner of the request.
     * 
     * I suppose we could look for all assignments that permit this role
     * but this raises issues of who owns the permit, only one of the assigned
     * roles or all of them.  Need to revisit this.
     *
     * In theory we can find requests for roles that are not related to any
     * assigned roles, either because the role model changed or because
     * someone programatically created a request (most likely a revocation)
     * and gave it a sunset that had nothing to do with permits.  LCM and
     * certs can't do that, but the Define/Identities page may be able to.
     * In those cases we'll leave it as a RoleRequest since we have no
     * where else to put it.  For op=Add we could promote this to an 
     * assignment since that is in effect what you're doing.
     */
    public static void upgradeRoleRequests(SailPointContext context, Identity identity)
        throws GeneralException {

        List<RoleRequest> requests = identity.getRoleRequests();
        List<RoleRequest> weirdRequests = new ArrayList<RoleRequest>();

        if (!Util.isEmpty(requests)) {

            // build a search structure keyed by permittable role
            Map<Bundle,Bundle> permittedBy = new HashMap<Bundle,Bundle>();
            for (Bundle assigned : Util.safeIterable(identity.getAssignedRoles())) {
                for (Bundle permitted : Util.safeIterable(assigned.getPermits())) {
                    // since we've lost ownership assume it is owned by the first one,
                    // could make a list<Bundle> if we needed multiple owners
                    if (permittedBy.get(permitted) == null)
                        permittedBy.put(permitted, assigned);
                }
            }

            // upgrade the RoleRequests
            for (RoleRequest req : requests) {
            
                Bundle permitted = null;
                String id = req.getRoleId();
                if (id != null)
                    permitted = context.getObjectById(Bundle.class, id);
                else if (req.getRoleName() == null)
                    permitted = context.getObjectByName(Bundle.class, req.getRoleName());

                if (permitted == null) {
                    log.warn("Identity " + identity.getName() + " has a RoleRequest for an invalid role" + 
                             req.getRoleName());
                }
                else {
                    Bundle assigned = permittedBy.get(permitted);
                    if (assigned == null) {
                        // some random detected role request that doesn't
                        // match an assignment, leave it 
                        log.warn("Identity " + identity.getName() + " has a RoleRequest for " +
                                 permitted.getName() + " that is not permitted by any of the assigned roles!");
                        weirdRequests.add(req);
                    }
                    else {
                        // have to assume the first one owns
                        RoleAssignment assignment = identity.getFirstRoleAssignment(assigned);
                        if (assignment == null) {
                            // should not be happening, I guess bootstrap one
                            log.warn("Identity " + identity.getName() + 
                                     " has role " + assigned.getName() + " on the assigned list but does not have a RoleAssignment");
                            assignment = new RoleAssignment(assigned);
                            assignment.setAssignmentId(Util.uuid());
                            assignment.setAssigner("System");
                            assignment.setSource(Source.UI.toString());
                            identity.addRoleAssignment(assignment);
                        }
                        
                        RoleAssignment child = assignment.getPermittedRole(permitted);
                        // if we already had one assume it is newer and ignore the 
                        // stale RoleRequest
                        if (child == null) {
                            child = new RoleAssignment(permitted);
                            child.setAssigner(req.getAssigner());
                            child.setSource(req.getSource());
                            child.setDate(req.getDate());
                            child.setNegative(req.isNegative());
                            child.setStartDate(req.getStartDate());
                            child.setEndDate(req.getEndDate());
                            assignment.addPermittedRole(child);
                        }
                    }
                }
            }
        }
        
        // normally this list will be empty
        identity.setRoleRequests(weirdRequests);
    }

    /**
     * Modify the detected role list and RoleAssignment metadata.
     * Since provisioning isn't always synchronous we only modify
     * the detected role list if an argument is passed in the project
     * or if we know for sure that the Connector committed the changes.
     * If neither of those apply, the detected role list won't
     * be changed until the next refresh when we run entitlement correlation.
     *
     * The modifyDetectedRoles flag can be used to add/remove detected
     * roles immediately without requiring a refresh.  This 
     * "optimistic" correlation might be desireable in some cases
     * though it isn't reliable, the next refresh could cause
     * the roles to be removed or restored.  
     * I added this for McDonalds thinking they would want it, 
     * but now I'm not sure...
     */
    private void processDetectedRoles(AttributeRequest req) 
        throws GeneralException {

        List<Bundle> newRoles = ObjectUtil.getObjects(_context,
                                                      Bundle.class,
                                                      req.getValue(),
                                                      // trust rules
                                                      true,
                                                      // throw exceptions
                                                      false, 
                                                      // convert CSV to List
                                                      false);

        // always do this
        reconcileRoleRequests(req, newRoles);

        // optionally update the detection list if requested
        // or if we know it was successful, but not if sunrising!
        if (_project.getBoolean(PlanEvaluator.ARG_MODIFY_DETECTED_ROLES) ||
             isRolesProvisioned()) {

            // allow name, object, name list, or object list
            // hmm, should the default be Add or Set?
            Operation op = req.getOp();
            if (op == null || op == Operation.Set) {
                if (req.getAddDate() == null) {
                    if (log.isInfoEnabled()) {
                        logRoleList("Setting detected roles: ", newRoles);
                    }
                    _identity.setDetectedRoles(newRoles);
                }
            }
            else if (op == Operation.Add) {
                if (req.getAddDate() == null) {
                    if (log.isInfoEnabled()) {
                        logRoleList("Adding detected roles: ", newRoles);
                    }
                    if (newRoles != null) {
                        List<Bundle> oldRoles = _identity.getDetectedRoles();
                        if (oldRoles == null)
                            _identity.setDetectedRoles(newRoles);
                        else {
                            for (Bundle role : newRoles) {
                                if (!oldRoles.contains(role))
                                    oldRoles.add(role);
                            }
                        }
                    }
                }
            }
            else if (op == Operation.Retain) {
                // they're either there or we ignore them
            }
            else if (newRoles != null && req.getRemoveDate() == null) {
                // must be a remove or revoke

                if (log.isInfoEnabled()) {
                    logRoleList("Removing detected roles: ", newRoles);
                }

                List<Bundle> oldRoles = _identity.getDetectedRoles();
                if (oldRoles != null)
                    oldRoles.removeAll(newRoles);
            }

            // reconcile the detected role metadata with what we just did
            reconcileDetectionMetadata(req);
        }
    }

    /**
     * Return true if all of the roles, assigned or detected, were succesfully
     * provisioned in this project.
     *
     * This is used to proactively add or remove roles from the
     * detected role list rather than waiting for the next refresh.
     *
     * This isn't always possible, if there are any retryable errors
     * we won't get this since the IIQ plan  is evaluated before retries.  
     * There also may be other things in the plan that are unrealted to
     * the roles and those may fail even though the role is satisified.  
     */
    private boolean isRolesProvisioned() {

        return (!_project.getBoolean(PlanCompiler.ARG_NO_ROLE_EXPANSION) &&
                !_project.getBoolean(PlanCompiler.ARG_NO_ROLE_PROVISIONING) &&
                isProjectSuccessful());
    }

    /**
     * Check to see if the project contained detected role expansions and
     * if all of the requests succeeded.
     *
     * To be successful, all of the managed plans must have STATUS_COMMITTED
     * at all levels, and there can be no unmanaged plan.
     */
    private boolean isProjectSuccessful() {

        boolean success = true;

        List<ProvisioningPlan> plans = _project.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                String target = plan.getTargetIntegration();
                if (target == null) {
                    // it's the unmanged plan
                    success = false;    
                }
                else if (target.equals("IIQ")) {
                    // ignore
                }
                else {
                    // walk over the results
                    success = plan.isFullyCommitted();
                }
                if (!success) break;
            }
        }
        return success;
    }

    /**
     * Helper for processDetectedRoles.  After possibly adding or
     * removing things from the detected role list, make corresponding
     * additions or removals from the detected role metadata.
     *
     * TODO: This may need changes similar to the way we're handling
     * RoleAssignments in order to support multiple detections.  
     * If AttributeRequest has an assignmentId we can know which
     * accounts were targeted and add RoleTargets.
     *
     * !! See how this fits with reDetectRoles
     */
    private void reconcileDetectionMetadata(AttributeRequest req) 
        throws GeneralException {

        List<Bundle> roles = _identity.getDetectedRoles();
        if (roles == null) {
            _identity.setRoleDetections(null);
        }
        else {
            List<RoleDetection> oldMetadata = _identity.getRoleDetections();
            List<RoleDetection> newMetadata = new ArrayList<RoleDetection>();

            for (Bundle role : roles) {

                RoleDetection rd = _identity.getRoleDetection(oldMetadata, role.getId(), null);
                if (rd != null)
                    newMetadata.add(rd);
                else {
                    // Hmm, we might want something to say that this was done
                    // by the provisioner rather than the entitlement correlator.
                    // !! We don't have enough information here to set
                    // the _items or _groupRendering fields, that may confuse something
                    // later that want's to show entitlements covered by a role.
                    // Will need to integrate with EntitlementCorrelator if that
                    // becomes necessary.
                    rd = new RoleDetection(role);
                    newMetadata.add(rd);
                }
            }
            
            _identity.setRoleDetections(newMetadata);
        }

        // also update the summary string
        _identity.updateDetectedRoleSummary();
    }

    /**
     * Helper for processDetectedRoles.  
     * After modifying the detected role list, make corresponding changes
     * to the assignment model.  There are two things that can happen here:
     *
     *    - modification of a nested RoleAssignment list inside a 
     *      parent RoleAssignment
     *
     *    - modification of the RoleRequest list
     *
     * The first case happens when the request is to add or remove 
     * roles that are permitted but not required by an assigned role.
     * We are effectively altering the characteristics of a previous
     * assignment which we track in the RoleAssignment list.  Permitted
     * roles are modeled with a list of child RoleAssignments inside
     * the RoleAssignment that represents the assigned role.
     *
     * The RoleRequest list is old and used in cases where a request
     * is made that is not related to an assigned role.  This is believed
     * to not be possible in the UI any more but it may be if they enable
     * role management from the Define/Identities page, or have custom
     * code that provisions detected roles.  In those cases we have
     * only reated RoleRequests if there was a sunrise or sunset date.
     * We maintain this for backward compatibility.
     * 
     */
    private void reconcileRoleRequests(AttributeRequest attreq, List<Bundle> roles)
        throws GeneralException {

        RoleAssignment assignment = null;
        String aid = attreq.getAssignmentId();
        if (aid != null) {
            assignment = _identity.getRoleAssignmentById(aid);
            if (assignment == null) {
                // If they bothered to pass one and we couldn't find it, 
                // what to do?  Don't guess here, if they ask for an id
                // then we require a match.  But ignore our temporary ones.
                //
                // dcd - Bug #20198: This can happen if we generate an assignment id in LCM.
                //       Encountering an issue during policy checking because the assignment ids
                //       from the attribute request are not on the identity yet. Need to revisit.
                // if (!AssignmentExpander.isTemporaryAssignmentId(aid))
                //     log.error("Invalid assignmentId in detectedRoles request: " +
                //              attreq.getAssignmentId());
            }
        }
        else {
            // Old code that doesn't use assignment ids.  We could guess like 
            // we tried in upgradeRoleRequests but I'm not liking that, 
            // too unreliable and the permit may apply to more than
            // one assignment.
        }

        if (assignment == null) {
            // ambiguous assignment, handle it the old way
            reconcileOldRoleRequests(attreq, roles);
        }
        else if (roles != null) {
            for (Bundle role : roles) {

                // A permit is only allowed once within the
                // context of an assignment
                RoleAssignment permit = assignment.getPermittedRole(role);
                Operation op = attreq.getOperation();
                Date sunset = attreq.getRemoveDate();

                if (op == Operation.Remove && sunset == null) {
                    // an immediate removal
                    assignment.removePermittedRole(permit);
                }
                else if (op != Operation.Retain) {
                    // an assignment or sunset, need to remember this
                    if (permit == null) {
                        permit = new RoleAssignment(role);
                        permit.setAssigner(getAssigner());
                        // TODO: source?
                        assignment.addPermittedRole(permit);

                        // if there is a permitted by role and the role name on the provisioning
                        // target for the assignment id matches the detected role name then we
                        // know it is a direct permitted role request so we may need to create and
                        // merge new role targets into the assignment
                        if (attreq.get(ProvisioningPlan.ARG_PERMITTED_BY) != null) {
                            ProvisioningTarget provTarget = _project.getProvisioningTarget(aid);
                            if (provTarget != null && provTarget.getRole().equals(role.getName())) {
                                List<RoleTarget> roleTargets = buildRoleTargets(provTarget);
                                for (RoleTarget roleTarget : Util.iterate(roleTargets)) {
                                    if (!assignment.hasMatchingRoleTarget(roleTarget)) {
                                        assignment.addRoleTarget(roleTarget);
                                    }
                                }
                            }
                        }
                    }

                    permit.setStartDate(attreq.getAddDate());
                    permit.setEndDate(sunset);
                }
            }
        }
    }

    /**
     * Maintain the semi-deprecated RoleRequest list for requets
     * that do not appear to match any of the current assignments.
     * This is only done when the request has a sunrise or sunset date.
     */
    private void reconcileOldRoleRequests(AttributeRequest attreq, 
                                          List<Bundle> roles)
        throws GeneralException {

        if (roles != null) {
            for (Bundle role : roles) {

                RoleRequest rolereq = _identity.getRoleRequest(role);
                Operation op = attreq.getOperation();

                if (op == Operation.Remove) {
                    Date date = attreq.getRemoveDate();
                    if (date == null) {
                        // an immediate removal
                        _identity.removeRoleRequest(rolereq);
                    }
                    else {
                        if (rolereq == null) {
                            rolereq = new RoleRequest();
                            rolereq.setRole(role);
                            rolereq.setAssigner(getAssigner());
                            // TODO: source?
                            _identity.addRoleRequest(rolereq);  
                        }
                        rolereq.setEndDate(date);
                    }
                }
                else if (op == null || op == Operation.Add || op == Operation.Set) {

                    Date start = attreq.getAddDate();
                    Date end = attreq.getRemoveDate();
                    if (start == null && end == null)  {
                        // don't bother unless there are dates
                        // eventually will want this all the time
                        // but need a more obvious way of knowing this
                        // is being formally requested from LCM
                        _identity.removeRoleRequest(rolereq);
                    }
                    else {
                        if (rolereq == null) {
                            rolereq = new RoleRequest();
                            rolereq.setRole(role);
                            rolereq.setAssigner(getAssigner());
                            // TODO: source?
                            _identity.addRoleRequest(rolereq);
                        }
                        rolereq.setStartDate(start);
                        rolereq.setEndDate(end);
                    }
                }
                else {
                    // shouldn't be here with Retain, ignore
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scheduled Assignments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called after we've applied the plan and possibly added or changed 
     * sunrise/sunset dates on assignment metadata.
     * 
     * Sunrise/sunset is implemented by scheduling Request objects, that
     * are kept in sync with the metadata.  There are three lists of metdata
     * stored in the preferences map of the Identity.
     *
     *   RoleAssignment - information about hard role assignments
     *   RoleRequest - infomration about requestd permitted roles
     *   AttributeAssignment - informationa bout hard attribute assignments
     *
     * Since doing this recon requires database hits, try to be smart and
     * only go through it if we notice there were changes int he plan
     * that may have effectd the three lists.
     */
    private void scheduleAssignmentRequests() 
        throws GeneralException {

        // This must NOT be done if we're simulating provisoining
        // for impact analysis, it may try to save and delete things.
        if (!isSimulating() && _needsScheduledAssignmentRefresh) {

            // all of the work is actually in here
            RoleEventGenerator generator = new RoleEventGenerator(_context);

            generator.reconcileScheduledRequests(_identity);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Link Moves
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Handle link removals and link moves either two or from
     * the plan identity.  When processing link moves the plan
     * identity is already locked but we have to lock the other identity.
     *
     * !! We have historically called the Identitizer here to refresh both
     * identities.  This needs to be revisited because it is unclear
     * which options should be selected, they proably needed to be passed down.
     * Note that calling Identitizer is a *very* complex thing to do and the
     * cache when you come out of it is in an completely unkown state. 
     * This method must ensure that _identity is refetched and in the
     * cache when it returns.
     */
    private void processLinks(AttributeRequest req) 
        throws GeneralException {

        boolean lock = isLockingRequired();
        Identity other = null;
        boolean changes = false;

        try {
            if (req.getOp().equals(Operation.Remove)) {

                List<Link> links = getLinksFromRequest(req, _identity);

                if (links == null || links.size() == 0) {
                    log.warn("No matching links to process in move request");
                }
                else {
                    String destName = getDestinationIdentityName(req);
                    if (destName == null) {
                        // this is treated like op='Delete' 
                        removeLinks(links, _identity);
                        changes = true;
                    }
                    else if (destName.equals(_identity.getName())) {
                        // should have prevented this in the UI
                        log.warn("Attempt to remove a link and move it to the same identity");
                    }
                    else {
                        if (lock)
                            other = ObjectUtil.lockIdentity(_context, destName);
                        else
                            other = _context.getObjectByName(Identity.class, destName);

                        // Create one if necessary.
                        // Passing the first link supplies the Application
                        // creation rule.  
                        // !! This seems rather arbitrary, we're not really
                        // aggregating here should we be running creation
                        // rules?  The order of these is undefined..
                        if (other == null) {
                            Identitizer identitizer = getLinkMoveIdentitizer();
                            other = identitizer.create(links.get(0), destName);

                            // A link move for an identity only creates a provisioning project and plan
                            // for the removal of that identity on the application with no
                            // trace that the identity create happened.
                            // The creation of the Identity above this comment is more of a side effect
                            // of the original intention which is the remove.
                            // So log a canned PTO to indicate to the user that a Identity create
                            // did happen.
                            logCreateIdentityProvisioningTransaction(destName);
                        }

                        moveLinks(links, _identity, other);
                        changes = true;
                    }
                }
            }
            else if (req.getOp().equals(Operation.Add)) {

                String sourceName = getSourceIdentityName(req);
                if (sourceName == null) {
                    // We don't support adding random new links to an identity
                    // through the plan but I suppose we could...
                    log.warn("Attempt to add new links through plan");
                }
                else if (sourceName.equals(_identity.getName())) {
                    // should have prevented this in the UI
                    log.warn("Attempt to add a link and take if from the same identity");
                }
                else {
                    if (lock)
                        other = ObjectUtil.lockIdentity(_context, sourceName);
                    else
                        other = _context.getObjectByName(Identity.class, sourceName);

                    if (other == null)
                        log.error("Source Identity not found");
                    else {
                        List<Link> links = getLinksFromRequest(req, other); 
                        if (links == null || links.size() == 0)
                            log.warn("No matching links to process in move request");
                        else {
                            moveLinks(links, other, _identity);
                            changes = true;
                        }
                    }
                }
            }

            // If we are not simulating (lock is true) commit the changes
            // and do a targeted refresh of both cubes.
            // !! Revisit this, it isn't at all clear what options 
            // should be enalbed, the probably need to be passed down.
            // Note that Identitizer can waste the cache so we have
            // to reattach when we get back.  If this throws should
            // it prevent the rest of the plan from being evaluated?
            // The Identitizer cannot be called if we're simulating 
            // since we can't trust what it will do.
            if (lock && changes) {
                // first commit the reparenting
                _context.saveObject(_identity);
                if (other != null)
                    _context.saveObject(other);
                _context.commitTransaction();

                Identitizer identitizer = getLinkMoveIdentitizer();
                if (other != null)
                    identitizer.refresh(other);

                // have to refresh this after calling Identitizer
                _identity = _context.getObjectById(Identity.class, _identity.getId());
                identitizer.refresh(_identity);
                // need to refresh _Identity again, will do that in the finaly clause
            }
        }
        finally {
            if (lock && other != null) {
                try {
                    // have to refresh this after calling Identitizer, may have been deleted
                    // during refresh
                    other = _context.getObjectById(Identity.class, other.getId());
                    if (other != null)
                        ObjectUtil.unlockIdentity(_context, other);
                }
                catch (Throwable t) {
                    log.error("Unable to unlock other identity after move");
                    log.error(t);
                }
            }
            
            if (lock && changes) {
                try {
                    _identity = _context.getObjectById(Identity.class, _identity.getId());
                }
                catch (Throwable t) {
                    log.error("Unable to refetch identity after move");
                    log.error(t);
                }
            }
        }
    }

    /**
     * Manually generate a canned PTO to keep a record of a created Identity in the PTO table.
     * This is used in a situation when a user moves an application account from one app
     * to another with the option to create a new identity. Only a PTO is created for the Remove
     * event but nothing is logged with the new creation.
     *
     * @param identityName The new identity name that is being crated in IIQ
     * @throws GeneralException
     */
    private void logCreateIdentityProvisioningTransaction(String identityName) throws GeneralException {
        ProvisioningTransactionService pts = new ProvisioningTransactionService(_context);

        // stub out the details with the user inputted name and source
        TransactionDetails details = new TransactionDetails();
        details.setIdentityName(identityName);
        details.setSource(Source.LCM.toString());

        // make a canned account request so that the service
        // the PTO knows what to populate it with
        AccountRequest ar = new AccountRequest();
        ar.setOp(ObjectOperation.Create);
        ar.setApplication(ProvisioningPlan.APP_IIQ);

        details.setRequest(ar);
        pts.logTransaction(details);
    }

    /**
     * Remove links from the plan identity using the Terminator.
     * This will commit several times.
     */
    private void removeLinks(List<Link> links, Identity identity) throws GeneralException {
        // Use the terminator to clean up the link references.  Don't decache
        // since we're still using the identity.
        Terminator t = new Terminator(_context);
        t.setNoDecache(true);
        for (Link link : links) {
            t.deleteObject(link);
            // do NOT add this to the _deletedLinks list, that will
            // trigger a refresh and we're currently handling the refresh
            // down here after the move finishes
        }
    }
    
    /**
     * Move links from one identity to another, both must be in the session.
     */
    private void moveLinks(List<Link> links, Identity source, Identity destination)
            throws GeneralException {
        Entitlizer entitlizer = new Entitlizer(_context, new Attributes<String, Object>());
        for (Link link : links) {
            if (link != null) {
                link.setDirty(true);
                link.setManuallyCorrelated(true);
                source.remove(link);
                destination.add(link);
                
                // If we are not simulating, we need to move the identity 
                // entitlements or else we will break on refresh. Also, audit.
                if (!isSimulating()) {
                    //Move the entitlements along to the new identity
                    entitlizer.moveLinkEntitlements(link, source, destination);

                    //IIQTC-305 :- Saving the source name into the account field
                    //and native identity into AttributeValue
                    // IIQETN-9358: Check whether auditing is enabled for
                    // LinkMoved before proceeding.
                    if (Auditor.isEnabled(AuditEvent.ActionLinkMoved)) {
                        AuditEvent event = new AuditEvent();
                        event.setSource(_project.getRequester());
                        event.setAction(AuditEvent.ActionLinkMoved);
                        event.setTarget(destination.getName());
                        event.setApplication(link.getApplicationName());
                        event.setAccountName(source.getName());
                        event.setAttributeValue(link.getNativeIdentity());
                        Auditor.log(event);
                    }
                }
            }
        }
    }
    
    /**
     * Check for the optional source identity name in the request arguments.
     */
    private String getSourceIdentityName(AttributeRequest req) {
        String name = null;
        Attributes<String, Object> args = req.getArguments();
        if (args != null)
            name = args.getString(ProvisioningPlan.ARG_SOURCE_IDENTITY);
        return name;
    }
    
    /**
     * Check for the optional destination identity name in the request arguments.
     */
    private String getDestinationIdentityName(AttributeRequest req) {
        String name = null;
        Attributes<String, Object> args = req.getArguments();
        if (args != null)
            name = args.getString(ProvisioningPlan.ARG_DESTINATION_IDENTITY);
        return name;
    }
    
    /**
     * Given a request containing either ids or Link objects, locate the 
     * corresponding "real" link objects in an identity and return them.
     */
    public static List<Link> getLinksFromRequest(AttributeRequest req, Identity identity) {
        List<Link> ret = new ArrayList<Link>();
        Object value = req.getValue();
        if (value instanceof List) {
            List vals = (List) value;
            for (Object val : vals) {
                ret.add(getLinkFromSingleValue(val, identity));
            }
        } else {
            ret.add(getLinkFromSingleValue(value, identity));
        }
        
        return ret;
    }
    
    /**
     * Helper for getLinksFromRequest.
     * Locate a Link in an identity given an id or a stub Link.
     */
    private static Link getLinkFromSingleValue(Object value, Identity identity) {

        Link link = null;

        if (value instanceof String) {
            link = identity.getLink((String) value);
            if (link == null) {
                String warning = "Link with id: " + value + ", was not found on identity: " + identity.getName();
                log.warn(warning);
                auditWarning(AuditEvent.ActionProvision, identity.getName(),  warning);
            }
        } 
        else if (value instanceof Link) {
            Link src = (Link) value;
            link = identity.getLink(src.getApplication(), src.getInstance(), src.getNativeIdentity());
            if (link == null) {
                String warning = "Link with id: " + value + ", was not found on identity: " + identity.getName();
                log.warn(warning);
                auditWarning(AuditEvent.ActionProvision, identity.getName(),  warning);
            }
        } 
        else {
            throw new IllegalStateException("unknown value type: " + value);
        }

        return link;
    }
    
    private static void auditWarning(String action, String identity, String warning) {

        if (Auditor.isEnabled(action)) {
            Auditor.log(action, identity, warning);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Capabilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Change the capabilities list.
     * Historically this is only been used for revociations
     * in a certification, but we now support adding them as well.
     *
     * Comments from the old IIQRequestExecutor:
     *   Note that the capabilities in the provisioning plan are the
     *   en_US translated capability names, so we'll iterate through
     *   the capabilities on the identity and check if the translated
     *   name of the capability is in the list of capabilities to remove.
     *   See BaseIdentityCertificationContext.getIdentityCertifiables().
     *
     * We're only going to support translated names for Remove ops
     * until we can change the cert builder.  For Set and Add
     * you must use the canonical name because we have to fetch
     * the Capability objects and we can't search on translated 
     * display names.
     *
     */
    private void processCapabilities(AttributeRequest req)
        throws GeneralException {
        
        Operation op = req.getOp();

        if (op == Operation.Remove) {
            // original implementation so we can handle
            // en_US translated names that the Certificationer sends
            List<String> caps = Util.asList(req.getValue());
            List<Capability> current = _identity.getCapabilities();
            if (current != null) {
                Iterator<Capability> it = current.iterator();
                while (it.hasNext()) {
                    Capability cap = it.next();
                    String name = cap.getName();
                    String id = cap.getId();
                    String displayName = cap.getDisplayableName(Locale.US);
                    if ( caps.contains(id)  || caps.contains(name) || caps.contains(displayName) ) {
                        it.remove();
                    }
                }
            }
        }   
        else if (op == Operation.Add) {
            List<Capability> caps = getCapabilities(req.getValue());
            List<Capability> current = _identity.getCapabilities();
            if (caps != null) {
                for (Capability cap : caps) {
                    if (current == null || !current.contains(cap))
                        _identity.add(cap);
                }
            }
        }
        else if (op == null || op == Operation.Set) {
            List<Capability> caps = getCapabilities(req.getValue());
            _identity.setCapabilities(caps);
        }
    }

    /**
     * Convert an AttributeRequest value into a list of
     * Capability objects.
     */
    private List<Capability> getCapabilities(Object value) 
        throws GeneralException {

        List<Capability> capabilities = new ArrayList<Capability>();
        if (value instanceof Collection) {
            for (Object o : (Collection)value) {
                Capability c = getCapability(o);
                if (c != null)
                    capabilities.add(c);
            }
        }
        else {
            Capability c = getCapability(value);
            if (c != null)
                capabilities.add(c);
        }
        return capabilities;
    }

    private Capability getCapability(Object value) 
        throws GeneralException {
        
        Capability cap = null;
        if (value instanceof String) {
            cap = _context.getObjectByName(Capability.class, (String)value);
        }
        else if (value instanceof Capability) {
            // have to fetch a fresh one
            Capability other = (Capability)value;
            cap = _context.getObjectById(Capability.class, other.getId());
        }
        return cap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Scopes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Change the controlled scopes list.
     * 
     * Historically this is only been used for revocations
     * in a certification, but we now support adding them as well.
     *
     * Oringinally only matched on displayPath now also supoprts the
     * id of the scope.
     *
     * Comments from the old IIQRequestExecutor:
     *   Remove the scopes from the given user that have the given displayable
     *   paths.  This will set the "controls assigned scope" flag to false 
     *   if one of the scopes is the assigned scope, is not a controlled 
     *   scope, and the user controls their assigned scope.
     *   The scopes in the provisioning plan are the "displayable paths".
     *   See BaseIdentityCertificationContext.getIdentityCertifiables().
     */
    private void processControlledScopes(AttributeRequest req)
        throws GeneralException {
        
        Operation op = req.getOp();
        if (op == Operation.Remove) {
            // original implementation from IIQRequestExecutor
            // only accepts displayable paths
            List<String> scopes = Util.asList(req.getValue());
            List<String> scopesToRemove = new ArrayList<String>(scopes);

            // First, try to remove the scopes from the controlled scopes list.
            List<Scope> controlled = _identity.getControlledScopes();
            if (controlled != null) {
                Iterator<Scope> it = controlled.iterator();
                while (it.hasNext()) {
                    Scope scope = it.next();
                    String id = scope.getId();
                    String path = scope.getDisplayablePath();
                    if ( ( scopesToRemove.contains(id) ) || 
                         ( scopesToRemove.contains(path) ) ) {
                        it.remove();
                        scopesToRemove.remove(path);
                    }
                }
            }

            // If we didn't remove a controlled scope, check if this is an
            // effective controlled scope b/c the user controls their
            // assigned scope.  If so, turn this option off for this user.
            if (!scopesToRemove.isEmpty() && 
                _identity.getControlsAssignedScope(_context.getConfiguration())) {
                Scope assigned = _identity.getAssignedScope();
                if ((null != assigned) && 
                    scopesToRemove.contains(assigned.getDisplayablePath())) {
                    _identity.setControlsAssignedScope(false);
                }
            }
        }
        else if (op == Operation.Add) {
            List<Scope> scopes = getScopes(req.getValue());
            List<Scope> current = _identity.getControlledScopes();
            if (scopes != null) {
                for (Scope scope : scopes) {
                    if (current == null || !current.contains(scope))
                        _identity.addControlledScope(scope);
                }
            }
        }
        else if (op == null || op == Operation.Set) {
            List<Scope> scopes = getScopes(req.getValue());
            _identity.setControlledScopes(scopes);
            // TODO: I don't understand the "controls assigned scope"
            // stuff, need more here...
        }
    }

    /**
     * Convert an AttributeRequest value into a list of
     * Scope objects.
     */
    private List<Scope> getScopes(Object value) 
        throws GeneralException {

        List<Scope> scopes = new ArrayList<Scope>();
        if (value instanceof Collection) {
            for (Object o : (Collection)value) {
                Scope s = getScope(o);
                if (s != null)
                    scopes.add(s);
            }
        }
        else {
            Scope s = getScope(value);
            if (s != null)
                scopes.add(s);
        }
        return scopes;
    }
    
    private Scope getScope(Object value) 
        throws GeneralException {
        
        Scope scope = null;
        if (value instanceof String) {
            //Scope does not have unique name, therefore need to use ID. Not sure if that's how plans are currently being created. -rap
            scope = _context.getObjectById(Scope.class, (String)value);
        }
        else if (value instanceof Scope) {
            // have to fetch a fresh one
            Scope other = (Scope)value;
            scope = _context.getObjectById(Scope.class, other.getId());
        }
        return scope;
    }

    /**
     * Change the assigned scope.
     * Since this is single valued set and add do the same thing.
     * Remove sets it to null if the value matches the current value.
     */
    private void processScope(AttributeRequest req)
        throws GeneralException {

        Scope scope = getScope(req.getValue());

        Operation op = req.getOp();
        if (op == Operation.Remove) {
            if (_identity.getAssignedScope() == scope)
                _identity.setAssignedScope(null);
        }
        else {
            _identity.setAssignedScope(scope);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Password
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * We have historically only paid attention to this if the
     * value was a non-null non-empty string, which effectively
     * made it impossible to clear the password.  That doesn't sound right
     * at this level we should do whatever the plan wants and expect policy
     * to have been checked above.
     */
    private void processPassword(AttributeRequest req)
        throws GeneralException {

        Object value = req.getValue();
        if (value != null) {
            String str = value.toString();
            // ignroe empty strings, should we trim as well?
            if (str.length() > 0) {
                
                // bug#20030 changed this to use PasswordPolice so
                // that we use consistent logic. Earlier comments
                // caution us to be careful. Thought I should leave that comment 
                // here for the time being.
                boolean usePolice = true;

                if (!usePolice) {
                    // old way, just save it
                	if (Auditor.isEnabled(AuditEvent.PasswordChange)) {
                        Auditor.logAs(_project.getRequester(),
                        		AuditEvent.PasswordChange,
                        		_identity.getName(),
                        		null,
                        		null,
                        		null
                        		);
                	}
                    _identity.setPassword(str);
                }
                else {
                    PasswordPolice.Expiry expiry = getExpiry(req);
                    boolean isSystemAdmin = req.getBoolean(PlanEvaluator.ARG_REQUESTER_IS_SYSTEM_ADMIN);
                    boolean isPasswordAdmin = isSystemAdmin || req.getBoolean(PlanEvaluator.ARG_REQUESTER_IS_PASSWORD_ADMIN);

                    // this will do the work
                    PasswordPolice pp = new PasswordPolice(_context);
                    String decryptedPass = _context.decrypt(str);
                    
                    if (req.getBoolean(ProvisioningPlan.ARG_CHECK_POLICY)) {
                        // hmm, we can check policies here but it's way to late,
                        // these should have been caught during plan compilation
                        // !! let this thorw or catch it, add it to the error message
                        // list and continue with the rest of the plan?
                        pp.checkPassword(_identity, decryptedPass, isSystemAdmin);
                    }
                
                    // besides encrypting and setting the password this will 
                    // also manage the history list, the expiration date,
                    // and audit.  Do wwe need to be selective about those?
                    pp.setPasswordNoCheck(_identity, decryptedPass, expiry, isPasswordAdmin);
                }
            }
        }
    }
    
    /**
     * Gets the Expiry argument from the AttributeRequest
     * @param req request to retrieve expiry argument from
     * @return Expiry argument or Expiry.USE_IDENTITY_VALUE if one is not provided
     */
    PasswordPolice.Expiry getExpiry(AttributeRequest req) {
        // this was the default value before we added ARG_PASSWORD_EXPIRY as an argument
        PasswordPolice.Expiry result = PasswordPolice.Expiry.USE_IDENTITY_VALUE;
        String strExpiry = (req != null) ? req.getString(ProvisioningPlan.ARG_PASSWORD_EXPIRY) : null;
        try {
            if (strExpiry != null) {
                result = sailpoint.api.PasswordPolice.Expiry.valueOf(strExpiry);
            }
        } catch (Exception e) {
            log.warn("Using USE_IDENTITY_VALUE for Expiry. Could not find Expiry with value: " + strExpiry);
        }
        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ Workgroups
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Change the workgroups.
     */
    private void processWorkgroups(AttributeRequest req)
        throws GeneralException {
        
        Operation op = req.getOp();
        if (op == Operation.Remove) {
            List<String> workgroups = Util.asList(req.getValue());
            List<String> workgroupsToRemove = new ArrayList<String>(workgroups);
            for ( String wgName : workgroupsToRemove ) {
                Identity workgroup = _context.getObjectByName(Identity.class, wgName);
                _identity.remove(workgroup);
            }
        }
        else if (op == Operation.Add) {
            List<Identity > newWgs = getWorkgroups(req.getValue());
            if (newWgs != null) {
                for (Identity wg : newWgs) {
                    _identity.add(wg);
                }
            }
        }
        else if (op == null || op == Operation.Set) {
            List<Identity> wgs = getWorkgroups(req.getValue());
            _identity.setWorkgroups(wgs);
        }
    }

    /**
     * Convert an AttributeRequest value into a list of
     * Scope objects.
     */
    private List<Identity> getWorkgroups(Object value) 
        throws GeneralException {

        List<Identity> wgs = new ArrayList<Identity>();
        if (value instanceof Collection) {
            for (Object o : (Collection)value) {
                Identity wg  = getWorkgroup(o);
                if (wg != null)
                    wgs.add(wg);
            }
        }
        else {
            Identity wg = getWorkgroup(value);
            if (wg != null)
                wgs.add(wg);
        }
        return wgs;
    }

    private Identity getWorkgroup(Object value) 
        throws GeneralException {
        
        Identity wg = null;
        if (value instanceof String) {
            //Assume this will always be name? -rap
            wg = _context.getObjectByName(Identity.class, (String)value);
        }
        else if (value instanceof Identity ) {
            // have to fetch a fresh one
            Identity id = (Identity)value;
            wg = _context.getObjectById(Identity.class, id.getId());
        }
        return wg;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ ActivityConfig
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is an unusual "attribute" because we're actually modifying
     * a structure (ActivityConfig) with two properties.  Usually
     * The value will be a String or a Collection<String> containing
     * application ids that we add or remove to the 
     * ActivityConfig.enabledApplications list.  But if the value is
     * a Boolean then this is used to set the ActivityConfig.enableAll
     * flag.  In practice I don't think enableAll is ever used I see
     * calls to it in the unit tests but not in IdentityBean so it
     * isn't being set.  
     */
    private void processActivityConfig(AttributeRequest req)
        throws GeneralException {

        ActivityConfig config = _identity.getActivityConfig();
        if (config == null) {
            config = new ActivityConfig();
            _identity.setActivityConfig(config);
        }

        Object value = req.getValue();
        if (value instanceof Boolean) {
            Boolean b = (Boolean)value;
            if (b) {
                // turning on enableAll cancels the attribute list
                config.setAllEnabled(true);
                config.setEnabledApplications(null);
            }
            else { 
                // force the flag off but leave the list
                config.setAllEnabled(false);
            }
        }
        else {
            List<String> ids = Util.asList(value);
            // these have to be stored as ids in the database
            // but they will usually be names in the plan so we can
            // see meaningful names when approving, have to convert
            ids = getObjectIds(Application.class, ids);

            if (req.getOp() == Operation.Set) {
                // sigh, has to be a Set
                config.setEnabledApplications(new HashSet<String>(ids));
            }
            else {
                if (ids != null) {
                    for (String id : ids) {
                        if (req.getOp() == Operation.Remove)
                            config.removeApplication(id);
                        else
                            config.addApplication(id);
                    }
                }
            }
        }
    }

    /**
     * General method to convert an unknown list of ids or names into
     * a known list of ids.  
     * Could be in ObjectUtil if we need it elsewhere.
     */
    private <T extends SailPointObject> List<String> getObjectIds(Class<T> cls, 
                                                                  List<String> names)   
        throws GeneralException {

        List<String> ids = null;
        if (names != null && names.size() > 0) {
            ids = new ArrayList<String>();
            for (String name : names) {

                if (ObjectUtil.isUniqueId(name))
                    ids.add(name);
                else {
                    SailPointObject obj = _context.getObjectByName(cls, name);
                    if (obj != null)
                        ids.add(obj.getId());
                    else {
                        // assume it was an id that didn't pass 
                        // the isUniqueid test, shold log this!?
                        ids.add(name);
                    }
                }
            }
        }

        return ids;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Archives
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a request for the pseudo-attribute "history" which
     * represents the IdentityArchive objects associated with this identity.
     * We only support Remove requests with the value expected to be
     * an id or list of ids of IdentityHistory objects.  You cannot
     * make new history objects from a plan.
     */
    private void processArchives(AttributeRequest req) 
        throws GeneralException {

        // only remove, could handle Set if we had to
        if (req.getOp() == Operation.Remove) {

            List<String> ids = Util.asList(req.getValue());
            if (ids != null) {
                for (String id : ids) {
                    IdentityArchive arch = _context.getObjectById(IdentityArchive.class, id);
                    if (arch == null)
                        log.error("Request to remove invalid IdentityArchive: " + id);
                    else {
                        // expect the commit to happen later
                        _context.removeObject(arch);
                    }
                }
            }
        }
    }

    /**
     * Deletes an IdentityIQ Identity that came from an AccountRequest Delete Operation.
     * @throws GeneralException
     */
    private void deleteIdentity() throws GeneralException {
        if (_identity == null) {
            if (log.isErrorEnabled()) {
                log.error("Identity being provisioned is missing. Ignoring identity delete request");
            }
        } else {
            Terminator t = new Terminator(_context);
            t.deleteObject(_identity);
            _identity = null;
        }
    }

    /**
     * Currently only removes identity snapshots because that is what is
     * enabled in the UI
     */
    private void processSnapshots(AttributeRequest req)
        throws GeneralException {
        
        if (req.getOp() == Operation.Remove) {

            @SuppressWarnings("unchecked")
            List<String> ids = Util.asList(req.getValue());
            if (ids != null) {
                for (String id : ids) {
                    IdentitySnapshot snapshot = _context.getObjectById(IdentitySnapshot.class, id);
                    if (snapshot == null)
                        log.error("Request to remove invalid Identitysnapshot: " + id);
                    else {
                        // expect the commit to happen later
                        _context.removeObject(snapshot);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Events
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a request for the pseudo-attribute "events" which
     * represents the Request and WorkflowCase objects associated with this identity.
     * We only support Remove requests with the value expected to be
     * an id or list of ids of the related objects.  You cannot
     * schedule new Requests or launch new WorkflowCases through a plan.
     */
    private void processEvents(AttributeRequest req) 
        throws GeneralException {

        // only remove, could handle Set if we had to
        if (req.getOp() == Operation.Remove) {

            List<String> ids = Util.asList(req.getValue());
            if (ids != null) {
                for (String id : ids) {
                    Request event = _context.getObjectById(Request.class, id);
                    if (event != null) {
                        // may have locking issues here with the request processor
                        _context.removeObject(event);
                    }
                    else {
                        WorkflowCase wfcase = _context.getObjectById(WorkflowCase.class, id);
                        if (wfcase != null) {
                            // these have to be shut down gracefully
                            Workflower wf = new Workflower(_context);
                            wf.terminate(wfcase);
                        }
                        else {
                            log.error("Invalid event id: " + id);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process delete requests for ProvisioningRequests.
     * Hmm, processEvents is already handling two things, just do these
     * under ATT_IIQ_EVENTS?
     */
    private void processProvisioningRequests(AttributeRequest req) 
        throws GeneralException {

        if (req.getOp() == Operation.Remove) {

            List<String> ids = Util.asList(req.getValue());
            if (ids != null) {
                for (String id : ids) {
                    ProvisioningRequest preq = _context.getObjectById(ProvisioningRequest.class, id);
                    if (preq != null) {
                        _context.removeObject(preq);
                    }
                    else {
                        log.error("Invalid event id: " + id);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IIQ General Attribute Update
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a request for one identity attribute.
     * 
     * Besides setting the value we also update the AttributeMetadata
     * for this attribute.
     *
     * The old IdentityBean would only allow this for extended attributes
     * that were marked isEditable, should we do that here too or
     * just let anything in?
     */
    private void processIdentityAttribute(AttributeRequest req)
        throws GeneralException {

        String attrName = req.getName();
        ObjectConfig config = Identity.getObjectConfig();
        ObjectAttribute attrDef = config.getObjectAttribute(attrName);

        // old IdentityBean also checked isEditable, do that here too?
        if ( attrDef == null ) {
            log.error("Unhandled attribute: " + attrName);
            return;
        }

        Operation op = req.getOp();
        Object value = req.getValue();

        //TODO: should I check the editmode before allowing?
        if ( ( op == Operation.Add ) ||
             ( op == Operation.Remove ) ) {

            List<Object> currentValue = Util.asList(_identity.getAttribute(attrName));
            if ( currentValue == null ) {
                currentValue = new ArrayList<Object>();
            }
            List<Object> newValue = new ArrayList<Object>(currentValue);
            List<Object> incommingValues = Util.asList(value);
            for ( Object obj : incommingValues ) {
                if ( op == Operation.Add) {
                    newValue.add(obj);
                } else
                if ( op == Operation.Remove) {
                    newValue.remove(obj);
                }
            }

            updateAttributeMetadata(attrDef, _identity.getAttribute(attrName));
            _identity.setAttribute(attrName, newValue);
        } 
        else if (op == Operation.Set) {
            Object val = value;
            if ( attrDef.isMulti() ) {
                val = Util.asList(value);
            } 
            else {
                // try and coerce this to a string
                if ( value != null ) {
                    if ( value instanceof String ) { 
                        val = (String)value;
                    } 
                    else if ( value instanceof List ) {
                        List listValue = (List)val; 
                        if ( listValue.size() == 1) {
                            val = listValue.get(0); 
                        } else {
                            log.error("Identity attribute ["+attrName+"] is marked single valued, but its updated value was a list.");
                        }
                    }
                }
            }

            // if it is of type identity fetch that identity
            if (val != null && attrDef.isIdentity()) {
                String strVal = value.toString();
                if (!Util.isNullOrEmpty(strVal)) {
                    // bug21374 there are times when the identity in coming in as an id
                    //This should be fixed in the code sending the ID, not here -rap
                    val = _context.getObjectByName(Identity.class, strVal);
                }
            }   
            updateAttributeMetadata(attrDef, _identity.getAttribute(attrName));
            _identity.setAttribute(attrName, val);            
        }
    }

    /**
     * Update or create metadata for an identity attribute.
     * This is a variant of Objectutil.editObjectAttribute that only handles
     * identity and understands the provisioning plan.
     *
     * One difference between this and ObjectUtil.editObjectAttribute
     * is that the later coerces the value to a String.  There is also some
     * strange logic around the current metadata having a non-null user
     * that I totally don't get.
     *
     * Note that the "source" property of AttributeMetadata is NOT the same
     * as the ARG_SOURCE project attribute.  The later is a
     * RoleAssignment.SOURCE_* value used for role metadata.  The former
     * is the key of an AttributeSource object.
     *
     * AttributeSource keys are used to track where the attribute value
     * came from during attribute promotion from the links.  Here
     * we can assume that the plan is being evaluated in response
     * to a manual edit that overrides the mapping in the 
     * ObjectConfig if there was one.  So the source always goes to null.
     * If we ever use provisioning plans to implement the attribute
     * promotion being done by Identitizer then we'll need a way to 
     * pass the source in, probably as an argument to the
     * AttributeRequest.
     * 
     */
    private void updateAttributeMetadata(ObjectAttribute att, 
                                         Object currentValue ) {
        
        AttributeMetaData metadata = _identity.getAttributeMetaData(att.getName()); 
        
        if (metadata == null) {
            metadata = new AttributeMetaData();
            metadata.setAttribute(att.getName());
            _identity.addAttributeMetaData(metadata);
        }

        // name of the user that made the modification, this
        // must be set in the plan with ARG_ASSIGNER, the same
        // value that goes into RoleAssignment metdata
        metadata.setUser(getAssigner());

        // Only the feed is allowed to set the last value.  Once 
        // it's been established, don't override it.  See IIQSR-72.
        if (currentValue != null) {
            if (currentValue instanceof Identity) {
                currentValue = ((Identity) currentValue).getName();
            }
        }
        String lastValue = Util.otos(metadata.getLastValue());
        if (Util.isNullOrEmpty(lastValue)) {
            metadata.setLastValue(currentValue);
        }

        // this is a "manual edit" so the source always goes null
        metadata.setSource(null);

        // ObjectUtil only does this if the user started off non-null
        metadata.incrementModified();
    }

    /**
     * Check if the status indicates it is ready to be applied. 
     * This is true if status is either COMMITTED or  
     * QUEUED with optimism. 
     * 
     * This method has been moved to PlanApplier.isApplyable 
     * 
     * @See {@link PlanApplier#isApplyable(String, boolean)}
     *  
     * @param status Status of the plan or request. 
     * @param optimistic Are we optimistic? 
     * @return true if the plan can be applied 
     */
    @Deprecated
    public static boolean isApplyable(String status, boolean optimistic) {
        return PlanApplier.isApplyable(status, optimistic);
    }
}
