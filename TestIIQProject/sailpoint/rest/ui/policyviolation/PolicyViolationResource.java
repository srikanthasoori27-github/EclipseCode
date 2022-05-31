/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.policyviolation;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import sailpoint.authorization.PolicyViolationAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.PolicyViolation;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.certification.RemediationAdviceResult;
import sailpoint.service.policyviolation.PolicyViolationService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.view.DecisionSummary;

/**
 * PolicyViolationResource
 */
public class PolicyViolationResource extends BaseListResource {

    private PolicyViolation policyViolation;
    private PolicyViolationService policyViolationService;

    private static final String REVOKED_ROLES = "revokedRoles";
    private static final String REVOKED_VIOLATION_ENTITLEMENTS = "selectedViolationEntitlements";
    /**
     * Constructor
     *
     * @param parent resource parent
     * @param policyViolationId policy violation id
     */
    public PolicyViolationResource(BaseResource parent, String policyViolationId) throws GeneralException {
        super(parent);
        if (policyViolationId == null) {
            throw new InvalidParameterException("Policy violation id is required");
        }

        policyViolation = getContext().getObjectById(PolicyViolation.class, policyViolationId);

        if (policyViolation == null) {
            throw new ObjectNotFoundException(PolicyViolation.class, policyViolationId);
        }

        policyViolationService = new PolicyViolationService(this);
    }

    /**
     * Get the remediation advice result for this item.
     * Authorize on user since this info is needed for edit.
     * @return RemediationAdviceResult object
     * @throws GeneralException
     */
    @GET
    @Path("remediationAdvice")
    public RemediationAdviceResult getViolationRemediationAdvice() throws GeneralException {
        authorize(new PolicyViolationAuthorizer(policyViolation));
        return policyViolationService.getViolationRemediationAdvice(policyViolation);
    }


    /**
     * Get the remediation summary data for this policy violation.
     * Authorize on user since this info is needed for edit.
     *
     * @param input map of input parameters
     * @return DecisionSummary object
     * @throws GeneralException
     */
    @POST
    @Path("remediationSummary")
    public DecisionSummary getRemediationSummary(Map<String, Object> input) throws GeneralException {
        authorize(new PolicyViolationAuthorizer(policyViolation));
        List<String> revokedRoles = null;
        if (input.containsKey(REVOKED_ROLES)) {
            revokedRoles = (List<String>)input.get(REVOKED_ROLES);
        }

        List<PolicyTreeNode> selectedViolationEntitlements = null;
        if (input.containsKey(REVOKED_VIOLATION_ENTITLEMENTS)) {
            selectedViolationEntitlements = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson((String)input.get(REVOKED_VIOLATION_ENTITLEMENTS));
        }

        return policyViolationService.getRemediationSummary(revokedRoles, selectedViolationEntitlements, policyViolation);
    }

    /**
     * Get the basic suggest resource. Needed for remediation modifiable.
     * @return SuggestResource.
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new PolicyViolationAuthorizer(policyViolation));

        SuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext()
                .add(Application.class.getSimpleName())
                .add(ManagedAttribute.class.getSimpleName())
                .add(Identity.class.getSimpleName());

        return new SuggestResource(this, authorizerContext);
    }
}