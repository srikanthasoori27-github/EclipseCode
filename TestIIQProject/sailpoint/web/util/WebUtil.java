/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIOutput;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.w3c.tidy.Tidy;

import sailpoint.api.AccountGroupService;
import sailpoint.api.EntitlementDescriber;
import sailpoint.api.Explanator;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleOverlapResult;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.object.Template.Usage;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.rest.ui.Paths;
import sailpoint.service.ConfigService;
import sailpoint.service.RedirectService;
import sailpoint.service.TablePreferencesService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Localizable;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Pair;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseBean;
import sailpoint.web.NavigationHistoryBean;
import sailpoint.web.PageCodeBase;
import sailpoint.web.UserContext;
import sailpoint.web.help.ContextualHelpItem;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.system.ObjectAttributeDTO;


/**
 * Helper methods for the web tier.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WebUtil
{
    private static final Log log = LogFactory.getLog(WebUtil.class);

    private static String CHART_WIDTH_SMALL = "300";
    private static String CHART_WIDTH_MED = "450";
    private static String CHART_WIDTH_LARGE = "550";
    
    private static String CHART_HEIGHT_SMALL = "400";
    private static String CHART_HEIGHT_MED = "350";
    private static String CHART_HEIGHT_LARGE = "400";
    
    private static String CHART_ORIENTATION_VERTICAL = "vertical";
    private static String CHART_ORIENTATION_HORIZONTAL = "horizontal";

    /**
     * Root path for the mobile responsive ui
     */
    private static final String MOBILE_PATH = "/ui/";

    /**
     * Attribute set on the request in case of error to keep track
     * of original request URI 
     */
    private static final String ERROR_REQUEST_URI_ATTRIBUTE = "javax.servlet.error.request_uri";

    protected static final int DEFAULT_LIST_RESULT_SIZE = 25;
    protected static final int DEFAULT_MAX_LIST_RESULT_SIZE = 100;

    /**
     * Possible OWASP sanitizer policies
     */
    public enum SanitizerPolicies {
        FORMATTING(Sanitizers.FORMATTING),
        BLOCKS(Sanitizers.BLOCKS),
        TABLES(Sanitizers.TABLES),
        LINKS(Sanitizers.LINKS),
        IMAGES(Sanitizers.IMAGES),
        STYLES(Sanitizers.STYLES);

        private PolicyFactory sanitizerPolicy;

        private SanitizerPolicies(PolicyFactory santizerPolicy) {
            this.sanitizerPolicy = santizerPolicy;
        }

        public PolicyFactory getSanitizerPolicy() {
            return this.sanitizerPolicy;
        }
    }

    /**
     * Private constructor since all methods are static.
     */
    private WebUtil() {}


    /**
     * Check whether the logged in user has any of the rights in the given
     * comma-separated list of rights.
     * 
     * @param  rights  A comma-separated string with the rights we're checking.
     */
    public static boolean hasRight(FacesContext context, String rights) {

        List<String> allRights = Util.csvToList(rights);
        String[] rightsArray =
            (!allRights.isEmpty()) ? (String[]) allRights.toArray(new String[allRights.size()]) : null;

        ValueBinding vb =
            context.getApplication().createValueBinding("#{base}");
        BaseBean base = (BaseBean) vb.getValue(context);
        assert (null != base) : "Could not retrieve base bean.";
        
        List<Capability> caps = base.getLoggedInUserCapabilities();
        Collection<String> userRights = base.getLoggedInUserRights();

        return Authorizer.hasAccess(caps, userRights, rightsArray);
    }

    /**
     * Clear all pages from the history stack given a FacesContext.
     *
     * @param  context  The FacesContext to use.
     */
    public static void clearNavigation(FacesContext context) {
        ValueBinding vb = context.getApplication().createValueBinding("#{navigationHistory}");
        NavigationHistoryBean nav = (NavigationHistoryBean) vb.getValue(context);
        nav.clearNavigation();
    }

    /**
     * Get the HTTP session timeout (in seconds) given a FacesContext.
     * 
     * @param  context  The FacesContext to use.
     * 
     * @return The HTTP session timeout (in seconds).
     */
    public static int getSessionTimeout(FacesContext context)
    {
        int timeout = 0;

        if (null != context)
        {
            HttpSession session =
                (HttpSession) context.getExternalContext().getSession(true);
            timeout = session.getMaxInactiveInterval();
        }

        return timeout;
    }

    /**
     * Return the value for the requested attribute in the system configuration.
     * If not found, return the defaultVal.
     * 
     * @param  attr        The name of the attribute to retrieve.
     * @param  defaultVal  The value to return if the system configuration does
     *                     not exist or the requested attribute is not present.
     * 
     * @return The value for the requested attribute in the system configuration
     *         or the defaultVal if not found.
     */
    public static Object getSystemConfigurationValue(String attr, Object defaultVal)
        throws GeneralException
    {
        Object value = null;
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        Configuration config = ctx.getConfiguration();
        if (null != config)
            value = config.get(attr);
        return (null != value) ? value : defaultVal;
    }

    /**
     * Return the value for the requested attribute in the system configuration.
     * as a JSON string. If not found, return defaultVal as JSON string.
     *
     * @param  attr        The name of the attribute to retrieve.
     * @param  defaultVal  The value to return if the system configuration does
     *                     not exist or the requested attribute is not present.
     *
     * @return The JSON string representation of the value for the requested attribute in the system configuration
     *         or the defaultVal if not found.
     */
    public static String getSystemConfigurationJSON(String attr, Object defaultVal) throws GeneralException {
        Object configValue = getSystemConfigurationValue(attr, defaultVal);
        return configValue == null ? null : JsonHelper.toJson(configValue);
    }

    /**
     * Return the contextual help item for displaying on the ui.
     *
     * @param key  The key of the contextual help item.
     *
     * @return The column config with the given key as a JSON string.
     */
    public static String getContextualHelpJSON(String key) throws Exception {
        UIConfig uiConfig = UIConfig.getUIConfig();
        Map map = uiConfig.getContextualHelp();
        if (map != null) {
            ContextualHelpItem item = (ContextualHelpItem) map.get(key);
            if (item != null) {
                return JsonUtil.render(ConfigService.convertContextualHelpItem(item));
            }
        }
        return null;
    }

    /**
     * Return the column config with the given key as a JSON string.
     *
     * @param key  The UIConfig key of the column config.
     *
     * @return The column config with the given key as a JSON string.
     */
    public static String getColumnConfigJSON(String key) throws Exception {

        FacesContext fc = FacesContext.getCurrentInstance();
        Locale locale = fc.getViewRoot().getLocale();

        return JsonUtil.render(ConfigService.getColumnConfig(key, locale));
    }

    /**
     * Gets the list of FacesMessage from the session, removes them from the session
     * and returns the messages as a JSON string.
     *
     * @return JSON string containing the list of all the messages, null if there are
     * no messages
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static String getMessagesJSON() throws Exception {

        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null && context.getExternalContext() != null) {
            List<FacesMessage> messages = new ArrayList();
            Map session = context.getExternalContext().getSessionMap();
            if (session != null && session.get(PageCodeBase.SESSION_MESSAGES) != null) {
                // this will get the list of FacesMessages and remove them from the session
                messages = (List<FacesMessage>)session.remove(PageCodeBase.SESSION_MESSAGES);
            }
            if (!Util.isEmpty(messages)) {
                List<Map<String, String>> messagesList = new ArrayList();
                String status = "ERROR";
                for (FacesMessage message : messages) {
                    Map<String, String> messagesMap = new HashMap<String, String>();
                    if (message.getSeverity().equals(FacesMessage.SEVERITY_INFO)) {
                        status = "SUCCESS";
                    }
                    else if (message.getSeverity().equals(FacesMessage.SEVERITY_ERROR)) {
                        status = "ERROR";
                    }
                    else if (message.getSeverity().equals(FacesMessage.SEVERITY_WARN)) {
                        status = "WARN";
                    }
                    messagesMap.put("status", status);
                    messagesMap.put("messageOrKey", message.getSummary());
                    messagesList.add(messagesMap);
                }
                return JsonUtil.render(messagesList);
            }
        }
        return null;
    }
    
    /**
     * Creates a FacesMessage instance with the given detail and summary message.
     * The FacesMessage severity is taken from the type property of
     * the detail or summary parameter, with precedence given to the summary. 
     *
     * @param summary FacesMessage summary
     * @param detail FacesMessage detail
     * @param locale Locale of the user
     * @param timezone TimeZone of the user
     * @return FacesMessage instance width the given summary and detail messages.
     */
    public static FacesMessage getFacesMessage(Message summary, Message detail, Locale locale, TimeZone timezone) {

        if (summary == null && detail == null)
            return null;

        Message.Type type = summary != null ? summary.getType() : detail.getType();
        // null types seem to NPE!?
        if (type == null) type = Message.Type.Info;

        FacesMessage.Severity severity = null;
        switch (type){
            case Error :
                severity=FacesMessage.SEVERITY_ERROR;
                break;
            case Warn :
                severity =FacesMessage.SEVERITY_WARN;
                break;
            default:
                severity =FacesMessage.SEVERITY_INFO;
        }

        String summaryText = summary != null ? summary.getLocalizedMessage(locale, timezone) : null;

        // default detailText to "", otherwise jsf will repeat the summary text in the messages tag.
        String detailText = detail != null ? detail.getLocalizedMessage(locale, timezone) : "";

        return new FacesMessage(severity,summaryText, detailText);
    }

    /**
     * Get the logged in users table column preferences as a JSON string.
     *
     * @param tableId table id to retrieve column preferences for
     * @return JSON string representation of column preferences string array
     * @throws GeneralException
     */
    public static String getTableColumnPreferenceJSON(String tableId) throws Exception {
        FacesContext context = FacesContext.getCurrentInstance();
        BaseBean baseBean = context.getApplication().evaluateExpressionGet(context, "#{base}", BaseBean.class);
        TablePreferencesService tablePreferencesService = new TablePreferencesService(baseBean);
        return JsonUtil.render(tablePreferencesService.getTableColumnPreferences(tableId));
    }

    public static boolean isRecommenderConfigured() throws Exception {
        return RecommenderUtil.isRecommenderConfigured(SailPointFactory.getCurrentContext());
    }

    /**
     * Return whether the given attribute on the specified application is a
     * group attribute according to the application's schema.
     * 
     * @param  appName   The name of the application.
     * @param  attrName     The name of the attribute.
     * 
     * @return True if the given attribute on the specified application is a
     *         group attribute, false otherwise.
     */
    public static boolean isGroupAttribute(String appName, String attrName)
        throws GeneralException {

        AccountGroupService svc =
            new AccountGroupService(SailPointFactory.getCurrentContext());
        return svc.isGroupAttribute(appName, attrName);
    }

    /**
     * Return whether the given attribute on the specified application is a
     * group attribute according to the application's schema, given the scheme name.
     *
     * @param  appName   The name of the application.
     * @param  attrName     The name of the attribute.
     * @param  schemaName The optional schema name, this method will look here if the group is not found in Account
     *                    schema.
     *
     * @return True if the given attribute on the specified application and schema is a
     *         group attribute, false otherwise.
     */
    public static boolean isGroupAttribute(String appName, String attrName, String schemaName)
            throws GeneralException {

        AccountGroupService svc =
                new AccountGroupService(SailPointFactory.getCurrentContext());
        return svc.isGroupAttribute(appName, attrName, schemaName);
    }

    /**
     * Return the displayable name of an attribute's value
     * 
     * @param  appName    The name of the application.
     * @param  attrName   The name of the attribute.
     * @param  attrValue  The value of the attribute.
     * 
     * @return The displayable name of the ManagedAttribute matching the given attributes;
     *         the attribute value otherwise
     */
    public static String getGroupDisplayableName(String appName, String attrName,
            String attrValue)
        throws GeneralException {
        
        // jsl - this wasn't using the cache, looks like it should
        //AccountGroupService svc =
        //new AccountGroupService(SailPointFactory.getCurrentContext());
        //return svc.getGroupDisplayableName(appName, attrName, attrValue);
        //Escape this UI side if needed
        return Explanator.getDisplayValue(appName, attrName, attrValue);
    }

    /**
     * Return the displayable names of an attribute's values
     * 
     * @param  appName    The name of the application.
     * @param  attrName   The name of the attribute.
     * @param  attrValues  The value of the attribute.
     * 
     * @return A list of the displayable names of the ManagedAttribute matching the 
     *         given attributes; the attribute values otherwise
     */
    public static List<String> getGroupDisplayableNames(String appName, String attrName,
            Object attrValues)
        throws GeneralException {

        AccountGroupService svc =
            new AccountGroupService(SailPointFactory.getCurrentContext());
        return svc.getGroupDisplayableNames(appName, attrName, attrValues);
    }
    
    /**
     * Returns a string representing the score's color value based on the provided score
     * Useful for loading images or css classes in the UI
     * 
     * <p>
     * This got updated to return only the color value, minus the pound sign,
     * when we shifted away from divs with a background color and started using
     * graphical risk indicators that couldn't include the pound sign in the
     * image name. 
     * </p>
     * 
     * @param score     The score value from 0 - score max (typically 100)
     */
    public static String getScoreColor(int score)
            throws GeneralException
    {
        return getScoreColor(score, true);
    }

    /**
     * Returns a string representing the score's color value based on the provided score
     * Useful for loading images or css classes in the UI 
     * @param score Score value from 0 - score max (typically 100)
     * @param trimHash True to trim the hash from front of color
     * @return CSS color
     * @throws GeneralException
     */
    public static String getScoreColor(int score, boolean trimHash)
            throws GeneralException {
        String color = null;

        ScoreBandConfig config = WebUtil.getScoreConfigForScore(score);
        if(config!=null) {
            color = config.getColor();
        }
        color = WebUtil.formatColor(color, "#FFFFFF", trimHash);

        return color;
    }

    /**
     * Returns a string representing the score's text color value based on the provided score
     * Useful for loading images or css classes in the UI - Falls back to #000000 when not set.
     * @param score Score value from 0 - score max (typically 100)
     * @param trimHash True to trim the hash from front of color
     * @return CSS color
     * @throws GeneralException
     */
    public static String getScoreTextColor(int score, boolean trimHash)
            throws GeneralException {
        String textColor = null;
        String color = null;
        ScoreBandConfig config = WebUtil.getScoreConfigForScore(score);
        if(config!=null) {
            textColor = config.getTextColor();
            color = config.getColor();
        }

        String fallback = "#FFFFFF";
        /* Need to handle cases where the textColor should fall back to black */
        if(!Util.isNullOrEmpty(color) && ScoreBandConfig.LIGHT_COLORS.contains(color)) {
            fallback = "#000000";
        }

        textColor = WebUtil.formatColor(textColor, fallback, trimHash);

        return textColor;
    }

    /**
     * Returns a ScoreBandConfig object that has the provided score inside of its range
     * @param score The score to search for the config for
     * @return
     * @throws GeneralException
     */
    public static ScoreBandConfig getScoreConfigForScore(int score) throws GeneralException {

        SailPointContext ctx = SailPointFactory.getCurrentContext();
        ScoreConfig scoreConfig = ctx.getObjectByName(ScoreConfig.class, "ScoreConfig");
        List<ScoreBandConfig> configs = scoreConfig.getBands();

        for(ScoreBandConfig config : configs) {
            if(score >= config.getLowerBound() && score <= config.getUpperBound()) {
                return config;
            }
        }
        return null;
    }

    /**
     * Formats a color string to return to the UI.
     * @param color The color we are trying to format
     * @param fallback The color to fallback to incase the provided color is null
     * @param trimHash Whether to remove the "#" sign from the front.
     * @return
     */
    private static String formatColor(String color, String fallback, boolean trimHash) {
        if(Util.isNullOrEmpty(color)) {
            color = fallback;
        }

        if (trimHash && color.startsWith(("#"))) {
            color = color.substring(1);
        }
        return color;
    }
    
    public static String splitCamelCase(String str) {
        return Util.splitCamelCase(str);
    }
    
    public static String substring(String str, int begin, int end) {
        if ((null == str) || (("").equals(str)))
            return "";
        else
            return str.substring(begin, end);
    }
    
    public static String substringToEnd(String str, int begin) {
        if ((null == str) || (("").equals(str)))
            return "";
        else
            return str.substring(begin);
    }
    
    public static String stripLeadingZeroes(String s) {
        return Util.stripLeadingChar(s, '0');
    }

    public static String stripHTML(String src) {
        return stripHTML(src, true);
    }
    
    public static String stripHTML(String src, boolean stripNew) {
        if(src != null) {
            String returnStr = src;
            if(stripNew) {
                returnStr = stripNewlines(src);
            }
            // Why bother sanitizing if we're just going to strip out the HTML anyway?
            // 1. Fix syntax: mangled HTML (such as tags without a closing bracket) will
            //    be made valid, allowing our RegExps to correctly strip them out.
            // 2. Insurance: sanitizing will remove any tags or attributes that could
            //    execute code. If for some reason our RegExps fail to match any tags
            //    embedded in the string, sanitize should at least make them benign.
            returnStr = sanitizeHTML(returnStr);
            returnStr = returnStr.replaceAll("\\<.*?>", "");
            returnStr = returnStr.replaceAll("&nbsp;", " ");
            return StringEscapeUtils.unescapeHtml4(returnStr);
        }
        return src;
    }
    
    public static String stripNewlines(String src) {
        if(src!=null) {
            String returnStr = src.replaceAll("\n", " ");
            returnStr = returnStr.replaceAll("\r", " ");
            return returnStr;
        }
        return src;
    }

    /** 
     * Performs some simple stripping of characters from a string to prune out any
     * dangerous javascript.
     * @param src the String to strip
     * @return the sanitized string
     */
    public static String safeHTML(String src) { 
        return safeHTML(src, false);
    }

    /**
     * Uses the OWASP library to sanitize HTML.  Allows benign HTML for formatting.
     * See https://github.com/owasp/java-html-sanitizer
     *
     * @param untrustedHTML String containing potentially dangerous HTML.
     * @return {String} String sanitized of dangerous HTML.
     */
    public static String sanitizeHTML(String untrustedHTML) {
        String clean = untrustedHTML;
        if(untrustedHTML != null && !"".equals(untrustedHTML)) {
            do {
                untrustedHTML = clean;
                clean = halfSanitizeHTML(clean);
            } while ( !isSafeValue(clean) && !untrustedHTML.equals(clean));
        }
        return (null != clean) ? clean.trim() : clean;
    }

    /**
     * Uses the OWASP library to sanitize HTML.  Allows benign HTML for formatting.
     * See https://github.com/owasp/java-html-sanitizer
     *
     * @param untrustedHTML String containing potentially dangerous HTML.
     * @return {String} String sanitized of dangerous HTML.
     *
     * OWAS has a bug handling nested HTML tags. So we should never call directly
     * this method. The intention of this method is to be called from sanitizeHTML
     * that is fixing this bug with a loop.
     * Eg:
     * &lt;&lt;&lt;a&gt;a&gt;svg onload=alert(1)&gt;
     * <<a>svg onload=alert(1)>
     */
    private static String halfSanitizeHTML(String untrustedHTML) {
        //The OWASP library depends on straight HTML that is not escaped.  We are going
        //to unescape this string to the nth degree, keep track of those degrees, log a warning
        //if something is too escaped, and then restore the escaped-ness as to prevent a future
        //bug.
        String escaped = untrustedHTML;
        String unescaped = StringEscapeUtils.unescapeHtml4(escaped);
        int numEscaped = 0;
        boolean unescapeAfterSanitizing = false;

        while(Util.isNotNullOrEmpty(unescaped) && !unescaped.equals(escaped)) {
            numEscaped++;
            escaped = unescaped;
            unescaped = StringEscapeUtils.unescapeHtml4(unescaped);
        }

        if(Util.isNullOrEmpty(unescaped) || numEscaped == 0) {
            //if escaping wiped the value out make
            //sure to re-unescape after sanitizing.
            unescaped = untrustedHTML;
            unescapeAfterSanitizing = true;
        }

        if(numEscaped > 1) {
            log.warn("HTML escaped more than once " + untrustedHTML);
        }

        String unescapedAndSafe = getConfiguredPolicyFactory().sanitize(unescaped).trim();

        if (unescapeAfterSanitizing) {
            unescapedAndSafe = StringEscapeUtils.unescapeHtml4(unescapedAndSafe);
        }

        //sanitize will escape the string, only go any deeper if the string was originally
        //double-escaped.
        for (int i = 1; i < numEscaped; i++) {
            unescapedAndSafe = StringEscapeUtils.escapeHtml4(unescapedAndSafe);
        }

        return unescapedAndSafe;
    }

    /**
     * Helper method to get the configured OWASP sanitizer policies to use for HTML sanitizing
     */
    private static PolicyFactory getConfiguredPolicyFactory() {
        List<String> configuredPolicies = Configuration.getSystemConfig().getList(Configuration.HTML_SANITIZER_POLICIES);

        PolicyFactory policyFactory = null;
        for (String configuredPolicy: Util.iterate(configuredPolicies)) {
            SanitizerPolicies sanitizerPolicy;
            try {
                sanitizerPolicy = Enum.valueOf(SanitizerPolicies.class, configuredPolicy);
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid sanitizer policy " + configuredPolicy);
                continue;
            }

            if (policyFactory == null) {
                policyFactory = sanitizerPolicy.getSanitizerPolicy();
            } else {
                policyFactory = policyFactory.and(sanitizerPolicy.getSanitizerPolicy());
            }
        }

        if (policyFactory == null) {
            // default to what we traditionally supported if configuration is missing or invalid
            return Sanitizers.FORMATTING.and(Sanitizers.BLOCKS);
        }

        return policyFactory;

    }

    /** 
     * Performs some simple stripping of characters from a string to prune out any
     * dangerous javascript.
     * @param src the string to strip
     * @param bypassTidy true if Tidy should not be used on src, false if tidy should be used
     * @return the sanitized string
     */
    public static String safeHTML(String src, boolean bypassTidy) { 
        String clean = src;
        if(src != null && !"".equals(src)) {
            clean = sanitizeHTML(src);

            if ( !bypassTidy ){
                // Use tidy to clean up all the wonderful html our users can create
                Tidy tidy = new Tidy();
                tidy.setPrintBodyOnly(true);
                tidy.setQuiet(true);
                tidy.setShowWarnings(false);
                tidy.setShowErrors(0);
                tidy.setTrimEmptyElements(true);
                // don't add extra newline characters /r/n
                tidy.setWraplen(Integer.MAX_VALUE);
                tidy.setOutputEncoding("UTF8");
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                tidy.parse(new StringReader(clean), ps);
                try {
                    clean = baos.toString("UTF8");
                } catch(UnsupportedEncodingException use) {
                    log.warn("Exception while cleaning html input: " + use.getMessage());
                }
            }
        }
        return (null != clean) ? clean.trim() : clean;
    }

    /**
     * Clean all tags with the given tagName out of a string. 
     * This removes both single tags and matching start/end tags,
     * both escaped and unescaped.
     * @param src Source string
     * @param tagName Name of tag to remove
     * @return String with tags removed
     */
    private static String cleanTags(String src, String tagName) {

        // bug 19980 To allow description text like <test> we have to first filter the HTML
        // for XSS vulnerabilities when text is entered from the UI. Once we filter out the
        // XSS cases whatever remains can be considered to be valid description text. There
        // is a separate case when displaying text aggregated from a csv file. 
        // In the UI case, HTML formatting tags will be delimited with < and > (ex. <b>) 
        // while other tags will be delimited with &lt; and &gt;. These other tags need to
        // be filtered for known XSS vulnerabilities. Whatever text remains (including tags
        // delimited with &lt; and &gt;) is assumed to be valid description text and can 
        // be passed to Tidy for further filtering.
        // In the case where we've aggregated a description from a csv file, the 
        // descriptions are written to the database without any filtering. A string like 
        // "<img src=asdf onerror=alert(document.cookie)>" may be added to the database
        // as a description and can cause an XSS problem when displayed in the UI. In 
        // these cases, we need to filter out the same XSS vulnerabilities only delimited 
        // with < and >.
        
        String clean = src;
        if (clean != null) {
            // escaped html, first matching tags, then single tags.
            clean = clean.replaceAll("&lt;" + tagName + "(.*?)&gt;(.*?)&lt;/" + tagName + "&gt;", "");
            clean = clean.replaceAll("&lt;" + tagName + "(.*?)&gt;", "");

            // unescaped html, first matching tags, then single tags
            // This block handles the case where text was aggregated from a csv file.
            clean = clean.replaceAll("(?i)(\\<(\\s)*){1,}" + tagName + ".*?>*</" + tagName + ">", "");
            clean = clean.replaceAll("(?i)(\\<(\\s)*){1,}" + tagName + ".*?>", "");
        }
        return clean;
    }

    /**
     * Convert the given value (either a scalar or list value) into a comma
     * separated string.
     */
    public static String commify(Object value) {
    
        StringBuilder builder = new StringBuilder();

        if (null != value) {
            List l = Util.asList(value);
            String sep = "";
            for (Object o : l) {
                builder.append(sep).append(o);
                sep = ", ";
            }
        }

        return builder.toString();
    }
    
    /** 
     * JSF sometimes can't pull a map's value off of the map if you give it 
     * a variable as a key.  This solves that.
     */
    public static Object mapValue(Map<String,Object> map, String key) {
        return map.get(key);
    }
    
    public static Object mapValueByPair(Map<Pair<String, String>,Object> map, String key1, String key2) {
        return map.get(new Pair<String, String>(key1, key2));
    }
    
    /**
     * Convert the given value (either a scalar or list value) into a comma
     * separated string with single quotes surrounding each value.
     */
    public static String singleQuoteCommify(Object value) {
    
        StringBuilder builder = new StringBuilder();

        if (null != value) {
            List l = Util.asList(value);
            String sep = "";
            for (Object o : l) {
                builder.append(sep).append("'").append(o).append("'");
                sep = ", ";
            }
        }

        return builder.toString();
    }
    
    public static String outputJSONFromList(List<?> list) throws GeneralException{
        if (list == null) {
            return JsonHelper.emptyList();
        }
        
        StringWriter stringContainer = new StringWriter();
        JSONWriter writer = new JSONWriter(stringContainer);

        try {
            writer.array();
            for (Object val : list) {
                writer.value(val);
            }
            writer.endArray();
        } catch (JSONException ex) {
            throw new GeneralException(ex);
        }
        
        return stringContainer.toString();
    }
    
    public static String outputJSON(Object value) {
        
        
        if(value==null)
            value = " ";
        
        String valueString = value.toString();
        
        /** Escape periods in the string **/
        valueString = valueString.replaceAll("[\\r\\f\\n]","").trim();

        return JSONObject.quote(valueString);
    }
    
    /**
     * This method is used to filter dangerous code from descriptions.  We want
     * to allow HTML formatting for these fields, but we also need to strip
     * images, links, scripts, etc. from the descriptions.  This needs to be done
     * server-side because the POST from the browser can still be edited in transit.
     * @param json
     * @return
     * @throws GeneralException
     */
    public static String cleanseDescriptionsJSON(String json) throws GeneralException {
        if (log.isDebugEnabled())
            log.debug("cleansing json: " + json);
        if (json == null || "".equals(json)) {
            return null;
        }
        
        try {
            // get the array of descriptions
            JSONArray descriptionEntries = new JSONArray(json);
            for (int i = 0; i < descriptionEntries.length(); i++) {
                JSONObject entry = descriptionEntries.getJSONObject(i);
                
                // get the description from this entry
                if (!entry.isNull("value")) {
                    String description = entry.getString("value");
                    entry.put("value", WebUtil.safeHTML(description));
                }
                // put the cleansed entry back into the array
                descriptionEntries.put(i, entry);
            }
            
            if (log.isDebugEnabled())
                log.debug("json cleansed:  " + descriptionEntries.toString());
            return descriptionEntries.toString();
        } catch (JSONException je) {
            throw new GeneralException("There was a problem parsing the description field.", je);
        }
    }
        
    public static String getIdForName(String clazz, String name) throws GeneralException {
        if(null==name || name.equals("")){
            return "";
        }
        String id = "";
        try {
            // since all of the primary SailPoint objects are in the same package...
            String classname = "sailpoint.object." + clazz;
            Class sailpointClass = Class.forName(classname);
            
            SailPointContext ctx = SailPointFactory.getCurrentContext();

            // jsl - This can be called with the "purview" property
            // of a Template which might be a name, so search for both.
            // Is there a reason this has to be strict?
            //Not sure what this means? This should take a name, return the id -rap
            SailPointObject obj = ctx.getObjectByName(sailpointClass, name);
            if (obj != null)
                id = obj.getId();
        } catch (Exception e) {
            throw new GeneralException(e);
        }
        
        return id;        
    }
    
    /**
     * Find the object with the given id and return its display name.
     * 
     * @param clazz Classname of the object
     * @param id ID of the object
     * @return Display name of the object
     */
    @SuppressWarnings("unchecked")
    public static String getDisplayNameForId(String clazz, String id) throws GeneralException {
        if ((null == id) || (id.equals("")))
            return "";
        
        String displayName = "";
        try {
            // since all of the primary SailPoint objects are in the same package...
            String classname = "sailpoint.object." + clazz;
            Class sailpointClass = Class.forName(classname);
            
            SailPointContext ctx = SailPointFactory.getCurrentContext();

            SailPointObject obj = ctx.getObjectById(sailpointClass, id);
            if (obj != null)
                displayName = findSomeName(obj);
        } catch (Exception e) {
            throw new GeneralException(e);
        }	    	
        
        return displayName;
    }
    

    /**
     * Find the object with the given name and return its display name.
     * 
     * @param clazz Classname of the object
     * @param name ID of the object
     * @return Display name of the object
     */
    @SuppressWarnings("unchecked")
    public static String getDisplayNameForName(String clazz, String name) throws GeneralException {
        if (null == name)
            return "";
        
        String displayName = "";
        try {
            // since all of the primary SailPoint objects are in the same package...
            String classname = "sailpoint.object." + clazz;
            Class sailpointClass = Class.forName(classname);
            
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            SailPointObject obj = ctx.getObjectByName(sailpointClass, name);

            if (obj != null)
                displayName = findSomeName(obj);
        } catch (Exception e) {
            throw new GeneralException(e);
        }	    	
        
        return displayName;
    }   
    
    /**
     * Convert the given object to JSON.
     */
    public static String toJSON(Object o) {
        return JsonHelper.toJson(o);
    }
    
    /**
     * 
     * @param className Simple or fully qualilfied name of the SailPointObject being referenced
     * @param csv List of String references to SailPointObjects (could be name or ID).  Also accepts the contents of an Array.toString()
     * @return JSON containing the items in the list
     * @throws GeneralException
     */
    public static String referencedJSONData(String className, String csv) throws GeneralException {
        String fullClassName;
        List<SailPointObject> referencedObjs;
        if(Util.isNullOrEmpty(className)) {
            referencedObjs = Collections.emptyList();
        } else {
            referencedObjs = new ArrayList<SailPointObject>();

            if (className.startsWith("sailpoint.object.")) {
                fullClassName = className;
            } else {
                fullClassName = "sailpoint.object." + className;
            }
            
            try {
                Class spClass = Class.forName(fullClassName);
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                if (!Util.isNullOrEmpty(csv)) {
                    // Correct for string that were passed up in array format
                    // because our ExtJS multi-suggest insists on it
                    if (csv.startsWith("[")) {
                        csv = csv.substring(1, csv.length()-1);
                    }
                    List<String> list = Util.csvToList(csv);
                    if (!Util.isEmpty(list)) {
                        for (String reference : list) {
                            SailPointObject obj = ctx.getObjectById(spClass, reference);
                            if (obj != null) {
                                referencedObjs.add(obj);
                            }
                        }
                    }                    
                }
            } catch (ClassNotFoundException e) {
                log.error("Attempted to retrive data that references objects of unknown type: " + className + ".  Nothing will be returned.", e);
            }
        }
        
        return basicJSONData(referencedObjs);
    }
    
    /** 
     * Takes a list and builds out JSON containing the id and name 
     * of the items in the list. 
     * 
     * @param list List of objects to convert to JSON
     * @return String JSON containing the items in the list
     * @throws GeneralException 
     */
    public static String basicJSONData(List<?> list) throws GeneralException {
        return basicJSONData(list, null);
    }

    /** 
     * Takes a list and builds out JSON containing the id and name 
     * of the items in the list, along with the custom fields specified
     *  in the map. 
     * 
     * @param list List of objects to convert to JSON
     * @param customFields Map of custom proeprties to add to the object.  
     *        Each entry's key corresponds to the record property name and 
     *        the value corresponds to the persisted object's property name.
     *        Right now only string-based properties are supported.  We may want
     *        to enhance this further to support other types of attributes later
     * @return String JSON containing the items in the list
     * @throws GeneralException 
     */
    public static String basicJSONData(List<?> list, Map<String, String> customFields) throws GeneralException {
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        
        // this will result in an empty JSON object if the incoming list is null
        if (null == list)
            list = new ArrayList<Object>();
        
        try {
            jsonWriter.object();

            // figure out how many items we have
            jsonWriter.key("totalCount");
            jsonWriter.value(list.size());

            // now write out the id and name for each item
            jsonWriter.key("objects");
            jsonWriter.array();

            for (Object obj : list) {
                // sometimes you'll get a list containing a single null element
                if (null == obj)
                    continue;
                
                String id = findValue(obj, "getId");
                
                // look for a displayable name
                String name = findSomeName(obj);
                
                jsonWriter.object();
                
                jsonWriter.key("id");
                jsonWriter.value(id);
                
                jsonWriter.key("displayField");
                jsonWriter.value(name);
                
                Boolean workgroup = findBooleanValue(obj, "isWorkgroup");
                if ( workgroup != null ) {
                    jsonWriter.key("icon");
                    if ( workgroup ) 
                        jsonWriter.value("groupIcon");
                    else
                        jsonWriter.value("userIcon");                                          
                }
                
                if (customFields != null && !customFields.isEmpty()) {
                    Set<String> keys = customFields.keySet();
                    if (keys != null && !keys.isEmpty()) {
                        for (String key : keys) {
                            String property = customFields.get(key);
                            if (property != null) {
                                String getter = "get" + Util.capitalize(property);
                                String value = findValue(obj, getter);
                                jsonWriter.key(key);
                                jsonWriter.value(value);
                                
                                // There is probably a better way to do this but for now
                                // we'll just add an emailclass every time we find an email
                                if ("email".equals(property)) {
                                    jsonWriter.key("emailclass");
                                    if (value == null || value.trim().length() == 0) {
                                        jsonWriter.value("noEmail");
                                    } else {
                                        jsonWriter.value("email");
                                    }
                                }
                            }
                        }
                    }                    
                }
    
                jsonWriter.endObject();
            }

            jsonWriter.endArray();            
            jsonWriter.endObject();
        } catch (JSONException e) {
            throw new GeneralException("Error writing basic JSON data", e);
        }
        
        return jsonString.toString();
    }


    /**
     * Takes a string key and object value and returns a simple JSON string with a single key and value.
     *
     * @param key key
     * @param value value
     * @return String JSON containing the key value pair.
     */
    public static String simpleJSONKeyValue(String key, String value) {
        String jsonString;

        if (Util.isNullOrEmpty(value)) {
            jsonString = "{}";
        }
        else {
            final Writer stringWriter = new StringWriter();
            final JSONWriter jsonWriter = new JSONWriter(stringWriter);

            try {
                jsonWriter.object();
                jsonWriter.key(key);
                jsonWriter.value(value);
                jsonWriter.endObject();
                jsonString = stringWriter.toString();
            }
            catch (JSONException e) {
                log.error(e);
                jsonString = "{}";
            }
        }

        return jsonString;
    }

    /**
     * Calls the given converter to transform the given list of ids back into
     * SailPoint objects. 
     * 
     * I think this is a bit of a hack since I'd rather rely exclusively on
     * converters associated with the form inputs.  However, many of our filters 
     * expect object ids instead of the objects themselves.  That means we're 
     * storing Lists of ids on the beans, which need to be converted back into 
     * objects before the UI can display them.  At least doing it here keeps us 
     * from having to recode a lot of beans to store both the objects and their 
     * ids (or proxy methods that do the conversions).
     * 
     * In the future, it would be nice if the filters took objects instead of 
     * ids, but that will entail reworking a lot of the HQL.
     * <br/> ---- <br/>
     * Just a note here to point out that we deliberately try to avoid fetching some
     * of the heavy-duty SailPointObjects (namely Identity and Application) until we absolutely 
     * have to.  A projection query on identities or apps that only returns the parameters
     * we need is much faster than a call to getObjects() because the latter has to take 
     * the time to construct full blown objects.
     * 
     * In other words, this:<pre>
     *     getContext().search(Identity.class, qo, Arrays.asList(new String [] {"id", "name", "firstname", "lastname", "email"}));
     * </pre>
     * is much faster than this:<pre>
     *     getContext().getObjects(Identity.class, qo);
     * </pre>
     * <br/>----<br/>
     * 
     * The projection query returns a List of arrays containing the parameters we asked for,
     * whereas the other query will return full Identity objects.  Once we have the set of 
     * objects that we're interested in persisting, we do want to convert them to real Objects,
     * but we don't want to go that route in the filtering phase if we can avoid it. This is
     * just something to keep in mind if we ever decide to refactor --Bernie  
     * 
     * @param list List of ids to convert to SailPointObjects
     * @param converterName Unqualified name of the converter to use
     * @return List of SailPointObjects; empty List if given list is null
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public static String basicJSONData(List<String> list, String converterName)
        throws GeneralException {
        final String pkgPrefix = "sailpoint.web.util."; 
        String converterClassname = new String();
        List<Object> objectList = null;
        try {
            // try to load up the given converter
            converterClassname = pkgPrefix + converterName;
            Class converterClass = Class.forName(converterClassname);
            Converter converter = (Converter)converterClass.newInstance();
            // we just need ANY UIComponent for getAsObject() - it won't 
            // actually be used by the converter and the converter throws 
            // an exception if the component is null
            UIComponent component = new UIOutput();
            String listAsStr = Util.join(list, ",");
            objectList = (List<Object>)converter.getAsObject(FacesContext.getCurrentInstance(), 
                component, listAsStr);
        } catch (Exception e) {
            throw new GeneralException(e);
        }	    	
        
        return basicJSONData(objectList);
    }


    
    /**
     * Calls the given converter to transform the given list of names back into
     * SailPoint objects also returns JSONData populated with requested customField
     * For e.g. identities with "name" field 
     * Right now if custom Fields are not set, it assumes custom field - "name" and proceeds
     */
    public static String basicJSONDataCustomFields(List<String> list, Map<String,String>customFields, String converterName) 
    throws GeneralException {
    final String pkgPrefix = "sailpoint.web.util."; 
    String converterClassname = new String();
    List<Object> objectList = null;
    try {
        // try to load up the given converter
        converterClassname = pkgPrefix + converterName;
        Class converterClass = Class.forName(converterClassname);
        Converter converter = (Converter)converterClass.newInstance();
        // we just need ANY UIComponent for getAsObject() - it won't 
        // actually be used by the converter and the converter throws 
        // an exception if the component is null
        UIComponent component = new UIOutput();
        String listAsStr = Util.join(list, ",");
        objectList = (List<Object>)converter.getAsObject(FacesContext.getCurrentInstance(), 
            component, listAsStr);
        if (customFields == null) {
            customFields = new HashMap <String, String>();
            customFields.put("name", "name");
        }
    } catch (Exception e) {
        throw new GeneralException(e);
    }

    
    return basicJSONData(objectList, customFields);
    }
    
    /**
     * Takes the given object and looks for the given getter method on it.
     * If anything goes wrong, assume the getter passed was no good and
     * return null.
     * 
     * @param obj Object whose property we're looking for
     * @param getter Getter method to try in retrieving the property 
     * @return Property of the object if the getter is found; null otherwise
     */
    private static String findValue(Object obj, String getter) {
        try {
            Class clazz = obj.getClass();
            Method method = clazz.getMethod(getter, (Class []) null);
            return (String)method.invoke(obj, (Object [])null);
        } catch (SecurityException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    }
    
    private static Boolean findBooleanValue(Object obj, String getter) {
        try {
            Class clazz = obj.getClass();
            Method method = clazz.getMethod(getter, (Class []) null);
            Boolean value = null;
            if ( method != null )
                value= (Boolean)method.invoke(obj, (Object [])null);
            return value;
        } catch (SecurityException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    }
    
    
    /**
     * Steps through a series of possible name getters, returning the 
     * value of the first one it finds.
     * 
     * @param obj
     * @return
     * @throws GeneralException
     */
    private static String findSomeName(Object obj) throws GeneralException {    
        // look for a displayable name
        String name = findValue(obj, "getDisplayableName");
    
        // no luck? maybe displayName?
        if (null == name)
            name = findValue(obj, "getDisplayName");
    
        // ok, we know we can find this one
        if (null == name)
            name = findValue(obj, "getName");
    
        // and if we're STILL null,...
        if (null == name)
            throw new GeneralException(
                new Message(MessageKeys.ERR_NO_NAME_FOUND,
                    obj.getClass().getName(), ((SailPointObject)obj).getId()));
        
        return name;
    }
    

    public static boolean isOdd(int i) {
        return i % 2 != 0;
    }
    
    public static int index(Object o, Iterable i)
    {
        int index = -1;
        if ( (null != o) && (null != i) ) {
            Iterator it = i.iterator();
            int x = 0;
            if ( it != null ) {
                while ( it.hasNext() ) {
                    if ( o.equals(it.next()) ) {
                        index = x;
                        break;
                    }
                    x++;
                }                
            }
        }
        return index;
    }
    
    /**
     * Escape the given javascript string by replacing single-quotes with
     * escaped single quotes.
     * 
     * @param  str  The javascript string to escape.
     * 
     * @return An escaped version of the given javascript string.
     */
    public static String escapeJavascript(String str) {

        String escaped = null;

        if (null != str) {
            // escape backslashes FIRST so the lines below 
            // don't get double-escaped
            escaped = StringEscapeUtils.escapeEcmaScript(str);
            // This is to be displayed in the web, so HTML escape newlines so they
            // make it though the a4j servlet filter (which strips whitespace).
            escaped = Util.escapeHTMLNewlines(escaped);
        }

        return escaped;
    }

    /**
     * Return a Map representation of an Identity instead of JSON.
     * This just calls buildJSONFromIdentity() under the covers and deserializes it into a Map.
     *
     * @param id The id of the identity to build.
     * @return Map of the data returned from buildJSONFromIdentity()
     * @throws GeneralException
     */
    public static Map<String, Object> buildMapFromIdentity(String id) throws GeneralException {
        String idJSON = WebUtil.buildJSONFromIdentity(id);
        if (!Util.isNullOrEmpty(idJSON)) {
            return JsonHelper.mapFromJson(String.class, Object.class, idJSON);
        }
        return Collections.EMPTY_MAP;
    }
    
    // Copied and modified this code from IdentitiesSuggestBean.jsonForIdentities()
    public static String buildJSONFromIdentity(String id) throws GeneralException {
        if ((null == id) || (id.equals("")))
            return "";
        
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();

            Identity idObj = ctx.getObjectById(Identity.class, id);
            return buildIdentityJSON(idObj);

        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    public static String buildJSONFromIdentityName(String name) throws GeneralException {
        if (Util.isNullOrEmpty(name)) {
            return "";
        }

        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();

            Identity idObj = ctx.getObjectByName(Identity.class, name);
            return buildIdentityJSON(idObj);

        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }


    protected static String buildIdentityJSON(Identity idObj) throws GeneralException {
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {

            if (idObj == null) {
                return "";
            }

            jsonWriter.object();
            jsonWriter.key("id");
            jsonWriter.value(idObj.getId());

            jsonWriter.key("name");
            jsonWriter.value(idObj.getName());

            jsonWriter.key("firstname");
            jsonWriter.value(idObj.getFirstname());

            jsonWriter.key("lastname");
            jsonWriter.value(idObj.getLastname());

            jsonWriter.key("email");
            jsonWriter.value(idObj.getEmail());

            jsonWriter.key(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_EMAIL);
            if (idObj.getEmail() != null) {
                jsonWriter.value(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_EMAIL_VALUE);
            } else {
                jsonWriter.value(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_NO_EMAIL_VALUE);
            }

            jsonWriter.key("displayableName");
            String displayableName = idObj.getDisplayableName();
            if (displayableName == null)
                displayableName = idObj.getId();
            jsonWriter.value(displayableName);

            //
            // A string to indicate which icon should be displayed
            //
            jsonWriter.key(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_ICON);
            if (idObj.getWorkgroups() != null) {
                if (idObj.getWorkgroups().size() > 1) //TODO extjs4: is this the right test to determine if group icon??
                    jsonWriter.value(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_ICON_GROUP_VALUE);
                else
                    jsonWriter.value(sailpoint.web.identity.IdentitiesSuggestBean.IDENTITY_CLASS_ICON_USER_VALUE);
            } else {
                jsonWriter.value("");
            }
            jsonWriter.endObject();
        } catch (Exception e) {
            throw new GeneralException(e);
        }

        return jsonString.toString();
    }



    /**
     * Creates json representation for App. This is used by initial values for App Suggest.
     * @param id The application id
     * @return Json with following format {id: 'the id', name : 'the name', displayName: 'the displayName' }
     * @throws GeneralException
     */
    public static String buildJSONFromApplication(String id) throws GeneralException {
        if (Util.isNullOrEmpty(id)) {
            return "";
        }

        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();

            // jsl - This can be called with the "purview" property
            // of a Template which might be a name, so search for both.
            // Is there a reason this has to be strict?
            //SailPointObject obj = ctx.getObjectById(sailpointClass, id);
            Application application = (Application)ctx.getObjectById(Application.class, id);
            if (application == null){
                return "";
            }

            jsonWriter.object();
            jsonWriter.key("id");
            jsonWriter.value(application.getId());

            jsonWriter.key("name");
            jsonWriter.value(application.getName());

            jsonWriter.key("displayName");
            jsonWriter.value(application.getName());
            jsonWriter.endObject();

        } catch (Exception e) {
            throw new GeneralException(e);
        }

        return jsonString.toString();
    }

    
    /**
     * Escape the given string to conform to the requirements of the HTML spec
     * for element IDs.
     * 
     * @param  id  The ID to escape.
     * 
     * @return The escaped ID.
     */
    public static String escapeHTMLElementId(String id) {

        String escaped = id;

        // Must start with a letter [A-Za-z].
        // Valid chars: [A-Za-z], [0-9], hyphen, underscore, colon, period.
        if (null != id) {
            StringBuilder sb = new StringBuilder();
    
            for (int i=0; i<id.length(); i++) {
                char c = id.charAt(i);

                // Make sure first char is a letter.  If not, prefix it.
                if ((i == 0) && !Character.isLetter(c)) {
                    sb.append("sp");
                }

                // Make sure that the character is valid.  If not, escape it
                // with it's ascii value equivalent.
                if (isValidHTMLElementIdChar(c)) {
                    sb.append(c);
                }
                else {
                    sb.append((int) c);
                }
            }

            escaped = sb.toString();
        }
        
        return escaped;
    }

    public static boolean isValidHTMLElementIdChar(char c) {
        // Can't use isLetter() because this returns true for international
        // characters.  ID letters must be between A and Z.
        // Bug #5707 - the colon breaks JS searching the dom, so treat it 
        //             as an invalid character
        // bug #6630 - period: ooh, ooh, me too!
        return Character.isDigit(c) || isBetweenAandZ(c) ||
               ('-' == c) || ('_' == c);
    }

    private static boolean isBetweenAandZ(char c) {
        int val = Character.getNumericValue(c);
        return ((10 <= val) && (val <= 35));

    }
    
    /**
     * Escape the given HTML, optionally allowing formatting characters (such as
     * newlines entities - &#xA; and &#xD;) to remain unescaped.  This is
     * required for some text output that needs to be escaped to prevent script
     * injection but also needs to have some formatting which would get stripped
     * by the a4j filter (eg - by using {@link #wrapText(String, int)}).
     * 
     * @param  html                      The HTML to escape.
     * @param  allowUnescapedFormatting  True to allow formatting string to
     *                                   remain unescaped.
     * 
     * @return The escaped HTML.
     */
    public static String escapeHTML(String html, boolean allowUnescapedFormatting) {
        
        return Util.escapeHTML(html, allowUnescapedFormatting);
        
    }

    /**
     * Escape the HTML out of the given comment string and add newlines.
     * @param comment Original comment text
     * 
     * @return escaped string
     */
    public static String escapeComment(String comment) {
        if (!Util.isNullOrEmpty(comment)) {
            comment = WebUtil.escapeHTML(Util.escapeHTMLNewlines(comment.trim()), true);
        }
        return comment;
    }
    
    /**
     * Wrap the given text at the given column width.  This also translates
     * newlines into their HTML entities.
     * 
     * @param  text     The text to wrap.
     * @param  columns  The number of columns to wrap at.
     * 
     * @return The wrapped text with HTML newline entities.
     */
    public static String wrapText(String text, int columns) {
    
        StringBuffer wrapped = new StringBuffer();
        
        if (null != text) {
            int currentLineLength = 0;

            // First, split out the lines in the text (ie - the newlines that
            // are already in the text).
            String[] lines = text.split("[\n]|\r\n");

            for (String line : lines) {
                // Then, split the words in the current line.
                String[] words = line.split(" ");
    
                boolean wroteThisLine = false;
    
                for (String word : words) {
    
                    int partLength = word.length() + 1;
    
                    // Wrap if we're past the total number of columns.  Only wrap
                    // if we have written on this line so we don't get into trouble
                    // if there is a word (without spaces) longer than our columns.
                    if ((currentLineLength + partLength > columns) && wroteThisLine) {
                        wrapped.append("\n");
                        currentLineLength = 0;
                        wroteThisLine = false;
                    }
                    else {
                        wroteThisLine = true;
                    }

                    wrapped.append(word).append(" ");
                    currentLineLength += partLength;
                }

                // Add the newline back that we split on.
                wrapped.append("\n");
            }
        }
        
        // This is to be displayed in the web, so HTML escape newlines so they
        // make it though the a4j servlet filter (which strips whitespace).
        return Util.escapeHTMLNewlines(wrapped.toString());
    }

    /**
     * Inserts wordbreak entities so that the browser can
     * break up long contiguous strings such as DNs.
     * @param text Text to insert wordbreaks into.
     * @param escapeHtml true if html entities should be escaped
     * @return
     */
    public static String insertWordBreak(String text, boolean escapeHtml){

        if(text == null || text.trim().length()==0)
            return text;

        String escaped = escapeHtml ? safeHTML(text) : text;

        // &#8203; works for all browsers except IE6 which cant handle it.
        // Insert <wbr> for IE6, class='wordbreak' is hidden in IE6 
        return escaped.replace(",",  ",<span class='wordbreak'>&#8203;</span><span class='wordbreakIE6'><wbr/></span>");
    }

    public static enum SortOrder {
        ascending,
        descending
    };
    
    public static class UnsortableException extends IllegalArgumentException {
        private static final long serialVersionUID = -3508076674501511722L;
        
        public UnsortableException(String message, Throwable cause) {
            super(message, cause);
        }
        
    }
    
    /**
     * Sorts the specified Collection of objects by the property that is specified.
     * The following assumptions are made:  
     * <ul>
     *   <li> the property is accessible via a public getter method
     *   <li> property values cannot be null unless the forceNull parameter is set to true, in which case null will be coerced to a blank instance of the property's class
     *   <li> the property's value is an object that implements Comparable
     * </ul>
     * @param objects Collection of objects that needs to be sorted
     * @param clazz Class of the objects contained in the collection
     * @param sortByPropertyName Name of the property by which we are sorting
     * @param order SortOrder of the returned Collection.  If the order is null
     *              it will default to SortOrder.ascending.
     * @param isCaseInsensitive Set this to true to make String comparisons case-insensitive.
     *                          This has no effect when sorting on non-String properties
     * @param forceNull Set this to true to replace null property values with newly constructed objects for the purposes of comparison
     * @return the sorted List of Objects in the Collection that was passed in
     * @throws UnsortableException
     */
    public static List<? extends Object> sortObjectsByProperty(Collection<? extends Object> objects, 
            Class clazz, String sortByPropertyName, SortOrder order, boolean isCaseInsensitive, boolean forceNull) 
            throws UnsortableException {
        final List<Object> sortedObjects = new ArrayList<Object>();

        try {
            StringBuffer getter = new StringBuffer(sortByPropertyName);
            getter.replace(0, 1, sortByPropertyName.substring(0, 1).toUpperCase());
            getter.insert(0, "get");
            
            Method propertyGetter = clazz.getMethod(getter.toString(), (Class []) null);

            // Map the objects by the specified property
            Map<Comparable, List<Object>> objMap = new HashMap<Comparable, List<Object>>();
            // Maintain a list of propertyValues
            List<Comparable> propertyValues = new ArrayList<Comparable>();
            
            for (Object obj : objects) {
                Comparable propertyVal = (Comparable) propertyGetter.invoke(obj, (Object []) null);
                if (propertyVal == null) {
                    if (forceNull) {
                        try {
                            propertyVal = (Comparable) propertyGetter.getReturnType().newInstance();
                        } catch (InstantiationException e) {
                            throw new IllegalArgumentException("The property sorter was given objects containing null values for the property " + sortByPropertyName + " and no suitable constructor for the class " + clazz.getName() + " could be found.");
                        }
                    } else {
                        throw new IllegalArgumentException("The property sorter was given objects containing null values for the property " + sortByPropertyName);
                    }
                }

                if (isCaseInsensitive && propertyVal instanceof String) {
                    propertyVal = ((String) propertyVal).toUpperCase();
                }
                
                propertyValues.add(propertyVal);
                // Use a Map of Lists in case more than one object shares the property value in question
                List<Object> mappedObjs = objMap.get(propertyVal);
                if (mappedObjs == null) {
                    mappedObjs = new ArrayList<Object>();
                    objMap.put(propertyVal, mappedObjs);
                }
                mappedObjs.add(obj);
            }
            
            // Sort the property values
            SortedSet<Comparable> sortedPropertyValues = new TreeSet<Comparable>(propertyValues);
                        
            // Use the sorted list to sort the values
            for (Comparable propertyVal : sortedPropertyValues) {
                if (order == null || order == SortOrder.ascending) {
                    sortedObjects.addAll(objMap.get(propertyVal));
                } else {
                    sortedObjects.addAll(0, objMap.get(propertyVal));
                }
            }
        } catch (SecurityException e) {
            throw new UnsortableException("The " + sortByPropertyName + " property is not accessible on objects of type " + clazz.getName(), e);
        } catch (NoSuchMethodException e) {
            throw new UnsortableException("The " + sortByPropertyName + " property is not accessible on objects of type " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            throw new UnsortableException("The " + sortByPropertyName + " property is not accessible on objects of type " + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            throw new UnsortableException("The " + sortByPropertyName + " property is not accessible on objects of type " + clazz.getName(), e);
        }

        return sortedObjects;
    }
    
    /**
     * @param attributes attribute being queried
     * @param app Name of the application to which this attribute applies
     * @param category Category of the attribute.  "Account" if the attribute refers to a specific user of the application;
     * "Group" if the attribute refers to a group of users of the application
     * @param entitlementsOnly true if the query should return entitlement attributes; false if the query should return non-entitlement attributes
     * @return The names of the attributes that match the specified criteria
     * @see sailpoint.connector.Connector.TYPE_GROUP
     * @see sailpoint.connector.Connector.TYPE_ACCOUNT
     */
    public static List<String> filterAttributesForAppGroupAndEntitlements(final Attributes<String, Object> attributes, final Application app, final String category, final boolean entitlementsOnly) {
        final List<String> attributeNames = new ArrayList<String>();
        
        Schema schema = app.getSchema(category);
        
        // Return an empty result if we can't find a schema or 
        // if there are no attributes passed in 
        if ((schema != null) && (attributes != null)) {
            for (String attributeName : attributes.keySet()) {
                AttributeDefinition def = schema.getAttributeDefinition(attributeName);
                if (def != null && entitlementsOnly == def.isEntitlement()) {
                    attributeNames.add(attributeName);
                }
            }
        }
        
        return attributeNames;
    }
    
    @SuppressWarnings("unchecked")
    public static Attributes<String, Object> getNonEntitlementAttributesForLink(LinkSnapshot link) {
        Attributes<String, Object> filteredAttributes = new Attributes<String, Object>();

        if (null != link) {
            try {
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                Application app = ctx.getObjectByName(Application.class, link.getApplication());
    
                if (null != app) {
                    Schema accountSchema = app.getSchema(Application.SCHEMA_ACCOUNT);
                    Attributes<String, Object> linkAttributes = link.getAttributes();
                    Set<String> attributeNames = linkAttributes.keySet();
                    for (String attributeName : attributeNames) {
                        AttributeDefinition def = accountSchema.getAttributeDefinition(attributeName);
                        if (def == null || !def.isEntitlement()) {
                            filteredAttributes.put(attributeName, linkAttributes.get(attributeName));
                        }
                    }
                }
            } catch (GeneralException e) {
                log.error("The attributes for the " + link.getApplication() + " application are not available right now.", e);
            }
        }
        
        return filteredAttributes;
    }

    /**
     * Return a key that we can embed in an HTML element's ID that will uniquely
     * identify the given entitlement value.
     */
    public static String getEntitlementKey(String app, String instance,
                                           String nativeId, String target,
                                           Object value) {
        if ((null != instance) && (0 == instance.length())) {
            instance = null;
        }
        
        return escapeHTMLElementId(app + "-" + instance + "-" + nativeId + "-" +
                                   target + "-" + value);
    }
    
    /**
     * Returns a non-null List of entitlement names from the provided
     * EntitlementSnapshot
     */
    public static List<String> getEntitlementSnapshotDisplayableName(String attrKey,
            EntitlementSnapshot entitlement) {
        List<String> names = new ArrayList<String>();
        Object val = entitlement.getAttributes().get(attrKey);
        if (val != null) {
            if (val instanceof Collection) {
                for (Object element : (Collection)val) {
                    names.add(String.valueOf(element));
                }
            } else {
                names.add(String.valueOf(val));
            }
        }
        return names;
    }
    
    /**
     * Return a key that we can embed in an HTML element's ID or classes that
     * will uniquely identify an application with the given properties.
     */
    public static String getApplicationKey(String app, String instance, String nativeId) {
        return WebUtil.escapeHTMLElementId(app + "-" + instance + "-" + nativeId);
    }
    
    public static String replace(String sourceString, 
                                 String target, 
                                 String replacement) {
        String str = sourceString;
        if ( sourceString != null ) {
            str = sourceString.replace(target, replacement);
        }
        return str;
    }


    /**
     * Method to take a String that will be used as a component id and 
     * munge it so its a valid id.
     * Rules that are applied are as follows. If an infraction
     * is found for 2, 3, or 4 then the character is replaced with an 
     * underscore. If 1 is encountered an exception is thrown.
     *
     * 1. Must not be a zero-length String.
     * 2. First character must be a letter or an underscore ('_').
     * 3. Subsequent characters must be a letter, a digit, an underscore ('_'), or a dash ('-').
     * 4. All letter characters must be in the Basic Latin UnicodeBlock -- This is due to an issue
     *    with a4j's implementation of JTidy.  It doesn't seem to allow foreign characters in 
     *    the component ID. 
     */
    public static String buildValidComponentId(String idValue) {
        return buildValidComponentId(idValue, false);
    }
    
    /**
     * Method to take a String that will be used as a component id and 
     * munge it so its a valid id.
     * Rules that are applied are as follows. If an infraction
     * is found for 2, 3, or 4 then the character is replaced with an 
     * underscore. If 1 is encountered an exception is thrown.
     *
     * 1. Must not be a zero-length String.
     * 2. First character must be a letter or an underscore ('_').
     * 3. Subsequent characters must be a letter, a digit, an underscore ('_'), or a dash ('-').
     * 4. All letter characters must be in the Basic Latin UnicodeBlock -- This is due to an issue
     *    with a4j's implementation of JTidy.  It doesn't seem to allow foreign characters in 
     *    the component ID. 
     * @param idValue ID that is being transformed
     * @param ignoreNulls true if we want to let null and/or empty ids slide; false otherwise.
     *        This parameter was added as a workaround to some lifecycle issues that cause blank
     *        ids to temporarily appear in the "Apply Request Values" JSF phase
     */
    public static String buildValidComponentId(String idValue, boolean ignoreNulls) {
        // 1. Must not be a zero-length String.
        if( idValue == null || idValue.length()==0 ){
            if (ignoreNulls)
                return idValue;
            else
                throw new IllegalArgumentException("sp:buildValidComponentId :: Component identifier must not be a zero-length String");
        }

        StringBuffer id = new StringBuffer(idValue.length());
        char[] chars = idValue.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char tmpChar = chars[i];
            
            //2. First character must be a letter or an underscore ('_').
            if ( i == 0 ){
                if (( !Character.isLetter(tmpChar))  && ( tmpChar != '_' )){
                    tmpChar = '_';
                }
            } else {
                //3. Subsequent characters must be a letter, a digit, 
                // an underscore ('_'), or a dash ('-').
                if( !Character.isDigit(tmpChar) && 
                    !Character.isLetter(tmpChar) && 
                    tmpChar !='-' && tmpChar !='_'){

                    tmpChar = '_';
                }
            }
            
            // 4. All characters must be in the Basic Latin UnicodeBlock
            if (Character.UnicodeBlock.of(tmpChar) != Character.UnicodeBlock.BASIC_LATIN) {
                tmpChar = '_';
            }
            
            id.append(tmpChar);
        }

        return id.toString();
    }

    /**
     * Converts the severity level of the message into one of our styles.
     * Not sure if this is the best way to do this but the styles
     * seem fixed and it's a lot more convenient than inline el expressions with
     * three trinary terms. - jsl
     *
     * This was added to render a generic message list containing any
     * severity level in the task results.
     */
    public static String getMessageStyle(Message msg) {

        String style = null;
        if (msg != null) {
            Message.Type type = msg.getType();
            if (type == Message.Type.Error) 
                style = "formError";
            else if (type == Message.Type.Warn)
                style = "formWarn";
            else
                style = "formInfo";
        }
        return style;
    }
    
    public static String localizeAttribute(SailPointObject object, String attribute) {
        String value = "";
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            FacesContext fc = FacesContext.getCurrentInstance();
            
            Localizer localizer = new Localizer(ctx, object.getId());
            Locale locale = ((fc != null) ? fc.getViewRoot().getLocale() : localizer.getDefaultLocale());
            LocalizedAttribute localizedAttribute = localizer.findAttribute(attribute, locale.toString(), true);
            
            if(localizedAttribute!=null) {
                return sanitizeHTML(localizedAttribute.getValue());
            }
            
            value = sanitizeHTML((String)PropertyUtils.getProperty(object, attribute));
        } catch (Exception e) {
            log.warn("Unable to get localized attribute for object: " + object + ", attribute: " + attribute);
        }
        
        if(value==null) {
            value = "";
        }
        
        return value;
    }

    /**
     * Converts a localized message to localized plain text.
     * @param msg Localizable object
     * @return localized plain text
     */
    public static String localizeMessage(Localizable msg){
        if (msg == null)
            return null;

        // todo on second thought it might not be good to reference the facescontext from a static method...
        // getting the session directly since getting the sessionmap may be causing problems on weblogic.
        HttpSession session = null;
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null)
            session = (HttpSession)fc.getExternalContext().getSession(false);

        TimeZone tz = TimeZone.getDefault();
        if (session != null && session.getAttribute(PageCodeBase.SESSION_TIMEZONE) != null)
                tz = (TimeZone)session.getAttribute(PageCodeBase.SESSION_TIMEZONE);

        Locale locale = ((fc != null) ? fc.getViewRoot().getLocale() : Locale.getDefault());

        return msg.getLocalizedMessage(locale, tz);
    }

    /**
     * Converts a key to appropriate localized message.
     *
     * @param msg Message key
     * @return Message text
     */

    public static String localizeMessage(String msg){
        return localizeMessage(msg, (Object[]) null);
    }
    
    /**
     * Converts a key to appropriate localized message.
     *
     * @param msg Message key
     * @return Message text
     */

    public static String localizeMessage(String msg, Object... args){

        if (msg != null && msg.toLowerCase().equals("true"))
            msg = MessageKeys.TXT_TRUE;
        else if (msg != null && msg.toLowerCase().equals("false"))
            msg = MessageKeys.TXT_FALSE;

        return localizeMessage(new Message(msg, args));
    }

    public static Date calculatePhaseEndDate(Certification cert, String phase) {
        return cert.calculatePhaseEndDate(Enum.valueOf(Certification.Phase.class, phase));
    }

    /**
     * Calculates the description to display depending on the type  
     * of the given cert item.
     * 
     * TODO: We've got several versions of this calculation running around IIQ.
     *       They should probably get consolidated at some point.
     * 
     * @param item CertificationItem whose description is needed
     * @throws GeneralException 
     */
    public static String caculateCertItemDesc(CertificationItem item) throws GeneralException {             
        String desc = null;
        switch (item.getType()) 
            {
            case Bundle:
                desc = item.getBundle(SailPointFactory.getCurrentContext()).getName();
                break;
            case AccountGroupMembership: 
            case Exception:
            case Account:
            case DataOwner:
                EntitlementSnapshot snap = item.getExceptionEntitlements();
                if (snap != null){
                    Message msg = EntitlementDescriber.summarize(snap);
                    desc = (msg != null) ? 
                        localizeMessage(msg) : null;
                }
        
                break;
            case PolicyViolation:
                desc = item.getPolicyViolation().getConstraintName();
                break;
            }
        
        return desc;
        }
    
    public static Date getCurrentDateIfValueNull(Date value) {
        Date val = new Date();
        if ( value instanceof Date )
            val = (Date)value; 
        return val;
    }

    public static String getCurrentDateIfValueNull(String value) {
        if ( Util.getString(value) == null ) 
            return new Date().toString();
        else
            return value;
    }

    /**
     * djs: Added this so I could concat two strings for use 
     * as a key for a Map object within a jsp page.
     * Could not find a way to concat within the []'s. 
     * 
     * i.e.
     * #{taskDefinition.customArgMap[sp:concat("operator.",attr.name)]}
     */
    public static String concat(String s1, String s2 ) {
        return s1 + s2;
    }

    /**
     * Turn each value into a List ( even if there is only one value) 
     * and go through and localize any Date object found in the 
     * list.
     */
    public static List formatLinkValues(String attrName, Object value, 
                                        Locale locale, TimeZone tz) {
        List list = Util.asList(value);
        List neuList = list;
        if ( ( list != null ) && ( list.size() > 0 ) ) {
            neuList = new ArrayList();
            for ( Object obj : list ) {
                if ( obj instanceof Date ) {
                    LocalizedDate lDate = new LocalizedDate((Date)obj,DateFormat.FULL, DateFormat.LONG);
                    obj = lDate.getLocalizedMessage(locale, tz);
                }
                neuList.add(obj);
            }
        }
        return neuList;
    }

    public static String objectListToIdString(List<SailPointObject> objects) {
        if(objects==null || objects.isEmpty()) {
            return "";
        } else {
            List<String> ids = new ArrayList<String>();
            for(SailPointObject obj : objects) {
                ids.add(obj.getId());
            }
            return ids.toString();
        }
    }
    
    public static <T extends SailPointObject> List<String> objectListToIds(List<T> objects) {
    List<String> ret = new ArrayList<String>();
    
    if (objects != null) {
        for (SailPointObject obj : objects) {
        ret.add(obj.getId());
        }
    }
    
    return ret;
    }

    /**
     * Get a comma-separated list of displayable names for the given objects.
     */
    public static String objectListToNameString(List<? extends SailPointObject> objects)
        throws GeneralException {
        
        StringBuilder sb = new StringBuilder();

        if (null != objects) {
            String sep = "";
            for(SailPointObject obj : objects) {
                sb.append(sep).append(findSomeName(obj));
                sep = ", ";
            }
        }

        return sb.toString();
    }
    
    public static <T extends SailPointObject> List<T> nameListToObjectList(SailPointContext context, Class<T> clz, List<String> names) 
        throws GeneralException{
        
        List<T> objs = new ArrayList<T>();
        if (names == null) {
            return objs;
        }
        
        for (String name : names) {
            T obj = context.getObjectByName(clz, name);
            objs.add(obj);
        }
        
        
        return objs;
    }
    
    
    public static <T extends SailPointObject> List<String> objectListToNameList(List<T> objects) {
        
        List<String> names = new ArrayList<String>();
        if(objects==null) {
            return names;
        } 

        for (SailPointObject obj : objects) {
            names.add(obj.getName());
        }
        
        return names;
    }

    /**
     * Given an arbitrary value coerce it to a List<SelectItem>.
     * Used when we're trying to feed stupid JSF components
     * like h:selectManyMenu a value list that comes from something
     * that doesn't like to traffic in SelectItems, notably
     * selection lists calculated in workflows and put into 
     * a work item.  
     */
    public static List<SelectItem> getSelectItems(Object value, boolean includeEmpty) {

        // jsf pukes in a horrible way if it gets a null list
        List<SelectItem> items = new ArrayList<SelectItem>();
        if (value instanceof List) {
            for (Object o : (List)value) {
                if (o instanceof SelectItem) {
                    // not expecting these since they won't serialize
                    // into a work item, but hey, pass them through
                    items.add((SelectItem)o);
                } else if(o instanceof Enum) {
                    Enum e = (Enum)o;
                    String label = e.name();
                    // MessageKeyHolder is what localizable enums should extend
                    if (MessageKeyHolder.class.isAssignableFrom(o.getClass())) {
                        String messageKey = ((MessageKeyHolder)e).getMessageKey();
                        if (messageKey != null) {
                            label = localizeMessage(messageKey);
                        }
                    }
                    items.add(new SelectItem(e.name(),label));
                }
                else if (o != null) {
                    // TODO: might be nice to allow nested lists
                    // with tuples of label/value ?
                    String s = o.toString();
                    items.add(new SelectItem(s, s));
                }
            }
        }
        else if (value != null) {
            // TODO: allow a Map with label/value pairs
            String s = value.toString();
            items.add(new SelectItem(s, s));
        }

        if(includeEmpty) {
            items.add(0, new SelectItem("",""));  
        }
        return items;
    }
    
    /**
     * Return a List of SelectItems for rules of the given type.
     */
    public static List<SelectItem> getRulesByType(SailPointContext context,
                                                  Rule.Type type,
                                                  boolean addSelectPrompt)
        throws GeneralException {
        
        return getRulesByType(context, type, addSelectPrompt, false, null);
    }

    /**
     * Return a List of SelectItems for rules of the given type.
     */
    public static List<SelectItem> getRulesByType(SailPointContext context,
                                                  Rule.Type type,
                                                  boolean addSelectPrompt,
                                                  boolean idIsValue)
        throws GeneralException {
        
        return getRulesByType(context, type, addSelectPrompt, idIsValue, null);
    }

    /**
     * Return a List of SelectItems for rules of the given type.
     * 
     * @param  context          The context to use.
     * @param  type             The type of rules to return.
     * @param  addSelectPrompt  Whether to add a "Select Rule" item to the list.
     * @param  idIsValue        Whether the ID should be the value of each item. 
     *                          If false, then name is used.
     * @param  selectedRule     The name of the currently selected rule.  If the
     *                          selected rule is outside the scope of the search, 
     *                          it will be added to the list.
     */
    public static List<SelectItem> getRulesByType(SailPointContext context,
                                                  Rule.Type type,
                                                  boolean addSelectPrompt,
                                                  boolean idIsValue,
                                                  String selectedRule)
        throws GeneralException {

        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        if (addSelectPrompt)
            selectItems.add(new SelectItem("", localizeMessage(MessageKeys.SELECT_RULE)));

        QueryOptions ops = new QueryOptions();
        ops.addOrdering("name", true);

        // a null type will return all rules
        if (type != null)
            ops.add(Filter.eq("type", type));
        
        List<String> props = new ArrayList<String>();
        props.add("id");
        props.add("name");
        
        boolean containsSelected = false;
        Iterator<Object[]> result = context.search(Rule.class, ops, props);
        while (result.hasNext()) {
            Object[] row = result.next();
            String id = (String)row[0];
            String name = (String)row[1];
            String val = (idIsValue) ? id : name;
            selectItems.add(new SelectItem(val, name));
            
            if ((selectedRule != null) && (selectedRule.equals(name)))
                containsSelected = true;
        }
        
        if (selectedRule != null) {
            if (idIsValue) {
                throw new GeneralException("Invalid usage - must use the rule name " +
                    "as the select item value to insert an out-of-scope rule.");
            }
            
            if (!containsSelected) 
                selectItems.add(new SelectItem(selectedRule, selectedRule));
        }
        
        return selectItems;
    }

    
    public static String getJSONString(JSONObject object, String key) throws org.json.JSONException{
        String value = null;        
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getString(key);
        } catch(JSONException jse) { }
        return value;
    }
    
    public static JSONArray getJSONArray(JSONObject object, String key) throws org.json.JSONException{
        JSONArray value = null;        
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getJSONArray(key);
        } catch(JSONException jse) { }
        return value;
    }
    
    public static boolean getJSONBoolean(JSONObject object, String key) {
        boolean value = false;        
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getBoolean(key);
        } catch(JSONException jse) { }
        return value;

    }

    public static int getJSONInt(JSONObject object, String key) {
        int value = 0;        
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getInt(key);
        } catch(JSONException jse) { }
        return value;

    }

    public static Date getJSONDate(JSONObject object, String key) {
        Date value = null;        
        try {
            if(object.has(key) && !object.isNull(key))
                value = new Date(object.getLong(key));
        } catch(JSONException jse) { }
        return value;

    }

    /**
     * Return a Attributes map of Items for the given JSONObject.
     *
     * @param JSONObject object
     * @return sailpoint.object.Attributes map
     */
    public static Attributes<String,Object> getJSONMap(JSONObject headObject,
                                                       String headKey) {
        Attributes<String,Object> map = null;
        try {
            if (headObject.has(headKey) && !headObject.isNull(headKey)) {
                JSONObject object = (JSONObject)headObject.get(headKey);
                map = new Attributes<String,Object>();

                @SuppressWarnings("unchecked")
                Iterator<String> keysItr = object.keys();
                while (keysItr.hasNext()) {
                    String key = keysItr.next();
                    Object value = object.get(key);

                    map.put(key, value);
                }
            }
        } catch (JSONException jse) { }

        return map;
    }

    /**
     * Method returns JSONObject for given key
     * @param JSONObject - parent json object
     * @param key - key to retrieve json object
     */
    public static JSONObject getJSONObject(JSONObject object, String key) {
        JSONObject value = null;
        try {
            if(object.has(key) && !object.isNull(key)) {
                value = object.getJSONObject(key);
            }
        } catch(JSONException jse) { }

        return value;
    }

    /**
     * For the given list, pulls a sublist with the given pagesize and
     * page number.
     *
     * @param list
     * @param page
     * @param pageSize
     * @return
     */
    public static List getPage(List list, int page, int pageSize){

        if (list == null || list.isEmpty())
            return list;

        int start =(page - 1) * pageSize;
        int end = start + pageSize;

        if (start < 0 || start > list.size())
            start = 0;
        if (end > list.size())
            end = list.size();

        return list.subList(start, end);
    }

    /**
     * Returns the size of a list.
     * @param list
     * @return
     */
    public static int getSize(List list){
        return list != null ? list.size() : 0;
    }

    /**
     * Returns true if the given item is a collection.
     * @param obj
     * @return
     */
    public static Boolean isCollection(Object obj){
        return obj != null && Collection.class.isAssignableFrom(obj.getClass());
    }

    /**
     * Detect if the given input from a parameter could contain a Cross Site
     * Scripting (XSS) attack, and if so throw a RuntimeException.
     * 
     * Note that JSF usually takes care of escaping values except when they are
     * rendered within script or style tags.  This should be used to validate
     * any input parameters that will be spit out into a script or style block
     * (including within a JSON string).
     * 
     * @param  input  The parameter to check.
     */
    public static void detectXSS(String input) throws RuntimeException {
        if (null != input) {
            for (int i=0; i<input.length(); i++) {
                char c = input.charAt(i);
                if (('\'' == c) || ('"' == c) || ('<' == c) || ('>' == c) || ('%' == c)) {
                    throw new RuntimeException("XSS attack detected.");
                }
            }
        }
    }
    
    /**
     * Detect if the given input from a parameter could contain a Cross Site
     * Scripting (XSS) attack, and if so throw a RuntimeException.
     * 
     * This should be used to validate form field input.
     * 
     * @param  input  The parameter to check.
     */
    public static void detectFormXSS(String input) throws RuntimeException {
        if (null != input) {
            for (int i=0; i<input.length(); i++) {
                char c = input.charAt(i);
                if (('<' == c) || ('>' == c)) {
                    throw new RuntimeException("XSS attack detected in form.");
                }
            }
        }
    }
    
    /**
     * Determines if the currently logged in user is authorized to view the work item with
     * with the specified id.
     *
     * @param userContext Object that implements sailpoint.web.UserContext.  
     *                    Any class extending sailpoint.web.BaseBean or 
     *                    sailpoint.rest.BaseResource meets this criteria
     * @param workItemId The work item id.
     * @return True if authorized to view, false otherwise.
     * @throws GeneralException
     */
    public static boolean isUserAuthorizedForWorkItem(UserContext userContext, String workItemId) throws GeneralException {
        SailPointContext spContext = SailPointFactory.getCurrentContext();
        if(workItemId != null) {
            WorkItem workItem = spContext.getObjectById(WorkItem.class, workItemId);
            if (null == workItem) {
                return false;
            }

            return AuthorizationUtility.isAuthorized(userContext, new WorkItemAuthorizer(workItem));
        }
        return false;
    }

    /**
     * Utility to find workflows of a particular type.
     */
    public static List<SelectItem> getWorkflows(SailPointContext spc, String type, 
                                                boolean includeNoType)
        throws GeneralException {
        
        List<SelectItem> workflows = new ArrayList<SelectItem>();
        workflows.add(new SelectItem("", localizeMessage(MessageKeys.SELECT_WORKFLOW)));

        // do two queries, the first to get the ones with the exact type
        // and another for untyped ones
        int typed = getWorkflows(spc, type, workflows);

        // and the untyped ones
        if (includeNoType) {
            int untyped = getWorkflows(spc, null, workflows);

            // add a divider if we had both
            if (typed > 0 && untyped > 0) 
                workflows.add(typed + 1, new SelectItem("", "-----------------------------------------"));
        }

        return workflows;
    }
    
    public static List<SelectItem> getWorkflows(SailPointContext spc, String type) 
        throws GeneralException {
        return getWorkflows(spc, type, true);
    }

    private static int getWorkflows(SailPointContext spc, String type, List<SelectItem> items) 
        throws GeneralException {

        int count = 0;

        List<String> props = new ArrayList();
        props.add("name");

        QueryOptions ops = new QueryOptions();
        ops.setOrderBy("name");

        if (type != null)
            ops.add(Filter.eq("type", type));
        else
            ops.add(Filter.isnull("type"));
        
        // bug #17119 comment #7 do not show templates in the LCM configuration screen
        ops.add(Filter.eq("template", false));
        
        Iterator<Object[]> result = spc.search(Workflow.class, ops, props);
        if (result != null) {
            while (result.hasNext()) {
                Object[] row = result.next();
                items.add(new SelectItem((String)(row[0])));
                count++;
            }
        }
        return count;
    }


    // Check if the element is the last extended attribute in the list
    public static boolean isLastExtendedAttribute(Object o, Iterable i)
    {
        boolean isLast = false;
        if ( (null != o) && (null != i) && o instanceof ObjectAttribute) {
            ObjectAttributeDTO oa = (ObjectAttributeDTO)o;
            if (!oa.isExtended()) {
                return false;
            }
            Iterator it = i.iterator();
            ObjectAttributeDTO objAttr = null;
            if ( it != null ) {
                while ( it.hasNext() ) {
                    objAttr = (ObjectAttributeDTO)it.next();
                    if (objAttr != null && objAttr.getUid().equals(oa.getUid())) {
                        if (it.hasNext()) {
                            ObjectAttributeDTO nextAttr = (ObjectAttributeDTO)it.next();
                            if (nextAttr != null && !nextAttr.isExtended()) {
                                return true;
                            }
                            else {
                                return false;
                            }
                        }
                        else {
                            return false;
                        }
                        
                    }
                    
                }
            }
        }
        return isLast;
    }


    
    /**
     * Takes the given String and prepares it for CSV export, including 
     * escaping any double-quotes contained in the String and wrapping
     * the String itself in double-quotes.
     * 
     * @param str String to be exported
     *  
     * @return String prepared for CSV export
     */
    public static String buildCSVField(String str) {        
        if (str == null)
            return "";
        
        int index = -1;

        // Escape any excel formula injection
        str = Rfc4180CsvBuilder.escapeFormulaInjection(str);

        StringBuilder sb = new StringBuilder(str);      
        while ((index = sb.indexOf("\"", index)) != -1) {
            sb.insert(index, "\"");
            
            // skip the two double-quotes for the next search
            index += 2;
        }
        
        // now wrap the whole thing in double-quotes
        sb.insert(0, "\"");
        sb.append("\"");
        
        return sb.toString();
    }        

    public static String fetchDisplayNameForIdentityName(String identityName) 
        throws GeneralException {
        
        if (Util.isNullOrEmpty(identityName)) {
            return identityName;
        }
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        Identity identity =   ctx.getObjectByName(Identity.class, identityName);
        if (identity == null) {
            log.warn("Could not find identity with name: " + identityName);
            return identityName;
        } else {
            return identity.getDisplayableName();
        }
    }
    
    /**
     * Attempts to translate approval item values of the form
     * name = value for localization purposes. If the value does not
     * match this form, Object.toString() is returned.
     * 
     * @param context The FacesContext to use.
     * @param value The value to translate.
     * @return The translated value.
     */
    public static String translateApprovalItemValue(FacesContext context, Object value)
    {
        if (!(value instanceof String)) {
            return String.valueOf(value);
        }
        
        String stringValue = (String)value;
        
        int equalsIndex = stringValue.indexOf('=');        
        if (equalsIndex >= 0 && equalsIndex < (stringValue.length() - 1)) {
            String propertyName = stringValue.substring(0, equalsIndex).trim();            
            String propertyValue = stringValue.substring(equalsIndex + 1).trim();
            
            return translatePropertyName(context, propertyName) + " = " + propertyValue;
        }
        
        return stringValue;
    }
    
    /**
     * Translates a property name using the Identity object config.
     * 
     * @param context The FacesContext to use.
     * @param propertyName The name of the property.
     * @return The translated name.
     */
    private static String translatePropertyName(FacesContext context, String propertyName)
    {
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        
        if (identityConfig != null) {            
            String displayName = identityConfig.getDisplayName(propertyName, context.getViewRoot().getLocale());
            if (displayName != null) {
                return displayName;
            }
        }
        
        return propertyName;
    }
    
    public static String getLocalizedEntitlementDescription(FacesContext context, String appName, String attrName, String attrValue) {
        String desc = null;
        try {
            Application app = SailPointFactory.getCurrentContext().getObjectByName(Application.class, appName);
            if (app != null)
                // jsl - should we be passing the browser locale?
                desc = Explanator.getDescription(app, attrName, attrValue, (Locale)null);
        }
        catch (Exception ignore) {
            // do nothing
        }

        if (desc == null)
            desc = localizeMessage(MessageKeys.LABEL_NO_DESCRIPTION);

        return desc;
    }
    
    /**
     * Determines whether or not the specified input string contains unsafe character combinations.
     * @param value The input string to test.
     * @return False if unsafe characters are detected, true otherwise.
     */
    public static boolean isSafeValue(String value) {
        try {
            detectXSS(value);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Encodes closing script tags within a string that is output directly to a javascript tag.
     * @param value The value to clean.
     * @return The encoded value.
     */
    public static String xss(String value) {
        if (value == null) {
            return value;
        }
        
        return value.replace("</", "<\\/");    	
    }
    
    /**
     * Determines whether or not a URL is valid for redirection.
     * @param url The test url. This must be an ABSOLUTE url.
     * @return True if the URL is safe for redirection, false otherwise.
     */
    public static boolean isValidRedirectUrl(String url)
    {   
    	if (Util.isNullOrEmpty(url)) {
    		return false;
    	}
    	
    	ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
    	HttpServletRequest request = (HttpServletRequest)externalContext.getRequest();
    	
    	try {
    	    
    	    //Use this to allow relative url.
    	    if(url.startsWith("/")) {
    	        return true;
    	    }
    	    
	    	URL serverUrl = new URL(request.getScheme(), request.getServerName(), request.getServerPort(), externalContext.getRequestContextPath());
	    	
	    	URL redirectUrl = new URL(url);
	    	
	    	if (!Util.nullSafeEq(serverUrl.getProtocol(), redirectUrl.getProtocol())) {
	    		return false;
	    	}
	    	
	    	if (!Util.nullSafeEq(serverUrl.getHost(), redirectUrl.getHost())) {
	    		return false;
	    	}
	    	
	    	if (!Util.nullSafeEq(serverUrl.getPort(), redirectUrl.getPort() == -1 ? redirectUrl.getDefaultPort() : redirectUrl.getPort())) {
	    		return false;
	    	}
	    	
	    	return true;
    	} catch (Exception ex) {
    		return false;
    	}
    }

    /**
     * Formats the specified date in short date style using the logged in user's
     * timezone and local. Falls back to server's timezone.
     * @param date The date to format.
     * @return A String representation of the formatted date.
     */
    public static String formatDate(Date date) {
        return formatDate(date, DateFormat.SHORT);
    }

    /**
     * Formats the specified date using the specified date style and the logged
     * in user's timezone and local. Falls back to server's timezone.
     * @param date The date to format.
     * @param dateStyle The date style.
     * @return A String representation of the formatted date.
     */
    public static String formatDate(Date date, int dateStyle) {
        DateFormat format = DateFormat.getDateInstance(dateStyle, getLocale());
        format.setTimeZone(getTimeZone());

        return format.format(date);
    }

    /**
     * Formats the specified date in short date and time style using the logged
     * in user's timezone and local. Falls back to server's timezone.
     * @param date The date to format.
     * @return A String representation of the formatted date.
     */
    public static String formatDateTime(Date date) {
        return formatDateTime(date, DateFormat.SHORT, DateFormat.SHORT);
    }

    /**
     * Formats the specified date using the specified date and time styles using
     * the logged in user's timezone and local. Falls back to server's timezone.
     * @param date The date to format.
     * @param dateStyle The date style.
     * @param timeStyle The time style.
     * @return A String representation of the formatted date.
     */
    public static String formatDateTime(Date date, int dateStyle, int timeStyle) {
        return Internationalizer.getLocalizedDate(date, dateStyle, timeStyle, getLocale(), getTimeZone());
    }

    /**
     * Returns a localized name for the specified template usage
     * @param usageString String representation of the Template.Usage whose name is being localized
     * @return localized name of the specified usage
     */
    public static String getLocalizedUsage(String usageString) {
        String msg = "";
        
        if (!Util.isNullOrEmpty(usageString)) {
            Usage usage;
            
            try {
                usage = Enum.valueOf(Usage.class, usageString);
            } catch (Exception e) {
                usage = null;
                log.error("Failed to generate Usage named " + usageString, e);
            }
            
            if (usage != null && !Util.isNullOrEmpty(usage.getMessageKey())) {
                msg = localizeMessage(usage.getMessageKey());
            }
        }
        
        return msg;
    }
    
    private static TimeZone getTimeZone() {
        return WebUtil.getTimeZone(TimeZone.getDefault());
    }
    
    /**
     * Gets the time zone of the currently logged in user. Falls back to default
     * timezone.
     * @param def default timezone to fall back to
     * @return The time zone.
     */
    public static TimeZone getTimeZone(TimeZone def) {
        TimeZone tz = null;
        FacesContext facesCtx = FacesContext.getCurrentInstance();
        if (facesCtx != null && facesCtx.getExternalContext() != null) {
            Map session = facesCtx.getExternalContext().getSessionMap();
            if (session != null && session.get(PageCodeBase.SESSION_TIMEZONE) != null) {
                tz = (TimeZone)session.get(PageCodeBase.SESSION_TIMEZONE);
            }
        }

        if (tz == null) {
            tz = def;
        }

        return tz;
    }

    /**
     * Attempts to get the locale for the currently logged in user.
     * @return The locale.
     */
    private static Locale getLocale() {
        FacesContext o = FacesContext.getCurrentInstance();
        Locale locale = null;
        if (o != null && o.getViewRoot() != null) {
            locale = o.getViewRoot().getLocale();
        }
        return locale;
    }
    
    /**
     * 
     * @param dt Date need to be convert to localized string
     * @param dateStyle DateFormat
     * @return localized date string
     */
	public static String getlocalizeDate(Date dt, Integer dateStyle){
		String localizeDate = Internationalizer.getLocalizedDate(dt ,
				dateStyle, null,
                getLocale(), getTimeZone());
		return localizeDate;
	}
	
	/**
	 * 
	 * @param dt Date need to be convert to localized string
	 * @param timeStyle DateFormat
	 * @return localized time string
	 */
	public static String getlocalizeTime(Date dt, Integer timeStyle){
		String localizeTime = Internationalizer.getLocalizedDate(dt,
                null, timeStyle,
                getLocale(), getTimeZone());
		return localizeTime;
	}

    /**
     * Convert a RoleOverlapResult to a JSON string for use in grid
     * @param roleOverlapResult RoleOverlapResult
     * @return JSON string with 'totalCount' as totalProperty and 'roles' as root
     */
    public static String getRoleOverlapJson(RoleOverlapResult roleOverlapResult) {

        Map<String, Object> jsonMap = new HashMap<String, Object>();
        List<Map<String, Object>> roleOverlapRows = new ArrayList<Map<String, Object>>();
        jsonMap.put("roles", roleOverlapRows);
        if (roleOverlapResult == null) {
            jsonMap.put("totalCount", 0);
        } else {
            jsonMap.put("totalCount", roleOverlapResult.getRoleOverlapCount());
            List<RoleOverlapResult.RoleOverlap> roleOverLaps = roleOverlapResult.getRoleOverlaps();
            if (roleOverLaps != null) {
                for (RoleOverlapResult.RoleOverlap roleOverlap : roleOverlapResult.getRoleOverlaps()) {
                    Map<String, Object> roleOverlapRow = new HashMap<String, Object>();
                    roleOverlapRow.put("name", roleOverlap.getName());
                    roleOverlapRow.put("type", roleOverlap.getType());
                    roleOverlapRow.put("localAssignment", roleOverlap.getLocalAssignment());
                    roleOverlapRow.put("assignment", roleOverlap.getAssignment());
                    roleOverlapRow.put("localProvisioning", roleOverlap.getLocalProvisioning());
                    roleOverlapRow.put("provisioning", roleOverlap.getProvisioning());
                    roleOverlapRows.add(roleOverlapRow);
                }
            }
        }
        return JsonHelper.toJson(jsonMap);
    }

    /**
     * Check if the request is from a mobile page 
     * @param request HttpServletRequest
     * @return true if request URI is from mobile directory
     */
    public static boolean isMobile(HttpServletRequest request) {
        if (request != null) {
            String uri = (String)request.getAttribute(ERROR_REQUEST_URI_ATTRIBUTE);
            if (uri == null) {
                uri = request.getRequestURI();
            }
            if (uri != null && uri.indexOf(MOBILE_PATH) > -1) {
                return true;
            }
        }

        return false;
        
    }

    /**
     * Reformats a URL that contains a fragment identifier (named anchor) to one that
     * uses the RedirectResource REST resource for redirection instead.  This is necessary
     * since not all browsers will re-apply the fragment identifier after getting redirected
     * though the login process.
     *
     * Will convert:
     *  - ui/index.jsf#/myApprovals
     *  to
     *  - http://<server>/identityiq/ui/rest/redirect?page=index.jsf&hash=myApprovals
     *
     *  Also handles query params:
     *  - ui/index.jsf?a=foo&b=bar#/myApprovals
     *  to
     *  - ui/rest/redirect?a=foo&b=bar&page=index.jsf&hash=myApprovals
     *
     *  Ignores strings without a pound sign '#':
     *  - http://<server>/identityiq/workitem/detail.jsf?a=foo&b=bar
     *
     * See bug #20527 for more info.
     *
     * @param url String URL that contains a fragment identifier (named anchor)
     * @return String URL to the REST redirect resource
     */
    public static String getRedirectURL(String url) throws GeneralException {
        StringBuilder redirectURL = new StringBuilder();
        String pound = "#";

        String rootPath = (String) WebUtil.getSystemConfigurationValue(Configuration.SERVER_ROOT_PATH,
                Configuration.DEFAULT_SERVER_ROOT_PATH);

        // Strip trailing slash since that will be added back later.
        if (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }

        if (url != null && url.contains(pound)) {
            String qm = "?";

            String hash = url.substring(url.indexOf(pound) + 1, url.length());
            if (hash.startsWith("/")) {
                hash = hash.substring(1);
            }

            String page = url.substring(0, url.indexOf(pound));
            if (page.startsWith(rootPath)) {
                page = page.substring(rootPath.length());
            }
            String path = null;
            if(page.contains("/")) {
                path = page.substring(0, page.lastIndexOf("/") + 1);
                page = page.substring(page.lastIndexOf("/") + 1);
            }

            // Parse out the query string (if any)
            String query = "";
            if (hash.contains(qm)) {
                query = hash.substring(hash.indexOf(qm) + 1, hash.length());
                hash = hash.substring(0, hash.indexOf(qm));
            }
            else if (page.contains(qm)) {
                query = page.substring(page.indexOf(qm) + 1, page.length());
                page = page.substring(0, page.indexOf(qm));
            }

            StringBuilder queryBuilder = new StringBuilder(query);
            if(queryBuilder.length() > 0) {
                queryBuilder.append(RedirectService.QUERY_SEP);
            }

            queryBuilder.append(RedirectService.PAGE_KEY).append("=");
            if(Util.isNotNullOrEmpty(path)){
                if (!path.startsWith("/")) {
                    queryBuilder.append("/");
                }
                queryBuilder.append(path);
            }
            queryBuilder.append(page).append(RedirectService.QUERY_SEP);
            queryBuilder.append(RedirectService.HASH_KEY).append("=").append(hash);

            // Build up the redirect URL
            redirectURL.append(rootPath)
                    .append("/ui/rest/")
                    .append(Paths.REDIRECT)
                    .append(qm)
                    .append(queryBuilder.toString());
        }
        else {
            if (!url.startsWith(rootPath)) {
                url = rootPath + (!url.startsWith("/") ? "/" : "") + url;
            }
            return url;
        }

        return redirectURL.toString();
    }

    /**
     * Validate the provided limit against our configured maximum and default list result size.
     * If it is higher than our maximum, return the configured maximum.
     * If it is 0 or less, return the configured default.
     * @param limit The limit parameter passed in the original request.
     * @return A valid limit for the list result.
     */
    public static int getResultLimit(int limit) {
        int maxLimit = DEFAULT_MAX_LIST_RESULT_SIZE;
        Configuration configuration = Configuration.getSystemConfig();
        if (configuration != null && configuration.containsKey(Configuration.MAX_LIST_RESULT_SIZE)) {
            maxLimit = configuration.getInt(Configuration.MAX_LIST_RESULT_SIZE);
        }

        if (limit >  maxLimit) {
            if (log.isDebugEnabled()) {
                log.debug("Limit " + limit + " exceeds max allowed result size " + maxLimit);
            }
            limit = maxLimit;
        } else if (limit <= 0) {
            limit = DEFAULT_LIST_RESULT_SIZE;
            if (configuration != null && configuration.containsKey(Configuration.DEFAULT_LIST_RESULT_SIZE)) {
                limit = configuration.getInt(Configuration.DEFAULT_LIST_RESULT_SIZE);
            }
            if (log.isDebugEnabled()) {
                log.debug("Invalid limit specified, defaulting to " + limit);
            }
        }

        return limit;
    }
}
