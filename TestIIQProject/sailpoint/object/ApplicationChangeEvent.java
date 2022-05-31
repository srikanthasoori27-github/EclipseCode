/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;


/**
 * An event that gets fired when an application is created/update/deleted.
 */
@XMLClass
public class ApplicationChangeEvent extends AbstractChangeEvent<Application> {

    /**
     * @exclude
     * @deprecated Default constructor - required for XML persistence.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public ApplicationChangeEvent() {
        super();
    }

    /**
     * Constructor for a deletion event.
     */
    public ApplicationChangeEvent(String deletedObjectName) {
        super(deletedObjectName);
    }

    /**
     * Constructor for a creation event.
     */
    public ApplicationChangeEvent(Application newObject) {
        super(newObject);
    }

    /**
     * Constructor for a modify event.
     */
    public ApplicationChangeEvent(Application oldObject, Application newObject) {
        super(oldObject, newObject);
    }
}
