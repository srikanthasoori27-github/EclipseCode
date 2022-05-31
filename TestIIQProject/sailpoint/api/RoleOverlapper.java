/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to calculate overlap percentages between roles.
 * 
 * Author: Jeff
 *
 * This is currently used by Wonderer to generate more info for the
 * impact analysis task, but we could make this into its own task.  
 * It's pretty close to a TaskExecutor, may want to just make it one
 * so we can use it directly?
 *
 * Like risk scoring, calculating overlap as a percentage is of
 * dubius use since there are lots of unrelated things in a role and
 * you don't necessarily want inconsequential stuff like owner,
 * riskScoreWeight, and extended attributes diluting the overlap
 * percentage of the profile filters.
 *
 * The only marginally useful thing is to calculate several scores
 * based on different parts of the role:
 *
 * Assignment 
 *   - the "how do you get this" score
 *   - overlap in flattened assignment rules and profile filters,
 *     relevant for both business and IT roles
 *
 * Permits
 *  - the "what does it allow" score
 *  - overlap in the flattened profiles of the required IT roles,
 *    relevant for biz roles
 *  - might be the same as the Provisioning score?
 * 
 * Provisioning
 *   - the "what does it do" score
 *   - overlap in provisioning, often close to the Assignment 
 *     overlap since they're driven from the same profiles, but
 *     derived provisioning plans could be much simpler than the
 *     full filter and more likely to overlap
 *
 * Attribute
 *   - the "other random junk" score
 *   - overlap in extended attribute values, owner, type,
 *     riskScoreWeight, activityConfig
 *
 * Composite
 *   - since score calculated by combining the other scores
 * 
 *
 * Much of the work in here is converting the Bundle, Profile, 
 * IdentitySelector, and ProvisioningPlan models into the 
 * generic Expression model so it can all be manipulated 
 * consistently.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CompoundFilter;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleOverlapResult;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.TaskResult;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.RoleOverlapResult.RoleOverlap;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class RoleOverlapper {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Task arguments.
     */
    public static final String ARG_OVERLAP_FILTER = "overlapFilter";
    
    /**
     * Minimum overlap value a role must have in any of the overlap
     * facets for it to be included in the result.
     */
    public static final String ARG_OVERLAP_THRESHOLD = "overlapThreshold";

    /**
     * Default overlap threshold.  This should never be zero, or else
     * every role in the system will end up in the result.
     */
    public static final int DEFAULT_OVERLAP_THRESHOLD = 1;

    /**
     * Task results.
     */
    public static final String RET_OVERLAP_RESULT = "overlapAnalysis";
    public static final String RET_OVERLAP_COUNT = "overlapRolesExamined";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(RoleOverlapper.class);

    /**
     * You have to love context.
     */
    private SailPointContext _context;

    /**
     * Task arguments.
     */
    Attributes<String,Object> _arguments;

    /**
     * Optional object to be informed of status.
     * !! Try to rework _trace into this so we don't need both 
     * mechanisms...
     */
    TaskMonitor _monitor;

    /**
     * Set by the task master to stop us.   
     */ 
    boolean _terminate;

    // Jeff's silly trace
    boolean _trace;

    /**
     * Role we're analyzing.
     */
    Bundle _role;


    /** 
     * Cached unflattened role expressions.
     */
    RoleExpressions _localExpressions;
    
    /**
     * Cached flattened role expressions.
     */
    RoleExpressions _flatExpressions;

    /**
     * Minimum overlap percentage a given score must have before
     * we'll put the RoleOverlap record in the result. 
     * Default of 1 keeps completely non-overlapping roles out
     * of the result.
     */
    int _threshold = DEFAULT_OVERLAP_THRESHOLD;

    //
    // Runtime Results
    //

    /**
     * Result we're accumulating.
     */
    RoleOverlapResult _result;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleOverlapper(SailPointContext context, Attributes<String,Object> args) {
        _context = context;
        _arguments = args;

        _threshold = args.getInt(ARG_OVERLAP_THRESHOLD, DEFAULT_OVERLAP_THRESHOLD);
    }

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////
    
    public RoleOverlapResult getRoleOverlapResult() {
        return _result;
    }

    /**
     * Be careful not to conflict with result names used
     * by Wonderer which normally calls this class within itself
     * and merges the the results.
     */
    public void getResults(TaskResult result) {

        if (_result != null) {
            result.setAttribute(RET_OVERLAP_RESULT, _result);
            result.setAttribute(RET_OVERLAP_COUNT, _result.getRolesExamined());
        }

        result.setTerminated(_terminate);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    private void updateProgress(String progress) {

        trace(progress);

        if ( _monitor != null ) _monitor.updateProgress(progress);
    }

    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    private void traceOverlap(String type, OverlapRegion region) {
        if (_trace && region != null) {
            println("  " + type + ": units " + Util.itoa(region.units) +
                    " matches " + Util.itoa(region.matches) + 
                    " overlap " + Util.itoa(region.getPercentage()));
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Scan
    //
    //////////////////////////////////////////////////////////////////////

    public void terminate() {
        _terminate = true;
    }

    public void execute(Bundle role) throws GeneralException {

        _role = role;
        _result = new RoleOverlapResult(role);
        _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);
        _localExpressions = getRoleExpressions(role, false);
        _flatExpressions = getRoleExpressions(role, true);

        String progress;
        Filter filter = null;
        String filterSource = _arguments.getString(ARG_OVERLAP_FILTER);
        if (filterSource != null)
            filter = Filter.compile(filterSource);

        if (filter == null)
            progress = "Beginning overlap analysis scan...";
        else
            progress = "Beginning overlap analysis scan with filter: " + 
                filter.toString();
        updateProgress(progress);

        Decacher decacher = new Decacher(_context);
        QueryOptions ops = new QueryOptions();
        ops.add(filter);
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Bundle.class, ops, props);

        while (it.hasNext() && !_terminate) {

            String id = (String)(it.next()[0]);
            Bundle other = _context.getObjectById(Bundle.class, id);
            if (other != null) {
                // don't analyze thyself
                if (!other.getId().equals(_role.getId())) {
                    _result.incRolesExamined();
                    RoleOverlap ro = analyze(other);
                    if (ro != null) {
                        _result.add(ro);
                    }
                }
            }

            // Iterating over all roles.  Probably a good idea to decache.
            decacher.increment();
        }
    }

    /**
     * Compare the _role with another role from the database.
     */
    public RoleOverlap analyze(Bundle other)
        throws GeneralException {

        RoleOverlap ro = null;

        trace("Analyzing " + other.getName());

        int attributeOverlap = getAttributeOverlap(other);
        int localAssignment = getAssignmentOverlap(other, false);
        int flatAssignment = getAssignmentOverlap(other, true);
        int localProvisioning = getProvisioningOverlap(other, false);
        int flatProvisioning = getProvisioningOverlap(other, true);

        // Filter out roles with little or no overlap in the
        // assignment or provisioning scores, ignore attribute overlaps
        if (localAssignment >= _threshold || 
            flatAssignment >= _threshold ||
            localProvisioning >= _threshold ||
            flatProvisioning >= _threshold) {

            ro = new RoleOverlap(other);
            ro.setAttribute(attributeOverlap);
            ro.setAssignment(flatAssignment);
            ro.setLocalAssignment(localAssignment);
            ro.setProvisioning(flatProvisioning);
            ro.setLocalProvisioning(localProvisioning);
        }

        return ro;
    }

    /**
     * Calculate overlap in the attribute maps of two roles.
     * We could consider modeling this with an Expression like we
     * do for everything else, maps become a list of AND'd 
     * equality terms.  But it isn't that hard to think about
     * maps and it saves some garbage.
     *
     * The scores seem cleaner if we don't pay attention
     * to built-in attributes unless they have a non-empty value.
     * For joinRule that's null and for riskScoreWeight that's zero.
     * If we include type that will always factor in and since
     * all roles are supposed to have a type the attribute overlap
     * will often be 1/1 = 100% even if there are no attributes.
     */
    private int getAttributeOverlap(Bundle other)
        throws GeneralException {

        OverlapRegion region = new OverlapRegion();

        // hard coded attributes
        // ignore SailPointStuff like name, creator
        // ignore activityConfig since we never use it
        // ignore miningStatistics since they're transient
        
        // type
        region.units++;
        if (Differencer.equal(_role.getType(), other.getType()))
            region.matches++;

        // riskScoreWeight
        int weight1 = _role.getRiskScoreWeight();
        int weight2 = other.getRiskScoreWeight();
        if (weight1 > 0 || weight2 > 0) {
            region.units++;
            if (weight1 == weight2)
                region.matches++;
        }

        // joinRule
        Rule rule1 = _role.getJoinRule();
        Rule rule2 = other.getJoinRule();
        if (rule1 != null || rule2 != null) {
            region.units++;
            if (Differencer.equal(rule1, rule2))
                region.matches++;
        }

        Attributes<String,Object> atts1 = _role.getAttributes();
        Attributes<String,Object> atts2 = other.getAttributes();

        // This is very similar to Difference.diffMaps but we keep
        // track of the "units".  Would be nice to merge these...

        if (atts1 == null) {
            if (atts2 != null)
                region.units += atts2.keySet().size();
        }
        else if (atts2 == null) {
            region.units += atts1.keySet().size();
        }
        else {
            // !! may want an exclusion list
            List map2keys = new ArrayList(atts2.keySet());
            Iterator<Map.Entry<String,Object>> it = atts1.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry e1 = it.next();
                Object key = e1.getKey();
                // ignore things with no keys?
                if (key != null) {
                    region.units++;
                    Object value1 = e1.getValue();
                    Object value2 = atts2.get(key);
                    map2keys.remove(key);
                    Difference d = Difference.diff(value1, value2);
                    if (d == null)
                        region.matches++;
                }
            }

            // anything left over in this list was not found in map1, 
            // and is considered is considered a difference
            region.units += map2keys.size();
        }

        traceOverlap("Attribute", region);

        return region.getPercentage();
    }

    /**
     * Calculate overlap in the assignment and detection rules.
     * The "flatten" option controls whether we just consider the local
     * definitions, or whether we flatten the inherited definitions.
     * 
     * We look at two "regions" here, one for the flattened profile filters
     * and one for the flattened IdentitySelectors.  Then combine them.
     * 
     * TODO: Might want to ignore roles that aren't detectable or assignable
     * to reduce the amount of work and possibly false positives.
     */
    private int getAssignmentOverlap(Bundle other, boolean flatten) {

        // keep thees cached since we need them for every role in the scan
        Expression myProfile;
        Expression mySelector;

        if (flatten) {
            myProfile = _flatExpressions.profile;
            mySelector = _flatExpressions.selector;
        }
        else {
            myProfile = _localExpressions.profile;
            mySelector = _localExpressions.selector;
        }

        Expression otherProf = getNormalizedProfileExpression(other, flatten);
        Expression otherSelector = getNormalizedSelectorExpression(other, flatten);

        OverlapRegion region1 = getOverlap(myProfile, otherProf, true);
        OverlapRegion region2 = getOverlap(mySelector, otherSelector, false);

        String prefix = (flatten) ? "Flat " : "Local ";
        traceOverlap(prefix + "Profile", region1);
        traceOverlap(prefix + "Selector", region2);

        // combine them
        region1.assimilate(region2);

        return region1.getPercentage();
    }

    /**
     * Calculate overlap in provisioning side effects.
     */
    private int getProvisioningOverlap(Bundle other, boolean flatten) {

        // keep thees cached since we need them for every role in the scan
        Expression myProv;
        if (flatten)
            myProv = _flatExpressions.provisioning;
        else
            myProv = _localExpressions.provisioning;

        Expression otherProv = getNormalizedProvisioningExpression(other, flatten);

        OverlapRegion region = getOverlap(myProv, otherProv, false);

        String prefix = (flatten) ? "Flat " : "Local ";
        traceOverlap("Provisioning", region);

        return region.getPercentage();
    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // Expression Model Conversion
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expressions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Container of several normalized expressions derived from a role.
     * We need to maintain two of these for local and flattened 
     * role definitions.  It's easier to have a container class that
     * duplicate fields.
     */
    public static class RoleExpressions {

        /**
         * Expression respresenting the profile filters.
         */
        public Expression profile;


        /**
         * Expression representing the assignment selector.
         */
        public Expression selector;

        /**
         * Expression representing the provisioning plan.
         */
        public Expression provisioning;

    };

    /**
     * Generate Expression representations of a role.
     * This is intended to be cached for reuse.
     */
    private RoleExpressions getRoleExpressions(Bundle role, boolean flatten) {

        RoleExpressions exp = new RoleExpressions();

        exp.profile = getNormalizedProfileExpression(role, flatten);
        exp.selector = getNormalizedSelectorExpression(role, flatten);
        exp.provisioning = getNormalizedProvisioningExpression(role, flatten);

        return exp;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Profile Expression
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Normalize an (optionally flattened) profile expression for a role.
     */
    private Expression getNormalizedProfileExpression(Bundle role, boolean flatten) {

        Expression e;
        if (flatten)
            e = getFlatProfileExpression(role);
        else
            e = getProfileExpression(role);

        normalize(e);

        return e;
    }

    /**
     * Convert a flattened hierarchy of profiles into an Expression.
     * Inherited profiles are effectively AND'd together.
     */
    private Expression getFlatProfileExpression(Bundle role) {

        Expression exp = null;

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance == null || inheritance.size() == 0) {
            // no inheritance, just return the local expression
            exp = getProfileExpression(role);
        }
        else {
            exp = new Expression(Expression.AND);
            getFlatProfileExpression(exp, role);
        }

        return exp;
    }

    /**
     * Walk a role hierarchy accumulating profile expressions.
     */
    private void getFlatProfileExpression(Expression exp, Bundle role) {

        // first walk upward
        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null) {
            for (Bundle parent : inheritance)
                getFlatProfileExpression(exp, parent);
        }

        // then add ours
        Expression myexp = getProfileExpression(role);
        exp.add(myexp);
    }

    /**
     * Convert the profile filters in a role into an Expression.
     */
    private Expression getProfileExpression(Bundle role) {

        Expression exp = null;

        List<Profile> profiles = role.getProfiles();
        if (profiles != null) {
            if (profiles.size() == 1) {
                // don't add an extra and/or wrapper
                exp = getProfileExpression(profiles.get(0));
            }
            else {
                exp = new Expression();
                if (role.isOrProfiles())
                    exp.setOp(Expression.OR);
                else
                    exp.setOp(Expression.AND);

                for (Profile p : profiles) {
                    Expression pexp = getProfileExpression(p);
                    exp.add(pexp);
                }
            }
        }

        return exp;
    }

    /**
     * Convert a profile filter and permission list into an Expression.
     */
    public Expression getProfileExpression(Profile p) {
        
        Expression exp = new Expression(Expression.AND);

        Application app = p.getApplication();
        String appname = (app != null) ? app.getName() : null;

        // normally only one of these, if multiple they're AND'd
        List<Filter> filters = p.getConstraints();
        if (filters != null) {
            for (Filter f : filters) {
                Expression fexp = getFilterExpression(f, appname, null);
                exp.add(fexp);
            }
        }

        // add any permissions
        addPermissionExpressions(p, appname, exp);

        // reduce clutter and prevent an extra "unit" by collapsing
        // expressions for empty profiles
        // hmm, this might not be good, what if we want to find
        // matches for all empty roles to find all empty roles?
        if (exp.getChildren() == null)
            exp = null;

        return exp;
    }
    
    /**
     * Add a profile permission list to an expression.
     * Factored out so we can share it with getProvisioningExpression.
     */
    private void addPermissionExpressions(Profile profile, 
                                          String appname, 
                                          Expression exp) {

        List<Permission> perms = profile.getPermissions();
        if (perms != null) {
            for (Permission p : perms) {
                // these are simple, just do them inline
                Expression e = new Expression(Expression.EQ);

                // TODO: in theory the target name could
                // be the same as an attribute name which
                // could lead to false matches, could qualify
                // the name with "perm:" or something...
                e.setName(appname, p.getTarget());

                // get the list so it can be sorted
                e.setValue(p.getRightsList());
                // TODO: factor in the annotation!

                exp.add(e);
            }
        }
    }

    /**
     * Convert a Filter to an Expression.
     * 
     * For LeafFilter assuming we don't have to deal with these:
     * 
     *   ignoreCase - always implied for profile matching
     *   joinProperty - not used in profile filters
     *   collectionCondition = not used in profile filters
     *   cast - not used in profile filters
     * 
     * MatchMode is only relevant for LIKE but it will also show
     * up in CONTAINS terms and will be ignored by the filter evaluator.
     * 
     * The string representation of the LogicalOperation is just
     * copied over into the Expression, I don't want to have dependencies
     * on the Filter model and don't want to define a bunch of enums.
     * We only care about their names, not what they do (yet anyway).
     *
     * It shouldn't happen but try to tolerate invalid filters,
     * missing names, missing operators, etc.  Just make something
     * up so we can normalize and compare.
     *
     * This is used for two kinds of filters: normal profile filters
     * and "compound" filters used in IdentitySelectors.  For profile
     * filters appname will be non-null and applications will be null.
     * When we make leaf Expressions, the appname needs to be prepended
     * to the property name.
     *
     * For CompondFiltrs, appname will be null and applications may
     * be non-null (but not necessarily).  If applications is non-null
     * for each property name in the filter we check to see if it
     * has a numeric prefix, and if so substitute the name of the
     * application with that index.
     *
     */
    private Expression getFilterExpression(Filter f, 
                                           String appname, 
                                           List<Application> applications) {

        Expression exp = null;
        
        if (f instanceof CompositeFilter) {
            CompositeFilter comp = (CompositeFilter)f;
            List<Filter> children = comp.getChildren();
            if (children != null) {
                exp = new Expression();
                BooleanOperation op = comp.getOperation();
                if (op == null)
                    exp.setOp(BooleanOperation.AND.toString());
                else
                    exp.setOp(op.toString());

                for (Filter child : children) {
                    Expression cexp = getFilterExpression(child, appname, applications);
                    exp.add(cexp);
                }
            }
        }
        else {
            LeafFilter leaf = (LeafFilter)f;
            String prefix = appname;
            String prop = leaf.getProperty();

            if (prop == null) {
                // invalid filter, put something there so we
                // don't have to worry about it later
                prop = "*undefined*";
            }

            if (applications != null && applications.size() > 0) {
                // possible prefix substitution for CompoundFilter
                int length = prop.length();
                int colon = prop.indexOf(":");
                if (colon == (length - 1)) {
                    // either an empty string or 
                    // prefix without attribute "foo:", ignore
                }
                else if (colon == 0) {
                    // fringe case ":foo", ignore
                }
                else if (colon > 0) {
                    String altprefix = prop.substring(0, colon);
                    String altprop = prop.substring(colon + 1);
                    boolean isNumber = true;
                    for (int i = 0 ; i < altprefix.length() ; i++) {
                        char ch = altprefix.charAt(i);
                        if (!Character.isDigit(ch)) {
                            isNumber = false;   
                            break;
                        }
                    }
                    if (isNumber) {
                        int index = Util.atoi(altprefix);
                        if (index >= 0 && index < applications.size()) {
                            Application app = applications.get(index);
                            if (app != null) {
                                // change the prefix to the indexed app name
                                // and strip the original prefix
                                prefix = app.getName();
                                prop = altprop;
                            }
                        }
                    }
                }
            }
            
            exp = new Expression();

            LogicalOperation op = leaf.getOperation();
            if (op == null) {
                // shouldn't see these but assume equality
                exp.setOp(LogicalOperation.EQ.toString());
            }
            else if (op != LogicalOperation.LIKE) {
                exp.setOp(op.toString());
            }
            else {
                // have to factor in MatchMode
                // simulate the names used in FilterToStringVisitor
                // so the serialized Expression is easire to read
                // for debugging
                MatchMode mm = leaf.getMatchMode();
                if (mm == null)
                    exp.setOp(op.toString());
                else if (mm == MatchMode.ANYWHERE)
                    exp.setOp("contains");
                else if (mm == MatchMode.START)
                    exp.setOp("startsWith");
                else if (mm == MatchMode.END)
                    exp.setOp("endsWith");
                else if (mm == MatchMode.EXACT)
                    exp.setOp("==");
            }

            // have to be carful with this, we may modify it
            Object value = copyIfNecessary(leaf.getValue());

            exp.setName(prefix, prop);
            exp.setValue(value);
        }

        return exp;
    }

    /**
     * Copy the value in a profile filter so we can modify
     * it during Expression normalization.  Primarily this is
     * for Lists which may be sorted.  In theory we could have
     * other things in here, like SailPointObjects but we're
     * trying to prevent that and we would only use the name anyway.
     */
    private Object copyIfNecessary(Object src) {

        Object copy = src;
        if (src != null) {
            Class srcclass = src.getClass();
            if (Collection.class.isAssignableFrom(srcclass)) {
                try {
                    Collection c = (Collection)srcclass.newInstance();
                    c.addAll(((Collection)src));
                    copy = c;
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    copy = null;
                }
            }
        }
        return copy;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IdentitySelector Expression
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flatten and normalize a selector expression for a role.
     */
    private Expression getNormalizedSelectorExpression(Bundle role, boolean flatten) {

        Expression e;
        if (flatten)
            e = getFlatSelectorExpression(role);
        else
            e = getSelectorExpression(role);

        normalize(e);

        return e;
    }

    /**
     * Convert the flattened assignment selector hierarchy for a role
     * into an Expression.
     */
    private Expression getFlatSelectorExpression(Bundle role) {

        Expression exp = null;

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance == null || inheritance.size() == 0) {
            // no inheritance, just return the local expression
            exp = getSelectorExpression(role);
        }
        else {
            exp = new Expression(Expression.AND);
            getFlatSelectorExpression(exp, role);
        }

        return exp;
    }

    /**
     * Walk a role hierarchy accumulating selector expressions.
     */
    private void getFlatSelectorExpression(Expression exp, Bundle role) {

        // first walk upward
        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null) {
            for (Bundle parent : inheritance)
                getFlatSelectorExpression(exp, parent);
        }

        // then add ours
        Expression myexp = getSelectorExpression(role);
        exp.add(myexp);
    }

    /**
     * Convert the assignment IdentitySelector in a role to an Expression.
     */
    private Expression getSelectorExpression(Bundle role) {

        Expression exp = null;

        // unlike Profiles we only have one of these
        IdentitySelector sel = role.getSelector();
        if (sel != null)
            exp = getSelectorExpression(sel);

        return exp;
    }

    /**
     * Normally we'll have only one form of selector, but Matchmaker
     * does all of them so be consistent.  Order doesn't really matter.
     */
    private Expression getSelectorExpression(IdentitySelector sel) {

        Expression exp = new Expression(Expression.AND);

        // MatchExpression

        MatchExpression matchexp = sel.getMatchExpression();
        if (matchexp != null) {
            Expression e = getMatchExpressionExpression(matchexp);
            exp.add(e);
        }
        
        // CompoundFilter
        // These are much like filters but they have their
        // own name qualification scheme

        CompoundFilter cf = sel.getFilter();
        if (cf != null) {
            Filter f = cf.getFilter();
            if (f != null) {
                Expression e = getFilterExpression(f, null, cf.getApplications());
                exp.add(e);
            }
        }

        // GroupDefinition
        // Hmm, population filters can in theory use
        // the more complex Filter features like joining and
        // collection conditions.  Just treat it like a profile
        // filter, it should be close enough.

        GroupDefinition pop = sel.getPopulation();
        if (pop != null) {
            Filter f = pop.getFilter();
            if (f != null) {
                Expression e = getFilterExpression(f, null, null);
                exp.add(e);
            }
        }

        // Script
        // I guess we compare the script source.  A more concise
        // representation of this would be to remove all newlines
        // and spaces then calculate the MD5 hash.
        // To make this look like a boolean expression the
        // property name will be "identity" to represent the 
        // entire identity, the operator will be "script" and the
        // value will be the source.

        Script script = sel.getScript();
        if (script != null) {
            exp = new Expression(Expression.SCRIPT);
            exp.setName(Expression.IDENTITY);
            exp.setValue(script.getSource());
        }

        // Rule
        Rule rule = sel.getRule();
        if (rule != null) {
            exp = new Expression(Expression.RULE);
            exp.setName(Expression.IDENTITY);
            exp.setName(rule.getName());
        }

        // remove the extra wrapper in the usual case where we have
        // only one selection method
        List<Expression> subs = exp.getChildren();
        if (subs == null)
            exp = null;
        else if (subs.size() == 1)
            exp = subs.get(0);

        return exp;
    }

    /**
     * Convert a MatchExpression from an IdentitySelector into
     * an Expression.
     */
    public static Expression getMatchExpressionExpression(MatchExpression match) {

        Expression exp = null;

        List<MatchTerm> terms = match.getTerms();
        if (terms != null) {

            // default property prefix
            String prefix = null;
            Application app = match.getApplication();
            if (app != null)
                prefix = app.getName();

            int length = terms.size();
            if (length == 1) {
                // don't need an extra level
                exp = getMatchTermExpression(terms.get(0), prefix);
            }
            else if (length > 1) {
                exp = new Expression();
                if (match.isAnd())
                    exp.setOp(Expression.AND);
                else
                    exp.setOp(Expression.OR);

                for (MatchTerm term : terms) {
                    Expression e = getMatchTermExpression(term, prefix);
                    exp.add(e);
                }
            }
        }

        return exp;
    }

    /**
     * Convert a MatchTerm from a MatchExpression into an Expression.
     */
    private static Expression getMatchTermExpression(MatchTerm term, String prefix) {

        Expression exp = new Expression(Expression.EQ);
        
        if (term.isContainer()) {
            if (term.isAnd()) {
                exp.setOp(Expression.AND);
            } else {
                exp.setOp(Expression.OR);
            }
            
            for (MatchTerm child : term.getChildren()) {
                exp.add(getMatchTermExpression(child, prefix));
            }
        } else {
            // may override the default prefix
            Application app = term.getApplication();
            if (app != null)
                prefix = app.getName();
    
            String name = term.getName();
    
            // like Profiles we're not making any attempt to qualify
            // the names of Permission targets, might want to?
            // if (term.isPermission()) name = "P:" + name
    
            // Note that the model doesn't support multivalued terms
            // you don't need to csvToList this value, at least not if
            // it came from the role modeler.  Hmm, I wish this 
            // had an Object like LeafFilter.
            String value = term.getValue();
    
            exp.setName(prefix, name);
            exp.setValue(value);
        }
        
        return exp;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Expression
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flatten and normalize a selector expression for a role.
     */
    private Expression getNormalizedProvisioningExpression(Bundle role, boolean flatten) {
        Expression e;
        if (flatten)
            e = getFlatProvisioningExpression(role);
        else
            e = getProvisioningExpression(role);

        normalize(e);

        return e;
    }

    /**
     * These are similar to profile expressions except that rather than 
     * calculating the expression for role detection we're calculating
     * an expression to what would be provisioned if this role
     * were assigned.  This involves the local and inherited profiles
     * plus the flattened profiles from each role on the "required" list.
     * 
     * This is normally only useful for assignable business roles.
     * If you do this for detectable IT roles the result will be
     * similar to the profile expression except that that we only
     * take the first term in an OR filter.
     */
    private Expression getFlatProvisioningExpression(Bundle role) {

        Expression exp = null;

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance == null || inheritance.size() == 0) {
            // no inheritance, just return the local expression
            exp = getProvisioningExpression(role);
        }
        else {
            exp = new Expression(Expression.AND);
            getFlatProvisioningExpression(exp, role);
        }

        return exp;
    }

    /**
     * Walk a role hierarchy accumulating provisioning expressions.
     */
    private void getFlatProvisioningExpression(Expression exp, Bundle role) {

        // first walk upward
        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null) {
            for (Bundle parent : inheritance)
                getFlatProvisioningExpression(exp, parent);
        }

        // then add ours
        Expression myexp = getProvisioningExpression(role);
        exp.add(myexp);
    }

    /**
     * Convert a role into a provisioning expression.
     * First we convert the local profiles or provisioning plan,
     * Then add the expressions for the required roles.
     * Note that when we add required roles we have to flatten them.
     *
     * If we don't have a ProvisioningPlan we derive one from
     * the profile filter.  The logic is similar to that 
     * in Provisioner, need to keep it in sync!  For any
     * OR grouping of profiles or filter terms we only 
     * provision the first one.  Within a filter term
     * we only provision those with EQ or CONTAINS operators.
     */
    private Expression getProvisioningExpression(Bundle role) {

        Expression exp = new Expression(Expression.AND);

        // use the plan if we have one
        ProvisioningPlan plan = role.getProvisioningPlan();
        if (plan != null) {
            Expression e = getProvisioningExpression(plan);
            exp.add(e);
        }
        else {
            // otherwise derive one from the profiles
            List<Profile> profiles = role.getProfiles();
            if (profiles != null && profiles.size() > 0) {
                if (role.isOrProfiles()) {
                    // only pay attention to the first one
                    Profile p = profiles.get(0);
                    Expression e = getProvisioningExpression(profiles.get(0));
                    exp.add(e);
                }
                else {
                    // all of them
                    for (Profile p : profiles) {
                        Expression e = getProvisioningExpression(p);
                        exp.add(e);
                    }
                }
            }
        }

        List<Bundle> requirements = role.getRequirements();
        if (requirements != null) {
            for (Bundle req : requirements) {
                // note that we flatten these!
                Expression e = getFlatProvisioningExpression(req);
                exp.add(e);
            }
        }

        return exp;
    }

    /**
     * Convert a ProvisioningPlan into an Expression.
     * Since these come from roles we only expect AccountRequests
     * in them, not ObjectRequests.
     */
    private Expression getProvisioningExpression(ProvisioningPlan plan) {

        Expression exp = null;

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null && accounts.size() > 0) {
            exp = new Expression(Expression.AND);
            for (AccountRequest account : accounts) {
                Expression e = getProvisioningExpression(account);
                exp.add(e);
            }
        }
        return exp;
    }

    /**
     * Convert a ProvisioningPlan.AccountRequest into an Expression.
     * 
     * Ignoring operations on accounts, they're always going
     * to be Modify. 
     * Ignoring application instances, though we may need them?
     * 
     * Like Profile permission lists, we're not doing any
     * name qualification on the target names so they could
     * end up looking like attribute names.  Unlikely though.
     *
     * For expression operators, we'll take the Operator name
     * which is normally Set or Add for plans in roles.
     */
    private Expression getProvisioningExpression(AccountRequest account) {

        Expression exp = null;

        List<AttributeRequest> atts = account.getAttributeRequests();
        List<PermissionRequest> perms = account.getPermissionRequests();
        
        if ((atts != null && atts.size() > 0) ||
            (perms != null && perms.size() > 0)) {

            exp = new Expression(Expression.AND);

            if (atts != null) {
                for (AttributeRequest att : atts) {
                    Expression e = new Expression();
                    Operation op = att.getOperation();
                    if (op == null) op = Operation.Set;

                    e.setOp(op.toString());
                    e.setName(att.getName());
                    // these may be Lists so have to copy them  
                    e.setValue(copyIfNecessary(att.getValue()));
                    exp.add(e);
                }
            }

            if (perms != null) {
                for (PermissionRequest perm : perms) {
                    Expression e = new Expression();
                    e.setOp(Operation.Add.toString());
                    e.setName(perm.getTarget());
                    e.setValue(copyIfNecessary(perm.getRightsList()));
                }
            }
        }

        return exp;
    }

    /**
     * Convert the filters and permission list into a provisioning expression.
     * This is similar to getProfileExpression except that we're more selective
     * about what we include from the filters.
     */
    private Expression getProvisioningExpression(Profile prof) {

        Expression exp = new Expression(Expression.AND);

        Application app = prof.getApplication();
        String appname = (app != null) ? app.getName() : null;

        List<Filter> filters = prof.getConstraints();
        if (filters != null) {
            // we're assuming these are AND'd ??
            for (Filter f : filters) {
                Expression e = getProvisioningExpression(f, appname);
                exp.add(e);
            }
        }

        // perms are effectively AND'd so do all of them
        addPermissionExpressions(prof, appname, exp);

        return exp;
    }

    /**
     * Cull out the parts of a profile filter that are  interesting 
     * for provisioning.
     */
    private Expression getProvisioningExpression(Filter f, String appname) {

        Expression exp = null;

        if (f instanceof CompositeFilter) {
            CompositeFilter comp = (CompositeFilter)f;
            List<Filter> children = comp.getChildren();
            if (children != null && children.size() > 0) {
                BooleanOperation op = comp.getOperation();
                if (op == BooleanOperation.OR) {
                    // only care about the first one
                    exp = getProvisioningExpression(children.get(0), appname);
                }
                else {
                    // need all of them
                    exp = new Expression(Expression.AND);
                    for (Filter child : children) {
                        Expression e = getProvisioningExpression(child, appname);
                        exp.add(e);
                    }
                }
            }
        }
        else {
            LeafFilter leaf = (LeafFilter)f;

            // we only convey EQ and CONTAINS_ALL terms
            Filter.LogicalOperation op = leaf.getOperation();
            if (op == Filter.LogicalOperation.EQ ||
                op == Filter.LogicalOperation.CONTAINS_ALL) {

                String prop = leaf.getProperty();
                if (prop == null) {
                    // invalid filter, put something there so we
                    // don't have to worry about it later
                    prop = "*undefined*";
                }

                // have to be carful with this, we may modify it
                Object value = copyIfNecessary(leaf.getValue());

                exp = new Expression();
                exp.setName(appname, prop);
                exp.setOp(op.toString());
                exp.setValue(value);
            }
        }

        return exp;
    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // Expression Normalization
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    /**
     * This could get unbelievably hairy but we'll keep it simple for now.
     * With an AND/OR block we'll sort the terms by string representation
     * which includes operator, property, and value and sort values that
     * are lists.  This will catch the common case of transposed terms like:
     *
     *     a == 1 && b == 2
     *     b == 2 && a == 1
     *
     * The next level would be to try and detect and eliminate redundancy
     * which might creep in with inheritance, especially in 
     * provisioning plans.
     *
     *     a == 1 && b == 2 && a == 1
     *
     * Then try and collapse operations on multi-valued attributes:
     *
     *     a contains [1] and a contains [2]
     *     a contains [1,2]
     * 
     * Then perhaps simple DeMorgan transformations:
     *
     *     a == 1 && b == 2
     *     a != 1 || b != 2
     *
     * Beyond that you're into equivalence algorithms way more complex
     * than I have time for or even understand.  Maybe someday.
     *
     * UPDATE: Sorting doesn't actually work.  If you have these:
     *      x == 1 && y == 2
     *      a == 1 && b == 2 || x == 1 && y == 2
     * 
     * The sort doesn't mean we'll encounter a match for each term
     * in the same position on the list.  For each element in the
     * first list we have to look for a match at any position 
     * in the second list.    Sorting does help for multi-valued
     * attributes though.
     */
    public void normalize(Expression e) {

        // Expression model itself does the basic work of sorting
        if (e != null)
            e.normalize();

        // TODO: magic!

    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // Overlap Analysis
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    /**
     * Okay, here's where the rubber meets the sky.
     * 
     * This is just insanely complex for arbitrary expressions, we're
     * making lots of assumptions about the expressions we're likely to 
     * see for roles.  Typically we'll have a list of ANDd equality
     * comparisons so optmize for that.  For anything complex we'll
     * go into more of a blind pattern match just trying not to assume
     * that everything in the tree is different.
     *
     * Once we're comparing lists of AND/OR terms we do a "best fit"
     * calculation to determine which terms are structurally closest 
     * and pair them.  This will probably be erroneous a great deal
     * of the time, just because there are some subexprssions in common
     * would the logic ever result in actual assignment overlap?
     * But hey, our job is not to question, we need numbers to show in 
     * a table.
     *
     * The e1 expression is assumed to come from the target role 
     * expression caches, in the process we will build out the 
     * "candidates" list  in each node in e1.  For the root node 
     * there will be one candidate and it represents the overlap 
     * of that tree.
     * 
     * Basically we try to calculate the number of comparable "units"
     * and the number of units that are considered equal and from
     * there can calculate a percentage.  The trick is in deciding what
     * a "unit" is and what "equality" means.
     *
     * The simple cases are of course easy:
     *
     *      a == 1 && b == 2
     *      a == 1 && b == 3 
     * 
     * There are two ANDd operations here, one matches and the other doesn't.
     * This would be 2 units with 1 match for a 50% overlap.
     *
     * ORs are less clear:
     *
     *     a == 1
     *     a == 1 || b == 2
     *
     * Every user that has the first role will also have the second role,
     * so you could say there is a 100% overlap between the two.  In other
     * words in an OR grouping, if any units match you consider there
     * to be only one significant unit with one match giving 100% overlap.
     *
     * Alternately you could treat this like an AND with a 50% match.
     * But will this be confusing when the user noticies that the same
     * two roles are always detected at the same time?
     *
     * We can also consider the direction.  You could say role B has a 
     * 100% overlap with role A since it is more permissive, but you can't
     * say that role A is a 100% overlap with role B.  If the purpose
     * of this were to clear out redundant roles you would have
     * to be careful to pick the least permissive role to delete.
     *
     * Next we have logical equivalence:
     *
     *     a == 1 && b == 2
     *     a != 1 || b != 2
     *
     * It is possible to apply DeMorgan's law to detect some equivalences
     * but the actual utility of this is questionable.  Roles tend to 
     * have positive ANDd assertions and going through all possible
     * inversions of individual terms and hierarcies of terms could be
     * expensive.  This also doesn't fit in with normalization since
     * == and != will sort differently.
     *
     * In general logical equivalence is extremely difficult.  We're
     * not going to attempt it beyond maybe a few hard coded patterns
     * that customers might bring to us.
     *
     * Finally there are things you just can't know without comparing
     * to actual identity data:
     *
     *     a like 'x*'
     *     a like '*z'
     *
     * Exactly the same identities may match these two roles but
     * we can't know that without applying them to the identity data.
     *
     * When the "ignoreRootOperator" flag is true, we do not add a "unit" 
     * for the root expression's operator.  This is for profile 
     * expressions which adds an AND around filter and permission 
     * lists to combine them. This ANDing is implicit and should
     * not factor in as a unit that can be matched.  Including
     * it dilutes the result a bit.
     */ 
    public OverlapRegion getOverlap(Expression e1, Expression e2,
                                    boolean ignoreRootOperator) {

        OverlapRegion overlap = null;

        // handle special cases where we have nothing to compare
        if (e1 == null) {
            if (e2 != null) {
                overlap = new OverlapRegion();
                countUnits(overlap, e2);
            } 
        }
        else if (e2 == null) {
            if (e1 != null) {
                overlap = new OverlapRegion();
                countUnits(overlap, e1);
            }
        }
        else {
            // From here down we maintain OverlapRegions
            // in the "candidates list" on each Expression
            // to save some garbage.
            // Reset all prior candidiate state.
            e1.resetCandidates();

            getOverlapInner(e1, e2, ignoreRootOperator);

            // for the root, there should one candidate with the
            // final overlap statistics
            List<OverlapRegion> cands = e1.getCandidates();
            if (cands.size() != 1)
                log.error("Unexpected number of expression candidiates at root");
            overlap = cands.get(0);
        }

        // this must always return something to keep the 
        // caller logic clean
        if (overlap == null)
            overlap = new OverlapRegion();

        return overlap;
    }

    /**
     * Inner recursive overlap calculator. Both expressions must be non-null.  
     * Be very careful with this, ultra sophisticated brain melting
     * algorithms ahead!
     */
    private void getOverlapInner(Expression e1, Expression e2,
                                 boolean ignoreRootOperator) {

        OverlapRegion overlap = e1.addCandidate(e2);

        List<Expression> girls = e1.getChildren();
        List<Expression> boys = e2.getChildren();

        if (girls == null || girls.size() == 0) {
            if (boys == null || boys.size() == 0) {
                // two little leaves
                overlap.units++;
                if (e1.isMatch(e2))
                    overlap.matches++;

            }
            else {
                // leaf+tree, look for a match somewhere inside
                // and substract that from the total unit count
                // for this subtree
                countUnits(overlap, e2);
                if (findUnit(e1, e2))
                    overlap.units--;
            }
        }
        else if (boys == null || boys.size() == 0) {
            // tree+leaf
            countUnits(overlap, e1);
            if (findUnit(e2, e1))
                overlap.units--;
        }
        else {
            // do a recursive "best fit" analysis of the child terms
            // you can think of the result as a 2D matrix of percentages
            // with girls one axis and boys on another with the percentage
            // at the intersection
            for (Expression girl : girls) {
                girl.resetCandidates();
                for (Expression boy : boys) {
                    getOverlapInner(girl, boy, false);
                }
            }

            // mate them
            // have to duplicate the list so we can determine leftovers
            girls = new ArrayList<Expression>(girls);
            boys = new ArrayList<Expression>(boys);

            // while there are couples left
            while (girls.size() > 0 && boys.size() > 0) {
                int gindex = 0;
                int matches = 0;
                while (gindex < girls.size()) {
                    Expression girl = girls.get(gindex);

                    // who does she like best?
                    OverlapRegion desire = null;
                    List<OverlapRegion> likes = girl.getCandidates();
                    for (OverlapRegion like : likes) {
                        // is he still available?
                        if (boys.contains(like.expression)) {
                            if (desire == null ||
                                like.getPercentage() > desire.getPercentage())
                                desire = like;
                        }
                    }

                    // anyone like him more?
                    if (desire != null) {
                        for (int i = 0 ; i < girls.size() ; i++) {
                            if (i != gindex) {
                                Expression bitch = girls.get(i);
                                OverlapRegion like = bitch.getCandidate(desire.expression);
                                if (like.getPercentage() > desire.getPercentage()) {
                                // sorry buffy, you lose
                                    desire = null;
                                    break;
                                }
                            }
                        }
                    }

                    if (desire != null) {
                        // sorry girls, he's taken
                        // we could also remove this guy from everyone's
                        // caididate list just so we don't keep looking at him
                        // when search for other candidates but I'm assuming we're
                        // not dealing with long lists
                        girl.setMate(desire);
                        matches++;

                        // factor in the match percentage for this pair
                        overlap.units += desire.units;
                        overlap.matches += desire.matches;

                        // note that we do not increment gindex here, removing
                        // the girl from the list will slide the next one into
                        // position
                        boys.remove(desire.expression);
                        girls.remove(girl);
                    }
                    else {
                        // else, back of the line buffy...
                        gindex++;
                    }
                }

                // I'm not a good enough mathematician to prove that
                // there can't be deadlock in this algorithm but I don't
                // think so.  To be safe, break if we went through the girls
                // and didn't find anyone.
                if (matches == 0)
                    break;
            }

            // Factor in the losers.  There should only be one loser list
            // we either had too many girls or boys.  Go home and watch
            // Heroes or Ugly Betty or whatever it is you geeks do.
            
            for (Expression girl : girls)
                countUnits(overlap, girl);
            
            for (Expression boy: boys)
                countUnits(overlap, boy);


            // factor in equivalence of the two tree operators
            // Hmm, having a matching and/or wrapper be considered a unit
            // dilutes most results, in a simple case like:
            // 
            //   a == 1 && b == 2
            //   a == 1 && b == 3
            //
            // we end up with a 66% overlap rather than 50% because
            // treating && as a unit makes it 2 out of 3 rather than 
            // 1 out of 2.  I think we should only make this a unit
            // if they differ.  Same technique may apply to other things?

            if (!ignoreRootOperator) {
                if (!e1.getOp().equals(e2.getOp()))
                    overlap.units++;
            }
        }
    }

    /**
     * Count the number of "significant units" in an expression.
     * This is vague as usual, I'm starting with A simple node
     * count.  If we did only leaf nodes, then these would end
     * up with a 100% match (both leaf terms match).  Instead
     * the and/or should factor in making this a 66% match.
     *
     *   a == 1 && b == 2
     *   a == 1 || b == 2
     *
     */
    private void countUnits(OverlapRegion reg, Expression e) {

        // one for me
        reg.units++;

        // and one for each interior node
        List<Expression> children = e.getChildren();
        if (children != null)
            for (Expression c : children)
                countUnits(reg, c);
    }

    /**
     * Search for a leaf term somewhere in a hierarchy.
     */
    private boolean findUnit(Expression leaf, Expression tree) {
        boolean found = false;
        if (tree != null) {
            List<Expression> children = tree.getChildren();
            if (children == null)
                found = leaf.isMatch(tree);
            else {
                for (Expression child : children) {
                    found = findUnit(leaf, child);
                    if (found)
                        break;
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // OverlapRegion
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    /**
     * Transient helper structure to accumulate overlap statistics.
     * A "region" is a set of semantically related things in an object
     * that can be compared to calculate an overlap percentage.  A "unit"
     * is something within the region that can be compared.  Analysis 
     * consists of counting the number of comparable units within the
     * region and the number of units that compared equal.  From those
     * you can calculate a percentage.
     *
     * Example: Profile Filter Region
     *    Units are terms within the filter such as "x == 1",
     *    (a && b) etc.  
     *
     * Example: Assignment Rule Region
     *    Units are MatchTerms in the MatchExpression.
     *
     * Example: Attributes Region
     *    Units are key/value pairs in a map plus selected
     *    properties from the object model that aren't in a map.
     *
     * Regions are also used for the "candidates" 
     * list of each Expression when pairing terms during overlap
     * analysis.  We need to do a private overlap calculation
     * for each pair of term pairs without this affecting the
     * overall stats for the region. 
     * Hmm, the term region is confusing, rethink...
     */
    static public class OverlapRegion {

        public int units;
        public int matches;

        // associated expression when the region used for term pairing
        Expression expression;

        public int getPercentage() {
            return getPercentage(units, matches);
        }
            
        static public int getPercentage(int extunits, int extmatches) {
            return (int)(((float)extmatches / (float)extunits) * 100.0f);
        }

        /**
         * Used when merging regions into one percentage.
         */
        public void assimilate(OverlapRegion other) {
            if (other != null) {
                units += other.units;
                matches += other.matches;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // Expression
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    /**
     * A generic model we build to represent Profiles, Filters, 
     * IdentitySelectors, ProvisioningPlans, and whatever else
     * there is in a role that needs to be anlayzed.  This let's
     * us normalize and analize all parts of the role in the same way.
     * Theoretically we could factor this out and use for overlap
     * analysis in other things, notably policies.
     */
    public static class Expression {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * A few built-in opcodes so we don't have to depend on 
         * other models.  It is important that we use the 
         * same name for AND and OR 
         */
        public static final String AND = BooleanOperation.AND.toString();
        public static final String OR = BooleanOperation.OR.toString();
        public static final String EQ = LogicalOperation.EQ.toString();
        public static final String RULE = "RULE";
        public static final String SCRIPT = "SCRIPT";

        /**
         * Special property name we use with RULE and SCRIPT
         * operators to indiciate that the value applies to the
         * entire identity.
         */
        public static final String IDENTITY = "IDENTITY";

        /**
         * The name of the "property" we're doing something to.
         * This is usually an attribute name, it may be qualified
         * with the name of a target application.
         */
        String _name;

        /**
         * What we want to do with the property.
         * Expression op codes are relatively ambiguous.
         * Other than AND and OR we don't really care what these are yet
         * since we don't try to determine true logical equivalence.
         * We just compare the names.
         */
        String _op;

        /**
         * What we're trying to compare or put into the property.
         * This will normally be an atomic boxed value (usually String)
         * or a List<String>.  You might see Date but rarely Integer. 
         */
        Object _value;

        /**
         * For AND/OR opertors the child expressions.
         */
        List<Expression> _children;
       
        /**
         * Sorting is done on the string representation of the expression.
         * Since we need this several times it is cached.
         * This is only done during normalization and debugging.
         */
        String _string;

        /**
         * A list of peer expressions with their calculated overlap percentage.
         * This is transient and used during overlap analysis.  To avoid generating
         * excessive garbage we'll maintain the list of objects as a cache and
         * try to reuse them. For this to have any effect these have to be 
         * maintained on the target role expressions, not the roles we're 
         * iterating over.
         */
        List<OverlapRegion> _candidates;

        /**
         * Number of valid candidates in the list.
         */
        int _candidateCount;

        /**
         * Chosen candidate.
         */
        OverlapRegion _mate;

        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        public Expression() {
        }

        public Expression(String op) {
            _op = op;
        }

        public String getName() {
            return _name;
        }
        
        public void setName(String s) {
            _name = s;
        }

        public void setName(String prefix, String name) {
            if (prefix == null)
                _name = name;
            else
                _name = prefix + ":" + name;
        }

        public String getOp() {
            return _op;
        }
        
        public void setOp(String s) {
            _op = s;
        }

        public Object getValue() {
            return _value;
        }
        
        public void setValue(Object o) {
            _value = o;
        }
        
        public List<Expression> getChildren() {
            return _children;
        }

        public void setChildren(List<Expression> l) {
            _children = l;
        }

        public void add(Expression e) {
            if (e != null) {
                if (_children == null)
                    _children = new ArrayList<Expression>();
                _children.add(e);
            }
        }

        public List<OverlapRegion> getCandidates() {
            return _candidates;
        }
        
        //////////////////////////////////////////////////////////////////////
        //
        // Candidate Management
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Reset the candidiate counter, but leave the list of objects
         * so we can reuse them in another pass.
         */
        public void resetCandidates() {
            _candidateCount = 0;
            // don't actually need this, getOverlapInner will reset
            // on the way down
            if (_children != null) {
                for (Expression e : _children)
                    e.resetCandidates();
            }
        }

        /**
         * Add a candidate expression to the list.
         */
        public OverlapRegion addCandidate(Expression exp) {
            OverlapRegion cand = null;

            if (_candidates == null)
                _candidates = new ArrayList<OverlapRegion>();

            if (_candidateCount < _candidates.size())
                cand = _candidates.get(_candidateCount);
            else {
                cand = new OverlapRegion();
                _candidates.add(cand);
            }

            cand.expression = exp;
            cand.units = 0;
            cand.matches = 0;

            _candidateCount++;

            return cand;
        }

        /**
         * Search for a candidiate expression.
         * This should only be called after fully populating
         * the candidates matrix.
         */
        public OverlapRegion getCandidate(Expression e) {
            OverlapRegion found = null;
            if (_candidates != null) {
                for (OverlapRegion cand : _candidates) {
                    if (cand.expression == e) {
                        found = cand;
                        break;
                    }
                }

            }
            return found;
        }

        /**
         * Don't actually use this with the current getOverlapInner algorithm
         */
        public void removeCandidate(Expression e) {
            if (e != null && _candidates != null) {
                for (OverlapRegion cand : _candidates) {
                    if (cand.expression == e) {
                        _candidates.remove(cand);
                        _candidateCount--;
                        break;
                    }
                }
            }
        }

        public void setMate(OverlapRegion mate) {
            _mate = mate;
        }

        public OverlapRegion getMate() {
            return _mate;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // String Representation
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * String representation for debugging.
         * Yes, it's lispish, get over it...
         */
        public String toString() {
            if (_string == null) {
                StringBuilder b = new StringBuilder();
                toString(b);
                _string = b.toString();
            }
            return _string;
        }

        private void toString(StringBuilder b) {
            b.append("(");
            b.append(_op);
            b.append(" ");
            if (_children == null) {
                if (_name != null) {
                    b.append(_name);
                    b.append(" ");
                    b.append(_value);
                }
            }
            else {
                for (Expression child : _children)
                    child.toString(b);
            }
            b.append(")");
        }
        
        public boolean isMatch(Expression other) {
            boolean match = false;
            String me = toString();
            String them = other.toString();
            if (me == null)
                match = (them == null);
            else if (them != null)
                match = me.equals(them);
            return match;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Normalization
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Utility to sort values, normally used prior to 
         * string rendering.
         * What about Maps??  Won't be in filters but might
         * be nice for comparing Attributes.
         */
        public void sortValues() {
            if (_value instanceof List) {
                // hmm, in theory we could need type-specific 
                // collators but in practice we'll almost always
                // be dealing with List<String>
                Collections.sort((List)_value);
            }

            if (_children != null) {
                for (Expression child : _children)
                    child.sortValues();
            }
        }
        
        /**
         * Comparator for Expression terms.
         * Might not need this if the default Comparator uses toString?
         */
        private static final Comparator<Expression> ExpressionComparator = 
        new Comparator<Expression>() {
            public int compare(Expression e1, Expression e2) {
                String s1 = e1.toString();
                String s2 = e2.toString();
                return s1.compareTo(s2);
            }
        };

        /**
         * Sort the terms in a compound expression.
         * This relies on the string representations so we have to 
         * do this bottom up to get the strings for parents rendered 
         * after sorting children.
         */
        public void sort() {
            if (_children != null) {
                // recursively sort the contents of children
                for (Expression child : _children)
                    child.sort();

                // then the children relative to each other
                Collections.sort(_children, ExpressionComparator);
            }
        }

        /**
         * Normalize an expression tree by first sorting the values
         * then the terms.
         */
        public void normalize() {
            sortValues();
            sort();
        }

    }


}
