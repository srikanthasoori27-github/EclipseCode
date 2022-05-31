/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.reflect.Field;
import java.text.Collator;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.server.Environment;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.messages.MobileMessage;


/**
 * Handles the retrieval and formatting of localized messages.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class Internationalizer {

    private static Log log = LogFactory.getLog(Internationalizer.class);

    public static final String SAILPOINT_CUSTOM_BUNDLE = BrandingServiceFactory.getService().getCustomMessageBundle();
    public static final String SAILPOINT_MESSAGES_BUNDLE = BrandingServiceFactory.getService().getMessageBundle();
    public static final String SAILPOINT_HELP_BUNDLE = BrandingServiceFactory.getService().getHelpMessageBundle();
    public static final String SAILPOINT_CONNECTOR_MESSAGES_BUNDLE =
            BrandingServiceFactory.getService().getConnectorMessageBundle();

    // NOTE iiqCustom must always be first so it will override the other bundles
    private static final String[] ALL_BUNDLES = new String[]{SAILPOINT_CUSTOM_BUNDLE,
            SAILPOINT_MESSAGES_BUNDLE, SAILPOINT_HELP_BUNDLE,
            SAILPOINT_CONNECTOR_MESSAGES_BUNDLE};

    // NOTE iiqCustom must always be first so it will override the other bundles
    // These are the bundles that might possible include mobile messages, basically just to exclude connector bundle
    private static final String[] MOBILE_BUNDLES = new String[]{SAILPOINT_CUSTOM_BUNDLE,
            SAILPOINT_MESSAGES_BUNDLE, SAILPOINT_HELP_BUNDLE};

    public static int IIQ_DEFAULT_DATE_STYLE = DateFormat.SHORT;
    public static int IIQ_DEFAULT_TIME_STYLE = DateFormat.SHORT;
    
    public static Comparator<String> INTERNATIONALIZED_STRING_COMPARATOR = 
        new Comparator<String>() {
            public int compare(String p1, String p2) {
                if (null == p1)
                    p1 = "";
                
                if (null == p2)
                    p2 = "";
                
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(p1, p2);
            }
        };

    /**
     * Cache of mobile message key names
     */
    private static HashSet<String> mobileMessageCache;

    static {
        try {
            // fire up the cache and fill it up!
            // put any field in MessageKeys with @MobileMessage annotation in here for fast lookup
            mobileMessageCache = Arrays.stream(MessageKeys.class.getDeclaredFields())
                    .filter(f -> f.getAnnotation(MobileMessage.class) != null)
                    .map(Field::getName)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception ex) {
            // If anything goes wrong, we wont use the cache. Seems unlikely to pass next time too, so I guess
            // all messages are mobile? That's the safest. What could even go wrong here?
            mobileMessageCache = null;
            log.debug("Unable to load mobile message keys into cache", ex);
        }
    }

    /**
     * Returns number localized for the given locale.
     *
     * @param num Number to localize
     * @param locale Locale to format with
     * @return Localized Number, or null if the number parameter is null
     */
    public static String getLocalizedNumber(Number num, Locale locale){
        if (num == null)
            return null;
        return NumberFormat.getInstance(locale != null ? locale : Locale.getDefault()).format(num);
    }

    /**
     * Localizes a given date for the given timezone and local, conditionally stripping off time portion. 
     * Uses default IdentityIQ date styles: Internationalzier.IIQ_DEFAULT_DATE_STYLE,
     * Internationalzier.IIQ_DEFAULT_TIME_STYLE.
     * @param dt  The date
     * @param dateOnly If true, use only the date in the returned string, otherwise use time as well.
     * @param locale The locale or the default locale if null
     * @param timeZone The timezone or default timezone if null
     * @return Localized date
     */
    public static String getLocalizedDate(Date dt, boolean dateOnly, Locale locale, TimeZone timeZone) {
        return getLocalizedDate(dt, IIQ_DEFAULT_DATE_STYLE, (dateOnly) ? null : IIQ_DEFAULT_TIME_STYLE, locale, timeZone);
    }
    /**
     * Localizes a given date for the given timezone and local. Uses default
     * IdentityIQ date styles: Internationalzier.IIQ_DEFAULT_DATE_STYLE,
     * Internationalzier.IIQ_DEFAULT_TIME_STYLE.
     *
     * @param dt The date
     * @param locale The locale or the default locale if null
     * @return Localized date for the given locale using default TimeZone,
     * DateFormat.SHORT for the date and DateFormat.LONG for the time.
     */
    public static String getLocalizedDate(Date dt, Locale locale, TimeZone timezone){
        return getLocalizedDate(dt, IIQ_DEFAULT_DATE_STYLE, IIQ_DEFAULT_TIME_STYLE, locale, timezone);
    }

    /**
     * Localizes a given date for the given format, timezone and local.
     *
     * @param dt The date
     * @param dateStyle Date format
     * @param timeStyle time format
     * @param locale Locale or default if null
     * @param timezone TimeZone or default timezone if null
     * @return Localized date or null if date was null.
     */
    public static String getLocalizedDate(Date dt, Integer dateStyle, Integer timeStyle,
                                          Locale locale, TimeZone timezone){
        if (dt == null)
            return null;
        LocalizedDate ldate = new LocalizedDate(dt, dateStyle, timeStyle);
        return ldate.getLocalizedMessage(locale, timezone);
    }

    /**
     * Checks all bundles for the given key. If the key is
     * found returns formatted string. Incorporates any of
     * the given parameters into the message.
     *
     * @param key Message key, may not be null.
     * @return  Localized, formatted message
     */
    public static String getMessage(String key, Locale locale){

        if (key == null)
            return null;

        String msg = null;
        for(String bundleName : ALL_BUNDLES){
            try{
                if (locale != null)
                    msg = ResourceBundle.getBundle(bundleName, locale).getString(key);
                else
                    msg = ResourceBundle.getBundle(bundleName).getString(key);

                if (msg != null)
                    return msg;
            } catch (MissingResourceException e) {
                // ignore and try next bundle
            }
        }

        // after looking through the SailPoint bundles, look in the plugins
        if (Environment.getEnvironment() != null &&
                Environment.getEnvironment().getPluginsConfiguration() != null &&
                Environment.getEnvironment().getPluginsConfiguration().isEnabled()) {
            Map<String, String> pluginMessages = buildPluginMessageMap(locale);
            if (!Util.isEmpty(pluginMessages) && pluginMessages.containsKey(key)) {
                return pluginMessages.get(key);
            }
        }

        if (msg == null && log.isDebugEnabled())
            log.debug("Couldn't find message for key '"+key+"'");

        return msg;
    }

    /**
     * Return a Map of message key to value for mobile message keys.
     * This will include any messages where the key has the MobileMessage annotation
     * in MessageKeys class, as well as any custom messages

     * @param  locale  The locale of the values to return.
     */
    public static Map<String,String> getMobileMessages(Locale locale) {
        Map<String,String> msgs = new HashMap<String,String>();
        
        for (String bundleName : MOBILE_BUNDLES) {
            boolean includeAll = false;
            if (SAILPOINT_CUSTOM_BUNDLE.equals(bundleName)) {
                includeAll = true;
            }
            
            try {
                ResourceBundle bundle = null;
                if (locale != null) {
                    bundle = ResourceBundle.getBundle(bundleName, locale);
                }
                else {
                    bundle = ResourceBundle.getBundle(bundleName);
                }

                if (null != bundle) {
                    Enumeration<String> keys = bundle.getKeys();
                    while (keys.hasMoreElements()) {
                        String key = keys.nextElement();

                        //bug29637 We load the custom messages first so we must see if those are present and
                        //if so use that message
                        if (includeAll || (!msgs.containsKey(key) && isMobileMessage(key))) {
                            msgs.put(key, bundle.getString(key));
                        }
                    }
                }
            } catch (MissingResourceException e) {
                // ignore and try next bundle
            }
        }
        
        // After iterating through the sailpoint bundles, look in the plugins.
        // The mobile messages will need the plugin messages since plugin angular apps will need to
        // hit the ui message catalog endpoint to get the localized messages.
        if (Environment.getEnvironment() != null &&
                Environment.getEnvironment().getPluginsConfiguration() != null &&
                Environment.getEnvironment().getPluginsConfiguration().isEnabled()) {
            Map<String, String> pluginMessages = buildPluginMessageMap(locale);
            if (!Util.isEmpty(pluginMessages)) {
                msgs.putAll(pluginMessages);
            }
        }

        return msgs;
    }

    /**
     * Helper method to build a map of messages for plugins
     * @param locale The locale
     * @return A Map of key to message strings for all available plugins
     */
    private static Map<String, String> buildPluginMessageMap(Locale locale) {
        Map<String,String> msgs = new HashMap<String,String>();
        if (Environment.getEnvironment().getPluginsCache() != null) {
            for (String pluginName : Environment.getEnvironment().getPluginsCache().getCachedPlugins()) {

                try {
                    if (locale == null) {
                        locale = Locale.getDefault();
                    }

                    ClassLoader loader = Environment.getEnvironment().getPluginsCache().getClassLoader(pluginName);
                    ResourceBundle bundle = ResourceBundle.getBundle(pluginName, locale, loader);
                    if (bundle != null) {
                        Enumeration<String> keys = bundle.getKeys();
                        while (keys.hasMoreElements()) {
                            String key = keys.nextElement();
                            String msg = bundle.getString(key);
                            if (msg != null) {
                                msgs.put(key, msg);
                            }
                        }
                    }

                } catch (MissingResourceException e) {
                    // ignore and try next plugin
                }
            }
        }
        return msgs;

    }

    /**
     * Check if the given key is a mobile message by looking in our cached map.
     * @param key String key for the message
     * @return True if mobile message, otherwise false.
     */
    private static boolean isMobileMessage(String key) {
        // See catch block in static initializer. If something went wrong loading the mobile message keys,
        // it seems likely to just fail again and again. So if that failed, we will just return all keys as mobile.
        return (mobileMessageCache == null || mobileMessageCache.contains(key.toUpperCase()));
    }
}
