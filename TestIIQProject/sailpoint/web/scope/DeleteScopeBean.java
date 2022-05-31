/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import sailpoint.api.ScopeService;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;

import javax.faces.model.SelectItem;

import java.util.List;


/**
 * JSF bean for deleting scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class DeleteScopeBean extends BaseScopeBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private Scope assignedScopeReplacement;
    private Scope controlledScopeReplacement;
    private boolean deleteChildren;

    private List<SelectItem> scopeSelections;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public DeleteScopeBean() {
        super();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public Scope getAssignedScopeReplacement() {
        return assignedScopeReplacement;
    }

    public void setAssignedScopeReplacement(Scope assignedScopeReplacment) {
        this.assignedScopeReplacement = assignedScopeReplacment;
    }

    public Scope getControlledScopeReplacement() {
        return controlledScopeReplacement;
    }

    public void setControlledScopeReplacement(Scope controlledScopeReplacment) {
        this.controlledScopeReplacement = controlledScopeReplacment;
    }

    public boolean isDeleteChildren() {
        return deleteChildren;
    }

    public void setDeleteChildren(boolean deleteChildren) {
        this.deleteChildren = deleteChildren;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Delete the selected scope, appropriately cleaning up the references to
     * this scope.
     */
    public String delete() throws GeneralException {

        ScopeService.DeletionOptions ops =
            new ScopeService.DeletionOptions(this.assignedScopeReplacement, this.controlledScopeReplacement, this.deleteChildren);

        // TODO: This could take a while ... background it?
        new ScopeService(getContext()).deleteScope(getObject(), ops);

        return "scopeDeleted";
    }
}
