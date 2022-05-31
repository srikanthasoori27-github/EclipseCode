/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.UserContext;
import sailpoint.web.workitem.WorkItemDTO;

/**
 * Simple WorkItemDTO used to render approvals that contained errors while rendering the original approval.
 */
public class WorkItemErrorDTO extends WorkItemDTO {
    
    public WorkItemErrorDTO(UserContext cxt, Map<String,Object> workItem, List<ColumnConfig> cols) throws ObjectNotFoundException, GeneralException {
        super(cxt, workItem, cols);
    }
}
