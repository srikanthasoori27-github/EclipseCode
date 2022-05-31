/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.ProvisioningPlan;

public class ConfiguredLeaverRequest {
    private LeaverAppConfigProvider requestConfigProvider;
    private List<ProvisioningPlan.AccountRequest> accountRequests;
    boolean deleteAccount = false;
    boolean nonDeleteAccount = false;

    /**
     * Used to determine if the ConfiguredLeaverRequest contains any AccountRequest with a Delete operation.
     * @return
     */
    public boolean isDeleteAccount() {
        return deleteAccount;
    }

    /**
     * Used to determine if the ConfiguredLeaverRequest contains any AccountRequest with a Modify or Disable operation
     * @return
     */
    public boolean isNonDeleteAccount() {
        return nonDeleteAccount;
    }

    public void setDeleteAccount(boolean deleteAccount) {
        this.deleteAccount = deleteAccount;
    }

    public void setNonDeleteAccount(boolean nonDeleteAccount) {
        this.nonDeleteAccount = nonDeleteAccount;
    }

    public ConfiguredLeaverRequest(LeaverAppConfigProvider appConfigProvider) {
        this.requestConfigProvider = appConfigProvider;
        this.accountRequests = new ArrayList<ProvisioningPlan.AccountRequest>();
    }

    public List<ProvisioningPlan.AccountRequest> getAccountRequests() {
        return accountRequests;
    }

    public void setAccountRequests(List<ProvisioningPlan.AccountRequest> accountRequests) {
        this.accountRequests = accountRequests;
    }

    public LeaverAppConfigProvider getRequestConfigProvider() {
        return requestConfigProvider;
    }

    public void setRequestConfigProvider(LeaverAppConfigProvider requestConfigProvider) {
        this.requestConfigProvider = requestConfigProvider;
    }
}
