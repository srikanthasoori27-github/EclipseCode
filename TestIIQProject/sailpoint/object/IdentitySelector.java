/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating various ways to select an identity
 * for inclusion in something fun!
 *
 * Author: Jeff
 *
 * This was developed to support "entitlement-level SOD" 
 * constraints, but it is more general than that.  In theory
 * this can be your one-stop shop for representing identity
 * matching criteria from simple attribute/value lists to
 * complex filters, to Beanshell rules.  
 * 
 * Since this is basically what a Profile does, we should
 * consider refactoring the profile model to be  a subclass 
 * of this, which would then make role correlation more
 * powerful as well.  
 *
 * This is expected to be embedded in something else and
 * serialized as XML.  This means we have to be careful
 * with ojbect references, particularly for Application.
 *
 * Though a Filter is an option for expressing the matching
 * rules, most selectors are expected to use a MatchExpression.
 * This is simply a list of MatchTerms that define equality
 * comparisons on attributes or permissions.  These are much
 * easier to display and edit in the UI.
 * 
 * More than one selection method may be used though normally
 * there is only one.  When non-null and not logically "empty"
 * all selection methods must evaluate to true.
 *
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * A class encapsulating various ways to select a population of identities.
 * This is the basis for identity selection in entitlement SOD and
 * advanced policies, as well as for role assignment rules.
 */
@SuppressWarnings("serial")
@XMLClass
public class IdentitySelector extends AbstractXmlObject {
    
    /**
     * A set of type constants used by the UI when editing
     * a selector. Not used in the persistent model
     */
    public static final String SELECTOR_TYPE_MATCH_LIST = "selectorMatchList";
    public static final String SELECTOR_TYPE_FILTER = "selectorFilter";
    public static final String SELECTOR_TYPE_SCRIPT = "selectorScript";
    public static final String SELECTOR_TYPE_RULE = "selectorRule";
    public static final String SELECTOR_TYPE_POPULATION = "selectorPopulation";
    
    
    public interface IAttribute {   
        public String getName();
        public String getMessageKey();
        public String getType();
        public boolean isMultiValued();
        /**
         * Returns query property used when value is null.
         * eg. for Classification attribute on Role object,
         * when value is not null : Filter.eq("classifications.name", value);
         * when value is null : Filter.isEmpty("classifications")
         */
        public String getNullQueryProperty();
    }
    
    //Enum used in RoleAttribute type MatchTerm
    public static enum RoleAttributes implements IAttribute, Localizable {
        Classification("classifications.classification.name", MessageKeys.ATTR_CLASSIFICATION, BaseAttributeDefinition.TYPE_STRING, true, "classifications"),
        Name("name", MessageKeys.UI_CERTIFICATION_SCHEDULE_NAME, BaseAttributeDefinition.TYPE_STRING),
        Type("type", MessageKeys.UI_CERTIFICATION_SCHEDULE_TYPE, BaseAttributeDefinition.TYPE_STRING),
        Disabled("disabled", MessageKeys.UI_CERTIFICATION_SCHEDULE_DISABLED, BaseAttributeDefinition.TYPE_BOOLEAN),
        Owner("owner.name", MessageKeys.UI_CERTIFICATION_SCHEDULE_OWNER, BaseAttributeDefinition.TYPE_STRING, false, "owner");

        private final String name;
        private final String key;
        private final String type;
        private final boolean multiValued;
        private final String nullQueryProperty;
        
        RoleAttributes(String name, String key, String type) {
            this(name, key, type, false, null);
        }

        RoleAttributes(String name, String key, String type, boolean multiValued, String nullQueryProperty) {
            this.name = name;
            this.key = key;
            this.type = type;
            this.multiValued = multiValued;
            this.nullQueryProperty = nullQueryProperty == null ? name : nullQueryProperty;
        }
        public String getName() {
            return name;
        }

        public String getMessageKey() {
            return key;
        }
        public String getType() {
            return type;
        }
        public boolean isMultiValued() {
            return multiValued;
        }
        public String getNullQueryProperty() {
            return nullQueryProperty;
        }

        @Override
        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        @Override
        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }

    };

    //Enum used in EntitlementAttribute type MatchTerm
    public static enum EntitlementAttributes implements IAttribute, Localizable {
        Classification("classifications.classification.name", MessageKeys.ATTR_CLASSIFICATION, BaseAttributeDefinition.TYPE_STRING, true, "classifications"),
        Type("type", MessageKeys.UI_CERTIFICATION_SCHEDULE_TYPE, BaseAttributeDefinition.TYPE_STRING),
        Requestable("requestable", MessageKeys.UI_CERTIFICATION_SCHEDULE_REQUESTBALE, BaseAttributeDefinition.TYPE_BOOLEAN),
        Aggregated("aggregated", MessageKeys.UI_CERTIFICATION_SCHEDULE_AGGREGATED, BaseAttributeDefinition.TYPE_BOOLEAN),
        Attribute("attribute", MessageKeys.UI_CERTIFICATION_SCHEDULE_ATTRIBUTE, BaseAttributeDefinition.TYPE_STRING),
        Value("value", MessageKeys.UI_CERTIFICATION_SCHEDULE_VALUE, BaseAttributeDefinition.TYPE_STRING),
        Owner("owner.name", MessageKeys.UI_CERTIFICATION_SCHEDULE_OWNER, BaseAttributeDefinition.TYPE_STRING, false, "owner");
        
        private final String name;
        private final String key;
        private final String type;
        private final boolean multiValued;
        private final String nullQueryProperty;
        
        EntitlementAttributes(String name, String key, String type) {
            this(name, key, type, false, null);
        }

        EntitlementAttributes(String name, String key, String type, boolean multiValued, String nullQueryProperty) {
            this.name = name;
            this.key = key;
            this.type = type;
            this.multiValued = multiValued;
            this.nullQueryProperty = nullQueryProperty == null ? name : nullQueryProperty;
        }
        
        public String getName() {
            return name;
        }

        public String getMessageKey() {
            return key;
        }

        public String getType() {
            return type;
        }

        public boolean isMultiValued() {
            return multiValued;
        }

        public String getNullQueryProperty() {
            return nullQueryProperty;
        }

        @Override
        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        @Override
        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }
        
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A brief summary of what the selector is doing.
     * This is intended for display in a table (such as the
     * table of Entitlement SOD constraints) so it needs to be
     * short, yet meaningful.
     */
    String _summary;

    /**
     * Simple list of attribute value or permission matches.
     */
    MatchExpression _matchExpression;

    /**
     * More complex filter with boolean operators. Can reference
     * both identity and link attributes in any combination.
     */
    CompoundFilter _filter;

    /**
     * An inline script.
     */
    Script _script;

    /**
     * An external rule reference.
     */
    Rule _rule;

    /**
     * An external iPOP reference. By definition, the population
     * filter applies only to identity cube attributes.
     */
    GroupDefinition _population;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public IdentitySelector() {
    }

    /**
     * A brief summary of what the selector is doing.
     * This is intended for display in a table (such as the
     * table of Entitlement SOD constraints) so it needs to be
     * short, yet meaningful.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getSummary() {
        return _summary;
    }

    public void setSummary(String s) {
        _summary = s;
    }

    /**
     * Simple list of attribute value or permission matches.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public MatchExpression getMatchExpression() {
        return _matchExpression;
    }

    public void setMatchExpression(MatchExpression exp) {
        _matchExpression = exp;
    }
    
    /**
     * More complex filter with boolean operators. Can reference
     * both identity and link attributes in any combination.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public CompoundFilter getFilter() {
        return _filter;
    }

    public void setFilter(CompoundFilter f) {
        _filter = f;
    }

    /**
     * An inline script that has access to the 
     * entire identity model.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Script getScript() {
        return _script;
    }

    public void setScript(Script s) {
        _script = s;
    }

    /**
     * An external rule reference. The rule has access
     * to the entire identity model.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public Rule getRule() {
        return _rule;
    }

    public void setRule(Rule r) {
        _rule = r;
    }

    /**
     * A reference to an population definition. These can be
     * populations defined manually in the search pages, or
     * groups generated by group factories.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="PopulationRef")
    public GroupDefinition getPopulation() {
        return _population;
    }

    public void setPopulation(GroupDefinition pop) {
        _population = pop;
    }

    /**
     * @exclude
     * Make sure selector is fully loaded.
     */
    public void load() {

        if (_matchExpression != null)
            _matchExpression.load();

        if (_filter != null)
            _filter.load();

        if (_rule != null)
            _rule.load();

        // this has a GroupFactory and a GroupIndex which we don't need here
        if (_population != null)
            _population.getName();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Derive a meaningful summary string by looking
     * at the contents of the selector. 
     * 
     * @ignore
     * TODO: Need to be smarter than this!  Just have something
     * for the initial demo right now.  
     */
    public String generateSummary() {

        String summary = null;

        if (_matchExpression != null)
            summary = _matchExpression.render();

        else if (_filter != null) 
            summary = _filter.render();

        else if (_script != null) 
            summary = _script.getSource();

        else if (_rule != null) {
            // TODO: Need some kind of prefix?
            summary = _rule.getName();
        }   
        else if (_population != null) {
            summary = _population.getName();
        }

        return summary;
    }

    /**
     * Return true if the selector is logically empty.
     * Prevents forcing the display of a selector UI in case
     * an empty one happens to be left on a role whose type
     * says it should not have a selector.
     */
    public boolean isEmpty() {

        // cheat and assume this will also be empty
        String summary = generateSummary();
        return (summary == null || summary.length() == 0);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Interface of an object that can be passed into match evaluation
     * methods to be informed about things encountered during evaluation.
     * This was added as a way to accumulate the Applications involved
     * in a match to set the "relevant apps" list in a PolicyViolation,
     * but it was made an interface so it can be extended if necessary.
     * Currently used only by MatchExpression/MatchTerm, eventually
     * should be used by Matchmaker for other selection styles
     * (filter, script, rule, etc.)
     */
    public interface MatchMonitor {

        /**
         * Called whenever there is a match involving account
         * attributes or permissions for a particular application.
         */
        public void matchMonitorApplication(Application app);

        /**
         * Called whenever there is a match on an attribute.
         * Link may be null if this is an identity attribute.
         */
        public void matchMonitorAttribute(Link link, String name, Object value);

        /**
         * Called whenever there is a match on a permission.
         * Link cannot be null since identities do not have permissions.
         * The Permission object is normally from the Link, the value
         * has only those rights that are interesting which may be less
         * than was is in the Permission.
         */
        public void matchMonitorPermission(Link link, Permission p, Object value);
    };

    //////////////////////////////////////////////////////////////////////
    //
    // MatchExpression/MatchTerm
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generic representation for a single attribute or permission value
     * within a MatchExpression.
     * 
     * This is similar to EntitlementWeight in that the same
     * class is used to represent both attribute values and Permission values.
     * Could consider factoring out something to share but there is not
     * much in here and the usage contexts are different.
     */
    @XMLClass
    public static class MatchTerm extends AbstractXmlObject {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * The Application that defines this entitlement.
         * This is optional, it can often be inferred from the owning object.
         */
        Application _application;

        /**
         * The TargetSource that the entitlmeent originated
         * This will only be set in the case of TargetAssociations aggregated from
         * TargetSource
         */
        TargetSource _targetSource;

        /**
         * True if this is a permission rather than a simple attribute value.
         * @deprecated @see _type
         */
        @Deprecated
        boolean _permission;

        @XMLClass(xmlname="MatchTermType")
        public static enum Type {
            Entitlement,
            Permission,
            TargetPermission,
            IdentityAttribute,
            RoleAttribute,
            EntitlementAttribute
        };

        /**
         * True to look for effective access matching the entitlement/permission as well
         */
        boolean _checkEffective;

        /**
         * Type of object to match on
         */
        Type _type;

        /**
         * The attribute name or permission target.
         */
        String _name;

        /**
         * The attribute value or permission right.
         */
        String _value;

        /**
         * @ignore
         * Should this be derived from _children.size() == 0
         * or, should this be explicitly set?
         * I like explicit for now, may revisit later
         * Knowing the intent beforehand is always better. 
         * Avoids unknown side-effects later.
         */
        boolean _container;
        boolean _and;
        boolean _negative;
        List<MatchTerm> _children = new ArrayList<MatchTerm>();

        // TODO: Do we need the "annotation" from the Permission model?


        /**
         * List of Contributions granting this match.
         *
         * @ignore
         * This is derived from effective match terms that have found a match. This
         * will contain all possible effective paths in which the term matches.
         */
        List<ContributingEntitlement> _contributingEntitlements;

        //////////////////////////////////////////////////////////////////////
        //
        // Constructor/Properties
        //
        //////////////////////////////////////////////////////////////////////

        public MatchTerm() {
        }

        /**
         * The Application that defines this entitlement.
         * This is optional, it can often be inferred from the owning object.
         */
        @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
        public Application getApplication() {
            return _application;
        }

        public void setApplication(Application a) {
            _application = a;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="TargetSourceRef")
        public TargetSource getTargetSource() {
            return _targetSource;
        }

        public void setTargetSource(TargetSource ts) {
            _targetSource = ts;
        }

        /**
         * The attribute name or permission target.
         */
        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String name) {
            _name = name;
        }

        /**
         * The attribute value or permission right.
         */
        @XMLProperty
        public String getValue() {
            return _value;
        }
    
        public void setValue(String value) {
            _value = value;
        }    

        /**
         * True if this is a permission rather than a simple attribute value.
         */
        @XMLProperty
        public boolean isPermission() {
            return _permission;
        }

        public boolean isPermissionType() {
            return _permission || _type == Type.Permission;
        }

        public boolean isEntitlementType() {
            return !_permission && (_type == null || _type == Type.Entitlement);
        }

        public void setPermission(boolean b) {
            _permission = b;
        }

        @XMLProperty
        public boolean isContainer() {
            return _container;
        }
        
        public void setContainer(boolean val) {
            _container = val;
        }
        
        @XMLProperty
        public boolean isNegative() {
            return _negative;
        }
        
        public void setNegative(boolean val) {
            _negative = val;
        }
        
        @XMLProperty
        public boolean isAnd() {
            return _and;
        }
        
        public void setAnd(boolean val) {
            _and = val;
        }
        
        @XMLProperty(mode = SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<MatchTerm> getChildren() {
            return _children;
        }


        @XMLProperty
        public Type getType() { return _type; }

        public void setType(Type t) { this._type = t; }
        
        public void setChildren(List<MatchTerm> val) {
            _children = val;
        }
        
        public void addChild(MatchTerm val) {
            _children.add(val);
        }

        @XMLProperty
        public boolean isCheckEffective() {
            return _checkEffective;
        }

        public boolean shouldCheckEffective() {
            return _checkEffective;
        }

        public void setCheckEffective(boolean b) { _checkEffective = b; }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<ContributingEntitlement> getContributingEntitlements() {
            return _contributingEntitlements;
        }

        public void setContributingEntitlements(List<ContributingEntitlement> ents) {
            _contributingEntitlements = ents;
        }

        public void addContributingEntitlement(ContributingEntitlement ent) {
            if (this._contributingEntitlements == null) {
                this._contributingEntitlements = new ArrayList<>();
            }

            _contributingEntitlements.add(ent);
        }

        /**
         * Fully load the term.
         */
        public void load() {
            
            // sigh, application has loads of crap hanging off it, 
            // hopefully we only need it for name identification,
            // though EntitlementCorrelator has historically fully
            // loaded Profile applications
            if (_application != null)
                _application.load();
            
            for (MatchTerm child : _children) {
                child.load();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Match Evaluation
        // Moved to MatchMaker.TermMatcher
        //////////////////////////////////////////////////////////////////////


        public String render() {
            StringBuilder builder = new StringBuilder();
            
            if (!_container) {
                return MatchTerm.renderLeaf((_type==null?null:_type.name()), _name, _value, _negative);
            }
            
            Iterator<MatchTerm> it = _children.iterator();
            while (it.hasNext()) {
                builder.append("(" + it.next().render() + ")");
                if (it.hasNext()) {
                    //localize "and"/"or"?, maybe not... queries are not language specific I think
                    builder.append(" " + (_and ? "AND" : "OR") + " ");
                }
            }
            
            return builder.toString();
        }
        
        public static String renderLeaf(String type, String name, String value, boolean negative) {
            StringBuilder b = new StringBuilder();
            if (name != null) {
                if (Type.Permission.name().equals(type)) {
                    // kludge: I don't like this...
                    // TODO: quote name if it contains a comma?
                    if (negative) {
                        b.append("not(");
                    }
                    b.append("p(");
                    b.append(name);
                    b.append(", ");
                    b.append(value);
                    b.append(")");
                    if (negative) {
                        b.append(")");
                    }
                } else {
                    if (Type.RoleAttribute.name().equals(type)) {
                        b.append("Role.");                        
                    } else if (Type.EntitlementAttribute.name().equals(type)) {
                        b.append("Entitlement.");
                    }
                    // TODO: quote name if it contains spaces
                    // or equals?
                    b.append(name);
                    if (negative) {
                        b.append(" != ");
                    } else {
                        b.append(" = ");
                    }
                    if (value == null)
                        b.append("null");
                    else {
                        b.append("\"");
                        b.append(value);
                        b.append("\"");
                    }
                }
            }
            return b.toString();
        }

        @XMLClass
        public static class ContributingEntitlement {

            //Application Name
            String source;
            //TargetType
            String type;
            //Hierarchy
            String path;
            //Classifications
            List<String> classificationNames;

            @XMLProperty
            public String getSource() {
                return source;
            }

            public void setSource(String source) {
                this.source = source;
            }

            @XMLProperty
            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            @XMLProperty
            public String getPath() {
                return path;
            }

            public void setPath(String path) {
                this.path = path;
            }

            @XMLProperty
            public List<String> getClassificationNames() {
                return classificationNames;
            }

            public void setClassificationNames(List<String> classificationNames) {
                this.classificationNames = classificationNames;
            }
        }

    }
    
    /**
     * Representation for a set of MatchTerms.  
     */
    @XMLClass
    public static class MatchExpression extends AbstractXmlObject {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * An optional Application to scope the attribute references
         * in the term list.
         */
        Application _application;

        List<MatchTerm> _terms = new ArrayList<MatchTerm>();
        
        /**
         * True if the terms are logically AND'd during evaluation.
         */
        boolean _and;

        //////////////////////////////////////////////////////////////////////
        //
        // Constructor/Properties
        //
        //////////////////////////////////////////////////////////////////////

        public MatchExpression() {
        }

        /**
         * An optional Application to scope the attribute references
         * in the term list.
         */
        @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
        public Application getApplication() {
            return _application;
        }

        public void setApplication(Application a) {
            _application = a;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<MatchTerm> getTerms() {
            return _terms;
        }

        public void setTerms(List<MatchTerm> terms) {
            _terms = terms;
        }

        public void addTerm(MatchTerm t) {
            _terms.add(t);
        }
        
        /**
         * True if the terms are logically AND'd during evaluation.
         */
        @XMLProperty 
        public boolean isAnd() {
            return _and;
        }

        public void setAnd(boolean b) {
            _and = b;
        }

        /**
         * @exclude
         * Fully load the expression.
         * Like MatchTerm it is assumed that the entire
         * Application is not needed, just the name and schema for matching.
         */
        public void load() {

            if (_application != null) {
                _application.load();
            }

            if (_terms != null)
                for (MatchTerm term : _terms)
                    term.load();
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Match Evaluation
        // Moved to MatchMaker.ExpressionMatcher
        //
        //////////////////////////////////////////////////////////////////////


        //////////////////////////////////////////////////////////////////////
        //
        // String Rendering
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Render the expression in a more pleasant format.
         */
        public String render() {
            StringBuilder b = new StringBuilder();
            Iterator<MatchTerm> it = _terms.iterator();
            while (it.hasNext()) {
                b.append("(" + it.next().render() + ")");
                if (it.hasNext()) {
                    b.append(" " + (_and ? "AND" : "OR") + " ");
                }
            }
            return b.toString();
        }

    }
    
}
