/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.rapidsetup.constraint.implicits.ImplicitCheck;
import sailpoint.rapidsetup.constraint.implicits.ImplicitCheckFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Used to evaluate if a RapidSetup trigger should proceed to calls its handler for the given identity.
 * Picture this as the rule for the trigger.
 */
public class TriggerPredicate {

    private static Log log = LogFactory.getLog(TriggerPredicate.class);
    private static String TRIGGER_NAME_PREFIX = "RapidSetup";

    public TriggerPredicate() {
    }

    public boolean evaluate(IdentityTrigger trigger, SailPointContext spContext, Identity newIdentity, Identity previousIdentity)
            throws GeneralException
    {
        String identityName = null;
        if(newIdentity != null) {
            identityName = newIdentity.getName();
        }

        String process = trigger.getMatchProcess();
        if (log.isDebugEnabled()) {
            log.debug("Evaluating lifecycleTrigger " + trigger.getName() +
                    " for process = " + process);
            log.debug("Enter evaluate.." + identityName);
        }

        boolean proceed = doEvaluate(process, spContext, newIdentity, previousIdentity);
        if (proceed) {
            /**
             * The process has evaluated to true.  However, we still can't return true until
             * we confirm that no higher-precedence processes are true.  If any are true,
             * then we will have to return false so that the only the highest-precedence one
             * will run.
             */
            List<String> preChecks = TriggerPrecedence.getOrderedCheckFirstList(process);
            for (String processToCheck : preChecks) {
                boolean triggerEnabled = isTriggerEnabled(spContext, processToCheck);
                if (triggerEnabled) {
                    log.debug("Check process " + processToCheck + ", which is higher precedence than " + process);
                    boolean qualified = doEvaluate(processToCheck, spContext, newIdentity, previousIdentity);
                    if (qualified) {
                        log.debug("Trigger " + trigger.getName() + " evaluated to false for identity " + identityName +
                                " because " + processToCheck + " is true");
                        return false;
                    }
                }
            }
        }

        log.debug("Trigger " + trigger.getName() + " evaluated to " + proceed + " for identity " + identityName);
        return proceed;
    }
    /**
     * Evaluate if the trigger condition is met
     * @param process the business process (e.g. joiner, mover)  that is being evaluated
     * @param spContext persistence context
     * @param newIdentity the new state of the identity
     * @param previousIdentity the previous state of the identity
     * @return true if the trigger's handler should be called, given the previous and new identity
     */
    public boolean doEvaluate(String process, SailPointContext spContext, Identity newIdentity, Identity previousIdentity)
    throws GeneralException
    {
        String identityName = null;
        if(newIdentity != null) {
            identityName = newIdentity.getName();
        }

        /**
         * Process-independent checks
         */

        // not enough info, return false
        if (Util.isEmpty(process)) {
            log.debug("Return false ... no process defined in trigger ... " + identityName);
            return false;
        }

        boolean isEnabled = RapidSetupConfigUtils.getBoolean("businessProcesses," + process + ",enabled");
        if (!isEnabled) {
            log.debug("Return false ... " + process + " processing is not enabled");
            return false;
        }

        // newIdentity is null
        if (newIdentity == null) {
            log.debug("Return false ... no new identity ..." + identityName);
            return false;
        }

        // newIdentity is not human
        if (isNotHuman(newIdentity)) {
            return false;
        }

        /**
         * Perform process-specific implicit checks
         */

        ImplicitCheck processImplicitChecker = ImplicitCheckFactory.getImplicitCheck(process);
        ImplicitCheck.TriggerShortcut shortcut = processImplicitChecker.check(previousIdentity, newIdentity, identityName);
        switch(shortcut) {
            case CANCEL_IMMEDIATELY:
                // not going to process this identity
                return false;
            case CANCEL_AND_MARK_SKIP:
                // not going to process this identity, and
                // also need to mark as skipped
                markIdentityAsSkipped(newIdentity);
                return false;
            case PERFORM_IMMEDIATELY:
                // proceed now to the trigger handler
                return true;
            case CONTINUE:
            case CONTINUE_OPT:
                // defer to explicit trigger rules below
                break;
        }

        /**
         * Evaluate the trigger rules configured by user.
         * If shortcut is CONTINUE_OPT then the trigger rules
         * are not required.
         */

        boolean proceed = false;
        Map constraintMap = RapidSetupConfigUtils.getMatchFilter(process);
        if (!Util.isEmpty(constraintMap)) {
            if (constraintMap.containsKey("group")) {
                // Here we go with the evaluation of the constraints.
                ConstraintContext constraintContext = new ConstraintContext(spContext, newIdentity, previousIdentity);
                Map root = (Map) constraintMap.get("group");

                if (hasItems(root)) {
                    proceed = constraintContext.evaluate(root);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Empty or null group defined for RapidSetup business process '" + process + "'.  Return false.");
                    }
                }
            }
            else {
                log.debug("Missing 'group' key in RapidSetup business process '" + process + "'.  Return false.");
            }
        }
        else {
            if (shortcut == ImplicitCheck.TriggerShortcut.CONTINUE_OPT) {
                log.debug("No trigger constraints were needed for RapidSetup business process '" + process + "'.  Return true.");
                proceed = true;
            }
            else {
                if (log.isDebugEnabled()) {
                    log.debug("No trigger constraints declared for RapidSetup business process '" + process + "'.  Return false.");
                }
            }
        }

        if (!proceed && Configuration.RAPIDSETUP_CONFIG_JOINER.equals(process)) {
            // Since we got this far in joiner check, but failed due to trigger filter,
            // we will set the processing state on the identity to "skipped".
            // The identity will therefore be ignored during further Joiner triggers,
            // unless the special "reprocessSkipped" configuration option is set.
            markIdentityAsSkipped(newIdentity);
        }

        return proceed;
    }

    /**
     * Mark the given identity as skipped
     * @param identity the identity to mark as skipped
     */
    private void markIdentityAsSkipped(Identity identity) {
        if (identity != null) {
            final String attrName = Identity.ATT_RAPIDSETUP_PROC_STATE;
            final String attrValue = Identity.RAPIDSETUP_PROC_STATE_SKIPPED;
            log.debug("Setting " + attrName + " to " + attrValue+ " on identity " + identity.getName());
            identity.setAttribute(attrName, attrValue);
        }
    }

    private boolean hasItems(Map group) {
        if (group != null) {
            Object items = group.get("items");
            return (items instanceof List) && !((List)items).isEmpty();
        }

        return false;
    }

    /**
     * Determine if the type of the given identity is not a human type.
     * Currently, this is true only if the type is "service" or "rpa".
     * @param identity the identity (must be non-null) to check if human
     * @return true if the identity is not human.  false if the identity is human.
     */
    private boolean isNotHuman(Identity identity) {
        boolean notHuman = false;
        String type = identity.getType();
        if ("service".equalsIgnoreCase(type) || "rpa".equalsIgnoreCase(type)) {
            log.debug("Return false because identity " + identity.getName() + " is of type " + type);
            notHuman = true;
        }
        return notHuman;
    }

    /**
     * Utility logging method
     * @param trigger the RapidSetup trigger whose identity selector failed
     * @param toMatch the identity that failed the identity selector
     */
    static public void logSelectorFail(IdentityTrigger trigger, Identity toMatch) {
        if (log.isDebugEnabled()) {
            String identityName = null;
            if(toMatch != null) {
                identityName = toMatch.getName();
            }
            String process = trigger.getMatchProcess();

            log.debug("Evaluating lifecycleTrigger " + trigger.getName() +
                        " for process = " + process);
            log.debug("Enter evaluate.." + identityName);
            log.debug("Return false ... identity selector failed");
        }
    }

    /**
     * @return true if the RapidSetup trigger associated with the given process exists
     * and is not disabled and not inactive
     * @throws GeneralException database error occurred
     */
    private boolean isTriggerEnabled(SailPointContext context, String process) throws GeneralException{
        if (Util.isEmpty(process)) {
            return false;
        }

        boolean enabled = false;
        IdentityTrigger identityTrigger = null;
        try {
            String triggerName = TRIGGER_NAME_PREFIX + " " + process.substring(0, 1).toUpperCase() + process.substring(1);
            identityTrigger = context.getObjectByName(IdentityTrigger.class, triggerName);
            if (identityTrigger != null) {
                if (!identityTrigger.isDisabled() && !identityTrigger.isInactive()) {
                    /**
                     * Found and not disabled and not inactive
                     */
                    enabled = true;
                }
            }
        } catch (Exception ex) {
            throw new GeneralException(ex.getMessage());
        } finally {
            if (identityTrigger != null)
                context.decache(identityTrigger);
        }
        return enabled;
    }
}
