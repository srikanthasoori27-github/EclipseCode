package sailpoint.service;

import sailpoint.tools.Util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Service used to persist UI settings on the session.
 * 
 * IIQSAW-2905 -- Isolate UI session data from real session data 
 * by store these under second level of session map with the key of "UI_SESSION_DATA".
 * These keys are listed under ALLOWED_UI_SESSION_KEYS.
 * 
 * However, there are still some navigation keys that need to be stored in the real session.
 * These keys are listed in ALLOWED_NAVIGATION_SESSION_KEYS.
 * We do not allow to retrieve value of Navigation key. 
 * 
 */
public class UISessionStorageService {
    private static final Log log = LogFactory.getLog(UISessionStorageService.class);

    /**
     * The key for UI session data that stored in real session.
     */
    public static final String KEY_UI_SESSION_DATA = "UI_SESSION_DATA";

    /**
     * List of allowed UI session data keys.
     */
    private static final String[] ALLOWED_UI_SESSION_KEYS = {
        "accessRequestSortOrder",
        "workItemListActiveWorkgroupFilterValue",
        "workItemListSortOrder",
        "workItemListSearchData"
    };

    /**
     * List of allowed Navigation session keys.
     */
    private static final String[] ALLOWED_NAVIGATION_SESSION_KEYS = {
        "workItemId",
        "roleToEdit",
        "workflowCaseId",
        "TaskResultId",
        "TaskResultWorkItemId"
    };
    
    private SessionStorage sessionStorage;
    
    /**
     * Constructor.
     */
    public UISessionStorageService(SessionStorage sessionStorage) {
        this.sessionStorage = sessionStorage;
    }

    /**
     * Get a session stored UI persistence value and pop value
     * @param key fetch the value with this key
     * @return Object session stored object
     */
    public Object get(String key) {
        return this.get(key, true);
    }

    /**
     * Get a session stored UI persistence value and pop if requested.
     * @param key fetch the value with this key
     * @param pop if true pop the value from the session so its not here next time
     * @return Object session stored object
     */
    public Object get(String key, boolean pop) {
        //only allow UI session key
        if (!isUiSessionKey(key)) {
            log.error("Trying to retrieve invalid UI session data for: " + key);
            throw new IllegalArgumentException("Trying to retrieve invalid UI session data for:" + key);
        }
        
        Object value = null;

        if (this.sessionStorage != null && Util.isNotNullOrEmpty(key)) {
            Map<String,Object> uiSessionDataMap = (Map<String,Object>) this.sessionStorage.get(KEY_UI_SESSION_DATA);
    
            if (uiSessionDataMap != null) {
                value = uiSessionDataMap.get(key);
                if (value != null && pop) {
                    uiSessionDataMap.remove(key);
                }
            }
        }
        return value;
    }

    /**
     * Store key,objects onto session.
     *
     * @param values Map of string object entries that we want to store on the session
     */
    public void put(Map<String, Object> values) {
        if (!Util.isEmpty(values) && this.sessionStorage != null) {
            for (String key : values.keySet()) {
                if (isUiSessionKey(key)) {
                    //for UI session data, we store it under KEY_UI_SESSION_DATA.
                    Map<String,Object> uiSessionDataMap = (Map<String,Object>) this.sessionStorage.get(KEY_UI_SESSION_DATA);
                    if (uiSessionDataMap == null) {
                        uiSessionDataMap = new HashMap<String, Object>();
                        this.sessionStorage.put(KEY_UI_SESSION_DATA, uiSessionDataMap);
                    }
                    uiSessionDataMap.put(key, values.get(key));
                } else if (isNavigationSessionKey(key)) {
                    //for Navigation data, it needs to be stored in real session.
                    this.sessionStorage.put(key, values.get(key));
                } else {
                    log.error("Trying to store invalid UI session data for: " + key);
                    throw new IllegalArgumentException("Trying to store invalid UI session data for:" + key);
                }
            }
        }
    }
    
    /**
     * determine whether the key is in predefined ALLOWED_UI_SESSION_KEYS.
     */
    private boolean isUiSessionKey(String key) {
        return Arrays.asList(ALLOWED_UI_SESSION_KEYS).contains(key);
    }

    /**
     * determine whether the key is in predefined ALLOWED_NAVIGATION_SESSION_KEYS 
     */
    private boolean isNavigationSessionKey(String key) {
        return Arrays.asList(ALLOWED_NAVIGATION_SESSION_KEYS).contains(key);
    }
}
