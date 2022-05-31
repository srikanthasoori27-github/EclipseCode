/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * Stores comments made on a given certifiable.
 */
@Deprecated
public class EntitlementComment extends Comment implements EntitlementHistoryItem {

    private CertifiableDescriptor certifiableDescriptor;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors.
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public EntitlementComment() {
        super();
    }

    /**
     * Constructor from a CertificationItem.
     */
    public EntitlementComment(CertificationItem item) {
        this();
        this.certifiableDescriptor = new CertifiableDescriptor(item);
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public CertifiableDescriptor getCertifiableDescriptor() {
        return this.certifiableDescriptor;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setCertifiableDescriptor(CertifiableDescriptor desc) {
        this.certifiableDescriptor = desc;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // EntitlementHistoryItem implementation
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getBusinessRole() {
        return this.certifiableDescriptor.getBusinessRole();
    }
    
    public EntitlementSnapshot getExceptionEntitlements() {
        return this.certifiableDescriptor.getExceptionEntitlements();
    }
    
    /**
     * Returns true if the comment was made on the given violation.
     *
     * Prior to 3.0 violations were deleted whenever a policy scan was run, so
     * ha comparison had to be performed on the violation details rather than using the
     * ID. This code needs to remain for a while so that
     * an accurate comparison can be obtained for older violations.
     *
     * @param otherViolation PolicyViolation to compare
     * @return True if the given violation is the same as the violation
     * referenced by this decision.
     *
     * @ignore
     * todo this method is duplicated by CertificationDecision
     */
    public boolean isSimiliarViolation(PolicyViolation otherViolation)
        throws GeneralException {
        return this.certifiableDescriptor.isSimiliarViolation(otherViolation);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Legacy setters for lazily upgrading XML.
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated  Kept for legacy comments - now stored in the CertifiableDescriptor. 
     */
    @XMLProperty(legacy=true)
    public void setBusinessRole(String businessRole) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setBusinessRole(businessRole);
    }

    /**
     * @deprecated  Kept for legacy comments - now stored in the CertifiableDescriptor. 
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED, legacy=true)
    public void setPolicyViolation(PolicyViolation policyViolation) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyViolation(policyViolation);
    }

    /**
     * @deprecated  Kept for legacy comments - now stored in the CertifiableDescriptor. 
     */
    @XMLProperty(legacy=true)
    public void setExceptionEntitlements(EntitlementSnapshot exceptionEntitlements) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setExceptionEntitlements(exceptionEntitlements);
    }

    /**
     * @deprecated Kept around for older (pre-3.0) violation decisions
     */
    @XMLProperty(legacy=true)
    public void setPolicyId(String policyId) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyId(policyId);
    }

    /**
     * @deprecated Kept around for older (pre-3.0) violation decisions
     */
    @XMLProperty(legacy=true)
    public void setPolicyName(String policyName) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyName(policyName);
    }

    /**
     * @deprecated Kept around for older (pre-3.0) violation decisions
     */
    @XMLProperty(legacy=true)
    public void setConstraintId(String constraintId) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setConstraintId(constraintId);
    }

    /**
     * @deprecated Kept around for older (pre-3.0) violation decisions
     */
    @XMLProperty(legacy=true)
    public void setConstraintRuleName(String constraintRuleName) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setConstraintRuleName(constraintRuleName);
    }
}
