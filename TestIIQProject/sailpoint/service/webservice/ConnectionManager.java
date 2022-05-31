/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import javax.ws.rs.client.WebTarget;

public interface ConnectionManager {
    WebTarget getWebTarget() throws Exception;
    void reset();
    boolean isConfigured();
}
