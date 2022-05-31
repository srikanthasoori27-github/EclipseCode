/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.mozilla.universalchardet.UniversalDetector;

import sailpoint.persistence.Sequencer;


/**
 * Various utilities.
 */
public class Util {


    //////////////////////////////////////////////////////////////////////
    //
    // Misc utilities
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Util.class);

    // detects charset/encoding of byte arrays
    private static UniversalDetector universalDetector = new UniversalDetector(null);

	private static String sailpointWebDir = null;

	public static void setSailpointWebDir(String slptWebDir) {
		sailpointWebDir = slptWebDir;
	}

    /**
     * Flag to set whether we should use the Secure attribute for the CSRF
     * cookies created by IIQ. Currently we mirror the configuration for the
     * session cookie as set in web.xml. See StartupContextListener.setProperties()
     */
    private static boolean secureCookies = false;

    public static void setSecureCookies(boolean secure) {
        secureCookies = secure;
    }

    public static boolean useSecureCookies() {
        return secureCookies;
    }

    //System property used to search native libraries
    private static final String JAVA_LIB_PATH = "java.library.path";

    /**
     * Pause for a specified number of milliseconds
     * 
     * @ignore
     * Catch the usual silly exception.
     */
    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
        }
    }

    
    /**
     * Takes an integer value and left pads it with 0s. Negative values will
     * have the negative marked at the beginning of the string, for example, -000001
     */
    public static String leftPadZeros(int toValue, int padding) {
        int workingValue = Math.abs(toValue);
        // The floor of Log10 of a (base 10) number tells us how many digits it is, minus 1.
        int delta = workingValue > 0 ? (int) Math.log10(workingValue) : 0;
        int realPad = padding - (delta + 1);
        StringBuilder buff = new StringBuilder();
        if (toValue < 0) {
            buff.append("-");
        }
        for (int i = 0; i <= realPad; i++) {
            buff.append(0);
        }
        buff.append(workingValue);
        return buff.toString();
    }
    
    /**
     * Generate a unique identifier.
     */
    public static String uuid() {

        // this one has hyphens and colons in it
        //return new java.rmi.dgc.VMID().toString();

        // this has hyphens but no colons
        String id = java.util.UUID.randomUUID().toString();

        // jsl - I like not having the hyphens it makes
        // them a little shorter and less distracting in the XML
        // and emacs can skip over them without stopping
        id = id.replaceAll("-", "");

        return id;
    }

    /**
     * Generate a unique string of given length.
     * @param length
     * @return uniqueStr
     */
    public static String uniqueString(int length) {
        char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        String uniqueStr = sb.toString();

        return uniqueStr;
    }

    public static String getHostName() {
        String name = null;
        try {
            // To support server affinity on multi-processor/core
            // machines we allowe the host name to be overridden
            // with a system property.
            name = System.getProperty("iiq.hostname");
            if (name == null || name.length() == 0) {
                InetAddress addr = InetAddress.getLocalHost();
                name = addr.getHostName();
            }
        }
        catch (UnknownHostException e) {
        }

        if (name == null) name = "????";

        return name;
    }

    /**
     * This method performs a null-safe equality comparison between the two
     * given objects. If both objects are null, this returns false.
     *
     * @param  o1  The first object to compare.
     * @param  o2  The second object to compare.
     *
     * @return True if both objects are non-null and equal.
     */
    public static boolean nullSafeEq(Object o1, Object o2) {
        return nullSafeEq(o1, o2, false);
    }

    /**
     * This method performs a null-safe equality comparison between the two
     * given objects. This will return true if both objects are null and
     * nullsEq is true.
     *
     * @param  o1       The first object to compare.
     * @param  o2       The second object to compare.
     * @param  nullsEq  Whether two null objects should be considered equal.
     *
     * @return True if both objects equal, or both objects are null and nullsEq
     *         is true.
     */
    public static boolean nullSafeEq(Object o1, Object o2, boolean nullsEq) {
        return nullSafeEq(o1, o2, nullsEq, false);
    }

    /**
     * This method performs a null-safe equality comparison between the two
     * given objects. This will return true if both objects are null and
     * nullsEq is true. Empty or "null" strings will be converted to null
     * before checking if emptyStringToNull is true.
     */
    public static boolean nullSafeEq(Object o1, Object o2,
                                     boolean nullsEq,
                                     boolean emptyStringToNull) {
        
        if (emptyStringToNull) {
            if (o1 instanceof String) {
                o1 = getString((String) o1);
            }
            if (o2 instanceof String) {
                o2 = getString((String) o2);
            }
        }

        if (nullsEq && (null == o1) && (null == o2))
            return true;

        return (null != o1) ? o1.equals(o2) : false;
    }

    /**
     * This method performs a null-safe equality comparison between the two strings.  
     * This will return true if both objects are null and nullsEq is true.  
     * Empty or "null" strings will be converted to null before checking if emptyStringToNull is true.
     */
    public static boolean nullSafeCaseInsensitiveEq(String o1, String o2) {
        if (null == o1 && null == o2)
            return true;

        return (null != o1) ? o1.equalsIgnoreCase(o2) : false;
    }
    
    /**
     * Return the compareTo() value for two objects that may be null. Nulls
     * are considered equal, and null is greater than non-null.
     */
    public static <T extends Comparable> int nullSafeCompareTo(T o1, T o2) {

        if ((null == o1) && (null == o2)) {
            return 0;
        }

        // We'll say that null > non-null.
        if ((null == o1) && (null != o2)) {
            return Integer.MAX_VALUE;
        }

        if ((null != o1) && (null == o2)) {
            return Integer.MIN_VALUE;
        }

        return o1.compareTo(o2);
    }

    /**
     * Return the hashcode of the given object, or -1 if the given object is
     * null.
     */
    public static int nullSafeHashCode(Object o) {
        return (null != o) ? o.hashCode() : -1;
    }
    
    /**
     * 
     * Check a list for the given object, checking for
     * null on the list.
     * 
     * @param list List to check for object
     * @param o Object to check for in the list
     * @return true if value is found in the list
     */
    public static boolean nullSafeContains(List list, Object o) {
        if ( list != null && o != null ) {
            return list.contains(o);
        }
        return false;
    }
    
    /**
     * 
     * Check a csv-based list for the given object, checking for
     * null on the list.
     * 
     * @param csv List of objects in CSV form
     * @param o Object to check for in the list
     * @return true if value is found in the list
     */
    public static boolean nullSafeContains(String csv, Object o) {
        String s = otos(o);        
        if ( !Util.isNullOrEmpty(csv) && !Util.isNullOrEmpty(s)) {
            List<String> list = Util.csvToList(csv, true);
            return list.contains(s);
        }
        return false;
    }

    /**
     * Return the length of a list or zero if it is null.
     */
    public static int nullSafeSize(List list) {
        return (list != null) ? list.size() : 0;
    }

    /**
     * Return lower case copy of string or null if it is null
     */
    public static String nullSafeLowerCase(String str) {
        return (str != null) ? str.toLowerCase() : null;
    }

    /**
     * Generate a random number between a low a high value inclusive.
     */
    public static int rand(int low, int high) {

        Random rand = new Random();

        int range = (high - low) + 1;
        int r = rand.nextInt(range);

        return r + low;
    }

    /**
     * Determines if a leading single quote is present to protect against an excel vulnerability
     * @param value
     * @return
     */
     public static boolean isEscapedForExcelVulnerability(String value) {
         if (size(value) > 1 && value.startsWith("\'")) {
             char[] valueArray = value.toCharArray();
             if (valueArray[1] == '=' || valueArray[1] == '+' || valueArray[1] == '-' || valueArray[1] == '@') {
                return true;
             }
         }
         return false;
     }

    //////////////////////////////////////////////////////////////////////
    //
    // Collection tools
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Remove all nulls from a list.
     * 
     * @ignore
     * Added as a temporary kludge for some obscure Hibernate crap
     * that adds phantom null's to Lists under some conditions.
     * Do not collapse empty lists to null, some object constructors
     * set the list before they begin populating it.  This obviously
     * still has the potential for nulls, but at least Hibernate doesn't
     * appear to follow that pattern.
     */
    static public <T extends Object> List<T> filterNulls(List<T> src) {
        if ((src != null) && src.contains(null)) {
            src.removeAll(Collections.singleton(null));
        }
        return src;
    }

    /**
     * cannot have '.' in json key names change them to '-'
     */
    public static String getJsonSafeKey(String key) {
        return key != null ? key.replace('.', '-') : null;
    }

    /**
     * Does the opposite of {@link #getJsonSafeKey(String)}
     */
    public static String getKeyFromJsonSafeKey(String jsonSafeKey) {
        return jsonSafeKey != null ? jsonSafeKey.replace('-', '.') : null;
    }

    /**
     * Interface that is used with filter(List<T>, ListFilter<T>) to specify
     * which elements should not be included in the filtered list.
     */
    public static interface ListFilter<T> {
        /**
         * Return true if the given element should not be included in the
         * filtered list, false otherwise.
         */
        public boolean removeFromList(T o) throws GeneralException;
    }

    /**
     * Return a filtered copy of the given list with any elements that match
     * the given ListFilter not included.
     *
     * @param  list    The List to filter.
     * @param  filter  The ListFilter to apply to each element to determine if
     *                 it should be in the copy or not.
     *
     * @return A filtered copy of the given list with any elements that match
     *         the given ListFilter not included.
     */
    public static <T> List<T> filter(List<T> list, ListFilter<T> filter)
    throws GeneralException {

        if ((null != list) && (null != filter)) {
            List<T> filtered = new ArrayList<T>();
            for (T t : list) {
                if (!filter.removeFromList(t))
                    filtered.add(t);
            }
            return filtered;
        }

        return list;
    }

    public interface IMatcher<T> {
        boolean isMatch(T val);
    }

    /**
     * Looks through the list and returns an item
     * which matches the IMatcher interface.
     */
    public static <T> T find(List<T> values, IMatcher<T> matcher) {
        
        if (values == null) {
            return null;
        }
        
        for (T value : values) {
            if (matcher.isMatch(value)) {
                return value;
            }
        }

        return null;
    }
    
    /**
     * Returns a List backed by the given list that wraps each element that is
     * returned using the given wrapper. This original intent was to provide
     * lazy proxy replacement for list elements (for example - a proxy for each element
     * is returned instead of the actual element), but this could also be used
     * to transform the elements when they are read from the list. Passing in
     * a null list will return null.
     *
     * @param  list     The List to wrap.
     * @param  wrapper  The ListElementWrapper to use to wrap the elements when
     *                  retrieved from the list.
     *
     * @return A List backed by the given list that wraps each element this is
     *         returned using the given wrapper, or null if the given list is
     *         null.
     *
     * @see sailpoint.tools.Util.ListElementWrapper
     */
    public static <T> List<T> wrappedList(List<T> list, ListElementWrapper<T> wrapper) {
        return (null != list) ? new WrappedList<T>(list, wrapper) : null;
    }

    /**
     * A ListElementWrapper is responsible for wrapping (or transforming) each
     * element of a List when the element is retrieved from the list.
     */
    public static interface ListElementWrapper<T> {
        /**
         * Wrap or transform the given list element.
         *
         * @param  element  The element to wrap or transform.
         *
         * @return A wrapper around the given element, or the element itself if
         *         the function of this ListElementWrapper is to just transform
         *         the element.
         */
        public T wrap(T element);
    }

    /**
     * A List implementation that wraps each element returned by get(int) using
     * the wrapper supplied to the constructor.
     */
    private static class WrappedList<T> extends AbstractList<T> {
        private List<T> list;
        private ListElementWrapper<T> wrapper;

        public WrappedList(List<T> list, ListElementWrapper<T> wrapper) {
            this.list = list;
            this.wrapper = wrapper;
        }
        public T get(int i) {
            return this.wrapper.wrap(this.list.get(i));
        }
        public int size() {
            return this.list.size();
        }
    }

    /**
     * Return a List with the given object. If the object is a List, it is
     * returned. If the object is a collection, a List with all of the elements
     * of the collection is returned. If the object is a non-collection, a new
     * List containing the given object is returned. If the object is null,
     * null is returned.
     *
     * @param  o  The object to return as a list.
     */
    @SuppressWarnings("unchecked")
    public static List asList(Object o) {
        if (null == o)
            return null;
        if (o instanceof List)
            return (List) o;

        List list = new ArrayList();
        if (o instanceof Collection)
            list.addAll((Collection) o);
        else
            list.add(o);
        return list;
    }

    /**
     * Return true if the given object is the first element of the given
     * Iterable.
     *
     * @param  o  The object to check as the first element.
     * @param  i  The Iterable to check.
     *
     * @return True if the given object is the first element of the given
     *         Iterable.
     */
    public static boolean isFirstElement(Object o, Iterable i)
    {
        return ((null != o) && (null != i) &&
                i.iterator().hasNext() && o.equals(i.iterator().next()));
    }

    public static boolean isLastElement(Object o, Iterable i)
    {
        boolean isLast = false;
        if ( (null != o) && (null != i) ) {
            Iterator it = i.iterator();
            Object lastObject = null;
            if ( it != null ) {
                while ( it.hasNext() ) {
                    lastObject = it.next();
                }
                if ( ( lastObject != null) && ( o.equals(lastObject) ) ) {
                    isLast = true;
                }
            }
        }
        return isLast;
    }
    
    /**
     * Return true if the specified Iterator is empty.
     * @param i Iterator being checked
     * @return true of the given Iterator is empty
     */
    public static boolean isEmpty(Iterator i) {
        return (i == null || !i.hasNext());
    }

    /**
     * Exhaust the remainder of the iterator so that Hibernate will properly
     * close the cursor associated with it. 
     * @param i Iterator being exhausted
     */
    public static void flushIterator(Iterator i) {
        if (i != null) {
            while (i.hasNext()) {
                i.next();
            }
        }
    }
    
    /**
     * Check if the two given Lists contain all of the same elements. Unlike
     * ArrayList.equals(Object), this ignores the order of the elements.
     *
     * @param  l1  The first List to compare.
     * @param  l2  The second List to compare.
     *
     * @return Return true if both Lists are non-null and contain all of the
     *         same elements.
     */
    public static <T> boolean orderInsensitiveEquals(List<T> l1, List<T> l2) {
        if ((null != l1) && (null != l2)) {
            HashSet<T> s1 = new HashSet<T>(l1);
            HashSet<T> s2 = new HashSet<T>(l2);
            return s1.equals(s2);
        }
        return false;
    }

    /**
     * Find the requested object in the given list using the given comparator.
     *
     * @param  list        The list in which to search for a match to t.
     * @param  t           The object to look for using the comparator in the list.
     * @param  comparator  The Comparator to use to compare t to the list elements.
     *
     * @return The requested object in the given list or null if not found.
     */
    public static <T> T find(List<T> list, T t, Comparator<T> comparator) {
        T found = null;
        if (null != list) {
            for (T current : list) {
                if (0 == comparator.compare(t, current)) {
                    found = current;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Return a matching object from the given list.
     *
     * @param  list    The list of comparable items.
     * @param  toFind  The item to find.
     * @param  sorted  Whether the list is already sorted.
     *
     * @return A matching object according to the comparable interface, or null
     *         if no match is found.
     */
    public static <T extends Comparable<? super T>> T findComparable(List<T> list, T toFind, boolean sorted) {

        T found = null;

        if ((null != list) && (null != toFind)) {

            List<T> searchList = list;

            // Copy the list so we don't mess up the original when we sort.
            if (!sorted) {
                List<T> copy = new ArrayList<T>(list);
                Collections.sort(copy);
                searchList = copy;
            }

            int idx = Collections.binarySearch(searchList, toFind);
            if (idx > -1) {
                found = list.get(idx);
            }
        }

        return found;
    }


    /**
     * Return whether the given list of comparable items contains the give item
     * according to the Comparable interface. This is similar to the contains()
     * method on Collection, except that it uses natural ordering (Comparable
     * interface) rather than equality (equals and hashCode) to determine
     * matches.
     *
     * @param  list    The list of comparable items.
     * @param  toFind  The item to find.
     * @param  sorted  Whether the list is already sorted.
     *
     * @return Whether the given item is in the given list. False if either
     *         parameter is null.
     */
    public static <T extends Comparable<? super T>> boolean containsComparable(List<T> list, T toFind, boolean sorted) {

        return (null != findComparable(list, toFind, sorted));
    }

    /**
     * Given an array, returns a list.
     *
     * @param array Array to copy to a list
     * @return New List, empty list if array is null.
     */
    public static <T>List<T> arrayToList(T[] array){
        final ArrayList<T> out;

        if (array == null)
            out = new ArrayList<T>();
        else
            out = new ArrayList<T>(Arrays.asList(array));

        return out;
    }

    /**
     * Return a copy of the array with the first element removed.
     * @param input  The array to shift.
     * @return A copy of the array with the first element removed.
     */
    public static String[] shiftArray(String[] input) {
        if ( input == null ) return new String[0];

        String[] output = new String[input.length - 1];

        System.arraycopy(input, 1, output, 0, input.length - 1);

        return output;
    }

    /**
     * Merge the contents of two arrays of longs into a newly created sorted array
     * @param array1 First array to merge
     * @param array2 Second array to merge
     * @return New array containing the sorted contents of the two specified arrays
     */
    public static long [] mergeAndSortArrays(long[] array1, long [] array2) {
        long[] merged;
        int array1Length = array1.length;
        int array2Length = array2.length;

        if (array1 == null || array1Length == 0) {
            if (array2 == null || array2Length == 0) {
                return new long[0];
            } else {
                merged = Arrays.copyOf(array2, array2Length);
            }
        } else if (array2 == null || array2.length == 0) {
            merged = Arrays.copyOf(array1, array1Length);
        } else {
            merged = new long[array1Length + array2Length];
            System.arraycopy(array1, 0, merged, 0, array1Length);
            System.arraycopy(array2, 0, merged, array1Length, array2Length);
        }

        Arrays.sort(merged);
        return merged;
    }
    
    /**
     * Return a List with the elements returned by the given iterator.
     *
     * @param  iterator  The Iterator from which to create the List.
     *
     * @return A List with the elements returned by the given iterator.
     */
    public static <T> List<T> iteratorToList(Iterator<T> iterator) {

        List<T> list = new ArrayList<T>();

        if (null != iterator) {
            while (iterator.hasNext()) {
                list.add(iterator.next());
            }
        }

        return list;
    }
    
    /**
     * Constructs a null-safe iterable for use with for-each loops to avoid null
     * checking.
     * @param iterable The iterable to use.
     * @return iterable if it is non-null, otherwise an empty iterable.
     */
    public static <T> Iterable<T> safeIterable(Iterable<T> iterable) 
    {
        if (iterable != null) {
            return iterable;
        }
        
        return Collections.emptyList();
    }

    /**
     * Constructs a null-safe stream
     *
     * @param stream
     *            The stream to use.
     * @return stream if it is non-null, otherwise an empty stream.
     */
    public static <T> Stream<T> safeStream(Collection<T> collection) {
        return collection == null ? Stream.empty() : collection.stream();
    }

    /**
     * Format a message with optional arguments protecting against {@link IllegalArgumentException}.
     * Unparsable messages will result in the unformatted message returned with the error logged
     * @param format The pattern to format
     * @param args Optional arguments
     * @return Formatted string, or the same string upon error
     * @see MessageFormat#format(String, Object...)
     */
    public static String safeMessageFormat(String format, Object... args) {
        String formatted = format;
        try {
            formatted = MessageFormat.format(format, args);
        } catch (IllegalArgumentException e) {
            StringBuilder buff = new StringBuilder();
            buff.append("Unable to format message: ").append(format).append("\n");
            if (!isEmpty(args)) {
                for (int i = 0; i < args.length; i++) {
                    buff.append("arg(").append(i).append("): ").append(args[i]).append("\n");
                }
            }
            // Log a warning with full context of what just happened. The original
            // format string without arguments formatted in will be the return string.
            // This might result in a surprising result in unexpected places, but funky 
            // messages w/ explicit warnings in the log is better than blowing up the
            // process with an Exception
            if (log.isDebugEnabled()) {
                // I like warnings to be terse and debug to have more context
                log.debug(buff.toString(), e);
            } else {
                log.warn(buff.toString());
            }
        }
        return formatted;
    }

    /**
     * Constructs a null-safe iterable for use with for-each loops to avoid null
     * checking.
     * @param iterable The iterable to use.
     * @return iterable if it is non-null, otherwise an empty iterable.
     * 
     * @ignore
     * Alternate name that's easier to remember.
     */
    public static <T> Iterable<T> iterate(Iterable<T> iterable) {
        return safeIterable(iterable);
    }
    
    /**
     * Return a List of maps that have the object arrays in the given iterator
     * keyed by the given list of keys.
     *
     * @param  iterator  The Iterator from which to create the List of Maps.
     * @param  keys      The keys for the maps that are being created. The
     *                   number of keys must match the number of objects in each
     *                   Object[] in the iterator. Note that this can be a
     *                   single string that has a comma-separated list of keys.
     *
     * @return A List with the elements returned by the given iterator.
     */
    public static List<Map<String,Object>> iteratorToMaps(Iterator<Object[]> iterator,
                                                          String... keys) {

        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();

        // Accept a single csv key also.
        if (keys.length == 1) {
            List<String> keyList = csvToList(keys[0]);
            keys = keyList.toArray(new String[keyList.size()]);
        }

        if (null != iterator) {
            while (iterator.hasNext()) {
                Object[] current = iterator.next();

                if (current.length != keys.length) {
                    throw new RuntimeException("Current element does not have expected columns: " +
                                               current + " - " + keys);
                }

                Map<String,Object> map = new HashMap<String,Object>();
                result.add(map);
                for (int i=0; i<current.length; i++) {
                    map.put(keys[i], current[i]);
                }
            }
        }

        return result;
    }

    /**
     * Gets the intersection of the two collections.
     *
     * @param xs A collection.
     * @param ys A collection.
     * @return The intersection.
     */
    public static <T> List<T> intersection(List<T> xs, List<T> ys) {
        List<T> result = new ArrayList<T>();
        if (xs == null && ys == null) {
            return result;
        }

        Set<T> valueSet = new HashSet<T>();
        if (xs == null) {
            valueSet.addAll(ys);
        } else if (ys == null) {
            valueSet.addAll(xs);
        } else {
            for (T x : xs) {
                if (ys.contains(x)) {
                    valueSet.add(x);
                }
            }
        }

        result.addAll(valueSet);

        return result;
    }

    /**
     * Return the size of the given collection.
     *
     * @param  c  The possibly-null collection to get the size of.
     */
    public static int size(Collection c) {
        return (null != c) ? c.size() : 0;
    }

    /**
     * Return the size of the given String.
     *
     * @param s  The possibly-null String to get the size of.
     */
    public static int size(String s) {
        return (null != s ? s.length() : 0);
    }

    public static boolean isEmpty(Collection collection) {
        return (collection != null ) ? collection.isEmpty() : true;
    }

    public static boolean isEmpty(Object[] args) {
        return args == null || args.length == 0;
    }

    public static boolean isEmpty(Map map) {
        return (map != null ) ? map.isEmpty() : true;
    }

    /**
     * Check if a CSV list is empty
     * 
     * @param csv CSV that is being checked for emptiness
     * @return true if the csv contains values; false otherwise
     */
    public static boolean isEmpty(String csv) {
        return (Util.isNullOrEmpty(csv) ) ? true : isEmpty(csvToList(csv, true));
    }

    /**
     * Return a list of the keys in a Map
     *
     * @param map Map containing keys
     * @return A List containing the keys from the Map
     */
    public static <T>List<T> mapKeys(Map<T, ?> map) {
        final ArrayList<T> out;
        if ( map == null )
            out = new ArrayList<T>();
        else
            out = new ArrayList<T>(map.keySet());

        return out;
    }

    /**
     * Return whether c1 contains any elements found in c2.
     *
     * @param  c1  The first collection to compare.
     * @param  c2  The second collection to compare.
     *
     * @return True if c1 contains any elements found in c2, false otherwise.
     */
    public static boolean containsAny(Collection c1, Collection c2) {

        boolean containsAny = false;

        if ((null != c1) && (null != c2)) {
            for (Object o : c2) {
                if (c1.contains(o)) {
                    containsAny = true;
                    break;
                }
            }
        }

        return containsAny;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Map utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a map value as a string.
     */
    public static String getString(Map map, String name) {

        String value = null;
        if (map != null) {
            Object o = map.get(name);
            if (o != null)
                value = o.toString();
        }
        return value;
    }

    public static boolean getBoolean(Map map, String name) {

        boolean value = false;
        if (map != null) {
            Object o = map.get(name);
            if (o != null)
                value = otob(o);
        }
        return value;
    }

    public static int getInt(Map map, String name) {

        int value = 0;
        if (map != null) {
            Object o = map.get(name);
            if (o != null)
                value = otoi(o);
        }
        return value;
    }

    public static Date getDate(Map map, String name) {

        Date date = null;
        if (map != null && name != null) {
            Object o = map.get(name);
            if (o instanceof Date)
                date = (Date)o;
            else if (o instanceof Long)
                date = new Date((Long)o);
            else if (o instanceof String) {
                // Attributes has logic to handle this, I don't
                // want to drag all that over here.  Should
                // really only be using this for Date or Long
            }
        }
        return date;
    }

    /**
     * Returns the value as a List of String.
     * If the current value is a String, it is assumed to be a CSV
     * and converted to a List.
     */
    public static List<String> getStringList(Map m, String name) {
        List<String> list = null;
        Object value = get(m, name);
        if (value instanceof Collection) {
            list = new ArrayList<String>();
            for (Object el : (Collection)value) {
                if (el != null)
                    list.add(el.toString());
            }
        }
        else if (value instanceof String) {
            // this doesn't return List<String> although
            // that is what it creates, why??
            list = (List<String>)Util.stringToList((String)value);
        }
        else if (value != null) {
            // shouldn't be here
            list = new ArrayList<String>();
            list.add(value.toString());
        }
        return list;
    }

    public static Object get(Map map, String name) {
        Object o = null;
        if ( map != null && name != null ) 
            o = map.get(name);
        return o; 
    }

    //////////////////////////////////////////////////////////////////////
    //
    // String utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Take a string that can contain formatting characters typical
     * for hand-edited XML and simplify it for rendering as HTML
     * or within an Ext component.
     *
     * @ignore
     * This was necessary for some object description strings since Ext
     * seems to collapse newlines which causes words separated only
     * by newlines to be merged.  Ext does seem to strip leading and
     * trailing whitespace but we'll do it here to so this method
     * can be used in several places.
     */
    public static String htmlify(String src) {

        String dest = src;
        if (src != null) {
            // start by trimming
            src = src.trim();

            // then replace newlines
            src = src.replace("\n", " ");
        }
        return src;
    }

    /**
     * Return a full name constructed from the given first and last names.
     *
     * More often than not, you should use {@link sailpoint.object.Identity#getDisplayableName()}
     * instead of this.
     */
    public static String getFullname(String first, String last) {

        StringBuilder sb = new StringBuilder();
        String filteredFirst = getString(first);
        String filteredLast = getString(last);
        if (null != filteredFirst)
            sb.append(filteredFirst);
        if ((null != filteredFirst) && (null != filteredLast))
            sb.append(" ");
        if (null != filteredLast)
            sb.append(filteredLast);

        return getString(sb.toString());
    }

    /**
     * Return the given String or null (if the given String is null or
     * zero-length).
     *
     * @param s  The String to filter.
     *
     * @return The given String or null (if the given String is null or
     *         zero-length).
     */
    public static String getString(String s)
    {
        if ((null != s) && (s.length() > 0)) {
            if ("null".equals(s))
                return null;
            return s;
        }

        return null;
    }

    /**
     * Collapse a value to null if it is logically null.
     * 
     * @ignore
     * This differs from getString because I didn't like the
     * handling for "null" where was that used??
     */
    public static Object nullify(Object obj) {

        if (obj instanceof String) {
            String str = ((String)obj).trim();
            if (str.length() == 0)
                obj = null;
        }
        return obj;
    }
    
    /**
     * Trim a string and collapse to null if it is empty. 
     */
    public static String trimnull(String str) {
        if (str != null) {
            str = str.trim();
            if (str.length() == 0)
                str = null;
        }
        return str;
    }

    /**
     * Capitalizes the first letter in the given String
     * Example: hElLo becomes HElLo
     * @param str string in need of some capitalization
     * @return capitalized string
     */
    public static String capitalize(String str) {

        char first = str.charAt(0);
        if (Character.isLowerCase(first))
            str = Character.toUpperCase(first) + str.substring(1);

        return str;
    }

    /**
     * Capitalizes the first letter in the given String and lowercases
     * the rest of the String
     * Example: hElLo becomes Hello
     * @param str string in need of help
     * @return capitalized and normalized string
     */
    public static String capitalizeAndNormalize(String str) {
        char first = str.charAt(0);
        if (Character.isLowerCase(first))
            str = Character.toUpperCase(first) + str.substring(1).toLowerCase();

        return str;
    }

    /**
     * Truncate the given string if it is longer than maxLength - if so, the
     * last 3 characters are replaced with an ellipsis (...).
     *
     * @param  s          The string to truncate.
     * @param  maxLength  The maximum length.
     */
    public static String truncate(String s, int maxLength) {

        if ((null != s) && (s.length() > maxLength)) {
            s = s.substring(0, maxLength-3) + "...";
        }

        return s;
    }

    /**
     * Similar to truncate(), but if truncation is necessary the beginning of
     * the string is trimmed rather than the end.
     *
     * @param  s          The string to truncate.
     * @param  maxLength  The maximum length.
     */
    public static String truncateFront(String s, int maxLength) {

        if ((null != s) && (s.length() > maxLength)) {
            // Grab the last maxLength-3 characters.
            s = "..." + s.substring(s.length()-(maxLength-3));
        }

        return s;
    }

    /**
     * Similar to the partition method from Google Guava
     * https://google.github.io/guava/releases/19.0/api/docs/com/google/common/collect/Lists.html#partition(java.util.List,%20int)
     * @param members The full list
     * @param maxSize The max size of the list
     * @param <T>
     * @return
     */
    public static <T> List<List<T>> partition(Collection<T> members, int maxSize)
    {
        List<List<T>> res = new ArrayList<>();

        List<T> internal = new ArrayList<>();

        for (T member : members)
        {
            internal.add(member);

            if (internal.size() == maxSize)
            {
                res.add(internal);
                internal = new ArrayList<>();
            }
        }
        if (internal.isEmpty() == false)
        {
            res.add(internal);
        }
        return res;
    }
    
    /**
     * Strip all occurrences of the given character off the front of the
     * given string.
     */
    public static String stripLeadingChar(String s, char c) {
        if (null == s) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean foundDifferentChar = false;
        for (int i=0; i<s.length(); i++) {
            char currentChar = s.charAt(i);
            if (c != currentChar) {
                foundDifferentChar = true;
            }

            if (foundDifferentChar) {
                sb.append(currentChar);
            }
        }
        
        return sb.toString();
    }

    /**
     * Strip all occurrences of the given character off the front and end of the given string. Occurences of c
     * in the middle of s are unaffected.
     * 
     * @param s the string
     * @param c the character to strip
     * @return the stripped string
     */
    public static String stripLeadingTrailingChar(String s, char c) {
        String cStr = String.valueOf(c);
        return StringUtils.stripEnd(StringUtils.stripStart(s, cStr), cStr);
    }
    
    /**
     * Takes a camel case string, splits the words out of it
     * and capitalizes each one.
     * Example: camelCaseString becomes Camel Case String
     */
    public static String splitCamelCase(String str) {
        if(str==null || str.equals("")) {
            return "";
        }
        StringBuffer sb = new StringBuffer();

        boolean allCaps = true;
        for(int i=0; i < str.length(); i++) {
            char c = str.charAt(i);
            if(Character.isLowerCase(c)) {
                allCaps = false;
                break;
            }
        }
        if ( allCaps ) return str;

        int start = 0;
        for(int i=0; i<str.length(); i++) {
            char c = str.charAt(i);

            //Check to see if the next character in the string is a capital letter.
            //If it is, we need to ignore this one because it's part of an acronym.
            if(i-1<=0) {
                continue;
            }else if(i+1<str.length()) {
                char d = str.charAt(i+1);
                if(Character.isUpperCase(d) && Character.isUpperCase(str.charAt(i-1)))
                    continue;

            } else if(i+1>=str.length()){
                continue;
            }

            if(Character.isUpperCase(c)) {
                if ( start == i ) continue;
                String s = str.substring(start, i);
                if(s!=null && !s.equals("")) {
                    s = capitalize(s);
                    sb.append(s + " ");
                }
                start = i;
            }
        }
        String s = str.substring(start, str.length());
        s = capitalize(s);
        sb.append(s);
        return sb.toString();
    }

    /**
     * Encode the given string into a valid java identifier (for example - does not start
     * with a number, does not include spaces, commas, etc...) by replacing any
     * invalid characters with the ASCII code of the character surrounded by
     * double underscores.  For example: 0 becomes __48__.
     *
     * Note that we allow spaces and dots to go through unescaped.
     *
     * @param  s  The string to convert to a valid java identifier.
     *
     * @return An encoded valid java identifier.
     */
    public static String encodeJavaIdentifier(String s) {

        String encoded = null;

        if (null != s) {
            StringBuilder sb = new StringBuilder();

            for (int i=0; i<s.length(); i++) {

                char c = s.charAt(i);

                if (0 == i) {
                    if (Character.isJavaIdentifierStart(c)) {
                        sb.append(c);
                    }
                    else {
                        sb.append(encodeJavaIdentifierChar(c));
                    }
                }
                else {
                    // Allow dotted identifier paths and spaces.
                    if (Character.isJavaIdentifierPart(c) || ('.' == c) || (' ' == c)) {
                        sb.append(c);
                    }
                    else {
                        sb.append(encodeJavaIdentifierChar(c));
                    }
                }
            }

            encoded = sb.toString();
        }

        return encoded;
    }

    /**
     * Decode the given encoded java identifier to remove any encoded invalid
     * characters and replace them with their original values.
     *
     * @param  s  The string to decode.
     *
     * @return The decoded version of the given encoded java identifier.
     *
     * @throws sailpoint.tools.Util.ParseException
     *    If the given string is not a valid encoded java identifier. The
     *    method encodeJavaIdentifier() should always produce valid identifiers
     *    that will not throw this exception.
     */
    public static String decodeJavaIdentifier(String s) throws ParseException {

        String decoded = null;

        if (null != s) {
            StringBuilder sb = new StringBuilder(s);

            for (int i=0; i<sb.length(); i++) {

                char c = sb.charAt(i);

                if ('_' == c && isEncodedCharAt(i, sb)) {
                    decodeJavaIdentifierChar(sb, i);
                }
            }

            decoded = sb.toString();
        }

        return decoded;
    }

    /*
     * Determine whether or not the sequence starting at the given index looks
     * like an encoded identifier.  Encoded identifiers have the following form:
     * __<ascii code>__
     * @return true if the specified sequence looks like an encoded character;
     * false otherwise
     */
    private static boolean isEncodedCharAt(int start, StringBuilder sb) {
        boolean isEncodedChar;
        // Found a possible encoded character, look for a second
        // underscore.  If we find it then use decodeJavaIdentifierChar()
        // to replace the encoded character in the buffer.
        int startOfEncoding = sb.indexOf("__", start);
        if (start == startOfEncoding) {
            int endOfEncoding = sb.indexOf("__", startOfEncoding + 2);
            if (endOfEncoding > startOfEncoding + 2) {
                String suspectedEncoding = sb.substring(startOfEncoding + 2, endOfEncoding);
                try {
                    int suspectedCodePoint = Integer.parseInt(suspectedEncoding);
                    isEncodedChar = Character.isValidCodePoint(suspectedCodePoint);
                } catch (NumberFormatException e) {
                    // If this isn't a valid integer then this isn't a valid encoded character
                    isEncodedChar = false;
                }
            } else {
                isEncodedChar = false;
            }
        } else {
            isEncodedChar = false;
        }
        
        return isEncodedChar;
    }

    /**
     * Encode a character that is invalid in a java identifier.
     *
     * @param  c  The invalid character to encode.
     *
     * @return A string with the given character encoded.
     */
    private static String encodeJavaIdentifierChar(char c) {
        // Return the ASCII code for the character.
        return "__" + Integer.valueOf((int) c).toString() + "__";
    }

    /**
     * Decode the encoded java identifier character from the given string that
     * starts at the given index and replace it with the decoded version.
     *
     * @param  sb        The StringBuilder in which to replace the encoded char.
     * @param  startIdx  The index in the given string from which to start
     *                   reading the decoded char.  This points to the first
     *                   underscore. The string will be decoded until another
     *                   double-underscore is found.
     *
     * @return The decoded java identifier character from the given string that
     *         starts at the given index.
     *
     * @throws sailpoint.tools.Util.ParseException
     *    If a character cannot be decoded from the given string. This will
     *    only be thrown if the given string was not encoded using
     *    encodeJavaIdentifier().
     */
    private static void decodeJavaIdentifierChar(StringBuilder sb, int startIdx)
        throws ParseException {

        StringBuilder buffer = new StringBuilder();
        boolean potentialEnd = false;

        for (int i=startIdx; i<sb.length(); i++) {

            char current = sb.charAt(i);

            // Skip the first two underscores (or puke on invalid input).
            if ((i == startIdx) || (i == startIdx+1)) {
                if ('_' != current) {
                    throw new ParseException("First two characters of encoded identifier should be '_'",
                                             sb.toString(), startIdx);
                }
                else {
                    continue;
                }
            }

            // If we find an underscore, either finish parsing if we had just
            // found one or remember that we found a potential end sequence.
            if ('_' == current) {
                if (potentialEnd) {

                    // Pull the last underscore off the buffer and convert it
                    // to a decoded character.
                    buffer.deleteCharAt(buffer.length()-1);
                    try {
                        int intVal = Integer.valueOf(buffer.toString());

                        // Replace length+4 (the 4 being all of the underscores that
                        // we don't capture in the buffer) with the decoded char.
                        sb.replace(startIdx, startIdx+buffer.length()+4, "" + (char) intVal);
                    } catch (NumberFormatException e) {
                        // rethrow as a parse exception
                        throw new ParseException("Invalid encoded value", buffer.toString(),
                                                 startIdx, e);
                    }
                    // Done.
                    return;
                }
                else {
                    potentialEnd = true;
                }
            }
            else {
                // Clear potentialEnd because we're not looking at an underscore.
                potentialEnd = false;
            }

            // Stick the current character in the buffer.
            buffer.append(current);
        }

        throw new ParseException("Unterminated encoded character", sb.toString(), startIdx);
    }

    /**
     * A runtime exception that is thrown when decoding fails.
     */
    public static class ParseException extends RuntimeException {
        private static final long serialVersionUID = 3477614293373278551L;

        public ParseException(String msg, String s, int startIdx) {
            super(msg + ": " + s + "(startIdx = " + startIdx + ")");
        }
        
        public ParseException(String msg, String s, int startIdx, Exception e) {
            super(msg + ": " + s + "(startIdx = " + startIdx + ")", e);
        }
    }

    /**
     * Convert an primitive integer value into a String.
     * This does not really save much space, but its the obvious
     * inverse to atoi.
     */
    static public String itoa(int i) {
        return new Integer(i).toString();
    }

    static public String ltoa(long i) {
        return new Long(i).toString();
    }

    static public String ftoa(float i) {
        return new Float(i).toString();
    }

    /**
     * Convert a String value into a primitive integer value.
     * Truncate doubles and return the floor.
     * Tolerate null strings, and invalid integer strings and return 0.
     */
    static public int atoi(String a) {

        int i = 0;
        if (a != null && a.length() > 0) {
            try {
                int dotIndex = a.indexOf('.');
                if (dotIndex > 0)
                    a = a.substring(0, dotIndex);

                i = Integer.parseInt(a);
            }
            catch (NumberFormatException e) {
                // ignore, return 0
            }
        }

        return i;
    }

    /**
     * Convert a String value into a primitive integer value.
     * If the string is invalid, the value of the "def" argument is returned.
     */
    static public int atoi(String a, int def) {

        int i = def;
        if (a != null && a.length() > 0) {
            try {
                i = Integer.parseInt(a);
            }
            catch (NumberFormatException e) {
                // ignore, return default
            }
        }

        return i;
    }

    static public long atol(String a) {

        long i = 0;
        if (a != null) {
            try {
                i = Long.parseLong(a);
            }
            catch (NumberFormatException e) {
                // ignore, return 0
            }
        }

        return i;
    }

    static public float atof(String a) {

        float f = 0.0f;
        if (a != null) {
            try {
                f = Float.parseFloat(a);
            }
            catch (NumberFormatException e) {
                // ignore, return 0
            }
        }

        return f;
    }

    static public boolean atob(String a) {

        boolean b = false;
        if (a != null) {
            try {
                b = Boolean.parseBoolean(a);
            }
            catch (Exception ex) {
            }
        }
        return b;
    }

    static public String otoa(Object o) {

        return (o != null) ? o.toString() : null;
    }

    static public boolean otob(Object o) {
        boolean val = false;

        if ( o != null ) {
            if (o instanceof Boolean)
                val = ((Boolean) o).booleanValue();
            else
                val = o.toString().equalsIgnoreCase("true");
        }

        return val;
    }

    static public int otoi(Object o) {
        int val = 0;

        if ( o != null ) {
            if (o instanceof Number)
                val = ((Number) o).intValue();
            else
                val = atoi(o.toString());
        }

        return val;
    }
    
    static public long otolo(Object o) {
        long val = 0L;
        
        if (o != null) {
            if (o instanceof Number) {
                val = ((Number)o).longValue();
            } else {
                val = atol(o.toString());
            }
        }
        return val;
    }

    /**
     * Convert an object to its string representation. This will convert Lists
     * and Collections to comma-separated lists. Any other object is simply
     * returned as its toString() representation.
     *
     * @param  o  The object to convert to a string.
     *
     * @return The string representation of the given object, or null if the
     *         given object is null.
     */
    @SuppressWarnings("unchecked")
    public static String otos(Object o) {

        String s = null;

        if (null != o) {
            if (o instanceof List)
                s = listToCsv((List) o);
            else if (o instanceof Collection)
                s = listToCsv(new ArrayList((Collection) o));
            else
                s = o.toString();
        }

        return s;
    }
    
    /**
     * Convert an object to a List of Strings. This is useful when contending
     * with arguments that may be coming in as CSVs or as Lists.
     * @param o Either a List<String> or a CSV list
     * @return A new List containing all the elements in the specified object.
     * Note that if the specified object is a List this method returns a new List 
     * containing all the elements of the one that was passed in.
     */
    public static List<String> otol(Object o) {
        List<String> l = null;
        if (null != o) {
            if (o instanceof List) {
                l = new ArrayList<String>();
                l.addAll((List)o);
            } else if (o instanceof String){
                l = new ArrayList<String>();
                List<String> csvAsList = csvToList((String)o, true);
                if(csvAsList != null) {
                    l.addAll(csvAsList);
                }
            } else {
                return Collections.<String>emptyList();
            }
        }
        return l;
    }

    /**
     * Convert an object to a Map of <String, Object>.
     * @param o Either a Map<String, Object> or a string representation of a map.
     * @return A new HashMap containing all the elements in the specified object.
     */
    public static Map<String, Object> otom(Object o) {
        Map<String, Object> m = null;
        if (null != o) {
            m = new HashMap<String, Object>();
            if (o instanceof Map) {
                m.putAll((Map<String, Object>) o);
            } else if (o instanceof String) {
                m.putAll(stringToMap(o.toString()));
            }
        }
        return m;
    }

    /**
     * Trims trailing whitespace from a string.
     * String.trim() can be used if you want to trim from both sides
     * of the string.
     */
    static public String trimWhitespace(String src) {

        String dest = null;

        if (src != null) {

            int end;
            for (end = src.length() - 1 ; end >= 0 ; end--) {
                char c = src.charAt(end);

                // Under J++ 0x10 and 0x13 aren't considered whitespace,
                // might happen if the string came out of a file that wasn't
                // read in "translated" mode?
                if (!Character.isSpaceChar(c) && c != '\012' && c != '\015')
                    break;
            }

            if (end >= 0) {
                // second arg is length not index!
                dest = src.substring(0, end+1);
            }
        }

        return dest;
    }

    /**
     * Deletes any whitespace from the string
     */
    static public String deleteWhitespace(String src) {
        String dest = null;
        if(src!=null) {
            dest = "";
            String[] parts = src.split(" ");
            for(int i=0; i<parts.length; i++) {
                if(parts[i]!=null) {
                    dest += parts[i];
                }
            }
        }
        return dest;
    }
    /**
     * This method strips leading and trailing whitespace and guarantees that there is
     * at most one space between characters in the output. For example,
     * <pre>"What    does    this    do?"</pre>  becomes
     * <pre>"What does this do?"</pre>
     * @param str string in need of compression
     * @return compressed string
     */
    static public String compressWhiteSpace(String str) {
        StringBuilder compressedStr = new StringBuilder();
        String trimmedStr = str.trim();

        char previousChar = 'x';

        for (int i = 0; i < trimmedStr.length(); ++i) {
            char currentChar = trimmedStr.charAt(i);

            while (previousChar == ' ' && currentChar == ' ') {
                ++i;
                currentChar = trimmedStr.charAt(i);
            }

            compressedStr.append(currentChar);
            previousChar = currentChar;
        }

        return compressedStr.toString();
    }

    /**
     * Trim (from leading and trailing) both whitespace
     * characters and slash (/) characters.   This is very
     * efficient if there are no leading/trailing characters
     * which need trimmed.
     * @param str the string to trim
     * @return the trimmed string
     */
    static public String trimWhiteSpaceAndSlash(String str) {
        if (str == null) {
            return null;
        }

        int beg;
        for(beg = 0; beg < str.length() &&
                ( str.charAt(beg) <= ' ' || str.charAt(beg) == '/')
            ; ++beg);

        int end;
        for(end = str.length(); end > beg &&
                ( str.charAt(end-1) <= ' ' || str.charAt(end-1) == '/')
                ; --end);

        if (beg == 0 && end == str.length()) {
            return str;
        }

        return str.substring(beg, end);
    }

    /**
     * Remove "xml garbage" from a string that might have started out
     * life in an XML element. These commonly have leading whitespace
     * that was used to indent the text in the XML file, but once this
     * has been imported it is no longer necessary.
     *
     * Also collapse empty strings to null for contexts where we select
     * from several values based on their null-ness.
     *
     * @ignore
     * NOTE: This isn't enough!  Also need to be sifting through
     * this looking for embedded newlines followed by spaces and
     * collapsing out this "insignificant whitespace".
     *
     * Sigh, also must avoid triggering unnecessary Hibernate updates
     * by checking to see if the string actually needs fixing.
     */
    public static String unxml(String src) {

        String fixed = src;

        if (src != null) {
            if (src.length() == 0) {
                // ok to collapse
                fixed = null;
            }
            else {
                // assume if it has no leading whitespace it doesn't
                // need fixing, this isn't enough, but catches most of
                // the cases
                char first = src.charAt(0);
                if (Character.isSpaceChar(first) ||
                        first == '\n' || first == '\r') {

                    fixed = src.trim();
                    if (fixed.length() == 0)
                        fixed = null;
                }
            }
        }
        return fixed;
    }

    /**
       Returns the last token in a string of tokens
       separated by delimiter argument.
     */
    public static String strstr(String inString, String delimeter) {

        int index = inString.lastIndexOf(delimeter);
        return new String(inString.substring(++index));

    }

    /**
     * Process a string which may contain references to variables
     * and substitute the values of those variables.
     * <p>
     * If a variable is not defined, it silently expands to nothing.
     * <p>
     * A variable is referenced with the syntax "$(x)" where "x" is
     * the name of the variable. Values for variables are supplied
     * through a Map.
     */
    public static String expandVariables(String src, Map<String, Object> variables) {

        String expanded = null;
        if (src != null) {
            if (src.indexOf('$') == -1) {
                // no references, optimize
                expanded = src;
            }
            else {
                StringBuffer b = new StringBuffer();

                int max = src.length();
                for (int i = 0 ; i < max ; i++) {
                    char ch = src.charAt(i);
                    if (ch != '$')
                        b.append(ch);
                    else {
                        i++;
                        if (i < max) {
                            // what's a better convention, $$ or \$ ?
                            // I don't like this, it means we can't
                            // recursively expand references
                            char next = src.charAt(i);
                            if (next == '$') {
                                // doubled $, collapse
                                b.append(ch);
                            }
                            else if (src.charAt(i) != '(') {
                                // a dollar without (, not a reference
                                b.append(ch);
                                b.append(next);
                            }
                            else {
                                // smells like a reference
                                i++;
                                int start = i;
                                int end = start;

                                while (end < max && src.charAt(end) != ')')
                                    end++;

                                if (end > start) {
                                    String name = src.substring(start, end);
                                    if (variables != null) {
                                        Object obj = variables.get(name);
                                        if (obj != null)
                                            b.append(obj.toString());
                                    }
                                }
                                i = end;
                            }
                        }
                    }
                }

                // let a reference to a null variable stay null
                if (b.length() > 0)
                    expanded = b.toString();
            }
        }
        return expanded;
    }

    /**
     * Process a string to escape newline characters.
     * <p>
     * Ordinarily done if you want to store a string as a value
     * in a property file, where the newline marks the end of the value.
     */
    public static String escapeNewlines(String src) {
        return escapeNewlines(src, "\\n", "\\r");
    }

    /**
     * Escape newlines from the string into the HTML escaped newline and
     * carriage return.
     */
    public static String escapeHTMLNewlines(String src) {
        return escapeNewlines(src, "&#xA;", "&#xD;");
    }

    /**
     * Process a string to escape newline characters.
     */
    public static String escapeNewlines(String src, String nRepl, String rRepl) {

        String escaped = null;

        if (src != null) {
            StringBuffer b = new StringBuffer();
            int max = src.length();
            for (int i = 0 ; i < max ; i++) {
                char ch = src.charAt(i);
                if (ch == '\n')
                    b.append(nRepl);
                else if (ch == '\r')
                    b.append(rRepl);
                else
                    b.append(ch);
            }
            escaped = b.toString();
        }
        return escaped;
    }

    public static List<String> delimToList(String delim,
                                           String src, boolean filterEmpty) {

        List<String> list = new ArrayList<String>();
        if (src != null) {
            StringTokenizer st = new StringTokenizer(src, delim);
            while (st.hasMoreElements()) {
                String tok = st.nextToken().trim();
                if (!filterEmpty || tok.length() > 0)
                    list.add(tok);
            }
        }
        return list;
    }

    /**
     * Convert a comma delimited string into a List of strings.
     */
    public static List<String> csvToList(String src) {
        return csvToList(src, false);
    }

    public static List<String> csvToList(String src, boolean filterEmpty) {
        List<String> list = new ArrayList<String>();
        if (src != null) {
            list = RFC4180LineParser.parseLine(",", src, filterEmpty);
        }
        return list;
    }

    public static String[] csvToArray(String src) {
        List<String> list = csvToList(src);
        return list.toArray(new String[list.size()]);
    }

    public static Set<String> csvToSet(String src, boolean filterEmpty) {
        List<String> list = csvToList( src, filterEmpty);
        return new HashSet<String>(list);
    }

    public static String setToCsv(Set<?> set) {
        return listToCsv(Arrays.asList(set.toArray()));
    }

    /**
     * Convert a list of strings into a comma delimited list.
     */
    public static String listToCsv(List list) {
        return listToCsv(list, false);
    }

    public static String listToCsv(List list, boolean filterEmpty) {
        String csv = listToQuotedCsv(list, '"', filterEmpty, true);
        return csv;
    }

    /**
     * Convert a list of strings into a comma delimited list of Strings wrapped in the specified
     * type of quote. If null as provided as the quote type no quotes are applied.
     * @param list List of objects to be converted into a list of strings
     * @param quoteChar Quote character in which to wrap the strings.  Can be ', ", or null.
     *                  If null is specified no quotes will be applied
     * @param filterEmpty True to filter blank strings out of the list. False to include them
     * @return String representation of a comma delimited list of Strings wrapped in the specified type of quote
     */
    public static String listToQuotedCsv(List list, Character quoteChar, boolean filterEmpty) {
        return listToQuotedCsv(list, quoteChar, filterEmpty, false);
    }

    /**
     * This is similar to listToQuoteCsv but allows the specification
     * of the conditionally quoted individual values in a csv that have
     * embedded commas or double quotes.
     *
     * @ignore djs:  When conditionallyQuote is true it prevents ALL
     * strings from being quoted, and instead will only
     * quote ( escape ) the values that actually contain embedded commas or double quotes.
     *
     * @see #listToQuotedCsv(List, Character, boolean)
     *
     * @return String representation of the list
     */
    public static String listToQuotedCsv(List list, Character quoteChar, boolean filterEmpty,
                                         boolean conditionallyQuote) {

        return listToQuotedCsv(list, quoteChar, filterEmpty, conditionallyQuote, true);
    }
    
    /**
     * This is similar to listToQuoteCsv but allows the specification
     * of the conditionally add space char between the csv values.
     *
     *@param list                  List of objects to be converted into a list of strings
     *@param quoteChar             Quote character in which to wrap the strings.  Can be ', ", or null.
     *                             If null is specified no quotes will be applied
     *@param filterEmpty           True to filter blank strings out of the list. False to include them
     *@param conditionallyQuote    If true it prevents ALL strings from being quoted, and instead will 
     *                             only quote ( escape ) the values that actually contain embedded commas or double-quotes.
     *@param conditionallyAddSpace If true it appends a space before adding the next value.
     *
     *@return String representation of the list
     */
    public static String listToQuotedCsv(List list, Character quoteChar, boolean filterEmpty,
                                         boolean conditionallyQuote, boolean conditionallyAddSpace) {

        String csv = null;

        if (list != null) {
            StringBuffer b = new StringBuffer();
            int added = 0;
            for (int i = 0 ; i < list.size() ; i++) {
                Object o = list.get(i);
                String s = ((o != null) ? o.toString() : null);
                if (!filterEmpty || (s != null && s.length() > 0)) {
                    if (added > 0) {
                        b.append(",");
                        if (conditionallyAddSpace){
                            b.append(" ");
                        }
                    }
                    if ( addQuote(quoteChar, s, conditionallyQuote) ) {
                        b.append(quoteChar);
                    }
                    if (s != null) {
                        b.append(s.replace("\"", "\"\""));
                    }
                    if ( addQuote(quoteChar, s, conditionallyQuote) ) {
                        b.append(quoteChar);
                    }
                    added++;
                }
            }
            csv = b.toString();
            // jsl - this is usually expected to collapse to null if the
            // list was empty
            if (csv != null && csv.length() == 0)
                csv = null;
        }
        return csv;
    }
    public static String listToRfc4180Csv( List<?> list ) {
        Rfc4180CsvBuilder builder = new Rfc4180CsvBuilder();

        if ( list != null ) {
            StringBuffer b = new StringBuffer();
            for ( Object object : list ) {
                String stringValue = ( ( object != null ) ? object.toString() : null );
                builder.addValue( stringValue );
            }
        }
        return builder.build();
    } 


    // Historically we've either quoted or not quoted have to use some messy logic to
    // determine our conditionally quote behavior
    static boolean addQuote(Character quote, String value, boolean conditionallyQuote ) {
        if ( quote != null ) {
            // We have to surround values that have either a comma or a double quote, by spec.
            // Should this check for double quote specifically or whatever the passed character quote is?
            // This is how it used to be pre-IIQPB-463 so restoring it.
            // TODO: Get rid of all these version of listToQuteCsv and just use standards everywhere.
            return !conditionallyQuote ||
                    ((value != null) && (value.contains(",") || value.contains(quote.toString())));
        }
        return false;
    }

    /**
     * Return a pipe (|) delimited string with all values in the given list.
     * Each list element will be surrounded by pipes. For example, [A, B, C]
     * would return "|A|B|C|".
     *
     * @param  list  The List to convert to a pipe delimited string.
     *
     * @return A pipe delimited string with all values in the given list, or
     *         null if the given list is null.
     */
    public static String listToPipeDelimited(List list) {

        String piped = null;

        if (list != null) {
            StringBuffer b = new StringBuffer();
            for (int i = 0 ; i < list.size() ; i++) {
                Object o = list.get(i);
                String s = ((o != null) ? o.toString() : null);
                // If this is the first element, put a pipe in front of it.
                if (i == 0) {
                    b.append("|");
                }
                b.append(s).append("|");
            }
            piped = b.toString();
            // jsl - this is usually expected to collapse to null if the
            // list was empty
            if (piped != null && piped.length() == 0)
                piped = null;
        }
        return piped;
    }

    /**
     * Join the items in a collection into a String just as the Perl join
     * function does.
     *
     * @param c the Collection to join
     * @param delimiter the delimeter to place between the collection elements
     * @return joined string
     */
    public static String join(Collection c, String delimiter) {
        if (null == c)
            return null;

        StringBuffer buf = new StringBuffer();
        Iterator iter = c.iterator();
        while ( iter.hasNext() ) {
            buf.append(iter.next());
            if ( iter.hasNext() )
                buf.append(delimiter);
        }
        return buf.toString();
    }

    /**
     * First joins the list of string values using the specified delimiter,
     * then truncates the resulting string to the specified character
     * maximum.
     * @param c The collection to join.
     * @param delimiter The join character.
     * @param maxChars The maximum characters after truncation.
     * @return The joined and truncated string.
     */
    public static String truncatedJoin(Collection c, String delimiter, int maxChars) {
        return truncate(join(c, delimiter), maxChars);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // File utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Read the contents of a file and return it as a byte array.
     */
    static public byte[] readBinaryFile(String name) throws GeneralException {

        byte[] bytes = null;

        try {
            String path = findFile(name);
            if (path == null) {
                String msg = "Unable to locate file '" + name + "'.";
                throw new GeneralException(msg);
            }

            // should be cleaner here with exception handling?
            FileInputStream fis = new FileInputStream(path);
            try {
                int size = fis.available();
                bytes = new byte[size];
                fis.read(bytes);
            }
            finally {
                try {
                    fis.close();
                }
                catch (IOException e) {
                    // ignore these
                }
            }
        }
        catch (IOException e) {
            throw new GeneralException(e);
        }

        return bytes;
    }
    
    public static byte[] readBinaryInputStream(InputStream inputStream) throws GeneralException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[2048];
            int numRead;
            
            while ((numRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, numRead);
            }            
            
            return out.toByteArray();
        } catch (IOException ex) {
            throw new GeneralException(ex);
        } finally {
            try { inputStream.close(); } catch (Exception ignored) { }
        }
    }

    static public byte[] readBinaryFile(File f) throws GeneralException {

        byte[] bytes = null;

        try {
            // should be cleaner here with exception handling?
            FileInputStream fis = new FileInputStream(f);
            try {
                int size = fis.available();
                bytes = new byte[size];
                fis.read(bytes);
            }
            finally {
                try {
                    fis.close();
                }
                catch (IOException e) {
                    // ignore these
                }
            }
        }
        catch (IOException e) {
            throw new GeneralException(e);
        }

        return bytes;
    }

    public static String readInputStream(InputStream inputStream) throws GeneralException {
        return bytesToString(readBinaryInputStream(inputStream));
    }
    
    /**
     * Read the contents of a file and return it as a String.
     */
    static public String readFile(String name) throws GeneralException {
        byte[] bytes = readBinaryFile(name);
        return bytesToString(bytes);
    }

    static public String readFile(File f) throws GeneralException {
        byte[] bytes = readBinaryFile(f);
        return bytesToString(bytes);
    }

    /**
     * Read the contents of a Resource and return it as a String. Resources
     * such as WEB-INF/database/someFile.db may require WEB-INF/database to be
     * added to the CLASSPATH
     */
    static public String readResource(String resource) throws GeneralException {
        try {
            ClassLoader loader = Util.class.getClassLoader();
            if (loader != null) { // uncertain when it would ever be null
                InputStream inputStream = loader.getResourceAsStream(resource);
                if (inputStream != null) { // null InputStream is returned when the Resource isn't found
                    return readInputStream(inputStream);
                }
            }
            // else return null
            return null;
        } catch (SecurityException se) {
            // accessing the ClassLoader might invoke this. Some consumers might squelch the
            // exception, so log.error it
            log.error("Exception retrieving resource: " + resource, se);
            throw new GeneralException(se);
        }
    }

    static public String bytesToString(byte[] bytes) throws GeneralException {
        String string = null;
        if (bytes != null && bytes.length > 0) {
            try {
                string = new String(bytes, getDetectedCharset(bytes, "UTF-8"));
            }
            catch (UnsupportedEncodingException ex) {
                throw new GeneralException(ex);
            }
        }
        return string;
    }

    /**
     * Makes a best guess at the charset.
     *
     * @param input the byte string to test
     * @return the name of the charset used for the input bytes
     */
    public static String getDetectedCharset(byte[] input, String defaultCharset) {
        if (universalDetector == null) { // shouldn't happen, but be safe anyways.
            universalDetector = new UniversalDetector(null);
        }
        universalDetector.reset();
        universalDetector.handleData(input, 0, input.length);
        universalDetector.dataEnd();
        String charset = universalDetector.getDetectedCharset();
        if (isNullOrEmpty(charset)) {
            return defaultCharset;
        }
        return charset;
    }

    /**
     * Store the contents of a byte array to a file.
     */
    static public void writeFile(String name, byte[] contents)
    throws GeneralException {

        try {
            String path = findOutputFile(name);
            FileOutputStream fos = new FileOutputStream(path);
            try {
                fos.write(contents, 0, contents.length);
            }
            finally {
                try {
                    fos.close();
                }
                catch (IOException e) {
                    // ignore these
                }
            }
        }
        catch (IOException e) {
            throw new GeneralException(e);
        }
    }

    static public void writeTestFile(String name, byte[] contents) {
        if (name != null && contents != null) {
            try {
                writeFile(name, contents);
            }
            catch (GeneralException e) {
                System.out.println(e.toString());
            }
        }
    }

    /**
     * Store the contents of a String in a file.
     */
    static public void writeFile(String name, String contents)
    throws GeneralException {

        if (name != null && contents != null) {
            try {
                byte[] bytes = contents.getBytes("UTF-8");
                writeFile(name, bytes);
            } catch (UnsupportedEncodingException ex) {
                throw new GeneralException(ex);
            }
        }
    }

    static public void writeTestFile(String name, String contents) {
        if (name != null && contents != null) {
            try {
                writeFile(name, contents);
            }
            catch (GeneralException e) {
                System.out.println(e.toString());
            }
        }
    }

    /**
     * Copies one file to another.
     * 
     * @ignore
     * I'm sort of suprised the JDK doesn't have something to do this.
     * If this were used with large files, we'd want to do iterate
     * over a fixed block size.
     */
    static public void copyFile(String src, String dest)
    throws GeneralException, IOException {

        String srcpath = findFile(src);
        if (srcpath == null) {
            String msg = "Unable to locate file '" + src + "'.";
            throw new GeneralException(msg);
        }

        String destpath = findOutputFile(dest);

        // should be cleaner here with exception handling?
        FileInputStream fis = new FileInputStream(srcpath);
        byte[] bytes = null;
        try {
            int size = fis.available();
            bytes = new byte[size];
            fis.read(bytes);

            FileOutputStream fos = new FileOutputStream(destpath);
            try {
                fos.write(bytes);
            }
            finally {
                try {
                    fos.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
        finally {
            try {
                fis.close();
            }
            catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Get the name of the user that is logged in to the host machine.
     */
    static public String getMachineUser() {

        return System.getProperty("user.name");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Date utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Try to derive a Date from a random object.
     * If this is a String then we have to use the default format.
     */
    static public Date getDate(Object o) {

        Date date = null;

        if (o instanceof Date)
            date = (Date)o;

        else if (o instanceof Long)
            date = new Date((Long)o);

        else if (o instanceof Integer)
            date = new Date((long)((Integer)o).intValue());

        else if (o instanceof String) {

            String s = ((String)o).trim();
            // ignore empty strings which can be posted
            // by the various UI date pickers
            if (s.length() > 0) {
                // support both the string representation of a long
                // which is posted by the Ext date pickers as well
                // as typical date strings
                int nonDigits = 0;
                for (int i = 0 ; i < s.length() ; i++) {
                    char ch = s.charAt(i);
                    if (!Character.isDigit(ch)) {
                        nonDigits++;
                        break;
                    }
                }

                if (nonDigits == 0) {
                    long l = Util.atol(s);
                    date = new Date(l);
                }
                else {
                    try {
                        date = stringToDate(s);
                    } catch (java.text.ParseException pe ) {
                        // TODO: don't be silent.. should this method throw?
                        // throw new IllegalArgumentException(attributeName + " is not a parseable String: " + System.getProperty("line.separator") + pe.toString());
                        System.out.println(pe.toString());
                    }
                }
            }
        }

        return date;
    }

    /**
     * Format a Date value as a String, using the usual "American"
     * format with the current time zone.
     */
    static public String dateToString(Date src) {
        return dateToString(src, null);
    }

    static public String dateToString(Date src, String format ) {
        return dateToString(src, format, TimeZone.getDefault());
    }

    static public String dateToString(Date src, String format, TimeZone tz ) {
        DateFormat f = null;

        if ( format == null )
            format = "M/d/y H:m:s a z";

        f = new SimpleDateFormat(format);

        f.setTimeZone(tz);
        return f.format(src);
    }

    /**
     * Format a duration in seconds as hh:mm:ss
     */
    static public String durationToString(int duration) {

        int hh = duration / 3600;
        int mm = (duration % 3600) / 60;
        int ss = duration % 60;

        return String.format("%d:%02d:%02d", hh, mm, ss);
    }

    static public Date incrementDateByDays(Date date, long duration)
    {
        final Date incrementedDate;
        incrementedDate = new Date(date.getTime() + (duration * MILLIS_PER_DAY));
        return incrementedDate;
    }

    static public Date incrementDateByMinutes(Date date, int minutes)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }
    static public Date incrementDateBySeconds(Date date, int seconds)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, seconds);
        return cal.getTime();
    }

    static public Date getBeginningOfDay(Date day) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    static public Date getEndOfDay(Date day) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    /**
     * Get a DateFormat instance that is appropriate for the specified locale.
     *
     * @param dateFormat Date format style to use. Optional if date is not needed.
     * @param timeFormat Time format style to use. Optional if time is not needed.
     * @param locale Locale to use for formatting the date/time.
     * @return Localized DateFormat
     */
    static public DateFormat getDateFormatForLocale(Integer dateFormat, Integer timeFormat, Locale locale) {
        DateFormat df;
        Locale myLocale = locale != null ? locale : Locale.getDefault();

        if (dateFormat != null && timeFormat != null) {
            df = DateFormat.getDateTimeInstance(dateFormat.intValue(), timeFormat.intValue(), myLocale);
        } else if (dateFormat != null && timeFormat == null) {
            df = DateFormat.getDateInstance(dateFormat.intValue(), myLocale);
        } else if (dateFormat == null && timeFormat != null) {
            df = DateFormat.getTimeInstance(timeFormat.intValue(), myLocale);
        } else {
            df = DateFormat.getDateTimeInstance();
        }

        return df;
    }

    static public String dateToString(Date src, Integer dateFormat, Integer timeFormat) {
            return dateToString(src, dateFormat, timeFormat, TimeZone.getDefault(), Locale.getDefault());
    }


    static public String dateToString(Date src, Integer dateFormat, Integer timeFormat, TimeZone timeZone, Locale locale) {
        if(src==null)
            return "";

        DateFormat f = getDateFormatForLocale(dateFormat, timeFormat, locale);
        f.setTimeZone(timeZone != null ? timeZone : TimeZone.getDefault());

        return f.format(src);
    }

    static public Date baselineDate(Date src) {
        return baselineDate(src, null);
    }
    
    /**
     * Get the baseline date for the source date. If timezone is passed, will use the
     * day of month this date will represent in that timezone. Otherwise, use default timezone.
     * 
     * @param src  Date to baseline
     * @param timeZone Optional. Timezone to use when getting day of month.
     * @return Baseline date with no time values
     */
    static public Date baselineDate(Date src, TimeZone timeZone) {
        Calendar cal = (timeZone != null) ? Calendar.getInstance(timeZone) : Calendar.getInstance();
        cal.setTime(src);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }

    static public Date dateBetween(Date startDate, Date endDate) {
        Calendar end = Calendar.getInstance();
        Calendar start = Calendar.getInstance();
        end.setTime(endDate);
        start.setTime(startDate);

        long endL = end.getTimeInMillis() + end.getTimeZone().getOffset( end.getTimeInMillis() );
        long startL = start.getTimeInMillis() + start.getTimeZone().getOffset( start.getTimeInMillis() );

        long diff = (endL - startL);

        Calendar middle = Calendar.getInstance();
        middle.setTimeInMillis(startDate.getTime() + diff/2);

        return middle.getTime();
    }

    /**
     * Determine whether the given date is between two other dates
     * @param testDate Date that we want to test
     * @param startDate Beginning of the test interval
     * @param endDate End of the test interval
     * @return true if the test Date is between the beginning and end, inclusive; false otherwise
     */
    static public boolean isDateBetween(Date testDate, Date startDate, Date endDate) {
        if (testDate == null || startDate == null || endDate == null) {
            return false;
        }
        long startTime = startDate.getTime();
        long endTime = endDate.getTime();
        long time = testDate.getTime();
        return time >= startTime && time <= endTime; 
    }

    /**
     * Determine whether the testDate is greater than or equal to the startDate
     * @param testDate Date that we want to test
     * @param startDate Beginning of the test interval
     * @return true if the test Date is after the start date; false otherwise
     */
    static public boolean isDateAfter(Date testDate, Date startDate) {
        if (testDate == null || startDate == null) {
            return false;
        }
        long startTime = startDate.getTime();
        long time = testDate.getTime();
        return time >= startTime; 
    }

    /**
     * @ignore
     * Note: Why don't we return List<String> here?
     * I have noticed that if I change it to List<String> some
     * classes depending on this break. Those classes are
     * already broken. We should fix that.
     */
    static public List stringToList(String value) {
        List<String> list = new ArrayList<String>();
        if (null != Util.getString(value))
        {
            // Trim off the brackets, if they exist (i.e [val1, val2, ..., valn])
            String tempVal = value;
            if (tempVal.length() > 1 && tempVal.startsWith("[") && tempVal.endsWith("]")){
                tempVal = tempVal.substring(1, tempVal.length() - 1);
            }

            String[] parts = tempVal.split(",");
            if (parts.length > 0)
            {
                for (int i=0; i<parts.length; i++)
                {
                    String val = parts[i].trim();
                    if (val.length() > 0)
                        list.add(val);
                }
            }
        }

        return list;
    }

    /**
     * Convert the given Map to a String.
     */
    @SuppressWarnings("unchecked")
    public static String mapToString(Map map) {
        if (null != map)
            return map.toString();
        return "{}";
    }

    /** 
     * Convert the string representation for a map into a Map object 
     */
    static public Map stringToMap(String value) {
        Map<String, String> map = new HashMap<String, String>();

        /** Value must take the form of [key,value;key,value;key,value]... **/
        if (null != Util.getString(value)) {
            String[] parts = value.split(",");

            //A single element means that we didn't have a semi-colon to split on.
            if (parts.length == 1)
            {
                String[] part = parts[0].trim().split("=");
                if(part.length == 2) {
                    String k = part[0];
                    String v = part[1];
                    // Trim off the brackets.
                    String key = k.substring(1, k.length());
                    String val = v.substring(0, v.length()-1);
                    if (key.length() > 0 && val.length() > 0) {
                        map.put(key, val);
                    }
                }
            } else {
                for (int i=0; i<parts.length; i++)
                {
                    String[] part = parts[i].trim().split("=");

                    if(part.length == 2) {
                        String k = part[0];
                        String v = part[1];
                        String key;
                        String val;
                        // Trim off the brackets.
                        if (i == 0) {
                            key = k.substring(1);
                            val = v;
                        }
                        else if (i == parts.length-1) {
                            key = k;
                            val = v.substring(0, v.length() - 1);
                        }
                        else {
                            key = k;
                            val = v;
                        }
                        if (val.length() > 0 && key.length() > 0) {
                            map.put(key, val);
                        }
                    }
                }
            }
        }
        return map;
    }

    /**
     * Convert the string representation for a date into a Date object.
     * The string can be of the following forms:
     *
     *        M/d/y H:m:s z
     *         M/d/y H:m:s
     *         M/d/y
     */
    static public Date stringToDate(String src)
    throws java.text.ParseException {

        // convenience hack
        Date d = null;
        if(src==null)
            return d;
        if (src.equals("now"))
            d = new Date();
        else {
            // SimpleDateFormat doesn't have a way to make various fields
            // optional. So, we try to be smart and pre-parse the string to
            // see what fields exist, and select a suitable format string.

            // assume there will always be at least a date
            boolean isTime = false;
            boolean isTimezone = false;
            boolean afterTime = false;
            boolean isAmPm = false;

            for (int i = 0 ; i < src.length() ; i++) {
                char c = src.charAt(i);
                if (c == ':')
                    isTime = true;
                else if (Character.isSpaceChar(c) && isTime)
                    afterTime = true;
                else if (afterTime)
                    isTimezone = true;
                if (Character.isSpaceChar(c) && isTimezone)
                    isAmPm = true;
            }

            SimpleDateFormat f = null;

            if (isAmPm)
                f = new SimpleDateFormat("M/d/y H:m:s a z");
            else if (isTimezone)
                f = new SimpleDateFormat("M/d/y H:m:s z");
            else if (isTime)
                f = new SimpleDateFormat("M/d/y H:m:s");
            else
                f = new SimpleDateFormat("M/d/y");
            d = f.parse(src);
        }

        return d;
    }

    /**
     * Convert the string representation for a date into a Date object.
     * This method is similar to stringToDate, except it assumes that
     * no day, month, or year is specified.
     * The string can be of the following forms:
     *
     *      H:m:s z
     *      H:m:s
     */
    static public Date stringToTime(String src)
    throws java.text.ParseException {

        // convenience hack
        Date d = null;
        if (src.equals("now"))
            d = new Date();
        else {
            // The stupid fucking SimpleDateFormat doesn't have a way
            // to make various fields optional.  So, we try to be smart
            // and pre-parse the string to see what fields exist, and
            // select a suitable format string.

            // assume there will always be at least a time
            boolean isTime = false;
            boolean isTimezone = false;
            boolean afterTime = false;
            boolean isAmPm = false;

            for (int i = 0 ; i < src.length() ; i++) {
                char c = src.charAt(i);
                if (c == ':')
                    isTime = true;
                else if (Character.isSpaceChar(c) && isTime)
                    afterTime = true;
                else if (afterTime)
                    isTimezone = true;
                if (Character.isSpaceChar(c) && isTimezone)
                    isAmPm = true;
            }

            SimpleDateFormat f = null;

            if (isAmPm)
                f = new SimpleDateFormat("H:m:s a z");
            else if (isTimezone)
                f = new SimpleDateFormat("H:m:s z");
            else
                f = new SimpleDateFormat("H:m:s");

            d = f.parse(src);
        }

        return d;
    }

    public static final long MILLIS_PER_DAY = 1000l*60l*60l*24l;

    /**
     * Return the number of days difference between the two given dates. If d1
     * is after d2, the returned value is positive otherwise the returned value
     * is negative. If either given date is null, this returns
     * Integer.MIN_VALUE.
     *
     * @return  The number of days difference between the two given dates, or
     *          Integer.MIN_VALUE if either date is null.
     */
    public static int getDaysDifference(Date d1, Date d2)
    {
        int daysDiff = Integer.MIN_VALUE;

        if ((null != d1) && (null != d2))
        {
            Calendar d1Cal = Calendar.getInstance();
            Calendar d2Cal = Calendar.getInstance();
            d1Cal.setTime(d1);
            d2Cal.setTime(d2);

            setToMidnight(d1Cal);
            setToMidnight(d2Cal);

            long millisDiff = d1Cal.getTimeInMillis() - d2Cal.getTimeInMillis();
            daysDiff = Math.round((float)millisDiff / (float)MILLIS_PER_DAY);
        }

        return daysDiff;
    }

    static public boolean isDateAfterToday(Date date)
    {
        Calendar calToday = Calendar.getInstance();
        return (calToday.getTime().after(date));

    }

    static public long getLongDifference(Date d1, Date d2) {
        return (d2.getTime() - d1.getTime());
    }


    /**
     * Returns percentage given two numbers. The result is rounded
     * to the nearest integer. If the percentage is between 0 and 1
     * we round up to 1. If the percentage is between 99 and 100 we round
     * down to 99.
     *
     * @param x number to divide
     * @param y number to divide by
     * @return Percentage dividing x/y. See rounding rules above.
     */
    public static int getPercentage(int x, int y)
    {
        float p = (((float) x / y) * 100);
        if (p < 100 && p >= 99){
            return 99;
        }else if (p <= 1 && p > 0){
            return 1;
        }
        return Math.round(p);
    }

    /**
     * Zero out the hour, minute, second, and milliseconds on the given calendar.
     *
     * @param  c  The Calendar to set to midnight on its date.
     */
    private static void setToMidnight(Calendar c)
    {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    public final static long MILLI_IN_SECOND = 1000;
    public final static long MILLI_IN_MINUTE = MILLI_IN_SECOND * 60;
    public final static long MILLI_IN_HOUR = MILLI_IN_MINUTE * 60;
    public final static long MILLI_IN_DAY = MILLI_IN_HOUR * 24;
    public final static long MILLI_IN_WEEK = MILLI_IN_DAY * 7;
    public final static long MILLI_IN_MONTH = MILLI_IN_DAY * 30;
    public final static long MILLI_IN_YEAR = MILLI_IN_DAY * 365;

    public static long computeDifferenceMilli(Date start, Date end )
        throws GeneralException {

        if ( ( start == null ) || ( end == null  ) ) {
            throw new GeneralException("Both dates must be non null.");
        }

        if ( end.before(start) ) {
            throw new GeneralException("End date ["+end.toString()+" is before start date ["+start.toString()+"].");
        }

        long startMilli = start.getTime();
        long endMilli = end.getTime();
        return ( endMilli - startMilli );
    }


    public static String computeDifference(Date start, Date end )
    throws GeneralException {

        long result = computeDifferenceMilli(start,end);

        StringBuffer b = new StringBuffer(20);
        if ( result > MILLI_IN_HOUR ) {
            long hours = 0;
            hours = result / MILLI_IN_HOUR;
            if ( hours > 0 ) result = result - ( hours * MILLI_IN_HOUR);
            b.append(hours + " h");
        }

        if ( result > MILLI_IN_MINUTE ) {
            long minutes = 0;
            minutes = result / MILLI_IN_MINUTE;
            if ( minutes > 0 ) result = result - ( minutes * MILLI_IN_MINUTE);
            if ( b.length() > 0) b.append(" ");
            b.append(minutes + " min");
        }

        if ( result > MILLI_IN_SECOND ) {
            long seconds = 0;
            seconds = result / MILLI_IN_SECOND;
            if ( seconds > 0 ) result = result - ( seconds * MILLI_IN_SECOND);
            if ( b.length() > 0) b.append(" ");
            b.append(seconds + " s");
        }

        if ( result > 0 ) {
            long millis = 0;
            millis =  result;
            if ( b.length() > 0) b.append(" ");
            b.append(millis + " ms");
        }
        return b.toString();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo sprintf
    // needs work...
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * A marginally functional attempt to provide "sprintf" in Java.
     * The format string is a blank delimited list of integer tokens
     * that represent the field sizes. The args array contains the
     * strings that will plug into the fields.
     *
     * Todo:
     *         take an Object[] array instead of a String[] array
     *          add format tokens for left/right justification
     *         allow literal text in the format string
     *
     * UPDATE: This is no longer needed now that we use Java 5,
     * remove...
     */
    public static StringBuffer sprintf(String format, String[] args)
    {
        StringBuffer buf = new StringBuffer();
        StringTokenizer toks = new StringTokenizer(format);
        int arg = 0;

        while (arg < args.length) {
            String s = args[arg++];
            int slen = 0;
            if (s != null) {
                buf.append(s);
                slen = s.length();
            }

            if (toks.hasMoreTokens()) {
                String tok = toks.nextToken();
                int width = Integer.parseInt(tok);
                int pad = width - slen;
                if (pad > 0) {
                    for (int i = 0 ; i < pad ; i++)
                        buf.append(' ');
                }
            }

            buf.append(' ');
        }

        return buf;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Directory/file location
    //
    //////////////////////////////////////////////////////////////////////
    /*
     *
     * What you would think to be a simple operation is actually quite
     * complicated...
     *
     * Try to derive the full path to a file that is supposed to
     * be on the CLASSPATH.
     *
     * You use a ClassLoader to locate a "resource" which is returned
     * as a java.net.URL.  You can then pick things out of the URL
     * to identify the path of the associated file.
     *
     * It would seem, that this isn't the intended usage since
     * there doesn't appear to be a documented standard for the string
     * representation of the file path stored inside the URL.  There
     * is a major difference between 1.1 and 1.2, and it appears there
     * are differences between J++ and others.  What you're supposed
     * to do is connect to the URL and read it as a stream, this seems
     * to always work, so the various VM's know how to decode these
     * path variants internally.
     *
     * First you must obtain a ClassLoader.  The static
     * method ClassLoader.getResource() is only available in 1.2, so
     * we started using ClassLoader.getSystemResource().
     *
     * Weblogic has some really strange restrictions on what can go
     * in the classpath, and uses its own internal ClassLoader, so I had
     * to stop using the system class loader
     * and instead use the loader that was used for THIS class.
     * There might be better ways to find the right class loader, but this
     * self reflection seems to work ok.
     *
     * Putting com.waveset.util on the system classpath broke the loading of
     * our Weblogic realm which couldn't then find Weblogic classes.
     * Putting the Weblogic classes on the system classpath is bad, because
     * it prevents the system from performing dynamic class loading.  Weblogic
     * creates its own class loader which it uses to load the stuff under
     * its control, that loader has its own class path, and com.waveset.util
     * can be on it.
     *
     * Once you've found a ClassLoader that has given you a URL, you
     * can use the getFile() method to return the internal string that
     * references the file.  You may see the following forms:
     *
     * JDK 1.2
     *
     *       internal URL fields may look like this:
     *
     *        file:/D:/directory/file.txt
     *
     *      getFile() will strip off the "file:" but leaves the initial slash.
     *
     *
     * Weblogic JRE 1.2 (and possibly others) returns
     *
     *     file:D:/directory/file.txt
     *
     *        Note that the slash after file: is missing.
     *
     * Microsoft JDK 1.1
     *
     *    systemresource:/FILED:\directory\file.txt
     *
     *    getFile() strips off the "systemresource:".  Note that
     *    after "/FILE" it usually looks like the desired path.
     *
     * JDK 1.1 under VisualCafe 3.0 looks like this:
     *
     *      systemresource:/FILE0/+/file.txt
     *
     *    The 0 in "FILE0" appears to be an index into the CLASSPATH
     *       which can be obtained through the system property java.class.path.
     */

    /**
     * Attempts to determine the absolute pathname of a resource file.
     * The "name" argument must be the name of a resource file that can
     * be located through the CLASSPATH. It must use forward slashes.
     */
    public static String getResourcePath(String name) {

        String path = null;

        // name must have forward slashes, backslashes will be escaped
        // which never works
        if (name.indexOf("\\") >= 0)
            name = name.replace("\\", "/");

        // Use a ClassLoader to find a URL

        java.net.URL res = null;

        try {
            ClassLoader l = Util.class.getClassLoader();
            if (l != null)
                res = l.getResource(name);

            if (res == null) {
                // use the system class loader
                res = ClassLoader.getSystemResource(name);
            }
        }
        catch (Exception e) {
            // ingore
        }

        if (res != null) {

            // strip off the file: or systemresource:
            path = res.getFile();

            // debugPrint("path before decode = " + path);
            String fileEncoding = System.getProperty("file.encoding");
            // debugPrint("Using encoding " + fileEncoding + " to decode URL.");
            try {
                path = URLDecoder.decode(path, fileEncoding);
            } catch ( UnsupportedEncodingException ex ) {
                System.err.println("Encoding " + fileEncoding +
                                                " unsupported.  Using UTF-8");
                try {
                    path = URLDecoder.decode(path, "UTF-8");
                } catch ( UnsupportedEncodingException ex2 ) {
                    System.err.println("UTF-8 encoding not supported. " +
                                            "No decoding will be performed.");
                }
            }
            // debugPrint("path after decode = " + path);


            if (path.startsWith("/FILE")) {

                // looks like jdk 1.1
                StringBuffer b = new StringBuffer();

                int psn = 5;
                boolean prevSlash = false;

                if (Character.isDigit(path.charAt(psn))) {

                    // looks like a classpath reference
                    int sep = path.indexOf('/', 6);
                    if (sep == -1) {
                        // odd, probably corrupt
                    }
                    else {
                        String nstr = path.substring(5, sep);
                        int n = Util.atoi(nstr);
                        String root = getClasspathDirectory(n);
                        b.append(root);
                        b.append("/");
                        psn = sep + 1;
                        prevSlash = true;
                    }
                }
                else {
                    // Its probably microsoft, start emitting
                    // the drive letter, but handle slash conversion
                    // and the occasional embedded +
                }

                for (int i = psn ; i < path.length() ; i++) {

                    char c = path.charAt(i);
                    if (c == '/') {
                        if (prevSlash) {
                            // this must be the "\/+/" pattern,
                            // ignore
                        }
                        else {
                            b.append("/");
                            prevSlash = true;
                        }
                    }
                    else if (c == '+') {
                        // we found the "/+" pattern, ignore
                    }
                    else if (c == '\\') {
                        // convert to forward slash
                        b.append("/");
                        prevSlash = true;
                    }
                    else {
                        b.append(c);
                        prevSlash = false;
                    }
                }

                path = b.toString();
            }
            else {
                // looks like jdk 1.2
                // If the path contains a colon, and there is an initial
                // slash remove it.  Slashes appear to come out the
                // right direction.

                if (path.charAt(0) == '/' && path.indexOf(":") != -1)
                    path = path.substring(1);
            }
        }

        return path;
    }

    /**
     * Return the nth item on the CLASSPATH.
     *
     * In addition to isolating the path, it will convert
     * \ to / and trim the trailing slash if any.
     */
    public static String getClasspathDirectory(int idx) {

        String dir = null;

        Properties props = System.getProperties();
        if (props != null) {

            String cp = props.getProperty("java.class.path");
            if (cp != null) {
                StringTokenizer toks = new StringTokenizer(cp, ";");
                for (int i = 0 ; toks.hasMoreElements() ; i++) {
                    String s = (String)toks.nextElement();
                    if (i == idx) {

                        StringBuffer b = new StringBuffer();
                        for (int j = 0 ; j < s.length() ; j++) {
                            char c = s.charAt(j);
                            if (c == '\\') c = '/';

                            if (c != '/' || j < s.length() - 1)
                                b.append(c);
                        }
                        dir = b.toString();

                        break;
                    }
                }
            }
        }

        return dir;
    }

    /**
     * Attempt to derive the absolute path of a file.
     * The "name" argument may be an absolute path or relative
     * path fragment.
     *
     * If the name is an absolute path we simply return it.
     * Otherwise we combine the name with various other
     * root directories and probe until we find a valid file.
     * The sequence of probes is:
     *
     * - relative path alone
     *
     * - relative path combined with the value of the system
     *   property named in the "property" argument
     *
     * - relative path passed to getResourcePath to search
     *   the classpath (the path *must* contain only forward
     *   slashes for this to be used)
     */
    public static String findFile(String property, String name,
                                  boolean searchClasspath) {

        String path = null;

        if (name.charAt(0) == '/' || name.indexOf(":") != -1) {
            // it's an absolute path, just return it
            path = name;
        }
        else {
            // first try it relative to CWD
            File f = new File(name);
            if (f.isFile())
                path = name;

            // Next try relative to the property
            if (path == null && property != null) {
                String root = System.getProperty(property);
                if (root != null) {
                    String testpath = assemblePath(root, name);
                    f = new File(testpath);
                    if (f.isFile())
                        path = testpath;
                }
            }

            // try relative to the application home directory
            if (path == null) {
                String home = null;
                try {
                    home = getApplicationHome();
                } catch ( GeneralException ex ) {
                    // ignore if unable to determine appHome
                }

                if (home != null) {
                    String testPath = assemblePath(home, name);
                    f = new File(testPath);
                    if (f.isFile())
                        path = testPath;
                    else {
                        // try relative to the WEB-INF/config directory in the
                        // application home directory
                        testPath = assemblePath(home, "/WEB-INF/config/" + name);
                        f = new File(testPath);
                        if (f.isFile())
                            path = testPath;
                    }
                }
            }

            // if all else fails, search the classpath
            if (path == null && searchClasspath == true)
                path = getResourcePath(name);

            // if we still couldn't find anything,
            // just use the original name, and let Java throw
            if (path == null)
                path = name;
        }

        return path;
    }

    /**
     * Helper for findFile, combine two pieces of a path,
     * normalizing slashes. Normalization might not be necessary
     * but we have seen some odd problems and it looks better anyway.
     */
    private static String assemblePath(String root, String leaf) {

        StringBuffer b = new StringBuffer();
        b.append(root);
        if (leaf != null) {
            b.append("/");
            b.append(leaf);
        }

        String path = b.toString();
        if (path.indexOf("\\") >= 0)
            path = path.replace("\\", "/");

        return b.toString();
    }

    public static String findFile(String property, String name) {
        return findFile( property, name, false);
    }

    public static String findFile(String name) {

        return findFile("user.dir", name);
    }

    /**
     * Build an absolute path name for a file.
     * Typically used when you want to write an output file to
     * the "current working directory".
     *
     * If the name is already an absolute path we use it, otherwise
     * we use the system property "user.dir" as the root directory
     * and add the name.
     */
    public static String findOutputFile(String name) {

        String path = null;

        if (name.charAt(0) == '/' || name.indexOf(":") != -1) {

            // its an absolute path, just return it
            path = name;
        }
        else {
            // try relative to user.dir
            String root = System.getProperty("user.dir");
            if (root != null)
                path = assemblePath(root, name);
            else {
                // no obvious way to position it, have to leave
                // it in the process' current working directory
                path = name;
            }
        }

        return path;
    }

    /**
     * The system property that indicates our "home" directory.
     */
    public static final String APPLICATION_HOME = "sailpoint.home";

    /**
     * Returns the filesystem path for the application home.
     *
     * If a System property named sailpoint.home is set, then use it.
     * Otherwise, calculate it by finding a known resource in the
     * class path and doing some string manipulation.
     */
    public static String getApplicationHome() throws GeneralException {
		log.trace("Application home by StartupContextListener : " + sailpointWebDir
				+ " and by System.getProperty(sailpoint.home) : " + System.getProperty(APPLICATION_HOME));

		String root = !isNullOrEmpty(System.getProperty(APPLICATION_HOME)) ? System.getProperty(APPLICATION_HOME)
				: sailpointWebDir;

        if (root == null) {
            // didn't set the right property, go medieval
            String propertyFilename = BrandingServiceFactory.getService().getPropertyFile();
            String path = getResourcePath( propertyFilename );

            if (path == null) {
                String msg = "Could not derive sailpoint home directory.";
                throw new GeneralException(msg);
            } else {
                int i = path.indexOf("WEB-INF");
                if ( i > 0 ) {
                    root = path.substring(0, i - 1);
                }
            }
		}
		log.debug("Application home detected : " + root);

        return root;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Prints the current system properties to a writer object.
     */
    public static void dumpProperties(PrintWriter out) {
        Properties props = System.getProperties();
        if (props != null)
            props.list(out);
        out.flush();
    }

    /**
     * Prints the current system properties to the system output device.
     */
    public static void dumpProperties() {
        dumpProperties(new PrintWriter(System.out));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Conversions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert a Hashtable into a HashMap.
     *
     * @ignore
     * There are probably easier ways to do this with some of the
     * newer collection methods.
     */
    @SuppressWarnings("unchecked")
    public static HashMap hashtableToMap(Hashtable hash) {

        HashMap map = null;
        if (hash != null) {

            map = new HashMap();

            Set entries = hash.entrySet();
            Iterator i = entries.iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry)i.next();
                map.put(e.getKey(), e.getValue());
            }
        }

        return map;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Exception handling
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Converts the stack trace of an exception to a String
     */
    public static String stackToString(Throwable th) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            th.printStackTrace(pw);
            return sw.toString();
        }
        catch(Exception e) {
            return "Bad stackToString";
        }
    }

    /**
     * Return true if the exception means that an attempt was made
     * to save an object with a name that was already in use.
     * This is used to avoid putting alarming messages in the log file
     * when an exception we expect is encountered. Since 
     * a specific exception class is not received from Hibernate 
     * the message has to be parsed.
     *
     * jsl - Factored out of TaskManager so we could use it for duplicate
     * detection in ManagedAttributer.  The "design" of this seems anecdotal
     * but it has been use for a long time by TaskManager.  Should really
     * force this in all 4 databases and explicitly document what each does.
     */
    public static boolean isAlreadyExistsException(Throwable t) {

        boolean existing = false;

        // Did this ever work?  Apparently so but in some cases we get
        // org.hibernate.exception.ConstraintViolationExceptions that
        // don't include this text with a message "Could not execute JDBC
        // batch update" which is used for many things.
        String msg = t.getMessage();
        if (msg != null) {
            existing = (msg.indexOf("already exists") > 0);
            // Violation of UNIQUE KEY constraint 'UQ__spt_task__72E12F1B11AA861D'. 
            // Cannot insert duplicate key in object 'identityiq.spt_task_result'.
            if (!existing) existing = (msg.indexOf("UNIQUE") > 0);
            if (!existing) existing = (msg.indexOf("unique") > 0);
            // This happens when the same workflow is launched simultaneously.
            if (!existing) existing = (msg.indexOf("an instance of sailpoint.object.TaskResult was altered") > 0);
        }

        while (t != null && !existing) {
            existing = (t.getClass().getName().indexOf("ConstraintViolation") > 0);
            t = t.getCause();
        }

        return existing;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Serialization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert a Serializable Object into a byte array.
     *
     * @param toWrite  The Object to serialize to bytes.
     *
     * @return A byte array containing the serialized object.
     */
    public static byte[] serializableObjectToBytes(Object toWrite)
        throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(toWrite);
        return bos.toByteArray();
    }

    /**
     * Reconstitute a serialized object from a byte array.
     *
     * @param  bytes  The byte array containing the serialized object.
     *
     * @return The Object reconstituted from the given byte array.
     */
    public static Object readSerializedObject(byte[] bytes)
        throws IOException, ClassNotFoundException {

        Object o = null;

        if (null != bytes) {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            o = ois.readObject();
        }

        return o;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Tests
    //
    //////////////////////////////////////////////////////////////////////

    public static void main(String args[]) {

        try {
            dumpProperties();

            String path = Util.getResourcePath("users.xml");
            // String path = Util.getResourcePath("foo.xml");
            System.out.println("Path is " + path);

            String home = Util.getApplicationHome();
            System.out.println("ApplicationHome is " + home);
        }
        catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static String longToDottedQuad(long inaddr) {
        long addr1 = (inaddr >>> 24) & 0xFF;
        long addr2 = (inaddr >>> 16) & 0xFF;
        long addr3 = (inaddr >>> 8) & 0xFF;
        long addr4 = inaddr & 0xFF;
        return addr1 + "." + addr2 + "." + addr3 + "." + addr4;
    }

    public static long dottedQuadToLong(String ipaddr)
    {
        long rv = 0;
        StringTokenizer tokens = new StringTokenizer(ipaddr,".",false);
        while (tokens.hasMoreTokens())
        {
            rv<<=8;
            String token = tokens.nextToken();
            int val = Util.atoi(token);
            rv |= (val&0xFF);
        }
        return rv;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * Create a new instance of an object by classname.
     * <p>
     * This method calls Class.forname and then creates
     * a default instance using the newInstance() method
     * on the Class object. This means the object must
     * have a no argument constructor for this method
     * to work properly.
     */
    static public Object createObjectByClassName(String name)
    throws GeneralException {

        Object instance = null;
        Class c = null;

        try {
            c = Class.forName(name);
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer();
            sb.append("Couldn't load class: ");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        if ( c == null ) {
            StringBuffer sb = new StringBuffer();
            sb.append("Unknown error loading class: ");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        try {
            instance = c.newInstance();
        } catch (Exception e) {
            throw new GeneralException("Failed to create object: "
                    + e.toString());
        }

        if ( instance == null ) {
            throw new GeneralException("Class instance is null.");
        }
        return instance;
    }

    /**
     * Converts number of bytes to a human readable format.
     *
     * @param size number of bytes
     *
     * @return human readable memory size
     */
    public static String memoryFormat(long size) {
        String units = "bytes";
        float adjustedSize = size;

        if ( size > 1024 * 1024 * 1024 ) {
            adjustedSize /= (1.0 * 1024 * 1024 * 1024);
            units = "GB";
        } else if ( size > 1024 * 1024 ) {
            adjustedSize /= ( 1.0 * 1024 * 1024 );
            units = "MB";
        } else if ( size > 1024 ) {
            adjustedSize /= ( 1.0 * 1024);
            units = "KB";
        }

        StringBuffer value = new StringBuffer();
        value.append((new DecimalFormat("###.000")).format(adjustedSize)).
              append(" ").
              append(units);

        return value.toString();
    }

    public static ResourceBundle getIIQMessages(Locale locale) {
        return getResourceBundle("sailpoint.web.messages.iiqMessages", locale);
    }

    public static ResourceBundle getIIQHelpMessages(Locale locale) {
        return getResourceBundle("sailpoint.web.messages.iiqHelp", locale);
    }

    /**
     * Wrap ResourceBundle.getBundle() so it does not throw an exception of the
     * bundle cannot be found.
     */
    public static ResourceBundle getResourceBundle(String bundleBaseName,
                                                   Locale locale) {
        ResourceBundle rb = null;
        try {
            if ( locale != null )
                rb = ResourceBundle.getBundle(bundleBaseName, locale);
            else
                rb = ResourceBundle.getBundle(bundleBaseName);
        } catch(MissingResourceException e) { // eat the exception, log?
        }
        return rb;
    }

    /**
     * Wrap ResourceBundle.getString() so it does not throw an exception if the
     * key cannot be found. Instead return the key that was not found.
     */
    public static String getMessage(ResourceBundle bundle,
                                    String keyName) {

        return getMessage(bundle, keyName, true);
    }

    public static String getMessage(ResourceBundle bundle, String keyName,
                                    boolean returnKey) {

        String value = null;
        try {
            if ( bundle != null) {
                value = bundle.getString(keyName);
            }
        } catch(MissingResourceException e) { // eat the exception, log
        }

        if ( ( returnKey ) && ( value == null ) ) {
            value = keyName;
        }
        return value;
    }

    /**
     * Return a String that contains the free, allocated, max and totalFree (free+(max-allocated))
     */
    public static String getMemoryStats() {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long allocated = rt.totalMemory();
        long max = rt.maxMemory();
        long totalFree = (free+(max-allocated));
        return " Memory : Free [" +(free/1024)+ "] Allocated ["+(allocated/1024)+"] Max ["+(max/1024)+"] TotalFree["+((free+ (max-allocated))/1024)+"]";
    }

    public static boolean isNullOrEmpty(String str) {
        if (str == null) {
            return true;
        }

        return str.trim().isEmpty();
    }

    /**
     * Negative of isNullOrEmpty
     * @ignore
     * Its simply there for easier to read code.
     * 
     */
    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }
    
    public static boolean isAnyNullOrEmpty(String... vals) {
        
        if (vals == null) {
            return true;
        }
        
        for (int i=0; i<vals.length; ++i) {
            if (isNullOrEmpty(vals[i])) {
                return true;
            }
        }
        
        return false;
    }
    
    public static boolean isNothing(String str) {
    	return isNullOrEmpty(str) || "null".equals(str);
    }
    
    public static boolean isInt(String str) {
        if (str == null) {
            return false;
        }
        try {
            int id = Integer.parseInt(str);
            return true;
        }
        catch (NumberFormatException e) {}
        return false;
    }

    /**
     * Does the given string look like a message key?
     */
    public static boolean smellsLikeMessageKey(String key) {

        if (null == Util.getString(key)) {
            return false;
        }

        // Crude - look for all lower case, no spaces.
        for (int i=0; i<key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) || c == ' ') {
                return false;
            }
        }

        return true;
    }

    /**
     * Look through a source string looking for the number
     * of occurrences of a given character.
     */
    public static int countChars(String sourceString, char lookFor) {
        int count = 0;
        if ( sourceString != null ) {
            for (int i = 0; i < sourceString.length(); i++) {
                final char c = sourceString.charAt(i);
                if (c == lookFor) {
                    count++;
                }
            }
        }
        return count;
    }

    public static interface IConverter<T1, T2> {
        public T2 convert(T1 t1) throws GeneralException;
    }

    /**
     * Converts a list of one type into another, IConverter defines the function
     * that is used for the conversion
     */
    public static <T1, T2> List<T2> convert(List<T1> list1, IConverter<T1, T2> converter) throws GeneralException {

        if (list1 == null)
            return null;

        List<T2> list2 = new ArrayList<T2>(list1.size());

        for (T1 value1 : list1) {
            T2 value2 = converter.convert(value1);
            list2.add(value2);
        }

        return list2;
    }
    
    public static long nullsafeLong(Long val) {
        if (val == null) {
            return 0l;
        }
        
        return val.longValue();
    }
    
    public static boolean nullsafeBoolean(Boolean val) {
        
        if (val == null) {
            return false;
        }
        
        else return val.booleanValue();
    }

    public static String padID(String strId) {
        try {
            // If it looks like a number, pad it with Zeros
            int id = Integer.parseInt(strId);
            strId = String.format("%0" + Sequencer.DEFAULT_LEFT_PADDING + "d", id);
        }
        catch (NumberFormatException e) {
            // Just do nothing ... not a number.
        }
        return strId;
    }
    
    public static <T> void removeDuplicates(List<T> list) {
        
        List<T> unique = new ArrayList<T>();

        Iterator<T> iterator = list.iterator();
        while (iterator.hasNext()) {
            T item = iterator.next();
            if (unique.contains(item)) {
                iterator.remove();
            } else {
                unique.add(item);
            }
        }
    }
    
   static boolean sqlTraceEnabled; 
   public static void enableSQLTrace() {
        
        if ( sqlTraceEnabled ) return;
        
        Logger rootLogger = org.apache.logging.log4j.LogManager.getRootLogger();

        // This is ridiculous but org.hibernate.type actually only uses the logger if its
        // enabled during an initial level check when UserType is loaded the first time.

       final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
       final Configuration config = ctx.getConfiguration();

       LoggerConfig typeLoggerConfig = config.getLoggerConfig("org.hibernate.type");
       if (typeLoggerConfig != null) {
           Level t = Level.toLevel("TRACE");
           typeLoggerConfig.setLevel(t);
       }

       LoggerConfig sqlLoggerConfig = config.getLoggerConfig("org.hibernate.SQL");
       if (sqlLoggerConfig != null) {
           sqlLoggerConfig.setLevel(Level.DEBUG);
       }

       sqlTraceEnabled = true;
    }
    
    public static void disableSQLTrace() {
    
        if ( !sqlTraceEnabled ) return;

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();

        LoggerConfig sqlLoggerConfig = config.getLoggerConfig("org.hibernate.SQL");
        if (sqlLoggerConfig != null) {
            sqlLoggerConfig.setLevel(Level.OFF);
        }

        LoggerConfig typeLoggerConfig = config.getLoggerConfig("org.hibernate.type");
        if (typeLoggerConfig != null) {
            typeLoggerConfig.setLevel(Level.OFF);
        }
        
        sqlTraceEnabled = false;
    }

    /**
     * Will search for the key in a case insensitive fashion.
     * @param map  The map.
     * @param key  The string key
     * @param <T> type of map values
     * @return The value in in the Map for the given case insensitive key.
     */
    public static <T> T caseInsensitiveKeyValue(Map<String, T> map, String key) {
        if (key == null) {
            throw new IllegalArgumentException("key can't be null");
        }
        T value = null;

        String lowerKey = key.toLowerCase();
        for (String oneKey : map.keySet()) {
            if (oneKey.toLowerCase().equals(lowerKey)) {
                value = map.get(oneKey);
                break;
            }
        }

        return value;
    }

    /**
     * Get the type of a property on an object. This supports chained hibernate properties. 
     * NOTE: All class types in the chain must support default constructor. 
     * @param objectClass Class of the initial object
     * @param propertyName Name of the property.
     * @return Class type of the last property on the chain.
     * @throws GeneralException
     */
    public static Class getPropertyType(Class objectClass, String propertyName)
            throws GeneralException {
        return getPropertyType(objectClass, propertyName, true);
    }

    /**
     * Get the type of a property on an object. This supports chained hibernate properties. 
     * NOTE: All class types in the chain must support default constructor. 
     * @param objectClass Class of the initial object
     * @param propertyName Name of the property.
     * @param top If true, is the top of the chain, to handle join properties                    
     * @return Class type of the last property on the chain.
     * @throws GeneralException
     */
    private static Class getPropertyType(Class objectClass, String propertyName, boolean top)
            throws GeneralException {
        try {
            if (objectClass == null) {
                return null;
            }

            int dotIdx = propertyName.indexOf('.');
            if (dotIdx > -1) {
                String nextClassName = propertyName.substring(0, dotIdx);
                String nextProperty = propertyName.substring(dotIdx + 1, propertyName.length());
                Class nextClass = null;
                // This is a join property, so it should be an actual sailpoint class name
                if (top && Character.isUpperCase(nextClassName.charAt(0))) {
                    nextClass = getSailPointObjectClass(nextClassName);
                }

                if (nextClass == null) {
                    nextClass = getTypeClass(objectClass, nextClassName);
                }
                
                return getPropertyType(nextClass, nextProperty, false);
            } else {
                return getTypeClass(objectClass, propertyName);
            }
        }
        catch (Exception ex) {
            throw new GeneralException(ex);
        }
    }

    /**
     * Private helper to get the class of the getter for the given property name. In case of a generic
     * List, this will return the class of the List contents, NOT the list itself.
     *
     * Note this throws all its exceptions as-is, caller can handle them (i.e. getPropertyType)
     *
     * @param clazz The class with the property
     * @param propertyName The name of the property on the class, should have a getter
     */
    private static Class getTypeClass(Class clazz, String propertyName) throws Exception {
        if (clazz == null) {
            throw new InvalidParameterException("clazz");
        }
        if (Util.isNothing(propertyName)) {
            throw new InvalidParameterException("propertyName");
        }

        // This method of determining possibly generic type was learned from http://tutorials.jenkov.com/java-reflection/generics.html
        String methodName = "get" + StringUtils.capitalize(propertyName);
        @SuppressWarnings("unchecked")
        Method method = clazz.getMethod(methodName);

        Type returnType = method.getGenericReturnType();
        Class returnClass = null;
        // If parameterized type, we are in something like List<T>, so do this work to find the class of T
        if (returnType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) returnType;
            Type[] typeArguments = type.getActualTypeArguments();
            if (typeArguments != null) {
                // This will typically be a single argument for the generic type
                for (Type typeArgument : typeArguments) {
                    returnClass = (Class) typeArgument;
                }
            }
        } else {
            // This is just a boring regular type
            returnClass = (Class)returnType;
        }

        return returnClass;
    }

    /**
     * Convert the given value to the type defined by the property on the class, if possible.
     * @param clazz Class of top level object
     * @param property Property name to match type, chained properties are supported.
     * @param valueObject Initial object to convert
     * @return Converted object.
     * @throws GeneralException
     */
    public static Object convertValue(Class clazz, String property, Object valueObject) throws GeneralException {
        Object convertedObject = valueObject;
        Class valueType = getPropertyType(clazz, property);
        if (String.class.equals(valueType)) {
            convertedObject = convertedObject.toString();
        } else if (Integer.class.equals(valueType) || Integer.TYPE.equals(valueType)) {
            convertedObject = Util.otoi(convertedObject);
        } else if (Boolean.class.equals(valueType) || Boolean.TYPE.equals(valueType)) {
            convertedObject = Util.otob(convertedObject);
        } else if (valueType != null && valueType.isInstance(convertedObject)) {
            convertedObject = valueType.cast(convertedObject);
        }

        return convertedObject;
    }

    /**
     * Removes/Replaces characters not supported by json parser to supported without changing actual value.
     * @param value String value to be passed as json
     * @return String value having valid json characters
     */
    public static String escapeJsonCharacter(String value){
        if (value != null) {
            value = value.replace("\r", "\\r").replace("\n", "\\n").replace("\\", "\\\\").replace("/", "\\/");
        }
        return value;
    }

    /**
     * Get the class represented by the SailPoint object. If it is not a SailPoint object,
     * then return null.
     * @param name Unqualified class name
     * @return Class if it is SailPoint object, otherwise null.
     */
    public static Class getSailPointObjectClass(String name) {
        Class clazz;
        try {
            String fullClassName = "sailpoint.object." + name;
            clazz = Class.forName(fullClassName);
        } catch (ClassNotFoundException ce) {
            clazz = null;
        }
        
        return clazz;
    }

    /**
     * Method name - decimalToBinary
     * Functionality - Converts the decimal value passed to it into 16-bit binary
     *                 and stores each individual bit into Integer array.
     * @param value - value passed to the method as String
     * @return - Integer array.
     */
    public static Integer[] convertDecimalToBinary(String value) {
        Integer[] binaryArr = new Integer[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
        Integer decValue = Integer.parseInt(value);
        Integer rem = 0, arrIndex = 0;

        if(decValue < 0) {
            binaryArr[15] = 1;
            decValue = Math.abs(decValue);
        }

        while(decValue>0) {
            rem = decValue%2;
            binaryArr[arrIndex] = rem;
            decValue = decValue/2;
            arrIndex++;
        }

        return binaryArr;
    }
    /**
     * Function to replace a charector with other charector in any string.
     * @param toReplace - Variable to hold the value for the charector to be replaced.
     * @param replaceWith - variable to hold the value for the charector to which will replace.
     * @param str - A string whose value need to be changed.
     * @return - Return new string where any given character is replaced by any other character specified in argument.
     */
    public static String replaceCharacter(String toReplace, String replaceWith, String str) {
        if(Util.isNotNullOrEmpty(str)) {
            if(str.indexOf(toReplace) > -1) {
                str = str.replace(toReplace, replaceWith);
            }
        }
        return str;
    }
    
    /**
     * Escape the given HTML, optionally allowing formatting characters (such as
     * newlines entities - &#xA; and &#xD;) to remain unescaped.  This is
     * required for some text output that needs to be escaped to prevent script
     * injection but also needs to have some formatting which would get stripped
     * by the a4j filter (eg - by using {@link #wrapText(String, int)}).
     * 
     * @param  html                      The HTML to escape.
     * @param  allowUnescapedFormatting  True to allow formatting string to
     *                                   remain unescaped.
     * 
     * @return The escaped HTML.
     */
    public static String escapeHTML(String html, boolean allowUnescapedFormatting) {

        String escaped = StringEscapeUtils.escapeHtml4(html);
        
        if (allowUnescapedFormatting) {
            // Unescape any newline entities - &#xA; and &#xD;.
            escaped = escaped.replace("&amp;#xA;", "&#xA;");
            escaped = escaped.replace("&amp;#xD;", "&#xD;");

            // Also should allow word breaks probably.  See insertWordBreak().
        }
        
        return escaped;
    }

    /**
     * Add WEB-INF/lib to the System property "java.library.path", if it is not already present.
     * This property is used for locating the native libraries.
     */
    public static void setJavaLibraryPath() {
        String appHome = null;
        try {
            appHome = getApplicationHome();
        } catch (GeneralException e) {
            // Since this could conceivably be called in a context where
            // application home is not known,
            // we will warn and skip setting java.library.path.
            log.warn("Could not set java.library.path because SailPoint home directory could not be derived.");
            return;
        }

        if (null != appHome) {
            String libPath = System.getProperty(JAVA_LIB_PATH);
            String webInfPath = appHome + File.separator + "WEB-INF"
                    + File.separator + "lib";
            if (Util.isNotNullOrEmpty(libPath)) {
                if (!libPath.contains(webInfPath)) {
                    System.setProperty(JAVA_LIB_PATH,
                            libPath + System.getProperty("path.separator") + webInfPath);
                    log.debug("Added " + webInfPath+ " lib to java.library.path");
                }
            } else {
                System.setProperty(JAVA_LIB_PATH, webInfPath);
                log.debug("Added " + webInfPath + " lib to java.library.path");
            }
        }

    }

    /**
     * Indicates whether an expression can be evaluated as a number.
     *
     * @param str
     *         The string to evaluate.
     * @return a boolean value.
     */
    public static boolean isNumeric(String str) {
        if(isNullOrEmpty(str)) {
            return false;
        } else {
            return str.matches("-?\\d+(\\.\\d+)?");
        }
    }
}