/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.service.task.TaskScheduleDTO;
import sailpoint.service.task.TaskScheduleService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Created by ryan.pickens on 7/4/17.
 */
public class TaskScheduleResource extends BaseResource {

    private String taskSchedule;

    public TaskScheduleResource(String taskSchedule, BaseResource parent) {
        super(parent);
        this.taskSchedule = taskSchedule;
    }

    @GET
    public TaskScheduleDTO getTaskSchedule() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessTask, SPRight.FullAccessTaskManagement,
                SPRight.ViewTaskManagement));

        TaskScheduleService svc = getService();
        return svc.getTaskScheduleDTO();
    }

    @DELETE
    public Response delete() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessTask, SPRight.FullAccessTaskManagement));

        TaskScheduleService service = getService();

        service.deleteSchedule();
        return Response.ok().build();
    }

    @PATCH
    public void patchTaskSchedule(Map<String, Object> values)
            throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessTask, SPRight.FullAccessTaskManagement));


        if (Util.isEmpty(values)) {
            throw new InvalidParameterException("values");
        }

        TaskScheduleService svc = getService();
        svc.patch(values);

    }

    private TaskScheduleService getService() throws GeneralException {
        return new TaskScheduleService(taskSchedule, this);
    }
}
