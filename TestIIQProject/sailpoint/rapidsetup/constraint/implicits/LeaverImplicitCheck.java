/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.implicits;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.rapidsetup.constraint.implicits.ImplicitCheck.TriggerShortcut;

/**
 * The ImplicitCheck implementation for the "leaver" process
 */
public class LeaverImplicitCheck implements ImplicitCheck {

    private static Log log = LogFactory.getLog(LeaverImplicitCheck.class);

    private final String PROCESS = Configuration.RAPIDSETUP_CONFIG_LEAVER;

    @Override
    public TriggerShortcut check(Identity previousIdentity, Identity newIdentity, String identityName) {
        if (previousIdentity == null) {
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + " ... No previous identity ... " + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }
        // Check if it needs to be correlated
        if (RapidSetupConfigUtils.isRequireCorrelatedForLeaver() && !newIdentity.isCorrelated()) {
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + ", Config requires correlation and Identity is not correlated - cancel ... " + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }

        log.debug("Return " + TriggerShortcut.CONTINUE);
        return TriggerShortcut.CONTINUE;
    }
}
