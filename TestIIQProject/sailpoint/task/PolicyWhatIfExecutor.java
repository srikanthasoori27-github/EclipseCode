/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A variant on the identity refresh task that does analysis of the impact of the change.
 * This class provides the glue between task scheduling and PolicyImpactAnalysisCalculator.
 * 
 */

package sailpoint.task;


import sailpoint.api.PolicyImpactAnalysisCalculator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

public class PolicyWhatIfExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    PolicyImpactAnalysisCalculator _policySimulationCalculator;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyWhatIfExecutor() {
    }

    public void execute(SailPointContext context,
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        _policySimulationCalculator = new PolicyImpactAnalysisCalculator(context, args);
        _policySimulationCalculator.setTaskMonitor(new TaskMonitor(context, result));
        _policySimulationCalculator.execute();
        _policySimulationCalculator.getResults(result);
    }

    public boolean terminate() {
        if (_policySimulationCalculator != null)
            _policySimulationCalculator.terminate();
        return true;
    }

}
