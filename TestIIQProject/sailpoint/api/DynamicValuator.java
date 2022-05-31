/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A utility class used to encapsulate various methods for calculating
 * values specified in the Workflow and Form models.  
 *
 * Author: Jeff
 *
 * I am not happy with this!
 *
 * This was added late in 5.0 to assist in sharing some code between
 * PlanCompiler and Formicator, in theory what this does should be shared
 * by Workflower but there are issues to work out.
 * 
 * Essentially what this does is provide a way to represent all of the
 * ways a value might be defined: literal, script, rule, or "scriptlet"
 * which can in turn be a literal, rule, reference, or call (to 
 * a workflow library method).
 *
 * We've evolved these representations over time and are still working
 * out the best way to make these consistent, if indeed we ever do.
 *
 * Workflows have traditionally let everything be defined by a script/script
 * pair. Recently Arg started allowing literal values. 
 *
 * Field needs to let "value" be defined using a literal,
 * script or rule.
 *
 * Field needs to let owner be defined by scripts or rules.
 *
 * Template needs to let owner be defined by scripts or rules.
 *
 * Workflower is already using Scriplet to do most of what this
 * class does.  The difference is that Scriplet is part of the
 * object model (a transient part) and as such isn't supposed to have
 * complicated business logic in it like actually running scripts/rules etc.
 *
 * I like having the thing that analyzes the various sources and picks
 * the right way to evaluate them up at the biz-logic level, but then
 * we need to work out a way for these to be attached to the object
 * model so they can be cahed and reused at runtime.  
 *
 * This needs more thought, but it's at step in the direction I think
 * we'll go in. Do not get too confortable with this class, it is likely
 * to change.
 *
 * If multiple sources happen to be set, the evaluation priority is:
 * 
 *    call
 *    rule
 *    script
 *    reference
 *    literal
 *
 * This is a bit different than Worklfower.
 */

package sailpoint.api;

import java.util.Map;

import sailpoint.object.DynamicValue;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.VariableExpander;


public class DynamicValuator {
        
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A literal value.
     */
    Object _literal;

    /**
     * A script to calculate the value.
     */
    Script _script;

    /**
     * A rule name to calculate the value.
     */
    String _ruleName;

    /**
     * The cached resolved rule.
     * This is only useful if we can find a way to serialize ValueSources.
     */
    Rule _rule;

    /**
     * A named reference to an variable in the calling environment.
     */
    String _reference;

    /**
     * A named call to a "handler method".  Currently this is only
     * defined by workflows as names of library methods, but we migth   
     * want to extend that to Templates someday.    
     */
    String _call;

    /**
     * True if the value is to be negated.
     * This is relevant only when parsing scriptlets.
     */
    boolean _not;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * No arg constructor you can use if you want to build
     * this out using explicit setter calls.
     */
    public DynamicValuator() {
    }

    /**
     * Constructor used to process a DynamicValue. 
     */
    public DynamicValuator(DynamicValue src) {

        if (src != null) {
            Rule rule = src.getRule();
            if (rule != null)
                setRule(rule);
            else {
                Script script = src.getScript();
                if (script != null)
                    setScript(script);
                else {
                    _literal = Util.nullify(src.getValue());
                    if (_literal instanceof String) {
                        // let it be a scriptlet    
                        setScriptlet((String)_literal, Scriptlet.METHOD_STRING);
                    }
                }
            }
        }
    }

    public void setLiteral(Object o) {
        _literal = o;
    }

    public void setReference(String s) {
        _reference = s;
    }
    
    public void setCall(String s) {
        _call = s;
    }

    public void setRule(String s) {
        _ruleName = s;
        _rule = null;
    }

    public void setRule(Rule r) {
        _rule = r;
        if (r != null)
            _ruleName = r.getName();
        else
            _ruleName = null;
    }

    /**
     * Set the script.
     * Workflow.Source has historically cloned this so that it could
     * be doctored by Workflower to add the standard library methods
     * like isTrue without having these be persisted.  This no longer
     * happens so we just copy the reference.
     */
    public void setScript(Script s) {
        _script = s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workflow Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize the source with a default string scriptlet.
     */
    public DynamicValuator(String scriptlet) {

        setScriptlet(scriptlet, Scriptlet.METHOD_STRING);
    }

    /**
     * Initialize the source with a scriptlet and a default method.
     */
    public DynamicValuator(String scriptlet, String defaultMethod) {

        setScriptlet(scriptlet, defaultMethod);
    }

    /**
     * Initialize the source with a scriptlet, default method, 
     * and a script object.  The script object has priority.
     */
    public DynamicValuator(String scriptlet, String defaultMethod, Script script) {

        if (script != null)
            setScript(script);
        else
            setScriptlet(scriptlet, defaultMethod);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scriptlet parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse a scrptlet and set the other fields.
     */
    public void setScriptlet(String scriptlet, String defaultMethod) {

        if (scriptlet != null) {
            // A convenient built-in negator so we don't always
            // have to use scripts.  Note that if this is actually
            // a script, we pass it through to make sure that the
            // true script semantics are obeyed.  Necessary?
            if (scriptlet.startsWith("!")) {
                _not = true;
                scriptlet = strip(scriptlet, "!");
            }
        }

        if (scriptlet != null) {

            // todo: need to support other literal types like int: date: etc.
            if (scriptlet.startsWith(Scriptlet.METHOD_STRING))
                _literal = strip(scriptlet, Scriptlet.METHOD_STRING);

            else if (scriptlet.startsWith(Scriptlet.METHOD_CALL))
                _call = strip(scriptlet, Scriptlet.METHOD_CALL);

            else if (scriptlet.startsWith(Scriptlet.METHOD_RULE))
                setRule(strip(scriptlet, Scriptlet.METHOD_RULE));

            else if (scriptlet.startsWith(Scriptlet.METHOD_REF))
                _reference = strip(scriptlet, Scriptlet.METHOD_REF);

            else if (scriptlet.startsWith(Scriptlet.METHOD_SCRIPT)) {
                _script = new Script();
                _script.setSource(strip(scriptlet, Scriptlet.METHOD_SCRIPT));
            }
            else if (Scriptlet.METHOD_STRING.equals(defaultMethod)) 
                _literal = scriptlet;

            else if (Scriptlet.METHOD_CALL.equals(defaultMethod)) 
                _call = scriptlet;

            else if (Scriptlet.METHOD_RULE.equals(defaultMethod)) 
                setRule(scriptlet);

            else if (Scriptlet.METHOD_REF.equals(defaultMethod))
                _reference = scriptlet;

            else if (Scriptlet.METHOD_SCRIPT.equals(defaultMethod)) {
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

    static  private String strip(String src, String prefix) {
        String result = null;
        if (src.length() > prefix.length())
            result = src.substring(prefix.length());
        return result;
    }

    /**
     * Return true if a string has a scriptlet prefix.
     * Used in a few cases (Formicator) where we can't tell if
     * something has been expanded or not.
     */
    static public boolean isScriptlet(String s) {

        boolean scriptlet = false;

        if (s != null) {
            // may be the negator
            if (s.startsWith("!"))
                s = strip(s, "!");
        }

        if (s != null) {
            // todo: need to support other literal types like int: date: etc.
            scriptlet = (s.startsWith(Scriptlet.METHOD_STRING) ||
                         s.startsWith(Scriptlet.METHOD_CALL) ||
                         s.startsWith(Scriptlet.METHOD_RULE) ||
                         s.startsWith(Scriptlet.METHOD_REF) ||
                         s.startsWith(Scriptlet.METHOD_SCRIPT));
        }

        return scriptlet;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Evalutor
    //
    //////////////////////////////////////////////////////////////////////
        
    /**
     * Hack to let the caller know if it needs to build an arg map.
     * True if the evaluator will be a rule or script.
     * Saves a little garbage when we're working mostly with literal values.
     */
    public boolean needsArgs() {

        // !! what about literal strings with $() references
        // should be okay now since this is only called for Field values
        return (_ruleName != null || _script != null);
    }

    /**
     * Evaluate the source and return it's value.
     *
     * A SailPointContext must be supplied to run scripts and rules
     * and also to resolve rules.
     *
     * The "vars" map is used to resolve references.   It may be better
     * for  this to be done with an interafce like ReferenceResolver
     * so the caller doesn't have to manufacture a Map.
     *
     * The "args" map is passed to the script or rule if there is one.
     * If the value is a literal string these are the inputs
     * to Variable.expand which substitues $() references.
     * 
     * It currently cannot handle calls until we figure out a 
     * CallResolver interface if that ever becomes necessary.  Until 
     * we try to make Workfower use this it isn't an issue.
     *
     */
    public Object evaluate(SailPointContext con, 
                           Map<String,Object> args)
        throws GeneralException {

        Object value = null;

        if (_call != null) {
            throw new GeneralException("Unsupported method: call");
        }
        else if (_ruleName != null) {

            if (_rule == null) {
                _rule = con.getObjectByName(Rule.class, _ruleName);
                if (_rule == null)
                    throw new GeneralException("Invalid rule: " + _ruleName);
            }

            // another thing that Workflower can do that we can't is
            // pass a List<Rule> of "rule libraries", this may be interesting   
            // to template and fields, think...
            try {
                value = con.runRule(_rule, args);
            }
            catch (GeneralException e) {
                // try to soften the typical BeanShell exception mess
                String msg = "Exception evaluating rule: " + _ruleName + 
                    "  \n" + ExceptionCleaner.getString(e);
                throw new GeneralException(msg);
            }

        }
        else if (_script != null) {
            try {
                value = con.runScript(_script, args);              
            }
            catch (GeneralException e) {
                // try to soften the typical BeanShell exception mess
                String msg = ExceptionCleaner.getString(e);
                throw new GeneralException(msg);
            }
        }
        else if (_reference != null) {
            if (args != null)
                value = args.get(_reference);
        }
        else {
            value = _literal;
            if (value instanceof String) {
                // allow $() expansion like workflows
                value = VariableExpander.expand((String)value, args);
            }
        }

        if (_not)
            value = new Boolean(!isTruthy(value));

        return value;
    }

    /**
     * Return true if a value is considered
     * logically true for workflows.
     * 
     * What is truth? Truth is defined as any non-null value 
     * 
     *    Boolean.FALSE
     *    empty string ""
     *    string "false"
     *
     * NOTE: Same method exists in Workflower, share someday...
     * Maybe this is core enough to go in Util.
     */
    private boolean isTruthy(Object o) {

        boolean truthy = false;

        if (o instanceof Boolean)
            truthy = ((Boolean)o).booleanValue();
        else if (o instanceof String) {
            String s = (String)o;
            truthy = (s.length() > 0 && !s.equals("false"));
        }
        else
            truthy = (o != null);
        
        return truthy;
    }


}

