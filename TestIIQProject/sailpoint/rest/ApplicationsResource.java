/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.application.ApplicationStatusService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;


/**
 * A list resource for applications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path("applications")
public class ApplicationsResource extends BaseListResource implements BaseListServiceContext {


    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.APPLICATION_TABLE_COLUMNS;
    }

    @GET
    @Path("status")
    public ListResult getApplicationsStatus() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));
        QueryOptions ops = getQueryOptions(getColumnKey());

        if (Util.isNotNullOrEmpty(query)) {
            ops.add(Filter.or(
                    Filter.eq("id", query),
                    Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START))
            ));
        }

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        ApplicationStatusService svc = new ApplicationStatusService(getContext(), this, selector);
        return svc.getApplicationStatuses(ops);

    }

    /**
     * Return the ApplicationResource sub-resource for the application with the
     * given name or ID.
     * 
     * @param  app  The name or ID of the application.
     */
    @Path("{application}")
    public ApplicationResource getApplication(@PathParam("application") String app) {
        return new ApplicationResource(app, this);
    }


    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
                .add(Application.class.getSimpleName(), true, "name");
        return new  SuggestResource(this, authorizerContext);
    }

}
