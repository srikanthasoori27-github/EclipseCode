/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to role model approvals.
 *
 * Author: Jeff
 *
 * Note that these are old methods and some directly access workflow
 * variables rather than being passed arguments.  This is not the recommended
 * convention for new workflows, but it's been used this way for
 * a long time and we have to retain backward compatibility.
 * 
 */

package sailpoint.workflow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.RoleChangeAnalyzer;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.Wonderer;
import sailpoint.api.Workflower;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.BundleDifference;
import sailpoint.object.Configuration;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.FilterRenderer;
import sailpoint.object.Identity;
import sailpoint.object.Profile;
import sailpoint.object.ProfileDifference;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.WorkflowCase;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A workflow library with methods related to role model approvals.
 */
public class RoleLibrary extends ApprovalLibrary {

    private static Log log = LogFactory.getLog(RoleLibrary.class);

    public RoleLibrary() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Approval Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Workflow variable holding the name of the UI component
     * that launched the workflow.  This may be used to conditionalize
     * workflow behavior.
     * 
     * @ignore
     * jsl - as far as I can tell these have never been used
     */
    public static final String VAR_APPROVAL_SOURCE = "approvalSource";

    /**
     * Values for the VAR_APPROVAL_SOURCE variable.
     * 
     * @ignore
     * jsl - as far as I can tell these have never been used
     */
    public static final String APPROVAL_SOURCE_MODELER = "modeler";
    public static final String APPROVAL_SOURCE_DIRECTED = "directed";
    public static final String APPROVAL_SOURCE_UNDIRECTED = "undirected";
    public static final String APPROVAL_SOURCE_CERTIFICATION = "certification";

    /**
     * Workflow option to not propagate entitlement changes on role edit
     */
    public static final String VAR_NO_ROLE_PROPAGATION = "noRolePropagation";

    //////////////////////////////////////////////////////////////////////
    //
    // Approval Object Inspection
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return true if the object being modified is considered to have
     * significant changes.  For roles this would be changes to the
     * profile list or the profiles themselves but not things like
     * the name or description.  
     * 
     * @ignore
     * The goal here is to prevent annoying approvals for small
     * non-behavioral changes though this is subjective.  We could try
     * to take an argument that lists the properties that are insignificant
     * but with slightly more work you can make a handler subclass and
     * overload this method.
     *
     * This has never been used.
     */
    public boolean isSignificantChange(WorkflowContext wfc) {

        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Impact Analysis
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Workflow variable holding the name of the Identity that
     * wishes to perform an impact analysis.  In the standard workflow
     * this will trigger an impact analysis, if not set we go directly
     * to approvals.
     */
    public static final String VAR_IMPACT_ANALYSIS_OWNER = 
    "impactAnalysisOwner";

    /**
     * Filter used to to restrict what the impact analyzer looks at.
     */
    public static final String VAR_IMPACT_ANALYSIS_FILTER = 
    "impactAnalysisFilter";

    /**
     * Workflow variable holding the id of the TaskResult containing
     * the impact analysis result.
     */
    public static final String VAR_IMPACT_ANALYSIS_RESULT = 
    "impactAnalysisResultId";

    /**
     * Start an impact analysis task for whatever is in the workflow.
     * Currently this must be a role.
     *
     * @ignore
     * There is only one impact analysis task, named by Wonderer.
     *
     * This is an old method and it directly access variables rather
     * than being passed arguments.  This is not the recommended
     * convention for new workflows, but it's been used this way for
     * a long time and we have to retain backward compatibility.
     */
    public Object launchImpactAnalysis(WorkflowContext wfc) 
        throws GeneralException {

        // This op handler is unusual in that we need the case id before
        // we've hit an approval.  Make sure it is persisted.
        Workflower flower = wfc.getWorkflower();
        flower.persistCase(wfc);

        // setup task args
        Attributes<String,Object> args = new Attributes<String,Object>();
        Workflow wf = wfc.getWorkflow();
        args.put(Wonderer.ARG_WORKFLOW_CASE, wfc.getWorkflowCase().getId());

        // pass through any <Args> we have directly under the <Step>
        // this is where noImpactAnalysis, doOverlapAnalysis, and
        // overlapAnalysisFilter can be set
        Attributes<String,Object> stepargs = wfc.getStepArguments();
        if (stepargs != null)
            args.putAll(stepargs);

        // we have historically passed this from a workflow variable,
        // would rather get it from a step arg, but these are easier 
        // to configure
        String filter = wf.getString(VAR_IMPACT_ANALYSIS_FILTER);
        if (filter != null)
            args.put(Wonderer.ARG_FILTER, filter);

        // Generate a name for the task
        // this will also use "Rename New" in case we have another one
        String resname = "Impact Analysis: " + getDisplayableRoleName(wfc);
        args.put(TaskSchedule.ARG_RESULT_NAME, resname);

        // Launch the task
        TaskManager tm = new TaskManager(wfc.getSailPointContext());
        TaskDefinition def = tm.getTaskDefinition(Wonderer.TASK_DEFINITION);
        TaskResult result = tm.runWithResult(def, args);

        // leave the result id in the workflow
        wf.put(VAR_IMPACT_ANALYSIS_RESULT, result.getId());

        return result.getId();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the differences between the role held in the workflow
     * and the current version.
     * @param wfc WorkflowContext
     * @return {@link BundleDifference} object           
     */
    public Object getRoleDifferences(WorkflowContext wfc) 
        throws GeneralException {

        BundleDifference diffs = null;
        SailPointContext con = wfc.getSailPointContext();
        SailPointObject obj = getObject(wfc);
        if (obj instanceof Bundle) {
            Bundle neu = (Bundle)obj;
            Bundle old = null;
            // if it doesn't have an id it's new
            if (neu.getId() != null) {
                old = con.getObjectById(Bundle.class, neu.getId());
                if (old == null) {
                    // deleted out from under us, proceed as if it were new
                    log.error("Lost current version of role");
                }

                RoleLifecycler cycler = new RoleLifecycler(con);
                diffs = cycler.diff(old, neu);
            }
        }

        return diffs;
    }
    
    /**
     * Friendly utility method to create a log entry,
     * mostly because auditRoleDifferences is peppered with it.
     */
    private void createAuditEntry(WorkflowContext wfc, String source, String action, String target,
            String string1, String string2, String string3, String string4) {
        
        SailPointContext con = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        try {
            con.decache();
        }
        catch (Throwable t) {
            // most likely will get another error below, but do our best
            log.error("Unable to decache prior to audit");
            log.error(t);
        }
        
        // must have a source and action
        if (source != null && action != null) {
            
            // Audit columns are 255 bytes wide
            Auditor.logAs(source, action, target, 
                          Difference.stringify(string1, 255),
                          Difference.stringify(string2, 255),
                          Difference.stringify(string3, 255),
                          Difference.stringify(string4, 255));
            
            // Auditor saves the log record but does NOT commit!
            try {
                con.commitTransaction();
            }
            catch (Throwable t) {
                log.error(t);
            }
        }
    }
    
    /**
     * Create enough audit entries to satisfy the lengths of the strings.
     * This is use by auditRoleDifferences because the differences could potentially
     * be very large.
     */
    private void createExtendedAuditEntries(WorkflowContext wfc, String source, String action, String target,
            String string1, String string2, String string3, String string4) {
        
        sliceExtendedAuditEntries(wfc, source, action, target, string1, string2, string3, string4, 0);
        
    }
    
    private void sliceExtendedAuditEntries(WorkflowContext wfc, String source, String action, String target,
            String string1, String string2, String string3, String string4, int iteration) {
        
        // Audit fields have a limit of 255 characters, we'll use 230 to be safe.
        int slice = 230;
        
        // Assume that source, action, and target are not too long
        if ("".equals(string1) && "".equals(string2) && 
            "".equals(string3) && "".equals(string4)) {
            // we're done, there are no more entries to create
            return;
        }
        
        // Find the end of the string or the point at which we are slicing.
        int string1Slice = (string1.length() < slice) ? string1.length() : slice;
        int string2Slice = (string2.length() < slice) ? string2.length() : slice;
        int string3Slice = (string3.length() < slice) ? string3.length() : slice;
        int string4Slice = (string4.length() < slice) ? string4.length() : slice;
        
        // Get the string that will actually be inserted into an audit event here.
        String entry1 = string1.substring(0, string1Slice);
        String entry2 = string2.substring(0, string2Slice);
        String entry3 = string3.substring(0, string3Slice);
        String entry4 = string4.substring(0, string4Slice);
        
        if (iteration > 0) {
            // Add "continued" at the beginning of each entry, so that we know
            // it's part of the previous entry
            if (string1Slice > 0) {
                entry1 = getMessage(MessageKeys.CONTINUED, null) + ": " + entry1;
            }
            
            if (string2Slice > 0) {
                entry2 = getMessage(MessageKeys.CONTINUED, null) + ": " + entry2;
            }
            
            if (string3Slice > 0) {
                entry3 = getMessage(MessageKeys.CONTINUED, null) + ": " + entry3;
            }
            
            if (string4Slice > 0) {
                entry4 = getMessage(MessageKeys.CONTINUED, null) + ": " + entry4;
            }
        }
        
        // add the audit event
        createAuditEntry(wfc, source, action, target, entry1, entry2, entry3, entry4);
        
        // recurse with substrings
        sliceExtendedAuditEntries(wfc, source, action, target,
                string1.substring(string1Slice),
                string2.substring(string2Slice),
                string3.substring(string3Slice),
                string4.substring(string4Slice), iteration + 1);
        
    }
    
    /**
     * Create multiple audit events, one for each attribute/diff in BundleDifferences
     * @param wfc WorkflowContext
     * @return null
     */
    public Object auditRoleDifferences(WorkflowContext wfc) throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        String source = args.getString(StandardWorkflowHandler.ARG_AUDIT_SOURCE);
        String action = args.getString(StandardWorkflowHandler.ARG_AUDIT_ACTION);
        String target = args.getString(StandardWorkflowHandler.ARG_AUDIT_TARGET);
        String string1 = args.getString(StandardWorkflowHandler.ARG_AUDIT_STRING1);
        
        // String1 will come from the workflow.  
        // String2-String4 will be used for differences.  Even if they are specified
        //    in the workflow, they will be overwritten here.
        // String2 = what changed
        // String3 = old value
        // String4 = new value
        
        BundleDifference diffs = null;
        SailPointContext con = wfc.getSailPointContext();
        SailPointObject obj = getObject(wfc);
        if (obj instanceof Bundle) {
            Bundle neu = (Bundle)obj;
            Bundle old = null;
            RoleLifecycler cycler = new RoleLifecycler(con);
            
            // if it doesn't have an id it's new
            if (neu.getId() != null) {
                old = con.getObjectById(Bundle.class, neu.getId());
                if (old == null) {
                    // deleted out from under us, proceed as if it were new
                    log.error("Lost current version of role");
                }
            }
            
            int limit = Difference.MAX_STRING_LENGTH;
            Difference.MAX_STRING_LENGTH = 6000;       // This should be enough for anybody
            
            // Do a diff even if this is a new role.  We want to know
            // what the new role looks like.
            diffs = cycler.diff(old, neu);
            
            // reset the limits.
            Difference.MAX_STRING_LENGTH = limit;
            
            // We need to know whether a profile was deleted and which one.
            // The RoleLifeCycler doesn't tell us this and at this point,
            // I don't want to modify that interface.
            addDeletedProfileDiffs(diffs, old, neu);
            
        }
        
        if (null != diffs) {
            List<Difference> diffList = diffs.getAttributeDifferences();
            if (null != diffList) {
                for (Difference diff : diffList) {
                    String string2 = getMessage(diff.getDisplayableName(), null);
                    String string3 = getMessage(MessageKeys.ATTR_OLD_VALUE, null) + ": " + diff.getOldValue();
                    String string4 = getMessage(MessageKeys.ATTR_NEW_VALUE, null) + ": " + diff.getNewValue();            
                    
                    createExtendedAuditEntries(wfc, source, action, target,
                            string1, string2, string3, string4);
                }
            }
            
            // in case there are profile changes
            List<ProfileDifference> proDiffList = diffs.getProfileDifferences();
            if (null != proDiffList) {
                for (ProfileDifference proDiff : proDiffList) {
                    if (proDiff.hasDifferences()) {
                        String app = proDiff.getApplication();
                        String onProfile = " " + getMessage("on", null) + " " +
                                getMessage(MessageKeys.PROFILE_FOR_NAMED_ENTITY, app);
                       
                        List<Difference> proAttrDiffList = proDiff.getAttributeDifferences();
                        if (null != proAttrDiffList) {
                            for (Difference diff : proAttrDiffList) {
                                String string2 = getMessage(diff.getDisplayableName(), null) + onProfile;
                                String string3 = getMessage(MessageKeys.ATTR_OLD_VALUE, null) + ": " + diff.getOldValue();
                                String string4 = getMessage(MessageKeys.ATTR_NEW_VALUE, null) + ": " + diff.getNewValue();            
                               
                                createExtendedAuditEntries(wfc, source, action, target,
                                        string1, string2, string3, string4);
                            }
                        }
                       
                        Difference proFilterDiff = proDiff.getFilterDifference();
                        if (null != proFilterDiff) {
                            String string2 = getMessage(MessageKeys.FILTER, null) + onProfile;
                            String string3 = getMessage(MessageKeys.ATTR_OLD_VALUE, null) + ": " + proFilterDiff.getOldValue();
                            String string4 = getMessage(MessageKeys.ATTR_NEW_VALUE, null) + ": " + proFilterDiff.getNewValue();            
                           
                            createExtendedAuditEntries(wfc, source, action, target,
                                    string1, string2, string3, string4);
                        }
                        
                        List<Difference> proPermDiffList = proDiff.getPermissionDifferences();
                        if (null != proPermDiffList) {
                            for (Difference diff : proPermDiffList) {
                                String string2 = getMessage(MessageKeys.PERMISSIONS, null) + ": " +
                                                 getMessage(diff.getDisplayableName(), null) + onProfile;
                                String string3 = getMessage(MessageKeys.ATTR_OLD_VALUE, null) + ": " + diff.getOldValue();
                                String string4 = getMessage(MessageKeys.ATTR_NEW_VALUE, null) + ": " + diff.getNewValue();            
                               
                                createExtendedAuditEntries(wfc, source, action, target,
                                        string1, string2, string3, string4);
                            }
                        }
                    }
                }
            }
            
            // Assignment rule differences
            Difference selDiff = diffs.getSelectorDifference();
            if (null != selDiff) {
                String string2 = getMessage(MessageKeys.ROLE_LABEL_ASSIGNMENT_SELECTOR, null) + ": " + getMessage(selDiff.getDisplayableName(), null);
                String string3 = getMessage(MessageKeys.ATTR_OLD_VALUE, null) + ": " + selDiff.getOldValue();
                String string4 = getMessage(MessageKeys.ATTR_NEW_VALUE, null) + ": " + selDiff.getNewValue();            
               
                createExtendedAuditEntries(wfc, source, action, target,
                        string1, string2, string3, string4);
            }
        }
        
        return null;
    }
    
    /**
     * To compare the profile lists on a bundle, convert them
     * to a sort list of names then use the Difference utility
     * to generate the diff with old/new values enumerated.
     * Originally from RoleLifecycler, this version only looks for deleted profiles.
     */
    private void addDeletedProfileDiffs(BundleDifference diffs, 
                                 Bundle old, Bundle neu) 
        throws GeneralException {

        if (old != null) {
            List<Profile> oldProfiles = old.getProfiles();
            if (oldProfiles != null) {
                for (Profile p : oldProfiles) {
                    Profile newp = null;
                    if (neu != null)
                        newp = neu.findProfile(p.getId());
                    ProfileDifference pd = diffForDeletion(p, newp);
                    if (pd.hasDifferences())
                        diffs.add(pd);
                }
            }
        }
    }
    
    /**
     * only report deleted profiles
     */
    private ProfileDifference diffForDeletion(Profile old, Profile neu) 
        throws GeneralException {

        ProfileDifference diffs = new ProfileDifference();
    
        if (old == null) {
            // We only want the delete case
        }
        else if (neu == null) {
            // this is the delete case, I'm not going to bother with
            // a Difference representation here since this should be
            // presented in a different way.
            diffs.setApplication(old.getApplication());
            addDiff(diffs, RoleLifecycler.ATT_DESCRIPTION, 
                    old.getDescription(), null);
            addDiff(diffs, RoleLifecycler.ATT_APPLICATION, 
                    old.getApplication(), null);
    
            addDiff(diffs, old.getConstraints(), null);
    
        }
        else {
            // We only want the delete case
        }
    
        return diffs;
    }
    
    /**
     * From RoleLifecycler
     */
    private void addDiff(ProfileDifference diffs, String attname, 
            Object old, Object neu) {

        if ((old instanceof String) && (((String)old).length() == 0))
            old = null;
        
        if ((neu instanceof String) && (((String)neu).length() == 0))
            neu = null;
        
        Difference diff = Difference.diff(old, neu);
        if (diff != null) {
            diff.setAttribute(attname);
            diffs.add(diff);
        }
    }
    
    /**
     * From roleLifecycler
     */
    private void addDiff(ProfileDifference diffs,
            List<Filter> old, List<Filter> neu) {

        // for simplicity combine these into a single filter
        Filter oldRoot = simplify(old);
        Filter newRoot = simplify(neu);
        
        // and diff the text
        // note that we're using the new renderer that generates
        // a more SQL-ish syntax, though it probably isn't parsable
        FilterRenderer ren = new FilterRenderer();
        String oldText = ren.render(oldRoot);
        String newText = ren.render(newRoot);
        
        // this is stored outside the attribute diff list so we
        // can more easily get to it and display it differently
        Difference diff = Difference.diff(oldText, newText);
        if (diff != null)
        diffs.setFilterDifference(diff);
    }
    
    /**
     * From RoleLifecycler: Collapse a list of filters into a single filter with the
     * list elements AND'd.
     */
    private Filter simplify(List<Filter> filters) {

        Filter root = null;
        if (filters != null) {
            for (Filter f : filters) {
                if (root == null)
                    root = f;
                else 
                    root = Filter.and(root, f);
            }
        }
        return root;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Approval Building
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Setup an Approval for the owner of a SailPointObject.
     * Default owner is whatever is on the embedded object,
     * it may also be passed in.
     *
     * @ignore
     * This was build for role approvals, but it is fairly 
     * generic except for the message keys.  Could refactor this
     * and move to ApprovalLibrary but we have to figure out how
     * to pass the keys.
     */
    public Approval buildOwnerApproval(WorkflowContext wfc) {
        Approval app = null;
        try {
            SailPointObject obj = getObject(wfc);
            if (obj != null) {
                Identity owner = null;
                Workflow wf = wfc.getWorkflow();
                String approver = wf.getString(Workflow.VAR_APPROVER);
                if (approver != null) {
                    SailPointContext spcon = wfc.getSailPointContext();
                    owner = spcon.getObjectByName(Identity.class, approver);
                }

                // note that the approval is sent to the ORIGINAL 
                // owner which may not be the same as the new owner
                if (owner == null)
                    owner = getObjectOwner(wfc);
                
                if (owner != null) {
                    app = new Approval();
                    app.setOwner(owner.getName());
                    app.setIdentity(owner); 
                    // in theory should localized based on the
                    // locale of the owner...
                    String key; 
                    WorkflowCase wfcase = wfc.getWorkflowCase();
                    if (wfcase.isDeleteApproval())
                        key = MessageKeys.WORKFLOW_ROLE_APPROVAL_DELETE;
                    else if (wfcase.isRollbackApproval())
                        key = MessageKeys.WORKFLOW_ROLE_APPROVAL_ROLLBACK;
                    else if (obj.getId() == null)
                        key = MessageKeys.WORKFLOW_ROLE_APPROVAL_CREATE;
                    else
                        key = MessageKeys.WORKFLOW_ROLE_APPROVAL_UPDATE;
                        
                    String name = obj.getName();
                    if (obj instanceof Bundle) {
                        Bundle bundle = (Bundle)obj;
                        name = bundle.getDisplayableName();
                    }
                    
                    app.setName(getMessage(key, name));
                }
            }
        }
        catch (Throwable t) {
            // not much we can do here, maybe set an error
            // status in the Workflow and trigger an automatic
            // transition?
            log.error(t.getMessage());
        }
        return app;
    }

    /**
     * For Role approvals only, build an Approval structure
     * for the owners of each Application referenced in the
     * role profiles.  This is normally processed as a parallelPoll with
     * the application owners submitting comments or modifying the
     * role but not terminating the approval process.
     *
     * @ignore
     * The CapONE POC went beyond this and removed the Profiles
     * for each app owner that rejected but I don't think this
     * is always desirable.
     *
     * The POC would send out a different approval for each profile
     * even if the applications were the same.  The Approver could
     * then remove individual profiles.  We identified the profiles
     * to be removed by setting the "tag" property of each Approval
     * to the Profile id, then overloading the endApproval callback
     * to look at this tag and remove the profile if the rejected.
     * 
     */
    public List<Approval> buildApplicationApprovals(WorkflowContext wfc) {
        List<Approval> approvals = null;
        try {
            SailPointObject obj = getObject(wfc);
            if (obj instanceof Bundle) {
                Bundle role = (Bundle)obj;
                List<Profile> profiles = role.getProfiles();
                if (profiles != null) {
                    List<Application> appsProcessed = new ArrayList<Application>();
                    for (Profile p : profiles) {
                        Application appl = p.getApplication();
                        if (!appsProcessed.contains(appl)) {
                            appsProcessed.add(appl);
                            Identity owner = appl.getOwner();
                            if (owner != null) {
                                Approval appr = new Approval();
                                appr.setName("Approve use of application: " + 
                                             appl.getName());
                                appr.setOwner(owner.getName());

                                if (approvals == null)
                                    approvals = new ArrayList<Approval>();
                                approvals.add(appr);
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            // not much we can do here, maybe set an error
            // status in the Workflow and trigger an automatic
            // transition?
            log.error(t.getMessage());
        }

        return approvals;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Activate/Deactivate
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_ROLE = RoleEventGenerator.ARG_ROLE;
    public static final String ARG_DISABLE = "disable";

  //saves role change events in the database
    private void saveRoleChangeEvents(List<RoleChangeEvent> eventList, SailPointContext context) throws GeneralException {
        for(RoleChangeEvent roleChangeEvt: Util.iterate(eventList)) {
            context.saveObject(roleChangeEvt);
        }
        context.commitTransaction();
    }

    /**
     * Enables the role 
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void enableRole(WorkflowContext wfc) 
        throws GeneralException {

        changeRoleStatus(wfc, false);
    }

    /**
     * Disables the role 
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void disableRole(WorkflowContext wfc) 
        throws GeneralException {

        changeRoleStatus(wfc, true);
    }

    /**
     * Enables or disabled the role based on value of {@link #ARG_DISABLE}
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void setRoleDisabledStatus(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        boolean disabled = args.getBoolean(ARG_DISABLE);
        
        changeRoleStatus(wfc, disabled);
    }

    /**
     * Original 4.0 name kept for compatibility.
     */
    public void updateRolesDisabledFlag(WorkflowContext wfc)
        throws GeneralException {
        
        setRoleDisabledStatus(wfc);
    }

    private void changeRoleStatus(WorkflowContext wfc, boolean disable)
        throws GeneralException {

        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
        
        String roleId = args.getString(ARG_ROLE);
        if (roleId == null)
            throw new GeneralException("Missing argument: role");

        Bundle role =  spcon.getObjectById(Bundle.class, roleId);
        if (role == null)
            throw new GeneralException("Unkown role: id[" + roleId + "]");

        else {
            if (role.isDisabled() == disable) {
                // is this interresting?
                String state = (disable) ? "disabled" : "enabled";
                log.warn("Role is already " + state + ": " + roleId);
            }
            else {
                role.setDisabled(disable);
                //calculate the role differences before committing the Role in database
                boolean isRolePropEnabled = Util.otob(spcon.getConfiguration().getBoolean(Configuration.ALLOW_ROLE_PROPAGATION));

                // When true it means to disable role change propagation.
                boolean noRolePropagation = args.getBoolean(RoleLibrary.VAR_NO_ROLE_PROPAGATION);
                isRolePropEnabled &= !noRolePropagation;

                List<RoleChangeEvent> roleChangeEvents = null;
                if (isRolePropEnabled) {
                    roleChangeEvents = new RoleChangeAnalyzer(spcon).calculateRoleChanges(role);
                }
                //do not trust the context when we need to commit
                spcon.decache();

                spcon.saveObject(role);
                spcon.commitTransaction();

                if (isRolePropEnabled)
                    saveRoleChangeEvents(roleChangeEvents, spcon);
            }
        }
    }
    
    /**
     * Removes incomplete requests to activate/deactivate roles that no longer exists.
     * @param wfc The current WorkflowContext object.
     * @throws GeneralException
     */
    public void removeOrphanedRoleRequests(WorkflowContext wfc)
        throws GeneralException
    {
        RoleEventGenerator generator = new RoleEventGenerator(wfc.getSailPointContext());
        generator.removeOrphanedRoleRequests();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Audit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by the "action" Arg being passed to the audit() method 
     * when were auditing approvals.
     */
    public String getApprovalAuditAction(WorkflowContext wfc) 
        throws GeneralException {
        
        String action = AuditEvent.ActionUpdateRole;
        SailPointObject obj = getObject(wfc);
        if (obj != null && obj.isDisabled())
            action = AuditEvent.ActionDisableRole;
        return action;
    }
    
    /**
     * If the object being approved is an instance of Bundle, use the 
     * displayable name, otherwise use the default getObjectName method.
     * 
     * @param wfc The workflow context.
     * @return The displayable name.
     */
    public String getDisplayableRoleName(WorkflowContext wfc) {
        SailPointObject obj = getObject(wfc);
        if (null == obj) {
            return null;
        }
        
        if (obj instanceof Bundle) {
            Bundle bundle = (Bundle)obj;
            
            return bundle.getDisplayableName();
        }
        
        return super.getObjectName(wfc);
    }

}
