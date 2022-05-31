/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * This is our only implementation of RuleRunner.
 * In retrospect injecting this with Spring was way overkill and
 * it makes some things awkward like accessing the SailPointContext.
 * This should really have been more like a sailpoint.api utility class
 * maybe with injected runners for specicic languages, but there should
 * be an outer runner that handles the language selection, resolving
 * the rule libraries etc, maybe tracing etc.
 *
 * In 5.0 we added the ability for the script and rule source to 
 * contain #include directives:
 *
 *      //#include MyRuleLibrary
 *
 * It is logical to process those here but we need a SailPointContext to
 * resolve them and that isn't passed in directly through the 
 * RuleRunner interface.  We'll rely on the convention that an input
 * variable named "context" is always set which is okay but it would
 * be more consistent if we could just give this a context in the
 * calls to runRule/runScript, but this violates the RuleRunner 
 * interface.
 *
 * jsl
 * 
 * In 7.0 merged this with BSFManagerFactory since with the introduction
 * of the BSFManagerWrapper they really aren't separable and we don't
 * need to mess with Spring.
 * 
 * The original implementation used a single global Map to 
 * maintain ManagerStatus for the reuse counter, this was not thread
 * safe and could lead to counter problems under load.  In particular, 
 * GenericKeyedObjectPool would call makeObject followed by validateObject
 * and when the later was called some other thread put status up to 100
 * which caused GKOP to throw.

 * Relevant comments from Dan from BSFManagerFactory:
 *
 * A Factory classs the is plugged into the ManagerPool
 * and is called to manage BSFManagers.  Using the KeyedPoolFactory
 * here since we want to pool a Manager for each Rule. The
 * only check we do here is to make sure that we don't use
 * a manager too many times.
 * 
 * Per rule manager?
 * Bsh caches a lot of bean/class information in a global namespace
 * and Rules having the same named attributes with different types
 * causes problems with the engine.
 * 
 * Limited Reuse?
 * Because of the caching of data, the Manager will continue
 * to consume memory because of the underlying BSH engine.
 * Recycle occationally, these settings can be tweeked in 
 * our spring properties file.  
 *
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.ConnectorClassLoader;
import sailpoint.object.JavaRuleContext;
import sailpoint.object.JavaRuleExecutor;
import sailpoint.object.LockInfo;
import sailpoint.object.Rule;
import sailpoint.object.RuleRunner;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ScriptPreParser;
import sailpoint.tools.Util;

/**
 * An implementation of a RuleRunner that uses BSF to execute rules.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a> 
 *
 */
public class BSFRuleRunner extends BaseKeyedPooledObjectFactory<Object, Object> implements RuleRunner
{
    private static Log _log = LogFactory.getLog(BSFRuleRunner.class);

    /**
     * Pool of BSFManagers wrapped in BSFManagerWrappers.
     */
    private GenericKeyedObjectPool _pool;

    /**
     * The maximum number of times a BSFManager can be reused before releasing.
     * This has been 1000 in past relases but that wasn't really accurate since
     * we were maintaining a count for all BSFManagers in use by all threads, 
     * not per-BSFManager uses.  Continuing with 1000 but may want to change
     * that now that we're keepign true per-BSFManager counts.
     *
     * This can be configured in Spring.
     */
    private int _maxPoolReuse = 1000;

    /**
     * Create a new BSFRuleRunner and initialize its pool
     * @param poolConfig the config to use for the pool.  This is specified
     *                   using ruleRunnerPoolConfig in iiq.properties.
     */
    public BSFRuleRunner(GenericKeyedObjectPoolConfig poolConfig) {

        _pool = new GenericKeyedObjectPool(this, poolConfig);

        // Why bother returning if we knows it's done?  And we don't want
        // to keep unneeded objects around in the pool because prevent
        //  garbage  collection from working as well as possible
        _pool.setTestOnReturn(true);

        // now that a change to the pluginCache can cause an object
        // to be considered invalid, we need to check on borrow
        _pool.setTestOnBorrow(true);
    }

    public int getMaxPoolReuse() {
        return _maxPoolReuse;
    }

    public void setMaxPoolReuse(int reuse) {
        _maxPoolReuse = reuse;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rule Execution
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.server.RuleRunner#run(sailpoint.object.Rule)
     */
    public Object runRule(Rule rule, Map<String,Object> params)
        throws GeneralException {

        return runRule(rule, params, null);
    }

    /*
     * Run a rule given the passed in params and the rule libraries.
     * The rule libraries will be evaluated before the Rule's referenced
     * libraries so the Rules library functions will over-ride any
     * of the passed in library functions.
     */ 
    public Object runRule(Rule rule, Map<String,Object> params, List<Rule> ruleLibraries)
        throws GeneralException {
        // jsl - some dummy rules have no source so catch those early
        // so BSFManager doesn't barf
        if (rule == null || Util.isNullOrEmpty(rule.getSource())) {
            return null;
        }

        // hack: experimental support for Java rules
        if (Script.LANG_JAVA.equals(rule.getLanguage()))
            return runJavaRule(rule, params, ruleLibraries);

        if (_log.isDebugEnabled()) {
            _log.debug("*** Running rule: " + rule.getName() + " ***");
        }

        BSFManagerWrapper wrapper = getPooledManager(rule);
        BSFManager manager = wrapper.getManager();
        Object returnVal = null;
        try {
            // add rule context for lock tracking
            // normal rules have a name, but catch the case
            // where they are creating a rule in memory then running it
            String lcontext = rule.getName();
            if (lcontext == null)
                lcontext = "*unnamed rule*";
            sailpoint.api.LockTracker.setRuleContext(lcontext);
            
            bindAttributes(manager, params);

            // First load any rule libraries passed in
            if ( Util.size(ruleLibraries) > 0 ) {
                for ( Rule ruleLibrary : ruleLibraries ) {
                    eval(manager, ruleLibrary, params);
                }
            }

            // Preload any referenced rules.
            List<Rule> referenced = rule.getReferencedRules();
            if (null != referenced) {
                for (Rule ref : referenced) {
                    eval(manager, ref, params);
                }
            }

            // Load source includes
            // note that library rules cannot themselves have includes
            // or even referencedRules, may want to fix that
            List<Rule> includes = getIncludes(params, rule.getSource());
            if (includes != null) {
                for (Rule ref : includes)
                    eval(manager, ref, params);
            }

            returnVal = eval(manager, rule, params);          
        } 
        catch (BSFException bsfe) {
            throw new GeneralException(bsfe);
        } 
        catch (Throwable t) {
            // will get things like java.security.PrivilegedActionException
            // when referencing unbound things
            throw new GeneralException(t);
        } 
        finally {
            sailpoint.api.LockTracker.setRuleContext(null);
            unbindAttributes(manager, params);
            releasePooledManager(rule, wrapper);
        }      
        return returnVal;
    }

    private Object eval(BSFManager manager, Rule rule, Map<String,Object> params) throws BSFException {
        Object retval = null;

        if (manager != null && rule != null) {
            if (_log.isDebugEnabled()) {
                _log.debug("Evaluating rule source: " + rule.getName());
                _log.debug(rule.getSource());
            }

            String source = getPreParsedSource(rule.getSource(), params);
            if (!Util.isNullOrEmpty(source)) {
                retval = manager.eval(rule.getLanguage(), rule.getName(), 0, 0, source);
            }
            else {
                _log.debug("The rule source has not content. Nothing to evaluate.");
            }
        }
        else {
            _log.debug("The manager and rule objects must have values. Manager is null:  " + (manager == null) + "  Rule is null: " + (rule == null));
        }

        return retval;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Script Execution
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.server.RuleRunner#runScript
     */
    public Object runScript(Script script, Map<String,Object> params)
        throws GeneralException {

        return runScript(script, params, null);
    }

    /**
     * Run the script. This method adds any passed in rule libraries into
     * the context of the script.
     */ 
    public Object runScript(Script script, Map<String,Object> params, List<Rule> libraries)
        throws GeneralException {
        Object returnVal = null;

        if (script != null) {
            String source = getPreParsedSource(script.getSource(), params);
            if (!Util.isNullOrEmpty(source)) {

                if (_log.isDebugEnabled()) {
                    _log.debug("*** Evaluating script ***");
                }

                // Scripts are anonymous so we have to cache the manager
                // directly on the object.
                // bug 19100, disable cache when scripts are from cached objects
                // that are not thread safe, hopefully this is temporary, see 23671
                BSFManager manager = null;
                if (isNoCompilationCache(params)) {
                    manager = createBSFManager();
                } else {
                    manager = (BSFManager) script.getCompilation();
                    if (manager == null) {
                        manager = createBSFManager();
                        script.setCompilation(manager);
                    } else {
                        if (isManagerStale(manager)) {
                            // replace with a new manager because manager is stale
                            manager.terminate();
                            manager.setClassLoader(null);
                            manager = createBSFManager();
                            script.setCompilation(manager);
                        }
                    }
                }

                try {
                    bindAttributes(manager, params);
                    String lang = script.getLanguage();
                    if (lang == null) lang = Script.LANG_BEANSHELL;
                    // do we need a name?
                    String name = "script";

                    // load external libraries
                    if (Util.size(libraries) > 0) {
                        for (Rule ref : libraries) {
                            eval(manager, ref, params);
                        }
                    }

                    // load explicit includes
                    List<Rule> includes = script.getIncludes();
                    if (includes != null) {
                        for (Rule ref : includes) {
                            eval(manager, ref, params);
                        }
                    }

                    // Load source includes
                    // note that library rules cannot themselves have includes
                    // or even referencedRules, may want to fix that
                    includes = getIncludes(params, script.getSource());
                    if (includes != null) {
                        for (Rule rule : includes)
                            eval(manager, rule, params);
                    }

                    if (_log.isDebugEnabled()) {
                        _log.debug("Evaluating script source:");
                        _log.debug(source);
                    }

                    returnVal = manager.eval(lang, name, 0, 0, source);
                } catch (BSFException bsfe) {
                    throw new GeneralException(bsfe);
                } catch (Throwable t) {
                    throw new GeneralException(t);
                } finally {
                    unbindAttributes(manager, params);
                }
            } else {
                _log.warn("Script source is empty. Nothing to execute.");
            }
        }
        else {
            _log.warn("Script object is null. Nothing to execute.");
        }

        return returnVal;
    }

    /**
     * Check to see if a special script argument is passed to disable compilation caching.
     * I'd rather this be stored on the Script than passed in the arg map to avoid
     * another map hit but this is hopefully temporary.
     */
    private boolean isNoCompilationCache(Map<String,Object> params) {

        return (params != null && Util.otob(params.get(Script.ARG_NO_COMPILATION_CACHE)));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rule/Script Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Bind a set of attributes into a manager.
     */
    private void bindAttributes(BSFManager manager, Map<String,Object> params)
        throws BSFException {
        if (null != params) {
            for (Map.Entry<String,Object> param : params.entrySet()) {
                String name = param.getKey();
                if (name != null) {
                    Object value = param.getValue();

                    // Before 3.0 we would filter null values but this becomes
                    // important if we want to convey the nullness of something,
                    // like before/after values for attribute listener rules.
                    Class type = (null != value) ? value.getClass() : Object.class;

                    if( !name.contains(".") ) {
                        manager.declareBean(name, value, type);
                    }
                    else if( _log.isDebugEnabled() ) {
                        _log.debug("Not declaring param (" + name + ") as a bean because it contains a dot '.' in the name.");
                    }
                }
            }
        }
    }

    /**
     * Remove a set of bindings from a manager.
     * This is important so the manger can be pooled for reuse without
     * leaving stale bindings behind for the next use.
     */
    private void unbindAttributes(BSFManager manager, 
                                  Map<String,Object> params ) {
        if (null != params) {
            try {
                for (Map.Entry<String,Object> param : params.entrySet()) {
                    String name = param.getKey();
                    if (name != null)
                        manager.undeclareBean(name);
                }
            } catch(BSFException e ){
                _log.warn("Problem unbinding rule attribute:" + e.toString());
            }
        }
    }

    /**
     * Preprocess the Beahshell source before compiling.
     * This looks like it is used for scripts that are used with a "model"
     * doing reference substitution.
     */
    private String getPreParsedSource(String source, Map<String, Object> params) {
        // Simple test to avoid parsing if there is nothing to convert
        source = StringUtils.trimToEmpty(source);

        if (Util.isNullOrEmpty(source)){
            return null;
        }

        if (source.contains("$(")) {
            // Use MODEL_BASE_PATH if it's available, otherwise use null and assume the source
            // has the fully qualified basePath in it.
            String basePath = null;
            if(params != null) {
                basePath = (String)params.get(Rule.MODEL_BASE_PATH);
            }
            // BSFRuleRunner is a singleton, so we have to use
            // discrete instances of ScriptPreParser
            ScriptPreParser preparser = new ScriptPreParser();
            source = preparser.preParse(basePath, source);
        }

        return source;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Inline include directives
    //
    //////////////////////////////////////////////////////////////////////

    public static final String INCLUDE_TOKEN = "#include ";

    /**
     * Look for include directives in script or rule source and
     * resolve them to Rule objects.    
     * 
     * Includes must be written in a // comment on a new line.
     * We could make the comment parser smarter to allow includes
     * within "slashdot" comments but feels better to be more restrictive
     * to avoid unintential references in long comments.
     *
     * A "phase" integer maintains the accumulated parse state:
     *      
     *    0 - waiting for a newline
     *    1 - newline found, waiting for the first /
     *    2 - first / found, waiting for the second
     *    3 - // found, waiting for #include
     */
    private List<Rule> getIncludes(Map<String,Object> params, String source)
        throws GeneralException {
        
        List<Rule> rules = null;
        source = StringUtils.trimToEmpty(source);

        // don't bother if we know it's not there
        if (!Util.isNullOrEmpty(source) && source.indexOf(INCLUDE_TOKEN) > 0) {
            List<String> refs = new ArrayList();
            int max = source.length();
            int psn = 0;
            int phase = 0;
            while (psn < max) {
                char ch = source.charAt(psn);
                if (ch == '\n') {
                    phase = 1;
                }
                else if (phase == 1) {
                    if (ch == '/')
                        phase = 2;
                    else if (!Character.isWhitespace(ch)) {
                        // comment not at start of line
                        phase = 0;
                    }
                }
                else if (phase == 2) {
                    if (ch == '/') 
                        phase = 3;
                    else {
                        // second slash must be adjacent
                        phase = 0;
                    }
                }
                else if (phase == 3) {
                    if (!source.startsWith(INCLUDE_TOKEN, psn)) {
                        // allow space between // and #
                        if (!Character.isWhitespace(ch))
                            phase = 0;
                    }
                    else {
                        // whatever we find here, phase starts over
                        phase = 0;
                        psn += INCLUDE_TOKEN.length();
                        int start = 0;
                        boolean quoted = false;
                        boolean stop = false;
                        while (!stop) {
                            boolean consume = false;
                            if (psn >= max) {
                                consume = true;
                                stop = true;
                            }
                            else {
                                ch = source.charAt(psn);
                                if (ch == '\n') {
                                    consume = true;
                                    stop = true;
                                    phase = 1;
                                }
                                else if (quoted) {
                                    if (ch == '"') {
                                        consume = true;
                                        quoted = false;
                                    }
                                    else if (start == 0)
                                        start = psn;
                                }
                                else if (ch == '"') {
                                    quoted = true;
                                    consume = true;
                                }
                                else if (ch == ',')  {
                                    consume = true;
                                }
                                else if (start == 0 && !Character.isWhitespace(ch)) {
                                    start = psn;
                                }
                            }

                            if (consume && start > 0) {
                                String token = source.substring(start, psn);
                                token = token.trim();
                                if (token.length() > 0)
                                    refs.add(token);
                                start = 0;
                            }

                            if (psn < max) psn++;
                        }
                        // we will already have incremented past the last char
                        // decrement so the increment at the end of the outer
                        // loop won't miss it
                        psn--;
                    }
                }

                psn++;
            }

            if (refs.size() > 0) {
                // sigh, have to get it from the thread-local since RuleRunner
                // doesn't have an interface to pass it
                // !! actually we should be able to get it from the rule arg map
                rules = new ArrayList<Rule>();
                SailPointContext con = SailPointFactory.getCurrentContext();
                for (String ref : refs) {
                    _log.debug("Rule reference: " + ref);
                    Rule rule = con.getObjectByName(Rule.class, ref);
                    if (rule != null)
                        rules.add(rule);
                    else
                        _log.error("Invalid rule name: " + ref);
                }
            }
        }

        return rules;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // BSFManager Pool
    //
    // This used to be optional and Spring configurable, but since we always
    // want it I merged the two to simplify things. - jsl
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Wrapper around BSFManager so we can keep a usage count.
     */
    public class BSFManagerWrapper {

        BSFManager _manager;
        int _uses;

        public BSFManagerWrapper(BSFManager m) {
            _manager = m;
            _uses = 1;
        }

        public BSFManager getManager() {
            return _manager;
        }

        public void incrementUses() {
            _uses++;
        }

        public int getUses() {
            return _uses;
        }

        private boolean isFinished() {
            // todo: might want zero to mean reuse forever?
            boolean finished = (_uses > _maxPoolReuse);
            if (!finished) {
                finished = isManagerStale(_manager);

                if (finished) {
                    if (_log.isDebugEnabled()) {
                        _log.debug("BSFManagerWrapper is finished because plugin changes detected");
                    }
                }
            }
            else {
                if (_log.isDebugEnabled()) {
                    _log.debug("BSFManagerWrapper is finished because too many uses");
                }

            }
            return finished;
        }
    }

    /**  
     * Get the BSFManager from the pool. There is a significant performance 
     * increase by pooling BSFManagers instead of creating a new Manager 
     * each time a rule is executed.
     */
    private BSFManagerWrapper getPooledManager(Rule rule) throws GeneralException {
        BSFManagerWrapper wrapper = null;
        try {
            String poolId = getPoolKey(rule);
            wrapper = (BSFManagerWrapper)_pool.borrowObject(poolId);
        }
        catch(Exception e) {
            throw new GeneralException("Error getting BSFManager from pool.", e);
        }
        return wrapper;
    }

    private void releasePooledManager(Rule rule, BSFManagerWrapper wrapper) {
        try {
            String key = getPoolKey(rule);
            _pool.returnObject(key, wrapper);
        }
        catch(Exception e) {
            _log.warn("Exception returning manager to pool." + e);
        }
    }
    
    /**
     * Return the key to use in the BSFManager pool.
     * This is normally the rule name.  If this is an anonymous rule it
     * uses the hash code, I'm not sure if that can happen but it's been that
     * way for a long time. - jsl
     */
    private String getPoolKey(Rule rule) {
        String key = rule.getName();
        if ( key == null ) {
            key = rule.getId();
        }
        if ( key == null ) {
            _log.warn("Both rule Name and id are null going postal and using the hashCode of the source.");
            // If no source we wouldn't be here...
            String source = rule.getSource();
            int hashCode = source.hashCode();
            key = String.valueOf(hashCode);
        }
        return key;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // BaseKeyedPoolableObjectFactory Implementation
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public synchronized boolean validateObject(Object key, PooledObject<Object> obj) {
        BSFManagerWrapper w = (BSFManagerWrapper)obj.getObject();
        return !w.isFinished();
    }

    @Override
    public synchronized void destroyObject(Object key, PooledObject<Object> obj) throws Exception {
        if (obj != null) {
            BSFManagerWrapper wrapper = (BSFManagerWrapper)obj.getObject();
            BSFManager manager = (BSFManager)wrapper.getManager();
            manager.terminate();
            manager.setClassLoader(null);
            if (_log.isDebugEnabled()) {
                _log.debug("Releasing BSFManager '" + key.toString() + "' after maximum reuses or plugin changes detected.");
            }
        }
    }

    @Override
    public void activateObject(Object key, PooledObject<Object> obj) {
        BSFManagerWrapper wrapper = (BSFManagerWrapper)obj.getObject();
        wrapper.incrementUses();
    }

    @Override
    public Object create(Object key) {
        BSFManager manager = createBSFManager();
        BSFManagerWrapper wrapper = new BSFManagerWrapper(manager);
        if (_log.isDebugEnabled()) {
            _log.debug("New BSFManager created: " + key.toString());
        }
        return wrapper;
    }

    @Override
    public PooledObject<Object> wrap(Object obj) {
        return new DefaultPooledObject<Object>(obj);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Java Rules
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run a Rule containing Java.
     * This is still experimental but works reasonably well.
     * We do not support rule libraries here, rule references, or
     * includes.  Though we could do something similar causing a set of 
     * classes to be loaded.
     */
    public Object runJavaRule(Rule rule, Map<String,Object> params, List<Rule> ruleLibraries)
        throws GeneralException {

        Object result = null;
        try {
            // this is actually passed in the params too, should take that out?
            // what about "log" should factor that out too...
            SailPointContext con = SailPointFactory.getCurrentContext();

            Class cls = DynamicLoader.classForName(con, rule.getName());
            // TODO: need more flexibility in declaring interfaces?
            JavaRuleExecutor instance = (JavaRuleExecutor)cls.newInstance();

            JavaRuleContext jrc = new JavaRuleContext(con, params);

            result = instance.execute(jrc);
        }
        catch (GeneralException e) {
            throw e;
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }

        return result;
    }

    /**
     * Convenience method to properly create a BSFManager
     * @return the new properly created BSFManager
     */
    private BSFManager createBSFManager() {
        BSFManager manager = new BSFManager();

        ClassLoader current_cl = Thread.currentThread().getContextClassLoader();
        if( current_cl != null && current_cl instanceof ConnectorClassLoader) {
            // Special case: The code within a connector is invoking a rule.
            // We will just use the connector classLoader.
            manager.setClassLoader(current_cl);
        }
        else {
            // Set a BSFClassLoader on the new manager to enable using classes from plugins.
            // The BSFClassLoader has the current version of the plugin cache stored in it --
            // so that we can tell (in isManagerStale())
            // if the BSFManager needs to be discarded because the version changed since
            // constructed.
            int pluginCacheVersion = Environment.getEnvironment().getPluginsCache().getVersion();
            ClassLoader bsf_cl = new  BSFClassLoader(Thread.currentThread().getContextClassLoader(), pluginCacheVersion);
            manager.setClassLoader(bsf_cl);
        }

        return manager;
    }

    /**
     * Check if the given BSFManager is using a BSFClassLoader which is now stale
     * because the pluginsCache has changed.
     * @param manager the BSFManager to check
     * @return true if it is stale, otherwise false
     */
    private boolean isManagerStale(BSFManager manager) {
        boolean stale = false;

        if (manager.getClassLoader() instanceof BSFClassLoader) {
            // ask the classloader if it is now stale
            BSFClassLoader cl = (BSFClassLoader)manager.getClassLoader();
            stale = cl.isStale();
        }
        return stale;
    }
}
