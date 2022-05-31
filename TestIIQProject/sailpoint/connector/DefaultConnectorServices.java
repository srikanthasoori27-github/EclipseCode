/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import openconnector.ConnectorServices;
import openconnector.OpenMessagePart;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLReferenceResolver;

/**
 * A ConnectorServices implementation for the things we are currently doing with direct access to
 * sailpoint packages.
 * It provides services from sailpoint api and tools like running rules, database lockups etc.
 * Along with an Application [sailpoint.connector] or ConnectorConfig [openconnector], Connectors
 * would also get ConnectorServices implementation.
 *
 * Created by ketan.avalaskar.
 */
public class DefaultConnectorServices implements ConnectorServices {
    
    
    private static Log log = LogFactory.getLog(DefaultConnectorServices.class);
    private static final String FETCH_ATTRIBUTES_METHOD = "getAttributes";

    ////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////
    private HashMap<String,Rule> _ruleCache;

    ////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////
    public DefaultConnectorServices() {
        _ruleCache = new HashMap<String,Rule>();
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Rule runner service
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Run a rule and return the result or null if there is no result.
     *
     * @param rule
     *            The Rule name text Or entire Rule object to run.
     * @param ruleContext
     *            A name/value map of parameters to pass into the rule.
     *
     * @return The result of the execution of the Rule, or null if there is no
     *         result.
     */
    public Object runRule(Object rule, Map<String, Object> ruleContext) throws GeneralException {
        Object o = null;

        if (rule instanceof Rule) {
            o = runRule((Rule) rule, ruleContext);
        } else if (rule instanceof String) {
            o = runRule((String) rule, ruleContext);
        } else {
            throw new GeneralException("Invalid argument detected. Expected Rule object Or name of Rule.");
        }

        return o;
    }

    /**
     * Run a rule and return the result or null if there is no result.
     *
     * @param rule        Entire Rule object to run.
     * @param ruleContext A name/value map of parameters to pass into the rule.
     *
     * @return The result of the execution of the Rule, or null if there is no
     *         result.
     */
    private Object runRule(Rule rule, Map<String, Object> ruleContext) throws GeneralException {
        return SailPointFactory.getCurrentContext().runRule(rule, ruleContext);
    }

    /**
     * Run a rule and return the result or null if there is no result.
     *
     * @param rule
     *            The Rule name to run.
     * @param ruleContext
     *            A name/value map of parameters to pass into the rule.
     *
     * @return The result of the execution of the Rule, or null if there is no
     *         result.
     */
    public Object runRule(String ruleName, Map<String, Object> ruleContext)
            throws GeneralException {

        log.debug("Running rule:" + ruleName);

        Rule rule = _ruleCache.get(ruleName);
        if (rule == null) {

            rule = SailPointFactory.getCurrentContext().
                    getObjectByName(Rule.class, ruleName);
            _ruleCache.put(ruleName, rule);
        }

        if (null == rule) {
            throw new GeneralException("Rule: " + ruleName + " not" + " found.");
        }

        return runRule(rule, ruleContext);
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // SailPoint Object reader service
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Retrieve an object by id or name.
     * Ideally instead of using this method, Connectors should use getAttributeValue() because
     * usually it is particular attribute that Connectors are interested in instead of an entire
     * Object.
     * Not useful for openconnectors as it cannot have dependency on SailPointObjects.
     *
     * @param cls      - Class that represents SailPointObject.
     * @param idOrname - Id or name of the object.
     *
     * @return - SailPointObject by given name.
     */
    @Override
    public Object getObject(Class cls, String idOrname) throws GeneralException {
        return SailPointFactory.getCurrentContext().getObject(cls, idOrname);
    }

    /**
     * Returns the attribute-value key-pair from the requested {@link SailPointObject}. The <tt>searchAttributes</tt>
     * should always produce a "unique/ single" search result. <br/>
     * <p>
     * <b>Note:</b> In case <tt>zero</tt> or <tt>more than one</tt> results are produced, then it will be treated as
     * erroneous condition. Also, the function returns an empty map in case <tt>Attributes</tt> are not present for the
     * object or the attributes mentioned in the <tt>attributes</tt> parameter are not present.
     * </p>
     * 
     * @param clazz
     *            Class object of the requested object
     * @param searchAttributes
     *            Map of attributes to be used for search
     * @param attributes
     *            <tt>List</tt> of attribute values requested
     * @return {@link Map} of attributes and their respective values
     * @throws GeneralException
     * @see DefaultConnectorServices#getObject(Class, String)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, Object> getAttributes(Class clazz,
            Map<String, Object> searchAttributes, List<String> attributes)
            throws GeneralException {
        // This function is the implementation of getAttributeValue as mentioned in the
        // javadoc for the getObject(...) method. This also extends the implementation
        // of the getObject(...) by performing search based on multiple search attributes.

        // perform basic validation. If either of the following arguments are null or empty
        // then the search will fail.
        if (null == clazz || Util.isEmpty(searchAttributes)
                || Util.isEmpty(attributes)) {
            log.debug(
                    "Invalid parameters received to fetch the details. Either of the "
                            + "required parameters: className, searchAttributes or attributes"
                            + " are either null or empty.  ");
            throw new GeneralException("Invalid parameters received.");
        }

        Map<String, Object> attributeValueMap = Collections.emptyMap();

        // Build Query options to search for the object.
        QueryOptions qop = new QueryOptions();

        // build the Filters
        for (Entry<String, Object> searchAttr : searchAttributes.entrySet()) {
            String searchKey = searchAttr.getKey();
            Object searchKeyValue = searchAttr.getValue();
            if (Util.isNotNullOrEmpty(searchKey) && (null != searchKeyValue)) {
                qop.add(Filter.eq(searchKey, searchKeyValue));
            }
        }

        if (qop.getFilters().isEmpty()) {
            // this means no valid filters were added...
            throw new GeneralException(
                    "Could not create valid filters. Invalid searchAttributes received.");
        }

        List<SailPointObject> resultSet = SailPointFactory.getCurrentContext()
                .getObjects(clazz, qop);

        if (Util.isEmpty(resultSet)) {
            throw new GeneralException("Search result was empty.");
        }
        if (resultSet.size() > 1) {
            throw new GeneralException(
                    "Expected search to generate a unique result, but got multiple entries.");
        }

        Object object = resultSet.get(0);
        Method getAttributesMethod = null;
        Attributes<?, ?> objectAttributes = null;
        try {
            getAttributesMethod = clazz.getMethod(FETCH_ATTRIBUTES_METHOD);

            objectAttributes = (Attributes<?, ?>) getAttributesMethod
                    .invoke(object);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GeneralException(
                    "Error while retrieving the: " + FETCH_ATTRIBUTES_METHOD
                            + " on the class: " + clazz.getName(),
                    e);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new GeneralException("Exception occurred while invoking: "
                    + FETCH_ATTRIBUTES_METHOD, e);
        }

        if (null != objectAttributes && !objectAttributes.isEmpty()) {

            log.debug("Adding the requested attributes for: " + clazz);
            attributeValueMap = new HashMap<>();

            for (String attribute : attributes) {
                if (Util.isNotNullOrEmpty(attribute)) {
                    attributeValueMap.put(attribute,
                            objectAttributes.get(attribute));
                }
            }

        } else {
            log.debug(
                    "Attributes on the object are null or empty. Returning empty map");
        }

        return attributeValueMap;
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Application configuration attribute manipulation services
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Save attribute in the Application.
     *
     * @param appName - Name of the application.
     * @param atrName - Name of the attribute.
     * @param value   - Value of the attribute.
     */
    @Override
    public void setApplicationAttribute(String appName, String atrName, Object value)
            throws GeneralException {
        Map<String, Object> atrMap = new HashMap<>();
        atrMap.put(atrName, value);
        setApplicationAttributes(appName, atrMap);
    }

    /**
     * Save attribute map in the Application.
     * Accept Map of attributes so that locking will happen only once.
     *
     * @param appName - Name of the application.
     * @param atrMap  - Map of attributes
     */
    @Override
    public void setApplicationAttributes(String appName, Map<String, Object> atrMap)
            throws GeneralException {
        if (!Util.isEmpty(atrMap)) {
            SailPointContext context = SailPointFactory.getCurrentContext();
            String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;
            // Do not wander around, just lock the application object right away.
            Application app = ObjectUtil.lockObject(context,
                                                    Application.class,
                                                    null,
                                                    appName,
                                                    lockMode);

            for(String key : atrMap.keySet()) {
                app.setAttribute(key, atrMap.get(key));
            }

            try {
                context.saveObject(app);
            } finally {
                // this will commit
                ObjectUtil.unlockObject(context, app, lockMode);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Deciphering service
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Decrypt an encrypted string.
     *
     * @param src - Encrypted string.
     *
     * @return - Decrypted string.
     */
    @Override
    @Untraced
    @SensitiveTraceReturn
    public String decrypt(String src) throws GeneralException {
        return SailPointFactory.getCurrentContext().decrypt(src);
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Object cloning service
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Perform a deep copy of the object. This differs from clone() which has
     * historically done a shallow copy.
     * 
     * @param obj
     *            Object to be copied/cloned
     * 
     * @return copy/clone of the calling the object
     * @throws Exception
     */
    @Override
    public Object deepCopy(Object obj) throws Exception {

        if (obj instanceof SailPointObject) {
            return ((SailPointObject) obj).deepCopy((XMLReferenceResolver) SailPointFactory.getCurrentContext());
        } else if (obj instanceof AbstractXmlObject) {
            return ((AbstractXmlObject) obj).deepCopy(null);
        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Services to tailor a message and labels according
    // to the conventions of a user’s language and region.
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Creates an empty message instance.
     */
    public OpenMessagePart createEmptyMessage() {
        return new Message();
    }

    /**
     * Creates Message instance with given key and arguments. Message key can be
     * a valid key from a resource bundle, or plain text. Message type is
     * defaulted to <code>Type.Info</code>.
     *
     * @param key
     *            ResourceBundle message key, or a plain text message.
     * @param args
     *            Message format arguments.
     */
    public OpenMessagePart createInfoMessage(String key, Object... args) {
        return Message.info(key, args);
    }

    /**
     * Creates Message instance with given key and arguments. Message key can be
     * a valid key from a resource bundle, or plain text. Message type is
     * defaulted to <code>Type.Warn</code>.
     *
     * @param key
     *            ResourceBundle message key, or a plain text message.
     * @param args
     *            Message format arguments.
     */
    public OpenMessagePart createWarnMessage(String key, Object... args) {
        return Message.warn(key, args);
    }

    /**
     * Creates Message instance with given key and arguments. Message key can be
     * a valid key from a resource bundle, or plain text. Message type is
     * defaulted to <code>Type.Error</code>.
     *
     * @param key
     *            ResourceBundle message key, or a plain text message.
     * @param args
     *            Message format arguments.
     */
    public OpenMessagePart createErrorMessage(String key, Object... args) {
        return Message.error(key, args);
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Persistence services.
    //
    ////////////////////////////////////////////////////////////////////////
    /**
     * Saves an entire object.
     *
     * @param obj
     *            Object that needs to be saved.
     */
    @Override
    public void saveObject(Object obj) throws Exception {
        SailPointContext context = SailPointFactory.getCurrentContext();
        context.saveObject((SailPointObject) obj);
    }

  }
