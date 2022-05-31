/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class encapsulating logic related to pass-through autentication
 * for the InternalContext.
 * 
 * Author: Kelly (factored out of InternalContext by Jeff)
 *
 * EXCEPTIONS
 * 
 * We started throwing some fairly specific exceptions but there
 * is a school of thought that prefers authentication exceptions
 * to be vague so hackers don't get any extra information.  For
 * example saying "invalid user" and "invalid password" gives the
 * hacker clues.
 *
 * Instead we'll use a mysterious "login failed" error for most
 * things.  Where it makes sense we'll put error messages
 * in the log file.
 * 
 * PASSWORD EXPIRATION
 *
 * Password expiration applies only if pass-through authentication
 * is not used.  After authentication we will compare the
 * last password set date with the passwordExpirationDays parameter
 * from the system configuration.  If the password has expired
 * we throw the ExpiredPassword exception from the authenticate
 * method.  The UI is responsible for catching this exception and 
 * navigating to a password change page.
 *
 * OBSERVATIONS
 *
 * The control flow between this, LoginBean, and IdenityFinder
 * is too complicated.  I noticed this during bug 10155.  We throw
 * ExpiredPasswordException, but then LoginBean has to use IdentityFinder
 * to get back to the Identity when we alredy know what it was.  Some
 * rainy day, look at all of this and simplify, it will probably
 * require not throwing ExpiredPasswordException, but doing the
 * correlation and leaving transient expiration status on the Identity
 * for LoginBean to test. - jsl
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AuthenticationFailureException;
import sailpoint.api.EncodingUtil;
import sailpoint.api.Identitizer;
import sailpoint.api.Lockinator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.SailPointContext;
import sailpoint.connector.AuthenticationFailedException;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.object.ResourceObject;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IdentityLockedException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class Authenticator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log LOG = LogFactory.getLog(Authenticator.class);

    public static final String IIQ = "IdentityIQ";
    
    SailPointContext _context;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Inner Classes
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * ConnectorAuthenticator is an interface used to abstract the two different Connector
     * authenticate methods. The previous code used two Authenticator.authenticate methods which
     * largely were a copy/paste aside from the one call to either call the connector with a password
     * or an options Map.
     */
    private interface ConnectorAuthenticator<T extends Object>{
        public ResourceObject authenticate(Connector conn, String accountId, T value) throws ConnectorException,
            ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException;
    }
    /**
     * Implements the connector.authenticate(password) method
     */
    static class PasswordAuthenticator implements ConnectorAuthenticator<String> {
        @Override
        public ResourceObject authenticate(Connector conn, String accountId, String password) throws ConnectorException,
            ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {

            if (Util.isNullOrEmpty(password)) {
                throw new AuthenticationFailedException("No Password Provided");
            }

            return conn.authenticate(accountId, password);
        }
    }
    /**
     * Implements the connector.authenticate(options) method
     */
    static class OptionsAuthenticator implements ConnectorAuthenticator<Map<String, Object>> {
        @Override
        public ResourceObject authenticate(Connector conn, String accountId, Map<String, Object> options) throws ConnectorException,
        ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {
            //TODO: Provide a way to validate options. Would need to be connector specific -rap
            return conn.authenticate(accountId, options);
        }
    }
    
    /**
     * Response object containing the PassThroughResponse
     */
    static class PassThroughResponse {
        ResourceObject account = null;
        Identity identity = null;
        GeneralException appError = null;
        
        PassThroughResponse() { }
        
        PassThroughResponse(ResourceObject account, Identity identity, GeneralException appError) {
            this.account = account;
            this.identity = identity;
            this.appError = appError;
        }
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Constuctor
    //
    //////////////////////////////////////////////////////////////////////

    public Authenticator(SailPointContext con) {
        _context = con;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Authentication
    //
    //////////////////////////////////////////////////////////////////////

    
    /**
     * Attempt pass-through authentication to the application configured as the
     * authentication source.  Fall back to authenticating against the
     * <code>Identity</code> with the given accountId.  If authentication against
     * the pass-through application succeeds but cannot be correlated to a
     * <code>Identity</code>, attempt to create a new <code>Identity</code>.
     * <p>
     * Note: This method commits the current transaction.
     * 
     * @param  accountId  The unique identifier of the account on the
     *                    pass-through application or the <code>Identity</code>.
     * @param  password   The password of the account or user.
     * 
     * @return The authenticated <code>Identity</code>.
     * @throws ExpiredPasswordException
     * @throws GeneralException
     * @throws AuthenticationFailureException
     */
    public Identity authenticate(String accountId, String password)
            throws ExpiredPasswordException, GeneralException, AuthenticationFailureException {
        
        return authenticate(accountId, password, true, true);
    }
    
    
    /**
     * Attempt pass-through authentication to the application configured as the
     * authentication source.  Fall back to authenticating against the
     * <code>Identity</code> with the given accountId.  If authentication against
     * the pass-through application succeeds but cannot be correlated to a
     * <code>Identity</code>, attempt to create a new <code>Identity</code>.
     * <p>
     * Note: This method commits the current transaction.
     * 
     * @param  accountId  The unique identifier of the account on the
     *                    pass-through application or the <code>Identity</code>.
     * @param  password   The password of the account or user.
     * 
     * @param doUpdateIdentity Flag to update identity lastLogin
     * @param doAudit   Flag to create login audit event
     * @return The authenticated <code>Identity</code>.
     * @throws ExpiredPasswordException
     * @throws GeneralException
     * @throws AuthenticationFailureException
     */
    public Identity authenticate(String accountId, String password, boolean doUpdateIdentity, boolean doAudit)
            throws ExpiredPasswordException, GeneralException, AuthenticationFailureException {

        Identity identity = null;
        try {
            identity = authenticate(accountId, password, doUpdateIdentity);
            //moved auditing code from InternalContext
            if (doAudit) {
                if (identity == null) {
                    // I think we should have thrown by now but make sure
                    // it gets logged
                    logAuthFailure(_context, accountId);
                } else {
                    // TODO: Detatch identity first or leave this as an exercise
                    // for the reader??? Or perhaps this should return a
                    // javax.security.auth.Subject which is Serializable and
                    // works with regular java authz?

                    // this will commit
                    logAuthSuccess(_context, identity.getName(), identity.getAuthApplication(), identity.getAuthAccount());
                }
            }
        } catch (ExpiredPasswordException | GeneralException e) {
            // this exception we will log, others represent system failure
            // that don't count as invalid credentials
            if (doAudit) {
                logAuthFailure(_context, accountId);
            }
            throw e;
        }
        return identity;
    }

    /**
     * Create ActionLogin event for the specified identityName.
     *
     * @param context SailPointContext
     * @param identityName The user of ActionLogin event
     */
    public static void logAuthSuccess(SailPointContext context, String identityName) {
        logAuthSuccess(context, identityName, null, null);
    }

    /**
     * Create ActionLogin event for the specified identityName.
     *
     * @param context SailPointContext
     * @param identityName The user of ActionLogin event
     */
    public static void logAuthSuccess(SailPointContext context, String identityName, String authSource, String authAccount) {
        try {
            // It is ambiguous if the context user will be set by now
            // so override it with the accountId we just used to it
            // be be the "source" in the audit event.  
            // NOTE: Auditor uses the thread local context while we've
            // had ours handed to us.  They should always be the same,
            // but the style difference is kind of ugly.  

            String saveUser = context.getUserName();
            context.setUserName(identityName);
            Auditor.log(AuditEvent.ActionLogin, null, authSource, authAccount);
            context.setUserName(saveUser);

            context.commitTransaction();
        }
        catch (Throwable t) {
            // eat logging failures, else Admin night not be
            // able to fix things?  
        }
    }

    /**
     * Create ActionLoginFailure event for the specified accoutnId.
     * 
     * @param context SailPointContext
     * @param accountId The user of ActionLoginFailure event
     */
    public static void logAuthFailure(SailPointContext context, String accountId) {
        try {   
            String saveUser = context.getUserName();
            context.setUserName(accountId);
            Auditor.log(AuditEvent.ActionLoginFailure);
            context.setUserName(saveUser);

            context.commitTransaction();
        }
        catch (Throwable t) {
            // eat logging failures, else Admin night not be
            // able to fix things?  
        }
    }
        

    /**
     * Attempt pass-through authentication to the application configured as the
     * authentication source.  Fall back to authenticating against the
     * <code>Identity</code> with the given accountId.  If authentication against
     * the pass-through application succeeds but cannot be correlated to a
     * <code>Identity</code>, attempt to create a new <code>Identity</code>.
     * <p>
     * Note: This method commits the current transaction.
     * 
     * @param  accountId  The unique identifier of the account on the
     *                    pass-through application or the <code>Identity</code>.
     * @param  password   The password of the account or user. This must be a plain text password.
     * 
     * @param doUpdateIdentityOnSuccess Flag to update identity lastLogin
     * @return The authenticated <code>Identity</code>.
     * @throws ExpiredPasswordException
     * @throws GeneralException
     * @throws AuthenticationFailureException
     */
    public Identity authenticate(String accountId, String password, boolean doUpdateIdentityOnSuccess)
        throws ExpiredPasswordException, GeneralException, AuthenticationFailureException {

        // IIQCB-1854 - REST authentication accepts encrypted passwords. Do not conditionally decrypt passwords,
        // this reduces the chance an encrypted password get compromised and is used to authenticate
        
        PassThroughResponse response = authenticatePassThrough(new PasswordAuthenticator(), accountId, password);
        Identity identity = response.identity;
        if ( response.account != null && doUpdateIdentityOnSuccess) {
            //set lastLogin for authenticated user and commit if needed
            updateIdentityOnSuccess(_context, identity);
        } else if (response.account == null) {
            // if we did not find an account on the authn application or if there
            // was not an authn application, then try to authn to our internal
            // authn store
            LOG.debug("No authentication application defined or account not " +
                      "found - attempting to authenticate internally.");
            // this will throw its own exceptions
            try {
                identity = authenticateSailPointIdentity(accountId, password);
                identity.setAuthApplication(BrandingServiceFactory.getService().getApplicationName());
                //set lastLogin for authenticated user and commit if needed
                if (doUpdateIdentityOnSuccess) {
                    updateIdentityOnSuccess(_context, identity);
                }
            } 
            catch (ExpiredPasswordException epe) {
                // we had a valid user with an expired password, let this
                // one propagate so they can change the password
                epe.setAppName(IIQ);
                throw epe;
            }
            catch ( GeneralException ex ) {
                // for all other internal exceptions assume it means
                // the user didn't exist and throw the pass-through
                // app exception
                if ( response.appError != null )
                    throw response.appError;
                else
                    throw ex;
            }
        }
        
        return identity;
    }

    @Untraced
    public Identity authenticate(String accountId, Map<String, Object> options)
            throws ExpiredPasswordException, GeneralException, AuthenticationFailureException {

        //decrypt first if needed -- moved from InternalContext
        for (String attr : Util.safeIterable(options.keySet())) {
            if ( ObjectUtil.isEncoded((String)options.get(attr)) ) {
                options.put(attr, _context.decrypt((String)options.get(attr)));
            }
        }
        
        Identity identity = null;
        try {
            identity = authenticateInternal(accountId, options);
            //moved auditing code from InternalContext
            if (identity == null) {
                // I think we should have thrown by now but make sure
                // it gets logged
                logAuthFailure(_context, accountId);
            } else {
                // TODO: Detatch identity first or leave this as an exercise
                // for the reader??? Or perhaps this should return a
                // javax.security.auth.Subject which is Serializable and
                // works with regular java authz?

                // this will commit
                logAuthSuccess(_context, identity.getName(), identity.getAuthApplication(), identity.getAuthAccount());
            }
        } catch (ExpiredPasswordException | GeneralException e) {
            // this exception we will log, others represent system failure
            // that don't count as invalid credentials
            logAuthFailure(_context, accountId);
            throw e;
        }
        return identity;

    }
    
    @Untraced
    private Identity authenticateInternal(String accountId, Map<String, Object> options)
        throws ExpiredPasswordException, GeneralException, AuthenticationFailureException {

        //TODO: No way to validate password is not empty. Don't know makeup of options
        PassThroughResponse response = authenticatePassThrough(new OptionsAuthenticator(), accountId, options);
        Identity identity = response.identity;
        if ( response.account != null ) {
            //set lastLogin for authenticated user and commit  
            updateIdentityOnSuccess(_context, identity);
        } else {
            // if we did not find an account on the authn application or if there
            // was not an authn application, then try to authn to our internal
            // authn store
            String password = "";
            for (String key : options.keySet()) {
                if(key.equalsIgnoreCase("password")) {
                    password = (String)options.get(key);
                }
            }
            if(Util.isNotNullOrEmpty(password)) {
                LOG.debug("No authentication application defined or account not " +
                          "found - attempting to authenticate internally.");
                // this will throw its own exceptions
                try {
                    identity = authenticateSailPointIdentity(accountId, password);
                    identity.setAuthApplication(BrandingServiceFactory.getService().getApplicationName());
                    //set lastLogin for authenticated user and commit  
                    updateIdentityOnSuccess(_context, identity);
                } 
                catch (ExpiredPasswordException epe) {
                    // we had a valid user with an expired password, let this
                    // one propagate so they can change the password
                    epe.setAppName(IIQ);
                    throw epe;
                }
                catch ( GeneralException ex ) {
                    // for all other internal exceptions assume it means
                    // the user didn't exist and throw the pass-through
                    // app exception
                    if ( response.appError != null )
                        throw response.appError;
                    else
                        throw ex;
                }
            }
            if (identity == null) {
                // Couldn't find the Identity for the account which was not authenticated
                // treat as a login failure
                LOG.error("Could not find the Identity for the account.");
                throw new AuthenticationFailureException();
            }
        }
        
        return identity;
    }
    
    /**
     * Updates lastLogin for the Identity, 
     * Clears lock state, failed loginAttempts and failedAuthQuestionAttemps,
     * Creates AuditEvent.IdentityUnlocked event if needed.
     * 
     * This should be performed after successful authentication.
     * 
     * @param context
     * @param identity
     * @throws GeneralException
     */
    public static void updateIdentityOnSuccess(SailPointContext context, Identity identity) throws GeneralException {
        //IIQHH-243 -- moved from InternalContext to here
        //avoiding update Identity multiple times
        //This sets lastLoginDate for Identity using passthrough Auth.
        if (identity != null) {
            Identity locked = null;
            // jsl - temporary, trying to debug lock timeouts from Authenticator for JPMC
            long start = System.currentTimeMillis();
            try {
                //IIQHH-243 -- Lock the Identity
                //commit and unlock the Identity in finally block
                locked = ObjectUtil.lockIdentity(context, identity);

                locked.setLastLogin(new Date());

                Lockinator padlock = new Lockinator(context);
                //Reset lock upon successful login
                //commit in finally block when release lock
                padlock.unlockUser(locked,false);
            } finally {
                // releasing the lock will also commit the Identity 
                try {
                    if (locked != null)
                        ObjectUtil.unlockIdentity(context, locked);
                }
                catch (GeneralException t) {
                    LOG.error("Unable to release Identity lock after login", t);
                }

                checkLockDuration(identity, start, "last login update");
            }
        }
    }
    
    /**
     * After receiving an ExpiredPasswordException from the connector,
     * look for some special information that may have been returned by the
     * connector that lets us do a targeted aggregation so we can
     * still bootstrap missing Identity and Link objects before
     * passing the exeption up to LoginBean.
     *
     * This is all very awkward. It would be better if both the
     * Connector and Authenticator interfaces did not throw
     * ExpiredPasswordException, but rather annotated their
     * objects (ResourceObject and Identity respectively) 
     * with expiration status that can be handled.  I guess
     * stuffing things in the Exception serves a similar purpose
     * but it feels wrong.
     *
     * Do not let this process throw, we still need to throw
     * the password exception.
     */
    private void bootstrapExpiredIdentity(Connector con, 
                                          ExpiredPasswordException epe) {

        try {

            // this is the preferred way if the connector can do it
            ResourceObject account = epe.getResourceObject();
            if (account == null) {
                String id = epe.getNativeIdentity();
                if (id != null) {
                    // go back to the connector and try to read the account
                    account = con.getObject(Connector.TYPE_ACCOUNT, id, null);
                }
            }
             
            if (account != null) {
                // Locate or bootstrap an owning Identity.
                // Unlike the logic above in Authenticate, we
                // won't throw AuthenticationFailureException
                // if we can't correlate.  Continue returning
                // the ExpiredPasswordException and let LoginBean 
                // figure it out.
            	
            	Application app = con.getTargetApplication();
            	if (app == null ) {
            		app = con.getApplication();
            	}
                Identity identity = correlateAccount(app, account);
				
                // LoginBean should use this rather than search again.
                epe.setIdentity(identity);
            }

            // TODO: should we null out nativeIdentity and resourceObject?
            // if LoginBean can't use them?
        }
        catch (Throwable t) {
            // we did our best
            LOG.error("Unable to bootstrap after expired password exception");
            LOG.error(t);
        }
    }

    /**
     * Pass-through authentication is now done in its own method. A ConnectorAuthenticator is used to abstract
     * the two different connector.authenticate methods.
     * @param auth Authenticator doing the connector authentication 
     * @param accountId userName or account id of the pass through system
     * @param value String of password or Map<String, Object> of options
     * @return PassThroughResponse to contain the ResourceObject, Identity and possibly an Exception
     * @throws ExpiredPasswordException during pass through expired password errors
     * @throws GeneralException when something else goes wrong
     */
    @Untraced
    <T extends Object> PassThroughResponse authenticatePassThrough(ConnectorAuthenticator<T> auth, String accountId, T value) 
        throws ExpiredPasswordException, GeneralException {
        ResourceObject account = null;
        Identity identity = null;
        GeneralException appError = null;
        
        // First try the applications configured for pass-through auth.
        List<Application> applications = getAuthApplications();
        if (applications != null ) {
            for (Application app : applications) {

                // if an app somehow got configured as a pass-through app 
                // when authentication isn't one of its features, punt to the 
                // next app on the auth list
                List<Feature> features = app.getFeatures();
                if ((null == features) || !features.contains(Feature.AUTHENTICATE)) {
                    LOG.error(app.getName() + " mistakenly designated as a pass-through " + 
                        "authentication app.  Please update the Login Configuration " +
                        "under System Setup.");
                    
                    continue;
                }
                
                Connector con = ConnectorFactory.getConnector(app, null);
                // (note that "not found" exceptions are swallowed)
                try {
                    account = authenticateResourceAccount(auth, con, accountId, value);
                } 
                catch (ExpiredPasswordException epe) {
                    // we had a valid user with an expired password, let this
                    // one propagate so they can change the password
                    
                    // Need to know which app so that we know which password to change
                    epe.setAppName(app.getName());

                    // bug# 10155, boostrap an Identity/Link if we don't have one so
                    // LoginBean can proceed
                    bootstrapExpiredIdentity(con, epe);

                    throw epe;
                }
                catch ( GeneralException ex ) {
                    // save this in case we decide not to do native auth
                    appError = ex;
                }
                finally {
                    Application connApp = ObjectUtil.getLocalApplication(con);
                    ObjectUtil.updateApplicationConfig(_context, connApp);
                }

                identity = correlateAuthenticatedAccount(app, account);
                verifyActive(identity);
                if (isAdminPassThroughDisabled(identity)) {
                    // setting these to null to allow internal authentication
                    identity = null;
                    account = null;
                }
                if (identity != null) {
                    identity.setAuthApplication(app.getName());
                    identity.setAuthAccount(accountId);
                    break;
                }
            }
        }

        return new PassThroughResponse(account, identity, appError);
    }
    
    /**
     * Authenticate against the given Connector and swallow an
     * ObjectNotFoundException if no account can be authenticated with the given
     * accountId.
     * 
     * @param  con        The Connector to use for authentication.
     * @param  accountId  The accountId to authenticate.
     * @param  value   The password to user for authentication or a Map<String, Object> of options.
     * 
     * @return A ResourceObject with attributes of the authenticated account
     *         or null if an account could not be found.
     * 
     * @throws GeneralException  If the account exists but authentication fails.
     */
    @Untraced
    private <T extends Object> ResourceObject authenticateResourceAccount(ConnectorAuthenticator<T> auth, Connector con, 
                                                       String accountId,
                                                       T value)
            throws GeneralException, ExpiredPasswordException {

        ResourceObject account = null;
        try {
            account = auth.authenticate(con, accountId, value);
            if (account != null)
                LOG.debug("Successfully authenticated accountId '" + accountId +
                          "' and retrieved account '" + account.getNameOrId() +
                          "'.");
            else {
                // same as ObjectNotFoundException
                // Do not throw - attempt SailPoint identity authn.
                LOG.debug("Accountid [" +accountId+"] was not found.");
            }
        } 
        catch (ObjectNotFoundException e) {
            // Do not throw - attempt SailPoint identity authn.
            LOG.debug("Accountid [" +accountId+"] was not found.");
        } catch (ExpiredPasswordException e) {
            // jsl - it is important that this remain ExpiredPasswordException
            LOG.error(e);
            throw e;
        } catch (AuthenticationFailedException e) {
            // let these exceptions percolate up
            LOG.error(e);
            throw new AuthenticationFailureException(e);
        } catch (ConnectorException e) {
            LOG.error(e);
            throw new AuthenticationFailureException(e);
        }
        return account;
    }
    
    /**
     * Attempt to authenticate a SailPoint <code>Identity</code> with the given
     * accountId.
     * 
     * @param  accountId  The accountId to attempt to authenticate.
     * @param  password   The password of the user.
     * 
     * @return The <code>Identity</code> if authentication succeeded.
     * 
     * @throws GeneralException  If the user was not found or was found and the
     *                           password did not match or was not specified.
     */
    private Identity authenticateSailPointIdentity(String accountId, 
                                                   String password) 
        throws AuthenticationFailureException, ExpiredPasswordException, GeneralException {

        // Ordinarilly we would use getObject, but this does a case sensitive 
        // search on Oracle.  Since we're dealing with a name typed in by
        // the user convert it to a case insensitive search followed by a fetch.

        String id = null;
        Boolean authLockoutEnabled = _context.getConfiguration().getBoolean(Configuration.ENABLE_AUTH_LOCKOUT);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.ignoreCase(Filter.eq("name", accountId)));
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);
        if (it.hasNext()) {
            id = (String)(it.next()[0]);
            if (it.hasNext()) {
                // I suppose we could just match the first identity
                // since they have to know the password.  This
                // really shouldn't happen though if correlation
                // was working properly.
                LOG.error("Ambiguous identity name");
                throw new AuthenticationFailureException();
            }   
        }

        // detailed errs - AUTH_FAILURE_INVALID_USR and AUTH_FAILURE_INVALID_PASSWD
        // should only be displayed if sys conf value loginErrorStyle=detailed

        if (id == null)
            throw new AuthenticationFailureException(MessageKeys.AUTH_FAILURE_INVALID_USR);
        
        Identity user = null;
        
        // jsl - temporary, trying to debug lock timeouts from Authenticator for JPMC
        long start = System.currentTimeMillis();
        try {
            //IIQHH-243 -- Lock the Identity
            //commit and unlock the Identity in finally block
            user = ObjectUtil.lockIdentity(_context, id);

            if (user == null) {
                // woah, deleted out from under us!
                throw new AuthenticationFailureException(MessageKeys.AUTH_FAILURE_INVALID_USR);
            }
            
            Lockinator padlock = new Lockinator(_context);
            
            //If auth lockout is enabled and user is locked, throw exception
            if(padlock.isUserLocked(user, Lockinator.LockType.Login) 
                    && authLockoutEnabled) {
                throw new IdentityLockedException(MessageKeys.IDENTITY_LOCKED_OUT_ERROR);
            }
            
            verifyActive(user);
            
            // This disallows empty/null passwords or fails on an invalid password.
            boolean isMatch = (Util.isNotNullOrEmpty(password)
                    && EncodingUtil.isMatch(password, user.getPassword(), _context));

            if (!isMatch) {
                //This should be where we increase invalid login attempts on identity preferences
                //We do not want to do this for administrators????
                //Do we want to increment if the lockout feature is disabled?
                int failedattempts = user.getFailedLoginAttempts()+1;
                //Update failed attempts for user
                user.setFailedLoginAttempts(failedattempts);
                
                //If we have exceeded the configured attempts, we need to lock the user
                if((failedattempts >= _context.getConfiguration().getInt(Configuration.FAILED_LOGIN_ATTEMPTS))
                        && authLockoutEnabled) {
                    if(_context.getConfiguration().getBoolean(Configuration.PROTECTED_USER_LOCKOUT)) {
                        
                        padlock.lockUser(user, accountId, false);
                    } else if(!user.isProtected()) {
                        
                        padlock.lockUser(user, accountId, false);
                    }
                }
                
                throw new AuthenticationFailureException(MessageKeys.AUTH_FAILURE_INVALID_PASSWD);
            }
    
            // check password expiration
            PasswordPolice police = new PasswordPolice(_context);
            police.checkExpiration(user);
        } 
        finally {
            // releasing the lock will also commit the Identity 
            try {
                if (user != null)
                    ObjectUtil.unlockIdentity(_context, user);
            }
            catch (GeneralException t) {
                LOG.error("Unable to release Identity lock after login", t);
            }

            checkLockDuration(user, start, "authentication");
        }

        return user;
    }

    static private void checkLockDuration(Identity identity, long start, String type) {
        long end = System.currentTimeMillis();
        int seconds = ((int)(end - start)) / 1000;
        if (seconds > 20) {
            LOG.error("Unusually long " + type + ": " + Util.itoa(seconds));

            List<sailpoint.object.Link> links = identity.getLinks();
            LOG.error("Identity: " + identity.getName() + " with " + Util.itoa(links.size()) + " accounts");
        }
    }

    /**
     * Return the list of configured pass-through authentication
     * applications.  Order of the list is important.
     *
     * Originally pass-through was configured by setting a flag
     * on the Application object, we searched for the ones with
     * that flag and used the first one we found.
     *
     * Starting with 2.5p7 we now configure these as an ordered
     * list in the sysconfig.  We will do dynamic upgrades from
     * the old format to the new.
     */
    public List<Application> getAuthApplications() 
        throws GeneralException {

        List<Application> apps = null;
        try {
            Configuration config = _context.getConfiguration();

            // This is normallly a List<String> but use ObjectUtil
            // to tolerate CSVs and References.
            // An unfortunate up-reference to a higher package, consider
            // moving this.
            Object value = config.get(Configuration.LOGIN_PASS_THROUGH);
            apps = ObjectUtil.getObjects(_context, Application.class, value);

            // dynamic upgrade
            if (apps == null || apps.size() == 0) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("authenticationResource", true));
                apps = _context.getObjects(Application.class, qo);
                if (apps != null && apps.size() > 0) {
                    List<Reference> refs = new ArrayList<Reference>();
                    for (Application app : apps) {
                        refs.add(new Reference(app));
                        app.setAuthenticationResource(false);
                        _context.saveObject(app);
                        _context.commitTransaction();
                    }

                    // Load this from the database to update it.
                    config = _context.getObjectByName(Configuration.class,
                                                      Configuration.OBJ_NAME);
                    config.put(Configuration.LOGIN_PASS_THROUGH, refs);
                    _context.saveObject(config);
                    _context.commitTransaction();
                }
            }
        }
        catch (GeneralException e) {
            // Can be here for misconfiguration errors, don't let this
            // prevent native login to the Admin can get in and fix things.
            LOG.error(e);
        }
        return apps;
    }
    
    private void verifyActive(Identity identity) throws AuthenticationFailureException {
        if (identity != null && identity.isInactive()) {
            LOG.warn("User " + identity.getName() + " has been correlated, but is inactive and can not login.");
            throw new AuthenticationFailureException(MessageKeys.AUTH_FAILURE_INACTIVE_USR);
        }
    }
    
    private boolean isAdminPassThroughDisabled(final Identity identity) {
        boolean result = false;
        if (preventPassThroughAdmin() && identity != null) {
            final String ADMIN_USER = BrandingServiceFactory.getService().getAdminUserName();
            if (ADMIN_USER.equals(identity.getName())) {
                // IIQETN-1374 Prevent pass through auth for spadmin - only warn and allow internal authentication
                LOG.warn(ADMIN_USER + " can not be used for pass through authentication");
                result = true;
            }
        }
        return result;
    }
    
    private boolean preventPassThroughAdmin() {
        boolean result = true;
        try {
            Configuration sysConfig = _context.getConfiguration();
            if (sysConfig != null) {
                // if undefined, will return false and prevent pass through auth for OOTB "spadmin" or "admin"
                result = !sysConfig.getBoolean(Configuration.ALLOW_ADMIN_PASS_THROUGH);
            }
        } catch (GeneralException e) {
            LOG.error("Unable to obtain Configuration: " + Configuration.ALLOW_ADMIN_PASS_THROUGH, e);
        }
        return result;
    }
    
    private Identity correlateAuthenticatedAccount(Application app, ResourceObject account) throws GeneralException {
        Identity identity = null;
        if ( account != null ) {
            // We successfully authenticated a user on the application -
            // now try to correlate with a user.
            identity = correlateAccount(app, account);
            if (identity == null) {
                // autoCreate was off and the Identity didn't exist
                // treat as a login failure
                LOG.error("User was authenticated to the application, but couldn't correlate.");
                throw new AuthenticationFailureException();
            }
        }
        return identity;
    }

    /**
     * Correlate the ResourceObject with the owning Identity in IIQ.
     * 
     * This method will perform correlation and if correlation fails
     * will create an identity from the ResourceObject.
     * 
     * Use the systemconfiguration to drive the behavior of 
     * auto creation of accounts and refreshing existing links
     * during authentication.
     * 
     * By default we do not refresh the account during passthrough, but do
     * auto create identities for authenticated links.
     * 
     * @See {@link sailpoint.object.Configuration#DISABLE_PASSTHROUGH_AUTO_CREATE}
     * @See {@link sailpoint.object.Configuration#REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION}
     * 
     * @param app
     * @param account
     * 
     * @return the correlated Identity
     * @throws GeneralException
     */
    private Identity correlateAccount(Application app, ResourceObject account)
        throws GeneralException {

        Identity identity = null;
        if ( account != null ) {
            LOG.debug("Successfully authenticated user to application - " +
                      "attempting to correlate account to user.");

            Configuration sysConfig = _context.getConfiguration();
            
            boolean autoCreate = true;
            boolean performLinkRefresh = false;
            if  ( sysConfig != null ) {
                if ( sysConfig.getBoolean(Configuration.REFRESH_PASSTHROUGH_LINK_DURING_AUTHENTICATION) )
                    performLinkRefresh = true;
                
                if ( sysConfig.getBoolean(Configuration.DISABLE_PASSTHROUGH_AUTO_CREATE) )
                    autoCreate = false;
            }
               
            Identitizer idz = new Identitizer(_context); 
            if ( performLinkRefresh ) {
                idz.setPromoteAttributes(true);
                idz.prepare();
            }
            identity = idz.bootstrap(app, account, autoCreate, performLinkRefresh);
        }
        return identity;
    }
    
    
    /**
     * max number of times user can try to authenticate using auth questions
     * @return
     * @throws GeneralException
     */
    protected int getMaxFailedAttempts() throws GeneralException {
        return _context.getConfiguration().getInt(Configuration.FAILED_LOGIN_ATTEMPTS);
    }

    /**
     * lock period in millis
     * @return
     * @throws GeneralException
     */
    protected long getLockoutPeriod() throws GeneralException {
        return _context.getConfiguration().getLong(Configuration.LOGIN_LOCKOUT_DURATION);
    }
}
