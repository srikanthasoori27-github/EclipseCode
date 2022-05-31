/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The evaluation engine for the IdentitySelector object.
 * 
 * Author: Jeff
 *
 * I wanted to split this ut of IdentitySelector since we potentially need 
 * access to things outside of the object model layer.  
 * 
 * MATCH TRACKING
 *
 * It is sometimes a customer requirement to know why roles were assigned
 * or detected (JPMC started this).  The current EntitlementCorrelator
 * maintains a Map<Bundle,List<EntitlementGroup>> that is updated
 * as profiles are evaluated.  We would like something similar
 * for IdentitySelector evaluation.
 *
 * I started using EntitlementGroup but those are pretty verbose
 * in XML and we'll be storing a  lot of these in the cube for
 * presentation in the UI.  The IdentityItem was added to provide
 * a simple flat model to keep track of things in the identity cube.
 *
 */

package sailpoint.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.certification.ApplicationCache;
import sailpoint.certification.EntitlementSelector;
import sailpoint.certification.RoleSelector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.CompoundFilter;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.IdentityMatcher;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.EntitlementAttributes;
import sailpoint.object.IdentitySelector.IAttribute;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.IdentitySelector.RoleAttributes;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Script;
import sailpoint.object.TargetAssociation;
import sailpoint.search.JavaMatcher;
import sailpoint.search.Matcher;
import sailpoint.service.EffectiveAccessListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;

/**
 * The evaluation engine for the IdentitySelector object.
 *
 * @author Jeff
 */
public class Matchmaker implements IdentityMatcher, IdentitySelector.MatchMonitor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Matchmaker.class);

    /**
     * Name of the argument holding the Identity being matched when
     * calling scripts or rules.
     */
    public static final String ARG_IDENTITY = "identity";

    /**
     * Pragma it is recognize in script and rule source to 
     * convey the applications involved. Should be specified in
     * the rule or script source prefixed with '@' and followed
     * by a comma-separated list of application names.
     */
    public static final String PRAGMA_RELEVANT_APPS = 
        "RelevantApplications";

    public static String ARG_APPLICATION_CACHE = "applicationCache";
    public static String ARG_ENTITLEMENT_SELECTORS = "entSelectors";
    public static String ARG_ROLE_SELECTORS = "roleSelectors";

    /**
     * The source of all power.
     */
    SailPointContext _context;

    /**
     * Arguments passed to expressions and rules.
     * This typically contains some number of static args defined
     * during construction, then can be incrementally modified 
     * for each evaluation.
     */
    Map<String,Object> _arguments;

    /**
     * Transient field set by isMatch when evaluating a script or rule.
     * In some contexts the IdentitySelector not only has a truthy boolean
     * result, but the object that the rule returns must also be accessible
     * by the caller. The main use of this is for GenericConstraints which
     * use an IdentitySelector to see if an identity violates a policy, but
     * need to let the selector rule generate a PolicyViolation with information
     * about the violation that cannot be statically defined in the
     * GenericConstraint.
     */
    Object _lastMatchValue;

    /**
     * Transient field holding information about which things in the 
     * identity are used to satisfy a match.
     */
    List<IdentityItem> _matchItems;

    /**
     * Transient field set by the IdentitySelector.Monitor
     * method that tracks applications used during match evaluation.
     */
    List<Application> _matchApplications;

    /**
     * Cache of information about application entitlements.
     * This is used in matching EntitlementAttribute MatchTerm
     */
    ApplicationCache _applicationCache;

    /**
     * Cache of entitlement selectors. key is string representation of the match term.
     * This is used in matching EntitlementAttribute MatchTerm
     */
    Map<String, EntitlementSelector> _entSelectors;

    /**
     * Cache of role selectors.  key is string representation of the match term.
     * This is used in matching RoleAttribute MatchTerm
     */
    Map<String, RoleSelector> _roleSelectors;
    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public Matchmaker(SailPointContext con) {
        _context = con;
        _applicationCache = new ApplicationCache(con);
        _entSelectors = new HashMap<String, EntitlementSelector>();
        _roleSelectors = new HashMap<String, RoleSelector>();
    }

    /**
     * Constructor.
     */
    public Matchmaker(SailPointContext con, Map<String,Object> args) {
        _context = con;
        _arguments = args;
        if (_arguments != null) {
            _applicationCache = (ApplicationCache) _arguments.get(ARG_APPLICATION_CACHE);
            _entSelectors = (Map<String, EntitlementSelector>) _arguments.get(ARG_ENTITLEMENT_SELECTORS);
            _roleSelectors = (Map<String, RoleSelector>) _arguments.get(ARG_ROLE_SELECTORS);
        }
        if (_applicationCache == null) {
            _applicationCache = new ApplicationCache(con);
        }
        if (_entSelectors == null) {
            _entSelectors = new HashMap<String, EntitlementSelector>();
        }        
        if (_roleSelectors == null) {
            _roleSelectors = new HashMap<String, RoleSelector>();
        }

    }

    /**
     * Set the arguments to use for for script and rule evaluation.
     */
    public void setArguments(Map<String,Object> args) {
        _arguments = args;

    }

    /**
     * Set an argument to use for for script and rule evaluation.
     */
    public void setArgument(String name, Object value) {
        if (_arguments == null)
            _arguments = new HashMap<String,Object>();
        _arguments.put(name, value);
    }

    /**
     * Remove an argument if it exists
     */
    public void removeArgument(String name) {
        if (_arguments != null) {
            _arguments.remove(name);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Evaluation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Evaluate any of the matching rules in a selector against an identity.
     * There is not usually more than one but if there are all must match.
     * 
     * For script and rule selectors, remember the returned object.  
     * In addition to being logically true, in some cases (such as policy
     * violation selectors) they are allowed to return an object that is
     * used by the caller (for example, a custom-built PolicyViolation).  
     * To keep the signature simple, a simple boolean is returned for
     * "truthiness" and the last value in a field that can be accessed
     * with getLastMatchValue() saved.
     *
     * @param selector  The IdentitySelector to evaluate.
     * @param identity  The Identity to evaluate the selector against.
     * 
     * @ignore
     * !! Think more about a richer interface for scripts and rules
     * to return things.  One thing we would like is to know exactly
     * which entitlements or attributes in the identity went into the
     * decision to make a postiive match.  We can do this automatically
     * for MatchExpressions but not scripts or rules.  "Pragmas" that
     * have been discussed to declare the applications used in the rule
     * probably aren't enough since there could be different combinations
     * of entitlements that match.
     * 
     */
    public boolean isMatch(IdentitySelector selector, Identity identity)
        throws GeneralException {

        boolean match = true;
        _lastMatchValue = null;
        _matchApplications = null;
        _matchItems = null;

        MatchExpression exp = selector.getMatchExpression();
        if (exp != null) {
            ExpressionMatcher expMatcher = new ExpressionMatcher(exp);
            match = expMatcher.match(identity);
        }

        if (match) {
            CompoundFilter f = selector.getFilter();
            if (f != null) {
                // This understands compound filter prefixes
                // against Identity objects, but not Lists!!
                // You can't do links.application.id for example.
                // Either need to fix this or handle it like we do
                // for populations and make database query.
                CubeMatcher matcher = new CubeMatcher(f);
                match = matcher.matches(identity);
            }
        }

        if (match) {
            GroupDefinition pop = selector.getPopulation();
            if (pop != null) {
                // Populations created from the analyzer commonly
                // contain complex join paths like links.application.id
                // which CubeMatcher can't deal with right now.  We have
                // to convert these into an actual database query.
                Filter f = pop.getFilter();
                if (f != null) {
                    match = doQuery(identity, f);
                }
            }
        }

        if (match) {
            Script script = selector.getScript();
            if (script != null) {
                // this changes every time
                setArgument(ARG_IDENTITY, identity);
                Object result = _context.runScript(script, _arguments);
                match = isTruthy(result);
                // may be interesting in some contexts
                _lastMatchValue = result;
                if (match)  
                    checkPragmas(script.getSource());
            }
        }

        if (match) {
            Rule rule = selector.getRule();
            if (rule != null) {
                setArgument(ARG_IDENTITY, identity);
                Object result = _context.runRule(rule, _arguments);
                match = isTruthy(result);

                // in theory we could have both a script and a rule, 
                // prefer the rule
                if (result != null)
                    _lastMatchValue = result;
                
                if (match)
                    checkPragmas(rule.getSource());
            }
        }

        return match;
    }

    /**
     * Formerly used Util.otob here but that is fairly strict about truth.  
     * For selectors used with policies you want the scripts and rules to return
     * PolicyViolation objects to represent truth. So for selectors
     * truthy is defined as any value that is not one of the following:
     *
     *      null
     *      "false"
     *      Boolean(false)
     *
     */
    private boolean isTruthy(Object result) {

        boolean truthy = false;

        if (result instanceof Boolean)
            truthy = ((Boolean)result).booleanValue();

        else if (result instanceof String)
            truthy = !((String)result).equalsIgnoreCase("false");
        
        else if (result != null)
            truthy = true;
        
        return truthy;
    }

    /**
     * After running a Script or a Rule that returns truthy,
     * look for the @RelevantApplications pragma and if it
     * exists add the referenced applications to the _matchedApplications list
     * for Interrogator.
     */
    private void checkPragmas(String source) throws GeneralException {

        String token = "@" + PRAGMA_RELEVANT_APPS;
        int psn = source.indexOf(token);
        int max = source.length();
        while (psn > 0) {
            int from = psn + token.length();
            int end = from + 1;
            while (end < max && source.charAt(end) != '\n')
                end++;
            
            if (end < max) {
                String remainder = source.substring(from, end);
                // trim leading and trailing whitespace
                remainder = remainder.trim();
            
                // this is supposed to be a csv
                List<String> names = Util.csvToList(remainder);
                if (names != null) {
                    for (String name : names) {
                        Application app = _context.getObjectByName(Application.class, name);
                        if (app != null)
                            matchMonitorApplication(app);
                        else {
                            // invalid name in the pragma
                            if (log.isWarnEnabled())
                                log.warn("Unknown application referenced in pragma: " + name);
                        }
                    }
                }
                psn = source.indexOf(token, end);
            }
            else {
                // overflow trying to find the end of a pragma line, stop
                psn = -1;
            }
        }
    }

    /**
     * Take a Filter from a population and add a term that selects
     * the identity being analyzed. If the result count is 1, then
     * the identity matches the filter
     */
    private boolean doQuery(Identity identity, Filter filter) 
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        Filter combo = Filter.and(Filter.eq("id", identity.getId()),
                                  filter);
        ops.add(combo);

        int count = _context.countObjects(Identity.class, ops);

        // this could only happen if there was more than one object with
        // the same id, or we have a logic error somewhere in filter processing
        if (count > 1)
            log.error("Selector filter produced more than one result");
        
        return (count > 0);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Extended Results
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the value returned by the script or rule during the last
     * call to isMatch().
     */
    public Object getLastMatchValue() {

        return _lastMatchValue;
    }

    /**
     * Return a list of Applications involved in the last match evaluation.
     */
    public List<Application> getLastMatchApplications() {

        return _matchApplications;
    }

    /**
     * Return a list of IdentiyItems involved in the last match evaluation.
     */
    public List<IdentityItem> getLastMatchItems() {

        return _matchItems;
    }

    /**
     * Add the names of the matched applications from the last match evaluation
     * into the given list. This is easier for policies that have more than one
     * IdentitySelector.
     *
     * @param names  The List of names to which to add the matched applications.
     */
    public void getLastMatchApplications(List<String> names) {
        if (names != null && _matchApplications != null) {
            for (Application app : _matchApplications) {
                String name = app.getName();
                if (!names.contains(name))
                    names.add(name);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IdentitySelector.Monitor
    //
    //////////////////////////////////////////////////////////////////////
   
    /**
     * IdentitySelector.match callback when a matching
     * term involving an application is reached.
     */
    public void matchMonitorApplication(Application app) {
        if (app != null) {
            if (_matchApplications == null)
                _matchApplications = new ArrayList<Application>();
            if (!_matchApplications.contains(app))
                _matchApplications.add(app);
        }
    }

    /**
     * IdentitySelector.match callback when a matching 
     * identity or account attribute is found. Link is null for identity
     * attributes.
     */
    public void matchMonitorAttribute(Link link, String name, Object value) {
        if (name != null)
            add(new IdentityItem(link, name, value));
    }

    /**
     * IdentitySelector.match callback when a matching permission is found.
     * Link cannot be null here.
     */
    public void matchMonitorPermission(Link link, Permission p, Object value) {
        if (link != null && p != null)
            add(new IdentityItem(link, p, value));
    }

    private void add(IdentityItem item) {
        if (item != null) {
            if (_matchItems == null) 
                _matchItems = new ArrayList<IdentityItem>();
            _matchItems.add(item);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CubeMatcher
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Extension of JavaMatcher used with Filter evaluation.
     * Here the target object is expected to be an Identity and
     * compound filter prefixes are recognized.
     *
     * If property has a prefix of this form:
     *
     *       target:property
     *
     * then this must be a compound filter and the target
     * identifies an Application. The target can either be
     * an application name or a numeric index into the
     * the application list of the CompoundFilter.
     * Find the Link for that Application and go from there.  
     * If there is no prefix this must be an Identity property.
     *
     * After stripping the prefix a property can either be a simple
     * property name or a path such as:
     *
     *       manager.name
     *       application.owner.name
     * 
     * All but the final token on the path are assumed to be Java
     * bean properties that can be accessed by reflection. The values
     * of these properties must be SailPointObject. The final
     * token can either be a bean property or an extended attribute name.
     * If there is no bean property with that name 
     * use the SailPointObject.getExtendedAttributes method to get
     * the extended attribute map.
     *
     * @ignore
     * In retrospect it would have been nice if SailPointObject had
     * a getAnyAttribute method that could be overloaded to provide
     * all objects with a Map-like interface.  But this would require
     * each subclass to use reflection on itself or maintain a list
     * of string comparisons for property names like we do in the
     * Identity.getAttribute method for "manager".  I'm not sure which
     * handling the reflection out here simplifies what the subclasses
     * have to do but a Map-like interface could be useful in other places.
     */
    static public class CubeMatcher extends JavaMatcher {

        Filter _simpleFilter;
        CompoundFilter _compoundFilter;

        /**
         * Constructor.
         *
         * @param filter  The Filter to match.
         */
        public CubeMatcher(Filter filter) {
            super(filter);
        }

        /**
         * Constructor.
         *
         * @param cfilter  The CompoundFilter to match.
         */
        public CubeMatcher(CompoundFilter cfilter) {
            super(cfilter.getFilter());
            _compoundFilter = cfilter;
        }

        /**
         * Return the value for the property referenced by the given leaf
         * filter on the given Identity.
         *
         * {@inheritDoc}
         */
        public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
            throws GeneralException {

            Object value = null;

            if (o != null) {
                if (!(o instanceof Identity))
                    throw new GeneralException("Expected an Identity: " + o);

                Identity ident = (Identity)o;
                String prop = leaf.getProperty();
                SailPointObject target = ident;

                if (_compoundFilter != null) {
                    int length = prop.length();
                    int colon = prop.indexOf(":");
                    if (colon == (length - 1)) {
                        // either an empty string or 
                        // prefix without attribute "foo:"
                        syntaxError(prop);
                    }
                    else if (colon == 0) {
                        // fringe case ":foo", still an identity target
                        prop = prop.substring(1);
                    }
                    else if (colon > 0) {
                        String prefix = prop.substring(0, colon);
                        prop = prop.substring(colon + 1);
                        // may return null
                        target = getLink(ident, prefix, prop);
                    }
                }
                
                // If we have no target treat the value as null rather than
                // throwing.  The filter may actually be testing for null.
                if (target != null)
                    value = getAttributePath(target, prop);
            }
            return value;
        }

        private void syntaxError(String prop) throws GeneralException {

            throw new GeneralException("Property syntax error: " + prop);
        }

        /**
         * Lookup a Link in an Identity with an application identifier.
         * The identifier can either be a name or a numeric index
         * into the CompoundFilter application list.
         *
         * !! Need to be handling template applications.  Will 
         * need another delimiting convention.
         *
         * !! Need to redesign this to work like MatchExpression which
         * looks at ALL links if an identity has more than one. Currently
         * we may consider the match a failure if we pick the
         * wrong random link.  Not such a big deal right now because
         * no one uses compound filters.
         * 
         * If an Identity has more than one Link on an Application one is
         * picked at random. This seems okay but CompoundFilter could be 
         * extended  to have more than just an Application
         * reference in the index. This would also be a way to 
         * represent template instances.
         */
        private Link getLink(Identity ident, String appid, String prop)
            throws GeneralException {

            Link found = null;

            boolean isNumber = true;
            for (int i = 0 ; i < appid.length() ; i++) {
                char ch = appid.charAt(i);
                if (!Character.isDigit(ch)) {
                    isNumber = false;   
                    break;
                }
            }

            if (isNumber) {
                // we can't be here if this isn't a CompoundApplication
                int index = Util.atoi(appid);
                Application app = _compoundFilter.getApplication(index);
                if (app == null) {
                    // hmm, I suppose we could have applications with
                    // names that look like numbers, let it fall through
                    //syntaxError(prop);
                }
                else {
                    // any template instance, any nativeIdentity
                    found = ident.getLink(app, null, null);
                }
            }
            
            if (found == null) {
                // try to lookup by name
                List<Link> links = ident.getLinks();
                if (links != null) {
                    for (Link link : links) {
                        // I suppse we can support ids here but the
                        // only reason for using prefixes is to get
                        // readable names and avoid the index
                        Application app = link.getApplication();
                        if ( app != null ) {
                            String linkAppName = app.getName();
                            String linkAppId = app.getId();
                            if ( Util.nullSafeEq(appid,linkAppName) || 
                                 Util.nullSafeEq(appid,linkAppId) ) {
                                found = link;
                                break;
                            }
                        }
                    }
                }
            }

            return found;
        }

        /**
         * Retrieve an attribute that can be identified with a path.
         */
        private Object getAttributePath(SailPointObject target, String prop) 
            throws GeneralException {

            Object value = null;

            if (prop.indexOf(".") < 0) {
                // a simple property reference
                value = getAttribute(target, prop);
            }
            else {
                List<String> path = parsePath(prop);
                int last = path.size() - 1;
                for (int i = 0 ; i < path.size() ; i++) {
                    Object o = getAttribute(target, path.get(i));
                    if (i == last) {
                        // it is what it is
                        value = o;
                    }
                    else if (o instanceof SailPointObject) {
                        target = (SailPointObject)o;
                    }
                    else {
                        // can't pass through this, I suppose
                        // we could support an indexing syntax
                        // for lists though ala Waveset GenericObject
                        // should we just stop or throw?
                        value = o;
                    }
                }
            }
            return value;
        }

        /**
         * Parse a dotted path into a list of tokens.
         * Could just use StringTokenizer in getAttributePath
         * but lets leave the options open in case it needs improvement.
         */
        private List<String> parsePath(String path) {
            List<String> tokens = new ArrayList<String>();
            StringTokenizer st = new StringTokenizer(path, ".");
            while (st.hasMoreTokens())
                tokens.add(st.nextToken());
            return tokens;
        }

        /** 
         * Retrieve an attribute that can either be a Java bean 
         * property or an extended attribute. The name must not
         * be a path.
         */
        private Object getAttribute(SailPointObject target, String name) 
            throws GeneralException {

            Object value = null;


            Method getter = Reflection.getAccessor(target, name);

            if (getter != null) {
                // this throws runtime exceptions, catch them
                try {
                    value = Reflection.getValue(getter, target);
                }
                catch (Throwable t) {
                    // hmm, not technically a syntax error, what to say?
                    throw new GeneralException(t);
                }
            } else {
                // it has to be an extended attribute
                Map<String, Object> extended = target.getExtendedAttributes();
                if (extended != null) {
                    value = extended.get(name);
                }
            }


            return value;
        }
    }

    public class ExpressionMatcher implements Matcher {

        MatchExpression _expression;

        public ExpressionMatcher(MatchExpression exp) {
            _expression = exp;
        }


        @Override
        public boolean matches(Object o) throws GeneralException {
            if (o instanceof Identity) {
                return match((Identity)o);
            }
            return false;
        }

        /**
         * Return true if the identity matches this selector.
         */
        public boolean match(Identity ident)
                throws GeneralException {

            boolean match = false;
            if (Util.size(_expression.getTerms()) > 0) {
                for (IdentitySelector.MatchTerm term : Util.iterate(_expression.getTerms())) {
                    TermMatcher tMatcher = new TermMatcher(term, _context);
                    match = tMatcher.match(ident, _expression.getApplication());
                    if ((!match && _expression.isAnd()) || (match && !_expression.isAnd()))
                        break;
                }
            }
            return match;
        }

        public List<IdentitySelector.MatchTerm> getMatches(Identity identity)
                throws GeneralException {
            List<IdentitySelector.MatchTerm> response = new ArrayList<IdentitySelector.MatchTerm>();
            if (Util.size(_expression.getTerms()) > 0) {
                for (IdentitySelector.MatchTerm term : Util.safeIterable(_expression.getTerms())) {
                    TermMatcher termMatcher = new TermMatcher(term, _context);
                    List<IdentitySelector.MatchTerm> tmp = termMatcher.getMatches( identity, _expression.getApplication(), _context);
                    if( _expression.isAnd() ) {
                        if( tmp.size() == 0 ) {
                            return new ArrayList<IdentitySelector.MatchTerm>();
                        }
                        else {
                            response.addAll( tmp );
                        }
                    } else {
                        if( tmp.size() != 0 ) {
                            response.addAll( tmp );
                        }
                    }
                }
            }
            return response;
        }





        public class TermMatcher implements Matcher {

            IdentitySelector.MatchTerm _term;
            SailPointContext _context;

            public TermMatcher(IdentitySelector.MatchTerm term, SailPointContext ctx) {
                _term = term;
                _context = ctx;
            }

            @Override
            public boolean matches(Object o) throws GeneralException {
                return false;
            }

            private boolean match(Identity ident, Application defaultApp) {
                boolean match = false;
                if (_term != null) {
                    if (!_term.isContainer()) {
                        return matchLeaf(ident, defaultApp);
                    }


                    for (IdentitySelector.MatchTerm child : Util.safeIterable(_term.getChildren())) {
                        TermMatcher childMatcher = new TermMatcher(child, _context);
                        match = childMatcher.match(ident, defaultApp);
                        if ((!match && _term.isAnd()) || (match && !_term.isAnd())) {
                            break;
                        }
                    }
                }
                return match;
            }

            public List<IdentitySelector.MatchTerm> getMatches(Identity ident, Application defaultApp, SailPointContext ctx ) {
                if (!_term.isContainer()) {
                    return getMatchingLeaves( ident, defaultApp, false );
                }
                List<IdentitySelector.MatchTerm> response = new ArrayList<IdentitySelector.MatchTerm>();
                for (IdentitySelector.MatchTerm term : Util.safeIterable(_term.getChildren())) {
                    TermMatcher childMatcher = new TermMatcher(term, ctx);
                    List<IdentitySelector.MatchTerm> tmp = childMatcher.getMatches(ident, defaultApp, ctx);
                    response.addAll( tmp );
                }
                return response;
            }



            private boolean matchLeaf(Identity ident, Application defaultApp) {
                return getMatchingLeaves(ident, defaultApp, true ).size() != 0;
            }

            private List<IdentitySelector.MatchTerm> getMatchingLeaves(Identity ident, Application defaultApp, boolean firstResultOnly ) {
                List<IdentitySelector.MatchTerm> response = new ArrayList<IdentitySelector.MatchTerm>();
                boolean isNegative = _term.isNegative();
                
                if (_term.getName() != null) {
                    Application app = (_term.getApplication() != null) ?
                            _term.getApplication() : defaultApp;

                    String sourceName = app != null ? app.getName() : _term.getTargetSource() != null ? _term.getTargetSource().getName() : null;

                    if (Util.isNotNullOrEmpty(sourceName)) {

                        if (_term.getType() == IdentitySelector.MatchTerm.Type.TargetPermission) {
                            boolean match = false;
                            try {
                                EffectiveAccessListService effectiveSvc = new EffectiveAccessListService(_context, null, null);

                                //Direct
                                match = effectiveSvc.hasDirectAccess(ident.getId(), sourceName, _term.getName(), _term.getValue());

                                if (_term.shouldCheckEffective() && !match) {
                                    //Effective
                                    match = effectiveSvc.hasEffectiveAccess(ident.getId(), sourceName, null, _term.getName(), _term.getValue(), TargetAssociation.TargetType.TP);
                                }

                                if (match) {
                                    response.add(_term);
                                    // note that we pass our _value which may have
                                    // less than what is in the link attribute
                                    //TODO: Do we need matchMonitor for Unstructured?
                                    //matchMonitorAttribute(link, _term.getName(), _term.getValue());
                                }

                            } catch (GeneralException ge) {
                                log.error("Error searching effective access[" + ge + "]");
                            }

                        } else if (app != null) {
                            // must be an account attribute
                            // note that there may be several links for an
                            // application, have to look at all of them
                            List<Link> links = ident.getLinks(app);
                            if (links != null) {
                                for (int i = 0; i < links.size(); i++) {
                                    Link link = links.get(i);
                                    if (_term.isPermissionType()) {
                                        List<Permission> perms = link.getPermissions();
                                        if (perms != null) {
                                            for (Permission p : perms) {
                                                if (_term.getName().equals(p.getTarget()) &&
                                                        p.hasRight(_term.getValue())) {
                                                    response.add(_term);
                                                    // note that we pass our _value which may
                                                    // have less than what is in the Permission
                                                    if (!isNegative) {
                                                        matchMonitorPermission(link, p, _term.getValue());
                                                    }
                                                    if (firstResultOnly)
                                                        break;
                                                }
                                            }
                                        } else {
                                            // What if we are searching for a null permission?  This handles
                                            // the case that the permission does not exist on the link.
                                            if (null == _term.getValue()) {
                                                response.add(_term);
                                                // create a permission with no rights.
                                                Permission p = new Permission();
                                                p.setTarget(_term.getName());
                                                p.setRights(_term.getValue());
                                                if (!isNegative) {
                                                    matchMonitorPermission(link, p, _term.getValue());
                                                }
                                                if (firstResultOnly)
                                                    break;
                                            }
                                        }


                                    } else if (_term.isEntitlementType()) {
                                        String attrType = getLinkAttributeType(_term.getName(), app);
                                        Object v = link.getAttribute(_term.getName());
                                        boolean match = compare(_term.getValue(), v, attrType);

                                        if (match) {
                                            response.add(_term);
                                            // note that we pass our _value which may have
                                            // less than what is in the link attribute
                                            if (!isNegative) {
                                                matchMonitorAttribute(link, _term.getName(), _term.getValue());
                                            }
                                        }

                                    }

                                }
                                
                                if (_term.shouldCheckEffective() && Util.isEmpty(response)) {
    
                                    try {
                                        //No direct match, Try Effective
                                        EffectiveAccessListService effectiveSvc = new EffectiveAccessListService(_context, null, null);
                                        boolean match = false;
                                        if (_term.isPermissionType()) {
                                            match = effectiveSvc.hasEffectiveAccess(ident.getId(), app.getName(), null, _term.getName(), _term.getValue(), TargetAssociation.TargetType.P);
                                        } else if (_term.isEntitlementType()) {
                                            match = effectiveSvc.hasEffectiveAccess(ident.getId(), app.getName(), _term.getName(), _term.getValue(), null, null);
                                        }
    
                                        if (match) {
                                            response.add(_term);
                                            //TODO: Anything with match monitor? -rap
                                        }
    
                                    } catch (GeneralException ge) {
                                        log.error("Error searching Effective Access", ge);
                                    }
    
                                }

                                if ((response.size() != 0)) {
                                    if (!isNegative) {
                                        matchMonitorApplication(app);
                                    }
                                }

                            }

                        }

                    } else {
                        if (MatchTerm.Type.RoleAttribute.equals(_term.getType())) {
                            boolean match = matchRoleAttribute(ident, _term);
                            if( match ) {
                                response.add(_term);
                                if (!isNegative) {
                                    matchMonitorAttribute(null, _term.getName(), _term.getValue());
                                }
                            }
                        } else if (MatchTerm.Type.EntitlementAttribute.equals(_term.getType())) {
                            boolean match = matchEntitlementAttribute(ident, _term);
                            if( match ) {
                                response.add(_term);
                                if (!isNegative) {
                                    matchMonitorAttribute(null, _term.getName(), _term.getValue());
                                }
                            }
                        } else {
                            // assume it's an Identity attribute, getAttribute will
                            // recognize the special system attributes like BUNDLES,
                            // MANAGER, etc.
                            String attrType = getIdentityAttributeType(_term.getName());

                            Object v = ident.getAttribute(_term.getName());
                            boolean match = compare(_term.getValue(), v, attrType);
                            if( match ) {
                                response.add(_term);
                                if (!isNegative) {
                                    matchMonitorAttribute(null, _term.getName(), _term.getValue());
                                }
                            }
                        }
                    }

                }
                
                //flip the result if is negative
                if (isNegative) {
                    if (Util.isEmpty(response)) {
                        response.add(_term);
                    } else {
                        response.clear();
                    }
                }
                return response;
            }

            private boolean matchEntitlementAttribute(Identity ident, MatchTerm term) {
                try {
                    EntitlementSelector selector = getEntitlementSelector(term);

                    //This might be called during access request, to check potential policy violations.
                    //We can't query IdentityEntitlement, since the change has not been processed to database.
                    for (Link link : Util.safeIterable(ident.getLinks())) {
                        Application app = link.getApplication();
                        for (Permission perm : Util.safeIterable(link.getPermissions())) {
                            if (selector.isIncluded(app, perm)) {
                                return true;
                            }
                        }
                        ApplicationCache.ApplicationInfo appinfo = _applicationCache.get(app.getName());
                        for (String key : Util.safeIterable(link.getAttributes().getKeys())) {
                            if (!appinfo.isManaged(key)) {
                                continue;
                            }
                            Object value = link.getAttribute(key);
                            if (value instanceof String) {
                                if (selector.isIncluded(app, key, (String) value)) {
                                    return true;
                                }
                            } else if (value instanceof Collection) {
                                Collection col = (Collection) value;
                                for (Object obj : Util.safeIterable(col)) {
                                    if (obj instanceof String) {
                                        if (selector.isIncluded(app, key, (String) obj)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                } catch (GeneralException ge) {
                    log.error("Error matching EntitlementAttribute", ge);
                }
                return false;
            }

            /**
             * Returns the cached EntitlementSelector.
             * 
             * This will prevent running the Filter query for every identity.
             * 
             */
            private EntitlementSelector getEntitlementSelector(MatchTerm term) throws GeneralException {
                String key = term.render();
                EntitlementSelector selector = null;
                selector = _entSelectors.get(key);
                if (selector == null) {
                    Filter filter = getMatchTermFilter(term, EntitlementAttributes.values(), ManagedAttribute.getObjectConfig());
                    
                    selector = new EntitlementSelector(_context, filter, _applicationCache);
                    _entSelectors.put(key,  selector);
                }
                return selector;
            }

            private boolean matchRoleAttribute(Identity ident, MatchTerm term) {
                //Check assigned and detected roles  
                try {
                    RoleSelector roleSelector = getRoleSelector(term);
                    
                    //This can be invoked by ImpactAnalysis during request access.
                    //Data may not be persisted to database, and only lives inside the Identity.
                    for (Bundle bundle : Util.safeIterable(ident.getAssignedRoles())) {
                        if (roleSelector.isIncluded(bundle)) {
                            return true;
                        }
                    }
                    for (Bundle bundle : Util.safeIterable(ident.getDetectedRoles())) {
                        if (roleSelector.isIncluded(bundle)) {
                            return true;
                        }
                    }
                } catch (GeneralException ge) {
                    log.error("Error matching RoleAttribute", ge);
                }
                return false;
            }

            /**
             * Returns the cached RoleSelector.
             * 
             * This will prevent running the Filter query for every identity.
             * 
             */
            private RoleSelector getRoleSelector(MatchTerm term) throws GeneralException {
                String key = term.render();
                RoleSelector selector = null;
                selector = _roleSelectors.get(key);
                if (selector == null) {
                    Filter filter = getMatchTermFilter(term, RoleAttributes.values(), Bundle.getObjectConfig());
                    
                    selector = new RoleSelector(_context, filter);
                    _roleSelectors.put(key,  selector);
                }
                return selector;
            }

            /**
             * Returns the Filter for the MatchTerm.
             */
            private Filter getMatchTermFilter(MatchTerm term, IAttribute[] attributes, ObjectConfig objectConfig) {
                Object value = term.getValue();
                
                String type = null;
                boolean isMultiValued = false;
                String nullProperty = null;
                
                //First looking into the predefined IAttributes
                if (attributes != null) {
                    for (int i = 0; i < attributes.length; ++i) {
                        if (attributes[i].getName().equals(term.getName())) {
                            type = attributes[i].getType();
                            nullProperty = attributes[i].getNullQueryProperty();
                            isMultiValued = attributes[i].isMultiValued();
                        }
                    }
                }
                if (type == null) {
                    type = getObjectAttributeType(term.getName(), objectConfig);
                }
                
                if (ObjectAttribute.TYPE_BOOLEAN.equals(type)) {
                    value = Util.otob(term.getValue());
                }
                
                Filter filter = Filter.eq(term.getName(), value);
                if (value == null) {
                    if (isMultiValued) {
                        filter = Filter.isempty(nullProperty == null ? term.getName() : nullProperty);
                    } else {
                        filter = Filter.isnull(term.getName());
                    }
                }

                return filter;
            }

            /**
             * Dig into the ObjectConfig to find attribute type. 
             * 
             * @param name Name of the attribute
             * @param objectConfig
             *
             * @return String representing the type of the role attribute, typically
             *                one of the AttributeDefinitionTypes,
             *                AttributeDefinition.TYPE_STRING if null.
             */
            private String getObjectAttributeType(String name, ObjectConfig objectConfig) {

                String attrType = null;
                
                if ( objectConfig != null ) {
                    ObjectAttribute objectAttribute = objectConfig.getObjectAttribute(name);
                    if ( objectAttribute != null ) {
                        attrType = objectAttribute.getType();
                    }
                }
                if ( attrType == null ) {
                    attrType = AttributeDefinition.TYPE_STRING;
                }
                
                return attrType;
            }
            

            /**
             * Check with the account schema to get the attribute type, if not
             * found there look in the LinkConfig. If not found in either place
             * default to a String.
             *
             * @return String representing the type of the attribute, typically
             *                one of the AttributeDefinitionTypes,
             *                AttributeDefinition.TYPE_STRING if null.
             */
            private String getLinkAttributeType(String name, Application app) {
                String attrType = null;
                Schema accountSchema = app.getAccountSchema();
                if ( accountSchema  != null ) {
                    AttributeDefinition def = accountSchema.getAttributeDefinition(name);
                    if ( def != null )
                        attrType = def.getType();
                }

                if ( attrType == null ) {
                    ObjectConfig linkConfig = Link.getObjectConfig();
                    if ( linkConfig != null ) {
                        ObjectAttribute attr = linkConfig.getObjectAttribute(name);
                        if ( attr != null )
                            attrType = attr.getType();
                    }
                }
                if ( attrType == null )
                    attrType = AttributeDefinition.TYPE_STRING;

                return attrType;
            }

            /**
             * Dig into the ObjectConfig for the Identity and
             * attempt to find the ObjectAttribute on the
             * Identity ObjectConfig.
             *
             * @return String representing the type of the attribute, typically
             *                one of the AttributeDefinitionTypes,
             *                AttributeDefinition.TYPE_STRING if null.
             */
            private String getIdentityAttributeType(String name) {

                String attrType = null;
                ObjectConfig objectConfig = Identity.getObjectConfig();
                if ( objectConfig != null ) {
                    ObjectAttribute objectAttribute = objectConfig.getObjectAttribute(name);
                    if ( objectAttribute != null ) {
                        attrType = objectAttribute.getType();
                    }
                }
                if ( attrType == null )
                    attrType = AttributeDefinition.TYPE_STRING;

                return attrType;
            }

            /**
             * Compare an attribute value from a Link or Identity with
             * this value.
             *
             * @ignore
             * TODO: Will need smarter coercions if we
             * need to handle dates.  If the value is atomic
             * coerce to a string and compare.  If the value
             * is a list assume that matching is a
             * "member of" not a full value match.
             *
             * !! What about case insensitivity?
             */
            @SuppressWarnings("rawtypes")
            private boolean compare(String termValue, Object v, String type) {

                boolean match = false;

                if ( v == null ) {
                    // null booleans are considered false
                    if ( Util.nullSafeEq(AttributeDefinition.TYPE_BOOLEAN, type) ) {
                        if ( !Util.otob(termValue) ) {
                            match =  true;
                        }
                    } else
                        match = (termValue == null);
                }
                else if (termValue != null) {

                    if ( v instanceof List ) {
                        for (Object el : (List)v) {
                            if (el == null) continue;
                            if (!(el instanceof SailPointObject)) {
                                match = termValue.equals(el.toString());
                            } else {
                                SailPointObject spo = (SailPointObject)el;
                                match = (termValue.equals(spo.getName()) ||
                                        termValue.equals(spo.getId()));
                            }
                            if (match) break;
                        }
                    }
                    else if(v instanceof Collection) {
                        for(Object el : (Collection)v) {
                            if (!(el instanceof SailPointObject)) {
                                match = termValue.equals(el.toString());
                            } else {
                                SailPointObject spo = (SailPointObject)el;
                                match = (termValue.equals(spo.getName()) ||
                                        termValue.equals(spo.getId()));
                            }
                            if (match) break;
                        }
                    }
                    else if( v instanceof Boolean ) {
                        match = v.toString().equalsIgnoreCase( termValue );
                    }
                    else if (v instanceof SailPointObject) {
                        SailPointObject spo = (SailPointObject)v;
                        match = (termValue.equals(spo.getName()) ||
                                termValue.equals(spo.getId()));
                    }
                    else {
                        match = v.equals(termValue);
                    }
                } else {
                    //termValue is null
                    //empty list is considered as null
                    if (v instanceof Collection) {
                        match = Util.isEmpty((Collection)v);
                    }
                }
                return match;
            }
        }


    }



}
