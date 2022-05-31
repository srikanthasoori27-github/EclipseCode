/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.extjs.ExtJSStateProvider;
import sailpoint.web.util.WebUtil;

/**
 * 
 * @author peter.holcomb
 *
 * This bean allows us to take state from the ui components and save
 * it in the back-end so that their ui preferences are persisted.  Currently
 * used to store/load state on Extjs components like grids.
 */
public class StateBean extends BaseBean implements ExtJSStateProvider {
	private static Log log = LogFactory.getLog(StateBean.class);
	
	private static String ACTIVE_TAB = "activeTab";

	String name;
	String state;
    String pageSize;
	String panelName;
    String activeTab;
    String attribute;
	IdentityService iSvc;

    public StateBean() {		
		setName(getRequestParameter("name"));
		setState(getRequestParameter("state"));
        setPageSize(getRequestParameter("pageSize"));
        setAttribute(getRequestParameter("attribute"));
		
		setActiveTab(getRequestParameter("activeTab"));
		
		/** If we are passing in a value and a name, we want to save the object state **/
		if(getState()!=null && !getState().equals("")) {
			saveState();
		}
        
        /** If we are passing in a page size and a name, we want to save the object's pageSize **/
        if(getPageSize()!=null && !getPageSize().equals("")) {
            savePageSize();
        }
        
        /** If we are passing in a page size and a name, we want to save the object's pageSize **/
        if(getAttribute()!=null && !getAttribute().equals("")) {
            saveAttribute();
        }
        
		/** If we are passing in a tab, we want to save the tab state **/
		if(activeTab != null && !activeTab.equals("")) {
			saveTabState();
		}
	}
	
    /**
     * Store the current panel and active tab on the session for 
     * later retrieval by a bean.
     */
	@SuppressWarnings({ "rawtypes", "unchecked" })
    private void saveTabState() {
		Map session = getSessionScope();
		session.put(ACTIVE_TAB, activeTab);
	}

	private void saveState() {
		if(getName()!=null) {
			log.debug("[Saving State] Name: " + getName() + " State: " + getState());
			try {
				Identity currentUser = getLoggedInUser();
				GridState gState = getISvc().getGridState(currentUser, getName());
				
				gState.setState(getState());
                getISvc().saveGridState(currentUser, gState);
			} catch(GeneralException ge) {
				log.warn("Unable to save grid state. Exception: " + ge);
			}
		}
	}
    
    private void savePageSize() {
        if(getName()!=null) {
            log.debug("[Saving Page Size] Name: " + getName() + " Page Size: " + getPageSize());
            try {
                Identity currentUser = getLoggedInUser();
                GridState gState = getISvc().getGridState(currentUser, getName());
                
                gState.setPageSize(Integer.parseInt(getPageSize()));
                getISvc().saveGridState(currentUser, gState);
            } catch(GeneralException ge) {
                log.warn("Unable to save grid state. Exception: " + ge);
            }
        }
    }

    private void saveAttribute() {
        if(getName()!=null) {
            log.debug("[Saving Attribute] Name: " + getName() + " Attribute: " + getAttribute());
            try {
                Identity currentUser = getLoggedInUser();
                GridState gState = getISvc().getGridState(currentUser, getName());
                
                gState.setAttribute(getAttribute());
                getISvc().saveGridState(currentUser, gState);
            } catch(GeneralException ge) {
                log.warn("Unable to save grid state. Exception: " + ge);
            }
        }
    }

	public String getState() {
		return state;
	}

	public void setState(String state) {
        // strip HTML from anything we get from the browser to prevent XSS attacks.
        // escape characters that could be used to inject javascript for same reason.
        this.state = WebUtil.escapeJavascript(WebUtil.stripHTML(state));
	}

    /**
     * @return the state in a JSON string.
     */
    public String getStateJSON() {
        String state = getState();
        return WebUtil.simpleJSONKeyValue("state", state);
    }
     

    public String getName() {
		return name;
	}

    public void setName(String name) {
        // strip HTML from anything we get from browser to prevent XSS attacks.
        // escape characters that could be used to inject javascript for same reason.
        this.name = WebUtil.escapeJavascript(WebUtil.stripHTML(name));
    }

	@SuppressWarnings("rawtypes")
    public String getActiveTab() {
		Map session = getSessionScope();
		
		// clean any existing tab off of the session
		activeTab = (String)session.remove(ACTIVE_TAB);
		
		// if there's not a tab in the session, or if a reset is requested,
		// default to the initial tab
		if ((activeTab == null) || (getRequestParameter("resetTab") != null)) {
			activeTab = "0";
		}

		return activeTab;
	}

	public void setActiveTab(String activeTab) {
		this.activeTab = activeTab;
	}

    
    public IdentityService getISvc() {
        if(iSvc==null) {
            /** if a value has been passed in, we need to save it **/
            iSvc = new IdentityService(getContext());
        }
        return iSvc;
    }

    public void setISvc(IdentityService svc) {
        iSvc = svc;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

}
