/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bean for editing ObjectConfig:Link.
 *
 * Author: Jeff
 *
 * NOTE: This class is not used yet, need to port
 * over identity/LinkConfigBean to use the new
 * simpler model.
 */
package sailpoint.web.system;


import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;

public class LinkObjectConfigBean extends ObjectConfigBean {

    public LinkObjectConfigBean() throws GeneralException {
        super();
        setObjectName(ObjectConfig.LINK);
    }

    /**
     * BaseObjectBean overload in case we don't have
     * one of these yet.
     */
    public ObjectConfig createObject() {

        ObjectConfig config = new ObjectConfig();
        config.setName(ObjectConfig.LINK);

        return config;
    }

}
