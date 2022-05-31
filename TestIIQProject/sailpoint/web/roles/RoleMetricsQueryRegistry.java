package sailpoint.web.roles;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.SearchResultsIterator;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.modeler.RoleUtil;

public class RoleMetricsQueryRegistry {
    private static final Log log = LogFactory.getLog(RoleMetricsQueryRegistry.class);
    private SailPointContext context;
    
    public static final String MEMBERS = "members";
    public static final String MEMBERS_WITH_ADDITIONAL_ENTITLEMENTS = "membersWithAdditionalEntitlements";
    public static final String MEMBERS_WITH_MISSING_REQUIRED = "membersWithMissingRequired";
    public static final String DETECTED = "detected";
    public static final String DETECTED_EXCEPTIONS = "detectedExceptions";
    public static final String PROVISIONED_ENTITLEMENTS = "provisionedEntitlements";
    public static final String PERMITTED_ENTITLEMENTS = "permittedEntitlements";
    
    /** max param size for SQL Server is 2100, just use 2000 to be safe **/
    public static final int MAX_PARAM_SIZE = 2000;

    private static final Map<String, Filter> metricFilters = new HashMap<String, Filter>();
    static {
        metricFilters.put(MEMBERS, Filter.eq("roleMetadatas.assigned", true));
        metricFilters.put(MEMBERS_WITH_ADDITIONAL_ENTITLEMENTS, Filter.eq("roleMetadatas.additionalEntitlements", true));
        metricFilters.put(MEMBERS_WITH_MISSING_REQUIRED, Filter.eq("roleMetadatas.missingRequired", true));
        metricFilters.put(DETECTED, Filter.eq("roleMetadatas.detected", true));
        metricFilters.put(DETECTED_EXCEPTIONS, Filter.eq("roleMetadatas.detectedException", true));
    }

    public RoleMetricsQueryRegistry(SailPointContext context) {
        this.context = context;
    }
    
    public Filter getMembersQuery(String roleId) throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, roleId);
        Set<String> roleNames = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
        Filter query = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            query = Filter.and(Filter.in("roleMetadatas.name", roleNames), Filter.eq("roleMetadatas.assigned", true));
        }        
        return query;
    }
    
    public Filter getMembersWithAdditionalEntitlementsQuery(String roleId) throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, roleId);
        Set<String> roleNames = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
        Filter query = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            query = Filter.and(Filter.in("roleMetadatas.name", roleNames), Filter.eq("roleMetadatas.additionalEntitlements", true));
        }
        return query;
    }
    
    public Filter getMembersWithMissingRequiredQuery(String roleId) throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, roleId);        
        Set<String> roleNames = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
        Filter query = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            query = Filter.and(Filter.in("roleMetadatas.name", roleNames), Filter.eq("roleMetadatas.missingRequired", true));
        }        
        return query;
    }
    
    public Filter getDetectedQuery(String roleId) throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, roleId);        
        Set<String> roleNames = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
        Filter query = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            query = Filter.and(Filter.in("roleMetadatas.name", roleNames), Filter.eq("roleMetadatas.detected", true));
        }
        return query;
    }
    
    public Filter getDetectedExceptionsQuery(String roleId) throws GeneralException {
        Bundle role = context.getObjectById(Bundle.class, roleId);
        Set<String> roleNames = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
        Filter query = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            query = Filter.and(Filter.in("roleMetadatas.name", roleNames), Filter.eq("roleMetadatas.detectedException", true));
        }
        return query;
    }
    
    /**
     * Retrieves the identity count based on the role and metric.
     * 
     * Bug#19471
     * This addresses the limitation on SQL server for max 2100 param size.
     * It will split to multiple queries and combine the result together.
     * 
     * If the param size is less than the max, it will behave as before.
     * 
     * @param metric filter metric
     * @param roleId role id
     * @return
     */
    public Integer getIdentityCount(String metric, String roleId) {
        int count = 0;
        try {
            Filter metricFilter = metricFilters.get(metric);
            if (metricFilter == null) {
                return count;
            }
            
            Bundle role = context.getObjectById(Bundle.class, roleId);
            Set<String> roleIds = RoleUtil.getRoleNamesInHierarchyAsSet(role, context);
            
            if (roleIds != null && roleIds.size() < MAX_PARAM_SIZE) {
                //for performance, do not split if size < max_param_size
                Filter filter = Filter.and(Filter.in("roleMetadatas.name", roleIds), metricFilter);
                QueryOptions qo = new QueryOptions(filter);
                qo.setDistinct(true);
                count = context.countObjects(Identity.class, qo);
            } else if (roleIds != null) {
                Set<String> identityIdSet = new HashSet<String>();
                //split filters to bypass SQL server max param limitation
                SearchResultsIterator iterator = ObjectUtil.searchAcrossIds(context, Identity.class,
                        Util.asList(roleIds), Util.asList(metricFilter), Util.asList("id"), "roleMetadatas.name");

                while (iterator != null && iterator.hasNext()) {
                    Object[] result = iterator.next();
                    if (result != null && result.length > 0) {
                        String identityId = (String)(result[0]);
                        identityIdSet.add(identityId);
                    }
                }

                count = identityIdSet.size();
            }
        } catch (Exception e) {}
        
        return count;
    }
    

    public Set<SimplifiedEntitlement> getProvisionedEntitlements(String roleId) {
        Set<SimplifiedEntitlement> provisionedEntitlements;
        
        try {
            Bundle role = context.getObjectById(Bundle.class, roleId);
            // Pretend to provision a fake Identity and see what they would get
            ProvisioningPlan assignmentPlan = new ProvisioningPlan();
            AccountRequest addRequest = new AccountRequest();
            addRequest.setApplication(ProvisioningPlan.APP_IIQ);
            addRequest.add(new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, Operation.Add, role));
            assignmentPlan.add(addRequest);        
            assignmentPlan.setIdentity(new Identity());
            provisionedEntitlements = getEntitlementsFromProvisioningPlan(assignmentPlan);
        } catch (GeneralException e) {
            provisionedEntitlements = new HashSet<SimplifiedEntitlement>();
            log.error("Failed to fetch provisioned entitlements");
        }
        return provisionedEntitlements;
    }
    
    public Set<SimplifiedEntitlement> getPermittedEntitlements(String roleId) {
        Set<SimplifiedEntitlement> permittedEntitlements;
        
        try {
            Bundle role = context.getObjectById(Bundle.class, roleId);
            
            Set<Bundle> requiredRoles = new HashSet<Bundle>();
            Set<Bundle> permittedRoles = new HashSet<Bundle>();
            Set<Bundle> permittedAndRequired = new HashSet<Bundle>();
            Set<Bundle> rolesInHierarchy = RoleUtil.getRolesInHierarchy(role, context, true);
            for (Bundle roleInHierarchy : rolesInHierarchy) {
                // Check for required roles
                RoleUtil.addRequiredRoles(roleInHierarchy, requiredRoles, new HashSet<String>());
                RoleUtil.addPermittedRoles(roleInHierarchy, permittedRoles, new HashSet<String>());
                permittedAndRequired.addAll(requiredRoles);
                permittedAndRequired.addAll(permittedRoles);
            }

            
            // Pretend to provision a fake Identity and see what they would get
            ProvisioningPlan assignmentPlan = new ProvisioningPlan();
            for (Bundle permittedOrRequired : permittedAndRequired) {
                AccountRequest addRequest = new AccountRequest();
                addRequest.setApplication(ProvisioningPlan.APP_IIQ);
                addRequest.add(new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, Operation.Add, permittedOrRequired));
                assignmentPlan.add(addRequest);
            }
            assignmentPlan.setIdentity(new Identity());
            permittedEntitlements = getEntitlementsFromProvisioningPlan(assignmentPlan);
        } catch (GeneralException e) {
            permittedEntitlements = new HashSet<SimplifiedEntitlement>();
            log.error("Failed to fetch provisioned entitlements");
        }
        return permittedEntitlements;
    }
    
    private Set<SimplifiedEntitlement> getEntitlementsFromProvisioningPlan(ProvisioningPlan assignmentPlan) throws GeneralException {
        Set<SimplifiedEntitlement> entitlements = new HashSet<SimplifiedEntitlement>();
        Attributes<String,Object> args = new Attributes<String, Object>();
        args.put(Provisioner.ARG_FULL_RECONCILIATION, true);
        Provisioner provisioner = new Provisioner(context, args);
        provisioner.setNoCreateTemplates(true);
        ProvisioningProject project = provisioner.compile(assignmentPlan, args);
        // System.out.println("result: " + project.toXml(false));
        
        // Look through the provisioning plan for entitlements
        List<ProvisioningPlan> plans = project.getPlans();
        if (plans != null && !plans.isEmpty()) {
            for (ProvisioningPlan plan : plans) {
                List<AccountRequest> accountRequests = plan.getAccountRequests();
                if (accountRequests != null && !accountRequests.isEmpty()) {
                    for (AccountRequest request : accountRequests) {
                        if (request.getOperation() == AccountRequest.Operation.Create || request.getOperation() == AccountRequest.Operation.Modify) {
                            List<AttributeRequest> attributeRequests = request.getAttributeRequests();
                            if (attributeRequests != null && !attributeRequests.isEmpty()) {
                                for (AttributeRequest entitlementRequest : attributeRequests) {
                                    if (entitlementRequest.getOperation() == Operation.Add) {
                                        Application app = request.getApplication(context);
                                        Object requestValue = entitlementRequest.getValue();
                                        if (requestValue instanceof List) {
                                            List<String> value = (List<String>)requestValue; 
                                            if (value != null && !value.isEmpty()) {
                                                for (String valueString : value) {
                                                    entitlements.add(new SimplifiedEntitlement(app.getId(), app.getName(), null, entitlementRequest.getName(), valueString, null));
                                                }
                                            }
                                        } else if (requestValue instanceof String) {
                                            String value = (String)requestValue;
                                            entitlements.add(new SimplifiedEntitlement(app.getId(), app.getName(), null, entitlementRequest.getName(), value, null));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return entitlements;
    }
    
    public Object get(String metric, String roleId) {
        Object query = null;
        try {
            Method queryGenerator = queryRegistry.get(metric);
            query = queryGenerator.invoke(this, roleId);
        } catch (IllegalArgumentException e) {
            log.error("Failed to query identities on metric: " + metric + " for role with id: " + roleId, e);
        } catch (IllegalAccessException e) {
            log.error("Failed to query identities on metric: " + metric + " for role with id: " + roleId, e);
        } catch (InvocationTargetException e) {
            log.error("Failed to query identities on metric: " + metric + " for role with id: " + roleId, e);
        }
        return query; 
    }
    
    private static final Map<String, Method> queryRegistry = new HashMap<String, Method>();
    static {
        try {
            queryRegistry.put(MEMBERS, RoleMetricsQueryRegistry.class.getMethod("getMembersQuery", String.class));
            queryRegistry.put(MEMBERS_WITH_ADDITIONAL_ENTITLEMENTS, RoleMetricsQueryRegistry.class.getMethod("getMembersWithAdditionalEntitlementsQuery", String.class));
            queryRegistry.put(MEMBERS_WITH_MISSING_REQUIRED, RoleMetricsQueryRegistry.class.getMethod("getMembersWithMissingRequiredQuery", String.class));
            queryRegistry.put(DETECTED, RoleMetricsQueryRegistry.class.getMethod("getDetectedQuery", String.class));
            queryRegistry.put(DETECTED_EXCEPTIONS, RoleMetricsQueryRegistry.class.getMethod("getDetectedExceptionsQuery", String.class));
            queryRegistry.put(PROVISIONED_ENTITLEMENTS, RoleMetricsQueryRegistry.class.getMethod("getProvisionedEntitlements", String.class));
            queryRegistry.put(PERMITTED_ENTITLEMENTS, RoleMetricsQueryRegistry.class.getMethod("getPermittedEntitlements", String.class));
        } catch (SecurityException e) {
            log.error("Failed to initialize the query registry for role metrics.", e);
        } catch (NoSuchMethodException e) {
            log.error("Failed to initialize the query registry for role metrics.", e);
        }
    }

}
