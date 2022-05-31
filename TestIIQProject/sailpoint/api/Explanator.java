/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class encapsulating the operations to retrieve display names
 * and localized descriptions for ManagedAttributes.  Also includes
 * utilities to import and export ManagedAttribute definitions 
 * using files.
 *
 * Author: Jeff, based on earlier work from Derry and Dan
 *
 * NOTE:  This was significantly redesigned in 6.0.  Pre 6.0 custom
 * code that used Explanator will no longer work, but there souldn't
 * be any.
 *
 * CACHE INVALIDATION
 *
 * Code that updates ManagedAttributes should call Explanator.refresh
 * to register the change immediately in this JVM.  Cross JVM cache invalidation
 * is done by periodically polling modification dates.  This can
 * work in several ways:
 *
 * 1) Full Cache, Query Sorted Mod Date
 *
 * All ManagedAttribute objects are fully loaded into the cache and
 * the cache records the highest modification date of all objects.  Each
 * refresh interval, a projection search is made to find the highest modification
 * date of all MAs.  Assuming this is indexed, this is a relatively fast operation.
 * If the highest mode data is greater than the caches mod date, all objects
 * whose mode date is higher than the last cache date is reloaded.
 *
 * NOTE: This is how the role cache maintained by CorrelationModel works.
 *
 * This results in a single query each refresh interval, though the query can
 * be stressful if not properly indexed and optimized.  Initial load time will
 * be very expensive, but period cache checks are inexpensive.
 *
 * 2) Incrermental Cache
 *
 * ManagedAttributes are then loaded incrementally into the cache as they are referneced.
 * The load date and modification date of each object is stored in the Explanation
 * Each time we lookup an Explanation we check to see if the load time is greater than
 * the cache timeout interval.  If it is we do a query to get the latest modification date
 * of this object.  If the dates are not the same the obejct is reloaded.  In either case, 
 * the load date is set to the current time so the refresh interval is restarted for this object.
 *
 * This does simple queries, but it will do many of them as objects loaded at
 * various times expire.  The number of hits to the database will be much larger
 * as it runs but the model does not need to be completely loaded.
 *
 * 3) Hybrid 1
 *
 * When the cache is created, the current highest mod date is queried and stored.
 * ManagedAttributes are loaded into the cache as they are referenced.
 * At the refresh interval, we check to see if the highest mode date has changed,
 * if not the cache is retained.  If the highest mod date changed, there
 * are two options:
 *
 *    - immediate refresh
 *       Iterate over all objects in the cache and reload them
 *
 *    - restart
 *       throw the entire cache away and start incremental loading
 *
 * Restart is the easiest but the user will notice the performance degredation as
 * the cache incrementally reloads.  
 *
 * Immediate will have similar problems unless we run it in a background thread.
 * So the refresh can't be done during a call to one of the get() methods, we need
 * to allow users to continue using the currently cached objects as we load the new ones.
 *
 * CONCLUSIONS
 *
 * 2 is how we do most of our cached objects like Configuration and ObjectConfig.  But given
 * the number of objects we'll have here the number of date probing queries worries me.
 *
 * 1 worries me because it will have to be done at system startup time which
 * will block anything that needs explanations until it finishes.
 *
 * 3 with restart is a decent start, but we will need to do background reloading.
 *
 */
package sailpoint.api;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * A class encapsulating the operations to retrieve display names
 * and localized descriptions for ManagedAttributes.  Also includes
 * utilities to import and export ManagedAttribute definitions 
 * using files.
 */
public class Explanator {
    private static final Log log = LogFactory.getLog(Explanator.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Explanation
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * A class holding extra information about an attribute value or
     * permission target.  This includes an alternative value string
     * to display instead of the raw value, commonly used when values
     * are DNs.  It may also include one or more strings of descriptive
     * text keyed by locale so we can support localized descriptions.
     *
     * Currently display names are not localized.
     */
    public static class Explanation {
        
        /**
         * Alternative value to display for an attribute.
         * Not currently used for permissions, if we want to 
         * allow alternative names for targets, then this
         * should be modeled as _displayName.
         */
        private String _displayValue;

        /**
         * Map of localized descriptions keyed by Locale string.
         */
        private Map<String,String> _descriptions;

        /**
         * True if this is a stub Explanation created for
         * a probe for an explanation that wasn't found. By
         * putting this in the cache it prevents us from
         * hitting the db again.
         * !! But what about cache invalidation?  This is
         * probably unreliable.
         */
        boolean _missing;

        /**
         * Designated owner of the ManagedAttribute.
         * This is not related to explaining anything, it is an optimization
         * for certification item ownership.  Rather than have another large cache
         * for that, we can resuse this one.
         */
        String _owner;

        /**
         * Map of classification name to classification displayable name.
         */
        Map<String, String> _classificationNamesMap = new HashMap<String, String>();;

        
        protected Explanation() {
        }

        public boolean isMissing() {
            return _missing;
        }

        protected void setMissing(boolean b) {
            _missing = b;
        }

        public String getDisplayValue() {
            return _displayValue;
        }

        protected void setDisplayValue(String s) {
            _displayValue = s;
        }

        public String getDescription() {
            return getDescription((String)null);
        }

        public Map<String,String> getDescriptions() {
            return _descriptions;
        }

        protected void setDescriptions(Map<String,String> map) {
            _descriptions = map;
        }

        /**
         * This is based on the original code from 
         * ManagedAttribute.getExplanation which supported
         * levels of localization by removing things from Locale
         * string.  This allows descriptions to be defined
         * for a base language without having to duplicate it for
         * country dialects.
         *
         * This will return a sanitized (based on OWASP format/block sanitization) version of the description
         */
        public String getDescription(String locale) {

            String description = null;
            boolean defaultChecked = false;
            if (locale == null) {
                locale = getDefaultLocaleName();
                defaultChecked = true;
            }

            description = getDescriptionInner(locale);

            if (description == null && !defaultChecked) {
                // try one more time with the default
                String dflt = getDefaultLocaleName();
                if (!dflt.equals(locale))
                    description = getDescriptionInner(dflt);
            }

            // TODO: If we didn't get an immediate map hit, 
            // copy the message from the secondary location
            // to the primary key so we don't keep doing this

            //Always sanitize. No reason to allow potential XSS for non-authoritative fields -rap
            return WebUtil.sanitizeHTML(description);
        }

        public String getDescription(Locale locale) {
            String slocale = (locale != null) ? locale.toString() : null;
            return getDescription(slocale);
        }

        /**
         * Lookup a message for a locale string.
         */
        private String getDescriptionInner(String locale) {

            String description = null;
            if (_descriptions != null) {
                while (description == null && locale != null) {
                    description = _descriptions.get(locale);
                    if (description == null) {
                        // chop and retry
                        int idx = locale.lastIndexOf('_');
                        if (idx < 0)
                            locale = null;
                        else
                            locale = locale.substring(0, idx);
                    }
                }
            }
            return description;
        }

        /**
         * Get default locale name from system configuration, or JVM default if not defined. 
         * Uses Localizer implementation.
         * @return Default locale
         */
        public static String getDefaultLocaleName() {
            return Localizer.getDefaultLocaleName(getConfig());
        }

        private static Configuration getConfig() {
            Configuration config = null;
            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                if (context != null) {
                    config = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
                }
            } catch(GeneralException ge) {
                if(log.isWarnEnabled()) {
                    log.warn("Unable to load configuration due to exception: " + ge.getMessage(), ge);
                }
            }
            return config;
        }

        public String getOwner() {
            return _owner;
        }

        public void setOwner(String s) {
            _owner = s;
        }

        @SuppressWarnings("unchecked")
        public List<String> getClassificationNames() {
            return Util.asList(_classificationNamesMap.keySet());
        }

        @SuppressWarnings("unchecked")
        public List<String> getClassificationDisplayableNames() {
            return Util.asList(_classificationNamesMap.values());
        }

        public Map<String, String> getClassificationNamesMap() {
            return _classificationNamesMap;
        }

        public void addClassificationNameAndDisplayableName(String name, String displayableName) {
            _classificationNamesMap.put(name, displayableName);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // ExplanationCache
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Contains the nested Map structure for locating Explanations,
     * and the highest mod date for cache invalidation.
     */
    public static class ExplanationCache {

        /**
         * Multi-level cache of Explanations.
         *
         * The first level is keyed by Application id.
         *
         * The second level is keyed by the name of the account attribute
         * or *permission* if we're talking about permissions.
         * 
         * The third level is keyed by attribute value or permission target.
         * We do not go down to he right level.
         * 
         */
        private Map<String, Map<String, Map<String, Explanation>>> _cache =
            new HashMap<String, Map<String, Map<String, Explanation>>>();

        /**
         * The highest modification date of all ManagedAttribute objects
         * at the time the cache was created.
         */
        private Date _baseline;

        /**
         * Flag to disable reset.
         */
        private boolean _locked;


        public void ExplanationCache() {
        }

        public void setBaseline(Date d) {
            _baseline = d;
        }

        public Date getBaseline() {
            return _baseline;
        }

        public void setLocked(boolean b) {
            _locked = b;
        }

        public boolean isLocked() {
            return _locked;
        }
        
        /**
         * Core accessor for reading the Explanation cache, the arguments must be valid.
         */
        public Explanation get(String appid, boolean permission, String name, String value) {

            Explanation exp = null;

            // I really wish we didn't have to synchronize at this level,
            // look into thread-save Maps so we can sync lower

            synchronized (Explanator.class) {
                Map<String, Map<String,Explanation>> appstuff = _cache.get(appid);
                if (appstuff != null) {
                    Map<String,Explanation> attstuff = null;
                    // a pseudo attribute name to make sure there are no conflicts
                    // between attribute names and permission targets
                    if (permission)
                        attstuff = appstuff.get("*permissions*");
                    else if (name != null)
                        attstuff = appstuff.get(name);

                    if (attstuff != null) {
                        if (permission)
                            exp = attstuff.get(name);
                        else
                            exp = attstuff.get(value);
                    }
                }
            }

            return exp;
        }

        /**
         * Core accessor for putting something in the Explanation cache.
         */
        public void put(String appid, boolean permission, String name, String value, Explanation exp) {

            synchronized (Explanator.class) {

                Map<String, Map<String,Explanation>> appstuff = _cache.get(appid);
                if (appstuff == null) {
                    appstuff = new HashMap<String,Map<String,Explanation>>();
                    _cache.put(appid, appstuff);
                }

                Map<String,Explanation> attstuff = null;
                // a pseudo attribute name to make sure there are no conflicts
                // between attribute names and permission targets
                if (permission)
                    attstuff = appstuff.get("*permissions*");
                else if (name != null)
                    attstuff = appstuff.get(name);

                if (attstuff == null) {
                    attstuff = new HashMap<String,Explanation>();
                    if (permission)
                        appstuff.put("*permissions*", attstuff);
                    else
                        appstuff.put(name, attstuff);
                }

                if (permission)
                    attstuff.put(name, exp);
                else
                    attstuff.put(value, exp);
            }
        }

        public void reset() {

            synchronized (Explanator.class) {
                
                _cache = new HashMap<String, Map<String, Map<String, Explanation>>>();
            }
        }

        /**
         * Debugging utility to test cache updates.
         */
        public void dump() {

            synchronized (Explanator.class) {

                System.out.println("*** ManagedAttribute Cache ***");

                Iterator<String> keys = _cache.keySet().iterator();
                while (keys.hasNext()) {
                    String appid = keys.next();
                    Map<String, Map<String, Explanation>> appstuff = _cache.get(appid);
                    if (appstuff != null) {
                    
                        Iterator<String> names = appstuff.keySet().iterator();
                        while (names.hasNext()) {
                            String name = names.next();
                            Map<String,Explanation> attstuff = appstuff.get(name);
                            if (attstuff != null) {
                            
                                Iterator<String> values = attstuff.keySet().iterator();
                                while (values.hasNext()) {
                                    String value = values.next();
                                    Explanation exp = attstuff.get(value);

                                    if (exp != null) {

                                        System.out.println(appid + " " + name + " " + value);

                                        String dvalue = exp.getDisplayValue();
                                        if (dvalue != null)
                                            System.out.println("  displayValue: " + dvalue);

                                        Map<String,String> descs = exp.getDescriptions();
                                        if (descs != null) {
                                            Iterator<String> langs = descs.keySet().iterator();
                                            while (langs.hasNext()) {
                                                String lang = langs.next();
                                                String desc = descs.get(lang);
                                                System.out.println("  " + lang + ": " + desc);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Singleton cache.
     */
    private static ExplanationCache Cache = new ExplanationCache();

    //////////////////////////////////////////////////////////////////////
    //
    // Explanation Cache Internals
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generic getter that handles both attributes and permissions.
     * This is not expected to be used in code, use one of the simplfied
     * interfaces below.
     */
    private static Explanation get(String appid, boolean permission, String name, String value, String objectType) {

        Explanation exp = null;

        // don't bother if the arguments are not well formed
        if (appid != null && name != null && (permission || (value != null))) {

            // see if it's already in the cache
            exp = Cache.get(appid, permission, name, value);

            if (exp == null || exp.isMissing()) {
                // read it from the db
                ManagedAttribute ma = null;
                if (null != objectType && !permission){
                    ma = getManagedAttribute(appid, permission, null, value, objectType);
                } else {
                    ma = getManagedAttribute(appid, permission, name, value, objectType);
                }
                if (ma == null) {
                    // !! it would be nice to leave something in the cache
                    // so we don't do this every time?  but then when it is invalidated?
                    exp = new Explanation();
                    exp.setMissing(true);
                    Cache.put(appid, permission, name, value, exp);
                }
                else {
                    exp = new Explanation();
                    exp.setDisplayValue(ma.getDisplayableName());
                    exp.setDescriptions(ma.getDescriptions());
                    Identity owner = ma.getOwner();
                    if (owner != null) {
                        exp.setOwner(owner.getName());
                    }
                    for (ObjectClassification classification : Util.safeIterable(ma.getClassifications())) {
                        exp.addClassificationNameAndDisplayableName(classification.getClassification().getName(), classification.getClassification().getDisplayableName());
                    }
                    Cache.put(appid, permission, name, value, exp);
                }
            }

            // DefaultLogicalConnector needs this to come back null for
            // filtering.  Think more about this...
            if (exp != null && exp.isMissing())
                exp = null;
        }

        return exp;
    }

    /**
     * Load a ManagedAttribute we didn't find the cache.
     * Have to assume we've already initalized a thread-local context.
     * Would be safer to create our own temporary context?
     */
    private static ManagedAttribute getManagedAttribute(String appid, boolean permission, String name, String value, String objectType) {

        ManagedAttribute ma = null;

        try {
            SailPointContext con = SailPointFactory.getCurrentContext();
            ma = ManagedAttributer.get(con, appid, permission, name, value, objectType);
        }
        catch (Throwable t) {
            // propagating these doesn't serve any purpose, just
            // return an empty description
            log.error("Unable to get ManagedAttribute", t);
        }

        return ma;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Attribute Explanations
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Most places pass the database id for the Application, but a few places
     * in UI beans want to use names.  So we don't have to have 
     * more method signatures be smart and inspect the id.
     */
    private static String ensureAppId(String id) {
        
        if (!ObjectUtil.isUniqueId(id)) {
            try {
                SailPointContext con = SailPointFactory.getCurrentContext();
                Application app = con.getObjectByName(Application.class, id);
                if (app != null)
                    id = app.getId();
                else {
                    // what now?  might be a custom id, just leave it
                }
            }
            catch (Throwable t) {
                // propagating these doesn't serve any purpose, just
                // return an empty description
                log.error("Unable to get Application", t);
            }
        }

        return id;
    }

    /**
     * Get an Explanation for an attribute value.
     */
    public static Explanation get(String appid, String name, String value) {
    	return get(appid, name, value, null);
    }

    public static Explanation get(String appid, String name, String value, String objectType) {

        appid = ensureAppId(appid);

        return get(appid, false, name, value, objectType);
    }

    public static Explanation get(Application app, String name, String value) {
        
        return (app != null) ? get(app.getId(), false, name, value, null) : null;
    }

    /**
     * Get the display value for an attribute value.
     * Don't have a corresponding concept for Permission targets but we could...
     */
    public static String getDisplayValue(String appid, String name, String value) {
    	return getDisplayValue(appid, name, value, null);
    }

    public static String getDisplayValue(String appid, String name, String value, String objectType) {

        String result = value;

        appid = ensureAppId(appid);

        Explanation exp = get(appid, name, value, objectType);
        if (exp != null) {
            String disp = exp.getDisplayValue();
            if (disp != null)
                result = disp;
        }
        return result;
    }

    public static String getDisplayValue(Application app, String name, String value) {
        
        return (app != null) ? getDisplayValue(app.getId(), name, value) : null;
    }

    /**
     * Get the localized description for an attribute value using
     * varioius fors for the Application id and Locale.
     */
    public static String getDescription(String appid, String name, String value, String locale) {
    	return getDescription(appid, name, value, locale, null);
    }

    public static String getDescription(String appid, String name, String value, String locale, String objectType) {
        String result = null;

        appid = ensureAppId(appid);

        Explanation exp = get(appid, name, value, objectType);
        if (exp != null) 
            result = exp.getDescription(locale);
        return result;
    }

    public static String getDescription(String appid, String name, String value, Locale locale) {
    	return getDescription(appid, name, value, locale, null);
    }
    
    public static String getDescription(String appid, String name, String value, Locale locale, String objectType) {
        String slocale = (locale != null) ? locale.toString() : null;
        return getDescription(appid, name, value, slocale, objectType);
    }

    public static String getDescription(Application app, String name, String value, String locale) {
        
        return (app != null) ? getDescription(app.getId(), name, value, locale) : null;
    }

    public static String getDescription(Application app, String name, String value, Locale locale) {
        
        String slocale = (locale != null) ? locale.toString() : null;
        return getDescription(app, name, value, slocale);
    }

    /**
     * Return a map containing the descriptions of all attributes
     * in the source map  Since the lookup could be expensive, we
     * should only be doing this for attributes that are declared managed.
     * !! Get an Application passed in here.
     */
    public static Map <String, Map<String, String>> 
        getDescriptions(Application app, Map<String,Object> attributes, String locale) {

        Map <String, Map<String, String>> descriptions = null;

        if (attributes != null && !attributes.isEmpty()) {
            try {
                descriptions = new HashMap<String, Map<String,String>>();

                Iterator<String> it = attributes.keySet().iterator();
                while (it.hasNext()) {
                    String attribute = it.next();
                    Object value = attributes.get(attribute);
                    
                    Map<String,String> valueDescs = new HashMap<String,String>();
                    
                    if (value instanceof Collection) {
                        for (Object o : (Collection)value) {
                            if (o != null) {
                                String v = o.toString();
                                String desc = getDescription(app, attribute, v, locale);
                                valueDescs.put(v, desc);
                            }
                        }
                    }
                    else if (value != null) {
                        String v = value.toString();
                        String desc = getDescription(app, attribute, v, locale);
                        valueDescs.put(v, desc);
                    }

                    descriptions.put(attribute, valueDescs);
                }
            }
            catch (Throwable t) {
                log.error("Unable to calculate descriptions for Map", t);
            }
        }
        return descriptions;
    }

    public static Map <String, Map<String, String>> 
        getDescriptions(Application app, Map<String,Object> attributes, Locale locale) {

        String slocale = (locale != null) ? locale.toString() : null;
        return getDescriptions(app, attributes, slocale);
    }


    /**
     * Get the correct description from a map of locales/descriptions that has already been
     * loaded. Doesn't use any caching. 
     * @param descriptionMap Map of descriptions keyed by locale name
     * @param locale Locale to find description for
     * @return String description for locale, or default locale if no description exists, or null
     */
    public static String getDescription(Map<String, String> descriptionMap, Locale locale) {
        Explanation exp = new Explanation();
        exp.setDescriptions(descriptionMap);
        return exp.getDescription(locale);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Permission Explanations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get an Explanation for a Permission target.
     */
    public static Explanation get(String appid, String target) {
        appid = ensureAppId(appid);
        return get(appid, true, target, null, null);
    }

    public static Explanation get(Application app, String target) {

        return (app != null) ? get(app.getId(), true, target, null, null) : null;
    }

    /**
     * Get the localized description for a permission target.
     * @ignore
     * Sigh...now that we support string locales the signatures are
     * ambiguous, have to change the name.
     */
    public static String getPermissionDescriptionx(String appid, String target, String locale) {

        String result = null;
        appid = ensureAppId(appid);
        Explanation exp = get(appid, target);
        if (exp != null) 
            result = exp.getDescription(locale);
        return result;
    }

    public static String getPermissionDescription(String appid, String target, Locale locale) {

        String slocale = (locale != null) ? locale.toString() : null;
        return getPermissionDescriptionx(appid, target, slocale);
    }

    public static String getPermissionDescriptionx(Application app, String target, String locale) {
        
        return (app != null) ? getPermissionDescriptionx(app.getId(), target, locale) : null;
    }

    public static String getPermissionDescription(Application app, String target, Locale locale) {

        String slocale = (locale != null) ? locale.toString() : null;
        return getPermissionDescriptionx(app, target, slocale);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Utility method to determine the baseline date.  Static
     * so we can call it from outside ExplanationCache.
     */
    public static Date getCurrentBaseline(SailPointContext con)
        throws GeneralException {

        // should never be null
        Date baseline = getLastDate(con, "created");

        // may be null if we've just imported or aggregated without editing
        Date lastMod = getLastDate(con, "modified");

        if (lastMod != null && (baseline == null || lastMod.after(baseline)))
            baseline = lastMod;

        return baseline;
    }

    private static Date getLastDate(SailPointContext con, String property) 
        throws GeneralException {

        Date last = null;
        QueryOptions qo = new QueryOptions();
        qo.setResultLimit(1);
        qo.add(Filter.notnull(property));
        qo.addOrdering(property, false);
    
        Iterator<Object[]> it = con.search(ManagedAttribute.class, qo, property);
        if (it.hasNext())
            last = (Date) it.next()[0];
        return last;
    }
     

    /**
     * Called periodically in a background thread to check for changes
     * that would invalidate things in the cache.  See file header
     * comments for an exploration of some of the options.
     *
     * This is currently called by CacheService, but we might
     * want to factor out a specific service if we need control
     * over the refresh interval.
     */
    public static void checkRefresh(SailPointContext con) {

        try {
            // cache can be locked when undergoing major surgery, 
            // disable refresh until it finishes
            if (isRefreshAllowed(con)) {

                Date current = getCurrentBaseline(con);
                if (current == null) {
                    // no objects found, if anythign is left in the cache
                    // it is invalid
                    Date baseline = new Date();
                    if (log.isInfoEnabled()) {
                        log.info("No objects found, cache being reset");
                        log.info("Cache baseline: " + Util.dateToString(baseline));
                    }
                    Cache.reset();
                    Cache.setBaseline(baseline);
                }
                else {
                    Date baseline = Cache.getBaseline();
                    if (baseline == null) {
                        // This state is only supposed to exist when we're initializing
                        // the cache at system startup.  It should be empty, just initialize
                        // the baseline date.
                        if (log.isInfoEnabled()) {
                            log.info("Initializing cache baseline");
                            log.info("Cache baseline: " + Util.dateToString(current));
                        }
                        Cache.setBaseline(current);
                    }
                    else if (!current.equals(baseline)) {
                        // taking the easy way out and just resetting it, need to 
                        // be doing a reload of the currently cached objects instead!!
                        if (log.isInfoEnabled()) {
                            log.info("Modifications found, resetting cache");
                            log.info("Cache baseline: " + Util.dateToString(current));
                        }
                        Cache.reset();
                        Cache.setBaseline(current);
                    }
                    else {
                        if (log.isInfoEnabled())
                            log.info("No modifications found, retaining cache");
                    }
                }
            }
            else {
                if (log.isInfoEnabled())
                    log.info("Cache refresh was disabled");
            }
        }
        catch (Throwable t) {
            // problem geting the baseline date or refreshing the objects
            // Service doesn't care
            log.error("Unable to check cache refresh");
            log.error(t);
        }
    }
    
    /**
     * Determine whether the periodic refresh checking should happen.
     * Refresh is disabled whenever there is some long running process
     * that will be making lots of changes to the ManagedAttribute
     * objects or to the cache itself.  The current examples are:
     *
     *     Explanator.load
     *       - bulk loading the entire cache
     *
     *     Identity Refresh with promoteManagedAttributes
     *       - the refresh task may create MAs
     *
     *     Group Aggregation
     *
     * To determine if either of the tasks is running we look at 
     * TaskResults that are pending.  The locked flag we use for
     * Explanator.load should not be used for tasks because it is
     * too easy for tasks to fail without executing cleanup code which
     * would leave the cache permantely locked.  We have to 
     * probe for task status.
     */
    private static boolean isRefreshAllowed(SailPointContext con) 
        throws GeneralException {

        boolean allowed = true;

        if (Cache.isLocked()) {
            allowed = false;
        }
        else {
            // todo; query tasks
        }

        return allowed;
    }

    /**
     * Called from the "Refresh Managed Attribute Cache" button
     * on the debug page.  Force the cache clear, then call checkRefresh
     * to calculate the latest baseline date.
     */
    public static void refresh(SailPointContext con) {

        Cache.reset();
        Cache.setBaseline(null);
        checkRefresh(con);
    }

    /**
     * Called by things that persist changes to individual ManagedAttributes to
     * refresh the cache in this JVM.  Since this doesn't hook in at the Hibernate
     * level, it isn't completely reliable but as long as we handle it in the 
     * MA importer and the MA update workflow those are the normal cases.  Anything
     * else is console or debug page low level edits that will have to wayt
     * for the cache refresh service to detect.
     *
     * I really don't like putting too much side effect magic down at the Hibernate level
     * thogh we're already doing that for audit.
     */
    public static void refresh(ManagedAttribute ma) {

        // don't bother if the arguments are not well formed
        Application app = ma.getApplication();
        String appid = (app != null) ? app.getId() : null;
        String name = ma.getAttribute();
        String type = ma.getType();
        String value = ma.getValue();
        boolean permission = (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(type));

        if (appid != null && name != null && (permission || (value != null))) {

            // see if it's already in the cache
            Explanation exp = Cache.get(appid, permission, name, value);

            if (exp == null) {
                // here we assume if we didn't find one that we're creating a new one
                exp = new Explanation();
                Cache.put(appid, permission, name, value, exp);
            }

            // old or new, we update the display name and descriptions
            exp.setDisplayValue(ma.getDisplayableName());
            exp.setDescriptions(ma.getDescriptions());
            Identity owner = ma.getOwner();
            if (owner != null) {
                exp.setOwner(owner.getName());
            }
            
            exp.getClassificationNamesMap().clear();
            for (ObjectClassification classification : Util.safeIterable(ma.getClassifications())) {
                exp.addClassificationNameAndDisplayableName(classification.getClassification().getName(), classification.getClassification().getDisplayableName());
            }
        }
    }

    /**
     * Debugging utility to test cache updates.
     */
    public static void dump() {

        Cache.dump();
    }

    /**
     * Do a full load of the MA cache.  This is mostly for testing, but I suppose
     * there might be a reason to pre-load this so we don't add overhead to the UI
     * to bring it in incrementally.
     */
    public static void load(SailPointContext context) {

        int count = 0;

        // Don't let periodic refresh confuse things
        // technically we should be smarter here, if the cache is being refreshed
        // by Servicer at this moment, locking won't do anything and we may do
        // redundant work and/or get concurrent mod exceptions.  This is only for
        // the debug page so I'm not losing too much sleep, but still we should
        // have a semaphore around both this and checkRefresh.  Or should this wait
        // until the cache service finishes so we guarentee that we have the latest stuff
        // in case the service is near the end and new things were added while it was running?
        
        Cache.setLocked(true);
        try {
            QueryOptions ops = null;
            IncrementalObjectIterator<ManagedAttribute> it 
                = new IncrementalObjectIterator<ManagedAttribute>(context, ManagedAttribute.class, ops);

            while (it.hasNext()) {
                ManagedAttribute ma = it.next();
                if (log.isDebugEnabled()) {
                    log.debug("Loading " + ma.getApplication().getName() + " " + 
                              ma.getAttribute() + " " + ma.getValue());
                }

                refresh(ma);

                context.decache(ma);
                // Be a good hibernate citizen...
                count++;
                if (0 == (count % 20)) {
                    context.decache();
                }
            }

            log.info("Loaded " + Util.itoa(count) + " ManagedAttributes");
        }
        catch (Throwable t) {
            log.error("Unable to fully load cache");
            log.error(t);
        }
        finally {
            Cache.setLocked(false);
        }
    }


}
