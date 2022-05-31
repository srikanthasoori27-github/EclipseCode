/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.rapidsetup.constraint.ConstraintUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PopulationEvaluator extends BaseConstraintEvaluator {

    private static Log log = LogFactory.getLog(PopulationEvaluator.class);

    private static final String ATTR_POPULATION = "populationValue";
    private static final String ATTR_POPULATION_NAME = "displayName";
    private static final String ATTR_CONTAINS   = "contains";

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        validate(constraintConfig);

        // Validate ensures we will have a non-null population with complete values by this point.
        Map population = getPopulation(constraintConfig);
        String populationName = (String)population.get(ATTR_POPULATION_NAME);
        boolean result;

        SailPointContext spContext = context.getSailPointContext();
        if (isContains(population)) {
            GroupDefinition groupDef = findPopulationByName(spContext, populationName);
            try {
                result = isMemberOf(context, groupDef, populationName);
            }
            finally {
                if (groupDef != null) {
                    spContext.decache(groupDef);
                }
            }
        }
        else {
            GroupDefinition groupDef = findPopulationByName(spContext, populationName);
            try {
                result = isNotMemberOf(context, groupDef, populationName);
            }
            finally {
                if (groupDef != null) {
                    spContext.decache(groupDef);
                }
            }
        }

        return result;
    }

    @Override
    public void validate(Map constraintConfig) throws GeneralException {
        if (!hasPopulation(constraintConfig)) {
            throw new GeneralException("Must have " + ATTR_POPULATION + " attribute set");
        }

        Map population = getPopulation(constraintConfig);
        Boolean contains = (Boolean)population.get(ATTR_CONTAINS);
        String popName = (String)population.get(ATTR_POPULATION_NAME);

        if (contains == null || Util.isEmpty(popName)) {
            throw new GeneralException("Must have both " + ATTR_CONTAINS + " and " + ATTR_POPULATION_NAME + " attributes set");
        }
    }

    /**
     * Determine if identity is a member (or not a member) of the given GroupDefinition
     * @param constraintContext the constraint evaluation context
     * @param groupDefinition the GroupDefinition to check if identity is/is not a member of, possibly null
     * @param groupDefName for logging, the name of GroupDefinition to check if identity is/is not a member of
     * @param checkNotMemberOf if true, check if identity is not a member of the GroupDefinition.
     *                         if false, check if identity is a member of the GroupDefinition.
     * @return
     * @throws GeneralException  a database error occurs
     */
    private boolean isMemberOf(ConstraintContext constraintContext, GroupDefinition groupDefinition, String groupDefName, boolean checkNotMemberOf)
            throws GeneralException {

        if (groupDefinition == null) {
            if (checkNotMemberOf) {
                log.debug("True, cannot be a member of an unknown population " + groupDefName);
                return true;
            } else {
                log.debug("False, cannot be a member of an unknown population " + groupDefName);
                return false;
            }
        }
        else {
            int count = 0;
            Filter filterGd = groupDefinition.getFilter();
            log.debug("Enter filterGd.." + filterGd + "..");
            if (filterGd != null) {
                Identity new_identity = constraintContext.getNewIdentity();
                Filter combo = Filter.and(Filter.eq("id", new_identity.getId()), filterGd);
                QueryOptions ops = new QueryOptions();
                ops.add(combo);
                log.debug("Enter isMemberOf ops.." + ops);
                SailPointContext spContext = constraintContext.getSailPointContext();
                count = spContext.countObjects(Identity.class, ops);
            }
            return checkNotMemberOf ? (count == 0) : (count > 0);
        }

    }

    private boolean isMemberOf(ConstraintContext constraintContext, GroupDefinition groupDef, String groupDefName) throws GeneralException {
        return isMemberOf(constraintContext, groupDef, groupDefName, false);
    }

    private boolean isNotMemberOf(ConstraintContext constraintContext, GroupDefinition groupDef, String groupDefName) throws GeneralException {
        return isMemberOf(constraintContext, groupDef, groupDefName, true);
    }

    /**
     * Get a GroupDefinition by name
     * @param context persistence context
     * @param populationName the name of the GroupDefinition to return
     * @return the GroupDefinition with the name given by populationName
     * @throws GeneralException if there are more than one GroupDefinition objects with the name
     */
    private GroupDefinition findPopulationByName(SailPointContext context, String populationName)
    throws GeneralException {
        GroupDefinition groupDef = null;

        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("name", populationName));
        int count = context.countObjects(GroupDefinition.class, ops);
        if (count == 0) {
            // repeating here for clarity
            groupDef = null;
        }
        else if (count == 1) {
            groupDef = context.getObjectByName(GroupDefinition.class, populationName);
        }
        else if (count > 1){
            throw new GeneralException("More than one population found with name ‘" + populationName + "'");
        }
        return groupDef;
    }

    private boolean hasPopulation(Map constraintConfig) {
        if (ConstraintUtil.hasAttributes(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);
            Object population = attributes.get(ATTR_POPULATION);
            return population != null;
        }

        return false;
    }

    private Map getPopulation(Map constraintConfig) {
        if (hasPopulation(constraintConfig)) {
            Map attributes = ConstraintUtil.getAttributes(constraintConfig);

            return (Map)attributes.get(ATTR_POPULATION);
        }

        return null;
    }

    private boolean isContains(Map populationValue) {
        boolean contains = true;

        Object value = populationValue.get(ATTR_CONTAINS);
        if (value instanceof Boolean) {
            contains = (Boolean)value;
        } else {
            log.debug("PopulationEvaluator: Cannot determine value of " + ATTR_CONTAINS + " property. Defaulting to true.");
        }

        return contains;
    }
}
