package sailpoint.tools.xml;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to add NetIQ specific managed beans to web.xml
 *
 * @author david.crow
 */
public class AgsFacesConfigManipulator {

    public static void main(String[] args) {
        if (args.length != 1) {
            showUsage();
        }

        File facesConfigFile = new File(args[0]);
        if (!facesConfigFile.exists()) {
            showUsage();
        }

        AgsFacesConfigManipulator manipulator = new AgsFacesConfigManipulator();

        Document document = manipulator.getXmlDocument(facesConfigFile);
        Node node = document.getDocumentElement();

        // <managed-bean>
        //   <description>Helps with the Novell Connected Systems Application Generator</description>
        //   <managed-bean-name>novellAppGenerator</managed-bean-name>
        //   <managed-bean-class>netiq.ags.idm.task.NetIQApplicationGeneratorBean</managed-bean-class>
        //   <managed-bean-scope>request</managed-bean-scope>
        // </managed-bean>

        // <managed-bean>
        //   <description>Helps with the Novell Connected Systems Logical Application Generator</description>
        //   <managed-bean-name>novellLogicalAppGenerator</managed-bean-name>
        //   <managed-bean-class>netiq.ags.idm.task.NetIQLogicalApplicationGeneratorBean</managed-bean-class>
        //   <managed-bean-scope>request</managed-bean-scope>
        // </managed-bean>

        Element el = manipulator.createManagedBean(
                        document,
                        "Helps with the Novell Connected Systems Application Generator",
                        "novellAppGenerator",
                        "netiq.ags.idm.task.NetIQApplicationGeneratorBean",
                        "request");
        node.appendChild(el);

        el = manipulator.createManagedBean(
                        document,
                        "Helps with the Novell Connected Systems Logical Application Generator",
                        "novellLogicalAppGenerator",
                        "netiq.ags.idm.task.NetIQLogicalApplicationGeneratorBean",
                        "request");
        node.appendChild(el);

        manipulator.writeDocument(document, facesConfigFile);

    }  // main(String[])

    /**
     *
     * @param doc
     * @param description
     * @param name
     * @param className
     * @param scope
     * @return
     */
    private Element createManagedBean(Document doc, String description,
            String name, String className, String scope) {
        Element managedBean = doc.createElement("managed-bean");

        Element el = doc.createElement("description");
        el.setTextContent(description);
        managedBean.appendChild(el);

        el = doc.createElement("managed-bean-name");
        el.setTextContent(name);
        managedBean.appendChild(el);

        el = doc.createElement("managed-bean-class");
        el.setTextContent(className);
        managedBean.appendChild(el);

        el = doc.createElement("managed-bean-scope");
        el.setTextContent(scope);
        managedBean.appendChild(el);

        return managedBean;
    } // createManagedBean(Document, String, String, String, String)

    /**
     *
     * @param xmlFile
     * @return
     */
    private Document getXmlDocument(File xmlFile) {
        DocumentBuilderFactory documentFactory =  DocumentBuilderFactory.newInstance();
        Document document;
        try {
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            document = documentBuilder.parse(xmlFile);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create document", e);
        }
        return document;
    } // getXmlDocument(File)

    /**
     *
     * @param document
     * @param outputFile
     */
    protected void writeDocument(Document document, File outputFile) {
        TransformerFactory transformerFactory =
                                             TransformerFactory.newInstance();
        try {
            /*
             * This block throws a bunch of different exceptions. There is no
             * recovery just rethrow as unchecked
             */
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty( OutputKeys.ENCODING, "UTF-8" );
            transformer.setOutputProperty( OutputKeys.DOCTYPE_PUBLIC,  "-//Sun Microsystems, Inc.//DTD JavaServer Faces Config 1.0//EN" );
            transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, "http://java.sun.com/dtd/web-facesconfig_1_0.dtd" );
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            Source source = new DOMSource(document);
            Result dest = new StreamResult(new FileOutputStream(outputFile));
            transformer.transform(source, dest);
        } catch (Exception e) {
            throw new RuntimeException("Unable to write " + outputFile.getAbsolutePath(), e);
        }
    } // writeDocument(Document, File)

    /**
     *
     */
    public static void showUsage() {
        System.err.println("AgsFacesConfigManipulator path\\to\\faces-config.xml");
        System.exit(1);
    }
}
