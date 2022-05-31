/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for various things in a Worklfow that can
 * be defined either with a scriptlet or a multi-line script.
 *
 * Author: Jeff
 *
 * I expect this will undergo a lot of revision.  Some options are:
 *
 *    - radio button to select scriptlet vs script with single line
 *      vs multi line text areas
 *
 *    - single multi-line text area with intelligent selection of
 *      type by looking at the script size
 *
 *    - combo box to select between each of the scriptlet methods
 *      expanding to a multi-line text area when "script" is selected
 *      saves having to type the method prefix in the scriptlet text
 *
 *    - some way to represent the default scriptlet method?
 */

package sailpoint.web.workflow;

import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.web.BaseDTO;

public class ScriptDTO extends BaseDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    // not supporting Script._language!
    
    /**
     * used to mark a script as explicitly declaring its method, even though it may be the default.
     * @see #getScriptlet()
     */
    boolean _explicitMethod = false;
    boolean _negate;
    String _defaultMethod;
    String _method;
    String _source;

    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    public ScriptDTO(String scriptlet, Script script, String defaultMethod) {

        _defaultMethod = defaultMethod;

        if (script != null) {
            _method = Scriptlet.METHOD_SCRIPT;
            _source = script.getSource();
        }
        else if (scriptlet == null) {
            _method = defaultMethod;
        }
        else {
            if (scriptlet.startsWith("!")) {
                _negate = true;
                if (scriptlet.length() > 1)
                    scriptlet = scriptlet.substring(1);
                else
                    scriptlet = "";
            }

            int colon = scriptlet.indexOf(":");
            if (colon <= 0) {
                _method = defaultMethod;
                _source = scriptlet;
            }

            // what should an initial colon this mean? 
            // the default or is it literal?
            if (colon > 0) {
                String method = scriptlet.substring(0, colon);
                if (Scriptlet.METHOD_CALL.equals(method+":") ||
                    Scriptlet.METHOD_SCRIPT.equals(method+":") ||
                    Scriptlet.METHOD_RULE.equals(method+":") ||
                    Scriptlet.METHOD_REF.equals(method+":") ||
                    Scriptlet.METHOD_STRING.equals(method+":")) {
                    // valid    
                    _method = method;
                    _explicitMethod = true;
                    int remainder = colon + 1;
                    if (scriptlet.length() > remainder)
                        _source = scriptlet.substring(remainder);
                }
                else {
                    // Assume it's literal text in something
                }
            }

            if (_method == null) {
                _method = defaultMethod;
                _source = scriptlet;
            }   
        }
    }

    public ScriptDTO(ScriptDTO src) {
        if (src != null) {
            _negate = src._negate;
            _explicitMethod = src._explicitMethod;
            _defaultMethod = src._defaultMethod;
            _method = src._method;
            _source = src._source;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * These don't commit like other DTOs because we don't have a common
     * model for the scriptlet/script pair.
     */
    public boolean isScript() {

        // if the script looks simple, collapse it to a scriptlet
        return Scriptlet.METHOD_SCRIPT.startsWith(_method) &&
            _source != null && 
            (_source.indexOf("\n") >=0 || _source.length() > 100);
    }
    
    /**
     * @return either method:source or source if the method matches the defaultMethod.
     */
    public String getScriptlet() {
        
        String scriptlet = null;
        
        if(_source==null || _source.equals("null")) 
        	return scriptlet;

        // should we strip these or normalize them, I like to 
        // see them script, especially for string: args
        // UPDATE : bug #15971 - incorrect calculation in getScriptlet when using constructor 
        //          ScriptDTO("script:something",null,Scriptlet.SCRIPT) would previously return "script:something"
        //          original intent was to return "something".
        String noColonMethod = (_method != null) ? _method.replace(":", "") : _method;
        String noColonDefault = (_defaultMethod != null) ? _defaultMethod.replace(":", "") : _defaultMethod;
        
        if ((noColonMethod).equals((noColonDefault)) && !_explicitMethod)
            scriptlet = _source;
        else 
            scriptlet = _method + ":" + _source;
        
        if(_negate)
            scriptlet="!"+scriptlet;

        return scriptlet;
    }

    public Script getScript() {

        Script s = new Script();
        s.setSource(_source);
        return s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isNegate() {
        return _negate;
    }

    public void setNegate(boolean b) {
        _negate = b;
    }

    public String getMethod() {
        if(_method!=null && _method.contains(":")) {
            _method = _method.replace(":", "");
        }
        return _method;
    }
    
    public void setMethod(String s) {
        if(s!=null && s.contains(":")) {
            s = s.replace(":", "");
        }
        _method = s;
    }

    public String getSource() {
        return _source;
    }

    public void setSource(String s) {
        _source = s;
    }

    public boolean isExplicitMethod() {
        return _explicitMethod;
    }

    public void setExplicitMethod(boolean explicitMethod) {
        this._explicitMethod = explicitMethod;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    public boolean isLiteral() {
        if ( _method == null || Scriptlet.METHOD_STRING.startsWith(_method) ) {
            return true;
        }
        return false;
    }
    
    public boolean isReference() {
        if ( _method != null ) {
            if ( Scriptlet.METHOD_REF.startsWith(_method) ) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isRule() {
        if ( _method != null ) {
            if ( Scriptlet.METHOD_RULE.startsWith(_method) ) {
                return true;
            }
        }
        return false;
    }    

}
