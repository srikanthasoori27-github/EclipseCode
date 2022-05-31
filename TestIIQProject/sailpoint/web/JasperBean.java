/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;

import javax.faces.context.FacesContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.TaskManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.JasperResult;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.reporting.JasperExport;
import sailpoint.reporting.JasperRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.task.TaskResultBean;
import sailpoint.web.messages.MessageKeys;


public class JasperBean extends TaskResultBean implements
        Serializable {

    static final long serialVersionUID = 0;
    String _reportName = null;
    
    private static Log log = LogFactory.getLog(TaskResultBean.class);

    private static final String MIME_CSV = "application/vnd.ms-excel";

    private static final String MIME_PDF = "application/pdf";

    public JasperBean() {
        super();
        setScope(TaskResult.class);

        FacesContext ctx = FacesContext.getCurrentInstance();
        Map request = ctx.getExternalContext().getRequestParameterMap();
        _reportName = Util.getString((String) request.get("reportName"));
    }

    public void setReportName(String name) {
        _reportName = name;
    }

    public String getReportName() {
        return _reportName;
    }

    public static void exportReportToFile(SailPointContext ctx, JasperResult result, String type,
                                          Locale locale, TimeZone timezone,
                                          Attributes<String, Object> renderOptions) throws Exception { 
        if (result != null) {

            JasperRenderer renderer = new JasperRenderer(result);

            if (renderOptions != null){
                for (Map.Entry<String, Object> entry : renderOptions.entrySet()){
                    renderer.putOption(entry.getKey(), entry.getValue());
                }
            }
            
            renderer.putOption(JasperExport.USR_LOCALE, locale);
            renderer.putOption(JasperExport.USR_TIMEZONE, timezone);

            String fileName = result.getName().replaceAll(" ,.", "_");

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response = 
                 (HttpServletResponse) fc.getExternalContext().getResponse();

            ServletOutputStream out = response.getOutputStream();
            if ("pdf".compareTo(type) == 0) {
                fileName += ".pdf";
                setHeader(response, fileName);
                response.setContentType(MIME_PDF);
                renderer.renderToPDF(out);
            } else if ("csv".compareTo(type) == 0 || "csv_direct".compareTo(type) == 0) {
                fileName += ".csv";
                setHeader(response, fileName);
                response.setContentType(MIME_CSV);
                renderer.renderToCSV(out, type);
            }
            response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");
            
            fc.responseComplete();
        }
    }
    
    public static void setHeader(HttpServletResponse response, String fileName) {
//        response.setHeader("Content-disposition", "inline; filename=\""
        response.setHeader("Content-disposition", "attachment; filename=\""
                + fileName + "\"");
    }


    private JasperResult runReport(String defName)
        throws Exception {

        JasperResult jr = null;
        String resultName = defName + "_" + "Dashboard" + System.currentTimeMillis();
        Identity currentUser = getLoggedInUser();

        Attributes<String,Object> options = new Attributes<String,Object>();
        options.put(TaskSchedule.ARG_RESULT_NAME, resultName);
        options.put(sailpoint.reporting.JasperExecutor.OP_JASPER_REPORT, 
                    _reportName);
        options.put("userName", currentUser.getName());

        TaskManager tm = new TaskManager(getContext());
        tm.setLauncher(getLoggedInUserName());
        TaskResult result = tm.runSync(defName, options);

        if ( result != null ) {
            List<Message> errors = result.getErrors();
            if ( ( errors != null ) && ( errors.size() > 1 ) ) {
                // create an message, only showing the first error from the errors list 
                Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_RUNNING_REPORT, errors.get(0));
                throw new GeneralException(errMsg);
            }

            jr = result.getReport();
            if ( jr == null ) {
                throw new GeneralException(MessageKeys.ERR_FINDING_JASPER_RESULT);
            }

        } else {
            throw new GeneralException(MessageKeys.ERR_TASK_RESULT_NOT_CREATED);
        }
        return jr;
    }

}
