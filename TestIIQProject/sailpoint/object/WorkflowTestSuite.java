/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A model for automated workflow testing.
 * 
 * Author: Jeff
 * 
 * TODO: Suite should have an initFile that is loaded each time a new case is launched.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;

@XMLClass
public class WorkflowTestSuite extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(WorkflowTestSuite.class);

    /**
     * For suites read from a file, the path to the file.
     * Normally this is non-null only when the inherited Hibenrate id is null.
     * If both are set we assume it is a Hibernate object.  Since there is no
     * Hibernate mapping for this property it will become null once it is 
     * saved and reloaded.
     */
    String _file;

    /**
     * The tests in the suite.  There should always be one of these,
     * but for manually written suites you can have more than one and
     * select the one to run with the "test" property.
     */
    List<WorkflowTest> _tests;

    /**
     * Shared responses to work items.  Responses are usually inside
     * each WorkflowTest but if you put them on this list you can 
     * reference them by name and share them among several tests.
     */
    List<WorkItemResponse> _responses;

    /**
     * Extended attributes.  We don't use this yet, but it's nice for all
     * SailPointObjects to have one of these so we don't have to modify the
     * Hibernate schema every time we add an option.
     */
    Attributes<String, Object> _attributes;

    /**
     * When true, a new WorkflowTestSuite object will be created when this
     * suite is launched that will have the same name as the WorkflowCase.
     * This would be used if you wanted to create a suite that brought
     * a case to a certain location, but then wanted to capture different
     * response sequences.  For example launch an LCM request and do the 
     * approvals, but capture different provisioning policy responses.
     *
     * TODO: Think more about this.  Since we have the ability for
     * a suite to contain several WorkflowTests with their own sequences,
     * we could have this meant to create a new WorkflowTest after launching
     * too.  Would require that we not only store the WorkflowTestSuite object
     * in the case but also the WorkflowTest that is being captured.
     */ 
    boolean _replicated;

    /**
     * When the suite is being captured, the name of the WorkflowCase
     * we are associated with.  When _replicated is true this will be
     * the same as our name.
     */
    String _caseName;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public WorkflowTestSuite() {
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty
    public void setFile(String s) {
        _file = s;
    }

    public String getFile() {
        return _file;
    }

    @XMLProperty
    public void setReplicated(boolean b) {
        _replicated = b;
    }

    public boolean isReplicated() {
        return _replicated;
    }

    @XMLProperty
    public void setCaseName(String s) {
        _caseName = s;
    }

    public String getCaseName() {
        return _caseName;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<WorkflowTest> getTests() {
        return _tests;
    }

    public void setTests(List<WorkflowTest> tests) {
        _tests = tests;
    }
        
    public void add(WorkflowTest test) {
        if (test != null) {
            if (_tests == null)
                _tests = new ArrayList<WorkflowTest>();
            _tests.add(test);
        }
    }

    public WorkflowTest getTest(String name) {
        WorkflowTest found = null;
        if (name != null) {
            for (WorkflowTest test : Util.iterate(_tests)) {
                if (name.equals(test.getName())) {
                    found = test;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Convenience method to get the capture test which is always
     * the first one.
     *
     * TODO: Allow some way of specifying which test to run if there
     * are multiples.
     */
    public WorkflowTest getPrimaryTest() {
        WorkflowTest first = null;
        if (Util.size(_tests) > 0) {
            first = _tests.get(0);
        }
        else {
            // should have one by now but bootstrap
            first = new WorkflowTest();
            add(first);
        }
        return first;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<WorkItemResponse> getResponses() {
        return _responses;
    }

    public void setResponses(List<WorkItemResponse> responses) {
        _responses = responses;
    }

    public WorkItemResponse getResponse(String name) {
        WorkItemResponse found = null;
        if (name != null) {
            for (WorkItemResponse response : Util.iterate(_responses)) {
                if (name.equals(response.getName())) {
                    found = response;
                    break;
                }
            }
        }
        return found;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // WorkflowTest
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A suite consists of one or more tests.  The test defines the workflow
     * definition to execute, the input variables, and the sequence of 
     * work item responses.
     */
    @XMLClass
    static public class WorkflowTest extends AbstractXmlObject {

        /**
         * The name of the test.  Should be something short and meaningful, 
         * used to select tests in the WorkflowTestSuite.run property.
         */
        String _name;
    
        /**
         * Launch definition for the workflow.  This includes
         * the workflow name, input variables, and optional case name.
         */
        WorkflowLaunch _launch;

        /**
         * Optional file containing launch variables if you want to share them
         * with multiple suites.
         */
        String _variableFile;

        /**
         * Responses to work items for this test.
         */
        List<WorkItemResponse> _responses;

        /**
         * The list index of the next response to use when using ordered
         * responses (the only supported way right now).
         */
        int _nextResponseIndex;

        public WorkflowTest() {
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setNextResponseIndex(int i) {
            _nextResponseIndex = i;
        }

        public int getNextResponseIndex() {
            return _nextResponseIndex;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public void setWorkflowLaunch(WorkflowLaunch launch) {
            _launch = launch;
        }

        public WorkflowLaunch getWorkflowLaunch() {
            return _launch;
        }

        /**
         * Capture the workflow launch state.  This is what Workflower
         * just call, setWorkflowLaunch is only used during XML parsing.
         * 
         * We must make a copy of this since we won't save capture
         * state until the case has advanced and the WorkflowLaunch object
         * will have been modified by then and contain things other than
         * the initial launch arguments.
         */
        public void captureWorkflowLaunch(XMLReferenceResolver resolver, 
                                          WorkflowLaunch src) {

            WorkflowLaunch copy = new WorkflowLaunch();

            copy.setWorkflowRef(src.getWorkflowRef());
            copy.setCaseName(src.getCaseName());
            copy.setLauncher(src.getLauncher());
            copy.setSessionOwner(src.getSessionOwner());
            copy.setTargetClass(src.getTargetClass());
            copy.setTargetId(src.getTargetId());
            copy.setTargetName(src.getTargetName());
            // sigh, going to assume this never changes so we don't have
            // to mess with a deep copy
            copy.setSecondaryTargets(src.getSecondaryTargets());

            // it is important to at least shallow copy this, but let's
            // be safe in case complex objects are modified
            // ugh, not so fast...
            // testIdentityChangeListeners winds up launching a workflow
            // with an InternalContext in the variables map, I'm not sure
            // why that happens but do a shallow copy until we can track this down
            Map<String,Object> vars = src.getVariables();
            if (vars != null) {
                // XMLObjectFactory xml = XMLObjectFactory.getInstance();
                // copy.setVariables((Map<String,Object>)xml.clone(vars, resolver));
                Map<String,Object> copyvars = new HashMap<String,Object>();
                copyvars.putAll(vars);
                copy.setVariables(copyvars);
            }

            _launch = copy;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<WorkItemResponse> getResponses() {
            return _responses;
        }

        public void setResponses(List<WorkItemResponse> responses) {
            _responses = responses;
        }

        public void add(WorkItemResponse res) {
            if (res != null) {
                if (_responses == null)
                    _responses = new ArrayList<WorkItemResponse>();
                _responses.add(res);
            }
        }

        public WorkItemResponse getResponse(String name) {
            WorkItemResponse found = null;
            if (name != null) {
                for (WorkItemResponse response : Util.iterate(_responses)) {
                    if (name.equals(response.getName())) {
                        found = response;
                        break;
                    }
                }
            }
            return found;
        }
        
        /**
         * Reset the reponse counter.
         */
        public void resetResponses() {
            _nextResponseIndex = 0;
        }

        public boolean hasMoreResponses() {
            return (_responses != null && _nextResponseIndex < _responses.size());
        }

        /**
         * Get the next response in the list and advance the response position.
         */
        public WorkItemResponse getNextResponse() {
            WorkItemResponse response = null;
            if (hasMoreResponses()) {
                response = _responses.get(_nextResponseIndex);
                _nextResponseIndex++;
            }
            return response;
        }

        /**
         * Look for a stub response for a given work item id and remove it.
         * It will normally be fleshed out with work item completion state
         * and inserted back with addFinishedResponse.
         */
        public WorkItemResponse removeStubResponse(String id) {
            WorkItemResponse found = null;
            if (id != null && _responses != null) {
                ListIterator<WorkItemResponse> it = _responses.listIterator();
                while (it.hasNext()) {
                    WorkItemResponse response = it.next();
                    if (id.equals(response.getWorkItemId())) {
                        found = response;
                        it.remove();
                        break;
                    }
                }
            }
            return found;
        }

        /**
         * Insert a finished response into the list.   This goes after the
         * previously finished responses and before the stubs.
         */
        public void addFinishedResponse(WorkItemResponse finished) {
            if (finished != null) {
                WorkItemResponse stub = null;
                int psn = 0;
                if (_responses != null) {
                    while (psn < _responses.size()) {
                        WorkItemResponse res = _responses.get(psn);
                        if (res.getWorkItemId() == null) {
                            psn++;
                        }
                        else {
                            // found a stub
                            stub = res;
                            break;
                        }
                    }
                }
                if (stub == null) {
                    add(finished);
                }
                else {
                    _responses.add(psn, finished);
                }
            }
        }

        /**
         * Remove any stub responses that were created during capture.
         * A stub response is defined as one with a non-null workItemId.
         * Normally these are all at the end.
         */
        public void clearStubResponses() {
            if (_responses != null) {
                ListIterator<WorkItemResponse> it = _responses.listIterator();
                while (it.hasNext()) {
                    WorkItemResponse res = it.next();
                    if (res.getWorkItemId() != null) {
                        // a stub
                        log.info("Removing work item stub response: " + res.getWorkItemId());
                        it.remove();
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemResponse
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An object that defines the response to one work item.
     */
    @XMLClass
    static public class WorkItemResponse extends AbstractXmlObject {

        /**
         * Name of the response.  Should be a short and meaningful name.
         * Used for shared responses in the WorkflowTest.responses list.
         */
        String _name;

        /**
         * The name of another WorkItemResponse to merge with this one.
         * This is similar to inheriting or simply referencing the
         * definition of another response so you can share parts of their
         * definition.
         */
        String _include;

        /**
         * The owner of the item.  This must be set if there are parallel
         * work items, if not we will do them in the order they appear in
         * the object.  
         */
        String _owner;

        /**
         * The state to set.
         */
        WorkItem.State _state;

        /**
         * Final completion comments from the popup.
         */
        String _completionComments;

        /**
         * Comments accumulated during approval, beyond the
         * completionComment.
         */
        List<Comment> _comments;

        /**
         * The actions to take.
         * TODO: state should just be one of these?
         */
        List<WorkItemAction> _actions;

        /**
         * When a stub response was created to track a work item, this
         * is the id of the work item.
         */
        String _workItemId;

        public WorkItemResponse() {
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setInclude(String s) {
            _include = s;
        }

        public String getInclude() {
            return _include;
        }

        @XMLProperty
        public void setOwner(String s) {
            _owner = s;
        }

        public String getOwner() {
            return _owner;
        }

        @XMLProperty
        public void setState(WorkItem.State s) {
            _state = s;
        }

        public WorkItem.State getState() {
            return _state;
        }

        @XMLProperty(mode = SerializationMode.ELEMENT)
        public String getCompletionComments() {
            return _completionComments;
        }

        public void setCompletionComments(String s) {
            _completionComments = s;
        }

        @XMLProperty(mode = SerializationMode.LIST, xmlname = "WorkItemComments")
        public List<Comment> getComments() {
            return _comments;
        }

        public void setComments(List<Comment> comments) {
            _comments = comments;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<WorkItemAction> getActions() {
            return _actions;
        }

        public void setActions(List<WorkItemAction> actions) {
            _actions = actions;
        }

        public void add(WorkItemAction action) {
            if (action != null) {
                if (_actions == null)
                    _actions = new ArrayList<WorkItemAction>();
                _actions.add(action);
            }
        }

        @XMLProperty
        public void setWorkItemId(String s) {
            _workItemId = s;
        }

        public String getWorkItemId() {
            return _workItemId;
        }

    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemAction
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An object that defines one action to take on the work item.
     * There are thre styles of action: variable, field, and approval.
     *
     * Variable actions set the value of one work item variable.
     * Field actions set the value of one Form Field.
     * Approval actions set things in one ApprovalItem.
     *
     * Approval actions need to be able to set these things in
     * an ApprovalItem.
     *
     *    state - approved or rejected
     *    comments - comments added during approval
     *    approver - who actually approved
     *    rejecters - csv of people who rejected
     *
     * Everything else in an Approval Item is read-only as far
     * as I can tell.
     *
     * State will be taken from our _value, set it to the Strings
     * "approved" or "rejected".
     *
     * We could probably simplify comments and just have a List<String>
     * 
     */
    @XMLClass
    static public class WorkItemAction extends AbstractXmlObject {

        /**
         * Value to set into a variable or field.
         * The "value" of an ApprovalItem is the _state;
         */
        Object _value;

        /**
         * The name of an item variable to set.
         */
        String _variable;

        /**
         * The name of a field to modify.   
         */
        String _field;
        
        /**
         * The id of an approval item to modify.
         * For LCM this is either a role name or an entitlement name.
         */
        String _approval;
        
        /**
         * An assignment id that qualifies the _approval.  Necessary when
         * the same role or entitlement is assigned more than once.
         * Actually not using this since ids aren't stable in capture files.
         */
        String _assignmentId;

        /**
         * The name of the user that did the approval for approval actions.
         */
        String _approver;

        /**
         * The names of rejecters for approval actions.
         */
        String _rejecters;

        /**
         * Comments added to an approval item.
         */
        List<Comment> _comments;

        /**
         * The approval state, Finished (approved) or Rejected.
         */
        WorkItem.State _state;

        public WorkItemAction() {
        }

        /**
         * Make an action for a form field.
         */
        public WorkItemAction(Field field) {
            _field = field.getName();
            _value = field.getValue();
        }

        /**
         * Make an action for an ApprovalItem.
         * For LCM displayName is "Role" for role requests and probably "Entitlement" for entitlement
         * requests, it may be translated but I'm not sure.  
         * displayValue has the role name which is what is interesting.  Assuming for now that
         * displayValue will uniquely define an item in a set, though we probably need both displayName and
         * and displayValue on the off chance that roles and entitlements can have the same name.
         */
        public WorkItemAction(ApprovalItem item) {

            _approval = item.getDisplayValue();
            _approver = item.getApprover();

            // this might cause problems for replay...
            // yes it does, can't rely on it
            //_assignmentId = item.getAssignmentId();

            // these are normally null when building stub actions, and
            // non-null when assimulating the completed work item
            _rejecters = item.getRejecters();
            _comments = item.getComments();
            _state = item.getState();
        }

        @XMLProperty
        public String getVariable() {
            return _variable;
        }

        public void setVariable(String s) {
            _variable = s;
        }

        @XMLProperty
        public String getField() {
            return _field;
        }

        public void setField(String s) {
            _field = s;
        }

        public void setValue(Object value) {
            _value = value;
        }

        public Object getValue() {
            return _value;
        }

        /**
         * @exclude
         * Required for Hibernate 
         * @deprecated use {@link #getValue()}
         */
        @Deprecated
        @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="value")
        public String getValueXmlAttribute() {
            return (_value instanceof String) ? (String)_value : null;
        }

        /**
         * @exclude
         * Required for Hibernate 
         * @deprecated use {@link #setValue(Object)} 
         */
        @Deprecated
        public void setValueXmlAttribute(String value) {
            _value = value;
        }

        /**
         * @exclude
         * Required for Hibernate 
         * @deprecated use {@link #getValue()}
         */
        @Deprecated
        @XMLProperty(xmlname="Value")
        public Object getValueXmlElement() {
            return (_value instanceof String) ? null : _value;
        }

        /**
         * @exclude
         * Required for Hibernate 
         * @deprecated use {@link #setValue(Object)}
         */
        @Deprecated
        public void setValueXmlElement(Object value) {
            _value = value;
        }

        @XMLProperty
        public String getApproval() {
            return _approval;
        }

        public void setApproval(String s) {
            _approval = s;
        }

        @XMLProperty
        public String getAssignmentId() {
            return _assignmentId;
        }

        public void setAssignmentId(String s) {
            _assignmentId = s;
        }

        @XMLProperty
        public String getApprover() {
            return _approver;
        }

        public void setApprover(String s) {
            _approver = s;
        }

        @XMLProperty
        public String getRejecters() {
            return _rejecters;
        }

        public void setRejecters(String s) {
            _rejecters = s;
        }

        @XMLProperty(mode = SerializationMode.LIST, xmlname = "ApprovalItemComments")
        public List<Comment> getComments() {
            return _comments;
        }

        public void setComments(List<Comment> comments) {
            _comments = comments;
        }

        @XMLProperty
        public void setState(WorkItem.State s) {
            _state = s;
        }

        public WorkItem.State getState() {
            return _state;
        }

    }

}
