/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.ApplicationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class ApplicationUserReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ApplicationUserReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String DETAIL_REPORT = "ApplicationUserMainReport";

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
        args.put("isCsv", isCsv());
        Message title = new Message(MessageKeys.REPT_APP_USER_REPORT_TITLE);
        args.put("title", title.getLocalizedMessage());
        return new ApplicationDataSource(buildFilters(args), getLocale(), getTimeZone());
    }
    
    @Override
    public boolean isRerunCSVOnExport() {
        return true;
    }
}
