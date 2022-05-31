/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Builds a new Lucene index based on configuration in a FullTextIndex object.
 * Instantiates AbstractIndexer classes where usage specific documents are defined.
 * 
 * Author: Jeff
 *
 */

package sailpoint.fulltext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.FullTextIndex;
import sailpoint.object.FullTextIndex.FullTextField;
import sailpoint.object.TaskResult;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;


public class Builder {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    //////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * Special internal field definition used for the "id" property
     * which is always included in the index even if it is not declared.
     */
    FullTextField _idField = new FullTextField("id");

    // 
    // The usual suspects
    //

    SailPointContext _context;
    TaskMonitor _monitor;
    Attributes<String,Object> _arguments;
    boolean _terminate;
    
    /**
     * True if we are to force the refresh of a specified index list
     * rather than asking it if it needs a refresh.  Set when
     * ARG_INDEXES is passed as a task argument, only for unit tests.
     */
    boolean _force;

    /**
     * True to disable refresh optimization.  Refresh optimization
     * will attempt to avoid rebuilding an index if it looks like
     * nothing changed since the last build.  This is on by default
     * but it is new code so provide a way to disable it if there
     * are bugs.
     */
    boolean _noOptimization;

    /**
     * The repository object holding state about the index.
     */
    FullTextIndex _index;

    /**
     * The full file system path to the root of the index.
     * Derived from _index.
     */
    String _path;

    /**
     * The usage specific indexer class we are currently using.
     * Need to use this to pass down a terminate request.
     */
    AbstractIndexer _indexer;

    /**
     * The Lucene object used to build indexes.
     */
    IndexWriter _indexWriter;

    //////////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////////

    public Builder(SailPointContext con, Attributes<String,Object> args, TaskMonitor monitor) {
        _context = con;
        _monitor = monitor;
        _arguments = args;
        // so we don't have to keep checking
        if (_arguments == null)
            _arguments = new Attributes<String,Object>();

        _noOptimization = _arguments.getBoolean(FullTextifier.ARG_NO_OPTIMIZATION);
    }

    public void terminate() {
        _terminate = true;
        if (_indexer != null) {
            _indexer.terminate();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Public Interface 
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh all full text indexes on this host.
     */
    public void refreshAll() throws GeneralException {

        List<FullTextIndex> indexes = null;

        // This is a test option that allows you to force the indexes
        // to refresh with a task argument, it is not used in practice
        String argIndexes = _arguments.getString(FullTextifier.ARG_INDEXES);
        if (argIndexes != null) {
            // force these to be refreshed, disable calling isSearchEnabled
            _force = true;
            indexes = new ArrayList<FullTextIndex>();
            List<String> names = Util.csvToList(argIndexes);
            for (String name : Util.iterate(names)) {
                FullTextIndex index = _context.getObjectByName(FullTextIndex.class, name);
                if (index != null) {
                    indexes.add(index);
                }
                else {
                    String errorMsg = "Invalid index name: " + name;
                    log.error(errorMsg);
                    addResultMessage(new Message(Message.Type.Error, errorMsg));
                }
            }
        }
        else {
            // find all of them, will check for disable later
            indexes = _context.getObjects(FullTextIndex.class);
        }

        for (FullTextIndex index : Util.iterate(indexes)) {
            refreshIndex(index);
            if (_terminate)
                break;
        }
    }

    /**
     * Refresh one index.  
     * Doesn't need to be public, but might be handy for unit tests?
     */
    public void refreshIndex(FullTextIndex index) throws GeneralException {

        if (index == null)
            throw new GeneralException("Index not specified");
        
        _index = index;
        _path = LuceneUtil.getIndexPath(index);
        _indexer = null;
        _indexWriter = null;
        
        // can return null if class not found
        _indexer = LuceneUtil.getIndexerInstance(index);
        if (_indexer != null) {

            _indexer.prepare(this);

            if (!_force && (_index.isDisabled() || !_indexer.isSearchEnabled())) {
                if (log.isInfoEnabled()) {
                    log.info("Ignoring disabled index: " + index.getName());
                }
            }
            else if (isOptimizable()) {
                if (log.isInfoEnabled()) {
                    log.info("Skipping refresh of unchanged index: " + index.getName());
                }
            }
            else {
                if (log.isInfoEnabled()) {
                    log.info("Refreshing index: " + index.getName());
                }

                try {
                    startLucene();

                    _indexer.addDocuments();

                }
                catch (Throwable t) {
                    addResultException(t);
                }
                finally {
                    try {
                        stopLucene();
                        // only if we end cleanly do we update the watermark
                        // for optimized refresh
                        if (!_terminate) {
                            _indexer.saveRefreshState();
                        }
                    }
                    catch (Throwable t2) {
                        addResultException(t2);
                    }
                }

                // let the indexer contribute to the task result
                if (_monitor != null) {
                    TaskResult result = _monitor.lockPartitionResult();
                    try {
                        _indexer.saveResults(result);
                        result.setTerminated(_terminate);
                    }
                    finally {   
                        _monitor.commitPartitionResult();
                    }
                }
            }
        }
    }
    
    /**
     * Return true if refresh of this index can be optimzied because 
     * nothing has changed since the last refresh.
     */
    private boolean isOptimizable() throws GeneralException {

        boolean optimize = false;

        if (!_arguments.getBoolean(FullTextifier.ARG_NO_OPTIMIZATION)) {
            optimize = !_indexer.isIndexStale();
        }

        // disable this until after Ryan and I merge
        return optimize;
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // Lucene Management
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * Prepare the Lucene IndexWriter.
     */
    private void startLucene() throws GeneralException {

        try {
            // upgrade as necessary
            if (_index.doFieldUpgrade() || _index.doClassUpgrade()) {
                _context.saveObject(_index);
                _context.commitTransaction();
            }

            // examples show closing this, do we need to save it and
            // close it in finishIndexing?
            Directory dir = FSDirectory.open(new File(_path).toPath());

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            if (log.isInfoEnabled()) {
                log.info("Creating Lucene index: " + _path);
            }

            try {
                _indexWriter = new IndexWriter(dir, createWriterConfig());
            }
            catch (org.apache.lucene.store.LockObtainFailedException e) {
                // bug 13879 saw this after taking the server down while
                // an index refresh was running.  Not positive if this still happens, I think locking
                // on thread gets cleaned up on server shutdown now.
                // Question is should we even be doing this? If we are locked it most likely means two
                // tasks are running at the same time, in which case letting one fail seems correct. But historically
                // we tried to make things better in case of any acts of god, so lets keep it for now.
                log.error("Attempt to rebuild a locked index -- forcibly unlocking");

                // There's no longer a method for unlocking, so I guess just try to delete the lock file altogether.
                try {
                    dir.deleteFile("write.lock");
                } catch (Throwable t) {
                    // No need to throw this, maybe the other thread cleaned it up real fast?
                    // If not we will fail again constructing our IndexWriter.
                    log.error("Unable to delete lock file", t);
                }

                //Need to create new IndexWriterConfig to avoid IllegalStateException, they really enforce
                //a single instance per writer.
                _indexWriter = new IndexWriter(dir, createWriterConfig());
            }
        }
        catch (IOException ioe) {
            log.error("Exception starting index", ioe);
            _index.addError(ioe);
            throw new GeneralException(ioe);
        }
    }

    private IndexWriterConfig createWriterConfig() {
        Analyzer analyzer = _index.getAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

        // OpenMode.CREATE means to create a new index removing any previous
        // indexes.  OpenMode.CREATE_OR_APPEND adds new things to an
        // existing index.
        iwc.setOpenMode(OpenMode.CREATE);
        // IIQCB-3339 - Explicit commit will avoid to replace an existing index with
        // a new truncated index in case some error happens.
        iwc.setCommitOnClose(false);

        return iwc;
    }

    /**
     * Close the Lucene IndexWriter.
     */
    private void stopLucene() throws GeneralException {

        // Can get here during finalization of the indexing task
        // after a startup error.  Don't bother throwing another
        // exception if _indexWriter was left null, just quietly return.
        if (_index != null && _indexWriter != null) {

            // record errors in the index object 
            try {
                _indexWriter.close();
            }
            catch (CorruptIndexException e) {
                log.error("Exception during index close", e);
                _index.addError(e);
            }
            catch (IOException e) {
                log.error("Exception during index close", e);
                _index.addError(e);
            }

            // should we refetch this?  clear the cache?
            // it doesn't really make sense to save this since the same
            // object will be shared by the cluster, but it is useful
            // to tell whether the index is infact being refreshed
            _index.setLastRefresh(new Date());
            _context.saveObject(_index);
            _context.commitTransaction();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Task Results
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Add a message (usually an error) to the task result.
     * When refreshing under a TaskExecutor or RequestExecutor this will
     * be an actual TaskResult.  When refreshing under a Service, 
     * the monitor may be null.
     */
    private void addResultMessage(Message msg) {
        try {
            if (_monitor != null) {
                TaskResult result = _monitor.lockPartitionResult();
                try {
                    result.addMessage(msg);
                }
                finally {
                    _monitor.commitPartitionResult();
                }
            }
        }
        catch (Throwable t) {
            log.error("Unable to add result message", t);
        }
    }

    /**
     * Add an exception to the task result.
     */
    private void addResultException(Throwable t) {
        log.error("Exception building index", t);
        try {
            if (_monitor != null) {
                TaskResult result = _monitor.lockPartitionResult();
                try {
                    result.addException(t);
                }
                finally {
                    _monitor.commitPartitionResult();
                }
            }
        }
        catch (Throwable t2) {
            log.error("Unable to add result exception", t2);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // AbstractIndexer Callbacks
    //
    //////////////////////////////////////////////////////////////////////

    public SailPointContext getSailPointContext() {
        return _context;
    }

    public FullTextIndex getIndexDefinition() {
        return _index;
    }
    
    public void updateProgress(String progress) {

        if (_monitor != null)  {
            _monitor.updateProgress(progress);
        }
    }
    
    public void addDocument(Document doc)
        throws CorruptIndexException, java.io.IOException {
        
        _indexWriter.addDocument(doc);
    }

    /**
     * Exposes the method {@link IndexWriter#commit()} to be used through the builder.
     * This method is required to explicitly commit the changes during a successful refresh as
     * the default behavior for the Index Writer is to not to "commit on close" anymore.
     * See {@link #createWriterConfig()}.
     * @throws IOException
     */
    public void commitIndex() throws IOException {
        _indexWriter.commit();
    }

}
