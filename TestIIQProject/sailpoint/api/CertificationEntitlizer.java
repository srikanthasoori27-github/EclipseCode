/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * An API class that handles updating entitlements as they are in the 
 * process of being certified (pending) and when they are completed 
 * during certification finalization.
 * 
 * This class currently only updates certification that are either
 * Identity, Manager or Application owner types.
 * 
 * There are two basic operations that this class performs. This
 * includes setting the PendingCertification when the certification
 * is generated and setting the completed one once it has
 * been finalized.  
 * 
 * For each case we find the IdentityEntitlement matching the
 * entitlement listed in the cert and update the entitlements
 * with the item references.
 * 
 * The certification and IdentityEntitlement models are 
 * related in that IdentityEntitlements  have references to a 
 * certification item.  In both methods here we go through each 
 * entity's items and try and find corresponding entitlements.
 * 
 * The various cert entitlement granularities to consider are :
 * 
 * o Application : Where there is just a single item for all
 *   entitlements.
 *               
 * o Attribute : An item for each attribute name /permission target
 * 
 * o Value : An item for each value or right
 * 
 * Here's the logic here handled for each certification item:
 * 
 * Fetching strategy, in all three types of granularity
 * we'll need update all of the user's entitlements
 * on that app.  The exception is when things have been
 * excluded from a certification.
 * 
 * @see {@link AbstractEntitlizer}
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @since 6.0
 * 
 */
public class CertificationEntitlizer extends AbstractEntitlizer   {
    
    private static Log log = LogFactory.getLog(CertificationEntitlizer.class);    
    
    /**
     * Cache of entitlements for a single identity.  Keep a cache
     * so we can fetch all entitlements  we will use in a 
     * certification to prevent a separate call for each 
     * item we process.
     */
    IdentityEntitlementCache _cache;
       
    /**
     * Calculate this based on the type of cert and the 
     * setting in the certification definition.
     */
    boolean _updateEntitlements;
    
    String _lastCertDefId;
    
    /**
     * Constructor that take a SailPointContext.
     * 
     * In addition to this method callers must also call prepare().
     * 
     * @param context SailPointContext
     */
    public CertificationEntitlizer(SailPointContext context) {
        super(context);        
        _updateEntitlements = false;
        _cache = null;
    }
    
    /***
     * Initialize the "enable" flag based on the definition and the cert
     * type.
     * 
     * All callers must call this method.
     * 
     * @throws GeneralException
     */
    public void prepare(Certification certification) throws GeneralException {
        _updateEntitlements = false;        
        if ( certification != null ) {
            String id = certification.getCertificationDefinitionId();
            if ( Util.nullSafeCompareTo(id, _lastCertDefId ) != 0 ) {
                CertificationDefinition def = certification.getCertificationDefinition(_context);
                if ( def != null ) {
                    if ( def.isUpdateIdentityEntitlements() && isApplicableCertType(certification)) {
                       _updateEntitlements = true;     
                    }
                }
            }
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Methods used to update the IdentityEntitlements based on the 
    // data from certification generation/finishing
    //
    //////////////////////////////////////////////////////////////////////////

    /** 
     *  Go through the certification item and where applicable
     *  adorn any dependent entitlements.
     *  
     *  This method is called by Certificationer and purposely 
     *  does not do any decaching because Certificationer is calling
     *  this as each item is finished and decaches along 
     *  the way.
     *  
     *  Let the caller commit, to avoid over committing.
     * 
     * @param entity CertificationEntity
     * @param item CertificationItem
     * @param adds is the attribute assignments that are going to be added as part of the cert options
     * @param removes is the attribute assignments that are going to be removed as part of the cert
     * @throws GeneralException
     */
    public void setCurrent(CertificationEntity entity, CertificationItem item, 
                           List<AttributeAssignment> adds, 
                           List<AttributeAssignment> removes ) 
        throws GeneralException {        
        if ( !_updateEntitlements ) {
            if ( log.isDebugEnabled() ){
                log.debug("Update entitlements flag disabled or wrong type, skipping setCurrent for [" + entity.getTargetId() + "]");
            }
            return;
        }
        String identityId;
        // If this is a DataOwner CertificationEntity type, then get the identityId from the
        // CertificationItem since the entity represents a group and not an identity.
        if ( CertificationEntity.Type.DataOwner.equals(entity.getType()) ) {
            identityId = item.getTargetId();
        } else {
            identityId = resolveIdentityId(entity);
        }

        if ( identityId == null ) {
            return;
        }        
        IdentityEntitlementCache cache = getEntityCache(identityId);
        adornCertItemToEntitlement(cache, item, false, adds, removes);
    }
    
    
    /**
     * Find the entitlement for each item and add the certification's item
     * reference to the entitlement. 
     *  
     * @param entity CertificationEntity
     * @throws GeneralException
     */
    public void setPending(CertificationEntity entity) throws GeneralException {
        if ( !_updateEntitlements ) {
            if ( log.isDebugEnabled() ){
                log.debug("Update entitlements flag disabled or wrong type, skipping setPending for [" + entity.getTargetId() + "]");
            }
            return;
        }
        String identityId = resolveIdentityId(entity);        
        if ( identityId == null ) {
            if ( log.isDebugEnabled() ){
                log.debug("Unable to resolve identity id, skipping setPending for [" + entity.getTargetId() + "]");
            }
            return;
        }        
        
        if ( log.isInfoEnabled() ) {
            log.info("setPending start" + entity.getTargetName() + " start.");            
        }
        if ( log.isDebugEnabled() ) {
            Util.enableSQLTrace();
        }
        
        Date start = new Date();
        if ( entity != null ) { 
            IdentityEntitlementCache cache = getEntityCache(identityId);
            List<CertificationItem> items = entity.getItems();
            if ( items != null ) {
                for ( CertificationItem item : items ) {
                    if ( item != null )
                        adornCertItemToEntitlement(cache, item, true, null, null); 
                }
            }
            // this is a per entity cache..
            cache = null;
        }
        long totalTime = Util.computeDifferenceMilli(start, new Date());
        
        if ( entity != null && log.isInfoEnabled() ) {
            log.info("Set pending during cert " + entity.getTargetName() +" completed in :" + totalTime);          
        }            
        if ( log.isDebugEnabled() ) {            
            Util.disableSQLTrace();
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Item -> Entitlement adornment
    //
    ///////////////////////////////////////////////////////////////////////////
   
    /**
     * Process the CertificationItem and update the entitlements with the 
     * interesting certification item reference.
     * 
     * The certification has a granularity, which tells us how much
     * stuff we are liable to see in the certification and can help
     * drive the techniques used to query the entitlements.
     * 

     * 1) Exception
     * 
     *    If the item is of Type Exception get snapshot and
     *    update any of the entitlements contained in the 
     *    snapshot.
     *    
     *    Most of the time this is just one entitlement, but really
     *    depends on the granularity of the certification.
     *    
     *    When the granularity is set to value, its a one to one
     *    mapping from entitlement to certificationItem.
     *    
     *    When the granularity is set to attribute, the map
     *    on each EntitlementSnapshot will contain one or
     *    more entitlements per name, that need to be updated 
     *    for each  certification item.
     *    
     *    When the granularity is set to application, the
     *    snapshot will contain one or more attributes from
     *    the application with one or more keys.
     *    
     * 2) Bundle
     *    
     *    The Bundle type will signify a role entitlement, a null
     *    subtype indicates a detected Role and the assignedRole
     *    type of course indicates assigned role.
     *    
     *    For assigned roles and detected roles we'll have an
     *    entitlement per role in the identity entitlements
     *    table.
     *    
     *    In the case of detected roles we need to also
     *    update the entitlements that were part of the 
     *    role.    
     *    
     *    
     * @ignore NOTE this is called by both setPending and setCurrent, 
     * cannot decache and should not commit.
     *
     * @throws GeneralException
     */
    private void adornCertItemToEntitlement(IdentityEntitlementCache cache,
                                            CertificationItem item, 
                                            boolean pending, 
                                            List<AttributeAssignment> adds,
                                            List<AttributeAssignment> removes) 
        throws GeneralException {

        Meter.enter(200, "adornCertItemToEntitlement getEntitlements");
        List<IdentityEntitlement> entitlements = getCertItemEntitlements(cache, item);
        Meter.exit(200);

        if ( Util.size(entitlements) == 0 ) {
            if ( log.isDebugEnabled() ) {
                log.debug("No entitlements found, cert enetitlement adorning aborted.");
            }
            return;
        }

        if ( CertificationItem.Type.Exception.equals(item.getType()) ||
                CertificationItem.Type.DataOwner.equals(item.getType()) ) {
            EntitlementSnapshot snapshot = item.getExceptionEntitlements();
            if ( snapshot != null) {
                EntitlementFinder entitlementFinder = new EntitlementFinder(entitlements);
                updateSnapshotEntitlements(entitlementFinder, item, snapshot, pending);
            }
        } else if ( CertificationItem.Type.Bundle.equals(item.getType()) ) {
            //
            // Update the role IdentityEntitlements and associated 
            // entitlements in the case of assigned roles
            //
            
            String bundleName = item.getBundle();                
            if ( bundleName != null ) {
                String attributeName = ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
                if ( Util.nullSafeEq(CertificationItem.SubType.AssignedRole, item.getSubType() ) ) {
                    attributeName = ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES;
                }
                
                // First update the "Role" connection we store in the IdentityEntitlement table
                // These should be assigned Roles or DetectedRoles without an assignmentId.
                IdentityEntitlement entitlement = findRoleEntitlement(entitlements, attributeName, bundleName, item.getBundleAssignmentId());
                List<IdentityEntitlement> assignedAndDetectedEntitlements = new ArrayList<IdentityEntitlement>();
                if  ( entitlement != null ) {
                    assignedAndDetectedEntitlements.add(entitlement);
                    // For assigned roles, add any detected roles with a matching assignment id, since they 
                    // are implicitly certified along with the assigned role
                    if (Util.nullSafeEq(CertificationItem.SubType.AssignedRole, item.getSubType())) {
                        List<IdentityEntitlement> detectedEntitlements = findDetectedEntitlements(entitlements, item.getBundleAssignmentId());
                        if (detectedEntitlements != null) {
                            assignedAndDetectedEntitlements.addAll(detectedEntitlements);
                        }
                    }
                } else {
                    if ( log.isInfoEnabled() ) {
                        log.info("Unable to find role entitlement for [" + attributeName + "]==[" + bundleName + "].  Number of entitlements on item: " + Util.size(entitlements));
                    }
                }
                
                for (IdentityEntitlement identityEntitlement : assignedAndDetectedEntitlements) {
                    if ( pending ) {
                        identityEntitlement.setPendingCertificationItem(item);
                    } else {
                        setCurrentCertItem(identityEntitlement, item);
                    }
                }
            }

            //
            // If there is overlap in the entitlements, last one wins....
            //
            List<EntitlementSnapshot> bundleEntitlements = item.getBundleEntitlements();
            EntitlementFinder entitlementFinder = new EntitlementFinder(entitlements);
            if ( Util.size(bundleEntitlements) > 0 ) {
                for ( EntitlementSnapshot bundleSnapshot : bundleEntitlements ) {
                    if ( bundleSnapshot != null)
                        updateSnapshotEntitlements(entitlementFinder, item, bundleSnapshot, pending); 
                }
            }
        }

        // 
        // role over all of the entitlements and make sure the assignment flag
        // is correctly configured based on certification decisions
        // Adds/Removes are only computed for Exceptions
        // 
        if ( !Util.isEmpty(entitlements) && (!Util.isEmpty(adds) || !Util.isEmpty(removes))) {
            AssignmentMatcher addMatches = new AssignmentMatcher(adds);
            AssignmentMatcher removeMatches = new AssignmentMatcher(removes);
            for ( IdentityEntitlement entitlement : Util.safeIterable(entitlements) ) {
                if (addMatches.found(entitlement)) {
                    entitlement.setAssigner(getCertifierDisplayName(item));
                    entitlement.setAssigned(true);
                } else if (removeMatches.found(entitlement)) {
                    entitlement.setAssigner(null);
                    entitlement.setAssigned(false);
                }
            }
        }
    }
    
    /**
     * Find identity entitlements representing detected roles in the given assignment
     * @return List of IdentityEntitlement for detected roles, or null
     */
    private List<IdentityEntitlement> findDetectedEntitlements(List<IdentityEntitlement> entitlements, String assignmentId) 
        throws GeneralException {
        if (Util.isEmpty(entitlements) || assignmentId == null) {
            return null;
        }

        List<IdentityEntitlement> detectedEntitlements = new ArrayList<IdentityEntitlement>();
        for (IdentityEntitlement entitlement : entitlements) {
            if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(entitlement.getName()) &&
                    assignmentId.equals(entitlement.getAssignmentId())) {
                detectedEntitlements.add(entitlement);
            }
        }

        return detectedEntitlements;
    }
    
    /**
     * From the item get the assigner names either from the Action or
     * from the certification directly.
     *
     * @throws GeneralException
     */
    private String getCertifierDisplayName(CertificationItem item)
        throws GeneralException  {
        
        String certifier = null;
        CertificationAction action = item.getAction();
        if ( action != null ) {
            certifier = action.getActorDisplayName();
        }
 
        CertificationEntity entity = item.getParent();
        if ( certifier == null && entity != null ) {
            Certification cert = entity.getCertification();
            if ( cert != null ) {                      
                //
                // If we can't get it from the action use the cert's certifiers
                //
                List<String> certifiers = cert.getCertifiers();
                if ( Util.size(certifiers) > 0 )  {
                    List<String> certifiersDisplayNames = new ArrayList<String>();
                    for ( String idName : certifiers ) {                                
                        String certifierDisplayName = resolveIdentityPropertyFromNameOrId(idName, "displayName");
                        if ( certifierDisplayName != null ) {
                            certifiersDisplayNames.add(certifierDisplayName);
                        } else {
                            certifiersDisplayNames.add(idName);
                        }
                    }
                    if ( certifiersDisplayNames.size() > 0 )
                        certifier = Util.listToCsv(certifiersDisplayNames);
                }                        
            }  
        }
        return certifier;
    }

    /**
     * Pull out the permissions and attributes from a snapshot and 
     * update the entitlements associated with them...
     */
    private void updateSnapshotEntitlements(EntitlementFinder entitlementFinder,
                                            CertificationItem item, 
                                            EntitlementSnapshot snapshot,
                                            boolean pending) 
        throws GeneralException {
       
        Attributes<String,Object> attrs = snapshot.getAttributes();
        if ( !Util.isEmpty(attrs) ) {
            updateSnapshotAttributes(entitlementFinder, snapshot.getApplicationName(), snapshot.getInstance(), snapshot.getNativeIdentity(), attrs, item, pending);
        }
            
        List<Permission> permissions = snapshot.getPermissions();
        if ( Util.size(permissions) > 0 ) {
            updateSnapshotPermissions(entitlementFinder, snapshot.getApplicationName(), snapshot.getInstance(), snapshot.getNativeIdentity(), permissions, item, pending);
        }
    }
    
    /**
     * Go through the Attributes from a snapshot and mark
     * each entitlement with the certification item
     * reference.
     */    
    private void updateSnapshotAttributes(EntitlementFinder entitlementFinder,
                                          String appName,
                                          String instance,
                                          String nativeIdentity,
                                          Attributes<String,Object> attrs,
                                          CertificationItem item, 
                                          boolean pendingCert) 
        throws GeneralException {
        
        if (attrs == null) {
            return;
        }

        Set<String> keys = attrs.keySet();               
        for ( String key : keys ){
            Object val = attrs.get(key);
            if ( val != null ) {
                updateEntitlements(entitlementFinder, appName, nativeIdentity, instance, key, val, item, pendingCert);
            }
        }
    }
    
    /**
     * For each value found, find the IdentityEntitlement and
     * set the certification properties on the entitlement.
     */
    @SuppressWarnings("unchecked")
    private void updateEntitlements(EntitlementFinder entitlementFinder,
                                    String appName, String nativeIdentity, String instance,
                                    String attrName, Object val, 
                                    CertificationItem item, 
                                    boolean pendingCert) 
        throws GeneralException { 

        List<Object> vals = Util.asList(val);
        if ( vals != null ) {
            for ( Object v : vals ) {
                if ( v != null ) {
                    String str = v.toString();
                    if ( Util.getString(str) != null ) {
                        EntitlementKey key = new EntitlementKey(appName, nativeIdentity, instance, attrName, str);
                        IdentityEntitlement entitlement = entitlementFinder.findEntitlement(key);
                        if ( entitlement != null ) {
                            if ( pendingCert ) {
                                entitlement.setPendingCertificationItem(item);                                                    
                            } else {
                                setCurrentCertItem(entitlement, item);
                            }
                        } else {
                            if ( log.isDebugEnabled() ) {
                                log.debug("Unable to find entitlement ["+attrName+"] value["+str+"] on " + item.getApplicationNames() +"." + "Number of entitlements on item: " + entitlementFinder.getEntitlementCount());
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * For each permission find the entitlement and adorn the
     * certification information onto the existing entitlement.
     */
    private void updateSnapshotPermissions(EntitlementFinder entitlementFinder,
                                           String appName, 
                                           String instance, 
                                           String nativeIdentity,
                                           List<Permission> permissions,  
                                           CertificationItem item, boolean pendingCert) 
        throws GeneralException {
        
        if ( permissions != null ) {
            for ( Permission permission : permissions ) {
                if ( permission == null ) continue;
                String target = permission.getTarget();
                List<String> rights = permission.getRightsList();
                if ( target != null && rights != null ) {
                    for ( String right : rights ) {
                        IdentityEntitlement entitlement = findPermissionEntitlement(entitlementFinder, appName, instance, nativeIdentity, target, right);
                        if ( entitlement != null ) {
                            if ( pendingCert ) { 
                                entitlement.setPendingCertificationItem(item);
                            } else {
                                setCurrentCertItem(entitlement, item);
                            }
                        } else {
                            if ( log.isDebugEnabled() )
                                log.debug("Unable to find permission entitlement ["+target+"] value["+right+"] on " + item.getApplicationNames());
                        }
                    }
                }
            }
        }
    }   
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Fetch/Find entitlements
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Get the list of effected IdentityEntitlements for a given
     * Certification Item.
     * 
     * When entitlement granularity is value there should be one to one
     * relationship between item and entitlement. 
     * 
     * For other granularities we may find multiple entitlements
     * for a single certification item.  We'll use the snapshot to
     * drive the "effected" entitlements lists.
     * 
     * For roles...
     * 
     * Assigned roles we'll just return the directly assigned 
     * roles.  
     * 
     * Detected roles when certified imply all of the entitlements that
     * are assigned by the detected role. We'll dig into the bundle
     * entitlements and derive the entitlements that need to be
     * updated. 
     *
     * @throws GeneralException
     */
    private List<IdentityEntitlement> getCertItemEntitlements(IdentityEntitlementCache cache, CertificationItem item) 
        throws GeneralException {
       
        if ( item == null ) { 
            return null;
        }
               
        List<IdentityEntitlement> entitlements = null;     
        if ( CertificationItem.Type.Exception.equals(item.getType()) ||
                CertificationItem.Type.DataOwner.equals(item.getType()) ) {
            EntitlementSnapshot snapshot = item.getExceptionEntitlements();  
            if ( snapshot != null ) {
                entitlements = cache.getSnapShotEntitlements(snapshot);
            }
        } else 
        if ( CertificationItem.Type.Bundle.equals(item.getType()) )   {            
            if ( CertificationItem.SubType.AssignedRole == item.getSubType() )  {                
                entitlements = cache.getAssignedRolesEntitlements();
                // We need the detected roles since they are indirectly certified along with
                // assigned role
                List<IdentityEntitlement> otherEntitlements = cache.getDetectedRolesEntitlements();
                if (otherEntitlements != null) {
                    entitlements.addAll(otherEntitlements);
                }
            } else {
                entitlements = cache.getDetectedRolesEntitlements();
            }

            // 
            // We are also interested in the bundle entitlements that
            // come along with the assignment of the bundle as they
            // are indirectly certified.
            List<EntitlementSnapshot> bundleSnapshots = item.getBundleEntitlements();
            if ( Util.size(bundleSnapshots) > 0 ) {          
                List<IdentityEntitlement> roleEntitlements = cache.getSnapShotEntitlements(bundleSnapshots);
                if ( Util.size(roleEntitlements) > 0 ) {
                    if ( Util.size(entitlements) == 0 ) {
                        if ( entitlements == null ) 
                            entitlements = new ArrayList<IdentityEntitlement>();
                        entitlements = roleEntitlements;
                    } else {
                        entitlements.addAll(roleEntitlements);
                    }
                }
            }
        }    
        return entitlements;
    }
   
    /**
     *  Find a permission entitlement in the list of entitlements
     *  which has already been refined down to the application,
     *  native identity and instance level. 
     */
    private IdentityEntitlement findPermissionEntitlement(EntitlementFinder entitlementFinder, String appName, String instance, String nativeIdentity, String target, String right)
        throws GeneralException {
        EntitlementKey key = new EntitlementKey(appName, nativeIdentity, instance, target, right);
        IdentityEntitlement found = entitlementFinder.findEntitlement(key);
        if ( ( found != null && Util.nullSafeEq(found.getType(), ManagedAttribute.Type.Permission) ) ) {
            return found;
        }

        return null; 
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Removal
    //
    //////////////////////////////////////////////////////////////////////////   
    
    /**
     * Query for the entitlements that have references to the certification
     * and null out the reference. Called from the Certificationer
     * when certifications are being removed, any references we have
     * need to be nulled.
     * 
     * This does a bulk update , which sets the defined property list
     * to null.
     * 
     * @param cert Certification
     * @param property Properties to be nulled
     * @throws GeneralException
     */
    public void clearEntitlementCertInfo(Certification cert, String... property)
            throws GeneralException {

        if ( cert == null ) return;

        if ( property == null )
            throw new GeneralException("Unable to perform entitlement cert cleanup. propery was null!");

        QueryOptions ops = new QueryOptions();
        List<Filter> filters = new ArrayList<Filter>();

        for ( String prop : property ) {
            filters.add(Filter.eq(prop+".parent.certification", cert));
        }

        if ( filters.size() > 0 ) {
            ops.add(Filter.or(filters));
            bulkNullColumns(ops, property);
        }
    }                              
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Using the targetId if it exists, otherwise try and resolve
     * the Identity name by a projection query.
     * 
     * @throws GeneralException
     */
    private String resolveIdentityId(CertificationEntity entity) throws GeneralException {
        String identityId = entity.getTargetId();
        if ( identityId == null ) {
            identityId = resolveIdentityPropertyFromNameOrId(entity.getIdentity(), "id");
        }
        if ( identityId == null ) {
            log.error("Unable to resolve Identity name for "+ entity.getTargetId() + "]");
        }
        return identityId;
    }
    
    /***
     * In some cases where we might not have an targetId set on the entity
     * we need to take the name stored on the entity and turn it in to an
     * id.
     */    
    private String resolveIdentityPropertyFromNameOrId(String nameOrId, String property) 
        throws GeneralException {
        
        String id = null;
        
        if ( nameOrId == null) 
            return id;
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.or(Filter.eq("name", nameOrId), 
                          Filter.eq("id", nameOrId)));
        
        Iterator<Object[]> rows = _context.search(Identity.class, ops, property);
        if ( rows != null ) {
            int i = 0;
            while ( rows.hasNext() ) {
                i++;
                Object[] row = rows.next();
                if ( row.length > 0 ) {
                    id = (String)row[0];
                }
            }
            if ( i != 1 ) {
                throw new GeneralException("Unable to resovle identity id from name. Returned " +i + " only one was required.");
            }
        }
        return id;
    }
       
    /**
     * Since we are only interested in a subset of certification
     * types filter out all except the ones that we know apply.
     */
    private boolean isApplicableCertType(Certification cert) {        
        if ( cert != null ) {
            Certification.Type type = cert.getType();
            if ( type != null &&
               ( Certification.Type.Identity == type ) || 
               ( Certification.Type.Manager == type )  ||
               ( Certification.Type.ApplicationOwner == type ) ||
               ( Certification.Type.DataOwner == type ) ||
               ( Certification.Type.Focused == type)) {
                return true;
            }
        }
        return false;
    }
        
    /**
     * Internal method to handle updating the entitlement with the 
     * certification item.
     * 
     * In addition to setting the current item, it also nulls out the 
     * pending field if it's referencing the same certification item.
     * 
     */
    private void setCurrentCertItem(IdentityEntitlement entitlement, CertificationItem item ) {
        if ( entitlement != null ) { 
            entitlement.setCertificationItem(item);
            CertificationItem pending = entitlement.getPendingCertificationItem();
            if ( pending != null ) {
                String itemId = ( item != null ) ? item.getId() : null;
                String pendingItemId = pending.getId();
                if ( ( Util.nullSafeEq(itemId, pendingItemId) ) ) { 
                    entitlement.setPendingCertificationItem(null);
                }
            }
        }
    }
    
    private IdentityEntitlementCache getEntityCache(String id) {
        if ( _cache == null ) {
            _cache = new IdentityEntitlementCache(id);            
        } else {
            if ( !_cache.isSameIdentity(id) ) {
                _cache = null;
                _cache = new IdentityEntitlementCache(id);
            }
        }
        return _cache;
    }
    
    /**
     * 
     * Utility class that will be used to access the entitlements
     * handled during a certification.
     *      
     */
    public class IdentityEntitlementCache {
        
        /**
         * For most certifications we'll be processing all of the
         * entitlements on an application. So instead of making a call
         * per item, up front load all of the app scoped entitlements.
         *  
         */
        Map<String,List<IdentityEntitlement>> _cache;
        
        /**
         * assignedRole Identity Entitlements
         */
        List<IdentityEntitlement> _assigned;

        /**
         * detectedRoles Identity Entitlements
         */
        List<IdentityEntitlement> _detected;
        
        /**
         * Typically a stub identity with just the ID set so we can
         * work around the HQLFilterVisitor and pass in a fake identity
         * to prevent the inner join needed to query by id.
         */
        Identity _identity;
        
        int _numObjectsCached;
        
        private IdentityEntitlementCache() {        
            clear();
        }
        
        public IdentityEntitlementCache(String identityId) {
            this();
            _identity = new Identity();
            _identity.setId(identityId);
        }
  
        /**
         * @return
         * @throws GeneralException
         */
        public List<IdentityEntitlement> getAssignedRolesEntitlements() throws GeneralException {
            if ( _assigned == null ) {
                _assigned = CertificationEntitlizer.this.getAssignedRolesEntitlements(_identity);
                if ( _assigned == null ) 
                    _assigned = new ArrayList<IdentityEntitlement>();
            }
            return _assigned;
        }

        /**
         * @return
         * @throws GeneralException
         */
        public List<IdentityEntitlement> getDetectedRolesEntitlements() throws GeneralException {
            if ( _detected == null ) {
                _detected = CertificationEntitlizer.this.getDetectedRoleEntitlements(_identity);     
                if ( _detected == null ) 
                    _detected = new ArrayList<IdentityEntitlement>(); 
            }
            return _detected;
        }

        public List<IdentityEntitlement> getSnapShotEntitlements(EntitlementSnapshot snapshot)
            throws GeneralException {            
                        
            if ( snapshot != null ) {
                Application app = snapshot.getApplicationObject(_context);
                String nativeIdentity = snapshot.getNativeIdentity();
                String instance = snapshot.getInstance();
                List<Object> values = snapshot.getAttributeValues();
                List<String> stringValues = new ArrayList<String>();
                for (Object value : Util.iterate(values)) {
                    stringValues.add(Util.otos(value));
                }
                return get(app, nativeIdentity, instance, stringValues);
            }
            return null;
        }

        public List<IdentityEntitlement> getSnapShotEntitlements(List<EntitlementSnapshot> snapshots)
            throws GeneralException {

            List<IdentityEntitlement> entitlements = null;
            if ( snapshots != null && snapshots.size() > 0 ) {                
                entitlements = new ArrayList<IdentityEntitlement>();
                for ( EntitlementSnapshot ss : snapshots ) {
                    List<IdentityEntitlement> snapshotEntitlements = getSnapShotEntitlements(ss);
                    if (!Util.isEmpty(snapshotEntitlements)) {
                        entitlements.addAll(snapshotEntitlements);
                    }
                }                
            }             
            return entitlements;
        }

        /*
         * This method returns the IdentityEntitlements corresponding to the ones contained in the certification item, as determined
         * by the item's EntitlementSnapshot.
         */
        private List<IdentityEntitlement> get(Application app, String nativeIdentity, String instance, List<String> values) 
            throws GeneralException {
            List<IdentityEntitlement> list;
            
            if ( app == null ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Null application encounterd during get() cache call. Could be normal for IIQ capabilites in snapshots.");
                }
                return null;
            }

            String appName = app.getName();
            String key = appName +"/" + nativeIdentity +"/" + instance;
            if (!Util.isEmpty(values)) {
                Collections.sort(values);
                key += "/" + Util.listToCsv(values);
            }

            list = _cache.get(key);            
            if ( list == null ) {
                QueryOptions qo = new QueryOptions();
                qo.add(buildAccountFilter(_identity, app, nativeIdentity, instance));
                if (!Util.isEmpty(values)) {
                    qo.add(Filter.in("value", values));
                }
                qo.add(Filter.not(ROLE_FILTER));
                list = getEntitlements(qo);
                _cache.put(key, list);                
                _numObjectsCached += Util.size(list);
                if ( log.isDebugEnabled() ) 
                    log.debug("Feching entitlements for cert " + _identity + " key: " + key + " current size " + _numObjectsCached);
            }
             
            return list;
        }

        public String getIdentityId() {
            return ( _identity != null ) ? _identity.getId() : null;
        }         
        
        public boolean isSameIdentity(String id) {
            if ( Util.nullSafeCompareTo(id, getIdentityId()) == 0 ) {
                 return true;
            }
            return false;
        }
        
        public void clear() {
            _numObjectsCached = 0;
            _identity = null;
            _detected = null;
            _assigned = null;
            _cache = null;
            _cache = new HashMap<String,List<IdentityEntitlement>>();
        }        
    }
    
    /**
     * This helps the entitlizer find an assignment's match among a set of entitlements.
     * It's a better alternative than the O(n^2) approach of brute forcing through a nested pair of loops.
     * @author Bernie Margolis
     */
    private class AssignmentMatcher {
        private Set<EntitlementKey> assignedEntitlements = new HashSet<EntitlementKey>();

        AssignmentMatcher(List<AttributeAssignment> assignmentList) {
            for (AttributeAssignment assignment : Util.iterate(assignmentList)) {
                assignedEntitlements.add(new EntitlementKey(assignment));
            }
        }

        boolean found(IdentityEntitlement entitlement) {
            EntitlementKey key = new EntitlementKey(entitlement);
            return assignedEntitlements.contains(key);
        }
    }
}
