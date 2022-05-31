package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Holds meta-data about a role that has been detected or assigned to an
 * identity.
 */
@XMLClass
public class RoleMetadata extends SailPointObject {

    private Bundle role;
    private boolean additionalEntitlements;
    private boolean missingRequired;
    private boolean assigned;
    private boolean detected;
    private boolean detectedException;
    
    /**
     * Default constructor.
     */
    public RoleMetadata() {
        super();
    }
    
    /**
     * Constructor with the role name.
     */
    public RoleMetadata(String name) {
        super();
        setName(name);
    }

    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * For visitation by the Terminator
     * @throws GeneralException
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitRoleMetadata(this);
    }


    // TODO: Not used - remove this.
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Bundle getRole() {
        return role;
    }
    public void setRole(Bundle role) {
        this.role = role;
    }
        
    /**
     * Return true if the identity has any additional entitlements and this is
     * an assigned role. Note that this is not the traditional definition of
     * "additional entitlements" (meaning any entitlement that is not part of a
     * role detection), but any entitlements that a user has that are not part
     * of a permitted or required role definition.
     */
    @XMLProperty
    public boolean isAdditionalEntitlements() {
        return additionalEntitlements;
    }
    public void setAdditionalEntitlements(boolean additionalEntitlements) {
        this.additionalEntitlements = additionalEntitlements;
    }
    
    /**
     * Return true if this is an assigned role and the identity is missing one
     * or more of the required roles.
     */
    @XMLProperty
    public boolean isMissingRequired() {
        return missingRequired;
    }
    public void setMissingRequired(boolean missingRequired) {
        this.missingRequired = missingRequired;
    }
    
    /**
     * Return true if this is an assigned role.
     */
    @XMLProperty
    public boolean isAssigned() {
        return assigned;
    }
    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }
    
    /**
     * Return true if this is a detected role.
     */
    @XMLProperty
    public boolean isDetected() {
        return detected;
    }
    public void setDetected(boolean detected) {
        this.detected = detected;
    }
    
    /**
     * Return true if this is a detected role that is not required or permitted
     * by any of the identity's assigned roles.
     */
    @XMLProperty
    public boolean isDetectedException() {
        return detectedException;
    }
    public void setDetectedException(boolean detectedException) {
        this.detectedException = detectedException;
    }
}
