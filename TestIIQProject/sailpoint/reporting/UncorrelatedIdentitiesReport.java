/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.reporting.datasource.UncorrelatedIdentityDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 */
public class UncorrelatedIdentitiesReport extends JasperExecutor {

	private static final String GRID_REPORT = "UncorrelatedIdentitiesGridReport";
	private static final String DETAIL_REPORT = "UncorrelatedIdentitiesDetailReport";
	public static final String APP_SUB_REPORT = "UncorrelatedIdentitiesApplicationReport";

	public static final String ATT_AUTHORITATIVE_APP_NAMES = "authoritativeAppNames";
	public static final String ATT_AUTHORITATIVE_APP_IDS = "authoritativeAppIds";
	public static final String ATT_CORRELATED_APPS = "correlatedApp";
	public static final String ATT_APPLICATION_TOTALS = "applicationTotals";
	public static final String ATT_REPORT_TYPE = "uncorrelatedIdentitiesReportType";

	private static Log log = LogFactory.getLog(UncorrelatedIdentitiesReport.class);

	public UncorrelatedIdentitiesReport() {
	}

	/* (non-Javadoc)
	 * @see sailpoint.reporting.JasperExecutor#buildFilters(sailpoint.object.Attributes)
	 */
	protected List<Filter> buildFilters(Attributes<String, Object> inputs) {
		List<Filter> filters = new ArrayList<Filter>();
		addEQFilter(filters, inputs, "correlatedApps", "id", null);
		return filters;
	}

	public TopLevelDataSource getDataSource()
	throws GeneralException {

		Attributes<String,Object> args = getInputs();
		List<Filter> filters = buildFilters(args);
		//Load an application as a test as the authoritative App
		return new UncorrelatedIdentityDataSource(filters, getLocale(), getTimeZone(), args);
	}

	@Override
	public void preFill(SailPointContext ctx, Attributes<String, Object> args, JasperReport report)

	throws GeneralException {
        super.preFill(ctx, args, report);
		//if there are application ids chosen, we need to get their names and create a list
		args.put(ATT_AUTHORITATIVE_APP_NAMES, getAuthoritativeAppNames(ctx));
	}

	public String getAuthoritativeAppNames(SailPointContext ctx) throws GeneralException{
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("authoritative", true));
		List<Application> apps = ctx.getObjects(Application.class, qo);
		if(apps!=null) {
			List<String> appNames = new ArrayList<String>();
			for(Application app : apps) {
				appNames.add(app.getName());
			}
			return Util.listToCsv(appNames);
		}
		return "";
	}

	/* (non-Javadoc)
	 * @see sailpoint.reporting.JasperExecutor#getJasperClass(sailpoint.object.Attributes, boolean)
	 */
	@Override
	public String getJasperClass() {
		String className = GRID_REPORT;
		if ( showDetailed() ) {
			className = DETAIL_REPORT;
		}
		return className;
	}
    
    @Override
    public boolean isRerunCSVOnExport() {
        return true;
    }
}
