/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;


public class SourceAnalyzer
{
    //////////////////////////////////////////////////////////////////////
    //
    // Statistics for a file or directory
    //
    //////////////////////////////////////////////////////////////////////

    public static class FileStats {

        public String path;
        public int files;
        public int characters;
        public int lines;
        public List<FileStats> children;

        public FileStats(File f) {
            path = f.getPath();
        }

        public void add(FileStats f) {
            if (f != null) {
                if (children == null)
                    children = new ArrayList<FileStats>();
                children.add(f);

                // aggregate statistics
                files += f.files;
                characters += f.characters;
                lines += f.lines;
            }
        }

        public void print(boolean showFiles) {

            if (children == null) {
                if (showFiles)
                    println("    " + path + " : " + itoa(lines));
            }
            else {
                println("Directory: " + path + " : " + 
                        itoa(files) + " files, " + 
                        itoa(lines) + " lines");

                for (FileStats child : children)
                    child.print(showFiles);
            }
        }

    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // File reading
    //
    //////////////////////////////////////////////////////////////////////

    static public String itoa(int i) {
        return new Integer(i).toString();
    }

    static public String readFile(String name) 
        throws IOException {

        String string = null;
        byte[] bytes = readBinaryFile(name);
        if (bytes != null)
            string = new String(bytes);
        return string;
    }

    /**
     * Read the contents of a file and return it as a byte array.
     */
    static public byte[] readBinaryFile(String path) 
        throws IOException {

        byte[] bytes = null;

        // should be cleaner here with exception handling?
        FileInputStream fis = new FileInputStream(path);
        try {
            int size = fis.available();
            bytes = new byte[size];
            fis.read(bytes);
        }
        finally {
            try {
                fis.close();
            }
            catch (IOException e) {
                // ignore these
            }
        }

        return bytes;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // walk
    //
    //////////////////////////////////////////////////////////////////////

    public static FileStats walk(File f) 
        throws IOException {

        FileStats stats = null;

        if (f.isFile())
            stats = analyze(f);

        else if (f.isDirectory()) {

            stats = new FileStats(f);

            //println("Directory: " + f.getPath());

            File files[] = f.listFiles(new FileFilter() {
                    public boolean accept(File file) {
                        return (file.isDirectory() ||
                                (file.isFile() &&
                                 file.getPath().endsWith(".gsp")));
                    }
                });

            if (files != null) {
                for (int i = 0 ; i < files.length ; i++) {
                    stats.add(walk(files[i]));
                }
            }

            // collapse empty directories
            if (stats.children == null)
                stats = null;
        }
        else {
            println("Unknown file type: " + f.getPath());
        }

        return stats;
    }

    private static FileStats analyze(File f) 
        throws IOException {

        FileStats stats = new FileStats(f);
        //println(f.getPath());

        String text = readFile(f.getPath());
        stats.characters = text.length();

        int lines = 0;

        for (int i = 0 ; i < text.length() ; i++) {
            int ch = text.charAt(i);
            if (ch == '\n')
                lines++;
        }

        stats.lines = lines;
        // this will get aggregated into the parent stats
        stats.files = 1;

        return stats;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // main
    //
    //////////////////////////////////////////////////////////////////////

    public static void analyze(String path) throws Exception {

        File f = new File(path);

        FileStats stats = walk(f);

        stats.print(true);
    }

    public static void main(String[] args) {

        try {
            if (args.length == 0)
                println("Usage: analyze <path>");
            else
                analyze(args[0]);
        }
        catch (Throwable t) {
            println(t.toString());
        }
    }

    public static void println(Object o) {
        System.out.println(o);
    }


}
