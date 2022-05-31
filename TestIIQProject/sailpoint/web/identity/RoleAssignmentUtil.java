/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.web.identity;

/**
 * Imports.
 */
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Resolver;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Utility functions that revolve around RoleAssignment objects.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class RoleAssignmentUtil {

    /**
     * Gets the a csv string of the application names
     * contained in the role targets of the specified role
     * assignment object.
     * @param targets The role targets.
     * @return The truncated csv string.
     */
    public static String getTargetAppNamesCsv(List<RoleTarget> targets) {
        Set<String> namesSet = new LinkedHashSet<String>();
        for (RoleTarget roleTarget : Util.safeIterable(targets)) {
            namesSet.add(roleTarget.getApplicationName());
        }

        return Util.join(namesSet, ", ");
    }

    /**
     * Gets the csv string of the account names
     * contained in the role targets of the specified role
     * assignment object.
     * @param targets The role targets.
     * @return The truncated csv string.
     */
    public static String getTargetAcctNamesCsv(List<RoleTarget> targets) {
        Set<String> accountsSet = new LinkedHashSet<String>();
        for (RoleTarget roleTarget : Util.safeIterable(targets)) {
            if (roleTarget.getDisplayableName() == null) {
                accountsSet.add("");
            } else {
                accountsSet.add(roleTarget.getDisplayableName());
            }
        }

        return Util.join(accountsSet, ", ");
    }

    /**
     * Adds the role assignment info to a row in a list result.
     *
     * @param row The row.
     * @param targets The role targets.
     * @param appKey The key of the application names csv in the map.
     * @param acctKey The key of the account names csv in the map.
     */
    public static void addRoleAssignmentInfoToListResultRow(Map<String, Object> row, List<RoleTarget> targets, String appKey, String acctKey) {
        if (Util.isEmpty(targets)) {
            return;
        }

        String appCsv = getTargetAppNamesCsv(targets);
        String acctCsv = getTargetAcctNamesCsv(targets);

        row.put(appKey, appCsv);
        row.put(acctKey, acctCsv);
    }

    /**
     * Determines if the role with the specified id is assignable.
     * @param resolver The resolver.
     * @param roleId The role id.
     * @return True if assignable, false otherwise.
     * @throws GeneralException
     */
    public static boolean isRoleAssignable(Resolver resolver, String roleId) throws GeneralException {
        if (resolver == null) {
            return false;
        }

        Bundle role = resolver.getObjectById(Bundle.class, roleId);

        return isRoleAssignable(role);
    }
    /**
     * Determines if the role is assignable.
     * @param role The role.
     * @return True if assignable, false otherwise.
     */
    public static boolean isRoleAssignable(Bundle role) {
        if (role == null || role.getRoleTypeDefinition() == null) {
            return false;
        }

        return role.getRoleTypeDefinition().isAssignable();
    }
    
    /**
     * Utility class to fetch a Bundle with transient assignmentId derived from a Role Assignment
     * 
     * @param context SailPoint context used to fetch the Bundle
     * @param assign RoleAssignment for the given Bundle
     * @return A cloned Bundle with a transient assignmentId or null if no context or Role Assignment. 
     * @throws GeneralException
     */
    public static Bundle getClonedBundleFromRoleAssignment(SailPointContext context, RoleAssignment assign) throws GeneralException {
        
        Bundle b = null;
        if(context != null && assign != null) {
            if(Util.isNotNullOrEmpty(assign.getRoleId())) {
                b = context.getObjectById(Bundle.class, assign.getRoleId());
            } else if(Util.isNotNullOrEmpty(assign.getRoleName())){
                //Id should not be null, support name lookup for unitTests
                b = context.getObjectByName(Bundle.class, assign.getRoleName());
            }
            if(b != null) {
                //Clone the Bundle so hibernate will not try to perform dirty checking
                XMLObjectFactory objFact = XMLObjectFactory.getInstance();
                b = (Bundle)objFact.clone(b, context);
                //Set the transient property
                b.setAssignmentId(assign.getAssignmentId());
            }
        }
        
        return b;
    }
    
    public static boolean allowsDetection(SailPointContext ctx, RoleAssignment ra, RoleDetection rd) throws GeneralException {
        boolean allowed = false;
        Bundle b = null;
        if(ctx != null) {
            b = ctx.getObjectById(Bundle.class, ra.getRoleId());
        }
        if(b != null) {
            //Check requirements
            for(Bundle req : b.getRequirements()) {
                if(rd.getRoleId() != null && rd.getRoleId().equals(req.getId())) {
                    return true;
                } else if(rd.getRoleName() != null && rd.getRoleName().equals(req.getName())) {
                    return true;
                }
            }
            
            //Check Permits
            for(Bundle perm : b.getPermits()) {
                if(rd.getRoleId() != null && rd.getRoleId().equals(perm.getId())) {
                    return true;
                } else if(rd.getRoleName() != null && rd.getRoleName().equals(perm.getName())) {
                    return true;
                }
            }
            
        }
        return allowed;
    }
}
