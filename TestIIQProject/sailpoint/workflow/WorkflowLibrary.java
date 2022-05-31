/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A base class for workflow library classes.
 *
 * Author: Jeff
 *
 * A workflow library is just a class providing methods that can
 * be called usin the "call:" scriptlet from a workflow definition.
 * Libraries are referenced from the workflow definition and instantiated
 * dynamically by the workflow engine.  Reflect is then used to locate
 * the referenced methods.
 *
 * I have mixed feelings about this base class because I don't want to make it
 * a requirement that libraries extend this.  All a library has to 
 * do is provide methods of this form:
 *
 *     Object myMethod(WorkflowContext wfc)
 *
 * That can then be referenced from scriptlets like this:
 *
 *      action='call:myMethod'
 *
 * We use reflection to find the method so there isn't any need to
 * require that it implement a certain interface.
 *
 * But it seems likely that we'll accumulate a few generic utility
 * methods and it's easier to inherit them than it is to use
 * a utility class like WorkflowUtil.
 *
 * This is not intended for general purpose things that you call
 * directly from the workflow.  If you have something like that
 * it probably belongs in StandardWorkflowHandler.  
 * 
 * This class is intended to provide simple utility methods that may 
 * of use to the methods in more than one library.
 *
 * TODO: Think about a way to put a reference to the library
 * instance in the Beanshell environment, maybe have the class
 * declare a symbol like "libidentity".  This would give Beanshell
 * writers an easy way to call methods that aren't packaged
 * up as "call:"able things. For example
 *
 *       if (libidentity.someRandomMethod("foo")) 
 *
 * UPDATE: Instead of the last comment, why can't all library
 * methods be static?  Then you can just call them with
 *
 *     FooLibrary.something(wfcontxt)
 *
 */

package sailpoint.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.tools.Message;
import sailpoint.tools.Util;

class WorkflowLibrary {

    /**
     * Localize a message.
     */
    public static String getMessage(String key, Object arg) {
        Message lm = new Message(key, arg);
        return lm.getLocalizedMessage();
    }

    /**
     * Coerce the given value into a list of strings.
     * 
     * The value of the selections argument can be one of:
     *
     *    - String
     *    - List<String>
     *
     * Further, each String can be a CSV.
     *
     * @ignore
     * Created for the ARG_SELECTIONS argument used with remediateViolation
     * but relatively general.  Items returned by getRemediatables will be
     * CSVs, and since you can select both left and right sides the selection
     * may be a List of CSVs. We need to flatten all this into a list of role
     * names for remediation.
     */
    protected static List<String> getStringList(Object value) {

        List<String> strings = null;

        if (value instanceof String) {
            strings = Util.csvToList((String)value);
        }
        else if (value instanceof Collection) {
            strings = new ArrayList<String>();
            Collection<?> c = (Collection<?>)value;
            for (Object el : c) {
                if (el instanceof String)
                    strings.addAll(Util.csvToList((String)el));
            }
        }

        return strings;
    }


}
