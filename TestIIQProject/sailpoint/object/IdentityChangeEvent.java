/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * An event that gets fired when an identity is created/update/deleted during
 * aggregation, refresh, or through the UI.
 */
@XMLClass
public class IdentityChangeEvent extends AbstractChangeEvent<Identity> {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    // The IdentityTrigger that caused this event (if sourced from a trigger).
    private IdentityTrigger trigger;
    
    
    private List<NativeChangeDetection> nativeChanges;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor - required for XML persistence.
     */
    public IdentityChangeEvent() {
        super();
    }

    /**
     * Constructor for a deletion event.
     */
    public IdentityChangeEvent(String deletedIdentityName) {
        super(deletedIdentityName);
    }

    /**
     * Constructor for a creation event.
     */
    public IdentityChangeEvent(Identity newIdentity) {
        super(newIdentity);
    }
    
    public IdentityChangeEvent(String identityName, List<NativeChangeDetection> detections) {
        super(identityName, Operation.Modify);
        nativeChanges = detections;
    }

    /**
     * Constructor for a modify or create event (if the old identity is null).
     */
    public IdentityChangeEvent(Identity oldIdentity, Identity newIdentity) {
        super(oldIdentity, newIdentity);
        /*
         *  Remove role metadatas from the oldIdentity because they are 
         *  prone to deletion, will quickly become out-dated, and will throw 
         *  Exceptions when the workflow attempts to deserialize them
         */
        if (oldIdentity != null) {
            oldIdentity.setRoleMetadatas(null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the IdentityTrigger that generated this event (if there was one).
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public IdentityTrigger getTrigger() {
        return this.trigger;
    }

    /**
     * Set the IdentityTrigger that generated this event.
     */
    public void setTrigger(IdentityTrigger trigger) {
        this.trigger = trigger;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return the full name of the identity that is being affected. For a
     * deleted identity, this might just be the name.
     */
    public String getIdentityFullName() {
        Identity identity = super.getObject();
        return (null != identity) ? identity.getDisplayableName()
                                  : getName();
    }

    /**
     * Return the name of the identity that is being affected.
     */
    public String getIdentityName() {
        Identity identity = super.getObject();
        return (null != identity) ? identity.getName()
                                  : getName();
    }
    
    private String getName() {
        String name = super.getDeletedObjectName();
        if ( Util.getString(name) == null ) {
            name = super.getObjectName();
        }
        return name;
    }

    /**
     * Return a description of the cause of this event (for example - "inactive changed
     * from false to true").
     */
    public String getCause() {
        return (null != this.trigger) ? this.trigger.formatCause(this) : null;
    }

    @XMLProperty
    public List<NativeChangeDetection> getNativeChanges() {
        return nativeChanges;
    }

    public void setNativeChanges(List<NativeChangeDetection> nativeChanges) {
        this.nativeChanges = nativeChanges;
    }
}
