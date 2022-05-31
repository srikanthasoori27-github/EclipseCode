/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.base.JRBaseTextField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.CertificationReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CertificationReport extends BaseCertificationReport {

    private static final Log log = LogFactory.getLog(CertificationReport.class);

    // list of applications to query, value is a csv string of IDs
    private static final String ATTRIBUTE_APPS = "applications";
    private static final String ATTRIBUTE_MGRS = "managers";
    private static final String ATTRIBUTE_GROUPS = "groups";

    private static final String ATTRIBUTE_CERT_TYPE = "certificationType";
    private static final String ATTRIBUTE_CERT_TYPE_GROUP = "group";
    private static final String ATTRIBUTE_CERT_TYPE_APP = "applicationOwner";
    private static final String ATTRIBUTE_CERT_TYPE_MGR = "manager";

    private static final String TITLE_MGR_CERT = MessageKeys.REPT_CERTIFICATION_TITLE_MGR;
    private static final String TITLE_APP_OWNER_CERT = MessageKeys.REPT_CERTIFICATION_TITLE_APP_OWNER;
    private static final String TITLE_ADV_CERT = MessageKeys.REPT_CERTIFICATION_TITLE_ADVANCED;


    // todo remove
    @Override
    public String getJasperClass() {
        return "CertificationReport";
    }

    public BaseCertificationDataSource internalCreateDataSource()
        throws GeneralException {
        
        Attributes<String, Object> attributes = getInputs();
        SailPointContext ctx = getContext();

        List<String> objectIds = new ArrayList<String>();
        Certification.Type certType = getCertificationType(attributes);
        if (Certification.Type.ApplicationOwner.equals(certType)){
            objectIds = Util.csvToList(attributes.getString(ATTRIBUTE_APPS));
        } else if (Certification.Type.Group.equals(certType)){
            objectIds = Util.csvToList(attributes.getString(ATTRIBUTE_GROUPS));
        } else if (Certification.Type.Manager.equals(certType)){
             // convert the selected IDs to name since cert stores the mgr's name not ID
            List<String> managerIds = Util.csvToList(attributes.getString(ATTRIBUTE_MGRS));
            if (managerIds != null && !managerIds.isEmpty()){
                Iterator<Object[]> managers = getContext().search(Identity.class,
                        new QueryOptions(Filter.in("id", managerIds)), Arrays.asList("name"));
                if (managers != null){
                    while (managers.hasNext()) {
                        Object[] row =  managers.next();
                        if(row[0] != null)
                            objectIds.add(row[0].toString());
                    }
                }
            }
        }                

        return new CertificationReportDataSource(certType, objectIds, attributes, getLocale(), getTimeZone());
    }

    @Override
    public void preFill(SailPointContext ctx,
                        Attributes<String, Object> args,
                        JasperReport report)
            throws GeneralException {
        super.preFill(ctx, args, report);
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        args.put("identityConfig", conf);

        Locale locale = args.get(JRParameter.REPORT_LOCALE) != null ?
                (Locale)args.get(JRParameter.REPORT_LOCALE) : Locale.getDefault();


        Certification.Type certType = getCertificationType(args);
        Message title = null;
        if (Certification.Type.ApplicationOwner.equals(certType)){
            title = new Message(TITLE_APP_OWNER_CERT);
        } else if (Certification.Type.Manager.equals(certType)){
            title = new Message(TITLE_MGR_CERT);
        } else if (Certification.Type.Group.equals(certType)){
            title = new Message(TITLE_ADV_CERT);
        } else {
            log.error("Unknown certification type. Value was '"+certType+"'.");
            throw new RuntimeException("Unknown certification type.");
        }

        args.put("title", title.getLocalizedMessage(locale, null));

        // by default the result set is limited to 300 rows. We can probably
        // got a little bit farther with this report since it's so flat
        if (args.containsKey(REPORT_PARAM_MAX_COUNT) && 300 == (Integer) args.get(REPORT_PARAM_MAX_COUNT))
            args.put(REPORT_PARAM_MAX_COUNT, 1500);

        // If it's csv, widen all the columns so jasper doesn't try and stretch
        // them horizontally. If it does, all kinds of strange things can happen.
        if ("csv".equals(args.get(OP_RENDER_TYPE))) {
            List head = report.getColumnHeader().getChildren();
            for (int i = 0; i < head.size(); i++) {
                JRBaseTextField field = (JRBaseTextField) head.get(i);
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

    private Certification.Type getCertificationType(Attributes<String, Object> args){
       String certType = args.getString(ATTRIBUTE_CERT_TYPE);

       if (ATTRIBUTE_CERT_TYPE_GROUP.equals(certType))
            return Certification.Type.Group;
       else if (ATTRIBUTE_CERT_TYPE_APP.equals(certType))
            return Certification.Type.ApplicationOwner;
       else if (ATTRIBUTE_CERT_TYPE_MGR.equals(certType))
            return Certification.Type.Manager;

       log.error("Unknown certification type. Value was '"+certType+"'.");
       throw new RuntimeException(MessageKeys.ERR_FATAL_SYSTEM);
    }
}
