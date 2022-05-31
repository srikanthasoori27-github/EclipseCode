/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.policy;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.TargetAssociation;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.EffectiveAccessListService;
import sailpoint.service.TargetAssociationDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

import java.util.ArrayList;
import java.util.List;

public class EffectiveEntitlementSODPolicyExecutor extends EntitlementSODPolicyExecutor {


    public static String CONTRIBUTING_ENT_COLUMNS = "contributingEntitlementColumns";

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    @Override
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy,
                                          Identity identity)
            throws GeneralException {

        List<PolicyViolation> violations = super.evaluate(context, policy, identity);

        //Get the Contributing Entitlements
        for (PolicyViolation violation : Util.safeIterable(violations)) {
            List<IdentitySelector.MatchTerm> clonedTerms = new ArrayList<>();
            for (IdentitySelector.MatchTerm term : Util.safeIterable(violation.getViolatingEntitlements())) {
                term = (IdentitySelector.MatchTerm)XMLObjectFactory.getInstance().clone(term, context);
                if (term.shouldCheckEffective()) {
                    //Decorate term with contributing entitlements
                    List<IdentitySelector.MatchTerm.ContributingEntitlement> conts = getContributingEntitlements(term,
                            context, identity);

                    if (!Util.isEmpty(conts)) {
                        term.setContributingEntitlements(conts);
                    }
                }
                clonedTerms.add(term);
            }
            violation.setViolatingEntitlements(clonedTerms);
        }


        return violations;
    }

    private List<IdentitySelector.MatchTerm.ContributingEntitlement> getContributingEntitlements(IdentitySelector.MatchTerm term, SailPointContext ctx, Identity ident)
        throws GeneralException {

        List<IdentitySelector.MatchTerm.ContributingEntitlement> ents = new ArrayList<>();
        if (term != null) {
            EffectiveAccessListService svc = new EffectiveAccessListService(ctx, null, new BaseListResourceColumnSelector(CONTRIBUTING_ENT_COLUMNS));
            String appName = term.getApplication() != null ? term.getApplication().getName() : null;
            ListResult result = null;
            if (term.isPermissionType()) {
                result = svc.getIdentityEffectiveAccess(ident.getId(), appName, null, term.getName(), term.getValue(), TargetAssociation.TargetType.P);
            } else if (term.isEntitlementType()) {
                result = svc.getIdentityEffectiveAccess(ident.getId(), appName, term.getName(), term.getValue(),null, null);
            } else if (IdentitySelector.MatchTerm.Type.TargetPermission == term.getType()) {
                if (Util.isNullOrEmpty(appName)) {
                    //See about TargetSource
                    appName = term.getTargetSource() != null ? term.getTargetSource().getName() : appName;
                }
                result = svc.getIdentityEffectiveAccess(ident.getId(), appName, null, term.getName(), term.getValue(), TargetAssociation.TargetType.TP);
            }

            if (result != null && result.getCount() > 0) {
                List<TargetAssociationDTO> objects = result.getObjects();
                //Convert to ContributingEntitlement model
                for (TargetAssociationDTO dto : Util.safeIterable(objects)) {
                    IdentitySelector.MatchTerm.ContributingEntitlement ent = new IdentitySelector.MatchTerm.ContributingEntitlement();
                    ent.setPath(dto.getHierarchy());
                    ent.setSource(dto.getApplicationName());
                    ent.setType(dto.getType());
                    ent.setClassificationNames(dto.getClassificationNames());
                    ents.add(ent);
                }
            }

        }

        return ents;


    }

}
