/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Bundle;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.policy.ViolationViewBean;


/**
 * A JSF been for providing advice around how to handle remediations of policy
 * violations and that allows selecting which business roles that constitute a
 * policy violation should be removed.
 * 
 * @author Kelly Grizzle
 */
public class PolicyViolationAdvisorBean
    extends BaseBean
    implements Serializable {

    /**
     * Inner bean holding the left/right bundle information and whether the
     * bundle has been selected for remediation.
     */
    public static class BusinessRoleBean {
        
        private Bundle businessRole;
        private boolean selected;

        public BusinessRoleBean(Bundle businessRole, boolean selected) {
            this.businessRole = businessRole;
            this.selected = selected;
        }

        public Bundle getBusinessRole() {
            return businessRole;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
    
    private PolicyViolation policyViolation;
    private List<BusinessRoleBean> leftBusinessRoles;
    private List<BusinessRoleBean> rightBusinessRoles;
    // Cached objects so we don't have to keep loading.
    private ViolationViewBean violationViewBean;
    private PolicyViolationAdvisor policyViolationAdvisor;

    
    /**
     * Default constructor.
     */
    public PolicyViolationAdvisorBean() {}

    /**
     * Constructor.
     * 
     * @param  violation  The PolicyViolation to advise on.
     */
    public PolicyViolationAdvisorBean(PolicyViolation violation)
        throws GeneralException {

        super();

        this.policyViolation = violation;
        this.policyViolationAdvisor = new PolicyViolationAdvisor(getContext(), violation, super.getLocale());
        if (null != violation) {
            this.violationViewBean = new ViolationViewBean(getContext(), violation);
        }
    }

    public String getEntitlementViolationsJson() throws GeneralException {
        PolicyTreeNode violations = getEntitlementViolations();
        List<PolicyTreeNode> selectedViolations = null;
        if (policyViolation != null) {
            selectedViolations = policyViolation.getEntitlementsToRemediate();
        }
        String response = PolicyViolationJsonUtil.encodeEntitlementViolationTree( violations, selectedViolations, getLocale() );
        return response;
    }
   
    public PolicyTreeNode getEntitlementViolations() throws GeneralException {
        return this.policyViolationAdvisor.getEntitlementViolations(null, false);
    }
   

    public PolicyViolation getPolicyViolation() {
        return this.policyViolation;
    }

    public ViolationViewBean getViolationViewBean() {
        return this.violationViewBean;
    }

    public List<BusinessRoleBean> getLeftBusinessRoles() throws GeneralException {
        if (null == this.leftBusinessRoles) {
            this.leftBusinessRoles = initBusinessRoles(this.policyViolationAdvisor.getLeftBusinessRoles());
        }
        return this.leftBusinessRoles;
    }

    public void setLeftBusinessRoles(List<BusinessRoleBean> businessRoles) {
        this.leftBusinessRoles = businessRoles;
    }

    public List<BusinessRoleBean> getRightBusinessRoles() throws GeneralException {
        if (null == this.rightBusinessRoles) {
            this.rightBusinessRoles = initBusinessRoles(this.policyViolationAdvisor.getRightBusinessRoles());
        }
        return this.rightBusinessRoles;
    }

    public void setRightBusinessRoles(List<BusinessRoleBean> businessRoles) {
        this.rightBusinessRoles = businessRoles;
    }
    
    /**
     * Initialize a BusinessRoleBean List with the given bundles.
     */
    private List<BusinessRoleBean> initBusinessRoles(List<Bundle> bundles)
        throws GeneralException {

        List<BusinessRoleBean> jfs = new ArrayList<BusinessRoleBean>();

        if (null != bundles) {
            for (Bundle b : bundles) {
                jfs.add(new BusinessRoleBean(b, this.policyViolationAdvisor.isBusinessRoleSelected(b)));
            }
        }

        return jfs;
    }

    /**
     * Return the list of Bundles selected for remediation.
     * 
     * @return The list of Bundles selected for remediation.
     */
    public List<Bundle> getBusinessRolesToRemediate() throws GeneralException {
        
        List<Bundle> toRemediate = getSelectedBusinessRoles(getLeftBusinessRoles());
        toRemediate.addAll(getSelectedBusinessRoles(getRightBusinessRoles()));
        return toRemediate;
    }

    /**
     * Return the List of Bundles that are selected in the given list.
     */
    private List<Bundle> getSelectedBusinessRoles(List<BusinessRoleBean> jfs) {
        
        List<Bundle> selected = new ArrayList<Bundle>();

        if (null != jfs) {
            for (BusinessRoleBean jf : jfs) {
                if (jf.isSelected()) {
                    selected.add(jf.getBusinessRole());
                }
            }
        }

        return selected;
    }

}
