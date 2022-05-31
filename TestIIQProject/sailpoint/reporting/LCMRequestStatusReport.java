/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.*;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.reporting.datasource.LCMRequestStatusDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.*;

import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.JRException;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class LCMRequestStatusReport extends JasperExecutor {
    private static final Log log = LogFactory.getLog(LCMRequestStatusReport.class);

    public static String ARG_COLS = "columns";

    public static String ARG_APPS = "applications";
    public static String ARG_AUDIT_EVENT_TYPES = "auditEventTypes";
    public static String ARG_APPROVER = "approver";
    public static String ARG_REQUESTOR = "requestor";
    public static String ARG_ENTITLEMENTS = "entitlements";
    public static String ARG_ROLES = "roles";
    public static String ARG_STATUS = "status";
    public static String ARG_CAUSE = "cause";
    public static final String ARG_TARGET_IDENTITY = "targetIdentity";

    public static String ARG_STATUS_PENDING = "pending";
    public static String ARG_STATUS_APPROVED = "approved";
    public static String ARG_STATUS_REJECTED = "rejected";
    public static String ARG_STATUS_CANCELLED = "cancelled";
    public static String ARG_STATUS_COMPLETED = "completed";
    public static String ARG_STATUS_FAILED = "failed";

    public static final String ATTRIBUTE_COMPLETED = "completionDate";
    public static final String ATTRIBUTE_CREATED = "creationDate";

    public static final String START = "Start";
    public static final String END = "End";

    @Override
    protected TopLevelDataSource getDataSource() throws GeneralException {
        Attributes<String, Object> attributes = getInputs();

        LCMRequestStatusDataSource ds = new LCMRequestStatusDataSource(getLocale(), getTimeZone());

        ds.setTypes(attributes.getList(ARG_AUDIT_EVENT_TYPES));
        
        List<?> tmpCauses = ( List<?> )attributes.getList( ARG_CAUSE );
        if( tmpCauses != null && !tmpCauses.isEmpty() ) {
            /* JSF has kindly returned a List with a single csv of the values we actually want */
            List<String> causes = Util.csvToList( tmpCauses.get( 0 ).toString() );
            ds.setTypes( causes );
        }
        
        if( ds.getTypes() == null || ds.getTypes().isEmpty() ) {
            Message message = new Message( MessageKeys.REPT_PASSWORD_MANAGEMENT_MUST_DEFINE_CAUSE );
            throw new GeneralException( message.getLocalizedMessage( _locale, null ) );
        }

        ds.setRequestDateStart(attributes.getDate(ATTRIBUTE_CREATED + START));
        ds.setRequestDateEnd(attributes.getDate(ATTRIBUTE_CREATED + END));

        ds.setCompletionDateStart(attributes.getDate(ATTRIBUTE_COMPLETED + START));
        ds.setCompletionDateEnd(attributes.getDate(ATTRIBUTE_COMPLETED + END));

        if (attributes.containsKey(ARG_APPS)) {
            List<String> apps = attributes.getStringList(ARG_APPS);
            if (apps != null && !apps.isEmpty()) {
                List<String> requestorNames =
                        ReportParameterUtil.convertIdsToNames(getContext(), Application.class, apps);
                ds.setApplications(requestorNames);
            }
        }

        if (attributes.containsKey(ARG_REQUESTOR)) {
            List<String> requestors = attributes.getStringList(ARG_REQUESTOR);
            if (requestors != null && !requestors.isEmpty()) {
                List<String> requestorNames =
                        ReportParameterUtil.convertIdsToNames(getContext(), Identity.class, requestors);
                ds.setRequestors(requestorNames);
            }
        }

        if (attributes.containsKey(ARG_APPROVER)) {
            List<String> approvers = attributes.getStringList(ARG_APPROVER);
            if (approvers != null && !approvers.isEmpty()) {
                List<String> approverNames =
                        ReportParameterUtil.convertIdsToNames(getContext(), Identity.class, approvers);
                ds.setApprovers(approverNames);
            }
        }

        if (attributes.containsKey(ARG_ROLES)) {
            List<String> roles = attributes.getStringList(ARG_ROLES);
            if (roles != null && !roles.isEmpty()) {
                List<String> roleNames =
                        ReportParameterUtil.convertIdsToNames(getContext(), Bundle.class, roles);
                ds.setRoleNames(roleNames);
            }
        }

        if (attributes.containsKey(ARG_ENTITLEMENTS)) {
            List entitlements = attributes.getList(ARG_ENTITLEMENTS);
            if (entitlements != null && !entitlements.isEmpty()) {
                List<IdentityItem> items = new ArrayList<IdentityItem>();
                for (Object ent : entitlements) {
                    items.add((IdentityItem) ent);
                }
                ds.setEntitlements(items);
            }
        }

        if (attributes.containsKey(ARG_TARGET_IDENTITY)) {
            ds.setTargetIdentities(attributes.getStringList(ARG_TARGET_IDENTITY));
        }

        if (attributes.containsKey(ARG_TARGET_IDENTITY)) {
            List<String> targetIds = attributes.getStringList(ARG_TARGET_IDENTITY);
            if (targetIds != null && !targetIds.isEmpty()) {
                List<String> identityNames = ReportParameterUtil.convertIdsToNames(getContext(), Identity.class, targetIds);
                ds.setTargetIdentities(identityNames);
            }
        }

        ds.setStatus(attributes.getString(ARG_STATUS));

        return ds;
    }

    @Override
    public JasperDesign updateDesign(JasperDesign design)
            throws GeneralException {

        Attributes<String, Object> inputs = getInputs();

        List<ReportColumnConfig> cols = (List<ReportColumnConfig>)inputs.getList(ARG_COLS);
        if (cols == null)
            return design;

        DynamicColumnReport rept = new DynamicColumnReport(design);

        rept.setDetailStyle("bandedText");
        rept.setHeaderStyle("spBlue");
        
        for(ReportColumnConfig col : cols){
            col.setHeader(Message.info(col.getHeader()).getLocalizedMessage(getLocale(), getTimeZone()));
            rept.addColumn(col);
        }

        try {
            return rept.getDesign();
        } catch (JRException e) {
            throw new GeneralException(e);
        }
    }


}