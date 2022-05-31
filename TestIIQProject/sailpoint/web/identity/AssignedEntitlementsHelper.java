/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.identity;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Differencer;
import sailpoint.api.SailPointContext;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by ryan.pickens on 3/21/17.
 */
public class AssignedEntitlementsHelper {

    private static final Log log = LogFactory.getLog(AssignedEntitlementsHelper.class);

    private IdentityDTO parent;

    private String entId;

    private Date sunriseDate;

    private Date sunsetDate;

    private String name;

    private String value;

    private String application;

    private String nativeId;

    private String instance;

    public AssignedEntitlementsHelper(IdentityDTO parent) {
        this.parent = parent;
    }

    public List<AttributeAssignmentBean> getAssignedEntitlements() {
        if (this.parent.getState().getAssignedEntitlements() == null) {
            this.parent.getState().setAssignedEntitlements(buildAssignmentBeans(this.parent.getObject(), this.parent.getContext()));
        }

        return this.parent.getState().getAssignedEntitlements();
    }

    private List<AttributeAssignmentBean> buildAssignmentBeans(Identity ident, SailPointContext context) {
        List<AttributeAssignmentBean> beans = new ArrayList<>();

        List<AttributeAssignment> assignments = ident.getAttributeAssignments();

        for (AttributeAssignment ass : Util.safeIterable(assignments)) {
            AttributeAssignmentBean b = new AttributeAssignmentBean(ass);
            //If start date in the past, do not show. THis may have slight disconnect if
            // the request hasn't yet been processed. Will fix itself
            if (ass.getStartDate() != null && ass.getStartDate().before(new Date())) {
                b.setStartDate(null);
            }
            beans.add(b);
        }

        return beans;

    }

    public String editEntTimeFrame() throws GeneralException {
        AttributeAssignmentBean bean = getAssignmentBean();
        if (bean != null) {
            bean.setStartDate(this.sunriseDate);
            bean.setEndDate(this.sunsetDate);
        } else {
            log.warn("Could not find AttributeAssignmentBean");
        }
        return null;
    }

    private AttributeAssignmentBean getAssignmentBean() {
        for (AttributeAssignmentBean bean : Util.safeIterable(getAssignedEntitlements())) {
            if (Util.nullSafeEq(bean.getName(), this.name) &&
                    Util.nullSafeEq(bean.getApplicationName(), this.application) &&
                    Util.nullSafeEq(bean.getNativeIdentity(), this.nativeId, true, true) &&
                    Util.nullSafeEq(bean.getInstance(), this.instance, true, true) &&
                    Util.nullSafeEq(bean.getValue(), this.value)) {
                return bean;
            }
        }
        //Couldn't find bean
        return  null;
    }

    public void addAssignedEntitlementInfoToPlan(ProvisioningPlan plan) {

        if (plan != null) {
            Identity identity = this.parent.getObject();
            List<AttributeAssignmentBean> assignments = getAssignedEntitlements();
            for (AttributeAssignmentBean bean : Util.safeIterable(assignments)) {
                if (isUpdateNeeded(identity, bean)) {
                    ProvisioningPlan.AccountRequest accntReq = plan.getAccountRequest(bean.getApplicationName(), bean.getInstance(), bean.getNativeIdentity());
                    if (accntReq == null) {
                        //Need an account request
                        plan.add(bean.toAccountRequest(ProvisioningPlan.AccountRequest.Operation.Modify, ProvisioningPlan.Operation.Add));
                    } else {
                        //Add attribute request to current account request
                        accntReq.add(bean.toAttributeRequest(ProvisioningPlan.Operation.Add));
                    }
                }
            }
        }

    }

    private boolean isUpdateNeeded(Identity ident, AttributeAssignmentBean bean) {
        boolean updateNeeded = false;
        if (bean != null) {
            AttributeAssignment current = ident.getAttributeAssignment(bean.getApplicationName(), bean.getNativeIdentity(),
                    bean.getName(), bean.getStringValue(), bean.getInstance(), bean.getAssignmentId());

            if (current != null) {
                updateNeeded = (!Differencer.equal(current.getStartDate(), bean.getStartDate()) ||
                        !Differencer.equal(current.getEndDate(), bean.getEndDate()));
            }
        }

        return updateNeeded;
    }

    public String getEntId() {
        return entId;
    }

    public void setEntId(String s) {
        entId = s;
    }

    public Date getSunriseDate() {
        return sunriseDate;
    }

    public void setSunriseDate(Date sunriseDate) {
        this.sunriseDate = sunriseDate;
    }

    public Date getSunsetDate() {
        return sunsetDate;
    }

    public void setSunsetDate(Date sunsetDate) {
        this.sunsetDate = sunsetDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }
}
