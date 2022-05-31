/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.identity;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;

import java.util.Date;
import java.util.List;

/**
 * Created by ryan.pickens on 3/22/17.
 */
public class AttributeAssignmentBean {

    private static final Log log = LogFactory.getLog(AttributeAssignmentBean.class);

    String applicationName;

    String applicationId;

    String instance;

    String nativeIdentity;

    String name;

    Object value;

    String annotation;

    String assigner;

    Date startDate;

    Date endDate;

    String type;

    String assignmentId;



    public AttributeAssignmentBean(AttributeAssignment assignment) {
        applicationId = assignment.getApplicationId();
        applicationName = assignment.getApplicationName();
        instance = assignment.getInstance();
        nativeIdentity = assignment.getNativeIdentity();
        name = assignment.getName();
        value = assignment.getValue();
        annotation = assignment.getAnnotation();
        assigner = assignment.getAssigner();
        startDate = assignment.getStartDate();
        endDate = assignment.getEndDate();
        type = assignment.getType().name();
        assignmentId = assignment.getAssignmentId();
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public String getAssigner() {
        return assigner;
    }

    public void setAssigner(String assigner) {
        this.assigner = assigner;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getAssignmentId() { return assignmentId; }

    public void setAssignmentId(String s) {
        this.assignmentId = s;
    }

    public String getStringValue() {
        String str = null;
        if (value != null) {
            // Keep consistent with AttributeAssignment Object
            // The non-bean version of the call will not wrap double quotes around the value
            // if it is a String
            if (value instanceof String) {
                str = (String)value;
            } else {
                str = sailpoint.tools.Util.listToCsv(getListValue());
            }
        }
        return str;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getListValue() {
        List<Object> list = null;
        if ( value != null ) {
            list = sailpoint.tools.Util.asList(value);
        }
        return list;
    }

    public boolean matches(AttributeAssignment assignment) {
        if (assignment == null) {
            return false;
        }

        if ( sailpoint.tools.Util.nullSafeCompareTo(name, assignment.getName()) == 0  &&
                sailpoint.tools.Util.nullSafeCompareTo(getStringValue(), assignment.getStringValue()) == 0  &&
                sailpoint.tools.Util.nullSafeCompareTo(nativeIdentity, assignment.getNativeIdentity()) == 0 &&
                sailpoint.tools.Util.nullSafeCompareTo(instance, assignment.getInstance()) == 0 &&
                sailpoint.tools.Util.nullSafeCompareTo(applicationId, assignment.getApplicationId()) == 0  &&
                sailpoint.tools.Util.nullSafeCompareTo(assignmentId, assignment.getAssignmentId()) == 0 )  {
            return true;
        }

        return false;
    }

    public boolean isPermission() {
        return ( sailpoint.tools.Util.nullSafeEq(type, ManagedAttribute.Type.Permission.name()) ) ? true : false;
    }

    public ProvisioningPlan.AccountRequest toAccountRequest(ProvisioningPlan.AccountRequest.Operation acctReqOp, ProvisioningPlan.Operation attrReqOp) {

        ProvisioningPlan.AccountRequest request = new ProvisioningPlan.AccountRequest();
        request.setNativeIdentity(getNativeIdentity());
        request.setInstance(getInstance());
        request.setApplication(getApplicationName());
        request.setOperation(acctReqOp);

        ProvisioningPlan.GenericRequest attr = (isPermission()) ? new ProvisioningPlan.PermissionRequest() : new ProvisioningPlan.AttributeRequest();
        attr.setName(getName());
        attr.setValue(getValue());
        attr.setOperation(attrReqOp);
        if (getStartDate() != null) {
            attr.setAddDate(getStartDate());
        }
        if (getEndDate() != null) {
            attr.setRemoveDate(getEndDate());
        }
        attr.setAssignmentId(getAssignmentId());
        attr.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");
        request.add(attr);

        return request;
    }

    /**
     * Return an AttributeRequest or PermissionRequest for the given AttributeAssignment
     * @param attrReqOp - Operation for the AttributeRequest
     * @return
     */
    public ProvisioningPlan.GenericRequest toAttributeRequest(ProvisioningPlan.Operation attrReqOp) {
        ProvisioningPlan.GenericRequest attr = (isPermission()) ? new ProvisioningPlan.PermissionRequest() : new ProvisioningPlan.AttributeRequest();
        attr.setName(getName());
        attr.setValue(getValue());
        attr.setOperation(attrReqOp);
        if (getStartDate() != null) {
            attr.setAddDate(getStartDate());
        }
        if (getEndDate() != null) {
            attr.setRemoveDate(getEndDate());
        }
        attr.setAssignmentId(getAssignmentId());
        attr.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");
        return attr;
    }

}
