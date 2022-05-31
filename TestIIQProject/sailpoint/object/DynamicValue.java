/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating several ways to specify a value in some parts 
 * of the model: as a literal value, as a script to calculate the value,
 * or as a reference to a rule to calculate the value.
 *
 * Author: Jeff
 *
 * We started needing this little collection of things in several places, 
 * and are likely to be adding more so I wanted to add a wrapper class
 * to make it easier to deal with the entire unit.
 *
 * These are currently used in the Template and Field models.  
 * They could be retrofitted into some parts of the Workflow model
 * but the way we do scriplets there has too much history so we can't
 * just drop this in without breaking old workflows and besides this
 * is much more wordy.  
 *
 * Use of this is appropriate in places where you have several dynamic
 * values in the same class.  If you have only one, these are typically
 * inlined so the literal can go in the containing element.
 *
 * These can also be built on the fly to deal with things that have
 * values and scripts but don't represent them this way in the 
 * persistent model.
 *
 * Note that as always you should not be putting evaluation logic in 
 * the object package, only the model itself.   See 
 * sailpoint.api.DynamicEvaluator for the evaluator.
 *
 */

package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;


public class DynamicValue extends AbstractXmlObject {

    private static final Log log = LogFactory.getLog(DynamicValue.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A reference to a rule.
     */
    Rule _rule;

    /**
     * An inline script.
     */
    Script _script;

    /**
     * The literal value. Possibly a scriptlet.
     */
    Object _value;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //  
    //////////////////////////////////////////////////////////////////////

    public DynamicValue() {
    }

    public DynamicValue(Rule r, Script s, Object o) {
        _rule = r;
        _script = s;
        _value = o;
    }

    public void load() {

        if (_rule != null)
            _rule.load();
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public void setRule(Rule rule) {
        _rule = rule;
    }

    public Rule getRule() {
        return _rule;
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setScript(Script s) {
        _script = s;
    }

    public Script getScript() {
        return _script;
    }

    // note we have the typical XML shenanigans to let simple
    // values be attributes

    public void setValue(Object value) {
        _value = value;
    }

    public Object getValue() {
        return _value;
    }

    /**
     * @exclude
     * Required for Hibernate
     * @deprecated  use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="value")
    public String getValueXmlAttribute() {
        return (_value instanceof String) ? (String)_value : null;
    }

    /**
     * @exclude
     * Required for Hibernate  
     * @deprecated  use {@link #setValue(Object)}
     */
    @Deprecated
    public void setValueXmlAttribute(String value) {
        _value = value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="Value")
    public Object getValueXmlElement() {
        return (_value instanceof String) ? null : _value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #setValue(Object)}
     */
    @Deprecated
    public void setValueXmlElement(Object value) {
        _value = value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this is logically empty.
     * Important for objects that can inherit values from
     * a higher level object unless overridden.
     */
    public boolean isEmpty() {
        
        // hmm, do we need to Util.nullify the value?
        return (_rule == null && _script == null && _value == null);
    }

    public boolean isLiteral() {

        return (_rule == null && _script == null);
    }

    /**
     * Helper for Field evaluators. Set the literal field value
     * and clear the script or rule used to calculate it. This should
     * only be used on clones during template/field compilation. It
     * saves some XML clutter. 
     *
     * ?? Is this still necessary after the Formicator redesign?
     */
    public void setFinalValue(Object value) {
        _value = value;
        _rule = null;
        _script = null;
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Attributes storage utilities
    //  
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return a DynamicValue for the requested attribute in the attributes map,
     * or null if the requested attribute is null or not a DynamicValue (for example -
     * rule or script).
     */
    public static DynamicValue get(Attributes<String,Object> attrs,
                                   String attribute,
                                   Resolver resolver)
        throws GeneralException {
        return get(attrs, attribute, resolver, true);
    }

    /**
     * Return a DynamicValue for the requested attribute in the attributes map.
     * If excludeLiterals is true and the value in the map is a literal, this
     * returns null.
     */
    public static DynamicValue get(Attributes<String,Object> attrs,
                                   String attribute,
                                   Resolver resolver,
                                   boolean excludeLiterals)
        throws GeneralException {
        
        DynamicValue dv = null;
        
        if (null != attrs) {
            Rule rule = null;
            Script script = null;
            Object value = null;
    
            Scriptlet scriptlet = getScriptlet(attrs, attribute);
            if (null != scriptlet) {
                // Scriptlets support a few things that are not valid on Fields
                // yet since they are treated as DynamicValues.  If we have an
                // invalid scriptlet we'll just return null..
                if ((null != scriptlet.getCall()) || (null != scriptlet.getReference()) ||
                    scriptlet.isNot()) {
                    log.warn("Invalid scriptlet - not/ref/call not supported: " + attrs.getFloat(attribute));
                }
                else {
                    if (null != scriptlet.getRule()) {
                        rule = resolver.getObjectByName(Rule.class, scriptlet.getRule());

                        // Throw an exception if this was configured as a rule but the rule
                        // does not exist, so people can tell why things aren't working as
                        // they expect.
                        if (null == rule) {
                            throw new GeneralException("Cannot find rule: " + scriptlet.getRule());
                        }
                    }
                    script = scriptlet.getScript();
                    value = scriptlet.getString();
                }
            }
            else {
                // If this wasn't a scriptlet, try to pull the literal value
                // from the attributes map.
                value = attrs.get(attribute);
            }
    
            // If we have a rule or script, OR if we have a literal and we're
            // not excluding literals ... return a DynamicValue.
            if ((null != rule) || (null != script) ||
                ((null != value) && !excludeLiterals)) {
                dv = new DynamicValue(rule, script, value);
            }
        }
        
        return dv;
    }
    
    /**
     * Set the given DynamicValue as a scriptlet in the attributes map.
     */
    public static Attributes<String,Object> set(Attributes<String,Object> attrs,
                                                String attribute, DynamicValue dv) {
        Object value = null;
        if (null != dv) {
            // Special case for booleans.  We want to remove "false" from the
            // attributes map.  Set the value to null and it will get removed.
            if ((null != dv.getValue()) && (dv.getValue() instanceof Boolean) &&
                !Util.otob(dv.getValue())) {
                value = null;
            }
            else {
                Scriptlet scriptlet = new Scriptlet(dv, Scriptlet.METHOD_STRING);
                value = scriptlet.toValue();
            }
        }

        attrs = (null != attrs) ? attrs : new Attributes<String,Object>();
        if (null != value) {
            attrs.put(attribute, value);
        }
        else {
            attrs.remove(attribute);
        }

        return (!attrs.isEmpty()) ? attrs : null;
    }
    
    /**
     * Return true if the given attribute is a DynamicValue.
     */
    public static boolean isDynamicValue(Attributes<String,Object> attrs,
                                         String attribute) {
        Scriptlet s = getScriptlet(attrs, attribute);
        return ((null != s) && !s.isLiteral());
    }

    /**
     * Return a Scriptlet for the requested attribute in the attributes map, or
     * null if the attribute is not found or is a non-string literal.
     */
    public static Scriptlet getScriptlet(Attributes<String,Object> attrs,
                                         String attribute) {

        Scriptlet scriptlet = null;

        String string = null;
        Script script = null;
        Object val = (null != attrs) ? attrs.get(attribute) : null;
        
        if (null != val) {
            if (val instanceof Script) {
                script = (Script) val;
            }
            else if (val instanceof String) {
                string = (String) val;
            }
    
            if ((null != string) || (null != script)) {
                scriptlet = new Scriptlet(string, Scriptlet.METHOD_STRING, script);
            }
        }
        
        return scriptlet;
    }
}
