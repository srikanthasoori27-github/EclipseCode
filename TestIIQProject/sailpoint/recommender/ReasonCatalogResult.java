/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.Locale;
import java.util.Map;

/**
 * Generic representation of a Recommendation Reason Message Catalog Result object.
 * Convenience methods should live here. The entire ReasonCatalogResult object
 * will live in a cache, and refresh at a very long interval as we expect Recommenders
 * won't update the Reason Message Catalog often.
 */
public class ReasonCatalogResult {

    // TODO make the value something more typed than Map?
    /* Structure of the Map looks like:
     * {
     *   "en": {
     *           "translation_key": "This is the message in English",
     *           "key_2": "Another translated key"
     *   },
     *   "fr": {
     *           "translation_key": "Par le vous Frenchie?",
     *           "key2": "anno key 2 Frenchie"
     *   }
     * } 
     */
    private Map<String, Map<String, String>> reasonCatalog;

    public boolean isEmpty() {
        return reasonCatalog == null || reasonCatalog.isEmpty();  
    }
    
    public Map<String, String> getReasonCatalog(Locale locale) {
        return reasonCatalog != null ? reasonCatalog.get(locale.toLanguageTag()) : null;
    }
    
    public void setReasonCatalog(Map<String, Map<String, String>> reasonCatalog) {
        this.reasonCatalog = reasonCatalog;
    }
    
    /* don't need to make this public, use the locale based version of this method */
    Map<String, Map<String, String>> getReasonCatalog() {
        return reasonCatalog;
    }
    
}
