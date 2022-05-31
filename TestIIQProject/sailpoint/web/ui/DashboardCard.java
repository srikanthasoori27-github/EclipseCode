package sailpoint.web.ui;

import java.util.Map;

/**
 * DTO for a single card on the responsive dashboard. 
 */
public class DashboardCard {
    
    // Card id.
    private String cardId;

    // Card attributes.
    private Map<String, Object> attributes;
    
    public DashboardCard() {}
    
    public DashboardCard(String id, Map<String, Object> attributes) {
        this.cardId = id;
        this.attributes = attributes;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
