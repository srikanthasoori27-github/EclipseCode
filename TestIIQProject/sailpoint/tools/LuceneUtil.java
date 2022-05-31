/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A simple wrapper around Lucene for testing the IIQ integration.
 * Initially based on the Lucene console demo.
 *
 * Not really used, does it even work? I dont care.
 */

package sailpoint.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

// query stuff

public class LuceneUtil {

    //////////////////////////////////////////////////////////////////////
    //
    // Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**     
     * Index all text files under a directory.
     * The rootPath argument has the name of a directory to scan, the
     * indexPath argument has the output directory for the index.
     */
    static public void index(String rootPath, String indexPath)
        throws Exception {

        if (rootPath == null)
            throw new Exception("Missing root path");

        if (indexPath == null)
            throw new Exception("Missing index path");

        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.canRead())
            throw new Exception("Root directory cannot be read: " + rootPath);

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(new File(indexPath).toPath());
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            // orginal demo makes this an option
            // OpenMode.CREATE means to create a new index removing any previous
            // indexes.  OpenMode.CREATE_OR_APPEND adds new things to an 
            // existing index.
            //iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            iwc.setOpenMode(OpenMode.CREATE);

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, rootDir);

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } 
        catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                               "\n with message: " + e.getMessage());
        }
    }

    /**
     * Indexes the given file using the given writer, or if a directory is given,
     * recurses over files and directories found under the given directory.
     * 
     * NOTE: This method indexes one document per input file.  This is slow.  For good
     * throughput, put multiple documents into your input file(s).  An example of this is
     * in the benchmark module, which can create "line doc" files, one document per line,
     * using the
     * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
     * >WriteLineDocTask</a>.
     *  
     * @param writer Writer to the index where the given file/dir info will be stored
     * @param file The file to index, or the directory to recurse into to find files to index
     * @throws IOException
     */
    static private void indexDocs(IndexWriter writer, File file)
        throws IOException {
        // do not try to index files that cannot be read
        if (file.canRead()) {
            if (file.isDirectory()) {
                String[] files = file.list();
                // an IO error could occur
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        indexDocs(writer, new File(file, files[i]));
                    }
                }
            } else {

                FileInputStream fis;
                try {
                    fis = new FileInputStream(file);
                } catch (FileNotFoundException fnfe) {
                    // at least on windows, some temporary files raise this exception with an "access denied" message
                    // checking if the file can be read doesn't help
                    return;
                }

                try {

                    // make a new, empty document
                    Document doc = new Document();

                    // Add the path of the file as a field named "path".  Use a
                    // field that is indexed (i.e. searchable), but don't tokenize 
                    // the field into separate words and don't index term frequency
                    // or positional information:
                    Field pathField = new StringField("path", file.getPath(), Field.Store.YES);
                    doc.add(pathField);

                    // Add the last modified date of the file a field named "modified".
                    // Use a NumericField that is indexed (i.e. efficiently filterable with
                    // NumericRangeFilter).  This indexes to milli-second resolution, which
                    // is often too fine.  You could instead create a number based on
                    // year/month/day/hour/minutes/seconds, down the resolution you require.
                    // For example the long value 2011021714 would mean
                    // February 17, 2011, 2-3 PM.
                    // TODO: NumericFields changed alot and we dont really support them in any real way so I am not bothering to figure out how to make this work.
                    /*NumericField modifiedField = new NumericField("modified");
                    modifiedField.setLongValue(file.lastModified());
                    doc.add(modifiedField);
                    */

                    // Add the contents of the file to a field named "contents".  Specify a Reader,
                    // so that the text of the file is tokenized and indexed, but not stored.
                    // Note that FileReader expects the file to be in UTF-8 encoding.
                    // If that's not the case searching for special characters will fail.
                    doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(fis, "UTF-8"))));

                    if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                        // New index, so we just add the document (no old document can be there):
                        System.out.println("adding " + file);
                        writer.addDocument(doc);
                    } else {
                        // Existing index (an old copy of this document may have been indexed) so 
                        // we use updateDocument instead to replace the old one matching the exact 
                        // path, if present:
                        System.out.println("updating " + file);
                        writer.updateDocument(new Term("path", file.getPath()), doc);
                    }
          
                } finally {
                    fis.close();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Searching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Search an index.
     *
     */
    static public void search(String indexPath) 
        throws Exception {

        // not sure what this is
        String field = "contents";
        int repeat = 0;
        boolean raw = false;
        int hitsPerPage = 10;

        Directory dir = FSDirectory.open(new File(indexPath).toPath());
        DirectoryReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        try {
            Analyzer analyzer = new StandardAnalyzer();

            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

            QueryParser parser = new QueryParser(field, analyzer);
            while (true) {
                System.out.println("Enter query: ");

                String line = in.readLine();

                if (line == null || line.length() == -1) {
                    break;
                }

                line = line.trim();
                if (line.length() == 0) {
                    break;
                }

                Query query = parser.parse(line);
                System.out.println("Searching for: " + query.toString(field));

                if (repeat > 0) {                           // repeat & time as benchmark
                    Date start = new Date();
                    for (int i = 0; i < repeat; i++) {
                        searcher.search(query, 100);
                    }
                    Date end = new Date();
                    System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
                }

                doPagingSearch(in, searcher, query, hitsPerPage, raw, true);
            }
        } finally {
            reader.close();
            dir.close();
        }
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine presents 
     * pages of size n to the user. The user can then go to the next page if interested in
     * the next hits.
     * 
     * When the query is executed for the first time, then only enough results are collected
     * to fill 5 result pages. If the user wants to page beyond this limit, then the query
     * is executed another time and all hits are collected.
     * 
     */
    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, 
                                      int hitsPerPage, boolean raw, boolean interactive) 
        throws IOException {
 
        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;
    
        int numTotalHits = Util.otoi(results.totalHits.value);
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);
        
        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length +" of " + numTotalHits + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }

                hits = searcher.search(query, numTotalHits).scoreDocs;
            }
      
            end = Math.min(hits.length, start + hitsPerPage);
      
            for (int i = start; i < end; i++) {
                if (raw) {                              // output raw format
                    System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
                    continue;
                }

                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    System.out.println((i+1) + ". " + path);
                    String title = doc.get("title");
                    if (title != null) {
                        System.out.println("   Title: " + doc.get("title"));
                    }
                } else {
                    System.out.println((i+1) + ". " + "No path for this document");
                }
                  
            }

            if (!interactive || end == 0) {
                break;
            }

            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print("Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");  
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page.");
          
                    String line = in.readLine();
                    if (line.length() == 0 || line.charAt(0)=='q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start+=hitsPerPage;
                        }
                        break;
                    } else {
                        int page = Integer.parseInt(line);
                        if ((page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println("No such page");
                        }
                    }
                }
                if (quit) break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }

}

