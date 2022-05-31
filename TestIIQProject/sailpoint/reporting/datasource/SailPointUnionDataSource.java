/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

/**
 * This class extracts properties across multiple SailPointObjects.  Intended use cases are for objects that
 * have some overlap in properties and should at least have commonality in the data reported on, although this is not required.  
 * Examples are WorkItem and WorkItemArchive, Certification and CertificationArchive, CertificationItem and IdentityHistoryItem, etc.
 * @author Bernie.Margolis@sailpoint.com
 * 
 */
public abstract class SailPointUnionDataSource<E extends SailPointObject> extends StandardSailPointDataSource<E> implements TopLevelDataSource {

    private static final Log log = LogFactory.getLog(SailPointUnionDataSource.class);

    /**
     * The top level object type that will be * iterated.
     */
    Class[] _scopes;

    /**
     * This array contains a candidate object from each iterator. Each time the
     * candidate is promoted to the current _object, it's replaced with the next
     * item in the iterator. Once the iterator runs out, it's set to null.
     */
    SailPointObject[] _candidates;

    /**
     * Array of iterators containing the SailPointObjects that are being
     * 'unified'. This is an array of objects because Java won't let us
     * construct an array of Iterator<SailPointObject>
     */
    Object[] _objects;

    /*
     * Filter map scoping a list of filters to each scoped class to be reported on
     */
    private Map<Class, List<Filter>> _filters;

    protected SailPointUnionDataSource(Class[] scopes) {
        super();
        _scopes = scopes;
    }

    public SailPointUnionDataSource(Class[] scopes, Locale locale, TimeZone timezone) {
        super(locale, timezone);
        _scopes = scopes;
        _filters = new HashMap<Class, List<Filter>>();
    }

    /**
	 * 
	 */
    public SailPointUnionDataSource(Class[] scopes, Map<Class, List<Filter>> filters, Locale locale, TimeZone timezone) {
        super(locale, timezone);
        _objects = null;
        _scopes = scopes;
        _filters = filters;
        
    }
    
    /*
     * Clones the backing QueryOptions and augments the filter set with "personalized" filters
     * for the given class.  This allows us to 'join' across classes that do not share equivalent property sets
     */
    private QueryOptions getScopedQueryOptions(Class forClass) {
        QueryOptions scopedQo = new QueryOptions(qo);
        List<Filter> scopedFilters = _filters.get(forClass);
        if (scopedFilters != null) {
            scopedQo.add(scopedFilters.toArray(new Filter[scopedFilters.size()]));
        }
        return scopedQo;
    }

    /**
     * Prepare for the iteration over the datasource objects. This method calls
     * internalPrepare so subclasses need to over-ride internalPrepare if they
     * need to do something before the iteration is started.
     */
    private void prepare() throws JRException {
        resetProcessed();
        initStart();
        log.info("DataSource prepare Thread [" + Thread.currentThread().getId() + "]");
        try {
            log.info("Calling internal Prepare");
            internalPrepare();
            log.info("Internal Prepare complete");

            // count the objects
            Class<SailPointObject>[] classes = getScopes();
            if (classes != null) {
                int cnt = 0;
                for (int i = 0; i < classes.length; ++i) {
                    if (classes[i] != null) {
                        log.info("Querying for count of [" + classes[i].getName() + "]");
                        QueryOptions scopedQo = getScopedQueryOptions(classes[i]);
                        cnt += getContext().countObjects(classes[i], scopedQo);
                    }
                }
                setObjectCount(cnt);
                log.info("Return from querying for count.");
            }

            // Iniitialize the object iterators and the candidates
            _objects = new Object[_scopes.length];
            _candidates = new SailPointObject[_scopes.length];

            for (int i = 0; i < _scopes.length; ++i) {
                updateProgress("Querying for WorkItems...");
                QueryOptions scopedQo = getScopedQueryOptions(classes[i]);
                Iterator<SailPointObject> objIterator = getContext().search(_scopes[i], scopedQo);
                _objects[i] = objIterator;
                if (objIterator != null && objIterator.hasNext()) {
                    _candidates[i] = objIterator.next();
                } else {
                    _candidates[i] = null;
                }
            }

        } catch (GeneralException e) {
            if (log.isErrorEnabled())
                log.error("Exception executing prepare: " + e.getMessage(), e);

            throw new JRException(e);
        }
        initBlockStart();
    }

    /**
     * Optional, hook for sub-classes to do stuff before the iteration starts.
     */
    public void internalPrepare() throws GeneralException {

    }

    /**
     * This method calls down to internalNext() and that method should be
     * overridden by subclasses. This class assures all datasources are prepared
     * before the iteration starts. It also keeps track of the number of objects
     * processed along with some debuging for performance analysis.
     */
    public boolean next() throws JRException {
        if (!_prepared) {
            // call this before we start iterating gives
            // us a chance to do stuff thread specific
            // within the calling thread context
            prepare();
            _prepared = true;
        }
        boolean next = internalNext();
        if (next) {
            incProcessed();
            if ((getProcessed() % 1000) == 0) {
                logStats(false);
                initBlockStart();
            }
        } else {
            logStats(true);
        }
        return next;
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    // Scopes
    //
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Specify the persistent object class we will be dealing with. If set by
     * the subclass, the _objectCont field will be filled in automatically.
     */
    public void setScopes(Class[] c) {
        _scopes = c;
    }

    public Class[] getScopes() {
        return _scopes;
    }
}
