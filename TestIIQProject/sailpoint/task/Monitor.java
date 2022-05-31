/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class to help task executors write task progress
 */
package sailpoint.task;

import sailpoint.tools.GeneralException;


public interface Monitor {

    /**
     * The maximum length of a string value we can store in the progress
     * column.
     */
    public static final int MAX_PROGRESS_LENGTH = 255;

    /**
     * Visual indicator that we have truncated the progress.
     */
    public static final String ELLIPSE = "...";

    /**
     * Update the progress string of a task.
     */
    public void updateProgress( String progressString)
        throws GeneralException;

    /**
     * Update the progress string and percent compelte of a task.
     */
    public void updateProgress( String progressString,
                                int percentComplete)  
        throws GeneralException;

    /**
     * Update the progress string and percent compelte of a task.
     * This method will save and commit the TaskResult if the
     * progressInterval has been exceeded.
     */
    public void updateProgress( String progressString,
                                int percentComplete,
                                boolean forceUpdate)
        throws GeneralException;

    
    public void completed();
}
