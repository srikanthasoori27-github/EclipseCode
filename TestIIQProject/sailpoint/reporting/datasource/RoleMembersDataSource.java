/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.reporting.ReportingLibrary;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class RoleMembersDataSource extends ProjectionDataSource implements JavaDataSource{

    private static final Log log = LogFactory.getLog(RoleMembersDataSource.class);

    private Iterator<Object[]> memberIterator;
    private Map<String, Object> currentRecord;
    private String currentRole;
    private String currentType;
    private String currentStatus;
    private Boolean hasMembersFilter;

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {
        super.setTimezone((TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);

        String arg = (String)arguments.get("showEmpty");
        if (arg != null && !"null".equals(arg)){
            hasMembersFilter = Util.otob(arg);
        }

        init(Bundle.class, ops, report.getGridColumns(), getLocale(), getTimezone());
    }

    @Override
    public boolean next() throws JRException {

        currentRecord = null;

        // If we are showing members, check to see if there's another member
        // queued up
        if ((hasMembersFilter == null || hasMembersFilter) && currentRole != null && processNextMember()){
            return true;
        }

        boolean foundNext = false;
        while(!foundNext && super.next()){
            try {
                currentRole = (String)super.getFieldValue("name");

                currentRecord = new HashMap<String, Object>() ;
                currentRecord.put("name", currentRole);
                
                boolean isDisabled = Util.otob(super.getFieldValue("status"));
                currentStatus = new Message(isDisabled ? MessageKeys.DISABLED : MessageKeys.ENABLED).getLocalizedMessage(getLocale(), getTimezone());
                currentRecord.put("status", currentStatus);
                
                currentType = ReportingLibrary.getLocalizedRoleTypeDisplayName(Util.otos(super.getFieldValue("type")), getLocale(), getTimezone());
                currentRecord.put("type", currentType);

                // Check to see if the role has members.
                boolean hasMembers = fetchMembers();
                if (hasMembersFilter == null){
                    foundNext = true;
                    processNextMember();
                } else if (hasMembers && hasMembersFilter){
                    // if the user only wants to see roles with members, or if
                    // they don't care, show the record regardless of whether it has members
                    foundNext = true;
                    processNextMember();
                } else if (!hasMembers && !hasMembersFilter){
                    // If the user only wants to see empty roles (!hasMembersFilter)
                    // dont show the record if no members were found
                    foundNext = true;
                }

            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        return foundNext;
    }


    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {
        return currentRecord != null ? currentRecord.get(fieldName) : null;
    }

    public void setLimit(int startPage, int pageSize) {
        // not supported
    }

    private boolean fetchMembers() throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("value", currentRole));
        ops.add(Filter.or(Filter.eq("name", "detectedRoles"), Filter.eq("name", "assignedRoles")));

        memberIterator = getContext().search(IdentityEntitlement.class, ops, Arrays.asList("identity.firstname", "identity.lastname",
                "identity.name"));

        return memberIterator.hasNext();
    }

    private boolean processNextMember(){
        if (memberIterator != null && memberIterator.hasNext()){
            Object[] row = memberIterator.next();
            if (currentRecord == null)
                currentRecord = new HashMap<String, Object>() ;
            currentRecord.put("name", currentRole);
            currentRecord.put("firstname", row[0]);
            currentRecord.put("lastname", row[1]);
            currentRecord.put("identity", row[2]);
            currentRecord.put("type", currentType);
            currentRecord.put("status", currentStatus);
            return true;
        }

        return false;
    }

}