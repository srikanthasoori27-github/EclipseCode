/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 */
public interface EntitlementHistoryItem {

    public CertifiableDescriptor getCertifiableDescriptor();

    
    public String getBusinessRole();

    public EntitlementSnapshot getExceptionEntitlements();

    /**
     * Returns true if the decision represents a decision on the
     * given violation.
     *
     * @param otherViolation PolicyViolation to compare
     * @return True if the given violation is the same as the violation
     * referenced by this decision.
     */
    public boolean isSimiliarViolation(PolicyViolation otherViolation)
        throws GeneralException;
}
