/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.IdentityApplicationRiskDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class IdentityApplicationRiskReport extends UncorrelatedIdentitiesReport {

	public static final String GRID_REPORT = "IdentityApplicationRiskGridReport";
	public static final String DETAIL_REPORT = "IdentityApplicationRiskDetailReport";
	public static final String GRID_SUB_REPORT = "IdentityApplicationRiskGridSubReport";

	private static Log log = LogFactory.getLog(IdentityApplicationRiskReport.class);

	public IdentityApplicationRiskReport() {
		super();
	}

	public TopLevelDataSource getDataSource()
	throws GeneralException {

		Attributes<String,Object> args = getInputs();
		List<Filter> filters = buildFilters(args);
		//Load an application as a test as the authoritative App
		return new IdentityApplicationRiskDataSource(filters, getLocale(), getTimeZone(), args);
	}

	@Override
	public String getJasperClass() {
            String className = GRID_REPORT;
	    if ( showDetailed() ) {
                className = DETAIL_REPORT;
	    }
	    return className;
	}
}
