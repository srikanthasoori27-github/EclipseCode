/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.activity;

import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import sailpoint.object.ApplicationActivity;
import sailpoint.object.SailPointObject;
import sailpoint.tools.Internationalizer;

/**
 * @author peter.holcomb
 *
 */
public class ActivityProxy extends AbstractMap<String, String> {

    /**
     * 
     */
    ApplicationActivity _activity;
    Object[] _row;
    List<String> _columns;
    TimeZone _timeZone;
    Locale _locale;
    
    /**
     * Todo: The TimeZone and Locale stuff is a hack so that we can format 
     * dates within this class. Ideally date formatting should happen in the jsf.
     * 
     * @param id ApplicationActivity ID
     * @param timeZone TimeZone to format dates with
     * @param locale Locale to format dates with
     */
    public ActivityProxy(ApplicationActivity id, TimeZone timeZone, Locale locale) {
        _activity = id;
        _timeZone = timeZone;
        _locale = locale;
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
    public ActivityProxy(Object[] row, List<String> cols, TimeZone timeZone, Locale locale) {
        _row = row;
        _columns = cols;
        _timeZone = timeZone;
        _locale = locale;
    }

    public ActivityProxy() {
        // TODO Auto-generated constructor stub
    }


    public String get(Object key) {

        String value = null;
        if (_activity != null)
            value = get(_activity, key, _timeZone, _locale);

        else if (_row != null && _columns != null) {
            Object cell = getCell(key);
            if (cell instanceof Date){
                value = Internationalizer.getLocalizedDate((Date)cell, _locale, _timeZone);
            } else if (cell instanceof SailPointObject)
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
    
    /**
     * Returns string value for property pulled from the given activity. 
     * 
     * Todo: The timezone and locale stuff is a hack so the internationalization
     * could be added to this class without completely breaking the API. I would suggest
     * the formatting of the different properties be moved to the jsf file, at least for
     * dates.
     * 
     * @param act Activity
     * @param key 
     * @param timeZone TimeZone to use when formatting dates.
     * @param locale Locale to use when formatting dates.
     * @return Formatted value for the requested property
     */
    private static String get(ApplicationActivity act, Object key, TimeZone timeZone, Locale locale) {

        String value = null;

        if (key == null) {
            // can happen in theory
        }
        else if (key.equals("id"))
            value = act.getId();

        else if (key.equals("name")){
            value = act.getName();
        }
        else if (key.equals("timeStamp")){
            value = Internationalizer.getLocalizedDate(act.getTimeStamp(), locale, timeZone);
        }
        else if (key.equals("sourceApplication")){
            value = act.getSourceApplication();
        }
        else if (key.equals("action")){
            value = act.getAction().name();
        }
        else if (key.equals("result")){
            value = act.getResult().name();
        }
        else if (key.equals("dataSource")){
            value = act.getDataSource();
        }
        else if (key.equals("userName")){
            value = act.getUser();
        }
        else if (key.equals("identityName")){
            value = act.getIdentityName();
        }
        else if (key.equals("target")){
            value = act.getTarget();
        }
        else if (key.equals("info")){
            value = act.getInfo();
        }
        return value;
    }

    /**
     * Wrapper method for the full get() passing in null timezone and locale. This method
     * was added while refactoring for Locale and TimeZone support. There's some refactoring 
     * that needs to make this more elegant and clean but for the time being I left it in to 
     * avoid breaking the API.
     * 
     * @param act Activity
     * @param key object key
     * @return property value for given key
     */
    public static String get(ApplicationActivity act, Object key) {
    	return get(act, key, null, null);
    }
    
    /* (non-Javadoc)
     * @see java.util.AbstractMap#entrySet()
     */
    @Override
    public Set<java.util.Map.Entry<String, String>> entrySet() {
        // TODO Auto-generated method stub
        return null;
    }

}
