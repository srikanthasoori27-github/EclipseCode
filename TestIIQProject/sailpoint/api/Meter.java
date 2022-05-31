/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * Simple performance monitoring utility.
 * 
 *  Author(s): Jeff, Kelly
 * 
 * A "meter" is an object that contains elapsed time statistics
 * for a block of code.  We will maintain an array of meters
 * which are accessed by index.  The instrumentation in the code
 * must know which index to use.
 * 
 * KPG - Added entry/exit by Meter name to allow more dynamic
 * creation of meters.  Entry/exit by index will perform slightly
 * faster and has a limit of number of meters being tracked.
 * 
 * The meter ranages for the classes that use numeric meters are:
 *
 *   Aggregator: 0-15
 *   Meter: 16
 *   Identitizer: 20-39
 *   Certificationer: 40-59
 *   IdentityArchiver: 60-69
 *   ObjectUtil: 70-79
 *   
 * CONCURRENCY
 *
 * Calls to the static Meter methods will record statistics in 
 * a MeterSet automatically created in thread-local storage for
 * each thread.  It is the responsibility of code at the root
 * of each thread to copy the private meters somewhere when the
 * thread completes.  Typically this will be a copy to the
 * global meter set, or a TaskResult.
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;

public class Meter {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //  
    //////////////////////////////////////////////////////////////////////

    
    private static final Log _log = LogFactory.getLog(Meter.class);
    /**
     * Logical name of the region.
     */
    String _name;

    /**
     * Number of times we've been entered.
     */
    int _entries;

    /**
     * Number of times we've been exited.
     * Don't really need this if we can assume that enter() and exit()
     * will be called symetrically, but with exceptions it's easy
     * to forget and it scews the statistics.
     */
    int _exits;

    /**
     * The total number of milliseconds we've spend in this meter.
     */
    long _total;

    /**
     * Maximum time for one call.
     */
    long _max;

    /**
     * Minimum time for one call.
     */
    long _min = -1;

    /**
     * The time of last entry.
     */
    long _start;

    /**
     * Number mismatched entry/exits we found.
     */
    int _errors;

    /**
     * Optional identifier that can be associated with meter results
     * in the top 5 list.  This is was added for RoleEntitlizer which
     * passes the name of the Identity being processed.  Presumably so 
     * if you have some extreme timings you can see which Identity they were 
     * related to.  It does not appear to have any other uses.
     * jsl
     */
    String _identifier;
    
    private static final int MAX_METER_SIZE = 5;
    List<MaxData> _topFive = new ArrayList<MaxData>(MAX_METER_SIZE);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public Meter(String name) {
        _name = name;
    }

    /**
     * This one is only for parsing the Map representation where
     * the name comes later.
     */
    public Meter() {
    }

    /**
     * For multi-thread MeterSet merging, create a copy of a Meter.
     */
    public Meter(Meter src) {

        if (src != null) {
            _name = src._name;
            _entries = src._entries;
            _exits = src._exits;
            _total = src._total;
            _max = src._max;
            _min = src._min;
            _start = src._start;
            _errors = src._errors;
            _topFive = new ArrayList<MaxData>(src._topFive);
        }
    }

    /**
     * For multi-thread MeterSet merging, combine two meters.
     * _name is expected to be correct.
     */
    public void assimilate(Meter src) {

        if (src != null) {
            _entries += src._entries;
            _exits += src._exits;
            _total += src._total;
            _errors += src._errors;
            
            // note that _min initializes to -1
            // so any non-negative value carries over
            if (_min < 0 || src._min < _min)
                _min = src._min;

            if (src._max > _max)
                _max = src._max;
            
            // merge the topFives, sort then remove extras
            _topFive.addAll(src._topFive);
            Collections.sort(_topFive);
            int i = _topFive.size();
            for (Iterator<MaxData> it = _topFive.iterator() ; it.hasNext() && i > MAX_METER_SIZE; i--) {
                it.next();
                it.remove();
            }
                
        }
    }

    public String getName() {
        return _name;
    }

    public int getEntries() {
        return _entries;
    }

    public int getExits() {
        return _exits;
    }

    public int getErrors() {
        return _errors;
    }

    public long getMax() {
        return _max;
    }

    public long getMin() {
        return _min;
    }

    public long getTotal() {
        return _total;
    }

    public long getStart() {
        return _start;
    }

    public long getAverage() {
        long average = 0;
        // only use exit count in the average
        if (_exits > 0)
            average = _total / _exits;
        return average;
    }
    
    public Collection<MaxData> getMaxData() {
        return _topFive;
    }
    
    public void enterWithIdentifier(String identifier) {

        // jsl - Why the conditional?  if we're entering
        // it should take the new identifier, or forget the old
        // one if it wasn't passed?  In practice it doesn't seem
        // to matter since the caller always passes an identifier.
        if (identifier != null)
            _identifier = identifier;
        
        enter();
    }

    public void enter() {

        if (_start != 0) {
            // a dangling entry, this can happen if
            // exceptions are thrown and exit() isn't called,
            // not sure if we want to remember anything special 
            // here, we can tell later by comparing _entries and _exits
            // TODO: It can also happen with recursion, that we
            // really should try to handle!!
        }

        _start = System.currentTimeMillis();
        _entries++;
    }

    public void exit() {

        if (_start == 0) {
            // exit without enter, this really shouldn't happen
            // probably represents a coding error
            _errors++;
        }
        else {
            long end = System.currentTimeMillis();      
            long delta = end - _start;
            if (_min < 0 || delta < _min)
                _min = delta;
            if (delta > _max)
                _max = delta;
            
            if (_topFive.size() < MAX_METER_SIZE) {
                _topFive.add(new MaxData(_identifier, delta));
            }
            else {
                MaxData lowestMaxTime = Collections.min(_topFive);
                if (delta > lowestMaxTime.time) {
                    while (_topFive.size() >= MAX_METER_SIZE) {
                        _topFive.remove(lowestMaxTime);
                    }
                    _topFive.add(new MaxData(_identifier, delta));
                }
            }
            _total += delta;
            _start = 0;
            _exits++;
        }
    }

    /**
     * Add a single Meter's statistics to a report buffer.
     * Also log a csv if loggine is ennabled.
     * See MeterSet.generateReport for more information.
     */
    public void generateReport(StringBuilder sb) {
        
        String name = (_name != null) ? _name : "???";
        String exits = Util.itoa(_exits);
        String total = Util.ltoa(_total);
        String min = Util.ltoa(_min);
        String max = Util.ltoa(_max);
        String average = Util.ltoa(getAverage());
        sortMaxData();

        sb.append("Meter " + name + ": " + 
                  exits +
                  " calls, " +
                  total +
                  " milliseconds, " + 
                  min +
                  " minimum, " +
                  max +
                  " maximum, " +
                  average +
                  " average");

        if (_topFive != null) {
            sb.append(", top five " + getMaxDataStringValue());
        }
        
        sb.append("\n");

        if (_log.isInfoEnabled()) {
            final String COMMA = ",";
            _log.info(name + COMMA + exits + COMMA + total + COMMA + min + COMMA + max + COMMA + average  + COMMA + getMaxDataStringValue());
        }
    }
    
    /**
     * Render a single meter as a Map.
     */
    public Map<String,String> renderMap() {

        Map<String,String> map = new HashMap<String,String>();
        map.put("name", Util.otoa(_name));
        map.put("calls", Util.otoa(_exits));
        map.put("total", Util.otoa(_total));
        map.put("min", Util.otoa(_min));
        map.put("max", Util.otoa(_max));
        map.put("average", Util.otoa(getAverage()));
        
        sortMaxData();
        map.put("top", getMaxDataStringValue());
        
        return map;
    }

    /**
     * Parse the Map rendering back to a Meter.
     */
    public void parseMap(Map<String,String> map) {

        _name = Util.otoa(map.get("name"));
        _exits = Util.otoi(map.get("calls"));
        _total = Util.otoi(map.get("total"));
        _min = Util.otoi(map.get("min"));
        _max = Util.otoi(map.get("max"));
        // TODO: top 5
    }
    
    /**
     * returns a pretty row of max data identifier and time delimited by a comma
     * @return
     */
    public String getMaxDataStringValue() {
        final String COMMA = ",";
        sortMaxData();
        StringBuffer buff = new StringBuffer();
        buff.append("[");
        if (_topFive != null) {
            for (MaxData md : _topFive) {
                // identifier is usually null
                if (md.identifier != null) {
                    buff.append(md.identifier);
                    buff.append(": ");
                }
                buff.append(md.time);
                buff.append(COMMA);
            }
            // remove final comma
            if (buff.length() > 0) {
                buff.deleteCharAt(buff.length()-1);
            }
        }
        buff.append("]");
        return buff.toString();
    }

    public void init() {

        _entries = 0;
        _exits = 0;
        _total = 0;
        _max = 0;
        _min = -1;
        _start = 0;
        _errors = 0;
        _topFive.clear();
    }
    
    /**
     * a data object for storing the top five maximum metered events. 
     * @author chris.annino
     *
     */
    public class MaxData implements Comparable<MaxData> {
        
        String identifier;
        Long time;
        
        public MaxData(String identifier, Long time) {
            this.identifier = identifier;
            this.time = time;
        }
        
        /* an object is greater if the maxData.time is greater
         * (non-Javadoc)
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(MaxData another) {
            if (another == null || another.time == null) {
                return -1;
            }
            return time.compareTo(another.time);
        }
        
        /* eclipse generated
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
            result = prime * result + ((time == null) ? 0 : time.hashCode());
            return result;
        }

        /* eclipse generated
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MaxData other = (MaxData) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (identifier == null) {
                if (other.identifier != null)
                    return false;
            } else if (!identifier.equals(other.identifier))
                return false;
            if (time == null) {
                if (other.time != null)
                    return false;
            } else if (!time.equals(other.time))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            return this.identifier + "," + this.time;
        }
        
        /** eclipse generated
         * @return this instance of the meter 
         */
        private Meter getOuterType() {
            return Meter.this;
        }
    }
    
    /**
     * let's sort in reverse order before we print
     */
    void sortMaxData() {
        Collections.sort(_topFive);
        Collections.reverse(_topFive);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // MeterSet
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Encapsulates a set of meters.
     * 
     * One of these will be created automatically in thread-local
     * storage the first time a static Meter method is called.
     * There is also a singleton global MeterSet that may receive
     * aggregate meters from threads.
     */
    public static class MeterSet {

        public static final int MAX_METERS = 200;

        private Meter[] meters = new Meter[MAX_METERS];
        private Map<String,Meter> metersByName = new HashMap<String,Meter>();
        private boolean byTime = false;

        public void enter(String name) {

            Meter m = metersByName.get(name);
            if (m == null) {
                m = new Meter(name);
                metersByName.put(name, m);
            }
            m.enter();
        }

        public void exit(String name) {

            Meter m = metersByName.get(name);
            if (m == null) {
                // if we have a dangling exit, why bother creating one?
                m = new Meter(name);
                metersByName.put(name, m);
            }
            m.exit();
        }

        public void enter(int index, String name) {
            enter(index, name, null);
        }
        
        public void enter(int index, String name, String identifier) {

            if (index >= 0 && index < MAX_METERS) {
                Meter m = meters[index];
                if (m == null) {
                    m = new Meter(name);
                    meters[index] = m;
                }
                if (identifier != null)
                    m.enterWithIdentifier(identifier);
                else
                    m.enter();
            }
        }

        public void exit(int index) {

            if (index >= 0 && index < MAX_METERS) {
                Meter m = meters[index];
                // jsl - formerly bootstrapped here but since we don't
                // have a name this doesn't really make sense
                if (m == null) {
                    m = new Meter("???");
                    meters[index] = m;
                }
                m.exit();
            }
        }

        public void reset() {

            for (Meter m : getMeters()) {
                m.init();
            }
        }

        public void report() {
            System.out.print(generateReport());
        }
        
        /**
         * Generate a meter report.  We have historically generated
         * a slightly formatted report that was sent to System.out.
         * At some point, a report formatted as a csv was added and
         * sent to the log4j logs.  Then we added having the
         * non-csv meter report stored in the TaskResult.  This
         * last part menas that we have to generate the report to 
         * a String that can be both printed and saved.  How the
         * log report integrates with this is a bit odd, we'll assume
         * that if you're generating the String that that we can
         * do the logging at the same time, though in theory one might
         * want to do them independently.
         */
        public String generateReport() {

            StringBuilder sb = new StringBuilder();
            sb.append("Meter Summary:\n");

            if (_log.isInfoEnabled()) {
                _log.info("Name,Exits,Total,Min,Max,Average,Top 1st Hit Identifier,Time,2nd Hit,Time,3rd Hit,Time,4th Hit,Time,5th Hit,Time");
            }

            for (Meter m : getMeters()) {
                m.generateReport(sb);
            }

            return sb.toString();
        }

        /**
         * Render a mater set as a List of Maps.
         */
        public List<Map<String,String>> renderMaps() {

            List<Map<String,String>> list = new ArrayList<Map<String,String>>();
            for (Meter m : getMeters()) {
                list.add(m.renderMap());
            }

            return list;
        }

        /**
         * Convert a rendered map list back into a MeterSet.
         * The Meters will all be converted to named meters even 
         * though they may have started as indexed meters.  This
         * is enough for post-capture analysis.
         */
        public void parseMaps(List<Map<String,String>> maps) {

            // start with a fresh name map
            metersByName = new HashMap<String,Meter>();
            for (Map<String,String> map : Util.iterate(maps)) {
                Meter meter = new Meter();
                meter.parseMap(map);
                metersByName.put(meter.getName(), meter);
            }
        }

        public void setByTime(boolean byTime) {
            this.byTime = byTime;
        }

        private Collection<Meter> getMetersSortedByTime() {
            
            Comparator<Meter> timeComparator = new Comparator<Meter>() {
                public int compare(Meter m1, Meter m2) {
                    // Sort DESCENDING
                    return new Long(m2._total).compareTo(new Long(m1._total));
                }
            };

            return getMetersSorted(timeComparator);
        }

        /**
         * Get a collection of all meters sorted by name.
         * 
         * @return A collection of all meters sorted by name.
         */
        public Collection<Meter> getMeters() {
            if (byTime) {
                return getMetersSortedByTime();
            } else {
                return getMetersSortedByName();
            }
        }

        private Collection<Meter> getMetersSortedByName() {
            
            Comparator<Meter> c = new Comparator<Meter>() {
                public int compare(Meter m1, Meter m2) {
                    if ((m1._name == null) || (m2._name == null))
                    return Integer.MIN_VALUE;
                
                    return m1._name.compareTo(m2._name);
                }
            };

            return getMetersSorted(c);
        }
        
        private Collection<Meter> getMetersSorted(Comparator<Meter> c) {
            // Sort the named meters
            Collection<Meter> byName = new TreeSet<Meter>(c);
            byName.addAll(metersByName.values());

            // Add the named meters.
            ArrayList<Meter> meterList = new ArrayList<Meter>(new TreeSet<Meter>(c));
            meterList.addAll(byName);

            // Add the indexed meters.
            for (int i = 0 ; i < MAX_METERS ; i++) {
                Meter m = meters[i];
                if (m != null) {
                    m.sortMaxData();
                    meterList.add(m);
                }
            }

            return meterList;
        }

        /**
         * Assimilate one meter set into this one.
         */
        public void assimilate(MeterSet otherSet) {

            for (int i = 0 ; i < otherSet.meters.length ; i++) {
                Meter theirs = otherSet.meters[i];
                if (theirs != null) {
                    Meter mine = meters[i];
                    if (mine != null)
                        mine.assimilate(theirs);
                    else {
                        mine = new Meter(theirs);
                        meters[i] = mine;
                    }
                }
            }

            Iterator<String> it = otherSet.metersByName.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Meter theirs = otherSet.metersByName.get(key);
                if (theirs != null) {
                    Meter mine = metersByName.get(key);
                    if (mine != null)
                        mine.assimilate(theirs);
                    else {
                        mine = new Meter(theirs);
                        metersByName.put(key, mine);
                    }
                }
            }
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Static Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The global meters.
     */
    static MeterSet GlobalMeters = new MeterSet();

    /**
     * The ThreadLocal storage for MeterSets.
     */
    static ThreadLocal<MeterSet> ThreadMeters = new ThreadLocal<MeterSet>();

    /**
     * Return the singleton global meter set.
     */
    public static MeterSet getGlobalMeters() {
        return GlobalMeters;
    }

    /**
     * Establish a private thread-local MeterSet for the current thread.
     */
    public static MeterSet getThreadMeters() {

        MeterSet meters = ThreadMeters.get();
        if (meters == null) {
            meters = new MeterSet();
            ThreadMeters.set(meters);
        }
        return meters;
    }

    /**
     * Enter this meter by name.
     * Originally didn't have static enter(String) and exit(String) because
     * those were defined in Meter.  Changed so Meter has other methods and
     * code using meters can use the more obvious names.
     */
    public static void enter(String name) {
        getThreadMeters().enter(name);
    }

    public static void exit(String name) {

        getThreadMeters().exit(name);
    }

    // old names for backward compatibility

    public static void enterByName(String name) {
        enter(name);
    }
    public static void exitByName(String name) {
        exit(name);
    }

    public static void enter(int index, String name) {

        getThreadMeters().enter(index, name);
    }
    
    public static void enter(int index, String name, String identifier) {
        getThreadMeters().enter(index, name, identifier);
    }

    public static void exit(int index) {

        getThreadMeters().exit(index);
    }

    public static void reset() {

        getThreadMeters().reset();
    }

    public static void report() {

        getThreadMeters().report();
    }
    
    public static String generateReport() {

        return getThreadMeters().generateReport();
    }

    public static List<Map<String,String>> render() {

        return getThreadMeters().renderMaps();
    }

    public static MeterSet parse(Object o) {

        MeterSet set = new MeterSet();
        if (o instanceof List) {
            List<Map<String,String>> list = (List<Map<String,String>>)o;
            set.parseMaps(list);
        }
        return set;
    }
    
    public static void setByTime(boolean byTime) {
        GlobalMeters.setByTime(byTime);
    }
    
    /**
     * Add a private meter set (usually one that was kept
     * in thread-local storage) into the global meter set.  
     *
     * This must synchronize.
     */
    public static synchronized void publishMeters(MeterSet meters) {

        GlobalMeters.assimilate(meters);

        // since code can publish meters incrementally, reset
        // the source so they don't multiply
        meters.reset();

    }

    /**
     * Add the private meter set for this thread to the global meter set.
     */
    public static void publishMeters() {

        MeterSet meters = ThreadMeters.get();
        if (meters != null) {
            publishMeters(meters);
            ThreadMeters.remove();
        }
    }

    /**
     * Called by the debug page to reset the global meters.
     * Must be synchronized.
     */
    public static synchronized void globalReset() {
        GlobalMeters.reset();
    }

    /**
     * Called by DebugBean to return a list of the global meters
     * for display.  This doesn't need to be synchronized since DebugBean
     * won't modify the objects, but they may be changing out from under it.
     */
    public static Collection<Meter> getGlobalMeterCollection() {
        
        return GlobalMeters.getMeters();
    }
    
    
}
