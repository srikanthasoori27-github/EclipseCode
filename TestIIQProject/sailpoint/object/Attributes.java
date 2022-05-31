/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.api.SailPointContext;
import sailpoint.tools.Util;
import sailpoint.tools.xml.PersistentXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An extension of HashMap that adds convenience coercion
 * methods.  This class is used throughout the model to 
 * represent collections of dynamic attributes.
 */
@XMLClass
public class Attributes<K,V> extends HashMap<K,V> implements PersistentXmlObject
{
    private static final long serialVersionUID = -6258203505621169803L;

    /**
     * @exclude
     *
     * Annotation to mark getter methods for attribute values in an Attributes map.
     * The value should be the key of the attribute that this getter pulls from.
     * This is useful with {@link #getFromObject(Object, SailPointContext)} method, see there
     * for more details.
     *
     * See {@link CertificationDefinition} for example of usage.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AttributesGetter {

        String value();
    }

    /**
     * @exclude
     *
     * Annotation to mark setter methods for attribute values in an Attributes map.
     * The value should be the key of the attribute that this getter pulls from.
     * This is useful with {@link #setOnObject(Object)} method, see there
     * for more details.
     *
     * See {@link CertificationDefinition} for example of usage.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AttributesSetter {

        String value();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Attributes()
    {
        super();
    }

    public Attributes(int initialCapacity)
    {
        super(initialCapacity);
    }

    public Attributes(int initialCapacity, float loadFactor)
    {
        super(initialCapacity, loadFactor);
    }

    public Attributes(Map<? extends K, ? extends V> map)
    {
        super((null != map) ? map : new HashMap<K, V>());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PersistentXmlObject
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The XML representation of this object when it was first brought
     * out of persistent storage.  A hack used for Hibernate to optimize
     * the comparison of XML custom types.
     */
    String _originalXml;

    public void setOriginalXml(String xml) {
        _originalXml = xml;
    }

    @JsonIgnore
    public String getOriginalXml() {
        return _originalXml;
    }

    /////////////////////////////////////////////////////////////////////////
    // 
    // XML Serialization
    //
    /////////////////////////////////////////////////////////////////////////

    // jsl - consider INLINE here to avoid the <Map> wrapper?
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Map<K, V> getMap() {
        return new HashMap<K, V>(this);
    }

    public void setMap(Map<K, V> map) {
        this.clear();
        this.putAll(map);
    }

    
    /////////////////////////////////////////////////////////////////////////
    //
    // Object-like methods.
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * By default, Map's clone returns a shallow copy.  This method will give
     * a bit of a deeper copy by cloning the values if they are Object[], an
     * ArrayList, or a HashSet.
     */
    @SuppressWarnings("unchecked")
    public Attributes<K,V> mediumClone()
    {
        Attributes<K,V> cloned = new Attributes<K,V>();

        for (Map.Entry<K,V> entry : this.entrySet())
        {
            Object val = entry.getValue();

            // This sucks.  Why doesn't the Cloneable interface declare a
            // public clone() method?  Instead, java leaves it up to the
            // subclass to raise the visibility of clone() to be public and
            // not throw the exception.  Because of this we have to test for
            // specific subclasses ... and you thought that Java was object
            // oriented - think again. :)
            if (val instanceof Object[])
                val = ((Object[]) val).clone();
            else if (val instanceof ArrayList)
                val = ((ArrayList) val).clone();
            else if (val instanceof HashSet)
                val = ((HashSet) val).clone();

            cloned.put(entry.getKey(), (V) val);
        }

        return cloned;
    }
    
    /**
     * Utility method to cast an Object to Attributes
     * @param o Object to cast
     * @return Attributes<?, ?> object or null if object is not an instance of Attributes
     */
    public static Attributes<?, ?> castAttributes(Object o) {
        Attributes<?, ?> attrs = null;
        if (o instanceof Attributes)
            attrs = (Attributes<?, ?>)o;
        return attrs;
    }

    /////////////////////////////////////////////////////////////////////////
    //
    // Convenience methods for getting values out of the map and
    // coercing them to specific types
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * Putter that tries to follow the usual rules for clearing out
     * old values to keep the map clean.    
     */
    /**
     * Set an option, try to keep the map clean.
     */
    public void putClean(K key, V value) {
        if (key != null) {
            if (value == null) {
                remove(key);
            }
            else if (value instanceof Boolean) {
                Boolean b = (Boolean)value;
                if (b.booleanValue())
                    put(key, value);
                else
                    remove(key);
            }
            else {
                put(key, value);
            }
        }
    }

    /**
     * Returns the value specified by attributeName, as a boolean.
     * <p>
     * Casts values of string "true" to  boolean true, 
     * all other values including null will return
     * false.
     */
    public boolean getBoolean(String attributeName) {
        Object value = get(attributeName);
        return Util.otob(value);
    }

    /**
     * Returns the value specified by attributeName, as a boolean.  If not
     * found, this returns the default value.
     * <p>
     * Casts values of string "true" to  boolean true, 
     * all other values including null will return
     * false.
     */
    public boolean getBoolean(String attributeName, boolean dflt) {
        Object value = get(attributeName);
        return (null != value) ? Util.otob(value) : dflt;
    }

    /**
     * Returns the value specified by attributeName, as a Boolean. 
     * <p>
     * Casts values of string "true" to  boolean true
     */
    public Boolean getBooleanObject(String attributeName) {
        Boolean val = null;
        Object value = get(attributeName);

        if ( value != null ) {
            if ( value instanceof Boolean ) 
               val = (Boolean)value;
            else  
               val = new Boolean(getBoolean(attributeName));

        }
        return val;
    }

    /** 
     * Returns the value specified by attributeName, as a List.
     * <p>
     * If the value is not a List, it will be added to a 
     * list of one and returned.
     */
    @SuppressWarnings("unchecked")
    public List getList(String attributeName) {
        return getList(attributeName, false);
    }

    public List getList(String attributeName, boolean csvToList) {
        if (csvToList) {
            return Util.otol(get(attributeName));
        } else {
            List val = null;
            Object value = get(attributeName);
            if ( value  != null ) {
                if ( value instanceof List ) {
                    val = (List)value;
                } else {
                    val = new ArrayList();
                    val.add(value);
                }
            }
            return val;
        }
    }

    /**
     * Returns the value as a List of String.
     * If the current value is a String, it is assumed to be a CSV
     * and converted to a List.
     */
    public List<String> getStringList(String name) {
        List<String> list = null;
        Object value = get(name);
        if (value instanceof Collection) {
            list = new ArrayList<String>();
            for (Object el : (Collection)value) {
                if (el != null)
                    list.add(el.toString());
            }
        }
        else if (value instanceof String) {
            // this doesn't return List<String> although
            // that is what it creates, why??
            list = (List<String>)Util.stringToList((String)value);
        }
        else if (value != null) {
            // shouldn't be here
            list = new ArrayList<String>();
            list.add(value.toString());
        }
        return list;
    }

    /**
     * Returns the value specified by attributeName, as a String.
     * <p>
     * If the value is not a String, it will be coerced
     * to a String through the Object's toString() method.
     */
    public String getString(String attributeName) {
        String val = null;
        Object value = get(attributeName);
        if ( value != null ) {
            val = value.toString();
        }
        return val;
    }

    /**
     * Returns the value specified by attributeName, as an Integer.
     * Strings are coerced to Integers but nothing else.
     * 
     * @ignore
     * Be careful of empty strings, it is common to have
     * these set from the UI.  The Integer converter
     * may throw NumberFormatException.
     */
    public Integer getInteger(String attributeName) {
        Integer val = null;
        Object value = get(attributeName);
        if ( value != null ) {
            if ( value instanceof Integer ) {
               val = (Integer)value;
            } else {
                if ( value instanceof String ) {
                    String s = (String)value;
                    if (s.length() > 0)
                        val = new Integer(s);
                }
            }
        }
        return val;
    }

    /**
     *
     * Returns the value specified by attributeName, as an int.(primitive)
     * <p>
     * If the value is null or the empty string, this method will return 0.
     */
    public int getInt(String attributeName) {
        return getInt(attributeName, 0);
    }

    public int getInt(String attributeName, int dflt) {
        int val = dflt;
        Object value = get(attributeName);
        if ( value != null ) {
            // jsl - using this which won't throw NumberFormatException,
            // should be doing this for getInteger too?
            val = Util.otoi(value);
        }
        return val;
    }

    /** 
     * Returns the value specified by attributeName, as a Long.
     * <p>
     * If the value is not a Long it is coerced.
     */
    public Long getLong(String attributeName) {
        Long val = null;
        Object value = get(attributeName);
        if ( value != null ) {
            if ( value instanceof Long ) {
                val = (Long)value;
            } else 
            if ( value instanceof String )
                val = new Long((String)value);
            else
                val = null;
        }
        return val;
    }

    /** 
     * Returns the value specified by attributeName, as a long.(primitive)
     * <p>
     * This method will return 0 if the value is not found in the map or 
     * the value is null.
     *
     * @ignore
     * TODO: 
     * jsl - there has been the most precedent for using the simple
     * capitalized name to mean the unboxed type.  If something needs
     * to have boxed types, then it is better to say that specifically
     * like getBoxedLong or getLongObject since that is relatively unusual.
     */
    public long getLng(String attributeName) {
        long val = 0L;
        Long value = getLong(attributeName);
        if ( value != null ) {
           val = value.longValue();
        }
        return val;
    }

    /**
     * Returns the value as a float primitive.
     *
     * Departing from the convention above because it is more common
     * to ask for primitives - jsl
     */
    public float getFloat(String attributeName) {

        return getFloat(attributeName, 0.0f);
    }

    public float getFloat(String attributeName, float dflt) {

        float value = dflt;

        Object o = get(attributeName);
        if (o instanceof Float)
            value = ((Float)o).floatValue();
        else if (o != null)
            value = Util.atof(o.toString());

        return value;
    }

    /** 
     * Returns the value specified by attributeName, as a Date.
     * <p>
     * If the value is a String this method will attempt to coerce
     * the string to a date using the Util.dateToString method.
     */
    public Date getDate(String attributeName) {
        Date val = null;

        Object value = get(attributeName);
        if ( value != null ) {
           if ( value instanceof Date ) {
               val = (Date)value;
           } 
           else if (value instanceof Long) {
               val = new Date(((Long)value).longValue());
           }
           else if (value instanceof Integer) {
               val = new Date((long)((Integer)value).intValue());
           }
           else if ( value instanceof String ) {
               String s = ((String)value).trim();
               // ignore empty strings which can be posted
               // by the various UI date pickers
               if (s.length() > 0) {
                   // support both the string representation of a long
                   // which is posted by the Ext date pickers as well
                   // as typical date strings
                   int nonDigits = 0;
                   for (int i = 0 ; i < s.length() ; i++) {
                       char ch = s.charAt(i);
                       if (!Character.isDigit(ch)) {
                           nonDigits++;
                           break;
                       }
                   }

                   if (nonDigits == 0) {
                       long l = Util.atol(s);
                       val = new Date(l);
                   }
                   else {
                       try {
                           val = Util.stringToDate((String)value);
                       } catch (java.text.ParseException pe ) {
                           // TODO: don't be silent.. should this method throw?
                           // throw new IllegalArgumentException(attributeName + " is not a parseable String: " + System.getProperty("line.separator") + pe.toString());
                           System.out.println(pe.toString());
                           val = null;
                       }
                   }
               }
           }
        }
        return val;
    }

    /**
     * Handy for iterating in JSF pages.
     */
    public List<String> getKeys() {
        List<String> keys = new ArrayList<String>();
        Set keyset = keySet();
        // sigh, this is potentially variable to can't use addAll
        if (keyset != null) {
            for (Object key : keyset)
                keys.add(key.toString());
        }
        return keys;
    }

    //************************************************************************************************
    // These methods are useful with @AttributesGetter and @AttributesSetter annotations.
    // Basically, for small XML sizes parent classes can manipulate data to, for example, store only
    // strings instead of IDs. One example of this is CertificationDefinition.  However, consumers may
    // want an attributes map of the "real" values to use more easily without having to massage data themselves.
    // To do so, use these methods.
    //************************************************************************************************

    /**
     * @exclude
     *
     * Gets a new attributes map that takes the current keys and attempts of get the value from the
     * associated @AttributesGetter annotated method on the parent object. If the getter is not present, or does
     * not have supported parameters, maintain the value from the existing attributes map.
     * @param parentObject Object with getter methods marked with @AttributesGetter matching attribute names to
     *                     the method.
     * @param clazz Class of the parentObject. Need to specify so we dont use the weird Hibernate version on a loaded object
     *              that wont have the annotations we want.
     * @param context SailPointContext
     * @return Copy of attributes map with converted values
     */
    public Attributes<K, V> getFromObject(Object parentObject, Class clazz, SailPointContext context) {
        Map<K, Method> getterMap = getGetterMap(clazz);
        Attributes<K, V> newAttributes = new Attributes<>();
        for (Map.Entry<K, V> entry : entrySet()) {
            V newValue = entry.getValue();
            try {
                Method getter = getterMap.get(entry.getKey());
                if (getter != null) {
                    Class[] paremeterTypes = getter.getParameterTypes();
                    if (paremeterTypes.length > 0) {
                        if (paremeterTypes[0].equals(SailPointContext.class)) {
                            newValue = (V)getter.invoke(parentObject, context);
                        } else {
                            // Shouldn't really have any other parameter types here ... probably not worth erroring tho.
                        }
                    } else {
                        newValue = (V)getter.invoke(parentObject);
                    }
                }
            } catch (Exception ex) {
                // If it doesnt work, no worries!
            }

            newAttributes.put(entry.getKey(), newValue);
        }

        return newAttributes;
    }

    /**
     * @exclude
     *
     * Uses the keys and values in the map to call setter methods on the parentObject matched with @AttributeSetter annotation.
     * If the setter method is not found, nothing is called. This does not modified the current attributes map directly,
     * however this attributes map may be modified by the setters on the parent object.
     * @param parentObject Object with setter methods.
     * @param clazz Class of the parentObject. Need to specify so we dont use the weird Hibernate version on a loaded object
     *              that wont have the annotations we want.
     */
    public void setOnObject(Object parentObject, Class clazz) {
        Map<K, Method> setterMap = getSetterMap(clazz);

        // Clone the attributes here. We expect 'this' to potentially be on the parent object.
        // So we want to be safe while iterating, however the setter methods will modify 'this'.
        Attributes<K, V> tempAttributes = new Attributes<>(this);

        for (Map.Entry<K, V> entry : tempAttributes.entrySet()) {
            try {
                Method setter = setterMap.get(entry.getKey());
                if (setter != null) {
                    setter.invoke(parentObject, entry.getValue());
                }
            } catch (Exception e) {
                //Do nothing.
            }
        }
    }

    private Map<K, Method> getGetterMap(Class clazz) {
        Map<K, Method> getterMap = new HashMap<>();
        Method[] methods = clazz.getMethods();

        if (methods != null) {
            for (Method method : methods) {
                AttributesGetter annotation = method.getAnnotation(AttributesGetter.class);
                if (annotation != null) {
                    getterMap.put((K)annotation.value(), method);
                }
            }
        }

        return getterMap;
    }

    private Map<K, Method> getSetterMap(Class clazz) {
        Map<K, Method> setterMap = new HashMap<>();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            AttributesSetter annotation = method.getAnnotation(AttributesSetter.class);
            if (annotation != null) {
                setterMap.put((K)annotation.value(), method);
            }
        }

        return setterMap;
    }

}
