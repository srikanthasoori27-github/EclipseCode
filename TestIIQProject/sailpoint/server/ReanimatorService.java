
package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.DatabaseVersion;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class ReanimatorService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ReanimatorService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Reanimator";


    /**
     * True once we've been started at least once.
     */
    boolean _initialized;

    /**
     * True if last check determined the database is currently failing/not available
     */
    boolean _databaseFailureOngoing;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ReanimatorService() {
        _name = NAME;
    }


    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext context) throws GeneralException {

    }

    /**
     * Do that thing you do.  This is where periodic services do their work.
     * Continuous services typically ignore this.
     */
    public void execute(SailPointContext context) throws GeneralException {

        String thisHost = Util.getHostName();

        // confirm that the database is available
        boolean isDatabaseOk = isDatabaseAvailable(context);
        if (!isDatabaseOk) {
            _databaseFailureOngoing = true;

            // There is nothing we can do right now.  So we will return, and
            // hope that next time around, there is a database avail to work with.

            if (log.isWarnEnabled()) {
                log.warn("On host " + thisHost + ", unable to access the database. Skipping current execution.");
            }

            return;
        }

        if (_databaseFailureOngoing) {
            _databaseFailureOngoing = false;

            if (log.isWarnEnabled()) {
                log.warn("On host " + thisHost + ", database availability has returned.");
            }
        }

        //////////////////////////////
        // Reset zombie Requests
        //////////////////////////////

        Set<String> zombieRequestIDs = findZombieRequestIDs(context);
        if (!Util.isEmpty(zombieRequestIDs)) {

            if (log.isWarnEnabled()) {
                log.warn("On host " + thisHost + ", the following unexpectedly dead Requests are being reset : " + zombieRequestIDs);
            }
            RequestManager rm = new RequestManager(context);
            rm.resetRequests(zombieRequestIDs);
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("On host " + thisHost + ", there were no dead Requests found.");
            }
        }

        /////////////////////////////////
        // terminate uunpartitioned
        // zombie taskresults
        /////////////////////////////////

        Set<String> zombieTaskResultIDs = findZombieTaskResultIDs(context);
        if (!Util.isEmpty(zombieTaskResultIDs)) {

            if (log.isWarnEnabled()) {
                log.warn("On host " + thisHost + ", the following unexpectedly dead unpartitioned TaskResults are being marked as terminated : " + zombieTaskResultIDs);
            }
            TaskManager tm = new TaskManager(context);
            tm.terminateTasks(zombieTaskResultIDs);
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("On host " + thisHost + ", there were no dead unpartitioned TaskResult objects found.");
            }
        }

    }

    /**
     * @return true if the context can be used to successfully perform a
     * simple query from the database
     */
    private boolean isDatabaseAvailable(SailPointContext context) {
        boolean isAvail = false;
        try {
            DatabaseVersion dbv = context.getObjectByName(DatabaseVersion.class, "main");
            isAvail = true;
        }
        catch (Exception e) {
            isAvail = false;
        }

        return isAvail;
    }

    /**
     * Return the IDs of the zombie Requests that are zombies for more than an instant.
     * A zombie Request is a request that is marked
     * in the database as if it is executing on this host, yet it is actually not executing.
     * @param context
     * @return the set of IDs of zombie requests.  An empty set will be returned if there
     * are no zombies.
     * @throws GeneralException
     */
    private Set<String> findZombieRequestIDs(SailPointContext context) throws GeneralException {
        final long WAIT_MS = 1000L; // 1 second

        // find zombies
        Set<String> zombieIDs = do_findZombieRequestIDs(context);
        if (zombieIDs.size() > 0) {
            // do again after waiting for WAIT_MS milliseconds, and only
            // return the zombies that appear in both
            try { Thread.sleep(WAIT_MS); } catch (InterruptedException e) { }
            Set<String> zombieIDs_2 = do_findZombieRequestIDs(context);
            zombieIDs.retainAll(zombieIDs_2);
        }

        return zombieIDs;
    }

    /**
     * Return the IDs of the current zombie Requests.  A zombie Request is a request that is marked
     * in the database as if it is executing on this host, yet it is actually not executing.
     * @param context
     * @return the set of IDs of zombie requests.  An empty set will be returned if there
     * are no zombies.
     * @throws GeneralException
     */

    private Set<String> do_findZombieRequestIDs(SailPointContext context) throws GeneralException {

        String thisHost = Util.getHostName();
        Set<String> activeRequestIDs = findActiveRequestIDs(context, thisHost);

        if (log.isDebugEnabled()) {
            log.debug("On host " + thisHost + ", expect the following Request ids to be alive: " + activeRequestIDs);
        }

        Set<String> zombieIDs = ExecutorTracker.findExecutorlessIDs(activeRequestIDs);
        return zombieIDs;
    }

    private Set<String> findZombieTaskResultIDs(SailPointContext context) throws GeneralException {
        final long WAIT_MS = 1000L; // 1 second

        // find zombies
        Set<String> zombieIDs = do_findZombieTaskResultIDs(context);
        if (zombieIDs.size() > 0) {
            // do again after waiting for WAIT_MS milliseconds, and only
            // return the zombies that appear in both
            try { Thread.sleep(WAIT_MS); } catch (InterruptedException e) { }
            Set<String> zombieIDs_2 = do_findZombieTaskResultIDs(context);
            zombieIDs.retainAll(zombieIDs_2);
        }

        return zombieIDs;
    }
    /**
     * Return the IDs of the zombie TaskResults.  A zombie TaskResult is an unpartitioned
     * task result that is marked in the database as if it is executing on this host,
     * yet it is actually not executing.
     * @param context
     * @return the set of IDs of zombie TaskResults.  An empty set will be returned if there
     * are no zombies.
     * @throws GeneralException
     */
    private Set<String> do_findZombieTaskResultIDs(SailPointContext context) throws GeneralException {

        String thisHost = Util.getHostName();
        Set<String> activeUnpartitionedTaskResultIDs = findActiveUnpartitionedTaskResultIDs(context, thisHost);

        if (log.isDebugEnabled()) {
            log.debug("On host " + thisHost + ", expect the following TaskResult ids to be alive: " + activeUnpartitionedTaskResultIDs);
        }

        Set<String> zombieIDs = ExecutorTracker.findExecutorlessIDs(activeUnpartitionedTaskResultIDs);
        return zombieIDs;
    }

    /**
     * Return the IDs of the Request objects that are supposedly running on the given hostName,
     * according to the database.  Note that just because this method returns a particular
     * Request id, we don't know yet that is REALLY running currently.
     * @param context
     * @param hostName the host to search for assigned Requests
     * @return the set of IDs of Request objects declared to be running on hostName, an
     * empty set if none.
     * @throws GeneralException
     */
    private Set<String> findActiveRequestIDs(SailPointContext context, String hostName) throws GeneralException {


        // Find the uncompleted requests launched on this host and are not rescheduled to run later
        Filter queryFilter =
            Filter.and(
                Filter.eq("host", hostName),
                Filter.notnull("launched"),
                Filter.eq("live", true),
                Filter.or(Filter.isnull("completed"),
                        Filter.eq("completed", "")
                ),
                Filter.or(Filter.isnull("nextLaunch"),
                        Filter.eq("nextLaunch", "")
                )
            );

        QueryOptions ops = new QueryOptions();
        ops.add(queryFilter);

        List<String> props = new ArrayList<String>();
        props.add("id");

        // Fully fetch the id list so we can rollback without
        // losing the cursor
        Set<String> ids = new HashSet<>();
        Iterator<Object[]> rows = context.search(Request.class, ops, props);
        while (rows.hasNext()) {
            Object[] row = rows.next();
            ids.add((String)row[0]);
        }

        return ids;
    }

    /**
     * Return the IDs of the unpartitioned TaskResult objects that are supposedly running
     * on the given hostName, according to the database.  Note that just because this method
     * returns a particular TaskResult id, we don't know yet that is REALLY running currently.
     * @param context
     * @param hostName the host to search for assigned Requests
     * @return the set of IDs of unpartitioned TaskResult objects declared to be running on hostName, an
     * empty set if none.
     * @throws GeneralException
     */
    private Set<String> findActiveUnpartitionedTaskResultIDs(SailPointContext context, String hostName) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        List<Filter> filterList = new ArrayList<Filter>();
        filterList.add(Filter.eq("host", hostName));
        filterList.add(Filter.isnull("completed"));
        filterList.add(Filter.eq("partitioned", false));
        filterList.add(Filter.notnull("launched"));
        filterList.add(Filter.eq("live", true));
        ops.add(Filter.and(filterList));

        List<String> props = new ArrayList<String>();
        props.add("id");

        // Fully fetch the id list so we can rollback without
        // losing the cursor
        Set<String> ids = new HashSet<>();
        Iterator<Object[]> rows = context.search(TaskResult.class, ops, props);
        while (rows.hasNext()) {
            Object[] row = rows.next();
            ids.add((String)row[0]);
        }

        return ids;
    }

}
