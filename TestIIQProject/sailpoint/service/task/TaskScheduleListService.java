/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.util.Log;

import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.api.TaskManager;
import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskSchedule;
import sailpoint.service.BaseListService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Created by ryan.pickens on 7/3/17.
 */
public class TaskScheduleListService extends BaseListService<TaskScheduleListServiceContext> {
    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector
     */
    public TaskScheduleListService(SailPointContext context, TaskScheduleListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
        ScopeService scopeService = new ScopeService(getContext());
        try {
            scopeService.applyScopingOptionsToContext(listServiceContext.getLoggedInUser());
        } catch (GeneralException e) {
            Log.error("The TaskScheduleListService was unable to apply scoping", e);
        }
    }


    public ListResult getTaskSchedules() throws GeneralException {
        List<TaskScheduleDTO> dtos = new ArrayList<TaskScheduleDTO>();
        //Can't call BaseListService.getResults. Iterator<Object[]> search isn't implemented in QuartsPersistenceManager
        //No sense in trying to filter down based on column config. Let the DTO dictate this
        QueryOptions ops = getQueryOptions();
        List<TaskSchedule> schedules = getContext().getObjects(TaskSchedule.class, ops);
        for (TaskSchedule sched : Util.safeIterable(schedules)) {
            ScopeService scopeService = new ScopeService(getContext());
            boolean addSchedule;
            Identity loggedInUser = listServiceContext.getLoggedInUser();
            boolean isSysAdmin = loggedInUser.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);

            if (!scopeService.isScopingEnabled() || isSysAdmin) {
                addSchedule = true;
            } else {
                // Unfortunately we have to Scope this on the fly because we are querying from Quartz
                Scope scheduleScope = sched.getAssignedScope();
                if (scheduleScope != null) {
                    addSchedule = scopeService.controlsScope(loggedInUser, scheduleScope);
                } else if (scopeService.isUnscopedGloballyAccessible()) {
                    addSchedule = true;
                } else {
                    addSchedule = false;
                }
            }

            if (addSchedule) {
                dtos.add(new TaskScheduleDTO(sched));
            }
        }

        int count = dtos.size();
        return new ListResult(dtos, count);
    }

    private static final String PROP_NAME = "name";
    private static final String PROP_HOST = "host";
    private static final String PROP_TYPE = "definition.type";
    private static final String PROP_SCHEDULED = "scheduled";
    private static final String PROP_DESCRIPTION = "description";

    private QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.createQueryOptions();

        // filter out schedules that are immediate task runners
        if (listServiceContext.excludeImmediate()) {
            ops.add(Filter.not(Filter.eq(PROP_DESCRIPTION, TaskManager.IMMEDIATE_SCHEDULE)));
        }

        if (Util.isNotNullOrEmpty(listServiceContext.getName())) {
            ops.add(Filter.ignoreCase(Filter.eq(PROP_NAME, listServiceContext.getName())));
        }

        if (Util.isNotNullOrEmpty(listServiceContext.getHost())) {
            ops.add(Filter.ignoreCase(Filter.eq(PROP_HOST, listServiceContext.getHost())));
        }

        if (Util.isNotNullOrEmpty(listServiceContext.getType())) {
            Type type = TaskItemDefinition.Type.valueOf(listServiceContext.getType());
            if (type != null) {
                ops.add(Filter.ignoreCase(Filter.eq(PROP_TYPE, type)));
            }
        }

        if (Util.isNotNullOrEmpty(listServiceContext.getQuery())) {
            ops.add(
                Filter.or(
                    Filter.ignoreCase(Filter.like(PROP_NAME, listServiceContext.getQuery(), Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like(PROP_HOST, listServiceContext.getQuery(), Filter.MatchMode.START))
                )
            );
        }

        return ops;
    }

    /**
     * Populates the number of scheduled tasks for the Task Management tabs
     * @return Map object containing the type of task and the number of them.
     * @throws GeneralException
     */
    public Map<String, Object> getTabCounts() throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();
        // This is horribly inefficient, but it's necessary because TaskSchedules
        // don't do scoping in the traditional way.  In order to avoid this, we would
        // have to add a TaskSchedule hibernate mapping that mirrors what Quartz has.
        // I doubt that will ever happen --Bernie
        int count = getTaskSchedules().getCount();
        result.put(PROP_SCHEDULED, count);
        return result;
    }


}
