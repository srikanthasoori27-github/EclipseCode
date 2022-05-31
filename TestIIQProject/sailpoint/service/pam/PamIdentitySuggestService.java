/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.tools.GeneralException;
import sailpoint.web.link.LinkDTO;
import sailpoint.web.link.PamLinkDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PamIdentitySuggestService {

    SailPointContext context;

    public PamIdentitySuggestService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Return the list of identities that fit the search criteria.  Decorate the list of identities with the list of
     * accounts that are attached to the PAM application
     *
     * @param ops QueryOptions A set of query options created from the query parameters
     * @return ListResult The list of identities that include their accounts on the pam application
     * @throws GeneralException
     */
    public ListResult getIdentities(QueryOptions ops, String applicationName) throws GeneralException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int total = this.context.countObjects(Identity.class, ops);

        PamExternalUserStoreService externalUserStore = new PamExternalUserStoreService(this.context, null);

        Application pamApplication = this.context.getObjectByName(Application.class, applicationName);

        if (total > 0) {
            results = SuggestHelper.getSuggestResults(Identity.class, ops, this.context);
        }

        for (Map<String, Object> result : results) {
            List<LinkDTO> links = this.getPamAccounts((String)result.get("id"), pamApplication, externalUserStore);
            result.put("accounts", links);
            result.put("applicationName", applicationName);
        }

        return new ListResult(results, total);
    }

    /**
     * Get the list of accounts that the identity has on the PAM application.  Any identity who does not have
     * an account on the PAM application will be blocked for selection on provisioning
     * @param identityId String The id of the identity
     * @param pamApplication Application The configured application for PAM
     * @param externalUserStore PamExternalUserStoreService used to get the external link/application name
     * @return a list of link dtos
     * @throws GeneralException
     */
    private List<LinkDTO> getPamAccounts(String identityId, Application pamApplication,
                                         PamExternalUserStoreService externalUserStore) throws GeneralException{
        List<LinkDTO> linkDTOS = new ArrayList();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity.id", identityId));
        qo.add(Filter.eq("application.id", pamApplication.getId()));
        List<Link> links = context.getObjects(Link.class, qo);
        for(Link link : links) {
            PamLinkDTO pamLink = new PamLinkDTO(link);
            Link externalLink = externalUserStore.getExternalLink(link);

            if(externalLink != null) {
                pamLink.setExternalAccountId(externalLink.getDisplayableName());
                pamLink.setExternalApplicationName(externalLink.getApplicationName());
            }

            linkDTOS.add(pamLink);
        }

        return linkDTOS;
    }
}