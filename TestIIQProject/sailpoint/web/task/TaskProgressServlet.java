package sailpoint.web.task;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.XmlUtil;

public class TaskProgressServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static Log log = LogFactory.getLog(TaskProgressServlet.class);
	
	/**
	 *	Handles TaskResults progress status in a Restful way 
	 */
	@Override
	protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException {
		/* Get TaskResult id from passed parameters */
		Object taskId = req.getParameter( TASK_RESULT_ID );
		/* Fetch TaskResult from PersistenceManager */
        TaskResult taskResult = getTaskResult( taskId );
		/* Process TaskResult, build response, respond */
        String progress = getProgress( taskResult );
        boolean complete = getComplete( taskResult );
        int percentComplete = getPercentComplete( taskResult );
        setupResponse( resp );
        writeResponse( resp, progress, complete, percentComplete );
	}

	private static final String TASK_RESULT_ID = "taskResultId";
	/* Constants for xml response */
	private static final String TASK_RESULT_COMPLETED = "completed";
	private static final String TASK_RESULT_PROGRESS = "progress";
	private static final String TASK_RESULT_PERCENT_COMPLETE = "percentComplete";
	private static final String AJAX_RESPONSE_OPENING = "<ajax-response>";
	private static final String AJAX_RESPONSE_CLOSING = "</ajax-response>";
	private static final String XML_OPEN = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	private static final String XML_CONTENT_TYPE = "text/xml; charset=UTF-8";

	private TaskResult getTaskResult( Object taskId ) throws ServletException {
		TaskResult response;
        SailPointContext ctx = null;
		try {
			/* Acquire SailPointContext */
			ctx = SailPointFactory.createContext();
			if( taskId == null ) {
				throw new GeneralException( "No Task Result ID specified" );
			}
			/* Acquire TaskResult */
			response = ctx.getObjectById( TaskResult.class, taskId.toString() );
		} catch ( GeneralException e ) {
			/* Either Acquiring Context or TaskResult failed... 
			 * catch exception and rethrow as something that makes sense in 
			 * the Servlet context */
			throw new ServletException( e );
		} finally {
			/* Regardless of what happened we need to try and release the
			 * SailPointContext we previously acquired */
			try {
                SailPointFactory.releaseContext( ctx );
            } catch (GeneralException e) {
                if (log.isWarnEnabled())
                    log.warn("Failed releasing SailPointContext: "
                             + e.getLocalizedMessage(), e);
            }
		}
		return response;
	}
	
	private String buildResponseString( String progress, boolean complete, int percentComplete ) {
	    /* Builds a response string compatible with the value expected from taskProgress.jsp */
		StringBuilder sb = new StringBuilder( XML_OPEN ).append( AJAX_RESPONSE_OPENING ).append( "<TaskProgress " );
		sb.append( TASK_RESULT_COMPLETED ).append( "=\"" ).append( complete ).append( "\" " );
		sb.append( TASK_RESULT_PERCENT_COMPLETE).append( "=\"" ).append( percentComplete ).append( "\" " );
		sb.append( TASK_RESULT_PROGRESS).append( "=\"" ).append( progress ).append( "\"" );
		sb.append( "/>" ).append( AJAX_RESPONSE_CLOSING );
		return sb.toString();
	}

	private void setupResponse( HttpServletResponse resp ) {
		resp.setStatus( HttpServletResponse.SC_OK );
		resp.setContentType( XML_CONTENT_TYPE );
	}
	
	private void writeResponse( HttpServletResponse response, String progress, boolean complete, int percentComplete ) throws IOException{
		String msg = buildResponseString( progress, complete, percentComplete );
        if (msg != null){
        	String charset = "UTF-8";
        	byte[] raw = msg.getBytes( charset );
        	response.setContentLength( raw.length );
        } else {
        	response.setContentLength(0);
        }
        
        PrintWriter out = response.getWriter();
        out.println(msg);
        out.flush();	
	}

	private int getPercentComplete(TaskResult taskResult) {
		return taskResult.getPercentComplete();
	}
	
	private boolean getComplete(TaskResult taskResult) {
		return taskResult.isComplete();
	}
	
	private String getProgress(TaskResult taskResult) {
	    String response = "Executing...";
        String progress = taskResult.getProgress();
        if ( progress != null) {
        	response = progress;
        }
		return XmlUtil.escapeAttribute( response );
	}
}
