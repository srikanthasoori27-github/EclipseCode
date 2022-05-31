/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.service.BaseDTO;

import java.util.List;

/**
 * @author: cindy.he
 * A DTO to represent the LinkDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the LinkDTO.  
 */

public class QuickLinkDTO extends BaseDTO {

    /**
     * @property {String} name of quicklink
     */
    private String name;
    /**
     * @property {String} action of quicklink
     */
    private String action;
    /**
     * @property {boolean} true if quicklink allows self service
     */
    private boolean selfService;
    /**
     * @property {boolean} true if quicklink allows action on other identities
     */
    private boolean forOthers;

    /**
     * @property {String} messageKey of the quicklink
     */
    private String messageKey;

    /**
     * Constructor, call parent's constructor, initialize properties
     * @param {QuickLink} quicklink a quicklink object
     */
    public QuickLinkDTO(QuickLink quickLink, List<String> dynamicScopes) {
        super(quickLink.getId());
        this.name = quickLink.getName();
        this.action = quickLink.getAction();
        this.selfService = isAllowSelfService(quickLink, dynamicScopes);
        this.forOthers = isAllowOthers(quickLink, dynamicScopes);
        this.messageKey = quickLink.getMessageKey();
    }

    private boolean isAllowOthers(QuickLink quickLink, List<String> dynamicScopes) {
        if(quickLink.isForceAllowOthers()) {
            return true;
        } else {
            for (QuickLinkOptions quickLinkOptions : quickLink.getQuickLinkOptions()) {
                if (dynamicScopes.contains(quickLinkOptions.getDynamicScope().getName())) {
                    if (quickLinkOptions.isAllowOther()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAllowSelfService(QuickLink quickLink, List<String> dynamicScopes) {
        if(quickLink.isForceAllowSelf()) {
            return true;
        } else {
            for (QuickLinkOptions quickLinkOptions : quickLink.getQuickLinkOptions()) {
                if (dynamicScopes.contains(quickLinkOptions.getDynamicScope().getName())) {
                    if (quickLinkOptions.isAllowSelf()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return {String} name of the quicklink
     */
    public String getName() {
        return name;
    }
    
    /**
     * @param {String} name of the quicklink
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * @return {String} action of the quicklink
     */
    public String getAction() {
        return action;
    }

    /**
     * @return {String} messageKey of the quicklink
     */
    public String getMessageKey() {
        return messageKey;
    }
    
    /**
     * @param {String} action of the quicklink
     */
    public void setAction(String action) {
        this.action = action;
    }
    
    /**
     * This can be used for non-self-service quicklink that require self-requests.
     * @return {boolean} true if this quicklink should allow self requests. 
     */
    public boolean isSelfService() {
        return selfService;
    }
    
    /**
     * This can be used for non-self-service quicklink that require self-requests.
     * @param {boolean} selfService set whether this quicklink should allow self requests.
     */
    public void setSelfService(boolean selfService) {
        this.selfService = selfService;
    }
    
    /**
     * This can be used for self-service quicklink that require requesting for others.
     * @return {boolean} true if this quicklink should allow requests for others.
     */
    public boolean isForOthers() {
        return forOthers;
    }
    
    /**
     * This can be used for self-service quicklink that require requesting for others.
     * @param {boolean} forOthers set whether this quicklink should allow requests for others.
     */
    public void setForOthers(boolean forOthers) {
        this.forOthers = forOthers;
    }
}
