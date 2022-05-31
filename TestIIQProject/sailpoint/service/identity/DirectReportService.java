/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.service.LCMConfigService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;


/**
 * A service that helps getting information about people that report to a manager.
 */
public class DirectReportService {

    /**
     * The actions that we want to check for each direct report.
     */
    private static String[] ACTIONS = {
        QuickLink.LCM_ACTION_VIEW_IDENTITY,
        QuickLink.LCM_ACTION_REQUEST_ACCESS,
        QuickLink.LCM_ACTION_MANAGE_PASSWORDS,
        QuickLink.LCM_ACTION_MANAGE_ACCOUNTS
    };

    private SailPointContext context;

    /**
     * Constructor.
     *
     * @param context  The SailPointContext.
     */
    public DirectReportService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Return a ListResult containing the direct reports for the given manager within the given
     * range, sorted by display name.
     *
     * @param  query  The query string used to filter the direct reports.
     * @param  manager  The manager for which to find direct reports.
     * @param  dynamicScopeNames  The names of the dynamic scopes the manager is in.
     * @param  start  The zero-based start index.
     * @param  limit  The max number of results to return.
     *
     * @return A ListResult of DirectReportDTOs.
     */
    public ListResult getDirectReports(String query, Identity manager, List<String> dynamicScopeNames,
                                       int start, int limit)
        throws GeneralException {

        // Load the identities to display.
        int total = countIdentities(query, manager);
        List<DirectReportDTO> reports = getIdentities(query, manager, start, limit);

        // Only look for available actions if there are reports and the manager is in at least one scope.
        if (!Util.isEmpty(reports) && !Util.isEmpty(dynamicScopeNames)) {
            // Create a map for quick lookup while adorning supported actions.
            Map<String,DirectReportDTO> mapById = mapById(reports);

            // Iterate over the actions we're checking.
            for (String action : ACTIONS) {
                // Find which identities the logged in user can perform this action on.
                List<String> matchedIdentities =
                    findRequestableIdentities(manager, dynamicScopeNames, reports, action);

                List<String> quickLinkNames = getQuickLinkNamesByAction(action, manager);
                // Add the action to all reports that we found.
                for (String matchedId : matchedIdentities) {
                    DirectReportDTO directReport = mapById.get(matchedId);
                    directReport.addAction(action, quickLinkNames);
                }
            }
        }

        return new ListResult(reports, total);
    }

    /**
     * Return the number of identities that report to the given manager that match the given query.
     */
    private int countIdentities(String query, Identity manager) throws GeneralException {
        QueryOptions qo = getManagerQueryOptions(query, manager);
        return this.context.countObjects(Identity.class, qo);
    }

    /**
     * Return a non-null list of DirectReportDTOs with the basic information about the direct
     * reports - the available actions must be adorned later.
     */
    private List<DirectReportDTO> getIdentities(String query, Identity manager, int start, int limit)
        throws GeneralException {

        List<DirectReportDTO> reports = new ArrayList<DirectReportDTO>();

        QueryOptions qo = getManagerQueryOptions(query, manager);
        qo.setFirstRow(start);
        qo.setResultLimit(limit);
        qo.addOrdering("displayName", true);

        Iterator<Object[]> results = this.context.search(Identity.class, qo, "id, name, displayName");
        while (results.hasNext()) {
            Object[] result = results.next();
            String id = (String) result[0];
            String name = (String) result[1];
            String displayName = (String) result[2];
            reports.add(new DirectReportDTO(id, name, displayName));
        }

        return reports;
    }

    /**
     * Return QueryOptions to search for identities that report to the given manager and match the
     * given query.
     */
    private QueryOptions getManagerQueryOptions(String query, Identity manager) {
        QueryOptions qo = new QueryOptions();

        // If a query was specified, look for firstname, lastname, or display name matches.
        if (!Util.isNullOrEmpty(query)) {
            qo.add(Filter.or(Filter.ignoreCase(Filter.like("displayName", query, MatchMode.START)),
                             Filter.ignoreCase(Filter.like("firstname", query, MatchMode.START)),
                             Filter.ignoreCase(Filter.like("lastname", query, MatchMode.START))));
        }
        // IIQCB-1945 Set query to return only active users
        qo.add(Filter.and(Filter.eq("manager", manager), Filter.eq("inactive", false)));
        return qo;
    }

    /**
     * Create a map that maps the given reports by their ID.
     */
    private static Map<String,DirectReportDTO> mapById(List<DirectReportDTO> reports) {
        Map<String,DirectReportDTO> map = new HashMap<String,DirectReportDTO>();
        for (DirectReportDTO report : reports) {
            map.put(report.getId(), report);
        }
        return map;
    }

    /**
     * Return the IDs of the identities in the given reports list for which the given manager can
     * perform the given action.
     *
     * Possible edge case where the reports list is too large and the query may fail (in sqlserver max is ~2100).
     * In this case the filter is done post query.
     */
    private List<String> findRequestableIdentities(Identity manager,
                                                   List<String> dynamicScopeNames,
                                                   List<DirectReportDTO> reports,
                                                   String action)
        throws GeneralException {

        List<String> matchedIds = new ArrayList<>();

        // Get the query options that will return all identities that this manager can perform the
        // given action on based on their dynamic scopes.
        LCMConfigService svc = new LCMConfigService(this.context);
        QueryOptions qo = svc.getRequestableIdentityOptions(manager, dynamicScopeNames, null, action);

        if (null != qo) {
            List<String> reportIds = reports.stream().map(x -> x.getId()).collect(Collectors.toList());

            // if the query is empty the manager can request for all so we can just return the direct report ids
            if (qo.getFilters().isEmpty()) {
                return reportIds;
            }

            boolean preprocessReportIds = false;
            // IIQMAG-2199 Report ids list size can potentially be larger than sqlserver max params 2100 so the IN query
            // filter can fail. Only add in the filter if the report ids is less than the max and process the IN filter
            // post query instead.
            if (reportIds.size() < ObjectUtil.MAX_IN_QUERY_SIZE) {
                qo.add(Filter.in("id", reportIds));
                preprocessReportIds = true;
            }

            Iterator<Object[]> it = this.context.search(Identity.class, qo, "id");

            while (it.hasNext()) {
                String identityId = (String) it.next()[0];

                if (preprocessReportIds || reportIds.contains(identityId)) {
                    matchedIds.add(identityId);

                    // if all the direct report identities are matched for the action then we can stop looking
                    if (matchedIds.size() == reportIds.size()) {
                        break;
                    }
                }
            }
        }

        return matchedIds;
    }

    private List<String> getQuickLinkNamesByAction (String action, Identity manager) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("action", action));
        qo.add(Filter.eq("hidden", false));
        qo.addOrdering("name", true);
        Iterator<Object[]> it = context.search(QuickLink.class, qo, "name");
        //Make sure the user can access the quick link
        QuickLinkOptionsConfigService qlSvc = new QuickLinkOptionsConfigService(context);
        ArrayList<String> quickLinkNames = new ArrayList<>();
        while (it.hasNext()) {
            String ql = (String) it.next()[0];
            if (qlSvc.isQuickLinkEnabled(manager, ql, false)) {
                quickLinkNames.add(ql);
            }

        }
        return quickLinkNames;
    }
}
