/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.LiveReport;
import sailpoint.object.Sort;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class ApplicationBundleDataSource extends BaseBundleJavaDataSource {
    private static final Log log = LogFactory.getLog(ApplicationBundleDataSource.class);

    private static final String ARG_INCLUDE_INHERITED = "includeInherited";
    private static final String ARG_SHOW_ALL_APPS = "showAllApps";

    Map<Bundle, Map<String, String>> appWithTypeBundles = new HashMap<>();

    @Override
    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments, String groupBy, List<Sort> sort) throws GeneralException {

        initialize(context);

        /**
         * Retrieve configuration for report and set up query options.
         */
        List<Bundle> bundleResults = new ArrayList<>();
        boolean includeInherited = arguments.getBoolean(ARG_INCLUDE_INHERITED);
        boolean showAllAppRelations = arguments.getBoolean(ARG_SHOW_ALL_APPS);
        List<String> applications = arguments.getStringList(ARG_APPLICATIONS);

        this.timezone = (TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE);
        this.locale = (Locale)arguments.get(JRParameter.REPORT_LOCALE);

        sort = applyGridGrouping(report, sort);

        /**
         * Pull either all roles and return as-is, or iterate over the roles
         * to find associated applications.
         */
        if (Util.isEmpty(applications) && !showAllAppRelations) {
            // We don't care about relation info, so simply pull all roles and
            // return the data as-is.
            Iterator<Bundle> roles = getRoles(applications, report, sort, groupBy);
            List<Bundle> bundles = new ArrayList<>();
            roles.forEachRemaining(bundles::add);

            estimatedSize = bundles.size();
            results = bundles.iterator();
        } else {
            // We need to pull all roles AND get application/relation info.
            //
            // First we will pull all rows from Bundle, filtering and sorting as necessary.
            //
            // Then, we will iterate over the roles, adding applications from direct roles and
            // traversing the required, permitted & inherited lists for indirect roles.
            IncrementalObjectIterator<Bundle> allBundles = getAllRoles(report, sort, groupBy);

            while (allBundles.hasNext()) {
                Bundle b = allBundles.next();
                // Map that holds applications associated to this bundle, and
                // its relation (required, permitted, etc.)
                Map<String, String> appResults = new HashMap<>();

                if (Util.isEmpty(b.getProfiles())) {
                    // This is an indirect bundle.
                    // Traverse the hierarchy to get application & relation info for required, permitted and
                    // (if applicable) inherited. If applications are specified, look specifically for those.
                    appResults.putAll(getIndirectApplications(b, applications, includeInherited));
                } else {
                    // This is a direct bundle.
                    // Add the applications directly tied to it. If requested, also retrieve
                    // inherited applications.
                    appResults.putAll(getProfileAppsWithRelation(getProfileApplications(b.getProfiles()), applications, MessageKeys.REPT_ROLE_BY_APP_DIRECT));

                    if (includeInherited) {
                        appResults = getIndirectApplications(b, applications, includeInherited, appResults);
                    }
                }

                // Only add to final results if we found a relationship (direct for roles with a profile,
                // required/permitted/inherited for roles without a profile) OR the option to return
                // applications for all roles was selected
                if (!appResults.isEmpty() || showAllAppRelations) {
                    if (!appResults.isEmpty()) {
                        appWithTypeBundles.put(b, appResults);
                    }
                    bundleResults.add(b);
                }
            }

            estimatedSize = bundleResults.size();
            results = bundleResults.iterator();
        }
    }

    @Override
    public Object getFieldValue(String field) throws GeneralException {
        if (currentBundle != null) {
            switch(field) {
                case COL_RELATION:
                    List<String> relations = new ArrayList<>();
                    if (appWithTypeBundles.containsKey(currentBundle)) {
                        Map<String, String> apps = appWithTypeBundles.get(currentBundle);
                        apps.forEach((appName, relation) -> {
                            relations.add(appName + " (" + getMessage(relation) + ")");
                        });
                        Collections.sort(relations);
                    }
                    return relations;
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
            log.error("Exception thrown while getting field value for Roles By Application Report. "
                    + "Exception [" + e.getMessage() + "].");

            throw new JRException(e);
        }
    }
}
