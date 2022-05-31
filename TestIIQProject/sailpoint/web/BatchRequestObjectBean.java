/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Date;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BatchRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;

/**
 * Batch request details bean. Shows each batch request item.
 * Might be batch request item list bean but it also shows batch request info.
 * 
 * @author patrick.jeong
 *
 */
public class BatchRequestObjectBean extends BaseObjectBean<BatchRequest> 
implements NavigationHistory.Page {
    private static Log log = LogFactory.getLog(BatchRequestObjectBean.class);
    
    ////////////////////////////////////////////////////////////////////////////
    // FIELDS
    ////////////////////////////////////////////////////////////////////////////

    private static final String GRID_STATE = "batchRequestGridState";
    
    private String fileName;
    private BatchRequest requestObject;
    private Integer totalRecords;
    private Integer totalCompleted;
    private Integer totalError;
	private Integer totalInvalid;
    private BatchRequest.Status status;
    private Date requestedDate;
    
	private Date launchedDate;
    private Date completedDate;
    
    ////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////////

	public BatchRequestObjectBean() throws GeneralException {
    	super();
        setScope(BatchRequest.class);
        restoreObjectId();
        restore();
        log.debug("id = " + getObjectId());
    }
	
    ////////////////////////////////////////////////////////////////////////////
    // Utility methods
    ////////////////////////////////////////////////////////////////////////////
	
	private void restore() {
        // Call getObject() to trigger a force load as needed
        try {
            getObject();
        } catch (GeneralException e) {
            log.error("Failed to properly restore the BatchRequestObjectBean. ", e);
        }
	}
	
	  /**
     * Locate the unique id of the object we are editing. If not there,
     * fall back to the common post parameter conventions.
     */
	protected void restoreObjectId() {

        if (Util.isNotNullOrEmpty(getObjectId())) {
            return;
        }
        
		// the other convention on some pages
		Map map = getSessionScope();
		String id = (String) map.get(BatchRequestListBean.ATT_OBJECT_ID);

		setObjectId(id);
	}
    
    /**
     * @return
     */
    public String getMessage() {
        String msg = null;
        if (requestObject != null) {
            msg = getMessage(requestObject.getErrorMessage());
        }
        return msg;
    }
    
    /**
     * @return
     */
    /* (non-Javadoc)
     * @see sailpoint.web.BaseObjectBean#getObject()
     */
    @SuppressWarnings("unchecked")
    public BatchRequest getObject() throws GeneralException {
    	if (requestObject  == null) {
    		requestObject = super.getObject();
    	}

    	return requestObject;
    }
    
    public int getItemCount() throws GeneralException {
    	BatchRequest request  = getObject();
    	return request.getBatchRequestItems().size();
    }
    
	public String getFileName() throws GeneralException {
		if (fileName == null && getObject() != null) {
			fileName = requestObject.getFileName();
		}
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
    
	public int getTotalRecords() throws GeneralException {
		if (totalRecords == null) {
			totalRecords = getObject().getRecordCount();
		}
		return totalRecords;
	}

	public void setTotalRecords(int totalRecords) {
		this.totalRecords = totalRecords;
	}

	public BatchRequest.Status getStatus() throws GeneralException {
		if (status == null) {
			status = getObject().getStatus();
		}
		return status;
	}

	public void setStatus(BatchRequest.Status status) {
		this.status = status;
	}
    
    public String getStatusMessage() throws GeneralException {
        String message = "";
        if (getStatus() != null) {
            message = getMessage(getStatus().getMessageKey());
        }
        return message;
    }

	public Date getRequestedDate() throws GeneralException {
		if (requestedDate == null) {
			requestedDate = getObject().getCreated();
		}
		return requestedDate;
	}

	public void setRequestedDate(Date requestedDate) {
		this.requestedDate = requestedDate;
	}

	public Date getLaunchedDate() throws GeneralException {
		if (launchedDate == null) {
			launchedDate = getObject().getRunDate();
		}
		return launchedDate;
	}

	public void setLaunchedDate(Date launchedDate) {
		this.launchedDate = launchedDate;
	}

	public Date getCompletedDate() throws GeneralException {
		if (completedDate == null) {
			completedDate = getObject().getCompletedDate();
		}
		return completedDate;
	}

	public void setCompletedDate(Date completedDate) {
		this.completedDate = completedDate;
	}

    public Integer getTotalCompleted() throws GeneralException {
    	if (totalCompleted == null) {
    		totalCompleted = getObject().getCompletedCount();
		}
		return totalCompleted;
	}

	public void setTotalCompleted(Integer totalCompleted) {
		this.totalCompleted = totalCompleted;
	}

	public Integer getTotalError() throws GeneralException {
		if (totalError == null) {
			totalError = getObject().getErrorCount();
		}
		return totalError;
	}

	public void setTotalError(Integer totalError) {
		this.totalError = totalError;
	}

	public Integer getTotalInvalid() throws GeneralException {
		if (totalInvalid == null) {
			totalInvalid = getObject().getInvalidCount();
		}
		return totalInvalid;
	}

	public void setTotalInvalid(Integer totalInvalid) {
		this.totalInvalid = totalInvalid;
	}
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods 
    //
    ////////////////////////////////////////////////////////////////////////////
    
	public String getPageName() {
		return "Batch Request Details";
	}

	public String getNavigationString() {
	   
		return "viewBatchRequest";
	}

	public Object calculatePageState() {
	    Object[] state = new Object[1];
//        state[0] = requestId;
        return state;
	}

	public void restorePageState(Object state) {
	    Object[] myState = (Object[]) state;
        setObjectId((String)myState[0]);
	}
	
	public String getGridStateName() { 
		return GRID_STATE; 
	}
}
