/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view;

import sailpoint.api.Explanator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.certification.ProvisioningPlanEditDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates a concise representation of a provisioning plan line item.
 * Additionally, ProvisioningPlanEditDTO.LineItem is not json-serializable.
 *
 */
public class LineItem {
    
    public static String ACCOUNT = "account";
    public static String NATIVE_IDENTITY = "nativeIdentity";
    public static String INSTANCE = "instance";
    public static String APPLICATION = "application";
    public static String APPLICATION_ID = "applicationId";
    public static String ATTRIBUTE = "attribute";
    public static String ATTRIBUTE_VALUE = "attributeValue";
    public static String PERMISSION_TARGET = "permissionTarget";
    public static String PERMISSION_RIGHTS = "permissionRights";
    public static String NEW_VALUE = "newValue";
    public static String NEW_OPERATION = "newOperation";
    
    private int id;
    private String account;
    private String nativeIdentity;
    private String instance;
    private String application;
    private String applicationId;
    private String attribute;
    private Object attributeValue;
    private String permissionTarget;
    private List<String> permissionRights;
    private boolean editable;
    private boolean canChangeValue;
    private String inputType;
    private List<String[]> selectOptions;
    private String[] operation;
    private boolean existingRemediation;
    private String attributeDisplayValue;

    /**
     * Holds the new value from the user input.
     */
    private String newValue;

    /**
     * Holds the new operation from the user input. Should correspond to ProvisioningPlan.Operation.
     */
    private String newOperation;
    
    public LineItem() {}

    @SuppressWarnings("unchecked")
    public LineItem(Map<String, Object> data) throws GeneralException {
        if (Util.isEmpty(data)) {
            throw new GeneralException("data is required");
        }
        
        this.account = (String)data.get(ACCOUNT);
        this.nativeIdentity = (String)data.get(NATIVE_IDENTITY);
        this.instance = (String)data.get(INSTANCE);
        this.application = (String)data.get(APPLICATION);
        this.applicationId = (String)data.get(APPLICATION_ID);
        this.attribute = (String)data.get(ATTRIBUTE);
        this.attributeValue = data.get(ATTRIBUTE_VALUE);
        this.permissionTarget = (String)data.get(PERMISSION_TARGET);
        this.permissionRights = (List<String>)data.get(PERMISSION_RIGHTS);
        this.newValue = (String)data.get(NEW_VALUE);
        this.newOperation = (String)data.get(NEW_OPERATION);
    }
    
    public LineItem(SailPointContext context, String identity,
                    ProvisioningPlanEditDTO.LineItem lineItem, Locale locale) throws GeneralException {
        this.id = lineItem.getId();

        this.inputType = lineItem.isShowFreetext() ? "text" : "select";
        this.editable = lineItem.isEditable();
        this.canChangeValue = lineItem.getCanChangeValue();
        this.existingRemediation = lineItem.isExistingRemediation();

        this.instance = lineItem.getAccountRequest().getInstance();
        this.nativeIdentity = lineItem.getAccountRequest().getNativeIdentity();
        this.application = lineItem.getAccountRequest().getApplication();

        // Need to grab the application ID for the purview on suggests.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", this.application));
        List<String> appResult = ObjectUtil.getObjectIds(context, sailpoint.object.Application.class, qo);
        this.applicationId = !Util.isEmpty(appResult) ? appResult.get(0) : "";
        
        this.account = ObjectUtil.getAccountId(context, identity, lineItem.getAccountRequest().getApplication(),
                lineItem.getAccountRequest().getInstance(), lineItem.getAccountRequest().getNativeIdentity());

        ProvisioningPlan.Operation currentOp = null;
        Object defaultRemediationModifiableOp = context.getObjectByName(Configuration.class, Configuration.OBJ_NAME)
            .get(Configuration.DEFAULT_REMEDIATION_MODIFIABLE_OP);

        if (lineItem.getAttributeRequest() != null){
            this.attribute = lineItem.getAttributeRequest().getName();
            this.attributeValue = lineItem.getAttributeRequest().getValue();
            this.attributeDisplayValue = Explanator.getDisplayValue(this.application, this.attribute, (String)this.attributeValue);
            if(defaultRemediationModifiableOp != null && defaultRemediationModifiableOp.toString().equalsIgnoreCase("Remove")) {
                currentOp = ProvisioningPlan.Operation.Remove;
            }
            else {
                currentOp = lineItem.getAttributeRequest().getOperation();
            }
        } else if (lineItem.getPermissionRequest() != null){
            this.permissionRights = lineItem.getPermissionRequest().getRightsList();
            this.permissionTarget = lineItem.getPermissionRequest().getTarget();
            currentOp = lineItem.getPermissionRequest().getOperation();
        }

        // Convert the operation into something we can use within an EXT combo box
        if (currentOp != null)
            this.operation = new String[]{currentOp.name(),
                    Internationalizer.getMessage(currentOp.getMessageKey(), locale)};

        selectOptions = new ArrayList<String[]>();
        for(ProvisioningPlan.Operation op : lineItem.getAllowedOperations()){
            selectOptions.add(new String[]{op.name(), Internationalizer.getMessage(op.getMessageKey(), locale)});
        }
    }

    public int getId() {
        return id;
    }

    public boolean getEditable() {
        return editable;
    }

    public String getInputType() {
        return inputType;
    }

    public String getAccount() {
        return account;
    }

    public String getNativeIdentity(){
        return nativeIdentity;
    }

    public String getApplication() {
        return application;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getInstance(){
        return instance;
    }

    public String getAttribute() {
        return attribute;
    }

    public Object getAttributeValue() {
        return attributeValue;
    }

    public String getAttributeDisplayValue() { return attributeDisplayValue; }

    public String getPermissionTarget() {
        return permissionTarget;
    }

    public List<String> getPermissionRights() {
        return permissionRights;
    }

    public boolean isEditable() {
        return editable;
    }

    public boolean isCanChangeValue() {
        return canChangeValue;
    }

    public List<String[]> getSelectOptions() {
        return selectOptions;
    }

    public String[] getOperation() {
        return operation;
    }

    public boolean isExistingRemediation() {
        return existingRemediation;
    }
    
    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getNewOperation() {
        return newOperation;
    }

    public void setNewOperation(String newOperation) {
        this.newOperation = newOperation;
    }
}
