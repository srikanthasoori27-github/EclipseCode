/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.List;
import java.util.Locale;

import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.BundleDifference;
import sailpoint.object.Capability;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.workitem.WorkItemDTO;
import sailpoint.workflow.RoleLibrary;

/**
 * This class adds additional information to ApprovalDTOs and consolidates
 * code used in the web tier and service tier.
 */
public class NonIdentityRequestApprovalService {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private UserContext userContext;
    private Locale locale;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor to create this object. This class is used within ApprovalListService to amend
     * additional information not currently available in the UIConfig.
     * @param userContext userContext
     * @param locale locale of the end user
     */
    public NonIdentityRequestApprovalService(UserContext userContext, Locale locale) {
        this.userContext = userContext;
        this.locale = locale;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Iterates through a list of WorkItemDTOs and adds additional data to the list
     * @param approvals List of WorkItemDTOs
     * @throws GeneralException when bad things happen
     */
    public void amendResults(List<WorkItemDTO> approvals) throws GeneralException {
        for (WorkItemDTO approval : Util.safeIterable(approvals)) {
            amendResults(approval);
        }
    }
    
    /**
     * Adds additional data to the WorkItemDTO about non identity request approvals.
     * @param approval the WorkItemDTO to fetch data from
     * @throws GeneralException when bad things happen
     * @ignore
     * This implementation fetches the WorkItem object but ideally should be refactored to
     * accept List<Map<String,Object>> so it fits into the BaseListService model and doesn't
     * re-fetch WorkItem data from the database.
     */
    public void amendResults(WorkItemDTO approval) throws GeneralException {
        // performance boost if there isn't any identity request id, i.e.
        // this isn't the type of approval we would amend data to
        if (approval != null && Util.isNullOrEmpty(approval.getAccessRequestName())
                && !WorkItemDTO.isErrorDTO(approval)) {
            WorkItem item = getWorkItem(approval.getId());
            BundleDifference roleDifference = getRoleDifference(item);
            if (roleDifference != null) {
                ((ApprovalDTO)approval).setRoleDifference(roleDifference);
                ((ApprovalDTO)approval).setApprovalType(ApprovalDTO.ApprovalType.Role);
            }
            ((ApprovalDTO)approval).setTaskResultId(getTaskResultId(item));
            ((ApprovalDTO)approval).setViewPendingChanges(canViewPendingChanges(item));
            ((ApprovalDTO)approval).setRoleTarget(getRoleTarget(item));
            ((ApprovalDTO)approval).setHasReport(getHasReport(item));
        }
    }

    /** 
     * Calculate the differences between the old and new versions 
     * of a role. Orginally we did diffing of both roles and profiles 
     * but in 3.0 you can only have roles. Need to generalize this 
     * to diff other kinds of objects. 
     *  @param workitem workitem
     *  
     * @return the summary of bundle differences 
     */  
    public BundleDifference getRoleDifference(WorkItem workitem) {  

        BundleDifference roleDifference = null;
        // We're doing this because customers need to see complete values in workitems.  
        // This might get messy for large values.  bug #6661  
        int previousDiffLength = Difference.setMaxStringLength(9999);  
        if (workitem != null) {  
            try {  
                RoleLifecycler cycler = new RoleLifecycler(getContext(), this.locale); 
                SailPointObject old = getOldObject(workitem);  
                SailPointObject neu = getNewObject(workitem);  

                if (old == null) {  
                    // creating a new object  
                    if (neu instanceof Bundle)  
                        roleDifference = cycler.diff((Bundle)null, (Bundle)neu, true);  

                }  
                else if (old instanceof Bundle && neu instanceof Bundle) {  
                    roleDifference = cycler.diff((Bundle)old, (Bundle)neu, true);  
                }  
            }  
            catch (Throwable t) {  
                Difference.setMaxStringLength(previousDiffLength);  
            }  
        }  

        Difference.setMaxStringLength(previousDiffLength);
        return roleDifference;
    }  
  
    /** 
     * Gets the approval workflow object, the new object about to be approved.
     * @param workitem
     * @return the new version of an object submitted for approval. 
     */  
    public SailPointObject getNewObject(WorkItem workitem) {
        SailPointObject newObject = null;  
        
        if (workitem != null) {  
            WorkflowCase wfcase = workitem.getWorkflowCase();  
            if (wfcase != null) {  
                newObject = wfcase.getApprovalObject();  
            }  
        }  
        return newObject;  
    }

    /** 
     * Gets the approval workitem in its original state without changes.
     * @param workitem in its original state
     * @return the previous version of an object submitted for approval. 
     * This will be null if we are approving the creation of 
     * a new object. 
     */  
    private SailPointObject getOldObject(WorkItem workItem) throws GeneralException {  
        SailPointObject old = null;  

        if (workItem != null) {  
            WorkflowCase wfcase = workItem.getWorkflowCase();  
            if (wfcase != null) {  
                SailPointObject neu = wfcase.getApprovalObject();  
                if (neu != null && neu.getId() != null) {  
                    old =  getContext().getObjectById(neu.getClass(), neu.getId());  
                }  
            }  
        }  
        return old;  
    }  

    /**
     * If approval object is role, fetch target object role dto
     * @param workitem
     * @return target object role dto
     * @throws GeneralException
     */
    private RoleSummaryDTO getRoleTarget(WorkItem workitem) throws GeneralException {
        SailPointObject old = getOldObject(workitem);  
        SailPointObject neu = getNewObject(workitem); 
        RoleSummaryDTO roleTargetDTO = null;
        if (old instanceof Bundle && neu instanceof Bundle) {
            roleTargetDTO = new RoleSummaryDTO((Bundle)old, this.userContext);
        }
        return roleTargetDTO;
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
    private String getTaskResultId(WorkItem workitem) {  

        String id = null;  

        if ( workitem == null ) {  
            return id;  
        }  

        // new way  
        if (workitem.isTargetClass(TaskResult.class)) {  
            id = workitem.getTargetId();  
        }  

        // old way  
        if (id == null) {  
            id = workitem.getString(WorkItem.ATT_TASK_RESULT);  
        }  

        // kludge way  
        if (id == null) {  
            WorkflowCase wfcase = workitem.getWorkflowCase();  
            if (wfcase != null)  
                id = wfcase.getString(RoleLibrary.VAR_IMPACT_ANALYSIS_RESULT);  
        }  

        return id;  
    }  

    /** 
     * @return boolean true if user can view the pending changes in the modeler. only allow owner or system admin. 
     */  
    private boolean canViewPendingChanges(WorkItem item) throws GeneralException {  
        return getHasPendingChange(item) && (isOwnerOrIsInWorkgroup(item, this.userContext) || isSystemAdmin(this.userContext));  
    }  

    /** 
     * @param workitem
     * @return true if there are pending changes to review in the modeler. 
     * @throws GeneralException
     */  
    private boolean getHasPendingChange(WorkItem item) throws GeneralException {  
        boolean pendingChange = false;  
        if (item != null) {
            WorkflowCase wfcase = item.getWorkflowCase();  
            if (wfcase != null) {  
                pendingChange = !wfcase.isDeleteApproval();  
            }  
        }
        return pendingChange;  
    }  

    /**
     * @param item workitem
     * @return true if work item has report result
     * @throws GeneralException
     */
    private boolean getHasReport(WorkItem item) throws GeneralException{
        boolean hasReport = false;
        String id = getTaskResultId(item);
        if(id != null) {
            TaskResult result = getContext().getObjectById(TaskResult.class, getTaskResultId(item));
            if (result != null && result.getReport() != null && result.getReport().getFiles() != null
                    && !result.getReport().getFiles().isEmpty()) {
                hasReport = true;
            }
        }
        return hasReport;
    }

    /** 
     * @param userContext
     * @return return if current user is systemAdmin 
     */  
    private boolean isSystemAdmin(UserContext userContext) {  
        return Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities());  
    }  

    /** 
     * A method that decides if the logged in user is the owner of within a workgroup that owns 
     * a workItem. 
     * @param workitem
     * @param userContext
     * 
     * @return true if the logged in user is the owner or within a workgroup that owns a workItem 
     * @throws GeneralException 
     */  
    private boolean isOwnerOrIsInWorkgroup(WorkItem workitem, UserContext userContext) throws GeneralException {  
        Identity loggedInUser = userContext.getLoggedInUser();  
        Identity owner = workitem.getOwner();  
        if (loggedInUser.getId().equals(owner.getId()) || (owner.isWorkgroup() && loggedInUser.isInWorkGroup(owner))){  
            return true;  
        }
        
        return false;  
    }
    
    /**
     * Load the WorkItem object for the given approval.  This requires an "id" to be in the approval
     * map or else it throws.
     *
     * @param  id approval or workitem id
     *
     * @return The WorkItem for the given approval.
     *
     * @throws GeneralException  If the approval map is missing the ID property.
     * @throws ObjectNotFoundException  If a WorkItem with the given ID is not found.
     */
    private WorkItem getWorkItem(String id)
        throws GeneralException, ObjectNotFoundException {

        if (null == id) {
            throw new GeneralException("ID is required.");
        }


        WorkItem workItem = getContext().getObjectById(WorkItem.class, id);
        if (null == workItem) {
            throw new ObjectNotFoundException(WorkItem.class, id);
        }

        return workItem;
    }
    
    private SailPointContext getContext() {
        return this.userContext.getContext();
    }
}