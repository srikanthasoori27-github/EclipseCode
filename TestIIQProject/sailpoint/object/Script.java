/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An anonymous script.
 *
 * These are similar to Rules but they don't have database identity.
 * They are embedded in other objects that need a fragment of logic
 * but don't need the overhead of a Rule.
 *
 * As of 5.0 scripts may now "include" Rule objects.  Included rules
 * will have their source inserted into the compilation of the script
 * so they can provide functions for the script.  Includes may
 * be defined by setting the _includes property, but that's rare since
 * the script UI doesn't currently support it.  Instead you can put
 * comment lines in the source like this:
 *
 *       //#include Foo
 *       //#include "My Rule Library"
 *       //#include Library1,Library2
 * 
 * This also serves as a place to store the compiled interpreter
 * state for reuse.  This is an opaque object set by the RuleRunner.
 * Originally BSFRuleRunner maintained an internal map for Rules
 * where it would cache interpreters keyed by rule name.  But since
 * these don't have identity we have to associate the interpreter
 * directly with each Script object.
 *
 * TODO: Think about a variant of scripts called "expressions".
 * These would be fragments of BeanShell that get automatically wrapped
 * with the necessary BeanShell to make a proper script.  For example,
 * in workflows it will be common to have expression gragments like this:
 *
 *       approval.getApproved() == true
 *
 * but what you have to write to get a boolean passed back to the
 * caller is this:
 *
 *      return (approval.getApproved() == true);
 * 
 * Alternately we could have Javascript be the default language for
 * scriptlets:
 *
 *       approval.approved == true
 * 
 *
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An embedded script.
 *
 * These are similar to Rules but they do not have database identity.
 * They are embedded in other objects that need a fragment of logic
 * but do not need the overhead of a Rule.
 */
@XMLClass
public class Script
{
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Languages supported for scripts and rules.
     * In theory you can use anything that BSF supports, but
     * constants are needed for a few languages where
     * the source might be processed.
     */
    public static final String LANG_BEANSHELL = "beanshell";
    public static final String LANG_JAVASCRIPT = "javascript";
    public static final String LANG_JAVA = "java";

    /**
     * Argument that when true in the script evaluation argument map
     * causes the script compiler to NOT reuse a previous compilation
     * artifcat.  In essence, it won't set _complation.
     *
     * This is a temporary solution to 19100 so we can reliably use
     * scripts in role assignment rules (IdentitySelector) when the
     * scripts are cached in the CorrelationRole and must be accessed
     * concurrently by multiple threads.  Eventually if we decide
     * to solve 23671 we won't need this option.
     * 
     * Give it an iiq prefix since this has to live in the same map
     * as user defined script args.
     */
    public static final String ARG_NO_COMPILATION_CACHE = "iiqNoCompilationCache";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The language the script is written in.
     * The default is LANG_BEANSHELL;
     * Unlike Rule allow this to stay null so the XML is not cluttered.
     */
    private String _language;

    /**
     * The source code of the script.
     */
    private String _source;

    /**
     * List of rule objects whose source is to be included when
     * compiling this script.
     */
    private List<Rule> _includes;

    /**
     * Handle to a compiled representation created by the RuleRunner.
     */
    Object _compilation;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Script() {
    }

    public Script(String src) {
        _source = src;
    }

    public Script(Script src) {
        _language = src._language;
        _source = src._source;
    }

    @XMLProperty
    public String getLanguage() {
        return _language;
    }

    public void setLanguage(String language)
    {
        _language = language;
    }
    
    // !! TODO: Figure out a way to let the script be in element content
    // of the <Script> element.  Sort of a cross between 
    // SerializationMode.ELEMENT and SerializationMode.UNQUALIFIED

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getSource()
    {
        return _source;
    }

    public void setSource(String source)
    {
        _source = source;
    }

    /**
     * This a transient runtime field, it is not serialized.
     */
    public Object getCompilation() {
        return _compilation;
    }

    public void setCompilation(Object o) {
        _compilation = o;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Rule> getIncludes() {
        return _includes;
    }

    public void setIncludes(List<Rule> rules) {
        _includes = rules;
    }

}
