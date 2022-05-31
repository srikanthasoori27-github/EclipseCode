/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 * An identity matcher can evaluate an IdentitySelector against an identity to
 * determine whether it matches or not. This interface is here to allow classes
 * in the object package to match without explicitly referencing the Matchmaker
 * in the api package.
 */
public interface IdentityMatcher {

    /**
     * Return whether the given identity matches the given selector.
     * 
     * @param  selector  The IdentitySelector to evaluate.
     * @param  identity  The Identity to attempt to match.
     * 
     * @return True if the given Identity matches the selector, false otherwise.
     */
    public boolean isMatch(IdentitySelector selector, Identity identity)
        throws GeneralException;
}
