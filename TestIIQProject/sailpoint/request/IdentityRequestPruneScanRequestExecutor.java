/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityRequestProvisioningScanner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.IdentityRequest;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.task.IdentityRequestMaintenance;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class IdentityRequestPruneScanRequestExecutor extends AbstractRequestExecutor {
    
    private static final Log log = LogFactory.getLog(IdentityRequestPruneScanRequestExecutor.class);

    /**
     * The argument key that holds the action.
     */
    public static final String ARG_IDENTITY_REQUEST_ACTION = "action";
    public static final String ARG_IDENTITY_REQUEST_ACTION_PRUNE = "prune";
    public static final String ARG_IDENTITY_REQUEST_ACTION_SCAN = "scan";

    /**
     * The argument key that holds the list of identity request ids.
     */
    public static final String ARG_IDENTITY_REQUEST_ID_LIST = "idList";
    
    private TaskResult result;
    private SailPointContext context;

    private List<Message> errors = new ArrayList<>();
    
    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args)
            throws RequestPermanentException, RequestTemporaryException {

        this.context = context;
        TaskMonitor mon = new TaskMonitor(context, request);
        int requestsPruned = 0;
        int requestsScanned = 0;
        String action = request.getString(ARG_IDENTITY_REQUEST_ACTION);
        String idStringCompressed = (String)request.getAttribute(ARG_IDENTITY_REQUEST_ID_LIST);
        String idListString = null;
        try {
            idListString = Compressor.decompress(idStringCompressed);
        } catch (GeneralException e) {
            errors.add(Message.error("Error retrieving Identity Request id list."));
            log.error("Error retrieving Identity Request id list.", e);
        }
        List<String> idList = Util.csvToList(idListString);

        if (ARG_IDENTITY_REQUEST_ACTION_PRUNE.equals(action)) {
            requestsPruned = prune(idList);
        } else if (ARG_IDENTITY_REQUEST_ACTION_SCAN.equals(action)) {
            requestsScanned = scan(idList, args);
        } else {
            log.error("Unknown action: " + action);
        }
            
        try {
            result = mon.lockPartitionResult();
            result.setAttribute(IdentityRequestMaintenance.RET_REQUESTS_PRUNED, requestsPruned);
            result.setAttribute(IdentityRequestMaintenance.RET_REQUESTS_SCANNED, requestsScanned);

            if (!Util.isEmpty(errors)) {
                result.addMessages(errors);
            }
            
        } catch (GeneralException ge) {
            // capture the error in the taskresult
            if (result != null) {
                result.addMessage(new Message(Message.Type.Error, ge));
                log.error(ge.getMessage(), ge);
            }
            // and in log4j
            log.error(ge.getMessage(), ge);
        } finally {
            try {
                mon.commitPartitionResult();
            } catch (GeneralException ge) {
                log.error(ge.getMessage(), ge);
            }
        }
    }
    
    //perform scan action
    private int scan(List<String> idList, Attributes<String,Object> args) {
        int count = 0;
        IdentityRequestProvisioningScanner scanner = new IdentityRequestProvisioningScanner(context, args);            
        for (String id : Util.safeIterable(idList)) {
            try {
                IdentityRequest request = context.getObjectById(IdentityRequest.class, id);
                if (request != null) {
                    // Scan the request but continue scanning if something goes wrong  with
                    // any single request
                    scanner.scan(request);
                    context.decache();
                    count++;
                }
            } catch(Throwable t) {
                errors.add(Message.error(MessageKeys.ERR_LCM_SCAN_FAILED_ON_REQUEST, id));
                log.error("Error scanning Identity Request.", t);
            }
        }
        return count;
    }

    //perform prune action
    private int prune(List<String> idList) {
        int count = 0;
        Terminator terminator = new Terminator(context);
        for (String id : Util.safeIterable(idList)) {
            try {
                IdentityRequest obj = context.getObjectById(IdentityRequest.class, id);
                if (obj != null) {
                    terminator.deleteObject(obj);
                    context.commitTransaction();
                    context.decache(obj);
                    count++;
                }
            } catch (Throwable t) {
                errors.add(Message.error("Error prune Identity Request : " + id));
                log.error("Error pruning Identity Requests.", t);
            }
        }
        return count;
    }

}
