/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.Stack;

import org.hibernate.Criteria;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;

import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A FilterVisitor that translates Filters into the Hibernate criteria
 * model.  This operates on a stack of filters.  Every leaf filter's
 * criteria is pushed onto the stack when it is visited.  Composite
 * filters allow all children filters to push values onto the stack,
 * then pull all children criteria off the stack and apply the composite
 * operation.  The result is pushed back onto the stack.  After all
 * filters are visited, the stack should have a single criteria. 
 * 
 * Hibernate does not support association comparison
 * simply by passing an object in as a value.  You have to 
 * create a nested Criteria for the property name, and
 * add a restriction to that that tests some unique property
 * of the referenced object, typically the name.  We can allow this
 * in Filters, but have to do the transformation here. 
 *
 * Example queries have a similar problem, associations are simply
 * ignored in the example, but those are harder to transform 
 * down here since we'd have to walk over all the properties using
 * reflection.
 *
 * The Criteria model is odd.  There are two forms:
 *
 * Criteria->createCritera->add(name,value)
 * 
 * In this form you create a nested criteria for the property
 * and add restrictions to that.  This works as long as you don't
 * need to reference the association in an and/or expression.
 *
 * Criteria->createAlias
 *         ->add(alias.name,value)
 *
 * In this form, you create an alias that can be used at any
 * depth in the Restriction hierarchy.  This is the approach we're taking
 * so we can handle all expressions the same way.
 */
public class CriteriaFilterVisitor extends Filter.BaseFilterVisitor {

    private Criteria criteria;
    private Stack<Criterion> filterStack;
    private Map<String,String> aliasMap;
    private boolean visitingOr;


    /**
     * Constructor.
     */
    public CriteriaFilterVisitor(Criteria criteria,
                                          Map<String,String> aliasMap) {
        this.criteria = criteria;
        this.aliasMap = aliasMap;
        this.filterStack = new Stack<Criterion>();
    }

    /**
     * Return the Criterion calculated from visiting a filter tree.
     */
    public Criterion getCriterion() throws GeneralException {
        if (1 != this.filterStack.size())
            throw new GeneralException("Unbalanced filter stack " + this.filterStack);
        return this.filterStack.peek();
    }

    private String getPropertyName(Filter.LeafFilter filter) {
        // Translate association references into aliases.
        String propertyName = filter.getProperty();
        Object value = filter.getValue();

        // If we're in an OR filter use a left outer join rather than an
        // inner join.  This will make it so that entries that don't join
        // will still show up in the results as long as they match one of
        // the other filters.
        int joinType =
            (this.visitingOr) ? Criteria.LEFT_JOIN : Criteria.INNER_JOIN;
        propertyName = getAlias(propertyName, this.criteria, joinType, this.aliasMap);

        if ((value instanceof SailPointObject) ||
            isCollectionOfType(SailPointObject.class, value)) {
            // Hmm, now that we support .name we don't necessarily
            // have to support this alternate convention but it
            // Is convenient.
            String qual = ".name";
            if (value instanceof SailPointObject) {
                SailPointObject spo = (SailPointObject) value;
                // Use the id if we have one, otherwise the name.
                if (null != spo.getId()) {
                    qual = ".id";
                }
            }
            else {
                qual = getQualifierType(value);
            }

            propertyName =
                getAlias(filter.getProperty() + qual,
                         this.criteria, joinType, this.aliasMap);
        }
        return propertyName;
    }

    /**
     * Peek at an element from the given collection and check if it is of
     * the given class type.
     */
    static boolean isCollectionOfType(Class clazz, Object collection) {
        if (collection instanceof Collection) {
            Collection col = (Collection) collection;
            if (!col.isEmpty() && clazz.isInstance(col.iterator().next()))
                return true;
        }
        else if (collection instanceof Object[]) {
            Object[] objs = (Object[]) collection;
            if ((objs.length > 0) && clazz.isInstance(objs[0]))
                return true;
        }

        return false;
    }

    /**
     * Return the qualifier to check (either '.id' or '.name') for the
     * elements in the given collection.
     */
    private String getQualifierType(Object collection) {
        SailPointObject so = null;
        if (collection instanceof Collection) {
            so = (SailPointObject) ((Collection) collection).iterator().next();
        }
        else if (collection instanceof Object[]) {
            so = (SailPointObject) ((Object[]) collection)[0];
        }

        return ((null != so) && (null != so.getId())) ? ".id" : ".name";
    }

    /**
     * Calculate a Hibernate query alias for the given property path.  If
     * this is not a dotted path (ie - a top-level property) the given path
     * is returned.  If this is a dotted path (ie - a nested path) an alias
     * is generated that can be referenced in Hiberate queries, the alias is
     * added to the given criteria using the   
     * 
     * @param  path      The path to the property.
     * @param  criteria  The Criteria to which to add the alias.
     * @param  joinType  The type of join to use for the alias definition.
     * @param  aliasMap  The map to which to 
     *
     * @return The given path if it is a top-level property, or the generated
     *         alias name if the path is a nested property.
     */
    private static String getAlias(String path, Criteria criteria, int joinType,
                                   Map<String,String> aliasMap) {

        // Transform an association reference into an alias.
        String[] parts = path.split("\\.");

        // Calculate or grab the alias from the aliasMap if this is a nested
        // property reference.
        if (parts.length > 1)
        {
            StringBuilder processedPath = new StringBuilder();
            StringBuilder processedAlias = new StringBuilder();

            // Don't process the last piece as part of the alias.
            for (int i=0; i<parts.length-1; i++)
            {
                String current = parts[i];
                if (0 != i)
                {
                    processedPath.append('.').append(current);
                    processedAlias.append(Util.capitalize(current));
                }
                else
                {
                    processedPath.append(current);
                    processedAlias.append(current);
                }

                // Add to the aliasMap if it does not a have a mapping for this
                // path yet.
                if (!aliasMap.containsKey(processedPath.toString()))
                {
                    aliasMap.put(processedPath.toString(), processedAlias.toString() + "Alias");
                    criteria.createCriteria(processedPath.toString(),
                                            processedAlias.toString() + "Alias",
                                            joinType);
                }
            }

            return processedAlias.toString() + "Alias." + parts[parts.length-1];
        }

        return path;
    }

    private Object getValue(Filter.LeafFilter filter) {
        return getValue(filter.getValue());
    }

    private Object getValue(Object value) {
        if (value instanceof SailPointObject) {
            // precedence has to match getPropertyName above!
            SailPointObject spo = (SailPointObject)value;
            value = spo.getId();
            if (value == null)
                value = spo.getName();
        }
        return value;
    }

    // Leaf operations.
    public void visitEQ(Filter.LeafFilter filter) {
        if (null == filter.getValue())
            visitIsNull(filter);
        else
            this.filterStack.push(ignoreCase(filter, Restrictions.eq(getPropertyName(filter),getValue(filter))));
    }

    public void visitNE(Filter.LeafFilter filter) {
        if (null == filter.getValue())
            visitNotNull(filter);
        else
            this.filterStack.push(ignoreCase(filter, Restrictions.ne(getPropertyName(filter), getValue(filter))));
    }

    public void visitLT(Filter.LeafFilter filter) {
        this.filterStack.push(ignoreCase(filter, Restrictions.lt(getPropertyName(filter), getValue(filter))));
    }

    public void visitGT(Filter.LeafFilter filter) {
        this.filterStack.push(ignoreCase(filter, Restrictions.gt(getPropertyName(filter), getValue(filter))));
    }

    public void visitLE(Filter.LeafFilter filter) {
        this.filterStack.push(ignoreCase(filter, Restrictions.le(getPropertyName(filter), getValue(filter))));
    }

    public void visitGE(Filter.LeafFilter filter) {
        this.filterStack.push(ignoreCase(filter, Restrictions.ge(getPropertyName(filter), getValue(filter))));
    }

    public void visitIn(Filter.LeafFilter filter) {
        this.filterStack.push(ignoreCase(filter, Restrictions.in(getPropertyName(filter), (Collection) getValue(filter))));
    }

    public void visitContainsAll(Filter.LeafFilter filter) throws GeneralException {

        Object val = filter.getValue();

        // Use a conjunction to AND together some eq's if the value being
        // compared against is a set of values.
        if ((val instanceof Iterable) || (val instanceof Object[])) {
            Conjunction and = Restrictions.conjunction();
            if (val instanceof Iterable) {
                int count = 0;
                for (Object current : (Iterable) val) {
                    // Throw if there is more than one value to check against.
                    if (count > 0) {
                        throwContainsAllException();
                    }
                    and.add(Restrictions.eq(getPropertyName(filter), getValue(current)));
                    count++;
                }
            }
            else if (val instanceof Object[]) {
                // Throw if there is more than one value to check against.
                if (((Object[]) val).length > 1) {
                    throwContainsAllException();
                }
                for (Object current : (Object[]) val) {
                    and.add(Restrictions.eq(getPropertyName(filter), getValue(current)));
                }
            }
            this.filterStack.push(and);
        }
        else {
            // The value is not a collection, handle gracefully with a
            // simple equality check.
            this.filterStack.push(Restrictions.eq(getPropertyName(filter), val));
        }
    }

    /**
     * Currently, our implementation of visitContainsAll() AND's together
     * multiple equality filters.  Unfortunately, since we're using aliases
     * we're AND'ing together equality checks for the same alias.  For
     * example, the query ends up saying something like:
     * 
     *   select *
     *     from identity
     *    inner join identity_bundles on identity.id = identity_bundles.identity
     *    inner join bundle on identity_bundles.bundle = bundle.id
     *    where (bundle.name = 'Foo' and bundle.name = 'Bar')
     *
     * This will obviously never return anything because a bundle name can't
     * be both Foo and Bar.  I tried to get around this by creating unique
     * alias names in this situation so that we would join on the bundle
     * table multiple times and be able to compare each name, but this
     * failed with a "duplicate association path" error from hibernate.
     * This seems to be a known issue that currently doesn't have a
     * workaround (without going to HQL):
     * http://forum.hibernate.org/viewtopic.php?t=931249
     */
    private void throwContainsAllException() throws GeneralException {
        throw new GeneralException("HibernatePersistenceManager doesn't " +
            "support containsAll() filters checking against multiple values");
    }

    public void visitLike(Filter.LeafFilter filter) throws GeneralException {
        this.filterStack.push(ignoreCase(filter, Restrictions.like(getPropertyName(filter), (String) getValue(filter),
                                                                   convertMatchMode(filter.getMatchMode()))));
    }

    private Criterion ignoreCase(Filter.LeafFilter filter, Criterion crit) {
        if (filter.isIgnoreCase() && (crit instanceof SimpleExpression))
            return ((SimpleExpression) crit).ignoreCase();
        return crit;
    }

    public void visitIsNull(Filter.LeafFilter filter) {
        this.filterStack.push(Restrictions.isNull(getPropertyName(filter)));
    }

    public void visitNotNull(Filter.LeafFilter filter) {
        this.filterStack.push(Restrictions.isNotNull(getPropertyName(filter)));
    }

    public void visitAnd(Filter.CompositeFilter filter) throws GeneralException {
        visitAndNot(filter);
    }

    public void visitNot(Filter.CompositeFilter filter) throws GeneralException {
        visitAndNot(filter);
    }

    private void visitAndNot(Filter.CompositeFilter filter) throws GeneralException {
        if ((null != filter.getChildren()) && !filter.getChildren().isEmpty()) {
            int startSize = this.filterStack.size();
            for (Filter f : filter.getChildren())
                f.accept(this);
            Criterion and = null;
            while (this.filterStack.size() > startSize) {
                if (null == and)
                    and = this.filterStack.pop();
                else
                    and = Restrictions.and(and, this.filterStack.pop());
            }
            if (Filter.BooleanOperation.NOT.equals(filter.getOperation()))
                and = Restrictions.not(and);
            this.filterStack.push(and);
        }
    }

    public void visitOr(Filter.CompositeFilter filter) throws GeneralException {
        boolean prevValue = this.visitingOr;
        this.visitingOr = true;
        if ((null != filter.getChildren()) && !filter.getChildren().isEmpty()) {
            int startSize = this.filterStack.size();
            for (Filter f : filter.getChildren())
                f.accept(this);
            Criterion or = null;
            while (this.filterStack.size() > startSize) {
                if (null == or)
                    or = this.filterStack.pop();
                else
                    or = Restrictions.or(or, this.filterStack.pop());
            }
            this.filterStack.push(or);
        }
        this.visitingOr = prevValue;
    }

    private static org.hibernate.criterion.MatchMode convertMatchMode(Filter.MatchMode mm)
        throws GeneralException
    {
        switch(mm)
        {
        case EXACT:
            return org.hibernate.criterion.MatchMode.EXACT;
        case START:
            return org.hibernate.criterion.MatchMode.START;
        case END:
            return org.hibernate.criterion.MatchMode.END;
        default:
            // default to anywhere if null or invalid
            return org.hibernate.criterion.MatchMode.ANYWHERE;
        }
    }
}
