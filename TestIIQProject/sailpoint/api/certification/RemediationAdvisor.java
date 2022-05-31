package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleRelationships;
import sailpoint.role.RoleAssignmentRelationships;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;

/**
 * Convenience methods used when dealing with remediations.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class RemediationAdvisor {

    private static final Log log = LogFactory.getLog(RemediationAdvisor.class);

    private SailPointContext context;

    public RemediationAdvisor(SailPointContext context) {
        this.context = context;
    }

    /**
     * Gets the system-wide default remediator.
     */
    public String getDefaultRemediator() throws GeneralException{
        Configuration config = context.getConfiguration();
        return  config.getString(Configuration.DEFAULT_REMEDIATOR);
    }

    /**
     * Gets the system-wide option to allow overriding the default remediator.  
     * Only allowed if the option is true and the item is a PolicyViolation.
     */
    public boolean getEnableOverrideDefaultRemediator(CertificationItem item) throws GeneralException {
        if (CertificationItem.Type.PolicyViolation.equals(item.getType())) {
        	CertificationDefinition def = item.getCertification().getCertificationDefinition(context);
            return def.isEnableOverrideViolationDefaultRemediator(context);
        } else {
            return false;
        }
    }

    /**
     * True if user is allowed to override the default remediator
     *
     * @return
     */
    public boolean isEnableOverrideDefaultRemediator() {
        Configuration sysConfig = Configuration.getSystemConfig();
        return sysConfig.getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR);
    }

    /**
     * Returns a summary of the remediation for the given item. This includes
     * the remediation action and the default remediator.
     * @param status Status chosen by the certifier, should be Remediate or RevokeAccount.
     * @param item The certification item
     * @param selectedRoles Any permitted or required roles the certifier chose to revoke as well.
     * @return Summary of this remediation
     */
    public RemediationSummary getRemediationSummary(CertificationAction.Status status,
                                                    CertificationItem item, List<Bundle> selectedRoles)
            throws GeneralException {

        RemediationSummary summary = new RemediationSummary();
        Set<Application> apps = new HashSet<Application>();
        if (selectedRoles != null && !selectedRoles.isEmpty()){
            for(Bundle role : selectedRoles){
                if (role!=null){
                    if (role.getApplications() != null)
                        apps.addAll(role.getApplications());
                }
            }
        }

        Set<Application> itemApps = item.getApplications(context);
        if (itemApps != null)
            apps.addAll(itemApps);

        summary.setAppOwner(getAppOwner(apps));

        // Get the remediation plan. If we're editing an existing action, look it up
        // on the item, otherwise get out the remediation manager and calculate a new one
        if (item.getAction() != null && CertificationAction.Status.Remediated.equals(item.getAction().getStatus()) &&
                item.getAction().isRevokeAccount() == false) {
            summary.setProvisioningPlan(item.getAction().getRemediationDetails());
            summary.setRemediationAction(item.getAction().getRemediationAction());
            summary.setDefaultRemediator(item.getAction().getOwner());
        } else {
            RemediationManager remediationMgr = new RemediationManager(context);
            RemediationManager.ProvisioningPlanSummary plan = null;
            if (selectedRoles == null || selectedRoles.isEmpty()){
                plan = remediationMgr.calculateRemediationDetails(item, status);
            } else {
                Identity identity = item.getIdentity(context);
                if (identity != null) {
                    plan = remediationMgr.calculateProvisioningDetails(status, selectedRoles, identity);
                }
            }

            if (plan != null) {
                summary.setProvisioningPlan(plan.getFullPlan());
                summary.setRemediationAction(plan.getAction());

                Identity defRemediator = this.calculateDefaultRemediator(null, plan);

                if (defRemediator != null) {
                    summary.setDefaultRemediator(defRemediator);
                }
            }
        }

        // Fallback, make sure we always try to get the remediator from the item
        if (summary.getDefaultRemediator() == null) {
            summary.setDefaultRemediator(item.getDefaultRemediator(this.context));
        }

        summary.setEnableOverrideDefaultRemediator(getEnableOverrideDefaultRemediator(item));

        return summary;
    }

    /**
     * Returns the revokable permitted roles for a given item. Revokable roles
     * are permitted roles which are not required by another role and are not
     * included as certification items in the cert.
     */
    public PermittedRoles getRevokablePermittedRoles(CertificationItem item) {

        PermittedRoles permittedRoles = new PermittedRoles();

        try {
            Bundle assignedRole = context.getObjectById(Bundle.class, item.getTargetId());
            Identity identity = item.getIdentity(context);

            if (assignedRole != null && identity != null) {
                
                if(item.getBundleAssignmentId() != null) {
                    
                    RoleAssignmentRelationships relationships = new RoleAssignmentRelationships(context);
                    relationships.analyze(identity);

                    List<Bundle> permits = relationships.getDistinctPermittedRoles(item.getBundleAssignmentId());

                    if(!Util.isEmpty(permits)) {
                        for(Bundle detectedRole : permits) {
                            if (relationships.isRequired(detectedRole, item.getBundleAssignmentId())) {
                                permittedRoles.addRequiredRole(detectedRole);
                            } else {
                                permittedRoles.addPermittedRole(detectedRole);
                            }
                        }
                    }
                    
                } else {
                    //Item did not have assignment id, default to old behavior
                    log.warn("Encountered Certification Item AssignedRole without an assignmentId");
                    
                    RoleRelationships relationships = new RoleRelationships();
                    relationships.analyze(identity);

                    List<Bundle> permits = relationships.getPermittedRoles(assignedRole);
                    if (permits != null){
                        for(Bundle detectedRole : permits){
                            if (relationships.isRequired(assignedRole, detectedRole)){
                                permittedRoles.addRequiredRole(detectedRole);
                            } else {
                                permittedRoles.addPermittedRole(detectedRole);
                            }
                        }
                    }

                    // remove any roles which are being certified on their own cert item.
                    if (!permittedRoles.isEmpty()){
                        QueryOptions ops = new QueryOptions();
                        ops.add(Filter.eq("parent.id", item.getCertificationEntity().getId()));
                        ops.add(Filter.in("targetId", permittedRoles.getIds()));
                        ops.add(Filter.eq("type", CertificationItem.Type.Bundle));
                        Iterator<Object[]> iter = null;
                        iter = context.search(CertificationItem.class, ops, Arrays.asList("targetId"));
                        try {
                            while (iter.hasNext()) {
                                String id = (String) ((Object[]) iter.next())[0];
                                permittedRoles.removeRole(id);
                                if (permittedRoles.isEmpty())
                                    break;
                            }
                        } finally {
                            Util.flushIterator(iter);
                        }

                        // remove any roles which are required or permitted by another role
                        // on the identity
                        if (identity != null && identity.getBundles() != null) {
                            for (Bundle b : identity.getBundles()) {
                                if (!assignedRole.equals(b)) {
                                    Iterator<Bundle> iterator = permittedRoles.getAllRoles().iterator();
                                    while (iterator.hasNext()) {
                                        Bundle r = iterator.next();
                                        if (b.permits(r) || b.requires(r))
                                           permittedRoles.removeRole(b.getId());
                                    }
                                }
                                if (permittedRoles.isEmpty())
                                    break;
                            }
                        }
                    }
                    
                }

            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        return permittedRoles;
    }

    /**
     * Return the names of the required or permitted roles for the given item (if a bundle) that can also be removed
     * with it.
     */
    public List<String> getRequiredOrPermittedRolesDisplayNames(CertificationItem item) throws GeneralException {
        if (CertificationItem.Type.Bundle.equals(item.getType()) &&
                CertificationItem.SubType.AssignedRole.equals(item.getSubType())) {
            PermittedRoles roles = getRevokablePermittedRoles(item);

            List<String> rolesNames = new ArrayList<String>();
            for (Bundle role : Util.safeIterable(roles.getAllRoles())) {
                rolesNames.add(role.getDisplayableName());
            }
            return rolesNames;
        }
        return null;
    }

    /**
     * Returns a list of roles required by the role being certified
     * which the identity does not have.
     * ASSUMPTION: This is only being done on RoleAssignments. Not sure it makes sense
     * to do this elsewhere
     */
    public List<Bundle> getMissingRequiredRoles(CertificationItem item) {

        List<Bundle> missingRoles = new ArrayList<Bundle>();

        if (CertificationItem.SubType.AssignedRole.equals(item.getSubType()) && item.getTargetId() != null){
            try {

                Identity identity = item.getIdentity(context);

                if (identity != null) {
                    if (Util.isNotNullOrEmpty(item.getBundleAssignmentId())) {
                        RoleAssignmentRelationships relationships = new RoleAssignmentRelationships(context);
                        relationships.analyze(identity);
                        missingRoles = relationships.getMissingRequirements(item.getBundleAssignmentId());
                    } else {
                        //Not sure this is a valid case. But will try the old way
                        log.warn("Encountered Certification Item with no assignmentId");
                        Bundle assignedRole = context.getObjectById(Bundle.class, item.getTargetId());
                        if (assignedRole != null && identity != null) {
                            RoleRelationships relation = new RoleRelationships();
                            relation.analyze(identity);
                            missingRoles = relation.getMissingRequirements(assignedRole);
                        }
                    }
                }
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }
        }

        return missingRoles;
    }

    /**
     * Helper method to calculate the default remediator for a policy violation
     *
     * @param policyViolation policy violation to find default remediator for
     * @return Identity
     * @throws GeneralException
     * TODO: Effective Entitlement Remediator?
     */
    public Identity calculateDefaultRemediator(PolicyViolation policyViolation,
                                               RemediationManager.ProvisioningPlanSummary plan) throws GeneralException {
        RemediationManager remediationMgr = new RemediationManager(context);
        remediationMgr.setExpandRoleAssignments(true);

        Identity defaultRemediator = null;

        // Check to see if we can get apps from the unmanaged plan
        if (getDefaultRemediatorFromPlan(plan)) {
            ProvisioningPlan unmanagedPlan = plan.getUnmanagedPlan();
            defaultRemediator = RemediationManager.getDefaultRemediator(unmanagedPlan, context);
        }
        else if (policyViolation != null) {
            //TODO: Who is default remediator for Effective? Manager?
            defaultRemediator = RemediationManager.getDefaultRemediator(getPolicyViolationApplications(policyViolation), context);
        }

        return defaultRemediator;
    }

    /**
     * Check if we can get a default remediator from the plan.
     * The unmanaged plan must have account requests that we can retrieve relevant apps from and action must be
     * OpenWorkItem.
     *
     * @return true if there are applications related to the plan
     */
    private boolean getDefaultRemediatorFromPlan(RemediationManager.ProvisioningPlanSummary plan) {
        boolean planHasApps = false;

        if (plan != null) {
            ProvisioningPlan unmanagedPlan = plan.getUnmanagedPlan();
            planHasApps = CertificationAction.RemediationAction.OpenWorkItem.equals(plan.getAction()) &&
                    unmanagedPlan != null && !Util.isEmpty(unmanagedPlan.getAccountRequests());
        }

        return planHasApps;
    }

    /**
     * Populates the list of application associated with the policy violation in question. For an exception we return
     * the exception application. We only return the apps in the bundles the user has chosen to remediate.
     *
     * @param policyViolation policy violation to find applications for
     * @return List<Application> list of applications pertaining to the policy violation
     * @throws GeneralException
     */
    public List<Application> getPolicyViolationApplications(PolicyViolation policyViolation) throws GeneralException {
        if (policyViolation == null) {
            return new ArrayList<>();
        }

        Set<Application> appSet = new HashSet<>();

        List<String> bundleNames = policyViolation.getBundleNamesMarkedForRemediation();

        for (String bundleName : Util.iterate(bundleNames)) {
            Bundle bundle = context.getObjectByName(Bundle.class, bundleName);
            if (bundle.getApplications() != null) {
                appSet.addAll(bundle.getApplications());
            }
        }

        List<PolicyTreeNode> selectedEntitlements = policyViolation.getEntitlementsToRemediate();
        for (PolicyTreeNode entitlement : Util.iterate(selectedEntitlements)) {
            if (Util.isNotNullOrEmpty(entitlement.getApplication()) && !PolicyTreeNode.TYPE_TARGET_SOURCE.equals(entitlement.getSourceType())) {
                Application app = context.getObjectByName(Application.class, entitlement.getApplication());
                if (app != null && !appSet.contains(app)) {
                    appSet.add(app);
                }
            }
        }

        return new ArrayList<>(appSet);
    }

    /**
     * @return Unique owner from the list of apps if there is one.
     */
    private Identity getAppOwner(Set<Application> apps){

        if (apps == null || apps.isEmpty()){
            return null;
        }

        List appList = new ArrayList<Application>();
        appList.addAll(apps);

        return RemediationManager.getUniqueAppIdentity(new RemediationManager.UniqueIdentityFetcher() {
           public Identity getUniqueIdentity(Application app) {
               return app.getOwner();
           }
        }, appList);
    }


    public static class RemediationSummary{

        private Identity defaultRemediator;
        private Identity appOwner;
        private ProvisioningPlan provisioningPlan;
        private CertificationAction.RemediationAction remediationAction;
        private boolean enableOverrideDefaultRemediator;

        public RemediationSummary() {
        }

        public Identity getDefaultRemediator() {
            return defaultRemediator;
        }

        public void setDefaultRemediator(Identity defaultRemediator) {
            this.defaultRemediator = defaultRemediator;
        }

        public CertificationAction.RemediationAction getRemediationAction() {
            return remediationAction;
        }

        public void setRemediationAction(CertificationAction.RemediationAction remediationAction) {
            this.remediationAction = remediationAction;
        }

        public ProvisioningPlan getProvisioningPlan() {
            return provisioningPlan;
        }

        public void setProvisioningPlan(ProvisioningPlan provisioningPlan) {
            this.provisioningPlan = provisioningPlan;
        }

        public Identity getAppOwner() {
            return appOwner;
        }

        public void setAppOwner(Identity appOwner) {
            this.appOwner = appOwner;
        }
        
        public boolean getEnableOverrideDefaultRemediator() {
            return this.enableOverrideDefaultRemediator;
        }
        
        public void setEnableOverrideDefaultRemediator(boolean enableOverride) {
            this.enableOverrideDefaultRemediator = enableOverride;
        }
    }

    public static class PermittedRoles{

        List<Bundle> requiredRoles = new ArrayList<Bundle>();
        List<Bundle> permittedRoles = new ArrayList<Bundle>();

        public PermittedRoles() {

        }

        public void addPermittedRole(Bundle role){
            permittedRoles.add(role);
        }

        public void addRequiredRole(Bundle role){
            requiredRoles.add(role);
        }

        public boolean isEmpty(){
            return permittedRoles.isEmpty() && requiredRoles.isEmpty();
        }

        public List<Bundle> getAllRoles(){
            List<Bundle> combined = new ArrayList<Bundle>();
            combined.addAll(requiredRoles);
            combined.addAll(permittedRoles);
            return combined;
        }

        public List<Bundle> getPermittedRoles() {
            return permittedRoles;
        }

        public List<Bundle> getRequiredRoles() {
            return requiredRoles;
        }

        public Set<String> getIds(){
            Set<String> ids = new HashSet<String>();
            for(Bundle b : permittedRoles){
                ids.add(b.getId());
            }
            for(Bundle b : requiredRoles){
                ids.add(b.getId());
            }

            return ids;
        }

        public void removeRole(String id){
            Iterator<Bundle> permittedRolesIter = permittedRoles.iterator();
            while(permittedRolesIter.hasNext()){
                if (permittedRolesIter.next().getId().equals(id)){
                    permittedRolesIter.remove();
                    return;
                }
            }

            Iterator<Bundle> requiredRolesIter = requiredRoles.iterator();
            while(requiredRolesIter.hasNext()){
                if (requiredRolesIter.next().getId().equals(id)){
                    requiredRolesIter.remove();
                    return;
                }
            }

        }

    }


}
