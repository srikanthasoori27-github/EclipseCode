package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.IdentityProjectionDataSource;
import sailpoint.reporting.datasource.IdentityProperty;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;

/**
 * Class for generating the Identity Cube Summary Report
 * 
 * @author justin.williams
 */
public class IdentityStatusSummaryReport extends JasperExecutor {

    public IdentityStatusSummaryReport() {
        /* Simple filters to build more complex filters with */
        Filter noWorkGroupsFilter = Filter.eq( Identity.ATT_WORKGROUP_FLAG, false );
        Filter activeFilter = Filter.eq( Identity.ATT_INACTIVE, false );
        Filter inactiveFilter = Filter.eq( Identity.ATT_INACTIVE, true );
        /* Build filters for getting summary information */
        activeIdentitiesFilter = Filter.and( noWorkGroupsFilter, activeFilter );
        inactiveIdentitiesFilter = Filter.and( noWorkGroupsFilter, inactiveFilter );
    }
    
    /**
     * Returns a DataSource that conatins all Identity Cubes that are not
     * workgroups
     * 
     * @throws GeneralException If there is a problem generating the data source
     */
    @Override
    protected TopLevelDataSource getDataSource() throws GeneralException {
        TopLevelDataSource response;
        if( isSummaryOnly ) {
            /* return an empty datasource */
            response = new TopLevelDataSource() {
                private Monitor monitor;

                public boolean next() throws JRException {
                    return false;
                }
                
                public Object getFieldValue( JRField arg0 ) throws JRException {
                    return null;
                }
                
                public void setMonitor( Monitor monitor ) {
                    this.monitor = monitor;
                }
                
                public void close() {
                    monitor.completed();
                }
            };
        } else {
            /* All Identities that are not workgroups */
            List<Filter> filters = new ArrayList<Filter>( 1 );
            filters.add( Filter.eq( Identity.ATT_WORKGROUP_FLAG, false ) );
            List<IdentityProperty> properties = new ArrayList<IdentityProperty>( 2 );
            properties.add( IdentityProperty.INACTIVE );
            properties.add( IdentityProperty.NAME );
            response = new IdentityProjectionDataSource( filters, properties, getLocale(), getTimeZone() );
        }
        return response;
    }

    /**
     * Populates the parameter set with summary information
     * 
     * @param ctx The context to retrieve values from
     * @param args Map to populate with report parameters
     * @param report The report to fill
     */
    @Override
    public void preFill( SailPointContext ctx, Attributes<String, Object> args, JasperReport report ) throws GeneralException {
        super.preFill( ctx, args, report );
        
        Boolean isSummaryOnly = args.getBooleanObject( "summaryOnly" );
        if( isSummaryOnly != null && isSummaryOnly ) {
            this.isSummaryOnly = true;
        }

        int numberOfActiveIdentities = getNumberOfActiveIdentities( ctx );
        int numberOfInactiveIdentities = getNumberOfInactiveIdentities( ctx );
        /* Populate parameter map with counts */
        args.put( ACTIVE_IDENTITIES_COUNT_PARAM, Integer.toString( numberOfActiveIdentities ) );
        args.put( INACTIVE_IDENTITIES_COUNT_PARAM, Integer.toString( numberOfInactiveIdentities ) );
        args.put( TOTAL_IDENTITIES_COUNT_PARAM, Integer.toString( numberOfInactiveIdentities  + numberOfActiveIdentities ) );
    }

    /**
     * Retrieves the number of inactive Identity Cubes from the context 
     * @param context The context to get inactive Identity Cubes from
     * @return The number of inactive Identity Cubes in the context
     * @throws GeneralException If the query to retrieve Identity Cubes from the context fails
     */
    private int getNumberOfInactiveIdentities( SailPointContext context ) throws GeneralException {
        QueryOptions qo = new QueryOptions( inactiveIdentitiesFilter );
        int numberOfInactiveIdentities = context.countObjects( Identity.class, qo );
        return numberOfInactiveIdentities;
    }

    /**
     * Retrieves the number of active Identity Cubes from the context 
     * @param context The context to get active Identity Cubes from
     * @return The number of active Identity Cubes in the context
     * @throws GeneralException If the query to retrieve Identity Cubes from the context fails
     */
    private int getNumberOfActiveIdentities( SailPointContext context ) throws GeneralException {
        QueryOptions qo = new QueryOptions( activeIdentitiesFilter );
        int numberOfActiveIdentities = context.countObjects( Identity.class, qo );
        return numberOfActiveIdentities;
    }
    
    private final Filter activeIdentitiesFilter;
    private final Filter inactiveIdentitiesFilter;
    private boolean isSummaryOnly = false;
    /* Constants */
    private static final String ACTIVE_IDENTITIES_COUNT_PARAM = "activeIdentitiesCount";
    private static final String INACTIVE_IDENTITIES_COUNT_PARAM = "inactiveIdentitiesCount";
    private static final String TOTAL_IDENTITIES_COUNT_PARAM = "totalIdentitiesCount";
}
