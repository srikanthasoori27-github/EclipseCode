/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Concrete implementation of the SailPointContext that provides
 * the Data Access Object (DAO) layer for the internal persistent store.
 * This is also the layer where we implement visitors, authorization,
 * and other potential side effects of object modification.
 * 
 * Author: Jeff, Kelly
 * 
 * Currently an initial instance is constructed by Spring, given 
 * an Environment, and then handed to the SailPointFactory for use
 * as a prototype in building other contexts.  In retrospect I'm 
 * not terribly thrilled with the prototype instance approach, it might
 * be to have another factory interface that just creates SailPointContexts
 * without all the thread services SailPointFactory provides and does
 * not have to be a well known class like SailPointFactory.  Doing this
 * as a prototype means that we have to implement a the entire 
 * SailPointContext interface just to be a factory.
 * 
 */

package sailpoint.server;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.EmailNotifier;
import sailpoint.api.EncodingUtil;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.Cacheable;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.LockInfo;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.ProvisioningConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.RuleRunner;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.UIConfig;
import sailpoint.persistence.ClassMappingUtil;
import sailpoint.provisioning.IntegrationConfigFinder;
import sailpoint.request.EmailRequestExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

public class InternalContext extends AbstractSailPointContext {

    private static final Log LOG = LogFactory.getLog(InternalContext.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Awful flag to control the initial caching of certain configuration 
     * objects.
     * TODO: See if this can be pushed down into initialization 
     * of the Environment.
     */
    static boolean _configCached;

    /**
     * Singleton object containing the Spring configurable components.
     */
    Environment _env;

    /**
     * Cache servivce we notify when objects change.
     */ 
    static CacheService _caches;

    /**
     * The implementation of the persistent storage manager.
     * There is one of these in the Environment but it has to be cloned
     * every time we make a context from the prototype because
     * it indirectly contains a Hibernate Session which cannot be shared
     * among threads.  I really don't like this aspect of the 
     * Environment/InternalContext interaction.
     */
    PersistenceManager _store;

    /**
     * Handle to the pass-through authenticator.
     * This is not configured with Spring, we just create a known
     * service object.  If we ever get to the point where we need pluggable
     * authentication it will have to be dynamically configurable so
     * Spring doesn't help.  Should that ever happen Authenticator will
     * manage it.
     */
    Authenticator _authenticator;

    /**
     * Object providing crypto services.
     * This is not configured with Spring, though this is potentially
     * more pluggable than Authenticator.  We use the javax.crypto API
     * so it is already reasonably pluggable without needing Spring.
     */
    Transformer _transformer;

    /**
     * Cached Connection from _dataSource, cached here so 
     * consumers don't drain the connectin pool. 
     */
    private Connection _dbCon;

    /**
     * Set to true after we've been closed.
     */
    boolean _closed;

    /**
     * The name of the "user" of this context.  This will be used
     * for auditing, and eventually authorization.  
     *
     * This is almost always an Identity name but we're keeping it abstract
     * so we can support anonymous users, users of external systems accessing
     * us through SPML, or "system" processes that aren't done on behalf
     * of any particular user.
     */
    String _userName;

    /**
     * Cached Identity for the user name (if this represents a real user).
     */
    transient Identity _user;
    
    /**
     * encapsulation of email sending logic
     */
    static transient EmailSender _emailSender = new EmailSender();

    /**
     * Operations used in the afterCommit method.
     */
    private enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }
    


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This should only be called in two places.  One from Spring
     * when it creates the prototype instance to give to the
     * SailPointFactory, and then by SailPointFactory when it clones
     * the prototype with the getContext method.
     */
    public InternalContext() {

        // don't bother injecting these
        _authenticator = new Authenticator(this);
        _transformer = new Transformer();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void setEnvironment(Environment env) {
        _env = env;
    }
    
    public Environment getEnvironment() {
        return _env;
    }

    public void setPersistenceManager(PersistenceManager pm) {
        _store = pm;
    }

    public PersistenceManager getPersistenceManager() {
        return _store;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Factory Method
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is the primary "factory" for contexts, called indirectly
     * by SailPointFactory from the prototype instance it was given
     * by Spring.
     *
     * NOTE: Even though most things are nicely encapsulated in the
     * Environment, we have to always clone the PersistenceManager because
     * it indirectly contains a Hibernate Session which can't be shared.
     * I don't like this aspect of the Environment, but I don't see a better
     * way without promoting Hibernate awareness into the Environment
     * rather than burying it in the PersistenceManager.  To avoid the
     * special case we could just clone everything in the Environment or
     * make them implement some sort of "clone required for thread" interface
     * but that seems like overkill.  At least this is encapsulated down
     * here where we can have dirty little secrets.
     */
    @Override
    public SailPointContext getContext()
    {
        InternalContext ctx = new InternalContext();
        // could just make this a static rather than passing after construction
        ctx.setEnvironment(_env);

        // make a priviate clone of the persistence manager (aka Hibernate)
        PersistenceManager pm = _env.getPersistenceManager();
        if (pm instanceof Cloneable) {
            try {
                pm = (PersistenceManager)pm.clone();
            }
            catch (CloneNotSupportedException e) {
                // Shouldn't throw since we're Cloneable.
            }
        }
        ctx.setPersistenceManager(pm);

        return ctx;
    }

    /**
     * An awful kludge to dig some configuration out of the persistent 
     * store and cache it in static fields.   When called in the 
     * constructor (or by getContext()) this introduces some very subtle
     * and ugly dependencies with the Hibernate user types XmlType and MapType.
     * These two need to parse XML with a "resolver" that can locate other
     * objects referenced from the XML object.  They use SailPointFactory
     * to get the current thread local context.  Usually we are in the process
     * of creating the context to assign to the thread local so it will
     * not be set yet.  The user types must therefore be tolerant of having
     * a null resolver, which up here means that we can't load containing
     * object references.
     *
     * Unfortunately ObjectConfig:Identity has several references that need 
     * to be resolved. So we have two choices: make SailPointContext call back
     * to us to load configuration after it has set the thread local, or
     * make InternalContext temporarily ask SailPointContext to set its
     * thread local to this.  Both are rather ugly violations of 
     * encapsulation.
     *
     * It felt marginally better to move this call to SailPointFactory.
     *
     * UPDATE: I'm thinking it would be better to try and pull this
     * back down here or maybe even into Environment.
     */
    public void prepare() {
        
        if (!_configCached) {
            
            // set this early just in case somethign in the getObject
            // call ends up back here
            _configCached = true;
            
            // CacheService is one of the only services that
            // have an extended API we use directly.  
            _caches = _env.getCacheService();

            try {
                LOG.info("Loading system configuration");

                // Load the ClassMappings from Hibernate config files and
                // do some sanity checks
                ClassMappingUtil.prepare();

                // load it if we have it (usually true)
                Configuration sysconfig = 
                    _store.getObjectByName(Configuration.class,
                                           Configuration.OBJ_NAME);

                if (sysconfig != null) {
                    // cache it
                    _caches.register(sysconfig);

                    // Sigh, lockTimeout needs to be accessible to
                    // HibernatePersistenceManager but since we've always
                    // created that with Spring it's awkward.  We could
                    // ask _store to propagate the sysconfig to all the
                    // child stores, but it's easier just to pass this
                    // through LockInfo.
                    LockInfo.setConfiguration(sysconfig);
                }

                LOG.info("Loading identity selector configuration");
                Configuration idconfig = 
                    _store.getObjectByName(Configuration.class,
                                           Configuration.IDENTITY_SELECTOR_CONFIG);
                if (idconfig != null)
                    _caches.register(idconfig);

                LOG.info("Loading AIServices configuration");
                Configuration iaiconfig =
                    _store.getObjectByName(Configuration.class,
                                           Configuration.IAI_CONFIG);
                if (iaiconfig != null)
                    _caches.register(iaiconfig);

                LOG.info("Loading FAM configuration");
                Configuration famConfig =
                        _store.getObjectByName(Configuration.class,
                                Configuration.FAM_CONFIG);
                if (famConfig != null)
                    _caches.register(famConfig);

                LOG.info("Loading RapidSetup configuration");
                Configuration rapidSetupConfig =
                        _store.getObjectByName(Configuration.class,
                                Configuration.RAPIDSETUP_CONFIG);
                if (rapidSetupConfig != null)
                    _caches.register(rapidSetupConfig);

                LOG.info("Loading Audit config");
                // TODO: Would this make sense as an ObjectConfig?
                // it isn't really class specific more concept specific
                AuditConfig auconfig = _store.getObjectByName(AuditConfig.class,
                                                        AuditConfig.OBJ_NAME);
                if (auconfig != null)
                    _caches.register(auconfig);

                LOG.info("Loading UI config");
                UIConfig uiconfig = _store.getObjectByName(UIConfig.class, UIConfig.OBJ_NAME);
                if (uiconfig != null)
                    _caches.register(uiconfig);

                LOG.info("Loading ObjectConfigs");
                cacheObjectConfigs();

                // Could do something to get the Explanator cache
                // started but a full load will take forever...
            }
            catch (Throwable t) {
                // nothing we can do at this point in the Spring 
                // lifecycle
                LOG.error("Initialization error: " + t.toString(), t);
            }
        }
    }

    /**
     * Cache the ObjectConfigs for classes that have them.
     * This must be called only once!  The CacheReferences we create
     * here may be referenced indefinately in other places.
     */
    private void cacheObjectConfigs()
        throws GeneralException {

        List<ObjectConfig> configs = _store.getObjects(ObjectConfig.class);

        // make sure they're fully loaded and detached before installing
        if (configs != null) {
            for (ObjectConfig config : configs) {
                // must be fully loaded since it won't be attached
                config.load();
                _store.decache(config);
                _caches.register(config);
            }
        }
    }

    /**
     * Clone an ObjectConfig prior to caching it.
     */
    private ObjectConfig cloneConfig(ObjectConfig src) 
        throws GeneralException {
        
        XMLObjectFactory f = XMLObjectFactory.getInstance();
        // the resolver will get us things like Application refs but those
        // may not be fully loaded!
        ObjectConfig config = (ObjectConfig)f.clone(src, this);
        
        // fully load any references so we can leave it in static caches
        config.load();
        // decache?

        return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointContext methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Used in a few special situations (mainly reports)
     * to get a connection directly to the database.
     * @ignore
     * jsl - !!! this will create a second Connection it does not
     * return the one currently used by Hibernate.  This can effectively
     * double the number of Connections a context needs, reconsider this
     */
    public Connection getJdbcConnection() throws GeneralException {

        DataSource ds = _env.getSpringDataSource();
        if (ds == null)
            throw new GeneralException("Unable to return connection, " + 
                                       "no DataSource defined!");
        
        try {
            if ( _dbCon == null ) 
                _dbCon = ds.getConnection();
        }
        catch (SQLException se) {
            throw new GeneralException(se);
        }
        return _dbCon;
    }

    /**
     * Return true if we're closed and can no longer be used.
     */
    public boolean isClosed() {
        return _closed;
    }
    
    /**
     * Set the current user name.
     * We're not using an Identity here in case we need to support anonymous
     * or "system" access without formally logging in.
     */
    public void setUserName(String name) {
        _userName = name;
    }

    public String getUserName() {
        return _userName;
    }

    /**
     * Return the system configuration object.
     * Just forwards to the Configuration classes cache.
     */
    public Configuration getConfiguration() throws GeneralException {

        return Configuration.getSystemConfig();
    }

    @Untraced
    public String encrypt(String src) throws GeneralException {

        return encrypt(src, true);
    }



    /**
     * Return the encrypted value of src. If checkForEncoded is true, we will check if src has already been encrypted.
     * This will prevent doube encryption.
     * @param src String to Encrypt
     * @param checkForEncrypted True to check if src is already encrypted. If it has been, return without encrypting
     * @return encrypted value for a given string
     * @throws GeneralException
     */
    @Untraced
    public String encrypt(String src, boolean checkForEncrypted) throws GeneralException {
        if (EncodingUtil.isHashed(src)) {
            LOG.error("Can not encrypt hashed value.  Please use EncodingUtil.encode() for Identity secrets.");
            return src;
        }

        return _transformer.encode(src, checkForEncrypted);
    }

    @Untraced
    @SensitiveTraceReturn
    public String decrypt(String src) throws GeneralException {
        if (EncodingUtil.isHashed(src)) {
            LOG.error("Can not decrypt hashed value. Please use EncodingUtil.isMatch() for Identity secrets.");
            return src;
        }

        return _transformer.decode(src);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PersistenceManager
    //
    //////////////////////////////////////////////////////////////////////

    public void startTransaction() throws GeneralException {
        _store.startTransaction();
    }

    public void commitTransaction() throws GeneralException {
        _store.commitTransaction();
    }

    public void rollbackTransaction() throws GeneralException {
        _store.rollbackTransaction();
    }

    public void close() throws GeneralException {
        _store.close();
        if ( _dbCon != null )  {
            try {
                _dbCon.close();
            } catch(java.sql.SQLException e) {
                /*DEBUG*/if ( LOG.isDebugEnabled() ) {
                /*DEBUG*/    LOG.debug("Exception closing db connection:"+
                /*DEBUG*/               e.toString());
                /*DEBUG*/}
            }
         }
        // set this so the UI can check ti see if a cached context
        // can still be used
        _closed = true;
    }

    /**
     * This method overrides the method in the AbstractContext, which 
     * calls the database twice, once to search by name and again to 
     * search by id.  If we have a real database this can normally
     * be optimized by combining both comparisons into one search filter.
     *
     * The name or id is expected to identify a single object if more
     * than one object satisfy the query an exception is thrown.
     */
    public <T extends SailPointObject> T getObject(Class<T> cls, 
                                                   String nameOrId)
        throws GeneralException {

        return _store.getObject(cls, nameOrId);
    }

    public <T extends SailPointObject> T getObjectById(Class<T> cls, String id)
        throws GeneralException {
        return _store.getObjectById(cls, id);
    }

    public <T extends SailPointObject> T getObjectByName(Class<T> c, String name)
        throws GeneralException {

        T obj = _store.getObjectByName(c, name);
        return (T)obj;
    }

    public <T extends SailPointObject> T lockObjectById(Class<T> cls, String id,
                                                        Map<String, Object> options)
        throws GeneralException {

        return _store.lockObjectById(cls, id, options);
    }

    public <T extends SailPointObject> T lockObjectByName(Class<T> cls, String name,
                                                    Map<String, Object> options)
        throws GeneralException {

        return _store.lockObjectByName(cls, name, options);
    }
    
    public <T extends SailPointObject> T lockObject(Class<T> clazz, LockParameters params) 
        throws GeneralException {
        
        return _store.lockObject(clazz, params);
    }
    

    public <T extends SailPointObject> void unlockObject(T object)
        throws GeneralException {

        _store.unlockObject(object);
    }

    public <T extends SailPointObject> T getUniqueObject(T example)
        throws GeneralException {

        return _store.getUniqueObject(example);
    }

    public <T extends SailPointObject> T getUniqueObject(Class<T> cls, Filter f)
        throws GeneralException {

        return _store.getUniqueObject(cls, f);
    }

    /**
     * Save an object in the persistent store, and adjust the system for side effects.
     */
    public void saveObject(SailPointObject obj) throws GeneralException {
        runPrePersistVisitor(obj);

        // ETN-5690: encrypt secrets prior to store.saveObject. Starting
        // in 5.1 we have an "authentication answers" list that has to
        // be encrypted.  This is a lazy loaded list and has to be
        // reattached before we try to encrypt the elements. Prior to
        // this change, we did the encryption AFTER the _store.saveObject
        // call, however it really must be done prior to saving the object.

        // encrypt things left as plain text in XML
        encryptSecrets(obj);

        _store.saveObject(obj);

        runPostPersistVisitor(obj);
    }
    
    /**
     * Before saving an object, make sure sensitive attributes
     * are encrypted.  This is a temporary kludge to make sure encryption
     * happens before we start doing it reliably in the UI layer.
     * 
     * UPDATE: Actually we need it even if the UI is encrypting properly
     * because importing XML or editing in the debug pages can leave
     * passwords in clear text.  
     *
     * There are still ways around this, if you dirty an Identity
     * and don't call saveObject it will still get flushed.  But that
     * pattern shouldn't happen in the console and debug pages.
     * 
     */
    private void encryptSecrets(SailPointObject obj) {

        // don't let this throw in case something goes wrong
        try {
            if (obj instanceof Application) {
                Application app = (Application)obj;
                // some test apps don't have connectors, so don't
                // provoke an error
                if (app.getConnector() != null) {
                    Connector con = ConnectorFactory.getConnectorNoClone(app, null);
                    List<AttributeDefinition> atts = con.getDefaultAttributes();
                    if (atts != null) {
                        for (AttributeDefinition att : atts) {
                            if (AttributeDefinition.TYPE_SECRET.equals(att.getType())) {
                                encryptAppConfigAttr(app, att.getName());                                
                            }
                        }
                    }
                } 
                List<String> encryptedAttrs = Util.csvToList(ProvisioningPlan.ATT_PASSWORD);
                List<String> appSpecified = app.getEncrpytedConfigAttributes();                
                if ( appSpecified != null ) {
                    encryptedAttrs.addAll(appSpecified);
                }
                for ( String name : encryptedAttrs ) {
                    encryptAppConfigAttr(app, name); 
                }                
            }
            else if (obj instanceof Identity) {
                ObjectUtil.encryptIdentity((Identity) obj, this);
            } else if (obj instanceof Link) {
                ObjectUtil.encryptLink((Link)obj, this);
            }
        }
        catch (Throwable t) {
            LOG.error(t, t);
        }
    }
    
    /**
     * Encrypt and store the named attribute onto the
     * application's configuraiton.
     * 
     * @param app
     * @param name
     * @throws GeneralException
     */
    private void encryptAppConfigAttr(Application app, String name) 
        throws GeneralException {
            
        String strVal = Util.otos(MapUtil.get(app.getAttributes(), name));
        if ( strVal != null && !_transformer.isEncoded(strVal) ) {            
            MapUtil.putAll(app.getAttributes(), name, (x) -> {
                String result = null;
                try {
                    String value = Util.otos(x);
                    if (value != null) result = this.encrypt(value, true);   
                } catch (GeneralException e) {
                    LOG.error("unable to encrypt strings for: " + name);
                }
                return result;
            });
      }
        
    }

    /**
     * This is called by saveObject and importObject.
     *
     * Massage objects before saving them. Added just for the ManagedAttribute has since it is
     * too easy to forget and we can generate it.  Like runPostPersistVisitor this isn't completely
     * accurate since you can flush dirty things without calling saveObject, but it will handle most
     * cases for system code.
     */
    private void runPrePersistVisitor(SailPointObject obj) throws GeneralException {
        if (obj instanceof ManagedAttribute) {
            // should make system code do this, but it is too easy to miss
            ManagedAttribute att = (ManagedAttribute)obj;
            if (att.getHash() == null) {
                att.setHash(ManagedAttributer.getHash(att));
            }
        }
    }
    
    /**
     * Run the save/update "visitor".  
     * Obviously not a visitor but this is where it would go if
     * we decide we need one.  May also want pre/post visitors.
     * 
     * In practice this isn't very reliable since Hibernate will
     * flush every dirty object in the cache whether you call
     * saveObject or not.  This can lead to errors of ommision
     * in the UI tier that are hard to detect.  For cache updates
     * we have to wait for the Hibernate afterCommit interceptor
     * since we only want to update caches if the commit actually
     * succeeds.  
     * !! should we be deferring the Remediationitem assimilation
     * too?
     */
    private void runPostPersistVisitor(SailPointObject obj) 
        throws GeneralException {

        if (obj instanceof RemediationItem) {
            // Let the Certificationer assimilate remediations.
            Certificationer certificationer = new Certificationer(this);
            certificationer.assimilate((RemediationItem) obj);
        }
        else if (obj instanceof Request) {
            // tickle the request manager thread?
            // this would be very convenient for testing
        }
        else if (obj instanceof Application) {
            // kludge for SM clusters, find a better way to do this...
            Application app = (Application)obj;
            ProvisioningConfig pc = app.getProvisioningConfig();
            if (pc != null) {
                Script script = pc.getClusterScript();
                if (script != null) {
                    try {
                        Map<String,Object> args = new HashMap<String,Object>();
                        args.put("application", app);
                        Object value = runScript(script, args);
                        String svalue = (value != null) ? value.toString() : null;
                        app.setCluster(svalue);
                    }
                    catch (Throwable t) {
                        LOG.error("Exception running cluster script");
                        LOG.error(t);
                    }
                }
            }
        }
    }

    public void importObject(SailPointObject obj) throws GeneralException {

        // name and description protected from unsafe HTML chars. see IIQSAW-3121
        runPrePersistVisitor(obj);
        
        // encrypt things left as plain text in XML
        encryptSecrets(obj);

        _store.importObject(obj);

        runPostPersistVisitor(obj);
    }
    
    public void removeObject(SailPointObject obj) throws GeneralException {

        // TODO: do we want any built in authorization or
        // safeguards here?
        _store.removeObject(obj);
    }

    /**
     * Bulk remove objects from the database using the Filter defined
     * in the query options. This method only supports a few SailPoint
     * object types and most of them do not suport the notion of 
     * bulk delete.
     */
    public <T extends SailPointObject> void removeObjects(Class<T> cls, 
                                                          QueryOptions options)
        throws GeneralException {

        // TODO: do we want any built in authorization or
        // safeguards here?
        _store.removeObjects(cls, options);
    }

    /**
     * Return either the impersonator (if set) or the Identity with the name
     * returned by getUserName().
     */
    Identity getUser() throws GeneralException {
        // If the impersonator is set, return him.
        if (null != _impersonator) {
            return _impersonator;
        }
        
        // No impersonator, try to load the user by name.
        if ((null == _user) && (null != _userName)) {
            _user = getObjectByName(Identity.class, _userName);
        }
        return _user;
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls) 
        throws GeneralException {
        return this.getObjects(cls, null);
    }

    public <T extends SailPointObject> List<T> getObjects(Class<T> cls, QueryOptions options) 
        throws GeneralException {
        ResultScoper scoper = new ResultScoper(this, options);
        return (scoper.isReturnAnyResults()) ?
            _store.getObjects(cls, scoper.getScopedQueryOptions()) : Collections.EMPTY_LIST;
    }

    @Override
    public <T extends SailPointObject> Iterator<T> search(Class<T> cls, QueryOptions options)
        throws GeneralException {
        ResultScoper scoper = new ResultScoper(this, options);
        return (scoper.isReturnAnyResults()) ?
            _store.search(cls, scoper.getScopedQueryOptions()) : Collections.EMPTY_LIST.iterator();
    }

    @Override
    public <T extends SailPointObject> Iterator<Object[]> search(Class<T> cls, QueryOptions options, List<String> properties) throws GeneralException {
        ResultScoper scoper = new ResultScoper(this, options);
        return (scoper.isReturnAnyResults()) ?
            _store.search(cls, scoper.getScopedQueryOptions(), properties) : Collections.EMPTY_LIST.iterator();
    }

    @Override
    public Iterator search(String query, Map<String,Object> args, QueryOptions options)
        throws GeneralException {
        // Can't add scoping query options here ... the caller will have to
        // implement it themselves if desired.
        return _store.search(query, args, options);
    }

    public int update(String query, Map<String,Object> args) 
        throws GeneralException {
        
        return _store.update(query, args);
    }
    
    
    public int countObjects(Class cls, QueryOptions ops)
        throws GeneralException {
        ResultScoper scoper = new ResultScoper(this, ops);
        return (scoper.isReturnAnyResults()) ?
            _store.countObjects(cls, scoper.getScopedQueryOptions()) : 0;
    }

    public void attach(SailPointObject obj) throws GeneralException {
        _store.attach(obj);
    }

    public void decache(SailPointObject object) throws GeneralException {
        // If this is our cached user, clear it.
        if (_user == object) {
            _user = null;
        }
        _store.decache(object);
    }

    public void decache() throws GeneralException {
        // Clear our cached user.
        _user = null;
        _store.decache();
    }
    
    public void clearHighLevelCache()
        throws GeneralException
    {
        _store.clearHighLevelCache();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Encapsulated Serivces
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render and send an email notification.
     * Setup a retry request if the notification fails.
     *
     * With the introduction of Velocity templates we created a
     * probelm for retries.  Originally if the EmailNotifier threw
     * RetryableEmailException we could create a Request and put the
     * database id of the template and the entire EmailOptions in it
     * then rerender the template later.
     *
     * Now however, the _variables map in EmailOptions may contain all
     * sorts of complex objects including an InternalContext which can't
     * be serialized.  We could filter that out, but there may still
     * be things like Identity, Policy, SODConstraint, PolicyViolation, 
     * and anything else from our model in there.  
     *
     * Rather than try to save all this and rerender the template later, 
     * we're going to fully render it now and pass the compiled template
     * into the Request.  We won't pass EmailOptions to the Request but we
     * do have to pass the List<EmailFileAttachment> it contains.  
     * !! How does a binary attachment serialize as XML anyway?
     * 
     * The only potential problems I can see are that you might
     * want to render the template differently if you know it 
     * has been retried but that seems like an obscure case.    
     *
     */
    public void sendEmailNotification(EmailTemplate template, EmailOptions options)
        throws GeneralException 
    {

        // We used to get an EmailNotifier from the environment that was
        // injected by spring.  Now, the EmailNotifier is based on system
        // config attributes since it can be edited through the UI.
        EmailNotifier em = getEmailNotifier();
        
        if (em != null) {
            EmailTemplate email = template.compile(this, getConfiguration(), options);
            Boolean send = em.sendImmediate();
            
            /*
             * This set of conditionals is admittedly confusing.  When we're coming
             * from the EmailRequestExecutor, we need to ignore the system configuration's
             * sendImmediate settings to avoid a cycle where we repeatedly process
             * and requeue notifications.  Ideally the passed in options would always 
             * take precedence, but too many existing services rely on the original
             * behavior where the system configuration setting always wins.  For this 
             * reason, the EmailOptions has a special forceImmediate override that always
             * take precedence.  See bug IIQSAW-3466
             */
            if (options != null) {
                boolean forceIt = options.isForceImmediate();
                if (forceIt) {
                    send = true;
                }
            }
            if ( send == null && options != null ) {
                send = options.sendImmediate();
            }
            if ( send != null && send) {
                _emailSender.send(this, em, email, options);
            } else {
                queueMessage(email, options);
            }
        }
    }
    
    private static class EmailSender
    {
        private volatile int _emailGap = -1;// uninitialized value
        private volatile long _lastSentTime = 0;
        private static final Lock EMAIL_LOCK = new ReentrantLock();
        
        /*
         * If this isn't a retry attempt, add a request to retry.
         * If it is a retry then there is already a request for it, do
         * not add another one.
         * Log the failure regardless and re-throw the exception.
         */
        private static void logAndRetry(InternalContext context, RetryableEmailException e, EmailOptions options, EmailTemplate template)
                throws GeneralException {
            String to = template.getTo();
            String subj = template.getSubject();
            context.logEmailFailure(to, subj, e.toString());
            if (options == null || !options.isNoRetry()) {
                context.queueMessage(template, options);
                throw e;
            } else {
                throw e;
            }
        }

        private EmailSender() {
        }
        
        private void initEmailGap(SailPointContext context) throws GeneralException{
            
            Integer val = context.getConfiguration().getInteger(Configuration.DEFAULT_EMAIL_GAP);
            if (val == null) {
                _emailGap = 0;
            } else {
                _emailGap = val.intValue();
            }

            InternalContext.LOG.info("EmailGap: " + _emailGap);
        }

        public void send(InternalContext context, EmailNotifier notifier, EmailTemplate template, EmailOptions options)
            throws GeneralException {

            try {
                int lockTimeoutMinutes = Configuration.getSystemConfig().getAttributes().getInt(Configuration.EMAIL_THREAD_LOCK_TIMEOUT_MIN, 5);
                boolean isLocked = EMAIL_LOCK.tryLock(lockTimeoutMinutes, TimeUnit.MINUTES);
                if (!isLocked) {
                    throw new RetryableEmailException(options.getTo(), new GeneralException(Message.error(
                            MessageKeys.NOTIFICATION_FAILED_CANNOT_ACQUIRE_LOCK, options.getTo())));
                }
                initEmailGap(context);

                if (_emailGap == 0) {
                    // no need for throttling
                    sendInternal(context, notifier, template, options);
                    return;
                }

                long currentGap = System.currentTimeMillis() - _lastSentTime;
                if (currentGap < _emailGap) {
                    if (InternalContext.LOG.isDebugEnabled()) {
                        InternalContext.LOG.debug("Sleeping for: " + (_emailGap - currentGap));
                    }
                    try {
                        // Please note that emailNotifierSendImmediately 
                        // property should not be set to true if a defaultEmailGap
                        // is set to non-zero value. That way the main thread will not
                        // sleep here.
                        Thread.sleep(_emailGap - currentGap);
                    } catch (InterruptedException ex) {
                        // do we need to do something here?... prolly not
                        throw new GeneralException(ex);
                    }
                }

                sendInternal(context, notifier, template, options);
            } catch (InterruptedException e) {
                throw new GeneralException(e);
            } catch (RetryableEmailException ree) {
                logAndRetry(context, ree, options, template);
            } catch (Exception e) {
                    // anything else that caused the email to fail
                String to = template.getTo();
                String subj = template.getSubject();
                context.logEmailFailure(to, subj, e.toString());
                throw new GeneralException(e);
            } finally {
                if (((ReentrantLock)EMAIL_LOCK).isHeldByCurrentThread()) {
                    EMAIL_LOCK.unlock();
                }
            }
        }

        /*
         * Callers should catch RetryableEmailException and invoke resend when applicable
         */
        private void sendInternal(InternalContext context, EmailNotifier notifier, EmailTemplate template, EmailOptions options)
            throws GeneralException {
            
            // for logging 
            String to = template.getTo();
            String subj = template.getSubject();
            notifier.sendEmailNotification(context, template, options);
            context.logEmailSent(to, subj);
            _lastSentTime = System.currentTimeMillis();
        }
    }

    /**
     * Retrieve an email notifier as configured in system config to use to send
     * email notifications.  This throws if the system config is incorrectly
     * configured.
     */
    private EmailNotifier getEmailNotifier() throws GeneralException {
        
        EmailNotifier notifier = null;
        Configuration config = getConfiguration();
        
        // Let the unit tests bypass this without having to modify sysconfig
        if (TestEmailNotifier.isEnabled())
            notifier = new TestEmailNotifier();

        if (notifier == null) {
            // new in 6.1, let a class be configured
            String clsname = config.getString(Configuration.EMAIL_NOTIFIER_CLASS);
            if (clsname != null) {
                try {
                    Class cls = Class.forName(clsname);
                    return (EmailNotifier)cls.newInstance();
                }
                catch (Throwable t) {
                    // configuration error, ignore and move on to the type
                    LOG.error("Unable to instantiate EmailNotifier: " + clsname);
                    LOG.error(t);
                }
            }
        }

        if (notifier == null) {
            // Look in the system configuration to figure out which to use.
            String type = config.getString(Configuration.EMAIL_NOTIFIER_TYPE);

            // This is required.  We should have upgraded this already.
            if (null == type) {
                throw new GeneralException(Configuration.EMAIL_NOTIFIER_TYPE +
                                           " not configured in system config.");
            }

            if (Configuration.EMAIL_NOTIFIER_TYPE_SMTP.equals(type)) {
                notifier = new SMTPEmailNotifier();
            }
            else if (isRedirectingType(type)) {
                RedirectingEmailNotifier redirecting = new RedirectingEmailNotifier();
                redirecting.setDelegate(new SMTPEmailNotifier());
                redirecting.setEmailAddress(config.getString(Configuration.REDIRECTING_EMAIL_NOTIFIER_ADDRESS));
                if (Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE.equals(type)) {
                    redirecting.setFileName(config.getString(Configuration.REDIRECTING_EMAIL_NOTIFIER_FILENAME));
                } else {
                    redirecting.setFileName(null);
                }
                notifier = redirecting;
            }
            else {
                throw new GeneralException("Unknown email notifier type: " + type);
            }
        }

        // propagate the global immediate send option but
        // don't override this if the notifier forces it
        if (notifier != null && notifier.sendImmediate() == null) {
            String sendImmediate =
                config.getString(Configuration.EMAIL_NOTIFIER_SEND_IMMEDIATELY);
            if (null != sendImmediate) {
                notifier.setSendImmediate(Util.otob(sendImmediate));
            }
        }

        return notifier;
    }
    
    private boolean isRedirectingType(String type) {
        return (
                Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_EMAIL.equals(type) 
                || 
                Configuration.EMAIL_NOTIFIER_TYPE_REDIRECT_TO_FILE.equals(type));
    }
    
    /** 
     * Queue the message for the Request processor. This is typically
     * done by default to avoid performance problems when there are issues 
     * connecting to the SMTP server.
     */
    private void queueMessage(EmailTemplate email, EmailOptions options) 
        throws GeneralException {

        Configuration config = getConfiguration();
        String defName = config.getString(Configuration.EMAIL_REQUEST_DEFINITION);
        RequestDefinition def = getObjectByName(RequestDefinition.class, defName);
        Request req = new Request(def);
        req.setAttribute(EmailRequestExecutor.TEMPLATE, email);
        if (options != null) {
            req.setAttribute(EmailRequestExecutor.ATTACHMENTS, 
                             options.getAttachments());
            String fileName = options.getFileName();
            if ( fileName != null && fileName.length() > 0 ) {
                req.setAttribute(EmailRequestExecutor.FILE_NAME, fileName);
            }
        }
        
        //store the To address in string1 property for 'target' column on requests.jsf 
        req.setString1AndTruncate(email.getTo());
        req.setName(email.getName());
        // addRequest which will now decache the request after it is persisted
        RequestManager.addRequest(this, req);
    }

    public Object runRule(Rule rule, Map<String,Object> params)
        throws GeneralException
    {
        return runRule(rule, params, null);
    }

    public Object runRule(Rule rule, Map<String,Object> params, List<Rule> libraries)
        throws GeneralException
    {
        Object rv = null;

        // This is dumb, there is no reason why we need Spring to inject
        // an effing RuleRunner here.  I want these to have contexts so they
        // can resolve rule libraries, but they can't given the current
        // interface.  They have to assume the thread-local context
        RuleRunner rr = _env.getRuleRunner();
        if (rr != null) {

            // jsl - give everyone a "context" and "log" if the caller
            // hasn't already.  This is handy for rules run for side
            // effect from the console.

            if (params == null)
                params = new HashMap<String,Object>();

            if (params.get("context") == null)
                params.put("context", this);

            if (params.get("log") == null)
                params.put("log", LOG);

            rv = rr.runRule(rule, params, libraries);

        }
        return rv;
    }

    public Object runScript(Script script, Map<String,Object> params)
        throws GeneralException
    {
        return runScript(script,params,null);
    }

    public Object runScript(Script script, Map<String,Object> params, List<Rule> ruleLibraries)
        throws GeneralException {
        Object rv = null;

        RuleRunner rr = _env.getRuleRunner();
        if (rr != null) {

            if (params == null)
                params = new HashMap<String,Object>();

            if (params.get("context") == null)
                params.put("context", this);

            if (params.get("log") == null)
                params.put("log", LOG);

            rv = rr.runScript(script, params, ruleLibraries);

        }
        return rv;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Authentication
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Attempt pass-through authentication to the resource configured as the
     * authentication source.  Fall back to authenticating against the
     * <code>Identity</code> with the given accountId.  If authentication against
     * the pass-through resource succeeds but cannot be correlated to a
     * <code>Identity</code>, attempt to create a new <code>Identity</code>.
     * <p>
     * Note: This method commits the current transaction.
     * 
     * @param  accountId  The unique identifier of the account on the
     *                    pass-through resource or the <code>Identity</code>.
     * @param  password   The password of the account or user.
     * 
     * @return The authenticated <code>Identity</code>.
     */
    public Identity authenticate(String accountId, String password)
            throws GeneralException, ExpiredPasswordException {

        return _authenticator.authenticate(accountId, password);
    }
    
    public Identity authenticate(String accountId, Map<String, Object> options)
            throws GeneralException, ExpiredPasswordException {

        return _authenticator.authenticate(accountId, options);
    }
        
    private void logEmailSent(String to, String subj) {
        try {   
            Auditor.log(AuditEvent.ActionEmailSent, to, subj);

            commitTransaction();
        }
        catch (Throwable t) {
            // eat logging failures, else Admin night not be
            // able to fix things?  
        }
    }
    
    private void logEmailFailure(String to, String subj, String errorMsg) {
        try {   
            Auditor.log(AuditEvent.ActionEmailFailure, to, subj, errorMsg);

            commitTransaction();
        }
        catch (Throwable t) {
            // eat logging failures, else Admin night not be
            // able to fix things?  
        }
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Hibernate Change Triggers
    //
    // These are called by TriggerInceptor in a rather ugly way.
    // Revisit the handoff of control after the ATT demo.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The transaction is about to be committed.
     *
     * I wanted to create the AuditEvents here under the assumption that
     * they would be added to the transaction, but they apparently aren't.
     * We have to wait until after the transaction commits, then commit 
     * another one.  It may be better to add these on the fly from
     * the onFlushDirty interceptor?
     */
    public void beforeCommit(Set<SailPointObject> creates,
                             Set<SailPointObject> updates,
                             Set<SailPointObject> deletes) {

         // doesn't work...
        //audit(creates, updates, deletes);

    }

    /**
     * The transaction has been committed.
     * We won't be notified if the transaction was rolled back.
     */
    public boolean afterCommit(Set<SailPointObject> creates,
                               Set<SailPointObject> updates,
                               Set<SailPointObject> deletes) {

        afterCommit(creates, Operation.CREATE);
        afterCommit(updates, Operation.UPDATE);
        afterCommit(deletes, Operation.DELETE);

        int count = audit(creates, updates, deletes);

        return (count > 0);
    }

    /**
     * Given a list of objects that have either been created or modified,
     * update the global caches.
     */
    public void afterCommit(Set<SailPointObject> objects, Operation op) {

        // Unused hook for pluggable triggers
        if (objects != null) {

            for (SailPointObject o : objects) {

                // Update various caches when we know something
                // has been committed.  These can't be done in 
                // runPersistVisitor because the object will not
                // have been committed yet
                if (o instanceof Rule && op != Operation.DELETE) {
                    // kludge for bug#3820
                    // It is common for ObjectConfigs to reference Rules but
                    // since they are cached in static fields they won't track
                    // changes to those rules.   Rather than trying to be smart
                    // just reload all ObjectConfigs any time a rule changes.
                    _caches.forceRefreshObjects(this);
                }
                else if (o instanceof Application || o instanceof IntegrationConfig) {
                    // updates to Applications or IntegrationConfigs means we need to
                    // recalculate the IntegrationConfigCache.
                    try {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("IntegrationConfigCache reloadCache set to true due to change to " +  
                                    o.getClass().getSimpleName() + " " + o.getName());
                        }
                        IntegrationConfigFinder.IntegrationConfigCache.markCacheForReload(true);
                    }
                    catch (Throwable t) {
                        LOG.error("Could not modify IntegrationConfigCache reloadCache due to change to" + 
                                o.getClass().getSimpleName() + " " + o.getName());
                        LOG.error(t);
                    }
                }
                else if (o instanceof Cacheable && op != Operation.DELETE) {
                    Cacheable cb = (Cacheable)o;
                    // not all objects of this class may be cacheable
                    if (cb.isCacheable(o)) {

                        try {
                            // bug#7981 Before calling load() on something that may have 
                            // unresolved references have to start a new transaction.  At this
                            // point in the interceptor the transaction is not active.  Failure
                            // to do this will result in "could not initialize proxy - no session"
                            // just startTransaction() didn't work, be safe and refetch
                            decache(o);
                            o = getObjectById(o.getClass(), o.getId());
                            o.load();
                            _caches.update(o);

                            // sigh, system Configuration is more complicated
                            // other things cache parts of it
                            // !! I don't like this, make them go back to the
                            // cached Configuration object it isn't that much slower
                            if (o instanceof Configuration && 
                                Configuration.OBJ_NAME.equals(o.getName())) {
                        
                                // sigh, this one is complicated because other objects
                                // cache parts of it
                                Configuration syscon = (Configuration)o;
                                LockInfo.setConfiguration(syscon);

                                // Services may pull things from the system config
                                Servicer svc = _env.getServicer();
                                svc.reconfigure(this, syscon);
                            }
                            else if (o instanceof ServiceDefinition) {
                                // Sigh, kludge for the unit tests.  When starting
                                // from a clean db importing the ServiceDefinitions for
                                // the first time won't cause changes until the 60 second
                                // refresh period.  Tell the service now.
                                Servicer svc = _env.getServicer();
                                svc.reconfigure(this, (ServiceDefinition)o);
                            }
                        }
                        catch (Throwable t) {
                            LOG.error("Unable to update cache: " + 
                                      o.getClass().getSimpleName() + ":" + 
                                      o.getName());
                            LOG.error(t);
                        }
                    }
                }
            }
        }
    }

    private int audit(Set<SailPointObject> creates,
                      Set<SailPointObject> updates,
                      Set<SailPointObject> deletes) {

        int count = 0;

        if (creates != null) {
            for (SailPointObject o : creates) {
                if (Auditor.log(AuditEvent.ActionCreate, o))
                    count++;
            }
        }
        if (updates != null) {
            for (SailPointObject o : updates) {
                if (Auditor.log(AuditEvent.ActionUpdate, o))
                    count++;
            }
        }
        if (deletes != null) {
            for (SailPointObject o : deletes) {
                if (Auditor.log(AuditEvent.ActionDelete, o))
                    count++;
            }
        }

        return count;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    //////////////////////////////////////////////////////////////////////

    public void enableStatistics(boolean b) {
        _store.enableStatistics(b);
    }

    public void printStatistics() {
        _store.printStatistics();
    }

    public void reconnect() throws GeneralException {
        _store.reconnect();
    }

    public void setPersistenceOptions(PersistenceOptions ops) {
        _store.setPersistenceOptions(ops);
    }
    
    public PersistenceOptions getPersistenceOptions() {
        return _store.getPersistenceOptions();
    }
}
