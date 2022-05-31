package sailpoint.rest.ui.me;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.quicklink.QuickLinkCard;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Resource to interact with user's quick link cards
 */
public class QuickLinkCardsResource extends BaseResource {

    public QuickLinkCardsResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Get the QuickLinkCards for the logged in user
     * @return List of QuickLinkCards
     * @throws GeneralException
     */
    @GET
    public List<QuickLinkCard> getQuickLinkCards(@QueryParam("all") boolean all) throws GeneralException {
        authorize(new AllowAllAuthorizer());

        QuickLinksService quickLinksService = new QuickLinksService(getContext(), getLoggedInUser(),
                getLoggedInUserDynamicScopeNames());

        if (all) {
            return quickLinksService.getQuickLinkCards();
        }

        return quickLinksService.getConfiguredQuickLinkCards();
    }

    /**
     * Set the user preferred quick link cards
     *
     * @param quickLinkCards
     */
    @PUT
    public void setQuickLinkCards(List<Map<String, Object>> quickLinkCards) throws GeneralException {
        authorize(new AllowAllAuthorizer());

        QuickLinksService quickLinksService = new QuickLinksService(getContext(), getLoggedInUser(),
                getLoggedInUserDynamicScopeNames());

        List<QuickLinkCard> cards = new ArrayList<QuickLinkCard>();

        for (Map<String, Object> cardMap : Util.safeIterable(quickLinkCards)) {
            cards.add(new QuickLinkCard(cardMap));
        }

        quickLinksService.setConfiguredQuickLinkCards(cards);
    }
}