/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rapidsetup.constraint.implicits;

import sailpoint.object.Identity;

public interface ImplicitCheck {
    
    TriggerShortcut check(Identity previousIdentity, Identity newIdentity, String identityName);

    enum TriggerShortcut {
        CONTINUE,            // continue with additional checks
        CONTINUE_OPT,        // continue with optional additional checks
        CANCEL_IMMEDIATELY,  // we know this should not be done
        CANCEL_AND_MARK_SKIP,// we know this should not be done, and should be marked as skipped
        PERFORM_IMMEDIATELY  // we know this should be done
    }
}
