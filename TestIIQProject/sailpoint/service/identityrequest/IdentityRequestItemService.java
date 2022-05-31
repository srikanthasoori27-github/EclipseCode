/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identityrequest;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.service.IdentityResetService;
import sailpoint.service.IdentityResetService.Consts.Flows;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.RegistrationBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service to calculate some details on an IdentityRequestItem
 */
public class IdentityRequestItemService {

    /* Identity Request item names that indicate Role item type */
    private static final List<String> ROLE_TYPE_ITEM_NAMES =
            new ArrayList<String>(Arrays.asList(
                    ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES,
                    ProvisioningPlan.ATT_IIQ_DETECTED_ROLES));

    private static final List<String> REQUEST_ACCESS_TYPES =
            new ArrayList<>(Arrays.asList(
                    IdentityRequest.ROLES_REQUEST_FLOW_CONFIG_NAME,
                    IdentityRequest.ENTITLEMENTS_REQUEST_FLOW_CONFIG_NAME,
                    RequestAccessService.FLOW_CONFIG_NAME
            ));

    private SailPointContext context;
    private IdentityRequestItem identityRequestItem;

    /**
     * Constructor
     * @param context SailPointContext
     * @param identityRequestItem The IdentityRequestItem to use
     */
    public IdentityRequestItemService(SailPointContext context, IdentityRequestItem identityRequestItem) {
        this.context = context;
        this.identityRequestItem = identityRequestItem;
    }

    /**
     * Return true if IdentityRequestItem targets a role
     * @return True if name matches an expected role type name, otherwise false
     */
    public boolean isRole() {
        return (ROLE_TYPE_ITEM_NAMES.contains(this.identityRequestItem.getName())) && isAddOrRemove();
    }

    /**
     * Return true if IdentityRequestItem targets an Entitlement. We can tell this
     * if the application is not IIQ and its not a role.
     * @return True if entitlement, otherwise false
     */
    public boolean isEntitlement() {
        return (!isRole() && !this.identityRequestItem.isIIQ()) && isAddOrRemove() && isRequestAccess();
    }

    /**
     * Return true if this IdentityRequestItem targets an Entitlement that has a managed attribute.
     * @return
     */
    public boolean hasManagedAttribute() {
        return isEntitlement() && this.identityRequestItem.getStringAttribute("id") != null;
    }
    /**
     * Returns true if the operation is either Add or Remove.
     */
    private boolean isAddOrRemove() {
        String operation = this.identityRequestItem.getOperation();
        return (ProvisioningPlan.Operation.Add.name().equals(operation) || 
                ProvisioningPlan.Operation.Remove.name().equals(operation));
    }

    /**
     * Helper method to make sure this item is part of entitlement access request.
     * This will exclude account creation items, IdentityReset, PAM, for example.
     */
    private boolean isRequestAccess() {
        String type = this.identityRequestItem.getIdentityRequest().getType();
        if (type != null) {
            //Not IdentityRest
            for (Flows flow : IdentityResetService.Consts.Flows.values()) {
            	if (flow.value().equals(type)) {
            		return false;
            	}
            }
            //Not PAM or Identity create/update/registration
            if (IdentityRequest.IDENTITY_CREATE_FLOW_CONFIG_NAME.equals(type) ||
            		IdentityRequest.IDENTITY_UPDATE_FLOW_CONFIG_NAME.equals(type) || 
            		RegistrationBean.FLOW_CONFIG_NAME.equals(type) || 
            		Source.PAM.name().equals(type) ) {
            	return false;
            }
        }
        return true;
    }

    /**
     * Get the role that the given IdentityRequestItem targets
     * @return Bundle targeted by this approval item, or null if not a role approval
     * @throws GeneralException
     */
    public Bundle getAccessRole() throws GeneralException {
        if (isRole() && this.identityRequestItem.getValue() != null) {
            Bundle role = context.getObjectByName(Bundle.class, (String)this.identityRequestItem.getValue());

            // If the role is null, try to fall back to the id
            if(role == null) {
                String id = this.identityRequestItem.getStringAttribute("id");
                if(id != null) {
                    role = context.getObjectById(Bundle.class, id);
                }
            }

            return role;
        }
        return null;
    }

    /**
     * Get the display value for the identityRequest operation
     * @return The display value
     */
    public String getDisplayableValue() {
        String value = "";
        if (this.identityRequestItem != null) {
            if (ObjectUtil.isSecret(this.identityRequestItem)) {
                value = "****";
            } else if (null != this.identityRequestItem.getAttribute("displayableValue")) {
                value = this.identityRequestItem.getAttribute("displayableValue").toString();
            } else if (null != this.identityRequestItem.getAttribute("displayableName")) {
                value = this.identityRequestItem.getAttribute("displayableName").toString();
            } else {
                try{
                    ManagedAttribute attr = getManagedAttribute();
                    if (null != attr){
                        value = attr.getDisplayableName();
                    }
                } catch (GeneralException e){

                }
            }
            if (Util.isNullOrEmpty(value)){
                value = this.identityRequestItem.getStringValue();
            }
        }
        return value;
    }

    /**
     * Get the managed attribute for the given identityRequestItem
     * @return The managedAttribute if exists, otherwise null.
     *
     * @throws GeneralException
     */
    private ManagedAttribute getManagedAttribute() throws GeneralException {
        if (null == this.identityRequestItem){
            return null;
        }
        return ManagedAttributer.get(
                context,
                context.getObjectByName(Application.class, this.identityRequestItem.getApplication()),
                this.identityRequestItem.getName(),
                this.identityRequestItem.getStringValue());
    }

    /**
     * Attempts to find an assignment ID in the provisioning plan of a role identity request item
     * @return String assignment ID if found in the IIQ plan on the provisioning plan
     * @throws GeneralException
     */
    public String getAssignmentId() throws GeneralException {
        if (isRole()) {
            ProvisioningPlan plan = this.identityRequestItem.getProvisioningPlan();
            for (ProvisioningPlan.AccountRequest areq : Util.safeIterable(plan.getAccountRequests())) {
                if (ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(areq.getApplicationName())) {
                    List<ProvisioningPlan.AttributeRequest> attreqs = areq.getAttributeRequests();
                    for (ProvisioningPlan.AttributeRequest req : attreqs) {
                        if (!Util.isNullOrEmpty(req.getAssignmentId())) {
                            return req.getAssignmentId();
                        }
                    }
                }
            }
        }
        return null;
    }
}
