/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The SailPointContext is an example of the Session Fascade pattern
 * that provides the primary API that most of the system code should use
 * to access core services like the persistent store,  the task scheduler, 
 * and the email notifier.  A SailPointContext must be obtained by calling
 * SailPointFactory.
 *
 * The SailPointContext can also be considered a Data Access Object (DAO) 
 * for Data Transfer Objects (DTO)s which are all subclasses
 * of SailPointObject class.  
 *
 * Author: Jeff
 * 
 * AbstractSailPointContext implements this interafce and provides
 * some convenience methods, it is usually easier to extend that than
 * implement this. 
 *
 * SimulatedSailPointContext manages a collection of objects in memory, 
 * it is convenient for UI testing where a actual server with persistent
 * storage is not required.
 *
 */
package sailpoint.api;

import java.sql.Connection;
import java.util.Map;

import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.RuleRunner;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLReferenceResolver;

/**
 * The primary API for accessing the persistent store and performing 
 * core system operations.
 */
public interface SailPointContext
    extends PersistenceManager, RuleRunner, XMLReferenceResolver {

    /**
     * Return a context derived from this one.
     * 
     * A single prototype context can serve as a factory for 
     * thread-specific contexts, depending on the implementation.
     * SailPointFactory will always call this when returning contexts.
     */
    SailPointContext getContext();

    /**
     * Method to be called by the SailPointFactory immediately after
     * creating a context from a prototype. See comments
     * in InternalContext for the complex and unfortunate reason.
     */
    void prepare();

    /**
     * Return a JDBC Connection to the underlying database.
     * This is used in a few places that need to run complex SQL 
     * queries. It should be unnecessary now that you
     * can run SQL with {@link PersistenceManager#search}.
     */
    Connection getJdbcConnection() throws GeneralException;

    /**
     * @deprecated Use {@link #getJdbcConnection()}
     */
    @Deprecated
    Connection getConnection() throws GeneralException;
    
    /**
     * Return true if this context has been closed and can no longer be used.
     * @ignore
     * Used in the UI to see of a context left on the HttpSession is still
     * active.
     */
    boolean isClosed();

    /**
     * Set the name of the current user of this context. This will
     * be used when generating audit events.
     */
    public void setUserName(String name);

    /**
     * Return the name of the current user of this context.
     * This will normally be the name of an Identity but
     * occasionally it might be an abstract name like "System"
     * or "Scheduler".
     */
    public String getUserName();

    /**
     * This can be used to impersonate the given identity with respect to the
     * scoping that is applied. When impersonating, the given identity's
     * controlled scopes are used to scope results rather than the user
     * specified by setUserName(String). Note that this Identity does not have
     * to be persistent.
     * 
     * @param  identity  The Identity to impersonate for scoping.
     */
    public void impersonate(Identity identity);
    
    /**
     * Set whether the results from search and getObjects methods should
     * have scoping applied to them based on the controlled scopes of the
     * user set with <code>setUserName(String)</code>. This can be
     * overridden by the QueryOptions that are passed into the searching
     * methods. Scoping results is disabled by default.
     * 
     * @param  scopeResults  Whether results from the search methods should
     *                       be scoped.
     */
    public void setScopeResults(boolean scopeResults);

    /**
     * Returns true if query results are being scoped.
     */
    public boolean getScopeResults();
    
    /**
     * Retrieve the system configuration object.
     * You can also get this just by calling getObject() but
     * this method will use a static cache.
     */
    public Configuration getConfiguration()
        throws GeneralException;

    /**
     * Encrypt a string.
     */
    public String encrypt(String src) throws GeneralException;

    /**
     * Encrypt a string, checking for double encryption
     * @param src String to encrypt
     * @param checkForEncrypted true to check for/prevent double encryption
     * @return encrypted version of String
     * @throws GeneralException
     */
    public String encrypt(String src, boolean checkForEncrypted) throws GeneralException;

    /**
     * Decrypt an encrypted string. 
     * This can fail if the context does not have privileges
     * to perform decryption.
     */
    public String decrypt(String src) throws GeneralException;

    /**
     * Send an email notification.
     * 
     * @throws  EmailException    If there is a problem sending the email.
     * @throws  GeneralException  If there is a system error.
     */
    public void sendEmailNotification(EmailTemplate template, EmailOptions options)
        throws GeneralException, EmailException;

    /**
     * Authenticate a user with the given accountId and password.
     *
     * This should have the side-effect of creating a new <code>Identity</code> if
     * the following criteria are met:
     * <ol>
     *   <li>Authentication is passed-through to another authentication source
     *       and succeeds.</li>
     *   <li>The account that was correlated on the pass-through authentication
     *       source does not have a corresponding <code>Identity</code>.</li>
     * </ol>
     * 
     * @param  accountId  A unique identifier for the user/account to
     *                    authenticate.
     * @param  password   The password to use in the authentication credentials.
     *
     * @return The authenticated or newly created user if authentication
     *         succeeded.
     *
     * @throws GeneralException  Can be thrown if authentication fails for some
     *                           reason (invalid password, account locked), if
     *                           an account/user with the given accountId cannot
     *                           was not found, or a problem occurs when
     *                           auto-creating a user.
     * @throws ExpiredPasswordException Can be thrown if authentication fails
     *                                  because account password is expired.
     */
    public Identity authenticate(String accountId, String password)
            throws GeneralException, ExpiredPasswordException;
    
    public Identity authenticate(String accountId, Map<String, Object> options)
            throws GeneralException, ExpiredPasswordException;
    
    /**
     * Set a context property. This is intended only for diagnostics,
     * currently there are not any publicly defined properties.
     */
    public void setProperty(String name, Object value);

    /**
     * Return the property with the given name.
     */
    public Object getProperty(String name);

}


