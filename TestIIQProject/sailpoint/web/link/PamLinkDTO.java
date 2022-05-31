package sailpoint.web.link;

import sailpoint.object.Link;

/**
 * Provides logic for representing a link on a PAM application.  Links on PAM applications can sometimes have external links
 * This allows us to capture that external link information in the Link model
 */
public class PamLinkDTO extends LinkDTO {

    private String externalAccountId;
    private String externalApplicationName;

    public PamLinkDTO(Link link) {
        super(link);
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public void setExternalAccountId(String externalAccountId) {
        this.externalAccountId = externalAccountId;
    }

    public String getExternalApplicationName() {
        return externalApplicationName;
    }

    public void setExternalApplicationName(String externalApplicationName) {
        this.externalApplicationName = externalApplicationName;
    }
}
