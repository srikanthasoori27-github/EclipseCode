/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bean for editing ObjectConfig:Application.
 * We only allow the definition of simple attributes.
 *
 * Author: Jeff
 *
 */
package sailpoint.web.system;


import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;

public class ApplicationObjectConfigBean extends ObjectConfigBean {

    public ApplicationObjectConfigBean() throws GeneralException {
        super();
        setObjectName(ObjectConfig.APPLICATION);
    }

    /**
     * BaseObjectBean overload in case we don't have
     * one of these yet.
     */
    public ObjectConfig createObject() {

        ObjectConfig config = new ObjectConfig();
        config.setName(ObjectConfig.APPLICATION);

        return config;
    }


}
