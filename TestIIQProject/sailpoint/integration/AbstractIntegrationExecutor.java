/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Stub implementation of IntegrationExecutor.  Stock and custom executors
 * must always extend this rather than implement IntegrationExecutor directly
 * so that we can add new methods to the IntegrationExecutor interface 
 * without breaking custom executors.
 *
 * Author: Jeff
 *
 * As of 5.2 this class is deprecated and should no longer be used
 * in new code.  It will be retained for backward compatibility with
 * with custom integration executors, but all stock integrations
 * should now use the Connector/Application model.
 * 
 * Sigh, because Java does not have multiple inheritance we've got
 * a problem trying to define classes that both extend 
 * AbstractIntegrationExecutor and RemoteIntegration which is the client-side
 * of the REST interface to a remote integration.  To get around this, 
 * this class will serve both to provide stub methods and also serve
 * as a proxy class to an inner implementation of IntegrationInterface.
 * 
 */

package sailpoint.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.unstructured.TargetCollector;

/**
 * Stub implementation of IntegrationExecutor. Stock and custom executors
 * should always extend this rather than implement IntegrationExecutor directly.
 */
public class AbstractIntegrationExecutor implements IntegrationExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields/Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(AbstractIntegrationExecutor.class);
    private static String authCredentials[] = { "username", "password",
        "authentication", "locale", "timeZone", "keyPass", "keystorePass",
        "basicAuthType", "httpUserName", "httpUserPass" };
    /**
     * Configuration argument that specifies the password.
     */
    private static final String ARG_PASSWORD = "password";

    protected static final String RETRYABLE_ERRORS = "retryableErrors";

    // Proxy constants
    protected static final String HTTP_PROXYHOST = "http.proxyHost";
    protected static final String HTTP_PROXYPORT = "http.proxyPort";
    protected static final String HTTP_PROXYUSER = "http.proxyUser";
    protected static final String HTTP_PROXYPASSWORD = "http.proxyPassword";
    protected static final String HTTPS_PROXYHOST = "https.proxyHost";
    protected static final String HTTPS_PROXYPORT = "https.proxyPort";
    protected static final String HTTPS_PROXYUSER = "https.proxyUser";
    protected static final String HTTPS_PROXYPASSWORD = "https.proxyPassword";

    /**
     * Optional proxy executor, typically RemoteIntegration.
     */
    protected IntegrationInterface _proxy;

    /**
     * Cached context.
     * This is normally provided in the configure() method.
     */
    protected SailPointContext _context;

    /**
     * Cached configuration.
     */
    protected IntegrationConfig _config;

    public AbstractIntegrationExecutor() {
    }

    public AbstractIntegrationExecutor(IntegrationInterface proxy) {
        setProxy(proxy);
    }

    public void setProxy(IntegrationInterface proxy) {
        _proxy = proxy;
    }

    public SailPointContext getContext() throws GeneralException {
        if (_context == null) {
            // should we allow this?
            // yes, IntegrationConsole creates these during the "use"
            // command and caches it, but creates a new context for
            // every subsequent command
            _context = SailPointFactory.getCurrentContext();
        }
        return _context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationExecutor
    //
    //////////////////////////////////////////////////////////////////////

    public void configure(SailPointContext context, 
                          IntegrationConfig config)
        throws Exception {

        // Decrypting password here
        log.trace("Decrypting password from IntegrationConfig if encrypted.");
        IntegrationConfig clonedConfig = (IntegrationConfig) XMLObjectFactory
                .getInstance()
                .clone(config, SailPointFactory.getCurrentContext());
        String password = clonedConfig.getString(ARG_PASSWORD);
        password = context.decrypt(password);
        clonedConfig.setAttribute(ARG_PASSWORD, password);

        // save these for later
        _context = context;
        _config = config;

        // forward to the IntegrationInterface configure method passing
        // just the arg map
        configure(clonedConfig.getAttributes());
    }

    /**
     * Return the underlying connector if we are a connector wrapper.
     */
    public Connector getConnector() {
        return null;
    }

    /**
     * Return the underlying target collector if we are a target collector wrapper.
     */
    public TargetCollector getTargetCollector() {
        return null;
    }

    /**
     * Older provisioning interface that provides an Identity.
     * This is the method called by PlanEvalator prior to 5.2.
     * Forwards to the IntegrationInterface.provision method below.
     */
    public RequestResult provision(Identity identity,
                                   sailpoint.integration.ProvisioningPlan plan) 
        throws Exception {

        // forward to the core method ignoring Identity
        // note that the old interface takes nativeIdentity as the first arg
        // which we don't need any more now that it the ProvisioningPlan
        // has it
        String nativeIdentity = plan.getIdentity();
        if (nativeIdentity == null && identity != null)
            nativeIdentity = identity.getName();

        return provision(nativeIdentity, plan);
    }

    /**
     * Hook for adding things to the RoleDefinition before passing it down.
     */
    public void finishRoleDefinition(Bundle src, 
                                     RoleDefinition dest)
        throws Exception {
    }

    /**
     * The primary provisioning method.
     */
    public ProvisioningResult provision(ProvisioningPlan plan)
        throws Exception {

        ProvisioningResult result = null;

        // this must be passed 
        Identity ident = plan.getIdentity();
        if (ident == null)
            throw new GeneralException("Missing identity");

        // convert the IIQ plan model to the generic plan model
        sailpoint.integration.ProvisioningPlan ipp = convertPlan(plan);

        // magic happens, catch exceptions so we can distinguish
        // between failures in the IntegrationExecutor call,
        //and failures to process the result

        // !! if ARG_SIMULATE is on should we even be calling this?
        // Currently Provisioner.impactAnalysis will force the integratin
        // list to null so we'll never get here, but it might be
        // interesting to have the integratins involved in the
        // simulation?
                
        try {
            // forward to the older interface
            RequestResult reqResult = provision(ident, ipp);
            if ( reqResult != null ) {
                // convert the result model
                upgradeCommittedStatus(reqResult);
                result = new ProvisioningResult(reqResult);
            }
            // propagate changes made to the converted plan back to the 
            unconvertPlan(ipp, plan);
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            
            // make this look like a failure below
            String msg = t.toString();
            if (result == null)
                result = new ProvisioningResult();
            
            result.fail();
            result.addError(msg);
        }

        return result;
    }

    /**
     * Upgrade STATUS_SUCCESS to STATUS_COMMITTED if configured.
     * This is a convenience for older executors that really do commit
     * things but we didn't have a way to say that before 6.4.  Ideally
     * the executor code would be updated but until then you can add
     * this and continue using old executors.
     */
    private void upgradeCommittedStatus(RequestResult result) {
        if (result != null) {
            // if nothing came back, assume success (queued)
            if (result.getStatus() == null)
                result.setStatus(RequestResult.STATUS_SUCCESS);

            // convert success to committed when configured
            if (_config.getBoolean(IntegrationConfig.ATT_STATUS_SUCCESS_IS_COMMITTED) &&
                RequestResult.STATUS_SUCCESS.equals(result.getStatus())) {
                result.setStatus(RequestResult.STATUS_COMMITTED);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Plan Conversion
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Convert the sailpoint.object.ProvisioningPlan that was compiled
     * into a sailpoint.integration.ProvisioningPlan to send to 
     * the IntegrationExecutor.
     */
    private sailpoint.integration.ProvisioningPlan 
    convertPlan(ProvisioningPlan src) {

        // ugh, class name conflicts are a pain I wish
        // we'd given this a unique name...
        sailpoint.integration.ProvisioningPlan dest = null;

        // the map transformation does the model magic
        Map map = src.toMap();
        dest = new sailpoint.integration.ProvisioningPlan(map);

        return dest;
    }

    /**
     * Assimilate results from the sailpoint.integration.ProvisioningPlan
     * we gave to the old integration interface back into the original
     * sailpoint.object.ProvisioningPlan.  
     * 
     * Here in this method we will assimilate the AccountRequest level 
     * results from the integration.Provisioning plan onto the object.ProvisioningPlan.
     * This is necessary to allow grainular control over things like retry
     * and failing only parts of a plan.
     *
     * NOTE: The ability for executors to modify their plans was added in 5.1
     * and we returned a completely new plan rather than modifying the
     * existing one. In 5.2 now that integrations have direct access to the
     * original sailpoint.object.ProvisioningPlan this is no longer necessary.
     * 
     * The ability to use the sailpoint.object.ProvisioningPlan requires 
     * that it has access to the IdentityIQ jar, which for cases where we are running
     * remotely won't work. Like the OIM andr TDI executor.
     * 
     * Since I do not think anyone took advantage of this feature between
     * 5.1 and 5.2 I'm not going try to copy results back.  But
     * if we have to there are two levels:
     *
     *     - copying attribute map changes at every level
     *     - detecting request objects that were deleted, added, or modified
     * 
     * The first one is the most likely since it was the notification for the 
     * original bug. Letting executors make structural modifications
     * to the source plans also became possible but was not promoted
     * and we do not believe it was ever used.
     *
     * Punting on attribute map changes and detecting objects were deleted,
     * added or modified.
     *
     */
    @SuppressWarnings("unchecked")
    private void unconvertPlan(sailpoint.integration.ProvisioningPlan src,
                               ProvisioningPlan dest) {
        
        if ( src != null && dest != null ) {
            List<sailpoint.integration.ProvisioningPlan.AccountRequest> iacctReqs = (List<sailpoint.integration.ProvisioningPlan.AccountRequest>)src.getAccountRequests();
            if ( iacctReqs != null ) {
                for ( sailpoint.integration.ProvisioningPlan.AccountRequest ireq : iacctReqs ) {
                    if ( ireq == null ) 
                        continue;                    
                    RequestResult ireqResult = ireq.getResult();
                    if ( ireqResult != null ) {
                        Map irecMap = ireq.toMap();
                        if ( !Util.isEmpty(irecMap) ) {
                            AccountRequest match = dest.getMatchingAccountRequest(new AccountRequest(irecMap));
                            if ( match != null )
                                match.setResult(new ProvisioningResult(ireqResult));
                            else
                                log.error("Unable to find matching account request, unable to add result.");
                        }
                    }
                }
            }
        }
     }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInteface
    //
    //////////////////////////////////////////////////////////////////////

    public void configure(Map args) throws Exception {
        // don't need to throw here
        if (_proxy != null)
            _proxy.configure(args);
    }

    public String ping() throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.ping();
    }

    public List listRoles() throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.listRoles();
    }

    public RequestResult addRole(RoleDefinition def) throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.addRole(def);
    }

    public RequestResult deleteRole(String roleName) throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.deleteRole(roleName);
    }

    public RequestResult provision(String identity, sailpoint.integration.ProvisioningPlan plan)
        throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.provision(identity, plan);
    }

    public RequestResult getRequestStatus(String requestID) throws Exception {
        if (_proxy == null)
            throw new UnsupportedOperationException();
        return _proxy.getRequestStatus(requestID);
    }

    /**
     * This was added to IntegrationExecutor but is not in 
     * IntegrationInterface so we don't pass it to the proxy.  Call
     * the old method and convert the result.
     */
    public ProvisioningResult checkStatus(String requestId) throws Exception {

        ProvisioningResult newResult = null;
        RequestResult oldResult = null;
        try {
            oldResult = getRequestStatus(requestId);
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error("Exception while getting ticket status " + ex.getMessage()
                         +" Retrying the status check", ex);
            }
            //On any exception SIM should checkstatus of ticket on next interval
            oldResult = new RequestResult();
            oldResult.setStatus(RequestResult.STATUS_IN_PROCESS);
            oldResult.setRequestID(requestId);
        }
        if (oldResult != null) {
            // convert the status if configured
            upgradeCommittedStatus(oldResult);
            newResult = new ProvisioningResult(oldResult);
        }
        return newResult;
    }
    
    /**
     * Checks whether exception caught is configured as retryable exception
     * @param ex Exception
     * @param retryErrorList List of retryable exception strings
     */
    public boolean shouldRetry(Exception ex, List<String> retryErrorList) {
        boolean isRetry = false;
        if (!Util.isEmpty(retryErrorList)) {
            for (String errString : retryErrorList) {
                if (Util.isNotNullOrEmpty(errString) 
                        && ex.getMessage().toLowerCase().contains(errString.toLowerCase())) {
                    isRetry = true;
                    break;
                }
            }
        }
        return isRetry;
    }

    /**
     * Utility method that loads any authentication credentials that need to be
     * inherited from the base configuration into the given config map. This
     * lets us pass the credentials to Velocity, regardless of whether they are
     * specific to the given config or global to the integration as a whole.
     * Note that instead of modifying the given map, this returns a copy to
     * prevent modifying the actual IntegrationConfig.
     * 
     * @param configMap
     * @throws Exception
     */
    @SensitiveTraceReturn
    protected Map<String,Object> inheritCredentials(Map<String,Object> configMap)
        throws Exception {
        Map<String,Object> copy = new HashMap<String,Object>(configMap);

        for (int i = 0; i < authCredentials.length; i++) {
            copy.put(authCredentials[i],
                    _context.decrypt(getString(configMap, authCredentials[i])));
        }

        return copy;
    }

    /**
     * Convenience method to pull the value from a map for the given key. If the
     * key cannot be found in the given map, then the base configuration map is
     * searched for a global value associated with the key.
     * 
     * @param map
     *            Map containing configuration data
     * @param key
     *            Key whose value is requested
     * 
     * @return String value associated with the given key; null if not found.
     */
    @SensitiveTraceReturn
    protected String getString(Map<String,Object> map, String key) {
        String value = null;
        if (map != null) {
            Object o = map.get(key);
            if (o != null)
                value = o.toString();
        }

        // if the value is still null, search the base config
        if (null == value) {
            value = _config.getAttributes().getString(key);
        }

        // if the value is STILL null, so be it
        return value;
    }
}
