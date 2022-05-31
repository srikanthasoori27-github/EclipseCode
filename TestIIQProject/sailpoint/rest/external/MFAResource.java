/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.external;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Identity;
import sailpoint.rest.BaseResource;
import sailpoint.service.LoginService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.MFAFilter;

/**
 * This class is used to provide MFA Configuration information to the UI.
 */
@Path("mfa/configuration")
public class MFAResource extends BaseResource {

    /**
     * Returns a list of the names of any applicable MFA workflows for a users that
     * has been previously stored in the session (at present that happens only in 
     * LoginBean).
     * 
     * @return A list of the names of the applicable workflows for the identity stored
     * in the session.
     * @throws GeneralException Thrown if unable to get the username from the session
     * @throws ObjectNotFoundException Thrown if unable to lookup an Identity for the
     * user name.
     */
    @GET
    public List<String> getMFAConfig() throws GeneralException, ObjectNotFoundException {
        String mfaUser = (String)getSession().getAttribute(MFAFilter.MFA_USER_NAME);
        if(Util.isNullOrEmpty(mfaUser)) {
            throw new GeneralException("Unable to get MFA user name");
        }

        SailPointContext context = SailPointFactory.getCurrentContext();
        Identity mfaIdentity = context.getObjectByName(Identity.class, mfaUser);
        if(mfaIdentity == null) {
            throw new ObjectNotFoundException("No identity found for MFA user name");
        }

        LoginService loginService = new LoginService(context);

        return loginService.getApplicableMFAWorkflowNames(mfaIdentity);
    }
}