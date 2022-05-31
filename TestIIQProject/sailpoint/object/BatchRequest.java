/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * This class represents the batch request file.
 * It contains the actual file contents for use later by the task scheduler.
 * This object will also track the request execution status.
 * 
 * @author patrick.jeong
 *
 */
@XMLClass
public class BatchRequest extends SailPointObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

	private static final long serialVersionUID = 1L;
   
    
    //////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////
	/**
     * Batch request completion states.
     */
    @XMLClass(xmlname = "BatchRequestStatus")
    public static enum Status
    {
        /**
         * The batch request is scheduled for a future date.
         */
        Scheduled(MessageKeys.BATCH_REQUEST_STATUS_SCHEDULED),
        /**
         * The batch request did not pass validation.
         */
        Invalid(MessageKeys.BATCH_REQUEST_STATUS_INVALID),

        /**
         * The batch request was cancelled before or during execution.
         */
        Terminated(MessageKeys.BATCH_REQUEST_STATUS_TERMINATED),

        /**
         * Waiting on approval
         */
        Approval(MessageKeys.BATCH_REQUEST_STATUS_APPROVAL),

        /**
         * Approval Rejected
         */
        Rejected(MessageKeys.BATCH_REQUEST_STATUS_REJECTED),

        Executed(MessageKeys.BATCH_REQUEST_STATUS_EXECUTED),

        Running(MessageKeys.BATCH_REQUEST_STATUS_RUNNING);

        private String messageKey;

        private Status(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public BatchRequest() {
    	
    }


    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(BatchRequest.class);
    
    private String fileName;
    
    private Date completedDate;
    private Date runDate;
    
    private String fileContents;
    
    // Just the header part for convenience later.
    private String header;

    /**
     * 7.0 converted to use errorMessage Message object instead.
     *
     * @deprecated use {@link #errorMessage} instead
     */
    @Deprecated
    private String message;

    private Message errorMessage;

    private int recordCount;
    
    private Status status;
    
    private Attributes<String, Object> runConfig;
    
    List<BatchRequestItem> batchRequestItems = new ArrayList<BatchRequestItem>();
    
    // How many items completed?
    private int completedCount = 0;
    
    // How many items errored out?
    private int errorCount = 0;
    
    // How manu items were invalid?`
    private int invalidCount = 0;

	private String[] uniqueKeyProperties;
    
    @XMLProperty
    public String getFileName() {
    	return fileName;
    }
    
    public void setFileName(String fname) {
    	fileName = fname;
    }
    
    @XMLProperty
    public void setCompletedDate(Date d)
    {
        completedDate = d;
    }

    public Date getCompletedDate()
    {
        return completedDate;
    }
    
    @XMLProperty
    public void setRunDate(Date d)
    {
        runDate = d;
    }

    public Date getRunDate()
    {
        return runDate;
    }
    
    @XMLProperty
    public int getRecordCount() {
    	return recordCount;
    }
    
    public void setRecordCount(int rc) {
    	recordCount = rc;
    }
    
    @XMLProperty
    public int getCompletedCount() {
    	return completedCount;
    }
    
    public void setCompletedCount(int cc) {
    	completedCount = cc;
    }
    
    @XMLProperty
    public int getErrorCount() {
    	return errorCount;
    }
    
    public void setErrorCount(int rc) {
    	errorCount = rc;
    }
    
    @XMLProperty
    public int getInvalidCount() {
    	return invalidCount;
    }
    
    public void setInvalidCount(int rc) {
    	invalidCount = rc;
    }
    
    @XMLProperty
    public String getFileContents() {
    	return fileContents;
    }
    
    public void setFileContents(String fc) {
    	fileContents = fc;
    }
    
    @XMLProperty
    public List<BatchRequestItem> getBatchRequestItems() {
    	return batchRequestItems;
    }
    
    public void setBatchRequestItems(List<BatchRequestItem> items) {
    	batchRequestItems = items;
    	recordCount = items.size();
    }
    
    @XMLProperty
    public Status getStatus() {
    	return status;
    }
    
    public void setStatus(Status st) {
    	status = st;
    }
    
    @XMLProperty
    public Attributes<String, Object> getRunConfig() {
    	return runConfig;
    }
    
    public void setRunConfig(Attributes<String, Object>  st) {
    	runConfig = st;
    }
    
    @XMLProperty
    public String getHeader() {
    	return header;
    }
    
    public void setHeader(String hdr) {
    	header = hdr;
    }
    
    public void updateStats(BatchRequestItem.Result result) {
    	if (result != BatchRequestItem.Result.Success) {
    		errorCount++;
    	}
    	else if (result != null){
    		completedCount++;
    	}
    	
    	if ((completedCount + errorCount + invalidCount) == recordCount) {
    		status = BatchRequest.Status.Executed;
    		completedDate = new Date();
    	}
    }

    /**
     * @deprecated use {@link #getErrorMessage()} instead
     */
    @XMLProperty(legacy = true)
    @Deprecated
    public String getMessage() {
        return message;
    }

    /**
     * @deprecated use {@link #setErrorMessage(Message)} instead
     */
    @Deprecated
    public void setMessage(String msg) {
        message = msg;
    }
    
    @XMLProperty
    public Message getErrorMessage() {
        if (Util.isNotNullOrEmpty(message)) {
            errorMessage = Message.error(message);
        }
        return errorMessage;
    }

    public void setErrorMessage(Message msg) {
        errorMessage = msg;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitBatchRequest(this);
    }
    
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "ID");
        cols.put("fileName", "Filename");
        cols.put("created", "Launched");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-20s %-24s\n";        
    }
    
    public boolean hasName() {
    	return false;
    }
    
    @Override
    public String[] getUniqueKeyProperties() {
    	if (uniqueKeyProperties == null) {
            uniqueKeyProperties = new String[2];
            uniqueKeyProperties[0] = "fileName";
            uniqueKeyProperties[1] = "created";
        }
        return uniqueKeyProperties;
    }
}
