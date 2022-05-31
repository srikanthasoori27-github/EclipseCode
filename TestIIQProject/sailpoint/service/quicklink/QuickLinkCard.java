package sailpoint.service.quicklink;

import java.util.Map;

import sailpoint.object.QuickLink;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Simple data transfer object to represent a card for displaying QuickLink with some extra information
 */
public class QuickLinkCard {

    /**
     * QuickLink name
     */
    private String name;
    
    /**
     * CSS class name
     */
    private String cssClass;

    /**
     * Card label (or message key)
     */
    private String label;

    /**
     * Text content for card
     */
    private String text;

    /**
     * ARIA label to describe text content for screen readers
     */
    private String ariaLabel;

    /**
     * True if allowed to target self
     */
    private boolean allowSelf;

    /**
     * True if allowed to target others
     */
    private boolean allowOthers;

    /**
     * True if bulk request allowed
     */
    private boolean allowBulk;

    public QuickLinkCard(QuickLinkWrapper quickLinkWrapper) throws GeneralException {
        QuickLink quickLink = quickLinkWrapper.getQuickLink();
        if (quickLink == null) {
            throw new GeneralException("quickLinkWrapper is invalid");
        }
        
        this.name = quickLink.getName();
        this.cssClass = quickLink.getCssClass();
        this.label = quickLinkWrapper.getLabel();
        this.allowOthers = quickLinkWrapper.isAllowOthers();
        this.allowSelf = quickLinkWrapper.isAllowSelf();
        this.allowBulk = quickLinkWrapper.isAllowBulk();
        if (!Util.isNullOrEmpty(quickLinkWrapper.getText())) {
            this.text = quickLinkWrapper.getText();
            this.ariaLabel = (String)Util.get(quickLink.getArguments(), QuickLink.ARG_TEXT_ARIA_LABEL);
        }
    }

    public QuickLinkCard(Map<String, Object> cardDataMap) throws GeneralException {
        if (cardDataMap == null) {
            throw new GeneralException("data map is null");
        }

        this.name = (String)cardDataMap.get("name");
        this.cssClass = (String)cardDataMap.get("cssClass");
        this.label = (String)cardDataMap.get("label");
        this.text = (String)cardDataMap.get("text");
        this.ariaLabel = (String)cardDataMap.get("ariaLabel");
        if (cardDataMap.containsKey("allowSelf")) {
            this.allowSelf = (Boolean)cardDataMap.get("allowSelf");
        }
        if (cardDataMap.containsKey("allowOthers")) {
            this.allowOthers = (Boolean)cardDataMap.get("allowOthers");
        }
        if (cardDataMap.containsKey("allowBulk")) {
            this.allowBulk = (Boolean)cardDataMap.get("allowBulk");
        }
    }

    
    /**
     * QuickLink name
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * CSS class name
     */
    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    /**
     * Card label (or message key)
     */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Text content for card
     */
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /**
     * ARIA label to describe text content for screen readers
     */
    public String getAriaLabel() {
        return ariaLabel;
    }

    public void setAriaLabel(String ariaLabel) {
        this.ariaLabel = ariaLabel;
    }

    /**
     * True if allowed to target self
     */
    public boolean isAllowSelf() {
        return allowSelf;
    }

    public void setAllowSelf(boolean allowSelf) {
        this.allowSelf = allowSelf;
    }

    /**
     * True if allowed to target others
     */
    public boolean isAllowOthers() {
        return allowOthers;
    }

    public void setAllowOthers(boolean allowOthers) {
        this.allowOthers = allowOthers;
    }

    public boolean isAllowBulk() { return allowBulk; }

    public void setAllowBulk(boolean allowBulk) { this.allowBulk = allowBulk; }
}