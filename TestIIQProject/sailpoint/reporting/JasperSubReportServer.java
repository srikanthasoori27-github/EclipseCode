/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.jasperreports.engine.JasperReport;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.JasperTemplate;

/** 
 * ImageServer to be used in conjunction with the JasperReports.
 * Reports will store url's to the images where the images can be
 * rendered via http.
 */
public class JasperSubReportServer extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    public static final String IMAGE_NAME_REQUEST_PARAMETER = "image";
				
    SailPointContext _ctx;
			
    /**
     * Entry which will take the name of the JasperResult.
     */
    public void service( HttpServletRequest request,
                         HttpServletResponse response) 
        throws IOException, ServletException {

 	try {

 	    _ctx = SailPointFactory.getCurrentContext();

	}  catch (Exception e) {
	    throw new ServletException(e);
	}
	        
        String reportName = request.getParameter("report");

        JasperReport report = getJasperReport(reportName); 

        // 
        // 
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(report);

        byte[] serializedObject = baos.toByteArray();

        ServletOutputStream ouputStream = response.getOutputStream();
        ouputStream.write(serializedObject, 0, serializedObject.length);
        ouputStream.flush();
        ouputStream.close();
    }

    private JasperReport getJasperReport(String name)  
        throws ServletException {
	
        JasperReport report = null;

        try {

            JasperTemplate temp = _ctx.getObjectByName(JasperTemplate.class, name);
            if ( temp == null ) throw new ServletException("Unknown template");
            report = temp.getReport();
                           	 
        } catch(Exception e) { 
            throw new ServletException(e);
        }

        return report;
    }
}
