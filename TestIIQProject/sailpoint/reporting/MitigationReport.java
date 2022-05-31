/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.MitigationExpirationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class MitigationReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(MitigationReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String DETAIL_REPORT = "MitigationMainReport";
    private static final String GRID_REPORT = "MitigationGridReport";

    @Override
    public String getJasperClass() {
        String className = GRID_REPORT;
        if ( showDetailed() ) {
            className = DETAIL_REPORT;
        }
        return className;
    }
    
    public List<Filter> buildFilters(Attributes<String,Object> inputs) {
		List<Filter> filters = new ArrayList<Filter>();
		addEQFilter(filters, inputs, "businessRoles", "businessRole.id", null);
		addEQFilter(filters, inputs, "identities", "identity.id", null);
		addEQFilter(filters, inputs, "managers", "mitigator.id", null);
		addDateTypeFilter(filters, inputs, "expriationDate", "expirationDateType", "expiration", null);
		return filters;
	}
    

    public TopLevelDataSource getDataSource()
	throws GeneralException {
		Attributes<String,Object> args = getInputs();
		return new MitigationExpirationDataSource(buildFilters(args), getLocale(), getTimeZone());
	}
}
