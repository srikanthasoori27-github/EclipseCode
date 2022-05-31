package sailpoint.service.homepage;

import sailpoint.service.quicklink.QuickLinkCard;
import sailpoint.service.widget.WidgetDTO;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data transfer object for home page content
 */
public class HomePageDTO {
    
    /**
     * List of configured quick link cards
     */
    private List<QuickLinkCard> quickLinkCards;

    /**
     * List of configured widgets
     */
    private List<WidgetDTO> widgets;

    /**
     * Ordering of the content
     */
    private List<String> contentOrder;

    /**
     * Constructor
     * @param quickLinkCards List of QuickLinkCards
     * @param widgets List of WidgetDTO
     * @param contentOrder List of content ordering (Widget, QuickLink)
     */
    public HomePageDTO(List<QuickLinkCard> quickLinkCards, List<WidgetDTO> widgets, List<String> contentOrder) {
        this.widgets = widgets;
        this.quickLinkCards = quickLinkCards;
        this.contentOrder = contentOrder;
    }

    /**
     * Constructor.
     * @param data Map representation of HomePageDTO
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public HomePageDTO(Map<String, Object> data) throws GeneralException {
        if (data == null) {
            throw new GeneralException("data is required");
        }
        
        if (data.containsKey("quickLinkCards")) {
            List<Map<String, Object>> cardMaps = (List<Map<String, Object>>)data.get("quickLinkCards");
            this.quickLinkCards = new ArrayList<QuickLinkCard>();
            for (Map<String, Object> cardMap : cardMaps) {
                this.quickLinkCards.add(new QuickLinkCard(cardMap));
            }
        }
        
        if (data.containsKey("widgets")) {
            List<Map<String, Object>> widgetMaps = (List<Map<String, Object>>)data.get("widgets");
            this.widgets = new ArrayList<WidgetDTO>();
            for (Map<String, Object> widgetMap : widgetMaps) {
                this.widgets.add(new WidgetDTO(widgetMap));
            }
        }
        
        if (data.containsKey("contentOrder")) {
            this.contentOrder = (List<String>)data.get("contentOrder");
        }
    }

    /**
     * @return List of configured quick link cards
     */
    public List<QuickLinkCard> getQuickLinkCards() {
        return quickLinkCards;
    }

    public void setQuickLinkCards(List<QuickLinkCard> quickLinkCards) {
        this.quickLinkCards = quickLinkCards;
    }

    /**
     * @return List of configured widgets
     */
    public List<WidgetDTO> getWidgets() {
        return widgets;
    }

    public void setWidgets(List<WidgetDTO> widgets) {
        this.widgets = widgets;
    }

    /**
     * @return Ordering of the content
     */
    public List<String> getContentOrder() {
        return contentOrder;
    }

    public void setContentOrder(List<String> contentOrder) {
        this.contentOrder = contentOrder;
    }

}