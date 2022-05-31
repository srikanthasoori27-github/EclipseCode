/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.widget;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service class to fetch data to be shown in widgets
 * on the home page.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class WidgetDataService {

    /**
     * Entry in metadata map with a true/false value indicating if the logged in user
     * has the rights to "view all" certification groups
     */
    public static final String METADATA_CAN_VIEW_ALL = "canViewAll";

    /**
     * The user context.
     */
    UserContext userContext;

    /**
     * Constructs a new instance of WidgetDataService.
     *
     * @param userContext The user context.
     */
    public WidgetDataService(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Fetches data to be displayed in the My Access Reviews widget.
     *
     * @param start The start record.
     * @param limit The page limit.
     * @return The list result.
     */
    public ListResult getMyAccessReviews(int start, int limit) throws GeneralException {
        // get certs that have open work items for the current user
        // ordered desc by due date
        QueryOptions queryOptions = getBaseQueryOptions();
        queryOptions.add(QueryOptions.getOwnerScopeFilter(userContext.getLoggedInUser(), "workItems.owner"));
        queryOptions.addOrdering("expiration", true);

        int count = getContext().countObjects(Certification.class, queryOptions);

        queryOptions.setFirstRow(start);
        queryOptions.setResultLimit(limit);

        List<Certification> certifications = getContext().getObjects(Certification.class, queryOptions);

        List<CertificationWidgetData> objects = new ArrayList<CertificationWidgetData>();
        for (Certification certification : Util.iterate(certifications)) {
            MyAccessReviewsWidgetData widgetData = new MyAccessReviewsWidgetData(certification);
            widgetData.setDefaultView(getDefaultView(certification));

            objects.add(widgetData);
        }

        return new ListResult(objects, count);
    }

    private String getDefaultView(Certification certification) throws GeneralException {
        return "viewResponsiveCertification#/certification/" + certification.getId();
    }

    /**
     * Fetches data to be displayed in the Certification Campaigns widget.
     *
     * @param start The start record.
     * @param limit The page limit.
     * @return The list result.
     */
    public ListResult getCertificationCampaigns(int start, int limit) throws GeneralException {
        // get the active cert groups ordered desc by created see bug IIQETN-1581
        QueryOptions queryOptions = getBaseQueryOptions();
        queryOptions.add(Filter.eq("status", CertificationGroup.Status.Active));
        queryOptions.addOrdering("created", true);

        int count = getContext().countObjects(CertificationGroup.class, queryOptions);
        List<CertificationWidgetData> objects = new ArrayList<>();
        if (count > 0) {
            queryOptions.setFirstRow(start);
            queryOptions.setResultLimit(limit);

            List<CertificationGroup> certGroups = getContext().getObjects(CertificationGroup.class, queryOptions);

            for (CertificationGroup certGroup : Util.iterate(certGroups)) {
                CertificationWidgetData widgetData = new CertificationWidgetData(certGroup);

                // have to fetch the due date from a cert in the group if it is
                // not a continuous cert group
                widgetData.setDueDate(getDueDateForCertificationGroup(certGroup));

                objects.add(widgetData);
            }
        }

        ListResult result = new ListResult(objects, count);
        Map<String, Object> metaData = new HashMap<>();
        metaData.put(METADATA_CAN_VIEW_ALL, AuthorizationUtility.isAuthorized(this.userContext, new RightAuthorizer(SPRight.FullAccessCertificationSchedule)));
        result.setMetaData(metaData);
        return result;
    }

    /**
     * Gets the base query options.
     *
     * @return The query options.
     */
    private QueryOptions getBaseQueryOptions() {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.setDistinct(true);

        return queryOptions;
    }

    /**
     * Gets a due date for the certification group by fetching one certification
     * in the group and getting the expiration date.
     *
     * @param certGroup The certification group.
     * @return The due date.
     * @throws GeneralException
     */
    private Date getDueDateForCertificationGroup(CertificationGroup certGroup) throws GeneralException {
        Date dueDate = null;

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", certGroup.getId()));
        queryOptions.setResultLimit(1);

        // grab the date out of the cert
        List<Certification> certifications = getContext().getObjects(Certification.class, queryOptions);
        if (!Util.isEmpty(certifications)) {
            dueDate = certifications.get(0).getExpiration();
        }

        return dueDate;
    }

    /**
     * Gets the context.
     *
     * @return The context.
     */
    private SailPointContext getContext() {
        return userContext.getContext();
    }

    /**
     * Class used to hold the certification widget data.
     */
    private static class CertificationWidgetData {

        /**
         * The object id.
         */
        private String id;

        /**
         * The object name.
         */
        private String name;

        /**
         * The number of completed items.
         */
        private int completedItems;

        /**
         * The total number of items.
         */
        private int totalItems;

        /**
         * The due date of the object.
         */
        private Date dueDate;

        /**
         * Constructor that populates the object using the
         * specified Certification object.
         *
         * @param certification The certification.
         */
        public CertificationWidgetData(Certification certification) {
            setId(certification.getId());
            setName(certification.getShortName());
            setCompletedItems(certification.getCompletedItems());
            setTotalItems(certification.getTotalItems());
            setDueDate(certification.getExpiration());
        }

        /**
         * Constructor that populates the object using the
         * specified CertificationGroup object. Note that this
         * does not populate the due date.
         *
         * @param certGroup The certification group.
         */
        public CertificationWidgetData(CertificationGroup certGroup) {
            setId(certGroup.getId());
            setName(certGroup.getName());
            setCompletedItems(certGroup.getCompletedCertifications());
            setTotalItems(certGroup.getTotalCertifications());
        }

        /**
         * Gets the object id.
         *
         * @return The id.
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the object id.
         *
         * @param id The id.
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Gets the object name.
         *
         * @return The name.
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the object name.
         *
         * @param name The name.
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the number of completed items.
         *
         * @return The completed items.
         */
        public int getCompletedItems() {
            return completedItems;
        }

        /**
         * Sets the number of completed items.
         *
         * @param completedItems The completed items.
         */
        public void setCompletedItems(int completedItems) {
            this.completedItems = completedItems;
        }

        /**
         * Gets the total number of items.
         *
         * @return The total items.
         */
        public int getTotalItems() {
            return totalItems;
        }

        /**
         * Sets the total number of items.
         *
         * @param totalItems The total items.
         */
        public void setTotalItems(int totalItems) {
            this.totalItems = totalItems;
        }

        /**
         * Gets the due date of the object.
         *
         * @return The due date.
         */
        public Date getDueDate() {
            return dueDate;
        }

        /**
         * Sets the due date of the object.
         *
         * @param dueDate The due date.
         */
        public void setDueDate(Date dueDate) {
            this.dueDate = dueDate;
        }

    }

    /**
     * Class that holds data for the My Access Reviews home page widget.
     */
    private static class MyAccessReviewsWidgetData extends CertificationWidgetData {

        /**
         * The default view for the certification based on the logged in user.
         */
        private String defaultView;

        /**
         * Constructs a new instance of MyAccessReviewsWidgetData.
         *
         * @param certification The certification.
         */
        public MyAccessReviewsWidgetData(Certification certification) {
            super(certification);
        }

        /**
         * Gets the default view.
         *
         * @return The default view.
         */
        public String getDefaultView() {
            return this.defaultView;
        }

        /**
         * Sets the default view.
         *
         * @param defaultView The default view.
         */
        public void setDefaultView(String defaultView) {
            this.defaultView = defaultView;
        }

    }

}
