/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

public class NamespaceContextImpl implements NamespaceContext
{
	Map<String, String> boundURIs = new HashMap<String, String>();

	Map<String, String> boundPrefixes = new HashMap<String, String>();

	public void addNamespaceContext(String prefix, String URI)
	{
		boundURIs.put(prefix, URI);
		boundPrefixes.put(URI, prefix);
	}

	public String getNamespaceURI(String prefix)
	{
		if (boundURIs.containsKey(prefix))
		{
			return boundURIs.get(prefix);
		}
		else if (prefix.equals(XMLConstants.XML_NS_PREFIX))
		{
			return XMLConstants.XML_NS_URI;
		}
		else if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE))
		{
			return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
		}
		else
		{
			return XMLConstants.NULL_NS_URI;
		}
	}

	public String getPrefix(String namespaceURI)
	{
		if (boundPrefixes.containsKey(namespaceURI))
		{
			return boundPrefixes.get(namespaceURI);
		}
		else if (namespaceURI.equals(XMLConstants.XML_NS_URI))
		{
			return XMLConstants.XML_NS_PREFIX;
		}
		else if (namespaceURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
		{
			return XMLConstants.XMLNS_ATTRIBUTE;
		}
		else
		{
			return null;
		}
	}

	public Iterator getPrefixes(String namespaceURI)
	{
		// not implemented
		return null;
	}
}
