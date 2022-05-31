/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.rest.ui.suggest.IdentitySuggestResource;
import sailpoint.service.pam.PamIdentitySuggestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Resource for pam identity specific suggests.  The purpose of this suggest is to return a list of identities
 * that is decorated with a list of the accounts that they have on the PAM application.  The list of accounts is used
 * to determine whether we can add the identity to a safe
 */
@Path("pam/identities/suggest")
public class PamIdentitySuggestResource extends IdentitySuggestResource {


    public PamIdentitySuggestResource() {
        super();
    }


    /**
     * Return the list of identities that apply to the provided search criteria in the query parameters along with
     * their accounts on the PAM Application
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("{applicationName}")
    public ListResult getIdentities(@PathParam("applicationName") String applicationName) throws GeneralException {
        // Since only the default super constructor is initiated for this resource,
        // we need to do some massaging of data to get stuff out of the extra params map.
        if (getExtraParamMap() != null) {
            this.suggestContext = (String)getExtraParamMap().get(PARAM_SUGGEST_CONTEXT);
            this.suggestId = (String)getExtraParamMap().get(PARAM_SUGGEST_ID);
        }

        if (this.suggestId == null && this.suggestContext == null) {
            throw new InvalidParameterException(new Message("Context or suggestId is required"));
        }

        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        PamIdentitySuggestService pamIdentitySuggestService = new PamIdentitySuggestService(getContext());

        return pamIdentitySuggestService.getIdentities(getQueryOptions(), applicationName);
    }

    /**
     * Add the limit/start properties to the query options
     * @return
     * @throws GeneralException
     */
    protected QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        if (ops == null) {
            throw new GeneralException("Could not find IdentityFilter for identity suggest");
        }

        if (this.limit > 0) {
            ops.setResultLimit(this.limit);
        }
        if (this.start > 0) {
            ops.setFirstRow(this.start);
        }
        return ops;
    }
}
