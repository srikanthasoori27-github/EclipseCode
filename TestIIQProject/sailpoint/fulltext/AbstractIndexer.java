/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The base class for bulding Lucene indexes.  This handles the Lucene API,
 * subclasses will provide the gathering of data from the appropriate place.
 *
 * Author: Jeff
 *
 */

package sailpoint.fulltext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;

import sailpoint.api.IdIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.FullTextIndex.FullTextField;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public abstract class AbstractIndexer {

    // let FullTextifier control logging for all of the helper classes like
    //  it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The number of objects we will index before clearing the cache.
     */
    public static final int MAX_CACHE_AGE = 100;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Common fields
    //
    
    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DISPLAYABLE_NAME = "displayableName";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_SCOPE = "assignedScope.path";

    public static final String FIELD_OWNER_ID = "owner.id";
    public static final String FIELD_OWNER_NAME = "owner.name";

    // note that unlike most newer uses of display names, Identity
    // does not have a "displayable" property, it is just "displayName"
    // and it will be have a duplicate the name
    public static final String FIELD_OWNER_DISPLAY_NAME = "owner.displayName";

    /**
     * Holds the targets or "tags" associated with this object.
     * This is an implicit field that does not have to be defined
     * in the FullTextIndex.  It will be added if ARG_INCLUDE_TARGETS is on.
     */
    public static final String FIELD_TARGETS = "targets";
    
    /**
     * The special value we use to represent null.
     * You can't put a null value in the index and in a few cases 
     * we need to filter on something being null.  One is 
     * "assignedScope.path".
     */
    public static final String FIELD_VALUE_NULL = "*null*";

    // 
    // Runtime state for index building
    //

    Builder _builder;
    FullTextIndex _index;
    boolean _terminate;

    /**
     * Flag indicating that the subclass is supposed to add it's
     * class name to the index, used when several class indexes are
     * combined.
     */
    boolean _addObjectClass;
    
    /**
     * For simple class indexers, the list of extended attributes defined for
     * that class.
     */
    List<ObjectAttribute> _extended;

    /**
     * For simple object indexes, the total number of objects scanned.
     * Note that this may be different than the number of objects actually
     * placed in the index if the subclass filters some of them.  This
     * can be used along with _highestObjectDate to save state for optimized
     * stale index checking.
     */
    int _objectsScanned;

    /**
     * For simple object indexes, the highest created or modifed date
     * of the objects scanned.  This can be used along with _objectsScanned
     * to save state for optimized stale index checking.  Note that this
     * only works if you are not using a Filter to restrict the number
     * of objects scanned.
     */
    Date _highestObjectDate;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public AbstractIndexer() {
    }

    public void terminate() {
        _terminate = true;
    }

    public void setAddObjectClass(boolean b) {
        _addObjectClass = b;
    }

    public int getObjectsScanned() {
        return _objectsScanned;
    }

    public Date getHighestObjectDate() {
        return _highestObjectDate;
    }



    //////////////////////////////////////////////////////////////////////
    //
    // Subclass Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if the index is enabled and should be used in searching.
     * This is expected to check a system configuration option.
     */
    public abstract boolean isSearchEnabled();

    /**
     * Return true if the index is stale and needs to be refreshed.
     * Usually will call back to the common isIndexStale(Date, int)
     * method below with the relevant statistics.
     */
    public abstract boolean isIndexStale() throws GeneralException;

    /**
     * Returnt the SailPointObject subclass to be indexed.  We will
     * provide the outer loop and call index(SailPointObject,Document)
     * for each object. If a subclass needs more control it can overload
     * the entire doIndex() method.
     */
    public abstract Class<? extends SailPointObject> getIndexClass();

    /**
     * Do indexing for one object.
     */
    public abstract Document index(SailPointObject obj)
        throws GeneralException, CorruptIndexException, IOException;

    /**
     * Allow the subclass to save state related to the last index refresh
     * that can be used by the next call to isIndexStale to skip refresh
     * if nothing changed.
     */
    public abstract void saveRefreshState();

    /**
     * Add index building statistics to the task result.
     */
    public abstract void saveResults(TaskResult result);

    //////////////////////////////////////////////////////////////////////
    //
    // Stale Index Detection
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Common implementation of a stale index checker based on 
     * creation and modification times.  Used in cases where you can't
     * be smart and incrementally replace or remove things from the index.
     */
    public <T extends SailPointObject> boolean isIndexStale(Class<T> clazz, Date lastDate, int lastCount)
        throws GeneralException {

        boolean stale = false;
        if (lastDate == null) {
            stale = true;
        }
        else {
            SailPointContext spc = _builder.getSailPointContext();
            int count = spc.countObjects(clazz, null);
            if (count != lastCount) {
                stale = true;
            }
            else {
                stale = isIndexStale(clazz, lastDate);
            }
        }

        return stale;
    }

    public <T extends SailPointObject> boolean isIndexStale(Class<T> clazz, Date lastDate)
            throws GeneralException {
        boolean stale = false;
        if (lastDate == null) {
            stale = true;
        } else {
            SailPointContext spc = _builder.getSailPointContext();
            // !! created and modified are not indexed, for the classes where
            // we query on those for cache invalidation may want to do that.
            // A table scan is still better than rebuilding the index but it's more
            // work than we need.  If we do add those indexes, then we'll need
            // to do the queries one at a time or make a composite index.
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.or(Filter.gt("modified", lastDate),
                    Filter.gt("created", lastDate)));
            int modified = spc.countObjects(clazz, ops);
            if (modified > 0) {
                stale = true;
            }
        }
        return stale;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Outer Loop
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by Builder immediately after construction.
     * This gives the subclass a chance to make adjutments to the
     * FullTextIndex or cache things.
     */
    public void prepare(Builder builder) throws GeneralException {

        _builder = builder;
        _index = builder.getIndexDefinition();
        _objectsScanned = 0;
        _highestObjectDate = null;
    }

    /**
     * Default implementation for addDocuments will add documents for a 
     * single class.  Subclass is expected to implement getIndexClass.
     * If you need more control over what goes into the index (I'm looking
     * at you BundleManagedAttributeIndexer) you need to override this method.
     */
    public void addDocuments() throws GeneralException {

        SailPointContext spc = _builder.getSailPointContext();

        Class<? extends SailPointObject> clazz = getIndexClass();
        if (clazz == null) {
            throw new GeneralException("Indexer did not implement getIndexClass");
        }

        // remember this for when the subclass calls addExtendedAttributes
        ObjectConfig config = ObjectConfig.getObjectConfig(clazz);
        _extended = (config == null ? null : config.getObjectAttributes());

        List<String> props = new ArrayList<String>();
        props.add("id");
        QueryOptions queryOptions = getQueryOptions();

        Iterator<Object[]> idresult = spc.search(clazz, queryOptions, props);
        // this isn't a decaching iterator because we decache in the loop
        IdIterator idit = new IdIterator(idresult);

        int cacheAge = 0;
        Integer sleeptime = _builder._arguments.getInteger("debugSleepTime");

        while (idit.hasNext() && !_terminate) {
            String id = idit.next();
            SailPointObject obj =  spc.getObjectById(clazz, id);
            if (obj != null) {

                // FullTextifier will trace so we don't need to
                String name = obj.getName();
                if (name == null) {
                    // classes that don't have names are supposed
                    // to make this nice
                    name = obj.toString();
                }
                _builder.updateProgress(name);

                try {
                    Document doc = index(obj);
                    if (doc != null) {
                        _builder.addDocument(doc);
                    }

                    // remember the watermark for optimized index refresh
                    _objectsScanned++;
                    Date date = obj.getModified();
                    if (date == null) {
                        date = obj.getCreated();
                    }
                    if (date != null && (_highestObjectDate == null || date.after(_highestObjectDate))) {
                        _highestObjectDate = date;
                    }
                }
                catch (CorruptIndexException e) {
                    _index.addError(e);
                    throw new GeneralException(e);
                }
                catch (IOException e) {
                    _index.addError(e);
                    throw new GeneralException(e);
                }

                cacheAge++;
                if (cacheAge > MAX_CACHE_AGE) {
                    spc.decache();
                    cacheAge = 0;
                }
            }
            // IIQCB-3339: Debug step.
            // The value for sleep time should be the necessary time to shutdown a DB.
            if(sleeptime != null){
                log.debug("Waiting time to test Full Text Index Refresh has started. " +  sleeptime + " milliseconds.");
                Util.sleep(sleeptime);
                log.debug("Waiting time to test Full Text Index Refresh has finished.");
                sleeptime = null;
            }
        }
        // IIQCB-3339: Explicit commit to refresh the index. This will force to only have a new index version in case
        // the index is not truncated.
        try {
            _builder.commitIndex();
            log.debug("New index successfully generated and committed.");
        } catch (IOException e) {
            _index.addError(e);
            throw new GeneralException(e);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Field Addition
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add the id field. This is always stored and never indexed.
     */
    protected void addId(Document doc, String id) {
        if (id != null && id.length() > 0) {
            // origianly we did not index this, but LCM needs
            // to filter out roles from the list of already assigned
            // so index it
            doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        }
    }

    protected void addScope(Document doc, String name, Scope scope) {

        String path = (scope != null) ? scope.getPath() : null;
        
        if (path != null)
            addField(doc, name, path);
        else {
            // scope is special, we need to support null searches
            addField(doc, name, FIELD_VALUE_NULL);
        }
    }

    /**
     * Add a field to a document, checking for null because that's apparently
     * to difficult for Document to do.  Check the list of indexed fields and
     * stored fields to decide how to add it.
     *
     * getIndexValue will do some standard transformations.
     * 
     */
    protected void addField(Document doc, String name, Object value) {

        // coerce the value to a string, except Integers
        // ints are used for date range queries and have to be stored
        // as IntFields
        // !!GAK, our version of lucene doesn't support IntField, so numeric
        // comparisons won't work.  Will have to ignore="true" these or better
        // change the UI to not pass them down
        String svalue = LuceneUtil.getStringValue(value);

        // Kludge for scopes.  This is the only one we will allow
        // to use Filter.isnull, if we have others then we'll need to
        // declare them because I'm not sure we want to do this
        // for every value?
        if (svalue == null && FIELD_SCOPE.equals(name)) {
            svalue = FIELD_VALUE_NULL;
        }
        
        FullTextField field = _index.getField(name);

        if (svalue != null && field != null) {

            if (field.isAnalyzed() || field.isIndexed() || field.isStored()) {

                if (log.isDebugEnabled()) {
                    if (field.isAnalyzed())
                        log.debug("  Analyzing " + name + "=" + svalue);

                    if (field.isIndexed())
                        log.debug("  Indexing " + name + "=" + svalue);

                    if (field.isStored())
                        log.debug("  Storing " + name + "=" + svalue);
                }

                // while not correct, the stock configurations have
                // been assuming that indexed='true' implise stored='true'
                // and it's too late to back that out now
                Field.Store fs = Field.Store.NO;
                if (field.isIndexed() || field.isStored())
                    fs = Field.Store.YES;

                Field luceneField = null;
                if (field.isAnalyzed()) {
                    // TextField is always analyzed and indexed
                    luceneField = new TextField(name, svalue, fs);
                } else if (field.isIndexed()) {
                    // String field is indexed but never analyzed
                    luceneField = new StringField(name, svalue, fs);
                } else if (field.isStored()) {
                    // StoredField is never indexed or analyzed
                    luceneField = new StoredField(name, svalue);
                }

                if (luceneField != null) {
                    doc.add(luceneField);
                }
            }
        }
    }

    /**
     * Add extended attributes to the Document.
     * !! For these we probably need more control over what is stored.  There
     * could be some large ones that aren't really necessary.
     */
    protected void addExtendedAttributes(Document doc, SailPointObject obj) {

        if (_extended != null) {
            Map<String,Object> atts = obj.getExtendedAttributes();
            if (atts != null) {
                for (ObjectAttribute att : _extended) {
                    String name = att.getName();
                    Object value = atts.get(name);
                    if (value != null && (value instanceof String || value instanceof Boolean)) {
                        addField(doc, name, value);
                    }
                }
            }
        }
    }

    /**
     * Add localized descriptions to the document.
     *
     * For now just concatenate all of them, under the assumption that
     * you will tend not to get cross-langauge hits.  This may not
     * be a good idea but then we'd need a field for each language.
     *
     * Since these are going to be mangled we don't need to store the value
     * but we could avoid an Explanator cache lookup if we just
     * returned the right description from the index?
     */
    protected void addDescriptions(Document doc, Map<String,String> descs) {

        FullTextField field = _index.getField(FIELD_DESCRIPTION);

        if (descs != null && field != null && field.isAnalyzed()) {

            StringBuffer b = new StringBuffer();
            Iterator<String> values = descs.values().iterator();
            while (values.hasNext())
                b.append(values.next()).append(" "); 

            // note that we clear the flag to NOT store it, only index
            // also ensure we do not store the crappy html that will make
            // searching these html-ized descriptions nearly impossible.
            addField(doc, FIELD_DESCRIPTION, WebUtil.stripHTML(b.toString()));
        }
    }

    /**
     * Add targets to the document.
     * This is enbled if ARG_INCLUDE_TARGETS is true.
     * 
     * I suppose we could also do this by having "tags" or "targets" be a FullTextField,
     * that might be more obvious, but it gives them the ability to play
     * play with indexing options and we don't need that.  These are
     * always simply analyzed.
     *
     * Since there can be a massive number of targets, this may be an issue
     * might have to split this into multiple fields?
     */
    protected void addTargetFields(Document doc, Collection<String> targets) {

        if (Util.size(targets) > 0) {
            StringBuilder b = new StringBuilder();
            for (String target : targets) {
                if (!Util.isNullOrEmpty(target)) {
                    b.append(target);
                    b.append(" ");
                }
            }

            String value = b.toString();
            if (!Util.isNullOrEmpty(value)) {
                // jsl - it seems necessary to have these Field.Store.YES even though
                // we don't actually want to store or return them, figure out what's going on
                doc.add(new TextField(FIELD_TARGETS, value, Field.Store.YES));

                if (log.isDebugEnabled()) {
                    log.debug("  Adding targets " + value);
                }
            }
        }
    }

    /**
     * Returns the query options to tailor the list of objects added to the index. By default this is null, so we
     * add every object with type index class. Subclasses can override this to provide simple things like filters
     * without having to override the whole addDocuments() method.
     * @return the query options
     */
    protected QueryOptions getQueryOptions() {
        return null;
    }

}
        
