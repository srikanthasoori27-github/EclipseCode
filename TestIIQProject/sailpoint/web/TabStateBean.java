/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONWriter;

import sailpoint.object.Capability;
import sailpoint.object.SPRight;
import sailpoint.object.Identity.CapabilityManager;

/**
 * 
 * @author Bernie Margolis
 *
 * This simple bean helps maintain the current state of a tab panel for the session's duration.
 * It is not persistent, so any state maintained by this bean will go away when the session expires.
 * For full-featured persistent functionality use sailpoint.web.StateBean instead. 
 */
public class TabStateBean extends BaseBean {
	private static Log log = LogFactory.getLog(TabStateBean.class);
	
	private String tabPanelId;
	private String activeTab;

    public TabStateBean() {
        // Our initialization depends on the request paramters that are coming in.
        // Unfortunately at this point in the JSF lifecycle request parameters haven't
        // been applied so we have to pre-emptively do a little work on our own.
        tabPanelId = getRequestOrSessionParameter("tabState:tabPanelId");
        if (tabPanelId == null || tabPanelId.trim().length() == 0) {
            // TODO: This is not ideal as a long term solution, but I don't
            // have time to work out something better right now. --Bernie 
            tabPanelId = "roleTabPanel";
        } else {
            activeTab = (String) getSessionScope().get(getSessionKey());
        }
	}
    
    public String getTabPanelId() {
        return tabPanelId;
    }

    public void setTabPanelId(String tabPanelId) {
        this.tabPanelId = tabPanelId;
    }

    /**
     * @return The last tab state that was saved in the session.  If none was saved, null is returned.
     */
    public String getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(String activeTab) {
        this.activeTab = activeTab;
    }

    @SuppressWarnings("unchecked")
    public String updateState() {
        getSessionScope().put(getSessionKey(), activeTab);
        log.debug("Updated tab state.  Active panel is now " + activeTab);
        return "tabStateUpdated";
    }
    
    public String getRightsJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String rightsJson;
        try {
            CapabilityManager capabilityManager = getLoggedInUser().getCapabilityManager();
            List<Capability> capabilities = capabilityManager.getEffectiveCapabilities();
            Set<String> rightSet = new HashSet<String>();
            if (capabilities != null && !capabilities.isEmpty()) {
                for (Capability capability : capabilities) {
                    List<SPRight> rights = capability.getAllRights();
                    if (rights != null && !rights.isEmpty()) {
                        for (SPRight right : rights) {
                            rightSet.add(right.getName());
                        }
                    }
                }
                
                if (capabilityManager.hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
                    rightSet.add(Capability.SYSTEM_ADMINISTRATOR);
                }
            }
            jsonWriter.object();
            if (!rightSet.isEmpty()) {
                for (String rightName : rightSet) {
                    jsonWriter.key(rightName);
                    jsonWriter.value(true);
                }
            }
            jsonWriter.endObject();
            rightsJson = jsonString.toString();
        } catch (Exception e) {
            log.error("The TabStateBean was unable to determine capabilities for the current user.", e);
            rightsJson = "{}";
        }
        return rightsJson;
    }
    
    private String getSessionKey() {
        return "tabState:" + tabPanelId;
    }
}
