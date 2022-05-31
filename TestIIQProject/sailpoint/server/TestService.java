/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service class used for testing service registration.
 *
 * Author: Jeff
 *
 *
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;


public class TestService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TestService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Test";

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public TestService() {
        _name = NAME;
        _interval = 10;
    }

    @Override
    public void configure(SailPointContext context) {
        // TODO: We'll have a ServiceDefinition, adapt to anything?
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called each execution interval.
     */
    public void execute(SailPointContext context) throws GeneralException {

        System.out.println("TestService");
    }


}
