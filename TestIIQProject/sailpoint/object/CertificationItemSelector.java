/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 * Interface that tells whether a certification item matches certain criteria.
 */
public interface CertificationItemSelector {

    /**
     * Return whether the given CertificationItem matches the criteria set up in
     * this selector.
     * 
     * @param  item  The CertificationItem to check for a match.
     * 
     * @return True if the given CertificationItem matches the criteria set up
     *         in this selector, false otherwise.
     */
    public boolean matches(CertificationItem item) throws GeneralException;
}
