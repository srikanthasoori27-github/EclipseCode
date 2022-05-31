/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.tools.GeneralException;

/**
 * 
 * @author justin.williams
 */
public class IdentityProjectionDataSource extends SailPointDataSource<Identity> {

    public IdentityProjectionDataSource( final List<Filter> filters, final List<IdentityProperty> properties, Locale locale, TimeZone timezone) {
        super(locale, timezone);
         /* Given null create empty lists, because they are nicer to work with */
        if( properties == null ) {
            this.properties = new ArrayList<IdentityProperty>();
        } else  {
            this.properties = properties;
        }
        if( filters == null ) {
            this.filters = new ArrayList<Filter>();
        } else {
            this.filters = filters;
        }
        initialized = false;
    }

    /**
     * Sets up next iteration
     * TODO: May need to use pagination for resultIterator with getListResult instead of iterate due to bug in Hibernate
     * not adhering to ResultSet.holdability https://hibernate.atlassian.net/browse/HHH-10394 -rap
     */
    @Override
    public boolean internalNext() throws JRException {
        /* First time through initialize counters and DataSource */
        if( !initialized ) {
            try {
                initialize();
            } catch ( GeneralException e ) {
                throw new JRException( "Unable to initialize iterator", e );
            }
        }
        boolean response = false;
        /* If there are more results in the iterator
         *   increment counts, cleanup, etc 
         *   load the next result
         *   update progress 
         */
        if( resultIterator.hasNext() ) {
            if( resultTicker > MAX_RESULTS_BEFORE_DECACHE ) {
                try {
                    getContext().decache();
                } catch ( GeneralException e ) {
                    throw new JRException( "Problem decaching datasource", e );
                }
                resultTicker = 0;
            }
            resultTicker++;
            Object[] resultEntry = resultIterator.next();
            resultMap = createResultMap( resultEntry, properties );
            updateProgress( Identity.class.getSimpleName(), "" );
            response = true;
        }
        return response;
    }
    
    /**
     * Gets value for field 
     */
    @Override
    public Object getFieldValue( final JRField field ) throws JRException {
        /* Check for illegal cases */
        if( field == null || field.getName() == null ) {
            throw new JRException( new IllegalArgumentException( "Field and Field name must not be null" ) );
        }
        if( resultMap == null ) {
            throw new JRException( new IllegalStateException( "ResultMap has not been initialized" ) );
        }
        if( !resultMap.containsKey( field.getName() ) ) {
            throw new JRException( new IllegalArgumentException( "Field ["+ field.getName() + "] is not supported by this data source" ) );
        }
        //TODO: Setup a cool extensible without inheritance way to get more than just toString values
        return resultMap.get( field.getName() ).toString();
    }
    
    /**
     * Initializes data set Iterator and various counts.  Should only be 
     * called once interalNext()
     *  
     * @throws GeneralException If there was a problem retrieving data set
     */
    private void initialize() throws GeneralException {
        QueryOptions options = new QueryOptions( filters.toArray( new Filter[ 0 ] ) );
        options.setFilters( filters );
        SailPointContext context = getContext();
        /* Get Count of objects for progress updates */
        setObjectCount(context.countObjects(Identity.class, options));
        /* Get projection for speedy queries */
        List<String> propertyStrings = getPropertyStrings( properties );
        ArrayList<Ordering> orderings = getOrderings( properties );
        options.setOrderings( orderings );
        options.setDistinct( true );
        resultIterator = context.search( Identity.class, options, propertyStrings );
        /* Rest result ticker */
        resultTicker = 0;
        /* initialization successful */
        initialized = true;
    }

    /**
     * Creates orderings list from properties 
     * 
     * @return List of orderings based on properties
     */
    private ArrayList<Ordering> getOrderings( List<IdentityProperty> properties ) {
        ArrayList<Ordering> orderings = new ArrayList<Ordering>( properties.size() );
        for( IdentityProperty property : properties ) {
            orderings.add( new Ordering( property.getPropertyName(), true ) );
        }
        return orderings;
    }
    
    /**
     * Returns a string representation of the PropertyList for use with 
     * PersistenceContext.search( Class, QueryOptions, List<String> )
     * 
     * @param properties List of properties to get Strings for
     * @return List of Strings representing properties
     */
    private List<String> getPropertyStrings( final List<IdentityProperty> properties ) {
        List<String> response = new ArrayList<String>( properties.size() );
        for( IdentityProperty property : properties ) {
            response.add( property.getPropertyName() );
        }
        return response;
    }

    /**
     * Creates a map of key/value pairs for propertyName, propertyValue 
     * 
     * @param resultEntry Array of values from the projection
     * @param properties List of properties the values represent 
     * @return Map of key/value pairs for propertyName, propertyValue
     */
    private Map<String, Object> createResultMap( final Object[] resultEntry, final List<IdentityProperty> properties ) {
        /* Ensure that we got the number we expected */
        if( resultEntry.length != properties.size() ) {
            throw new IllegalArgumentException( "Unable to get next row: Resulting number of items in row and expected number of items fo not match" );
        }
        int length = resultEntry.length;
        Map<String, Object> response = new HashMap<String, Object>();
        /* Iterate through the properties and results to create key/value pairs*/
        for( int i = 0; i < length; i++ ) {
            response.put( properties.get( i ).getPropertyName(), resultEntry[ i ] );
        }
        return response;
    }
    
    private boolean initialized;
    private Iterator<Object[]> resultIterator;
    private Map<String, Object> resultMap;
    private int resultTicker;
   
    private final List<IdentityProperty> properties;
    private final List<Filter> filters;
    
    private static final int MAX_RESULTS_BEFORE_DECACHE = 500;
}
