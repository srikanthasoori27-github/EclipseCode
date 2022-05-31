/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.PersistenceOptionsUtil;
import sailpoint.api.PolicyTreeNodeSummarizer;
import sailpoint.object.Identity;
import sailpoint.object.RemediationItem;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.util.NavigationHistory;

import java.util.List;


/**
 * JSF bean for dealing with remediation items.
 */
public class RemediationItemBean extends BaseObjectBean<RemediationItem> {
    private static Log log = LogFactory.getLog(RemediationItemBean.class);
    
    /**
     * The comment to be added to the remediation item or to be used for
     * completion.
     */
    private String comment;

    /**
     * Whether this item is editable by the logged in user.
     */
    private boolean editable;
    
    /**
     * The priority to set on the work item
     */
    private String workItemPriority;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public RemediationItemBean() throws GeneralException {
        super();
        setScope(RemediationItem.class);
        setStoredOnSession(false);

        // Try to pull ID from request.
        String remediationItemId =
            Util.getString(super.getRequestParameter("remediationItemId"));
        if (null != remediationItemId) {
            super.setObjectId(remediationItemId);

            Identity loggedInUser = super.getLoggedInUser();
            WorkItem item = super.getObject().getWorkItem();
            if ((null != item) &&
                 (loggedInUser.equals(item.getOwner()) ||
                  loggedInUser.isInWorkGroup(item.getOwner()))) {
                this.editable = true;
                WorkItem.Level priority = item.getLevel();
                if (priority == null) {
                    workItemPriority = WorkItem.Level.Normal.name();                    
                } else {
                    workItemPriority = priority.name();
                }
            } else {
                workItemPriority = WorkItem.Level.Normal.name();
            }
        } 
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isEditable() {
        return this.editable;
    }
    
    public String getWorkItemPriority() {
        return workItemPriority;
    }
    
    public void setWorkItemPriority(String priority) {
        workItemPriority = priority;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Action to complete the remediation item.
     */
    public String complete() throws GeneralException {
        RemediationItem item = getObject();
        item.complete(this.comment);
        WorkItem workItem = item.getWorkItem();
        if (workItem != null) {
            try {
                workItem.setLevel(WorkItem.Level.valueOf(workItemPriority));
            } catch (Exception e) {
                log.error("Failed to update the work item priority to " + workItemPriority + ".  Updating it to 'Normal' instead.", e);
                workItem.setLevel(WorkItem.Level.Normal);
            }
        }
        
        // bug 21969 - Make the actual person completing the remediation the owner
        // (similar to fix in bug 8550)
        // Get the logged in user
        String newOwnerName = getContext().getUserName();
        Identity newOwner = getContext().getObjectByName(Identity.class, newOwnerName);
        if (null != newOwner) {
            item.setOwner(newOwner);
        }
        
        getContext().saveObject(item);
        getContext().commitTransaction();
        String outcome = NavigationHistory.getInstance().back();
        return (null != outcome) ? outcome : "completeSuccess";
    }

    /**
     * Action to add a comment to the remediation item.
     */
    public String addComment() throws GeneralException {
        RemediationItem item = getObject();
        item.addComment(this.comment, getLoggedInUser());
        getContext().saveObject(item);
        /*
         * bug 25324 Setting the context to mutable because a flushing objects takes place
         * and we don't want to get a ImmutableModificationException, as soon as we commit
         * the transaction, we restore the immutable state to the context.
         */
        PersistenceOptionsUtil pou = new PersistenceOptionsUtil();
        pou.configureImmutableOption(getContext());
        getContext().commitTransaction();
        pou.restoreImmutableOption(getContext());
        String outcome = NavigationHistory.getInstance().back();
        return (null != outcome) ? outcome : "completeSuccess";
    }

    /**
     * Override BaseBean isAuthorized so we can allow the RemediationItem's
     * assignee or owner to view the object even if they are technically
     * out of scope.
     *
     * @param object
     * @return
     * @throws GeneralException
     */
    @Override
    protected boolean isAuthorized(SailPointObject object) throws GeneralException {
        boolean auth = super.isAuthorized(object);

        if(auth == false) {
            Identity user = getLoggedInUser();
            if(object instanceof RemediationItem) {
                RemediationItem ri = (RemediationItem)object;
                if(user.equals(ri.getAssignee())){
                    return true;
                }
                WorkItem wi = ri.getWorkItem();
                Identity wiAssignee = wi.getAssignee();
                if(wiAssignee != null && user.equals(wiAssignee)) {
                    return true;
                }
                Identity wiOwner = wi.getOwner();
                if(wiOwner != null && user.equals(wiOwner)) {
                    return true;
                }
                List<Identity> workgroups = (user != null) ? user.getWorkgroups() : null;
                if ( Util.size(workgroups) > 0 )  {
                    for ( Identity workgroup : workgroups ) {
                        if ( wiAssignee != null && workgroup.equals(wiAssignee) ) {
                            return true;
                        }
                        else if ( wiOwner != null && workgroup.equals(wiOwner) ) {
                            return true;
                        }
                    }
                }
            }
        }

        return auth;
    }

    public String asSummary(PolicyTreeNode policyTreeNode) throws GeneralException {
        if (policyTreeNode == null) {
            return "";
        }

        return new PolicyTreeNodeSummarizer(policyTreeNode, getLocale(), getUserTimeZone()).getSummary();
    }

}
