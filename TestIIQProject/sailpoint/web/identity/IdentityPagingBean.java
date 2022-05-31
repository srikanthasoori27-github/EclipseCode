/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.web.BaseBean;

/**
 * Backing bean for paging through the identity page.  
 * @author peter.holcomb
 *
 */
public class IdentityPagingBean extends BaseBean {
	
	private static final Log log = LogFactory.getLog(IdentityPagingBean.class);
	public static final String ATT_IDENTITY_IDS = "IdentityPagingBeanCurrentIdentities";
	public static final String ATT_CURRENT_IDENTITY_ID = "IdentityPagingBeanCurrentIdentity";
	
	/** List of identity ids to page through **/
	List<String> identityIds;
	
	/** The current identity **/
	private String currentIdentityId;
	
	private String nextIdentityId;
	
	private String prevIdentityId;

	private int currentIdentityIndex;
	
	
	public IdentityPagingBean() {
		restore();
	}
	
	public IdentityPagingBean(String currentIdentityId) {
		this.currentIdentityId = currentIdentityId;
		restore();
	}
	
	private void restore(){
		Map session = getSessionScope();
        identityIds = (List)session.get(ATT_IDENTITY_IDS);
        log.debug("[pagingbean.restore] Identity List: " + identityIds);
        
        if(identityIds!=null && !identityIds.isEmpty()) {
        	if(currentIdentityId==null)
        		currentIdentityId = identityIds.get(0);
        	
        	currentIdentityIndex = identityIds.indexOf(currentIdentityId);
        	
        	if(currentIdentityIndex<identityIds.size()-1)
        		nextIdentityId = identityIds.get(currentIdentityIndex+1);
    		else
    			nextIdentityId = null;
        	
        	if(currentIdentityIndex>0)
        		prevIdentityId = identityIds.get(currentIdentityIndex-1);
    		else
    			prevIdentityId = null;
        }
	}


	public String getCurrentIdentityId() {
		return currentIdentityId;
	}

	public void setCurrentIdentityId(String currentIdentityId) {
		this.currentIdentityId = currentIdentityId;
	}

	public List<String> getIdentityIds() {
		return identityIds;
	}

	public void setIdentityIds(List<String> identityIds) {
		this.identityIds = identityIds;
	}

	public String getNextIdentityId() {
		
		return nextIdentityId;
	}

	public void setNextIdentityId(String nextIdentityId) {
		this.nextIdentityId = nextIdentityId;
	}

	public String getPrevIdentityId() {
		
		return prevIdentityId;
	}

	public void setPrevIdentityId(String prevIdentityId) {
		this.prevIdentityId = prevIdentityId;
	}

	public int getCurrentIdentityIndex() {
		return currentIdentityIndex+1;
	}

	public void setCurrentIdentityIndex(int currentIdentityIndex) {
		this.currentIdentityIndex = currentIdentityIndex;
	}

}
