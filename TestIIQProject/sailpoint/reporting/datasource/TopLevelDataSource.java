/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRDataSource;
import sailpoint.task.Monitor;

/**
 *  An interface that can be implemented and assosciated
 *  with JasperExecutors.
 */
public interface TopLevelDataSource extends JRDataSource {

    /**
     * Set the Monitor object that will be used to 
     * communicate status back the user as the 
     * report is filled.
     */
    public void setMonitor(Monitor monitor);

    /**
     * Method that will be call when the executor is done
     * with the filling process. 
     * Cleanup of any open resources should happen here 
     */
    public void close();
}
