/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class configured to be launched from "iiq parse"
 * that parses an XML file.  We have a console command
 * to do the same thing but this is faster and can be
 * used in my current situation where the console can't be
 * run because the database needs to be ugpraded, but
 * the upgrade fails due to an XML error in one of the files
 */

package sailpoint.server;

import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.XMLObjectFactory;

public class Parser {

    public Parser() {
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static final void main(String[] args) {

        try {
            if (args.length < 1 || args.length > 2 )  
                println("usage: parse <file>");
            else {
                String xml = Util.readFile(args[0]);
                XMLObjectFactory f = XMLObjectFactory.getInstance();
                String dtd = f.getDTD();
                XmlUtil.parse(xml, dtd, true);
            }
        }
        catch (Throwable t) {
            System.out.println(t);
        }
    }

}
