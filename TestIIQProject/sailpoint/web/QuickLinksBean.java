/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.QuickLinkLaunchAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.*;
import sailpoint.service.LCMConfigService;
import sailpoint.service.quicklink.QuickLinkWrapper;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.URLUtil;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.identity.IdentityListBean;
import sailpoint.web.lcm.BaseRequestBean;


/**
 * A JSF bean that helps in displaying quick links and quick link categories, and allows launching
 * quick links.
 */
public class QuickLinksBean extends BaseBean {

    private static final Log LOG = LogFactory.getLog(QuickLinksBean.class);

    private static final String WORK_ITEM_TYPE = "workItemType";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private QuickLinksService quickLinksService;

    /**
     * The name of the quick link that is being launched.
     */
    private String quickLinkName;

    /**
     * The ID of the identity for which the quick link is being launched.  This is non-null for
     * self-service requests and null otherwise.
     */
    private String identityId;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public QuickLinksBean() throws GeneralException {
        super();
        this.quickLinksService = new QuickLinksService(getContext(), getLoggedInUser(), getSessionScope());
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CALCULATED PROPERTIES
    //
    // This bean is used on every page.  To prevent having to calculate these for every page we
    // cache the values on the session.  These values will change extremely rarely, so it is alright
    // to require a logout to reset them.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public List<QuickLinkCategory> getQuickLinkCategories() throws GeneralException {
        // Try to get it from the session.
        final String ATT_CATEGORIES = "quickLinksBeanQuickLinkCategories";
        List<QuickLinkCategory> categories = (List<QuickLinkCategory>) getSessionScope().get(ATT_CATEGORIES);

        // No dice ... load them.
        if (null == categories) {
            categories = quickLinksService.getQuickLinkCategories();
            getSessionScope().put(ATT_CATEGORIES, categories);
        }

        return categories;
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<QuickLinkWrapper>> getQuickLinks() throws GeneralException {
        // Try to get it from the session.
        final String ATT_QUICK_LINKS = "quickLinksBeanQuickLinks";
        Map<String, List<QuickLinkWrapper>> links =
            (Map<String, List<QuickLinkWrapper>>) getSessionScope().get(ATT_QUICK_LINKS);

        // The court is not in session ... load them.
        if (null == links) {
            links = quickLinksService.getQuickLinks();

            // Since we're storing this in the HTTP session, make sure we load the objects so
            // we don't get lazy load exceptions later.
            for (List<QuickLinkWrapper> wrappers : links.values()) {
                for (QuickLinkWrapper wrapper : wrappers) {
                    wrapper.load();
                }
            }

            getSessionScope().put(ATT_QUICK_LINKS, links);
        }

        return links;
    }

    /**
     * Return the number of individuals that you can submit a lifecycle request for.
     * If zero, we present a message indicating that you are SOL
     */
    public int getRequesteeCount() throws GeneralException {
        // Try to get it from the session.
        final String ATT_REQUESTEE_COUNT = "quickLinksBeanRequesteeCount";
        Integer requesteeCount = (Integer) getSessionScope().get(ATT_REQUESTEE_COUNT);

        // Swing and a miss ... gotta calculate it.
        if (null == requesteeCount) {
            LCMConfigService svc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone(), getQuickLinkName());
            QueryOptions qo = svc.getConfiguredIdentityQueryOptions(getLoggedInUser(), getLoggedInUserDynamicScopeNames());
            if (qo == null) {
                requesteeCount = 0;
            } else {
                requesteeCount = getContext().countObjects(Identity.class, qo);
            }
            getSessionScope().put(ATT_REQUESTEE_COUNT, requesteeCount);
        }

        return requesteeCount;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public String getQuickLinkName() {
        return quickLinkName;
    }

    public void setQuickLinkName(String quickLinkName) {
        this.quickLinkName = quickLinkName;
    }

    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * JSF action that gets executed when a quick link is clicked.
     */
    public String chooseQuickLink() throws GeneralException {
        String returnStr = "";
        BaseRequestBean.clearSession();

        QuickLink quickLink = quickLinksService.getQuickLink(quickLinkName);
        
        // If not valid quick link, you should not have been able to use it.
        if (!validateQuickLink(quickLink)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(getLoggedInUser().getName() + " is not allowed to access quicklink [" + quickLinkName + "]");
            }
            returnStr = "";
            getSessionScope().put(LCMConfigService.ATT_LCM_CONFIG_SERVICE_ACTION, returnStr);
            return returnStr;
        }

        // A null identityId means that this was a request "for me".
        boolean isSelfRequest = !Util.isNothing(getIdentityId());
        boolean wfIdentitySelection = !isSelfRequest;

        // Process the quick link.  This may launch the workflow for a self
        // request or redirect to an external URL.
        QuickLinkExecutorBean executorBean = new QuickLinkExecutorBean(this);
        returnStr = executorBean.processQuickLink(quickLink, getIdentityId(),
                                                  wfIdentitySelection);

        if (returnStr != null) {

            // Self requests, Request Access and Create Identity do not require selecting an
            // identity.  Just setup some stuff in the session.
            if (returnStr.equals(Consts.NavigationString.requestAccess.name())) {
                returnStr = returnStr + "#/accessRequest" + "?quickLink=" + URLUtil.encodeUTF8(quickLink.getName());
            }
            else if (returnStr.equals(Consts.NavigationString.managePasswords.name()) ||
                    returnStr.equals(Consts.NavigationString.manageAccounts.name()) ||
                    returnStr.equals(Consts.NavigationString.manageAttributes.name()) ||
                    returnStr.equals(Consts.NavigationString.viewIdentity.name())) {
                returnStr = returnStr + "#/quickLinks/" + URLUtil.encodeUTF8(quickLink.getName());
            }
            else if (returnStr.equals(Consts.NavigationString.createIdentity.name())) {
                returnStr = returnStr + "#/quickLinks/" + URLUtil.encodeUTF8(quickLink.getName()) + "/createIdentity";
            }
            // reroute the viewCertifications quick link action to the angular cert list page
            else if (returnStr.equals(Consts.NavigationString.viewCertifications.name())) {
                returnStr = "viewResponsiveCertification#/certifications";
            }
            else if (returnStr.equals(Consts.NavigationString.viewApprovals.name())) {
                returnStr = "viewResponsiveApproval#/approvals";
            }
            else if (returnStr.equals(Consts.NavigationString.manageWorkItems.name())) {
                String workItemType = (String)getSessionScope().get(WORK_ITEM_TYPE);
                if (Util.isNotNullOrEmpty(workItemType)) {
                    // need to reattach workItemType param after hash
                    returnStr += "#/workItems?" + "workItemType="+ URLUtil.encodeUTF8(workItemType);
                }
            }
            else if (returnStr.equals(Consts.NavigationString.createIdentity.name()) || isSelfRequest) {
                getSessionScope().put(BaseListBean.ATT_EDITFORM_ID, getIdentityId());
                getSessionScope().put(IdentityListBean.ATT_SELECTED_ID, getIdentityId());
            } else {
                // Determine if this quicklink allows bulk requesting.
                Map<String,List<QuickLinkWrapper>> quickLinksMap = getQuickLinks();
                QuickLinkWrapper quickLinkWrapper = getQuickLinkWrapperFromMap(quickLinksMap);
                boolean allowBulk = (quickLinkWrapper != null) ? quickLinkWrapper.isAllowBulk() : false;
                
                getSessionScope().put(IdentityRequest.ATT_ALLOW_BULK, allowBulk);
                // Store the flow name so we'll know where to go after submitting
                // the identity selections.
                getSessionScope().put(IdentityRequest.ATT_FLOW_NAME, returnStr);
                if(LOG.isInfoEnabled()) {
                    LOG.info("Running LCM Action: " + "requestPopulation" + " with flow " + returnStr);
                }
                returnStr = "requestPopulation?quickLink="+URLUtil.encodeUTF8(quickLink.getName());
            }
        }

        if(LOG.isInfoEnabled()) {
            LOG.info("Running LCM Action: " + returnStr);
        }
        getSessionScope().put(LCMConfigService.ATT_LCM_CONFIG_SERVICE_ACTION, quickLink.getAction());
        getSessionScope().put(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK, quickLink.getName());
        
        if (returnStr != null && returnStr.equals(Consts.NavigationString.viewIdentity.name())) {
            returnStr = IdentityDTO.createNavString(Consts.NavigationString.viewIdentity.name(), getIdentityId());
        }
        return returnStr;
    }
    
    /**
     * Helper method to return a QuickLinkWrapper that contains the name of the quick link based on the quick link that is being launched
     * @param quickLinksMap map of categories to list of QuickLinkWrappers
     * @return the QuickLinkWrapper that has a matched QuickLink name that is being launched, null if empty or not found
     */
    private QuickLinkWrapper getQuickLinkWrapperFromMap(
            Map<String, List<QuickLinkWrapper>> quickLinksMap) {
        if (Util.isEmpty(quickLinksMap)) {
            return null;
        }
        for (String category : Util.mapKeys(quickLinksMap)) {
            List<QuickLinkWrapper> list = quickLinksMap.get(category);
            for (QuickLinkWrapper quickLinkWrapper : Util.safeIterable(list)) {
                QuickLink ql = quickLinkWrapper.getQuickLink();
                if (Util.nullSafeEq(ql.getName(), quickLinkName)) {
                    return quickLinkWrapper;
                }
            }
        }
        return null;
    }


    /**
     * Validate that the logged in user is able to execute the given quick link
     * and that they're not trying to game the system.
     */
    private boolean validateQuickLink(QuickLink quickLink) throws GeneralException {

        //bug#27332 -- quicklink access is removed while user is logged in.
        if (quickLink == null) {
            return false;
        }
        
        boolean isSelfRequest = !Util.isNothing(getIdentityId());
        
        // If this is a self request make sure that the identity ID actually
        // matches the ID of the logged in user.
        if (isSelfRequest && !getLoggedInUser().getId().equals(getIdentityId())) {
            return false;
        }

        boolean authorized = false;
        QuickLinkLaunchAuthorizer authz = new QuickLinkLaunchAuthorizer(quickLink, isSelfRequest);
        try {
            authz.authorize(this);
            authorized = true;
        }
        catch (UnauthorizedAccessException e) { /* leave authorized as false */ }

        return authorized;
    }
}
