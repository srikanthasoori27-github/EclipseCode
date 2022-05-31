/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Service to deal with scopes.  While the Scoper is more aimed at scope
 * services as they relate to identities, this provides methods to deal with
 * scopes from a more interactive perspective.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.TaskSchedule;
import sailpoint.server.ResultScoper;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;


/**
 * Service to deal with scopes. While the Scoper is more aimed at scope
 * services as they relate to identities, this provides methods to deal with
 * scopes from a more interactive perspective.
 */
public class ScopeService {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * DeletionOptions provide information about what to do with scope
     * references when a scope is deleted.
     */
    public static class DeletionOptions {
        
        private Scope assignedScopeReplacement;
        private Scope controlledScopeReplacement;
        private boolean deleteChildren;
        
        /**
         * Constructor.
         * 
         * @param assignedScopeReplacement
         *     The Scope to use to replace assigned scope references.
         * @param controlledScopeReplacement
         *     The Scope to use to replace controlled scope references.
         * @param deleteChildren
         *     Whether to delete the children of this scope. If false, the
         *     children are added to the parent scope.
         */
        public DeletionOptions(Scope assignedScopeReplacement,
                               Scope controlledScopeReplacement,
                               boolean deleteChildren) {

            this.assignedScopeReplacement = assignedScopeReplacement;
            this.controlledScopeReplacement = controlledScopeReplacement;
            this.deleteChildren = deleteChildren;
        }

        public Scope getAssignedScopeReplacement() {
            return this.assignedScopeReplacement;
        }

        public Scope getControlledScopeReplacement() {
            return this.controlledScopeReplacement;
        }

        public boolean deleteChildren() {
            return this.deleteChildren;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private SailPointContext context;

    private boolean terminateDenormalization;

    /**
     * So we can report back to the task results page.
     */
    TaskMonitor _monitor;
    
    /**
     * Enables trace messages to the console.
     */
    private boolean _trace;
    
    private static Log log = LogFactory.getLog(ScopeService.class);
        
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public ScopeService(SailPointContext context) {
        this.context = context;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    
    /**
     * This does the some necessary processing to correctly save a scope. This
     * commits the transaction.
     * 
     *  @param scope The scope to save
     */
    public void save( Scope scope ) throws GeneralException {
        context.saveObject( scope );
        scope.updateSubtreePaths();
        scope.setAssignedScope( scope.getAssignedScope() );
        context.commitTransaction();
    }
    
    /**
     * Move the given scope from being a child of the oldParent to the
     * newParent.
     * 
     * @param  toMove     The Scope to move.
     * @param  oldParent  The previous parent of the scope being moved (null if
     *                    toMove was a root scope).
     * @param  newParent  The new parent of the scope (null if toMove is to
     *                    become a root scope).
     */
    public void moveScope(Scope toMove, Scope oldParent, Scope newParent)
        throws GeneralException {
        
        if (null != toMove) {
            if (null != oldParent) {
                oldParent.removeScope(toMove);
                this.context.saveObject(oldParent);
            }

            if (null != newParent) {
                newParent.addScope(toMove);
                this.context.saveObject(newParent);
            }

            // Mark this as a dirty scope so we will denormalize later.
            queueDirtyScope(toMove, true);

            this.context.saveObject(toMove);
            this.context.commitTransaction();
        }
    }

    /**
     * Delete the given scope using the given DeletionOptions to clean up
     * references.
     * 
     * @param  scope    The Scope to delete.
     * @param  options  Options that describe how to deal with references to
     *                  this scope.
     */
    public void deleteScope(Scope scope, DeletionOptions options)
        throws GeneralException {

        // Clear all references from controlled scope and possibly replace them
        // with the replacement in the DeletionOptions.
        List<Scope> scopes = new ArrayList<Scope>();
        scopes.add(scope);
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.containsAll("controlledScopes", scopes));
        Iterator<Identity> results = context.search(Identity.class, qo);
        if (null != results) {
            while (results.hasNext()) {
                Identity id = results.next();
                id.removeControlledScope(scope);

                if (null != options.getControlledScopeReplacement()) {
                    id.addControlledScope(options.getControlledScopeReplacement());
                }

                context.saveObject(id);
            }
        }
        context.commitTransaction();
        context.decache();
        scope = context.getObjectById(Scope.class, scope.getId());
        
        // Clear all references from assigned scope.
        // KG - All SailPointObjects can have an assigned scope ... we need to
        // search for all instances of SailPointObject (regardless of type) to
        // find any references.  We're not using hibernate inheritance that
        // allows for polymorphic queries from SailPointObject, so we'll have to
        // explicitly march through all SailPointObject subclasses and search
        // for assigned scopes.  There is not a good way in java to find all
        // subclasses of SailPointObject.  The only way I have found is to
        // determine either the directory on the filesystem with the classes or
        // the jar, and march through them all loading them one by one.  This is
        // painful and error prone.  Instead, we'll use the fact that all
        // SailPointObjects are XML classes and therefore are registered in
        // XMLClasses.MF.  We'll walk through these and find all classes that
        // inherit from SailPointObject.
        List<Class<? extends SailPointObject>> spClasses = getSailPointObjectClasses();
        for (Class<? extends SailPointObject> clazz : spClasses) {

            qo = new QueryOptions();
            qo.add(Filter.eq("assignedScope", scope));

            Iterator<? extends SailPointObject> objs = context.search(clazz, qo);
            if (null != objs) {
                while (objs.hasNext()) {
                    SailPointObject o = objs.next();
                    o.setAssignedScope(options.getAssignedScopeReplacement());
                    context.saveObject(o);
                    // TODO: decache occassionally?  maybe bulk update?
                }
            }
            context.commitTransaction();
            context.decache();
            scope = context.getObjectById(Scope.class, scope.getId());
        }

        // references have now been pruned, splice this scope out 
        // of the hierarcy, remember the children so we can deal with them later
        // have to be careful here because recursively calling deleteScope
        // may decache
        
        List<Scope> children = scope.getChildScopes();
        if (children != null && children.size() > 0) {
            // Hibernate is spooky about list ownership, make a copy and
            // clear out the old one rather than nulling
            List<Scope> children2 = new ArrayList<Scope>(children);
            children.clear();

            // have to do this too or will Hibenrate figure it out?
            //for (Scope child : children)
            //chid.setParent(null);

            children = children2;
        }

        // Remove this scope from its parent if it is a child
        Scope parent = scope.getParent();
        if (parent != null)
            parent.removeScope(scope);

        context.removeObject(scope);
        context.commitTransaction();

        // Either delete the children or flatten them onto the parent.
        if (children != null && children.size() > 0) {

            if (options.deleteChildren()) {
                for (Scope child : children) {
                    // calling deleteScope recursievely will decache so have
                    // to refetch
                    child = context.getObjectById(Scope.class, child.getId());
                    deleteScope(child, options);
                }
            }
            else {
                for (Scope child : children) {
                    if (parent != null)
                        parent.addScope(child);
                    // this will commit, could defer...
                    queueDirtyScope(child, true);
                }

                // everything should be committed by queueDirtyScope but make sure
                context.commitTransaction();
            }
        }
    }

    private List<Class<? extends SailPointObject>> getSailPointObjectClasses()
        throws GeneralException {
        
        List<Class<? extends SailPointObject>> spClasses =
            new ArrayList<Class<? extends SailPointObject>>();
    
        List<Class<?>> xmlClasses = XMLObjectFactory.getAnnotatedClasses();
        for (Class<?> clazz : xmlClasses) {
            if (SailPointObject.class.isAssignableFrom(clazz)) {
                // Don't add this if it is only persisted as XML.
                Class<? extends SailPointObject> spClass =
                    clazz.asSubclass(SailPointObject.class);
    
                try {
                    SailPointObject obj = spClass.newInstance();
                    if (!obj.isXml() && obj.hasAssignedScope()) {
                        spClasses.add(spClass);
                    }
                }
                catch (Exception e) {
                    throw new GeneralException(e);
                }
            }
        }
        
        return spClasses;
    }

    /**
     * Queue this scope as a dirty scope.  Note - this commits the transaction.
     */
    public void queueDirtyScope(Scope scope, boolean commit)
        throws GeneralException {
        
        scope.setDenormalizationPending(true);

        // Mark a system config setting as "scopes dirty" so we won't try to
        // use denormalized paths during searching.  Lock, so that we don't have
        // a race condition with denormalizePaths.
        Configuration config = ObjectUtil.transactionLock(this.context, Configuration.class, 
                                                          Configuration.OBJ_NAME);
        // in some special cases in the unit tests the config object
        // not be loaded yet
        if (config != null) {
            config.put(Configuration.SCOPE_PATHS_DENORMALIZED, false);
            this.context.saveObject(config);

            if (commit) {
                // Commit so the transaction lock is released.
                this.context.commitTransaction();
            }
        }
    }

    /**
     * Terminate the scope denormalization process when we get to a good
     * stopping point.
     */
    public void terminateDenormalization() {
        this.terminateDenormalization = true;
    }

    /**
     * If there are any scope changes that affect the denormalized paths on
     * SailPointObjects, push these out to all objects.
     * 
     * @return The number of scopes that were denormalized.
     */
    public int denormalizePaths() throws GeneralException {

        int count = 0;
        
        // If there are any dirty scopes queued, update the denormalized
        // paths in all SailPointObjects.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("denormalizationPending", true));
        List<Scope> dirty = this.context.getObjects(Scope.class, qo);
        int scopeTotal = dirty.size();

        if (null != dirty) {
            for (Scope scope : dirty) {
                if (this.terminateDenormalization) {
                    continue;
                }

                updateProgress("Denormalizing Scope " + (count+1) + " of " + scopeTotal + ": " + scope.getDisplayableName());
                denormalizeSubtreePaths(scope);
                // Not dirty anymore.
                scope.setDenormalizationPending(false);
                this.context.saveObject(scope);
                this.context.commitTransaction();

                count++;
            }
        }
        
        // Now that we're all denormalized, change the system config if there
        // are no more dirty scopes.  We'll check again in case any scopes
        // became dirty while we were denormalizing.
        if (0 == this.context.countObjects(Scope.class, qo)) {

            // Lock the config object to avoid race condition with
            // queueDirtyScope.
            Configuration config = ObjectUtil.transactionLock(this.context, Configuration.class, 
                                                              Configuration.OBJ_NAME);
            if (config != null) {
                config.put(Configuration.SCOPE_PATHS_DENORMALIZED, true);
                this.context.saveObject(config);
                this.context.commitTransaction();
            }
        }

        return count;
    }
    
    /**
     * Generates a Filter to find objects in the specified user's assigned scope 
     * @param user Identity whose assigned scope is being applied
     * @return Filter that finds objects in the specified user's assigned scope or
     * null if scoping is disabled
     * @deprecated Use {@link #getAssignedScopeQueryInfo(Identity)} instead
     */
    @Deprecated
    public Filter getAssignedScopeFilter(Identity user) throws GeneralException {
        Identity userToScope;
        if (user.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
            userToScope = user;
        } else {
            // Dummy up an Identity that will "trick" the scoper into delivering an appropriate Filter
            userToScope = new Identity();
            Scope userScope = user.getAssignedScope();
            if (userScope != null) {
                userToScope.setControlledScopes(Arrays.asList(new Scope[] {userScope}));
            }
        }
        QueryOptions forceScoping = new QueryOptions();
        forceScoping.setScopeResults(true);
        ResultScoper resultScoper = new ResultScoper(context, userToScope, forceScoping);
        Filter f = resultScoper.getScopeFilter();
        return f;
    }
    
    /**
     * Generates a QueryInfo representing a query that finds objects in the specified user's assigned scope 
     * @param user Identity whose assigned scope is being applied
     * @return QueryInfo representing a query that finds objects in the specified user's assigned scope
     */
    public QueryInfo getAssignedScopeQueryInfo(Identity user) throws GeneralException {
        Identity userToScope;
        if (user.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
            userToScope = user;
        } else {
            // Dummy up an Identity that will "trick" the scoper into delivering an appropriate Filter
            userToScope = new Identity();
            Scope userScope = user.getAssignedScope();
            if (userScope != null) {
                userToScope.setControlledScopes(Arrays.asList(new Scope[] {userScope}));
            }
        }
        QueryOptions forceScoping = new QueryOptions();
        forceScoping.setScopeResults(true);
        ResultScoper resultScoper = new ResultScoper(context, userToScope, forceScoping);
        QueryInfo qi = new QueryInfo(resultScoper.getScopeFilter(), !resultScoper.isReturnAnyResults());
        return qi;
    }
    
    /**
     * Generates a Filter to find objects in the specified user's controlled scopes 
     * @param user Identity whose controlled scopes are being applied
     * @return Filter that finds objects in the specified user's controlled scopes or 
     * null if scoping is disabled
     * @deprecated Use {@link #getControlledScopesQueryInfo(Identity)} instead
     */
    @Deprecated
    public Filter getControlledScopesFilter(Identity user) throws GeneralException {
        QueryOptions forceScoping = new QueryOptions();
        forceScoping.setScopeResults(true);
        ResultScoper resultScoper = new ResultScoper(context, user, forceScoping);
        Filter f = resultScoper.getScopeFilter();
        return f;
    }
    
    /**
     * Generates a QueryInfo representing a query that finds objects in the specified user's controlled scopes 
     * @param user Identity whose controlled scopes are being applied
     * @return QueryInfo representing a query that finds objects in the specified user's controlled scopes
     */
    public QueryInfo getControlledScopesQueryInfo(Identity user) throws GeneralException {
        QueryOptions forceScoping = new QueryOptions();
        forceScoping.setScopeResults(true);
        ResultScoper resultScoper = new ResultScoper(context, user, forceScoping);
        QueryInfo qi = new QueryInfo(resultScoper.getScopeFilter(), !resultScoper.isReturnAnyResults());
        return qi;        
    }
    
    /**
     * Applies the system-wide scope settings for the given user to the SailPointContext
     * @param userBeingScoped - User whose controlled scopes are potentially being applied
     */
    public void applyScopingOptionsToContext(Identity userBeingScoped) {
        boolean scopeResults;
        // Ignore scoping for system administrators
        if (userBeingScoped != null) {
            scopeResults = !userBeingScoped.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
            if (scopeResults) {
                Configuration sysConfig = Configuration.getSystemConfig();
                if (sysConfig == null) {
                    scopeResults = false;
                } else {
                    scopeResults = Util.otob(sysConfig.get(Configuration.SCOPING_ENABLED));
                }
            }
        } else {
            // Assume unit tests
            scopeResults = true;
        }
        context.setScopeResults(scopeResults);
    }

    /**
     * Determines whether the given scope is controlled by the specified user
     * @param user
     * @param scope
     * @return
     */
    public boolean controlsScope(Identity user, Scope scope) throws GeneralException {
        Set<Scope> controlledScopes = new HashSet<Scope>(user.getEffectiveControlledScopes(Configuration.getSystemConfig()));
        return controlledScopes.contains(scope);
    }

    /**
     * Convenience method for determining whether or not scoping is enabled on this system
     * @return true if scoping is enabled on this system; false otherwise
     */
    public boolean isScopingEnabled() {
        Configuration sysConfig = Configuration.getSystemConfig();
        return Util.otob(sysConfig.get(Configuration.SCOPING_ENABLED));
    }

    public boolean isUnscopedGloballyAccessible() {
        Configuration sysConfig = Configuration.getSystemConfig();
        return Util.otob(sysConfig.get(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE));
    }

    
    /**
     * Denormalize the path for all objects that have the given scope or any
     * scope in this scope's subtree as an assigned scope. Note that this
     * commits the transaction.
     */
    private void denormalizeSubtreePaths(Scope scope) throws GeneralException {

        // This could update A LOT of objects, so we have a few options.
        //
        // 1) Query and update using our normal PersistenceManager - just be
        //    careful to flush judiciously.
        // 2) Attempt to use Hibernate's StatelessSession so we don't go
        //    through the entire persistent object lifecycle (mark as dirty,
        //    flush, notify listeners, etc...).
        // 3) Use hibernates DML-style updates.  The downside here is that
        //    you can't join in the "SET" part of the statement, so we would
        //    have to run one update per scope in the subtree.  Another
        //    downside is that this definitely won't work for TaskSchedules
        //    since these go through the QuartzPersistenceManager.  The
        //    upside is that there is no object hydrating - it's all done on
        //    the database - so this is likely to be the fastest of the
        //    three options.
        //
        // Note that options 2 and 3 don't interact with the hibernate cache
        // at all, so we can get objects in the Session cache (and 2nd level
        // cache) that have invalid denormalized paths.  This isn't too bad,
        // though, since the denormalized path is really only used for
        // querying and should never be retrieved programatically.
        //
        // For now, we'll stick with option 1 since it is the most straight-
        // forward and is already implemented in the persistence layer.  May
        // need to consider moving away from this if we see performance
        // issues later.

        List<Class<? extends SailPointObject>> classes = getSailPointObjectClasses();

        try {
            // Impersonate a user with this scope as a controlled scope, so
            // searches will return all objects in this scope's subtree.
            Identity impersonator = new Identity();
            impersonator.addControlledScope(scope);
            this.context.impersonate(impersonator);

            // Turn on scoping, but don't return objects that don't have a scope.
            QueryOptions qo = new QueryOptions();
            qo.setScopeResults(true);
            qo.setUnscopedGloballyAccessible(false);
            qo.add(Filter.notnull("assignedScope"));
            qo.setCloneResults(true);
            
            for (Class<? extends SailPointObject> clazz : classes) {
                if (this.terminateDenormalization) {
                    continue;
                }
                //This won't work with QuartzPersistenceManager/TaskSchedules, as it only supports certain filters in the search(class, ops, props) api -rap
                Iterator it;
                boolean containsObjects = false;

                if (clazz == TaskSchedule.class) {
                    //Must use this search, and return iterator of TaskSchedules because search(class, ops, props) does not support these filters
                   it = this.context.search(clazz, qo);
                   containsObjects = true;
                } else {
                    it = this.context.search(clazz, qo, Arrays.asList("id"));
                }

                if (null != it) {
                    List<SailPointObject> toDecache = new ArrayList<SailPointObject>();
                    int count = 0;
                    while (it.hasNext()) {
                        if (this.terminateDenormalization) {
                            continue;
                        }
                        SailPointObject o = null;
                        if (containsObjects) {
                            o = (SailPointObject)it.next();
                        } else {
                            String id = (String) ((Object[])it.next())[0];
                            o = this.context.getObjectById(clazz, id);
                        }
                        // This query returns any objects in the scope sub-tree,
                        // so make sure to denormalize based on the assigned
                        // scope of the object - not the one used in the search.
                        o.setAssignedScopePath(o.getAssignedScope().getPath());
                        this.context.saveObject(o);
                        toDecache.add(o);

                        // Every 100 (or on the last iteration), commit and
                        // decache objects that haven't yet been decached.
                        if ((++count % 100) == 0 || !it.hasNext()) {
                            this.context.commitTransaction();
                            for (SailPointObject so : toDecache) {
                                this.context.decache(so);
                            }
                            toDecache.clear();
                        }
                    }
                }
            }
        }
        finally {
            // Hey funny guy, no more impersonations!!
            this.context.impersonate(null);
        }
    }

    /**
     * Set the TaskMonitor to update progress on in {@link #denormalizePaths()}.
     */
    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor(TaskMonitor monitor ) {
        return _monitor;
    }

    private void updateProgress(String progress) {
        trace(progress);
        if ( _monitor != null ) _monitor.updateProgress(progress);
    }
    
    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        
        if (_trace)
            println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }
    
    /**
     * Set whether to log progress to STDOUT in {@link #denormalizePaths()}.
     */
    public void setTrace(boolean b) {
        _trace = b;
    }
    
    public boolean getTrace() {
        return _trace;
    }    
}
