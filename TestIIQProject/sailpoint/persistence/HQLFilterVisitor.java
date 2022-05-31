/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.CollectionType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import sailpoint.object.Filter;
import sailpoint.object.Filter.BaseFilterVisitor;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;


/**
 * A FilterVisitor that can build HQL queries.
 *  
 * @author Kelly Grizzle
 */
class HQLFilterVisitor
    extends BaseFilterVisitor
    implements HQLJoinHandler {

    static private Log log = LogFactory.getLog(HQLFilterVisitor.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * An "order by" entry for a query.
     */
    private static class Order {
        private String property;
        private boolean ascending;
        private boolean ignoreCase;
        
        public Order(String property, boolean ascending, boolean ignoreCase) {
            this.property = property;
            this.ascending = ascending;
            this.ignoreCase = ignoreCase;
        }
        
        public Order(String property, boolean ascending) {
            this.property = property;
            this.ascending = ascending;
            ignoreCase = false;
        }

        public boolean isAscending() {
            return ascending;
        }

        public String getProperty() {
            return property;
        }
        
        public boolean isIgnoreCase() {
            return this.ignoreCase;
        }
    }

    /**
     * A join across a given property.  This contains informational properties
     * about the structure of the join (class, property, type), as well as
     * calculated properties that are used when converting the join to HQL
     * (alias, joinPath).  Before accessing the latter, you must call
     * configure().  This should be called when the join is first created.
     */
    static class Join {
        public static enum Type {
            INNER("inner join"),
            LEFT_OUTER("left outer join");

            private String hql;
            
            private Type(String hql) {
                this.hql = hql;
            }

            public String getHQL() {
                return this.hql;
            }
        }

        private Class<?> clazz;
        private String property;
        private Type type;

        private String alias;
        private String joinPath;
        private String onCondition;

        /**
         * Constructor.
         * 
         * @param clazz     The root of the joined property.  This should be
         *                  null unless the property has an explicit class
         *                  reference (eg - Identity.scorecard).
         * @param property  The property of the join.
         * @param type      The type of join.
         */
        public Join(Class<?> clazz, String property, Type type) {
            this.clazz = clazz;
            this.property = property;
            this.type = type;
        }

        public String getProperty() {
            return property;
        }
        
        public Type getType() {
            return type;
        }

        public String getAlias() {
            if (null == this.alias) {
                throwNotConfigured("getAlias()");
            }
            return this.alias;
        }

        public String getJoinPath() {
            if (null == this.joinPath) {
                throwNotConfigured("getJoinPath()");
            }
            return this.joinPath;
        }

        public String getOnCondition() {
            if (null == this.onCondition) {
                throwNotConfigured("getOnCondition()");
            }
            return this.onCondition;
        }

        public boolean isOnConditionConfigured()  {
            return null != this.onCondition;
        }

        public Class<?> getJoinClass() {
            return this.clazz;
        }

        private void throwNotConfigured(String method) throws IllegalStateException {
            throw new IllegalStateException("Cannot call " + method + " for " + calculateFullJoinProperty() + " until join is configured.");
        }

        /**
         * Call this to configure the join when it is being added to this
         * visitor.  It is important to call this early (ie - when the join is
         * added) rather than later (ie - when the join is added to the HQL).
         * This is to ensure that the alias substitution is able to appropriately
         * choose the correct alias for the property being joined.  See bug 7539
         * for more details.
         * 
         * @param  joinAlias     The alias for this join.
         * @param  aliasContext  The HQLAliasContext to use to substitute any
         *                       aliases in the join path.
         */
        public void configure(String joinAlias, HQLAliasContext aliasContext) {
            this.alias = joinAlias;
            
            // Calculate join path using the alias context.
            this.joinPath = aliasContext.substituteAlias(this.calculateFullJoinProperty());
        }

        /**
         * Call this to configure teh join to use the "ON" condition as part of the Join clause
         * instead of relying on the "WHERE" condition.
         * @param onCondition The string condition for the ON clause of this join
         */
        public void configureOnCondition(String onCondition) {
            this.onCondition = onCondition;
        }

        /**
         * Return the full join property (ie - "classname.property" if this join
         * has a class, or just "property" otherwise) for this join.
         */
        private String calculateFullJoinProperty() {
            String prop = this.property;
            if (null != this.clazz) {
                String className = this.clazz.getName();
                prop = className.substring(className.lastIndexOf('.')+1) + "." + prop;
            }
            return prop;
        }
    }

    /**
     * This is a helper class to escape filter values that may contain
     * literal search strings.  _reserved is an array of strings that are not
     * allowed in HQL and SQL.  _escapes are possible escape strings to use.
     */
    private static class Escaper {
        private final String[] _reserved;
        
        // HQL escape sequences can only be one character
        private static final String[] _escapes = {"@","~","#","|","!","^"};
        
        private int _currentEsc = 0;
        private boolean _escapeNeeded = false;
        
        Escaper( DatabaseCapabilities capabilities ) {
            _currentEsc = 0;
            _escapeNeeded = false;
            _reserved = capabilities.getReservedQueryStrings();
        }
        
        public String getEscapeString() {
            return _escapes[_currentEsc];
        }
        
        public boolean needsEscape(String s) {
            return _escapeNeeded;
        }
        
        /**
         * Find an escape string that does not exist in a given string.
         * This returns the escape string, but the class also stores
         * the index of the current escape string.  If an escape string
         * could not be found, it returns null.
         * 
         * A possible improvement to this is that if you have many strings
         * that you need to find a unique escape for, this wouldn't work.
         * The loop here would need to start at index _currentEsc and iterate
         * from there, instead of from index 0.
         */
        public String findEscapeString(String s) {
            int i = 0;
            for(String escape : _escapes) {
                _currentEsc = i;
                if (s.indexOf(escape) < 0) {
                    return escape;
                    // use this escape string
                }
                
                i++;
            }
            
            // couldn't find an escape string
            return null;
        }
        
        /**
         * Escape the strings listed in the reserved values.  The String with the
         * reserved strings escaped is returned.
         */
        public String escapeReserved(String s) {
            String newString = s;
            for (String restrict : _reserved) {
                newString = newString.replace(restrict, getEscapeString() + restrict);
            }
            
            if(newString.equals(s)) {
                _escapeNeeded = false;
            } else {
                _escapeNeeded = true;
            }
            
            return newString;
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private HQLAliasContextStack aliasContext;
    private SessionFactory sessionFactory;
    private Class<?> queryClass;
    private ClassMappingUtil.ClassMapping classMapping;
    private List<String> importPackages;
    private DatabaseCapabilities databaseCapabilities;
    private HQLJoinHandler joinHandler;

    private boolean distinct;
    private boolean autoIgnoreCase;
    private List<String> selectColumns;
    private List<Class<?>> fromClasses;
    private Map<Class<?>,List<Join>> joins;
    private StringBuilder conditionBuilder;
    private StringBuilder havingConditionBuilder;
    private int havingDepth;
    private StringBuilder savedConditionBuilder;
    private List<Order> orderBys;
    private List<String> groupBys;

    private Map<String,Object> parameterMap;

    // Used to uniquely name parameters.
    private int parameterStartIdx;

    private String newAliasContextForCompositeProperty;

    // Part of the from clause that contains the theta style
    // join classes and aliases
    private StringBuilder thetaJoinBuilder;

    // Since we have to join the same class multiple times with
    // some collection conditions keep a counter
    private int thetaJoinAliasInstance;

    // Set to true if we are in a collection condition on an external table.
    private boolean thetaJoinCollectionCondition;
    
    private Escaper escaper;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     * 
     * @param  queryClass    The class that is being queried over.
     * @param  capabilities  The database capabilities.
     */
    public HQLFilterVisitor(Class<?> queryClass, DatabaseCapabilities capabilities,
                            SessionFactory factory)
        throws GeneralException {

        if (null == queryClass) {
            throw new IllegalArgumentException("Query class must be specified in constructor");
        }
        if (null == capabilities) {
            throw new IllegalArgumentException("DatabaseCapabilities must be specified in constructor");
        }
        if (null == factory) {
            throw new IllegalArgumentException("SessionFactory must be specified in constructor.");
        }

        this.queryClass = queryClass;
        this.classMapping = ClassMappingUtil.getClassMapping(queryClass);
        if (this.classMapping == null) {
            throw new GeneralException("Unable to find ClassMapping for " + queryClass.getSimpleName());
        }
        this.databaseCapabilities = capabilities;
        this.sessionFactory = factory;

        this.selectColumns = new ArrayList<String>();
        this.fromClasses = new ArrayList<Class<?>>();
        this.joins = new HashMap<Class<?>,List<Join>>();
        this.orderBys = new ArrayList<Order>();
        this.groupBys = new ArrayList<String>();
        this.parameterMap = new HashMap<String,Object>();
        this.conditionBuilder = new StringBuilder();
        this.havingConditionBuilder = new StringBuilder();
        this.havingDepth = 0;

        // Default to the object package.
        this.importPackages = new ArrayList<String>();
        this.importPackages.add("sailpoint.object");

        // Add the initial from class to the from statement.
        String defaultAlias = addFrom(queryClass);

        // Create the alias context stack.
        HQLAliasContext ctx = new HQLAliasContextImpl(queryClass, queryClass, defaultAlias,
                                                      this, this.importPackages,
                                                      this.sessionFactory);
        this.aliasContext = new HQLAliasContextStack(ctx);

        this.thetaJoinBuilder = new StringBuilder();
        this.thetaJoinCollectionCondition = false;
        this.thetaJoinAliasInstance = 0;
        
        this.escaper = new Escaper( capabilities );
    }     


    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Set whether to make the query return distinct results.
     * 
     * @param  distinct  Whether to make the query return distinct results.
     */
    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    /**
     * Return whether the query returns distinct results.
     * 
     * @return Truf if the query should return distinct results, false otherwise.
     */
    public boolean isDistinct() {
        return this.distinct;
    }

    /**
     * Set whether to make the query case insensitive.
     * 
     * @param  ignoreCase  True to make the query case insensitive.
     */
    public void setIgnoreCase(boolean ignoreCase) {
        this.autoIgnoreCase = ignoreCase;
    }

    /**
     * Return whether the query is case insensitive.
     * 
     * @return True if the query is case insensitive.
     */
    public boolean isIgnoreCase() {
        return this.autoIgnoreCase;
    }

    /**
     * Set the columns to return in the query.  If not set, this defaults to the
     * objects that are being searched on.
     * 
     * @param  columns  The list of columns to select.
     */
    public void setSelectColumns(List<String> columns) {
        this.selectColumns = columns;
    }

    /**
     * Add ordering by the given property.
     */
    public void addOrderBy(String property, boolean ascending, boolean ignoreCase) {
        if(databaseCapabilities != null && !databaseCapabilities.isCaseInsensitive()) {
            this.orderBys.add(new Order(property, ascending, ignoreCase));
        } else {
            this.orderBys.add(new Order(property, ascending));
        }
                
    }
    
    public void addOrderBy(String property, boolean ascending) {
        this.orderBys.add(new Order(property, ascending));
    }

    public void setGroupBys(List<String> groupBys) {
        this.groupBys = groupBys;
    }
    
    /**
     * Build a string we can use for bulk deletes via the 
     * BulkDeletePersistenceManager. Most of this code
     * was borrowed from the getQueryString method since there 
     * is a lot of commonality. 
     * 
     * @return  The HQL delete string.
     */
    public String getDeleteString() {

        StringBuilder b = new StringBuilder();

        b.append("delete ");
        addFromAndJoins(b);
        addWhereClause(b);

        return b.toString();
    }

    /**
     * Get the HQL query string from this visitor.  The visitor should already
     * have been accepted by the Filter by the time this is called.
     * 
     * @return  The HQL query string.
     */
    public String getQueryString() {

        // Before we start, check to see if we're ordering by any properties
        // that are not yet being joined into the query.  If we are, then this
        // will add outer joins to the order by properties.  This needs to be
        // called before we add the from/joins.
        addOrderByJoins();

        // If this is a distinct projection query, we need to make sure that all
        // order by columns are being selected.
        addOrderBySelectsForDistinct();

        StringBuilder b = new StringBuilder();

        b.append("select ");
        addSelectColumns(b);
        addFromAndJoins(b);
        addWhereClause(b);
        addGroupBy(b);
        addOrderBy(b);

        return b.toString();
    }

    /**
     * Return whether this query has any outer joins.
     */
    private boolean hasOuterJoins() {

        for (Class<?> clazz : this.fromClasses) {
            List<Join> joins = this.joins.get(clazz);
            if (null != joins) {
                for (Join join : joins) {
                    if (Join.Type.LEFT_OUTER.equals(join.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Add the select columns HQL to the given StringBuilder.
     */
    private void addSelectColumns(StringBuilder b) {

        // Only add the DISTINCT if the database supports it for this query.
        // If not, we'll rely on the DistinctRootEntityTransformer to do the
        // duplicate filtering after the query has been made.
        if (this.distinct && this.databaseCapabilities.canUseDistinct(this.selectColumns)) {
            b.append("distinct ");
        }

        // If there aren't any select columns, default to the query class.
        if ((null == this.selectColumns) || selectColumns.isEmpty()) {
            String col = this.aliasContext.getDefaultAlias();
            b.append(col);
        }
        else {
            String sep = "";
            for (String col : this.selectColumns) {
                String countDistinctProp = null;
                
                // If the query is a count, add a distinct on the queryClass alias so that an individual
                // queryClass instance will only return one row, ie when using multiple outer joins. 
                // Otherwise substitute an alias (note that this will handle other aggregate functions).
                if (-1 < col.indexOf("count(*)")){
                    // As an optimization, only apply the "distinct" if the query has outer joins.
                    String distinctStr = (this.distinct || this.hasOuterJoins()) ? "distinct " : "";
                    b.append(sep).append(" count(" + distinctStr + this.aliasContext.getDefaultAlias()  + ") ");
                }
                // Special case for counting distinct values of a property.
                else if (null != (countDistinctProp = getCountDistinctProperty(col))) {
                    b.append(sep).append(" count(distinct " + this.aliasContext.substituteAlias(countDistinctProp) + ") ");
                }
                else {
                    // A non-count aggregate or normal property - use an outer join to
                    // make sure that selecting a projection column does not filter the
                    // results if the selected column is null.
                    b.append(sep).append(this.aliasContext.substituteAlias(col, false, true));
                }

                sep = ", ";
            }
        }
    }

    /**
     * If the given column in the form of "count(distinct attr)", return attr.
     */
    private static String getCountDistinctProperty(String col) {
        String prop = null;
        final String PREFIX = "count(distinct";
        if (col.startsWith(PREFIX)) {
            prop = col.substring(PREFIX.length(), col.length()-1).trim();
        }
        return prop;
    }
    
    /**
     * Add the HQL for the from clause with joins (and theta joins) to the given
     * StringBuilder.
     */
    private void addFromAndJoins(StringBuilder b) {
        
        if (this.fromClasses.isEmpty()) {
            throw new RuntimeException("Invalid query: no classes to select from");
        }

        b.append(" from ");
        String sep = "";
        for (Class<?> clazz : this.fromClasses) {
            b.append(sep).append(clazz.getName());
            String alias = this.aliasContext.getAlias(clazz);
            if (null != alias) { 
                b.append(" ").append(alias);
            }

            addJoinInner(b, clazz);

            sep = ", ";
        }

        if ( this.thetaJoinBuilder.length() > 0 ) 
            b.append(sep).append(thetaJoinBuilder);
    }

    /**
     * Add the join to the string builder, handling both inner and outer joins with on clauses.
     * This may optionally recurse to add related joins for classes not in FROM list
     */
    private void addJoinInner(StringBuilder b, Class clazz) {
        List<Join> joins = this.joins.get(clazz);
        if (null != joins) {
            for (Join join : joins) {
                b.append(" ").append(join.getType().getHQL());

                if (!join.isOnConditionConfigured()) {
                    b.append(" ").append(join.getJoinPath());
                    b.append(" ").append(join.getAlias());
                } else {
                    b.append(" ").append(join.getJoinClass().getName());
                    b.append(" ").append(this.aliasContext.getAlias(join.getJoinClass()));
                    b.append(" on ").append(join.getOnCondition());

                    // Joins using ON clauses will not necessarily have their classes included in the FROM classes, but
                    // could have joins against them (for example, if the join class is Identity and one of the properties is Identity.manager)
                    // So in that case add the joins for the class here to make sure they are included.
                    if (!clazz.equals(join.getJoinClass()) && !this.fromClasses.contains(join.getJoinClass())) {
                        addJoinInner(b, join.getJoinClass());
                    }
                }
            }
        }
    }
    
    /**
     * Add the HQL for the where clause and conditions to the given
     * StringBuilder.
     */
    private void addWhereClause(StringBuilder b) {
        
        if (0 != this.conditionBuilder.length()) {
            b.append(" where ").append(this.conditionBuilder);
        }
    }
    
    /**
     * Add the HQL for the group by clause to the given StringBuilder.
     */
    private void addGroupBy(StringBuilder b) {

        if ((null != this.groupBys) && !this.groupBys.isEmpty()) {
            b.append(" group by ");
            String sep = "";
            for (String prop : this.groupBys) {
                b.append(sep).append(this.aliasContext.substituteAlias(prop));
                sep =", ";
            }

            if (this.havingConditionBuilder.length() > 0) {
                b.append(" having ").append(this.havingConditionBuilder);
            }
        }
        else if (this.havingConditionBuilder.length() > 0) {
            throw new RuntimeException("A 'group by' is required for conditions " +
                                       "that use aggregates.");
        }
    }
    
    /**
     * Add the HQL for the order by clause to the given StringBuilder.
     */
    private void addOrderBy(StringBuilder b) {

        if (!this.orderBys.isEmpty()) {
            b.append(" order by ");
            String sep = "";
            for (Order order : this.orderBys) {

                if (databaseCapabilities != null && !databaseCapabilities.isCaseInsensitive()) {
                    // Originally I wanted to auto-detect ignore case like we do for things
                    // in the where clause, but order by doesn't work that way.  Order by columns
                    // have to appear in the select list, it doesn't matter how they are indexed.
                    // Since I doubt anyone ever selects upper(foo) I'm wondering why we even have
                    // case sensitivity options for order by? - jsl
                    b.append(sep).append(getCasedProperty(this.aliasContext.substituteAlias(order.getProperty()), order.isIgnoreCase()));
                }
                else {
                    // database is case insensitive
                    b.append(sep).append(this.aliasContext.substituteAlias(order.getProperty()));
                }
                
                if (!order.isAscending()) {
                    b.append(" desc");
                }
                sep =", ";
            }
        }
    }
    
    /**
     * Get the parameter map for all of the variable bindings generated while
     * visiting the filter.  The visitor should already have been accepted by
     * the Filter by the time this is called.
     * 
     * @return The parameter map with all of the variable binding values for the
     *         query.
     */
    public Map<String,Object> getParameterMap() {
        return this.parameterMap;
    }

    /**
     * Return true if the results span multiple tables and make it possible for
     * duplicates to be returned.
     * 
     * @return True if the results span multiple tables and make it possible for
     *         duplicates to be returned.
     */
    public boolean spansMultipleTables() {
        return (this.fromClasses.size() + this.joins.size()) > 1;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // UNIT TEST ONLY!
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Only used by the unit tests.  Set another join handler to be called when
     * joins are added.
     */
    public void setJoinHandler(HQLJoinHandler handler) {
        this.joinHandler = handler;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Iterate over the "order by" statements and outer join any properties that
     * aren't already joined in.
     */
    private void addOrderByJoins() {

        if (null != this.orderBys) {
            for (Order order : this.orderBys) {
                this.aliasContext.substituteAlias(order.getProperty(), false, true);
            }
        }
    }

    /**
     * Iterate over the "order by" statements and select from any properties
     * that aren't selected if this is a distinct projection query.
     */
    private void addOrderBySelectsForDistinct() {

        // Only do this for distinct projection queries.  I think that
        // non-projection queries should handle this already.
        if (this.distinct && (null != this.orderBys) &&
            (null != this.selectColumns) && !this.selectColumns.isEmpty()) {

            // Short-circuit this if the property is in the count distinct query.
            if ((1 == this.selectColumns.size()) &&
                (null != getCountDistinctProperty(this.selectColumns.get(0)))) {
                return;
            }
            
            for (Order order : this.orderBys) {
                if (!this.selectColumns.contains(order.getProperty())) {
                    this.selectColumns.add(order.getProperty());
                }
            }
        }
    }

    /**
     * Add a new class to select from.
     */
    private String addFrom(Class<?> clazz) {

        if (!this.fromClasses.contains(clazz)) {
            this.fromClasses.add(clazz);
        }
        return setClassAlias(clazz,null);
    }

    private String setClassAlias(Class<?> clazz, Integer i) {
        String alias = null;
        String className = getClassName(clazz);
        StringBuilder b = new StringBuilder(className + "Alias");
        if ( i != null )  {
            b.append(i);
        }
        b.setCharAt(0, Character.toLowerCase(b.charAt(0)));
        alias = b.toString();

        if (null != this.aliasContext) {
            this.aliasContext.setClassAlias(clazz, alias);
        }
        return alias;
    }

    /**
     * Add a new JOIN to the given class in the from statement.
     */
    public String addJoin(Class<?> clazz, Join join) {

        // Get or create the joins for this class.
        List<Join> joinsForClass = this.joins.get(clazz);
        if (null == joinsForClass) {
            joinsForClass = new ArrayList<Join>();
            this.joins.put(clazz, joinsForClass);
        }

        // Create an alias.
        String className = getClassName(clazz);
        className += "_" + join.getProperty();
        className = className.replace('.', '_');
        StringBuilder b = new StringBuilder(className + "Alias");
        b.setCharAt(0, Character.toLowerCase(b.charAt(0)));

        int numJoins = 0;
        for (Join current : joinsForClass) {
            if (join.getProperty().equals(current.getProperty())) {
                numJoins++;
            }
        }
        b.append(numJoins);

        // Add the join to the list.
        joinsForClass.add(join);

        // Configure the join.  This sets calculated properties - the join path
        // and the alias.
        String alias = b.toString();
        join.configure(alias, this.aliasContext.getCurrent());

        // If another join handler is registered, let it have a crack, too.
        if (null != this.joinHandler) {
            this.joinHandler.addJoin(clazz, join);
        }
        
        return alias;
    }

    /**
     * Get the short name of a class (ie - Identity, instead of
     * sailpoint.object.Identity).
     */
    private static String getClassName(Class<?> clazz) {
        String className = clazz.getName();
        int lastDotIdx = className.lastIndexOf('.');
        if (-1 != lastDotIdx) {
            className = className.substring(lastDotIdx+1);
        }
        return className;
    }

    /**
     * If the given property is a class reference, return the referenced class.
     * A property is considered a class reference if it starts with a capital
     * letter and is dotted.  If a class cannot be loaded and this looks like a
     * class referenced, an exception is thrown.
     * 
     * @param  prop  The property from which to try to pull the class (eg -
     *               Identity.name);
     */
    static Class<?> getClassFromProperty(String prop, List<String> imports) {

        if (null == prop)
            throw new IllegalArgumentException("Expected a non-null property");

        Class<?> clazz = null;

        int idx = prop.indexOf('.');
        String className = (-1 != idx) ? prop.substring(0, idx) : prop;

        if (Character.isUpperCase(className.charAt(0))) {
            for (String pkg : imports) {
                String fullClass = pkg + "." + className;
                try {
                    clazz = Class.forName(fullClass);
                    // If we were able to load it, jump out of the loop.
                    break;
                }
                catch (ClassNotFoundException e) {
                    // Ignore ... we'll blow chunks at the end if the class
                    // isn't found in any of the import package.
                }
            }

            if (null == clazz) {
                throw new RuntimeException("Could not find class for join property " +
                                           className + " in packages " + imports);
            }
        }

        return clazz;
    }

    /**
     * Check to see if the property will require a unique join alias.
     * A unique join is ok most of the time except when we are dealing
     * with properties that are class references (i.e. Identity.id ) or
     * that are non-dotted properties ( i.e. id )
     */
    static boolean requiresUniqueJoin(String prop ) {
        boolean uniqueJoin = true;
        if ( prop != null ) {
            // we don't want unique joins for class References or for any 
            // non-dotted properties ( i.e. Identity.id or just id )
            int count = Util.countChars(prop, '.');
            if ( ( count == 0 ) || 
                 ( ( Character.isUpperCase(prop.charAt(0)) ) && ( count == 1 ) ) )  {
                uniqueJoin = false;                            
            }                     
        }
        return uniqueJoin;
    } 

    /**
     * Return true if case insensitivity applies to this filter.
     * This is used to adjust the rendering of both the property name and value.
     *
     * First the database must be known to be case sensitive.
     * Then either the filter must have the ignoreCase flag or
     * the global ignoreCase flag is set in this class (via QueryOptions).
     * Next the value must be a String.
     *
     * The final check for a String value is necessary for autoIgnoreCase
     * since we don't want to apply this to Date or Integer values.
     *
     * !! We may want to be smarter about blob columns.
     */
    private boolean isIgnoreCase(LeafFilter filter, String prop, Object value) {

        boolean ignore = false;

        // removing this so we can log warnings for case mismatch when using mysql - jsl
        //if (!this.databaseCapabilities.isCaseInsensitive() && 

        // the id kludge is because just about all SearchInputDefinitions have
        // ignoreCase="true", even on id columns
        if (!prop.equals("id") &&
            !prop.endsWith(".id") &&
            (value instanceof String)) {
            
            ClassMappingUtil.PropertyMapping pmap = this.classMapping.getProperty(prop);
            if (pmap == null) {
                // Set ignoreCase to true for most common ending properties like name, displayName
                // these are often used in join paths which aren't yet supported here
                if (prop.endsWith(".name") || prop.endsWith(".displayName")) {
                    ignore = true;
                }
                else {
                    // this can happen with some complex .hbm.xml files, have to
                    // trust the Filter
                    ignore = (autoIgnoreCase ||  filter.isIgnoreCase());
                }
            }
            else if (pmap.isCaseInsensitive()) {
                if (!autoIgnoreCase && !filter.isIgnoreCase()) {
                    // this is expected once people start forgetting
                    // about Filter.ignoreCase but I want to see where this
                    // is happening for awhile
                    // actually , it happens all the time in the unit tests so leave it off
                    if (log.isInfoEnabled()) {
                        log.info("Filter.ignoreCasse missing for case insensitive index: " + 
                                 this.classMapping.name + " " + prop);
                    }
                }
                ignore = true;
            }
            else if (autoIgnoreCase || filter.isIgnoreCase()) {
                // code explicitly asked for this but there is no ci index
                if (pmap.mappingIndex) {
                    // there is a normal index defined, this is an error in the
                    // code or the index definition that needs to be investigated
                    // ignore the ignoreCase so the index can be used
                    log.warn("Filter.ignoreCase used with a case sensitive index: " +
                             this.classMapping.name + " " + prop);
                }
                else if (pmap.annotationIndex) {
                    // this one is more troublesome because the column could be part
                    // of a composite index which wouldn't end up being used,
                    // log a warning here too since someone needs to figure out why
                    // we have a composite index that doesn't match, but obey the ignoreCase
                    log.warn("Filter.ignoreCase used with a case sensitive annotation index: " +
                             this.classMapping.name + " " + prop);
                    ignore = true;
                }
                else {
                    // If there is no normal index, we can proceed with upper(value) unindexed
                    // and it will work as expected
                    ignore = true;
                }
            }
        }

        // ignore what we calculated if we know the db is insensitive
        if (this.databaseCapabilities.isCaseInsensitive())
            ignore = false;
        
        return ignore;
    }

    /**
     * Return the property name surroned by upper() if we're ignoring case.
     */
    private String getCasedProperty(String prop, boolean ignoreCase) {

        if (ignoreCase)
            prop = "upper(" + prop + ")";

        return prop;
    }

    /**
     * Make any changes to the search value in order to normalize the queries.
     */
    private Object getProcessedValue(LeafFilter filter, Object val, boolean ignoreCase) {
        if (val instanceof String) {
            val = getCasedValue(filter, val, ignoreCase);
            val = getEscapedValue(filter, val);
            val = getLikeSyntax(filter, val);
        }
        return val;
    }
    
    /**
     * Apply ignore-case to the given value if it is a string.
     * 
     * This contains some special handling for the eszett character ß.  Java
     * toUpperCase always converts it to SS, no matter the locale.  Case-sensitive
     * databases do an UPPER on the data to try to match.  However, UPPER in the
     * databases don't modify this value, so a user will never get a match if
     * they are specifically searching for ß.   There are some obscure settings
     * that can make a difference.  Since Oracle requires the use of NLS_UPPER
     * to even have a chance of a locale specific uppercase and our db scripts
     * use UPPER, we'll do this for now.
     * 
     * As far as I know there is a Turkish character that will also have this
     * problem:
     * lower case:  i    upper case:  İ
     * lower case:  ı    upper case:  I
     */
    private Object getCasedValue(LeafFilter filter, Object val, boolean ignoreCase) {

        if (ignoreCase) {
            // "\u00DF" == "ß".  IndexOf doesn't find ß, OMG.
            if (((String) val).indexOf("\u00DF") > -1) {
                // I'm not proud of this.
                val = ((String) val).replace("\u00DF", "\u9999");
                val = ((String) val).toUpperCase();
                val = ((String) val).replace("\u9999", "\u00DF");
            } else {
                val = ((String) val).toUpperCase();
            }
        }
        return val;
    }
    
    /**
     * Escape characters that are meant to be literals.
     */
    private Object getEscapedValue(LeafFilter filter, Object val) {
        if (Filter.LogicalOperation.LIKE.equals(filter.getOperation())) {
            
            // Find an escape string and make sure one exists.
            if (null == escaper.findEscapeString((String)val)) {
                // Could not find an escape string
                return val;
            }
            
            // prepend escape string to reserved strings.
            // This also sets needsEscape.
            val = escaper.escapeReserved((String) val);
        }
        
        return val;
    }
    
    /**
     * Apply "like" syntax (percent sign) to the value appropriately.
     * 
     */
    private Object getLikeSyntax(LeafFilter filter, Object val) {
        // Add some % signs for a LIKE comparison.
        if (Filter.LogicalOperation.LIKE.equals(filter.getOperation())) {
            switch(filter.getMatchMode())
            {
            case EXACT:
                break;
            case START:
                val = val + "%"; break;
            case END:
                val = "%" + val; break;
            default:
                // assume anywhere if null
                val = "%" + val + "%"; break;
            }
        }

        return val;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FILTER VISITOR IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * If a filter has an aggregate property, we will automatically put it into
     * the HAVING clause rather than the normal WHERE condition.  This method
     * will detect if the given filter is an aggregate, and if so switch the
     * condition builder around so that the resulting conditions are added to
     * the HAVING clause.  After each filter is visited, revertConditionBuilder()
     * should be called to get back to the normal state.
     */
    private void switchConditionBuilder(LeafFilter filter) {

        // Check if this looks like an aggregate.
        if (filter.getProperty().indexOf('(') > -1) {

            // Only switch the condition builders if we haven't already done so.
            // This can happen if nested composite filters use having conditions.
            if (this.havingDepth < 1) {
                this.savedConditionBuilder = this.conditionBuilder;
                this.conditionBuilder = this.havingConditionBuilder;
            }
            
            // If there is already stuff in the HAVING condition, AND it.
            if (this.havingConditionBuilder.length() > 0) {
                this.havingConditionBuilder.append(" and ");
            }

            this.havingDepth++;
        }
    }

    /**
     * Switch the WHERE and HAVING condition builders back to their previous
     * states if we had swapped them to visit an aggregate condition.
     */
    private void revertConditionBuilder() {
        if (null != this.savedConditionBuilder) {
            this.havingDepth--;

            // If we're back to the top of the having stack, switch the
            // condition builders back.
            if (0 == this.havingDepth) {
                this.conditionBuilder = this.savedConditionBuilder;
                this.savedConditionBuilder = null;
            }
        }
    }
    
    private void visitComparison(LeafFilter filter, String op) {
        this.visitComparison(filter, op, filter.getValue(), false);
    }

    private void visitComparison(LeafFilter filter, String op, Object value,
                                 boolean forceUniqueJoin) {

        switchConditionBuilder(filter);

        String prop = filter.getProperty();
        boolean ignoreCase = isIgnoreCase(filter, prop, value);

        String alias = this.aliasContext.substituteAlias(prop, forceUniqueJoin, false);
        alias = getCasedProperty(alias, ignoreCase);

        String param = getParameterName();
        value = getProcessedValue(filter, value, ignoreCase);
        /** cast it if the filter is for an extended attribute that has a type **/
        if(filter.getCast()!=null) {
            String append = new String(" cast("+alias+" as "+filter.getCast()+ ") "+op+" :"+param);
            this.conditionBuilder.append(append);
        } else {
            this.conditionBuilder.append(alias).append(" ").append(op).append(" :").append(param);
        }
        
        this.conditionBuilder.append(addEscapeClause(filter, value));
        
        this.parameterMap.put(param, value);

        revertConditionBuilder();
    }
    
    /**
     * Add return an escape clause only if it needs it.
     */
    private String addEscapeClause(LeafFilter filter, Object value) {
        String escapeClause = "";
        if (Filter.LogicalOperation.LIKE.equals(filter.getOperation()) && value instanceof String) {
            // needsEscape depends on the last time escape.escapeReserved() was run
            if (escaper.needsEscape((String) value)) {
                escapeClause = " escape '" + escaper.getEscapeString() + "'";
            }
        }
        
        return escapeClause;
    }

    private String getParameterName() {
        return "param" + (this.parameterStartIdx + this.parameterMap.size());
    }

    @Override
    public void visitEQ(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, "=");
    }

    @Override
    public void visitGE(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, ">=");
    }

    @Override
    public void visitGT(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, ">");
    }

    @Override
    public void visitLE(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, "<=");
    }

    @Override
    public void visitLT(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, "<");
    }

    @Override
    public void visitNE(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, "!=");
    }

    @Override
    public void visitLike(LeafFilter filter) throws GeneralException {
        this.visitComparison(filter, "like");
    }

    @Override
    public void visitNotNull(LeafFilter filter) throws GeneralException {
        switchConditionBuilder(filter);
        this.conditionBuilder.append(this.aliasContext.substituteAlias(filter.getProperty())).append(" is not null");
        revertConditionBuilder();
    }

    @Override
    public void visitIsNull(LeafFilter filter) throws GeneralException {
        switchConditionBuilder(filter);
        this.conditionBuilder.append(this.aliasContext.substituteAlias(filter.getProperty())).append(" is null");
        revertConditionBuilder();
    }

    @Override
    public void visitIsEmpty(LeafFilter filter) throws GeneralException {
        switchConditionBuilder(filter);
        this.conditionBuilder.append("size(");
        this.conditionBuilder.append(this.aliasContext.substituteAlias(filter.getProperty()));
        this.conditionBuilder.append(") = 0");
        revertConditionBuilder();
    }

    @Override
    public void visitIn(LeafFilter filter) throws GeneralException {

        switchConditionBuilder(filter);

        Object val = filter.getValue();
        
        // Entities and scalars are handled differently in HQL.
        if ((val instanceof SailPointObject) ||
            CriteriaFilterVisitor.isCollectionOfType(SailPointObject.class, val)) {
            visitEntityIn(filter);
        }
        else {
            visitScalarIn(filter);
        }

        revertConditionBuilder();
    }

    /**
     * Visit an in() filter where the value has entities.  This converts the
     * in() filter to a set of OR'd together equality filters.
     */
    private void visitEntityIn(LeafFilter filter) throws GeneralException {

        Object val = filter.getValue();
        List<Filter> eqFilters = new ArrayList<Filter>();

        if (val instanceof Iterable) {
            for (Object current : (Iterable<?>) val) {
                eqFilters.add(Filter.eq(filter.getProperty(), current));
            }
        }
        else if (val instanceof Object[]) {
            for (Object current : (Object[]) val) {
                eqFilters.add(Filter.eq(filter.getProperty(), current));
            }
        }
        else {
            eqFilters.add(Filter.eq(filter.getProperty(), val));
        }

        // If there is only one filter visit it singly, otherwise OR the filters
        // together and visit the OR.
        if (eqFilters.size() == 1) {
            this.visitEQ((LeafFilter) eqFilters.get(0));
        }
        else if (eqFilters.size() > 1) {
            Filter or = Filter.or(eqFilters);
            this.visitOr((CompositeFilter) or);
        }
    }

    /**
     * For "in" filters derive the value we want to use when checking
     * for case insensntivity mutations.
     */
    private Object getPotentialCasedValue(Object value) {

        Object caseval = null;

        if (value instanceof Iterable) {
            // sigh, try not to generate Iterator garbage for the usual case
            if (value instanceof List) {
                List<?> l = (List<?>)value;
                if (l.size() > 0)
                    caseval = l.get(0);
            }
            else {
                Iterator<?> it = ((Iterable<?>)value).iterator();
                if (it.hasNext())
                    caseval = it.next();
            }
        }
        else if (value instanceof Object[]) {
            Object[] array = (Object[])value;
            if (array.length > 0)
                caseval = array[0];
        }

        return caseval;
    }
    
    private boolean isScalarCollection(String property) throws HibernateException {
        boolean isScalarCollection = false;
        if(this.sessionFactory!=null) {
            ClassMetadata cmd = sessionFactory.getClassMetadata(this.queryClass);
            Type propType = cmd.getPropertyType(property);
            if (propType instanceof CollectionType) {
                Type eltType = ((CollectionType) propType).getElementType((SessionFactoryImplementor)sessionFactory);
                isScalarCollection = !(eltType instanceof EntityType);
            }
        }
        return isScalarCollection;
    }

    /**
     * Visit an in() filter where the value has scalars.  This uses the HQL "in"
     * expression.
     */
    private void visitScalarIn(LeafFilter filter) {

        String prop = filter.getProperty();
        Object val = filter.getValue();

        // see if we can apply case insensitivity
        boolean ignoreCase = isIgnoreCase(filter, prop, getPotentialCasedValue(val));

        String alias = null;
        
        /* We need to determine if this is a scalar collection.  If it is, we
         * need to create a unique join
         */
        boolean isScalarCollection = false;
        try {
            isScalarCollection = isScalarCollection(filter.getProperty());
        } catch (HibernateException e) {
            // do nothing; let it alias as normal
        }
        
        // scalar collection properties need to force a join; others don't
        alias = this.aliasContext.substituteAlias(prop, isScalarCollection, false);
        alias = getCasedProperty(alias, ignoreCase);

        this.conditionBuilder.append(alias).append(" in (");

        if ((val instanceof Iterable) || (val instanceof Object[])) {
            if (val instanceof Iterable) {
                String sep = "";
                for (Object current : (Iterable<?>) val) {
                    this.conditionBuilder.append(sep);
                    addScalarInValue(filter, current, ignoreCase);
                    sep = ", ";
                }
            }
            else if (val instanceof Object[]) {
                String sep = "";
                for (Object current : (Object[]) val) {
                    this.conditionBuilder.append(sep);
                    addScalarInValue(filter, current, ignoreCase);
                    sep = ", ";
                }
            }
        }
        else {
            // The value is not a collection, handle gracefully.
            addScalarInValue(filter, val, ignoreCase);
        }

        this.conditionBuilder.append(")");
    }

    private void addScalarInValue(LeafFilter filter, Object val, boolean ignoreCase) {
        
        String param = getParameterName();
        val = getProcessedValue(filter, val, ignoreCase);

        this.conditionBuilder.append(':').append(param);
        
        this.conditionBuilder.append(addEscapeClause(filter, val));
        
        this.parameterMap.put(param, val);
    }
    
    @Override
    public void visitContainsAll(LeafFilter filter) throws GeneralException {

        Object val = filter.getValue();

        // Use a conjunction to AND together some eq's if the value being
        // compared against is a set of values.
        if ((val instanceof Iterable) || (val instanceof Object[])) {
            if (val instanceof Iterable) {
                String sep = "";
                for (Object current : (Iterable<?>) val) {
                    this.conditionBuilder.append(sep);
                    this.visitComparison(filter, "=", current, true);
                    sep = " and ";
                }
            }
            else if (val instanceof Object[]) {
                String sep = "";
                for (Object current : (Object[]) val) {
                    this.conditionBuilder.append(sep);
                    this.visitComparison(filter, "=", current, true);
                    sep = " and ";
                }
            }
        }
        else {
            // The value is not a collection, handle gracefully with a
            // simple equality check.
            this.visitComparison(filter, "=", val, true);
        }
    }

    @Override
    public void visitJoin(LeafFilter filter) throws GeneralException {
 
        // Extract the class from the join property and add it to the FROM
        // list - this will add an alias for it.
        String joinProp = filter.getJoinProperty();
        Class<?> clazz = getClassFromProperty(joinProp, this.importPackages);
   
        if (!this.thetaJoinCollectionCondition) {
            addFrom(clazz);

            handleJoinConditionBuilderInternal(filter, joinProp, this.conditionBuilder);
        } else {
            // In this case we may have to add more then once instance of 
            // the alias so we don't want to add it to the from clause
            setClassAlias(clazz, thetaJoinAliasInstance++);

            // thetaJoinBuilder will be added to the from clause 
            // in getQueryString
            String classAlias = this.aliasContext.getAlias(clazz);
            if ( this.thetaJoinBuilder.length() > 0  ) {
                this.thetaJoinBuilder.append(",");
            }
            this.thetaJoinBuilder.append(clazz.getName());
            this.thetaJoinBuilder.append(" ").append(classAlias);

            // Use the parent HQLAliasContext to resolve the property that we're
            // joining to, since this will be a reference from the parent class.
            String alias1 = substituteAliasWithAncestors(filter.getProperty());
            
            // The join property will be within the collection condition's
            // alias context.
            String alias2 = this.aliasContext.substituteAlias(joinProp);

            // add the theta style join to the where clause
            this.conditionBuilder.append(alias1).append(" = ").append(alias2);
        } 
    }

    @Override
    public void visitLeftJoin(LeafFilter filter) throws GeneralException {
        String joinProp = filter.getJoinProperty();
        Class<?> clazz = getClassFromProperty(joinProp, this.importPackages);

        // Make sure we have an alias set up for this class since we wont be adding to FROM list
        if (this.aliasContext.getAlias(clazz) == null) {
            setClassAlias(clazz, null);
        }

        // Create a standard outer join, we will further configure this later to add "ON" condition
        String prop = joinProp.substring(joinProp.indexOf(".") + 1);
        Join join = new Join(clazz, prop, Join.Type.LEFT_OUTER);
        addJoin(this.queryClass, join);

        // Create condition string
        StringBuilder onConditionBuilder = new StringBuilder();
        handleJoinConditionBuilderInternal(filter, joinProp, onConditionBuilder);

        // Set our join to use "ON" condition, this will be handled differently when building HQL string
        join.configureOnCondition(onConditionBuilder.toString());

        // TODO: Does this need to handle being inside a collection condition (i.e. thetaJoinCollectionCondition)?  Punt for now.
    }

    /**
     * Given the filter and join property, append the appropriate condition to the given builder
     */
    private void handleJoinConditionBuilderInternal(LeafFilter filter, String joinProp, StringBuilder conditionBuilder) {
        String alias1 = this.aliasContext.substituteAlias(filter.getProperty());
        String alias2 = this.aliasContext.substituteAlias(joinProp);

        /* We need to determine if this is a scalar collection.  If it is, we'll
         * join using the 'in elements' operation instead of a regular '=' join
         */
        boolean isScalarCollection = false;
        try {
            isScalarCollection = isScalarCollection(filter.getProperty());
        } catch (HibernateException e) {
            // do nothing; let it join as normal
        }
        if(isScalarCollection) {
            conditionBuilder.append(alias2).append(" in elements(").append(alias1).append(")");
        } else {
            //add the theta style join to the where clause
            conditionBuilder.append(alias1).append(" = ").append(alias2);
        }
    }

    /**
     * Return the substituted alias for the given property.  This will attempt
     * to resolve the alias walking through the HQLAliasContextStack - starting
     * with the parent - until a non-null looking alias is found.  This will
     * allow theta-joins from external attribute collection conditions to
     * resolve aliases from classes joined in at the top level of the the query.
     */
    private String substituteAliasWithAncestors(String prop)
        throws GeneralException {
        
        String alias = null;
        List<HQLAliasContext> popped = new ArrayList<HQLAliasContext>();

        // Jump up one level to start with.
        HQLAliasContext current = this.aliasContext.getCurrent();
        this.aliasContext.pop();
        popped.add(current);
        
        // Walk up the ancestor hierarchy until we find a good one or run out of
        // contexts to look at.
        try {
            do {
                current = this.aliasContext.getCurrent();
                boolean uniqueJoin = requiresUniqueJoin(prop);
                alias = current.substituteAlias(prop, uniqueJoin, false);
                this.aliasContext.pop();
                popped.add(current);
            }
            while ((null == alias) || (alias.indexOf("null") != -1));
        }
        catch (Exception e) {
            // If we get to the top of the stack without anything, we can get
            // an EmptyStackException.  Just throw again.
            throw new GeneralException(e);
        }
        finally {
            // Restore the stack.
            Collections.reverse(popped);
            for (HQLAliasContext pushMe : popped) {
                this.aliasContext.push(pushMe);
            }
        }

        return alias;
    }
    
    @Override
    public void visitSubquery(LeafFilter filter) throws GeneralException {

        switchConditionBuilder(filter);
        
        String prop =
            this.aliasContext.substituteAlias(filter.getProperty(), false, false);

        this.addSubqueryCondition(prop, filter.getOperation().toString(),
                                  filter.getSubqueryClass(),
                                  filter.getSubqueryProperty(),
                                  filter.getSubqueryFilter());

        revertConditionBuilder();
    }
    
    @Override
    public void visitCollectionCondition(LeafFilter filter) throws GeneralException {

        // Save the property name on the visitor, so that when the composite is
        // visited we will create a new HQLAliasContext.
        this.newAliasContextForCompositeProperty = filter.getProperty();

        // If the collection condition is something like LinkExtendedAttribute which 
        // references an external table, do special stuff 
        if ( ( this.newAliasContextForCompositeProperty != null ) &&
             ( !this.newAliasContextForCompositeProperty.contains(".") && 
             ( Character.isUpperCase(this.newAliasContextForCompositeProperty.charAt(0)) ) ) ) {
            this.thetaJoinCollectionCondition = true;
        }

        // Split the collection condition into NOT filters and non-NOT filters.
        // NOT filters will get special treatment with "NOT IN", while non-NOT
        // filters will not get special treatment.
        Filter.CompositeFilter nonNots =
            splitFilters(filter.getCollectionCondition(), false);
        if (null != nonNots) {
            nonNots.accept(this);
        }

        // Now, handle the NOT filters with a NOT IN subselect.  See bug 1927
        // for more information.
        Filter.CompositeFilter nots =
            splitFilters(filter.getCollectionCondition(), true);
        if (null != nots) {
            // Only need to add the conjuct if the nonNots added some conditions.
            if (null != nonNots) {
                String conjunct = null;
                switch (filter.getCollectionCondition().getOperation()) {
                case AND:
                case NOT:
                    conjunct = " and "; break;
                case OR:
                    conjunct = " or "; break;
                default:
                    throw new GeneralException("Uknown operation: " + filter);
                }
                this.conditionBuilder.append(conjunct);
            }
            handleNotInSubquery(filter.getProperty(), nots);
        }
        this.thetaJoinCollectionCondition = false;
        this.newAliasContextForCompositeProperty = null;
    }

    /**
     * Split the given composite filter so that we return only NOT filters or
     * non-NOT filters based on the onlyNots parameter.  If we are returning
     * only NOTs, we also inverse the boolean logic because the NOT is getting
     * pulled into the NOT IN statement.
     * 
     * @param  cond      The condition to split into NOTs or non-NOTs.
     * @param  onlyNots  Whether to only get the NOT conditions.
     * 
     * @return Either the non-NOTs or the inverse of the NOTs.  This returns
     *         null if there are no NOTs/non-NOTs if onlyNots is true/false.
     */
    private Filter.CompositeFilter splitFilters(Filter.CompositeFilter cond, boolean onlyNots) {
        
        Filter.CompositeFilter composite = null;

        if (null != cond) {

            if (onlyNots && isNotFilter(cond)) {
                // Return the inverse of a not - an OR.
                composite = (Filter.CompositeFilter) Filter.or(cond.getChildren());
            }
            else {
                if (!onlyNots && isNotFilter(cond)) {
                    return null;
                }

                List<Filter> filters = new ArrayList<Filter>();
                if (null != cond.getChildren()) {
                    for (Filter child : cond.getChildren()) {
                        if (onlyNots && isNotFilter(child)) {
                            filters.add(getInverseLeafFilterForNot(child));
                        }
                        else if (!onlyNots && !isNotFilter(child)) {
                            filters.add(child);
                        }
                    }

                    if (!filters.isEmpty()) {
                        Filter.BooleanOperation op = cond.getOperation();

                        // If we're getting NOTs, we need to flip the boolean
                        // operator since we're flipping the logical operators
                        // or the filters.  For example,
                        //
                        //   (a != 'foo' && b != 'bar')
                        //
                        //  turns into:
                        //
                        //   !(a == 'foo' || b == 'bar')
                        //
                        if (onlyNots) {
                            if (Filter.BooleanOperation.AND.equals(op)) {
                                op = Filter.BooleanOperation.OR;
                            }
                            else if (Filter.BooleanOperation.OR.equals(op)) {
                                op = Filter.BooleanOperation.AND;
                            }
                            else {
                                throw new RuntimeException("No inverse operation for: " + op);
                            }
                        }

                        composite = new Filter.CompositeFilter(op, filters);
                    }
                }
            }
        }
        
        return composite;
    }
    
    /**
     * Return true if the given filter is a NOT condition of some sort.
     */
    private boolean isNotFilter(Filter filter) {

        if (filter instanceof Filter.CompositeFilter) {
            return Filter.BooleanOperation.NOT.equals(((Filter.CompositeFilter) filter).getOperation());
        }
        else if (filter instanceof Filter.LeafFilter) {
            Filter.LeafFilter leaf = (Filter.LeafFilter) filter;
            return Filter.LogicalOperation.NE.equals(leaf.getOperation()) ||
                   Filter.LogicalOperation.NOTNULL.equals(leaf.getOperation());
        }
        else if (null != filter) {
            throw new RuntimeException("Unknown filter type: " + filter);
        }

        return false;
    }

    /**
     * Return the inverse of the given leaf filter if there is one.
     */
    private Filter getInverseLeafFilterForNot(Filter f) {
        
        Filter inverse = null;

        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter leaf = (Filter.LeafFilter) f;
            Filter.LogicalOperation op = leaf.getOperation().getInverseOperation();
            if (null == op) {
                throw new RuntimeException("Cannot handle a filter without an inverse");
            }

            inverse = new Filter.LeafFilter(op, leaf.getProperty(), leaf.getValue(), leaf.getMatchMode());
            ((LeafFilter)inverse).setIgnoreCase(leaf.isIgnoreCase());
        }
        else if (f instanceof Filter.CompositeFilter) {
            // This is a Filter.not() - if it has a single child, invert it by
            // removing the enclosing NOT.
            List<Filter> children = ((Filter.CompositeFilter) f).getChildren(); 
            if ((null != children) && (1 == children.size())) {
                inverse = children.get(0);
            }
            else {
                throw new RuntimeException("Can only handle a not with a single child here.");
            }
        }
        else {
            throw new RuntimeException("Unknown filter type");
        }

        return inverse;
    }
    
    /**
     * Convert the given subquery filters into a NOT IN subquery searching over
     * the given collection.
     * 
     * @param  collectionProperty  The property on the query class that holds
     *                             the collection to execute the subquery over.
     * @param  subqueryFilters     The filters for the subquery.
     * 
     * @throws GeneralException If the collection doesn't have a bi-directional
     *                          association back to the owning object. 
     */
    private void handleNotInSubquery(String collectionProperty, Filter subqueryFilters)
        throws GeneralException {

        // IIQCB-2893: In subqueries with a NOT there are situations when the collection property
        // includes a prefix with the name of the query class, for those cases we need to remove the prefix
        // to avoid issues with reflection. The change in the value of the property happens temporarily in this
        // method without affecting the original value in the filterleaf.
        String prefix = this.queryClass.getSimpleName() + ".";

        if(collectionProperty.startsWith(prefix)) {
            collectionProperty = collectionProperty.replace(prefix, "");
        }

        // Query over the list element's class.
        Class<?> listElementType =
            Reflection.getListElementType(this.queryClass, collectionProperty);

        // Select the inverse property - the property on the collection element
        // that points back to the parent.  If this isn't available, we'll puke.
        String inverseProp = getInverseProperty(this.queryClass, collectionProperty);

        this.addSubqueryCondition(this.aliasContext.getDefaultAlias(), "not in",
                                  listElementType, inverseProp, subqueryFilters);
    }

    /**
     * Add the given subquery to the condition builder.
     * 
     * @param  alias             The alias to compare against the subquery.
     * @param  op                The operation to use to compare the subquery
     *                           results (eg - "not in", "in", etc...)
     * @param  subqueryClass     The class to query over in the subquery.
     * @param  subqueryProperty  The property to select in the subquery.
     * @param  subqueryFilter    The Filter to apply to the subquery.
     */
    private void addSubqueryCondition(String alias, String op,
                                      Class<?> subqueryClass,
                                      String subqueryProperty,
                                      Filter subqueryFilter)
        throws GeneralException {
        
        this.conditionBuilder.append(alias).append(" ");
        this.conditionBuilder.append(op).append(" (");

        // Calculate the subquery.
        HQLFilterVisitor visitor =
            new HQLFilterVisitor(subqueryClass, this.databaseCapabilities,
                                 this.sessionFactory);

        // Set the parameter start index so we get unique names.
        visitor.parameterStartIdx = this.parameterMap.size() + this.parameterStartIdx;

        // Select the requested property.
        List<String> cols = new ArrayList<String>();
        cols.add(subqueryProperty);
        visitor.setSelectColumns(cols);
        
        // Only visit if we have a subquery filter.
        if (null != subqueryFilter) {
            subqueryFilter.accept(visitor);
        }

        this.conditionBuilder.append(visitor.getQueryString());
        this.parameterMap.putAll(visitor.getParameterMap());
        
        this.conditionBuilder.append(")");
    }
    
    /**
     * Get the inverse property on the elements of the given collection that
     * points back to the owner.  This throws an exception if not found.  This
     * is based on the {@link sailpoint.tools.BidirectionalCollection}
     * annotation.
     * 
     * @param  owner           The class that owns the collection.
     * @param  collectionName  The name of the property of the collection.
     * 
     * @return The name of the inverse property on the elements of the given
     *         collection that points back to the owner.
     *
     * @throws GeneralException If the requested collection does not have a
     *                          BidirectionalCollection annotation.
     */
    private String getInverseProperty(Class<?> owner, String collectionName)
        throws GeneralException {

        String inverse = null;

        PropertyDescriptor pd =
            Reflection.getBidirectionalProperty(owner, collectionName);

        if (null == pd) {
            throw new GeneralException("Cannot handle NOT IN subquery when collection element " +
                                       "does not have bi-directional association with owning entity.");
        }
        else {
            inverse = pd.getName();
        }

        return inverse;
    }

    private void visitComposite(CompositeFilter filter, String conjunct)
        throws GeneralException {
    
        List<Filter> children = filter.getChildren();
        if ((null != children) && !children.isEmpty()) {

            int beforeChildrenSize = this.conditionBuilder.length();
            String sep = "";

            for (Filter child : children) {

                // Push a new alias context if we're visiting a colleciton
                // condition.
                String prevProp = this.newAliasContextForCompositeProperty;
                boolean pushedContext = pushNewAliasContext(filter);

                int prevSize = this.conditionBuilder.length();
                child.accept(this);

                // Go back and add the separator if visiting the child added
                // anything to the condition builder.  This won't be added to
                // the condition builder if it gets put into the HAVING clause.
                if (this.conditionBuilder.length() > prevSize) {
                    this.conditionBuilder.insert(prevSize, sep);
                    sep = " " + conjunct + " ";
                }

                // Pop the context back off if we created one.
                if (pushedContext) {
                    this.aliasContext.pop();
                    this.newAliasContextForCompositeProperty = prevProp;
                }
            }

            // If we added any conditions surround them with parens.
            if (this.conditionBuilder.length() > beforeChildrenSize) {
                this.conditionBuilder.insert(beforeChildrenSize, "(");
                this.conditionBuilder.append(")");
            }
        }
    }

    /**
     * Push a new HQLAliasContext onto the stack using 
     * newAliasContextForCompositeProperty for the property name (if non-null).
     * 
     * @param  filter  The CompositeFilter for which to create the context.
     * 
     * @return True if a new context was pushed, false otherwise.
     */
    private boolean pushNewAliasContext(CompositeFilter filter) {

        if (null != this.newAliasContextForCompositeProperty) {

            String joinAlias = null;

            Class<?> fromClass = this.queryClass;
            Class<?> clazz = this.queryClass;
            String joinProp = this.newAliasContextForCompositeProperty;

            if (!this.thetaJoinCollectionCondition) {
                boolean outer = Filter.BooleanOperation.OR.equals(filter.getOperation());
                Join.Type joinType = (outer) ? Join.Type.LEFT_OUTER : Join.Type.INNER;

                // Look for properties like Identity.scorecard.  If we find one, we'll
                // want to join on this FROM class, otherwise default to the queryClass.
                Class<?> joinClass =
                    getClassFromProperty(this.newAliasContextForCompositeProperty, this.importPackages);
                fromClass = (null != joinClass) ? joinClass : this.queryClass;

                // If there is a joinClass (ie - property is like Identity.scorecard),
                // trim the classname off the front of the property.
                if ((null != joinClass) && (joinProp.startsWith(joinClass.getSimpleName()))) {
                    joinProp = joinProp.substring(joinProp.indexOf('.')+1);
                }

                // If this is an implicit join, make sure that the implicit join
                // is added by calling substituteAlias before the joins for the
                // new alias context.  This will happen for the following:
                // Filter.collectionCondition("certification.tags", Filter.and(...))
                if (joinProp.indexOf('.') > -1) {
                    this.aliasContext.substituteAlias(joinProp);
                }
                
                joinAlias = this.addJoin(fromClass, new Join(joinClass, joinProp, joinType));
                clazz = HQLAliasContextImpl.getPropertyType(fromClass, joinProp, this.sessionFactory);
            }

            // Push a new alias context so that any sub-operations will use it.
            HQLAliasContext ctx =
                new HQLAliasContextImpl(this.queryClass, clazz, joinAlias, this,
                                        this.importPackages, this.sessionFactory);
            this.aliasContext.push(ctx);

            // Null this out, so we don't create another alias context.
            this.newAliasContextForCompositeProperty = null;

            return true;
        }

        return false;
    }

    @Override
    public void visitAnd(CompositeFilter filter) throws GeneralException {
        this.visitComposite(filter, "and");
    }

    @Override
    public void visitOr(CompositeFilter filter) throws GeneralException {
        boolean prevValue = this.aliasContext.setInsideOr(true);
        this.visitComposite(filter, "or");
        this.aliasContext.setInsideOr(prevValue);
    }

    @Override
    public void visitNot(CompositeFilter filter) throws GeneralException {
        this.conditionBuilder.append(" not ");
        this.visitComposite(filter, "and");
    }
}
