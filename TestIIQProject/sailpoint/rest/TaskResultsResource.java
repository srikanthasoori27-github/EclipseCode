/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import java.util.Date;
import java.util.Map;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.TaskResult;
import sailpoint.object.Identity;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.integration.ListResult;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.task.TaskResultListService;
import sailpoint.service.task.TaskResultListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Rest resource for TaskResult lists
 *
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("taskResults")
public class TaskResultsResource extends BaseListResource implements TaskResultListServiceContext {

    private static final Log log = LogFactory.getLog(TaskResultsResource.class);

    //QueryParams
    public static final String PARAM_STATUS = "status";
    public static final String PARAM_START_DATE = "launchedStartDate";
    public static final String PARAM_LAUNCHER = "launcher";
    public static final String PARAM_IDENTITY = "identity";
    public static final String PARAM_END_DATE = "launchedEndDate";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_HOST = "host";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_COMPLETED = "isCompleted";

    @QueryParam(PARAM_START_DATE) private long launchedStartDate;
    @QueryParam(PARAM_END_DATE) private long launchedEndDate;
    @QueryParam(PARAM_STATUS) private String status;
    @QueryParam(PARAM_LAUNCHER) private String launcher;
    @QueryParam(PARAM_TYPE) private String resultType;
    @QueryParam(PARAM_IDENTITY) private String requestee;
    @QueryParam(PARAM_HOST) private String host;
    @QueryParam(PARAM_NAME) private String name;
    @QueryParam(PARAM_COMPLETED) private String isCompleted;


    private static final String DEFAULT_COL_CONFIG_KEY = "taskResultsTableColumns";


    /**
     * Returns a TaskResultResource for the given task result ID or Name.
     * @param taskResult task result ID or Name
     * @return TaskResultResource instance
     */
    @Path("{taskResult}")
    public TaskResultResource getApplication(@PathParam("taskResult") String taskResult) {
        return new TaskResultResource(taskResult, this);
    }

    /**
     * Returns list of task results matching the given query parameters.
     * @return List of TaskResults
     */
    @GET
    public ListResult list() throws GeneralException {

        //Only allow Task rights when hitting the resource directly. If needing to list these
        // in a limited context, pre-authorize via parent resource
        authorize(new RightAuthorizer(SPRight.ReadTaskResults, SPRight.FullAccessTask,
                SPRight.FullAccessTaskManagement, SPRight.ViewTaskManagement));

        return getListService().getTaskResults();
    }

    /**
     * Returns the number of active and completed tasks to populate the counts
     * on the Task Management page tabs
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

    /**
     * Get the SuggestResource for the filters on the task results
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ReadTaskResults, SPRight.FullAccessTask,
                SPRight.FullAccessTaskManagement, SPRight.ViewTaskManagement));

        // TODO: Build filters on the server
        BaseSuggestAuthorizerContext authorizerContext =
                new BaseSuggestAuthorizerContext().add(TaskResult.class.getSimpleName(), false, "type", "host");
        return new SuggestResource(this, authorizerContext);
    }

    public TaskResultListService getListService() {
        return new TaskResultListService(getContext(), this, getColumnSelector());
    }

    public ListServiceColumnSelector getColumnSelector() {
        return new BaseListResourceColumnSelector(getColumnKey());
    }

    @Override
    public TaskItemDefinition.Type getType() {
        if (Util.isNotNullOrEmpty(resultType)) {
            return TaskItemDefinition.Type.valueOf(resultType);
        } else {
            return null;
        }
    }

    @Override
    public Date getLaunchedStartDate() {
        if (launchedStartDate > 0) {
            return new Date(launchedStartDate);
        } else {
            return null;
        }
    }

    @Override
    public Date getLaunchedEndDate() {
        if (launchedEndDate > 0) {
            return new Date(launchedEndDate);
        } else {
            return null;
        }
    }

    @Override
    public CompletionStatus getStatus() {
        if (Util.isNotNullOrEmpty(status)) {
            return CompletionStatus.valueOf(status);
        } else {
            return null;
        }
    }

    @Override
    public String getLauncher() {
        return launcher;
    }

    @Override
    public Identity getRequestee() throws GeneralException {
        if (Util.isNotNullOrEmpty(requestee)) {
            return getContext().getObjectById(Identity.class, requestee);
        } else {
            return null;
        }
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Boolean getIsCompleted() {
        if (Util.isNotNullOrEmpty(isCompleted)) {
            return Boolean.valueOf(isCompleted);
        } else {
            return null;
        }
    }

    @Override
    public String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : DEFAULT_COL_CONFIG_KEY;
    }
}
