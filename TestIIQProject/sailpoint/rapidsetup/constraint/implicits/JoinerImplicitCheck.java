/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.implicits;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.tools.Util;

/**
 * The ImplicitCheck implementation for the "joiner" process
 */
public class JoinerImplicitCheck implements ImplicitCheck {

    private static Log log = LogFactory.getLog(JoinerImplicitCheck.class);

    private final String JOINER_PROCESS =
            Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES + "," + Configuration.RAPIDSETUP_CONFIG_JOINER;
    private final String REQUIRE_CORRELATED = JOINER_PROCESS + "," +
            Configuration.RAPIDSETUP_CONFIG_JOINER_REQUIRE_CORRELATED;
    private final String AUTO_JOIN_NEW_EMPTY_IDENTITIES = JOINER_PROCESS + "," +
            Configuration.RAPIDSETUP_CONFIG_JOINER_AUTO_JOIN_NEW_EMPTY;
    private final String REPROCESS_SKIPPED = JOINER_PROCESS + "," +
            Configuration.RAPIDSETUP_CONFIG_JOINER_REPROCESS_SKIPPED;

    @Override
    public TriggerShortcut check(Identity previousIdentity, Identity newIdentity, String identityName) {

        if (checkIsAlreadyJoined(newIdentity)) {
            // don't run joiner again on an identity that has already been joined
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + ", previously joined .. identityName.." + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }

        if (forcedJoin(newIdentity)) {
            // this is being forced upon us, probably by some manual editing of an identity
            log.debug("Return " + TriggerShortcut.PERFORM_IMMEDIATELY + ", forced join .. identityName.." + identityName);
            return TriggerShortcut.PERFORM_IMMEDIATELY;
        }

        if (checkIsSkipped(newIdentity)) {
            // identity is already marked as "skipped", so let's not waste our time on it
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + ", because marked '" +
                    Identity.RAPIDSETUP_PROC_STATE_SKIPPED  + "' .. identityName.." + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }

        if (previousIdentity == null) {
            // This is a new identity
            List<Link> links = newIdentity.getLinks();
            if (Util.isEmpty(links)) {
                boolean autoJoinNewEmpties = RapidSetupConfigUtils.getBoolean(AUTO_JOIN_NEW_EMPTY_IDENTITIES);
                if (autoJoinNewEmpties) {
                    log.debug("Return " + TriggerShortcut.PERFORM_IMMEDIATELY + " ... auto-join new empty identities with no accounts ... new identity  " + newIdentity.getName());
                    return TriggerShortcut.PERFORM_IMMEDIATELY;
                }
            }
        }

        boolean requireCorrelated = RapidSetupConfigUtils.getBoolean(REQUIRE_CORRELATED);
        if (requireCorrelated) {
            if (!newIdentity.isCorrelated()) {
                log.debug("Return " + TriggerShortcut.CANCEL_AND_MARK_SKIP + " ... Identity is not correlated ... " + newIdentity.getName());
                return TriggerShortcut.CANCEL_AND_MARK_SKIP;
            }
        }

        String rapidSetupState = newIdentity.getStringAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE);
        boolean needsProcessedState = Identity.RAPIDSETUP_PROC_STATE_NEEDED.equalsIgnoreCase(rapidSetupState);
        if (!needsProcessedState) {
            log.debug(Identity.ATT_RAPIDSETUP_PROC_STATE + " = " + rapidSetupState);
            boolean skippedState = Identity.RAPIDSETUP_PROC_STATE_SKIPPED.equalsIgnoreCase(rapidSetupState);
            if (skippedState) {
                boolean reprocessSkipped = RapidSetupConfigUtils.getBoolean(REPROCESS_SKIPPED);
                if (reprocessSkipped) {
                    log.debug("Reprocessing previously skipped identity " + identityName);
                }
                else {
                    log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + ", skipping again previously skipped identity " + identityName);
                    return TriggerShortcut.CANCEL_IMMEDIATELY;
                }
            }
            else {
                // don't run joiner because existing identity isn't marked as needing joined
                log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + ", " + Identity.ATT_RAPIDSETUP_PROC_STATE + " != '" +
                        Identity.RAPIDSETUP_PROC_STATE_NEEDED + "' .. identityName.." + identityName);
                return TriggerShortcut.CANCEL_IMMEDIATELY;
            }
        }

        // Evaluate the trigger filter, if there is one.
        // If there is no trigger filter, then go ahead with joiner anyways.
        log.debug("Return " + TriggerShortcut.CONTINUE_OPT );
        return TriggerShortcut.CONTINUE_OPT;
    }

    /**
     * @return true if the identity is marked as "skipped", and the reprocessSkipped flag
     * isn't set to true.  Otherwise, false.
     */
    private boolean checkIsSkipped(Identity newIdentity) {
        String rapidSetupState = newIdentity.getStringAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE);
        boolean skipped = Identity.RAPIDSETUP_PROC_STATE_SKIPPED.equalsIgnoreCase(rapidSetupState);
        if (skipped) {
            boolean reprocessSkipped = RapidSetupConfigUtils.getBoolean(REPROCESS_SKIPPED);
            if (reprocessSkipped) {
                skipped = false;
            }
        }
        return skipped;
    }

    private boolean forcedJoin(Identity newIdentity) {
        String rapidSetupState = newIdentity.getStringAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE);
        boolean needsProcessed = Identity.RAPIDSETUP_PROC_STATE_FORCED.equalsIgnoreCase(rapidSetupState);
        if (needsProcessed) {
            log.debug(Identity.ATT_RAPIDSETUP_PROC_STATE + " = " + rapidSetupState);
        }
        return needsProcessed;
    }

    /**
     * is already identity already joined?
     * @param newIdentity
     * @return true if the identity is marked as already having joiner processed for it
     */
    private boolean checkIsAlreadyJoined(Identity newIdentity) {
        String rapidSetupState = newIdentity.getStringAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE);
        boolean alreadyProcessed = Identity.RAPIDSETUP_PROC_STATE_PROCESSED.equalsIgnoreCase(rapidSetupState);
        if (alreadyProcessed) {
            log.debug(Identity.ATT_RAPIDSETUP_PROC_STATE + " = " + rapidSetupState);
        }
        return alreadyProcessed;
    }

}
