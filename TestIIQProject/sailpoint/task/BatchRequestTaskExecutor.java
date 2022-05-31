/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BatchRequestValidator;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.api.Workflower;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequest;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.Configuration;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.BatchRequestLibrary;
import sailpoint.workflow.IdentityLibrary;

/**
 * This task is used to process batch request files.
 * The file content is parsed, validated, and then turned into BatchRequestItem objects.
 * Afterwards, the batch processing wrapper workflow is launched which will turn the
 * BatchRequestItems into attribute requests which will be added to a provisioning plan
 * and run through a workflow.
 * 
 * Here are the main steps:
 * 
 * 1. Parse out each request item.
 * 2. Validate headers and each request item.
 * 3. Create batch request items.
 * 4. Launch wrapper workflow.
 * 
 */
public class BatchRequestTaskExecutor extends AbstractTaskExecutor {
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    private static final Log log = LogFactory.getLog(BatchRequestTaskExecutor.class);

    public static final String ARG_BATCH_REQUEST = "batchRequestId";
    
    public static final String BATCH_REQUEST = "batchRequest";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    boolean ignoreErrors = true;
    boolean handleExistingCreate = true; 
	int stopNumber = 0;
    
	// number of records that errored out or skipped because of an error
	int errorCount = 0;
	
    /**
     * Set by the terminate method to indicate that we should stop
     * when convenient.
     */
    boolean _terminate;
   
    /** 
     * Cached copy of the context to avoid having to pass it
     * to every method.
     */
    SailPointContext _context;

    /** 
     * Cached copy of the arguments to avoid having to pass them to 
     * to every method that needs to use the args.
     */
    Attributes<String,Object> _arguments;

    /**
     * The result so various methods
     * can write warnings and errors if necessary.
     */
    TaskResult _result;
    
    List<String> headers;

    String lastOperationType;
    
    BatchRequest batchReq;
	
    Set<String> requiredFields = new HashSet<String>();
    
    RFC4180LineParser csvParser = new RFC4180LineParser(',');
    
    List<ObjectAttribute> attributes;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public BatchRequestTaskExecutor() {
    	// initialize statics
    	BatchRequestLibrary.initStatic();

        csvParser.setTrimValues(true);
        csvParser.tolerateMissingColumns();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////
    
    public boolean terminate() {
        _terminate = true;
        return _terminate;
    }

    /**
     * Main execution method.
     * Do some validation on headers and data.
     * Create batch request items.
     * Run the wrapper workflow
     */
    public void execute(SailPointContext context, TaskSchedule sched,
                        TaskResult result, Attributes<String, Object> args) 
        throws Exception {

        _terminate = false;
        _context = context;
        _arguments = args;
        _result = result;

        try {

            if (!args.containsKey(ARG_BATCH_REQUEST)) {
                throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_BATCH_REQUEST_ID));
            }

            // Get and set batchReq
            getBatchRequest((String)args.get(ARG_BATCH_REQUEST));

            String batchFileString = batchReq.getFileContents();

            String[]  batchRequestStrArr = null;

            // Do length prevalidation
            try {
                batchRequestStrArr = prevalidate(batchFileString);
            }
            catch (GeneralException ge) {
                // prevalidation failed, set task result status accordingly
                result.addMessage(ge.getMessageInstance());
                throw ge;
            }

            batchReq.setHeader(batchRequestStrArr[0]);
            batchReq.setRecordCount(batchRequestStrArr.length-1);

            // Prefetch operation type
            ArrayList<String> prefetchRecord = csvParser.parseLine(batchRequestStrArr[1]);

            // To be used in header validation
            lastOperationType = prefetchRecord.get(0);

            // Get run args from batch request object
            setRunArgs(batchReq.getRunConfig());

            // Parse out header fields
            headers = csvParser.parseLine(batchRequestStrArr[0]);

            BatchRequestValidator validator = new BatchRequestValidator(context, headers, batchReq.getRunConfig());

            // Validate the header fields
            try {
                validator.validateHeaderFields(lastOperationType);
            }
            catch (GeneralException ge) {
                Message msg = ge.getMessageInstance();

                if (msg != null) {
                    // Set message key
                    batchReq.setErrorMessage(ge.getMessageInstance());
                }

                result.addMessage(Message.error(MessageKeys.BATCH_REQUEST_INVALID_HEADER_TASK_RESULT));
                context.saveObject(batchReq);
                context.commitTransaction();
                throw ge;
            }

            batchReq.setHeader(Util.listToCsv(validator.getHeaders()));

            HashMap<String, BatchRequestItem> items = new HashMap<String, BatchRequestItem>();

            // merge map, used to merge request items
            HashMap<String, List<String>> mergeMap = new HashMap<String, List<String>>();
            boolean mergeExists = false;

            // Parse out the batch request into batch request items
            for (int i = 1; i < batchRequestStrArr.length; ++i) {

                if (_terminate) {
                    result.setTerminated(true);
                    break;
                }
                ArrayList<String> requestValues = csvParser.parseLine(batchRequestStrArr[i]);

                // We always assume the operation is the first value
                String op = requestValues.get(0);

                BatchRequestItem item = new BatchRequestItem();
                item.setRequestData(batchRequestStrArr[i]);

                try {
                    if (!validator.validateRequestValues(requestValues, lastOperationType)) {
                        log.error(validator.getErrorMsg());
                        item.setErrorMessage(validator.getErrorMsg());
                        invalidateItem(item);

                        context.saveObject(item);

                        if (errorCount++ > stopNumber && !ignoreErrors) {
                            batchReq.setStatus(BatchRequest.Status.Invalid);
                            log.error("Too many errors. Aborting.");
                            items.put(item.getId(), item);
                            break;
                        }
                    }
                    else if (BatchRequestLibrary.OP_ADD_ENTITLEMENT.equals(op)) {
                        mergeExists = mergeExists || mergeAttributes(mergeMap, requestValues, item.getId());
                    }
                }
                catch (GeneralException ge) {
                    // invalid op mix
                    item.setErrorMessage(validator.getErrorMsg());
                    invalidateItem(item);
                    batchReq.setStatus(BatchRequest.Status.Invalid);
                    items.put(item.getId(), item);
                    break;
                }
                item.setTargetIdentityId(validator.getTargetId());
                context.saveObject(item);
                items.put(item.getId(), item);
            } // end for loop

            batchReq.setInvalidCount(errorCount);

            if (errorCount == batchReq.getRecordCount()) {
                // all items errored out, invalidate parent request
                batchReq.setStatus(BatchRequest.Status.Invalid);
            }

            if (mergeExists) {
                items = processMerges(items, mergeMap);
            }

            batchReq.setBatchRequestItems(new ArrayList<BatchRequestItem>(items.values()));

            context.saveObject(batchReq);
            context.commitTransaction();

            // if there are no fatal errors
            if (batchReq.getStatus() != BatchRequest.Status.Invalid) {
                // Launch batch wrapper workflow
                runWrapperWorkflow();
            }
        } catch (Exception e) {
            // outer catch clause - need this as a literal 'catch-all' so we can
            // invalidate the request and capture the error
            //
            log.error("Error processing Batch Request: " + batchReq, e);
            // One problem is that the error may have occurred trying to save our recently modified batchReq
            // object.  So just changing that status and trying to save again will likely result in the same
            // dumb error.  Ditch the changes and just try the version we know is useful
            context.decache();
            batchReq = context.getObjectById(BatchRequest.class, batchReq.getId());
            batchReq.setStatus(BatchRequest.Status.Invalid);
            if (batchReq.getErrorMessage() == null) {
                batchReq.setErrorMessage(Message.error(e.getMessage()));
            }
            result.setCompletionStatus(TaskResult.CompletionStatus.Error);

            try {
                context.saveObject(batchReq);
                context.commitTransaction();
            } catch (GeneralException ge) {
                // Oh, we are in the thick of it here.  Add a little more complaint.
                log.error("Cannot invalidate request", ge);
                // we're going to re-throw the outer exception, so aside from logging this one
                // just swallow it.  It's likely the same as the outer exception anyways.
            }

            // we're not done with this exception.  Toss it back out so our calling framework
            // will know something tragic just happened
            throw e;
        }
    }
    
	private boolean mergeAttributes(HashMap<String, List<String>> mergeMap, ArrayList<String> requestValues, String itemId) {
		// entitlement ops may be merged
		String nativeIdentity = null;
		String appName = null;
		String entName = null;
		String value = null;
		boolean mergeExists = false;
		
		if (headers.contains(BatchRequestLibrary.NATIVE_HEADER)) {
			nativeIdentity = requestValues.get(headers.indexOf(BatchRequestLibrary.NATIVE_HEADER));
		}
		
		if (headers.contains(BatchRequestLibrary.APP_HEADER)) {
			appName = requestValues.get(headers.indexOf(BatchRequestLibrary.APP_HEADER));
		}
		
		if (headers.contains(BatchRequestLibrary.ENTNAME_HEADER)) {
			entName = requestValues.get(headers.indexOf(BatchRequestLibrary.ENTNAME_HEADER));
		}
		
		if (headers.contains(BatchRequestLibrary.ENTVALUE_HEADER)) {
			value = requestValues.get(headers.indexOf(BatchRequestLibrary.ENTVALUE_HEADER));
		}
		
		if (nativeIdentity != null && appName != null && entName != null && value != null) {
			String key = nativeIdentity + "," + appName + "," + entName;
			
			if (mergeMap.containsKey(key)) {
				mergeExists = true;
				mergeMap.get(key).add(itemId);
			}
			else {
				List<String> bidList = new ArrayList<String>();
				bidList.add(itemId);
				mergeMap.put(key, bidList);
			}
		}
		return mergeExists;
	}
    
    //////////////////////////////////////////////////////////////////////
    //
    // Worker methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Merge add entitlement batch request items into one
     * 
     * @param items
     * @param mergeMap
     * @return
     * @throws GeneralException 
     */
    private HashMap<String, BatchRequestItem> processMerges(HashMap<String, BatchRequestItem> items, HashMap<String, List<String>> mergeMap) throws GeneralException {
		
    	Iterator<String> keys = mergeMap.keySet().iterator();
    	
    	while (keys.hasNext()) {
    		List<String> ids = mergeMap.get(keys.next());
    		
    		if (ids.size() > 1) {
    			BatchRequestItem masterItem = new BatchRequestItem();
    			
    			String[] requestValues = null;;
    			String mergeVal = "";
    			boolean isFirst = true;
    			for (String id: ids) {
    				BatchRequestItem item = items.get(id);
    				String reqData = item.getRequestData();
    				requestValues = reqData.split("\\s*,\\s*");
    				String value = requestValues[headers.indexOf(BatchRequestLibrary.ENTVALUE_HEADER)].trim();
    				
    				if (isFirst) {
    					mergeVal = value;
    					isFirst = false;
    				}
    				else {
    					mergeVal += "|" + value;
    				}
    				items.remove(id);
    			}
    			requestValues[headers.indexOf(BatchRequestLibrary.ENTVALUE_HEADER)] = mergeVal;

    			StringBuffer masterReqData = new StringBuffer();
    			
    			for (int i=0;i<requestValues.length;++i) {
    				masterReqData.append(requestValues[i]);
    				if (i+1 < requestValues.length) {
    					masterReqData.append(",");
    				}
    			}
    			
    			masterItem.setRequestData(masterReqData.toString());
    			_context.saveObject(masterItem);
    			items.put(masterItem.getId(), masterItem);
    		}
    	}
    	
    	return items;
	}

	private void getBatchRequest(String requestId) throws GeneralException {
        // Check if object exists and retrieve
        try {
        	batchReq = _context.getObjectById(BatchRequest.class, requestId);
        }
        catch (GeneralException ge) {
        	_result.setCompletionStatus(TaskResult.CompletionStatus.Error);
        	_result.addMessage(Message.error(MessageKeys.BATCH_REQUEST_TASK_RESULT_OBJECT_NOT_FOUND));
        	throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_NOT_FOUND));
        }
        
        if (batchReq == null) {
        	_result.setCompletionStatus(TaskResult.CompletionStatus.Error);
            _result.addMessage(Message.error(MessageKeys.BATCH_REQUEST_TASK_RESULT_OBJECT_NOT_FOUND));
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_NOT_FOUND));
        }
    }
	
	private void invalidateItem(BatchRequestItem item) {
		item.setStatus(BatchRequestItem.Status.Invalid);
		item.setResult(BatchRequestItem.Result.Skipped);
	}

	/**
     * Get batch version of workflow
     * Duplicate code from SubmitRequestBean
     * 
     * @param ctx
     * @param flowName
     * @return
     * @throws GeneralException
     */
    public String getWorkflowName(SailPointContext ctx, String flowName) 
    throws GeneralException {

        String workflow = null;
        Configuration sysConfig = ctx.getConfiguration();
        if ( sysConfig != null ) {
            String configName = "batchRequest"+ flowName;
            String configuredWorkflow = sysConfig.getString(configName);
            if ( Util.getString(configuredWorkflow) != null ) {
                workflow = configuredWorkflow;
            } else {
                throw new GeneralException("Unable to find system config system settting for flow '"+flowName+"' using config name'"+configName+"'");
            }
        }
        return workflow;
    }
    
	/**
     * 
     * @param runConfig
     */
	private void setRunArgs(Attributes<String, Object> runConfig) {
		handleExistingCreate = (Boolean) runConfig.get("handleExistingCreate");
		ignoreErrors = (Boolean) runConfig.get("ignoreErrors");
		stopNumber = (Integer) runConfig.get("stopNumber");
	}
	
	/**
	 * Run batch wrapper workflow that does approval first
	 * and then launches the rest of the workflows.
	 * @throws GeneralException 
	 * 
	 */
	private void runWrapperWorkflow() throws GeneralException {
		
		// setup workflow launch options
        WorkflowLaunch wfl = new WorkflowLaunch();

        String owner = batchReq.getOwner().getName();
        wfl.setSessionOwner(owner);

        // this is the default workflow we'll run
        wfl.setWorkflowRef("Batch Request Wrapper");

        // generate a case name
        String caseName = "Batch request wrapper for - " + batchReq.getFileName();
        
        wfl.setCaseName(caseName);
        wfl.setLauncher(owner);
        
        Attributes<String,Object> vars = new Attributes<String,Object>();

        // Get sysconfig option for approvals
        Configuration config = _context.getConfiguration();
        boolean requireApproval = config.getBoolean(Configuration.REQUIRE_BATCH_REQUEST_APPROVAL);

        vars.put("requireBatchRequestApproval", requireApproval);
        
        if (requireApproval) {
	        ApprovalSet apprSet = new ApprovalSet();
	        ApprovalItem apprItem = new ApprovalItem();
	        apprItem.setDisplayName("Batch Request File: " + batchReq.getFileName());
	        apprItem.setDisplayValue(batchReq.getFileName());
	        Attributes<String, Object> attrs = new Attributes<String, Object>();
	        attrs.put(BATCH_REQUEST, BATCH_REQUEST);
			apprItem.setAttributes(attrs);
	        apprSet.add(apprItem);
	        vars.put("approvalSet", apprSet);
	        vars.put("batchRequestApprover", getBatchApproverName());
	        
	        batchReq.setStatus(BatchRequest.Status.Approval);
	        _context.saveObject(batchReq);
        }
        
        vars.put(IdentityLibrary.VAR_IDENTITY_NAME, owner);
        vars.put("batchRequestId", batchReq.getId());
        
        wfl.setVariables(vars);

        // launch a session
        Workflower wf = new Workflower(_context);
        WorkflowSession ses = wf.launchSession(wfl);
        
        WorkflowLaunch launch = ses.getWorkflowLaunch();
        
        // if launch failed for some reason
        // it won't set the statuses correctly so do it here
        if (launch.isFailed()) {
            List<Message> messages = launch.getMessages();
            StringBuffer msgBuf = new StringBuffer();
            if (messages != null && !messages.isEmpty()) {
                for (Message message : messages) {
                    ses.addReturnMessage(message);
                    msgBuf.append(message.getLocalizedMessage());
                }
            }
            batchReq.setStatus(BatchRequest.Status.Executed);
            batchReq.setErrorMessage(Message.error(msgBuf.toString()));
        }
        
	}
	
	/**
	 * Check system config for batch request approver.
	 * 
	 * @return
	 * @throws GeneralException 
	 */
	private String getBatchApproverName() throws GeneralException {

        Configuration systemConfig = Configuration.getSystemConfig();

        return systemConfig.getString(Configuration.BATCH_REQUEST_APPROVER);
	}
	
    //////////////////////////////////////////////////////////////////////
    //
    // Validation methods
    //
    //////////////////////////////////////////////////////////////////////
	/**
     * Do some basic pre-validation on the file contents.
     * Return string array containing lines of file.
     * 
     * @param batchFileString
     * @throws GeneralException
     */
	private String[] prevalidate(String batchFileString) throws GeneralException {
        boolean error = false;
        String errorMsgKey = "";

		if (batchFileString == null) {
            error = true;
            errorMsgKey = MessageKeys.BATCH_REQUEST_NO_FILE;
		}

		if (batchFileString.length() == 0) {
            error = true;
            errorMsgKey = MessageKeys.BATCH_REQUEST_EMPTY_FILE;
		}

		String[] batchRequests = batchFileString.split("\\r?\\n");
        
		if (batchRequests.length < 2) {
            error = true;
            errorMsgKey = MessageKeys.BATCH_REQUEST_NO_DATA_RECORD;
		}

        if (error) {
            Message errorMessage = Message.error(errorMsgKey);
            batchReq.setErrorMessage(errorMessage);
            _context.saveObject(batchReq);
            _context.commitTransaction();

            throw new GeneralException(errorMessage);
        }

		return batchRequests;
	}

    // Utility Methods
    
    /**
     *  Trim strings in list
     *  
     * @param values
     * @return
     */
	public static String[] trim(String[] values) {
		for (int i = 0, length = values.length; i < length; i++) {
			if (values[i] != null) {
				values[i] = values[i].trim();
			}
		}
		return values;
	}
	
}
