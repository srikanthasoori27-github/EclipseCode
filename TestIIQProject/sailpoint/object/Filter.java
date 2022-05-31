/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * jsl - Note that this must implement Serializable in order to be
 * handled as a Hibernate user type (see FilterType.disassemble).
 *
 * @author Rob Cauble (initial development)
 * @author Kelly Grizzle
 */

package sailpoint.object;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Parser;
import sailpoint.tools.Parser.BaseLookAhead;
import sailpoint.tools.Reflection;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A Filter is an abstract class that is used to express boolean logical
 * expressions. The static methods on this class should be considered the
 * public API as they are the most straight-forward to use when constructing
 * query filters. Note that <code>Filter.toExpression()</code> can be fed back into
 * <code>Filter.compile(String)</code> to reconstruct a filter.
 * <p>
 * The inner classes are public, but are typically only used in an SPI to
 * convert Filters into consumer-specific queries (for example - an LDAP filter
 * string).
 * 
 */
abstract public class Filter extends AbstractXmlObject 
    implements Serializable, IXmlEqualable<Filter>
{
    private static final String COLLECTION_NOT_ALLOWED_MESSAGE =
        Util.getMessage(Util.getIIQMessages(Locale.getDefault()), "filter_collection_invalid_in_context");
    
    private static final Log log = LogFactory.getLog(Filter.class);
    
    private static final int MAX_IN_FILTER_SIZE = 100;

    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC API
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Perform a shallow copy of a filter. Here "shallow" means that
     * the structure of the filter is cloned, but not the values
     * used in the leaf filters. This is intended for use by query
     * optimizers that might need to change the structure of the filter
     * as well as do name mapping, but do not need to touch the values.
     * Some values used in filters are not currently XML serializable
     * (AbstractCertificationItem$ContinuousState) but even if it were
     * cloning the values to do query optimization is expensive and
     * best avoided.
     */
    public static Filter clone(Filter src) {
        Filter neu = null;
        if (src instanceof LeafFilter)
            neu = new LeafFilter((LeafFilter)src);
        else if (src instanceof CompositeFilter) 
            neu = new CompositeFilter((CompositeFilter)src);
        return neu;
    }

    /**
     * Compile a filter from its String representation.
     * 
     * @param  filter  The filter string.
     * 
     * @return The Filter that is compiled from the given string.
     * 
     * @throws Parser.ParseException  If the format of the filter is incorrect.
     * 
     * @see FilterCompiler
     */
    public static Filter compile(String filter) throws Parser.ParseException
    {
        return new FilterCompiler().compileFilter(filter);
    }

    /**
     * Create a Filter from an example object. This will return an AND filter
     * that compares equality for each non-null property on the given object.
     * Null is returned if the given object does not have any non-null
     * properties.
     * 
     * @param  o  The object for which to create the Filter.
     * 
     * @return An AND filter that compares equality for each non-null property
     *         on the given object. Null is returned if the given object does
     *         not have any non-null properties.
     *
     * @throws GeneralException  If there is a problem reading the properties
     *                           from the given object.
     */
    public static Filter fromExample(Object o) throws GeneralException
    {
        Filter filter = null;

        Map<String, Object> props = Reflection.getNonNullPropertyValues(o);
        for (Map.Entry<String, Object> entry : props.entrySet())
        {
            Object value = entry.getValue();
            Filter eq;
            
            // eq is not supported for lists
            if (value instanceof Collection) {
                eq = Filter.containsAll(entry.getKey(), (Collection) value);
            } else {
                eq = Filter.eq(entry.getKey(), value);
            }
            
            if (null != filter)
                filter = Filter.and(eq, filter);
            else
                filter = eq;
        }

        return filter;
    }

    /**
     * Check that the given property is equal to the given value.
     */
    public static Filter eq(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "eq"} ));
        return new LeafFilter(LogicalOperation.EQ, propertyName, value);
    }

    /**
     * Check that the given property is not equal to the given value.
     */
    public static Filter ne(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "ne"} ));
        return new LeafFilter(LogicalOperation.NE, propertyName, value);
    }

    /**
     * Check that the given property is less than the given value.
     */
    public static Filter lt(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "lt"} ));
        return new LeafFilter(LogicalOperation.LT, propertyName, value);
    }

    /**
     * Check that the given property is greater than the given value.
     */
    public static Filter gt(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "gt"} ));
        return new LeafFilter(LogicalOperation.GT, propertyName, value);
    }

    /**
     * Check that the given property is less than or equal to the given value.
     */
    public static Filter le(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "le"} ));
        return new LeafFilter(LogicalOperation.LE, propertyName, value);
    }

    /**
     * Check that the given property is greater than or equal to the given value.
     */
    public static Filter ge(String propertyName, Object value) throws IllegalArgumentException
    {
        if (value instanceof Collection)
            throw new IllegalArgumentException(MessageFormat.format(COLLECTION_NOT_ALLOWED_MESSAGE, new Object [] {value, "ge"} ));
        return new LeafFilter(LogicalOperation.GE, propertyName, value);
    }

    /**
     * Check that the given single-valued property has a value within the given
     * set of values.
     */
    public static Filter in(String propertyName, Collection<?> value)
        throws IllegalArgumentException
    {
        if ((null == value) || value.isEmpty()) {
            throw new IllegalArgumentException("A non-empty collection is required for an 'in' filter.");
        }
        
        // We aren't actually touching any of the elements of the variables value or values,
        // so don't worry about attempting to match the type of the value.
        @SuppressWarnings("unchecked")
        ArrayList<?> values = new ArrayList(value);
        List<Filter> inFilters = new ArrayList<Filter>();
        
        int valueSize = Util.size(values); 
        int slice = MAX_IN_FILTER_SIZE;
        
        // Pick off the values into manageable slices since some databases 
        // can't handle more than 1000 values for an IN clause.  Create a bunch of ORed INs. 
        for (int i = 0; i < valueSize;) {
            slice = ((i + MAX_IN_FILTER_SIZE) > valueSize) ? valueSize : i + MAX_IN_FILTER_SIZE;
            inFilters.add(new LeafFilter(LogicalOperation.IN, propertyName, values.subList(i, slice)));
            i += MAX_IN_FILTER_SIZE; 
        }
        
        // if we didn't split, then just return the IN filter
        if (Util.size(inFilters) == 1) {
            return inFilters.get(0);
        }
        
        return Filter.or(inFilters);
    }

    /**
     * Check that the given multi-valued property contains the given value.
     */
    public static Filter contains(String propertyName, Object value)
    {
        List values = new ArrayList();
        values.add(value);
        return Filter.containsAll(propertyName, values);
    }

    /**
     * Check that the given multi-valued property contains all of the given
     * values.
     */
    public static Filter containsAll(String propertyName, Collection value)
    {
        return new LeafFilter(LogicalOperation.CONTAINS_ALL, propertyName, value);
    }
    
    /**
     * Check that the given string property contains the given value as a
     * substring.
     */
    public static Filter like(String propertyName, Object value)
    {
        return like(propertyName, value, MatchMode.ANYWHERE);
    }

    /**
     * Check that the given string property contains the given value according
     * to the given match mode.
     */
    public static Filter like(String propertyName, Object value, MatchMode matchMode)
    {
        return new LeafFilter(LogicalOperation.LIKE, propertyName, value, matchMode);
    }

    /**
     * Check that the given property is not null.
     */
    public static Filter notnull(String propertyName)
    {
        return new LeafFilter(LogicalOperation.NOTNULL, propertyName, null);
    }

    /**
     * Check that the given property is null.
     */
    public static Filter isnull(String propertyName)
    {
        return new LeafFilter(LogicalOperation.ISNULL, propertyName, null);
    }

    /**
     * Check that the given multi-valued property is empty.
     */
    public static Filter isempty(String propertyName)
    {
        return new LeafFilter(LogicalOperation.ISEMPTY, propertyName, null);
    }

    /**
     * Join the given property to the requested fully-qualified join property.
     * As an example, if you are filtering Cars based on engine size but do not
     * have a direct reference to the engine, the filter might look like this:
     * 
     * <code>
     * Filter engineJoin = Filter.join("car.engineModel", "Engine.model");
     * Filter engineSize = Filter.gt("Engine.size", 289);
     * Filter engineCheck = Filter.and(engineJoin, engineSize);
     * </code>
     * 
     * 
     * @param  property      The property on entity being filter to join through.
     *                       For example, "identity".
     * @param  joinProperty  The fully-qualified property name to join to. For
     *                       example, "Identity.name".
     */
    public static Filter join(String property, String joinProperty) {
        LeafFilter leaf = new LeafFilter(LogicalOperation.JOIN, property, null);
        leaf.setJoinProperty(joinProperty);
        return leaf;
    }

    /**
     * Left join the given property to the requested fully-qualified join property.
     * As an outer join, this will not limit the results to those with matching properties
     * on the join class, instead any properties will be null in search results.
     *
     * @param  property      The property on entity being filter to join through.
     *                       For example, "identity".
     * @param  joinProperty  The fully-qualified property name to join to. For
     *                       example, "Identity.name".
     */
    public static Filter leftJoin(String property, String joinProperty) {
        LeafFilter leaf = new LeafFilter(LogicalOperation.LEFT_JOIN, property, null);
        leaf.setJoinProperty(joinProperty);
        return leaf;
    }


    /**
     * Check that the given collection (multi-valued) property has elements that
     * match the given compoundFilter. The property names in the compound
     * filter should be rooted from the collectionProperty.
     * <p>
     * Example 1: Check to see if a Car's front doors both have power locks.
     * 
     * <code>
     * // Note that these properties assume that they are rooted at the "doors"
     * // property rather than the Car.
     * Filter driverSide =
     *     Filter.and(Filter.eq("position", "frontDriver"),
     *                Filter.eq("powerLock", true));
     * Filter passengerSide =
     *     Filter.and(Filter.eq("position", "frontPassenger"),
     *                Filter.eq("powerLock", true));
     * Filter.collectionCondition("doors", Filter.and(driverSide, passengerSide));
     * </code>
     * 
     * <p>
     * Example 2: Check to see if either of a Car's front doors have power locks.
     * 
     * <code>
     * // Note that these properties assume that they are rooted at the "doors"
     * // property rather than the Car.
     * Filter driverSide =
     *     Filter.and(Filter.eq("position", "frontDriver"),
     *                Filter.eq("powerLock", true));
     * Filter passengerSide =
     *     Filter.and(Filter.eq("position", "frontPassenger"),
     *                Filter.eq("powerLock", true));
     * Filter.collectionCondition("doors", Filter.or(driverSide, passengerSide));
     * </code>
     * 
     * @param  collectionProperty  The collection (multi-valued) property to
     *                             check the elements on.
     * @param  compoundFilter      The composite filter (for example - AND or OR) that
     *                             will filter the elements.
     *
     * @return A Filter that checks that the given collection property has
     *         elements that match the given compoundFilter.
     *
     * @throws IllegalArgumentException
     *     If the compoundFilter is not an AND or OR.
     */
    public static Filter collectionCondition(String collectionProperty,
                                             Filter compoundFilter) {
        if (!(compoundFilter instanceof CompositeFilter)) {
            throw new IllegalArgumentException("Expected a composite filter");
        }

        return new LeafFilter(collectionProperty, (CompositeFilter) compoundFilter);
    }

    /**
     * Perform a subquery where the given property on the current class is found
     * in a subquery over the given subquery class/property/filter. For
     * example, if you want to find all Identities that have risky roles named
     * after them, you would do the following:
     *
     * <code>
     * Filter.subquery("firstname", Bundle.class, "name", Filter.gt("riskScoreWeight", 500));
     * </code>
     * 
     * This turns into a query looking for Identities with a first name in the
     * result set of role names with risk scores greater than 500. In pseudo
     * SQL, this would look like:
     * 
     * <pre>
     *   select *
     *     from Identity
     *    where firstname in (select name from Bundle where riskScoreWeight > 500)
     * </pre>
     * 
     * Note that a similar effect can be achieved by using Filter.join(), but
     * this allows performing multiple subqueries on the same table, whereas a
     * joined table can only be joined once.
     * 
     * @param  property          The property on the query class to compared in
     *                           against the results of the subquery.
     * @param  subqueryClass     The class to query over in the subquery.
     * @param  subqueryProperty  The property on the subquery class to compare
     *                           against.
     * @param  subqueryFilter    The possibly-null filter to apply to the
     *                           subquery.
     *
     * @return A subquery filter.
     */
    public static Filter subquery(String property, Class<?> subqueryClass,
                                  String subqueryProperty, Filter subqueryFilter) {
        // Default to an IN subquery.  May want to expose other methods to all
        // other operators and quantifiers.
        return new LeafFilter(LogicalOperation.IN, property, subqueryClass,
                              subqueryProperty, subqueryFilter);
    }

    /**
     * Combine the given filters with an AND conjunction.
     */
    public static Filter and(Filter filter1, Filter filter2)
    {
        return new CompositeFilter(BooleanOperation.AND, filter1, filter2);
    }
    
    /**
     * Combine the given filters with an AND conjunction.
     */
    public static Filter and(List<Filter> children)
    {
        return new CompositeFilter(BooleanOperation.AND, children);
    }

    /**
     * Combine the given filters with an AND conjunction.
     */
    public static Filter and(Filter... children)
    {
        return new CompositeFilter(BooleanOperation.AND, children);
    }

    /**
     * Combine the given filters with an OR conjunction.
     */
    public static Filter or(Filter filter1, Filter filter2)
    {
        return new CompositeFilter(BooleanOperation.OR, filter1, filter2);
    }

    /**
     * Combine the given filters with an OR conjunction.
     */
    public static Filter or(List<Filter> children)
    {
        return new CompositeFilter(BooleanOperation.OR, children);
    }

    /**
     * Combine the given filters with an OR conjunction.
     */
    public static Filter or(Filter... children)
    {
        return new CompositeFilter(BooleanOperation.OR, children);
    }
    
    /**
     * Negate the given filter.
     */
    public static Filter not(Filter filter)
    {
        return new CompositeFilter(BooleanOperation.NOT, filter);
    }

    /**
     * Apply case-insensitivity to the given filter.
     */
    public static Filter ignoreCase(Filter filter)
    {
        if (filter instanceof LeafFilter)
            ((LeafFilter) filter)._ignoreCase = true;
        return filter;
    }

    public abstract void accept(FilterVisitor visitor) throws GeneralException;

    public abstract String getExpression();
    
    /**
     * 
     * @param readable Specify expression for machine for human readable language
     * @return String expression
     */
    public String getExpression(boolean readable) {
        String exp = getExpression();
        if (readable == false) {
            return exp;
        } else {
            // try and decode any java identifiers. If there's an error, stick with what we
            // got instead.
            try {
                exp = Util.decodeJavaIdentifier(exp);
            } catch (Util.ParseException e) { // Not to be confused with Parser.ParseException
                // There was a parse exception.  Log the error and return
                // the non-decoded expression instead.
                String msg = exp + " could not be decoded: " + e.getMessage();
                // bit of a reverse trick here: toss the causing exception only when we
                // are debugging.  All that noise in a warning might over-excite some folks
                if (log.isDebugEnabled()) {
                    log.debug(msg, e);
                } else {
                    log.warn(msg);
                }
            }
            return exp;
        }
    }
    
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof Filter))
            return false;
        return this.getExpression().equals(((Filter) o).getExpression());
    }
    
    public boolean contentEquals(Filter other) {
        
        return this.equals(other);
    }
    

    public int hashCode()
    {
        return this.getExpression().hashCode();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // SPI API
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * An enumeration that describes how to match strings in a 'like' filter.
     */
    @XMLClass
    public static enum MatchMode
    {
        /**
         * Match anywhere within a String - equivalent to contains().
         */
        ANYWHERE
        {
            public String toMatchString(String val, String wildcard)
            {
                return wildcard + val + wildcard;
            }
        },

        /**
         * Match the start of a String - equivalent to startsWith().
         */
        START
        {
            public String toMatchString(String val, String wildcard)
            {
                return val + wildcard;
            }
        },

        /**
         * Match the end a String - equivalent to endsWith().
         */
        END
        {
            public String toMatchString(String val, String wildcard)
            {
                return wildcard + val;
            }
        },

        /**
         * Match an exact String - equivalent to equals().
         */
        EXACT
        {
            public String toMatchString(String val, String wildcard)
            {
                return val;
            }
        };

        /**
         * Convert the given value into a match string using the given wildcard.
         * 
         * @param  value     The value for which to retrieve the match string.
         * @param  wildcard  The wildcard to use.
         * 
         * @return A match string of the given value using the given wildcard.
         */
        public abstract String toMatchString(String value, String wildcard);
    }

    @XMLClass
    public static enum LogicalOperation
    {
        /**
         *  Checks that a property is equal to a value.
         */
        EQ("==", "Equals", "NE"),

        /**
         * Checks that a property is not equal to a value.
         */
        NE("!=", "Does Not Equal", "EQ"),

        /**
         * Checks that a property is less than a value.
         */
        LT("<", "Is Less Than", "GT"),

        /**
         * Checks that a property is greater than a value.
         */
        GT(">", "Is Greater Than", "LT"),

        /**
         * Checks that a property is less than or equal to a value.
         */
        LE("<=", "Is Less Than Or Equal To", "GE"),

        /**
         * Checks that a property is greater than or equal to a value.
         */
        GE(">=", "Is Greater Than Or Equal To", "LE"),

        /**
         * Checks that a single-valued property's value is one of a given list
         * of values.
         */
        IN("IN", "Includes"),

        /**
         * Checks that a multi-valued property's list of values is a subset of
         * (or contains all elements from) another list of values.
         */
        CONTAINS_ALL("CONTAINS_ALL", "Contains All Of"),

        /**
         * Checks that a property is like a given string using a MatchMode.
         */
        LIKE("LIKE", "Is Like"),

        /**
         * Checks that a property is not null.
         */
        NOTNULL("NOTNULL", "Is Not Null", "ISNULL"),

        /**
         * Checks that a property is not null.
         */
        ISNULL("ISNULL", "Is Null", "NOTNULL"),

        /**
         * Checks that a property is not null.
         */
        ISEMPTY("ISEMPTY", "Is Empty"),

        /**
         * Joins a property from one class to a property on another.
         */
        JOIN("JOIN", "Join"),

        /**
         * Outer left joins a property from one class to a property on another
         */
        LEFT_JOIN("LEFT_JOIN", "Left Join"),
        
        /**
         * Apply a filter to elements of a collection.
         */
        COLLECTION_CONDITION("COLLECTION_CONDITION", "Collection Condition");

        
        private String stringRepresentation;
        private String displayName;
        private String inverseOperationName;

        private LogicalOperation(String stringRepresentation, String displayName)
        {
            this(stringRepresentation, displayName, null);
        }

        private LogicalOperation(String stringRepresentation, String displayName,
                                 String inverseOperationName)
        {
            this.stringRepresentation = stringRepresentation;
            this.displayName = displayName;

            // There is a chicken-egg situation here.  The enum declarations
            // cannot reference enums that have not yet been defined, so we
            // can't pass in the actual enum.  Instead, we'll store the name
            // of the enum and load by name when requested.
            this.inverseOperationName = inverseOperationName;
        }

        public String getStringRepresentation()
        {
            return this.stringRepresentation;
        }
        
        public String getDisplayName() {
            return displayName;
        }

        public LogicalOperation getInverseOperation() {
            if (null != this.inverseOperationName) {
                return LogicalOperation.valueOf(this.inverseOperationName);
            }

            return null;
        }
    }

    @XMLClass
    public static enum BooleanOperation
    {
        NOT("!"),
        AND("&&"),
        OR("||");

        private String stringRepresentation;

        private BooleanOperation(String stringRepresentation)
        {
            this.stringRepresentation = stringRepresentation;
        }

        String getStringRepresentation()
        {
            return this.stringRepresentation;
        }
    }

    @XMLClass(xmlname="Filter")
    public static class LeafFilter extends Filter
    {
        private static final long serialVersionUID = -1035650760835459534L;
        private LogicalOperation _operation;
        private MatchMode _matchMode;
        private String _property;
        private Object _value;
        private boolean _ignoreCase;
        private String _joinProperty;
        private CompositeFilter _collectionCondition;
        private String _cast;
        private Filter _subqueryFilter;
        private String _subqueryProperty;
        private Class<?> _subqueryClass;
        

        public LeafFilter()
        {
        }

        public LeafFilter(LogicalOperation operation, String property)
        {
            this(operation, property, null);
        }

        public LeafFilter(LogicalOperation operation, String property, Object value)
        {
            this(operation, property, value, null);
        }

        public LeafFilter(LogicalOperation operation, String property, Object value,
                          MatchMode matchMode)
        {
            _operation = operation;
            _property  = property;
            _value     = value;
            _matchMode = matchMode;
        }

        public LeafFilter(String property, CompositeFilter collectionCondition) {
            _operation = LogicalOperation.COLLECTION_CONDITION;
            _property = property;
            _collectionCondition = collectionCondition;
        }

        public LeafFilter(LogicalOperation operation, String property,
                          Class<?> subqueryClass, String subqueryProperty,
                          Filter subqueryFilter) {

            // May want to allow specifying a quantifier (ie - all/some) and
            // possibly also exists/not exists.
            if ((null == property) || (null == subqueryProperty) || (null == subqueryClass)) {
                throw new IllegalArgumentException("Property, subquery property, and class are required");
            }

            _operation = operation;
            _property = property;
            _subqueryClass = subqueryClass;
            _subqueryProperty = subqueryProperty;
            _subqueryFilter = subqueryFilter;
        }

        /**
         * Shallow copy for the optimizer.
         * it is important to NOT try to clone the _value.
         */
        public LeafFilter(LeafFilter src) {
            _operation = src._operation;
            _matchMode = src._matchMode;
            _property = src._property;
            _value = src._value;
            _ignoreCase = src._ignoreCase;
            _joinProperty = src._joinProperty;
            _collectionCondition = src._collectionCondition;
            _cast = src._cast;
            _subqueryClass = src._subqueryClass;
            _subqueryProperty = src._subqueryProperty;
            _subqueryFilter = src._subqueryFilter;
        }

        @XMLProperty
        public LogicalOperation getOperation()
        {
            return _operation;
        }

        public void setOperation(LogicalOperation op)
        {
            _operation = op;
        }

        @XMLProperty
        public MatchMode getMatchMode()
        {
            return _matchMode;
        }

        public void setMatchMode(MatchMode mm)
        {
            _matchMode = mm;
        }

        /**
         * Utility to get the MatchMode.toMatchString value checking for null.
         */
        public String getMatchModeString(String value, String wild) {
            MatchMode mm = (_matchMode != null) ? _matchMode : MatchMode.ANYWHERE;
            return mm.toMatchString(value, wild);
        }

        @XMLProperty
        public String getProperty()
        {
            return _property;
        }

        public void setProperty(String property)
        {
            _property = property;
        }

        @XMLProperty
        public String getJoinProperty()
        {
            return _joinProperty;
        }

        public void setJoinProperty(String joinProperty)
        {
            _joinProperty = joinProperty;
        }

        public Object getValue()
        {
            return _value;
        }

        public void setValue(Object val)
        {
            _value = val;
        }
        
        public String getCast() {
            return _cast;
        }

        public void setCast(String _cast) {
            this._cast = _cast;
        }


        //
        // KLUDGE!!!  Can't tell the annotation-based XML serializer to use an
        // attribute based serialization if possible.  In order to make value
        // canonical when it is a string but an element when it is not a string
        // we have to introduce multiple getter/setter pairs just for XML
        // serialization.
        //
        @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="value")
        public String getValueXMLAttribute() {
            return (_value instanceof String) ? (String)_value : null;
        }
        public void setValueXMLAttribute(String value) {
            _value = value;
        }
        @XMLProperty(xmlname="Value")
        public Object getValueXMLElement() {
            return (_value instanceof String) ? null : _value;
        }
        public void setValueXMLElement(Object value) {
            if (_value == null) {
                _value = value;
            }
        }
        //
        // END KLUDGE!!
        //

        @XMLProperty
        public boolean isIgnoreCase()
        {
            return _ignoreCase;
        }

        public void setIgnoreCase(boolean ignoreCase)
        {
            _ignoreCase = ignoreCase;
        }

        @XMLProperty
        public CompositeFilter getCollectionCondition() {
            return _collectionCondition;
        }

        public void setCollectionCondition(CompositeFilter filter) {
            _collectionCondition = filter;
        }

        @XMLProperty
        public Class<?> getSubqueryClass() {
            return _subqueryClass;
        }

        public void setSubqueryClass(Class<?> clazz) {
            _subqueryClass = clazz;
        }

        @XMLProperty
        public String getSubqueryProperty() {
            return _subqueryProperty;
        }
        
        public void setSubqueryProperty(String property) {
            _subqueryProperty = property;
        }
        
        @XMLProperty
        public Filter getSubqueryFilter() {
            return _subqueryFilter;
        }

        public void setSubqueryFilter(Filter filter) {
            _subqueryFilter = filter;
        }
        
        public String toString()
        {
            return getExpression();
        }
        
        /**
         * Alternate getter for the string representation that can
         * be called from JSF.
         */
        public String getExpression() {
            FilterToExpressionVisitor ftsh = new FilterToExpressionVisitor();
            try {
                accept(ftsh);
            }
            catch (GeneralException e) {
                throw new RuntimeException(e);
            }
            return ftsh.getExpression();
        }

        public void accept(FilterVisitor visitor) throws GeneralException {

            // Most leaf filters key off of the operation.  However, subquery
            // filters can have different operations depending on the type of
            // comparison desired against the subquery results.  Use the
            // subquery class to determine this type of filter.
            if (null != _subqueryClass) {
                visitor.visitSubquery(this);
                return;
            }
            
            switch(getOperation())
            {
            case EQ: visitor.visitEQ(this); break;
            case NE: visitor.visitNE(this); break;
            case LT: visitor.visitLT(this); break;
            case GT: visitor.visitGT(this); break;
            case LE: visitor.visitLE(this); break;
            case GE: visitor.visitGE(this); break;
            case IN: visitor.visitIn(this); break;
            case CONTAINS_ALL: visitor.visitContainsAll(this); break;
            case LIKE: visitor.visitLike(this); break;
            case NOTNULL: visitor.visitNotNull(this); break;
            case ISNULL: visitor.visitIsNull(this); break;
            case ISEMPTY: visitor.visitIsEmpty(this); break;
            case JOIN: visitor.visitJoin(this); break;
            case LEFT_JOIN:visitor.visitLeftJoin(this); break;
            case COLLECTION_CONDITION: visitor.visitCollectionCondition(this); break;
            default:
                throw new GeneralException("Unknown type of this filter: " + this);
            }
        }
    }

    @XMLClass
    public static class CompositeFilter extends Filter
    {
        private static final long serialVersionUID = -2222994052543435736L;
        private BooleanOperation _operation;
        private List<Filter> _children;

        public CompositeFilter()
        {
        }

        public CompositeFilter(BooleanOperation operation,
                               Filter... children)
        {
            _operation = operation;
            _children = null;

            if (children != null) {
                _children = Arrays.asList(children);
                // jsl - this will be a list that can't be extended, copy
                // so we can call add
                _children = new ArrayList<Filter>(_children);
            }
        }

        public CompositeFilter(BooleanOperation operation, List<Filter> children)
        {
            _operation = operation;
            _children = children;
        }

        /**
         * Shallow copy for the optimizer.
         */
        public CompositeFilter(CompositeFilter src) {
            _operation = src._operation;
            if (src._children != null) {
                _children = new ArrayList<Filter>();
                for (Filter child : src._children)
                    _children.add(Filter.clone(child));
            }
        }

        @XMLProperty
        public BooleanOperation getOperation()
        {
            return _operation;
        }

        public void setOperation(BooleanOperation op)
        {
            _operation = op;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Filter> getChildren()
        {
            return _children;
        }

        public void setChildren(List<Filter> children)
        {
            _children = children;
        }

        public void add(Filter child) {
            if (child != null) {
                if (_children == null)
                    _children = new ArrayList<Filter>();
                _children.add(child);
            }
        }

        public String toString()
        {
            return getExpression();
        }

        /**
         * Alternate getter for the string representation that can
         * be called from JSF.
         */
        public String getExpression() {
            FilterToExpressionVisitor ftsh = new FilterToExpressionVisitor();
            try {
                accept(ftsh);
            }
            catch (GeneralException e) {
                throw new RuntimeException(e);
            }
            return ftsh.getExpression();
        }

        public void accept(FilterVisitor visitor) throws GeneralException {
            switch(getOperation())
            {
            case AND: visitor.visitAnd(this); break;
            case OR: visitor.visitOr(this); break;
            case NOT: visitor.visitNot(this); break;
            default:
                throw new GeneralException("Unknown type of composite filter: " + this);
            }
        }
    }

    /**
     * Visitor interface that is accepted by filters to perform various
     * operations. It is the responsibility of the visitor implementation to
     * iterate through children on composite operations.
     *
     * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
     */
    public static interface FilterVisitor
    {
        // Composite operations.
        public void visitAnd(CompositeFilter filter) throws GeneralException;
        public void visitOr(CompositeFilter filter) throws GeneralException;
        public void visitNot(CompositeFilter filter) throws GeneralException;

        // Leaf operations.
        public void visitEQ(LeafFilter filter) throws GeneralException;
        public void visitNE(LeafFilter filter) throws GeneralException;
        public void visitLT(LeafFilter filter) throws GeneralException;
        public void visitGT(LeafFilter filter) throws GeneralException;
        public void visitLE(LeafFilter filter) throws GeneralException;
        public void visitGE(LeafFilter filter) throws GeneralException;
        public void visitIn(LeafFilter filter) throws GeneralException;
        public void visitContainsAll(LeafFilter filter) throws GeneralException;
        public void visitLike(LeafFilter filter) throws GeneralException;
        public void visitNotNull(LeafFilter filter) throws GeneralException;
        public void visitIsNull(LeafFilter filter) throws GeneralException;
        public void visitIsEmpty(LeafFilter filter) throws GeneralException;
        public void visitJoin(LeafFilter filter) throws GeneralException;
        public void visitLeftJoin(LeafFilter filter) throws GeneralException;
        public void visitCollectionCondition(LeafFilter filter) throws GeneralException;
        public void visitSubquery(LeafFilter filter) throws GeneralException;
    }

    /**
     * Base implementation of a FilterVisitor that throws unsupported exceptions
     * for all operations.
     */
    public static class BaseFilterVisitor implements FilterVisitor
    {
        void throwUnsupported(Filter filter) throws GeneralException {
            throw new GeneralException("Filter " + filter.getExpression() + " not supported");
        }

        public void visitAnd(CompositeFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitOr(CompositeFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitNot(CompositeFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitEQ(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitNE(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitLT(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitGT(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitLE(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitGE(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitIn(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitContainsAll(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitLike(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitNotNull(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitIsNull(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitIsEmpty(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitJoin(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitLeftJoin(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitCollectionCondition(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
        public void visitSubquery(LeafFilter filter) throws GeneralException {
            throwUnsupported(filter);
        }
    }

    /**
     * Filter visitor that converts the filter to an Expression.
     *
     * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
     */
    @Untraced
    static class FilterToExpressionVisitor extends BaseFilterVisitor
    {
        static final String DATE_PREFIX = "DATE$";
        private StringBuilder builder = new StringBuilder();

        /**
         * Default constructor.
         */
        public FilterToExpressionVisitor() {}

        /**
         * Return the string value accumulated from all visited filters.
         */
        public String getExpression() {
            return this.builder.toString();
        }
        
        public void visitAnd(CompositeFilter filter) throws GeneralException {
            visitComposite(filter);
        }

        public void visitOr(CompositeFilter filter) throws GeneralException {
            visitComposite(filter);
        }

        private void visitComposite(CompositeFilter filter) throws GeneralException {
            List<Filter> children = filter.getChildren();
            if ((null != children) && !children.isEmpty()) {
                this.builder.append('(');

                String sep = "";
                for (Filter f : children) {
                    this.builder.append(sep);
                    f.accept(this);
                    sep = " " + filter.getOperation().getStringRepresentation() + " ";
                }

                this.builder.append(')');
            }
        }

        public void visitNot(CompositeFilter filter) throws GeneralException {
            List<Filter> children = filter.getChildren();
            if ((null == children) || (1 != children.size()))
                throw new GeneralException("NOT condition must have a single child.");

            this.builder.append('!');

            // Remember the index before the child has been into the buffer so
            // we can determine whether we need to surround it with parens.
            int idx = this.builder.length();
            children.get(0).accept(this);

            if ('(' != this.builder.charAt(idx)) {
                this.builder.insert(idx, '(');
                this.builder.append(')');
            }
        }

        // Leaf operations.
        public void visitEQ(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitNE(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitLT(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitGT(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitLE(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitGE(LeafFilter filter)      { visitComparisonOp(filter); }
        public void visitNotNull(LeafFilter filter) { visitUnaryOp(filter); }
        public void visitIsNull(LeafFilter filter)  { visitUnaryOp(filter); }
        public void visitIsEmpty(LeafFilter filter)  { visitUnaryOp(filter); }

        private void visitComparisonOp(LeafFilter filter) {
            // Encode the property name to make it a valid java identifier.
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty())).append(' ');
            if (filter.isIgnoreCase())
                this.builder.append('i');
            this.builder.append(filter.getOperation().getStringRepresentation());
            this.builder.append(' ').append(valueToString(filter.getValue()));
        }
        
        private void visitUnaryOp(LeafFilter filter) {
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty())).append('.');

            String op = null;

            switch (filter.getOperation()) {
            case ISNULL: op = "isNull()"; break;
            case NOTNULL: op = "notNull()"; break;
            case ISEMPTY: op = "isEmpty()"; break;
            default:
                throw new RuntimeException("Unexpected unary operation: " + filter.getOperation());
            }

            this.builder.append(op);
        }

        private static Object valueToString(Object o)
        {
            if (o instanceof String)
            {
                StringBuilder sb = new StringBuilder((String) o);
                for (int i=0; i<sb.length(); i++)
                {
                    char c = sb.charAt(i);
                    if (('"' == c) || ('\\' == c))
                    {
                        sb.insert(i, '\\');
                        i++;
                    }
                }
                return "\"" + sb.toString() + "\"";
            } 
            else if (o instanceof Enum) 
            {
                StringBuffer buf = new StringBuffer();
                buf.append(o.getClass().getName());

                // If an enum value has an implementation, it will have a
                // numbered class name.  If we find this, strip it off.
                // For example, ContinuousState.CertificationRequired will look
                // like this when converted to a string:
                //
                //   sailpoint.object.AbstractCertificationItem$ContinuousState$2
                //
                // Don't hate the player ... hate the game!
                int lastDollar = buf.lastIndexOf("$");
                if ((lastDollar > -1) && (buf.length() >= lastDollar+1)) {
                    boolean stripIt = false;
                    String rest = buf.substring(lastDollar+1);
                    try {
                        Integer.parseInt(rest);
                        stripIt = true;
                    }
                    catch (NumberFormatException nfe) { /* Not a number */ }

                    // We found an enum inner class, chop it off.
                    if (stripIt) {
                        buf.setLength(lastDollar);
                    }
                }

                int replaceChar = buf.indexOf("$");
                buf.replace(replaceChar, replaceChar + 1, ".");
                buf.append(".");
                buf.append(o.toString());
                
                o = buf.toString();
            }
            else if (o instanceof Date)
            {
             StringBuffer buf = new StringBuffer(DATE_PREFIX);
             buf.append(((Date)o).getTime());
             o = buf.toString();
            }
            
            return o;
        }

        public void visitLike(LeafFilter filter) {
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            switch(filter.getMatchMode())
            {
            case START:
                this.builder.append(".startsWith");
                break;
            case END:
                this.builder.append(".endsWith");
                break;
            case ANYWHERE:
                this.builder.append(".contains");
                break;
            }
            if (filter.isIgnoreCase())
                this.builder.append("IgnoreCase");
            this.builder.append("(").append(valueToString(filter.getValue())).append(")");
        }

        public void visitIn(LeafFilter filter)          { visitListFilter(filter); }
        public void visitContainsAll(LeafFilter filter) { visitListFilter(filter); }

        private void visitListFilter(LeafFilter filter) {
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            Object value = filter.getValue();
            if ((value instanceof Iterable) || (value instanceof Object[]))
            {
                if (LogicalOperation.IN.equals(filter.getOperation()))
                    this.builder.append(".in");
                else
                    this.builder.append(".containsAll");

                if (filter.isIgnoreCase())
                    this.builder.append("IgnoreCase");

                this.builder.append("({");

                String sep = "";
                if (value instanceof Iterable)
                {
                    for (Object current : (Iterable) value)
                    {
                        this.builder.append(sep);
                        this.builder.append(valueToString(current));
                        sep = ", ";
                    }
                }
                else if (value instanceof Object[])
                {
                    for (Object current : (Object[]) value)
                    {
                        this.builder.append(sep);
                        this.builder.append(valueToString(current));
                        sep = ", ";
                    }
                }
                this.builder.append("})");
            }
            else
                throw new RuntimeException("An 'in' filter must have an array or collection value.");
        }

        public void visitJoin(LeafFilter filter) {
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            this.builder.append(".join(");
            this.builder.append(Util.encodeJavaIdentifier(filter.getJoinProperty()));
            this.builder.append(")");
        }

        public void visitLeftJoin(LeafFilter filter) {
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            this.builder.append(".leftJoin(");
            this.builder.append(Util.encodeJavaIdentifier(filter.getJoinProperty()));
            this.builder.append(")");
        }

        public void visitCollectionCondition(LeafFilter filter) {

            // Escape any double quotes in the condition string.
            String conditionExpression = (String)filter.getCollectionCondition().getExpression();
            conditionExpression = conditionExpression.replace("\"", "\\\"");

            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            this.builder.append(".collectionCondition(\"");
            this.builder.append(conditionExpression);
            this.builder.append("\")");
        }

        public void visitSubquery(LeafFilter filter) {

            // Escape any double quotes in the condition string.
            Filter subfilter = filter.getSubqueryFilter();
            String subquery = (null != subfilter) ? subfilter.getExpression() : null;
            if (null != subquery) {
                subquery = subquery.replace("\"", "\\\"");
            }

            // Consider encoding the operation once we allow changing this.
            this.builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
            this.builder.append(".subquery(");
            this.builder.append(Util.encodeJavaIdentifier(filter.getSubqueryClass().getName()));
            this.builder.append(", \"");
            this.builder.append(Util.encodeJavaIdentifier(filter.getSubqueryProperty()));
            this.builder.append("\", ");

            if (null != subquery) {
                this.builder.append("\"").append(subquery).append("\"");
            }
            else {
                this.builder.append("null");
            }

            this.builder.append(")");
        }
    }

    /**
    * A compiler that can create a Filter from a string representation using the
    * following grammar (note the Java-like syntax):
    * <pre>
    * String literals should have double-quotes.
    * true/false are treated as boolean literals
    * digits are treated as numbers
    * the string value 'null' (no quotes) is treated as null
    * fully-qualified constants are resolved to enums
    * everything else is assumed to be the property name
    *
    * Leafs:
    *  Any comparison operator can be prepended with an 'i' to signify a
    *  case-insensitive comparison (for example - i==, i!=, etc...).
    *
    *  EQ - propertyName == value
    *  NE - propertyName != value
    *  LT - propertyName < value 
    *  GT - propertyName > value
    *  LE - propertyName <= value
    *  GE - propertyName >= value
    *  IN - propertyName.in({ "foo", "bar", "baz" }) (or inIgnoreCase())
    *  CONTAINS_ALL - propertyName.containsAll({ "foo", "bar", "baz" })
    *                 (or containsAllIgnoreCase())
    *  ISNULL - propertyName.isNull()
    *  NOTNULL - propertyName.notNull()
    *  ISEMPTY - propertyName.isEmpty()
    *  
    *  LIKE
    *    - EXACT - propertyName == value
    *    - START - propertyName.startsWith(value) (or startsWithIgnoreCase())
    *    - END - propertyName.endsWith(value) (or endsWithIgnoreCase())
    *    - ANYWHERE - propertyName.contains(value) (or containsIgnoreCase())
    *
    *  JOIN - propertyName.join(ClassName.propertyName)
    *  LEFT_JOIN - propertyName.leftJoin(ClassName.propertyName)
    *  
    *  COLLECTION_CONDITION -
    *    propertyName.collectionCondition("fooProp == \"bar\"")
    *    
    *    Note that the parameter to collectionCondition() is the string
    *    representation (with quotes escaped) of the collection element filter.
    *
    *  SUBQUERY
    *    propertyName.subquery("firstname", sailpoint.object.Bundle, "name", "riskScoreWeight > 500");
    *    
    *    A subquery takes the following parameters:
    *     - property
    *     - subquery class
    *     - subquery property
    *     - subquery filter: Either a string representation of a filter (with
    *       quotes escaped) or null.
    *
    * Composites:
    *  AND - (expr && expr)
    *  OR - (expr || expr)
    *  NOT - !(expr)
    * </pre>
    * .
    */
    public static class FilterCompiler
    {
        private Parser p;
        
        public Filter compileFilter(String filter) throws Parser.ParseException
        {
            p = new Parser(filter);

            Filter filterObj = compileFilter();

            if (!p.parseEOS())
                throw new Parser.ParseException("Expected end-of-string", p);

            return filterObj;
        }

        private Filter compileFilter()
        {
            return compileConditionalOrFilter();
        }

        private Filter compileConditionalOrFilter()
        {
            Filter filter = compileConditionalAndFilter();
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(filter);

            while (p.parseString("||")) {
                filters.add(compileConditionalAndFilter());
            }
            if ( filters.size() > 1 ) {
                filter = Filter.or(filters);
            }

            return filter;
        }

        private Filter compileConditionalAndFilter()
        {
            Filter filter = compileNotFilter();
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(filter);

            while (p.parseString("&&")) {
                filters.add(compileNotFilter());
            }
            if ( filters.size() > 1 ) {
                filter = Filter.and(filters);
            }
            return filter;
        }

        private Filter compileNotFilter()
        {
            Filter filter;

            if (p.parseChar('!'))
                filter = Filter.not(compilePrimary());
            else
                filter = compilePrimary();

            return filter;
        }

        private Filter compilePrimary()
        {
            // Three types of primaries:
            //  - Paren grouping of other expressions
            //  - property.function(value)
            //  - property OPERATOR value
            Filter filter = null;

            // Type #1: Paren grouping.
            if (p.parseChar('('))
            {
                filter = compileFilter();

                if (!p.parseChar(')'))
                    throw new Parser.ParseException("Expected ')'", p);

                return filter;
            }

            IdentitiferLookAhead lookAhead = new IdentitiferLookAhead();
            String property = p.parseIdentifier(true, lookAhead);
            if (null == property)
                throw new Parser.ParseException("Expected either a parenthetical grouping or comparison.", p);

            property = Util.decodeJavaIdentifier(property);

            // A dot can either access a function or nested property.
            while (p.parseChar('.'))
            {
                String identifier = p.parseIdentifier(true, lookAhead);
                if (null == identifier)
                    throw new Parser.ParseException("Expected identifier after '.'", p);

                identifier = Util.decodeJavaIdentifier(identifier);

                // Type #2: Function Reference.
                if (p.parseChar('('))
                {
                    if ("startsWith".equals(identifier))
                        filter = compileMatchingFilter(property, MatchMode.START);
                    else if ("startsWithIgnoreCase".equals(identifier))
                        filter = Filter.ignoreCase(compileMatchingFilter(property, MatchMode.START));
                    else if ("endsWith".equals(identifier))
                        filter = compileMatchingFilter(property, MatchMode.END);
                    else if ("endsWithIgnoreCase".equals(identifier))
                        filter = Filter.ignoreCase(compileMatchingFilter(property, MatchMode.END));
                    else if ("contains".equals(identifier))
                        filter = compileMatchingFilter(property, MatchMode.ANYWHERE);
                    else if ("containsIgnoreCase".equals(identifier))
                        filter = Filter.ignoreCase(compileMatchingFilter(property, MatchMode.ANYWHERE));
                    else if ("in".equals(identifier))
                        filter = compileInFilter(property);
                    else if ("inIgnoreCase".equals(identifier))
                        filter = Filter.ignoreCase(compileInFilter(property));
                    else if ("containsAll".equals(identifier))
                        filter = compileContainmentFilter(property, LogicalOperation.CONTAINS_ALL);
                    else if ("containsAllIgnoreCase".equals(identifier))
                        filter = Filter.ignoreCase(compileContainmentFilter(property, LogicalOperation.CONTAINS_ALL));
                    else if ("join".equals(identifier))
                        filter = compileJoin(property);
                    else if ("leftJoin".equals(identifier))
                        filter = compileLeftJoin(property);
                    else if ("isNull".equals(identifier))
                        filter = Filter.isnull(property);
                    else if ("notNull".equals(identifier))
                        filter = Filter.notnull(property);
                    else if ("isEmpty".equals(identifier))
                        filter = Filter.isempty(property);
                    else if ("collectionCondition".equals(identifier))
                        filter = compileCollectionCondition(property);
                    else if ("subquery".equals(identifier))
                        filter = compileSubquery(property);
                    else
                        throw new Parser.ParseException("Unknown function: " + identifier, p);

                    if (!p.parseChar(')'))
                        throw new Parser.ParseException("Expected ')' after function.", p);

                    return filter;
                }
                else
                {
                    // If not a function, then we'll assume this is a nested property.
                    property += "." + identifier;
                }
            }

            // Type #3: property OPERATOR value
            LogicalOperation op = null;
            boolean ignoreCase = false;
            if (p.parseString("=="))
                op = LogicalOperation.EQ;
            else if (p.parseString("!="))
                op = LogicalOperation.NE;
            else if (p.parseString(">="))
                op = LogicalOperation.GE;
            else if (p.parseString("<="))
                op = LogicalOperation.LE;
            else if (p.parseString(">"))
                op = LogicalOperation.GT;
            else if (p.parseString("<"))
                op = LogicalOperation.LT;
            else if (p.parseString("i=="))
            {
                op = LogicalOperation.EQ;
                ignoreCase = true;
            }
            else if (p.parseString("i!="))
            {
                op = LogicalOperation.NE;
                ignoreCase = true;
            }
            else if (p.parseString("i>="))
            {
                op = LogicalOperation.GE;
                ignoreCase = true;
            }
            else if (p.parseString("i<="))
            {
                op = LogicalOperation.LE;
                ignoreCase = true;
            }
            else if (p.parseString("i>"))
            {
                op = LogicalOperation.GT;
                ignoreCase = true;
            }
            else if (p.parseString("i<"))
            {
                op = LogicalOperation.LT;
                ignoreCase = true;
            }
            // NOTE - If you add a new operator, add it to the array
            // IdentifierLookAhead.OPERATORS.
            else if (null == (filter = compileLegacyUnaryOperation(property)))
                throw new Parser.ParseException("Expected operator", p);

            // Only create a filter if we didn't parse a legacy unary operation.
            if (null == filter) {
                filter = new LeafFilter(op, property, compileLiteral());
                if (ignoreCase)
                    filter = Filter.ignoreCase(filter);
            }

            return filter;
        }

        /**
         * Identifiers can have ambiguity because they can contain spaces. To
         * figure out if a space should be a part of the identifier or not, 
         * look ahead to see if the next part of the string is something
         * we expect in the grammar after an identifier - either a dot (for a
         * dotted property), a parenthesis (when using an identifier as a
         * function name), or an operator.
         */
        public static class IdentitiferLookAhead extends BaseLookAhead {

            private static final String[] OPERATORS =
                new String[] { "==", ">", "<", "!=",
                               "i==", "i>", "i<", "i!=",
                               "ISEMPTY", "ISNULL", "NOTNULL" };

            public boolean isAmbiguous(char c) {
                return (' ' == c);
            }

            public boolean continueParsingInternal(Parser p) {

                if (p.parseChar('.') || p.parseChar('('))
                    return false;

                for (int i=0; i<OPERATORS.length; i++) {
                    if (p.parseString(OPERATORS[i])) {
                        return false;
                    }
                }

                return true;
            }
        }
        
        private Filter compileLegacyUnaryOperation(String property) {

            LogicalOperation op = null;

            if (p.parseString("ISEMPTY")) {
                op = LogicalOperation.ISEMPTY;
            }
            else if (p.parseString("ISNULL")) {
                op = LogicalOperation.ISNULL;
            }
            else if (p.parseString("NOTNULL")) {
                op = LogicalOperation.NOTNULL;
            }

            return (null != op) ? new LeafFilter(op, property, null) : null;
        }


        private Object compileLiteral()
        {
            Object literal;

            if ((literal = p.parseLiteral()) != null)
                return literal;
            else if ((literal = compileArrayLiteral(false)) != null)
                return literal;
            else if (p.parseNullLiteral())
                return null;
            else
                throw new Parser.ParseException("Expected literal value.", p);
        }

        private Filter compileMatchingFilter(String property, MatchMode mode)
        {
            return new LeafFilter(LogicalOperation.LIKE, property, compileLiteral(), mode);
        }

        private Filter compileContainmentFilter(String property, LogicalOperation op)
        {
            return new LeafFilter(op, property, compileArrayLiteral(true));
        }

        private Filter compileInFilter(String property)
        {
            return in(property, compileArrayLiteral(true));
        }

        private List compileArrayLiteral(boolean failIfNotFound)
        {
            List<Object> list = new ArrayList<Object>();

            if (!p.parseChar('{'))
                if (failIfNotFound)
                    throw new Parser.ParseException("Expected '{' to start array literal.", p);
                else
                    return null;

            if (!p.parseChar('}'))
            {
                do
                {
                    list.add(compileLiteral());
                } while (p.parseChar(','));

                if (!p.parseChar('}'))
                    throw new Parser.ParseException("Expected '}' to end array literal.", p);
            }

            return list;
        }

        private Filter compileJoin(String property) {
            String joinProperty = compileDottedClassIdentifier();
            return Filter.join(property, joinProperty);
        }

        private Filter compileLeftJoin(String property) {
            String joinProperty = compileDottedClassIdentifier();
            return Filter.leftJoin(property, joinProperty);
        }

        private String compileDottedClassIdentifier() {
            String val = p.parseDottedClassIdentifier();
            if (null == val) {
                throw new Parser.ParseException("Expected a dotted class identifier.", p);
            }
            return Util.decodeJavaIdentifier(val);
        }

        private Filter compileCollectionCondition(String property) {
            String conditionString = p.parseStringLiteral();
            if (null == conditionString) {
                throw new Parser.ParseException("Expected a collection condition string.", p);
            }

            Filter collectionCond = Filter.compile(conditionString);

            // If compiling the condition collapsed a single-element AND,
            // restore the structure since it is important with collection
            // conditions.  See bug #5788.
            collectionCond =
                expandCollapsedComposite(collectionCond, conditionString);

            // Bug 13347 - The collectionCond may end up something like:
            //    (exceptionApplication ==\"Oracle_DB_oasis\")")
            // which is still pretty leafy.  If leafy, compositorize:
            if (collectionCond instanceof LeafFilter) {
                collectionCond = Filter.and(collectionCond);
            }
            return Filter.collectionCondition(property, collectionCond);
        }

        /**
         * Determine whether the given condition that was compiled from the
         * given string had nested composites collapsed - if so then expand it
         * back out.
         * 
         * There is no great way of showing a composite AND with a single
         * element when converting toExpression().  This shows up with double
         * parens and no "&&".  Usually, this is fine because getting rid of the
         * nested composite does not hurt anything. However, collection
         * conditions expect each composite element in the collection condition
         * to serve as a logical grouping, so they are important here.
         */
        private Filter expandCollapsedComposite(Filter collectionCond,
                                                String conditionString) {
            Filter filter = collectionCond;
            
            // A collapsed composite will start with double parens.  For
            // example, ((a == b && c == d)).  This should turn into:
            //
            // Filter.and(Filter.and(Filter.eq("a", "b"),
            //                       Filter.eq("c", "d")))
            //
            // A collapsed composite produces this (which is incorrect for a
            // collection condition):
            //
            // Filter.and(Filter.eq("a", "b"),
            //            Filter.eq("c", "d"))
            if (conditionString.startsWith("((") &&
                (collectionCond instanceof CompositeFilter)) {

                // IIQETN-6624: We used to exclude nested composites here, but
                // having a composite inside a collection condition is a valid
                // scenario.
                // i.e.: Filter.and(Filter.eq("a", "b"),
                //                  Filter.or(Filter.eq("c", "d"), Filter.eq("c", "e")))
                filter = Filter.and(collectionCond);
            }

            return filter;
        }
        
        private Filter compileSubquery(String property) {

            Class<?> clazz = p.parseFullyQualifiedClass();
            if (null == clazz) {
                throw new Parser.ParseException("Expected a full qualified class.", p);
            }

            if (!p.parseChar(',')) {
                throw new Parser.ParseException("Expected a comma between class name and subquery property.", p);
            }

            String subqueryProperty = p.parseStringLiteral();
            if (null == subqueryProperty) {
                throw new Parser.ParseException("Expected a subquery property.", p);
            }

            if (!p.parseChar(',')) {
                throw new Parser.ParseException("Expected a comma between subquery property and subquery filter.", p);
            }

            String subqueryString = p.parseStringLiteral();
            if (null == subqueryString) {
                if (!p.parseNullLiteral()) {
                    throw new Parser.ParseException("Expected a subquery filter string or null.", p);
                }
            }

            Filter subfilter = null;
            if (null != subqueryString) {
                subfilter = Filter.compile(subqueryString);
            }
            return Filter.subquery(property, clazz, subqueryProperty, subfilter);
        }
    }
}
