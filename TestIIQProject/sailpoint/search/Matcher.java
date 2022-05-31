/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import sailpoint.tools.GeneralException;

/**
 * A Matcher accepts an Object and returns whether the object matches based on
 * its matching algorithm.  This was developed originally to work in conjunction
 * with <code>Filter</code>s, but can be used more generally.
 * 
 * @see sailpoint.object.Filter
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface Matcher
{
    /**
     * Return whether the given Object matches based on the matching algorithm
     * of this Matcher.
     * 
     * @param  o  The Object for which to determine matching.
     * 
     * @return True if the given Object matches based on the matching algorithm
     *         of this Matcher, false otherwise.
     *
     * @throws GeneralException  If an exception occurs while matching.
     */
    public boolean matches(Object o) throws GeneralException;
}
