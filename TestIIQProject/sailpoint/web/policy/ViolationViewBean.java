/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.policy;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SailPointObject;
import sailpoint.service.WorkItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.BaseBean;
import sailpoint.web.util.NavigationHistory;

/**
 * UI bean to hold information about policy violations.
 * 
 * See ViolationDetailer for additional comments
 *
 */
public class ViolationViewBean extends BaseBean implements Serializable{

    private static final long serialVersionUID = 5168239065690678141L;

    private static Log log = LogFactory.getLog(ViolationViewBean.class);

    public static String POLICY_DETAILS_PAGE = "policyDetails.xhtml";

    PolicyViolation _violation;
    ViolationDetailer violationDetails;

    //These have setters in the bean, so keep a copy of the value locally
    //to keep the ViolationDetailer read-only. 
    ApplicationActivity _activity;
    String _status;
    String _identityName;
    String _identityFirstname;
    String _identityLastname;
    String _identityId;
    String _certItemId;

    @SuppressWarnings("rawtypes")
    public ViolationViewBean() throws GeneralException {
        super();
        
        // this can be called from several different places, so examine the 
        // data in the request to figure out how to proceed
        _certItemId = getRequestParameter("certItemId");
        String violationId = peekRequestOrSessionParameter("violationId");
        String workitemId = getRequestParameter("workitemId");
        
        PolicyViolation pv = null;

        if (_certItemId != null) {
            CertificationItem certItem = getContext().getObjectById(CertificationItem.class, _certItemId);
            pv = certItem.getPolicyViolation();
        }

        if (pv == null && violationId != null) {
            pv = getContext().getObjectById(PolicyViolation.class, violationId);
        }

        if (pv == null && workitemId != null) {
            pv = buildStubViolation(workitemId, getRequestParameter("ruleName"));
        }

        init(getContext(), pv);
    }

    public ViolationViewBean(SailPointContext context, PolicyViolation v)
        throws GeneralException {
        super();

        init(context, v);
    }

    private void init(SailPointContext context, PolicyViolation pv) throws GeneralException
    {
        _violation = pv;
        violationDetails = new ViolationDetailer(context, pv, getLocale(), getUserTimeZone());
        
        _activity = violationDetails.getActivity();
        _status = violationDetails.getStatus();
        
        _identityName = violationDetails.getIdentityName();
        _identityFirstname = violationDetails.getIdentityFirstname();
        _identityLastname = violationDetails.getIdentityLastname();
        _identityId = violationDetails.getIdentityId();
        
    }

    /**
     * Build a skeletal policy violation from the violation info in the work item.
     */
    private PolicyViolation buildStubViolation(String workItemId, String ruleName) throws GeneralException {
        // get the WorkItem object provided in the request
        if (workItemId == null) {
            log.warn("No work item found with id: " + workItemId);
            return null;
        }
        
        // make sure we have a rule name
        if (ruleName == null) {
            log.warn("No rule name given for construction of ViolationViewBean.");
            return null;
        }

        try {
            WorkItemService workItemService = new WorkItemService(workItemId, this);
            return workItemService.getPolicyViolationStub(ruleName);
        } catch (ObjectNotFoundException e) {
            log.warn(e);
            return null;
        }
    }
    
    public String cancelAction() throws GeneralException {
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null)
            outcome = "listViolations";
        return outcome;
    }

    public boolean isCertUserAuthorized() throws GeneralException{

        if (_certItemId != null){
            CertificationItem item = getContext().getObjectById(CertificationItem.class, _certItemId);
            Certification cert = item.getCertification();
            return isAuthorized(cert);
        }

        return true;
    }

    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException{

        // this is performed in the super class, but do a quick check here to avoid executing
        // the more intensive ops below.
        if (object == null || Capability.hasSystemAdministrator(getLoggedInUser().getCapabilityManager().getEffectiveCapabilities()))
            return true;

        Certification cert = (Certification)object;
        if (cert.getCertifiers() != null && cert.getCertifiers().contains(getLoggedInUserName()))
            return true;

        if (getLoggedInUserName().equals(cert.getCreator()))
                return true;

        return false;
    }

    public String getSodConflict(){
        return violationDetails.getSodConflict();
    }

    public String getId() {
        return violationDetails.getId();
    }

    /**
     * @return the Policy
     */
    public Policy getPolicy() {
        return violationDetails.getPolicy();
    }

    /**
     * @return the _policyType
     * jsl - this may not be necessary any more now that we can return 
     * the full Policy?
     */
    public String getPolicyType() {
        return violationDetails.getPolicyType();
    }

    /**
     * @return the _activity
     */
    public ApplicationActivity getActivity() {
        return _activity;
    }

    /**
     * @param _activity the _activity to set
     */
    public void setActivity(ApplicationActivity _activity) {
        this._activity = _activity;
    }

    /**
     * @param type the _policyType to set
     */
    public void setPolicyType(String type) {
        // why was this here? you can't change the type while viewing it - jsl
        //_policyType = type;
    }

    public Date getCreated() {
        return violationDetails.getCreatedDate();
    }

    public String getPolicyName() {
        return violationDetails.getPolicyName();
    }

    public String getConstraint() {
        return violationDetails.getConstraint();
    }

    public String getSummary() {
        return violationDetails.getSummary();
    }

    public String getStatus() {
        return _status;
    }

    public void setStatus(String _status) {
        this._status = _status;
    }

    public String getCompensatingControl() {
        return violationDetails.getCompensatingControl();
    }

    public String getConstraintDescription() {
        return violationDetails.getConstraintDescription();
    }

    public String getConstraintWeight() {
        return violationDetails.getConstraintWeight();
    }

    public String getConstraintPolicy() {
        return violationDetails.getConstraintPolicy();
    }

    public String getRemediationAdvice() {
        return violationDetails.getRemediationAdvice();
    }

    public Map<String, Object> getArgs() {
        return violationDetails.getArgs();
    }
    
    public String getIdentityName() {
        return _identityName;
    }

    public void setIdentityName(String identityName) {
        this._identityName = identityName;
    }

    public String getIdentityId() {
        return _identityId;
    }

    public void setIdentityId(String identityId) {
        this._identityId = identityId;
    }
    
    public String getIdentityFirstname() {
        return _identityFirstname;
    }

    public void setIdentityFirstname(String firstname) {
        this._identityFirstname = firstname;
    }

    public String getIdentityLastname() {
        return _identityLastname;
    }

    public void setIdentityLastname(String lastname) {
        this._identityLastname = lastname;
    }

    public PolicyViolation getViolation() {
        return _violation;
    }
    
    public void setViolation(PolicyViolation violation) throws GeneralException {
        this._violation = violation;
        
        //re-initialize details with new violation
        init(getContext(), violation);
    }
    
    public String getOwner() {
        return violationDetails.getOwner();
    }

    /** Gets the jsf page that renders this violation on the identity edit page **/
    public String getIdentityPageRenderer() {

        // new way, first-class field
        String renderer = null;
        if ( _violation != null ) {
            renderer = _violation.getRenderer();        
            if (renderer == null) {
                // old way, stuffed in the argument map
                // temporarily supported until we can upgrade
                // or delete all old violations
                if (_violation.getArguments() != null) {
                    renderer = (String) _violation.getArguments().getString(
                            PolicyViolation.ARG_IDENTITY_PAGE_RENDERER);
                }
            }
        }
        return renderer;
    }

    /** Sets the jsf page that renders this violation on the identity page **/
    // jsl - why is this here?  would we ever override what the 
    // PolicyExecutor and set?
    /*
    public void setIdentityPageRenderer(String page) {
       _violation.setArgument(PolicyViolation.RENDERER_IDENTITY_PAGE, page);
    }
    */

    public IdentityHistoryItem getLastDecision() throws GeneralException {
        return violationDetails.getLastDecision();
    }

    public IdentityHistoryItem getCurrentDecision() throws GeneralException {
        return violationDetails.getCurrentDecision();
    }

    public boolean isAllowRemediation(){
        return violationDetails.isAllowRemediation();
    }
    
    /**
     * Flag used by to figure out if the violation has 
     * been deleted so we can display the appropriate 
     * message.
     * 
     * Used by 
     * 
     * @return
     */
    public boolean isViolationMissing() {
        if ( _violation == null ) return true;
        return false;
    }
    public String policyViolationDetail() {
        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        return "policyViolationDetail";
    }
}
