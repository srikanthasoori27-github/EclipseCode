/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * This task executor will fetch each of the following
 * objects and re-encrypt the parts of the object that
 * are encrpyted.
 * 
 * 1) Identity 
 *    a) password
 *    b) authentication question answers
 *    c) password history
 * 
 * 2) Application
 *    a) fields in config marked secret
 *    b) activity config attributes marked secret
 *    c) target sources where attributes where attributes contain 'password' OR
 *       1) If defined it'll use an attribute in 
 *    
 * 3) IntegrationConfig objects
 *    a) All integration config objects that contain the word "password"
 *    b) If defined it'll use an attribute in the IntegrationConfig.attributes 
 *       that is named 'secretAttributes' that when defined will also 
 *       be re-encrypted.
 *       
 * TODO:
 * 
 *  o Should this be in the server package?
 *  o Use Context or cryptographer
 *  
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.activity.ActivityCollectorFactory;
import sailpoint.api.EncodingUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.DefaultApplicationFactory;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.Attachment;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.service.AttachmentService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class EncryptedDataSyncExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Input arguments
    //
    
    /**
     * Input to indicate not to round trip the application objects.
     */
    public static final String ARG_DISABLE_APPLICATIONS = "disableApplicationSync";
    
    /**
     * Input to indicate not to round trip the identity objects.
     */
    public static final String ARG_DISABLE_IDENTITIES  = "disableIdentitySync";
    
    /**
     * Input to indicate not to round trip the integration config objects.
     */
    public static final String ARG_DISABLE_INTEGRATION_CONFIGS = "disableIntegrationSync";

    /**
     * Input to indicate not to round trip the attachment objects
     */
    public static final String ARG_DISABLE_ATTACHMENTS = "disableAttachmentSync";
    
    
    public static final String ARG_SYNC_IQSERVICE_KEYS = "syncIQServiceKeys";

    /**
     * Input to indicate not to encrypt sync system configuration.
     */
    public static final String ARG_DISABLE_SYSTEM_CONFIGURATION = "disableSysConfigSync";
    
    /**
     * Input to indicate to convert identity secrets to hashing.
     */
    public static final String ARG_CONVERT_IDENTITY_SECRET_TO_HASHING = "convertIdentitySecretToHashing";
    
    //
    // Return attributes
    //

    /**
     * Output variable which indicates the number of applications that
     * were fetched and then saved.
     */
    public static final String VAR_APPLICATIONS_REFRESHED = "applicationsRefreshed";
    
    /**
     * Output variable which indicates the number of identities that
     * were fetched and then saved.
     */
    public static final String VAR_IDENTITIES_REFRESHED = "identitiesRefreshed";
    
    /**
     * Output variable which indicates the number of integration configs
     * that were fetched and then saved.
     */
    public static final String VAR_INTEGRATION_CONFIGS_REFRESHED = "integrationConfigsRefreshed";

    /**
     * Output variable which indicates the number of attachments that were fetched and then saved
     */
    public static final String VAR_ATTACHMENTS_REFRESHED = "attachmentsRefreshed";
    
    /**
     * Output variable which indicated the number of iqservice hosts that
     * were updated with a new key.
     */
    public static final String VAR_IQSERVICE_HOSTS = "iqServiceHostsChanged";
    
    /**
     * Output variable which indicated the number of links that
     * were hashed.
     */
    public static final String VAR_LINKS_HASHED = "linksUpdated";
    
    /**
     * Attribute that can be defined on an IntegrationConfig
     * to tell this task which attributes should be 
     * re-encrypted. 
     */
    private static final String ATTR_IIQ_SECRETS = "IIQSecretAttributes";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log LOG = LogFactory.getLog(EncryptedDataSyncExecutor.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Result object we're leaving things in.
     */
    TaskResult _result;

    //
    // Runtime state
    //

    /**
     * May be set by the task executor to indicate that we should stop
     * when convenient.
     */
    private boolean _terminate;

    //
    // Statistics
    //
    
    /**
     * Again for progress, as we start a type increment this
     * so we can get a percent complete figure.
     */
    int _typesVisited = 0;
    
    /**
     * Variable that helps us with the progress, need to increment
     * this each time we start round trip a new type of object.
     */
    int TOTAL_TYPES = 3;
    
    /**
     * Cached copy of the incoming args to avoid passing them around
     * to each method.
     */
    Attributes<String,Object> _args;
    
    boolean _trace = false;
    
    
    int _iqServicesUpdated;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor() {
        return _monitor;
    }

    private void trace(String msg) {
        LOG.info(msg);
        if (_trace)
            println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Executor
    //
    //////////////////////////////////////////////////////////////////////

    public EncryptedDataSyncExecutor() {  }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _terminate = true;
        return true;
    }
    
    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and disappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context,
                        TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {


        _context = context;
        _result = result;
        _monitor = new TaskMonitor(context, result);
        _args = args;
        _trace = Util.getBoolean(_args, "trace");
        
        boolean toHashing = Util.getBoolean(_args, ARG_CONVERT_IDENTITY_SECRET_TO_HASHING);
        boolean doHashing = toHashing && EncodingUtil.isHashingEnabled(_context);
        if (toHashing && !EncodingUtil.isHashingEnabled(_context)) {
            _result.addMessage(new Message(Message.Type.Warn, MessageKeys.TASK_WARN_HASHING_NOT_ENABLED));
        }
        
        // up front check to see if there is any reason to 
        // sync before we go through possibly go through all
        // of the identity and application objects
        String str = _context.encrypt("foobar");
        boolean doEncryption = true;
        if ( isEncrypted(str) && isUsingDefaultKey(str)) {
            _result.addMessage(new Message(Message.Type.Warn, MessageKeys.TASK_WARN_SYNC_UNNECESSARY));
            doEncryption = false;
        }
        
        try {
            getTaskMonitor().updateProgress("Encryption/Hashing sync starting...", 0, true);
            if ( doEncryption && enabled(ARG_DISABLE_APPLICATIONS) )  {
                ++_typesVisited;
                int apps = roundtrip(Application.class);
                result.setAttribute(VAR_APPLICATIONS_REFRESHED, Util.itoa(apps));
            }
            if ( doHashing || (doEncryption && enabled(ARG_DISABLE_IDENTITIES)) ) {   
                ++_typesVisited;
                int identities = roundtrip(Identity.class);
                result.setAttribute(VAR_IDENTITIES_REFRESHED, Util.itoa(identities));
            }
            if ( doEncryption && enabled(ARG_DISABLE_INTEGRATION_CONFIGS) ) {
                ++_typesVisited;
                int configs = roundtrip(IntegrationConfig.class);
                result.setAttribute(VAR_INTEGRATION_CONFIGS_REFRESHED, Util.itoa(configs));
            }
            
            if ( doEncryption && enabled(ARG_DISABLE_SYSTEM_CONFIGURATION)) {
                ++_typesVisited;
                // system configuration - Currently the only attribute that is encrypted is
                // Configuration.SmtpConfiguration.Password
                handleConfiguration(Configuration.OBJ_NAME, Arrays.asList(Configuration.SmtpConfiguration.Password));

                // TODO:  If this ends up being a large list of config objects
                // and values, we should probably add a better mechanism for
                // keeping track of what should be re-encrypted (like apps have
                // for example), but since there is only one at present, I will
                // just put it here.
                handleConfiguration(Configuration.IAI_CONFIG, Arrays.asList(Configuration.IAI_CONFIG_CLIENT_SECRET));
                handleConfiguration(Configuration.FAM_CONFIG, Arrays.asList(Configuration.FAM_CONFIG_PASSWORD, Configuration.FAM_CONFIG_CLIENT_SECRET));
            }

            if (doEncryption && enabled(ARG_DISABLE_ATTACHMENTS)) {
                ++_typesVisited;
                int attachments = roundtrip(Attachment.class);
                result.setAttribute(VAR_ATTACHMENTS_REFRESHED, Util.itoa(attachments));
            }
            
            if  ( doEncryption && enabled(ARG_SYNC_IQSERVICE_KEYS ) ) {
                result.setAttribute(VAR_IQSERVICE_HOSTS, Util.itoa(_iqServicesUpdated));
            }
            if ( doHashing ) {
                int links = roundtrip(Link.class);
                result.setAttribute(VAR_LINKS_HASHED, Util.itoa(links));
            }
            
        } catch (Throwable t) {
            LOG.error("Error executing Key Sync.", t);
            result.addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_EXCEPTION, t));
        } finally {
            result.setTerminated(_terminate);
        }
        getTaskMonitor().updateProgress("Encryption sync task complete.", 100, true);
        context.saveObject(result);
        context.commitTransaction();
        _context.decache();
    }    

    /**
     * Re-persist all of the objects of a type so the encryption
     * keys will be updated using the newest key.
     * 
     * @param clazz
     * @return
     * @throws GeneralException
     */
    private int roundtrip(Class<? extends SailPointObject> clazz) 
        throws GeneralException {
        
        int processed = 0;
        int total = _context.countObjects(clazz, null); 
        getTaskMonitor().updateProgress("Starting type ["+clazz.getCanonicalName()+"]");

        if ( total > 0  ) {
            QueryOptions ops = new QueryOptions();
            ops.setCloneResults(true);
            Iterator<Object[]> rows = 
                _context.search(clazz, ops, Arrays.asList("id"));
            if ( rows != null ) {
                while ( rows.hasNext() ) { 
                    if ( this._terminate  ) {
                         new GeneralException(new Message(""));
                         throw new GeneralException(MessageKeys.TASK_EXCEPTION_TERMINATED);
                    }
                    Object[] row = rows.next();
                     if ( row != null ) {
                        String id = (String)row[0];
                        if ( id != null ) {
                            SailPointObject obj = 
                                _context.getObjectById(clazz, id);
                            if ( obj != null ) {                                
                                if ( clazz.equals(Identity.class) ) {
                                   handleIdentity((Identity)obj);
                                } else
                                if ( clazz.equals(Application.class) ) {
                                    handleApplication((Application)obj);
                                } else 
                                if ( clazz.equals(IntegrationConfig.class) ) {
                                    handleIntegrationConfig((IntegrationConfig)obj);
                                } else 
                                if ( clazz.equals(Link.class) ) {
                                    //do nothing here,
                                    // _context.saveObject(link) will 
                                    // convert the encrypted password history to hashing
                                } else
                                if ( clazz.equals(Attachment.class)) {
                                    handleAttachment((Attachment)obj);
                                }
                                processed++;
                                
                                float totalProgress = ((float)processed/(float)total)* 100;
                                String objName = obj.getName() == null ? "" : obj.getName();
                                String progress = "Updating object ["+clazz.getCanonicalName()+"] " + objName + ". ( TypeProgress = " + totalProgress + " "  + _typesVisited + " of " +TOTAL_TYPES + ").";
                                trace(progress);
                                getTaskMonitor().updateProgress(progress);
                                
                                // round tip the object to re-persist the encrypted
                                // elements
                                _context.saveObject(obj);
                                _context.commitTransaction();
                                _context.decache(obj);
                            }
                        }
                    }
                } 
            }
            _context.decache();            
        }
        return total;
    }
    
    /*
     * Re-encrypt the encrypted data on each identity 
     * 
     *   1) Password
     *   2) Authentication Question Answers     
     *   3) PasswordHistories
     * 
     * @param id
     * @throws GeneralException
     */
    private void handleIdentity(Identity id) 
        throws GeneralException {
        
        if ( id == null ) return;
        
        String password = id.getPassword();
        if ( isEncrypted(password)) {
            String decrypted = _context.decrypt(password);
            if ( decrypted != null ) {
                id.setPassword(_context.encrypt(decrypted));    
            }
        }
        
        List<AuthenticationAnswer> answers = id.getAuthenticationAnswers();
        if ( answers != null ) {
            for (AuthenticationAnswer answer : answers) {
                String a = answer.getAnswer();
                if ( isEncrypted(a)) {
                    String ans = _context.decrypt(a);
                    if ( ans != null )
                        ans = _context.encrypt(ans);
                    answer.setAnswer(ans);                    
                }
            }
        }
        
        String historyStr = id.getPasswordHistory();
        if ( historyStr != null ) {
            // djs: why doesn't a helper on identity return/take a list 
            List<String> histories = Util.csvToList(historyStr);
            if ( histories != null ) {
                List<String> newList = new ArrayList<String>();
                for ( String history  : histories ) {
                    if ( history != null ) {
                        if ( isEncrypted(history) ) {
                            String decrypted = _context.decrypt(history);
                            if ( decrypted != null ) {
                                newList.add(_context.encrypt(decrypted));
                            }
                        }
                    }
                }                
                id.setPasswordHistory(Util.listToCsv(newList));
            }
        }
    }
    
    /*
     * Re-encrypt all of the configuration attributes that store 
     * an encrypted values and are marked secret in the attribute
     * definitions of the application.
     */
    private void handleApplication(Application app) 
        throws GeneralException {
        
        if ( app == null ) return;  
        
        // Base attribute definitions
        Attributes<String,Object> attrs = app.getAttributes();
        if ( !Util.isEmpty(attrs) ) {
            String template = app.getTemplateApplication();
            if ( template != null ) {
                List<AttributeDefinition> defs = DefaultApplicationFactory.getDefaultConfigAttributesByTemplate(app.getTemplateApplication());
                encryptSecretAttrs(attrs,defs);
            }
        }
        
        //App-specific encrypted fields
        List<String> encryptedAttrs = ProvisioningPlan.getSecretProvisionAttributeNames();
        List<String> appSpecified = app.getEncrpytedConfigAttributes();
        Set<String> combinedSpecified = new HashSet<String>();
        if(appSpecified != null) {
            combinedSpecified.addAll(appSpecified);
        }
        //Let's also look at the app's template's encrypted attrs in case the app differs from the template
        Application templateApp = DefaultApplicationFactory.getTemplateByName(app.getTemplateApplication());
        if(templateApp != null) {
            List<String> templateSpecified = templateApp.getEncrpytedConfigAttributes();
            if ( templateSpecified != null ) {
                combinedSpecified.addAll(templateSpecified);
            }
        }
        
        if(!Util.isEmpty(combinedSpecified)) {
            encryptedAttrs.addAll(combinedSpecified);
        }

        
        for ( String name : encryptedAttrs ) {
            encryptAppConfigAttr(app, name); 
        }  
        
        // Activity Datasources
        List<ActivityDataSource> sources = app.getActivityDataSources();
        if ( sources != null ) {
            for ( ActivityDataSource source : sources) {
                if ( source == null ) continue;
                List<AttributeDefinition> sdefs = ActivityCollectorFactory.getDefaultConfigAttributes(source.getCollector());
                Attributes<String,Object> sattrs = source.getConfiguration();  
                encryptSecretAttrs(sattrs, sdefs);
            }
        }
        
        // targetSource config
        List<TargetSource> targetSources = app.getTargetSources();
        if ( sources != null ) {
            for ( TargetSource targetSource : targetSources ) {
                if ( targetSource != null ) {
                    Attributes<String,Object> tattrs = targetSource.getConfiguration();
                    encryptPasswordAttrs(tattrs);
                }
            }
        }
    }
    
    /**
     * Encrypt and store the named attribute onto the
     * application's configuraiton.  Ugh, taken from InternalContext with a minor mod.
     * 
     * @param app
     * @param name
     * @throws GeneralException
     */
    private void encryptAppConfigAttr(Application app, String name) 
        throws GeneralException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("re-encrypting " + name + " for application " + ((app != null) ? app.getName() : null));
        }
        MapUtil.putAll(app.getAttributes(), name, (x) -> {
            String result = null;
            try {
                String strVal = Util.otos(x);
                if (strVal != null && isEncrypted(strVal)) {
                    String decrypted = _context.decrypt(strVal);   
                    result = _context.encrypt(decrypted);
                }
            } catch (GeneralException e) {
                LOG.error("unable to decrypt/encrypt strings for: " + name);
            }
            return result;
        });
    }
    
    /**
     * Encrypt any values stored on the IntegrationConfig that are
     * encrypted.
     * 
     * Allow a configuration attribute named 'secretAttributes' to specify
     * a csv list of attribute names that should be encrypted.
     * 
     * @param config
     * @throws GeneralException
     */
    private void handleIntegrationConfig(IntegrationConfig config) throws GeneralException {
        if ( config != null ){
            Attributes<String,Object> attrs = config.getAttributes();
            if ( attrs != null ) {
                encryptPasswordAttrs(attrs);
            }
        }
    }
    
    /**
     * Encrypt any values stored on Configuration objects that are
     * encrypted.
     */
    private void handleConfiguration(String name, List<String>encryptedKeys) throws GeneralException {
        if (LOG.isInfoEnabled()) {
            LOG.info("Updating Configuration '" + name + "'");
        }

        boolean present = false;

        Configuration config = _context.getObjectByName(Configuration.class, name);
        if (config == null) {
            if (LOG.isInfoEnabled()) {
                LOG.info(String.format("Could not find configuration '%s'. This may be expected.", name));
            }
            return;
        }

        for (String encryptedKey : encryptedKeys) {
            String value = config.getString(encryptedKey);
            // This will decrypt, then encrypt the value.  If the value
            // was already plain text, that's fine, but it will now end
            // up encrypted.
            if (value != null) {
                String decrypted = value;
                if(!isEncrypted(value)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Encrypting configuration object '");
                    sb.append(name);
                    sb.append("' value for key '");
                    sb.append(encryptedKey);
                    sb.append("' which was previously unencrypted");
                    LOG.info(sb.toString());
                } else {
                    decrypted = _context.decrypt(value);
                }
                config.put(encryptedKey, _context.encrypt(decrypted));
                present = true;
            }
        }

        if (present) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Saving encoded configuration values");
            }
            _context.saveObject(config);
            _context.commitTransaction();
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Updated Configuration '" + name + "'");
        }
    }

    private void handleAttachment(Attachment attachment) throws GeneralException {
        if (attachment != null) {
            byte[] decryptedContent = AttachmentService.decrypt(attachment.getContent(), _context);
            if (decryptedContent != null) {
                attachment.setContent(AttachmentService.encrypt(decryptedContent, _context));
            }
        }
    }
      
    /**
     * Use the AttributeDefinition SECRET_TYPE to indicate if we should 
     * encrypt the values.
     * 
     * @param attrs
     * @param defs
     * @throws GeneralException
     */
    @Untraced
    private void encryptSecretAttrs(Attributes<String,Object> attrs, List<AttributeDefinition> defs )
        throws GeneralException {
        
        if ( !Util.isEmpty(attrs) ) {
            Set<String> keys = attrs.keySet();
            if ( keys != null ) {
                for ( String key : keys ) {
                    // check attribute definition
                    if ( isSecret(defs, key) ) {
                        Object val =  attrs.get(key);
                        if ( val == null ) continue;                        
                        if ( val instanceof String ) {
                            String str = (String)val;
                            if ( str != null ) {                                
                                if ( isEncrypted(str) ) {
                                    String decrypted = _context.decrypt(str);
                                    if ( decrypted != null)
                                        attrs.put(key, _context.encrypt(decrypted));
                                }                                
                            }
                        }
                    }
                }
            }
        }        
    }
    

    /**
     * For cases where we don't have attribute definitions to cross
     * reference for "secret" assume all attributes with "password" 
     * in the name attributes should be encrypted.
     * 
     * Otherwise, allow an attribute to be defined name IIQSecretAttributes
     * that indicates list of the attribute names that should be 
     * encrypted.
     * 
     * We also dig into any nested Maps and do simmular checks.  The  
     * RemedyIntegrationConfig has a list of Map configurations 
     * (one per operation).
     * 
     * @param attrs
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    @Untraced
    private void encryptPasswordAttrs(Map<String,Object> attrs) 
        throws GeneralException {
        
        if ( !Util.isEmpty(attrs) ) {
            List<String> attributeNames = (List<String>)attrs.get(ATTR_IIQ_SECRETS);
            if ( attributeNames == null )
                attributeNames = new ArrayList<String>();
            
            Set<String> keys = attrs.keySet();
            if ( keys != null ) {
                for ( String key : keys ) {
                    // check attribute definition
                    if ( key == null ) continue;  
                    Object val =  attrs.get(key);
                    if ( isWorthy(attributeNames, key) ) { 
                        if ( val == null ) continue;                        
                        if ( val instanceof String ) {
                            String str = (String)val;
                            if ( str != null ) {                                
                                if ( isEncrypted(str) ) {
                                    // only change the value if it was already encrypted
                                    String decrypted = _context.decrypt(str);
                                    if ( decrypted != null ){
                                        // re-encrypt using the newest key
                                        attrs.put(key, _context.encrypt(decrypted));
                                    }
                                }                           
                            }                               
                        }
                    }                    
                    if ( val != null && val instanceof Map ) {
                        Map<String,Object> nested = (Map<String,Object>)val;
                        encryptPasswordAttrs(nested);
                    }
                }                  
            }
        }                    
    }
       
    /**
     * Dig through the attribute definitions and find the
     * attribute we are dealing with, if marked secret
     * return true. 
     * 
     * @param defs
     * @param attrName
     * @return
     */
    private boolean isSecret(List<AttributeDefinition> defs, String attrName) {
        if ( defs != null ) {
            for ( AttributeDefinition def : defs ) {
                // ignore nulls;
                if ( def == null ) continue;                
                if ( Util.nullSafeCompareTo(def.getName(), attrName) == 0 ) {
                    return ( Util.nullSafeCompareTo( def.getType(), AttributeDefinition.TYPE_SECRET) == 0 ) ? true : false; 
                }
            }
        }
        return false;
    }
    
    // djs: this is from Crytographer
    @Untraced
    private boolean isEncrypted(String src) throws GeneralException {        
        return EncodingUtil.isEncrypted(src);
    }
    
    private int parseInt(String src) {        
        int val = 0;        
        int colon = src.indexOf(":");
        if (colon > 0 && colon < (src.length() - 1)) {
            String id = src.substring(0, colon);
            // Must contain only digits or one of the
            // special algorithm names.  Util.atoi returns zero
            // for non-numeric strings, there is no id zero
            if ( !id.equals("ascii") ) 
                val = Util.atoi(id);
        }        
        return val;
    }
    
    /*
     * Check for the given NEGATIVE argument along with the terminate
     * flag to indicate if something is enabled.
     *  
     * @param arg
     * @return boolean
     */
    private boolean enabled(String arg) { 
        if  (( _terminate || Util.getBoolean(_args, arg) ) )  {
            return false;
        }
        return true;
    }
    
    /**
     * If the index of the key is 1 then we know we are
     * using the default key.
     * 
     * @param str
     * @return
     */
    private boolean isUsingDefaultKey(String str) {
        if ( str != null && str.contains(":") ) {
            int index = parseInt(str);
            if ( index == 1 )
                return true;
        }
        return false;
    }
    
    /**
     * 
     * If there is a defined list of names let it determine
     * the behavior, otherwise if the attribute name
     * contains a password consider it worthy.
     * 
     * @param secrets
     * @param name
     * @return
     */
    private boolean isWorthy(List<String> secrets, String name) {        
        if ( name != null ) {
            if ( Util.size(secrets) > 0 ) {
                if ( secrets.contains(name) ) { 
                    return true;
                }
            } else {
                // If there is a list of secrets let that
                // define ALL
                String lcKey = name.toLowerCase();                    
                if ( (lcKey.indexOf("password" ) != -1 ) ||
                        (lcKey.indexOf("client_secret") != -1) ||
                        (lcKey.indexOf("refresh_token") != -1) ||
                        (lcKey.indexOf("oauthbearertoken") != -1) ) {
                    return true;   
                }
            }
        }
        return false;
    }
}


