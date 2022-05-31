/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating various maintenance of the IdentityEntitlement
 * objects added in 6.0 specifically around Role IdentityEntitlements.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Entitlizer.RoleData;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Difference;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleRequest;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * 
 * An API class that handles the create/update/delete of 
 * IdentityEntitlement objects as they pertain to
 * Role assignment and detection.
 * Promote detected and assigned roles into the entitlements
 * table. 
 *      
 * While doing the promotion of assignable roles this method will also 
 * go through and mark any entitlements granted by a role as indirect 
 * ( grantedByRole = true) and annotate the details 
 * granting role name. 
 *      
 * For each entitlement we update up to four calculated values :
 *      
 *    On entitlements:
 *    
 *      grantedByRole : Flag to indicate when a entitlement has ( stored in a db column for queriablity )
 *      sourceAssignableRoles : List of assignable role names that  ( stored in xml ) 
 *      sourceDetetableRoles : List of detected roles that grant an entitlement ( stored in xml )
 *       
 *    On IT Roles entitlements :  
 *      
 *      allowed:  Flag to indicates when one ore more assigned roles allows
 *                an IT role either as part of the permits or required list. *
 *  
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * 
 * 
 * Meter range 100-106
 * 
 * @since 6.0
 *
 */
public class RoleEntitlizer extends AbstractEntitlizer {
    
    private static Log log = LogFactory.getLog(RoleEntitlizer.class);

    /**
     * Argument put into the result ( if saveResults ) is called to indicate
     * how many entitlements were created.
     */
    public static final String RET_CREATED_ASS = "identityEntitlementsRoleAssignmentsCreates";
    public static final String RET_REMOVED_ASS = "identityEntitlementsRoleAssignmentsRemoves";
    public static final String RET_UPDATED_ASS = "identityEntitlementsRoleAssignmentsUpdates";
    
    public static final String RET_CREATED_DET = "identityEntitlementsRoleDetectionsCreates";
    public static final String RET_REMOVED_DET = "identityEntitlementsRoleDetectionsRemoves";
    public static final String RET_UPDATED_DET = "identityEntitlementsRoleDetectionsUpdates";
    
    /**
     * Argument put into the result ( if savedResults) is called to indicate
     * how many indirect entitlement relationships were updated.
     */
    public static final String RET_INDIRECT = "identityEntitlementsIndirectUpdates";
    public static final String RET_INDIRECT_PERMS = "identityEntitlementsIndirectPermissionUpdates";    
    
    /**
     * Flag that tells the RoleEntitlizer to optimize by comparting the names
     * of the roles before doing any promotion.
     */
    public static final String ARG_OPTIMIZE_IDENTITY_ENTITLEMENT_ROLE_PROMOTION = 
        "optimizeIdentityEntitlementRolePromotion";
    
    /* 
     * Statistics kept to help us understand what was changed.
     */
    int _totalRoleAssignmentCreates;  
    int _totalRoleAssignmentDeletes;
    int _totalRoleAssignmentUpdates;
    
    int _totalRoleDetectionCreates;
    int _totalRoleDetectionDeletes;
    int _totalRoleDetectionUpdates;
    
    int _totalIndirectUpdates;
    int _totalIndirectPermissionUpdates;
        
    /**
     * Flag that can be set to force the promotion of the entitlements
     * regardless of the differences we detect.
     */
    private boolean _optimizePromotion;
        
    /**
     * A copy of the entitlement mapping that comes from the
     * entitlement correlator for each identity.
     * 
     * The data from the entitlement correlator is normalized
     * so we can use it easly when dealing with entitlements.
     * 
     * The key is formated with a string :
     *    appName/nativeIdentity/instance/name/value
     *    
     * We use this mapping during Link entitlement promotion to
     * adorn role data to the link when possible.  Here in the
     * RoelEntitlizer we check with the role data to prevent
     * duplication of the promotion. 
     * 
     */
    private Map<String,RoleData> _linktimeRoleData;
    
    /**
     * List of entitlements that will be removed during the next
     * commit.  We keep these here so that we can augment the
     * indirect list with changes that are pending.
     * 
     * These are only set when we are promoting Link data at the
     * same time we are also promoting role data and indirect
     * entitlement data.
     */
    List<IdentityEntitlement> _toBeRemoved;
    
    /**
     * Just liek the _toBeRemoved member, this gives us
     * the pending changes that will be persisted.
     * 
     * These are only set when we are promoting Link data at the
     * same time we are also promoting role data and indirect
     * entitlement data.
     */
    List<IdentityEntitlement> _toBeSaved;
            
    public RoleEntitlizer(SailPointContext context, Attributes<String,Object> args) {
        super(context, args);
        _totalRoleAssignmentCreates = 0;
        _totalRoleAssignmentDeletes = 0;
        _totalRoleAssignmentUpdates = 0;
        
        _totalRoleDetectionCreates = 0;
        _totalRoleDetectionDeletes = 0;
        _totalRoleDetectionUpdates = 0;
        
        _totalIndirectUpdates = 0; 
        _totalIndirectPermissionUpdates = 0;
        
        _optimizePromotion = Util.getBoolean(args, ARG_OPTIMIZE_IDENTITY_ENTITLEMENT_ROLE_PROMOTION);
    }

    public void setRoleData(Map<String, RoleData> roleDataMap) {
        _linktimeRoleData = roleDataMap;        
    }
    
    public void setPendingSave(List<IdentityEntitlement> pendingSave) {
        _toBeSaved = pendingSave;
    }
    
    public void setPendingRemoval(List<IdentityEntitlement> pendingDelete) {
        _toBeRemoved = pendingDelete;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Role Assignment Promotion
    //
    //////////////////////////////////////////////////////////////////////////

    private static final String METER_PREFIX = "   RoleEntitlizer --";
    
    /**
     * Build an entitlement that represents each of the assigned
     * roles. Fetch the current list of assigned role entitlements
     * so we are able to update and prune the ones no longer
     * assigned.
     * 
     * @param identity
     */
    public void promoteRoleAssignments(Identity identity) 
        throws GeneralException {

        // only allow optimized promotion if all role assignments have an assignment id
        if (_optimizePromotion && !hasNullAssignmentIds(identity.getRoleAssignments())) {
            Difference diff = diffRoles(identity, true);
            if ( diff == null ) {
                if ( log.isDebugEnabled() ) {
                    String name = identity.getName();
                    log.debug("Skipping role assignment promotion for ["+name+"] because they are in sync.");
                }
            } else {
                List<String> adds = diff.getAddedValues();

                Meter.enter(101, METER_PREFIX+ " assignment adds");

                for (String add : Util.safeIterable(adds)) {
                    IdentityEntitlement ent = new IdentityEntitlement();
                    ent.setIdentity(identity);

                    RoleAssignment assignment = identity.getRoleAssignmentById(add);

                    EntitlementUpdater updater = new EntitlementUpdater(ent);
                    updater.setRoleAssignmentDetails(assignment);

                    _context.saveObject(ent);
                    _totalRoleAssignmentCreates++;
                }
                
                Meter.exit(101);
                Meter.enter(102, METER_PREFIX +" assignment removes");

                List<String> removes = diff.getRemovedValues();                
                if (removes != null) {
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("identity", identity));
                    ops.add(Filter.and(Filter.ignoreCase(Filter.eq("name", ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)), 
                    		           buildValueFilter(removes, "assignmentId")));
                    _context.removeObjects(IdentityEntitlement.class, ops);
                    _totalRoleAssignmentDeletes++;
                } 
                Meter.exit(102);
            }
        } else {
            Meter.enter(104, METER_PREFIX + " refresh all role assignments.");
            refreshAllRoleAssignmentEntitlements(identity);
            Meter.exit(104);
        }        
    }

    /**
     * Determines if any of the role assignments has a null or empty assignment id.
     * @param roleAssignments The role assignments.
     * @return True if at least one role assignment has a null or empty assignment id, false otherwise.
     */
    private boolean hasNullAssignmentIds(List<RoleAssignment> roleAssignments) {
        for (RoleAssignment roleAssignment : Util.safeIterable(roleAssignments)) {
            if (Util.isNullOrEmpty(roleAssignment.getAssignmentId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Iterate over all of the current assignment and update the 
     * IdentityEntitlementregardless if they have changed.
     * 
     * @param identity
     * @throws GeneralException
     */
    private void refreshAllRoleAssignmentEntitlements(Identity identity) throws GeneralException {
        List<IdentityEntitlement> currentRoleEntitlements = getAssignedRolesEntitlements(identity);

        List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
        if (!Util.isEmpty(roleAssignments))  {
            // pre-process in case list is large
            Map<String, IdentityEntitlement> idEntitlementMap = generateIdEntitlementMap(
                currentRoleEntitlements,
                roleAssignments
            );

            for (RoleAssignment assignment : roleAssignments) {
                // skip negative assignments
                if (assignment == null || assignment.isNegative()) {
                    continue;
                }

                ensureAssignmentId(assignment);

                IdentityEntitlement roleEntitlement;

                // iiqtc-108 - In instances where role assignments share an assignment id using
                // just the assignment id is not enough to uniquely identify it's associated
                // identity entitlement. The key also contains the role name.
                String key = generateIdEntitlementMapKey(assignment.getAssignmentId(), assignment.getRoleName());

                if (idEntitlementMap.containsKey(key)) {
                    roleEntitlement = idEntitlementMap.get(key);

                    _totalRoleAssignmentUpdates++;
                } else {
                    // this is a new assignment id so create a new entitlement,
                    // the old one will be cleaned up later
                    roleEntitlement = new IdentityEntitlement();
                    roleEntitlement.setIdentity(identity);

                    _totalRoleAssignmentCreates++;
                }

                // mark that we visited this entitlement so that it is not removed
                roleEntitlement.setFoundInFeed(true);

                EntitlementUpdater updater = new EntitlementUpdater(roleEntitlement);
                updater.setRoleAssignmentDetails(assignment);

                if (updater.hasUpdates()) {
                    _context.saveObject(roleEntitlement);
                }
            }
        }

        // since we did not set foundInFeed to true for identity entitlements without
        // an assignment id they will be removed here
        removeMissingRoleEntitlements(currentRoleEntitlements, true);
    }

    /**
     * Generate a unique key for the id entitlement map that includes assignmentId and roleName
     * of the role assignment
     */
    private String generateIdEntitlementMapKey(String assignmentId, String roleName) {
        return assignmentId + "_" + roleName;
    }

    /**
     * Generates a map from a role assignment's assignment id to the identity entitlement
     * which represents that assignment.
     * @param entitlements The current identity entitlements.
     * @param assignments The role assignments.
     * @return The map.
     */
    private Map<String, IdentityEntitlement> generateIdEntitlementMap(List<IdentityEntitlement> entitlements, List<RoleAssignment> assignments) {
        Map<String, IdentityEntitlement> idEntitlementMap = new HashMap<String, IdentityEntitlement>();

        for (RoleAssignment assignment : Util.safeIterable(assignments)) {
            // skip assignments with null or empty assignment ids
            if (Util.isNullOrEmpty(assignment.getAssignmentId())) {
                continue;
            }

            for (IdentityEntitlement entitlement : Util.safeIterable(entitlements)) {
                // iiqtc-108 - Role assignments can share the same assignment id and in order
                // for them to be uniquely identtified we also have to compare the role name.
                // The key for each entitlement in the map will be built using the assignment
                // id and the role name.
                if (assignment.getAssignmentId().equals(entitlement.getAssignmentId()) &&
                        assignment.getRoleName().equals(entitlement.getStringValue())) {
                    String key = generateIdEntitlementMapKey(assignment.getAssignmentId(), assignment.getRoleName());
                    idEntitlementMap.put(key, entitlement);
                    break;
                }
            }
        }

        return idEntitlementMap;
    }

    /**
     * Ensures that the role assignment has an assignment id. If not, then
     * one is set.
     * @param roleAssignment The role assignment.
     */
    private void ensureAssignmentId(RoleAssignment roleAssignment) {
        if (Util.isNullOrEmpty(roleAssignment.getAssignmentId())) {
            roleAssignment.setAssignmentId(Util.uuid());
        }
    }
    
    /**
     * Called during aggregation and refresh and is used to 
     * clean up entitlements that have been removed.
     * @param currentList The current list of entitlements
     * @param assigned True if currentList represents assigned roles, false for detected roles.
     * @throws GeneralException
     */    
    private void removeMissingRoleEntitlements(List<IdentityEntitlement> currentList, boolean assigned)
        throws GeneralException {

        for (IdentityEntitlement entitlement : Util.safeIterable(currentList)) {
            if (entitlement != null && !entitlement.foundInFeed()) {
                _context.removeObject(entitlement);

                if (assigned) {
                    _totalRoleAssignmentDeletes++;
                } else {
                    _totalRoleDetectionDeletes++;
                }
            }
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    //  Role Detection Promotion
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Promote any IT (Detectable) Roles for an identity. This including adorning other
     * entitlements with the "indirectly" assigned relationship data.
     * 
     * @param identity The identity.
     * @param entMappings The bundle to entitlement group mappings generated by the correlator.
     * @throws GeneralException
     */    
    public void promoteRoleDetections(Identity identity, Map<Bundle, List<EntitlementGroup>> entMappings)
        throws GeneralException {
        
        if (!roleDetectionUpdateRequired(identity)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping role detection promotion for " + identity.getName() + " because they are in sync.");
            }

            return;            
        }        

        IndirectEntitlementFinder sifter = new IndirectEntitlementFinder(entMappings);
        sifter.setPendingSave(_toBeSaved);
        sifter.setPendingRemoval(_toBeRemoved);
        
        List<IdentityEntitlement> indirect = sifter.getIndirectEntitlements(identity);
        IndirectUpdater indirector = new IndirectUpdater(indirect, entMappings);

        // clear existing role data
        indirector.clearExistingIndirectInformation(_context);
        
        List<IdentityEntitlement> currentDetectionEntitlements = getDetectedRoleEntitlements(identity);
        List<RoleDetection> detections = identity.getRoleDetections();
        List<RoleRequest> roleRequests = identity.getRoleRequests();
        if (!Util.isEmpty(detections)) {
            List<RoleAssignment> assignments = identity.getRoleAssignments();
            for (RoleDetection detection : detections) {
                if (detection == null) {
                    continue;
                }

                IdentityEntitlement ent = findRoleDetectionEntitlement(currentDetectionEntitlements, detection);                
                if (ent == null) {
                    ent = new IdentityEntitlement();
                    ent.setIdentity(identity);

                    _totalRoleDetectionCreates++;
                } else {
                    _totalRoleDetectionUpdates++;
                }                

                ent.setFoundInFeed(true);
                
                EntitlementUpdater updater = new EntitlementUpdater(ent);

                // Try to find a RoleRequest for this role, if exists need it to get the sunrise/sunset dates for the entitlement
                RoleRequest existingRequest = null;
                if (!Util.isEmpty(roleRequests)) {
                    for (RoleRequest roleRequest : roleRequests) {
                        if (roleRequest.getRoleName() != null && roleRequest.getRoleName().equals(detection.getRoleName())) {
                            existingRequest = roleRequest;
                            break;
                        }
                    }
                }

                updater.setRoleDetectionDetails(detection, existingRequest);
                
                List<String> allowedBy = getAllowedBy(assignments, detection);
                if (!Util.isEmpty(allowedBy)) {                    
                    updater.setAllowed(true);
                }

                indirector.updateIndirect(detection, allowedBy);   
                
                if (updater.hasUpdates()) {
                    _context.saveObject(ent);
                }
            }            
        }
        
        // purge any of the roles that weren't updated ( found in feed )
        removeMissingRoleEntitlements(currentDetectionEntitlements, false);
    }

    /**
     * Finds the identity entitlement in the list for the role detection if it exists.
     * @param currentEntitlements The current role detection identity entitlements.
     * @param detection The role detection.
     * @return The identity entitlement or null if not found.
     */
    private IdentityEntitlement findRoleDetectionEntitlement(List<IdentityEntitlement> currentEntitlements, RoleDetection detection) {
        if (detection == null) {
            return null;
        }

        // compare role name and first assignment id
        for (IdentityEntitlement entitlement : Util.iterate(currentEntitlements)) {
            // allow nulls to equal for assignmentId
            if (Util.nullSafeEq(entitlement.getAssignmentId(), detection.getFirstAssignmentId(), true) &&
                Util.nullSafeEq(entitlement.getStringValue(), detection.getRoleName())) {
                return entitlement;
            }
        }

        return null;
    }

    /**
     * Because we have to update entitlements granted by a
     * detected role be careful when updating the entitlements
     * and only do it when it looks like our role data in
     * the entitlements table is out of date.
     * 
     * @param identity The identity.
     * @return True when required, false if not necessary
     * @throws GeneralException
     */
    private boolean roleDetectionUpdateRequired(Identity identity) 
        throws GeneralException {
        
        if (!_optimizePromotion) {
             return true;
        }               

        return diffRoles(identity, false) != null;
    }
     
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////////
     
    /**
      * Diff the list of assignments/detections stored on the identiy
      * versus the ones stored in the entiltlement table.
      * 
      * @param identity The identity.
      * @param assigned True if diffing assigned roles, false for detected.
      * @return The different.
      * @throws GeneralException
      */
    private Difference diffRoles(Identity identity, boolean assigned) 
        throws GeneralException {        
         
        Meter.enter(100, METER_PREFIX + " diffRoles");

        String nameColumn = assigned ? ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES :
                                       ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
         
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        ops.add(Filter.ignoreCase(Filter.eq("name", nameColumn)));

        // for detections uniqueness is determined by assignmentId and value
        // so use a pair and do same for assignments as it wont hurt anything
        List<Pair<String, String>> currentValues = new ArrayList<Pair<String, String>>();
        if (assigned) {
            List<RoleAssignment> assignments = identity.getRoleAssignments();
            for (RoleAssignment assignment : Util.iterate(assignments)) {
                String assignmentId = assignment.getAssignmentId();
                if (!Util.isNullOrEmpty(assignmentId) && !assignment.isNegative() ) {
                    currentValues.add(Pair.make(assignmentId, assignment.getRoleName()));
                }
            }
        } else { 
            List<RoleDetection> detections = identity.getRoleDetections();
            for (RoleDetection detection : Util.iterate(detections)) {
                currentValues.add(Pair.make(detection.getFirstAssignmentId(), detection.getRoleName()));
            }
        }
        
        // check if there is anything to do
        if (Util.isEmpty(currentValues)) {
            int count = _context.countObjects(IdentityEntitlement.class, ops);
            if (count == 0) {            
                return null;
            } 
        } 
         
        List<Pair<String, String>> stored = new ArrayList<Pair<String, String>>();

        List<String> projectionCols = Arrays.asList("assignmentId", "value");
        Iterator<Object[]> rows = _context.search(IdentityEntitlement.class, ops, projectionCols);
        while (rows != null && rows.hasNext()) {
            Object[] row = rows.next();
            
            String assignmentId = (String) row[0];
            String value = (String) row[1];

            stored.add(Pair.make(assignmentId, value));
        }
         
        Difference d = Difference.diff(stored, currentValues);

        Meter.exit(100);

        return d;        
    }
    
    /**
     * Resolve the correlationRole based on the detection so we can 
     * determine the entitlements that were granted as part of 
     * the detected role.
     * 
     * @param detection
     * @param entMapping
     * @return List<EntitlementGroups> that matched when this role was detected
     * 
     * @throws GeneralException
     */
    private List<EntitlementGroup> getEntitlementGroups(RoleDetection detection, 
                                                        Map<Bundle, List<EntitlementGroup>> entMapping)
        throws GeneralException {
        
        String roleId = detection.getRoleId();
        if (!Util.isNullOrEmpty(roleId)) {
            Bundle bundle = detection.getRoleObject(_context);
            if (bundle != null) {
                return entMapping.get(bundle);
            }
        }

        return null;
    }    
   
    /**
     * Merge the entitlement groups down to a single group per account/instance.
     * 
     * Well use the merged groups to query back to db for the indirect
     * permissions/entitlement from the IdentityEntitlements table.
     * 
     * @param groups
     * @param in
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void merge(List<EntitlementGroup> groups, EntitlementGroup in) {
        
        if ( in == null || in.isAccountOnly() )
            return;
        
        // Copy the group since we are adding stuff to it
        EntitlementGroup incomingGroup = (EntitlementGroup) XMLObjectFactory.getInstance().clone(in, _context);
        if ( Util.size(groups) == 0 )  {
            groups.add(incomingGroup);
        } else {            
            EntitlementGroup found = findGroup(groups, in);
            if ( found == null ) {
                // add it if not found
                groups.add(incomingGroup);
            } else {
                //
                // We have a match with an existing group, merge the attrs and permissions
                // into a single group to help us build an indirect query
                //
                List<String> inAttrNames = incomingGroup.getAttributeNames();                        
                if ( Util.size(inAttrNames) > 0 ) {
                    Attributes<String,Object> inAttrs = incomingGroup.getAttributes();
                    Attributes<String,Object> currentAttrs = found.getAttributes();
                    if ( currentAttrs == null ) {
                         currentAttrs = new Attributes<String,Object>();
                         found.setAttributes(currentAttrs);
                    }

                    for (String attrName : inAttrNames ) {
                        Object o = inAttrs.get(attrName);
                        Object existing = currentAttrs.get(attrName);
                        if (o != null && existing != null ) {
                            List existingList = Util.asList(existing);
                            List neuList = Util.asList(o);
                            if ( Util.size(existingList) > 0 && Util.size(neuList) > 0 ) {
                                for ( Object nueItem : neuList)  {
                                    if ( nueItem != null) {
                                        if ( !existingList.contains(nueItem) ) {
                                            existingList.add(nueItem);
                                        }
                                    }
                                }
                            }
                            currentAttrs.put(attrName,existingList);                                        
                        } else 
                            if ( o != null && existing == null ) {
                                currentAttrs.put(attrName, o);
                            }
                    }
                    found.setAttributes(currentAttrs);
                }
                
                List<Permission> inPerms = incomingGroup.getPermissions();
                if ( Util.size(inPerms) > 0 ) {
                    List<Permission> merged = ObjectUtil.mergePermissions(found.getPermissions(), inPerms);
                    found.setPermissions(merged);
                }                
            }            
        }
    }
    
    private EntitlementGroup findGroup(List<EntitlementGroup> groups, EntitlementGroup incomingGroup) {        
        for ( EntitlementGroup group : groups ) {
            if ( group == null ) continue;
            // if it matches the current group, merge
            if ( Util.nullSafeCompareTo(group.getApplicationName(), incomingGroup.getApplicationName()) == 0 &&
                 Util.nullSafeCompareTo(group.getInstance(), incomingGroup.getInstance()) == 0 &&
                 Util.nullSafeCompareTo(group.getNativeIdentity(), incomingGroup.getNativeIdentity() )  == 0 ) {
                return group;
            }
        }
        return null;
    }
    /**
     * Called during aggregation to populate the results
     * with some stats to help understand what we ended
     * up doing.
     * 
     * @param result
     */
    public void saveResults(TaskResult result) {
       result.setInt(RET_CREATED_ASS, _totalRoleAssignmentCreates);
       result.setInt(RET_REMOVED_ASS, _totalRoleAssignmentDeletes);
       result.setInt(RET_UPDATED_ASS, _totalRoleAssignmentUpdates);
       
       result.setInt(RET_CREATED_DET, _totalRoleDetectionCreates);
       result.setInt(RET_REMOVED_DET, _totalRoleDetectionDeletes);
       result.setInt(RET_UPDATED_DET, _totalRoleDetectionUpdates);
       
       result.setInt(RET_INDIRECT, _totalIndirectUpdates);
       result.setInt(RET_INDIRECT_PERMS, _totalIndirectPermissionUpdates);
    }
   
    public void traceStatistics() {
       System.out.println(_totalRoleAssignmentCreates + " Role Assignment Entitlements Created.");   
       System.out.println(_totalRoleAssignmentDeletes  + " Role Assignment Entitlements Removed.");       
       System.out.println(_totalIndirectPermissionUpdates  + " Role Assignment Entitlements Updated.");
       
       System.out.println(_totalRoleDetectionCreates + " Role Detection Entitlements Created.");   
       System.out.println(_totalRoleDetectionDeletes  + " Role Detection Entitlements Removed.");       
       System.out.println(_totalRoleDetectionUpdates  + " Role Detection Entitlements Updated.");
       
       System.out.println(_totalIndirectUpdates + " Role Indirect Attribute Updates."); 
       System.out.println(_totalIndirectPermissionUpdates + " Role Indirect Permission Updates.");
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // IndirectEntitlementFinder
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * Helper class that finds any indirect entitlements (entitlements
     * granted by a role) so they can be adorned with the role 
     * information.
     */
    private class IndirectEntitlementFinder {
        
        /**
         * Handle to the identity we are querying.
         */
        Identity _identity = null;
        
        /**
         * Maping built up during entitlement correaltion by the 
         * CorrelationModel.
         */
        Map<Bundle, List<EntitlementGroup>> _entMappings;

        /**
         * List of entitlements that are pending save, either because they are
         * new or because they have been updated. 
         * 
         * NOTE: This field is useful when entitlement promotion is happening during 
         * the same transaction as role data is bring promoted.
         */
        List<IdentityEntitlement> _pendingSave;
        
        /**
         * List of entitlements that are pending removal.
         * 
         * NOTE: This field is useful when entitlement promotion is happening during 
         * the same transaction as role data is bring promoted.
         */
        List<IdentityEntitlement> _pendingRemoval;
        
        public IndirectEntitlementFinder(Map<Bundle, List<EntitlementGroup>> entMappings) {
           _entMappings = entMappings;
        }        

        public void setPendingRemoval(List<IdentityEntitlement> pendingRemoval) {
            _pendingRemoval = pendingRemoval;        
        }
        
        public void setPendingSave(List<IdentityEntitlement> pendingSave) {
            _pendingSave = pendingSave;   
        }    
        
        public List<IdentityEntitlement> getIndirectEntitlements(Identity identity) throws GeneralException {
            Meter.enter(105, METER_PREFIX+ " getIndirectEntitlements", (identity != null) ? identity.getId() : null);
            _identity = identity;
            Set<QueryOptions> opsSet = buildOptionsSet();

            // Gather the queried entitlements into a set to avoid duplicates.
            Set<IdentityEntitlement> entitlementsSet = new HashSet<IdentityEntitlement>();

            for (QueryOptions ops : opsSet) {
                if ( ops != null ) {
                    try {
                        entitlementsSet.addAll(getEntitlements(ops));
                    } catch (GeneralException e) {
                        if (log.isErrorEnabled()) {
                            List<Filter> filters = ops.getFilters();
                            if (Util.isEmpty(filters)) {
                                // This should never happen
                                log.error("Returning all IdentityEntitlements because no filters were specified.");
                            } else {
                                log.error("Failed to fetch entitlements using the following filter:" );
                                if (filters.size() > 1) {
                                    log.error(Filter.and(filters).getExpression(true));
                                } else {
                                    log.error(filters.get(0).getExpression(true));
                                }
                                log.error("Exception was " + e.getMessage(), e);
                            }
                        }
                        throw e;
                    }
                }
            }

            updateWithPendingData(entitlementsSet);

            // Make a list of the entitlements to adhere to this method's contract.
            List<IdentityEntitlement> entitlements = new ArrayList<IdentityEntitlement>();
            entitlements.addAll(entitlementsSet);

            Meter.exit(105);

            // The original behavior was to return null when there were no entitlements, so retain that behavior to avoid
            // regressing
            if (Util.isEmpty(entitlements)) {
                entitlements = null;
            }

            return entitlements;
        }
        
        private List<EntitlementGroup> getEntitlementGroupsForDetectedRoles(List<RoleDetection> roleDetections) throws GeneralException {
            List<EntitlementGroup> mergedEntitlementGroups = new ArrayList<EntitlementGroup>();
            if ( roleDetections != null && _entMappings != null ) {
                //
                // Flatten out all of the entitlement groups from all detections
                // so we get back a single list of entitlements groups
                // one per application/instance/native id
                //
                for ( RoleDetection detection : roleDetections ) {
                    List<EntitlementGroup> egroups = getEntitlementGroups(detection, _entMappings);
                    if ( egroups != null ) {
                        for (EntitlementGroup group : egroups ) {
                            merge(mergedEntitlementGroups, group);                                
                        }
                    }
                }
            }

            return mergedEntitlementGroups;
        }
        
        /**
         * Apply the queried indirect entitlements that are pending save and that are marked for deletion.
         *
         * @param entitlements
         * 
         * @return List of the revised indirect entitlements
         */
        private void updateWithPendingData(Set<IdentityEntitlement> entitlements) {
            // Filter out the removes
            entitlements.removeAll(_pendingRemoval);

            // Add the saves
            entitlements.addAll(_pendingSave);
        }
        
        /**
         * Build options that will allow us to Filter the entitlements
         * that are indirectly granted by a role.
         * 
         * This method will return null when there is nothing to query.
         * 
         * @return Set<QueryOptions> containing the QueryOptions needed to fetch the
         * entitlements corresponding to the Identity's RoleDetections.  In most cases
         * only one QueryOptions will be returned, but if there are too many entitlements
         * the query will be split so that we don't exceed parameter limits
         * 
         * @throws GeneralException
         */
        private Set<QueryOptions> buildOptionsSet() throws GeneralException {
            final int MAX_ENTITLEMENTS = 200;

            Set<QueryOptions> optionsSet = new HashSet<QueryOptions>();
            // Always fetch the entitlements that are believed to be granted by a role
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(Filter.eq("identity", _identity));      
            filters.add(Filter.not(ROLE_FILTER));
            filters.add(Filter.eq("grantedByRole", true));
            optionsSet.add(new QueryOptions(Filter.and(filters)));

            // Figure out how many other entitlements we're talking about before building the query.  If too many are involved
            // then we need to query in batches
            List<RoleDetection> roleDetections = _identity.getRoleDetections();
            if ( roleDetections != null && _entMappings != null ) {
                List<EntitlementGroup> entitlementGroupsForDetectedRoles = getEntitlementGroupsForDetectedRoles(roleDetections);
                List<EntitlementGroup> manageableChunks = sliceEntitlementGroupsIntoManageableChunks(entitlementGroupsForDetectedRoles);

                // Note that a Set is needed because optimizations that cause existing entitlements to be filtered out of the
                // QueryOptions may result in duplicate QueryOptions
                int numEntitlementsInOptions = 0;
                List<EntitlementGroup> currentEntitlementGroups = new ArrayList<EntitlementGroup>();
    
                for (EntitlementGroup manageableChunk : manageableChunks) {
                    int numEntitlementsInChunk = manageableChunk.getValueCount();
                    // If we've exceeded the count it's time to add the new set of 
                    // QueryOptions and clear the currentEntitlementGroups
                    if (numEntitlementsInOptions + numEntitlementsInChunk > MAX_ENTITLEMENTS) {
                        QueryOptions options = buildOptions(currentEntitlementGroups);
                        if (options != null) {
                            optionsSet.add(options);
                        }
                        currentEntitlementGroups.clear();
                        numEntitlementsInOptions = 0;
                    }
                    currentEntitlementGroups.add(manageableChunk);
                    numEntitlementsInOptions += numEntitlementsInChunk;
                }
    
                if (!Util.isEmpty(currentEntitlementGroups)) {
                    QueryOptions options = buildOptions(currentEntitlementGroups);
                    if (options != null) {
                        optionsSet.add(options);
                    }
                }
            }

            return optionsSet;
        }

        private QueryOptions buildOptions(List<EntitlementGroup> entitlementGroups) throws GeneralException {
            //
            // From the entitlement groups, build filters to bring us
            // back all of the entitlements that are based on roles.
            //
            QueryOptions ops;

            Filter entitlementsFilter = computeEntitlementFilters(entitlementGroups);
            if ( entitlementsFilter != null ) {
                List<Filter> filters = new ArrayList<Filter>();
                filters.add(Filter.eq("identity", _identity));      
                filters.add(Filter.not(ROLE_FILTER));
                filters.add(entitlementsFilter);
                ops = new QueryOptions(Filter.and(filters));
            } else {
                ops = null;
            }

            return ops;
        }
        /*
         * Split any EntitlementGroups with more than 200 entitlements into multiple EntitlementGroups with fewer than that
         */
        private List<EntitlementGroup> sliceEntitlementGroupsIntoManageableChunks(List<EntitlementGroup> entitlementGroups) {
            GroupSlicer slicer = new GroupSlicer(entitlementGroups);
            slicer.prepare();
            return slicer.getManageableChunks();
        }

        /**
         * Build a filter to return all of the interesting entitlements
         * we have already recorded in the db.  Try to be picky to avoid
         * having to read in all of the entitlements.
         * 
         * @return
         * @throws GeneralException
         */
        private Filter computeEntitlementFilters(List<EntitlementGroup> entitlementGroups) throws GeneralException {
            List<Filter> filters = new ArrayList<Filter>();

            //
            // Now that we have a merged list build filters to bring
            // back the entitlements covered by these groups.
            //
            for ( EntitlementGroup group : entitlementGroups ) {
                Filter egFilter = entitlementToFilter(group);
                if ( log.isDebugEnabled() ) {
                    log.debug("Group [\n"+group.toXml()+"]\nEGFilter["+egFilter+"]");
                }
                if ( egFilter != null ){
                    filters.add(egFilter);
                }
            }

            return (Util.size(filters) > 0 ) ? Filter.or(filters) : null;
        }
    }
    
    /**
     * To avoid updating entitlements mutliple times, check to see if we already
     * wrote the role details. In the case where we did already refresh a link during
     * the same refresh process it'll save us additional queries and updates
     * for the entitlements that have already been written.
     */
    @Override
    protected List<String> filterValues(Entitlements group, String attrName, List<String> vals) {
        List<String> values = null;        
        if ( _linktimeRoleData == null  )
            values = vals;
        else {
            if ( vals != null ) {
                values = new ArrayList<String>();
                for ( String val : vals ) {
                    if ( !updatedDuringLinkRefresh(group, attrName, val) ) {
                        values.add(val);
                    } else {
                        if ( log.isDebugEnabled() ) {
                            log.debug("Filtering entitlement because it was already written[" + val + "]");
                        }
                    }
                }
            }
        }            
        return values;
    }      
    
    private boolean updatedDuringLinkRefresh(Entitlements group, String attributeName, String attributeValue ) {
        if ( _linktimeRoleData != null) {
            RoleData data = _linktimeRoleData.get(Entitlizer.getKey(group, attributeName, attributeValue));
            if ( data != null && data.updatedDuringLinkRefresh() ) {
                return true;
            }
        }
        return false;   
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // IndirectUpdate class
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Helper class that updates indirect entitlements/permissions that
     * are "granted" through one or more roles. 
     * 
     */
    private class IndirectUpdater {
        
        /**
         * List of entitlements that are granted indirectly because
         * of an IT role.
         */
        List<IdentityEntitlement> _indirectEntitlements;
        
        /**
         * Mapping that came in from the Entitlement correlation
         * process.
         */
        Map<Bundle, List<EntitlementGroup>> _entMapping;
        
        public IndirectUpdater(List<IdentityEntitlement> indirectEntitlements, 
                               Map<Bundle, List<EntitlementGroup>> entMapping) {
                 
            _indirectEntitlements = indirectEntitlements;
            _entMapping = entMapping;
        }      

        /**
         * Remove the RoleMeta data from the indirect entitlements.  This includes the 
         * granted_by_role, assigned and detected assigned role list on each entitlement.
         * 
         * @param context
         * @throws GeneralException
         */
        public void clearExistingIndirectInformation(SailPointContext context) throws GeneralException  {
            if ( _indirectEntitlements != null ) {
                for ( IdentityEntitlement entitlement : _indirectEntitlements) {
                    if ( entitlement != null ) {
                        EntitlementUpdater updater = new EntitlementUpdater(entitlement);
                        updater.clearRoleData();
                        if ( updater.hasUpdates() ) 
                            context.saveObject(entitlement);
                    }
                }
            }
        }
        
        public void updateIndirect(RoleDetection detection, List<String> allowed) throws GeneralException {
            List<EntitlementGroup> groups = null;
            if (_entMapping != null ) {
                groups = getEntitlementGroups(detection, _entMapping);
            }
            
            if ( groups != null ) {
                for ( EntitlementGroup group : groups ) {
                   updateFromGroup(detection.getName(), group, allowed);
                }
            }
        }            
        
        private void updateFromGroup(String roleName, EntitlementGroup group, List<String> allowedBy ) 
            throws GeneralException {
            
            Meter.enter(106, METER_PREFIX + " updateFromGroup");
            List<String> attrNames = group.getAttributeNames();
            Attributes<String,Object> attrs = group.getAttributes();
            String appName = group.getApplicationName();
            String nativeId = group.getNativeIdentity();
            
            if ( attrNames == null || Util.isEmpty(attrs) ) {
                return;
            }

            //
            // Go over all of the attribute values and make/update entitlements
            //
            if ( attrNames != null && !Util.isEmpty(attrs) ) {
                for ( String name : attrNames ) {
                    if ( name == null ) continue;
                    Object attrVal = attrs.get(name);
                    if ( attrVal == null ) continue;
                    
                    List<String> values = valueToStringList(attrVal);
                    if ( values == null ) continue;
    
                    for ( String valStr : values ) {                        
                        if ( Util.getString(valStr) != null ) {                            
                            IdentityEntitlement ent = findEntitlementWithApp(_indirectEntitlements, name, valStr, appName, nativeId);
                            if ( ent != null ) {
                                EntitlementUpdater updater = new EntitlementUpdater(ent);
                                updater.setGrantedByRole(roleName, allowedBy);
                                if ( updater.hasUpdates() ) {
                                    _context.saveObject(ent);
                                    _totalIndirectUpdates++;
                                }
                            }
                        } else {
                            log.warn("Entitlement not found for detected role entitlement[" + name + "] value["+ valStr+"].");
                        }
                    }
                }
            }
            
            List<Permission> permissions = group.getPermissions();
            if ( permissions != null ) {
                for ( Permission permission : permissions ) {
                    String name = permission.getTarget();
                    List<String> rights = permission.getRightsList();
                    for ( String right : rights ) {
                        IdentityEntitlement ent = findEntitlementWithApp(_indirectEntitlements, name, right, appName, nativeId);
                        if ( ent != null ) {
                            EntitlementUpdater updater = new EntitlementUpdater(ent);
                            updater.setGrantedByRole(roleName, allowedBy  );                            
                            if ( updater.hasUpdates() ) {                                                        
                                _context.saveObject(ent);
                                _totalIndirectPermissionUpdates++;
                            }                               
                        } else {
                            log.warn("Entitlement Permission was not found for detected role entitlement[" + name + "] value["+ right+"].");
                        }
                    }
                }
            }
            Meter.exit(106);
        }        
    }
    
    /*
     * This class splits EntitlementGroups into chunks that can generate reasonably-sized queries
     * for IdentityEntitlements
     */
    private class GroupSlicer {
        private List<EntitlementGroup> _entitlementGroups;
        private static final int MAX_ENTITLEMENTS_IN_CHUNK = 200;
        private List<EntitlementGroup> _manageableChunks;
        private int _numEntitlementsAdded;
        private int _numEntitlementsInCurrentGroup;
        private EntitlementGroup _currentGroup;
        
        private Application _currentApplication;
        private String _currentInstance;
        private String _currentNativeIdentity;
        private String _currentDisplayName;

        private GroupSlicer(List<EntitlementGroup> entitlementGroups) {
            _entitlementGroups = entitlementGroups;
            _manageableChunks = new ArrayList<EntitlementGroup>();
            _numEntitlementsInCurrentGroup = 0;
            _numEntitlementsAdded = 0;
        }

        
        private void prepare() {
            _manageableChunks = new ArrayList<EntitlementGroup>();
            for (EntitlementGroup entitlementGroup : _entitlementGroups) {
                int numEntitlementsInGroup = entitlementGroup.getValueCount();
                if (numEntitlementsInGroup < MAX_ENTITLEMENTS_IN_CHUNK) {
                    // If we're under the limit we can leave the group as-is
                    _currentGroup = entitlementGroup;
                    finishCurrentGroup();
                } else {
                    _currentApplication = entitlementGroup.getApplication();
                    _currentInstance = entitlementGroup.getInstance();
                    _currentNativeIdentity = entitlementGroup.getNativeIdentity();
                    _currentDisplayName = entitlementGroup.getDisplayName();

                    // Start with a blank group
                    _currentGroup = new EntitlementGroup(_currentApplication, _currentInstance, _currentNativeIdentity, _currentDisplayName);

                    // Split the attributes if needed
                    splitAttributes(entitlementGroup);

                    // Then split the Permissions if needed
                    splitPermissions(entitlementGroup.getPermissions());

                    // We're done with this group now so finish it
                    finishCurrentGroup();
                }
            }
            log.debug("Added " + _numEntitlementsAdded + " entitlements.");
        }

        // TODO:  splitAttributes() and splitPermissions() are similar enough to be refactored
        // into a single method through the clever use of interfaces.  Consider doing so
        private void splitAttributes(EntitlementGroup entitlementGroup) {
            Attributes<String, Object> attributes = entitlementGroup.getAttributes();
            if (!Util.isEmpty(attributes)) {
                List<String> attributeNames = entitlementGroup.getAttributeNames();

                for (String attributeName : attributeNames) {
                    Object attributeValues = attributes.get(attributeName);
                    List valueList = Util.asList(attributeValues);
                    int numValuesInAttribute = Util.size(valueList);
                    // Check whether to split this attribute
                    int remainingSpaceInCurrentGroup = MAX_ENTITLEMENTS_IN_CHUNK - _numEntitlementsInCurrentGroup;
                    if (numValuesInAttribute <= remainingSpaceInCurrentGroup) {
                        // If there's room just add everything
                        addAttributesToCurrentGroup(attributeName, attributeValues, numValuesInAttribute);
                    } else {
                        // Otherwise fill the current chunk
                        int start = 0;
                        int end = remainingSpaceInCurrentGroup;
                        while (end <= numValuesInAttribute) {
                            // Add the attributes and check process the group if it's filled
                            addAttributesToCurrentGroup(attributeName, valueList.subList(start, end), end - start);
                            checkIfCurrentGroupFull();

                            // Set up for the next iteration
                            start = end;
                            end += MAX_ENTITLEMENTS_IN_CHUNK;

                            // If we're starting within the boundaries of the list but ending
                            // outside of it trim it down to a proper size. Otherwise leave 
                            // it alone so we can fall out of the loop.
                            if (start < numValuesInAttribute && end > numValuesInAttribute) {
                                end = numValuesInAttribute;
                            }
                        }
                    }
                }
            }
        }

        private void addAttributesToCurrentGroup(String attributeName, Object attributeValues, int numValuesInAttribute) {
            Attributes<String, Object> attributesInCurrentGroup = _currentGroup.getAttributes();
            if (attributesInCurrentGroup == null) {
                attributesInCurrentGroup = new Attributes<String, Object>();
                _currentGroup.setAttributes(attributesInCurrentGroup);
            }
            attributesInCurrentGroup.put(attributeName, attributeValues);
            _numEntitlementsAdded += numValuesInAttribute;
            _numEntitlementsInCurrentGroup += numValuesInAttribute;
        }

        private void splitPermissions(List<Permission> permissions) {
            if (!Util.isEmpty(permissions)) {
                for (Permission permission : permissions) {
                    List<String> rights = permission.getRightsList();
                    int numRights = Util.size(rights);
                    // Check whether to split this attribute
                    int remainingSpaceInCurrentGroup = MAX_ENTITLEMENTS_IN_CHUNK - _numEntitlementsInCurrentGroup;
                    if (numRights <= remainingSpaceInCurrentGroup) {
                        // If there's room just add everything
                        addPermissionToCurrentGroup(permission, numRights);
                    } else {
                        // Otherwise fill the current chunk
                        int start = 0;
                        int end = remainingSpaceInCurrentGroup;
                        while (end <= numRights) {
                            // Add the attributes and check process the group if it's filled
                            List<String> rightsSubset = rights.subList(start, end);
                            Permission partialPermission = new Permission(permission);
                            partialPermission.setRights(rightsSubset);
                            addPermissionToCurrentGroup(permission, end - start);
                            checkIfCurrentGroupFull();
    
                            // Set up for the next iteration
                            start = end;
                            end += MAX_ENTITLEMENTS_IN_CHUNK;
    
                            // If we're starting within the boundaries of the list but ending
                            // outside of it trim it down to a proper size. Otherwise leave 
                            // it alone so we can fall out of the loop.
                            if (start < numRights && end > numRights) {
                                end = numRights;
                            }
                        }
                    }
                }
            }
        }

        private void addPermissionToCurrentGroup(Permission permission, int numRights) {
            List<Permission> permissionsInCurrentGroup = _currentGroup.getPermissions();
            if (permissionsInCurrentGroup == null) {
                permissionsInCurrentGroup = new ArrayList<Permission>();
                _currentGroup.setPermissions(permissionsInCurrentGroup);
            }
            permissionsInCurrentGroup.add(permission);
            _numEntitlementsAdded += numRights;
            _numEntitlementsInCurrentGroup += numRights;
        }

        /*
         * Check whether the current group is full.  If it is, then add it to the list
         * of chunks and start a new one
         */
        private void checkIfCurrentGroupFull() {
            if (_numEntitlementsInCurrentGroup >= MAX_ENTITLEMENTS_IN_CHUNK) {
                finishCurrentGroup();
                _currentGroup = new EntitlementGroup(_currentApplication, _currentInstance, _currentNativeIdentity, _currentDisplayName);
            }
        }

        /*
         * Add the current group to the chunk and reset the entitlement count to zero.
         * This should only be called once per group when we're done adding to it
         */
        private void finishCurrentGroup() {
            if (_currentGroup != null && _currentGroup.getValueCount() > 0) {
                _manageableChunks.add(_currentGroup);
                _numEntitlementsAdded += _currentGroup.getValueCount();
                _numEntitlementsInCurrentGroup = 0;
            }
        }

        private List<EntitlementGroup> getManageableChunks() {
            return _manageableChunks;
        }
    }
    

}
