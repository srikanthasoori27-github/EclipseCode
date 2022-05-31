/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Filter installed to check for an authenticated SailPoint server session,
 * redirecting to the login page if not found.
 */

package sailpoint.web;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.Configuration;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Filter installed to handle registration error, 
 * redirect to the login page if cancelled,
 * create workflow session if not exists.
 */
public class RegistrationFilter implements javax.servlet.Filter
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RegistrationFilter.class);

    

    /**
     * Where we go when we need to authenticate.
     */
    private String loginUrl;

    /**
     * Where we go if there is registration error.
     */
    private String registrationErrorUrl;

    /**
     * Where we go if registration is successful.
     */
    private String registrationSuccessUrl;

    /**
     * Action if registration is successful.
     */
    private String registrationSuccessAction;



    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Extract the <init-param>s.
     */
    public void init(FilterConfig config) throws ServletException {
        loginUrl = config.getInitParameter("loginUrl");
        registrationErrorUrl = config.getInitParameter("registrationErrorUrl");
        registrationSuccessUrl = config.getInitParameter("registrationSuccessUrl");
        registrationSuccessAction = config.getInitParameter("registrationSuccessAction");
    }

    /**
     * Called by the container when the filter is destroyed.
     */
    public void destroy() { }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        // don't create one unless we have to
        HttpSession httpSession = httpRequest.getSession(false);
        if (isRegistrationError(httpRequest)) {
            //no more processing here
            chain.doFilter(httpRequest, httpResponse);
        } else if (!isRegistrationEnabled()) {
            redirectToURL(httpRequest, httpResponse, httpSession, registrationErrorUrl);
        } else if (isCancelAction(httpSession)) {
            //need to be before isRegistrationSuccess test.
            httpSession.removeAttribute(WorkflowSessionWebUtil.ATT_IS_CANCEL_ACTION);
            redirectToURL(httpRequest, httpResponse, httpSession, loginUrl);
        } else if (isRegistrationSuccess(httpRequest)) {
            //no more processing here
            chain.doFilter(httpRequest, httpResponse);
        } else {
            if (!(new WorkflowSessionWebUtil(httpSession).hasWorkflowSession())) {
                try {
                    startRegistrationWorkflow(httpSession);
                } catch (GeneralException e) {
                    redirectToURL(httpRequest, httpResponse, httpSession, registrationErrorUrl);
                }
            } else {
                //df: should we check if launch has errors, and add to session?
            }

            if (isMobileReferrer(httpRequest)) {
                httpSession.setAttribute(RegistrationBean.ATT_MOBILE_SSR_LAUNCH, true);
            }
            chain.doFilter(httpRequest, httpResponse);
            
        }
    }

    private boolean isMobileReferrer(HttpServletRequest request) {
        final String REFERER_HEADER = "referer";

        String header = request.getHeader(REFERER_HEADER);

        return header != null && header.contains("/ui/login.jsf");
    }

    private boolean isRegistrationSuccess(HttpServletRequest httpRequest) {
        String url = httpRequest.getRequestURL().toString();
        return (url.indexOf(registrationSuccessUrl) >= 0);
    }

    private void redirectToURL(HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse,
                               HttpSession httpSession,
                               String url) throws IOException
    {
        httpResponse.sendRedirect(httpRequest.getContextPath() + url);
    }

    private boolean isRegistrationError(HttpServletRequest httpRequest) {
        String url = httpRequest.getRequestURL().toString();
        return (url.indexOf(registrationErrorUrl) >= 0);
    }
    
    private boolean isRegistrationEnabled(){
        Configuration systemConfig = Configuration.getSystemConfig();
        boolean registrationEnabled = Util.otob(systemConfig.getBoolean("enableSelfServiceRegistration"));

        return registrationEnabled;
    }
    
    private boolean isCancelAction(HttpSession httpSession){
        boolean isCancelAction = Util.otob( httpSession.getAttribute(WorkflowSessionWebUtil.ATT_IS_CANCEL_ACTION));
        return isCancelAction;
    }
    
    //create workflow session, and store it in session
    private void startRegistrationWorkflow(HttpSession httpSession) throws GeneralException {
        
        // jsl - Workflower.launchSession will do this too, but 
        // it throws rather than return "registrationError", could avoid
        // some duplication but it isn't much
        String workflowName = Configuration.getSystemConfig().getString(Configuration.WORKFLOW_LCM_SSR_REQUEST);
    
        log.info("Executing Registration Workflow: " + workflowName);
    

        if (!Util.isNullOrEmpty(workflowName)) {
            Workflow workflow = getContext().getObjectByName(Workflow.class, workflowName);
            if (workflow == null) {
                if (log.isWarnEnabled())
                    log.warn("No Workflow found with name: " + workflowName);
                return ;
            }
    
            String workflowOwner = Configuration.getSystemConfig().getString(Configuration.SELF_REGISTRATION_WORKGROUP);
    
            // launch a session
            WorkflowLaunch wfl = new WorkflowLaunch();
            wfl.setLauncher(workflowOwner);
            wfl.setWorkflow(workflow);
            // jsl - will need this I18Nd?
            wfl.setCaseName("Registration Launch for identity registration");
            Workflower wf = new Workflower(getContext());
            WorkflowSession ses = wf.launchSession(wfl);
            
            ses.setReturnPage(registrationSuccessAction);
            httpSession.setAttribute(WorkItemBean.NEXT_PAGE, ses.getNextPage());
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(httpSession);
            wfUtil.saveWorkflowSession(ses);
        }
    }
    
    private SailPointContext getContext() throws GeneralException {
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        return ctx;
    }

}
