/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.BidirectionalCollection;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.ArrayList;
import java.util.List;


/**
 * A Scope is a container within the system that in which any SailPointObject
 * can live (via an assigned scope). This is used to scope access to objects so
 * that on a user that controls a scope can see the objects in that scope.
 */
@XMLClass
public class Scope extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The display name of this scope. Not required - default to the
     * name if not specified.
     */
    private String displayName;

    /**
     * The sub-scopes in the scope hierarchy.
     */
    private List<Scope> childScopes;

    /**
     * A back-pointer to the parent scope (inverse of childScopes).
     */
    private Scope parent;

    /**
     * Whether this scope was manually created in the user interface.  If so,
     * it is not marked as dormant.
     */
    private boolean manuallyCreated;

    /**
     * Whether this scope is dormant. This means that 
     * the scope was auto-created but it is no longer referenced by any objects.
     */
    private boolean dormant;

    /**
     * A string representation of this scopes location in the scope hierarchy.
     * This is used for searching for objects that exist in a sub-tree of the
     * scope hierarchy by allowing queries that match a given prefix. If A has
     * a sub-scope B, and B has a sub-scope C, their respective paths would be
     * "A", "A:B", "A:B:C".  To search for any objects in the B or any of its
     * sub-scopes you could look for "scope.path like 'A:B%'". This will
     * actually store IDs rather than names so that we can limit the length to
     * use 32 characters per level in the hierarchy and so it is immune to
     * renames. This needs to be an indexed field and 
     * fairly deep hierarchies might need to be supported. SQL Server has a 900 byte 
     * index limitation, which would quickly be exceeded by a hierarchy with long scope names.
     */
    private String path;

    /**
     * Flag that is set to true when a scopes path changes. This triggers path
     * denormalization for all SailPointObjects that have an assigned scope in
     * the subtree rooted at this scope.
     */
    private boolean denormalizationPending;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public Scope() {
        super();

        // Every scope has itself as an assigned scope by default.
        setAssignedScope(this);
    }

    /**
     * Create a scope with a name.
     */
    public Scope(String name) {
        this();

        setName(name);

        // Default the display name so we can sort by this and display it in
        // the UI and don't have to worry about whether to use name or display
        // name.
        setDisplayName(name);
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The display name of this scope. Not required - default to the
     * name if not specified.
     */
    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * The sub-scopes in the scope hierarchy.
     */
    @BidirectionalCollection(elementClass=Scope.class, elementProperty="parent")
    public List<Scope> getChildScopes() {
        return childScopes;
    }

    public void setChildScopes(List<Scope> childScopes) {
        this.childScopes = childScopes;
    }

    /**
     * @exclude
     * Use a different getter/setter for XML persistence so we will set the
     * parent when the child scopes are set.
     * @deprecated Use {@link #getChildScopes()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST, xmlname="ChildScopes")
    public List<Scope> getXmlChildScopes() {
        return this.childScopes;
    }

    /**
     * @exclude
     * @deprecated Use {@link #setChildScopes(java.util.List)} 
     */
    @Deprecated
    public void setXmlChildScopes(List<Scope> scopes) {
        this.childScopes = scopes;
        
        if (null != scopes) {
            for (Scope scope : scopes) {
                scope.setParent(this);
            }
        }
    }

    public void addScope(Scope scope) {
        if (null != scope) {
            if (null == this.childScopes) {
                this.childScopes = new ArrayList<Scope>();
            }
            this.childScopes.add(scope);
            scope.setParent(this);

            // Also, recalculate the paths for the added scope and all children.
            // This will change because of the hierarchy change.
            scope.updateSubtreePaths();
        }
    }

    public void removeScope(Scope scope) {
        if (null != scope) {
            if (null != this.childScopes) {
                this.childScopes.remove(scope);
            }
            scope.setParent(null);

            // Also, recalculate the paths for the removed scope and all children.
            // This will change because of the hierarchy change.
            scope.updateSubtreePaths();
        }
    }

    /**
     * A back-pointer to the parent scope (inverse of childScopes).
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Scope getParent() {
        return parent;
    }

    /**
     * @exclude
     * Do not call this from outside code - this used internally
     *  hibernate to maintain a bidirectional collection.  
     * @deprecated use {@link #addScope(Scope)}, which manages the parent-child relationship.
     */
    @Deprecated
    public void setParent(Scope parent) {
        this.parent = parent;
    }

    /**
     * True if the scope is dormant. This means that 
     * the scope was auto-created but it is no longer referenced by any objects.
     */
    @XMLProperty
    public boolean isDormant() {
        return dormant;
    }

    public void setDormant(boolean dormant) {
        this.dormant = dormant;
    }

    /**
     * True if the scope was manually created in the user interface. If so,
     * it is not marked as dormant.
     */
    @XMLProperty
    public boolean isManuallyCreated() {
        return manuallyCreated;
    }

    public void setManuallyCreated(boolean manuallyCreated) {
        this.manuallyCreated = manuallyCreated;
    }
    
    /**
     * A string representation of this scopes location in the scope hierarchy.
     * This is used for searching for objects that exist in a sub-tree of the
     * scope hierarchy by allowing queries that match a given prefix.  If A has
     * a sub-scope B, and B has a sub-scope C, their respective paths would be
     * "A", "A:B", "A:B:C".  To search for any objects in the B or any of its
     * sub-scopes you could look for "scope.path like 'A:B%'". This will
     * actually store IDs rather than names so that we can limit the length to
     * use 32 characters per level in the hierarchy and so it is immune to
     * renames. This needs to be an indexed field and 
     * fairly deep hierarchies might need to be supported. SQL Server has a 900 byte 
     * index limitation, which would quickly be exceeded by a hierarchy with long scope names.
     */
    @XMLProperty
    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    /**
     * True when a scopes path changes. This triggers path
     * denormalization for all SailPointObjects that have an assigned scope in
     * the subtree rooted at this scope.
     */
    @XMLProperty
    public boolean isDenormalizationPending() {
        return this.denormalizationPending;
    }
    
    public void setDenormalizationPending(boolean b) {
        this.denormalizationPending = b;
    }

    /**
     * An upgrade pseudo-property to convert the older "dirty" property
     * into the 6.1 "needsDenormalizaton" property.
     * @deprecated use {@link #isDenormalizationPending()}
     */
    @Deprecated
    @XMLProperty(xmlname="dirty")
    public boolean isXmlDirty() {
        return false;
    }

    /**
     * @deprecated use {@link #setDenormalizationPending(boolean)} 
     */
    @Deprecated
    public void setXmlDirty(boolean b) {
        setDenormalizationPending(b);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return either the displayName or name (if displayName is null).
     */
    public String getDisplayableName() {
        return (null != this.displayName) ? this.displayName : super.getName();
    }

    /**
     * Calculate the path String for this scope in the scope hierarchy.
     * 
     * @throws IllegalStateException  If the ID has not yet been set.
     */
    private String calculatePath() throws IllegalStateException {
        
        if (null == getId()) {
            throw new IllegalStateException("Scope already should have been saved when calculatePath() is called.");
        }
        
        StringBuilder sb = new StringBuilder();

        if (null != this.parent) {
            // This calculates the parent's path if not yet set.  Don't know if
            // we need this or not.
            if (null == this.parent.getParent()) {
                this.parent.setPath(this.parent.calculatePath());
            }
            sb.append(this.parent.getPath()).append(':');
        }

        // Use IDs for the scope path since they are unique and are always 32
        // hex digits or less.  This allows 14 levels of hierarchy on a UTF-8
        // oracle database, or 28 levels on a non-UTF-8 (actually probably need
        // to subtract one level for the separators).
        sb.append(getId());
    
        return sb.toString();
    }

    /**
     * Recalculate and set the paths on this scope and all descendant scopes.
     * This should be called if a scopes position in the hierarchy changes.
     */
    public void updateSubtreePaths() {
        
        this.setPath(this.calculatePath());

        if (null != this.childScopes) {
            for (Scope child : this.childScopes) {
                child.updateSubtreePaths();
            }
        }
    }

    /**
     * Return a friendly path string in the form of "Parent:Child:Grandchild".
     */
    public String getDisplayablePath() {
        Scope scope = this;

        StringBuilder path = new StringBuilder();
        String sep = "";

        do {
            path.insert(0, scope.getDisplayableName() + sep);
            sep = ":";
            scope = scope.getParent();
        } while (null != scope);

        return path.toString();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isNameUnique() {
        return false;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitScope(this);
    }

    /**
     * The properties on this object that determine uniqueness.
     */
    private static final String[] UNIQUE_KEY_PROPERTIES =
        new String[] { "parent", "name" };

    @Override
    public String[] getUniqueKeyProperties() {
        return UNIQUE_KEY_PROPERTIES;
    }


}
