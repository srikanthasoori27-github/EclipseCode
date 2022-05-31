/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * DTO representing a single entitlement on an identity, including account and attribute/permission data.
 */
public class IdentityEntitlementDTO extends BaseEntitlementDTO {

    /**
     * Name of the instance
     */
    private String instance;

    /**
     * Native identifier for the account
     */
    private String nativeIdentity;

    /**
     * Displayable name of the account
     */
    private String accountName;

    /**
     * The state of the aggregation for this entitlement
     */
    private IdentityEntitlement.AggregationState aggregationState;

    /**
     * Directly assigned entitlments will have this flag set to true.
     * This applies only to entitlements.
     */
    private boolean assigned;


    /**
     * Valid for entitlements only.  Flag will be set to true if one of user's current detectable roles grants this
     * entitlement.
     */
    private boolean grantedByRole;


    /**
     * List of assignable role names that could grant an entitlement.
     * Property is displayed as csv in the ui popup.
     */
    List<String> sourceAssignableRoleNames;

    /**
     * List of detectable role names that could have granted an entitlement.
     *
     */
    List<String> sourceDetectableRoleNames;

    /**
     * Date that the entitlement was activated
     */
    private Date startDate;

    /**
     * Date that the entitlement was deactivated
     */
    private Date endDate;

    /**
     * Date entitlement was last certified.
     */
    private Date lastCertDate;
    
    /**
     * String certification name of which entitlement was last certified.
     */
    private String lastCertName;
    
    /**
     * The last certification that this entitlement was a part of
     */
    private IdentityEntitlementCertificationItemDTO lastCertificationItem;


    /**
     * The current certification that this entitlement is a part of
     */
    private IdentityEntitlementCertificationItemDTO pendingCertificationItem;


    /**
     * The last identity request item for this entitlement
     */
    private IdentityEntitlementIdentityRequestItemDTO lastRequestItem;


    /**
     * The current pending request item for this entitlement
     */
    private IdentityEntitlementIdentityRequestItemDTO pendingRequestItem;


    /**
     * The identity that assigned this entitlement
     */
    private String assigner;

    /**
     * The source of the entitlement
     */
    private String source;

    /**
     * Whether the entitlement was found on the account
     */
    private boolean foundOnAccount;

    /**
     * Whether the entitlement is a detected role or not
     */
    private boolean detectedRole;

    /**
     * True if has pending request
     */
    private boolean hasPendingRequest;

    /**
     * Pending request id
     */
    private String pendingRequestId;

    /**
     * If a detected role is allowed by an assigned role.
     */
    private boolean allowed;

    public IdentityEntitlementDTO() {
    }

    /**
     * Copy constructor.
     *
     * @param dto Original IdentityEntitlementDTO to copy.
     */
    public IdentityEntitlementDTO(IdentityEntitlementDTO dto) {
        super(dto);

        this.instance = dto.instance;
        this.nativeIdentity = dto.nativeIdentity;
        this.accountName = dto.accountName;
        this.aggregationState = dto.aggregationState;
        this.startDate = dto.getStartDate();
        this.endDate = dto.getEndDate();
        this.lastCertDate = dto.getLastCertDate();
        this.lastCertName = dto.getLastCertName();
        this.assigned = dto.isAssigned();
        this.grantedByRole = dto.isGrantedByRole();
        this.source = dto.getSource();
        this.sourceAssignableRoleNames = dto.getSourceAssignableRoleNames();
        this.sourceDetectableRoleNames = dto.getSourceDetectableRoleNames();
        this.assigner = dto.getAssigner();
        this.lastCertificationItem = dto.getLastCertificationItem();
        this.pendingCertificationItem = dto.getPendingCertificationItem();
        this.pendingRequestId = dto.getPendingRequestId();
        this.hasPendingRequest = dto.isHasPendingRequest();
        this.allowed = dto.isAllowed();
    }

    /**
     * Construct an IdentityEntitlementDTO object from an IdentityEntitlement
     * @param identityEntitlement The IdentityEntitlement object
     * @param userContext A user context to get locale/timezone from
     * @param context A sailpoint context to fetch other objects with
     * @throws GeneralException
     */
    public IdentityEntitlementDTO(IdentityEntitlement identityEntitlement, UserContext userContext, SailPointContext context) throws GeneralException {
        super(identityEntitlement, userContext, context);
        this.instance = identityEntitlement.getInstance();
        this.nativeIdentity = identityEntitlement.getNativeIdentity();
        this.accountName = identityEntitlement.getDisplayName();

        if(identityEntitlement.getAggregationState()!=null) {
            this.aggregationState = identityEntitlement.getAggregationState();
        }
        this.startDate = identityEntitlement.getStartDate();
        this.endDate = identityEntitlement.getEndDate();
        this.assigned = identityEntitlement.isAssigned();
        this.grantedByRole = identityEntitlement.isGrantedByRole();
        this.source = identityEntitlement.getSource();
        this.foundOnAccount = Util.nullSafeEq(getAggregationState(), IdentityEntitlement.AggregationState.Connected);
        this.detectedRole = (Util.nullSafeCompareTo(getName(), ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) == 0) ? true : false;

        sourceDetectableRoleNames = Util.csvToList(identityEntitlement.getSourceDetectedRoles());
        sourceAssignableRoleNames = Util.csvToList(identityEntitlement.getSourceAssignableRoles());

        if(identityEntitlement.getAssigner()!=null) {
            this.assigner = identityEntitlement.getAssigner();
            Identity assigner = context.getObjectByName(Identity.class, identityEntitlement.getAssigner());
            if(assigner != null) {
                this.assigner = assigner.getDisplayableName();
            }
        }

        CertificationItem lastItem = identityEntitlement.getCertificationItem();
        if(lastItem!=null) {
            this.lastCertificationItem = new IdentityEntitlementCertificationItemDTO(lastItem, context, userContext);
            this.lastCertDate = lastItem.getModified();
            this.lastCertName = lastItem.getName();
        }

        CertificationItem pendingItem = identityEntitlement.getPendingCertificationItem();
        if(pendingItem!=null) {
            this.lastCertificationItem = new IdentityEntitlementCertificationItemDTO(pendingItem, context, userContext);
        }

        IdentityRequestItem lastRequestItem = identityEntitlement.getRequestItem();
        if(lastRequestItem !=null) {
             this.lastRequestItem = new IdentityEntitlementIdentityRequestItemDTO(lastRequestItem);
        }

        IdentityRequestItem pendingRequestItem = identityEntitlement.getPendingRequestItem();
        if(pendingRequestItem !=null) {
            this.pendingRequestItem = new IdentityEntitlementIdentityRequestItemDTO(pendingRequestItem);
            this.pendingRequestId = pendingRequestItem.getId();
            this.hasPendingRequest = true;
        }

        this.allowed = identityEntitlement.isAllowed();
    }

    /**
     * Constructor, call parent's constructor
     *
     * @param entitlementMap an entitlement object stored in map, object properties are used for key
     * @param cols    list of UI ColumnConfigs of the projection columns
     */
    public IdentityEntitlementDTO(Map<String, Object> entitlementMap, List<ColumnConfig> cols) {
        super(entitlementMap, cols);
        if(entitlementMap.containsKey("hasPendingRequest")) {
            this.setHasPendingRequest(Boolean.parseBoolean((String)entitlementMap.get("hasPendingRequest")));
        }
    }

    /**
     * @return Whether the entitlement is a detected role or not
     */
    public boolean isDetectedRole() {
        return this.detectedRole;
    }

    /**
     * @return Whether the entitlement was found on an account
     */
    public boolean isFoundOnAccount() {
        return this.foundOnAccount;
    }

    /**
     * @return Name of the instance
     */
    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    /**
     * @return Native identifier for the account
     */
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    /**
     * @return Displayable name of the account
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * @return agregation state.
     */
    public IdentityEntitlement.AggregationState getAggregationState() {
        return aggregationState;
    }

    public void setAggregationState(IdentityEntitlement.AggregationState aggregationState) {
        this.aggregationState = aggregationState;
    }

    /**
     * @return Date that the entitlement was activated
     */
    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return Date that the entitlement was deactivated
     */
    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return Date that the entitlement was last certified
     */
    public Date getLastCertDate() {
        return lastCertDate;
    }

    public void setLastCertDate(Date lastCertDate) {
        this.lastCertDate = lastCertDate;
    }

    /**
     * @return String name of the certification that entitlement was last certified
     */
    public String getLastCertName() {
        return lastCertName;
    }

    public void setLastCertName(String lastCertName) {
        this.lastCertName = lastCertName;
    }

    /**
     * @return Whether the entitlement is assigned
     */
    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    /**
     * @return Whether the entitlement is granted by a role
     */
    public boolean isGrantedByRole() {
        return grantedByRole;
    }

    public void setGrantedByRole(boolean grantedByRole) {
        this.grantedByRole = grantedByRole;
    }

    /**
     * @return The list of assignable roles that could grant the entitlement
     */
    public List<String> getSourceAssignableRoleNames() {
        return sourceAssignableRoleNames;
    }

    public void setSourceAssignableRoleNames(List<String> sourceAssignableRoleNames) {
        this.sourceAssignableRoleNames = sourceAssignableRoleNames;
    }

    /**
     * @return The list of detectable roles that could grant the entitlement
     */
    public List<String> getSourceDetectableRoleNames() {
        return sourceDetectableRoleNames;
    }

    public void setSourceDetectableRoleNames(List<String> sourceDetectableRoleNames) {
        this.sourceDetectableRoleNames = sourceDetectableRoleNames;
    }

    /**
     * @return The last certification item associated with this entitlement
     */
    public IdentityEntitlementCertificationItemDTO getLastCertificationItem() {
        return lastCertificationItem;
    }

    public void setLastCertificationItem(IdentityEntitlementCertificationItemDTO lastCertificationItem) {
        this.lastCertificationItem = lastCertificationItem;
    }

    /**
     * @return The certification item associated with this entitlement
     */
    public IdentityEntitlementCertificationItemDTO getPendingCertificationItem() {
        return pendingCertificationItem;
    }

    public void setPendingCertificationItem(IdentityEntitlementCertificationItemDTO pendingCertificationItem) {
        this.pendingCertificationItem = pendingCertificationItem;
    }

    /**
     * @return The identity that assigned this entitlement
     */
    public String getAssigner() {
        return assigner;
    }

    public void setAssigner(String assigner) {
        this.assigner = assigner;
    }

    /**
     * @return The source of this entitlement
     */
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return The most recent identity request item associated with this entitlement
     */
    public IdentityEntitlementIdentityRequestItemDTO getLastRequestItem() {
        return lastRequestItem;
    }

    public void setLastRequestItem(IdentityEntitlementIdentityRequestItemDTO lastRequestItem) {
        this.lastRequestItem = lastRequestItem;
    }


    /**
     * @return The pending identity request item associated with this entitlement
     */
    public IdentityEntitlementIdentityRequestItemDTO getPendingRequestItem() {
        return pendingRequestItem;
    }

    public void setPendingRequestItem(IdentityEntitlementIdentityRequestItemDTO pendingRequestItemDTO) {
        this.pendingRequestItem = pendingRequestItemDTO;
    }

    /**
     * Returns true if has a pending request item
     * @return true if has a pending request item
     */
    public boolean isHasPendingRequest() {
        return hasPendingRequest;
    }

    /**
     * Set to true if has a pending request item
     * @param hasPendingRequest Whether has pending request or not
     */
    public void setHasPendingRequest(boolean hasPendingRequest) {
        this.hasPendingRequest = hasPendingRequest;
    }

    /**
     * Returns pending request id
     * @return pending request id
     */
    public String getPendingRequestId() {
        return pendingRequestId;
    }

    /**
     * Sets the pending request id
     * @param pendingRequestId The new pending request id
     */
    public void setPendingRequestId(String pendingRequestId) {
        this.pendingRequestId = pendingRequestId;
    }

    /**
     * True if the detected role is allowed by an assigned role
     * @return true if the detected role is allowed by an assigned role
     */
    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
}
