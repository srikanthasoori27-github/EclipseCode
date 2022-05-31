package sailpoint.rest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;

@Path("tabState")
public class TabStateResource extends BaseResource {
    private static final Log log = LogFactory.getLog(TabStateResource.class);
    /**
     * Updates the session with the latest tab state
     * @param tabPanelId ID of the tab panel whose state is being saved.
     * @param activeTab tab that we want to update the state with 
     */
    @POST
    @Path("session/update")
    public Map<String, Object> updateSession(@FormParam("tabPanelId") String tabPanelId, @FormParam("activeTab") String activeTab ) 
    		throws GeneralException {
    	
        //Used in RoleModeler
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        request.getSession().setAttribute(getSessionKey(tabPanelId), activeTab);
        log.debug("Updated tab state.  Active panel is now " + activeTab);
        Map<String, Object> results = new HashMap<String, Object>();
        results.put("tabStateUpdated", true);
        return results;
    }
    
    private String getSessionKey(String tabPanelId) {
        return "tabState:" + tabPanelId;
    }
}
