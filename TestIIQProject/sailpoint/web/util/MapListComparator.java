package sailpoint.web.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/** Performs a sort on the list of object for a List Result based on the column
 * specified
 * @author peter.holcomb
 *
 */
public class MapListComparator implements Comparator<Map<String,Object>>{
    String column;
    boolean ignoreCase;
    Collator collator;

    public MapListComparator(String column, boolean ignoreCase) throws GeneralException {
        this(column, ignoreCase, null);
    }

    public MapListComparator(String column, boolean ignoreCase, Locale locale) throws GeneralException {
        // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
        // For now, pull off the first sorter and use that for comparisons. - MLH
        if(column.startsWith("[")) {
            List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, column);
            column = sorters.get(0).getProperty();
        }
        this.column = column;
        this.ignoreCase = ignoreCase;
        
        // If locale is set, make a collator to use for comparing localized values
        if (locale != null) {
            this.collator = Collator.getInstance(locale);
            this.collator.setStrength(this.ignoreCase ? Collator.SECONDARY : Collator.TERTIARY);
        }
    }

    public MapListComparator(String column) throws GeneralException {
        this(column, false);
    }

    @SuppressWarnings("unchecked")
    public int compare(Map<String,Object> o1, Map<String,Object> o2) {
        // Sort order is potentially a list of columns
        return listCompare(o1, o2, Util.csvToList(column));
    }

    /**
     * Compare maps by list of properties
     * @param o1 The map to compare
     * @param o2 The map object
     * @param properties The properties to compare
     * @return -1, 0, or 1 depending on order
     */
    private int listCompare(Map<String,Object> o1, Map<String,Object> o2, List<String> properties) {
        /* For each property in properties
         *  if the property is not equal in both objects
         *    return the comparison value
         */
        for (String property : properties) {
            int comparison = compareItem(o1, o2, property);
            if(comparison != 0) {
                return comparison;
            }
        }
        /* Exhausted all the comparable properties so the items are equal */
        return 0;
    }

    /**
     * Compare individual maps
     * @param o1 The map to compare
     * @param o2 The other amp
     * @param property The property to compare
     * @return -1, 0, or 1 depending on order
     */
    private int compareItem(Map<String,Object> o1, Map<String,Object> o2, String property) {
        Object v1 = o1.get(property);
        Object v2 = o2.get(property);
        if(v1==null)
            return (v2 == null) ? 0 : -1;
        if(v2==null)
            return 1;
        
        if (v1 instanceof String && v2 instanceof String) {
            if (this.collator != null) {
                return collator.compare(v1, v2);
            } else if (this.ignoreCase) {
                return ((String)v1).compareToIgnoreCase((String)v2);
            } else {
                return ((String)v1).compareTo((String)v2);
            }            
        }

        // If comparable, compare them.
        if (v1 instanceof Comparable) {
            return ((Comparable) v1).compareTo(v2);
        }

        // If not comparable, convert to string and compare.
        if (this.collator != null) {
            return this.collator.compare(v1.toString(), v2.toString());
        } else {
            return v1.toString().compareTo(v2.toString()); 
        }        
    }
}