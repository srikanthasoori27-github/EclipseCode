/**
 * @author michael.hide
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.authorization.WorkItemArchiveAuthorizer;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.ApprovalItemGridHelper;
import sailpoint.web.workitem.ApprovalSetDTO;
import sailpoint.web.workitem.WorkItemArchiveAttribute;
import sailpoint.web.workitem.WorkItemUtil;

/**
 *
 */
public class WorkItemArchiveBean 
                extends BaseObjectBean<WorkItemArchive>
                implements NavigationHistory.Page {
    
    private static Log log = LogFactory.getLog(WorkItemArchiveBean.class);
    
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
    private static final String GRID_STATE = "workItemsArchiveGridState";
    private static final String ATT_APPROVAL_SET = "approvalSet";
    private static final String ATT_ELECTRONIC_SIGNATURE = "electronicSignature";

    private String priority;

    private ApprovalSetDTO approvalSet;

    private List<ColumnConfig> approvalColumns;

    private SignOffHistory signoffHistory;

    /**
     * 
     */
    public WorkItemArchiveBean() throws GeneralException {
        super();
        setScope(WorkItemArchive.class);
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
        
        WorkItemArchive workItemArchive = super.getObject();

        if (workItemArchive != null) {
            authorize(new WorkItemArchiveAuthorizer(workItemArchive));
        }
        
        if (workItemArchive == null || workItemArchive.getLevel() == null) {
            this.priority = WorkItem.Level.Normal.name();
        } else {
            this.priority = workItemArchive.getLevel().name();
        }

    }
    
    /**
     * Get the next page, either from the history or default to the work items
     * list.
     */
    String getNextPage() {
        clearHttpSession();
        String prev = NavigationHistory.getInstance().back();
        return (null != prev) ? prev : getDefaultNextPage();
    }

    String getDefaultNextPage() {
        return "manageWorkItemArchive";
    }
    
    /**
     * Cancel from viewing a work item archive.
     */
    public String cancel() {
        return getNextPage();
    }

    public String getPageName() {
        return "Work Item Archive";
    }

    public String getNavigationString() {
        return "viewWorkItemArchive";
    }

    public Object calculatePageState() {
        return super.getObjectId();
    }

    public void restorePageState(Object state) {
        if (null == getObjectId()) {
            setObjectId((String) state);
        }
    }
    
    @Override
    public WorkItemArchive getObject() throws GeneralException {
        String objectId = super.getRequestOrSessionParameter("id");
        
        if (objectId == null || objectId.trim().length() == 0) {
            objectId = super.getRequestOrSessionParameter("navigationForm:objectId");
        }
        
        if (objectId != null && objectId.trim().length() > 0) {
            _objectId = objectId;
        }
        WorkItemArchive wia = super.getObject();
        if(wia == null){
            //crap
        }
        return wia;
    }
    
    /**
     * Transition to the identity request associated with this item.
     */
    public String viewAccessRequest() throws GeneralException {
        NavigationHistory.getInstance().saveHistory(this);
        WorkItemArchive item = getObject();
        if (null == item.getIdentityRequestId()) {
            throw new GeneralException("No request ID for work item archive: " + item);
        }
        return "viewAccessRequest#/request/" + item.getIdentityRequestId();
    }
    
    public boolean isAccessRequestViewable() throws GeneralException {
        return IdentityRequestAuthorizer.isAuthorized(getObject().getIdentityRequestId(), this);
    }
    
    public List<SelectItem> getPrioritySelectItems() {
        return WorkItemUtil.getPrioritySelectItems(getLocale(), getUserTimeZone());
    }
    
    /**
     * Return the name of the identity that is considered to be the 
     * "requester" of this item.  Older work items will have
     * this stored in the WorkItem.requester property.  Newer
     * work items created by workflows processed in the background
     * will store the requester name in a variable.
     */
    public String getRequester() throws GeneralException {
        String requester = "???";
        WorkItemArchive item = super.getObject();
        if (item != null) {
            requester = item.getRequester();
            if (requester == null) {
                requester = item.getString(Workflow.VAR_LAUNCHER);
            }
        }
        return requester;
    }
    
    public String getPriority() {
        return this.priority;
    }
    
    public void setPriority(String priority) {
        this.priority = priority;
    }
    
    /**
     * Used on rendering pages to display the appropriate format of data.
     */
    public boolean isArchive(){
        return true;
    }
    
    public String getGridStateName() {
        return GRID_STATE;
    }

    public ApprovalSetDTO getApprovalSet() throws GeneralException {
        if (null == approvalSet && hasApprovalSet()) {
            ApprovalSet as = (ApprovalSet) getObject().getAttributes().get(ATT_APPROVAL_SET);
            // scrub password values from approval set
            approvalSet = new ApprovalSetDTO(ObjectUtil.scrubPasswordsAndClone(as));
        }

        return approvalSet;
    }

    public List<ColumnConfig> getApprovalMemberColumns() throws GeneralException {
        if(approvalColumns == null) {
            ApprovalItemGridHelper columnHelper = new ApprovalItemGridHelper(getApprovalSet(), getObject().getAttributes().get("showOwner") != null);
            approvalColumns = columnHelper.getApprovalItemHeaders(isManualWorkItem());
        }

        return approvalColumns;
    }

    public String getDefaultSortColumn() {
        return ApprovalItemGridHelper.JSON_APPLICATION;
    }

    public String getApprovalMemberColumnJSON() throws GeneralException {
        return super.getColumnJSON(getDefaultSortColumn(), getApprovalMemberColumns());
    }

    public String getStoreFieldsJson() throws GeneralException {
        ApprovalItemGridHelper columnHelper = new ApprovalItemGridHelper(getApprovalSet(), getObject().getAttributes().get("showOwner") != null);
        return columnHelper.getStoreFieldsJson();
    }

    private boolean isManualWorkItem() throws GeneralException {
        return getObject().getType().equals(WorkItem.Type.ManualAction);
    }

    public boolean isEditable() {
        return false;
    }

    public IdentityDTO getTargetIdentityBean() {
        return null;
    }

    public String getMembersGridJson() throws GeneralException {
        int page = Integer.parseInt((String) getRequestParam().get("page"));
        int limit = getResultLimit();
        int start = (page - 1) * limit;
        int end = start + limit;
        String sortColumn = (String) getRequestParam().get("sort");
        boolean ascending = "ASC".equals((String) getRequestParam().get("dir"));

        String filter = (String) getRequestParam().get("decision");
        if( filter == null ) {
            filter = "all";
        }
        return getApprovalSet().getMembersGridJson( start, end, sortColumn, ascending, filter );
    }

    public boolean isElectronicallySigned() throws GeneralException {
        return getObject().isSigned();
    }

    public SignOffHistory getSignoffHistory() throws GeneralException {
        if (null == signoffHistory) {
            signoffHistory = (SignOffHistory) getObject().getSystemAttributes().get(ATT_ELECTRONIC_SIGNATURE);
        }

        return signoffHistory;
    }

    private boolean hasApprovalSet() throws GeneralException {
        return null != getObject() && null != getObject().getAttributes() &&
               getObject().getAttributes().containsKey(ATT_APPROVAL_SET);
    }

    /**
     * Gets a List of WorkItemArchiveAttributes for display in a <h:dataTable>
     *
     * @return The list of attributes
     * @throws GeneralException
     */
    public List<WorkItemArchiveAttribute> getAttributeList() throws GeneralException {
        WorkItemArchive wia = this.getObject();
        ArrayList<WorkItemArchiveAttribute> list = new ArrayList();

        if (wia != null) {
            Attributes attribs = wia.getAttributes();
            if (!Util.isEmpty((Map)attribs)) {
                List<String> keys = attribs.getKeys();
                for (String key : keys) {
                    list.add(new WorkItemArchiveAttribute(key, attribs.get(key)));
                }
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        return list;
    }
}
