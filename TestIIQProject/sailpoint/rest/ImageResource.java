package sailpoint.rest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartUtilities;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;

/**
 * Responsible for dynamically generating images, and returning them as images.
 * @author chris.annino
 *
 */
@Produces("image/png")
@Consumes(MediaType.WILDCARD)
@Path("image")
public class ImageResource extends BaseResource {
    
    private static final Log log = LogFactory.getLog(ImageResource.class);
    
    @Context private HttpServletResponse response;
    
    /**
     * used in the compliance dashboard and certification group page to dynamically generate JFreeChart
     * images. Really, all this method does is read an image object from the session and return it as a 
     * png media type. The bulk of the creation of the image is done in the ChartTag class.
     * @param id unique id of the image stored on the session
     * @return an HTTP 200 ok 
     * @see sailpoint.web.tags.ChartTag
     */
    @GET
    @Path("chart/certification")
    public Response getCertification(@QueryParam("id") String id) throws GeneralException {
        
        authorize(new RightAuthorizer(SPRight.ViewGroupCertification, SPRight.FullAccessCertificationSchedule, SPRight.FullAccessCertifications));
        
        return handleChart(id);
    }
    
    @GET
    @Path("chart/dashboard")
    public Response getDashboard(@QueryParam("id") String id) throws GeneralException {
        
        authorize(new AllowAllAuthorizer());
        
        return handleChart(id);
    }

    private Response handleChart(String id) {
        try {
            //Set MIME type on response
            this.response.setContentType("image/png");
            writeChart(id, this.getSession(), this.response.getOutputStream());
        } catch (IOException e) {
            log.error("could not write response with id: " + id, e);
        }
        return Response.ok().build();
    }
    

    private void writeChart(String id, HttpSession session, OutputStream stream) throws IOException{
        
        BufferedImage image = (BufferedImage)session.getAttribute(id+"image");
        if(image!=null) {
            ChartUtilities.writeBufferedImageAsPNG(stream, image);
        }
        
        stream.flush();
        stream.close();
    }
}
