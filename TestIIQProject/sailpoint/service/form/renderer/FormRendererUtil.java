package sailpoint.service.form.renderer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import sailpoint.api.SailPointContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.workflow.WorkflowBean;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author: pholcomb
 * A Utility class for the FormRenderers that allows us to carry around utility items like the locale,
 * timezone, form, etc...  Provides utility functions for fields and localization
 */
public class FormRendererUtil {

    private static final Log log = LogFactory.getLog(FormRendererUtil.class);

    SailPointContext _context;
    /**
     * Underlying form object.
     */
    Form _form;


    /**
     * Map of the state needed to reconstruct the FormBean.
     */
    private Map<String,Object> _formBeanState;

    /**
     * Name of a class implementing the FormBean interface that will be used by
     * REST requests handle dynamic allowed values.
     */
    private String _formBeanClass;

    private Locale _locale;
    private TimeZone _timezone;

    public FormRendererUtil(SailPointContext context, Form form, String formBeanClass, Map<String,Object> formBeanState, Locale locale, TimeZone tz) {
        _context = context;
        _form = form;
        _formBeanState = formBeanState;
        _formBeanClass = formBeanClass;
        _locale = locale;
        _timezone = tz;
    }


    /**
     * In some cases we may want to the field value to be populated with
     * something other than the ID of the selected object. In those cases
     * we allow the form creator to specify an attribute which indicates
     * the name of the property to use as the value. Most often this will
     * be the object name.
     *
     * Note: this is a very rough implementation so as of 5.0 we only support
     * the name property.
     *
     * @param field
     * @param value
     * @return
     */
    public Object getObjectValue(Field field, Object value) {

        // Get the property to use to persist this field.
        String property = (String)field.getAttribute(Field.ATTR_VALUE_PROPERTY);
        if (property == null && useNameAsValue(field.getTypeClass(), field))
            property = Field.ATTR_VALUE_PROPERTY_NAME;

        Class spClass = field.getTypeClass();

        // jfb: If the property is NULL (meaning we should return the ID) we could probably
        // just skip this query. I'm not 100% that the value will always be the ID
        // and it's very late in 6.1, so I'm going to avoid that optimization for now.
        try {
            //TODO: Should we use the property to determine what to look up as? -rap
            SailPointObject obj = getContext().getObjectById(spClass, (String)value);
            if (obj != null){
                value = Field.ATTR_VALUE_PROPERTY_NAME.equals(property) ? obj.getName() : obj.getId();
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return value;
    }

    public boolean useNameAsValue(Class<?> spClass, Field f) {
        boolean nameUnique = false;
        try {
            Object o = spClass.newInstance();
            if(o instanceof SailPointObject) {
                SailPointObject spo = (SailPointObject)o;
                //If the name is unique, it is possible to use the name as the post value.
                nameUnique = spo.isNameUnique();
            }
        } catch (InstantiationException e) {
            log.warn("Could not instantiate: " + spClass.getSimpleName());
        } catch (IllegalAccessException e) {
            log.warn("Method useNameAsValue does not have access to: " + spClass.getSimpleName());
        }

        //If the base path is supplied, it is assumed that this is model backed
        //Unless the field arg says to use id, we will  use name. As of now, we only allow
        //name and id.
        boolean modelBacked =
                (_form !=null && _form.hasBasePath() &&
                        !FormRenderer.DEFAULT_POST_VALUE.equalsIgnoreCase((String)f.getAttribute(Field.ATTR_VALUE_PROPERTY)));

        boolean isWorkflowConfigForm = _formBeanClass == WorkflowBean.class.getName();

        return (nameUnique && (modelBacked || isWorkflowConfigForm));
    }

    /**
     * Attempts to localize a message key.
     * @param messageKey The key to localize.
     * @return The translated message if a translation exists, the original message key otherwise.
     */
    public String localizedMessage(String messageKey, Object... params)
    {
        // check if messageKey is json
        try {
            Gson gson = new Gson();
            Type collectionType = new TypeToken<Map<String, Object>>() { }.getType();
            Map<String, Object> localLabelMap = gson.fromJson(messageKey, collectionType);
            if (localLabelMap != null) {
                messageKey = (String)localLabelMap.get("messageKey");
                List<String> messageKeyArgs = (List<String>)localLabelMap.get("args");
                if (messageKeyArgs != null) {
                    params = messageKeyArgs.toArray();
                }
            }
        }
        catch(JsonSyntaxException jse) {
            // must not be json. that's ok. just move on.
        }

        Message msg = Message.localize(messageKey, params);
        return msg.getLocalizedMessage(_locale, _timezone);
    }

    public SailPointContext getContext() {
        return _context;
    }

    public void setContext(SailPointContext context) {
        this._context = context;
    }

    public String getFormBeanClass() {
        return _formBeanClass;
    }

    public Locale getLocale() {
        return _locale;
    }

    public Map<String, Object> getFormBeanState() {
        return _formBeanState;
    }

    public TimeZone getTimezone() {
        return _timezone;
    }

    public Form getForm() {
        return _form;
    }

    public void setForm(Form form) {
        this._form = form;
    }
}
