/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import sailpoint.object.Identity;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;

import java.util.Date;

/**
 * Created by ryan.pickens on 6/16/17.
 */
public interface TaskResultListServiceContext extends BaseListServiceContext {

    TaskItemDefinition.Type getType();
    Date getLaunchedStartDate();
    Date getLaunchedEndDate();
    TaskResult.CompletionStatus getStatus();
    String getLauncher();
    Identity getRequestee() throws GeneralException;
    String getHost();
    String getName();
    Boolean getIsCompleted();

}
