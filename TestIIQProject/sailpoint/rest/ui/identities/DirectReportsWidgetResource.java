/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identities;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.rest.BaseResource;
import sailpoint.service.identity.DirectReportService;
import sailpoint.tools.GeneralException;


/**
 * Resource that returns information about the direct reports widget.
 */
public class DirectReportsWidgetResource extends BaseResource {

    /**
     * Constructor.
     */
    public DirectReportsWidgetResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Return the direct reports information about the users that report to the logged in manager.
     *
     * @param  query  The query string used to filter the direct reports.
     * @param  start  The first user to return.
     * @param  limit  The max number of users to return - defaults to no limit if not specified.
     *
     * @return A ListResult with the direct reports information.
     */
    @GET
    public ListResult getDirectReports(@QueryParam("query") String query,
                                       @QueryParam("start") @DefaultValue("0") int start,
                                       @QueryParam("limit") Integer limit)
        throws GeneralException {

        // Let everyone access this - if they're not a manager they won't get anything anyway.
        super.authorize(new AllowAllAuthorizer());

        // Default to no limit.
        limit = (null != limit) ? limit : Integer.MAX_VALUE;

        DirectReportService svc = new DirectReportService(getContext());
        return svc.getDirectReports(query, getLoggedInUser(), getLoggedInUserDynamicScopeNames(), start, limit);
    }
}
