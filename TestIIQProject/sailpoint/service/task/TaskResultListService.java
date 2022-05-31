/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.util.Log;

import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.service.BaseListService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * Created by ryan.pickens on 6/16/17.
 */
public class TaskResultListService extends BaseListService<TaskResultListServiceContext> {
    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector
     */
    public TaskResultListService(SailPointContext context, TaskResultListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
        ScopeService scopeService = new ScopeService(getContext());
        try {
            scopeService.applyScopingOptionsToContext(listServiceContext.getLoggedInUser());
        } catch (GeneralException e) {
            Log.error("The TaskResultListService was unable to apply scoping", e);
        }
    }


    public ListResult getTaskResults() throws GeneralException {
        return getTaskResults(null);
    }

    /**
     * Get task Results, with a pre-defined set of Query Options. These will be anded with
     * the normal QueryOptions
     * @param ops
     * @return
     * @throws GeneralException
     */
    public ListResult getTaskResults(QueryOptions ops) throws GeneralException {
        QueryOptions qo = getQueryOptions();

        if (ops != null) {
            qo.add(ops.getFilters().toArray(new Filter[ops.getFilters().size()]));
        }


        int count = countResults(TaskResult.class, qo);

        List<Map<String, Object>> results = getResults(TaskResult.class, qo);
        List<TaskResultDTO> taskResults = convertResults(results);
        return new ListResult(taskResults, count);
    }

    /**
     * Populates the number of active and completed tasks for the Task Management tabs
     * @return Map object containing the type of task and the number of them.
     * @throws GeneralException
     */
    public Map<String, Object> getTabCounts() throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();
        QueryOptions completed = new QueryOptions();

        completed.add(Filter.not(Filter.isnull(PROP_COMPLETED)));

        int count = countResults(TaskResult.class, completed);
        result.put(PROP_COMPLETED, count);

        QueryOptions notCompleted = new QueryOptions();
        notCompleted.add(Filter.isnull(PROP_COMPLETED));

        count = countResults(TaskResult.class, notCompleted);
        result.put(PROP_ACTIVE, count);

        return result;
    }

    private static final String PROP_TYPE = "type";
    private static final String PROP_LAUNCHER = "launcher";
    private static final String PROP_LAUNCHED = "launched";
    private static final String PROP_COMPLETED_STATUS = "completionStatus";
    private static final String PROP_COMPLETED = "completed";
    private static final String PROP_HOST = "host";
    private static final String PROP_ACTIVE = "active";
    private static final String PROP_NAME = "name";
    private static final String PROP_OWNER = "owner.name";

    private static final String DATA_INDEX_CAN_RESTART = "canRestart";

    private QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.createQueryOptions();


        if (listServiceContext.getType() != null) {
            ops.addFilter(Filter.eq(PROP_TYPE, listServiceContext.getType()));
        }

        if (listServiceContext.getLauncher() != null) {
            ops.addFilter(Filter.eq(PROP_LAUNCHER, listServiceContext.getLauncher()));
        }

        if (listServiceContext.getLaunchedStartDate() != null) {
            ops.addFilter(Filter.ge(PROP_LAUNCHED, listServiceContext.getLaunchedStartDate()));
        }

        if (listServiceContext.getLaunchedEndDate() != null) {
            ops.addFilter(Filter.lt(PROP_LAUNCHED, listServiceContext.getLaunchedEndDate()));
        }

        if (listServiceContext.getStatus() != null) {
            ops.addFilter(Filter.eq(PROP_COMPLETED_STATUS, listServiceContext.getStatus()));
        }

        if (Util.isNotNullOrEmpty(listServiceContext.getHost())) {
            ops.addFilter(Filter.eq(PROP_HOST, listServiceContext.getHost()));
        }

        if (listServiceContext.getIsCompleted() != null) {
            if (listServiceContext.getIsCompleted()) {
                ops.add(Filter.not(Filter.isnull(PROP_COMPLETED)));
            } else {
                ops.add(Filter.isnull(PROP_COMPLETED));
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
        
        ScopeService scopeService = new ScopeService(getContext());
        scopeService.applyScopingOptionsToContext(listServiceContext.getLoggedInUser());

        return ops;
    }

    private List<TaskResultDTO> convertResults(List<Map<String, Object>> results) throws GeneralException {
        List<TaskResultDTO> taskResults = new ArrayList<>();
        List<ColumnConfig> cols = this.columnSelector.getColumns();
        for (Map<String,Object> row: Util.safeIterable(results)) {
            taskResults.add(new TaskResultDTO(this.getListServiceContext(), row, cols));
        }
        return taskResults;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // BaseListService Overrides
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Override the BaseListService to *not* do any conversion for date values.
     */
    @Override
    protected Object convertColumn(Map.Entry<String, Object> entry, ColumnConfig config,
                                   Map<String, Object> rawObject) throws GeneralException {

        Object value = entry.getValue();

        if (!(value instanceof Date)) {
            value = super.convertColumn(entry, config, rawObject);
        }

        return value;
    }

    /**
     * Override the BaseListService to get the value of canRestart from an instance of the TaskResult.
     */
    @Override
    protected void calculateColumn(ColumnConfig config,
                                   Map<String,Object> rawQueryResults,
                                   Map<String,Object> map)
            throws GeneralException {

        if (DATA_INDEX_CAN_RESTART.equals(config.getDataIndex())) {
            TaskResult.CompletionStatus completionStatus =
                    (TaskResult.CompletionStatus)rawQueryResults.get(PROP_COMPLETED_STATUS);

            if (TaskResult.CompletionStatus.Terminated == completionStatus ||
                    TaskResult.CompletionStatus.Error == completionStatus) {
                TaskResult taskResult = getContext().getObjectById(TaskResult.class, (String) map.get("id"));
                if (taskResult != null) {
                    map.put(DATA_INDEX_CAN_RESTART, taskResult.canRestart());
                }
            }
        }
    }

}
