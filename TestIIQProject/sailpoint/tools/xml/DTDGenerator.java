/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Little utility class to generate a DTD from the
 * registered serializers and store the result in a file.
 *
 * Originally the DTD was generated and used only at runtime,
 * but this makes it awkward to have a DTD including other
 * things besides SailPointObjects, notably import files
 * that need a common <sailpoint> wrapper as well as meta-commands
 * for the importer.
 *
 * It is unclear how to insert markup declarations for
 * non-SailPointObjects in the DTDBuilder machinery.  
 * For now, this will get us the guts of the file, it will have
 * to be manaully merged with other files.
 *
 * Author: Jeff
 */
package sailpoint.tools.xml;

import sailpoint.tools.Util;

public class DTDGenerator
{
    public static void main(String[] args)
    {
        String dtd = XMLObjectFactory.getInstance().getDTD();

        if (args.length == 0)
            System.out.println(dtd);
        else {
            try {
                Util.writeFile(args[0], dtd);
            }
            catch (Throwable t) {
                System.out.println(t);
            }
        }
    }

}
