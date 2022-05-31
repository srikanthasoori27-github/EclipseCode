/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * Utility class used to construct a bill of materials that contains the
 * checksums for all entries in a war file.  The BOM file format has each a line
 * per war file entry in the form of <checksum><tab><entry name>.
 * 
 * Eventually, we may want to formalize a BOM object so we can create a BOM from
 * a file system and compare it to a BOM generated for a war.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class BOMGenerator {

    /**
     * Main method - expects a war file name and an output file name.
     */
    public static void main(String[] args) throws Exception {
        
        if (2 != args.length) {
            System.err.println("Usage: bomGenerator <war filename> <output filename>");
            System.exit(1);
        }

        generateBOM(args[0], args[1]);
    }


    /**
     * Read the contents of the given war (or jar) file and create a bill of
     * materials that includes a checksum and the entry name for all entries in
     * the war file.
     * 
     * @param  warFileName  The name of the war file.
     * @param  outputFile   The name of the output file.
     * 
     * @throws IOException  If the war cannot be read of outfile written.
     */
    public static void generateBOM(String warFileName, String outputFile)
        throws IOException {

        JarFile war = new JarFile(warFileName);
        Writer out = new FileWriter(outputFile);

        Enumeration<JarEntry> entries = war.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            out.write(entry.getCrc() + "\t" + entry.getName() + "\n");
        }
        out.flush();
        out.close();
        war.close();
    }
}
