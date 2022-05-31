/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import java.util.Map;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.SPRight;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.task.TaskScheduleListService;
import sailpoint.service.task.TaskScheduleListServiceContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * REST resource for TaskSchedules
 *
 * Created by ryan.pickens on 7/3/17.
 */
@Path("taskSchedules")
public class TaskScheduleListResource extends BaseListResource implements TaskScheduleListServiceContext {

    @QueryParam(PARAM_HOST) private String hostParam;
    @QueryParam(PARAM_NAME) private String nameParam;
    @QueryParam(PARAM_EXCLUDE_IMMEDIATE) private boolean excludeImmediateParam;
    @QueryParam(PARAM_TYPE) private String typeParam;
    
    public static final String PARAM_HOST = "host";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_EXCLUDE_IMMEDIATE = "excludeImmediate";
    public static final String PARAM_TYPE = "type";

    private static final String DEFAULT_COL_CONFIG_KEY = "taskManagementScheduleTableColumns";

    @GET
    public ListResult list() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessTask, SPRight.FullAccessTaskManagement,
                SPRight.ViewTaskManagement));

        return getListService().getTaskSchedules();
    }

    @Path("{taskSchedule}")
    public TaskScheduleResource getTaskScheudle(@PathParam("taskSchedule") String taskScheduleName) {
        return new TaskScheduleResource(taskScheduleName, this);
    }

    /**
     * REST endpoint to return the number of tab counts for pending and completed tasks
     * @return Map object containing the type of task and the number of them.
     * @throws GeneralException
     */
    @GET
    @Path("tabCounts")
    public Map<String, Object> getTabCounts() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ReadTaskResults, SPRight.FullAccessTask,
                SPRight.FullAccessTaskManagement, SPRight.ViewTaskManagement));
        return getListService().getTabCounts();
    }

    private TaskScheduleListService getListService() {
        return new TaskScheduleListService(getContext(), this, getColumnSelector());
    }

    public ListServiceColumnSelector getColumnSelector() {
        return new BaseListResourceColumnSelector(getColumnKey());
    }

    @Override
    public String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : DEFAULT_COL_CONFIG_KEY;
    }

    @Override
    public String getHost() {
        return hostParam;
    }

    @Override
    public String getName() {
        return nameParam;
    }

    @Override
    public boolean excludeImmediate() {
        return excludeImmediateParam;
    }
    public String getType() {
        return typeParam;
    }
}
