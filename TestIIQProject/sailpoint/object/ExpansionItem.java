/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An ExpansionItem contains information about why a value was expanded in
 * a provisioning plan as a result of plan compilation. This contains
 * information that identifies the expanded value (application, native identity,
 * name, value, etc...) and information about the expansion (why did it happen).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@XMLClass
public class ExpansionItem extends IdentityItem {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * An enumeration of reasons for expansion.
     */
    @XMLClass(xmlname="ExpansionCause")
    public static enum Cause {

        /**
         * The value was expanded due to a role. This could be be from a role's
         * profile, a provisioning policy on a role, etc...
         */
        Role,
        
        /**
         * The value was expanded due to attribute synchronization, which is
         * controlled by targets on identity attributes.
         */
        AttributeSync,
        
        /**
         * The value was expanded due to an attribute assignment which had been
         * lost and is being reprovisioned.
         */
        AttributeAssignment,
        
        /**
         * The value was expanded due to evaluation of a provisioning policy on
         * an application.
         */
        ProvisioningPolicy
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * The cause of the expansion.
     */
    private Cause cause;

    /**
     * The operation that indicates whether this item is being added or removed
     * from the identity.
     */
    private ProvisioningPlan.Operation operation;

    /**
     * Information about what caused the expansion. This will differ based
     * on the cause.
     * 
     * Role expansion: The name of the role.
     * Attribute sync: The name of the identity attribute.
     * AttributeAssignment: The AttributeAssignment source.
     * Provisioning policy: The name of the application the policy lives on.
     */
    private String sourceInfo;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public ExpansionItem() {
        super();
    }

    /**
     * Constructor for an attribute.
     */
    public ExpansionItem(String application, String instance,
                         String nativeIdentity, String attr, Object value,
                         ProvisioningPlan.Operation operation,
                         Cause cause, String sourceInfo) {
        this(application, instance, nativeIdentity, operation, cause, sourceInfo);
        _name = attr;
        _value = value;
    }

    /**
     * Constructor for a permission.
     */
    public ExpansionItem(String application, String instance,
                         String nativeIdentity, String target, String right,
                         ProvisioningPlan.Operation operation,
                         Cause cause, String sourceInfo) {
        this(application, instance, nativeIdentity, operation, cause, sourceInfo);
        _name = target;
        _value = right;
    }

    /**
     * Constructor with basic information.
     */
    private ExpansionItem(String application, String instance,
                          String nativeIdentity,
                          ProvisioningPlan.Operation operation,
                          Cause cause, String sourceInfo) {
        _application = application;
        _instance = instance;
        _nativeIdentity = nativeIdentity;
        this.operation = operation;
        this.cause = cause;
        this.sourceInfo = sourceInfo;
    }    
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @XMLProperty
    public Cause getCause() {
        return this.cause;
    }
    
    public void setCause(Cause cause) {
        this.cause = cause;
    }

    @XMLProperty
    public ProvisioningPlan.Operation getOperation() {
        return this.operation;
    }
    
    public void setOperation(ProvisioningPlan.Operation operation) {
        this.operation = operation;
    }
    
    @XMLProperty
    public String getSourceInfo() {
        return this.sourceInfo;
    }
    
    public void setSourceInfo(String sourceInfo) {
        this.sourceInfo = sourceInfo;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OBJECT OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExpansionItem))
            return false;
        
        if (o == this)
            return true;
        
        ExpansionItem i = (ExpansionItem) o;
        return new EqualsBuilder()
                    .append(_application, i._application)
                    .append(_instance, i._instance)
                    .append(_nativeIdentity, i._nativeIdentity)
                    .append(_name, i._name)
                    .append(_value, i._value)
                    .append(this.operation, i.operation)
                    .append(this.cause, i.cause)
                    .append(this.sourceInfo, i.sourceInfo).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                    .append(_application)
                    .append(_instance)
                    .append(_nativeIdentity)
                    .append(_name)
                    .append(_value)
                    .append(this.operation)
                    .append(this.cause)
                    .append(this.sourceInfo).toHashCode();
    }
}
