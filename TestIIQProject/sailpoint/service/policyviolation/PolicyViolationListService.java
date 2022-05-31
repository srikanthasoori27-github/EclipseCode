/*
 * (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.policyviolation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.PolicyViolationCertificationManager;
import sailpoint.integration.ListResult;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.service.BaseListService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.UserContext;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * Service handling a list of policy violations.
 */
public class PolicyViolationListService extends BaseListService<PolicyViolationListServiceContext> {

    /**
     * Key for the metadata entry holding statusCounts map
     */
    public static String METADATA_STATUS_COUNTS = "statusCounts";

    private UserContext userContext;

    /**
     * Constructor
     * @param userContext UserContext
     * @param listServiceContext ListServiceContext
     * @param columnSelector ListServiceColumnSelector
     */
    public PolicyViolationListService(
        UserContext userContext,
        PolicyViolationListServiceContext listServiceContext,
        ListServiceColumnSelector columnSelector) throws GeneralException {

        super(userContext.getContext(), listServiceContext, columnSelector);
        this.userContext = userContext;
    }

    /**
     * List the PolicyViolations for the logged in user.
     *
     * @return ListResult containing PolicyViolationDTO objects
     * @throws GeneralException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ListResult getPolicyViolations() throws GeneralException {
        QueryOptions queryOptions = super.createQueryOptions();

        addBasicPolicyViolationFilters(queryOptions);

        int count = countResults(PolicyViolation.class, queryOptions);
        List<PolicyViolationDTO> policyViolationDTOs = new ArrayList<>();
        if (count > 0) {
            List<PolicyViolation> policyViolations = context.getObjects(PolicyViolation.class, queryOptions);
            for (PolicyViolation pv : Util.iterate(policyViolations)) {
                PolicyViolationDTO policyViolationDTO = new PolicyViolationDTO(pv, userContext);
                policyViolationDTO.setAllowedActions(this.getPolicyViolationDecisionChoices(context, pv));
                policyViolationDTOs.add(policyViolationDTO);
            }
        }

        ListResult listResult = new ListResult(policyViolationDTOs, count);

        // Add the statusCounts metadata
        listResult.setMetaData(new HashMap());
        listResult.getMetaData().put(METADATA_STATUS_COUNTS, getStatusCounts());

        return listResult;
    }

    /**
     * Add the "basic" filters for policy violations, limiting to logged in user, active, etc.
     */
    public void addBasicPolicyViolationFilters(QueryOptions queryOptions) throws GeneralException {
        queryOptions.add(Filter.eq("active", true));

        boolean showAll = listServiceContext.isShowAll();

        // add identity name filter if not showAll or user doesn't have access
        // if both showAll is true and user has access then the identity name filter doesn't get added
        // and the user can view all policy violations
        if (!showAll || !Authorizer.hasAccess( userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(),
                SPRight.FullAccessPolicyViolation)) {

            // Get violations that are owned by or delegated to the logged in user (IIQMAG-1952),
            // or one of the workgroups the logged in user is a member of.

            Filter ownerFilter = ObjectUtil.getOwnerFilterForIdentity(userContext.getLoggedInUser());
            List<String> delegatedViolationIds =  getDelegatedViolationIds();
            if (Util.isEmpty(delegatedViolationIds)) {
                queryOptions.add(ownerFilter);
            } else {
                Filter delegateOwnerFilter = Filter.in("id", delegatedViolationIds);
                queryOptions.add(Filter.or(ownerFilter, delegateOwnerFilter));
            }
        }

        // filter out violations for the current user
        queryOptions.add(Filter.ne("identity.name", userContext.getLoggedInUser().getName()));
    }

    /**
     * Get a list of policy violation ids for any violations that have been delegated to the current user
     * @return List<String> list of policy violation ids
     * @throws GeneralException
     */
    private List<String> getDelegatedViolationIds() throws GeneralException {
        ArrayList<String> violationIds = new ArrayList<String>();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("type", WorkItem.Type.Delegation));
        qo.add(QueryOptions.getOwnerScopeFilter(userContext.getLoggedInUser(), "owner"));
        Iterator<Object[]> iter = context.search(WorkItem.class, qo, "targetId");
        while (iter.hasNext()) {
            violationIds.add((String) iter.next()[0]);
        }
        return violationIds;
    }

    /**
     * Get the configuration for making decision on policy violation list
     * @return PolicyViolationDecisionConfigDTO object
     * @throws GeneralException
     */
    public PolicyViolationDecisionConfigDTO getDecisionConfig() throws GeneralException {
        return new PolicyViolationDecisionConfigDTO(this.userContext);
    }

    /**
     * Get the allowed policy violation decision choices.
     * @param context SailPointContext
     * @param violation PolicyViolation object
     * @return List of ActionStatus object corresponding to allowed actions on the violation
     */
    public List<CertificationDecisionStatus.ActionStatus> getPolicyViolationDecisionChoices(
            SailPointContext context,
            PolicyViolation violation) throws GeneralException {

        CertificationDecisionStatus decisions = PolicyViolationCertificationManager.getViolationDecisionChoices(
            context,
            violation,
            this.allowDelegate());

        return new ArrayList<>(decisions.getStatuses().values());
    }

    private boolean allowDelegate() throws GeneralException {

        return getContext().getConfiguration().getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, false);
    }

    /**
     * @return a map keyed by policy violation status with values holding the counts of violations
     * with that status.
     */
    private Map<PolicyViolation.Status, Integer> getStatusCounts() throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();
        addBasicPolicyViolationFilters(queryOptions);

        queryOptions.addGroupBy("status");

        List<String> cols = new ArrayList<String>();
        cols.add("status");
        cols.add("count(*)");

        Iterator<Object[]> rows = this.context.search(PolicyViolation.class, queryOptions, cols);
        Map<PolicyViolation.Status, Integer> statusCountsMap = new HashMap<>();
        if (rows != null) {
            while (rows.hasNext()) {
                Object[] row = rows.next();
                statusCountsMap.put((PolicyViolation.Status)row[0], ((Long)row[1]).intValue());
            }
        }

        return statusCountsMap;
    }

}