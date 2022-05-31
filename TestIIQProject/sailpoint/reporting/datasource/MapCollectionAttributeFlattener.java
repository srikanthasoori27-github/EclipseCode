/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRRewindableDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * This is a specialized datasource for use when a report is rendering
 * a csv grid report. Most of the subreports in the "summary" type reports
 * are sourced my a JRMapCollectionDataSource which is a collection of 
 * maps. 
 * 
 * This class can be used to flatten a single value from each map 
 * and build a csv list of unique values.
 *
 * Example datasource expression that can be used...
 *
 * ( $P{renderType} == "csv" ) 
 *  ?  (JRRewindableDataSource) new MapCollectionAttributeFlattener($F{businessRoleMapList},"bundleName")
 *  :  (JRRewindableDataSource) new JRMapCollectionDataSource($F{businessRoleMapList})
 * 
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class MapCollectionAttributeFlattener implements JRRewindableDataSource {

    private static Log _log = LogFactory.getLog(MapCollectionAttributeFlattener.class);

    /**
     * Initial set of maps that will be flattened.
     */
    private Collection<Map> _records = null;

    /**
     * Iterator over our single flatten map.
     */
    private Iterator<Map> _iterator = null;

    /**
     * The one map with the flattened attribute.
     */
    private Map _current;

    /**
     * Name of the attribute in each map that should be
     * flatten into a single csv list.
     */
    private String _attribute;
	
    /**
     * Given a list of maps and an attribute, build a single map 
     * with a single map with all of the values from the list of maps
     * merged in csv form.
     */
    public MapCollectionAttributeFlattener(Collection<Map> list, 
                                           String attributeName ) {
        _current = null;
        if ( attributeName != null )  {
            _attribute = attributeName;
            _records = list;
            flattenCollection();
        }
    }

    /**
     * Go through each of the Maps, pull out the attribute specified
     * in the constructor and build a list of all of the unique values.
     */
    private List<String> flattenValues() {
        List<String> values = new ArrayList<String>();
        for ( Map map : _records ) {
            if ( _log.isDebugEnabled() ) {
                _log.debug("Map ["+XMLObjectFactory.getInstance().toXml(map)
                           +"]");
            }
            String valStr = Util.getString(map, _attribute);
            if ( ( valStr != null ) && ( !values.contains(valStr) ) ) {
                values.add(valStr);
            }
        }
        return ( values.size() > 0 ) ? values : null;
    }

    /**
     * Given the attribute values build a single 
     * map with one key. The value of the key will
     * be a csv string.
     */
    private Map<String,String> buildMergedMap(List<String> values) {
        Map<String,String> map = new HashMap<String,String>();
        if ( values.size() > 0 ) {
            String csv = Util.listToCsv(values);
            if ( values.size() > 1 ) {
                // Wrap the value in quotes for proper csv formatting
                csv = "\""+csv+"\"";
            }
            if ( _log.isDebugEnabled() ) {
                _log.debug("Generated csv ["+csv+"]");
            }
            // build a single merged map
            if ( csv != null ) {
                map.put(_attribute, csv);
            }
        }     
        return map;
    }

    /*
     * Merge all of the values into one map.
     */
    private void flattenCollection() {
        if ( _records != null ) {
            List<String> values = flattenValues();
            if ( values != null ) {
                Map map = buildMergedMap(values);
                _records = new ArrayList<Map>();
                _records.add(map);
                _iterator = _records.iterator();
            }
        }
    }

    public Object getFieldValue(JRField field) {
        String fieldName = field.getName();
        Object value = null;
        if ( _current != null) {
            if ( fieldName != null  ) {
                value = _current.get(field.getName());
            }
        }
        if ( _log.isDebugEnabled() ) {
            if ( value  != null ) {
                _log.debug("Field ["+fieldName+"] val ["+value.toString()+"]");
            } else {
                _log.debug("Field ["+fieldName+"] val [NULL].");
            }
        }
        return value;
    }
	
    /**
     * Get the next() object from the iterator and 
     * return if there was a new one..
     */
    public boolean next() { 
        boolean hasNext = false;
        if (_iterator != null) {
            hasNext = _iterator.hasNext();
            if (hasNext) {
                _current = (Map)_iterator.next();
            }
        }
        return hasNext;
    }

    /**
     * Reset the source iterator using 
     * the original list.
     */
    public void moveFirst() {
        if (_records != null) {
            _iterator = _records.iterator();
        }
    }
}
