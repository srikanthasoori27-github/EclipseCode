/**
 * 
 */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.api.Localizer;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
@Path("localizedAttribute")
public class LocalizedAttributeResource extends BaseResource {
    

    /**
     * Returns a list of locale names from the ObjectConfig
     */
    @GET 
    @Path("localeNames")
    public ListResult getLocaleNames() throws GeneralException {
        authorize(new AllowAllAuthorizer());

        Configuration config = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        List<String> languages = config.getList(Configuration.SUPPORTED_LANGUAGES);
        if (languages != null){
            for (String lang : languages) {
                Map<String, Object> result = new HashMap<String, Object>();
                result.put("name", lang);
                results.add(result);
            }
        }
        return new ListResult(results, results.size());
    }

    
    /** returns the default list of available options for the language chooser **/
    @GET
    @Path("languageOptions")
    public ListResult getLocaleOptions() throws GeneralException {
    	authorize(new AllowAllAuthorizer());
    	
        List<Map<String, Object>> locales = getLocaleMaps();
        
        return new ListResult(locales, locales.size());
    }

    /** returns the default list of available options for a language suggest **/
    @GET
    @Path("languageSuggest")
    public ListResult getLocaleSuggestOptions() throws GeneralException {
        
        //This is used in accountGroupGrid.js
    	authorize(new WebResourceAuthorizer("define/groups/accountGroups.jsf"));
    	
        List<Map<String,Object>> locales = getLocaleMaps();
        return new ListResult(locales, locales.size());
    }
    
    private List<Map<String, Object>> getLocaleMaps() {
        List<Map<String,Object>> locales = new ArrayList<Map<String,Object>>();        

        Localizer localizer = new Localizer(getContext());
        Locale defaultLocale = localizer.getDefaultLocale();   
        String defaultDisplayName = null;
        
        if(defaultLocale != null) {
            Map<String,Object> localeMap = getLocaleMap(defaultLocale, true);
            locales.add(localeMap);
            defaultDisplayName = defaultLocale.getDisplayName();
        }
        
        List<Map<String, String>> availableLocales = localizer.getLocaleList();
        if (availableLocales != null && !availableLocales.isEmpty()) {
            for (Map<String, String> locale : availableLocales) {
                if (locale != null) {
                    if (defaultDisplayName == null) {
                        locales.add(getNonDefaultLocaleMap(locale));
                    } else {
                        String displayName = locale.get("displayName");
                        if (!defaultDisplayName.equals(displayName)) {
                            locales.add(getNonDefaultLocaleMap(locale));                            
                        }
                    }
                }
            }
        }
        
        return locales;
    }
    
    private Map<String, Object> getNonDefaultLocaleMap(Map<String, String> nonDefaultLocaleMap) {
        Map<String, Object> localeMap = new HashMap<String, Object>();
        localeMap.putAll(nonDefaultLocaleMap);
        localeMap.put("isDefault", false);
        return localeMap;
    }
    
    private Map<String, Object> getLocaleMap(Locale locale, boolean isDefaultLocale) {
        Map<String, Object> localeMap;
        if (locale == null) {
            localeMap = null;
        } else {
            localeMap = new HashMap<String, Object>();
            localeMap.put("value", locale.toString());
            localeMap.put("displayName", locale.getDisplayName());
            localeMap.put("isDefault", isDefaultLocale);            
        }
        
        return localeMap;
    }
}
