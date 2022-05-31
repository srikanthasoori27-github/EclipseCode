/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.identityrequest;

import java.util.ArrayList;
import java.util.List;

import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.search.IdentityRequestFilterBuilder;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.LCMConfigService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.UserContext;

/**
 * @author patrick.jeong
 */
public class IdentityRequestListService extends BaseListService<BaseListServiceContext> {
    private UserContext userContext;

    /**
     * Constructor
     *
     * @param userContext UserContext
     * @param listServiceContext ListServiceContext
     * @param columnSelector ListServiceColumnSelector
     */
    public IdentityRequestListService(UserContext userContext, BaseListServiceContext listServiceContext,
                                      ListServiceColumnSelector columnSelector) {
        super(userContext.getContext(), listServiceContext, columnSelector);
        this.userContext = userContext;
    }

    /**
     * Get the list of identity requests for user.
     *
     * If the logged in user can NOT view all the requests then only those requests made BY the logged in user or requests
     * made FOR the logged in user will be shown.
     *
     * @return ListResult list of identity requests
     * @throws GeneralException
     */
    public ListResult getIdentityRequests() throws GeneralException {
        QueryOptions queryOptions = super.createQueryOptions();

        // default order by created desc
        queryOptions.addOrdering("created", false);

        String query = this.listServiceContext.getQuery();
        if (!Util.isNothing(query)) {
            List<Filter> searchFilters = new ArrayList<>();
            searchFilters.add(Filter.like("targetDisplayName", query, Filter.MatchMode.START));
            searchFilters.add(Filter.like("requesterDisplayName", query, Filter.MatchMode.START));
            // Only need to search on externalTicketId if it is configured.
            if (new LCMConfigService(this.context).isShowExternalTicketId()) {
                searchFilters.add(Filter.eq("externalTicketId", query));
            }
            // Make sure this looks like a number before trying to get the padded sequence
            if (Util.isInt(query)) {
                searchFilters.add(Filter.eq("name", IdentityRequestFilterBuilder.getPaddedNameFilterValue(query)));
            }
            queryOptions.add(Filter.or(searchFilters));
        }

        // if user can not view all the requests restrict the results to only those made by user or made for user
        // also check for identity request item owner
        if (!canViewAll()) {
            //IIQCB-3389 since workgroups can be the requester of IdentityRequests add the logged in users workgroup ids to the filter
            List<Identity> loggedInUserWorkgroups = this.userContext.getLoggedInUser().getWorkgroups();
            List<String> loggedInUserWorkgroupIdList = new ArrayList<String>();
            for (Identity loggedInUserWorkgroup : Util.safeIterable(loggedInUserWorkgroups)) {
                    loggedInUserWorkgroupIdList.add(loggedInUserWorkgroup.getId());
            }

            Filter targetFilter = Filter.or(Filter.eq("targetId", this.userContext.getLoggedInUser().getId()),
                                          Filter.eq("requesterId", this.userContext.getLoggedInUser().getId()));
            
            // If the logged in user is part of a workgroup or this into the filter
            if (!Util.isEmpty(loggedInUserWorkgroupIdList)) {
                targetFilter = Filter.or(targetFilter, Filter.in("requesterId", loggedInUserWorkgroupIdList));
            }

            //NOTE: For a brief period of time, we were consider IdentityRequestItem ownership here too. This was added as part of
            //      the fix for bugzilla bug 15797, to allow approvers to get to the request after they have approved. We are
            //      removing that behavior intentionally, since it doesn't make much sense.
            queryOptions.addFilter(targetFilter);
        }

        int count = countResults(IdentityRequest.class, queryOptions);
        List<IdentityRequestDTO> identityRequestDTOList = new ArrayList<>();

        if (count > 0) {
            List<IdentityRequest> identityRequests = this.context.getObjects(IdentityRequest.class, queryOptions);

            for (IdentityRequest identityRequest : Util.iterate(identityRequests)) {
                IdentityRequestService requestService = new IdentityRequestService(this.context, identityRequest);
                identityRequestDTOList.add(requestService.getDto(this.userContext));
            }

        }

        return new ListResult(identityRequestDTOList, count);
    }

    /**
     * Check if the logged in user can view all the identity requests in the system.
     *
     * True if the user is system admin or has FullAccessIdentityRequest spRight.
     *
     * @return boolean true if the logged in user can view all the identity requests
     */
    private boolean canViewAll() {
        return Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities()) ||
                Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(),
                        SPRight.FullAccessIdentityRequest);
    }
}
