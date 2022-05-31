package sailpoint.api.monitor;

import sailpoint.tools.Message;

/**
 * We currently have many different Monitors in the product (TaskMonitor, Console.Monitor, etc.).
 * The intent behind this interface is to create one generic standard Monitor that can be utilized
 * for tasks application-wide.  Obviously we're not going to retrofit everything in the 6.0 time-
 * frame but we can at least start.
 * 
 * This interface is primarily for Monitors that immediately dump their output to a log or response
 * as they go.  If your task needs to store messages in order to present them to the UI in an organized
 * manner you should consider using GenericMonitor instead.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 * @see sailpoint.api.monitor.GenericMonitor
 */
public interface ImmediateMonitor {
    /**
     * Add an informational message to the monitor
     * @param msg
     */
    public void info(Message msg);
    
    /**
     * Add a warning messge to the monitor
     * @param msg
     */
    public void warn(Message msg);
    
    /**
     * Add an error to the monitor
     * @param msg
     */
    public void error(Message msg);    
}
