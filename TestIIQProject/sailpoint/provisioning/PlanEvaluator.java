/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Private utility class uesd by Provisioner to evaluate compiled plans.
 *
 * Author: Jeff
 * 
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.DynamicValuator;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.integration.IntegrationExecutor;
import sailpoint.integration.IntegrationManager;
import sailpoint.integration.RequestResult;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicValue;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ManagedResource;
import sailpoint.object.ManagedResource.ResourceAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.object.TaskResult;
import sailpoint.object.Template;
import sailpoint.object.Template.Usage;
import sailpoint.request.IntegrationRequestExecutor;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.ProvisioningTransactionService.TransactionDetails;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class PlanEvaluator {

    public class BlockedAttributeEvaluator {
        Application _application;
        /**
         * A list of account attribute names which need to be blocked from being sent to
         * the connector. This is lazily loaded.
         */
        transient LazyLoad<List<String>> _blockedAccountAttributeNames;
        
        /**
         * Per schemaObjectType, we have a list of group attribute names which need to be blocked from being sent to
         * the connector. This is lazily loaded.
         */
        transient LazyLoad<Map<String, List<String>>> _blockedGroupAttributeNamesMap;
        
        public BlockedAttributeEvaluator() {}

        public BlockedAttributeEvaluator(Application app) {
            _application = app;
            _blockedAccountAttributeNames = createBlockedAttributeLoader();
            _blockedGroupAttributeNamesMap = createBlockedGroupAttributeLoader();
        }

        /**
         * 
         * This method will return a list attribute names which need to be blocked
         * from the account request which is sent to the connector.
         * 
         * See {@link #createBlockedAttributeLoader()}
         */
        public List<String> getBlockedAccountAttributeNames() throws GeneralException {
            return _blockedAccountAttributeNames.getValue();
        }

        /**
         * 
         * This method will return a list attribute names which need to be blocked
         * from the group request which is sent to the connector.
         * 
         * See {@link #createBlockedGroupAttributeLoader()}
         * @deprecated {@link #getBlockedSchemaObjectTypeAttributeNames(String)}
         */
        public List<String> getBlockedGroupAttributeNames() throws GeneralException {
            return getBlockedSchemaObjectTypeAttributeNames(Connector.TYPE_GROUP);
        }

        /**
         *
         * This method will return a list attribute names which need to be blocked
         * from the request which is sent to the connector.
         *
         * See {@link #createBlockedGroupAttributeLoader()}
         */
        public List<String> getBlockedSchemaObjectTypeAttributeNames(String schemaObjectType) throws GeneralException {
            if (_blockedGroupAttributeNamesMap.getValue().containsKey(schemaObjectType)) {
                return _blockedGroupAttributeNamesMap.getValue().get(schemaObjectType);
            } else {
                return Collections.emptyList();
            }
        }

        /**
         * Loops over the template and adds 'displayOnly' fields to the 
         * values list.
         * 
         * @param values a list of displayOnly field names
         * @param template 'Create' 'Update' etc. 
         */
        private void addDisplayOnlyValuesFromTemplate(List<String> values, Template template) {

            if (template != null) {
                //List<Field> fields = template.getFields();
                TemplateFieldIterator fieldIterator = new TemplateFieldIterator(template, _context);
                if (fieldIterator != null) {
                    for (;fieldIterator.hasNext();) {
                        Field field = fieldIterator.next();
                        if (field.isDisplayOnly()) {
                           values.add(field.getName()); 
                        }
                    }
                }
            }
        }
        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private LazyLoad<List<String>> createBlockedAttributeLoader() {
            return new LazyLoad(new ILazyLoader<List<String>>() {

                public List<String> load() throws GeneralException {
                    List<String> values = new ArrayList<String>();
                    
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Create, Connector.TYPE_ACCOUNT));
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Update, Connector.TYPE_ACCOUNT));
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Enable, Connector.TYPE_ACCOUNT));
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Disable, Connector.TYPE_ACCOUNT));
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Unlock, Connector.TYPE_ACCOUNT));
                    addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.ChangePassword, Connector.TYPE_ACCOUNT));
                    
                    return values;
                }
            });
        }

        /**
         * 
         * This method will return a list of blocked field names from 
         * the 'Create' and 'Update' Account provisioning forms.
         * A field is considered blocked if its 'displayOnly' property is 
         * set to true. 
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private LazyLoad<Map<String, List<String>>> createBlockedGroupAttributeLoader() {
            return new LazyLoad(new ILazyLoader<Map<String, List<String>>>() {
                public Map<String, List<String>> load() throws GeneralException {
                    Map<String, List<String>> map = new HashMap<String, List<String>>();
                    if (_application.getSchemas() != null) {
                        for (Schema schema : _application.getSchemas()) {
                            if (!Connector.TYPE_ACCOUNT.equals(schema.getObjectType())) {
                                List<String> values = new ArrayList<String>();
                                map.put(schema.getObjectType(), values);

                                addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Create, schema.getObjectType()));
                                addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Update, schema.getObjectType()));
                            }
                        }
                    }

                    // Special case.
                    // that is, if no group schema was specified.
                    // we still need to handle CreateGroup and UpdateGroup
                    if (map.keySet().size() == 0) {
                        List<String> values = new ArrayList<String>();
                        map.put(Connector.TYPE_GROUP, values);

                        addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Create, Connector.TYPE_GROUP));
                        addDisplayOnlyValuesFromTemplate(values, _application.getOldTemplate(Usage.Update, Connector.TYPE_GROUP));
                    }

                    return map;
                }
            });
        }
        
        /**
         * @see Application#getBlockedAccountAttributeNames() for more details.
         */
         public boolean isAccountAttributeBlocked(AccountRequest accountRequest, AttributeRequest attributeRequest) throws GeneralException {
             
             _application = accountRequest.getApplication(_context);
             if (_application == null) {
                 return false;
             }
             _blockedAccountAttributeNames = createBlockedAttributeLoader();
             return getBlockedAccountAttributeNames().contains(attributeRequest.getName());
         }

         /**
          * @see Application#getBlockedGroupAttributeNames() for more details.
          */
         public boolean isGroupAttributeBlocked(ObjectRequest objectRequest, AttributeRequest attributeRequest) throws GeneralException {
             
             if(_application == null) {
                 _application = objectRequest.getApplication(_context);
                 if (_application == null) {
                     return false;
                 }
             }
             _blockedGroupAttributeNamesMap = createBlockedGroupAttributeLoader();
             return getBlockedGroupAttributeNames().contains(attributeRequest.getName());
         }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    // 
    //////////////////////////////////////////////////////////////////////

    //
    // NOTE: If you add or remove arguments, you must also 
    // adjust the ARG_NAMES array.
    //

    /**
     * Argument containing the name of the actor that is consisdered
     * to have submitting the project.  This is the default
     * name used for the "assigner" property of RoleAssignment
     * metadata.  It may be overridden by also setting the ARG_ASSIGNER.
     */
    public static final String ARG_REQUESTER = ProvisioningPlan.ARG_REQUESTER;

    /**
     * Name to use for the "assigner" property of any RoleAssignment
     * metadata we may need to create.  If not set defaults
     * to ARG_REQUESTER.
     *
     * This will often be the same as ARG_REQUESTER but for self-service
     * requests the requster may be the end user and the assigner is the
     * identity that approved the request.
     */
    public static final String ARG_ASSIGNER = "assigner";

    /**
     * Name to use for the "source" property of any RoleAssignment
     * metadata we may need to create.
     */
    public static final String ARG_SOURCE = ProvisioningPlan.ARG_SOURCE;

    /**
     * When true, we will apply the changes defined in the
     * compiled plans directly to the Identity during execution
     * whether or not there was an integration to handle them,
     * and without waiting for the integrations to complete.
     *
     * The alternative is to wait for the integrations to 
     * complete which may take unbounded time since we can
     * be dealing with request retries and workflows, then
     * reaggregating to pick up the changes.
     *
     * Reaggregation is more accurate so this option is normally off.
     * It is used in a few special cases where we want to analyze
     * what a plan would do without actually doing any provisioning
     * requests.  I can imagine it also being useful in POCs
     * where we do have integrations we want to use, but we don't
     * want to wait for them to finish before displaying the results.
     */
    public static final String ARG_OPTIMISTIC_PROVISIONING = "optimisticProvisioning";
    
    /**
     * Old name for optimisticProvisioning.
     * This should no longer be seen in new configurations.
     */
    public static final String ARG_IMMEDIATE_UPDATE = "immediateUpdate";

    /**
     * When true indiciates that we should apply the contents of the
     * plan directly to the Identity and Links without sending provisioning
     * requests to the connectors or integrations.
     *
     * This is used when we have provisioning plans built to make changes
     * to to the cube to reflect changes we detected in the target systems.
     * Since we're responding to changes that have already been made we don't
     * want to send redundant requests to the target systems.
     *
     * This is sort of a combination of OPTIMISTIC_PROVISIONING and SIMULATE,
     *
     * OPTIMISTIC_PROVISIONING sends provisioning requests, updates the links,
     * and commits the changes.
     * 
     * SIMULATE does not send provisioning requests, updates the links,
     * and does not commit the changes.
     *
     * LOCAL_UPDATE does not send provisioning requests, updates the links,
     * and commits the changes.
     *
     * This is often used in combination with ARG_DO_REFRESH to refresh
     * the cube after the update.
     */
    public static final String ARG_LOCAL_UPDATE = "localUpdate";
    
    /**
     * When true indiciates that we should not apply the contents of the
     * plan directly to the Identity and Links, and do not send provisioning
     * requests to the connectors or integrations.
     *
     * ARG_NO_LINK_UPDATE does not send provisioning requests, or update the links,
     * or commit the changes.
     *
     * This is often used in combination with ARG_DO_REFRESH to refresh
     * the cube after the update.
     */
    public static final String ARG_NO_LINK_UPDATE = "noLinkUpdate";

    /**
     * Flag to indicate when we are provisioning native changes.
     * This will cause a side-effect of calling NativeChangeDetection 
     * so these changes will trigger life cycle events if they
     * are defined.
     * 
     * Most of the time these will also be LOCAL_UPDATE calls
     * only since we are getting this from data directly
     * the source application.
     */
    public static final String ARG_NATIVE_CHANGE = "nativeChange";

    /**
     * When true indicates that we're "simulating" provisioning
     * and should not have any persistent side effects on the target
     * identity or the audit logs.
     *
     * This is often on when ARG_OPTIMISTIC_PROVISIONING is on for 
     * impact analysis. Could have this imply the other?
     */
    public static final String ARG_SIMULATE = "simulateProvisioning";

    /**
     * When true it means that the Identity object will have been
     * locked at a higher level and we do not have to lock
     * it again in IIQEvaluator.
     */
    public static final String ARG_NO_LOCKING = "noLocking"; 

    /**
     * When true, we will make additions and removals to the
     * detected role list immediately rather than waiting
     * for the next entitlement correlation after the provisioning
     * has finished.  
     *
     * Added this thinking McDonalds might want it, but it hasn't
     * been used yet.
     * 
     * !! this has been set by IdentityLibrary.checkPolicyViolations
     * for some time.  I still don't understand why but it may be
     * an ARM holdover.  Also this is a very ambiguous name.
     * Need to revisit this...
     */
    public static final String ARG_MODIFY_DETECTED_ROLES = "modifyDetectedRoles";
    
    /**
     * When true the plan evaluator will be configured to not create
     * retry requests. This is typically toggled during the LCM 
     * provisioning sub processes since they do their own 
     * retry processing.
     */    
    public static final String ARG_NO_RETRY_REQUEST = "disableRetryRequest";

    /**
     * When true, a plan that was ignored due to the application being
     * in the maintenance window will be treated as a hard failiure.
     * This is normally off for LCM requests, but may be set for refresh
     * tasks to avoid creating large number of retry requests during
     * maintenance windows.  The alternative is to simply run the refresh
     * again at a later time.
     */
    public static final String ARG_NO_MAINTENANCE_WINDOW_RETRY = "noMaintenanceWindowRetry";

    /**
     * When true the plan evaluator will refresh the Links and Identity
     * if the plan resulted in changes to any Links or the identity.
     * This is typically used when connectors can return STATUS_COMMITTED
     * to confirm that a change was made, or when using ARG_LINK_EDIT
     * to directly edit Links without sending provisioning requests.
     * 
     * In both cases we commonly want to do some of the same things
     * we do in the aggregation and identity refresh tasks, specifically
     * promotion of link attributes, promotion of identity attributes, 
     * and role detection (which includes extra entitlement analysis).
     *
     * This will be ignored unless the plan changed some aspect of the 
     * identity that could affect the refresh.  In other words you can't
     * use this with an empty plan to force a refresh.  There are other
     * ways to do that.
     */
    public static final String ARG_DO_REFRESH = "doRefresh";

    /**
     * When doRefresh is true, this may contain a Map of refresh options.
     * The keys in the map are the same as those recognized by the
     * identity refresh task (the Identitizer).   If this is not
     * specified and doRefresh is enalbed, we will by default
     * assume these options:
     *
     *     promoteLinkAttributes
     *     promoteAttributes
     *     correlateEntitlements
     *     
     * Maybe:
     *
     *     noManagerCorrelation
     *     promoteManagedAttributes
     *     correlateScope
     *     processTriggers
     *
     * The following options are forced off to prevent recursion:
     *
     *     provision
     *     provisionIfChanged
     *         
     */
    public static final String ARG_REFRESH_OPTIONS = "refreshOptions"; 
    
    /**
     * Flag that disables IdentityEntitlement promotion when provisioning will
     * update an Identity's entitlements (IdentityEntitlement).
     */    
    public static final String ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION = "disableIdentityEntitlementPromotion";
    
    /**
     * A flag to allows callers to avoid the _project.resetEvalution in the evaluate() 
     * method call which is typically  used to clear all of the results from a project. 
     * 
     * There are cases during dependency 
     * fulfillment that we'll need to revisit evaluate() if there were retrys invoked
     * during dependency execution.
     */
    public static final String ARG_DISABLE_EVAL_RESET = "disableProjectReset";
    
    /**
     * When true, indicates that the requester is a password administrator. 
     * During evaluation, the password last changed date will be reset so the 
     * user whom the administrator is resetting the password for will not be 
     * affected by potential 'minimum password change duration' system configurations.
     */
    public static final String ARG_REQUESTER_IS_PASSWORD_ADMIN = "requesterIsPasswordAdmin";
    
    /**
     * When true, indicates the requester has the system administrator 
     * capability and is allowed to change other system administrator passwords.
     */
    public static final String ARG_REQUESTER_IS_SYSTEM_ADMIN = "requesterIsSystemAdmin";
    
    /**
     * Argument to indicate that we don't need an Identity for provisioning request.
     * This argument is used in workflow 'Restore Deleted Objects'. This workflow is used for
     * Active Directory Recycle Bin feature, in which we restore deleted objects. As there is no
     * Identity exists for deleted object,we must execute restore request without an Identity.
     * If this argument is passed then we don't create an dummy Identity for just provisioning request.
     */
    
    public static final String ARG_IDENTITY_NOT_REQUIRED = "identityNotRequired";

    /**
     * When true, indicates that when provisioning the sunrise date of a request will be ignored.
     */
    public static final String ARG_IGNORE_SUNRISE_DATE = "ignoreStartDate";

    /**
     * When true, indicates we want to create TriggerSnapshot items for an Identity during this provisioning request.
     * By default, snapshots are done during the IIQEvaluator.prepareIdentity() method call. When the ARG_NO_LINK_UPDATE
     * attribute is set to true, the prepareIdentity() method is skipped and snapshots are not created.
     * When an Identity update is performed via a SCIM request, the ARG_NO_LINK_UPDATE attribute is set during
     * the provisioning request.  This attribute should be set during that request so that snapshots are created
     * prior to the updates.  This will allow any Event Triggers to get access to the previous Identity state and
     * can properly recognize changes that were made.
     */
    public static final String ARG_TRIGGER_SNAPSHOTS = "triggerSnapshots";

    /**
     * Arguments we pull in from the task args.
     */
    public static final String[] ARG_NAMES = {
        ARG_REQUESTER,
        ARG_SOURCE,
        ARG_IMMEDIATE_UPDATE,
        ARG_OPTIMISTIC_PROVISIONING,
        ARG_LOCAL_UPDATE,
        ARG_NO_LINK_UPDATE,
        ARG_SIMULATE,
        ARG_NO_LOCKING,
        ARG_NO_RETRY_REQUEST,
        ARG_NO_MAINTENANCE_WINDOW_RETRY,
        ARG_MODIFY_DETECTED_ROLES,
        ARG_DO_REFRESH,
        ARG_REFRESH_OPTIONS,
        ARG_NATIVE_CHANGE,
        ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION,
        ARG_REQUESTER_IS_PASSWORD_ADMIN,
        ARG_REQUESTER_IS_SYSTEM_ADMIN,
        ARG_IDENTITY_NOT_REQUIRED,
        ARG_IGNORE_SUNRISE_DATE
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of a RequestDefinition for handling retryable
     * calls to the IntegrationExecutor.  This is not configurable.
     */
    public static final String REQUEST_DEFINITION = "Integration Request";

    //
    // Task results
    //

    public static final String RET_REQUESTS = "provisioningRequests";
    public static final String RET_ERRORS = "provisioningErrors";

    /**
     * Application configuration attribute to define additional metadata
     * required by target connector to process provisioning plan. 
     */
    public static final String PROVISIONING_METADATA = "provisioningMetaData";
    
    public static final String ENTITLEMENT_ATTRIBUTES = "entitlementAttributes";
    
    /**
     * All Link related attributes to be requested under this
     * key in {@link PlanEvaluator#PROVISIONING_METADATA}.
     * 
     */
    public static final String LINK_ATTRIBUTES = "linkAttributes";
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PlanEvaluator.class);

    /**
     * Who doesn't love context?
     */
    SailPointContext _context;

    /**
     * Identity being provisioned.  
     */
    Identity _identity;

    /**
     * Previously compiled project containing plans and options.
     */
    ProvisioningProject _project;
    
    /**
     * Evaluator for the IIQ plan.
     */
    IIQEvaluator _iiqEvaluator;

    /**
     * Utilities for searching and caching IntegrationConfigs.
     */
    IntegrationConfigFinder _integrationFinder;

    //
    // Runtime statistics
    //

    /**
     * Various runtime statistics, used with tasks.
     * Might be nice to break these down by IntegrationConfigs...
     */
    int _provisioningRequests;
    int _provisioningErrors;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Accessors
    // 
    //////////////////////////////////////////////////////////////////////

    public PlanEvaluator(SailPointContext con)  {
        _context = con;
        _iiqEvaluator = new IIQEvaluator(_context);
        _integrationFinder = new IntegrationConfigFinder(_context);
    }

    /**
     * When used from a task, copy statistics to the result.
     */
    public void saveResults(TaskResult result) {

        result.setInt(RET_REQUESTS, _provisioningRequests);
        result.setInt(RET_ERRORS, _provisioningErrors);

        if (_iiqEvaluator != null)
            _iiqEvaluator.saveResults(result);
    }

    /**
     * Return the identity we ended up committing, may be different
     * than the original if we locked.
     */
    public Identity getIdentity() {
        return _identity;
    }

    /**
     * Return the requester from the argument map if present, 
     * or default to the context owner.
     */
    public String getRequester() {

        String requester = _project.getRequester();
        if (requester == null)
            requester = _context.getUserName();
        
        return requester;
    }    
    
    /**
     * Log the project with a header.
     * This is intended to trace the project at the end of each
     * of the major phases and uses info level logging.
     */
    private void logProject(String header) {
        if (log.isInfoEnabled()) {
            log.info(header);
            try {
                // log a scrubbed clone of the _project
                ProvisioningProject clone = (ProvisioningProject)_project.deepCopy(_context);
                if (clone != null) {
                    ObjectUtil.scrubPasswords(clone);
                    log.info(clone.toXml());
                }
            }
            catch (Throwable t) {
                log.error(t);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Public Interface
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Kludge for IntegrationRequestExecutor.
     * PlanEvaluator is used at the very last minute to handle the plan
     * for one integration.  It is already fully compiled but we need to 
     * pass options in since we're not starting from a project.
     */
    public void setProject(ProvisioningProject proj) {
        _project = proj;
    }

    /**
     * Execute the managed plans after compilation.
     * The identity must be specified in the project.
     *
     * !! Need to think about how to return error and other status.
     */
    public void execute(ProvisioningProject project) 
        throws GeneralException {

        execute(null, project);
    }

    /**
     * Execute the managed plans after compilation on a given identity.
     * A slight optimization to avoid fetching the Identity if we
     * already had it.
     */
    public void execute(Identity ident, ProvisioningProject project) 
        throws GeneralException {

        final String phase1 = "PlanEvaluator.execute phase 1";
        Meter.enterByName(phase1);

        _project = project;
        _provisioningRequests = 0;
        _provisioningErrors = 0;

        fixArguments();

        // Simulating means we'll update the Identity and Links but won't 
        // commit any changes.
        boolean simulating = _project.getBoolean(PlanEvaluator.ARG_SIMULATE);

        // Refreshing means we're applying a plan sent to us without
        // provisioning and we commit changes.
        boolean localUpdate = _project.getBoolean(PlanEvaluator.ARG_LOCAL_UPDATE);

        // noLinkUpdate means we do nothing.
        boolean noLinkUpdate = _project.getBoolean(PlanEvaluator.ARG_NO_LINK_UPDATE);

        // Do not lock the identity yet, calling the connectors can be time
        // consuming and the transaction may be committed several times for
        // logging.

        _identity = ident;
        if (_identity == null) {
            // this is no longer required for group provisoning
            String idname = project.getIdentity();
            if (idname == null) {
                ProvisioningPlan plan = _project.getMasterPlan();
                if (plan != null && plan.isIdentityPlan())
                    throw new GeneralException("Missing identity name");
            }
            if (idname != null) {
                _identity = _context.getObjectByName(Identity.class, idname);
                if (_identity == null) {
                    // this indicates we are in a create
                    _identity = new Identity();
                    _identity.setName(idname);                    
                }
            }
        }

        // Reset stale results, shouldn't have any if the master plan
        // is being recompiled.
        if ( !Util.getBoolean(_project.getAttributes(), ARG_DISABLE_EVAL_RESET))
            _project.resetEvaluation();

        logProject("Beginning execution of project");
        if (noLinkUpdate) {
            // do nothing here.
        } else if (simulating || localUpdate) {
            // make all plans look like they succeeded, even if
            // we would not have sent them to an integration
            List<ProvisioningPlan> plans = _project.getPlans();
            if (plans != null) {
                for (ProvisioningPlan plan: plans) {
                    ProvisioningResult res = new ProvisioningResult();
                    res.setStatus(ProvisioningResult.STATUS_COMMITTED);
                    plan.setResult(res);
                }
            }
        } else  {
            DependencyEvaluator dependencyEvaluator = new DependencyEvaluator(_context, _project, _identity);

            List<ProvisioningPlan> plans = null;            
            List<ProvisioningPlan> intPlans = _project.getIntegrationPlans();
            if ( intPlans != null ) 
               plans = new ArrayList<ProvisioningPlan>(intPlans);
            
            if ( plans != null ) {
                
                // To handle dependencies loop through all of the managed plans
                // and fulfill any pending plans. We can get here through 
                // normal processing Or if a retry of a dependency has been 
                // executed.
                            
                int MAX = 20;
                int iterations = 0;
                while ( dependencyEvaluator.hasReadyPlans(_project) ) {
                    
                    ListIterator<ProvisioningPlan> it = (Util.size(plans) > 0 ) ? plans.listIterator() : null;
                    if ( it == null ) {
                        break;
                    }                                        
                    while ( it.hasNext() ) {                         
                        ProvisioningPlan plan = it.next();                        
                        if ( plan != null && !plan.hasBeenExecuted() ) {
                            boolean ready = dependencyEvaluator.eval(plan);                            
                            if ( ready ) {
                                execute(plan);
                                it.remove();                                
                            }
                        }                    
                    }
                    
                    // prevent iterating forever, this is basicaly "levels" deep.. This probably doesn't go
                    // too far deep.
                    if ( iterations++ > MAX )
                        throw new GeneralException("Iterated over the project trying to fulfill dependencies and did not finish all of the plans in a project.");
                    
                }
            }      
            
            // Unmanaged plans can also be dependent so evaluate the unmanaged plan for dependencies
            ProvisioningPlan unmanagedPlan = _project.getUnmanagedPlan();
            if ( unmanagedPlan != null ) {                
                dependencyEvaluator.eval(unmanagedPlan);                
            }

            // log provisioning transactions for any requests that were completely
            // filtered out and never provisioned
            logFilteredRequests();
        }

        final String phase2 = "PlanEvaluator.execute phase 2";
        Meter.enterByName(phase2);

        // Use this object to handle updates to the IIQ Identity or
        // ManagedAttribute objects which require locking.
        _iiqEvaluator.provision(_identity, _project);

        Meter.exitByName(phase2);
    }

    /**
     * Execute one plan from the project.
     */
    private void execute(ProvisioningPlan plan)
        throws GeneralException {

        String target = plan.getTargetIntegration();
        if (target == null) {
            // an umnaged plan, shouldn't be on this list
            log.error("Found unmanaged plan on the managed plan list!");
        }
        else {
            // Check if plan contains unstructured target collector name. If plan
            // contains target collector name, then get target collector application name.
            // Going ahead target collector application name is used to generate the
            // IntegrationConfig for target collector. If target collector application name
            // is null then IntegrationConfig for application is generated.
            // Starting release 6.4, IdentityIQ supports provisioning through unstructured
            // target collector.
            String targetCollectorAppName = plan.getAppForTargetCollector();

            IntegrationConfig config = getIntegration(target,
                                                      targetCollectorAppName);

            if (config == null) {
                log.error("Lost an IntegrationConfg!");
                // might want to audit this?
                // leave a failure on the plan so we don't try to 
                // apply it
                ProvisioningResult res = new ProvisioningResult();
                res.fail();
                plan.setResult(res);
            }
            else {
                IntegrationExecutor inst = null;
                try {
                    inst = IntegrationManager.getExecutor(config);
                }
                catch (Throwable t) {
                    // typically class not found
                    log.error(t);
                }

                if (inst == null) {
                    // might want to audit this?
                    log.error("Invalid executor for IntegrationConfig: " +
                              config.getName());
                    ProvisioningResult res = new ProvisioningResult();
                    res.fail();
                    plan.setResult(res);
                }
                else {
                    Request retryRequest = null;

                    // We always try one synchronous call before kicking
                    // off the request so we can pass things back to
                    // the caller in the ProvisioningPlan.

                    _provisioningRequests++;

                    // plan may be modified
                    provision(config, plan, true);
                            
                    // If any part of the plan needs a retry, launch
                    // a request.  In theory we could be filtering
                    // out the parts of the plan that don't need
                    // to be retried but they may have information
                    // needed for the parts that are being retried so
                    // leave them in for now.  It is the responsibility
                    // of the connector to annotate the plan so it 
                    // knows what to do and not do.
                    
                    String status = plan.getNormalizedStatus();

                    // Only launch request based retry if we are using older
                    // workflows or if we are explicity configured to
                    // enable them setting the ARG_NO_RETRY_RESULT set
                    // to false.
                    if (ProvisioningResult.STATUS_RETRY.equals(status) && 
                        !_project.getBoolean(PlanEvaluator.ARG_NO_RETRY_REQUEST)) { 

                        retryRequest = launchRequest(config, plan);
                    }
                    else if ( !plan.hasBeenExecuted() ) {
                        // BUG#16527: JdbcExecutor doesn't return a result trips up dependency evaluator
                        // If we've gotten here and the connector/integration didn't return
                        // a result do it for the connector using the normalized status
                        //    
                        // The hasBeenExecuted flag goes through the plan and looks for
                        // results at any level returns false if it doesn't find any 
                        // results
                        
                        ProvisioningResult defaultResult = new ProvisioningResult();
                        defaultResult.setStatus(status);
                        plan.setResult(defaultResult);
                    }

                    // logging is done here because we are only going to log transactions that we actually try to
                    // provision so if we could not find the integration config or executor then do not create a
                    // failed transaction object
                    if (plan.isProvisioned()) {
                        logProvisioningTransactions(_project.getMasterPlan(), plan, retryRequest);
                    }
                }
            }
        }
    }

    /**
     * Takes care of logging any provisioning transactions based on the configured
     * logging levels. In the retry case, the master plan will be the same as the
     * partitioned plan.
     *
     * @param masterPlan The master plan.
     * @param partitionedPlan The partitioned plan.
     * @throws GeneralException
     */
    private void logProvisioningTransactions(ProvisioningPlan masterPlan, ProvisioningPlan partitionedPlan)
        throws GeneralException {

        logProvisioningTransactions(masterPlan, partitionedPlan, null);
    }

    /**
     * Takes care of logging any provisioning transactions based on the configured
     * logging levels. In the retry case, the master plan will be the same as the
     * partitioned plan.
     *
     * @param masterPlan The master plan.
     * @param partitionedPlan The partitioned plan.
     * @param retryRequest The retry Request object.
     * @throws GeneralException
     */
    private void logProvisioningTransactions(ProvisioningPlan masterPlan, ProvisioningPlan partitionedPlan, Request retryRequest)
        throws GeneralException {

        ProvisioningTransactionService transactionService = new ProvisioningTransactionService(_context);

        // loop through account and object requests in the plan
        for (AbstractRequest abstractRequest : Util.iterate(partitionedPlan.getAllRequests())) {
            TransactionDetails details = new TransactionDetails();
            details.setProject(_project);
            details.setSource(masterPlan.getSource());
            details.setPartitionedPlan(partitionedPlan);
            details.setRequest(abstractRequest);
            details.setRetryRequest(retryRequest);

            transactionService.logTransaction(details);
        }
    }

    /**
     * Logs any requests in the project that were completely filtered and therefore
     * were not executed.
     *
     * @throws GeneralException
     */
    private void logFilteredRequests() throws GeneralException {
        ProvisioningTransactionService transactionService = new ProvisioningTransactionService(_context);

        for (AbstractRequest filteredRequest : Util.iterate(_project.getFiltered())) {
            // don't log filtered IIQ requests here as they will be handled later
            if (!ProvisioningPlan.isIIQ(filteredRequest.getApplication())) {
                boolean logged = Util.otob(filteredRequest.get(ProvisioningProject.ATT_FILTER_LOGGED));
                if (!logged) {
                    transactionService.logTransactionForFilteredRequest(_project, filteredRequest);
                }
            }
        }
    }

    /**
     * Return the IntegrationConfig named in a provisioning plan.
     * PlanCompiler maintains a cache of these, I suppose we could
     * too but we don't need them very often here.
     * 6.4 onwards, this is used to find IntegrationConfig for app
     */
    public IntegrationConfig getIntegration(String name) 
        throws GeneralException {

        IntegrationConfig config = 
            _context.getObjectByName(IntegrationConfig.class, name);

        if (config == null) {
            // this may be one of the newer Applications with provisioning

            Application app = _context.getObjectByName(Application.class, name);
            if (app != null) {
                // convert it
                config = new IntegrationConfig(app);
            }
            else {
                // warn?
            }
        }

        return config;
    }

    /**
     * Returns the IntegrationConfig. Starting release 6.4, IdentityIQ
     * supports provisioning through unstructured target collector.
     *
     * If 'targetCollectorAppName' is not null then IntegrationConfig for
     * standard integration or target collector is returned, else
     * IntegrationConfig for app or standard/managed integration is returned.
     *
     * @ignore 
     * Similar to PlanComiler, this uses IntegrationConfig cache to
     * detect whether integration is standard/managed integration or a
     * target collector. IntegrationConfig cache provides convenient
     * utilities for detection. If we do not use IntegrationConfig
     * cache, then we need to write alternative in the PlanEvalutor.
     */
    public IntegrationConfig getIntegration(String name, String targetCollectorAppName)
        throws GeneralException {
        // CONSEALINK-886
        // If 'targetCollectorAppName' is non null, meaning 'name' contains either 
        // target collector name or standard/managed integration config name.
        // The IntegrationFinder obtains IntegrationConfig for standard/managed
        // integrations when 'name' holds standard/managed integration config name.
        // Otherwise, IntegrationFinder obtains the IntegrationConfig for a target collector.

        // If 'targetCollectorAppName' is null, meaning 'name' holds application name 
        // or standard/managed integration name, hence IntegrationFinder obtains IntegrationConfig 
        // for an application or standard/managed integration.
        return _integrationFinder.getIntegrationConfig(name, targetCollectorAppName);
    }

    /**
     * Upgrade options.
     */
    private void fixArguments() {

        if (_project != null) {
            Attributes<String,Object> args = _project.getAttributes();
            if (args != null) {
                if (args.containsKey(ARG_IMMEDIATE_UPDATE)) {
                    if (!args.containsKey(ARG_OPTIMISTIC_PROVISIONING)) {
                        boolean b = args.getBoolean(ARG_IMMEDIATE_UPDATE);
                        args.putClean(ARG_OPTIMISTIC_PROVISIONING, b);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Requests
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch a Request to handle a provisioning retry.
     * This is called whenever there was a retryable item somewhere
     * inside the plan.
     * 
     * This might be a loggable event but what we're really
     * interested in is the call to the IntegrationExecutor
     * done below in provision() that the Request will
     * eventually call.  Adding a longer log here might
     * confuse things.
     *
     * Create a new plan that contains only the AbstractRequests that
     * did not succeed.  This is necessary because retry() just expects
     * to retry the entire project and if we don't remove completed requests
     * it will try to do them again.  I suppose we could have 
     * provision() check for an existing ProvisioningResult but it feels 
     * cleaner to filter them here.
     */
    private Request launchRequest(IntegrationConfig config,
                                  ProvisioningPlan plan) {

        Request req = null;

        // don't throw, let the other provisionings succeed?
        try {
            ProvisioningPlan retryPlan = buildRetryPlan(plan);

            if (retryPlan.isEmpty()) {
                // result normalization wasn't working, should have at
                // least one to retry
                log.error("Nothing to retry");
            }
            else {
                RequestDefinition def = _context.getObjectByName(RequestDefinition.class,
                                                           REQUEST_DEFINITION);
                if (def == null) 
                    log.error("Unconfigured integration request definition");
                else {
                    req = new Request(def);

                    // pass this through so we can use it for logging
                    String launcher = getRequester();
                    req.setLauncher(launcher);

                    if (_identity != null)
                        req.setAttribute(IntegrationRequestExecutor.ARG_IDENTITY,
                                         _identity.getName());

                    // TODO: This needs to evolve to be the Application name,
                    // actually this should be in the ProvisioningPlan
                    // targetIntegration property as well?
                    req.setAttribute(IntegrationRequestExecutor.ARG_INTEGRATION,
                                     config.getName());

                    // Create a "sliced" project containing all of the original
                    // options and this plan as the master plan.
                    // This ensures that all of the provisioning options make it
                    // through to the other side.

                    ProvisioningProject proj = new ProvisioningProject();
                    // don't need to clone we're just serializing 
                    proj.setAttributes(_project.getAttributes());
                    proj.setExpansionItems(_project.getExpansionItems());
                    proj.add(retryPlan);

                    req.setAttribute(IntegrationRequestExecutor.ARG_PROJECT, proj);

                    setRetryDate(config, plan, req);

                    if (log.isInfoEnabled()) {
                        log.info("Creating provisioning retry request:");
                        log.info(req.toXml());
                    }

                    RequestManager.addRequest(_context, req);
                }
            }
        }
        catch (Throwable t) {
            log.error(t.toString());
        }

        return req;
    }

    /**
     * Given a plan containing one or more items that need to be retried,
     * build a new plan containing only those items.
     */
    public static ProvisioningPlan buildRetryPlan(ProvisioningPlan src) {

        ProvisioningPlan retryPlan = new ProvisioningPlan();

        ProvisioningResult planResult = src.getResult();

        // make a retry plan
        retryPlan.setTargetIntegration(src.getTargetIntegration());
        retryPlan.setNativeIdentity(src.getNativeIdentity());
        retryPlan.setArguments(src.getArguments());
        retryPlan.setRequesters(src.getRequesters());

        // ProvisioningPlan._identity isn't necessary since it's passed
        // as a request argument.  

        // bring over the necessary requests
        List<AbstractRequest> requests = src.getAllRequests();
        if (requests != null) {
            for (AbstractRequest req : requests) {

                // Bug 27115 - Include if Account or Object Request is marked for retry, or any
                // one of the contained Attribute or Permission Requests is marked for retry.
                if (req.needsRetry()) {
                    ProvisioningResult accountResult = req.getResult();
                    AbstractRequest copy = req.cloneRequest();
                    // Cloning does not set the result, ideally it should be okay without
                    // the result since we are rebuilding plan here. But there is
                    // some piece of code which marks result as completed if null.
                    copy.setResult(req.getResult());
                    copy.setAttributeRequests(null);
                    copy.setPermissionRequests(null);

                    List<GenericRequest> genReqs = new ArrayList<GenericRequest>();
                    List<AttributeRequest> reqs = req.getAttributeRequests();
                    if ( !Util.isEmpty(reqs) ) {
                        genReqs.addAll(reqs);
                    }
                    List<PermissionRequest> permReqs = req.getPermissionRequests();
                    if ( !Util.isEmpty(permReqs) ) {
                        genReqs.addAll(permReqs);
                    }

                    boolean sawGenReqSuccess = false;
                    for ( GenericRequest genReq : Util.iterate(genReqs) ) {

                        // First looks for a result directly on the Attribute or Permission Request,
                        // then looks up at the Account or Object Request,
                        // and if that does not have one gets the result from the ProvisioningPlan.
                        ProvisioningResult genResult = genReq.getResult();
                        ProvisioningResult effectiveResult = null;
                        if ( genResult != null ) {
                            effectiveResult = genReq.getResult();

                            if (isSuccess(genResult)) {
                                sawGenReqSuccess = true;
                            }
                        } else if ( accountResult != null ) {
                            effectiveResult = req.getResult();
                        } else if ( planResult != null ) {
                            effectiveResult = src.getResult();
                        }
                        if ( effectiveResult == null || effectiveResult.isRetry() ) {
                            copy.add(genReq);
                        }
                    }

                    // if the account/object request op is a create and either the plan result was success,
                    // the account/object request result was success or there was at least one successful
                    // attribute/permission request then the create operation should be changed to a modify
                    // in the retry plan
                    if (ObjectOperation.Create.equals(req.getOp()) &&
                        (isSuccess(accountResult) || isSuccess(planResult) || sawGenReqSuccess)) {

                        copy.setOp(ObjectOperation.Modify);
                    }

                    // Request should be added even if attribute and permission requests are empty
                    retryPlan.addRequest(copy);
                }
            }
        }
        return retryPlan;
    }

    /**
     * Determines if the result is successful. The result is successful if the
     * status is equal to committed or queued.
     *
     * @param result The result.
     * @return True if success, false otherwise.
     */
    private static boolean isSuccess(ProvisioningResult result) {
        return result != null &&
               (ProvisioningResult.STATUS_COMMITTED.equals(result.getStatus()) ||
                ProvisioningResult.STATUS_QUEUED.equals(result.getStatus()));
    }

    /**
     * Calculate the execution date for the provisioning retry and the
     * retry interval.
     *
     * We have historically left this null which means it will retry on 
     * the next RequestProcessor cycle. This is likely to be wrong most of
     * the time, think about configuring this.
     *
     * We have also left the retry interval zero, which causes RequestProcessor
     * to use the interval stored in the RequestDefinition which is unset
     * by default so it falls back to hard coded default of 1 hour. 
     *
     * Ideally we would let the Connector set expected retry time and interval
     * in the ProvisioningResult since they might know.  If we did that we would
     * have to break this up into multiple Requests since each AccountRequest could
     * have a different ProvisioningResult with a different retry.
     *
     * If the plan is being retried due to an application maintenance window
     * schedule the request after the window.  If it is in permanent maintenance
     * window, schedule it for 24 hours from now.  
     *
     * Letting the interval default to 1 hour, they can still override it
     * in the RequestDefinition, but that's for all applications, it should
     * be app specific.
     */
    private void setRetryDate(IntegrationConfig config, ProvisioningPlan plan,
                              Request req) {

        // transient property captured by execute() 
        long expiration = plan.getMaintenanceExpiration();
        if (expiration == 0) {
            // a normal retry, letting this retry on the next
            // cycle as we have before, but I think it makes more sense
            // to have an initial hour wait
        }
        else if (expiration < 0) {
            // a permanent window, try again in one day with a 1 hour interval
            // ugh, really should be using Calender but it sucks so bad
            // utime resolution is in milliseconds
            long next = new Date().getTime() + (24 * 60 * 60 * 1000);
            req.setNextLaunch(new Date(next));
        }
        else {
            req.setNextLaunch(new Date(expiration));
            // in theory retry interval could be shorter than an hour since
            // we expect it to be up by now
        }
    }

    /**
     * Entry point called by IntegrationRequstExecutor to retry a plan.
     * See if we can simplify the arguments here, IntegrationConfig should
     * be in the project already, as can Identity.
     */
    public String retry(Identity ident, IntegrationConfig config,
                        ProvisioningProject project)
        throws GeneralException {

        String status = null;

        // establish context, this should a newly built PlanEvalator
        _identity = ident;
        _project = project;

        // For awhile we stored the plan to be retried as the master plan,
        // move that to the integration plan list so it looks more like
        // a normal project, necessary for IIQEvaluator.
        ProvisioningPlan plan = project.getMasterPlan();
        if (plan != null) {
            // transfer to the integration plan list
            project.setMasterPlan(null);
            project.setPlans(null);
            project.add(plan);
        }
        else {
            // shold only have one plan in here for a single connector
            List<ProvisioningPlan> plans = project.getIntegrationPlans();
            if (plans == null || plans.size() == 0)
                log.error("Provisioning retry with no plans");
            else {
                if (plans.size() > 1) 
                    log.error("Retry request with more than one plan");
                plan = plans.get(0);
            }
        }

        if (plan != null) {

            // Reset all former results, Connector must set new ones
            plan.resetResults();

            if (log.isInfoEnabled()) {
                log.info("Retrying plan:");
                log.info(plan.toXml());
            }

            // Since this is a retry and we're using the plan from the request,
            // changes to this plan won't propagate back.  Setting canModifyPlan
            // to false gets around this.
            provision(config, plan, false);

            // update any provisioning transaction objects
            logProvisioningTransactions(plan, plan);

            status = plan.getNormalizedStatus();

            if (ProvisioningResult.STATUS_RETRY.equals(status)) {
                // have to filter the accounts again
                // buildRetryPlan does the work but we want to keep 
                // the original plan so give it the filtered account list
                ProvisioningPlan retryPlan = buildRetryPlan(plan);
                plan.setAccountRequests(retryPlan.getAccountRequests());
                plan.setObjectRequests(retryPlan.getObjectRequests());
            }

            // apply the changes we can
            _iiqEvaluator.provision(_identity, project);
        }

        return status;
    }

    /**
     * Synchronously call the IntegrationExecutor and return 
     * an annotated plan containing ProvisioiningResults as
     * well as other information set by the executor. 
     * 
     * This is always called once before scheduling a background request.
     * A request is scheduled only if the initial synchronous call fails
     * with a retryable error.
     * 
     * Since this can be called by IntegrationRequestExecutor, it
     * must have passed any of the options through the request and
     * set them on this object.
     *
     * NOTE: We update statistics here but those will only
     * be meaningful if we do an initial synchronous call.  
     * Once this gets passed through a Request we lose insight into
     * what happened.
     * UPDATE: We now try copy changes back to the original plan even
     * if this is a retry.
     * 
     * The passed plan is modified as a side effect, call needsRetry
     * on it to see if it needs to be retried.
     */
    private void provision(IntegrationConfig config,
                           ProvisioningPlan plan,
                           boolean canModifyPlan)
        throws GeneralException {

        // Normally we will use a result returned by the connector,
        // but start by maintaining our own to hold things that
        // happen before we call the connector.
        ProvisioningResult preResult = new ProvisioningResult();
        ProvisioningResult connectorResult = null;

        // eat our own exceptions and turn them into request failures
        try {
            IntegrationExecutor inst = IntegrationManager.getExecutor(config);
            if (inst == null) {
                String msg = "Invalid executor for IntegrationConfig: " +
                    config.getName();
                log.error(msg);
                _provisioningErrors++;
                preResult.fail();
                preResult.addError(msg);
            }
            else if (!checkMaintenanceWindow(config, plan, preResult)) {
                // let the executor initialize itself,
                // since we normalize plans we should only be
                // here once for each integration, but if we pooled
                // them somewhere we could avoid this
                inst.configure(_context, config);

                // First run any plan initializing logic.  This is used
                // in cases where we need to splice in customer-specific
                // pre-processing.  Let the rule return a result object,
                // in case it has something interesting to say.
                DynamicValue logic = config.getPlanInitializerLogic();
                if (logic != null) {
                    // If the attributes map isn't there, make one so the rule
                    // doesn't have to worry about it.  
                    // NOTE: prior to 5.0 this was called "integration data"
                    if (plan.getArguments() == null)
                        plan.setArguments(new Attributes<String, Object>());

                    // NOTE: The "scriptArgs" that are passed into the PlanCompiler
                    // aren't stored in the project and are not accessible here.
                    // I think this should be less of an issue for this than the
                    // Template and Field scripts.  If the workflow needs to pass something
                    // in they can always doctor the ProvisioningProject before evaluating it.
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("identity", _identity);
                    params.put("integration", config);
                    params.put("plan", plan);

                    // ProvisioningConfig migration: new logic expects to have
                    // the Application not an IntegrationConfig so pass that too
                    // if we have it.
                    Connector con = inst.getConnector();
                    if ( null != con ) {
                        params.put("application", con.getApplication());
                    }

                    DynamicValuator dv = new DynamicValuator(logic);
                    Object retval = dv.evaluate(_context, params);

                    // Rules can return result objects to convey errors and
                    // halt provisioning. Have to support both the new 
                    // ProvisioningResult and the original RequestResult.

                    if (retval instanceof ProvisioningResult)
                        preResult = (ProvisioningResult)retval;

                    else if (retval instanceof RequestResult)
                        preResult = new ProvisioningResult((RequestResult)retval);
                }
                
                // initializer rules can only indicate success or failure, the
                // other options like retry and queued are ignored
                if (!preResult.isFailed()) {

                    ProvisioningPlan filtered = null, filteredAttributesPlan = null;
                    
                    try {
                        // Attribute filtering needs to happen before the mapNames
                        // call below because once the appname gets mapped we can't get
                        // to the IIQ app name.
                        filteredAttributesPlan = filterAttributes(plan);

                        // Map names before sending it down.
                        // Do this only for old IntegrationExecutors, the ones that
                        // wrap Connectors/TargetCollectors do not need to do mapping,
                        // ConnectorProxy/TargetCollectorProxy will handle it.
                        if ( inst.getConnector() == null ) {
                            mapNames(config, plan);
                        }

                        // standardize representation of Boolean, this
                        // is not undone after the Connector call
                        normalizeValueTypes(plan);

                        // pull out things that the Connector won't understand
                        filtered = filterPlan(plan);

                        // !! if ARG_SIMULATE is on should we even be here?
                        // Currently Provisioner.impactAnalysis     
                        // will force the integrationlist to null so we'll
                        // never get here, but it might be interesting
                        // to have the integrations involved in the simulation?

                        if (_identity != null) {
                            // identity is passed this way now
                            plan.setIdentity(_identity);

                            // Derive the id of the IDM meta-account if there is one
                            String nativeId = IntegrationUtil.getIntegrationIdentity(config, _identity);
                            plan.setNativeIdentity(nativeId);
                        }

                        // call the connector
                        if (log.isInfoEnabled()) {
                            log.info("Executing connector/unstructured target collector/integration plan: " + config.getName());
                            ProvisioningPlan maskedPlan = ProvisioningPlan.getLoggingPlan(plan);
                            log.info(maskedPlan.toXml());
                        }

                        // decrypt passwords, must do this after any logging
                        decryptSecrets(plan);

                        // filtering may remove everything if this is a non-group MA
                        // so don't bother calling the connector
                        if (!plan.isEmpty()) {
                            // Before sending the plan to connector, check if any additional information is requested
                            // via application configuration 'provisioningMetaData'.
                            // In some cases target connector might need extra info in order to process the provisioning plan.
                            // ex - If ADConnector is configured to use objectGUID as native identity, then it requires
                            // distinguishedName (available in Link attributes) to process provisioning operation.
                            
                            addProvisioningMetadata(plan);
                            
                            connectorResult = inst.provision(plan);
                            // tested later to see if we should make a PTO entry
                            plan.setProvisioned(true);
                            pullUpNativeIdentity(connectorResult, plan);
                        }

                        // reencrypt passwords in case we have to retry
                        encryptSecrets(plan);

                        if (log.isInfoEnabled()) {
                            log.info("Connector/unstructured target collector/integration plan after execution:");
                            ProvisioningPlan maskedPlan = ProvisioningPlan.getLoggingPlan(plan);
                            log.info(maskedPlan.toXml());
                            if (connectorResult != null) {
                                log.info("Connector/unstructured target collector/integration provisioning result:");
                                log.info(connectorResult.toXml());
                            }
                        }
                    }
                    finally {
                        // undo the plan filtering
                        unfilterPlan(plan, filtered);

                        // undo the name transformations 
                        try {
                            if ( inst.getConnector() == null ) {
                                unmapNames(config, plan);
                            }

                            // unfiltering needs to happen after unmap because we need
                            // original iiq names not mapped name
                            unfilterAttributes(plan, filteredAttributesPlan);
                        }
                        catch (Throwable t) {
                            // Sigh, do not let this look like a provisioning
                            // failure since technically it succeeded.
                            // Attempting to apply the plan may cause strange
                            // things to happen though so maybe that's
                            // not so bad.
                            log.error("Unable to unmap provisioning plan names");
                            log.error(t);
                        }

                        // in case we threw before encryptSecrets was called
                        // in the above clause, make another pass
                        encryptSecrets(plan);
                    }
                }
            }
        }
        catch (Throwable t) {
            fail(preResult, t);
        }

        // Normalize the top-level result so we can deal with it
        // consistently.
        if (preResult.isFailed() || preResult.isRetry()) {
            // We had a problem before calling the connector, the plan initializer
            // indidicated early failure, or we had an exception calling the connector.
            // We assume that the entire request failed and can control the plan result.
            // isRetry is true only for maintenance windows.
            plan.setResult(preResult);
        }
        else {
            // Connectors can return top-level results in two ways, 
            // a return value from the provision() method and in the plan.
            // They are not supposed to use both styles, prefer the
            // method return value if we get both.
            if (connectorResult != null) {
                ProvisioningResult planResult = plan.getResult();
                if (planResult != null && planResult != connectorResult) {
                    log.warn("Connector returned two results!");
                }
                plan.setResult(connectorResult);
            }

            if (preResult.hasMessages()) {
                // Odd case, the plan initializer rule must have left some 
                // warning messages.  Merge these with the connector result.
                ProvisioningResult planResult = plan.getResult();
                if (planResult != null)
                    planResult.assimilateMessages(preResult);
                else {
                    // can reuse the preResult but try to be smart
                    // about the overall status
                    plan.setResult(preResult);
                    preResult.setStatus(plan.getNormalizedStatus());
                }
            }
        }

        // Post-provision auditing.
        // note that we audit before we start propagating results
        // to the identity cube since technically the provisioning has
        // now happened and problems we have later do not change that
        audit(config, plan);

        String status = plan.getNormalizedStatus();
        if ( ! Util.nullSafeEq(ProvisioningResult.STATUS_FAILED, status) ) {
            // don't let failures here halt processing
            try {
                // some stuff for RemediationManager
                propagateResults(config, plan, canModifyPlan);

                // save request history
                saveProvisioningRequest(config, plan);
       
                // !! this is locking the identity once for every password
                // move this to IIQEvaluator
                // should try to push locking higher or defer this to
                // IIQEvalator?
                updatePasswordHistory(plan);
            }
            catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        }
    }
    
    /**
     * Before calling a Connector, check to see if the Application is within
     * a maintenance window.  If it is, we short-circut the connector call and
     * make it look like a failure.
     */
    private boolean checkMaintenanceWindow(IntegrationConfig config, ProvisioningPlan plan,
                                           ProvisioningResult result) {

        boolean inMaintenance = false;

        long expiration = config.getMaintenanceExpiration();
        if (expiration < 0) {
            inMaintenance = true;
        }
        else if (expiration > 0) {
            Date exp = new Date(expiration);
            inMaintenance = exp.after(new Date());
        }

        if (inMaintenance) {
            if (log.isInfoEnabled()) {
                log.info("Provisioning ignored for application in maintenance: " + config.getName());
            }

            if (_project.getBoolean(PlanEvaluator.ARG_NO_MAINTENANCE_WINDOW_RETRY)) {
                // treat as a hard failure
                result.setStatus(ProvisioningResult.STATUS_FAILED);
                // leave something that explains the situation
                Message msg = new Message(Message.Type.Error, MessageKeys.PROV_APP_IN_MAINTENANCE,
                                          config.getName());
                result.addError(msg);
            } else {
                // Whether this generates a Request is controlled by
                // ARG_NO_RETRY_REQUEST later.  If we wanted to get fancy we would
                // schedule the retry immediately after the window expires.  But if we
                // do that for Requests the expectation is that the LCM workflow handles it the
                // same way which is much more complicated.
                result.setStatus(ProvisioningResult.STATUS_RETRY);
                
                // save this so we can be smarter about scheduling the retry request later
                plan.setMaintenanceExpiration(config.getMaintenanceExpiration());
                
                Message msg = new Message(Message.Type.Warn, MessageKeys.PROV_APP_IN_MAINTENANCE,
                        config.getName());
                result.addWarning(msg);
            }




            // we are making this look like a connector error,
            // this flag is necessary to get a PTO entry
            plan.setProvisioned(true);
            
        }

        return inMaintenance;
    }

    /*
     * Some connectors derive the nativeIdentity during provisioning instead of having us provide it.
     * In these cases, we need to go back to the AccountRequest and update it with the nativeIdentity
     * if it wasn't originally provided.  We do this by using the ProvisioningResult's ResourceObject
     * to determine the identity.
     * 
     * At this time, it's unexpected for the ResourceObject to have a nativeIdentity different than that
     * of the AccountRequest.  For now we ignore differences.
     */
    private void pullUpNativeIdentity(ProvisioningResult connectorResult,
            ProvisioningPlan plan) throws GeneralException {

        ProvisioningResult result = connectorResult;

        // The connector may have returned a result in the plan rather than from the provision() call.
        if (null == connectorResult) {
            // Try to get the top-level result.
            result = plan.getResult();

            // If the top-level result doesn't exist, try to get it from an account request.
            if (null == result) {
                for (AccountRequest request : Util.iterate(plan.getAccountRequests())) {
                    result = request.getResult();
                    if (null != result) {
                        break;
                    }
                }
            }
        }

        // Only pull up the native identity if we have a result.
        if (null != result) {
            ResourceObject ro = result.getObject();
            if (ro == null) {
                log.debug("No ResourceObject found from result");
                return;
            } else if (Util.isEmpty(plan.getAccountRequests())) {
                log.debug("No AccountRequests in plan");
                return;
            }
            String nativeIdentity = ro.getIdentity();
            // Find the AccountRequest
            for (AccountRequest request : plan.getAccountRequests()) {
                if (Util.isNothing(request.getNativeIdentity())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Pulling up nativeIdentity " + nativeIdentity + " for accountRequest: \n" + AbstractRequest.getLoggingRequest(request).toXml());
                    }
                    request.setNativeIdentity(nativeIdentity);
                }
            }
        }
    }

/**
    * If the result has a requestId and the the result is queued, call
    * down to the connector and check the status of an existing request.
    * 
    * The result's status and messages returned from the the connector.checkstatus 
    * method call will be merged into the passed in result.
    *  
    */
    public void checkStatus(IntegrationConfig config, ProvisioningResult result)
        throws Exception {
        
        if ( result != null  && result.isQueued() ){
            String reqId = result.getRequestID();
            if ( reqId != null ) {
                IntegrationExecutor inst = IntegrationManager.getExecutor(config);
                if (inst == null) {
                    String msg = "Invalid executor for IntegrationConfig: " + config.getName();
                    log.error(msg);
                    return;
                }

                inst.configure(_context, config);

                try {
                    ProvisioningResult statusResult = inst.checkStatus(reqId);
                    if (statusResult != null) {
                        // override the status and add any errors
                        String status = statusResult.getStatus();
                        if (status != null)
                            result.setStatus(status);
                        result.assimilateMessages(statusResult);
                    }
                }
                catch(UnsupportedOperationException e ) {
                    // handle this by setting this to commit
                    // jsl - this was added by Dan awhile ago for 9041, it seems
                    // presumtuous that if this method isn't implemented we'll
                    // always assume committed.  Why bother having the connector
                    // return QUEUED with a request id if we're just going to
                    // assume it was committed the next time the status checker
                    // task runs??
                    result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                }
            }            
        }        
    }
    
    /**
     * Propagate request ids and target integrations to the accounts
     * !! This has been done for awhile and is used by RemediationManager
     * and RemediationDataSource.  If this is the initial request, we can
     * modify the plan directly and this will get saved on the certification.
     * Otherwise, if this is a retry, we are dealing with the plan from the
     * retry integration request.  We will attempt to load the plan that
     * is stored on the certification and copy the information onto that.
     *
     * Propgagting the targetIntegration is the same here but the
     * request is a bit more complicated due to the possibily of
     * granular account results.
     */
    private void propagateResults(IntegrationConfig config,
                                  ProvisioningPlan plan,
                                  boolean canModifyPlan)
        throws GeneralException {

        if (canModifyPlan) {
            propagateResults(config, plan, plan);
        }
        else {
            // Here we are assuming that the tracking IDs in the plan are
            // CertificationItem IDs.  This is typically true for remediation
            // plans, but that may not always be the case.
            Set<String> trackingIds = collectTrackingIds(plan);
            if (!trackingIds.isEmpty()) {
                for (String trackingId : trackingIds) {
                    propgateResultToCertification(config, plan, trackingId);
                }
            }
        }
    }
    
    /**
     * Return a set of all of the tracking IDs from the given plan.
     */
    private static Set<String> collectTrackingIds(ProvisioningPlan plan) {
        Set<String> trackingIds = new HashSet<String>();
        addTrackingIds(plan.getTrackingId(), trackingIds);

        List<AbstractRequest> requests = plan.getAllRequests();
        if (requests != null) {

            for (AbstractRequest req : requests) {

                addTrackingIds(req.getTrackingId(), trackingIds);

                if (null != req.getAttributeRequests()) {
                    for (AttributeRequest attrReq : req.getAttributeRequests()) {
                        addTrackingIds(attrReq.getTrackingId(), trackingIds);
                    }
                }
                if (null != req.getPermissionRequests()) {
                    for (PermissionRequest permReq : req.getPermissionRequests()) {
                        addTrackingIds(permReq.getTrackingId(), trackingIds);
                    }
                }
            }
        }
        return trackingIds;
    }

    /**
     * Add the given trackingId to the set if non-null.
     */
    private static void addTrackingIds(String trackingId, Set<String> trackingIds) {
        if (null != trackingId) {
            trackingIds.addAll(Util.csvToList(trackingId));
        }
    }

    /**
     * Propagate the targetIntegration and request ID into the plan stored on 
     * the given CertificationItem.  This is used for retry requests where the
     * PlanEvaluator doesnt' have a handle to the actual plan from the cert.
     */
    private void propgateResultToCertification(IntegrationConfig config,
                                               ProvisioningPlan plan,
                                               String certItemId)
        throws GeneralException {

        // Take care to check for null since this may not actually be a
        // CertificationItem ID.
        CertificationItem item =
            _context.getObjectById(CertificationItem.class, certItemId);
        if (null != item) {
            CertificationAction action = item.getAction();
            if (null != action) {
                ProvisioningPlan actionPlan = action.getRemediationDetails();
                if (null != actionPlan) {
                    propagateResults(config, plan, actionPlan);
                }
    
                actionPlan = action.getAdditionalActions();
                if (null != actionPlan) {
                    propagateResults(config, plan, actionPlan);
                }
    
                _context.saveObject(item);
                
                // Is it alright to commit here?
                _context.commitTransaction();
            }
        }
    }
    
    /**
     * Copy the targetIntegration and request IDs from the given plan into the
     * account requests on the "to" plan.
     */
    private void propagateResults(IntegrationConfig config,
                                  ProvisioningPlan from, 
                                  ProvisioningPlan to) {

        String rid = null;
        String status = null;
        ProvisioningResult top = from.getResult();
        if (top != null) {
            rid = top.getRequestID();
            status = top.getStatus();
        }

        List<AbstractRequest> requests = to.getAllRequests();
        if (requests != null) {
            for (AbstractRequest req : requests) {
                // todo: might want an alternate display name
                req.setTargetIntegration(from.getTargetIntegration());
                // only if there is no granular account
                // we have historically only propagated the ID not anything
                // else int the ProvisionignResult, this semes strange but I don't
                // want to worry about the side effects now, this is ugly
                // Yes, it's ugly, we should propagate the status as well
                if (rid != null) {
                    ProvisioningResult res = req.getResult();
                    if (res == null || res.getRequestID() == null) {
                        if (res == null) {
                            res = new ProvisioningResult();
                            req.setResult(res);
                            if (null != status && res.getStatus() == null) {
                                res.setStatus(status);
                            }
                        }
                        res.setRequestID(rid);
                    }
                }
            }
        }
    }

    /**
     * Add a caught exception to the provisioning result.
     */
    private void fail(ProvisioningResult result, Throwable t) {

        if (log.isErrorEnabled())
            log.error(t.getMessage(), t);

        // hard to debug without some context
        _provisioningErrors++;
        result.fail();
        result.addError(t.toString());
    }

    /**
     * Apply name mappings to a plan before passing it on to the integration.
     * 
     * This is old-style name mapping defined in the IntegrationConfig
     * and performed during plan evaluation.  For new-style read/write
     * Connectors, the mapping is defined in either the Schema or
     * the ProvisioningConfig and is handled at a lower level by
     * ConnectorProxy.
     */
    private void mapNames(IntegrationConfig config, ProvisioningPlan plan) {

        // Not supporting ObjectRequests here since none of the
        // old IntegrationExecutors do.

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                String appname = account.getApplication();
                ManagedResource mr = config.getManagedResource(appname);
                if (mr != null) {
                    String altname = mr.getName();
                    if (altname != null)
                        account.setApplication(altname);
                    
                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        for (AttributeRequest att : atts) {
                            
                            String attname = att.getName();
                            ResourceAttribute ra = mr.getResourceAttribute(attname);
                            if (ra != null) {
                                altname = ra.getName();
                                if (altname != null)
                                    att.setName(altname);
                                // TODO: value transfomration rules?
                            }

                            // If any Retains filtered down to this level
                            // convert then to Adds, it means they were missing
                            Operation op = att.getOp();
                            if (op == Operation.Revoke)
                                att.setOp(Operation.Remove);

                            else if (op == Operation.Retain)
                                att.setOp(Operation.Add);
                        }
                    }
                    
                    // NOTE: we don't have a model for permission
                    // mappings, assume if we get those they're raw

                }
            }
        }
    }

    /**
     * Undo the name mappings perfored by mapNames.
     */
    private void unmapNames(IntegrationConfig config, ProvisioningPlan plan) {

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                String appname = account.getApplication();
                ManagedResource mr = config.getRemoteManagedResource(appname);
                if (mr != null) {
                    String altname = mr.getLocalName();
                    if (altname != null)
                        account.setApplication(altname);
                    
                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        for (AttributeRequest att : atts) {
                            
                            String attname = att.getName();
                            ResourceAttribute ra = mr.getRemoteResourceAttribute(attname);
                            if (ra != null) {
                                altname = ra.getLocalName();
                                if (altname != null)
                                    att.setName(altname);
                                // TODO: value transfomration rules?
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Adjust values in the plan so that they match the application schema.
     * This is a one-directional transformation, we won't undo this after
     * the connector returns.
     * 
     * This was added for bug 14189 and primarly needs to handle booleans.
     * That's all we're going to hit for this bug, though in theory we 
     * should be checking for non-strings and coerce those too.
     *
     * Does it make any sense to do this earlier during compilation?  
     * Might be better for tracing.
     */
    private void normalizeValueTypes(ProvisioningPlan plan) 
        throws GeneralException {

        // these are a pain to build so optimize on there
        // being only one Application in a plan, the usual case
        Application fieldApplication = null;
        Map<String,Field> accountFields = null;
        Map<String,Field> groupFields = null;

        List<AbstractRequest> requests = plan.getAllRequests();
        if (requests != null) {
            for (AbstractRequest req : requests) {
                
                Application app = req.getApplication(_context);
                if (app == null) {
                    // normally IIQ, ignore
                    continue;
                }

                String schemaType = Connector.TYPE_ACCOUNT;
                if (!(req instanceof AccountRequest)) {
                    schemaType = req.getType();
                    if (schemaType == null) {
                        // type=null is an old convention for groups, 
                        // shouldn't have these any more?
                        schemaType = Connector.TYPE_GROUP;
                    }
                }

                Schema schema = app.getSchema(schemaType);
                boolean doingGroups = app.hasGroupSchema(schemaType);

                List<AttributeRequest> atts = req.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest att : atts) {
                        
                        String type = null;
                        AttributeDefinition def = null;
                        if (schema != null)
                            def = schema.getAttributeDefinition(att.getName());
                        
                        if (def != null) {
                            // schema definitions always win
                            type = def.getType();
                        }
                        else {
                            // might be from a policy field, sigh have to have
                            // two maps for accounts and groups
                            if (app != fieldApplication) {
                                // cache these, usually only one app per plan
                                accountFields = null;
                                groupFields = null;
                                fieldApplication = app;
                            }

                            Field field = null;
                            if (doingGroups) {
                                if (groupFields == null)
                                    groupFields = cachePolicyFields(app, doingGroups);
                                field = groupFields.get(att.getName());
                            }
                            else {
                                if (accountFields == null)
                                    accountFields = cachePolicyFields(app, doingGroups);
                                field = accountFields.get(att.getName());
                            }

                            if (field != null)
                                type = field.getType();
                        }
                        
                        // TODO: Only handling booleans for now
                        if (BaseAttributeDefinition.TYPE_BOOLEAN.equals(type)) {

                            att.setValue(new Boolean(Util.otob(att.getValue())));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Helper for fixValueTypes.
     * Walk over the create and update provisioning policies and build a 
     * lookup map.
     */
    private Map<String,Field> cachePolicyFields(Application app, boolean group) {

        Map<String,Field> map = new HashMap<String,Field>();

        List<Template> templates = app.getOldTemplates();
        if (templates != null) {
            for (Template tmp : templates) {
                Template.Usage usage = tmp.getUsage();

                if ((group && 
                     (usage == Template.Usage.Create ||
                      usage == Template.Usage.Update ||
                      usage == Template.Usage.Delete)) ||
                    (!group &&
                     (usage == Template.Usage.Create ||
                      usage == Template.Usage.Update ||
                      usage == Template.Usage.Delete ||
                      usage == Template.Usage.Enable ||
                      usage == Template.Usage.Disable ||
                      usage == Template.Usage.Unlock ||
                      usage == Template.Usage.ChangePassword))) {

                    TemplateFieldIterator fieldIterator = new TemplateFieldIterator(tmp, _context);
                    while (fieldIterator.hasNext()) {
                        Field field = fieldIterator.next();
                        map.put(field.getName(), field);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Remove things from the plan that the Connector won't understand.
     * This was added for group provisioning since there can now
     * be ObjectRequests in the plan that either don't target groups
     * or contain IIQ ManagedAttribugte properties that aren't
     * in the group Schema.  Many connectors will just ignore
     * the extra attributes but some may not.
     *
     * This is all rather messy since the plan model is bidirectional.
     * The returned plan must be merged back in with the connector
     * result plan later by calling unfilterPlan.
     *
     * Hmm, there are two ways this can work, we can either
     * filter thigns that are not extended attributes or system attributes,
     * or we can let the Schema be our guide and only pass things
     * declared in the group schema.  The later is easier but it
     * means we can't insert hints to the connector in the initializer rule
     * which happens a lot in account plans.  
     */
    private ProvisioningPlan filterPlan(ProvisioningPlan src)  {

        ProvisioningPlan filtered = new ProvisioningPlan();

        try {
            //System.out.println("Plan before filtering:");
            //System.out.println(src.toXml());
        } catch (Throwable t) {}

        List<ObjectRequest> orlist = src.getObjectRequests();
        if (orlist != null) {
            ListIterator<ObjectRequest> ors = orlist.listIterator();
            while (ors.hasNext()) {
                ObjectRequest or = ors.next();
                Schema schema;
                try {
                    Application app = or.getApplication(_context); 
                    if (app != null) {
                        schema = app.getSchema(or.getType());
                    } else {
                        schema = null;
                    }
                } catch (GeneralException e) {
                    schema = null;
                    log.error("Could not find an application corresponding to the " + or.getApplication() + " application that was set in the Provisioning Plan");
                }
                
                if (schema == null || Util.otob(or.getArgument(ObjectRequest.ATT_OBJ_REQ_NO_PROVISIONING))) {
                    // the entire request is filtered for unmanaged plans
                    ors.remove();
                    filtered.addRequest(or);
                    continue;
                } else if (or.getType() != null && or.isGroupRequest()) {
                    
                    // Group plans will normally include several 
                    // IIQ system attributes, 
                    List<AttributeRequest> attlist = or.getAttributeRequests();
                    if (attlist != null) {
                        ObjectRequest save = new ObjectRequest();
                        save.setApplication(or.getApplication());
                        save.setNativeIdentity(or.getNativeIdentity());
                        filtered.addRequest(save);
                        
                        ObjectConfig config = ObjectConfig.getObjectConfig(ManagedAttribute.class);

                        ListIterator<AttributeRequest> atts = attlist.listIterator();
                        while (atts.hasNext()) {
                            AttributeRequest att = atts.next();
                            String name = att.getName();
                            if (ManagedAttribute.isSystemAttribute(name) ||
                                config.getObjectAttribute(name) != null) {
                                atts.remove();
                                save.add(att);
                            }
                        }
                    }
                }
            }
        }

        try {
            //System.out.println("Plan after filtering:");
            //System.out.println(src.toXml());

            //System.out.println("Filtered plan:");
            //System.out.println(filtered.toXml());
        } catch (Throwable t) {}

        return filtered;
    }
    
    /**
     * This will lookup the fields in create or update provisioning form
     * if a field is 'displayOnly' it will be filtered
     * 
     */
    public ProvisioningPlan filterAttributes(ProvisioningPlan src) {
        
        ProvisioningPlan filtered = new ProvisioningPlan();
        
        filterAccountRequestAttributes(src, filtered);

        filterGroupRequestAttributes(src, filtered);
        
        return filtered;
    }

    /**
     * Filter group attribute requests from the provisioning plan where the 
     * fields are 'displayOnly'
     * 
     * @see Application#getBlockedAccountAttributeNames() for more details
     */
    private void filterGroupRequestAttributes(ProvisioningPlan src, ProvisioningPlan filtered) {

        if (Util.isEmpty(src.getObjectRequests())) {
            return;
        }

        for (ObjectRequest objectRequest : src.getObjectRequests()) {
            if (Util.isEmpty(objectRequest.getAttributeRequests())) {
                continue;
            }
            if (!objectRequest.isGroupRequest()) {
                continue;
            }
            
            Iterator<AttributeRequest> iterator = objectRequest.getAttributeRequests().iterator();
            while (iterator.hasNext()) {
                AttributeRequest attributeRequest = iterator.next();
                if (attributeRequest.getName() == null) {
                    continue;
                }
                
                try {
                    BlockedAttributeEvaluator blockedAttrEvaluator = new BlockedAttributeEvaluator();
                    if (blockedAttrEvaluator.isGroupAttributeBlocked(objectRequest, attributeRequest)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Filtering group attribute: " + attributeRequest.getName());
                        }
                        iterator.remove();
                        ObjectRequest destObjectRequest = filtered.getObjectRequest(objectRequest.getApplication(), objectRequest.getInstance(), objectRequest.getNativeIdentity());
                        if (destObjectRequest == null) {
                            destObjectRequest = new ObjectRequest();
                            destObjectRequest.setApplication(objectRequest.getApplication());
                            destObjectRequest.setInstance(objectRequest.getInstance());
                            destObjectRequest.setNativeIdentity(objectRequest.getNativeIdentity());
                            filtered.addRequest(destObjectRequest);
                        }
                        destObjectRequest.add(attributeRequest);
                    }
                } catch (Throwable ex) {
                    log.error(ex);
                }
            }
        }
    }

    /**
     * Filter Account attribute requests from the provisioning plan where the 
     * fields are 'displayOnly'
     * 
     * @see Application#getBlockedGroupAttributeNames() for more details.
     */
    private void filterAccountRequestAttributes(ProvisioningPlan src, ProvisioningPlan filtered) {

        if (Util.isEmpty(src.getAccountRequests())) {
            return;
        }
        
        for (AccountRequest accountRequest : src.getAccountRequests()) {
            if (Util.isEmpty(accountRequest.getAttributeRequests())) {
                continue;
            }
            
            Iterator<AttributeRequest> iterator = accountRequest.getAttributeRequests().iterator();
            while (iterator.hasNext()) {
                AttributeRequest attributeRequest = iterator.next();
                if (attributeRequest.getName() == null) {
                    continue;
                }
                
                try {
                    BlockedAttributeEvaluator blockedAttrEvaluator = new BlockedAttributeEvaluator();
                    if (blockedAttrEvaluator.isAccountAttributeBlocked(accountRequest, attributeRequest)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Filtering account attribute: " + attributeRequest.getName());
                        }
                        iterator.remove();
                        AccountRequest destAccountRequest = filtered.getAccountRequest(accountRequest.getApplication(), accountRequest.getInstance(), accountRequest.getNativeIdentity());
                        if (destAccountRequest == null) {
                            destAccountRequest = new AccountRequest();
                            destAccountRequest.setApplication(accountRequest.getApplication());
                            destAccountRequest.setInstance(accountRequest.getInstance());
                            destAccountRequest.setNativeIdentity(accountRequest.getNativeIdentity());
                            filtered.add(destAccountRequest);
                        }
                        destAccountRequest.add(attributeRequest);
                    }
                } catch (Throwable ex) {
                    log.error(ex);
                }
            }
        }
    }

    
    /**
     * Merge a plan returned by filterPlan() back into the
     * original plan that now contains Connector results.
     */
    private void unfilterPlan(ProvisioningPlan plan, ProvisioningPlan filtered) {

        if (filtered != null) {
            List<ObjectRequest> olist = filtered.getObjectRequests();
            if (olist != null) {
                for (ObjectRequest oreq : olist) {
                    if ((oreq.getType() != null && !oreq.isGroupRequest()) ||
                            Util.otob(oreq.getArgument(ObjectRequest.ATT_OBJ_REQ_NO_PROVISIONING))) {
                        // the entire request was filtered
                        plan.addRequest(oreq);
                    }
                    else {
                        AbstractRequest dest = plan.getMatchingRequest(oreq, true);
                        if (dest == null) {
                            // shouldn't happen, take the whole thing to be safe
                            log.error("Unable to find matching request during unfiltering");
                            plan.addRequest(oreq);
                        }
                        else {
                            List<AttributeRequest> atts = oreq.getAttributeRequests();
                            if (atts != null) {
                                for (AttributeRequest att : atts) 
                                    dest.add(att);
                            }
                        }
                    }
                }
            }
        }

        try {
            //System.out.println("Plan after unfiltering:");
            //System.out.println(plan.toXml());
        } catch (Throwable t) {}
    }

    /**
     * Merge back the attributes which were filterd by @see #filterAttributes()
     * 
     */
    public void unfilterAttributes(ProvisioningPlan plan, ProvisioningPlan filtered) {

        if (filtered == null) {
            return;
        }

        unfilterAccountRequestAttributes(plan, filtered);
        
        unfilterGroupRequestAttributes(plan, filtered);
    }

    /**
     * 
     * Merge back the account attributes which were filtered.
     * @see #filterAccountRequestAttributes(ProvisioningPlan, ProvisioningPlan)
     * 
     */
    private void unfilterAccountRequestAttributes(ProvisioningPlan plan, ProvisioningPlan filtered) {

        if (Util.isEmpty(filtered.getAccountRequests())) {
            return;
        }

        Iterator<AccountRequest> iterator = filtered.getAccountRequests().iterator();
        while (iterator.hasNext()) {
            AccountRequest accountRequest = iterator.next();
            AccountRequest sourceAccountRequest = plan.getAccountRequest(accountRequest.getApplication(), accountRequest.getInstance(), accountRequest.getNativeIdentity());
            if (sourceAccountRequest == null) {
                sourceAccountRequest = new AccountRequest();
                sourceAccountRequest.setApplication(accountRequest.getApplication());
                sourceAccountRequest.setInstance(accountRequest.getInstance());
                sourceAccountRequest.setNativeIdentity(accountRequest.getNativeIdentity());
                plan.add(sourceAccountRequest);
            }
            sourceAccountRequest.addAll(accountRequest.getAttributeRequests());
        }
    }

    /**
     * 
     * Merge back the group attributes which were filtered.
     * @see #filterGroupRequestAttributes(ProvisioningPlan, ProvisioningPlan)
     * 
     */
    private void unfilterGroupRequestAttributes(ProvisioningPlan plan, ProvisioningPlan filtered) {

        if (Util.isEmpty(filtered.getObjectRequests())) {
            return;
        }

        Iterator<ObjectRequest> iterator = filtered.getObjectRequests().iterator();
        while (iterator.hasNext()) {
            ObjectRequest objectRequest = iterator.next();
            ObjectRequest sourceObjectRequest = plan.getObjectRequest(objectRequest.getApplication(), objectRequest.getInstance(), objectRequest.getNativeIdentity());
            if (sourceObjectRequest == null) {
                sourceObjectRequest = new ObjectRequest();
                sourceObjectRequest.setApplication(objectRequest.getApplication());
                sourceObjectRequest.setInstance(objectRequest.getInstance());
                sourceObjectRequest.setNativeIdentity(objectRequest.getNativeIdentity());
                plan.addRequest(sourceObjectRequest);
            }
            sourceObjectRequest.addAll(objectRequest.getAttributeRequests());
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Side Effects
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Audit the provisioning results and any expansion information.
     */
    private void audit(IntegrationConfig config, ProvisioningPlan plan)
        throws GeneralException {

        // Audit the result of the provisioning request.
        auditResults(config, plan);
        
        // Audit each expansion for the items in this plan.
        auditExpansions(plan);
    }
    
    /**
     * Audit the result of a provisioning request.
     * Results are stored in the plan, usually there will be only one
     * top-level result for the entire plan, occasionally each AccountRequest
     * may have a granular plan.
     *
     * Historically we have only audited the top-level plan.  Now that we
     * will audit granular plans we need to think about what we audit
     * if some account requests have results and some don't.  For now
     * recommend that the connector not mix and match result styles.
     */
    private void auditResults(IntegrationConfig config, 
                              ProvisioningPlan plan)
        throws GeneralException {

        // we're going to build our own event so have to check
        // enabling ourselves too
        if (Auditor.isEnabled(AuditEvent.ActionProvision)) {

            // iiqpb-802 start with a clean cache
            // normally I would decache here but that causes failures
            // in IIQEvaluator which expects the Identity to still be in the session
            // could reattach, but I'm worried about other callers that may have
            // session expectations, use a private context instead
            SailPointContext saveContext = SailPointFactory.pushContext();
            _context = SailPointFactory.getCurrentContext();
            try {
                ProvisioningResult result = plan.getResult();
                if (result != null)
                    auditResult(config, result, null);

                List<AbstractRequest> requests = plan.getAllRequests();
                if (requests != null) {
                    for (AbstractRequest req : requests) {
                        ProvisioningResult childResult = req.getResult();
                        if (childResult != null) {
                            if (result == null || (!Util.nullSafeEq(childResult.getStatus(), result.getStatus()) ||
                                    !Util.nullSafeEq(childResult.getObject(), result.getObject())))
                            auditResult(config, childResult, req);
                        }
                        // Have to audit errors in AttributeRequests,
                        // especially for PE2 which handles group memberships
                        // as distinct transactions.
                        // !! How should this look, is it okay to have one
                        // sucessful message for the AccountRequest and another
                        // failure for an AttributeRequest...combining these
                        // will be ugly in the logs
                        auditResults(config, req.getAttributeRequests(), req);
                        auditResults(config, req.getPermissionRequests(), req);
                    }
                }
            }
            finally {
                SailPointFactory.popContext(saveContext);
                _context = saveContext;
            }
        }
    }

    /**
     * When doing items only audit failures.
     * Success will be handled by the AccountRequest or top-level plan.
     */
    private <T extends GenericRequest> void auditResults(IntegrationConfig config, 
                                                         List<T> items,
                                                         AbstractRequest req) {
        if (items != null) {
            for (T item : items) {
                ProvisioningResult result = item.getResult();
                if (result != null && result.isFailed())
                    auditResult(config, result, req);
            }
        }
    }

    /**
     * Audit one ProvisioningResult from a plan.
     */
    private void auditResult(IntegrationConfig config, 
                             ProvisioningResult result,
                             AbstractRequest req) {

        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.ActionProvision);

        // target could either be the identity or the idm system,
        // since we're doing something an identity that seems
        // the best target
        String target;
        if (_identity != null)
            target = _identity.getName();
        else {
            // what here?  if the plan contains only one ObjectRequest
            // could fish out the group name
            target = "group";
        }

        event.setTarget(target);

        // source=who did it
        String requester = getRequester();
        event.setSource(requester);

        event.setString1(config.getName());

        // some events use different actions for each result,
        // in retrospect I'm not liking that so much
        // here we'll use one of the strings to convey a result

        String string2 = "success";
        String string3 = null;
        String string4 = null;

        if (result == null) {
            if (log.isInfoEnabled())
                log.info("Provisioning successful: " + 
                         target + 
                         " on " + config.getName());
        }
        else if (result != null) {
            if (result.isFailed()) {
                log.error("Provisioning failure: " + 
                          target + 
                          " on " + config.getName());
                _provisioningErrors++;
            }
            else {
                if (log.isInfoEnabled())
                    log.info("Provisioning " +
                             result.getStatus() +
                             " successful: " + 
                             target + 
                             " on " + config.getName());
            }

            // any translation to do on these!!?
            string2 = result.getStatus();

            // only success and warning have a request id?
            string4 = result.getRequestID();

            // what about warnings and errors, try to 
            // cram them into a column or make different events?
            // in the unlikely case where we have both, assume 
            // they will distinguish themselves

            List<Message> warnings = result.getWarnings();
            List<Message> errors = result.getErrors();
            if (warnings != null || errors != null) {
                StringBuilder sb = new StringBuilder();
                if (warnings != null) {
                    for (Message m : warnings) {
                        String msg = m.getLocalizedMessage();
                        log.warn(msg);
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append(msg);
                    }
                }
                if (errors != null) {
                    for (Message m : errors) {
                        String msg = m.getLocalizedMessage();
                        log.error(msg);
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append(msg);
                    }
                }
                
                string3 = sb.toString();
            }
        }

        event.setString2(string2);
        event.setString3(string3);
        event.setString4(string4);

        //bug#15189 -- add more detail in provision audit
        event.setApplication(config.getName());
        if (req != null) {
            event.setAccountName(req.getNativeIdentity());
            event.setAttribute("op", req.getOp());
        }
        if (result != null && result.getObject() != null 
                && result.getObject().getAttributes() != null) 
        {
            for (Map.Entry<String, Object> attr: result.getObject().getAttributes().entrySet()) {
                if (attr != null) {
                    event.setAttribute(attr.getKey(), attr.getValue());
                }
            }
        }

        try {
            Auditor.log(event);
            _context.commitTransaction();
            // iiqpb-802 keep the cache clean as we go in case we have a large number
            // of these to commit
            _context.decache();
        }
        catch (Throwable t) {
            log.error("Unable to audit provisioning result");
            log.error(t);
        }
    }

    /**
     * Audit any expansions in the project that were successfully provisioned.
     */
    private void auditExpansions(ProvisioningPlan plan)
        throws GeneralException {

        if (Auditor.isEnabled(AuditEvent.ActionExpansion)) {
            if (!Util.isEmpty(plan.getAccountRequests())) {
                // iiqpb-802 start with a clean cache
                // normally I would decache here but that causes failures
                // in IIQEvaluator which expects the Identity to still be in the session
                // could reattach, but I'm worried about other callers that may have
                // session expectations, use a private context instead
                SailPointContext saveContext = SailPointFactory.pushContext();
                _context = SailPointFactory.getCurrentContext();
                try {
                    
                    String planStatus = getStatus(plan.getResult(), null);
                    for (AccountRequest acctReq : plan.getAccountRequests()) {
                        String acctStatus = getStatus(acctReq.getResult(), planStatus);
                        auditExpansions(acctReq, acctStatus);
                    }
                }
                finally {
                    SailPointFactory.popContext(saveContext);
                    _context = saveContext;
                }
            }
        }
    }
    
    /**
     * Audit any expansions in this request that were successfully provisioned.
     */
    @SuppressWarnings("unchecked")
    private void auditExpansions(AccountRequest acctReq, String status)
        throws GeneralException {

        if (!Util.isEmpty(acctReq.getAttributeRequests())) {
            for (AttributeRequest attrReq : acctReq.getAttributeRequests()) {
                status = getStatus(attrReq.getResult(), status);
                if (shouldAuditExpansion(status)) {
                    List<Object> vals = Util.asList(attrReq.getValue());
                    if (null != vals) {
                        for (Object val : vals) {
                            ExpansionItem expansion =
                                _project.getExpansionItem(acctReq, attrReq, val);
                            if (null != expansion) {
                                auditExpansion(expansion);
                            }
                        }
                    }
                }
            }
        }

        if (!Util.isEmpty(acctReq.getPermissionRequests())) {
            for (PermissionRequest permReq : acctReq.getPermissionRequests()) {
                status = getStatus(permReq.getResult(), status);
                if (shouldAuditExpansion(status)) {
                    List<String> rights = permReq.getRightsList();
                    if (null != rights) {
                        for (String right : rights) {
                            ExpansionItem expansion =
                                _project.getExpansionItem(acctReq, permReq, right);
                            if (null != expansion) {
                                auditExpansion(expansion);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Audit the given expansion.
     */
    private void auditExpansion(ExpansionItem expansion)
        throws GeneralException {

        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.ActionExpansion);

        // Target is the identity.
        event.setTarget((null != _identity) ? _identity.getName() : null);

        // Source is the requester.
        event.setSource(getRequester());

        // Set info about what is being provisioned.
        event.setApplication(expansion.getApplication());
        event.setInstance(expansion.getInstance());
        event.setAccountName(expansion.getNativeIdentity());
        event.setAttributeName(expansion.getName());
        if (null != expansion.getValue()) {
            event.setAttributeValue(expansion.getValue().toString());
        }

        // string1 = cause
        // string2 = source info
        // string3 = operation
        event.setString1(expansion.getCause().name());
        event.setString2(expansion.getSourceInfo());
        if (null != expansion.getOperation()) {
            event.setString3(expansion.getOperation().name());
        }
        
        Auditor.log(event);
        _context.commitTransaction();
        // iiqpb-802 keep the cache clean as we go in case we have a large number
        // of these to commit
        _context.decache();
    }
    
    /**
     * Return either the status of the given provisioning result or the default
     * status.
     */
    private static String getStatus(ProvisioningResult result, String defaultStatus) {
        String status = (null != result) ? result.getStatus() : null;
        return (null != status) ? status : defaultStatus;
    }
    
    /**
     * Return whether we should audit an expansion for the given provisioning
     * result status.
     */
    private static boolean shouldAuditExpansion(String status) {
        // Assume that null means committed.  We will audit committed or queued
        // since we don't have a good way of getting called back once the queued
        // item is complete.  Retries may get audited later when retried.
        return (null == status) ||
               ProvisioningResult.STATUS_COMMITTED.equals(status) ||
               ProvisioningResult.STATUS_QUEUED.equals(status);
    }
    
    /**
     * After sucessfully sending the plan, save it in a pending
     * request object that can be factored into later plans compiled
     * for this account.
     *
     * TODO: We may want to remove AccountRequests that failed.
     * For now leave them in and filter them in PlanCompiler.
     * It might be useful to see the failed plans for diagnostics.
     *
     */
    private void saveProvisioningRequest(IntegrationConfig config,
                                         ProvisioningPlan plan)
        throws GeneralException {

        // we absolutely do not want to do this if simulation is turned
        // on since this will commit transactions!
        if (_project.getBoolean(ARG_SIMULATE))
            return;

        // bug#13351 do not save requests if the connector committed
        // kludge: in theory we should be walking the plan pulling out
        // things that were queued since you can have a combination, 
        // we're only looking at the AccountRequest since that's
        // all we'll have in practice with PE2.  I am REALLY
        // not liking inheritance of results
        boolean planQueued = false;
        ProvisioningResult res = plan.getResult();
        if (res == null || res.getStatus() == null || res.isQueued())
            planQueued = true;

        // if all the accounts say yes, then ignore the plan status
        int queuedAccounts = 0;
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest req : accounts) {
                res = req.getResult();
                if (res != null && res.isQueued())
                    queuedAccounts++;
                else if (res == null || res.getStatus() == null) {
                    // not explicitly marked, inherit from the plan
                    if (planQueued) queuedAccounts++;
                }
            }
        }
        
        // jsl - this is too tied up with Identity so avoid this
        // for group requests for now.  In theory we still might need
        // something that tracks group requests though...
        if (_identity != null &&
            queuedAccounts > 0 &&
            !config.getBoolean(IntegrationConfig.ATT_NO_PROVISIONING_REQUESTS)) {

            ProvisioningRequest pr = new ProvisioningRequest();
            pr.setIdentity(_identity);
            pr.setTarget(plan.getTargetIntegration());
            pr.setPlan(plan);

            // !! I don't think this will be set here, didn't we convert
            // this to a plan attribute?
            List<Identity> requesters = plan.getRequesters();
            if (requesters != null && requesters.size() > 0)
                pr.setRequester(requesters.get(0).getName());


            // old convention, in the IntegrationConfig
            int days = config.getInt(IntegrationConfig.ATT_PROVISIONING_REQUEST_EXPIRATION);

            // new convention, in the system config
            if (days == 0) {
                Configuration syscon = _context.getConfiguration();
                days = syscon.getInt(Configuration.PROVISIONING_REQUEST_EXPIRATION_DAYS);
            }

            // we need at least something, letting these not expire
            // is a bad idea
            if (days == 0) days = 7;

            // oh my how I hate Calendar
            Date now = DateUtil.getCurrentDate();
            Date expiration = null;
            if (days > 0) 
                expiration = new Date(now.getTime() + (days * Util.MILLI_IN_DAY));
            else if (days < 0) {
                // usual services hack for seconds
                expiration = new Date(now.getTime() + (days * 1000));
            }
            pr.setExpiration(expiration);

            _context.saveObject(pr);
            _context.commitTransaction();
        }
    }

    /**
     * Update password history on links if any of the requests 
     * are password updates.
     * 
     * @param plan
     *
     * @ignore
     * jsl - this is locking the identity which I'd rather not do here,
     * try to push locking up a level to the caller or maybe defer
     * this until IIQEvalator runs which also has to deal with locking.
     *
     */
    private void updatePasswordHistory(ProvisioningPlan plan) 
        throws GeneralException {
            
        // we absolutely do not want to do this if simulation is turned
        // on since this will commit transactions!
        if (_project.getBoolean(ARG_SIMULATE))
            return;

        // Build a list of AccountRequests containing password changes
        List<AccountRequest> targets = new ArrayList<AccountRequest>();
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                ObjectOperation aop = account.getOp();
                if (aop == ObjectOperation.Modify) {
                    // search for one with this special name
                    AttributeRequest att = account.getAttributeRequest(ProvisioningPlan.ATT_PASSWORD);
                    if (att != null)
                        targets.add(account);
                }
            }
        }

        // only lock the identity if we know we have passwords to update
        if (targets.size() > 0) {

            Identity locked = ObjectUtil.lockIdentity(_context, _identity);
            if (locked == null) {
                // deleted out from under us!
                log.error("Identity evaporated: " + _identity.getName());
            }
            else {
                try {
                    _identity = locked;
                    accounts = plan.getAccountRequests();
                    if (accounts != null) {
                        for (AccountRequest req : accounts) {
                            ObjectOperation aop = req.getOp();
                            if (aop == ObjectOperation.Modify) {

                                AttributeRequest attReq = req.getAttributeRequest(ProvisioningPlan.ATT_PASSWORD);
                            
                                if (attReq != null) {                                    
                                    @SuppressWarnings("unused")
                                    Application app = req.getApplication(_context);
                                    Link link = getLinkForRequest(req, true);
                                    String passValEncrypted = (String) attReq.getValue();
                                    // our plan should have its secrets encrypted, including this password value
                                    // so, fix that
                                    String passVal = _context.decrypt(passValEncrypted);
                                    PasswordPolice police = new PasswordPolice(_context);
                                    police.addPasswordHistory(link, passVal);
                                    _context.saveObject(link);
                                }
                            }
                        }
                    }
                }
                finally {
                    // this will save and commit
                    ObjectUtil.unlockIdentity(_context, locked);
                }
            }
        }
    }

    /**
     * Get the Link corresponding to an AccountRequest.
     * Optionally create one if it does not exist.
     */
    @SuppressWarnings("deprecation")
    private Link getLinkForRequest(AccountRequest req, boolean create) 
        throws GeneralException {

        Link link = null;
        Application app = req.getApplication(_context);
        if (app != null && _identity != null) {
            link = _identity.getLink(app, req.getInstance(), req.getNativeIdentity());
            if (create && link == null)
                link = createLink(req, app);
        }

        return link;
    }

    // duplicated from IIQEvaluator, need to move this over there!!!!
    private Link createLink(AccountRequest account, Application app) {

        Link link = new Link();

        link.setApplication(app);
        link.setInstance(account.getInstance());
        link.setNativeIdentity(account.getNativeIdentity());
        _identity.add(link);

        return link;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Encryption
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Walk over a plan looking for AttributeRequests for sensitive
     * attributes like passwords and encrypt the values.  This should
     * be used by anything that launches a workflow and pssses in a plan
     * to ensure that the passwords are encrypted so they won't appear
     * in workflow trace.  This transformation will be undone immediately
     * before passing the plan to the Connector.
     */
    public void encryptSecrets(ProvisioningPlan plan) 
        throws GeneralException {
        changeSecrets(plan, true);
    }

    /**
     * Do the reverse of encryptSecrets above.
     */
    public void decryptSecrets(ProvisioningPlan plan) 
        throws GeneralException {
        changeSecrets(plan, false);
    }

    private void changeSecrets(ProvisioningPlan plan, boolean encrypt) 
        throws GeneralException {

        List<AccountRequest> accreqs = plan.getAccountRequests();
        if (accreqs != null) {
            for (AccountRequest accreq : accreqs) {
                List<AttributeRequest> attreqs = accreq.getAttributeRequests();
                if (attreqs != null) {
                    // We also need to check the AttributeRequest args for
                    // secrets. IIQETN-5128
                    for (AttributeRequest attreq : attreqs) {
                        Attributes<String, Object> args = attreq.getArguments();
                        boolean modifiedArgs=false;
                        if (args != null) {
                            for (Entry<String, Object> arg : args.entrySet()) {
                                String argStr = arg.getKey();
                                if (ObjectUtil.isSecret(argStr)) {
                                    Object o = arg.getValue();
                                    if (o instanceof String) {
                                        String neu;
                                        if (encrypt)
                                            neu = _context.encrypt((String)o);
                                        else
                                            neu = _context.decrypt((String)o);
                                        args.put(argStr, neu);
                                        modifiedArgs=true;
                                    }
                                    else if (o != null) {
                                        log.error("Found secret attribute arg that wasn't a String:" + argStr);
                                    }
                              }
                          }
                          if (modifiedArgs) {
                              attreq.setArgs(args);
                          }
                        }
                        if (attreq.isSecret()) {
                            Object o = attreq.getValue();
                            if (o instanceof String) {
                                String neu;
                                if (encrypt)
                                    neu = _context.encrypt((String)o);
                                else
                                    neu = _context.decrypt((String)o);
                                attreq.setValue(neu);
                            }
                            else if (o != null) {
                                log.error("Found secret attribute that wasn't a String");
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Iterate through all the AccountRequests to add requested metadata by target application.
     * Check {@link PlanEvaluator#PROVISIONING_METADATA} in application and populate the requested 
     * info in the plan. 
     * 
     * Currently, it only adds information related to Link attributes. 
     * 
     * @param plan
     */
    @SuppressWarnings({ "deprecation", "unchecked" })
    private void addProvisioningMetadata(ProvisioningPlan plan) {
        if (null != plan) {
            for (AbstractRequest abstractRequest : Util.safeIterable(plan.getAccountRequests())) {
                try {
                    Map<String, Object> metaData = new HashMap<String, Object>();
                    Application application = abstractRequest.getApplication(_context);

                    if (null != application && !Util
                            .isEmpty((Map<String, Object>) application.getAttributeValue(PROVISIONING_METADATA))) {

                        Map<String, Object> provisioningMetaData = (Map<String, Object>) application
                                .getAttributeValue(PROVISIONING_METADATA);

                        // current use case only needs attributes available on Link to be populated
                        // on AccountRequest. Hence only processing linkAttributes key from provisioningMetaData.
                        if (provisioningMetaData.containsKey(LINK_ATTRIBUTES)
                                && !Util.isEmpty((List<String>) provisioningMetaData.get(LINK_ATTRIBUTES))) {
                            Link link = _identity.getLink(application, abstractRequest.getInstance(),
                                    abstractRequest.getNativeIdentity());
                            if (null != link) {
                                List<String> attributeList = Util.asList(provisioningMetaData.get(LINK_ATTRIBUTES));
                                Map<String, Object> linkAttributesMap = new HashMap<String, Object>();

                                for (String attribute : Util.safeIterable(attributeList)) {
                                    linkAttributesMap.put(attribute, link.getAttribute(attribute));
                                }
                                metaData.put(LINK_ATTRIBUTES, linkAttributesMap);
                            }
						}
                        /*
                         * Check if the application template contains the key "ENTITLEMENT_ATTRIBUTES".
                         * If this key is present, then, it means some extra data should be appended to
                         * all the AttributeRequests related to entitlement provisioning. These types of
                         * requests should be scanned.  
                         * 
                         * The extra data that needs to be appended is present in the "ENTITLEMENT_ATTRIBUTES"
                         * list. All the fields mentioned in the list should be fetched from the db
                         * for that request and appended. 
                         */
                        if (provisioningMetaData
                                .containsKey(ENTITLEMENT_ATTRIBUTES)) {

                            /*
                             * Get the "ENTITLEMENT_ATTRIBUTES" list. These fields should be figured out for the
                             * request. Ex: If an AD application has provisioning meta data as follows:
                             * 
                             * <entry key="provisioningMetaData">
                             *   <value> 
                             *      <Map> 
                             *          <entry key="linkAttributes">....</entry>
                             *          <entry key="entitlementAttributes> 
                             *              <value> 
                             *                  <List> 
                             *                      <String>distinguishedName</String>
                             *                  </List> 
                             *              </value> 
                             *          </entry> 
                             *      </Map> 
                             *   </value> 
                             * </entry>
                             * 
                             * then, for each entitlement AttributeRequest, distinguishedName is the extra information
                             * that needs to be appended. The list can have more fields as well.
                             */
                            List<String> entitlementAttributesList = Util
                                    .asList(provisioningMetaData.get(ENTITLEMENT_ATTRIBUTES));
                            Map<String, Object> entitlementValueMap = new HashMap<>(); // This
                            // map would be set to the parent AccountRequest in "metaData" as
                            // ENTITLEMENT_ATTRIBUTES along with LINK_ATTRIBUTES (if present).
                            // entitlementValueMap will have all the extra data that we want 
                            // send to the connector for entitlement attribute requests.
                            
                            // Get the entitlement attributes for the given application. These are the type
                            // for requests that would be scanned.
                            // For ex: In AD, application.getEntitlementAttributeNames() will return
                            // "memberOf".

                            if (!Util.isEmpty(entitlementAttributesList)) {
                                List<String> appEntitlementAttr = application.getEntitlementAttributeNames();
                                if (!Util.isEmpty(appEntitlementAttr)) {
                                    for (String appEntitlementAttribute : Util.safeIterable(appEntitlementAttr)) {
                                        Map<String, Object> appEntitlementMap = new HashMap<>();
                                        // Get all the AttributeRequests of the type: entitlementAttribute
                                        List<AttributeRequest> entitlementRequests = abstractRequest
                                                .getAttributeRequests(appEntitlementAttribute);
                                        List<String> stringValueList = new ArrayList<>();
                                        // Loop through all the requests, and gather all the information
                                        // at once that needs to be fetched from the db. stringValueList
                                        // has all the "value" that needs to be matched in the ManagedAttribute
                                        // table.

                                        // Note: What if comma separated values are present? As of now we know these
                                        // types
                                        // if any other way is present, we will handle it on need-basis.
                                        for (AttributeRequest entitlementRequest : Util
                                                .safeIterable(entitlementRequests)) {
                                            Object valueObject = entitlementRequest.getValue();
                                            if (null != valueObject) {
                                                if (valueObject instanceof String) {
                                                    stringValueList.add(Util.otoa(valueObject));
                                                } else if (valueObject instanceof List) {
                                                    stringValueList.addAll(Util.asList(valueObject));
                                                }
                                            }
                                        }
                                        if (!Util.isEmpty(stringValueList)) {
                                            QueryOptions qop = new QueryOptions();
                                            Filter filter = Filter.eq("application", application);
                                            List<ManagedAttribute> managedAttributesList = new ArrayList<>();
                                            filter = Filter.in("value", stringValueList);
                                            qop.add(filter);
                                            // managedAttributesList has all the data
                                            managedAttributesList = _context.getObjects(ManagedAttribute.class, qop);
                                            if (!Util.isEmpty(managedAttributesList)) {
                                                for (ManagedAttribute ma : Util.safeIterable(managedAttributesList)) {
                                                    Map<String, Object> valuesMap = new HashMap<>();
                                                    for (String attr : Util.safeIterable(entitlementAttributesList)) {
                                                        if (null != ma.getAttribute(attr)) {
                                                            valuesMap.put(attr, ma.getAttribute(attr));
                                                        }
                                                    }
                                                    appEntitlementMap.put(ma.getValue(), valuesMap);
                                                }
                                                if (!Util.isEmpty(appEntitlementMap)) {
                                                    entitlementValueMap.put(appEntitlementAttribute, appEntitlementMap);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if(!Util.isEmpty(entitlementValueMap)) {
                                metaData.put(ENTITLEMENT_ATTRIBUTES, entitlementValueMap);
                            }
                        }
                    }
                    if(!Util.isEmpty(metaData)) {
                        abstractRequest.put(PROVISIONING_METADATA, metaData);
                        log.debug("Additional information added in AccountRequest "+ metaData.toString());
                        /*
                         * Resulting structure of the Metadata Map is: 
                         * >>> For each Account Request in the plan
                         * MetaData
                         *  |___LinkAttributes
                         *      ....
                         *      ....
                         *  |___EntitlementAttributes
                         *      |___memberOf(this is app entitlement attribute)
                         *          |___{objectGUID-1} --> respective DN1
                         *          |___{objectGUID-2} --> respective DN2         
                         */
                    }
                } catch (Exception ex) {
                    log.warn("Unable to check or add requested metadata " + ex.getMessage());
                }
            }
        }
    }
}
