package sailpoint.rest.ui.me;

import sailpoint.rest.BaseResource;

import javax.ws.rs.Path;

/**
 * Resource to interact with user preferences and other things specific to logged in user
 */
@Path("me")
public class MeResource extends BaseResource {
    
    @Path("quickLinkCards")
    public QuickLinkCardsResource getQuickLinkCardsResource() {
        return new QuickLinkCardsResource(this);
    }

    @Path("mobileQuickLinks")
    public MobileQuickLinksResource getMobileQuickLinksResource() {
        return new MobileQuickLinksResource(this);
    }

    @Path("widgets")
    public WidgetResource getWidgetResource() {
        return new WidgetResource(this);
    }
    
    @Path("home")
    public HomePageResource getHomePageResource() {
        return new HomePageResource(this);
    }

    @Path("tableColumnPreferences")
    public TablePreferencesResource getTableColumnPreferencesResource() {
        return new TablePreferencesResource(this);
    }

    @Path("uiPreferences")
    public UiPreferencesResource getUiPreferencesResource() {
        return new UiPreferencesResource(this);
    }
}