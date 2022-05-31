/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.Reference;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * The ProvisioningChecker is in charge of looking at plans and checking
 * to see if the plan has been applied to an object.
 * 
 * This was re-factored from the RemediatinScanner so it could be leverage
 * by the LCMProvisioningScanner which needs to do something similar.
 * * 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class ProvisioningChecker {

    private static final Log log = LogFactory.getLog(ProvisioningChecker.class);
    
    private SailPointContext context;
    private IdentityService identityService;

    /**
     * Constructor.
     */
    public ProvisioningChecker(SailPointContext ctx) {
        this.context = ctx;
        identityService = new IdentityService(ctx);
    }

    public boolean hasBeenExecuted(ProvisioningPlan plan, ManagedAttribute group)
        throws GeneralException {

        // jsl - this was iterating over the AccountRequest list before which
        // was wrong so I'm not sure if this has ever been used
        List<ObjectRequest> reqs = plan.getObjectRequests();
        if (reqs != null) {
            for (ObjectRequest req : reqs) {
                if (req.getAttributeRequests() != null){
                    Schema schema = null;
                    Application app = group.getApplication();
                    if ( app != null )
                        schema = app.getGroupSchema();
                    for(ProvisioningPlan.AttributeRequest attrReq : req.getAttributeRequests()){
                        try {
                            if( !hasBeenExecuted( attrReq, group.getAttributes(), schema ) ) {
                                return false;
                            }
                        } catch (ProvisioningCheckerException pce) {
                            // catch more specific exception first and throw it again, since we are
                            // handling it further down the line.
                            throw pce;
                        } catch ( GeneralException ex ) {
                            /* Catch exception from hasBeenExecuted and wrap with some context */
                            String groupName = group.getDisplayableName();
                            throw new GeneralException( "Problem processing plan for " + groupName, ex );
                        }
                    }
                }
                if (req.getPermissionRequests() != null){
                    for(ProvisioningPlan.PermissionRequest permReq : req.getPermissionRequests()){
                        if (!hasBeenExecuted(permReq, group.getAllPermissions()))
                            return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean hasBeenExecuted(ProvisioningPlan plan, Bundle bundle)
        throws GeneralException {

        if (plan.getObjectRequests() != null){
            for(ProvisioningPlan.ObjectRequest request : plan.getObjectRequests()){
                if (request.getAttributeRequests() != null){
                    for(ProvisioningPlan.AttributeRequest req : request.getAttributeRequests()){
                        if (!hasBeenExecuted(req, bundle))
                            return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean hasBeenExecuted(ProvisioningPlan.AttributeRequest request, Bundle role)
        throws GeneralException {

        if (role != null && request != null){
            switch (request.getOperation()){
            case Remove:
                return !hasProperty(role, request.getName(), (Reference)request.getValue());
             case Add:
                return hasProperty(role, request.getName(), (Reference)request.getValue());
            default:
                throw new RuntimeException("Unhandled operation " + request.getOperation());
            }
        }

        return true;
    }

    private boolean hasProperty(Bundle role, String property, Reference value){
        if (role != null){
            if (ProvisioningPlan.ATT_IIQ_ROLE_CHILD.equals(property)){
                if (role.getInheritance() == null)
                    return false;
                for(Bundle inherited : role.getInheritance()){
                    if (inherited.getId().equals(value.getId()))
                        return true;
                }
            } else if (ProvisioningPlan.ATT_IIQ_ROLE_PROFILES.equals(property)){
                if (role.getProfiles() == null)
                    return false;
                for (Profile profile : role.getProfiles()){
                    if (profile.getId().equals(value.getId()))
                        return true;
                }
            } else if (ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_SCOPE.equals(property)){
                return roleGrantsProperty(role, Certification.IIQ_ATTR_SCOPES, value.getId());
            } else if (ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_CAPABILITY.equals(property)){
                return roleGrantsProperty(role, Certification.IIQ_ATTR_CAPABILITIES, value.getName());
            } else if (ProvisioningPlan.ATT_IIQ_ROLE_REQUIREMENT.equals(property)){
                if (role.getRequirements() == null)
                    return false;
                for(Bundle required : role.getRequirements()){
                    if (required.getId().equals(value.getId())){
                        return true;
                    }
                }
            } else if (ProvisioningPlan.ATT_IIQ_ROLE_PERMIT.equals(property)){
                if (role.getPermits() == null)
                    return false;
                for(Bundle permitted : role.getPermits()){
                    if (permitted.getId().equals(value.getId())){
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean roleGrantsProperty(Bundle role, String propertyName, String propertyValue){
        if (role.getProvisioningPlan() == null)
            return false;
        ProvisioningPlan.AccountRequest iiqReq =
                role.getProvisioningPlan().getIIQAccountRequest();
        if (iiqReq == null)
            return false;
        for(ProvisioningPlan.AttributeRequest attrReq : iiqReq.getAttributeRequests()){
            if (attrReq.getName().equals(propertyName)){
                if (attrReq.getValue() != null && ((List)attrReq.getValue()).contains(propertyValue))
                    return true;
            }
        }

        return false;
    }

    /**
     * Check to see if the given ProvisioningPlan has been executed based on the
     * current state of the identity.
     */
    public boolean hasBeenExecuted(ProvisioningPlan plan, Identity identity)
        throws GeneralException {
    
        // If the plan is null, we'll say it has been executed.
        if (null == plan) {
            return true;
        }
        
        boolean hasBeenExecuted = true;

        List<ProvisioningPlan.AccountRequest> accountRequests = plan.getAccountRequests();
        if (null != accountRequests) {
            for (ProvisioningPlan.AccountRequest accountReq : accountRequests) {

                // Default to a modify request.  We could throw here.
                ProvisioningPlan.AccountRequest.Operation op =
                    (null != accountReq.getOperation()) ? accountReq.getOperation()
                                                        : ProvisioningPlan.AccountRequest.Operation.Modify;

                switch (op) {
                case Modify:
                    if(null != identity) {
                        if (!hasBeenExecuted(accountReq, identity)) {
                            hasBeenExecuted = false;
                        }
                    } else {
                        if(onlyRemoveRequests(accountReq)) {
                            //we can safely assume this request was satisfied
                            hasBeenExecuted = true;
                        } else {
                            log.warn("Identity removed prior to confirmation of provisioning! " + accountReq.toXml());
                            hasBeenExecuted = false;
                        }
                    }
                    break;

                case Delete:
                    Link account = null;
                    if(null != identity) {
                        account = getLink(identity, accountReq);
                        if (null != account) {
                            hasBeenExecuted = false;
                        }
                    } else {
                        //no identity, no link.
                        hasBeenExecuted = true;
                    }
                    break;

                case Disable:
                case Enable:
                    boolean expectedDisabled =
                        ProvisioningPlan.AccountRequest.Operation.Disable.equals(op);
                    Application app = accountReq.getApplication(this.context);
                    if ((null != app) && app.supportsFeature(Application.Feature.ENABLE)) {
                        if(null != identity) {
                            account = getLink(identity, accountReq);
                            if ((null != account) && (expectedDisabled != account.isDisabled())) {
                                hasBeenExecuted = false;
                            }
                        } else {
                            log.warn("Identity removed prior to confirmation of provisioning! " + accountReq.toXml());
                            hasBeenExecuted = false;
                        }
                    }
                    else {
                        if (log.isWarnEnabled())
                            log.warn("Could not check account disable/enable on app: " + 
                                     accountReq.getApplication());
                    }
                    break;

                case Lock:
                case Unlock:
                    boolean expectedLocked =
                        ProvisioningPlan.AccountRequest.Operation.Lock.equals(op);
                    app = accountReq.getApplication(this.context);
                    if ((null != app) && app.supportsFeature(Application.Feature.UNLOCK)) {
                        if(null != identity) {
                            account = getLink(identity, accountReq);
                            if ((null != account) && (expectedLocked != account.isLocked())) {
                                hasBeenExecuted = false;
                            }
                        } else {
                            log.warn("Identity removed prior to confirmation of provisioning! " + accountReq.toXml());
                            hasBeenExecuted = false;
                        }
                    }
                    else {
                        if (log.isWarnEnabled())
                            log.warn("Could not check account lock/unlock on app: " + 
                                     accountReq.getApplication());
                    }
                    break;
                 case Create:
                    if(null != identity) {
                        Link createAcct =
                            identityService.getLink(identity, accountReq.getApplication(context),
                                             accountReq.getInstance(),
                                             accountReq.getNativeIdentity());
                        if (null == createAcct || !hasBeenExecuted(accountReq, identity)) {
                            hasBeenExecuted = false;
                        }
                    } else {
                        log.warn("Identity removed prior to confirmation of provisioning! " + accountReq.toXml());
                        hasBeenExecuted = false;
                    }
                    break;
                default:
                    throw new RuntimeException("Cannot detect " + op + " changes.");
                }
            }
        }
        
        return hasBeenExecuted;
    }

    /**
     * Return the Link on the given identity for this account request.
     */
    private Link getLink(Identity identity,
                         ProvisioningPlan.AccountRequest accountReq)
        throws GeneralException {

        return identityService.getLink(identity, accountReq.getApplication(context),
                                        accountReq.getInstance(),
                                        accountReq.getNativeIdentity());
    }
    
    /**
     * Check whether the given account request has been executed.
     */
    private boolean hasBeenExecuted(ProvisioningPlan.AccountRequest acctReq,
                                    Identity identity)
        throws GeneralException {

        boolean hasBeenExecuted = true;

        String application = acctReq.getApplication();
        if (ProvisioningPlan.isIIQ(application)) {
            hasBeenExecuted = hasIIQRequestBeenExecuted(acctReq, identity);
        }
        else {
            hasBeenExecuted = hasLinkRequestBeenExecuted(acctReq, identity);
        }

        return hasBeenExecuted;
    }
    
    /**
     * Check if the attribute and permission requests have been executed on a
     * the IdentityIQ identity.
     */
    private boolean hasIIQRequestBeenExecuted(ProvisioningPlan.AccountRequest acctReq,
                                              Identity identity)
        throws GeneralException {

        List<ProvisioningPlan.AttributeRequest> attrReqs =
            acctReq.getAttributeRequests();

        if (null != attrReqs) {
            // Fake up an attributes map that has the capabilities and scopes as
            // we expect them in the provisioning plan.  These correspond to the
            // attributes that are included in the certification via
            // BaseIdentityCertificationContext.getIdentityCertifiables().
            Attributes<String,Object> attrs =
                identity.getCapabilityManager().createCapabilitiesAttributes();
            Attributes<String,Object> scopeAttrs =
                identity.createEffectiveControlledScopesAttributes(this.context.getConfiguration());
            attrs.putAll(scopeAttrs);

            for (ProvisioningPlan.AttributeRequest attrReq : attrReqs) {
                if ( (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrReq.getName())) ||
                     (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName())) ) {
                    if( !checkRoleMembership( identity, attrReq ) ) {
                        return false;
                    }
                } else {
                    try {
                        if( !hasBeenExecuted( attrReq, attrs, null ) ) {
                            return false;
                        }
                    } catch (ProvisioningCheckerException pce) {
                        // catch more specific exception first and throw it again, since we are
                        // handling it further down the line.
                        throw pce;
                    } catch( GeneralException ex ) {
                        /* Catch exception from hasBeenExecuted, wrap, rethrow */
                        String identityName = identity.getDisplayableName();
                        throw new GeneralException( "Problem processing AccountRequest for " + identityName, ex );
                    }
                }
            }
        }

        return true;
    }

    private boolean checkRoleMembership(Identity identity,  
                                        ProvisioningPlan.AttributeRequest attrReq) 
        throws GeneralException {

        boolean hasBeenExecuted = true;        
        List<String> assigned = new ArrayList<String>();
        
        List<Bundle> roles = null;
        if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrReq.getName())){
            roles = identity.getAssignedRoles();
        } else
        if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName())){
            roles = identity.getDetectedRoles();
        }        
        if ( Util.size(roles) > 0 ) {
            for ( Bundle role : roles ) {
                assigned.add(role.getName());
            }
        }
        
        List<String> values = Util.asList(attrReq.getValue());
        if ( values == null ) values = new ArrayList<String>();
        
        if ( attrReq.getOperation().equals(ProvisioningPlan.Operation.Remove) ||
             attrReq.getOperation().equals(ProvisioningPlan.Operation.Revoke) ) {

            if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName())) {
                //Detected Roles may still be on the identity after refresh regardless of provisioning, if the entitlements
                //are still granted elsewhere.
                if (Util.isNotNullOrEmpty(attrReq.getAssignmentId())) {
                    RoleAssignment assignment = identity.getRoleAssignmentById(attrReq.getAssignmentId());
                    if (assignment != null) {
                        //Make sure the PermittedRoles have all been removed from the assignment
                        for (String roleName : Util.iterate(values)) {
                            hasBeenExecuted &= (assignment.getPermittedRole(null, roleName) == null);
                        }
                    }
                    //No Role assignment, nothing to remove. Call it good.
                } else {
                    //Not sure if we can make it here anymore. Need to mirror IIQEvaluator.reconcileOldRoleRequests
                    List<Bundle> attrRoles = ObjectUtil.getObjects(context,
                            Bundle.class,
                            values,
                            // trust rules
                            true,
                            // throw exceptions
                            false,
                            // convert CSV to List
                            false);

                    for (Bundle b : Util.safeIterable(attrRoles)) {
                        hasBeenExecuted &= (identity.getRoleRequest(b) == null);
                    }
                }

            } else {
                if ( containsAny(assigned, values) ) {
                    hasBeenExecuted = false;
                }
            }
        } else {
            if ( !assigned.containsAll(values) ) {
                hasBeenExecuted = false;
            }
        }        
        return hasBeenExecuted;
    }

    /**
     * Check if the attribute and permission requests have been executed on an
     * identity.
     * 
     * If the attrRequests are all Remove requests and the Link has been removed 
     * its considered executed.
     * 
     * Otherwise, check ALL links on an identity for an app to verify the 
     * execution.
     */
    private boolean hasLinkRequestBeenExecuted(ProvisioningPlan.AccountRequest acctReq,
                                               Identity identity)
        throws GeneralException {

        boolean hasBeenExecuted = true;
        
        Application app = acctReq.getApplication(context);
        List<Link> links = null;

        // Try to find the exact link that would be affected.
        if (null != acctReq.getNativeIdentity()) {
            // Consider looking for the link with a case-insensitive search.
            // The rest of the code uses case-sensitive, but this may not quite
            // work if the case in the nativeIdentity gets changed (for example
            // during an account creation).
            Link link = identityService.getLink(identity, app, acctReq.getInstance(), acctReq.getNativeIdentity());
            if (null != link) {
                links = new ArrayList<Link>();
                links.add(link);
            }
        }

        // Could not find a link by nativeIdentity.  This could be due to slight
        // differences in the nativeIdentity after a create (for example, spaces
        // in a DN, etc...).  Instead, look at all links.  At some point we
        // might want to add a "strict" mode that causes an error here rather
        // than looking at all links on the app b/c this could produce false
        // positives.
        //
        // Bug 12133 - let's use a strict mode when dealing with remediations
        if (null == links && !onlyRemoveRequests(acctReq)) {
            links = identityService.getLinks(identity, app);
        }

        // If the request is all remove attribute requests and the link
        // is missing assume its been executed
        if ( ( onlyRemoveRequests(acctReq) ) && ( Util.size(links) == 0 ) ) {
            return true;
        }
        // Go through all of the links and see if the plan was satisified
        // by any of the links
        if (Util.size(links) > 0 ) {
            for ( Link link : links ) {
                if ( hasBeenExecuted = checkLink(link, acctReq) ) {
                    break;    
                }
            }
        } else {
            hasBeenExecuted = false;
        }
        return hasBeenExecuted;
    }

    /**
     * Check a single link for the Attribute and Permission requests
     * found in the AccountRequest.
     */
    private boolean checkLink(Link link, ProvisioningPlan.AccountRequest acctReq) 
        throws GeneralException {
 
        List<ProvisioningPlan.AttributeRequest> attrReqs =
            acctReq.getAttributeRequests();

        if ( link != null ) {
            if (null != attrReqs) {
                Application app = link.getApplication();
                Schema schema = null;
                if ( app != null ) 
                    schema = app.getAccountSchema();
                for (ProvisioningPlan.AttributeRequest attrReq : attrReqs) {
                    try {
                        if( !hasBeenExecuted( attrReq, link.getAttributes(), schema ) ) {
                            return false;
                        }
                    } catch (ProvisioningCheckerException pce) {
                        // catch more specific exception first and throw it again, since we are
                        // handling it further down the line.
                        throw pce;
                    } catch ( GeneralException ex ) {
                        /* Catch exception from hasBeenExecuted, wrap with context then rethrow */
                        Identity identity = link.getIdentity();
                        String identityName = identity != null ? identity.getDisplayableName() : "null Identity";
                        String applicationName = link.getApplicationName() != null ? link.getApplicationName() : "null Application";
                        throw new GeneralException( "Problem processing link for " + identityName + " on " + applicationName, ex );
                    }
                }
            }
    
            List<ProvisioningPlan.PermissionRequest> permReqs =
                acctReq.getPermissionRequests();
            if (null != permReqs) {
                for (ProvisioningPlan.PermissionRequest permReq : permReqs) {
                    List<Permission> perms = new ArrayList<>();
                    if (Util.isNotNullOrEmpty(permReq.getTargetCollector())) {
                        //TargetPermission
                        perms = ObjectUtil.getTargetPermissions(context, link);
                    } else {
                        perms = link.getPermissions();
                    }

                    if (!hasBeenExecuted(permReq, perms)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Check whether the given attribute request has been executed.
     *
     * @param attrReq The Attribute Request
     * @param attrs
     * 
     * @return True if the AttributeRequest seems to have been processed, else false
     * @throws GeneralException If the AttributeRequest operation is Add and 
     * the attribute to add is null 
     */
    private boolean hasBeenExecuted(ProvisioningPlan.AttributeRequest attrReq,
                                    Attributes<String,Object> attrs,
                                    Schema schema)
        throws GeneralException {
        
        boolean hasBeenExecuted = true;
        
        ProvisioningPlan.Operation op = attrReq.getOperation();
        if (null == op) {
            op = ProvisioningPlan.Operation.Remove;
        }

        Object currentVal = null;
        if (null != attrs) {
            currentVal = normalizeValue(attrReq, attrs.get(attrReq.getName()), schema );
        }

        switch (op) {
        case Revoke:
            // for entitlements, this is the same as remove
        case Remove:
            // Not executed if the value is still there.
            if ( null == attrReq.getValue()) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " +
                              "'Remove' in an attribute request: " + attrReq.toXml());
                
                ProvisioningCheckerException pce = new ProvisioningCheckerException("Expected a non-null value to 'Remove' in the attribute request.");
                pce.attributeRequest = attrReq;
                pce.requestAttrValueIsNull = true;
                throw pce;
            }
            
            if (containsAny(currentVal, attrReq.getValue())) {
                hasBeenExecuted = false;
            }
            break;

        case Add:
            // Check if the current value contains the requested value.
            List currentList = Util.asList(currentVal);
            
            Object added = attrReq.getValue();
            if ( null == added ) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " +
                              "'Add' in an attribute request: " + attrReq.toXml());
                
                ProvisioningCheckerException pce = new ProvisioningCheckerException("Expected a non-null value to 'Add' in the attribute request.");
                pce.attributeRequest = attrReq;
                pce.requestAttrValueIsNull = true;
                throw pce;
            }
            List addedList = Util.asList(added);
            // Not added if the list is null, it is not contained (if a single
            // value), or all values are not contained (if multi-valued).
            if ( (null == currentList) || !(containsAllIgnoreCase(currentList,addedList)) )  {
                hasBeenExecuted = false;
            }
            
            break;

        case Set:
            //We are not going to worry if a value has been set to null because
            //it is allowed.  If we had thrown an exception, completion would never
            //be calculated when setting an attribute value to null.
//-            if(null == attrReq.getValue()) {
//-                if (log.isInfoEnabled())
//-                    log.info("There was a null value on operation: " +
//-                              "'Set' in an attribute request: " + attrReq.toXml());
//-                
//-                ProvisioningCheckerException pce = new ProvisioningCheckerException("Expected a non-null value to 'Set' in the attribute request.");
//-                pce.attributeRequest = attrReq;
//-                pce.requestAttrValueIsNull = true;
//-                throw pce;
//-            }
            
            Difference diff = Difference.diff(attrReq.getValue(), currentVal);
            if (null != diff) {
                hasBeenExecuted = false;
            }
            break;
            
        case Retain:
            // The operation is to retain the assignment. While uncommon to remain in the
            // provisioning plan, it should not be considered unknown.

            // Not executed if the value is missing
            if ( null == attrReq.getValue()) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " +
                              "'Retain' in an attribute request: " + attrReq.toXml());
                
                ProvisioningCheckerException pce = new ProvisioningCheckerException("Expected a non-null value to 'Retain' in the attribute request.");
                pce.attributeRequest = attrReq;
                pce.requestAttrValueIsNull = true;
                throw pce;
            }
            
            if (!containsAny(currentVal, attrReq.getValue())) {
                hasBeenExecuted = false;
            }
            break;

        default:
            throw new RuntimeException("Unknown operation " + op);
        }

        return hasBeenExecuted;
    }
    

    /**
     * Check whether the given permission request has been executed.
     */
    private boolean hasBeenExecuted(ProvisioningPlan.PermissionRequest permReq,
                                    List<Permission> permissions)
        throws GeneralException {
        
        boolean hasBeenExecuted = true;
        
        ProvisioningPlan.Operation op = permReq.getOperation();
        if (null == op) {
            op = ProvisioningPlan.Operation.Remove;
        }

        List<String> currentRights = null;
        if (null != permissions) {
            for (Permission perm : permissions) {
                if (permReq.getTarget().equals(perm.getTarget())) {
                    boolean match = false;
                    if (Util.isNotNullOrEmpty(permReq.getTargetCollector())) {
                        //Check aggSource
                        if (Util.nullSafeEq(permReq.getTargetCollector(), perm.getAggregationSource())) {
                            match = true;
                        }
                    } else {
                        //Same as link
                        match = true;
                    }

                    if (match) {
                        if (null == currentRights) {
                            currentRights = new ArrayList<String>();
                        }
                        currentRights.addAll(perm.getRightsList());
                    }
                }
            }
        }


        switch (op) {
        case Remove:
            List<String> removed = permReq.getRightsList();
            if (null == removed) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " +
                              "'Remove' in a permission request: " + permReq.toXml());
                
                ProvisioningCheckerException pce =
                     new ProvisioningCheckerException("Expected a non-null list of rights to 'Remove' in the permission request.");
                pce.permissionRequest = permReq;
                pce.requestPermValueIsNull = true;
                throw pce;
            }
            
            // Not executed if the value is still there.
            if (containsAny(currentRights, permReq.getRightsList())) {
                hasBeenExecuted = false;
            }
            break;

        case Add:
            // Check if the current value contains the requested values.
            List<String> added = permReq.getRightsList();
            if (null == added) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " + 
                              "'Add' in a permission request: " + permReq.toXml());
                
                ProvisioningCheckerException pce =
                     new ProvisioningCheckerException("Expected a non-null list of rights to 'Add' in the permission request.");
                pce.permissionRequest = permReq;
                pce.requestPermValueIsNull = true;
                throw pce;
            }

            // Not added if the list is null or all rights are not contained.
            if ((null == currentRights) || !currentRights.containsAll(added)) {
                hasBeenExecuted = false;
            }
            
            break;

        case Set:
            List<String> setting = permReq.getRightsList();
            if (null == setting) {
                if (log.isErrorEnabled())
                    log.error("There was a null value on operation: " +
                              "'Set' in a permission request: " + permReq.toXml());
                
                ProvisioningCheckerException pce =
                     new ProvisioningCheckerException("Expected a non-null list of rights to 'Set' in the permission request.");
                pce.permissionRequest = permReq;
                pce.requestPermValueIsNull = true;
                throw pce;
            }
            
            if (!Util.orderInsensitiveEquals(currentRights, permReq.getRightsList())) {
                hasBeenExecuted = false;
            }
            break;

        default:
            throw new RuntimeException("Unknown operation " + op);
        }

        return hasBeenExecuted;
    }

    /**
     * Check whether the first object (possibly null, possible not a collection)
     * is equal to or contains any of the values in the second object (must not
     * be null).
     */
    private static boolean containsAny(Object o1, Object o2) {

        assert (null != o2) : "Expected a value to check for.";

        if (o1 == null) {
            return false;
        }

        if (o2 instanceof Collection) {
            for (Object current : (Collection) o2) {
                if (containsAny(o1, current)) {
                    return true;
                }
            }
        }
        else {
            return Util.asList(o1).contains(o2);
        }

        return false;
    }

    /**
     * When comparing two list of Strings we need to make sure we ignore case.
     * Otherwise if the Entitlement doesn't match exactly as its specified in  
     * a profile we will never detect that the entitlement was granted.
     */
    private static boolean containsAllIgnoreCase(List current, List newList) {
        List notFound = new ArrayList();
        if ( Util.size(current) == 0 ) {
            return false;
        }
        if ( Util.size(newList) > 0 ) {
            for ( Object o : newList ) {
                if ( o == null ) continue; // ignore nulls
                String s = o.toString();
                boolean foundInExisting = false;
                for ( Object cur : current ) {
                     if ( cur == null ) continue;                     
                     if ( s.compareToIgnoreCase(cur.toString()) == 0 ) {
                         foundInExisting = true;
                         break;
                     }
                }
                if ( !foundInExisting ) 
                    notFound.add(o);
            }
        }
        return ( Util.size(notFound) > 0 ) ? false : true;
    }

    /**
     * Check the account request and if all of the attribute requests in the
     * request are Removes return true.
     */
    private boolean onlyRemoveRequests(ProvisioningPlan.AccountRequest acctReq) {
        boolean allRemoves = true;
        List<ProvisioningPlan.AttributeRequest> attrReqs = acctReq.getAttributeRequests();
        if ( Util.size(attrReqs) > 0 ) {
            for (ProvisioningPlan.AttributeRequest req : attrReqs ) {
                if (  !req.getOperation().equals(ProvisioningPlan.Operation.Remove) ) {
                     return false;
                }
            } 
        } 
        return allRemoves;
    }
    
    /**
     * Comes from BUG#16019 
     * 
     * Normalize the integer and boolean values based on the
     * type of the attribute if we have a stored in the schema.
     * 
     * Important for the null casa and booleans because typically 
     * null boolean values get filtered from the Link and should be 
     * treated the same as false. AD for instance doesn't return 
     * false values at all.
     * 
     * Additionally, in most cases the connector tolerates a 
     * string for boolean and integer types during proivisioning
     * and does the coercion before setting the value. Typically
     * however, the connector will store a Boolean/Integer and return
     * that non-string type when we fetch the link. 
     * 
     * With that said we drive the normalized value based on the 
     * request value's type for the best chance of matching.
     * 
     * @param attrReq
     * @param currentValue
     * @param schema
     * @return
     */
    private Object normalizeValue(ProvisioningPlan.AttributeRequest attrReq, Object currentValue, Schema schema) {
        
        Object val = currentValue;
      
        if ( schema != null )  {            
            Object reqValue = null;
            if ( attrReq != null ) 
                reqValue = attrReq.getValue();
            
            if ( reqValue == null ) {
                return val;
            }
            
            String attrType = null;
            
            String name = attrReq.getName();   
            AttributeDefinition def = null;
            if ( name != null ) {
                def = schema.getAttributeDefinition(name);
                if ( def != null ) {
                    if ( def.isMulti() ) { 
                        return val;
                    } else {
                        attrType = def.getType();
                    }
                }
            }
            if ( isInternalBoolean(name) || Util.nullSafeEq(AttributeDefinition.TYPE_BOOLEAN, attrType ) )  {
                if ( reqValue instanceof Boolean ) {
                    // we must normalize null vals
                    // that were compressed or not returned because
                    // they were false.
                    val = Util.otob(val);
                } else
                    if ( reqValue instanceof String ) {
                        Boolean bool = Util.otob(val);
                        val = bool.toString();
                    }
            } else
            if ( Util.nullSafeEq(AttributeDefinition.TYPE_INT, attrType ) )  {
                if ( reqValue instanceof String ) {
                    val = Util.otoa(currentValue);
                }
            }
        }
        return val;
    }
    
    /**
     * Handle the few attributes we publish internally that we
     * know are booleans.
     * 
     * @param name
     * @return
     */
    private boolean isInternalBoolean(String name) {
        if ( Util.nullSafeEq(Connector.ATT_IIQ_DISABLED, name) || Util.nullSafeEq(Connector.ATT_IIQ_LOCKED, name) ) {
            return true;
        }
        return false;
    }
    
    // ProvisioningChecker could fail for any number of reasons.  I'm including
    // an exception class here so that we can be more specific about what failed.
    public class ProvisioningCheckerException extends GeneralException {
        public ProvisioningPlan.AttributeRequest attributeRequest;
        public boolean requestAttrValueIsNull = false;
        
        public ProvisioningPlan.PermissionRequest permissionRequest;
        public boolean requestPermValueIsNull = false;
        
        ProvisioningCheckerException(String msg) {
            super(msg);
        }
        
    }
}
