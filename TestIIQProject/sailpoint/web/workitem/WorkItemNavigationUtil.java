/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.workitem;

import java.util.Iterator;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;

/**
 * Utility to help with work item navigation
 *
 * @author patrick.jeong
 */
public class WorkItemNavigationUtil {

    SailPointContext context;

    public static final String NAV_VIEW_WORK_ITEM = "viewWorkItem";
    public static final String NAV_VIEW_COMMON_WORK_ITEM = "viewCommonWorkItem";
    public static final String NAV_VIEW_CHALLENGE_WORK_ITEM = "viewChallengeItem";
    public static final String NAV_VIEW_WORK_ITEM_ARCHIVE = "viewWorkItemArchive";


    public WorkItemNavigationUtil(SailPointContext context) {
        this.context = context;
    }

    /**
     * Get nav string by work item id
     *
     * @param workItemId
     * @return navigation string
     */
    public String navigate(String workItemId, boolean checkArchive, Map<String,Object> sessionMap) throws GeneralException {
        if (workItemId == null) {
            return NAV_VIEW_WORK_ITEM;
        }

        WorkItem workItem = context.getObjectById(WorkItem.class, workItemId);

        // if work item doesn't exist try looking for work item archive
        if (workItem == null) {
            String nav = NAV_VIEW_WORK_ITEM;
            if (checkArchive) {
                QueryOptions wiaop = new QueryOptions();
                wiaop.add(Filter.or(Filter.eq("workItemId", workItemId), Filter.eq("name", workItemId)));
                Iterator<WorkItemArchive> wia = context.search(WorkItemArchive.class, wiaop);
                if (wia.hasNext()) {
                    WorkItemArchive arch = wia.next();
                    nav = viewWorkItemArchive(arch.getId(), sessionMap);
                }
            }
            return nav;
        }

        return navigate(workItem, sessionMap);
    }

    /**
     * Old work item types navigate to viewWorkItem
     * New style work item types navigate to viewCommonWorkItem
     *
     * @param workItem
     * @return navigation string
     */
    public String navigate(WorkItem workItem, Map<String,Object> sessionMap) throws GeneralException {
        String nav = NAV_VIEW_WORK_ITEM;

        String workItemId = workItem.getId();

        //BUG30489 We needed to add the ability to force the classic UI for custom forms
        if (isNewTypeWorkItem(workItem) && !forceClassicApprovalUI(workItem)) {
            nav = NAV_VIEW_COMMON_WORK_ITEM + "#/commonWorkItem/" + workItemId;
        }
        else if (WorkItem.Type.Challenge.equals(workItem.getType())) {
            nav = NAV_VIEW_CHALLENGE_WORK_ITEM;
        }

        // for old type work items add work item id to session
        //BUG30489 We needed to add the ability to force the classic UI for custom forms
        if (!isNewTypeWorkItem(workItem) || forceClassicApprovalUI(workItem)){
            setWorkItemIdSessionValues(sessionMap, workItemId);
        }

        return nav;
    }

    /**
     * An action request to view a work item archive occurred
     *
     * @param workItemId
     * @param sessionMap
     * @return navigation outcome
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String viewWorkItemArchive(String workItemId, Map<String, Object> sessionMap) throws GeneralException {
        assert (null != workItemId);
        String next = NAV_VIEW_WORK_ITEM_ARCHIVE + "?id=" + workItemId;
        setWorkItemIdSessionValues(sessionMap, workItemId);
        return next;
    }

    /**
     * Add workItem id to session map and set forceLoad to true
     *
     * @param sessionMap
     */
    private void setWorkItemIdSessionValues(Map<String, Object>  sessionMap, String workItemId) {
        sessionMap.put("editForm:id", workItemId);
        sessionMap.put("forceLoad", "true");
    }

    /*
     * We need to leave the ability for the customer to force the classic UI
     * for approvals so that they can add in custom forms. Theere are true place 
     * that you can setup this flag.  You can either add it to the workItem or as
     * a systemConfiguration.  
     * 
     * Add this workItem flag
     * 
     *  <Arg name="forceClassicApprovalUI" value="true"/>
     *
     * or this system config option
     * 
     *  <entry key="forceClassicApprovalUI" value="true"/>
     *  
     * @param workItem
     * @return true if the work item type Approval and they have the flag forceClassicApprovalUI set to true.
     */
    
    public boolean forceClassicApprovalUI(WorkItem workItem)throws GeneralException {
        if(null != workItem && WorkItem.Type.Approval.equals(workItem.getType())){
            return workItem.getBoolean("forceClassicApprovalUI") ||
                    this.context.getConfiguration().getBoolean(Configuration.FORCE_CLASSIC_APPROVAL_UI);
        }
        return false;
    }

    /**
     * Check if the work item type is one of the "new" type
     *
     * @param workItem
     * @return true if the work item type is "new"
     * @throws GeneralException
     */
    public boolean isNewTypeWorkItem(WorkItem workItem) throws GeneralException {
        if (workItem == null) {
            return false;
        }

        WorkItem.Type workItemType = workItem.getType();
        // this used to check access request (those with an identity request) or PAM Approvals, but now any ol'
        // approval is fine
        return (WorkItem.Type.ViolationReview.equals(workItemType) ||
                WorkItem.Type.Form.equals(workItemType) ||
                WorkItem.Type.Approval.equals(workItemType));
    }
}
