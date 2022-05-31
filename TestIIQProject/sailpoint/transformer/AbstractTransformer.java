package sailpoint.transformer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;

/**
 * abstract class to hold common methods all transformers will use
 * 
 * @param <T>
 *            the type of object which you will be transforming
 */
public abstract class AbstractTransformer<T> implements Transformer<T> {
    /**
     * a map of options that can be used to change transformer behavior 
     */
    private Map<String,Object> options = new HashMap<String, Object>();
    
    /**
     * Our context required for database access.
     */
    protected SailPointContext context;
    
    /**
     * Attribute that represents the hibernate ID of an object.
     */
    public static final String ATTR_ID = "id";
    
    /**
     * Attribute that represents the name of an object.
     */
    public static final String ATTR_NAME = "name";
    
    /**
     * Attribute that represents the displayName of an object.
     */
    public static final String ATTR_DISPLAY_NAME = "displayName";
        
    /**
     * Attribute that represents the description, a lot of object have description but not all.
     */
    public static final String ATTR_DESCRIPTION = "description";
    
    /**
     * Attribute that represents the classname of the underlying object.
     */
    public static final String ATTR_OBJECT_CLASS = "class";
    
    /**
     * Attribute that represents the transformer classname that performed the operation 
     */
    public static final String ATTR_TRANSFORMER_CLASS = "transformerClass";
    
    /**
     * Attribute that represents the options used when performing the transformation 
     */
    public static final String ATTR_TRANSFORMER_OPTIONS = "transformerOptions";

    /**
     * System namespace.
     */
    public static final String ATTR_SYSTEM = "sys";
    
    /**
     * The info namespace used for storing read only values
     */
    public static final String ATTR_INFO = "info";
    
    /**
     * The sys attribute that indicates if the object should be
     * deleted.
     * 
     * @ignore: currently only supported for Link model
     */
    public static final String ATTR_DELETE = "delete";
    
    /**
     * Operation override that will be applied to the generated
     * AccountRequest when converted from a Model to ProvsioningPlan.
     */
    public static final String ATTR_SYS_OPERATION = "sys.operation";

    public abstract Map<String, Object> toMap(T object) throws GeneralException;
    
    /* Default implementation returns an empty map, override if your transformer 
     * supports this behavior
     * @see sailpoint.transformer.Transformer#refresh(java.util.Map)
     */
    public Map<String, Object> refresh(Map<String, Object> map) throws GeneralException {
        return new HashMap<String, Object>();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utils
    //
    ///////////////////////////////////////////////////////////////////////////
    
    protected void setOptions(Map<String, Object> optionsMap) {
        if (optionsMap != null) {
            this.options = optionsMap;
        }
    }
    
    protected Map<String, Object> getOptions() {
        return options;
    }

    protected void setOption(String option, Object optionValue) {
        options.put(option, optionValue);
    }
    
    protected Object getOption(String option) {
        return options.get(option);
    }

    protected String prefixWithSystem(String attrName) {
        return ATTR_SYSTEM + "." + attrName;
    }
    
    protected static String prefixWithInfo(String attrName) {
        return ATTR_INFO + "." + attrName;
    }
    
    protected void putIfNotNull(Map<String, Object> mapModel, String key, Object value) throws GeneralException {
        if (value != null) {
            MapUtil.put(mapModel, key, value);
        }
    }
    
    protected void putIfNotEmpty(Map<String, Object> mapModel, String key, Collection value) throws GeneralException {
        if (! Util.isEmpty(value)) {
            MapUtil.put(mapModel, key, value);
        }
    }
    
    protected void addIfNotNull(List list, Object value) {
        if (value != null) {
            list.add(value);
        }
    }

    /**
     * Build some base info that should be included in most Models. 
     * Including the hibernate ID, object's name, objectClass, 
     * transformer class and transformer options.   
     */
    
    protected Map<String,Object> buildBaseSailPointObjectInfo(SailPointObject obj ) {
        
        Map<String,Object> map = new HashMap<String,Object>();
        if ( obj != null ) {
            map.put(ATTR_ID, obj.getId());
            map.put(ATTR_NAME, obj.getName());
            String clazzName = obj.getClass().getName();
            // Hibernate stores a pojo when lazy loading
            // sniff the classname and call our special method
            // to resolve the correct name
            //Not using CGLIB anymore, New class for proxies is "HibernateProxy"
            if ( clazzName.contains("HibernateProxy") ) {
                Class clazz = ObjectUtil.getTheRealClass(obj);
                if ( clazz != null) {
                    clazzName = clazz.getName();
                }
            }
            map.put(ATTR_OBJECT_CLASS, clazzName);
        }
        map.put(ATTR_TRANSFORMER_CLASS, this.getClass().getName());
        map.put(ATTR_TRANSFORMER_OPTIONS, getOptions());
        
        return map;
    }
    
    protected void appendBaseSailPointObjectInfo(SailPointObject obj, Map<String,Object> mapModel) {        
        if ( obj != null ) {
            Map<String,Object> base = buildBaseSailPointObjectInfo(obj);
            if ( mapModel == null ) {
                mapModel = new HashMap<String,Object>();
            }
            mapModel.putAll(base);
        }        
    }
}
