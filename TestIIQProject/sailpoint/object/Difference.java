/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The representation of a "difference" between two abstract attributes.
 * Currently, the <code>sailpoint.api.Differencer</code> will compare two 
 * Links or Identities and
 * generate a list of Difference objects, though in theory the object
 * is generic enough to represent differences between other classes.
 * 
 * The model is designed to make it easy for the UI to display the
 * differences, not necessarily to be used in analysis or computation.
 * Old and new values will therefore be normalized to Strings where
 * possible.  Additions and deletions to multi-valued attributes
 * will be represented as List<String>.
 * 
 * As policy scanning evolves we can extend the model to include
 * more concrete representations of the changes if necessary.
 * 
 * Author: Jeff
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * This class provides a model for a difference between two 
 * abstract attributes.
 *
 * It also provides a set of static methods to compare various
 * classes and calculate differences.
 */
@XMLClass
public class Difference extends AbstractXmlObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * The maximum length of a string value  
     * rendered before restoring to ellipses.
     */
    public static int MAX_STRING_LENGTH = 40;

    /**
     * The maximum number of values rendered
     * of a multi-valued attribute before resorting to ellipses...
     */
    public static int MAX_MULTI_VALUES = 10;

    /**
     * An arbitrary name identifying the context of the attribute.
     * One use for this is to hold the Application name when generating
     * differences for Link attributes.
     */
    String _context;

    /**
     * The name of the attribute that was different.
     */
    String _attribute;

    /**
     * The display name (or catalog key) of the attribute if
     * one is available. When comparing Identity attributes,
     * this is taken from the ObjectAttribute. When
     * comparing Link attributes, it could be taken from 
     * AttributeDefinition of the Schema.
     */
    String _displayName;

    /**
     * True if this is a multi-valued attribute.
     * The UI might choose to display these in a different way
     * than single valued attributes.
     */
    boolean _multi;

    /**
     * String representation of the old value.
     * For multi-valued attributes this will be formatted as
     * a CSV, with ellipses (...) at the end if the value extends
     * beyond the maximum size. This is therefore intended
     * only as a summary of the changes and might not be totally accurate.
     */
    String _oldValue;

    /**
     * String representation of the new value.
     * For multi-valued attributes, the same issues described
     * with _oldValue apply.
     */
    String _newValue;

    /**
     * For multi-valued attributes, the exact list of values
     * that were added.
     */
    List<String> _addedValues;

    /**
     * For multi-valued attributes, the exact list of values
     * that were removed.
     */
    List<String> _removedValues;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Difference() {
    }

    public Difference(String att, String old, String neu) {
        _attribute = att;
        _oldValue = old;
        _newValue = neu;
    }

    public Difference(ObjectAttribute ida) {
        setAttribute(ida);
    }

    public Difference(AttributeDefinition ad) {
        setAttribute(ad);
    }

    public void setAttribute(ObjectAttribute ida) {
        if (ida != null) {
            _attribute = ida.getName();
            _displayName = ida.getDisplayName();
        }
    }

    public void setAttribute(AttributeDefinition def) {
        if (def != null) {
            _attribute = def.getName();
            // no display name yet
        }
    }
    
    // Provide control over the max lengths for customizations that
    // need more room.
    
    static public int setMaxStringLength(int max) {
        int prev = MAX_STRING_LENGTH;
        MAX_STRING_LENGTH = max;
        return prev;
    }

    static public int setMaxMultiValues(int max) {
        int prev = MAX_MULTI_VALUES;
        MAX_MULTI_VALUES = max;
        return prev;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setContext(String name) {
        _context = name;
    }

    /**
     * An arbitrary name identifying the context of the attribute.
     * One use for this is to hold the Application name when generating
     * differences for Link attributes.
     */
    public String getContext() {
        return _context;
    }

    @XMLProperty
    public void setAttribute(String name) {
        _attribute = name;
    }

    /**
     * The name of the attribute that was different.
     */
    public String getAttribute() {
        return _attribute;
    }

    @XMLProperty
    public void setDisplayName(String name) {
        _displayName = name;
    }

    /**
     * The display name (or catalog key) of the attribute if
     * one is available. When comparing Identity attributes,
     * this is taken from the ObjectAttribute. When
     * comparing Link attributes, it could be taken from 
     * AttributeDefinition of the Schema.
     */
    public String getDisplayName() {
        if (Util.isNullOrEmpty(_displayName)) {
            return _attribute;
        } else {
            return _displayName;
        }
    }

    @XMLProperty
    public void setMulti(boolean b) {
        _multi = b;
    }

    /**
     * True if this is a multi-valued attribute.
     * The UI might choose to display these in a different way
     * than single valued attributes.
     */
    public boolean isMulti() {
        return _multi;
    }

    @XMLProperty
    public void setOldValue(String v) {
        _oldValue = v;
    }

    /**
     * String representation of the old value.
     * For multi-valued attributes this will be formatted as
     * a CSV, with ellipses (...) at the end if the value extends
     * beyond the maximum size. This is therefore intended
     * only as a summary of the changes and might not be totally accurate.
     */
    public String getOldValue() {
        return _oldValue;
    }

    @XMLProperty
    public void setNewValue(String v) {
        _newValue = v;
    }

    /**
     * String representation of the new value.
     * For multi-valued attributes, the same issues described
     * with _oldValue apply.
     */
    public String getNewValue() {
        return _newValue;
    }

    @XMLProperty
    public void setAddedValues(List<String> values) {
        _addedValues = values;
    }

    /**
     * For multi-valued attributes, the exact list of values
     * that were added.
     */
    public List<String> getAddedValues() {
        return _addedValues;
    }

    @XMLProperty
    public void setRemovedValues(List<String> values) {
        _removedValues = values;
    }

    /**
     * For multi-valued attributes, the exact list of values
     * that were removed.
     */
    public List<String> getRemovedValues() {
        return _removedValues;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience Accessors
    //
    //////////////////////////////////////////////////////////////////////

    public String getDisplayableName() {
        return (_displayName != null) ? _displayName : _attribute;
    }

    public void addAddedValue(String value) {
        if (_addedValues == null)
            _addedValues = new ArrayList<String>();
        _addedValues.add(value);
    }

    public void addRemovedValue(String value) {
        if (_removedValues == null)
            _removedValues = new ArrayList<String>();
        _removedValues.add(value);
    }

    /**
     * Return the added values as a CSV.
     */
    public String getAddedValuesCsv() {
        String csv = null;
        if (_addedValues != null) 
            csv = Util.listToCsv(_addedValues);
        return csv;
    }

    /**
     * Return the removed values as a CSV.
     */
    public String getRemovedValuesCsv() {
        String csv = null;
        if (_removedValues != null) 
            csv = Util.listToCsv(_removedValues);
        return csv;
    }

    /**
     * Convenience method to return a non-null (but possibly empty) list
     * containing either all of the addedValues or the newValue.
     */
    public List<String> getAllNewValues() {
    
        List<String> newVals = null;

        // Make a copy so we don't corrupt the added values list by potentially
        // adding the _newValue to it.
        newVals = (null != _addedValues) ? new ArrayList<String>(_addedValues)
                                         : new ArrayList<String>();

        // If there's a new value add it to the list.  Only use this if there
        // aren't added values.  If there are, they are already in the list.
        if (newVals.isEmpty() && (_newValue != null)) {
            newVals.add(_newValue);
        }

        return newVals;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    // Putting some of the basic stuff in here rather than encapsulating
    // it all in Differencer so we can potentially use it more places.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Compare two values, and return a Difference object if they
     * are different.
     *
     * @ignore
     * !! Consider having this treat null and empty string the same.
     * This is required by RoleLifecycler and it would be nice
     * to have differencing be done consistently everywhere.
     */
    static public Difference diff(Object oldValue, Object newValue, int maxStringLength, boolean caseInsensitive) {

        Difference d = null;

        if (oldValue == null) {
            if ((newValue != null) && !isEmptyCollection(newValue)) {
                d = new Difference();
                d.setNewValue(stringify(newValue, maxStringLength));
                if (newValue instanceof Collection)
                    d.setAddedValues(listify((Collection)newValue, maxStringLength));
            }
        }
        else if (newValue == null) {
            if ((oldValue != null) && !isEmptyCollection(oldValue)) {
                d = new Difference();
                d.setOldValue(stringify(oldValue, maxStringLength));
                if (oldValue instanceof Collection)
                    d.setRemovedValues(listify((Collection)oldValue, maxStringLength));
            }
        }
        else if (oldValue instanceof Collection) {
            if (!(newValue instanceof Collection)) {
                // coerce both sides to a collection before diffing
                newValue = coerceList(newValue);
            }
            d = diffCollections((Collection)oldValue, (Collection)newValue, maxStringLength, caseInsensitive);
        }
        else if (newValue instanceof Collection) {
            // went from single value to List
            // coerce both sides to Collection before diffing
            oldValue = coerceList(oldValue);
            d = diffCollections((Collection)oldValue, (Collection)newValue, maxStringLength, caseInsensitive);
        }
        else if (!oldValue.equals(newValue)) {
            d = new Difference();
            d.setOldValue(stringify(oldValue, maxStringLength));
            d.setNewValue(stringify(newValue, maxStringLength));
        }

        return d;
    }

    static public Difference diff(Object oldValue, Object newValue, int maxStringLength) {
        return diff(oldValue, newValue, maxStringLength, false);
    }

    static public Difference diff(Object oldValue, Object newValue) {
        return diff(oldValue, newValue, MAX_STRING_LENGTH);
    }

    /**
     * Return whether the given object is an empty collection.
     */
    static private boolean isEmptyCollection(Object o) {
        if ((null != o) && (o instanceof Collection) && ((Collection) o).isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Compare two collections and factor out the differences.
     * Changes in element order are not considered differences.
     * Both collections must be non-null at this point.
     * 
     * Collections will usually be implemented as Lists, but semantically
     * they are almost always Sets and will not contain nulls or
     * duplicates. Still it is not that hard to recognize duplicate values.
     */
    static private Difference diffCollections(Collection oldValue, 
                                              Collection newValue,
                                              int maxStringLength,
                                              boolean caseInsensitive) {

        Difference d = null;
        List old = new ArrayList();
        old.addAll(oldValue);
        List neu = new ArrayList();
        neu.addAll(newValue);

        for (Object o : old) {
            if (!neu.remove(o)) {
                String same = null;
                if (caseInsensitive && o instanceof String) {
                    same = Util.find(new ArrayList<String>(neu), (String) o, String.CASE_INSENSITIVE_ORDER);
                    if (same != null) {
                        neu.remove(same);
                    }
                }

                if (same == null) {
                    if (d == null)
                        d = new Difference();
                    d.addRemovedValue(stringify(o, maxStringLength));
                }
            }
        }

        // anything left over are new
        if (neu.size() > 0) {
            if (null == d)
                d = new Difference();
            for (Object o : neu) {
                d.addAddedValue(stringify(o, maxStringLength));
            }
        }

        if (d != null) {
            // if any diffs detected also generate the summary string
            d.setOldValue(stringify(oldValue, maxStringLength));
            d.setNewValue(stringify(newValue, maxStringLength));
        }

        return d;
    }

    static private Difference diffCollections(Collection oldValue,
                                              Collection newValue,
                                              int maxStringLength) {

        return diffCollections(oldValue, newValue, MAX_STRING_LENGTH, false);
    }

    static private Difference diffCollections(Collection oldValue,
                                              Collection newValue) {

        return diffCollections(oldValue, newValue, MAX_STRING_LENGTH);
    }

    /**
     * Helper for diff, promote an atomic value to a collection
     * of one value.
     */
    static private List coerceList(Object element) {
        List list = null;
        if (element != null) {
            list = new ArrayList();
            list.add(element);
        }
        return list;
    }

    /**
     * Helper for diff, convert a list of objects into a List<String>
     */
    static public List<String> listify(Collection src, int maxStringLength) {

        List<String> strings = null;
        if (src != null) {
            strings = new ArrayList<String>();
            for (Object o : src) {
                // preserve null elements?
                if (o != null)
                    strings.add(stringify(o, maxStringLength));
            }
        }
        return strings;
    }

    static public List<String> listify(Collection src) {
        return listify(src, MAX_STRING_LENGTH);
    }

    /**
     * Render a value as a string.
     * If it is a long string, truncate it and add ellipses.
     * If it is a list, render it as [ el1, el2, ...] and
     * truncate if too long.
     * 
     * @ignore
     * TODO: not supporting nested lists or other complex values.   
     * The max string length should be shorter for list elements 
     * if we're going to be applying MAX_STRING_LENGTH to the
     * rendering of the list as a whole.
     * 
     */
    static public String stringify(Object value, int maxStringLength) {

        String str = null;

        if (value instanceof Collection) {
            Collection c = (Collection)value;
            StringBuffer b = new StringBuffer();
            Iterator it = c.iterator();
            int count = 0;

            b.append("[");
            while (it.hasNext()) {
                if (maxStringLength > 0 && b.length() > maxStringLength) {
                    b.append(",...");
                    break;
                }
                else {
                    if (count > 0) b.append(",");
                    String s = renderString(it.next(), maxStringLength);
                    if (s != null)
                        b.append(s);
                    count++;
                }
            }
            b.append("]");
            
            str = b.toString();
        }
        else if (value != null)
            str = renderString(value, maxStringLength);

        return str;
    }

    static public String stringify(Object value) {
        return stringify(value, MAX_STRING_LENGTH);
    }

    /**
     * Render an object as a string, truncating and adding ellipses
     * if it is "too long".
     */
    static private String renderString(Object o, int maxStringLength) {
        
        String str = null;
        if (o != null) {
            if (!(o instanceof SailPointObject))
                str = o.toString();
            else {
                SailPointObject spo = (SailPointObject)o;
                str = spo.getName();
                if (str == null) {
                    str = spo.getId();
                    if (str == null)
                        str = o.toString();
                }
            }

            if (maxStringLength > 0 && str.length() > maxStringLength)
                str = str.substring(0, maxStringLength) + "...";
        }
        return str;
    }

    static private String renderString(Object o) {
        return renderString(o, MAX_STRING_LENGTH);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Map Comparison
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Compare two Maps generating a list of Difference objects.
     * It is assumed that flat maps are being dealt with here and don't
     * need to descend into complex child objects, other than compare
     * them for equality.
     *
     * The exclusion list might contain keys that are to be ignored.
     *
     * The max value contains a limit, when set this is usually 1 
     * to indicate that it is just checking to see if anything is
     * different.
     */
    public static List<Difference> diffMaps(Map map1, Map map2, 
                                            List exclusions, int maxStringLength, int maxDiff) {

        List<Difference> differences = null;
        int diffcount = 0;

        if (map1 == null) {
            if (map2 != null)
              differences = allDiffs(map2, exclusions, maxDiff, true, maxStringLength);
        }
        else if (map2 == null) {
            differences = allDiffs(map1, exclusions, maxDiff, false, maxStringLength);
        }
        else {
            // compare things in map1 with map2
            List map2keys = new ArrayList(map2.keySet());
            Iterator<Map.Entry> it = map1.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry e1 = it.next();
                Object key = e1.getKey();
                // ignore things with no keys?
                if (key != null && 
                    (exclusions == null || !exclusions.contains(key))) {
                    Object value1 = e1.getValue();
                    Object value2 = map2.get(key);
                    map2keys.remove(key);
                    Difference d = diff(value1, value2, maxStringLength);
                    if (d != null) {
                        d.setAttribute(key.toString());
                        if (differences == null)
                          differences = new ArrayList<Difference>();
                        differences.add(d);
                        diffcount++;
                        if (maxDiff > 0 && diffcount >= maxDiff)
                          break;
                    }
                }
            }

            // anything left over in this list was not found in map1, 
            // and is considered is a new value
            if (maxDiff == 0 || diffcount < maxDiff) {
                Iterator<Object> keys = map2keys.iterator();
                while (keys.hasNext()) {
                    Object key = keys.next();
                    if (key != null &&
                        (exclusions == null || !exclusions.contains(key))) {
                        Object value2 = map2.get(key);
                        // NOTE: If the value is null, treat this as being
                        // the same as the key being missing.  This is 
                        // important for some maps that can get decorated
                        // with attributes that don't have values yet.
                        if (value2 != null) {
                            Difference d = new Difference();
                            d.setAttribute(key.toString());
                            d.setNewValue(stringify(value2, maxStringLength));
                            if (value2 instanceof Collection)
                                d.setAddedValues(listify((Collection)value2, maxStringLength));
                            if (differences == null)    
                                differences = new ArrayList<Difference>();
                            differences.add(d);
                            diffcount++;
                            if (maxDiff > 0 && diffcount >= maxDiff)
                                break;
                        }
                    }
                }
            }
        }

        return differences;
    }


    /**
     * Helper for diffMaps. 
     * In cases where two maps are being compared and one of them
     * is null, create a list of Differences for every element in the map.
     * The "neu" flag indicates which side of the difference IdentityIQ is on,
     * when true it means that these are the new values, when false it
     * means they were the old values.
     */
    private static List<Difference> allDiffs(Map map, List exclusions,
                                             int maxDiff, boolean neu, int maxStringLength) {

        // if the Map happens to be empty treat it like null
        List<Difference> differences = null;
        if (map.size() > 0) {
            int diffcount = 0;
            differences = new ArrayList<Difference>();
            Iterator<Map.Entry> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry e1 = it.next();
                Object key = e1.getKey();
                Object value = e1.getValue();
                // ignore things that dont' have keys?
                if (key != null &&
                    (exclusions == null || !exclusions.contains(key))) {
                    Difference d = new Difference();
                    d.setAttribute(key.toString());
                    if (neu) {
                        d.setNewValue(stringify(value, maxStringLength));
                        if (value instanceof Collection)
                            d.setAddedValues(listify((Collection)value, maxStringLength));
                    }
                    else {
                        d.setOldValue(stringify(value, maxStringLength));
                        if (value instanceof Collection)
                            d.setRemovedValues(listify((Collection)value, maxStringLength));
                    }
                    differences.add(d);
                    diffcount++;
                    if (maxDiff > 0 && diffcount >= maxDiff)
                      break;
                }
            }
        }

        return differences;
    }

    public static List<Difference> diffMaps(Map map1, Map map2) {

        return diffMaps(map1, map2, null);
    }

    public static List<Difference> diffMaps(Map map1, Map map2, 
                                            List exclusions) {

        return diffMaps(map1, map2, exclusions, MAX_STRING_LENGTH);
    }
    
    public static List<Difference> diffMaps(Map map1, Map map2, 
                                            List exclusions, int maxStringLength) {
        
        return diffMaps(map1, map2, exclusions, maxStringLength, 0); 
    }

    /**
     * Return true if two Maps have differences.
     * Stop as soon as any difference is detected.
     */
    public static boolean equal(Map map1, Map map2) {

        List<Difference> diffs = diffMaps(map1, map2, null, 0, 1);
        return (diffs == null || diffs.size() == 0);
    }
    
    /***
     * Create a copy of this Difference with all the strings truncated  
     * @param maxStringLength Length to truncate strings at
     * @return Difference 
     * @throws GeneralException
     */
    public Difference truncateStrings(int maxStringLength) throws GeneralException {
        
        Difference newDiff = (Difference)this.deepCopy(null);
        newDiff.setOldValue(Difference.stringify(newDiff.getOldValue(), maxStringLength));
        newDiff.setNewValue(Difference.stringify(newDiff.getNewValue(), maxStringLength));
        newDiff.setAddedValues(listify(newDiff.getAddedValues(), maxStringLength));
        newDiff.setRemovedValues(listify(newDiff.getRemovedValues(), maxStringLength));
        
        return newDiff;
    }
    
    /***
     * Create a copy of this Difference with all the strings truncated to default length  
     * 
     * @return Difference 
     * @throws GeneralException
     */
    public Difference truncateStrings() throws GeneralException {
        return truncateStrings(Difference.MAX_STRING_LENGTH);
    }


}
