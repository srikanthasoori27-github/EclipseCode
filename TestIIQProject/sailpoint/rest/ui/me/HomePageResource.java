package sailpoint.rest.ui.me;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.homepage.HomePageDTO;
import sailpoint.service.homepage.HomePageService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import java.util.Map;

/**
 * Resource to get and set home page content in classic UI
 */
public class HomePageResource extends BaseResource {
    
    public HomePageResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Get the home page content for the logged in user
     * @return HomePageDTO 
     * @throws GeneralException
     */
    @GET
    public HomePageDTO getHomePageContent() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        
        return getHomePageService().getConfiguredContent();
    }

    /**
     * Save configured home page content for the logged in user
     * @param data Map representation of HomePageDTO object
     * @throws GeneralException
     */
    @PUT
    public void saveHomePageContent(Map<String, Object> data) throws GeneralException {
        authorize(new AllowAllAuthorizer());

        getHomePageService().setConfiguredContent(new HomePageDTO(data));
    }
    
    private HomePageService getHomePageService() throws GeneralException {
        return new HomePageService(getContext(), getLoggedInUser(), getLoggedInUserDynamicScopeNames(),
                getLoggedInUserCapabilities());
    }
}