/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.extjs.TreeBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

import javax.faces.event.ActionEvent;


/**
 * JSF bean used to render the scope tree.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ScopeTreeBean extends TreeBean<Scope> {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    // Fields for moving a scope.
    private String movedScopeId;
    private String newParentScopeId;
    private String oldParentScopeId;
    
    // Need to know whether scopes are enabled
    private boolean scopingEnabled;
    
    private boolean manageScopeRight;

    // Fields for creating a scope.
    private String parentId;

    // Fields for editing a scope.
    private String selectedScopeId;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public ScopeTreeBean() throws GeneralException {
        super(Scope.class);
        this.scopingEnabled =
            getContext().getConfiguration().getBoolean(Configuration.SCOPING_ENABLED, false);
        
        this.manageScopeRight = WebUtil.hasRight(getFacesContext(), SPRight.ManageScope);
        
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // TreeBean implementation
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getPageSize() throws GeneralException {
        return Configuration.getSystemConfig().getInt(Configuration.SCOPE_TREE_UI_DISPLAY_LIMIT);
    }

    /* (non-Javadoc)
     * @see sailpoint.web.extjs.TreeBean#getQueryOptionsForChildren(sailpoint.object.SailPointObject)
     */
    @Override
    protected QueryOptions getQueryOptionsForChildren(Scope scope)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        if (null == scope) {
            qo.add(Filter.isnull("parent"));
        }
        else {
            qo.add(Filter.eq("parent", scope));
        }
        qo.addOrdering("displayName", true);

        return qo;
    }

    @Override
    protected String getIconClass(Scope scope) {
        return (!scope.isDormant()) ? "scopeNode" : "scopeNodeDormant";
    }

    @Override
    protected String getName(Scope scope) {
        String name = scope.getDisplayableName();

        if (scope.isDormant()) {
            name = super.getMessage(MessageKeys.DORMANT_SCOPE, name);
        }

        return name;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getMovedScopeId() {
        return movedScopeId;
    }

    public void setMovedScopeId(String movedScopeId) {
        this.movedScopeId = movedScopeId;
    }

    public String getNewParentScopeId() {
        return newParentScopeId;
    }

    public void setNewParentScopeId(String newParentScopeId) {
        this.newParentScopeId = newParentScopeId;
    }

    public String getOldParentScopeId() {
        return oldParentScopeId;
    }

    public void setOldParentScopeId(String oldParentScopeId) {
        this.oldParentScopeId = oldParentScopeId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getSelectedScopeId() {
        return selectedScopeId;
    }

    public void setSelectedScopeId(String selectedScopeId) {
        this.selectedScopeId = selectedScopeId;
    }

    public boolean isScopingEnabled() {
        return scopingEnabled;
    }
    
    public boolean isManageScopeRight() {
        
        return this.manageScopeRight;
    }
    
    public String getDisabledScopesMsg() {
        Message msgparam = new Message(MessageKeys.CONFIGURE_SCOPES);
        Message msg = new Message(MessageKeys.SCOPE_DISABLED_WARNING, msgparam.getLocalizedMessage( getLocale(), null ));
        return msg.getLocalizedMessage( getLocale(), null );
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String moveScopeAction() throws GeneralException {
        
        SailPointContext context = getContext();
        Scope oldParent = null;
        Scope newParent = null;

        String oldParentId = super.getNodeId(this.oldParentScopeId);
        String newParentId = super.getNodeId(this.newParentScopeId);
        String movedId = super.getNodeId(this.movedScopeId);

        if (null != oldParentId) {
            oldParent = context.getObjectById(Scope.class, oldParentId);
        }

        if (null != newParentId) {
            newParent = context.getObjectById(Scope.class, newParentId);
        }

        Scope movedScope = context.getObjectById(Scope.class, movedId);

        boolean exists = ScopeBean.doesScopeExist(context, movedScope.getName(), movedScope.getDisplayName(), newParent);
        if (exists) {
            Message msg = new Message(Message.Type.Error, MessageKeys.DUPLICATE_SCOPE, movedScope.getName());
            super.addMessage(msg, msg);
        } else {
            if (ScopeBean.doesScopeExistInDestinationHierarchy(context, movedScope.getName(), movedScope.getDisplayName(), newParent)) {
                Message msg = new Message(Message.Type.Warn, MessageKeys.SCOPE_EXISTS_IN_DESTINATION_HIERARCHY);
                super.addMessage(msg, msg);
            }
            new ScopeService(context).moveScope(movedScope, oldParent, newParent);
        }
        
        return null;
    }
    
    /**
     * ActionListener to move a scope from one parent to another.
     */
    public void moveScopeOld(ActionEvent e) throws GeneralException {
        
        SailPointContext context = getContext();
        Scope oldParent = null;
        Scope newParent = null;
        Scope movedScope = null;

        String oldParentId = super.getNodeId(this.oldParentScopeId);
        String newParentId = super.getNodeId(this.newParentScopeId);
        String movedId = super.getNodeId(this.movedScopeId);

        if (null != oldParentId) {
            oldParent = context.getObjectById(Scope.class, oldParentId);
        }

        if (null != newParentId) {
            newParent = context.getObjectById(Scope.class, newParentId);
        }

        movedScope = context.getObjectById(Scope.class, movedId);

        new ScopeService(context).moveScope(movedScope, oldParent, newParent);
    }

    /**
     * Action to create a new scope under another scope.
     */
    public String newScope() throws GeneralException {
        ScopeBean.saveNewScopeParentId(getNodeId(this.parentId), this);
        return "newScope";
    }
    
    /**
     * Action to edit a scope.
     */
    public String editScope() throws GeneralException {
        ScopeBean.saveSelectedScopeId(this.selectedScopeId, this);
        return "editScope";
    }
    
    /**
     * Action to delete a scope.
     */
    public String deleteScope() throws GeneralException {
        ScopeBean.saveSelectedScopeId(this.selectedScopeId, this);
        return "deleteScope";
    }

    /**
     * Action to go to the configure scopes page.
     */
    public String configureScopes() {
        return "configureScopes";
    }
}
