/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.ReportDataSource;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportingLibrary;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public abstract class BaseBundleJavaDataSource implements JavaDataSource {

    SailPointContext context = null;
    Iterator<Bundle> results = null;
    Bundle currentBundle = null;
    QueryOptions baseOptions;
    int estimatedSize = 0;
    TimeZone timezone;
    Locale locale;

    protected static final String ARG_APPLICATIONS = "applications";

    protected static final String COL_ROLE = "role";
    protected static final String COL_OWNER = "owner";
    protected static final String COL_TYPE = "type";
    protected static final String COL_RELATION = "relation";
    protected static final String COL_CLASSIFICATION = "classification";
    protected static final String COL_STATUS = "status";

    protected void initialize(SailPointContext context) {
        this.context = context;
    }

    @Override
    public Object getFieldValue(String field) throws GeneralException {
        if (currentBundle != null) {
            switch(field) {
                case COL_ROLE:
                    return currentBundle.getName();
                case COL_OWNER:
                    Identity owner = currentBundle.getOwner();
                    return (owner != null) ? owner.getName() : null;
                case COL_TYPE:
                    String value = currentBundle.getType();

                    if (value != null){
                        String displayName = ReportingLibrary.getRoleTypeDisplayName(value);
                        if (displayName != null){
                            return displayName;
                        }
                    }

                    return value;
                case COL_CLASSIFICATION:
                    return currentBundle.getClassificationDisplayNames();
                case COL_STATUS:
                    return currentBundle.isDisabled() ? MessageKeys.DISABLED : MessageKeys.ENABLED;
            }
        }

        return null;
    }

    @Override
    public void setLimit(int startRow, int pageSize) {
        // Preview not enabled for these reports.
    }

    @Override
    public int getSizeEstimate() throws GeneralException {
        return estimatedSize;
    }

    @Override
    public QueryOptions getBaseQueryOptions() {
        return baseOptions;
    }

    @Override
    public String getBaseHql() {
        return null;
    }

    @Override
    public void setMonitor(Monitor monitor) {
        // Needed for these reports?
    }

    @Override
    public void close() {
        // Nothing to cleanup by default. Override if necessary.
    }

    @Override
    public boolean next() throws JRException {
        boolean hasNext = results.hasNext();

        if (hasNext) {
            currentBundle = results.next();
        }

        return hasNext;
    }

    protected Iterator<Bundle> getRoles(List<String> applications, LiveReport report, List<Sort> sort, String groupBy) throws GeneralException {
        baseOptions = new QueryOptions();

        if (!Util.isEmpty(applications)) {
            baseOptions.addFilter(Filter.in("profiles.application.id", applications));
            baseOptions.setDistinct(true);
        } else {
            applyOrdering(baseOptions, report, sort, groupBy);
        }

        /**
         * Pull either all roles, or all direct roles related to the selected applications.
         */
        return context.search(Bundle.class, baseOptions);
    }

    protected IncrementalObjectIterator<Bundle> getAllRoles(LiveReport report, List<Sort> sort, String groupBy) {
        return getAllRoles(null, null, null, report, sort, groupBy);
    }

    protected IncrementalObjectIterator<Bundle> getAllRoles(List<String> owners, List<String> types, Boolean disabled,
                                        LiveReport report, List<Sort> sort, String groupBy) {
        QueryOptions queryOptions = new QueryOptions();

        if (!Util.isEmpty(owners)) {
            queryOptions.addFilter(Filter.in("owner.id", owners));
        }

        if (!Util.isEmpty(types)) {
            queryOptions.addFilter(Filter.in("type", types));
        }

        if (disabled != null) {
            queryOptions.addFilter(Filter.eq("disabled", disabled));
        }

        applyOrdering(queryOptions, report, sort, groupBy);

        return new IncrementalObjectIterator<>(context, Bundle.class, queryOptions);
    }

    // Apply group by & order by sorting for bundles
    protected void applyOrdering(QueryOptions qo, LiveReport report, List<Sort> sorts, String groupBy) {
        if (!Util.isNullOrEmpty(groupBy)) {
            ReportColumnConfig config = report.getGridColumnByFieldName(groupBy);
            if (config != null && !Util.isNullOrEmpty(config.getProperty())) {
                qo.addGroupBy(config.getProperty());
            }
        }
        for (Sort s : Util.safeIterable(sorts)) {
            ReportColumnConfig config = report.getGridColumnByFieldName(s.getField());
            if (config != null && !Util.isNullOrEmpty(config.getProperty())) {
                qo.addOrdering(config.getProperty(), s.isAscending());
            }
        }
    }

    // Add any grid grouping to the list of sorts
    protected List<Sort> applyGridGrouping(LiveReport report, List<Sort> sorts) {
        String gridGrouping = null;
        ReportDataSource dataSource = report.getDataSource();

        // This is the report UI's grid group by, not a SQL group by.
        // The grid does its grouping under the assumption that all rows with the same
        // value are grouped together. So, we need to sort on the selected column to ensure
        // that holds true.
        if (dataSource != null) {
            gridGrouping = dataSource.getGridGrouping();
        }

        if (gridGrouping != null) {
            if (sorts == null) {
                sorts = new ArrayList<>();
            }

            // It's okay if this sort already exists, duplicates will be filtered out later
            // when added to the queryoptions.
            sorts.add(0, new Sort(gridGrouping, true));
        }

        return sorts;
    }

    // Convenience method to use if there are no targetApps, i.e. all apps should be returned
    protected Map<String, String> getIndirectApplications(Bundle role, boolean includeInherited) {
        return getIndirectApplications(role, null, includeInherited);
    }

    // Convenience method to use if there are no existing results. This will create a new result Hashmap
    // and return it.
    protected Map<String, String> getIndirectApplications(Bundle role, List<String> targetApps, boolean includeInherited) {
        Map<String, String> result = new HashMap<>();
        getIndirectApplications(role, targetApps, includeInherited, result, new HashSet<>());
        return result;
    }

    /**
     * Visits the required & permitted bundles for role and populates result with the applications
     * associated with them, along with the relation. Optionally can also include inherited applications.
     *
     * @param role The Bundle whose requirements & permits we want to search.
     * @param targetApps (Optional) Only include the specified apps in the result.
     * @param includeInherited If true, also include inherited applications.
     * @param result (Optional) The result Hashmap to populate.
     * @return Map with associated applications as the key, and the role's relation to the application as the
     * value. This will be the same Map instance as result, if provided.
     */
    protected Map<String, String> getIndirectApplications(Bundle role, List<String> targetApps, boolean includeInherited,
                                                          Map<String, String> initResult) {
        if (initResult == null) {
            initResult = new HashMap<>();
        }
        getIndirectApplications(role, targetApps, includeInherited, initResult, new HashSet<>());
        return initResult;
    }

    private void getIndirectApplications(Bundle role, List<String> targetApps, boolean includeInherited,
                                         Map<String, String> result, Set<Bundle> seen) {
        Map<Iterator<Bundle>, String> iterators = new HashMap<>();
        boolean hasTargetApps = !Util.isEmpty(targetApps);

        if (seen != null && !seen.contains(role)) {
            seen.add(role);

            // Set up iterators for the roles that we should traverse
            iterators.put(Util.safeIterable(role.getRequirements()).iterator(), MessageKeys.REQUIRED);
            iterators.put(Util.safeIterable(role.getPermits()).iterator(), MessageKeys.REPT_ROLE_BY_APP_PERMIT);

            if (includeInherited) {
                iterators.put(Util.safeIterable(role.getInheritance()).iterator(), MessageKeys.REPT_ROLE_BY_APP_INHERIT);
            }

            for (Iterator<Bundle> iterator : iterators.keySet()) {
                while (iterator.hasNext() && (!hasTargetApps || result.size() < targetApps.size())) {
                    Bundle b = iterator.next();

                    // If this is a direct role, add its app(s) to the result
                    if (b.getProfiles() != null) {
                        String relation = iterators.get(iterator);
                        result = getProfileAppsWithRelation(getProfileApplications(b.getProfiles()), targetApps, relation, result);
                    }

                    // Continue to traverse if we haven't found all target apps yet, or we're looking
                    // for all apps.
                    if (!hasTargetApps || result.size() < targetApps.size()) {
                        getIndirectApplications(b, targetApps, includeInherited, result, seen);
                    }
                }
            }
        }
    }

    // Convenience method if there is no existing result Map to use.
    // Will return a brand new Hashmap.
    protected Map<String, String> getProfileAppsWithRelation(List<Application> apps, List<String> targetApps, String relation) {
        return getProfileAppsWithRelation(apps, targetApps, relation, null);
    }

    /**
     * Return a Map of the application names for the provided list of Applications as the key,
     * and the provided relation as the value.
     *
     * @param apps List of Applications
     * @param targetApps (Optional) Only include applications if they are in this list
     * @param relation Relation to use as the Map value
     * @param result (Optional) Map to append results to
     * @return Map of application names and relations. This will be the same Map instance as result, if provided.
     */
    protected Map<String, String> getProfileAppsWithRelation(List<Application> apps, List<String> targetApps, String relation, Map<String, String> result) {
        if (result == null) {
            result = new HashMap<>();
        }

        for (Application app : apps) {
            if (Util.isEmpty(targetApps) || targetApps.contains(app.getId())) {
                String appName = app.getName();

                if (!result.containsKey(appName)) {
                    result.put(appName, relation);
                }
            }
        }

        return result;
    }

    /**
     * Extract all Applications from the specified list of Profiles.
     *
     * @param profiles List of Profiles to use
     * @return List of Applications
     */
    protected List<Application> getProfileApplications(List<Profile> profiles) {
        List<Application> result = new ArrayList<>();

        for (Profile p : Util.safeIterable(profiles)) {
            Application app = p.getApplication();
            if (app != null) {
                result.add(app);
            }
        }

        return result;
    }

    /**
     * Check whether the list of Required app names or list of Inherited app names contain any of the
     * target app names.
     *
     * @param targetNames Application names to look for
     * @param requiredNames Required application names to search
     * @param inheritedNames Inherited application names to search
     * @return
     */
    protected boolean hasApplication(List<String> targetNames, List<String> requiredNames, List<String> inheritedNames) {
        for (String appName : Util.safeIterable(requiredNames)) {
            if (targetNames.contains(appName)) {
                return true;
            }
        }

        for (String appName : Util.safeIterable(inheritedNames)) {
            if (targetNames.contains(appName)) {
                return true;
            }
        }

        return false;
    }

    protected String getMessage(String key, Object... args) {
        if (key == null)
            return null;

        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(locale, timezone);
    }
}
