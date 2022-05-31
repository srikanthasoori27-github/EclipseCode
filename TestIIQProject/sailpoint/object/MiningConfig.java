/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object encapsulating the parameters for one undirected
 * role mining session.
 *
 * Author: Jeff
 *
 * Meaningful mining can only be performed if you carefully
 * select which applications, application attributes,
 * and attribute values you want included in the mining.
 * This mess is specified using MiningSource objects
 * which look like this:
 *
 *   <MiningSources>
 *     <MiningSource>
 *       <Application>
 *          <Reference.... name='Some App'/>
 *       </Application>
 *       <MiningAttribute name='groups'>
 *         <List>
 *          <String>asdf</String>
 *         </List>
 *       <MiningAttribute>
 *       <MiningAttribute name='emptable' permission='true'>
 *         <List>
 *          <String>select</String>
 *          <String>update</String>
 *         </List>
 *       <MiningAttribute>
 *     </MiningSource>
 *   </MiningSources>
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object encapsulating the parameters for one undirected
 * role mining session.
 */
@XMLClass
public class MiningConfig extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // General Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    //
    // Algorithms
    //
    
    /**
     * A simple bucketing algorithm that finds all distinct combinations
     * of attribute values and makes candidates out of them.
     */
    public static final String ALG_BUCKET = "bucket";

    /**
     * @ignore
     * The highly advanced and utterly useless Weka mining toolkit.
     * Enjoy!
     */
    public static final String ALG_WEKA = "weka";

    //
    // Control arguments
    //

    /**
     * The algorithm to use.
     */
    public static final String ARG_ALGORITHM = "algorithm";

    /**
     * For integration with external mining tools, the Java class
     * that implements the integration interface.
     */
    public static final String ARG_EXT_MINING_CLASS = "miningClass";

    /**
     * For integration with external mining tools, the Java classpath
     * necessary to run the mining_class.
     */
    public static final String ARG_EXT_MINING_CLASSPATH = "miningClasspath";

    //
    // Attribute Aggregation arguments
    //

    /**
     * The applications we will include.
     * In practice multi-application candidates are not found,
     * the applications are processed in sequence. The current
     * profile model cannot support multi-application filters, but
     * with the eventual migration to IdentitySelectors rather
     * than Filters it will.
     * 
     * @deprecated This is deprecated, applications are now represented
     * with ApplicationConstraint objects.
     */
    @Deprecated
    public static final String ARG_APPLICATIONS = "applications";

    /**
     * An optional name of a population used to define the 
     * identities to examine. If one is not specified 
     * identities are examined up to the ARG_MAX_INSTANCES limit.
     */
    public static final String ARG_POPULATION = "population";

    /**
     * The maximum number of candidate instances to include.
     * It is STRONGLY recommended that you set this to a value
     * less than a thousand.
     */
    public static final String ARG_MAX_INSTANCES = "maxInstances";

    /**
     * The minimum number of identities that need to match a role
     * for it to be included in the result.
     */
    public static final String ARG_MIN_USAGE = "minUsage";

    /**
     * The maximum number of candidate roles to generate.
     */
    public static final String ARG_MAX_ROLES = "maxRoles";

    /**
     * boolean option to include the manager name with the
     * mining attributes. This is generally off since Weka
     * does not handle string values and the enumeration 
     * would be too large.
     */
    public static final String ARG_INCLUDE_MANAGER = "includeManager";

    //////////////////////////////////////////////////////////////////////
    //
    // WEKA Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Clustering arguments
    //

    public static final String ARG_CLUSTER_DEBUG = "clusterDebug";
    public static final String ARG_MAX_ITERATIONS = "clusterMaxIterations";
    public static final String ARG_MIN_DEVIATION = "clusterMinDeviation";
    // same as MAX_ROLES
    public static final String ARG_NUM_CLUSTERS = "clusterNumClusters";
    
    //
    // Classifier arguments
    //

    public static final String ARG_BINARY_SPLITS = "classifierBinarySplits";
    public static final String ARG_CONFIDENCE = "classifierConfidence";
    public static final String ARG_MIN_INSTANCES = "classifierMinInstances";
    public static final String ARG_FOLDS = "classifierFolds";
    public static final String ARG_REDUCED_ERROR_PRUNING = "classifierReducedErrorPruning";
    public static final String ARG_NO_CLEANUP = "classifierNoCleanup";
    public static final String ARG_NO_SUBTREE_RAISING = "classifierNoSubtreeRaising";
    public static final String ARG_NO_PRUNING = "classifierNoPruning";
    public static final String ARG_SEED = "classifierSeed";
    public static final String ARG_LAPLACE = "classifierUseLaplace";

    //
    // Defaults
    //

    public static final String DEF_MAX_INSTANCES = "10000";
    public static final String DEF_MAX_ITERATIONS = "100";
    public static final String DEF_NUM_CLUSTERS = "50";
    public static final String DEF_MIN_DEVIATION = "1e-6";
    public static final String DEF_CONFIDENCE = "0.25";
    public static final String DEF_MIN_INSTANCES = "2";
    public static final String DEF_FOLDS = "3";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Simple arguments, named by the constants above.
     */
    Attributes<String,Object> _arguments;

    /**
     * The list of sources defining which applications, 
     * attributes, and permissions to include.
     */
    List<MiningSource> _sources;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public MiningConfig() {
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        _arguments = args;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<MiningSource> getSources() {
        return _sources;
    }

    public void setSources(List<MiningSource> sources) {
        _sources = sources;
    }

    /**
     * Initialize the argument map with some typical defaults.
     * Normally used only when creating new configs.
     */
    public void initArguments() {

        put(ARG_MAX_INSTANCES, DEF_MAX_INSTANCES);
        put(ARG_MAX_ITERATIONS, DEF_MAX_ITERATIONS);
        put(ARG_NUM_CLUSTERS, DEF_NUM_CLUSTERS);

        put(ARG_MIN_DEVIATION, DEF_MIN_DEVIATION);
        put(ARG_CONFIDENCE, DEF_CONFIDENCE);
        put(ARG_MIN_INSTANCES, DEF_MIN_INSTANCES);
        put(ARG_FOLDS, DEF_FOLDS);
    }

    /**
     * Fully load the object from Hibernate.
     */
    public void load() {
        // assume we can't have references in the Map but it would
        // be easy enough just to walk the values and call getName()
        if (_sources != null) {
            for (MiningSource src : _sources)
                src.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience Accessors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the source list, bootstrapping it if necessary
     * from the pre-3.0 configuration attribute.
     */
    public List<MiningSource> getSources(Resolver r) 
        throws GeneralException {

        upgrade(r);
        return _sources;
    }

    /**
     * Upgrade the older attribute into a list of MiningSources.
     */
    @SuppressWarnings("deprecation")
    public void upgrade(Resolver r) throws GeneralException {
        String csv = getString(ARG_APPLICATIONS);
        if (csv != null) {
            List<String> names = Util.csvToList(csv);
            if (names != null) {
                for (String name : names) {
                    Application app = r.getObjectByName(Application.class, name);
                    if (app != null) {
                        MiningSource src = getSource(app);
                        if (src == null) {
                            src = new MiningSource(app);
                            add(src);
                        }
                    }
                }
            }
            _arguments.remove(ARG_APPLICATIONS);
        }
    }

    public void add(MiningSource src) {
        if (src != null) {
            if (_sources == null)
                _sources = new ArrayList<MiningSource>();
            _sources.add(src);
        }
    }

    /**
     * Find a matching source.
     */
    public MiningSource getSource(Application app) {
        MiningSource found = null;
        if (app != null && _sources != null) {
            for (MiningSource src : _sources) {
                if (app.equals(src.getApplication())) {
                    found = src;
                    break;
                }
            }
        }
        return found;
    }

    public String getAlgorithm() {
        return getString(ARG_ALGORITHM);
    }

    public void setAlgorithm(String s) {
        put(ARG_ALGORITHM, s);
        
    }

    // Since most of the configuration is in the arguments map, provide
    // convenience accessors so callers don't have to dig out the Attributes

    public Object get(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }

    public String getString(String name) {
        return (_arguments != null) ? _arguments.getString(name) : null;
    }

    public int getInt(String name) {
        return getInt(name, 0);
    }

    public int getInt(String name, int dflt) {
        return (_arguments != null) ? _arguments.getInt(name) : dflt;
    }

    public boolean getBoolean(String name) {
        return (_arguments != null) ? _arguments.getBoolean(name) : false;
    }

    public float getFloat(String name) {
        return getFloat(name, 0.0f);
    }

    public float getFloat(String name, float dflt) {
        return (_arguments != null) ? _arguments.getFloat(name) : dflt;
    }

    public void put(String name, Object value) {

        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        _arguments.put(name, value);
    }

    /**
     * For the selection grid, summarize the application names used
     * in sources.
     */
    public String getSourceSummary() {
        String summary = null;
        if (_sources != null) {
            List<String> names = new ArrayList<String>();
            for (MiningSource src : _sources) {
                Application app = src.getApplication();
                if (app != null)
                    names.add(app.getName());
            }
            summary = Util.listToCsv(names);
        }
        return summary;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MiningSource
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass
    static public class MiningSource {
        
        Application _application;
        List<MiningAttribute> _attributes;

        public MiningSource() {
        }

        public MiningSource(Application app) {
            _application = app; 
        }

        @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
        public Application getApplication() {
            return _application;
        }

        public void setApplication(Application a) {
            _application = a;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<MiningAttribute> getAttributes() {
            return _attributes;
        }

        public void setAttributes(List<MiningAttribute> atts) {
            _attributes = atts;
        }

        public void load() {
            if (_application != null)
                _application.getName();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MiningAttribute
    //
    //////////////////////////////////////////////////////////////////////

    static public class MiningAttribute {

        String _name;
        boolean _permission;
        List<String> _values;
        boolean _negative;
        Map<String,String> _valueMap;

        public MiningAttribute() {
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<String> getValues() {
            return _values;
        }

        public void setValues(List<String> values) {
            _values = values;
        }

        @XMLProperty
        public boolean isPermission() {
            return _permission;
        }

        public void setPermission(boolean b) {
            _permission = b;
        }

        @XMLProperty
        public boolean isNegative() {
            return _negative;
        }

        public void setNegative(boolean b) {
            _negative = b;
        }

        /**
         * Return true if a given atomic value is allowed.
         * Build a search map since this will be called many times. 
         */
        public boolean isAllowed(Object value) {
            boolean allowed = false;
            if (_values == null || _values.size() == 0)
                allowed = true;
            else if (value != null) {
                String svalue = value.toString();
                if (_valueMap == null) {
                    _valueMap = new HashMap<String,String>();
                    for (String v : _values)
                        _valueMap.put(v, v);
                }
                allowed = (_valueMap.get(value) != null);
            }
            return allowed;
        }
    }

}
