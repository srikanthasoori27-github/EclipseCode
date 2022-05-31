package sailpoint.transformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Identity.WorkgroupNotificationOption;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectAttribute.EditMode;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

/**
 * A Transformer responsible for converting an identity to a Identity Model (a Map Model for the 
 * Identity object).
 * <p>
 * Root level primitive attributes will be available by default, i.e. things like email, firstname, etc. Referenced objects
 * which only contain a list of IDs on the top level attribute.  Further information about these attributes can be obtained 
 * from the info namespace on the identity object. The meta data for these references will be held in the identity.info Map. If any of the references need 
 *  to change, update the Identity."attribute" with the new IDs.
 *  We will treat identity.info as read only data. This data will be refreshed when any of the reference ids are changed. Any
 *  changes set in the info will not be acknowledged by the transformer. Please update the top level attribute id in order for the 
 *  info Data to change.
 * </p>
 * Unless otherwise noted see the description in the javadoc for Identity to understand what the field names are. 
 * The Map Model for an identity is as follows:
 * <pre>
 * attribute                type            description
 * ------------------------------------------------------
 * id                       string          unique identifier for the identity
 * name                     string
 * correlated               boolean         
 * protected                boolean         
 * lastLogin                date
 * lastRefresh              date
 * managerStatus            boolean         
 * password                 string          will not be included in the Model however, is a placeholder for use in provisioning plans
 * correlatedOverriden      boolean
 * passwordExpiration       date            
 * isWorkgroup              boolean         if this identity represents a workgroup
 * controlsAssignedScope    boolean  
 * assignedScope            string          id of the assigned scope assigned to this identity       
 * manager                  string          name of the manager. if you want more information see the info.manager object
 * capabilities             stringList      a list of names that contain the capabilities. see info.capabilities
 * assignedRoles            stringList      a list of names containing assigned roles
 * detectedRoles            stringList      a list of names containing detected roles in the system
 * workgroups               stringList      a list of names containing the workgroups assigned to this identity
 * controlledScopes         stringList      a list of ids (scopes do not have to have unique names) of type string containing the controlScopes assigned to this identity
 * links                    LinkList        a list of link objects, see LinkTransformer javadoc for more information about those fields
 * info                     namespace       e.g. info.capabilities.name will get you the name of a capability
 * info.manager             Manger
 * info.manager.id          string          the id of the manager
 * info.manager.displayName string          display name of the manager
 * info.manager.name        string          unique name of the manager
 * info.capabilities                CapabilitiesList    
 * info.capabilities.id           string    
 * info.capabilities.name           string    
 * info.capabilities.displayName    string        
 * info.capabilities.description    string
 * info.assignedRoles               AssignedRoleList        
 * info.assignedRoles.id          string        
 * info.assignedRoles.name          string        
 * info.assignedRoles.startDate     date        
 * info.assignedRoles.endDate       date        
 * info.assignedRoles.assigner      string
 * info.assignedRoles.source        string
 * info.assignedRoles.date          date
 * info.assignedRoles.negative      boolean
 * info.detectedRoles               DetectedRolesList
 * info.detectedRoles.id          string
 * info.detectedRoles.name          string
 * info.detectedRoles.displayName   string
 * info.workgroups                  WorkgroupList
 * info.workgroups.id             string
 * info.workgroups.name             string
 * info.workgroups.displayName      string
 * info.workgroups.notificationOption   string
 * info.controlledScopes            ControlledScopeList
 * info.controlledScopes.id       string
 * info.controlledScopes.name       string
 * info.controlledScopes.displayName       string
 * info.objectConfig                ObjectConfigList
 * info.objectConfig.name
 * info.objectConfig.displayName
 * info.objectConfig.type
 * info.objectConfig.isMulti
 * info.objectConfig.isSystem       boolean
 * info.objectConfig.isStandard     boolean
 * info.objectConfig.editMode       ReadOnly, Permanent, UntilFeedValueChanges
 * 
 * 
 *         
 * </pre>
 * The system defined attributes are defined below. Custom attributes can also be found at the root level of the identity object, e.g.
 * if you defined an Identity Attribute location you can reference that value as location.
 * 
 * <pre>
 * attribute                type            description
 * ------------------------------------------------------
 * firstname                string          
 * lastname                 string          
 * inactive                 boolean
 * email                    string
 * displayName              string          typically full name
 * </pre>
 *
 * @see sailpoint.object.Identity
 * @see sailpoint.transformer.LinkTransformer
 */
public class IdentityTransformer extends AbstractTransformer<Identity> {
    
    private static Log log = LogFactory.getLog(IdentityTransformer.class);
    
    // you may be wondering why some two word constants are separated with
    // an underscore and why some are not.  If the value is camel-cased 
    // then lets use underscore between words. if not then munge them 
    // together like jobtitle=ATTR_JOBTITLE rather than jobTitle=ATTR_JOB_TITLE
    public static final String ATTR_CORRELATED = "correlated";
    public static final String ATTR_PROTECTED = "protected";
    public static final String ATTR_LAST_LOGIN = "lastLogin";
    public static final String ATTR_LAST_REFRESH = "lastRefresh";
    public static final String ATTR_MANAGER_STATUS = "managerStatus";
    public static final String ATTR_PASSWORD = "password";
    public static final String ATTR_CORRELATED_OVERRIDEN = "correlatedOverriden";
    public static final String ATTR_PASSWORD_EXPIRATION = "passwordExpiration";
    public static final String ATTR_IS_WORKGROUP = "isWorkgroup";
    public static final String ATTR_ASSIGNED_SCOPE = "assignedScope";
    public static final String ATTR_CONTROLS_ASSIGNED_SCOPE = "controlsAssignedScope";
    public static final String ATTR_FIRSTNAME = "firstname";
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_LASTNAME = "lastname";
    public static final String ATTR_MANAGER = "manager";
    public static final String ATTR_CAPABILITIES = "capabilities";
    public static final String ATTR_ASSIGNED_ROLES = "assignedRoles";
    public static final String ATTR_DETECTED_ROLES = "detectedRoles";
    public static final String ATTR_WORKGROUPS = "workgroups";
    public static final String ATTR_CONTROLLED_SCOPES = "controlledScopes";
    public static final String ATTR_NOTIFICATION_OPTION = "notificationOption";
    public static final String ATTR_OBJECT_CONFIG = "objectConfig";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_IS_MULTI = "isMulti";
    public static final String ATTR_IS_SYSTEM = "isSystem";
    public static final String ATTR_IS_STANDARD =  "isStandard";
    public static final String ATTR_EDIT_MODE = "editMode";
    
    
    /**
     * Bundle / Assigned Role / Detected Role constants
     */
    public static final String ATTR_START_DATE = "startDate";
    public static final String ATTR_END_DATE = "endDate";
    public static final String ATTR_ASSIGNER = "assigner";
    public static final String ATTR_SOURCE = "source";
    public static final String ATTR_DATE = "date";
    public static final String ATTR_NEGATIVE = "negative";
    
    /**
     * Attribute in the map model that will include the Link MapModels.
     */
    public static final String ATTR_LINKS = "links";   
    
    /**
     * Internal attribute that will tell MapUtils what to use by default when 
     * searching for values in lists of maps and the property is not
     * specified.
     */
    public static final String ATTR_SEARCH_PROPERTIES = "sys.defaultSearchKeys";
    
    /**
     * option which can be set on the identity to expand referenced objects like
     * capabilities, assigned roles etc. 
     */
    public static final String OP_IDENTITY_EXPAND = "expandIdentity";
    
    /**
     * option set on the link transformation to all link attributes 
     */
    public static final String OPT_LINK_EXPAND = "expandLinks";
    
    /**
     * Static filter that indicates that when searching for Identities
     * include only workgroups.
     */
    private static final Filter WG_FILTER = Filter.eq("workgroup", true);
    
    public IdentityTransformer(SailPointContext ctx) {
        this(ctx, null);
    }
    
    public IdentityTransformer(SailPointContext ctx, Map<String,Object> optMap) {
        context = ctx;
        setOptions(optMap);
    }

    /* Transforms the identity into a model as defined in the javadoc of this class.
     * @see sailpoint.transformer.Transformer#toMap(java.lang.Object)
     */
    @Override
    public Map<String, Object> toMap(Identity identity) throws GeneralException {
        Map<String, Object> retMap = new HashMap<String, Object>();
        if (identity == null) {
            return retMap;
        }
        
        appendBaseSailPointObjectInfo(identity, retMap);
        appendIdentityAttributes(retMap, identity);
        appendLinkModel(retMap, identity.getLinks());
        appendCapabilities(retMap, identity);
        appendAssignedRoles(retMap, identity);
        appendDetectedRoles(retMap, identity);
        appendWorkgroups(retMap, identity);
        appendControlScopes(retMap, identity);
        
        if (log.isDebugEnabled()) log.debug("returning identity map model: " + retMap);
        
        return retMap;
    }
    
    /* Refresh performs a diff on root level (attributes like capabilities, assignedRoles, 
     * detectedRoles, workgroups, controlledScopes) against the info level objects. Info attributes
     * will be updated and returned in the response model.
     * @see sailpoint.transformer.Transformer#refresh(java.util.Map)
     */
    @Override
    public Map<String, Object> refresh(Map<String, Object> identityModel) throws GeneralException {
        // expect the caller wants an expanded model, so set the transformer up in such a way
        setIdentityExpand(true);
        
        appendManager(identityModel);
        
        appendCapabilities(identityModel);
        appendAssignedRoles(identityModel);
        appendDetectedRoles(identityModel);
        appendWorkgroups(identityModel);
        appendControlScopes(identityModel);
        
        if (log.isDebugEnabled()) log.debug("returning identity map model: " + identityModel);
        
        return identityModel;
    }
    
    public void setIdentityExpand(boolean value) {
        setOption(OP_IDENTITY_EXPAND, value);
    }
    
    public boolean isIdentityExpand() {
        return Util.otob(getOption(OP_IDENTITY_EXPAND));
    }
    
    public void setLinkExpand(boolean value) {
        setOption(OPT_LINK_EXPAND, value);
    }
    
    public boolean isLinkExpand() {
        return Util.otob(getOption(OPT_LINK_EXPAND));
    }
    
    private void appendIdentityAttributes(Map<String, Object> identityModel, Identity identity) 
        throws GeneralException {
        
        MapUtil.put(identityModel, ATTR_FIRSTNAME, identity.getFirstname());
        MapUtil.put(identityModel, ATTR_LASTNAME, identity.getLastname());
        MapUtil.put(identityModel, ATTR_CORRELATED, identity.isCorrelated());
        MapUtil.put(identityModel, ATTR_PROTECTED, identity.isProtected());
        MapUtil.put(identityModel, ATTR_LAST_LOGIN, identity.getLastLogin());
        MapUtil.put(identityModel, ATTR_LAST_REFRESH, identity.getLastRefresh());
        MapUtil.put(identityModel, ATTR_MANAGER_STATUS, identity.getManagerStatus());
        MapUtil.put(identityModel, ATTR_CORRELATED_OVERRIDEN, identity.isCorrelatedOverridden());
        MapUtil.put(identityModel, ATTR_PASSWORD_EXPIRATION, identity.getPasswordExpiration());
        MapUtil.put(identityModel, ATTR_IS_WORKGROUP, identity.isWorkgroup());
        String assignedScopeId = null;
        Scope scope = identity.getAssignedScope();
        if (scope != null) {
            assignedScopeId = scope.getId();
        }
        // scopes not unique by name, using Id here 
        MapUtil.put(identityModel, ATTR_ASSIGNED_SCOPE, assignedScopeId);
        // allow null as this represents default to the system config
        MapUtil.put(identityModel, ATTR_CONTROLS_ASSIGNED_SCOPE, identity.getControlsAssignedScope());
        
        // Let the object config guide which attributes make their 
        // way into the map model
        ObjectConfig config = Identity.getObjectConfig();
        if ( config != null ) {
            List<ObjectAttribute> objAttrs = config.getObjectAttributes();
            if ( objAttrs != null ) {
                for ( ObjectAttribute attr : objAttrs ) {

                    // don't include system stuff
                    if ( !attr.isSystem() ) {
                        String name = attr.getName();
                        Object value = identity.getAttribute(name);
                        if (!attr.isIdentity()) {
                            putIfNotNull(identityModel, name, value);
                        }
                        else if (value instanceof Identity) {
                                Identity extIdentity = (Identity) value;
                                putIfNotNull(identityModel, name, extIdentity.getName());
                        }
                    }
                }
                appendAttributeConfig(identityModel, objAttrs);
            }
        }        

        //TODO see below comment, as this feels pretty hackey...
        /**
         * DO NOT MOVE this manager block above the object config block
         * manager is a attribute which needs to be expanded and is also 
         * included in the object config.
         */
        Identity manager = identity.getManager();
        appendManager(identityModel, manager);
        
    }
    
    private void appendManager(Map<String, Object> identityModel) throws GeneralException {
        
        String managerName =  Util.otos(MapUtil.get(identityModel, ATTR_MANAGER));
        if (managerName == null) {
            MapUtil.put(identityModel, prefixWithInfo(ATTR_MANAGER), null);
            return;
        }
        
        // check to see if the info.manager name is different if so refresh
        Object o = MapUtil.get(identityModel, prefixWithInfo(ATTR_MANAGER));
        if (o == null) {
            o = new HashMap<String, Object>();
        }

        if (o instanceof Map) {
            Map<String, Object> infoManager = (Map<String, Object>)o;
            
            if (! managerName.equals(MapUtil.get(infoManager, ATTR_NAME))) {
                log.info("refreshing manager: " + managerName);
                Identity manager = context.getObjectByName(Identity.class, managerName);
                appendManager(identityModel, manager);
            }
        }
        else log.info("manager info object is not an instance of Map for model with manager name: " + managerName);
    }

    private void appendManager(Map<String, Object> identityModel, Identity manager) throws GeneralException {
        if ( manager != null ) {
            Map<String,Object> managerMap = new HashMap<String,Object>();
            MapUtil.put(managerMap, ATTR_DISPLAY_NAME, manager.getDisplayName());
            MapUtil.put(managerMap, ATTR_NAME, manager.getName());
            MapUtil.put(managerMap, ATTR_ID, manager.getId());
            MapUtil.put(identityModel, prefixWithInfo(ATTR_MANAGER), managerMap);
            
            MapUtil.put(identityModel, ATTR_MANAGER, manager.getName());
        }
    }
    
    private void appendAttributeConfig(Map<String, Object> identityModel, List<ObjectAttribute> objAttrs) throws GeneralException {
        if ( isIdentityExpand() ) {
            List<Map<String, Object>> objConfigList = new ArrayList<Map<String, Object>>();
            
            for (ObjectAttribute attr : Util.safeIterable(objAttrs)) {
                Map<String, Object> attrMap = new HashMap<String, Object>();
                
                MapUtil.put(attrMap, ATTR_NAME, attr.getName());
                MapUtil.put(attrMap, ATTR_DISPLAY_NAME, attr.getDisplayName());
                MapUtil.put(attrMap, ATTR_NAME, attr.getName());
                // default to string if no type exists
                MapUtil.put(attrMap, ATTR_TYPE, (attr.getType() != null) ? attr.getType() : BaseAttributeDefinition.TYPE_STRING);
                MapUtil.put(attrMap, ATTR_IS_MULTI, attr.isMulti());
                MapUtil.put(attrMap, ATTR_IS_STANDARD, attr.isStandard());
                MapUtil.put(attrMap, ATTR_IS_SYSTEM, attr.isSystem());
                MapUtil.put(attrMap, ATTR_EDIT_MODE, (attr.getEditMode() != null) ? attr.getEditMode().name() : EditMode.ReadOnly.name());
                
                objConfigList.add(attrMap);
            }
            
            putIfNotEmpty(identityModel, prefixWithInfo(ATTR_OBJECT_CONFIG), objConfigList);
        }
    }

    private void appendControlScopes(Map<String, Object> identityModel) throws GeneralException {
        appendControlScopes(identityModel, null);
    }
            
    /**
     * controlled scopes are not unique by name so must use id as the identifier when iterating scopes
     * @param identityModel
     * @param identity
     * @throws GeneralException
     */
    private void appendControlScopes(Map<String, Object> identityModel, Identity identity)
            throws GeneralException {
        
        List<String> mapModelNames = Arrays.asList(ATTR_ID, ATTR_NAME, ATTR_DISPLAY_NAME);
        
        if (isIdentityIdNotNull(identity)) {
            List<String> dbProperties = Arrays.asList("controlledScopes.id", "controlledScopes.name", "controlledScopes.displayName");
            appendProjectionQueryInfo(identityModel, ATTR_CONTROLLED_SCOPES, identity, mapModelNames, dbProperties);
        }
        else {
            List<String> dbProperties = Arrays.asList("id", "name", "displayName");
            appendProjectionQueryInfo(identityModel, ATTR_CONTROLLED_SCOPES, Scope.class, ATTR_ID, mapModelNames, dbProperties);
        }

    }
    
    private void appendWorkgroups(Map<String, Object> identityModel) throws GeneralException {
        appendWorkgroups(identityModel, null);
    }

    private void appendWorkgroups(Map<String, Object> identityModel, Identity identity) throws GeneralException {
        
        List<String> mapModelNames = Arrays.asList(ATTR_NAME, ATTR_ID, ATTR_DISPLAY_NAME, ATTR_NOTIFICATION_OPTION);
        
        if (isIdentityIdNotNull(identity)) {
            List<String> dbProperties = Arrays.asList("workgroups.name", "workgroups.id", "workgroups.displayName", "workgroups.preferences");
            appendProjectionQueryInfo(identityModel, ATTR_WORKGROUPS, identity, mapModelNames, dbProperties);
        }
        else {
            List<String> dbProperties = Arrays.asList("name", "id", "displayName", "preferences");
            appendProjectionQueryInfo(identityModel, ATTR_WORKGROUPS, Identity.class, ATTR_NAME, mapModelNames, dbProperties);
        }
        
        Object workgroups = MapUtil.get(identityModel, prefixWithInfo(ATTR_WORKGROUPS));
        if (workgroups instanceof List) {
            replaceNotificationOptions((List<Map<String, Object>>) workgroups);
        }
    }
    
    /**
     * replaces the value of the notification option (currently a map of the preferences after retrieved from the DB)
     * with the true value of the workgroup notification
     * @param workgroupList
     */
    private void replaceNotificationOptions(List<Map<String, Object>> workgroupList) {
        
        if (workgroupList != null) {
            for (Map<String, Object> map : workgroupList) {
                
                if (map.get(ATTR_NOTIFICATION_OPTION) instanceof Map) {
                    
                    Object o = map.remove(ATTR_NOTIFICATION_OPTION);
                    Map<String, Object> mapPreferences = (Map<String, Object>)o;
                    Object notifObj = mapPreferences.get(Identity.PRF_WORKGROUP_NOTIFICATION_OPTION);
                    if (notifObj instanceof WorkgroupNotificationOption) {
                        WorkgroupNotificationOption option = (WorkgroupNotificationOption)notifObj;
                        map.put(ATTR_NOTIFICATION_OPTION, option.name());
                    }
                }
                
            }
        }
    }
    
    private void appendAssignedRoles(Map<String, Object> identityModel) throws GeneralException {
        List<String> mapModelNames = Arrays.asList(ATTR_NAME, ATTR_ID);
        List<String> dbProperties = Arrays.asList("name", "id");
        
        appendProjectionQueryInfo(identityModel, ATTR_ASSIGNED_ROLES, Bundle.class, ATTR_NAME, mapModelNames, dbProperties);
    }
    
    private void appendAssignedRoles(Map<String, Object> identityModel, Identity identity)
            throws GeneralException {
        
        List<Map<String, Object>> assignedList = new ArrayList<Map<String, Object>>();
        List<String> assignedNames = new ArrayList<String>();
        
        List<Bundle> assignedBundles = identity.getAssignedRoles();

        // Bug #19935 US3105 TA4868
        // This method and in fact the specification for how roles are represented in the map model makes it 
        // impossible to correctly transform assigned roles when an identity contains multiple role assignments for
        // the same role. In this case, only the model for the first role assignment found for a role will be 
        // included in the "info.assignedRoles.*" portion of the map, and a warning will be logged by the 
        // deprecated use of getRoleAssignment.

        if (assignedBundles != null) {
            for (Bundle bundle : assignedBundles) {
                assignedNames.add(bundle.getName());
                
                if (isIdentityExpand()) {
                    RoleAssignment ra = identity.getRoleAssignment(bundle);
                    Map<String, Object> mapModel = new HashMap<String, Object>();
                    MapUtil.put(mapModel, ATTR_ID, bundle.getId());
                    MapUtil.put(mapModel, ATTR_NAME, bundle.getName());
                    MapUtil.put(mapModel, ATTR_START_DATE, ra.getStartDate());
                    MapUtil.put(mapModel, ATTR_END_DATE, ra.getEndDate());
                    MapUtil.put(mapModel, ATTR_ASSIGNER, ra.getAssigner());
                    MapUtil.put(mapModel, ATTR_SOURCE, ra.getSource());
                    MapUtil.put(mapModel, ATTR_DATE, ra.getDate());
                    MapUtil.put(mapModel, ATTR_NEGATIVE, ra.isNegative());
                    
                    assignedList.add(mapModel);
                }
            }
        }
        
        if (! Util.isEmpty(assignedNames)) {
            MapUtil.put(identityModel, ATTR_ASSIGNED_ROLES, assignedNames);
            if (isIdentityExpand() && ! Util.isEmpty(assignedList)) {
                MapUtil.put(identityModel, prefixWithInfo(ATTR_ASSIGNED_ROLES), assignedList);
            }
        }
    }
    
    private void appendDetectedRoles(Map<String, Object> identityModel) throws GeneralException {
        appendDetectedRoles(identityModel, null);
    }
    
    private void appendDetectedRoles(Map<String, Object> identityModel, Identity identity)
            throws GeneralException {
        
        List<String> mapModelNames = Arrays.asList(ATTR_NAME, ATTR_ID, ATTR_DISPLAY_NAME);
        
        if(isIdentityIdNotNull(identity)) {
            List<String> dbProperties = Arrays.asList("bundles.name", "bundles.id", "bundles.displayableName");
            appendProjectionQueryInfo(identityModel, ATTR_DETECTED_ROLES, identity, mapModelNames, dbProperties);
        }
        else {
            List<String> dbProperties = Arrays.asList("name", "id", "displayableName");
            appendProjectionQueryInfo(identityModel, ATTR_DETECTED_ROLES, Bundle.class, ATTR_NAME, mapModelNames, dbProperties);
        }
    }

    
    private void appendCapabilities(Map<String, Object> identityModel) throws GeneralException {
        appendCapabilities(identityModel, null);
    }
    /**
     * Calling the Capabilities transformer and adds them to the identity model 
     * @param identityModel
     * @param identity
     * @throws GeneralException
     */
    private void appendCapabilities(Map<String, Object> identityModel, Identity identity) throws GeneralException {
        
        List<String> mapModelNames = Arrays.asList(ATTR_NAME, ATTR_ID, ATTR_DISPLAY_NAME, ATTR_DESCRIPTION);
        
        if (isIdentityIdNotNull(identity)) {
            List<String> dbProperties = Arrays.asList("capabilities.name", "capabilities.id", "capabilities.displayName",
                    "capabilities.description");
            appendProjectionQueryInfo( identityModel, ATTR_CAPABILITIES, identity, mapModelNames, dbProperties);
        }
        else {
            List<String> dbProperties = Arrays.asList("name", "id", "displayName", "description");
            appendProjectionQueryInfo(identityModel, ATTR_CAPABILITIES, Capability.class, ATTR_NAME, mapModelNames, dbProperties);
        }
        
    }

    /**
     * not crazy about the name of this utility method, but it uses a projection query to fill in the mapModelNames 
     * onto the mapModelList based on the results of a query using the dbProperties.  mapModelNames and dbProperties indexes must match.
     * there is currently an overhead of fetching the values from the DB even when the identityExpand option is not set.
     * Also, the expectation for mapModelNames and dbProperties are the first fields in the list being the identifiers (IDs most of the time).
     *  
     * @param identityModel the root model
     * @param field the field name to add to the model
     * @param identity identity to search on
     * @param mapModelNames the names of the fields
     * @param dbProperties the database columns to add to the map model
     * @return
     * @throws GeneralException
     */
    private void appendProjectionQueryInfo(Map<String, Object> identityModel, String field, Identity identity,
            List<String> mapModelNames, List<String> dbProperties) throws GeneralException {
        
        final String id = identity.getId();
        if (id != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("id", id));
            
            Iterator<Object[]> iter = context.search(Identity.class, ops, dbProperties);
            transformProjectionQueryInfo(iter, identityModel, field, mapModelNames);
        }
        else log.info(String.format("identity was null. did not perform transfer on field: %s", field));
    }
    
    /**
     * used in the refresh process, this method resolves differences in the root "field" and those that are expanded in the "info" namespace.
     * e.g. identity.capabilities="OLD, NEW" and identity.info.capabilities="OLD" this method will detect NEW has been added and ONLY fetch 
     * NEW from the database and add it to the existing list of info.capaabilities. 
     * 
     * @param identityModel the root model
     * @param field the field name to compare against in the model
     * @param clazzToQuery class to query by identifier changes are detected
     * @param identifierPropertyName the identifier property, name or id
     * @param mapModelNames the names of the fields
     * @param dbProperties the database columns to add to the map model
     * @throws GeneralException
     */
    private void appendProjectionQueryInfo(Map<String, Object> identityModel, String field, Class clazzToQuery,
        String identifierPropertyName, List<String> mapModelNames, List<String> dbProperties) throws GeneralException {
    
        List<String> nameOfObject = resolveDifferences(identityModel, field, identifierPropertyName);
        if (! Util.isEmpty(nameOfObject)) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.in(identifierPropertyName, nameOfObject));
            
            Iterator<Object[]> iter = context.search(clazzToQuery, ops, dbProperties);
            transformProjectionQueryInfo(iter, identityModel, field, mapModelNames);
        }
    }

    /**
     * performs transformation from database query to map model for a few of the projection queries
     * @param iter
     * @param identityModel
     * @param field
     * @param mapModelNames
     * @throws GeneralException
     */
    private void transformProjectionQueryInfo(Iterator<Object[]> iter, Map<String, Object> identityModel, String field,
            List<String> mapModelNames) throws GeneralException {
        
        List<String> projectionIds = new ArrayList<String>();
        List<Map<String, Object>> mapModelList = new ArrayList<Map<String, Object>>();
        
        while (iter != null && iter.hasNext()) {
            Object[] obj = iter.next();
            if (obj != null && obj[0] != null) {
                projectionIds.add((String)obj[0]);

                // build out the info level object as well
                if (isIdentityExpand()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (int i = 0; i < obj.length; i++) {
                        if (obj[i] != null) {
                            row.put(mapModelNames.get(i), obj[i]);
                        }
                    }
                    // add the row to the info list if not empty
                    if (! Util.isEmpty(row)) {
                        mapModelList.add(row);
                    }
                }
            }
        }
        
        if (! Util.isEmpty(projectionIds)) {
            List<String> existingNames = otoList(identityModel, field);
            addAllIfNotExist(existingNames, projectionIds);
            MapUtil.put(identityModel, field, existingNames);
            if (isIdentityExpand() && ! Util.isEmpty(mapModelList)) {
                //append to the existing model any new changes
                List<Map<String, Object>> existing = otoListMap(identityModel, prefixWithInfo(field));
                existing.addAll(mapModelList);
                MapUtil.put(identityModel, prefixWithInfo(field), existing);
            }
        }
    }

    /**
     * 
     * Call to the LinkTransfomer to get build up the Link map Model.
     *
     * @param identityModel
     * @param links
     * @throws GeneralException
     */
    private void appendLinkModel(Map<String,Object> identityModel, List<Link> links) 
        throws GeneralException {
        
        if ( links != null && isLinkExpand()) {
            List<Map<String,Object>> linkModels = new ArrayList<Map<String,Object>>();            
            LinkTransformer jetFire = new LinkTransformer(context, getOptions());
            for ( Link link : links ) {                
                Map<String,Object> linkMapModel = jetFire.toMap(link);
                addIfNotNull(linkModels, linkMapModel);
            }
            putIfNotEmpty(identityModel, "links", linkModels);
        }
    }
    
    private boolean isIdentityIdNotNull(Identity identity) {
        return identity != null && identity.getId() != null;
    }
    
    private List<String> otoList(Map<String, Object> model, String field) throws GeneralException {
        List<String> list = new ArrayList<String>();
        
        Object o = MapUtil.get(model, field);
        if (o instanceof List) {
            list = (List)o;
        }
        
        return list;
    }
    
    private List<Map<String, Object>> otoListMap(Map<String, Object> model, String field) throws GeneralException {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
                
        Object o = MapUtil.get(model, field);
        if (o instanceof List) {
            list = (List)o;
        }
        
        return list;
    }
    
    private void addAllIfNotExist(List<String> original, List<String> toAdd) {
        for (String valueToAdd : Util.safeIterable(toAdd)) {
            if (! original.contains(valueToAdd)) {
                original.add(valueToAdd);
            }
        }
    }
    
    private List<String> resolveDifferences(Map<String, Object> identityModel, String field, String identifierPropertyName) throws GeneralException {
        
       List<String> retDiffs = new ArrayList<String>();
       
       List<String> fieldList = otoList(identityModel, field);
       final String FIELD_PATH = prefixWithInfo(field);
       List<Map<String, Object>> fieldMapList = otoListMap(identityModel, FIELD_PATH);
       
       List<String> namesOfMapObjects = new ArrayList<String>(); 
       //create a list of names from the list of maps
       for (Map<String, Object> map : Util.safeIterable(fieldMapList)) {
           String name = Util.otos(map.get(identifierPropertyName));
           if (name != null) {
               namesOfMapObjects.add(name);
           }
       }
       
       if (log.isDebugEnabled()) log.debug("differencing: " + namesOfMapObjects + " against: " + fieldList);
       
       Difference theDiffs = Difference.diff(namesOfMapObjects, fieldList);
       if (theDiffs != null) {
           List<String> removed = theDiffs.getRemovedValues();
           
           if (removed != null) {
               if (log.isDebugEnabled()) log.debug("removing values: " + removed);
               
               // create a collection of maps to remove from the list
               List<Map<String, Object>> removeFromList = new ArrayList<Map<String, Object>>();
               for (Map<String, Object> map : Util.safeIterable(fieldMapList)) {
                   String name = Util.otos(map.get(identifierPropertyName));
                   if (removed.contains(name)) {
                       removeFromList.add(map);
                   }
               }
               
               fieldMapList.removeAll(removeFromList);
               // remove the collection from the model if it contains no members
               if (Util.isEmpty(fieldMapList)) {
                   MapUtil.put(identityModel, FIELD_PATH, null);
               }
               
           }
           
           retDiffs = theDiffs.getAddedValues();
       }
       
       return retDiffs;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Model to ProvisioningPlan 
    //
    ///////////////////////////////////////////////////////////////////////////


    /**
     * Produce a ProvisioningPlan that can be used to make the changes
     * produced by a modified identityModel.
     * 
     * This method is typical called after a MapModel has been modified
     * and the changes to the view need to be persisted back to an
     * Identity object. The ProvisioningPlan can be executed to
     * make the changes, through the ProvisioningEngine either using
     * one of our out of the box workflows or by calling the 
     * provisioning sub-process.
     * 
     * This method will fetch the existing version of the identity
     * from the database, build a MapModel and diff the two 
     * models which will drive the ProvisioningPlan creation. 
     * 
     * @param identityModel
     * @param ops
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan mapToPlan(
            Map<String,Object> identityModel, 
            HashMap<String,Object> ops) 
        throws GeneralException {        

        String identityId = Util.getString(identityModel, ATTR_ID);
        String identityName = Util.getString(identityModel, ATTR_NAME);
        if ( identityName == null ) 
            throw new GeneralException("Identity name was not found in MapModel.");

        // Names on identities will always be trimmed when set, so trim it here before we try to match it.
        identityName = identityName.trim();
        Identity currentIdentity = null;
        if ( identityId != null ) {
            currentIdentity = context.getObjectById(Identity.class, identityId);
        } else {
            QueryOptions qOps = new QueryOptions();
            qOps.addFilter(Filter.in(Identity.ATT_WORKGROUP_FLAG, Util.csvToList("true, false")));
            //IIQPB-879 - Don't allow creating an Identity with name set to an existing identity's ID OR an already existing name
            //Creating identities with name == an id causes issues all over if context.getObject() is called
            qOps.addFilter(Filter.or(Filter.eq("id", identityName), Filter.eq("name", identityName)));
            int num = context.countObjects(Identity.class, qOps);
            if ( num > 0 ) {
                throw new GeneralException("User '"+ identityName + "' already exists.");
                // Should this just turn into modify? 
            }
            else {
                currentIdentity = new Identity();     
            }
        }

        Map<String,Object> currentMapModel = toMap(currentIdentity);  
        ProvisioningPlan plan = new ProvisioningPlan();

        // Enable -- Disable?

        AccountRequest iiq = new AccountRequest();
        iiq.setApplication(ProvisioningPlan.APP_IIQ);
        if ( identityId != null ) {
            iiq.setOperation(AccountRequest.Operation.Modify);
        } else {
            iiq.setOperation(AccountRequest.Operation.Create);
            addAttributeRequest(iiq, ATTR_NAME, null, identityModel, currentMapModel);
        }
        Configuration sysConfig = context.getConfiguration();
        IdentityLifecycler.addUseByAttributeRequest(iiq, sysConfig);

        // The current identity will have a null name for a creation, but we
        // need one for the plan.  Add it here.  Note that we don't want to do
        // this earlier when we create the stub identity becaues it will prevent
        // an AttributeRequest for "name" from being added to the plan.
        if (null == currentIdentity.getName()) {
            currentIdentity.setName(identityName);
        }

        iiq.setNativeIdentity(identityName);      
        plan.setIdentity(currentIdentity);
        plan.add(iiq);

        if ( Difference.diffMaps(identityModel, currentMapModel) == null ) {
            // They are the same, bail out, nothing todo...
            return null;
        }        

        //
        // Handle updates to the identity attributes
        //
        ObjectConfig config = Identity.getObjectConfig();
        if ( config != null ) {
            List<ObjectAttribute> objAttrs = config.getObjectAttributes();
            if ( objAttrs != null ) {
                for ( ObjectAttribute attr : objAttrs ) {
                    if ( attr.isSystem() )
                        // don't mess with these, they aren't typically directly
                        // editable
                        continue;
                    if ( attr.isIdentity() )
                        addAttributeRequest(iiq, attr.getName(), AttributeDefinition.TYPE_IDENTITY, identityModel, currentMapModel);
                    else
                    // standard attributes are impled editable, for Create all values
                    // are editable
                    if ( attr.isEditable() || attr.isStandard() ||  AccountRequest.Operation.Create.equals(iiq.getOperation()) ) {
                        // only allow editable attributes to be updated
                        addAttributeRequest(iiq, attr.getName(), attr.getType(), identityModel, currentMapModel);
                    }
                }   
            }
        }        
        addAttributeRequest(iiq, ATTR_PASSWORD, AttributeDefinition.TYPE_SECRET, identityModel, currentMapModel);
        addAttributeRequest(iiq, ATTR_PROTECTED, AttributeDefinition.TYPE_BOOLEAN, identityModel, currentMapModel);

        addAttributeRequest(iiq, ATTR_CAPABILITIES, TYPE_CAPABILITY, identityModel, currentMapModel);
        
        addAttributeRequest(iiq, ATTR_DETECTED_ROLES, TYPE_BUNDLE, identityModel, currentMapModel);
        addAttributeRequest(iiq, ATTR_ASSIGNED_ROLES, TYPE_BUNDLE, identityModel, currentMapModel);

        addAttributeRequest(iiq, ATTR_MANAGER, AttributeDefinition.TYPE_IDENTITY, identityModel, currentMapModel);    
        addAttributeRequest(iiq, ATTR_WORKGROUPS, TYPE_WORKGROUP, identityModel, currentMapModel);
        
        //
        // This are particular because names are not unique, should we be using path?
        //
        addAttributeRequest(iiq, ATTR_CONTROLLED_SCOPES, TYPE_SCOPE, identityModel, currentMapModel);
        addAttributeRequest(iiq, ATTR_CONTROLS_ASSIGNED_SCOPE, TYPE_SCOPE, identityModel, currentMapModel);  
        addAttributeRequest(iiq, ATTR_ASSIGNED_SCOPE, TYPE_SCOPE, identityModel, currentMapModel);   
        
        if ( Util.size(iiq.getAttributeRequests()) == 0 ) {
            // nothing todo for iiq
            plan = null;
        }        
        List<Map<String,Object>> linkModels = (List<Map<String, Object>>) identityModel.get("links");
        if ( linkModels != null ) {
            LinkTransformer linker = new LinkTransformer(context, ops);
            for ( Map<String,Object> linkModel : linkModels ) {
                ProvisioningPlan linkPlan = linker.mapToPlan(linkModel, null);
                if ( linkPlan != null ) {
                    if ( plan == null )
                        plan = linkPlan;
                    else 
                        plan.merge(linkPlan);
                }
            }            
        }
        return plan;   
    }

    /**
     * Add an attribute request to an existing iiq account request.
     * 
     * Right now all sets, but we need to figure out the add/remove
     * for incremental changes for things that need to scale
     * to large numbers.
     * 
     * @param iiq accountRequest
     * @param key the name of the attribute
     * @param type maybe null, but indicates the type of the extended types
     * @param newModel the new MapModel
     * @param oldModel MapModel based on the existing db object
     * @return
     */
    @Untraced
    private AttributeRequest addAttributeRequest(AccountRequest iiq, String key, String type,  
                                                 Map<String,Object> newModel, Map<String,Object> oldModel) 
        throws GeneralException {
        AttributeRequest att = null;
        if ( key != null && newModel != null && oldModel != null ) {
            Object newValue = newModel.get(key);
            Object existing = oldModel.get(key);
            if(AttributeDefinition.TYPE_SECRET.equals(type) && newValue instanceof String) {
                newValue = context.encrypt((String)newValue);
            } else if (ATTR_NAME.equals(key) && newValue instanceof String) {
                //Always trim before comparison and creating attribute request. Name is always trimmed on identity object.
                newValue = ((String)newValue).trim();
            }

            Difference diff = Difference.diff(existing, newValue);
            if ( diff != null ) {
                att = new AttributeRequest(key, ProvisioningPlan.Operation.Set, coerce(key, type, newValue));
                att.setArguments(new Attributes<String, Object>());
                att.getArguments().put(ProvisioningPlan.ARG_TYPE, type);
                AttributeRequest inPlan = iiq.getAttributeRequest(key);
                if ( inPlan == null ) {
                    inPlan = att;
                    iiq.add(inPlan);
                } else {
                    inPlan = att;
                }
            }            
        }
        return att;
    }
    
    private final String TYPE_BUNDLE = "Bundle";
    private final String TYPE_CAPABILITY = "Capability";
    private final String TYPE_SCOPE = AttributeDefinition.TYPE_SCOPE;
    private final String TYPE_WORKGROUP = "Workgroup";
    
    /**
     * We allow any format to come back into the view, coerce it back
     * to the known type if possible.
     * 
     * @param type
     * @param val
     * @return
     */
    private Object coerce(String attrName, String type, Object val)
            throws GeneralException {

        Object coerced = val;        
        if ( Util.nullSafeEq(AttributeDefinition.TYPE_BOOLEAN, type) ) {
            coerced = Util.otob(val);
        } else
        if ( Util.nullSafeEq(AttributeDefinition.TYPE_DATE, type) ) {
            if ( val instanceof String ) {
                    throw new GeneralException("Unable to coerce value for ["+attrName+"] to value date.  Value muse be a long or java.Date.");
            }
            coerced = otoDate(val);
        } else
        if ( Util.nullSafeEq(AttributeDefinition.TYPE_IDENTITY, type) ) {
            coerced = resolveNames(Identity.class, val, null);
        } else 
        if ( Util.nullSafeEq(TYPE_CAPABILITY, type) ) {
            coerced = resolveNames(Capability.class, val, null);
        } else
        if ( Util.nullSafeEq(TYPE_SCOPE, type) ) {
          //  coerced = resolveNames(Scope.class,  val, null);
        } else
        if ( Util.nullSafeEq(TYPE_BUNDLE, type) ) {
            coerced = resolveNames(Bundle.class, val, null);
        } else
        if ( Util.nullSafeEq(TYPE_WORKGROUP, type) ) {
            coerced = resolveWorkgroupNames(val);
        }
        return coerced;
    }

    /**
     * Convert a Date or Long to
     * 
     * TOOD: move this up or to util
     * 
     * @param o
     * @return
     */
    private static Date otoDate(Object o) {
        Date date = null;
        if (o instanceof Date)
            date = (Date)o;
        else if (o instanceof Long)
            date = new Date((Long)o);        

        return date;
    }   

    /**
     * Given the list of ids or a single ID resolve the 
     * names of the workgroups.
     * 
     * @param val
     * @return
     * @throws GeneralException
     */
    private Object resolveWorkgroupNames(Object val) throws GeneralException {
        return resolveNames(Identity.class, val, WG_FILTER);        
    }

    /**
     * 
     * Given the list of values and a SailPointObject class return a 
     * an object that represents the names of the sailpoint objects.
     * 
     * This gives us some flexability with the model values so they
     * can be either ids or names.
     * 
     * Assure to collapse null values to prevent them from flowing down
     * stream to the evaluator.
     * 
     * @param clazz
     * @param val
     * 
     * @return Either a list of names or a single string representing the 
     * names of the objects.
     * 
     * @throws GeneralException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object resolveNames(Class clazz, Object val, Filter additionalFilter) 
        throws GeneralException {
        
        Object resolved = null;
        
        if ( val instanceof String ) {
            String str = (String)val;
            if ( !Util.isNullOrEmpty(str) ) {
                resolved = resolveName(clazz, str, additionalFilter);    
            }            
        } else if ( val instanceof Collection ) {
            Collection<String> col = (Collection<String>)val;
            if ( col != null ) {
                List newVal = new ArrayList<String>();
                for ( String s : col ) {
                    if ( !Util.isNullOrEmpty(s) ) {
                        String name = resolveName(clazz, s, additionalFilter);
                        if ( name != null )
                            newVal.add(name);
                    }
                }
                if ( Util.size(newVal) > 0 ) {
                    resolved = newVal;
                }
            }
        }        
        return resolved;
    }
        
    @SuppressWarnings({"rawtypes"})
    private String resolveName(Class clazz, String id, Filter additionalFilter ) 
        throws GeneralException {
        
        String name = id;
        if (id != null && ObjectUtil.isUniqueId(id)) {
            name =  resolveProperty(clazz, id, "name", additionalFilter);
        }
        return name;
    }
    
    /**
     * Resolve a single property from an object using the Id AND
     * the additionalFilter passed in.
     * 
     * @param clazz
     * @param id
     * @param property
     * @param additionalFilter
     * @return value of the property
     * 
     * @throws GeneralException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private String resolveProperty(Class clazz, String id, String property, Filter additionalFilter) 
        throws GeneralException {
        
        String propertyVal = null;
                
        QueryOptions ops = new QueryOptions();
        if ( id != null ) 
            ops.addFilter(Filter.eq("id", id));
        
        if ( additionalFilter != null )
            ops.addFilter(additionalFilter);
        
        Iterator<Object[]> iter = context.search(clazz, ops, property);
        while (iter != null && iter.hasNext()) {
            Object[] obj = iter.next();
            if ( obj != null && obj.length == 1 ) {
                propertyVal = (String)obj[0];
                // first one wins
                break;
            }                
        }    
        return propertyVal;
    }    
  
}