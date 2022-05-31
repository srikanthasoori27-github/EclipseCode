package sailpoint.service.homepage;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.UIPreferences;
import sailpoint.service.quicklink.QuickLinkCard;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.service.widget.WidgetDTO;
import sailpoint.service.widget.WidgetService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;

/**
 * Service to get and save home page content 
 */
public class HomePageService {

    /**
     * Default content order for home page
     */
    public static String DEFAULT_CONTENT_ORDER  = "QuickLink, Widget";
    
    private SailPointContext context;
    private Identity user;
    private List<String> dynamicScopeNames;
    private List<Capability> capabilities;

    /**
     * Constructor
     * @param context SailPointContext
     * @param user Logged in user
     * @param dynamicScopeNames List of dynamic scopes for logged in user
     * @param capabilities list of session stored capabilities for logged in user
     */
    public HomePageService(SailPointContext context, Identity user, List<String> dynamicScopeNames,
                           List<Capability> capabilities) {
        this.context = context;
        this.user = user;
        this.dynamicScopeNames = dynamicScopeNames;
        this.capabilities = capabilities;
    }

    /**
     * Gets the configured (or default) HomePageDTO for the current logged in user
     * @return HomePageDTO
     * @throws GeneralException
     */
    public HomePageDTO getConfiguredContent() throws GeneralException {
        List<QuickLinkCard> quickLinkCards = getQuickLinksService().getConfiguredQuickLinkCards();
        List<WidgetDTO> widgets = getWidgetService().getConfiguredWidgets();
        String contentOrder = (String)this.user.getUIPreference(UIPreferences.PRF_HOME_CONTENT_ORDER);
        if (Util.isNullOrEmpty(contentOrder)) {
            contentOrder = DEFAULT_CONTENT_ORDER;
        }
        
        return new HomePageDTO(quickLinkCards, widgets, Util.csvToList(contentOrder));
    }

    /**
     * Saves the configured HomePageDTO for the logged in user
     * @param homePageDTO HomePageDTO
     * @throws GeneralException
     */
    public void setConfiguredContent(HomePageDTO homePageDTO) throws GeneralException {
        getQuickLinksService().setConfiguredQuickLinkCards(homePageDTO.getQuickLinkCards());
        getWidgetService().setConfiguredWidgets(homePageDTO.getWidgets());
        
        // Save the content order
        this.user.setUIPreference(UIPreferences.PRF_HOME_CONTENT_ORDER, Util.listToCsv(homePageDTO.getContentOrder()));
        this.context.saveObject(this.user);
        this.context.commitTransaction();        
    }
    
    private QuickLinksService getQuickLinksService() {
        return new QuickLinksService(this.context, this.user, this.dynamicScopeNames);
    }
    
    private WidgetService getWidgetService() {
        return new WidgetService(this.context, this.user, this.capabilities);
    }
}