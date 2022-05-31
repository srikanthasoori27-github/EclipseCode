/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;

public class AttributesDataSource 
       implements JRDataSource, JRRewindableDataSource, XMLReferenceResolver {

    /**
     * The original set of entries that the data source contained.
     * Need to store this so we can reset the iterator if necessary.
     */
    private Set<Map.Entry> _setOfEntries;
    
    /**
     *
     */
    Iterator<Map.Entry> _entries;

    /**
     *
     */
    Map.Entry _current;
	
    /**
     *
     */
    public AttributesDataSource(Attributes map) {
        if (map != null  && map.size() > 0) {
            TreeMap tm = new TreeMap(map);
            _setOfEntries = tm.entrySet();
            _entries = _setOfEntries.iterator();
        }  
    }
    
    public AttributesDataSource(String xml) {
        super();
        try {

            if (( xml != null ) && (xml.trim().length() > 0)) {
                Attributes map = (Attributes)
                    XMLObjectFactory.getInstance().parseXml(this, xml, false);
                if (map != null  && map.size() > 0) {
                    TreeMap tm = new TreeMap(map);
                    _entries = tm.entrySet().iterator();
                }   
            }
        } catch(Exception e ) {
            System.out.println("Error parsing xml." + e.toString());
        }
    }
	
    /**
     * Moves the iterator for this datasource to the next entry and returns true if the next entry existed.
     * Returns false otherwise.
     */
    public boolean next() {
        boolean hasMore = false;
        if ( _entries != null ) {
            hasMore = _entries.hasNext();
            if ( hasMore ) {
                _current = _entries.next();
            } else {
                _current = null;
            }
        }
        return hasMore;
    }
    
    /**
     * Resets the iterator for this datasource
     */
    public void moveFirst() throws JRException {
        if (_setOfEntries != null && !_setOfEntries.isEmpty()) {
            _entries = _setOfEntries.iterator();
        } else {
            _entries = null;
        }
        
    }
	
    /**
     *
     */
    public Object getFieldValue(JRField field) {

        String s = null;
        if ( _current != null ) {
            String name = field.getName();
            if ( "key".compareToIgnoreCase(name) == 0 ) {
                s = (String)_current.getKey();
            } else 
            if ( "value".compareToIgnoreCase(name) == 0 ) {
                Object value = _current.getValue();
                if ( value != null ) {
                    String key = (String)_current.getKey();
                    if (key != null && key.toLowerCase().contains("password")) {
                        s = "********";
                    } else {
                        if ( value instanceof List ) {
                            List list = (List)value;
                            s = Util.listToCsv(list);
                        } else {
                            s = value.toString();
                        }
                    }
                }
            }
        }
        return s;
    }

    // For XML serialization.. TODO: Will we have to deal with references..
    // probably not..
    public Object getReferencedObject(String className, String id, String name)
        throws GeneralException {
        return "";
    }
}
