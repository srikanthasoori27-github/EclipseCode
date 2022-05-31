/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.implicits;

import sailpoint.object.Identity;

/**
 * This implementation does nothing.  Used instead of worrying about a null to represent noop.
 */
public class NoOpImplicitCheck implements ImplicitCheck {

    /**
     * @return null always
     */
    @Override
    public TriggerShortcut check(Identity previousIdentity, Identity newIdentity, String identityName) {
        return TriggerShortcut.CONTINUE;
    }

}
