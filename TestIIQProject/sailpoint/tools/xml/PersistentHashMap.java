/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 *
 * An extension of HashMap that implements the PersistentXmlObject interface
 * so we can remember the original XML for change detection.
 *
 * Author: Jeff
 *
 */

package sailpoint.tools.xml;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PersistentHashMap<K,V> extends HashMap<K,V> 
    implements PersistentXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // PersistentXmlObject
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The XML representation of this object when it was first brought
     * out of persistent storage.  A hack used for Hibernate to optimize
     * the comparison of XML custom types.
     */
    String _originalXml;

    public void setOriginalXml(String xml) {
        _originalXml = xml;
    }

    @JsonIgnore
    public String getOriginalXml() {
        return _originalXml;
    }

}
