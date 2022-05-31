/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used maintain results from a provisioning request sent to 
 * an application connector or a provisioning system.
 * 
 * Author: Jeff
 *
 * This is similar to sailpoint.integration.RequestResult but is part
 * of the IIQ model so it can use JDK 1.5isms and have an XML serialization.
 * It also adds a reference to the target Application (or IntegrationConfig)
 * so we can keep these on a list and present them in context.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.RequestResult;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used maintain results from a request to a provisioning system.
 */
@XMLClass
public class ProvisioningResult extends AbstractXmlObject {

    private static final Log log = LogFactory.getLog(ProvisioningResult.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Status Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Indicates that the request was accepted and passed validation,
     * but it was given to another system and we do not know
     * when it will be committed. If possible the connector
     * should also set a request id for tracking.
     *
     * This is the default status, if no result object is returned or
     * if the result object is missing a status, it can be assumed
     * to have been queued.
     */
    public static final String STATUS_QUEUED = "queued";
    
    /**
     * Indicates that the request was fully processed and the changes 
     * are known to have been made. The plan evaluator can use this
     * to immediately update the identity to reflect the changes rather
     * than waiting for the next aggregation.
     */
    public static final String STATUS_COMMITTED = "committed";

    /**
     * Indicates that the request was not processed due to a
     * fatal error.  The connector should save one or more
     * messages on the error list.
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * Indicates that the request was not processed due to a
     * non fatal error and that it should be retried at a later
     * time. The connector may also set the retryInterval property
     * to indicate the preferred wait time.
     */
    public static final String STATUS_RETRY = "retry";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the Application (or IntegrationConfig) that was used
     * to process the request. This will match the targetIntegration 
     * property of the associated ProvisioningPlan.
     */
    String _targetIntegration;

    /**
     * Status of plan evaluation. One of the STATUS constants above.
     */
    String _status;

    /**
     * Optional request id returned by the connector when
     * the status is STATUS_QUEUED.
     */
    String _requestID;

    /**
     * Warning messages returned by the connector.
     * These may be returned for all status codes including
     * STATUS_COMMITTED and STATUS_QUEUED. They are expected
     * to be shown to a user but will not prevent the request
     * from being considered successfully processed.
     */
    List<Message> _warnings;

    /**
     * Error messages returned by the connector when status
     * is STATUS_FAILED.
     */
    List<Message> _errors;

    /**
     * Optional preferred amount of time to wait before
     * the request is retried. The value is in seconds.
     * This is ignored unless status is STATUS_RETRY.
     * If not specified, a default retry period is taken
     * from the system configuration.
     */
    int _retryInterval;

    /**
     * An object that may be optionally returned by the connector
     * in the result stored within an AccountRequest.  When this
     * is returned it will contain all of the attributes of the
     * updated account, which can include things that were not
     * in the request. This can then be used by the plan evaluator
     * to refresh the identity model in a way similar to aggregation.
     *
     * @exclude
     * TODO: If we're going to call this "aggregation" then we need to 
     * be clear on what else happens like attribute promotion, role
     * correlation etc.  If that can now happen underneath provisioning
     * we'll need a way to pass all those options down.
     */
    ResourceObject _object;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningResult() {
    }

    /**
     * Convert a RequestResult object returned by older IntegrationExecutors
     * into a new provisioning result.
     *
     * @ignore
     * The only trick here is the status codes which have
     * been simplified in 5.2.  Assuming that we don't
     * have to priority order status codes, the last result
     * wins.  Normally the plan initializer is assimilated first
     * and that can only succeed or fail, then if that succeeds
     * we assimilate the executor result.
     */
    public ProvisioningResult(RequestResult src) {
        if (src != null) {
            // status requires transformations
            setStatus(src.getStatus());
            _requestID = src.getRequestID();
            _retryInterval = src.getRetryWait();
            _warnings = merge(_warnings, src.getWarnings(), false, false);
            _errors = merge(_errors, src.getErrors(), false, true);
        }
    }

    public ProvisioningResult(Map src) {
        fromMap(src);
    }
    
    /**
     * Merge warning and error messages from one result into another.
     */
    public void assimilateMessages(ProvisioningResult src, boolean before) {
        if (src != null) {
            _warnings = merge(_warnings, src.getWarnings(), before, false);
            _errors = merge(_errors, src.getErrors(), before, true);
        }
    }

    public void assimilateMessages(ProvisioningResult src) {
        assimilateMessages(src, false);
    }

    /**
     * Assimilation helper, merge a list of messages into one of our lists.
     * Support message lists from the older RequestResult objects that
     * are strings and convert them to Messages.
     */
    private List<Message> merge(List<Message> orig, List neu, boolean before,
                                boolean error) {

        List<Message> merged = orig;
        if (neu != null) {
            List<Message> converted = messageListify(neu, error);
            if (merged == null)
                merged = converted;
            else {
                if (before)
                    merged.addAll(converted);
            }
        }
        return merged;
    }

    /**
     * Utility to convert the untyped list from a RequestResult
     * into a List<Message>. These can not be translated, but
     * everything else in the system is set up to deal with Message
     * so wrap them.
     */
    private List<Message> messageListify(List src, boolean error) {
        List<Message> list = null;
        if (src != null) {
            list = new ArrayList<Message>();
            for (Object o : src) {
                if (o instanceof Message)
                    list.add((Message)o);
                else if (o != null) {
                    Message.Type type = (error) ? Message.Type.Error : Message.Type.Warn;
                    list.add(new Message(type, o.toString()));
                }
            }
        }
        return list;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the name of the IntegrationConfig used to process this request.
     */
    @XMLProperty
    public String getTargetIntegration() {
        return _targetIntegration;
    }

    public void setTargetIntegration(String s) {
        _targetIntegration = s;
    }

    /**
     * Return the status - one of the STATUS_* constants.
     */
    @XMLProperty
    public String getStatus() {
        return _status;
    }

    /**
     * Set the request status.
     *
     * A few of the older RequestResult status codes will be
     * automatically upgraded into 5.2 status code if they
     * are seen here.  "success" becomes "committed" and 
     * "failure" becomes "failed".
     */
    public void setStatus(String s) {

        // Convert old RequestResult status codes into new ones
        
        if (RequestResult.STATUS_SUCCESS.equals(s)) {
            // Originally I wanted to assume STATUS_QUEUED only
            // if the requestID was non-null, but most older integrations
            // don't set a requestID so we have to assume queued.
            s = STATUS_QUEUED;
        }
        else if (RequestResult.STATUS_COMMITTED.equals(s)) {
            // don't need this since the names are the same but I wanted
            // an explicit clause for every RequestResult constant to
            // make it clear
            s = STATUS_COMMITTED;
        }
        else if (RequestResult.STATUS_FAILURE.equals(s)) {
            s = STATUS_FAILED;
        }   
        else if (RequestResult.STATUS_RETRY.equals(s)) {
            s = STATUS_RETRY;
        }
        else if (RequestResult.STATUS_WARNING.equals(s)) {
            // I don't think this was ever used
            // This should probably be QUEUED since old executors never
            // supported COMMITTED but it's too late to change now.
            log.warn("STATUS_WARNING found in RequestResult");
            s = STATUS_COMMITTED;
        }
        else if (RequestResult.STATUS_NOT_STARTED.equals(s)) {
            // I don't think this was ever used
            log.warn("STATUS_NOT_STARTED found in RequestResult");
            // Bug 14932 - notStarted should be treated as queued instead of failed
            s = STATUS_QUEUED;
        }
        else if (RequestResult.STATUS_IN_PROCESS.equals(s)) {
            // Remedy integration uses this to mean QUEUED
            log.info("STATUS_IN_PROCESS found in RequestResult");
            s = STATUS_QUEUED;
        }

        _status = s;
    }

    /**
     * Return the ID of the request if available. This can be used in the
     * IntegrationInterface by getRequestStatus() to check on the request later.
     */
    @XMLProperty
    public String getRequestID() {
        return _requestID;
    }

    public void setRequestID(String s) {
        _requestID = s;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Message> getWarnings() {
        return _warnings;
    }

    public void setWarnings(List<Message> l) {
        _warnings = l;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Message> getErrors() {
        return _errors;
    }

    public void setErrors(List<Message> l) {
        _errors = l;
    }

    /**
     * Set the minimum number of seconds to wait until the next retry.
     * If zero the wait can be defined in the Application 
     * (or IntegrationConfig) and if not there by a global system default.
     */
    @XMLProperty
    public int getRetryInterval() {
        return _retryInterval;
    }

    public void setRetryInterval(int i) {
        _retryInterval = i;
    }

    /**
     * Return the object holding the updated account attributes.
     * This may be optionally set by the connector for a result stored
     * within an AccountRequest. When set, the plan evaluator will
     * refresh the identity with the new attributes.
     */
    public ResourceObject getObject() {
        return _object;
    }

    public void setObject(ResourceObject obj) {
        _object = obj;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void addError(Message m) {
        if (m != null) {
            if (_errors == null)
                _errors = new ArrayList<Message>();
            _errors.add(m);
        }
    }

    public void addError(String s) {
        if (s != null)
            addError(new Message(Message.Type.Error, s));
    }

    public void addError(Throwable t) {
        if (t != null) {
            // todo: stack track might be nice too
            addError(t.toString());
        }
    }
    public void addWarning(List<Message> m) {
        if (m != null) {
            if (_warnings == null)
                _warnings = new ArrayList<Message>();
            _warnings.addAll(m);
        }
    }
    public void addWarning(Message m) {
        if (m != null) {
            if (_warnings == null)
                _warnings = new ArrayList<Message>();
            _warnings.add(m);
        }
    }

    public void addWarning(String s) {
        if (s != null)
            addWarning(new Message(Message.Type.Warn, s));
    }

    public void fail() {
        setStatus(STATUS_FAILED);
    }

    public void fail(Throwable t) {
        fail();
        addError(t);
    }

    public boolean isCommitted() {
        return STATUS_COMMITTED.equals(_status);
    }

    public boolean isQueued() {
        return _status == null || STATUS_QUEUED.equals(_status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(_status);
    }

    public boolean isRetry() {
        return STATUS_RETRY.equals(_status);
    }

    public boolean isSubmitted() {
        return isCommitted() || isQueued();
    }

    public boolean isFailure() {
        return !isSubmitted();
    }

    public boolean hasMessages() {
        return ((_warnings != null && _warnings.size() > 0) ||
                (_errors != null && _errors.size() > 0));
    }
    
  	 public Map<String,Object> toMap() {
     	Map<String, Object> resultMap = new HashMap<String,Object>();
     	resultMap.put("targetIntegration", _targetIntegration ); 
     	resultMap.put("status", _status);
     	resultMap.put("requestID", _requestID);
     	if(_warnings != null ) {
	     	List<Map<String,String>> warnStringList = new ArrayList<Map<String,String>>();
	     	for(Message msg : _warnings) {
	     		Map<String, String> msgInfo = new HashMap<String, String>();
	     		String localizedMessage = msg.getLocalizedMessage();
	     		if(localizedMessage != null) {
	     			msgInfo.put("warning_msg", localizedMessage);
	     			msgInfo.put("warning_type", msg.getType().toString());
	     		}
	     		warnStringList.add(msgInfo);
	     	}
	     	resultMap.put("warnings", warnStringList);
     	}
     	if(_errors != null ) {
	     	List<Map<String,String>> errorStringList = new ArrayList<Map<String,String>>();
	     	for(Message msg : _errors) {
	     		Map<String, String> msgInfo = new HashMap<String, String>();
	     		String localizedMessage = msg.getLocalizedMessage();
	     		if(localizedMessage != null) {
	     			msgInfo.put("error_msg", localizedMessage);
	     			msgInfo.put("error_type", msg.getType().toString());
	     		}
	     		errorStringList.add(msgInfo);
	     	}
	     	resultMap.put("errors", errorStringList);
     	}
     	resultMap.put("retryInterval",new Integer(_retryInterval));
     	if(_object != null)
     		resultMap.put("resourceObject", _object.toMap());
     	return resultMap;
     }
    	 
    	 @SuppressWarnings("unchecked")
 	public void fromMap(Map map) {
     	_targetIntegration = (String)map.get("targetIntegration");
     	_status = (String)map.get("status");
     	_requestID = (String)map.get("requestID");
     	_retryInterval = Util.getInt(map,"retryInterval");
     	//_warnings = (List<Message>)map.get("warnings");
     	List<Map<String,String>> warnStringList = (List<Map<String,String>>)map.get("warnings");
     	if(warnStringList != null && warnStringList.size() > 0 ) {
	     	for(Map<String,String> warnString : warnStringList) {
	     		if (_warnings == null)
	                 _warnings = new ArrayList<Message>();
	     		String localizedMsg = warnString.get("warning_msg");
	     		if(localizedMsg != null) {
	     			String type = warnString.get("warning_type");
	     			Object[] args =null ;
	     			Message msg = new Message(Message.Type.Warn, localizedMsg, args);
	     			_warnings.add(msg);
	     		}
	     	}
     	}
     	//_errors = (List<Message>)map.get("errors");
     	List<Map<String,String>> errorStringList = (List<Map<String,String>>)map.get("errors");
     	if(errorStringList != null && errorStringList.size() > 0 ) {
	     	for(Map<String,String> errorObj : errorStringList) {
	     		if (_errors == null)
	     			_errors = new ArrayList<Message>();
	     		String localizedMsg = errorObj.get("error_msg");
	     		if(localizedMsg != null) {
	     			String type = errorObj.get("error_type");
	     			Message msg = new Message(Message.Type.Error, localizedMsg);
	     			_errors.add(msg);
	     		}
	     	}
     	}
     	Object o = map.get("resourceObject");
     	if( o != null && o instanceof Map ) {
     		_object = new ResourceObject((Map)o);
     	}
   	 }


}
