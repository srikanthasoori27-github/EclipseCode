/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.logging.log4j.core.LogEvent;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The SyslogEvent contains information about a serious error that occurred
 * somewhere in IdentityIQ.  
 * 
 * @author derry.cannon
 */
@XMLClass
public class SyslogEvent extends SailPointObject 
    {
    private static final long serialVersionUID = -450506054215989601L;
    
    private static final Log log = LogFactory.getLog(SyslogEvent.class);
    
    /** unique id, used in the UI to quickly reference the event */
    private String quickKey; 
    
    /** severity level of the event */
    private String eventLevel;
    
    /** name of the class that created the event */
    private String classname;

    /** line number at which the event occurred */
    private String lineNumber;
    
    /** message of the event */
    private String message;
    
    /** name of the user for whom the event occurred; "system" if unavailable */
    private String username;
    
    /** server on which the event occurred */
    private String server;
    
    /** name of the thread in which the event occurred */
    private String thread;
    
    /** full stacktrace of the event */
    private String stacktrace;

    /** field names to use when generating a unique id for the event */
    private String[] uniqueKeyProperties;
    
        
    public SyslogEvent() {}

    public SyslogEvent(LogEvent e, String q, String s, String u)
        {
        try 
            {
            eventLevel = e.getLevel().toString();
            classname = e.getSource().getClassName();
            lineNumber = "" + e.getSource().getLineNumber();
            message = generateMessage(e);
            thread = e.getThreadName();
            stacktrace = generateStackTrace(e);
            
            quickKey = q;
            server = s;
            username = u;
            } 
        catch (Exception ex) 
            {
            log.error("Problem creating syslog event", ex);
            }
        }

    
    /**
     * ServletExceptions are bad about putting information down in the 
     * root cause, which is not included as part of the ServletException's 
     * printStacktrace() method.  Iterate down through the ServletException's 
     * root cause chain to mine out the useful error info.
     * 
     * @param e LoggingEvent containing the Throwable
     * @return The full stacktrace of the Throwable
     * 
     * @ignore
     * NOTE: We can't have a dependency on ServletException, so use
     * reflection to deduce how to proceed.
     */
    private String generateStackTrace(LogEvent e)
        {
        if (e.getThrownProxy() == null)
            return null;
        
        Throwable t = e.getThrownProxy().getThrowable();
        StringBuffer sb = new StringBuffer(Util.stackToString(t));
        
        Method rootCauseMethod = findRootCauseMethod(t);
        while (rootCauseMethod != null) 
            {
            Throwable rootCause = findRootCause(t, rootCauseMethod);
            if (rootCause != null)
                {
                sb.append("Caused by: ");
                sb.append(Util.stackToString(rootCause));
                }

            t = rootCause;
            rootCauseMethod = findRootCauseMethod(rootCause);
            }
        
        return sb.toString();
        }

    
    /**
     * Dig through the LoggingEvent to generate some sort of useful
     * message, even if it is just the classname of the exception
     * contained in the LoggingEvent. 
     * 
     * @param e LoggingEvent containing the Throwable
     * @return A useful message about the Throwable
     * 
     * @ignore
     * So many weird wrinkles are possible here...
     * NOTE: We can't have a dependency on ServletException, so use
     * reflection to deduce how to proceed.
     */
    private String generateMessage(LogEvent e)
        {
        String msg = null;
        if (e.getMessage() != null)
            {
            msg = Util.truncate(e.getMessage().toString(), 450);
            }
        else if (e.getThrownProxy() ==  null)
            {
            msg = e.getSource().getClassName();
            }
        else
            {
            Throwable t = e.getThrownProxy().getThrowable();
            Method rootCauseMethod = findRootCauseMethod(t);
            if (rootCauseMethod != null)
                {
                try 
                    {
                    Throwable rootCause = (Throwable)rootCauseMethod.invoke(t, new Object[0]);
                    if (rootCause != null)
                        msg = messageFromThrowable(rootCause);
                    else 
                        msg = messageFromThrowable(t);
                    } 
                // bail if there are any reflection problems
                catch (IllegalArgumentException e1) { msg = messageFromThrowable(t); }
                catch (IllegalAccessException e1) { msg = messageFromThrowable(t); }
                catch (InvocationTargetException e1) { msg = messageFromThrowable(t); } 
                }
            else if (t.getCause() != null)
                {
                msg = messageFromThrowable(t.getCause());
                }
            else
                {
                msg = messageFromThrowable(t);
                }
            }
        
        return msg;
        }

    
    /**
     * Utility method that builds a message using the given throwable. 
     * The 450 char truncation is to support indexing on SQLServer.
     * 
     * @param t The Throwable to examine
     * @return The Throwable's message if one exists; 
     *         the Throwable's name otherwise
     */
    private String messageFromThrowable(Throwable t) 
        {
        String msg = t.getMessage();
        if (msg != null)
            Util.truncate(msg, 450);
        else
            msg = t.getClass().getName();
        
        return msg;
        }

    
    /**
     * Determine whether or not the given throwable has a getRootCause() method.
     * If so, return it. If not, return null, which is fine, too.
     * 
     * @param t The Throwable to inspect
     * 
     * @return The getRootCause() method if found; null, otherwise
     */
    private Method findRootCauseMethod(Throwable t) 
        {
        if (t == null)
            return null;
        
        try 
            {
            return t.getClass().getMethod("getRootCause", new Class<?>[0]);
            } 
        catch (NoSuchMethodException nsme) { return null; } 
        catch (SecurityException se) { return null; } 
        catch (IllegalArgumentException argEx) { return null; } 
        }

    
    /**
     * Determine whether or not the given throwable's root cause method returns
     * a Throwable. If so, return it.  If not, return null, which is fine, too.
     * 
     * @param t The Throwable to inspect
     * @param rootCauseMethod The Method to invoke on the Throwable
     * 
     * @return The root cause throwable if found; null, otherwise
     */
    private Throwable findRootCause(Throwable t, Method rootCauseMethod) 
        {
        if ((t == null) || (rootCauseMethod == null))
            return null;
        
        try 
            {
            return (Throwable)rootCauseMethod.invoke(t, new Object[0]);
            } 
        catch (SecurityException se) { return null; } 
        catch (IllegalArgumentException argEx) { return null; } 
        catch (IllegalAccessException e) { return null; } 
        catch (InvocationTargetException e) { return null; } 
        }

    
    @Override
    public boolean hasAssignedScope() 
        {
        return false;
        }

    
    @Override
    public boolean hasName() 
        {
        return false;
        }


    /**
     * @exclude
     * 
     * This is one of the few objects that overloads getUniqueKeyProperties() 
     * and specifies a set of unique key properties to prevent duplication if 
     * you load XML files full of SyslogEvents that don't have ids.  Without
     * matching against existing objects, we would end up with duplicates.
     */
    @Override
    public String[] getUniqueKeyProperties() 
        {
        if (uniqueKeyProperties == null) 
            {
            uniqueKeyProperties = new String[3];
            uniqueKeyProperties[0] = "created";
            uniqueKeyProperties[1] = "server";
            uniqueKeyProperties[2] = "classname";
            }
        
        return uniqueKeyProperties;
        }
    

    public static Map<String, String> getDisplayColumns() 
        {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("created", "Created");
        cols.put("quickKey", "Incident Code");
        cols.put("eventLevel", "Level");
        cols.put("message", "Message");
        
        return cols;
        }
    

    public static String getDisplayFormat() 
        {
        return "%-34s %-25s %-14s %-7s %s\n";
        }

    
    //////////////////////////////////////////////
    //
    // getters and setters
    //
    //////////////////////////////////////////////
    @XMLProperty
    public String getQuickKey() 
        {
        return quickKey;
        }

    public void setQuickKey(String quickKey) 
        {
        this.quickKey = quickKey;
        }

    @XMLProperty
    public String getEventLevel() 
        {
        return eventLevel;
        }

    public void setEventLevel(String eventLevel) 
        {
        this.eventLevel = eventLevel;
        }

    @XMLProperty
    public String getClassname() 
        {
        return classname;
        }

    public void setClassname(String classname) 
        {
        this.classname = classname;
        }

    @XMLProperty
    public String getLineNumber() 
        {
        return lineNumber;
        }

    public void setLineNumber(String lineNumber) 
        {
        this.lineNumber = lineNumber;
        }

    @XMLProperty(mode=SerializationMode.ELEMENT, xmlname="EventMessage")
    public String getMessage() 
        {
        return message;
        }

    public void setMessage(String message) 
        {
        this.message = message;
        }

    @XMLProperty
    public String getUsername() 
        {
        return username;
        }

    public void setUsername(String username) 
        {
        this.username = username;
        }

    @XMLProperty
    public String getServer() 
        {
        return server;
        }

    public void setServer(String server) 
        {
        this.server = server;
        }

    @XMLProperty
    public String getThread() 
        {
        return thread;
        }

    public void setThread(String thread) 
        {
        this.thread = thread;
        }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getStacktrace() 
        {
        return stacktrace;
        }

    public void setStacktrace(String stacktrace) 
        {
        this.stacktrace = stacktrace;
        }
    }
