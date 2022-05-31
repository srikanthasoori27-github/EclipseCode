/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ExceptionCleaner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ScriptletEvaluator {

    private static Log log = LogFactory.getLog(ScriptletEvaluator.class);

    /**
     * Interface to aid in evaluating scriptlets.
     */
    public interface ScriptletContext {
        List<Object> getCallLibraries(Scriptlet s);
        Object[] getCallParamaters(Scriptlet s);
        Map getScriptParameters(Scriptlet s);
        List<Rule> getScriptLibraries(Scriptlet s);
        List<Rule> getRuleLibraries(Scriptlet s);
        Map getRuleArgs(Scriptlet s);
        Object getReference(Scriptlet s);
        Object getString(Scriptlet s);

    }

    /**
     * Evaluate a given Scriptlet
     * @param ctx - SailPointContext used for rule/script evaluation
     * @param scriptCtx - ScriptletContext used to get scriptlet params
     * @param src Scriptlet
     * @return
     * @throws GeneralException
     */
    public static Object evalSource(SailPointContext ctx, ScriptletContext scriptCtx, Scriptlet src)
            throws GeneralException {

        Object result = null;

        if (src != null) {
            if (!Util.isNullOrEmpty(src.getString())) {
                result = scriptCtx.getString(src);
            }
            else {
                if (!Util.isNullOrEmpty(src.getCall())) {
                    result = doCall(scriptCtx.getCallLibraries(src), src.getCall(), scriptCtx.getCallParamaters(src));
                }
                else {
                    if (!Util.isNullOrEmpty(src.getRule())) {
                        result = doRule(ctx, src.getRule(), scriptCtx.getRuleArgs(src), scriptCtx.getRuleLibraries(src));
                    }
                    else {
                        if (src.getScript() != null) {
                            result = doScript(ctx, src.getScript(), scriptCtx.getScriptParameters(src), scriptCtx.getScriptLibraries(src));
                        }
                        else {
                            if (!Util.isNullOrEmpty(src.getReference())) {
                                result = scriptCtx.getReference(src);
                            }
                        }
                    }
                }
            }

            // universal negation hack
            //TODO: May want to generalize this -rap
            if (src.isNot())
                result = new Boolean(!Workflower.isTruthy(result));
        }
        else {
            log.warn("Scriptlet object is null.");
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Calls
    //
    //////////////////////////////////////////////////////////////////////

    public static class UnresolvableException extends GeneralException {
        UnresolvableException(String msg) {
            super(msg);
        }
    }


    /**
     * Little struct to represent both the target object
     * (handler or library) and the resolved method.
     */
    public static class ResolvedCall {
        Object object;
        Method method;
    }

    /**
     * Source evaluator that calls a WorkflowHandler method.
     */
    private static Object doCall(List<Object> libraries, String op, Object... params)
            throws GeneralException {

        Object result = null;

        // look for a method in the WorkflowHandler or libraries
        ResolvedCall resolved = resolveCall(libraries, op, params);
        if (resolved == null) {
            if (log.isErrorEnabled())
                log.error("Unsupported call: " + op);

            throw new UnresolvableException("Unsupported call: " + op);
        }
        else {
            try {
                result = resolved.method.invoke(resolved.object, params);
            }
            catch (IllegalAccessException e) {
                // found but protected, ignore
            }
            catch (InvocationTargetException e) {
                // method was there but it threw, assume it needs to
                // be propagated
                Throwable t = e.getTargetException();
                log.error("Step call threw an exception:");
                log.error(Util.stackToString(t));

                if (t instanceof GeneralException)
                    throw (GeneralException)t;
                else
                    throw new GeneralException(t);
            }
        }

        return result;
    }

    /**
     * Helper for doCall, look for the referenced method in the
     * list of libraries. These will be evaluated in order
     * and return the first instance found
     */
    private static ScriptletEvaluator.ResolvedCall resolveCall(List<Object> libraries, String name, Object... params) {

        ScriptletEvaluator.ResolvedCall resolved = null;

        List<Class> paramCls = null;
        if (params != null) {
            //Get param classes
            paramCls = new ArrayList<>();
            for (Object o : Util.safeIterable(Arrays.asList(params))) {
                paramCls.add(o.getClass());
            }
        }

        for (Object lib : Util.safeIterable(libraries)) {
            resolved = ScriptletEvaluator.getResolvedCall(lib, name, paramCls.toArray(new Class[paramCls.size()]));
            if (resolved != null) {
                break;
            }
        }

        return resolved;
    }

    /**
     * Helper for doCall, look for a method by name in a handler
     * or library class.
     */
    public static ResolvedCall getResolvedCall(Object obj, String name, Class<?>... parameterTypes) {

        ResolvedCall resolved = null;
        try {
            Class cls = obj.getClass();
            Method method = cls.getMethod(name, parameterTypes);
            if (method != null) {
                resolved = new ResolvedCall();
                resolved.method = method;
                // if this is static this should be null but
                // it doesn't matter according to the javadocs for Method
                resolved.object = obj;
            }
            else {
            }
        }
        catch (NoSuchMethodException e) {
            // method not found, ignore
        }
        catch (IllegalArgumentException e) {
            // must have ben an error matching the signature, ignore
        }

        return resolved;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rules and Scripts
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Step action handler that calls an external rule.
     * Has to be accessible by WorkflowContext.resolveVariable.
     */
    private static Object doRule(SailPointContext context, String ruleName, Map ruleArgs, List<Rule> ruleLibs)
            throws GeneralException {

        Object result = null;

        Rule rule = context.getObjectByName(Rule.class, ruleName);
        if (rule == null) {
            if (log.isErrorEnabled())
                log.error("Unknown rule name: " + ruleName);
        }
        else {

            try {
                result = context.runRule(rule, ruleArgs, ruleLibs);
            }
            catch (GeneralException e) {
                // try to soften the typical BeanShell exception mess
                String msg = "Exception evaluating rule: " + ruleName +
                        "  \n" + ExceptionCleaner.getString(e);
                throw new GeneralException(msg);
            }
        }

        return result;
    }

    /**
     * Step action handler that evaluates a script.
     * Has to be accessible by WorkflowContext.resolveVariable.
     */
    private static Object doScript(SailPointContext context, Script script, Map scriptArgs, List<Rule> ruleLibs)
            throws GeneralException {

        Object result = null;

        if (script == null || Util.isNullOrEmpty(script.getSource()))
            log.warn("Script with no source");
        else {
            try {
                result = context.runScript(script, scriptArgs, ruleLibs);
            }
            catch (GeneralException e) {
                // try to soften the typical BeanShell exception mess
                String msg = ExceptionCleaner.getString(e);
                throw new GeneralException(msg, e);
            }
        }

        return result;
    }
}
