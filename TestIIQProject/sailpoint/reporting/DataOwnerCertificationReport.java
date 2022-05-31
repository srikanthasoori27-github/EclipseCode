/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.base.JRBaseTextField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ObjectConfig;
import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.DataOwnerCertificationReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:tpox.mozambo@sailpoint.com">Tpox Mozambo</a>
 */
public class DataOwnerCertificationReport extends BaseCertificationReport {

     // list of applications to query, value is a csv string of IDs
    private static final String ATTRIBUTE_APPLICATIONS = "applications";

    public BaseCertificationDataSource internalCreateDataSource()
        throws GeneralException {

        Attributes<String,Object> args = getInputs();

        List<String> applicationIds =  ReportParameterUtil.splitAttributeValue(args.getString(ATTRIBUTE_APPLICATIONS));
        List<String> applicationNames = new ArrayList<String>();
        if (Util.isEmpty(applicationIds)) { 
            Message message = new Message(MessageKeys.REPORT_ERROR_NO_APPLICATION_SPECIFIED);
            throw new GeneralException(message.getLocalizedMessage(_locale, null));
		} 

        for (String applicationId : applicationIds) {
            Application app = getContext().getObjectById(Application.class, applicationId);
            if (app != null) {
                applicationNames.add(app.getName());
            }
        }

        return new DataOwnerCertificationReportDataSource(applicationNames, _locale, _timezone);
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
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

    @Override
    public String getJasperClass() throws GeneralException{

        return "DataOwnerCertificationReport";
    }
}
