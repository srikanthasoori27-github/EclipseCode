/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;


/**
 * This class can tweak the contents of certifications - merge new entities into
 * a certification, remove entities from a certification, move entities between
 * certifications, etc...
 *
 * This is only really used for certification reassignments, most of the functionality
 * of this class has been removed due to the deprecation of both continuous certifications
 * and adding/removing entities based on inactive status of identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationSwizzler {

    private static Log log = LogFactory.getLog(CertificationSwizzler.class);
    
    private SailPointContext context;
    private Certificationer certificationer;
    private Map<String, CertificationContext> contextsCache = null;
    private EntitlementCorrelator correlator;
    private CertificationService service;

    /**
     * Constructor.
     */
    public CertificationSwizzler(SailPointContext context, Certificationer certificationer) 
        throws GeneralException {

        this(context, certificationer, null);
    }

    public CertificationSwizzler(SailPointContext context, Certificationer certificationer, EntitlementCorrelator correlator) 
        throws GeneralException {

        assert (null != context) : "Context is required";
        assert (null != certificationer) : "Certificationer is required";

        this.context = context;
        this.certificationer = certificationer;
        this.correlator = correlator;
        this.service = new CertificationService(this.context);

        boolean useCache = this.context.getConfiguration().getBoolean(Configuration.USE_CERT_CONTEXTS_CACHE, false);
        if (useCache) {
            this.contextsCache = new HashMap<String, CertificationContext>();
        }
        
        if (log.isInfoEnabled()) 
            log.info("useCache: " + useCache);
    }
    
    /**
     * Merge the given entities into the given certifications.  If a matching
     * entity already exists in the given certification, this will add any
     * missing items from the certifiable entity onto the existing entity.
     * If a matching entity doesn't exist, this just adds a new entity to the
     * certification.
     * 
     * @param  entities      The entities to merge into the certs.
     * @param  cert          The Certification into which to merge the entity.
     * @param  authoritative If true, the items in the given entities are
     *                       treated as authoritative and any non-matching items
     *                       are removed from the certification.
     * @param  markDiffs     Whether new items that are merged into this cert
     *                       should be marked as "has differences".
     */
    void merge(List<CertificationEntity> entities, Certification cert,
               boolean authoritative, boolean markDiffs)
        throws GeneralException {

        Meter.enterByName("CertificationSwizzler: merge2");
        if ((null != entities) && !entities.isEmpty()) {
            Terminator terminator = new Terminator(context);
            for (CertificationEntity entity : entities) {
                if (entity == null || entity.getItems() == null || entity.getItems().size() == 0) {
                    // don't merge if there's nothing to merge
                    continue; // next entity
                }
                if(cert != null) { 
                    CertificationEntity found = findEntity(entity, cert);
                    if (null != found) {
                        Meter.enterByName("CertificationSwizzler: merge2=>mergeEntity");
                        List<CertificationItem> removed =
                            found.mergeEntity(entity, authoritative, markDiffs);
                        Meter.exitByName("CertificationSwizzler: merge2=>mergeEntity");

                        // Delete any removed items.  The merge just removes them
                        // from the list.
                        Meter.enterByName("CertificationSwizzler: merge2=>deleteObjects");
                        for (CertificationItem item : removed) {
                            // Cleanup any associated work items.
                            List<WorkItem> workItems = Certificationer.getWorkItems(this.context, item);
                            for (WorkItem workItem : workItems) {
                                terminator.deleteObject(workItem);
                            }
                            
                            //save the items to prevent transient failures while
                            //removing.  The entities' merge function has modified
                            //the items making this necessary.
                            this.context.saveObject(item);
                            terminator.deleteObject(item);
                        }
                        Meter.exitByName("CertificationSwizzler: merge2=>deleteObjects");

                        // cleanup the entity used to transfer the cert items
                        Iterator<CertificationItem> itemIterator = entity.getItems().iterator();

                        // remove all item references to the entity and the items from the list
                        while (itemIterator.hasNext()) {
                            CertificationItem item = itemIterator.next();
                            if (item != null && item.getParent().equals(entity)) {
                                item.setParent(null);
                            }
                            itemIterator.remove();
                        }

                        // remove object from session. this entity was created in Certificationer removeItems method
                        // to transfer the reassigned items.
                        this.context.removeObject(entity);
                    }
                    else {
                        cert.add(entity);
    
                        // We added a new user to this certification.  Mark as new.
                        if (markDiffs) {
                            entity.setNewUser(true);
                        }
                        
                        entity.markForRefresh();

                        // save transient object. during reassign this entity was created in the Certificationer
                        // removeItems method but is never saved until now.
                        this.context.saveObject(entity);
                    }
    
                    // Move the work item references to point at the new cert (if
                    // there are any).
                    CertificationEntity toTweak = (null != found) ? found : entity;
                    List<WorkItem> workItems = Certificationer.getWorkItems(this.context, toTweak);
                    for (WorkItem workItem : workItems) {
                        workItem.setCertification(cert);
                        this.context.saveObject(workItem);
                    }
                }
            }

            // Refresh the certification.  This will generate per-delegation
            // items, update completion and cert statistics, etc...
            Meter.enterByName("CertificationSwizzler: merge2=>refresh");
            this.context.saveObject(cert);
            this.context.commitTransaction();
            this.certificationer.refresh(cert);
            Meter.exitByName("CertificationSwizzler: merge2=>refresh");

        }
        Meter.exitByName("CertificationSwizzler: merge2");

        // TODO: Do we need to transfer decision history and comments, too?
    }
    
    /**
     * Look for a matching entity in the given certification.
     */
    private CertificationEntity findEntity(CertificationEntity entity,
                                           Certification cert)
        throws GeneralException {
        
        Filter f = entity.getEqualsFilter(cert);
        return this.context.getUniqueObject(CertificationEntity.class, f);
    }
}
