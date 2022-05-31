/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.ApplicationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class ApplicationAccountsReport extends JasperExecutor {

	//////////////////////////////////////////////////////////////////////
	//
	// Fields
	//
	//////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(ApplicationAccountsReport.class);
	protected static final String IDENTITY_ALIAS = "identityAlias";
	protected static final String LINK_ALIAS = "identity_linksAlias0";
	protected static final String LINK_ALIAS2 = "links2";
	
	private static final String IDENTITY_MULTIPLE_ACCOUNT_COUNT_QUERY = 
		"select count(distinct "+IDENTITY_ALIAS+".id) " +
		"from " +
		"       sailpoint.object.Identity "+IDENTITY_ALIAS +
		"       where " +
		"               (select count(*) from sailpoint.object.Link "+LINK_ALIAS2+
		"                   where "+LINK_ALIAS2+".identity.id = "+IDENTITY_ALIAS+".id " +
		"                   and "+LINK_ALIAS2+".application.id = (:applicationId)) > 1";
	

	private static final String DETAIL_REPORT = "ApplicationAccountsMainReport";

        @Override
	public String getJasperClass() {
	    return DETAIL_REPORT;
	}

	public List<Filter> buildFilters(Attributes<String,Object> inputs) {
		List<Filter> filters = new ArrayList<Filter>();
		addEQFilter(filters, inputs, "applications", "id", null);
		return filters;
	}

        public TopLevelDataSource getDataSource() 
            throws GeneralException {

            Attributes<String,Object> args = getInputs();
	    List<Filter> filters = buildFilters(args);
	    return new ApplicationDataSource(filters, getLocale(), getTimeZone());
        }

	@Override
	public void preFill(SailPointContext ctx, 
                            Attributes<String, Object> args, 
                            JasperReport report)
	    throws GeneralException {
        Message title = new Message(MessageKeys.REPT_APP_ACCOUNTS_TITLE);
        args.put("title", title.getLocalizedMessage());
        super.preFill(ctx, args, report);
	    List<Filter> filters = buildFilters(args);
	    buildSummary(ctx, filters, args);
	}    

	/** Builds a summary of the link statistics for each application **/
	private void buildSummary(SailPointContext ctx, List<Filter> filters, Attributes<String,Object> args) 
		throws GeneralException {
		List<Map<String,Object>> summary = new ArrayList<Map<String, Object>>();

		QueryOptions qo = new QueryOptions();
		if(filters!=null && !filters.isEmpty()){
			for(Filter filter : filters) 
				qo.add(filter);
		}

		//Sort by name
		qo.setOrderBy("name");
		Iterator<Application> apps = ctx.search(Application.class, qo);
		while(apps.hasNext()) {
			Map<String, Object> applicationSummary = new HashMap<String, Object>();

			Application app = apps.next();
			
			

			/** Count the links to this app **/
			QueryOptions ops = new QueryOptions();
			ops.add(Filter.eq("application.id", app.getId()));
			int linkCount = ctx.countObjects(Link.class, ops);
			
			/** Count the identities linked to this app **/
			ops = new QueryOptions();
			ops.add(Filter.eq("links.application.id", app.getId()));
			ops.setDistinct(true);
			int singleLinksCount = ctx.countObjects(Identity.class, ops);
			
			/** Count the identities with multiple links to this app **/
			Map<String,Object> queryArgs = new HashMap<String,Object>();
			queryArgs.put("applicationId", app.getId());
			
			int multipleLinksCount = 0;
			Iterator it = ctx.search(IDENTITY_MULTIPLE_ACCOUNT_COUNT_QUERY, queryArgs, null);
			while (it.hasNext()) {
				Object o = it.next();
				if(o!=null) {
					multipleLinksCount = ((Long)o).intValue();
				}
			}
			
			applicationSummary.put("appName", app.getName());
			applicationSummary.put("linkCount", linkCount);
			applicationSummary.put("singleLinksCount", singleLinksCount);
			applicationSummary.put("multipleLinksCount", multipleLinksCount);
			
			summary.add(applicationSummary);
		}
		args.put("applicationLinksSummary", summary);
	}
}
