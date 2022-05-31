/**
 * 
 */
package sailpoint.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.primefaces.model.UploadedFile;

import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.Filter;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.server.Importer.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class Localizer {
    private static final Log log = LogFactory.getLog(Localizer.class);

    String targetId;
    SailPointContext context;
    Configuration config;
    List<LocalizedAttribute> localizedAttributes;
    Monitor monitor;
    Locale currentLocale;
    /**
     * Indicates we should search both targetName and targetId for LocalizedAttribute
     */
    boolean useTargetName;

    static final Map<String , Class<? extends SailPointObject>> CLASS_MAPPINGS = new HashMap<String, Class<? extends SailPointObject>>() {{
        put("Application", Application.class);
        put("Bundle", Bundle.class);
        put("Classification", Classification.class);
        put("Policy", Policy.class);
    }};


    public static final String ATTR_DESCRIPTION = "description";
    public static final String LOCALE_DEFAULT = "default";
    public static final String LOCALE_DEFAULT_DISPLAY_NAME = "Default";
    public static final String MAP_LOCALE = "locale";
    public static final String MAP_LOCALE_DISPLAY_NAME = "displayName";
    public static final String MAP_VALUE = "value";

    public Localizer(SailPointContext context) {
        this.context = context;
    }

    public Localizer(SailPointContext context, String targetId) {
        this(context);
        this.targetId = targetId;
    }

    public Localizer(SailPointContext context, String targetId, boolean useTargetName) {
        this(context, targetId);
        this.useTargetName = useTargetName;
    }

    public Localizer(SailPointContext context, String targetId, Locale locale) {
        this(context, targetId);
        this.currentLocale = locale;
    }

    public Localizer(SailPointContext context, String targetId, Locale locale, boolean useTargetName) {
        this(context, targetId, locale);
        this.useTargetName = useTargetName;
    }    

    public String getLocalizedValue(String attribute, Locale locale) {
        return this.getLocalizedValue(this.targetId, attribute, locale);
    }

    /**
     * Return localed value, with value sanitized
     * @param targetId
     * @param attribute
     * @param locale
     * @return
     */
    public String getLocalizedValue(String targetId, String attribute, Locale locale) {

        return this.getLocalizedValue(targetId, attribute, locale, true);
    }

    /**
     *
     * @param targetId id of object
     * @param attribute name of attribute
     * @param locale locale to use
     * @param sanitizeValue true to sanitize output
     * @return
     */
    public String getLocalizedValue(String targetId, String attribute, Locale locale, boolean sanitizeValue) {

        this.targetId = targetId;
        String localizedValue = "";
        LocalizedAttribute localizedAttribute = findAttribute(attribute, locale.toString());
        if (localizedAttribute != null) {
            localizedValue = localizedAttribute.getValue();
        } else {
            //Fallback to parent locale, if one exists. Otherwise just get default
            Locale parent = getParentLocale(locale);
            if (parent != null) {
                localizedValue = getLocalizedValue(targetId, attribute, parent);
            } else {
                /** Get the default locale's value **/
                localizedValue = getDefaultLocalizedValue(targetId, attribute);
            }
        }

        if (localizedValue == null) {
            localizedValue = "";
        }

        if (sanitizeValue) {
            localizedValue = WebUtil.sanitizeHTML(localizedValue);
        }
        return localizedValue;
    }
    
    
    /** This is a fallback method.  Returns the defaultLocale's version of the attribute.  Used for when
     * we try to retrieve a localized value for a language that doesn't exist in the database.
     */
    public String getDefaultLocalizedValue(String targetId, String attribute) {
        String localizedValue = null;
        this.targetId = targetId;

        LocalizedAttribute localizedAttribute = findAttribute(attribute, getDefaultLocaleName());

        if(localizedAttribute!=null) {
            localizedValue = localizedAttribute.getValue();
        }
        return localizedValue;
        
    }

    public String getLocalizedValue(SailPointObject object, String attribute, Locale locale) {
        String localizedValue = "";

        if(object!=null) {
            return this.getLocalizedValue(object.getId(), attribute, locale);
        }
        return localizedValue;
    }

    /**
     * Gets the "parent" locale for the given locale. 
     * @param locale Current locale
     * @return New Locale that represents the parent up the locale chain
     */
    private Locale getParentLocale(Locale locale) {
        Locale parent = null;
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();
        
        if (!Util.isNullOrEmpty(variant)) {
            // If there is a variant, get the locale for the language and country
            parent = new Locale(language, country);
        } else if (!Util.isNullOrEmpty(country)) {
            // If we are at at a language/country locale, get the locale for the language 
            parent = new Locale(language);
        } else if (!Util.isEmpty(language)) {
            // If we are at a language locale, nowhere to go, return null
            parent = null;
        }
        return parent;
    }
    
    /** 
     * Populates the LocalizedAttributes from a Map of attributes keyed by locale
     * @param attribute Type of attribute that is being populated (Localizer.ATTR_DESCRIPTION, in most cases)
     * @param localizedValues Map<String, String> of new values keyed by locale
     * @param name optional name to be applied to the LocalizedAttribute.  
     *             Note that the suffix, :<attribute>:<locale> is appended.
     */
    public void buildAttributes(String attribute, Map<String, String> localizedValues, String name, String simpleClassName) {

        if (log.isInfoEnabled())
            log.info("localizedValues: " + (Util.isEmpty(localizedValues) ? "{}" : localizedValues.toString()));

        localizedAttributes = new ArrayList<LocalizedAttribute>();

        if (!Util.isEmpty(localizedValues)) {
            Set<String> locales = localizedValues.keySet();
            for(String locale : locales) {

                if(locale==null) {
                    continue;
                }
                
                String value = localizedValues.get(locale);

                LocalizedAttribute localizedAttribute = findAttribute(attribute, locale);
                if(localizedAttribute==null) {
                    localizedAttribute = buildLocalizedAttribute(attribute, locale, name, simpleClassName);

                    if(name!=null) {
                        localizedAttribute.setName(name + " : " + attribute + " : " + locale);
                    }
                }
                localizedAttribute.setValue(value);
                localizedAttributes.add(localizedAttribute);
            }
            
        }
    }

    /**
     * Create a LocalizedAttribute for the given attribute, locale,
     * and value.  Note that the targetId must be set before calling this
     * method and that this no-ops if the attribute already exists
     * 
     * @param  attribute  The name of the attribute (eg - Localizer.ATTR_DESCRIPTION).
     * @param  locale     The Locale string for this value.
     * @param  value      The localized value.
     * @param  name       An optional name to set on the LocalizedAttribute.
     * @param  simpleClassName The simple class name of the target.
     * 
     * @throws GeneralException  If the targetId is not set on this Localizer.
     */
    public void addLocalizedAttribute(String attribute, String locale, String value, String name, String simpleClassName)
            throws GeneralException {
        
        // This must be set before calling this method.
        if (null == this.targetId) {
            throw new GeneralException("targetId must be set before calling addLocalizedAttribute");
        }
        
        LocalizedAttribute localizedAttribute = findAttribute(attribute, locale);

        if(localizedAttribute==null) {
            localizedAttribute = buildLocalizedAttribute(attribute, locale, name, simpleClassName);

            localizedAttribute.setValue(value);

            if(name!=null) {
                localizedAttribute.setName(name + " : " + attribute + " : " + locale);
            }

            context.saveObject(localizedAttribute);
            context.commitTransaction();
            context.decache(localizedAttribute);
        }
    }

    /**
     * Commits established localized attributes
     * @param targetId
     */
    public void commitAttributes(String targetId) {

        if(targetId==null || targetId.equals("")) {
            if(log.isWarnEnabled())
                log.warn("Unable to save localized attributes due to null target ID.");
            return;
        }

        if(localizedAttributes!=null && !localizedAttributes.isEmpty()) {
            for(LocalizedAttribute attribute : localizedAttributes) {
                try {
                    if(targetId!=null) {
                        attribute.setTargetId(targetId);
                    }

                    context.saveObject(attribute);
                    context.commitTransaction();
                    context.decache(attribute);
                } catch(GeneralException ge) {
                    if (log.isWarnEnabled())
                        log.warn("Unable to save localized attribute for attribute [" +
                                attribute + "] target ID [" + targetId + "] and locale [" +
                                attribute.getLocale() + "]: " + ge.getMessage(), ge);
                }
            }
        }
    }
    
    /**
     * Commits new localized attributes in a manner appropriate for the specified object
     * @param obj
     */
    public void commitAttributes(SailPointObject obj) {
        String targetId = this.targetId;
        
        if (targetId == null) {
            targetId = obj.getId();
        }
        
        if(targetId==null || targetId.equals("")) {
            if(log.isWarnEnabled())
                log.warn("Unable to save localized attributes due to null target ID.");
            return;
        }
        
        // The target class's convention is the name of the class sans the package
        String className = obj.getClass().getName();
        String [] splitName = className.split("\\.");
        String targetClass = splitName[splitName.length-1];
        
        if(localizedAttributes!=null && !localizedAttributes.isEmpty()) {
            for(LocalizedAttribute attribute : localizedAttributes) {
                try {
                    if(targetId!=null) {
                        attribute.setTargetId(targetId);
                    }

                    attribute.setTargetClass(targetClass);
                    attribute.setTargetName(obj.getName());
                    if (attribute.getName() == null) {
                        attribute.setName(attribute.getTargetName() + " : " + attribute.getAttribute() + " : " + attribute.getValue());                        
                    }
                    context.saveObject(attribute);
                    context.commitTransaction();
                    context.decache(attribute);
                } catch(GeneralException ge) {
                    if (log.isWarnEnabled())
                        log.warn("Unable to save localized attribute for attribute [" + 
                                attribute + "] target ID [" + targetId + "] and locale [" +
                                attribute.getLocale() + "]: " + ge.getMessage(), ge);
                }
            }
        }
    }


    /** Finds a LocalizedAttribute value in the database by the targetId, attribute, and locale **/
    public LocalizedAttribute findAttribute(String attribute, String locale) {
        return findAttribute(attribute, locale, false);
    }

    public LocalizedAttribute findAttribute(String attribute, String locale, boolean findDefaultLocale) {
        if(this.targetId==null) {
            log.info("Target ID is null, skipping find");
            return null;
        }

        QueryOptions qo = new QueryOptions();
        if (this.useTargetName) {
            qo.add(Filter.or(Filter.eq("targetId", targetId), Filter.eq("targetName", targetId)));
        } else {
            qo.add(Filter.eq("targetId", targetId));
        }

        qo.add(Filter.eq("attribute", attribute));
        qo.add(Filter.eq("locale", locale));

        try {
            List<LocalizedAttribute> attributes = context.getObjects(LocalizedAttribute.class, qo);
            if(attributes!=null && !attributes.isEmpty()) {
                return attributes.get(0);
            } else if (findDefaultLocale) {
                return findAttribute(attribute, getDefaultLocaleName(), false);
            }

        } catch(GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to find localized attribute for attribute [" + 
                        attribute + "] target ID [" + targetId + "] and locale [" +
                        locale + "]: " + ge.getMessage(), ge);
        }
        return null;
    }

    /** Finds all LocalizedAttribute values in the database for the targetId and attribute **/
    public List<LocalizedAttribute> findAttributes(String attribute) {
        return findAttributes(attribute, false);
    }

    /** 
     * Finds all LocalizedAttribute values in the database for the specified attribute
     * @param attribute Attribute to find -- "description," for example
     * @param checkNames true to return an attribute regardless of whether the targetId is found as an ID or a name 
     */
    public List<LocalizedAttribute> findAttributes(String attribute, boolean checkNames) {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("attribute", attribute));
        if (checkNames) {
            qo.add(Filter.or(Filter.eq("targetId", targetId), Filter.eq("targetName", targetId)));
        } else {
            qo.add(Filter.eq("targetId", targetId));
        }

        try {
            List<LocalizedAttribute> attributes = context.getObjects(LocalizedAttribute.class, qo);
            if(attributes!=null && !attributes.isEmpty()) {
                return attributes;
            }

        } catch(GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to find localized attributes for attribute [" + 
                        attribute + "] and target ID [" + targetId + "]: " + 
                        ge.getMessage(), ge);
        }
        return null;
    }

    public String getAttributesJSON(String attribute) {
        localizedAttributes = this.findAttributes(attribute);
        String json = this.getAttributesJSON();

        if(log.isInfoEnabled())
            log.info(json);

        return json;
    }
    
    /**
     * Return description values map keyed by locale
     * 
     * @param attribute
     * @return
     */
    public Map<String, String> getAttributesMap(String attribute) {
        localizedAttributes = this.findAttributes(attribute);
        
        Map<String,String> attributeMap = new HashMap<String,String>();
        
        if (localizedAttributes != null) {
            for(LocalizedAttribute attr : localizedAttributes) {
                attributeMap.put(attr.getLocale(), attr.getValue());
            }
        }

        return attributeMap;
    }
    
    /**
     * Returns a map of localized attributes keyed by locale.  This differs
     * from getAttributesMap() in that it won't overwrite any transient attributes.
     * This is only useful when the call has invoked Localizer.buildAttributes() prior
     * to calling this method
     * that were added to this Localizer.
     * @param attribute
     * @return
     */
    public Map<String, String> getTransientAttributesMap(String attribute) {
        Map<String,String> attributeMap = new HashMap<String,String>();
        if (localizedAttributes == null) {
            localizedAttributes = this.findAttributes(attribute);            
        }
        
        if (localizedAttributes != null) {
            for(LocalizedAttribute attr : localizedAttributes) {
                if (attribute != null && attribute.equals(attr.getAttribute())) {
                    attributeMap.put(attr.getLocale(), attr.getValue());                    
                }
            }
        }

        return attributeMap;
    }

    public String getAttributesJSON() {
        List<Map<String,String>> attributeMapList = new ArrayList<Map<String,String>>();

        Locale defaultLocale = getDefaultLocale();
        if(defaultLocale!=null) {
            LocalizedAttribute attr = findAttribute(ATTR_DESCRIPTION, defaultLocale.toString());
            if(attr!=null) {
                Map<String,String> defaultMap = new HashMap<String,String>();
                defaultMap.put(MAP_LOCALE, defaultLocale.toString());
                defaultMap.put(MAP_VALUE, attr.getValue());
                defaultMap.put(MAP_LOCALE_DISPLAY_NAME, defaultLocale.getDisplayName());
                defaultMap.put("isDefault", "true");
                
                if(currentLocale!=null && defaultLocale.equals(currentLocale)) {
                    defaultMap.put("isCurrent", "true");
                }
                
                attributeMapList.add(defaultMap);
            }

        }

        Locale[] locales = this.getAvailableLocales();

        if(localizedAttributes!=null && !localizedAttributes.isEmpty()) {
            for(LocalizedAttribute attr : localizedAttributes) {
                
                if(defaultLocale != null && defaultLocale.toString().equals(attr.getLocale())) {
                    continue;
                }
                
                Map<String,String> attributeMap = new HashMap<String,String>();
                attributeMap.put(MAP_LOCALE, attr.getLocale());
                attributeMap.put(MAP_VALUE, attr.getValue());
                
                if(currentLocale!=null && attr.getLocale().equals(currentLocale.toString())) {
                    attributeMap.put("isCurrent", "true");
                }

                for(Locale locale : locales) {
                    if(locale.toString().equals(attr.getLocale())) {
                        attributeMap.put(MAP_LOCALE_DISPLAY_NAME, locale.getDisplayName());
                        break;
                    }                    
                } 
                attributeMapList.add(attributeMap);
            }
        }

        return JsonHelper.toJson(attributeMapList);
    }

    /**
     * Return a Comparator that will sort a list of language maps
     */
    protected Comparator<Locale> getLocaleComparator() {
        return new Comparator<Locale>() {
            public int compare(Locale l1, Locale l2) {
                return l1.getDisplayName().compareTo(l2.getDisplayName());
            }
        };
    }
    
    public Locale[] getAvailableLocales() {
        List<Locale> locales = new ArrayList<Locale>();
        Locale list[] = Locale.getAvailableLocales();
        
        List<String> supportedLanguages = null;
        if(getConfig()!=null) {
            supportedLanguages = (List<String>)getConfig().get(Configuration.SUPPORTED_LANGUAGES);
        }
        
        if(supportedLanguages!=null && !supportedLanguages.isEmpty()) {
            for(Locale locale : list) {
                if(supportedLanguages.contains(locale.toString())) {
                    locales.add(locale);
                }
            }
        }
        
        return locales.toArray(new Locale[locales.size()]);
    }
    
    public boolean isLocaleSupported(String locale) {
        boolean supported = false;
        
        List<String> supportedLanguages = null;
        if(getConfig()!=null) {
            supportedLanguages = (List<String>)getConfig().get(Configuration.SUPPORTED_LANGUAGES);
            supported = supportedLanguages.contains(locale);
        }
        
        return supported;
    }

    /** Utility method called by resources and beans to get the list of all available
     * locales for the app
     */
    public List<Map<String,String>> getLocaleList() {
        Locale list[] = this.getAvailableLocales();

        Arrays.sort(list, 0, list.length, getLocaleComparator());
        List<Map<String,String>> locales = new ArrayList<Map<String,String>>();
        for(int i=0; i<list.length; i++) {
            Map<String,String> localeMap = new HashMap<String,String>();
            Locale locale = list[i];
            localeMap.put("value", locale.toString());
            localeMap.put("displayName", locale.getDisplayName());
            locales.add(localeMap);
        }
        return locales;
    }
    
    /**
     * Utility method called by resources and beans to convert locale-value maps coming out of the system 
     * config to a language list that is suitable for consumption by the javascript-based MultLanguageHTMLEditor
     * @param valuesByLocale Map of values keyed by their locale
     * @return List of values paired with locales -- suitable for consumption by the javascript-based 
     *         MultiLanguageHTMLEditor
     */
    public List<Map<String, Object>> localeValueMapToLanguageList(Map<String, String> valuesByLocale, Locale userLocale) {
        List<Map<String, Object>> localeList = new ArrayList<Map<String, Object>>();
        Set<String> locales = new HashSet<String>();
        if (valuesByLocale != null && !valuesByLocale.isEmpty()) {
            locales.addAll(valuesByLocale.keySet());            
        }
        
        if (!locales.isEmpty()) {
            for (String localeString : locales) {
                boolean isDefault = localeString.equals(getDefaultLocaleName());
                Locale locale = localeStringToLocale(localeString);
                String localeDisplayName = locale.getDisplayLanguage(userLocale);
                if (localeDisplayName != null) {
                    Map<String, Object>currentEntry = new HashMap<String, Object>();
                    currentEntry.put("locale", localeString);
                    currentEntry.put("displayName", localeDisplayName);
                    currentEntry.put("value", valuesByLocale.get(localeString));
                    currentEntry.put("isDefault", isDefault);
                    localeList.add(currentEntry);
                }
            }
        }
        return localeList;
    }

    public Locale getDefaultLocale() {
        return localeStringToLocale(getDefaultLocaleName());
    }

    public String getDefaultLocaleName() {
          return Localizer.getDefaultLocaleName(getConfig());
    }

    /**
     * Builds a BufferedReader using an import file uploaded via the UI
     * and passes it along to the actual processing method.
     * import file
     * @param language   Language of the explanations to be created
     * @return Number of lines with problems (token count, invalid type, etc.)
     * @throws IOException
     * @throws GeneralException
     */
    public int importFile(UploadedFile importFile, String objectType, String language, Monitor monitor)
            throws IOException, GeneralException {
        if (log.isInfoEnabled()) {
            log.info("Importing explanations via UI:");
            log.info("    file: " + importFile.getFileName());
            log.info("    objectType: " + objectType);
            log.info("    language: " + language);
        }

        this.monitor = monitor;

        byte[] fileBytes = new byte[(int) importFile.getSize()];
        fileBytes = importFile.getContents();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), "UTF8"));

        return processImportFile(br, objectType, language);
    }

    /**
     * Does the work of processing the import file, parsing the lines into
     * tokens, validating each line, creating explanation objects using the
     * tokens and saving the explanations to the db.
     *
     * @param br Byte array of the import file's contents
     * @param language  Language of the explanations to be created
     * @return Number of lines with problems (token count, invalid type, etc.)
     * @throws IOException
     * @throws GeneralException
     */
    private int processImportFile(BufferedReader br, String objectType, String language)
            throws IOException, GeneralException {

        Map<String, String> nameIds = new HashMap<String,String>();

        if (br == null)
            throw new GeneralException("No file contents available for localized attribute imports");

        if (language == null)
            throw new GeneralException("No language available for localized attribute imports");

        // build the line iterator that can handle CRLF's within a value
        RFC4180LineIterator it = new RFC4180LineIterator(br);

        // build the RFC-compliant parser 
        RFC4180LineParser parser = new RFC4180LineParser(',');
        parser.tolerateMissingColumns(true);

        String line;
        int badLines = 0;
        int lineNo = 0;
        int updatedDescriptions = 0;
        while ((line = it.readLine()) != null) {
            lineNo++;

            if (log.isInfoEnabled())
                log.info("    importing: " + line);

            // skip comment lines
            if (line.startsWith("#"))
                continue;

            // parse the line of CSV data
            List<String> data = parser.parseLine(line);

            // blank line - no special care needed
            if (data == null)
                continue;

            // if there aren't exactly four tokens, log and continue                
            if (data.size() != 3) {
                badLines++;
                if (log.isInfoEnabled())
                    log.info("Invalid localized attribute import - missing/extra tokens (line " +
                            lineNo + "): " + line);

                continue;
            }

            // make sure the line provides a valid object name and object type
            String objectName;

            try {
                objectName = data.get(0);

                String id = nameIds.get(objectName);

                if(id==null) {
                    QueryOptions qo = new QueryOptions();
                    qo.add(Filter.eq("name", objectName));
                    Iterator<Object[]> rows = context.search(CLASS_MAPPINGS.get(objectType), qo, Arrays.asList("id"));
                    if(rows!=null && rows.hasNext()) {
                        Object[] row = rows.next();
                        id = (String)row[0];
                        nameIds.put(objectName, id);
                    }
                }

                if(id!=null) {                
                    this.targetId = id;
                } else {
                    badLines++;
                    if (log.isInfoEnabled())
                        log.info("Unable to locate object type: " + objectType + 
                                " with name: " + objectName);

                    monitor.info("Error: Unable to locate object type: [" + objectType + 
                            "] with name: [" + objectName + "] for import");

                    continue;
                }
            }
            catch (IllegalArgumentException e) {
                badLines++;
                if (log.isInfoEnabled())
                    log.info("Invalid localized attribute import - invalid object name (line " +
                            lineNo + ") and object type, line: " + line);

                monitor.info("Invalid localized attribute import - invalid object name (line " +
                        lineNo + ") and object type, line: " + line);

                continue;
            }

            // validate the description, first making sure it's not null
            String desc = data.get(2);
            if (desc == null) {
                // don't count as a bad line since we have a workaround
                if (log.isInfoEnabled())
                    log.info("Problematic explanation import - description is null (line " +
                            lineNo + ")");

                monitor.info("Problematic explanation import - description is null (line " +
                        lineNo + ")");

                desc = "";
            }

            // max length for a description is 1024
            if (desc.length() > 1024) {
                // don't count as a bad line since we have a workaround
                if (log.isInfoEnabled())
                    log.info("Problematic explanation import - description is too long (line " +
                            lineNo + ")");

                desc = desc.substring(0, 1020) + "...";
                data.remove(2);
                data.add(2, desc);
            }

            // validation checks out, so run with it
            processData(data, language, objectName, objectType);
            String attribute = data.get(1);
            if (!Util.isNullOrEmpty(attribute)) {
                attribute = attribute.trim();
                if (ATTR_DESCRIPTION.equals(attribute)) {
                    updatedDescriptions++;
                }
            }
        }

        if (log.isInfoEnabled()) {
            log.info("Import complete:");
            log.info("    total lines processed: " + lineNo);
            log.info("    bad data lines: " + badLines);
            log.info("    localized attributes created: " + (lineNo - badLines));
            log.info("    descriptions updated: " + updatedDescriptions);
        }

        monitor.info("Import complete:");
        monitor.info("    total lines processed: " + lineNo);
        monitor.info("    bad data lines: " + badLines);
        monitor.info("    localized attributes created: " + (lineNo - badLines));
        monitor.info("    descriptions updated: " + updatedDescriptions);

        return badLines;
    }

    /**
     * Takes the import data and either updates any existing explanation
     * or creates a new one.  Note the decache() after committing the
     * transaction. This is protection against memory issues when importing
     * large files.
     *
     * @param data String array of a line of import data
     * @throws GeneralException
     */
    private void processData(List<String> data, String locale, String name, String simpleClassName)
            throws GeneralException {

        String attribute = data.get(1);
        String value = data.get(2);

        LocalizedAttribute localizedAttribute = findAttribute(attribute, locale);
        if(localizedAttribute==null) {
            localizedAttribute = buildLocalizedAttribute(attribute, locale, name, simpleClassName);
            if (null != name) {
                localizedAttribute.setName(name + " : " + attribute + " : " + locale);
            }
        }
        localizedAttribute.setValue(value);

        try {
            if (log.isInfoEnabled())
                log.info("Importing " + localizedAttribute.toString());

            if (monitor != null)
                monitor.info("Successfully Imported "+ attribute + " on " + data.get(0) + " for locale: " + locale + "\n  text: " + value + "\n");


            context.saveObject(localizedAttribute);
            if (ATTR_DESCRIPTION.equals(attribute.trim())){
                Describable updatedObj = Describer.updateDescription(context, localizedAttribute);
                context.commitTransaction();
                context.decache((SailPointObject) updatedObj);
                monitor.info("Updating description on object of type " + simpleClassName + " named "+ data.get(0) + " for locale: " + locale + "\n  text: " + value + "\n");
            } else {
                // Only needed in this case 
                // because Describer.updateDescription() does this otherwise
                context.commitTransaction(); 
            }
            context.decache(localizedAttribute);
        }
        catch (GeneralException ex) {
            if (log.isErrorEnabled()) {
                log.error("Unable to save explanation '" + localizedAttribute.toString() + 
                        "': " + ex.getMessage(), ex);
            }
        }
        catch (ClassNotFoundException ex) {
            if (log.isErrorEnabled()) {
                log.error("Unable to save explanation '" + localizedAttribute.toString() + 
                        "' because its class is not valid: " + ex.getMessage(), ex);
            }
        }
    }

    public List<LocalizedAttribute> getLocalizedAttributes() {
        return localizedAttributes;
    }

    public void setLocalizedAttributes(List<LocalizedAttribute> localizedAttributes) {
        this.localizedAttributes = localizedAttributes;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Configuration getConfig() {
        if(config==null) {
            try {
                config = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            } catch(GeneralException ge) {
                if(log.isWarnEnabled()) {
                    log.warn("Unable to load configuration due to exception: " + ge.getMessage(), ge);
                }
            }
        }
        return config;
    }
    
    /**
     * Utility to convert a locale that's been dumped to a String back into its original form
     * @param localeString The output of a call to Locale.toString()
     * @return Locale appropriate to the output of a call to Locale.toString()
     */
    public static Locale localeStringToLocale(String localeString) {
        Locale locale = null;
        if(localeString!=null) {
            StringTokenizer tokenizer = new StringTokenizer(localeString, "_");
            String language = tokenizer.nextToken();
            String country = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
            String variant = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";                
            locale = new Locale(language, country, variant);
        }
        return locale;
    }
    
    public static String getDefaultLocaleName(Configuration systemConfig) {
        String defaultLocaleName = null;
        if (systemConfig != null) {
            defaultLocaleName = systemConfig.getString(Configuration.DEFAULT_LANGUAGE);
        }

        // This is to support legacy databases.  If the defaultLanguage is unavailable 
        // or set to "default" just assume the server's locale
        if (defaultLocaleName == null || defaultLocaleName.equals("default")) {
            defaultLocaleName = Locale.getDefault().toString();
        }

        return defaultLocaleName;
    }

    /**
     * Builds a LocalizedAttribute object from the specified parameters. Note that the
     * value is not set by this method.
     *
     * @param attribute The attribute name.
     * @param locale The locale.
     * @param name The target name.
     * @param simpleClassName The simple class name of the target.
     * @return The new localized attribute object.
     */
    public LocalizedAttribute buildLocalizedAttribute(String attribute, String locale, String name, String simpleClassName) {
        LocalizedAttribute localizedAttribute = new LocalizedAttribute();
        localizedAttribute.setTargetId(targetId);
        localizedAttribute.setTargetName(name);
        localizedAttribute.setTargetClass(simpleClassName);
        localizedAttribute.setLocale(locale);
        localizedAttribute.setAttribute(attribute);

        return localizedAttribute;
    }


}
