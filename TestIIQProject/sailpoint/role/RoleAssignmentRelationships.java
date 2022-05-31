package sailpoint.role;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.tools.GeneralException;
import sailpoint.web.identity.RoleAssignmentUtil;
import sailpoint.web.identity.RoleDetectionUtil;

/**
 * Helper class used to derive all RoleAssignment relationships for a given identity.
 * 
 * @author ryan.pickens
 *
 */
public class RoleAssignmentRelationships {
    
    protected SailPointContext _ctx;
    private static final Log log = LogFactory.getLog(RoleAssignmentRelationships.class);
    
    //RoleAssignments on the identity
    protected List<RoleAssignment> _roleAssignments;
    //RoleDetections on the identity
    protected List<RoleDetection> _roleDetections;
    
    /**
     * Map of RoleAssignment and the Roles permitted by the assignment
     * that we have detected in the role detections
     * The key is the RoleAssignment AssignmentId and value is a List of 
     * Bundles that it permits each with their transient assignmentId 
     */
    protected Map<String, List<Bundle>> _assignmentPermits;
    
    /**
     * Map keyed on RoleAssignment assignmentId. This contains the
     * Required Roles that we have found in the Role Detections
     */
    protected Map<String, List<Bundle>> _assignmentRequiredDetections;
    
    /**
     * Map of RoleAssignment and the Roles required by the assignment
     * The key is the RoleAssignment assignmentId and the value is a 
     * list of Bundles that are required by the assigned role
     * 
     * NOTE: These are what the Role dictates as required. This does not
     * mean the Identity Role Detections contains these 
     * @see #getMissingRequirements(String)
     */
    protected Map<String, List<Bundle>> _assignmentRequired;
    
    
    public RoleAssignmentRelationships(SailPointContext context) {
        _ctx = context;
    }
    
    public void analyze(Identity ident) {
        try {
            analyzeAssignments(ident.getRoleAssignments(), ident.getRoleDetections());
        } catch(GeneralException e) {
            log.warn("Unable to analyze Role Assignments");
        }
    }
    
    
    public void analyzeAssignments(List<RoleAssignment> assignments, List<RoleDetection> detections) throws GeneralException {
        
        _roleAssignments = assignments;
        _roleDetections = detections;
        _assignmentPermits = new HashMap<String,List<Bundle>>();
        _assignmentRequired = new HashMap<String, List<Bundle>>();
        _assignmentRequiredDetections = new HashMap<String, List<Bundle>>();
        
        if(!Util.isEmpty(assignments)) {
            for(RoleAssignment ra : assignments) {
                Bundle b = RoleAssignmentUtil.getClonedBundleFromRoleAssignment(_ctx, ra);
                _assignmentPermits.put(ra.getAssignmentId(), getPermittedRoles(ra));
                _assignmentRequired.put(ra.getAssignmentId(), getRequirements(b));
            }
        }
        
    }
    
    /**
     * Gets the Permitted Roles that are in the RoleDetection list for a given Role Assignment
     * @param assignment RoleAssignment to get permitted roles from
     * @return permitted roles for a given role assignment
     * @throws GeneralException 
     */
    public List<Bundle> getPermittedRoles(RoleAssignment assignment) throws GeneralException {
        return this.getPermittedRoles(assignment.getAssignmentId());
    }
    
    public List<Bundle> getPermittedRoles(String assignmentId) throws GeneralException {
        List<Bundle> permits = new ArrayList<Bundle>();
        if(!Util.isEmpty(_roleDetections) && assignmentId != null) {
            for(RoleDetection det : _roleDetections) {
                if(det.hasAssignmentId(assignmentId)) {
                    permits.add(RoleDetectionUtil.getClonedBundleFromRoleDetection(_ctx, det, assignmentId));
                }
            }
        }
        return permits;
    }
    
    /**
     * Returns a list of missing required bundles for a given Role Assignment.
     * We look for the roles required by a certain role assignment, and iterate
     * the RoleDetections to see if the Detection exists.
     * 
     * @param assignmentId The assignmentId of a RoleAssignment on an Identity
     * @return List of Missing requiredBundles with transient assignment ids already assigned
     */
    public List<Bundle> getMissingRequirements(String assignmentId) {
        List<Bundle> missing = new ArrayList<Bundle>();
        
        if(sailpoint.tools.Util.isNotNullOrEmpty(assignmentId)) {
            List<Bundle> required = _assignmentRequired.get(assignmentId);
            if(!Util.isEmpty(required)) {
                for(Bundle req : required) {
                    //For each required role, iterate through role detections and try to find the bundle
                    boolean found = false;
                    if(!Util.isEmpty(_roleDetections)) {
                        for(RoleDetection rd : _roleDetections) {
                            //Need to check Role Id and assignment Id
                            if(rd.getRoleId().equals(req.getId()) && rd.getAssignmentIdList().contains(assignmentId)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if(!found) {
                        //Should we clone before returning to avoid hibernate mess? -rap
                        req.setAssignmentId(assignmentId);
                        missing.add(req);
                    }
                }
            }
        }
        
        return missing;
        
    }
    
    /**
     * Returns a list of Bundles required by a particular RoleAssignment
     * that we have found in the RoleDetections
     * @param assignmentId RoleAssignment id in which to search on
     * @return
     */
    public List<Bundle> getDetectedRequiredRoles(String assignmentId) {
        return getAssignmentRequiredDetections().get(assignmentId);
    }
    
    public Map<String, List<Bundle>> getAssignmentRequiredDetections() {
        if(_assignmentRequiredDetections == null) {
            _assignmentRequiredDetections = new HashMap<String, List<Bundle>>();

            //Walk through the RoleAssignments and see what requirements we are missing in the RoleDetections
            if(!Util.isEmpty(_roleAssignments)) {
                for(RoleAssignment assignment : _roleAssignments) {
                    List<Bundle> reqBunds = _assignmentRequired.get(assignment.getAssignmentId());
                    List<Bundle> foundBundles = new ArrayList<Bundle>();
                    if(!Util.isEmpty(_roleDetections) && !Util.isEmpty(reqBunds)) {
                        for(Bundle req : reqBunds) {
                            boolean found = false;
                            for(RoleDetection detection : _roleDetections) {
                                if(req.getId().equals(detection.getRoleId()) 
                                        && detection.hasAssignmentId(assignment.getAssignmentId())) {
                                    //Is this going to have wierd behavior with hibernate? -rap
                                    req.setAssignmentId(assignment.getAssignmentId());
                                    foundBundles.add(req);
                                    break;
                                }
                            }
                        }
                    }
                    _assignmentRequiredDetections.put(assignment.getAssignmentId(), foundBundles);
                }
            }
        }
        return _assignmentRequiredDetections;
        
    }
    
    
    public boolean isRequired(Bundle detectedRole, String assignmentId) {
        boolean required = false;
        if(!Util.isEmpty(_assignmentRequired)) {
            List<Bundle> reqs = _assignmentRequired.get(assignmentId);
            if(!Util.isEmpty(reqs)) {
                for(Bundle b : reqs) {
                    if(b.getId().equals(detectedRole.getId()))
                        return true;
                }
            }
        }
        return required;
    }
    
    /**
     * Returns the permitted roles for a given role assignment that are not permitted/required by any other Role
     * @param assignmentId The assignment id.
     * @return A List containing the distinct permits
     */
    public List<Bundle> getDistinctPermittedRoles(String assignmentId) throws GeneralException {
        List<Bundle> distinctPermits = new ArrayList<Bundle>();
        if(!Util.isEmpty(_roleDetections)) {
            for(RoleDetection rd : _roleDetections) {
                // IIQETN-6282 - if the assignmentId is in the RoleDetection assignmentIds, then add it.
                if(Util.isNotNullOrEmpty(rd.getAssignmentIds()) && rd.getAssignmentIds().contains(assignmentId)) {
                    Bundle b = RoleDetectionUtil.getClonedBundleFromRoleDetection(_ctx, rd, assignmentId);
                    distinctPermits.add(b);
                }
            }
        }
        return distinctPermits;
    }

    public List<Bundle> getRequirements(Bundle role){
        List<Bundle> reqs = new ArrayList<Bundle>();
        if (role.getRequirements() != null)
            reqs.addAll(role.getRequirements());  

        if (role.getInheritance() != null){
            for(Bundle parent : role.getInheritance()){
                reqs.addAll(getRequirements(parent));
            }
        }

        return reqs;
    }

    

}
