/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import sailpoint.object.Identity;

/**
 *
 */
public class AdministratorListBean extends BaseListBean<Identity> {

    /**
     *
     */
    public AdministratorListBean() {
        super();
        setScope(Identity.class);

        // jsl - not sure the intent here, but will need to add more
        // filtering...

    }  // AdministratorListBean()

}  // class AdministratorListBean
