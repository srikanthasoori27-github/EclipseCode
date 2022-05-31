/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of PolicyExecutor that checks Segregation of Duty 
 * policies on roles.  The policy model consists of a root Policy object
 * containing a list of SODConstraint objects.
 *
 * Author: Jeff
 *
 * Assigned vs Detected Roles
 *
 * Originally this only dealt with detected roles.  Starting with 3.0
 * we added the assigned role concept but these were not being
 * included in the policy scan.  In 3.1 we started including them,
 * but this leads to interesting combinations like
 * a business role conflicting with an IT role that we haven't
 * fully thought out in UI.
 *
 * One thing that is NOT being handled is the implication
 * that when you assign a role we will provision the required roles
 * if enabled.  So, even though an identity may not have the
 * detected IT roles that would cause a violation, the violation
 * may exist after provisioning.  We should try to "look through"
 * the assignment to see if the provisioning side effects would
 * cause a violation and treat that as a violation of the assigned role.
 * Complex..
 *
 */

package sailpoint.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Profile;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SODConstraint;
import sailpoint.tools.GeneralException;

/**
 * Checks whether an identity violates an {@link sailpoint.object.SODConstraint}
 */
public class SODPolicyExecutor extends AbstractPolicyExecutor {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SODPolicyExecutor.class);

    private static final String DETAILS_RENDERER = "policySODDetails.xhtml";

    SailPointContext _context;
    Policy _policy;
    Identity _identity;
    Map<Bundle,AssignedBundle> _roles;

    List<PolicyViolation> _violations;

    /**
     * When true disables bundle hierarchy flattening.
     */
    boolean _noFlattening;

    /**
     * When true we will generate only one PolicyViolation object
     * for each SODConstraint.  This may correspond to several
     * pairs of conflicting bundles.
     *
     * NOTE: MitigationExpiration can only reference a Policy and
     * an SODConstraint. If we generate multiple PolicyViolations for
     * a single SODConstraint, there is no way to individually mitigate
     * the violations.  It feels better to consolodate these rather than
     * making the mitigation model more complicated.  This will also
     * cut down on notifications.
     */
    boolean _consolidatedViolations = true;

    //////////////////////////////////////////////////////////////////////
    //
    // AssignedBundle
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Helper class to track the origin of a bundle that was 
     * assigned indirectly through inheritance.
     */
    private static class AssignedBundle {

        Bundle _bundle;
        LinkedHashSet<Bundle> _origins;

        public AssignedBundle(Bundle b, Bundle o) {
            _bundle = b;
            if (null == _origins) {
                _origins = new LinkedHashSet<Bundle>();
            }
            addOrigin(o);
        }

        public Bundle getBundle() {
            return _bundle;
        }

        public Set<Bundle> getOrigins() {
            return _origins;
        }
               
        public boolean addOrigin(Bundle o) {
        	_origins.add(o);
        	
        	//  If we've reached the level at which we're evaluating, we're done.
        	if (o.getId().equals(_bundle.getId())) {
        		return true;
        	}
        	
            // traverse the hierarchy looking for _bundle
            // if we find it, use it, unless its a business role, in which case, move back up and use the previous set.
        	// only process the hierarchy if this is detected
        	RoleTypeDefinition otype = o.getRoleTypeDefinition();
        	if (null == otype || otype.isDetectable()) {
	            List<Bundle> inherits = o.getInheritance();
	            for (Bundle i : inherits) {
	            	RoleTypeDefinition itype = i.getRoleTypeDefinition();
	                if (i.getId().equals(_bundle.getId())) {
	                    if (null != itype && itype.isAssignable()) {
	                        return true;
	                    } else {
	                        _origins.add(i);
	                        return true;
	                    }
	                } 
	
	                if (null == itype || itype.isDetectable()) {
	                	if (addOrigin(i)) return true;
	                }
	            }
        	}
            return true;
        }
        
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public SODPolicyExecutor() {
    }

    public void setConsolidatedViolations(boolean b) {
        _consolidatedViolations = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy,
                                          Identity id)
        throws GeneralException {

        _context = context;
        _policy = policy;
        _identity = id;
        _roles = null;
        _violations = null;

        List<SODConstraint> constraints = policy.getSODConstraints();

        if (constraints != null) {

            flatten(id.getDetectedRoles());
            flatten(id.getAssignedRoles());
                            
            for (SODConstraint con : constraints) {

                // Ignore disabled constraints.
                // Originally ignored constraints with a compensating
                // control, but you might still want a violation, just
                // use the control to reduce its weight.  If you really
                // don't want the rule evaluated, then disable it.

                if (!con.isDisabled()) {

                    List<Bundle> left = con.getLeftBundles();
                    List<Bundle> right = con.getRightBundles();

                    if (left != null && left.size() > 0 &&
                        right != null && right.size() > 0) {

                        evaluateRoleConstraint(con, left, right);
                    }
                }
            }
        }

        return _violations;
    }

    /**
     * Derive a map of all bundles assigned to the user.
     * Optionally include all indirect bundles assigned
     * through inheritance.
     */
    private void flatten(List<Bundle> bundles) {
        
        if (_roles == null)
            _roles = new HashMap<Bundle,AssignedBundle>();

        if (bundles != null) {
            for (Bundle b : bundles) {
                addBundle(b, b);
            }
        }
    }

    private void addBundle(Bundle b, Bundle origin) {

        if (_roles.get(b) == null) {
            _roles.put(b, new AssignedBundle(b, origin));
        } else {
            AssignedBundle ab = _roles.get(b);
            ab.addOrigin(origin);
        }
        
        if (!_noFlattening) {
            List<Bundle> supers = b.getInheritance();
            if (supers != null) {
                for (Bundle s : supers) {
                    addBundle(s, origin);
                }
            }
        }
    }

    private void addViolation(PolicyViolation v) {
        if (_violations == null)
            _violations = new ArrayList<PolicyViolation>();
        _violations.add(v);
    }

    /**
     * Return true if this is considered to be a "severe" violation
     * that warrants a notification and work item.
     * We probably want to allow this to be configured on individual
     * SODConstraint objectds to prevent email overload.
     */
    private boolean isSevere(Policy p, SODConstraint c) {

        return true;
    }

    /**
     * Create a base violation, will be further decorated by the caller.
     */
    private PolicyViolation makeBaseViolation(SODConstraint con) throws GeneralException {

        PolicyViolation v = new PolicyViolation();
        v.setStatus(PolicyViolation.Status.Open);
        v.setActive(true);
        v.setIdentity(_identity);
        v.setPolicy(_policy);
        v.setConstraint(con);
        v.setAlertable(isSevere(_policy, con));
        v.setRenderer(DETAILS_RENDERER);
        Identity owner = _policy.getViolationOwnerForIdentity(_context, _identity, con);
        // Owner may be a reference at this point.  Reattach it so that it doesn't cause problems
        // during workflow execution.
        v.setOwner(ObjectUtil.reattachWithPrejudice(_context, Identity.class, owner));
        
        return v;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role constraints
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * After having determined that the constraint has non-empty
     * left/right role lists, check for conflicts.
     */
    private void evaluateRoleConstraint(SODConstraint con, 
                                        List<Bundle> left, 
                                        List<Bundle> right)
        throws GeneralException {

        if (isViolation(_roles, left, right)) {

            // Note that we still create PolicyViolation
            // objects even if there is a covering mitigation.
            // The violation will be marked as mitigated 
            // for scoring.

            // two possible methods, a single violation for all
            // combinations, or one violation for each
            if (_consolidatedViolations) {
                addViolation(con);
            }
            else {
                // !! rework this to combine with isViolation 
                // we don't need this many list traversals
                for (Bundle l : left) {
                    AssignedBundle al = _roles.get(l);
                    if (al != null) {
                        for (Bundle r : right) {
                            if (r != l) {
                                AssignedBundle ar = _roles.get(r);
                                if (ar != null) {
                                    // Show the origin of the bundles
                                    // in the violation.  May want an option?
                                    addViolation(con, al.getOrigins(), ar.getOrigins());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Test two bundle sets against the identity for a violation.
     * Lists must not be null at this point.
     * 
     * TODO: Should we try to detect and ignore impossible constraints
     * that have the same bundle on both sides?
     */
    private boolean isViolation(Map<Bundle,AssignedBundle> assigned, 
                                List<Bundle> left, 
                                List<Bundle> right) {

        // note that we're doing "or" logic on multiples
        boolean violation = 
            containsAny(assigned, left) && containsAny(assigned, right);

        return violation;
    }

    private boolean containsAny(Map<Bundle,AssignedBundle> master, List things) {

        boolean contains = false;
        if (master != null && things != null) {
            for (Object o : things) {
                if (master.get(o) != null) {
                    contains = true;
                    break;
                }
            }
        }
        return contains;
    }

    /**
     * Build an object with information about one consolodated violation.
     */
    private void addViolation(SODConstraint c) throws GeneralException {

        PolicyViolation v = makeBaseViolation(c);

        // Need to have values for the left/right bundle lists for the UI
        // could find all of them, but for now just stopping on the first one.
        // TODO: basicaly the same walk as isViolation, try to merge...

        List<Bundle> left = c.getLeftBundles();
        List<Bundle> right = c.getRightBundles();
        List<Bundle> involved = new ArrayList<Bundle>();

        boolean stop = false;
        for (int i = 0 ; i < left.size() && !stop ; i++) {
            Bundle l = left.get(i);
            AssignedBundle al = _roles.get(l);
            if (al != null) {
                for (Bundle r : right) {
                    if (l != r) {
                        AssignedBundle ar = _roles.get(r);
                        if (ar != null) {
                            String violation = "";
                            
                            for (Bundle lOrigin : al.getOrigins()) {
                                v.addLeftBundle(lOrigin.getName());
                                involved.add(lOrigin);
                                violation += lOrigin.getName() + " ";
                            }
                            
                            violation += ": ";

                            for (Bundle rOrigin : ar.getOrigins()) {
                                v.addRightBundle(rOrigin.getName());
                                involved.add(rOrigin);
                                violation += rOrigin.getName() + " ";
                            }
                            
                            log.debug(violation);
                            
                            // TODO: option to keep going?
                            //stop = true; // break out of outer loop
                            //break; // and inner loop
                            // Don't stop or break.  bug 14664: just keep accumulating the roles
                            // in the violation, so that they are all collected
                            // into one violation.  This means use addLeftBundle
                            // and addRightBundle above.  We'll assume this is
                            // desired behavior and not provide an option for it.
                            
                        }
                    }
                }
            }
        }
        
        // Derive the "relevant applications" from the conflicting roles,
        // this is used to fitler the policy violations by app.  
        // NOTE: If a role has more than one OR profile on different
        // application, we technically should be determining which
        // one we used to detect this role.  This requires firing
        // up an EntitlementCorrelator though which is expensive, 
        // revisit when we work out a way to do faster targeted
        // entitlement analysis.
        if (involved.size() > 0) {
            List<String> appnames = new ArrayList<String>();
            for (Bundle role : involved) {
                List<Profile> profiles = role.getProfiles();
                if (profiles != null) {
                    for (Profile p : profiles) {
                        Application app = p.getApplication();
                        if (app != null) {
                            String name = app.getName();
                            if (!appnames.contains(name))
                                appnames.add(name);
                        }
                    }
                }
            }
            // check size to reduce clutter in the XML
            if (appnames.size() > 0)
                v.setRelevantApps(appnames);
        }

        // Generiate a "constraint name" if the model didn't have one.
        // We have done this since the dawn of time, but I'm not sure
        // if it is necesssary.  Possibly for the unit tests.

        if (v.getConstraintName() == null) {
            // fake one up from the l/r bundle lists we calculated above
            StringBuffer b = new StringBuffer();
            b.append(v.getLeftBundles());
            b.append(" : ");
            b.append(v.getRightBundles());
            v.setConstraintName(b.toString());
        }

        // allow a rule to post-process the violation
        v = formatViolation(_context, _identity, _policy, c, v);
        addViolation(v);
    }

    /**
     * Build an object with information about one unique violation.
     */
    private void addViolation(SODConstraint c, Set<Bundle> left, Set<Bundle> right) throws GeneralException {

        PolicyViolation v = makeBaseViolation(c);
        for (Bundle l : left) {
            v.addLeftBundle(l.getName());
        }

        for (Bundle r : right) {
            v.addRightBundle(r.getName());
        }

        // Generiate a "constraint name" if the model didn't have one.
        // We have done this since the dawn of time, but I'm not sure
        // if it is necesssary.  Possibly for the unit tests.

        if (v.getConstraintName() == null) {
            // fake one up from the l/r bundle lists we calculated above
            StringBuffer b = new StringBuffer();
            for (Bundle l : left) {
                b.append(l.getName());
                b.append(", ");
            }
            if (b.length() > 1) {
                b = new StringBuffer(b.substring(0, b.length() - 2));
            }
            b.append(" : ");
            for (Bundle r : right) {
                b.append(r.getName());
                b.append(", ");
            }
            v.setConstraintName(b.substring(0, b.length() - 2));
        }

        // allow a rule to post-process the violation
        v = formatViolation(_context, _identity, _policy, c, v);
        addViolation(v);
    }

}

