/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.ApplicationActivity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;

/*
 *
 */

public class ActivityBean extends BaseObjectBean<ApplicationActivity> {

    
    private Map<String, String> keyValuePairs;
    public ActivityBean() {
        super();
        setScope(ApplicationActivity.class);
    }
    
    public List<String> getAdditionalFieldNames() {
        initializeFieldMap();
        return new ArrayList<String>( keyValuePairs.keySet() );
    }
    
    public Map<String, String> getAdditionalFields() {
        initializeFieldMap();
        return keyValuePairs;
    }

    private void initializeFieldMap() {
        if( keyValuePairs != null ) {
            return;
        }
        keyValuePairs = new HashMap<String, String>();
        ApplicationActivity activity = getObject();
        String info = activity.getInfo();
        if (Util.isNotNullOrEmpty(info)) {
            String[] nameValuePairs = info.split( "\\^" );
            if (nameValuePairs != null) {
                for( String s : nameValuePairs ) {
                    int indexOf = s.indexOf( "=" );
                    if( indexOf < 0 )
                        keyValuePairs.put( "info", s );
                    else {
                        String key = s.substring( 0, indexOf );
                        String value = s.substring( indexOf + 1, s.length() );
                        keyValuePairs.put( key, value );
                    }
                }
            }
        }
    }
    
    @Override
    public ApplicationActivity getObject() {
        try {
            return super.getObject();
        } catch ( GeneralException e ) {
            throw new RuntimeException( "Unable to fetch Application Activity with id : " + getObjectId() );
        }
    }

}
