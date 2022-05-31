/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Represents a task for calculation impact analysis results for policy & rule.
 *
 * Currently we are gathering only statistical data from impact analysis.
 * This data consists of number of violations & number of Identities with violations.
 *
 */

package sailpoint.api;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyExecutor;
import sailpoint.object.PolicyImpactAnalysis;
import sailpoint.object.PolicyViolation;
import sailpoint.object.TaskResult;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 *
 */
public class PolicyImpactAnalysisCalculator {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the standard system task that runs the PolicyImpactAnalysisCalculator
     * in the background.
     */
    public static final String TASK_DEFINITION = "Policy Impact Analysis";

    //
    // Task Arguments
    //

    /**
     * Alternate argument to specify the policy to analyze.
     */
    public static final String ARG_TARGET_POLICY = "targetPolicy";

    /**
     * Alternate argument to specify the Rule to analyze.
     */
    public static final String ARG_TARGET_RULE = "targetRule";

    /**
     * Task Results
     */
    public static final String RET_ANALYSIS = "violationResults";

    /**
     * Attribute to be used while querying Identities from _context.
     */
    public static final String ATTR_ID = "id";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PolicyImpactAnalysisCalculator.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Arguments from the task scheduler.
     */
    Attributes<String,Object> _arguments;

    /**
     * Optional object to be informed of status.
     * !! Try to rework _trace into this so we don't need both
     * mechanisms...
     */
    TaskMonitor _monitor;

    boolean _trace;

    /**
     * Flag set in another thread to halt the execution.
     */
    boolean _terminate;

    /**
     * Analysis result we accumulate.
     */
    PolicyImpactAnalysis _analysis;

    /**
     * Policy Impact Analysis task is used for both policy & rule impact analysis.
     * This is an indicator to signify task is called for rule impact analysis.
     */
    private boolean _ruleImpactAnalysis;

    /**
     * Violations information store per constraint.
     */
    private Map<String,ConstraintConflict> _constraintConflictsInfo;

    /**
     * Number of distinct identities having violations
     */
    private int _distinctIdentities;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyImpactAnalysisCalculator(SailPointContext con) {
        _context = con;
    }

    public PolicyImpactAnalysisCalculator(SailPointContext con, Attributes<String,Object> args) {
        _context = con;
        _arguments = args;
    }

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////

    public void getResults(TaskResult result) throws GeneralException {

        if (Util.isNotNullOrEmpty(_analysis.getPolicyType()) && (_analysis.getPolicyType().equals(Policy.TYPE_RISK) ||_analysis.getPolicyType().equals(Policy.TYPE_ACCOUNT))){
            _analysis.addViolationResultToList("", _distinctIdentities,_distinctIdentities);
        }else{
            for(Map.Entry<String, ConstraintConflict> entry : _constraintConflictsInfo.entrySet()){
                _analysis.addViolationResultToList(entry.getKey(), entry.getValue().getViolationCount(), _distinctIdentities);	
            }
        }

        result.setAttribute(RET_ANALYSIS, _analysis);
        result.setTerminated(result.isTerminated() || _terminate);

    }


    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    private void updateProgress(String progress) {

        trace(progress);

        if ( _monitor != null ) _monitor.updateProgress(progress);
    }

    private void trace(String msg) {
        log.info(msg);

        if (_trace)
            System.out.println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    public void terminate() {
        _terminate = true;
    }

    /**
     * Perform impact analysis.
     */
    public void execute()
    throws GeneralException
    {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        if (_arguments == null)
            throw new GeneralException("No execution arguments");

        String progress = "Beginning Policy Impact Analysis...";
        updateProgress(progress);

        _distinctIdentities = 0;
        _ruleImpactAnalysis = false;
        _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);

        /** Create a clone of policy object
         * Keep only those roles which are being simulated.
         */
        try
        {
            String policyXml = (String) _arguments.get(ARG_TARGET_POLICY);
            if(policyXml == null){
                throw new GeneralException("No Policy object received by task");
            }

            Policy clonePolicyObject = (Policy) Policy.parseXml(_context, policyXml);
            if(clonePolicyObject == null){
                throw new GeneralException("Policy object cloning failed");
            }

            _analysis = new PolicyImpactAnalysis();

            _analysis.setPolicyName(clonePolicyObject.getName());

            /*For Rule Impact Analysis constraintId is provided as argument to task.*/
            String constraintId = _arguments.getString(ARG_TARGET_RULE);

            if(constraintId != null && !constraintId.isEmpty())
                _ruleImpactAnalysis = true;

            _constraintConflictsInfo = new HashMap<String, ConstraintConflict>();
            if(_ruleImpactAnalysis){
                /*Evaluate only the constraint which has been received for Impact Analysis*/
                List<BaseConstraint> listOfConstraints = new ArrayList<BaseConstraint>(clonePolicyObject.getConstraints());
                for(BaseConstraint constraint: listOfConstraints){
                    String id = constraint.getName();
                    if(!id.contentEquals(constraintId)){
                        clonePolicyObject.removeConstraint(constraint);
                    }
                }
                _constraintConflictsInfo.put(constraintId, new ConstraintConflict());
                clonePolicyObject.getConstraint(null,constraintId).setDisabled(false);
            }else{
                if (!Util.isEmpty(clonePolicyObject.getConstraints())) {
                    /*Policy Impact Analysis. Add only enabled constraints in impact analysis result*/
                    List<BaseConstraint> listOfConstraints = new ArrayList<BaseConstraint>(clonePolicyObject.getConstraints());
                    for(BaseConstraint constraint: listOfConstraints){
                        if(constraint.isDisabled())
                            clonePolicyObject.removeConstraint(constraint);
                        else
                            _constraintConflictsInfo.put(constraint.getName(), new ConstraintConflict());
                    }
                }
            }

            //If policy type is Risk or Account then don't set the noActiveRules value as true because Risk and Account Policy does not contain rules
            // and there is a check for noActiveRules in PolicyImpacAnalysis.xhtml which will not display proper result
            // if noActiveRules is set to true.
            String pType = clonePolicyObject.getType();
            if (Util.isNotNullOrEmpty(pType))
            	_analysis.setPolicyType(pType);
            
            if (!(pType.equalsIgnoreCase(Policy.TYPE_RISK) || pType.equalsIgnoreCase(Policy.TYPE_ACCOUNT)))
                _analysis.setNoActiveRules((_constraintConflictsInfo.size() < 1));

            int totalIdentities = 0;
            Iterator<Object[]> it = _context.search(Identity.class, null ,"id");
            IdIterator idit = new IdIterator(_context, it);
            while (idit.hasNext() && !_terminate) {
                String id = (String)(idit.next());
                Identity identity = _context.getObjectById(Identity.class, id);
                if (identity != null) {
                    totalIdentities++;
                    progress = "Identity " + Util.itoa(totalIdentities) + " : " +
                    identity.getName();
                    updateProgress(progress);
                    analyze(identity,clonePolicyObject);
                }
            }

            if(_terminate == true){
                /* Task has been terminated. Propagate required message. */
                if(log.isInfoEnabled())
                    log.info("Policy Impact Analysis task termintated");
            }
        }catch(Throwable t){
           if(log.isErrorEnabled())
               log.error("Policy Impact Analysis task failed with exception. ", t);
        }
    }

    private void analyze(final Identity identity, final Policy obj)
    throws Exception
    {
       List<PolicyViolation> violations = null;
       PolicyExecutor executor = obj.getExecutorInstance();
       if(executor != null){
           violations = executor.evaluate(_context, obj, identity);
           if (violations != null && !violations.isEmpty()) {
               if(log.isInfoEnabled()){
                   log.info("Violation(s) detected for Identity "+ identity.getName());
               }
               _distinctIdentities++;

               if (!(Util.isNotNullOrEmpty(_analysis.getPolicyType()) && (_analysis.getPolicyType().equals
                   (Policy.TYPE_RISK) || _analysis.getPolicyType().equals(Policy.TYPE_ACCOUNT)))){
                   for(PolicyViolation pv : violations){
                       /*Fill data in ConstraintConflict object per constraint*/
                       String currentConstraintName = pv.getConstraintName();

                       if(log.isInfoEnabled()){
                           log.info("Violation detected for rule "+ currentConstraintName);
                       }
                       ConstraintConflict cc = _constraintConflictsInfo.get(currentConstraintName);
                       if(cc != null){
                           cc.incrementViolationCounter();
                           _constraintConflictsInfo.put(currentConstraintName,cc);
                       }
                   }
               }
           }
       }
    }


   /**
    * An internal object to store violation statistics
    * while performing impact analysis
    */
    public class ConstraintConflict{
        /**
         * Number of violations.
         */
        int _violationCount;

        public ConstraintConflict() {
            _violationCount = 0;
        }

        public void setViolationCount(int count){
            _violationCount = count;
        }

        public int getViolationCount(){
            return _violationCount;
        }

        public void incrementViolationCounter(){
            _violationCount++;
        }
    }
}
