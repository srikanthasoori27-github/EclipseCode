/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import sailpoint.object.SailPointObject;

/**
 * ModifyImmutableException should be thrown whenever someone attempts to modify a SailPointObject that is immutable.
 *
 * @author: michael.hide
 * Created: 2/23/15 2:17 PM
 */
public class ModifyImmutableException extends GeneralException {

    /**
     * Private variable for holding the object that someone tried to modify.
     */
    private SailPointObject spObject;

    /**
     * Construct an exception with a custom message and the SailPointObject that someone attempted to modify.
     *
     * @param object The offending object
     * @param msg Custom message
     */
    public ModifyImmutableException(SailPointObject object, Message msg) {
        super(msg);
        this.spObject = object;
    }

    /**
     * Get the SailPointObject, if defined in constructor.
     *
     * @return SailPointObject
     */
    public SailPointObject getObject() {
        return this.spObject;
    }

    /**
     * Set the SailPointObject. This will NOT reset the message.
     *
     * @param object SailPointObject
     */
    public void setObject(SailPointObject object) {
        this.spObject = object;
    }
}
