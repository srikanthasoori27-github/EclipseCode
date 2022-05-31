/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.List;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.base.JRBaseTextField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ObjectConfig;
import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.BusinessRoleCompositionCertificationReportDataSource;
import sailpoint.reporting.datasource.BusinessRoleMembershipCertificationReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BusinessRoleCertificationReport extends BaseCertificationReport {

     // list of roles to query, value is a csv string of IDs
    private static final String ATTRIBUTE_ROLES = "businessRoles";

    private static final String ATTRIBUTE_CERT_TYPE = "certificationType";

    
    protected BaseCertificationDataSource internalCreateDataSource()
        throws GeneralException {

        Attributes<String,Object> args = getInputs();

        List<String> roleIds =  ReportParameterUtil.splitAttributeValue(args.getString(ATTRIBUTE_ROLES));

         // todo move cert type name to static or compare to enum
        BaseCertificationDataSource datasource = null;
        if ("roleMembership".equals(args.getString(ATTRIBUTE_CERT_TYPE))){
            datasource = new BusinessRoleMembershipCertificationReportDataSource(roleIds, _locale, _timezone);
        }else if ("roleComposition".equals(args.getString(ATTRIBUTE_CERT_TYPE))) {
            datasource = new BusinessRoleCompositionCertificationReportDataSource(roleIds, _locale, _timezone);
        }else{
            Message message = new Message(MessageKeys.REPORT_ERROR_NO_CERTIFICATION_TYPE_SPECIFIED);
            throw new GeneralException(message.getLocalizedMessage(_locale, null));
        }

        return datasource;
    }

    @Override
    public void preFill(SailPointContext ctx, 
                        Attributes<String, Object> args, 
                        JasperReport report)
            throws GeneralException {
        super.preFill(ctx, args, report);
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        args.put("identityConfig", conf);

        // by default the result set is limited to 300 rows. We can probably
        // got a little bit farther with this report since it's so flat
        if (args.containsKey("REPORT_MAX_COUNT") && 300 == (Integer)args.get("REPORT_MAX_COUNT"))
            args.put("REPORT_MAX_COUNT", 1500);

        // If it's csv, widen all the columns so jasper doesn't try and stretch
        // them horizontally. If it does, all kinds of strange things can happen.
        if ("csv".equals(args.get("renderType"))){
            List head = report.getColumnHeader().getChildren();
            for (int i = 0; i < head.size(); i++) {
                JRBaseTextField field =  (JRBaseTextField)head.get(i);
                field.setWidth(2000);
                field.setX(i * 2000);
            }

            JRBand[] bands = report.getDetailSection().getBands();
            for(int i=0;bands != null && i<bands.length;i++){
                JRBand band = bands[i];
                if (band.getElements() != null){
                    for(JRElement elem : band.getElements()){
                        if (JRBaseTextField.class.isAssignableFrom(elem.getClass())){
                            JRBaseTextField field =  (JRBaseTextField)elem;
                            field.setWidth(2000);
                            field.setX(i * 2000);
                        }
                    }
                }
            }
        }
    }

    // todo move cert type name to static or compare to enum
    @Override
    public String getJasperClass() throws GeneralException{

        Attributes<String,Object> inputs = getInputs();
        if ("roleMembership".equals(inputs.getString(ATTRIBUTE_CERT_TYPE))){
            return "BusinessRoleMembershipCertificationReport";
        }else if ("roleComposition".equals(inputs.getString(ATTRIBUTE_CERT_TYPE))) {
            return "BusinessRoleCompositionCertificationReport";
        }else{
            Message message = new Message(MessageKeys.REPORT_ERROR_NO_CERTIFICATION_TYPE_SPECIFIED);
            throw new GeneralException(message.getLocalizedMessage(_locale, null));
        }
    }
}
