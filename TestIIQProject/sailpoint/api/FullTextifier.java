/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A service class providing construction and searching of full text indexes
 * implemented with Lucene. As of 7.1 this is a fascade that delegates
 * to Builder and Searcher so be careful with backporting.
 *
 * Author: Jeff
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.fulltext.Builder;
import sailpoint.fulltext.LuceneUtil;
import sailpoint.fulltext.Searcher;
import sailpoint.object.Attributes;
import sailpoint.object.FullTextIndex;
import sailpoint.object.QueryOptions;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;


public class FullTextifier {

    private static Log log = LogFactory.getLog(FullTextifier.class);

    //////////////////////////////////////////////////////////////////////
    //
    // SearchResult
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Used to convey a full text search result.
     * Primarily this is here for the combination of the List of Maps
     * that has the interesting results as well as a count of the total
     * number of matches.  These are both needed when drawing grids and
     * we don't want to make a second "count(*)" search.
     */
    public static class SearchResult {

        public int totalRows;
        public List<Map<String,Object>> rows;

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A task argument to control which indexes are to be rebuilt.
     * Used only when index refresh is run as a task, and only for testing.
     * Normally we automatically determine which indexes to refresh
     * by looking at the FullTextIndex objects and calling the indexer classes.
     */
    public static final String ARG_INDEXES = "indexes";

    /**
     * A task argument to disable refresh optimization.  Refresh optimization
     * will attempt to avoid rebuilding an index if it looks like
     * nothing changed since the last build.  This is on by default
     * but it is new code so provide a way to disable it if there
     * are bugs.
     *
     * If index refresh is happening in a service rather than a task, this
     * can be added to the ServiceDefinition.
     */
    public static final String ARG_NO_OPTIMIZATION = "noOptimization";

    /**
     * Common field that may be used in indexes to distinguish between
     * classes when an index contains more than one thing.  This is out here
     * because it is part of the public API.  The rest should be standard
     * property names.
     */
    public static final String FIELD_OBJECT_CLASS = "objectClass";
    
    // 
    // The usual suspects
    //

    SailPointContext _context;

    /**
     * Arguments passed to constructor and then on to Builder.
     * Only used when called from the index resfresh task, not when searching.
     */
    Attributes<String,Object> _arguments;

    /**
     * Index may be specified in the constructor or in setScope.
     */
    FullTextIndex _index;

    /**
     * Optional maximum search result cap.
     */
    int _maxResults;
    
    /**
     * Currently active index builder.  Have to save this so terminate()
     * can forward the request on to it.
     */
    Builder _builder;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    // Don't need all of these, clean it up!!

    /**
     * Interface used by FullTextIndexer task.  It will eventually call refreshAll()
     * which is sensitive to task arguments.
     */
    public FullTextifier(SailPointContext con, Attributes<String,Object> args) {
        _context = con;
        _arguments = args;
    }

    /**
     * Interface used by pages that want to search.  The index must be specified,
     * search options are later passed as QueryOptions in the call to search().
     */
    public FullTextifier(SailPointContext con, FullTextIndex index)
        throws GeneralException {
        this(con, index, 0);
    }

    /**
     * Interface used by pages that want to search, and support a configurable
     * maximum result cap.
     */
    public FullTextifier(SailPointContext con, FullTextIndex index, int max)
        throws GeneralException {

        _context = con;
        _maxResults = max;
        setIndex(index);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scope
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set the FullTextIndex to use for index building and searching.
     * Defer most of the expensive setup to Builder.prepare() so we can  
     * call methods like isEnabled without doing unnecessary work.
     */
    public void setIndex(FullTextIndex index) throws GeneralException {
        if (index == null) {
            throw new GeneralException("Index is required");
        }
        _index = index;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Index Building
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * May be called in another thread after refreshAll is started
     * to terminate the thread doing the refresh.   
     */
    public void terminate() {
        if (_builder != null) {
            _builder.terminate();
        }
    }

    /**
     * Refresh all full text indexes on this host, posting periodic results
     * to a TaskMonitor.  This was factored out of the FullTextIndexer task
     * so it could be called by a TaskExecutor, RequestExecutor, or 
     * Service.
     */
    public void refreshAll(TaskMonitor monitor)
        throws GeneralException {

        _builder = new Builder(_context, _arguments, monitor);
        _builder.refreshAll();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Index Searching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this index should be used for searching.
     * Typically called by the UI to decide whether to use the index
     * or fallback to SQL queries.
     *
     * There are three levels of defense.  First the FullTextIndex object may
     * have the disabled flag set.  This is not set by default and I can't think
     * of any good reasons for this, but it's been out there.
     *
     * Next the FullTextIndex object must have a non-null refresh date.
     * If it doesn't then the index doesn't exist and can't be used.
     *
     * Finally the AbstractIndexer subclass must overload isSearchEnabled and
     * to check a system configuration option.
     */
    public boolean isSearchEnabled() throws GeneralException {

        if (_index == null)
            throw new GeneralException("FullTextIndex is not set");
        return LuceneUtil.isSearchEnabled(_index);
    }

    /**
     * Search the index given.
     * 
     * The fulltext query is defined in two parts.  First a list of strings 
     * representing the terms to include in full text searching, and QueryOptions
     * that may contain Filter objects used to restrict the results.
     * 
     * QueryOptions may also be used pass the maximum number of results,
     * we do yet support setting a starting row.
     * 
     * Note that it is important that we have a clear distinction between
     * what will be handled by a fulltext Query and what will be a filter applied
     * to the results of that query.  In theory it would be possible to mix them
     * like ((description="*something*" && scope="foo") || description="*other*")
     * but this would requires some relatively complicated analysis to split this
     * into a Query and Filter and in some cases may not be possible.
     * 
     * The text will automatically apply to all indexed fields ORd.
     * If the text is surrounded by double quotes it becomes a PhraseQuery.
     * Otherwise the text is broken by spaces and the words become
     * ANDd TermQuerys
     */
    public SearchResult search(List<String> terms, QueryOptions ops)
        throws GeneralException {

        if (_index == null)
            throw new GeneralException("FullTextIndex is not set");

        // TODO: Just be consistent and pass this in QueryOptions
        Searcher searcher = new Searcher(_context, _index);

        // pass along the option result cap, note that this is different
        // than ops.getResultLimit which will usually be set up for a paging
        // grid with a max of 25
        searcher.setMaxResults(_maxResults);
        
        return searcher.search(terms, ops);
    }

    /**
     * Wrapper around the previous search method that handles the
     * more common case of a single search term.
     */
    public SearchResult search(String text, QueryOptions ops)
        throws GeneralException {

        List<String> terms = null;
        if (text != null) {
            terms = new ArrayList<String>();
            terms.add(text);
        }

        return search(terms, ops);
    }
    
}
        
