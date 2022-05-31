/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.FilterVisitor;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.persistence.ExtendedAttributeUtil.PropertyMapping;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;


/**
 * Filter visitor to perform attribute name mapping.
 */
public class ExtendedAttributeVisitor implements FilterVisitor {
    
    private static final Log log = LogFactory.getLog(ExtendedAttributeVisitor.class);
    
    private Class<?> defaultClass;
    private Map<Class<?>, Map<String, ObjectAttribute>> attributeMap;

    /**
     * It should be perfectly ok to cache this.
     * That is because class structure is guaranteed
     * not to change during the execution of iiq.
     * Plus it is expensive to do introspection 
     * on a class everytime.
     * 
     * Also this is lazyily loaded on as needed basis.
     *
     */
    private static ClassesInfo classesInfo = new ClassesInfo();
    
    /**
     * Constructor.
     * 
     * @param  clazz  The class that is being searched on.
     */
    public ExtendedAttributeVisitor(Class<?> clazz) {
        this.defaultClass = clazz;
        this.attributeMap = new HashMap<Class<?>, Map<String,ObjectAttribute>>();
    }

    
    /**
     * 
     * NEW: DO NOT CHECK FOR NULL TO DETERMINE IF SOMETHING CHANGED.
     * You will need to compare what was sent vs what was returned
     * for that.

     * Map the property according to the search attributes.  For example,
     * this will translate "location" to "searchAttributes['location']" or
     * "extended1".  This also handles properties with class name prefixes,
     * such as Identity.location.  If this does not map to a search
     * attribute, null is returned.
     * 
     * @param  property  The name of the property to map to a search
     *                   attribute.
     *
     * @return The name of the mapped attribute or IT WILL RETURN
     * the ORIGINAL (use equals() and not ==) if nothing needs to be changed. 
     */
    public String mapProperty(String property) throws GeneralException {
        
        PropertyResolver resolver = new PropertyResolver(this.defaultClass);

        // kludge: a few places in the UI use aggregate functions
        // in the projection property list, the one found first was
        // count(distinct attname).

        String prefix = null;
        String suffix = null;

        int lparen = property.indexOf("(");
        if (lparen > 0) {
            int rparen = property.indexOf(")", lparen);
            if (rparen > 0) {
                prefix = property.substring(0, lparen + 1);
                suffix = property.substring(rparen);
                String trimmed = property.substring(lparen + 1, rparen).trim();

                // handle count(distinct foo)
                String token = "distinct ";
                if (trimmed.startsWith(token)) {
                    prefix += token;
                    trimmed = trimmed.substring(token.length());
                }
                //System.out.println("Property " + property + " trimmed to " + trimmed);
                property = trimmed;
            }
        }

        property = resolver.resolve(property).resolvedProperty;

        if (prefix != null)
            property = prefix + property + suffix;

        return property;
    }

    /**
     * Currently this only handles the date type. For  
     * date type we want to covert the Date value 
     * specified in the Filter to the stringified utime.
     * We also convert boolean values to string to avoid hibernate class cast exceptions.
     */
    private static Object coerceValue(String valueType, Object val) 
        throws GeneralException {
        
        if ( val == null ) return val;
        /** Cast strings to ints if the property info is integer **/
        if(PropertyInfo.TYPE_INT.equals(valueType) && val instanceof String){
            return Integer.parseInt((String)val);
        }
        if ( PropertyInfo.TYPE_DATE.equals(valueType) )  {
            if ( val instanceof Date ) {
                Date date = (Date) val;
                // query on stringified utime since that's
                // how those values are transformed when
                // written. 
                return Long.toString(date.getTime());
            }
        }
        else if (PropertyInfo.TYPE_BOOLEAN.equals(valueType) && val instanceof Boolean) {
            // convert boolean to string to avoid class cast exceptions
            return val.toString();
        }
        return null;
    }
    
    /**
     * When dealing with extended attributes check the type
     * specified on the ObjectAttribute.
     */
    private String getValueType(String unmappedPropertyName, Class<?> leafClass) throws GeneralException{
        
        String valueType = null;
        
        Map<String,ObjectAttribute> attributeMap = getExtendedAttributeMap(leafClass);

        ObjectAttribute attr = attributeMap.get(unmappedPropertyName);
        if ( attr != null ) {
            valueType = attr.getType();
        }
        
        return valueType;
    }

    /**
     * Add an attribute map for the given class.
     */
    private Map<String,ObjectAttribute> addAttributeMap(Class<?> clazz, Map<String,ObjectAttribute> map) {
        if (null == map) {
            map = new HashMap<String,ObjectAttribute>();
        }
        this.attributeMap.put(clazz, map);
        return map;
    }

    /**
     * Return (and create if it has not yet been created) the name map for
     * the given class.
     * 
     * @param  clazz  The class for which to get the name map.
     */
    private Map<String,ObjectAttribute> getExtendedAttributeMap(Class<?> clazz)
        throws GeneralException {

        if (null == clazz) {
            clazz = this.defaultClass;
        }
        Map<String,ObjectAttribute> map = this.attributeMap.get(clazz);

        if (null == map) {
            try {
                ObjectConfig config = ObjectConfig.getObjectConfig(clazz);
                if (config != null)
                    map = config.getExtendedAttributeMap();
            
                // just so we don't keep asking?
                if (map == null)
                    map = new HashMap<String,ObjectAttribute>();
                map = addAttributeMap(clazz, map);
            }
            catch (Exception e) {
                log.error("error getting attributeMap for class: " + clazz);
                throw new GeneralException(e);
            }
        }

        return map;
    }

    public void visitAnd(CompositeFilter filter) throws GeneralException {
        visitComposite(filter);
    }
    public void visitOr(CompositeFilter filter) throws GeneralException {
        visitComposite(filter);
    }
    public void visitNot(CompositeFilter filter) throws GeneralException {
        visitComposite(filter);
    }

    private void visitComposite(CompositeFilter filter) throws GeneralException {
        List<Filter> children = filter.getChildren();
        if (children != null) {
            for (Filter f : children) {
                f.accept(this);
            }
        }
    }

    public void visitEQ(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitNE(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitLT(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitGT(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitLE(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitGE(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitIn(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitContainsAll(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitLike(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitNotNull(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitIsNull(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitIsEmpty(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitJoin(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }
    public void visitLeftJoin(LeafFilter filter) throws GeneralException {
        visitLeaf(filter);
    }

    public void visitCollectionCondition(LeafFilter filter) throws GeneralException {

        visitLeaf(filter);

        PropertyResolver resolver = new PropertyResolver(this.defaultClass);
        resolver.resolve(filter.getProperty());

        if (resolver.getFinalPropertyClass() == null) {
            CompositeFilter composite = filter.getCollectionCondition();
            visitComposite(composite);
            
        } else {
        
            ExtendedAttributeVisitor visitor = new ExtendedAttributeVisitor(resolver.getFinalPropertyClass());
            
            CompositeFilter composite = filter.getCollectionCondition();
            visitor.visitComposite(composite);
        }        
    }
    
    public void visitSubquery(LeafFilter filter) throws GeneralException {

        visitLeaf(filter);
        
        // Create a new visitor with the subquery class as the default class, so
        // we can visit the subquery filter.
        Filter subqueryFilter = filter.getSubqueryFilter();
        Class<?> subqueryClass = filter.getSubqueryClass();
        ExtendedAttributeVisitor subVisitor = new ExtendedAttributeVisitor(subqueryClass);

        if (null != subqueryFilter) {
            subqueryFilter.accept(subVisitor);
        }

        String subqueryProperty = filter.getSubqueryProperty();
        if (null != subqueryProperty) {
            String mapName = subVisitor.mapProperty(subqueryProperty);
            if (!mapName.equals(subqueryProperty))
                filter.setSubqueryProperty(mapName);
        }
    }
    
    private void visitLeaf(LeafFilter filter) throws GeneralException {

        // original property name
        String propName = filter.getProperty();

        PropertyResolver resolver = new PropertyResolver(this.defaultClass);
        PropertyResolver.ResolveResult resolveResult = resolver.resolve(propName);
        String mapName = resolveResult.resolvedProperty;
        if (!mapName.equals(propName) || resolveResult.resolvedExtended) {
            filter.setProperty(mapName);
            // since we transformed the name of the attribute
            // see if we also need to convert the filter based
            // on its value type
            Object value = filter.getValue();
            if ( value != null  ) {
                String valueType = getValueType(resolveResult.unresolvedUnprefixedProperty, resolveResult.propertyClass);
                if(valueType!=null) {
                    if(valueType.equals(PropertyInfo.TYPE_INT)){
                        filter.setCast(PropertyInfo.TYPE_INT);
                    }
                    Object coercedVal = coerceValue(valueType, value);
                    if ( coercedVal != null) {
                        filter.setValue(coercedVal);
                    }
                }

            }
        }

        String joinProperty = filter.getJoinProperty();
        if (null != joinProperty) {
            PropertyResolver.ResolveResult joinResolveResult = resolver.resolve(joinProperty);
            mapName = joinResolveResult.resolvedProperty;
            if (!mapName.equals(joinProperty))
                filter.setJoinProperty(mapName);
        }
    }



    /**
     * It should be perfectly ok to cache this.
     * That is because class structure is guaranteed
     * not to change during the execution of iiq.
     * Plus it is expensive to do introspection
     * on a class everytime.
     *
     *
     */
    private static class ClassesInfo {

        Map<Class<?>, Map<String, Class<?>>> cache = new HashMap<Class<?>, Map<String, Class<?>>>();

        private Class<?> getPropertyType(Class<?> clazz, String propertyName) {

            // jsl - started seeing an NPE here testing partitioning
            // with classInfo coming back null, add synchronizagion around
            // the mods but don't do it on read because this cache
            // will warm quickly

            Map<String, Class<?>> classInfo = this.cache.get(clazz);
            if (classInfo == null) {
                synchronized (this) {
                    // have to check again
                    classInfo = this.cache.get(clazz);
                    if (classInfo == null) {
                        classInfo = new HashMap<String, Class<?>>();
                        this.cache.put(clazz, classInfo);
                    }
                }
            }

            Class<?> ptype  = classInfo.get(propertyName);
            if (ptype == null) {
                synchronized (this) {
                    ptype  = classInfo.get(propertyName);
                    if (ptype == null) {
                        try {
                            ptype = getReturnType(clazz, propertyName);

                        } catch (Exception ex) {
                            log.error(ex);
                        }

                        if (ptype != null) {
                            classInfo.put(propertyName, ptype);
                        }
                        else if(log.isInfoEnabled()) {
                            log.info("could not get property type for class: " + clazz + ", propertyName: " + propertyName);
                        }
                    }
                }
            }

            return ptype;
        }

        private static Class<?> getReturnType(Class<?> clazz, String propertyName) throws NoSuchMethodException, IllegalStateException {

            Class<?> returnType = null;

            Method method = getMethod(clazz, propertyName);
            if (method == null) {return null;}

            returnType = method.getReturnType();
            if (returnType == null) {
                log.warn("returnType is null for class: " + clazz + ", methodName: " + method.getName());
                return null;
            }

            if (Collection.class.isAssignableFrom(returnType)) {
                Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) genericReturnType;
                    Class<?> firstClass = (Class<?>)parameterizedType.getActualTypeArguments()[0];
                    returnType = firstClass;
                } else {
                    log.warn("expecting parameterized list for List<?> type return value for class: " + clazz + ", methodName: " + method.getName());
                }
            }

            return returnType;
        }

        private static Method getMethod(Class<?> clazz, String propertyName) {

            String methodName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                return clazz.getMethod(methodName);
            } catch (NoSuchMethodException ex) {
                if (log.isInfoEnabled()) {
                    log.warn("method: " + methodName + " does not exist in class: " + clazz);
                }
                return null;
            }
        }

    }

    private class PropertyResolver {

        /**
         * Class to hold some results of the resolve() method.
         */
        public class ResolveResult {
            /**
             * Name of property, unresolved, without any prefix
             * i.e. Identity.manager.name => name
             */
            public String unresolvedUnprefixedProperty;

            /**
             * Full resolved property
             * i.e. Identity.manager.dateAttr => Identity.manager.extended1
             */
            public String resolvedProperty;

            /**
             * Class of the object with the fully resolved property
             * i.e. Identity.manager.name => Identity.class
             */
            public Class propertyClass;

            /**
             * True if the resolved property is an extended Attribute
             */
            public boolean resolvedExtended;
        }

        private Class<?> rootClass;
        private Class<?> currentClazz;
        private String propertyPrefix;
        private String currentProperty;
        private String unresolvedProperty;
        private boolean _extendedProp;

        public PropertyResolver(Class<?> rootClass)
            throws GeneralException {

            this.rootClass = rootClass;
            this.propertyPrefix = null;
            this.currentClazz = rootClass;
        }

        /**
         *
         * @param property say items.buddy.location
         * @return ResolveResult object with:
         * - resolvedProperty = items.extendedIdentity1.extended5,
         * - unresolvedUnprefixedProperty = location,
         * - propertyClass = Identity.class
         * where Identity.class is the class for the last item
         * in this example rootClass = CertificationEntity.class
         * see walkThrough() method below for more details
         */
        public ResolveResult resolve(String property) throws GeneralException {

            this.propertyPrefix = null;
            this.currentClazz = this.rootClass;
            this.currentProperty = this.unresolvedProperty = property;

            walkThrough();

            ResolveResult result = new ResolveResult();
            result.unresolvedUnprefixedProperty = this.unresolvedProperty;
            if (this.propertyPrefix == null) {
                result.resolvedProperty = this.currentProperty;
            } else {
                result.resolvedProperty = this.propertyPrefix + "." + this.currentProperty;
            }
            result.propertyClass = this.currentClazz;

            result.resolvedExtended = this._extendedProp;

            return result;
        }

        public Class<?> getFinalPropertyClass() {

            return ExtendedAttributeVisitor.classesInfo.getPropertyType(this.currentClazz, this.currentProperty);
        }

        // let us say
        // before iteration
        // currentProperty = items.owner.displayName
        // currentClass = CertificationEntity.class
        // propertyPrefix = null;
        //
        // then,
        // after walkThrough#1()
        // currentClass = CertificationItem.class
        // propertyPrefix = items
        // currentProperty = owner.displayName
        //
        // afterWalkThrough#2()
        // currentClass = Identity.class
        // propertyPrefix = owner (it would be tried to be mapped)
        // currentProperty = displayName
        //
        private void walkThrough() throws GeneralException {

            int firstDotIdx = this.currentProperty.indexOf('.');

            if (firstDotIdx == -1) {
                Pair<String, Class<?>> mappedPropertyInfo = getMappedPropertyInfo(this.currentClazz, this.currentProperty);
                this.currentProperty = mappedPropertyInfo.getFirst();
            } else {
                // anything before the first dot "."
                String firstChunk = this.currentProperty.substring(0, firstDotIdx);
                if (Character.isUpperCase(this.currentProperty.charAt(0))) {
                    // In most cases this should be an object, so try it out first
                    String fullClassName = "sailpoint.object." + firstChunk;
                    try {
                        this.currentClazz = Class.forName(fullClassName);
                        addPropertyPrefix(firstChunk);
                    }
                    catch (ClassNotFoundException e) {
                        // If not a class, see if it is an extended attribute
                        if (log.isInfoEnabled()) {
                            log.info("Could not find object: " + fullClassName + ", looking for property: " + firstChunk +
                                    " in extended for class: " + this.currentClazz);
                        }
                        Pair<String, Class<?>> mappedPropertyInfo = getMappedPropertyInfo(this.currentClazz, firstChunk);
                        if (!mappedPropertyInfo.getFirst().equals(firstChunk)) {
                            // Found a matching extended attribute, use it
                            addPropertyPrefix(mappedPropertyInfo.getFirst());
                            this.currentClazz = mappedPropertyInfo.getSecond();
                        } else {
                            // If neither a class nor extended attribute, we have historically thrown, so keep it that way
                            throw new GeneralException(e);
                        }
                    }
                }
                else {
                    if (firstChunk.indexOf("(") == -1) {
                        // these are regular properties
                        Class<?> propertyType = ExtendedAttributeVisitor.classesInfo.getPropertyType(this.currentClazz, firstChunk);
                        if (propertyType != null) {
                            addPropertyPrefix(firstChunk);
                            // this is set for the child property
                            this.currentClazz = propertyType;
                        } else {
                            if (log.isInfoEnabled()) {
                                log.info("Could not find property: " + firstChunk + " in class: " + this.currentClazz + " looking in extended.");
                            }
                            Pair<String, Class<?>> mappedPropertyInfo = getMappedPropertyInfo(this.currentClazz, firstChunk);
                            if (log.isInfoEnabled()) {
                                if (mappedPropertyInfo.getFirst().equals(firstChunk)) {
                                    log.info("Could not find property: " + firstChunk + ", in class: " + this.currentClazz + " either in regular or extended properties.");
                                }
                            }
                            addPropertyPrefix(mappedPropertyInfo.getFirst());
                            this.currentClazz = mappedPropertyInfo.getSecond();
                        }
                    } else {
                        // avg(stepDuration) etc
                        // no need to get the type here
                        addPropertyPrefix(firstChunk);
                    }
                }

                this.unresolvedProperty = this.currentProperty = this.currentProperty.substring(firstDotIdx + 1);
                /** Continue up the string recursively on any further pieces of the string after the period **/
                walkThrough();
            }
        }

        private void addPropertyPrefix(String toAdd) {
            if (this.propertyPrefix == null) {
                this.propertyPrefix = toAdd;
            } else {
                this.propertyPrefix = this.propertyPrefix + "." + toAdd;
            }
        }

        /**
         * Returns propertyName if it is not mapped to extended
         * or it will return extended1 etc
         * @param clazz
         * @param propertyName
         * @return
         * @throws GeneralException
         */
        private Pair<String, Class<?>> getMappedPropertyInfo(Class<?> clazz, String propertyName) throws GeneralException {

            String mapName = propertyName;

            Class<?> theClass = String.class;// non-identity type is currently string

            Map<String,ObjectAttribute> attrMap = getExtendedAttributeMap(clazz);

            ObjectAttribute attr = attrMap.get(propertyName);
            if ( attr != null ) {

                PropertyMapping propMap = ExtendedAttributeUtil.getPropMapping(clazz.getSimpleName(), attr);
                if(propMap != null) {
                    //Extended Property
                    this._extendedProp = true;
                    mapName = propMap.getName();
                    if(propMap.isIdentity()) {
                        theClass = Identity.class;
                    }
                } else {
                    // Could not find property in Extended Property Mappings
                    this._extendedProp = false;
                }
            }   
            return new Pair<String, Class<?>>(mapName, theClass);
        }
    }
}
