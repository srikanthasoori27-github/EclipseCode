/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.environmentMonitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.MonitoringStatistic;
import sailpoint.object.Rule;
import sailpoint.object.Scriptlet;
import sailpoint.server.ScriptletEvaluator;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MonitoringScriptletContext implements ScriptletEvaluator.ScriptletContext {

    private static Log log = LogFactory.getLog(MonitoringScriptletContext.class);

    MonitoringStatistic _statistic;
    SailPointContext _context;

    public MonitoringScriptletContext(MonitoringStatistic stat, SailPointContext ctx) {
        _statistic = stat;
        _context = ctx;
    }

    private static final String DEFAULT_CALL_LIB = "sailpoint.environmentMonitoring.MonitoringLibrary";


    @Override
    public List<Object> getCallLibraries(Scriptlet s) {


        List<Object> libraries = new ArrayList<>();
        List<String> libNames = new ArrayList<String>();
        libNames.add(DEFAULT_CALL_LIB);

        List<String> customLibs = Util.otol(_statistic.getAttribute(MonitoringStatistic.ATTR_CALL_LIBS));
        if (!Util.isEmpty(customLibs)) {
            libNames.addAll(customLibs);
        }

        for (String libname : Util.safeIterable(libNames)) {
            try {
                Class cls = Class.forName(libname);
                Object lib = cls.newInstance();
                libraries.add(lib);
            } catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Unable to instantiate library: " + libname, t);
            }
        }

        return libraries;
    }

    @Override
    public Object[] getCallParamaters(Scriptlet s) {
        return new Object[] {this};
    }

    @Override
    public Map getScriptParameters(Scriptlet s) {
        return _statistic.getAttributes();
    }

    @Override
    public List<Rule> getScriptLibraries(Scriptlet s) {
        return null;
    }

    @Override
    public List<Rule> getRuleLibraries(Scriptlet s) {

        List<Rule> rules = new ArrayList<>();
        List<String> ruleLibs = Util.otol(_statistic.getAttribute(MonitoringStatistic.ATTR_RULE_LIBS));

        for (String ruleName : Util.safeIterable(ruleLibs)) {
            try {
                Rule r = _context.getObjectByName(Rule.class, ruleName);
                if (r != null) {
                    rules.add(r);
                }
            } catch (Throwable t) {
                log.warn("Unable to get rule[" + ruleName + "]" + t);
            }
        }

        return rules;
    }

    @Override
    public Map getRuleArgs(Scriptlet s) {
        return _statistic.getAttributes();
    }

    @Override
    public Object getReference(Scriptlet s) {
        //TODO: Reference shouldn't be used here -rap
        return _statistic.getAttribute(s.getReference());
    }

    @Override
    public Object getString(Scriptlet s) {
        return s.getString();
    }


    public MonitoringStatistic getStatistic() {
        return _statistic;
    }

    public SailPointContext getContext() {
        return _context;
    }


}
