/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object that can generate XML using
 * a more convenient programatic interface.  There are
 * two implementations, StringXMLBuilder accumulates the XML
 * in a string buffer and FileXMLBuilder which streams to a file.
 *
 * In theory we could provide an implementation that generated
 * the "binary infoset" or some other compressed respresentation.
 *
 * Author: Rob, refactoring by Jeff
 */

package sailpoint.tools.xml;

public interface XMLBuilder
{
    public void startElement(String name);
    public void startPotentialElement(String name);
    public void addAttribute(String name, String value);
    public void addContent(String value);
    public void addContent(String value, boolean escape);
    public void endElement(String name);

}
