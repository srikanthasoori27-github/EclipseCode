/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.CollectionType;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import sailpoint.persistence.HQLFilterVisitor.Join;

/**
 * Implementation of HQLAliasContext.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
class HQLAliasContextImpl implements HQLAliasContext {

    private SessionFactory sessionFactory;
    private List<String> importPackages;
    private String defaultAlias;
    private Class<?> fromClass;
    private Class<?> clazz;
    private Map<String,String> aliasesByPath;
    private HQLJoinHandler joinHandler;
    private boolean insideOr = false;


    /**
     * Constructor.
     * 
     * @param  fromClass     The class that is being selected in the FROM clause.
     *                       Note that this might be different from clazz if the
     *                       alias context has been switched due to a collection
     *                       condition.
     * @param  clazz         The base class of the query.
     * @param  defaultAlias  The default alias (ie - for an empty property path).
     * @param  joinHandler   The JoinHandler to use to create joins.
     * @param  imports       The import packages to use when attempting to
     *                       resolve class names from properties.
     * @param  factory       The SessionFactory to use.
     */
    public HQLAliasContextImpl(Class<?> fromClass, Class<?> clazz, String defaultAlias,
                               HQLJoinHandler joinHandler,
                               List<String> imports, SessionFactory factory) {
        this.fromClass = fromClass;
        this.clazz = clazz;
        this.defaultAlias = defaultAlias;
        this.joinHandler = joinHandler;
        this.importPackages = imports;
        this.sessionFactory = factory;
        this.aliasesByPath = new HashMap<String,String>();

        // Set the default alias for the root class.
        this.aliasesByPath.put(clazz.getName(), defaultAlias);
    }

    public boolean setInsideOr(boolean insideOr) {
        boolean previous = this.insideOr;
        this.insideOr = insideOr;
        return previous;
    }

    public String getDefaultAlias() {
        return this.defaultAlias;
    }

    public String getAlias(Class clazz) {
        return this.getAlias(clazz.getName());
    }
    
    public String getAlias(String propertyPath) {
        return this.aliasesByPath.get(propertyPath);
    }

    public void setClassAlias(Class clazz, String alias) {
        this.aliasesByPath.put(clazz.getName(), alias);
    }

    public String substituteAlias(String prop) {
        return this.substituteAlias(prop, false, false);
    }

    public String substituteAlias(String prop, boolean forceUniqueJoin,
                                  boolean forceOuterJoin) {
        String alias = null;

        // The property may be a function.  Try to parse it.
        Function function = Function.parse(prop);
        prop = function.getProperty();
        
        // Use the query class or the joined class if this is an explicit join.
        Class<?> clazz = this.clazz;
        Class<?> from = this.fromClass;
        Class<?> joinClass = HQLFilterVisitor.getClassFromProperty(prop, this.importPackages);
        if (null != joinClass) {
            clazz = joinClass;
            from = joinClass;
            prop = prop.substring(prop.indexOf('.')+1);
        }

        // If we're visiting an OR, outer join since the condition isn't required.
        Join.Type type = (forceOuterJoin || this.insideOr) ? Join.Type.LEFT_OUTER : Join.Type.INNER;
        String joinPath = clazz.getName();
        String fullProp = "";
        String propSep = "";

        List<PropertyInfo> parts = parseProperty(clazz, prop);

        PropertyInfo last = parts.remove(parts.size() - 1);

        // Either lookup or add joins for everything up to the last PropertyInfo.
        for (Iterator<PropertyInfo> it = parts.iterator(); it.hasNext(); ) {
            PropertyInfo part = it.next();
            boolean isLast = !it.hasNext();

            joinPath += "." + part.getProperty();
            fullProp += propSep + part.getProperty();
            propSep = ".";

            // Check if we already have an alias.
            alias = this.getAlias(joinPath);

            // Add a join if we haven't added one yet, or we're at the end of
            // the property and we're forcing a join.
            if ((null == alias) || (isLast && forceUniqueJoin)) {

                // Errors thrown by Hibernate are not very helpful here so put
                // a check in place to give us some idea whats happening
                if (!HQLFilterVisitor.requiresUniqueJoin(prop) &&
                        isLast && forceUniqueJoin) {
                    throw new RuntimeException("Invalid join on property '" + prop + "'. An attempt to force a unique join on a Class property that requires a theta style join.");
                }

                alias = this.addJoin(from, new Join(joinClass, fullProp, type));
            }
        }


        // If we're forcing a unique join, create a join for this and return
        // the alias, otherwise return the alias of the class with the
        // property on the end.
        if ((null == alias) && forceUniqueJoin) {
            alias = this.addJoin(from, new Join(joinClass, prop, type));
        } else {
            if (null == alias) {
                alias = (null != joinClass) ? this.getAlias(clazz) : this.getDefaultAlias();
            }
            alias += "." + last.getProperty();
        }

        // Format the alias as a function if there was one.
        return function.format(alias);
    }
    
    /**
     * Return the type of the given property (possibly dotted) of the given
     * class by inspecting the Hibernate metadata.
     * 
     * @param  clazz           The Class that the property lives on.
     * @param  property        The possibly dotted property to find the type.
     * @param  sessionFactory  The SessionFactory to use.
     * 
     * @return The type of the requested property, or if the property is a
     *         collection the type of element.
     */
    static Class<?> getPropertyType(Class<?> clazz, String property,
                                    SessionFactory sessionFactory) {
        PropertyInfo info = getPropertyInfo(clazz, property, sessionFactory);
        return (null != info) ? info.getPropertyClass() : null;
    }
    
    /**
     * Return the type of the given property (possibly dotted) of the given
     * class by inspecting the Hibernate metadata.
     * 
     * @param  clazz           The Class that the property lives on.
     * @param  property        The possibly dotted property to find the type.
     * @param  sessionFactory  The SessionFactory to use.
     * 
     * @return The type of the requested property, or if the property is a
     *         collection the type of element.
     */
    private static PropertyInfo getPropertyInfo(Class<?> clazz, String property,
                                                SessionFactory sessionFactory) {
        Class<?> propertyClass = null;
        boolean isCollection = false;
        String[] parts = property.split("\\.");

        for (String part : parts) {
            if (null == clazz) {
                break;
            }

            ClassMetadata cmd = null;
            try {
                 cmd = sessionFactory.getClassMetadata(clazz);
            } catch (MappingException e) {
                //Embeddable/component types will throw with Unknown Entity exception.
            }

            // This could be null for component types.  We could maybe do
            // something more.  Good enough for now, though.
            if (null != cmd) {
                Type type = cmd.getPropertyType(part);
                if (type instanceof CollectionType) {
                    type = ((CollectionType) type).getElementType((SessionFactoryImplementor) sessionFactory);
                    isCollection = (type instanceof EntityType);
                }

                // Get the class of either the property or the element type if this
                // is a collection.
                propertyClass = type.getReturnedClass();
            }
            clazz = propertyClass;
        }

        Class<?> propInfoClass = (null != propertyClass) ? propertyClass : clazz;
        return new PropertyInfo(property, propInfoClass, isCollection);
    }
    
    /**
     * Split the given possibly-dotted property of the given class into
     * PropertyInfos.
     */
    private List<PropertyInfo> parseProperty(Class<?> clazz, String prop) {

        List<PropertyInfo> parts = new ArrayList<PropertyInfo>();

        String[] split = prop.split("\\.");
        for (int i=0; i<split.length; i++) {
            String current = split[i];
            PropertyInfo part = getPropertyInfo(clazz, current, sessionFactory);
            parts.add(part);
            clazz = part.getPropertyClass();
            
            // If this is the next to last, try to slurp an "id" property to
            // avoid joins that are not necessary.
            if ((i == split.length-2) && part.addId(split[split.length-1])) {
                break;
            }
        }

        return parts;
    }
    
    /**
     * Add the given Join to the given from class and store the alias.
     */
    private String addJoin(Class<?> clazz, Join join) {
        String alias = this.joinHandler.addJoin(clazz, join);
        String path = clazz.getName() + "." + join.getProperty();
        this.aliasesByPath.put(path, alias);
        return alias;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * A structure holding information about a property.
     */
    private static class PropertyInfo {

        private String property;
        private Class<?> propertyClass;
        private boolean isCollection;

        /**
         * Constructor.
         * 
         * @param  property       The property name.
         * @param  propertyClass  The class of the property.
         * @param  isCollection   Whether the property is a collection.
         */
        public PropertyInfo(String property, Class<?> propertyClass,
                            boolean isCollection) {
            this.property = property;
            this.propertyClass = propertyClass;
            this.isCollection = isCollection;

        }

        /**
         * Return the name of the property.
         */
        public String getProperty() {
            return this.property;
        }
        
        /**
         * Return the type of the property.
         * @return
         */
        public Class<?> getPropertyClass() {
            return this.propertyClass;
        }

        /**
         * Try to add the "remaining" part of a property to this property as an
         * id.
         * 
         * @param  remaining  The remaining part of a property.
         * 
         * @return True if the id was added to this property.
         */
        public boolean addId(String remaining) {
            boolean added = false;
            
            // Hibernate only likes "foo.id" references in HQL if the property
            // is not a collection.
            if (!this.isCollection && "id".equals(remaining)) {
                this.property += ".id";
                added = true;
            }
            
            return added;
        }
    }


    /**
     * A structure holding a property and possibly a function that is applied
     * to that property.
     */
    private static class Function {

        /**
         * The parsed property.
         */
        private String property;

        /**
         * The possibly null parsed function.
         */
        private String function;


        /**
         * Private constructor - only constructed by calling parse().
         */
        private Function(String property, String function) {
            this.property = property;
            this.function = function;
        }
        
        /**
         * Create a ParsedProperty by parsing the given property.
         * 
         * @param  unparsed  The unparsed property.
         */
        public static Function parse(String unparsed) {
            String property = unparsed;
            String function = null;

            int parenIdx = unparsed.indexOf('(');
            if (-1 != parenIdx) {
                int endIdx = unparsed.indexOf(')');
                function = unparsed.substring(0, parenIdx);
                property = unparsed.substring(parenIdx+1, endIdx);
            }
            
            return new Function(property, function);
        }

        /**
         * Return the property.
         */
        public String getProperty() {
            return this.property;
        }
        
        /**
         * Format the given alias into the function of this parsed property.  If
         * there is no function, this just returns the alias.
         */
        public String format(String alias) {
            return (null != this.function) ? this.function + "(" + alias + ")"
                                           : alias;
        }
    }
}
