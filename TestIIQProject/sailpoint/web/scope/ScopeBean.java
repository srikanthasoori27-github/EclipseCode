/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * JSF bean to edit and create scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ScopeBean extends BaseScopeBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    static final String NEW_SCOPE_PARENT_ID = "newScopeParentId";

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private String newScopeParentId;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public ScopeBean() {
        super();

        // Pull this off the session.  It will be put here before transitioning
        // to this page.  If this is a post-back, this will later be set from
        // the input field.
        this.newScopeParentId =
            super.getRequestOrSessionParameter(NEW_SCOPE_PARENT_ID);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public Scope createObject() {
        return new Scope();
    }

    @SuppressWarnings("unchecked")
    static void saveNewScopeParentId(String newScopeParentId, BaseBean bean) {
        bean.getSessionScope().put(NEW_SCOPE_PARENT_ID, newScopeParentId);
    }

    /**
     * Checks if scopeName within the same parent scope where it is to be moved.
     * If it exists then scope creationg is not allowed.
     */
    public String getAllowScopeMove() throws Exception {
        
        String name = getRequestParameter("scopeName");

        Map<String, Object> vals = new HashMap<String, Object>();
        if (Util.isNullOrEmpty(name)) {
            vals.put("allow", false);
            return JsonHelper.toJson(vals);
        }

        String parentId = getRequestParameter("parentId");
        Scope parent = null;
        if (!Util.isNullOrEmpty(parentId)) {
            parent = getContext().getObjectById(Scope.class, parentId);
        }

        vals.put("allow", !doesScopeExist(getContext(), name, name, parent));

        return JsonHelper.toJson(vals);
    }
    
    /**
     * Checks if scopeName exists in another container.
     * Note that this will pass if scopeName exists in the same container.
     */
    public String getExistsInAnotherScopeJson() throws Exception {
        
        String scopeName = getRequestParameter("scopeName");

        Map<String, Object> vals = new HashMap<String, Object>();

        // TODO: tqm rename parentId to destinationScopeId
        String destinationScopeId = getRequestParameter("parentId");
        Scope destinationScope = null;
        if (!Util.isNullOrEmpty(destinationScopeId)) {
            destinationScope = getContext().getObjectById(Scope.class, destinationScopeId);
        }
        
        boolean exists = doesScopeExistInAnotherContext(getContext(), scopeName, destinationScope);
        vals.put("exists", exists);
        if (exists) {
            vals.put("parentScopeName", getExistingScopeParentScopeName(scopeName, destinationScope));
        }
        
        return JsonHelper.toJson(vals);
    }


    private String getExistingScopeParentScopeName(String scopeName, Scope parent) throws GeneralException {
        
        // if there are more than one just get the first one
        List<Scope> existingScopes = getContext().getObjects(Scope.class, createGetOtherScopesQueryOptions(scopeName, parent));
        Scope existingScope =  existingScopes.get(0);
        String parentScopeName = null;
        Scope existingScopeParent = existingScope.getParent();
        if (existingScopeParent != null) {
            parentScopeName = existingScopeParent.getDisplayName();
        }
        return parentScopeName;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getNewScopeParentId() {
        return newScopeParentId;
    }

    public void setNewScopeParentId(String newScopeParentId) {
        this.newScopeParentId = newScopeParentId;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Save a newly created scope.
     */
    public String create() throws GeneralException {

        Scope scope = super.getObject();
        
        scope.setDisplayName(WebUtil.safeHTML(scope.getDisplayName()));

        // The create/edit page deals allows editing the display name (not the
        // name), so copy it over.
        if (null == scope.getName()) {
            scope.setName(scope.getDisplayName());
        }

        if (null != Util.getString(this.newScopeParentId)) {
            Scope parent = getContext().getObjectById(Scope.class, this.newScopeParentId);
            if (!validateCreate(scope.getName(), parent)) {
                return null;
            }

            // Must save this first to get an ID before we add it to the parent.
            getContext().saveObject(scope);
            parent.addScope(scope);
            getContext().saveObject(parent);
        }
        else {
            if (!validateCreate(scope.getName(), null)) {
                return null;
            }

            // Save this first so we have an ID to calculate the path.
            getContext().saveObject(scope);
            
            // Need to update the paths explicitly if we don't have a parent.
            // If we do have a parent, these get calculated automatically when
            // calling parent.addScope() above.
            scope.updateSubtreePaths();
        }

        // Finally, set this scope as its own assigned scope to make sure that
        // the assignedScopePath gets setup correctly.  This is done in the
        // Scope constructor, but the path is null at that point.
        scope.setAssignedScope(scope);
        getContext().saveObject(scope);
        
        getContext().commitTransaction();
        
        return "scopeCreated";
    }

    /**
     * Make sure that the scope is unique within it's parent.  This adds an
     * error to the page and returns false if the new scope is not unique.
     */
    private boolean validateCreate(String scopeName, Scope parent)
        throws GeneralException {
        
        boolean valid = !doesScopeExist(getContext(), scopeName, scopeName, parent);
        if (!valid) {
            setObjectId(null);
            Message msg =
                new Message(Message.Type.Error, MessageKeys.DUPLICATE_SCOPE, scopeName);
            super.addMessage(msg, msg);
        }
        return valid;
        
    }

    static boolean doesScopeExist(SailPointContext context, String scopeName, String scopeDisplayName, Scope parent)
            throws GeneralException {
        return doesScopeExist(context, scopeName, scopeDisplayName, parent, null);
    }
    
    static boolean doesScopeExist(SailPointContext context, String scopeName, String scopeDisplayName, Scope parent, String idToSkip) 
    throws GeneralException {
    
            
        // Check for a unique name within the parent.
        Filter f = Filter.or(Filter.eq("name", scopeName), Filter.eq("displayName", scopeDisplayName));
        if (parent == null) {
            f = Filter.and(f, Filter.isnull("parent"));
        }
        else {
            f = Filter.and(f, Filter.eq("parent", parent));
        }
        
        if (idToSkip != null) {
            f = Filter.and(f, Filter.ne("id", idToSkip));
        }

        QueryOptions qo = new QueryOptions();
        qo.add(f);
        
        return context.countObjects(Scope.class, qo) > 0;
    }

    static boolean doesScopeExistInDestinationHierarchy(SailPointContext context, String scopeName, String scopeDisplayName, Scope destinationScope)
        throws GeneralException {
        
        if (destinationScope == null) {
            return false;
        }
        if (scopeName.equals(destinationScope.getName()) ||
                scopeDisplayName.equals(destinationScope.getDisplayName())) {
            return true;
        }
        
        Scope parentScope = destinationScope.getParent();
        while (parentScope != null) {
            if (scopeName.equals(parentScope.getName()) ||
                    scopeDisplayName.equals(parentScope.getDisplayName())) {
                return true;
            }
            
            parentScope = parentScope.getParent();
        }
        
        return false;
    }

    static boolean doesScopeExistInAnotherContext(SailPointContext context, String scopeName, Scope destinationScope) 
        throws GeneralException {

        if (Util.isNullOrEmpty(scopeName)) {
            return false;
        }

        QueryOptions options = createGetOtherScopesQueryOptions(scopeName, destinationScope);

        return context.countObjects(Scope.class, options) > 0;
        
    }

    private static QueryOptions createGetOtherScopesQueryOptions(String scopeName, Scope destinationScope) {
        
        QueryOptions options = new QueryOptions();
        Filter filter = Filter.eq("name", scopeName);
        Filter parentFilter;
        if (destinationScope == null) {
            // check for duplicates in achild context
            parentFilter = Filter.notnull("parent");
        } else {
            // Either parent is null (root level)
            parentFilter = Filter.isnull("parent");
            // or parent is in another scope
            parentFilter = Filter.or(parentFilter, Filter.ne("parent", destinationScope));
        }
        filter = Filter.and(filter, parentFilter);
        
        options.addFilter(filter);
        return options;
    }
    
    /**
     * Edit an existing scope.
     */
    public String edit() throws GeneralException {
        
        Scope scope = super.getObject();
        
        scope.setDisplayName(WebUtil.safeHTML(scope.getDisplayName()));
        
        boolean valid = !doesScopeExist(getContext(), scope.getName(), scope.getDisplayName(), scope.getParent(), scope.getId());
        if (!valid) {
            Message msg =
                new Message(Message.Type.Error, MessageKeys.DUPLICATE_SCOPE);
            super.addMessage(msg, msg);
            return null;
        }
        
        getContext().saveObject(scope);
        getContext().commitTransaction();
        return "scopeEdited";
    }
}
