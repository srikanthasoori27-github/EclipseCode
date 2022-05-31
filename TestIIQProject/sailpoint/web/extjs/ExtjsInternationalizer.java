/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//import sun.text.resources.LocaleData;

/**
 * Utility to used to retrieve the correct extjs language include file
 * and date formats.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ExtjsInternationalizer {

    private static Log log = LogFactory.getLog(ExtjsInternationalizer.class);

    private final static String EXT_LOCALE_FILE = "extLocaleFile";
    private final static String EXT_DATE_FMT = "extDateFormat";
    private final static String EXT_TIME_FMT = "extTimeFormat";

    private static Map<String, String> LOCALE_FILES = new HashMap<String, String>();

    /**
     * Mapping of locales to ext localization imports. This should be updated when
     * we upgrade if new localization files are added to ext.
     */
    static {
        LOCALE_FILES.put("af", "ext-lang-af.js");
        LOCALE_FILES.put("bg", "ext-lang-bg.js");
        LOCALE_FILES.put("ca", "ext-lang-ca.js");
        LOCALE_FILES.put("cs", "ext-lang-cs.js");
        LOCALE_FILES.put("da", "ext-lang-da.js");
        LOCALE_FILES.put("de", "ext-lang-de.js");
        LOCALE_FILES.put("el_GR", "ext-lang-el_GR.js");
        LOCALE_FILES.put("en", "ext-lang-en.js");
        LOCALE_FILES.put("en_AU", "ext-lang-en_AU.js");
        LOCALE_FILES.put("en_GB", "ext-lang-en_GB.js");
        LOCALE_FILES.put("en_UK", "ext-lang-en_GB.js");
        LOCALE_FILES.put("es", "ext-lang-es.js");
        LOCALE_FILES.put("fa", "ext-lang-fa.js");
        LOCALE_FILES.put("fr", "ext-lang-fr.js");
        LOCALE_FILES.put("fr_CA", "ext-lang-fr_CA.js");
        LOCALE_FILES.put("gr", "ext-lang-gr.js");
        LOCALE_FILES.put("he", "ext-lang-he.js");
        LOCALE_FILES.put("hr", "ext-lang-hr.js");
        LOCALE_FILES.put("hu", "ext-lang-hu.js");
        LOCALE_FILES.put("id", "ext-lang-id.js");
        LOCALE_FILES.put("it", "ext-lang-it.js");
        LOCALE_FILES.put("ja", "ext-lang-ja.js");
        LOCALE_FILES.put("ko", "ext-lang-ko.js");
        LOCALE_FILES.put("lt", "ext-lang-lt.js");
        LOCALE_FILES.put("lv", "ext-lang-lv.js");
        LOCALE_FILES.put("mk", "ext-lang-mk.js");
        LOCALE_FILES.put("nl", "ext-lang-nl.js");
        LOCALE_FILES.put("no_NB", "ext-lang-no_NB.js");
        LOCALE_FILES.put("no_NN", "ext-lang-no_NN.js");
        LOCALE_FILES.put("pl", "ext-lang-pl.js");
        LOCALE_FILES.put("pt", "ext-lang-pt.js");
        LOCALE_FILES.put("pt_BR", "ext-lang-pt_BR.js");
        LOCALE_FILES.put("ro", "ext-lang-ro.js");
        LOCALE_FILES.put("ru", "ext-lang-ru.js");
        LOCALE_FILES.put("sk", "ext-lang-sk.js");
        LOCALE_FILES.put("sl", "ext-lang-sl.js");
        LOCALE_FILES.put("sr", "ext-lang-sr.js");
        LOCALE_FILES.put("sr_RS", "ext-lang-sr_RS.js");
        LOCALE_FILES.put("sv_SE", "ext-lang-sv_SE.js");
        LOCALE_FILES.put("th", "ext-lang-th.js");
        LOCALE_FILES.put("tr", "ext-lang-tr.js");
        LOCALE_FILES.put("ukr", "ext-lang-ukr.js");
        LOCALE_FILES.put("vn", "ext-lang-vn.js");
        LOCALE_FILES.put("zh_CN", "ext-lang-zh_CN.js");
        LOCALE_FILES.put("zh_TW", "ext-lang-zh_TW.js");
    }

    private static String[][] conversionTable = new String[][]{{"yyyy", "Y"}, {"yy", "Y"}, {"MMMMM", "F"}, {"MMMM", "F"},
            {"MMM", "M"}, {"MM", "m"}, {"M", "n"}, {"EEEEEE", "l"}, {"EEEEE", "l"}, {"EEEE", "l"}, {"EEE", "D"}, {"dd", "d"},
            {"HH", "H"}, {"mm", "i"}, {"ss", "s"}, {"hh", "h"}, {"A", "a"}, {"S", "u"}};

    /**
     * Converts java date format to PHP date format
     * (used in extjs)
     *
     * @param javaFormat e.g. dd-MM-yyyy
     * @return e.g. d-m-y
     */
    public static String convertDateFormat(String javaFormat) {
        String result = javaFormat;
        for (int i = 0; i < conversionTable.length; i++) {
            result = result.replaceAll(conversionTable[i][0], conversionTable[i][1]);
        }
        return result;
    }

    private String localeFile;
    private String dateFormat;
    private String timeFormat;

    /**
     * Gets date time format patterns for user's locale.
     */
    private void initDateTime() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map sessionMap = ctx.getExternalContext().getSessionMap();

        if (sessionMap.containsKey(EXT_DATE_FMT) && sessionMap.get(EXT_DATE_FMT) != null &&
            sessionMap.containsKey(EXT_TIME_FMT) && sessionMap.get(EXT_TIME_FMT) != null) {

            // jsl - added this so we don't keep regenerating on every
            // new page request, the sessinoMap cache wasn't being very effective
            dateFormat = (String)sessionMap.get(EXT_DATE_FMT);
            timeFormat = (String)sessionMap.get(EXT_TIME_FMT);
        }
        else if (sessionMap.containsKey(EXT_LOCALE_FILE) && sessionMap.get(EXT_LOCALE_FILE) != null) {

            // old way - requires LocaleData not available in JDK 1.6
            /*
            ResourceBundle r = LocaleData.getLocaleElements(ctx.getViewRoot().getLocale());
            String[] dateTimePatterns = r.getStringArray("DateTimePatterns");

            dateFormat = convertDateFormat(dateTimePatterns[DateFormat.SHORT + 4]);
            timeFormat = convertDateFormat(dateTimePatterns[DateFormat.MEDIUM]);
            */
            
            Locale locale = ctx.getViewRoot().getLocale();

            // new way - downcasts to SimpleDataFormat to get the pattern, 
            // this is still not considered proper, but I couldn't find a better way

            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
            if (df instanceof SimpleDateFormat)
                dateFormat = ((SimpleDateFormat)df).toPattern();
            else {
                log.error("Unable to determine Locale data format pattern!");
                // default to something
                dateFormat = "M/d/yy";
            }
            dateFormat = convertDateFormat(dateFormat);

            df = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale);
            if (df instanceof SimpleDateFormat)
                timeFormat = ((SimpleDateFormat)df).toPattern();
            else {
                log.error("Unable to determine Locale data format pattern!");
                // default to something
                timeFormat = "h:mm:ss a";
            }
            timeFormat = convertDateFormat(timeFormat);

            sessionMap.put(EXT_DATE_FMT, dateFormat);
            sessionMap.put(EXT_TIME_FMT, timeFormat);
        }

    }

    /**
     * Looks for a file with matches the user's locale. If there's a country
     * language match, we return that file, otherwise we return the file
     * matching the language. If the file is for US english we return null
     * to avoid importing the js file.
     *
     * @return File name of the javascript language file to import, or
     *         null if the default english should be used
     */
    public String lookupExtjsLocaleFile() {
        FacesContext ctx = FacesContext.getCurrentInstance();

        String lang = null;
        String country = null;

        Locale userLocale = ctx.getViewRoot().getLocale();

        if (userLocale == null)
            userLocale = Locale.getDefault();

        lang = userLocale.getLanguage();
        country = userLocale.getCountry();

        // check for the full locale string
        String fullLocale = country != null ? lang + "_" + country : lang;
        if (LOCALE_FILES.containsKey(fullLocale))
            localeFile = LOCALE_FILES.get(fullLocale);

        // check for just the language with no country specifier
        if (localeFile == null && lang != null && LOCALE_FILES.containsKey(lang))
            localeFile = LOCALE_FILES.get(lang);

        // Look for a language match regardless of the country
        if (localeFile == null) {
            for (String localeKey : LOCALE_FILES.keySet()) {
                if (localeKey.substring(0, 2).equals(lang))
                    localeFile = LOCALE_FILES.get(localeKey);
            }
        }

        if (localeFile == null)
            localeFile = "en";

        return localeFile;
    }

    /**
     * Returns name of file to import for extjs localization. Returns
     * null if the file would be us english. 
     *
     * @return File name of the javascript language file to import, or
     *         null if the default english should be used
     */
    public String getExtjsLocaleFile() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map sessionMap = ctx.getExternalContext().getSessionMap();
        
        if (localeFile != null)
            return localeFile;

        if (sessionMap.containsKey(EXT_LOCALE_FILE) && sessionMap.get(EXT_LOCALE_FILE) != null) {
            localeFile = sessionMap.get(EXT_LOCALE_FILE).toString();            
        } else {
            localeFile = lookupExtjsLocaleFile();
            sessionMap.put(EXT_LOCALE_FILE, localeFile);
        }

        return localeFile;
    }

    /**
     * Date format for user's locale in ext's date format.
     *
     * @return  date format string
     */
    public String getExtjsDateFormat() {
        if (dateFormat == null)
            initDateTime();
        return dateFormat;
    }

    /**
     * Time format for user's locale in ext's date format.
     *
     * @return  date format string
     */
    public String getExtjsTimeFormat() {
        if (timeFormat == null)
            initDateTime();
        return timeFormat;
    }

    /**
     * DateTime format for user's locale in ext's date format.
     *
     * @return  date format string
     */
    public String getExtjsDateTimeFormat() {
        if (dateFormat == null)
            initDateTime();

        return this.dateFormat + " " + timeFormat;
    }

}
