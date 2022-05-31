/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.build;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;

import java.io.*;
import java.util.*;

/**
 * Takes a set of message properties files and creates an
 * interface with all keys from those files included as constants. An individual
 * key may be duplicated in multiple files and will be ignored after the first time
 * it is processed.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CreateMessageKeysTask extends Task {

    //Source dir where the generated class should be output to.
    private String destDir;

    //Fully qualified name of the generated class.
    private String outputClass;

    // included filesets
    private final Vector<FileSet> filesets = new Vector<FileSet>();

    // Stores the keys which have already been processed so
    // a duplicate property is not added to the class.
    private Set<String> allProps = new HashSet<String>();

    /**
     * Generates a class, specified by the outputClass parameter which includes
     * constant definitions for all the keys in the given messages file.
     *
     * @throws BuildException
     */
    public void execute() throws BuildException {

        super.execute();

        try {

            // make sure that the full path for the output class exists.
            File filePath = new File(getOutputPath().substring(0, getOutputPath().lastIndexOf('/') + 1));
            if (!filePath.exists())
                filePath.mkdirs();

            File messagesKeysClass = new File(getOutputPath());
            FileWriter fw = new FileWriter(messagesKeysClass);

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(fw));
                out.println("/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */");
                out.println("");
                out.println("package " + getMessageKeysClassPackage() + ";");
                out.println("");
                out.println("import sailpoint.tools.Untraced;");
                out.println("");
                out.println("");

                out.println("@Untraced");
                out.println("public interface "+ getMessageKeysClassName() +" {");
                out.println("");
                out.println();

                for(FileSet fs : filesets){
                    DirectoryScanner ds = fs.getDirectoryScanner(getProject());
                    File dir = fs.getDir(getProject());
                    for (String src : ds.getIncludedFiles()){
                        String file = dir + "//" + src;
                        log("Handling file:" + file, Project.MSG_VERBOSE);
                        out.println("    // From properties file " + src);
                        out.println(getPropertiesContents(file));
                    }
                }

                out.println("");
                out.println("");
                out.println("}");

                out.flush();
                out.close();
            }
            finally {
                fw.close();
            }

        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    private String getPropertiesContents(String file) throws IOException {

        List<String> keys = MessagePropertiesFileParser.getPropertyKeys(file);
        Set<String> mobileMessageKeys = MessagePropertiesFileParser.getMobilePropertyKeys(file);
        StringBuffer buf = new StringBuffer();

        // Add key as a constant to the MessageKeys class. If we have already seen the key,
        // ignore it. We may have dupes dues to the entries in iiqCustom.properties.
        for (String key : keys) {
            String propertyName = key.toUpperCase();
            if (!allProps.contains(propertyName)) {
                // If this is a mobile message key, add the correct annotation. 
                // See Internationalizer.getMobileMessages for usage.
                if (mobileMessageKeys.contains(key)) {
                    buf.append("    @MobileMessage\n");
                }
                buf.append("    public static final String " + propertyName + " = " + "\"" +
                        key + "\";\n");

                allProps.add(propertyName);
            }
        }

        return buf.toString();
    }

    private String getOutputPath(){
        if (destDir.endsWith("/"))
            return destDir + getMessageKeysClassFile();
        else
            return destDir + "/" + getMessageKeysClassFile();
    }

    private String getMessageKeysClassName(){
        return outputClass.substring(outputClass.lastIndexOf('.') + 1);
    }

    private String getMessageKeysClassFile(){
        return outputClass.replace('.','/') + ".java";
    }

    private String getMessageKeysClassPackage(){
        return outputClass.substring(0, outputClass.lastIndexOf('.')); 
    }

    // -------------------------------------------------------------------------------
    //  TASK PARAMETERS
    // -------------------------------------------------------------------------------

    public String getDestDir() {
        return destDir;
    }

    public void setDestDir(String destDir) {
        this.destDir = destDir;
    }


    public void addFileset(FileSet set) {
       filesets.addElement(set);
    }

    public void setOutputClass(String outputClass) {
        this.outputClass = outputClass;
    }
}
