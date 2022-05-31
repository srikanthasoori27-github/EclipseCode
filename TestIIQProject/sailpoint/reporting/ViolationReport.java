/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.PolicyViolationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class ViolationReport extends JasperExecutor {
    private static Log log = LogFactory.getLog(ViolationReport.class);
    private static final String DETAIL_REPORT = "ViolationMainReport";
    private static final String GRID_REPORT = "ViolationGridReport";
  
    @Override
    public String getJasperClass() {
        String className = GRID_REPORT;
        if ( showDetailed() ) {
            className = DETAIL_REPORT;
        }
        return className;
    }
    
    protected List<Filter> buildFilters(Attributes<String,Object> inputs) {
        
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, "identities", "identity.id", null);
        addEQFilter(filters, inputs, "policies", "policyId", null);
        
        /**Special treatment for businessRoles since it can be in either left or right bundles.
         * Need to make one big OR statement **/
        String value = inputs.getString("businessRoles");
        if ( value != null ) {
            List<Filter> bundleFilters = new ArrayList<Filter>();
            addLikeFilter(bundleFilters, inputs, "businessRoles", "leftBundles", null);
            addLikeFilter(bundleFilters, inputs, "businessRoles", "rightBundles", null);
            
            filters.add(Filter.or(bundleFilters));
        }
        inputs.put("violationDateType", "Before");
        addDateTypeFilter(filters, inputs, "violationDate", "violationDateType", "created", null);

        if (null == inputs.getString("status")){
            filters.add(Filter.eq("active", true));
        } else if ("completed".equals(inputs.getString("status"))) {
            filters.add(Filter.eq("active", false));     
        }

        return filters;
        
    }

    public TopLevelDataSource getDataSource()
        throws GeneralException {
   
        Attributes<String,Object> args = getInputs();
        List<Filter> filters = buildFilters(args);
        return new PolicyViolationDataSource(filters, getLocale(), getTimeZone());
    }
}
