/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.help;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;

/**
 * Servlet for the help documentation. Traditionally this maintained references to specific places
 * in the help based on keys in a properties file. However, decision was made to do away with all that and
 * always load the main page, so it simply redirects to the index in a configurable location.
 */
public class HelpServlet extends HttpServlet {

    private static final long serialVersionUID = 5540208112008601975L;
    private static Log log = LogFactory.getLog(HelpServlet.class);

    private static final String PARAM_HELP_PATH = "helpPath";
    private static final String DEFAULT_HELP_PATH = "/doc/help/help/index.html";

    private String helpPath;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        this.helpPath = config.getInitParameter(PARAM_HELP_PATH);
        if (Util.isNothing(this.helpPath)) {
            this.helpPath = DEFAULT_HELP_PATH;
        }
    }

    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        res.sendRedirect(req.getContextPath() + this.helpPath);
    }
}
