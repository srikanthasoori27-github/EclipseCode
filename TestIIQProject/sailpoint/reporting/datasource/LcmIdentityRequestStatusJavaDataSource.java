/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportParameterUtil;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

public class LcmIdentityRequestStatusJavaDataSource implements JavaDataSource {

    private LCMIdentityRequestStatusDataSource dataSource;
    private SailPointContext context;

    @SuppressWarnings( "unchecked" )
    public void initialize( SailPointContext context, LiveReport report,
                            Attributes<String, Object> arguments, String groupBy, List<Sort> sorts ) throws GeneralException {
        this.context = context;
        Locale locale = ( Locale ) arguments.get( JRParameter.REPORT_LOCALE );
        TimeZone timeZone = ( TimeZone ) arguments.get( JRParameter.REPORT_TIME_ZONE );
        dataSource = new LCMIdentityRequestStatusDataSource( locale, timeZone );
        List<String> approverArgs = (List<String>) arguments.get( "approvers" );
        List<String> approvers = new ArrayList<String>();
        if(approverArgs != null) {
            for(String approverId : approverArgs){
                Identity approver = context.getObjectById(Identity.class, approverId);
                approvers.add(approver.getName());
            }
        }
        dataSource.setApprovers( approvers );
        dataSource.setRequestors( ( List<String> ) arguments.get( "requestors" ) );
        dataSource.setTargetIdentities( ( List<String> ) arguments.get( "targetIdentities" ) );
        List<String> applicationNames = getApplicationNames( ( List<String> ) arguments.get( "applications" ) );
        dataSource.setApplications( applicationNames );
        dataSource.setStatus( ( String ) arguments.get( "status" ) );
        Map<String, Long> requestDateRange = ( Map<String, Long> ) arguments.get( "requestDateRange" );
        if( requestDateRange != null ) {
            Map<String, Date> dateRange = getDateMap(requestDateRange);
            dataSource.setRequestDateStart(dateRange.get("start"));
            dataSource.setRequestDateEnd(dateRange.get("end"));
        }
        if( hasType(arguments) ) {
            dataSource.setTypes( arguments.getList( "auditEventTypes" ) );
        } else {
            // Password Management Report Uses Cause not auditEventTypes
            List<String> causes = ( List<String> ) arguments.getList( "cause" );
            // If there is no cause set then add all the password IdentityRequest types
            if(causes == null || causes.isEmpty()) {
                causes = new ArrayList<String>();
                causes.add("ExpiredPassword");
                causes.add("ForgotPassword");
                causes.add("PasswordsRequest");
            }
            dataSource.setTypes(causes);
        }
        if( hasEntitlements( arguments ) ) {
            List<String> entitlementJsons = (List<String>) arguments.getList( "entitlements" );
            List<IdentityItem> entitlements = new ArrayList<IdentityItem>( entitlementJsons.size() );
            for( String entitlementJson : entitlementJsons ) {
                Map<String, String> deserialize = deserializeEntitlementMap( entitlementJson );
                IdentityItem entitlement = createEntitlementParameter( deserialize );
                entitlements.add( entitlement );
            }
            dataSource.setEntitlements( entitlements );
        }

        Map<String, Long> completionDateRange = ( Map<String, Long> ) arguments.get( "completionDateRange" );
        if( completionDateRange != null ) {
            Map<String, Date> dateRange = getDateMap(completionDateRange);
            dataSource.setCompletionDateStart(dateRange.get("start"));
            dataSource.setCompletionDateEnd(dateRange.get("end"));
        }
        List<String> roleNames = getRoleNames( ( List<String> )arguments.get( "roles" ) );
        dataSource.setRoleNames( roleNames );
        if( sorts != null ) {
            for( Sort sort : sorts ) {
                dataSource.addOrdering( sort );
            }
        }
        dataSource.setGroupBy( groupBy );
    }

    private Map<String, Date> getDateMap(Map<String, Long> requestDateRange) {
        Map <String, Date> dateRange = new HashMap<String, Date>();
        Long startLong = requestDateRange.get( "start" );
        if( startLong != null ) {
            dateRange.put("start", new Date(startLong));
        }
        Long endLong = requestDateRange.get( "end" );
        if( startLong != null ) {
            if(endLong == null) {
                dateRange.put("end", new Date());
            } else {
                dateRange.put("end", new Date(endLong));
            }
        }
        return dateRange;
    }

    /**
     * Identity Requests are differentiated by type.  Report arguments set auditEventTypes
     * without user intervention except for the Password Status report which uses a user
     * editable cause property.
     *
     * @param arguments Argument map
     * @return true if arguments has a non-empty type property
     */
    private boolean hasType( Attributes<String, Object> arguments ) {
        List<?> types = arguments.getList( "auditEventTypes" );
        return !(types == null || types.isEmpty());
    }

    private IdentityItem createEntitlementParameter(
            Map<String, String> deserialize ) {
        IdentityItem entitlement = new IdentityItem();
        entitlement.setApplication( deserialize.get( "app" ) );
        entitlement.setName( deserialize.get( "attr" ) );
        entitlement.setValue( deserialize.get( "attrVal" ) );
        return entitlement;
    }

    private Map<String, String> deserializeEntitlementMap( String entitlementJson ) throws GeneralException {
        return JsonHelper.mapFromJson(String.class, String.class, entitlementJson);
    }

    private boolean hasEntitlements( Attributes<String, Object> arguments ) {
        List<String> entitlementJsons = (List<String>) arguments.getList( "entitlements" );
        return entitlementJsons != null && !entitlementJsons.isEmpty();
    }

    private List<String> getRoleNames( List<String> roleIds ) throws GeneralException {
        return ReportParameterUtil.convertIdsToNames( context, Bundle.class, roleIds );
    }

    private List<String> getApplicationNames( List<String> applicationIds ) throws GeneralException {
        return ReportParameterUtil.convertIdsToNames( context, Application.class, applicationIds );
    }

    public void setLimit( int startPage, int pageSize ) {
        dataSource.setLimit( startPage, pageSize );
    }

    public Object getFieldValue( String field ) throws GeneralException {
        return dataSource.getValueForField( field );
    }

    public int getSizeEstimate() throws GeneralException {
        return dataSource.getObjectCount();
    }

    public void close() {
        dataSource.close();
    }

    public void setMonitor( Monitor monitor ) {
        dataSource.setMonitor( monitor );
    }

    public Object getFieldValue( JRField jrField ) throws JRException {
        return dataSource.getFieldValue( jrField );
    }

    public boolean next() throws JRException {
        return dataSource.next();
    }

    public QueryOptions getBaseQueryOptions() {
        return dataSource.getBaseQueryOptions();
    }

    public String getBaseHql() {
        return null;
    }
}
