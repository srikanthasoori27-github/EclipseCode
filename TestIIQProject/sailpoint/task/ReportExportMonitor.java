/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 * Author: Dan 
 *
 */
package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;

/**
 * This class is used to track the export status 
 * when exporting reports to pdf, csv, and rtf.
 */
public class ReportExportMonitor implements Monitor {

    private static Log log = LogFactory.getLog(ReportExportMonitor.class);

    /**
     * The current progress string.
     */
    private String _progress;
   
    /**
     * Current Percentage complete.
     */
    private int _percentComplete;

    /**
     * Current Percentage complete.
     */
    boolean _completed = false;

    public ReportExportMonitor() {
        _completed = false;
        _percentComplete = 0;
        _progress  = null;
    }

    /**
     * Update the progress string of a task.
     */
    public void updateProgress( String progressString)
        throws GeneralException {

        updateProgress(progressString, -1, false);
    }

    /**
     * Update the progress string and percent compelte of a task.
     */
    public void updateProgress( String progressString,
                                int percentComplete)  
        throws GeneralException {

        updateProgress(progressString, percentComplete, false);
    }

    /**
     * Update the progress string and percent compelte of a task.
     * This method will save and commit the TaskResult if the
     * progressInterval has been exceeded.
     */
    public void updateProgress( String progressString,
                                int percentComplete,
                                boolean forceUpdate)
        throws GeneralException {

        _percentComplete = percentComplete;
        _progress = TaskMonitor.truncateIfNecessary(progressString);
    }

    public String getProgress() {
        return _progress;
    }

    public int getPercentComplete() {
         return _percentComplete;
    }

    public boolean hasCompleted() {
        return _completed;
    }

    public void completed() {
        _completed = true;
    }
}
