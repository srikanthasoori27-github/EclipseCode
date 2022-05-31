/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.me;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.widget.WidgetDTO;
import sailpoint.service.widget.WidgetService;
import sailpoint.tools.GeneralException;

/**
 * Resource to interact with widgets.
 *
 * @author patrick.jeong
 */
public class WidgetResource extends BaseResource {

    public WidgetResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Get the list of widgets for the logged in user
     *
     * @param all If true, return all widgets available to user. Otherwise, only configured widgets.
     * @return List of widget DTOs
     * @throws GeneralException
     */
    @GET
    public List<WidgetDTO> getConfiguredWidgets(@QueryParam("all") boolean all) throws GeneralException {
        authorize(new AllowAllAuthorizer());

        WidgetService service = new WidgetService(getContext(), getLoggedInUser(), this.getLoggedInUserCapabilities());
        return (all) ? service.getAllWidgets() : service.getConfiguredWidgets();
    }
}
