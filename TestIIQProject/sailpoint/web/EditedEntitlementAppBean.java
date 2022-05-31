/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;


/**
 * This bean is used to indicate which app is currently being edited by the entitlement 
 * risk score config pages.  If JSF had a proper parameter forwarding mechanism, this
 * wouldn't be necessary.
 * @author Bernie Margolis
 */
public class EditedEntitlementAppBean {
    private EntitlementsBarConfigBean.ApplicationEntitlement application;
    // This next field admittedly belongs elsewhere, but I don't want to add another managed bean
    private String currentRiskConfigTab;
    
    
    public EditedEntitlementAppBean() {
        super();
        
        application = null;
        currentRiskConfigTab = "baselineAccessRiskPanel";
    }

    public EntitlementsBarConfigBean.ApplicationEntitlement getApplication() {
        return application;
    }

    public void setApplication(
                    EntitlementsBarConfigBean.ApplicationEntitlement application) {
        this.application = application;
    }

    public String getCurrentRiskConfigTab() {
        return currentRiskConfigTab;
    }

    public void setCurrentRiskConfigTab(String currentRiskConfigTab) {
        this.currentRiskConfigTab = currentRiskConfigTab;
    }
}
