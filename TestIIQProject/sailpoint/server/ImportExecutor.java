/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;

import java.sql.Connection;


/**
 * An interface for an executor that can be run during import.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface ImportExecutor {

    /**
     * The context that is passed into the execute method of the executor and
     * is used to provide services needed by the importer.
     */
    public static interface Context {

        /**
         * Return a JDBC Connection.
         */
        public Connection getConnection() throws GeneralException;

        /**
         * Return a SailPointContext.
         */
        public SailPointContext getContext() throws GeneralException;

        /**
         * Return an import monitor.
         */
        public Importer.Monitor getMonitor();
        
    }
    
    /**
     * Does this ImportExecutor require a JDBC Connection - should return true
     * if this requires a connection and should be run before hibernate is
     * initialized.
     */
    public boolean requiresConnection();


    /**
     * Execute this import executor.
     * 
     * @param  context  The Context to use to execute this command.
     */
    public void execute(Context context) throws GeneralException;
    
    /**
     * ImportActions can have a single arg and can
     * be any AbstractXmlObject.  We need those
     * here so they can be used during execution
     * of the import commands.
     * 
     * @return
     */
    public AbstractXmlObject getArgument();    
    public void setArgument(AbstractXmlObject arg);
}
