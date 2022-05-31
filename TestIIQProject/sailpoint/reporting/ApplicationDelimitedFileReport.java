/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.ApplicationDelimitedDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class ApplicationDelimitedFileReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ApplicationDelimitedFileReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String GRID_REPORT = "ApplicationDelimitedFileGridReport";

    @Override
    public String getJasperClass() {
        String className = GRID_REPORT;
        //if ( showDetailed() ) {
        //    className = DETAIL_REPORT;
        //}
        return className;
    }
   
    public List<Filter> buildFilters(Attributes<String,Object> inputs) {
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, "applications", "id", null);
        
        if(inputs.get("owners") !=null) {
            Object owners =inputs.get("owners");
            if(owners instanceof String && (owners.toString().indexOf(",")>=0)) {
                List<String> list = Util.csvToList(owners.toString());
                List<Filter> ownerFilters = new ArrayList<Filter>();
                List<Filter> secondaryOwnerFilters = new ArrayList<Filter>();
                for(String owner : list) {
                    ownerFilters.add(Filter.eq("owner.id", owner));
                    secondaryOwnerFilters.add(Filter.eq("secondaryOwners.id", owner));
                }
                Filter ownerOr = Filter.or(ownerFilters);
                Filter secondaryOwnerOr = Filter.or(secondaryOwnerFilters);
                
                filters.add(Filter.or(ownerOr, secondaryOwnerOr));
            } else {
                filters.add(Filter.or(Filter.eq("owner.id", owners),Filter.eq("secondaryOwners.id", owners)));
            }
        }
        
        //"Delimited File Parsing Connector" is kept as a possible type for backward compatibility
        filters.add(Filter.or(
                Filter.eq("type", "Delimited File Parsing Connector"), 
                Filter.eq("type", "DelimitedFile")));
        
        return filters;
    }

    public TopLevelDataSource getDataSource()
        throws GeneralException {

        Attributes<String,Object> args = getInputs();
        return new ApplicationDelimitedDataSource(buildFilters(args), getLocale(), getTimeZone());
    }
}
