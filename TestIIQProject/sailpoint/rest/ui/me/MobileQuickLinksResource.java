package sailpoint.rest.ui.me;

import java.util.List;

import javax.ws.rs.GET;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.quicklink.QuickLinkCard;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;

/**
 * Resource to interact with user's mobile quick links.
 */
public class MobileQuickLinksResource extends BaseResource {
    
    public MobileQuickLinksResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Get the mobile QuickLinks for the logged in user.
     * @return List of QuickLinkCards
     * @throws GeneralException
     */
    @GET
    public List<QuickLinkCard> getQuickLinkCards() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        
        return new QuickLinksService(getContext(), getLoggedInUser(), getLoggedInUserDynamicScopeNames()).getMobileQuickLinks();
    }
}
