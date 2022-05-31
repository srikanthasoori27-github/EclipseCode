/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.jasperreports.engine.JRImageRenderer;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRRenderable;
import net.sf.jasperreports.engine.JRWrappingSvgRenderer;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRHtmlExporter;
import net.sf.jasperreports.engine.type.OnErrorTypeEnum;
import net.sf.jasperreports.engine.util.JRTypeSniffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.JasperResult;
import sailpoint.tools.GeneralException;

/** 
 * ImageServer to be used in conjunction with the JasperReports.
 * Reports will store url's to the images where the images can be
 * rendered via http.
 */
public class JasperImageServer extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static Log _log = LogFactory.getLog(JasperImageServer.class);
				
    public static final String IMAGE_NAME_REQUEST_PARAMETER = "image";
    public static final String REPORT_REQUEST_PARAMETER = "report";
			
    /**
     * Entry which will take the name of the JasperResult.
     */
    public void service( HttpServletRequest request,
                         HttpServletResponse response) 
        throws IOException, ServletException {

        SailPointContext ctx = null;
		
        try {

            // note that we need a name for auditing
            ctx = SailPointFactory.createContext("JasperImageServer");

            byte[] imageData = null;
            String imageMimeType = null;

            // TODO: can we avoid embeding this url into reports? 
            // kinda lame.
            String imageName = request.getParameter(IMAGE_NAME_REQUEST_PARAMETER);
            if ( "px".equals(imageName) ) {

                JRRenderable pxRenderer = JRImageRenderer.getInstance(
				"net/sf/jasperreports/engine/images/pixel.GIF", OnErrorTypeEnum.ERROR);
		imageData = pxRenderer.getImageData();

            } else {
                String reportName = request.getParameter(REPORT_REQUEST_PARAMETER);
                if ( reportName == null ) {
                    throw new ServletException(REPORT_REQUEST_PARAMETER + " must be specified.");
                }
                List jasperPrintList = getPrintList(ctx, reportName);
                if (jasperPrintList == null) {
	            throw new ServletException("No JasperPrint documents found.");
	        }
                JRPrintImage image = 
                    JRHtmlExporter.getImage(jasperPrintList, imageName);

                JRRenderable renderer = image.getRenderer();
	        if ( renderer.getType() == JRRenderable.TYPE_SVG ) {
	            renderer = new JRWrappingSvgRenderer( renderer, 
         		          new Dimension(image.getWidth(), image.getHeight()),
        			  image.getBackcolor());
                }

                imageMimeType = JRTypeSniffer.getImageMimeType(renderer.getImageType());
	        imageData = renderer.getImageData();
            }

            // Send the image over to the client...
            if (imageData != null && imageData.length > 0) {
                if (imageMimeType != null) {
                    response.setHeader("Content-Type", imageMimeType);
                }
	        response.setContentLength(imageData.length);
	        ServletOutputStream ouputStream = response.getOutputStream();
	        ouputStream.write(imageData, 0, imageData.length);
	        ouputStream.flush();
	        ouputStream.close();
            }

	} catch (Exception e) {
	    throw new ServletException(e);
	} finally {
            try {
                SailPointFactory.releaseContext(ctx);
            } catch (GeneralException e) {
                if (_log.isWarnEnabled())
                    _log.warn("Failed releasing SailPointContext: "
                             + e.getLocalizedMessage(), e);
            }
        }
    }

    private List getPrintList(SailPointContext ctx, String name) 
        throws Exception {
	
        JasperResult result = ctx.getObjectById(JasperResult.class, name);
        JasperPrint jasperPrint = result.getJasperPrint();
        List<JasperPrint> list = null;
        if ( jasperPrint != null ) {                   	 
            list = new ArrayList<JasperPrint>();
            list.add(jasperPrint);
        }
        return list;
    }
}
