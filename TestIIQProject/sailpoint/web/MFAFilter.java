/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.HttpMethod;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.Identity;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.server.Authenticator;
import sailpoint.service.LoginService;
import sailpoint.service.PageAuthenticationService;
import sailpoint.service.SessionStorage;
import sailpoint.service.WorkflowSessionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * Filter installed to manage the session for MFA.
 * 
 * 
 * @author danny.feng
 *
 */
public class MFAFilter implements Filter {

    public static final String MFA_USER_NAME = "MFA_USERNAME";
    public static final String MFA_SELECTED_WORKFLOW = "MFA_SELECTED_WORKFLOW";
    public static final String MFA_WORKFLOWS = "MFA_WORKFLOWS";
    
    /** Flag to indicate to redirect to AuthAnswers flow **/
    public static final String NEED_AUTH_ANSWERS = "NEED_AUTH_ANSWERS";

    /** Flag to indicate to redirect to ExpiredPassword flow **/
    public static final String EXPIRED_PASSWORD = "EXPIRED_PASSWORD";

    /** Flag to indicate to redirect to ResetPassword/UnlockAccount flow **/
    public static final String RESET_UNLOCK = "RESET_UNLOCK";
    
    public static final String RESET_UNLOCK_URL = "RESET_UNLOCK_URL";
    
    private static Log log = LogFactory.getLog(MFAFilter.class);
    
    private String homeUrl = null;
    private String mobileHomeUrl = null;
    private String authAnswersUrl = null;
    private String expiredPasswordUrl = null;
    private String mfaCancelUrl = null;


    /**
     * Extract the <init-param>s.
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        homeUrl  = config.getInitParameter("homeUrl");
        mobileHomeUrl  = config.getInitParameter("mobileHomeUrl");
        authAnswersUrl  = config.getInitParameter("authAnswersUrl");
        expiredPasswordUrl  = config.getInitParameter("expiredPasswordUrl");
        mfaCancelUrl = config.getInitParameter("mfaCancelUrl");
    }

    @Override
    public void destroy() {
        
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        // don't create one unless we have to
        HttpSession httpSession = httpRequest.getSession(false);
        
        WorkflowSessionWebUtil sessUtil = new WorkflowSessionWebUtil(httpSession);
        WorkflowSession wfSess = sessUtil.getWorkflowSession();
        if (log.isDebugEnabled()) {
            boolean isExist = sessUtil.hasWorkflowSession();
            log.debug(String.format("url: %s wf session exists: %s", httpRequest.getRequestURL().toString(), isExist));
            if (wfSess != null) {
                WorkflowCase caze = wfSess.getWorkflowCase();
                log.debug("is wf case null: " + (caze == null));
                if (caze != null) {
                    log.debug("is complete: " + caze.isComplete() + " is error: " + caze.isError());
                }
            }
        }
        
        if (isMFACancel(httpRequest)) {
            // don't forget to remove session variables after determining state
            sessUtil.removeWorkflowSession();
            //TODO redirect to login page. For now PageAuthFilter takes over and because the session is unauthenticated
            //     will redirect again to the login page.
            redirectToHome(httpRequest, httpResponse, httpSession);
            // don't call chain.doFilter() on redirect.
            return;
        } else if (isMFAError(wfSess)) {
            addErrorMessages(httpSession, wfSess);
            sessUtil.removeWorkflowSession();
        } else if (isMFASuccess(wfSess)) {
            login(httpRequest, httpResponse, httpSession, sessUtil);
            // sessUtil contains an httpSession reference to an invalidated session, do not call sessUtil.removeWorkflowSession();
            
            // At this point we will have redirected somewhere.  Don't call chain.doFilter() on redirect.
            return;
        } else {
            /* IIQHH-514 - no way to control 302 redirect to homepage when angular POST occurs and response contains homepage html.
             * bail when user is already logged in.
             */
            if (isLoggedIn(httpSession)) {
                redirectToHome(httpRequest, httpResponse, httpSession);
                return;
            }

            if (!sessUtil.hasWorkflowSession()) {
                try {
                    if(HttpMethod.POST.equals(httpRequest.getMethod())) {
                        addMfaWorkflowPostDataToSession(httpSession, request);
                    }

                    // see if we have a workflowName stored in the session.  If so, launch the workflow,
                    // but if not continue on to the selection page.
                    String workflowName = (String)httpSession.getAttribute(MFA_SELECTED_WORKFLOW);
                    if(!Util.isNullOrEmpty(workflowName)) {
                        startMFAWorkflow(httpSession);
                    }
                } catch (Throwable e) {
                    // do not throw here, messages will be logged downstream
                    log.error("Error when starting MFA workflow:", e);
                }
                WorkflowSession ses = sessUtil.getWorkflowSession();
                // we just started a workflow, it's possible an error already occurred
                if (isMFAError(ses)) {
                    addErrorMessages(httpSession, ses);
                } else if (isMFASuccess(ses)) {
                    // it's possible the workflow allows bypassing second factor auth
                    login(httpRequest, httpResponse, httpSession, sessUtil);
                    // sessUtil contains an httpSession reference to an invalidated session, do not call sessUtil.removeWorkflowSession();
                    // redirect occurred, don't chain.doFilter()
                    return;
                }
            }
        }

        chain.doFilter(httpRequest, httpResponse);
    }
    
    List<Message> getErrorMessages(WorkflowSession wfSession) {
        if (wfSession != null && wfSession.getWorkflowCase() != null) {
            WorkflowCase wfcase = wfSession.getWorkflowCase();
            List<Message> messages = wfcase.getMessagesByType(Type.Error);
            for (Message message : Util.safeIterable(messages)) {
                log.error(message.getLocalizedMessage());
            }
        }
        // obscure messages from unauthenticated users        
        return Arrays.asList(new Message(Type.Error, MessageKeys.LOGIN_AUTHENTICATION_FAILED, (String)null));
    }

    boolean isMFAError(WorkflowSession wfSess) {
        /* workflow case is complete and has errored */
        return wfSess != null && wfSess.getWorkflowCase() != null && wfSess.getWorkflowCase().isComplete() && wfSess.getWorkflowCase().isError();
    }

    void addErrorMessages(HttpSession httpSession, WorkflowSession wfSess) {
        List<Message> errorList = getErrorMessages(wfSess);
        @SuppressWarnings("unchecked")
        List<FacesMessage> msgs = (List<FacesMessage>) httpSession.getAttribute(PageCodeBase.SESSION_MESSAGES);
        if (null == msgs) {
            msgs = new ArrayList<FacesMessage>();
            httpSession.setAttribute(PageCodeBase.SESSION_MESSAGES, msgs);
        }
        
        for (Message spMess : Util.safeIterable(errorList)) {
            msgs.add(WebUtil.getFacesMessage(spMess, null, null, null));
        }
    }
    
    void login(HttpServletRequest httpRequest, HttpServletResponse httpResponse, HttpSession httpSession,
               WorkflowSessionWebUtil workflowSessionWebUtil) throws IOException, ServletException {
        Boolean expiredPassword = (Boolean) httpSession.getAttribute(MFAFilter.EXPIRED_PASSWORD);
        Boolean isResetUnlock = (Boolean) httpSession.getAttribute(MFAFilter.RESET_UNLOCK);
        httpSession.removeAttribute(MFAFilter.EXPIRED_PASSWORD);
        httpSession.removeAttribute(MFAFilter.RESET_UNLOCK);
        if (Util.otob(expiredPassword)) {
            addErrorMessages(httpSession, workflowSessionWebUtil.getWorkflowSession());
            // IIQCB-2066 - Expired password no longer logs LoginFailed AuditEvent
            try {
                String accountId = Util.otos(httpSession.getAttribute(LoginBean.ATT_ORIGINAL_ACCOUNT_ID));
                Authenticator.logAuthFailure(getContext(), accountId);
            } catch (GeneralException e) {
                log.error("Error when processing mfa-success expired password action:", e);
                throw new ServletException(e);
            }

            String expiredPasswordAppName =
                    (String)httpSession.getAttribute(LoginBean.ATT_EXPIRED_APP_NAME);
            Message msg = new Message(Message.Type.Error,
                MessageKeys.LOGIN_EXPIRED_PROMPT, expiredPasswordAppName);

            List<FacesMessage> msgs = (List<FacesMessage>) httpSession.getAttribute(PageCodeBase.SESSION_MESSAGES);
            if (null == msgs) {
                msgs = new ArrayList<FacesMessage>();
            }

            msgs.add(WebUtil.getFacesMessage(msg, null, null, null));
            httpSession.setAttribute(PageCodeBase.SESSION_MESSAGES, msgs);

            workflowSessionWebUtil.removeWorkflowSession();
            //Don't set principal user for expiredPassword
            redirectToURL(httpRequest, httpResponse, expiredPasswordUrl);

        } else if (Util.otob(isResetUnlock)) {
            String resetUnlockUrl = (String) httpSession.getAttribute(MFAFilter.RESET_UNLOCK_URL);
            httpSession.removeAttribute(MFAFilter.RESET_UNLOCK_URL);
            workflowSessionWebUtil.removeWorkflowSession();
            redirectToURL(httpRequest, httpResponse, resetUnlockUrl);

        } else {
            // #IIQCB-2560 - session is not reset after SAML or rule-based SSO : get all session attributes, do this before session is invalidated and login occurs
            String userName = (String) httpSession.getAttribute(MFAFilter.MFA_USER_NAME);
            httpSession.removeAttribute(MFAFilter.MFA_USER_NAME);
            
            Boolean needAuthAnswers = (Boolean) httpSession.getAttribute(MFAFilter.NEED_AUTH_ANSWERS);
            httpSession.removeAttribute(MFAFilter.NEED_AUTH_ANSWERS);
            
            //This is saved from LoginBean
            String redirectUrl = (String) httpSession.getAttribute(PageAuthenticationService.ATT_PRE_LOGIN_URL);
            //remove from session after consuming it.
            httpSession.removeAttribute(PageAuthenticationService.ATT_PRE_LOGIN_URL);

            if(Util.isNullOrEmpty(redirectUrl)) {
                redirectUrl = (String)httpSession.getAttribute(PageAuthenticationService.ATT_POST_MFA_REDIRECT);
            }
            httpSession.removeAttribute(PageAuthenticationService.ATT_POST_MFA_REDIRECT);
            
            Map<String, Object> persistedSessionAttrs = getSessionAttributes(httpSession);
            // #IIQCB-2560 - END Getting session attributes
            
            try {
                Identity user = getContext().getObjectByName(Identity.class,  userName);

                //update lastLogin and create login audit event
                Authenticator.updateIdentityOnSuccess(getContext(), user);

                // #IIQCB-2560 - invalidate session before login
                httpSession.invalidate();
                httpSession = httpRequest.getSession(true);
                SessionStorage sessionStorage = new HttpSessionStorage(httpSession);
                LoginService.writeAncillarySession(sessionStorage, persistedSessionAttrs);
                LoginService.writeIdentitySession(sessionStorage, user);

            } catch (GeneralException e) {
                log.error("Error when processing mfa-success action:", e);
                throw new ServletException(e);
            } 

            if (Util.otob(needAuthAnswers)) {
                redirectToURL(httpRequest, httpResponse, authAnswersUrl);
            } else {
                if (Util.isNotNullOrEmpty(redirectUrl)) {
                    redirectToURL(httpRequest, httpResponse, redirectUrl);
                } else {
                    redirectToHome(httpRequest, httpResponse, httpSession);
                }
            }
        }
    }

    private boolean isMFACancel(HttpServletRequest httpRequest) {
        String url = httpRequest.getRequestURL().toString();
        return (mfaCancelUrl != null && url.indexOf(mfaCancelUrl) >= 0);
    }
        
    private void addMfaWorkflowPostDataToSession(HttpSession httpSession,
            ServletRequest request) throws IOException, GeneralException {
        BufferedReader reader = request.getReader();
        StringBuilder jsonStringBuilder = new StringBuilder();
        String line = null;
        while((line = reader.readLine()) != null) {
            jsonStringBuilder.append(line);
        }
        String jsonString = jsonStringBuilder.toString();
        Map<String, String> jsonData = JsonHelper.mapFromJson(String.class, String.class, jsonString);
        httpSession.setAttribute(MFA_SELECTED_WORKFLOW, jsonData.get("mfaWorkflow"));
    }
    
    private boolean isMFASuccess(WorkflowSession wfSess) {
        return /* workflow case is complete and does not contain errors */
               (wfSess != null && wfSess.getWorkflowCase() != null && wfSess.getWorkflowCase().isComplete() && !wfSess.getWorkflowCase().isError());
    }

    private void redirectToURL(HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse,
                               String url) throws IOException
    {
        String path = httpRequest.getContextPath();
        if (url != null && url.indexOf(path) >= 0) {
            httpResponse.sendRedirect(url);
        } else {
            httpResponse.sendRedirect(path + url);
        }
    }


    private boolean isMobileLogin(HttpSession httpSession) {
        return isBooleanAttribute(httpSession, PageAuthenticationService.ATT_MOBILE_LOGIN);
    }
    
    private boolean isSSOAuth(HttpSession httpSession) {
        return isBooleanAttribute(httpSession, PageAuthenticationService.ATT_SSO_AUTH);
    }
    
    private boolean isBooleanAttribute(HttpSession httpSession, String attributeName) {
        return Util.otob( httpSession.getAttribute(attributeName));
    }
    
    private Map<String, Object> getSessionAttributes(HttpSession httpSession) {
        Map<String, Object> result = new HashMap<>();
        result.put(PageAuthenticationService.ATT_MOBILE_LOGIN, isMobileLogin(httpSession));
        result.put(PageAuthenticationService.ATT_SSO_AUTH, isSSOAuth(httpSession));
        
        result.putAll(LoginService.getLinkSessionAttributes(new HttpSessionStorage(httpSession)));
        return result;
    }
    
    private boolean isLoggedIn(HttpSession httpSession) {
        boolean result = false;
        SessionStorage store = new HttpSessionStorage(httpSession);
        try {
            result = new LoginService(getContext()).isLoggedIn(store);
        } catch (GeneralException e) { /* ignore */ }
        return result;
    }
    
    // create workflow session, and store it in session
    private void startMFAWorkflow(HttpSession httpSession) throws GeneralException {
        String workflowName = (String)httpSession.getAttribute(MFA_SELECTED_WORKFLOW);
        String identityName = (String)httpSession.getAttribute(MFA_USER_NAME);
    
        log.info("Executing MFA Workflow: " + workflowName);

        if (!Util.isNullOrEmpty(workflowName)) {
            Workflow workflow = getContext().getObjectByName(Workflow.class, workflowName.trim());
            if (workflow == null) {
                throw new GeneralException("No Workflow found with name: " + workflowName);
            }
    
            // launch a session
            WorkflowLaunch wfl = new WorkflowLaunch();
            wfl.setLauncher(identityName);
            wfl.setWorkflow(workflow);
            wfl.setCaseName("MFA Workflow: " +  workflow.getName() + " for identity: " + identityName);
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("identityName", identityName);
            // all MFA workflows are transient
            args.put(Workflow.VAR_TRANSIENT, true);
            // persist session so MFAFilter can query status later
            args.put(WorkflowSessionService.VAR_WORKFLOW_SESSION_PERSIST, true);
            wfl.setVariables(args);

            Workflower wf = new Workflower(getContext());
            WorkflowSession ses = wf.launchSession(wfl);
            
            httpSession.setAttribute(WorkItemBean.NEXT_PAGE, ses.getNextPage());
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(httpSession);
            wfUtil.saveWorkflowSession(ses);
        }
    }
    
    private void redirectToHome(HttpServletRequest httpRequest, HttpServletResponse httpResponse, HttpSession httpSession) throws IOException {
        if (isMobileLogin(httpSession)) {
            redirectToURL(httpRequest, httpResponse, mobileHomeUrl);
        } else {
            redirectToURL(httpRequest, httpResponse, homeUrl);
        }
    }
    
    private SailPointContext getContext() throws GeneralException {
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        return ctx;
    }
}  
