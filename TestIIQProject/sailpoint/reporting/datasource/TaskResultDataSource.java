/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import sailpoint.object.Argument;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class TaskResultDataSource implements JRDataSource {

    /**
     * The return values extracted from the TaskResult.
     */
    Map _returns;

    /**
     * The iterator used when we're displaying the raw map contents.
     */
    Iterator<Map.Entry> _entries;

    /**
     * The current entry from _entries.
     */
    Map.Entry _current;
	

    /**
     * The iterator used when we're letting the TaskDefinition 
     * Signature guide what we display. 
     */
    Iterator<Argument> _arguments;

    /**
     * The current argument in the iteration.
     */
    Argument _argument;

    /**
     * Requestor's locale
     */
    Locale _locale;

    /**
     * Requestor's timezone
     */
    TimeZone _timezone;

    int cnt;

    /**
     * Initialize the internal iteration state in one of two ways.
     * If we can get to the Signature, always use it, otherwise
     * default to just dumping the raw map contents.
     */
    public TaskResultDataSource(TaskResult result, Locale locale, TimeZone timezone) {
        super();

        this._locale = locale;
        this._timezone = timezone;

        try {

            if ( result != null ) {
                result.load();
                _returns = result.getAttributes();

                // can we assume this will always be attached so 
                // we can navigate to the TaskDefinition?
                Signature sig = null;
                try {
                    TaskDefinition def = result.getDefinition();
                    if (def != null) {
                        def.load();
                        sig = def.getEffectiveSignature();
                    }
                }
                catch (Throwable t) {
                    // try to move on with the raw dump,
                    // !! will want to fix the Hibernate issues
                }

                if (sig != null) {
                    List<Argument> returns = new ArrayList<Argument>(sig.getReturns());
                    if (returns != null)
                        _arguments = returns.iterator();
                }
                else {
                    // the old way, dump the raw map
                    if ( _returns != null ) {
                        SortedMap sortedMap = new TreeMap(_returns);
                        _entries = sortedMap.entrySet().iterator();
                    }
                }   
            }
        } catch(Exception e ) {
            System.out.println("Error parsing xml." + e.toString());
        }
    }

    /*
     * djs: added this constructor so the caller set the argument definitions
     * and the arg values explicitly instead of digging into the TaskResult.
     *
     * When building a report using this datasource during task execution
     * (vs post-execution) there seem to be Hibernate issues getting the task 
     * results' signature.
     *
     * Problem even from the executor, so its not specifically a problem with Jasper
     * threading.
     *
     * error was: 
     * org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role: sailpoint.object.TaskDefinition.signature.arguments, no session or session was closed
     */
    public TaskResultDataSource(List<Argument> argDefs, Map<String,Object> args ) {
        super();
        _returns = args;
        if ( argDefs != null ) {
            _arguments = argDefs.iterator();
        }
    }   
	
    /**
     *
     */
    public boolean next() {

        boolean hasMore = false;

        if (_arguments != null) {
            hasMore = _arguments.hasNext();
            if ( hasMore ) {
                _argument = _arguments.next();

                // jsl - skip over arguments that cannot
                // be rendered in the table
                //if (_argument.isComplex())
                //hasMore = next();

            } else {
                _argument = null;
            }
        }
        else if ( _entries != null ) {
            hasMore = _entries.hasNext();
            if ( hasMore ) {
                _current = _entries.next();
            } else {
                _current = null;
            }
        }

        cnt++;

        return hasMore;
    }
	
    private String stringify(Object o) {

        if (o==null)
            return null;

        /**
         * If it's a lsit, make it a csv with spaces so that it wraps to a new line
         */
        if(o instanceof java.util.List) {
            return Util.listToCsv((List)o);
        }
        // if the value is a key, assume it's a key. If the value
        // is not a string, just pass it to the message class which will
        // handle dates, numbers, lists, other messages, etc. MSG_PLAIN_TEXT is
        // used b/c all it does is expect a single param
        Message msg = null;
        if (o instanceof String)
            msg = new Message((String)o);
        else if (o != null)
            msg = new Message(MessageKeys.MSG_PLAIN_TEXT, o);

        return msg.getLocalizedMessage(_locale, _timezone);
    }

    /**
     *
     */
    public Object getFieldValue(JRField field) {

        String s = null;

        String name = field.getName();

        if (_argument != null) {

            if ( "key".compareToIgnoreCase(name) == 0 ) {
                s = _argument.getDisplayLabel();
            } 
            else if ( "value".compareToIgnoreCase(name) == 0 ) {
                if (_returns != null)
                    s = stringify(_returns.get(_argument.getName()));
            }
        }
        else if ( _current != null ) {

            if ( "key".compareToIgnoreCase(name) == 0 ) {
                s = (String)_current.getKey();
            } 
            else if ( "value".compareToIgnoreCase(name) == 0 ) {
                s = stringify(_current.getValue());
            }
        }

        return s;
    }
}
