/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class RoleDetailsDataSource extends BaseBundleJavaDataSource {
    private static final Log log = LogFactory.getLog(RoleDetailsDataSource.class);

    private static final String ARG_OWNERS = "owners";
    private static final String ARG_TYPE = "type";
    private static final String ARG_DISABLED = "disabled";
    private static final String ARG_SHOW_INDIRECT_APPS = "showIndirectApps";
    private static final String ARG_SHOW_INHERITED_APPS = "showInheritedApps";

    protected static final String COL_APPLICATIONS = "application";
    protected static final String COL_INHERITED_APPLICATIONS = "inherited";

    Map<Bundle, List<String>> bundleApplications = new HashMap<>();
    Map<Bundle, List<String>> bundleInheritedApplications = new HashMap<>();

    @Override
    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments, String groupBy, List<Sort> sort) throws GeneralException {

        initialize(context);

        /**
         * Retrieve configuration for report and set up query options.
         */
        List<Bundle> bundleResults = new ArrayList<>();

        List<String> owners = arguments.getStringList(ARG_OWNERS);
        List<String> types = arguments.getStringList(ARG_TYPE);
        List<String> applications = arguments.getStringList(ARG_APPLICATIONS);
        String disabledString = arguments.getString(ARG_DISABLED);
        boolean hasApplicationFilter = !Util.isEmpty(applications);
        boolean showIndirectApplications = arguments.getBoolean(ARG_SHOW_INDIRECT_APPS);
        boolean showInheritedApplications = arguments.getBoolean(ARG_SHOW_INHERITED_APPS);

        Boolean disabled = (disabledString == null || disabledString.equalsIgnoreCase("null"))
                ? null : Util.otob(disabledString);

        sort = applyGridGrouping(report, sort);

        // If filtering based on application, retrieve the names for the selected applications.
        List<String> appNames = new ArrayList<>();
        if (hasApplicationFilter) {
            QueryOptions opts = new QueryOptions();
            opts.addFilter(Filter.in("id", applications));
            Iterator<Object[]> nameResult = context.search(Application.class, opts, "name");

            nameResult.forEachRemaining((row) -> {
                appNames.add(row[0].toString());
            });
        }

        // First we will pull all rows from Bundle, filtering and sorting as necessary.
        //
        // Then, we will iterate over the roles, adding applications from direct roles and
        // traversing the required, permitted & inherited lists for indirect roles.
        IncrementalObjectIterator<Bundle> allBundles = getAllRoles(owners, types, disabled, report, sort, groupBy);

        while (allBundles.hasNext()) {
            Bundle b = allBundles.next();
            List<String> requiredAppNames = new ArrayList<>(), inheritedAppNames = new ArrayList<>();
            Map<String, String> bundleApps;

            // We need to know indirect & inherited apps if filtering by application.
            // We also need this info if report is configured to show them in the results.
            boolean includeIndirect = (hasApplicationFilter || showIndirectApplications);
            boolean includeInherited = (hasApplicationFilter || showInheritedApplications);

            if (Util.isEmpty(b.getProfiles())) {
                // We only need to fetch applications if:
                // 1. Report is set to show applications in the results
                // 2. User filtered the report by applications
                //
                // In addition, if report is set to filter by app but not show apps,
                // then there's no need to add them to the application Hashmaps.
                if (includeIndirect || includeInherited) {
                    bundleApps = getIndirectApplications(b, includeInherited);

                    bundleApps.forEach((appName, relation) -> {
                        if (includeIndirect && !relation.equals(MessageKeys.REPT_ROLE_BY_APP_INHERIT)) {
                            requiredAppNames.add(appName);
                        } else if (includeInherited && relation.equals(MessageKeys.REPT_ROLE_BY_APP_INHERIT)) {
                            inheritedAppNames.add(appName);
                        }
                    });

                    if (!requiredAppNames.isEmpty() && showIndirectApplications) {
                        bundleApplications.put(b, requiredAppNames);
                    }
                    if (!inheritedAppNames.isEmpty() && showInheritedApplications) {
                        bundleInheritedApplications.put(b, inheritedAppNames);
                    }
                }

                // Include this bundle if there is no application filter, or it satisfies the filter if there is one.
                if (!hasApplicationFilter || hasApplication(appNames, requiredAppNames, inheritedAppNames)) {
                    bundleResults.add(b);
                }
            } else {
                // This is a direct bundle.
                // Fetch the apps directly tied to this bundle.
                bundleApps = getProfileAppsWithRelation(getProfileApplications(b.getProfiles()), null, MessageKeys.REPT_ROLE_BY_APP_DIRECT);
                requiredAppNames.addAll(bundleApps.keySet());
                bundleApplications.put(b, requiredAppNames);

                // Also fetch inherited app names if requested.
                if (includeInherited) {
                    bundleApps = getIndirectApplications(b, includeInherited);
                    bundleApps.forEach((appName, relation) -> {
                        if (relation.equals(MessageKeys.REPT_ROLE_BY_APP_INHERIT)) {
                            inheritedAppNames.add(appName);
                        }
                    });
                    if (showInheritedApplications) {
                        bundleInheritedApplications.put(b, inheritedAppNames);
                    }
                }

                if (!hasApplicationFilter || hasApplication(appNames, requiredAppNames, inheritedAppNames)) {
                    bundleResults.add(b);
                }
            }
        }

        estimatedSize = bundleResults.size();
        results = bundleResults.iterator();
    }

    @Override
    public Object getFieldValue(String field) throws GeneralException {
        List<String> applications = null;

        if (currentBundle != null) {
            switch(field) {
                case COL_APPLICATIONS:
                    if (bundleApplications.containsKey(currentBundle)) {
                        applications = bundleApplications.get(currentBundle);
                        Collections.sort(applications);
                    }
                    return applications;
                case COL_INHERITED_APPLICATIONS:
                    if (bundleInheritedApplications.containsKey(currentBundle)) {
                        applications = bundleInheritedApplications.get(currentBundle);
                        Collections.sort(applications);
                    }
                    return applications;
                default:
                    return super.getFieldValue(field);
            }
        }

        return null;
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        try {
            return getFieldValue(fieldName);
        } catch (GeneralException e) {
            log.error("Exception thrown while getting field value for Role Details Report. "
                    + "Exception [" + e.getMessage() + "].");

            throw new JRException(e);
        }
    }
}
