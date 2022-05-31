/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Used to represent an analysis of the role relationships for
 * a single identity.  This is a transient object used by the UI
 * when displaying assigned and detected roles for an identity.
 * It is built by the Identitizer.
 *
 * Author: Jeff
 *
 * Currently we just to try to show which assigned roles
 * permit which detected roles.  Eventually this will grow
 * to hold more complex analysis like things "missing" from
 * the assigned roles.
 *
 * This is not persistent.  Try not to get to confortable with
 * this since it may be refactored in a later release.  It should
 * only be used by IdentityBean right now.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.XMLClass;

/**
 * Used to represent an analysis of the role relationships for
 * a single identity. This is a transient object used by the UI
 * when displaying assigned and detected roles for an identity.
 * It is built by the Identitizer.
 */
@XMLClass
public class RoleRelationships {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A map representing the detected roles permitted
     * by the assigned roles. The key is an assigned role,
     * the value is a list of detected roles.
     */
    Map<Bundle,List<Bundle>> _assignedRoles;

    /**
     * A map representing the assigned roles that permit
     * the detected roles. The key is a detected role
     * and the value is a list of assigned roles.
     */
    Map<Bundle,List<Bundle>> _detectedRoles;

    /**
     * Stores requirements by role. This includes the role's direct
     * requirements as well as the requirements for it's parent roles.
     */
    Map<Bundle,List<Bundle>> _requiredRoles;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RoleRelationships() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Analyzer
    //
    //////////////////////////////////////////////////////////////////////

    public void analyze(Identity ident) {
        analyze(ident.getAssignedRoles(), ident.getBundles());
    }

    public void analyze(List<Bundle> assigned, List<Bundle> detected) {
        _assignedRoles = new HashMap<Bundle,List<Bundle>>();
        _detectedRoles = new HashMap<Bundle,List<Bundle>>();
        _requiredRoles = new HashMap<Bundle,List<Bundle>>();

        if (assigned != null) {
            for (Bundle b : assigned){
                if (b != null) {
                    _assignedRoles.put(b, new ArrayList<Bundle>());
                    _requiredRoles.put(b, getRequirements(b));
                }
            }
        }

        if (detected != null) {
            for (Bundle b : detected) {
                if (b != null) {
                    List<Bundle> permitting = getPermittingRoles(assigned, b);
                    _detectedRoles.put(b, permitting);
                    for (Bundle pr : permitting) {
                        List<Bundle> permits = _assignedRoles.get(pr);
                        if (!permits.contains(b))
                            permits.add(b);
                    }
                }
            }
        }
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

    /**
     * Calculate the list of all assigned roles that directly or indirectly
     * permit the detected role.
     */
    public List<Bundle> getPermittingRoles(List<Bundle> assigned, Bundle b) {

        List<Bundle> permitting = new ArrayList<Bundle>();
        if (assigned != null) {
            for (Bundle ar : assigned) {
                if (ar != null && isPermitted(ar, b)) {
                    // TODO: may want to remember the super role that did
                    // the permitting 
                    permitting.add(ar);
                }
            }
        }
        return permitting;
    }

    /**
     * Walk up the hierarchy from assignedRole looking for detectedRole
     * on any of the permits lists.
     */
    public boolean isPermitted(Bundle assignedRole, Bundle detectedRole) {

        boolean permitted = false;
        
        // a detected role may also be assigned
        if (assignedRole == detectedRole)
            permitted = true;

        // look at the local permits list
        if (!permitted) {
            List<Bundle> permits = assignedRole.getPermits();
            if (permits != null) {
                for (Bundle permit : permits) {
                    if (permit == detectedRole) {
                        permitted = true;
                        break;
                    }
                }
            }
        }

        // and the requirements list
        if (!permitted) {
            List<Bundle> requirements = assignedRole.getRequirements();
            if (requirements != null) {
                for (Bundle req : requirements) {
                    if (req == detectedRole) {
                        permitted = true;
                        break;
                    }
                }
            }
        }

        // walk up the hierarchy
        if (!permitted) {
            List<Bundle> supers = assignedRole.getInheritance();
            if (supers != null) {
                for (Bundle superRole : supers) {
                    permitted = isPermitted(superRole, detectedRole);
                    if (permitted)
                        break;
                }
            }
        }

        return permitted;
    }

    public boolean isRequired(Bundle assignedRole, Bundle detectedRole){
        List<Bundle> requirements = _requiredRoles.get(assignedRole);
        return requirements.contains(detectedRole);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////

    public List<Bundle> getPermittedRoles(Bundle role) {
        List<Bundle> roles = null;
        if (_assignedRoles != null)
            roles = _assignedRoles.get(role);
        return roles;
    }
    
    public String getPermittedNames(Bundle role) {
        List<Bundle> roles = getPermittedRoles(role);
        return getNameCsv(roles);
    }

    public List<Bundle> getPermittingRoles(Bundle role) {
        List<Bundle> roles = null;
        if (_detectedRoles != null)
            roles = _detectedRoles.get(role);
        return roles;
    }

    public String getPermittingNames(Bundle role) {
        List<Bundle> roles = getPermittingRoles(role);
        return getNameCsv(roles);
    }

    public List<Bundle> getRequiringRoles(Bundle role) {
        List<Bundle> roles = null;
        if (_requiredRoles != null)
            roles = _requiredRoles.get(role);
        return roles;
    }

    public List<Bundle> getMissingRequirements(Bundle role){
        List<Bundle> missingReqs = new ArrayList<Bundle>();
        if (_requiredRoles.get(role) != null){
            for (Bundle reqRole : _requiredRoles.get(role)){
                if (!hasRole(reqRole))
                    missingReqs.add(reqRole);
            }
        }
        return missingReqs;
    }

    /**
     * Returns true if the identity has the given role.
     * @param role The role to look for.
     * @return True if the identity is assigned, detected or inherited by
     * on of the identity's detected roles.
     */
    public boolean hasRole(Bundle role){

        if (_detectedRoles.containsKey(role) || _assignedRoles.containsKey(role))
            return true;

        if (_detectedRoles != null && !_detectedRoles.isEmpty()){
            for(Bundle detectedRole : _detectedRoles.keySet()){
                if (detectedRole.getFlattenedInheritance().contains(role))
                    return true;
            }
        }

        return false;
    }

    private String getNameCsv(List<Bundle> roles) {
        StringBuilder b = new StringBuilder();
        if(roles!=null) {
            for (Bundle role : roles) {
                if (b.length() > 0)
                    b.append(",");
                b.append(role.getName());
            }
        }
        return b.toString();
    }
    
    public void dump() {
        println("Assigned Roles:");
        dump(_assignedRoles);
        println("Detected Roles:");
        dump(_detectedRoles);
    }

    private void dump(Map<Bundle,List<Bundle>> map) {
        Iterator<Map.Entry<Bundle,List<Bundle>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Bundle,List<Bundle>> entry = it.next();
            print("  " + entry.getKey().getName() + ": ");
            println(getNameCsv(entry.getValue()));
        }
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static void print(Object o) {
        System.out.print(o);
    }

}
