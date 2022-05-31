/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to define a set of abstract attributes for
 * SailPointObject subclasses.  Some of these may map directly
 * to Java properties, some may be derived at runtime, and some
 * may be custom attributes added during a deployment.
 *
 * Author: Jeff
 * 
 * The model serves two primary purposes:
 *
 *   1) Defines the set of attributes that may be used
 *      in configurable search pages and result tables.
 *      The identity table is one example.
 *
 *   2) Defines "extended attributes" that be added by the customer
 *      and determines whether these are allocated a column in 
 *      the Hibernate table so they can be used in searches.
 *
 * In addition the attribute definitions for Identity objects
 * may be given a set of Sources that determine how we derive
 * the values for the attributes during aggregation and refresh.
 *
 * The source concept is specific to Identity attributes, but I
 * didn't want to factor out a subclass because we only want
 * a single class (ObjectConfig) to contain the attribute 
 * configurations for all SailPointObject subclasses.
 *
 */
package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class used to define a set of abstract attributes for
 * SailPointObject subclasses. Some of these might map directly
 * to Java properties, some might be derived at runtime, and some
 * might be custom attributes added during a deployment.
 */
@XMLClass
public class ObjectAttribute extends BaseAttributeDefinition
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * True if this is a "system" attribute that does not have a source
     * and is not configurable.
     */
    boolean _system;

    /**
     * True if this is a "standard" attribute.  
     * Examples: manager, firstname, lastname, email.
     */
    boolean _standard;

    /**
     * Flag indicating that a change listener cannot be specified
     * for this attribute. This is intended for use with "system"
     * attributes that will not be monitored. For Identity this
     * includes name, bundleSummary, lastRefresh, lastLogin.
     * 
     * @ignore
     * In theory this could also be set for standard and extended
     * attributes but I can't think of a good reason and there is
     * no UI to set that.
     */
    boolean _silent;

    /**
     * The number of one of the extended attribute columns for this class.
     * If this is non-zero the value will be copied to one of the columns
     * (extended1, extended2 etc.) when it is saved. You must assign
     * an extended number to any attribute that you want to use in 
     * search filters, and does not already have a Hibernate mapping.
     *
     * The number must not change once assigned. If you need
     * to reassign numbers you will need to do a full refresh of the
     * associated class.
     */
    int _extendedNumber;
    
    /**
     * True if this attribute has a named column in the schema.
     * Named columns are defined in the Extended.hbm.xml file 
     * with a special access method. This is an alternative to 
     * using the fixed numbered columns. When an attribute has
     * a named column, it is considered searchable but will not
     * be assigned an _extendedNumber.
     */
    boolean _namedColumn;

    /**
     * @exclude
     * True if the value of this attribute is is stored in an external 
     * table to facilitate searching.  This was an experiment that we
     * decided not to pursue but may want to revisit someday.  In theory
     * this allows us to have any number of searchable custom attributes,
     * but it means that we have to mutate the Filter into a join
     * between the two tables which caused some unresolved problems 
     * for HibernatePersistenceManager.  Filter conversion is working,
     * but using these in a project list is broken.
     */
    boolean _external;

    /**
     * Rule to derive the value.
     * Usually if a rule is specified there will be no sources.
     * If both are defined, the sources are used first, and the rule
     * is used as a default.
     * 
     * The rule is passed an "object" argument that has the SailPointObject
     * being refreshed. For backward compatibility with older 
     * IdentityConfig objects, an "identity" argument is also passed
     * if this is an ObjectConfig for the Identity class.
     */
    Rule _rule;

    /**
     * Potential sources of values for this attribute.
     * This is relevant only for Identity attributes.
     */
    List<AttributeSource> _sources;

    /**
     * The targets of this attribute that should receive the
     * value upon attribute synchronization. This currently
     * is only relevant for identity attributes.
     */
    List<AttributeTarget> _targets;
    
    /**
     * True if this attribute can be used in a group factory.
     * This is relevant only for Identity attributes.
     * 
     * If this is an extended attribute, it must also be given
     * an _extendedNumber or a _namedColumn so it can be used in searches.
     */
    boolean _groupFactory;

    /**
     * Enumeration used to indicate whether modifications
     * to this attribute are allowed.
     */
    @XMLClass(xmlname="EditMode")
    public static enum EditMode {

        /**
         * The attribute is readonly and is not editable from the  UI.
         * @ignore
         * jsl - This is the default behavior, if _editMode is null
         * you should assume ReadOnly.
         */
        ReadOnly,

        /**
         * Any manually edited values will override the feed value.
         */
        Permanent,

        /**
         * The manually edited value will take precedence over the
         * the feed until the feed value changes. The Last feed value
         * is store in an AttributeMetaData object.
         */
        UntilFeedValueChanges, 
    }

    /**
     * Edit Type which applies if an attribute is Editable.
     */
    EditMode _editMode;

    /**
     * Rule called whenever a change is detected on this attribute
     * during aggregation. This is intended for attributes with Sources
     * that are processed automatically.  
     * 
     * Arguments to the rule:
     *
     *     object      - object that was modified (all types)
     *     identity    - Identity modified
     *     link        - Link modified
     *
     *     attribute   - name of attribute modified
     *     oldValue    - previous value
     *     newValue    - new value
     *
     * The "object" argument will always be set, other type specific
     * arguments will also be set depending on the type. This is for
     * consistency with other rules, which get specific arguments.
     * 
     * @ignore
     * In theory this means Identity
     * and Link attribute but in practice it is only for Identity attributes.
     * 
     * TODO: This could make sense for Link attributes.
     * 
     * This is NOT called when the attribute is modified interactively
     * in the UI. That should be considered but it means a similar 
     * before/after state have to be kept.
     *
     * TODO: Consider having this fire for interactive changes.
     */
    Rule _listenerRule;

    /**
     * Workflow launched whenever a change is detected.
     * All the commentary for _listenerRule also applies here.
     * If both listener rule and workflow are defined the rule is
     * called first.
     *
     * @ignore
     * I'm not sure if we need this but I wanted to added the plumbing
     * for services.  The rule is enough since you can launch a workflow
     * from the rule, but if we usually want to use workflows this
     * is more convenient. 
     */
    Workflow _listenerWorkflow;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectAttribute() {
    }

    public void add(AttributeSource src) {
        if (_sources == null)
            _sources = new ArrayList<AttributeSource>();
        _sources.add(src);
    }

    public boolean remove(AttributeSource src) {
        boolean removed = false;
        if (_sources != null)
            removed = _sources.remove(src);
        return removed;
    }

    public void add(AttributeTarget target) {
        if (_targets == null)
            _targets = new ArrayList<AttributeTarget>();
        _targets.add(target);
    }

    public boolean remove(AttributeTarget target) {
        boolean removed = false;
        if (_targets != null)
            removed = _targets.remove(target);
        return removed;
    }

    /**
     * @exclude
     * Fully fetch the object tree.  
     * Note that even though this class is stored in an XML blob,
     * it may contain Rule references.  We still have to walk the
     * XML objects to make sure the things they reference are
     * fully loaded.
     */
    public void load() {

        if (_rule != null)
            _rule.load();

        if (_listenerRule != null)
            _listenerRule.load();

        if (_listenerWorkflow != null)
            _listenerWorkflow.load();

        if (_sources != null) {
            for (AttributeSource src : _sources) 
                src.load();
        }

        if (_targets != null) {
            for (AttributeTarget target : _targets) 
                target.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setSystem(boolean b) {
        _system = b;
    }

    /**
     * True if this is a "system" attribute that does not have a source
     * and is not configurable. These typically map to properties
     * in the Java class. The class is also expected to have
     * getAttribute and setAttribute methods that will take the 
     * name specified in this definition and return the property value.
     * This must also be the name used in the Hibernate mappings
     * so it can be used to build Filters.
     *
     * System attributes must not be removed from the ObjectConfig.
     *
     * Examples: lastLogin, lastRefresh, bundles, exceptions.
     */
    public boolean isSystem() {
        return _system;
    }

    @XMLProperty
    public void setStandard(boolean b) {
        _standard = b;
    }

    /**
     * True if this is a "standard" attribute. These are intended primarily
     * for identity attributes that have sources, but they are expected
     * to be defined. The only real distinction between these and extended
     * attributes are that the UI should not allow these to be removed
     * from the ObjectConfig.
     *
     * Examples: manager, firstname, lastname, email.
     */
    public boolean isStandard() {
        return _standard;
    }

    @XMLProperty
    public void setSilent(boolean b) {
        _silent = b;
    }

    /**
     * Flag indicating that a change listener cannot be specified
     * for this attribute. This is intended for use with "system"
     * attributes that will not be monitored. For Identity this
     * includes name, bundleSummary, lastRefresh, lastLogin.
     */
    public boolean isSilent() {
        return _silent;
    }

    @XMLProperty
    public void setExtendedNumber(int i) {
        _extendedNumber = i;
    }

    /**
     * The number of one of the extended attribute columns for this class.
     * If this is non-zero the value will be copied to one of the columns
     * (extended1, extended2 etc.) when it is saved. You must assign
     * an extended number to any attribute that you want to use in 
     * search filters, and does not already have a Hibernate mapping.
     *
     * The number must not change once assigned. If you need
     * to reassign numbers you will need to do a full refresh of the
     * associated class.
     */
    public int getExtendedNumber() {
        return _extendedNumber;
    }

    @XMLProperty
    public void setNamedColumn(boolean b) {
        _namedColumn = b;
    }

    public boolean isNamedColumn() {
        return _namedColumn;
    }

    /**
     * @exclude
     */
    @XMLProperty
    public void setExternal(boolean b) {
        _external = b;
    }

    /**
     * @exclude
     * True if the value of this attribute is is stored in an external 
     * table to facilitate searching.  This was an experiment that we
     * decided not to pursue but may want to revisit someday.  In theory
     * this allows us to have any number of searchable custom attributes,
     * but it means that we have to mutate the Filter into a join
     * between the two tables which caused some unresolved problems 
     * for HibernatePersistenceManager.  Filter conversion is working,
     * but using these in a project list is broken.
     */
    public boolean isExternal() {
        return _external;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public void setRule(Rule r) {
        _rule = r;
    }

    /**
     * Rule to derive the value.
     * Usually if a rule is specified there will be no sources.
     * If both are defined, the sources are used first, and the rule
     * is used as a default.
     * 
     * The rule is passed an "object" argument that has the SailPointObject
     * being refreshed. For backward compatibility with older 
     * IdentityConfig objects, an "identity" argument is also passed
     * if this is an ObjectConfig for the Identity class.
     */
    public Rule getRule() {
        return _rule;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setSources(List<AttributeSource> sources) {
        _sources = sources;
    }

    /**
     * Potential sources of values for this attribute.
     * This is relevant only for Identity attributes.
     */
    public List<AttributeSource> getSources() {
        return _sources;
    }

    // KG - I tried using INLINE_LIST_UNQUALIFIED like getSources(), but this
    // caused problems for the XML serialization framework.  I'm pretty sure
    // that it got confused because AttributeTarget extends AttributeSource
    // and didn't cope with it.  Just changing the XML serialization mode to
    // get around this.
    @XMLProperty(xmlname="AttributeTargets",mode=SerializationMode.LIST)
    public void setTargets(List<AttributeTarget> targets) {
        _targets = targets;
    }

    /**
     * The targets of this attribute that should receive the
     * value upon attribute synchronization. This currently
     * is only relevant for identity attributes.
     */
    public List<AttributeTarget> getTargets() {
        return _targets;
    }

    @XMLProperty
    public void setGroupFactory(boolean b) {
        _groupFactory = b;
    }

    /**
     * True if this attribute can be used in a group factory.
     * This is relevant only for Identity attributes.
     * 
     * If this is an extended attribute, it must also be given
     * an _extendedNumber or _namedColumn so it can be used in searches.
     */
    public boolean isGroupFactory() {
        return _groupFactory;
    }

    /**
     * Indicates whether this attribute is editable.
     */
    @XMLProperty
    public EditMode getEditMode() {
        return _editMode;
    }

    public void setEditMode(EditMode type) {
        _editMode = type;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ListenerRule")
    public void setListenerRule(Rule r) {
        _listenerRule = r;
    }

    /**
     * Rule called whenever a change is detected on this attribute
     * during aggregation. This is intended for attributes with Sources
     * that are processed automatically. In theory this means Identity
     * and Link attribute but in practice it is only for Identity attributes.
     * 
     * This is NOT called when the attribute is modified interactively
     * in the UI.
     * 
     * Arguments to the rule:
     * <pre>
     *     object      - object that was modified (all types)
     *     identity    - Identity modified
     *     link        - Link modified
     *
     *     attribute   - name of attribute modified
     *     oldValue    - previous value
     *     newValue    - new value
     * </pre>
     * 
     * The "object" argument will always be set, other type specific
     * arguments will also be set depending on the type. This is for
     * consistency with other rules, which get specific arguments.
     *
     * @ignore
     * TODO: This could make sense for Link attributes.
     * 
     */
    public Rule getListenerRule() {
        return _listenerRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ListenerWorkflow")
    public void setListenerWorkflow(Workflow w) {
        _listenerWorkflow = w;
    }

    /**
     * Workflow launched whenever a change is detected.
     * All the commentary for <code>listenerRule</code> also applies here.    
     * If both listener rule and workflow are defined the rule is
     * called first.
     */
    public Workflow getListenerWorkflow() {
        return _listenerWorkflow;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isExtended() {
        return (_extendedNumber > 0);
    }

    /**
     * @deprecated now using ExtendedAttributeUtil.getPropertyMapping to determine the 
     * DB mapping
     */
    @Deprecated
    public String getExtendedName() {
        String name = null;
        if ( _extendedNumber > 0 ) {
            name = "extended" + Util.itoa(getExtendedNumber());
        }
        return name;
    }

    /**
     * @deprecated now using ExtendedAttributeUtil.getPropertyMapping to determine the 
     * DB mapping
     */
    @Deprecated
    public String getExtendedIdentityName() {
        String name = null;
        if ( _extendedNumber > 0 ) {
            name = "extendedIdentity" + Util.itoa(getExtendedNumber());
        }
        return name;
    }

    /**
     * True if this is an identity reference.
     * Note that this does not necessarily mean that the reference will
     * be stored in an extendedIdentity column, that is class specific
     * and has to be checked at a higher level. This just makes
     * a common type check easier.
     */
    public boolean isIdentity() {
        return ObjectAttribute.TYPE_IDENTITY.equals(_type);
    }

    public boolean isSearchable() {
        return isStandard() || isExtended() || isNamedColumn() || isExternal();
    }

    public boolean isCustom() {
        return isExtended() || isNamedColumn() || isExternal() || isMulti();
    }

    public AttributeSource getSource(AttributeSource other) {

        AttributeSource match = null;
        if (_sources != null) {
            for (AttributeSource src : _sources) {
                if (src.isMatch(other)) {
                    match = src;
                    break;
                }
            }
        }
        return match;
    }

    public AttributeTarget getTarget(AttributeTarget other) {

        AttributeTarget match = null;
        if (_targets != null) {
            for (AttributeTarget target : _targets) {
                if (target.isMatch(other)) {
                    match = target;
                    break;
                }
            }
        }
        return match;
    }

    /**
     * If the attribute has an edit mode and its not ReadOnly
     * the attribute is considered editable.
     */
    public boolean isEditable() {
        if ( _editMode != null ) {
            if ( !EditMode.ReadOnly.equals(_editMode) && !_system) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * This is a convenience function that works around the fact that JSF
     * will not gracefully support Enums
     * @return The EditMode in String form
     */
    public String getEditModeString() {
        if (_editMode == null) {
            return EditMode.ReadOnly.name();
        } else {
            return _editMode.name();
        }
    }

    /**
     * This is a convenience function that works around the fact that JSF
     * will not gracefully support Enums. It sets the editMode according to
     * the stringified form that is passed in
     */
    public void setEditModeString(final String editModeString) {
        _editMode = EditMode.valueOf(editModeString); 
    }
    
    private static class ObjectAttributeByDisplayNameComparator
        implements Comparator<ObjectAttribute> {

        private Locale locale;
        
        private ObjectAttributeByDisplayNameComparator(Locale locale) {
            this.locale = locale;
        }
        
        public int compare(ObjectAttribute p1, ObjectAttribute p2) {
            Collator collator = Collator.getInstance(this.locale);
            collator.setStrength(Collator.PRIMARY);
            return collator.compare(p1.getDisplayableName(this.locale),
                                    p2.getDisplayableName(this.locale));
        }
    }
        
    /**
     * Return the name of the application attribute that is a source
     * of this attribute. Null indicates the application does not
     * source this attribute.
     */
    public String getSourceAttribute(Application app) {
        AttributeSource source = getSource(app);
        if (source != null) {
            String mappedTo = source.getName();
            Rule rule = source.getRule();
            // jsl - this is kind of obscure, we're mostly
            // using just the non-nullness but if there is
            // code out there doing things with this prefix
            // we should have a different method!!
            if ( rule != null ) {
                return "Rule:"+rule.getName();
            } else if ( mappedTo != null ) {
                return mappedTo;
            }
        }
        return null;
    }

    public AttributeSource getSource(Application app) {
        if ( ( app != null)  && ( Util.size(_sources) > 0 ) )  {
            for ( AttributeSource source : _sources ) {
                Application sourceApp = source.getApplication();
                if ( ( sourceApp != null ) && ( app.equals(sourceApp) ) ) {
                    return source;
                }
            }
        }
        return null;
    }

    /**
     * @deprecated use {@link #getSourceAttribute(Application)}
     * @ignore 
     * jsl - I found a reference to this in IdentityBean and
     * changed it to the more obvious name.
     */
    @Deprecated
    public String sourcedBy(Application app) {

        return getSourceAttribute(app);
    }

    /**
     * Return true if the attribute has a source for this application.
     */
    public boolean isSourcedBy(Application app) {
        return (getSourceAttribute(app) != null);
    }

    /**
     * Return the ordinal position of an AttributeSource given a key.
     * Return -1 if the source was not found.
     */
    public int getSourceOrdinal(String key) {
        int ordinal = -1;
        if (key != null && _sources != null) {
            for (int i = 0 ; i < _sources.size() ; i++) {
                AttributeSource src = _sources.get(i);
                if (key.equals(src.getKey())) {
                    ordinal = i;
                    break;
                }
            }
        }
        return ordinal;
    }

    /**
     * Return the ordinal position of an AttributeSource given
     * AttributeMetadata previously for this attribute.
     */
    public int getSourceOrdinal(AttributeMetaData metadata) {

        int ordinal = -1;
        if (metadata != null) {
            // must have a key, if not it's a manual override 
            // and has no ordinal
            String key = metadata.getSource();
            if (key != null)
                ordinal = getSourceOrdinal(key);
        }
        return ordinal;
    }
    
    /**
     * Return the attribute source for the given metadata, or null
     * if there is no source.
     */
    public AttributeSource getSource(AttributeMetaData metadata) {
        int ordinal = getSourceOrdinal(metadata);
        return (ordinal > -1) ? _sources.get(ordinal) : null;
    }
    
    /**
     * Convenience method that maps this attribute's type to a 
     * SearchInputDefinition PropertyType
     */
    public SearchInputDefinition.PropertyType getPropertyType() {
        if (_type == null) {
            return PropertyType.String;
        }
        
        return typeToPropertyTypeMap.get(_type);
    }
    
    private static Map<String, SearchInputDefinition.PropertyType> typeToPropertyTypeMap;
    
    static {
        typeToPropertyTypeMap = new HashMap<String, SearchInputDefinition.PropertyType>();
        typeToPropertyTypeMap.put(TYPE_BOOLEAN, PropertyType.Boolean);
        typeToPropertyTypeMap.put(TYPE_DATE, PropertyType.Date);
        typeToPropertyTypeMap.put(TYPE_IDENTITY, PropertyType.Identity);
        typeToPropertyTypeMap.put(TYPE_INT, PropertyType.Integer);
        typeToPropertyTypeMap.put(TYPE_SECRET, PropertyType.String);
        typeToPropertyTypeMap.put(TYPE_STRING, PropertyType.String);
        typeToPropertyTypeMap.put(TYPE_RULE, PropertyType.Rule);
    }
}
