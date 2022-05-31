package sailpoint.monitoring;

import java.util.Date;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.monitoring.IWorkflowMonitor.ProcessMetrics;
import sailpoint.monitoring.IWorkflowMonitor.ProcessMetricsParams;
import sailpoint.object.Filter;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

class ProcessMetricsHelper
{
    private static final Log     log = LogFactory
                                             .getLog(ProcessMetricsHelper.class);
    private SailPointContext     context;

    private ProcessMetricsParams params;
    private ProcessMetrics       result;

    public ProcessMetricsHelper(
            SailPointContext context,
            ProcessMetricsParams params)
    {
        this.context = context;
        this.params = params;
    }

    public ProcessMetrics getProcessMetrics()
            throws GeneralException
    {
        this.result = new ProcessMetrics();

        handle();

        return this.result;
    }

    private void handle()
            throws GeneralException
    {
        setAverageEtc();

        setTotalExecutions();

        setSuccessfulExecutions();

        setFailedExecutions();

        setPendingExecutions();
        
        setDateOfLastExecution();
    }

    // count(*) comes back as Long, not Integer
    private int getIntValueFromLong(String property, QueryOptions options)
            throws GeneralException
    {
        Long val = getScalarValueFilter(property, options, Long.class, new Long(0));
        return val.intValue();
    }

    private <T> T getScalarValueFilter(
            String property,
            QueryOptions options,
            Class<T> clz,
            T defaultValue)
            throws GeneralException
    {

        if (ProcessMetricsHelper.log.isDebugEnabled())
        {
            ProcessMetricsHelper.log.debug("QueryOptions: " + options);
        }

        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, property);
        if (!it.hasNext())
        {
            throw new GeneralException("No value for iterator");
        }
        Object[] vals = it.next();
        if (vals == null)
        {
            ProcessMetricsHelper.log.warn("iterntor.next() returned null value at getScalarValueFilter()");
            return defaultValue;
        }
        Object val = vals[0];
        if (val == null)
        {
            return defaultValue;
        }

        if (!val.getClass().equals(clz))
        {
            throw new GeneralException("expecting " + clz.getName()
                    + ", value returned: " + val + ", class: " + val.getClass());
        }

        return clz.cast(val);
    }
    
    private void setTotalExecutions()
            throws GeneralException
    {
        this.result
                .setNumExecutions(getIntValueFromLong("count(*)", getTotalExecutionsQueryOptions()));
    }

    private void setSuccessfulExecutions()
            throws GeneralException
    {
        this.result
                .setSuccessfulExecutions(getIntValueFromLong("count(*)", getSuccessfulExecutionsQueryOptions()));
    }

    private void setFailedExecutions()
            throws GeneralException
    {
        this.result
                .setFailedExecutions(getIntValueFromLong("count(*)", getFailedExecutionsQueryOptions()));
    }

    private void setPendingExecutions()
    {
        this.result.setPendingExecutions(this.result.getNumExecutions()
                - (this.result.getSuccessfulExecutions() + this.result
                        .getFailedExecutions()));
    }
    
    private void setDateOfLastExecution()
        throws GeneralException
    {
        this.result.setDateOfLastExecution(getScalarValueFilter(
                "max(startTime)",
                getDateOfLastExecutionQueryOptions(),
                Date.class,
                null));
    }

    private void setAverageEtc()
            throws GeneralException
    {
        String props = "min(stepDuration), max(stepDuration), avg(stepDuration)";

        QueryOptions options = getAvgEtcOptions();
        if (ProcessMetricsHelper.log.isDebugEnabled())
        {
            ProcessMetricsHelper.log.debug("QueryOptions: " + options);
        }
        
        Iterator<Object[]> it = this.context.search(ProcessLog.class, options, props);
        while (it.hasNext())
        {
            Object[] vals = it.next();

            this.result
                    .setMinExecutionTime(getIntCheckForNull((Integer) vals[0]));
            this.result
                    .setMaxExecutionTime(getIntCheckForNull((Integer) vals[1]));
            this.result
                    .setAverageExecutionTime(getIntCheckForNull((Double) vals[2]));

        }
    }
    
    private <T extends Number> int getIntCheckForNull(T val)
    {
        if (val == null)
        {
            return 0;
        }
        return val.intValue();
    }

    private QueryOptions getAvgEtcOptions()
    {
        QueryOptions options = new QueryOptions();

        Filter filter = Filter.and(
                Filter.eq("processName", this.params.getProcessName()), 
                Filter.isnull("stepName"), 
                Filter.notnull("endTime"));
        options.add(filter);

        return options;
    }
    
    private QueryOptions getTotalExecutionsQueryOptions()
    {
        QueryOptions options = new QueryOptions();

        Filter filter = Filter.and(
                Filter.eq("processName", this.params.getProcessName()), 
                Filter.isnull("stepName"), 
                Filter.isnull("endTime"));
        options.add(filter);

        return options;
    }

    private QueryOptions getSuccessfulExecutionsQueryOptions()
    {
        QueryOptions options = new QueryOptions();

        Filter filter = Filter.and(
                Filter.eq("processName", this.params.getProcessName()), 
                Filter.isnull("stepName"), 
                Filter.notnull("endTime"),
                Filter.eq("caseStatus", IWorkflowMonitor.Status.Successful.getValue()));
        
        options.add(filter);

        return options;
    }

    private QueryOptions getFailedExecutionsQueryOptions()
    {
        QueryOptions options = new QueryOptions();

        Filter filter = Filter.and(
                Filter.eq("processName", this.params.getProcessName()), 
                Filter.isnull("stepName"), 
                Filter.notnull("endTime"),
                Filter.eq("caseStatus", IWorkflowMonitor.Status.Failed.getValue()));
        
        options.add(filter);

        return options;
    }

    private QueryOptions getDateOfLastExecutionQueryOptions()
    {
        QueryOptions options = new QueryOptions();

        Filter filter = Filter.and(
                Filter.eq("processName", this.params.getProcessName()), 
                Filter.isnull("stepName"), 
                Filter.isnull("endTime"));
        
        options.add(filter);

        return options;
    }
}
