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
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.module.ModuleStatusService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;


/**
 * A list resource for modules.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Path("modules")
public class ModulesResource extends BaseListResource implements BaseListServiceContext {


    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.MODULE_TABLE_COLUMNS;
    }

    @GET
    @Path("status")
    public ListResult getModulesStatus() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));
        QueryOptions ops = getQueryOptions(getColumnKey());

        if (Util.isNotNullOrEmpty(query)) {
            ops.add(Filter.or(
                    Filter.eq("id", query),
                    Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START))
            ));
        }

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        ModuleStatusService svc = new ModuleStatusService(getContext(), this, selector);
        return svc.getModuleStatuses(ops);

    }

    /**
     * Return the ModuleResource sub-resource for the module with the
     * given name or ID.
     * 
     * @param  module  The name or ID of the module.
     */
    @Path("{module}")
    public ModuleResource getModule(@PathParam("module") String module) {
        return new ModuleResource(module, this);
    }


    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws Exception {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
                .add(Application.class.getSimpleName(), true, "name");
        return new  SuggestResource(this, authorizerContext);
    }

}
