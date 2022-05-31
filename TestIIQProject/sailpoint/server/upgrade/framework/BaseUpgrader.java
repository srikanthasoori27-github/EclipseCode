package sailpoint.server.upgrade.framework;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.server.ImportExecutor;
import sailpoint.server.upgrade.Upgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

/**
 * Base class for all upgraders that provides support for formatted output,
 * required arguments, and progress reporting.
 * 
 * @author Jeff Upton
 */
public abstract class BaseUpgrader implements ImportExecutor
{
    /**
     * The minimum progress reporting difference that should be output to the UI.
     * reportProgress can(and should) be called as often as possible, but this limits
     * the interval between output to the specified percentage difference.
     */
    private static final long DEFAULT_MIN_PERCENTAGE_REPORT_DIFFERENCE = 10;
    
    /**
     * Default maximum time elapsed between progress reports to 30 seconds.
     */
    private static final long DEFAULT_MAX_REPORT_TIME_INTERVAL = 30;
    
    /**
     * Value for _lastPercentage that means reportProgress hasn't been called
     * since construction or the last call to resetProgress.
     */
    private static final long NO_LAST_PERCENTAGE = -1;
    
    /**
     * Value for _lastReportTime that means reportProgress hasn't been called
     * since construction or the last call to resetProgress.
     */
    private static final long NO_LAST_REPORT_TIME = -1;
    
    /**
     * The active context.
     */
    protected Context _context;
    
    /**
     * The last percentage that was reported.
     */
    private long _lastPercentage = NO_LAST_PERCENTAGE;
    
    /**
     * The minimum progress reporting difference that should be output to the UI.
     * reportProgress can(and should) be called as often as possible, but this limits
     * the interval between output to the specified percentage difference.
     */
    private long _minPercentageReportDifference = DEFAULT_MIN_PERCENTAGE_REPORT_DIFFERENCE;
    
    /**
     * Argument, which can be an AbstractXmlObject 
     * comes from the ImportAction.
     */
    protected AbstractXmlObject _arg;
    
    /**
     * The list of argument descriptors required by this upgrader.
     */
    private List<ArgumentDescriptor> _argumentDescriptors = new ArrayList<ArgumentDescriptor>();
    
    /**
     * The timestamp of the last time that progress text was output to the user.
     */
    private long _lastReportTime = NO_LAST_REPORT_TIME;
    
    /**
     * The maximum number of seconds between progress report output.
     */
    private long _maxReportTimeInterval = DEFAULT_MAX_REPORT_TIME_INTERVAL;

    /**
     * Don't require a JDBC connection by default.
     */
    public boolean requiresConnection()
    {
        return false;
    }
    
    /**
     * Sets the maximum number of seconds between progress report output.
     * @param maxReportTimeInterval The number of seconds.
     */
    public void setMaxReportTimeInterval(long maxReportTimeInterval)
    {
    	_maxReportTimeInterval = maxReportTimeInterval;
    }
    
    /**
     * Sets the minimum progress reporting difference that should be output to the UI.
     * @param minPercentageReportDifference The percentage difference
     */
    public void setMinPercentageReportDifference(long minPercentageReportDifference)
    {
    	_minPercentageReportDifference = minPercentageReportDifference;
    }
    
    /**
     * Object set by the ImportAction and typically part
     * of the ImportAction specified in xml. These can
     * be useful if the upgrader wants to parameterize
     * its behavior based on configured settings.
     * 
     * This is set by the ImportCommand before
     * the execute method is called.
     * 
     * @return
     */
    public AbstractXmlObject getArgument() 
    {
        return _arg;
    }
    
    public void setArgument(AbstractXmlObject arg) 
    {
        _arg = arg;
    }
    
    /**
     * Does some setup and invokes 'performUpgrade'.
     */
    public final void execute(Context context) 
        throws GeneralException
    {
        _context = context;
        
        performUpgrade(context);
    }

    /**
     * Subclasses should implement this method to perform the actual upgrade logic.
     * @param context The import context.
     * @throws GeneralException
     */
    public abstract void performUpgrade(Context context) throws GeneralException;
    
    /**
     * Outputs text to the import monitor.
     * @param pattern The format pattern.
     * @param args The format arguments.
     */
    public void info(String pattern, Object... args)
    {
        if (_context != null) {
            String text = Util.safeMessageFormat(pattern, args);
            _context.getMonitor().info(text);
        }
    }
    
    /**
     * Outputs warning text to the import monitor.
     * @param pattern The format pattern.
     * @param args The format arguments.
     */
    public void warn(String pattern, Object... args)
    {
        if (_context != null) {
            String text = Util.safeMessageFormat(pattern, args);
            _context.getMonitor().warn(text);
        }
    }
    
    /**
     * Outputs text to the log (and not the console).
     * @param pattern The format pattern.
     * @param args The format arguments.
     */
    public void log(String pattern, Object... args)
    {
    	String text = Util.safeMessageFormat(pattern, args);
    	System.out.println(text);
    }
    
    /**
     * Logs a simple string.
     * This was added to address bug 25063 where we need to log
     * thigns that have unpredictable syntax that may not
     * work as a MessageFormat patterns.
     */
    public void logString(String str)
    {
    	System.out.println(str);
    }

    /**
     * Reports upgrader progress. An upgrader can call this as many times as they like without
     * spamming the user. The progress is only output if the percentage is different than the most recent call.
     * @param completedAmount The number of "items" successfully processed.
     * @param totalAmount The total number of "items" to process.
     */
    public void reportProgress(long completedAmount, long totalAmount)
    {
        assert(completedAmount <= totalAmount);
        
        if (_context != null) {
            long percentage = (totalAmount == 0) ? 100 : (completedAmount * 100) / totalAmount;
        
            if (shouldOutputProgress(percentage)) {
                _lastPercentage = percentage;
                _lastReportTime = System.currentTimeMillis();
                
                info("{0}% - {1,number,#} of {2,number,#} processed ({3,number,#} remaining)",
                    percentage,
                    completedAmount,
                    totalAmount,
                    totalAmount - completedAmount);
            }
        }        
    }
    
    /**
     * Determines whether or not the upgrader should output the progress report
     * for the specified percentage.     * 
     * @param percentage The current percentage.
     * @return True if the upgrader should report progress, false otherwise.
     */
    private boolean shouldOutputProgress(long percentage)
    {
    	if (percentage >= 100) {
    		return true;
    	}
    	
    	if (_lastPercentage == NO_LAST_PERCENTAGE) {
    		return true;
    	}
    	
    	long percentChange = Math.abs(percentage - _lastPercentage);
    	if (percentChange >= _minPercentageReportDifference) {
    		return true;
    	}
    	
    	if (_lastReportTime != NO_LAST_REPORT_TIME) {
    		long elapsedMillis = System.currentTimeMillis() - _lastReportTime;
    		long elapsedSeconds = elapsedMillis / 1000;
    		
    		if (elapsedSeconds > _maxReportTimeInterval) {
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    /**
     * Resets the progress tracking for a new subset of processing.
     * This should be used when an upgrader processes multiple sets of data, each with
     * a distinct totalAmount.
     */
    public void resetProgress()
    {
        _lastPercentage = NO_LAST_PERCENTAGE;
        _lastReportTime = NO_LAST_REPORT_TIME;
    }
    
    /**
     * Gets the current context associated with this upgrader.
     * @return The context.
     */
    public Context getContext()
    {
    	return _context;
    }
    
    /**
     * Gets the SailPointContext associated with this upgrader.
     * @return The SailPointContext instance.
     * @throws GeneralException
     */
    public SailPointContext getSailPointContext()
    	throws GeneralException
    {
    	return _context.getContext();
    }
    
    /**
     * Gets the argument descriptors required by this upgrader.
     * @return The list of argument descriptors.
     */
    public List<ArgumentDescriptor> getArgumentDescriptors()
    {
        return _argumentDescriptors;
    }
    
    /**
     * Specifies that this upgrade requires the specified argument.
     * Subclasses should invoke this method in the constructor, before
     * performUpgrade is executed.
     * 
     * @param argDescriptor The argument descriptor to add.
     */
    protected void addArg(ArgumentDescriptor argDescriptor)
    {
        if (requiresConnection()) {
            throw new RuntimeException("Arguments for JDBC upgraders are not supported");
        }

        if (_context != null) {
            throw new RuntimeException("Argument added during upgrader execution");
        }

        _argumentDescriptors.add(argDescriptor);
    }

    /**
     * Gets the value for the specified argument.
     * @param name The argument name.
     * @return The value for the argument if one exists, null otherwise.
     */
    protected String getArg(String name) 
    {
        if (_context instanceof Upgrader.Context) {
            return ((Upgrader.Context)_context).getArg(name);
        }
        
        return null;        
    }
}
