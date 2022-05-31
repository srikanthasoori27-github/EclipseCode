package sailpoint.web.group;

import java.io.PrintWriter;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributeExporter;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class ManagedAttributeExportBean extends BaseBean {
    private static Log log = LogFactory.getLog(ManagedAttributeExportBean.class);
    private static final String MIME_CSV = "application/vnd.ms-excel";
    
    private List<String> applicationsToExport;

    private List<String> languagesToExport;

    private String exportType;
    
    private String exportToken;
    
    public List<String> getApplicationsToExport() {
        return applicationsToExport;
    }



    public void setApplicationsToExport(List<String> applicationsToExport) {
        this.applicationsToExport = applicationsToExport;
    }



    public List<String> getLanguagesToExport() {
        return languagesToExport;
    }



    public void setLanguagesToExport(List<String> languagesToExport) {
        this.languagesToExport = languagesToExport;
    }



    public String getExportType() {
        return exportType;
    }



    public void setExportType(String exportType) {
        this.exportType = exportType;
    }



    public String getExportToken() {
        return exportToken;
    }



    public void setExportToken(String exportToken) {
        this.exportToken = exportToken;
    }


    /**
     * Export explanations to a CSV file.
     * 
     * @return
     * @throws GeneralException 
     */
    public String exportEntitlements() throws GeneralException {
        ManagedAttributeExporter exporter = new ManagedAttributeExporter(getContext());
        List<String> applicationsToExport = getApplicationsToExport();
        if (applicationsToExport != null && !applicationsToExport.isEmpty()) {
            for (String applicationToExport : applicationsToExport) {
                Application app = getContext().getObjectById(Application.class, applicationToExport);
                exporter.addApplication(app);
            }
        }
        String exportType = getExportType();
        if (exportType != null && "descriptions".equals(exportType)) {
            List<String> languagesToExport = getLanguagesToExport();
            if (languagesToExport != null && !languagesToExport.isEmpty()) {
                for (String languageToExport : languagesToExport) {
                    exporter.addLanguage(languageToExport);
                }
                
            }
        }
        
        // TODO: Need to decide whether or not to kick off a task here and redirect to a results page
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = 
            (HttpServletResponse) fc.getExternalContext().getResponse();
        PrintWriter out = null;
        
        try {
            response.setCharacterEncoding("UTF-8");
            out = response.getWriter();
            exporter.export(out);
        } catch (Exception e) {
            log.warn("Unable to export ManagedAttributes: " + e.getMessage());
            addMessage(new Message(Message.Type.Error, 
                MessageKeys.EXPLANATION_EXPORT_FAILED), null);
            
            return "";
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
        
        String filename = "ManagedAttributes.csv";
        response.setHeader("Content-disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(MIME_CSV);
        response.addCookie(new Cookie("MAExportToken", exportToken));
        
        fc.responseComplete();      
        
        return "";
    }

}
