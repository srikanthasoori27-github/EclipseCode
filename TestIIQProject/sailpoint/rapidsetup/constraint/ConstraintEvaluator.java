/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rapidsetup.constraint;

import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * The contract for trigger constraint evaluators
 */
public interface ConstraintEvaluator {

    /**
     *
     * @param context the identity information which the evaluator will evaluate
     * @param constraintConfig the configuration info about the constraint evaluator
     * @return true if the constraint evaluator feels the given context does not warrant
     * @throws GeneralException
     */
    boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException;

    /**
     * @throws GeneralException if the given constraintConfig is not appropriate or sufficient
     */
    void validate(Map constraintConfig) throws GeneralException;
}
