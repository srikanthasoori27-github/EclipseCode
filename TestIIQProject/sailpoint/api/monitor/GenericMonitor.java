package sailpoint.api.monitor;

import java.util.Collection;

import sailpoint.tools.Message;

/**
 * We currently have many different Monitors in the product (TaskMonitor, Console.Monitor, etc.).
 * The intent behind this interface is to create one generic standard Monitor that can be utilized
 * for tasks application-wide.  Obviously we're not going to retrofit everything in the 6.0 time-
 * frame but we can at least start.
 * 
 * This interface assumes that the task is storing up messages in order to present them all at once
 * upon completion.  If your task is immediately dumping all of its messages into an OuputStream consider
 * using ImmediateMonitor instead.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 * @see sailpoint.api.monitor.ImmediateMonitor
 */
public interface GenericMonitor extends ImmediateMonitor {
    /**
     * @return Collection of informational messages
     */
    public Collection<Message> getInfo();
    
    /**
     * @return Collection of warning messages
     */
    public Collection<Message> getWarnings();

    /**
     * @return Collection of error messages
     */
    public Collection<Message> getErrors();

}
