/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.api.IdentityService;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.provisioning.PlanUtil;
import sailpoint.service.certification.RoleProfileHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class RemediationCalculator {

    private static Log log = LogFactory.getLog(RemediationCalculator.class);

    // personal copy of the sp contet
    private SailPointContext context;
    private Provisioner provisioner;


    public RemediationCalculator(SailPointContext context) {
        this.context = context;
        this.provisioner = new Provisioner(context);
    }

    /**
     * Calculate remediation details for the given item based on the item CertificationAction
     * status value.
     * @param item
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan calculateProvisioningPlan(CertificationItem item) throws GeneralException{
        CertificationAction.Status status = item.getAction() != null ? item.getAction().getStatus() : null;
        if (item.getAction() != null && item.getAction().isRevokeAccount())
            status = CertificationAction.Status.RevokeAccount;

        if (status == null)
            return null;

        return calculateProvisioningPlan(item, status);
    }

     /**
     * Calculate the ProvisioningPlan to execute for the remediation of this
     * item for the given CertificationAction status.
     * <p/>
     * For an exceptional entitlement item, this will return the a plan
     * to remove all entitlements that are exceptional.
     * <p/>
     * For a bundle, this will return a plan that will remove the business role.
     * Related entitlements for the business role are handled in the PlanCompiler
     * during provisioning.
     * <p/>
     * For a policy violation, this looks for all bundles marked for remediation
     * and returns a plan that will remove the entitlements that grant those
     * business roles but aren't still being used by other business roles.
     *
     * @param item   The CertificationItem that is being remediated.
     * @param status The desired action status. This may not match the status on the item
     *               in the event that we're trying to calculate the remediation details before a
     *               decision has been made.
     * @return The ProvisioningPlan to execute for the remediation of this item.
     * @throws GeneralException
     */
    public ProvisioningPlan calculateProvisioningPlan(CertificationItem item,
                                                      CertificationAction.Status status) throws GeneralException{
        // Don't bother with approvals unless it's a role certification item.
        // Role items may require provisioning of required roles
        if (CertificationAction.Status.Approved.equals(status) &&
                !CertificationItem.Type.Bundle.equals(item.getType()))
            return null;

        ProvisioningPlan plan = null;
        switch (item.getType()) {
            case AccountGroupMembership:
            case Account:
            case Exception:
            case DataOwner:
                plan = createExceptionPlan(item, status);
                break;
            case BusinessRoleHierarchy:
                plan = createRoleRelationshipPlan(item, ProvisioningPlan.ATT_IIQ_ROLE_CHILD);
                break;
            case BusinessRolePermit:
                plan = createRoleRelationshipPlan(item, ProvisioningPlan.ATT_IIQ_ROLE_PERMIT);
                break;
            case BusinessRoleRequirement:
                plan = createRoleRelationshipPlan(item, ProvisioningPlan.ATT_IIQ_ROLE_REQUIREMENT);
                break;
            case BusinessRoleProfile:
                Profile profile = context.getObjectById(Profile.class, item.getTargetId());
                plan = createRoleCertifiablePlan(item, ProvisioningPlan.ATT_IIQ_ROLE_PROFILES, profile);
                break;
            case BusinessRoleGrantedCapability:
                Capability cap = context.getObjectById(Capability.class, item.getTargetId());
                plan = createRoleCertifiablePlan(item, ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_CAPABILITY, cap);
                break;
            case BusinessRoleGrantedScope:
                Scope scope = context.getObjectById(Scope.class, item.getTargetId());
                plan = createRoleCertifiablePlan(item, ProvisioningPlan.ATT_IIQ_ROLE_GRANTED_SCOPE, scope);
                break;
            case Bundle:
                plan = createRolePlan(item, status);
                break;
            case PolicyViolation:
                plan = createViolationPlan(item, status);
                break;
        }

        if (plan == null)
            return null;

        plan.setRequestTrackingId(item.getId());

        plan.setSourceId(item.getCertification().getId());
        plan.setSourceName(item.getCertification().getName());
        plan.setSourceType(Source.Certification.toString());

        return plan;
    }

    /**
     * Calculates remediation details for the given policy violation.
     *
     * @param violation Policy violation to remediate
     * @return The ProvisioningPlan to execute for the remediation.
     * @throws sailpoint.tools.GeneralException
     *
     */
    public ProvisioningPlan calculateProvisioningPlan(PolicyViolation violation)
            throws GeneralException {
        Policy policy = violation.getPolicy( context );
        
        // added the policy name hack for the case where the policy object gets deleted out from under us.
        // if this becomes more of a frequent problem we should pull out the policy type info from the policy
        // table into the policy_violation table.
        if ((policy != null && policy.getType().equals(Policy.TYPE_ENTITLEMENT_SOD )) || 
                (policy == null && violation.getEntitlementsToRemediate() != null)) {
            return calculateEntitlementSodProvisioningPlan ( violation );
        }
        
        Identity identity = violation.getIdentity();
        ProvisioningPlan plan = new ProvisioningPlan();

        ProvisioningPlan.AccountRequest account = new ProvisioningPlan.AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        
        plan.add(account);

        if (violation.getBundleNamesMarkedForRemediation() != null) {

            // We normally won't have more than a few revocations, but just
            // in case build a little search helper.
            // todo: deal with multiple assignments per role.
            Map<String,RoleAssignment> assignments = new HashMap<String,RoleAssignment>();
            List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
            //Need to do some null checking here -rap
            if(!Util.isEmpty(roleAssignments)) {
                for (RoleAssignment roleAssignment : Util.iterate(roleAssignments)) {
                    assignments.put(roleAssignment.getRoleName(), roleAssignment);
                }
            }
            
            for (String roleName : violation.getBundleNamesMarkedForRemediation()) {
                ProvisioningPlan.AttributeRequest primaryRoleRequest = new ProvisioningPlan.AttributeRequest();
                // bug#13706, we're not dealing with just detected roles now, 
                // this has been broken a LONG time
                String attname = ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;
                if (assignments.get(roleName) != null)
                    attname = ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES;
                primaryRoleRequest.setName(attname);
                primaryRoleRequest.setValue(roleName);
                primaryRoleRequest.setOperation(getRoleOperation(identity, roleName));
                //Not sure if we want to do this for violations or not, but shouldn't hurt
                if(assignments.get(roleName) != null) {
                    primaryRoleRequest.setAssignmentId(assignments.get(roleName).getAssignmentId());
                }

                PlanUtil.addDeassignEntitlementsArgument(primaryRoleRequest);

                account.add(primaryRoleRequest);

                // we are in a policy violation so if it is an assigned role we need to
                // add attribute requests for all required roles as well so they
                // can be expanded and remove requests will be generated for the entitlements
                if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(primaryRoleRequest.getName())) {
                    Bundle assignedRole = context.getObjectByName(Bundle.class, roleName);
                    if (assignedRole != null) {
                        // this will walk the inheritance hierarchy of the assigned role
                        addRequiredRoleRequests(assignedRole, account, primaryRoleRequest.getAssignmentId());

                        // also remove the hard permits
                        addHardPermitRequests(identity, primaryRoleRequest.getAssignmentId(), account);
                    }
                }
            }
        }

        plan.setRequestTrackingId(violation.getId());

        plan.setSourceId(violation.getId());
        plan.setSourceName(violation.getPolicyName());
        plan.setSourceType(Source.PolicyViolation.toString());

        return plan;
    }

    /**
     * Adds remove requests for the hard permits of the role assignment with the
     * specified assignment id.
     * @param identity The identity.
     * @param assignmentId The assignment id.
     * @param acctRequest The account request.
     */
    private void addHardPermitRequests(Identity identity, String assignmentId, ProvisioningPlan.AccountRequest acctRequest) {
        RoleAssignment assignment = identity.getRoleAssignmentById(assignmentId);
        if (assignment != null) {
            // create an attribute request for each hard permit
            for (RoleAssignment permit : Util.iterate(assignment.getPermittedRoleAssignments())) {
                ProvisioningPlan.AttributeRequest permitReq = new ProvisioningPlan.AttributeRequest();
                permitReq.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                permitReq.setValue(permit.getRoleName());
                permitReq.setOperation(ProvisioningPlan.Operation.Remove);
                permitReq.setAssignmentId(assignmentId);

                // use assigned role setting here because the request was originated
                // from an assigned role
                PlanUtil.addDeassignEntitlementsArgument(permitReq, true);

                acctRequest.add(permitReq);
            }
        }
    }

    /**
     * Adds remove requests for the required roles for the specified role as well as
     * its inheritance chain.
     * @param role The role.
     * @param acctRequest The account request.
     * @param assignmentId The assignment id.
     */
    private void addRequiredRoleRequests(Bundle role, ProvisioningPlan.AccountRequest acctRequest, String assignmentId) {
        for (Bundle superRole : Util.iterate(role.getInheritance())) {
            addRequiredRoleRequests(superRole, acctRequest, assignmentId);
        }

        for (Bundle requiredRole : Util.iterate(role.getRequirements())) {
            ProvisioningPlan.AttributeRequest requiredReq = new ProvisioningPlan.AttributeRequest();
            requiredReq.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
            requiredReq.setValue(requiredRole.getName());
            requiredReq.setOperation(ProvisioningPlan.Operation.Remove);
            requiredReq.setAssignmentId(assignmentId);

            // use assigned role setting here because the request was originated
            // from an assigned role
            PlanUtil.addDeassignEntitlementsArgument(requiredReq, true);

            acctRequest.add(requiredReq);
        }
    }

    private ProvisioningPlan calculateEntitlementSodProvisioningPlan( PolicyViolation violation ) 
    throws GeneralException {

        ProvisioningPlan plan = new ProvisioningPlan();
        IdentityService idService = new IdentityService(context);
        
        for( PolicyTreeNode node : violation.getEntitlementsToRemediate() ) {
            if (!Util.isEmpty(node.getContributingEntitlements())) {
                //Effective. Can't create a plan. Return null
                //TODO: What if policyType is effective, but none of the matches are effective?
                return null;
            }
            ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
            String applicationName = node.getApplication();
            String nativeIdentity = null;
            if( applicationName == null ) {
                applicationName = ProvisioningPlan.APP_IIQ;
            } else {
                nativeIdentity = getNativeIdentity(node, violation);
            }
            accountRequest.setApplication(applicationName);
            accountRequest.setNativeIdentity(nativeIdentity);
            accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            
            ProvisioningPlan.GenericRequest attributeRequest;
            if( node.isPermission() ) {
                attributeRequest = new ProvisioningPlan.PermissionRequest();
            } else {
                attributeRequest = new ProvisioningPlan.AttributeRequest();
            }
            attributeRequest.setName( node.getName() );
            attributeRequest.setValue( node.getValue() );
            attributeRequest.setOperation( ProvisioningPlan.Operation.Remove );
            accountRequest.add( attributeRequest );
            
            boolean addedRequest = false;
            if (!ProvisioningPlan.APP_IIQ.equals(applicationName)) {
                // The identity could have multiple accounts that match this
                // violation, we need the nativeIdentities in the plan.
                Application app = context.getObjectByName(Application.class, applicationName);
                if (null != app) {
                    // bug 25529 - If the identity has been deleted then there is no need
                    // to get the links. Could happen if the identity is deleted after a
                    // certification is created and it contains a policy violation for that
                    // identity.
                    if (violation.getIdentity() != null) {
                        List<Link> links = idService.getLinks(violation.getIdentity(), app);
                        if (null != links) {
                            for (Link link : links) {

                                Object attrValue = link.getAttribute(node.getName());
                                if (Differencer.objectsEqualOrContains(node.getValue(), attrValue, false)) {
                                    // this violation applies to this link
                                    ProvisioningPlan.AccountRequest reqClone = new ProvisioningPlan.AccountRequest();
                                    reqClone.clone(accountRequest);
                                    reqClone.setNativeIdentity(link.getNativeIdentity());
                                    plan.add(reqClone);
                                    addedRequest = true;

                                    if (log.isDebugEnabled()) {
                                        log.debug("Added AccountRequest for nativeIdentity: " + link.getNativeIdentity());
                                    }
                                }
                            }
                        } else {
                            // links are null
                            if (log.isDebugEnabled()) {
                                log.debug("Didn't find any links that the violation applies to." +
                                        "   App: " + applicationName +
                                        "   Name: " + node.getName() +
                                        "   Value: " + node.getValue());
                            }
                        }
                    } else {
                        // bug 25529 - The identity that has this violation is null. Could happen if
                        // the identity is deleted after a certification containing a policy violation
                        // has been created.
                        if (log.isDebugEnabled()) {
                            log.debug("Null identity for policy violation " + violation.getDisplayableName());
                        }
                    }
                } else {
                    // app is null
                    if (log.isDebugEnabled()) {
                        log.debug("For some reason, the app was null.");
                    }
                }
            }
            
            if (!addedRequest) {
                plan.add( accountRequest );
                if (log.isDebugEnabled()) {
                    log.debug("Didn't add any account requests to the plan when examining links or the app was IIQ.  Adding account request now.");
                }
            }
        }
        
        plan.setRequestTrackingId( violation.getId() );
        plan.setSourceId( violation.getId() );
        plan.setSourceName( violation.getPolicyName() );
        plan.setSourceType( Source.PolicyViolation.toString() );

        return plan;
    }
    
    String getNativeIdentity(PolicyTreeNode node, PolicyViolation violation) 
    throws GeneralException {
        if (!PolicyTreeNode.TYPE_TARGET_SOURCE.equals(node.getSourceType())) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.ignoreCase(Filter.eq("name", node.getName())));
            ops.add(Filter.ignoreCase(Filter.eq("value", node.getValue())));
            ops.add(Filter.eq("application.name", node.getApplication()));
            ops.add(Filter.eq("identity", violation.getIdentity()));
            ops.setResultLimit(1);

            Iterator<Object[]> results = this.context.search(IdentityEntitlement.class, ops, "nativeIdentity");
            if (results != null && results.hasNext()) {
                Object[] identityEntitlement = results.next();
                if (identityEntitlement != null) {
                    return (String) identityEntitlement[0];
                }
            }
        }
        
        return null;
    }
    
    /**
     * Create a provisioning for a group of roles. This is used to calculate
     * the plan for revoking the permitted roles granted by an assigned role.
     * @param roles
     * @param identity
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan calculateProvisioningPlan(CertificationAction.Status status,
                                                      List<Bundle> roles, Identity identity)
            throws GeneralException {

        ProvisioningPlan plan = new ProvisioningPlan();

        ProvisioningPlan.AccountRequest account = new ProvisioningPlan.AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);

        plan.add(account);

        if (roles != null) {
            ProvisioningPlan.Operation op =  null;
            for (Bundle role : roles) {

                if (CertificationAction.Status.Approved.equals(status)){
                    op = ProvisioningPlan.Operation.Add;
                } else {

                    // todo rshea TA4710: not sure the right way to handle multiple assignments per role here. 

                    // If it's a manual assignment, just remove the role. If it was assigned by
                    // a rule, remove and flag the role so it will not be re-assigned automatically.
                    List<RoleAssignment> assignments = identity.getRoleAssignments(role);

                    op = ProvisioningPlan.Operation.Remove;
                    for (RoleAssignment assignment : assignments) {
                        if (!assignment.isManual() && assignment.getAssigner() == null) {
                            op = ProvisioningPlan.Operation.Revoke;
                            break;
                        }
                    }
                    
                }

                ProvisioningPlan.AttributeRequest req = createRoleAttributeRequest(op, role, identity);
                if (req != null){
                    account.add(req);
                }
            }
        }

        return plan;
    }

    /**
     * Creates an attribute request for the removal of the given role. Takes into acount
     * the type of role and the assignment method, since this will affect how that role is
     * removed.
     *
     * Note that the plan does not address remediation of the underlying entitlements. The
     * generated plan must be passed to the provisioner to get the full remediation details.
     *
     * @param role The role to remove
     * @param identity Identity the role is being removed from
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan.AttributeRequest createRoleAttributeRequest(ProvisioningPlan.Operation op,
                                                                        Bundle role, Identity identity)
            throws GeneralException {


        boolean isAssignedRole = identity.getAssignedRole(role.getId()) != null;
        String attrName = isAssignedRole ? ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES :
            ProvisioningPlan.ATT_IIQ_DETECTED_ROLES;

        ProvisioningPlan.AttributeRequest attrReq = new ProvisioningPlan.AttributeRequest();
        attrReq.setName(attrName);
        attrReq.setAssignmentId(role.getAssignmentId());
        attrReq.setValue(role.getName());
        attrReq.setOperation(op);

        return attrReq;
    }

    private ProvisioningPlan createViolationPlan(CertificationItem item, CertificationAction.Status status)
            throws GeneralException {

        ProvisioningPlan plan = calculateProvisioningPlan(item.getPolicyViolation());

        ProvisioningPlan.AccountRequest adds = getAddedAssignedRoles(item);
        if (adds != null)
            plan.add(adds);

        return plan;

    }

    /**
     * Creates provising plan for a assigned role certification.
     * @param item
     * @return
     * @throws GeneralException
     */
    private ProvisioningPlan createRolePlan(CertificationItem item, CertificationAction.Status status)
            throws GeneralException {

        Identity identity = item.getIdentity(context);
        Bundle primaryRole = item.getBundle(context);

        ProvisioningPlan plan = new ProvisioningPlan();

        // This may occur if the underlying role has been deleted since cert
        if (primaryRole == null)
            return plan;

        ProvisioningPlan.AccountRequest account = new ProvisioningPlan.AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);

        // if this is a remediation of an assigned role then we will want any
        // possible removal of detected roles to respect the system config option
        // to remove assigned entitlements that is set for assigned role removal
        boolean forceAssignedOptionForDetected = false;

        // add the role as a revoke the item unless this is an approval
        if (CertificationAction.Status.Remediated.equals(status)) {

            ProvisioningPlan.AttributeRequest primaryRoleRequest = new ProvisioningPlan.AttributeRequest();
            primaryRoleRequest.setName(CertificationItem.SubType.AssignedRole.equals(item.getSubType()) ?
                    ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES : ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
            primaryRoleRequest.setValue(primaryRole.getName());
            primaryRoleRequest.setOperation(getRoleOperation(identity, primaryRole.getName()));
            //Try to set assignmentId here. We may not have one in the case of Native RoleDetection or pre 6.3 certs
            primaryRoleRequest.setAssignmentId(item.getBundleAssignmentId());

            forceAssignedOptionForDetected = ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(primaryRoleRequest.getName());

            account.add(primaryRoleRequest);
        }

        // IIQETN-6282 - we used to get AdditionalActions here, but we no longer provide users the functionality
        // to explicitly choose requires or permits of revoked roles.  So, AdditionalActions are deprecated here.

        PlanUtil.addDeassignEntitlementsArgument(account, forceAssignedOptionForDetected);
        
        plan.add(account);
        
        if(log.isDebugEnabled()) {
            log.debug("Role plan after so much munging: " + plan.toXml());
        }

        return plan;
    }

    private ProvisioningPlan.AccountRequest getAddedAssignedRoles(CertificationItem remediatedItem) {

        ProvisioningPlan.AccountRequest account = new ProvisioningPlan.AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);

        CertificationEntity entity = remediatedItem.getParent();
        for (CertificationItem item : entity.getItems()) {
            if (item.getAction() != null &&
                    CertificationAction.Status.Approved.equals(item.getAction().getStatus())) {

                List<ProvisioningPlan.AccountRequest> accountRequests =
                        item.getAction().getAdditionalActions() != null ?
                                item.getAction().getAdditionalActions().getAccountRequests() : null;

                if (accountRequests != null) {
                    for (ProvisioningPlan.AccountRequest acctReq : accountRequests) {
                        if (acctReq.getAttributeRequests() != null) {
                            account.addAll(acctReq.getAttributeRequests());
                        }
                    }
                }
            }
        }

        return account.getAttributeRequests() != null && !account.getAttributeRequests().isEmpty() ? account : null;
    }

    /**
     * @deprecated No longer used since users can no longer choose what related roles to revoke.
     * 
     * Take the additional actions listed on a cert item and create a plan which can be
     * merged into the primary plan.
     */
    @Deprecated
    public ProvisioningPlan calculateAdditionalActionPlan(CertificationItem item){

        ProvisioningPlan additionalActions = new ProvisioningPlan();

        if (item.getAction()  != null && item.getAction().getAdditionalActions() != null
                && item.getAction().getAdditionalActions().getAccountRequests() != null){

            // If we're revoking roles, to play nicely with provisioner we
            // actually have to retain the roles that were not revoked.
            if (CertificationAction.Status.Remediated.equals(item.getAction().getStatus())){

                List<String> revokedRoles = item.getAction().getAdditionalRemediations();

                if (revokedRoles != null && !revokedRoles.isEmpty()){

                    // Add a revoke for each role the user want to remove
                    if (!revokedRoles.isEmpty()){
                        ProvisioningPlan.AccountRequest accountRequest =
                                new ProvisioningPlan.AccountRequest(ProvisioningPlan.AccountRequest.Operation.Modify,
                                ProvisioningPlan.APP_IIQ, null, item.getIdentity());

                        for (String role : revokedRoles){
                            ProvisioningPlan.AttributeRequest attrReq = new ProvisioningPlan.AttributeRequest();
                            attrReq.setOperation(ProvisioningPlan.Operation.Remove);
                            attrReq.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                            attrReq.setValue(role);
                            //These may or may not have AssignmentIds, depending on the timeframe the cert was created.
                            //If the cert was created prior to 6.3, assignment id's are not likely
                            attrReq.setAssignmentId(item.getBundleAssignmentId());
                            accountRequest.add(attrReq);
                        }
                        additionalActions.add(accountRequest);
                        if(log.isDebugEnabled()) {
                            try {
                                log.debug("Additional Action Plan: " + additionalActions.toXml());
                            }
                            catch(GeneralException ge) {
                                log.error("Unable to print additionalActions.", ge);
                            }
                        }
                    }
                }
            } else {
                // If we're adding roles, just merge them in
                additionalActions = item.getAction().getAdditionalActions();
            }
        }

        // set any pre-filtered values
        if (item.getAction() != null && item.getAction().hasFilteredRemediations()) {
            // probably not necessary but be safe
            if (additionalActions == null) {
                additionalActions = new ProvisioningPlan();
            }

            additionalActions.setFiltered(item.getAction().getAdditionalActions().getFiltered());
        }

        if (additionalActions != null) {
            if (Util.isEmpty(additionalActions.getAccountRequests()) && Util.isEmpty(additionalActions.getFiltered()))  {
                additionalActions = null;
            }
        }

        return additionalActions;
    }

    /**
     * Returns the appropriate remediation operation for a role based on whether or not
     * is was assigned manually or by a rule. Detected roles are always marked as removes.
     * @param identity
     * @param roleName
     * @return
     */
    private ProvisioningPlan.Operation getRoleOperation(Identity identity, String roleName) {

        ProvisioningPlan.Operation op = ProvisioningPlan.Operation.Remove;

        if (roleName != null && identity != null) {

            List<RoleAssignment> roleAssignmentList = identity.getRoleAssignments(roleName);

            for (RoleAssignment roleAssignment : roleAssignmentList) {
                // set revoke is just one is rule assigned
                if (!roleAssignment.isManual() || roleAssignment.getAssigner() == null) {
                    op = ProvisioningPlan.Operation.Revoke;
                    break;
                }
            }
        }

        return op;
    }

    /**
     * Creates ProvisiongPlan for a role hierarchy certification item from a
     * role composition certification.
     *
     * @param item
     * @return
     * @throws GeneralException
     */
    private ProvisioningPlan createRoleRelationshipPlan(CertificationItem item, String attributeName)
            throws GeneralException {

        Bundle parentRole = context.getObjectById(Bundle.class,
                item.getParent().getTargetId());
        
        Bundle childBundle = context.getObjectById(Bundle.class,
                item.getTargetId());
        
        if (null == childBundle) {
            // Something happened to the childRole.  Probably deleted at some point.
            if (log.isWarnEnabled())
                log.warn("Could not get CertificationItem with targetId=" + 
                         item.getTargetId());
            
            return null;
        }

        //what if roles have been deleted?
        if (parentRole == null )
            return null;
        
        Reference childRoleRef = new Reference(childBundle);
        ProvisioningPlan.AttributeRequest attrRequest = new ProvisioningPlan.AttributeRequest();
        attrRequest.setName(attributeName);
        attrRequest.setOperation(ProvisioningPlan.Operation.Remove);
        attrRequest.setValue(childRoleRef);

        // old way
        //ProvisioningPlan.ObjectRequest request =
        //new ProvisioningPlan.ObjectRequest(parentRole, Arrays.asList(attrRequest));

        // 6.0 way
        ProvisioningPlan.ObjectRequest request = new ProvisioningPlan.ObjectRequest();
        request.setType("role");
        request.setNativeIdentity(parentRole.getName());
        request.add(attrRequest);

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.addObjectRequest(request);

        return plan;
    }

    private ProvisioningPlan createRoleCertifiablePlan(CertificationItem item, String attrName,
                                                       SailPointObject toRemove) throws GeneralException {

        if (toRemove == null)
            return null;

        Bundle parentRole = context.getObjectById(Bundle.class,
                item.getParent().getTargetId());

        // what if role has been deleted?
        if (parentRole == null)
            return null;

        ProvisioningPlan.AttributeRequest attrRequest = new ProvisioningPlan.AttributeRequest();
        attrRequest.setName(attrName);
        attrRequest.setOperation(ProvisioningPlan.Operation.Remove);
        attrRequest.setValue(new Reference(toRemove));
        
        // If this is a profile, assign a displayable name because profiles don't
        // have names in the UI, though we assign and use a name in the miner.
        if (ProvisioningPlan.ATT_IIQ_ROLE_PROFILES.equals(attrName)) {
            if (toRemove instanceof Profile) {
                Profile profile = (Profile) toRemove;
                setProfileData(profile, attrRequest);
            }
        }

        // old way
        //ProvisioningPlan.ObjectRequest request =
        //new ProvisioningPlan.ObjectRequest(parentRole, Arrays.asList(attrRequest));

        // 6.0 way
        ProvisioningPlan.ObjectRequest request = new ProvisioningPlan.ObjectRequest();
        request.setType("role");
        request.setNativeIdentity(parentRole.getName());
        request.add(attrRequest);

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.addObjectRequest(request);

        return plan;
    }

    /**
     * Set the profile related data
     *
     * @param profile Profile data object
     * @param attrRequest AttributeRequest object to add profile details to
     * @throws GeneralException
     */
    private void setProfileData(Profile profile, ProvisioningPlan.AttributeRequest attrRequest) throws GeneralException {
        // Set the profile ordinal
        if (null != profile.getApplication()) {
            attrRequest.setDisplayValue(profile.getApplication().getName());
            // if profile is multiple for this role, give it an ordinal
            int ord = profile.getProfileOrdinal();
            attrRequest.put(ProvisioningPlan.ATT_PLAN_PROFILE_ORDINAL, ord);
        }

        // Note that we use the defaults for locale and timezone because localization is not
        // an issue when getting the constraints description values (filters). Filters are just string representations
        // of the user defined entitlement profile filters.
        RoleProfileHelper roleProfileHelper = new RoleProfileHelper(new RoleSnapshot.ProfileSnapshot(profile),
                this.context, Locale.getDefault(), TimeZone.getDefault());

        // set profile description
        String profileDescription = profile.getDescription();
        if (Util.isNotNullOrEmpty(profileDescription)) {
            attrRequest.put(ProvisioningPlan.ATT_PLAN_PROFILE_DESCRIPTION, profileDescription);
        }

        // set profile constraint(s)
        List<String> constraints = roleProfileHelper.getContraintsDescription();

        if (!Util.isEmpty(constraints)) {
            attrRequest.put(ProvisioningPlan.ATT_PLAN_PROFILE_CONSTRAINTS, constraints);
        }
    }

    /**
     * Creates provising plan for an additional entitlement
     *
     * @param item
     * @param status
     * @return
     * @throws GeneralException
     */
    private ProvisioningPlan createExceptionPlan(CertificationItem item, CertificationAction.Status status)
            throws GeneralException {

        ProvisioningPlan plan = null;

        EntitlementSnapshot entitlements = item.getExceptionEntitlements();
        List<EntitlementSnapshot> details = null;

        if (null != entitlements) {
            // If this is an account revocation, delete the account.
            if (CertificationAction.Status.RevokeAccount.equals(status)) {
                
                // If identity does not exist or is no longer linked to this application account, 
                // there is no plan to execute. This can happen if application account is deleted 
                // after cert is created.
                Identity identity = item.getIdentity(this.context);
                if (identity == null || 
                        identity.getLink(entitlements.getApplicationObject(context),
                                entitlements.getInstance(),
                                entitlements.getNativeIdentity()) == null) {
                    return null;
                }

                plan = new ProvisioningPlan();
                plan.add(new ProvisioningPlan.AccountRequest(ProvisioningPlan.AccountRequest.Operation.Delete,
                        entitlements.getApplication(),
                        entitlements.getInstance(),
                        entitlements.getNativeIdentity()));
            } else {
                // Get a copy of the entitlement list if this is an entitlement group.
                // Note that the entitlements will have already been filtered when the
                // report was generated for an app owner certification.
                details = new ArrayList<EntitlementSnapshot>(1);
                details.add((EntitlementSnapshot) entitlements.clone());
                boolean isCertifyingIdentities = item.getCertification().isCertifyingIdentities();
                String maObjectType = null;
                if (!isCertifyingIdentities) {
                    //Need to get the object Type from the ManagedAttribute so we can set it on the ObjectRequest
                    //TODO: May need to revisit this
                    ManagedAttribute ma = item.getCertificationEntity().getAccountGroup(context);
                    maObjectType = ma != null ? ma.getType() : null;
                }
                plan = new ProvisioningPlan(details, isCertifyingIdentities, maObjectType);
            }
        }

        return plan;
    }

}
