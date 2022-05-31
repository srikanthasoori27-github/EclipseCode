/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Object that represents one line in a batch request file
 *
 */
@XMLClass
public class BatchRequestItem extends SailPointObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass(xmlname = "BatchRequestItemStatus")
        public static enum Status
        {
            Running(MessageKeys.BATCH_REQUEST_ITEM_STATUS_RUNNING), 
            Finished(MessageKeys.BATCH_REQUEST_ITEM_STATUS_FINISHED), 
            Terminated(MessageKeys.BATCH_REQUEST_ITEM_STATUS_TERMINATED), 
            Invalid(MessageKeys.BATCH_REQUEST_ITEM_STATUS_INVALID), 
            Rejected(MessageKeys.BATCH_REQUEST_ITEM_STATUS_REJECTED);
            
            private String messageKey;
            
            private Status(String messageKey) {
                this.messageKey = messageKey;
            }
            
            public String getMessageKey() {
                return this.messageKey;
            }
        }
    
    @XMLClass(xmlname = "BatchRequestItemResult")
        public static enum Result
        {
            Success(MessageKeys.BATCH_REQUEST_ITEM_RESULT_SUCCESS), 
            Failed(MessageKeys.BATCH_REQUEST_ITEM_RESULT_FAILED), 
            Approval(MessageKeys.BATCH_REQUEST_ITEM_RESULT_APPROVAL), 
            Skipped(MessageKeys.BATCH_REQUEST_ITEM_RESULT_SKIPPED), 
            ManualWorkItem(MessageKeys.BATCH_REQUEST_ITEM_RESULT_MANUAL_WORK_ITEM), 
            PolicyViolation(MessageKeys.BATCH_REQUEST_ITEM_RESULT_POLICY_VIOLATION), 
            ProvisioningForm(MessageKeys.BATCH_REQUEST_ITEM_RESULT_PROVISIONING_FORM);

            private String messageKey;

            private Result(String messageKey) {
                this.messageKey = messageKey;
            }

            public String getMessageKey() {
                return this.messageKey;
            }
        }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static final long serialVersionUID = 1794512274227037327L;
    private static final Log log = LogFactory.getLog(BatchRequestItem.class);

    private String requestData;
    
    private String identityRequestId;

    private Status status;
    
    private Result result;
    
    private String targetIdentityId;
    
    private BatchRequest batchRequest;

    /**
     * 7.0 converted to use errorMessage Message object instead.
     *
     * @deprecated use {@link #errorMessage} instead.
     */
    @Deprecated
    private String message;

    private Message errorMessage;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public BatchRequestItem() {
    	
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitBatchRequestItem(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getRequestData() {
    	return requestData;
    }
    
    public void setRequestData(String rq) {
    	requestData = rq;
    }
    
    @XMLProperty
    public String getIdentityRequestId() {
    	return identityRequestId;
    }
    
    public void setIdentityRequestId(String rq) {
    	identityRequestId = rq;
    }
    
    @XMLProperty
    public Status getStatus() {
    	return status;
    }
    
    public void setStatus(Status s) {
    	status = s;
    }
    
    @XMLProperty
    public Result getResult() {
    	return result;
    }
    
    public void setResult(Result s) {
    	result = s;
    }
    
    @XMLProperty
    public String getTargetIdentityId() {
    	return targetIdentityId;
    }
    
    public void setTargetIdentityId(String s) {
    	targetIdentityId = s;
    }
    
    public BatchRequest getBatchRequest() {
    	return batchRequest;
    }
    
    public void setBatchRequest(BatchRequest br) {
    	batchRequest = br;
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
    public boolean hasName() {
        return false;
    }    
}
