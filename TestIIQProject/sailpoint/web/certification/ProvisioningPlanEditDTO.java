/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.certification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import sailpoint.api.ObjectUtil;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;


/**
 * This is a DTO that allows editing provisioning plans.  This turns each
 * attribute and permission request into a line item,  which will also provide
 * information about whether the item is editable, how to edit it, etc...
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ProvisioningPlanEditDTO extends BaseDTO {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private static int idCounter = 0;
    
    /**
     * A LineItem is a single attribute or permission from a provisioning plan
     * that may or may not be editable.
     */
    public class LineItem implements Serializable {

        private int id;
        private AccountRequest accountRequest;
        private AttributeRequest attributeRequest;
        private AttributeRequest originalAttributeRequest;
        private PermissionRequest permissionRequest;
        private PermissionRequest originalPermissionRequest;
        private Boolean editable;
        private UserInterfaceInputType inputType;
        private String schemaName;


        /**
         * Private constructor used by other constructors.
         */
        private LineItem(AccountRequest acctReq) {
            this.accountRequest = acctReq;
            this.id = idCounter++;
        }
        
        /**
         * Constructor for an attribute request.
         */
        public LineItem(AccountRequest acctReq, AttributeRequest attrReq, String schema) {
            this(acctReq);
            this.attributeRequest = attrReq;
            this.originalAttributeRequest = new AttributeRequest(attrReq);
            this.schemaName = schema;
        }

        /**
         * Constructor for a permission request.
         */
        public LineItem(AccountRequest acctReq, PermissionRequest permReq, String schema) {
            this(acctReq);
            this.permissionRequest = permReq;
            this.originalPermissionRequest = new PermissionRequest(permReq);
            this.schemaName = schema;
        }

        private UserInterfaceInputType getUserInterfaceInputType()
            throws GeneralException {

            if (null == this.inputType) {
                String appName = this.accountRequest.getApplication();
                if (null != this.attributeRequest) {
                    this.inputType =
                        ObjectUtil.getRemediationInputType(getContext(), appName, schemaName, this.attributeRequest.getName());
                }
                else {
                    this.inputType =
                        ObjectUtil.getPermissionRemediationInputType(getContext(), appName, schemaName);
                }
            }

            return this.inputType;
        }

        public boolean getCanChangeValue() throws GeneralException {

            if (readOnly)
                return false;

            ProvisioningPlan.Operation op =
                (null != this.attributeRequest) ? this.attributeRequest.getOperation()
                                                : this.permissionRequest.getOperation();
            return !ProvisioningPlan.Operation.Remove.equals(op);
        }

        public boolean isShowFreetext() throws GeneralException {
            return UserInterfaceInputType.Freetext.equals(getUserInterfaceInputType());
        }

        public boolean isShowSelect() throws GeneralException {
            return UserInterfaceInputType.Select.equals(getUserInterfaceInputType());
        }

        /**
         * Return whether this attribute or permission is editable according to
         * the schema configuration.
         */
        public boolean isEditable() throws GeneralException {
            if (null == this.editable) {
                this.editable = false;
                if (!readOnly) {
                    String app = this.accountRequest.getApplication();
                    if (null != this.attributeRequest) {
                        this.editable =
                            ObjectUtil.isRemediationModifiable(getContext(), app, schemaName, this.attributeRequest.getName());
                    }
                    else {
                        this.editable =
                            ObjectUtil.isPermissionRemediationModifiable(getContext(), this.accountRequest.getApplication(), schemaName);
                    }
                }
            }

            return editable;
        }
        
        public boolean isExistingRemediation() {
            return existingRemediation;
        }

        /**
         * Return the available operations.
         */
        public List<SelectItem> getOperations() {

            List<SelectItem> items = new ArrayList<SelectItem>();
            for(ProvisioningPlan.Operation op : getAllowedOperations()){
                items.add(new SelectItem(op, getMessage(op.getMessageKey())));
            }
            
            return items;
        }

        public List<ProvisioningPlan.Operation> getAllowedOperations() {

            List<ProvisioningPlan.Operation> ops = new ArrayList<ProvisioningPlan.Operation>();

            ops.add(ProvisioningPlan.Operation.Remove);
            ops.add(ProvisioningPlan.Operation.Set);


            Operation op = null;
            if (null != this.attributeRequest) {
                op = this.attributeRequest.getOperation();
            }
            else if (null != this.permissionRequest) {
                op = this.permissionRequest.getOperation();
            }

            // Display "Add" only if it was selected.  This can happen if we
            // convert a set to an add/remove.
            if (Operation.Add.equals(op)) {
                ops.add(ProvisioningPlan.Operation.Add);
            }

            return ops;
        }

        /**
         * Return the generated id for this line item.
         */
        public int getId() {
            return this.id;
        }
        
        /**
         * Return the account request.
         */
        public AccountRequest getAccountRequest() {
            return this.accountRequest;
        }
        
        
        public AttributeRequest getAttributeRequest() {
            return this.attributeRequest;
        }
        
        public void setAttributeRequest(AttributeRequest attributeRequest) {
            this.attributeRequest = attributeRequest;
        }
        
        public PermissionRequest getPermissionRequest() {
            return this.permissionRequest;
        }
        
        public void setPermissionRequest(PermissionRequest permissionRequest) {
            this.permissionRequest = permissionRequest;
        }

        public AttributeRequest getOriginalAttributeRequest() {
            return this.originalAttributeRequest;
        }

        public PermissionRequest getOriginalPermissionRequest() {
            return this.originalPermissionRequest;
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private ProvisioningPlan provisioningPlan;
    private List<LineItem> lineItems;
    private boolean readOnly;
    private boolean existingRemediation;
    private String schema;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public ProvisioningPlanEditDTO() {
        super();
    }
    
    /**
     * Constructor.
     */
    public ProvisioningPlanEditDTO(ProvisioningPlan plan, boolean readOnly, boolean existingRemediation, String schema) {
        this();
        this.provisioningPlan = plan;
        this.existingRemediation = existingRemediation;
        this.readOnly = readOnly;
        this.schema = schema;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    ProvisioningPlan getProvisioningPlan() throws GeneralException{

        // Change "Set" operations to a "remove old value/add new value".
        for (LineItem item : getLineItems()) {
            AccountRequest acctReq = item.getAccountRequest();

            changeSetToRemoveAdd(acctReq, item.getOriginalAttributeRequest(),
                                 item.getAttributeRequest());
            changeSetToRemoveAdd(acctReq, item.getOriginalPermissionRequest(),
                                 item.getPermissionRequest());
        }

        return this.provisioningPlan;
    }

    /**
     * If the given modified GenericRequest is a "Set" operation, change this to
     * an "add the new value, remove the old value" pair of operations.  Setting
     * single values on a multi-valued attribute will clear out all other
     * values.
     * 
     * @param  acctReq      The AccountRequest on which the modified request
     *                      lives.
     * @param  originalReq  A copy of the original request (before modified).
     * @param  modifiedReq  The modified request.
     */
    private void changeSetToRemoveAdd(AccountRequest acctReq,
                                      GenericRequest originalReq,
                                      GenericRequest modifiedReq) {
        
        // Only change the requests if the user chose "Set".
        if ((null != modifiedReq) &&
            Operation.Set.equals(modifiedReq.getOperation())) {

            // Change the "Set" operation to an add for the new value.
            modifiedReq.setOperation(Operation.Add);

            // Add back the original request, which will remove the
            // original value.
            acctReq.add(originalReq);
        }
    }
    
    /**
     * Return whether this plan has any editable entitlements or not.
     */
    public boolean hasEditableEntitlements() throws GeneralException {

        boolean anyEditable = false;

        for (LineItem item : getLineItems()) {
            if (item.isEditable()) {
                anyEditable = true;
                break;
            }
        }
        
        return anyEditable;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public List<LineItem> getLineItems() throws GeneralException{
        if (null == this.lineItems) {
            this.lineItems = new ArrayList<LineItem>();

            if ((null != this.provisioningPlan) &&
                (null != this.provisioningPlan.getModifyAccountRequests())) {

                for (AccountRequest acctReq : this.provisioningPlan.getModifyAccountRequests()) {
                    if (null != acctReq.getAttributeRequests()) {
                        for (AttributeRequest attrReq : acctReq.getAttributeRequests()) {
                            addLineItems(acctReq, attrReq);
                        }
                    }

                    if (null != acctReq.getPermissionRequests()) {
                        for (PermissionRequest permReq : acctReq.getPermissionRequests()) {
                            addLineItems(acctReq, permReq);
                        }
                    }
                }
            }
        }

        return this.lineItems;
    }
    
    private void addLineItems(AccountRequest acctReq, AttributeRequest attrReq) 
            throws GeneralException {
        if (attrReq.getValue() instanceof List) {
            List valueList = (List)attrReq.getValue();
            for (Object val : valueList) {
                AttributeRequest newReq = new AttributeRequest(attrReq);
                newReq.setValue(val);
                addLineItem(new LineItem(acctReq, newReq, this.schema));
            }
        } else {
            addLineItem(new LineItem(acctReq, attrReq, this.schema));
        }
    }
    
    private void addLineItems(AccountRequest acctReq, PermissionRequest permReq) 
            throws GeneralException {
        if (permReq.getRightsList() != null && permReq.getRightsList().size() > 1) {
            List<String> valueList = permReq.getRightsList();
            for (String val : valueList) {
                PermissionRequest newReq = new PermissionRequest(permReq);
                newReq.setRightsList(Util.asList(val));
                addLineItem(new LineItem(acctReq, newReq, this.schema));
            }
        } else {
            addLineItem(new LineItem(acctReq, permReq, this.schema));
        }
    }
    
    private void addLineItem(LineItem item) 
            throws GeneralException {
        if (item != null) {
            if (!this.existingRemediation && item.isEditable()) {
                if (item.getAttributeRequest() != null) {
                    item.getAttributeRequest().setOperation(Operation.Set);
                } else if (item.getPermissionRequest() != null) {
                    item.getPermissionRequest().setOperation(Operation.Set);    
                } 
            }
            this.lineItems.add(item);
        }
    }

    public void setLineItems(List<LineItem> items) {
        this.lineItems = items;
    }
}
