/*
 * (c) Copyright 2019. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.me;

import sailpoint.api.Identitizer;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.object.Identity;
import sailpoint.object.Source;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Resource to get/set a users ui preferences.
 *
 * @author peter.holcomb
 */
public class UiPreferencesResource extends BaseResource {
    public UiPreferencesResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Update a ui preference with the given key to be the given value
     * @param key
     * @param value
     * @throws GeneralException
     */
    @PUT
    @Path("{key}")
    public void updateUiPreference(@PathParam("key") String key, Object value) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        if (value != null) {

            Identity currentUser = this.getLoggedInUser();
            if(currentUser == null) {
                throw new GeneralException("User is not logged in");
            }
            currentUser.setUIPreference(key, value);
            
            // Use the identitizer to get auditing of this change
            Identitizer i = new Identitizer(getContext());
            i.setRefreshSource(Source.UI, getLoggedInUserName());
            i.saveAndTrigger(currentUser, true);
        }
    }

    /**
     * Return the value of a preference with the given key
     * @param key
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("{key}")
    public Object getUiPreference(@PathParam("key") String key) throws GeneralException {
        authorize(new AllowAllAuthorizer());

        Identity currentUser = this.getLoggedInUser();
        if(currentUser == null) {
            throw new GeneralException("User is not logged in");
        }
        return currentUser.getUIPreference(key);
    }
}
