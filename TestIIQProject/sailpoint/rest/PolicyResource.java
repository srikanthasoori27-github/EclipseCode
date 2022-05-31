/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;


/**
 * REST methods for the "polices" resource.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path(IIQClient.RESOURCE_POLICIES)
public class PolicyResource extends BaseResource {

    /**
     * Check the given credentials against the password policies.
     * 
     * @param  credentials  Map with the identity name and password.
     * 
     * @return A Map of a RequestResult that has a "valid" boolean indicating
     *         whether the password is valid or not.
     */
    @POST @Path(IIQClient.PasswordService.Consts.RESOURCE_CHECK_PASSWORD_POLICIES)
    public Map<String,Object> checkPasswordPolicy(Map<String,String> credentials)
        throws GeneralException {

    	authorize(new RightAuthorizer(SPRight.WebServiceRights.CheckPasswordPolicyWebService.name()));
    	
        return getHandler().handlePasswordPolicyCheckReqest(credentials);
    }

    /**
     * Check whether assigning the given roles to the given identity would
     * result in role policy violations.
     * 
     * @param  identity  The name of the identity to which to assign the roles.
     * @param  roles     The names of the roles to check (may be a CSV).
     * 
     * @return A list of violation descriptions or null if there are would be
     *         no violations.
     */
    @GET @Path(IIQClient.RESOURCE_CHECK_ROLE_POLICIES)
    public List<String> checkRolePolicies(@QueryParam(IIQClient.ARG_IDENTITY) String identity,
                                          @QueryParam(IIQClient.ARG_ROLES) List<String> roles)
        throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.CheckRolePoliciesWebService.name()));
        
        roles = fixCSV(roles);
        return getHandler().checkRolePolicies(identity, roles);
    }
}
