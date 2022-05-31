/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of PolicyExecutor that provides utility
 * methods common to most executors.
 *
 * Author: Jeff
 */

package sailpoint.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyExecutor;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;

/**
 * An implementation of PolicyExecutor that provides utility
 * methods common to most executors.
 */
public class AbstractPolicyExecutor implements PolicyExecutor {

	private static Log log = LogFactory.getLog(AbstractPolicyExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Arguments to the violation formatting rule.
     */
    public static final String ARG_STATE = "state";
    public static final String ARG_IDENTITY = "identity";
    public static final String ARG_VIOLATION = "violation";
    public static final String ARG_POLICY = "policy";
    public static final String ARG_CONSTRAINT = "constraint";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Map of arbitrary state that will live for as long
     * as the executor is being used and will be passed into
     * the violation formatter rule.  This allows the rule
     * to maintain caches of things that need to be used  in
     * each call to the rule.
     *
     * It is preferred  that Interrogator managing this but then 
     * it would have to pass it in through the PolicyExecutor.evalute
     * method which would be a non-backward compatible extension.
     */
    Map<String,Object> _state;

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Are we in simulation mode (for example, links can be in memory and not in database)?
     */
    boolean _simulating;

    public void setSimulating(boolean val) {
        _simulating = val;
    }
    
    protected boolean isSimulating() {
        return _simulating;
    }

    /**
     * @exclude
     * Most policies are applied to a particular Identity during
     * an iteration managed by the Interrogator.
     * A context is provided so the executor can examine other objects.
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy, 
                                          Identity id)
        throws GeneralException {

        throw new UnsupportedOperationException();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * After generating a violation, run the formatting rule if one exists.
     * Do not throw, if there is a problem in the rule use the default
     * violation.
     *
     * The rule can either modify the passed violation or generate a new one.
     */
    public PolicyViolation formatViolation(SailPointContext context,
                                           Identity identity, 
                                           Policy policy,
                                           BaseConstraint constraint,
                                           PolicyViolation violation) {

        try {
            Rule rule = null;

            // constraint is optional for some really simple policies
            if (constraint != null)
                 rule = constraint.getViolationRuleObject(context);

            // if constraint doesn't have a rendering rule can have
            // a global one on the policy
            if (rule == null)
                rule = policy.getViolationRuleObject(context);
            
            if (rule != null) {
                Map<String,Object> args = new HashMap<String,Object>();
                args.put(ARG_IDENTITY, identity);
                args.put(ARG_POLICY, policy);
                args.put(ARG_CONSTRAINT, constraint);
                args.put(ARG_VIOLATION, violation);

                // this lets the rule maintain state between calls
                if (_state == null)
                    _state = new HashMap<String,Object>();
                args.put(ARG_STATE, _state);

                Object ret = context.runRule(rule, args);

                if (ret instanceof PolicyViolation) {
                    // I suppose we can let the rule create an entirely
                    // new violation, but there really shouldn't be a need.
                    violation = (PolicyViolation)ret;
                }
            }
        }
        catch (Throwable t) {
            // don't let rule misconfiguration stop the whole thing?
            log.error(t.getMessage(), t);
        }

        return violation;
    }

}
