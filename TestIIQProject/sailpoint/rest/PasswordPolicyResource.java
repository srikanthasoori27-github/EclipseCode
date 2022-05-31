package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 *
 *
 * User: justin.williams
 * Date: 1/14/13
 */
@Path("passwordPolicy")
public class PasswordPolicyResource extends BaseResource {

    /**
     * Creates a display friendly unified set of constraints from various accounts
     *
     * @return An ObjectResult with a list of constraint text values
     * @throws GeneralException If we were unable to create valid set of constraints from the passed values
     */
    @POST @Path("mergeConstraints")
    public ObjectResult getUnifiedConstraints(@FormParam("accountIds") String accountIdsJson) throws GeneralException {
        authorize(new LcmEnabledAuthorizer());
        List<String> accountIds = JsonHelper.listFromJson(String.class, accountIdsJson);
        List<String> constraints = mergeConstraints(accountIds);
        ObjectResult result = new ObjectResult(constraints);
        return result;
    }

    /**
     * Creates a display friendly unified set of constraints from all of an Identity's account less the
     * passed excluded accounts
     *
     * @return An ObjectResult with a list of constraint text values
     * @throws GeneralException If we were unable to create valid set of constraints from the passed values
     */
    @POST @Path("mergeAllConstraints")
    public ObjectResult mergeAllConstraints(@FormParam("identity") String identityId, @FormParam("accountIds") String excludedAccountsJson) throws GeneralException {
        authorize(new LcmEnabledAuthorizer());
        List<String> accountIds = JsonHelper.listFromJson(String.class, excludedAccountsJson);
        List<String> links = new ArrayList<String>();
        LinkService service = new LinkService(getContext());
        Identity identity = getContext().getObjectById(Identity.class, identityId);
        List<Link> allLinks = identity.getLinks();

        // Build list of all accounts that support set password
        for (Link lnk : allLinks) {
            if (service.supportsSetPassword(lnk)) {
                links.add(lnk.getId());
            }
        }

        // Remove excluded accounts
        if (links != null && accountIds != null) {
            for (String id: accountIds) {
                links.remove(id);
            }
        }

        List<String> constraints = mergeConstraints(links);
        return new ObjectResult(constraints);
    }

    /**
     * Gets the names of any password policies, including the IIQ password policy, that
     * have any constraints that conflict with one-way hashing of passwords.
     * @return A list of policy names in conflict.
     * @throws GeneralException
     */
    @GET
    @Path("invalidHashPolicies")
    public ListResult getInvalidHashPolicies() throws GeneralException {
        authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));

        PasswordPolice police = new PasswordPolice(getContext());
        List<String> invalidPolicies = police.findInvalidHashingPolicies();

        return new ListResult(invalidPolicies, invalidPolicies.size());
    }

    private List<String> mergeConstraints(List<String> accountIds) throws GeneralException {
        List<PasswordPolicy> policies = fetchPasswordPolicies(accountIds);
        List<String> constraints;
        try {
            PasswordPolicy unifiedPolicy = new PasswordPolicy();
            unifiedPolicy.assimilatePolicies(policies);
            constraints = unifiedPolicy.convertConstraints(getLocale(), getUserTimeZone());
        } catch (PasswordPolicyException ex) {
            constraints = new ArrayList<String>();
            Message message = new Message(MessageKeys.LCM_MANAGE_PASSWORDS_SYNC_PASSWORDS_CONFLICT);
            constraints.add(message.getLocalizedMessage(getLocale(), getUserTimeZone()));
        }
        return constraints;
    }

    private List<PasswordPolicy> fetchPasswordPolicies(List<String> accountIds) throws GeneralException {
        SailPointContext context = getContext();
        List<PasswordPolicy> policies = new ArrayList<PasswordPolicy>();
        PasswordPolice police = new PasswordPolice(getContext());
        for (String accountId : accountIds) {
            Link link = (Link) getContext().getObjectById(Link.class, accountId);
            PasswordPolicy policy = police.getEffectivePolicy(link);
            if(policy != null) {
                policies.add(policy);
            }
        }
        return policies;
    }
}
