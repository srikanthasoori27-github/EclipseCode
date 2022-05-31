/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A wrapper for the results of a projection search, 
 * based on activity/ActivityProxy.
 *
 * Author: Jeff (ActivityProxy by Peter)
 *
 * This basically just provides named lookup of search result
 * columns, but I also carried over some time zone stuff from
 * ActivityProxy that looked important.  Where possible we should
 * try to use this rather than bringing in the entire object as
 * is currently being done with IdentityProxy and ActivityProxy.
 * This causes less Hiberante stress and only gets those columns
 * you actually want to display.
 * 
 */
package sailpoint.web;

import java.text.DateFormat;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;

public class SearchProxy extends AbstractMap<String, String> {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    Object[] _row;
    List<String> _columns;
    TimeZone _timeZone;
    Locale _locale;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public SearchProxy() {
    }

    public SearchProxy(Object[] row, List<String> cols) {
        _row = row;
        _columns = cols;
    }

    /**
     * Todo: The TimeZone and Locale stuff is a hack so that we can format 
     * dates within this class. Ideally date formatting should happen in the jsf.
     * 
     * @param row
     * @param cols
     * @param timeZone TimeZone to format dates with
     * @param locale Locale to format dates with
     */
    public SearchProxy(Object[] row, List<String> cols, TimeZone timeZone, Locale locale) {
        _row = row;
        _columns = cols;
        _timeZone = timeZone;
        _locale = locale;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convenience method which formats the date using the standard format used on web
     * pages.  
     * 
     * Todo: This is a hack and should be refactored out. 
     * 
     * @param date Date to format
     * @return Formatted date using Date=DateFormat.SHORT and Time=DateFormat.MEDIUM
     */	
    private String dateToString(Date date){
    	return dateToString(date, _timeZone, _locale);
    }
    
    /**
     * Converts a date to the standard format used in this app's web pages so that 
     * every single call to the Util class does not need to pass in the DateFormat
     * params. This static method exists so that the static get() method can call this.
     * 
     * Todo: This is a hack and should be refactored out. 
     * 
     * @param date Date to format
     * @param timeZone Timezone to format with
     * @param locale Locale to format with
     * @return Formatted date using Date=DateFormat.SHORT and Time=DateFormat.MEDIUM
     */
    private static String dateToString(Date date, TimeZone timeZone, Locale locale){
    	return Util.dateToString(date, DateFormat.SHORT, DateFormat.MEDIUM, timeZone, locale);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Column Lookup
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see java.util.AbstractMap#entrySet()
     */
    @Override
    public Set<java.util.Map.Entry<String, String>> entrySet() {
        // TODO Auto-generated method stub
        return null;
    }

    public String get(Object key) {

        String value = null;

        if (_row != null && _columns != null) {
            Object cell = getCell(key);
            if (cell instanceof Date)
                value = dateToString((Date)cell);
            else if (cell instanceof SailPointObject)
                value = ((SailPointObject)cell).getName();
            else if (cell != null)
                value = cell.toString();
        }

        return value;
    }

    private Object getCell(Object key) {

        Object value = null;
        int index = _columns.indexOf(key);

        if (index >= 0)
            value = _row[index];
        return value;
    }

}
