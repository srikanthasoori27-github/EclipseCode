/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that carefully deletes all group or identity indexes.
 * This is a debugging hack necessary because there is a reference
 * from the Identity/GroupDefinition to the most recent 
 * Scorecard/GroupIndex, and deleting them in the console causes
 * a foreign key violation in Hibernate.  
 *
 * This needs to be generalzed to do other things thin out
 * selected indexes after a granule change.
 * 
 * Author: Jeff
 *
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Grouper;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScoreKeeper;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

public class IndexCleaner extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(IndexCleaner.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Causes removal of all group indexes.
     */
    public static final String ARG_DELETE_GROUP_INDEXES = 
    "deleteGroupIndexes";

    /**
     * Causes group indexes to be pruned based on the
     * current granule size.
     */
    public static final String ARG_PRUNE_GROUP_INDEXES = 
    "pruneGroupIndexes";
    
    /**
     * Causes removal of all identity indexes.
     */
    public static final String ARG_DELETE_IDENTITY_INDEXES = 
    "deleteIdentityIndexes";

    /**
     * Causes identity indexes to be pruned based on the
     * current granule size.
     */
    public static final String ARG_PRUNE_IDENTITY_INDEXES = 
    "pruneIdentityIndexes";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Arguments from the caller
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    private SailPointContext _context;

    boolean _trace;

    /**
     * Enables profiling messages.
     * This is only used during debugging, it can't be set
     * from the outside.
     */
    private boolean _profile;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime state
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flag set in another thread to halt the execution of
     * the refreshIdentityScores() method.
     */
    boolean _terminate;

    Grouper _grouper;
    ScoreKeeper _scoreKeeper;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public IndexCleaner() {
    }

    public boolean terminate() {
        _terminate = true;
        // we may be down in the Grouper, tell it to stop too
        if (_grouper != null) _grouper.setTerminate(true);
        if (_scoreKeeper != null) _scoreKeeper.setTerminate(true);
        return true;
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            System.out.println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        if (_trace)
            System.out.println(msg);
    }

    public void execute(SailPointContext context, 
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        // since this is a debug util used from the console, 
        // always enable trace
        //_trace = args.getBoolean(ARG_TRACE);
        _trace = true;

        boolean deleteGroup = args.getBoolean(ARG_DELETE_GROUP_INDEXES);
        boolean pruneGroup = args.getBoolean(ARG_PRUNE_GROUP_INDEXES);
        boolean deleteIdentity = args.getBoolean(ARG_DELETE_IDENTITY_INDEXES);
        boolean pruneIdentity = args.getBoolean(ARG_PRUNE_IDENTITY_INDEXES);

        _grouper = new Grouper(context, args);
        _grouper.setTrace(_trace);

        _scoreKeeper = new ScoreKeeper(context, args);
        _scoreKeeper.setTrace(_trace);

        if (deleteGroup) {
            _grouper.deleteGroupIndexes();

        }
        else if (pruneGroup) {
            log.error("Group index pruning not implemented!");
        }
        
        if (deleteIdentity) {
            _scoreKeeper.deleteIdentityIndexes();
        }
        else if (pruneIdentity) {
            log.error("Identity index pruning not implemented!");
        }
        
        result.setTerminated(_terminate);
    }

}
