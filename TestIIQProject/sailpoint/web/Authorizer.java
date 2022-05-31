/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.WebResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Authorizes user access to a given url based on the user's capabilities, rights and attributes.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class Authorizer {

    private static final String FILE_EXTENSION_PATTERN = "^(.*\\.)[^./]*$";

    private static Log log = LogFactory.getLog(Authorizer.class);

    /**
     * Urls which should always be accessible.
     */
    private static Set<String> GLOBALLY_ACCESSIBLE_URLS;

    static{
        GLOBALLY_ACCESSIBLE_URLS = new HashSet<String>();
        GLOBALLY_ACCESSIBLE_URLS.add("org.ajax4jsf.resource/org.ajax4jsf.framework.ajax.AjaxScript.jsf");
        GLOBALLY_ACCESSIBLE_URLS.add("state.json");
    }

    private static Authorizer ourInstance;
    private static Date modifiedDate;

    private Map<String, WebResource> _webResourcesMap;

    private static void init() {

        if (ourInstance == null) {
            synchronized (Authorizer.class) {
                if (ourInstance == null) {
                    try {
                        SailPointContext ctx = SailPointFactory.getCurrentContext();
                        Configuration webResourceConf = ctx.getObjectByName(Configuration.class, Configuration.WEB_RESOURCE_CONFIG);
                        List<WebResource> someWebResources = (List<WebResource>) webResourceConf.get(Configuration.WEB_RESOURCES);

                        modifiedDate = webResourceConf.getModified();
                        if (modifiedDate == null) {
                            modifiedDate = webResourceConf.getCreated();
                        }
                        ourInstance = new Authorizer(someWebResources);
                        log.debug("Loaded Authorizer with webResources with Configuration dated " +
                                (modifiedDate ==null ? "null" : modifiedDate.toString()));
                    } catch (Throwable e) {
                        log.fatal("Could not retrieve web resource configuration!", e);
                        ourInstance = new Authorizer(null);
                        modifiedDate = null;
                        log.debug("Loaded Authorizer with empty webResources");
                    }
                }
            }
        }
    }


    /**
     * Default constructor required for mocking in unit tests.  Do not call.
     */
    public Authorizer() {
    }

    private Authorizer(List<WebResource> webResources) {
        _webResourcesMap = new HashMap<String, WebResource>();
        if (webResources != null) {
            for (WebResource res : webResources) {
                String urlKey = normalizeUrl(res.getUrl());
                WebResource existing = _webResourcesMap.get(urlKey);
                // IIQETN-5494 - New unit test has exposed that duplicate definitions in the WebResource
                // configuration overwrite previous definitions instead of merging them.
                if (existing != null) {
                    existing.merge(res);
                } else {
                    _webResourcesMap.put(urlKey, res);
                }
            }
        }
    }
    
    public static String normalizeUrl(String url) {
        // convert a url like identityiq/debug/threads.jsf to identityiq/debug/threads.*
        if (!Util.isNullOrEmpty(url) && !url.endsWith(".*")) {
            // Normalize only if it's non-null and not already normalized
            // example path pattern is directory/directory/file.extension. I only want to change .extension
            // and leave everything else the same
            Pattern p = Pattern.compile(FILE_EXTENSION_PATTERN);
            Matcher m = p.matcher(url);
            if (m.find()) {
                // replace
                url = m.replaceFirst("$1*");
            }
        }
        
        return url;
    }

    public static Authorizer getInstance() {

        if (ourInstance == null) {
            init();
        }

        return ourInstance;
    }

    /**
     * Intended only for unit tests.
     * This forces next call to getInstance() to construct
     * a fresh Authorizer.
     */
    public static void resetInstance() {
        synchronized (Authorizer.class) {
            if (log.isDebugEnabled()) {
                if (ourInstance != null) {
                    log.debug("Clearing webResource authorizations");
                }
            }
            ourInstance = null;
            modifiedDate = null;
        }
    }

    public static void forceRefresh() {
        resetInstance();
    }

    public static void checkRefresh(SailPointContext context) {
        Date newModDate = getModificationDate(context);
        if (newModDate != null) {
            synchronized (Authorizer.class) {
                if (modifiedDate == null || !newModDate.equals(modifiedDate)) {
                    resetInstance();
                }
            }
        }
    }

    /**
     * Return the modification dates for the WebResource Configuration object
     */
    static public Date getModificationDate(SailPointContext context) {
        final String objName = Configuration.WEB_RESOURCE_CONFIG;
        final Class<? extends SailPointObject> objClass = Configuration.class;

        Date result = null;
        try {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", objName));
            List<String> props = new ArrayList<String>();
            props.add("created");
            props.add("modified");

            Iterator<Object[]> it = context.search(objClass, ops, props);
            if (it.hasNext()) {
                Object[] row = it.next();
                if (row == null) {
                    log.error("Null row of " + objName + " " + objClass);
                }
                else {
                    // use mode date if we have it, fall back to create date
                    if (row.length > 1) {
                        result = (Date) row[1];
                    }
                    if (result == null && row.length > 0) {
                        result = (Date) row[0];
                    }
                }
            }

            if (result == null && log.isInfoEnabled()) {
                log.info("No modification date for: " +
                        objClass.getSimpleName() + ":" +
                        objName);
            }
        }
        catch (Throwable t) {
            // shouldn't happen, leave the old one in the cache
            String msg = "Exception trying to get modification date for: " +
                    objClass.getSimpleName() + ":" +
                    objName;
            log.error(msg, t);
        }

        return result;
    }

    /**
     * Determines if a user has access to the current menu item.
     *
     * @param identityCaps Capabilities assigned to the identity
     * @param identityRights Authentication-specific user attributes
     * @return True if the identity has access to this menu item
     */
    public boolean isAuthorized(String url, List<Capability> identityCaps, Collection<String> identityRights,
                                       Map<String, Object> identityAttrs) {

        if (url==null || Capability.hasSystemAdministrator(identityCaps) ||
                GLOBALLY_ACCESSIBLE_URLS.contains(url))
            return true;

        return hasAccess(getResource(url), identityCaps, identityRights, identityAttrs);
    }

    /**
     * Returns true if identity has access to the given url.
     *
     * @param url
     * @param identity
     * @return
     */
    public boolean isAuthorized(String url, Identity identity) {
        if (identity == null)
            return false;
        return isAuthorized(url, identity.getCapabilityManager().getEffectiveCapabilities(), identity.getCapabilityManager().getEffectiveFlattenedRights(), identity.getAttributes());
    }

    
    /**
     * Returns true if the given set of capabilities, rights and identity attributes grant
     * access to the given resourceor any of it's children.
     *
     * @param resource
     * @param identityCaps
     * @param identityRights
     * @param identityAttrs
     * @return
     */
    private boolean hasAccess(WebResource resource, List<Capability> identityCaps, Collection<String> identityRights,
                                       Map<String, Object> identityAttrs){

        if (resource==null || resource.hasAccess(identityCaps, identityRights, identityAttrs))
            return true;

        if (resource.getChildResources() != null){
            for(String dependantUrl : resource.getChildResources()){
                WebResource dependantResource = getResource(dependantUrl);
                if (dependantResource != null && dependantResource.hasAccess(identityCaps, identityRights,
                        identityAttrs))
                    return true;
            }
        }

        return false;
    }

    /**
     * Retrieves WebResource for a given url. Url parameters are ignored. If an
     * exact match is not found the web resource config is checked for
     * wilcard urls which match the given url.
     *
     * @param url The url to match
     * @return Matching web resource or null if not found.
     */
    public WebResource getResource(String url) {

        if (url==null)
            return null;

        if ( url.indexOf('?') >= 0 )
            url = url.substring(0, url.indexOf('?') );

        // get the WR for the given URL. If the URL were already normalized and the keyset is normalized, this is
        // an awesome operation
        WebResource resource = _webResourcesMap.get(normalizeUrl(url));

        // if no exact match is found try and match just the 1st part of the url
        if (resource == null){
            // try to find the most specific wildcard as there
            // may be more than one that matches the url
            int bestMatchLength = 0;

            for(String key : _webResourcesMap.keySet()){
                String modifiedUrl = key.contains("*") ? key.substring(0,key.length() - 1): key;
                if (url.startsWith(modifiedUrl)) {
                    if (modifiedUrl.length() > bestMatchLength) {
                        resource = _webResourcesMap.get(key);
                        bestMatchLength = modifiedUrl.length();
                    }
                }
            }
        }

        return resource;
    }

    /**
     * Returns true if the given capabilities and user rights grant
     * access given the set of required rights. An empty list of
     * required rights always returns true.
     *
     * @param userCaps
     * @param userRights
     * @param requiredRights Rights required for access
     * @return
     */
    public static boolean hasAccess(List<Capability> userCaps,
                                    Collection<String> userRights,
                                    String... requiredRights) {

        boolean ok = false;

        if ((requiredRights == null) || (0 == requiredRights.length)) {
            // assume we don't need anything special
            ok = true;
        }
        else {
            if (Capability.hasSystemAdministrator(userCaps)) {
                // backstage passs!
                ok = true;
            }
            else if (userRights != null) {
                for (int i = 0 ; i < requiredRights.length ; i++) {
                    if (userRights.contains(requiredRights[i])) {
                        ok = true;
                        break;
                    }
                }
            }
        }
        return ok;
    }

    /**
     * Returns true if the given capabilities and user rights grant
     * access given the set of required rights. An empty list of
     * required rights always returns true.
     *
     * @param userCaps
     * @param userRights
     * @param requiredRights
     * @return
     */
    public static boolean hasAccess(List<Capability> userCaps,
                                    Collection<String> userRights,
                                    List<SPRight> requiredRights) {

        String[] requiredRightArray = (null != requiredRights) ? new String[requiredRights.size()] : null;
        if (null != requiredRights) {
            for (int i=0; i<requiredRights.size(); i++) {
                requiredRightArray[i] = requiredRights.get(i).getName();
            }
        }

        return hasAccess(userCaps, userRights, requiredRightArray);
    }
    
    /**
     * Based on isObjectInUserScope() in BaseBean.java
     * 
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    public static boolean hasScopeAccess(SailPointObject object, SailPointContext context) 
            throws GeneralException {
        // this is a new object, no auth needed
        if (object.getId()==null || "".equals(object.getId()))
            return true;

        QueryOptions scopingOptions = new QueryOptions();
        scopingOptions.setScopeResults(true);
        scopingOptions.add(Filter.eq("id", object.getId()));

        // Getting the class straight off the object may return a hibernate
        // proxy.  For now, use the Hibernate utility class to strip the class
        // out of its proxy.
        Class clas = org.hibernate.Hibernate.getClass(object);
        int count = context.countObjects(clas, scopingOptions);
        return (count > 0);
    }
    
}
