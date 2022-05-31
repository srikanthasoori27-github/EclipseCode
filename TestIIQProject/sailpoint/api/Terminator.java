/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This is a utility class that can delete various objects
 * while carefully pruning references to them from other objects.
 * 
 * This is necessary because most relationships between
 * objects are modeled by Hibernate with foreign key constraints.
 * The constraints prevent deletion of a row until all key references
 * from other tables are nulled. 
 *
 * This not intended for normal use, but it is extremely convenient
 * during development, sales demos, and pocs to do bulk removals of
 * classes like Identity and Application.
 *
 * djs: NOTE
 * This class should be used sparingly and only when absolutely necessary.
 *
 * After talking with Jeff I moved this to the api package so 
 * that we could encapsulate the cleanup of loose references.
 * I needed in the case for JasperPageBucket objects so we 
 * can remove the pages of a report when a JasperResult is deleted,
 * which is stored in a TaskResult. The console is already utilizing
 * this class and I didn't want to duplicate the removal logic in
 * ui bean.
 * 
 * If you are planning on using this class in the ui tier, make sure
 * you think of the ramifications of unraveling an entire object 
 * relationship.
 * 
 */

package sailpoint.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.*;
import sailpoint.score.EntitlementScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.workflow.RoleLibrary;

/**
 * Utility class that can delete SailPointObject subclasses
 * while carefully pruning references to them from other objects.
 * This is necessary to avoid foreign key constraint violations.
 */
public class Terminator extends Visitor {

    private static final Log log = LogFactory.getLog(Terminator.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The context we operate within.   
     */
    SailPointContext _context;

    /**
     * Flag to halt iteration of bulk deletion loops.   
     */
    boolean _terminate;

    /**
     * Flag to enable console trace.
     */
    boolean _trace;

    /**
     * Flag to prevent decaching associated objects.
     */
    boolean _noDecache;
    
    /**
     * Flag to prevent object locking.
     * This is used by the unit tests to avoid timeouts from locks left behind
     * by previous tests.
     */
    boolean _noLocking;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Terminator(SailPointContext con) {
        _context = con;
    }

    /**
     * Set the flag to terminate a bulk termination.
     * Used if the caller wanted to launch a thread to monitor
     * deletion and stop it.    
     */
    public void setTerminate(boolean b) {
        _terminate = b;
    }

    /**
     * Set whether to log deletion information to STDOUT when deleting.
     */
    public void setTrace(boolean b) {
        _trace = b;
    }

    /**
     * Set this to true to prevent deletion from decaching associated objects
     * that get loaded as references are cleaned up. This can be useful if
     * you are deleting a child object from code that still needs to reference
     * the parent.
     */
    public void setNoDecache(boolean b) {
        _noDecache = b;
    }

    /**
     * Set this to true to prevent locking of Identity objects.
     * This is used by unit tests when they want to clear out old objects but
     * don't want to timeout on locks that were left dangling by previous tests.
     * that get loaded as references are cleaned up. This can be useful if
     * you are deleting a child object from code that still needs to reference
     * the parent.
     */
    public void setNoLocking(boolean b) {
        _noLocking = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Generic
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete one object carefully.
     */
    public void deleteObject(SailPointObject obj)
        throws GeneralException {

        // kludge to get some minimal status in the console
        traceDelete(obj);

        // dispatch through the Visitor
        visit(obj);
    }

    private void traceDelete(SailPointObject obj) {

        // kludge to get some minimal status in the console
        if (_trace) {
            String className = obj.getClass().getSimpleName();
            // kludge: prune off Hibernate stuff
            int hibernateStuff = className.indexOf("$$");
            if (hibernateStuff > 0)
                className = className.substring(0, hibernateStuff);
            String name = obj.getName();
            if (name == null) name = obj.getId();

            if ( ( obj != null ) && ( obj instanceof Identity ) ) {
                boolean workgroup = ((Identity)obj).isWorkgroup();
                if ( workgroup ) 
                    className = "Workgroup";
            }
            System.out.println("Deleting " + className + " " + name);
        }
    }

    /**
     * Delete all objects matching a filter.
     * Assume there are many objects, so do a projection search,
     * commit and flush the cache as we go.
     */
    public void deleteObjects(Class<? extends SailPointObject> cls,
                              QueryOptions ops) 
        throws GeneralException {

        Iterator<String> it = getIds(cls, ops);

        while (it.hasNext() && !_terminate) {

            String id = it.next();
            if (id != null) {
                // sigh, need a delete method that doesn't fetch
                SailPointObject o = _context.getObjectById(cls, id);
                if (o != null) {
                    // this will commit and decache
                    deleteObject(o);
                }
            }
        }
    }

    /**
     * @exclude
     * Default visitor.
     */
    public void visitSailPointObject(SailPointObject obj)
        throws GeneralException {
        
        innerDelete(obj);
    }

    /**
     * The holy trinity of deletion.
     */
    private void innerDelete(SailPointObject o) throws GeneralException {

        _context.removeObject(o);
        _context.commitTransaction();
        _context.decache(o);
    }

    /**
     * Return a projection query containing the ids of the matching objects.
     * This is preferable to using getObjects when the number of potential
     * objects is large.  
     *
     * It is also preferable to avoid subtle attach
     * issues, if you follow this pattern:
     *
     *    List l = getObjects
     *    for each element of l
     *       modify l
     *       commit l
     *       decache l
     *    
     * After the first element, the remaining elements will not be flushed
     * if the modification is to remove something from a child list. You
     * can work around this by adding a saveObject call before the commit,
     * or you can fetch the objects one at a time by id after every commit.
     * @ignore
     * See snide comments in visitProfile for more info.
     *
     */
    public <T extends SailPointObject> Iterator<String> getIds(Class<T> cls, QueryOptions ops)
	throws GeneralException {

        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object []> it =  _context.search(cls, ops, props);
        HashSet<String> ids = new HashSet<>();
        while (it.hasNext()) {
            ids.add((String) (it.next()[0]));
        }

        return ids.iterator();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lock a related identity before modifying it.
     * This can be disabled at the Terminator level for unit tests,
     * and also at the HibernatePersistenceManager layer to completely
     * shut it down.
     */
    private Identity lockIfNecessary(String id) throws GeneralException {
        Identity ident = null;
        if (!_noLocking) {
            ident = ObjectUtil.lockIfNecessary(_context, id);
        }
        else {
            ident = _context.getObjectById(Identity.class, id);
        }
        return ident;
    }
    
    /**
     * Release a lock that may have been previously obtained by
     * lockIfNecessary.
     */
    private void unlockIfNecessary(Identity ident)
        throws GeneralException {

        if (!_noLocking) {
            ObjectUtil.unlockIfNecessary(_context, ident);
        }
        else {
            _context.saveObject(ident);
            _context.commitTransaction();
        }
    }

    /**
     * @exclude
     * Delete one identity, damnit.
     *
     * These are particularly complicated because many things can have
     * them as an owner.  Since every SailPointObject now has an owner
     * we could generalize this by iterating over ClassLists.MajorClasses
     * but in practice few classes actually use this field so we'll
     * just hard code them.
     *
     * This can be called from agg/refresh tasks so it is important that
     * we lock OTHER identities we have to touch.
     */
    public void visitIdentity(Identity identity)
        throws GeneralException {

        // KLUDGE: Preserve some system identities in case we're doing
        // a bulk delete
        if (identity.isProtected())
            return;

        if ( identity.isWorkgroup() ) {
            // in this case we need to query for all of the Identitieis
            // that have reference to this group and remove it..
            List<String> props = new ArrayList<String>();
            props.add("id");
            Iterator<Object[]> members = ObjectUtil.getWorkgroupMembers(_context, identity,props);
            // unlockIfNecessary causes resultset of members to close, so use an IdIterator.
            IdIterator idit = new IdIterator(_context, members);
            while ( idit.hasNext() ) {
                String id = idit.next();
                Identity member = lockIfNecessary(id);
                if (member != null) {
                    try {
                        List<Identity> workgroups = member.getWorkgroups();
                        if ( Util.size(workgroups) > 0  ) {
                            workgroups.remove(identity);
                        }
                    }
                    finally {
                        // this will also commit
                        unlockIfNecessary(member);
                    }
                }
            }
        }

        // Link will be deleted with cascade
        //
        // djs: but be sure to clean up external references 
        // 
        List<Link> links = identity.getLinks();
        if ( Util.size(links) > 0 ) {
            for ( Link link : links ) {
                removeLinkExternalAttributes(link);
            }
        }

        // For some odd reason, MitigationExpiration started needing
        // to be deleted explicitly even though the relationship
        // is cascade=all.  This may have just been a temporary situation
        // brought about by orphaned MitigationExpirations?

        // null references to things we're going to delete in bulk
        identity.setScorecard(null);
        //Can't just set null. Getting LazyInitializationException -rap
        //identity.setWorkgroups(null);
        //Fetching the lazy associations will fix the LazyInit issue, however, we don't need to do this at all. -rap
//        identity.getWorkgroups().clear();

        identity.setOwner(null);

        identity.setUIPreferences(null);
        
        // Link will be cascade deleted when we delete the Identity

        // some of these like Profile and Process don't exist any more
        pruneOwner(Identity.class, identity);
        pruneOwner(Bundle.class, identity);
        pruneOwner(Application.class, identity);
        pruneOwner(TaskResult.class, identity);
        pruneRemediator(identity);
        pruneSecondaryOwner(identity);
        pruneOwner(Profile.class, identity);
        pruneRequester(identity);
        pruneMitigator(identity);
        pruneOwner(CertificationDefinition.class, identity);
        pruneOwner(CertificationGroup.class, identity);
        pruneOwner(CertificationAction.class, identity);
        pruneOwner(Policy.class, identity);
        pruneOwner(GenericConstraint.class, identity);
        pruneOwner(ActivityConstraint.class, identity);
        pruneOwner(SODConstraint.class, identity);
        pruneOwner(ManagedAttribute.class, identity);
        pruneOwner(CertificationGroup.class, identity);
        pruneOwner(PolicyViolation.class, identity);
        pruneAssignedRemediationItems(identity);
        pruneOwner(AccountGroup.class, identity);
        pruneOwner(GroupDefinition.class, identity);
        pruneOwner(TaskDefinition.class, identity);
        pruneOwner(TaskEvent.class, identity);
        pruneOwner(BatchRequest.class, identity);
        pruneOwner(IdentityRequestItem.class, identity);
        deleteOwned(UIPreferences.class, identity);
        pruneOwner(AlertDefinition.class, identity);
        pruneOwner(Attachment.class, identity);
        
        reassignOwnedWorkItems(identity);
        reassignOwnedDelegations(identity);
        deleteOwned(Request.class, identity);
        deleteRequestedWorkItems(identity);

        // WorkItem.assignee - Just null this out ... it will go back to the workgroup.
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("assignee", identity));
        List<WorkItem> workItems = _context.getObjects(WorkItem.class, ops);
        if (workItems != null) {
            for (WorkItem workItem : workItems) {
                workItem.setAssignee(null);
            }
        }
        _context.commitTransaction();

        // Policy.violationOwner
        ops = new QueryOptions();
        ops.add(Filter.eq("violationOwner", identity));
        List<Policy> policies = _context.getObjects(Policy.class, ops);
        if (policies != null) {
            for (Policy p : policies) {
                p.setViolationOwner(null);
            }
        }
        _context.commitTransaction();


        // PolicyViolation.identity
        // These are violations detected for the identity being deleted, the relationship
        // implies a cascade delete. The difference between PolicyViolation.identity
        // and PolicyViolation.owner is subtle, the identity is the one that is in violation,
        // and the owner is the one that is supposed to take action on the violation.
        // Deleting the owner just sets the owner field to null (above).

        ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        List<PolicyViolation> violations = _context.getObjects(PolicyViolation.class, ops);
        if (violations != null) {
            for (PolicyViolation v : violations) {
                v.setIdentity(null);
                //_context.removeObject(v);
                deleteObject(v);
            }
        }
        _context.commitTransaction();

        // IdentitySnapshot
        // these have weak references so there is no pruning, but we don't
        // want to leave them around
        // Note that these have to be commited before deleting Scorecards
        // because there is a reference from the snapshot to the scorecard
        ops = new QueryOptions();
        ops.add(Filter.eq("identityId", identity.getId()));
        deleteObjects(IdentitySnapshot.class, ops);

        // Delete any associated IdentityHistoryItems
        ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        this.deleteObjects(IdentityHistoryItem.class, ops);
        _context.commitTransaction();

        // Scorecard
        // !! Same stuff as PolicyViolation..
        // hmm, this could be a big result, should be using a projection
        ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        List<Scorecard> cards = _context.getObjects(Scorecard.class, ops);
        if (cards != null) {
            for (Scorecard c : cards) {
                c.setIdentity(null);
                //_context.removeObject(c);
                deleteObject(c);
            }
        }
        _context.commitTransaction();

        // This identity may also be a manger of another identity, 
        // have to prune those from the other direction
        // NOTE: This search seems to create duplicate copies of the
        // Identity and/or the Identity/Link collection.  Trying to 
        // commit after this results in "Found two representations of the
        // same collection: sailpoint.object.Identity.links" exception.
        // This feels like a bug, but it is unclear why and the forum is useless.
        // Commit now and get on with our lives.
        _context.commitTransaction();

        // we could avoid some thrash by making two passes, one to null
        // out all managers, then another to delete them
        ops = new QueryOptions();
        ops.add(Filter.eq("manager", identity));
        Iterator<String> it = getIds(Identity.class, ops);

        while(it.hasNext()) {
            String id = it.next();
            Identity sub = lockIfNecessary(id);
            if (sub != null) {
                try {
                    sub.setManager(null);
                }
                finally {
                    unlockIfNecessary(sub);
                    // sigh, another potentially long list, commit as we go
                    _context.decache(sub);
                }
            }
        }

        // Clear out administrator too, I know this code looks similar to manager but we dont do much
        // sharing in here.
        // You could maybe do administrator and manager in a single query and iteration but that could force loading
        // more objects, and there shouldn't be too any identities with the same value for both. So just keep 'em
        // separated.
        ops = new QueryOptions();
        ops.add(Filter.eq("administrator", identity));
        it = getIds(Identity.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Identity sub = lockIfNecessary(id);
            if (sub != null) {
                try {
                    sub.setAdministrator(null);
                }
                finally {
                    unlockIfNecessary(sub);
                    // sigh, another potentially long list, commit as we go
                    _context.decache(sub);
                }
            }
        }
        
        // Clean references to the current identity from WorkItemConfig._owners
        cleanUpIdentityReferencesInList(identity, "owners", WorkItemConfig.class);

        // Remove any external identity attributes
        ops = new QueryOptions();
        ops.add(Filter.eq("objectId", identity.getId()));
        ops.setCloneResults(true);
        Iterator<IdentityExternalAttribute> attrIt = _context.search(IdentityExternalAttribute.class, ops);
        if ( attrIt != null ) {
            while (attrIt.hasNext()) {
                IdentityExternalAttribute extern = attrIt.next();
                _context.removeObject(extern);
                _context.commitTransaction();
                _context.decache(extern);
            }
        }

        // IdentityArchives
        ops = new QueryOptions();
        ops.add(Filter.eq("sourceId", identity.getId()));
        deleteObjects(IdentityArchive.class, ops);

        // delete activity constraints
        ops = new QueryOptions();
        ops.add(Filter.eq("violationOwner", identity));
        List<ActivityConstraint> activityConstraints = _context.getObjects(ActivityConstraint.class, ops);
        if (activityConstraints != null) {
            for (ActivityConstraint constraint : activityConstraints) {
                constraint.setViolationOwner(null);
                deleteObject(constraint);
            }
        }
        _context.commitTransaction();

        // delete sod constraints
        ops = new QueryOptions();
        ops.add(Filter.eq("violationOwner", identity));
        List<SODConstraint> sodConstraints = _context.getObjects(SODConstraint.class, ops);
        if (sodConstraints != null) {
            for (SODConstraint constraint : sodConstraints) {
                constraint.setViolationOwner(null);
                constraint.setViolationOwnerType(Policy.ViolationOwnerType.None);
                // Leave the sod constraint with a null owner and
                // owner type of None. That's the default anyway.
            }
        }
        _context.commitTransaction();

        // ProvisioningRequests
        ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        List<ProvisioningRequest> prequests = _context.getObjects(ProvisioningRequest.class, ops);
        if (prequests != null) {
            for (ProvisioningRequest preq : prequests) {
                preq.setIdentity(null);
                deleteObject(preq);
            }
        }
        _context.commitTransaction();

        // related identity extended attributes
        cleanupFromRelatedExtendedIdentities(identity);
        
        // UIPreferences will be deleted automatically with cascacde, right?
        
        //
        // Delete any IdentityEntitlments that reference the identity
        // Bulk delete used, here commit required.
        //
        ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        _context.removeObjects(IdentityEntitlement.class, ops);
        _context.commitTransaction();   
        
        // Clean references to the current identity from DynamicScopes
        cleanUpIdentityReferencesInList(identity, "inclusions", DynamicScope.class);
        cleanUpIdentityReferencesInList(identity, "exclusions", DynamicScope.class);
        
        // ok, NOW we're ready
        innerDelete(identity);
    }

    private void cleanupFromRelatedExtendedIdentities(Identity identity) throws GeneralException {

        if (!identity.supportsExtendedIdentity()) {
            return;
        }
        
        for (int i=1; i<SailPointObject.MAX_EXTENDED_IDENTITY_ATTRIBUTES; ++i) {
            try {
                String propertyName = "extendedIdentity" + i;
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq(propertyName, identity));
                Iterator<String> it = getIds(Identity.class, ops);
                while (it.hasNext()) {
                    String id = it.next();
                    // if this is self referential avoid releasing the lock, just ignore it
                    if (!id.equals(identity.getId())) {
                        Identity related = lockIfNecessary(id);
                        if (related != null) {
                            try {
                                Reflection.setProperty(related, propertyName, Identity.class, null);
                            }
                            finally {
                                unlockIfNecessary(related);
                            }
                        }
                    }
                }
            }
            catch (Exception ex) {
                // It is ok to continue here because they may have mapped less than
                // MAX_EXTENDED_IDENTITY_ATTRIBUTES
                log.warn(ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * @exclude
     */
    @Override
    public void visitCertificationGroup(CertificationGroup certGroup) throws GeneralException {

        if (certGroup == null)
            return;

        // The list of certifications could be huge, so be mindful of cache
        QueryOptions childOps = new QueryOptions(Filter.eq("certificationGroups.id", certGroup.getId()));
        this.deleteObjects(Certification.class, childOps);
        
        // Certificationer.delete will now decache, so have to refetch
        // the group
        certGroup = _context.getObjectById(CertificationGroup.class, certGroup.getId());

        if (certGroup.getDefinition() != null && certGroup.getDefinition().isContinuous()) {
        	deleteObject(certGroup.getDefinition());
        }

        innerDelete(certGroup);
    }

    /**
     * Remove the owner reference to an Identity from all objects
     * of one class.
     */
    private void pruneOwner(Class<? extends SailPointObject> cls,
                            Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", identity));

        if ( Identity.class.equals(cls) ) {
            ops.add(Filter.in("workgroup",
                              Arrays.asList(new Boolean[]{true,false})));
        }
        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        // jsl - technically if we have an Identity here we should lock it,
        // have we ever actually allowed Identity to have an owner?
        List<? extends SailPointObject> objects = _context.getObjects(cls, ops);
        if (objects != null) {
            for (SailPointObject o : objects) {
                o.setOwner(null);
                // don't really need this, its dirty in 
                // the session cache
                //_context.saveObject(o);
            }
        }
    }

    /**
     * Remove the assignee reference to an Identity on
     * RemediationItems. This is typically set when 
     * dealing with workgroup assigned remediation workitems.
     */
    private void pruneAssignedRemediationItems(Identity identity) 
        throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("assignee", identity));
        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<RemediationItem> objects = _context.getObjects(RemediationItem.class, ops);
        if (objects != null) {
            for ( RemediationItem o : objects) {
                o.setAssignee(null);
            }
        }
    }

    /**
     * Remove the requester reference to an Identity from all WorkItems
     */
    private void pruneRequester(Identity identity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("requester", identity));

        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<WorkItem> objects = _context.getObjects(WorkItem.class, ops);
        if (objects != null) {
            for (WorkItem o : objects) {
                o.setRequester(null);
            }
        }
    }

    private void pruneRemediator(Identity remediator) throws GeneralException {
        QueryOptions ops = new QueryOptions();        
        ops.add(Filter.containsAll("remediators", Arrays.asList(new Identity[] {remediator})));
        List<Application> apps = _context.getObjects(Application.class, ops);
        if (apps != null) {
            for (Application app : apps) {
                try {
                    List<Identity> currentRemediators = app.getRemediators();
                    currentRemediators.remove(remediator);
                    app.setRemediators(currentRemediators);
                } catch (UnsupportedOperationException e) {
                    // This is not as efficient, but it may be necessary in the 
                    // event that the returned List implementation does not 
                    // support the remove method
                    List<Identity> currentRemediators = app.getRemediators();
                    List<Identity> updatedRemediators = new ArrayList<Identity>();
                    
                    for (Identity currentRemediator : currentRemediators) {
                        if (!remediator.equals(currentRemediator)) {
                            updatedRemediators.add(currentRemediator);
                        }
                    }
                    
                    app.setRemediators(updatedRemediators);
                }
            }
        }
    }
    
    private void pruneSecondaryOwner(Identity secondaryOwner) throws GeneralException {
        QueryOptions ops = new QueryOptions();        
        ops.add(Filter.containsAll("secondaryOwners", Arrays.asList(new Identity[] {secondaryOwner})));
        List<Application> apps = _context.getObjects(Application.class, ops);
        if (apps != null) {
            for (Application app : apps) {
                try {
                    List<Identity> currentSecondaryOwners = app.getSecondaryOwners();
                    currentSecondaryOwners.remove(secondaryOwner);
                    app.setSecondaryOwners(currentSecondaryOwners);
                } catch (UnsupportedOperationException e) {
                    // This is not as efficient, but it may be necessary in the 
                    // event that the returned List implementation does not 
                    // support the remove method
                    List<Identity> currentSecondaryOwners = app.getSecondaryOwners();
                    List<Identity> updatedSecondaryOwners = new ArrayList<Identity>();
                    
                    for (Identity currentSecondaryOwner : currentSecondaryOwners) {
                        if (!secondaryOwner.equals(currentSecondaryOwner)) {
                            updatedSecondaryOwners.add(currentSecondaryOwner);
                        }
                    }
                    
                    app.setSecondaryOwners(updatedSecondaryOwners);
                }
            }
        }
    }

    /**
     * The mitigator is a non-null field on MitigationExpiration, so we can
     * either reset the mitigator to another user or just remove the
     * MitigationExpiration from the identity. For simplicity, we will remove
     * the mitigation expiration.
     * 
     * @param  mitigator  The mitigator that is being deleted.
     */
    private void pruneMitigator(Identity mitigator) throws GeneralException {

        QueryOptions ops = new QueryOptions();        
        ops.add(Filter.eq("mitigator", mitigator));
        List<MitigationExpiration> exps = _context.getObjects(MitigationExpiration.class, ops);
        if (exps != null) {
            for (MitigationExpiration mex : exps) {
                // The MitigationExpiration doesn't have a back-pointer to its
                // owner, so we'll have to search for the owner.
                Filter f = Filter.eq("mitigationExpirations.id", mex.getId());
                Identity identity = _context.getUniqueObject(Identity.class, f);
                if (null != identity) {
                    // have to lock it to avoid the persistence layer whining,
                    // could have avoided a redundant fetch by querying for the id rather
                    // than using getUniqueObject
                    identity = lockIfNecessary(identity.getId());
                    try {
                        identity.remove(mex);
                    }
                    finally {
                        unlockIfNecessary(identity);
                    }
                }
                _context.removeObject(mex);
            }
        }
    }

    /**
     * Delete any object owned by this identity.
     * Historically taken care to 
     * null the owner reference too, it was a Hibernate
     * session cache issue.
     */
    private <T extends SailPointObject> void deleteOwned(Class<T> cls, Identity identity)
        throws GeneralException {

        List<T> objects = getOwnedObjects(cls, identity);
        if (objects != null) {
            for (SailPointObject o : objects) {
                o.setOwner(null);
                //_context.removeObject(o);
                deleteObject(o);
            }
        }
    }

    private <T extends SailPointObject> List<T> getOwnedObjects(Class<T> cls, Identity identity)
            throws GeneralException {
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", identity));

        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<T> objects = _context.getObjects(cls, ops);
        return objects;
    }
    
    /**
     * Instead of deleting the workitems we reassign to spadmin.
     * Otherwise workflow case will remain hanging. 
     * See bug #7799 for more details.
     */
    private void reassignOwnedWorkItems(Identity identity) throws GeneralException {
        
        List<WorkItem> workItems = getOwnedObjects(WorkItem.class, identity);
        if (Util.size(workItems) == 0) {
            return;
        }
        Identity spadmin = _context.getObjectByName(Identity.class, Identity.ADMIN_NAME);
        for (WorkItem item : workItems) {
            item.setOwner(spadmin);
            _context.saveObject(item);
            _context.commitTransaction();
        }
    }

    private void reassignOwnedDelegations(Identity identity) throws GeneralException {
        
        List<CertificationDelegation> delegations = getCertificationDelegations(identity);
        if (Util.size(delegations) == 0) {
            return;
        }
        for (CertificationDelegation delegation : delegations) {
            delegation.setOwnerName(Identity.ADMIN_NAME);
            _context.saveObject(delegation);
            _context.commitTransaction();
        }
    }

    private List<CertificationDelegation> getCertificationDelegations(Identity identity)
            throws GeneralException {
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("ownerName", identity.getName()));

        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<CertificationDelegation> objects = _context.getObjects(CertificationDelegation.class, ops);
        return objects;
    }
    
    private void deleteRequestedWorkItems(Identity identity) throws GeneralException {
        QueryOptions ops = new QueryOptions().add(Filter.eq("requester", identity));
        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<WorkItem> workItems = _context.getObjects(WorkItem.class, ops);
        if (workItems != null) {
            for (WorkItem workItem : workItems) {
                deleteObject(workItem);
            }
        }        
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // IdentityTrigger
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitIdentityTrigger(IdentityTrigger trigger) 
        throws GeneralException {

        // Should we null out any certifications that reference this (not
        // completely necessary since this isn't a hard reference)?  Go ahead
        // and do this since getObjectById() will explode with a non-existent
        // object.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("triggerId", trigger.getId()));
        List<Certification> certs = _context.getObjects(Certification.class, qo);
        for (Certification cert : certs) {
            cert.setTriggerId(null);
            _context.saveObject(cert);

            // Commit so that we can decache.
            _context.commitTransaction();
            _context.decache(cert);
        }

        innerDelete(trigger);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Application
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Delete one Application and related flotsam.
     */
    public void visitApplication(Application application)
        throws GeneralException {

        // lots of decaching are about to happen and application has to
        // survive the whole ordeal
        application.load();

        // null references to things we're going to delete in bulk
        application.setScorecard(null);
        // save this so scorecard deletion later won't pick it up
        _context.commitTransaction();
        
        // Proxy, just null out the references the proxied apps
        // may still be in use
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("proxy", application));
        IncrementalObjectIterator<Application> appIt = new IncrementalObjectIterator<Application>(_context, Application.class, ops);
        while (appIt.hasNext()) {
            Application a = appIt.next();
            if (a != null) {
                a.setProxy(null);
                _context.commitTransaction();
                _context.decache();
            }
        }

        // AccountGroups
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<AccountGroup> acctGrpIt = new IncrementalObjectIterator<AccountGroup>(_context, AccountGroup.class, ops);
        while (acctGrpIt.hasNext()) {
            AccountGroup g = acctGrpIt.next();
            deleteObject(g);
            if (acctGrpIt.getCount() % 20 == 0) {
                _context.decache();
            }
        }
        // cleanup the rest
        _context.decache();

        // Profiles don't make sense without their Application, 
        // but leave the Bundles behind
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<Profile> profileIt = new IncrementalObjectIterator<Profile>(_context, Profile.class, ops);
        while (profileIt.hasNext()) {
            Profile p = profileIt.next();
            if (p != null) {
                p.setApplication(null);
                deleteObject(p);
                if (profileIt.getCount() % 20 == 0) {
                    _context.decache();
                }
            }
        }
        // cleanup the rest
        _context.decache();
        
        // EntitlementGroup is used by Identity to represent extra entitlements
        // sadly, there isn't an easy way to get from an EntitlementGroup
        // back to an Identity (there is no backref) so the best we can
        // do is null the reference.  
        // !! this may cause NPR's if you are not also deleting Identity

        // Get the list of identities that have entitlementgroups with application
        
        ops = new QueryOptions();
        ops.add(Filter.join("exceptions.id", "EntitlementGroup.id"));
        ops.add(Filter.eq("exceptions.application", application));
        IncrementalObjectIterator<Identity> identIt = new IncrementalObjectIterator<Identity>(_context, Identity.class, ops);

        
        // Go through the identities exceptions
        // If the entitlementgroup has the specified application
        // delete the entitlementgroup and remove from exceptions list.
        while (identIt.hasNext() && !_terminate) {
            Identity ident = (Identity)identIt.next();

            List<EntitlementGroup> excs = ident.getExceptions();

            Iterator<EntitlementGroup> egIt = excs.iterator();

            while (egIt.hasNext() && !_terminate) {
                EntitlementGroup egroup = egIt.next();

                if (egroup != null && application.equals(egroup.getApplication())) {
                    egIt.remove();
                }
            }
            if(identIt.getCount() % 10 == 0) {
                _context.commitTransaction();
                _context.decache();
            }
        }
        
        if (!_terminate && identIt.getCount() % 10 != 0) {
            _context.commitTransaction();
            _context.decache();
        }
        
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<EntitlementGroup> entGroupIt = new IncrementalObjectIterator<EntitlementGroup>(_context, EntitlementGroup.class, ops);
        while (entGroupIt.hasNext() && !_terminate) {
            EntitlementGroup g = entGroupIt.next();
            if (g != null) {
                g.setApplication(null);
                deleteObject(g);
                if (entGroupIt.getCount() % 20 == 0) {
                    _context.decache();
                }
            }
        }
        
        // clean up
        _context.decache();

        // Link is similar to EntitlementGroup but here we do have a backref
        // to the owning Identity
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<Link> linkIt = new IncrementalObjectIterator<Link>(_context, Link.class, ops);
        while (linkIt.hasNext() && !_terminate) {
            Link link = linkIt.next();
            if (link != null) {
                link.setApplication(null);
                deleteObject(link);
                if (linkIt.getCount() % 20 == 0) {
                    _context.decache();
                }
            }
        }

        // clean up
        _context.decache();

        // Remove this Application from the dependency list of any
        // other Application
        ops = new QueryOptions();
        ops.add(Filter.eq("dependencies.id", application.getId()));
        appIt = new IncrementalObjectIterator<Application>(_context, Application.class, ops);
        while (appIt.hasNext()) {
            Application app = appIt.next();
            if (null != app) {
                app.getDependencies().remove(application);
                _context.saveObject(app);
                _context.commitTransaction();
                _context.decache();
            }
        }

        // ApplicationScorecard
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<ApplicationScorecard> cardsIt = new IncrementalObjectIterator<ApplicationScorecard>(_context, ApplicationScorecard.class, ops);
        while (cardsIt.hasNext() && !_terminate) {
            ApplicationScorecard card = cardsIt.next();
            card.setApplication(null);
            deleteObject(card);
            _context.decache();
        }
        _context.commitTransaction();

        //remove associated Managed Attributes
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<ManagedAttribute> managedAttrIt = new IncrementalObjectIterator<ManagedAttribute>(_context, ManagedAttribute.class, ops);
        while (managedAttrIt.hasNext() && !_terminate){
            ManagedAttribute attr = managedAttrIt.next();
            if (attr != null) {
                attr.setApplication(null);
                deleteObject(attr);
                if (managedAttrIt.getCount() % 20 == 0) {
                    _context.decache();
                }
            }
        }
        // clean up
        _context.decache();

        // remove Targets
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<Target> targetIt = new IncrementalObjectIterator<Target>(_context, Target.class, ops);
        while (targetIt.hasNext() && !_terminate){
            Target targ = targetIt.next();
            if (targ != null) {
                targ.setApplication(null);
                deleteObject(targ);
                if (targetIt.getCount() % 20 == 0) {
                    _context.decache();
                }
            }
        }
        // clean up
        _context.decache();


        // prune references from Forms
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<Form> formIt = new IncrementalObjectIterator<Form>(_context, Form.class, ops);
        while (formIt.hasNext() && !_terminate){
            Form form = formIt.next();
            if (form != null) {
                form.setApplication(null);
                _context.saveObject(form);
                _context.commitTransaction();
                _context.decache();
            }
        }

        // ObjectAttribute inside ObjectConfig has an XML serialized 
        // reference to the app.  Note that we don't have the Hibernate
        // issue discussed above visitProfile here because the child list
        // is serialized to XML, hooray!

        List<ObjectConfig> configs = _context.getObjects(ObjectConfig.class, null);
        boolean removed = false;
        if (configs != null) {
            for (ObjectConfig config : configs) {
                List<ObjectAttribute> atts = config.getObjectAttributes();
                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        List<AttributeSource> sources = att.getSources();
                        Iterator<AttributeSource> sit = Util.iterate(sources).iterator();
                        while (sit.hasNext()) {
                            AttributeSource src = sit.next();
                            if (src.getApplication() != null && application.getId().equals(src.getApplication().getId())) {
                                sit.remove();
                                removed = true;
                            }
                        }
                        List<AttributeTarget> attTargets = att.getTargets();
                        Iterator<AttributeTarget> attTargetIt = Util.iterate(attTargets).iterator();
                        while (attTargetIt.hasNext()) {
                            AttributeTarget attTarget = attTargetIt.next();
                            if (attTarget.getApplication() != null && application.getId().equals(attTarget.getApplication().getId())) {
                                attTargetIt.remove();
                                removed = true;
                            }
                        }
                    }
                }
            }
        }
        if (removed) {
            _context.commitTransaction();
        }

        // IntegrationConfig
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        IncrementalObjectIterator<IntegrationConfig> intCfgIt = new IncrementalObjectIterator<IntegrationConfig>(_context, IntegrationConfig.class, ops);
        while (intCfgIt.hasNext() && !_terminate) {
            IntegrationConfig config = intCfgIt.next();
            if (config != null) {
                config.setApplication(null);
                _context.saveObject(config);
                if (intCfgIt.getCount() % 20 == 0) {
                    _context.commitTransaction();
                    _context.decache();
                }
            }
        }
        
        if (intCfgIt.getCount() % 20 != 0) {
            _context.commitTransaction();
            _context.decache();
        }

        // The ManagedResources can also have Application references
        // but these are buried in the XML.   Do we need to prune these?

        // ScoreConfig
        // ugh, wish these were soft references so we wouldn't have to dig
        // into scorer-specific models
        removed = false;
        ScoreConfig sc = _context.getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
        if (sc != null) {
            List<ScoreDefinition> defs = sc.getIdentityScores();
            if (defs != null) {
                for (ScoreDefinition def : defs) {
                    List<ApplicationEntitlementWeights> weights = 
                        (List<ApplicationEntitlementWeights>)def.getArgument(EntitlementScoreConfig.ARG_APPLICATION_WEIGHTS);
                    if (weights != null) {
                        ListIterator<ApplicationEntitlementWeights> li = weights.listIterator();
                        while (li.hasNext()) {
                            ApplicationEntitlementWeights weight = li.next();
                            if (weight.getApplication() == application) {
                                li.remove();
                                removed = true;
                            }
                        }
                    }
                }
            }
        }
        if (removed) {
            _context.commitTransaction();
        }

        // Configuration, pass through authorization
        Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        if (config != null) {
            Object o = config.get(Configuration.LOGIN_PASS_THROUGH);
            // ugh, usually a List<Reference> but not always
            if (o instanceof List) {
                List list = (List)o;
                ListIterator lit = list.listIterator();
                while (lit.hasNext()) {
                    Object el = lit.next();
                    if (el instanceof String) {
                        String s = (String)el;
                        if (s.equals(application.getId()) ||
                            s.equals(application.getName()))
                            lit.remove();
                    }
                    else if (el instanceof Reference) {
                        Reference ref = (Reference)el;
                        if (application.getId().equals(ref.getId()) ||
                            application.getName().equals(ref.getName()))
                            lit.remove();
                    }
                }
            }
            else if (o instanceof String) {
                // assume csv   
                List<String> list = Util.csvToList((String)o);
                if (list != null) {
                    list.remove(application.getId());
                    list.remove(application.getName());
                }
                config.put(Configuration.LOGIN_PASS_THROUGH, Util.listToCsv(list));
            }
            _context.commitTransaction();
        }

        //
        // Bulk delete any IdentityEntitlments that reference the application
        //
        ops = new QueryOptions();
        ops.add(Filter.eq("application", application));
        _context.removeObjects(IdentityEntitlement.class, ops);
        _context.commitTransaction();
        _context.decache();

        // Remove application from RapidSetup Configuration
        Configuration cfg = _context.getObjectByName(Configuration.class, Configuration.RAPIDSETUP_CONFIG);
        if (cfg != null) {
            RapidSetupConfigUtils.removeApplication(cfg, application.getName());
            _context.saveObject(cfg);
            _context.commitTransaction();
            _context.decache();
        }


        // remove associated LocalizedAttributes
        visitDescribable(application);
        
        innerDelete(application);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ApplicationScorecard
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitApplicationScorecard(ApplicationScorecard card)
        throws GeneralException {

        Application app = card.getApplication();
        if (app != null) {
            ApplicationScorecard current = app.getScorecard();
            if (current != null && card.equals(current)) {
                app.setScorecard(null);
                _context.saveObject(app);
            }
            card.setApplication(null);
        }
        else  {
            // we need to go look for applications that have references to this scorecard
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("scorecard.id", card.getId()));

            Iterator<Application> applicationIterator = new IncrementalObjectIterator<Application>(_context, Application.class, qo);

            while(applicationIterator.hasNext()) {
                Application application = applicationIterator.next();
                if (application.getScorecard() != null) {
                    application.setScorecard(null);
                    _context.saveObject(application);
                }
            }
        }

        innerDelete(card);

        if (app != null) {
            // !!! , we may be called recursively 
            // when deleting the Application, need to fix this
            // and have more context to make these decisions
            //_context.decache(app);
        }
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // AuthenticationQuestion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * This removes/deletes any AuthenticationAnswers on any Identities that
     * have answered the give question.
     */
    @Override
    public void visitAuthenticationQuestion(AuthenticationQuestion question)
        throws GeneralException {

        // Find any identities that have answers for the given question.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("authenticationAnswers.question", question));
        Iterator<String> ids = getIds(Identity.class, qo);
        int idx = 0;

        while (ids.hasNext()) {
            String id = ids.next();
            Identity identity = lockIfNecessary(id);
            if (identity != null) {
                try {
                    List<AuthenticationAnswer> answers =
                        new ArrayList<AuthenticationAnswer>(identity.getAuthenticationAnswers());
                    for (AuthenticationAnswer answer : answers) {
                        if (question.equals(answer.getQuestion())) {
                            identity.removeAuthenticationAnswer(answer);
                            deleteObject(answer);
                        }
                    }
                    idx++;
                }
                finally {
                    unlockIfNecessary(identity);
                }
            }
            
            // Decache every once in a while to prevent cache bloat.
            if ((idx % 100) == 0) {
                _context.commitTransaction();
                _context.decache();
            }
        }

        innerDelete(question);
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Profile
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * NOTE WELL: I hit a bizzare Hibernate problem here and in a few
     * other places like Application->Profile.  If you use getObjects()
     * to fetch several objects in to the session, iterate over them 
     * removing something from a child array, and committing after each one,
     * objects after the first one in the list will not be marked dirty
     * and will not be flushed.  That's right.
     *
     * Here if the profile appears in more than one bundle it would
     * be removed from the first but not the second causing a foreign
     * key violation when we try to delete the profile.  
     * 
     * If you think you want to find the reason why I left the original
     * implementation commented out below.  It probably has something
     * to do with the fact that we're modifying a child list not the
     * parent object and when you commit something happens to the
     * parent/child reference for the remaining objects on the list. 
     * Don't believe me?  Comment out the original implementation below
     * and have fun!
     *
     * There are two solutions to this, a simple one is to add a call
     * to saveObject before the commit.  This will mark the object dirty
     * by setting a modification date and it will be flushed.
     * An arguably better approach for all these sorts of object iterations
     * is to do a projection query to just get the ids, then fetch the
     * objects one at a time.  Since we will be fetching a fresh object
     * after every commit, it does not appear to have the problem with
     * the child list detaching.
     *
     * UPDATE: Well, the incremental id fetching doesn't work either
     * there is some fundamental problem modifying child lists that
     * will not flush the parent.  This is disturbing, need to find out why.
     * Make sure to call saveObject before committing.
     */
    public void visitProfile(Profile profile)
        throws GeneralException {

        // sigh, a MEMBER operator would be nice
        List<Profile> profiles = new ArrayList<Profile>();
        profiles.add(profile);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.containsAll("profiles", profiles));

        /* Original implementation
        List<Bundle> bundles = _context.getObjects(Bundle.class, ops);
        if (bundles != null) {
            for (Bundle b : bundles) {
                b.remove(profile);
                _context.commitTransaction();
                _context.decache(b);
            }
        }
        */

        // New, improved, working implementation
        Iterator<String> it = getIds(Bundle.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Bundle b = _context.getObjectById(Bundle.class, id);
            if (b != null) {
                b.remove(profile);
                _context.saveObject(b);
                _context.commitTransaction();
                _context.decache(b);
            }
        }

        profile.setApplication(null);

        innerDelete(profile);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Bundle
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitBundle(Bundle b) throws GeneralException {

        QueryOptions ops;
        Iterator<String> it;
        List<Bundle> bundles = new ArrayList<Bundle>();
        bundles.add(b);

        // inheritance
        ops = new QueryOptions();
        ops.add(Filter.containsAll("inheritance", bundles));
        it = getIds(Bundle.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Bundle other = _context.getObjectById(Bundle.class, id);
            if (other != null) {
                other.removeInheritance(b);
                _context.saveObject(other);
                _context.commitTransaction();
                _context.decache(other);
            }
        }

        // permits
        ops = new QueryOptions();
        ops.add(Filter.containsAll("permits", bundles));
        it = getIds(Bundle.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Bundle other = _context.getObjectById(Bundle.class, id);
            if (other != null) {
                other.removePermit(b);
                _context.saveObject(other);
                _context.commitTransaction();
                _context.decache(other);
            }
        }

        // requirements
        ops = new QueryOptions();
        ops.add(Filter.containsAll("requirements", bundles));
        it = getIds(Bundle.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Bundle other = _context.getObjectById(Bundle.class, id);
            if (other != null) {
                other.removeRequirement(b);
                _context.saveObject(other);
                _context.commitTransaction();
                _context.decache(other);
            }
        }

        // Identity detected roles
        ops = new QueryOptions();
        ops.add(Filter.containsAll("bundles", bundles));
        it = getIds(Identity.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Identity ident = lockIfNecessary(id);
            if (ident != null) {
                try {
                    ident.remove(b);
                }
                finally {
                    unlockIfNecessary(ident);
                }
                _context.decache(ident);
            }
        }
        
        // Identity assigned roles
        ops = new QueryOptions();
        ops.add(Filter.containsAll("assignedRoles", bundles));
        it = getIds(Identity.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Identity ident = lockIfNecessary(id);
            if (ident != null) {
                try {
                    ident.removeAssignedRole(b);
                    // Also remove related role assignments.
                    List<RoleAssignment> roleAssignments = ident.getRoleAssignments(b);
                    for (RoleAssignment ra : Util.iterate(roleAssignments)) {
                        ident.removeRoleAssignment(ra);
                    }
                    ident.updateAssignedRoleSummary();
                }
                finally {
                    unlockIfNecessary(ident);
                }
                _context.decache(ident);
            }
        }
        
        // RoleScorecard
        b.setScorecard(null);
        ops = new QueryOptions();
        ops.add(Filter.eq("role", b));
        it = getIds(RoleScorecard.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            RoleScorecard scorecard = _context.getObjectById(RoleScorecard.class, id);
            if (scorecard != null) {
                scorecard.setRole(null);
                deleteObject(scorecard);
            }
        }
                
        // RoleIndex
        b.setRoleIndex(null);
        ops = new QueryOptions();
        ops.add(Filter.eq("bundle", b));
        it = getIds(RoleIndex.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            RoleIndex index = _context.getObjectById(RoleIndex.class, id);
            if (index != null) {
                // just delete these, we don't need them for historical
                // analysis at the moment, if we did then we'd have to convert
                // the pointer to a name
                index.setBundle(null);
                deleteObject(index);
            }
        }

        // IntegrationConfig.synchronizedRoles
        ops = new QueryOptions();
        ops.add(Filter.containsAll("synchronizedRoles", bundles));
        it = getIds(IntegrationConfig.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            IntegrationConfig ic = _context.getObjectById(IntegrationConfig.class, id);
            if (ic != null) {
                ic.removeSynchronizedRole(b);
                // note that we have to save here to the parent/child list
                // dirty issue
                _context.saveObject(ic);
                _context.commitTransaction();
                _context.decache(ic);
            }
        }

        // IntegrationConfig.roleSyncContainer
        ops = new QueryOptions();
        ops.add(Filter.eq("roleSyncContainer", b));
        it = getIds(IntegrationConfig.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            IntegrationConfig ic = _context.getObjectById(IntegrationConfig.class, id);
            if (ic != null) {
                ic.setRoleSyncContainer(null);
                _context.saveObject(ic);
                _context.commitTransaction();
                _context.decache(ic);
            }
        }

        // SODConstraint
        // Here we could remove the constraint if both sides collapse
        // to null, but there is other potentially interesting info here
        // so let them linger.  Might want to leave some kind of marker behind.

        // sigh, could do this in one search with an or composite filter
        // but I'm tired and don't want to risk two OR'd containsAll
        // filters which I don't think we've tried with the HQL 
        // FilterVisitor yet..
        ops = new QueryOptions();
        ops.add(Filter.containsAll("leftBundles", bundles));
        it = getIds(SODConstraint.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            SODConstraint con = _context.getObjectById(SODConstraint.class, id);
            if (con != null) {
                con.removeLeft(b);
                // note that we have to save here to the parent/child list
                // dirty issue
                _context.saveObject(con);
                _context.commitTransaction();
                _context.decache(con);
            }
        }

        ops = new QueryOptions();
        ops.add(Filter.containsAll("rightBundles", bundles));
        it = getIds(SODConstraint.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            SODConstraint con = _context.getObjectById(SODConstraint.class, id);
            if (con != null) {
                con.removeRight(b);
                // note that we have to save here to the parent/child list
                // dirty issue
                _context.saveObject(con);
                _context.commitTransaction();
                _context.decache(con);
            }
        }

        // BundleArchives
        ops = new QueryOptions();
        ops.add(Filter.eq("sourceId", b.getId()));
        deleteObjects(BundleArchive.class, ops);
        
        // remove associated LocalizedAttributes
        visitDescribable(b);

        // TargetAssociations
        List<TargetAssociation> assocs = b.getAssociations();
        for (TargetAssociation assoc : Util.iterate(assocs)) {
            innerDelete(assoc);
        }
        
        innerDelete(b);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MitigationExpiration
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Should only get here if we're deleting a Bundle, which deletes
     * any associated MitigiationExpirations.
     */
    public void visitMitigationExpiration(MitigationExpiration exp)
        throws GeneralException {

        // Identity
        List<MitigationExpiration> expirations = new ArrayList<MitigationExpiration>();
        expirations.add(exp);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.containsAll("mitigationExpirations", expirations));
        Iterator<String> it = getIds(Identity.class, ops);
        while (it.hasNext()) {
            String id = it.next();
            Identity ident = lockIfNecessary(id);
            if (ident != null) {
                try {
                    ident.remove(exp);
                }
                finally {
                    unlockIfNecessary(ident);
                }
                _context.decache(ident);
            }
        }
        
        innerDelete(exp);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MonitoringStatistic
    //
    //////////////////////////////////////////////////////////////////////
    public void visitMonitoringStatistic(MonitoringStatistic stat)
        throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("monitoringStatistic.id", stat.getId()));
        _context.removeObjects(ServerStatistic.class, ops);

        innerDelete(stat);
        _context.commitTransaction();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Link
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitLink(Link link)
        throws GeneralException {

        visitLink(link, false);
    }
    
    /**
     * For logical application link removal during refresh.  Entitlizer takes
     * care of IdentityEntitlements, no reason to remove them here and lose
     * request and LCM information, sheesh.
     * @exclude
     *
     * @ignore
     * NOTE WELL: Unlike most visitors that touch Identity, Do NOT lock/unlock the
     * identity here, it is already locked by Aggregator/Identitizer and we have to keep it locked.  
     * In rare cases, this could be called from custom code or the console and we'll see the warning.
     */
    public void visitLink(Link link, boolean preserveIdentityEntitlements)
        throws GeneralException {

        if (log.isInfoEnabled())
            log.info("Deleting link " + link.toString());

        //Identity ident = link.getIdentity();
        // ^^^ that's too easy, let's do it the hard way instead
        // There's a bug in hibernate that makes accessing a parent via its child potentially
        // problematic.  PlanCompiler --> Terminator is one such code path.  So we have
        // to fetch the Identity indirectly of the Link
        //
        // (Bugs 10473 & 10604 for the long version)
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("links.id", link.getId()));
        options.setDistinct(true);
        Iterator<String> results = getIds(Identity.class, options);
        String identityId = null;
        
        while (results.hasNext()) {
            identityId = results.next();
            // in theory, there can be only one, but ensure we run through
            // the iterator entirely anyways
            while (results.hasNext()) {
                results.next();
            }
        }
        
        Identity ident = null;
        if (identityId != null) {
            ident = _context.getObjectById(Identity.class, identityId);
        }
        
        if (ident != null) {
            // This needs the identity ref, so do it up front
            // Identity from the link could be disassociated due to a hibernate issue
            if(!preserveIdentityEntitlements) {
                removeLinkIdentityEntitlements(link);
            }
            ident.remove(link);
            // make sure the parent gets marked dirty too, see comments
            // above visitProfile
            _context.saveObject(ident);
        }

        link.setIdentity(null);
        link.setApplication(null);
        removeLinkExternalAttributes(link);
        innerDelete(link);

        // don't keep this around either
        // NOTE: This assumes that something else
        // isn't still modifying this Identity, 
        // deleteIdentity won't because it deletes links
        // thorugh cascade
        if (!_noDecache && (ident != null))
            _context.decache(ident);
    }

    /**
     * Remove any multi-valued Account (Link) attributes
     * that are defined. These are not hard references
     * but we do not want to have orphans in the external
     * tables. 
     * 
     * @param link
     * @throws GeneralException
     */
    private void removeLinkExternalAttributes(Link link) 
        throws GeneralException {

        if ( link != null ) {
           // Remove any external link attributes
           QueryOptions ops = new QueryOptions();
           ops.add(Filter.eq("objectId", link.getId()));
           _context.removeObjects(LinkExternalAttribute.class, ops);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Identity Entitlement have an inverse relationship with Identity
    //
    //////////////////////////////////////////////////////////////////////
   
    /**
     * Remove any IdentityEntitlements that were promoted for the link
     * by aggregation or refresh. This method does a bulk delete where
     * all the objects are removed in a single call to the database.
     * 
     * @param link
     * 
     * @throws GeneralException
     */
    private void removeLinkIdentityEntitlements(Link link) throws GeneralException {
        if ( link != null ) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("application", link.getApplication()));            
            ops.add(Filter.ignoreCase(Filter.eq("nativeIdentity", link.getNativeIdentity())));
            ops.add(Filter.eq("identity", link.getIdentity()));            
            if ( link.getInstance() != null ) {
                // jsl - unlike Application.instance, this has a case insensitive index
                ops.add(Filter.ignoreCase(Filter.eq("instance", link.getInstance())));
            }            
            _context.removeObjects(IdentityEntitlement.class, ops);
            _context.commitTransaction();
        }        
    }
    
    /*
     * Remove the current identity from the list referenced by the specified property 
     * @param identity Identity to be removed from the referenced list
     * @param referenceProperty property containing a List of Identity objects that needs to be cleaned up
     * @param referenceClass Class of the SailPointObject containing reference(s) to the given Identity
     * @throws GeneralException Thrown when we fail to fetch objects containing references to the identity
     */
    @SuppressWarnings("unchecked")
    private void cleanUpIdentityReferencesInList(Identity identity, String referenceProperty, Class<? extends SailPointObject> referenceClass) throws GeneralException {
        List<Identity> identityToClean = Arrays.asList(new Identity[] { identity });
        QueryOptions ops = new QueryOptions(Filter.containsAll(referenceProperty, identityToClean));
        Iterator<String> it = getIds(referenceClass, ops);
        List<Identity> listToModify;
        while (it.hasNext()) {
            String id = it.next();
            SailPointObject obj = _context.getObjectById(referenceClass, id);
            if (obj != null) {
                Method referenceAccessor = Reflection.getAccessor(obj, referenceProperty);
                try {
                    listToModify = (List<Identity>) referenceAccessor.invoke(obj, (Object[]) null);
                    if (!Util.isEmpty(listToModify)) {
                        listToModify.remove(identity);
                    }
                    
                    // If nothing's left in the list get rid of it to clean up the XML representation
                    if (Util.isEmpty(listToModify)) {
                        Reflection.setProperty(obj, referenceProperty, List.class, null);
                    }
                    
                    _context.saveObject(obj);
                    _context.commitTransaction();
                    _context.decache(obj);
                } catch (Exception e) {
                    // If we're getting here there is a serious programming error that needs to be corrected prior to shipping.  Log this and abort
                    // the current change
                    log.error("Failed to use the accessor for the " + referenceProperty + " property on the " + referenceClass.getName() + " class.", e);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitCertification(Certification cert) 
        throws GeneralException {

        // Certificationer has historically done the work
        Certificationer c = new Certificationer(_context);
        c.deleteWithoutLock(cert);
    }
    
    /**
     * @exclude
     */
    @Override
    public void visitCertificationEntity(CertificationEntity entity)
        throws GeneralException {
    	
    	if (entity == null)
            return;

        CertificationAction action = entity.getAction();
        if (null != action) {
            action.setParentActions(null);
            action.setChildActions(null);
            action.setSourceAction(null);
            _context.saveObject(action);
        }
    	entity.setItems(null);
    	
        QueryOptions childOps = new QueryOptions(Filter.eq("parent.id", entity.getId()));
        this.deleteObjects(CertificationItem.class, childOps);        
        
        innerDelete(entity);
    }

    /**
     * @exclude
     */
    public void visitCertificationItem(CertificationItem item) 
        throws GeneralException {    
        
        if ( item != null ) {
            CertificationAction action = item.getAction();
            if (null != action) {
                action.setParentActions(null);
                action.setChildActions(null);
                action.setSourceAction(null);
                _context.saveObject(action);
            }
        
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.or(Filter.eq("certificationItem",item), 
                              Filter.eq("pendingCertificationItem", item)));
            ops.setCloneResults(true);
            
            Iterator<Object[]> rows = _context.search(IdentityEntitlement.class, ops,
                    Util.csvToList("id,certificationItem.id,pendingCertificationItem.id"));
            if ( rows != null ) {
                while ( rows.hasNext() ) {
                    Object[] row = rows.next();
                    String id = (String)row[0];
                    String certItemId = (String)row[1];
                    String pendingCertItemId = (String)row[2];                    
                    if ( id != null ) {
                        IdentityEntitlement ie = _context.getObjectById(IdentityEntitlement.class, id);
                        if ( ie != null ) {
                            String itemId = item.getId();
                            if ( Util.nullSafeCompareTo(itemId, certItemId) == 0 ) {
                                ie.setCertificationItem(null);
                            }
                            if ( Util.nullSafeCompareTo(itemId, pendingCertItemId) == 0 ) {
                                ie.setPendingCertificationItem(null);
                            }                            
                            _context.saveObject(ie);
                            _context.commitTransaction();
                        }
                    }
                }
            }
        }
        innerDelete(item);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // CertificationDefinition
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitCertificationDefinition(CertificationDefinition def) 
        throws GeneralException {

        // Should we null out any certifications that reference this (not
        // completely necessary since this isn't a hard reference)?  Go ahead
        // and do this since getObjectById() will explode with a non-existent
        // object.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("certificationDefinitionId", def.getId()));
        List<Certification> certs = _context.getObjects(Certification.class, qo);
        for (Certification cert : certs) {
            cert.setCertificationDefinitionId(null);
            _context.saveObject(cert);

            // Commit so that we can decache.
            _context.commitTransaction();
            _context.decache(cert);
        }
        
        // Delete any task schedules that reference this definition.  Use
        // innerDelete rather than visit to avoid infinite recursion.
        CertificationScheduler scheduler = new CertificationScheduler(_context);
        List<TaskSchedule> schedules = scheduler.findSchedules(def);
        for (TaskSchedule sched : schedules) {
            innerDelete(sched);
        }

        // Delete any triggers that reference this definition.  Use
        // innerDelete rather than visit to avoid infinite recursion.
        List<IdentityTrigger> triggers =
            _context.getObjects(IdentityTrigger.class);
        for (IdentityTrigger trigger : triggers) {
            CertificationDefinition currentDef =
                trigger.getCertificationDefinition(_context);
            if ((null != currentDef) && def.equals(currentDef)) {
                innerDelete(trigger);
            }
        }

        QueryOptions ops = new QueryOptions(Filter.eq("definition", def));
        List<CertificationGroup> groups = _context.getObjects(CertificationGroup.class, ops);
        if (groups != null){
            for(CertificationGroup group : groups){
                group.setDefinition(null);
                _context.saveObject(group);
                _context.commitTransaction();
            }
        }

        // Now, delete the definition.
        innerDelete(def);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // GroupIndex
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitGroupIndex(GroupIndex index)
        throws GeneralException {

        // definition points to the most recent index
        GroupDefinition def = index.getDefinition();
        if (def != null && def.getIndex() == index)
            def.setIndex(null);

        index.setDefinition(null);

        innerDelete(index);

        if (def != null)
            _context.decache(def);

    }

    //////////////////////////////////////////////////////////////////////
    //
    // GroupDefinition
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * We will consider the definition to own the indexes and imply 
     * a cascade delete.   If the indexes were left with a null definition
     * they would become invisible garbage since they won't show up in the UI
     * and won't deleted on the next group refresh.
     */
    public void visitGroupDefinition(GroupDefinition def)
        throws GeneralException {


        // before we start deleting indexes, null out our
        // reference to the most recent index
        def.setIndex(null);
        _context.saveObject(def);
        _context.commitTransaction();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("definition", def));
        // this one is harder than most since there can be a LOT of indexes
        Iterator<String> rows = getIds(GroupIndex.class, ops);
        if ( rows != null ) {
            while ( rows.hasNext() ) {
                String id = rows.next();
                GroupIndex idx = _context.getObjectById(GroupIndex.class, id);
                if (idx != null) {
                    idx.setDefinition(null);
                    // don't bother calling deleteObject(), the only
                    // dependency visitGroupIndex has to prune is the one
                    // pointing from the GroupDefinition to the latest
                    // GroupIndex and we're about to delete that anyway
                    traceDelete(idx);
                    innerDelete(idx);
                }
            }
        }

        innerDelete(def);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // GroupFactory
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * We will consider all GroupDefinitions created by this factory to 
     * be owned and will delete them.  We could just null the backref
     * to the factory and leave them behind but I don't think that's
     * normally what you want.  If you really wanted a GroupFactory
     * to bootstrap a set of standalone GroupDefinitions then null
     * out the backref after generation.
     */
    public void visitGroupFactory(GroupFactory factory)
        throws GeneralException {

        // locate all definitions built from this factory
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("factory", factory));
        // this one is harder than most since there can be a LOT of groups
        Iterator<String> rows = getIds(GroupDefinition.class, ops);
        if ( rows != null ) {
            while ( rows.hasNext() ) {
                String id = rows.next();
                GroupDefinition def = _context.getObjectById(GroupDefinition.class, id);
                if (def != null) {
                    def.setFactory(null);
                    // have to call deleteObject() so we can unwind
                    // the GroupIndexes too
                    deleteObject(def);
                }
            }
        }

        innerDelete(factory);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Request
    //
    //////////////////////////////////////////////////////////////////////
    public void visitRequest(Request req) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("request.id", req.getId()));
        _context.removeObjects(RequestState.class, ops);

        innerDelete(req);

    }

    //////////////////////////////////////////////////////////////////////
    //
    // RoleIndex
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitRoleIndex(RoleIndex roleIndex) throws GeneralException {

        Bundle bundle = roleIndex.getBundle();
        if (null != bundle) {
            bundle.setRoleIndex(null);
            _context.saveObject(bundle);
            _context.commitTransaction();
        }

        innerDelete(roleIndex);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RoleMetadata
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * @ignore
     * jsl - who uses Terminator for RoleMetadata?
     * Leaving this without Identity locking since I doubt it is used and if it is
     * the caller should be responsible for locking.  I'm worried about putting in a lock/unlock
     * here since we don't know the context of the call.
     */
    public void visitRoleMetadata(RoleMetadata roleMetadata) throws GeneralException {

        // We need to query for all Identities that have a many-to-many relationship to this
        // RoleMetadata and remove the metadata from the identity.
        QueryOptions ops = new QueryOptions();
        List<RoleMetadata> rmList = Arrays.asList(roleMetadata);
        ops.add(Filter.containsAll("roleMetadatas", rmList));

        // There should be only one of these.
        List<Identity> identities = _context.getObjects(Identity.class, ops);
        if (identities != null && !identities.isEmpty()) {
            if (identities.size() > 1) {
                if (log.isDebugEnabled()) {
                    log.debug("Terminator found more than one identity for a RoleMetadata instance. Continuing RoleMetadata delete for all " + identities.size() + " identities.");
                }
            }

            for (Identity identity : identities) {
                // Remove the metadata from the identity.
                List<RoleMetadata> metadatas = identity.getRoleMetadatas();
                if (metadatas != null) {
                    metadatas.remove(roleMetadata);
                }
                _context.saveObject(identity);
            }

            _context.commitTransaction();
        }

        innerDelete(roleMetadata);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rule
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitRule(Rule rule) throws GeneralException {

        // Remove this rule from any rules that reference it.
        QueryOptions qo = new QueryOptions();
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(rule);
        qo.add(Filter.containsAll("referencedRules", rules));
        List<Rule> referencers = _context.getObjects(Rule.class, qo);

        for (Rule referencer : referencers) {
            referencer.getReferencedRules().remove(rule);
            _context.saveObject(referencer);
        }

        // ...and workflows
        qo = new QueryOptions();
        qo.add(Filter.containsAll("ruleLibraries", rules));
        List<Workflow> workflows = _context.getObjects(Workflow.class, qo);
        for (Workflow workflow : workflows) {
            workflow.getRuleLibraries().remove(rule);
            _context.saveObject(workflow);
        }

        // ActivityDataSource references.
        qo = new QueryOptions();
        qo.add(Filter.eq("correlationRule", rule));
        List<ActivityDataSource> dataSources =
            _context.getObjects(ActivityDataSource.class, qo);
        for (ActivityDataSource ds : dataSources) {
            ds.setCorrelationRule(null);
            _context.saveObject(ds);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("transformationRule", rule));
        dataSources = _context.getObjects(ActivityDataSource.class, qo);
        for (ActivityDataSource ds : dataSources) {
            ds.setTransformationRule(null);
            _context.saveObject(ds);
        }

        // Application references.
        qo = new QueryOptions();
        qo.add(Filter.eq("correlationRule", rule));
        List<Application> apps = _context.getObjects(Application.class, qo);
        for (Application app : apps) {
            app.setCorrelationRule(null);
            _context.saveObject(app);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("creationRule", rule));
        apps = _context.getObjects(Application.class, qo);
        for (Application app : apps) {
            app.setCreationRule(null);
            _context.saveObject(app);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("managerCorrelationRule", rule));
        apps = _context.getObjects(Application.class, qo);
        for (Application app : apps) {
            app.setManagerCorrelationRule(null);
            _context.saveObject(app);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("customizationRule", rule));
        apps = _context.getObjects(Application.class, qo);
        for (Application app : apps) {
            app.setCustomizationRule(null);
            _context.saveObject(app);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("managedAttributeCustomizationRule", rule));
        apps = _context.getObjects(Application.class, qo);
        for (Application app : apps) {
            app.setManagedAttributeCustomizationRule(null);
            _context.saveObject(app);
        }

        //Application Schemas
        qo = new QueryOptions();
        qo.add(Filter.eq("creationRule", rule));
        List<Schema> schemas = _context.getObjects(Schema.class, qo);
        for (Schema sch : Util.safeIterable(schemas)) {
            sch.setCreationRule(null);
            _context.saveObject(sch);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("customizationRule", rule));
        schemas = _context.getObjects(Schema.class, qo);
        for (Schema sch : Util.safeIterable(schemas)) {
            sch.setCustomizationRule(null);
            _context.saveObject(sch);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("correlationRule", rule));
        schemas = _context.getObjects(Schema.class, qo);
        for (Schema sch : Util.safeIterable(schemas)) {
            sch.setCorrelationRule(null);
            _context.saveObject(sch);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("refreshRule", rule));
        schemas = _context.getObjects(Schema.class, qo);
        for (Schema sch : Util.safeIterable(schemas)) {
            sch.setRefreshRule(null);
            _context.saveObject(sch);
        }

        // Bundle references.
        qo = new QueryOptions();
        qo.add(Filter.eq("joinRule", rule));
        List<Bundle> bundles = _context.getObjects(Bundle.class, qo);
        for (Bundle bundle : bundles) {
            bundle.setJoinRule(null);
            _context.saveObject(bundle);
        }

        // Target source references.
        qo = new QueryOptions();
        qo.add(Filter.eq("correlationRule", rule));
        List<TargetSource> sources = _context.getObjects(TargetSource.class, qo);
        for (TargetSource source : sources) {
            source.setCorrelationRule(null);
            _context.saveObject(source);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("creationRule", rule));
        sources = _context.getObjects(TargetSource.class, qo);
        for (TargetSource source : sources) {
            source.setCreationRule(null);
            _context.saveObject(source);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("transformationRule", rule));
        sources = _context.getObjects(TargetSource.class, qo);
        for (TargetSource source : sources) {
            source.setTransformationRule(null);
            _context.saveObject(source);
        }

        // WorkItemConfig references.
        qo = new QueryOptions();
        qo.add(Filter.eq("ownerRule", rule));
        List<WorkItemConfig> configs = _context.getObjects(WorkItemConfig.class, qo);
        for (WorkItemConfig config : configs) {
            config.setOwnerRule(null);
            _context.saveObject(config);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("escalationRule", rule));
        configs = _context.getObjects(WorkItemConfig.class, qo);
        for (WorkItemConfig config : configs) {
            config.setEscalationRule(null);
            _context.saveObject(config);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("planInitializer", rule));
        List<IntegrationConfig> intconfigs = _context.getObjects(IntegrationConfig.class, qo);
        for (IntegrationConfig obj : intconfigs) {
            obj.setPlanInitializer(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("rule", rule));
        List<TaskEvent> events = _context.getObjects(TaskEvent.class, qo);
        for (TaskEvent obj : events) {
            obj.setRule(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("managedAttributeRequestControl", rule));
        List<DynamicScope> scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setManagedAttributeRequestControl(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("roleRequestControl", rule));
        scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setRoleRequestControl(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("applicationRequestControl", rule));
        scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setApplicationRequestControl(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("managedAttributeRemoveControl", rule));
        scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setManagedAttributeRemoveControl(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("roleRemoveControl", rule));
        scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setRoleRemoveControl(null);
            _context.saveObject(obj);
        }

        qo = new QueryOptions();
        qo.add(Filter.eq("applicationRemoveControl", rule));
        scopes = _context.getObjects(DynamicScope.class, qo);
        for (DynamicScope obj : scopes) {
            obj.setApplicationRemoveControl(null);
            _context.saveObject(obj);
        }

        // If the rule is an attachmentConfig rule remove references from system config
        if (rule.getType() == Rule.Type.AttachmentConfig) {
            // Remove any references contained in the system config attachmentConfigRules setting
            Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            if (config != null && config.containsAttribute(Configuration.ATTACHMENT_CONFIG_RULES)) {
                List<String> selectedAttachmentConfigRules = config.getList(Configuration.ATTACHMENT_CONFIG_RULES);
                List<String> modifiedAttachmentConfigRules = new ArrayList<>();

                for (String ruleName : Util.safeIterable(selectedAttachmentConfigRules)) {
                    // if the rule name is not the same as the one getting deleted add it to the list
                    if (!ruleName.equals(rule.getName())) {
                        modifiedAttachmentConfigRules.add(ruleName);
                    }
                }

                config.put(Configuration.ATTACHMENT_CONFIG_RULES, modifiedAttachmentConfigRules);
            }
        }

        innerDelete(rule);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scope
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitScope(Scope scope) throws GeneralException {

        // Just clear the references and delete children.
        ScopeService.DeletionOptions ops =
            new ScopeService.DeletionOptions(null, null, true);
        new ScopeService(_context).deleteScope(scope, ops);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Scorecard
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitScorecard(Scorecard card)
        throws GeneralException {

        Identity ident = card.getIdentity();
        if (ident != null) {
            Scorecard current = ident.getScorecard();
            if (current != null && card.equals(current))
                ident.setScorecard(null);
        }

        card.setIdentity(null);
        innerDelete(card);

        if (ident != null) {
            // !!! , we may be called recursively 
            // when deleting the Identity, need to fix this
            // and have more context to make these decisions
            //_context.decache(def);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Server
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitServer(Server server)
            throws GeneralException {
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("host.id", server.getId()));
        _context.removeObjects(ServerStatistic.class, ops);

        innerDelete(server);
        _context.commitTransaction();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // TaskSchedule
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitTaskSchedule(TaskSchedule sched) throws GeneralException {
        boolean deleted = false;
        CertificationScheduler scheduler = new CertificationScheduler(_context);
        if (scheduler.isCertificationSchedule(sched)) {
            
            // Visit the certification definition.  This will delete the
            // definition AND this schedule.
            CertificationDefinition def = null;
            
            try {
                def =
                    scheduler.getCertificationDefinition(sched);
            }
            catch (GeneralException e) {
                // Old schedules that weren't upgraded (from unit tests) can
                // cause this.  Just ignore and only delete the schedule.
                if (log.isDebugEnabled())
                    log.debug("Task schedule being deleted does not have certification definition: " + sched);
            }
            catch (IllegalArgumentException e) {
                // For demo data and some tasks creating certs, the schedule may not have 
                // the cert id, and this exception is thrown. Just ignore and only delete the schedule
                if (log.isDebugEnabled())
                    log.debug("Task schedule being deleted does not have certification definition: " + sched);
            }
            
            if (null != def) {
                // Check that this definition is not referenced by an existing CertificationGroup
                // or Certification before deleting it. 
                if ((_context.countObjects(CertificationGroup.class, 
                        new QueryOptions(Filter.eq("definition", def))) == 0) &&
                        (_context.countObjects(CertificationGroup.class, 
                                new QueryOptions(Filter.eq("definition", def))) == 0)) {
                    visit(def);
                    deleted = true;
                }
            }
        }

        // If we didn't already delete this, do the deed.
        if (!deleted) {
            innerDelete(sched);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // UIPreferences
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitUIPreferences(UIPreferences prefs)
        throws GeneralException {

        Identity ident = prefs.getOwner();
        if (ident != null) {
            UIPreferences current = ident.getUIPreferences();
            if (current != null && prefs.equals(current))
                ident.setUIPreferences(null);
            prefs.setOwner(null);
        }

        innerDelete(prefs);

        if (ident != null) {
            // !!! , we may be called recursively 
            // when deleting the Identity, need to fix this
            // and have more context to make these decisions
            //_context.decache(def);
        }

    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // WorkItem
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Approval work items will have references to them from the
     * object being approved.  In practice we only have to worry
     * about Bundle and Profile right now so just search in those tables.
     * We could be smarter here and look at work item attributes
     * to determine exactly which object should be pointing to us,
     * but ther's still the case where the database got corrupted
     * and we have to do it the hard way.
     */
    public void visitWorkItem(WorkItem item)
        throws GeneralException {

        // WorkflowCase, really you should be doing this the other way
        // around, deleting the case.
        WorkflowCase wfcase = item.getWorkflowCase();
        if (wfcase != null) {
            // case as a soft reference, we could clean those up too
            // but it is less important, Workflower can deal
            // with invalid item ids
            item.setWorkflowCase(null);
        }

        _context.commitTransaction();
        innerDelete(item);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Tag
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Remove any references to the given tag.
     */
    public void visitTag(Tag tag) throws GeneralException {
        
        // Remove references from CertificationDefinitions.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("tags.id", tag.getId()));
        qo.setDistinct(true);
        List<CertificationDefinition> defs =
            _context.getObjects(CertificationDefinition.class, qo);
        for (CertificationDefinition def : defs) {
            def.getTags().remove(tag);
            _context.saveObject(def);
        }

        // Remove references from Certifications.
        List<Certification> certs = _context.getObjects(Certification.class, qo);
        for (Certification cert : certs) {
            cert.getTags().remove(tag);
            _context.saveObject(cert);
        }

        _context.commitTransaction();
        innerDelete(tag);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // TaskResult
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitTaskResult(TaskResult result) 
        throws GeneralException {
        
        // THINK: There can be a soft reference to a TaskResult
        // from a WorklfowCase, but this isn't in a searchable form.
        // It seems reasonable to let the reference become stale and
        // have Workflower clean it up.

        JasperResult report = result.getReport();
        result.setReport(null);

        QueryOptions ops = new QueryOptions(Filter.eq("taskResult", result));
        List<TaskEvent> events = _context.getObjects(TaskEvent.class, ops);
        if (events != null) {
            for (TaskEvent event : events) {
                deleteObject(event);
            }
        }

        // in 6.2 TaskResults can be shared by multiple Requests
        ops = new QueryOptions(Filter.eq("taskResult", result));
        List<Request> requests = _context.getObjects(Request.class, ops);
        if (requests != null) {
            for (Request request : requests) {
                deleteObject(request);
            }
        }

        innerDelete(result);
        _context.commitTransaction();
        if ( report != null) { 
            visitJasperResult(report);
        }
    }

    /**
     * @exclude
     */
    public void visitJasperResult(JasperResult result)
        throws GeneralException {

        String handlerId = result.getHandlerId();
        if ( handlerId != null ) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("handlerId", handlerId));
            // this is configured to use the BulkDeletePersistenceManager
            _context.removeObjects(JasperPageBucket.class, ops);
        }

        if (result.getFiles() != null){
            List<PersistedFile> files = result.getFiles();
            result.setFiles(null);
            _context.saveObject(result);

            for(PersistedFile file : files){
                deleteObject(file);
            }
        }

        innerDelete(result);
        _context.commitTransaction();
    }

    /**
     * @exclude
     */
    public void visitPersistedFile(PersistedFile file)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent", file));
        // this is configured to use the BulkDeletePersistenceManager
        _context.removeObjects(FileBucket.class, ops);
        _context.commitTransaction();  // the FK will cause contention if we dont commit here
        innerDelete(file);
        _context.commitTransaction();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workflow
    //
    //////////////////////////////////////////////////////////////////////
    @Override
    public void visitWorkflow(Workflow workflow)
            throws GeneralException {
        // remove any MFA configuration that refers to this
        // workflow.  Only need to look at workflows that are
        // the multi-factor auth type.
        if(Workflow.TYPE_MULTI_FACTOR_AUTHENTICATION.equals(workflow.getType())) {
            Configuration mfaConfiguration = _context.getObjectByName(Configuration.class, Configuration.MFA);
            if(mfaConfiguration != null) {
                // keep a list of things to remove to avoid concurrent modification exceptions
                List<MFAConfig> mfaConfigsToRemove = new ArrayList<MFAConfig>();
                List<MFAConfig> mfaConfigList = mfaConfiguration.getList(Configuration.MFA_CONFIG_LIST);
                for(MFAConfig config : Util.safeIterable(mfaConfigList)) {
                    if(config.getWorkflow().equals(workflow)) {
                        mfaConfigsToRemove.add(config);
                    }
                }

                // if there is anything to remove, do so and resave the config object
                if(mfaConfigsToRemove.size() > 0) {
                    mfaConfigList.removeAll(mfaConfigsToRemove);
                    mfaConfiguration.put(Configuration.MFA_CONFIG_LIST, mfaConfigList);
                    _context.saveObject(mfaConfiguration);
                }
            }
        }

        innerDelete(workflow);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkflowCase
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * These are soft references so they're not required but it's
     * what you want when deleting cases from the console.
     * Note that this is a very serious thing to be doing,
     * cases often have valuable state in them and if they
     * are partially complete could leave external processes and
     * data in an inconsistent state.
     */
    public void visitWorkflowCase(WorkflowCase wfcase)
        throws GeneralException {

        // hmm, arguably this should be encapsulated in Workflower
        // but I like to keep all the pruning logic in one place
        // Workflower will null this out if it wants to preserve it
        TaskResult result = null;
        String resultId = wfcase.getTaskResultId();
        if (resultId != null) {
            result = _context.getObjectById(TaskResult.class, resultId);
            if (result != null) {
                // there is a visitor for TaskResult but workflow results
                // won't have those dependencies
                traceDelete(result);
                innerDelete(result);
            }
        }
        
        // Similar issues for the impact analysis results, this
        // one is more debatable since impact analysis is not part
        // of the core workflow model.  Would be better to give the
        // WorkflowCase a list of TaskResult ids that can be maintained
        // by the WorkflowHandler.
        resultId = (String)wfcase.get(RoleLibrary.VAR_IMPACT_ANALYSIS_RESULT);
        if (resultId != null) {
            result = _context.getObjectById(TaskResult.class, resultId);
            if (result != null) {
                // !! ignore if not completed?
                traceDelete(result);
                innerDelete(result);
            }
        }

        // delete all WorkItems opened for this case
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("workflowCase", wfcase));
        // here we're assuming we're dealing with relatively few objects
        // and can fetch them all at once
        List<WorkItem> items = _context.getObjects(WorkItem.class, ops);
        if (items != null) {
            for (WorkItem item : items) {
                item.setOwner(null);
                item.setWorkflowCase(null);
                deleteObject(item);
            }
        }

        // ugh, in theory anything can have a workflow attached,
        // just get the major ones for now

        // Bundle._workflow
        ops = new QueryOptions();
        ops.add(Filter.eq("pendingWorkflow", wfcase));
        List<Bundle> bundles = _context.getObjects(Bundle.class, ops);
        if (bundles != null) {
            for (int i = 0 ; i < bundles.size() ; i++) {
                Bundle b = bundles.get(i);
                b.setPendingWorkflow(null);
            }
        }

        innerDelete(wfcase);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyViolation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Try to be smart about work items assigned to this violation.
     * Work items assigned through delegation on the policy violation pages
     * will have a "targetId" set to the PolicyViolation database id.
     * Since ids are unique accross classes we can use that for searching.
     * Policy workflows will have the violation stored inside the workflow case.
     * 
     * We're not going to handle the later case as there isn't a nice
     * searchable thing for workflow cases.  Even if we had one, rather than
     * just terminate the worklfow it would be better to allow jumping
     * to a handler step.
     */
    public void visitPolicyViolation(PolicyViolation pv) 
        throws GeneralException {

        deleteTargetedWorkItems(pv.getId());
        pv.setIdentity(null);
        innerDelete(pv);
    }
    
    /**
     * @exclude
     */
    public void visitPolicy(Policy p) 
        throws GeneralException {
        
        // remove associated LocalizedAttributes
        visitDescribable(p);
        
        innerDelete(p);
    }

    /**
     * @exclude
     * Delete any work items that have a given target id.
     */
    public void deleteTargetedWorkItems(String targetId) 
        throws GeneralException {

        if (targetId != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("targetId", targetId));
            List<WorkItem> items = _context.getObjects(WorkItem.class, ops);
            if (items != null) {
                for (int i = 0 ; i < items.size() ; i++) {
                    WorkItem item = items.get(i);
                    deleteObject(item);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Account Group
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Delete one account group
     *
     * The only thing special here is to make sure we clean up 
     * any references in other account groups via the inheritance
     * model.
     * 
     */
    public void visitAccountGroup(AccountGroup group) 
        throws GeneralException {

        if ( group != null ) {
            group.setInheritance(null);
            // Cleanup any inheritance references to this account group 
            List<AccountGroup> vals = new ArrayList<AccountGroup>();
            vals.add(group);
            Filter filter = Filter.containsAll("inheritance", vals);
            QueryOptions qo = new QueryOptions();
            qo.add(filter);
            Iterator<String> groupIds = getIds(AccountGroup.class, qo);
            while ( groupIds.hasNext() ) {
                String gid = groupIds.next();
                AccountGroup child = _context.getObjectById(AccountGroup.class,gid);
                if ( child != null ) {
                    List<AccountGroup> inheritance = child.getInheritance();
                    if ( Util.size(inheritance) > 0  ) {
                        inheritance.remove(group);
                        _context.saveObject(child);
                        _context.commitTransaction();
                        _context.decache(child);
                    }
                }
            }
            innerDelete(group);
        }
    }

    /**
     * @exclude
     * In 6.0 ManagedAttributes are much like AccountGroups.
     * Keep both visitors though in case the customer choses
     * not to delete their AccountGroups during upgrade
     */
    public void visitManagedAttribute(ManagedAttribute ma)
        throws GeneralException {

        if ( ma != null ) {
            //Can't set inheritence(null), or hibernate can throw lazy initialization exception
            //Do we need to clear, or just let it go away with cascade? -rap
//            ma.getInheritance().clear();
//            ma.setInheritance(null);
            // Cleanup any inheritance references to this attribute
            List<ManagedAttribute> vals = new ArrayList<ManagedAttribute>();
            vals.add(ma);
            Filter filter = Filter.containsAll("inheritance", vals);
            QueryOptions qo = new QueryOptions();
            qo.add(filter);
            Iterator<String> attIds = getIds(ManagedAttribute.class, qo);
            while ( attIds.hasNext() ) {
                String gid = attIds.next();
                ManagedAttribute child = _context.getObjectById(ManagedAttribute.class,gid);
                if ( child != null ) {
                    List<ManagedAttribute> inheritance = child.getInheritance();
                    if ( Util.size(inheritance) > 0  ) {
                        inheritance.remove(ma);
                        _context.saveObject(child);
                        _context.commitTransaction();
                        _context.decache(child);
                    }
                }
            }

            // TargetAssociations
            List<TargetAssociation> assocs = ma.getAssociations();
            for (TargetAssociation assoc : Util.iterate(assocs)) {
                innerDelete(assoc);
            }

            innerDelete(ma);

            
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Targets - Unstructured scan reminents
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Delete one target
     *
     * The only thing special here is to make sure we clean up 
     * TargetAssociation objects.
     */
    public void visitTarget(Target target) 
        throws GeneralException {

        if ( target != null ) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("target",target));
            _context.removeObjects(TargetAssociation.class, qo);
            _context.commitTransaction();
            innerDelete(target);
        }
    }
    
    /**
     * @exclude
     * Deletion of TaskDefinition will delete all referenced TaskResults and TaskSchedules
     * 
     */
    public void visitTaskDefinition(TaskDefinition taskDef) throws GeneralException {
        
        if (taskDef != null) {
            // First get rid of the TaskResults that reference this TaskDef
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("definition", taskDef));

            List<TaskResult> objs = _context.getObjects(TaskResult.class, qo);
            if (objs != null) {
                for (TaskResult result : objs) {
                    visitTaskResult(result);
                }
            }
            
            List<TaskSchedule> schedules = _context.getObjects(TaskSchedule.class, qo);
            if (schedules != null) {
                for (TaskSchedule result : schedules) {
                    visitTaskSchedule(result);
                }
            }
            
            innerDelete(taskDef);
        } // end if
        
    }

    /**
     * @exclude
     * Query and remove any references to the CorrelationConfig
     * before we remove it.  These are references by Applications
     * in the accountCorrealtionConfig attribute.
     */
    public void visitCorrelationConfig(CorrelationConfig config) 
        throws GeneralException {

        if (config != null) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("accountCorrelationConfig", config));
            Iterator<String> rows = getIds(Application.class, qo);
            if ( rows != null ) {
                while ( rows.hasNext() ) {
                    String id = rows.next();
                    if ( id != null ) {
                        Application app = _context.getObjectById(Application.class, id);
                        if ( app != null ) {
                            app.setAccountCorrelationConfig(null);
                        }
                    }
                }
            }
            innerDelete(config);
        }
    }
    
    /**
     * @exclude
     * Identity Entitlements can contain refernces to IdentityRequestItem
     * objects so clean those up before we remove the IdentityRequest.
     * 
     * TODO: Is there a way to just update to null? Maybe using update HQL.
     * 
     * @param ir
     * @throws GeneralException
     */
    public void visitIdentityRequest(IdentityRequest ir) 
        throws GeneralException {
        
        if ( ir != null ) {
            deleteOrphanedWorkItems(ir);
            nullifyRequestItemOrPendingRequestItem(ir, "requestItem");
            nullifyRequestItemOrPendingRequestItem(ir, "pendingRequestItem");

            innerDelete(ir);
        }
    }

    /*
     * IIQETN-4258 When deleting an IdentityRequest we want to visit the approval WorkItem objects
     * so that we don't leave orphans that might clutter access requests down the line.
     */
    private void deleteOrphanedWorkItems(IdentityRequest request) throws GeneralException {
        QueryOptions qo = new QueryOptions(Filter.eq("identityRequestId", request.getName()));
        List<WorkItem> workItems = _context.getObjects(WorkItem.class, qo);

        if(!Util.isEmpty(workItems)) {
            Iterator<WorkItem> workItemIter = workItems.listIterator();
            while (workItemIter.hasNext()) {
                WorkItem item = workItemIter.next();
                if (null != item) {
                    workItemIter.remove();
                    visitWorkItem(item);
                }
            }
        }
    }

    private void nullifyRequestItemOrPendingRequestItem(IdentityRequest request, String propertyToNullify) 
        throws GeneralException {

        QueryOptions qo = new QueryOptions(Filter.eq(propertyToNullify + ".identityRequest", request));
        qo.setCloneResults(true);
        Iterator<Object[]> rows = _context.search(IdentityEntitlement.class, qo, "id, " + propertyToNullify + ".id");
        if ( rows != null ) {
            RequestItemNullifier nullifier = new RequestItemNullifier(propertyToNullify); 
            while ( rows.hasNext() ) {
                Object[] row = rows.next();
                String id = (String)row[0];
                String requestItemId = (String) row[1];
                if ( !Util.isNullOrEmpty(id)) {
                    nullifier.addIdentityEntitlement(id, requestItemId);
                }
            }

            // Flush any remaining IdentityEntitlements from the nullifier
            nullifier.updateCurrentIdentityEntitlements();
        }
    }

    /**
     * @exclude
     * Clear out any references to this item on IdentityEntitlements.
     * 
     * @param item
     * @throws GeneralException
     */
    public void visitIdentityRequestItem(IdentityRequestItem item) 
        throws GeneralException {
    
        if ( item != null ) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.or(Filter.eq("requestItem", item),
                   Filter.eq("pendingRequestItem", item)));   
            
            Iterator<String> rows = getIds(IdentityEntitlement.class, qo);
            if ( rows != null ) {
                while ( rows.hasNext() ) {
                    String id = rows.next();
                    if ( id != null ) {
                        IdentityEntitlement ie = _context.getObjectById(IdentityEntitlement.class, id);
                        if ( ie != null ) {
                            ie.setRequestItem(null);
                            ie.setPendingRequestItem(null);
                            _context.saveObject(ie);
                            _context.commitTransaction();
                        }
                    }
                }
            }
            innerDelete(item);
        }
    }

    /**
     * This class nullifies request items on IdentityEntitlements.
     * The inspiration for this was taken from sailpoint.api.Aggregator.LinkUpdateMangaer.
     * We should consider generalizing this
     */
    private class RequestItemNullifier {
        // how many to update together
        private static final int CommitBlock = 1000;
        private static final int RemovalBlock = 100;

        // The query that will be used to nullify the specified property
        private String nullifyQuery;

        // The Set of IDs corresponding to the IdentityEntitlements whose property is being nullified
        private Set<String> identityEntitlementIds = new HashSet<String>();

        // The list of IDs corresponding to the IdentityRequestItems that need to be removed
        private Set<String> identityRequestItemIds = new HashSet<String>();

        /**
         * Constructor for the RequestItemNullifier
         * @param propertyToNullify Property that is going to be nullified by this class
         * @throws GeneralException
         */
        public RequestItemNullifier(String propertyToNullify) throws GeneralException {
            if (Util.isNullOrEmpty(propertyToNullify)) {
                throw new GeneralException("The RequestItemNullifier needs a property to nullify");
            }

            if ("requestItem".equals(propertyToNullify) || "pendingRequestItem".equals(propertyToNullify)) {
                nullifyQuery = "update IdentityEntitlement set " + propertyToNullify + " = null where id = :id";
            } else {
                throw new GeneralException("The RequestItemNullifier can only nullify requestItems and pendingRequestItems.  It cannot nulllify " + propertyToNullify);
            }
        }

        /**
         * Flags an IdentityEntitlement as well as its associated request item for removal
         * @param identityEntitlementId ID of the IdentityEntitlement being removed
         * @param requestItemId ID of the associated IdentityRequestItem that needs to be removed
         * @throws GeneralException
         */
        public void addIdentityEntitlement(String identityEntitlementId, String requestItemId) 
            throws GeneralException {
            this.identityEntitlementIds.add(identityEntitlementId);
            this.identityRequestItemIds.add(requestItemId);
            if (this.identityEntitlementIds.size() == CommitBlock) {
                updateCurrentIdentityEntitlements();
            }
        }

        /**
         * Once a commit block is reached or the nullifier is finishing its work, this method
         * persists pending changes on the nullifier to the database
         * @throws GeneralException
         */
        public void updateCurrentIdentityEntitlements() 
            throws GeneralException {
            if (!Util.isEmpty(identityEntitlementIds)) {
                Map<String, Object> args = new HashMap<String, Object>();
                for (String identityEntitlementId : identityEntitlementIds) {
                    args.put("id", identityEntitlementId);
                    _context.update(nullifyQuery, args);
                }
                _context.commitTransaction();
                identityEntitlementIds.clear();
            }

            if (!Util.isEmpty(identityRequestItemIds)) {
                List<String> currentRemovals = new ArrayList<String>();
                for (String identityRequestItemId : identityRequestItemIds) {
                    currentRemovals.add(identityRequestItemId);
                    if (currentRemovals.size() == RemovalBlock) {
                        removeRequestItems(currentRemovals);
                    }
                }
                removeRequestItems(currentRemovals);
                identityRequestItemIds.clear();
                _context.commitTransaction();
            }
        }

        private void removeRequestItems(List<String> currentRemovals) throws GeneralException {
            if (!Util.isEmpty(currentRemovals)) {
                deleteObjects(IdentityRequestItem.class, new QueryOptions(Filter.in("id", currentRemovals)));
                currentRemovals.clear();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PasswordPolicy
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * @exclude
     * Delete any PasswordPolicyHolder objects along with the PasswordPolicy
     */
    public void visitPasswordPolicy(PasswordPolicy pp) 
    throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("policy", pp));
        List<PasswordPolicyHolder> pphList = _context.getObjects(PasswordPolicyHolder.class, qo);
        if (pphList != null) {
            for (PasswordPolicyHolder pph : pphList) {
                deleteObject(pph);
            }
        }
        
        innerDelete(pp);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // BatchRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    public void visitBatchRequest(BatchRequest br) throws GeneralException {
    	if (br == null) {
    		return;
    	}
    	
    	// Delete all batch request items as well
    	QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("batchRequest", br));
        List<BatchRequestItem> list = _context.getObjects(BatchRequestItem.class, qo);
        if (list != null) {
            for (BatchRequestItem bri : list) {
            	innerDelete(bri);
            }
        }
    	
    	innerDelete(br);
    }
    
    /**
     * @exclude
     */
    public void visitBatchRequestItem(BatchRequestItem br) throws GeneralException {
    	if (br == null) {
    		return;
    	}
    	
    	innerDelete(br);
    }
    
    /**
     * @exclude
     */
    public void visitTargetSource(TargetSource source) throws GeneralException {
        if ( source == null )
            return;
        
        List<TargetSource> sources = new ArrayList<TargetSource>();
        sources.add(source);
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("targetSources.id", source.getId() ));
        
        Iterator<String> objs = getIds(Application.class, ops);
        if ( objs != null ){
            while ( objs.hasNext() ){
                // For each application remove the source from the list
                String id = objs.next();
                Application app = _context.getObjectById(Application.class, id);
                if ( app != null ) {
                    app.remove(source);
                }
            }
        }
        
        ops = new QueryOptions();
        ops.add(Filter.eq("targetSource", source));
        Iterator<String> targets = getIds(Target.class, ops);
        if ( targets != null ){
            while ( targets.hasNext() ){
                // For each application remove the source from the list 
                String id = targets.next();
                Target target = _context.getObjectById(Target.class, id);
                if ( target != null ) {
                    visitTarget(target);
                }
            }
        }
        
        
        innerDelete(source);
    }
    
    /**
     * @exclude
     */
    public void visitLocalizedAttribute(LocalizedAttribute attribute) 
            throws GeneralException {
        try {
            Describable updatedObj = Describer.removeDescription(_context, attribute);
            if (updatedObj != null) {
                _context.commitTransaction();
                _context.decache((SailPointObject) updatedObj);
            }
        } catch (ClassNotFoundException e) {
            throw new GeneralException("Could not delete the object corresponding to LocalizedAttribute: " + attribute.toString() + " because its targetClass could not be found", e);
        }
        
        innerDelete(attribute);
        
    }
    
    /* remove associated LocalizedAttributes */
    private void visitDescribable(Describable describable) throws GeneralException {
        if (describable != null && !Util.isNullOrEmpty(((SailPointObject)describable).getId())) {
            QueryOptions qo = new QueryOptions(Filter.eq("targetId", ((SailPointObject)describable).getId()));
            // Just remove them -- the only object they reference is being deleted anyway
            List<LocalizedAttribute> attributesToRemove = _context.getObjects(LocalizedAttribute.class, qo);
            if (!Util.isEmpty(attributesToRemove)) {
                for (LocalizedAttribute attributeToRemove : attributesToRemove) {
                    _context.removeObject(attributeToRemove);
                }
                _context.commitTransaction();
            }
        }
    }
    
    /**
     * @exclude
     * Iterate through the list of quicklinkoptions and delete references to this scope prior to 
     * deleting the dynamic scope object
     * @see sailpoint.object.Visitor#visitDynamicScope(sailpoint.object.DynamicScope)
     */
    @Override
    public void visitDynamicScope(DynamicScope dynamicScope) throws GeneralException {
        if (dynamicScope == null)
            return;
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("dynamicScope.id", dynamicScope.getId() ));
        
        deleteObjects(QuickLinkOptions.class, ops);
        
        innerDelete(dynamicScope);
        
    }
    
    /**
     * @exclude
     * Iterate through the list of quicklinkoptions and delete references to this quickLink prior to 
     * deleting the quickLink object
     * @see sailpoint.object.Visitor#visitQuickLink(sailpoint.object.QuickLink)
     */
    @Override
    public void visitQuickLink(QuickLink ql) throws GeneralException {
        if (ql == null)
            return;
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("quickLink.id", ql.getId() ));
        
        deleteObjects(QuickLinkOptions.class, ops);
        
        innerDelete(ql);
    }

    /**
     * @exclude
     *
     * @see sailpoint.object.Visitor#visitPlugin(sailpoint.object.Plugin)
     */
    @Override
    public void visitPlugin(Plugin plugin) throws GeneralException {
        if (plugin == null) {
            return;
        }

        PersistedFile pf =  plugin.getFile();
        if (pf != null) {
            // remove plugin here so PersistedFile will not get a constraint error
            _context.removeObject(plugin);
            visitPersistedFile(pf);
            // visiting the file will commit the transaction so then decache to finish the logic of an innerDelete()
            _context.decache(plugin);
        } else {
            innerDelete(plugin);
        }

    }

    @Override
    public void visitClassification(Classification classification) throws GeneralException {
        if (classification == null) {
            return;
        }

        visitDescribable(classification);

        // Find all ObjectClassification that reference this.
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("classification", classification));
        _context.removeObjects(ObjectClassification.class, ops);
        _context.commitTransaction();
        innerDelete(classification);
    }
}
