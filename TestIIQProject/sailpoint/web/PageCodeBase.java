/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base class for all JSF managed beans.
 *
 * This came from the original SecureSite code and comments indicate
 * that it may have been generated.  I'm not sure how much of
 * this is currently used but it looks like a nice general purpose
 * set of toosl for JSF managed beans.
 *
 * Keep this independent of any particular web app.  Put application
 * specific page utilities in a subclass.
 *
 */

package sailpoint.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.faces.application.FacesMessage;
import javax.faces.component.NamingContainer;
import javax.faces.component.StateHolder;
import javax.faces.component.UIComponent;
import javax.faces.component.UIComponentBase;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.service.PageAuthenticationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.sso.SAMLSSOAuthenticator;
import sailpoint.web.util.WebUtil;

/**
 * Provides a common base class for all generated code behind files.
 * 
 * This implements the StateHolder interface so the beans can be saved in the
 * JSF view using the t:saveState tag.  The StateHolder interface is required
 * so the bean can clear any transient state before being saved and restore it
 * when restored. 
 */
public abstract class PageCodeBase
implements StateHolder {

	private static Log log = LogFactory.getLog(PageCodeBase.class);

	/**
	 * Constant of session property used to store messages that need to live
	 * longer than request scope.
	 */
	public static final String SESSION_MESSAGES =
		PageCodeBase.class.getName() + ".sessionMessages";

    public static final String SESSION_TIMEZONE = "timeZone";
    
    public static final String NAV_OUTCOME_HOME = "viewHome"; 

    @Deprecated
    public static final String SESSION_TIMEZONE_REDIRECT_HASH = "timeZoneForm:redirectHash";

    private String initialTimeZoneId;

    private transient FacesContext _facesContext;
	private transient Map _applicationScope;
	private transient Map<String, Object> _sessionScope;
	private transient Map _requestScope;
	private transient Map _requestParam;
    private transient Map _requestParamValues;

	private String sessionId;
    /**
	 *
	 */
	public PageCodeBase() {
		initFacesVariables();
	}

	protected void initFacesVariables() {
		_facesContext = FacesContext.getCurrentInstance();
        if (null != _facesContext) {
            ExternalContext external = _facesContext.getExternalContext();
            _requestScope = external.getRequestMap();
            _sessionScope = external.getSessionMap();
            _applicationScope = external.getApplicationMap();
            _requestParam = external.getRequestParameterMap();
            _requestParamValues = external.getRequestParameterValuesMap();
        }
	}
	
	/**
	 * Clear any transient state on this bean before saving.
	 */
	protected void clearTransientFields() {
		_facesContext = null;
		_applicationScope = null;
		_sessionScope = null;
		_requestScope = null;
		_requestParam = null;
        _requestParamValues = null;
	}

	/**
	 * @return
	 */
	public FacesContext getFacesContext() {
		if (null == _facesContext || _facesContext.isReleased()) {
			initFacesVariables();
		}
		return _facesContext;
	}

	/**
	 * @return
	 */
	public Map getApplicationScope() {
		if (null == _applicationScope) {
			initFacesVariables();
		}
		return _applicationScope;
	}

	/**
	 * @return
	 */
	public Map<String, Object> getSessionScope() {
		if (null == _sessionScope) {
			initFacesVariables();
		}
		return _sessionScope;
	}

	/**
	 * @return
	 */
	public Map getRequestScope() {
		if (null == _requestScope) {
			initFacesVariables();
		}
		return _requestScope;
	}

	/**
	 * @return
	 */
	public Map getRequestParam() {
		if (null == _requestParam) {
			initFacesVariables();
		}
		return _requestParam;
	}

    public String getInitialTimeZoneId() { return this.initialTimeZoneId; }

    public void setInitialTimeZoneId(String timeZoneName) { this.initialTimeZoneId = timeZoneName; }
    
	/**
	 *
	 */
	protected void gotoPage(String pageName) {
		if (pageName != null) {
			UIViewRoot newView = getFacesContext().getApplication().
			getViewHandler().createView(getFacesContext(), pageName);
			getFacesContext().setViewRoot(newView);
		}
	}

	/**
	 *
	 * @param url
	 * @throws IOException
	 */
	protected void redirect(String url) throws IOException {
		getFacesContext().getExternalContext().redirect(url);
	}

	/**
	 * <p>
	 * Return the {@link UIComponent} (if any) with the specified
	 * <code>id</code>, searching recursively starting at the specified
	 * <code>base</code>, and examining the base component itself, followed
	 * by examining all the base component's facets and children. Unlike
	 * findComponent method of {@link UIComponentBase}, which skips recursive
	 * scan each time it finds a {@link NamingContainer}, this method examines
	 * all components, regardless of their namespace (assuming IDs are unique).
	 *
	 * @param base
	 *            Base {@link UIComponent} from which to search
	 * @param id
	 *            Component identifier to be matched
	 */
	public static UIComponent findComponent(UIComponent base, String id) {

		// Is the "base" component itself the match we are looking for?
		if ( id.equals(base.getId()) ) {
			return base;
		}

		// Search through our facets and children
		UIComponent kid = null;
		UIComponent result = null;
		Iterator kids = base.getFacetsAndChildren();
		while ( kids.hasNext() && ( result == null ) ) {
			kid = (UIComponent) kids.next();
			if ( id.equals(kid.getId()) ) {
				result = kid;
				break;
			}
			result = findComponent(kid, id);
			if (result != null) {
				break;
			}
		}
		return result;
	}  // findComponent(UIComponent, String)

	public static UIComponent findComponentInRoot(String id) {
		UIComponent ret = null;

		FacesContext context = FacesContext.getCurrentInstance();
		if (context != null) {
			UIComponent root = context.getViewRoot();
			ret = findComponent(root, id);
		}

		return ret;
	}  // findComponentInRoot(String)

	/**
	 * Place an Object on the tree's attribute map
	 *
	 * @param key
	 * @param value
	 */
	@SuppressWarnings("unchecked")
	protected void putTreeAttribute(String key, Object value) {
		getFacesContext().getViewRoot().getAttributes().put(key, value);
	}

	/**
	 * Retrieve an Object from the tree's attribute map
	 *
	 * @param key
	 * @return
	 */
	protected Object getTreeAttribute(String key) {
		return getFacesContext().getViewRoot().getAttributes().get(key);
	}

	/**
	 * Return the result of the resolved expression
	 *
	 * @param expression
	 * @return
	 */
	protected Object resolveExpression(String expression) {
		Object value = null;
		if ( expression.indexOf("#{") != -1 &&
				expression.indexOf("#{") < expression.indexOf('}') ) {
			value = getFacesContext().getApplication().
			createValueBinding(expression).getValue(getFacesContext());
		} else {
			value = expression;
		}
		return value;
	}

	/**
	 * Resolve all parameters passed in via the argNames/argValues
	 * array pair, and add them to the provided paramMap. If a
	 * parameter can not be resolved, then it will attempt to be
	 * retrieved from a cachemap stored using the cacheMapKey
	 *
	 * @param paramMap
	 * @param argNames
	 * @param argValues
	 * @param cacheMapKey
	 */
	@SuppressWarnings("unchecked")
	protected void resolveParams(Map paramMap,
			String[] argNames,
			String[] argValues,
			String cacheMapKey) {

		Object rawCache = getTreeAttribute(cacheMapKey);
		Map cache = Collections.EMPTY_MAP;
		if ( rawCache instanceof Map ) {
			cache = (Map)rawCache;
		}
		for ( int i = 0; i < argNames.length; i++ ) {
			Object result = resolveExpression(argValues[i]);
			if ( result == null ) {
				result = cache.get(argNames[i]);
			}
			paramMap.put(argNames[i], result);
		}
		putTreeAttribute(cacheMapKey, paramMap);
	}

	/**
	 * Returns a full system path for a file path given relative to
	 * the web project
	 */
	protected static String getRealPath(String relPath) {
		String path = relPath;
		try {
			URL url = FacesContext.getCurrentInstance().
			getExternalContext().getResource(relPath);
			if ( url != null ) {
				path = url.getPath();
			}
		} catch (MalformedURLException e) {
		    if (log.isErrorEnabled())
		        log.error(e.getMessage(), e);
		}
		return path;
	}

	protected String getRequestParameter(String name) {
	    if (null == _requestParam) {
	        initFacesVariables();
	    }
	    return (String) _requestParam.get(name);
	}

    protected String[] getRequestParameterValues(String name) {
        if (null == _requestParamValues) {
            initFacesVariables();
        }
        return (String[]) _requestParamValues.get(name);
    }

	/**
	 * Retrieve the parameter with the given name from either the request scope
	 * or session scope.  If found in the session scope, the parameter is
	 * removed from the session.
	 * 
	 * @param  name  The name of the parameter for which to retrieve the value.
	 * 
	 * @return The parameter with the given name from either the request scope
	 *         or session scope.
	 */
	protected String getRequestOrSessionParameter(String name)
	{
		String param = getRequestParameter(name);
		if (null == param)
		{
			param = (String) getSessionScope().remove(name);
		}
		return Util.getString(param);
	}

    /**
     * 
	 * Retrieve the parameter with the given name from either the request scope
	 * or session scope, but do *not* remove it.
     * @ignore
     * jsl - I've never understood why getRequestOrSessionParameter removes
     * the parameter, this makes it difficult to write "dispatching" beans.
     */
	protected String peekRequestOrSessionParameter(String name) {

		String param = getRequestParameter(name);
		if (null == param) {
            Object o = getSessionScope().get(name);
            if (o != null)
                param = o.toString();
        }
        return param;
	}


	/**
	 *
	 * @return
	 */
	public String getRequestContextPath() {
		return getFacesContext().getExternalContext().getRequestContextPath();
	}

	/**
	 * Return whether the current request has any errors (ie - any messages at
	 * error level or above).
	 * 
	 * @return Return true if the current request has errors, false otherwise.
	 */
    public boolean getHasErrors() {
        // May want to also check the session messages.
        FacesMessage.Severity sev = getFacesContext().getMaximumSeverity();
        return (null != sev) && ((sev.compareTo(FacesMessage.SEVERITY_ERROR)) >= 0);
    }
	
    /**
	 * Returns the FacesMessage list from the session.
	 *
	 * @return List of FacesMessage instances attached to the session, or null if the
	 * SESSION_MESSAGES key has not been added to the session.
	 */
	public List<FacesMessage> getMessages() {

		Map session = getSessionScope();

		if (!session.containsKey(SESSION_MESSAGES))
			return null;

		return (List<FacesMessage>) session.get(SESSION_MESSAGES);
	}

    /**
	 * Helper method for cases where the "required" flag is not flexible enough
	 */
    protected void addRequiredErrorMessage(String componentID) {
        addRequiredErrorMessage(componentID, new Message(Message.Type.Error,
                MessageKeys.ERR_REQUIRED));
    }

	protected void addRequiredErrorMessage(String componentID, String message) {
		addRequiredErrorMessage(componentID, new Message(Message.Type.Error, message));
	}

    protected void addRequiredErrorMessage(String componentID, Message summary) {
        addErrorMessage(componentID, summary, Message.error(MessageKeys.ERR_REQUIRED));
	}
    
    protected void addErrorMessage(String componentId, Message summary, Message detail) {
        String clientId = getClientId(componentId);
        
        FacesMessage facesMessage = getFacesMessage(summary, detail);
        getFacesContext().addMessage(clientId, facesMessage);
    }

    protected String getClientId(String componentID) {
        UIComponent component = findComponentInRoot(componentID);
		
		// Need some CYA here - it's ugly if this happens w/out a null check.
		// The message will still display as a general message if the clientId 
		// is null.  It simply won't be associated with a specific component.  
		String clientID = null;
		if (null != component) 
			clientID = component.getClientId(getFacesContext());
        return clientID;
    }
    
    /**
     * Add a message that will live longer than the request scope.  This should
     * be used when messages need to be displayed after a redirect.  These are
     * rendered with the custom MessagesRenderer.
     *
     * @param  summary   The summary of the message.
     * @param  detail    The details of the message.
     *
     * @see sailpoint.web.tags.MessagesRenderer
     */
    @SuppressWarnings("unchecked")
    public void addMessageToSession(Message summary, Message detail) {

		FacesMessage fm = getFacesMessage(summary, detail);
		Map session = getSessionScope();
		List<FacesMessage> msgs = (List<FacesMessage>) session.get(SESSION_MESSAGES);
		if (null == msgs) {
			msgs = new ArrayList<FacesMessage>();
			session.put(SESSION_MESSAGES, msgs);
		}
		msgs.add(fm);
	}

    public void addMessageToSession(Message summary) {
        addMessageToSession(summary, null);
    }

    /**
     * Adds a message to the FacesContext. The severity is taken from the type
     * property of the Message parameters.
     *
     * @param summary FacesMessage summary
     * @param detail FacesMessage detail
     */
    public void addMessage(Message summary, Message detail) {
        getFacesContext().addMessage(null, getFacesMessage(summary, detail));
	}

    protected void addMessage(Message summary) {
        addMessage(summary, null);
    }

    public void addMessage(GeneralException e) {
        addMessage(e.getMessageInstance());
    }

    public void addMessage(Throwable t) {
        if (t instanceof GeneralException) 
            addMessage((GeneralException)t);
        else {
            // punt and display the raw exception text
            addMessage(new Message(t.toString()));
        }
    }

    /**
     * Creates a FacesMessage instance with the given detail and summary message.
     * The FacesMessage severity is taken from the type property of
     * the detail or summary parameter, with precedence given to the summary. 
     *
     * @param summary FacesMessage summary
     * @param detail FacesMessage detail
     * @return FacesMessage instance width the given summary and detail messages.
     */
    protected FacesMessage getFacesMessage(Message summary, Message detail) {
        return WebUtil.getFacesMessage(summary, detail, getLocale(), getUserTimeZone());
	}

    /**
     * @param key Msg key, or plain text string.
     * @param args Any arguments which need to be formatted with the message
     * @return Localized, formatted string
     */
    public String getMessage(String key, Object... args) {
        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
	}

    /**
     * Localizes a given message for the user's locale and timezone.
     *
     * @param msg Message to localize
     * @return Localized message for the current user. Null if  msg was null.
     */
    public String getMessage(Message msg){
        if (msg==null)
            return null;
        return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
    }

    /**
	 * @return The Locale from the FacesContext
	 */
	public Locale getLocale(){
        FacesContext o = FacesContext.getCurrentInstance();
        Locale locale = null;
        if (o != null && o.getViewRoot() != null) {
            locale = o.getViewRoot().getLocale(); 
        }
        return locale;
	}

    /**
     * Get the language tag for the current locale.
     * 
     * @return Lower case representation of locale with dashes as separators.
     */
    public String getLocaleTag() {
        
        String tag = null;
        Locale locale = getLocale();
        if (locale != null) {
            //Would love to use Locale.toLanguageTag here, but doesnt exist in Java 6
            tag = locale.toString();
            if (tag != null) {
                tag = tag.replaceAll("_", "-").toLowerCase();
            }
        }        
        return tag;
    }

    /**
	 * @return The SimpleDateFormat associated with the Locale from the FacesContext
	 */
	public SimpleDateFormat getDateFormat(){
		
		// this was lifted from the Tomahawk HtmlCalendarRenderer's 
		// CalendarDateTimeConverter.createStandardDateFormat() method,
		// which has the unfortunate visibility of private, hence the copy 
        DateFormat dateFormat;
        dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, getLocale());

        if(dateFormat instanceof SimpleDateFormat)
            return (SimpleDateFormat) dateFormat;
        else
            return new SimpleDateFormat("dd.MM.yyyy", getLocale());
	}

    /**
	 * @return The pattern of the date format associated with the Locale
	 */
	public String getDateFormatPattern(){        
        return getDateFormat().toPattern();
	}

    /**
     * Return the timezone of the server so we can display any of our
     * GMT times in the server timezone.
     *
     * @return Server timezone
     */
    public TimeZone getServerTimeZone() {
        return TimeZone.getDefault();
    }

    /**
     * @return User's timezone
     */
    public TimeZone getUserTimeZone(){
        return WebUtil.getTimeZone(getServerTimeZone()); 
    }

    /**
     * Returns user's timezone id. Used for populating the timeZone property
     * on t:inputDate tags.
     * @return
     */
    public String getUserTimeZoneId(){
        TimeZone tz = getUserTimeZone();

        if (tz == null)
            tz = TimeZone.getDefault();

        return tz.getID();
    }

    /**
     * Store's user's timezone in the http session.
     *
     * @param  tz  The time zone
     */
    @SuppressWarnings("unchecked")
    public void setUserTimeZone(TimeZone tz){
    	getSessionScope().put(SESSION_TIMEZONE, tz);
    }

    /**
     * Store's user's timezone in the http session.
	 * @param timeZoneId The ID of the time zone
     */
    public void setUserTimeZone(String timeZoneId) {
        setUserTimeZone(calculateTimeZone(timeZoneId));
    }
    
    public void initializeUserTimeZone( ActionEvent e ) {
        setUserTimeZone( initialTimeZoneId );
    }

    /**
     * This is used when we come in through SSO or otherwise bypass the login screen.  First we
     * redirect to setTimeZone.jsf in PageAuthenticationFilter, and then from setTimeZone call
     * this to set the TimeZone session attribute and forward on to the originally requested page.
     *
     * @return
     * @throws GeneralException
     * @throws IOException
     */
    public String initDetectedUserTimeZone() throws GeneralException, IOException {
        setUserTimeZone( initialTimeZoneId );

        String url = getRequestOrSessionParameter(PageAuthenticationService.ATT_TZ_REDIRECT);
        if (WebUtil.isValidRedirectUrl(url)) {
            // Forward to the requested page if the login is the result of
            // trying to access a particular page when not logged in.
            redirect(url);
        } else {
            // If we have an invalid redirect url, redirect to login.jsf
            // IIQETN-6398
            if (log.isWarnEnabled()) {
                log.warn("Invalid redirect URL: " + url);
            }
            try {
                String path = getRequestContextPath();
                HttpServletRequest request = (HttpServletRequest) getFacesContext().getExternalContext().getRequest();
                URL loginUrl = new URL(request.getScheme(), request.getServerName(), request.getServerPort(), path + SAMLSSOAuthenticator.DESKTOP_LOGIN_URL + "?prompt=true");
                if (log.isWarnEnabled()) {
                    log.warn("Redirecting to: " + loginUrl.toExternalForm());
                }
                redirect(loginUrl.toExternalForm());
            } catch (MalformedURLException e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return "";
    }

    /**
     * Calculate a TimeZone based on the passed time zone ID. IF not set, or is invalid, we will default
     * to server time zone.
     * @param timeZoneId ID of the time zone
     * @return TimeZone object
     */
    static TimeZone calculateTimeZone(String timeZoneId) {

        if (!Util.isNothing(timeZoneId)) {
            try {
                // Important! This will fallback to GMT if the ID is bogus.
                return TimeZone.getTimeZone(timeZoneId);
            } catch (Exception e) {
                // Warn about this exception but do not throw, let it through. We will create our bogus
                // fake time zone below instead.
                if (log.isWarnEnabled()) {
                    log.warn("Unable to get time for " + timeZoneId, e);
                }
            }
        }

        
        // We used to make a fake user time zone here, but I don't see the point. Momentjs is doing a good faith guess on the frontend
        // and if nothing comes back from that then it should be quite exceptional. So lets just default to GMT after logging the warning.
        // This matches what TimeZone.getTimeZone() does for unrecognized IDs.
        // It is quite unlikely that this will actually happen.  We never expect to get here unless there is a bug in the browser-side code.
        if (log.isWarnEnabled()) {
            log.warn("No time zone id was available from the browser. Defaulting to GMT.");
        }

        return TimeZone.getTimeZone("GMT");
    }

    ////////////////////////////////////////////////////////////////////////////
	//
	// StateHolder interface.  Implemented so classes that extend PageCodeBase
	// can be saved using t:saveState and have the transient objects managed
	// correctly.
	//
	////////////////////////////////////////////////////////////////////////////

	boolean isTransient;

	public boolean isTransient() {
		return this.isTransient;
	}
	public void setTransient(boolean newTransientValue) {
		this.isTransient = newTransientValue;
	}

	/**
	 * When restored, reinitialize the transient fields.
	 */
	public void restoreState(FacesContext context, Object state) {
		try {
			// Note that this only copies properties that have getters and
			// setters.
			BeanUtils.copyProperties(this, state);
			initFacesVariables();
		}
		catch (Exception e) {
			// Wrap in a RuntimeException since restoreState() doesn't throw
			// anything but NPE's.
			throw new RuntimeException(e);
		}
	}

	/**
	 * When saved, this clears the transient fields and returns this bean.
	 */
	public Object saveState(FacesContext context) {
		clearTransientFields();
		return this;
	}
}  // class PageCodeBase
