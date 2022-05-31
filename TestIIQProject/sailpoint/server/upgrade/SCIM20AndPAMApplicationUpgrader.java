/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.tools.Util;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;

/**
 * Upgrader for performing upgrade on "SCIM 2.0" and "Privileged Account
 * Management (PAM)"applications.
 *
 * @author Chandrashekhar.Gaikwad
 *
 */
public class SCIM20AndPAMApplicationUpgrader extends BaseUpgrader {
    private static final String CONNECTOR_CLASS = "sailpoint.connector.OpenConnectorAdapter";
    private static final String CONNECTOR_CLASS_SCIM20 = "openconnector.connector.scim2.SCIM2Connector";
    private static final String JSON_JAR = "json.jar";
    private static final String LOAD_BY_SYSCLASSLOADER = "load-by-sysclassloader";
    private static final int COMMIT_SIZE = 20;
    private SailPointContext spContext;

    /**
     * The IncrementalObjectIterator wraps a GeneralException inside a RuntimeException before throwing it upwards,
     * at 2 places.
     * 1. In its constructor which fires a search query using the context.
     * 2. In its next method, when individual objects are fetched via getObjectBytId method, using the context.
     * @param context The import context.
     * @throws {@link GeneralException}
     */
    @Override
    public void performUpgrade(Context context) throws GeneralException {
        spContext = context.getContext();
        if (null != spContext) {
            upgradeSCIM20AndPAMApplications(getIteratorOverSCIM20AndPAMApplications());
        }
    }

    /**
     * This method prepares a query to search for SCIM2.0 and PAM applications
     * and returns an an instance of {@link IncrementalObjectIterator} over the
     * application objects.
     *
     * <p>
     * The newly constructed iterator's {@link IncrementalObjectIterator#next()}
     * method can also throw a {@link RuntimeException} wrapped inside a
     * {@link GeneralException}
     * </p>
     *
     * @return An instance of {@link IncrementalObjectIterator}
     */
    private IncrementalObjectIterator<Application> getIteratorOverSCIM20AndPAMApplications() {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addFilter(Filter.eq("connector", CONNECTOR_CLASS));
        return new IncrementalObjectIterator<>(spContext, Application.class, queryOptions);
    }

    /**
     * This method will actually modify the SCIM2.0 and PAM applications if
     * required and also save the application to the database.
     *
     * @param applicationIterator
     *            The iterator over the applications.
     * @throws GeneralException
     *             The argument iterator's next method can also throw a
     *             {@link RuntimeException} wrapped inside a
     *             {@link GeneralException}
     */
    private void upgradeSCIM20AndPAMApplications(IncrementalObjectIterator<Application> applicationIterator)
            throws GeneralException {
        if (applicationIterator == null || applicationIterator.getSize() == 0) {
            log("No applications found to upgrade.");
            return;
        }
        int numberOfAppsProcessed = 0;
        int totalAppsUpgraded = 0;
        List<String> skipJarsClassloader = new ArrayList<>();
        skipJarsClassloader.add(JSON_JAR);
        while (applicationIterator.hasNext()) {
            Application application = applicationIterator.next();
            numberOfAppsProcessed++;
            if (application != null) {
                String appDetailsForLogging = "Application name = " + application.getName() + ". Application type = "
                        + application.getType();
                Map<String, Object> appConfigAttributes = application.getAttributes();
                if (isUpgradeRequired(appConfigAttributes)) {
                    log("Attempting to upgrade json.jar entry for SCIM2.0 Or PAM application: " + appDetailsForLogging);
                    application.setAttribute(LOAD_BY_SYSCLASSLOADER, skipJarsClassloader);
                    spContext.saveObject(application);
                    totalAppsUpgraded++;
                    log("Added entry load-by-sysclassloader for application: " + appDetailsForLogging);
                } else if (isScim2AppLoadedBySysClassLoader(appConfigAttributes)){
                    log("Skipping SCIM2.0 or PAM application from upgrade as it has "
                            + LOAD_BY_SYSCLASSLOADER + " attribute set. " + appDetailsForLogging);
                }
            }
            if (isCommitRequired(applicationIterator)) {
                spContext.commitTransaction();
                spContext.decache();
            }
            reportProgress(numberOfAppsProcessed, applicationIterator.getSize());
        }
        log("Total number of applications considered for upgrade = " + numberOfAppsProcessed
                + ". Total number of applications successfully upgraded = " + totalAppsUpgraded
                + ". Total number of applications skipped from upgrade = " + (numberOfAppsProcessed - totalAppsUpgraded));
    }

    /**
     * Commit and de-cache after every 20 applications or if we exhaust all applications.
     * @param applicationIterator - iterator that has applications for upgrade.
     * @return true if number of upgraded applications reached commit size,
     * or number of upgraded applications equals the amount of applications being processed.
     */
    private boolean isCommitRequired(IncrementalObjectIterator<Application> applicationIterator) {
        return (applicationIterator.getCount() % COMMIT_SIZE == 0)
                || (applicationIterator.getCount() == applicationIterator.getSize());
    }

    /**
     * Filter applications that were not loaded by sysclassloader and have connectorClass attribute set to SCIM2Connector.
     * @param appConfigAttributes - map with application's config attributes
     * @return true if there is connectorClass attribute set to SCIM2Connector and "load-by-sysclassloader" key is not set.
     */
    private boolean isUpgradeRequired(Map<String, Object> appConfigAttributes) {
        return !Util.isEmpty(appConfigAttributes)
                && !appConfigAttributes.containsKey(LOAD_BY_SYSCLASSLOADER)
                && appConfigAttributes.get("connectorClass").toString().equalsIgnoreCase(CONNECTOR_CLASS_SCIM20);
    }

    /**
     * Filter applications that: were loaded by sysclassloader and have connectorClass attribute set to SCIM2Connector.
     * @param appConfigAttributes - map with application's config attributes
     * @return true if there is connectorClass attribute set to SCIM2Connector and "load-by-sysclassloader" key is set.
     */
    private boolean isScim2AppLoadedBySysClassLoader(Map<String, Object> appConfigAttributes) {
        return !Util.isEmpty(appConfigAttributes)
                && appConfigAttributes.containsKey(LOAD_BY_SYSCLASSLOADER)
                && appConfigAttributes.get("connectorClass").toString().equalsIgnoreCase(CONNECTOR_CLASS_SCIM20);
    }
}