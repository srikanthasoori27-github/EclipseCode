/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.external;

import sailpoint.object.Configuration;
import sailpoint.rest.ui.form.BaseFormResource;

import javax.ws.rs.Path;

/**
 * The external form resource.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Path("forms")
public class FormResource extends BaseFormResource {

    /**
     * The form processing relies on a UserContext. Since here we have no
     * authenticated user we will use the self registration workgroup user
     * that is configured the system configuration.
     *
     * @return The self registration workgroup name.
     */
    @Override
    public String getLoggedInUserName() {
        return Configuration.getSystemConfig().getString(Configuration.SELF_REGISTRATION_WORKGROUP);
    }

}
