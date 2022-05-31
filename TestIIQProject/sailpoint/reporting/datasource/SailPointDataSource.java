/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

/**
 * This class extracts properties from a single type of SailPointObject that is being reported on
 * @author peter.holcomb, Dan.Smith@sailpoint.com
 *
 */
public abstract class SailPointDataSource<E extends SailPointObject> 
    extends StandardSailPointDataSource<E> 
    implements TopLevelDataSource {

	private static final Log log = LogFactory.getLog(SailPointDataSource.class);

	/**
	 * The top level object type that will be * iterated.
	 */
	Class _scope;
	
	/**
	 * Iterator over the objects being reported on
	 */
	Iterator<E> _objects;

    protected SailPointDataSource() {
        super();
    }

    public SailPointDataSource(Locale locale, TimeZone timezone) {
        this(null, locale, timezone);
    }

    /**
	 * 
	 * 
	 */
	 public SailPointDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
		 this(filters, locale, timezone, false);
	 }

	/**
	 *
	 *
	 */
	public SailPointDataSource(List<Filter> filters, Locale locale, TimeZone timezone, boolean distinct) {
		super(filters, locale, timezone, distinct);
		_objects = null;
		_scope = null;
		log.info("QueryOptions == \n" + qo.toString());
	}

	/**
	  * Prepare for the iteration over the datasource objects.
	  * This method calls internalPrepare so subclasses need
	  * to over-ride internalPrepare if they need to do something
	  * before the iteration is started.
	  */
	 private void prepare() throws JRException {
		 resetProcessed();
		 initStart();
		 log.info("DataSource prepare Thread ["+Thread.currentThread().getId()+"]");
		 try {
			 log.info("Calling internal Prepare");
			 internalPrepare();
			 log.info("Internal Prepare complete");
			 Class clazz = getScope();
			 if ( clazz != null ) {
				 log.info("Querying for count of ["+clazz.getName()+"]");
                 setObjectCount(getContext().countObjects(clazz, qo));
				 log.info("Return from querying for count.");
			 }
		 } catch(GeneralException e ) {
		     if (log.isErrorEnabled())
		         log.error("Exception executing prepare: " + e.getMessage(), e);

			 throw new JRException(e);
		 }
		 initBlockStart();
	 }

	 /**
	  * Optional, hook for sub-classes to do stuff before
	  * the iteration starts.  Does nothing by default but 
	  * thinks like the initial query for objects or populating
	  * the _objectCount of objects.
	  */
	 public void internalPrepare() throws GeneralException {
	 }

	 /**
	  *  This method calls down to internalNext() and that
	  *  method should be overridden by subclasses.  This class
	  *  assures all datasources are prepared before the iteration
	  *  starts. It also keeps track of the number of objects
	  *  processed along with some debuging for performance
	  *  analysis.
	  */
	 public boolean next() throws JRException {
		 if ( !_prepared ) {
			 // call this before we start iterating gives
			 // us a chance to do stuff thread specific
			 // within the calling thread context
			 prepare();
			 _prepared = true;
		 }
		 boolean next = internalNext();
		 if ( next ) {
			 incProcessed();
			 if ( ( getProcessed() % 1000 ) == 0 ) {
				 logStats(false);
				 initBlockStart();
			 }
		 } else {
		     logStats(true);
		 }
		 return next;
	 }

	 ///////////////////////////////////////////////////////////////////////////
	 //
	 // Scope
	 //
	 ///////////////////////////////////////////////////////////////////////////

	 /**
	  * Specify the persistent object class we will be dealing with.
	  * If set by the subclass, the _objectCount field will be filled
	  * in automatically.
	  */
	 public void setScope(Class c) {
		 _scope = c;
	 }

	 public Class getScope(){
		 return _scope;
	 }
}
