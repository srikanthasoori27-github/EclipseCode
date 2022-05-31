/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A base class for workflow library classes that manage the approval
 * process for a persistent subclass of SailPointobject.
 *
 * Author: Jeff
 *
 * Currently this is used only for role model approvals (inherited by
 * RoleLibrary) but it is general enough to use for other SailPointObjects
 * if necessary.
 *
 * Object approval workflows are expected to maintain a COPY of the
 * object being edited in the workflow case.  After the approval process
 * the object may be "committed" by saving the object in the case.
 * 
 * Workflows that use this library are required to define these variables:
 *
 *     approvalObject - the SailPointobject being approved
 *
 */

package sailpoint.workflow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.WorkflowCase;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

public class ApprovalLibrary extends WorkflowLibrary {

    //////////////////////////////////////////////////////////////////////
    //
    // Approval Object Inspection
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the object being approved.
     */
    public SailPointObject getObject(WorkflowContext wfc) {
        WorkflowCase wfcase = wfc.getWorkflowCase();
        return wfcase.getApprovalObject();
    }

    /**
     * Return the simple class name of the object being aproved.
     * If this is a Bundle instance, "Role" is returned.
     */
    public String getObjectClass(WorkflowContext wfc) {
        String cls = null;
        SailPointObject obj = getObject(wfc);
        if (obj instanceof Bundle) 
            cls = "Role";
        else
            cls = obj.getClass().getSimpleName();
        return cls;
    }

    /**
     * Return the name of the object being approved.
     */
    public String getObjectName(WorkflowContext wfc) {
        String name = null;
        SailPointObject obj = getObject(wfc);
        if (obj != null)
            name = obj.getName();
        return name;
    }

    /**
     * Get the current persistent version of the object we're holding 
     * in the workflow case.
     * 
     * This assumes that the object is stored in the "approvalObject"
     * variable.
     */
    public SailPointObject getCurrentObject(WorkflowContext wfc)
        throws GeneralException {

        WorkflowCase wfcase = wfc.getWorkflowCase();
        SailPointObject obj = wfcase.getApprovalObject();
        if (obj != null) {
            SailPointContext con = wfc.getSailPointContext();
            obj = con.getObjectById(obj.getClass(), obj.getId());
        }
        return obj;
    }


    /**
     * Return the owner of the object being approved.
     * Note that this is the <b>current</b> owner, which may be different
     * from the owner set in the approval object.  
     *
     * @ignore
     * When changing owners we may want accessors for the
     * new owner so they can also approve?
     */
    public Identity getObjectOwner(WorkflowContext wfc) 
        throws GeneralException {
        Identity owner = null;
        SailPointObject obj = getObject(wfc);
        if (obj != null) {
            SailPointContext spcon = wfc.getSailPointContext();
            if (obj.getId() != null) {
                SailPointObject current = 
                    spcon.getObjectById(obj.getClass(), obj.getId());
                if (current != null)
                    owner = current.getOwner();
            }
            else {
                // a new object make the new owner approve
                owner = obj.getOwner();
            }
        }
        return owner;
    }

    /**
     * Return the new object owner.
     * This may be different than the identity returned
     * by {@link #getObjectOwner} since the owner may have changed.
     */
    public Identity getNewObjectOwner(WorkflowContext wfc) {
        Identity owner = null;
        SailPointObject obj = getObject(wfc);
        if (obj != null)
            owner = obj.getOwner();
        return owner;
    }

    /**
     * Return the name of the owner of the object
     * being approved.
     */ 
    public String getObjectOwnerName(WorkflowContext wfc) 
        throws GeneralException {

        Identity owner = getObjectOwner(wfc);
        return (owner != null) ? owner.getName() : null;
    }

    /**
     * Return just the name of the new object owner.
     */
    public String getNewObjectOwnerName(WorkflowContext wfc) {
        Identity owner = getNewObjectOwner(wfc);
        return (owner != null) ? owner.getName() : null;
    }

    /**
     * Return true if the object being approved has
     * had an owner change.
     */
    public boolean isOwnerChange(WorkflowContext wfc) 
        throws GeneralException {

        boolean change = false;
        Identity owner = getObjectOwner(wfc);
        Identity newOwner = getNewObjectOwner(wfc);
        if (owner == null)
            change = (newOwner != null);
        else if (newOwner == null)
            change = (owner != null);
        else
            change = owner.getId().equals(newOwner.getId());
        return change;
    }

    /**
     * Return true if the user launching the workflow is the same
     * as the owner of the object being approved.
     */
    public boolean isSelfApproval(WorkflowContext wfc) 
        throws GeneralException {

        // We won't be in the background yet so use the spcontext
        // user. !! I think we should use the "launcher" variable
        // instead?
        SailPointContext spcon = wfc.getSailPointContext();
        String userName = spcon.getUserName();
        String owner = getObjectOwnerName(wfc);
        
        return userName.equals(owner);
    }




}
