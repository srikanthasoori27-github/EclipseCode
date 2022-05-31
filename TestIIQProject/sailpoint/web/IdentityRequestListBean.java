/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.web.util.NavigationHistory;

/**
 * Actual list data for the Identity Request grid is being pulled from the list resource class.
 * This is to keep track of nav history.
 * 
 * @author patrick.jeong
 *
 */
public class IdentityRequestListBean extends BaseBean implements NavigationHistory.Page {

    private AccessRequestSearchSettings arSearchSettings;

	public IdentityRequestListBean() {
		super();

        this.setArSearchSettings((AccessRequestSearchSettings)getSessionScope().get("arSearchSettings"));
	}
	
	/**
	 * View Identity Request details
	 * 
	 * @return
	 */
	public String viewIdentityRequestDetail() {

    	String requestId  =  getRequestOrSessionParameter("mainForm:requestId");
    	
        // store requestId on session
		getSessionScope().put("editForm:id", requestId);
        
        NavigationHistory.getInstance().saveHistory(this);
        
        return "viewAccessRequestDetail#/request/" + requestId;
	}
	
	public String getPageName() {
		return "Identity Request List";
	}

	public String getNavigationString() {
		return "viewAccessRequests";
	}

	public Object calculatePageState() {
		// TODO Auto-generated method stub
		return null;
	}

	public void restorePageState(Object state) {
		// TODO Auto-generated method stub
		
	}
    
	public AccessRequestSearchSettings getArSearchSettings() {
		return arSearchSettings;
	}

	public void setArSearchSettings(AccessRequestSearchSettings arSearchSettings) {
		this.arSearchSettings = arSearchSettings;
	}

}
