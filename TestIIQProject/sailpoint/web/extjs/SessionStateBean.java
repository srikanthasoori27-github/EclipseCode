/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ExtState;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.util.WebUtil;

/**
 * This bean allows us to take state from ExtJS components and save
 * it in the session so that it can be retrieved as needed from there.
 * @author Peter Holcomb
 * @author Bernie Margolis
 */
public class SessionStateBean extends BaseBean implements ExtJSStateProvider {
	private static Log log = LogFactory.getLog(SessionStateBean.class);
	public static final String SESSION_STATE = "SESSION_STATE";
    
	String name;
	String state;
    
	public SessionStateBean() {
        setName(getRequestParameter("name"));
		setState(getRequestParameter("state"));
        
		/** If we are passing in a value and a name, we want to save the object state **/
		if(getState() != null && !getState().equals("")) {
		    saveState();
		} else {
		    initState();
        }
    }
	
    private void saveState() {
	    if(getName() != null) {
			try {
                String stateKey = getSessionKey(getName());
                ExtState extState = (ExtState) getSessionScope().get(stateKey);

                if (extState == null) {
				    extState = new ExtState();
                    extState.setName(getName());
                }
                
				extState.setState(getState());
                getSessionScope().put(stateKey, extState);
			} catch(GeneralException ge) {
				log.warn("Unable to save state. Exception: " + ge);
			}
		}
	}
    
    private void initState() {
        try {
            String stateKey = getSessionKey(getName());
            ExtState extState = (ExtState) getSessionScope().get(stateKey);
            if (extState != null)
                setState(extState.getState());
        } catch (GeneralException e) {
            log.warn("Unable to initialize state. Exception: " + e);
        }
    }
    
    public String getState() {
        return state;
    }

    /**
     * @return the state in a JSON string.
     */
    public String getStateJSON() {
        String state = getState();
        return WebUtil.simpleJSONKeyValue("state", state);
    }


    public void setState(String state) {
        // strip HTML from anything we get from the browser to prevent XSS attacks.
        // escape characters that could be used to inject javascript for same reason.
        this.state = WebUtil.escapeJavascript(WebUtil.stripHTML(state));
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    private String getSessionKey(String name) throws GeneralException {
        Identity currentUser = getLoggedInUser();
        return currentUser.getId() + ":" + name + ":" + SESSION_STATE;
    }
}
