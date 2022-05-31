package sailpoint.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public class Error500Servlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * Exception page for standard paths
     */
    private static final String STANDARD_EXCEPTION_REDIRECT = "/exception.jsf";

    /**
     * Exception page for mobile paths
     */
    private static final String MOBILE_EXCEPTION_REDIRECT = "/ui/500.jsf";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
	    
	    /* Don't log here - let the context request filter handle logging
	    Log log = LogFactory.getLog(Error500Servlet.class);
	    */

        String redirect = WebUtil.isMobile(req) ? MOBILE_EXCEPTION_REDIRECT : STANDARD_EXCEPTION_REDIRECT;

        // if syslogging is turned off, there won't be a quick key
	    String quickKey = SyslogThreadLocal.get();
	    if (Util.isNotNullOrEmpty(quickKey)) {
	        //IIQTC-85 :- Using session attributes instead of URL parameters to avoid data injection.
	        req.getSession().setAttribute("qk", quickKey);
	    } else {
	        req.getSession().removeAttribute("qk");
	    }
	    // redirect to the Red Message of Death ;)
		resp.sendRedirect(req.getContextPath() + redirect);
    }
}