package sailpoint.tools;

import sailpoint.web.messages.MessageKeys;

/**
 * Exception for object not found.
 * Use in REST services when params specify invalid objects.
 *
 */
public class ObjectNotFoundException extends GeneralException {
    
    private Class objectClass;
    private String objectIdentifier;
    
    public ObjectNotFoundException() {
        super();
    }

    /**
     * Construct an exception based on the object type and identifier that cannot be found
     * @param objectClass Class of object searched for
     * @param identifier Identifier used (either ID or name)
     */
    public ObjectNotFoundException(Class objectClass, String identifier) {
        super (new Message(MessageKeys.ERR_OBJECT_NOT_FOUND, 
                (objectClass == null) ? "" : objectClass.getSimpleName(), identifier));
        this.setObjectClass(objectClass);
        this.setObjectIdentifier(identifier);
    }

    /**
     * Construct an exception based on the object type and identifier that cannot be found,
     * with an inner exception
     * @param objectClass Class of object searched for
     * @param identifier Identifier used (either ID or name)
     * @param t Inner exception
     */
    public ObjectNotFoundException(Class objectClass, String identifier, Throwable t) {
        super (new Message(MessageKeys.ERR_OBJECT_NOT_FOUND, 
                (objectClass == null) ? "" : objectClass.getSimpleName(), identifier), t);
        this.setObjectClass(objectClass);
        this.setObjectIdentifier(identifier);
    }

    /**
     * Construct an exception with a custom message
     * @param message Message
     */
    public ObjectNotFoundException(Message message) {
        super(message);
    }

    /**
     * Construct an exception with a custom message and inner exception
     * @param message Message
     * @param t Inner exception
     */
    public ObjectNotFoundException(Message message, Throwable t) {
        super(message, t);
    }

    /**
     * Get the object class
     * @return Object class
     */
    public Class getObjectClass() {
        return objectClass;
    }

    /**
     * Set the object class. This will NOT reset message.
     * @param objectClass Object class
     */
    public void setObjectClass(Class objectClass) {
        this.objectClass = objectClass;
    }

    /**
     * Get the object identifier.
     * @return Object identifier
     */
    public String getObjectIdentifier() {
        return objectIdentifier;
    }

    /**
     * Set the object identifier. This will NOT reset message.
     * @param objectIdentifier Object identifier
     */
    public void setObjectIdentifier(String objectIdentifier) {
        this.objectIdentifier = objectIdentifier;
    }
}