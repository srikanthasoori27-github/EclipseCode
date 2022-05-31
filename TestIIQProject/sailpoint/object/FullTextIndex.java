/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object describing the configuration and build state of a full-text index.
 *
 * Jeff
 *
 * While we currently only have one of these for ManagedAttribute, it is likely
 * we'll find other uses for Lucene so I factored out the config parameters
 * into a new class.
 * 
 */

package sailpoint.object;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;

import sailpoint.fulltext.ConfigurableAnalyzer;
import sailpoint.fulltext.CustomAnalyzer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

@XMLClass
public class FullTextIndex extends SailPointObject {
    private static Log log = LogFactory.getLog(FullTextIndex.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Field
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The definition of a field within the index and how it will be used.
     *
     * Analyzed means that the field will be broken up and indexed for
     * full text search with substring matching.
     *
     * Indexed means that the field will be stored and can be used in 
     * filters, but cannot be used with substring matching.
     *
     * Stored means that the field will be returned in the search result,
     * but it cannot be used in full text search or filters.
     *
     * Ignored means that the field may be sent down in an IdentityIQ Filter
     * but it should be ignored.
     */
    @XMLClass
    public static class FullTextField extends AbstractXmlObject {
        
        private String _name;
        private boolean _analyzed;
        private boolean _indexed;
        private boolean _stored;
        private boolean _ignored;
        private int _boost;

        public FullTextField() {
        }

        public FullTextField(String name) {
            _name = name;
        }

        @XMLProperty
        public void setName(String name) {
            _name = name;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setAnalyzed(boolean b) {
            _analyzed = b;
        }

        public boolean isAnalyzed() {
            return _analyzed;
        }
        
        @XMLProperty
        public void setIndexed(boolean b) {
            _indexed = b;
        }

        public boolean isIndexed() {
            return _indexed;
        }
        @XMLProperty
        public void setStored(boolean b) {
            _stored = b;
        }

        public boolean isStored() {
            return _stored;
        }
        @XMLProperty
        public void setIgnored(boolean b) {
            _ignored = b;
        }

        public boolean isIgnored() {
            return _ignored;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An attribute holding the base path of the index.
     * The full path will be this plus the class name plus "Index".
     */
    public static final String ATT_INDEX_PATH = "indexPath";

    /**
     * An attribute that when true disables use of the index.
     */
    public static final String ATT_DISABLED = "disabled";

    /**
     * An attribute holding the definitions of the fields.
     */
    public static final String ATT_FIELDS = "fields";

    /**
     * An attribute holding a CSV list of classes to include in the index.
     * Added for BundleManagedAttribute, no longer needed after the 7.1
     * refactoring.
     */
    public static final String ATT_CLASSES = "classes";

    /**
     * An attribute holding the name of the indexer class to use.
     * If not specified it defaults to the name of this object followed
     * by "Indexer".
     */
    public static final String ATT_INDEXER = "indexer";

    /**
     * An attribute holding the name of the org.apache.lucene.analysis.Analyzer
     * to use.  If none is specified we default to our custom
     * sailpoint.fulltext.CaseInsensitiveWhitespaceAnalyzer
     * 
     * Older versions of the product used the 
     * org.apache.lucene.analysis.standard.StandardAnalyzer
     * 
     * To specify a set of delimiter characters, use
     * sailpoint.fulltext.CustomAnalyzer
     */
    public static final String ATT_ANALYZER = "analyzer";

    /**
     * An attribute holding a Map of configuration parameters to pass into 
     * configurable Analyzers.  The only configurable Analyzer right now is 
     * sailpoint.fulltext.CustomAnalyzer
     */
    public static final String ATT_ANALYZER_CONFIG = "analyzerConfig";

    /**
     * When true, include indexed attributes and permissions with 
     * both entitlements and roles.  These are modeled as
     * TargetAssociations built by Aggregator.
     */
    public static final String ATT_INCLUDE_TARGETS = "includeTargets";

    /**
     * When true, include objects that have been marked disabled.
     * Currently this is only for the role indexes, LCM leaves out disabled
     * roles, but the slicer/dicer wants them.
     */
    public static final String ATT_INCLUDE_DISABLED = "includeDisabled";

    
    // Attributes holding the old model for fields
    // Retained for auto-upgrade then can be deleted

    private static final String ATT_ANALYZED_FIELDS = "analyzedFields";
    private static final String ATT_INDEXED_FIELDS = "indexedFields";
    private static final String ATT_STORED_FIELDS = "storedFields";
    private static final String ATT_IGNORED_FIELDS = "ignoredFields";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // _name will be the name of a class or some other unique system 
    // defined name. 

    /**
     * The date this index was last refreshed.
     */
    Date _lastRefresh;
    
    /**
     * The attributes map, stored as an XML blob.
     */
    private Attributes<String,Object> _attributes;

    // TODO: Config options like the path to the file or 
    // the database id of a db object assuming we can make that work

    /**
     * Error messages captured during the last refresh.
     */
    List<Message> _errors;

    /**
     * Transient field lookup cache.
     */
    Map<String,FullTextField> _fieldMap;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public FullTextIndex() {
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty
    public void setLastRefresh(Date d) {
        _lastRefresh = d;
    }

    public Date getLastRefresh() {
        return _lastRefresh;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Message> getErrors() {
        return _errors;
    }

    public void setErrors(List<Message> errors) {
        _errors = errors;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public boolean isDisabled() {
        return Util.otob(get(ATT_DISABLED));
    }
    
    public void setDisabled(boolean b) {
        if (b)
            put(ATT_DISABLED, "true");
        else
            put(ATT_DISABLED, null);
    }

    @Deprecated
    public String getPath() {
        return getString(ATT_INDEX_PATH);
    }

    @Deprecated
    public void setPath(String s) {
        put(ATT_INDEX_PATH, s);
    }

    public List<FullTextField> getFields() {
        doFieldUpgrade();
        return (List<FullTextField>)get(ATT_FIELDS);
    }

    public void setFields(List<FullTextField> fields) {
        put(ATT_FIELDS, fields);
        _fieldMap = null;
    }

    /**
     * Get the names of the classes included in the index
     * @return List of String names
     */
    public List<String> getClassNames() {
        doClassUpgrade();
        return Util.csvToList(getString(ATT_CLASSES));
    }

    /**
     * Set the names of the classes included in the index
     * @param classNames List of String names
     */
    public void setClassNames(List<String> classNames) {
        put(ATT_CLASSES, classNames);
    }

    /**
     * Get a list of Class objects included in the index. Uses reflection to 
     * find classes from sailpoint.object package. 
     * @return List of Class objects
     * @throws ClassNotFoundException If any class name is not a valid SailPointObject class
     */
    public List<Class<? extends SailPointObject>> getClasses() throws ClassNotFoundException {
        List<Class<? extends SailPointObject>> classes = new ArrayList<Class<? extends SailPointObject>>();
        String argClasses = getString(ATT_CLASSES);
        List<String> classNames = Util.csvToList(argClasses);
        for (String className : classNames) {
            // allow this common alias
            if ("Role".equals(className)) {
                className = "Bundle";
            }
            classes.add((Class<? extends SailPointObject>)Class.forName("sailpoint.object." + className));
        }
        return classes;
    }

    /**
     * Get the name of the indexer to use for indexing
     * @return String name of indexer
     */
    public String getIndexerName() {
        doClassUpgrade();
        return getString(ATT_INDEXER);
    }

    /**
     * Set the name of the indexer to use for indexing 
     */
    public void setIndexerName(String indexerName) {
        put(ATT_INDEXER, indexerName);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Temporary Field Upgrade
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return true if the field list is upgraded.
     * Used by FullTextifier to know if it should write out the new model.  
     */
    public boolean doFieldUpgrade() {
        boolean upgraded = false;

        if (_attributes != null && !_attributes.containsKey(ATT_FIELDS)) {

            List<FullTextField> fields = new ArrayList<FullTextField>();

            List<String> names = getFieldList(ATT_ANALYZED_FIELDS);
            if (names != null) {
                for (String name : names) {
                    FullTextField f = internField(fields, name);
                    f.setAnalyzed(true);
                }
            }   

            names = getFieldList(ATT_INDEXED_FIELDS);
            if (names != null) {
                for (String name : names) {
                    FullTextField f = internField(fields, name);
                    f.setIndexed(true);
                }
            }   

            names = getFieldList(ATT_STORED_FIELDS);
            if (names != null) {
                for (String name : names) {
                    FullTextField f = internField(fields, name);
                    f.setStored(true);
                }
            }   

            names = getFieldList(ATT_IGNORED_FIELDS);
            if (names != null) {
                for (String name : names) {
                    FullTextField f = internField(fields, name);
                    f.setIgnored(true);
                }
            }   

            _attributes.put(ATT_FIELDS, fields);
            _attributes.remove(ATT_ANALYZED_FIELDS);
            _attributes.remove(ATT_INDEXED_FIELDS);
            _attributes.remove(ATT_STORED_FIELDS);
            _attributes.remove(ATT_IGNORED_FIELDS);
            upgraded = true;
        }
        return upgraded;
    }

    /** 
     * @return The Analyzer to use for this FullTextIndex
     */
    @SuppressWarnings("unchecked")
    public Analyzer getAnalyzer() {
        String defaultClassName = CustomAnalyzer.class.getName();
        String analyzerClassName = Util.otos(get(ATT_ANALYZER));
        if (Util.isNullOrEmpty(analyzerClassName)) {
            analyzerClassName = defaultClassName;
        }

        Constructor<? extends Analyzer> analyzerConstructor = null;
        Class<? extends Analyzer> analyzerClass = null;
        try {
            analyzerClass = (Class<? extends Analyzer>) Class.forName(analyzerClassName);
            analyzerConstructor = analyzerClass.getConstructor();
        } catch (Exception e) {
            log.debug("Could not instantiate the configured analyzer class:  " + analyzerClassName + ".  Using the default " + defaultClassName + " instead.");
            analyzerConstructor = null;
        }

        Analyzer analyzer = null;
        if (analyzerConstructor != null) {
            try {
                analyzer = analyzerConstructor.newInstance();
            } catch (Exception e) {
                log.debug("Unable to instantiate an analyzer of class " + analyzerClassName + ".  Using the default " + defaultClassName + " instead.");
            }
        }

        // Default to the CustomAnalyzer
        if (analyzer == null) {
            analyzer = new CustomAnalyzer();
        }

        if (analyzer instanceof ConfigurableAnalyzer) {
            ((ConfigurableAnalyzer)analyzer).setConfiguration((Map<String, Object>)_attributes.get(ATT_ANALYZER_CONFIG));
        }

        if (log.isDebugEnabled()) {
            log.debug("Using analyzer: " + analyzer.getClass().getName());
        }

        return analyzer;
    }

    private FullTextField internField(List<FullTextField> fields, String name) {
        FullTextField found = null;
        for (FullTextField field : fields) {
            if (name.equals(field.getName())) {
                found = field;
                break;
            }
        }
        if (found == null) {
            found = new FullTextField();
            found.setName(name);
            fields.add(found);
        }
        return found;
    }

    private List<String> getFieldList(String name) {
        List<String> fields = null;
        Object o = get(name);
        if (o instanceof List) {
            fields = (List<String>)o;
        }
        else if (o instanceof String) {
            fields = Util.csvToList((String)o);
        }
        else if (o != null) {
            // should not have been allowed
            fields = new ArrayList<String>();
            fields.add(o.toString());
            put(name, fields);
        }
        return fields;
    }

    /**
     * Upgrades FullTextIndex to add class and indexer information, if necessary.
     * @return True if upgraded, otherwise false.
     */
    public boolean doClassUpgrade() {
      
        boolean upgraded = false;
        if (_attributes != null) {
            if (!_attributes.containsKey(ATT_CLASSES)) {
                // Previous convention was that object name was the class it referenced.
                _attributes.put(ATT_CLASSES, getName());
                upgraded = true;
            }
            if (!_attributes.containsKey(ATT_INDEXER)) {
                // Previous convention was that object name plus Indexer was the indexer.
                _attributes.put(ATT_INDEXER, getName() + "Indexer");
                upgraded = true;
            }
        }
        
        return upgraded;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a map keyed by field name for master lookup.
     * Note that this may included transient fields added
     * by addTransientField.
     */
    public Map<String,FullTextField> getFieldMap() {
        if (_fieldMap == null) {
            _fieldMap = new HashMap<String,FullTextField>();
            List<FullTextField> fields = getFields();
            if (fields != null) {
                for (FullTextField field : fields)
                    _fieldMap.put(field.getName(), field);
            }
        }
        return _fieldMap;
    }

    /**
     * Lookup a field by name.
     */
    public FullTextField getField(String name) {
        Map<String,FullTextField> map = getFieldMap();
        return map.get(name);
    }

    /**
     * Sort of a kludge for Fulltextifier.
     * "targets" is an implied field that does not need to be
     * explicitly defined, but most of the code expects it to behave
     * like other fields.  This will inject runtime fields in the search
     * map but won't add it to the _fields list so it won't be persisted
     * by accident.
     */
    public void addTransientField(FullTextField field) {
        Map<String,FullTextField> map = getFieldMap();
        // if they bothered to configure it, let the object win?
        if (map.get(field.getName()) == null) {
            map.put(field.getName(), field);
        }
    }
        
    public void put(String name, Object value) {
        if (name != null) {
            if (_attributes == null)
                _attributes = new Attributes<String, Object>();
            // Attributes does this, be consistent
            if (value == null)
                _attributes.remove(name);
            else
                _attributes.put(name, value);
        }
    }

    public Object get(String name)
    {
        return ((_attributes != null) ? _attributes.get(name) : null);
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public Date getDate(String name) {
        return (_attributes != null) ? _attributes.getDate(name) : null;
    }

    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    public Object remove(String name) {

        return ((_attributes != null) ? _attributes.remove(name) : null);
    }

    public void addError(Message msg) {
        if (msg != null) {
            if (_errors == null)
                _errors = new ArrayList<Message>();
            _errors.add(msg);
        }
    }

    public void addError(String s) {
        if (s != null)
            addError(new Message(Message.Type.Error, s));
    }

    /**
     * Add a message for an exception.
     * This is what TaskManager has historically done
     * for exceptions thrown from the executor.
     */
    public void addError(Throwable t) {

        Message msg;

        // If it's one of ours, take off the class prefix to make
        // message in the UI look cleaner.
        if (t instanceof GeneralException) {
            msg = ((GeneralException)t).getMessageInstance();
            // Override the type set when the exception was created
            msg.setType(Message.Type.Error);
        }
        else {
            msg = new Message(Message.Type.Error,
                              MessageKeys.ERR_EXCEPTION, t);
        }

        addError(msg);
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("name", "Name");
        cols.put("lastRefresh", "Last Refresh");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s\n";
    }

}
