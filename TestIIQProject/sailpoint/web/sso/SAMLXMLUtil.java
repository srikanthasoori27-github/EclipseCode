/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.InvalidParameterException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilder;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import sailpoint.tools.GeneralException;

public class SAMLXMLUtil {
    
    private static final Log log = LogFactory.getLog(SAMLXMLUtil.class);
    
    private static final Map<Class<?>, QName> elementCache = new ConcurrentHashMap<>();
    
    /**
     * Pretty print an XML object.
     * 
     * @param object
     *            The SAML object
     * @return A SAML object as pretty print XML
     */
    public static String toPrettyPrintXML(XMLObject object) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        Element e1 = marshallObject(object);
    
        return SerializeSupport.prettyPrintXML(e1);
    }
    
    /**
     * Build a new empty object of the requested type.
     * 
     * The requested type must have a DEFAULT_ELEMENT_NAME attribute describing the element type as a QName.
     * 
     * @param <T> SAML Object type
     */
    @SuppressWarnings("unchecked")
    public static <T extends XMLObject> T buildXMLObject(Class<T> type) {
        try {
            QName objectQName = getElementQName(type);
            
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            XMLObjectBuilder<?> builder = builderFactory.getBuilder(objectQName);
            if (builder == null) {
                throw new InvalidParameterException("No builder exists for object: " + objectQName.getLocalPart());
            }
            return (T)builder.buildObject(objectQName);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }               
    }
    
    public static Element marshallObject(XMLObject object) {
        if (object.getDOM() == null) {
            Marshaller m = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(object);
            if (m == null) {
                throw new IllegalArgumentException("No unmarshaller for " + object);
            }
            try {
                return m.marshall(object);
            } catch (MarshallingException e) {
                log.error(e);
            }
        } else {
            return object.getDOM();
        }
        
        return null;
    }
    
    /**
     * Unmarshall a string containing a SAML2.0 document in XML to an XMLObject.
     * 
     * @param elementString
     *            The XML object as a string
     * @return The corresponding {@link XMLObject}
     */
    public static XMLObject unmarshallElementFromString(String elementString) throws GeneralException {
        try {
            Element samlElement = loadElementFromString(elementString);

            Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(samlElement);
            if (unmarshaller == null) {
                log.error("Unable to retrieve unmarshaller by DOM Element");
                throw new IllegalArgumentException("No unmarshaller for " + elementString);
            }

            return unmarshaller.unmarshall(samlElement);
        } catch (UnmarshallingException e) {
            log.error("Unmarshalling failed when parsing element string " + elementString, e);
            throw new GeneralException(e);
        }
    }
    
    /**
     * Parse an XML string.
     * 
     * @param elementString
     *            The String to parse
     * @return The corresponding document {@link Element}.
     */
    public static Element loadElementFromString(String elementString) throws GeneralException {
        try {
            DocumentBuilderFactory newFactory = DocumentBuilderFactory.newInstance();
            newFactory.setNamespaceAware(true);
            
            DocumentBuilder builder = newFactory.newDocumentBuilder();

            Document doc = builder.parse(new ByteArrayInputStream(elementString.getBytes("UTF-8")));
            Element samlElement = doc.getDocumentElement();

            return samlElement;
        } catch (ParserConfigurationException e) {
            log.error("Unable to parse element string " + elementString, e);
            throw new GeneralException(e);
        } catch (SAXException e) {
            log.error("Ue, nable to parse element string " + elementString, e);
            throw new GeneralException(e);
        } catch (IOException e) {
            log.error("Unable to parse element string " + elementString, e);
            throw new GeneralException(e);
        }
    }

    private static <T> QName getElementQName(Class<T> type) {
        if (elementCache.containsKey(type)) return elementCache.get(type);
        
        try {
            Field typeField;
            try { 
                typeField = type.getDeclaredField("DEFAULT_ELEMENT_NAME");
            } catch (NoSuchFieldException ex) {
                typeField = type.getDeclaredField("ELEMENT_NAME");
            }

            QName objectQName = (QName) typeField.get(null);
            elementCache.put(type, objectQName);
            return objectQName;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    
}
