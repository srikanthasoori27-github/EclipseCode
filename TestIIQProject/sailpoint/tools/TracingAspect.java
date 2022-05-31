/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * An aspect that adds tracing to all sailpoint methods.  This uses a
 * "pertypewithin" aspect, which associates the aspect state (in this case, the
 * log) with each type.  The net effect is that we essentially get a static
 * "log" variable to use for every class.
 * 
 * The pertypewithin causes all classes that don't have the Untraced annotation
 * to be woven.  The pointcuts further limit the use of the tracing loggers to
 * avoid infinite recursion, etc...
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Aspect("pertypewithin(!@sailpoint.tools.Untraced sailpoint..* || openconnector..*)")
@Untraced
public class TracingAspect {

    /**
     * The text to use to replace sensitive logging information.
     */
    private static final String SENSITIVE_VALUE_REPLACEMENT = "*****";


    /**
     * Maximum levels to recurse when filtering maps. 10 is both very low value and yet represents a fairly
     * deep Map.
     */
    private static final int MAX_RECURSION = 10;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Log initialization
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A Log that is specific to the class for this aspect.  This is pretty much
     * equivalent to the usual:
     * 
     *   private static final Log log = LogFactory.getLog(MyClass.class);
     */
    private Log log;

    @Pointcut("staticinitialization(*)")
    public void staticInit() {
    }

    /**
     * This is run in a static initializer block for every woven class, and is
     * used to initialize the trace logger for the class.
     */
    @After("staticInit()")
    public void initLogger(JoinPoint.StaticPart jps) {
        this.log = LogFactory.getLog(jps.getSignature().getDeclaringTypeName());
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Pointcuts
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Pointcut("!within(sailpoint.tools.UntracedObject)")
    void tracedClasses() {}

    @Pointcut("tracedClasses() && execution(new(..))")
    void tracedConstructors() {}

    // The !execution(String toString()) avoids infinite recursion by not
    // tracing toString() methods.
    // Also, don't trace the compile-time inserted 'access$nnn' methods. That might
    // (in very specific cases) lead to recursion but more generally just confusing
    @Pointcut("tracedClasses() && execution(* *(..)) && !execution(String toString()) && !execution(* access$*(..)) " +
            "&& !@annotation(sailpoint.tools.Untraced)")
    void tracedMethods() {}

    @Pointcut("tracedMethods() && !@annotation(sailpoint.tools.TracedSpecialCase)")
    void tracedStandardMethods() {}

    @Pointcut("tracedMethods() && @annotation(sailpoint.tools.TracedSpecialCase) " +
            "&& @annotation(sailpoint.tools.TracedKeyValueParams)")
    void tracedKeyValueMethods() {}

    ////////////////////////////////////////////////////////////////////////////
    //
    // Advice
    //
    ////////////////////////////////////////////////////////////////////////////

    @Before("tracedConstructors()")
    public void traceConstructorEntry(JoinPoint thisJoinPoint) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(entryMsg(thisJoinPoint));
    }

    @AfterReturning("tracedConstructors()")
    public void traceConstructorExit(JoinPoint thisJoinPoint) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(exitMsg(thisJoinPoint, null));
    }

    @AfterThrowing(pointcut="tracedConstructors()",throwing="t")
    public void traceConstructorThrow(JoinPoint thisJoinPoint, Throwable t) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(throwingMsg(thisJoinPoint, t));
    }

    @Before("tracedStandardMethods()")
    public void traceMethodEntry(JoinPoint thisJoinPoint) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(entryMsg(thisJoinPoint));
    }

    @Before("tracedKeyValueMethods()")
    public void traceKeyValueMethodEntry(JoinPoint thisJoinPoint) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(entryMsgWithKeyValueParams(thisJoinPoint));
    }

    @AfterReturning(pointcut="tracedMethods()",returning="r")
    public void traceMethodExit(JoinPoint thisJoinPoint, Object r) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(exitMsg(thisJoinPoint, r));
    }

    @AfterThrowing(pointcut="tracedMethods()",throwing="t")
    public void traceMethodThrow(JoinPoint thisJoinPoint, Throwable t) {
        if ((null != log) && log.isTraceEnabled())
            log.trace(throwingMsg(thisJoinPoint, t));
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Helper Methods
    //
    ////////////////////////////////////////////////////////////////////////////
    /*
     *  Tests if a constructor is for an inner type.
     *  
     *  So here's our quandary: when we trace the constructor of
     *  an inner class, the first parameter is the encapsulating
     *  instance. Most of the time we probably don't care. But in
     *  some rare cases this can cause nasty recursion.
     *  Ex. Implement a Map where the keySet() method leverages an
     *  anonymous inner type. When we trace the construction of that
     *  anonymous inner type, the first parameter will be the
     *  encapsulating Map instance. When we go to filter the values
     *  of that Map, we will have to iterate the values
     *  which calls the map's keySet() method. As stated earlier, keySet()
     *  declares an anonymous inner type and at this point we've entered
     *  a never ending recursion loop
     *  
     *  Note: this is only an issue with inner classes. Nested classes
     *  are fine.
     *  
     *  Solution: we ignore the first parameter of inner type constructors.
     *  Given that it's the Java compiler who adds it, those who later
     *  rely on this trace for debugging will not miss that first
     *  missing parameter. This method exposes a JoinPoint representing
     *  such a constructor.
     */
    private static boolean isConstructorOfInnerClass(JoinPoint jp) {

        String kind = jp.getKind();
        // Not a constructor? Don't bother with the expensive reflection stuff later
        if (!JoinPoint.CONSTRUCTOR_CALL.equals(kind) && !JoinPoint.CONSTRUCTOR_EXECUTION.equals(kind)) {
            return false;
        }
        
        Signature sig = jp.getSignature();
        Class declaringType = sig.getDeclaringType();
        // So how do we tell? Test if it has an enclosing type (nested and inner classes both do)
        // and if it's missing the static modifier (static = nested, !static = inner)
        int modifiers = declaringType.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        return !isStatic && declaringType.getEnclosingClass() != null;
        
    }

    // TODO: Consider moving these to a logging helper class?

    /**
     * Standard method entry tracing
     */
    private static String entryMsg(JoinPoint jp)
    {
        StringBuffer buf = new StringBuffer();
        openSignature(buf, jp);
        processStandardParams(buf, jp);
        closeSignature(buf);
        return buf.toString();
    }

    /**
     * method entry tracing for methods with separate key and value params
     */
    private static String entryMsgWithKeyValueParams(JoinPoint jp)
    {
        StringBuffer buf = new StringBuffer();
        openSignature(buf, jp);
        processKeyValueParams(buf, jp);
        closeSignature(buf);
        return buf.toString();

    }

    /**
     * Perform the standard tracing of method parameters
     */
    private static void processStandardParams(StringBuffer buf, JoinPoint jp) {

        CodeSignature sig = (CodeSignature) jp.getSignature();
        String[] params = sig.getParameterNames();
        Object[] paramVals = jp.getArgs();

        int startParam = 0;
        if (isConstructorOfInnerClass(jp)) {
            startParam = 1;
        }

        for (int i=startParam; i<params.length; i++) {
            addParamDeclaration(buf, params[i]);

            if (isSensitive(params[i])) {
                buf.append(SENSITIVE_VALUE_REPLACEMENT);
            } else {
                buf.append(filterValue(paramVals[i]));
            }

            if (i != params.length - 1)
                addParamSeparator(buf);
        }
    }



    /**
     * Perform the tracing of method parameters, respecting the TracedKeyValueAnnotation annotation --
     * which identifies which parameters are key and value.  Show the value parameter as hidden if the
     * key parameters is sensitive.
     */
    private static void processKeyValueParams(StringBuffer buf, JoinPoint jp) {

        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();

        TracedKeyValueParams annot = method.getAnnotation(TracedKeyValueParams.class);
        if (annot == null) {
            // fail-safe
            processStandardParams(buf, jp);
            return;
        }

        String[] params = sig.getParameterNames();
        Object[] paramVals = jp.getArgs();

        int startParam = 0;
        if (isConstructorOfInnerClass(jp)) {
            startParam = 1;
        }

        // Look for the key and value parameters, based on the names specified in the annotation

        String keyName = annot.keyParamName();
        String valueName = annot.valueParamName();

        int posKey = -1;
        int posValue = -1;
        for (int i=startParam; i<params.length; i++) {
            if (keyName.equals(params[i])) {
                posKey = i;
            }
            if (valueName.equals(params[i])) {
                posValue = i;
            }
        }

        if (posValue == -1) {
            // no parameter found matching the name of the value parameter
            processStandardParams(buf, jp);
            return;
        }

        boolean isSensitiveKey = true;
        if (posKey != -1) {
            isSensitiveKey = isSensitive(paramVals[posKey]);
        }

        for (int i=startParam; i<params.length; i++) {
            addParamDeclaration(buf, params[i]);

            if (i == posKey) {
                // always print the key unfiltered
                buf.append(paramVals[i]);
            }
            else if (i == posValue) {
                // filter the value only if the key name was sensitive
                if (isSensitiveKey) {
                    buf.append(SENSITIVE_VALUE_REPLACEMENT);
                }
                else {
                    buf.append(paramVals[i]);
                }
            }
            else {
                if (isSensitive(params[i])) {
                    buf.append(SENSITIVE_VALUE_REPLACEMENT);
                } else {
                    buf.append(filterValue(paramVals[i]));
                }
            }

            if (i != params.length - 1)
                addParamSeparator(buf);
        }
    }

    private static String ENTERING_STR = "Entering ";

    private static void openSignature(StringBuffer buf, JoinPoint jp) {
        CodeSignature sig = (CodeSignature) jp.getSignature();

        buf.append(ENTERING_STR);
        buf.append(sig.getName());
        buf.append("(");
    }

    private static void closeSignature(StringBuffer buf) {
        buf.append(")");
    }

    private static void addParamDeclaration(StringBuffer buf, String paramName) {
        buf.append(paramName).append(" = ");
    }

    private static void addParamSeparator(StringBuffer buf) {
        buf.append(", ");
    }
    
    /**
     * Filters sensitive information out of the string representation of value.
     * If the value is determined to be sensitive, then SENSITIVE_VALUE_REPLACEMENT
     * is returned. There is special handling for maps to avoid filtering the entire map
     * when only one key/value pair is sensitive.
     * 
     * @param value The value to test.
     * @return The filtered string representation.
     */
    @SuppressWarnings("rawtypes")
    static String filterValue(Object value)
    {
        return filterValue(value, 0);
    }
    
    /**
     * Filters sensitive information out of the string representation of value.
     * If the value is determined to be sensitive, then SENSITIVE_VALUE_REPLACEMENT
     * is returned. There is special handling for maps to avoid filtering the entire map
     * when only one key/value pair is sensitive.
     * 
     * @param value The value to test.
     * @return The filtered string representation.
     */
    @SuppressWarnings("rawtypes")
     private static String filterValue(Object value, int level)
    {
        if (value instanceof Map) {
            if (level < MAX_RECURSION) {
                return filterMap((Map) value, level);
            } else {
                // We've gone too deep, likely within this map. Stop trying to filter so much and just
                // send back an elipsis to represent there was more data. This way we don't accidentally expose
                // sensitive data.
                return "...(truncating filtered value due to maximum recursion level reached)";
            }
        }
        
        if (isSensitive(value)) {
            return SENSITIVE_VALUE_REPLACEMENT;
        }
        
        return String.valueOf(value);        
    }
    
    /**
     * Filters the string representation of a map to remove sensitive data.
     * @param map The map to filter.
     * @return The filtered string representation.
     */
    @SuppressWarnings("rawtypes")
    public static String filterMap(Map map)
    {
        return filterMap(map, 0);
    }
    
    
    /**
     * Filters the string representation of a map to remove sensitive data.
     * @param map The map to filter.
     * @param level The current level of recursion
     * @return The filtered string representation.
     */
    @SuppressWarnings("rawtypes")
    private static String filterMap(Map map, int level)
    {
        assert(map != null);

        //Preserve insertion order
        Map<Object,Object> result = new LinkedHashMap<Object,Object>();
        
        ArrayList<Object> keys = new ArrayList<Object>();
        // See bug 11695 to see why full iteration of keySet first
        for (Object key: map.keySet()) {
            keys.add(key);
        }
        
        for (Object key : keys) {
            if (isSensitive(key)) {
                result.put(key, SENSITIVE_VALUE_REPLACEMENT);
            } else {
                result.put(key, filterValue(map.get(key), level++));
            }
        }
        
        return result.toString();        
    }
    
    /**
     * Gets whether or not the string representation of value contains
     * sensitive information. This can be used to check argument names to determine
     * if the value is sensitive or within the value itself.
     * 
     * @param value The value to check.
     * @return True if sensitive information is found, false otherwise.
     */
    static boolean isSensitive(Object value)
    {
        String stringValue = String.valueOf(value);
        
        final List<String> sensitiveValues = Arrays.asList(
            "password",
            "currentPassword",
            "passPhrase",
            "secret",
            "USER_PWD",
            "answer",
            "java.naming.security.credentials",
            "m_sAWSAccessKeyId",
            "m_sAWSSecretKey",
            "bearerToken",
            "clientId",
            "clientSecret",
            "accessToken",
            "secretKey",
            "authPassword",
            "access_token",
            "assertion",
            "Authorization",
            "JSESSIONID",
            "private_key",
            "privateKey",
            "wsPasswd",
            "jco.client.passwd",
            "keyPass", 
            "keystorePass",
            "httpUserPass",
            "token"
        );
        
        return containsAny(stringValue, sensitiveValues);
    }

    /**
     * Gets whether or not the specified string contains any of the searchValues as substrings.
     * This check is case-insensitive.
     * 
     * @param value The value to search.
     * @param searchValues The values to search for.
     * @return True if value contains any of searchValues as a substring, false otherwise.
     */
    static boolean containsAny(String value, Collection<String> searchValues)
    {
        if (value == null || searchValues == null) {
            return false;
        }
        
        String lowerValue = value.toLowerCase();
        
        for (String searchValue : searchValues) {
            String lowerSearchValue = searchValue.toLowerCase();
            
            if (lowerValue.contains(lowerSearchValue)) {
                return true;
            }
        }        
        
        return false;
    }
    
    private static String exitMsg(JoinPoint jp, Object returnVal)
    {
        StringBuffer buf = new StringBuffer("Exiting ");
        boolean sensitive = false;
        if (jp.getSignature() instanceof  MethodSignature) {
            sensitive = ((MethodSignature)jp.getSignature()).getMethod().getAnnotation(SensitiveTraceReturn.class) != null;
        }

        buf.append(jp.getSignature().getName()).append(" = ").append(sensitive ? SENSITIVE_VALUE_REPLACEMENT : filterValue(returnVal));
        return buf.toString();
    }

    private static String throwingMsg(JoinPoint jp, Throwable t)
    {
        StringBuffer buf = new StringBuffer("Throwing ");
        buf.append(jp.getSignature().getName()).append(" - ").append(t.toString());
        return buf.toString();
    }

}
