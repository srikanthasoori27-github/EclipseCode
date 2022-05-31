package sailpoint.web.view;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.certification.ProvisioningPlanEditDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DecisionSummary {

    public enum ItemType {
        CertificationEntity,
        CertificationItem
    }

    private String id;
    private ItemType itemType;
    private CertificationAction.Status status;
    private IdentitySummary owner;
    private IdentitySummary defaultRemediator;
    private boolean enableOverrideDefaultRemediator;
    private String description;
    private String comments;
    private String violationConstraint;
    private String violationSummary;
    private String remediationAdvice;
    private List<AssignmentOption> assignmentOptions;
    private Long mitigationExpiration;
    private List<String> provisioners;
    private List<LineItem> remediationDetails;
    private boolean useManagedAttributesForRemediation;
    private CertificationAction.RemediationAction remediationAction;
    private DelegationSummary delegation;
    private DelegationSummary entityDelegation;
    private List<String> additionalRoles;

    /**
     * List of display names for required or permitted roles, for an assigned role being revoked. 
     */
    private List<String> requiredOrPermittedRoles;

    public DecisionSummary(){

    }

    public DecisionSummary(SailPointContext ctx, Locale locale, TimeZone tz, CertificationItem item)
            throws GeneralException{

        this.id = item.getId();
        this.itemType = DecisionSummary.ItemType.CertificationItem;
        setUseManagedAttributesForRemediation(ctx.getConfiguration().getAttributes().getBoolean(
                Configuration.USE_MANAGED_ATTRIBUTES_FOR_REMEDIATION));

        if (item.getPolicyViolation() != null){
            ViolationDetailer violationDetailer = new ViolationDetailer(ctx, item.getPolicyViolation(), locale, tz);
            violationConstraint = violationDetailer.getConstraint();
            violationSummary = violationDetailer.getSummary();
        }

        if (item.getAction() != null){

            this.status = item.getAction().getStatus();
            this.description = item.getAction().getDescription();
            this.comments = item.getAction().getComments();

            // Both these can be set in an approval if the approver decided
            // to provision missing required roles.
            remediationAction = item.getAction().getRemediationAction();
            additionalRoles = item.getAction().getAdditionalRemediations();

            switch (item.getAction().getStatus()){
                case Mitigated:
                    this.mitigationExpiration = item.getAction().getMitigationExpiration().getTime();
                    break;
                case Remediated:
                    if (item.getAction().getOwnerName() != null) {
                        Identity owner = ObjectUtil.getIdentityOrWorkgroup(ctx, item.getAction().getOwnerName());
                        if (owner != null)
                            setOwner(new IdentitySummary(owner));
                    }
                    break;
            }
        }

        if (item.getDelegation() != null){
            Identity delegationOwner = ObjectUtil.getIdentityOrWorkgroup(ctx, item.getDelegation().getOwnerName());
            this.delegation = new DelegationSummary(item.getDelegation(), delegationOwner, false);
        }

        if (item.getParent().getDelegation() != null){
            Identity delegationOwner = ObjectUtil.getIdentityOrWorkgroup(ctx,
                    item.getParent().getDelegation().getOwnerName());
            this.entityDelegation = new DelegationSummary(item.getParent().getDelegation(), delegationOwner, true);
        }
    }

    public DecisionSummary(CertificationEntity entity){

        this.id = entity.getId();
        this.itemType = DecisionSummary.ItemType.CertificationEntity;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CertificationAction.Status getStatus() {
        return status;
    }

    public void setStatus(CertificationAction.Status status) {
        this.status = status;
    }

    public IdentitySummary getOwner() {
        return owner;
    }

    public void setOwner(IdentitySummary owner) {
        this.owner = owner;
    }

    public IdentitySummary getDefaultRemediator() {
        return defaultRemediator;
    }

    public void setDefaultRemediator(IdentitySummary defaultRemediator) {
        this.defaultRemediator = defaultRemediator;
    }
    
    public boolean isEnableOverrideDefaultRemediator() {
        return enableOverrideDefaultRemediator;
    }
    
    public void setEnableOverrideDefaultRemediator(boolean enableOverride) {
        this.enableOverrideDefaultRemediator = enableOverride;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public DelegationSummary getDelegation() {
        return delegation;
    }

    public void setDelegation(DelegationSummary delegation) {
        this.delegation = delegation;
    }

    public DelegationSummary getEntityDelegation() {
        return entityDelegation;
    }

    public void setEntityDelegation(DelegationSummary entityDelegation) {
        this.entityDelegation = entityDelegation;
    }

    public Long getMitigationExpiration() {
        return mitigationExpiration;
    }

    public void setMitigationExpiration(Long mitigationExpiration) {
        this.mitigationExpiration = mitigationExpiration;
    }

    public String getViolationConstraint() {
        return violationConstraint;
    }

    public void setViolationConstraint(String violationConstraint) {
        this.violationConstraint = violationConstraint;
    }

    public String getViolationSummary() {
        return violationSummary;
    }

    public void setViolationSummary(String violationSummary) {
        this.violationSummary = violationSummary;
    }

    public String getRemediationAdvice() {
        return remediationAdvice;
    }

    public void setRemediationAdvice(String remediationAdvice) {
        this.remediationAdvice = remediationAdvice;
    }

    public List<AssignmentOption> getAssignmentOptions() {
        return assignmentOptions;
    }

    public void setAssignmentOptions(List<AssignmentOption> assignmentOptions) {
        this.assignmentOptions = assignmentOptions;
    }

    public void addAssignmentOptions(IdentitySummary identity, String description){
        if (assignmentOptions==null)
            assignmentOptions = new ArrayList<AssignmentOption>();
        assignmentOptions.add(new AssignmentOption(identity, description));
    }

    public List<String> getProvisioners() {
        return provisioners;
    }

    public void setProvisioners(List<String> provisioners) {
        this.provisioners = provisioners;
    }

    public List<LineItem> getRemediationDetails() {
        return remediationDetails;
    }

    public void setRemediationDetails(List<LineItem>  remediationDetails) {
        this.remediationDetails = remediationDetails;
    }

    public void addRemediationDetail(SailPointContext context, String identityName,
                                     ProvisioningPlanEditDTO.LineItem item, Locale locale) throws GeneralException{
        if (remediationDetails == null)
            remediationDetails = new ArrayList<LineItem>();

        remediationDetails.add(new LineItem(context, identityName, item, locale));
    }

    public CertificationAction.RemediationAction getRemediationAction() {
        return remediationAction;
    }

    public void setRemediationAction(CertificationAction.RemediationAction remediationAction) {
        this.remediationAction = remediationAction;
    }

    public boolean isUseManagedAttributesForRemediation() {
        return useManagedAttributesForRemediation;
    }

    public void setUseManagedAttributesForRemediation(boolean useManagedAttributesForRemediation) {
        this.useManagedAttributesForRemediation = useManagedAttributesForRemediation;
    }

    public List<String> getAdditionalRoles() {
        return additionalRoles;
    }

    public void setAdditionalRoles(List<String> additionalRoles) {
        this.additionalRoles = additionalRoles;
    }

    private List addRole(List targetList, String id, String name, String desc, boolean selected){
        Map map = new HashMap();
        map.put("id", id);
        map.put("name", name);
        if (description != null)
            map.put("description", desc);
        map.put("selected", selected);

        if (targetList == null)
            targetList = new ArrayList();
        targetList.add(map);

        return targetList;
    }

    public List<String> getRequiredOrPermittedRoles() {
        return requiredOrPermittedRoles;
    }

    public void setRequiredOrPermittedRoles(List<String> requiredOrPermittedRoles) {
        this.requiredOrPermittedRoles = requiredOrPermittedRoles;
    }

    public static class DelegationSummary{

        private String id;
        private IdentitySummary owner;
        private boolean entityDelegation;
        private String description;
        private String comments;
        private String completionComments;

        public DelegationSummary(CertificationDelegation delegation, Identity delegationOwner, boolean isEntityDelegation){
            this.entityDelegation = isEntityDelegation;
            this.id = delegation.getId();
            this.comments = delegation.getComments();
            this.completionComments =  delegation.getCompletionComments();
            this.description = delegation.getDescription();

            if (delegationOwner != null)
                this.owner = new IdentitySummary(delegationOwner);

        }

        public String getId() {
            return id;
        }

        public IdentitySummary getOwner() {
            return owner;
        }

        public boolean isEntityDelegation() {
            return entityDelegation;
        }

        public String getDescription() {
            return description;
        }

        public String getComments() {
            return comments;
        }

        public String getCompletionComments() {
            return completionComments;
        }

        public void setCompletionComments(String completionComments) {
            this.completionComments = completionComments;
        }
    }


    public static class AssignmentOption{

        IdentitySummary identity;
        String description;

        public AssignmentOption(IdentitySummary identity, String description) {
            this.identity = identity;
            this.description = description;
        }

        public IdentitySummary getIdentity() {
            return identity;
        }

        public String getDescription() {
            return description;
        }
    }
}
