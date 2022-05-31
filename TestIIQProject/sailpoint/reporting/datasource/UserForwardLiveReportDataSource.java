/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LocalizedDate;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class UserForwardLiveReportDataSource extends ProjectionDataSource implements JavaDataSource {

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {
        super.setTimezone((TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);
        init(Identity.class, ops, report.getGridColumns(), getLocale(), getTimezone());
    }

    @Override
    public boolean next() throws JRException {

        Object forwardPref = null;

        while(super.next()){
             try {
                forwardPref = getFieldValue("forwardingUser");
                 if (forwardPref != null){
                    break;
                 }
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        return forwardPref != null;
    }

    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {

        Object value = null;
        if(fieldName.equals("forwardingUser")) {
            value = getPreference(Identity.PRF_FORWARD);
        } else if(fieldName.equals("forwardingDisplayName")) {
            String forwardUserName = (String)getPreference(Identity.PRF_FORWARD);
            if (forwardUserName != null){
                getContext().search(Identity.class, new QueryOptions(Filter.eq("name", forwardUserName)),
                        Arrays.asList("displayName"));
                Identity forwardingUser = getContext().getObjectByName(Identity.class, forwardUserName);
                if (forwardingUser != null)
                    value = forwardingUser.getDisplayableName();
            }
        } else if(fieldName.equals("startDate")) {
            LocalizedDate start = new LocalizedDate((Date)getPreference(Identity.PRF_FORWARD_START_DATE), DateFormat.SHORT, null);
            value = start.getLocalizedMessage();
        } else if(fieldName.equals("endDate")) {
            LocalizedDate end = new LocalizedDate((Date)getPreference(Identity.PRF_FORWARD_END_DATE), DateFormat.SHORT, null);
            value = end.getLocalizedMessage();
        } else {
            value = super.getFieldValue(fieldName);
        }

        return value;
    }

    private Object getPreference(String name) throws GeneralException{

        Map<String, Object> prefs = (Map<String, Object>)super.getFieldValue("forwardingUser");
        if (prefs != null){
            return prefs.get(name);
        }

        return null;
    }

    public void setLimit(int startPage, int pageSize) {

    }
}
