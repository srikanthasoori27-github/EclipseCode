/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.URLUtil;
import sailpoint.tools.Util;

/**
 * Project: identityiq
 * Author: michael.hide
 * Created: 5/6/14 1:50 PM
 */
public class RedirectService {
    /*
     * Public constants also used in PageAuthFilter
     */
    public static final String HASH_TOKEN = "redirect.hash";
    public static final String ANCHOR_TOKEN = "#/";
    public static final String QUERY_SEP = "&";
    public static final String EQUALS_SEP = "=";

    /** rp1 stands for RedirectPage1 */
    public static final String PAGE_KEY = "rp1";
    /** rp2 stands for RedirectPage2 */
    public static final String HASH_KEY = "rp2";

    private static final Log log = LogFactory.getLog(RedirectService.class);
    private URI url;
    private String qStr;
    private StringBuilder newUrl;

    /**
     * Formats the info from RedirectService into the target URL with named anchor
     *
     * @param scheme HttPServletRequest.getScheme()
     * @param server HttPServletRequest.getServerName()
     * @param port   HttPServletRequest.getServerPort()
     * @param path   HttPServletRequest.getContextPath()
     * @param query  HttPServletRequest.getQueryString()
     * @param page   The resource path/file to redirect to
     * @param hash   The AngularJs routing 'page' (e.g. #/myApprovals without the ANCHOR_TOKEN)
     * @return
     */
    public URI getRedirectURL(String scheme, String server, int port, String path, String query, String page, String hash) {

        try {
            newUrl = new StringBuilder();

            // Build up the redirect URL using request info
            newUrl.append(scheme).append("://").
                    append(server).append(":").
                    append(port).
                    append(path).
                    append(page);

            qStr = cleanQueryString(query);
            if (Util.isNotNullOrEmpty(qStr)) {
                newUrl.append("?").append(qStr);
            }
            newUrl.append(ANCHOR_TOKEN).append(hash);

            url = new URI(newUrl.toString());
        }
        catch (URISyntaxException e) {
            // If we get here, just try and jump out to the root
            url = URI.create("../../");
            log.error("Error creating redirect URL.", e);
        }

        return url;
    }

    /**
     * Helper function to remove page and hash from query string.
     *
     * @param q String of query parameters
     * @return Query parameter string cleaned of page and hash
     */
    private String cleanQueryString(String q) {
        StringBuilder newQuery = new StringBuilder();
        if (Util.isNotNullOrEmpty(q)) {
            String[] nq = q.split(QUERY_SEP);
            for (int i = 0; i < nq.length; i++) {
                if (!nq[i].contains(PAGE_KEY) && !nq[i].contains(HASH_KEY)) {
                    newQuery.append(QUERY_SEP).append(nq[i]);
                }
            }
        }

        return newQuery.length() > 0 ? newQuery.substring(1) : "";
    }

    /**
     * The hash passed into getRedirectURL can contain troublesome characters, so we need to encode them appropriately.
     * (e.g.
     * entitlementValue1=CN=2ndRoundAllSame,OU=HierarchicalGroups&filterKeyword="Quote" group
     * becomes
     * entitlementValue1=CN%3D2ndRoundAllSame%2COU%3DHierarchicalGroups&filterKeyword=%22Quote%22%20group)
     *
     * @param fragment
     * @return Properly URI encoded hash
     */
    public String encodeURIFragmentValues(String fragment) {
        StringBuilder encodedFragment = new StringBuilder();
        if (Util.isNotNullOrEmpty(fragment)) {
            // First replace spaces with %20
            String[] kv = fragment.replaceAll(" ", "%20").split(QUERY_SEP);
            for (int i = 0; i < kv.length; i++) {
                // key plus equals sign
                String key = kv[i].substring(0, kv[i].indexOf(EQUALS_SEP) + 1);
                // value to encode
                String val = kv[i].substring(kv[i].indexOf(EQUALS_SEP) + 1);

                encodedFragment.append(key).append(URLUtil.encodeUTF8(val));

                if (i < kv.length - 1) {
                    encodedFragment.append(QUERY_SEP);
                }
            }
        }
        // encodeUTF8() will convert the % in %20 into %25, so we need to reset that back to %20.
        return encodedFragment.toString().replaceAll("%2520", "%20");
    }
}
