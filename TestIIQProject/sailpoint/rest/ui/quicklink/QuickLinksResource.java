/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.quicklink;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.object.*;
import sailpoint.rest.BaseListResource;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.web.QuickLinkDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * A REST resource to manage quick links.
 */
@Path("quickLinks")
public class QuickLinksResource extends BaseListResource {

    /**
     * Return a sub-resource that handles a quick link.
     *
     * @param  quicklinkName  The name of the quick link.
     *
     * @return A sub-resource that handles a quick link.
     */
    @Path("{quicklinkName}")
    public QuickLinkResource getQuickLinkResource(@PathParam("quicklinkName") String quicklinkName) {
        return new QuickLinkResource(this, quicklinkName);
    }
    /**
     * Provides a list of available actions for the identity
     * @param {String} identityId - identit id
     * @return {List<QuickLinkDTO>} list of available actions for the identity
     * @throws GeneralException
     */
    @GET
    @Path("{identityId}/availableActions")
    public List<QuickLinkDTO> getAvailableActions(@PathParam("identityId") String identityId) throws GeneralException {

        String[] actions = {QuickLink.LCM_ACTION_MANAGE_ACCOUNTS, QuickLink.LCM_ACTION_MANAGE_PASSWORDS, QuickLink.LCM_ACTION_VIEW_IDENTITY,
                QuickLink.LCM_ACTION_EDIT_IDENTITY};

        List<QuickLinkDTO> linkDTOs = new ArrayList<QuickLinkDTO>();
        Identity requestee = getContext().getObjectById(Identity.class, identityId);
        List<QuickLink> quicklinks = new ArrayList<QuickLink>();
        for (String action : actions) {
            quicklinks = getQuickLinksByAction(action);
            QuickLink ql = getSupportedQuickLink(quicklinks, requestee);
            if (ql != null && authorizeByQuickLink(ql.getName(), requestee)) {
                linkDTOs.add(new QuickLinkDTO(ql, getLoggedInUserDynamicScopeNames()));
            }
        }
        return linkDTOs;
    }

    /**
     * return one quicklink that is a allowed for the requester and requestee from
     * a list of the quicklink that has the same action
     * @param {List<QuickLink>} a list of the quicklink that has the same action
     * @param {Identity} requester
     * @param {Identity} requestee
     * @return {QuickLink} quicklink that is a allowed for the requester and requestee
     */
    private QuickLink getSupportedQuickLink(List<QuickLink> quicklinks, Identity requestee) throws GeneralException {
        for (QuickLink ql : quicklinks) {
            if (Capability.hasSystemAdministrator(getLoggedInUserCapabilities())) {
                return ql;
            }
            if (hasDynamicScopesWithQuicklink(ql.getName(), isSelfService(requestee))) {
                return ql;
            }
        }
        return null;
    }

    /**
     * get all quicklinks has the given action
     * @param {String} action - quicklink action
     * @return {List<QuickLink>} - list of quicklinks
     * @throws GeneralException
     */
    private List<QuickLink> getQuickLinksByAction(String action) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("action", action));
        return (List<QuickLink>)getContext().getObjects(QuickLink.class, qo);
    }

    /**
     * return true if user is doing selfService request
     * @param {Identity} requestee
     * @return {boolean} true if requester is the requestee
     */
    private boolean isSelfService(Identity requestee) throws GeneralException{
        return requestee.getId().equals(getLoggedInUser().getId());
    }

    /**
     * Return the true if any dynamic scope in the given list supports the given quicklink.
     * @param {List<String>} dynamicScopeNames
     * @param {String} quickLinkName
     * @param {boolean} isSelf - true if it's self service request
     * @return {boolean} true if quicklink suppose any dynamic scope provided
     * @throws GeneralException
     */
    private boolean hasDynamicScopesWithQuicklink(String quickLinkName, boolean isSelf)
            throws GeneralException {

        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(getContext());
        QueryOptions qo = svc.getQueryOptions(getLoggedInUserDynamicScopeNames(), quickLinkName, null, isSelf, !isSelf, true);

        if(getContext().countObjects(QuickLinkOptions.class, qo) > 0) {
            return true;
        }
        return false;
    }
    
    /**
     * Authorize by quick link name, default to quick link action
     * @param {Identity} requestee, requestee of the quicklink action
     * @return {boolean} true if logged user is authorized for the quicklink action on the requestee
     * @throws GeneralException
     */
    private boolean authorizeByQuickLink(String quickLinkName, Identity requestee) throws GeneralException{
        boolean auth = false;
        try {
            LcmRequestAuthorizer authorizer = new LcmRequestAuthorizer(requestee);
            authorizer.setQuickLinkName(quickLinkName);
            AuthorizationUtility.authorize(this, authorizer);
            auth = true;
        }
        catch (GeneralException e) {
            auth = false;
        }
        return auth;
    }
}
