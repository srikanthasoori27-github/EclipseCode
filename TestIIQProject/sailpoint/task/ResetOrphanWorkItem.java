package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.LockInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Step;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class ResetOrphanWorkItem extends AbstractTaskExecutor {

    private static final String ARG_WF_NAMES_TO_RESET = "resetWorkflows";
    private static final String ARG_MIN_LOCK_AGE = "minLockAge";
    private static final String RET_EVENTS_DELETED = "eventsDeleted";
    private static final String RET_EVENTS_RESET = "eventsReset";
    private static final int DEFAULT_MIN_LOCK_AGE = 7;
    private SailPointContext _context;
    private int _daysExpired;
    private int _minLockAgeDays;
    private List<String> _wfToReset;
    private Terminator _terminator;
    private int _decacheEvery = 10;
    private int _count = 0;
    private int _prunedCount = 0;
    private int _resetCount = 0;
    private boolean _terminate;
    private TaskResult _result;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result,
            Attributes<String, Object> args) throws Exception {

        _context = context;
        _result = result;
        _terminator = new Terminator(_context);
        init (args);

        
        // Build the list of WorkItem ids
        List<String> ids = buildListToReset();

        // process the ids
        resetEvents(ids);

        result.addAttribute(RET_EVENTS_DELETED, _prunedCount);
        result.addAttribute(RET_EVENTS_RESET, _resetCount);
    }

    private void init (Attributes<String, Object> args) throws GeneralException {
        _wfToReset = Util.csvToList(args.getString(ARG_WF_NAMES_TO_RESET));
        if (_wfToReset == null) {
            _wfToReset = new ArrayList<String>();
        }
        _minLockAgeDays = args.getInt(ARG_MIN_LOCK_AGE, DEFAULT_MIN_LOCK_AGE);
        if (_minLockAgeDays < 0) {
            _result.addMessage(Message.warn(MessageKeys.TASK_OUT_RESET_ORPHAN_WI_INVALID_MIN_LOCK_AGE, _minLockAgeDays, DEFAULT_MIN_LOCK_AGE));
            _minLockAgeDays = DEFAULT_MIN_LOCK_AGE;
        }
    }

    private void resetEvents (List<String> ids) throws GeneralException {
        // based on Workflow name, either clear the lock or delete the WI
        // If we can find the associated WFC, sniff it for the WF name
        // and if it's in our list, clear the lock. If any of that doesn't
        // pan out, purge the WI
        for (String eventId : Util.iterate(ids)) {
            if (_terminate) {
                _result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                return;
            }
            boolean reset = false;
            WorkItem event = _context.getObjectById(WorkItem.class, eventId);
            // why should it, but just in case
            if (event == null) {
                continue;
            }
            List<String> workflowNames = findWorkflowNames (event);
            for (String wfName : workflowNames) {
                if (_wfToReset.contains(wfName)) {
                    reset = true;
                    break;
                }
            }
            resetEvent (event, reset);
        }
        // And one for the road...
        _context.commitTransaction();
    }

    private List<String> findWorkflowNames (WorkItem event) {
        List<String> wfNames = new ArrayList<String>();
        // from the WFC case of the event, investigate the current step and walk
        // the tree of workflow steps to determine the stack of wf names
        WorkflowCase wfc = event.getWorkflowCase();
        addCurrentWorkflow (wfc, wfNames);
        return wfNames;
    }

    private void addCurrentWorkflow (WorkflowCase wfc, List<String> workflowNames) {
        if (wfc == null) {
            // Nothing to do
            return;
        }
        Workflow currentWf = wfc.getWorkflow();
        if (currentWf != null) {
            workflowNames.add(currentWf.getName());
            String stepId = currentWf.getCurrentStep();
            Step currentStep = currentWf.findStepById(stepId);
            List<WorkflowCase> wfCases = currentStep.getWorkflowCases();
            for (WorkflowCase subWfc : Util.safeIterable(wfCases)) {
                addCurrentWorkflow(subWfc, workflowNames);
            }
        }
    }
    private void resetEvent (WorkItem event, boolean reset) throws GeneralException {
        // if reset, just clean the lock off. Otherwise purge it
        _count++;
        if (reset) {
            event.setLock(null);
            _context.saveObject(event);
            _resetCount++;
        } else {
            // prune
            // If there's a WFC, send that into the Terminator. Otherwise get the WI
            WorkflowCase wfc = event.getWorkflowCase();
            if (wfc != null) {
                _terminator.deleteObject(wfc);
            } else {
                _terminator.deleteObject(event);
            }
            _prunedCount++;
        }
        _context.commitTransaction();
        if (_count % _decacheEvery == 0) {
            _context.commitTransaction();
            _context.decache();
        }
    }

    private List<String> buildListToReset () throws GeneralException {
        QueryOptions opts = buildQueryOptions();
        String properties = "id, lock";
        Iterator<Object[]> results = _context.search(WorkItem.class, opts, properties);
        long maxLockAgeMS = System.currentTimeMillis() - (_minLockAgeDays * 86400000);
        Date maxLockAge = new Date (maxLockAgeMS);
        List<String> idsToReset = new ArrayList<String>();

        while (results.hasNext()) {
            Object[] data = results.next();
            String id = (String)data[0];
            LockInfo lock = new LockInfo((String)data[1]);
            Date lockExpiration = lock.getExpiration();
            if (lockExpiration.before(maxLockAge)) {
                idsToReset.add(id);
            }
        }
        return idsToReset;
    }

    private QueryOptions buildQueryOptions () {
        QueryOptions opts = new QueryOptions();
        opts.add(Filter.eq ("type", "Event"));
        opts.add(Filter.not(Filter.isnull("lock")));
        long msExpired = _daysExpired * 86400000;
        long expired = System.currentTimeMillis() - msExpired;
        opts.add(Filter.or(Filter.isnull("expiration"),
                           Filter.le("expiration", new Date (expired))));
        opts.setCloneResults(true);

        return opts;
    }

    @Override
    public boolean terminate() {
        _terminate = true;
        return _terminate;
    }

}
