/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bean for editing ObjectConfig:Identity.
 *
 * Author: Jeff
 *
 * NOTE: This class is not used yet, need to port
 * over identity/IdentityConfigBean to use the new
 * simpler model.
 */
package sailpoint.web.system;


import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;

public class IdentityObjectConfigBean extends ObjectConfigBean {

    public IdentityObjectConfigBean() throws GeneralException {
        super();
        setObjectName(ObjectConfig.IDENTITY);
    }

    /**
     * BaseObjectBean overload in case we don't have
     * one of these yet.
     */
    public ObjectConfig createObject() {

        ObjectConfig config = new ObjectConfig();
        config.setName(ObjectConfig.IDENTITY);

        return config;
    }

}

