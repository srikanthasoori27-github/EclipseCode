/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.batchrequest;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BatchRequest;
import sailpoint.object.BatchRequestItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.workflow.BatchRequestLibrary;

/**
 * This class centralizes logic from the web and service layers converting BatchRequestItem
 * columns into localize-able strings and masks password values 
 */
public class BatchRequestItemsListConvertor {
    private static final Log log = LogFactory.getLog(BatchRequestItemsListConvertor.class);
    
    private static final String GRID_COLUMN_STATUS = "status";
    private static final String GRID_COLUMN_RESULT = "result";
    
    static final String MASK = "*****";
    
    private UserContext userContext;
    private List<String> headerColumns = null;
    private int passwordColumn = -1;
    
    public BatchRequestItemsListConvertor(UserContext userContext) {
        this.userContext = userContext;
    }
    
    public Object convertColumn(String batchRequestId, String name, Object value) {
        Object newValue = value;
        
        if (GRID_COLUMN_STATUS.equals(name) && value != null) {
            newValue = getMessage(((BatchRequestItem.Status)value).getMessageKey());
        } else if (GRID_COLUMN_RESULT.equals(name) && value != null) {
            newValue = getMessage(((BatchRequestItem.Result)value).getMessageKey());
        }
        else {
            // Bug 25055 - For batch requests with a password column we should not
            // display the actual password in the Batch Request Details panel. The
            // "requestData" column will contain a csv string that describes the 
            // batch operation and it's arguments including the password, if any.
            // Using the headers in the batch request, we need to locate the password
            // in the csv string and replace it with "*****" to display in the UI. 
            int pwCol = initPasswordIndex(batchRequestId);

            if (pwCol != -1) {
                if (name.equalsIgnoreCase("requestdata")) {
                    List<String> batchRequestParts = Util.csvToList((String) value);
                    batchRequestParts.set(pwCol, MASK);
                    newValue = Util.listToCsv(batchRequestParts);
                }
            }
        }
        
        return newValue;
    }
    
    /**
     * Gets the index of the password header column, if any, for the batch request.
     * 
     * @return The index of the password column or -1 if it doesn't exist.
     */
    int initPasswordIndex(String batchRequestId) {
        if (headerColumns == null) {
            try {
                BatchRequest request = userContext.getContext().getObjectById(BatchRequest.class, batchRequestId);
                if (request != null) {
                    headerColumns = Util.csvToList(request.getHeader());
                    passwordColumn = headerColumns.indexOf(BatchRequestLibrary.PASSWORD_HEADER);
                }
                else {
                    log.warn("Unable to find BatchRequest for batchRequestId - " + batchRequestId);
                }
            } catch (GeneralException e) {
                log.error("Exception while finding BatchRequest for batchRequestId - " + batchRequestId + "\n" + e.getMessage(), e);
            }
        }
        return passwordColumn;
    }
    
    List<String> getHeaderColumns() {
        return this.headerColumns;
    }
    
    int getPasswordColumn() {
        return this.passwordColumn;
    }
    
    private String getMessage(String key, Object... args) {
        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
    }
}
