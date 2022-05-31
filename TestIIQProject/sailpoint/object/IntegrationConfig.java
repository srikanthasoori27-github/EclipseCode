/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An object holding configuration for an Identity Management (IDM)
 * System integration.  
 *
 * Author: Jeff
 *
 * Among the things you need to configure are:
 *
 *   - connectivity parameters (host, URL, user, password)
 *   - scope, base DN, database name, service name, or other
 *     properties necessary to specify where you need to go
 *   - list of resources (applications) managed by the system
 *   - selector for roles to sync between IIQ and the IDM system
 *   - mappings between IdentityIQ role and resource names and the names
 *     used on the IDM system
 *   - name of an "executor" class to process role sync and
 *     provisioning requests
 *
 * Though not used now, the config may be marked as a "template"
 * and have a Signature so we can ship example configurations and
 * provide a generic UI for editing them.
 *
 * It is structurally similar to a TaskItemDefinition but there
 * are enough differences I decided not to subclass that.
 * 
 * ROLE SYNCHRONIZATION
 *
 * Role synchronization is driven by the standard IIQ task 
 * "Role Synchronization" which uses the executor 
 * sailpoint.task.RoleSynchronization.  See comments in that class
 * for more information.  
 *
 * Since role models vary widely among IDM systems, we normally
 * create a simplified version of our role model for synchronization.
 * This process is called "role compilation", the result is a set
 * of "target roles".  
 *
 * roleSyncStyle=none|detectable|assignable|dual
 *   Determines the types of roles to be synchronized and how
 *   they are transformed.  The styles are defined in detail below.  
 *   Note that style is combined with the other selection properties
 *   and may result in nonsensical combinations.
 *
 * synchronizedRoles=List<Bundle>
 *   An explicit list of roles to synchronize.  This is not expected
 *   to be a common option.
 *
 * roleSyncFilter=Filter
 *   A filter to specify a subset of roles to synchronize.
 *   Typically this is used to filter roles with a particular value
 *   for an extended attribute.
 * 
 * roleSyncContainer=Bundle
 *   A role that must be in the inheritance hierarchy of all 
 *   synchronized roles.  The inheritance may either be direct or
 *   indirect (e.g. a top-level "Synchronized Roles" container
 *   with sub-containers for other useful categories).
 * 
 *   The effect is similar to using roleSyncFilter with an extended
 *   attribute, but it makes the relationship clearer in the UI because
 *   there will be a node in the tree view for the sync'd roles.
 * 
 * THINK: There is some semantic overlap between the style, filter, and 
 * container options.  We could make these mutually exclusive, but then
 * we could select roles that are both assignable and detectable which makes
 * the provisioning logic more complex.
 *
 * ROLE SYNC STYLES
 *   
 * none 
 *   Roles are not synchronized, this is the default.
 *   ProvisioningPlans sent to this IDM system must only
 *   contain entitlement attributes.  
 * 
 * detectable
 *   Detectable roles (typically typed as "IT roles") are synced.
 *   A target role is constructed by flattening all entitlement
 *   Profiles in the role into a single list of attribute vales.
 *   This flattening may be imprecise if the profile filter is complex.
 *   If the detectable role has inheritance, all inherited roles
 *   are included in the flattening.  If the role has a ProvisioningPlan
 *   is is used in the flattening instead of the Profiles.
 * 
 * assignable
 *   Assignable roles (typically typed as "business roles") are synced.
 *   A target role is constructed by flattening the entitlements
 *   defined by all required roles.  If the required roles are hierarchical
 *   the inherited roles are included in the flattening.  If the assignable
 *   role itself is hierarchical, the required roles of all inherited
 *   roles are included in the flattening.
 * 
 * dual
 *   First assignable roles are selected.  If an assignable role has 
 *   inheritance the required role list is flattened to included the required
 *   roles of all inherited roles.  
 *
 *   A set of target roles for each required role is created as described
 *   in the "detectable" style.  
 * 
 *   A set of target roles for each assignable role is created and given
 *   an extended attribute with a list of the required roles.
 *
 * PROVISIONING
 *
 * 
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Application.Feature;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class to hold configuration for an Identity Management (IDM) 
 * system integration.
 */
@Indexes({
    @Index(name="spt_integration_conf_created", property="created"),
    @Index(name="spt_integration_conf_modified", property="modified")
        })
@XMLClass
public class IntegrationConfig extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Attribute Constants
    //
    //////////////////////////////////////////////////////////////////////

    /*
     * Execution styles.  The default is REQUEST if not specified.
     * 
     * The provisioner will still try to call the executor
     * synchronously once even if style is REQUEST, then if it returns
     * a retryable error it will make a Request.  This is so the executor
     * can return a requestID through the ProvisioningPlan which we can't
     * do if we send it immediately into a Request.
     * 
     * SYNCHRONOUS means the a Request will never be made.
     * 
     */

    /**
     * Execution style allowing an asynchronous request.
     */
    public static final String EXEC_STYLE_REQUEST = "request";

    /*
     * Execution style indiciating that a synchronous request is required.
     */
    public static final String EXEC_STYLE_SYNCHRONOUS = "synchronous";


    //
    // The usual gang of connection attributes.
    //
    // Typically either host or url will be used but not both.
    //
    // username and password may be missing if communication is over
    // a secure connection and the target system will do it's own
    // proxy authentication.
    //

    public static final String ATT_HOST = "host";
    public static final String ATT_URL = "url";
    public static final String ATT_USERNAME = "username";
    public static final String ATT_NAME = "name";
    public static final String ATT_PASSWORD = "password";
    public static final String ATT_PASSWORD_CONFIRM = "passwordConfirm";

    /**
     * Attribute containing a list of <code>RoleSyncHistory</code>
     * objects, used to track which roles have already
     * been synchronized.
     *
     * @ignore
     * Putting this in the Map since it is likely to change and I
     * don't want to mess with schema upgrades.
     * This could be large and slow down loading, may want to break
     * out into another table?
     */
    public static final String ATT_ROLE_SYNC_HISTORY = "roleSyncHistory";

    /**
     * Role synchronization styles.
     * Default is "none" if not specified.
     */
    public static final String ROLE_SYNC_STYLE_NONE = "none";
    public static final String ROLE_SYNC_STYLE_DETECTABLE = "detectable";
    public static final String ROLE_SYNC_STYLE_ASSIGNABLE = "assignable";
    public static final String ROLE_SYNC_STYLE_DUAL = "dual";

    /**
     * Special attribute that enables a test mode where the integration
     * will be considered a manager of all Applications rather than
     * needing a concrete ManagedResource list.  
     * 
     * @ignore
     * It's an attribute so it does not show up as a tantalizing table column.
     */
    public static final String ATT_UNIVERSAL_MANAGER = "universalManager";

    /**
     * Attribute that specified which operations the executor can participate.
     * This attribute acts a lot like the Features on Application. For 
     * AccountRequest.Operation.Enable, AccountRequest.Operation.Disable,
     * and AccountRequest.Unlock.
     */
    public static final String ATT_OPERATIONS = "operations";

    /**
     * An operation string that allows setting passwords.
     */
    public static final String OP_SET_PASSWORD = "SetPassword";
    
    /**
     * Name of an attribute that when true disables the saving
     * of ProvisioningRequest objects whenever plans are sent through
     * this integration. Prior to 6.0 this was named 
     * saveProvisioningRequests and was off by default, now the behavior
     * is on by default.
     */
    public static final String ATT_NO_PROVISIONING_REQUESTS = 
        "noProvisioningRequests";
    
    /**
     * When saving pending requests is enabled, this defines the number
     * of days the request is allowed to live before it is 
     * considered expired and no longer affects plan compilation.
     * 
     * If this is zero, the expiration days are taken from the system
     * configuration entry "provisioningRequestExpirationDays".
     */
    public static final String ATT_PROVISIONING_REQUEST_EXPIRATION = 
        "provisioningRequestExpiration";

    /**
     * Attribute indicating that this integration does not handle 
     * PermissionRequests. When this is true the integration will
     * be sent AttributeRequests but PermissionRequests will be routed
     * to the unmanaged plan and displayed in work items. This is necessary
     * only for the new 5.5 read/write connectors that do not handle
     * permissions added to the cube by the unstructured data collector,
     * primarily this is AD, there can be others.
     *
     * @ignore
     * This is a kludge, eventually replace this with a more flexible
     * way to partition AttributeRequests and PermissionRequests.
     */
    public static final String ATT_NO_PERMISSIONS = "noPermissions";
    public static final String ATT_NO_GROUP_PERMISSIONS = "noGroupPermissions";

    /**
     * When set indicates that this integration is capable of receiving
     * ObjectRequests for group provisioning.
     */
    public static final String ATT_GROUP_PROVISIONING = "groupProvisioning";

    public static final String ATT_SCHEMA_PROVISIONING_MAP = "schemaProvisioningMap";

    /**
     * Attribute indicating that when this executor returns STATUS_SUCCESS  
     * from a provision() call, it really means STATUS_COMMITTED.  This is
     * to support older connectors that have not been upgraded to use
     * STATUS_COMMITTED, the option can be set in the field without changing
     * executor code.
     */
    public static final String ATT_STATUS_SUCCESS_IS_COMMITTED =
        "statusSuccessIsCommitted";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    // SailPointObject._name has the name of the target system, for example
    // "Sun IDM", Oracle OIM".  We don't currently have a displayName, 
    // though that might make sense if we needed to use _name for
    // reserved names that can't be changed.  
    //
    // There is no _type property since I think the name should be enough.
    // There is no _parent property to inherit Signatures, when we get
    // to the point of deriving configurations from templates we can
    // just clone the template since I don't think there will be
    // many of these.

    /**
     * The name of a class implementing the 
     * sailpoint.integration.IntegrationExecutor interface
     * (or for backward compatibility the 
     *  sailpoint.object.IntegrationExecutor interface).
     */ 
    String _executor;

    /**
     * An optional "execution style" that determines how the
     * executor is called. The default is "request" which means
     * a Request is launched that will retry executor calls if they
     * return retryable errors.
     *
     * The only other option currently is "synchronous" which will
     * make Provisioner call the executor directly. This would only be
     * true for simple executors that do not have retryable errors to
     * save the overhead of launching a request.
     *
     * NOTE: This option is currently unused. Provisioner will always
     * attempt one synchronous call, then start a Request if the
     * call fails with a retryable error.
     */
    // I decided to make this a String rather than a boolean _synchronous
    // flag so we can add other execution styles later if necessary.
    // Possibilities include launching a TaskDefinition or a Workflow, 
    // though those could also be done by the IntegrationExecutor.
    String _execStyle;

    /**
     * True if this is a template configuration.
     * Not used yet but in theory IdentityIQ could ship with a set
     * of example configurations for each of the supported 
     * integrations, then instantiate one by cloning a template.
     */
    boolean _template;

    /**
     * Signature describing the configuration attributes.
     * This is not currently used, but will be necessary to 
     * provide an extensible configuration UI.
     */
    Signature _signature;

    /**
     * The arbitrary configuration attributes.
     * This will normally contain one or more of the common attributes 
     * whose names are defined by the constants above. It can
     * also contain other attributes which should be defined in the
     * Signature.  
     */
    Attributes<String,Object> _attributes;

    /**
     * The optional IdentityIQ Application that represents the IDM system.
     * This is used in cases where aggregation is done from the IDM system,
     * possibly a multiplexed feed, and ther is a need to represent a Link
     * to the IDM meta-account.
     *
     * If this is set you can omit saving redundant connection
     * parameters in this object, but it is up to the executor
     * to know where to look.
     */
    Application _application;

    /**
     * List of resources that are managed by the target system.
     * This is used to filter role sync and provisioning requests
     * into requests that only involve resources on this list and also
     * defines name transformations for the resource and its attributes.
     */
    List<ManagedResource> _managedResources;

    /**
     * Role synchronization style, see header comments for
     * more information.
     */
    String _roleSyncStyle;

    /**
     * List of roles that are to be synchronized with the target system.
     */
    List<Bundle> _synchronizedRoles;

    /**
     * A filter used to select the roles for synchronization.
     */
    Filter _roleSyncFilter;

    /**
     * A container role used to select roles for synchronization.
     */
    Bundle _roleSyncContainer;
    
    /**
     * A rule that is used to load arbitrary data in a ProvisioningPlan.
     * This helps economize what data gets passed across to the integration,
     * instead of sending lots of unneeded data (for example - loading just the name
     * of the person being remediated, instead of passing the entire Identity
     * object to the integration).
     */
    Rule _planInitializer;

    //
    // Temporary support for ProvisioningConfig migration
    //

    /**
     * A script that can be used to initialize the plan.
     * This is not part of the persistent model, it is only used
     * temporarily for the conversion of the new ProvisioningConfig
     * into the old IntegrationConfig.  
     */
    Script _planInitializerScript;

    //
    // Runtime properties
    //

    Map<String,ManagedResource> _localResourceMap;
    Map<String,ManagedResource> _remoteResourceMap;

    /**
     * A copy of Application.maintenanceExpiration for configs created
     * to wrap an Application.
     */
    long _maintenanceExpiration;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public IntegrationConfig() {
    }

    /**
     * This is a special constructor to convert the new model of an
     * Application with a ProvisioningConfig into the older IntegrationConfig.
     * This is used only for temporarily compatibility with the 
     * provisioning engine. It should be removed eventually.
     */
    public IntegrationConfig(Application app) {

        // assume that app names won't overlap with
        // IntegrationConfig names
        _name = app.getName();

        // always uses this executor
        _executor = "sailpoint.integration.ConnectorExecutor";

        // initialize remaining IntegrationConfig members
        initIntegrationConfig(app);

        // jsl - note well, it would be nice to call setApplication(app)
        // here like the constructor for target collectors so we can consistently
        // maintain a reference to the wrapped Application.  Unfortunately that has meaning
        // for older provisioning integrations with a "meta" user.  See
        // IntegrationUtil.getIntegrationIdentity.  
    }

    /**
     * Starting release 6.4, IdentityIQ supports provisioning through target collector
     * for applications that support unstructured target collectors.
     *
     * This is a special constructor to convert the new model of target collector
     * into the older IntegrationConfig.
     *
     * This is used only for temporarily compatibility with the provisioning engine.
     * It should be removed eventually.
     */
    public IntegrationConfig(Application app, String targetCollector) {

        // populate IntegationConfig object name with unstructured
        // target collector name.
        _name = targetCollector;

        // always use this executor for target collector provisioning
        _executor = "sailpoint.integration.TargetCollectorExecutor";

        // initialize remaining IntegrationConfig members
        initIntegrationConfig(app);

        // Set application for IntegrationConfig
        setApplication(app);
    }

    /**
     * Initialize IntegrationConfig members
     *
     * @param app
     */
    private void initIntegrationConfig(Application app) {

        _maintenanceExpiration = app.getMaintenanceExpiration();
        
        // translate the ProvisioningConfig model 
        // the IntegrationConfig model
        boolean deleteToDisable = false;
        ProvisioningConfig pc = app.getProvisioningConfig();
        if (pc != null) {
            _planInitializer = pc.getPlanInitializer();
            _planInitializerScript = pc.getPlanInitializerScript();

            if (pc.isUniversalManager())
                setAttribute(ATT_UNIVERSAL_MANAGER, true);

            if (pc.isMetaUser())
                setApplication(app);

            // We've flipped the polarity in 6.0 to default to true
            // I'm leaving it default false in IntegrationConfig though
            // so old integrations work as before.
            if (pc.isNoProvisioningRequests()) {
                setAttribute(ATT_NO_PROVISIONING_REQUESTS, "true");
            }
            else {
                int expiration = pc.getProvisioningRequestExpiration();
                if (expiration != 0)
                    setAttribute(ATT_PROVISIONING_REQUEST_EXPIRATION,
                                 expiration);
            }

            // do a shallow copy so we can modify the list without
            // corrupting the Application
            // NOTE: We don't need the ResourceAttributes here, 
            // in the new model, ConnectorProxy will handle the name
            // mappings, not PlanEvaluator.  We do however need the outer 
            // ManagedResources list to know what this integration
            // will support.
            List<ManagedResource> mrs = pc.getManagedResources();
            if (mrs != null)
                setResources(new ArrayList<ManagedResource>(mrs));

            deleteToDisable = pc.isDeleteToDisable();

            // transfer all of the ManagedResources, 
            // copy because we may modify the list
            List<ManagedResource> resources = pc.getManagedResources();
            if (resources != null) {
                _managedResources = new ArrayList<ManagedResource>(resources);
                // kludge: we let Connector XML writers leave off the
                // app name since it will be assumed to pertain to the
                // parent app, have to fix that to have an explicit
                // name here. 
                // jsl - really?  revisit this...shouldn't be modifying
                // the ManagedResource object without copying it!!
                for (ManagedResource res : _managedResources) {
                    if (res.getName() == null && res.getApplication() == null) {
                        res.setApplication(app);
                    }
                }
            }
        }

        // Always assume this app manages itself
        ManagedResource mr = getManagedResource(app);
        if (mr == null) {
            mr = new ManagedResource();
            mr.setApplication(app);
            add(mr);
        }

        // Figure out the operations for this application.
        List<Feature> features = app.getFeatures();
        List<String> operations = new ArrayList<String>();
        if (null != features) {
            // Provisioning implies Create, Modify, and Delete.
            if (features.contains(Feature.PROVISIONING)) {
                operations.add(AccountRequest.Operation.Create.toString());
                operations.add(AccountRequest.Operation.Modify.toString());
                operations.add(AccountRequest.Operation.Delete.toString());

                // Enable implies both enable and disable.
                if (features.contains(Feature.ENABLE)) {
                    operations.add(AccountRequest.Operation.Enable.toString());
                    operations.add(AccountRequest.Operation.Disable.toString());
                }

                // No locking yet - just unlock.  Do any apps support lock?
                if (features.contains(Feature.UNLOCK)) {
                    operations.add(AccountRequest.Operation.Unlock.toString());
                }

                if (features.contains(Feature.PASSWORD)) {
                    operations.add(OP_SET_PASSWORD);
                }
            }
        }
        if (!operations.isEmpty()) {
            setAttribute(ATT_OPERATIONS, Util.listToCsv(operations));
        }

        // If deleteToDisable is configured, add an operation transformation to
        // all managed resources.
        if (deleteToDisable) {
            for (ManagedResource resource : _managedResources) {
                resource.addOperationTransformation(Operation.Delete, Operation.Disable);
            }
        }

        // bring over the group provisioning flag
        if (app.isSupportsGroupProvisioning()) {
            setAttribute(ATT_GROUP_PROVISIONING, true);

            // build a map for the configured schemas
            Map<String, Object> schemaMap = new HashMap<String, Object>();
            for (Schema schema : Util.iterate(app.getGroupSchemas())) {
                schemaMap.put(schema.getObjectType(), schema.supportsFeature(Feature.PROVISIONING));
            }

            setAttribute(ATT_SCHEMA_PROVISIONING_MAP, schemaMap);
        }

        // Kludges to prevent passing PermissionRequests to the
        // connecotr and leaving them in the unmanaged  plan for work items.
        // Since we can't tell the difference between target and direct
        // permissions in the Permission model, and many related
        // api classes we have to assume that if the connector can
        // provision either type then all pass.  This may be a problem
        // for some connectors.
        boolean noPermissions = true;
        // no one actually used this flag, should remove it
        if (pc == null || !pc.isNoPermissions()) {

            // we can't be here if we didn't have Feature.PROVISIONING, right?
            if (app.supportsFeature(Feature.PROVISIONING) &&

                (app.supportsFeature(Feature.DIRECT_PERMISSIONS) &&
                 !app.supportsFeature(Feature.NO_PERMISSIONS_PROVISIONING)) ||

                 (app.supportsFeature(Feature.UNSTRUCTURED_TARGETS) &&
                  !app.supportsFeature(Feature.NO_PERMISSIONS_PROVISIONING))) {

                // app can provision at least one type of permission
                noPermissions = false;
            }
            if (app.isSupportsGroupProvisioning()) {


                HashMap<String, Object> schemaMap = new HashMap<String, Object>();
                for(Schema sch : app.getGroupSchemas()) {
                    if (sch.supportsFeature(Feature.PROVISIONING) &&
                            (app.supportsFeature(Feature.UNSTRUCTURED_TARGETS) || app.supportsFeature(Feature.DIRECT_PERMISSIONS))) {
                        schemaMap.put(sch.getObjectType(), sch.supportsFeature(Feature.NO_PERMISSIONS_PROVISIONING));
                    } else {
                        //We don't support provisioning, therefore we don't support permission provisioning
                        schemaMap.put(sch.getObjectType(), true);
                    }
                }
                setAttribute(ATT_NO_GROUP_PERMISSIONS, schemaMap);
            }
        }

        if (noPermissions) {
            setAttribute(ATT_NO_PERMISSIONS, "true");
        }
    }

    /**
     * @exclude
     */
    public void load() {
        
        if (_application != null)
            _application.load();

        if (_planInitializer != null)
            _planInitializer.load();
        
        if (_synchronizedRoles != null) {
            for (Bundle b : _synchronizedRoles)
                b.load();
        }

        if (_roleSyncContainer != null)
            _roleSyncContainer.load();

        if (_managedResources != null) {
            for (ManagedResource res : _managedResources)
                res.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitIntegrationConfig(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of a class implementing the 
     * sailpoint.integration.IntegrationExecutor interface.
     */ 
    @XMLProperty
    public String getExecutor() {
        return _executor;
    }

    public void setExecutor(String s) {
        _executor = s;
    }

    /**
     * An optional "execution style" that determines how the
     * executor is called. The default is "request" which means
     * a Request is launched that will retry executor calls if they
     * return retryable errors.
     *
     * The other option is "synchronous" which will
     * make Provisioner call the executor directly. This would only be
     * true for simple executors that do not have retryable errors to
     * save the overhead of launching a request.
     *
     * NOTE: This option is currently unused. Provisioner will always
     * attempt one synchronous call, then start a Request if the
     * call fails with a retryable error.
     */
    @XMLProperty
    public String getExecStyle() {
        return _execStyle;
    }

    public void setExecStyle(String s) {
        _execStyle = s;
    }

    public boolean isExecStyleRequest() {
        return (_execStyle == null || EXEC_STYLE_REQUEST.equals(_execStyle));
    }

    public boolean isExecStyleSynchronous() {
        return EXEC_STYLE_SYNCHRONOUS.equals(_execStyle);
    }

    /**
     * Signature describing the configuration attributes.
     * This is not currently used, but will be necessary to 
     * provide an extensible configuration UI.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    /**
     * True if this is a template configuration.
     * Not used yet but in theory IdentityIQ could ship with a set
     * of example configurations for each of the supported 
     * integrations, then instantiate one by cloning a template.
     */
    @XMLProperty
    public boolean isTemplate() {
        return _template;
    }

    public void setTemplate(boolean b) {
        _template = b;
    }

    /**
     * The arbitrary configuration attributes.
     * This will normally contain one or more of the common attributes 
     * whose names are defined by the constants above. It can
     * also contain other attributes which should be defined in the
     * Signature.  
     */
    @XMLProperty(mode=SerializationMode.INLINE)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    /**
     * A rule that is used to load arbitrary data in a ProvisioningPlan.
     * This helps economize what data gets passed across to the integration,
     * instead of sending lots of unneeded data (for example - loading just the name
     * of the person being remediated, instead of passing the entire Identity
     * object to the integration).
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
	public Rule getPlanInitializer() {
		return _planInitializer;
	}

	public void setPlanInitializer(Rule initializer) {
		_planInitializer = initializer;
	}

	public Script getPlanInitializerScript() {
		return _planInitializerScript;
	}

	public void setPlanInitializerScript(Script script) {
		_planInitializerScript = script;
	}

    /**
     * Return a DynamicValue that can be used to evaluate
     * the plan initializer. This is what is now used.
     */
    public DynamicValue getPlanInitializerLogic() {
        DynamicValue result = null;
        if (_planInitializer != null || _planInitializerScript != null)
            result = new DynamicValue(_planInitializer, _planInitializerScript, null);
        return result;
    }

    /**
     * The optional IdentityIQ Application that represents the IDM system.
     * This is used in cases where aggregation is done from the IDM system,
     * possibly a multiplexed feed, and need to represent a Link
     * to the IDM meta-account.
     *
     * If this is set you may omit saving redundant connection
     * parameters in this object, but it is up to the executor
     * to know where to look.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application app) {
        _application = app;
    }

    /**
     * List of resources that are managed by the target system.
     * This is used to filter role sync and provisioning requests
     * into requests that only involve resources on this list and also
     * defines name transformations for the resource and its attributes.
     *
     * @ignore
     * 3.0 had a "ManagedResources" property that we're not going to 
     * upgrade but pick a different name to ease the Hibernate transition
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="ManagedResources")
    public List<ManagedResource> getResources() {
        return _managedResources;
    }

    public void setResources(List<ManagedResource> resources) {
        _managedResources = resources;
    }
    
    /**
     * Role synchronization style, see static comments for
     * more information.
     */
    @XMLProperty
    public String getRoleSyncStyle() {
        return _roleSyncStyle;
    }

    public void setRoleSyncStyle(String style) {
        _roleSyncStyle = style;
    }

    /**
     * Static list of roles that are to be synchronized with the target system.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Bundle> getSynchronizedRoles() {
        return _synchronizedRoles;
    }

    public void setSynchronizedRoles(List<Bundle> roles) {
        _synchronizedRoles = roles;
    }

    public void removeSynchronizedRole(Bundle role) {
        if (_synchronizedRoles != null && role != null)
            _synchronizedRoles.remove(role);
    }

    /**
     * A filter used to select the roles for synchronization.
     */
    @XMLProperty
    public Filter getRoleSyncFilter() {
        return _roleSyncFilter;
    }

    public void setRoleSyncFilter(Filter f) {
        _roleSyncFilter = f;
    }

    /**
     * A container role used to select roles for synchronization.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Bundle getRoleSyncContainer() {
        return _roleSyncContainer;
    }

    public void setRoleSyncContainer(Bundle b) {
        _roleSyncContainer = b;
    }

    /**
     * When set, indiciates that the integration is in a maintenance period.
     * If positive, it is a Date that has the expiration of the period.
     * If -1, it is a non expiring period that must be manually disabled.
     */
    @XMLProperty
    public long getMaintenanceExpiration() {
        return _maintenanceExpiration;
    }

    public void setMaintenanceExpiration(long i) {
        _maintenanceExpiration = i;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Attribute Digging
    // The usual set of type coercers.  One of the few places where
    // multiple inheritance would be useful.
    //
    //////////////////////////////////////////////////////////////////////

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        else {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, value);
        }
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public List<String> getStringList(String name) {
        return (_attributes != null) ? _attributes.getStringList(name) : null;
    }

    public List<RoleSyncHistory> getRoleSyncHistory() {
        return (List<RoleSyncHistory>)getAttribute(ATT_ROLE_SYNC_HISTORY);
    }

    public void setRoleSyncHistory(List<RoleSyncHistory> history) {
        setAttribute(ATT_ROLE_SYNC_HISTORY, history);
    }

    public boolean isUniversalManager() {
        return getBoolean(ATT_UNIVERSAL_MANAGER);
    }

    public boolean isRoleSyncStyle(String style) {
        return (style != null && style.equals(_roleSyncStyle));
    }

    public boolean isRoleSyncStyleDetectable() {
        return isRoleSyncStyle(ROLE_SYNC_STYLE_DETECTABLE);
    }

    public boolean isRoleSyncStyleAssignable() {
        return isRoleSyncStyle(ROLE_SYNC_STYLE_ASSIGNABLE);
    }

    public boolean isRoleSyncStyleDual() {
        return isRoleSyncStyle(ROLE_SYNC_STYLE_DUAL);
    }

    /**
     * @deprecated See {@link #isGroupProvisioning(String)}
     */
    @Deprecated
    public boolean isGroupProvisioning() {
        return getBoolean(ATT_GROUP_PROVISIONING);
    }

    /**
     * Determines if the schema for the specified object type
     * supports provisioning.
     * @param objectType The object type.
     * @return True if the schema supports provisioning, false otherwise.
     */
    public boolean isGroupProvisioning(String objectType) {
        boolean supported = false;

        if (!Util.isNullOrEmpty(objectType)) {
            Map<String, Object> schemaMap = (Map<String, Object>) getAttribute(ATT_SCHEMA_PROVISIONING_MAP);
            if (schemaMap != null) {
                supported = Util.otob(schemaMap.get(objectType));
            }
        }

        return supported;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Supported Operations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Check the IntegrationConfig to see if it supports the operation.
     * For backward compatibility and brevity listing
     * Create,Modify,Delete is not required, it can be assumed those will work on all 
     * Integrations.
     */
    public boolean supportedOperation(String op) {
        
        if ( op == null ) {
            return true;
        }
        // Assume that Enable implies support for Disable
        String requiredOp = null;               
        if (AccountRequest.Operation.Enable.toString().equals(op) || 
            AccountRequest.Operation.Disable.toString().equals(op) ) {
            requiredOp = AccountRequest.Operation.Enable.toString();
        } else { 
            requiredOp = op;
        }
        boolean supported = false;
        if ( requiredOp != null ) {
            // make the integrations opt in 
            List<String> ops = null;
            String csv = getString(IntegrationConfig.ATT_OPERATIONS);
            if ( csv == null ) {
                //
                // Null operations list implies Create, Modify and Delete
                //
                ops = Arrays.asList(AccountRequest.Operation.Create.toString(),
                                    AccountRequest.Operation.Modify.toString(),
                                    AccountRequest.Operation.Delete.toString());
            } else {
                ops = Util.csvToList(csv);
            }
            if ( Util.size(ops) > 0 ) { 
                for ( String supportedOp : ops ) {
                    if ( supportedOp.equals(requiredOp) ) {
                        supported = true;
                        break;
                    }
                }
            }
        }
        return supported;
    }
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Runtime Mapping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Bootstrap a resource map for master searches.
     */
    private Map<String,ManagedResource> getResourceMap() {
        if (_localResourceMap == null) {
            _localResourceMap = new HashMap<String,ManagedResource>();  
            if (_managedResources != null) {
                for (ManagedResource res : _managedResources) {
                    String local = res.getLocalName();
                    // if there is no local name, assume it is the same
                    // on both sides
                    if (local == null)
                        local = res.getName();
                    if (local != null)
                        _localResourceMap.put(local, res);
                }
            }
        }
        return _localResourceMap;
    }

    private Map<String,ManagedResource> getRemoteResourceMap() {
        if (_remoteResourceMap == null) {
            _remoteResourceMap = new HashMap<String,ManagedResource>();  
            if (_managedResources != null) {
                for (ManagedResource res : _managedResources) {
                    String name = res.getName();
                    // if name is null assume they're the same
                    // on both sides
                    if (name == null)
                        name = res.getLocalName();
                    if (name != null) {
                        _remoteResourceMap.put(name, res);
                    }
                }
            }
        }
        return _remoteResourceMap;
    }

    /**
     * Get the managed resource definition for a local resource (Application).
     */
    public ManagedResource getManagedResource(String name) {
        Map<String,ManagedResource> map = getResourceMap();
        return map.get(name);
    }

    public ManagedResource getManagedResource(Application app) {
        return (app != null) ? getManagedResource(app.getName()) : null;
    }

    /**
     * Get the managed resource definition using a remote resource name.
     */
    public ManagedResource getRemoteManagedResource(String name) {
        Map<String,ManagedResource> map = getRemoteResourceMap();
        return map.get(name);
    }

    /**
     * Add a new managed resource.
     */
    public void add(ManagedResource res) {
        if (res != null) {
            if (_managedResources == null)
                _managedResources = new ArrayList<ManagedResource>();
            _managedResources.add(res);
            _localResourceMap = null;
            _remoteResourceMap = null;
        }
    }

    public void removeManagedResource(Application app) {
        if (app != null) {
            ManagedResource res = getManagedResource(app);
            if (res != null)
            _managedResources.remove(res);
        }
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // RoleSyncHistory
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Used to maintain history about the roles sent to the IDM system.
     */
    @XMLClass
    public static class RoleSyncHistory {

        String _name;
        Date _modified;

        // transient flag used to maintain refresh status
        boolean _touched;

        public RoleSyncHistory() {
        }

        public RoleSyncHistory(Bundle src) {
            if (src != null) {
                _name = src.getName();
                _modified = src.getModified();
                if (_modified == null)
                    _modified = src.getCreated();
            }
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }
 
        @XMLProperty
        public void setModified(Date d) {
            _modified = d;
        }

        public Date getModified() {
            return _modified;
        }

        public void setTouched(boolean b) {
            _touched = b;
        }

        public boolean isTouched() {
            return _touched;
        }

    }
}
