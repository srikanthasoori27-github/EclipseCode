/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * NOTE: This class is in the published Javadocs!
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ValidationException;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleDifference;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.LimitReassignmentException;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.SelfCertificationException;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItem.Type;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.NonIdentityRequestApprovalService;
import sailpoint.service.WorkItemService;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.tools.DateUtil;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LoadingMap;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.FormHandler.FormStore;
import sailpoint.web.certification.CertificationBean;
import sailpoint.web.certification.CertificationEntityViewBean;
import sailpoint.web.certification.CertificationItemBean;
import sailpoint.web.extjs.Response;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleEditorBean;
import sailpoint.web.policy.ViolationViewBean;
import sailpoint.web.task.TaskResultBean;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.ApprovalItemFilterBean;
import sailpoint.web.workitem.ApprovalItemGridHelper;
import sailpoint.web.workitem.ApprovalSetDTO;
import sailpoint.web.workitem.ApprovalSetDTO.ApprovalItemDTO;
import sailpoint.web.workitem.WorkItemNavigationUtil;
import sailpoint.web.workitem.WorkItemUtil;
import sailpoint.workflow.RoleLibrary;


/**
 * A JSF UI bean that displays and allows working with work items.
 */
public class WorkItemBean
    extends BaseObjectBean<WorkItem>
    implements CertificationBean.SaveListener, NavigationHistory.Page, FormBean, FormStore
{
    private static Log log = LogFactory.getLog(WorkItemBean.class);

    /**
     * Temporary HttpSession attribute where we store the desired
     * WorkItem id to display when transition from random pages.
     * These pages may be using "id" or "editform:id" for their
     * own purposes.  Would be better to not use the session
     * but with the JSF <redirect/> issue this is hard.
     * Really need to come up with a framework for doing
     * these sorts of dynamic navigation rules better.
     */
    public static final String ATT_ITEM_ID = "WorkItemId";
    public static final String FORM_BEAN_WORKITEM_ID = "formBeanWorkItemId";


    public static final String CERTIFICATION_TO_FORWARD_ID = "certificationToForward";
    public static final String NEXT_PAGE = "workItemFwdNextPage";
    private static final String GRID_STATE = "workItemGridState";
    private static final String APPROVAL_SET_DTO = "approvalSetDTO";

    private static final String WORKITEM_STATUS_UNKNOWN = "unknown";
    private static final String WORKITEM_STATUS_ACTIVE = "active";
    private static final String WORKITEM_STATUS_ARCHIVED = "archived";

    private String newComment;
    private boolean editable = false;
    private boolean inWorkflowSession;

    // Used for forwarding.
    private Identity newOwner;
    private String forwardComment;
    private String nextForwardPage;
    // This handles the case where an entire certification is forwarded from someone's inbox
    private boolean forwardingCertification;

    private CertificationBean certification;
    private CertificationItemBean.BusinessRoleBean businessRole;
    private CertificationItemBean.ExceptionBean exception;
    private CertificationItemBean.ViolationBean violation;
    private CertificationItemBean.ProfileBean profile;
    private CertificationItemBean.GenericCertifiableBean businessRoleRelationshipBean;
    private CertificationItemBean.GenericCertifiableBean businessRoleGrantBean;
    private IdentityDTO identity;

    // This should not be confused with the violation CertificationitemBean.
    // This bean represents a PolicyViolation forwarded to the current user from
    // the policy violation viewer. It's main purpose is to get the user to go to the
    // PV viewer and address the violation.
    private ViolationViewBean violationViewBean;

    // Used to goto another work item page from within this page.
    private String selectedWorkItemId;

    private CertificationEntityViewBean certEntityViewBean;
    //
    // Used for approval work items
    //

    /**
     * Cached objects being approved.
     */
    SailPointObject _newObject;

    /**
     * Cached summary of bundle or profile differences.
     */
    BundleDifference _roleDifferences;

    /**
     * Cached list of default actions for the violation.
     * Used with policy violation workflows.
     */
    List<SelectItem> _defaultPolicyViolationActions;

    /**
     * Identity Bean that is built off the targetClass, targetName and targetId
     * properties of the workitem. This can be null depending on the what type
     * of certification we are dealing with. Initially designed for use in lcm workitem
     * approval forms.
     */
    private IdentityDTO _targetIdentityBean;

    /**
     * FormBean that is used during editable approvals in LCM.
     * The Bean is created from the Form pulled from the workitem.
     */
    FormRenderer _formBean;

     /**
      * DTO of the ApprovalSet that is part of this WorkItem.
      */
    ApprovalSetDTO _approvalSetDTO;

    /**
     * Workflow case object associated with this work item
     */
    WorkflowCase _workflowCase;
    
    /**
     * WorkflowSession associated with this workflow case
     */
    WorkflowSession _session;

    private String priority;

    private List<ColumnConfig> approvalColumns;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    ApprovalItemFilterBean approvalItemFitler = new ApprovalItemFilterBean();

    private String decisionJson;

    public ApprovalItemFilterBean getApprovalItemFilter() {
        return approvalItemFitler;
    }

    public ApprovalItemFilterBean.ApprovalItemFilter getFilter() {
        return approvalItemFitler.getFilter();
    }
    /**
     * Default constructor.
     */
    public WorkItemBean() throws GeneralException
    {
        super();
        setScope(WorkItem.class);
        setStoredOnSession(false);
        


        // jsl - Added this convention so we can transition here from
        // pages that are already using "id" or "editform:id" for
        // their own purposes.
        Map session = getSessionScope();
        String itemId = (String)session.get(ATT_ITEM_ID);
        if (itemId != null) {
            setObjectId(itemId);
            // a one shot deal
            session.remove(ATT_ITEM_ID);
        }
        
        WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(session);
        _session = wfUtil.getWorkflowSession();

        Identity loggedInUser = super.getLoggedInUser();

        // If a certification ID is specified, load the work item owned by the
        // logged in user for the given certification.  This will only work
        // if the logged in user is the one forwarding the item or if there is
        // only one work item for the cert.  TODO to figure out how to get the
        // work item owner into this flow so that work items can be forwarded
        // in the dashboard by non-owners.
        String certId = super.getRequestParameter(CERTIFICATION_TO_FORWARD_ID);
        if (null != certId) {
            this.forwardingCertification = true;

            Certification cert = getContext().getObjectById(Certification.class, certId);
            List<WorkItem> items = cert.getWorkItems();
            if (null != items) {
                // if there is only one work item for this cert, then this
                // must be the one to forward independent of the owner
                // matching the logged in user.
                super.setObjectId(WorkItemUtil.getWorkItemId(items, loggedInUser));
            }
        }

        /** Try to get the object from the workflow session if it is null still **/
        if(super.getObject()==null){
            if(_session !=null && _session.getWorkItem()!=null) {
                super.setObjectId(_session.getWorkItem().getId());
                this.inWorkflowSession = true;
                // clear the approval set DTO
              //  getSessionScope().remove(APPROVAL_SET_DTO);
            }
        }

        initWorkItem();

        String next = (String)session.get(NEXT_PAGE);
        if (null != next) {
            this.nextForwardPage = next;
        }

        next = super.getRequestParameter(NEXT_PAGE);
        if (null != next) {
            this.nextForwardPage = next;
        }
    }

    /**
     * Constructor from a previous state.
     *
     * @param  state  The previous state of this bean, a work item ID is expected.
     *
     * @see #getFormBeanState()
     */
    public WorkItemBean(Map<String,Object> state) throws GeneralException {
        this();

        if (null == state) {
            throw new IllegalArgumentException("Expected a non-null state.");
        }

        String formBeanWorkItemId = (String)state.get(FORM_BEAN_WORKITEM_ID);
        if (!Util.isNothing(formBeanWorkItemId)) {
            setObjectId(formBeanWorkItemId);
            initWorkItem();
        }
    }

    private void initWorkItem() throws GeneralException {
        WorkItem workItem = super.getObject();

        if (workItem != null) {
            this.editable = !isReadOnly();
        }

        if (workItem == null || workItem.getLevel() == null) {
            this.priority = WorkItem.Level.Normal.name();
        } else {
            this.priority = workItem.getLevel().name();
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDDEN METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public WorkItem createObject()
    {
        // Don't want to allow creating a work item for now.  This is always
        // done programmatically.
        return null;
    }

    @Override
    public WorkItem getObject() throws GeneralException {
        String objectId = super.getRequestOrSessionParameter("workItemForwardPanelForm:id");

        if (objectId == null || objectId.trim().length() == 0) {
            objectId = super.getRequestOrSessionParameter("navigationForm:objectId");
        }

        if (objectId != null && objectId.trim().length() > 0) {
            _objectId = objectId;
        }

        return super.getObject();
    }
    
    public WorkItem getObjectSafe() {
        WorkItem retItem = null;
        
        try {
            retItem = getObject();
        } catch (GeneralException ge) {
            log.error("Unable to retrieve workitem!", ge);
        }
        
        return retItem;
    }

    /**
     * After the certification is saved, reset the state of this bean so that it
     * will be recalculated. Also, save the page state in the navigation
     * history.
     */
    @Override
    public void certificationSaved()
    {
        this.certification = null;
        this.businessRole = null;
        this.exception = null;
        this.violation = null;

        // This pops off what the CertificationBean stuffed in the nav history
        // when the decision was saved.
        NavigationHistory.getInstance().back();

        // Now save this page's state in the navigation history.  This will get
        // used in the forward string returned by the CertificationBean action.
        NavigationHistory.getInstance().saveHistory(this);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // UI BEAN PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Provides direct access to work item variables for those cases
     * when they are nicely formatted.
     */
    public Map getAttributes() throws GeneralException {
        Map map = null;
        WorkItem item = super.getObject();
        if (item != null)
            map = item.getAttributes();

        // since JSF pukes on null
        if (map == null) map = new HashMap();

        return map;
    }

    /**
     * Specialized attribute map accessor when dealing with components
     * like selectManyListBox that need to post a List, and which
     * gets confused with a raw Map. See commentary in MapWrapper
     * for more about what is going on here.
     */
    public Map<String,List> getListAttributes() throws GeneralException {
        return new MapWrapper<String,List>(getAttributes(), new ArrayList());
    }

    public String getNewComment()
    {
        return newComment;
    }

    public void setNewComment(String newComment)
    {
        this.newComment = newComment;
    }

    public boolean isEditable() {
        return this.editable;
    }

    /**
     * A method that investigates whether a given workitem
     * can be edited/completed.
     */
    private boolean isReadOnly() throws GeneralException {
        //IIQETN-4282 :- Allowing the requester to see details, add comments and save
        //an access request
        Identity loggedInUser = super.getLoggedInUser();
        WorkItemService svc = new WorkItemService(this.getObjectId(), this, true);

        return !svc.isEditable(loggedInUser);
    }

    public boolean isInWorkflowSession() {
        return this.inWorkflowSession;
    }

    public Identity getNewOwner() {
        return newOwner;
    }

    public void setNewOwner(Identity newOwner) {
        this.newOwner = newOwner;
    }

    public String getForwardComment() {
        return forwardComment;
    }

    public void setForwardComment(String forwardComment) {
        this.forwardComment = forwardComment;
    }

    public String getNextForwardPage() {
        return nextForwardPage;
    }

    public void setNextForwardPage(String nextForwardPage) {
        this.nextForwardPage = nextForwardPage;
    }

    public boolean isForwardingCertification() {
        return this.forwardingCertification;
    }

    public void setForwardingCertification(boolean forwardingCertification) {
        this.forwardingCertification = forwardingCertification;
    }

    /**
     * A method that decides if the logged in user is the owner of within a workgroup that owns
     * a workItem.
     * 
     * @return true if the logged in user is the owner or within a workgroup that owns a workItem
     * @throws GeneralException
     */
    public boolean isOwnerOrIsInWorkgroup() throws GeneralException {
    	Identity loggedInUser = super.getLoggedInUser();
    	Identity owner = getOwner();
    	if (loggedInUser.getId().equals(owner.getId()) || (owner.isWorkgroup() && loggedInUser.isInWorkGroup(owner))){
    		return true;
    	} else {
    		return false;
    	}
    }

    public Identity getOwner() throws GeneralException{
        return getObject().getOwner();
    }

    public boolean isAccessRequestViewable() throws GeneralException {
        return IdentityRequestAuthorizer.isAuthorized(getObject().getIdentityRequestId(), this);
    }

    public boolean isOwnedByWorkgroup() throws GeneralException{
        WorkItem item = getObject();
        return item != null && item.getOwner() != null && item.getOwner().isWorkgroup();
    }

    /**
     * If the WorkItem is certification related, we will return the CertificationBean representing the certification.
     * 
     * @return CertificationBean representing the certification associated to the given WorkItem
     * @throws GeneralException
     */
    public CertificationBean getCertification() throws GeneralException
    {
        if (null == this.certification)
        {
            WorkItem item = super.getObject();

            // Only return a certification for items that deal with certs.
            if ((null != item) &&
                ( ( WorkItem.Type.Delegation.equals( item.getType() ) && !isStandaloneViolationDelegation() ) ||
                 WorkItem.Type.Challenge.equals(item.getType()))) {
                this.certification = createCertificationBean(item);
            }
        }

        return this.certification;
    }

    /**
     * Create a CertificationBean for the given WorkItem. This assumes that the
     * item is certification related.
     */
    private CertificationBean createCertificationBean(WorkItem item)
        throws GeneralException {

        CertificationBean certBean = null;
        CertificationItem certItem =
            (null != item.getCertificationItem())
                ? item.getCertificationItem(getContext()) : null;
        CertificationEntity entity =
            (null != certItem) ? certItem.getParent()
                               : item.getCertificationEntity(getContext());
        certBean =
            new CertificationBean(item.getCertification(getContext()),
                                  entity, this, item.getId());

        return certBean;
    }

    public List<WorkItem.State> getStates () {
        List<WorkItem.State> list = new ArrayList<WorkItem.State>();
        for (WorkItem.State state : WorkItem.State.values())
        {
            list.add(state);
        }
        return list;

    }

    public List<WorkItem.Level> getLevels () {
        List<WorkItem.Level> list = new ArrayList<WorkItem.Level>();
        for (WorkItem.Level level : WorkItem.Level.values())
        {
            list.add(level);
        }
        return list;

    }

    public List<WorkItem.Type> getTypes () {
        List<WorkItem.Type> list = new ArrayList<WorkItem.Type>();
        for (WorkItem.Type type : WorkItem.Type.values()) {

            // we have historically filtered these, why? - jsl
            if (!type.equals(WorkItem.Type.Generic) &&
                !type.equals(WorkItem.Type.Test) &&
                !type.equals(WorkItem.Type.Event)) {

                // note that these have to be localized!
                String key = type.getMessageKey();
                String name = getMessage(key);
                if (name != null)
                    list.add(type);
            }
        }
        return list;

    }

    /**
     * @return true if the subject of this work item is an account group
     * @throws GeneralException
     */
    public boolean isAccountGroupEntity() throws GeneralException{
        CertificationBean certBean = getCertification();
        return certBean != null && certBean.isAccountGroupCertification();
    }

    /**
     * Indicates that this workitem is not part of a certification, but is
     * a work item to handle an individual policy violation.
     *
     * @return True if this is a item.type==PolicyViolation.
     */
    public boolean isPolicyViolationWorkItem(){
        try{
            WorkItem item = getObject();
            return  WorkItem.Type.PolicyViolation.equals(item.getType());
        }catch (GeneralException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Indicates that the work item in question is a delegated standalone policy
     * violation work item.
     *
     * @return True if this is a delegated violation work item
     * @throws GeneralException
     */
    public boolean isStandaloneViolationDelegation() throws GeneralException{
        WorkItem item = getObject();
        return item.isTargetClass(PolicyViolation.class)
            && WorkItem.Type.Delegation.equals(item.getType());
    }

    /**
     * It is possible for a PolicyViolation to be deleted out from under a work item.
     * The page should check this and adjust the form action buttons. Unfortunately
     * there is a lot of code that assumes getViolationViewBean and other methods
     * will return non-null, it is easier to prevent the action buttons than
     * track down all the places that need to check.
     */
    public boolean isValidViolation() throws GeneralException {
        return getViolationViewBean() != null;
    }

    /**
     * 
     * @return true if the workItem is certification related and the certification is of Type BusinessRoleComposition
     * @throws GeneralException
     */
    public boolean isBusinessCompositionCertification() throws GeneralException{
        WorkItem workItem = super.getObject();
        if (null != workItem){
            Certification cert = workItem.getCertification(getContext());
            if (cert != null){
                return Certification.Type.BusinessRoleComposition.equals(cert.getType());
            }
        }

        return false;
    }

    /**
     * Used to customize the columns when displaying working
     * items.
     */
    public boolean isAccountGroupPermissionCertification()
        throws GeneralException{

        WorkItem workItem = super.getObject();
        if (null != workItem){
            Certification cert = workItem.getCertification(getContext());
            if (cert != null){
                Certification.Type type = cert.getType();
                if ( Certification.Type.AccountGroupPermissions.equals(type))
                    return true;
            }
        }
        return false;
    }

    /**
     * @return true if the UI should allow work item priorities to be edited; false otherwise
     */
    public boolean isPriorityEditingAllowed() {
        boolean priorityEditingEnabled = false;
        try {
            WorkItem workItem = super.getObject();
            if (workItem != null) {
                String requesterDisplayName = (workItem.getRequester() != null ) ? workItem.getRequester().getDisplayableName() : null;
                String ownerDisplayName = (workItem.getOwner() != null ) ? workItem.getOwner().getDisplayableName() : null;
                priorityEditingEnabled = WorkItemUtil.isWorkItemPriorityEditingEnabled(ownerDisplayName, requesterDisplayName, getLoggedInUser());
            } else {
                priorityEditingEnabled = false;
            }
        } catch (GeneralException e) {
            log.error("The WorkItemBean could not fetch the current user's credentials.  Priorities won't be editable.", e);
            priorityEditingEnabled = false;
        }
        return priorityEditingEnabled;
    }

    /**
     * If the WorkItem is certification related, and the item represented by the workItem is of type Bundle, return
     * the UI bean representing the business role
     * 
     * @return UI bean used to hold information about business role certification.
     * @throws GeneralException
     */
    public CertificationItemBean.BusinessRoleBean getBusinessRole() throws GeneralException
    {
        if (null == this.businessRole)
        {
            WorkItem workItem = super.getObject();
            if ((null != workItem) && (null != workItem.getCertificationItem()))
            {
                CertificationItem item = workItem.getCertificationItem(getContext());
                if ((null != item) && (CertificationItem.Type.Bundle.equals(item.getType())))
                {
                    // We're passing in null for the non-flattened entitlement
                    // mappings.  This is now loaded with an AJAX call.
                    this.businessRole =
                        new CertificationItemBean.BusinessRoleBean(this, item, item.getParent(),
                                                                   workItem.getId(), null);
                }
            }
        }

        return this.businessRole;
    }

    /**
     * If WorkItem is certification related, and the certification item is of type Exception, Account, DataOwner, 
     * or AccountGroupMembership, return the CertificationItemBean representing the certificationItem. 
     * 
     * @return UI bean used to hold information about exceptional entitlement certification.
     * @throws GeneralException
     */
    public CertificationItemBean.ExceptionBean getException() throws GeneralException
    {
        if (null == this.exception)
        {
            WorkItem workItem = super.getObject();
            if ((null != workItem) && (null != workItem.getCertificationItem()))
            {
                CertificationItem item = workItem.getCertificationItem(getContext());
                if ((null != item) && ( CertificationItem.Type.Exception.equals(item.getType()) ||
                                        CertificationItem.Type.Account.equals(item.getType()) ||
                                        CertificationItem.Type.DataOwner.equals(item.getType()) ||
                                      ( CertificationItem.Type.AccountGroupMembership.equals(item.getType()))) )
                {
                    CertificationEntity certId = item.getParent();

                    Map<String,Application> appMap =
                        new LoadingMap<String,Application>(new CertificationItemBean.ApplicationLoader());

                    this.exception =
                        new CertificationItemBean.ExceptionBean(this, item, certId, workItem.getId(), appMap);
                }
            }
        }

        return this.exception;
    }

    /**
     * If the workitem is certification related, and the certificationItem is a policy Violation, return the
     * ViolationBean representing the Policy Violation
     * 
     * @return UI bean used to hold information about PolicyViolations.
     * @throws GeneralException
     */
    public CertificationItemBean.ViolationBean getViolation() throws GeneralException
    {
        if (null == this.violation)
        {
            WorkItem workItem = super.getObject();
            if ((null != workItem) && (null != workItem.getCertificationItem()))
            {
                CertificationItem item = workItem.getCertificationItem(getContext());
                if ((null != item) && CertificationItem.Type.PolicyViolation.equals(item.getType()))
                {
                    this.violation =
                        new CertificationItemBean.ViolationBean(this, item, item.getParent(),
                                                                workItem.getId());
                }
            }
        }

        return this.violation;
    }

    /**
     * If the workitem is certification related, and the certificationItem is a BusinessRoleProfile, return the
     * ProfileBean representing the given profile.
     * 
     * @return UI bean which exposes the properties of a given profile under certification.
     * @throws GeneralException
     */
    public CertificationItemBean.ProfileBean getProfile() throws GeneralException
    {
        if (null == this.profile)
        {
            WorkItem workItem = super.getObject();
            if ((null != workItem) && (null != workItem.getCertificationItem()))
            {
                CertificationItem item = workItem.getCertificationItem(getContext());
                if ((null != item) && CertificationItem.Type.BusinessRoleProfile.equals(item.getType()))
                {
                    this.profile =
                        new CertificationItemBean.ProfileBean(this, item, item.getParent(),
                                                                workItem.getId());
                }
            }
        }

        return this.profile;
    }

    /**
     * For workitems related to certifications, with the certification item of type 
     * BusinessRoleHierarchy/BusinessRolePermit/BusinessRoleRequirement, return the GenericCertifiableBean
     * representing the certification item
     * 
     * @return GenericCertifiableBean representing a generic certifiable object
     * @throws GeneralException
     */
    public CertificationItemBean.GenericCertifiableBean getBusinessRoleRelationshipBean() throws GeneralException
    {
        if (null == this.businessRoleRelationshipBean)
        {
            WorkItem workItem = super.getObject();
            if ((null != workItem) && (null != workItem.getCertificationItem()))
            {
                CertificationItem item = workItem.getCertificationItem(getContext());
                if (CertificationItem.Type.BusinessRoleHierarchy.equals(item.getType()) ||
                    CertificationItem.Type.BusinessRolePermit.equals(item.getType()) ||
                    CertificationItem.Type.BusinessRoleRequirement.equals(item.getType())) {
                    this.businessRoleRelationshipBean =
                        new CertificationItemBean.GenericCertifiableBean(this, item, item.getParent(),
                                                                workItem.getId());
                }
            }
        }

        return this.businessRoleRelationshipBean;
    }

    /**
     * For workitems related to certifications, with the certification item of type
     * BusinessRoleGrantedCapability/BusinessRoleGrantedScope, return the GenericCertifiableBean
     * representing the certification item
     * 
     * @return GenericCertifiableBean representing a generic certifiable object
     * @throws GeneralException
     */
    public CertificationItemBean.GenericCertifiableBean getBusinessRoleGrantBean() throws GeneralException
    {

        if (businessRoleGrantBean == null){
            WorkItem workItem = super.getObject();
            if (workItem != null && null != workItem.getCertificationItem()){
                CertificationItem item = workItem.getCertificationItem(getContext());
                if (CertificationItem.Type.BusinessRoleGrantedCapability.equals(item.getType()) ||
                        CertificationItem.Type.BusinessRoleGrantedScope.equals(item.getType())){
                    businessRoleGrantBean = new CertificationItemBean.GenericCertifiableBean(this, item, item.getParent(),
                                                                                      workItem.getId());
                }
            }
        }

        return this.businessRoleGrantBean;
    }

    /**
     * 
     * @return Identity Bean that is built off the targetClass, targetName and targetId
     * properties of the workitem. This can be null depending on the what type
     * of certification we are dealing with. 
     * 
     * @throws GeneralException
     * 
     * @exclude Initially designed for use in lcm workitem
     * approval forms.
     */
    public IdentityDTO getTargetIdentityBean() throws GeneralException {
        if ( _targetIdentityBean == null ) {
            WorkItem item = getObject();
            if ( item != null ) {
                String targetClass = item.getTargetClass();
                // kludge, we normally use simple names but for awhile
                // at least LoginBean was using package qualifiers
                if (item.isTargetClass(Identity.class) ||
                    "sailpoint.object.Identity".equals(targetClass)) {

                    String targetName = item.getTargetName();
                    String targetId = item.getTargetId();
                    Identity id = null;
                    if ( targetId != null ) {
                        id = getContext().getObjectById(Identity.class, targetId);
                    } else
                    if ( targetName != null ) {
                        id = getContext().getObjectByName(Identity.class, targetName);
                    }
                    if ( id != null ) {
                        _targetIdentityBean = new IdentityDTO(id);
                    }
                }
            }
        }
        return _targetIdentityBean;
    }

    /**
    * This bean represents either a standard policy violation certification item, or a
    * PolicyViolation forwarded to the current user from the policy violation viewer.
    * As of 3.2 it is also used for work items created from the example policy workflows.
    * Here, the PolicyViolation will be inside the WorkItem. We could support
    * other conventions here, like "approvalObject" stored in the WorkflowCase
    * and not copied to the WorkItem which is more like how edit approvals work.
    *
    * @return view bean for the policy violation
    * @throws GeneralException
    */
    public ViolationViewBean getViolationViewBean() throws GeneralException
    {
        if (violationViewBean== null) {
            WorkItem item = getObject();
            PolicyViolation violation = null;
            if(item!=null) {
                // first check included violation
                Object o = item.getAttribute(Workflow.VAR_APPROVAL_OBJECT);
                if (o instanceof PolicyViolation) {
                    violation = (PolicyViolation)o;
                }
                else if (item.isTargetClass(PolicyViolation.class)) {
                    // this is the convention used by the policy management pages
                    violation = getContext().getObjectById(PolicyViolation.class,
                                                           item.getTargetId());
                }
                else {
                    // the original convention used for cert items
                    CertificationItem certItem = item.getCertificationItem(getContext());
                    if (certItem != null && CertificationItem.Type.PolicyViolation.equals(certItem.getType())){
                        violation = certItem.getPolicyViolation();
                    }
                }

                if (violation != null)
                    violationViewBean = new ViolationViewBean(getContext(), violation);
            }
        }

        return violationViewBean;
    }

    /**
     * Return the CertificationItemBean for this work item. This assumes that
     * getBusinessRole(), getException(), getProfile() or getViolation() has already been
     * called.
     */
    public CertificationItemBean getCertificationItemBean() {

        CertificationItemBean bean = this.businessRole;

        if (null == bean) {
            bean = this.exception;
        }
        if (null == bean) {
            bean = this.violation;
        }
        if (null == bean) {
            bean = this.profile;
        }
        if (null == bean){
            bean = this.businessRoleGrantBean;
        }
        if (null == bean){
            bean = this.businessRoleRelationshipBean;
        }

        if(null == bean){
            bean = this.businessRole;
        }

        return bean;
    }

    /**
     * For workitems related to certifications, this will return the Identity that the certification item is certifying
     * 
     * @return Identity that the certification item is certifying
     * @throws GeneralException
     */
    public IdentityDTO getIdentity() throws GeneralException
    {
        if (null == this.identity)
        {
            WorkItem workItem = super.getObject();
            if (null != workItem)
            {
                CertificationItem certItem = workItem.getCertificationItem(getContext());
                if (certItem != null){
                    Identity id = certItem.getIdentity(getContext());
                    if (id != null)
                        this.identity = new IdentityDTO(id);
                }
            }
        }
        return this.identity;
    }

    public void setSelectedWorkItemId(String workItem) {
        this.selectedWorkItemId = workItem;
    }

    public String getSelectedWorkItemId() {
        return this.selectedWorkItemId;
    }

    /**
     * For workitems related to certifications, return the JSF bean for the certification entity
     * 
     * @return CertificationEntityViewBean JSF bean for Certification Entities
     * @throws GeneralException
     */
    public CertificationEntityViewBean getCertificationEntityViewBean() throws GeneralException{

        if (certEntityViewBean == null){
            WorkItem workItem = this.getObject();
            if (workItem.getCertificationEntity() != null){
                certEntityViewBean =
                    new CertificationEntityViewBean(workItem.getCertificationEntity(), workItem.getId(), isEditable());
            } else if (workItem.getCertificationItem() != null){
                CertificationItem item =  workItem.getCertificationItem(getContext());
                certEntityViewBean =
                    new CertificationEntityViewBean(item, workItem.getId(), isEditable());
            }
        }
        return certEntityViewBean;
    }

    public String getPriority() {
        return this.priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }


    public List<SelectItem> getPrioritySelectItems() {
        return WorkItemUtil.getPrioritySelectItems(getLocale(), getUserTimeZone());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // AccountSelection
    //
    ////////////////////////////////////////////////////////////////////////////

    private Map<String,List<String>> accountSelectionReasonsByApp;

    private String bulkDecision;

    /**
     * @return a localized list map of reasons that account selections are being
     * required, keyed by application ID.
     */
    @SuppressWarnings("unchecked")
    public Map<String,List<String>> getAccountSelectionReasonsByApp()
        throws GeneralException {

        if (null == this.accountSelectionReasonsByApp) {
            this.accountSelectionReasonsByApp = new HashMap<String,List<String>>();

            List<AccountSelection> selections =
                (List<AccountSelection>) getObject().getAttribute("accounts");
            List<ExpansionItem> expansions =
                (List<ExpansionItem>) getObject().getAttribute("expansionItems");
            if ((null != selections) && (null != expansions)) {
                for (AccountSelection acctSel : selections) {
                    String appId = acctSel.getApplicationId();
                    Application app =
                        getContext().getObjectById(Application.class, appId);
                    if (null != app) {
                        String appName = app.getName();
                        for (ExpansionItem item : expansions) {
                            // Only display a reason if the nativeIdentity is
                            // null.  Otherwise we have already selected the
                            // account somehow, so this is not an interesting
                            // reason.
                            if (appName.equals(item.getApplication()) &&
                                Util.isNullOrEmpty(item.getNativeIdentity())) {
                                List<String> msgs = this.accountSelectionReasonsByApp.get(appId);
                                if (null == msgs) {
                                    msgs = new ArrayList<String>();
                                    this.accountSelectionReasonsByApp.put(appId, msgs);
                                }

                                msgs.add(getExpansionReason(item));
                            }
                        }
                    }
                }
            }
            else {
                // A legacy work item will not have expansion items.  In these
                // cases we'll just not return any information.
            }
        }

        return this.accountSelectionReasonsByApp;
    }

    /**
     * Return a localized reason string for the given expansion.
     */
    private String getExpansionReason(ExpansionItem item)
        throws GeneralException {

        String attr = item.getName();
        Object val = item.getValue();
        String source = item.getSourceInfo();
        String op = null;

        switch (item.getOperation()) {
        case Add:
        case Retain:
            op = getMessage(MessageKeys.OPERATION_ADDED); break;
        case Remove:
        case Revoke:
            op = getMessage(MessageKeys.OPERATION_REMOVED); break;
        case Set:
            op = getMessage(MessageKeys.OPERATION_SET); break;
        default:
            throw new GeneralException("Unknown operation: " + item.getOperation());
        }

        String msg = null;
        switch (item.getCause()) {
        case AttributeAssignment:
            msg = getMessage(MessageKeys.WORKITEM_ACCOUNT_SELECTION_REASON_ATTR_ASSIGNMENT, op, attr, val); break;
        case AttributeSync:
            msg = getMessage(MessageKeys.WORKITEM_ACCOUNT_SELECTION_REASON_ATTR_SYNC, source, op, attr, val); break;
        case ProvisioningPolicy:
            msg = getMessage(MessageKeys.WORKITEM_ACCOUNT_SELECTION_REASON_PROV_POLICY, source, op, attr, val); break;
        case Role:
            msg = getMessage(MessageKeys.WORKITEM_ACCOUNT_SELECTION_REASON_ROLE, source, op, attr, val); break;
        default:
            throw new GeneralException("Unknown cause: " + item.getCause());
        }

        return msg;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Approvals
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @return the name of the identity that is considered to be the
     * "requester" of this item. Older work items will have
     * this stored in the WorkItem.requester property. Newer
     * work items created by workflows processed in the background
     * will store the requester name in a variable.
     */
    public String getRequester() throws GeneralException {
        String requester = "???";
        WorkItem item = super.getObject();
        if (item != null) {
            Identity ident = item.getRequester();
            if (ident != null)
                requester = ident.getDisplayableName();
            else
                requester = item.getString(Workflow.VAR_LAUNCHER);
        }
        return requester;
    }

    /**
     * @return the new version of an object submitted for approval.
     */
    public SailPointObject getNewObject() throws GeneralException {
        if (_newObject == null) {
            NonIdentityRequestApprovalService niraService = new NonIdentityRequestApprovalService(this, getLocale());
            _newObject = niraService.getNewObject(getObject());
        }
        return _newObject;
    }

    /**
     * In the event the object implements sailpoint.object.Describable, this will return a localized description. 
     * Otherwise, return the description as it is on the object.
     * 
     * @return description of the object submitted for approval
     * @throws GeneralException
     */
    public String getObjectDescription() throws GeneralException {
        String description = null;
        SailPointObject newObject = getNewObject();
        if (newObject == null) {
            description = "";
        } else if (!(newObject instanceof Describable)) {
            log.warn("Work Items cannot fetch localized descriptions for SailPointObjects that unless they implement sailpoint.object.Describable.  The " + newObject.getClass().getName() + " class fails that criteria.");
            description = newObject.getDescription();
        } else {
            Locale userLocale = getLocale();
            description = ((Describable)newObject).getDescription(userLocale);
        }

        return description;
    }

    /**
     * Calculate the differences between the old and new versions
     * of a role. Originally we did diffing of both roles and profiles
     * but in 3.0 you can only have roles. Need to generalize this
     * to diff other kinds of objects.
     * 
     * @return the summary of bundle differences
     */
    public BundleDifference getRoleDifferences() throws GeneralException {
        NonIdentityRequestApprovalService niraService = new NonIdentityRequestApprovalService(this, getLocale());
        return niraService.getRoleDifference(getObject());
    }
    
    /**
     *
     * The old convention was to leave an "taskResultId" attribute
     * in the WorkItem, we will still recognize that but I am not sure
     * if it is still used, I think it was only for impact analysis.
     *
     * The newer convention is to look a the class and target fields
     * of the WorItem to see if it references a TaskResult.
     *
     * The fallback if this item has a WorkflowCase is to check
     * for the StandardWorkflowHandler convention of putting
     * the task id in VAR_IMPACT_ANALYSIS_RESULT. 
     * We need to think about making it set the WorkItem target.
     * 
     * @return the id of a TaskResult associated with this work item.
     */
    public String getTaskResultId() throws GeneralException {

        String id = null;
        WorkItem item = getObject();

        if ( item == null ) return id;

        // new way
        if (item.isTargetClass(TaskResult.class))
            id = item.getTargetId();

        // old way
        if (id == null)
            id = item.getString(WorkItem.ATT_TASK_RESULT);

        // kludge way
        if (id == null) {
            WorkflowCase wfcase = item.getWorkflowCase();
            if (wfcase != null)
                id = wfcase.getString(RoleLibrary.VAR_IMPACT_ANALYSIS_RESULT);
        }

        return id;
    }

    /**
     * Need a setter so it can be a hidden field if we post back here
     * but it cannot be changed so ignore it.
     */
    public void setTaskResultId(String s) {
    }

    /**
     * Formerly used to control whether to show a link to the
     * modeler to see the pending change. We now use
     * {@link #getHasPendingChange}.
     * 
     * @return workflowcase Id
     */
    public String getWorkflowCaseId() throws GeneralException {

        String id = null;
        if (getWorkflowCase()!=null)
            id = _workflowCase.getId();
        return id;
    }

    /**
     * 
     * @return WorkflowCase associated with the workitem
     * @throws GeneralException
     */
    public WorkflowCase getWorkflowCase() throws GeneralException {
        if(_workflowCase==null) {
            WorkItem item = getObject();
            _workflowCase = item.getWorkflowCase();
        }
        return _workflowCase;
    }

    /**
     * 
     * @return Attributes of the workflow in the workflowCase
     * @throws GeneralException
     */
    public Attributes<String,Object> getWorkflowAttributes() throws GeneralException{
        if(getWorkflowCase()!=null) {
            return _workflowCase.getWorkflow().getVariables();
        }
        return null;
    }

    /**
     * Do Nothing
     */
    public void setWorkflowCaseId(String id) {
    }

    /**
     * @return true if there are pending changes to review in the modeler.
     */
    public boolean getHasPendingChange() throws GeneralException {
        boolean pendingChange = false;
        WorkItem item = getObject();
        WorkflowCase wfcase = item.getWorkflowCase();
        if (wfcase != null) {
            pendingChange = !wfcase.isDeleteApproval();
        }

        return pendingChange;
    }


    /**
     * @return boolean true if user can view the pending changes in the modeler. only allow owner or system admin.
     */
    public boolean isCanViewPendingChanges() throws GeneralException {
        return getHasPendingChange() && (isOwnerOrIsInWorkgroup() || isSystemAdmin());
    }

    /**
     * Checks that the current user should have access to the workitem. The workitem
     * should be accessible by the owner, creator or creator.
     *
     * @return true if the current user has access to the workitem
     * @throws GeneralException
     */
    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException{

        return super.isAuthorized(new WorkItemAuthorizer((WorkItem)object));
    }

    @Override
    protected QueryOptions addScopeExtensions(QueryOptions ops){
         ops.setUnscopedGloballyAccessible(false);
        return ops;
    }

    /**
     * Uses the ApprovalItemGridHelper to fetch the grid store fields.
     * 
     * @return JSON representation of the fields for the store backing the ApprovalItemGrid
     * @throws GeneralException
     */
    public String getStoreFieldsJson() throws GeneralException {
        ApprovalItemGridHelper columnHelper = new ApprovalItemGridHelper( getApprovalSet(), getAttributes().get( "showOwner" ) != null  );
        return columnHelper.getStoreFieldsJson();
    }

    /**
     * Uses the ApprovalItemGridHelper to calculate the ColumnConfig for the approval item grid based on the approval set
     * and the work item properties. 
     * 
     * @return List of ColumnConfigs for the Approval item grid
     * @throws GeneralException
     */
    public List<ColumnConfig> getApprovalMemberColumns() throws GeneralException {
        if( approvalColumns == null ) {
            ApprovalItemGridHelper columnHelper = new ApprovalItemGridHelper( getApprovalSet(), getAttributes().get( "showOwner" ) != null  );
            
            // if its a batch approval and user doesn't have full access batch rights or not a sysadmin don't show link
            if (getApprovalSet().isBatchApproval() && 
                    (!getLoggedInUserRights().contains(SPRight.FullAccessBatchRequest) && !isSystemAdmin())) {
                ApprovalItemGridHelper.removeBatchItemsLink();
            }
            
            approvalColumns = columnHelper.getApprovalItemHeaders( isManualWorkItem() );
        }
        return approvalColumns;
    }

    /**
     * Sort on application by default
     * 
     * @return default column to sort on
     */
    public String getDefaultSortColumn() {
    	return ApprovalItemGridHelper.JSON_APPLICATION;
    }
    
    /**
     * Returns the JSON representation of the approvalMemberColumns. Used in approvalItemsBulkDecisionInclude as the
     * grid metaData source.
     * 
     * @return JSON representation of the approvalMemberColumns metadata
     * @throws GeneralException
     */
    public String getApprovalMemberColumnJSON() throws GeneralException {
    	return super.getColumnJSON(getDefaultSortColumn(), getApprovalMemberColumns());
    }
    
    private boolean isManualWorkItem() throws GeneralException {
        return getObject().getType().equals( WorkItem.Type.ManualAction );
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Policy Violation Workflow
    //
    // GAG! I hate how this class has evolved.  Would like to subclass this,
    // but then we've got the hard coded JSF backing bean problem.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * jsl - for the 3.2 policy violation workflows, this builds a list of
     * the possible actions as defined on the policy. We might extend this
     * with other custom actions.
     *
     * In theory we could try to make this purely data driven from the
     * CertificationActions but we still have the present/past tense
     * problem, CertificationAction keys are past tense (Approved, Revoked)
     * but we need to display present (Approve, Revoke).
     * Consider extending the CertificationAction model to have both keys.
     * 
     * @return list of the possible actions as defined on the policy
     */
    public List<SelectItem> getDefaultPolicyViolationActions()
        throws GeneralException {

        if (_defaultPolicyViolationActions == null) {
            List<SelectItem> items = new ArrayList<SelectItem>();

            // we're expecting the workflow convention of having the violation
            // inside the item...
            WorkItem witem = getObject();
            Object o = witem.getAttribute(Workflow.VAR_APPROVAL_OBJECT);
            if (o instanceof PolicyViolation) {
                PolicyViolation v = (PolicyViolation)o;
                Policy p = v.getPolicy(getContext());
                String actionSpec = p.getCertificationActions();
                if (actionSpec != null) {
                    List<String> actionNames = Util.csvToList(actionSpec);
                    if (actionNames != null) {
                        for (String name : actionNames) {
                            CertificationAction.Status action =
                                Enum.valueOf(CertificationAction.Status.class, name);
                            // always filter Delegated
                            if (action != null && action != CertificationAction.Status.Delegated) {
                                // ignore if we don't have a prompt key
                                String prompt = action.getPromptKey();
                                if (prompt != null) {
                                    String msg = super.getMessage(action.getPromptKey());
                                    SelectItem sitem = new SelectItem(name, msg);
                                    items.add(sitem);
                                }
                            }
                        }
                    }
                }
            }

            _defaultPolicyViolationActions = items;
        }
        return _defaultPolicyViolationActions;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTION HANDLING
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add a comment to this work item.
     */
    public String addComment() throws GeneralException
    {
        // don't bother if the comment is empty
        if ((this.newComment == null) || (this.newComment.trim().equals("")))
            return "";

        savePriority();

        // bug 24090 - adding boolean to allow requestor of work item to add a comment
        WorkItemService svc = new WorkItemService(this.getObjectId(), this, true);
        try {
            svc.addComment(this.newComment);
        }
        catch (EmailException e)  {
            Message msg = e.getMessageInstance();
            msg.setType(Message.Type.Warn);
            super.addMessageToSession(msg, null);
        }

        this.newComment = null;
        return "";
    }

    /**
     * Update this work item's priority
     * @return Blank string (intended to be called as an a4j action)
     */
    public String updatePriority() throws GeneralException {
        savePriority();
        getContext().commitTransaction();
        return "";
    }

    /**
     * Forward the work item to someone else.
     */
    public String forward() {

        try {
            // make sure that the cert is not locked
            if (checkIfLocked()) {
                throw new GeneralException(new Message(Message.Type.Warn, MessageKeys.CERT_LOCKED_FAIL_FORWARD));
            }

            // Check if we're forwarding a certification to someone that is a
            // member of the certification.  If so, this throws.
            //
            // It would be nice to be able to put this in
            // Certificationer.forwardWorkItem().  Unfortunately, this gets
            // called when certification work items are created, when they are
            // escalated, etc... and right now we only want to throw this error
            // when forwarding happens interactively.  See bug 2773.
            WorkItemService workItemService = new WorkItemService(getObject().getId(), this);

            workItemService.checkSelfCertificationForward(this.newOwner);
            workItemService.checkReassignmentLimit();

            savePriority();
            
            workItemService.forward(this.newOwner.getId(), this.forwardComment);
        }
        catch (EmailException e)  {
           Message msg = e.getMessageInstance();
           msg.setType(Message.Type.Warn);
           super.addMessageToSession(msg, null);
        }
        catch (SelfCertificationException e) {
            super.addMessageToSession(new Message(Message.Type.Error,
                                                           MessageKeys.ERR_CANNOT_SELF_CERTIFY_FORWARD,
                                                           e.getSelfCertifier().getDisplayableName()));
        }
        catch (LimitReassignmentException lre) {
            if (log.isErrorEnabled()) {
                log.error("Failed to reassign due to exceeding the amount of reassignments allowed: " + lre);
            }
            super.addMessageToSession(lre.getMessageInstance());
        }
        catch (GeneralException ge) {
            Message msg = ge.getMessageInstance();
            msg.setType(Message.Type.Warn);
            super.addMessageToSession(msg, null);
        }

        clearHttpSession();
        return getNextPage();
    }

    private boolean checkIfLocked() throws GeneralException {

        boolean locked = false;
        WorkItem item = getObject();
        if (item.getType() == WorkItem.Type.Certification) {
            if (ObjectUtil.isLockedById(getContext(), Certification.class, item.getCertification())) {
                locked = true;
            }
        }
        return locked;
    }

    /**
     * Common handler to process a modified work item.
     * Originally we just called SailPointContext.saveObject
     * which caused side effects depending on the work item type.
     * Now we must use the Workflower object to fire the side effects.
     */
    private boolean processWorkItem(WorkItem item) {

        boolean success = true;
        try {
            SailPointContext con = getContext();
            Workflower wf = new Workflower(con);
            wf.process(item, true);

            // For approval items,  a rejection may delete objects,
            // which in turn may require that the modeler tree
            // be rebuilt.   Currently we're rebuilding it on ever
            // refresh but if we start caching will need to invalidate
            // it here. - jsl
        }
        catch (ValidationException ve) {
            // just display what the exception has without any extra wrapping
            Message m = ve.getMessageInstance();
            addMessage(m);
            success = false;
        }
        catch (Throwable t) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t), null);
            success = false;

            // don't let uncommitted status persist on the HttpSession
            // is this really necessary? probably not since
            // it will be set on every post
            item.setState(null);
        }

        return success;
    }

    /**
     * Newer version of the processWorkItem method that uses WorkflowSession.
     * It uses the session to help control transitions back to the workitem
     * page when there is more then one item for a person related to a
     * specific workflow.
     */
    private String processWorkItemWithSession(WorkItem item, String currentTransition) {
        String transition = currentTransition;
        try {
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(getSessionScope());

            // Try to use the session that we have if there is one, otherwise
            // we create a new one.
            WorkflowSession session = wfUtil.getWorkflowSession(item);
            if (null == session) {
            	item = ObjectUtil.reattach(getContext(), item);
                session = new WorkflowSession(item);
            }
            else {
                // Sanity check - the session we're dealing with should use this
                // work item.
                if (!item.equals(session.getWorkItem())) {
                    throw new GeneralException("WorkItem doesn't match session: " + item + " - " + session);
                }

                // Set the workitem on the session since this may have some
                // local changes.
                session.setWorkItem(item);
            }

            // Push the session along, this may throw a validation exception.
            session.advance(getContext());

            // If we're staying on this page clear some stuff.
            // should we be doing this for other stuff that stays on this page?
            // probably...  maybe we should always clear?
            if ( session.hasWorkItem() ) {
                super.clearHttpSession();
                super.setObject(null);
                super.setObjectId(null);
            }

            // Get the next page and setup the session as needed.  This won't
            // look in the navigation history b/c that has already been done.
            transition = wfUtil.getNextPage(session, false, this, currentTransition, false);
        } catch (ValidationException ve) {
            // just display what the exception has without any extra wrapping
            Message m = ve.getMessageInstance();
            addMessage(m);
            transition = null;
        }
        catch (Throwable t) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t), null);
            // don't let uncommitted status persist on the HttpSession
            // is this really necessary? probably not since
            // it will be set on every post
            item.setState(null);
            transition = null;
        }
        return transition;
    }

    /**
     * Return the work item to requester because you will not deal with it.
     *
     * jsl - we are overloading a single Reject button for two things.
     *
     * For certification work items reject is actually a Return
     * in the WorkItem model, which is  an indication that the user
     * has elected not to do anything with the work item. The changes
     * made to the certification model are rolled back.
     *
     * For approval work items the concepts of "reject" and "return"
     * are different. Return means "no opinion, give it to someone else"
     * and "reject" means "I don't approve".
     *
     * I do not really like the overload but since Reject and Return
     * effectively mean the same thing for certifications, labeling it as "Reject"
     * makes the most sense.
     */
    public String reject() throws GeneralException
    {
        String transition = getNextPage();
        clearHttpSession();
        WorkItem item = super.getObject();
        savePriority();

        // jsl - commented this out since some WorkItems won't
        // have requesters, and there isn't anything we can do
        // about it here anyway
        //Identity requester = item.getRequester();
        //if (null == requester)
        //throw new GeneralException("Cannot return a work item without a requester.");

        item.setCompletionComments(this.newComment);

        if (item.getType() == WorkItem.Type.Approval ||
            item.getType() == WorkItem.Type.ImpactAnalysis)
            item.setState(WorkItem.State.Rejected);
        else
            item.setState(WorkItem.State.Returned);

        if (!processWorkItem(item))
            transition = null;

        return transition;
    }

    /**
     * Save the payload of the work item. This currently only does something
     * for certification delegations and priority changes.
     */
    public String save() throws GeneralException {

        boolean success = saveInternal(false);
        //Don't wipe out our valuable workitem approval state if we do not save successfully
        if(success) { 
            clearHttpSession();
        }
        return (success) ? getNextPage() : null;
    }
    
    /**
     * Return whether the given argument is a special FormHandler arg that
     * should not be saved in the WorkItem attributes.
     */
    private boolean isSpecialArg(String argName) {
        return "identity".equals(argName) || "workItem".equals(argName);
    }
    
    /**
     * Copy any values from the given arguments map that the FormHandler
     * reads and writes from/to into the WorkItem.  The argument map is
     * bi-directional now, so changes in this map need to stick on the
     * work item.  Scrub out any special arguments so we don't get a bunch
     * of junk in the work item.
     */
    private void copyArgsToWorkItem(Map<String,Object> args) {
        if (null != args) {
            Attributes<String,Object> dest = getObjectSafe().getAttributes();

            for (Map.Entry<String,Object> entry : args.entrySet()) {
                if (!isSpecialArg(entry.getKey())) {
                    if (null == dest) {
                        dest = new Attributes<String,Object>();
                        getObjectSafe().setAttributes(dest);
                    }
                    dest.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
    
    private void saveSession() {

        if (_session != null) {
            WorkflowSessionWebUtil wfUtil =
                new WorkflowSessionWebUtil(getSessionScope());
            wfUtil.saveWorkflowSession(_session);
        }
    }

    /**
     * Save the payload of the work item. This currently only does something
     * for certification delegations.
     */
    public String refresh() throws GeneralException {

        if (getBusinessRole() != null)
            getBusinessRole().refresh();
        if (getException() != null)
            getException().refresh();
        if (getViolation() != null)
            getViolation().refresh();
        //bug 19395 - re-populates the form postBack
        if (getFormBean() !=null){
            FormRenderer formBean = getFormBean();
            if(log.isDebugEnabled()) {
                log.debug("Pre-Refresh: " + formBean.getForm().toXml());
            }
            FormHandler handler = new FormHandler(getContext(), this);
            Map<String,Object> args = getFormArguments();
            handler.refresh(retrieveMasterForm(), formBean, args);
            copyArgsToWorkItem(args);
            saveSession();
            if(log.isDebugEnabled()) {
                log.debug("Post-Refresh: " + formBean.getForm().toXml());
            }
        }
        updateDecisions();

        return "";
    }

    /**
     * 
     * @return WorkItem type
     * @throws GeneralException
     */
    public String getType()
        throws GeneralException
    {
        WorkItem item = getObject();

        if (item == null) {
            return null;
        }

        return item.getType().name();
    }

    /**
     * Called by JSF, no-op.
     */
    public void setType(String type) { }

    /**
     * Determines whether the give workitem id is for an active workitem
     * or one that has been archived. If the given id is null, or if a workitem
     * (either active or archived) cannot be found, return "unknown."
     *
     * @return Status of the workitem with the given id
     * @throws GeneralException
     */
    public String getStatus() throws GeneralException {
        String id = super.getRequestParameter(ATT_ITEM_ID);
        if (id == null) {
            if (log.isWarnEnabled())
                log.warn("No id provided for workitem status check");

            return WORKITEM_STATUS_UNKNOWN;
        }

        WorkItem item = getContext().getObjectById(WorkItem.class, id);
        if (item != null)
            return WORKITEM_STATUS_ACTIVE;

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("workItemId", id));
        Iterator<Object[]> it = getContext().search(WorkItemArchive.class, qo, "id");
        if (it.hasNext())
            return WORKITEM_STATUS_ARCHIVED;
        else {
            if (log.isWarnEnabled())
                log.warn("Unable to find a workitem with the given id");

            return WORKITEM_STATUS_UNKNOWN;
        }
    }

    /**
     * Return whether the workItem is completed. This will ensure the workitem is fully complete 
     * {@link #isFullyCompleted(AbstractCertificationItem)} 
     * and will not add warning messages to the page
     * 
     * @return true if the workitem is complete
     * @throws GeneralException
     */
    public boolean isComplete()
        throws GeneralException
    {
        return verifyComplete(getObject(), false, true);
    }

    /**
     * Called by JSF, no-op.
     */
    public void setComplete(boolean complete) { }

    /**
     * True if:
     * 1) the work item is a delegation and the system config REQUIRE_DELEGATION_COMPLETION is true
     * or
     * 2) the work item is a remediation
     * 
     * @return True if the workitem requires completion 
     * @throws GeneralException
     */
    public boolean getRequiresCompletion()
        throws GeneralException
    {
        WorkItem item = getObject();
        if (item == null)
            return false;

        Configuration sysConfig = getContext().getConfiguration();

        if (sysConfig.getBoolean(Configuration.REQUIRE_DELEGATION_COMPLETION) &&
            WorkItem.Type.Delegation.equals(item.getType())) {
            return true;
        }

        if (WorkItem.Type.Remediation.equals(item.getType())) {
            return true;
        }

        return false;
    }

    /**
     * Called by JSF, no-op.
     */
    public void setRequiresCompletion(boolean requiresCompletion) { }

    public int getRemediationCount()
        throws GeneralException
    {
        if (getObject() == null)
            return 0;
        else
        {
            List<RemediationItem> items = getObject().getRemediationItems();
            return (items == null) ? 0 : items.size();
        }
    }

    /**
     * Called by JSF, no-op.
     */
    public void setRemediationCount(int count) { }

    public void setDecisions( String decisionJson ) {
        this.decisionJson = decisionJson;
    }
    public String getDecisions() {
        return decisionJson;
    }

    public String getBulkDecision() {
        return bulkDecision;
    }

    public void setBulkDecision( String bulkDecision ) {
        this.bulkDecision = bulkDecision;
    }

    private boolean saveInternal(boolean isCompleted) throws GeneralException {
        savePriority();

        saveCertification();
        // If not a certification
        if ( ( this.getCertificationItemBean() == null )  &&
             ( this.getCertification() == null ) ) {
            FormRenderer formBean = getFormBean();
            if ( formBean != null && formBean.getData() != null ) {
                // assimilate the posted form data
                formBean.populateForm();

                if ( !formBean.validate(getFormArguments()) ) {
                    return false;
                }
                
                //Process the form data one last time to ensure we get user-posted form data
                //back into the workflow.
                if(log.isDebugEnabled()) {
                    if(formBean.getForm() != null) {
                        log.debug("Pre-Refresh: " + formBean.getForm().toXml());
                    }
                }
                FormHandler handler = new FormHandler(getContext(), this);
                Map<String,Object> args = getFormArguments();
                handler.refresh(retrieveMasterForm(), formBean, args);
                copyArgsToWorkItem(args);
                saveSession();
                if(log.isDebugEnabled()) {
                    if(formBean.getForm() != null) {
                        log.debug("Post-Refresh: " + formBean.getForm().toXml());
                    }
                }
            }
            WorkItem item = getObject();
            if ( item != null ) {
                updateDecisions();
                if (isCompleted) {
                    try {
                        item.setCompleter(getLoggedInUser().getDisplayName());
                        getNotary().sign(item, getNativeAuthId(), getSignaturePass());

                        // If username was passed in, store it for future use.
                        String sai = getSignatureAuthId();
                        String oai = getOriginalAuthId();
                        if(sai != null && !sai.equals("") && (oai == null || oai.equals(""))) {
                            getSessionScope().put(LoginBean.ORIGINAL_AUTH_ID, sai);
                        }
                    } catch (GeneralException | ExpiredPasswordException e) {
                        log.warn(e);
                        this.addMessage(new Message(Message.Type.Error, MessageKeys.ESIG_POPUP_AUTH_FAILURE), new Message(e.getLocalizedMessage()));

                        return false;
                    } finally {
                        setSignaturePass(null); // Don't store the password!
                        setSignatureAuthId(null);
                    }
                }

                // save off the workitem in case there are changes to the ApprovalSet or priority
                getContext().saveObject(item);
                getContext().commitTransaction();
            }
        }
        return true;
    }
    
    /**
     * Saves the decisions if there are any so that but does not 
     * process the decisions.
     *
     * @throws GeneralException
     */
    
    private void updateDecisions() throws GeneralException{
        WorkItem item = getObject();
        if ( item != null ) { 
            ApprovalSetDTO setDTO = getApprovalSet();
            if ( setDTO != null ) {
                if(item.getType().equals(WorkItem.Type.ViolationReview)) {
                    updateApprovalState(setDTO.getObject());
                }
                ApprovalSet set = setDTO.getObject();
                if (set != null) {
                    saveDecisions( decisionJson, set.getItems() );
                    Map<String,Object> attrs = item.getAttributes();
                    attrs.put(WorkItem.ATT_APPROVAL_SET, set);
                }
            }
        }
    }

    private void updateApprovalState(ApprovalSet set) throws GeneralException {
        List<HashMap> decisions = deserializeDecisions(decisionJson);
        for(ApprovalItem approvalItem: set.getItems()) {
            WorkItem.State state = WorkItem.State.Finished;
            for( HashMap decision : decisions ) {
                if(approvalItem.getId().equals(decision.get("approvalItemId"))) {
                    Object decisionValue = decision.get("decision");
                    if(decisionValue != null && decisionValue.equals("Rejected")) {
                        state = WorkItem.State.Rejected;
                        break;
                    }
                }
            }
            approvalItem.setState(state);
        }
    }

    private void saveDecisions( String decisionsJSON, List<ApprovalItem> approvalItems) throws GeneralException {
        List<HashMap> decisions = deserializeDecisions( decisionsJSON );
        if( hasDecisions( decisions, getBulkDecision() ) ){
            for( ApprovalItem approvalItem : approvalItems ) {
                boolean processed = false;
                for( HashMap decision : decisions ) {
                    if( approvalItem.getId().equals( decision.get( "approvalItemId" ) ) ) {
                        processDecision( approvalItem, (String)decision.get( "decision" ) );
                        processed = true;
                        break;
                    }
                }
                if( !processed && hasBulkDecision() ) {
                    processDecision( approvalItem, bulkDecision );
                    processed = true;
                }
            }
        }
    }

    private boolean hasBulkDecision() {
        return bulkDecision != null && !bulkDecision.trim().equals( "" ) && !bulkDecision.equals( "undefined" );
    }

    private void processDecision( ApprovalItem approvalItem, String decision ) {
        if( decision != null ) {
            if( decision.equals( "Finished" ) ) {
                approvalItem.approve();
            } else if( decision.equals( "Rejected" ) ){
                approvalItem.reject();
            }
        }
    }

    private boolean hasDecisions( List<HashMap> decisions, String bulkDecision ) {
        return !decisions.isEmpty() || hasBulkDecision();
    }

    private List<HashMap> deserializeDecisions( String decisionsJSON ) throws GeneralException {
        if (isDecisionEmpty( decisionsJSON )) {
            return new ArrayList<HashMap>();
        }
        List<HashMap> decisions = JsonHelper.listFromJson(HashMap.class, decisionsJSON );
        return decisions;
    }

    private boolean isDecisionEmpty( String decisionsJSON ) {
        return decisionsJSON == null || decisionsJSON.equals("[]") ||  decisionsJSON.trim().equals( "" );
    }

    private List<ApprovalItem> getApprovalItems() throws GeneralException {
        Map<String,Object> attributes = getObject().getAttributes();
        List<ApprovalItem> approvalItems = Collections.emptyList();
        if ( attributes != null ) {
            ApprovalSet set = (ApprovalSet)attributes.get(WorkItem.ATT_APPROVAL_SET);
            if (set != null) {
                approvalItems = set.getItems();
            }
        }
        return approvalItems;
    }

    private ApprovalItem getApprovalItem(String approvalItemId) throws GeneralException {
        List<ApprovalItem> items = getApprovalItems();
        for (ApprovalItem item : items) {
            if (approvalItemId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    private void savePriority() throws GeneralException {
        WorkItem item = getObject();
        if (item != null) {
            WorkItem.Level level;

            if (priority != null) {
                try {
                    level = Enum.valueOf(WorkItem.Level.class, priority);
                } catch (Exception e) {
                    level = WorkItem.Level.Normal;
                }
            } else {
                level = WorkItem.Level.Normal;
            }

            item.setLevel(level);
        }
    }

    private void saveCertification() throws GeneralException {

        CertificationItemBean item = this.getCertificationItemBean();
        if (null != item) {
            item.saveIfChanged(this.getObject().getId());
            Certification cert = this.getObject().getCertification(getContext());
            CertificationBean.saveAndRefreshCertification(cert, getContext(), this);
        }
        else if (null != this.getCertification()) {
            this.getCertification().save();
        }
    }

    /**
     * Mark a work item as complete. If this is a work item for a single
     * remediation, complete the remediation with the given comment, too.
     *
     * For approval work items, this is also the handler for the
     * "Approve" button.
     */
    public String complete() throws GeneralException
    {
        WorkItem item = super.getObject();
        // Check again that the item that we are completing is still accessible
        if (isReadOnly()) {
            throw new GeneralException("This workitem: + " + item.getId() + " is read-only and " +
                        "cannot be completed by the logged in user: " + getLoggedInUser().getId());
        }
        savePriority();

        setCompletionUser(item);

        // Save the contents of the work item if we need to.
        boolean success = this.saveInternal(true);
        if (!success)
           return null;

        // Now that the item is saved, verify that it is complete before saving
        // this stuff.  Return null immediately if this isn't complete so we
        // won't save the completion state and will stay on this page.
        if (!verifyComplete(item, true, false)) {
            return null;
        }
        
        item.setCompletionComments(this.newComment);
        item.setState(WorkItem.State.Finished);

        if (shouldSendDelegationFinishedEmail(item)) {
            sendDelegationFinishedEmail(item);
        }

        // Make sure that the nav history doesn't point back to the current page
        // see bug#5145.
        String transition = getNextPage();
        if (getNavigationString().equals(transition))
            transition = getNextPage();

        return processWorkItemWithSession(item, transition);
    }

    private boolean shouldSendDelegationFinishedEmail(WorkItem item) throws GeneralException {

        if (!WorkItem.Type.Delegation.equals(item.getType())) {
            return false;
        }
        Certification cert = getContext().getObjectById(Certification.class, item.getCertification());
        if (cert == null) {
            return false;
        }
        CertificationDefinition definition = cert.getCertificationDefinition(getContext());
        if (definition == null) {
            return false;
        }
        if (definition.isSendPreDelegationCompletionEmails()) {
            return true;
        }

        //Can be either entity or item, but not both
        AbstractCertificationItem certItem = null;
        if (item.getCertificationEntity() != null) {
            certItem = item.getCertificationEntity(getContext());
        } else if (item.getCertificationItem() != null){
            certItem = item.getCertificationItem(getContext());
        }
        if (certItem == null) {
            return false;
        }

        CertificationDelegation delegation =  certItem.getDelegation();
        if (delegation == null){
            return false;
        }

        return true;
    }

    private void sendDelegationFinishedEmail(WorkItem item) throws GeneralException {

        EmailTemplate template =
            ObjectUtil.getSysConfigEmailTemplate(getContext(), Configuration.DELEGATION_FINISHED_EMAIL_TEMPLATE);
        if (template == null) {
            log.warn("Could not find email template for: " + Configuration.DELEGATION_FINISHED_EMAIL_TEMPLATE);
            return;
        }

        Certification cert = getContext().getObjectById(Certification.class, item.getCertification());
        List<String> certifierNames = cert.getCertifiers();
        //Using a HashSet to avoid duplicated emails
        Set<String> ownerEmails = new HashSet<String>();
        for (String certifierName : certifierNames) {
            Identity certifier = getContext().getObjectByName(Identity.class, certifierName);
            List<String> effectiveEmails = ObjectUtil.getEffectiveEmails(getContext(), certifier);
            if (!Util.isEmpty(effectiveEmails)) {
                ownerEmails.addAll(effectiveEmails);
            } else {
                    log.warn("Work item owner (" + certifier.getName() + ") has no email. " +
                             "Could not send delegation notification.");
            }
        }

        //IIQETN-4943 :- if a work item is of type delegation hence we are going to send a
        //notification to the requester (delegator)
        if (item.getType() == Type.Delegation && item.getRequester() != null) {
            List<String> effectiveEmails = ObjectUtil.getEffectiveEmails(getContext(), item.getRequester());
            if (!Util.isEmpty(effectiveEmails)) {
                ownerEmails.addAll(effectiveEmails);
            }
        }

        if (ownerEmails.size() == 0) {
            log.warn("Could not find emails for any owners. " +
                     "Could not send delegation notification.");
            return;
        }

        //Sending an individual email to each member of the list
        for (String mail: ownerEmails) {
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("workItemName", item.getDescription());
            args.put("certification", cert);
            args.put("delegateeName", item.getOwner().getDisplayableName());
            EmailOptions ops = new EmailOptions(mail, args);

            getContext().sendEmailNotification(template, ops);
        }
    }


    /**
     * Set the completion user on the delegation or remedation. Challenge is handled in ChallengeItemBean.
     * Also set the comment on the remediation.
     * @param item
     * @throws GeneralException
     */
    private void setCompletionUser(WorkItem item) throws GeneralException {
        // Get the logged in user
        String completionUserName = getContext().getUserName();
        Identity completionUser = getContext().getObjectByName(Identity.class, completionUserName);
        CertificationItem certItem = item.getCertificationItem(getContext());

        // If this is a work item for a single remediation, mark the remediation
        // as complete with the completion comments and completion user.
        if (WorkItem.Type.Remediation.equals(item.getType())) {
            List<RemediationItem> items = item.getRemediationItems();
            if ((null != items) && (1 == items.size())) {

                // Also set the comment
                // !! jsl - this could be donein the work item handler?
                items.get(0).complete(this.newComment);

                // When the remediationItem is assimilated, the CertificationAction
                // will use the RemediationItem owner as the completionuser on the certaction.
                items.get(0).setOwner(completionUser);
            }
        }

        else if (WorkItem.Type.Delegation.equals(item.getType())) {
            CertificationEntity certEntity = item.getCertificationEntity(getContext());
            CertificationDelegation delegation = null;

            if (certItem != null) {
                delegation = certItem.getDelegation();
            } else if (certEntity != null) {
                delegation = certEntity.getDelegation();
            }

            if (delegation != null) {
                delegation.setCompletionUser(completionUserName);
            }
        } else if(WorkItem.Type.Approval.equals(item.getType())) {
            ApprovalSetDTO setDTO = getApprovalSet();
            if(setDTO!=null) {
                List<ApprovalItemDTO> items = setDTO.getItems();
                if(items!=null && !items.isEmpty()) {
                    for(ApprovalItemDTO approvalItem : items) {
                        approvalItem.getObject().setApprover(completionUser.getName());
                    }
                }
            }
        }
    }

    /**
     * Verify work item is complete
     *
     * 1) If this is a certification delegation work item, check if the item(s)
     * that were delegated are all finished (if delegation completion is
     * required).
     * 2) If this is a remediation work item, check that all remediation items
     * are marked as complete.
     * 3) Check if any of the approval items have expired sunset dates.
     *
     * If not complete, this returns false and adds a warning to the page.
     */
    private boolean verifyComplete(WorkItem item, boolean showMessages, boolean ignoreSysConfig)
        throws GeneralException
    {

        boolean isComplete = true;

        // If this is a delegation and delegation completion is required, check
        // if the delegation is done.
        Configuration sysConfig = getContext().getConfiguration();
        if ((ignoreSysConfig || sysConfig.getBoolean(Configuration.REQUIRE_DELEGATION_COMPLETION)) &&
            WorkItem.Type.Delegation.equals(item.getType())) {

            // Get either the CertificationItem or CertificationEntity.
            AbstractCertificationItem certItem = null;
            if (null != item.getCertificationItem()) {
                certItem = item.getCertificationItem(getContext());
            }
            else {
                certItem = item.getCertificationEntity(getContext());
            }

            // If not complete, add a message and set isComplete to false.
            if ((null != certItem) && !isFullyCompleted(certItem)) {
                isComplete = false;

                if (showMessages) {
                    super.addMessage(new Message(Message.Type.Warn, MessageKeys.ERR_DELEGATION_NOT_COMPLETE));
                }
            }
        }

        // If this is a remediation, check that all remediation items are complete
        if (WorkItem.Type.Remediation.equals(item.getType())) {
            List<RemediationItem> remItems = item.getRemediationItems();
            for (RemediationItem remItem : remItems) {
                if (!remItem.isComplete()) {
                    isComplete = false;

                    if (showMessages) {
                        super.addMessage(new Message(Message.Type.Warn, MessageKeys.ERR_REMEDIATION_NOT_COMPLETE));
                    }

                    break;
                }
            }
        }

        // bug#22063 - check if any of the approval items have expired sunset dates
        ApprovalSet appset = item.getApprovalSet();
        if (appset != null) {
            for ( ApprovalItem approvalItem : Util.iterate(appset.getItems())) {
                Date sunsetDate = approvalItem.getEndDate(); 
                Date now = DateUtil.getCurrentDate();
                if (sunsetDate != null && sunsetDate.compareTo(now) <= 0) {
                    isComplete = false;
                    if (showMessages) {
                        super.addMessage(new Message(Message.Type.Warn, MessageKeys.ERR_WORK_ITEM_SUNSET_EXPIRED));
                    }
                    break;
                }
            }
        }

        return isComplete;
    }

    /**
     * Check if the given item is complete. This looks for non-null decisions
     * rather than the isComplete(). Since delegated items are not considered
     * complete, isComplete() will always return false here.
     */
    private boolean isFullyCompleted(AbstractCertificationItem item)
        throws GeneralException {

        // This is a leaf - CertificationItem - check for a decision.
        if (isLeaf(item)) {
            if (!actionHasDecision(item.getAction())) {
                return false;
            }
        }
        else {
            // Not a leaf - recurse.
            for (CertificationItem subItem : item.getItems()) {
                if (!isFullyCompleted(subItem)) {
                    return false;
                }
            }
        }

        // If the item/entity has all decisions made, run the completion rule
        // (if there is one) for the final say as to whether this is done.
        Certificationer certificationer = new Certificationer(getContext());
        return certificationer.isCompletePerCompletionRule(item);
    }

    /**
     * Gets whether or not the certification item is a leaf.
     * @param item The item to check.
     * @return True if the certification item is a leaf (no children), false otherwise.
     */
    private boolean isLeaf(AbstractCertificationItem item)
    {
        return (item.getItems() == null) || item.getItems().isEmpty();
    }

    /**
     * Gets whether or not a CertificationAction has a decision.
     * @param action The action to check.
     * @return True if the action has a decision that is not Cleared.
     *         If action is null, false is returned.
     */
    private boolean actionHasDecision(CertificationAction action)
    {
        if (action == null) {
            return false;
        }

        return action.getStatus() != null && !action.getStatus().equals(CertificationAction.Status.Cleared);
    }

    /**
     * Mark a work item as returned. This is currently used only
     * for approval work items and it indicates that the user has
     * no opinion and that no action was taken.
     */
    public String returnAction() throws GeneralException
    {
        String transition = getNextPage();
        clearHttpSession();
        WorkItem item = super.getObject();

        item.setCompletionComments(this.newComment);
        item.setState(WorkItem.State.Returned);

        if (!processWorkItem(item))
            transition = null;

        return transition;
    }

    /**
     * Action to view a different work item navigating from this work item.
     * This saves state in the history.
     */
    public String viewWorkItem() throws GeneralException {
        NavigationHistory.getInstance().saveHistory(this);
        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(this.selectedWorkItemId, true /* check archive */, super.getSessionScope());
    }

    /**
     * Cancel from viewing a work item.
     */
    public String cancel() {
        clearHttpSession();
        return getNextPage();
    }

    public String cancelRequest() throws GeneralException {
        WorkItem workItem = getObject();
        
        WorkItemService service = new WorkItemService(workItem.getId(), this);
        service.deleteWorkItem();
        clearHttpSession();
        return getNextPage();
    }

    /**
     * Get the next page, either from the history or default to the work items
     * list.
     */
    String getNextPage() {

        // Try this one first.
        if (null != this.nextForwardPage) {
            String fwd = this.nextForwardPage;

            // Allow tacking parameters onto the next forward page.  These are
            // URL-style parameters that start with a ? and are separated by
            // ampersands and equals signs.  This get peeled off and stuffed
            // into the session.
            int idx = this.nextForwardPage.indexOf('?');
            if (idx > 0) {
                fwd = this.nextForwardPage.substring(0, idx);
                String params = this.nextForwardPage.substring(idx+1);
                String[] pairs = params.split("&");
                for (String pair : pairs) {
                    String[] vals = pair.split("=");
                    String key = vals[0];
                    String val = vals[1];
                    super.getSessionScope().put(key, val);
                }
            }

            return fwd;
        }


        String prev = NavigationHistory.getInstance().back();
        return (null != prev) ? prev : getDefaultNextPage();
    }

    /**
     * JSON representatino of the grid metadata for the approval Items grids.
     * Takes request parameters:
     * "page" - The page to display if grid is multi paged
     * "limit" - The number of rows to fetch
     * "sort" - column to sort on
     * "dir" - direction to sort
     * "decision" - work item state. Takes values all/rejected/approved/decided. If null, default to all
     * 
     * 
     * @return JSON reprensentation of the meta data for approval items grid
     * @throws GeneralException
     */
    public String getMembersGridJson() throws GeneralException {
        ApprovalSetDTO approvalSet = getApprovalSet();
        if (approvalSet != null) {
            int page = Integer.parseInt( ( String ) getRequestParam().get( "page" ) );
            int limit = getResultLimit();
            int start = ( page - 1 ) * limit;
            int end = start + limit;
            String sortColumn = ( String ) getRequestParam().get( "sort" );
            boolean ascending = "ASC".equals( ( String ) getRequestParam().get( "dir" ) );
            String filter = ( String ) getRequestParam().get( "decision" );
            if( filter == null ) {
                filter = "all";
            }

            return approvalSet.getMembersGridJson( start, end, sortColumn, ascending, filter );
        } else {
            return ApprovalSetDTO.getEmptyMembersGridJson();
        }
    }

    String getDefaultNextPage() {
        return "viewWorkItems";
    }

    /**
     * An action listener method that saves the page in the navigation history.
     *
     * @param  evt  The JSF action event.
     */
    public void saveNavigationHistoryActionListener(ActionEvent evt) {
        NavigationHistory.getInstance().saveHistory(this);
    }

    /**
     * Transition to the identity request associated with this item.
     */
    public String viewAccessRequest() throws GeneralException {
        NavigationHistory.getInstance().saveHistory(this);
        WorkItem item = getObject();
        if (null == item.getIdentityRequestId()) {
            throw new GeneralException("No request ID for work item: " + item);
        }
        return "viewAccessRequest#/request/" + item.getIdentityRequestId();
    }

    /**
     * Transition to a task result.
     */
    public String viewTaskResultAction() throws GeneralException {

        String transition = null;
        WorkItem item = super.getObject();

        String resultId = getTaskResultId();
        if (resultId != null) {

            TaskResult result = getContext().getObjectById(TaskResult.class, resultId);
            if (result.getReport() != null && result.getReport().getFiles() != null
                    && !result.getReport().getFiles().isEmpty()){
                    return "viewReport?id=" + result.getId() + "&w="+item.getId();
            } else {
                // should we do the verification here?
                // it sure would be nice if we could avoid using the HttpSession
                // to pass this, but there are the <redirect/> issues
                Map session = getSessionScope();
                session.put(TaskResultBean.ATT_RESULT_ID, resultId);
                session.put(TaskResultBean.ATT_WORK_ITEM_ID, item.getId());
                transition = "taskResult";
            }
        }

        return transition;
    }

    /**
     * Transition to a page capable of viewing/editing the
     * object being approved.
     *
     * Currently we will only be here for roles but should try
     * to make this general.
     */
    public String viewPendingChangeAction() throws GeneralException {

        String transition = null;
        WorkItem item = super.getObject();
        savePriority();

        WorkflowCase wfcase = item.getWorkflowCase();
        if (wfcase != null) {
            SailPointObject obj = wfcase.getApprovalObject();
            if (obj == null) {
                // odd, not an approval workflow
                log.error("No approval object in workflow");
            }
            else if (!(obj instanceof Bundle)) {
                // odd, not an approval workflow
                log.error("Unable to view object of class: " +
                          obj.getClass().getSimpleName());
            }
            else {
                Map session = getSessionScope();
                session.put(RoleEditorBean.ATT_WORK_ITEM_ID, item.getId());
                session.put(RoleEditorBean.ATT_WORKFLOW_CASE_ID, wfcase.getId());
                session.put(RoleEditorBean.ATT_ROLE_TO_EDIT, obj.getId());
                transition = "roleEditor";
            }
        }

        return transition;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // JSON Request Handlerz
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Handles assignment of a work item owned by a workgroup.
     * This is called from the dashboard inbox.
     * @return Json response text
     */
    public String getAssignmentResults(){

        String workItemId = this.getRequestParameter("workItemId");
        String assigneeId = this.getRequestParameter("assigneeId");

        Response response = null;

        if (workItemId != null){

            Workflower workflower = new Workflower(getContext());

            try {
                WorkItem item = getContext().getObjectById(WorkItem.class, workItemId);
                if (item != null){
                    //IIQETN-4956 :- Protecting the resource "assign assignee"
                    boolean isAuthorized = getLoggedInUser().isInWorkGroup(item.getOwner()) ||
                            isAuthorized(CompoundAuthorizer.or(
                                    new RightAuthorizer(SPRight.FullAccessWorkItems),
                                    new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR)));

                    if (isAuthorized) {
                        if (assigneeId == null || "".equals(assigneeId)){
                            workflower.removeAssignee(getLoggedInUser(), item);
                            response = new Response(true, "", "");
                        } else {
                            Identity assignee = getContext().getObjectById(Identity.class, assigneeId);
                            //make sure that the assignee belong to the workgroup, see bug IIQETN-4956
                            if (assignee.isInWorkGroup(item.getOwner())) {
                                workflower.setAssignee(getLoggedInUser(), item, assignee);
                                response = new Response(true, "", "");
                            }
                        }
                    }
                } else {
                    response = new Response(false, Response.SYS_ERROR, "Work item identitified by id '"+workItemId+"' was not found.");
                }
            } catch (Throwable e) {
                log.error(e);
                response = new Response(false, Response.SYS_ERROR, e.getMessage());
            }
        } else {
            response = new Response(false, Response.SYS_ERROR, "Work item id was null.");
        }


        return JsonHelper.toJson(response);
    }

    /**
     * Handles assignment of remediation items for work items owned
     * by a workgroup. Called from the work item detail view.
     * @return Json response text
     */
    public String getRemediationItemAssignmentResults(){

        Response response = null;

        String assigneeId = getRequestParameter("assigneeId");
        String workItemId = getRequestParameter("workItemId");
        boolean selectAll = Util.atob(getRequestParameter("selectAll"));
        List<String> selectedIds = Util.csvToList(getRequestParameter("selected"), true);
        List<String> excludedIds = Util.csvToList(getRequestParameter("excludedIds"), true);

        try {
            Identity assignee = getContext().getObjectById(Identity.class, assigneeId);
            boolean hasSelections = true;
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("workItem.id", workItemId));
            if (selectAll){
                if (excludedIds != null){
                    for(String id : excludedIds){
                        ops.add(Filter.ne("id", id));
                    }
                }
            } else if(selectedIds != null && !selectedIds.isEmpty()){
                ops.add(Filter.in("id", selectedIds));
            } else {
                // in case the json request is bad, make sure we don't assign every single
                // remediation item.
                hasSelections = false;
            }

            if (hasSelections){
                ops.setCloneResults(true);
                Iterator<RemediationItem> items = getContext().search(RemediationItem.class, ops);
                Workflower workflower = new Workflower(getContext());
                if (items != null){
                    while (items.hasNext()) {
                        RemediationItem item =  items.next();
                        workflower.setAssignee(getLoggedInUser(), item, assignee);
                        getContext().decache(item);
                    }
                }
            }

            response = new Response(true, "", "");

        } catch (Throwable e) {
            log.error(e);
            response = new Response(false, Response.SYS_ERROR, e.getMessage());
        }

        return JsonHelper.toJson(response);
    }


    public String assignWorkItem(){
        return null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods - allows a work item to participate in
    // navigation history.
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPageName() {
        return "Work Item";
    }

    @Override
    public String getNavigationString() {
        return "viewWorkItem";
    }

    @Override
    public Object calculatePageState() {
        return super.getObjectId();
    }

    @Override
    public void restorePageState(Object state) {
        if (null == getObjectId()) {
            setObjectId((String) state);
        }
    }

    public String getSignoffsEmailTemplate() {
        return Configuration.SIGNOFF_EMAIL_TEMPLATE;
    }

    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }


    /**
     * If workitem has a form, return the FormRenderer to handle the renderering and processing of the form
     * 
     * @return FormRenderer to handle workItem form
     * @throws GeneralException
     */
    public FormRenderer getFormBean() throws GeneralException {

        if (null == _formBean) {
            // First, try to retrieve the expanded form.
            Form expanded = this.retrieveExpandedForm();
            
            // No expanded form ... create it from the master.
            if (null == expanded) {
                // Get the master - this is the Form on the WorkItem.
                Form master = this.retrieveMasterForm();

                // No master ... should we throw??
                if (null == master) {
                    // shouldn't be on this page of the work item didn't
                    // have a form?
                    // actually, parts of the page can be rendered (or at least
                    // the bean continues to be accessed) after
                    // the submit, not sure why, typically JSF spookiness,  
                    // getForm(() is called in several places, let the caller
                    // decide if it's worth throwing
                    //throw new GeneralException("Work item has no form");
                }
                else {
                    // Use the form handler to build the expanded form from the
                    // master.  This also stores the expanded form as the
                    // active form for the session.
                    FormHandler handler = new FormHandler(getContext(), this);
                    Map<String,Object> args = getFormArguments();
                    expanded = handler.initializeForm(master, true, args);
                }
            }
            
            if (null != expanded) {
                _formBean = new FormRenderer(expanded, this, getLocale(), getContext(), getUserTimeZone());
            }
        }

        return _formBean;
    }


    /**
     * DTO to represent the ApprovalSet in the workitem attributes map. 
     * This is stored in the map with the key "approvalSet".
     * 
     * @return DTO for the workitem ApprovalSet
     * @throws GeneralException
     */
    public ApprovalSetDTO getApprovalSet() throws GeneralException {

        if (_approvalSetDTO == null){

            Map session = getSessionScope();
            _approvalSetDTO = (ApprovalSetDTO)session.get(APPROVAL_SET_DTO);

            // if the workitem ID on the approval set stored on the session doesnt match
            // our current work item, clear it
            if (_approvalSetDTO != null && getObjectId() != null && !getObjectId().equals(_approvalSetDTO.getWorkItemId())){
                _approvalSetDTO = null;
                session.remove(APPROVAL_SET_DTO);
            }
            if ( _approvalSetDTO == null ) {
                WorkItem item = getObject();
                if ( item != null) {
                    Map<String,Object> attrs = item.getAttributes();
                    if ( attrs != null ) {
                        ApprovalSet set = (ApprovalSet)attrs.get(WorkItem.ATT_APPROVAL_SET);
                        if (set != null) {
                            _approvalSetDTO = new ApprovalSetDTO(set);
                            _approvalSetDTO.setWorkItemId(item.getId());
                            _approvalSetDTO.setIdentityRequestId(item.getIdentityRequestId());
                            session.put(APPROVAL_SET_DTO, _approvalSetDTO);
                        }
                    }
                }
            }
        }
        return _approvalSetDTO;
    }


    /**
     * Add a comment to an approval item. This takes request parameters to complete the action:
     * "editForm:approvalComment" - the comment
     * "editForm:approvalId" - the approvalItemId
     * 
     * @return empty string to prevent jsf navigation and stay on same page
     * @throws GeneralException if Approval Item not found
     */
    public String addApprovalItemComment() throws GeneralException {
        WorkItem item = super.getObject();
        if ( item != null ) {
            ApprovalSetDTO setDTO = getApprovalSet();
            if ( setDTO != null ) {
                String comment = (String)getRequestParam().get("editForm:approvalComment");
                if (!Util.isNullOrEmpty(comment)) {
                    String apprId = (String)getRequestParam().get("editForm:approvalId");
                    ApprovalItem approvalItem = getApprovalItem(apprId);
                    if (null == approvalItem) {
                        throw new GeneralException("Approval item not found.");
                    }
    
                    ApprovalItemsService svc =
                        new ApprovalItemsService(getContext(), approvalItem);
                    svc.addComment(item, getLoggedInUser(), comment);
    
                    // Rebuild the ApprovalSetDTO and store it on the session.
                    Map<String,Object> attrs = item.getAttributes();
                    ApprovalSet set = (ApprovalSet) attrs.get(WorkItem.ATT_APPROVAL_SET);
                    _approvalSetDTO = new ApprovalSetDTO(set);
                    _approvalSetDTO.setWorkItemId(item.getId());
                    Map session = getSessionScope();
                    session.put(APPROVAL_SET_DTO, _approvalSetDTO);
                }
            }

        }
        return "";
    }

    /**
     * Update sunrise/sunset dates on approval item. 
     * This takes data in request parameters to drive the update:
     * "editForm:approvalId" - approvalItemId
     * "editForm:approvalSunrise" - sunrise date
     * "editForm:approvalSunset" - sunset date
     * 
     * @return empty string to prevent jsf navigation and stay on same page
     * @throws GeneralException
     */
    public String updateActivationDates() throws GeneralException {
        WorkItem item = super.getObject();
        if ( item != null ) {
            ApprovalSetDTO setDTO = getApprovalSet();
            if ( setDTO != null ) {
                String apprId = (String)getRequestParam().get("editForm:approvalId");
                
                //Get StartDate/EndDate
                long sunrise = Util.atol((String)getRequestParam().get("editForm:approvalSunrise"));
                Date sunriseDate = (sunrise > 0) ? new Date(sunrise) : null;

                long sunset = Util.atol((String)getRequestParam().get("editForm:approvalSunset"));
                Date sunsetDate = (sunset > 0) ? new Date(sunset) : null;

                ApprovalItem approvalItem = getApprovalItem(apprId);
                if (approvalItem != null) {
                    ApprovalItemsService service = new ApprovalItemsService(getContext(), approvalItem);
                    service.setSunriseSunset(item, sunriseDate, sunsetDate);
                }
                
                _approvalSetDTO = new ApprovalSetDTO(item.getApprovalSet());
                _approvalSetDTO.setWorkItemId(item.getId());
                Map session = getSessionScope();
                session.put(APPROVAL_SET_DTO, _approvalSetDTO);
            }

        }
        return "";
    }


    @Override
    public void clearHttpSession() {
        super.clearHttpSession();
        Map session = getSessionScope();
        session.remove(getSessionKey());
        session.remove(APPROVAL_SET_DTO);
    }

    /**
     * Used on rendering pages to display the appropriate format of data.
     */
    public boolean isArchive(){
        return false;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Only one form in this page, so formId is ignored.
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {
        return getFormBean();
    }

    /**
     * Delegate to the WorkItemFormBean to get the arguments.
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        return WorkItemService.getFormArguments(getContext(), getObject(),
                                                 getWorkflowCase());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FormStore interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Store the given master form for later retrieval.
     */
    @Override
    public void storeMasterForm(Form form) {
        // This is already being kept on the WorkItem, so we don't need to do
        // anything really.
        
        if ((null != form) && null != getObjectSafe() && !form.equals(getObjectSafe().getForm())) {
            throw new RuntimeException("Expected to get the WorkItem form.");
        }
    }

    /**
     * Retrieve the master form from the store.
     */
    @Override
    public Form retrieveMasterForm() {
        return (null != getObjectSafe()) ? getObjectSafe().getForm() : null;
    }

    /**
     * Clear the master form from the store.
     */
    @Override
    public void clearMasterForm() {
        // No-op.
    }


    /**
     * Store the given expanded form for later retrieval.  This will be the
     * active form in the workflow session.
     */
    @Override
    public void storeExpandedForm(Form form) {
        if (null != _session) {
            _session.setActiveForm(form);
        }
    }

    /**
     * Retrieve the expanded form from the store.  This is the active form on
     * the session.
     */
    @Override
    public Form retrieveExpandedForm() {
        return (null != _session) ? _session.getActiveForm() : null;
    }

    /**
     * Clear the expanded form from the store AND null out the FormRenderer
     * so the expanded form will be regenerated.
     */
    @Override
    public void clearExpandedForm() {
        _session.setActiveForm(null);
        _formBean = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * No state is required since this just grabs the construction info off the
     * session.
     */
    @Override
    public Map<String,Object> getFormBeanState() {
        if (!Util.isNothing(getObjectId())) {
            return new HashMap<String, Object>() {{
                put(FORM_BEAN_WORKITEM_ID, getObjectId());
            }};
        }

        return null;
    }
}
