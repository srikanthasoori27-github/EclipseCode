/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Evolving test harness for automating workflow tests.
 * 
 * Author: Jeff
 * 
 *
 * There are two parts to this capturing the work item interactions
 * of a workflow case as it is advanced in the UI, and replaying
 * previously captured interactions to automate workflow testing.
 * 
 * This class will be created in one of two ways:
 *
 *    - by Workflower to perform a capture when the workflow is
 *      launched normally
 *
 *    - by the wftest command when a previous capture is replayed
 *
 * The distinction is necessary because capture behaves differently
 * in the two situations.
 * 
 */

package sailpoint.workflow;

import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.Workflower;
import sailpoint.object.ApprovalSet;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Comment;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Form.Button;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkflowTestSuite;
import sailpoint.object.WorkflowTestSuite.WorkflowTest;
import sailpoint.object.WorkflowTestSuite.WorkItemResponse;
import sailpoint.object.WorkflowTestSuite.WorkItemAction;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class WorkflowTestHarness {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    SailPointContext _context;
    Workflower _workflower;
    PrintWriter _out;
    
    /**
     * The suite we are replaying.
     */
    WorkflowTestSuite _replay;

    /**
     * The test we are capturing.
     */
    WorkflowTestSuite _capture;

    /**
     * A list of WorkItems that were opened during workflow capture.
     */
    List<WorkItem> _items;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Constructor used by Workflower.
     */
    public WorkflowTestHarness(SailPointContext con, Workflower wf) {
        _context = con;
        _workflower = wf;
    }

    /**
     * Constructor used by the console.
     */
    public WorkflowTestHarness(SailPointContext con, PrintWriter out) {
        _context = con;
        _out = out;
    }
    
    private void println(Object o) {
        if (_out != null) {
            _out.print(o);
            _out.println();
        }
        else {
            System.out.println(o);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Capture
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by Workflower when it is ready to start.
     */
    public void startCapture(WorkflowLaunch wflaunch) {

        _items = null;

        if (_replay != null) {
            // we are replaying a previous capture, don't need to capture again
        }
        else {
            WorkflowTest test = new WorkflowTest();
            test.captureWorkflowLaunch(_context, wflaunch);
            _capture = new WorkflowTestSuite();
            _capture.add(test);
        }
    }

    /**
     * Called by Workflower when it opens a new work item.
     */
    public void addWorkItem(WorkItem item) {
        if (item != null) {
            if (_items == null)
                _items = new ArrayList<WorkItem>();
            _items.add(item);
        }
    }

    /**
     * Save the workflow capture when the case ends or suspends.
     * WorkflowCase is only null if we died early in variable initialization.
     */
    public void saveCapture(WorkflowCase wfcase) throws GeneralException {

        // only do this if we're not replaying
        if (_capture != null) {
            boolean doCapture = false;
            String captureFile = null;
            String captureObject = null;

            // TODO: If we launched the case from a capture file, should
            // we always save back there or save to a db object?  I like
            // having capture files to get things started but then capture
            // to the db so you have control over whether to replace the file
            // or use it again.  This seems the most useful.
            // Could have a special command line option or variable to force
            // it back to the file.
            //captureFile = _capture.getFile();

            if (wfcase != null) {
                // made it past launch variable initialization
                Workflow wf = wfcase.getWorkflow();
                doCapture = wf.getBoolean(Workflow.VAR_CAPTURE);
                String altFile = wf.getString(Workflow.VAR_CAPTURE_FILE);
                if (altFile != null)
                    captureFile = altFile;
                captureObject = wf.getString(Workflow.VAR_CAPTURE_OBJECT);
            }
            else {
                // failed very early, these are normally not going to 
                // be in the initial launch args, but allow it
                WorkflowTest test = _capture.getPrimaryTest();
                WorkflowLaunch starting = test.getWorkflowLaunch();
                doCapture = Util.otob(starting.get(Workflow.VAR_CAPTURE));
                String altFile = Util.otoa(starting.get(Workflow.VAR_CAPTURE_FILE));
                if (altFile != null)
                    captureFile = altFile;
                captureObject = Util.otoa(starting.get(Workflow.VAR_CAPTURE_OBJECT));
            }

            if (doCapture) {

                if (wfcase != null)
                    saveStubResponses(wfcase);

                if (captureFile != null) {
                    println("Capturing WorkflowTestSuite to file: " + captureFile);
                    String xml = _capture.toXml();
                    Util.writeFile(captureFile, xml);
                }
                else {
                    // capture name will be non-null if we resumed, null if we just launched
                    if (_capture.getName() == null) {
                        // this can be used to specify a fixed name
                        if (captureObject != null) {
                            _capture.setName(captureObject);
                        }
                        else if (wfcase != null && wfcase.getName() != null) {
                            // we default to the case name  
                            _capture.setName(wfcase.getName());
                        }
                        else {
                            // didn't get far enough to have a case name
                            _capture.setName("Anonymous");

                        }
                    }

                    // decache first so we make sure we don't commit garbage
                    _context.decache();
                    if (_capture.getId() == null) {
                        // this is a new one, delete the previous one
                        WorkflowTestSuite existing = _context.getObjectByName(WorkflowTestSuite.class, _capture.getName());
                        if (existing != null) {
                            println("Replacing WorkflowTestSuite: " + _capture.getName());
                            _context.removeObject(existing);
                            _context.commitTransaction();
                        }
                        else {
                            println("Creating WorkflowTestSuite: " + _capture.getName());
                        }
                    }
                    else {
                        println("Replacing WorkflowTestSuite: " + _capture.getName());
                    }

                    _context.saveObject(_capture);
                    _context.commitTransaction();
                }
            }
        }
    }

    /**
     * Convert WorkItems captured durning the last case advance into
     * WorkItemResponses in the captured test.  Call this only if
     * catpure is enabled.
     *
     * If we find any incomplete stubbed responses, remove them since
     * we advanced past them.
     */
    private void saveStubResponses(WorkflowCase wfcase) {

        WorkflowTest test = _capture.getPrimaryTest();

        // TODO: STATUS_APPROVED is true only if we didn't open a backgrounding
        // item.  Need to figure out what to do there.
        if (!wfcase.isComplete()) {
            if (Util.size(_items) == 0) {
                // didn't create any new items, this can happen
                // if we've still got parallel approvals outstanding
            }
            else {
                // once we advance to the point where a new items is created
                test.clearStubResponses();

                // create new stub responses
                for (WorkItem item : _items) {
                    WorkItemResponse response = new WorkItemResponse();
                    test.add(response);

                    response.setWorkItemId(item.getId());
                    // don't need this but it helps
                    Identity owner = item.getOwner();
                    if (owner != null)
                        response.setOwner(owner.getName());
                    
                    Form form = item.getForm();
                    if (form != null) {
                        Iterator<Field> fi = form.iterateFields();
                        while (fi.hasNext()) {
                            Field field = fi.next();
                            // only fields with names, others are informational
                            if (field.getName() != null) {
                                WorkItemAction action = new WorkItemAction(field);
                                response.add(action);
                            }
                        }

                        // TODO: What about buttons?  Assuming
                        // response.state is enough to handle the posted
                        // button action, if we find buttons with
                        // parameters add variable actions.  If the
                        // Button has a parameter we coud stub that out
                        // but several buttons might set the same parameter!!
                        for (Button button : Util.iterate(form.getButtons())) {
                            if (button.getParameter() != null) {
                                WorkItemAction action = new WorkItemAction();
                                action.setVariable(button.getParameter());
                                response.add(action);
                            }
                        }
                    }

                    // you normally do not have both a form and a set
                    ApprovalSet aset = item.getApprovalSet();
                    if (aset != null) {
                        for (ApprovalItem appitem : Util.iterate(aset.getItems())) {
                            WorkItemAction action = new WorkItemAction(appitem);
                            response.add(action);
                        }
                    }

                    // TODO: stubbing out variable actions is harder because we
                    // are guided by what is declared as returns in the Approval
                    // which we don't have access to at this point
                }

                // reset for the next advance
                _items = null;
            }
        }
        else {
            // If we reach normal completion this list should be empty
            // if we failed there might be things here if we had several
            // items to open and it failed after the first one.  I suppose
            // we could create stubs just to show what we had but you're not
            // going to be able to replay until you fix this problem
            if (Util.size(_items) > 0) {
                println("Capture is ignoring work items opened before case failure");
                _items = null;
            }
            test.clearStubResponses();
        }
    }

    /**
     * Capture a work item that is about to be assimilated.
     * This is only relevant for a new capture.  If we're still
     * replaying we don't recapture the item, we already have
     * the response.
     */
    public void captureWorkItem(WorkflowCase wfcase, WorkItem item)
        throws GeneralException {

        if (_replay == null) {

            Workflow wf = wfcase.getWorkflow();
            boolean doCapture = wf.getBoolean(Workflow.VAR_CAPTURE);
            String captureFile = wf.getString(Workflow.VAR_CAPTURE_FILE);
            String captureObject = wf.getString(Workflow.VAR_CAPTURE_OBJECT);

            if (doCapture) {
                // there should be a stubbed response for this item
                WorkflowTestSuite suite;
                if (captureFile != null) {
                    println("Resuming WorkflowTestSuite capture from file: " + captureFile);
                    String xml = Util.readFile(captureFile);
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    suite = (WorkflowTestSuite)f.parseXml(_context, xml, false);
                }
                else {
                    if (captureObject == null)
                        captureObject = wfcase.getName();
                    suite = _context.getObjectByName(WorkflowTestSuite.class, captureObject);
                }

                if (suite == null) {
                    println("ERROR: Unable to restore previous workflow capture");
                }
                else {
                    WorkflowTest test = suite.getPrimaryTest();
                    WorkItemResponse response = test.removeStubResponse(item.getId());
                    if (response == null) {
                        // should have generated a stub before now, if not make it now
                        response = new WorkItemResponse();
                    }

                    saveWorkItemActions(item, response);

                    // insert the response back into the test after the previous responses
                    // but before the other stubs
                    test.addFinishedResponse(response);

                    // save this here so when Workflower advance eventually calls
                    // saveCapture we'll get the next set of stub items
                    _capture = suite;
                }
            }
        }
    }

    /**
     * Save a completed work item into a response.
     * There is similarity between this and saveStubResponses, see if we can
     * refactor part of the core.
     */
    private void saveWorkItemActions(WorkItem item, WorkItemResponse response) {

        // ignore previous stub actions
        response.setActions(null);

        // make this not a stub
        response.setWorkItemId(null);

        // don't require this but it's nice for documentation
        // can also use this to sanity check the workflow definition
        // against the capture
        Identity owner = item.getOwner();
        if (owner != null)
            response.setOwner(owner.getName());

        // copy over the Form
        Form form = item.getForm();
        if (form != null) {
            Iterator<Field> fi = form.iterateFields();
            while (fi.hasNext()) {
                Field field = fi.next();
                // only fields with names, others are informational
                if (field.getName() != null) {
                    WorkItemAction action = new WorkItemAction(field);
                    response.add(action);
                }
            }

            // TODO: What about buttons?  Assuming
            // response.state is enough to handle the posted
            // button action, if we find buttons with
            // parameters add variable actions.
            if (form.getButtons() != null) {
                // can have more than one button with the same
                // parameter name so only make an action for one
                Map<String,Button> buttonParameters = new HashMap();
                for (Button button : Util.iterate(form.getButtons())) {
                    String name = button.getParameter();
                    if (name != null && buttonParameters.get(name) == null) {
                        WorkItemAction action = new WorkItemAction();
                        action.setVariable(name);
                        action.setValue(item.get(name));
                        response.add(action);
                        buttonParameters.put(name, button);
                    }
                }
            }
        }

        // you normally do not have both a form and a set
        ApprovalSet aset = item.getApprovalSet();
        if (aset != null) {
            for (ApprovalItem appitem : Util.iterate(aset.getItems())) {
                WorkItemAction action = new WorkItemAction(appitem);
                response.add(action);
            }
        }

        // TODO: stubbing out variable actions is harder because we
        // are guided by what is declared as returns in the Approval
        // which we don't have access to at this point
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Replay
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Replay a workflow test.
     * A new case will be launched each time you replay, if one already
     * exists with the desired name it is terminated.  The case will
     * automatically advance while we have responses, if it needs to 
     * suspend on a work item we convert into capture mode and 
     * capture the remainder of the interactions.
     */
    public void execute(WorkflowTestSuite suite) throws Exception {

        WorkflowTest test = suite.getPrimaryTest();
        if (test == null) {
            println("ERROR: No primary test in suite");
        }
        else {
            // make a Workflower and point it back to us
            _workflower = new Workflower(_context);
            _workflower.setTestHarness(this);

            // this tells the Workflower callback functions that
            // we're replaying rather than doing a new capture
            _replay = suite;

            // if we replay an old capture file, reset any residual state
            test.resetResponses();
            test.clearStubResponses();

            // launch a new workflow case, delete the old one if it exists
            WorkflowCase wfcase = launch(test);

            if (wfcase != null) {
                String caseName = wfcase.getName();

                // advance until we run out
                wfcase = advance(suite, test, wfcase);

                if (wfcase == null || wfcase.isComplete()) {
                    if (test.hasMoreResponses()) {
                        println("WARNING: Workflow case completed before consuming all responses");
                    }

                    TaskResult result = _context.getObjectByName(TaskResult.class, caseName);
                    if (result == null) {
                        println("WARNING: Could not find TaskResult for case: " + caseName);
                    }
                    else {
                        // should we just dump all the messages?    
                        showWarningsAndErrors(result);
                    }
                }
                else {
                    // make sure capture is enabled from this
                    // point forward so we can capture responses
                    // to the new items
                    wfcase.put(Workflow.VAR_CAPTURE, "true");

                    // If we read this from a file make sure work item
                    // capture goes back to that file?
                    // No, I like live capture to create new WorkfowTestSuite objects
                    // and you can decide later whether to update the files.
                    // Could have an option for this.

                    //if (suite.getFile() != null)
                    //wfcase.put(Workflow.VAR_CAPTURE_FILE, suite.getFile());

                    _context.saveObject(wfcase);
                    _context.commitTransaction();

                    // make it look like a capture rather than a replay
                    // so we can save stub responses
                    _capture = _replay;
                    _replay = null;
                    saveCapture(wfcase);
                }
            }
        }
    }

    private void showWarningsAndErrors(TaskResult result) {
        if (result != null) {
            showMessages(result.getMessagesByType(Message.Type.Warn));
            showMessages(result.getMessagesByType(Message.Type.Error));
        }
    }

    private void showMessages(List<Message> msgs) {
        for (Message msg : Util.iterate(msgs)) {
            println(msg.toString());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Launching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch a new workflow test from the suite.
     * This always launches a new case, if one already exists with the
     * preferred name it will be terminated.  In other words we do 
     * not support numeric suffixes to qualify the name, this is because
     * we most often want to terminate previous cases after fixing
     * a problem.
     */
    private WorkflowCase launch(WorkflowTest test) throws Exception {

        WorkflowCase wfcase = null;

        WorkflowLaunch launch = test.getWorkflowLaunch();
        if (launch == null) {
            println("ERROR: Missing WorkflowLaunch in test: " + test.getName());
        }
        else {
            String ref = launch.getWorkflowRef();
            if (ref == null) {
                println("ERROR: Missing workflow reference in WorkflowLaunch: " + test.getName());
            }
            else {
                // clone this so we can modify it without saving side effects by accident
                launch = (WorkflowLaunch)launch.deepCopy(_context);

                // should normally have had one of these for a capture
                // if it was manually written fall back to the process name
                if (launch.getCaseName() == null)
                    launch.setCaseName(ref);

                println("Launching case: " + launch.getCaseName());
                println("Workflow process: " + ref);

                // delete residue from a previous run of this case
                cleanupPreviousCase(launch);

                // fix the targetId if the capture was made from a different db
                SailPointObject obj = getTargetObject(launch.getTargetClass(), launch.getTargetName());
                if (obj != null)
                    launch.setTargetId(obj.getId());
                else
                    launch.setTargetId(null);

                // TODO: If there is a _variableFile in this test read it and merge
                // it into the WorkflowLaunch variables

                // TODO: make trace optional but easier to ask for
                //launch.put(Workflow.VAR_TRACE, "terse");

                launch = _workflower.launch(launch);
                wfcase = launch.getWorkflowCase();

                println("Launch status: " + launch.getStatus());
                if (launch.getBackgroundItemId() != null)
                    println("Background item id: " + launch.getBackgroundItemId());

                TaskResult result = launch.getTaskResult();
                if (result == null) {
                    println("ERROR: Launched without a TaskResult!");
                }
                else {
                    // noisy and don't really need it, just print errors
                    //println(result.toXml());
                    showWarningsAndErrors(result);
                }
            }
        }

        return wfcase;
    }

    /**
     * Get the target object of a case.
     */
    private <T extends SailPointObject> SailPointObject getTargetObject(String className, String name)
        throws GeneralException {

        SailPointObject obj = null;
        try {
            if (className != null && name != null) {
                if (className.indexOf(".") < 0) 
                    className = "sailpoint.object." + className;
                Class<T> clazz = (Class<T>)Class.forName(className);
                obj = _context.getObjectByName(clazz, name);
            }
        }
        catch (Throwable t) {
            println("Unable to determine target object: " + className + ":" + name);
        }

        return obj;
    }

    /**
     * Cleanup state from a previous execution of this test.
     * Unlike normal case launches we always delete the last one rather than
     * making a qualified name.  This is important to clear out clutter
     * especially the IdentityRequest objects which can impact how LCM behaves.
     * 
     * Terminator does the work for WorkflowCase and WorkItem but we have to handle
     * IdentityRequests and the TaskResult.
     *
     *    - find all IdentityRequests whose targetId is the same as
     *      the WorkflowCase targetId
     *    - for each of those get the taskResultId from the attribute map
     *    - if the taskResultId matches the result id of the case, delete it
     */
    private void cleanupPreviousCase(WorkflowLaunch launch) 
        throws GeneralException {

        // delete the existing case
        // TODO: try to find and delete the IdentityRequest objects too?
        // the connections are unfortunately very loose and not easily queryable
        WorkflowCase wfcase = _context.getObjectByName(WorkflowCase.class, launch.getCaseName());
        if (wfcase != null) {
            // terminator will trace this
            println("Deleting previous case results:");

            String caseResultId = wfcase.getTaskResultId();

            // this will get the WorkflowCase, TaskResult, and WorkItems
            Terminator t = new Terminator(_context);
            t.setTrace(true);
            t.deleteObject(wfcase);

            // find the IdentityRequests
            List<String> props = new ArrayList<String>();
            props.add("id");
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("targetId", wfcase.getTargetId()));
            // options are endDate, completionStatus, both are indexed
            ops.add(Filter.isnull("completionStatus"));

            Iterator<Object[]> it = _context.search(IdentityRequest.class, ops, props);
            while (it.hasNext()) {
                Object[] row = it.next();
                String id = (String)row[0];
                IdentityRequest req = _context.getObjectById(IdentityRequest.class, id);
                if (req != null) {
                    String resultId = req.getTaskResultId();
                    if (resultId != null && resultId.equals(caseResultId)) {
                        println("Deleting IdentityRequest " + req.getName());
                        _context.removeObject(req);
                        _context.commitTransaction();
                    }
                }
            }
        }

        // make sure there is no TaskResult for a completed and deleted case
        // since this is what determines if we do a name qualifier
        TaskResult result = _context.getObjectByName(TaskResult.class, launch.getCaseName());
        if (result != null) {
            println("Deleting previous TaskResult: " + result.getName());
            _context.removeObject(result);
            _context.commitTransaction();
        }

        _context.decache();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Advancing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Advance a suspended case by supplying work item responses.
     * Responses must be processed in the order they appear in the test.
     * The owner is important so we know which of several parallel work items
     * to respond to.  This currently assumes that one Identity cannot be the
     * owner of more than one parallel work item.  May need to make this more complex.
     */
    private WorkflowCase advance(WorkflowTestSuite suite, WorkflowTest test, WorkflowCase wfcase) 
        throws Exception {

        boolean stop = false;

        while (!stop) {
            if (wfcase == null || wfcase.isComplete()) {
                // ran to completion without needing anything
                println("Workflow completed");
                stop = true;
            }
            else {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("workflowCase", wfcase));
                List<WorkItem> items = _context.getObjects(WorkItem.class, ops);

                if (items == null || items.size() == 0) {
                    // shouldn't be here without a completed case
                    println("Case was not complete but had no work items!");
                    stop = true;
                }
                else {
                    // check for event items (background, wait), these do not have responses
                    WorkItem item = getEventItem(items);
                    if (item != null) {
                        item.setState(WorkItem.State.Finished);
                        _workflower.process(item, false);
                        // refetch the case after each response
                        _context.decache();
                        wfcase = _context.getObjectById(WorkflowCase.class, wfcase.getId());
                    }
                    else {
                        WorkItemResponse response = test.getNextResponse();
                        if (response == null) {
                            println("No more responses for test: " + suite.getName());
                            stop = true;
                        }
                        else {
                            item = getItemForResponse(items, response);
                            if (item == null) {
                                println("ERROR: No matching WorkItem for response:");
                                println(response.toXml());
                                stop = true;
                            }
                            else {
                                // TODO: Figure out if this was a shared response and say something about it
                                String owner = "???";
                                Identity ident = item.getOwner();
                                if (ident != null)
                                    owner = ident.getName();
                                println("Responding to work item: " + item.getName() + ", " + owner + ", " + item.getDescription());
                                respond(item, response);

                                // refetch the case after each response
                                _context.decache();
                                wfcase = _context.getObjectById(WorkflowCase.class, wfcase.getId());
                            }
                        }
                    }
                }
            }
        }

        return wfcase;
    }

    /**
     * Look for an event item.
     * These are seen with Steps that used the background='true' or wait='x' options.
     * When these exist they must be the only work items open for this case.  
     */
    private WorkItem getEventItem(List<WorkItem> items) {
        WorkItem event = null;
        for (WorkItem item : Util.iterate(items)) {
            if (item.getOwner() == null) {
                event = item;
                break;
            }
        }
        if (event != null && items.size() > 1) {
            // not supposed to happen, just finish the backgrounding item and move on
            // but there is probably an error somewhere
            println("ERROR: Found event work item along with other work items.");
        }
        return event;
    }
        
    /**
     * Match a response to one of the active work items.
     * Currently this is only matching by name which is enough most of the time but
     * in theory there can be more than one parallel item with the same owner.
     * TODO: What about workgroup owners, will those be captured properly?
     */
    private WorkItem getItemForResponse(List<WorkItem> items, WorkItemResponse response) {
        WorkItem found = null;
        for (WorkItem item : Util.iterate(items)) {
            Identity owner = item.getOwner();
            if (owner != null && owner.getName().equals(response.getOwner())) {
                found = item;   
                break;
            }
        }
        return found;
    }

    /**
     * Respond to a work item.
     * Return true if the case reaches completion.
     */
    private void respond(WorkItem item, WorkItemResponse response) throws Exception {

        // what should the default be?
        if (response.getState() == null)
            item.setState(WorkItem.State.Finished);
        else
            item.setState(response.getState());

        println("Work item state: " + item.getState());
        String ccomments = response.getCompletionComments();
        if (ccomments != null) {
            println("Completion comments: " + ccomments);
            item.setCompletionComments(ccomments);
        }

        List<Comment> comments = response.getComments();
        if (Util.size(comments) > 0) {
            println("Other comments: " + comments);
            item.setComments(comments);
        }

        for (WorkItemAction action : Util.iterate(response.getActions())) {

            // this applies to both Variables and Fields, I was going to use
            // it for ApprovalItem states as well but it looks strange in the XML
            Object value = action.getValue();

            if (action.getVariable() != null) {
                String name = action.getVariable();
                println("Setting variable: " + name + " = " + Util.otoa(value));
                item.put(name, value);
            }
            else if (action.getField() != null) {
                String name = action.getField();
                Form form = item.getForm();
                if (form == null) {
                    println("ERROR: Ignoring field action without form: " + name);
                    println(item.toXml());
                }
                else {
                    Field field = form.getField(name);
                    if (field == null) {
                        println("ERROR: Ignoring action for unknown field: " + name);
                        println(item.toXml());
                    }
                    else {
                        println("Setting field: " + name + " = " + Util.otoa(value));
                        field.setValue(value);
                    }
                }
            }
            else if (action.getApproval() != null) {
                String name = action.getApproval();
                ApprovalSet appset = item.getApprovalSet();
                if (appset == null) {
                    println("ERROR: Ignoring approval action without approval set: " + name);
                    println(item.toXml());
                }
                else {
                    ApprovalItem appitem = getApprovalItem(appset, action);
                    if (appitem == null) {
                        println("ERROR: Ignoring action for unknown approval item: " + name);
                        println(item.toXml());
                    }
                    else {
                        println("Setting approval item: " + name);

                        // state should only be Finished (approved) or Rejected
                        WorkItem.State state = action.getState();
                        println("  state: " + Util.otoa(state));
                        appitem.setState(state);

                        String approver = action.getApprover();
                        if (approver != null) {
                            println("  approver: " + approver);
                            appitem.setApprover(approver);
                        }

                        String rejecters = action.getRejecters();
                        if (rejecters != null) {
                            println("  rejecters: " + rejecters);
                            appitem.setRejecters(rejecters);
                        }

                        comments = action.getComments();
                        if (Util.size(comments) > 0) {
                            println("  comments: " + comments);
                            appitem.setComments(comments);
                        }
                    }
                }
            }
            else {
                println("ERROR: Missing action type");
            }
        }

        // don't set the foreground flag so it will ignore any 
        // backgrounding events
        _workflower.process(item, false);
    }
    
    /**
     * Given an ApprovalSet, locate the ApprovalItem that corresponds with
     * a WorkItemAction.
     *
     * Matching on displayValue (role name or entitlement name) works for LCM.
     * If there are multiple assignments there will be an assignmentId
     * but we can ignore that since replays won't match what the
     * new launch generates.  Assume order is stable and we can just
     * match the one whose name matches that isn't already resolved.
     *
     * Hmm, this actually won't be true if they make selective approve/reject
     * decisions on duplicate assignments out of order.  need to match these
     * by position!!
     */
    private ApprovalItem getApprovalItem(ApprovalSet aset, WorkItemAction action) {
        
        ApprovalItem found = null;
        String name = action.getApproval();
        for (ApprovalItem item : Util.iterate(aset.getItems())) {
            // if names match and hasn't been resolved yet
            if (name.equals(item.getDisplayValue()) &&
                item.getState() == null) {
                found = item;
                break;
            }
        }
        return found;
    }



}


