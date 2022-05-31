/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.scriptlet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.jasperreports.engine.JRDefaultScriptlet;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

/**
 * Base scriptlet class. Provides common utility methods, including methods
 * to query a Resolver instance.
 *
 * User: jonathan.bryant
 * Created: 1:35:12 PM Jun 27, 2007
 */
public class BaseScriptlet extends JRDefaultScriptlet {

    /**
     * Default constructor
     */
    public BaseScriptlet() {
    }

    /**
     * Performs a query on the Resolver with for objects of class 'beanClass' where
     * the value of 'filterProperty' = 'filterValue'.
     *
     * @param resolver Resolver instance to query with, may not be null.
     * @param beanClass Object class to query for
     * @param filterProperty Object property to query for equality
     * @param filterValue Value of object property
     * @return Objects returned by the query
     */
    public List getObjects(Resolver resolver, Class beanClass, String filterProperty, Object filterValue) {
        return getObjects(resolver, beanClass, Arrays.asList(new Filter[]{Filter.eq(filterProperty, filterValue)}));
    }

    /**
     * Performs a query on the Resolver with for objects of class 'beanClass' which
     * match the supplied filters.
     *
     * @param resolver Resolver instance to query with, may not be null.
     * @param beanClass Object class to query for
     * @param filters Query filters
     * @return  objects returned by the query
     */
    public List getObjects(Resolver resolver, Class beanClass, List<Filter> filters) {
        return getObjects(resolver, beanClass, filters, null);
    }

    /**
     * Performs a query on the Resolver with for objects of class 'beanClass' which
     * match the supplied filters. Results sorted by the property specified in orderBy.
     *
     * @param resolver Resolver instance to query with, may not be null.
     * @param beanClass Object class to query for
     * @param filters Query filters
     * @param orderBy Property to sort the returned results
     * @return  objects returned by the query
     */
    public List getObjects(Resolver resolver, Class beanClass, List<Filter> filters, String orderBy) {

        if (beanClass == null)
            throw new IllegalArgumentException("Bean class was null.");

        if (resolver == null)
            throw new IllegalArgumentException("Resolver was null.");

        QueryOptions queryOptions = new QueryOptions();
        if (filters != null) {
            for (Filter filter : filters) {
                queryOptions.add(filter);
            }
        }

        if (orderBy != null)
            queryOptions.setOrderBy(orderBy);

        try {
            return resolver.getObjects(beanClass, queryOptions);
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Converts any collection to a comma separated list.
     *
     * @param col Collection to convert to a comma separated list.
     * @return Comma separated list, or null if collection
     *         is empty or null
     */
    public String collectionToCsv(Collection col) {
        String csv = null;

        if (col != null && !col.isEmpty()) {
            StringBuffer b = new StringBuffer();
            for (Iterator iterator = col.iterator(); iterator.hasNext();) {
                Object o = iterator.next();
                if (o != null) {
                    if (b.length() != 0)
                        b.append(", ");
                    b.append(o.toString());
                }
            }
            csv = b.toString();
        }
        return csv;
    }

    /**
     * Return a comma-separated list of names of the given sailpoint objects.
     */
    public String getSailPointObjectNamesCsv(List<? extends SailPointObject> objs)
        throws GeneralException {

        return WebUtil.objectListToNameString(objs);
    }
}
