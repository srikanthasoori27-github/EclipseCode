/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

/**
 * This class contains functionality common to the sailpoint.reporting.datasource.SailPointDataSource and 
 * sailpoint.reporting.datasource.SailPointUnionDataSource classes.  This class should only ever be extended 
 * by one of those two classes.  If functionality from this class is needed one of those two classes 
 * should be extended rather than this one.
 * @author peter.holcomb, Dan.Smith@sailpoint.com
 *
 */
public abstract class StandardSailPointDataSource<E extends SailPointObject> extends AbstractDataSource {

	private static final Log log = LogFactory.getLog(StandardSailPointDataSource.class);

//	djs : TODO: should these be on the interface? 
	List<Filter> filters;
	QueryOptions qo;

    /**
     * Flag that tells us we've already printed the header in csvs
     * 
     */
    boolean _headerPrinted;

	/**
	 * Flag that we've allready done our prepare.
	 */
	boolean _prepared;

	/**
	 * Currently selected object.  May be null until one is
	 * selected from the list page.  Used by both the details
	 * and edit pages.
	 */
	E _object;

    protected StandardSailPointDataSource() {
        super();
    }

    public StandardSailPointDataSource(Locale locale, TimeZone timezone) {
        this(null, locale, timezone);
    }

    /**
	 * 
	 * 
	 */
	 public StandardSailPointDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
		 this(filters, locale, timezone, false);
	 }

	/**
	 *
	 *
	 */
	public StandardSailPointDataSource(List<Filter> filters, Locale locale, TimeZone timezone, boolean distinct) {
		super(locale, timezone);
		qo = new QueryOptions();
		filters = filters;
		_prepared = false;
		_object = null;

		if(filters != null) {
			for(Filter filter : filters) {
				//log.debug("Adding Filter: " + filter.toString());
				qo.add(filter);
			}
		}

		qo.setDistinct(distinct);
		log.info("QueryOptions == \n" + qo.toString());
	}

	 /**
	  * Optional, hook for sub-classes to do stuff before
	  * the iteration starts.  Does nothing by default but
	  * thinks like the initial query for objects or populating
	  * the _objectCount of objects.
	  */
	 public void internalPrepare() throws GeneralException {
	 }

	 /* (non-Javadoc)
	  * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
	  */
	 public Object getFieldValue(JRField jrField) throws JRException {
		 String fieldName = jrField.getName();
		 Object value = null;
         
         /** A standard way of feeding the report the total number of objects processed*/
         if(fieldName.equals("OBJECT_COUNT")) {
             return getProcessed();
         }
         
		 if (fieldName != null && fieldName.toLowerCase().contains("password")) {
			 value = "********";
		 }
		 else {
			 // bug 28026 - Swizzling the calls so that we attempt to get the nested
			 // property first. If we try to get an attribute like manager.displayName
			 // as an extended attribute first the method Identity.getAttribute("manager")
			 // will return manager.name In fact, getting manager.<anything> as an
			 // extended attribute will always return manager.name.
			 try {
				 value = PropertyUtils.getNestedProperty(_object, fieldName);
			 } catch(Exception e) {
				 //if the above call throws an exception we presume the beanShell failed
				 //and we need to see if it is an extended attribute.
				 value = _object.getExtendedAttribute(fieldName);
				 if (value == null) {
					 log.info("Unable to get field value for fieldName: " + fieldName + " from object: " + _object
							 + " Exception: " + e.getMessage());
				 }
			 }

			 if(value instanceof Enum) {
				 value = ((Enum)value).name();
			 }
		 }

		 if(value==null) {
			 if(fieldName.equals("ownerName")) {
				 Identity owner = _object.getOwner();
				 if(owner!=null)
					 value = owner.getName();
			 }
		 }

		 return value;
	 }

	 /**
	  * To be overridden by subclasses.
	  * This method can assume prepare has been
	  * called once.
	  */
	 abstract public boolean internalNext() throws JRException;

	 /**
	  *  This method calls down to internalNext() and that
	  *  method should be overridden by subclasses.  This class
	  *  assures all datasources are prepared before the iteration
	  *  starts. It also keeps track of the number of objects
	  *  processed along with some debuging for performance
	  *  analysis.
	  */
	 abstract public boolean next() throws JRException;

}
