/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class EntitlementMiningBucketBean extends BaseBean
{
    private static final Log log = LogFactory.getLog(EntitlementMiningBucketBean.class);
    int id;
    int count;
    int total;
    String name;
    AttrSelectBean attr;
    List<EntitlementMiningBucketBean> childBuckets;
    List<EntitlementMiningIdentityBean> identities;
    String completeClass;
    String applicationId;
    
    /** Used by the UI to determine whether it needs to load the identities into the 
     * results **/
    boolean renderIdentities;
    int percent;        

    public EntitlementMiningBucketBean () { }

    public EntitlementMiningBucketBean(int total) {
        this.total = total;
    }
    /**
     * @return the completeClass
     */
    public String getCompleteClass() {
        completeClass = "";
        if(getPercent() >= 80f)
            completeClass = "progressBarCompleteGreen";
        else if(getPercent() >= 30f && getPercent() < 80f)
            completeClass = "progressBarCompleteYellow";
        else 
            completeClass = "progressBarCompleteRed";

        return completeClass;
    }
    /**
     * @param completeClass the completeClass to set
     */
    public void setCompleteClass(String completeClass) {
        this.completeClass = completeClass;
    }
    /**
     * @return the count
     */
    public int getCount() {
        if(identities==null) {
            count = 0;
        }
        else {
            count = identities.size();
        }
        return count;
    }

    /**
     * @return the percentComplete
     */
    public int getPercent() {
        if(total > 0) {
            percent = Math.round(((float)getCount() / getTotal()) * 100);
        }
        else {
            percent = 0;
        }
        return percent;
    }

    /**
     * @param percentComplete the percentComplete to set
     */
    public void setPercent(int percent) {
        this.percent = percent;
    }

    /**
     * @return the attr
     */
    public AttrSelectBean getAttr() {
        return attr;
    }

    /**
     * @param attr the attr to set
     */
    public void setAttr(AttrSelectBean attr) {
        this.attr = attr;
    }

    /**
     * @return the total
     */
    public int getTotal() {
        return total;
    }

    /**
     * @param total the total to set
     */
    public void setTotal(int total) {
        this.total = total;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the childBuckets
     */
    public List<EntitlementMiningBucketBean> getChildBuckets() {
        return childBuckets;
    }

    /**
     * @param childBuckets the childBuckets to set
     */
    public void setChildBuckets(List<EntitlementMiningBucketBean> childBuckets) {
        this.childBuckets = childBuckets;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the applicationId
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * @param applicationId the applicationId to set
     */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }
    
    public String getApplication() {
        String appName;
        
        if (applicationId != null) {
            Application app;
            try {
                app = getContext().getObjectById(Application.class, applicationId);
            } catch (GeneralException e) {
                app = null;
                log.error("Failed to get an application with ID " + applicationId, e);
            }
            if (app == null) {
                appName = "";
            } else {
                appName = app.getName();
            }
        } else {
            appName = "";
        }
        
        return appName;
    }

    /**
     * @return the renderIdentities
     */
    public boolean isRenderIdentities() {
        return renderIdentities;
    }

    /**
     * @param renderIdentities the renderIdentities to set
     */
    public void setRenderIdentities(boolean renderIdentities) {
        this.renderIdentities = renderIdentities;
    }

	public List<EntitlementMiningIdentityBean> getIdentities() {
		if(identities!=null)
			Collections.sort(identities, EntitlementMiningIdentityBean.COMPARATOR);
		return identities;
	}

	public void setIdentities(List<EntitlementMiningIdentityBean> identities) {
		this.identities = identities;
	}
	
	public void addIdentity(EntitlementMiningIdentityBean identity) {
        if(identities==null) {
        	identities = new ArrayList<EntitlementMiningIdentityBean>();
        }
        if(!identities.contains(identity)){
        	identities.add(identity);
        }
    }
}
