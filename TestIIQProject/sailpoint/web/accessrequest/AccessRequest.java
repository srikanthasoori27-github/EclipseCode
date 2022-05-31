/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.accessrequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This is a pseudo DTO object used to translate JSON from the client request into
 * objects that can be used by the access request service layer.
 *
 * @author: michael.hide
 * Created: 10/8/14 10:08 AM
 */
public class AccessRequest {
    private static Log log = LogFactory.getLog(AccessRequest.class);

    // Public constants
    public static final String IDENTITIES = "identities";
    public static final String ADDED_ROLES = "addedRoles";
    public static final String REMOVED_ROLES = "removedRoles";
    public static final String ADDED_ENTITLEMENTS = "addedEntitlements";
    public static final String REMOVED_ENTITLEMENTS = "removedEntitlements";
    public static final String PRIORITY = "priority";

    // Member variables
    private List<String> identities;
    private List<RequestedRole> addedRoles;
    private List<RemovedRole> removedRoles;
    private List<RequestedEntitlement> addedEntitlements;
    private List<RemovedEntitlement> removedEntitlements;
    private WorkItem.Level priority;

    // Calculated variables
    private Map<String, List<ProvisioningTarget>> accountSelections;

    /**
     * Takes a map of configuration data and sets member variables
     *
     * @param data Map of configuration data
     */
    @SuppressWarnings("unchecked")
    public AccessRequest(Map<String, Object> data) throws GeneralException {
        if (data != null && data.size() > 0) {
            identities = (List<String>) data.get(IDENTITIES);

            addedRoles = marshallRequestedRoles((List<Map<String, Object>>) data.get(ADDED_ROLES));
            removedRoles = marshallRemovedRoles((List<Map<String, Object>>) data.get(REMOVED_ROLES));
            addedEntitlements = marshallRequestedEntitlements((List<Map<String, Object>>) data.get(ADDED_ENTITLEMENTS));
            removedEntitlements = marshallRemovedEntitlements((List<Map<String, Object>>) data.get(REMOVED_ENTITLEMENTS));
            Configuration config = Configuration.getSystemConfig();
            Boolean isPriorityEnabled = config.getBoolean(Configuration.WORK_ITEM_PRIORITY_EDITING_ENABLED);
            if (isPriorityEnabled != null && isPriorityEnabled) {
                this.setPriority((String) data.get(PRIORITY));
            }
            else {
                this.setPriority(WorkItem.Level.Normal.toString());
            }
            // Do some minimum validation
            if(Util.isEmpty(identities)) {
                throw new GeneralException("No identities found in config data.");
            }

            boolean hasItems = false;
            if(!Util.isEmpty(addedRoles)) {
                hasItems = true;
            }
            if (!Util.isEmpty(removedRoles)) {
                hasItems = true;
            }
            if (!Util.isEmpty(addedEntitlements)) {
                hasItems = true;
            }
            if (!Util.isEmpty(removedEntitlements)) {
                hasItems = true;
            }

            if(!hasItems) {
                throw new GeneralException("No items found in config data.");
            }
        }
        else {
            throw new GeneralException("No data in constructor parameter.");
        }
    }

    /**
     * Marshals the requested roles map into a list of objects for easy handling.
     * @param data the data map
     * @return the list of RequestedRole instances
     * @throws GeneralException
     */
    private List<RequestedRole> marshallRequestedRoles(List<Map<String, Object>> data) throws GeneralException {
        List<RequestedRole> roles = new ArrayList<RequestedRole>();
        if(data != null) {
            for (Map<String, Object> role : data) {
                roles.add(new RequestedRole(role));
            }
        }
        return roles;
    }

    /**
     * Marshals the removed roles map into a list of objects for easy handling.
     * @param data the data map
     * @return the list of RemovedRole instances
     * @throws GeneralException
     */
    private List<RemovedRole> marshallRemovedRoles(List<Map<String, Object>> data) throws GeneralException {
        List<RemovedRole> roles = new ArrayList<RemovedRole>();
        if(data != null) {
            for (Map<String, Object> role : data) {
                roles.add(new RemovedRole(role));
            }
        }
        return roles;
    }
    
    private List<RequestedEntitlement> marshallRequestedEntitlements(List<Map<String, Object>> data) throws GeneralException {
        List<RequestedEntitlement> entitlements = new ArrayList<RequestedEntitlement>();
        for (Map<String, Object> entitlement : Util.safeIterable(data)) {
            entitlements.add(new RequestedEntitlement(entitlement));
        }
        return entitlements;
    }
    
    private List<RemovedEntitlement> marshallRemovedEntitlements(List<Map<String, Object>> data) throws GeneralException {
        List<RemovedEntitlement> entitlements = new ArrayList<RemovedEntitlement>();
        for (Map<String, Object> entitlement : Util.safeIterable(data)) {
            entitlements.add(new RemovedEntitlement(entitlement));
        }
        return entitlements;
    }

    /**
     * @return List of identities
     */
    public List<String> getIdentityIds() {
        return this.identities;
    }

    /**
     * @return List of role ids to add
     */
    public List<String> getAddedRoleIds() {
        List<String> roleIds = new ArrayList<String>();
        if(this.addedRoles != null) {
            for (RequestedRole role : this.addedRoles) {
                roleIds.add(role.getId());
            }
        }
        return roleIds;
    }

    /**
     * @return List of AccessItems
     */
    public List<AccessItem> getAccessItems() {
        List<AccessItem> accessItems = new ArrayList<>();
        accessItems.addAll(this.addedRoles);
        accessItems.addAll(this.addedEntitlements);
        accessItems.addAll(this.removedEntitlements);
        accessItems.addAll(this.removedRoles);
        return accessItems;
    }

    /**
     * @return List of add role requests
     */
    public List<RequestedRole> getAddedRoles() {
        return Util.isEmpty(this.addedRoles) ? Collections.<RequestedRole>emptyList() : this.addedRoles;
    }

    /**
     * @return List of remove role requests
     */
    public List<RemovedRole> getRemovedRoles() {
        return Util.isEmpty(this.removedRoles) ? Collections.<RemovedRole>emptyList() : this.removedRoles;
    }

    /**
     * @return List of role ids to removed
     */
    public List<String> getRemovedRoleIds() {
        List<String> roleIds = new ArrayList<String>();
        for (RemovedRole role : Util.safeIterable(this.removedRoles)) {
            roleIds.add(role.getId());
        }
        return roleIds;
    }

    /**
     * @return List of entitlement ids to add
     */
    public List<String> getAddedEntitlementIds() {
        List<String> ids = new ArrayList<String>();
        for (RequestedEntitlement entitlement : Util.safeIterable(this.addedEntitlements)) {
            ids.add(entitlement.getId());
        }
        return ids;
    }
    
    /**
     * @return List of Entitlement ids to add
     */
    public List<RequestedEntitlement> getAddedEntitlements() {
        return this.addedEntitlements != null ? this.addedEntitlements : Collections.<RequestedEntitlement>emptyList();
    }

    /**
     * @return List of AccessRequestEntitlements
     */
    public List<RemovedEntitlement> getRemovedEntitlements() {
        return this.removedEntitlements != null ? this.removedEntitlements : Collections.<RemovedEntitlement>emptyList();
    }

    /**
     * @return List of ids of entitlements to remove
     */
    public List<String> getRemovedEntitlementIds() {
        List<String> ids = new ArrayList<String>();
        for (RemovedEntitlement entitlement : Util.safeIterable(this.removedEntitlements)) {
            ids.add(entitlement.getId());
        }
        return ids;
    }

    /**
     * @return WorkItem.Level for workflow priority
     */
    public WorkItem.Level getPriority() {
        if(priority==null) {
            priority = WorkItem.Level.Normal;
        }
        return priority;
    }

    /**
     * Sets the priority of a request
     */
    public void setPriority(WorkItem.Level priority) {
        this.priority = priority;
    }

    /**
     * Returns the priority of a accessrequest
     */
    public void setPriority(String priority) {
        if(!Util.isNullOrEmpty(priority)) {
            try {
                this.priority = WorkItem.Level.valueOf(priority);
            } catch(IllegalArgumentException iae) {
                log.warn("Invalid work item level: " + priority);
            }
        }
    }

    /**
     * Get the full of account selection provisioning targets in the AccessRequest across
     * requested roles and entitlements
     * @param context SailPointContext
     * @return Map keyed by Identity ID containing ProvisioningTargets
     * @throws GeneralException
     */
    public Map<String, List<ProvisioningTarget>> getAccountSelections(SailPointContext context)
            throws GeneralException {
        if (this.accountSelections == null) {
            List<RequestedAccessItem> items = new ArrayList<RequestedAccessItem>();
            if (this.addedRoles != null) {
                items.addAll(this.addedRoles);
            }
            if (this.addedEntitlements != null) {
                items.addAll(this.addedEntitlements);
            }
            this.accountSelections = RequestedAccessItem.getProvisioningTargets(context, items);
        }
        return this.accountSelections;
    }
}
