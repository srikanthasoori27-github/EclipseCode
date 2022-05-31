/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlTools
{
	// ========================================

	public static Document getDocumentFromXmlString(String xml)
	    throws IOException, SAXException, ParserConfigurationException
	{
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        Document document = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
		return document;
	}

	// ========================================

	public static String getFormattedXml(Document document)
	{
        DOMImplementationLS domImpl =
            (DOMImplementationLS) document.getImplementation();
        LSSerializer serializer = domImpl.createLSSerializer();
        serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        return serializer.writeToString(document);
	}
	
	public static String getFormattedXmlWithoutHeader(Element element) {
        DOMImplementationLS domImpl = (DOMImplementationLS) element.getOwnerDocument().getImplementation();
        LSSerializer serializer = domImpl.createLSSerializer();

        serializer.getDomConfig().setParameter("xml-declaration", false);

        return serializer.writeToString(element);
	}
	
	// ========================================

	public static String getFormattedXml(Element element)
	{
		String xml = "";

		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			Document document = dbf.newDocumentBuilder().newDocument();
			document.appendChild(document.importNode(element, true));
			xml = XmlTools.getFormattedXml(document);
		}
		catch (Exception ex)
		{
		}

		return xml;
	}

	// ========================================

	public static Node getXPathNode(String expression, Document document)
	{
		Node returnValue = null;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			returnValue = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static String getXPathNodeAsString(String expression, Document document, String defaultString)
	{
		String returnValue = defaultString;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			returnValue = (String) xpath.evaluate(expression, document, XPathConstants.STRING);

			if (returnValue.equals(""))
			{
				returnValue = defaultString;
			}
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static Node getXPathNode(String expression, Element element)
	{
		Node returnValue = null;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			returnValue = (Node) xpath.evaluate(expression, element, XPathConstants.NODE);
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static String getXPathNodeAsString(String expression, Element element, String defaultString)
	{
		String returnValue = defaultString;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			returnValue = (String) xpath.evaluate(expression, element, XPathConstants.STRING);

			if (returnValue.equals(""))
			{
				returnValue = defaultString;
			}
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static String getXPathNodeAsString(String namespacePrefix, String namespaceUri, String expression,
			Element element, String defaultString)
	{
		String returnValue = defaultString;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			NamespaceContextImpl nci = new NamespaceContextImpl();
			nci.addNamespaceContext(namespacePrefix, namespaceUri);
			xpath.setNamespaceContext(nci);

			if (returnValue.equals("")) returnValue = (String) xpath.evaluate(expression, element,
					XPathConstants.STRING);
			{
				returnValue = defaultString;
			}
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static String getXPathNodeAsString(String expression, String xml, String defaultString)
	{
		String returnValue = defaultString;

		try
		{
			Document document = XmlTools.getDocumentFromXmlString(xml);
			XPath xpath = XPathFactory.newInstance().newXPath();
			returnValue = (String) xpath.evaluate(expression, document, XPathConstants.STRING);

			if (returnValue.equals(""))
			{
				returnValue = defaultString;
			}
		}
		catch (Exception ex)
		{
		}

		return returnValue;
	}

	// ========================================

	public static Element addNode(Document document, Element element, String tagName, String data)
			throws ParserConfigurationException
	{
		Element tagElement = document.createElement(tagName);
		tagElement.appendChild(document.createTextNode(data));
		element.appendChild(tagElement);

		return tagElement;
	}
}
