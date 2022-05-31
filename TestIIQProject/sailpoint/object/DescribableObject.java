/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Forwarding class that manages objects with descriptions. For 6.2 these
 * classes are: Application, Policy, Bundle, and ManagedAttribute
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
class DescribableObject<E extends SailPointObject> implements Describable {
    private final Describable describableObj;
    private static final Log log = LogFactory.getLog(DescribableObject.class);
    
    DescribableObject(E describableObj) {
        this.describableObj = (Describable)describableObj;
    }
    
    /**
     * Return the Map of localized descriptions.
     */
    public Map<String,String> getDescriptions() {
        return describableObj.getDescriptions();
    }
    
    /**
     * Set the Map of localized descriptions.
     */
    public void setDescriptions(Map<String,String> map) {
        describableObj.setDescriptions(map);
    }
    
    /**
     * Incrementally add one description. This logic is shared between 
     * subclasses that implement the Describable interface.
     * @param locale Locale for which the description is being added
     * @param desc Description
     */
    public void addDescription(String locale, String desc) {
        if (locale != null && desc != null) {
            Map<String,String> descs = describableObj.getDescriptions();
            if (Util.isEmpty(descs)) {
                descs = new HashMap<String,String>();
            }
            descs.put(locale, Util.trimWhitespace(desc));
            describableObj.setDescriptions(descs);
        }
    }
    
    /**
     * Return the description for one locale. This logic is shared between 
     * subclasses that implement the Describable interface.
     */
    public String getDescription(String locale) {
        return getDescription(locale, true);
    }

    /**
     * Return the description for one locale. This logic is shared between
     * subclasses that implement the Describable interface.
     *
     * This will return a sanitized (based on OWASP format/block sanitization) version of the description
     */
    public String getDescription(String locale, boolean fallbackToDefaultLang) {
        String desc = null;
        Map<String, String> map = describableObj.getDescriptions();
        if (!Util.isEmpty(map)) {
            if (!Util.isNullOrEmpty(locale)) {
                desc = map.get(locale);
            }

            // Fall back on the default if the specified locale could not be found
            if (desc == null && fallbackToDefaultLang) {
                String localeName = Localizer.getDefaultLocaleName(Configuration.getSystemConfig());
                desc = map.get(localeName);
            }
        }

        //XSS protection
        return WebUtil.sanitizeHTML(desc);
    }
    
    /**
     * Return the description for one locale.
     */
    public String getDescription(Locale locale) {
        String desc = null;
        if (locale != null)
            desc = getDescription(locale.toString());
        else
            desc = getDescription("");

        return desc;
    }
    
    public void logDeprecationWarning(String s) {
        if (!Util.isNullOrEmpty(s)) {
            log.debug("The setDescription() method for " + this.describableObj.getClass().getName() + " is deprecated.  Use addDescription(String locale, String description) instead.");
        }
    }
}
