/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * The ManagedAttributer provides utility methods for searching,
 * reading, and creating ManagedAttribute objects.  It is also used
 * by the Identitizer to promote attribute values from links into
 * ManagedAttributes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * 
 *
 * Uniqueness Notes
 *
 * In 7.2 we added the notion of a unique hash to ManagedAttributes so we could
 * detect duplications among a cluster of machines doing partitioned MA promotion.
 * Historically we have considered uniqueness as being the combination of these
 * properties:
 *
 *       application
 *       type           (Entitlement, Permission, group, other schema name)
 *       attribute      (groups, memberOf, etc.)
 *       value          (group name, dn...)
 *
 * In 6.4 we added the abiliity for connectors to return other types of objects
 * besides groups, and for these objects to be nested.  For example there could
 * be a Schema named "group" and another named "profile".  Within the "group"
 * schema there is an attribute named "profiles" whose type is "profile".  Once
 * we started doing that we stopped saving the attribute name in these nested
 * MAs, this was because that schema/value combination could appear in multiple
 * parent schemas and we don't want to duplicate them.  Example:
 *
 *     Schema: group
 *        Attribute: profiles, type=profile
 *
 *     Schema: somethingElse
 *        Attribute: primaryProfiles, type=profile
 *
 * We're now allowing more flexibility in how connector objects can be related,
 * we can't say that the attribute name used to reference them will be the same
 * in all schemas.
 *
 * This means that for uniqueness checking, it reduces to:
 *
 *     application + type + value
 *
 * The trick is for non-aggregatable account attributes.  These are things like 
 * "department" or "location" that someone wants promoted to the entitlement catalog
 * but which we don't aggregate.  We have stored them with type=Entitlement along
 * with the account attribute name.  When we form the uniqueness hash we can't
 * use type since the same value could appear in two different attributes.  For example
 * "Austin" could be both a location and a department.  In these cases instead of
 * type=Entitlement we use type=location or type=department.  In effect the attribute
 * name becomes the type.  
 *
 * Arguably we should have done this all along, making attributeName not be part
 * of the search keys, and perhaps removing it all together.  It's cleaner.
 * It's a messy migration though with backward compatibility issues.  
 *
 * So while there is still a lot of code that searches with attribute names and
 * that isn't going away, we're not including attribute name in the hash calculation.
 * 
 * Oh, and Permissions are weird.  There the type=Permission and the attribute is
 * the target name and value is null.  The unique hash for those is therefore a 
 * combination of application, type (Permission), and attribute.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.codec.digest.DigestUtils;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Schema;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The ManagedAttributer provides utility methods for searching,
 * reading, and creating ManagedAttribute objects.  It is also used
 * by the Identitizer to promote attribute values from links into
 * ManagedAttributes.
 */
public class ManagedAttributer {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public static final String RET_CREATED = "managedAttributesCreated";
    public static final String RET_CREATED_BY_APP = "managedAttributesCreatedByApplication";

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private static final Log log = LogFactory.getLog(ManagedAttributer.class);

    private SailPointContext context;
    private boolean _disableExistenceCheck;
    private int _uniqueViolations;
    
    // Shared state for the customization rule.
    private Map<String,Object> state = new HashMap<String,Object>();
    
    // Statistics
    private int totalCreated;
    private Map<String,Integer> createdByApp = new HashMap<String,Integer>();

    ////////////////////////////////////////////////////////////////////////////
    //
    // STATIC METHODS
    //
    // This section contains basic utility methods for searching and 
    // creating ManagedAttribute objects. They are static so they can be
    // more easily used by Explanator and others.  The style here is similar
    // to what we have in ObjectUtil, but it doesn't seem necessary to have
    // another class just for these.
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @see #getAll(SailPointContext, String, boolean, String, String, String)
     */
    @Deprecated
    public static List<ManagedAttribute> getAll(SailPointContext con, String appid,
                                                boolean permission, String name, String value) 
       throws GeneralException {
        
        return getAll(con, appid, permission, name, value, null);
    }

    /**
     * Load all matching ManagedAttributes from the database.
     * We don't expect more than one but it's hard to prevent.
     * This is public so it can be used by cleanup tasks.
     *
     * @param con
     * @param appid
     * @param permission
     * @param name
     * @param value
     * @param objectType
     * @return List<ManagedAttribute> list of matching attributes
     * @throws GeneralException
     */
    public static List<ManagedAttribute> getAll(SailPointContext con, String appid, 
                                                boolean permission, String name, String value, String objectType) 
        throws GeneralException {

        List<ManagedAttribute> result = null;

        QueryOptions ops = getQueryOptions(appid, permission, name, value, objectType);

        // jsl - notes from Kelly over in AccountGroupService, does
        // still applhy?
        //
        // setting this to true causes some funky class cast exceptions
        // figure this out for 5.2
        // Update 1/3/2010 : It seems this is related to hibernate bug: http://opensource.atlassian.com/projects/hibernate/browse/HHH-2463
        // The issue doesn't seem to occur when using mysql db.
        // Hibernate issue is fixed in version 3.5.5 or 3.6.1
        ops.setCacheResults(false);
        
        // In theory we should prepare for a large number but that really
        // isn't supposed to happen.
        result = con.getObjects(ManagedAttribute.class, ops);

        return result;
    }

    /**
     * Load all matching ManagedAttributes from the database that
     * match a given ManagedAttribute.
     *
     * @param con
     * @param src
     * @return List<ManagedAttribute> list of matching attributes
     * @throws GeneralException
     */
    public static List<ManagedAttribute> getAll(SailPointContext con, ManagedAttribute src) 
        throws GeneralException {

        List<ManagedAttribute> result = null;

        Application app = src.getApplication();
        if (app != null) {
            boolean permission = (ManagedAttribute.Type.Permission.name().equals(src.getType()));
            result = getAll(con, app.getId(), permission, src.getAttribute(), src.getValue(), src.getType());
        }

        return result;
    }

    /**
     * Return the number of ManagedAttributes that match a given object.
     *
     * @param con
     * @param src
     * @return number of matching attributes
     * @throws GeneralException
     */
    public static int count(SailPointContext con, ManagedAttribute src) 
        throws GeneralException {

        int count = 0;

        Application app = src.getApplication();
        if (app != null) {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("application.id", app.getId()));
            ops.add(Filter.eq("type", src.getType()));
            ops.add(Filter.ignoreCase(Filter.eq("attribute", src.getAttribute())));

            ops.add(Filter.ignoreCase(Filter.eq("value", src.getValue())));

            count = con.countObjects(ManagedAttribute.class, ops);
        }

        return count;
    }

    /**
     * Return the number of ManagedAttributes for the given application with the given ObjectType
     *
     * @param con SailPointContext to perform query
     * @param app Application the ManagedAttributes are part of
     * @param objectType The schemaObjectType of the ManagedAttributes
     * @return the number of ManagedAttributes for the given application of a particular ObjectType
     * @throws GeneralException
     */
    public static int count(SailPointContext con, Application app, String objectType)  throws GeneralException {

       int count=0;
       QueryOptions qo = new QueryOptions();
       qo.add(Filter.eq("application", app));
       qo.add(Filter.eq("type", objectType));
       count = con.countObjects(ManagedAttribute.class, qo);

       return count;

    }
    
    /**
     * Prior to 6.4 this method was deprecated. If the managed attribute is not an account group,
     * this method is completely valid and will return the number of ManagedAttributes that match
     * the arguments. One example of correct use of this method would be passing a directly assignable 
     * managed attribute name/value pair.
     *
     * @param con
     * @param app
     * @param name
     * @param value
     * @return number of matching attributes
     * @throws GeneralException
     */
    public static int count(SailPointContext con, Application app, String name, String value) 
        throws GeneralException {
        
        return count(con, app, name, value, null);
    }

    /**
     * Return the number of ManagedAttributes that match the given arguments.
     * Used by Aggregator when promoting groups related to logical apps.
     * This method does not include Permissions in its count.
     * If objectType is nonNull, look for MA with type = objectType, otherwise
     * default to Entitlement
     *
     * @param con
     * @param app
     * @param name
     * @param value
     * @param objectType
     * @return number of matching attributes
     * @throws GeneralException
     */
    public static int count(SailPointContext con, Application app, String name, String value, String objectType)
        throws GeneralException {

        int count = 0;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        if (Util.isNotNullOrEmpty(objectType)) {
            ops.add(Filter.eq("type", objectType));
        } else {
            ops.add(Filter.eq("type", ManagedAttribute.Type.Entitlement.name()));
        }
        ops.add(Filter.ignoreCase(Filter.eq("attribute", name)));
        ops.add(Filter.ignoreCase(Filter.eq("value", value)));

        count = con.countObjects(ManagedAttribute.class, ops);

        return count;
    }

    /**
     * @see #get(SailPointContext, Application, boolean, String, String, String)
     */
    @Deprecated
    public static ManagedAttribute get(SailPointContext con, String appid, 
                                       boolean permission, String name, String value) 
        throws GeneralException {
        
        return get(con, appid, permission, name, value, null);
    }
                                       
    /**
     * Load one ManagedAttribute from the database.
     *
     * @param con
     * @param appid
     * @param permission
     * @param name
     * @param value
     * @param objectType
     * @return ManagedAttribute matching attribute
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, String appid, 
                                       boolean permission, String name, String value, String objectType) 
        throws GeneralException {

        ManagedAttribute ma = null;
        List<ManagedAttribute> result = getAll(con, appid, permission, name, value, objectType);
        if (result != null && result.size() > 0) {
            if (result.size() > 1) {
                if (log.isWarnEnabled())
                    log.warn("More than one matching ManagedAttribute: " + 
                             appid + ", " + name + ", " + value);
            }
            ma = result.get(0);
        }

        return ma;
    }

    /**
     * Get just the specified attributes for this Managed Attribute
     */
    public static Object[] getAttr(SailPointContext con, String appid,
                                       boolean permission, String name, String value, List<String> properties)
            throws GeneralException {
        QueryOptions ops = getQueryOptions(appid, permission, name, value, null);

        // In theory we should prepare for a large number but that really
        // isn't supposed to happen.
        Iterator<Object[]> resultItr = con.search(ManagedAttribute.class, ops, properties);
        if (resultItr.hasNext()) {
            // Get the first object returned and flush any remaining.
            Object[] result = resultItr.next();
            Util.flushIterator(resultItr);
            return result;
        }

        return null;

    }

    /**
     * @see #get(SailPointContext, Application, boolean, String, String, String)
     */
    @Deprecated
    public static ManagedAttribute get(SailPointContext con, Application app,
                                       boolean permission, String name, String value)
        throws GeneralException {

        return get(con, app, permission, name, value, null);

    }

    /**
     * Get ManagedAttribute
     *
     * @param con
     * @param app
     * @param permission
     * @param name
     * @param value
     * @param objectType
     * @return matching attribute
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, Application app,
                                       boolean permission, String name, String value, String objectType)
        throws GeneralException {

        String appid = (app != null) ? app.getId() : null;
        return get(con, appid, permission, name, value, objectType);
    }

    /**
     * Get a ManagedAttribute for an entitlement.
     *
     * @param con
     * @param appid
     * @param name
     * @param value
     * @return matching attribute
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, String appid, 
                                       String name, String value) 
        throws GeneralException {

        return get(con, appid, false, name, value, null);
    }

    /**
     * NOTE: this will not work for AccountGroup lookups. We are setting name null for accountGroups.
     * Use the {@link #get(SailPointContext, String, boolean, String, String, String)} method
     * @param con the context
     * @param app application this managed attribute is associated with
     * @param name name of the managed attribute, i.e. capabilities
     * @param value value of the managed attribute, i.e. User
     * @return the managed attribute or null if we could not find one
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, Application app,
                                       String name, String value) 
        throws GeneralException {
        
        if (null == app){
            return null;
        }

        return get(con, app.getId(), false, name, value, null);
    }

    /**
     * Get a ManagedAttribute for a permission.
     *
     * @param con
     * @param appid
     * @param target
     * @return managed attribute for permission
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, String appid, String target)
        throws GeneralException{

        return get(con, appid, true, target, null, null);
    }

    /**
     * Look up a MangedAttribute by displayable name.
     * This is used in some of the older unit tests where name uniqueness
     * is expected.
     *
     * @param con
     * @param app
     * @param name
     * @return ManagedAttribute
     * @throws GeneralException
     */
    public static ManagedAttribute getByDisplayName(SailPointContext con, 
                                                    Application app,
                                                    String name) 
        throws GeneralException {
        
        ManagedAttribute found = null;
        String appid = app.getId();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application.id", appid));
        ops.add(Filter.ignoreCase(Filter.eq("displayableName", name)));
        ops.setCacheResults(false);
        
        List<ManagedAttribute> result = con.getObjects(ManagedAttribute.class, ops);
        if (result != null && result.size() > 0) {
            if (result.size() > 1) {
                if (log.isWarnEnabled())
                    log.warn("More than one matching ManagedAttribute: " + 
                             appid + ", " + name);
            }
            found = result.get(0);
        }
        return found;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // ObjectRequest Matching
    //
    // These are specific to ProvisionignPlans for group updates, but we
    // need them in several places and sailpoint.provisioner seemed to low.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if an ObjectRequest is for a MangedAttribute representing
     * a Permission rather than an entitlement or group.  This is represented
     * by setting the sysManagedAttribute type attribute.  
     * 
     * The ObjectRequest.type should be "ManagedAttribute" but we don't care.
     *
     * @param req
     * @return boolean true if request is permission request
     */
    static public boolean isPermissionRequest(ObjectRequest req) {
        boolean permission = false;

        AttributeRequest ar = req.getAttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE);
        if (ar != null) {
            permission = ManagedAttribute.Type.Permission.name().equalsIgnoreCase((String) ar.getValue());
        }

        return permission;
    }

    /**
     * Locate the Schema of the object targeted by this request.
     * We're expecting this to be extensible where the ObjectRequest.type
     * property can be the name of any Schema in the Application.
     * Prior to release 6.4 we only supported SCHEMA_GROUP, now you may 
     * configure whatever schema type you would like.
     *
     * @param req
     * @param app
     * @return Schema target schema
     */
    static public Schema getTargetSchema(ObjectRequest req, Application app) {
        
        String type = req.getType();

        // this is the most common, allow it to be missing
        if (type == null) type = Connector.TYPE_GROUP;

        return app.getSchema(type);
    }

    /**
     * Determine the "attribute name" of the ManagedAttribute targeted
     * by this request.  For groups and entitlements this will be the
     * name of the attribute from the account schema whose values
     * are being promoted to MAs.
     * 
     * For Permissions this is a misnomer, there is no reference attribute
     * this will be a target name.
     *
     * @param req
     * @param app
     * @return String reference attribute
     * @throws GeneralException
     */
    static public String getReferenceAttribute(ObjectRequest req, Application app)
        throws GeneralException {

        String referenceAttribute = null;

        // new convention, pass the name in as an explicit AttributeRequest
        // !! do we need the alternate logic below, when would this ever
        // not be set?
        AttributeRequest ar = req.getAttributeRequest(ManagedAttribute.PROV_ATTRIBUTE);
        if (ar != null) {
            Object v = ar.getValue();
            if (v != null)
                referenceAttribute = v.toString();
        }

        if (referenceAttribute == null) {

            String type = req.getType();

            if (ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE.equals(type)) {
                // the new convention for creating ManagedAttributes that
                // aren't groups, PROV_ATTRIBUTE must be set
                log.error("Missing plan attribute: " + ManagedAttribute.PROV_ATTRIBUTE);
            }
            else {
                Schema schema = getTargetSchema(req, app);

                if (schema != null) {
                    // reference attribute comes from the account schema
                    Schema aschema = app.getSchema(Connector.TYPE_ACCOUNT);
                    if (aschema != null) 
                        referenceAttribute = aschema.getGroupAttribute(schema.getObjectType());
                }
                else {
                    // old convention, must map to an account attribute
                    Schema sch = app.getSchema(Connector.TYPE_ACCOUNT);
                    // !! should we continue to support this?
                    if (sch != null) {
                        AttributeDefinition def = sch.getAttributeDefinition(type);
                        if (def != null)
                            referenceAttribute = type;
                    }
                }
                // Technically, we don't need a reference attribute in the case of indirect assignments.
                // we used to log an error here if referenceAttribute was null, now we'll keep the comments for posterity
            }
        }

        return referenceAttribute;
    }

    /**
     * Get the id of the target object, which will be stored in the 
     * ManagedAttribute.value property.  This is only relevant
     * for requests to create entitlements or groups.
     *
     * Usually this will be the nativeIdentity in the request.
     * For requests on MAs representing groups, we also support
     * this being represented as an AttributeReuqest for the naming attribute
     * when we create new groups
     *
     * @param req
     * @param app
     * @param schema
     * @return String native identity
     */
    static public String getObjectIdentity(ObjectRequest req, Application app, Schema schema) {


        String id = null;

        // A ResourceObject returned by the Connector this authoritative, 
        // even over the ObjectRequest.nativeIdentity
        ProvisioningResult result = req.getResult();
        if (result != null) {
            ResourceObject retobj = result.getObject();
            if (retobj != null) {
                id = retobj.getIdentity();
                if (id == null && schema != null) {
                    String idatt = schema.getIdentityAttribute();
                    id = retobj.getString(idatt);
                }
            }
        }

        if (id == null) {
            id = req.getNativeIdentity();
            if (id == null && schema != null) {
                // might be true for op=Create look for the naming attribute
                String identityAttribute = schema.getIdentityAttribute();
                if (identityAttribute != null) {
                    AttributeRequest attr = req.getAttributeRequest(identityAttribute);
                    if (attr != null) {
                        Object value = attr.getValue();
                        if (value != null)
                            id = value.toString();
                    }
                }
            }
        }

        return id;
    }

    /**
     * Get identity for request and application
     *
     * @param req
     * @param app
     * @return String native identity
     */
    static public String getObjectIdentity(ObjectRequest req, Application app) {

        // check before we bother
        String id = req.getNativeIdentity();
        if (id == null)
            id = getObjectIdentity(req, app, getTargetSchema(req, app));
        return id;
    }

    /**
     * Get identity for request
     *
     * @param con
     * @param req
     * @return String native identity
     * @throws GeneralException
     */
    static public String getObjectIdentity(SailPointContext con, ObjectRequest req)
            throws GeneralException {

        // check before we bother
        String id = req.getNativeIdentity();
        if (id == null) {
            Application app = con.getObjectByName(Application.class, req.getApplication());
            if (app != null)
                id = getObjectIdentity(req, app, getTargetSchema(req, app));
        }
        return id;
    }

    /**
     * Locate an existing ManagedAttribute that would be targeted by this request.
     * This is a static public utility because we need it in several places:
     * PlanCompiler and SMListener.
     *
     * referenceAttribute is a misnomer if this is a Permission request, in that
     * case it will be the target name.
     *
     * @param con
     * @param req
     * @param app
     * @return ManagedAttribute targeted managed attribute
     * @throws GeneralException
     */
    static public ManagedAttribute get(SailPointContext con,
                                       ObjectRequest req, 
                                       Application app)
        throws GeneralException {

        ManagedAttribute found = null;

        String referenceAttribute = getReferenceAttribute(req, app);

        if (isPermissionRequest(req)) {
            found = ManagedAttributer.get(con, app, true, referenceAttribute, null, null);
        } else {
            Schema schema = getTargetSchema(req, app);
            String objectType = null;
            if(schema != null) {
                objectType = schema.getObjectType();
            }
            String id = getObjectIdentity(req, app, schema);

            // don't throw here since this is used by PlanCompiler which doesn't care
            // it will fail later when we attempt to provision
            // IIQMAG-3341 - in the case of a create, it's possible that the system we are creating
            // the managed attribute on will generate a native id and pass it back, so if this is
            // a create, we should not log this as an error.  We will log it as info in case there is
            // some reason that we want to know this.
            if (id == null) {
                final String message = "Unable to locate ManagedAttribute: request has no nativeidentity";
                if (ProvisioningPlan.ObjectOperation.Create.equals(req.getOp())) {
                    log.info(message);
                } else {
                    log.error(message);
                }
            } else {
                found = get(con, app, false, referenceAttribute, id, objectType);
            }
        }

        return found;
    }

    /**
     * Get managed attribute for request
     *
     * @param con
     * @param req
     * @return ManagedAttribute
     * @throws GeneralException
     */
    static public ManagedAttribute get(SailPointContext con, ObjectRequest req)
        throws GeneralException {

        ManagedAttribute found = null;
        String appname = req.getApplication();
        if (appname != null) {
            Application app = con.getObjectByName(Application.class, appname);
            if (app != null)
                found = get(con, req, app);
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ResourceObject Matching
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Locate a ManagedAttribute identified by a ResourceObject returned
     * by a Connector.  This became complicated in delta-aggregation 
     * delete case because the Connector may not have the native group
     * identity (typically a DN), instead it has a uuid.  The usual
     * search methods above don't handle uuid.
     *
     * @param con
     * @param app
     * @param groupAttribute
     * @param obj
     * @return ManageAttribute attribute returned by connector
     * @throws GeneralException
     */
    public static ManagedAttribute get(SailPointContext con, Application app,
                                       String groupAttribute, 
                                       ResourceObject obj)
        throws GeneralException {

        ManagedAttribute found = null;

        String id = obj.getIdentity();
        String objectType = obj.getObjectType();
        if (id != null) {
            // ignore uuid, but we could use that to detect rename someday
            // Let's pass objectType as well in case attribute is null (AccountGroups)
            found = getEntitlementOrObject(con, app, groupAttribute, id, objectType);
        }
        else {
            String uuid = obj.getUuid();
            if (uuid != null) {
                // don't bother with type, perms won't ever match uuids
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("application", app));
                //If an attribute is supplied, use that as the key (app/attribute/value)
                //Otherwise, we will use objectType (indirect groups)
                if(Util.isNotNullOrEmpty(groupAttribute)) {
                    ops.add(Filter.ignoreCase(Filter.eq("attribute", groupAttribute)));
                } else {
                    ops.add(Filter.eq("type", objectType));
                }
                ops.add(Filter.ignoreCase(Filter.eq("uuid", uuid)));

                List<ManagedAttribute> result = con.getObjects(ManagedAttribute.class, ops);
                // not expecting more than one since it's a uuid...
                if (result != null && result.size() > 0) {
                    if (result.size() > 1) {
                        if (log.isWarnEnabled())
                            log.warn("More than one matching ManagedAttribute uuid: " + 
                                     app.getName() + ", " + groupAttribute + ", " + uuid);
                    }
                    found = result.get(0);
                }
            }
        }

        return found;
    }

    /**
     * Return the Managedttribute for the given application. This will return a Managed Entitlement
     * or Managed Application Object type with the given app/value. This will use the attribute
     * (application/value/attribute) as the lookup if non-null. Otherwise use (application/type/value)
     * as the key. Type can be either an ApplicationObject type or Entitlement.
     *
     * @param ctx SailPointContext used to query
     * @param app Application of the ManagedAttribute
     * @param groupAttribute AccountSchema Attribute in which this MA can be assigned
     * @param value Value of the ManagedAttribute
     * @param type The type of the Managed Attribute
     * @return ManagedAttribute of given app/value/attribute with type of Entitlement or schemaObjectType
     *
     * @ignore
     * This is used when looking up MA in AccountGroup Aggregation. We need to see if there exists either
     * a Managed Entitlement that was created during Account Agg, or a corresponding AccountGroup for the
     * given application/value
     */
    public static ManagedAttribute getEntitlementOrObject(SailPointContext ctx, Application app, String groupAttribute, String value, String type)
    throws GeneralException {
        ManagedAttribute found = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application.id", app.getId()));

        if(Util.isNotNullOrEmpty(groupAttribute)) {
            ops.add(Filter.ignoreCase(Filter.eq("attribute", groupAttribute)));
        } else {
            ops.add(Filter.or(Filter.eq("type", type),
                              Filter.eq("type", ManagedAttribute.Type.Entitlement.name())));
        }

        // case insensitive index
        ops.add(Filter.ignoreCase(Filter.eq("value", value)));

        List<ManagedAttribute> result = ctx.getObjects(ManagedAttribute.class, ops);
        if (result != null && result.size() > 0) {
            if (result.size() > 1) {
                if (log.isWarnEnabled())
                    log.warn("More than one matching ManagedAttribute: " +
                            app.getId() + ", " + value + ", " + type + ", " + groupAttribute);
            }
            found = result.get(0);
        }
        return found;

    }

    private static QueryOptions getQueryOptions(String appid,
                                                boolean permission, String name, String value, String objectType) {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application.id", appid));

        if (Util.isNotNullOrEmpty(name)) {
            ops.add(Filter.ignoreCase(Filter.eq("attribute", name)));
        }

        if (permission) {
            ops.add(Filter.eq("type", ManagedAttribute.Type.Permission.name()));
        } else {

            if (Util.isNotNullOrEmpty(objectType)) {
                ops.add(Filter.eq("type", objectType));
            } else {
                //Assume anything other than Permission is a form of Entitlement (group/complexAttribute) -rap
                // jsl - is a null check still needed here?  System code will never set this to null
                ops.add(Filter.or(Filter.isnull("type"),
                        Filter.ne("type", ManagedAttribute.Type.Permission.name())));
            }

            // case insensitive index
            ops.add(Filter.ignoreCase(Filter.eq("value", value)));
        }

        return ops;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public ManagedAttributer(SailPointContext context) {
        this.context = context;
    }

    /**
     * @exclude
     * Disable duplicate checking when bootstrapping.
     * This is used only for unit testing so we can force collisions and verify
     * that they are handled.
     */
    public void setDisableExistenceCheck(boolean b) {
        _disableExistenceCheck = b;
    }

    /**
     * @exclude
     * Return the number of unique violations encountered.  Used with
     * _disableExistenceCheck to test uniqueness checking.
     */
    public int getUniqueViolations() {
        return _uniqueViolations;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // STATISTICS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public int getTotalCreated() {
        return this.totalCreated;
    }

    public Map<String,Integer> getCreatedByApp() {
        return this.createdByApp;
    }

    /**
     * @exclude
     */
    public void saveResults(TaskResult result) {
        result.setInt(RET_CREATED, this.totalCreated);
        //result.setAttribute(RET_CREATED_BY_APP, formatCreatedByApp());
    }

    public void restoreFromTaskResult(TaskResult result) {
        if (result != null) {
            this.totalCreated = result.getInt(RET_CREATED);
        }
    }

    /**
     * @exclude
     */
    public void traceStatistics() {
        System.out.println(this.totalCreated + " managed attributes created.");
        System.out.println("Managed attributes created by application: " + formatCreatedByApp());
    }
    
    /**
     * Format a message that shows the number of managed attributes created for
     * each application.
     */
    private String formatCreatedByApp() {
        StringBuffer sb = new StringBuffer();
        String sep = "";
        for (Map.Entry<String,Integer> entry : this.createdByApp.entrySet()) {
            sb.append(sep).append(entry.getKey()).append(" = ").append(entry.getValue());
            sep = ", ";
        }
        return sb.toString();
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PROMOTION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Promote the entitlement attributes and permissions on the given link into
     * ManagedAttributes.
     *
     * @param link
     * @return List<ManagedAttribute> list of promoted managed attributes
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ManagedAttribute> promoteManagedAttributes(Link link)
        throws GeneralException {

        List<ManagedAttribute> added = new ArrayList<ManagedAttribute>();

        // Promote the attributes that are marked as entitlements.
        Application app = link.getApplication();
        Schema schema = app.getAccountSchema();

        if (schema != null) {
            // formerly we promoted anything declared entitlement=true, now
            // we require a specific managed=true flag
            List<AttributeDefinition> atts = schema.getAttributes();
            if (atts != null) {
                for (AttributeDefinition att : atts) {
                    if (att.isManaged()) {
                        String attname = att.getName();
                        String objType = Util.isNotNullOrEmpty(att.getSchemaObjectType()) ? att.getSchemaObjectType()
                                                                            : ManagedAttribute.Type.Entitlement.name();
                        boolean isGroupAttribute = app.hasGroupSchema(objType);
                        Object v = link.getAttribute(attname);
                        List<Object> values = Util.asList(v);
                        if ( values != null ) {

                            // Create a copy of the values.  If this is account is being processed by multiple threads
                            // in different partitions (which is a no-no, but can happen) then the values may get
                            // updated underneath us, which leads to a ConcurrentModificationException.
                            values = new ArrayList<Object>(values);

                            for ( Object obj : values ) {
                                if( obj != null ) {
                                    String strVal = obj.toString();
                                    ManagedAttribute managedAttr = new ManagedAttribute(app, objType, attname, strVal);

                                    // Groups inherit their application's scope when the Group Aggregator creates them,
                                    // so let's do it here too to be consistent
                                    if (isGroupAttribute) {
                                        managedAttr.setAssignedScope(app.getAssignedScope());
                                    }

                                    if (att.isEntitlement()) {
                                        // Entitlements will be requestable based on the application and corresponding schema configuration
                                        boolean createAsRequestable = app.isSchemaRequestable(objType);
                                        managedAttr.setRequestable(createAsRequestable);
                                    }

                                    findOrCreate(managedAttr, added);
                                }
                            }
                        }
                    }
                }
            }

            // Promote the permissions.
            if (!schema.isNoPermissionPromotion())
                promotePermissions(app, link.getPermissions(), added);
        }

        return added;
    }

    /***
     * Promote the permissions in the given ManagedAttribute into 
     * other ManagedAttributes.
     *
     * @param group
     * @return List<ManagedAttribute> list of promoted attributes
     * @throws GeneralException
     */
    public List<ManagedAttribute> promoteManagedAttributes(ManagedAttribute group)
        throws GeneralException {

        List<ManagedAttribute> added = new ArrayList<ManagedAttribute>();

        // Could check the schema.isNoPermissionPromotion flag for the group
        // schema too.  If if they've bothered to agg groups, it seems like
        // they would always want this too?
        promotePermissions(group.getApplication(), group.getPermissions(), added);

        return added;
    }
    
    /**
     * Promote the given permissions. 
     */
    private void promotePermissions(Application app, List<Permission> perms,
                                    List<ManagedAttribute> added)
        throws GeneralException {

        if (null != perms) {
            for (Permission p : perms) {
                ManagedAttribute managedAttr = new ManagedAttribute(app, p);                
                // what about requestable?
                findOrCreate(managedAttr, added);
            }
        }
    }  
    
    /**
     * Find an existing ManagedAttribute that matches the given attribute, and
     * automatically create and persist a new ManagedAttribute if it doesn't
     * exist.
     */
    private void findOrCreate(ManagedAttribute attr, List<ManagedAttribute> added)
        throws GeneralException {
        
        ManagedAttribute neu = bootstrapIfNew(attr);
        if (neu != null)
            added.add(neu);
    }

    /**
     * Bootstrap an attribute if one does not already exist.
     * Factored out of findOrCreate so it can be accessible to
     * MissingManagedEntitlementsTask.
     *
     * @param src
     * @return ManagedAttribute newly bootstrapped attribute
     * @throws GeneralException
     */
    public ManagedAttribute bootstrapIfNew(ManagedAttribute src)
        throws GeneralException {

        ManagedAttribute neu = null;

        String msg = checkValidity(src);
        if (msg != null)
            throw new GeneralException(msg);
        
        // jsl - for testing the unique constraint, allow the existance
        // check to be disabled, this can be removed after awhile
        ManagedAttribute existing = null;
        if (!_disableExistenceCheck) {
            Application app = src.getApplication();
            if (src.isPermission()) {
                existing = get(this.context, app.getId(), src.isPermission(),
                               src.getAttribute(), src.getValue(), src.getType());
            }
            else {
                // Need to query with type Entitlement/GroupType
                existing = getEntitlementOrObject(this.context, app,
                                                  src.getAttribute(), src.getValue(), src.getType());
            }
        }

        if (existing == null) {
            neu = bootstrap(src, true);
        }

        return neu;
    }

    /**
     * Run the managed attribute customization rule (if configured) and save the
     * ManagedAttribute.  Returns null if we detect a duplicate.
     *
     * @param attr
     * @return ManagedAttribute
     * @throws GeneralException
     */
    private ManagedAttribute bootstrap(ManagedAttribute attr, boolean useNewContext)
        throws GeneralException {

        ManagedAttribute created = null;
        Application app = attr.getApplication();

        if (log.isInfoEnabled()) {
            // skip the application, it's usually obvious from the name
            String logname = attr.getAttribute() + "=" + attr.getValue();
            log.info("New ManagedAttribute: " + logname,
                     new GeneralException("New ManagedAttribute: " + logname));
        }

        // Call a customization rule if configured.
        callCustomizationRule(attr, app);

        attr.setHash(getHash(attr));

        SailPointContext neuCtx = null;
        try {
            if (useNewContext) {
                if (log.isInfoEnabled()) {
                    log.info("Creating private context to bootstrap ManagedAttribute");
                }

                if (null != app.getManagedAttributeCustomizationRule() &&
                        null != attr.getOwner()) {
                    //IIQMAG-3129 :- reloading the object for the new private session
                    attr.getOwner().load();
                }

                neuCtx = SailPointFactory.createPrivateContext();
                neuCtx.saveObject(attr);
                neuCtx.commitTransaction();
            } else {
                this.context.saveObject(attr);
                this.context.commitTransaction();
            }
            created = attr;
        }
        catch (Throwable t) {
            if (Util.isAlreadyExistsException(t)) {
                // ignore, return null
                if (log.isInfoEnabled()) {
                    log.info("Caught duplication attempt: " + getDescription(attr));
                }
                _uniqueViolations++;
            }
            else {
                throw t;
            }
        } finally {
            if (neuCtx != null) {
                SailPointFactory.releasePrivateContext(neuCtx);
            }
        }
        
        // prevents cache problems when lots of these have to be processed
        // jsl - now that these have collections probably not!  but we're
        // usually in an agg/refresh task that will full decache eventually
        if (!useNewContext) {
            this.context.decache(attr);
        }

        if (created != null) {
            // Increment statistics about creating a managed attribute.
            incrementStats(app);
        }
        
        return created;
    }

    /**
     * Call a customization rule (if configured) to customize the given
     * ManagedAttribute.  This can set the owner, whether the attribute is
     * requestable, and possibly even some descriptions.
     */
    private ManagedAttribute callCustomizationRule(ManagedAttribute attr,
                                                   Application app)
        throws GeneralException {
        
        Rule rule = getCustomizationRule(app);
        if (null != rule) {

            //System.out.println("Calling customization rule: " + rule.getName());

            Map<String,Object> params = new HashMap<String,Object>();
            params.put("attribute", attr);
            params.put("application", app);
            params.put("state", this.state);
            
            // The rule modifies the attribute handed to it, so we don't
            // return anything.  Consider allowing some sort of return that
            // will prevent saving the attribute.
            this.context.runRule(rule, params);
        }
        
        return attr;
    }
    
    /**
     * Return the managed attribute customization rule to run if configured.
     */
    private Rule getCustomizationRule(Application app) throws GeneralException {
        
        // Try to get this from the application first.
        Rule rule = app.getManagedAttributeCustomizationRule();
        
        // Fallback to a global rule specified in system config.
        if (null == rule) {
            String ruleName =
                this.context.getConfiguration().getString(Configuration.MANAGED_ATTRIBUTE_CUSTOMIZATION_RULE);
            if (null != ruleName) {
                rule = this.context.getObjectByName(Rule.class, ruleName);
            }
        }
        
        return rule;
    }
    
    /**
     * A new managed attribute was created on the given application ... update
     * the stats accordingly.
     */
    private void incrementStats(Application app) {
        
        // Increment the total number created.
        this.totalCreated++;
        
        // Increment the number created per app.
        Integer total = this.createdByApp.get(app.getName());
        if (total == null) {
           total = 0;
        }
        total++;
        this.createdByApp.put(app.getName(), total);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Hashing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Format a description of the attribute for use in log messages.
     */
    public static String getDescription(ManagedAttribute att) {

        // tolerate corrupted attributes
        Application app = att.getApplication();
        String attname = (app != null) ? app.getName() : "???";

        return (attname + ", " +
                att.getType() + ", " +
                att.getAttribute() + ", " +
                att.getValue());
    }

    /**
     * Check to see if the ManagedAttribute is fleshed out enough to 
     * have a valid hash.  
     *
     * Used within ManagedAttributer to ensure we don't save an invalid
     * object.  Used by the ManagedAttributeHashUpgrader to detect invalid
     * objects and remove them.
     */
    public static String checkValidity(ManagedAttribute att) {
        
        String msg = null;
        String type = att.getType();
        
        if (att.getApplication() == null) {
            msg = "ManagedAttribute missing application: " + getDescription(att);
        }
        else if (type == null) {
            msg = "ManagedAttribute missing type: " + getDescription(att);
        }
        else if (ManagedAttribute.Type.Permission.name().equals(type)) {
            // permissions are weird, the target is stored in the attribute column
            if (att.getAttribute() == null) {
                msg = "ManagedAttribute missing permission target: " + getDescription(att);
            }
        }
        else if (att.getValue() == null) {
            msg = "ManagedAttribute missing value: " + getDescription(att);
        }
        else if (ManagedAttribute.Type.Entitlement.name().equals(att.getType()) &&
                 att.getAttribute() == null) {
            msg = "ManagedAttribute missing attribute: " + getDescription(att);
        }

        return msg;
    }

    /**
     * Calculate the unique hash for an attribute.
     * The MA must be valid.  See file header comments for situations
     * where attribute and value may be null.
     *
     * value can be null for type=Permission
     * attribute can be null for nested schemas, don't include attribute name
     * in the hash.
     */
    public static String getHash(ManagedAttribute attr) throws GeneralException {

        String msg = checkValidity(attr);
        if (msg != null)
            throw new GeneralException(msg);

        Application app = attr.getApplication();
        String appid = (app != null) ? app.getId() : null;
        String type = attr.getType();
        String attribute = attr.getAttribute();
        String value = attr.getValue();

        return getHash(appid, type, attribute, value);
     }

    /**
     * Calculate the unique hash for an attribute.
     * This is used by CachedManagedAttributer to generate a key for the cache.
     * Since we may not have an actual ManagedAttribute when checking the cache, this version calculates
     * the hash using the constituent parts.
     *
     * @param appId Application ID for the ManagedAttribute
     * @param type Type of the ManagedAttribute
     * @param attribute Attribute name of the ManagedAttribute
     * @param value Attribute value of the ManagedAttribute
     * @return Hashed value as a string
     */
    public static String getHash(String appId, String type, String attribute, String value) {
        if (ManagedAttribute.Type.Permission.name().equals(type)) {
            // permissions are weird, the target is stored in the attribute column
            type = attribute;
        }
        else if (ManagedAttribute.Type.Entitlement.name().equals(type)) {
            // move the attribute name to the type
            type = attribute;
        }

        return getHash(appId, type, value);
    }

    /**
     * Return the hash for a Permission.
     * Used by the new certification generator.
     * Here we're comparing a single Permission from within an EntitlementGroup
     * (aka exceptions) to see if it matches the result of a filter
     * on the ManagedAttribute table.  If this permission were promoted to the
     * MA table, MA type would be Permission, and the permission target would
     * be stored in the attribute name column.  The value column is null.
     */
    public static String getPermissionHash(Application app, String target) {

        return getHash(app.getId(), target, null);
    }

    /**
     * Return the hash for an entitlment.
     * Used by the new certification generator.
     * Here we're comparing an attribute name and value within an EntitlementGroup
     * (aka exceptions) to see if it matches the result of a filter
     * on the ManagedAttribute table.  The "type" for the hash is the 
     * attribute name.
     */
    public static String getEntitlementHash(Application app, String attName, String value) {

        return getHash(app.getId(), attName, value);
    }

    /**
     * Build the hash from the three component parts.
     */
    private static String getHash(String appId, String type, String value) {

        // Putting a delimiter to avoid the rare but possible "shifting" problem between the type name
        // and the value.  E.g. type=a, value=aa vs. type=aa, value=a
        // This means that schema names or schema attribute names cann't end with the delimiter

        String src = appId + "|" + type + "|" + value;
        String hash = DigestUtils.sha1Hex(src);

        return hash;
    }
    
}
