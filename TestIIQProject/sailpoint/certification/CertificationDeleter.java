/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Delete a Certification object.
 * 
 * Author: Jeff
 *
 * Adapted from original code in Certificationer but redesigned for
 * more efficient use of memory.  There are still some Certificationer
 * public methods we might want to bring over.  
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// if we factor out a new stat refresher, combine with this
import sailpoint.api.certification.CertificationStatCounter;

import sailpoint.api.CertificationEntitlizer;
import sailpoint.api.CertificationService;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class CertificationDeleter {

	private static Log log = LogFactory.getLog(CertificationDeleter.class);

    SailPointContext _context;

    public CertificationDeleter(SailPointContext context) {
        _context = context;
    }

    /**
     * Delete a certification with options.
     * The cert will be reattached to the context provided at construction.
     * This may decache several times so the state of context cannot be assumed.
     */
    public void delete(Certification cert, boolean forArchive)
        throws GeneralException {

        delete(cert, forArchive, true);
    }
        
    /**
     * Inner recursive deleter.
     * removeFromParent is true for the root cert, false for child certs.
     */
    private void delete(Certification cert, boolean forArchive, boolean removeFromParent)
        throws GeneralException {
        
        final String MeterName = "CertificationDeleter - delete";
        Meter.enter(MeterName);

        // in case we're called several times, start fresh
        _context.decache();
        cert = _context.getObjectById(Certification.class, cert.getId());

        try {
            // remove references from IdentityEntitlements
            clearCertDataFromEntitlements(cert);

            // refetch in case CertificationEntitlizer decached
            cert = _context.getObjectById(Certification.class, cert.getId());
            
            // remove associated WorkItems
            deleteWorkItems(cert, forArchive);

            // Delete the entities
            // The next two can clear the session cache
            deleteArchivedEntities(cert);
            deleteEntities(cert);

            // Recursively delete child certs
            deleteChildren(cert, forArchive);

            // since recursive delete of children can decache, have to refetch again
            cert = _context.getObjectById(Certification.class, cert.getId());

            // Remove this certification from the parent collection
            Certification parent = cert.getParent();
            if (removeFromParent && parent != null) {
                List<Certification> siblings = parent.getCertifications();
                if (siblings != null) {
                    siblings.remove(cert);
                }   
                _context.saveObject(parent);
                _context.commitTransaction();
            }

            // Save the group this cert is in to refresh statistics after
            // deleting. In practice there is only one group and groups do not
            // have a collection of certs so we don't have to worry about pruning.
            List<CertificationGroup> updatedGroups = new ArrayList<CertificationGroup>();
            if (cert.getCertificationGroups() != null) {
                for(CertificationGroup group : cert.getCertificationGroups()) {
                    updatedGroups.add(group);
                }
            }

            _context.removeObject(cert);
            _context.commitTransaction();

            // jsl - Certificationer did this for ever cert but if we're
            // recursing on children only need to do this once for the root
            if (removeFromParent) {
                for (CertificationGroup group : updatedGroups) {
                    refreshStatistics(group);
                }
            }
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    /**
     * Delete child certs.
     */
    private void deleteChildren(Certification cert, boolean forArchive)
        throws GeneralException {

        // Find the children, do this before orphaning because
        // I think Hibernate will null out the back ref
        QueryOptions ops = new QueryOptions();
        // the query certificationer uses, not sure if it is
        // smart enough to avoid the join, 
        // ops.add(Filter.eq("parent.id", cert.getId()));
        ops.add(Filter.eq("parent", cert));

        List<String> ids = getIds(Certification.class, ops);

        // start by emptying the collection, this will reattach
        // and the children will become orphans
        cert.setCertifications(null);
        _context.saveObject(cert);
        _context.commitTransaction();

        for (String id : ids) {
            Certification child = _context.getObjectById(Certification.class, id);
            delete(child, forArchive, false);
            _context.decache();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Entities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete the entities.
     * The list can be massive to query for ids and fetch them 
     * one at a time.
     */
    private void deleteEntities(Certification cert)
        throws GeneralException {

        final String MeterName = "CertificationDeleter - deleteEntities";
        Meter.enter(MeterName);
        try {
            List<String> ids = getEntityIds(cert, CertificationEntity.class);
            if (!Util.isEmpty(ids)) {
                int count = 0;
                // TODO: configuable ?
                int max = 100;

                // null this first so we don't get flush conflicts
                // with the collection
                cert.setEntities(null);
                _context.saveObject(cert);
                _context.commitTransaction();

                for (String id : ids) {

                    CertificationEntity entity = _context.getObjectById(CertificationEntity.class, id);
                    
                    deleteEntity(entity);
                    // any reason to not commit every time, many small transactions
                    // vs fewer larger ones?
                    _context.commitTransaction();
                    count++;
                    if (count >= max) {
                        _context.decache();
                        count = 0;
                    }
                }
            }
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    /**
     * Delete one CertificationEntity.
     * Continuing to use cascade on the items, but need to experiment with
     * deleting them independently.
     * 
     * Let the caller commit so it can control how many we delete per transaction.
     *
     * Complications because CertificationActions on each entity
     * can reference other actions and deleting them causes FK exceptions.
     * So have to null out the references first.  
     * NOTE: This assumes that all of the cross references are within
     * this one entity.  That must be the case since it has always
     * worked this way.
     */
    private void deleteEntity(CertificationEntity entity)
        throws GeneralException {

        final String MeterName = "CertificationDeleter - deleteEntity";
        Meter.enter(MeterName);
        try {
            // Prune cross references between actions
            int prunes = 0;
            CertificationAction action = entity.getAction();
            if (null != action) {
                action.setParentActions(null);
                action.setChildActions(null);
                action.setSourceAction(null);
                _context.saveObject(action);
                prunes++;
            }
            List<CertificationItem> items = entity.getItems();
            for(CertificationItem item : Util.iterate(items)) {
                action = item.getAction();
                if (null != action) {
                    action.setParentActions(null);
                    action.setChildActions(null);
                    action.setSourceAction(null);
                    _context.saveObject(action);
                    prunes++;
                }
            }

            // Certificationer always committed here, not sure if that
            // is necessary if the caller commits
            if (prunes > 0) {
                _context.commitTransaction();
            }
            
            _context.removeObject(entity);
        }
        finally {
            Meter.exit(MeterName);
        }
    }
    
    /**
     * Delete the archived entities in a cert.
     */
    private void deleteArchivedEntities(Certification cert) 
            throws GeneralException {

        final String MeterName = "CertificationDeleter - deleteArchivedEntities";
        Meter.enter(MeterName);
        try {
            // this can be a lengthy list so iterate on the ids
            // Certification.fetchArchivedEntities brings them all in to memory
            // when we get rid of the old code, get rid of that too
            List<String> ids = getEntityIds(cert, ArchivedCertificationEntity.class);

            if (!Util.isEmpty(ids)) {

                int count = 0;
                // TODO: configuable ?
                int max = 100;

                // this is a bag so I don't think we have to null the collection
                // but it could have been partitially materialized so be safe
                cert.setArchivedEntities(null);
                _context.saveObject(cert);
                _context.commitTransaction();

                for (String id : ids) {
                    ArchivedCertificationEntity entity = _context.getObjectById(ArchivedCertificationEntity.class, id);

                    _context.removeObject(entity);
                    // any reason to batch these up?
                    // many small transactions vs one big one
                    // same applies to the main entities list
                    _context.commitTransaction();

                    count++;
                    if (count >= max) {
                        _context.decache();
                        count = 0;
                    }
                }
            }
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    /**
     * Run a query for things that can point back to a Certification and return a 
     * list of their ids.
     */
    private List<String> getEntityIds(Certification cert,
                                      Class<? extends SailPointObject> clazz)
        throws GeneralException {
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("certification", cert));

        return getIds(clazz, ops);
    }
    
    /**
     * Run a query for things that can point back to a Certification and return a 
     * list of their ids.
     */
    private List<String> getIds(Class<? extends SailPointObject> clazz,
                                QueryOptions ops)
        throws GeneralException {

        List<String> ids = new ArrayList<String>();
        
        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> it  = _context.search(clazz, ops, props);
        while (it.hasNext()) {
            Object[] row = it.next();
            ids.add((String)(row[0]));
        }

        return ids;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // WorkItems
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Delete all of the WorkItems associated with a cert.
     * jsl - what's up with the forArchive logic, find out more
     *
     * The top-level items are Hibernate references and have to be pruned.
     * The others are all soft string references.
     */
    private void deleteWorkItems(Certification cert, boolean forArchive)
        throws GeneralException {

        final String MeterName = "CertificationDeleter - deleteWorkItems";
        Meter.enter(MeterName);

        try {
            deleteTopWorkItems(cert);

            // Remediation work items stick around after archiving.
            if (!forArchive) {
                deleteWorkItems("action", cert, CertificationItem.class);
            }

            // Delete challenge and delegation work items - these should go away
            // regardless of whether we're archiving or not.
            deleteWorkItems("challenge", cert, CertificationItem.class);
            deleteWorkItems("delegation", cert, CertificationItem.class);
            deleteWorkItems("delegation", cert, CertificationEntity.class);
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    /**
     * Remove the top-level work items.
     */
    private void deleteTopWorkItems(Certification cert) throws GeneralException {

        List<WorkItem> items = cert.getWorkItems();
        if (items != null) {

            // Would like to null the Certification.workItems list first
            // like we do for entities but since we're not using an id list
            // this creates some session issues when the collection is nulled
            // before it is fully loaded.  Do it after like Certificationer

            Workflower wf = new Workflower(_context);

            for (WorkItem item : items) {
                // Certification work items normally do not have
                // a completion state, so we set it prior to archiving  
                // so it looks consistent with other work items
                if (item.getState() == null)
                    item.setState(WorkItem.State.Finished);

                wf.archiveIfNecessary(item);
                _context.removeObject(item);
            }

            cert.setWorkItems(null);
            _context.saveObject(cert);
            _context.commitTransaction();
        }
    }
    
    /**
     * Delete any work items that were created and had their information stored
     * in the WorkItemMonitor on the given class with the given name for the
     * given cert.
     */
    private void deleteWorkItems(String workItemMonitorName,
                                 Certification cert,
                                 Class<? extends SailPointObject> clazz)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();

        String certPath = "certification";
        if (CertificationItem.class.equals(clazz)) {
            certPath = "parent.certification";
        }

        // Look for WorkItemMonitors that have a non-null work item (ie - a work
        // item was generated) but null completion state (ie - it wasn't
        // completed).
        qo.add(Filter.and(Filter.notnull(workItemMonitorName + ".workItem"),
                          Filter.isnull(workItemMonitorName + ".completionState"),
                          Filter.eq(certPath, cert)));
        qo.setCloneResults(true);
        List<String> props = new ArrayList<String>();
        props.add(workItemMonitorName + ".workItem");

        Iterator<Object[]> it = _context.search(clazz, qo, props);
        if (null != it) {
            while (it.hasNext()) {
                Object[] row = it.next();
                String workItemId = (String) row[0];

                WorkItem workItem =
                    _context.getObjectById(WorkItem.class, workItemId);
                if (null != workItem) {
                    _context.removeObject(workItem);
                }
            }
        }

        // jsl - how many of these are there?  should we commit as we go?
        _context.commitTransaction();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // IdentityEntitlement reference cleanup
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Bulk update Identity Entitlements that match this cert
     * and clear ant previous and current values that refrence
     * the cert that is about to be deleted.
     * 
     * This method performs bulk updates, which isn't complicated
     * but also not the norm.
     */
    private void clearCertDataFromEntitlements(Certification cert) 
        throws GeneralException {

        // Old comments say "This queries Identity Entitlements avoid it if
        // at all possible".  We can check for an empty entities list which
        // almost never happens, wouldn't it be better to go back to the
        // option that caused us to put them there to begin with?  Maybe that
        // can be saved on the cert somewhere.  Also don't like calling size()
        // on this which may have more cache overhead that just doing a count(*)
        // query, compare...
        if (!Util.isEmpty(cert.getEntities())) {
        
            final String MeterName = "CertificationDeleter - clearCertDataFromEntitlements";
            Meter.enterByName(MeterName);
            try {
                CertificationEntitlizer ce = new CertificationEntitlizer(_context);
                ce.prepare(cert); 
                ce.clearEntitlementCertInfo(cert, "certificationItem");
                ce.clearEntitlementCertInfo(cert, "pendingCertificationItem");
            }
            finally {
                Meter.exit(MeterName);
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Statistics
    //
    // Needed for deletion, but it is more general than that.  As we
    // continue refactoring Certificationer this probably belongs in
    // another utility class.
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Refreshes statistics for the certgroup.
     */
    private void refreshStatistics(CertificationGroup group)
        throws GeneralException {

        final String MeterName = "CertificationDeleter - refreshStatistics";
        Meter.enter(MeterName);
        try {
            // this would likely be faster if we didn't have to join and
            // assume there was more than one - jsl
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("certificationGroups.id", group.getId()));
            ops.setCloneResults(true);

            // optionally filter empty certs for pending groups.
            CertificationService.filterEmptyCerts(_context, ops, group);
        
            List<String> projectionCols = new ArrayList<String>();
            projectionCols.add("count(id)");
            projectionCols.add("count(signed)");

            Iterator<Object[]> iter = _context.search(Certification.class, ops, projectionCols);
            if (iter != null && iter.hasNext()){
                Object[] results=iter.next();
                int total = ((Long)results[0]).intValue();
                int completed = ((Long)results[1]).intValue();
            
                int percentComplete = CertificationStatCounter.calculatePercentComplete(completed, total);

                // Don't overwrite an Error or Pending status regardless of completion status.
                // Pending cert groups will get set to Complete in BaseCertificationBuilder.finalize

                if (percentComplete == 100 && !CertificationGroup.Status.Error.equals(group.getStatus())
                    && !CertificationGroup.Status.Pending.equals(group.getStatus())) {
                    group.setStatus(CertificationGroup.Status.Complete);
                }

                group.setCompletedCertifications(completed);
                group.setTotalCertifications(total);
                group.setPercentComplete(percentComplete);

                _context.saveObject(group);
                _context.commitTransaction();
            }
        }
        finally {
            Meter.exit(MeterName);
        }
    }
    
    
}

