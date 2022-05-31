/*
 *  (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.server.upgrade;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.server.ImportExecutor;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;

/**
 * This upgrader will do the following: upgrade RecommenderDefinition name
 * upgrade Module name upgrade SystemConfiguration recommenderSelected entry
 * value upgrade WorkGroup name upgrade Capability name upgrade
 * ServiceDefinition name
 */
public class FAMUpgrader extends BaseUpgrader {

    private SailPointContext _context;
    private static final String _SecurityIQ = "SecurityIQ";
    private static final String _FAM = "File Access Manager";

    public void performUpgrade(ImportExecutor.Context context) throws GeneralException {
        _context = context.getContext();
        this.upgradeApplications();
    }

    /**
     * Convenience method used to update the application type
     * for all applications that are "SecurityIQ" type.
     * The new type should be "File Access Manager".
     */
    public void upgradeApplications() {
        if (_context == null) {
            warn("Invalid context, cannot update application type.");
            return;
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("type", _SecurityIQ));

        //we must clone results into memory if the session will commit
        //before fully iterating the results
        ops.setCloneResults(true);

        try {
            IncrementalObjectIterator<Application> apps =
                    new IncrementalObjectIterator<>(_context, Application.class, ops);

            if (apps != null) {
                int count = 0;
                int total = _context.countObjects(Application.class, ops);

                while (apps.hasNext()) {
                    Application app = apps.next();
                    app.setType(_FAM);
                    count++;

                    info("Updating type \"" + _SecurityIQ + "\" to \"" + _FAM +"\" for application \"" +
                            app.getName() + "\"");

                    _context.saveObject(app);
                    _context.commitTransaction();

                    if (count % 20 == 0 || count == total) {
                        _context.decache();
                    }
                }

                if (count > 0 ) {
                    info("Successfully updated " + count + " applications.");
                } else {
                    info("There is no need to update any application.");
                }
            }
        } catch (GeneralException e) {
            warn("There was an unexpected error while updating the application types.", e);
        }
    }
}