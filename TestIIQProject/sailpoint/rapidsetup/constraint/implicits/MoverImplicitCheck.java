/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.implicits;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.RapidSetupConfigUtils;

/**
 * The ImplicitCheck implementation for the "mover" process
 */
public class MoverImplicitCheck implements ImplicitCheck {

    private final String MOVER_PROCESS =
            Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES + "," + Configuration.RAPIDSETUP_CONFIG_MOVER;

    private final String REQUIRE_CORRELATED = MOVER_PROCESS + "," +
            Configuration.RAPIDSETUP_CONFIG_JOINER_REQUIRE_CORRELATED;

    private static Log log = LogFactory.getLog(MoverImplicitCheck.class);

    @Override
    public TriggerShortcut check(Identity previousIdentity, Identity newIdentity, String identityName) {
        if (previousIdentity == null) {
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + " ... No previous identity ... " + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }

        boolean requireCorrelated = RapidSetupConfigUtils.getBoolean(REQUIRE_CORRELATED);

        // Must be correlated
        if (requireCorrelated && !newIdentity.isCorrelated()) {
            log.debug("Return " + TriggerShortcut.CANCEL_IMMEDIATELY + " ... Identity is not correlated ... " + identityName);
            return TriggerShortcut.CANCEL_IMMEDIATELY;
        }

        log.debug("Return " + TriggerShortcut.CONTINUE);
        return TriggerShortcut.CONTINUE;
    }

}
