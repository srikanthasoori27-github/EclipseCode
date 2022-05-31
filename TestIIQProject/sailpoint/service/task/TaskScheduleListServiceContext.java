/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import sailpoint.service.BaseListServiceContext;

/**
 * Created by ryan.pickens on 7/3/17.
 */
public interface TaskScheduleListServiceContext extends BaseListServiceContext {
    String getHost();
    String getName();
    boolean excludeImmediate();
    String getType();
}
