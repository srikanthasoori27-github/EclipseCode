/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Analyze options for including roles in a certification.
 *
 * Author: Jeff
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class EntitlementSelector {

    private static Log log = LogFactory.getLog(EntitlementSelector.class);
    
    SailPointContext _context;
    CertificationDefinition _definition;
    ApplicationCache _applications;
    
    Map<String,String> _inclusions;
    boolean _includeAll;
    FilterEvaluator _evaluator;
    EvaluatorSource _source;
    
    public EntitlementSelector(SailPointContext con, CertificationDefinition def, ApplicationCache appcache)
        throws GeneralException {
        
        _context = con;
        _definition = def;
        _applications = appcache;

        init();
    }

    public EntitlementSelector(SailPointContext con, Filter filter, ApplicationCache appcache) throws GeneralException {
        _context = con;
        _applications = appcache;

        buildInclusions(filter);
    }

    /**
     * Examine the filter and determine the best method for evaluating it.
     */
    private void init() throws GeneralException {

        Filter f = _definition.getEntitlementFilter();
        if (f == null) {
            _includeAll = true;
        }
        else {
            // unit test option to force the use of hash based filtering
            boolean noSimple = _definition.getBoolean(CertificationDefinition.ARG_NO_SIMPLE_ENTITLEMENT_FILTER, false);
            
            if (!noSimple && isSimple(f)) {
                // note that we don't need to use FilterProcessor to convert NE
                // since the evaluator will handle it properly
                _source = new EvaluatorSource();
                _evaluator = new FilterEvaluator(f, _source);
            }
            else {
                buildInclusions(f);
            }
        }
    }

    /**
     * A filter is simple if the leaf filter terms use only
     * the properties:
     * 
     *    application.name
     *    attribute
     *    value
     *
     * Or is effectivley empty.
     */
    private boolean isSimple(Filter f) {

        boolean simple = true;

        if (f instanceof CompositeFilter) {
            CompositeFilter cf = (CompositeFilter)f;
            for (Filter child : Util.iterate(cf.getChildren())) {
                simple = isSimple(child);
                if (!simple) {
                    break;
                }
            }
        }
        else {
            LeafFilter lf = (LeafFilter)f;
            String property = lf.getProperty();
            simple = ("application.name".equals(property) ||
                      "attribute".equals(property) ||
                      "value".equals(property));
        }

        return simple;
    }

    /**
     * Calculate inclusions from the definition.
     * If there are no inclusion filters set the includeAll flag.
     */
    private void buildInclusions(Filter f)
            throws GeneralException {

        final String MeterBuildInclusions = "EntitlementSelector: buildInclusions";
        Meter.enter(MeterBuildInclusions);

        _inclusions = new HashMap<String, String>();
        // conversions
        f = FilterProcessor.process(f);
        if (log.isInfoEnabled()) {
            log.info("Loading ManagedAttributes: " + f.toString());
        }

        QueryOptions ops = new QueryOptions();
        ops.add(f);
        List<String> props = new ArrayList<String>();
        // We used to just get the hash directly, but we can't rely on that because
        // of case sensitivity. If we ever change that hash to be based on lower case
        // then we could go back to fetching the hash directly.
        // props.add("hash");
        props.add("application.id");
        props.add("type");
        props.add("attribute");
        props.add("value");

        Iterator<Object[]> result = _context.search(ManagedAttribute.class, ops, props);
        while (result.hasNext()) {
            Object[] row = result.next();
            String appId = (String) row[0];
            String type = (String) row[1];
            String attribute = (String) row[2];
            String value = (String) row[3];
            String hash = ManagedAttributer.getHash(appId, type, Util.nullSafeLowerCase(attribute), Util.nullSafeLowerCase(value));

            if (log.isInfoEnabled()) {
                log.info("Loaded " + hash + " " + " for attribute " + attribute + " and value " + value + " on application id " + appId);
            }

            _inclusions.put(hash, hash);

        }
        Meter.exit(MeterBuildInclusions);
    }
    
    public boolean isIncluded(Application app, Permission perm)
        throws GeneralException {

        boolean include = true;

        if (!_includeAll) {
            if (_inclusions != null) {
                String hash = ManagedAttributer.getPermissionHash(app, Util.nullSafeLowerCase(perm.getTarget()));
                include = (_inclusions.get(hash) != null);
            }
            else {
                _source.setPermission(app, perm);
                include = _evaluator.eval();
            }
        
            if (log.isInfoEnabled()) {
                if (include) {
                    log.info("Including permission:" + app.getName() + " " + perm.getTarget());
                }
                else {
                    log.info("Rejecting permission:" + app.getName() + " " + perm.getTarget());
                }
            }
        }

        return include;
    }

    public boolean isIncluded(Application app, String attribute, String value)
        throws GeneralException {

        boolean include = true;

        if (!_includeAll) {
            String hash = null;
            String appname = app.getName();

            if (_inclusions != null) {
                ApplicationCache.ApplicationInfo appinfo = _applications.get(appname);
                // null appinfo really can't happen if we started with a resolved Application
                if (appinfo != null) {
                    // If this attribute was declared with a schemaObjectType then the MA
                    // is assumed to have been aggregated and the type used in the hash.
                    // If it doesn't, it was promoted during account agg, the type will be "Entitlement"
                    // and the attribute name will have been used for the hash.
                    String type = appinfo.getObjectType(attribute);
                    if (type == null)
                        type = Util.nullSafeLowerCase(attribute);

                    hash = ManagedAttributer.getEntitlementHash(app, type, Util.nullSafeLowerCase(value));

                    include = (_inclusions.get(hash) != null);
                }
            }
            else {
                _source.setEntitlement(app, attribute, value);
                include = _evaluator.eval();
            }

            if (log.isInfoEnabled()) {
                if (include) {
                    log.info("Including:" + appname + " " + attribute + " " + value);
                }
                else if (hash != null) {
                    log.info("Rejecting:" + appname + " " + attribute + " " + value + " " + hash);
                }
                else {
                    log.info("Rejecting:" + appname + " " + attribute + " " + value);
                }
            }
        }
        
        return include;
    }

    /**
     * Class used with FilterEvaluator to provide values for properties 
     * in the filter.
     */
    public class EvaluatorSource implements FilterEvaluator.ValueSource {

        Application _application;
        String _attribute;
        String _value;

        public EvaluatorSource() {
        }

        public void setEntitlement(Application app, String attribute, String value) {
            _application = app;
            _attribute = attribute;
            _value = value;
        }

        public void setPermission(Application app, Permission perm) {
            _application = app;
            _attribute = perm.getTarget();
            _value = null;
        }
        
        public Object getValue(String property) {

            Object value = null;
            
            if (property.equals("application.name")) {
                value = _application.getName();
            }
            else if (property.equals("attribute")) {
                value = _attribute;
            }
            else {
                value = _value;
            }

            return value;
        }
    }

}
