/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used maintain a complex provisioning request involving
 * several applications.  A provisioning plan may be implemented
 * in a variety of ways: trouble tickets, work items, and actual provisioning.
 * Try to keep this model generic, don't put implementation details here.
 *
 * Author: Jeff, Kelly
 *
 * This is conceptually similar to an SPML BatchRequest containing
 * AddRequests, ModifyRequests, and DeleteRequets.  We're going to
 * simplify that and bias our model toward the ModifyRequest since that's
 * what we do most of the time.
 *
 * NOTE: There is a simplified version of this class over in the
 * sailpoint.integration package.  The Map representation generated here
 * must match the one used in the other class so we can pass plans
 * from IIQ to a provisioning system using our integration toolkit.
 * We should try to merge the models, maybe by subclassing the other
 * ProvisioningPlan here.  See header comments in the other class
 * for more details.
 *
 * ACCOUNT REQUEST VS OBJECT REQUEST
 *
 * Originally there was only AccountRequest.  ObjectRequest was added later
 * to represent plans created for role certifications, Prior to 6.0 these
 * were relatively simple the implied target application was IIQ and they
 * contined various properties to represent the target object in the IIQ 
 * model though in practice they were only used for roles. 
 *
 * In 6.0 we added support for resource group management.  ObjectRequest
 * was redesigned so that it can be used to edit any resource object though
 * at the moment we only have infrastucture for group management.
 * This required that ObjectRequest look almost identical to AccountRequest.
 * After this refactoring the only real difference is that AccountRequest has
 * a List<PermissionRequest> and it's object type (account) is implied by the
 * class name.  In a prefect world we would simply stop using AccountRequest
 * and use ObjectRequest for everything, but there are too many customizations
 * that rely on the old model.
 *
 * AccountRequest was redesigned to extend ObjectRequest so we can share
 * most things.
 *
 * SUNRISE/SUNSET
 *
 * Starting in 5.0 the plan is also used to request a role assignment
 * with sunrise and sunset dates.  This could be done in several ways
 * none of which are perfect.
 *
 * We started thinking about a command pattern, where a list of AccountAction
 * objects could be stored on the AccountRequest and trigger arbitrary
 * things by name: sunriseRole, sunsetRole, resetPassword, etc.
 * There are some interesting things that could be done with that but
 * for role and entitlement assignments we decided it would be better
 * to do this with "parameters" on the AttributeRequest. That way there
 * is a clearer association between the special operation and the
 * attribute it applies to which is good when analyzing the plan
 * for approvals.
 *
 * So insead of just:
 *
 *     op=add, name=roles, value=foo
 *
 * you can parameterize this with:
 *
 *     op=add, name=roles, value=foo
 *         sunrise=x, sunset=y
 *
 * This isn't perfect though, we have to work through the semantics
 * for using sunrise/sunset parameters in both Add and Remove requets,
 * and be clear about what happens if there is already a deferred
 * assignment present represented with a RoleAssignment object.  Once
 * the deferred assignment has been made editing the previous sunrise
 * sunset dates are troublesome in the plan model since we don't have a 
 * way to directly address fields of sub-objects.  Using a path-like
 * syntax would be powerful and close to Sun "view" paths but are
 * hard to explain:
 *
 *     op=set name=roles[foo].sunrise value=x
 *
 * I suspect we'll get to paths eventually if we start allowing
 * the direct modification of some of the more complex objects
 * hanging off the identity (scorecard, certification history, etc.)
 * but for now we'll do this with parameterized operations on the "roles"
 * attribute.
 *
 * op=Add
 *    Immediate add, cancels previous sunrise and sunset
 *
 * op=Add, sunrise=x
 *    Deferred add, changes previous sunrise, cancels previous sunset
 *    If role already added, just cancels sunset
 *
 * op=Add, sunrise=x, sunset=y
 *    Deferred add, changes previous sunrise, changes previous sunset
 *    If role already added, changes sunset
 *
 * op=Add, sunset=y
 *    Immediate add, cancels previous sunrise, changes previous sunset
 *
 * op=Remove
 *    Immediate remove, cancels previous sunrise and sunset
 *
 * op=Remove, sunset=y
 *    Deferred remove, changes previous sunset, does NOT cancel sunrise
 *    This is inconsistent with op=Add which cancels both if not specified,
 *    but it seems reasonable for Remove since that implies a more focused
 *    operation.  Add can both sunrise and sunset, but remove can only sunset.
 *
 * op=Remove, sunrise=x
 *    Immediate remove, sunrise is ignored because it is meaningless.
 * 
 * op=Remove, sunrise=x, sunset=y
 *    I think sunrise should be ignored.  We could have this work like
 *    op=Add but thats inconsistent with the notion presented above
 *    under "op=Remove, sunset=y" that says op=Remove should just ignore
 *    the sunrise date and only adjust the sunset date.
 *
 * I also considered having just an "effectiveDate" and doing sunrise
 * and sunset as individual requests:
 *
 *    op=Add name=roles, value=foo
 *       effectiveDate=x
 *
 *    op=Remove, name=roles, value=foo
 *       effectiveDate=y
 *
 * That has some nice properties, and looks simpler on the surface, but
 * it introducts a funny kind of "transaction" concept into the plan 
 * where Add and Remove operations on the same attribute don't cancel
 * each other out, they are effectively merged.  This makes analyzig
 * the plan for approvals harder (am I adding or removing?) 
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Class used maintain a complex provisioning request involving
 * several applications.  
 * 
 * @ignore
 * NOTE: There is a simplified version of this class in the
 * <code>sailpoint.integration</code> package.  The Map representation 
 * generated here must match the one used in the other class so we can 
 * pass plans from IIQ to a provisioning system using our integration toolkit.
 */
@XMLClass
public class ProvisioningPlan extends AbstractXmlObject {

    private static final Log log = LogFactory.getLog(ProvisioningPlan.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Plan Arguments
    //
    // These may be set in the top-level attributes map of the plan
    // to convey extra information to the plan compiler.  It may also
    // contain random values to be passed to the IntegrationExecutors.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of a plan argument holding the name of the identity
     * that is considered to be the requester of the provisioning.
     * This is intended as an replacement for the _requesters property
     * that is easier to pass through the machinery in maps.
     * @ignore
     * !! Can _requesters ever have more than one thing on it?
     * I suppose we could allow this to be a CSV or List<String>
     */
    public static final String ARG_REQUESTER = "requester";

    /**
     * Name of a plan argument that contains the "source" type.
     * The value should be one of the sailpoint.object.Source enumerations
     * but it is allowed to be a custom source.
     */
    public static final String ARG_SOURCE = "source";

    /**
     * Name of a plan argument that contains the database id of
     * an object associated with the source. This is used only
     * for Certification and PolicyViolation sources.
     */
    public static final String ARG_SOURCE_ID = "sourceId";

    /**
     * Name of a plan argument that contains the database name of
     * an object associated with the source. This is used only
     * for Certification and PolicyViolation sources.
     */
    public static final String ARG_SOURCE_NAME = "sourceName";

    /**
     * Name of a plan argument that contains the timeout to be used
     * when acquiring locks on the target Identity. Also used
     * when locking the source or destination identity when moving
     * links. This is intended for use in plans that are being
     * synchronously executed in a UI thread so you do not have to wait
     * the default 1 minute for a lock timeout.
     */
    public static final String ARG_LOCK_TIMEOUT = "lockTimeout";

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Request Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of a special AccountRequest application that represents
     * the IdentityIQ identity.  
     */
    public static final String APP_IIQ = "IIQ";

    /**
     * This is the name of the application "IIQ" that goes out in email.
     * @ignore
     * TODO: Maybe we should move it to some other class like 'Consts' or Configuration
     */
    public static final String IIQ_APPLICATION_NAME = "IdentityIQ";

    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the assigned role list.
     */
    public static final String ATT_IIQ_ASSIGNED_ROLES = "assignedRoles";

    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the detected role list.
     */
    public static final String ATT_IIQ_DETECTED_ROLES = "detectedRoles";

    /**
     * The name of special AttributeRequest within the IdentityIQ
     * application to move or delete links
     */
    public static final String ATT_IIQ_LINKS = "links";
    
    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the workgroup list.
     */
    public static final String ATT_IIQ_WORKGROUPS = "workgroups";

    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the needsRefresh flag.
     */
    public static final String ATT_IIQ_NEEDS_REFRESH = "needsRefresh";
    
    /**
     * Special attribute name used in AttributeRequests to 
     * set an Identity or account's password. 
     */
    public static final String ATT_PASSWORD =
        sailpoint.integration.ProvisioningPlan.ATT_PASSWORD;

    /**
     * Special attribute used in the arguments map of a password
     * AttributeRequest that holds the users current password. If not
     * specified, the password will be "reset" rather than "changed".
     */
    public static final String ATT_CURRENT_PASSWORD =
        sailpoint.integration.ProvisioningPlan.ATT_CURRENT_PASSWORD;

    /**
     * Special attribute used in the arguments map of a password
     * AttributeRequest that indicates that the new password should be
     * pre-expired (for example - the user has to change it after first login).
     */
    public static final String ATT_PRE_EXPIRE =
        sailpoint.integration.ProvisioningPlan.ATT_PRE_EXPIRE;

    /**
     * Used to notify email template 
     */
    public static final String ATT_GENERATED = "generatedPass";
    
    /**
     * Constant for the password attribute found in AttributeRequests
     * for IdentityIQ.
     * 
     * @deprecated use {@link #ATT_PASSWORD}
     */
    @Deprecated
    public static final String ATT_IIQ_PASSWORD = ATT_PASSWORD;    

    /**
     * Attribute holding the capabilities list.
     * Certification has one of these too.
     */
    public static final String ATT_IIQ_CAPABILITIES = 
        Certification.IIQ_ATTR_CAPABILITIES;

    /**
     * Preferred camel case version of the capabilities attribute. IIQEvaluator
     * will accept either ATT_IIQ_CAPABILITIES or this attribute, but the IdentityMap 
     * will be using this name.
     */
    public static final String ATT_IIQ_CAPABILITIES_NEW = "capabilities"; 

    /**
     * Attribute holding the controlled scopes.
     */
    public static final String ATT_IIQ_CONTROLLED_SCOPES = 
        Certification.IIQ_ATTR_SCOPES;
    
    /**
     * Preferred camel case version of the authorizedScopes attribute. IIQEvaluator
     * will accept either ATT_IIQ_CONTROLLED_SCOPES or this attribute, but the 
     * IdentityMap will be using this name.
     */
    public static final String ATT_IIQ_CONTROLLED_SCOPES_NEW = 
        "controlledScopes";
    
    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the authorized scopes list.
     */
    public static final String ATT_IIQ_AUTHORIZED_SCOPES = 
        Certification.IIQ_ATTR_SCOPES;

    /**
     * Attribute holding the assigned scope.
     */
    public static final String ATT_IIQ_SCOPE = "scope";

    /**
     * Attribute name allowed on IdentityIQ plans that assign values for
     * Identity.assignedScope. Also allowed is ATT_SCOPE.
     */
   public static Object ATT_ASSIGNED_SCOPE = "assignedScope";

    /**
     * Attribute holding the flag indicating that the identity
     * also controls the assigned scope.
     */
    public static final String ATT_IIQ_CONTROLS_ASSIGNED_SCOPE = 
        "controlsAssignedScope";

    /**
     * The name of a special AttributeRequest within the IdentityIQ
     * application to modify the ActivityConfig. The value
     * is normally a String or List of application ids.  
     * If the value is Boolean true/false it sets the "enableAll"
     * flag.
     */
    public static final String ATT_IIQ_ACTIVITY_CONFIG = "activityConfig";

    /**
     * A pseudo attribute representing the IdentityArchive list
     * which we display in the UI as "identity history".
     * You can only make Remove requests for this list, adding
     * IdentityArchive objects is only done as a side effect
     * of an identity refresh you cannot make one from a provisioning plan.
     * The value must be a id or list of ids of IdentityArchive objects.
     */
    public static final String ATT_IIQ_ARCHIVES = "archives";

    /**
     * An IdentityIQ pseudo attribute targeting the list of IdentitySnapshots.
     */
    public static final String ATT_IIQ_SNAPSHOTS = "snapshots";

    /**
     * A pseudo attribute representing Request objects associated
     * with this identity which the UI displays as "identity events".
     * You can only make Remove requests for this list, adding
     * Request objects is only done as a side effect of other things
     * like role sunrise/sunset. You cannot make one from a plan.
     * The value must be a id or list of ids of Request objects.
     * 
     * @ignore
     * !! I think we also show pending WorkflowCases as events too
     * in which case this may be a mixed list of Request and
     * WorkflowCase ids.  Might want to make these different
     * attributes but we don't really have to as long as we ref
     * them by id.
     */
    public static final String ATT_IIQ_EVENTS = "events";

    /**
     * An IdentityIQ pseudo attribute targeting the list of 
     * ProvisioningRequests.
     */
    public static final String ATT_IIQ_PROVISIONING_REQUESTS = "provisioningRequests";

    /**
     * The name of a special AccountRequest application that represents
     * the aggregate identity managed by a provisioning system.
     * This account may have additional attributes not represented
     * in resource accounts, notably a list of provisioning system
     * role assignments.
     * 
     * @ignore
     * TODO: This may not be necessary if we have a Link
     * in the identity for each IDM system account.  In that case
     * just use the application name for the IDM system.
     */
    public static final String APP_IDM =
        sailpoint.integration.ProvisioningPlan.APP_IDM;

    /**
     * The name of the attribute in the APP_IDM account that
     * represents the assigned roles. The value must
     * be a List<String>.
     */
    public static final String ATT_IDM_ROLES =
        sailpoint.integration.ProvisioningPlan.ATT_IDM_ROLES;

    //////////////////////////////////////////////////////////////////////
    //
    // AccountRequest Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When logically true, this AccountRequest argument indicates that the
     * user specifically requested to create an account.
     */
    public static final String ARG_FORCE_NEW_ACCOUNT = "forceNewAccount";


    //////////////////////////////////////////////////////////////////////
    //
    // AttributeRequest/PermissionRequest Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The date at which an add or set request is to occur,
     * the "sunrise" date.
     */
    public static final String ARG_ADD_DATE = "addDate";

    /**
     * The date at which a Remove request is to occur,
     * the "sunset" date.
     * This can be combined with addDate in a Set or Add request which
     * is why it needs a different name.
     */
    public static final String ARG_REMOVE_DATE = "removeDate";
    
    /** 
     * The request comments coming in with the request. Need to be shown
     * on the approval.
     */
    public static final String ARG_COMMENTS = "comments";

    /** 
     * The role assignment note coming in with the attribute request.  
     */
    public static final String ARG_ASSIGNMENT_NOTE = "assignmentNote";

    /**
     * When this is logically true, it means that the link attribute
     * was manually edited. It should be stored in the link similar
     * to the "optimistic provisioning" option, but it should NOT be
     * sent to a provisioning system.
     */
    public static final String ARG_LINK_EDIT = "linkEdit";

    /**
     * When used with ATT_PASSWORD, requests that policy checking
     * be performed before setting. 
     */
    public static final String ARG_CHECK_POLICY = "checkPolicy";
    
    /**
     * When used with ATT_PASSWORD, requests a password expiration value
     * according to {@link sailpoint.api.PasswordPolice.Expiry } 
     */
    public static final String ARG_PASSWORD_EXPIRY = "passwordExpiry";
    
    /**
     * When added to the arguments and set to true, will
     * indicate that the AttributeAssignment stored on the identity,
     * which makes it sticky, should also be created or removed. 
     * 
     * @see AttributeAssignment
     */
    public static final String ARG_ASSIGNMENT = "assignment";

    /**
     * When true indicates that the AttributeRequest value is secret.
     * 
     * @ignore
     * This is a semi-kludge for bug#15808 to keep us from including
     * values of secret fields in provisioning policies in the 
     * IdentityRequest object to address an urgent customer need.
     * We need to follow this with a more thorough encryption
     * strategy that we can use with secret fields so they remain
     * encrypted while in the workflow rather than just hidden.
     */
    public static final String ARG_SECRET = "secret";

    /**
     * Holds the name of the role which permits the role being requested
     * in a permitted role request.
     */
    public static final String ARG_PERMITTED_BY = "permittedBy";

    //////////////////////////////////////////////////////////////////////
    //
    // Object Request Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Request to remove a profile from a role in a role composition certification.
     */
    public static final String ATT_IIQ_ROLE_PROFILES = "profiles";

    /**
     * Request to remove a child role from a role in a role composition certification.
     */
    public static final String ATT_IIQ_ROLE_CHILD = "children";

    /**
     * Request to remove a required role from a role in a role composition certification.
     */
    public static final String ATT_IIQ_ROLE_REQUIREMENT = "requiredRole";

    /**
     * Request to remove a permitted role from a role in a role composition certification.
     */
    public static final String ATT_IIQ_ROLE_PERMIT = "permittedRole";

    /**
     * Request to remove a scope grant from a role.
     */
    public static final String ATT_IIQ_ROLE_GRANTED_SCOPE= "roleGrantedScope";

    /**
     * Request to remove a capability grant from a role.
     */
    public static final String ATT_IIQ_ROLE_GRANTED_CAPABILITY= "roleGrantedCapability";


    /**
     * Optional AttributeRequest argument used to convey
     * the previous values that were assigned.
     */
    public static final String ARG_PREVIOUS_VALUE = "previousValue";
    public static final String ARG_TYPE = "type";
    public static final String ARG_REQUIRED = "required";
    public static final String ARG_TYPE_DATE = "date";
    public static final String ARG_ALLOW_SIMPLIFICATION = "allowSimplification";
    
    /**
     * Argument for "links" request. To which identity the link
     * should be moved to.
     */
    public static final String ARG_DESTINATION_IDENTITY = "destinationIdentity";

    /**
     * Argument for "links" request. From which identity the link
     * should be moved.
     */
    public static final String ARG_SOURCE_IDENTITY = "sourceIdentity";

    /**
     * Argument used to specify that a Remove operation should set
     * the negative assignment flag to true.
     */
    public static final String ARG_NEGATIVE_ASSIGNMENT = "negativeAssignment";

    /**
     * A type name used in ObjectRequest to indicate that the request
     * will create or update an IdentityIQ ManagedAttribute object but will
     * not provisioning anything through a Connector. This is necessary
     * to distinguish between ObjectRequests of type "group" that
     * will provision groups, and ORs for other managed attributes.
     *
     * @ignore
     * Originally we allowed the type to be the name of the account
     * schema attribute, but that felt funny since it isn't a Schema
     * name and in theory we might want to have MAs for more than just
     * the account schema.  
     */
    public static final String OBJECT_TYPE_MANAGED_ATTRIBUTE = "ManagedAttribute";

    /**
     * A type name used in ObjectRequest to indicate that the request
     * will create or update a group. Use of this constant is not required,
     * any Schema name in the target Application will do, but this
     * is common and consistent with OBJECT_type_MANAGED_ATTRIBUTE.
     */
    public static final String OBJECT_TYPE_GROUP = Application.SCHEMA_GROUP;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Account Group Attributes
    //
    //////////////////////////////////////////////////////////////////////
    public static final String ACCOUNT_GROUP_NAME = "accountGroupName";
    public static final String ACCOUNT_GROUP_DESCRIPTION = "accountGroupDescription";
    public static final String ACCOUNT_GROUP_OWNER = "accountGroupOwner";
    public static final String ACCOUNT_GROUP_SCOPE = "accountGroupScope";
    public static final String ACCOUNT_GROUP_APPLICATION = "accountGroupApplication";
    public static final String ACCOUNT_GROUP_NATIVE_IDENTITY = "accountGroupNativeIdentity";
    public static final String ACCOUNT_GROUP_REFERENCE_ATTRIBUTE = "accountGroupReferenceAttribute";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Keys in the map model
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ATT_PLAN_IDENTITY =
        sailpoint.integration.ProvisioningPlan.ATT_PLAN_IDENTITY;
    public static final String ATT_PLAN_ACCOUNTS =
        sailpoint.integration.ProvisioningPlan.ATT_PLAN_ACCOUNTS;
    public static final String ATT_PLAN_OBJECTS =
        sailpoint.integration.ProvisioningPlan.ATT_PLAN_OBJECTS;
    public static final String ATT_PLAN_REQUESTERS = "requesters";
    public static final String ATT_PLAN_ARGUMENTS =
        sailpoint.integration.ProvisioningPlan.ATT_PLAN_ARGUMENTS;
    public static final String ATT_PLAN_INTEGRATION_DATA =
        sailpoint.integration.ProvisioningPlan.ATT_PLAN_INTEGRATION_DATA;
    public static final String ATT_PLAN_PROFILE_ORDINAL = "profileOrdinal";
    public static final String ATT_PLAN_PROFILE_DESCRIPTION = "profileDescription";
    public static final String ATT_PLAN_PROFILE_CONSTRAINTS = "profileConstraints";
    
    public static final String ATT_OP =
        sailpoint.integration.ProvisioningPlan.ATT_OP;

    public static final String ATT_OBJECT_APPLICATION =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_APPLICATION;
    public static final String ATT_OBJECT_INSTANCE =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_INSTANCE;
    public static final String ATT_OBJECT_TYPE = "type";
    // this used to be "nativeIdentity", continue that for backward
    // with the integration/common model.
    public static final String ATT_OBJECT_ID =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_IDENTITY;

    public static final String ATT_OBJECT_ATTRIBUTES =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_ATTRIBUTES;
    public static final String ATT_OBJECT_PERMISSIONS =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_PERMISSIONS;
    public static final String ATT_OBJECT_ARGUMENTS =
        sailpoint.integration.ProvisioningPlan.ATT_ACCOUNT_ARGUMENTS;

    public static final String ATT_ATTRIBUTE_NAME =
        sailpoint.integration.ProvisioningPlan.ATT_ATTRIBUTE_NAME;
    public static final String ATT_ATTRIBUTE_VALUE =
        sailpoint.integration.ProvisioningPlan.ATT_ATTRIBUTE_VALUE;

    public static final String ATT_PERMISSION_TARGET =
        sailpoint.integration.ProvisioningPlan.ATT_PERMISSION_TARGET;
    public static final String ATT_PERMISSION_RIGHTS =
        sailpoint.integration.ProvisioningPlan.ATT_PERMISSION_RIGHTS;

    public static final String ATT_REQUEST_ARGUMENTS =
        sailpoint.integration.ProvisioningPlan.ATT_REQUEST_ARGUMENTS;

    public static final String ATT_REQUEST_RESULT = 		
        sailpoint.integration.ProvisioningPlan.ATT_REQUEST_RESULT;

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A reserved name that can be set in an AttributeRequest's assignmentId
     * to indicate that a new assignment is to be created. This is an alternative
     * to having the application generate a uuid.
     */
    public static final String ASSIGNMENT_ID_NEW = "new";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The Identity being acted upon.
     * 
     * @ignore
     * NOTE: This is not persisted in the XML, it should only be used
     * to pass an identity into the project compiler, thereafter the
     * identity is referenced by name in the ProvisioningProject.
     */
    Identity _identity;
    
    /**
     * The name of an account in the provisioning system that corresponds
     * to the _identity. This is set in cases where there is a "meta" user
     * account through which all provisioning requests have to be channeled.
     * This is the case for OIM and Sun IDM. ITIM and SM do not have
     * this concept. The name here will normally be taken from the 
     * Link.nativeIdentity of the Link on _identity that corresponds to the
     * provisioning system. This is only set in compiled plans that
     * are about to be sent to the integration executor, or that are being
     * retried.
     *
     * In some cases there can also be an AccountRequest for this same
     * identity if changes are also being made to the meta user.
     */
    String _nativeIdentity;
    
    /**
     * The list of account operations to process.
     */
    List<AccountRequest> _accounts;

    /**
     * The list of object (non account) operations to process.
     */
    // jsl - it would be nice if we could use this consistently
    // instead of AccountRequest if we need a generalized request interface
    List<ObjectRequest> _objectRequests;

    /**
     * Extensible attributes associated with this plan.
     * What is in here is different for the master plan and the
     * compiled integration plans.
     *
     * NOTE: This is called "arguments" rather than "attributes"
     * throughout the model because the term "attributes" has 
     * already been used to mean AttributeRequests so try
     * to avoid confusion.
     *
     * In the master plan this can contain compilation options that
     * apply to the entire plan. Any of the compilation and evaluation
     * options defined by PlanCompiler and PlanEvaluator can go here as
     * an alternative to calling specific option setter methods on Provisioner.
     * This would be the mechanism used by web service calls to
     * pass in compilation options.
     *
     * Metadata like ARG_COMMENTS is also allowed here that applies to the
     * entire plan. A comments field could have been added, 
     * but was not, to be consistent with comments in account and attribute
     * requests.  
     *
     * For compiled integration plans this contains what prior to 5.0
     * we called "integration data". These are arbitrary values
     * that can be set by a "plan initializer" rule defined in the
     * IntegrationConfig. This was added as a hook to pass extra
     * information from the identity cube down to the integration.
     */
    Attributes<String,Object> _arguments;

    /**
     * Tracking id used by remediation manager to create "itemized" plans.
     */
    String _trackingId;

    /**
     * The Identity(s) who has made the requests.
     * Typically there is only one identity on the list.
     * 
     * @ignore
     * jsl - I really wish this were List<String> 
     */
    List<Identity> _requesters;

    /**
     * The provisioning targets for the plan.
     */
    List<ProvisioningTarget> _provisioningTargets;

    /**
     * The values that were pre-filtered before compilation of
     * this plan. Typically used in certifications.
     */
    List<AbstractRequest> _filtered;

    /**
     * Questions that have been previously submitted and answered.
     * These are copied from the project, and used when compiling the plan
     * without the previous project.
     */
    List<Question> _questionHistory;
    
    //
    // Plan compilation results
    //

    /**
     * Name of the IntegrationConfig this plan will be sent to.
     * Used only by the Provisioner when it compiles high-level plans
     * into targeted plans. Used by the unit tests.
     */
    String _targetIntegration;

    // 
    // Plan evaluation results
    // 

    /**
     * Top level results from plan evaluation.
     * This can be placed here by the Connector, or returned
     * by the Connector's provision() method, in which case the
     * plan evaluator will leave it here.
     *
     * In addition the connector can leave fine grained 
     * ProvisioningResult objects inside each AccountRequest.
     * When fine grained requests are used a top-level request
     * is normally not used, but the plan evaluator must handle
     * combinations.
     */
    ProvisioningResult _result;

    /**
     * Transient property that indicates whether or not a plan was actually
     * sent to the connector. Currently used to determine whether or not
     * provisioning transactions need to be logged.
     */
    boolean _provisioned;

    /**
     * Transient property used only on partitioned single-application plans 
     * that indiciates whether the application was in a maintenance window.
     * Used to schedule retry requests.
     */
    long _maintenanceExpiration;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningPlan() {
    }

    public ProvisioningPlan(Map map) {
        fromMap(map);
    }

    /**
     * Clone one plan from another.
     * This has historically not included evaluation results.
     */
    public ProvisioningPlan(ProvisioningPlan src) {
        this(src, true);
    }

    /**
     * Clone one plan from another. If keepRequests is true, we will keep the AbstractRequests on the plan. If false,
     * we will copy over everything but the AbstractRequests
     *
     * @param src ProvisioningPlan to clone
     * @param keepRequests true to keep Account/Object Requests
     */
    public ProvisioningPlan(ProvisioningPlan src, boolean keepRequests) {
        if (src != null) {

            _identity = src._identity;
            _trackingId = src._trackingId;
            _targetIntegration = src._targetIntegration;

            if (src._arguments != null) {
                // shallow copy enough?
                _arguments = new Attributes<String,Object>(src._arguments);
            }
                
            if (src._requesters != null)
                _requesters = new ArrayList<Identity>(src._requesters);


            _provisioningTargets = src.getProvisioningTargets();

            // don't bother cloning the execution results?
            // _arguments might be interesting?
            // UPDATE: now that _arguments contains compiler options
            // it should not make sense to clone that during compilation
            // and since cloning is only done during compilation cloning
            // evaluation results including "integration data" returned
            // by the plan initializer rule would never be relevant.

            if(keepRequests) {
                List<AccountRequest> accounts = src._accounts;
                if (accounts != null) {
                    for (int i = 0; i < accounts.size(); i++)
                        add(new AccountRequest(accounts.get(i)));
                }

                if (src._objectRequests != null) {
                    for (int i = 0; i < src._objectRequests.size(); i++) {
                        if (_objectRequests == null)
                            _objectRequests = new ArrayList<ObjectRequest>();
                        _objectRequests.add(new ObjectRequest(src._objectRequests.get(i)));
                    }
                }
            }
            // what about results?
        }
    }

    /**
     * Constructor that creates a ProvisioningPlan that will remove the given
     * entitlements.
     */
    public ProvisioningPlan(Collection<EntitlementSnapshot> snaps) {

        setEntitlementSnapshots(snaps,true, null);
    }
    
    public ProvisioningPlan(Collection<EntitlementSnapshot> snaps, boolean isCertifyingIdentities, String objectType) {

        setEntitlementSnapshots(snaps, isCertifyingIdentities, objectType);
    }

    /**
     * Return true if this is an identity plan.
     * These are distinguished by having AttributeRequests and normally
     * do not have ObjectRequests. In this case the _identity property
     * just also be set.
     */
    public boolean isIdentityPlan() {
        return (_accounts != null && _accounts.size() > 0);
    }

    public void merge(ProvisioningPlan planToMerge) {
        merge(planToMerge, true);
    }

    public void merge(ProvisioningPlan planToMerge, boolean removeMatches){
        if (planToMerge==null)
            return;

        // necessary to convey sourceType, etc.
        // shallow copy is okay for now
        if (planToMerge._arguments != null) {
            if (_arguments == null)
                _arguments = new Attributes<String,Object>();
            _arguments.putAll(planToMerge._arguments);
        }

        if (planToMerge.getAccountRequests() != null){
            if (_accounts == null)
                _accounts = new ArrayList<AccountRequest>();
            for(AccountRequest req : planToMerge.getAccountRequests()){
                // Check if any of the AccountRequests in the planToMerge are also in this plan
                // and remove those that are.
                Iterator<AccountRequest> thisReqIter = _accounts.iterator();
                while (thisReqIter.hasNext()) {
                    AccountRequest thisReq = thisReqIter.next();
                    if (thisReq.isMatch(req) && removeMatches) {
                        thisReqIter.remove();
                    }
                }

                _accounts.add(new AccountRequest(req));
            }
        }

        if (planToMerge.getObjectRequests() != null){
            if (_objectRequests == null)
                _objectRequests = new ArrayList<ObjectRequest>();
            for(ObjectRequest req : planToMerge.getObjectRequests()){
                // Check if any of the ObjectRequests in the planToMerge are also in this plan
                // and remove those that are.
                Iterator<ObjectRequest> thisReqIter = _objectRequests.iterator();
                while (thisReqIter.hasNext()) {
                    ObjectRequest thisReq = thisReqIter.next();
                    if (thisReq.isMatch(req)) {
                        thisReqIter.remove();
                    }
                }

                _objectRequests.add(new ObjectRequest(req));
            }
        }

        if (planToMerge.getRequesters() != null){
            if (_requesters == null)
                _requesters = new ArrayList<Identity>();
            for(Identity req : planToMerge.getRequesters()){
                if (!_requesters.contains(req))
                    _requesters.add(req);
            }
        }
    }

    public void add(AccountRequest account) {
        if (account != null) {
            if (_accounts == null)
                _accounts = new ArrayList<AccountRequest>();
            _accounts.add(account);
        }
    }

    public void add(ObjectRequest object) {
        if (object != null) {
            if (_objectRequests == null)
                _objectRequests = new ArrayList<ObjectRequest>();
            _objectRequests.add(object);
        }
    }
    public void remove(AccountRequest account) {
        if (account != null && _accounts != null)
            _accounts.remove(account);
    }

    public void addRequest(AbstractRequest req) {

        // sigh, don't have nice polymorphism here
        if (req instanceof AccountRequest)
            add((AccountRequest)req);

        else if (req instanceof ObjectRequest)
            addObjectRequest((ObjectRequest)req);
    }


    /**
     * The list of account operations to process.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setAccountRequests(List<AccountRequest> reqs) {
        _accounts = reqs;
    }

    public List<AccountRequest> getAccountRequests() {
        return _accounts;
    }

    /**
     * The list of object (non account) operations to process.
     */
    public List<ObjectRequest> getObjectRequests() {
        return _objectRequests;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setObjectRequests(List<ObjectRequest> reqs) {
        this._objectRequests = reqs;
    }

    public void addObjectRequest(ObjectRequest request) {
        if (request != null) {
            if (_objectRequests == null)
                _objectRequests = new ArrayList<ObjectRequest>();
            _objectRequests.add(request);
        }
    }

    /**
     * Convenience method for things that want to iterate over all
     * account and object requests without caring about the subclass.
     */
    public List<AbstractRequest> getAllRequests() {

        List<AbstractRequest> requests = null;
        if (_accounts != null || _objectRequests != null) {
            requests = new ArrayList<AbstractRequest>();
            if (_accounts != null)
                requests.addAll(_accounts);
            if (_objectRequests != null)
                requests.addAll(_objectRequests);
        }
        return requests;
    }

    /**
     * Return true if the plan is logically empty.
     * It is assumed that if there are any AccountRequests they
     * do something, but to be thorough check
     * op=Modify requests to see if they have no AttributeRequests
     * and ignore those.
     */
    public boolean isEmpty() {
        return isEmpty(_accounts) && isEmpty(_objectRequests);
    }

    /**
     * Determines if the list of AbstractRequests is empty.
     *
     * @param requests The requests.
     * @return True if empty, false otherwise.
     */
    private <T extends AbstractRequest> boolean isEmpty(List<T> requests) {
        // if null or empty
        if (Util.isEmpty(requests)) {
            return true;
        } else {
            for (T req : requests) {
                // if op is modify then see if there are any attr/perm requests
                // and if so then non-empty
                if (ObjectOperation.Modify.equals(req.getOp())) {
                    if (!Util.isEmpty(req.getAttributeRequests()) ||
                        !Util.isEmpty(req.getPermissionRequests())) {
                        return false;
                    }
                } else {
                    // any other op should be non-empty
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Older name, isEmpty is preferred.
     */
    public boolean hasRequests(){
        return !isEmpty();
    }

    /**
     * The Identity(s) who has made the requests.
     */
    public List<Identity> getRequesters() {
        return _requesters;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public void setRequesters(List<Identity> _requesters) {
        this._requesters = _requesters;
    }

    public void addRequester(Identity requester){
        if (_requesters == null)
            _requesters = new ArrayList<Identity>();

        // prevent duplication of requesters
        if (requester != null && !_requesters.contains(requester))
            _requesters.add(requester);
    } 
    
    public String getComments() {
        return getString(ARG_COMMENTS);
    }

    public void setComments(String c) {
        put(ARG_COMMENTS, c);
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<ProvisioningTarget> getProvisioningTargets() {
        return _provisioningTargets;
    }

    public void setProvisioningTargets(List<ProvisioningTarget> provisioningTargets) {
        _provisioningTargets = provisioningTargets;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<AbstractRequest> getFiltered() {
        return _filtered;
    }

    public void setFiltered(List<AbstractRequest> filtered) {
        _filtered = filtered;
    }

    public void addFiltered(AbstractRequest request) {
        if (_filtered == null) {
            _filtered = new ArrayList<>();
        }

        _filtered.add(request);
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Question> getQuestionHistory() {
        return _questionHistory;
    }

    public void setQuestionHistory(List<Question> _questionHistory) {
        this._questionHistory = _questionHistory;
    }

    /**
     * Return the source of this plan.
     * This is normally one of the sailpoint.object.Source enumeration
     * names but it can be a custom source.
     */
    public String getSource() {
        return getString(ARG_SOURCE);
    }

    public void setSource(String s) {
        put(ARG_SOURCE, s);
    }

    public void setSource(Source s) {
        put(ARG_SOURCE, s.toString());
    }

    /**
     * Return the source of this plan.
     * This is the old property name that is retained for backward compatibility
     * with older integrations.
     */
    public String getSourceType() {
        return getSource();
    }

    public void setSourceType(String s) {
        setSource(s);
    }

    public String getSourceName() {
        return getString(ARG_SOURCE_NAME);
    }

    public void setSourceName(String sourceName) {
        put(ARG_SOURCE_NAME, sourceName);
    }

    public String getSourceId() {
        return getString(ARG_SOURCE_ID);
    }

    public void setSourceId(String sourceId) {
        put(ARG_SOURCE_ID, sourceId);
    }

    /**
     * The Identity being acted upon.
     */
    public Identity getIdentity() {
        return _identity;
    }

    public void setIdentity(Identity identity) {
        this._identity = identity;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String s) {
        _nativeIdentity = s;
    }

    public Attributes<String, Object> getArguments() {
        return _arguments;
    }
    
    /**
     * @ignore
     * Note that this will serialize as <Attributes> rather
     * than <Arguments>.  We're doing this in several classes
     * now and it's become expected.  It would require some
     * serializer changes to change the element without adding
     * another level of wrapper.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setArguments(Attributes<String, Object> atts) {
        _arguments = atts;
    }
    
    /**
     * @ignore
     * This is the old name for what is now the "attributes" property.
     * PlanEvaluator doesn't use this any more but some of the old
     * plan initializer rules may still use this name.
     */
    public Attributes<String, Object> getIntegrationData() {
        return _arguments;
    }
    
    public void setIntegrationData(Attributes<String, Object> integrationData) {
        _arguments = integrationData;
    }

    /**
     * Name of the IntegrationConfig this plan will be sent to.
     */
    @XMLProperty
    public String getTargetIntegration() {
        return _targetIntegration;
    }

    public void setTargetIntegration(String name) {
        _targetIntegration = name;
    }

    /**
     * Status of plan evaluation, derived from the RequestResult
     * returned by the IntegrationExecutor.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ProvisioningResult getResult() {
        return _result;
    }
    
    public void setResult(ProvisioningResult r) {
        _result = r;
    }

    /**
     * Transient property set to indiciate that this plan was actually
     * sent to the connector.
     */
    public boolean isProvisioned() {
        return _provisioned;
    }

    public void setProvisioned(boolean provisioned) {
        _provisioned = provisioned;
    }

    /**
     * Transient property set to indiciate that the plan was not sent to 
     * the connector because it was in a maintenance window.
     */
    public long getMaintenanceExpiration() {
        return _maintenanceExpiration;
    }

    public void setMaintenanceExpiration(long l) {
        _maintenanceExpiration = l;
    }
    
    /**
     * Return true if the given application name is the IdentityIQ application, either
     * the Certification "IdentityIQ" name or the ProvisioningPlan "IIQ" name.
     */
    public static boolean isIIQ(String app) {
        return APP_IIQ.equals(app) || Certification.IIQ_APPLICATION.equals(app);
    }
    
    /**
     * Return true if this is a partitioned plan targeted at IdentityIQ.
     * Used when iterating over plans managed by a ProvisioningProject.
     */
    public boolean isIIQ() {
        return APP_IIQ.equals(_targetIntegration);
    }

    /**
     * Tracking id of an "itemized" plan culled from the 
     * compiled plans.
     */
    @XMLProperty
    public String getTrackingId() {
        return _trackingId;
    }

    public void setTrackingId(String id) {
        _trackingId = id;
    }

    /**
     * Set tracking id for the plan as well as all the requests it contains.
     * @param id Tracking ID
     */
    public void setRequestTrackingId(String id){
        if (_accounts != null){
            for(ProvisioningPlan.AccountRequest req : _accounts){
                req.setTrackingId(id);
                if (req.getAttributeRequests() != null){
                    for(ProvisioningPlan.AttributeRequest attrReq : req.getAttributeRequests()){
                        attrReq.setTrackingId(id);
                    }
                }
                if (req.getPermissionRequests() != null){
                    for(ProvisioningPlan.PermissionRequest pReq : req.getPermissionRequests()){
                        pReq.setTrackingId(id);
                    }
                }
            }
        } else if(_objectRequests != null) {        	
            for(ProvisioningPlan.ObjectRequest req : _objectRequests){
                req.setTrackingId(id);
                if (req.getAttributeRequests() != null){
                    for(ProvisioningPlan.AttributeRequest attrReq : req.getAttributeRequests()){
                        attrReq.setTrackingId(id);
                        }
                }
                if (req.getPermissionRequests() != null){
                    for(ProvisioningPlan.PermissionRequest pReq : req.getPermissionRequests()){
                        pReq.setTrackingId(id);
                    }
                }
            }          
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Upgrades
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Prior to 5.0 these were top-level properties and XML attributes,
    // if we find the XML for old plans, convert these to plan attributes.
    // These should only be necessary for one release.
    //

    /**
     * @exclude
     * @deprecated use {@link #getSource()}
     */
    @Deprecated
    @XMLProperty(xmlname="sourceType")
    public String getXmlSourceType() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link #setSource(String)}
     */
    @Deprecated
    public void setXmlSourceType(String s) {
        setSource(s);
    }

    /**
     * @exclude
     * @deprecated use {@link #getSourceName()}
     */
    @Deprecated
    @XMLProperty(xmlname="sourceName")
    public String getXmlSourceName() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link #setSourceName(String)}
     */
    @Deprecated
    public void setXmlSourceName(String s) {
        setSourceName(s);
    }

    /**
     * @exclude
     * @deprecated use {@link #getSourceId()}
     */
    @Deprecated
    @XMLProperty(xmlname="sourceId")
    public String getXmlSourceId() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link #setSourceId(String)}
     */
    @Deprecated
    public void setXmlSourceId(String s) {
        setSourceId(s);
    }

    //
    // Prior to 5.0 these were top-level plan properties,
    // now they are encapsulated in a ProvisioningResult object
    // Support these in case we need to parse older serialized
    // plans.
    //

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#getStatus()} on {@link #getResult()} 
     */
    @Deprecated
    @XMLProperty(xmlname="status")
    public String getXmlStatus() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#setStatus(String)} on {@link #getResult()} 
     */
    @Deprecated
    public void setXmlStatus(String status) {
        if (status != null) {
            if (_result == null) _result = new ProvisioningResult();
            _result.setStatus(status);
        }
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#getRequestID()} on {@link #getResult()} 
     */
    @Deprecated
    @XMLProperty(xmlname="requestID")
    public String getXmlRequestID() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#setRequestID(String)} on {@link #getResult()} 
     */
    @Deprecated
    public void setXmlRequestID(String id) {
        if (id != null) {
            if (_result == null) _result = new ProvisioningResult();
            _result.setRequestID(id);
        }
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#getWarnings()} on {@link #getResult()} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Warnings")
    public List<Message> getXmlWarnings() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#setWarnings(java.util.List)} on {@link #getResult()} 
     */
    @Deprecated
    public void setXmlWarnings(List<Message> warnings) {
        if (warnings != null) {
            if (_result == null) _result = new ProvisioningResult();
            _result.setWarnings(warnings);
        }
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#getErrors()} on {@link #getResult()} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Errors")
    public List<Message> getXmlErrors() {
        return null;
    }

    /**
     * @exclude
     * @deprecated use {@link ProvisioningResult#setErrors(java.util.List)} on {@link #getResult()} 
     */
    @Deprecated
    public void setXmlErrors(List<Message> errors) {
        if (errors != null) {
            if (_result == null) _result = new ProvisioningResult();
            _result.setErrors(errors);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Adds an AccountRequest, normally for Delete, Disable, Enable, Unlock.
     * Not expecting this to be called more than once for a given
     * appname. If you do another AccountRequest will be added but
     * calls to add(appname,attname,op,value) below will continue
     * going to the first one.
     */
    public AccountRequest add(String appname, String identity, AccountRequest.Operation op) {

        AccountRequest account = null;

        // Should have a method that takes an instance but those are rare.
        if (appname != null && identity != null) {
            account = new AccountRequest();
            account.setApplication(appname);
            account.setNativeIdentity(identity);
            account.setOperation(op);
            add(account);
        }
        return account;
    }

    /**
     * Adds a simple attribute request for an application.
     * This does not attempt to simplify the plan.
     * You normally precede this with a call to {@link #add(String, String, sailpoint.object.ProvisioningPlan.AccountRequest.Operation)}
     * to build the containing AccountRequest with the right identity.
     */
    public AccountRequest add(String appname, String nativeIdentity, String attname,
                              Operation op, Object value) {

        AccountRequest account = null;

        if (appname != null && attname != null) {

            account = getAccountRequest(appname, null, nativeIdentity);
            if (account == null) {
                // default op is Modify
                account = new AccountRequest();
                account.setApplication(appname);
                account.setNativeIdentity(nativeIdentity);
                add(account);
            }
            
            AttributeRequest att = new AttributeRequest();
            att.setName(attname);
            att.setOperation(op);
            att.setValue(value);
            
            account.add(att);
        }
        return account;
    }

    /**
     * Set a plan attribute.  
     * 
     * For compiled plans this is intended for use by initialization rules
     * from the IntegrationConfig so they can pass extra stuff
     * through to the IntegrationExecutor.
     *
     * For the master plan this can be used to set compilation options.
     */
    public void put(String name, Object value) {
        if (name != null) {
            if (_arguments == null)
                _arguments = new Attributes<String,Object>();
            _arguments.putClean(name, value);
        }
    }

    public Object get(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }

    public String getString(String name) {
        return (_arguments != null) ? _arguments.getString(name) : null;
    }

    /**
     * Look for a matching request. Used in merging.
     * Returns the first one if there is ambiguity.
     * @param src Request to match. 
     * @return Matching AbstractRequest of same type
     */
    public AbstractRequest getMatchingRequest(AbstractRequest src) {
        return getMatchingRequest(src, false);
    }

    /**
     * Look for a matching request. Used in merging.
     * Returns the first one if there is ambiguity.
     * @param src Request to match.
     * @param allowGeneratedId If true, will match first request from same application 
     *                         if either is missing nativeIdentity. Used for create requests
     *                         after being sent to connector. 
     * @return Matching AbstractRequest of same type
     */
    public AbstractRequest getMatchingRequest(AbstractRequest src, boolean allowGeneratedId) {

        AbstractRequest found = null;

        // sigh, need better polymorphism
        if (src instanceof AccountRequest)
            found = getMatchingRequest(_accounts, src, allowGeneratedId);
        else
            found = getMatchingRequest(_objectRequests, src, allowGeneratedId);

        return found;
    }

    public <T extends AbstractRequest> AbstractRequest 
    getMatchingRequest(List<T> requests, AbstractRequest src) {
        return getMatchingRequest(requests, src, false);
    }

    public <T extends AbstractRequest> AbstractRequest
    getMatchingRequest(List<T> requests, AbstractRequest src, boolean allowGeneratedId) {

        AbstractRequest found = null;
        if (requests != null) {
            for (AbstractRequest req : requests) {
                if (req.isTargetMatch(src)) {
                    found = req;
                    break;
                }
            }

            // Kludge: for create requests, it is common for the request
            // to start with no nativeIdentity which the Connector then
            // sets as a side effect.  In this case there is in practice only
            // one request in the plan so we can go ahead and resolve to that.
            // If we ever did have more than one create per plan we would
            // have to use positional matching and pass in the position.
            // We need to make sure that we don't find a false positive in
            // something like a role request in an iiq plan, so compare
            // applications and operations as well.
            if (found == null && allowGeneratedId && requests.size() == 1) {
                AbstractRequest first = requests.get(0);
                
                if (Util.nullSafeEq(first.getApplication(), src.getApplication()) &&
                    // this should work! put it back in, sayeth Jeff
                    src.getOp() == ObjectOperation.Create && 
                    first.getOp() == ObjectOperation.Create && 
                    (src.getNativeIdentity() == null || first.getNativeIdentity() == null)) {
                    
                    found = first;
                }
            }
        }

        return found;
    }

    /**
     * Return an AccountRequest that matches another.
     * This was used by PlanCompiler but now it uses getMatchingRequest()
     * Have to keep though in case it leaked into custom code.
     */
    public AccountRequest getMatchingAccountRequest(AccountRequest src) {
        AccountRequest found = null;
        if (src != null)
            found = (AccountRequest)getMatchingRequest(src);
        return found;
    }

    /**
     * Return all AccountRequests in this plan for the given application.
     * This can return multiple requests if the identity has two accounts on
     * the same application.
     */
    public List<AccountRequest> getAccountRequests(String appname) {
        List<AccountRequest> reqs = new ArrayList<AccountRequest>();
        if (_accounts != null && appname != null) {
            for (AccountRequest req : _accounts) {
                if (appname.equals(req.getApplication())) {
                    reqs.add(req);
                }
            }
        }
        return reqs;
    }

    /**
     * Gets all account requests that match the application and native identity. If
     * native identity is not set then all requests for the specified application
     * will be returned.
     *
     * @param appname The app name.
     * @param nativeIdentity The native identity.
     * @return The account requests.
     */
    public List<AccountRequest> getAccountRequests(String appname, String nativeIdentity) {
        List<AccountRequest> requests = new ArrayList<>();
        for (AccountRequest request : Util.iterate(_accounts)) {
            if (Util.nullSafeEq(appname, request.getApplication())) {
                // if no native id passed in then just gather all requests
                // otherwise check the native id for equality
                if (Util.isNullOrEmpty(nativeIdentity) || Util.nullSafeEq(nativeIdentity, request.getNativeIdentity())) {
                    requests.add(request);
                }
            }
        }

        return requests;
    }
    
    /**
     * Find the account request for an application.
     * 
     * @deprecated  Use {@link #getAccountRequest(String, String, String)}
     *   instead which specifies the native identity or
     *   {@link #getAccountRequests(String)}.
     */
    @Deprecated
    public AccountRequest getAccountRequest(String appname) {
        return getAccountRequest(appname, null, null, true);
    }

    /**
     * Return an AccountRequest for the requested account.
     */
    public AccountRequest getAccountRequest(String appname, String instance,
                                            String nativeIdentity) {
        return getAccountRequest(appname, instance, nativeIdentity, false);
    }

    /**
     * Return an ObjectRequest for the requested object.
     */
    public ObjectRequest getObjectRequest(String appName, String instance, String nativeIdentity) {
        
        if (Util.isEmpty(_objectRequests)) {
            return null;
        }

        ObjectRequest found = null;
        for (ObjectRequest request : _objectRequests) {
            if (Util.nullSafeEq(appName, request.getApplication(), true)
                    &&
                Util.nullSafeEq(instance, request.getInstance(), true)
                    &&
                Util.nullSafeEq(nativeIdentity, request.getNativeIdentity(), true)) {
                found = request;
                break;
            }
        }
        return found;
    }

    /**
     * Find the account request for an account.
     */
    private AccountRequest getAccountRequest(String appname, String instance,
                                             String nativeIdentity,
                                             boolean onlyAppName) {
        AccountRequest found = null;
        if (_accounts != null && appname != null) {
            for (AccountRequest req : _accounts) {
                if (appname.equals(req.getApplication()) &&
                    (onlyAppName ||
                     (Util.nullSafeEq(req.getInstance(), instance, true) &&
                      Util.nullSafeEq(req.getNativeIdentity(), nativeIdentity, true)))) {
                    found = req;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find the account request for the IdentityIQ application.
     */
    public AccountRequest getIIQAccountRequest() {
        return getSingleAccountRequest(APP_IIQ);
    }

    /**
     * Find the account request for the IDM application, which is used when
     * integration support provisioning IdM-managed roles to users.
     */
    public AccountRequest getIDMAccountRequest() {
        return getSingleAccountRequest(APP_IDM);
    }

    /**
     * Return the AccountRequest for the given application. This method assumes
     * that there is a single account request in this plan for the requested application
     * so should only be called in cases where this can be guaranteed.
     */
    private AccountRequest getSingleAccountRequest(String appName) {
        AccountRequest req = null;
        List<AccountRequest> reqs = getAccountRequests(appName);
        if (1 == reqs.size()) {
            req = reqs.get(0);
        }
        else if (reqs.size() > 1) {
            log.warn("Found multiple account requests for " + appName);
        }
        return req;
    }
    
    /**
     * Set the AbstractRequests that will remove the given entitlements.
     */
    private void setEntitlementSnapshots(Collection<EntitlementSnapshot> snaps, boolean isCertifyingIdentites, String objectType) {

        if (null != snaps) {
            for (EntitlementSnapshot snap : snaps) {
                // We're assuming that each each account is represented in a
                // single EntitlementSnapshot.
                AbstractRequest req = null;
                if(isCertifyingIdentites) {
                    req = new AccountRequest(AccountRequest.Operation.Modify,
                                       snap.getApplication(),
                                       snap.getInstance(),
                                       snap.getNativeIdentity());
                } else {                
                    req = new ObjectRequest();
                    req.setOp(ObjectOperation.Modify);
                    req.setApplication(snap.getApplication());
                    req.setInstance(snap.getInstance());
                    req.setNativeIdentity(snap.getNativeIdentity());
                    req.setType(objectType);
                }
                
                Attributes<String,Object> attrs = snap.getAttributes();
                if (null != attrs) {
                    for (Map.Entry<String,Object> attr : attrs.entrySet()) {
                        req.add(new AttributeRequest(attr.getKey(),
                                                     Operation.Remove,
                                                     attr.getValue()));
                    }
                }

                List<Permission> perms = snap.getPermissions();
                if (null != perms) {
                    for (Permission perm : perms) {
                        req.add(new PermissionRequest(perm.getTarget(),
                                                      Operation.Remove,
                                                      perm.getRightsList(),
                                                      perm.getAggregationSource()));
                    }
                }

                addRequest(req);
            }
        }
    }

    /**
     * Convert this ProvisioningPlan into a List of EntitlementSnapshots that
     * are to be removed. This throws a GeneralException if there are any
     * requests other than attribute or permission removal.
     *
     * @return A List of EntitlementSnapshots that are to be removed.
     *
     * @throws GeneralException  If this plan has any requests other than
     *                           attribute or permission removal.
     */
    public Collection<EntitlementSnapshot> convertToEntitlementSnapshots()
        throws GeneralException {

        EntitlementCollection collection = new EntitlementCollection();

        if (null != _accounts) {
            for (AccountRequest acctReq : _accounts) {

                if (!AccountRequest.Operation.Modify.equals(acctReq.getOperation())) {
                    throw new GeneralException("Only account modification requests supported.");
                }

                if (null != acctReq.getAttributeRequests()) {
                    for (AttributeRequest attrReq : acctReq.getAttributeRequests()) {
                        if (!Operation.Remove.equals(attrReq.getOperation())) {
                            throw new GeneralException("Only attribute removal requests supported.");
                        }

                        collection.addAttribute(acctReq.getApplication(), acctReq.getInstance(),
                                                acctReq.getNativeIdentity(),
                                                acctReq.getNativeIdentity(),
                                                attrReq.getName(), attrReq.getValue());
                    }
                }

                if (null != acctReq.getPermissionRequests()) {
                    for (PermissionRequest permReq : acctReq.getPermissionRequests()) {
                        if (!Operation.Remove.equals(permReq.getOperation())) {
                            throw new GeneralException("Only permission removal requests supported.");
                        }

                        collection.addPermission(acctReq.getApplication(), acctReq.getInstance(),
                                                 acctReq.getNativeIdentity(),
                                                 acctReq.getNativeIdentity(),
                                                 new Permission(permReq.getRights(), permReq.getTarget()));
                    }
                }
            }
        }

        return collection.getEntitlements();
    }

    /**
     * If appname is {@link #APP_IIQ} we change it to {@link #IIQ_APPLICATION_NAME}
     * @param appName the appname to display
     * @return modified Appname
     * 
     * @ignore
     * TODO: Move this to a utility class.
     */
    public static String getApplicationDisplayName(String appName) {
        
        if (ProvisioningPlan.APP_IIQ.equals(appName)) {
            appName = ProvisioningPlan.IIQ_APPLICATION_NAME;
        }
        return appName;
    }

    /**
     * Get the names of the applications referenced by the AccountRequests as well as
     * the ObjectRequests in this ProvisioningPlan.
     */
    public List<String> getApplicationNames() {
        Set<String> apps = new HashSet<String>();
        if (null != _accounts) {
            for (AccountRequest req : _accounts) {
                apps.add(req.getApplication());
            }
        }

        if (_objectRequests != null && !_objectRequests.isEmpty()){
            for (ObjectRequest objectRequest : _objectRequests) {
                String app = objectRequest.getApplication();
                if (app == null) {
                    apps.add(Certification.IIQ_APPLICATION);
                } else {
                    apps.add(app);
                }
            }
        }

        return Arrays.asList(apps.toArray(new String[apps.size()]));
    }

    /**
     * Get the applications referenced by this ProvisioningPlan.
     */
    public List<Application> getApplications(Resolver resolver)
        throws GeneralException {

        List<Application> apps = new ArrayList<Application>();
        if (null != _accounts) {
            for (AccountRequest req : _accounts) {
                Application app = req.getApplication(resolver);

                // Can be null if the app was deleted or renamed.
                if (null != app) {
                    apps.add(app);
                }
                else {
                    String appName = req.getApplication();
                    // Skip the IIQ case since this is normal and not alarming
                    if ( ( appName != null ) && ("IIQ".compareTo(req.getApplication()) != 0 ) ) { 
                        if (log.isInfoEnabled())
                            log.info("Could not load application '" + req.getApplication() + "' " +                 
                                     "for provisioning plan - skipping. Plan: " + this);
                    }
                }
            }
        }

        return apps;
    }

    public List<AccountRequest> getNonModifyAccountRequests() {

        List<AccountRequest> requests = null;

        if (null != _accounts) {
            for (AccountRequest request : _accounts) {
                if (!AccountRequest.Operation.Modify.equals(request.getOperation())) {
                    if (null == requests) {
                        requests = new ArrayList<AccountRequest>();
                    }
                    requests.add(request);
                }
            }
        }

        return requests;
    }

    public List<AccountRequest> getModifyAccountRequests() {

        List<AccountRequest> requests = null;

        if (null != _accounts) {
            for (AccountRequest request : _accounts) {
                if (AccountRequest.Operation.Modify.equals(request.getOperation())) {
                    if (null == requests) {
                        requests = new ArrayList<AccountRequest>();
                    }
                    requests.add(request);
                }
            }
        }

        return requests;
    }

    /**
     * Checks if the PermissionRequest / AttributeRequest within the
     * AccountRequest / ObjectRequest contains "targetCollector" and
     * returns the application name if it does.
     *
     * @return appName
     */
    public String getAppForTargetCollector()
        throws GeneralException {

        String appName = null;

        List <AccountRequest> acctReqs = getAccountRequests();
        if ( null != acctReqs && acctReqs.size() > 0 ) {
            // assuming, only one account request in AccountRequest list
            AccountRequest acctReq = acctReqs.get(0);
            if ( null != acctReq ) {
                if ( Util.isNotNullOrEmpty(acctReq.getTargetCollector()) ) {
                    appName = acctReq.getApplication();
                }
            }
        }
        else {
            List<ObjectRequest> objReqs = getObjectRequests();
            if ( null != objReqs && objReqs.size() > 0 ) {
                // assuming, only one object request in ObjectRequest list
                ObjectRequest objReq = objReqs.get(0);
                if ( null != objReq ) {
                    if ( Util.isNotNullOrEmpty(objReq.getTargetCollector()) ) {
                        appName = objReq.getApplication();
                    }
                }
            }
        }

        return appName;
    }

    /**
     * Render the ProvisioningPlan as a generic Map/List model.
     * This is used for older IntegrationExecutors that used the
     * RemoteIntegration interface to send plans over the network.
     * Note that the ProvisioningResult is not included.
     */
    public Map toMap() {

        Map map = new HashMap();

        // Unlike sailpoint.integration.ProvisioningPlan we don't
        // include _identity.  The _identity here represents 
        // the IIQ identity we're operating against, the identity
        // in an integration plan represents the master user
        // in the target provisioning system.  In compled plans that
        // will be stored in the _nativeIdentity property.
        if (_nativeIdentity != null)
            map.put(ATT_PLAN_IDENTITY, _nativeIdentity);

        if (_accounts != null) {
            List list = new ArrayList();
            for (AccountRequest req : _accounts) {
                Map rmap = req.toMap();
                if (rmap != null)
                    list.add(rmap);
            }
            map.put(ATT_PLAN_ACCOUNTS, list);
        }

        if (_objectRequests != null) {
            List list = new ArrayList();
            for (ObjectRequest req : _objectRequests) {
                Map rmap = req.toMap();
                if (rmap != null)
                    list.add(rmap);
            }
            map.put(ATT_PLAN_OBJECTS, list);
        }
        
        if (_arguments != null)
            map.put(ATT_PLAN_ARGUMENTS, (Map)_arguments);
        
        if(_result != null) 
           map.put(ATT_REQUEST_RESULT, _result.toMap()); 
        return map;
    }

    /**
     * Build a ProvisioningPlan from the generic Map/List model.
     * This was used when cloning plans for IntegrationExecutors.
     * It should eventually be removed.
     */
    public void fromMap(Map map) {

        _accounts = null;
        _objectRequests = null;
        _requesters = null;
        _arguments = null;

        _nativeIdentity = Util.otoa(map.get(ATT_PLAN_IDENTITY));

        Object o = map.get(ATT_PLAN_ACCOUNTS);
        if (o instanceof List) {
            List list = (List)o;
            for (Object el : list) {
                if (el instanceof Map)
                    add(new AccountRequest((Map)el));
            }
        }

        o = map.get(ATT_PLAN_OBJECTS);
        if (o instanceof List) {
            List list = (List)o;
            for (Object el : list) {
                if (el instanceof Map)
                    addObjectRequest(new ObjectRequest((Map)el));
            }
        }
        
        o = map.get(ATT_PLAN_ARGUMENTS);
        if (o instanceof Map) {
           if ( _arguments == null ) 
               _arguments =  new Attributes<String, Object>();

            _arguments.putAll((Map)o);
        }

        // this is the old name, support it for old test files?
        o = map.get(ATT_PLAN_INTEGRATION_DATA);
        if (o instanceof Map) {
           if ( _arguments == null ) 
               _arguments =  new Attributes<String, Object>();

            _arguments.putAll((Map)o);
        }

		o = map.get(ATT_REQUEST_RESULT); 
        if(o instanceof Map) { 
       	  _result =  new ProvisioningResult((Map)o); 
        } 

    }

    /**
     * Utility to add values to a multi-valued attribute with
     * necessary coercion. This is generic and could go in Util.
     * Originally this would always coerce the target value to a List
     * but this creates XML clutter during the simplify phase where
     * most AttributeRequests are just for atomic values.
     * 
     * When nocase is true, the values are case insensitive.
     */
    static public Object addValues(Object something, Object toSomething,
                                   boolean nocase) {

        // collapse simple lists
        if (something instanceof List) {
            List lvalue = (List)something;
            int size = lvalue.size();
            if (size == 0)
                something = null;
            else if (size == 1) 
                something = lvalue.get(0);
        }

        if (something != null) {

            if (toSomething == null) {
                toSomething = something;
            }
            else if (!equals(something, toSomething, nocase)) {

                if (!(toSomething instanceof List)) {
                    // promote to a list
                    List newvalue = new ArrayList();
                    newvalue.add(toSomething);
                    toSomething = newvalue;
                }

                List lvalue = (List)toSomething;
                if (!(something instanceof List)) {
                    if (!contains(lvalue, something, nocase))
                        lvalue.add(something);
                }
                else {
                    for (Object o : (List)something) {
                        if (!contains(lvalue, o, nocase))
                            lvalue.add(o);
                    }
                }
            }
        }

        return toSomething;
    }

    /**
     * Backward compatibility for the Original signature before 
     * case insensitivity was added.
     */
    static public Object addValues(Object something, Object toSomething) {
        return addValues(something, toSomething, false);
    }

    /**
     * Utility to remove values from a multi-valued attribute.
     * Like addValues, used by Provisioner during plan compilation
     * and during plan application.
     *
     * When nocase is true, the values are case insensitive.
     */
    static public Object removeValues(Object something, Object fromSomething,
                                      boolean nocase) {

        if (something != null) {
            if (fromSomething instanceof List) {
                List lvalue = (List)fromSomething;

                if (something instanceof List)
                    removeAll(lvalue, (List)something, nocase);
                else
                    remove(lvalue, something, nocase);

                // collapse to null
                if (lvalue.size() == 0)
                    fromSomething = null;
            }
            else if (fromSomething != null) {
                // I guess it makes sense to have remove collapse a single
                // value to null
                if (something instanceof List) {
                    List lvalue = (List)something;
                    if (contains(lvalue, fromSomething, nocase))
                        fromSomething = null;
                }
                else if (equals(fromSomething, something, nocase))
                    fromSomething = null;
            }
        }
        return fromSomething;
    }

    /**
     * Backward compatibility for the Original signature before
     * case insensitivity was added.
     */
    static public Object removeValues(Object something, Object fromSomething) {
        return removeValues(something, fromSomething, false);
    }

    /**
     * Utility to remove values from a multi-valued attribute that
     * are not in a list.
     * When nocase is true, the values are case insensitive.
     */
    static public Object retainValues(Object something, Object fromSomething,
                                      boolean nocase) {
        if(something == null ) {
            return null;
        } else {
            if (fromSomething instanceof List) {
                List lvalue = (List)fromSomething;
                List retains = null;
                if (something instanceof List)
                    retains = (List)something;
                else {
                    retains = new ArrayList();
                    retains.add(something);
                }
                retainAll(lvalue, retains, nocase);

                // collapse to null
                if (lvalue.size() == 0)
                    fromSomething = null;
            }
            else if (fromSomething != null) {
                // I guess it makes sense to have retain collapse a single
                // value to null
                if (something instanceof List) {
                    List lvalue = (List)something;
                    if (!contains(lvalue, fromSomething, nocase))
                        fromSomething = null;
                }
                else if (!equals(fromSomething, something, nocase))
                    fromSomething = null;
            }
        }
        return fromSomething;
    }

    // Various case insensitivity helpers, might want to move these to Util?
    
    static public void removeAll(List list, List values, boolean nocase) {
        // iiqetn-274 - when list and values are the same list we can get
        // a ConcurrentModificationException while iterating through the values
        // list if we remove an item from list. If they are the same list then
        // just clear it since case doesn't matter.
        if (list == values) {
            list.clear();
            return;
        }

        if (nocase) {
            for (Object o : values)
                remove(list, o, nocase);
        }
        else {
            list.removeAll(values);
        }
    }

    static public void retainAll(List list, List values, boolean nocase) {

        if (nocase) {
            int max = list.size();
            int psn = 0;
            while (psn < max) {
                Object el = list.get(psn);
                boolean remove = false;
                if (el instanceof String)
                    remove = !contains(values, el, nocase);
                else
                    remove = !values.contains(el);

                if (remove) {
                    list.remove(psn);
                    max--;
                }
                else
                    psn++;
            }
        }
        else {
            // retainAll is "optional", better be an ArrayList!
            list.retainAll(values);
        }
    }

    static public void remove(List list, Object value, boolean nocase) {
        if (nocase && value instanceof String) {
            String svalue = (String)value;
            ListIterator it = ((List)list).listIterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof String && svalue.equalsIgnoreCase((String)o))
                    it.remove();
            }
        }
        else
            list.remove(value);
    }

    static public boolean contains(List list, Object value, boolean nocase) {
        boolean contains = false;
        if (nocase && value instanceof String) {
            String svalue = (String)value;
            for (Object o : list) {
                if (o instanceof String && svalue.equalsIgnoreCase((String)o)) {
                    contains = true;
                    break;
                }
            }
        }
        else 
            contains = list.contains(value);

        return contains;
    }

    static public boolean equals(Object o1, Object o2, boolean nocase) {
        boolean eq = false;
        if (nocase && o1 instanceof String && o2 instanceof String)
            eq = ((String)o1).equalsIgnoreCase((String)o2);
        else 
            eq = Util.nullSafeEq(o1, o2, true);
        return eq;
    }

    /**
     * Collapse unnecessary items from the plan. If null is returned
     * the plan itself was collapsed.
     */
    public ProvisioningPlan collapse(boolean includeNullSet) {

        ProvisioningPlan result = this;

        if (_accounts != null) {
            ListIterator<AccountRequest> it = _accounts.listIterator();
            while (it.hasNext()) {
                AccountRequest req = it.next();
                if (req.collapse(includeNullSet) == null)
                    it.remove();
            }
        }

        if (_objectRequests != null) {
            ListIterator<ObjectRequest> it = _objectRequests.listIterator();
            while (it.hasNext()) {
                ObjectRequest req = it.next();
                if (req.collapse(includeNullSet) == null)
                    it.remove();
            }
        }

        if (isEmpty())
            result = null;
        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ObjectRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Defines the operations that can be performed on a resource object.
     * If not specified the default is assumed to be Modify.
     *
     * Note that "Set" is included in this list only for a temporary
     * backward compatibility kludge when parsing old ObjectRequests
     * created by the RemediationCalculator for role certifications.
     * ObjectRequest.operation used to be a different enumeration but
     * the operation was always Set. This must be parsed and upgraded
     * to Modify.
     *
     * @ignore
     * Some of these are specific to accounts but you can't have
     * hierarchies of enumerations in Java so in order to have ObjectRequest
     * and AccountRequest share things we have to have one enum with
     * all possible object ops.
     */
    @XMLClass
    public static enum ObjectOperation {
        Create(MessageKeys.RESOURCE_OBJECT_OP_CREATE),
        Modify(MessageKeys.RESOURCE_OBJECT_OP_MODIFY),
        Delete(MessageKeys.RESOURCE_OBJECT_OP_DELETE),
        Disable(MessageKeys.RESOURCE_OBJECT_OP_DISABLE),
        Enable(MessageKeys.RESOURCE_OBJECT_OP_ENABLE),
        Unlock(MessageKeys.RESOURCE_OBJECT_OP_UNLOCK),
        Lock(MessageKeys.RESOURCE_OBJECT_OP_LOCK),
        // upgrade kludge, see aove
        Set(MessageKeys.RESOURCE_OBJECT_OP_SET);
        
        private String messageKey;

        private ObjectOperation(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    /**
     * The common representation of a request for one resource object.
     * This is subclassed by ObjectRequest and AccountRequest.
     * 
     * @ignore
     * In theory we should be able to just use this for all types
     * of object requests including accounts but there is too
     * much backward compatibility baggage around the AccountRequest class.
     *
     * Also note the seemingly gratuitous subclassing by ObjectRequest
     * since it contains nothing.  This is a (hopefully) temporary 
     * workaround for a problem with the XML serializer that doesn't like
     * two INLINE_LIST_UNQUALIFIED_PROPERTIES where one element is a 
     * subclass of the other, here this is:
     *
     *   @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
     *   public void setObjectRequests(List<ObjectRequest> reqs)
     *
     *   @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
     *   public void setAccountRequests(List<AccountRequest> reqs)
     *
     * Since these use UNQUALIFIED it has some sort of conflict
     * because AccountRequest extends ObjectRequest even though they
     * would appear under different element names.  That needs to be
     * fixed, but it's an old obscure piece of code so for now
     * I'm adding different subclasses.
     */
    @XMLClass
    public static abstract class AbstractRequest extends AbstractXmlObject {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        String _application;
        String _instance;
        String _type;
        String _nativeIdentity;
        ObjectOperation _op;
        Attributes<String,Object> _arguments;
        List<AttributeRequest> _attributes;
        List<PermissionRequest> _permissions;

        /**
         * Tracking ids are assigned by the master plan builder and are
         * used to associate request objects with something in another model.
         * Specifically they are used to associate plan items with
         * certification items.
         */
        String _trackingId;

        /**
         * The name of the IntegrationConfig that was used
         * to process this request. This is set after the plan
         * has been executed and the result plan is assembled.
         */
        String _targetIntegration;

        /**
         * Fine grained results for this account request.
         * These can be left behind by the connector if it 
         * processes account requests in different transactions and
         * some might succeed and others might fail. In this case a 
         * single top-level plan result does not carry enough information.
         *
         * Note that there is no formal model for partial success.
         * If the connector was able to process some AttributeRequests
         * but not others, the entire AccountRequest needs to be marked
         * as RETRY or FAILED. If the request is to be retried, the 
         * connector leaves private annotations in each AttributeRequest to 
         * determine what needs to be retried.
         */
        ProvisioningResult _result;

        /**
         * Transient property holding the original value of the _application 
         * property if a ProvisioningConfig mapping needed to be applied. This
         * is used only by ConnectorProxy when it maps names into and out of
         * calls to the Connector.
         */
        String _unmappedApplication;

        /**
         * Transient flag set during plan compilation to indicate that
         * this request can be removed because a matching request
         * was found in the pending ProvisioningRequest list. This is 
         * needed for ops other than Modify which we clean automatically
         * once they become empty, but Create/Delete/Disable and others
         * must have a matching pending request with the same op.
         */
        boolean _cleanable;

        /**
         * When expanding the entitlements for an assigned role that
         * has the allowMultipleAccounts option or accountSelectorRule, a different
         * AccountRequest is created for each required IT role. This has the name
         * of that role so it can be matched back to an AccountSelection
         * in the project.
         *
         * @ignore
         * This is relevant only for AccountRequest but since the 
         * comparison method isTargetMatch is defined up here, its
         * a lot simpler just to have the property here.
         */
        String _sourceRole;

        public static String ATT_ALLOW_DUPLICATE_ACCOUNTS = "allowDuplicateAccounts";

        /**
         * Account selection rule to use when automating target
         * account selection for this request. This is a transient
         * property that is not serialized to XML.
         * 
         * @ignore
         * This is relevant only for AccountRequest but since it is
         * closely related to _sourceRole it's simpler to just have
         * the property here.
         *
         * JsonIgnore is important here, JSON deserializing SailPointObjects seems
         * problematic.
         */
        @JsonIgnore
        Rule _accountSelectorRule;

        /**
         * Attribute used in combination with accountSelectorRule to provide
         * details on the role in with the selectorRule was configured
         */
        public static String ATT_SELECTOR_RULE_SRC = "selectorRuleSource";

        /**
         * A csv of assignment ids.
         * Used during plan compilation to keep track of the accounts
         * and entitlements expanded for each role. Since roles can share
         * the same accounts multiple ids must be allowed. This is modeled
         * as a csv because it looks much cleaner in the XML.
         * 
         * @ignore
         * This is relevant only for AccountRequest but since the compiler
         * tries to always use AbstractRequest it's easier to have it here.
         */
        String _assignmentIds;

        //////////////////////////////////////////////////////////////////////
        //
        // Constructor
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Must be implemented in the subclass to create the same
         * class of object from another.
         */
        abstract public AbstractRequest instantiate();

        public AbstractRequest() {
        }

        public AbstractRequest(Map map) {
            fromMap(map);
        }

        public AbstractRequest(AbstractRequest src) {
            clone(src);
        }

        /**
         * Copy the contents of one object into another.
         */
        public void clone(AbstractRequest src) {
            clone(src,false);
        }

        /**
         * Copy the contents of one object into another.
         * @param cloneResultErrors  (if true) it will clone provisioning result errors.
         */
        public void clone(AbstractRequest src, boolean cloneResultErrors) {
            if (src != null) {

                cloneRequestProperties(src, cloneResultErrors);

                List<AttributeRequest> srcatts = src._attributes;
                if (srcatts != null) {
                    for (int i = 0 ; i < srcatts.size() ; i++)
                        add(new AttributeRequest(srcatts.get(i)));
                }
                List<PermissionRequest> srcperms = src._permissions;
                if (srcperms != null) {
                    for (int i = 0 ; i < srcperms.size() ; i++)
                        add(new PermissionRequest(srcperms.get(i)));
                }
            }
        }

        /**
         * Clone one object from another. Note that this has to be named
         * cloneResourceRequest because there are other clone() methods in the
         * subclasses that return the subclass.
         *
         * @ignore
         * Unfortunately we can't use clone() here because that conflicts with
         * the method in the AccountRequest subclass which we have to continue
         * for backward compatibility.
         */
        public AbstractRequest cloneRequest() {
            return cloneRequest(false);
        }

        /**
         * Clone one object from another. Note that this has to be named
         * cloneResourceRequest because there are other clone() methods in the
         * subclasses that return the subclass.
         * @param cloneResultErrors  (if true) it will clone provisioning result errors.
         */
        public AbstractRequest cloneRequest(boolean cloneResultErrors) {
            // subclass knows how to make itself
            AbstractRequest neu = instantiate();
            neu.clone(this,cloneResultErrors);
            return neu;
        }

        /**
         * Copy target properties from one request to another but
         * not the attribute or permission requests.
         * Used in the implementation of plan itemization and cloning.
         */
        public void cloneRequestProperties(AbstractRequest src) {
            cloneRequestProperties(src,false);
        }
        /**
         * Copy target properties from one request to another but
         * not the attribute or permission requests.
         * Used in the implementation of plan itemization and cloning.
         * @param cloneResultErrors  (if true) it will clone provisioning result errors.
         * @ignore
         * Hate the name...
         */
        public void cloneRequestProperties(AbstractRequest src, boolean cloneResultErrors) {

            _application = src._application;
            _unmappedApplication = src._unmappedApplication;
            _instance = src._instance;
            _type = src._type;
            _nativeIdentity = src._nativeIdentity;
            _op = src._op;
            _trackingId = src._trackingId;
            _targetIntegration = src._targetIntegration;
            _sourceRole = src._sourceRole;
            _accountSelectorRule = src._accountSelectorRule;
            _assignmentIds = src._assignmentIds;

            if (src._arguments != null) {
                // shallow copy enough?
                _arguments = new Attributes<String,Object>(src._arguments);
            }

            // Historically we have also copied the requestID,
            // I'm not sure if that is required but be safe and bring over
            // the at least that.  Would it be safer to just get the entire
            // ProvisioningResult?
            ProvisioningResult srcres = src.getResult();
            if (srcres != null) {
                _result = new ProvisioningResult();
                _result.setRequestID(srcres.getRequestID());
                //IIQETN-6539 :- Cloning provisioning result as long as there are errors in
                //the provisioning.
                if (cloneResultErrors && srcres.isFailed() && !Util.isEmpty(srcres.getErrors())) {
                    _result.setErrors(new ArrayList<Message>(srcres.getErrors()));
                }
                // IIQSR-165 - we need the status when cloneResultErrors is true.
                if (cloneResultErrors) {
                    _result.setStatus(srcres.getStatus());
                }
            }
        }

        public void add(GenericRequest req) {
            if (req != null) {
                if (req instanceof AttributeRequest) {
                    if (_attributes == null)
                        _attributes = new ArrayList<AttributeRequest>();
                    _attributes.add((AttributeRequest)req);
                }
                else {
                    if (_permissions == null)
                        _permissions = new ArrayList<PermissionRequest>();
                    _permissions.add((PermissionRequest)req);
                }
            }
        }

        public void addAll(Collection<? extends GenericRequest> reqs) {
            if (reqs != null) {
                for(GenericRequest req : reqs){
                    add(req);
                }
            }
        }

        public void remove(GenericRequest req) {
            if (req != null) {
                if (req instanceof AttributeRequest) {
                    if (_attributes != null)
                        _attributes.remove(req);
                }
                else {
                    if (_permissions != null)
                        _permissions.remove(req);
                }
            }
        }

        public boolean isEmpty() {
            return ((_attributes == null || _attributes.size() == 0) &&
                    (_permissions == null || _permissions.size() == 0) &&
                    (_op == null || _op == ObjectOperation.Modify));
        }
        
        /**
         * Collapse attribute and permission requests from this account
         * or object request. If all items collapse we collapse.
         *
         * @ignore
         * Note that unlike isEmpty, we do not pay attention to the op
         * code.  This is intended for use in ProvisioningRequest and
         * other things that do plan mergers where we care about
         * the items, not create vs delete, or disable vs enable.
         */
        public AbstractRequest collapse(boolean includeNullSet) {
            
            AbstractRequest result = this;  
            
            if (_attributes != null) {
                ListIterator<AttributeRequest> it = _attributes.listIterator();
                while (it.hasNext()) {
                    AttributeRequest att = it.next();
                    if (att.collapse(includeNullSet) == null)
                        it.remove();
                }
            }

            if (_permissions != null) {
                ListIterator<PermissionRequest> it = _permissions.listIterator();
                while (it.hasNext()) {
                    PermissionRequest perm = it.next();
                    if (perm.collapse(includeNullSet) == null)
                        it.remove();
                }
            }

            // isEmpty pays attention to op code, in this case
            // we're not interested in that
            if ((_attributes == null || _attributes.size() == 0) &&
                (_permissions == null || _permissions.size() == 0))
                result = null;
            
            return result;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        @XMLProperty
        public String getApplication() {
            return _application;
        }
        
        public String getApplicationName() {
            return ProvisioningPlan.getApplicationDisplayName(getApplication());
        }

        public void setApplication(String s) {
            _application = s;
        }
        
        @XMLProperty
        public String getUnmappedApplication() {
            return _unmappedApplication;
        }

        public void setUnmappedApplication(String s) {
            _unmappedApplication = s;
        }

        /**
         * Convenience method to get the Application.
         */
        public Application getApplication(Resolver resolver)
            throws GeneralException {

            Application app = null;
            // isn't t his always name? if so we can remove this check
            // Will id always start with letter? I believe this is always by name anyways-rap
            if ( _application != null && _application.length() == 32 && Character.isDigit(_application.charAt(0) )) {                
                app = resolver.getObjectById(Application.class, _application);
            } else {
                // use cache
                app = resolver.getObjectByName(Application.class, _application);
            }                
            return app;
        }

        @XMLProperty
        public String getInstance() {
            return _instance;
        }

        public void setInstance(String instance) {
            _instance = instance;
        }

        @XMLProperty
        public String getType() {
            return _type;
        }
        
        public void setType(String s) {
            _type = s;
        }
        
        // sigh, I really wanted to call this just "name" but since
        // we're now inherited by AccountRequest it's easier to be backward
        // compatible and call this the unwieldy and redundant "nativeIdentity"
        @XMLProperty
        public String getNativeIdentity() {
            return _nativeIdentity;
        }

        public void setNativeIdentity(String id) {
            _nativeIdentity = id;
        }

        /**
         * The name of the role that needs this account, used only
         * when expanding roles that have the allowMultipleAccounts option or when account
         * selector rule present.
         */
        @XMLProperty
        public String getSourceRole() {
            return _sourceRole;
        }

        public void setSourceRole(String s) {
            _sourceRole = s;
        }

        /**
         * Get the account selection rule for this request.
         * This is transient, used only by AssignmentExpander.
         */
        public Rule getAccountSelectorRule() {
            return _accountSelectorRule;
        }

        //JsonIgnore is important here, JSON deserializing SailPointObjects seems problematic.
        @JsonIgnore
        public void setAccountSelectorRule(Rule rule) {
            _accountSelectorRule = rule;
        }

        /**
         * Set the operation to perform on this object.
         * 
         * For backward compatibility with AccountRequest there are
         * two operation enumerations, ObjectOperation and
         * AccountRequest.Operation with a corresponding set of property
         * methods. The values of the enumerations are identical and
         * methods that set AccountRequest.Operation will be converted
         * to ObjectOperations.
         *
         * @ignore
         * Note though that the property names are different, ObjectRequest
         * uses "op" and AccountRequest uses "operation".  This is
         * necessary to avoid having two methods named getOperation()
         * int the same class hierarchy that return different things.
         */
        @XMLProperty
        public void setOp(ObjectOperation op) {
            // prior to 5.5p1 and 6.0 this was a different enumeration and
            // always used Set.  This is parsed but we upgrade it to Modify.
            // see header comments above ObjectOperation for more
            if (op == ObjectOperation.Set)
                op = ObjectOperation.Modify;
            _op = op;
        }

        public ObjectOperation getOp() {
            return _op;
        }

        public Attributes<String, Object> getArguments() {
            return _arguments;
        }
        
        public Object getArgument(String name){
            Object obj = null;
            if(this.getArguments()!=null) {
                obj = this.getArguments().get(name);
            }
            return obj;
        }
        
        public void addArgument(String key, Object value) {
            if(_arguments==null) {
                _arguments = new Attributes<String,Object>();
            }
            _arguments.put(key, value);
        }
        
    
        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public void setArguments(Attributes<String, Object> atts) {
            _arguments = atts;
        }
        
        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<AttributeRequest> getAttributeRequests() {
            return _attributes;
        }

        public void setAttributeRequests(List<AttributeRequest> reqs) {
            _attributes = reqs;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<PermissionRequest> getPermissionRequests() {
            return _permissions;
        }

        public void setPermissionRequests(List<PermissionRequest> reqs) {
            _permissions = reqs;
        }

        @XMLProperty
        public String getTrackingId() {
            return _trackingId;
        }

        public void setTrackingId(String trackingId) {
            _trackingId = trackingId;
        }

        @XMLProperty
        public String getTargetIntegration() {
            return _targetIntegration;
        }

        public void setTargetIntegration(String name) {
            _targetIntegration = name;
        }


        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public ProvisioningResult getResult() {
            return _result;
        }
    
        public void setResult(ProvisioningResult r) {
            _result = r;
        }

        /**
         * Return true if it is marked for retry, or any one of the contained
         * AttributeRequests or PermissionRequests is marked for retry.
         */
        public boolean needsRetry() {
            if (null == _result || _result.isRetry()) {
                return true;
            }
            for (AttributeRequest atrReq : Util.iterate(_attributes)) {
                ProvisioningResult result = atrReq.getResult();
                if (null == result || result.isRetry()) {
                    return true;
                }
            }
            for (PermissionRequest perReq : Util.iterate(_permissions)) {
                ProvisioningResult result = perReq.getResult();
                if (null == result || result.isRetry()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isCleanable() {
            return _cleanable;
        }

        public void setCleanable(boolean b) {
            _cleanable = b;
        }

        /**
         * Get the assignment id csv.   
         */
        @XMLProperty
        public String getAssignmentIds() {
            return _assignmentIds;
        }

        public void setAssignmentIds(String ids) {
            _assignmentIds = ids;
        }

        /**
         * Get the assignment ids as a list.
         * Internally this is maintained as a csv for cleaner XML.
         */
        public List<String> getAssignmentIdList() {
            return Util.csvToList(_assignmentIds);
        }

        /**
         * Add an assignment id to the list.
         */
        public void addAssignmentIds(String ids) {
            if (!Util.isNullOrEmpty(ids)) {
                if (Util.isNullOrEmpty(_assignmentIds)) {
                    _assignmentIds = ids;
                } else {
                    Set<String> existingIds = Util.csvToSet(_assignmentIds, true);
                    List<String> newIds = Util.csvToList(ids);
                    for (String newId : Util.iterate(newIds)) {
                        if (!existingIds.contains(newId)) {
                            existingIds.add(newId);
                            _assignmentIds += "," + newId;
                        }
                    }
                }
            }
        }

        /**
         * Return true if an assignment id is on the list.
         * Since these are uuids separated by commas a substring
         * match is reliable.
         */
        public boolean hasAssignmentId(String id) {
            return (_assignmentIds != null && _assignmentIds.indexOf(id) >= 0);
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Utilities
        //
        //////////////////////////////////////////////////////////////////////

        public String getComments() {
            return getString(ARG_COMMENTS);
        }
        
        public void setComments(String c) {
            put(ARG_COMMENTS, c);
        }

        public void put(String name, Object value) {
            if (name != null) {
                if (_arguments == null)
                    _arguments = new Attributes<String,Object>();
                _arguments.putClean(name, value);
            }
        }

        public Object get(String name) {
            return (_arguments != null) ? _arguments.get(name) : null;
        }

        public String getString(String name) {
            return (_arguments != null) ? _arguments.getString(name) : null;
        }

        /**
         * Return true if the target object of this request matches
         * that of another.
         */
        public boolean isTargetMatch(AbstractRequest other) {

            boolean match = false;

            // apps must match and be non-null
            if (_application != null &&
                _application.equals(other.getApplication())) {

                String instance = other.getInstance();
                if ((instance == null && _instance == null) ||
                    (instance != null && instance.equals(_instance))) {

                    // if native ids are both null then also check sourceRole
                    // property, otherwise don't care about the sourceRole check
                    // and just check the native ids
                    String id = other.getNativeIdentity();
                    if (id == null && _nativeIdentity == null) {
                        //bug#20956, merge create requests and don't check sourceRole
                        if (ObjectOperation.Create.equals(getOp()) &&
                            ObjectOperation.Create.equals(other.getOp())) 
                        {
                            match = true;
                        } else {
                            //SourceRole must match
                            String role = other.getSourceRole();
                            if ((role == null && _sourceRole == null) ||
                                (role != null && role.equals(_sourceRole))) {
                                match = true;
                            }
                        }
                    } else if (id != null && id.equals(_nativeIdentity)) {
                        match = true;
                    }
                }
            }

            return match;
        }

        /**
         * Return true if the target object of this request matches
         * that of another.  This checks both the request and the attributes
         * of the request.
         */
        public boolean isMatch(AbstractRequest other) {
            boolean match = false;

            if (isTargetMatch(other)) {
                // Look at attributes to make sure they match.
                match = isAttributesMatch(other);
            }

            return match;
        }

        public boolean isAttributesMatch(AbstractRequest other) {
            boolean match = false;
            if (null != getAttributeRequests() && null != other.getAttributeRequests() &&
                    getAttributeRequests().size() == other.getAttributeRequests().size() &&
                    getAttributeRequests().containsAll(other.getAttributeRequests())) {
                match = true;
            }
            return match;
        }

        public AttributeRequest getAttributeRequest(String name) {

            return  getAttributeRequest(name, null);
        }

        public AttributeRequest getAttributeRequest(String name, Object value) {

            return  getRequest(_attributes, name, value);
        }

        /**
         * @ignore
         * In some cases there may be multiple attribute requests for the
         * same attribute in an account request.  These eventually be
         * collapsed, but at some points we need to retrieve all.
         */
        public List<AttributeRequest> getAttributeRequests(String name) {

            return  getRequests(_attributes, name);
        }
        
        public PermissionRequest getPermissionRequest(String target) {

            return  getRequest(_permissions, target);
        }

        /**
         * @ignore
         * In some cases there may be multiple permission requests for the
         * same target in an account request.  These eventually be
         * collapsed, but at some points we need to retrieve all.
         */
        public List<PermissionRequest> getPermissionRequests(String target) {

            return  getRequests(_permissions, target);
        }

        /**
         * @ignore
         * Check if request contains either of the below:-
         * multiple attribute requests 
         * multiple permission requests
         * one or more attribute requests and one or more permission requests
         */
        public boolean hasMultipleAttributesOrPermissions() {
            return ((_attributes != null && _attributes.size() > 1) ||
                    (_permissions != null && _permissions.size() > 1) ||
                    (_attributes != null && _attributes.size() >= 1) &&
                    (_permissions != null && _permissions.size() >= 1));
        }

        /**
         * @ignore
         * Check if request contains an attribute request or permission request
         * with Target Collector
         */
        public boolean containsTargetCollector () {
            boolean found = false;
            if (_attributes != null) {
                for (AttributeRequest req:_attributes) {
                    if (req.hasTargetCollector()) {
                        found = true;
                        break;
                    }
                }
            }
            if (found == false && _permissions != null) {
                for (PermissionRequest req:_permissions) {
                    if (req.hasTargetCollector()) {
                        found = true;
                        break;
                    }
                }
            }
            return found;
        }

        public <T extends GenericRequest> T getRequest(List<T> reqs, String name) {
            return getRequest(reqs, name, null);
        }

        /**
         * Find the request object from a list of request objects that matches the name and value
         * specified.  If the value is not specified, then just return the first object found that
         * matches the group.
         */
        public <T extends GenericRequest> T getRequest(List<T> reqs, String name, Object value) {

            List<T> found = getRequests(reqs, name);

            T request = null;
            if (null != value) {
                for (T req : found) {
                    if (value.equals(req.getValue())) {
                        request = req;
                        break;
                    }
                 }
            } else {
                request = (!found.isEmpty()) ? found.get(0) : null;
            }

            return request;
        }

        private <T extends GenericRequest> List<T> getRequests(List<T> reqs, String name) {
            
            List<T> found = new ArrayList<T>();
            if (reqs != null) {
                for (T req : reqs) {
                    if (name.equals(req.getName())) {
                        found.add(req);
                    }
                }
            }
            return found;
        }

        
        //////////////////////////////////////////////////////////////////////
        //
        // Map Transformation
        //
        //////////////////////////////////////////////////////////////////////

        public boolean isAccountRequest() {
            return false;
        }

        public boolean isGroupRequest() {
            // type is generally ommitted and defalts to "group"
            return ((_type != null && _type.equals(Application.SCHEMA_GROUP)) ||
                    (_type == null && !isAccountRequest()));
        }

        /**
         * Render the request in a generic Map/List model for JSON conversion.
         * Note that the ProvisioningResult is not included here.
         */
        public Map toMap() {
            Map map = new HashMap();

            map.put(ATT_OBJECT_APPLICATION, _application);
            map.put(ATT_OBJECT_INSTANCE, _instance);
            // kludge: suppress for AccountRequest
            if (!isAccountRequest() && _type != null)
                map.put(ATT_OBJECT_TYPE, _type);
            map.put(ATT_OBJECT_ID, _nativeIdentity);
            if (_op != null)
                map.put(ATT_OP, _op.toString());

            if (_attributes != null) {
                List list = new ArrayList();
                for (AttributeRequest req : _attributes) {
                    Map rmap = req.toMap();
                    if (rmap != null)
                        list.add(rmap);
                }

                map.put(ATT_OBJECT_ATTRIBUTES, list);
            }

            if (_permissions != null) {
                List list = new ArrayList();
                for (PermissionRequest req : _permissions) {
                    Map rmap = req.toMap();
                    if (rmap != null)
                        list.add(rmap);
                }
                map.put(ATT_OBJECT_PERMISSIONS, list);
            }

            if (_arguments != null)
                map.put(ATT_OBJECT_ARGUMENTS, (Map)_arguments);
			
            if(_result != null) 
              map.put(ATT_REQUEST_RESULT, _result.toMap()); 

            return map;
        }

        public void fromMap(Map map) {
            _application = Util.getString(map, ATT_OBJECT_APPLICATION);
            _instance = Util.getString(map, ATT_OBJECT_INSTANCE);
            _type = Util.getString(map, ATT_OBJECT_TYPE);
            _nativeIdentity = Util.getString(map, ATT_OBJECT_ID);
            Object o = map.get(ATT_OP);
            if (o != null)
                _op = Enum.valueOf(ObjectOperation.class, o.toString());

            o = map.get(ATT_OBJECT_ATTRIBUTES);
            if (o instanceof List) {
                List list = (List)o;
                if(!list.isEmpty()){
                    for (Object el : list) {
                        if (el instanceof Map)
                            add(new AttributeRequest((Map)el));
                    }
                }else{
                	//if list is empty then initializing empty list
                	_attributes=list;
                }
            }

            o = map.get(ATT_OBJECT_PERMISSIONS);
            if (o instanceof List) {
                List list = (List)o;
                for (Object el : list) {
                    if (el instanceof Map)
                        add(new PermissionRequest((Map)el));
                }
            }

            o = map.get(ATT_OBJECT_ARGUMENTS);
            if (o instanceof Map) {
                if (_arguments == null) 
                    _arguments =  new Attributes<String, Object>();
                _arguments.putAll((Map)o);
            }
            
    		o = map.get(ATT_REQUEST_RESULT); 
            if(o instanceof Map) { 
           	  _result =  new ProvisioningResult((Map)o); 
            } 
        }

        /**
         * Checks PermissionRequest and AttributeRequest for unstructured target
         * collector name. Here, the assumption is AccountRequest / ObjectRequest
         * contains either PermissionRequest or AttributeRequest.
         *
         * @return targetCollector
         */
        public String getTargetCollector() throws GeneralException {

            String targetCollector = null;
            if ( null != _permissions && _permissions.size() > 0 ) {
                // get unstructured target collector name from first
                // PermissionRequest
                targetCollector = _permissions.get(0).getTargetCollector();
            }
            else {
                if ( null != _attributes && _attributes.size() > 0 ) {
                    // get unstructured target collector name from first
                    // AttributeRequest
                    targetCollector = _attributes.get(0).getTargetCollector();
                }
            }

            return targetCollector;
        }

        public static AbstractRequest getLoggingRequest(AbstractRequest req) {
            List<AttributeRequest> atts = req.getAttributeRequests();
            if (atts != null) {
                for (AttributeRequest att : atts) {
                    if (att.isSecret()) {
                        att.setValue("********");
                    }

                    Attributes<String, Object> args = att.getArguments();
                    if (args != null) {
                        Iterator<String> keys = args.keySet().iterator();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (isSecret(key)) {
                                args.put(key, "********");
                            }
                        }
                    }
                }
            }
            return req;
        }
    }

    /**
     * @ignore
     * This is the ObjectRequest that we'll actually use, it just
     * extends AbstractRequest to avoid an XML serializer problem,
     * see comments about ResourceObject
     */
    public static class ObjectRequest extends AbstractRequest {

        //Attribute used to set noProvisioning. This will allow CRUD of the local MA,
        //but not send this to the integration.
        public static String ATT_OBJ_REQ_NO_PROVISIONING = "noProvisioning";

        public ObjectRequest() {
        }

        public ObjectRequest(Map map) {
            super(map);
        }

        public ObjectRequest(ObjectRequest src) {
            super(src);
        }

        /**
         * @ignore
         * Implementation of abstract method from ResourceRequest interface
         * so we can clone something without knowing what it is.
         */
        public AbstractRequest instantiate() {
            return new ObjectRequest();
        }

        /**
         * @ignore
         * clone() method for ObjectRequest 
         */
        public ObjectRequest clone() {
            return new ObjectRequest(this);
        }

        // Upgrades
        // In case we have some serialized objects that earlier versions of
        // RemediationCalculator created, upgrade the XML properties.  

        /**
         * @deprecated use {@link #setType(String)}
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.
         */
        @Deprecated
        @XMLProperty
        public void setTargetClass(String clazz) {
            setType(clazz);
        }
        
        public void setRequestID(String id) {
            if (id == null) {
                // don't bootstrap a result object if we don't have one yet
                if (_result != null)
                    _result.setRequestID(null);
            }
            else {
                if (_result == null)
                    _result = new ProvisioningResult();
                _result.setRequestID(id);
                // the status needs to make sense too
                _result.setStatus(ProvisioningResult.STATUS_QUEUED);
            }
        }
        

        public String getRequestID() {
            return ((_result != null) ? _result.getRequestID() : null);
        }
        
        /**
         * @deprecated use {@link #getType()}
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.
         */
        @Deprecated
        public String getTargetClass() {
            return null;
        }

        /**
         * @deprecated
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        @Deprecated
        @XMLProperty
        public void setTargetId(String targetId) {
        }

        /**
         * @deprecated
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        @Deprecated
        public String getTargetId() {
            return null;
        }

        /**
         * @deprecated use {@link #setNativeIdentity(String)}
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        @Deprecated
        @XMLProperty
        public void setTargetName(String targetName) {
            setNativeIdentity(targetName);
        }

        /**
         * @deprecated use {@link #getNativeIdentity()}
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        @Deprecated
        public String getTargetName() {
            return null;
        }

        /**
         * @deprecated use {@link #setType(String)} and {@link #setNativeIdentity(String)}
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        @XMLProperty
        public void setTargetReference(Reference ref) {
            if (ref != null) {
                setType(ref.getClassName());
                setNativeIdentity(ref.getName());
            }
        }

        /**
         * @deprecated
         * Temporary backward compatibility for parsing objects created with
         * older versions of RemediationCalculator.  This one we ignore and assume
         * that name will come in too.
         */
        public Reference getTargetReference() {
            return null;
        }
        public boolean hasNoAttributesOrPermissions() {
            return ((_attributes == null || _attributes.size() == 0) &&
                    (_permissions == null || _permissions.size() == 0));
        }

        /**
         * Expand an object request into multiple object requests
         * each containing single attribute request or single permission request
         * This is so that the ObjectRequest whose AttributeRequest or PermissionRequest contains "targetCollector" field gets routed to the 
         * pass-through proxy for the collector object for provisioning.
         */
        public  List<ObjectRequest> expandRequest ()  {
            
            List<ObjectRequest> expandedReqs = new ArrayList <ObjectRequest>();
            // Process attribute requests, create new object request containing just one attribute request.
            for (AttributeRequest attr : Util.iterate(_attributes)) {
                List<AttributeRequest> attrs = new ArrayList <AttributeRequest> ();
                attrs.add(attr);
                ObjectRequest objReq = this.clone();
                objReq.setAttributeRequests(attrs);
                objReq.setPermissionRequests(null);
                expandedReqs.add(objReq);
            }

            // Process permission requests, create new object request containing just one permission request.
            for (PermissionRequest perm : Util.iterate(_permissions)) {
                List <PermissionRequest> perms = new ArrayList <PermissionRequest> ();
                perms.add(perm);
                ObjectRequest objReq = this.clone();
                objReq.setPermissionRequests (perms);
                objReq.setAttributeRequests(null);
                expandedReqs.add(objReq);
            }
            return(expandedReqs);
        }

        /**
         * If type is not ManagedAttribute, we will assume an ObjectRequest is for a group.
         * @return true if the objectRequest is for an AccountGroup (Application Object)
         */
        @Override
        public boolean isGroupRequest() {
            //Older code will assume a null type on an object request is a group. I don't like this,
            //but we must support backward compatibility. - rap
            return (_type == null ||
                    (_type != null && !_type.equals(ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE)));
        }

        /**
         * Return true if the target object of this request matches
         * that of another.  This checks both the request and the attributes
         * of the request.
         *
         * @ignore
         *
         * iiqetn-49 - Overriding this method from AbstractRequest to account for ObjectRequests
         * that aren't associated with an application like adding or removing a role. The method
         * isTargetMatch() tests for a null application and returns false which can result in multiple
         * copies of the ObjectRequest added to the plan.
         */
        public boolean isMatch(AbstractRequest other) {
            boolean match = false;

            // iiqtc-49 - The application may be null for an object request such as
            // a request to remove a required IT role from a business role.
            if (_application == null && other.getApplication() == null) {
                if (_nativeIdentity.equalsIgnoreCase(other.getNativeIdentity()) &&
                        _type.equalsIgnoreCase(other.getType())) {
                            match = isAttributesMatch(other);
                }
            } else {
                if (isTargetMatch(other)) {
                    match = isAttributesMatch(other);
                }
            }

            return match;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // AccountRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Represents a request for one application account.
     * This exists for backward compatibility with many things prior to 6.0
     * but it is effectively nothing more than an ObjectRequest.
     *
     * Some deprecated methods are carried forward for XML upgrading.
     */
    @XMLClass
    public static class AccountRequest extends AbstractRequest {

        public static final String TYPE_ROLE = "role";
        public static final String TYPE_ENTITLEMENT = "entitlement";

        /**
         * Key in attributes map for attachments list
         */
        public static final String ATTACHMENTS = "attachments";

        /**
         * Key in attributes map for attachment overlay description
         */
        public static final String ATTACHMENT_CONFIG_LIST = "attachmentConfigList";

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Defines the operation to perform on the account. This values are
         * the same as ObjectRequest.Operation. Either can be used when 
         * building the request. This is for backward compatibility with
         * pre-6.0 code.
         *
         * @ignore
         * 
         * I couldn't find a way avoid duplicating this.  Old code often
         * does this:  Just inheriting
         *
         *   import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
         *
         * But this results in the error:
         *
         *   import requires canonical name forsailpoint.object.ProvisioningPlan.ObjectRequest.Operation
         *
         * Java enums cannot "subclass" like classes can so we cannot create
         * an empty one down here that extends the other.  Since the 
         * operation list almost never changes it is duplicated and
         * translation methods are provided.
         */
        @XMLClass(xmlname="AccountOperation")
        public static enum Operation {
            Create,
            Modify,
            Delete,
            Disable,
            Enable,
            Unlock,
            Lock;
        }

        /**
         * Temporary kludge for role assignment with multiple accounts.
         * If this flag is true, it means this AccountRequest was generated
         * by the role expander and tells PlanCompiler NOT to generate
         * pre-6.3 AccountSelections if the target account is ambiguous.
         * This should be removed by the time 6.3 is over.
         */
        boolean _roleExpansion;

        //////////////////////////////////////////////////////////////////////
        //
        // Constructors
        //
        //////////////////////////////////////////////////////////////////////

        public AccountRequest() {
        }

        public AccountRequest(Map map) {
            super(map);
        }

        public AccountRequest(AccountRequest src) {
            super(src);
        }

        /**
         * @ignore
         * ObjectRequest doesn't have this, but some older code may
         * still use it.
         */
        public AccountRequest(Operation op, String app, String inst, String id) {

            setOperation(op);
            setApplication(app);
            setInstance(inst);
            setNativeIdentity(id);
        }

        /**
         * Temporary transient flag meaning this request came from role expansion.
         * It is no serialized to xml.
         */
        public boolean isRoleExpansion() {
            return _roleExpansion;
        }

        public void setRoleExpansion(boolean b) {
            _roleExpansion = b;
        }

        /**
         * @ignore
         * Implementation of abstract method from ResourceRequest interface
         * so we can clone something without knowing what it is.
         */
        public AbstractRequest instantiate() {
            return new AccountRequest();
        }

        /**
         * @ignore
         * AccountRequest has historically had a clone() method. I don't
         * think we use it in system code but it's been around for a long time
         * so it may be used in custom code.
         */
        public AccountRequest clone() {
            return new AccountRequest(this);
        }

        /**
         * @ignore
         * Not sure who uses this, it wasn't factored up to 
         * ObjectRequest, so we need this?
         */
        public boolean hasNoAttributesOrPermissions() {
            return ((_attributes == null || _attributes.size() == 0) &&
                    (_permissions == null || _permissions.size() == 0));
        }

        /**
         * @ignore
         * Backward compatibility accessor for cloneRequestProperties.
         */
        public void cloneAccountProperties(AccountRequest src) {

            cloneRequestProperties(src);
        }

        /**
         * @ignore
         * Backward compatibility property that uses the older operation
         * enumeration.  This is functionally the same as ObjectRequest.setOp()
         */
        public void setOperation(Operation op) {
            if (op != null)
                setOp(Enum.valueOf(ObjectOperation.class, op.toString()));
        }

        /**
         * @ignore
         * Backward compatibility property that uses the older operation
         * enumeration.  This is functionally the same as 
         * ObjectRequest.getOp().
         */
        public Operation getOperation() {
            Operation op = null;
            ObjectOperation oop = getOp();
            if (oop != null)
                op = Enum.valueOf(Operation.class, oop.toString());
            return op;
        }

        /**
         * Causes object type to be suppressed from the Map representation.
         */
        public boolean isAccountRequest() {
            return true;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Upgrades
        //
        //////////////////////////////////////////////////////////////////////
        
        /**
         * Return the request ID if this request was queued.
         * 
         * @deprecated use {@link ProvisioningResult#getRequestID()} on {@link #getResult()}
         * 
         * @ignore
         * requestID used to be a first-class property, now it
         * is stored inside the ProvisioningResult.  Support the
         * old property for backward compatibility with 
         * IntegrationExecutors
         */
        @Deprecated
        public String getRequestID() {
            return ((_result != null) ? _result.getRequestID() : null);
        }

        /**
         * Set the request ID if this request was queued.
         * This is provided for backward compatibility with 
         * IntegrationExecutors. New provisioning Connectors should
         * not call this, instead build a suitable ProvisioningResult
         * object with the request id and status code.
         *
         * @deprecated use {@link ProvisioningResult#setRequestID(String)} on {@link #getResult()}
         */
        @Deprecated
        public void setRequestID(String id) {
            if (id == null) {
                // don't bootstrap a result object if we don't have one yet
                if (_result != null)
                    _result.setRequestID(null);
            }
            else {
                if (_result == null)
                    _result = new ProvisioningResult();
                _result.setRequestID(id);
                // the status needs to make sense too
                _result.setStatus(ProvisioningResult.STATUS_QUEUED);
            }
        }

        /**
         * @exclude
         * I'm not sure if we need to parse XML for old requests but
         * just in case we have to auto-convert what used to be an
         * XML element into something inside the ProvisioningResult.
         * @deprecated use {@link ProvisioningResult#setRequestID(String)} on {@link #getResult()} 
         */
        @Deprecated
        @SuppressWarnings("deprecation")
        @XMLProperty(xmlname="requestID")
        public void setXmlRequestID(String id) {
            setRequestID(id);
        }

        /**
         * @exclude
         * Parallel accessor for the xmlRequestID property upgrade.
         * @deprecated use {@link ProvisioningResult#getRequestID()} on {@link #getResult()} 
         */
        @Deprecated
        public String getXmlRequestID() {
            return null;
        }

        /**
         * @exclude
         * @deprecated Only used for JSON - use {@link #getArguments()} instead.
         */
        @Deprecated
        public Attributes<String, Object> getArgs() {
            return getArguments();
        }

        /**
         * @exclude
         * @deprecated Only used for JSON - use {@link #setArguments(Attributes)} instead.
         */
        @Deprecated
        public void setArgs(Attributes<String, Object> atts) {
            setArguments(atts);
        }

        /**
         * Expand an account request into multiple account requests
         * each containing single attribute request or single permission request
         * This is so that the AccountRequest whose AttributeRequest or PermissionRequest contains "targetCollector" field gets routed to the 
         * pass-through proxy for the collector object for provisioning.
         */
        public  List<AccountRequest> expandRequest ()  {
            
            List<AccountRequest> expandedReqs = new ArrayList <AccountRequest>();
            // Process attribute requests, create new account request containing just one attribute request.
            for (AttributeRequest attr : Util.iterate(_attributes)) {
                List<AttributeRequest> attrs = new ArrayList <AttributeRequest> ();
                attrs.add(attr);
                AccountRequest accReq = this.clone();
                accReq.setAttributeRequests(attrs);
                accReq.setPermissionRequests(null);
                expandedReqs.add(accReq);
            }

            // Process permission requests, create new account request containing just one permisison request.
            for (PermissionRequest perm : Util.iterate(_permissions)) {
                List <PermissionRequest> perms = new ArrayList <PermissionRequest> ();
                perms.add(perm);
                AccountRequest accReq =  this.clone();
                accReq.setPermissionRequests (perms);
                accReq.setAttributeRequests(null);
                expandedReqs.add(accReq);
            }
            return(expandedReqs);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Operation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Operation codes for attributes and permissions.
     * 
     * Set means to replace the current value (the default).
     * Add means to incrementally add something to the current value.
     * Remove means to incrementally remove something to the current value.
     * 
     * Revoke is used only during certification to indicate that a role
     * assignment should both be removed, and marked as permanently
     * revoked so the assignment rules don't put it back.
     *
     * Retain means to keep the attribute values if they exist but do not
     * add them if they do not exist. It is only used in high-level
     * plans, never in a compiled plan. The effect is to remove the value
     * from Remove or Revoke operations but not to leave Add operations
     * if one did not already exist.
     * 
     */
    @XMLClass
    public static enum Operation {
        Set(MessageKeys.PROVISIONING_PLAN_OP_SET),
        Add(MessageKeys.PROVISIONING_PLAN_OP_ADD),
        Remove(MessageKeys.PROVISIONING_PLAN_OP_REMOVE),
        Revoke(MessageKeys.PROVISIONING_PLAN_OP_REVOKE),
        Retain(MessageKeys.PROVISIONING_PLAN_OP_RETAIN);

        private String messageKey;

        private Operation(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // GenericRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An interface implemented by both AttributeRequest and PermissionRequest
     * so they can be treated in the same way for plan analysis
     * and compilation. In retrospect, it would have been better just
     * to use the same class for both of these. 
     *
     * The name/value is rendered differently in the subclasses
     * AttributeRequest calls these name/value and PermissionRequest
     * calls them target/rights. There is also so work done in
     * PermissionRequest to maintain the _value as a List<String>
     * for plan compilation but provide getTarget setTarget methods
     * that take a CSV string like is use in Permission objects.
     * 
     * The _targetCollector maintains the name of the target collector 
     * (aggregation source) used to aggregate the target permission. 
     * It is used to route the provisioning request to the same 
     * target collector during plan compilation.
     *
     */
    @XMLClass
    public static abstract class GenericRequest extends AbstractXmlObject {

        String _name;
        Object _value;
        String _displayValue;
        // JsonIgnore is important here, JSON deserializing SailPointObjects seems problematic.
        // Script has Rules in it.
        @JsonIgnore
        Script _script;
        Operation _op;
        String _trackingId;
        Attributes<String,Object> _arguments;
        String _targetCollector;

        /**
         * Fine-grained results for this attribute/permission.
         * This should be set only if the connector needs to execute
         * requests in different transactions that can succeed and fail 
         * independently. This is sometimes the case for the "password"
         * attribute and the group membership attribute.
         */
        ProvisioningResult _result;

        /**
         * Transient property holding the original value of the _name
         * property if we had to apply a ProvisioningConfig mapping. This
         * is used only by ConnectorProxy when it maps names into and out of
         * calls to the Connector.
         */
        String _unmappedName;

        /**
         * An assignment id for this request. This is relevant only for
         * operations on assignedRoles and detectedRoles attribute of the IdentityIQ identity.
         * 
         * @ignore
         * I'd rather it be a first class attribute than an argument since it will be
         * common and this results in less XML clutter.  Having this set prevents
         * the AttributeRequest from being merged with other requests for the same
         * attribute, the same as having an arguments map.
         */
        String _assignmentId;

        // must be implemented in the subclass
        abstract public GenericRequest instantiate();

        public GenericRequest() {
        }

        public GenericRequest(GenericRequest src) {
            clone(src);
        }

        /**
         * Copy the contents of one request to another.
         */
        public void clone(GenericRequest src) {
            if (src != null) {
                _name = src._name;
                _unmappedName = src._unmappedName;
                _op = src._op;
                _trackingId = src._trackingId;
                _displayValue = src._displayValue;
                _assignmentId = src._assignmentId;
                _targetCollector = src._targetCollector;

                // since we never modify the Scripts, this is safe 
                // to share, don't need a clone 
                _script = src._script;

                // value needs to be cloned if mutable
                // stopping at the List level since the thing we need
                // to guard against is the adding or removing of values
                // during partitioning, the actual values will not be modified
                if (src._value instanceof List) {
                    List srclist = (List)src._value;
                    _value = new ArrayList(srclist);
                }
                else {
                    _value = src._value;
                }

                // assume these are simple things that don't need
                // more than a shallow clone
                if (src._arguments != null)
                    _arguments = new Attributes<String,Object>(src._arguments);

                // we have always ignored ProvisioningResult since it's
                // a return from the Connecotr and we normally only clone
                // during plan compilation
            }
        }

        /**
         * Utility to clone a request from another.
         */
        public GenericRequest clone() {

            GenericRequest neu = this.instantiate();
            neu.clone(this);
            return neu;
        }

        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }
        
        @XMLProperty
        public String getUnmappedName() {
            return _unmappedName;
        }

        public void setUnmappedName(String s) {
            _unmappedName = s;
        }

        public String getDisplayValue() {
            return _displayValue;
        }
        
        public void setDisplayValue(String val) {
            _displayValue = val;
        }

        public Object getValue() {
            return _value;
        }

        public void setValue(Object o) {
            _value = o;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }

        @JsonIgnore
        public void setScript(Script s) {
            _script = s;
        }

        @XMLProperty
        public Operation getOp() {
            return _op;
        }

        public void setOp(Operation op) {
            _op = op;
        }

        @XMLProperty
        public String getTargetCollector() {
            return _targetCollector;
        }

        public void setTargetCollector(String targetCollector) {
            _targetCollector = targetCollector;
        }

        /**
         * Backward compatibility with the old property name
         * The standard property is now just "op" for consistency with
         * ObjectRequest.
         */
        public Operation getOperation() {
            return getOp();
        }

        /**
         * Backward compatibility with the old property name
         * The standard property is now just "op" for consistency with
         * ObjectRequest.
         */
        public void setOperation(Operation op) {
            setOp(op);
        }

        @XMLProperty
        public String getTrackingId() {
            return _trackingId;
        }

        public void setTrackingId(String id) {
            _trackingId = id;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public ProvisioningResult getResult() {
            return _result;
        }
    
        public void setResult(ProvisioningResult r) {
            _result = r;
        }

        /**
         * Utility to add values to a multi-valued attribute.
         * This is used by Provisioner during plan compilation to normalize
         * multiple requests for the same attribute. The given value
         * is normally a List but it can be an atomic value (usually
         * a String).
         */
        public void addValues(Object something, boolean nocase) {

            _value = ProvisioningPlan.addValues(something, _value, nocase);
        }

        /**
         * Utility to remove values from a multi-valued attribute.
         * Like addValues, used by Provisioner during plan normalization.
         */
        public void removeValues(Object something, boolean nocase) {
            _value = ProvisioningPlan.removeValues(something, _value, nocase);
        }

        /**
         * Utility to reatain only those values in a given list.
         * This one is used by Aggregator to filter plans in 
         * ProvisioningRequests.
         */
        public void retainValues(Object something, boolean nocase) {
            _value = ProvisioningPlan.retainValues(something, _value, nocase);
        }

        /**
         * Attribute and permission requests can have arguments
         * that influence how they are provisioned. Sunrise/sunset
         * dates are one example.  
         * @ignore
         * NOTE: This is not currently in the integration/common
         * model, it probably should be.
         */
        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public void setArguments(Attributes<String,Object> atts) {
            _arguments = atts;
        }
        
        public void setArgs(Attributes<String,Object> atts) {
            _arguments = atts;
        }
        
        public Attributes<String,Object> getArgs() {
            return _arguments;
        }
        
        public Attributes<String,Object> getArguments() {
            return _arguments;
        }

        /**
         * Return true if the Request contains targetCollector
         */
        public boolean hasTargetCollector() {
            return (Util.isNotNullOrEmpty(_targetCollector));
        }

        public void put(String name, Object value) {
            if (name != null) {
                if (_arguments == null)
                    _arguments = new Attributes<String,Object>();
                _arguments.putClean(name, value);
            }
        }

        public Object get(String name) {
            return (_arguments != null) ? _arguments.get(name) : null;
        }

        public Object remove(String name) {
            return (_arguments != null) ? _arguments.remove(name) : null;
        }

        public boolean getBoolean(String name) {
            return (_arguments != null) ? Util.otob(_arguments.get(name)) : false;
        }

        public String getString(String name) {
            return (_arguments != null) ? Util.otos(_arguments.get(name)) : null;
        }

        /**
         * Convenience accessor for the effective dates.
         */
        public void setAddDate(Date d) {
            put(ARG_ADD_DATE, d);
        }

        public Date getAddDate() {
            return (_arguments != null) ? _arguments.getDate(ARG_ADD_DATE) : null;
        }

        public void setRemoveDate(Date d) {
            put(ARG_REMOVE_DATE, d);
        }

        public Date getRemoveDate() {
            return (_arguments != null) ? _arguments.getDate(ARG_REMOVE_DATE) : null;
        }
        
        public String getComments() {
            return(_arguments!=null) ? _arguments.getString(ARG_COMMENTS) : null;
        }

        public void setComments(String c) {
            put(ARG_COMMENTS, c);
        }

        public void setLinkEdit(boolean b) {
            if (b)
                put(ARG_LINK_EDIT, "true");
            else
                remove(ARG_LINK_EDIT);
        }

        public boolean isLinkEdit() {
            return(_arguments!=null) ? _arguments.getBoolean(ARG_LINK_EDIT) : false;
        }

        public void setAssignment(boolean b) {
            if (b)
                put(ARG_ASSIGNMENT, "true");
            else
                remove(ARG_ASSIGNMENT);
        }

        public boolean isAssignment() {
            return (_arguments!=null) ? _arguments.getBoolean(ARG_ASSIGNMENT) : false;
        }

        public abstract boolean isSecret();

        @XMLProperty
        public void setAssignmentId(String id) {
            _assignmentId = id;
        }

        public String getAssignmentId() {
            return _assignmentId;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(_name)
                .append(_value)
                .append(_op).toHashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) { return true; }
            if (!(o instanceof GenericRequest)) { return false; }
            GenericRequest req = (GenericRequest) o;
            return new EqualsBuilder()
                .append(_name, req._name)
                .append(_value, req._value)
                .append(_op, req._op).isEquals();
        }

        /**
         * Collapse an attribute or permission request if it has become
         * a noop. Set requests are not collapsed unless the
         * includeNullSet flag is set since those could represent
         * "set to null" and are still relevant.
         */
        public GenericRequest collapse(boolean includeNullSet) {
            
            GenericRequest result = this;  
            if (_op != Operation.Set || includeNullSet) {
                if (_value == null ||
                    (_value instanceof Collection &&
                     ((Collection)_value).size() == 0))
                    result = null;
            }

            return result;
        }
        
        /**
         * Arguments are added during role assignment removal
         * that don't mean we can't simplify and combine
         * multiple requests.  During role assignment, there
         * are no arguments, so those plans are simplified regardless.
         * @return is it ok to simplify this if allowed.
         */
        public boolean okToSimplify() {
            boolean okToSimplify = false;
            if (_arguments != null && _arguments.containsKey(ProvisioningPlan.ARG_ALLOW_SIMPLIFICATION)) {
                okToSimplify = _arguments.getBoolean(ProvisioningPlan.ARG_ALLOW_SIMPLIFICATION);
            } else {
                okToSimplify = !(_arguments != null && _arguments.size() > 0);
            }
            
            //assignmentIds will always trump since simplify has a good chance of messing
            //up targets.
            okToSimplify &= (this.getAssignmentId() == null);
            
            return okToSimplify;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // AttributeRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Represents an operation on a single account attribute.
     *
     * A Script can be used if you need to calculate a value at runtime
     * rather than defining it statically. This is intended for plans
     * attached to roles, not the compiled plans passed to the
     * IntegrationExecutors.  
     * 
     * @ignore
     * We could take this further and allow
     * "scriptlets" in the _value like we do in the Workflow model
     * but this seems kind of overkill here since only scripts or rule
     * references are meaningful.  Still value='rule:foo' is a lot more
     * convenient than this:
     * <pre>
     *   &lt;Script>
     *     &lt;Source>
     *       import java.util.HashMap;
     *       import sailpoint.object.Rule;
     *       Rule rule = context.getObject(Rule.class, "foo");
     *       HashMap args = new HashMap();
     *       args.put("identity", identity);
     *       context.runRule(rule, args);
     *     &lt;/Source>
     *   &lt;/Script>
     * </pre>
     * I don't like over-using Rules now that we have inline Script
     * capabilities so we'll punt on "rule:foo" for now.
     */
    @XMLClass
    public static class AttributeRequest extends GenericRequest {

        public static final String ATT_DEASSIGN_ENTITLEMENTS = "deassignEntitlements";

        public static final String ATT_PREFER_REMOVE_OVER_RETAIN = "preferRemoveOverRetain";

        public AttributeRequest() {
        }

        public AttributeRequest(Map map) {
            fromMap(map);
        }

        public AttributeRequest(String name, Object value) {
            _name = name;
            _value = value;
        }

        public AttributeRequest(String name, Operation op, Object value) {
            _name = name;
            _op = op;
            _value = value;
        }
        
        public AttributeRequest(String name, Operation op, Object value, String assignmentId) {
            this(name, op, value);
            _assignmentId = assignmentId;
        }

        /**
         * Handy constructor for plan transformers.
         */
        public AttributeRequest(AttributeRequest src) {
            super(src);
        }

        //
        // GenericRequest interface
        //
        
        public GenericRequest instantiate() {
            return new AttributeRequest();
        }

        //
        // Pseudo properties for XML serialization
        // The inherited properties aren't XMLProperties so we have
        // more control over the names, don't need to change the
        // name here but we do in PermissionRequest.  Put the 
        // XML qualifier after the base property name so these 
        // continue to sort they would if they actually had the xmlname.
        //

        /**
         * @exclude
         * @deprecated use {@link #getName()}
         */
        @Deprecated
        @XMLProperty(xmlname="name")
        public String getNameXml() {
            return _name;
        }

        /**
         * @exclude
         * @deprecated use {@link #setName(String)}
         */
        @Deprecated
        public void setNameXml(String s) {
            _name = s;
        }

        //
        // This little dance let's us represent strings
        // using an XML element while complex values are
        // wrapped in a <value> element.  Not absolutely necessary
        // but it's what XML authors expect.
        //
        /**
         * @exclude
         * @deprecated use {@link #getValue()} 
         */
        @Deprecated
        @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="value")
        public String getValueXmlAttribute() {
            return (_value instanceof String) ? (String)_value : null;
        }

        /**
         * @exclude
         * @deprecated use {@link #setValue(Object)}
         */
        @Deprecated
        public void setValueXmlAttribute(String value) {
            _value = value;
        }

        /**
         * @exclude
         * @deprecated use {@link #getValue()}
         */
        @Deprecated
        @XMLProperty(xmlname="Value")
        public Object getValueXmlElement() {
            return (_value instanceof String) ? null : _value;
        }
        /**
         * @exclude
         * @deprecated use {@link #setValue(Object)}
         */
        @Deprecated
        public void setValueXmlElement(Object value) {
            _value = value;
        }

        /**
         * @exclude
         * @deprecated use {@link #getDisplayValue()}
         */
        @Deprecated
        @XMLProperty(xmlname="displayValue")
        public String getDisplayValueXml() {
            return this._displayValue;
        }

        /**
         * @exclude
         * @deprecated use {@link #setDisplayValue(String)}
         */
        @Deprecated
        public void setDisplayValueXml(String s) {
            this._displayValue = s;
        }

        @Override
        public boolean isSecret() {
            boolean secret = false;

            // if declared secret by the corresponding provisioning policy form field
            boolean declaredSecret = getBoolean(ProvisioningPlan.ARG_SECRET);

            // if the name looks secret-ish
            boolean appearsSecret = ProvisioningPlan.isSecret(getName());

            // consider it secret if deemed secret through either means
            secret = appearsSecret || declaredSecret;
            return secret;
        }

        //
        // utilities
        //

        /**
         * Returns attribute value. If the value is a reference, the referenced
         * object will be returned.
         *
         * @param ctx SailPointContext
         * @return Attribute value or null
         * @throws GeneralException
         */
        public Object getValue(SailPointContext ctx) throws GeneralException{
            if (_value != null && _value instanceof Reference){
                Reference ref = (Reference)_value;
                return ctx.getReferencedObject(ref.getClassName(), ref.getId(), ref.getName());
            } else {
                 return _value;
            }
        }

        public Map toMap() {
            Map map = new HashMap();
            map.put(ATT_ATTRIBUTE_NAME, _name);
            map.put(ATT_ATTRIBUTE_VALUE, _value);
            if (_op != null)
                map.put(ATT_OP, _op.toString());
            if (_arguments != null)
                map.put(ATT_REQUEST_ARGUMENTS, _arguments);
            if(_result != null) 
                map.put(ATT_REQUEST_RESULT, _result.toMap()); 
            return map;
        }

        public void fromMap(Map map) {
            _name = Util.getString(map, ATT_ATTRIBUTE_NAME);
            _value = map.get(ATT_ATTRIBUTE_VALUE);//Util.getString(map, ATT_ATTRIBUTE_VALUE);
            Object o = map.get(ATT_OP);
            if (o != null)
                _op = Enum.valueOf(Operation.class, o.toString());

            o = map.get(ATT_REQUEST_ARGUMENTS);
            if (o instanceof Map) {
                _arguments = new Attributes<String,Object>();
                _arguments.putAll((Map)o);
            }
            
    		o = map.get(ATT_REQUEST_RESULT); 
            if(o instanceof Map) { 
           	  _result =  new ProvisioningResult((Map)o); 
            } 
        }        
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PermissionRequest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Represents an operation on a single account permission.
     * 
     * @ignore
     * I thought about trying to merge this with AttributeRequest
     * like we do in some other places when dealing with both attributes
     * and permissions.  But I don't like the ambiguity in the model or
     * the silly permission='true' flag.  These aren't that big, just
     * duplicate them.  If it becomes annoying to manage two lists we
     * can merge them later.
     *
     * The target list is maintained in GenericRequest as a List<String>
     * which is what we need for plan compilation.  We provide
     * interfaces to access this as a CSV like we do for Permission
     * objects.
     */
    @XMLClass
    public static class PermissionRequest extends GenericRequest {

        public PermissionRequest() {
        }

        public PermissionRequest(Map src) {
            fromMap(src);
        }

        public PermissionRequest(String target, String rights) {
            _name = target;
            setRights(rights);
        }

        public PermissionRequest(String target, Operation op, String rights) {
            _name = target;
            _op = op;
            setRights(rights);
        }

        public PermissionRequest(String target, Operation op, List<String> rights) {
            _op = op;
            _name = target;
            _value = rights;
        }

        public PermissionRequest(String target, Operation op, List<String> rights, String targetCollector) {
            this(target, op, rights);
            if (!Util.isNullOrEmpty(targetCollector)) {
                _targetCollector = targetCollector;
            }
        }

        public PermissionRequest(PermissionRequest src) {
            super(src);
        }

        /**
         * @exclude
         * GenericRequest interface
         */
        public GenericRequest instantiate() {
            return new PermissionRequest();
        }

        // 
        // In XML we call name the "target" and value the "rights"
        //

        @XMLProperty
        public String getTarget() {
            return _name;
        }

        public void setTarget(String s) {
            _name = s;
        }

        @XMLProperty
        public String getRights() {
            String rights = null;
            if (_value instanceof List) {
                rights = Util.listToCsv((List)_value, true);
            }
            else if (_value != null) {
                // this would be unusual, someone must have called
                // setValue instead of setRights
                rights = _value.toString();
            }
            return rights;
        }

        public void setRights(String s) {
            if (s == null)
                _value = null;
            else 
                _value = Util.csvToList(s);
        }

        //
        // Older interface used before we forced the _value to
        // be a List<String>.  Still nice for coercion.
        //

        public List<String> getRightsList() {
            List<String> rights = null;
            if (_value instanceof List)
                rights = (List<String>)_value;
            else if (_value != null) {
                // unusual, should we promote it in place?
                rights = new ArrayList<String>();
                rights.add(_value.toString());
            }
            return rights;
        }

        /** 
         * Most places that call getRightsList will turn
         * around and pass that to Permission.setRights which
         * will sort the list. While this is usually harmless
         * it can cause test diffs if the plan gets changed as
         * a side effect of executing it so call this to 
         * ensure you get an independent list.
         */
        public List<String> getRightsListClone() {
            List<String> rights = getRightsList();
            return (rights != null) ? new ArrayList<String>(rights) : null;
        }

        public void setRightsList(List<String> list) {
            _value = list;
        }

        //
        // Map model
        //

        public Map toMap() {
            Map map = new HashMap();
            map.put(ATT_PERMISSION_TARGET, _name);
            map.put(ATT_PERMISSION_RIGHTS, getRights());
            if (_op != null)
                // Here we are inserting the String representation of _op enum
                // object into the Map.
                map.put(ATT_OP, _op.toString());
            if (_arguments != null)
                map.put(ATT_REQUEST_ARGUMENTS, _arguments);
            if(_result != null) 
                map.put(ATT_REQUEST_RESULT, _result.toMap()); 
            return map;
        }

        public void fromMap(Map map) {
            _name = Util.getString(map, ATT_PERMISSION_TARGET);
            setRights(Util.getString(map, ATT_PERMISSION_RIGHTS));
            Object o = map.get(ATT_OP);
            if (o != null)
                _op = Enum.valueOf(Operation.class, o.toString());

            o = map.get(ATT_REQUEST_ARGUMENTS);
            if (o instanceof Map) {
                _arguments = new Attributes<String,Object>();
                _arguments.putAll((Map)o);
            }
            
    		o = map.get(ATT_REQUEST_RESULT); 
            if(o instanceof Map) { 
           	  _result =  new ProvisioningResult((Map)o); 
            } 
        }

        /**
         * Returns false.  PermissionRequests cannot be secret
         * @return false,
         */
        @Override
        public boolean isSecret() {
            return false;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Result Normalization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if the plan was a complete success.
     * @ignore
     * We could probably do this in getNormalizedStatus but that's
     * being used for retry logic and it's complicated enough
     * as it is.
     */
    public boolean isFullyCommitted() {
        // if the plan has no result, the default status is queued
        String planStatus = ProvisioningResult.STATUS_QUEUED;
        if (_result != null && _result.getStatus() != null)
            planStatus = _result.getStatus();

        int accountFailures = 0;

        if (_accounts != null) {
            for (AccountRequest account : _accounts) {

                int itemFailures = 0;

                // If all the items succeed then the account request succeeds
                // unless it has an explciit result and an op that may do
                // more than what the AttributeRequests did (enable/disalbe).
                // This case is so obscure, I'm just assuming that if a local
                // result is provided it wins. 
                ProvisioningResult accountResult = account.getResult();
                if (accountResult != null && !accountResult.isCommitted()) {
                    accountFailures++;
                }
                else {
                    String accountStatus = planStatus;
                    if (accountResult != null && accountResult.getStatus() != null)
                        accountStatus = accountResult.getStatus();

                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        for (AttributeRequest att : atts) {
                            String itemStatus = accountStatus;
                            ProvisioningResult ires = att.getResult();
                            if (ires != null && ires.getStatus() != null)
                                itemStatus = ires.getStatus();
                        
                            if (!ProvisioningResult.STATUS_COMMITTED.equals(itemStatus)) {
                                itemFailures++;
                                // one is enough
                                break;
                            }
                        }
                    }

                    if (itemFailures == 0) {
                        List<PermissionRequest> perms = account.getPermissionRequests();
                        if (perms != null) {
                            for (PermissionRequest perm : perms) {
                                String itemStatus = accountStatus;
                                ProvisioningResult ires = perm.getResult();
                                if (ires != null && ires.getStatus() != null)
                                    itemStatus = ires.getStatus();
                        
                                if (!ProvisioningResult.STATUS_COMMITTED.equals(itemStatus)) {
                                    itemFailures++;
                                    break;
                                }
                            }
                        }
                    }

                    if (itemFailures > 0)
                        accountFailures++;
                    // else, AccountRequest didn't have an explicit result and all
                    // the items succeeded, let the account succeed regardless of
                    // what the plan result is
                }

                if (accountFailures > 0)
                    break;
            }
        }

        // It doesn't matter what the plan result says, if all accounts succeed
        // the plan succeeds

        return (accountFailures == 0);
    }

    /**
     * Calculate an "overall" result for the plan from the various 
     * levels of result objects the connector can leave in the plan.
     * The return value will mean:
     *
     *     FAILED
     *       - everything in the plan failed
     *
     *     RETRY
     *       - at least one thing in the plan needs a retry
     *
     *     QUEUED
     *       - at least one thing in the plan was COMMITTED or QUEUED,
     *         some things might be FAILED, and nothing was RETRY
     *
     * The first one is the most important because it means that nothing
     * was done and any further processing can be skipped. The second state
     * is important so you can know to launch a retry request.
     *
     * The last state is ambiguous and should only be used to mean that
     * something did not fail and further processing can be done. It does
     * not necessarily mean that everything was committed.
     *
     * It is possible for the connector to create some nonsensical results.
     * For example it could return AccountRequests that all have granular
     * results with FAILED, but an overall result with COMMITTED. In this
     * case the plan overall was a complete failure, even though the 
     * connector said it was not.
     *
     */
    public String getNormalizedStatus() {
     
        String normalized = null;

        if (_result != null)
            normalized = _result.getStatus();
        
        // Reconcile the overall result with the granular results
        if (_accounts != null && _accounts.size() > 0) {
            
            String defaultStatus = normalized;

            int failed = 0;
            int retry = 0;

            for (AccountRequest account : _accounts) {
                // start by inheriting the overall connector status
                String status = defaultStatus;
                ProvisioningResult childResult = account.getResult();
                if (childResult != null) {
                    status = childResult.getStatus();
                }

                if (ProvisioningResult.STATUS_FAILED.equals(status))
                    failed++;

                else if (ProvisioningResult.STATUS_RETRY.equals(status))
                    retry++;
                
                //else
                   // normalized = status;

                // let attribute/permission requests contribute
                int itemFailed = 0;
                int totalItems = 0;
                List<AttributeRequest> atts = account.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest att : atts) {
                        totalItems++;
                        ProvisioningResult itemResult = att.getResult();
                        if (itemResult != null) {
                            String itemStatus = itemResult.getStatus();
                            if (ProvisioningResult.STATUS_FAILED.equals(itemStatus))
                                itemFailed++;
                            if (ProvisioningResult.STATUS_RETRY.equals(itemStatus))
                                retry++;
                        }
                    }
                }

                List<PermissionRequest> perms = account.getPermissionRequests();
                if (perms != null) {
                    for (PermissionRequest perm : perms) {
                        totalItems++;
                        ProvisioningResult itemResult = perm.getResult();
                        if (itemResult != null) {
                            String itemStatus = itemResult.getStatus();
                            if (ProvisioningResult.STATUS_FAILED.equals(itemStatus))
                                itemFailed++;
                            if (ProvisioningResult.STATUS_RETRY.equals(itemStatus))
                                retry++;
                        }
                    }
                }

                // if all items failed, does the AccountRequest implicitly fail?
                // sigh this is messy...the AccountRequest Operation could have
                // succeeded (op=Disable for example) but the attributes all failed,
                // that doesn't mean the AccountRequest failed.  This is fringe case
                // require connectors to return AccountRequest results if there 
                // was total failure.
            }

            // if all accounts fail, the entire plan fails
            if (failed == _accounts.size())
                normalized = ProvisioningResult.STATUS_FAILED;

            else if (retry > 0)
                normalized = ProvisioningResult.STATUS_RETRY;

            else
                // we don't make a distinction beetween committed and queued
                normalized = ProvisioningResult.STATUS_QUEUED;            
        }
        
        // If there are ObjectRequests and status is 'retry', set to fail instead
        if (ProvisioningResult.STATUS_RETRY.equals(normalized) && !Util.isEmpty(_objectRequests)) {
            normalized = ProvisioningResult.STATUS_FAILED;
        }

        // if nothing was returned, the default is QUEUED
        if (normalized == null  )
            normalized = ProvisioningResult.STATUS_QUEUED;

        return normalized;
    }

    /**
     * Clear all former results in the plan.
     * This is normally done only when retrying the plan so that 
     * old results are not confused with what the connector did during the retry.
     */
    public void resetResults() {

        _result = null;

        if (_accounts != null) {
            for (AccountRequest account : _accounts) {
                account.setResult(null);
                
                List<AttributeRequest> atts = account.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest att : atts)
                        att.setResult(null);
                }

                List<PermissionRequest> perms = account.getPermissionRequests();
                if (perms != null) {
                    for (PermissionRequest perm : perms)
                        perm.setResult(null);
                }
            }
        }
    }    
    
    /**
     * Return a boolean if we have executed the project and it has nothing left 
     * to execute.
     * 
     * This is called during provisioning execution AND after retries to make 
     * sure that there are not any plans in the project that were skipped
     * because of dependencies went into retry.
     * 
     * Plan level results can be over-ridden by request level requests and 
     * attribute requests override the request level.
     * 
     * If a result is found somewhere in the plan it is assumed that
     * it has been executed.
     * 
     * Retries are the only case where parts of a plan were revisited
     * and in that case a derivative plan is build that includes
     * only the retryable parts.
     * 
     * @return true if any part of the plan has a result
     */
    public boolean hasBeenExecuted() {
        return ( new ResultStatistics(this).hasNonNullResult() ) ? true : false;
    }
    
    /**
     * Return true if the plan has a result at any level marked for
     * retry.
     * 
     * @return true if any of the results on the plan indicate retry is needed
     */
    public boolean needsRetry() {
        return ( new ResultStatistics(this).hasRetry() ) ? true : false;
    }
    
    /**
     * Simple model to help indicate if a plan has been 
     * executed (nonNull result) or needs retry. Keeping other
     * counters since they are useful.
     * 
     * @author dan.smith
     *
     */
    private class ResultStatistics {
        
        /**
         * Counter of non-null results, indicates that
         * the plan has been executed at least once.
         */
        int nonNull;
        
        /**
         * Number of results at any level that report retry.
         */
        int retry;
        
        /**
         * Number of results at any level that report committed.
         */        
        int commit;
       
        /**
         * Number of results at any level that report failure.
         */
        int fail;
        
        /**
         * Number of results at any level that report they were queued.
         */
        int queued;
        
        public ResultStatistics(ProvisioningPlan plan) {
            nonNull = 0;
            retry = 0;
            commit = 0;
            fail = 0;
            queued = 0;
        
            ProvisioningResult planResult = plan.getResult();       
            analyze(planResult);
                        
            List<AbstractRequest> reqs = plan.getAllRequests();
            if ( reqs != null ) {
                for ( AbstractRequest req : reqs ) {
                    if ( req != null ) {
                        analyze(req.getResult());                        
                        List<AttributeRequest> attrReqs = req.getAttributeRequests();
                        if ( attrReqs != null ) {
                            for ( AttributeRequest att : attrReqs ) {
                                if ( att == null )
                                    continue;
                                analyze(att.getResult());                                   
                            }
                        }
                        
                        List<PermissionRequest> permReqs = req.getPermissionRequests();
                        if ( permReqs != null ) {
                            for ( PermissionRequest perm : permReqs ) {
                                if ( perm == null ) 
                                    continue;
                                analyze(perm.getResult());
                            }
                        }                    
                    }
                }            
            }
        }
        
        private void analyze(ProvisioningResult result ) {
            if ( result != null ) {
                nonNull++;
                if ( result.isRetry() ) {
                    retry++;
                } else
                if ( result.isCommitted() ) {
                    commit++;
                } else
                if ( result.isFailed() || result.isFailure() )
                    fail++;
                else
                if ( result.isQueued() )
                    queued++;                
            }
        }
        
        /**
         * Returns true when one or more things in the plan
         * are marked for retry.
         * 
         * @return true if more than one result was found that indicated retry
         */
        public boolean hasRetry() {
            return ( retry > 0 ) ? true : false;
        }

        /**
         * Returns true when one or more things in the plan
         * have results that are non null.
         * 
         * @return true if more than one result was found that was non-null
         */
        public boolean hasNonNullResult() {
            return ( nonNull > 0 ) ? true : false;
        }        
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Secure Logging
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if the given name is a case-insensitive member of
     * {@link #getSecretProvisionAttributeNames()} result
     */
    public static boolean isSecret(String name) {
        if ( name != null ) {
            List<String> secrets = getSecretProvisionAttributeNames();
            for ( String secret : Util.iterate(secrets)) {
                if ( secret != null ) {
                    if ( name.equalsIgnoreCase(secret)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Return the list of strings for key "secretProvisionAttributeNames" from
     * cached SystemConfiguration Configuration object
     */
    public static List<String> getSecretProvisionAttributeNames() {
        return Configuration.getSystemConfig().getList(Configuration.SECRET_PROVISION_ATTRIBUTE_NAMES);
    }

    /**
     * Clone a plan removing passwords and other secret data.
     * This is intended for use when logging plans.  
     * 
     * @ignore
     * This isn't as generic
     * as it could be, we only look for AttributeRequest and their 
     * argument maps.
     */
    static public ProvisioningPlan getLoggingPlan(ProvisioningPlan src) {
        ProvisioningPlan filtered = null;
        if (src != null) {
            try {
                XMLObjectFactory xf = XMLObjectFactory.getInstance();
                filtered = (ProvisioningPlan)xf.clone(src, null);
                List<AbstractRequest> requests = filtered.getAllRequests();
                filtered.setAccountRequests(new ArrayList<AccountRequest>());
                filtered.setObjectRequests(new ArrayList<ObjectRequest>());
                if (requests != null) {
                    for (AbstractRequest req : requests) {
                        AbstractRequest fReq = AbstractRequest.getLoggingRequest(req);
                        filtered.addRequest(fReq);
                    }
                }
            }
            catch (Throwable t) {
                log.error("Unable to log plan: ", t); 
            }
        }
        return filtered;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // DEPRECATED
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Adds a simple attribute request for an application.
     * This does not attempt to simplify the plan.
     * You normally precede this with a call to add(appname,identity,op)
     * to build the containing AccountRequest with the right identity.
     * 
     * @deprecated  Use {@link #add(String, String, String, Operation, Object)}
     *    instead, which required a nativeIdentity.
     */
    @Deprecated
    public void add(String appname, String attname, Operation op, Object value) {
        add(appname, null, attname, op, value);
    }

    /**
     * @deprecated  Use {@link #add(String, String, String, Operation, Object)}
     *    instead, which required a nativeIdentity.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void add(String appname, String attname, Object value) {
        add(appname, attname, Operation.Add, value);
    }

    /**
     * @deprecated  Use {@link #add(String, String, String, Operation, Object)}
     *    instead, which required a nativeIdentity.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void remove(String appname, String attname, Object value) {
        add(appname, attname, Operation.Remove, value);
    }

    /**
     * @deprecated  Use {@link #add(String, String, String, Operation, Object)}
     *    instead, which required a nativeIdentity.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public void set(String appname, String attname, Object value) {
        add(appname, attname, Operation.Set, value);
    }
}
