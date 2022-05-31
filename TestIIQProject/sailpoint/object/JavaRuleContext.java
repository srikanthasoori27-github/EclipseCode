/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Runtime information passed to an instance of JavaRuleExecutor
 *
 * Author: Jeff
 */

package sailpoint.object;

import java.util.Date;
import java.util.Map;

import sailpoint.api.SailPointContext;

public class JavaRuleContext {

    SailPointContext _context;
    Attributes<String,Object> _arguments;

    public JavaRuleContext(SailPointContext con, Map<String,Object> args) {
        _context = con;
        if (args instanceof Attributes)
            _arguments = (Attributes<String,Object>)args;
        else
            _arguments = new Attributes<String,Object>(args);
    }

    public SailPointContext getContext() {
        return _context;
    }

    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    // the usual pass throughs

    public String getString(String name) {
        return (_arguments != null) ? _arguments.getString(name) : null;
    }

    public int getInt(String name) {
        return (_arguments != null) ? _arguments.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_arguments != null) ? _arguments.getBoolean(name) : false;
    }

    public Date getDate(String name) {
        return (_arguments != null) ? _arguments.getDate(name) : null;
    }

}
