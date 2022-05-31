/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseListBean;
import sailpoint.web.search.SearchBean;


/**
 * A REST resource for returning information about currently running exports
 * (PDF and CSV).
 */
@Path("/export")
public class ExportResource extends BaseResource {

    /**
     * The status returned in the export monitor when the export is complete.
     */
    public static final String STATUS_DONE = "done";


    /**
     * Return the status from the currently running export (PDF or CSV).
     * 
     * @return A Map with "status" and "percentComplete" entries that provides
     *     the status of the currently running export.  If an export is not yet
     *     running this returns a "pending" status with 0 percent complete.
     */
    @GET @Path("/status")
    public Map<String,Object> getStatus() throws GeneralException {

        // We can allow all here because it only returns data that is on the
        // logged in user's session.  The only way that the monitor can get on
        // the session is if the user kicked off an export.
        super.authorize(new AllowAllAuthorizer());

        // If the export hasn't started (ie - no monitor found on the session)
        // return a pending status.  This resource should be the only thing
        // removing the monitor from the session.
        String status = "pending";
        int percentComplete = 0;

        // Grab the monitor that was put on the session by the export process.
        ReportExportMonitor monitor =
            (ReportExportMonitor) super.getSession().getAttribute(BaseListBean.EXPORT_MONITOR);
        if (null != monitor) {
            status = getStatus(monitor);
            percentComplete = getPercentComplete(monitor);

            if (STATUS_DONE.equals(status)) {
                super.getSession().removeAttribute(SearchBean.EXPORT_MONITOR);
            }
        }
        
        // Build the result map.
        Map<String,Object> result = new HashMap<String,Object>();
        result.put("status", status);
        result.put("percentComplete", percentComplete);
        
        return result;
    }

    /**
     * Return the export status progress string from the given monitor.
     */
    private static String getStatus(ReportExportMonitor monitor) {
        String progress = null;
        if (null != monitor) {
            if (monitor.hasCompleted()) {
                progress = STATUS_DONE;
            }
            else if (monitor.getProgress() != null) {
                progress = monitor.getProgress();
            }
        }

        return (progress == null) ? "" : progress;
    }

    /**
     * Return the percent complete from the given monitor.
     */
    private static int getPercentComplete(ReportExportMonitor monitor) {
        int percent = 0;
        if (null != monitor) {
            percent = monitor.getPercentComplete();
        } 
        return percent;
    }
}
