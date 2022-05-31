/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.Request;
import sailpoint.tools.GeneralException;

public class RequestObjectBean extends BaseObjectBean<Request> {

    /**
     *
     */
    public RequestObjectBean() {
        super();
        setScope(Request.class);
        setStoredOnSession(false);
    } // RequestObjectBean()

    /**
     *
     */
    public String getXml() {
        String value = "";
        try {
            Request req = getObject();
            if ( req != null )
                value = req.toXml();
        } catch (GeneralException ex) {
            value = ex.getLocalizedMessage();
        }

        return value;
    }  // getXml()

}  // class RequestObjectBean
