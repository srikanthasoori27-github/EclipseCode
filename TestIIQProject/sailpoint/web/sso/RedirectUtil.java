/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import sailpoint.tools.Util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class RedirectUtil {

    public static final String DEFAULT_SSO_LANDING = "/home.jsf";
    public static final String MOBILE_DEFAULT_SSO_LANDING = "/ui/index.jsf";

    public static final String DESKTOP_LOGIN_URL = "/login.jsf";
    public static final String MOBILE_LOGIN_URL = "/ui/login.jsf";

    public static final String ANCHOR_TOKEN = "#";

    /**
     * Calculates a redirect URL.  This is used to determine where to send the user
     * after SSO and/or MFA authentication.
     * @param input sso authentication input values.
     * @return the redirect url.
     */
    protected static String calculateRedirectUrl(SSOAuthenticator.SSOAuthenticationInput input) {
        StringBuilder relayStateBuilder = new StringBuilder();
        StringBuffer requestURL = input.getRequest().getRequestURL();

        if(requestURL.indexOf(input.getLoginUrl() != null ? input.getLoginUrl() : DESKTOP_LOGIN_URL) > 0) {
            if(input.getRequest().getRequestURI().indexOf(input.getMobileLoginUrl() != null ? input.getMobileLoginUrl() : MOBILE_LOGIN_URL) > 0) {
                relayStateBuilder.append(input.getRequest().getContextPath());
                relayStateBuilder.append(input.getDefaultMobileLandingUrl() != null ? input.getDefaultMobileLandingUrl() : MOBILE_DEFAULT_SSO_LANDING);
            } else {
                relayStateBuilder.append(input.getRequest().getContextPath());
                relayStateBuilder.append(input.getDefaultLandingUrl() != null ? input.getDefaultLandingUrl() : DEFAULT_SSO_LANDING);
            }
        } else {
            relayStateBuilder.append(requestURL.toString());
            if(Util.isNotNullOrEmpty(input.getRequest().getQueryString())) {
                relayStateBuilder.append("?");
                relayStateBuilder.append(input.getRequest().getQueryString());
            }
        }

        return relayStateBuilder.toString();
    }

    /**
     * Calculates the querystring for a url from a parameter map.  For example if the map contains:
     *   "foo" : "red"
     *   "bar" : "black"
     * The query string "?foo=red&bar=black" will be returned.
     * @param paramMap the parameter map to convert to a query string.
     * @return the query string formed from the parameter map.
     * @throws UnsupportedEncodingException
     */
    public static String calculateParamString(Map<String, String[]> paramMap) throws UnsupportedEncodingException {
        StringBuilder params = new StringBuilder();

        if (!Util.isEmpty(paramMap)) {
            String sep = "?";
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                String paramName = entry.getKey();
                String[] vals = entry.getValue();
                if ((null != vals) && (vals.length > 0)) {
                    for (String val : vals) {
                        // Bug 20747 - Encode parameter values to UTF-8.
                        // Necessary for redirect URL, else UTF characters gets truncated
                        // when jsf parses the parameter map.
                        String encodedVal = URLEncoder.encode(val, "UTF-8");
                        params.append(sep).append(paramName).append("=").append(encodedVal);
                        sep = "&";
                    }
                }
            }
        }

        return params.toString();
    }

    /**
     * Formats a url from a base url, a parameter map and a hash (ui hash anchor) component.  The url will
     * contain the base url followed by any query parameters from the parameter map, followed by a hash sign ('#')
     * and the hash anchor component.  For example, if called with the following:
     *   http://localhost/iiq
     *   "foo" : "red", "bar" : "black"
     *   hash/component
     * This method would return:
     *   http://localhost/iiq?foo=red&bar=black#hash/component
     *
     * @param URL the base url
     * @param paramMap map containing query parameters
     * @param hash the hash anchor component of the url (does not contain a hash sign '#' this method adds one)
     * @return the formatted url
     * @throws UnsupportedEncodingException
     */
    public static String formatUrlString(String URL, Map<String, String[]> paramMap, String hash) throws UnsupportedEncodingException {
        StringBuilder urlStringBuilder = new StringBuilder(URL);
        String paramString = calculateParamString(paramMap);
        if(!Util.isNullOrEmpty(paramString)) {
            urlStringBuilder.append(paramString);
        }
        if(!Util.isNullOrEmpty(hash)) {
            urlStringBuilder.append(RedirectUtil.ANCHOR_TOKEN);
            urlStringBuilder.append(hash);
        }
        return urlStringBuilder.toString();
    }
}
