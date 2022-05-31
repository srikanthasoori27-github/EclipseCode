package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.RoleMetadata;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlementsKey;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.web.modeler.RoleUtil;

public class RoleMetadator {
    private static final Log log = LogFactory.getLog(RoleMetadator.class);
    private SailPointContext _context;
    private Attributes<String,Object> _arguments;
    /**
     * Enable trace messages for the bulk refresh methods.
     */
    boolean _trace;
    
    public RoleMetadator(SailPointContext context, Attributes<String,Object> args) {
        _context = context;
        _arguments = args;
    }
    
    /**
     * Preload persistent state to the extent possible and cache it.
     *
     */
    public void prepare() throws GeneralException {

        if (_arguments != null) {
            _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);
        }
    }
    
    public void execute(Identity ident) {
        Map<String, RoleMetadata> metadatasToUpdate = new HashMap<String, RoleMetadata>();
        
        ProvisioningPlan assignmentPlan = new ProvisioningPlan();
        Set<Bundle> permittedAndRequired = new HashSet<Bundle>();

        Meter.enterByName("RoleMetadator - Assigned role stats");
        if (ident.getAssignedRoles() != null && !ident.getAssignedRoles().isEmpty()) {
            List<Bundle> assignedRoles = new ArrayList<Bundle>();
            assignedRoles.addAll(ident.getAssignedRoles()); // Avoid ConcurrentModificationException
            Set<Bundle> requiredRoles = new HashSet<Bundle>();
            Set<Bundle> permittedRoles = new HashSet<Bundle>();
            for (Bundle assignedRole : assignedRoles) {
                RoleMetadata newMetadata = new RoleMetadata(assignedRole.getName());
                newMetadata.setAssigned(true);
                metadatasToUpdate.put(newMetadata.getName(), newMetadata);

                // Check for required roles
                Meter.enterByName("RoleMetadator - gather required/permitted");
                requiredRoles.clear();
                RoleUtil.addRequiredRoles(assignedRole, requiredRoles, new HashSet<String>());
                RoleUtil.addPermittedRoles(assignedRole, permittedRoles, new HashSet<String>());
                Meter.exitByName("RoleMetadator - gather required/permitted");

                Meter.enterByName("RoleMetadator - check missing required");
                boolean isMissingRequired = false;
                if (!requiredRoles.isEmpty()) {
                    for (Bundle requiredRole : requiredRoles) {
                        isMissingRequired |= !ident.hasRole(requiredRole, true);
                    }
                }
                if (isMissingRequired) {
                    newMetadata.setMissingRequired(true);
                }
                Meter.exitByName("RoleMetadator - check missing required");
                
                permittedAndRequired.addAll(requiredRoles);
                permittedAndRequired.addAll(permittedRoles);
            }
        }
        Meter.exitByName("RoleMetadator - Assigned role stats");
        
        Meter.enterByName("RoleMetadator - Detected role stats");
        if (ident.getBundles() != null && !ident.getBundles().isEmpty()) {
            for (Bundle detectedRole : ident.getBundles()) {
                RoleMetadata metadata = metadatasToUpdate.get(detectedRole.getName());
                if (metadata == null) {
                    metadata = new RoleMetadata(detectedRole.getName());
                    metadatasToUpdate.put(metadata.getName(), metadata);
                }
                metadata.setDetected(true);
                metadata.setDetectedException(!permittedAndRequired.contains(detectedRole));
            }
        }
        Meter.exitByName("RoleMetadator - Detected role stats");
        
        Meter.enterByName("RoleMetadator - Gather link entitlements");
        Set<SimplifiedEntitlement> identityEntitlements = new HashSet<SimplifiedEntitlement>();
        if (ident.getLinks() != null && !ident.getLinks().isEmpty()) {
            Set<SimplifiedEntitlement> entitlementsToExclude = new HashSet<SimplifiedEntitlement>();
            SimplifiedEntitlementsKey entitlementSet = null;
            for (Link link : ident.getLinks()) {
                if (entitlementSet == null) {
                    entitlementSet = new SimplifiedEntitlementsKey(link, entitlementsToExclude, true);
                } else {
                    entitlementSet.addLink(link, entitlementsToExclude, true);
                }
            }
            
            identityEntitlements.addAll(entitlementSet.getSimplifiedEntitlements());
        }
        Meter.exitByName("RoleMetadator - Gather link entitlements");
        
        Meter.enterByName("RoleMetadator - Gather permitted/required entitlements");
        Set<SimplifiedEntitlement> permittedOrRequiredEntitlements = new HashSet<SimplifiedEntitlement>();
        if (!permittedAndRequired.isEmpty()) {
            try {
                for (Bundle permittedOrRequired : permittedAndRequired) {
                    AccountRequest addRequest = new AccountRequest();
                    addRequest.setApplication(ProvisioningPlan.APP_IIQ);
                    addRequest.add(new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, Operation.Add, permittedOrRequired));
                    assignmentPlan.add(addRequest);
                }
                // Pretend to provision a fake Identity and see what they would get
                assignmentPlan.setIdentity(new Identity());
                Attributes<String,Object> args = new Attributes<String, Object>();
                args.put(Provisioner.ARG_FULL_RECONCILIATION, true);
                // bug#12575 suppress create policies so we don't get random
                // HR attributes included in the plan that aren't really part of the role
                args.put(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES, true);
                // we also don't want attribute sync working here
                args.put(PlanCompiler.ARG_NO_ATTRIBUTE_SYNC_EXPANSION, true);

                Provisioner provisioner = new Provisioner(_context, args);
                Meter.enterByName("RoleMetadator - Compile provisioning plan");
                ProvisioningProject project = provisioner.compile(assignmentPlan, args);
                Meter.exitByName("RoleMetadator - Compile provisioning plan");
                // System.out.println("result: " + project.toXml(false));
                
                // Look through the provisioning plan for entitlements
                Meter.enterByName("RoleMetadator - Get entitlements from provisioning plan");
                List<ProvisioningPlan> plans = project.getPlans();
                if (plans != null && !plans.isEmpty()) {
                    for (ProvisioningPlan plan : plans) {
                        List<AccountRequest> accountRequests = plan.getAccountRequests();
                        if (accountRequests != null && !accountRequests.isEmpty()) {
                            for (AccountRequest request : accountRequests) {
                                AccountRequest.Operation accountRequestOp = request.getOperation();
                                if (accountRequestOp == AccountRequest.Operation.Create || accountRequestOp == AccountRequest.Operation.Modify) {
                                    List<AttributeRequest> attributeRequests = request.getAttributeRequests();
                                    if (attributeRequests != null && !attributeRequests.isEmpty()) {
                                        for (AttributeRequest entitlementRequest : attributeRequests) {
                                            if (entitlementRequest.getOperation() == Operation.Add) {
                                                Application app = request.getApplication(_context);
                                                Object requestValue = entitlementRequest.getValue();
                                                if (requestValue instanceof List) {
                                                    List<String> value = (List<String>)requestValue; 
                                                    if (value != null && !value.isEmpty()) {
                                                        for (String valueString : value) {
                                                            permittedOrRequiredEntitlements.add(new SimplifiedEntitlement(app.getId(), app.getName(), null, entitlementRequest.getName(), valueString, null));
                                                        }
                                                    }
                                                } else if (requestValue instanceof String) {
                                                    String value = (String)requestValue;
                                                    permittedOrRequiredEntitlements.add(new SimplifiedEntitlement(app.getId(), app.getName(), null, entitlementRequest.getName(), value, null));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Meter.exitByName("RoleMetadator - Get entitlements from provisioning plan");
                
            } catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error("The RoleMetadator failed to determine the entitlements " +
                              "that would be provisioned for identity " + ident.getName(), e);
            }
        } 
        Meter.exitByName("RoleMetadator - Gather permitted/required entitlements");
        
        Meter.enterByName("RoleMetadator - Check extra entitlements");
        boolean hasExtraEntitlements = !permittedOrRequiredEntitlements.containsAll(identityEntitlements);
        Collection<RoleMetadata> metadatas = metadatasToUpdate.values();
        if (metadatas != null && !metadatas.isEmpty()) {
            for (RoleMetadata metadata : metadatas) {
                if (metadata.isAssigned()) {
                    metadata.setAdditionalEntitlements(hasExtraEntitlements);
                }
            }
        }
        Meter.exitByName("RoleMetadator - Check extra entitlements");

        // Apply the metadata updates to the identity
        Meter.enterByName("RoleMetadator - Apply role metadatas to identity");
        List<RoleMetadata> existingMetadata = ident.getRoleMetadatas();
        Set<RoleMetadata> metadatasToRemove = new HashSet<RoleMetadata>();
        Set<String> processedMetadata = new HashSet<String>();
        if (existingMetadata != null && !existingMetadata.isEmpty()) {
            for (RoleMetadata metadataToUpdate : existingMetadata) {
                RoleMetadata updatedMetadata = metadatasToUpdate.get(metadataToUpdate.getName());
                if (updatedMetadata == null) {
                    // If we have metadata that wasn't found by the RoleMetadator flag it for removal
                    metadatasToRemove.add(metadataToUpdate);
                } else {
                    // Update existing metadata
                    metadataToUpdate.setAssigned(updatedMetadata.isAssigned());
                    metadataToUpdate.setAdditionalEntitlements(updatedMetadata.isAdditionalEntitlements());
                    metadataToUpdate.setMissingRequired(updatedMetadata.isMissingRequired());
                    metadataToUpdate.setDetected(updatedMetadata.isDetected());
                    metadataToUpdate.setDetectedException(updatedMetadata.isDetectedException());
                    try {
                        _context.saveObject(metadataToUpdate);
                    } catch (GeneralException e) {
                        log.error("Failed to completely update role metadata for the identity with name " + ident.getName(), e);
                    }
                    processedMetadata.add(updatedMetadata.getName());
                }
            }
        }
        
        // Finally we add brand new metadata
        if (!metadatasToUpdate.isEmpty()) {
            if (existingMetadata == null) {
                existingMetadata = new ArrayList<RoleMetadata>();
                ident.setRoleMetadatas(existingMetadata);
            }
            Collection<RoleMetadata> metadatasToAdd = metadatasToUpdate.values();
            for (RoleMetadata metadataToAdd : metadatasToAdd) {
                if (!processedMetadata.contains(metadataToAdd.getName())) {
                    existingMetadata.add(metadataToAdd);
                }
            }
        }
        
        // Remove the stale metadata
        if (!metadatasToRemove.isEmpty()) {
            existingMetadata.removeAll(metadatasToRemove);
            for (RoleMetadata metadataToDelete : metadatasToRemove) {
                try {
                    _context.removeObject(metadataToDelete);
                } catch (GeneralException e) {
                    if (log.isErrorEnabled()) {
                        log.error("The RoleMetadator failed to delete the metadata for the " +
                                metadataToDelete.getRole().getName() + " role on identity " + ident.getName(), e);
                    }
                }
            }
        }
        Meter.exitByName("RoleMetadator - Apply role metadatas to identity");
    }
    
}
