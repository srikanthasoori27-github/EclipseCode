package sailpoint.service.certification;

import sailpoint.api.Iconifier;
import sailpoint.object.Link;

import java.util.Date;
import java.util.List;

/**
 * Class that encapsulates the link attributes seen in the entitlement owner cert item detail dialog
 */
public class LinkAttributesDTO {
    private final String appName;
    private final String instance;
    private final List<Iconifier.Icon> icons;
    private final Date lastRefresh;
    private final String accountName;

    public LinkAttributesDTO(Link link, List<Iconifier.Icon> icons) {
        this.appName = link.getApplicationName();
        this.instance = link.getInstance();
        this.icons = icons;
        this.lastRefresh = link.getLastRefresh();
        this.accountName = link.getDisplayableName();
    }

    public String getAppName() {
        return appName;
    }

    public String getInstance() {
        return instance;
    }

    public List<Iconifier.Icon> getIcons() {
        return icons;
    }

    public Date getLastRefresh() {
        return lastRefresh;
    }

    public String getAccountName() {
        return accountName;
    }
}
