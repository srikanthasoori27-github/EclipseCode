/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bean for editing ObjectConfig:ManagedAttirbute.
 * We only allow the definition of simple attributes.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.system;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;

public class ManagedAttributeConfigBean extends ObjectConfigBean {
    private static final Log log = LogFactory.getLog(ManagedAttributeConfigBean.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Session
    //
    //////////////////////////////////////////////////////////////////////

    public ManagedAttributeConfigBean() throws GeneralException {
        super();
        setObjectName(ObjectConfig.MANAGED_ATTRIBUTE);
    }

    /**
     * BaseObjectBean overload in case we don't have
     * one of these yet.
     */
    public ObjectConfig createObject() {

        ObjectConfig config = new ObjectConfig();
        config.setName(ObjectConfig.MANAGED_ATTRIBUTE);

        return config;
    }

}
