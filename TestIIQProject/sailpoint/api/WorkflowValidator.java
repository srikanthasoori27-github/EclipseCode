/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A utility class that atttempts to find errors in workflow definitons.
 * 
 * Author: Jeff
 *
 * The primary goal here is to catch Beanshell syntax errors without
 * having to actually run the workflow.  Once the Beanshell compiles,
 * we can't do much about logic errors but it's better than nothing.
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Arg;
import sailpoint.object.Workflow.Step;
import sailpoint.object.Workflow.Transition;
import sailpoint.object.Workflow.Variable;
import sailpoint.object.WorkItemConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.VariableExpander;

public class WorkflowValidator {

    SailPointContext _context;
    List _errors;
    Workflow _workflow;
    Map<String,Object> _variables;
    boolean _trace;

    public WorkflowValidator(SailPointContext con) {
        _context = con;
    }

    public void setTrace(boolean b) {
        _trace = b;
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    /**
     * Validate a Workfow object.
     * 
     * Top level fields that could be validated:
     *
     *    type - probably not, these are extensible
     *    template - no
     *    currentStep - runtime only
     *    handler - valid class path
     *    workItemRenderer - valid JSF path
     *    complete - runtime only
     *    resultVarialbes - should only reference declared variables
     *    variables - the Map is supposed to be runtime only
     */
    public List<String> validate(Workflow wf, Map<String,Object> variables) 
        throws GeneralException {

        resetErrors();
        _workflow = wf;
        _variables = variables;

        if (_trace)
            println("Workflow: " + wf.getName());

        validateWorkItemConfig("", wf.getWorkItemConfig());

        List<Variable> vars = wf.getVariableDefinitions();
        if (vars != null) {
            for (Variable var : vars)
                validateVariable(var);
        }

        List<Step> steps = wf.getSteps();
        if (steps != null) {
            for (Step step : steps)
                validateStep(step);
        }

        return _errors;
    }

    /**
     * Validate a rule.
     * We need similar exception analysis and it isn't worth factoring out
     * into another utility class.
     */
    public List<String> validate(Rule rule, Map<String,Object> variables)
        throws GeneralException {

        resetErrors();
        if (rule != null) {
            try {
                _context.runRule(rule, variables);
            }
            catch (Throwable t) {
                analyzeException("Rule: " + rule.getName(), t);
            }
        }
        return _errors;
    }

    public void resetErrors() {
        _errors = new ArrayList();
    }

    public List<String> getErrors() {
        return _errors;
    }

    public void addError(String path, String error) {

        _errors.add(path);
        _errors.add("--> " + error);
    }

    public void validateWorkItemConfig(String context, WorkItemConfig config)
        throws GeneralException {

        // TODO: most things are hard refs so they
        // self-validate, could check the various 
        // reminder and escalation numbers to see if they   
        // make sense
    }

    public void validateVariable(Variable var) 
        throws GeneralException {

        String path = "Variable:" + var.getName();
        if (_trace)
            println(path);

        validateScriptlet(path, var.getInitializer(), Scriptlet.METHOD_STRING);
        validateScript(path, var.getScript());
    }

    public void validateStep(Step step) 
        throws GeneralException {

        // TODO: resutlVariable 

        String path = "Step:" + step.getName();
        if (_trace)
            println(path);

        String path2 = path += "/action";
        validateScriptlet(path2, step.getAction(), Scriptlet.METHOD_CALL);
        validateScript(path2, step.getScript());

        Approval app = step.getApproval();
        if (app != null)
            validateApproval(path, app, 0, 0);

        List<Arg> args = step.getArgs();
        if (args != null) {
            for (Arg arg : args)    
                validateArg(path, arg);
        }

        List<Transition> trans = step.getTransitions();
        if (trans != null) {
            for (Transition tran : trans)
                validateTransition(path, tran);
        }
    }

    public void validateArg(String path, Arg arg) 
        throws GeneralException {

        path += "/Arg:" + arg.getName();
        if (_trace)
            println(path);

        validateScriptlet(path, arg.getValue(), Scriptlet.METHOD_STRING);
        validateScript(path, arg.getScript());
    }

    public void validateTransition(String path, Transition trans) 
        throws GeneralException {
        
        path += "/Transition:" + trans.getTo();
        if (_trace)
            println(path);

        String path2 = path += "/to";
        validateScriptlet(path2, trans.getTo(), Scriptlet.METHOD_STRING);

        path2 = path += "/when";
        validateScriptlet(path2, trans.getWhen(), Scriptlet.METHOD_SCRIPT);
        validateScript(path2, trans.getScript());
    }

    public void validateApproval(String path, Approval app, int level, int index) 
        throws GeneralException {
        
        String name = app.getName();
        if (name != null)
            path += "/Approval:" + name;
        else
            path += "/Approval:" + Util.itoa(level) + "," + Util.itoa(index);
        
        // TODO: mode, renderer
        if (_trace)
            println(path);

        validateWorkItemConfig(path, app.getWorkItemConfig());

        String path2 = path += "/send";
        validateVariableRefs(path2, app.getSend());

        path2 = path += "/return";
        validateVariableRefs(path2, app.getReturn());
        
        path2 = path += "/mode";
        validateScriptlet(path2, app.getMode(), Scriptlet.METHOD_STRING);
        validateScript(path2, app.getModeScript());

        path2 = path += "/owner";
        validateScriptlet(path2, app.getOwner(), Scriptlet.METHOD_STRING);
        validateScript(path2, app.getOwnerScript());

        path2 = path += "/validator";
        validateScriptlet(path2, app.getValidator(), Scriptlet.METHOD_SCRIPT);
        validateScript(path2, app.getValidatorScript());

        path2 = path += "/description";
        validateScriptlet(path2, app.getDescription(), Scriptlet.METHOD_STRING);

        path2 = path += "/afterScript";
        validateScript(path2, app.getAfterScript());

        path2 = path += "/interceptorScript";
        validateScript(path2, app.getInterceptorScript());

        List<Arg> args = app.getArgs();
        if (args != null) {
            for (Arg arg : args)    
                validateArg(path, arg);
        }

        List<Approval> children = app.getChildren();
        if (children != null) {
            int chindex = 0;
            for (Approval child : children) {
                validateApproval(path, child, level + 1, chindex);
                chindex++;
            }
        }
    }

    public void validateVariableRefs(String path, String refs)
        throws GeneralException {

        List<String> names = Util.csvToList(refs);
        if (names != null) {
            for (String name : names) {
                Variable var = getVariable(name);
                if (var == null)
                    addError(path, "Undeclared variable: " + name);
            }
        }
    }

    private Variable getVariable(String name) {

        Variable found = null;
        List<Variable> vars = _workflow.getVariableDefinitions();

        if (name != null && vars != null) {
            for (Variable var : vars) {
                if (name.equals(var.getName())) {
                    found = var;
                    break;
                }
            }
        }
        return found;
    }

    public void validateScriptlet(String path, String scriptlet, String defaultMethod) 
        throws GeneralException {

        path += "/Scriptlet";

        if (scriptlet != null) {
            if (_trace)
                println(path);

            VariableExpander.expand(scriptlet, new VariableExpander.PathMapResolver(_variables));
            
            Scriptlet src = new Scriptlet(scriptlet, defaultMethod);
            if (src.getCall() != null) {
                // TODO: use reflection on the handler to make sure
                // it's a valid method
            }
            else if (src.getRule() != null) {
                validateRule(path, src.getRule());
            }
            else if (src.getReference() != null) {
                validateVariableRefs(path, src.getReference());
            }
            else if (src.getScript() != null) {
                try {
                    _context.runScript(src.getScript(), _variables);
                }
                catch (Throwable t) {
                    analyzeException(path, t);
                }
            }
        }
    }

    public void validateScript(String path, Script script) 
        throws GeneralException {

        path += "/Script";
        if (script != null) {
            if (_trace)
                println(path);

            if (script != null) {
                try {
                    _context.runScript(script, _variables);
                }
                catch (Throwable t) {
                    analyzeException(path, t);
                }
            }
        }
    }

    public void validateRule(String path, String name) 
        throws GeneralException {

        if (name != null) {
            Rule rule = _context.getObjectByName(Rule.class, name);
            if (rule == null)
                addError(path, "Unknown rule: " + name);
            else {
                try {
                    _context.runRule(rule, _variables);
                }
                catch (Throwable t) {
                    analyzeException(path, t);
                }
            }
        }
    }
    
    /**
     * Typical Beanshell error looks like:
     * 
     * java.security.PrivilegedActionException: org.apache.bsf.BSFException: 
     * BeanShell script error: Sourced file: inline evaluation of: 
     * ``approvalObject.getType();'' : Attempt to resolve method: getType() 
     * on undefined variable or class name: approvalObject : at Line: 1 : 
     * in file: inline evaluation of: ``approvalObject.getType();'' : 
     * approvalObject .getType ( ) 
     *
     */
    public void analyzeException(String path, Throwable t) {

        // TODO: smarter about parsing the mess
        String msg = t.toString();

        // search up to the terminating quotes on the first
        // "inline evaluation of" section
        String token = "'' : ";
        int psn = msg.indexOf(token);
        if (psn > 0) {
            // then to the colon after "at Line: x :"
            msg = msg.substring(psn + token.length());
            token = "at Line:";
            psn = msg.indexOf(token);
            if (psn > 0) {
                psn = msg.indexOf(":", psn + token.length());
                if (psn > 0) 
                    msg = msg.substring(0, psn);
            }
        }

        addError(path, msg);
    }
    
}

