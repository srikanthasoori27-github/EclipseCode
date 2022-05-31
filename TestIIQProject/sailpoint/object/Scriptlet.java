package sailpoint.object;

import java.io.Serializable;
import sailpoint.tools.Util;

/**
 * This define a transient object used to parse scriptlets.
 * It is not persisted, the workflow/forms engines will build
 * these at runtime when they need to evaluate a scriptlet.
 *
 * Most classes allow either a scriptlet represented as
 * an XML attribute and a Script represented as an element.
 * The Script element is normally used of you have complex
 * multi-line scripts, the attribute if you have simple
 * boolean expressions.
 *
 * You are not supposed to have both.  If a class does have
 * values for both the Script is preferred.
 *
 * @ignore
 * TODO: Consider using this in workflow/forms editors as
 * a bidirectional object used to set the values of the
 * scriptlet or script.
 *
 * Note that if we have a Script object rather than a scriptlet
 * we clone it for the Scriplet.  This is done so we can "doctor"
 * the source later by prepending a library of utility functions 
 * (see Workflower.doScript).  If we do this to a Script object
 * that came directly from the Workflow model this doctoring ends
 * up being persisted in the case which looks messy.  Cloning 
 * means that Scriptlet and what it contains are always transient
 * and the engine can have it's way without affecting the persistent
 * model.
 */
public class Scriptlet implements Serializable {
    
    //
    // Evaluation Methods
    //

    public static final String METHOD_STRING = "string:";
    public static final String METHOD_REF = "ref:";
    public static final String METHOD_RULE = "rule:";
    public static final String METHOD_SCRIPT = "script:";
    public static final String METHOD_CALL = "call:";

    String _defaultMethod;
    
    String _string;
    String _call;
    String _rule;
    String _reference;
    Script _script;
    boolean _not;

    /**
     * Private because we require a scriptlet string or Script.
     */
    private Scriptlet(String defaultMethod) {
        _defaultMethod = defaultMethod;
    }
    
    /**
     * Constructor from a scriptlet string.
     */
    public Scriptlet(String scriptlet, String defaultMethod) {
        this(defaultMethod);
        parse(scriptlet);
    }

    /**
     * Constructor from a scriptlet string or a Script - only one should be
     * non-null.
     */
    public Scriptlet(String scriptlet, String defaultMethod, Script script) {
        this(defaultMethod);
        if (script == null)
            parse(scriptlet);
        else {
            // must clone, see comments above
            _script = new Script(script);
        }
    }

    /**
     * Constructor from a DynamicValue.
     */
    public Scriptlet(DynamicValue dv, String defaultMethod) {
        this(defaultMethod);
        if (null != dv) {
            if (null != dv.getValue()) {
                _string = Util.otos(dv.getValue());
            }
            else if (null != dv.getRule()) {
                _rule = dv.getRule().getName();
            }
            else if (null != dv.getScript()) {
                _script = dv.getScript();
            }
        }
    }
    
    public boolean isNot() {
        return _not;
    }

    public String getString() {
        return _string;
    }

    public String getCall() {
        return _call;
    }

    public String getRule() {
        return _rule;
    }

    public String getReference() {
        return _reference;
    }

    public Script getScript() {
        return _script;
    }

    /**
     * Convert this Scriptlet either to a String representation (for example - rule:Foo)
     * or a Script if this is a multi-line script.
     */
    public Object toValue() {
        Object val = null;

        if (null != _string) {
            val = prefix(_string, METHOD_STRING);
        }
        else if (null != _call) {
            val = prefix(_call, METHOD_CALL);
        }
        else if (null != _rule) {
            val = prefix(_rule, METHOD_RULE);
        }
        else if (null != _reference) {
            val = prefix(_reference, METHOD_REF);
        }
        else if ((null != _script) && (null != _script.getSource())) {
            // We'll use the "script:" prefix for single-line scripts and a
            // Script object for everything else.
            String source = _script.getSource().trim();
            if (source.contains("\n") || source.contains("\r")) {
                val = _script;
            }
            else {
                val = prefix(source, METHOD_SCRIPT);
            }
        }

        if ((null != val) && (val instanceof String) && _not) {
            val = "!" + val;
        }
        
        return val;
    }
    
    private String prefix(String val, String method) {
        if (!method.equals(_defaultMethod)) {
            return method + val;
        }
        return val;
    }
    
    private void parse(String scriptlet) {
        if (!Util.isNullOrEmpty(scriptlet)) {
            // A convenient built-in negator so we don't always
            // have to use scripts.  Note that if this is actually
            // a script, we pass it through to make sure that the
            // true script semantics are obeyed.  Necessary?
            if (scriptlet.startsWith("!")) {
                _not = true;
                scriptlet = strip(scriptlet, "!");
            }

            if (scriptlet.startsWith(METHOD_STRING))
                _string = strip(scriptlet, METHOD_STRING);

            else if (scriptlet.startsWith(METHOD_CALL))
                _call = strip(scriptlet, METHOD_CALL);

            else if (scriptlet.startsWith(METHOD_RULE))
                _rule = strip(scriptlet, METHOD_RULE);

            else if (scriptlet.startsWith(METHOD_REF)) {
                _reference = strip(scriptlet, METHOD_REF);
            }

            else if (scriptlet.startsWith(METHOD_SCRIPT)) {
                _script = new Script();
                _script.setSource(strip(scriptlet, METHOD_SCRIPT));
            }
            else if (METHOD_STRING.equals(_defaultMethod)) 
                _string = scriptlet;

            else if (METHOD_CALL.equals(_defaultMethod)) 
                _call = scriptlet;

            else if (METHOD_RULE.equals(_defaultMethod)) 
                _rule = scriptlet;

            else if (METHOD_REF.equals(_defaultMethod)) 
                _reference = scriptlet;

            else if (METHOD_SCRIPT.equals(_defaultMethod)) {
                // hmm, if we stripped off the built-in negator,
                // there's a chance it could produce a different result
                // if evaluated by the script interpreter.  Put it
                // back just to be safe.  These two forms must have the
                // same result if the default evalautor is SCRIPT:
                //    !something
                //    script:!something
                if (_not) {
                    scriptlet = "!" + scriptlet;
                    _not = false;
                }
                _script = new Script();
                _script.setSource(scriptlet);
            }
        }
    }

    private static String strip(String src, String prefix) {
        String result = null;
        if (src.length() > prefix.length())
            result = src.substring(prefix.length());
        return result;
    }

    /**
     * Return true if the source is logically empty.
     * This is important to know for things like the step condition
     * where absence of any scripts means "true" but existence requires
     * returning "true".
     */
    public boolean isEmpty() {
        return (_string == null && _call == null && _rule == null &&
                _reference == null && _script == null);
    }

    /**
     * Return true if this Scriptlet is a string literal.
     */
    public boolean isLiteral() {
        return (_call == null && _rule == null &&
                _reference == null && _script == null);
    }
}