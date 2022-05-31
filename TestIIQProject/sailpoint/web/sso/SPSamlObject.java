/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.core.xml.XMLObject;

public class SPSamlObject {
    private static final Log log = LogFactory.getLog(SPSamlObject.class);
    
    private final XMLObject obj;

    public SPSamlObject(XMLObject obj) {
        if (obj == null) throw new IllegalArgumentException("Object cannot be null");

        this.obj = obj;
    }

    /**
     * Generate a valid xs:ID string.
     */
    protected static String generateUUID() {
        return "_" + UUID.randomUUID().toString();
    }

}
