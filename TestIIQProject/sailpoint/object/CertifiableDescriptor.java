/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;
import java.util.ArrayList;

/**
 * A CertifiableDescriptor holds information that can uniquely identify a
 * Certifiable object or an object holding information about a Certifiable
 * object (such as a CertificationItem).
 */
public class CertifiableDescriptor extends AbstractXmlObject {

    private static final Log log = LogFactory.getLog(CertifiableDescriptor.class);
    
    /**
     * Business role being certified
     */
    private String businessRole;

    /**
     * Exception entitlements being certified
     */
    private EntitlementSnapshot exceptionEntitlements;

    /**
     *  Policy violation being certified
     */
    private PolicyViolation policyViolation;

    /**
     * Cached list of calculated source EntitlementSnapshots
     */
    private List<EntitlementSnapshot> attributeSources;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public CertifiableDescriptor() {
        super();
    }

    /**
     * Constructor from a certification item.
     */
    public CertifiableDescriptor(CertificationItem item) {
        this();

        this.businessRole = item.getBundle();
        this.exceptionEntitlements = item.getExceptionEntitlements();
        this.policyViolation = item.getPolicyViolation();
    }

    /**
     * Constructor from a PolicyViolation.
     */
    public CertifiableDescriptor(PolicyViolation violation) {
        this();
        this.policyViolation = violation;
    }

    /**
     * Constructor for an individual role. This is used when the certifier
     * has revoked a permitted or required role as an additional action on
     * the remediation of the parent role.
     */
    public CertifiableDescriptor(Bundle role) {
        this();
        this.businessRole = role.getName();
    }

    public CertifiableDescriptor(EntitlementSnapshot entitlements) {
        this();
        this.exceptionEntitlements = entitlements;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Role being certified.
     */
    public String getBusinessRole() {
        return businessRole;
    }

    @XMLProperty
    public void setBusinessRole(String businessRole) {
        this.businessRole = businessRole;
    }

    public Bundle getBusinessRole(Resolver resolver) throws GeneralException {
        Bundle bundle = null;
        if (null != this.businessRole) {
            bundle = resolver.getObjectByName(Bundle.class, this.businessRole);
        }
        return bundle;
    }
    
    /**
     * Exception entitlements being certified.
     */
    public EntitlementSnapshot getExceptionEntitlements() {
        return exceptionEntitlements;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setExceptionEntitlements(EntitlementSnapshot exceptionEntitlements) {
        this.exceptionEntitlements = exceptionEntitlements;
    }

    /**
     * Policy violation being certified.
     */
    public PolicyViolation getPolicyViolation() {
        return policyViolation;
    }

    @XMLProperty(mode= SerializationMode.UNQUALIFIED)
    public void setPolicyViolation(PolicyViolation policyViolation) {
        this.policyViolation = policyViolation;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Setters used for upgrading legacy objects
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated  Used to upgrade legacy objects.
     */
    @Deprecated
    public void setPolicyId(String policyId) {
        if (null == this.policyViolation) {
            this.policyViolation = new PolicyViolation();
        }
        this.policyViolation.setPolicyId(policyId);
    }

    /**
     * @deprecated  Used to upgrade legacy objects.
     */
    @Deprecated
    public void setPolicyName(String policyName) {
        if (null == this.policyViolation) {
            this.policyViolation = new PolicyViolation();
        }
        this.policyViolation.setPolicyName(policyName);
    }

    /**
     * @deprecated  Used to upgrade legacy objects.
     */
    @Deprecated
    public void setConstraintId(String constraintId) {
        if (null == this.policyViolation) {
            this.policyViolation = new PolicyViolation();
        }
        this.policyViolation.setConstraintId(constraintId);
    }

    /**
     * @deprecated  Used to upgrade legacy objects.
     */
    @Deprecated
    public void setConstraintRuleName(String constraintRuleName) {
        if (null == this.policyViolation) {
            this.policyViolation = new PolicyViolation();
        }
        this.policyViolation.setConstraintName(constraintRuleName);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return whether this descriptor refers to the same certifiable as the
     * given descriptor.
     */
    public boolean matches(CertifiableDescriptor other)
        throws GeneralException {

        boolean matches = false;

        if (null != other.getBusinessRole()) {
            matches = other.getBusinessRole().equals(this.businessRole);
        }
        else if ((null != other.getExceptionEntitlements()) &&
                 (null != this.exceptionEntitlements)) {

            Differencer.EntitlementSnapshotComparator comparator =
                new Differencer.EntitlementSnapshotComparator();
            // Note that we have to compare the attribute sources as well
            for(EntitlementSnapshot thisSnap : getAttributeSources()){
                for(EntitlementSnapshot thatSnap : other.getAttributeSources()){
                    matches = comparator.areEqual(thatSnap, thisSnap);
                    if (matches)
                        break;
                }
                if (matches)
                    break;
            }
        }
        else if ((null != other.getPolicyViolation()) &&
                 (null != this.policyViolation)) {
            matches = other.isSimiliarViolation(this.policyViolation);
        }

        return matches;
    }

    /**
     * Returns a list of clones of the given snapshot. The attribute name in
     * each clone is replaced by the names of a source attribute for the given
     * entitlement. This is used in cases where the snapshot might reference
     * a groupMembership attribute name.
     */
    public List<EntitlementSnapshot> getAttributeSources(){
        if (attributeSources == null) {
            attributeSources = new ArrayList<EntitlementSnapshot>();
            attributeSources.add(exceptionEntitlements);

            if ( ( exceptionEntitlements != null ) && ( exceptionEntitlements.hasAttributes() ) && 
                 ( exceptionEntitlements.getAttributes().size() ==1 ) ) {
                String attrName = exceptionEntitlements.getAttributes().getKeys().get(0);
                // jsl - need to change this for the new IdentityEntitlement

                List<String> sourceAttrNames = null;
                //List<String> sourceAttrNames =
                //AccountGroupService.findMemberAttributeSources(exceptionEntitlement, s.getApplication(),
                // attrName);

                if (sourceAttrNames != null){
                    for(String srcAttName : sourceAttrNames){
                        Attributes newAttrs = new Attributes<String,Object>();
                        newAttrs.put(srcAttName, exceptionEntitlements.getAttributes().get(attrName));

                        EntitlementSnapshot altSnap = new EntitlementSnapshot(exceptionEntitlements.getApplication(),
                                exceptionEntitlements.getInstance(), exceptionEntitlements.getNativeIdentity(),
                                exceptionEntitlements.getDisplayName(), exceptionEntitlements.getPermissions(),
                                newAttrs);
                        attributeSources.add(altSnap);
                    }
                }
            }

            // loading the snaps prevents a quixotic Hibernate lazy initialization error
            for (EntitlementSnapshot thisSnap : attributeSources) {
                thisSnap.load();
            }
        }
        return attributeSources;
    }
    
    /**
     * Returns true if the comment was made on the given violation.
     *
     * Prior to 3.0 violations were deleted whenever a policy scan ran, so 
     * a comparison had to be performed on the violation details rather than using the
     * ID. This code was kept around for a while so that you can still
     * get an accurate comparison for older violations.
     *
     * @param otherViolation PolicyViolation to compare
     * @return True if the given violation is the same as the violation
     * referenced by this decision.
     *
     * @ignore
     * todo this method is duplicated by CertificationDecision
     *
     */
    public boolean isSimiliarViolation(PolicyViolation otherViolation)
        throws GeneralException {
        
        if (otherViolation == null)
            return false;

        // The PolicyViolation that we store can have a null ID if this was an
        // upgraded legacy object.  Only compare IDs if we have one.  The
        // Interrogator can call us with transient violations, so be sure to
        // make sure the violation has an ID.
        if ((this.policyViolation != null) && (null != this.policyViolation.getId()) &&
            (null != otherViolation) && (null != otherViolation.getId()))
            return otherViolation.getId().equals(policyViolation.getId());

        /*
         * The following code has been deprecated as of 3.0. See note in javadoc
         *
         * This code was copied out of the Interrogator.  Here's Jeff's note:
         *
         * UNIT TEST KLUDGE:
         * For test init files, it is really convenient to create 
         * MitigiationExpiration objects that reference the associated 
         * policies by name rather than id.  To see a mitigation is 
         * related to a violation we therefore have to fetch the Policy
         * and SODConstraint objects and compare both ids and names.
         * Since this adds overhead for an unusual case, we'll only
         * do this if the id appears to be a name based on some
         * extremely complex heuristics.
         */
        boolean areEqual = false;

        if (null != this.policyViolation) {
            String thisPolicyId = this.policyViolation.getPolicyId();
            String otherPolicyId = otherViolation.getPolicyId();
            if (!thisPolicyId.equals(otherPolicyId)) {
                thisPolicyId = getUniqueId(Policy.class, thisPolicyId);
                otherPolicyId = getUniqueId(Policy.class, otherPolicyId);
            }
    
            if (thisPolicyId.equals(otherPolicyId)) {
                // so far so good, must also match the constraint id
                String thisConstraintId = this.policyViolation.getConstraintId();
                String otherConstraintId = otherViolation.getConstraintId();
    
                if ((thisConstraintId == null) && (otherConstraintId == null)) {
                    areEqual = true;
                }
                else if (thisConstraintId != null && otherConstraintId != null) {
    
                    // kludge 2 for name references
                    // this one is trickier since we could in theory
                    // be pointing at things other than an SODConstraint
                    if (!thisConstraintId.equals(otherConstraintId)) {
                        thisConstraintId = getUniqueId(SODConstraint.class, thisConstraintId);
                        otherConstraintId = getUniqueId(SODConstraint.class, otherConstraintId);
                    }
            
                    if (thisConstraintId.equals(otherConstraintId))
                        areEqual = true;
                }
            }
        }

        return areEqual;
    }

    /**
     * To support name references in unit test files.
     * It would be easier and more reliable to catch these during Import!?
     */
    private String getUniqueId(Class<? extends SailPointObject> cls, String id) 
        throws GeneralException {

        String unique = id;

        if (id != null && id.length() > 0) {

            // extremely complex heuristics to determine conclusively
            // if a dude looks like a lady
            if (!Character.isDigit(id.charAt(0))) {
                
                // It looks like a name.  Note that although we try to 
                // keep the unit test object names unique there is no 
                // guarantee, so be careful when calling getObject() which
                // will throw if there are duplicates.
                
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", id));

                // This is a unittest-only situation, so we'll just directly
                // grab the SailPointContext.  This is generally not a great
                // idea, but we'll let it slide so every caller doesn't have
                // to pass in a context.
                List<? extends SailPointObject> result =
                    SailPointFactory.getCurrentContext().getObjects(cls, ops);
                if (result != null && result.size() > 0) {
                    SailPointObject obj = result.get(0);
                    unique = obj.getId();
                    
                    // we need to tolerate this here, but it shouldn't
                    // happen if the unit tests are written properly
                    if (result.size() > 1) {
                        if (log.isWarnEnabled())
                            log.warn("Duplicate object name: " + 
                                     cls.getSimpleName() + " : " + id);
                    }
                }
            }
        }

        return unique;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public boolean equals(Object o) {
        
        if (o == this)
            return true;

        if (!(o instanceof CertifiableDescriptor))
            return false;

        boolean equal = false;
        try {
            equal = this.matches((CertifiableDescriptor) o);
        }
        catch (GeneralException e) {
            log.error(e.getMessage(), e);
        }

        return equal;
    }

    @Override
    public int hashCode() {
        return Util.nullSafeHashCode(this.businessRole) ^
            Util.nullSafeHashCode(this.exceptionEntitlements) ^
            Util.nullSafeHashCode(this.policyViolation);
    }
}
