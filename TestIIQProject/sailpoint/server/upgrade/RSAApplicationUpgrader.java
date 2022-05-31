/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server.upgrade;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.tools.Util;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;

/**
 * Upgrader for performing upgrade on "RSA" applications.
 *
 */
public class RSAApplicationUpgrader extends BaseUpgrader {

    private SailPointContext spContext;

    @Override
    public void performUpgrade(Context context) throws GeneralException {
 
        spContext = context.getContext();

        if (null != spContext) {
            // The IncrementalObjectIterator wraps a GeneralException inside a
            // RuntimeException before throwing it upwards, at 2 places.
            // 1. In its constructor which fires a search query using the
            // context.
            // 2. In its next method, when individual objects are fetched via
            // getObjectBytId method, using the context.

            // After connectors library migration there is no need of custom
            // class-path for RSA so, remove all class-path configuration
            // as those are redundant
            upgradeRSAApplications(getIteratorOverRSAApplications());
        }
    }

    /**
     * This method prepares a query to search for RSA applications
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
    private IncrementalObjectIterator<Application> getIteratorOverRSAApplications() {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addFilter(Filter.eq("type", "RSA Authentication Manager - Direct"));

        return new IncrementalObjectIterator<Application>(spContext,
                Application.class, queryOptions);
    }

    /**
     * This method will actually modify the RSA applications
     * and also save the application to the database.
     * 
     * @param applicationIterator
     *            The iterator over the applications.
     * @throws GeneralException
     *             The argument iterator's next method can also throw a
     *             {@link RuntimeException} wrapped inside a
     *             {@link GeneralException}
     */
    private void upgradeRSAApplications(
            IncrementalObjectIterator<Application> applicationIterator)
            throws GeneralException {

        if (Util.isEmpty(applicationIterator)) {
            log("No applications found to upgrade.");
            return;
        } else {

            int numberOfAppsProcessed = 0;

            while (applicationIterator.hasNext()) {
                Application application = applicationIterator.next();
                numberOfAppsProcessed++;

                if (null != application) {
                    application.removeAttribute("connector-classpath");
                    spContext.saveObject(application);
                    log("connector-classpath entry removed for application: " + application.getName() + ".");
                }

                // Commit and decache after every 20 applications or if we
                // exhaust all applications.
                if ((applicationIterator.getCount() % 20 == 0)
                        || (applicationIterator.getCount() == applicationIterator.getSize())) {
                    spContext.commitTransaction();
                    spContext.decache();
                }

                reportProgress(numberOfAppsProcessed, applicationIterator.getSize());
            }

            log("Total number of applications successfully upgraded = " + numberOfAppsProcessed + ".");
        }
    }
}