package sailpoint.transformer;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;

/**
 * Factory used to recreate the Transformer originally used to create the model. Implementation uses a 
 * very simple Factory pattern to create a Transformer. 
 */
public class TransformerFactory {
    
    private static final String EXCEPTION_STRING = "could not construct class with name: ";
    
    private static final Log log = LogFactory.getLog(TransformerFactory.class);
    
    private TransformerFactory() {}
    
    /**
     * model should contain attributes AbstractTransformer.ATTR_TRANSFORMER_CLASS and 
     * AbstractTransformer.ATTR_TRANSFORMER_OPTIONS in order to correctly create a Transformer
     * implementation tries to construct the transformer first via a (context, map) constructor, 
     * then (context), and finally default constructor.
     * 
     * 
     * @param context a SailPointContext
     * @param model created from a Transformer
     * @return Transformer
     * @throws GeneralException if something expected goes wrong
     * @throws IllegalArgumentException don't pass null arguments
     * @throws IllegalStateException if the factory can not determine what Transformer to create
     */
    public static Transformer<?> getTransformer(SailPointContext context, Map<String, Object> model) throws GeneralException {
        if (context == null || model == null) {
            final String message = "arguments context or model can not be null";
            log.error(message);
            throw new IllegalStateException(message);
        }

        if (! model.containsKey(AbstractTransformer.ATTR_TRANSFORMER_CLASS) &&
            ! model.containsKey("sys." + AbstractTransformer.ATTR_TRANSFORMER_CLASS)) {
            final String message = "model must contain ATTR_TRANSFORMER_CLASS";
            log.error(message);
            throw new IllegalStateException(message);
        }

        if (! model.containsKey(AbstractTransformer.ATTR_TRANSFORMER_OPTIONS) &&
            ! model.containsKey("sys." + AbstractTransformer.ATTR_TRANSFORMER_OPTIONS)) {
            final String message = "model must contain ATTR_TRANSFORMER_OPTIONS";
            log.error(message);
            throw new IllegalStateException(message);
        }

        // djs: Look at both the root level and in the sys namespace for the link transformer
        // that puts everything non-accountish at the root level
        Object clazz =  MapUtil.get(model, AbstractTransformer.ATTR_TRANSFORMER_CLASS);
        if ( clazz == null ) {
            clazz = MapUtil.get(model, "sys." +AbstractTransformer.ATTR_TRANSFORMER_CLASS);
        }

        String transClazzName = Util.otos(clazz);
        Map<String, Object> options = null;
        if (model.containsKey(AbstractTransformer.ATTR_TRANSFORMER_OPTIONS)) {
            options = otoMap(model, AbstractTransformer.ATTR_TRANSFORMER_OPTIONS);
        } else if (model.containsKey("sys." + AbstractTransformer.ATTR_TRANSFORMER_OPTIONS)) {
            options = otoMap(model, "sys." + AbstractTransformer.ATTR_TRANSFORMER_OPTIONS);
        } else {
            options = new HashMap<String, Object>();
        }

        try {
            Class<?> transClass = Class.forName(transClazzName);
            Constructor<?> construct = null;
            Object o = null;

            try {
                //first try constructor with context and options
                construct = transClass.getConstructor(SailPointContext.class, Map.class);
                o = construct.newInstance(context, options);
            } catch (NoSuchMethodException ignore) {
                log.warn("attempt to create transformer with constructor(context, map) failed for class: " + transClazzName);
            }

            // next attempt with context constructor
            if (o == null) {
                try {
                    construct = transClass.getConstructor(SailPointContext.class);
                    o = construct.newInstance(context);
                } catch (NoSuchMethodException ignore) {
                    log.warn("attempt to create transformer with constructor(context) failed for class: " + transClazzName);
                }
            }

            // finally no constructor
            if (o == null) {
                construct = transClass.getConstructor();
                o = construct.newInstance();
            }

            if (o instanceof Transformer)
                return (Transformer<?>)o;

        } catch (Exception e) {
            final String message = EXCEPTION_STRING + transClazzName;
            log.error(message, e);
            throw new IllegalStateException(message, e);
        }

        // should never reach here
        throw new IllegalStateException(EXCEPTION_STRING + transClazzName);
    }

    private static Map<String, Object> otoMap(Map<String, Object> model, String field) throws GeneralException {
        Map<String, Object> retMap = new HashMap<String, Object>();
        
        Object o = MapUtil.get(model, field);
        if (o instanceof Map) {
            retMap = (Map) o;
        }
        
        return retMap;
    }
}
