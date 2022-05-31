/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * An event that gets fired when an object is created/update/deleted.  
 *
 * @ignore
 * This could be subclassed since deletions use different fields from creations and
 * modifies, but it seems like overkill for now.
 *
 */
@XMLClass
public abstract class AbstractChangeEvent <T> extends AbstractXmlObject {

    /**
     * Enumeration of operations on the identity.
     */
    @XMLClass(xmlname="ChangeEventOperation")
    public static enum Operation {
        Add,
        Modify,
        Remove
    }

    private T oldObject;
    private T newObject;
    private Operation operation;
        
    /**
     * @deprecated  field that is only used prior to 6.0.
     */
    private String deletedObjectName;
    
    /**
     * Newer field used to hold the objectName involved
     * in the event.  
     */
    private String objectName;
    
    /**
     * @exclude
     * Default constructor - required for XML persistence.
     * @deprecated use parameterized constructor
     */
    @Deprecated
    protected AbstractChangeEvent() {}

    /**
     * Constructor for a deletion event.
     */
    protected AbstractChangeEvent(String objectName) {
        this.objectName = objectName;
        this.operation = Operation.Remove;
    }
    
    protected AbstractChangeEvent(String objectName, Operation op ) {
        this.objectName = objectName;
        this.operation = op;
    }

    /**
     * Constructor for a creation event.
     */
    protected AbstractChangeEvent(T newObject) {
        this.newObject = newObject;
        this.operation = Operation.Add;
    }

    /**
     * Constructor for a modify or create event (if the old object is null).
     */
    protected AbstractChangeEvent(T oldObject, T newObject) {
        this.oldObject = oldObject;
        this.newObject = newObject;
        this.operation = (null != oldObject) ? Operation.Modify : Operation.Add;
    }
    
    @XMLProperty
    public T getOldObject() {
        return this.oldObject;
    }
    
    /**
     * @exclude
     * This field is immutable, but the setter is required for XML serialization.
     * @deprecated set using constructor
     */
    @Deprecated
    public void setOldObject(T t) {
        this.oldObject = t;
    }
    
    @XMLProperty
    public T getNewObject() {
        return this.newObject;
    }
    
    /**
     * @exclude
     * This field is immutable, but the setter is required for XML serialization.
     * @deprecated set using constructor
     */
    @Deprecated
    public void setNewObject(T t) {
        this.newObject = t;
    }
    
    /**
     * Convenience method that returns the new object (if available),
     * otherwise this returns the old object.  This can return null for a
     * deletion event.
     */
    public T getObject() {
        return (null != this.newObject) ? this.newObject : this.oldObject;
    }
    
    @XMLProperty
    public Operation getOperation() {
        return this.operation;
    }

    /**
     * @exclude
     * This field is immutable, but the setter is required for XML serialization.
     * @deprecated set using constructor
     */
    @Deprecated
    public void setOperation(Operation op) {
        this.operation = op;
    }
    
    /**
     * @deprecated for a more generic method {@link #getObjectName()}.
     * 
     * @return The name of the deleted object.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @XMLProperty
    public String getDeletedObjectName() {
        String name = deletedObjectName;
        if ( name == null ) 
            name = getObjectName();
        return name;
    }

    /**
     * @exclude
     * @exclude
     * This field is immutable, but the setter is required for XML serialization.
     * @deprecated set using constructor
     */
    @Deprecated
    public void setDeletedObjectName(String s) {
        this.objectName = s;
    }

    @XMLProperty
    public String getObjectName() {
        return objectName;
    }

    /**
     * @exclude
     * This field is immutable, but the setter is required for XML serialization.
     * @deprecated set using constructor
     */
    @Deprecated
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
}
