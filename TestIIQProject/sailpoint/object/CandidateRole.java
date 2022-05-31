/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The definition of a "role" produced by role mining.
 *
 * Author: Jeff
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of a role produced by role mining.
 *
 * This is a simplification of the Bundle and Profile models to 
 * make it easier to represent role mining results without having to 
 * generate a lot of transient Bundles and Profiles (all with
 * unique names), cluttering up the business modeler. It also gives
 * a place to hang transient statistics like the matching identities.
 *
 * The classes are intended to be stored as XML until they are
 * "promoted" so they do not require a Hibernate mapping. When
 * a candidate role is promoted, it becomes an actual Bundle/Profile
 * set and is visible in the modeler for further editing and approvals.
 *
 * Like Bundles these can be organized into a hierarchy but
 * in current practice these should be limited to two levels, the
 * topmost representing Bundles and the second the Profiles.  
 * Hierarchical roles will be generated only when you are mining for
 * patterns of roles rather than raw entitlements, or in constrained
 * cases where cross-application clusters are discovered.
 * 
 */
@XMLClass
public class CandidateRole extends SailPointObject
    implements Cloneable, Describable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Maximum number of matching identity names saved.  
     * This cannot be too large or else there is the risk ov overflowing the XML blob.
     * Could make this configurable but really if you need to go
     * beyond 100 storing should be done in a different way.
     */
    public static final int MAX_MATCHES = 100;

    /**
     * The list of child roles (Profiles) when mining
     * cross-application roles. 
     *
     * Unlike Bundle, only AND'ing of child roles is supported, though
     * OR could be supported if necessary.
     */
    List<CandidateRole> _children;

    /**
     * For leaf roles, the target application.
     * To fully support cross-application roles this would have
     * to be a list and the attributes and targets mentioned
     * in the rules would need to use qualified names.
     */
    Application _application;

    /**
     * Boolean expressions that match identities to this role.
     * The filters are OR'd if there is more than one.
     * 
     */
    List<Filter> _filters;

    /**
     * Low level Permissions required by the role.
     *
     * This will only be set when 
     * building a candidate role from a certification. These are
     * not used with undirected mining in part because there are
     * no connectors that return them but also because 
     * boolean expressions will nee to be supported on permissions just like
     * for attributes so it is probably better to find a way to reference
     * permissions from within the filter.
     */
    List<Permission> _permissions;

    /**
     * Various mining statistics. These are factored out into
     * a standalone class so one can be attached to the Bundles as well.
     */
    MiningStatistics _miningStatistics;

    /**
     * A list Identity names that match this role. Since this
     * is just a candidate role these are not stored as references
     * from the Identity yet so the names must be remembered here.
     * Since there are scaling issues, the number of identities
     * remember is limited.
     *
     * In the original demo a table that included firstname
     * and lastname was shown. Could turn this into a complex object.
     */
    List<String> _matches;

    /**
     * Name of role type definition
     */
    String _roleType;

    /**
     * Extended attributes.
     */
    Attributes<String, Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CandidateRole() {
    }

    /**
     * @exclude
     * This tells Hibernate that even though it is a SailPointObject
     * subclass, it is always serialized within the parent object.
     */
    public boolean isXml() {
        return true;
    }

    public void add(Filter c) {
        if (c != null) {
            if (_filters == null)
                _filters = new ArrayList<Filter>();
            _filters.add(c);
        }
    }

    public void add(Permission p) {
        if (p != null) {
            if (_permissions == null)
                _permissions = new ArrayList<Permission>();
            _permissions.add(p);
        }
    }

    public void add(CandidateRole r) {
        if (r != null) {
            if (_children == null)
                _children = new ArrayList<CandidateRole>();
            _children.add(r);
        }
    }

    public void addMatch(String name) {
        if (name != null) {
            if (_matches == null)
                _matches = new ArrayList<String>();
            if (_matches.size() < MAX_MATCHES)
                _matches.add(name);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The list of child roles when mining cross-application roles. 
     * Unlike Bundle, only AND'ing of child roles is supported.
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="CandidateRoles")
    public List<CandidateRole> getChildren() {
        return _children;
    }

    public void setChildren(List<CandidateRole> roles) {
        _children = roles;
    }

    /**
     * For leaf roles, the target application.
     * To fully support cross-application roles this would have
     * to be a list and the attributes and targets mentioned
     * in the rules would need to use qualified names.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application r) {
        _application = r;
    }

    /**
     * Boolean expressions that match identities to this role.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getFilters() {
        return _filters;
    }

    public void setFilters(List<Filter> filters) {
        _filters = filters;
    }

    /**
     * Low level Permissions required by the role.
     *
     * This will only be set when 
     * building a candidate role from a certification.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Permission> getPermissions() {
        return _permissions;
    }

    public void setPermissions(List<Permission> perms) {
        _permissions = perms;
    }

    /**
     * Various mining statistics. These are factored out into
     * a standalone class so one can be attached to the Bundles as well.
     */
    public MiningStatistics getMiningStatistics() {
        return _miningStatistics;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setMiningStatistics(MiningStatistics stats) {
        _miningStatistics = stats;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setMatches(List<String> matches) {

        if (matches == null || matches.size() < MAX_MATCHES)
            _matches = matches;
        else {
            // prune this so the XML doesn't get too large
            _matches = new ArrayList<String>(MAX_MATCHES);
            for (int i = 0 ; i < MAX_MATCHES ; i++)
                _matches.add(matches.get(i));
        }
    }

    /**
     * A list of Identity names that match this role. Since this
     * is just a candidate role these are not stored as references
     * from the Identity yet so the names must be remembered here.
     * Since there are scaling issues, the number of identities
     * remembered is limited.
     */
    public List<String> getMatches() {
        return _matches;
    }

    @XMLProperty
    public String getRoleType() {
        return _roleType;
    }

    public void setRoleType(String roleType) {
        _roleType = roleType;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Pseudo property for JSF.
     */
    public int getMatchCount() {
        return (_matches != null) ? _matches.size() : 0;
    }

    /**
     * Used by the JSF role mining result pages to display the "rule"
     * used for matching identities to this role. Since you cannot edit
     * this yet, a full blown boolean expression is not needed.
     */
    public String getRuleExpression() {
        
        String exp = null;

        if (_filters != null && _filters.size() > 0) {
            Filter root = _filters.get(0);
            for (int i = 1 ; i < _filters.size() ; i++)
                root = Filter.or(root, _filters.get(i));


            // formerly used this, but it isn't as pretty
            //exp = root.toString();

            FilterRenderer fr = new FilterRenderer(root);
            exp = fr.render();
        }

        return exp;
    }

    /**
     * Return true if it looks like the role has the default
     * name generated by the miner. This is used during promotion
     * to see if an attempt should be made to try to qualify the name with a unique number
     * or leave the user-specified name alone. Unfortunately the only
     * way to tell this is by generating the default name again and comparing.
     */
    public boolean isDefaultName() {
        
        String dflt = getRuleSummary();
        return dflt.equals(_name);
    }

    /**
     * A variant of the filter expression that is shorter but
     * not necessarily semantically meaningful. This is nice for
     * the generated name to get a flavor for what the rule is.
     */
    public String getRuleSummary() {

        StringBuffer b = new StringBuffer();
        if (_filters == null || _filters.size() == 0)
            b.append("No rule");
        else {
            for (int i = 0 ; i < _filters.size() ; i++) {
                if (i > 0) b.append(" || ");
                Filter f = _filters.get(i);
                getRuleSummary(b, f);
            }
        }

        return b.toString();
    }

    /**
     * Inner filter walker to convert the tree to something
     * resembling infix notation.
     */
    private void getRuleSummary(StringBuffer b, Filter f) {
        
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter)f;
            Filter.LogicalOperation op = lf.getOperation();
            String prop = lf.getProperty();
            Object value = lf.getValue();

            if (op == Filter.LogicalOperation.CONTAINS_ALL) {
                // the filter language for this is 
                // <property>.containsAll({"<value>"}) with a single
                // value which is kind of busy.  
                // Reduce to "<property> <value>" e.g. "groups A"
                // "not groups B"
                if (value != null) {
                    b.append(prop);
                    b.append(" ");
                    if (!(value instanceof List))
                        b.append(value.toString());
                    else {
                        // these are almost always single valued so simplify
                        List l = (List)value;
                        if (l.size() == 1)
                            b.append(l.get(0));
                        else
                            b.append(value.toString());
                    }
                }
            }
            else {
                // should only see EQ but give the others a shot
                b.append(prop);
                b.append(" ");  
                b.append(op.getStringRepresentation());
                b.append(" ");
                if (value == null)
                    b.append("null");
                else
                    b.append(value.toString());
            }
        }
        else {
            // let's leave out parens for now to reduce clutter
            // in practice these will be long lists of ANDs with the
            // occasional NOT
            Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
            Filter.BooleanOperation op = cf.getOperation();
            if (op == Filter.BooleanOperation.NOT) {
                // this must be emitted before the child expressions
                // in practice this filter must only have one child
                //b.append(op.getStringRepresentation());
                b.append("not ");
            }

            List<Filter> children = cf.getChildren();
            for (int i = 0 ; i < children.size() ; i++) {
                if (i > 0) {
                    b.append(" ");
                    if (op == Filter.BooleanOperation.AND)
                        b.append("and");
                    else 
                        b.append(op.getStringRepresentation());
                    b.append(" ");
                }
                getRuleSummary(b, children.get(i));
            }
        }
    }

    /**
     * Return a list of CandidateRoles representing the Profiles that
     * would be created if this role were approved. This is a convenience
     * for the JSF pages so they can deal with hierarchical and self contained
     * candidates in the same way.
     */
    public List<CandidateRole> getCandidateProfiles() {

        List<CandidateRole> profiles = new ArrayList<CandidateRole>();

        if (_children == null) {
            // we're it
            profiles.add(this);
        }
        else {
            // we're logically a profile if we have an app and
            // some entitlements
            if (_filters != null || _permissions != null)
                profiles.add(this);

            for (CandidateRole child : _children) 
                profiles.add(child);
        }

        return profiles;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Descriptions
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * @return Map of descriptions keyed by locale
     */
    public Map<String, String> getDescriptions() {
        Map<String,String> map = null;
        Object o = getAttribute(ATT_DESCRIPTIONS);
        if (o instanceof Map) {
            map = (Map<String,String>)o;
        }
        return map;
    }

    /**
     * Set the descriptions
     */
    public void setDescriptions(Map<String, String> map) {
        setAttribute(ATT_DESCRIPTIONS, map);
    }

    /**
     * Incrementally add one description.
     */
    public void addDescription(String locale, String desc) {
        new DescribableObject<CandidateRole>(this).addDescription(locale, desc);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(String locale) {
        return new DescribableObject<CandidateRole>(this).getDescription(locale);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(Locale locale) {
        return new DescribableObject<CandidateRole>(this).getDescription(locale);
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_attributes != null) {
                _attributes.remove(name);
            }
        }
        else {
            if (_attributes == null) {
                _attributes = new Attributes<String,Object>();
            }
            _attributes.put(name, value);
        }
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }
}
