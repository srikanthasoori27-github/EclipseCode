/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.provisioning.PlanUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Class to handle the side effects of mitigations in a certification.
 */
public class MitigationManager {

    private final static Log log = LogFactory.getLog(MitigationManager.class);

    /**
     * Inner helper class to hold mitigation related information for an identity.
     */
    class IdentityData {
        /**
         * Mitigation provisioning plan to set sunsets on mitigated access, if it is enabled.
         * If not enabled, this will be null.
         */
        private ProvisioningPlan masterPlan;

        /**
         * List of MitigationExpirations to be added to the identity. These will be
         * created when Mitigate decision is made on an item.
         */
        private List<MitigationExpiration> mitigationExpirationAdds;

        /**
         * List of MitigationExpirations to be potentially removed from an identity. These will be
         * created when a non-Mitigate decision is made, to remove previous mitigations.
         */
        private List<MitigationExpiration> mitigationExpirationRemoves;

        IdentityData() { }

        /**
         * Add a provisioning plan to the master plan for this identity, to set sunset dates.
         */
        public void add(ProvisioningPlan plan) {
            if (masterPlan == null) {
                masterPlan = plan;
            } else {
                masterPlan.merge(plan, false);
            }
        }

        /**
         * Add a MitigationExpiration to the list to add to an identity.
         */
        public void add(MitigationExpiration mitigationExpiration) {
            if (mitigationExpirationAdds == null) {
                this.mitigationExpirationAdds = new ArrayList<>();
            }

            this.mitigationExpirationAdds.add(mitigationExpiration);
        }

        /**
         * Add a MitigationExpiration to the list to remove from an identity.
         */
        public void remove(MitigationExpiration mitigationExpiration) {
            if (this.mitigationExpirationRemoves == null) {
                this.mitigationExpirationRemoves = new ArrayList<>();
            }

            this.mitigationExpirationRemoves.add(mitigationExpiration);
        }

    }

    private SailPointContext context;
    private RemediationCalculator remediationCalculator;

    /**
     * Cache of mitigation related data keyed on identity name.
     */
    private Map<String, IdentityData> identityMitigations;

    /**
     * The action to execute when mitigations expire.
     */
    private MitigationExpiration.Action mitigationExpirationAction;

    /**
     * Parameters for the action to execute when mitigations expire.
     */
    private Map<String,Object> mitigationExpirationActionParameters;

    /**
     * Flag to indicate if auto deprovisioning is enabled for the certification. Only set when
     * initialize is called.
     */
    private boolean autoDeprovisioningEnabled;

    public MitigationManager(SailPointContext context) throws GeneralException {
        this.context = context;
        this.remediationCalculator = new RemediationCalculator(this.context);

        Configuration config = this.context.getConfiguration();
        String actionString = config.getString(Configuration.MITIGATION_EXPIRATION_ACTION);
        if (null != actionString) {
            this.mitigationExpirationAction = MitigationExpiration.Action.valueOf(actionString);
        }

        this.mitigationExpirationActionParameters =
                (Map<String,Object>) config.get(Configuration.MITIGATION_EXPIRATION_ACTION_PARAMETERS);
    }

    /**
     * Reinitialize for a fresh run.
     * @param autoDeprovisioningEnabled If true, provision sunset dates for mitigations.
     */
    public void initialize(boolean autoDeprovisioningEnabled) {
        if (this.identityMitigations != null) {
            this.identityMitigations.clear();
        }
        this.autoDeprovisioningEnabled = autoDeprovisioningEnabled;
    }

    /**
     * Create the interesting mitigation-related artifacts for a given item.
     * If mitigated, we will create a MitigationExpiration to add to the identity and optionally create a provisioning
     * plan to set sunsets on the mitigated item. The latter applies only to non-violation items.
     * If not mitigated, we will create a MitigationExpiration that will potentially match one on the identity,
     * to remove it.
     *
     * To apply these artifacts to the identity, you must call {@link #flush()}
     * @param item CertificationItem
     * @throws GeneralException
     */
    public void handle(CertificationItem item) throws GeneralException {

        CertificationAction action = item.getAction();
        if (action == null) {
            return;
        }

        Map<String, Object> actionParams = new HashMap<String, Object>();
        if (mitigationExpirationActionParameters != null) {
            actionParams.putAll(mitigationExpirationActionParameters);
        }

        MitigationExpiration.Action expirationAction = this.mitigationExpirationAction;

        //if auto-deprovision is enabled, treat as setting sunset date on role/entitlement.
        if (autoDeprovisioningEnabled
                && CertificationAction.Status.Mitigated.equals(action.getStatus())
                && applicableToDeprovision(item)) {

            //setup sunset date on the plan
            deprovisionUponExpiration(item);

            //Override the action, so the Scanner will not send out notification when using sunset.
            //Since the sunset date might change, and we will not sync up the MitigationExpiration object.
            expirationAction = MitigationExpiration.Action.NOTHING;

            //add flag to indicate this MitigationExpiration will be deprovisioned using sunset date
            actionParams.put("sunset", item.getAction().getMitigationExpiration());
        }

        //always create MitigationExpiration object to keep the history log.
        MitigationExpiration mex = new MitigationExpiration(item.getCertification(), item, this.context);
        mex.setAction(expirationAction);
        mex.setActionParameters(actionParams);

        IdentityData identityData = getIdentityData(item.getIdentity());
        if (CertificationAction.Status.Mitigated.equals(action.getStatus())) {
            identityData.add(mex);
        }
        else {
            identityData.remove(mex);
        }
    }

    /**
     * Flush the relevant artifacts to all the identities affected by mitigation related decisions.
     * @throws GeneralException
     */
    public void flush() throws GeneralException {
        if (this.identityMitigations == null) {
            return;
        }

        Iterator<Map.Entry<String, IdentityData>> mitigationIterator = this.identityMitigations.entrySet().iterator();
        while (mitigationIterator.hasNext()) {
            Map.Entry<String, IdentityData> mitigationEntry = mitigationIterator.next();
            IdentityData identityData = mitigationEntry.getValue();

            // No identity found, nothing to do!
            Identity identity = ObjectUtil.lockIdentityByName(this.context, mitigationEntry.getKey());
            if (identity == null) {
                continue;
            }

            try {
                MitigationExpiration removed = null;
                for (MitigationExpiration removeExpiration : Util.iterate(identityData.mitigationExpirationRemoves)) {
                    removed = identity.remove(removeExpiration);
                    // Delete the removed MitigationExpiration if adding/removing the
                    // MitigationExpiration removed an expiration from the identity.
                    if (null != removed) {
                        this.context.removeObject(removed);
                    }
                }

                for (MitigationExpiration removeExpiration : Util.iterate(identityData.mitigationExpirationAdds)) {
                    removed = identity.add(removeExpiration);
                    // Delete the removed MitigationExpiration if adding/removing the
                    // MitigationExpiration removed an expiration from the identity.
                    if (null != removed) {
                        this.context.removeObject(removed);
                    }
                }
            } finally {
                // this will save and commit for us
                ObjectUtil.unlockIdentity(this.context, identity);
            }

            if (identityData.masterPlan != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Executing the provisioning plan for mitigation expiration for identity " + identity.getName() + ":\n" + identityData.masterPlan.toXml());
                }

                // Set the identity on the plan so we can use the more modern provisioner signature
                identityData.masterPlan.setIdentity(identity);
                Provisioner provisioner = new Provisioner(this.context);
                // This will compile and execute for us.
                provisioner.execute(identityData.masterPlan);
            }

        }

        // Clean up the map as we go.
        mitigationIterator.remove();

        // Decache every time, no reason not to.
        this.context.decache();
    }

    //Whether this item is applicable for auto-deprovisioning upon mitigation expiration.
    //Auto-deprovisioning is only applicable for Roles and Entitlements
    private boolean applicableToDeprovision(CertificationItem item) {
        CertificationItem.Type type = item.getType();
        return (CertificationItem.Type.Bundle.equals(type) || 
                CertificationItem.Type.Exception.equals(type) ||
                CertificationItem.Type.AccountGroupMembership.equals(type) ||
                CertificationItem.Type.DataOwner.equals(type) 
                );
    }

    //setup sunset date on underlying role/entitlement
    //and execute the provisioning plan
    //essentially, this should be the same as LCM remove access with sunset date.
    private void deprovisionUponExpiration(CertificationItem item) throws GeneralException {
        ProvisioningPlan plan = this.remediationCalculator.calculateProvisioningPlan(item,
                CertificationAction.Status.Remediated);

        plan.setRequesters(Collections.singletonList(item.getAction().getActor(this.context)));
        for (ProvisioningPlan.AccountRequest acctReq : Util.safeIterable(plan.getAccountRequests())) {
            for (ProvisioningPlan.AttributeRequest req : Util.safeIterable(acctReq.getAttributeRequests())) {
                //IIQSAW-2010 -- Revoke is for remediation on Roles assigned by Rule, 
                //need to use Remove for sunset.
                if (Operation.Revoke.equals(req.getOp())) {
                    req.setOp(Operation.Remove);
                }
                req.setRemoveDate(item.getAction().getMitigationExpiration());
                if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(req.getName())) {
                    PlanUtil.addDeassignEntitlementsArgument(req);
                } else if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(req.getName())) {
                    PlanUtil.addDeassignEntitlementsArgument(req);
                } else {
                    req.setAssignment(true);
                }
            }
        }

        getIdentityData(item.getIdentity()).add(plan);
    }

    private IdentityData getIdentityData(String identity) {
        if (this.identityMitigations == null) {
            this.identityMitigations = new HashMap<>();
        }

        if (!this.identityMitigations.containsKey(identity)) {
            this.identityMitigations.put(identity, new IdentityData());
        }

        return this.identityMitigations.get(identity);
    }
}
