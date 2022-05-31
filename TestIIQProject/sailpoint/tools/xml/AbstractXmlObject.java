/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Provides a set of common XML serialization methods.
 * You don't have to be a subclass of this to be managed
 * by the XMLObjectFactory, it is only a convenience.
 */

package sailpoint.tools.xml;

import java.io.Serializable;

import org.w3c.dom.Element;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Base class for objects that want to provide an XML
 * serialization but do not need to be first-class persistent 
 * SailPointObjects.
 */
public abstract class AbstractXmlObject implements PersistentXmlObject, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // PersistentXmlObject
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The XML representation of this object when it was first brought
     * out of persistent storage. Used for Hibernate to optimize
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

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Serializes.
     */
    public String toXml() throws GeneralException {
        return toXml(true);
    }

    public String toXml(boolean includeHeader) throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.toXml(this, includeHeader);
    }

    public void toXml(XMLBuilder b) throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        f.toXml(this, b);
    }

    /**
     * Clones.
     */
    public Object deepCopy(XMLReferenceResolver res)  
        throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.clone(this, res);
    }

    /**
     * Debug.
     */
    public void writeXml(String filename) throws GeneralException {
        String xml = toXml();
        Util.writeFile(filename, xml);
    }

    /**
     * Deserialize an object from XML.
     * Resolver is normally a SailPointContext, but could be a 
     * PersistenceManager or something else if necessary.
     */
    static public Object parseXml(XMLReferenceResolver resolver, String xml, boolean validate) 
        throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.parseXml(resolver, xml, validate);
    }

    static public Object parseXml(XMLReferenceResolver resolver, String xml) 
        throws GeneralException {

        // should validation be on by default?
        return parseXml(resolver, xml, false);
    }

    static public Object parseXml(XMLReferenceResolver resolver, Element e)
        throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.parseElement(resolver, e);
    }
    

}
