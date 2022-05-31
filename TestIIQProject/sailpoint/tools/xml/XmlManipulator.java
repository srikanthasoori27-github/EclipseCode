package sailpoint.tools.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class XmlManipulator {

    public XmlManipulator() {
        super();
    }

    protected abstract NodeList getInterestingNodes( Document document );
    protected abstract boolean isNodeToRemove( Node node );

    public void manipulate( File connectorRegistryFile ) {
        Document document = getXmlDocument( connectorRegistryFile );
        NodeList nodes = getInterestingNodes( document );
        List<Node> nodesToRemove = new ArrayList<Node>();
        processNodes( nodes, nodesToRemove );
        removeNodes( nodesToRemove );
        writeDocument( document, connectorRegistryFile );
    }
    
    protected Document getXmlDocument( File xmlFile ) {
        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
        documentFactory.setValidating(false);
        documentFactory.setCoalescing(true);
        try {
            documentFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            documentFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to disable dtd loading");
        }

        Document document;
        try {
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            document = documentBuilder.parse( xmlFile );
        } catch ( Exception e ) {
            throw new RuntimeException( "Unable to create document", e );
        }
        return document;
    }

    private void processNodes( NodeList applications, List<Node> applicationsToRemove ) {
        for( int i = 0; i < applications.getLength(); i++ ) {
            Node application = applications.item( i );
            processNode( application, applicationsToRemove );
        }
    }
    
    protected void processNode( Node node, List<Node> nodesToRemove ) {
        if( isNodeToRemove( node ) ) {
            nodesToRemove.add( node );
        }
    }
    
    private void removeNodes( List<Node> applicationsToRemove ) {
        for( Node application : applicationsToRemove ) {
            Node parentNode = application.getParentNode();
            parentNode.removeChild( application );
        }
    }
    
    protected void writeDocument( Document document, File outputFile ) {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            /* This block throws a bunch of different exceptions. There is no 
             * recovery just rethrow as unchecked */
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty( OutputKeys.ENCODING, "UTF-8" );
            transformer.setOutputProperty( OutputKeys.DOCTYPE_PUBLIC, "netiq.dtd" );
            transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, "netiq.dtd" );
            Source source = new DOMSource( document );
            Result dest = new StreamResult( new FileOutputStream( outputFile ) );
            transformer.transform( source, dest );
        } catch ( Exception e ) {
            throw new RuntimeException( "Unable to write ConnectorRegistry", e );
        }
    }

    protected Node getAttribute( Node node, String attribute ) {
        NamedNodeMap nodeAttributes = node.getAttributes();
        Node featureAttribute = nodeAttributes.getNamedItem( attribute );
        return featureAttribute;
    }

}