/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.IdColumnSelector;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * Service for dealing with list of certifications
 *
 * @author patrick.jeong
 */
public class CertificationListService extends BaseListService<BaseListServiceContext> {

    private UserContext userContext;

    /**
     * Constructor
     * @param userContext UserContext
     * @param listServiceContext ListServiceContext
     */
    public CertificationListService(UserContext userContext, BaseListServiceContext listServiceContext,
                                    ListServiceColumnSelector columnSelector) throws GeneralException {
        super(userContext.getContext(), listServiceContext, columnSelector);
        this.userContext = userContext;
    }

    /**
     * Get a filter to limit certifications to the current ones owned by the given user
     * @param currentUser Identity to limit certifications
     * @param responsiveOnly If true, include only types supported by responsive UI
     * @return Filter 
     */
    public static Filter getMyCertificationsFilter(Identity currentUser, boolean responsiveOnly) {
        return getMyCertificationsFilter(currentUser, responsiveOnly, false);
    }

    /**
     * Get a filter to limit certifications to the current ones owned by the given user
     * @param currentUser Identity to limit certifications
     * @param responsiveOnly If true, include only types supported by responsive UI
     * @param showSigned If true, include signed certs
     * @return Filter 
     */
    public static Filter getMyCertificationsFilter(Identity currentUser, boolean responsiveOnly, boolean showSigned) {
        List<Filter> filters = new ArrayList<>();
        List<String> certifiers = new ArrayList<>();
        certifiers.add(currentUser.getName());
        for (Identity workgroup : Util.iterate(currentUser.getWorkgroups())) {
            certifiers.add(workgroup.getName());
        }

        filters.add(Filter.in("certifiers", certifiers));
        filters.add(Filter.ne("phase", Certification.Phase.Staged));
        if(!showSigned) {
            filters.add(Filter.isnull("signed"));
        }

        return Filter.and(filters);
    }

    /**
     * Return a list of the sub-certifications
     *
     * @param certificationId ID of the parent certification
     * @return list of sub certs
     * @throws GeneralException
     */
    public ListResult getSubCertifications(String certificationId) throws GeneralException {
        QueryOptions queryOptions = this.createQueryOptions();

        // None of our columns are sortable, so set this manually here.
        queryOptions.addOrdering("name", true);
        // Add a secondary unique sort to force SQLServer to page properly 
        queryOptions.addOrdering("id", true);
        queryOptions.add(Filter.eq("parent.id", certificationId));

        return getListResult(queryOptions);
    }

    /**
     * Get a list of current certifications
     * @param responsiveOnly If true, limit to certs supported by responsive UI
     * @param showSigned If false, esigned certs are excluded from the results
     * @return ListResult with CertificationDTO objects
     * @throws GeneralException
     */
    public ListResult getCurrentCertifications(boolean responsiveOnly, boolean showSigned) throws GeneralException {
        QueryOptions queryOptions = this.createQueryOptions();

        // sort by expiration will be default or secondary sort option7
        queryOptions.addOrdering("expiration", true);

        //This filter out the signed certs
        queryOptions.add(getMyCertificationsFilter(this.userContext.getLoggedInUser(), responsiveOnly, showSigned));
        
        return getListResult(queryOptions);
    }

    /**
     * Get a ListResult of CertificationDTO from the query options
     * @param queryOptions QueryOptions
     * @return ListResult
     * @throws GeneralException
     */
    private ListResult getListResult(QueryOptions queryOptions) throws GeneralException {
        int count = countResults(Certification.class, queryOptions);
        List<CertificationDTO> certificationDTOs = new ArrayList<>();
        if (count > 0) {
            List<Map<String, Object>> results = getResults(Certification.class, queryOptions);
            for (Map<String, Object> result : Util.iterate(results)) {
                Certification certification = this.context.getObjectById(Certification.class, (String) result.get(IdColumnSelector.ID_COLUMN));
                certificationDTOs.add(new CertificationDTO(certification, this.userContext));
            }
        }
        
        return new ListResult(certificationDTOs, count);
    }
}
