/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class DeferredPlan {
    public ProvisioningPlan plan;
    public int delay;
    public String applicationName;

    public DeferredPlan() {
    }

    public DeferredPlan(int delay, ProvisioningPlan plan) {
        this.delay = delay;
        this.plan=plan;
    }

    @XMLProperty
    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public void setPlan(ProvisioningPlan plan) {
        this.plan = plan;
    }

    @XMLProperty
    public ProvisioningPlan getPlan() {
        return plan;
    }

    @XMLProperty
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}