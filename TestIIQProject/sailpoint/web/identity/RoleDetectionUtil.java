package sailpoint.web.identity;

import java.util.LinkedHashSet;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class RoleDetectionUtil {
    
    /**
     * Utility class to fetch a Bundle with transient assignmentId derived from a Role Assignment
     * 
     * @param context SailPoint context used to fetch the Bundle
     * @param detection RoleDetection for a given bundle
     * @param assignmentId assignmentId derived from the RoleAssignment if there is one
     * @return A cloned Bundle with a transient assignmentId or null if no context or Role Assignment. 
     * @throws GeneralException
     */
    public static Bundle getClonedBundleFromRoleDetection(SailPointContext context, RoleDetection detection, String assignmentId) throws GeneralException {
        
        Bundle b = null;
        if(context != null && detection != null) {
            if(Util.isNotNullOrEmpty(detection.getRoleId())) {
                b = context.getObjectById(Bundle.class, detection.getRoleId());
            } else if(Util.isNotNullOrEmpty(detection.getRoleName())){
                //ID should not be null. Need to support name lookup for unit tests
                b = context.getObjectByName(Bundle.class, detection.getRoleName());
            }
            
            if(b != null) {
                //Clone the Bundle so hibernate will not try to perform dirty checking
                XMLObjectFactory objFact = XMLObjectFactory.getInstance();
                b = (Bundle)objFact.clone(b, context);
                //Set the transient property
                b.setAssignmentId(assignmentId);
            }
        }
        
        return b;
    }
    
    /**
     * Get a list of Target Account Display Names from a RoleDetection's RoleTargets
     * @param rd RoleDetection from which to get Target Account Display Names
     * @return Target Account Display Name of all RoleTargets on the specified RoleDetection
     */
    public static Set<String> getTargetAccountNames(RoleDetection rd) {
        Set<String> accntNames = new LinkedHashSet<String>();
        if(rd != null && rd.getTargets() != null) {
            for(RoleTarget rt : rd.getTargets()) {
                accntNames.add(rt.getDisplayableName());
            }
        }
        
        return accntNames;
    }
    
    /**
     * Get a list of Target Account Application Names from a RoleDetection's RoleTargets
     * @param rd RoleDetection from which to get Target Account Application Names
     * @return List of Target Account Application Name of all RoleTargets on the specified RoleDetection
     */
    public static Set<String> getTargetAppNames(RoleDetection rd) {
        Set<String> accntNames = new LinkedHashSet<String>();
        if(rd != null && rd.getTargets() != null) {
            for(RoleTarget rt : rd.getTargets()) {
                accntNames.add(rt.getApplicationName());
            }
        }
        
        return accntNames;
        
    }

}
