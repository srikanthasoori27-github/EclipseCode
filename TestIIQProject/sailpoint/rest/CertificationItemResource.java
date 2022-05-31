package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.Localizer;
import sailpoint.api.certification.RemediationAdvisor;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.integration.Util;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.service.certification.CertificationItemService;
import sailpoint.service.certification.ChildRole;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.DecisionSummary;
import sailpoint.web.view.DecisionSummaryFactory;
import sailpoint.web.view.IdentitySummary;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * @author jonathan.bryant@sailpoint.com
 */
@Path("certItem")
public class CertificationItemResource extends BaseResource {

    private static final Log log = LogFactory.getLog(CertificationItemResource.class);

    @QueryParam("workItemId") protected String workItemId;

    private String itemId;

    private Localizer localizer;
    
    public CertificationItemResource() {
    }

    public CertificationItemResource(String itemId) {
        this.itemId = itemId;
    }

    /**
     * Constructor for this sub-resource.
     *
     * @param itemId The id of the certificationitem this sub-resource is servicing.
     * @param parent The parent of this sub-resource.
     */
    public CertificationItemResource(String itemId, String workItemId, BaseResource parent) {
        super(parent);
        this.workItemId = workItemId;
        this.itemId = itemId;
    }

    @GET
    public RequestResult getSummary() throws GeneralException {
    	authCertItem(itemId);

        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(getContext(), getLoggedInUser(),
            getLocale(), getUserTimeZone());
        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        DecisionSummary summary = summaryFactory.getSummary(item);

        return new ObjectResult(summary);
    }
    
    @GET
    @Path("{action}")
    public RequestResult calculateSummary(@PathParam("action") String action) throws GeneralException {
    	authCertItem(itemId);
    	
        CertificationAction.Status status = CertificationAction.Status.valueOf(action);

        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(getContext(), getLoggedInUser(),
        getLocale(), getUserTimeZone());
        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        DecisionSummary summary = summaryFactory.calculateSummary(item, status, null);

        return new ObjectResult(summary);
    }

    @GET()
    @Path("missingRolesAdvice")
    public RequestResult getMissingRolesRemediationAdvice() throws GeneralException {
    	authCertItem(itemId);

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(getContext(), getLoggedInUser(),
                        getLocale(), getUserTimeZone());
        DecisionSummary summary = summaryFactory.calculateSummary(item, CertificationAction.Status.Approved, null);
        return new ObjectResult(summary);
    }


    @GET
    @Path("remediationAdvice")
    public RequestResult getViolationRemediationAdvice() throws GeneralException {
    	authCertItem(itemId);

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        CertificationItemService service = new CertificationItemService(this);
        return new ObjectResult(service.getViolationRemediationAdvice(item));
    }

    @GET
    @Path("revokableRoles")
    public RequestResult getRevokableRoles() throws GeneralException {
    	authCertItem(itemId);

        List<ChildRole> childRoles = new ArrayList<ChildRole>();

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);

        String permittedTypeMsg = Internationalizer.getMessage(
                            MessageKeys.CERT_DECISION_REVOKE_ROLE_TYPE_PERMITTED, getLocale());

        String requiredTypeMsg = Internationalizer.getMessage(
                MessageKeys.CERT_DECISION_REVOKE_ROLE_TYPE_REQUIRED, getLocale());

        if (CertificationItem.SubType.AssignedRole.equals(item.getSubType())) {

            // handle roles where the revoke has already occurred. In this case
            // we can get the list of selected roles and from the action
            if (item.getAction() != null && (item.isChallengeActive() || item.getAction().isRemediationKickedOff())){
                if (item.getAction().getAdditionalActions() != null){
                    ProvisioningPlan.AccountRequest request =
                        item.getAction().getAdditionalActions().getIIQAccountRequest();
                    if (request != null){
                        Bundle parent = item.getBundle(getContext());
                        for(ProvisioningPlan.AttributeRequest attrRequest : request.getAttributeRequests()){
                            String roleName = (String)attrRequest.getValue();
                            Bundle b = getContext().getObjectByName(Bundle.class, roleName);
                            if (b != null){
                                String relationshipType = parent != null && parent.permits(b) ?
                                        permittedTypeMsg :requiredTypeMsg;
                                childRoles.add(new ChildRole(b, relationshipType, true));
                            }
                        }
                    }
                }
            } else {
                // Get the list of permitted and required roles for this assigned role
                // that are revokable.
                RemediationAdvisor advisor = new RemediationAdvisor(getContext());
                RemediationAdvisor.PermittedRoles roles = advisor.getRevokablePermittedRoles(item);

                // Determine if there's an existing set of roles to be remediated
                List<String> selectedRoles = new ArrayList<String>();
                boolean editingExistingRemediation = false;
                if (item.getAction() != null && CertificationAction.Status.Remediated.equals(item.getAction().getStatus())) {
                    editingExistingRemediation = true;
                    selectedRoles = item.getAction().getAdditionalRemediations();
                }

                if (roles != null && !roles.isEmpty()) {
                    String assignedRoleId = item.getTargetId();
                    if (roles.getPermittedRoles() != null){
                        for (Bundle b : roles.getPermittedRoles()) {
                            boolean isSelected = editingExistingRemediation &&
                                    selectedRoles != null && selectedRoles.contains(b.getName());
                            if (!assignedRoleId.equals(b.getId())) {
                                childRoles.add(new ChildRole(b, permittedTypeMsg, isSelected));
                            }
                        }
                    }
                    if (roles.getRequiredRoles() != null){
                        for (Bundle b : roles.getRequiredRoles()) {
                            boolean isSelected = editingExistingRemediation &&
                                    selectedRoles != null && selectedRoles.contains(b.getName());
                            if (!assignedRoleId.equals(b.getId())) {
                                childRoles.add(new ChildRole(b, requiredTypeMsg, isSelected));
                            }
                        }
                    }
                }
            }
            return new ListResult(childRoles, childRoles.size());
        }
        
        return null;
    }


    @POST
    @Path("summary")
    public RequestResult getRemediationSummary(@PathParam("itemId") String id,
                                               @FormParam("details") String remediationJson) throws GeneralException {
    	
    	authCertItem(id);

        CertificationItem item = getContext().getObjectById(CertificationItem.class, id);
        List<Bundle> additionalRevokedBundles = null;
        CertificationItemService service = new CertificationItemService(this);

        if (remediationJson != null && !remediationJson.equals("")) {
            if (CertificationItem.Type.PolicyViolation.equals(item.getType())) {
                List<String> revokedRoles = null;
                List<PolicyTreeNode> revokedEntitlements = null;
                if (item.getPolicyViolation().getRightBundles() != null) {
                    // Role SoD, assume details is list of role names
                    revokedRoles = JsonHelper.listFromJson(String.class, remediationJson);
                } else if (item.getPolicyViolation().getEntitlementsToRemediate() != null) {
                    // Entitlement SoD, assume details is list of PolicyTreeNodes
                    revokedEntitlements = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson(remediationJson);
                }
                //update the cert item with the policy violation details
                item = service.updateViolationDetails(item, revokedRoles, revokedEntitlements);
            } else if (CertificationItem.SubType.AssignedRole.equals(item.getSubType())) {
                List<String> rolesToRemediate = JsonHelper.listFromJson(String.class, remediationJson);
                if (rolesToRemediate != null && !rolesToRemediate.isEmpty()){
                    additionalRevokedBundles = new ArrayList<Bundle>();
                    for(String role : rolesToRemediate){
                        Bundle b = getContext().getObjectByName(Bundle.class, role);
                        if (b!=null) {
                            b.setAssignmentId(item.getBundleAssignmentId());
                            additionalRevokedBundles.add(b);
                        }
                    }
                }
            }
        }
        return new ObjectResult(service.getDecisionSummary(item, CertificationAction.Status.Remediated, additionalRevokedBundles));
    }

    @GET()
    @Path("challengeDetails")
    public RequestResult getChallengeDetails() throws GeneralException {
    	
    	authCertItem(itemId);

        ChallengeSummary challengeSummary = new ChallengeSummary();

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        CertificationChallenge challenge = item.getChallenge();
        if (challenge != null){
            Identity owner = null;
            IdentitySummary ownerSummary = null;
            if (challenge.getOwnerName() != null){
                owner = getContext().getObjectByName(Identity.class, challenge.getOwnerName());
            }

            if (owner != null){
                ownerSummary = new IdentitySummary(owner);
            } else {
                ownerSummary = new IdentitySummary();
                ownerSummary.setName(challenge.getOwnerName());
                ownerSummary.setDisplayName(challenge.getOwnerName());
            }

            challengeSummary.setOwner(ownerSummary);
            challengeSummary.setId(challenge.getId());
            challengeSummary.setCompletionComments(challenge.getCompletionComments());
            challengeSummary.setDecisionComments(challenge.getDecisionComments());
        }

        return new ObjectResult(challengeSummary);
    }

    @POST
    @Path("itemClassification")
    public RequestResult setItemClassificationData(@PathParam("itemId") String id,
                                               @FormParam("data") String data) throws GeneralException {
    	
    	authCertItem(id);

        CertificationItem item = getContext().getObjectById(CertificationItem.class, id);

        Map<String, Object> customData = JsonHelper.mapFromJson(String.class, Object.class, data);

        if (customData.containsKey("custom1")){
            Object val = customData.get("custom1");
            item.setCustom1((String)val);
        }

        if (customData.containsKey("custom2")){
            Object val = customData.get("custom2");
            item.setCustom2((String) val);
        }

        getContext().saveObject(item);
        getContext().commitTransaction();

        Certification cert = item.getParent().getCertification();
        if(cert!=null) {
            Certificationer certificationer = new Certificationer(getContext());
            certificationer.refresh(cert);
        }

        return new RequestResult(RequestResult.STATUS_SUCCESS, null,
                 null, null);
    }

    /**
     * Add an entitlement comment to the identity's history. Posts with
     * empty comment text are ignored.
     *
     * @return Returns json success or error message.
     */
    @POST
    @Path("{id}/entitlementComment")
    public RequestResult postEntitlementComment(@PathParam("id") String id, @FormParam("comments") String commentText) throws GeneralException{

        authCertItem(id);
        this.itemId = id;

        CertificationItemService service = new CertificationItemService(this);
        return service.addEntitlementComment(getLoggedInUser(), this.itemId, commentText);
    }
    
    @GET
    @Path("appSummary/{application}")
    public ObjectResult getAppSummary(@PathParam("application") String app) throws GeneralException {
        
        authCertItem(itemId);
        setPreAuth(true);
        ApplicationResource ar = new ApplicationResource(app, this);
        return ar.getSummary();
        
    }

    // ------------------------------------------------------------------
    //
    //  Private Methods
    //
    // ------------------------------------------------------------------

    private void authCertItem(String itemId) throws GeneralException {

        QueryOptions ops = new QueryOptions(Filter.eq("id", itemId));
        Iterator<Object[]> iter =
                getContext().search(CertificationItem.class, ops, Arrays.asList("parent.certification"));

        if (iter.hasNext()){
            Certification cert = (Certification)iter.next()[0];

            // Try to get the work item id
            if (Util.isNullOrEmpty(workItemId)) {
                workItemId = WorkItemUtil.getWorkItemId(getContext(), itemId);
            }

            authorize(new CertificationAuthorizer(cert, workItemId));
        } else {
        	throw new UnauthorizedAccessException(new Message(MessageKeys.UI_CERT_ITEM_PARENT_UNAUTHORIZED_ACCESS));
        }
    }

    public static class ChallengeSummary{
        private String id;
        private IdentitySummary owner;
        private String completionComments;
        private String decisionComments;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public IdentitySummary getOwner() {
            return owner;
        }

        public void setOwner(IdentitySummary owner) {
            this.owner = owner;
        }

        public String getCompletionComments() {
            return completionComments;
        }

        public void setCompletionComments(String completionComments) {
            this.completionComments = completionComments;
        }

        public String getDecisionComments() {
            return decisionComments;
        }

        public void setDecisionComments(String decisionComments) {
            this.decisionComments = decisionComments;
        }
    }

}
