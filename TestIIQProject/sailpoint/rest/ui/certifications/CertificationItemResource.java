package sailpoint.rest.ui.certifications;/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import sailpoint.api.IdentityHistoryService;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.authorization.CertifiableItemAuthorizer;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Identity;
import sailpoint.object.LinkInterface;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.identities.ApplicationAccountDetailResource;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.IdentityAttributesDTO;
import sailpoint.service.IdentityDetailsService;
import sailpoint.service.certification.CertificationItemService;
import sailpoint.service.certification.RemediationAdviceResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.view.DecisionSummary;

/**
 * @author: michael.hide
 * Created: 1/20/2016 14:04
 */
public class CertificationItemResource extends BaseListResource implements BaseListServiceContext {

    private String itemId;
    private Certification certification;

    private static final String COMMENTS_KEY = "comments";
    private static final String REVOKED_ROLES = "revokedRoles";
    private static final String REVOKED_VIOLATION_ENTITLEMENTS = "selectedViolationEntitlements";

    private BaseListResourceColumnSelector identityHistoryTableByItemColumnSelector =
            new BaseListResourceColumnSelector(UIConfig.UI_IDENTITY_HISTORY_BY_ITEM_TABLE_COLUMNS);

    /**
     * Constructor for this sub-resource.
     *
     * @param parent The parent of this sub-resource.
     * @param itemId The id of the CertificationItem this sub-resource is servicing.
     */
    public CertificationItemResource(BaseResource parent, Certification certification, String itemId) throws GeneralException {
        super(parent);
        if (certification == null) {
            throw new GeneralException("certification is required");
        }
        this.certification = certification;
        this.itemId = itemId;
    }

    /**
     * Get the identity history result data from the identity history list service and add in the temp decision if there
     * is no history data found.
     *
     * @return ListResult list of identity decision history
     * @throws GeneralException
     */
    @GET
    @Path("history")
    public ListResult getIdentityHistory() throws GeneralException {
        CertificationItem item = getCertificationItem();
        Identity identity = item.getIdentity(this.getContext());

        if (identity == null) {
            throw new ObjectNotFoundException(Identity.class, item.getIdentity());
        }

        IdentityHistoryService identityHistoryService = new IdentityHistoryService(this, identityHistoryTableByItemColumnSelector);
        ListResult identityHistoryResult = identityHistoryService.getIdentityHistory(identity.getId(), item);

        Map pendingDecision = identityHistoryService.getPendingDecision(identity.getId(), item);
        if (pendingDecision != null) {
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("pendingDecision", pendingDecision);
            identityHistoryResult.setMetaData(metaData);
        }

        return identityHistoryResult;
    }

    /**
     * Add an entitlement comment to the identity's history. Posts with
     * empty comment text are ignored.
     *
     * @return Returns json success or error message.
     */
    @POST
    @Path("comment")
    public RequestResult postEntitlementComment(Map<String, String> contentMap) throws GeneralException {
        CertificationItem item = getCertificationItem();
        Certification certification = (item.getParent() != null) ? item.getParent().getCertification() : null;
        if (certification != null) {
            // Check if the current user is authorized
            authorize(new CertificationAuthorizer(certification));
            // and only allow comments if the cert is editable by the current user.
            if (CertificationUtil.isEditable(this, certification)) {
                CertificationItemService service = new CertificationItemService(this);
                return service.addEntitlementComment(getLoggedInUser(), this.itemId, contentMap.get(COMMENTS_KEY));
            }
        }
        return new RequestResult(RequestResult.STATUS_FAILURE, null, null, null);
    }

    /**
     * Get the remediation advice for this certification item.
     * @return RemediationAdviceResult object
     * @throws GeneralException
     */
    @GET
    @Path("remediationAdvice")
    public RemediationAdviceResult getViolationRemediationAdvice() throws GeneralException {
        CertificationItemService service = new CertificationItemService(this);
        CertificationItem item = getCertificationItem();
        return service.getViolationRemediationAdvice(item);
    }

    /**
     * Get the delegation description for this certification item.
     * @return DecisionSummary object
     * @throws GeneralException
     */
    @GET
    @Path("delegationDescription")
    public DecisionSummary getDelegationDescription () throws GeneralException {
        CertificationItemService service = new CertificationItemService(this);
        CertificationItem item = getCertificationItem();
        return service.getDecisionSummary(item, CertificationAction.Status.Delegated, null);
    }

    /**
     * Gets the remediation summary for this certification item.
     * @param input Map of input with additional information, specifically for remediating violations.
     * @return DecisionSummary object
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    @POST
    @Path("remediationSummary")
    public DecisionSummary getRemediationSummary(Map<String, Object> input) throws GeneralException {

        CertificationItemService service = new CertificationItemService(this);
        CertificationItem item = getCertificationItem();

        List<String> revokedRoles = null;
        if (input.containsKey(REVOKED_ROLES)) {
            revokedRoles = (List<String>)input.get(REVOKED_ROLES);
        }

        List<PolicyTreeNode> selectedViolationEntitlements = null;
        if (input.containsKey(REVOKED_VIOLATION_ENTITLEMENTS)) {
            selectedViolationEntitlements = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson((String)input.get(REVOKED_VIOLATION_ENTITLEMENTS));
        }

        if (!Util.isEmpty(revokedRoles)|| !Util.isEmpty(selectedViolationEntitlements)) {
            item = service.updateViolationDetails(item, revokedRoles, selectedViolationEntitlements); 
        }
        
        return service.getDecisionSummary(item, CertificationAction.Status.Remediated, null);
    }

    /**
     * Gets the role details for the role represented by the certification item. 
     * Passes through to the RoleDetailsResource.
     * @return RoleDetailResource whose main getter will return a RoleDetailDTO
     * @throws GeneralException
     */
    @Path("roleDetails")
    public RoleDetailResource getRoleDetails() throws GeneralException {
        CertificationItem item = getCertificationItem();
        if (!((item.getType().equals(CertificationItem.Type.Bundle) || item.getType().equals(CertificationItem.Type.BusinessRoleHierarchy) ||
            item.getType().equals(CertificationItem.Type.BusinessRoleRequirement) || item.getType().equals(CertificationItem.Type.BusinessRolePermit)))){
            throw new GeneralException("item must be type Bundle or Role Composition certification roles");
        }
        String roleId = item.getTargetId();
        // Is there any case the targetId is not set correctly? I dont think so but give a fallback anyway.
        if (roleId == null && item.getBundle() != null) {
            roleId = ObjectUtil.getId(getContext(), Bundle.class, item.getBundle());
        }

        if (roleId == null) {
            throw new GeneralException("No role ID exists for the item");
        }
        String identityId = null;
        if (item.getIdentity() != null) {
            identityId = ObjectUtil.getId(getContext(), Identity.class, item.getIdentity());
        }

        return new RoleDetailResource(roleId, item.getBundleAssignmentId(), identityId, item, isClassificationsEnabled(item), this);
    }

    private boolean isClassificationsEnabled(CertificationItem item) throws GeneralException {
        Certification certification = item.getCertification();
        if (certification == null) {
            throw new GeneralException("CertificationItem found with no associated certification");
        }

        CertificationDefinition definition = certification.getCertificationDefinition(getContext());
        if (definition == null) {
            throw new GeneralException("Certification found with no associated definition");
        }

        return definition.isIncludeClassifications();
    }

    /**
     * Gets the application account details.
     * @return ApplicationAccountDetailResource
     * @throws GeneralException
     */
    @Path("accountDetails")
    public ApplicationAccountDetailResource getApplicationAccountDetails() throws GeneralException {

        CertificationItem item = getCertificationItem();

        CertificationItemService service = new CertificationItemService(this);
        LinkInterface link = service.getLink(item);

        if (link == null) {
            throw new ObjectNotFoundException(LinkInterface.class, item.getExceptionEntitlements().getNativeIdentity());
        }
        return new ApplicationAccountDetailResource(link, this);
    }

    /**
     * Gets the managed attribute details represented by the certification item. 
     * Passes through to the ManagedAttributeDetailsResource.
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @Path("managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetails() throws GeneralException {
        CertificationItem item = getCertificationItem();
        EntitlementSnapshot entitlement = item.getExceptionEntitlements();
        if (entitlement == null) {
            throw new GeneralException("Unable to get managed attribute details for item " + item.getId());
        }

        String attributeName = null;
        String attributeValue = null;
        boolean isPermission = false;
        if (entitlement.hasAttributes()){
            attributeName = entitlement.getAttributeName();
            attributeValue = entitlement.getAttributeValue().toString();
        } else if (entitlement.hasPermissions()){
            attributeName = entitlement.getPermissionTarget();
            attributeValue = entitlement.getPermissionRight();
            isPermission = true;
        }
        String appId = ObjectUtil.getId(getContext(), Application.class, entitlement.getApplicationName());
        if (Util.isNothing(appId) || Util.isNothing(attributeName) || Util.isNothing(attributeValue)) {
            throw new GeneralException("Unable to get managed attribute details for item " + item.getId());
        }
        
        ManagedAttribute managedAttribute = ManagedAttributer.get(getContext(), appId, isPermission, attributeName, attributeValue, null);
        if (managedAttribute == null) {
            // rshea: Try again leaving the attribute name null. Certain types of managed attributes can have a blank name,
            // in which case the certificationer fills in the attribute name on the cert item with the MA type. Since
            // we can't telling by looking at the cert item if it is one of these cases, we'll just try the
            // name-less search if we didn't find it with the specified name. This is the kludgiest, and this null name
            // trips up code all over the place but this is the only one where we would otherwise throw.
            managedAttribute = ManagedAttributer.get(getContext(), appId, isPermission, null, attributeValue, null);
        }
        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, entitlement.getAttributeName() + " " + entitlement.getAttributeValue());
        }
        
        return new ManagedAttributeDetailResource(managedAttribute, isClassificationsEnabled(item), this);
    }

    /**
     * Returns LinkAttributesDTOs for the identity on the certification item
     * uses start and limit query parameters
     * @return LinkAttributesDTOs for the identity on the certification item
     */
    @GET
    @Path("linkAttributes")
    public ListResult getLinkAttributes() throws GeneralException {
        CertificationItemService service = new CertificationItemService(this);
        return service.getLinksAttributes(getCertificationItem(), this.start, this.limit);
    }

    /**
     * Returns IdentityAttributesDTO for certification item
     * @return IdentityAttributesDTO for certification item
     */
    @GET
    @Path("identityAttributes")
    public IdentityAttributesDTO getIdentityAttributes() throws GeneralException {
        CertificationItem certificationItem = getCertificationItem();
        Certification.Type certType = certificationItem.getCertification().getType();
        if (!certType.isObjectType()) {
            throw new IllegalArgumentException("getIdentityAttributes only applicable to object-based certs");
        }
        Identity identity = certificationItem.getIdentity(getContext());
        if (identity == null) {
            throw new ObjectNotFoundException(Identity.class, certificationItem.getIdentity());
        }
        IdentityDetailsService ids = new IdentityDetailsService(identity);
        return ids.getIdentityAttributesDTO(this.getLocale(), this.getUserTimeZone(), UIConfig.getUIConfig().getIdentityViewAttributesList());
    }

    @Path("roleProfile")
    public CertificationItemProfileResource getRoleProfileDTO() throws GeneralException {
        return new CertificationItemProfileResource(this, this.certification, this.itemId);
    }

    private CertificationItem getCertificationItem() throws GeneralException {
        CertificationItem item = getContext().getObjectById(CertificationItem.class, this.itemId);
        if (item == null) {
            throw new ObjectNotFoundException(CertificationItem.class, this.itemId);
        }

        authorize(new CertifiableItemAuthorizer(this.certification, item));

        return item;
    }
}
