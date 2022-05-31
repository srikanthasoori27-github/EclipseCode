/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.help.ContextualHelpItem;

/**
 * Service class for retrieving and saving Configuration entries.
 *
 * @author: michael.hide
 */
public class ConfigService {

    // JSON keys
    public static final String ATTRIBUTE = "attribute";
    public static final String LABEL = "label";

    // ColumnConfig properties
    public static final String HEADER_KEY = "headerKey";
    public static final String DATA_INDEX = "dataIndex";
    public static final String RENDERER = "renderer";
    public static final String FIELD_ONLY = "fieldOnly";
    public static final String DATE_STYLE = "dateStyle";
    public static final String SORTABLE = "sortable";
    public static final String HIDDEN = "hidden";
    public static final String PERCENT_WIDTH = "percentWidth";
    public static final String WIDTH = "width";
    public static final String MIN_WIDTH = "minWidth";
    public static final String FIXED = "fixed";
    public static final String HIDEABLE = "hideable";

    private static Log log = LogFactory.getLog(ConfigService.class);
    private static final List<String> ENCRYPTED_FAM_ATTRS = Arrays.asList(Configuration.FAM_CONFIG_PASSWORD, Configuration.FAM_CONFIG_CLIENT_SECRET);
    
    private SailPointContext context;

    public ConfigService(SailPointContext context) {
        this.context = context;
    }
    
    /**
     * Gets a list of Configuration attributes for the Configuration object named {@link sailpoint.object.Configuration.FAM_CONFIG}
     * @return map of attributes
     * @throws GeneralException when bad things happen
     */
    public Map<String, Object> getFAMConfiguration() throws GeneralException {
        Configuration famConfig = getConfiguration(Configuration.FAM_CONFIG);
        Attributes<String,Object> result = famConfig.getAttributes();
        if (result == null) {
            result = new Attributes<>();
            famConfig.setAttributes(result);
        }
        decrypt(result);
        return result;
    }

    /**
     * Saves the FAM configuration as the Configuration object named {@link sailpoint.object.Configuration.FAM_CONFIG}
     * @param map map of attributes to save
     * @see #saveConfigurationAttributes(String, Map)
     * @throws GeneralException when bad things happen
     */
    public void saveFAMConfiguration(Map<String, Object> map) throws GeneralException {
        isErrorRequest(map);
        saveConfigurationAttributes(Configuration.FAM_CONFIG, map);
    }
    
    
    protected Configuration buildConfiguration(Map<String, Object> map) {
        Configuration config = new Configuration();
        Attributes<String, Object> attrs = config.getAttributes();
        if (attrs == null) {
            attrs = new Attributes<>();
            config.setAttributes(attrs);
        }
        if (!Util.isEmpty(map)) {
            attrs.putAll(map);
        }
        return config;
    }
    
    protected void isErrorRequest(Map<String, Object> map) throws GeneralException {
        //TODO remove during real implementation
        if ("error".equals(getConfigUrl(map))) { 
          throw new GeneralException("error me this batman!!!");
        }
    }
    
    protected String getConfigUrl(Map<String, Object> map) {
        return Util.getString(map, Configuration.FAM_CONFIG_URL);
    }
    
    /*
     * Saves all configuration Attributes to the named Configuration object. This method performs
     * a commit transaction. WARNING: This method clears all existing Attributes before saving the new map of Attributes.
     */
    protected void saveConfigurationAttributes(String configName, Map<String, Object> map) throws GeneralException {
        Configuration config = getConfiguration(configName);
        Attributes attrs = config.getAttributes();
        if (attrs == null) {
            attrs = new Attributes();
            config.setAttributes(attrs);
        }
        
        attrs.clear();
        encrypt(map);
        attrs.putAll(map);
        
        context.saveObject(config);
        context.commitTransaction();
    }

    protected Configuration patchConfigurationAttributes(String configName, Map<String, Object> map) throws GeneralException {
        Configuration config = getConfiguration(configName);
        if (config != null) {

            Attributes attrs = config.getAttributes();
            if (attrs == null) {
                attrs = new Attributes();
                config.setAttributes(attrs);
            }

            encrypt(map);
            attrs.putAll(map);

            context.saveObject(config);
            context.commitTransaction();

        } else {
            throw new GeneralException("Config [" + configName + "] does not exist");
        }
        return config;
    }
    
    protected Configuration getConfiguration(String configName) throws GeneralException {
        Configuration config = context.getObjectByName(Configuration.class, configName);
        if (config == null) {
            config = new Configuration();
            config.setName(configName);
        }
        return config;
    }
    
    protected void encrypt(Map<String, Object> map) throws GeneralException {
        crypt(map, new GEFunction() {
            @Override
            public String apply(String x) throws GeneralException {
                return context.encrypt(x);
            }
        });
    }
    
    protected void decrypt(Map<String, Object> map) throws GeneralException {
        crypt(map, new GEFunction() {
            @Override
            public String apply(String x) throws GeneralException {
                return context.decrypt(x);
            }
        });
    }
   
    /* Tried using a lambda function here, but en/decrypt functions throw GeneralException, 
     * and doesn't fit with the Function<S, T> lambda approach. Created this cause it's better than duplicate code */
    interface GEFunction { String apply(String value) throws GeneralException; }
    
    protected void crypt(Map<String, Object> map, GEFunction operation) throws GeneralException {
        if (map == null) {
            return;
        }
        
        for (String attr : ENCRYPTED_FAM_ATTRS) {
            String value = Util.getString(map, attr);
            if (value != null) {
                map.put(attr, operation.apply(value));
            }
        }
    }
    
    /**
     * Return the column configs for the requested key with localized labels.
     *
     * @param key  The key of the column configs in UIConfig.
     * @param locale  The Locale to use for l10n.
     *
     * @return The column configs for the requested key with localized labels, or null if the given
     *     key does not exist in the UIConfig or doesn't contain a list.
     */
    public static List<Map<String,Object>> getColumnConfig(String key, Locale locale) throws GeneralException {
        Object entry = UIConfig.getUIConfig().getObject(key);
        return convertColumnConfigs(entry, locale);
    }

    /**
     * Service method to get a stripped down version of a UIConfig entry.
     *
     * @param keys
     * @return Map of UIConfig entries
     * @throws GeneralException
     */
    public static Map<String, List<Map<String, Object>>> getUIConfigEntries(List<String> keys, Locale locale) throws GeneralException {

        if (log.isDebugEnabled()) {
            log.debug("Getting UIConfig entries: " + Arrays.toString(keys.toArray()));
        }

        HashMap<String, List<Map<String, Object>>> map = new HashMap<String, List<Map<String, Object>>>();

        // if requesting specific keys, loop through and parse out relevant data
        if (keys != null && keys.size() > 0) {
            for (String key : keys) {
                addEntry(UIConfig.getUIConfig().getObject(key), map, key, locale);
            }
        }
        // Otherwise just grab ALL the ColumnConfigs
        else {
            Attributes attrs = UIConfig.getUIConfig().getAttributes();
            List<String> entryKeys = attrs.getKeys();
            for (String key : entryKeys) {
                addEntry(attrs.get(key), map, key, locale);
            }
        }

        return map;
    }

    /**
     * Private utility method to take a UIConfig entry and add it to a collection.
     *
     * @param entry
     * @param map
     * @param key
     * @param locale
     */
    private static void addEntry(Object entry, Map<String, List<Map<String, Object>>> map,
                                 String key, Locale locale) {

        List<Map<String,Object>> columns = convertColumnConfigs(entry, locale);
        if (null != columns) {
            map.put(key, columns);
        }
    }

    /**
     * Convert the given UIConfig entry in a list of ColumnConfigs represented as Maps.
     *
     * @param  entry   The UIConfig entry to convert.
     * @param  locale  The Locale to use.
     *
     * @return A list of Maps representing the ColumnConfigs, or null if the the entry is null
     *     or not a list.
     */
    @SuppressWarnings("rawtypes")
    private static List<Map<String,Object>> convertColumnConfigs(Object entry, Locale locale) {
        ArrayList<Map<String, Object>> columnList = null;

        if (entry != null && entry instanceof List) {
            columnList = new ArrayList<Map<String,Object>>();
            for (Object cfg : (List) entry) {
                if (cfg instanceof ColumnConfig) {
                    columnList.add(convertColumnConfig((ColumnConfig) cfg, locale));
                }
            }
        }

        return columnList;
    }

    /**
     * Converts a full ColumnConfig object into a smaller key:value map that can be consumed by an AngularJS
     * service.  The idea being that with the new UI cards we need some sort of config mechanism, but we don't
     * really need all the attributes of a ColumnConfig (since this won't be used for a grid).  Instead of creating
     * a new config object we'll just repurpose ColumnConfig and strip away the things we don't need.  In fact
     * this set of attributes is larger than what is currently needed, but seems conceivably useful for future
     * implementations.
     *
     * @param cc
     * @param locale
     * @return Map of ColumnConfig entries
     */
    private static Map<String, Object> convertColumnConfig(ColumnConfig cc, Locale locale) {
        if (cc != null) {
            HashMap<String, Object> map = new HashMap<String, Object>();

            map.put(DATA_INDEX, getDataIndex(cc));

            if (cc.getHeaderKey() != null) {
                map.put(HEADER_KEY, cc.getHeaderKey());
                map.put(LABEL, new Message(cc.getHeaderKey()).getLocalizedMessage(locale, null));
            }
            else {
                // If there is no header key, fall back to using the data index for the label
                map.put(LABEL, getDataIndex(cc));
            }

            if (cc.isFieldOnly()) {
                map.put(FIELD_ONLY, cc.isFieldOnly());
            }
            if (cc.getDateStyle() != null) {
                map.put(DATE_STYLE, cc.getDateStyle());
            }
            if (cc.getRenderer() != null) {
                map.put(RENDERER, cc.getRenderer());
            }
            if (cc.getPercentWidth() > -1) {
                map.put(PERCENT_WIDTH, cc.getPercentWidth());
            }
            if (cc.getWidth() > -1) {
                map.put(WIDTH, cc.getWidth());
            }
            if (cc.getMinWidth() > -1) {
                map.put(MIN_WIDTH, cc.getMinWidth());
            }

            map.put(SORTABLE, cc.isSortable());
            map.put(HIDDEN, cc.isHidden());
            if (cc.getFixed() != null) {
                map.put(FIXED, cc.getFixed().name());
            }
            if (cc.isHideable()) {
                map.put(HIDEABLE, cc.isHideable());
            }
            

            return map;
        }
        return null;
    }


    public static Map<String, Object> convertContextualHelpItem(ContextualHelpItem item) {
        if (item != null) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("type", item.getType().name());
            map.put("key", item.getKey());
            map.put("enabled", item.isEnabled());
            map.put("height", item.getHeight());
            map.put("source", item.getSource());
            map.put("width", item.getWidth());
            return map;
        }
        return null;
    }

    /**
     * Helper function to return a value for the dataIndex attribute.
     * Since the ColumnConfig js object depends on dataIndex, we need to
     * try our best to provide one.  Sometimes there are ColumnConfigs without
     * dataIndex so in that case default to the property.
     *
     * @param cc ColumnConfig
     * @return Object (string) value of dataIndex or property
     */
    private static Object getDataIndex(ColumnConfig cc) {
        return cc.getDataIndex() != null ? cc.getDataIndex() : cc.getProperty();
    }

    /**
     * Returns a list of maps containing the available viewable identity attributes along with a localized label.
     *
     * @param locale
     * @return List of metadata maps
     * @throws GeneralException
     */
    public static List<Map<String, Object>> getIdentityDetailsMetadata(Locale locale) throws GeneralException {
        List<String> filterList = UIConfig.getUIConfig().getIdentityViewAttributesList();
        if (log.isDebugEnabled()) {
            log.debug("Building identity view attributes: " + Arrays.toString(filterList.toArray()));
        }
        List<Map<String, Object>> list = new ArrayList();
        for (String attrName : filterList) {
            String displayName = IdentityDetailsService.getLocalizedAttribute(locale, attrName);
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put(ATTRIBUTE, attrName);
            map.put(LABEL, displayName);
            list.add(map);
        }
        return list;
    }

}
