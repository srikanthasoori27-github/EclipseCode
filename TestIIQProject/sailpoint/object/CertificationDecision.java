/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * Stores the decision details for a given certifiable.
 */
@Deprecated
@XMLClass
public class CertificationDecision extends SailPointObject implements EntitlementHistoryItem {

    private CertificationLink certificationLink;

    private CertifiableDescriptor certifiableDescriptor;

    /**
     * The action taken on the certification item
     */
    private CertificationAction action;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor
     */
    public CertificationDecision() {
    }

    /**
     * Creates a decision record with for the given certification item.
     *
     * @param item CertificationItem in question
     * @throws GeneralException
     */
    public CertificationDecision(CertificationItem item) throws GeneralException{
        this(item, new CertifiableDescriptor(item));
    }

    /**
     * Create a decision for the given policy violation. The violation should have been
     * handled outside of a certification, otherwise use CertificationItem constructor.
     *
     * @param newViolation The PolicyViolation in quest.
     * @param action The action details: comments, description etc
     * @throws GeneralException
     */
    public CertificationDecision(PolicyViolation newViolation,
                                 CertificationAction action)
        throws GeneralException {

        this.action = action;
        this.certifiableDescriptor = new CertifiableDescriptor(newViolation);
    }

    /**
     * Creates a decision record with the given item and certifiable. Useful in
     * cases where the certifiable is not the primary entitlement being certified.
     * For example -  cases where the certifier has chosen to revoke the required
     * and/or permitted roles for a given role. In that case the certifiableDesc
     * will not match the role on the certification item.
     * @param item
     * @param certifiableDesc
     */
    public CertificationDecision(CertificationItem item,
                                 CertifiableDescriptor certifiableDesc){
        if (item.getCertification() != null)
            this.certificationLink =
                new CertificationLink(item.getCertification() , item.getParent());
        this.action = item.getAction();
        this.certifiableDescriptor = certifiableDesc;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides.
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @exclude
     * This is only persisted as XML.
     */
    @Override
    public boolean isXml() {
        return true;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setAction(CertificationAction action) {
        this.action = action;
    }

    public CertificationAction getAction() {
        return action;
    }

    /**
     * @exclude
     * jsl - temporary pseudo property to upgrade the <Action> wrapper
     */
    @XMLProperty(xmlname="Action")
    public void setXmlAction(CertificationAction action) {
        this.action = action;
    }

    /**
     * @exclude
     */
    public CertificationAction getXmlAction() {
        return null;
    }

    public CertificationLink getCertificationLink() {
        return certificationLink;
    }

    @XMLProperty(mode= SerializationMode.INLINE)
    public void setCertificationLink(CertificationLink certificationLink) {
        this.certificationLink = certificationLink;
    }

    public CertifiableDescriptor getCertifiableDescriptor() {
        return this.certifiableDescriptor;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setCertifiableDescriptor(CertifiableDescriptor desc) {
        this.certifiableDescriptor = desc;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // EntitlementHistoryItem interface
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getBusinessRole() {
        return this.certifiableDescriptor.getBusinessRole();
    }

    public EntitlementSnapshot getExceptionEntitlements() {
        return this.certifiableDescriptor.getExceptionEntitlements();
    }

    public PolicyViolation getPolicyViolation() {
        return this.certifiableDescriptor.getPolicyViolation();
    }

    /**
     * Returns true if the decision was mode of the given violation.
     *
     * The second half of this method includes deprecated code that 
     * kept for pre-3.0 installs. See the notes on
     * Violation Attributes above.
     *
     * @param otherViolation PolicyViolation to compare
     * @return True if the given violation is the same as the violation
     * referenced by this decision.
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
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(legacy=true)
    public void setBusinessRole(String businessRole) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setBusinessRole(businessRole);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED, legacy=true)
    public void setExceptionEntitlements(EntitlementSnapshot exceptionEntitlements) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setExceptionEntitlements(exceptionEntitlements);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    // jsl - temporary property to remove the <ExceptionEntitlements> wrapper
    @XMLProperty(xmlname="ExceptionEntitlements", legacy=true)
    public void setXmlExceptionEntitlements(EntitlementSnapshot exceptionEntitlements) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setExceptionEntitlements(exceptionEntitlements);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED, legacy=true)
    public void setPolicyViolation(PolicyViolation policyViolation) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyViolation(policyViolation);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(legacy=true)
    public void setConstraintId(String constraintId) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setConstraintId(constraintId);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(legacy=true)
    public void setConstraintRuleName(String constraintRuleName) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setConstraintRuleName(constraintRuleName);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(legacy=true)
    public void setPolicyId(String policyId) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyId(policyId);
    }

    /**
     * @exclude
     * @deprecated  This information is now on the CertifiableDescriptor.
     */
    @XMLProperty(legacy=true)
    public void setPolicyName(String policyName) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyName(policyName);
    }
}
