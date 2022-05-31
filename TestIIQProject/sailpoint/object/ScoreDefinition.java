/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing a scoring algorithm.
 *
 * Author: Jeff
 *
 * These were designed as SailPointObjects but we don't
 * have a Hibernate mapping for them, they're always
 * serialized inside a ScoreConfig.
 *
 * There are three "name" fields here so be careful:
 *
 *    _name
 *      The primary name canonical name.  Used as keys
 *      in score indexes.  Not intended for display.
 *
 *    _displayName
 *      Name displayed in the UI score config pages.
 *      These tend to be long.
 * 
 *    _shortName
 *      A shortened displayable name.  These are used
 *      when showing tables of ScoreItems.
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of one scoring algorithm.
 * These were designed a SailPointObject subclasses, but they
 * are only stored as XML within the ScoreConfig.
 */
@XMLClass
public class ScoreDefinition extends SailPointObject
    implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -744825849414968451L;

    /**
     * The default range if unspecified.    
     */
    public static final int DEFAULT_RANGE = 1000;

    /**
     * The name of a standard score definition argument that
     * when true indicates that the Scorer is to calculate
     * a compensated score rather than a raw score.
     *
     * @ignore
     * It would make sense for this to be a first-class property
     * but we've historically kept in the attributes map and    
     * I don't want to mess with the schema upgrade.
     */
    public static final String ARG_COMPENSATED = "compensated";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name which should be displayed in the UI. This can be plain text or
     * a message catalog key. This property is optional, if it is null
     * the shortname or name is used.
     */
    String _displayName;

    /**
     * A brief name to use when presenting scores and score items for 
     * this type. This is not ideal but the full name is much
     * too long (for example, Separation of Duty Violation Compensated Score).
     * This was added for ScoreItems so they can have a brief name
     * to display in the score explanation table.
     */
    String _shortName;

    /**
     * @deprecated The canonical name is now stored in _name, but this needs to 
     * stay around for awhile for upgrades.  
     */
    @Deprecated
    String _scoreName;

    /**
     * Name of the parent definition. 
     * This is used in cases where a "baseline" score 
     * and a "compensated" score are calculated, but all of the same configuration
     * from the baseline score definition is needed in the compensated score
     * definition. It has to be a string since it is not a full
     * SailPointObject yet, other ScoreDefinitions are referenced by name.
     */
    String _parent;

    /**
     * The fully qualified class name to the Scorer implementation.
     */
    String _scorer;

    /**
     * True if this score is disabled.
     * The score will not be calculated or be displayed in the
     * scorecard. This allows a scoring algorithm to remain 
     * in the configuration but not displayed. It is not
     * currently used for identity scores, but useful for
     * application scores where we ship a number of example
     * scores, not all of which might be needed.
     * 
     * UPDATE: noticed in 3.2 that this is redundant,
     * already inherit one from SailPointObject.
     */
    //boolean _disabled;

    /**
     * True if this calculates a score that will be included
     * in the composite score. If this is false and
     * _composite is false, this is considered a "raw" or
     * "baseline" score. Raw scores normally have a related
     * "compensated" score that will be marked as a component.
     * Compensated scores will also normally have a non-null
     * _parent reference to the raw score.
     *
     * @ignore
     * If both _component and _composite are set, _composite
     * has precedence.  Since these are mutually exclusive
     * a _type enumeration might be better.
     *
     * A more general model would be to require that the composite
     * scorer identify the component scores by name, that way
     * we could have more than one composite with different
     * components.  In that case we wouldn't need a flag,
     * but the UI only supports one composite right now.
     */
    boolean _component;

    /**
     * True if this calculates a composite score rather than a 
     * component or baseline score.  
     */
    boolean _composite;

    /**
     * Verbose description.
     */
    String _description;

    /**
     * The upper bound of the score produced by this scorer.
     * The lower bound is assumed to be zero.
     */
    int _range;

    // NOTE: I don't really like having weight here.  It would
    // be more flexible if we let ScoreDefinitions stand on their own,
    // then referenced them from the ScoreConfig with weightings defined
    // there rather than on the definition.  That would make it easier
    // to swap score algorithms without changing weights.  But as long
    // as the ScoreDefinitions are going to live inside the ScoreConfig
    // it is simpler to keep the weights here.

    /**
     * The weight this score has relative to its peers.
     * This is encoded as an integer from 0 to 100 representing
     * the percentage of the composite score this score contributes.
     */
    int _weight;
    
    /**
     * Name URL of the page where containing the configuration panel
     * for this score. Example: businessRoleScoringInclude.jsf
     */
    String _configPage;

    /**
     * Optional arguments to the scorer.
     * This might be used to tweak operating parameters.
     */
    Attributes<String,Object> _arguments;

    /**
     * For custom scores, the signature is used to defines the 
     * configurable arguments so that a configuration form
     * can be automatically generated.
     */
    Signature _signature;

    // TODO: Might want a ScoreConfig object to  hold things like
    // upper bounds common to all scores.

    /**
     * Cached resolved scorer instance.
     */
    Scorer _scorerInstance;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public ScoreDefinition() {
    }

    /**
     * @exclude
     * Return true here so HibernatePersistenceManager will not try
     * to save these when they are discovered within a ScoreConfig.
     * Temporary kludge until we can decide for sure whether these
     * should be SailPointObjects.
     */
    public boolean isXml() {
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setComponent(boolean b) {
        _component = b;
    }

    /**
     * True if this is a component score rather than a composite score.
     */
    public boolean isComponent() {
        // for temporary backward compatibility, recognize the older
        // argument convention for identifying components
        return (_component || isCompensated());
    }

    @XMLProperty
    public void setComposite(boolean b) {
        _composite = b;
    }

    /**
     * True if this is a composite score rather than a component score.
     */
    public boolean isComposite() {
        return _composite;
    }

    @XMLProperty
    public void setShortName(String s) {
        _shortName = s;
    }

    /**
     * A brief name to use when presenting scores and score items for 
     * this type.  
     */
    public String getShortName() {
        return _shortName;
    }

    /**
     * @exclude
     * @deprecated hack supported for upgrades but change the name so the
     * system doesn't call it any more
     */
    @Deprecated
    @XMLProperty(xmlname="scoreName")
    public void setXmlScoreName(String s) {
        _scoreName = s;
    }

    /**
     * @exclude
     * @deprecated hack supported for upgrades but change the name so the
     * system doesn't call it any more
     */
    @Deprecated
    public String getXmlScoreName() {
        return _scoreName;
    }

    /**
     * Name of the parent definition.
     * This is used in cases where a "baseline" score 
     * and a "compensated" score are calculated, but all of the same configuration
     * from the baseline score definition is needed in the compensated score
     * definition.
     */
    @XMLProperty
    public void setParent(String s) {
        _parent = s;
    }

    public String getParent() {
        return _parent;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String d) {
        _description = d;
    }

    /**
     * The upper bound of the score produced by this scorer.
     * The lower bound is assumed to be zero.
     */
    @XMLProperty
    public int getRange() {
        return _range;
    }

    public void setRange(int i) {
        _range = i;
    }

    /**
     * The weight this score has relative to its peers.
     * This is encoded as an integer from 0 to 100 representing
     * the percentage of the composite score this score contributes.
     */
    @XMLProperty
    public int getWeight() {
        return _weight;
    }

    public void setWeight(int i) {
        _weight = i;
    }

    /**
     * Name URL of the page where containing the configuration panel
     * for this score. Example: businessRoleScoringInclude.jsf
     */
    @XMLProperty
    public String getConfigPage() {
        return _configPage;
    }
    
    public void setConfigPage(String configPage) {
        _configPage = configPage;
    }

    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        this._displayName = displayName;
    }

    @XMLProperty
    public String getScorer() {
        return _scorer;
    }

    public void setScorer(String s) {
        _scorer = s;
        _scorerInstance = null;
    }

    public void setScorer(Class cls) {
        
        setScorer((cls != null) ? cls.getName() : null);
    }

    public void setScorer(Scorer s) {

        if (s == null)
            setScorer((String)null);
        else {
            setScorer(s.getClass());
            _scorerInstance = s;
        }
    }

    // !! crap, this will serialize as <Attributes> but we
    // want <Arguments>, not sure this is worth messing with
    // another serialization mode, or Attributes subclass, should
    // we just rename the property "attributes" and be done with it?

    /**
     * Optional arguments to the scorer.
     * This might be used to tweak operating parameters.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        if (args != null) 
            _arguments = args;
        else {
            // always keep an empty map for JSF
            // !! is this really necessary, its annoying for the XML
            // serialization
            _arguments = new Attributes<String,Object>();
        }
    }

    public Object getArgument(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }

    public void setArgument(String name, Object value) {

        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        _arguments.put(name, value);
    }

    /**
     * For custom scores, the signature is used to defines the 
     * configurable arguments so that a configuration form
     * may be automatically generated.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    /**
     * @deprecated Deprecated property accessor to convert an older enumeration
     * property into the new model.  There should be no
     * users of this other than during XML deserialization,
     * it will be eventually taken away.
     */
    @Deprecated
    @XMLProperty
    public void setType(String t) {
        if (t != null && t.equals("Composite"))
            _composite = true;
    }

    /**
     * @deprecated This never persists after the initial conversion.
     */
    @Deprecated
    public String getType() {
        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns the name depending on which name fields are populated in
     * this order of preference: displayName, shortName, name. Note that
     * this can be a message catalog key.
     */
    public String getDisplayableName() {
        if (_displayName != null)
            return _displayName;
        else if (_shortName != null)
            return _shortName;
        else
            return _name;
    }

    public String getString(String name) {
        return (_arguments != null) ? _arguments.getString(name) : null;
    }

    public int getInt(String name) {
        return (_arguments != null) ? _arguments.getInt(name) : null;
    }

    public float getFloat(String name) {
        return (_arguments != null) ? _arguments.getFloat(name) : null;
    }

    public boolean getBoolean(String name) {
        return (_arguments != null) ? _arguments.getBoolean(name) : null;
    }

    /**
     * Return true if this scorer is configured to calculate
     * a compensated score.
     */
    public boolean isCompensated() {

        boolean compensated = false;
        if (_arguments != null)
            compensated = _arguments.getBoolean(ARG_COMPENSATED);

        return compensated;
    }

    /**
     * Convenience accessor to resolve the scorer.
     */
    public Scorer getScorerInstance() throws GeneralException {


        if (_scorerInstance == null && _scorer != null) {
            try {
                Class cls = Class.forName(_scorer);
                _scorerInstance = (Scorer)cls.newInstance();
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }
        return _scorerInstance;
    }

    /** 
     * Utility to look for a score on a list. Necessary when you want
     * to search one of the score definitions in ScoreConfig without
     * knowing which one you have.
     */
    static public ScoreDefinition getScore(List<ScoreDefinition> scores, 
                                           String name) {

        ScoreDefinition found = null;
        if (name != null && scores != null) {
            for (ScoreDefinition d : scores) {
                if (name.equals(d.getName())) {
                    found = d;
                    break;
                }
            }
        }
        return found;
    }
    
};

