/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of XMLBuilder that streams to a file.
 *
 * Author: Jeff
 */

package sailpoint.tools.xml;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;

public class FileXMLBuilder extends BaseXMLBuilder
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    Writer _writer;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public FileXMLBuilder(String name) throws GeneralException {
        super(BrandingServiceFactory.getService().getXmlHeaderElement());
        try {
            String path = Util.findOutputFile(name);
            _writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(path),
                    "UTF-8"));
        }
        catch (IOException e) {
            throw new GeneralException(e);
        }
        startElement( BrandingServiceFactory.getService().getXmlHeaderElement() );
    }

    /**
     * Must call this when you're done.
     */
    public void close() {
        try {
            endElement( BrandingServiceFactory.getService().getXmlHeaderElement() );
            if (_writer != null) {
                _writer.close();
                _writer = null;
            }
        }
        catch (IOException e) {
            // ignore these
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Method Overloads
    //
    //////////////////////////////////////////////////////////////////////

    public void append(String text) {

        try {
            _writer.write(text);
        }
        catch (java.io.IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public void escapeAttribute(String text) {

        try {
            XmlUtil.escapeAttribute(_writer, text);
        }
        catch (java.io.IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public void escapeContent(String text) {

        try {
            XmlUtil.escapeContent(_writer, text);
        }
        catch (java.io.IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

}
