/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.json.JSONWriter;
import org.json.JSONException;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public interface JSONSerializable {

    String getJson() throws JSONException;
    void getJson(JSONWriter writer) throws JSONException;

}
