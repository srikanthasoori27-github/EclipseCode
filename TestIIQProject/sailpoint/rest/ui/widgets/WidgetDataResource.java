/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.widgets;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseListResource;
import sailpoint.service.widget.WidgetDataService;
import sailpoint.tools.GeneralException;

/**
 * A resource for fetching data to be consumed by a
 * widget on the home page.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class WidgetDataResource extends BaseListResource {

    /**
     * Default constructor.
     */
    public WidgetDataResource() { }

    /**
     * Constructor for use as a sub-resource.
     *
     * @param parent The parent resource.
     */
    public WidgetDataResource(BaseListResource parent) {
        super(parent);
    }

    /**
     * Retrieves data for the My Access Reviews widget.
     *
     * @return The list result.
     * @throws GeneralException
     */
    public ListResult getMyAccessReviews() throws GeneralException {
        authorize(new AllowAllAuthorizer());

        return getWidgetDataService().getMyAccessReviews(start, limit);
    }

    /**
     * Retrieves data for the Certification Campaigns widget.
     *
     * @return The list result.
     * @throws GeneralException
     */
    public ListResult getCertificationCampaigns() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.CertificationCampaignsWidget));

        return getWidgetDataService().getCertificationCampaigns(start, limit);
    }

    /**
     * Creates the widget data service.
     *
     * @return The service.
     * @throws GeneralException
     */
    private WidgetDataService getWidgetDataService() throws GeneralException {
        return new WidgetDataService(this);
    }

}