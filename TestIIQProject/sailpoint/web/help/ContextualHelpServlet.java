/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.help;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.messages.MessageKeys;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom HttpServlet that is used for serving up pages for the Contextual Help feature.
 * This is also used to send redirects to external pages and resources.
 */
public class ContextualHelpServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    public static final String HELP_KEY_PARAM = "helpKey";
    protected static final String INCLUDED_CSS_PATH = "ui/css/contextual-help.css";
    protected static final String INCLUDED_CUSTOM_CSS_PATH = "ui/css/ui-custom.css";
    protected static final String LOGO_PATH = "ui/images/sailpoint-logo-optimized.png";
    protected static final String TEMPLATE_PATH = "ui/help/contextual-help-template.html";
    private static final String HTML_DOCTYPE = "<!DOCTYPE html>";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String HTML_OPEN = "<html lang=\"en\">";
    private static final String HTML_HEAD_OPEN = "<head>";
    private static final String HTML_HEAD_CHARSET = "<meta charset=\"UTF-8\">";
    private static final String HTML_HEAD_CSS = String.format("<link rel=\"stylesheet\" href=\"%s\">",
                                                              INCLUDED_CSS_PATH);
    private static final String HTML_HEAD_CLOSE = "</head>";
    private static final String HTML_BODY_OPEN = "<body>";
    private static final String HTML_BODY_CLOSE = "</body>";
    private static final String HTML_CLOSE = "</html>";
    /*
     * Height and width offset used when building the <iframe> for videos.
     */
    protected static final int VIDEO_OFFSET = 10;
    /**
     * The following are the template variables used when substituting them for actual values.
     */
    private static final String TEMPLATE_CSS_PATH = "helpPageCSSPath";
    private static final String TEMPLATE_CUSTOM_CSS_PATH = "helpPageCustomCSSPath";
    private static final String TEMPLATE_LOGO_PATH = "helpPageLogoPath";
    private static final String TEMPLATE_CONTENT = "helpPageContent";
    private static final String TEMPLATE_TITLE = "helpPageTitle";
    private static final String TEMPLATE_LOGO_DESC = "helpPageLogoDesc";

    // Help message statics
    protected static final String AUTHENTICATION_ERROR_MSG = "The current session does not have a logged in user.";
    protected static final String MISSING_ERROR_MSG = "ContextualHelpItem identified by %s is not found.";
    protected static final String MISSING_MAP_MSG = "UIConfig is missing the contextualHelp entry.";
    protected static final String INTERNAL_ERROR_MSG = "Error occured while checking authorization";
    protected static final String AUTHORIZATION_ERROR_MSG = "User %s is not authorized to view this help item";
    protected static final String MISSING_HOME_URL = "ContextualHelpItem identified by %s is missing a home url.";

    protected static final String DEFAULT_LOGO_DESC = "SailPoint logo";

    private static Log log = LogFactory.getLog(ContextualHelpServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String userName = getUserNameOnSession(req);

        // if a username does not exist, the user has not logged in yet.
        if (Util.isNullOrEmpty(userName)) {
            
            if (log.isErrorEnabled()) {
                log.error(AUTHENTICATION_ERROR_MSG);
            }
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, AUTHENTICATION_ERROR_MSG);
            return;
        }

        String helpKey = req.getParameter(HELP_KEY_PARAM);

        if (Util.isNotNullOrEmpty(helpKey)) {
            UIConfig uiConfig = UIConfig.getUIConfig();
            Map<String, ContextualHelpItem> map = uiConfig.getContextualHelp();

            if (!Util.isEmpty(map)) {
                ContextualHelpItem item = map.get(helpKey);

                // Do some validity checks. Make sure it exists and has a home url.
                if (item == null) {
                    final String notFoundMsg = String.format(MISSING_ERROR_MSG, helpKey);
                    if (log.isErrorEnabled()) {
                        log.error(notFoundMsg);
                    }
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, notFoundMsg);
                    return;
                } else if (Util.isNullOrEmpty(item.getHomeUrl())) {
                    // Required to have a web resource to check against. Otherwise assume that the user is not authorized
                    if (log.isErrorEnabled()) {
                        log.error(String.format(MISSING_HOME_URL, item.getKey()));
                    }
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                                   String.format(MISSING_HOME_URL, item.getKey()));
                    return;

                }

                // Check if the user is authorized to view this item based on the url of where the button lives.
                boolean isAuthorized = false;
                try {
                    isAuthorized = isAuthorized(item.getHomeUrl(), userName);
                } catch (GeneralException e) {

                    if (log.isErrorEnabled()) {
                        log.error(INTERNAL_ERROR_MSG, e);
                    }
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MSG);
                    return;
                }

                if (!isAuthorized) {
                    final String notAuthMsg = String.format(AUTHORIZATION_ERROR_MSG, userName);
                    if (log.isErrorEnabled()) {
                        log.error(String.format(notAuthMsg));
                    }
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, notAuthMsg);
                    return;
                }

                // Check for URL type and if it is not a template. If it is a template,
                // build the template and content in writeResponse()
                if ((item.getType() == ContextualHelpItem.Type.URL && !item.isUseTemplate()) 
                        || item.getType() == ContextualHelpItem.Type.PDF) {
                    resp.sendRedirect(item.getUrl());
                    return;
                }

                setupResponse(resp);
                writeResponse(resp, req, item);
            } else {
                if (log.isErrorEnabled()) {
                    log.error(MISSING_MAP_MSG);
                }
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, MISSING_MAP_MSG);
                return;
            }
        }
    }

    /**
     * Helper to grab the userName(principal) off the session.
     * @param req The HttpServletRequest that contains the session.
     * @return The userName of the logged in user. Otherwise null
     * @throws ServletException
     */
    private String getUserNameOnSession(HttpServletRequest req) throws ServletException {
        String userName = null;
        if (req != null) {
            HttpSession session = req.getSession();
            if (session != null) {
                userName = (String) session.getAttribute(PageAuthenticationFilter.ATT_PRINCIPAL);
            }
        }
        return userName;
    }

    /**
     * Checks if the logged in user is authorized to view the help item.
     * We use the homeUrl of the contextual help item to see where this button lives.
     * From there we then check if the logged in user is authorized to even view the IIQ page.
     * If the user cannot view the page, then they should not see the help either.
     *
     * @param req The HttpServletRequest
     * @param item The contextual help item being viewed.
     * @param userName The logged in user
     * @return True if the user is authorized to view the item. Otherwise false.
     * @throws GeneralException
     */
    private boolean isAuthorized(String url, String userName)
            throws GeneralException {
        boolean authorized = false;
        SailPointContext context = null;

        try {
            // Because we are in a servlet and not part of a JSF sp page, a sailpoint context does not exist.
            // We must make one in this method and release it at the end.
            context = SailPointFactory.createContext();

            Identity identity = context.getObjectByName(Identity.class, userName);
            if (identity != null) {
                Authorizer authorizer = Authorizer.getInstance();
                if (authorizer != null) {
                    // check if the user can view the page that this item lives at.
                    authorized = authorizer.isAuthorized(url, identity);
                }
            }
            return authorized;

        } finally {
            SailPointFactory.releaseContext(context);
        }
    }

    private void setupResponse(HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(HTML_CONTENT_TYPE);
    }

    private void writeResponse(HttpServletResponse response, HttpServletRequest req,
            ContextualHelpItem item) throws IOException {

        String html = item.isUseTemplate() ? 
                      buildResponseStringWithTemplate(item, req.getContextPath()) :
                      buildResponseString(item);

        if (Util.isNotNullOrEmpty(html)) {
            byte[] raw = html.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(raw.length);

            PrintWriter out = response.getWriter();
            if (out != null) {
                out.println(html);
                out.flush();
                out.close();
            }
        }
    }

    /**
     * Builds the response for the servlet when using a template.
     * This will replace all the template variables with actual strings to populate the template.
     *
     * @param item The ContextualHelpItem being represented
     * @param contextPath Context path of the server used to build paths
     * @return The final html to be returned by the servlet.
     * @throws IOException
     */
    private String buildResponseStringWithTemplate(ContextualHelpItem item, String contextPath)
            throws IOException {
        String response = null;
        String template = null;
        String content = null;

        try {
            content = getTemplate(item.getUrl());
            template = getTemplate(TEMPLATE_PATH);

        } catch (GeneralException e) {
            if (log.isErrorEnabled()) {
                log.error(e);
            }
            // bubble up the IOException so that HTTPServlet's doGet can throw it
            throw new IOException(e);
        }

        if (Util.isNotNullOrEmpty(template) && Util.isNotNullOrEmpty(content)) {
            Map<String, String> substitutions = new HashMap<>();
            substitutions.put(TEMPLATE_CONTENT, content);

            // For the title, css, logo, and description: Check if there is an override.
            // Otherwise use the defaults.
            String titleKey = Util.isNotNullOrEmpty(item.getTitle()) ?
                           item.getTitle() :
                           MessageKeys.UI_CONTEXTUAL_HELP_TITLE;
            String title = Message.localize(titleKey).getLocalizedMessage();

            substitutions.put(TEMPLATE_TITLE, title);

            String cssPath = contextPath + "/" + INCLUDED_CSS_PATH;

            substitutions.put(TEMPLATE_CSS_PATH, cssPath);

            String customCssPath = contextPath + "/" + INCLUDED_CUSTOM_CSS_PATH;

            substitutions.put(TEMPLATE_CUSTOM_CSS_PATH, customCssPath);

            String logoPath = Util.isNotNullOrEmpty(item.getLogoPath()) ?
                              item.getLogoPath() :
                              contextPath + "/" + LOGO_PATH;

            substitutions.put(TEMPLATE_LOGO_PATH, logoPath);

            String logoDesc = Util.isNotNullOrEmpty(item.getLogoDescription()) ?
                              item.getLogoDescription() :
                              DEFAULT_LOGO_DESC;

            substitutions.put(TEMPLATE_LOGO_DESC, logoDesc);

            // Use Apache commons StrSubstitutor to replace the template variables
            StrSubstitutor sub = new StrSubstitutor(substitutions);
            response = sub.replace(template);
        }

        return response;
    }

    /**
     * Attempts to read the file from the classpath.
     * The files are expected to live in WEB-INF/classes which is
     * on the default classpath.
     * @param url Path to the file to read
     * @return The contents of the file.
     * @throws GeneralException
     */
    String getTemplate(String url) throws GeneralException {
        return Util.readResource(url);
    }

    /**
     * Builds the html for the servlet when NOT using a template.
     *
     * @param item The ContextualHelpItem being represented
     * @return The final html to send to the client to be rendered.
     */
    private String buildResponseString(ContextualHelpItem item) {
        StringBuilder sb = new StringBuilder();

        sb.append(HTML_DOCTYPE)
          .append(HTML_OPEN)
          .append(HTML_HEAD_OPEN)
          .append(HTML_HEAD_CHARSET);

        // only include the contextual css if configured
        if (item.isUseIncludedCSS()) {
            sb.append(HTML_HEAD_CSS);
        }

        sb.append(HTML_HEAD_CLOSE)
          .append(HTML_BODY_OPEN);

        if (item.getSource() != null) {
            sb.append(item.getSource());
        }

        if (item.getType() == ContextualHelpItem.Type.Video) {
            sb.append("<iframe width=\"" + (item.getWidth() - VIDEO_OFFSET) + "\" height=\"" + (item.getHeight() - VIDEO_OFFSET) + "\" src=\""
                    + item.getUrl() + "\" frameborder=\"0\" allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe>");
        }
        sb.append(HTML_BODY_CLOSE)
          .append(HTML_CLOSE);

        return sb.toString();
    }
}
