/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.Notary;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.AuthenticationUtil;
import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.ElectronicSignature;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.service.LoginService;
import sailpoint.service.PageAuthenticationService;
import sailpoint.service.ServiceHandler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.LoginBean;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.UserContext;
import sailpoint.web.util.JavascriptFilter;


/**
 * Base class for all JAX-RS resource classes.  This provides default behavior
 * around data types, authentication, listing data.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON, MediaType.WILDCARD })
public class BaseResource implements UserContext{

    private static final Log log = LogFactory.getLog(BaseResource.class);

    private SailPointContext context;

    /**
     * A ServiceHandler that can be used for the business logic.
     */
    private ServiceHandler handler;

    private List<Capability> capabilities;
    private List<String> dynamicScopes;
    
    private Notary notary;
    
    /**
     * Allows a parent resource to authorize before calling to a
     * sub resource.
     * 
     */
    private boolean preAuth = false;

    public boolean isPreAuth() {
        return preAuth;
    }

    public void setPreAuth(boolean preAuth) {
        this.preAuth = preAuth;
    }

    /**
     * The HttpRequest for this resource.
     */
    @Context protected HttpServletRequest request;

    /**
     * Headers are available for every request - mainly to retrieve the logged
     * in user.
     */
    @Context protected HttpHeaders headers;

    /**
     * UriInfo can be used to extract information about the request - should be
     * rarely used.
     */
    @Context protected UriInfo uriInfo;

    // These query parameters can override or be used in place of BASIC auth.
    // Should just be used for testing since it is not safe to put a password
    // on a URL as a query parameter.
    @QueryParam(AuthenticationUtil.PARAM_AUTHN_USER) protected String authnUsername;
    @QueryParam(AuthenticationUtil.PARAM_AUTHN_PASSWORD) protected String authnPassword;


    /**
     * Default constructor.
     */
    public BaseResource() {
    }
    
    /**
     * Constructor for a subresource - the parameters that would have been
     * injected get copied over from the given parent resource.
     * 
     * @param  parent  The BaseResource that is a parent to this resource.
     */
    public BaseResource(BaseResource parent) {
        this.context = parent.getContext();
        this.handler = parent.handler;
        this.request = parent.request;
        this.headers = parent.headers;
        this.uriInfo = parent.uriInfo;
        this.authnUsername = parent.authnUsername;
        this.authnPassword = parent.authnPassword;
        this.preAuth = parent.isPreAuth();
    }
    
    
    /**
     * Return a ServiceHandler that uses the authenticated identity.
     */
    protected ServiceHandler getHandler() throws GeneralException{
        // Should all requests require an authenticated identity?
        if (null == this.handler) {
            this.handler = new ServiceHandler(getContext());
        }
        return this.handler;
    }
    
    /**
     * if no handler previously exists, Returns a ServiceHandler that uses the context passed in
     * else return the previous handler
     * @param ctx The Context to use within the Service Handler
     * @return ServiceHandler that uses the authenticated identity
     */
    protected ServiceHandler getHandler(SailPointContext ctx) {
        if (null == this.handler) {
            this.handler = new ServiceHandler(ctx);
        }
        return this.handler;
    }
    
    /**
     * Need access to HttpServletRequest in sub-classes to get request params
     */
    public HttpServletRequest getRequest() {
        return this.request;
    }
    
    /**
     * Return a two-element array with the username and password of the
     * authenticated user.  This information is either pulled from the BASIC
     * authorization information in the header or from authnUsername and
     * authnPassword request parameters.
     * 
     * @throws  WebApplicationException  If the authentication information is
     *                                   not in the request.
     */
    protected String[] getCredentials() {

        String[] creds = null;
        String authHeader = null;
        List<String> authzHeaders =
            headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
        if ((null != authzHeaders) && !authzHeaders.isEmpty()) {
            authHeader = authzHeaders.get(0);
        }

        try {
            creds = AuthenticationUtil.getCredentials(authHeader, authnUsername, authnPassword);
        }
        catch (Exception e) {
            throw new WebApplicationException(e);
        }

        return creds;
    }
    
    /**
     * In the past we supported comma-separated strings to represent
     * multi-valued query parameters.  JAX-RS does not do this sort of
     * conversion, so this is a helper to turn a query parameter that used
     * the CSV notation into the correct value.  Note that if the request
     * uses the standard mutli-valued technique (ie - the parameter appearing
     * multiple times in the query string) this does nothing.
     * 
     * @param  values  A query parameter value that may have a CSV.
     * 
     * @return A List with CSV multivalues properly expanded.
     */
    protected static List<String> fixCSV(List<String> values) {
        
        if ((values != null) && (1 == values.size()) &&
            (values.get(0).indexOf(',') > -1)) {
            values = Util.csvToList(values.get(0));
        }
        return values;
    }

    /**
     * Throw a WebApplicationException with the given message as the reason.
     */
    protected void throwWebApplicationException(String message)
        throws WebApplicationException {

        // Is there a better way to do this?  Maybe create a Response?
        throw new WebApplicationException(new Throwable(message));
    }

    @Override
    public SailPointContext getContext() {
        if ( context == null ){
            try {
                context = SailPointFactory.getCurrentContext();
                context.setUserName(getLoggedInUserName());
            }
            catch (Exception e) {
                log.error(e);
                throw new RuntimeException(e);
            }
        }

        return context;
    }

    @Override
    public String getLoggedInUserName() throws GeneralException {
    	String loggedInUser = getSessionUserName();

    	if (loggedInUser == null) {
    	    loggedInUser = BaseOAuthRestFilter.getIdentityName(getRequest());
    	    if (null == loggedInUser) {
    	        throw new GeneralException("Expected an identity in the session or request.");
    	    }
        }

        return loggedInUser;
    }
    
    private String getSessionUserName(){
        HttpSession httpSession = request.getSession(true);
        Object o = httpSession.getAttribute(PageAuthenticationService.ATT_PRINCIPAL);
        return o!=null ? (String)o : null;
    }

    @Override
    public boolean isMobileLogin() {
        HttpSession httpSession = request.getSession(true);
        return Util.otob(httpSession.getAttribute(PageAuthenticationService.ATT_MOBILE_LOGIN));
    }

    public boolean isSsoAuthenticated() {
        HttpSession httpSession = request.getSession(true);
        return Util.otob(httpSession.getAttribute(PageAuthenticationService.ATT_SSO_AUTH));
    }

    @Override
    public Identity getLoggedInUser() throws GeneralException {
        Identity identity = null;
        String identityName = getLoggedInUserName();
        if (null != identityName) {
            identity = getContext().getObjectByName(Identity.class, identityName);
        }
        return identity;
    }

    /**
    * Gets user's effective capabilities. Note that this won't
    * be useful for web service users who are not logged in.
    * @return
    */
    @Override
    public List<Capability> getLoggedInUserCapabilities(){
       if (capabilities == null){

           // first check the HttpSession
           HttpSession httpSession = request.getSession(true);
           capabilities =  (List<Capability>)httpSession.getAttribute(PageAuthenticationFilter.ATT_CAPABILITIES);

           if (capabilities == null) {
        	   try {
        		   Identity loggedInUser = getLoggedInUser();
        		   if (loggedInUser != null) {
        			   capabilities = loggedInUser.getCapabilityManager().getEffectiveCapabilities();
        		   }
        	   } catch (Exception ex) {
        		   throw new RuntimeException(ex);
        	   }
           }
           
           if (capabilities == null)
               capabilities = Collections.EMPTY_LIST;
       }

       return capabilities;
    }    

    /**
    * Gets user's effective rights. Note that this won't
    * be useful for web service users who are not logged in.
    * @return
    */
    @Override
    public Collection<String> getLoggedInUserRights(){
       HttpSession httpSession = request.getSession(true);
       Object o = httpSession.getAttribute(PageAuthenticationFilter.ATT_RIGHTS);
       if (o instanceof Collection)
           return (Collection<String>) o;
       
	   try {
		   Identity loggedInUser = getLoggedInUser();
		   if (loggedInUser != null) {
			   return loggedInUser.getCapabilityManager().getEffectiveFlattenedRights();
		   }
	   } catch (Exception ex) {
		   throw new RuntimeException(ex);
	   }
       

       return Collections.EMPTY_LIST;
    }


    @Override
    public Locale getLocale(){
        Locale locale = null;
        if (request != null)
            locale = request.getLocale();

        return locale == null ? Locale.getDefault() : locale;
    }


    @Override
    public TimeZone getUserTimeZone(){
        TimeZone tz = null;
        if (request != null && request.getSession() != null){
            tz = (TimeZone)request.getSession().getAttribute("timeZone");
        }

        return tz == null ? TimeZone.getDefault() : tz;
    }

    public String localize(String key, Object... parameters){
        if (key == null)
            return null;

        Message msg = Message.info(key, parameters);
        return msg.getLocalizedMessage(getLocale(), getUserTimeZone());
    }

    public HttpSession getSession(){
        return request != null ? request.getSession() : null;
    }

    /**
     * Make all of the keys of the given list of Maps JSON-safe.  That is,
     * trade out all dots (periods) with dashes.
     */
    protected static List<Map<String,Object>> makeJsonSafeKeys(List<Map<String,Object>> rows) {

        if (null != rows) {
            Map<String,String> keyFixes = new HashMap<String,String>();

            for (Map<String,Object> row : rows) {

                // First time through, look for any unsafe keys.
                if (keyFixes.isEmpty()) {
                    for (Map.Entry<String,Object> entry : row.entrySet()) {
                        String key = entry.getKey();
                        if(key.contains(".")) {
                            keyFixes.put(key, Util.getJsonSafeKey(key));
                        }
                    }

                    // We didn't find anything bad, bail out.
                    if (keyFixes.isEmpty()) {
                        break;
                    }
                }

                // If we got here there is stuff to fix, so do it.
                for (Map.Entry<String,String> entry : keyFixes.entrySet()) {
                    Object val = row.remove(entry.getKey());
                    row.put(entry.getValue(), val);
                }
            }
        }

        return rows;
    }

    /**
     * Convert the given iterator of search results into a ListResult of maps
     * that are keyed with the given properties.
     * 
     * @param  iterator  The Iterator to convert to a ListResult.
     * @param  props     The names of the properties for each object within each
     *                   element of the iterator.
     */
    protected static ListResult createListResult(Iterator<Object[]> iterator,
                                                 String... props) {
        List<Map<String,Object>> results =
            makeJsonSafeKeys(Util.iteratorToMaps(iterator, props));
        return new ListResult(results, results.size());
    }

    /**
     * Convert the given iterator of search results into a ListResult of maps
     * that are keyed with the given properties.
     * 
     * @param  iterator  The Iterator to convert to a ListResult.
     * @param  props     The names of the properties for each object within each
     *                   element of the iterator.
     */
    protected static ListResult createListResult(Iterator<Object[]> iterator,
                                                 int total, String... props) {
        
        List<Map<String,Object>> results =
            makeJsonSafeKeys(Util.iteratorToMaps(iterator, props));
        return new ListResult(results, total);
    }

    /**
     * Utility function which converts datetime (longs) strings into dates.
     * @param dateStr
     * @param isMin If true this is considered to be the start of a date range
     * @return
     */
    protected static Date parseDateRange(String dateStr, boolean isMin){

        long time = Util.atol(dateStr);

        // handle invalid dates
        if (time==0)
            return null;

        Date d = new Date(time);
        return isMin ? Util.getBeginningOfDay(d) : Util.getEndOfDay(d);
    }

/**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    @Override
    public boolean isObjectInUserScope(SailPointObject object) throws GeneralException{
        return isObjectInUserScope(object.getId(), getClass(object));
    }

    /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the object.
     *
     * @param id ID of the object
     * @param clazz Class of the object
     * @return True if the object is in a scope controlled by the user
     * @throws GeneralException
     */
    @Override
    public boolean isObjectInUserScope(String id, Class clazz) throws GeneralException{

        // this is a new object, no auth needed
        if (id==null || "".equals(id))
            return true;

        QueryOptions scopingOptions = new QueryOptions();
        scopingOptions.setScopeResults(true);
        scopingOptions.add(Filter.eq("id", id));


        // Getting the class straight off the object may return a hibernate
        // proxy.  For now, use the Hibernate utility class to strip the class
        // out of its proxy.
        int count = getContext().countObjects(clazz, scopingOptions);
        return (count > 0);
    }

    /**
     * Returns true if scoping is enabled in the system configuration
     * @return true if scoping is enabled, true if the option doesn't exist, false otherwise.
     * @throws GeneralException
     */
    @Override
    public boolean isScopingEnabled() throws GeneralException {
        Configuration config = getContext().getObjectByName(Configuration.class,
                Configuration.OBJ_NAME);        
        return config.getBoolean(Configuration.SCOPING_ENABLED, true);
    }



    /**
     * Extremely rare instanceof Hibernate leaking into the UI layer
     * so we don't have to put another method in SailPointContext
     * or make the UI dependent HibernatePersistenceManager.
     *
     * This has to be used whenever you want to get a Class to
     * pass into a SailPointContext method using the getClass
     * method of a SailPointObject instance.  For example:
     *
     *    con.getObject(something.getClass(), ...
     *
     * Because Hibernate uses CGLib to wrap proxy classes around
     * lazy loaded objects what you get back from Object.getClass is
     * not necessarily a "real" class and Hibernate isn't able to
     * deal with them.  Hibernate thoughtfully provides a utility
     * to unwrap the proxy class that they're apparently too lazy
     * to use themselves.
     */
    private Class getClass(SailPointObject src) {
        return org.hibernate.Hibernate.getClass(src);
    }
    
    /**
     * Determines whether the current user is authorized for all passed authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @return True if authorization is successful, false otherwise.
     * @throws GeneralException If an error occurs during authorization.
     */
    protected boolean isAuthorized(Authorizer... authorizers) throws GeneralException {
    	return AuthorizationUtility.isAuthorized(this, authorizers);
    }
    
    /**
     * Authorizes the current user with the specified authorizers.
     * 
     * @param authorizers The authorizers to check.
     * @throws UnauthorizedAccessException If authorization fails.
     * @throws GeneralException If an error occurs during authorization.
     */
    protected void authorize(Authorizer... authorizers) throws GeneralException {    	
    	if(!isPreAuth()) {
    	    AuthorizationUtility.authorize(this, authorizers);
    	}
    }

    /**
     * Decode a URI component that was encoded before sending
     * @param uriComponent Component to decode
     * @return Decoded component
     */
    public String decodeRestUriComponent(String uriComponent) {
        return decodeRestUriComponent(uriComponent, true);
    }

    /**
     * Decode a URI component that was encoded before sending
     * @param uriComponent Component to decode
     * @param decodeTwice If true, decode the component twice                    
     * @return Decoded component
     */
    public String decodeRestUriComponent(String uriComponent, boolean decodeTwice) {
        try {
            uriComponent = URLDecoder.decode(uriComponent, JavascriptFilter.DEFAULT_ENCODING);
            if (decodeTwice) {
                // we might triple encode when using RestJsonStore.appendPathParam, so decode again just in case...
                uriComponent = URLDecoder.decode(uriComponent, JavascriptFilter.DEFAULT_ENCODING);
            }
        }
        catch (UnsupportedEncodingException uee) {
            log.warn("Exception decoding URI: " + uee.getMessage());
        }
        return uriComponent;
    }
    
    /**
     * Get a Notary for signing
     * 
     * @return Notary object
     */
    protected Notary getNotary() {
        if(this.notary == null) {
            this.notary = new Notary(getContext(), getLocale());
        }
        return this.notary;
    }

    /**
     * Get a Signature from the input map, checking the session for account
     * @param inputs Input map
     * @return Signature
     */
    protected ElectronicSignature getSignature(Map<String, Object> inputs) {
        String inputPassword = (inputs == null) ? null : (String)inputs.get(ElectronicSignature.INPUT_SIGNATURE_PASSWORD);
        String inputAccountId = (inputs == null) ? null : (String)inputs.get(ElectronicSignature.INPUT_SIGNATURE_ACCOUNT);
        return new ElectronicSignature(getSignatureAccountId(inputAccountId), inputPassword);
    }

    /**
     * Get the account to use for electronic signing from the session,
     * or from the given request account id if not present on the session.
     * @param inputAcccountId Account ID sent in the request
     * @return Account ID to use for electronic signature
     */
    protected String getSignatureAccountId(String inputAcccountId) {
        String accountId = inputAcccountId;
        HttpSession session = getSession();
        if (session != null) {
            String sessionAccountId = (String)session.getAttribute(LoginBean.ATT_ORIGINAL_NATIVE_ID);
            if (Util.isNullOrEmpty(sessionAccountId)) {
                sessionAccountId =  (String)session.getAttribute(LoginBean.ORIGINAL_AUTH_ID);
                if (Util.isNullOrEmpty(sessionAccountId)) {
                   sessionAccountId = (String)session.getAttribute(LoginBean.ATT_ORIGINAL_ACCOUNT_ID);
                }
            }
            if (!Util.isNullOrEmpty(sessionAccountId)) {
                accountId = sessionAccountId;
            }
        }
        
        return accountId;
    }

    /**
     * Save the input signature account ID in the session if it has been authenticated
     * for use in future signatures
     * @param inputAccountId Account ID to save
     */
    protected void saveSignatureAccountId(String inputAccountId) {
        HttpSession session = getSession();
        if (session != null && !Util.isNullOrEmpty(inputAccountId)) {
            String existingAuthId = (String)session.getAttribute(LoginBean.ORIGINAL_AUTH_ID);
            if (Util.isNullOrEmpty(existingAuthId)) {
                session.setAttribute(LoginBean.ORIGINAL_AUTH_ID, inputAccountId);
            }
        }
    }
    
    /**
     * Check the configuration object and see if we should
     * show detailed messages.
     */
    protected boolean reportDetailedLoginErrors() {
        LoginService loginService = new LoginService(getContext());
        return loginService.isDetailedErrorLogging();
    }

    /**
     * Take the interesting information from an Identity to a Map for JSON
     *
     * @param identity Identity to convert
     * @return Map with Identity information
     */
    protected Map<String, Object> convertIdentity(Identity identity) {
        Map<String, Object> identityMap = new HashMap<String, Object>();
        if (identity != null) {
            identityMap.put("id", (identity == null) ? "" : identity.getId());
            identityMap.put("name", (identity == null) ? "" : identity.getName());
            identityMap.put("displayName", (identity == null) ? "" : identity.getDisplayableName());
            if(identity.isWorkgroup()){
                identityMap.put("isWorkgroup", true);
            }
        }
        return identityMap;
    }

    /**
     * Convert query parameters to single value map of CSV values. Exclude any 
     * parameters with null or empty string values.
     * @return Map with query parameters in CSV value format
     */
    protected Map<String, String> getQueryParamMap() {
        MultivaluedMap<String, String> restParameters = uriInfo.getQueryParameters();
        Map<String, String> queryParameters = new HashMap<String, String>();
        for(String key : restParameters.keySet()) {
            String value = (Util.size(restParameters.get(key)) > 1) ? 
                    Util.listToCsv(restParameters.get(key)) : restParameters.getFirst(key);
            if (!Util.isNullOrEmpty(value)) {
                queryParameters.put(key, value);
            }
        }
        
        return queryParameters;
    }

    /**
    * Gets user's dynamic scope names. Note that this won't
    * be useful for web service users who are not logged in.
    * @return
    */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
        if (dynamicScopes == null) {
            HttpSession httpSession = request.getSession(true);

            Object o = httpSession.getAttribute(SessionAttributes.ATT_DYNAMIC_SCOPES.value());
            if (o instanceof List) {
                dynamicScopes = (List<String>) o;
            }
            if (dynamicScopes == null) {
                DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
                dynamicScopes = matcher.getMatches(getLoggedInUser());
            }
        }
        return dynamicScopes;
    }


    /**
     * Get the URI string including the servlet path for the current resource.
     * IMPORTANT: This will return different results based on where it is called from.
     * For example, if called from a method with a @Path annotation, it will return the URI
     * to the @Path. If called from a constructor on a sub-resource, it will return the URI
     * to that sub-resource
     *
     * DOUBLE IMPORTANT: This will return an empty string if called from the constructor of a top-level
     * resource because the UriInfo is not initialized yet.
     * @return Relative URI path string including REST servlet parts to the current matched resource.
     * @throws GeneralException
     */
    protected String getMatchedUri() throws GeneralException {
        // This returns a list of URIs for the matched resources along the chain to get to this resource.
        // They are returned in order of specificity, so we want to get the first.
        if (this.uriInfo == null || Util.size(this.uriInfo.getMatchedURIs()) < 1) {
            return "";
        };

        String uri = this.request.getServletPath() + "/" + this.uriInfo.getMatchedURIs().get(0);
        // Remove preceding slash if there
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }

        return uri;
    }

}
