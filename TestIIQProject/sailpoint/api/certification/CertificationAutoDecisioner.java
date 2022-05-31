/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.Recommendation;
import sailpoint.service.certification.IdentityCertItemListFilterContext;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class CertificationAutoDecisioner extends CertificationDecisioner {

    private ListFilterService listFilterService;
    private boolean autoApprove;
    private String autoApproveComment;

    public CertificationAutoDecisioner(SailPointContext context, Certification certification,
                                       CertificationDefinition definition, Identity decidingUser) {
        super(context, certification, decidingUser);

        this.certification = certification;
        this.autoApprove = definition.isAutoApprove();

        ListFilterContext filterContext = new IdentityCertItemListFilterContext(certificationId);
        this.listFilterService = new ListFilterService(getContext(), Locale.getDefault(), filterContext);
        this.autoApproveComment =
                new Message(MessageKeys.RECOMMENDER_REQUIRED_COMMENT, (Object)null).getLocalizedMessage();
    }

    /**
     * Go through the items in the certification and automatically set a decision
     * based on the recommendation set on the item.
     *
     * @return DecisionResults describing the outcome of the decisions made.
     * @throws GeneralException
     */
    public DecisionResults autoDecide() throws GeneralException {
        List<Decision> decisions = new ArrayList<>();

        for (CertificationEntity entity : certification.getEntities()) {
            for (CertificationItem item : entity.getItems()) {
                Decision decision = this.getDecisionFromRecommendation(item);
                if (decision != null) {
                    decisions.add(decision);
                }
            }
        }

        if (!decisions.isEmpty()) {
            DecisionResults result = this.decide(decisions);
            return result;
        }

        return null;
    }

    /**
     * Create a cert decision based on the item's recommendation.
     *
     * @param item The certification item to make the decision for.
     * @return The Decision generated for the item, or null if no decision could be made.
     * @throws GeneralException
     */
    private Decision getDecisionFromRecommendation(CertificationItem item) throws GeneralException {
        Decision result = null;
        Recommendation rec = item.getRecommendation();
        if (rec != null) {
            if (this.autoApprove && rec.getRecommendedDecision() == Recommendation.RecommendedDecision.YES) {
                Map<String, Object> decisionMap = new HashMap<>();
                decisionMap.put("certificationItemId", item.getId());
                decisionMap.put("status", CertificationAction.Status.Approved.toString());
                decisionMap.put("autoDecision", true);
                decisionMap.put("comments", this.autoApproveComment);

                result = new Decision(decisionMap, listFilterService, certification.getType());
            }
        }
        return result;
    }
}
