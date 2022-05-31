/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.util;

import javax.faces.application.Application;
import javax.faces.application.NavigationHandler;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.tools.Util;

import com.sun.faces.application.ActionListenerImpl;

/**
 * @author peter.holcomb
 *
 */
public class SailPointActionListener  extends ActionListenerImpl {
    
    private static final Log log = LogFactory.getLog(SailPointActionListener.class);
    
    /**
     * JSF Navigation target for exceptions on actions on standard pages
     */
    private static final String STANDARD_EXCEPTION_NAV_TARGET = "exceptionNavigation";

    /**
     * JSF Navigation target for exception on actions on mobile pages
     */
    private static final String MOBILE_EXCEPTION_NAV_TARGET = "mobileExceptionNavigation";
        
    public void processAction(ActionEvent event) {
        try {
            super.processAction(event);
        } catch (Exception exception) {
            log.error("Caught unhandled JSF exception: " + exception.getMessage(), exception);

            // Look for the HttpServletRequest in the FacesContext to check path for mobile
            FacesContext facesContext = FacesContext.getCurrentInstance();
            Object request = facesContext.getExternalContext().getRequest();
            boolean isMobile = 
                    (request instanceof HttpServletRequest && WebUtil.isMobile((HttpServletRequest)request));
            
            String navTarget = (isMobile) ? MOBILE_EXCEPTION_NAV_TARGET : STANDARD_EXCEPTION_NAV_TARGET;
            
            // if syslogging is turned off, there won't be a quick key
            String quickKey = SyslogThreadLocal.get();
            if (Util.isNotNullOrEmpty(quickKey)) {
                //IIQTC-85 :- Using session attributes instead of URL parameters to avoid data injection.
                facesContext.getExternalContext().getSessionMap().put("qk", quickKey);
            } else {
                facesContext.getExternalContext().getSessionMap().remove("qk");
            }

            
            Application application = facesContext.getApplication();
            NavigationHandler navigationHandler = application.getNavigationHandler();
            navigationHandler.handleNavigation(facesContext, null, navTarget);
            
            facesContext.renderResponse();
        }
    }
}
