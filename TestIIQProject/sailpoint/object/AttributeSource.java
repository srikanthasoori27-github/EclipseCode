/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Part of the ObjectAttribute model.  Used for attributes whose
 * values may be derived from one of several sources.  In practice
 * these are used only for Identity attributes.
 *
 * Author: Jeff
 *
 * NOTE: This was originally an inner class of IdentityAttribute
 * but it had to be split out so it could be shared temporarily
 * with both IdentityAttribute and ObjectAttribute until after
 * the upgrade.  Eventually it could be merged back into
 * ObjectAttribute.  It is an XML issue, you cannot have two
 * different classes using the same XML element name.
 *
 */
package sailpoint.object;

import java.io.Serializable;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Part of the ObjectAttribute model.  Used for attributes whose
 * values can be derived from one of several sources.  In practice
 * these are used only for Identity attributes.
 *
 * The attribute can be sourced from one of three places:
 *
 * <ol>
 * <li>An application account attribute</li>
 * <li>A rule applied to an application account link</li>
 * <li>A rule applied to the entire identity cube</li>
 * </ol>
 *
 * When sourcing directly from an account attribute the 
 * application and name properties must be set and the
 * instance can optionally be set if using template applications.
 *
 * When sourcing from an account rule the application and
 * rule properties must be set, the instance is optional.
 * The rule is passed the Link object representing the account,
 * as well as the Identity, the AttributeDefinition, and
 * this AttributeSource.  The name property is optional
 * but can be set if the rule is a generic formatting rule
 * that can be used with several attributes.
 *
 * If no application is specified, then this represents
 * a "global rule" that is applied to the entire identity cube.
 * The rule property must be set to a Rule that will receive
 * the Identity, the AttributeDefinition, and this AttributeSource.
 * The instance and name properties are normally ignored by the rule.
 * In theory you could use these to pass arguments to the rule
 * but since these are not settable in the UI this is not recommended.
 *
 * @ignore
 * TODO: Consider exposing something in the UI to set one or more
 * arguments to be passed into the global rule.
 */
@XMLClass
public class AttributeSource implements Serializable {
    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An Application that can provide a value for the attribute.
     */
    Application _application;

    /**
     * Optional instance name for template applications.
     * If this is null and _application is a template,
     * a link is selected at random.
     */
    String _instance;

    /**
     * The name of the attribute on the application.
     */
    String _name;

    /**
     * A Rule that will calculate a value for the attribute.
     */
    Rule _rule;

    /**
     * Derived identifier for this source. See getKey method for
     * more about what this is.
     */
    String _key;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public AttributeSource() {
    }

    public void load() {

        if (_application != null)
            _application.load();

        if (_rule != null)
            _rule.load();
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public void setApplication(Application a) {
        _application = a;
        _key = null;
    }

    /**
     * An Application that can provide a value for the attribute.
     */
    public Application getApplication() {
        return _application;
    }

    @XMLProperty
    public void setInstance(String inst) {
        _instance = inst;
        _key = null;
    }

    /**
     * Optional instance name for template applications.
     * If this is null and _application is a template,
     * a link is selected at random.
     */
    public String getInstance() {
        return _instance;
    }

    @XMLProperty
    public void setName(String name) {
        if (name != null && name.length() > 0) {
            _name = name.trim();
        } else {
            _name = name;
        }
        _key = null;
    }

    /**
     * The name of the attribute on the application.
     */
    public String getName() {
        return _name;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public void setRule(Rule r) {
        _rule = r;
        _key = null;
    }

    /**
     * A Rule that will calculate a value for the attribute.
     * The rule is always passed an Identity object in the "identity"
     * argument. If the source includes an Application reference
     * the Link associated with that Application is passed to the
     * rule in the "link" argument.
     */
    public Rule getRule() {
        return _rule;
    }
        
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Return the (usually) unique key for this attribute source.
     * 
     * Since these are not persistent objects they do not have
     * generated uuids. Even if you tried to maintain one, the UI
     * is not set up right now to preserve them.
     *
     * Instead a key is derived by combining the other properties.
     * Normally this will be unique, but in theory you could have
     * more than one AttributeSource on a list that just happen to have
     * the same key. That is considered a benign error since they
     * would be functionally identical.  
     *
     * This is intended for use by the Identitizer which will
     * store it in the "source" property of AttributeMetadata in order
     * to determine where the attribute value came from.
     *
     * Global rule source keys will look like this:
     *
     *    $<ruleName>
     * 
     * Application source keys will look like this:
     *
     *    <applicationName>[/<instanceName>][:<attributeName>][$<ruleName>]
     *
     * @ignore
     *
     * The keys are potentially ambiguous if you have /:$ in application
     * names but wanted these to be concise and readable in the
     * common cases. In practice you will not have collisions, if 
     * the application name is surrounded in "
     *
     */
    public String getKey() {
        if (_key == null) {
            StringBuilder b = new StringBuilder();
            if (_application != null) {
                b.append(_application.getName());
                if (_instance != null) {
                    b.append("/");
                    b.append(_instance);
                }
                if (_name != null) {
                    b.append(":");
                    b.append(_name);
                } 
            }
            
            if (_rule != null) {
                b.append("$");
                b.append(_rule.getName());
            }
            
            _key = b.toString();
        }
        return _key;
    }

    /*
     * Return true if two sources are logically the same.
     * The applications must match.  If both names are non-null
     * they must match.  If both names are null, then they match
     * because there can only be generic rule for a given Application.
     */
    public boolean isMatch(AttributeSource other) {

        boolean match = false;
            
        // careful with == on _application, one side or the 
        // other may be "enhanced" by Hibernate and won't be the same
        Application otherapp = other.getApplication();
        if ((_application == null && otherapp == null) ||
            (_application != null && _application.equals(otherapp))) {

            String othername = other.getName();
            if (_name != null) {
                if (othername != null)
                    match = _name.equals(othername);
            }
            else if (othername == null)
                match = true;
        }
        return match;
    }

}
