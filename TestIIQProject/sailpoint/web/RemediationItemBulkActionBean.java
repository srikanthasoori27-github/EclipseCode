/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.PersistenceOptionsUtil;
import sailpoint.object.Identity;
import sailpoint.object.RemediationItem;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;


/**
 * A JSF bean for performing bulk remediation item actions.
 * 
 * @author Kelly Grizzle
 */
public class RemediationItemBulkActionBean extends BaseObjectBean<WorkItem> {

    /**
     * The IDs of the remediation items on which the bulk action should
     * be performed.
     */
    private List<String> selectedRemediationItemIds;

    /**
     * The comment entered for the bulk action.
     */
    private String comment;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public RemediationItemBulkActionBean() {
        super();

        setScope(WorkItem.class);
        setStoredOnSession(false);

        // Try to pull request parameters from bulk remediation request.
        String workItemId =
            Util.getString(super.getRequestParameter("workItemId"));
        String selectedIds =
            Util.getString(super.getRequestParameter("selectedRemediationItemIds"));
        if (null != workItemId) {
            super.setObjectId(workItemId);
            if (null != selectedIds) {
                this.selectedRemediationItemIds = Util.csvToList(selectedIds);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ/WRITE PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public List<String> getSelectedRemediationItemIds() {
        return selectedRemediationItemIds;
    }

    public void setSelectedRemediationItemIds(List<String> selected) {
        this.selectedRemediationItemIds = selected;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to complete all remediation items on this work item.
     */
    public String completeAll() throws GeneralException {
        
        completeRemediations(getAllRemediationItems());
        String prev = NavigationHistory.getInstance().back();
        return (null != prev) ? prev : "viewCertifications";
    }

    /**
     * Action to complete the selected remediation items on this work item.
     */
    public String completeSelected() throws GeneralException {

        completeRemediations(this.selectedRemediationItemIds);
        String prev = NavigationHistory.getInstance().back();
        return (null != prev) ? prev : "viewCertifications";
    }

    private List<String> getAllRemediationItems() throws GeneralException {
        
        List<String> ids = new ArrayList<String>();
        WorkItem workItem = super.getObject();
        if (null != workItem.getRemediationItems()) {
            for (RemediationItem item : workItem.getRemediationItems()) {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    private void completeRemediations(List<String> ids)
        throws GeneralException {
        
        if (null != ids) {
            for (String id : ids) {
                RemediationItem item =
                    getContext().getObjectById(RemediationItem.class, id);
                
                // Only mark as complete if not yet completed.
                if (!item.isComplete()) {
                    item.complete(this.comment);
                    
                    // Make the actual person completing the remediation the owner
                    // (bug 8550)
                    // Get the logged in user
                    String newOwnerName = getContext().getUserName();
                    Identity newOwner = getContext().getObjectByName(Identity.class, newOwnerName);
                    if (null != newOwner) {
                        item.setOwner(newOwner);
                    }
                }
                getContext().saveObject(item);
            }
            /*
             * bug 25324 Setting the context to mutable because a flushing objects takes place
             * and we don't want to get a ImmutableModificationException, as soon as we commit
             * the transaction, we restore the immutable state to the context.
             */
            PersistenceOptionsUtil pou = new PersistenceOptionsUtil();
            pou.configureImmutableOption(getContext());
            getContext().commitTransaction();
            pou.restoreImmutableOption(getContext());
        }
    }
}
