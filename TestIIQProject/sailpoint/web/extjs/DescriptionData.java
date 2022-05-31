/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Class for storing and fetching description data intended to be consumed by the description widget
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class DescriptionData {
    private static final Log log = LogFactory.getLog(DescriptionData.class);
    
    /**
     * List of description information used by the description widget
     */
    private List<Map<String, Object>> descriptionInfo;
    
    /**
     * Build a DescriptionData from a Map of descriptions keyed by locale
     * @param descriptions Map of descriptions keyed by locale
     * @param defaultLocale The default locale for the current IIQ deployment
     */
    public DescriptionData(Map<String, String> descriptions, String defaultLocale) {
        if (Util.isEmpty(descriptions)) {
            descriptionInfo = Collections.emptyList();
        } else {
            descriptionInfo = new ArrayList<Map<String, Object>>();
            Set<String> locales = descriptions.keySet();
            for (String locale : locales) {
                Map<String, Object> descriptionDataMap = new HashMap<String, Object>();
                if (Util.isNullOrEmpty(defaultLocale)) {
                    descriptionDataMap.put("isDefault", false);
                } else {
                    descriptionDataMap.put("isDefault", defaultLocale.equals(locale));
                }
                
                descriptionDataMap.put("locale", locale);
                descriptionDataMap.put("value", WebUtil.safeHTML(descriptions.get(locale)));
                descriptionDataMap.put("displayName", getLocaleDisplayName(locale, defaultLocale));
                descriptionInfo.add(descriptionDataMap);
            }
        }
    }
    
    /**
     * Build a DescriptionData from its JSONified representation
     * @param descriptionJson JSONified representation of this DescriptionData
     */
    public DescriptionData(String descriptionJson) {
        if (Util.isNullOrEmpty(descriptionJson)) {
            descriptionInfo = Collections.emptyList();            
        } else {
            try {
                descriptionInfo = JsonHelper.listOfMapsFromJson(String.class, Object.class, WebUtil.cleanseDescriptionsJSON(descriptionJson));
            } catch (GeneralException e) {
                log.error("Failed to cleanse JSON: " + descriptionJson, e);
                descriptionInfo = Collections.emptyList();
            }
        }
    }
    
    public String getDescriptionsJson() {
        return JsonHelper.toJson(descriptionInfo);
    }
    
    public void setDescriptionsJson(String descriptionsJson) {
        if (Util.isNullOrEmpty(descriptionsJson)) {
            descriptionInfo = Collections.emptyList();
        } else {
            try {
                descriptionsJson = WebUtil.cleanseDescriptionsJSON(descriptionsJson);
                descriptionInfo = JsonHelper.listOfMapsFromJson(String.class, Object.class, descriptionsJson);
            } catch (GeneralException e) {
                log.error("Descriptions could not be properly processed so they will be cleared", e);
                descriptionInfo = Collections.emptyList();
            }            
        }
    }
    
    public Map<String, String> getDescriptionMap() {
        if (Util.isEmpty(descriptionInfo)) {
            return Collections.emptyMap();
        } else {
            Map<String, String> descriptionMap = new HashMap<String, String>();
            for (Map<String, Object> descriptionDataMap : descriptionInfo) {
                descriptionMap.put((String)descriptionDataMap.get("locale"), (String)descriptionDataMap.get("value")); 
            }
            return descriptionMap;
        }
    }
    
    private String getLocaleDisplayName(String locale, String defaultLocale) {
        // Figure out how to display the language to the user
        String displayName;
        Locale localeObj = Localizer.localeStringToLocale(locale);
        
        if (localeObj == null) {
            displayName = locale;
        } else if (Util.isNullOrEmpty(defaultLocale)) {
            displayName = localeObj.getDisplayLanguage();
        } else {
            Locale defaultLocaleObj = Localizer.localeStringToLocale(defaultLocale);
            if (defaultLocaleObj == null) {
                displayName = localeObj.getDisplayLanguage();
            } else {
                displayName = localeObj.getDisplayLanguage(defaultLocaleObj);                        
            }
        }

        return displayName;
    }
}
