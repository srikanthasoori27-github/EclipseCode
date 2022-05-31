/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base bean for JSF debug pages.
 */

package sailpoint.web;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.faces.model.SelectItem;
import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import sailpoint.Version;
import sailpoint.api.Explanator;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.bundleinfo.ConnectorBundleVersionable;
import sailpoint.environmentMonitoring.MonitoringUtil;
import sailpoint.object.CacheReference;
import sailpoint.object.ClassLists;
import sailpoint.object.Configuration;
import sailpoint.object.DatabaseVersion;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskSchedule;
import sailpoint.object.UIConfig;
import sailpoint.persistence.SailPointDataSource;
import sailpoint.provisioning.IntegrationConfigFinder;
import sailpoint.server.CacheService;
import sailpoint.server.Environment;
import sailpoint.server.RequestService;
import sailpoint.server.Service;
import sailpoint.server.Servicer;
import sailpoint.server.TaskService;
import sailpoint.tools.Brand;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.DateUtil;
import sailpoint.tools.DateUtil.IDateCalculator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

// backdoor for connection trace

public class DebugBean extends BaseBean
{
    private static Log _log = LogFactory.getLog(DebugBean.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // HttpSession attributes
    //

    public static final String ATT_OBJECT_CLASS = "debugObjectClass";
    public static final String ATT_OBJECT_NAME = "debugObjectName";
    public static final String ATT_OBJECT = "debugObject";
    public static final String ATT_RULE_NAME = "debugRuleName";
    public static final String ATT_RESULT = "debugResult";
    public static final String ATT_SUB_TYPE = "debugSubType";
    public static final String ATT_XML = "debugXml";

    //
    // There is only one sub type and it shares Identity
    //
    public static final String WORKGROUP_SUBTYPE = "Workgroup";
    private static final String TIME_MACHINE_KEY = "timeMachineEnabled";

    /**
     * Cached array of SelectItems for the class selection menu.
     * Derived from the ClassLists.MajorClasses list.
     */
    SelectItem[] _objectClassItems;

    /**
     * Cached list of the names of the major classes in the product.
     */
    List<String> _objectClassNames;

    /**
     * Cached list of rule names.
     */
    List<SelectItem> _ruleNames;

    /**
     * The object class selected for other operations.
     * !!Would be really nice to have a session bean here.
     */
    Class _class;

    /**
     * The name of the object class entered into the text field, or
     from the list.
     */
    String _name;

    /**
     * The name of the selected rule.
     */
    String _ruleName;

    /**
     * Subtype
     */
    String _subType;

    /**
     * Resolved object for edit.
     */
    SailPointObject _object;

    /**
     * The result of the rule.
     */
    Object _result;

    /**
     * Map containing database column names and display labels for the list
     * of objects we are going to display.
     */
    private Map<String, String> _columns;

    /**
     * XML of the object being edited.
     */
    String _xml;

    /**
     * Map of Java system properties in a format that is easily used by JSF
     */
    private Map<String, String> _systemProperties = null;

    /**
     * Map of JSF request scope beans in a format that is easily used by JSF
     */
    private Map<String, Object> _requestScopeBeans;

    /**
     * Map of JSF session scope beans in a format that is easily used by JSF
     */
    private Map<String, Object> _sessionScopeBeans;

    /**
     * Map of JSF application scope beans in a format that is easily used by JSF
     */
    private Map<String, Object> _applicationScopeBeans;

    /**
     * Map of the object types and the count of objects of that type.
     */
    private Map<String, Integer> _objectCount;
    
    /**
     * A path to a log4j2.properties configuration file
     */
    private String _logConfigFile = Util.findFile("WEB-INF/classes/log4j2.properties");

    /**
     * Map of the properties of the datasource connection pool when using DBCP.
     */
    private Map<String, String> _dataSourceProperties;

    /**
     * Map of the properties of the plugins datasource connection pool when using DBCP.
     */
    private Map<String, String> _pluginsDataSourceProperties;
    
    private Map<String, String> _identityStatMap;
    
    private LazyLoad<TimeMachineBean> timeMachine;

    /**
     * Supplies connector bundle version information.
     */
    private ConnectorBundleVersionable cbVersionSupplier;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public DebugBean() {
        super();
        restore();
        timeMachine = new LazyLoad<TimeMachineBean>(new ILazyLoader<TimeMachineBean>() {
            public TimeMachineBean load() {
                try {
                    return new TimeMachineBean(isSystemAdmin(), getContext().getConfiguration().getBoolean(TIME_MACHINE_KEY, false));
                } catch (GeneralException ex) {
                    _log.error(ex);
                    return null;
                }
            }
        });

        // Load Connector Bundle version supplier
        try {
            cbVersionSupplier = ConnectorFactory.getCBVersionSupplier();
        } catch (GeneralException e) {
            // Just log it and leave
            _log.error(e.getMessage(), e);
        }
    }

    /**
     * Save our form state to the HttpSession for transfer to the next page.
     * Necessary since we're only a request scoped bean, which is the
     * prevailing convention for all the other pages.  Might be able
     * to make an exception here, but the last time I tried I ran into
     * issues with the initialization of the SailPointContext and the JSF
     * request state down in BaseBean.
     */
    @SuppressWarnings("unchecked")
    private void save() {
        Map session = getSessionScope();
        session.put(ATT_OBJECT_CLASS, getObjectClass());
        session.put(ATT_OBJECT_NAME, getObjectName());
        session.put(ATT_RULE_NAME, _ruleName);
        session.put(ATT_RESULT, _result);
        session.put(ATT_SUB_TYPE, getSubType());
        session.put(ATT_XML, _xml);
    }


    private void restore() {
        Map session = getSessionScope();

        try {
            setObjectClass((String)session.get(ATT_OBJECT_CLASS));
            setSubType((String)session.get(ATT_SUB_TYPE));
            setObjectName((String)session.get(ATT_OBJECT_NAME));

            // jsl - we've been doing this for awhile but I don't
            // understand how it ever worked, saveAction takes XML
            // and creates a new object then calls context.saveObject
            // on it which for "child" objects like Scorecard and
            // UIPreferences results in two objects with the same id being
            // in the cache.  Doesn't seem to bother top level objects
            // for some reason, I guess the update vs replace logic
            // down in HibernatePersistenceManager doesn't handle
            // child objects correctly?  Anyway, there is no need
            // for the attach since we don't have multi-session
            // edits and we replace the entire object from the XML.
            // For that matter it doesn't really accomplish anything
            // keeping this on the HttpSession.
            //if (_object != null)
            //getContext().attach(_object);

            _ruleName = (String)session.get(ATT_RULE_NAME);
            _result = session.get(ATT_RESULT);

            // jsl - since the objects don't always have full load()
            // methods have to capture the XML immediately while we still
            // have it in the Hibernate session
            // we shouldn't need _object after this
            _xml = (String)session.get(ATT_XML);
            if (_xml == null) {
                SailPointObject obj = getObject();
                if (obj != null)
                    _xml = obj.toXml();
            }

        }
        catch (Throwable t) {
            // eat class errors on initialization in case we have
            // corrupted session attributes
        }
    }

    private void clearObject() {
        _name = null;
        _object = null;
        _xml = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Property to specify the internal persistnet class by name.
     */
    public String getObjectClass() {

        String name = null;
        if (_class != null) {
            if ( isWorkgroupSubType() ) {
                name = WORKGROUP_SUBTYPE;
            } else
                name = _class.getSimpleName();
        }
        return name;
    }

    public void setObjectClass(String name) throws Exception {

        Class cls = null;
        if (name != null) {
            setSubType(null);
            String path = "sailpoint.object." + name;
            if ( name.equals(WORKGROUP_SUBTYPE) ) {
                cls = sailpoint.object.Identity.class;
                setSubType(name);
            } else {
                cls = Class.forName(path);
            }
        }

        if (cls != _class) {
            _class = cls;
            _object = null;
            _xml = null;
        }
    }

    public void setSubType(String name) {
        _subType = name;
    }

    public String getSubType() {
        return _subType;
    }

    /**
     * Property to specify the object name for View and Edit actions.
     */
    public String getObjectName() {
        return _name;
    }

    public void setObjectName(String s) {

        if (s == null) {
            _name = null;
            _object = null;
            _xml = null;
        }
        else if (!s.equals(_name)) {
            _name = s;
            _object = null;
            _xml = null;
        }
    }

    public String getRuleName() {
        return _ruleName;
    }

    public void setRuleName(String s) {
        _ruleName = s;
    }


    /**
     * Return items for the class selection menu.
     */
    public SelectItem[] getObjectClassItems() {

        if (_objectClassItems == null) {
                // this has a side effect of setting _objectClassNames
            getObjectClassNames();
            List<SelectItem> objectClassItemsList = new ArrayList<SelectItem>();
            for (String name : _objectClassNames ) {
                objectClassItemsList.add(new SelectItem(name, name));
            }
            _objectClassItems = objectClassItemsList.
                         toArray(new SelectItem[objectClassItemsList.size()]);
        }

        return _objectClassItems;
    }

    /**
     * Return a list of the names of the major objects in the product.
     */
    public List<String> getObjectClassNames() {
        if ( _objectClassNames == null ) {
            _objectClassNames = new ArrayList<String>();

            for ( Class c : ClassLists.MajorClasses ) {
                _objectClassNames.add(c.getSimpleName());
            }
            // Add this in, its a special type of Identity
            _objectClassNames.add(WORKGROUP_SUBTYPE);
            Collections.sort(_objectClassNames);
        }

        return _objectClassNames;
    }

     public String getObjectClassNamesJson() {
        List<String> names = getObjectClassNames();

        if (names != null){
            return JsonHelper.toJson(names);
        }

        return "[]";
    }

    public void setObjectClassNamesJson(String foo){}

    public List<SelectItem> getRuleNames() throws GeneralException {

        if (_ruleNames == null) {
            _ruleNames = new ArrayList<SelectItem>();

            QueryOptions ops = new QueryOptions();
            ops.setOrderBy("name");
            ops.setOrderAscending(true);

            List<String> projection = new ArrayList<String>();
            projection.add("name");

            Iterator<Object[]> result =
                             getContext().search(Rule.class, ops, projection);
            if (result != null) {
                while (result.hasNext()) {
                    Object[] current = result.next();
                    _ruleNames.add(new SelectItem(current[0].toString(),
                                                  current[0].toString()));
                }
            }
        }
        return _ruleNames;
    }

    private boolean isWorkgroupSubType() {
        if ( ( Util.getString(_subType) != null ) &&
             ( WORKGROUP_SUBTYPE.compareTo(_subType) == 0 ) ) {
            return true;
        }
        return false;

    }

    /**
     * Return the object previously selected
     */
    @SuppressWarnings("unchecked")
    public SailPointObject getObject() throws GeneralException {

        if (_object == null && _class != null && _name != null) {
            if ( _class.equals(Identity.class) ) {
                Filter filter = null;
                Filter nameFilter = Filter.or(Filter.eq("name", _name), Filter.eq("id", _name));
                if ( isWorkgroupSubType() ) {
                    filter = Filter.and(nameFilter, Filter.eq("workgroup", true));
                } else {
                    filter = Filter.and(nameFilter, Filter.eq("workgroup", false));
                }
                _object = getContext().getUniqueObject(_class, filter);
            } else {
                _object = getContext().getObjectByName(_class, _name);
            }
        }
        return _object;
    }

    /**
     * Return the XML representation of the selected object.
     */
    public String getXml() throws GeneralException {

        if (_xml == null) {
            SailPointObject o = getObject();
            if (o != null)
                _xml = o.toXml();
        }
        return _xml;
    }

    /**
     * Assimilate the modified XML.
     * Don't convert it back to the object yet, we have to decide
     * what to do with it based on the action.
     */
    public void setXml(String xml) throws GeneralException {

        _xml = null;
        if (xml != null) {
            xml = xml.trim();
            if (xml.length() > 0) {
                // any other sanity checks?
                _xml = xml;
            }
        }
    }

    /**
     * Render the _result as XML.
     * Used for rule results.
     */
    public String getResultXml() throws GeneralException {

        String xml = null;
        if (_result != null) {
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            xml = f.toXml(_result);
        }
        return xml;
    }

    /**
     * Render the _result as a String.
     * Used for cached configs.
     */
    public String getResultString() throws GeneralException {

        return (_result != null) ? _result.toString() : null;
    }

    public List<String> getColumnKeys() {
        Map<String, String> columns = getColumns();
        List<String> keys = new ArrayList<String>();
        if ( columns != null ) {
            keys.addAll(columns.keySet());
        }
        return keys;
    }

    public String getKeyColumn() {
        List<String> columnKeys = getColumnKeys();        
        if ( isNameUnique() &&  columnKeys.contains("name") ) 
            return "name";        
        if ( columnKeys.contains("id") ) 
            return "id";
        return columnKeys.get(0);
    }

    /**
     * Some classes like IdentityRequest the name field isn't 
     * unique so use the id, which is a friendly identifier
     * unlike some ids.
     * 
     * @return
     */
    private boolean isNameUnique() {
        boolean isUnique = true;
        try {
            if ( _class != null ) {
                Method method = _class.getMethod("isNameUnique", (Class[])null);
                if ( method != null ) {
                    Object o = Util.createObjectByClassName(_class.getName());
                    if ( o != null ) {
                        isUnique = (Boolean)method.invoke(o, (Object[])null);
                    }
                }
            }
        } catch(Exception e) {
            _log.error("Exception calling isNameUnique" + e);
        }
        return isUnique;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getColumns() {
        if ( _class == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SELECT_OBJ_TYPE), null);
            return null;
        }

        if ( _columns == null ) {
            try {
                Method m = _class.getMethod("getDisplayColumns", (Class[])null);
                _columns = (Map<String, String>)m.invoke(null, (Object[])null);
            } catch ( NoSuchMethodException ex ) {
            } catch ( IllegalArgumentException ex ) {
            } catch ( IllegalAccessException ex ) {
            } catch ( InvocationTargetException ex ) {
            }

            if ( _columns == null ) {
                    // help callers of this function by always returning a
                    // non-null valid Map
                _columns = new HashMap<String, String>();
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_UNABLE_DISPLAY_COLS), null);
            }
        }

        return _columns;
    }

    /**
     * Return a simplfied search model for the list page.
     */
    @SuppressWarnings("unchecked")
    public List<Map> getRows() throws GeneralException {

        List<Map> rows = null;
        if (_class != null) {

            // kludge: some classes don't support projection queries
            if (_class == TaskSchedule.class) {

                List<SailPointObject> objs = getContext().getObjects(_class, null);
                if (objs != null) {
                    rows = new ArrayList<Map>();
                    for (SailPointObject obj : objs) {
                        Map<String, Object> row = new HashMap<String, Object>();
                        row.put("id", obj.getId());
                        row.put("name", obj.getName());
                        rows.add(row);
                    }
                }
            }
            else {
                QueryOptions ops = new QueryOptions();
                    // limit the results
                    // TODO: Eventually should support filters in case the
                    //       list is too long
                ops.setResultLimit(1000);
                List<String> projection = getColumnKeys();
                if ( ! projection.contains("id") )
                    projection.add(0, "id");

                if (_class == IdentityHistoryItem.class) {
                    ops.addOrdering("entryDate", false);
                } else {
                    if ( isNameUnique() && projection.contains("name") ) {
                        ops.setOrderBy("name");
                    } else
                    if ( !isNameUnique() && projection.contains("id" )) {
                        ops.setOrderBy("id");
                    }
                    ops.setOrderAscending(true);
                }

                if ( isWorkgroupSubType() ) {
                    ops.add(sailpoint.object.Filter.eq("workgroup",true));
                }

                Iterator<Object[]> result =
                                     getContext().search(_class, ops, projection);

                if (result != null) {
                    rows = new ArrayList<Map>();
                    while (result.hasNext()) {
                        Object[] current = result.next();

                            // if name is one of the columns and there is
                            // no name, then use the id instead
                        int nameIndex = projection.indexOf("name");
                        int idIndex = projection.indexOf("id");
                        if ( nameIndex >= 0 && idIndex >= 0 ) {
                            if ( current[nameIndex] == null ||
                                    current[nameIndex].toString().length() == 0 ) {
                                current[nameIndex] = current[idIndex];
                            }
                        }

                        Map<String, Object> row = new HashMap<String, Object>();

                            // massage any data for a more usable display
                            // and add it to our map
                        for ( int i = 0; i < current.length; i++ ) {
                            if ( current[i] == null )
                                current[i] = "";

                            if ( current[i] instanceof SailPointObject ) {
                                SailPointObject spo = ((SailPointObject)current[i]);
                                String nameOrId = spo.getName();
                                if ( nameOrId == null || nameOrId.length() == 0 )
                                    nameOrId = spo.getId();
                                current[i] = nameOrId;
                            }

                            if ( current[i] instanceof Date ) {
                                current[i] =
                                      Util.dateToString((Date)current[i]);
                            }

                            // todo i18n  Some values like description,
                            // displayName, etc... have message keys.
                            row.put(projection.get(i), current[i]);
                        }  // for i = 0 ... current.length

                        rows.add(row);
                    }  // while result.hasNext()
                }  // if result != null
            }
        }

        return rows;
    }

    public Map<String, Integer> getObjectCount() {
        if ( _objectCount == null ) {
            _objectCount = new HashMap<String, Integer>();

            for ( Class c : ClassLists.MajorClasses ) {
                try {
                    int count = getContext().countObjects(c, null);
                    _objectCount.put(c.getSimpleName(), new Integer(count));
                } catch ( GeneralException ex ) {
                    _log.warn("Unable to get object count.", ex);
                }
            }

        }
        return _objectCount;
    }
    
    public List<String> getIdentityStatisticsNames() throws GeneralException {

        _log.info("Identity Statistics - ");
        _identityStatMap =  new LinkedHashMap <String, String>();
        
        SailPointContext context = getContext();
        // Get Total Identities.
        int count = context.countObjects(Identity.class, null);
        _log.debug("    Total Identities - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_TOTAL).toString(), Integer.toString(count));


        // Get Active Identities.
        QueryOptions qo = new QueryOptions(Filter.eq(Identity.ATT_INACTIVE, false));
        count = context.countObjects(Identity.class, qo);
        _log.debug("    Active Identities - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_ACTIVE).toString(), Integer.toString(count));

        // Get Inactive Identities.
        qo = new QueryOptions(Filter.eq(Identity.ATT_INACTIVE, true));
        count = context.countObjects(Identity.class, qo);
        _log.debug("    Inactive Identities - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_INACTIVE).toString(), Integer.toString(count));

        // Get Uncorrelated Identities.
        qo = new QueryOptions(Filter.eq(Identity.ATT_CORRELATED, false));
        count = context.countObjects(Identity.class, qo);
        _log.debug("    Uncorrelated Identities - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_UNCORRELATED).toString(), Integer.toString(count));

        // Get Identity Snapshots.
        count = context.countObjects(IdentitySnapshot.class, null);
        _log.debug("    Identity Snapshots - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_SNAPSHOTS).toString(), Integer.toString(count));

        // Get License identities (Active + Correlated)
        qo = new QueryOptions(Filter.and(Filter.eq(Identity.ATT_INACTIVE, false),
                Filter.eq(Identity.ATT_CORRELATED, true)));
        count = context.countObjects(Identity.class, qo);
        _log.debug("    License identities (Active + Correlated) - " + count);
        _identityStatMap.put(new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_LICENSE).toString(), Integer.toString(count));

        List<String> keys = new ArrayList<String>();
        Map map = _identityStatMap;
        if ( map != null ) {
            Iterator iter = map.keySet().iterator();
            while ( iter.hasNext() ) {
                Object key = iter.next();
                if ( key != null ) keys.add(key.toString());
            }
        }
        return keys;
    }
    
    public Map<String, String> getIdentityStatisticsProperties() throws GeneralException {
        if (_identityStatMap == null) getIdentityStatisticsNames();
        return _identityStatMap;
    }

    /**
     * Returns the application installation directory.
     */
    public String getApplicationHome() {
        String appHome = "";
        try {
            appHome = Util.getApplicationHome();
        } catch (GeneralException ex) { }

        return appHome;
    }

    /**
     * Return the compiled-in version of the product.
     *
     * @return the version of the product
     */
    public String getVersion() {
        return Version.getVersion();
    }

    /**
     * Return the complete version of the product including patch level and
     * revision.
     *
     * @return the complete version of the product
     */
    public String getFullVersion() {
        return Version.getFullVersion();
    }

    public boolean isAcceleratorPackDeployed() {
        return Configuration.getSystemConfig().containsKey(Configuration.ACCELERATOR_PACK_VERSION);
    }

    /**
     * Return the Accelerator Pack Version
     *
     * @return version as string
     */
    public String getAcceleratorPackVersion() {
        String version = Configuration.getSystemConfig().getString(Configuration.ACCELERATOR_PACK_VERSION);
        return (version == null) ? "" : version;
    }

    /**
     * Return a list of efixes installed - basically all files ending in .txt in the WEB-INF/efixes dir
     *
     * @return list of efixes installed
     */
    public String geteFixes() {
    StringBuilder sb = new StringBuilder();
        final Pattern STRIP_BAD_CHARS = Pattern.compile("[^a-zA-Z0-9.-]+");
        final String efixDir = "/WEB-INF/efixes";

        try {
            String appName;
            BrandingService bs = BrandingServiceFactory.getService();
            if (bs.getBrand() == Brand.AGS) {
                appName = bs.getApplicationShortName().toLowerCase();
            } else {
                appName = bs.getApplicationName().toLowerCase();
            }
            appName = appName + "-";

            Set<String> resourcePaths = getFacesContext().getExternalContext().getResourcePaths(efixDir);
            if (null != resourcePaths && ! resourcePaths.isEmpty()) {
                for (String efix:resourcePaths) {
                    _log.debug("geteFixes(): checking resourcePath: " + efix);
                    if (efix.endsWith(File.separator)) {
                        continue;  // directory found
                    }
                    Path efixPath = Paths.get(efix);
                    String efixName = efixPath.getFileName().toString();
                    efixName = STRIP_BAD_CHARS.matcher(efixName).replaceAll("");
                    if (efixName.toLowerCase().startsWith(appName) && efixName.toLowerCase().endsWith(".txt")) {
                        efixName = efixName.substring(0, efixName.lastIndexOf("."));
                        if (! efixName.isEmpty()) {
                            sb.append(efixName);
                            sb.append("<br />");
                        }
                    }
                }
            }
            if (sb.length() > 0) {
                return(sb.toString());
            }
        } catch (Exception e) {
            _log.warn("Error getting efix list", e);
        }
        return "None";
    }

    public String getSchemaVersion() {
        String version = null;
        try {
            DatabaseVersion dbv = getContext().getObjectByName(DatabaseVersion.class, "main");
            if (dbv != null)
                version = dbv.getSchemaVersion();
        }
        catch (GeneralException e) {}
        return version;
    }

    /**
     * Return the compiled-in repository location of the product.
     *
     * @return the repository location of the product
     */
    public String getRepoLocation() {
        return Version.getRepoLocation();
    }

    /**
     * Return the compiled-in patch level of the product.
     *
     * @return the patch level of the product
     */
    public String getPatchLevel() {
        return Version.getPatchLevel();
    }

    /**
     * Return the compiled-in source revision of the product.
     *
     * @return the source revision of the product
     */
    public String getRevision() {
        return Version.getRevision();
    }

    /**
     * Return the compiled-in user that built the product.
     *
     * @return the builder of the product
     */
    public String getBuilder() {
        return Version.getBuilder();
    }

    /**
     * Return the compiled-in build date of the product.
     *
     * @return the build date of the product
     */
    public String getBuildDate() {
        return Version.getBuildDate();
    }

    /**
     * Return the compiled-in version of the connector bundle.
     *
     * @return The version of the connector bundle
     */
    public String getCbVersion() {
        return cbVersionSupplier != null ? cbVersionSupplier.getVersion() : "";
    }

    /**
     * Return the complete version of the connector bundle
     * including patch level and revision.
     *
     * @return The complete version of the connector bundle
     */
    public String getCbFullVersion() {
        return cbVersionSupplier != null ? cbVersionSupplier.getFullVersion() : "";
    }

    /**
     * Return the compiled-in repository location of the connector bundle.
     *
     * @return The repository location of the connector bundle
     */
    public String getCbRepoLocation() {
        return cbVersionSupplier != null ? cbVersionSupplier.getRepoLocation() : "";
    }

    /**
     * Return the compiled-in patch level of the connector bundle.
     *
     * @return The patch level of the connector bundle
     */
    public String getCbPatchLevel() {
        return cbVersionSupplier != null ? cbVersionSupplier.getPatchLevel() : "";
    }

    /**
     * Return the compiled-in source revision of the connector bundle.
     *
     * @return The source revision of the connector bundle
     */
    public String getCbRevision() {
        return cbVersionSupplier != null ? cbVersionSupplier.getRevision() : "";
    }

    /**
     * Return the compiled-in user that built the connector bundle.
     *
     * @return The builder of the connector bundle
     */
    public String getCbBuilder() {
        return cbVersionSupplier != null ? cbVersionSupplier.getBuilder() : "";
    }

    /**
     * Return the compiled-in build date of the connector bundle.
     *
     * @return The build date of the connector bundle
     */
    public String getCbBuildDate() {
        return cbVersionSupplier != null ? cbVersionSupplier.getBuildDate() : "";
    }

    /**
     * Return the number of processors available to this JVM instance.
     *
     * @return the number of processors available to this JVM instance
     */
    public String getAvailableProcessors() {
        return Integer.toString(Runtime.getRuntime().availableProcessors());
    }

    public String getHostName() {
        return Util.getHostName();
    }

    public String getSchedulerHosts() {
        Environment env = Environment.getEnvironment();
        Servicer svc = env.getServicer();
        return svc.getTaskSchedulerHosts();
    }

    public String getSchedulerStatus() {
        return getServiceStatus(TaskService.NAME);
    }

    public String getRequestSchedulerHosts() {
        Environment env = Environment.getEnvironment();
        Servicer svc = env.getServicer();
        return svc.getRequestSchedulerHosts();
    }

    public String getRequestSchedulerStatus() {
        return getServiceStatus(RequestService.NAME);
    }

    public String getServiceStatus(String name) {
        String status = "Not Running";
        Environment env = Environment.getEnvironment();
        Service svc = env.getService(name);
        if (svc != null)
            status = svc.getStatusString();
        return status;
    }

    /**
     * Helper function to extract key values from a map and return them in a
     * sorted list.
     *
     * @param map the map
     * @return a sorted list of the map keys
     */
    private List<String> getKeyNames(final Map map) {
        List<String> keys = new ArrayList<String>();

        if ( map != null ) {
            Iterator iter = map.keySet().iterator();
            while ( iter.hasNext() ) {
                Object key = iter.next();
                if ( key != null ) keys.add(key.toString());
            }
        }

        Collections.sort(keys);
        return keys;
    }  // getKeyNames(Map)

    /**
     * Load the system property map from the Java System properties into a
     * data structure more easily used by JSF.
     */
    private void loadSystemProperties() {
            // TreeMap is sorted
        _systemProperties = new TreeMap<String, String>();

        Enumeration e = System.getProperties().propertyNames();
        while ( e.hasMoreElements() ) {
            String key = e.nextElement().toString();
            _systemProperties.put(key, System.getProperty(key));
        }

    }

    /**
     * Return a map of the Java System properties.
     *
     * @return a map of the Java System properties
     */
    public Map<String, String> getSystemProperties() {
        if ( _systemProperties == null ) loadSystemProperties();
        return _systemProperties;
    }

    /**
     * Return a list of the Java System property names.
     *
     * @return a list of the Java System property names
     */
    public List<String> getSystemPropertyNames() {
        if ( _systemProperties == null ) loadSystemProperties();

        return getKeyNames(_systemProperties);
    }

    /**
     * Iterate over a map and convert any values that are SailPointObjects
     * into the XML string representation of the object.
     *
     * @param map the map to convert
     * @return the converted map
     */
    private Map<String, Object> toXmlSailPointObjects(final Map map) {
        TreeMap<String, Object> attrMap = new TreeMap<String, Object>();

        if ( map != null ) {
            Iterator iter = map.keySet().iterator();
            while ( iter.hasNext() ) {
                Object key = iter.next();
                if ( key != null ) {
                    Object value = map.get(key);
                    if ( value instanceof SailPointObject ) {
                        try {
                            getContext().attach(((SailPointObject)value));
                            value = ((SailPointObject)value).toXml();
                        } catch ( GeneralException ex ) { }
                    }
                    attrMap.put(key.toString(), value);
                }  // if key != null
            }  // while iter.hasNext()
        }  // if map != null

        return attrMap;
    }  // toXmlSailPointObjects(Map)

    /**
     * Return the names of the request scope beans.
     *
     * @return the names of the request scope beans
     */
    public List<String> getRequestScopeBeanNames() {
        return getKeyNames(getRequestScope());
    }

    /**
     * Return the request scope beans map with the values that are
     * SailPointObject's converted to XML.
     *
     * @return the request scope beans
     */
    public Map<String, Object> getRequestScopeValues() {
        if ( _requestScopeBeans == null )
            _requestScopeBeans = toXmlSailPointObjects(getRequestScope());
        return _requestScopeBeans;
    }

    /**
     * Return the names of the session scope beans.
     *
     * @return the names of the session scope beans
     */
    public List<String> getSessionScopeBeanNames() {
        return getKeyNames(getSessionScope());
    }

    /**
     * Return the session scope beans map with the values that are
     * SailPointObject's converted to XML.
     *
     * @return the session scope beans
     */
    public Map<String, Object> getSessionScopeValues() {
        if ( _sessionScopeBeans == null )
            _sessionScopeBeans = toXmlSailPointObjects(getSessionScope());
        return _sessionScopeBeans;
    }

    /**
     * Return the names of the application scope beans.
     *
     * @return the names of the application scope beans
     */
    public List<String> getApplicationScopeBeanNames() {
        return getKeyNames(getApplicationScope());
    }

    /**
     * Return the application scope beans map with the values that are
     * SailPointObject's converted to XML.
     *
     * @return the application scope beans
     */
    public Map<String, Object> getApplicationScopeValues() {
        if ( _applicationScopeBeans == null )
            _applicationScopeBeans =
                                 toXmlSailPointObjects(getApplicationScope());
        return _applicationScopeBeans;
    }

    /**
     * Return the current time and date in full format.
     *
     * @return a full format current time and date
     */
    public String getCurrentDate() {
        return DateFormat.
                        getDateTimeInstance(DateFormat.FULL, DateFormat.FULL).
                        format(new Date());
    }

    // cannot be static because it's used by JSF
    public String getTotalMemory() {
        return Util.memoryFormat(Runtime.getRuntime().totalMemory());
    }

    // cannot be static because it's used by JSF
    public String getFreeMemory() {
        return Util.memoryFormat(Runtime.getRuntime().freeMemory());
    }

    // cannot be static because it's used by JSF
    public String getMaxMemory() {
        return Util.memoryFormat(Runtime.getRuntime().maxMemory());
    }

    // cannot be static because it's used by JSF
    public String getOperatingSystemArchitecture() {
        return MonitoringUtil.getOperatingSystemArchitecture();
    }

    // cannot be static because it's used by JSF
    public String getOperatingSystemNameVersion() {
        return MonitoringUtil.getOperatingSystemNameVersion();
    }

    // cannot be static because it's used by JSF
    public long getMaxFileCount() {
        return MonitoringUtil.getMaxFileCount();
    }

    /**
     * A wraper class around <code>java.lang.Thread</code> that allows for
     * easy extraction of information in a JSF page.
     */
    public class ThreadInfo {
        private Thread _thread;

        public ThreadInfo(Thread thread) { _thread = thread; }

        /**
         * Returns a string representation of the thread's id.
         *
         * @return the thread id
         */
        public String getId() { return Long.toString(_thread.getId()); }

        /**
         * Returns the thread's name.
         *
         * @return the thread name
         */
        public String getName() { return _thread.getName(); }

        /**
         * Returns the class and method name of the top-most non-native
         * thread in the thread's stack trace.
         *
         * @return a string with the class and method name.
         */
        public String getCurrentMethod() {
            String top = "";
            StackTraceElement[] ste = _thread.getStackTrace();
            if ( ste != null ) {
                for ( int i = 0; i < ste.length; i++ ) {
                    if ( ! ste[i].isNativeMethod() ) {
                        top = ste[i].toString();
                        break;
                    }
                }
                if ( top.equals("") && ste.length > 0 )
                    top = ste[0].toString();
            }
            return top;
        }

        /**
         * Provide the stack trace for the thread in a list of stack elements
         *
         * @return a list of strings containing each stack element
         */
        public List<String> getStackTrace() {
            StackTraceElement[] ste = _thread.getStackTrace();
            List<String> stList = new ArrayList<String>();
            if ( ste != null ) {
                for ( int i = 0; i < ste.length; i++ ) {
                    stList.add(ste[i].toString());
                }
            }
            return stList;
        }

        /**
         * Return the thread's priority as a string
         *
         * @return the thread priority
         */
        public String getPriority() {
            return Long.toString(_thread.getPriority());
        }

        /**
         * Return the thread's state as a string
         *
         * @return the thread state
         */
        public String getState() {
            return _thread.getState().toString();
        }
    }  // class ThreadInfo

    /**
     * Walks the entire thread tree gathering all of the threads in the JVM
     *
     * @return a list of threads wrapped in an object that is easy to use in JSF
     */
    public List<ThreadInfo> getThreads() {
        List<ThreadInfo> threads = new ArrayList<ThreadInfo>();

            // walk the thread group tree to find the root thread group
        ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
        while ( root.getParent() != null ) {
            root = root.getParent();
        }

            // visit each group adding the contained threads to our list
        gatherChildren(root, threads);

        return threads;
    }  // getThreads()

    /**
     * Adds the child threads of the specified <code>group</code> into the
     * <code>threads</code> array.  Will recursively invoke itself to walk
     * the child thread groups.
     *
     * @param group a ThreadGroup
     * @param threads a list that will have ThreadInfo objects added to it
     */
    private void gatherChildren(ThreadGroup group, List<ThreadInfo> threads) {

            // first go through this group's threads
        int numThreads = group.activeCount();
        Thread[] children = new Thread[numThreads * 2];
        numThreads = group.enumerate(children, false);
        for ( int i = 0; i < numThreads; i++ ) {
            Thread t = children[i];
            threads.add(new ThreadInfo(t));
        }

            // next visit all of the subgroups
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups * 2];
        numGroups = group.enumerate(groups, false);
        for ( int i = 0; i < numGroups; i++ ) {
            gatherChildren(groups[i], threads);
        }
    }  // gatherChildren(ThreadGroup, List<Thread>)

    /**
     * Return a list of the Meters with call timing information.
     */
    public List<Meter> getMeters() {
        return new ArrayList<Meter>(Meter.getGlobalMeterCollection());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public String listAction() throws GeneralException {
        save();
        return "list";
    }

    public String selectAction() throws GeneralException {

        if ( _name != null )
            return prepareObject("edit");
        else
            return null;
    }

    public String viewAction() throws GeneralException {

        return prepareObject("view");
    }

    public String editAction() throws GeneralException {
        return prepareObject("edit");
    }

    /**
     * Run a rule for side effect and stay on the page.
     */
    public String ruleAction() throws GeneralException {
        if (_ruleName != null) {
            SailPointContext con = getContext();
            Rule rule = con.getObjectByName(Rule.class, _ruleName);
            if (rule != null)
                _result = con.runRule(rule, null);
        }
        save();
        return "result";
    }

    private String prepareObject(String outcome) throws GeneralException {

        String next = null;
        if (_class != null && _name != null) {
            try {
                _object = getObject();
                // not all objects fully load and remain attached -
                // regenerate the xml early so we don't depend on _object
                if (_object != null)
                    _xml = _object.toXml();
                else
                    _xml = null;
                next = outcome;
            }
            catch (Throwable t) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t), null);
            }
        }

        save();

        return next;
    }

    public String saveAction() throws GeneralException {

        if (_xml != null) {
            // for child objects attached to the Identity like
            // Scorecard and UIPreferences, there can already be
            // a representation of those in the cache when we fetch
            // the login user (apparently every page hit?)
            // to be safe do a full decache before saving
            SailPointContext con = getContext();

            XMLObjectFactory f = XMLObjectFactory.getInstance();
            Object o = f.parseXml(con, _xml, true);
            if (o instanceof SailPointObject) {
                _object = (SailPointObject)o;
                // these really should be the same, but I suppose
                // we can let them type in entirely different XML?
                _class = _object.getClass();
                _name = _object.getName();

                ObjectUtil.checkIllegalRename(con, _object);
                /* Decache session */
                con.decache();
                _object = (SailPointObject) ObjectUtil.recache(con, _object);
                con.saveObject(_object);
                con.commitTransaction();
            }
        }

        // assume we're changing objects, so don't leave these behind
        clearObject();
        save();

        return "main";
    }

    public String cancelAction() {

        // leave the class for the selector, but forget the object
        clearObject();
        save();

        return "main";
    }

    public String cancelEditAction() {
        clearObject();
        save();

        return "list";
    }

    public String systemConfigAction() {

        clearObject();
        _class = Configuration.class;
        _name = Configuration.OBJ_NAME;
        save();
        return "edit";
    }

    public String identityConfigAction() {

        clearObject();
        _class = ObjectConfig.class;
        _name = ObjectConfig.IDENTITY;
        save();
        return "edit";
    }

    public String linkConfigAction() {

        clearObject();
        _class = ObjectConfig.class;
        _name = ObjectConfig.LINK;
        save();
        return "edit";
    }


    public String uiConfigAction() {

        clearObject();
        _class = UIConfig.class;
        _name = UIConfig.OBJ_NAME;
        save();
        return "edit";
    }

    public String cachedConfigAction()
        throws GeneralException {

        clearObject();

        StringBuilder sb = new StringBuilder();

        // ooh...magic backdoor
        Environment env = Environment.getEnvironment();
        Servicer services = env.getServicer();
        CacheService service = (CacheService)services.getService(CacheService.NAME);
        if (service != null) {
            List<CacheReference> caches = service.getCaches();
            if (caches != null) {
                for (CacheReference cache : caches) {
                    SailPointObject obj = cache.getObject();
                    if (obj != null) {
                        sb.append(obj.toXml());
                    }
                }
            }
        }

        // Transitioning to the rule result page with a null
        // rule will just render the result as a string, otherwise
        // it converts it to XML.
        _ruleName = null;
        _result = sb.toString();

        save();

        return "result";
    }

    public String resetCacheAction()
        throws GeneralException {

        // A pretty large backdoor...
        Environment env = Environment.getEnvironment();
        CacheService caches = env.getCacheService();
        if (caches != null)
            caches.forceRefresh(getContext());

        return "";
    }

    public String resetManagedAttributeCache() throws GeneralException {

        Explanator.refresh(getContext());
        return "";
    }

    public String dumpManagedAttributeCache() throws GeneralException {

        Explanator.dump();
        return "";
    }

    public String loadManagedAttributeCache() throws GeneralException {

        Explanator.load(getContext());
        return "";
    }

    public String resetIntegrationConfigCache() throws GeneralException {
        // Get the IntegrationConfigCache instance. If there isn't one the 
        // getter will create it and initialize it OR it will refresh the 
        // cache if it's already created and it's stale.
        IntegrationConfigFinder.IntegrationConfigCache.getIntegrationConfigCache(getContext());
        return "";
    }

    public String gcAction() {
        System.gc();
        return "";
    }

    public String finalizeAction() {
        System.runFinalization();
        return "";
    }

    public String resetMetersAction() {
        Meter.globalReset();
        return "";
    }

    public String getLogConfigFile() {
        return _logConfigFile;
    }

    public void setLogConfigFile(String logConfigFile) {
        _logConfigFile = logConfigFile;
    }

    public String reloadLogConfigAction() {
        File f = new File(_logConfigFile);
        if ( f.exists() ) {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.setConfigLocation(f.toURI());
                // warn is a little severe, but we really want to record in
                // the logging output that there was a change in settings
            _log.warn("Reloading logging config from " + _logConfigFile);
            addMessage(new Message(Message.Type.Info, "Reloaded " + _logConfigFile));
        } else {
            addMessage(new Message(Message.Type.Error,
                                         _logConfigFile + " does not exist"));
        }

        return "";
    }
    
    public List<String> getDataSourcePropertyNames() {
        if ( _dataSourceProperties == null )
            getDataSourceProperties();
        
        return getSortedPropertyNames(_dataSourceProperties.keySet());
    }

    public Map<String, String> getDataSourceProperties() {
        if ( _dataSourceProperties == null ) {
            _dataSourceProperties = new HashMap<String, String>();

            Environment env = Environment.getEnvironment();
            DataSource ds = env.getSpringDataSource();
            if ( ds != null ) {
                initDataSourceProperties(ds, _dataSourceProperties);
            }
        }
        
        return _dataSourceProperties;
    }

    public boolean isPluginsEnabled() {
        return Environment.getEnvironment().getPluginsConfiguration().isEnabled();
    }

    public List<String> getPluginsDataSourcePropertyNames() {
        if (_pluginsDataSourceProperties == null) {
            getPluginsDataSourceProperties();
        }

        return getSortedPropertyNames(_pluginsDataSourceProperties.keySet());
    }

    public Map<String, String> getPluginsDataSourceProperties() {
        if (_pluginsDataSourceProperties == null) {
            _pluginsDataSourceProperties = new HashMap<String, String>();

            Environment env = Environment.getEnvironment();
            DataSource ds = env.getPluginsConfiguration().getDataSource();
            if (ds != null) {
                initDataSourceProperties(ds, _pluginsDataSourceProperties);
            }
        }

        return _pluginsDataSourceProperties;
    }

    private List<String> getSortedPropertyNames(Set<String> keySet) {
        List<String> keys = new ArrayList<String>();
        keys.addAll(keySet);
        Collections.sort(keys);

        return keys;
    }

    private void initDataSourceProperties(DataSource ds, Map<String, String> properties) {
        properties.put("className", ds.getClass().getName());

        if ( ds instanceof BasicDataSource ) {
            BasicDataSource bds = (BasicDataSource)ds;

            properties.put("initialSize", Integer.toString(bds.getInitialSize()));

            properties.put("maxTotal", Integer.toString(bds.getMaxTotal()));
            properties.put("maxIdle", Integer.toString(bds.getMaxIdle()));
            properties.put("maxOpenPreparedStatements", Integer.toString(bds.getMaxOpenPreparedStatements()));
            properties.put("maxWait", Long.toString(bds.getMaxWaitMillis()));

            properties.put("minEvictableIdleTimeMillis", Long.toString(bds.getMinEvictableIdleTimeMillis()));
            properties.put("minIdle", Integer.toString(bds.getMinIdle()));

            properties.put("numActive", Integer.toString(bds.getNumActive()));
            properties.put("numIdle", Integer.toString(bds.getNumIdle()));
            properties.put("numTestsPerEvictionRun", Integer.toString(bds.getNumTestsPerEvictionRun()));

            properties.put("timeBetweenEvictionRunsMillis", Long.toString(bds.getTimeBetweenEvictionRunsMillis()));
        }
    }
    
    public String refreshDataSourcePropertiesAction() {
        _dataSourceProperties = null;
        getDataSourceProperties();

        _pluginsDataSourceProperties = null;
        getPluginsDataSourceProperties();

        return "";
    }
    
    /**
     * Dive down to a Connection tracker we can optionally configure.
     */
    public List<SailPointDataSource.SPConnection> getConnections() {

        return SailPointDataSource.getConnectionList();
    }

    public TimeMachineBean getTimeMachine() throws GeneralException {
        return timeMachine.getValue();
    }
    
    /**
     * This class encapsulates timeMachine specific values
     * @author tapash
     *
     */
    public static class TimeMachineBean {
        
        private boolean systemAdmin;
        private boolean enabled;
        
        private String daysStr;
        private String hoursStr;
        private String minutesStr;
        
        private String commandOutput;
        
        public TimeMachineBean(boolean systemAdmin, boolean enabled) {
            this.systemAdmin = systemAdmin;
            this.enabled = enabled;
        }
        
        public String getDays() {
            return daysStr;
        }
        
        public void setDays(String val) {
            daysStr = val;
        }
        
        public String getHours() {
            return hoursStr;
        }
        
        public void setHours(String val) {
            hoursStr = val;
        }
        
        public String getMinutes() {
            return minutesStr;
        }
        
        public void setMinutes(String val) {
            minutesStr = val;
        }
        
        public String getCommandOutput() {
            return commandOutput;
        }
        
        public void setCommandOutput(String val) {
            commandOutput = val;
        }
        
        public boolean isEnabled() throws GeneralException {
            return (systemAdmin && enabled);
        }
        
        public void advanceTime() throws Exception {
            
            if (!isEnabled()) {
                if (_log.isDebugEnabled()) {
                    _log.debug("timeMachineEnabled must be set in configuration");
                    return;
                }
            }
            
            int days = getIntValue(getDays());
            int hours = getIntValue(getHours());
            int minutes = getIntValue(getMinutes());
            
            long millis = days * Util.MILLI_IN_DAY + hours * Util.MILLI_IN_HOUR + minutes * Util.MILLI_IN_MINUTE;
            DateUtil.advanceMillis(millis);
            
            IDateCalculator myCalc = DateUtil.getDateCalculator();
            Map<String, Integer> totalHMS = getDaysHoursMinutesFromMillis(myCalc.getMillisAdvanced());

            commandOutput = String.format(
                    "Advanced by: %d Days, %d Hours, %d Minutes.<br/>" + 
                    "Total time advanced: %d Days, %d Hours, %d Minutes.<br/>" + 
                    "CurrentDate: %s", 
                    days, hours, minutes, 
                    totalHMS.get("days"), totalHMS.get("hours"), totalHMS.get("minutes"), 
                    DateUtil.getCurrentDate());
            
        }
        
        private int getIntValue(String strValue) {

            if (Util.isNullOrEmpty(strValue)) {
                return 0;
            }

            try {
                return Integer.parseInt(strValue);
            } catch (Exception ex) {
                _log.error("Unable to parse: " + strValue);
                return 0;
            }
        }
        
        private Map<String, Integer> getDaysHoursMinutesFromMillis(long millis) {
            
            Map<String, Integer> retVal = new HashMap<String, Integer>();
            
            int days = (int) (millis / Util.MILLI_IN_DAY);
            long remaining = (long) (millis % Util.MILLI_IN_DAY);
            int hours = (int) (remaining / Util.MILLI_IN_HOUR);
            remaining = (long) (remaining % Util.MILLI_IN_HOUR); 
            int minutes = (int) (remaining / Util.MILLI_IN_MINUTE);
            
            retVal.put("days", days);
            retVal.put("hours", hours);
            retVal.put("minutes", minutes);
            
            return retVal;
        }

        public void resetTime() throws Exception {
            
            if (!isEnabled()) {
                if (_log.isDebugEnabled()) {
                    _log.debug("timeMachineEnabled must be set in configuration");
                    return;
                }
            }

            DateUtil.reset();
            
            daysStr = "";
            hoursStr = "";
            minutesStr = "";
            commandOutput = String.format("CurrentDate: %s", DateUtil.getCurrentDate());
        }
        
    }
    
}
