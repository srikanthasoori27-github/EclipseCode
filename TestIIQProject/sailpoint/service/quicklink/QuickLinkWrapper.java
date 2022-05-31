/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.quicklink;

import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;

/** A simple class that wraps a quick link options object and indicates the target population for the quicklink
 * with some simple boolean attributes. This used to live in sailpoint.object.QuickLink as an inner class, but 
 * was moved because it is only referenced by the web and api.dashboard tiers. This also used to wrap a quick link
 * object but is now associated to QuickLinkOptions.
 * @author peter.holcomb
 *
 */
public class QuickLinkWrapper {
    private QuickLink quickLink;
    private boolean allowOthers;
    private boolean allowSelf;
    private boolean allowBulk;
    private boolean allowSubMenu;
    private String text;
    private String label;

    public QuickLinkWrapper() {
    }

    /**
     * Instead call the constructor with the parameter QuickLinkOptions.
     * @param quicklink no longer used
     */
    @Deprecated
    public QuickLinkWrapper(QuickLink quicklink) {
        this();
    }
    
    public QuickLinkWrapper(QuickLinkOptions quickLinkOptions) {
        this.quickLink = quickLinkOptions.getQuickLink();
        this.label = this.quickLink.getMessageKey();
        // either you can request for one other or multiple/others
        setAllowOthers(quickLinkOptions.isAllowBulk() || quickLinkOptions.isAllowOther());
        setAllowBulk(quickLinkOptions.isAllowBulk());
        setAllowSelf(quickLinkOptions.isAllowSelf());
        //sub-menu to be displayed for all quicklinks by default 
        setAllowSubMenu(true);
    }
    
    @Override
    public String toString() {
        StringBuffer stringBuff = new StringBuffer();
        if (this.quickLink != null) {
            QuickLink link = this.quickLink;
            stringBuff.append("[quickLinkName=");
            stringBuff.append(link.getName());
            stringBuff.append(", category=");
            stringBuff.append(link.getCategory());
            stringBuff.append("]");
        }
        return stringBuff.toString();
    }
    
    public QuickLink getQuickLink() {
        return this.quickLink;
    }

    public boolean isAllowOthers() {
        return allowOthers;
    }
    public void setAllowOthers(boolean allowOthers) {
        this.allowOthers = allowOthers;
    }
    public boolean isAllowBulk() {
        return allowBulk;
    }
    public void setAllowBulk(boolean allowBulk) {
        this.allowBulk = allowBulk;
    }
    public boolean isAllowSelf() {
        return allowSelf;
    }
    public void setAllowSelf(boolean allowSelf) {
        this.allowSelf = allowSelf;
    }
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Perform a load on the underlying persistent objects to avoid lazy load exceptions.
     */
    public void load() {
        if (null != this.quickLink) {
            this.quickLink.load();
        }
    }
    
    public boolean isAllowSubMenu() {
        return allowSubMenu;
    }
    
    public void setAllowSubMenu(boolean allowSubMenu) {
        this.allowSubMenu = allowSubMenu;
    }
}