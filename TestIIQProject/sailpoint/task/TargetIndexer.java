/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that builds TargetAssociations for roles and groups representing the 
 * indirect access granted by the entitlements in the role or group.  
 *
 * Author: Jeff
 *
 * The initial use case for this was to be able to request roles and groups by searching
 * for a specific SAP Tcode.  This led to the extension of the TargetAssociation
 * model to allow more flexible associations beyond just file shares.  
 *
 *
 * ROLE INDEXING
 * 
 * The role indexer works as follows:
 *
 *    1) calculate the flattened list of role entitlements
 *    2) isolate the entitlements that are aggregated (have a ManagedAttribute)
 *       and whose Schema as one or more attributes marked as indexed.
 *    3) For each value of the indexed attribute, create a TargetAssociation
 *        with the value and a reference back to the role.
 *
 * Questions:
 *
 * What about disabled roles?  You can't request them so building out
 * targets doesn't do anything for LCM, may be useful for something custom though?
 *
 * What about permitted roles?  Assuming we include them since LCM will provide
 * the option to ask for them.  It isn't clear to the user however which permit
 * will give them the target.  May be some UI issues.
 *
 * Think about more intelligent traversal so we don't keep reflattening the
 * same hierarchy of roles over and over.  It may make sense to build out something
 * like CorrelationModel, or even just extend that.  The problem is that we
 * don't want to rewrite AssignmentExpander and it doesn't know how to use
 * pre-calculated hierarchy plan fragments.
 * 
 *
 * GROUP HIERARCHIES    
 *
 * There are two forms of hierarchy: parent hierarchy and child hierarchy.
 *
 * The ManagedAttribute as a very unfortunately named "inheritance" property
 * that can contain a list of other ManagedAttributes arranged in a hierarchy.
 * Until 7.1 this hiearchy didn't have a direction, it was called "inheritance"
 * because it was assumed the objects referenced were parent objects who contributed
 * things to the child object, similar to the way inheritance works in the role
 * model or in Java.  One example of this is ActiveDirectory-Direct whose "group"
 * schema has a hierarchy attribute named "memberOf", conteaining the other groups
 * a group is a member of.
 *
 * For other connectors the hierarchy is not an inheritance relationship, the hierachy
 * attribute contains a list of child objects that receive things from the parent object.  
 * Although named "inheritance" it would logically be named "children" or "members".
 * One example is in SAP-Direct where the schema "profile" has an attribute named 
 * "Child Profiles".  
 *
 * Until 7.1 we didn't really care about the hierarchy direction, the parent or child
 * objects were simply displayed in a click-through grid without mentioning what
 * they meant.  But for flattening indirect entitlements we must know the direction,
 * the Schema.childHierarchy flag was added to indiciate that.
 *
 * GROUP INDEXING - PARENT HIERARCHY
 *
 * Flattening a parent hierarchy is relatively straightforward.  Each ManagedAttribute
 * is considered one at a time in random order.  For each object on the inheritance
 * list we recursively flatten the parent objects.  During recursion we keep a "bucket"
 * that accumulates the targets from everything above in the hierarchy.  Since the same
 * parent may be encountered multiple times during traversal, to improve performance
 * we keep a table of the objects already flattened so we don't analyze them more than once.
 *
 * While the recursion has the potential to bring a large number of MAs into memory, it
 * tends to be well behaved since you are only traversing up through parents, you do not
 * bring in peers.  You will have as many objects on the stack as you are deep in the hierarchy,
 * hierarchies tend to be tree-ish with modest depth with a small number of roots.
 * 
 * GROUP INDEXING - CHILD HIERARCHY
 *
 * Flattening a child hierarchy is more complicated.  You can't simply recursively
 * traverse the hierarchy list "pushing" targets down because each child cannot know all
 * of the other parents that contribute to it without searching form them and merging their
 * contributions.  Knowing the full set of contributions for each object is imprtant
 * to prevent database thrashing.  While we could simply delete all the TargetAssociations
 * and rebuild them incrementally as we encounter different parents, it's better for
 * database access if we calculate them all, then compare them to what is currently
 * in the db.  In the usual case we'll end up doing nothing in the db.
 *
 * Finding "objects which have me as a child" requires an "in" query which will be more
 * expensive than simply following a Hibernate one-to-many relationship.
 *
 * A less complicated but more memory expensive approach is to use a temporary
 * model to represent the hierarchy as a parent hierarchy, this is built in an
 * initial pass over all ManagedAttributes.  This is FlatteningState.  Once that is
 * constructed with child->parent references rather than the other way around, the parent
 * hierarchy algorighm described above can be used.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.Provisioner;
import sailpoint.api.IdIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Classifiable;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TargetAssociation.OwnerType;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TargetIndexer extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(TargetIndexer.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Argument requesting indexing of roles.
     */
    public static final String ARG_INDEX_ROLES = "indexRoleTargets";

    /**
     * Argument requesting indexing of entitlements directly on the role.
     */
    public static final String ARG_INDEX_ROLE_ENTITLEMENTS = "indexRoleEntitlements";

    /**
     * Argument requesting indexing of permissions directly on the role.
     */
    public static final String ARG_INDEX_ROLE_PERMISSIONS = "indexRolePermissions";

    /**
     * Argument requesting indexing of entitlements.
     * If not overridden by ARG_APPLICATIONS this will index all 
     * entitlements from applications that request indexing.
     */
    public static final String ARG_INDEX_ENTITLEMENTS = "indexEntitlementTargets";

    /**
     * Argument requesting the indexing of unstructured target associations.
     * This is optional and effects the behavior only when indexRoleTargets
     * or indexEntitlementTargets is on.  
     *
     * For roles this will result in the addition of unstructured target associations
     * for any targets granted through groups in this role.
     *
     * For entitlements (ManagedAttributes) it will result in hierarchy flattening since
     * the target collector will have already set up associations for the targets
     * directly referenced by a group.
     *
     * Note that in both cases you must have run the target collectors first.
     */
    public static final String ARG_INDEX_UNSTRUCTURED_TARGETS = "indexUnstructuredTargets";

    /**
     *  Argument requesting the indexing of classifications
     *  For ManagedAttributes, this will promote classifications up the hierarchy
     *  For Roles, this will result in the addition of target associations for any
     *  classifications granted through groups in this role
     */
    public static final String ARG_INDEX_CLASSIFICATIONS = "indexClassifications";

    /**
     * For Roles, this will allow promoting classifications granted through groups
     * in the role, to first class Classifications on the Role itself.
     */
    public static final String ARG_PROMOTE_CLASSIFICATIONS = "promoteClassifications";

    /**
     * Names of applications to index.  
     * The value may be just one application name, a List of names,
     * or a CSV of names.  Restricts the set of applications
     * to be indexed when ARG_INDEX_ENTITLEMENTS is on.  Primamrily for
     * the unit tests to restrict the sources to make the results predictable.
     * Not currently visible in the UI.
     */
    public static final String ARG_APPLICATIONS = "applications";

    /**
     * Names of roles to index.  
     * The value may be just one role name, a List of names,
     * or a CSV of names.  Restricts the set of roles
     * to be indexed when ARG_INDEX_ROLES is on.  Primamrily for
     * the unit tests to restrict the sources to make the results predictable.
     * Not currently visible in the UI.
     */
    public static final String ARG_ROLES = "roles";

    /**
     * Argument requesting refresh of the fulltext indexes so that
     * they include the newly indexed targets.  A convenience for
     * demos and POCs so you don't have to run two tasks or use
     * the sequential task executor.  Note that this only refreshes
     * the index on the host the task is run on, it does not refresh
     * indexes on all cluster nodes.
     */
    public static final String ARG_REFRESH_FULLTEXT = "refreshFulltext";

    /**
     * Argument requesting a full reset of all existing target
     * associations to ensure that no garbage is left behind. Normally
     * we incrementally update associations to reduce churn.
     */
    public static final String ARG_FULL_RESET = "fullReset";

    // 
    // Return values
    //

    public static final String RET_ROLES_EXAMINED = "rolesExamined";
    public static final String RET_ROLES_INDEXED = "rolesIndexed";
    public static final String RET_ENTITLEMENTS_INDEXED = "entitlementsIndexed";
    public static final String RET_TARGETS_ADDED = "targetsAdded";
    public static final String RET_TARGETS_RETAINED = "targetsRetained";
    public static final String RET_TARGETS_REMOVED = "targetsRemoved";
    public static final String RET_TARGETS_RESET = "targetsReset";
    public static final String RET_MISSING_OBJECTS = "missingObjects";

    
    //////////////////////////////////////////////////////////////////////
    //
    // ApplicationInfo
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Cache of information about applications that we need many times.
     *
     * Group Flattening
     *
     * For each Schema that has indexed attributes, create list of IndexedAttribute 
     * objects that contains a cache of display names. This is also used by
     * role flattening.
     * 
     * Role Flattening
     * 
     * For each attribute in the account schema, determine whether the type
     * of that attribute references another schema of objects that can be aggregated.
     * If those aggregated objects can have display names or target associations,
     * add an IndexedAttribute for the account attribute so role flattening knows
     * it needs attention.
     */
    private class ApplicationInfo {

        Application _application;

        Map<String,IndexedAttribute> _accountAttributes;
        
        Map<String,SchemaInfo> _schemas;

        /**
         * This is only used as a placeholder for unresolved Applications.
         * This can used if there are roles left behind that reference unresolved
         * applications that have been deleted.
         */
        public ApplicationInfo(String name) {
            if (log.isInfoEnabled()) {
                log.info("Adding application cache for unresolved application " + name);
            }
            _accountAttributes = new HashMap<String,IndexedAttribute>();
            _schemas = new HashMap<String,SchemaInfo>();
        }
        
        public ApplicationInfo(Application app) {
            if (log.isInfoEnabled()) {
                log.info("Starting application cache for " + app.getName());
            }
            
            _application = app;
            
            // non-account schemas
            // pass 1: stub out SchemaInfo objects
            _schemas = new HashMap<String,SchemaInfo>();
            for (Schema schema : Util.iterate(app.getSchemas())) {
                String type = schema.getObjectType(); 
                if (!Application.SCHEMA_ACCOUNT.equals(type)) {
                    SchemaInfo schinfo = new SchemaInfo(this, schema);
                    _schemas.put(type, schinfo);
                }
            }

            // pass 2: resolve references between them
            for (SchemaInfo schinfo : _schemas.values()) {
                schinfo.resolve();
            }
            
            // account schema cache for role indexing
            _accountAttributes = new HashMap<String,IndexedAttribute>();
            Schema schema = app.getAccountSchema();
            if (schema != null) {
                for (AttributeDefinition att : Util.iterate(schema.getAttributes())) {
                    String otype = att.getSchemaObjectType();
                    if (otype != null) {
                        SchemaInfo other = _schemas.get(otype);
                        if (other == null) {
                            // odd, this is a good a place as any to complain
                            log.warn("Application " + app.getName() + " account attribute " +
                                     att.getName() + " unresolved object type " + otype);
                        }
                        else {
                            // is there anything interesting about this?
                            if (other.hasDisplayName() || other.hasAssociations()) {

                                // yes, this account attribute needs attention
                                IndexedAttribute iatt = new IndexedAttribute(this, att, other);
                                _accountAttributes.put(att.getName(), iatt);

                                if (log.isInfoEnabled()) {
                                    List<IndexedAttribute> otheratts = other.getIndexedAttributes();
                                    if (otheratts != null) {
                                        for (IndexedAttribute oatt : otheratts) {
                                            log.info("Attribute " + att.getName() + " of type " +
                                                     otype + " needs indexing for " +
                                                     oatt.getName());
                                        }
                                    }
                                    if (other.hasIndexedPermissions()) {
                                        log.info("Attribute " + att.getName() + " of type " +
                                                 otype + " needs indexing for permissions");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        public String getId() {
            return (_application != null ? _application.getId() : "Unknown");
        }

        public String getName() {
            return (_application != null ? _application.getName() : "Unknown");
        }
        
        public Application getApplication() {
            return _application;
        }

        /**
         * Return true if there is something interesting about any of the account
         * attributes, meaning they can either have display names or associations.
         * Used during role indexing to quickly determine if we need to do 
         * additional work for an AccountRequest.
         */
        public boolean hasInterestingAccountAttributes() {
            return (_accountAttributes.size() > 0);
        }

        public IndexedAttribute getAccountAttribute(String name) {
            return _accountAttributes.get(name);
        }

        public SchemaInfo getSchemaInfo(String type) {
            SchemaInfo info = null;
            if (type != null)
                info = _schemas.get(type);
            return info;
        }

        /**
         * Return the list of IndexedAttributes for a non-account schema.
         */
        public List<IndexedAttribute> getIndexedAttributes(Schema schema) {
            List<IndexedAttribute> atts = null;
            SchemaInfo info = _schemas.get(schema.getObjectType());
            if (info != null) {
                atts = info.getIndexedAttributes();
            }
            return atts;
        }
    }

    /**
     * State remembered for one Schema.
     * Determines which attributes are indexed, and builds an IndexedAttribute
     * list containing a display name cache.
     */
    private class SchemaInfo {

        ApplicationInfo _application;
        Schema _schema;
        boolean _hasIndexedPermissions;
        boolean _hasDisplayName;
        List<IndexedAttribute> _attributes;
        
        public SchemaInfo(ApplicationInfo app, Schema src) {
            _application = app;
            _schema = src;
            _hasIndexedPermissions = src.isIndexPermissions();
            // displayAttribute is often set the same as the identityAttribute
            // ignore those
            String datt = src.getDisplayAttribute();
            _hasDisplayName = (datt != null && !datt.equals(src.getIdentityAttribute()));
        }

        /**
         * Once all SchemaInfos have been built, now we can build the 
         * IndexedAttributes.
         */
        public void resolve() {
            _attributes  = new ArrayList<IndexedAttribute>();
            for (AttributeDefinition att : Util.iterate(_schema.getAttributes())) {
                if (att.isIndexed()) {
                    // other can be null if this is just an index string attribute
                    SchemaInfo other = _application.getSchemaInfo(att.getSchemaObjectType());
                    _attributes.add(new IndexedAttribute(_application, att, other));
                }
            }

        }

        public String getObjectType() {
            return _schema.getObjectType();
        }

        public String getHierarchyAttribute() {
            return _schema.getHierarchyAttribute();
        }

        public boolean hasAssociations() {
            return (Util.size(_attributes) > 0 || _hasIndexedPermissions);
        }

        public boolean hasDisplayName() {
            return _hasDisplayName;
        }

        public boolean hasIndexedPermissions() {
            return _hasIndexedPermissions;
        }

        public List<IndexedAttribute> getIndexedAttributes() {
            return _attributes;
        }
        
    }

    /**
     * Model for an attribute whose values can have display names or have
     * TargetAssociations.
     * 
     * For non-account attributes, one of these is created for each attribute that is 
     * marked indexed.  We maintain a cache of display names for the attribute values.
     *
     * For an attribute in the account schema, one of these is created if the 
     * attribute has a schema object type that references another schema that has
     * display names or indexed attributes.  
     *
     * For account schema attributes, the display name cache is used when indexing
     * direct role entitlements, where memberOf=...dn... needs to be converted
     * to a display name when building the TargetAssociation.
     *
     * For account schema attributes, we also maintain a cache of values to 
     * TargetAssociations to speed up role indexing.  We do not currently maintain
     * that cache for ManagedAttribute indexing as it would effectively bring
     * all associations into memory.  In theory that could happen for roles too
     * so may need to make this an aging cache.
     * 
     * Attribute column issues:
     *
     * When we query for specific ManagedAttributes, we normally include the
     * attribute column in the filter since MAs are uniquely defined by the combination
     * of application/type/attribute/value.  For aggregated things we don't really need that
     * since the type is enough to disambiguate it.  This is convenient because
     * of other bugs where schema objects more than one level away from the account
     * won't even have attribute columns filled in since there is no account attribute
     * that directly references them.
     *
     * A problem that arises from this is that in theory you coud have a managed account
     * attribute, say department, that just happens to have the same name as an aggregated group.
     * If that attribute is indexed, we may resolve that to the MA for the group and give it
     * that group's display name even though departments don't have display names.  This feels
     * rare and not worth losing sleep over, the best solution would be to merge the type
     * and application columns so everything is consistently application/type/value, where
     * type may be the attribute name if it is not aggregated.
     */
    private class IndexedAttribute {

        /**
         * The Application we were built from.
         * This will usually be decached, but we only need the id when querying
         * for ManagedAttributes.
         */
        ApplicationInfo _application;

        /**
         * Name of the attribute.
         */
        String _name;

        /**
         * Schema of the objects referenced by this attribute.
         * May be null if the indexed attribute values are not aggregated.
         */
        SchemaInfo _schema;

        /**
         * True if this is the hiearchy attribute.  If so, the values
         * will not be in the attributes map, they'll be in the "inheritance"
         * Java property.  An unusual case but Eric likes unusual things.
         */
        boolean _hierarchyAttribute;

        /**
         * Cache of display names built out over time.
         */
        Map<String,String> _displayNames;
        
        /**
         * Cache of values to target associations.
         */
        Map<String,List<TargetAssociation>> _targets;

        public IndexedAttribute(ApplicationInfo app, AttributeDefinition def, SchemaInfo schema) {
            // this may be decached, but we only need it for the display name query
            _application = app;
            _name = def.getName();
            _schema = schema;
            _hierarchyAttribute = (schema != null && _name.equals(schema.getHierarchyAttribute()));
            if (_schema != null && _schema.hasDisplayName()) {
                _displayNames = new HashMap<String,String>();
            }
        }

        public String getName() {
            return _name;
        }

        // for logging
        public String toString() {
            return _name;
        }

        public String getSchemaObjectType() {
            return (_schema != null ? _schema.getObjectType() : null);
        }

        public boolean hasAssociations() {
            return (_schema != null ? _schema.hasAssociations() : false);
        }

        public List<TargetAssociation> getAssociations(String value) {
            return (_targets != null ? _targets.get(value) : null);
        }

        public boolean isHiearchyAttribute() {
            return _hierarchyAttribute;
        }
        
        public void setAssociations(String value, List<TargetAssociation> targets) {
            if (_targets == null)
                _targets = new HashMap<String,List<TargetAssociation>>();
            _targets.put(value, targets);
        }

        /**
         * Derive the name of a TargetAssociation from a value in the referencing
         * object.  This will be the value unless the attribute is a reference to 
         * another object with a schema and has a display name.
         */
        public String getDisplayName(String value)
            throws GeneralException {
            
            String dname = value;
            if (_displayNames != null) {
                dname = _displayNames.get(value);
                if (dname == null) {
                    // haven't found this one yet
                    // see class comments for why we don't include the attribute column
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("application", _application.getApplication()));
                    ops.add(Filter.eq("type", _schema.getObjectType()));
                    ops.add(Filter.eq("value", value));

                    List<ManagedAttribute> mas = _context.getObjects(ManagedAttribute.class, ops);
                    if (mas != null && mas.size() > 0) {
                        if (mas.size() > 1) {
                            log.warn("Found more than one managed attribute: " +
                                     _application.getName() + "/" + _schema + "/" +
                                     _name + "/" + value);
                        }
                        ManagedAttribute other = mas.get(0);
                        dname = other.getDisplayName();
                    }
                            
                    if (dname == null) {
                        // if after all that we still don't have one fall back to the
                        // original value
                        dname = value;
                        if (log.isInfoEnabled()) {
                            log.info("No display name for: " + value);
                        }
                    }
                            
                    if (log.isInfoEnabled()) {
                        log.info("Caching display name: " + value + ", " + dname);
                    }
                    _displayNames.put(value, dname);
                }
            }
            return dname;
        }

        /**
         * Return list of Classification Names for the given attribute. This is currently used
         * for hierarchy attributes, but could potentially be used for more in the future
         * @param value
         * @return
         * @throws GeneralException
         */
        public List<String> getClassificationNames(String value) throws GeneralException {

            List<String> classificationNames = new ArrayList<>();

            QueryOptions ops = new QueryOptions();
            // see class comments for why we don't include the attribute column
            ops.add(Filter.eq("application", _application.getApplication()));
            ops.add(Filter.eq("type", _schema.getObjectType()));
            ops.add(Filter.eq("value", value));

            List<ManagedAttribute> mas = _context.getObjects(ManagedAttribute.class, ops);
            if (mas != null && mas.size() > 0) {
                if (mas.size() > 1) {
                    log.warn("Found more than one managed attribute: " +
                            _application.getName() + "/" + _schema + "/" +
                            _name + "/" + value);
                }
                ManagedAttribute other = mas.get(0);
                classificationNames = other.getClassificationNames();
            }

            return classificationNames;
        }

    }
    
    /**
     * Return or bootstrap an ApplicationInfo.
     * This is what is used when indexing roles where the application
     * name is in an AccountRequest.
     */
    private ApplicationInfo getApplicationInfo(String name) throws GeneralException {

        ApplicationInfo info = _applicationCache.get(name);
        if (info == null) {
            Application app = _context.getObjectByName(Application.class, name);
            if (app != null) {
                info = new ApplicationInfo(app);
            }
            else {
                // hmm, could be misconfiguration somewhere, build out out by name
                // but say it doesn't have anything
                info = new ApplicationInfo(name);
            }
            _applicationCache.put(name, info);
        }
        return info;
    }

    /**
     * Return or bootstrap an ApplicationInfo.
     * This is what is used when indexing entitlements where we have a direct
     * reference to the Application object.
     */
    private ApplicationInfo getApplicationInfo(Application app) throws GeneralException {
        ApplicationInfo info = _applicationCache.get(app.getName());
        if (info == null) {
            info = new ApplicationInfo(app);
            _applicationCache.put(app.getName(), info);
        }
        return info;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // TargetAssociationBucket
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A collection of TargetAssociations with unique name filtering.
     * There are three levels of target identity, the application that
     * contains the target, the name of the schema attribute that was
     * marked indexed, and the target name (attribute value).
     *
     * For ManagedAttribute associations we don't store the application name
     * since there can only be one and it must be the same as the 
     * ManagedAttribute.  Bundle associations may span several applications
     * and must have an application name.
     *
     * We could in addition add the Schema name since in theory several
     * Schemas could have an attribute named "Tcode" that mean different things,
     * but this adds a layer of complexity we don't really need.  For things like
     * Tcodes and Permission targets, they actually woulld be the same things and
     * you would not want to duplicate them.  If they are different it is easy
     * enough for the Connector designer to give them different names.
     *
     * The TargetAssociation objects in the bucket must be either new, 
     * or already owned by the intended owner.  When flattening hierarchy
     * you must make a copy of the TargetAssociation owned by a different owner.
     * It is assumed that everything in the bucket is safe to modify and save.
     * 
     */
    private class TargetAssociationBucket {

        /**
         * The object that will own the associations.
         */
        SailPointObject _owner;

        /**
         * The application/type/name bucket.
         */
        Map<String,Map<String,Map<String,TargetAssociation>>> _appBucket;

        /**
         * List of duplicates encountered when the current associations were loaded.
         * These aren't supposed to happen but they have due to bugs so we need to 
         * detect them and clean them up.
         */
        List<TargetAssociation> _duplicates;

        /**
         * Dummy application id used when we're indexing ManagedAttribute
         * associations where we're only dealing with one application.
         */
        private static final String ANY_APP = "any";

        /**
         * Build an ownerless bucket.
         * Use this when you want to build out the "required" list or 
         * want to manage your own owner ids.
         */
        public TargetAssociationBucket() {
            reset();
        }

        /**
         * Build an owned bucket.
         * This is normally done for the bucket representing the
         * current associations.
         */
        public TargetAssociationBucket(SailPointObject owner, List<TargetAssociation> current) {
            reset();
            _owner = owner;
            addCurrent(current);
        }

        /**
         * Reset the flattening state from the last object.
         */
        public void reset() {
            _appBucket = new HashMap<String,Map<String,Map<String,TargetAssociation>>>();
            _duplicates = new ArrayList<TargetAssociation>();
        }

        /**
         * Look for an association already in the bucket.
         */
        public TargetAssociation get(String app, String type, String name) {

            TargetAssociation found = null;

            if (app == null)
                app = ANY_APP;

            Map<String,Map<String,TargetAssociation>> typeBucket = _appBucket.get(app);
            if (typeBucket != null) {
                Map<String,TargetAssociation> nameBucket = typeBucket.get(type);
                if (nameBucket != null) {
                    found = nameBucket.get(name);
                }
            }

            return found;
        }

        /**
         * Look for an association already in the bucket.
         */
        public TargetAssociation get(TargetAssociation assoc) {

            return get(assoc.getApplicationName(), assoc.getTargetType(), assoc.getTargetName());
        }

        public TargetAssociation get(String type, String name) {
            return get(null, type, name);
        }

        /**
         *
         * @param neu
         * @param old
         * @return
         */
        protected TargetAssociation changed(TargetAssociation neu, TargetAssociation old) {

            if (!_indexClassifications) {
                if (old.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS) != null) {
                    //Not indexing, but existing has classifications
                    old.setAttribute(TargetAssociation.ATT_CLASSIFICATIONS, null);
                    return old;
                }
            } else {
                //Indexing enabled, ensure they're the same
                if (!Util.orderInsensitiveEquals(Util.otol(neu.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS)),
                        Util.otol(old.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS)))) {
                    old.setAttribute(TargetAssociation.ATT_CLASSIFICATIONS, neu.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS));
                    return old;
                }
            }
            //No Change, return null
            return null;

        }

        /**
         * Add an association to the bucket, this should only be called after you
         * have checked for an existing assocation.
         */
        public void add(TargetAssociation assoc) {

            String app = assoc.getApplicationName();
            if (app == null)
                app = ANY_APP;

            Map<String,Map<String,TargetAssociation>> typeBucket = _appBucket.get(app);
            if (typeBucket == null) {
                typeBucket = new HashMap<String,Map<String,TargetAssociation>>();
                _appBucket.put(app, typeBucket);
            }

            Map<String,TargetAssociation> nameBucket = typeBucket.get(assoc.getTargetType());
            if (nameBucket == null) {
                nameBucket = new HashMap<String,TargetAssociation>();
                typeBucket.put(assoc.getTargetType(), nameBucket);
            }

            TargetAssociation found = nameBucket.get(assoc.getTargetName());

            if (found == null) {
                nameBucket.put(assoc.getTargetName(), assoc);
            }
        }

        /**
         * Remove an association from the bucket, this should only be called after you
         * have checked for an existing assocation.
         */
        public void remove(TargetAssociation assoc) {

            String app = assoc.getApplicationName();
            if (app == null)
                app = ANY_APP;

            Map<String,Map<String,TargetAssociation>> typeBucket = _appBucket.get(app);
            if (typeBucket != null) {
                Map<String,TargetAssociation> nameBucket = typeBucket.get(assoc.getTargetType());
                if (nameBucket != null) {
                    nameBucket.remove(assoc.getTargetName());
                }
            }
        }

        /**
         * Add the current set of TargetAssociations for an object to the bucket.
         * The TargetAssociation may be currently owned and must not be changed.
         * 
         * If we decide we need to support remembering multiple paths, we'll have
         * to make a copy of the TargetAssociation so we can maintain a list of
         * paths in it without modifying the parent association.
         *
         * Duplicates are not supposed to happen but they can due to bugs so detect
         * those and store in a special list for cleanup.
         */
        public void addCurrent(List<TargetAssociation> assocs) {
            if (assocs != null) {
                for (TargetAssociation assoc : assocs) {
                    TargetAssociation existing = get(assoc);
                    if (existing == null) {
                        add(assoc);
                    }
                    else {
                        _duplicates.add(assoc);
                    }
                }
            }
        }

        /**
         * Look to see if a required association is already in the bucket.
         * If it exists it is removed, if it doesn't a new one is created.
         */
        private void checkAssociation(TargetAssociation req)
            throws GeneralException {

            TargetAssociation existing = get(req);
            if (existing != null) {
                //Check to see if changed
                TargetAssociation updated = changed(req, existing);

                if (updated == null) {
                    logAssociation("Retaining", existing);
                    remove(existing);
                    _targetsRetained++;
                } else {
                    logAssociation("Updating", updated);
                    _context.saveObject(updated);
                    //Commit here?
                    remove(existing);
                    _targetsUpdated++;
                }
            }
            else {
                logAssociation("Creating", req);

                if (_owner != null) {
                    req.setObjectId(_owner.getId());
                    if (_owner instanceof Bundle)
                        req.setOwnerType(OwnerType.R.name());
                    else
                        req.setOwnerType(OwnerType.A.name());
                }
                else if (req.getObjectId() == null) {
                    // if this is an owerless bucket, you  needed to
                    // set this by now
                    throw new GeneralException("No owner specified for association bucket");
                }
                
                _context.saveObject(req);
                _targetsAdded++;
            }
        }

        /**
         * Return the list of unique associations in the bucket.
         */
        public List<TargetAssociation> getAssociations() {
            List<TargetAssociation> combined = new ArrayList<TargetAssociation>();
            Iterator<Map<String,Map<String,TargetAssociation>>> typeBuckets = _appBucket.values().iterator();
            while (typeBuckets.hasNext()) {
                Map<String,Map<String,TargetAssociation>> typeBucket = typeBuckets.next();
                Iterator<Map<String,TargetAssociation>> nameBuckets = typeBucket.values().iterator();
                while (nameBuckets.hasNext()) {
                    Map<String,TargetAssociation> nameBucket = nameBuckets.next();
                    combined.addAll(nameBucket.values());
                }
            }
            return combined;
        }

        /**
         * Remove remaining associations in the bucket.
         */
        private void removeRemaining() throws GeneralException {

            List<TargetAssociation> remainder = getAssociations();
            if (remainder != null) {
                for (TargetAssociation assoc : remainder) {
                    logAssociation("Removing", assoc);
                    _context.removeObject(assoc);
                    _targetsRemoved++;
                }
            }

            if (_duplicates != null) {
                for (TargetAssociation assoc : _duplicates) {
                    logAssociation("Removing duplicate", assoc);
                    _targetsRemoved++;
                }
            }

            reset();
        }

        /**
         * Common association logger.
         */
        private void logAssociation(String prefix, TargetAssociation assoc) {
    
            if (log.isDebugEnabled()) {
                log.debug(prefix + " TargetAssociation for " +
                          (assoc.isAttribute() ? "attribute " : "permission ") + assoc.getTargetName());
            }
        }
    
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    SailPointContext _context;
    Attributes<String,Object> _arguments;
    boolean _terminate;
    boolean _indexRoleTargets;
    boolean _indexRoleEntitlements;
    boolean _indexRolePermissions;
    boolean _indexUnstructuredTargets;
    boolean _indexClassifications;
    boolean _promoteClassifications;
    
    Map<String,ApplicationInfo> _applicationCache;

    // statistics
    
    int _rolesExamined;
    int _rolesIndexed;
    int _entitlementsIndexed;
    int _targetsAdded;
    int _targetsRetained;
    int _targetsUpdated;
    int _targetsRemoved;
    int _targetsReset;
    int _effectiveClassificationsReset;
    int _missingObjects;
    
    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public TargetIndexer() {
        _applicationCache = new HashMap<String,ApplicationInfo>();
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _terminate = true;
        return true;
    }

    /**
     * Root task executor.
     * Iterate over every Bundle and work that magic.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        _context = context;
        _arguments = args;

        // now that we have three options, "indexRoles" is misleading argument name
        _indexRoleTargets = args.getBoolean(ARG_INDEX_ROLES);
        _indexRoleEntitlements = args.getBoolean(ARG_INDEX_ROLE_ENTITLEMENTS);
        _indexRolePermissions = args.getBoolean(ARG_INDEX_ROLE_PERMISSIONS);
        _indexUnstructuredTargets = args.getBoolean(ARG_INDEX_UNSTRUCTURED_TARGETS);
        _indexClassifications = args.getBoolean(ARG_INDEX_CLASSIFICATIONS);
        _promoteClassifications = args.getBoolean(ARG_PROMOTE_CLASSIFICATIONS);
        
        try {

            // must do these first since role indexing
            // will query the associations of the MAs
            if (!_terminate && args.getBoolean(ARG_INDEX_ENTITLEMENTS)) {
                // TODO: technically this may do too much if you've given the
                // task a specific Application list
                if (args.getBoolean(ARG_FULL_RESET)) {
                    resetTargetAssociations(OwnerType.A);
                    resetClassifications(ManagedAttribute.class.getSimpleName());
                    //Commit/decache because indexing may query on these
                    _context.commitTransaction();
                    _context.decache();
                }
                indexEntitlements();
            }

            if (!_terminate && (_indexRoleTargets || _indexRoleEntitlements || _indexRolePermissions)) {
                if (args.getBoolean(ARG_FULL_RESET)) {
                    resetTargetAssociations(OwnerType.R);
                    resetClassifications(Bundle.class.getSimpleName());
                    //Commit/decache because indexing may query on these
                    _context.commitTransaction();
                    _context.decache();
                }
                indexRoles();
            }

            if (!_terminate && args.getBoolean(ARG_REFRESH_FULLTEXT)) {
                refreshFulltextIndexes();
            }
            
        }
        catch (Throwable t) {
            result.addException(t);
            log.error("Error Indexing" + t);
        }
        
        result.setAttribute(RET_ROLES_EXAMINED, Util.itoa(_rolesExamined));
        result.setAttribute(RET_ROLES_INDEXED, Util.itoa(_rolesIndexed));
        result.setAttribute(RET_ENTITLEMENTS_INDEXED, Util.itoa(_entitlementsIndexed));
        result.setAttribute(RET_TARGETS_ADDED, Util.itoa(_targetsAdded));
        result.setAttribute(RET_TARGETS_RETAINED, Util.itoa(_targetsRetained));
        result.setAttribute(RET_TARGETS_REMOVED, Util.itoa(_targetsRemoved));
        result.setAttribute(RET_TARGETS_RESET, Util.itoa(_targetsReset));
        result.setAttribute(RET_MISSING_OBJECTS, Util.itoa(_missingObjects));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Index all roles.
     */
    private void indexRoles() throws GeneralException {

        Object roleList = _arguments.get(ARG_APPLICATIONS);

        if (roleList == null) {
            // disabled is indexed, but this is rare it might be faster just
            // to load them and filter them in memory
            // TODO: Should only be asking for assignable roles!!
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("disabled", false));

            Iterator<Object[]> ids = _context.search(Bundle.class, ops, "id");
            IdIterator it = new IdIterator(_context, ids);

            while (!_terminate && it.hasNext()) {
                String id = it.next();
                Bundle role = _context.getObjectById(Bundle.class, id);
                indexRole(role);
            }
        }
        else {
            List<Bundle> roles = ObjectUtil.getObjects(_context, Bundle.class, roleList);
            for (Bundle role : Util.iterate(roles)) {
                indexRole(role);
                if (_terminate) break;
            }
        }
    }
    
    /**
     * Index one role.
     */
    private void indexRole(Bundle role) throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("Indexing role: " + role.getName());
        }

        _rolesExamined++;
        
        // flatten the role hierarchy into a ProvisioningProject
        ProvisioningProject proj = flattenRole(role);

        // derive the list of TargetAssociations needed for everythign in this role
        List<TargetAssociation> required = getRequiredAssociations(role, proj);

        if (Util.size(required) == 0) {
            // if there are none, then we can simply remove all current associations
            int count = removeCurrentAssociations(role);
            if (count > 0) {
                // consider removal of any existing ones an index event
                _rolesIndexed++;
            }
        }
        else {
            _rolesIndexed++;

            List<TargetAssociation> current = getCurrentAssociations(role);
            TargetAssociationBucket bucket = new TargetAssociationBucket(role, current);

            if (_promoteClassifications) {
                //CleanUp previous classifications
                cleanEffectiveClassifications(role);
            }

            // now iterate per requirement
            for (TargetAssociation req : required) {
                bucket.checkAssociation(req);
                if (_promoteClassifications) {
                    promoteClassifications(role, req);
                }
            }

            // leftovers are stale
            bucket.removeRemaining();

            if (_promoteClassifications) {
                _context.saveObject(role);
            }

            _context.commitTransaction();
            _context.decache();
        }
    }
    
    /**
     * Traverse a role hiearchy building out a ProvisioningPlan representing
     * the things that would be provisioned if this role were assigned.
     *
     * The calculations are done by Provisioner/AssignmentExpander.
     * This does more than we need, but handling all the cases of profile
     * expansion, provisioning policy expansion, profile/policy merger,
     * and the obsolete local provisioning plans is complicated.  This
     * way we're sure that the result will be the same as what Provisioner
     * will eventually do.  Note that this does mean that if the
     * policies have rules that are sensitive to the identity making the
     * request we may generate a different plan.  Nothing we can do about that,
     * other than maybe passing an argument that the rule can test to know
     * what context it is in.
     */
    private ProvisioningProject flattenRole(Bundle role) throws GeneralException {

        ProvisioningProject proj = null;

        Identity ident = new Identity();
        ident.setName("dummy");
        
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        plan.add("IIQ", "dummy", "assignedRoles", Operation.Add, role);

        // This will NOT include permitted roles, if we decide we want that
        // we'll need a Provisioner option
        Provisioner p = new Provisioner(_context);
        // We only want what is in the role, not application policies
        p.setArgument(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES, true);
        proj = p.compile(plan, null);
        
        // this is pretty big and usually correct, require debug
        // gak, like debug for other things and this is just way too big,
        // if we need it make it a task option
        /*
        if (log.isDebugEnabled()) {
            log.debug("Flatting project for role " + role.getName());
            log.debug(proj.toXml());
        }
        */

        return proj;
    }

    /**
     * Given a flattened role project, calculate the list of unique target associations
     * for both the indirect indexed entitlements and the entitlements held directly on this role.
     */
    private List<TargetAssociation> getRequiredAssociations(Bundle role, ProvisioningProject proj)
        throws GeneralException {


        TargetAssociationBucket bucket = new TargetAssociationBucket();

        List<ProvisioningPlan> plans = proj.getPlans();
        for (ProvisioningPlan plan: Util.iterate(plans)) {

            // what about the unmanaged plan?  Assumming that will manually
            // provisioned so we can include it

            List<AccountRequest> accounts = plan.getAccountRequests();
            for (AccountRequest account : Util.iterate(accounts)) {
                // ignore the IIQ account
                String appname = account.getApplication();
                if (!appname.equals(ProvisioningPlan.APP_IIQ)) {

                    ApplicationInfo app = getApplicationInfo(appname);
                    if (_indexRoleEntitlements ||
                        (_indexRoleTargets && app.hasInterestingAccountAttributes())) {

                        List<AttributeRequest> atts = account.getAttributeRequests();
                        for (AttributeRequest attreq : Util.iterate(atts)) {
                            
                            IndexedAttribute att = app.getAccountAttribute(attreq.getName());
                            boolean hasAssociations = (att != null && att.hasAssociations());
                            
                            if (_indexRoleEntitlements ||
                                (_indexRoleTargets && hasAssociations)) {

                                List<String> values = getList(attreq.getValue());
                                for (String value : Util.iterate(values)) {

                                    // if the value references a group, get indirect targets
                                    if (_indexRoleTargets && hasAssociations) {
                                        getIndirectTargets(app, att, value, role.getName(), bucket);
                                    }

                                    // then add the reference itself 
                                    // should we include all of these or just ones
                                    // that are marked as entitlements in the account schema?
                                    if (_indexRoleEntitlements) {
                                        // have to convert to display name
                                        String target = value;
                                        if (att != null) {
                                            target = att.getDisplayName(value);
                                        }
                                        TargetAssociation existing = bucket.get(app.getName(), attreq.getName(), target);
                                        if (existing == null) {
                                            TargetAssociation assoc = new TargetAssociation();
                                            assoc.setApplicationName(app.getName());
                                            assoc.setTargetType(attreq.getName());
                                            assoc.setTargetName(target);
                                            // Ryan wants the role to be mentinoed as the hierarchy attribute so we
                                            // can more easily see it in the UI without doing a join
                                            assoc.setHierarchy(role.getName());

                                            if (_indexClassifications) {
                                                //MissingObjects will get incremented, may not want this
                                                ManagedAttribute thing = findManagedAttribute(app, attreq.getName(), value);
                                                if (thing != null) {
                                                    if (!Util.isEmpty(thing.getClassifications())) {
                                                        //Add classifications to the TA attributes
                                                        Set<String> classificationNames = new HashSet<>();
                                                        thing.getClassifications().forEach((c) -> classificationNames.add(c.getClassification().getName()));
                                                        assoc.setAttribute(TargetAssociation.ATT_CLASSIFICATIONS, new ArrayList<>(classificationNames));
                                                    }
                                                }
                                            }

                                            // might want a flag for direct entitlements?
                                            bucket.add(assoc);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // optionally index the role direct permissions
                    if (_indexRolePermissions) {
                        List<PermissionRequest> perms = account.getPermissionRequests();
                        if (Util.size(perms) > 0) {
                            // dealing with duplicates is even harder for permissions,
                            // we can have the same targetName in different application profiles,
                            // but within that profile we can also have several permissions with
                            // different rights, unless we allow app-specific targets, it makes no
                            // sense to merge rights
                            for (PermissionRequest perm : Util.iterate(perms)) {
                                String name = perm.getTarget();
                                String type = TargetAssociation.TargetType.P.name();
                                TargetAssociation existing = bucket.get(app.getName(), type, name);
                                if (existing == null) {
                                    TargetAssociation assoc = new TargetAssociation();
                                    assoc.setApplicationName(app.getName());
                                    assoc.setTargetType(type);
                                    assoc.setTargetName(name);
                                    assoc.setHierarchy(role.getName());
                                    // might want a flag for direct permissions?
                                    bucket.add(assoc);
                                }
                            }
                        }
                    }
                }
            }
        }

        return bucket.getAssociations();
    }

    /**
     * For the value of an account attribute, determine the indirect targets granted
     * through the group this value represents.  
     *
     * This uses a TargetAssociation cache in the IndexedAttribute so we don't keep querying.
     * This makes it different than the way we do ManagedAttribute indexing, it will
     * be faster but take up more memory.  May need to revisit this for large role models.
     */
    private void getIndirectTargets(ApplicationInfo app, IndexedAttribute att, String attvalue,
                                    String roleName, TargetAssociationBucket bucket)
        throws GeneralException {
     
        List<TargetAssociation> assocs = att.getAssociations(attvalue);
        if (assocs == null) {
            // haven't encountered this value yet
            ManagedAttribute thing = findManagedAttribute(app, att, attvalue);
            if (thing != null) {
                // query directly against the TargetAssocation table
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("objectId", thing.getId()));

                // leave out unstructured target permissions unless requested
                if (!_indexUnstructuredTargets) {
                    ops.add(Filter.ne("targetType", TargetAssociation.TargetType.TP.name()));
                }
                
                assocs = _context.getObjects(TargetAssociation.class, ops);
                
                // can store these directly in the cache since they're simple
                // and won't have detachment issues unless we start giving them a Target
                att.setAssociations(attvalue, assocs);
            }
        }

        if (assocs != null) {
            for (TargetAssociation assoc : assocs) {
                TargetAssociation existing = bucket.get(assoc);
                if (existing == null) {
                    TargetAssociation neu = copyAssociation(app, roleName, assoc);
                    bucket.add(neu);
                }
            }
        }
    }

    /**
     * Clone an association from a ManagedAttribute for a role.
     */
    private TargetAssociation copyAssociation(ApplicationInfo app, String roleName, TargetAssociation src) {

        TargetAssociation copy = new TargetAssociation();
        copy.setApplicationName(app.getName());
        copy.setTargetType(src.getTargetType());
        copy.setTargetName(src.getTargetName());
        copy.setTarget(src.getTarget());
        copy.setRights(src.getRights());
        String hierarchy = "";
        if (Util.isNotNullOrEmpty(roleName)) {
            hierarchy = roleName;
            if (Util.isNotNullOrEmpty(src.getHierarchy())) {
                hierarchy.concat("|").concat(src.getHierarchy());
            }
        }
        copy.setHierarchy(hierarchy);
        copy.setInherited(src.isInherited());
        copy.setAttributes(src.getAttributes());

        return copy;
    }

    /**
     * After isolating an attribute request for a managed entitlement, locate
     * the ManagedAttributes for each value on the list from the plan.
     *
     * See comments in getTarget() for why we don't need to include the attribute name 
     * in the query.
     */
    private ManagedAttribute findManagedAttribute(ApplicationInfo app, IndexedAttribute att,
                                                  String value) 
        throws GeneralException {

        Filter f = Filter.and(Filter.eq("application", app.getApplication()),
                              Filter.eq("type", att.getSchemaObjectType()),
                              Filter.eq("attribute", att.getName()),
                              Filter.eq("value", value));

        ManagedAttribute ma = _context.getUniqueObject(ManagedAttribute.class, f);
        if (ma == null) {
            // Haven't aggregated this yet
            if (log.isInfoEnabled()) {
                log.info("Unaggregated object: " + app.getName() + 
                         " " + att.getName() + " " + value);
            }
            _missingObjects++;
        }
        return ma;
    }

    private ManagedAttribute findManagedAttribute(ApplicationInfo app, String att,
                                                  String value)
            throws GeneralException {

        Filter f = Filter.and(Filter.eq("application", app.getApplication()),
                Filter.eq("attribute", att),
                Filter.eq("value", value));

        return _context.getUniqueObject(ManagedAttribute.class, f);
    }

    /**
     * Get the current list of TargetAssociations for a role.
     */
    private List<TargetAssociation> getCurrentAssociations(Bundle role)
        throws GeneralException {

        // assume for now there won't be many of these, may be a bad assumption
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", role.getId()));

        // leave out unstructured target permissions unless requested
        if (!_indexUnstructuredTargets) {
            ops.add(Filter.ne("targetType", TargetAssociation.TargetType.TP.name()));
        }
        
        return _context.getObjects(TargetAssociation.class, ops);
    }

    /**
     * Remove all associations for a role.
     * It doesn't matter what _indexUnstructuredTargets is, this is called
     * when rebuilding from scratch so if we didn't include any unstructured
     * in the new set, we don't want to leave old ones around.
     */
    private int removeCurrentAssociations(Bundle role)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", role.getId()));

        // first count them for statistics
        int count = _context.countObjects(TargetAssociation.class, ops);
        _targetsRemoved += count;

        _context.removeObjects(TargetAssociation.class, ops);

        if (count > 0) {
            if (log.isInfoEnabled()) {
                log.info("Cleared all associations for role " + role.getName());
            }
        }
        return count;
    }


    /**
     * Coerce a value to a string list.
     * These are entitlements from a role so they'll always either be
     * List<String> or String.
     */
    private List<String> getList(Object value) {

        List<String> result = null;
        if (value instanceof List) {
            result = (List<String>)value;
        }
        else if (value != null) {
            result = new ArrayList<String>();
            result.add(value.toString());
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Entitlement Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Index entitlements on all applications, or optionally a specific
     * set of applications passed as a task argument.
     */
    private void indexEntitlements() throws GeneralException {

        List<Application> applications = null;

        Object applist = _arguments.get(ARG_APPLICATIONS);

        if (applist == null) {
            applications = getAllIndexableApplications();
        }
        else {
            applications = ObjectUtil.getObjects(_context, Application.class, applist);
        }

        if (!_terminate) {
            if (log.isInfoEnabled()) {
                StringBuilder b = new StringBuilder();
                b.append("Indexing applications: ");
                int count = 0;
                for (Application app : applications) {
                    if (count > 0) b.append(", ");
                    b.append(app.getName());
                    count++;
                }
                log.info(b.toString());
            }

            for (Application app : Util.iterate(applications)) {
                indexEntitlements(app);
                if (_terminate) break;
            }
        }
    }
    
    /**
     * Build a list of applications that have schemas with indexed attribute.
     * Also include the ones that can have permissions.  Since this is potentially
     * a longer list, we might want to control whether attributes and permissions
     * are done independently?
     */
    private List<Application> getAllIndexableApplications() throws GeneralException {

        List<Application> result = new ArrayList<Application>();

        // TODO: Potentially a partitionable thing here if there are billions of applications,
        // better to promote a queryable column
        Iterator<Object[]> ids = _context.search(Application.class, null, "id");
        IdIterator it = new IdIterator(_context, ids);

        while (!_terminate && it.hasNext()) {
            String id = it.next();
            Application app = _context.getObjectById(Application.class, id);
            boolean include = false;
            List<Schema> schemas = app.getSchemas();
            for (Schema schema : Util.iterate(schemas)) {
                if (!Application.SCHEMA_ACCOUNT.equals(schema.getObjectType()) &&
                    (schema.hasIndexedAttribute() || schema.isIndexPermissions())) {
                    result.add(app);
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Build out indexes for the ManagedAttributes from one Application.
     * This is a potentially expensive operation that should be partitionable.
     *
     * TODO: For now we're only indexing objects that are directly
     * assignable to an account.  Intermediaries aren't requestable
     * so there is no need to include them for LCM.  We may however
     * still want them for searching in the Entitlement Catalog so may
     * need another task or Schema option.
     *
     * TODO: Cleanup isn't working if you change your mind and remove
     * an indexing option.
     */
    private void indexEntitlements(Application app) throws GeneralException {
        
        List<Schema> schemas = app.getSchemas();
        for (Schema schema : Util.iterate(schemas)) {
            if (!Application.SCHEMA_ACCOUNT.equals(schema.getObjectType())) {

                // see method comments, may want an option here
                if (app.isDirectlyAssignable(schema.getObjectType())) {

                    // Do the direct refresh and build out the list of things that need
                    // hierarchical refresh.
                    FlatteningState state = indexDirectTargets(app, schema);

                    // flatten things that were in hierarchies
                    if (!_terminate)
                        flatten(state);
                }
            }
            if (_terminate) break;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // FlatteningState
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Object we maintain during direct indexing that remembers which 
     * ManagedAttributes were part of a hierarchy and will need indirect
     * target flattening.  Saves having to scan them all later.
     *
     * For parent hierarchies we can just keep a list of things with parents.
     * For child hierarchies we have two approaches:
     *
     *    - keep a list of children and then use the Child Hierarchy
     *       Traversal method of database queries
     *
     *    - build a memory representation of the parent hierarchy and
     *        use the Child Hierarchy Conversion method
     *
     * We're going to start with the second since that's easier though it
     * requires more memory.  The algorithm for parent and child hierarchy
     * flattening can be the same, the only thing that is different is calculating
     * for a given ManagedAttribute what the parent list is.
     */
    private class FlatteningState {

        /**
         * True if the schema we are flattening defines a hierarchy attribute.
         */
        boolean _hasHierarchy;

        /**
         * When flattening a parent hierarchy, the list of ManagedAttribute 
         * ids that had parents.
         */
        List<String> _parentIds;

        /**
         * When flattening a child hierarchy, a map whose keys are ManagedAttribute
         * ids that were in a hieararchy, and whose values are the list of
         * parent ids.
         */
        Map<String,List<String>> _childHierarchies;
        
        /**
         * Cache of ManagedAttributes we have visited during hierarchy flattening.
         * 
         * For parent/inheritance flattening If there is an entry, it means that we
         * have already flattened this object and do not have to traverse further up 
         * the chain.  Currently this is just a boolean, you still have to query
         * for the current associations.  Eventually consider making this an aging
         * cache that we can keep in memory to avoid requerying for popular parents.
         */
        Map<String,String> _flattened;

        /**
         * List of MA ids that have had classifications cleaned. Used so we don't
         * clean multiple times
         * TOOD: Can we piggy back on flattened state?
         */
        List<String> _cleanedIds;


        public FlatteningState(Schema schema) {
            _flattened = new HashMap<String,String>();
            _hasHierarchy = (schema.getHierarchyAttribute() != null);
            if (_hasHierarchy) {
                if (schema.isChildHierarchy()) {
                    _childHierarchies = new HashMap<String,List<String>>();
                }
                else {
                    _parentIds = new ArrayList<String>();
                }
            }
            _cleanedIds = new ArrayList<String>();
        }

        public void addHierarchy(ManagedAttribute thing) {
            
            if (_hasHierarchy && Util.size(thing.getInheritance()) > 0) {
                if (_parentIds != null) {
                    // we go on the list
                    _parentIds.add(thing.getId());
                }
                else {
                    // add us to the parent ids for our children
                    for (ManagedAttribute child : thing.getInheritance()) {
                        List<String> parents = _childHierarchies.get(child.getId());
                        if (parents == null) {
                            parents = new ArrayList<String>();
                            _childHierarchies.put(child.getId(), parents);
                        }
                        parents.add(thing.getId());
                    }
                }
            }
        }

        /**
         * Return the ids of the objects that need flattening.
         */
        public Collection<String> getIdsToFlatten() {
            Collection<String> result = null;
            if (_parentIds != null) {
                result = _parentIds;
            }
            else if (_childHierarchies != null) {
                result = _childHierarchies.keySet();
            }
            return result;
        }

        /**
         * Given an object in the hierarchy, return the parent objects.
         */
        public List<ManagedAttribute> getParents(ManagedAttribute thing)
            throws GeneralException {

            List<ManagedAttribute> result = null;
            if (_parentIds != null) {
                result = thing.getInheritance();
            }
            else if (_childHierarchies != null) {
                result = new ArrayList<ManagedAttribute>();
                List<String> pids = _childHierarchies.get(thing.getId());
                if (pids != null) {
                    for (String id : pids) {
                        ManagedAttribute parent = _context.getObjectById(ManagedAttribute.class, id);
                        if (parent != null) {
                            result.add(parent);
                        }
                        else {
                            log.warn("ManagedAttribute evaporated: " + id);
                        }
                    }
                }
            }
            return result;
        }

        /**
         * Mark an object as having been flattened.
         */
        public void addFlattened(ManagedAttribute thing) {
            _flattened.put(thing.getId(), thing.getId());
        }

        public boolean isFlattened(ManagedAttribute thing) {
            return (_flattened.get(thing.getId()) != null);
        }
        
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Direct Target Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh the direct targets for one object.
     *
     * !! This is not following reference chains.  Need to do this for 
     * all schemas that directly or indirectly have an indexed attribute.
     */
    private FlatteningState indexDirectTargets(Application app, Schema schema)
        throws GeneralException {

        FlatteningState state = new FlatteningState(schema);
        
        // determine the list of indexed attributes
        ApplicationInfo appinfo = getApplicationInfo(app);
        List<IndexedAttribute> atts = appinfo.getIndexedAttributes(schema);
        
        if (log.isInfoEnabled()) {
            log.info("Indexing schema " + schema.getObjectType());
            log.info("Indexing entitlements: " + atts);
            if (schema.isIndexPermissions()) {
                log.info("Indexing permissions");
            }
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        ops.add(Filter.eq("type", schema.getObjectType()));

        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> ids = _context.search(ManagedAttribute.class, ops, props);
        IdIterator it = new IdIterator(_context, ids);
        while (!_terminate && it.hasNext()) {
            String id = it.next();
            ManagedAttribute thing = _context.getObjectById(ManagedAttribute.class, id);

            if (log.isDebugEnabled()) {
                log.debug("Refreshing " + thing.getAttribute() + "/" + thing.getValue());
            }
            
            // get the currrent set associated with this thing, leaving out file shares
            List<TargetAssociation> current = getCurrentAssociations(thing, false);

            // sort them and detect duplicates
            TargetAssociationBucket bucket = new TargetAssociationBucket(thing, current);

            List<TargetAssociation> required = getRequiredAssociations(app, schema, atts, thing);
            if (required != null) {
                if (_promoteClassifications) {
                    //Need to look into this when walking hierarchy
                    if (!Util.nullSafeContains(state._cleanedIds, thing.getId())) {
                        cleanEffectiveClassifications(thing);
                        state._cleanedIds.add(thing.getId());
                    }
                }
                for (TargetAssociation req : required) {
                    bucket.checkAssociation(req);
                    if (_promoteClassifications) {
                        promoteClassifications(thing, req);
                    }
                }
            }
            
            // leftovers are stale
            bucket.removeRemaining();

            // if this thing is part of a hiearchy remember the relationships that need to be refreshed
            state.addHierarchy(thing);

            _context.commitTransaction();
            _context.decache();
            _entitlementsIndexed++;
        }

        return state;
    }

    /**
     * Calculate the required list of direct associations for a ManagedAttribute.
     */
    private List<TargetAssociation> getRequiredAssociations(Application app,
                                                            Schema schema,
                                                            List<IndexedAttribute> atts,
                                                            ManagedAttribute thing)
        throws GeneralException {

        TargetAssociationBucket bucket = new TargetAssociationBucket();
        
        if (Util.size(atts) > 0) {
            for (IndexedAttribute att : atts) {
                if (!att.isHiearchyAttribute()) {
                    Object value = thing.get(att.getName());
                    if (value instanceof List) {
                        for (Object el : (List)value) {
                            if (el != null)
                                addRequiredAssociation(thing, att, el.toString(), bucket);
                        }
                    }
                    else if (value != null) {
                        addRequiredAssociation(thing, att, value.toString(), bucket);
                    }
                }
                else {
                    // these aren't in the attribute map, they are promoted
                    // to a class property
                    List<ManagedAttribute> values = thing.getInheritance();
                    if (values != null) {
                        for (ManagedAttribute value : values) {
                            addRequiredAssociation(thing, att, value.getValue(), bucket);
                        }
                    }
                }
            }
        }

        // promote permissions
        if (schema.isIndexPermissions()) {
            List<Permission> perms = thing.getPermissions();
            if (Util.size(perms) > 0) {
                String type = TargetAssociation.TargetType.P.name();
                for (Permission perm : Util.iterate(perms)) {
                    String name = perm.getTarget();
                    TargetAssociation existing = bucket.get(type, name);
                    if (existing == null) {
                        TargetAssociation assoc = new TargetAssociation();
                        assoc.setTargetType(type);
                        assoc.setTargetName(name);
                        assoc.setRights(perm.getRights());
                        assoc.setHierarchy(thing.getDisplayableName());
                        assoc.setApplicationName(app.getName());
                        bucket.add(assoc);
                    }
                    else {
                        // in theory there can be multiple Permission objects
                        // with different rights lists, could merge but Connectors
                        // aren't supposed to do that
                    }
                }
            }
        }
        
        return bucket.getAssociations();
    }

    /**
     * Helper for getRequiredAssociations.
     * Build out a single TargetAssociation for one indexed attribute value.
     */
    private void addRequiredAssociation(ManagedAttribute thing, IndexedAttribute att,
                                        String value, TargetAssociationBucket bucket)
        throws GeneralException {

        // fetch display name if it has one
        String name = att.getDisplayName(value);

        TargetAssociation existing = bucket.get(att.getName(), name);
        if (existing == null) {
            TargetAssociation assoc = new TargetAssociation();
            assoc.setTargetType(att.getName());
            assoc.setTargetName(name);
            assoc.setHierarchy(thing.getDisplayableName());
            assoc.setApplicationName(thing.getApplication().getName());
            if (_indexClassifications && att._hierarchyAttribute) {
                assoc.setAttribute(TargetAssociation.ATT_CLASSIFICATIONS, att.getClassificationNames(value));
            }
            bucket.add(assoc);
        }
        else {
            // must have had a duplicate entry on the list, could warn but there
            // isn't anything they can do about it
        }
    }

        
    /**
     * Get the current list of TargetAssociations for a group.
     * This includes only attribute and permission associations, file share
     * associations must be managed by the TargetCollector.
     */
    private List<TargetAssociation> getCurrentAssociations(ManagedAttribute thing, boolean indirect)
        throws GeneralException {

        // assume for now there won't be many of these, may be a bad assumption
        // TODO: better to do a projection query than loading the entire object?
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", thing.getId()));

        // leave out unstructured targets when managing direct associations,
        // those won't change, when indirect leave them out unless we've been
        // asked to flatten them
        if (!indirect || !_indexUnstructuredTargets) {
            ops.add(Filter.ne("targetType", TargetAssociation.TargetType.TP.name()));
        }
        
        // The flattened flag is set on TargetAssociations we created as indirect associations
        ops.add(Filter.eq("flattened", indirect));

        return _context.getObjects(TargetAssociation.class, ops);
    }

    /**
     * Utility method aggregator can call when groups are deleted.
     * Clean up all the existing associations.
     */
    public static void removeCurrentAssociations(SailPointContext context, ManagedAttribute thing)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", thing.getId()));

        context.removeObjects(TargetAssociation.class, ops);
    }

    public void promoteClassifications(Classifiable obj, TargetAssociation ass) throws GeneralException {
        if (ass != null){
            if (ass.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS) != null) {
                List<String> classNames = Util.otol(ass.getAttribute(TargetAssociation.ATT_CLASSIFICATIONS));
                for (String s : Util.safeIterable(classNames)) {
                    Classification c = _context.getObjectByName(Classification.class, s);
                    if (c != null) {
                        obj.addClassification(c, Source.Task.name(), true);
                    }
                }
            }
        }

    }

    /**
     * Clean all effective classifications from the Classifiable obejct
     * @param obj
     */
    public void cleanEffectiveClassifications(Classifiable obj) {
        if (!Util.isEmpty(obj.getClassifications())) {
            Iterator<ObjectClassification> cIter = obj.getClassifications().iterator();
            while (cIter.hasNext()) {
                ObjectClassification c = cIter.next();
                if (Util.nullSafeEq(c.getSource(), Source.Task.name()) && c.isEffective()) {
                    //Effective CLassification generated from this Task, remove
                    cIter.remove();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Hierarhcy Flattening
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For each object we encountered during direct indexing that was
     * part of a hierarchy, copy the contributions of the parent objects
     * to the children.  The same algorighm is used for both parent
     * hierarchies and child hierarchies, the only difference is the model
     * we use to determine what for a given ManagedAttribute are the parents.
     * For parent hierarchies it is simply the inheritance list.  For child
     * hierarchies we will have remembered this in the FlatteningState.
     */
    private void flatten(FlatteningState state) throws GeneralException {

        Collection<String> ids = state.getIdsToFlatten();
        if (ids != null) {
            for (String id : ids) {
                ManagedAttribute thing = _context.getObjectById(ManagedAttribute.class, id);
                if (thing != null) {
                    // flattening will decache so load it first
                    thing.load();
                    flatten(state, thing);
                }
                else {
                    log.warn("ManagedAttribute evaporated: " + id);
                }
                if (_terminate) break;
            }
        }
    }
        
    /**
     * Flatten one object and refresh the indirect associations.
     */
    private void flatten(FlatteningState state, ManagedAttribute thing) throws GeneralException {
            
        // ignore if we've already been here
        if (state.isFlattened(thing)) {
            if (log.isDebugEnabled()) {
                log.debug("Already flattened " + thing.getAttribute() + "/" + thing.getValue());
            }
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Flattening " + thing.getAttribute() + "/" + thing.getValue());
            }

            // get the current indirect associations
            List<TargetAssociation> current = getCurrentAssociations(thing, true);
            TargetAssociationBucket bucket = new TargetAssociationBucket(thing, current);

            // flatten all the parent targets
            List<TargetAssociation> required = getRequiredIndirectAssociations(state, thing);

            if (_promoteClassifications) {
                //Need to look into this when walking hierarchy
                if (!Util.nullSafeContains(state._cleanedIds, thing.getId())) {
                    cleanEffectiveClassifications(thing);
                    state._cleanedIds.add(thing.getId());
                }
            }

            // reconcile with existing
            for (TargetAssociation req : required) {
                bucket.checkAssociation(req);
                if (_promoteClassifications) {
                    promoteClassifications(thing, req);
                }
            }

            // leftovers are stale
            bucket.removeRemaining();

            //Save MA to get classifications
            if (_promoteClassifications) {
                _context.saveObject(thing);
            }
            _context.commitTransaction();
            _context.decache();
            _entitlementsIndexed++;

            // remember that this one was flattened
            state.addFlattened(thing);
        }
    }

    /**
     * Flatten indirect associations.
     */
    private List<TargetAssociation> getRequiredIndirectAssociations(FlatteningState state, ManagedAttribute thing)
        throws GeneralException {

        TargetAssociationBucket bucket = new TargetAssociationBucket();

        List<ManagedAttribute> parents = state.getParents(thing);
        if (parents != null) {
            for (ManagedAttribute parent : parents) {
                // recursively flatten parents first
                flatten(state, parent);

                // query for the resulting targets and add them to the bucket
                // could cache this for heavily used parent nodes?
                List<TargetAssociation> current = getParentAssociations(parent);
                if (current != null) {
                    for (TargetAssociation assoc : current) {

                        // unlike direct associations, it is normal for there to be duplicates
                        // the same thing can come in from different branches of the hierarchy,
                        // but we'll only remember the first one
                        TargetAssociation existing = bucket.get(assoc);
                        if (existing == null) {
                            TargetAssociation neu = new TargetAssociation();

                            neu.setTargetType(assoc.getTargetType());
                            neu.setTargetName(assoc.getTargetName());
                            // only set for unstructured targets
                            neu.setTarget(assoc.getTarget());
                            neu.setRights(assoc.getRights());
                            neu.setInherited(assoc.isInherited());
                            neu.setApplicationName(assoc.getApplicationName());
                            neu.setAttributes(assoc.getAttributes());

                            // add our prefix to the hierarchy
                            String ours = thing.getDisplayableName();
                            String theirs = assoc.getHierarchy();
                            // ours should never be null, but be safe
                            // theirs looks like it can be null for unstructured target permissions so
                            // don't add a trailing pipe, something wrong here?
                            String combined = (ours != null ? ours : "");
                            if (theirs != null) {
                                combined += "|" + theirs;
                            }
                            neu.setHierarchy(combined);
                            
                            // this flag indiciates that it was copied from above in the hierarchy
                            // used during cleanup when the fullReset option is enabled
                            neu.setFlattened(true);
                            
                            bucket.add(neu);
                        }
                    }
                }
            }
        }
        return bucket.getAssociations();
    }

    /**
     * Return the associations of a parent node.
     */
    private List<TargetAssociation> getParentAssociations(ManagedAttribute parent)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", parent.getId()));

        // don't flatten unstructured targets unless requested
        if (!_indexUnstructuredTargets) {
            ops.add(Filter.ne("targetType", TargetAssociation.TargetType.TP.name()));
        }
        
        return _context.getObjects(TargetAssociation.class, ops);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Extra Work
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Remove all TargetAssociations for the given owner type.
     * This is the nuclear option you have to use if you've changed your mind
     * and decided not to index previously indexed attributes or permissions.
     * Because the path from an object to an association can be complex
     * and span several levels, knowing exactly what to clean is difficult 
     * without touching every last Bundle and ManagedAttribute just to see.
     *
     * iiqpb-79 noticed that this was removing all of the unstructured target
     * associations that had been collected and are not created by this task. 
     * Those should be preserved since this task can't recreate them.  Deletion
     * now requires that the "flattened" flag be on if this is an attribute association.
     */
    private void resetTargetAssociations(OwnerType type) throws GeneralException {

        if (log.isInfoEnabled()) {
            if (type == OwnerType.R)
                log.info("Resetting role target associations");
            else
                log.info("Resetting attribute target associations");
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("ownerType", type.name()));
        if (type == OwnerType.A) {
            // iiqpb-79 only ones we created with flattening
            ops.add(Filter.eq("flattened", true));
        }
        
        _targetsReset = _context.countObjects(TargetAssociation.class, ops);
        
        if (log.isInfoEnabled()) {
            log.info("Deleting " + Util.itoa(_targetsReset) + " associations.");
        }

        _context.removeObjects(TargetAssociation.class, ops);
    }

    public void resetClassifications(String ownerType) throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("Resetting effective classificatinos for ownerType[" + ownerType + "]");
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("ownerType", ownerType));
        ops.add(Filter.eq("source", Source.Task.name()));
        ops.add(Filter.eq("effective", true));

        _effectiveClassificationsReset = _context.countObjects(ObjectClassification.class, ops);

        if (log.isInfoEnabled()) {
            log.info("Deleting " + Util.itoa(_effectiveClassificationsReset) + " effective ObjectClassifications.");
        }

        _context.removeObjects(ObjectClassification.class, ops);

    }

    /**
     * Refresh the full text indexes on this machine only.
     */
    private void refreshFulltextIndexes() throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("Refreshing fulltext indexes");
            
        }
    }

    

}

