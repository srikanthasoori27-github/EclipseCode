/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rapidsetup.constraint.impl.AndEvaluator;
import sailpoint.rapidsetup.constraint.impl.BaseAttributeConstraintEvaluator;
import sailpoint.rapidsetup.constraint.impl.BooleanEvaluator;
import sailpoint.rapidsetup.constraint.impl.DateEvaluator;
import sailpoint.rapidsetup.constraint.impl.GenericChangeEvaluator;
import sailpoint.rapidsetup.constraint.impl.IdentityEvaluator;
import sailpoint.rapidsetup.constraint.impl.NumberEvaluator;
import sailpoint.rapidsetup.constraint.impl.OrEvaluator;
import sailpoint.rapidsetup.constraint.impl.PopulationEvaluator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.rapidsetup.constraint.impl.StaticEvaluator;
import sailpoint.rapidsetup.constraint.impl.StringEvaluator;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

/**
 * Encapsulates the data needed for ConstraintEvaluator implementations to evaluate.
 */
public class ConstraintContext {

    private static Log log = LogFactory.getLog(ConstraintContext.class);

    /**
     * the new (or updated) state of a changing identity
     */
    private Identity newIdentity;

    /**
     * the previous (snapshot) state of a changing identity
     */
    private Identity prevIdentity;

    /**
     * for data persistence and rule execution
     */
    private SailPointContext spContext;


    /**
     * Construct the ConstraintContext with the desired fields
     * @param spContext for data persistence and rule execution
     * @param newIdentity the new (or updated) state of a changing identity
     * @param prevIdentity the previous (snapshot) state of a changing identity
     */
    public ConstraintContext(SailPointContext spContext, Identity newIdentity, Identity prevIdentity) {
        this.newIdentity = newIdentity;
        this.prevIdentity = prevIdentity;
        this.spContext = spContext;
    }

    /**
     * Given the constraintConfig map, instantiate a corresponding ConstraintEvaluator, and run evalaute
     * the ConstraintEvaluator with this
     * @param constraintConfig defines which ConstraintEvaluator to run, and its config
     * @return the result of calling the ConstraintEvaluator
     * @throws GeneralException an unexpected error occurred
     */
    public boolean evaluate(Map constraintConfig) throws GeneralException {
        boolean result = false;

        if (constraintConfig != null) {
            String debugString = null;
            if (log.isDebugEnabled()) {
                debugString = configToString(constraintConfig);
            }
            try {
                if (log.isDebugEnabled()) {
                    log.debug("START -> " + debugString);
                }
                ConstraintEvaluator evaluator = createEvaluator(constraintConfig);
                if (evaluator != null) {
                    result = evaluator.evaluate(this, constraintConfig);

                    if (log.isDebugEnabled()) {
                        log.debug("COMPLETE (result=" + result + ") -> " + debugString );
                    }
                }
            }
            catch (GeneralException e) {
                if (log.isDebugEnabled()) {
                    log.debug("EXCEPTION -> " + debugString + " with exception=" + e.getLocalizedMessage());
                }
                throw e;
            }
        }
        return result;
    }

    /**
     * Create a ConstraintEvaluator object which is capable of evaluating the expression given
     * in ConstraintConfig, or throw an exception if it can't
     * @param constraintConfig the constraintConfig for which to create the evaluator
     * @return an instance of ConstraintEvaluator which corresponds to the constraintConfig
     * @throws GeneralException if no evaluator can be found for the given constraintConfig
     */
    private ConstraintEvaluator createEvaluator(Map constraintConfig) throws GeneralException {
        String type = (String)constraintConfig.get("type");

        ConstraintEvaluator evaluator = null;
        if (type != null) {
            if ("group".equalsIgnoreCase(type)) {
                String operator = (String)constraintConfig.get("booleanOperation");
                if ("AND".equalsIgnoreCase(operator)) {
                    evaluator = new AndEvaluator();
                }
                else if ("OR".equalsIgnoreCase(operator)) {
                    evaluator = new OrEvaluator();
                }
            }
            else if ("Population".equalsIgnoreCase(type)) {
                evaluator = new PopulationEvaluator();
            }
            else if ("Attribute".equalsIgnoreCase(type)) {
                evaluator = getAttributeEvaluator(constraintConfig);
            }
            // Used for testing only
            else if ("Static".equalsIgnoreCase(type)) {
                evaluator = new StaticEvaluator();
            }
        }
        else {
            throw new GeneralException("Missing constraint type");
        }
        return evaluator;
    }

    //////////////////////////////
    // Debug utilities
    //////////////////////////////

    /**
     * Convert the given constraintConfig to a string for debugging
     * @param constraintConfig the constraintConfig map to convert to string
     * @return the string representation of the map
     */
    private String configToString(Map constraintConfig) {
        String str = null;
        if (constraintConfig != null) {
            String type = (String)constraintConfig.get("type");

            StringBuilder builder = new StringBuilder();
            builder.append("[");
            builder.append(" type='").append(type).append("'");
            for (Object keyObj : constraintConfig.keySet()) {
                String key = (String)keyObj;
                if ("type".equalsIgnoreCase(key)) {
                    continue;
                }
                Object valObj = constraintConfig.get(key);
                if (valObj instanceof List) {
                    builder.append(" ").append(key).append("<nested>");
                    continue;
                }
                builder.append(" ").append(key).append("='").append(valObj.toString()).append("'");
            }
            builder.append("]");
            str = builder.toString();
        }
        return str;
    }

    private BaseAttributeConstraintEvaluator getAttributeEvaluator(Map constraintConfig) throws GeneralException {

        AttributeValueDTO attributeValue = new AttributeValueDTO(constraintConfig);
        BaseAttributeConstraintEvaluator evaluator = null;

        if (attributeValue.hasOperator()) {
            ListFilterValue.Operation op = attributeValue.getOperator();
            if (op == ListFilterValue.Operation.Changed || op == ListFilterValue.Operation.NotChanged) {
                evaluator = new GenericChangeEvaluator(attributeValue);
            }
        }
        if (evaluator == null && attributeValue.hasDataType()) {
            ListFilterDTO.DataTypes dataType = attributeValue.getDataType();

            if (dataType == ListFilterDTO.DataTypes.Date) {
                evaluator = new DateEvaluator(attributeValue);
            }
            else if (dataType == ListFilterDTO.DataTypes.Boolean) {
                evaluator = new BooleanEvaluator(attributeValue);
            }
            else if (dataType == ListFilterDTO.DataTypes.String) {
                evaluator = new StringEvaluator(attributeValue);
            }
            else if (dataType == ListFilterDTO.DataTypes.Number) {
                evaluator = new NumberEvaluator(attributeValue);
            }
            else if (dataType == ListFilterDTO.DataTypes.Identity) {
                evaluator = new IdentityEvaluator(attributeValue);
            }
            else {
                throw new GeneralException("Unsupported attribute data type " +  dataType +
                        " for property '" + attributeValue.getProperty() + "'");
            }
        }
        if (evaluator == null) {
            throw new GeneralException("Invalid attribute configuration with name '" +
                    attributeValue.getProperty() + "'");
        }

        return evaluator;
    }

    /////////////////////////////
    // Getters/Setters
    /////////////////////////////

    public Identity getNewIdentity() {
        return newIdentity;
    }

    public Identity getPrevIdentity() {
        return prevIdentity;
    }

    public SailPointContext getSailPointContext() {
        return spContext;
    }

}
