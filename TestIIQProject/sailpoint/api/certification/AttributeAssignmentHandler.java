/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationEntitlizer;
import sailpoint.api.Differencer;
import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;

/**
 * Handles all things AttributeAssignment. Typical usage of this class involves creating the handler, preparing,
 * computing then updating the identity with the internal representation of the AttributeAssignments.  Most of 
 * this was refactored away from Certificationer in an effort to encapsulate AttributeAssignment logic into this
 * helper class.
 * @author chris.annino
 *
 */
public class AttributeAssignmentHandler {
    
    SailPointContext _context;
    AssignmentInfo info = new AssignmentInfo();
    
    private CertificationEntitlizer entitlizer;
    private IdentityService idService;
    private Log log = LogFactory.getLog(AttributeAssignmentHandler.class);
    
    ///////////////////////////////////////////////////////////////////////////
    //
    //  Attribute Assignment
    //
    /////////////////////////////////////////////////////////////////////////
    
    /**
     * Utility class that keeps track of the assignments we have to make
     * based on the certification entity.
     * 
     * There are four lists total kept on this calass
     * 
     *  1) one for removes  
     *  2) one for adds
     *  3) one for the current items adds
     *  4) one for the current items removes
     * 
     * The last two are cleared as we go so when we adorn this
     * data to the IdentityEntitlements it can refresh the cert
     * items assignment flag.
     * 
     */
    private class AssignmentInfo {
        List<AttributeAssignment> _currentAdds;
        List<AttributeAssignment> _currentRemoves;
        
        List<AttributeAssignment> _removes;
        List<AttributeAssignment> _adds;
        
        boolean _enabled;
        
        /**
         * All callers must call prepare(Certification cert) or prepare(PolicyViolation violation)
         */
        public AssignmentInfo() {
            reset();
            _removes = null;
            _adds = null;
            _currentAdds = null;
            _currentRemoves = null;
            _enabled = false;
        }     
    
        public void prepare(Certification cert) throws GeneralException {            
            CertificationDefinition def = cert.getCertificationDefinition(_context);
            if ( def != null && def.isUpdateAttributeAssignments() ) {
                _enabled = true;
            }
            init();
        }
        
        @SuppressWarnings("unused") 
        // PolicyViolation and GeneralException are unused, however for consistency with prepare(cert) let's make the API similar 
        public void prepare(PolicyViolation violation) throws GeneralException {
            // always enable these features for violations 
            _enabled = true;
            init();
        }
        
        private void init() {
            reset();
            _removes = new ArrayList<AttributeAssignment>();
            _adds = new ArrayList<AttributeAssignment>();
        }
        
        public void reset() {            
            
            _currentAdds = new ArrayList<AttributeAssignment>();            
            _currentRemoves = new ArrayList<AttributeAssignment>();
        }
        
        public boolean enabled() {
            return _enabled;
        }
        
        public void add(AttributeAssignment assignment) {
            
            _adds.add(assignment);
            _currentAdds.add(assignment);
        }
        
        public void remove(AttributeAssignment assignment) {
            
            _removes.add(assignment);
            _currentRemoves.add(assignment);
        }
        
        public List<AttributeAssignment> getCurrentAdds() {
            return _currentAdds;
        }
        
        public List<AttributeAssignment> getCurrentRemoves() {
            return _currentRemoves;
        }
        
        public List<AttributeAssignment> getAdds() {
            return _adds;
        }
        
        public List<AttributeAssignment> getRemoves() {
            return _removes;
        }
    }
    
    public AttributeAssignmentHandler(SailPointContext context) {
        _context = context;
        entitlizer = new CertificationEntitlizer(context);
        idService = new IdentityService(context);
    }
    
    /**
     * @return if the certification flag "Update Attribute Assignments" is enabled.
     */
    public boolean enabled() {
        return info.enabled();
    }
    
    /***
     * Initialize the "enable" flag based on the definition.
     * 
     * All callers must call this method.
     * 
     * @param cert 
     * @throws GeneralException
     */
    public void prepare(Certification cert) throws GeneralException {
        info.prepare(cert);
        entitlizer.prepare(cert);
    }
    
    /**
     * Initialize the "enable" flag based on the violation.
     * 
     * All callers must call this method.
     * 
     * @param violation
     * @throws GeneralException
     */
    public void prepare(PolicyViolation violation) throws GeneralException {
        info = new AssignmentInfo();
        info.prepare(violation);
    }
    
    /**
     * Reset the internal representation of current AttributeAssignment adds and removes. This 
     * should be called when in a loop and some processing of getCurrentAdds and getCurrentRemoves
     * has occurred. 
     */
    public void reset() {
        info.reset();
    }
    
    /**
     * @return the list of current attribute assignments to add to the identity
     */
    public List<AttributeAssignment> getCurrentAdds() {
        return info.getCurrentAdds();
    }
    
    /**
     * @return the list of current attribute assignments to remove from the identity
     */
    public List<AttributeAssignment> getCurrentRemoves() {
        return info.getCurrentRemoves();
    }

    /**
     * Add and subtract the assignments we have computed as we
     * went through the certification items.
     * 
     * @param identity
     */
    public void updateAssignments(Identity identity) {
    
        if ( identity != null ) {
                List<AttributeAssignment> adds = info.getAdds();
                List<AttributeAssignment> removes = info.getRemoves();
                
                if ( Util.size(adds) > 0 ) {
                    for ( AttributeAssignment assig : adds ) {
                        identity.add(assig);
                    }
                }
                if ( Util.size(removes) > 0 ) {
                    for ( AttributeAssignment assig : removes ) {
                        identity.remove(assig);
                    }
                }
            }
    }

    /**
     * If enabled at the certification definition level for each
     * approved item add an assignment and for things revoked
     * remove the assignments. Accumulate them into list to 
     * avoid having to lock the identity more then one time.
     * 
     * It is the caller's responsibility to check if the feature 
     * is enabled before invoking computeAssignment.  This class will
     * not actually change anything related to the Identity until updateAssignments.
     * 
     * @param item
     * @throws GeneralException
     */
    public void computeAssignment(CertificationItem item)
            throws GeneralException { 
    
        if ( item != null ) {
            
            // add will handle merge and remove will tolerate non-existing
            CertificationAction action = item.getAction();
            CertificationAction.Status status = ( action != null ) ? action.getStatus() : null; 
            String assignerName = (action != null ) ? action.getActorDisplayName() : null;
            
            boolean add = false;
            boolean remove = false;
            if ( Util.nullSafeEq(status, CertificationAction.Status.Approved) ) {                
                add = true;
            } else 
            if ( Util.nullSafeEq(status, CertificationAction.Status.RevokeAccount) || 
                 Util.nullSafeEq(status, CertificationAction.Status.Remediated)  ) {
                remove = true;
            }
            
            // All we care about here are exceptions, roles have an alternate
            // assignment model
            if ( Util.nullSafeEq(item.getType(), CertificationItem.Type.Exception) 
                    || Util.nullSafeEq(item.getType(), CertificationItem.Type.DataOwner) ) {
                EntitlementSnapshot snapshot = item.getExceptionEntitlements();
                if ( snapshot != null ) {
                    Attributes<String,Object> attrs = snapshot.getAttributes();
                    if ( attrs != null ) {
                        Iterator<String> keys = attrs.keySet().iterator();
                        if ( keys != null ) {
                            while ( keys.hasNext() ) {
                                String key = keys.next();
                                if ( key != null ) {
                                    Object val = attrs.get(key);
                                    if ( val == null ) 
                                        continue;                                    
                                    AttributeAssignment assignment = createAssignment(snapshot, key, val, assignerName, false);
                                    if ( add ) {
                                        info.add(assignment);
                                    } else
                                    if ( remove ) {
                                        info.remove(assignment);
                                    }
                                }
                            }
                        }   
                    }
                    
                    List<Permission> perms = snapshot.getPermissions();
                    if ( perms != null ) {
                        for ( Permission perm : perms ) {
                            String target = perm.getTarget();
                            List<String> rights = perm.getRightsList();
                            if ( rights != null ) {                                
                                for ( String right: rights ) {
                                    AttributeAssignment assignment = createAssignment(snapshot, target, right, assignerName, true);
                                    if ( add ) {
                                        info.add(assignment);
                                    } else
                                    if ( remove ) {
                                        info.remove(assignment);
                                    }
                                }
                            }
                        }                               
                    }
                }
            }                        
        }        
    }

    /**
     * This method removes AttributeAssignment objects from the Identity based on the certification action.
     * @param entity
     * @throws GeneralException
     * @see {@link #computeAssignment(CertificationItem)}
     */
    public void revoke(CertificationEntity entity) 
            throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("attempting to revoke on entity: " + entity.toString());
        }

        // If this is a DataOwner CertificationEntity type, then we need to process each of the
        // CertificationItems individually instead of bunching them together.  This is because
        // each item could be on a different identity.
        if (CertificationEntity.Type.DataOwner.equals(entity.getType())) {
            List<CertificationItem> items = entity.getItems();
            for (CertificationItem subItem : Util.safeIterable(items)) {
                CertificationAction action = subItem.getAction();
                // phase refresh occurs, or refreshItem, and begins remediation
                // before this method is called. ensure that remediation is ready before revoking assignment
                if (log.isDebugEnabled()) {
                   log.debug("item: " + subItem.toString() + " action is ready for remediation: " + ((action != null) ? action.isReadyForRemediation() : null));
                }
                if ( action != null && action.isReadyForRemediation()) {
                    computeAssignment(subItem);
                    // we are only interested in the removes, pass null for current adds
                    if (log.isDebugEnabled()) {
                        log.debug("current removes: " + info.getCurrentRemoves());
                    }
                    entitlizer.setCurrent(entity, subItem, null, info.getCurrentRemoves());
                    info.reset();
                }

                // ensure we aren't adding attributeAssignments
                if ( !Util.isEmpty(info.getAdds()) ) {
                    info.getAdds().clear();
                }
                // update the identity but do not perform a commit
                Identity ident = _context.getObjectByName(Identity.class, subItem.getTargetName());
                if (ident != null) {
                    updateAssignments(ident);
                }
            }
        } else {
            List<CertificationItem> items = entity.getItems();
            for (CertificationItem subItem : Util.safeIterable(items)) {
                CertificationAction action = subItem.getAction();
                // phase refresh occurs, or refreshItem, and begins remediation
                // before this method is called. ensure that remediation is ready before revoking assignment
                if (log.isDebugEnabled()) {
                    log.debug("item: " + subItem.toString() + " action is ready for remediation: " + ((action != null) ? action.isReadyForRemediation() : null));
                }
                if ( action != null && action.isReadyForRemediation()) {
                    computeAssignment(subItem);
                    // we are only interested in the removes, pass null for current adds
                    if (log.isDebugEnabled()) {
                        log.debug("current removes: " + info.getCurrentRemoves());
                    }
                    entitlizer.setCurrent(entity, subItem, null, info.getCurrentRemoves());
                    info.reset();
                }
            }

            // ensure we aren't adding attributeAssignments
            if ( !Util.isEmpty(info.getAdds()) ) {
                info.getAdds().clear();
            }
            // update the identity but do not perform a commit
            if (CertificationEntity.Type.Identity.equals(entity.getType())) {
                Identity ident = _context.getObjectByName(Identity.class, entity.getIdentity());
                if (ident != null) {
                    updateAssignments(ident);
                }
            }
        }
    }

    /**
     * Removes the appropriate attribute assignment based on the violation.
     *
     * You must call prepare to enable the handler.
     * @param violation
     * @param actor
     * @throws GeneralException
     */
    public void revoke(PolicyViolation violation, Identity actor) throws GeneralException {
        if ( violation == null ) return;
        
        List<PolicyTreeNode> nodes = violation.getEntitlementsToRemediate();
        for (PolicyTreeNode aNode : Util.safeIterable(nodes)) {
            AttributeAssignment assignment = createAssignment(violation, aNode, aNode.getName(), aNode.getValue(), actor, aNode.isPermission());
            info.remove(assignment);
            info.reset();
        }
        
        updateAssignments(violation.getIdentity());
    }

    /**
     * Build an assignment from the entitlement snapshot. 
     *
     * @param snapshot
     * @param attrName
     * @param val
     * @param assignerName
     * @return
     * @throws GeneralException
     */
    private AttributeAssignment createAssignment(EntitlementSnapshot snapshot, String attrName, Object val, String assignerName, boolean isPermission) 
        throws GeneralException {
        
        return new AttributeAssignment(snapshot.resolveApplication(_context), snapshot.getNativeIdentity(), 
                                       snapshot.getInstance(), attrName, val, assignerName, 
                                       Source.Certification, 
                                       (isPermission ) ? ManagedAttribute.Type.Permission : ManagedAttribute.Type.Entitlement );
    }
    
    /**
     * Build an assignment from the violation and violation node.
     * 
     * @param violation
     * @param node
     * @param attrName
     * @param val
     * @param assigner
     * @param isPermission
     * @return
     * @throws GeneralException
     */
    private AttributeAssignment createAssignment(PolicyViolation violation, PolicyTreeNode node, String attrName, Object val, Identity assigner, boolean isPermission) 
            throws GeneralException {


        if (PolicyTreeNode.TYPE_TARGET_SOURCE.equals(node.getSourceType())) {
            throw new GeneralException("Cannot create assignment from TargetSource");
        }

        String applicationName = node.getApplication();
        String nativeIdentity = null;
        // pulled this from RemediationCalculator, not really sure why we are checking applicationName
        // existence, then setting the native identity if it's not null. Imagine it has something to do with the 
        // application name check in the if block.
        if( applicationName == null ) {
            applicationName = ProvisioningPlan.APP_IIQ;
        } else {
            RemediationCalculator calc = new RemediationCalculator(_context);
            nativeIdentity = calc.getNativeIdentity(node, violation);
        }
        
        String instance = null;
        Application app = null;
        if (!ProvisioningPlan.APP_IIQ.equals(applicationName)) {
            
            app = _context.getObjectByName(Application.class, applicationName);
            if (null != app) {
                List<Link> links = idService.getLinks(violation.getIdentity(), app);
                
                for (Link link : Util.safeIterable(links)) {

                    Object attrValue = link.getAttribute(node.getName());
                    String linkNativeIdentity = link.getNativeIdentity();
                    if (Differencer.objectsEqualOrContains(node.getValue(), attrValue, false) &&
                            Differencer.equal(nativeIdentity, linkNativeIdentity)) {
                        // this link applies to this node
                        instance = link.getInstance();

                        if (log.isDebugEnabled()) {
                            log.debug("Found instance: " + instance + " for nativeIdentity: " + linkNativeIdentity);
                        }
                        
                        break;
                    }

                }
            }
        }
        String assignerName = ( assigner != null ) ? assigner.getName() : null;
        
        return new AttributeAssignment(app, nativeIdentity, 
                instance, attrName, val, assignerName, 
                Source.PolicyViolation, 
                (isPermission ) ? ManagedAttribute.Type.Permission : ManagedAttribute.Type.Entitlement );
    }

}
