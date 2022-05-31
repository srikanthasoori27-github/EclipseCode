/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class for searching a Lucene index.  Accessed indirectly
 * through FullTextifier.
 *
 * Author: Jeff/Bernie
 *
 */

package sailpoint.fulltext;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import sailpoint.api.FullTextifier.SearchResult;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.FullTextIndex;
import sailpoint.object.FullTextIndex.FullTextField;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;


public class Searcher {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A property we'll recognize in filters but which we'll convert
     * to a term using assignedScope.path instead.  In theory we
     * coudld do this for several object relationships but we
     * only need it for scope at the moment.  This is only
     * used for filters of the form Filter.isnull("assigndScope") which
     * gets converted to to Filter.eq("assigendScope.path", "*null*")
     */
    public static final String PROP_ASSIGNED_SCOPE = "assignedScope";

    /**
     * A property we'll recognize in filters but which we'll convert
     * to a term using assignedScope.path instead.  There are two Hibernate
     * property paths that reference the assigned scope path.
     * assignedScopePath is directly on the object, and 
     * assignedScope.path joins through the Scope object.  
     * They are usualy the same but the one ont he Scope object
     * is considered authoritative.
     */
    public static final String PROP_ASSIGNED_SCOPE_PATH = "assignedScopePath";

    SailPointContext _context;
    FullTextIndex _index;
    
    /**
     * Optional maximum result cap.  Note that this goes beyond what is specified
     * in QueryOptions, the max result limit there will be 25 for paging grids.
     */
    int _maxResults;

    /**
     * Special internal field definition used for the "id" property
     * which is always included in the index even if it is not declared.
     */
    FullTextField _idField = new FullTextField("id");

    /**
     * This will be a org.apache.CharArraySet containing words that Lucene's
     * StandardAnalyzer deliberately ignores.  Unfortunately it's not 
     * parameterized so we can't parameterize either
     */
    static CharArraySet STOPWORDS = null;

    //////////////////////////////////////////////////////////////////////
    //
    // Search Interface
    //
    //////////////////////////////////////////////////////////////////////

    public Searcher(SailPointContext con, FullTextIndex index) {
        _context = con;
        _index = index;

        // only needs to be done once and can be cached
        Analyzer abstractAnalyzer = index.getAnalyzer();
        if (STOPWORDS == null && abstractAnalyzer instanceof StandardAnalyzer) {
            StandardAnalyzer analyzer = (StandardAnalyzer)abstractAnalyzer;
            STOPWORDS = analyzer.getStopwordSet();
            analyzer.close();
        }
    }

    /**
     * Set the optional maximum result cap.
     */
    public void setMaxResults(int max) {
        _maxResults = max;
    }

    /**
     * Search the index given.
     * 
     * The fulltext query is defined in two parts.  First a single string
     * represents the fulltext search, it will be converted
     * into a Lucene Query.  Second the QueryOptions may contain
     * a list of Filter objects, these are converted into Lucene Filters.
     * QueryOptions may also specify values for the starting result number
     * and the maximum number of results.
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

        SearchResult sresult = null;
        Query query = null;
        int first = 0;
        int max = 0;

        // Inject the "targets" field so you don't have to explicitly define it.
        if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {
            FullTextField f = new FullTextField(AbstractIndexer.FIELD_TARGETS);
            f.setAnalyzed(true);
            _index.addTransientField(f);
        }

        // First convert the search terms into an IIQ Filter for conversion.
        // This was originally designed to take one string that
        // would be tokenized by spaces with the result words ORd.
        // LCM started using multiple terms with the expectation that they
        // be ANDed.  Quotes were supported to indiciate a phrase, remove those
        // if we see them.

        if (log.isInfoEnabled()) {
            log.info("Searching with terms: " + terms);
            if (ops != null) {
                log.info("Options:");
                log.info(ops.toString());
            }
        }

        // convert the terms into a Query
        if (terms != null && terms.size() > 0) {

            List<Filter> termFilters = new ArrayList<Filter>();

            for (String term : terms) {
                List<String> tokens = null;

                int len = term.length();
                if (len > 2 && 
                    term.charAt(0) == '"' && 
                    term.charAt(len-1) == '"') {
                    // Remove blank search terms
                    String quotedValue = term.substring(1, len - 1).trim();
                    if(quotedValue.length() != 0) {
                        // looks like a phrase, only one token
                        // !! whoa wait, don't we have to strip the quotes
                        // or does Lucene handle them?
                        // TK: No, not yet.  See 'makeQuery'
                        tokens = new ArrayList<String>();
                        tokens.add(term);
                    }
                }
                else {
                    // filter stop words and punctuation
                    //tokens = Util.delimToList(" ", term, true);
                    tokens = tokenize(term);
                }

                if (tokens != null && tokens.size() > 0) {

                    // for each possible search field
                    List<Filter> fieldTerms = new ArrayList<Filter>();
                    for (FullTextField field : _index.getFieldMap().values()) {
                        if (field.isAnalyzed()) {
                            // for each token
                            List<Filter> tokenTerms = new ArrayList<Filter>();
                            for (String token: tokens)
                                tokenTerms.add(Filter.eq(field.getName(), token));

                            // each token is and'd for each field
                            if (tokenTerms.size() == 1) 
                                fieldTerms.add(tokenTerms.get(0));
                            else if (tokenTerms.size() > 1)
                                fieldTerms.add(Filter.and(tokenTerms));
                        }
                    }
                    
                    // Fields are alway or'd, you would never
                    // want "description matches this AND name matches this"
                    if (fieldTerms.size() == 1)
                        termFilters.add(fieldTerms.get(0));
                    else if (fieldTerms.size() > 1)
                        termFilters.add(Filter.or(fieldTerms));
                }
            }

            // AND or OR these?
            Filter root = null;
            if (termFilters.size() == 1)
                root = termFilters.get(0);
            else if (termFilters.size() > 1)
                root = Filter.and(termFilters);

            if (root != null) {
                query = convertToQuery(root, true);
            }
        }

        // Next convert IIQ Filters into Lucene Filters
        if (ops != null) {
            first = ops.getFirstRow();
            max = ops.getResultLimit();

            List<Filter> filters = ops.getFilters();
            if (filters != null && filters.size() > 0) {
                // in QueryOptions they are implicitly anded
                Filter root;
                if (filters.size() == 1)
                    root = filters.get(0);
                else
                    root = Filter.and(filters);

                Query q = convertToQuery(root, false);
                
                if (q != null) {
                    BooleanQuery.Builder fullQueryBuilder = new BooleanQuery.Builder();
                    if (query != null) {
                        fullQueryBuilder.add(query, BooleanClause.Occur.MUST);
                    }
                    fullQueryBuilder.add(q, BooleanClause.Occur.FILTER);
                    query = fullQueryBuilder.build();
                }
            }
        }

        return search(query, first, max);
    }

    /** 
     * Take a string and pass it through the same Lucene analyzer we use
     * when building the index.  This is used to filter out stop words
     * and punctuation that won't be in the index.
     */ 
    private List<String> tokenize(String text) {

        List<String> result = new ArrayList<String>();

        Analyzer analyzer = _index.getAnalyzer();

        TokenStream stream  = analyzer.tokenStream(null, new StringReader(text));
        try {
            stream.reset();
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            while (stream.incrementToken()) {
                result.add(termAtt.toString());
            }
        }
        catch(IOException e) {
            // not thrown b/c we're using a string reader...
        } finally{
            try {
                // Not positive if we need these or not
                stream.end();
                stream.close();
                analyzer.close();
            } catch (IOException ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Error closing token stream", ex);
                }
            }
        }

        if (log.isInfoEnabled()) {
            log.info("Tokenized \"" + text + "\" to \"" + result + "\"");
        }

        return result;
    }

    /**
     * Search the index given a Lucene query string.
     * The default field is always "description".
     *
     * jsl - is this necesary?  It doesn't behave like
     * the other search() below if you pass in an empty QueryOptions
     * get rid of this...
     *
     * TODO: Not in the new FullTextifier interface, if no one needs it get rid of it.
     */
    private SearchResult search(String query) throws GeneralException {

        SearchResult result = null;

        Analyzer analyzer = null;
        try {
            analyzer = _index.getAnalyzer();
            QueryParser qp = new QueryParser(AbstractIndexer.FIELD_DESCRIPTION, analyzer);

            // filter the string before parsing, why the hell doesn't
            // Lucene do this?
            String simplified = filterQueryText(query);
            Query q = qp.parse(simplified);
            result = search(q, 0, 0);
        }
        catch (ParseException e) {
            throw new GeneralException(e);
        } finally {
            if (analyzer != null) {
                analyzer.close();
            }
        }

        return result;
    }

    /**
     * Take a raw query string and tokenize it.
     * Surprised Lucene isn't doing this.
     */
    private String filterQueryText(String text) {

        String filtered = null;
        if (text != null) {
            List<String> tokens = tokenize(text);
            if (tokens != null) {
                StringBuffer b = new StringBuffer();
                for (String token : tokens) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(token);
                }
                filtered = b.toString();
            }
        }
        return filtered;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Search Internals
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Recursively walk an IIQ Filter attempting to build
     * the corresponding Lucene Query.
     *
     * If we thought hard enough we might be able to support
     * some of the range terms, LT, GT, LE, etc. but those
     * don't happen in the IIQ UI.
     *
     * Be tolerant of thigns we can't do to make it easier to 
     * splice the index in and out of the UI.
     *
     * !! Need to be smarter about dividing the fields into things
     * that are indexed and can become Queries and those that are not
     * and must become Lucene Filters.  Using IIQ Filter you can come
     * up with combinations that are impossible so it may be best to 
     * require that this be split into two Filters, or just have a Filter
     * for the filter and a string for the text search.
     *
     * We're going to use this for both Query and Filter assuming
     * QueryFilterWrapper works as expected.
     */
    private Query convertToQuery(Filter src, boolean isQuery)
        throws GeneralException {

        Query q = null;

        if (src instanceof LeafFilter) {
            q = convertLeafFilter((LeafFilter)src, isQuery);
        }
        else {
            // and/or/not
            CompositeFilter cf = (CompositeFilter)src;
            List<Filter> children = cf.getChildren();
            if (children != null && children.size() > 0) {
                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                BooleanClause.Occur occur = BooleanClause.Occur.MUST;
                if (cf.getOperation() == BooleanOperation.OR) {
                    occur = BooleanClause.Occur.SHOULD;
                } else if (cf.getOperation() == BooleanOperation.NOT){
                    // NOTs happen at the composite level, so we need to 
                    // set the occur in that case.  NOT is unary, so it
                    // should only ever have one child.  Note that NOT
                    // is distinct from NE, which is another form of 'not'
                    // that is handled as a LeafFilter
                    occur = BooleanClause.Occur.MUST_NOT;
                }

                for (Filter child : children) {
                    Query cq = convertToQuery(child, isQuery);
                    if (cq != null) {
                        if(cq instanceof BooleanQuery) {
                            List<BooleanClause> bc = ((BooleanQuery) cq).clauses();
                            if(bc != null && bc.size() == 1) {
                                if (bc.get(0).getOccur() == BooleanClause.Occur.MUST_NOT) {
                                    // Lucene doesn't support MUST_NOT clauses by themselves because it implements
                                    // NOTs by removing them from the results and when it's by itself there
                                    // are no results to remove from.  ANDing it with the entire document
                                    // set generates equivalent functionality
                                    // Have to rebuild?
                                    BooleanQuery.Builder newCq = new BooleanQuery.Builder();
                                    bc.forEach(newCq::add);
                                    newCq.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                                    cq = newCq.build();
                                }
                            }
                        }
                        bq.add(cq, occur);
                    }
                }

                // if the children collapsed to null so do we
                BooleanQuery builtBq = bq.build();
                List<BooleanClause> clauses = builtBq.clauses();
                if (clauses != null && clauses.size() > 0) {
                    // Note: we used to "simplify" queries with a single
                    // clause to just the clause, but doing so strips the
                    // occur from the parent clause, and that's kind of important.
                    // For this reason the "simplification" has been rolled back.
                    q = builtBq;
                }
            }
        }
        return q;
    }

    /**
     * Convert a LeafFilter to a Query
     * First we do some transfrmations on the property name and value.
     */
    private Query convertLeafFilter(LeafFilter lf, boolean isQuery)
        throws GeneralException {

        Query q = null;
        String property = lf.getProperty();
        Object value = lf.getValue();

        if (property != null && property.endsWith(PROP_ASSIGNED_SCOPE)) {
            property = replaceSuffix(property, PROP_ASSIGNED_SCOPE, AbstractIndexer.FIELD_SCOPE);
            // since we're referencing the Scope object, the value may be a Scope
            if (value instanceof Scope)
                value = ((Scope)value).getPath();
            else if (value == null)
                value = AbstractIndexer.FIELD_VALUE_NULL;
        }
        else if (property != null && property.endsWith(PROP_ASSIGNED_SCOPE_PATH)) {
            property = replaceSuffix(property, PROP_ASSIGNED_SCOPE_PATH, AbstractIndexer.FIELD_SCOPE);
            if (value == null)
                value = AbstractIndexer.FIELD_VALUE_NULL;
        }
        else if (value instanceof SailPointObject) {
            // kludge: It is common in Hibernate to use foo.object
            // to reference something by id rather than foo.object.id.
            // So we don't have to keep remembering this and changing the UI
            // convert here.  Commonly used with "owner"
            property = property + ".id";
            value = ((SailPointObject)value).getId();
        }

        // we always index "id", substutute a special FullTextField
        FullTextField field = _index.getField(property);
        if (field == null && "id".equals(property))
            field = _idField;
            
        if (lf.getSubqueryClass() != null || 
            lf.getSubqueryProperty() != null ||
            lf.getSubqueryFilter() != null) {

            // we can transform one of these
            q = convertSubquery(lf, isQuery);
        }
        else if (property == null) {
            log.error("Missing property name");
        }
        else if (field == null) {
            // all fields must be declared
            log.error("Undeclared field: " + property);
        }
        else if (value == null) {
            // since we don't support any of the unary
            // operators can check value now too
            // suppress warnigns for "id", this is often seen
            // in LCM filters that try to filter things with a 
            // long NOT IN list of ids.  We're trying to make that
            // stop but till then don't emit disturbing warnings
            if (!"id".equals(property))
                log.error("Missing value for property: " + property);
        }
        else if (field.isIgnored() || "id".equals(property)) {
            // this one can be passed down but we ignore it,
            // typically for things like the disabled flag
            // or a big "not in" list, consider supporting not in?
            if (log.isInfoEnabled())
                log.info("Field is ignored: " + property);
        }
        else if ((isQuery && !field.isAnalyzed()) ||
                 (!isQuery && !field.isIndexed())) {

            // Not analyzed or indexed.  Silently remove but warn because
            // it means that something above may need to have prevented 
            // this. Actually this is good for removing things that 
            // may have gotten into a Filter but can't be supported
            // by the index, we can ignore it or fail.

            // TODO: Need option to throw here

            if (isQuery)
                log.error("Field is not analyzed: " + property);
            else
                log.error("Field is not indexed: " + property);
        }
        else {
            LogicalOperation op = lf.getOperation();

            if (op == LogicalOperation.EQ || 
                op == LogicalOperation.NE ||
                op == LogicalOperation.LIKE ) {

                if (op == LogicalOperation.LIKE) {
                    MatchMode mode = lf.getMatchMode();
                    /* We used to only handle EXACT and START match mode.
                     * But LCM defaults to ANYWHERE. It doesn't work in 
                     * Lucene but that's ok, fold it in with the others.
                     * Pre-6.4 we used equality filter in these cases anyway. 
                     */
                    if (mode != MatchMode.EXACT &&
                            mode != MatchMode.START) {
                        log.debug("Folding unsupported match mode " + mode + " in with supported ones");
                    }
                }

                q = makeQuery(property, op, value, isQuery);

                // if NE, wrap it in a BooleanQuery
                if (op == LogicalOperation.NE) {
                    q = new BooleanQuery.Builder().add(q, BooleanClause.Occur.MUST_NOT).build();
                }
            }
            else if (op == LogicalOperation.ISNULL) {
                // Use of this should be limited to assignedScope.path
                // right now.  We could support it elsewhere but
                // it requires that we store our special null
                // symbol which we're not doing for everything.
                // NOTE: Only use this in cases where we know
                // there will be a result.  The "dynamic pruning"
                // approach.
                if (property.endsWith(AbstractIndexer.FIELD_SCOPE)) {
                    Term term = new Term(property, AbstractIndexer.FIELD_VALUE_NULL);
                    q = new TermQuery(term);
                }
                else {
                    log.warn("Ignoring ISNULL filter on " + property);
                }
            }
            else if (op == LogicalOperation.IN) {
                // convert this to a list of ORs
                // the list is usually short
                // should should always be a collectino
                if (!(value instanceof Collection))
                    log.warn("Found IN operator wihtout a list!");
                else {
                    Collection col = (Collection)value;
                    List<BooleanClause> clauses = new ArrayList<>();
                    if (col.size() > 0) {
                        BooleanClause.Occur occur = BooleanClause.Occur.SHOULD;
                        Query first = null;
                        for (Object el : col) {
                            if (el != null) {
                                Query inq = makeQuery(property, LogicalOperation.EQ, el, isQuery);
                                if (inq != null) {
                                    if (first == null) {
                                        first = inq;
                                    }
                                    clauses.add(new BooleanClause(inq, occur));
                                }
                            }
                        }

                        // simplify
                        if (clauses.size() > 0) {
                            if (clauses.size() == 1)
                                q = first;
                            else {
                                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                                clauses.forEach(bq::add);
                                q = bq.build();
                            }
                        }
                    }
                }
            }
            else {
                log.warn("Unable to convert logical operation: " + 
                         op.toString());
            }
        }

        return q;
    }

    /**
     * Replace a path suffix with another.
     * Usually this is used for one of the various paths to assigned scope.
     */
    private String replaceSuffix(String path, String suffix, String replacement) {

        int psn = path.lastIndexOf(suffix);
        if (psn > 0)
            path = path.substring(0, psn) + replacement;
        else if (psn == 0)
            path = replacement;
        return path;
    }

    /**
     * Here from convertLeafFilter if we find a subquery.
     * We only support transformation of one of these, the one used in 
     * request authority filters to filter objects owned by the user
     * OR owned by a workgroup the user is a member of.
     *
     * For workgroup ownership, the parent filter usually looks like
     * this:
     *
     *    Filter.or(Filer.eq("owner.id", owner),
     *              Filter.subquery(......))
     *
     * Since the subquery will end up being an OR list, we could convert
     * this at a higher level and include the owner comparison in
     * with the list of workgroup comparisons but it messes up
     * the logic converting CompositeFilters.  An extra level
     * of OR nesting shouldn't matter to Lucene.
     *
     *
     * For ManagedAttributes we see a subquery for application.owner.id,
     * the rest is the same.
     */
    private Query convertSubquery(LeafFilter lf, boolean isQuery) 
        throws GeneralException {

        Query q = null;

        String property = lf.getProperty();

        if (property != null && 
            property.endsWith("owner.id")) {

            if ("id".equals(lf.getSubqueryProperty())) {

                log.info("Converting identity ownership subquery");

                Filter sub = lf.getSubqueryFilter();
                if (sub instanceof LeafFilter) {
                    LeafFilter slf = (LeafFilter) sub;
                    String id = Util.otoa(slf.getValue());
                    if (id != null) {
                        Filter f = Filter.eq(property, id);
                        q = convertLeafFilter((LeafFilter) f, isQuery);
                    } else {
                        log.error("Unable to convert identity subquery");
                    }
                }
            } else if("workgroups.id".equals(lf.getSubqueryProperty())) {

                log.info("Converting workgroup ownership subquery");

                // supposed to be a subquery with the requesting
                // user's id
                Identity ident = null;
                Filter sub = lf.getSubqueryFilter();
                if (sub instanceof LeafFilter) {
                    LeafFilter slf = (LeafFilter) sub;
                    if ("id".equals(slf.getProperty())) {
                        String id = Util.otoa(slf.getValue());
                        if (id != null)
                            ident = _context.getObjectById(Identity.class, id);
                    }
                }

                if (ident == null) {
                    log.warn("Unexpected workgroup filter structure");
                    // hurts to try this?
                    String name = _context.getUserName();
                    if (name != null)
                        ident = _context.getObjectByName(Identity.class, name);
                }

                if (ident == null)
                    log.error("Unable to convert workgroup subquery");
                else {
                    List<Identity> wgroups = ident.getWorkgroups();
                    if (wgroups != null && wgroups.size() > 0) {
                        List<String> ids = new ArrayList<String>();
                        for (Identity wg : wgroups)
                            ids.add(wg.getId());

                        Filter in = Filter.in(property, ids);
                        q = convertLeafFilter((LeafFilter) in, isQuery);
                    }
                }
            }
        }
        else {
            log.error("Subqueries are not supported in fulltext search");
            log.error("Property: " + lf.getProperty());
        }

        return q;
    }

    /**
     * Build a Query object for a leaf comparison.
     */
    private Query makeQuery(String property, LogicalOperation op, Object value, boolean isQuery) throws GeneralException {

        Query q = null;

        // Boolean and Enums were abbreviated
        // This may not work for all Enums but is enough
        // for Entitlement vs Permission
        // !! Not handling Integer values with GT and LT for role search date ranges
        String svalue = LuceneUtil.getStringValue(value);

        if (isQuery) {
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            BooleanClause.Occur occur = BooleanClause.Occur.SHOULD;
            Term term = new Term(property, svalue);
            // assume prefix always
            q = new PrefixQuery(term);
            
            int len = svalue.length();
            if (len > 2 && svalue.charAt(0) == '"' && svalue.charAt(len-1) == '"') {
                // we should strip the double-quotes here.  Lucene will put them back nicely
                String dequoted = svalue.substring(1, len - 1);
                PhraseQuery phraseQuery = buildPhraseQuery(property, dequoted);
                bq.add(phraseQuery, occur);
                bq.add(q, occur);
                q = bq.build();
            } 
        }
        else {
            // using it for a Filter obey the op
            // since sometimes we need EQ and other times
            // we need LIKE
            if (op == LogicalOperation.LIKE) {
                if (isAnalyzed(property)) {
                    // If the property is analyzed run it through the splitter
                    // so that it can be tokenized using the same filters that
                    // the StandardAnalyzer uses
                    q = splitQuery(property, svalue, true);
                } else {
                    Term term = new Term(property, svalue);
                    q = new PrefixQuery(term);
                }
            } else if (op == LogicalOperation.EQ || op == LogicalOperation.NE) {
                q = buildPhraseQuery(property, svalue);
            } else {
                q = splitQuery(property, svalue, false);
            }
        }

        return q;
    }

    private PhraseQuery buildPhraseQuery(String property, String svalue) throws GeneralException {
        // A phrase query should be a collection of terms, so split the now quote stripped
        // string into individual words using Lucene's StandardTokenizer.
        PhraseQuery.Builder pq = new PhraseQuery.Builder();
        if (isAnalyzed(property)) {
            TokenStream tokenizer = null;
            try {
                tokenizer = getTokenizer(property, svalue);
                int numTokens = 0;
                CharTermAttribute searchTerm = tokenizer.addAttribute(CharTermAttribute.class);
                while (tokenizer.incrementToken()) {
                    String word = searchTerm.toString();
                    Term term = new Term(property, word);
                    pq.add(term);
                    numTokens++;
                }
                // Sometimes the Lucene tokenizer strips out and separates words.
                // When/if that happens we need to add a little slop to the search
                // to maintain the proper match.
                List<String> words = RFC4180LineParser.parseLine(" ", svalue, true);
                int expectedNumTokens = words.size();
                pq.setSlop(Math.abs(expectedNumTokens - numTokens));
            } catch (IOException e) {
                throw new GeneralException("Failed to parse Lucene search term: " + svalue, e);
            } finally {
                try {
                    if (tokenizer != null) {
                        tokenizer.end();
                        tokenizer.close();
                    }
                } catch (IOException e) {
                    throw new GeneralException("Failed to close the Lucene tokenizer", e);
                }
            }
        } else {
            // Leave it as-is without any tokenization
            Term term = new Term(property, svalue);
            pq.add(term);
        }

        return pq.build();
    }

    /*
     * Builds a tokenizer capable of generating search terms for the given property and value, and resets it so that
     * it's ready for consumption using incrementToken().
     * @property Property on which the search is being conducted
     * @value String containing the search terms
     * @return TokenStream capable of generating search terms for the specified property and value.
     */
    private TokenStream getTokenizer(String property, String value) throws IOException {
        Analyzer analyzer = _index.getAnalyzer();
        TokenStream tokenizer = null;
        try {
            tokenizer = analyzer.tokenStream(null, new StringReader(value));
            // If the property is analyzed then apply the same filters that the
            // StandardAnalyzer uses to generate the expected search terms
            if (isAnalyzed(property)) {
                tokenizer = new LowerCaseFilter(tokenizer);
                if (STOPWORDS != null) {
                    tokenizer = new StopFilter(tokenizer, STOPWORDS);
                }
            }
            tokenizer.reset();
        } finally {
            if (analyzer != null) {
                analyzer.close();
            }
        }

        return tokenizer;
    }

    private Query splitQuery(String property, String svalue, boolean isPrefixQuery) throws GeneralException {
        // The query should be a collection of terms, so split the now quote stripped
        // string into individual words.
        BooleanQuery.Builder splitQuery = new BooleanQuery.Builder();
        TokenStream tokenizer = null;

        try {
            tokenizer = getTokenizer(property, svalue);
            CharTermAttribute searchTerm = tokenizer.addAttribute(CharTermAttribute.class);
            while (tokenizer.incrementToken()) {
                // Filter out stop words and strip punctuation because the 
                // Standard Analyzer that we use for Lucene filtered them out
                // when indexing and will fail if they are included
                String word = searchTerm.toString();
                if (!Util.isNullOrEmpty(word)) {
                    Term term = new Term(property, word);
                    Query query;
                    if (isPrefixQuery) {
                        query = new PrefixQuery(term);
                    } else {
                        query = new TermQuery(term);
                    }
                    splitQuery.add(query, BooleanClause.Occur.MUST);
                }
            }
        } catch (IOException e) {
            throw new GeneralException("Failed to parse Lucene search term: " + svalue, e);
        } finally {
            try {
                if (tokenizer != null) {
                    tokenizer.end();
                    tokenizer.close();
                }
            } catch (IOException e) {
                throw new GeneralException("Failed to close the Lucene tokenizer", e);
            }
        }
        return splitQuery.build();
    }

    private boolean isAnalyzed(String property) {
        boolean analyzed = false;
        FullTextField f = _index.getField(property);
        if (f != null) {
            analyzed = f.isAnalyzed();
        }
        return analyzed;
    }

    /**
     * Search the index given a Lucene Query.
     */
    private SearchResult search(Query query, int start, int max)

        throws GeneralException {

        if (_index == null)
            throw new GeneralException("Scope not set");

        SearchResult sresult = new SearchResult();
        sresult.rows = new ArrayList<Map<String,Object>>();

        // think about allowing scope to be passed in the constructor
        if (_index == null)
            throw new GeneralException("Index scope not set");

        Directory dir = null;
        DirectoryReader dirReader = null;
        IndexSearcher searcher = null;
        String path = LuceneUtil.getIndexPath(_index);
        
        try {
            dir = FSDirectory.open(new File(path).toPath());
            dirReader = DirectoryReader.open(dir);
            searcher = new IndexSearcher(dirReader);

            // Third arg is max results, note that this is the total cap, what
            // is passed in QueryOptinos is the page max.
            // Hmm, we may need to use a Collector here?  let's cap this
            // at 10,000 for now and see how this falls out.  This really 
            // shouldn't be used to iterate over complete result sets.
            // Learn more about Collectors...
            int luceneLimit = _maxResults > 0 ? _maxResults : 10000;
            if (log.isDebugEnabled()) {
                log.debug("Using Lucene query: " + query);
            }
            TopDocs result = null;
            //initalize hits with empty array
            ScoreDoc[] hits = new ScoreDoc[]{};
            
            if (query != null) {
                result = searcher.search(query, luceneLimit);
                hits = result.scoreDocs;
            }

            sresult.totalRows = hits.length;
            int added = 0;

            // rather horrible implemenation of "paging"
            for (int i = start ; i < hits.length && (max == 0 || added < max) ; i++) {
                Document doc = searcher.doc(hits[i].doc);
                // sigh, althrough we will always return a Map<String,String>
                // this is hard to use with existing BaseListResource code
                // which always expects Map<String,Object>
                Map<String,Object> map = new HashMap<String,Object>();
                
                // Pull back everything that is stored, unless we start having
                // a large number of fields I don't think there is any
                // significant difference that would require "projection" searches
                // to only pull back certain fields
                List<IndexableField> fields = doc.getFields();
                if (fields != null) {
                    for (IndexableField field : fields) {
                        IndexableFieldType type = field.fieldType();
                        // don't bother asking for things that aren't stored
                        // might provoke an exception?
                        if (type.stored()) {
                            String name = field.name();
                            map.put(name, doc.get(name));
                        }

                    }
                }

                sresult.rows.add(map);
                added++;
            }
        }
        catch (IOException e) {
            throw new GeneralException(e);
        }
        finally {
            try {
                if (dirReader != null) dirReader.close();
            }
            catch (IOException e) {
                log.error("Unable to close DirectoryReader", e);
            }
            try {
                if (dir != null) dir.close();
            }
            catch (IOException e) {
                log.error("Unable to close Directory", e);
            }

        }

        return sresult;
    }

}    
